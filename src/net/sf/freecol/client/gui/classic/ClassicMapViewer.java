/**
 *  Copyright (C) 2002-2024  The FreeCol Team
 *
 *  This file is part of FreeCol.
 *
 *  FreeCol is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  FreeCol is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with FreeCol.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.sf.freecol.client.gui.classic;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;
import java.util.WeakHashMap;
import java.util.function.IntConsumer;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.GUI;
import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Direction;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Settlement;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.Unit;


/**
 * The classic-UI map view: a rectangular-grid renderer for the FreeCol map.
 *
 * Where {@code SwingGUI}'s {@link net.sf.freecol.client.gui.mapviewer.MapViewer}
 * paints isometric diamonds, this paints a plain rectangular grid of tiles
 * from an explicit view origin, mirroring the look of the original 1994
 * Colonization.  The per-tile <em>image selection</em> logic is reused from
 * {@link ImageLibrary} (terrain / settlement / unit lookups); only the
 * projection is different.  On the HUD grid ({@link #setFixedScale}) the
 * origin {@code (vx, vy)} is the tile in the top-left cell:
 *
 * <pre>screenX = (tileX - vx) * tileW
 * screenY = (tileY - vy) * tileH</pre>
 *
 * and it moves only by the original's jumps ({@link #jumpIfNeeded}, build
 * spec W4).  The adaptive layout centres the origin's cell
 * ({@link ClassicHud#UNIT_COL}, {@link ClassicHud#UNIT_ROW}) instead.
 *
 * <p><b>Terrain.</b> On the HUD grid the terrain is the original's as palette
 * indices (M1c design 10 §6, W6a): {@link ClassicTerrainLayer} composes the
 * 15x12 view from the {@code TERRAIN.SS} and {@code PHYS0.SS} index sheets
 * ({@link ClassicTerrainComposer}: the dark unexplored tile, its 3-px fog
 * fringe from explored neighbours, the side-mask blends, the coast quarters
 * and beach corners, the overlays) and draws it through the game palette.
 * It reads the explored state <em>as shown</em> ({@link #shownExplored}): taken from the model by full paints
 * outside a slide, so a reveal the model already holds appears with the
 * slide's final draw, not in its margins (Critic 5); the true terrain of
 * the fog ring comes from {@link ClassicTerrainOracle}.  Without the pack's
 * index sheets, and in the adaptive layout, the RGBA fallback draws the
 * {@code TERRAIN.SS} PNGs (aliased onto FreeCol's
 * {@code image.tile.<type>.center} keys, see
 * {@code tools/classic_assets/aliases.properties}; FreeCol's own art without
 * the pack) with their overlays and no blends, and unexplored tiles flat in
 * the dark sea's colour; the native 16&times;16 tiles are up-scaled by the
 * integer factor {@code scale()} with nearest-neighbour interpolation to keep
 * the chunky classic pixels crisp.
 *
 * <p>This panel owns the classic view state (view mode, view origin,
 * selected tile, active unit); {@link ClassicGUI} delegates the
 * corresponding {@code GUI} facade methods to it.
 */
final class ClassicMapViewer extends JPanel {

    /** Native size (px) of an original {@code TERRAIN.SS} tile sprite. */
    private static final int TILE_SRC = 16;

    /**
     * Tile columns visible in the original game's map viewport (240px map
     * area / 16px tiles).  The adaptive {@link #scale()} targets this count,
     * so the view reads as zoomed-in as the 1994 original regardless of
     * window size.
     */
    private static final int CLASSIC_VIEW_COLS = 15;

    /** Minimum integer up-scale from the native 16&times;16 tile. */
    private static final int MIN_SCALE = 3;

    /** The HUD's fixed scale ({@link #setFixedScale}), or 0 for adaptive. */
    private int fixedScale = 0;

    /**
     * The keystrokes this viewer binds {@code WHEN_IN_FOCUSED_WINDOW}
     * ({@link #installKeyBindings}); {@link ClassicKeyMap} leaves them to the
     * map.  Kept in step with the bindings by hand.
     */
    static final String[] BOUND_KEYS = {
        "UP", "NUMPAD8", "DOWN", "NUMPAD2", "LEFT", "NUMPAD4", "RIGHT",
        "NUMPAD6", "HOME", "NUMPAD7", "PAGE_UP", "NUMPAD9", "END", "NUMPAD1",
        "PAGE_DOWN", "NUMPAD3", "ENTER", "SPACE", "W", "B"
    };

    /** Native tile-sprite size requested from {@link ImageLibrary}. */
    private static final Dimension SRC_SIZE = new Dimension(TILE_SRC, TILE_SRC);

    /**
     * The colour of an unexplored tile in the RGBA fallback: VICEROY.PAL's
     * index 61, the dark sea's main colour ({@code PHYS0.SS.148} is 165 of
     * its 256 px; design 10 §4).
     */
    static final Color DARK_SEA = new Color(0x181C7D);

    /**
     * Fraction of the cell an up-scaled classic unit/settlement sprite fills
     * in the adaptive (non-HUD) layout only.  On the HUD grid
     * ({@link #setFixedScale}) sprites are drawn 1:1 in native pixels, as
     * the original does (build spec W2).
     */
    private static final double UNIT_CELL_FRACTION = 0.9;

    /**
     * Native pixels a cell's settlement or unit icon may reach past the
     * cell: the shadow 2 px left, the stack marker 2 px up, a 21-px
     * settlement 2 px left and 3 px right ({@link ClassicHud#paintIcon},
     * {@link ClassicHud#settlementOffset}).
     */
    private static final int ICON_MARGIN = 3;

    /** Interval (ms) between successive edge-scroll steps while at an edge. */
    private static final int EDGE_SCROLL_INTERVAL_MS = 110;

    private final FreeColClient freeColClient;

    /**
     * The owning {@link ClassicGUI}, used to route clicks/keys through the
     * {@code GUI} facade (select, focus, show-colony) exactly as
     * {@code SwingGUI.clickAt}/{@code MoveAction} do, so the classic UI drives
     * the real controllers rather than a self-contained local state.
     */
    private final ClassicGUI gui;

    /** Image library used for terrain/unit/settlement lookups. */
    private final ImageLibrary lib;

    /** Composites the per-tile feature overlays (item (e)) onto each cell. */
    private final ClassicTileArt tileArt;

    // Classic view state (owned here; ClassicGUI delegates to these).
    private GUI.ViewMode viewMode = GUI.ViewMode.END_TURN;

    /**
     * The view origin {x, y}: the map tile in the view's top-left cell,
     * within {@link ClassicHud#clampView}'s range (one step past it only
     * to show a unit on the map's outer ring, {@link ClassicHud#viewFor});
     * null before the first view.  Replaced, never changed in place (the
     * recorder's probe reads it from another thread, {@link #peekViewOrigin}).
     */
    private volatile int[] origin;

    private Tile selectedTile;
    private Unit activeUnit;

    /**
     * The unit of a silent visit (master plan W5f, {@link #visit}): drawn
     * on top of its tile until the next activation; null when none.
     */
    private Unit visitedUnit = null;

    // In-progress unit slide (see animateMove): the unit drawn animOffset
    // native pixels from animFrom toward animTo, or null when idle.
    private Unit animUnit;
    private Tile animFrom;
    private Tile animTo;

    /** The slide's native offset, 0..15 ({@link ClassicSlide}). */
    private int animOffset;

    /** The slide's raw-grid step per axis (the destination minus the source). */
    private int animDx, animDy;

    /**
     * The native-phase cue (build spec W19) up now: the source tile of the
     * native move that comes next, whose minimap pixel is white, or null.
     */
    private Tile cueTile = null;

    /**
     * The cue's tile is on the screen (in the view's margin): the white
     * square is drawn on it and the sprite there hidden.
     */
    private boolean cueSquare = false;

    /**
     * A slide (with its cue) holds the event thread now; read by the
     * preload's hold on another thread ({@link #holdsPreload}).
     */
    private volatile boolean timedPaint = false;

    /**
     * Foreign moves the server announced whose slide has not started yet
     * (build spec W19, M1 acceptance F5): each unit with its moves {source,
     * destination}, oldest first.  The server sends the animation before
     * the update, and in the AI phase the slides wait on the event thread
     * while the model runs ahead: a unit could show at its new tile before
     * its slide.  Until its slide starts such a unit is drawn at its next
     * move's source instead ({@link #displayUnit}).  Written by the
     * network thread ({@link #moveQueued}), read and taken on the event
     * thread; guarded by itself.
     */
    private final java.util.Map<Unit, java.util.ArrayDeque<Tile[]>> queuedMoves
        = new java.util.HashMap<>();

    /**
     * The unit a movement key is moving right now ({@link #handleMoveKey}),
     * else null: its slide follows the key, not a previous slide, so it
     * keeps no pause after the last final draw.
     */
    private Unit keyMoveUnit = null;

    /**
     * The passenger a landing sends ashore ("An Land gehen", build spec
     * W8b), and when its slide may start: {@link #LANDING_SLIDE_MS} after
     * the box closed; null when none is due.
     */
    private Unit landingUnit = null;
    private long landingDue = 0L;

    /** When the last movement key was taken, on the slide clock; 0 before the first. */
    private long moveKeyNanos = 0L;

    /**
     * From the close of the landing box to the landed unit's offset 0
     * over the ship: 6-9 frames in the clips (landfall #11570 -&gt; #11576/7,
     * clip007 #3039 -&gt; #3048, #5736 -&gt; #5743, #6575 -&gt; #6583:
     * 86-128 ms).
     */
    static final double LANDING_SLIDE_MS = 110.0;

    /**
     * A slide ended, so the next paint is its final draw: offset 16,
     * together with the tiles the move revealed (logged as
     * {@code final-draw}, see {@link ClassicFrameRecorder}).
     */
    private boolean finalDrawPending = false;

    /** {@code System.nanoTime} of the last final draw; 0 before the first. */
    private long lastFinalNanos = 0L;

    /** The final draw being painted shows land newly explored ({@link #noteReveal}). */
    private boolean landRevealed = false;

    /**
     * The next paint shows a change the player sees: a view jump, a
     * blink toggle, the cursor (the final draw counts by itself).  Told
     * to the GUI's turn flow (build spec W5: the pauses run from the last
     * change), while the many repaints that change nothing are not.
     */
    private boolean changeToShow = false;

    /**
     * The active unit's blink is OFF: its tile is drawn bare, with no unit
     * at all, carrier and stack included (build spec W3).
     */
    private boolean blinkOff = false;

    /**
     * The blink clock (build spec W3): armed ON on every activation, at the
     * end of every slide and when a box closes; its toggles come through
     * {@link #blinkToggle}.
     */
    private final ClassicBlink blink;

    /**
     * The blink is held ON: a slide, a dialog, a menu, the first scene or
     * the AI phase is on ({@link #blinkHoldReason}).  The hold's end
     * re-arms the clock ({@link #resumeBlink}, else the next toggle).
     */
    private boolean blinkHeld = false;

    /**
     * The Spielzugende mode's cursor tile (build spec W17), or null: a
     * white 16x16 square on it, blinking on its own clock in step with the
     * panel's word, while the unit blink is idle (no active unit).
     */
    private Tile promptTile = null;

    /** The square is in its OFF phase (the tile drawn as it is). */
    private boolean promptOff = false;

    /**
     * The end command froze the square ON: it stays until the map is next
     * repainted over it -- a slide, a jump, the next activation (clip004
     * #2766, landing-slow #2902/#5525/#6460).
     */
    private boolean promptFrozen = false;

    /** A box, a menu or a screen is up: the square's phase is frozen until it closes. */
    private boolean promptHeld = false;

    /** The square's clock (the blink's half-period, ON first). */
    private final ClassicBlink promptBlink;

    /** The last unit made active (the Spielzugende mode's cursor tile is its tile). */
    private Unit lastUnit = null;

    /**
     * The delay of the square's ON after a box or a menu closes: one frame
     * (landing-slow #5320 restore -&gt; #5321 ON, clip004 #4726 -&gt; #4727).
     */
    static final double PROMPT_RESTART_MS = 15.0;

    /** FONTTINY and the pack's texts for the flag letters; null without the pack. */
    private ClassicFont iconFont;
    private ClassicText iconText;

    /** Sprites scaled to 16x16 (only the large fallback art without the pack). */
    private final WeakHashMap<BufferedImage, BufferedImage> fitted
        = new WeakHashMap<>();

    /** Cached spec lookup for plain ocean (the adaptive layout's off-map sea), lazy. */
    private TileType oceanType;

    /**
     * The terrain as palette indices on the HUD grid (W6a), or null: the
     * RGBA fallback ({@link #paintTile}).
     */
    private ClassicTerrainLayer layer = null;

    /** The true terrain of the fog ring for the layer, or null (unknown). */
    private ClassicTerrainOracle oracle = null;

    /** The layer's view of the map ({@link ShownTerrain}). */
    private final ShownTerrain shownTerrain = new ShownTerrain();

    /**
     * The explored state as shown, by {@code y * width + x} of
     * {@link #shownMap} (Critic 5): taken from the model by every full
     * paint outside a slide (the final draw included), kept through a
     * slide.  Server-pushed moves are animated after their update, so
     * the model can already hold a move's reveal while it slides; the
     * map shows it with the final draw.  Null before the first paint.
     */
    private BitSet shown = null;

    /** The map {@link #shown} belongs to. */
    private Map shownMap = null;

    /** {@link #shown} at the last final draw (the recorder's reveal event). */
    private BitSet shownAtFinal = null;

    /**
     * The map without a client (tests, the preview harness): the map of
     * the tile the view was last centred on.
     */
    private Map viewMap = null;

    /**
     * Tests only: the viewer is never shown headless, so its immediate
     * paints ({@link #paintNow}) go into this image instead; null on the
     * screen.
     */
    private BufferedImage offscreen = null;

    /**
     * The clock of the blocking waits on the event thread -- the slide
     * ({@link ClassicSlide#run}), the gap before a chained slide, the
     * native cue -- and the water cycle's servicing clock in the game
     * ({@link ClassicWaterCycle#servicing}): a palette step due meanwhile is
     * painted at its own deadline, between two slide steps (design 10 §7.2,
     * Critic 15).  The slide's schedule is the same.
     */
    private ClassicSlide.Clock slideClock = ClassicSlide.SYSTEM;

    /**
     * True while a palette step paints the cycling cells: that paint shows
     * no change of the game, so it takes no explored state, uses up no final
     * draw and tells the turn flow nothing (Critic 3).
     */
    private boolean palettePaint = false;

    /**
     * Repeating timer that drives edge scrolling; it pans the view by
     * {@link #edgeDX}/{@link #edgeDY} each tick while the mouse sits in an edge
     * hot zone, and is stopped whenever that direction is zero.
     */
    private final Timer edgeScrollTimer;
    private int edgeDX;
    private int edgeDY;

    /**
     * Set by {@link #dispose}.  The viewer can stay the content pane for a
     * moment after it is disposed (until the queued title panel replaces it),
     * and a mouse event still queued for it must not restart the timer.
     */
    private boolean disposed = false;

    /** Longest edge (px) of the whole-map minimap raster (drawn by the info panel). */
    private static final int MINIMAP_MAX = 200;

