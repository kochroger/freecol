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
import java.util.List;
import java.util.Locale;
import java.util.WeakHashMap;

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
 * <p><b>Phase 1.</b> Terrain is drawn from the original Colonization
 * {@code TERRAIN.SS} sprites — square 16&times;16 tiles, aliased onto FreeCol's
 * {@code image.tile.<type>.center} keys by the {@code classic_original} pack
 * (see {@code tools/classic_assets/aliases.properties}) — so the rectangular
 * grid fills cleanly with no diamond-shaped gaps. The native 16&times;16 tiles
 * are fetched at source size and up-scaled by the adaptive integer factor
 * {@code scale()} (targeting the original's ~15 visible tile columns) with
 * nearest-neighbour interpolation to keep the chunky classic pixels crisp. When
 * the pack is absent the same keys fall back to FreeCol's own (isometric) art,
 * shrunk into the square cells. See CLASSIC_UI_PLAN.md ("Phase 1").
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
     * Width, in native ({@link #TILE_SRC}-scale) pixels, of the dithered
     * land-land border blend (see {@link #blendLandBorders}).
     */
    private static final int BORDER_BAND = 3;

    /**
     * Probability a pixel right at the shared edge blends toward the
     * neighbour, tapering linearly to 0 over {@link #BORDER_BAND} rows.
     * Kept deliberately low -- the reference original renders land-land
     * borders as sparse, uneven speckling, not a dense band -- see
     * {@link #hashNoise} for why the pattern is per-pixel noise rather than
     * a small repeating ordered-dither matrix. Land-land only: see
     * {@link #COAST_GAP_PROBABILITY} for the land-water case.
     */
    private static final float BORDER_DENSITY = 0.45f;

    /**
     * Probability a given lateral position along a land/water edge gets only
     * a single-pixel-deep water incursion, rather than a deeper reach of up
     * to {@link #BORDER_BAND} rows (see {@link #blendCoastEdge}). Unlike
     * land-land ({@link #BORDER_DENSITY}), land/water pixels are not
     * independently scattered: water is such a high-contrast colour swap
     * from any land texture that isolated water pixels deep in solid land
     * read as unnatural "flooded" potholes rather than texture noise, so
     * each lateral position instead gets one contiguous run from the edge --
     * a wavy but solid boundary line -- and row 0 (right at the shared edge)
     * is never skipped, so the coastline never gaps back to a hard land/water
     * step; only how far past row 0 it reaches varies.
     */
    private static final float COAST_GAP_PROBABILITY = 0.5f;

    /**
     * Approximate colour of the original's coastal foam/wave-crest fringe --
     * sampled directly from the shoreline in {@code screenshots/initial/
     * opening_007.png} (a fairly desaturated light grey, not a saturated
     * white or blue), used by {@link #foamEdge} in place of the sprite-based
     * coast quarter-tiles removed from {@link ClassicTileArt} (their
     * extracted colours didn't match this).
     */
    private static final int FOAM_R = 150, FOAM_G = 155, FOAM_B = 160;

    /**
     * Depth, in native pixels, of the procedural foam blend on the water side
     * of a coastline (see {@link #foamEdge}). Narrower than the land-side
     * {@link #BORDER_BAND} -- sampled against the reference screenshot, the
     * original's own foam fringe reads as a thin highlight, not a wide band.
     */
    private static final int FOAM_BAND = 2;

    /**
     * Peak alpha (right at the shared edge) of the foam blend, tapering to 0
     * over {@link #FOAM_BAND} rows.
     */
    private static final float FOAM_MAX_ALPHA = 0.6f;

    /** Raw-grid cardinal offsets checked for a land-land border blend. */
    private static final int[][] BORDER_EDGES = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };

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
     * Offset 0 of a foreign unit that was not on screen: the frame shows
     * the view, not yet the unit (a brave out of the fog appears at
     * offset 1).
     */
    private boolean animHidden = false;

    /**
     * The unit a movement key is moving right now ({@link #handleMoveKey}),
     * else null: its slide follows the key, not a previous slide, so it
     * keeps no pause after the last final draw.
     */
    private Unit keyMoveUnit = null;

    /**
     * A slide ended, so the next paint is its final draw: offset 16,
     * together with the tiles the move revealed (logged as
     * {@code final-draw}, see {@link ClassicFrameRecorder}).
     */
    private boolean finalDrawPending = false;

    /** {@code System.nanoTime} of the last final draw; 0 before the first. */
    private long lastFinalNanos = 0L;

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

    /** Cached spec lookup for plain ocean (unexplored-area art), lazy. */
    private TileType oceanType;

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
     * as in the original game — Space ends the turn instead.
     */
    private void skipActiveUnitOrEndTurn() {
        final Unit unit = this.activeUnit;
        if (unit == null) {
            endTurn();
            return;
        }
        if (unit.getState() != Unit.UnitState.SKIPPED) {
            this.freeColClient.getInGameController()
                .changeState(unit, Unit.UnitState.SKIPPED);
        }
        if (unit.getState() == Unit.UnitState.SKIPPED) {
            this.freeColClient.getInGameController().nextActiveUnit();
        }
    }

    /**
     * The classic W key: wait the active unit — cycle to the other units needing
     * orders and return to this one afterwards ({@code InGameController.waitUnit}).
     */
    private void waitActiveUnit() {
        this.freeColClient.getInGameController().waitUnit();
    }

    /**
     * The classic B key: found a colony with the active unit, mirroring
     * {@code BuildColonyAction} ({@code InGameController.buildColony}).  The
     * controller does the rest — it checks the unit can build, confirms any
     * site warnings, asks {@link ClassicGUI#getNewColonyName} for the name, and
     * on success opens the colony screen.  Guarded by the same precondition as
     * the action's {@code shouldBeEnabled}, so pressing B with (say) a ship
     * selected quietly does nothing rather than provoking an error panel the
     * classic GUI would swallow anyway.
     */
    private void buildColony() {
        final Unit unit = this.activeUnit;
        if (unit == null || !unit.hasTile() || !unit.canBuildColony()) return;
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
                if (ClassicFrameRecorder.on()) {
                    ClassicFrameRecorder.event("move-key", intent + " " + d
                        + " unit=" + u.getId() + " at=" + xy(u.getTile())
                        + " moves=" + u.getMovesLeft());
                }
                this.keyMoveUnit = u;
                try {
                    this.freeColClient.getInGameController().moveUnit(u, d);
                } finally {
                    this.keyMoveUnit = null;
                }
                if (ClassicFrameRecorder.on()) {
                    ClassicFrameRecorder.event("move-done", "unit=" + u.getId()
                        + " at=" + xy(u.getTile()) + " moves=" + u.getMovesLeft());
                }
                // No view test on arrival: the view rule ran on the source
                // tile before the slide (animateMove, build spec W4).
                // The slide's final draw now that the model has the move
                // and its reveal, not when the event queue gets to it.
                if (this.finalDrawPending) {
                    paintNow(null);
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
                if (n != null) this.gui.changeView(n);
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
        final boolean activated = unit != this.activeUnit
            || this.viewMode != GUI.ViewMode.MOVE_UNITS;
        // The Spielzugende square goes (left, or frozen until this
        // repaint: the first unit of the next turn, clip004 #2766).  Not
        // for no unit: the controller's "restore the active unit" after
        // our end's goto pass (doExecuteGotoOrders) is no activation.
        if (unit != null) clearPrompt("activate", true);
        this.viewMode = GUI.ViewMode.MOVE_UNITS;
        this.activeUnit = unit;
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
        } else {
            repaint();
        }
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
        final boolean rec = ClassicFrameRecorder.on();
        if (unit == null || srcTile == null || dstTile == null
            || !isShowing()
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
        // A frozen Spielzugende square stays on the screen until a paint
        // covers it: this slide's steps where they cross it, else its
        // final draw (landing-slow #5525, #2902/#6460); its minimap pixel
        // goes with the final draw.
        final boolean promptGone = this.promptFrozen && clearPrompt("slide", false);
        // A chained slide: draw the previous slide's final frame if no
        // paint has yet, then keep the pause after it.
        if (this.finalDrawPending) paintNow(null);
        if (unit != this.keyMoveUnit && this.lastFinalNanos != 0L) {
            try {
                ClassicSlide.waitUntil(this.lastFinalNanos
                                       + ClassicSlide.gapNanos(own));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
        final boolean shown = isShownAt(unit, srcTile);
        // The view rule on the SOURCE tile, now the move is accepted and
        // before the first step (build spec W4 (b)/(c), also for native
        // moves): a jump is the slide's offset 0, the whole map and the
        // minimap ring, and offset 1 follows one step later (landfall
        // #10269 -> #10270, #17712 -> #17714).
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
        // The slide shows the unit ON: the blink holds until it ends.  A key
        // that came while OFF gets offset 0 at once (the slide's own paint),
        // and the minimap dot turns back to the nation colour right after it;
        // after a jump the minimap (its ring) is painted with offset 0.
        final boolean minimap = this.blinkOff || jumped;
        this.blinkOff = false;
        final int dotStep = redraw ? 0 : 1;
        this.animUnit = unit;
        this.animFrom = srcTile;
        this.animTo = dstTile;
        this.animDx = dstTile.getX() - srcTile.getX();
        this.animDy = dstTile.getY() - srcTile.getY();
        try {
            ClassicSlide.run(ClassicSlide.SYSTEM, step, redraw, k -> {
                    this.animOffset = k;
                    this.animHidden = k == 0 && !own && !shown;
                    // Offset 0 may come with a view jump: the whole map.
                    paintNow((k == 0) ? null : slideBounds());
                    if (minimap && k == dotStep && this.gui != null) {
                        this.gui.paintBlinkDot();
                    }
                    if (rec) {
                        ClassicFrameRecorder.event("slide-step", k + "/"
                            + ClassicSlide.CELL);
                    }
                });
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } finally {
            this.animUnit = null;
            this.animFrom = null;
            this.animTo = null;
            this.animOffset = 0;
            this.animHidden = false;
            this.finalDrawPending = true;
            if (rec) {
                ClassicFrameRecorder.event("slide-end", "unit=" + unit.getId());
            }
            // The blink restarts ON at the end of every slide; the panel
            // refresh after the final draw re-bases it (build spec W3).
            rearmBlink("slide");
            repaint();
            if (promptGone && this.gui != null) this.gui.paintBlinkDot();
        }
    }

    /**
     * Paint now on the EDT ({@code paintImmediately}), else ask for a
     * repaint.
     *
     * @param r The area, or null for the whole map.
     */
    private void paintNow(Rectangle r) {
        final Rectangle a = (r == null) ? new Rectangle(0, 0, getWidth(), getHeight()) : r;
        if (SwingUtilities.isEventDispatchThread()) {
            paintImmediately(a);
        } else {
            repaint(a);
        }
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
        final Player me = this.freeColClient.getMyPlayer();
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
        if (tile.getSettlement() != null || !tile.isExplored()) return false;
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
        paintBlinkCell();
        if (this.gui != null) this.gui.paintBlinkDot();
    }

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
     * Without an active unit in MOVE_UNITS, or once it has no moves left,
     * the clock stops instead: after its last move the unit stays on
     * screen and does not blink, through the pause before the next unit or
     * the end of turn (landfall 03 section 1, {@code ship_w1.png}).
     *
     * @param reason What re-arms it (for the recorder's events).
     */
    void rearmBlink(String reason) {
        this.blinkHeld = false;
        setBlinkOff(false);
        final Unit u = this.activeUnit;
        if (this.viewMode == GUI.ViewMode.MOVE_UNITS && u != null
            && u.getTile() != null && u.getMovesLeft() > 0) {
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
     * for the hold to end, which re-arms.
     *
     * @param n The toggle: OFF for odd, ON for even.
     */
    void blinkToggle(int n) {
        final Unit u = this.activeUnit;
        if (this.viewMode != GUI.ViewMode.MOVE_UNITS || u == null
            || u.getTile() == null || u.getMovesLeft() <= 0) {
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
                + String.format(Locale.ROOT, " late=%.2fms", late / 1e6));
        }
    }

    /**
     * Paint the active unit's cell now (and the icon's reach past it), for
     * a blink toggle.  A pending final draw paints the whole map instead.
     */
    private void paintBlinkCell() {
        final Unit u = this.activeUnit;
        final int[] o = this.origin;
        if (this.finalDrawPending || u == null || u.getTile() == null
            || o == null) {
            repaint();
            return;
        }
        final Tile t = u.getTile();
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
        this.selectedTile = null;
        rearmBlink("end-turn");
        repaint();
    }


    // Internals

    private Map getMap() {
        return (this.freeColClient == null
                || this.freeColClient.getGame() == null) ? null
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
        final Player player = this.freeColClient.getMyPlayer();

        if (this.gui != null && this.gui.turnPrompt()) {
            // The Spielzugende mode: only an own unit that can still move
            // takes a click, and becomes active (build spec W17 item 6, I);
            // the rest of the map is inert, as the arrows are.
            final Unit u = (tile.isExplored()) ? tile.getFirstUnit() : null;
            if (u != null && player != null && player.owns(u)
                && u.getMovesLeft() > 0) {
                this.gui.changeView(u, false);
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
            } else { // Foreign/indian settlement: just centre for now
                this.gui.setFocus(tile);
            }
            return;
        }
        final Unit unit = tile.getFirstUnit();
        if (unit != null && player != null && player.owns(unit)) {
            this.gui.changeView(unit, false); // Make our unit active
        } else if (unit != null) { // Someone else's unit: select the tile
            this.gui.setFocus(tile);
        } else { // Empty explored tile: terrain-select
            this.gui.changeView(tile);
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

        // Two passes: every cell's terrain first, then settlements and
        // units, so an icon's shadow or overhang lies on its neighbour's
        // terrain (the original: the 21-px village 1 px into the next cell).
        for (int pass = 0; pass < 2; pass++) {
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
        if (this.viewMode == GUI.ViewMode.TERRAIN) paintCursor(g, vx, vy);
        // The Spielzugende mode's square, over terrain and sprite (W17).
        final Tile pt = this.promptTile;
        if (pt != null && isPromptShown()) {
            ClassicHud.paintPromptSquare(g, screenX(pt.getX(), vx),
                                         screenY(pt.getY(), vy), scale());
        }
        boolean changed = this.changeToShow || this.animUnit != null;
        this.changeToShow = false;
        if (this.finalDrawPending && this.animUnit == null) {
            this.finalDrawPending = false;
            this.lastFinalNanos = System.nanoTime();
            ClassicFrameRecorder.event("final-draw", "");
            changed = true;
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
        if (u == null || from == null || to == null || this.animHidden) return;
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
        if (!tile.isExplored()) return;
        final Settlement settlement = tile.getSettlement();
        if (settlement != null) {
            paintSettlement(g, settlement, sx, sy);
            return;
        }
        if (this.blinkOff && this.activeUnit != null
            && this.activeUnit.getTile() == tile) return;
        // A unit mid-slide is painted by paintAnimatedUnit instead.
        final Unit unit = displayUnit(tile);
        if (unit != null) paintUnit(g, unit, sx, sy, markerOf(unit, tile));
    }

    /**
     * The unit drawn on a tile: the active unit when it is there (also a
     * passenger: it is drawn instead of its ship), else the first unit,
     * never the one mid-slide.
     *
     * @param tile The tile.
     * @return The unit, or null.
     */
    Unit displayUnit(Tile tile) {
        final Unit a = this.activeUnit;
        if (a != null && a != this.animUnit && a.getTile() == tile) return a;
        for (Unit u : tile.getUnitList()) {
            if (u != this.animUnit) return u;
        }
        return null;
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
        final BufferedImage img = this.lib.getScaledUnitImage(unit);
        if (this.fixedScale <= 0) {
            drawCentered(g, img, sx, sy);
            return;
        }
        final Player owner = unit.getOwner();
        final int s = scale();
        final Graphics2D gg = (Graphics2D) g.create();
        try {
            gg.translate(sx, sy);
            gg.scale(s, s);
            ClassicHud.paintIcon(gg, this.iconFont,
                ClassicHud.orderLetter(this.iconText, ClassicHud.ordersRow(unit)),
                fit16(img), ClassicHud.nationRgb(owner),
                ClassicHud.nationDark(owner), 0, 0, marker);
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
        final BufferedImage img = this.lib.getScaledSettlementImage(settlement);
        if (img == null) return;
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
     * Paint one tile's ground: base terrain, then the terrain-feature
     * overlays (forest / hills / mountains / river / road / plow / resource
     * / lost-city — item (e), composited by {@link ClassicTileArt}).  What
     * stands on it comes in the second pass ({@link #paintOccupant}).
     */
    private void paintTile(Graphics2D g, Map map, Tile tile, int sx, int sy) {
        if (!tile.isExplored()) {
            // The original never shows black fog on the main map: unexplored
            // area reads as plain open ocean (reference: the expert's
            // opening_032 capture — the whole screen is sea around the start
            // ship, with no black anywhere). The minimap keeps black for
            // unexplored, as the original's minimap does.
            paintOpenSea(g, tile.getX(), tile.getY(), sx, sy);
            return;
        }
        // Fetch the tile at its native 16x16 size and let the (nearest-neighbour)
        // scaling in paintComponent up-scale it, so classic pixels stay crisp.
        final BufferedImage terrain = this.lib.getTerrainImage(
            tile.getType(), tile.getX(), tile.getY(), SRC_SIZE);
        if (terrain != null) {
            final BufferedImage blended = tile.isLand()
                ? blendLandBorders(map, tile, terrain)
                : blendWaterBorders(map, tile, terrain);
            g.drawImage(blended, sx, sy, tileW(), tileH(), null);
        }

        // Composite the physical-feature overlays on top of the base terrain.
        this.tileArt.paintOverlays(g, map, tile, sx, sy, tileW(), tileH());
    }

    /**
     * Paint the endless-ocean filler used for unexplored tiles and for cells
     * beyond the map edge.  The coordinates may lie off-map; they only seed
     * the per-tile texture variation ({@code floorMod} keeps them positive).
     */
    private void paintOpenSea(Graphics2D g, int x, int y, int sx, int sy) {
        final TileType ocean = oceanType();
        if (ocean == null) return;
        final BufferedImage sea = this.lib.getTerrainImage(
            ocean, Math.floorMod(x, 1000), Math.floorMod(y, 1000), SRC_SIZE);
        if (sea != null) g.drawImage(sea, sx, sy, tileW(), tileH(), null);
    }

    /** The spec's plain ocean type, used to paint unexplored area (lazy). */
    private TileType oceanType() {
        if (this.oceanType == null
            && this.freeColClient.getGame() != null
            && this.freeColClient.getGame().getSpecification() != null) {
            this.oceanType = this.freeColClient.getGame().getSpecification()
                .getTileType("model.tile.ocean");
        }
        return this.oceanType;
    }

    /**
     * Blend a {@link #BORDER_BAND}-pixel-wide dithered band into {@code terrain}
     * along each raw-grid edge that faces a neighbour of a <em>different</em>
     * {@link net.sf.freecol.common.model.TileType} -- land or water -- so a
     * land-land boundary reads as organic dithering rather than the flat
     * rectangular edge two differently-coloured base textures otherwise
     * produce (see {@code classic_ui_plan/land-tile-borders.md}, Q7), and so
     * the land side of a coastline softens toward the water's own colour
     * instead of ending in a hard square. This is independent of, and drawn
     * before, {@link #blendWaterBorders} (which feathers the <em>water</em>
     * side of the same boundary with a procedural foam highlight) -- the two
     * are complementary, each softening their own side of the edge.
     *
     * @return {@code terrain} unchanged when {@code tile} is not land or no
     *     neighbour needs blending (the common case, kept cheap); otherwise a
     *     new image, leaving the shared cached source untouched.
     */
    private BufferedImage blendLandBorders(Map map, Tile tile, BufferedImage terrain) {
        if (!tile.isLand()) return terrain;
        BufferedImage blended = null;
        for (int[] edge : BORDER_EDGES) {
            final int nx = tile.getX() + edge[0];
            final int ny = tile.getY() + edge[1];
            final Tile neighbour = map.getTile(nx, ny);
            if (neighbour == null) continue;
            // An unexplored neighbour has no usable type/art of its own, but
            // it is *painted* as open sea (see paintTile) — so blend toward
            // ocean, not toward the black its null type would sample (which
            // drew black fringes along coasts facing unexplored water).
            final boolean nExplored = neighbour.isExplored();
            final TileType nType = nExplored ? neighbour.getType()
                : oceanType();
            if (nType == null || nType == tile.getType()) continue;
            final BufferedImage neighbourImg =
                this.lib.getTerrainImage(nType, nx, ny, SRC_SIZE);
            if (neighbourImg == null) continue;
            if (blended == null) blended = copyImage(terrain);
            if (nExplored && neighbour.isLand()) {
                ditherEdge(blended, neighbourImg, tile.getX(), tile.getY(), edge[0], edge[1]);
            } else {
                blendCoastEdge(blended, neighbourImg, tile.getX(), tile.getY(), edge[0], edge[1]);
            }
        }
        return (blended != null) ? blended : terrain;
    }

    /**
     * Blend a soft foam highlight into {@code terrain} along each raw-grid
     * edge of a <em>water</em> tile that faces land. Replaces the sprite-
     * based coast quarter-tiles removed from {@link ClassicTileArt} (see its
     * class comment): those extracted frames rendered a scattered green fleck
     * along the wave crest that the real 1994 game never shows (compared
     * directly against {@code screenshots/initial/opening_007.png}, which
     * shows a clean, fairly desaturated grey/white foam fringe hugging the
     * coast). Rather than ship a fringe that doesn't match the source
     * material, this paints that fringe procedurally instead -- the same call
     * Q7 already made for the land side after it turned out there was no
     * faithful land-land border sprite to source either.
     *
     * @return {@code terrain} unchanged when {@code tile} is not water or no
     *     neighbour is land; otherwise a new image.
     */
    private BufferedImage blendWaterBorders(Map map, Tile tile, BufferedImage terrain) {
        if (tile.isLand()) return terrain;
        BufferedImage blended = null;
        for (int[] edge : BORDER_EDGES) {
            final int nx = tile.getX() + edge[0];
            final int ny = tile.getY() + edge[1];
            final Tile neighbour = map.getTile(nx, ny);
            if (neighbour == null || !neighbour.isLand()) continue;
            if (blended == null) blended = copyImage(terrain);
            foamEdge(blended, tile.getX(), tile.getY(), edge[0], edge[1]);
        }
        return (blended != null) ? blended : terrain;
    }

    /** Return a mutable {@code TYPE_INT_ARGB} copy of {@code src}. */
    private static BufferedImage copyImage(BufferedImage src) {
        final BufferedImage copy = new BufferedImage(
            src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g = copy.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return copy;
    }

    /**
     * Deterministic pseudo-random value in {@code [0,1)} for the pixel at
     * world-space native coordinate {@code (x, y)}. Stable across repaints
     * (no flicker), but -- unlike a small repeating ordered-dither matrix
     * such as a 2&times;2 Bayer pattern -- does not read as a regular grid,
     * and is seeded per <em>world</em> pixel rather than per in-tile
     * position, so different tile boundaries scatter differently instead of
     * all looking stamped from the same template (see {@link #ditherEdge}).
     */
    private static float hashNoise(int x, int y) {
        int h = x * 0x27d4eb2d ^ y * 0x165667b1;
        h = (h ^ (h >>> 15)) * 0x85ebca6b;
        h = (h ^ (h >>> 13)) * 0xc2b2ae35;
        h ^= (h >>> 16);
        return (h & 0x7fffffff) / (float) 0x7fffffff;
    }

    /**
     * Map band row {@code row} (0 = right at the shared edge) and lateral
     * position {@code i} along a {@code (dx, dy)}-facing edge of a
     * {@code w}&times;{@code h} image to {@code {ox, oy, nxp, nyp}}: the
     * pixel to overwrite in the tile's own image, and the mirrored pixel to
     * read from an {@code nw}&times;{@code nh} neighbour image.
     *
     * <p>{@code neighbour} is <em>not</em> assumed to share {@code img}'s
     * dimensions: {@link ImageLibrary#getTerrainImage} only honours the
     * requested size when the source sprite's aspect ratio already matches
     * it ({@code ImageUtils.wildcardDimension} otherwise preserves the
     * source's own aspect ratio to avoid distortion) -- true for every
     * square {@code TERRAIN.SS} land frame, but not guaranteed for water,
     * whose source art need not be square. The along-edge axis is scaled
     * proportionally into the neighbour's own span and the depth axis is
     * clamped into it, so an odd-shaped neighbour degrades to a coarser
     * sample rather than an out-of-bounds read.
     */
    private static int[] edgeCoords(int w, int h, int nw, int nh, int dx, int dy,
                                    int row, int i) {
        final int ox, oy, nxp, nyp;
        if (dx != 0) {
            oy = i;
            nyp = Math.min(nh - 1, i * nh / h);
            if (dx < 0) { ox = row;         nxp = Math.max(0, nw - 1 - row); }
            else        { ox = w - 1 - row; nxp = Math.min(nw - 1, row);      }
        } else {
            ox = i;
            nxp = Math.min(nw - 1, i * nw / w);
            if (dy < 0) { oy = row;         nyp = Math.max(0, nh - 1 - row); }
            else        { oy = h - 1 - row; nyp = Math.min(nh - 1, row);      }
        }
        return new int[] { ox, oy, nxp, nyp };
    }

    /**
     * Replace a sparse, noise-selected subset of the pixels along the edge of
     * {@code img} facing raw-grid offset {@code (dx, dy)} with the mirrored
     * pixel from a <em>land</em> {@code neighbour}, so the blend reads as the
     * neighbour's actual (dithered) texture, scattered unevenly (via
     * {@link #hashNoise}, seeded by the tile's world position
     * {@code (tileX, tileY)} so adjacent boundaries don't repeat the same
     * pattern) with density falling off over {@link #BORDER_BAND} native
     * pixels from the shared edge. Independent per-pixel scatter reads fine
     * here because neighbouring land textures are close enough in value that
     * it looks like organic noise -- for the water case, see
     * {@link #blendCoastEdge}, which needs a stricter, contiguous fill.
     */
    private static void ditherEdge(BufferedImage img, BufferedImage neighbour,
                                   int tileX, int tileY, int dx, int dy) {
        final int w = img.getWidth(), h = img.getHeight();
        final int nw = neighbour.getWidth(), nh = neighbour.getHeight();
        final int span = (dx != 0) ? h : w;
        for (int row = 0; row < BORDER_BAND; row++) {
            final float density = BORDER_DENSITY * (BORDER_BAND - row) / BORDER_BAND;
            for (int i = 0; i < span; i++) {
                final int[] c = edgeCoords(w, h, nw, nh, dx, dy, row, i);
                final int gx = tileX * TILE_SRC + c[0], gy = tileY * TILE_SRC + c[1];
                if (hashNoise(gx, gy) >= density) continue;
                img.setRGB(c[0], c[1], neighbour.getRGB(c[2], c[3]));
            }
        }
    }

    /**
     * Blend the edge of {@code img} facing {@code (dx, dy)} toward a
     * <em>water</em> {@code neighbour}. Unlike {@link #ditherEdge}'s
     * independently-scattered pixels, water is such a high-contrast colour
     * swap from any land texture that isolated water pixels deep in solid
     * land read as unnatural "flooded" potholes rather than texture noise --
     * flagged by the expert from a side-by-side screenshot comparison against
     * the reference art (see {@code classic_ui_plan/land-tile-borders.md}).
     * Instead, each lateral position {@code i} along the edge gets one
     * noise-derived incursion depth in {@code [1, BORDER_BAND]} -- row 0 (the
     * pixel right at the shared edge) is <em>always</em> replaced, so the
     * coastline itself is a continuously-present line rather than gapping
     * back to a hard land/water step at {@link #COAST_GAP_PROBABILITY} of all
     * lateral positions; that probability instead only gates how much
     * <em>further</em> a given position reaches inland (1 row the rest of the
     * time, up to {@code BORDER_BAND} rows the other {@code 1 -
     * COAST_GAP_PROBABILITY}), keeping the reach jagged and uneven without
     * ever opening a gap. Every pixel from the edge up to the chosen depth is
     * replaced, so the boundary itself is a wavy but <em>solid</em> line:
     * strictly water beyond it, strictly land before it.
     */
    private static void blendCoastEdge(BufferedImage img, BufferedImage neighbour,
                                       int tileX, int tileY, int dx, int dy) {
        final int w = img.getWidth(), h = img.getHeight();
        final int nw = neighbour.getWidth(), nh = neighbour.getHeight();
        final int span = (dx != 0) ? h : w;
        for (int i = 0; i < span; i++) {
            final int worldPerp = (dx != 0) ? tileY * TILE_SRC + i : tileX * TILE_SRC + i;
            final float n = hashNoise(worldPerp, dx * 7 + dy * 13);
            final int depth;
            if (n < COAST_GAP_PROBABILITY) {
                depth = 1;
            } else {
                final float fraction = (n - COAST_GAP_PROBABILITY) / (1f - COAST_GAP_PROBABILITY);
                depth = Math.min(BORDER_BAND, 1 + (int) (fraction * BORDER_BAND));
            }
            for (int row = 0; row < depth; row++) {
                final int[] c = edgeCoords(w, h, nw, nh, dx, dy, row, i);
                img.setRGB(c[0], c[1], neighbour.getRGB(c[2], c[3]));
            }
        }
    }

    /**
     * Alpha-blend the foam colour ({@link #FOAM_R}/{@link #FOAM_G}/
     * {@link #FOAM_B}) into the edge of {@code img} facing raw-grid offset
     * {@code (dx, dy)}, peaking at {@link #FOAM_MAX_ALPHA} right at the
     * shared edge and tapering to 0 over {@link #FOAM_BAND} rows. Blends
     * (rather than replaces, unlike {@link #ditherEdge}/{@link
     * #blendCoastEdge}) because there is no neighbour art to stay faithful to
     * here -- the water tile has no land-coloured pixels to sample, only its
     * own base ocean texture -- so lightening the existing water colour reads
     * as a foam highlight sitting on top of it, the same way the original's
     * own fringe looks like whitecaps over water rather than a separate
     * layer. A per-pixel {@link #hashNoise} factor varies the alpha slightly
     * so the line reads as an uneven natural highlight rather than a
     * ruler-straight stripe, without gapping back to nothing anywhere along
     * the edge -- unlike the land side, this has no "flooded" failure mode to
     * guard against (a lighter-than-usual water pixel never reads as a hole),
     * so there is no need for {@link #blendCoastEdge}'s gap/depth machinery.
     */
    private static void foamEdge(BufferedImage img, int tileX, int tileY, int dx, int dy) {
        final int w = img.getWidth(), h = img.getHeight();
        final int span = (dx != 0) ? h : w;
        for (int row = 0; row < FOAM_BAND; row++) {
            final float rowAlpha = FOAM_MAX_ALPHA * (FOAM_BAND - row) / FOAM_BAND;
            for (int i = 0; i < span; i++) {
                final int[] c = edgeCoords(w, h, w, h, dx, dy, row, i);
                final int gx = tileX * TILE_SRC + c[0], gy = tileY * TILE_SRC + c[1];
                final float alpha = rowAlpha * (0.7f + 0.3f * hashNoise(gx, gy));
                final int argb = img.getRGB(c[0], c[1]);
                final int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
                final int nr = Math.round(r * (1 - alpha) + FOAM_R * alpha);
                final int ng = Math.round(g * (1 - alpha) + FOAM_G * alpha);
                final int nb = Math.round(b * (1 - alpha) + FOAM_B * alpha);
                img.setRGB(c[0], c[1], (argb & 0xFF000000) | (nr << 16) | (ng << 8) | nb);
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
