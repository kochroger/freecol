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

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;


/**
 * The original's <b>right-hand panel</b> in play: x 240..319, y 8..199 of
 * the 320x200 in-game canvas ({@link ClassicHudPane}), painted by the static
 * {@link ClassicHud} painters from the live game -- wood, minimap with the
 * viewport ring, season and gold lines, the active unit with its flag and
 * order lines, and the cargo or the other units on its tile (Steam
 * {@code opening_032}, {@code 000}; start-sequence {@code 052}).
 *
 * <p>It paints its 80x192 pixels into an off-screen picture and scales it
 * up nearest-neighbour by the HUD's integer scale, so the screen shows the
 * very pixels the headless preview diffs against the captures.  There are
 * no buttons and no hint footer: the original panel has neither; orders go
 * through the BEFEHLE menu and the keys ({@link ClassicKeyMap}).  A click in
 * the minimap recentres the map there.  {@code ClassicGUI.repaintInfo}
 * repaints it whenever the view or the model changes.
 *
 * <p>The active unit's minimap pixel blinks with the unit (build spec W3,
 * {@link #blinkDot}): nation colour while ON, white while OFF.  Its paints
 * also tell the map's blink clock when the panel refresh after a
 * (re)activation comes, from which the first OFF is timed.
 *
 * <p>In <em>scene mode</em> ({@link #setSceneMode}, the first game scene)
 * the unit block is hidden.  In the Spielzugende mode (build spec W17,
 * {@link #enterPrompt}) the tile mode of the cursor tile replaces it, with
 * the blinking word under it; a press on the word ends the turn.
 */
final class ClassicInfoPanel extends JComponent {

    private static final Logger logger = Logger.getLogger(ClassicInfoPanel.class.getName());

    private final FreeColClient freeColClient;

    /** Source of the live view state (active unit, focus, mode). */
    private final ClassicMapViewer mapViewer;

    /** For the units' sprites (ICONS.SS through the pack's aliases). */
    private final ImageLibrary lib;

    /** The pack's texts, FONTTINY and wood; null without the pack. */
    private final ClassicText text;
    private final ClassicFont font;
    private final BufferedImage wood;

    /** Scene mode: no unit block. */
    private boolean scene = false;

    /**
     * The end of turn and hand-over (build spec W5): the turn indicator's
     * colour, whether the panel waits for our next turn, and where the
     * panel's changes are reported; null until the game view has one.
     */
    private ClassicTurnFlow turnFlow = null;

    /**
     * The stale panel (build spec W5b): the unit block last built while
     * its unit could still move, and that unit.  It is painted while no
     * unit is active or the remembered unit has no moves left, so after a
     * unit's last move the panel keeps its "Züge" and "Ort" from before
     * that move -- through the pause, the hand-over and the AI phase --
     * until another unit's block replaces it or the turn-start wipe
     * forgets it ({@link #clearStale}).
     */
    private Unit staleUnit = null;
    private ClassicHud.UnitFacts staleActive = null;
    private List<ClassicHud.UnitFacts> staleList = new ArrayList<>();

    /**
     * The landing (build spec W8b, master plan W18): from the landing box
     * until the next unit's block, the panel keeps the block it showed
     * when the ship was ordered onto land -- the passengers still
     * "Wache" -- although FreeCol wakes them before the box and the ship
     * keeps its moves (clip007 #3040 -&gt; #3107: only the minimap changes).
     */
    private boolean blockHeld = false;

    /**
     * The Spielzugende mode (build spec W17): the tile the panel shows in
     * the tile mode instead of the unit block, as it was when the mode
     * began, or null; and whether the end command froze the word white
     * (until the turn-start wipe, clip004 #2745).
     */
    private ClassicHud.TileFacts promptFacts = null;
    private boolean promptFrozen = false;

    /**
     * The season line of the last paint in our turn: kept while the turn
     * flow waits for our next turn, so the year changes in the paint of
     * the wipe (W5b), not when FreeCol's turn changes.
     */
    private String shownSeason = null;

    /**
     * What the screen shows of this panel (80x192 RGB, as last blitted
     * within each paint's clip), to tell the turn flow when a paint
     * changed a pixel; null before the first paint.
     */
    private int[] shown = null;

    /** The minimap shown in the last paint (for click-to-recentre). */
    private ClassicHud.MinimapModel lastMinimap = null;

    /**
     * The panel as the last paint rendered it (80x192), which a
     * minimap-only paint redraws only the minimap of; null before the
     * first paint.
     */
    private BufferedImage rendered = null;