    /**
     * Cached raster of the whole map (one {@link #minimapPPT}-px square per
     * tile), rebuilt only when {@link #minimapDirty} — i.e. when the model
     * changes (exploration / settlements / unit moves come through
     * {@code refresh}) — not on every repaint.  Each paint just blits this
     * image and overlays the viewport box, so panning/cursor repaints stay
     * cheap even on a large map.  Null until first built.
     */
    private BufferedImage minimapCache;

    /** Pixels per tile in {@link #minimapCache} (integer, at least 1). */
    private int minimapPPT = 1;

    /** Whether {@link #minimapCache} needs rebuilding before the next blit. */
    private boolean minimapDirty = true;


    ClassicMapViewer(FreeColClient freeColClient, ClassicGUI gui,
                     ImageLibrary lib) {
        this(freeColClient, gui, lib, true);
    }

    /**
     * @param blinkThread Whether the blink clock runs a thread of its own,
     *     which posts its toggles to the event thread; false for tests,
     *     which call {@link #blinkToggle} themselves.
     */
    ClassicMapViewer(FreeColClient freeColClient, ClassicGUI gui,
                     ImageLibrary lib, boolean blinkThread) {
        this.freeColClient = freeColClient;
        this.gui = gui;
        this.lib = lib;
        this.tileArt = new ClassicTileArt(lib);
        this.blink = new ClassicBlink(ClassicSlide.SYSTEM,
            SwingUtilities::invokeLater, this::blinkToggle, blinkThread);
        this.promptBlink = new ClassicBlink(ClassicSlide.SYSTEM,
            SwingUtilities::invokeLater, this::promptToggle, blinkThread);
        setBackground(Color.BLACK);
        setOpaque(true);
        setFocusable(true);
        addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    onClick(e);
                }
                @Override
                public void mouseExited(MouseEvent e) {
                    stopEdgeScroll();
                }
            });
        addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    updateEdgeScroll(e.getPoint());
                }
                @Override
                public void mouseDragged(MouseEvent e) {
                    updateEdgeScroll(e.getPoint());
                }
            });
        this.edgeScrollTimer = new Timer(EDGE_SCROLL_INTERVAL_MS, e -> {
                if (inputBlocked()) return;
                if (this.edgeDX != 0 || this.edgeDY != 0) {
                    panView(this.edgeDX, this.edgeDY);
                }
            });
        installKeyBindings();
    }


    /**
     * Bind the keyboard movement controls.  The arrow keys and numpad 8/2/4/6
     * move orthogonally, numpad 7/9/1/3 and Home/PageUp/End/PageDown move
     * diagonally — matching FreeCol's own {@code moveAction.*.accelerator}
     * key layout.  Bound {@code WHEN_IN_FOCUSED_WINDOW} so the keys work
     * whenever the map window is focused, independent of which child component
     * currently holds focus.
     *
     * <p>Unlike item (b)'s provisional raw-grid pan, these keys now drive the
     * real game, mirroring {@link net.sf.freecol.client.gui.action.MoveAction}:
     * in MOVE_UNITS mode they move the active unit via
     * {@link net.sf.freecol.client.control.InGameController#moveUnit}; in
     * TERRAIN mode they step the selected-tile cursor to a neighbour.  When
     * nothing is selected (END_TURN mode) they do nothing, as in the
     * original (build spec W5d).
     *
     * <p><b>Isometric vs. rectangular.</b> The model is isometric — a model
     * {@link Direction} steps in the diamond lattice, so {@code Direction.N}
     * jumps two raw rows — but this viewer draws a plain rectangular grid.  To
     * keep on-screen movement matching the pressed key, the four orthogonal
     * keys resolve to the {@code Direction} whose <em>raw</em> step lands on the
     * visually adjacent cell (computed parity-aware via
     * {@link Map#getDirection}); the four diagonal keys map to the isometric
     * corner directions, whose raw offset shifts with row parity (documented in
     * {@link #intentToDirection}).
     */
    private void installKeyBindings() {
        final InputMap im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        final ActionMap am = getActionMap();
        bindMove(im, am, Intent.UP, "UP", "NUMPAD8");
        bindMove(im, am, Intent.DOWN, "DOWN", "NUMPAD2");
        bindMove(im, am, Intent.LEFT, "LEFT", "NUMPAD4");
        bindMove(im, am, Intent.RIGHT, "RIGHT", "NUMPAD6");
        bindMove(im, am, Intent.NW, "HOME", "NUMPAD7");
        bindMove(im, am, Intent.NE, "PAGE_UP", "NUMPAD9");
        bindMove(im, am, Intent.SW, "END", "NUMPAD1");
        bindMove(im, am, Intent.SE, "PAGE_DOWN", "NUMPAD3");
        bindTurnControls(im, am);
    }

    /**
     * Bind the classic-<em>Colonization</em> turn-control keys, driving the real
     * {@link net.sf.freecol.client.control.InGameController} (the classic UI has
     * no menu bar / {@code Canvas} to install FreeCol's own accelerators, so we
     * bind directly, {@code WHEN_IN_FOCUSED_WINDOW} like the movement keys).
     *
     * <p>Keys follow the original 1994 game's reference (from the manual):
     * <ul>
     *   <li><b>Enter</b> — end of turn ("Pressing the Space Bar, Enter key … causes
     *   the next game turn to begin"), in the Spielzugende mode only (or with
     *   nothing left to move; build spec W17): with units left the turn ends by
     *   itself once none can move.</li>
     *   <li><b>Space</b> — "no orders": skip the active unit for this turn.  With
     *   no active unit, Space likewise ends the turn, under the same condition as
     *   Enter (matching the original, where Space advances the turn once every
     *   unit is done).</li>
     *   <li><b>W</b> — wait: temporarily skip this unit, cycle through the others,
     *   then return to it.</li>
     *   <li><b>B</b> — build a colony with the active unit ("To build a colony,
     *   press the build key (B)").</li>
     * </ul>
     * (Movement keys — arrows + numpad — are bound above and unchanged.)
     */
    private void bindTurnControls(InputMap im, ActionMap am) {
        im.put(KeyStroke.getKeyStroke("ENTER"), "classic_endTurn");
        am.put("classic_endTurn", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (inputBlocked()) return;
                    endTurn();
                }
            });
        im.put(KeyStroke.getKeyStroke("SPACE"), "classic_skip");
        am.put("classic_skip", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (inputBlocked()) return;
                    skipActiveUnitOrEndTurn();
                }
            });
        im.put(KeyStroke.getKeyStroke("W"), "classic_wait");
        am.put("classic_wait", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (inputBlocked()) return;
                    waitActiveUnit();
                }
            });
        im.put(KeyStroke.getKeyStroke("B"), "classic_buildColony");
        am.put("classic_buildColony", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (inputBlocked()) return;
                    buildColony();
                }
            });
    }

    /**
     * End the current turn.  Passes {@code showDialog=false}: the classic
     * {@code GUI} no-ops modal dialogs, so {@code endTurn(true)}'s "units still
     * active" confirmation would misbehave.  The server's new-turn response drives
     * the next active unit back through the {@code changeView}/{@code refresh}
     * hooks {@link ClassicGUI} already delegates here.  Through the GUI's
     * turn flow (build spec W5), which lights the turn indicator first.
     */
    private void endTurn() {
        if (this.gui != null) {
            // Only in the Spielzugende mode, or with nothing left to move
            // (spec delta W17 item 7): the original has no other end of
            // turn, it ends by itself when no unit can move.
            if (!this.gui.mayEndTurnByKey()) {
                ClassicFrameRecorder.event("key-ignored", "end-turn mode="
                    + this.viewMode);
                return;
            }
            this.gui.requestEndTurn("key");
            return;
        }
        ClassicFrameRecorder.event("end-turn", "request");
        this.freeColClient.getInGameController().endTurn(false);
    }

    /**
     * The classic Space key: give the active unit "no orders" this turn (skip it
     * and advance to the next unit needing orders), mirroring
     * {@code SkipUnitAction}.  With no active unit there is nothing to skip, so —
     * as in the original game — Space ends the turn instead; a unit off the
     * map (a ship that has sailed for Europe) counts as none.
     *
     * <p>A passenger offered aboard stays aboard (master plan W18).  The
     * skipped unit stays drawn ON and stops blinking, and the next unit
     * comes as after a last move (clip007 #1397 -&gt; #1449, #1528,
     * {@code ClassicTurnFlow.ranOut}).  The controller's state change
     * already asks for the next unit; asking once more took two units
     * from the cycle, so the first of them was passed over.
     */
    private void skipActiveUnitOrEndTurn() {
        final Unit unit = this.activeUnit;
        if (unit == null || !unit.hasTile()) {
            endTurn();
            return;
        }
        if (ClassicFrameRecorder.on()) {
            ClassicFrameRecorder.event("skip", "unit=" + unit.getId()
                + " at=" + xy(unit.getTile()) + " moves=" + unit.getMovesLeft()
                + (unit.isOnCarrier() ? " aboard=" + unit.getCarrier().getId() : ""));
        }
        if (unit.getState() != Unit.UnitState.SKIPPED
            && this.freeColClient.getInGameController()
                .changeState(unit, Unit.UnitState.SKIPPED)) {
            skipped();
            return;   // its updateGUI brought the next unit
        }
        if (unit.getState() == Unit.UnitState.SKIPPED) {
            skipped();
            this.freeColClient.getInGameController().nextActiveUnit();
        }
    }

    /**
     * The skipped unit is redrawn ON and stops blinking; the redraw is the
     * last change the next unit's pause runs from (clip007 #1528: the
     * sprite redrawn off its rhythm, the end 0.513 s later).
     */
    private void skipped() {
        rearmBlink("skip");
        if (this.gui != null) this.gui.screenChanged();
    }

    /**
     * The classic W key: wait the active unit — the next unit of the
     * original's unit cycle comes, and this one again after the wrap
     * ({@code ClassicGUI.waitUnit}, master plan W5f); without the GUI
     * {@code InGameController.waitUnit}.
     */
    private void waitActiveUnit() {
        if (this.gui != null) {
            this.gui.waitUnit(this.activeUnit);
            return;
        }
        this.freeColClient.getInGameController().waitUnit();
    }

    /**
     * The classic B key: found a colony with the active unit, mirroring
     * {@code BuildColonyAction} ({@code InGameController.buildColony}).  The
     * controller does the rest — it checks the unit can build, confirms any
     * site warnings, asks {@link ClassicGUI#getNewColonyName} for the name, and
     * on success opens the colony screen.  A unit that cannot found a colony
     * now gets the original's refusal instead ({@link ClassicGUI#colonyRefused}:
     * a ship @SEACOLONY, a wagon train @ONLYCOL, a colonist without moves
     * nothing; R3); the controller's site refusals come as the original's
     * boxes through {@link ClassicGUI#colonyNotice}.
     */
    private void buildColony() {
        final Unit unit = this.activeUnit;
        if (unit == null || !unit.hasTile()) return;
        if (!unit.canBuildColony()) {
            if (this.gui != null) this.gui.colonyRefused(unit);
            return;
        }
        this.freeColClient.getInGameController().buildColony(unit);
    }

    /**
     * An on-screen movement intent from a key press.  Distinct from a model
     * {@link Direction} because the four orthogonal intents resolve to a
     * parity-dependent {@code Direction} (see {@link #intentToDirection}).
     */
    private enum Intent { UP, DOWN, LEFT, RIGHT, NW, NE, SW, SE }

    /**
     * Bind the given keystrokes to the given {@link Intent}.  A key the
     * map must ignore now ({@link #inputBlocked}) is logged as
     * {@code key-blocked} for the recorder.
     */
    private void bindMove(InputMap im, ActionMap am, Intent intent,
                          String... keys) {
        final String name = "move_" + intent;
        for (String key : keys) {
            im.put(KeyStroke.getKeyStroke(key), name);
        }
        am.put(name, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (inputBlocked()) {
                        ClassicFrameRecorder.event("key-blocked", intent.toString());
                        return;
                    }
                    handleMoveKey(intent);
                }
            });
    }

    /**
     * Whether the map's own keys and clicks must do nothing: while the
     * first game scene is up ({@link ClassicGUI#isDialogShowing}; the
     * scene's layer holds the focus and consumes every key, so this is the
     * backstop for a key that reaches the map anyway), and while the
     * player waits (build spec W5d): not our turn, or the pause before the
     * end of turn, the next unit or the turn's first unit is pending
     * ({@link ClassicGUI#turnInputBlocked}).  Nothing on the screen reacts
     * then (landfall 03 section 7).
     */
    private boolean inputBlocked() {
        return this.gui != null
            && (this.gui.isDialogShowing() || this.gui.turnInputBlocked());
    }

    /**
     * Resolve a movement {@link Intent} to the model {@link Direction} to apply
     * from a reference tile.
     *
     * <p>The four orthogonal intents pick the {@code Direction} whose step from
     * {@code ref} lands on the visually adjacent raw cell — always a real
     * isometric neighbour, but which one depends on {@code ref}'s row parity
     * (e.g. the cell straight above is {@code NE} on even rows, {@code NW} on
     * odd rows), so we look it up via {@link Map#getDirection}.  The four
     * diagonal intents map straight to the isometric corner directions; their
     * raw-grid offset likewise shifts with parity, so a diagonal key may read as
     * a straight or diagonal step depending on the row — an inherent artefact of
     * flattening the isometric map onto a rectangular grid.
     *
     * @return The {@code Direction}, or null if the intended neighbour is off
     *     the map.
     */
    private Direction intentToDirection(Intent intent, Tile ref) {
        final Map map = getMap();
        if (map == null || ref == null) return null;
        switch (intent) {
        case UP:    return dirToRaw(ref, ref.getX(), ref.getY() - 1);
        case DOWN:  return dirToRaw(ref, ref.getX(), ref.getY() + 1);
        case LEFT:  return Direction.W;
        case RIGHT: return Direction.E;
        case NW:    return Direction.NW;
        case NE:    return Direction.NE;
        case SW:    return Direction.SW;
        case SE:    return Direction.SE;
        default:    return null;
        }
    }

    /** The {@code Direction} from {@code ref} to the raw cell {@code (tx, ty)}. */
    private Direction dirToRaw(Tile ref, int tx, int ty) {
        final Map map = getMap();
        final Tile t = (map == null) ? null : map.getTile(tx, ty);
        return (t == null) ? null : map.getDirection(ref, t);
    }

    /**
     * Handle a movement key: move the active unit (MOVE_UNITS) or step the
     * selected-tile cursor (TERRAIN).  Mirrors {@code MoveAction.actionPerformed}.
     * With nothing selected the key does nothing: the original's arrows
     * never moved the landscape while no unit was up (build spec W5d,
     * landfall 03 section 7); the view moves only by its jumps.
     */
    private void handleMoveKey(Intent intent) {
        if (this.viewMode == GUI.ViewMode.MOVE_UNITS
            && this.activeUnit != null && this.activeUnit.getTile() != null) {
            final Direction d = intentToDirection(intent, this.activeUnit.getTile());
            if (d != null) {
                final Unit u = this.activeUnit;
                this.moveKeyNanos = this.slideClock.now();
                if (ClassicFrameRecorder.on()) {
                    ClassicFrameRecorder.event("move-key", intent + " " + d
                        + " unit=" + u.getId() + " at=" + xy(u.getTile())
                        + " moves=" + u.getMovesLeft());
                }
                // Roger's Europe question takes a ship's order east past
                // the last drawn column, or west past the first, instead
                // of a move (build spec W8a, E1).
                if (this.gui != null && this.gui.sailHomeKey(u, d)) {
                    if (ClassicFrameRecorder.on()) {
                        ClassicFrameRecorder.event("move-done", "unit=" + u.getId()
                            + " at=" + xy(u.getTile()) + " moves=" + u.getMovesLeft()
                            + " question");
                    }
                    repaint();
                    return;
                }
                // A refused move gets the original's box, or nothing, and
                // never reaches the controller: no sound, no move cost, the
                // unit stays active (R3).
                if (this.gui != null && this.gui.illegalMoveKey(u, d)) {
                    if (ClassicFrameRecorder.on()) {
                        ClassicFrameRecorder.event("move-done", "unit=" + u.getId()
                            + " at=" + xy(u.getTile()) + " moves=" + u.getMovesLeft()
                            + " refused");
                    }
                    repaint();
                    return;
                }
                // The first entry into a native village: its woodcut on
                // the key, before the move (W9; landfall #15424, no slide).
                if (this.gui != null && !this.gui.villageEntryKey(u, d)) return;
                this.keyMoveUnit = u;
                // A unit that boards a ship hands over to the ship (spec
                // delta W18): the GUI picks the carrier when the controller
                // asks for the next unit, which it does inside moveUnit.
                final boolean boarding = this.gui != null
                    && u.getMoveType(d) == Unit.MoveType.EMBARK;
                if (boarding) this.gui.unitBoarding(u);
                try {
                    this.freeColClient.getInGameController().moveUnit(u, d);
                } finally {
                    this.keyMoveUnit = null;
                    if (boarding) this.gui.unitBoarding(null);
                }
                if (ClassicFrameRecorder.on()) {
                    ClassicFrameRecorder.event("move-done", "unit=" + u.getId()
                        + " at=" + xy(u.getTile()) + " moves=" + u.getMovesLeft());
                }
                // No view test on arrival: the view rule ran on the source
                // tile before the slide (animateMove, build spec W4).
                // The slide's final draw now that the model has the move
                // and its reveal, not when the event queue gets to it; the
                // panel's refresh follows a tick later (M1 acceptance F2).
                if (this.finalDrawPending) {
                    paintNow(null);
                    if (!this.finalDrawPending && this.gui != null) {
                        this.gui.panelAfterFinalDraw(this.lastFinalNanos);
                    }
                } else if (u.getTile() != null) {
                    repaint();
                }
            }
            return;
        }
        if (this.viewMode == GUI.ViewMode.TERRAIN && this.selectedTile != null) {
            final Direction d = intentToDirection(intent, this.selectedTile);
            if (d != null) {
                final Tile n = this.selectedTile.getNeighbourOrNull(d);
                // The cursor stays on the drawn map, off the outer ring.
                if (n != null && !n.isOuterRing()) this.gui.selectTile(n);
            }
            return;
        }
        // Nothing selected: nothing happens.
        if (ClassicFrameRecorder.on()) {
            ClassicFrameRecorder.event("key-ignored", intent + " mode=" + this.viewMode);
        }
    }

    /** "x,y" of a tile for the recorder's events, "-" for none. */
    private static String xy(Tile t) {
        return (t == null) ? "-" : t.getX() + "," + t.getY();
    }


    // View-state accessors, delegated to from ClassicGUI.

    GUI.ViewMode getViewMode() {
        return this.viewMode;
    }

    Unit getActiveUnit() {
        return this.activeUnit;
    }

    Tile getSelectedTile() {
        return this.selectedTile;
    }

    /**
     * Get the focus tile: the tile in the view's cell
     * ({@link ClassicHud#UNIT_COL}, {@link ClassicHud#UNIT_ROW}), where a
     * recentred unit sits unless the view is clamped at the map's edge.
     * The first view is chosen lazily ({@link #viewOrigin}).
     *
     * @return The current focus {@code Tile}, or null if there is no map yet.
     */
    Tile getFocus() {
        final Map map = getMap();
        final int[] o = viewOrigin();
        if (map == null || o == null) return null;
        return map.getTile(
            ClassicHud.clamp(o[0] + ClassicHud.UNIT_COL, 0, map.getWidth() - 1),
            ClassicHud.clamp(o[1] + ClassicHud.UNIT_ROW, 0, map.getHeight() - 1));
    }

    /**
     * Centre the view on a tile, clamped to the map ({@link ClassicHud#viewFor}):
     * the game's start and a reconnect (the start ship at (56,42) of the
     * 58x72 map in cell (14,6), landfall #340), the centre command, a
     * minimap click.
     *
     * @param tile The tile, or null to keep the view.
     */
    void setFocus(Tile tile) {
        centreOn(tile, "focus");
        repaint();
    }

    /**
     * Mark the cached minimap raster stale so it is rebuilt before the next
     * paint.  Called from {@code ClassicGUI.refresh}/{@code refreshTile} — the
     * hooks the controllers fire when the model changes (exploration, new
     * settlements, unit movement) — so the minimap picks up map changes without
     * rebuilding on ordinary (pan/cursor) repaints.
     */
    void invalidateMinimap() {
        this.minimapDirty = true;
    }

    // Minimap accessors -- the in-game panel now draws the original's 56x39
    // window itself (ClassicHud.minimapOf); this raster is no longer shown.

    /** The cached whole-map minimap raster, rebuilt if stale; null if no map. */
    BufferedImage getMinimapImage() {
        if (this.minimapDirty || this.minimapCache == null) buildMinimap();
        return this.minimapCache;
    }

    /** Pixels-per-tile in {@link #getMinimapImage()} (integer, at least 1). */
    int getMinimapPixelsPerTile() {
        return this.minimapPPT;
    }

    /** Recentre the main view on the given map tile (clamped to the map). */
    void recenterOnTile(int tileX, int tileY) {
        final Map map = getMap();
        if (map == null) return;
        final Tile t = map.getTile(
            Math.max(0, Math.min(map.getWidth() - 1, tileX)),
            Math.max(0, Math.min(map.getHeight() - 1, tileY)));
        if (t != null) this.gui.setFocus(t);
    }

    /**
     * TERRAIN view mode: a tile is selected (see {@code GUI.changeView(Tile)}).
     * The view follows the cursor by the same jumps as a unit
     * ({@link #jumpIfNeeded}; I: the original's view mode was not recorded).
     */
    void changeToTerrain(Tile tile) {
        if (tile != this.selectedTile || this.viewMode != GUI.ViewMode.TERRAIN) {
            this.changeToShow = true;   // the cursor moves
        }
        clearPrompt("terrain", true);
        this.viewMode = GUI.ViewMode.TERRAIN;
        this.selectedTile = tile;
        this.activeUnit = null;
        this.visitedUnit = null;
        jumpIfNeeded(tile, "terrain");
        rearmBlink("terrain");
        repaint();
    }

    /**
     * MOVE_UNITS mode: an active unit is selected.  When it becomes active
     * the view jumps to it if it is in the view's margin or off the view
     * (build spec W4 (a): the turn start and the next unit, landfall jumps
     * #4-#13; the soldier at (14,1) in #5).  The controller also selects
     * the active unit again after each of its moves
     * ({@code InGameController.moveDirection}'s redisplay): that is the
     * arrival, so the view is not tested then (landfall #10077: the ship
     * reached row 10 and blinked there, the view jumped only at its next
     * move).  A jump paints the map and the minimap (its ring) at once, as
     * one cut.  The blink starts ON, the first OFF one half-period after
     * the panel refresh (build spec W3).
     */
    void changeToMoveUnits(Unit unit) {
        final Unit before = this.activeUnit;
        final boolean activated = unit != this.activeUnit
            || this.viewMode != GUI.ViewMode.MOVE_UNITS;
        // The Spielzugende square goes (left, or frozen until this
        // repaint: the first unit of the next turn, clip004 #2766).  Not
        // for no unit: the controller's "restore the active unit" after
        // our end's goto pass (doExecuteGotoOrders) is no activation.
        if (unit != null) clearPrompt("activate", true);
        this.viewMode = GUI.ViewMode.MOVE_UNITS;
        this.activeUnit = unit;
        this.visitedUnit = null;
        if (unit != null) this.lastUnit = unit;
        boolean jumped = false;
        if (unit != null && unit.getTile() != null) {
            this.selectedTile = unit.getTile();
            if (activated) jumped = jumpIfNeeded(unit.getTile(), "activate");
        }
        rearmBlink("activate");
        if (jumped) {
            paintNow(null);
            if (this.gui != null) this.gui.paintBlinkDot();
        } else if (activated && (aboard(unit) || aboard(before))) {
            // A passenger drawn instead of its ship, or the ship back in
            // its place: the cells change with the block (clip007 #3107,
            // landfall #13118), not when a repaint gets through.
            if (before != null && before != unit) paintCellNow(before.getTile());
            paintCellNow((unit == null) ? null : unit.getTile());
        } else {
            repaint();
        }
    }

    /** Whether a unit is a passenger on the map (drawn instead of its ship while active). */
    private static boolean aboard(Unit u) {
        return u != null && u.isOnCarrier() && u.getTile() != null;
    }

    /**
     * The original's view rule (build spec W4, landfall 02 section 9): the
     * view jumps, putting {@code tile} in cell (7,6) clamped to the map
     * ({@link ClassicHud#viewFor}), when the tile is in the view's margin
     * on a side where the map goes on ({@link ClassicHud#needsRecentre});
     * else it stays.  Tested on activation, and on the source tile when a
     * move is accepted (own and foreign, {@link #animateMove}), never on
     * arrival and never at the end of the turn.  The first view of a game
     * is centred on the tile.  A plain state change: the caller paints.
     *
     * @param tile The tile the unit (or the cursor) is on.
     * @param reason What asks (for the recorder's {@code view-jump}).
     * @return True if the view moved.
     */
    boolean jumpIfNeeded(Tile tile, String reason) {
        if (tile == null) return false;
        final Map map = tile.getMap();
        final int[] o = this.origin;
        if (map == null) return false;
        if (o != null && !ClassicHud.needsRecentre(map.getWidth(),
                map.getHeight(), o[0], o[1], tile.getX(), tile.getY())) {
            return false;
        }
        return centreOn(tile, reason);
    }

    /**
     * Whether {@link #jumpIfNeeded} would move the view for {@code tile}
     * now; a plain question (the hand-over plans its jump with it, build
     * spec W5e).
     *
     * @param tile The tile, or null.
     * @return True if the view would jump.
     */
    boolean wouldJump(Tile tile) {
        final Map map = (tile == null) ? null : tile.getMap();
        if (map == null) return false;
        final int[] o = this.origin;
        if (o != null && !ClassicHud.needsRecentre(map.getWidth(),
                map.getHeight(), o[0], o[1], tile.getX(), tile.getY())) {
            return false;
        }
        final int[] v = ClassicHud.viewFor(map.getWidth(), map.getHeight(),
                                           tile.getX(), tile.getY());
        return o == null || o[0] != v[0] || o[1] != v[1];
    }

    /**
     * The hand-over's jump (build spec W5e): the view moves to the next
     * unit's tile ahead of its panel block, the map and the minimap ring
     * painted at once, as one cut; the active unit (and so the panel's
     * block) stays as it is until the unit comes up.
     *
     * @param tile The next unit's tile.
     * @param reason What asks (for the recorder).
     * @return True if the view moved.
     */
    boolean jumpTo(Tile tile, String reason) {
        if (!jumpIfNeeded(tile, reason)) return false;
        paintNow(null);
        if (this.gui != null) this.gui.paintBlinkDot();
        return true;
    }

    /**
     * Put {@code tile} in cell (7,6) of the view, clamped to the map.
     *
     * @param tile The tile, or null to keep the view.
     * @param reason What asks (for the recorder).
     * @return True if the view moved.
     */
    private boolean centreOn(Tile tile, String reason) {
        final Map map = (tile == null) ? null : tile.getMap();
        if (map == null) return false;
        this.viewMap = map;
        return moveView(ClassicHud.viewFor(map.getWidth(), map.getHeight(),
                                           tile.getX(), tile.getY()),
                        reason, tile);
    }

    /**
     * Set the view origin; a hard cut, the next paint shows the new view
     * (landfall 02 section 5: the view never scrolls).
     *
     * @param v The new origin {x, y}, already clamped.
     * @param reason What moves it (for the recorder).
     * @param tile The tile it centres on, or null (a pan).
     * @return True if the view moved.
     */
    private boolean moveView(int[] v, String reason, Tile tile) {
        final int[] o = this.origin;
        if (o != null && o[0] == v[0] && o[1] == v[1]) return false;
        this.origin = v;
        this.changeToShow = true;
        // A jump repaints the whole map: a frozen Spielzugende square goes
        // with it (clip004 #5058); a live one stays on its tile.
        if (this.promptFrozen) clearPrompt("jump", true);
        if (ClassicFrameRecorder.on()) {
            ClassicFrameRecorder.event("view-jump", reason + " "
                + ((o == null) ? "-" : o[0] + "," + o[1]) + " -> "
                + v[0] + "," + v[1]
                + ((tile == null) ? "" : " tile=" + xy(tile)
                    + ((o == null) ? "" : " cell=" + (tile.getX() - o[0])
                        + "," + (tile.getY() - o[1]))
                    + " now=" + (tile.getX() - v[0]) + ","
                    + (tile.getY() - v[1])));
        }
        return true;
    }

    /**
     * The landing box sent {@code unit} ashore ("An Land gehen", build
     * spec W8b): its slide starts at {@code due} at the earliest.
     *
     * @param unit The passenger, or null to drop a landing whose slide did
     *     not come.
     * @param due The earliest start on the slide clock.
     */
    void landingSlide(Unit unit, long due) {
        this.landingUnit = unit;
        this.landingDue = due;
    }

    /**
     * @return When the last movement key was taken, on the slide clock
     *     ({@link #slideClock}); 0 before the first (the landing box is
     *     timed from it, build spec W8b).
     */
    long moveKeyNanos() {
        return this.moveKeyNanos;
    }

    /**
     * The unit a landing put ashore becomes the active unit, as the unit
     * that has just made its last move (master plan W18): no view test,
     * no paint (the map shows it already), and no blink, since it has no
     * moves left.  The ship it left is drawn as before, and the hand-over
     * to the next unit follows (build spec W5e).
     *
     * @param unit The landed unit.
     */
    void finishedUnit(Unit unit) {
        if (unit == null) return;
        this.viewMode = GUI.ViewMode.MOVE_UNITS;
        this.activeUnit = unit;
        this.lastUnit = unit;
        if (unit.getTile() != null) this.selectedTile = unit.getTile();
        rearmBlink("landed");
    }

    /**
     * Slide {@code unit}'s sprite from {@code srcTile} to {@code dstTile}:
     * the original's slide ({@link ClassicSlide}, build spec W2).
     *
     * <ul>
     *   <li>One native pixel per step, offsets 1..15 on an absolute
     *   schedule (16.43 ms per step, 13.25 ms with the classic pref
     *   {@code moveAccelerator}, read now), straight or diagonal
     *   ({@code (+-1,+-1)}), never mirrored; the source tile is restored on
     *   every step because every step repaints it.</li>
     *   <li>Then the hold at offset 15 (72 ms) and the return: offset 16 is
     *   the final draw, the next paint, which shows the unit on its new tile
     *   together with what the move revealed ({@link #handleMoveKey} paints
     *   it at once for a key move).</li>
     *   <li>Offset 0 first, one step before offset 1, when the view jumps
     *   for the move or the player's unit is not on screen at its source
     *   (blink OFF, a passenger leaving its ship).</li>
     *   <li>A slide that follows another one without a key (goto steps, AI
     *   moves) first paints the other's final draw if no paint has yet, and
     *   starts {@link ClassicSlide#gapNanos} after it.</li>
     *   <li>A native's move whose source fails the view rule first shows
     *   the cue, {@link #CUE_GAP_MS} after that final draw: the source's
     *   minimap pixel white, and the white square on it with the sprite
     *   hidden if the source is on the screen; the jump follows
     *   {@link #CUE_MS} later (build spec W19, {@link #showNativeCue}).</li>
     *   <li>Until its slide starts, a foreign unit stays at its source
     *   although the model has moved it ({@link #moveQueued}; M1
     *   acceptance F5).</li>
     *   <li>The passenger a landing sends ashore starts no earlier than
     *   {@link #LANDING_SLIDE_MS} after the landing box closed
     *   ({@link #landingSlide}, build spec W8b); its offset 0 shows it
     *   instead of its ship ({@link #paintOccupant}).</li>
     * </ul>
     *
     * <p>The controller delivers the hook on the EDT (an own move from
     * inside {@code moveUnit}, an AI move via {@code invokeLater} in
     * {@code InGameController.animateMoveHandler}), so the steps are pushed
     * with {@code paintImmediately} over the cells the sprite crosses --
     * the same blocking-EDT approach FreeCol's own {@code UnitMoveAnimation}
     * uses -- with a plain {@code repaint()} fallback should it ever be
     * called from another thread.  Painting suppresses the unit at its model
     * tile and draws the sliding icon instead (see {@link #paintOccupant} /
     * {@link #paintAnimatedUnit}).
     */
    void animateMove(Unit unit, Tile srcTile, Tile dstTile) {
        // The unit's queued move (moveQueued) is taken off when its slide
        // starts, or here when it does not slide at all.
        boolean taken = false;
        try {
            final boolean rec = ClassicFrameRecorder.on();
            if (unit == null || srcTile == null || dstTile == null
                || (!isShowing() && this.offscreen == null)
                // Fog: only animate moves the player can actually see.
                || (!srcTile.isExplored() && !dstTile.isExplored())) {
                if (rec) {
                    ClassicFrameRecorder.event("slide-skip", "unit="
                        + ((unit == null) ? "-" : unit.getId()) + " from="
                        + xy(srcTile) + " to=" + xy(dstTile));
                }
                return;
            }
            final boolean own = isOwn(unit);
            this.timedPaint = true;   // the preload waits (holdsPreload)
            // A frozen Spielzugende square stays on the screen until a paint
            // covers it: this slide's steps where they cross it, else its
            // final draw (landing-slow #5525, #2902/#6460); its minimap pixel
            // goes with the final draw.
            final boolean promptGone = this.promptFrozen && clearPrompt("slide", false);
            // A chained slide: draw the previous slide's final frame if no
            // paint has yet (this unit still at its source, F5), then keep
            // the pause after it.
            finalDraw();
            // A native move whose source fails the view rule gets its cue
            // first (build spec W19), 2-4 frames after the final draw.
            final boolean cue = needsNativeCue(own, isNative(unit),
                                               wouldJump(srcTile));
            if (unit != this.keyMoveUnit && this.lastFinalNanos != 0L) {
                try {
                    this.slideClock.waitUntil(this.lastFinalNanos + ((cue)
                            ? nanos(CUE_GAP_MS) : ClassicSlide.gapNanos(own)));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
            if (cue) showNativeCue(unit, srcTile);
            if (unit == this.landingUnit) {
                // The landed unit leaves its ship the original's pause after
                // the landing box closed (build spec W8b).
                this.landingUnit = null;
                try {
                    this.slideClock.waitUntil(this.landingDue);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
            final boolean shown = isShownAt(unit, srcTile);
            // The view rule on the SOURCE tile, now the move is accepted and
            // before the first step (build spec W4 (b)/(c), also for native
            // moves): a jump is the slide's offset 0, the whole map and the
            // minimap ring, and offset 1 follows one step later (landfall
            // #10269 -> #10270, #17712 -> #17714).  The jump frame shows the
            // unit at its source, a native on an unexplored tile included
            // (landing-slow #6482).
            final boolean jumped = jumpIfNeeded(srcTile,
                                                own ? "move" : "foreign-move");
            final int[] v = viewOrigin();
            final boolean redraw = startsAtOffsetZero(jumped, own, shown);
            final boolean fast = ClassicPrefs.get().is(ClassicPrefs.MOVE_ACCELERATOR);
            final long step = ClassicSlide.stepNanos(fast);
            if (rec) {
                ClassicFrameRecorder.event("slide-start", "unit=" + unit.getId()
                    + " owner=" + unit.getOwner().getNationId()
                    + " from=" + xy(srcTile) + " to=" + xy(dstTile)
                    + ((v == null) ? "" : " view=" + v[0] + "," + v[1]
                        + " cell=" + (srcTile.getX() - v[0]) + ","
                        + (srcTile.getY() - v[1]))
                    + " steps=" + ClassicSlide.LAST_STEP + "x"
                    + (fast ? ClassicSlide.FAST_STEP_MS : ClassicSlide.STEP_MS)
                    + "ms hold=" + ClassicSlide.HOLD_MS + "ms"
                    + (redraw ? " redraw" + (jumped ? "=jump" : "=hidden") : ""));
            }
            // The slide shows the unit ON: the blink holds until it ends.  A
            // key that came while OFF gets offset 0 at once (the slide's own
            // paint), and the minimap dot turns back to the nation colour
            // right after it; after a jump the minimap (its ring) is painted
            // with offset 0.
            final boolean minimap = this.blinkOff || jumped;
            this.blinkOff = false;
            final int dotStep = redraw ? 0 : 1;
            this.animUnit = unit;
            this.animFrom = srcTile;
            this.animTo = dstTile;
            this.animDx = dstTile.getX() - srcTile.getX();
            this.animDy = dstTile.getY() - srcTile.getY();
            moveDequeued(unit, srcTile, dstTile);   // the slide draws it now
            taken = true;
            try {
                // Offset 1 is due one step after offset 0's map paint (the
                // jump frame), not after the minimap and the event behind it.
                final long[] zeroShown = { 0L };
                ClassicSlide.run(this.slideClock, step, redraw, k -> {
                        this.animOffset = k;
                        // Offset 0 may come with a view jump: the whole map.
                        paintNow((k == 0) ? null : slideBounds());
                        if (k == 0) zeroShown[0] = System.nanoTime();
                        if (minimap && k == dotStep && this.gui != null) {
                            this.gui.paintBlinkDot();
                        }
                        if (rec) {
                            ClassicFrameRecorder.event("slide-step", k + "/"
                                + ClassicSlide.CELL);
                        }
                    }, () -> zeroShown[0]);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } finally {
                this.animUnit = null;
                this.animFrom = null;
                this.animTo = null;
                this.animOffset = 0;
                this.finalDrawPending = true;
                if (rec) {
                    ClassicFrameRecorder.event("slide-end", "unit=" + unit.getId());
                }
                // The blink restarts ON at the end of every slide; the panel
                // refresh after the final draw re-bases it (build spec W3).
                rearmBlink("slide");
                // The final draw: a key move paints it as soon as the model
                // has the move (handleMoveKey), a chained slide before its
                // own start, else the event queue does, with the minimap in
                // the same pass (Critic 4).
                if (SwingUtilities.isEventDispatchThread()) {
                    SwingUtilities.invokeLater(this::finalDraw);
                } else {
                    repaint();
                }
                if (promptGone && this.gui != null) this.gui.paintBlinkDot();
            }
        } finally {
            if (!taken) moveDequeued(unit, srcTile, dstTile);
            this.cueTile = null;
            this.cueSquare = false;
            this.timedPaint = false;
        }
    }

    /** {@code ms} in nanoseconds. */
    private static long nanos(double ms) {
        return Math.round(ms * 1_000_000.0);
    }

    /**
     * From a native's cue to its jump (build spec W19: 250 ms; landing-slow
     * 0.243-0.300 s, #6464 -&gt; #6481; clip004 14-16 frames off-screen, 23
     * in the margin; window 199-330 ms).
     */
    static final double CUE_MS = 250.0;

    /**
     * From the previous native's final draw to the cue: 2-4 frames
     * (landing-slow #6460 -&gt; #6464, #6501 -&gt; #6503, #8391 -&gt;
     * #8395, #8438 -&gt; #8440; clip004 #9058 -&gt; #9060; mean 2.8).  Two
     * frames here: the cue's own paints (the square, the minimap) put its
     * frame about one later (C3's first run, three frames: 4-5).
     */
    static final double CUE_GAP_MS = 2 * 1000.0 / ClassicFrameRecorder.HZ;

    /**
     * Whether a move gets the native-phase cue before its slide (build
     * spec W19): a native's move whose source tile fails the view rule
     * (the view will jump for it); no cue inside the view, none for the
     * player's own or a European's unit.  The pref "Indianer zeigen" gates
     * the whole slide ({@link ClassicGUI#animateUnitMove}).
     *
     * @param own The unit is the player's.
     * @param indian It is a native unit.
     * @param jump The view jumps for its source tile ({@link #wouldJump}).
     * @return True for a cue.
     */
    static boolean needsNativeCue(boolean own, boolean indian, boolean jump) {
        return !own && indian && jump;
    }

    /** Whether a unit is a native's. */
    private static boolean isNative(Unit unit) {
        return unit.getOwner() != null && unit.getOwner().isIndian();
    }

    /**
     * The native-phase cue (build spec W19): the source tile's minimap
     * pixel white, and if the tile is on the screen (in the view's margin)
     * the white 16x16 square on it with the sprite hidden (clip004 #9060;
     * the Spielzugende mode's square, landing-slow #6464), in one go; then
     * {@link #CUE_MS} until the jump, which paints both away.  The event
     * thread is held meanwhile, as by a slide.
     *
     * @param unit The native.
     * @param src Its move's source tile.
     */
    private void showNativeCue(Unit unit, Tile src) {
        final int[] o = this.origin;
        this.cueTile = src;
        this.cueSquare = o != null && cellInView(src, o);
        if (this.cueSquare) {
            final Rectangle r = new Rectangle(screenX(src.getX(), o[0]),
                screenY(src.getY(), o[1]), tileW(), tileH());
            final int m = ICON_MARGIN * scale();
            r.grow(m, m);
            paintNow(r);
        }
        if (this.gui != null) this.gui.paintBlinkDot();
        final long shownAt = System.nanoTime();   // the jump counts from here
        if (ClassicFrameRecorder.on()) {
            ClassicFrameRecorder.event("native-cue",
                ((this.cueSquare) ? "square" : "minimap")
                + " unit=" + unit.getId() + " owner=" + unit.getOwner().getNationId()
                + " at=" + xy(src) + ((o == null) ? ""
                    : " cell=" + (src.getX() - o[0]) + "," + (src.getY() - o[1])));
        }
        try {
            this.slideClock.waitUntil(shownAt + nanos(CUE_MS));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        // The jump's offset 0 paints the map and the minimap without it.
        this.cueTile = null;
        this.cueSquare = false;
    }

    /**
     * Whether a tile's cell is in the drawn view (15x12 cells from the
     * origin).
     *
     * @param t The tile.
     * @param o The view origin.
     * @return True if it is on the screen.
     */
    static boolean cellInView(Tile t, int[] o) {
        final int c = t.getX() - o[0], r = t.getY() - o[1];
        return c >= 0 && c < ClassicHud.VIEW_COLS && r >= 0 && r < ClassicHud.VIEW_ROWS;
    }

    /** @return The native cue's tile (its white minimap pixel), or null. */
    Tile cueTile() {
        return this.cueTile;
    }

    /**
     * How close to a blink toggle the background preload waits
     * ({@link #holdsPreload}): its paint must not share the machine with
     * an image decode.
     */
    static final double PRELOAD_TOGGLE_WINDOW_MS = 40.0;

    /**
     * Whether the background resource preload should wait now (M1
     * acceptance F6; {@code ResourceManager.setPreloadHold}): a slide (or
     * a native cue) runs, or a toggle of the blink or of the Spielzugende
     * square is due within {@link #PRELOAD_TOGGLE_WINDOW_MS}.  The game's
     * first 13 s overlap the preload, and its decoding made those paints
     * 2-5 times as slow (the first blink OFF 24-26 frames after the panel
     * instead of 23).  Any thread (the preload thread asks).
     *
     * @return True to hold the preload.
     */
    boolean holdsPreload() {
        if (this.timedPaint) return true;
        final long now = System.nanoTime();
        final long w = nanos(PRELOAD_TOGGLE_WINDOW_MS);
        final long h = nanos(ClassicBlink.HALF_PERIOD_MS);
        for (ClassicBlink b : new ClassicBlink[] { this.blink, this.promptBlink }) {
            final long[] n = b.next();
            if (n == null) continue;
            // The next toggle soon, or the last one (or the arm) just
            // posted: its paint is running.
            if (n[2] - now < w || now - (n[2] - h) < w) return true;
        }
        return false;
    }

    /**
     * A foreign move the server announced (build spec W19, M1 acceptance
     * F5): until its slide starts the unit is drawn at {@code src}, not at
     * the tile the model already has it on.  Any thread (the network
     * thread, before the update that moves the unit).
     *
     * @param unit The moving unit.
     * @param src Its source tile.
     * @param dst Its destination tile.
     */
    void moveQueued(Unit unit, Tile src, Tile dst) {
        if (unit == null || src == null || dst == null) return;
        synchronized (this.queuedMoves) {
            this.queuedMoves.computeIfAbsent(unit, u -> new java.util.ArrayDeque<>())
                .addLast(new Tile[] { src, dst });
        }
    }

    /**
     * Take a queued move off: its slide starts, or it does not slide.  The
     * oldest matching one goes.
     *
     * @param unit The unit.
     * @param src Its source tile.
     * @param dst Its destination tile.
     */
    void moveDequeued(Unit unit, Tile src, Tile dst) {
        if (unit == null) return;
        synchronized (this.queuedMoves) {
            final java.util.ArrayDeque<Tile[]> q = this.queuedMoves.get(unit);
            if (q == null) return;
            for (java.util.Iterator<Tile[]> it = q.iterator(); it.hasNext();) {
                final Tile[] m = it.next();
                if (m[0] == src && m[1] == dst) {
                    it.remove();
                    break;
                }
            }
            if (q.isEmpty()) this.queuedMoves.remove(unit);
        }
    }

    /**
     * Where a unit waiting for its slide is drawn: its next move's source.
     *
     * @param unit The unit.
     * @return The tile, or null if no move of it is queued.
     */
    Tile queuedSource(Unit unit) {
        synchronized (this.queuedMoves) {
            if (this.queuedMoves.isEmpty()) return null;
            final java.util.ArrayDeque<Tile[]> q = this.queuedMoves.get(unit);
            return (q == null || q.isEmpty()) ? null : q.peekFirst()[0];
        }
    }

    /**
     * A unit that waits on {@code tile} for its slide while the model has
     * already moved it away, or null.
     *
     * @param tile The tile.
     * @return The unit, or null.
     */
    private Unit queuedAt(Tile tile) {
        synchronized (this.queuedMoves) {
            if (this.queuedMoves.isEmpty()) return null;
            for (java.util.Map.Entry<Unit, java.util.ArrayDeque<Tile[]>> e
                     : this.queuedMoves.entrySet()) {
                final Unit u = e.getKey();
                final Tile[] m = e.getValue().peekFirst();
                if (m != null && m[0] == tile && u != this.animUnit
                    && u.getTile() != tile
                    && u.getType() != null && u.getOwner() != null) return u;
            }
        }
        return null;
    }

    /**
     * Paint now on the EDT ({@code paintImmediately}), else ask for a
     * repaint.
     *
     * @param r The area, or null for the whole map.
     */
    private void paintNow(Rectangle r) {
        final Rectangle a = (r == null) ? new Rectangle(0, 0, getWidth(), getHeight()) : r;
        final BufferedImage off = this.offscreen;
        if (off != null) {
            final Graphics2D g = off.createGraphics();
            try {
                g.setClip(a);
                paintComponent(g);
            } finally {
                g.dispose();
            }
        } else if (SwingUtilities.isEventDispatchThread()) {
            paintImmediately(a);
        } else {
            repaint(a);
        }
    }

    /**
     * The final draw of the last slide, if no paint has shown it yet: the
     * whole map now (offset 16, with what the move revealed), then the
     * panel's minimap in the same pass, so it lights with the reveal
     * (fog-start: the same frame in 63 of 80 reveals, one later in 17).
     * Every path that ends a slide comes here (Critic 4) but the key
     * move's, which paints the minimap in {@link ClassicGUI#panelAfterFinalDraw}.
     * EDT only.
     */
    void finalDraw() {
        if (!this.finalDrawPending) return;
        paintNow(null);
        if (!this.finalDrawPending && this.gui != null) this.gui.paintBlinkDot();
    }

    /**
     * Tests: paint into an image instead of the screen ({@link #offscreen}),
     * the viewer sized to it.
     *
     * @param img The image, or null for the screen.
     */
    void paintOffscreen(BufferedImage img) {
        this.offscreen = img;
        if (img != null) setSize(img.getWidth(), img.getHeight());
    }

    /**
     * The screen area a slide changes: its source and destination cells
     * (and every cell between, for the isometric model's two-row steps),
     * plus the icon's reach past them ({@link #ICON_MARGIN}).
     *
     * @return The area, or null (the whole map) without a view.
     */
    private Rectangle slideBounds() {
        final int[] o = this.origin;
        final Tile a = this.animFrom, b = this.animTo;
        if (o == null || a == null || b == null) return null;
        final Rectangle r = new Rectangle(screenX(a.getX(), o[0]),
            screenY(a.getY(), o[1]), tileW(), tileH());
        r.add(new Rectangle(screenX(b.getX(), o[0]),
            screenY(b.getY(), o[1]), tileW(), tileH()));
        final int m = ICON_MARGIN * scale();
        r.grow(m, m);
        return r;
    }

    /**
     * Whether a slide paints offset 0 first, one step before offset 1: when
     * the view jumps for it (the jump frame, then offset 1; landfall #10269
     * -&gt; #10270), or when the player's own unit is not on screen at its
     * source (a key while the blink is OFF, #5112 -&gt; #5115 -&gt; #5117; a
     * passenger drawn over its ship as it leaves, #11577).  A foreign unit
     * that was not on screen appears at offset 1 (#9594).
     *
     * @param jumped The view jumps for the slide.
     * @param own The unit is the player's.
     * @param shown The unit is on screen at its source.
     * @return True for offset 0 first.
     */
    static boolean startsAtOffsetZero(boolean jumped, boolean own, boolean shown) {
        return jumped || (own && !shown);
    }

    /** Whether a unit is the player's own. */
    private boolean isOwn(Unit unit) {
        final Player me = (this.freeColClient == null) ? null
            : this.freeColClient.getMyPlayer();
        return me != null && unit.getOwner() == me;
    }

    /**
     * Whether the map shows {@code unit} on {@code tile} now: its icon is
     * the one drawn there and its tile is not blinked off.
     *
     * @param unit The unit.
     * @param tile The tile.
     * @return True if the unit's icon is on screen there.
     */
    boolean isShownAt(Unit unit, Tile tile) {
        if (tile.getSettlement() != null || !shownExplored(tile)) return false;
        if (this.blinkOff && this.activeUnit != null
            && this.activeUnit.getTile() == tile) return false;
        return displayUnit(tile) == unit;
    }

    /**
     * Set the active unit's blink state and paint it at once: its tile
     * (bare while OFF), and the panel's minimap dot
     * ({@link ClassicGUI#paintBlinkDot}).  The clock drives it
     * ({@link #blinkToggle}); a slide, a hold and every re-arm set it ON.
     *
     * @param off True to show the active unit's tile bare.
     */
    void setBlinkOff(boolean off) {
        if (off == this.blinkOff) return;
        this.blinkOff = off;
        this.changeToShow = true;
        final long a = System.nanoTime();
        paintBlinkCell();
        final long b = System.nanoTime();
        if (this.gui != null) this.gui.paintBlinkDot();
        this.togglePaintNs = new long[] { b - a, System.nanoTime() - b };
    }

    /** How long the last blink paint took: {the cell, the minimap dot} (ns), for the recorder. */
    private long[] togglePaintNs = { 0L, 0L };

    /**
     * Whether the active unit's blink is OFF.
     *
     * @return True while its tile is drawn bare.
     */
    boolean isBlinkOff() {
        return this.blinkOff;
    }

    /**
     * Whether the blink clock runs (an active unit in MOVE_UNITS).
     *
     * @return True while armed.
     */
    boolean isBlinkArmed() {
        return this.blink.isArmed();
    }

    /**
     * Whether the blink is held ON ({@link #holdBlink}, or a toggle found
     * a hold).
     *
     * @return True while held.
     */
    boolean isBlinkHeld() {
        return this.blinkHeld;
    }

    /**
     * Restart the blink: ON now, the first OFF one half-period later (or
     * after the panel refresh that follows, {@link #blinkPanelPainted}).
     * The counter is reset, not resumed (landfall #7703 / #8163 / #8187).
     * Without an active unit in MOVE_UNITS, or once it has no moves left
     * or was skipped, the clock stops instead: after its last move (or
     * Space, clip007 #1397, #1528) the unit stays on screen and does not
     * blink, through the pause before the next unit or the end of turn
     * (landfall 03 section 1, {@code ship_w1.png}).
     *
     * @param reason What re-arms it (for the recorder's events).
     */
    void rearmBlink(String reason) {
        if (this.disposed) return;   // a late close after the teardown
        this.blinkHeld = false;
        setBlinkOff(false);
        final Unit u = this.activeUnit;
        if (this.viewMode == GUI.ViewMode.MOVE_UNITS && u != null
            && u.getTile() != null && u.getMovesLeft() > 0
            && u.getState() != Unit.UnitState.SKIPPED) {
            this.blink.arm();
            if (ClassicFrameRecorder.on()) {
                ClassicFrameRecorder.event("blink", "arm " + reason + " unit="
                    + u.getId() + " at=" + xy(u.getTile()));
            }
        } else if (this.blink.isArmed()) {
            this.blink.stop();
            ClassicFrameRecorder.event("blink", "stop " + reason);
        }
    }

    /**
     * Hold the blink ON from now: a dialog, a menu or the first scene
     * opens over the map (a key while OFF opening a box first redraws the
     * unit ON, landfall #11146 -&gt; #11151 -&gt; #11154).  EDT only.
     *
     * @param reason What holds it.
     */
    void holdBlink(String reason) {
        if (this.disposed) return;
        if (!this.blinkHeld) {
            this.blinkHeld = true;
            ClassicFrameRecorder.event("blink", "hold " + reason);
        }
        setBlinkOff(false);
        holdPrompt(reason);
    }

    /**
     * A box that held the blink has closed: re-arm it now, ON, unless
     * something else still holds it (then the first toggle after that
     * hold re-arms it).  EDT only.
     *
     * @param reason What closed.
     */
    void resumeBlink(String reason) {
        if (this.disposed) return;
        if (blinkHoldReason() == null) {
            rearmBlink(reason);
            resumePrompt(reason);
        } else {
            this.blinkHeld = true;
        }
    }


    // The Spielzugende mode's square (build spec W17)

    /**
     * The Spielzugende mode begins: the white square on {@code tile}, ON
     * now, and its clock started (the first OFF one half-period later,
     * landing-slow #2306 -&gt; #2328 = 23 frames).  Painted at once, with the
     * panel's tile mode ({@link ClassicGUI}).
     *
     * @param tile The cursor tile, or null for none.
     */
    void enterPrompt(Tile tile) {
        this.promptBlink.stop();
        this.promptTile = null;
        if (tile == null) return;
        this.promptTile = tile;
        this.promptOff = false;
        this.promptFrozen = false;
        this.promptHeld = blinkHoldReason() != null;
        if (!this.promptHeld) this.promptBlink.arm();
        this.changeToShow = true;
        ClassicFrameRecorder.event("prompt", "on " + xy(tile)
            + (this.promptHeld ? " held" : ""));
        paintPromptCell();
    }

    /**
     * A toggle of the square's clock (the event thread): OFF for odd
     * {@code n}, ON for even, while nothing holds it; a hold freezes the
     * phase (landing-slow #4731-#5320, clip004 #4028-#4726), and the first
     * toggle after a hold that ended without a close hook restarts it.
     *
     * @param n The toggle.
     */
    void promptToggle(int n) {
        if (this.promptTile == null || this.promptFrozen) {
            this.promptBlink.stop();
            return;
        }
        if (blinkHoldReason() != null) {
            if (!this.promptHeld) {
                this.promptHeld = true;
                ClassicFrameRecorder.event("prompt", "hold");
            }
            return;
        }
        if (this.promptHeld) {
            resumePrompt("toggle");
            return;
        }
        final boolean off = ClassicBlink.isOff(n);
        final long late = System.nanoTime()
            - ClassicBlink.dueNanos(this.promptBlink.phaseStart(), n);
        setPromptOff(off);
        if (ClassicFrameRecorder.on()) {
            ClassicFrameRecorder.event("prompt", (off ? "off" : "on")
                + " n=" + n + " at=" + xy(this.promptTile)
                + String.format(Locale.ROOT, " late=%.2fms", late / 1e6));
        }
    }

    /** A box or a menu opens in the mode: the square's phase is frozen. */
    private void holdPrompt(String reason) {
        if (this.promptTile == null || this.promptFrozen || this.promptHeld) return;
        this.promptHeld = true;
        this.promptBlink.stop();
        ClassicFrameRecorder.event("prompt", "hold " + reason);
    }

    /**
     * The box or menu has closed: the square (and the word) restart ON one
     * frame after the close's restore, then blink on.
     */
    private void resumePrompt(String reason) {
        if (this.promptTile == null || this.promptFrozen) return;
        this.promptHeld = false;
        this.promptBlink.armDelayed(PROMPT_RESTART_MS);
        ClassicFrameRecorder.event("prompt", "restart " + reason);
    }

    /**
     * Set the square's phase and paint it at once: the cell, and the
     * panel's word and minimap pixel ({@link ClassicGUI#paintPromptPanel}).
     */
    private void setPromptOff(boolean off) {
        if (off == this.promptOff) return;
        this.promptOff = off;
        this.changeToShow = true;
        paintPromptCell();
    }

    /**
     * The end command: the square ON at once (out of rhythm if it was
     * OFF, landing-slow #5500) and frozen there.
     */
    void freezePrompt() {
        if (this.promptTile == null) return;
        this.promptFrozen = true;
        this.promptHeld = false;
        this.promptBlink.stop();
        ClassicFrameRecorder.event("prompt", "freeze " + xy(this.promptTile));
        if (this.promptOff) {
            this.promptOff = false;
            this.changeToShow = true;
        }
        paintPromptCell();
    }

    /**
     * The square goes: the mode was left, or the paint that covers a frozen
     * square comes.  A live mode's panel goes back to the unit block; a
     * frozen one keeps the word until the wipe.
     *
     * @param why What clears it (for the recorder).
     * @param paint Ask for the map's repaint and paint the panel's minimap
     *     pixel now; false when the caller's own paints do it (a slide).
     * @return True if there was a square.
     */
    boolean clearPrompt(String why, boolean paint) {
        if (this.promptTile == null) return false;
        final boolean frozen = this.promptFrozen;
        this.promptTile = null;
        this.promptOff = false;
        this.promptFrozen = false;
        this.promptHeld = false;
        this.promptBlink.stop();
        ClassicFrameRecorder.event("prompt", "clear " + why + (frozen ? " frozen" : ""));
        if (paint) repaint();
        if (this.gui != null) this.gui.promptCleared(frozen, paint);
        return true;
    }

    /**
     * Paint the square's cell now, and the panel's word and minimap
     * pixel; a pending final draw paints the whole map instead.
     */
    private void paintPromptCell() {
        final Tile t = this.promptTile;
        final int[] o = this.origin;
        if (this.finalDrawPending || t == null || o == null) {
            repaint();
        } else {
            paintNow(new Rectangle(screenX(t.getX(), o[0]), screenY(t.getY(), o[1]),
                                   tileW(), tileH()));
        }
        if (this.gui != null) this.gui.paintPromptPanel();
    }

    /** @return The Spielzugende mode's cursor tile, or null. */
    Tile promptTile() {
        return this.promptTile;
    }

    /** @return Whether the square is drawn now (ON, or frozen). */
    boolean isPromptShown() {
        return this.promptTile != null && (this.promptFrozen || !this.promptOff);
    }

    /** @return Whether the square is frozen by the end command. */
    boolean isPromptFrozen() {
        return this.promptTile != null && this.promptFrozen;
    }

    /** @return Whether the square's phase is frozen by a box or a menu. */
    boolean isPromptHeld() {
        return this.promptHeld;
    }

    /** @return Whether the square's clock runs. */
    boolean isPromptArmed() {
        return this.promptBlink.isArmed();
    }

    /**
     * The tile the Spielzugende mode shows when no village decides it: the
     * last active unit's tile (the target of its last move; landing-slow
     * #2306, #6182, clip004 #1973), else the view's focus tile (the tile
     * mode at a turn start, clip005 #18186).
     *
     * @return The tile, or null without a map.
     */
    Tile promptTileFor() {
        final Unit u = this.lastUnit;
        if (u != null && !u.isDisposed() && u.getTile() != null) return u.getTile();
        return getFocus();
    }

    /**
     * The panel was painted: the panel refresh after an arm re-bases the
     * blink's phase (the original's first OFF comes one half-period after
     * it).  Called by {@link ClassicInfoPanel} on every paint.
     */
    void blinkPanelPainted() {
        if (this.blink.panelPainted() && ClassicFrameRecorder.on()) {
            ClassicFrameRecorder.event("blink", "rebase panel");
        }
    }

    /**
     * Why the blink must show the unit ON now, or null to let it blink:
     * the map is not on screen, a slide runs, or the GUI has a box, a
     * menu, the first scene or the AI phase up
     * ({@link ClassicGUI#blinkHoldReason}).
     *
     * @return The reason, or null.
     */
    String blinkHoldReason() {
        if (this.animUnit != null) return "slide";
        if (this.gui != null) {
            if (!isShowing()) return "hidden";
            final String r = this.gui.blinkHoldReason();
            if (r != null) return r;
        }
        return null;
    }

    /**
     * A toggle of the blink clock is due (the event thread; the clock has
     * dropped toggles of an older phase).  Held: show the unit ON and wait
     * for the hold to end, which re-arms.  A unit with no moves left or
     * skipped stops it, as {@link #rearmBlink} does (FreeCol's trade route
     * can leave a unit SKIPPED with moves, no box: its toggles re-based
     * the hand-over after it).
     *
     * @param n The toggle: OFF for odd, ON for even.
     */
    void blinkToggle(int n) {
        final Unit u = this.activeUnit;
        if (this.viewMode != GUI.ViewMode.MOVE_UNITS || u == null
            || u.getTile() == null || u.getMovesLeft() <= 0
            || u.getState() == Unit.UnitState.SKIPPED) {
            rearmBlink("none");
            return;
        }
        final String hold = blinkHoldReason();
        if (hold != null) {
            holdBlink(hold);
            return;
        }
        if (this.blinkHeld) {
            rearmBlink("resume");
            return;
        }
        final boolean off = ClassicBlink.isOff(n);
        // How late the toggle runs, before its paint (the recorder's detail).
        final long late = System.nanoTime()
            - ClassicBlink.dueNanos(this.blink.phaseStart(), n);
        setBlinkOff(off);
        if (ClassicFrameRecorder.on()) {
            ClassicFrameRecorder.event("blink", (off ? "off" : "on")
                + " n=" + n + " unit=" + u.getId() + " at=" + xy(u.getTile())
                + String.format(Locale.ROOT, " late=%.2fms cell=%.1fms dot=%.1fms",
                    late / 1e6, this.togglePaintNs[0] / 1e6, this.togglePaintNs[1] / 1e6));
        }
    }

    /**
     * Paint the active unit's cell now (and the icon's reach past it), for
     * a blink toggle.  A pending final draw paints the whole map instead.
     */
    private void paintBlinkCell() {
        final Unit u = this.activeUnit;
        paintCellNow((u == null) ? null : u.getTile());
    }

    /**
     * Paint one tile's cell now, plus the icon's reach; the whole map
     * later while a final draw is pending or without the tile.
     *
     * @param t The tile, or null.
     */
    private void paintCellNow(Tile t) {
        final int[] o = this.origin;
        if (this.finalDrawPending || t == null || o == null) {
            repaint();
            return;
        }
        final Rectangle r = new Rectangle(screenX(t.getX(), o[0]),
            screenY(t.getY(), o[1]), tileW(), tileH());
        final int m = ICON_MARGIN * scale();
        r.grow(m, m);
        paintNow(r);
    }

    /**
     * The pack's art for the flags on the map's unit icons
     * ({@link ClassicHud#paintIcon}).
     *
     * @param font FONTTINY, or null (flags without letters).
     * @param text The pack's texts, or null (the letter '-').
     */
    void setIconArt(ClassicFont font, ClassicText text) {
        this.iconFont = font;
        this.iconText = text;
        repaint();
    }

    /**
     * Whether a slide is running ({@link #animateMove}).
     *
     * @return True mid-slide.
     */
    boolean isAnimating() {
        return this.animUnit != null;
    }

    /** END_TURN mode: clear active unit and selected tile (no blink). */
    void changeToEndTurn() {
        this.viewMode = GUI.ViewMode.END_TURN;
        this.activeUnit = null;
        this.visitedUnit = null;
        this.selectedTile = null;
        rearmBlink("end-turn");
        repaint();
    }

    /**
     * A silent visit (master plan W5f, c6 #3447, #4555, #4589): the view
     * jumps to the unit if it needs to, the map and the minimap ring in one
     * paint, the unit drawn on top of its tile (I); the active unit and the
     * panel's block stay as they are.  An active unit that still blinks (a
     * visit that came at once after W, F, S or a goto order that stopped
     * with moves left) is drawn ON and held from now: the cycle has moved
     * past it, and the turn flow holds it until the next unit comes
     * ({@code ClassicTurnFlow.holdsBlink}, as Space stops it).
     *
     * @param unit The unit visited.
     */
    void visit(Unit unit) {
        if (unit == null || unit.getTile() == null) return;
        this.visitedUnit = unit;
        if (ClassicFrameRecorder.on()) {
            ClassicFrameRecorder.event("visit", "unit=" + unit.getId()
                + " at=" + xy(unit.getTile()) + " row=" + ClassicUnitCycle.ordersRowShown(unit)
                + " jump=" + wouldJump(unit.getTile()));
        }
        if (this.activeUnit != null && this.activeUnit != unit && this.blink.isArmed()) {
            holdBlink("visit");
        }
        if (!jumpTo(unit.getTile(), "visit")) {
            this.changeToShow = true;   // the unit on top of its tile
            paintCellNow(unit.getTile());
        }
    }

    /**
     * The visit's completion (c6 #3450: the letter R becomes '-' and the
     * new road's hub and spokes appear; #4556: the F's ink): the unit's
     * cell and its neighbours, into which the spokes reach, painted now.
     *
     * @param unit The unit visited.
     */
    void visitShown(Unit unit) {
        final Tile t = (unit == null) ? null : unit.getTile();
        final int[] o = this.origin;
        if (ClassicFrameRecorder.on() && unit != null) {
            ClassicFrameRecorder.event("visit", "shown unit=" + unit.getId()
                + " row=" + ClassicUnitCycle.ordersRowShown(unit)
                + ((t == null) ? "" : " road=" + ClassicUnitCycle.roadShown(t)));
        }
        // The completion is the change the next pause runs from.
        this.changeToShow = true;
        if (this.finalDrawPending || t == null || o == null) {
            repaint();
            return;
        }
        final Rectangle r = new Rectangle(screenX(t.getX() - 1, o[0]),
            screenY(t.getY() - 1, o[1]), 3 * tileW(), 3 * tileH());
        final int m = ICON_MARGIN * scale();
        r.grow(m, m);
        paintNow(r);
    }


    // Internals

    private Map getMap() {
        if (this.freeColClient == null) return this.viewMap;
        return (this.freeColClient.getGame() == null) ? null
            : this.freeColClient.getGame().getMap();
    }

    /**
     * Find a reasonable initial focus tile: the player's first settlement, else
     * their first unit, else their entry tile, else the map centre.
     */
    private Tile defaultFocus() {
        final Player player = (this.freeColClient == null) ? null
            : this.freeColClient.getMyPlayer();
        if (player != null) {
            final List<Settlement> settlements = player.getSettlementList();
            if (!settlements.isEmpty() && settlements.get(0).getTile() != null) {
                return settlements.get(0).getTile();
            }
            final Unit unit = player.getUnits().findFirst().orElse(null);
            if (unit != null && unit.getTile() != null) {
                return unit.getTile();
            }
            if (player.getEntryTile() != null) {
                return player.getEntryTile();
            }
        }
        final Map map = getMap();
        return (map == null) ? null
            : map.getTile(map.getWidth() / 2, map.getHeight() / 2);
    }

    /**
     * Integer up-scale from the native 16&times;16 tile to on-screen pixels,
     * chosen so roughly {@link #CLASSIC_VIEW_COLS} tile columns fit the
     * current viewer width — matching how much map the original's
     * 320&times;200 screen showed — and never below {@link #MIN_SCALE}.
     */
    private int scale() {
        if (this.fixedScale > 0) return this.fixedScale;
        final int w = getWidth();
        if (w <= 0) return MIN_SCALE;
        return Math.max(MIN_SCALE,
            Math.round((float) w / (CLASSIC_VIEW_COLS * TILE_SRC)));
    }

    /**
     * Put the viewer on the in-game HUD's 320x200 grid
     * ({@link ClassicHudPane}): tiles of exactly {@code 16 * s} pixels,
     * bypassing {@link #MIN_SCALE}, so the 240x192 map area shows the
     * original's 15x12 tiles at the same scale as the strip and the panel,
     * the view origin in the top-left cell, instead of the
     * half-tile-centred adaptive layout.
     *
     * @param s The HUD scale, or 0 for the adaptive scale.
     */
    void setFixedScale(int s) {
        if (s == this.fixedScale) return;
        this.fixedScale = Math.max(0, s);
        repaint();
    }

    /**
     * The view origin: the top-left tile of the 15x12 HUD view, {x, y}.
     * Before the first view it is centred on a default tile (a settlement,
     * a unit, the entry tile, the map centre; {@link #setFocus} and the
     * first activation centre it on the unit).
     *
     * @return A copy of the origin, or null without a map.
     */
    int[] viewOrigin() {
        if (this.origin == null) centreOn(defaultFocus(), "default");
        final int[] o = this.origin;
        return (o == null) ? null : o.clone();
    }

    /**
     * {@link #viewOrigin} without choosing a default view: a plain read,
     * for the recorder's probe on another thread.
     *
     * @return {x, y}, or null while there is no view yet.
     */
    int[] peekViewOrigin() {
        final int[] o = this.origin;
        return (o == null) ? null : o.clone();
    }

    /** On-screen tile cell width (square, like the original game). */
    private int tileW() {
        return TILE_SRC * scale();
    }

    /** On-screen tile cell height (square, like the original game). */
    private int tileH() {
        return TILE_SRC * scale();
    }

    /**
     * Screen x of the left edge of the cell for map column {@code x}: on
     * the HUD grid the origin's column is cell 0; the adaptive layout
     * centres the origin's cell {@link ClassicHud#UNIT_COL}.
     *
     * @param x The map column.
     * @param vx The view origin's column.
     * @return The screen x.
     */
    private int screenX(int x, int vx) {
        if (this.fixedScale > 0) return (x - vx) * tileW();
        return getWidth() / 2 + (x - vx - ClassicHud.UNIT_COL) * tileW()
            - tileW() / 2;
    }

    /** Screen y of the top edge of the cell for map row {@code y} ({@link #screenX}). */
    private int screenY(int y, int vy) {
        if (this.fixedScale > 0) return (y - vy) * tileH();
        return getHeight() / 2 + (y - vy - ClassicHud.UNIT_ROW) * tileH()
            - tileH() / 2;
    }

    /**
     * Pan the view by {@code (dx, dy)} raw grid cells, clamped to the view
     * origins the map allows ({@link ClassicHud#clampView}: never past the
     * map's edge, build spec W4.7).
     *
     * <p>The classic viewer draws on a plain rectangular grid keyed on raw map
     * coordinates, so panning steps by raw {@code x}/{@code y} — not via
     * {@link net.sf.freecol.common.model.Direction} (whose isometric N/S steps
     * two rows), so the grid moves exactly one cell in the pressed
     * direction.
     *
     * @param dx The columns to pan.
     * @param dy The rows to pan.
     */
    void panView(int dx, int dy) {
        final Map map = getMap();
        final int[] o = viewOrigin();
        if (map == null || o == null) return;
        if (moveView(ClassicHud.clampView(map.getWidth(), map.getHeight(),
                                          o[0] + dx, o[1] + dy), "pan", null)) {
            repaint();
        }
    }

    /**
     * Update the edge-scroll direction from the current mouse position, starting
     * or stopping the repeating scroll timer as the mouse enters or leaves an
     * edge hot zone.
     */
    private void updateEdgeScroll(Point p) {
        // Edge-scroll hot zone: roughly a tile wide, so it is easy to hit
        // without being triggered by ordinary map clicks.
        final int margin = tileW();
        int dx = 0;
        int dy = 0;
        if (p.x < margin) dx = -1;
        else if (p.x >= getWidth() - margin) dx = 1;
        if (p.y < margin) dy = -1;
        else if (p.y >= getHeight() - margin) dy = 1;
        this.edgeDX = dx;
        this.edgeDY = dy;
        if ((dx == 0 && dy == 0) || this.disposed) {
            this.edgeScrollTimer.stop();
        } else if (!this.edgeScrollTimer.isRunning()) {
            this.edgeScrollTimer.start();
        }
    }

    /** Stop edge scrolling (mouse left the panel). */
    private void stopEdgeScroll() {
        this.edgeDX = 0;
        this.edgeDY = 0;
        this.edgeScrollTimer.stop();
    }

    /**
     * Release this viewer for good.  Called by {@code ClassicGUI.teardownInGame}
     * when the game is left for the title screen: the edge-scroll Swing
     * {@code Timer} and the blink clock's thread would otherwise keep
     * firing against a game that no longer exists.
     */
    void dispose() {
        this.disposed = true;
        stopEdgeScroll();
        this.blink.close();
        this.promptBlink.close();
        this.layer = null;
        this.oracle = null;
        this.shown = null;
        this.shownMap = null;
        this.shownAtFinal = null;
        this.viewMap = null;
        synchronized (this.queuedMoves) {
            this.queuedMoves.clear();
        }
    }

    /** Resolve the map {@link Tile} under a screen point, or null if off-map. */
    private Tile tileAt(int px, int py) {
        final Map map = getMap();
        final int[] o = viewOrigin();
        if (map == null || o == null) return null;
        // The inverse of screenX/screenY: the origin cell's top-left corner.
        final int ox = screenX(o[0], o[0]);
        final int oy = screenY(o[1], o[1]);
        final int x = o[0] + Math.floorDiv(px - ox, tileW());
        final int y = o[1] + Math.floorDiv(py - oy, tileH());
        return map.getTile(x, y);
    }

    /**
     * Map click → select the tile through the {@code GUI}/controller path,
     * porting {@code SwingGUI.clickAt}: an unexplored tile just takes the focus;
     * an owned colony opens the colony panel; an owned unit becomes the active
     * unit (MOVE_UNITS); anything else selects the tile in TERRAIN mode.  Unlike
     * {@code SwingGUI}, a single click already terrain-selects (the rectangular
     * classic grid has no drag-vs-click ambiguity to disambiguate with a
     * double-click), which also arms the TERRAIN-mode cursor keys.
     */
    private void onClick(MouseEvent e) {
        if (inputBlocked()) {
            ClassicFrameRecorder.event("click-blocked", "");
            return;
        }
        final Tile tile = tileAt(e.getX(), e.getY());
        if (tile == null) return;
        requestFocusInWindow();
        clickOn(tile, this.freeColClient.getMyPlayer());
    }

    /**
     * What a click on a tile does ({@link #onClick}).  A native village
     * is centred and then its notice comes
     * ({@code ClassicGUI.showIndianSettlementPanel}, G1, an invention: the
     * original's reaction is in no clip; drop the post to go back to
     * centring only), posted, as the click is a press and the box must
     * not open inside it.  Package-private for the tests.
     *
     * @param tile The tile clicked.
     * @param player Our player, or null.
     */
    void clickOn(Tile tile, Player player) {
        if (this.gui != null && this.gui.turnPrompt()) {
            // The Spielzugende mode: only an own unit that can still move
            // takes a click, and becomes active (build spec W17 item 6, I);
            // the rest of the map is inert, as the arrows are.
            final Unit u = (tile.isExplored()) ? tile.getFirstUnit() : null;
            if (u != null && player != null && player.owns(u)
                && u.getMovesLeft() > 0) {
                this.gui.unitClicked(u);
            } else {
                ClassicFrameRecorder.event("click-ignored", "prompt " + xy(tile));
            }
            return;
        }
        if (!tile.isExplored()) { // Select (focus) unexplored tiles
            this.gui.setFocus(tile);
            return;
        }
        final Settlement settlement = tile.getSettlement();
        if (settlement != null) {
            if (settlement instanceof Colony && player != null
                && player.owns(settlement)) {
                this.gui.showColonyPanel((Colony) settlement, null);
            } else if (settlement instanceof IndianSettlement) {
                this.gui.setFocus(tile);
                final ClassicGUI g = this.gui;
                final IndianSettlement is = (IndianSettlement) settlement;
                SwingUtilities.invokeLater(() -> g.showIndianSettlementPanel(is));
            } else { // A foreign colony: just centre for now
                this.gui.setFocus(tile);
            }
            return;
        }
        final Unit unit = tile.getFirstUnit();
        if (unit != null && player != null && player.owns(unit)) {
            // Our unit active; the unit cycle goes on after it (W5f).
            this.gui.unitClicked(unit);
        } else if (unit != null) { // Someone else's unit: select the tile
            this.gui.setFocus(tile);
        } else { // Empty explored tile: terrain-select
            this.gui.selectTile(tile);
        }
    }


    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        final Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                           RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        final Map map = getMap();
        final int[] o = viewOrigin();
        if (map == null || o == null) {
            paintWaiting(g);
            return;
        }

        final int vx = o[0];
        final int vy = o[1];
        final boolean hud = this.fixedScale > 0;
        // The tiles to paint: on the HUD grid the 15x12 view and the ring
        // around it, whose icons may reach in; in the adaptive layout all
        // the panel can show around the origin's centre cell.
        final int cols = hud ? 0 : getWidth() / tileW() + 2;
        final int rows = hud ? 0 : getHeight() / tileH() + 2;
        final int x0 = hud ? vx - 1 : vx + ClassicHud.UNIT_COL - cols;
        final int x1 = hud ? vx + ClassicHud.VIEW_COLS : vx + ClassicHud.UNIT_COL + cols;
        final int y0 = hud ? vy - 1 : vy + ClassicHud.UNIT_ROW - rows;
        final int y1 = hud ? vy + ClassicHud.VIEW_ROWS : vy + ClassicHud.UNIT_ROW + rows;
        // A slide step repaints only the cells it crosses: skip the cells
        // whose terrain and icon cannot reach the clip.
        final Rectangle clip = g.getClipBounds();
        final int m = ICON_MARGIN * scale();
        // The explored state as shown: from the model on every full paint
        // outside a slide, kept through one (Critic 5) and through a
        // palette step's paint (Critic 3).
        final boolean palette = this.palettePaint;
        final boolean finalPaint = !palette && this.finalDrawPending
            && this.animUnit == null;
        if (this.shown == null || this.shownMap != map
            || (!palette && this.animUnit == null && (clip == null
                || clip.contains(0, 0, getWidth(), getHeight())))) {
            takeShown(map);
        }
        if (finalPaint) noteReveal();

        // Two passes: every cell's terrain first, then settlements and
        // units, so an icon's shadow or overhang lies on its neighbour's
        // terrain (the original: the 21-px village 1 px into the next cell).
        // On the HUD grid with the pack's index sheets the terrain is the
        // layer's, the 15x12 view in one blit (W6a).
        final boolean layered = hud && this.layer != null;
        if (layered) {
            this.layer.paint(g, this.shownTerrain.of(map), vx, vy, clip, scale());
        }
        for (int pass = (layered) ? 1 : 0; pass < 2; pass++) {
            for (int ty = y0; ty <= y1; ty++) {
                for (int tx = x0; tx <= x1; tx++) {
                    final int sx = screenX(tx, vx);
                    final int sy = screenY(ty, vy);
                    if (clip != null && !clip.intersects(sx - m, sy - m,
                            tileW() + 2 * m, tileH() + 2 * m)) continue;
                    final Tile tile = map.getTile(tx, ty);
                    if (pass == 1) {
                        if (tile != null) paintOccupant(g, tile, sx, sy);
                    } else if (tile == null) {
                        // Beyond the map edge.  The HUD's clamped view
                        // never gets there (the original draws nothing
                        // past it, landfall 02 section 6); the adaptive
                        // layout shows endless open sea, never a black
                        // band against the last column.
                        if (!hud) paintOpenSea(g, tx, ty, sx, sy);
                    } else {
                        paintTile(g, map, tile, sx, sy);
                    }
                }
            }
        }

        paintAnimatedUnit(g, vx, vy);
        // The original draws no box around the active unit; the cursor
        // marks only a selected tile (TERRAIN).
        if (isCursorShown()) paintCursor(g, vx, vy);
        // The Spielzugende mode's square, over terrain and sprite (W17).
        final Tile pt = this.promptTile;
        if (pt != null && isPromptShown()) {
            ClassicHud.paintPromptSquare(g, screenX(pt.getX(), vx),
                                         screenY(pt.getY(), vy), scale());
        }
        // The native cue's square on a source in the margin (W19), the
        // same square (clip004 #9060, landing-slow #6464).
        final Tile ct = this.cueTile;
        if (ct != null && this.cueSquare) {
            ClassicHud.paintPromptSquare(g, screenX(ct.getX(), vx),
                                         screenY(ct.getY(), vy), scale());
        }
        // A palette step's paint changes nothing of the game: the original's
        // palette frames have pixelsChanged=0 (W5a counts changed pixels),
        // and the change or final draw still due stays due (Critic 3).
        if (palette) return;
        boolean changed = this.changeToShow || this.animUnit != null;
        this.changeToShow = false;
        if (this.finalDrawPending && this.animUnit == null) {
            this.finalDrawPending = false;
            this.lastFinalNanos = System.nanoTime();
            ClassicFrameRecorder.event("final-draw", "");
            changed = true;
            // Land shown explored: the discovery's woodcut, after this
            // paint (W9; the GUI posts it, never a modal loop in a paint).
            if (this.landRevealed) {
                this.landRevealed = false;
                if (this.gui != null) this.gui.landSighted(this.lastFinalNanos);
            }
        }
        if (changed && this.gui != null) this.gui.screenChanged();
    }

    /**
     * Paint the sliding icon of an in-progress move (see
     * {@link #animateMove}): {@link #animOffset} native pixels from the
     * source cell toward the destination, on top of everything.
     */
    private void paintAnimatedUnit(Graphics2D g, int vx, int vy) {
        final Unit u = this.animUnit;
        final Tile from = this.animFrom;
        final Tile to = this.animTo;
        if (u == null || from == null || to == null) return;
        final int s = scale();
        final int sx = screenX(from.getX(), vx)
            + ClassicSlide.screenOffset(this.animOffset, s, this.animDx);
        final int sy = screenY(from.getY(), vy)
            + ClassicSlide.screenOffset(this.animOffset, s, this.animDy);
        paintUnit(g, u, sx, sy, carriesUnits(u)
                  ? ClassicHud.CARGO_MARKER : ClassicHud.NO_MARKER);
    }

    /**
     * Paint what stands on a tile, over every cell's terrain: the
     * settlement, else the unit in front (none while the active unit's
     * blink is OFF on this tile).
     */
    private void paintOccupant(Graphics2D g, Tile tile, int sx, int sy) {
        if (!shownExplored(tile)) return;
        final Settlement settlement = tile.getSettlement();
        if (settlement != null) {
            paintSettlement(g, settlement, sx, sy);
            return;
        }
        if (this.blinkOff && this.activeUnit != null
            && this.activeUnit.getTile() == tile) return;
        // The native cue's square hides the sprite under it (W19).
        if (this.cueSquare && tile == this.cueTile) return;
        // A passenger leaving its ship: offset 0 shows it instead of the
        // ship, as an active passenger is drawn (landfall #11577, clip007
        // #3048); the ship is back under the sprite from the steps on.
        if (this.animUnit != null && this.animOffset == 0 && tile == this.animFrom
            && !this.animUnit.isNaval() && !tile.isLand()) return;
        // A unit mid-slide is painted by paintAnimatedUnit instead.
        final Unit unit = displayUnit(tile);
        if (unit != null) paintUnit(g, unit, sx, sy, markerOf(unit, tile));
    }

    /**
     * The unit drawn on a tile: the active unit when it is there (also a
     * passenger offered aboard: it is drawn instead of its ship, clip007
     * #3107; but not one that has just boarded and sits sentried until the
     * hand-over, #4272), else the first unit, never the one mid-slide.  A
     * foreign unit whose announced move has not slid yet stays at that
     * move's source ({@link #moveQueued}).
     *
     * @param tile The tile.
     * @return The unit, or null.
     */
    Unit displayUnit(Tile tile) {
        final Unit a = this.activeUnit;
        if (a != null && a != this.animUnit && a.getTile() == tile
            && !(a.isOnCarrier() && a.getState() == Unit.UnitState.SENTRY)) return a;
        final Unit v = this.visitedUnit;
        if (v != null && v != this.animUnit && v.getTile() == tile
            && !v.isDisposed()) return v;
        for (Unit u : tile.getUnitList()) {
            if (u == this.animUnit) continue;
            final Tile from = queuedSource(u);
            if (from != null && from != tile) continue;   // not slid here yet
            return u;
        }
        return queuedAt(tile);
    }

    /** Whether a unit carries at least one unit (not counting one mid-slide). */
    private boolean carriesUnits(Unit unit) {
        if (!unit.isNaval()) return false;
        for (Unit u : unit.getUnitList()) {
            if (u != this.animUnit) return true;
        }
        return false;
    }

    /**
     * The second flag of a unit drawn on a tile: the cargo marker of a laden
     * ship, the stack marker of a land unit that stands over others or over
     * its carrier, else none.
     */
    int[] markerOf(Unit unit, Tile tile) {
        if (unit.isNaval()) {
            return carriesUnits(unit) ? ClassicHud.CARGO_MARKER
                : ClassicHud.NO_MARKER;
        }
        if (unit.getLocation() instanceof Unit) return ClassicHud.STACK_MARKER;
        for (Unit u : tile.getUnitList()) {
            if (u != unit && u != this.animUnit) return ClassicHud.STACK_MARKER;
        }
        return ClassicHud.NO_MARKER;
    }

    /**
     * Paint a unit's icon in the cell at {@code (sx, sy)}: on the HUD grid
     * 1:1 in native pixels with its shadow and flag, as the panel and the
     * original draw it ({@link ClassicHud#paintIcon}); in the adaptive
     * layout the bare sprite fitted to the cell.
     */
    private void paintUnit(Graphics2D g, Unit unit, int sx, int sy,
                           int[] marker) {
        if (this.lib == null) return;   // no art (tests)
        final BufferedImage img = this.lib.getScaledUnitImage(unit);
        if (this.fixedScale <= 0) {
            drawCentered(g, img, sx, sy);
            return;
        }
        final Player owner = unit.getOwner();
        final int s = scale();
        // The held letter until a visit shows the completion (W5f).
        final int orders = ClassicUnitCycle.ordersRowShown(unit);
        final Graphics2D gg = (Graphics2D) g.create();
        try {
            gg.translate(sx, sy);
            gg.scale(s, s);
            // The panel's icon (build spec W21a): the flag's side and the
            // sprite's place per sprite, the letter after the sprite.
            ClassicHud.paintIcon(gg, this.iconFont,
                ClassicHud.orderLetter(this.iconText, orders),
                fit16(img), ClassicHud.nationRgb(owner),
                ClassicHud.letterInk(orders, ClassicHud.nationDark(owner)),
                ClassicHud.unitRow(unit), 0, 0, marker);
        } finally {
            gg.dispose();
        }
    }

    /**
     * Paint a settlement in the cell at {@code (sx, sy)}: on the HUD grid
     * 1:1 in native pixels, centred, a wider sprite overhanging both sides
     * ({@link ClassicHud#settlementOffset}); in the adaptive layout fitted
     * to the cell.
     */
    private void paintSettlement(Graphics2D g, Settlement settlement,
                                 int sx, int sy) {
        if (this.lib == null) return;   // no art (tests)
        final BufferedImage art = this.lib.getScaledSettlementImage(settlement);
        if (art == null) return;
        // A colony's flag in its nation's colours, as on the panel (build
        // spec W21b; clip008 #14868, clip006 #9345: the Dutch flag orange).
        final BufferedImage img = (settlement instanceof Colony)
            ? ClassicHud.colonyFlag(art, settlement.getOwner()) : art;
        if (this.fixedScale <= 0) {
            drawCentered(g, img, sx, sy);
            return;
        }
        // The classic pack's settlements are up to 21x16; larger art is
        // FreeCol's fallback, fitted to the cell.
        final BufferedImage sp = (img.getWidth() <= TILE_SRC + 2 * ICON_MARGIN
            && img.getHeight() <= TILE_SRC) ? img : fit16(img);
        final int s = scale();
        g.drawImage(sp, sx + ClassicHud.settlementOffset(sp.getWidth()) * s, sy,
                    sp.getWidth() * s, sp.getHeight() * s, null);
    }

    /** A sprite fitted to 16x16 (cached; the pack's sprites as they are). */
    private BufferedImage fit16(BufferedImage img) {
        if (img == null || (img.getWidth() <= TILE_SRC
                            && img.getHeight() <= TILE_SRC)) return img;
        return this.fitted.computeIfAbsent(img, ClassicHud::fit16);
    }

    /**
     * Paint one tile's ground in the RGBA fallback (no index sheets, or
     * the adaptive layout; Critic 6): an unexplored tile flat in the dark
     * sea's colour ({@link #DARK_SEA}), else the base terrain and the
     * terrain-feature overlays (forest / hills / mountains / river / road /
     * plow / resource / lost-city -- item (e), {@link ClassicTileArt}),
     * without blends.  What stands on it comes in the second pass
     * ({@link #paintOccupant}).
     */
    private void paintTile(Graphics2D g, Map map, Tile tile, int sx, int sy) {
        if (!shownExplored(tile) || tile.getType() == null) {
            g.setColor(DARK_SEA);
            g.fillRect(sx, sy, tileW(), tileH());
            return;
        }
        if (this.lib == null) return;   // no art (tests)
        // Fetch the tile at its native 16x16 size and let the (nearest-neighbour)
        // scaling in paintComponent up-scale it, so classic pixels stay crisp.
        final BufferedImage terrain = this.lib.getTerrainImage(
            tile.getType(), tile.getX(), tile.getY(), SRC_SIZE);
        if (terrain != null) g.drawImage(terrain, sx, sy, tileW(), tileH(), null);

        // Composite the physical-feature overlays on top of the base terrain.
        this.tileArt.paintOverlays(g, map, tile, sx, sy, tileW(), tileH());
    }

    /**
     * Paint the endless-ocean filler of the adaptive layout's cells beyond
     * the map edge.  The coordinates lie off-map; they only seed the
     * per-tile texture variation ({@code floorMod} keeps them positive).
     */
    private void paintOpenSea(Graphics2D g, int x, int y, int sx, int sy) {
        final TileType ocean = oceanType();
        if (ocean == null || this.lib == null) return;
        final BufferedImage sea = this.lib.getTerrainImage(
            ocean, Math.floorMod(x, 1000), Math.floorMod(y, 1000), SRC_SIZE);
        if (sea != null) g.drawImage(sea, sx, sy, tileW(), tileH(), null);
    }

    /** The spec's plain ocean type, for the adaptive layout's off-map sea (lazy). */
    private TileType oceanType() {
        if (this.oceanType == null && this.freeColClient != null
            && this.freeColClient.getGame() != null
            && this.freeColClient.getGame().getSpecification() != null) {
            this.oceanType = this.freeColClient.getGame().getSpecification()
                .getTileType("model.tile.ocean");
        }
        return this.oceanType;
    }


    // The terrain layer (M1c design 10 §6, W6a)

    /**
     * Draw the HUD grid's terrain from palette indices: the layer of the
     * pack ({@link ClassicTerrainLayer#create}; null keeps the RGBA
     * fallback) and the true terrain of the fog ring
     * ({@code ClassicGUI.terrainOracle}).
     *
     * @param layer The layer, or null.
     * @param oracle The oracle, or null (the ring unknown, F-W6d-MP).
     */
    void setTerrain(ClassicTerrainLayer layer, ClassicTerrainOracle oracle) {
        this.layer = layer;
        this.oracle = oracle;
        repaint();
    }

    /** @return The terrain layer, or null (the RGBA fallback). */
    ClassicTerrainLayer terrainLayer() {
        return this.layer;
    }

    /**
     * The clock of this viewer's blocking waits on the event thread
     * ({@link #slideClock}): the water cycle's servicing clock in the game.
     *
     * @param clock The clock, or null for the system clock.
     */
    void setSlideClock(ClassicSlide.Clock clock) {
        this.slideClock = (clock == null) ? ClassicSlide.SYSTEM : clock;
    }

    /** @return The clock of the blocking waits ({@link #setSlideClock}). */
    ClassicSlide.Clock slideClock() {
        return this.slideClock;
    }

    /**
     * A step of the water cycle (M1c design 10 §7.2, W6c; the event
     * thread): draw the terrain at the new phase from now on and repaint
     * the cells whose last composition holds a cycling index (sea lanes,
     * rivers, swamps, beach corners and the fringes and blends next to
     * them), with the units over them.  Only while the map is on the
     * screen; behind a dialog, a menu or the Europe screen the clock runs
     * on all the same.  Nothing is composed anew but those cells.
     *
     * <p>The paint is silent ({@link #palettePaint}).  While a slide's final
     * draw is due, the step paints that final draw instead -- the whole map
     * at the new phase, which is due anyway (Critic 3) -- so it never shows
     * the unit at its target without the move's reveal.  With no cycling
     * cell in the view nothing is painted: every phase looks alike there.
     *
     * @param phase The new phase.
     * @param k The cycle's step (for the recorder).
     * @param due When it was due ({@code System.nanoTime} in the game).
     * @param serviced Fired by a blocking wait ({@link #slideClock}), not
     *     by the cycle's post.
     */
    void paletteStep(int phase, long k, long due, boolean serviced) {
        final ClassicTerrainLayer l = this.layer;
        if (l == null || this.disposed) return;
        final long start = System.nanoTime();
        l.setPhase(phase);
        final String how;
        boolean painted = false;
        if (!isShowing() && this.offscreen == null) {
            how = "hidden";   // a full paint follows when it shows again
        } else if (this.origin == null) {
            l.markShown();
            how = "no-view";
        } else if (this.finalDrawPending && this.animUnit == null) {
            finalDraw();
            how = "final-draw";
            painted = true;
        } else {
            final int[] c = l.cyclingCells();
            if (c == null) {
                l.markShown();
                how = "none";
            } else {
                final int cs = TILE_SRC * scale();
                this.palettePaint = true;
                try {
                    paintNow(new Rectangle(c[0] * cs, c[1] * cs,
                        (c[2] - c[0]) * cs, (c[3] - c[1]) * cs));
                } finally {
                    this.palettePaint = false;
                }
                how = "cells=" + c[0] + "," + c[1] + "-" + (c[2] - 1) + ","
                    + (c[3] - 1);
                painted = true;
            }
        }
        if (ClassicFrameRecorder.on()) {
            final long end = System.nanoTime();
            ClassicFrameRecorder.paletteStep(String.format(Locale.ROOT,
                "p=%d k=%d late=%.2fms dur=%.2fms via=%s %s", phase, k,
                (start - due) / 1e6, (end - start) / 1e6,
                (serviced) ? "wait" : "post", how), end - start, start - due,
                painted);
        }
    }

    /**
     * The phase the screen shows the terrain at (the recorder's frame
     * palette, design 10 §9.2.1).
     *
     * @return The layer's painted phase, or -1 without a layer.
     */
    int paintedPhase() {
        final ClassicTerrainLayer l = this.layer;
        return (l == null) ? -1 : l.paintedPhase();
    }

    /**
     * The recorder's index hint of the view (design 10 §9.2.4).
     *
     * @param out 240x192 bytes.
     * @return False without a layer (out untouched).
     */
    boolean indexHint(byte[] out) {
        final ClassicTerrainLayer l = this.layer;
        if (l == null) return false;
        // A woodcut covers the map: no pixel of it is the terrain (W9).
        if (this.gui != null && this.gui.woodcutCovers()) return false;
        l.indexHint(out);
        return true;
    }

    /** @return The game palette the terrain is drawn with, or null without a layer. */
    ClassicGamePalette gamePalette() {
        final ClassicTerrainLayer l = this.layer;
        return (l == null) ? null : l.palette();
    }

    /**
     * Whether a tile is drawn explored: the explored state as shown
     * ({@link #shown}), the model's before the first paint.
     *
     * @param tile The tile.
     * @return True if it is drawn explored.
     */
    boolean shownExplored(Tile tile) {
        final BitSet b = this.shown;
        final Map m = this.shownMap;
        if (b == null || m == null || tile.getMap() != m) return tile.isExplored();
        return b.get(tile.getY() * m.getWidth() + tile.getX());
    }

    /**
     * Take the explored state as shown from the model.  For a new map the
     * recorder notes it ({@code explored}: at the start the 3x3 around the
     * ship, design 10 §3), and it is the base of the first reveal.
     *
     * @param map The map.
     */
    private void takeShown(Map map) {
        final int w = map.getWidth(), h = map.getHeight();
        final BitSet b = new BitSet(w * h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                final Tile t = map.getTile(x, y);
                if (t != null && t.isExplored()) b.set(y * w + x);
            }
        }
        final boolean fresh = this.shownMap != map;
        this.shown = b;
        this.shownMap = map;
        if (fresh) {
            this.shownAtFinal = (BitSet)b.clone();
            if (ClassicFrameRecorder.on()) {
                ClassicFrameRecorder.event("explored", tileList(b, w));
            }
        }
    }

    /**
     * The final draw's reveal for the recorder ({@code reveal n= tiles=}):
     * the tiles shown explored now and not at the last final draw (design
     * 10 §9.2.5; the staircase, fog-start §2.1).
     */
    private void noteReveal() {
        final BitSet now = this.shown;
        if (now == null || this.shownMap == null) return;
        final BitSet add = (BitSet)now.clone();
        if (this.shownAtFinal != null) add.andNot(this.shownAtFinal);
        if (ClassicFrameRecorder.on()) {
            ClassicFrameRecorder.event("reveal", tileList(add, this.shownMap.getWidth()));
        }
        this.landRevealed |= revealsLand(this.shownMap, add);
        this.shownAtFinal = (BitSet)now.clone();
    }

    /**
     * Whether a final draw's reveal shows land as explored: the sighting
     * of the New World (woodcut 1, master plan W9; V: landfall #2107,
     * fog-start #1042).  Land only in the fog ring is not explored and
     * does not count (fog-start #876).
     *
     * @param map The map.
     * @param add The tiles newly shown explored ({@code y * width + x}).
     * @return True if one of them is land.
     */
    static boolean revealsLand(Map map, BitSet add) {
        if (map == null || add == null) return false;
        final int w = map.getWidth();
        for (int i = add.nextSetBit(0); i >= 0; i = add.nextSetBit(i + 1)) {
            final Tile t = map.getTile(i % w, i / w);
            if (t != null && t.isLand()) return true;
        }
        return false;
    }

    /**
     * When the last final draw was painted ({@code System.nanoTime}), 0
     * before the first: a woodcut's black comes 57 ms after it.
     *
     * @return The time.
     */
    long lastFinalNanos() {
        return this.lastFinalNanos;
    }

    /** "n=count tiles=x,y;x,y..." of a tile set, every tile listed (the analysis needs them). */
    private static String tileList(BitSet b, int w) {
        final StringBuilder sb = new StringBuilder("n=").append(b.cardinality())
            .append(" tiles=");
        for (int i = b.nextSetBit(0); i >= 0; i = b.nextSetBit(i + 1)) {
            if (sb.charAt(sb.length() - 1) != '=') sb.append(';');
            sb.append(i % w).append(',').append(i / w);
        }
        return sb.toString();
    }

    /**
     * The map as the layer composes it: the explored state as shown, the
     * client's types of explored tiles, the oracle's for the fog ring.
     */
    private final class ShownTerrain implements ClassicTerrainComposer.TerrainSource {

        /** The map painted. */
        private Map map;

        /** @return This, for {@code m}. */
        ShownTerrain of(Map m) {
            this.map = m;
            return this;
        }

        @Override
        public boolean onMap(int x, int y) {
            return this.map.getTile(x, y) != null;
        }

        @Override
        public boolean explored(int x, int y) {
            final Tile t = this.map.getTile(x, y);
            return t != null && shownExplored(t);
        }

        @Override
        public TileType type(int x, int y) {
            final Tile t = this.map.getTile(x, y);
            if (t == null) return null;
            if (t.getType() != null && shownExplored(t)) return t.getType();
            final ClassicTerrainOracle o = ClassicMapViewer.this.oracle;
            return (o == null) ? null : o.trueType(x, y);
        }

        @Override
        public void overlays(int x, int y, IntConsumer frames) {
            final Tile t = this.map.getTile(x, y);
            if (t != null && t.getType() != null) {
                ClassicTileArt.overlayFrames(this.map, t, this::type, frames);
            }
        }
    }

    /**
     * Draw a unit/settlement sprite centred within the tile cell at
     * {@code (sx, sy)}, sized to the cell (preserving aspect).  The
     * adaptive layout only; the HUD grid draws icons 1:1
     * ({@link #paintUnit}, {@link #paintSettlement}).
     *
     * <p>Two cases, distinguished by source size (which doubles as pack
     * detection):
     * <ul>
     *   <li><b>Small classic art</b> — the original ICONS.SS sprites are
     *   ~16&times;16, so they fit inside the 48px cell and would render tiny if
     *   drawn native.  They are <em>up-scaled</em> to {@link #UNIT_CELL_FRACTION}
     *   of the cell, nearest-neighbour (via the hint set in
     *   {@link #paintComponent}) so the chunky classic pixels stay crisp, like
     *   the terrain.</li>
     *   <li><b>Large FreeCol art</b> — the pack-absent fallback art is sized for
     *   FreeCol's 128&times;64 tiles, much bigger than our square cell, and is
     *   <em>shrunk</em> to fit as before.</li>
     * </ul>
     */
    private void drawCentered(Graphics2D g, BufferedImage img, int sx, int sy) {
        if (img == null) return;
        int w = img.getWidth();
        int h = img.getHeight();
        final int tw = tileW();
        final int th = tileH();
        final double s = (w <= tw && h <= th)
            ? Math.min(UNIT_CELL_FRACTION * tw / w,
                       UNIT_CELL_FRACTION * th / h)   // up-scale classic art
            : Math.min((double) tw / w, (double) th / h); // shrink to fit
        w = Math.max(1, (int) Math.round(w * s));
        h = Math.max(1, (int) Math.round(h * s));
        final int x = sx + (tw - w) / 2;
        final int y = sy + (th - h) / 2;
        g.drawImage(img, x, y, w, h, null);
    }

    /**
     * Whether the TERRAIN mode's cursor is drawn: a tile is selected, the
     * player is not waiting and the Spielzugende mode is off.  With no unit
     * left the controller selects its fallback tile (the first colony, else
     * the entry tile) and the turn ends by itself 485 ms later; the cursor
     * then flashed white for about 0.5 s in every turn without a unit (C
     * acceptance A3), which the original does not show.  While the player
     * waits (that end, the AI phase, a hand-over, our turn not yet shown)
     * no key moves the cursor anyway (build spec W5d), and the Spielzugende
     * mode has its own square (W17).
     *
     * @return True if the cursor is drawn.
     */
    boolean isCursorShown() {
        if (this.viewMode != GUI.ViewMode.TERRAIN || this.selectedTile == null) {
            return false;
        }
        return this.gui == null
            || !(this.gui.turnInputBlocked() || this.gui.turnPrompt());
    }

    /**
     * Highlight the selected tile (TERRAIN mode) with a cursor.  Not the
     * active unit: the original draws no box around it (build spec W2).
     */
    private void paintCursor(Graphics2D g, int vx, int vy) {
        final Tile cursor = this.selectedTile;
        if (cursor == null) return;
        final int sx = screenX(cursor.getX(), vx);
        final int sy = screenY(cursor.getY(), vy);
        final Stroke old = g.getStroke();
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(2f));
        g.drawRect(sx + 1, sy + 1, tileW() - 3, tileH() - 3);
        g.setStroke(old);
    }

    // Minimap raster (item (d)): a scaled whole-map overview, giving a navigation
    // aid the 48px main view cannot (it shows only a handful of tiles).  Not
    // isometric — a plain rectangular map.getWidth() x map.getHeight() raster,
    // unlike FreeCol's own iso MiniMap.  The raster is built here (it is map data);
    // it was drawn by the old ClassicInfoPanel; the HUD panel now builds its own
    // 1-px-per-tile window (ClassicHud.minimapOf), see the accessors above.

    /** {@code c} if non-null, else {@code fallback} (guards missing resources). */
    private static Color orElse(Color c, Color fallback) {
        return (c != null) ? c : fallback;
    }

    /**
     * Render the whole map into {@link #minimapCache}: one
     * {@link #minimapPPT}-px square per tile, background colour for unexplored
     * tiles, {@link ImageLibrary#getMinimapPoliticsColor} for explored terrain,
     * and the owner's nation colour for a tile carrying a settlement or unit.
     * Iterates every tile, so it runs only on a rebuild (see
     * {@link #invalidateMinimap}).
     */
    private void buildMinimap() {
        this.minimapDirty = false;
        final Map map = getMap();
        if (map == null) { this.minimapCache = null; return; }
        final int w = map.getWidth();
        final int h = map.getHeight();
        if (w <= 0 || h <= 0) { this.minimapCache = null; return; }

        final int ppt = Math.max(1, Math.min(MINIMAP_MAX / w, MINIMAP_MAX / h));
        this.minimapPPT = ppt;
        final Color bg = orElse(ImageLibrary.getMinimapBackgroundColor(),
                                new Color(0x0a2a3a));
        final BufferedImage img = new BufferedImage(w * ppt, h * ppt,
                                                    BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g = img.createGraphics();
        g.setColor(bg);
        g.fillRect(0, 0, w * ppt, h * ppt);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                final Tile tile = map.getTile(x, y);
                if (tile == null || !tile.isExplored()) continue;
                Color c = orElse(
                    ImageLibrary.getMinimapPoliticsColor(tile.getType()), bg);
                final Settlement s = tile.getSettlement();
                if (s != null && s.getOwner() != null) {
                    c = orElse(s.getOwner().getNationColor(), c);
                } else {
                    final Unit u = tile.getFirstUnit();
                    if (u != null && u.getOwner() != null) {
                        c = orElse(u.getOwner().getNationColor(), c);
                    }
                }
                g.setColor(c);
                g.fillRect(x * ppt, y * ppt, ppt, ppt);
            }
        }
        g.dispose();
        this.minimapCache = img;
    }

    private void paintWaiting(Graphics2D g) {
        g.setColor(Color.LIGHT_GRAY);
        g.setFont(g.getFont().deriveFont(Font.PLAIN, 18f));
        g.drawString(Messages.message("classic.mapViewer.waitingForMap"), 24, 32);
    }
}
