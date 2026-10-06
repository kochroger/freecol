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
 * the unit block is hidden.
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

    /** The minimap shown in the last paint (for click-to-recentre). */
    private ClassicHud.MinimapModel lastMinimap = null;

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

    /** The canvas scale: this component is 80 virtual pixels wide. */
    private int scale() {
        return Math.max(1, getWidth() / ClassicHud.PANEL_W);
    }

    /**
     * The panel's model from the live game.
     *
     * @return The model; never null (an empty panel without a game).
     */
    ClassicHud.PanelModel model() {
        final Game game = this.freeColClient.getGame();
        final Player player = this.freeColClient.getMyPlayer();
        ClassicHud.MinimapModel mm = null;
        String season = null, gold = null;
        if (game != null && game.getMap() != null) {
            final int[] o = this.mapViewer.viewOrigin();
            if (o != null) {
                mm = blinkDot(ClassicHud.minimapOf(game.getMap(), o[0], o[1]),
                              this.mapViewer.getActiveUnit(),
                              this.mapViewer.isBlinkOff());
            }
        }
        if (game != null && game.getTurn() != null) {
            season = ClassicHud.seasonLine(this.text, game.getTurn().getSeason(),
                                           game.getTurn().getYear());
            if (season == null) {
                season = Messages.message(game.getTurn().getLabel());
            }
        }
        if (player != null) {
            gold = ClassicHud.goldLine(this.text, Math.max(0, player.getGold()),
                                       player.getTax());
            if (gold == null) {
                gold = Messages.message("gold") + ": " + player.getGold()
                    + "  " + Messages.message("tax") + ": " + player.getTax();
            }
        }
        ClassicHud.UnitFacts active = null;
        final List<ClassicHud.UnitFacts> list = new ArrayList<>();
        final Unit unit = this.mapViewer.getActiveUnit();
        if (unit != null && unit.hasTile()) {
            active = facts(unit);
            final List<Unit> others = new ArrayList<>();
            if (unit.isCarrier() && unit.hasCargo()) {
                others.addAll(unit.getUnitList());
            } else {
                final Tile t = unit.getTile();
                for (Unit u : t.getUnitList()) {
                    if (u != unit) others.add(u);
                }
            }
            for (Unit u : others) list.add(facts(u));
        }
        return new ClassicHud.PanelModel(mm, season, gold, this.scene, active, list);
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
        final BufferedImage img = new BufferedImage(ClassicHud.PANEL_W,
            ClassicHud.PANEL_H, BufferedImage.TYPE_INT_RGB);
        final Graphics2D ig = img.createGraphics();
        try {
            ig.translate(-ClassicHud.PANEL_X, -ClassicHud.PANEL_Y);
            final ClassicHud.PanelModel p = model();
            this.lastMinimap = p.minimap;
            ClassicHud.paintPanel(ig, this.font, this.wood, this.text, p);
        } catch (RuntimeException e) {
            if (!this.failLogged) {
                this.failLogged = true;
                logger.log(Level.WARNING, "Classic panel paint failed", e);
            }
        } finally {
            ig.dispose();
        }
        ClassicMenuStrip.blit(g, img, 0, 0, scale());
        // The panel refresh after a (re)activation starts the blink's
        // phase: now, as its pixels are on the screen.
        this.mapViewer.blinkPanelPainted();
    }

    /** A click in the minimap's interior recentres the map on that tile. */
    private void onClick(MouseEvent e) {
        final ClassicHud.MinimapModel m = this.lastMinimap;
        if (m == null) return;
        final int s = scale();
        final int vx = ClassicHud.PANEL_X + e.getX() / s;
        final int vy = ClassicHud.PANEL_Y + e.getY() / s;
        final Rectangle in = ClassicHud.MINIMAP;
        if (!in.contains(vx, vy)) return;
        this.mapViewer.recenterOnTile(
            ClassicHud.minimapOriginX(m.mapWidth, m.c0) + vx - in.x,
            ClassicHud.minimapOriginY(m.mapHeight, m.r0) + vy - in.y);
    }
}
