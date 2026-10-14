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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.control.InGameController;
import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.AbstractGoods;
import net.sf.freecol.common.model.BuildableType;
import net.sf.freecol.common.model.BuildingType;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.UnitType;


/**
 * The classic-UI <b>build queue</b> screen — the original 1994
 * <em>Colonization</em>'s "what shall we build?" list.
 *
 * <p>The original game does not offer a modern drag-reorderable multi-item
 * queue (see FreeCol's own {@code BuildQueuePanel}); it offers a simple list of
 * what a colony can currently build, one thing picked at a time. This mirrors
 * that: every candidate {@link BuildableType} the colony can legally build next
 * ({@link Colony#canBuild(BuildableType)}) is listed with its icon, name and the
 * goods still needed to finish it; clicking a row sets it as the colony's sole
 * build target ({@link InGameController#setBuildQueue}) and closes the screen,
 * exactly as choosing from the original's build menu does.
 *
 * <p>Painted into a virtual <b>320&times;200</b> canvas, up-scaled by the
 * largest integer factor that fits the window, nearest-neighbour, hosted in the
 * main window over its colony screen (M4.0, {@link ClassicScreenStack}; the
 * original's wood list box over the colony comes in M4.11). No
 * reference screenshot of the original's build-selection screen has surfaced
 * (see {@code CLASSIC_UI_PLAN.md}), so the look here — a plain list over a dark
 * plate, in the same gold/green palette as the other screens — is a considered
 * placeholder, not a faithfulness claim.
 */
final class ClassicBuildQueuePanel extends JPanel {

    /** The original VGA canvas this screen is laid out in. */
    private static final int VW = 320;
    private static final int VH = 200;

    private static final int TITLE_H = 9;

    /** Baseline of the header caption. */
    private static final int HEAD_Y = TITLE_H + 8;

    /** Row pitch; a row's cell spans {@code [y-ROW_H+3, y+3)} about its baseline. */
    private static final int ROW_H = 15;

    /** Baseline of the first row — a full row-pitch below the header. */
    private static final int ROW_Y0 = HEAD_Y + ROW_H;

    private static final int CLOSE_W = 30;
    private static final int CLOSE_H = 11;
    private static final int CLOSE_X = VW - CLOSE_W - 3;
    private static final int CLOSE_Y = VH - CLOSE_H - 3;

    /** The lowest y a row may paint into (above the close plate). */
    private static final int BODY_BOTTOM = CLOSE_Y - 2;

    private static final Color TITLE_BG = new Color(0x00, 0x00, 0x00);
    private static final Color GOLD = new Color(0xC8, 0xB0, 0x40);
    private static final Color BG = new Color(0x20, 0x18, 0x10);
    private static final Color ROW_ALT = new Color(0x00, 0x00, 0x00, 0x40);
    private static final Color CURRENT_BG = new Color(0x40, 0x60, 0x30, 0xB0);
    private static final Color FG = new Color(0xFF, 0xF0, 0xD0);
    private static final Color TAG_FG = new Color(0xFF, 0xFF, 0xFF);
    private static final Color CLOSE_BG = new Color(0x30, 0x28, 0x10);
    private static final Color CLOSE_FG = new Color(0xF0, 0xD8, 0x8C);

    private final FreeColClient freeColClient;
    private final ImageLibrary lib;
    private final Colony colony;

    /** Run when the screen is dismissed (Escape / Close / a selection made). */
    private final Runnable onClose;

    /** Every buildable the colony can legally build next, in spec order. */
    private final List<BuildableType> candidates = new ArrayList<>();

    /** Virtual-space row bounds, rebuilt each paint, parallel to {@link #candidates}. */
    private final List<Rectangle> rowBounds = new ArrayList<>();

    /** Device-space scale + origin of the virtual canvas, set on each paint. */
    private int scale = 1;
    private int originX;
    private int originY;