    /** Logged once when painting fails. */
    private boolean failLogged = false;


    ClassicInfoPanel(FreeColClient freeColClient, ClassicMapViewer mapViewer,
                     ImageLibrary lib, ClassicText text, ClassicFont font,
                     BufferedImage wood) {
        this.freeColClient = freeColClient;
        this.mapViewer = mapViewer;
        this.lib = lib;
        this.text = text;
        this.font = font;
        this.wood = wood;
        setOpaque(true);
        addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    onClick(e);
                }
            });
    }


    /** Hide (true) or show the unit block. */
    void setSceneMode(boolean on) {
        if (this.scene == on) return;
        this.scene = on;
        repaint();
    }

    /**
     * Use the game view's turn flow (W5).
     *
     * @param flow The flow, or null.
     */
    void setTurnFlow(ClassicTurnFlow flow) {
        this.turnFlow = flow;
    }

    /**
     * Forget the remembered unit block (the turn-start wipe, W5b): until
     * the next unit is up the panel shows no block, as in scene mode.
     */
    void clearStale() {
        this.staleUnit = null;
        this.staleActive = null;
        this.staleList = new ArrayList<>();
        this.promptFacts = null;
        this.promptFrozen = false;
        this.blockHeld = false;
    }

    /**
     * Keep the unit block as last painted, live or not, until
     * {@link #releaseBlock} (the landing, {@link #blockHeld}); the minimap
     * and the lines above the block stay live.
     */
    void holdBlock() {
        this.blockHeld = true;
    }

    /** The block follows the live game again (the next unit, a box answered "stay"). */
    void releaseBlock() {
        this.blockHeld = false;
    }

    /** @return Whether the block is held ({@link #holdBlock}). */
    boolean isBlockHeld() {
        return this.blockHeld;
    }

    /**
     * The Spielzugende mode begins (build spec W17 item 5): the panel shows
     * the tile mode of {@code tile} -- position, land, terrain, its
     * settlement and units, no "Züge" line -- with the word under it.  The
     * caller paints it, together with the map's square (landing-slow
     * #2305/#2306: one update).
     *
     * @param tile The cursor tile, or null for none.
     */
    void enterPrompt(Tile tile) {
        this.promptFacts = (tile == null) ? null : tileFacts(tile);
        this.promptFrozen = false;
    }

    /**
     * The end command in the mode: the word is forced white and stays
     * so until the turn-start wipe (clip004 #2427 -&gt; #2745).
     */
    void freezePrompt() {
        if (this.promptFacts == null) return;
        this.promptFrozen = true;
        paintNow();
    }

    /** The mode was left without an end (a unit was activated): the unit block is back. */
    void leavePrompt() {
        if (this.promptFacts == null) return;
        this.promptFacts = null;
        this.promptFrozen = false;
        paintNow();
    }

    /** @return The tile mode's tile, or null (tests and the recorder). */
    ClassicHud.TileFacts promptFacts() {
        return this.promptFacts;
    }

    /** The tile mode's facts of a live tile, as the player knows it. */
    private ClassicHud.TileFacts tileFacts(Tile tile) {
        final List<ClassicHud.UnitFacts> units = new ArrayList<>();
        for (Unit u : tile.getUnitList()) units.add(facts(u));
        BufferedImage sp = null;
        if (tile.getSettlement() != null) {
            try {
                sp = this.lib.getScaledSettlementImage(tile.getSettlement());
            } catch (RuntimeException e) {
                sp = null;
            }
        }
        return ClassicHud.TileFacts.of(tile, this.freeColClient.getMyPlayer(),
                                       sp, units);
    }

    /** Paint the whole panel at once (EDT), else ask for a repaint. */
    void paintNow() {
        if (SwingUtilities.isEventDispatchThread()) {
            paintImmediately(0, 0, getWidth(), getHeight());
        } else {
            repaint();
        }
    }

    /**
     * Paint the turn indicator's box at once (EDT), else ask for a
     * repaint of it ({@link ClassicHud#INDICATOR}).
     */
    void paintIndicatorNow() {
        final int s = scale();
        final Rectangle r = new Rectangle(
            (ClassicHud.INDICATOR.x - ClassicHud.PANEL_X) * s,
            (ClassicHud.INDICATOR.y - ClassicHud.PANEL_Y) * s,
            ClassicHud.INDICATOR.width * s, ClassicHud.INDICATOR.height * s);
        if (SwingUtilities.isEventDispatchThread()) {
            paintImmediately(r);
        } else {
            repaint(r);
        }
    }

    /** The canvas scale: this component is 80 virtual pixels wide. */
    private int scale() {
        return Math.max(1, getWidth() / ClassicHud.PANEL_W);
    }

    /**
     * The minimap as the panel shows it now: the map at the view, the
     * active unit's blink dot, the Spielzugende mode's pixel and a native
     * cue's pixel (W19).
     *
     * @return The minimap, or null without a game or a view.
     */
    private ClassicHud.MinimapModel minimapModel() {
        final Game game = this.freeColClient.getGame();
        if (game == null || game.getMap() == null) return null;
        final int[] o = this.mapViewer.viewOrigin();
        if (o == null) return null;
        ClassicHud.MinimapModel mm = blinkDot(
            ClassicHud.minimapOf(game.getMap(), o[0], o[1]),
            this.mapViewer.getActiveUnit(), this.mapViewer.isBlinkOff());
        mm = promptDot(mm, this.mapViewer.promptTile(),
                       this.mapViewer.isPromptShown());
        // The native cue's white pixel on the move's source (W19).
        final Tile cue = this.mapViewer.cueTile();
        return promptDot(mm, cue, cue != null);
    }

    /**
     * Whether a paint's clip lies within the minimap's frame: only the
     * minimap can have changed then ({@link #paintMinimapNow}).
     *
     * @param clip The clip in component pixels, or null (all).
     * @param s The canvas scale.
     * @return True for a minimap-only paint.
     */
    static boolean minimapOnly(Rectangle clip, int s) {
        if (clip == null) return false;
        final Rectangle f = new Rectangle(
            (ClassicHud.MINIMAP_FRAME.x - ClassicHud.PANEL_X) * s,
            (ClassicHud.MINIMAP_FRAME.y - ClassicHud.PANEL_Y) * s,
            ClassicHud.MINIMAP_FRAME.width * s, ClassicHud.MINIMAP_FRAME.height * s);
        return f.contains(clip);
    }

    /**
     * The panel's model from the live game.
     *
     * @return The model; never null (an empty panel without a game).
     */
    ClassicHud.PanelModel model() {
        final Game game = this.freeColClient.getGame();
        final Player player = this.freeColClient.getMyPlayer();
        final ClassicHud.MinimapModel mm = minimapModel();
        String season = null, gold = null;
        if (game != null && game.getTurn() != null) {
            season = ClassicHud.seasonLine(this.text, game.getTurn().getSeason(),
                                           game.getTurn().getYear());
            if (season == null) {
                season = Messages.message(game.getTurn().getLabel());
            }
        }
        final ClassicTurnFlow flow = this.turnFlow;
        if (flow != null && flow.isWaiting() && this.shownSeason != null) {
            season = this.shownSeason;   // the year changes with the wipe
        } else {
            this.shownSeason = season;
        }
        if (player != null) {
            gold = ClassicHud.goldLine(this.text, Math.max(0, player.getGold()),
                                       player.getTax());
            if (gold == null) {
                gold = Messages.message("gold") + ": " + player.getGold()
                    + "  " + Messages.message("tax") + ": " + player.getTax();
            }
        }
        final int indicator = (flow == null) ? -1 : flow.indicatorRgb();
        if (flow != null) flow.indicatorShown(indicator);
        if (this.promptFacts != null) {
            // The Spielzugende mode: the tile mode, the word blinking with
            // the map's square, white once the end command froze it.
            final boolean on = this.promptFrozen || this.mapViewer.isPromptShown();
            return new ClassicHud.PanelModel(mm, season, gold, this.scene, null,
                null, indicator, this.promptFacts,
                on ? ClassicHud.PROMPT_ON_RGB : ClassicHud.PROMPT_OFF_RGB);
        }
        ClassicHud.UnitFacts active = null;
        List<ClassicHud.UnitFacts> list = new ArrayList<>();
        final Unit unit = this.mapViewer.getActiveUnit();
        if (liveBlock(this.blockHeld, unit, this.staleUnit)) {
            active = facts(unit);
            final List<Unit> others = new ArrayList<>();
            if (unit.isCarrier() && unit.hasCargo()) {
                others.addAll(cargoNewestFirst(unit));
            } else {
                final Tile t = unit.getTile();
                for (Unit u : t.getUnitList()) {
                    if (u != unit) others.add(u);
                }
            }
            for (Unit u : others) list.add(facts(u));
            this.staleUnit = unit;
            this.staleActive = active;
            this.staleList = list;
        } else if (this.staleActive != null) {
            active = this.staleActive;
            list = new ArrayList<>(this.staleList);
        }
        return new ClassicHud.PanelModel(mm, season, gold, this.scene, active,
                                         list, indicator);
    }

    /**
     * Whether the panel shows the active unit as it is now (build spec
     * W5b): a unit with moves left, or a unit other than the remembered
     * one.  The remembered unit without moves left -- after its last move,
     * through the pause and the hand-over -- and no active unit at all
     * show the remembered block instead.
     *
     * @param unit The active unit, or null.
     * @param remembered The unit of the remembered block, or null.
     * @return True to build the block from the live unit.
     */
    static boolean showsLive(Unit unit, Unit remembered) {
        return unit != null && unit.hasTile()
            && (unit.getMovesLeft() > 0 || unit != remembered);
    }

    /**
     * Whether the panel builds its block from the live game now: not while
     * a landing holds it ({@link #holdBlock}), else as {@link #showsLive}.
     *
     * @param held The block is held.
     * @param unit The active unit, or null.
     * @param remembered The unit of the remembered block, or null.
     * @return True to build the block from the live unit.
     */
    static boolean liveBlock(boolean held, Unit unit, Unit remembered) {
        return !held && showsLive(unit, remembered);
    }

    /**
     * A carrier's passengers as the panel lists them: the one that boarded
     * last on top, so the bottom entry is the one a landing sends ashore
     * (clip007 #5093, #6188, #6989; master plan W18).  FreeCol's list
     * appends on boarding, so it is reversed.
     *
     * @param carrier The carrier.
     * @return Its units, newest first.
     */
    static List<Unit> cargoNewestFirst(Unit carrier) {
        final List<Unit> units = new ArrayList<>(carrier.getUnitList());
        java.util.Collections.reverse(units);
        return units;
    }

    /**
     * The active unit's minimap dot (build spec W3): its nation colour
     * while the blink is ON, white ({@link ClassicHud#BLINK_DOT_RGB}) while
     * it is OFF.  The viewport ring is still drawn over it.
     *
     * @param mm The minimap, or null.
     * @param active The active unit, or null.
     * @param off The blink is OFF.
     * @return The minimap with the dot, or {@code mm} without an active
     *     unit on the map.
     */
    static ClassicHud.MinimapModel blinkDot(ClassicHud.MinimapModel mm,
                                            Unit active, boolean off) {
        if (mm == null || active == null || !active.hasTile()) return mm;
        final Tile t = active.getTile();
        return mm.with(t.getX(), t.getY(), off ? ClassicHud.BLINK_DOT_RGB
            : ClassicHud.nationRgb(active.getOwner()));
    }

    /**
     * The Spielzugende mode's minimap pixel (build spec W17 item 4): the
     * cursor tile's pixel white while the map's square is drawn, its own
     * colour otherwise -- the opposite phase to the unit's dot.
     *
     * @param mm The minimap, or null.
     * @param tile The cursor tile, or null.
     * @param shown The square is drawn.
     * @return The minimap with the pixel, or {@code mm}.
     */
    static ClassicHud.MinimapModel promptDot(ClassicHud.MinimapModel mm,
                                             Tile tile, boolean shown) {
        if (mm == null || tile == null || !shown) return mm;
        return mm.with(tile.getX(), tile.getY(), ClassicHud.PROMPT_ON_RGB);
    }

    /**
     * Paint the minimap at once (a blink toggle changed the active unit's
     * dot), else ask for a repaint off the event thread.
     */
    void paintMinimapNow() {
        final int s = scale();
        final Rectangle r = new Rectangle(
            (ClassicHud.MINIMAP.x - ClassicHud.PANEL_X) * s,
            (ClassicHud.MINIMAP.y - ClassicHud.PANEL_Y) * s,
            ClassicHud.MINIMAP.width * s, ClassicHud.MINIMAP.height * s);
        if (SwingUtilities.isEventDispatchThread()) {
            paintImmediately(r);
        } else {
            repaint(r);
        }
    }

    private ClassicHud.UnitFacts facts(Unit u) {
        BufferedImage sprite = null;
        try {
            sprite = this.lib.getScaledUnitImage(u);
        } catch (RuntimeException e) {
            sprite = null;
        }
        return ClassicHud.UnitFacts.of(u, sprite);
    }

    @Override
    protected void paintComponent(Graphics g) {
        final int s = scale();
        final Rectangle clip = g.getClipBounds();
        final BufferedImage img = new BufferedImage(ClassicHud.PANEL_W,
            ClassicHud.PANEL_H, BufferedImage.TYPE_INT_RGB);
        final Graphics2D ig = img.createGraphics();
        try {
            ig.translate(-ClassicHud.PANEL_X, -ClassicHud.PANEL_Y);
            if (this.rendered != null && minimapOnly(clip, s)) {
                // A minimap-only paint (a blink's dot, a jump's ring, a
                // native cue's pixel): the rest as the last paint left it,
                // only the minimap drawn again.  The whole panel took 3-15
                // ms per toggle, the minimap alone a fraction (C3; the
                // blink's second paint, F6).
                ig.drawImage(this.rendered, ClassicHud.PANEL_X, ClassicHud.PANEL_Y, null);
                final ClassicHud.MinimapModel mm = minimapModel();
                this.lastMinimap = mm;
                ClassicHud.paintMinimapArea(ig, mm);
            } else {
                final ClassicHud.PanelModel p = model();
                this.lastMinimap = p.minimap;
                ClassicHud.paintPanel(ig, this.font, this.wood, this.text, p);
            }
        } catch (RuntimeException e) {
            if (!this.failLogged) {
                this.failLogged = true;
                logger.log(Level.WARNING, "Classic panel paint failed", e);
            }
        } finally {
            ig.dispose();
        }
        this.rendered = img;
        ClassicMenuStrip.blit(g, img, 0, 0, s);
        // The panel refresh after a (re)activation starts the blink's
        // phase: now, as its pixels are on the screen.  Only a paint that
        // changed something is the refresh: the identical repaint the
        // controller queues behind a move's block (10-15 ms later) put the
        // first OFF a frame late (24 frames after the block, the original
        // 22-23).
        final boolean changed = noteShown(img, g.getClipBounds(), s);
        if (changed) this.mapViewer.blinkPanelPainted();
        if (changed && this.turnFlow != null) this.turnFlow.screenChanged();
    }

    /**
     * Record what this paint put on the screen and tell whether that
     * changed a pixel the player sees, outside the turn indicator (whose
     * changes are the turn flow's own).
     *
     * @param img The panel as painted, 80x192.
     * @param clip The paint's clip in component pixels, or null for all.
     * @param s The canvas scale.
     * @return True if a pixel changed.
     */
    private boolean noteShown(BufferedImage img, Rectangle clip, int s) {
        final int w = ClassicHud.PANEL_W, h = ClassicHud.PANEL_H;
        final int[] now = img.getRGB(0, 0, w, h, null, 0, w);
        int x0 = 0, y0 = 0, x1 = w, y1 = h;
        if (clip != null) {
            x0 = Math.max(0, clip.x / s);
            y0 = Math.max(0, clip.y / s);
            x1 = Math.min(w, (clip.x + clip.width + s - 1) / s);
            y1 = Math.min(h, (clip.y + clip.height + s - 1) / s);
        }
        if (this.shown == null) {
            this.shown = now;
            return true;
        }
        final Rectangle ind = new Rectangle(
            ClassicHud.INDICATOR.x - ClassicHud.PANEL_X,
            ClassicHud.INDICATOR.y - ClassicHud.PANEL_Y,
            ClassicHud.INDICATOR.width, ClassicHud.INDICATOR.height);
        boolean changed = false;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                final int i = y * w + x;
                if (this.shown[i] != now[i]) {
                    if (!ind.contains(x, y)) changed = true;
                    this.shown[i] = now[i];
                }
            }
        }
        return changed;
    }

    /**
     * A press on the word "Spielzugende" ends the turn (build spec W17 item
     * 7: the pointer rested on the word before every end, 5 of 5); a click
     * in the minimap's interior recentres the map on that tile.
     */
    private void onClick(MouseEvent e) {
        final int s = scale();
        final int vx = ClassicHud.PANEL_X + e.getX() / s;
        final int vy = ClassicHud.PANEL_Y + e.getY() / s;
        final ClassicTurnFlow flow = this.turnFlow;
        if (this.promptFacts != null && !this.promptFrozen && flow != null
            && flow.isPrompt() && ClassicHud.promptWordBounds(this.font,
                this.text, this.promptFacts).contains(vx, vy)) {
            flow.endTurnNow("prompt-click");
            return;
        }
        final ClassicHud.MinimapModel m = this.lastMinimap;
        if (m == null) return;
        final Rectangle in = ClassicHud.MINIMAP;
        if (!in.contains(vx, vy)) return;
        this.mapViewer.recenterOnTile(
            ClassicHud.minimapOriginX(m.mapWidth, m.c0) + vx - in.x,
            ClassicHud.minimapOriginY(m.mapHeight, m.r0) + vy - in.y);
    }
}