    ClassicBuildQueuePanel(FreeColClient freeColClient, ImageLibrary lib,
                           Colony colony, Runnable onClose) {
        this.freeColClient = freeColClient;
        this.lib = lib;
        this.colony = colony;
        this.onClose = onClose;
        collectCandidates();
        setOpaque(true);
        setBackground(Color.BLACK);
        setPreferredSize(new Dimension(VW * 3, VH * 3));
        setFocusable(true);
        installKeyBindings();
        addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    onClick(e);
                }
            });
    }


    // Candidate list

    /**
     * Every {@link BuildingType} / {@link UnitType} the colony can build next
     * right now, in specification order — buildings, then buildable units.
     * {@link Colony#canBuild(BuildableType)} already excludes buildings the
     * colony has built (or is upgrading past), population/ability/limit
     * shortfalls and non-coastal-only mismatches, so no further filtering is
     * needed for this single-pick list.
     */
    private void collectCandidates() {
        final Specification spec = this.colony.getSpecification();
        for (BuildingType bt : spec.getBuildingTypeList()) {
            if (bt.needsGoodsToBuild() && this.colony.canBuild(bt)) {
                this.candidates.add(bt);
            }
        }
        for (UnitType ut : spec.getBuildableUnitTypes()) {
            if (this.colony.canBuild(ut)) this.candidates.add(ut);
        }
    }


    // Input

    private void installKeyBindings() {
        final InputMap im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        final ActionMap am = getActionMap();
        im.put(KeyStroke.getKeyStroke("ESCAPE"), "classic_closeBuildQueue");
        am.put("classic_closeBuildQueue", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    close();
                }
            });
    }

    private void onClick(MouseEvent e) {
        if (this.scale <= 0) return;
        final int vx = (e.getX() - this.originX) / this.scale;
        final int vy = (e.getY() - this.originY) / this.scale;
        if (vx >= CLOSE_X && vy >= CLOSE_Y) {
            close();
            return;
        }
        for (int i = 0; i < this.rowBounds.size(); i++) {
            if (this.rowBounds.get(i).contains(vx, vy)) {
                pick(this.candidates.get(i));
                return;
            }
        }
    }

    /** Set {@code bt} as the colony's sole build target, then close (as the
     * original's build menu does: choose one thing, done). */
    private void pick(BuildableType bt) {
        this.freeColClient.getInGameController()
            .setBuildQueue(this.colony, List.of(bt));
        close();
    }

    private void close() {
        if (this.onClose != null) this.onClose.run();
    }


    // Painting

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        final Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                           RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                           RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        this.scale = Math.max(1, Math.min(getWidth() / VW, getHeight() / VH));
        this.originX = (getWidth() - VW * this.scale) / 2;
        this.originY = (getHeight() - VH * this.scale) / 2;
        g.translate(this.originX, this.originY);
        g.scale(this.scale, this.scale);
        g.clipRect(0, 0, VW, VH);

        g.setColor(BG);
        g.fillRect(0, 0, VW, VH);
        paintTitle(g);
        paintRows(g);
        paintClose(g);
        g.dispose();
    }

    private void paintTitle(Graphics2D g) {
        g.setColor(TITLE_BG);
        g.fillRect(0, 0, VW, TITLE_H);
        g.setColor(GOLD);
        g.setFont(font(7f, Font.BOLD));
        final String s = this.colony.getName();
        g.drawString(s, (VW - g.getFontMetrics().stringWidth(s)) / 2, 7);
    }

    private void paintRows(Graphics2D g) {
        this.rowBounds.clear();
        g.setFont(font(7f, Font.BOLD));
        g.setColor(GOLD);
        final String head = Messages.message("classic.buildQueue.header");
        g.drawString(head, 4, HEAD_Y);

        if (this.candidates.isEmpty()) {
            g.setColor(FG);
            g.drawString(Messages.message("classic.buildQueue.empty"), 4, ROW_Y0);
            return;
        }

        final Player owner = this.colony.getOwner();
        final BuildableType current = this.colony.getCurrentlyBuilding();
        int y = ROW_Y0;
        int i = 0;
        for (BuildableType bt : this.candidates) {
            // Clip on the row's own content extent, not a full next row's
            // headroom (the off-by-one that dropped a row in two other
            // reports — see the README's clipping-margin note).
            if (y > BODY_BOTTOM) break;
            if ((i & 1) == 1) {
                g.setColor(ROW_ALT);
                g.fillRect(2, y - ROW_H + 3, VW - 4, ROW_H);
            }
            if (bt == current) {
                g.setColor(CURRENT_BG);
                g.fillRect(2, y - ROW_H + 3, VW - 4, ROW_H);
            }
            this.rowBounds.add(new Rectangle(2, y - ROW_H + 3, VW - 4, ROW_H));

            final BufferedImage icon = this.lib.getSmallBuildableTypeImageWithWithSize(
                bt, owner, new Dimension(ROW_H - 2, ROW_H - 2));
            if (icon != null) g.drawImage(icon, 4, y - ROW_H + 4, null);

            g.setFont(font(7f, Font.PLAIN));
            g.setColor(FG);
            g.drawString(Messages.getName(bt), ROW_H + 8, y);

            paintRequiredGoods(g, bt, y);
            y += ROW_H;
            i++;
        }
    }

    /** The goods still needed to finish {@code bt}, as icon+amount tags. */
    private void paintRequiredGoods(Graphics2D g, BuildableType bt, int y) {
        final List<AbstractGoods> required = this.colony.getRequiredGoods(bt);
        int x = VW - 8;
        g.setFont(font(6f, Font.BOLD));
        for (int j = required.size() - 1; j >= 0; j--) {
            final AbstractGoods ag = required.get(j);
            final String s = String.valueOf(ag.getAmount());
            final int tw = g.getFontMetrics().stringWidth(s);
            x -= tw;
            g.setColor(TAG_FG);
            g.drawString(s, x, y);
            x -= 2;
            final BufferedImage icon = this.lib.getScaledGoodsTypeImage(ag.getType());
            if (icon != null) {
                x -= 9;
                g.drawImage(icon, x, y - 8, 9, 9, null);
            }
            x -= 6;
        }
    }

    private void paintClose(Graphics2D g) {
        g.setColor(CLOSE_BG);
        g.fillRect(CLOSE_X, CLOSE_Y, CLOSE_W, CLOSE_H);
        g.setColor(GOLD);
        g.drawRect(CLOSE_X, CLOSE_Y, CLOSE_W - 1, CLOSE_H - 1);
        g.setColor(CLOSE_FG);
        g.setFont(font(7f, Font.BOLD));
        final String s = Messages.message("close");
        g.drawString(s, CLOSE_X + (CLOSE_W - g.getFontMetrics().stringWidth(s)) / 2,
                     CLOSE_Y + CLOSE_H - 3);
    }

    /** A font in <em>virtual</em> pixels — the paint transform scales it up. */
    private Font font(float size, int style) {
        return getFont().deriveFont(style, size);
    }
}
