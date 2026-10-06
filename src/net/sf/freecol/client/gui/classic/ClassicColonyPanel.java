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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import net.sf.freecol.common.model.Building;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.ColonyTile;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;


/**
 * The classic-UI <b>colony screen</b> — the signature screen of the original
 * 1994 <em>Colonization</em>.
 *
 * <p>Everything is painted into a virtual <b>320&times;200</b> canvas (the
 * original's VGA resolution) which is then up-scaled by the largest integer
 * factor that fits the window, nearest-neighbour, so the classic pixels stay
 * crisp and the layout constants below can be read straight off the original
 * screenshots.  Top to bottom:
 *
 * <ul>
 *   <li><b>Title bar</b> — colony name, turn and treasury in gold on black.</li>
 *   <li><b>Buildings pane</b> (left) — the colony's buildings on the sandy
 *   ground, each with the colonists working in it and a black production tag
 *   ({@code amount} + goods icon), drawn from the original {@code BUILDING.SS}
 *   sprite set.</li>
 *   <li><b>Tile pane</b> (right) — the 3&times;3 grid of the colony's work
 *   tiles on the wood panel ({@code WOODTILE.SS}), each worked tile carrying its
 *   colonist and a production tag.  The centre cell is the colony itself.</li>
 *   <li><b>Bottom band</b> — the original {@code COLONY.PIK} chrome (320&times;72)
 *   blitted as-is, with the Sons-of-Liberty / population figures and the units
 *   outside the colony on the left, the ships in port in the middle, the net
 *   production on the right, and the 16-slot <b>warehouse</b> row of goods icons
 *   and amounts along the bottom.</li>
 * </ul>
 *
 * <p><b>Building frame map.</b> {@code BUILDING.SS} holds 48 frames.  The
 * seven buildings a new colony starts with are matched against the
 * original's colony screen (clip008, population 1: every sprite found with
 * match 1.000): the town hall, the carpenter's and the five craft houses.
 * Their upgrades are inferred as the consecutive frames after each house,
 * the fortifications and harbour scenes by their size and slot; the
 * buildings no frame could be tied to yet (custom house, armory chain)
 * draw no sprite.  See {@link #BUILDING_FRAMES}.  Each
 * building also carries its localized <b>name tag</b>, which the original
 * only shows on hover, so a mis-mapped sprite stays legible.
 *
 * <p>Interaction is click-to-select, click-to-target — the same style as every
 * other classic screen (order buttons, report rows, the build queue, Europe
 * boarding) rather than drag-and-drop: click a colonist (standing in the
 * colony, already working a building, or already working a tile) to select
 * it, then click a building or work tile to move it there via
 * {@link net.sf.freecol.client.control.InGameController#work}. Loading cargo
 * and per-nation tints remain later slices; for now Escape or a click on the
 * red exit button closes the screen.
 */
final class ClassicColonyPanel extends JPanel {

    /** The original VGA canvas this screen is laid out in. */
    private static final int VW = 320;
    private static final int VH = 200;

    /** Title bar: full width, gold on black. */
    private static final int TITLE_H = 9;

    /** The colony area (buildings + work tiles) sits between title and band. */
    private static final int AREA_Y = TITLE_H;
    private static final int BUILD_W = 204;

    /**
     * The construction indicator — current build + progress, clickable to open
     * the build-queue screen ({@link ClassicBuildQueuePanel}) — sits atop the
     * buildings pane, mirroring FreeCol's own {@code ConstructionPanel} (whose
     * click opens {@code GUI.showBuildQueuePanel} the same way).
     */
    private static final int CONSTR_H = 11;

    /** The {@code COLONY.PIK} chrome occupies the bottom 72 rows. */
    private static final int BAND_Y = 128;

    /** Work-tile cell size: the native 16px tiles at a crisp integer 2x. */
    private static final int GRID_CELL = 32;
    private static final int GRID_X = 213;
    private static final int GRID_Y = 19;

    /** Warehouse row (inside the COLONY.PIK chrome). */
    private static final int WARE_Y = 178;
    private static final int WARE_H = 22;
    private static final int WARE_W = 306;

    /** The three sub-panels of the band, and their shared top. */
    private static final int PANEL_Y = 130;
    private static final int PANEL1_X = 3;
    private static final int PANEL2_X = 122;
    private static final int PANEL3_X = 207;

    private static final Color TITLE_BG = new Color(0x00, 0x00, 0x00);
    private static final Color GOLD = new Color(0xC8, 0xB0, 0x40);
    private static final Color TAG_BG = new Color(0x00, 0x00, 0x00, 0xC0);
    private static final Color TAG_FG = new Color(0xFF, 0xFF, 0xFF);
    private static final Color SAND = new Color(0xEB, 0xDB, 0xA2);
    private static final Color WOOD = new Color(0x49, 0x28, 0x1C);
    private static final Color FRAME = new Color(0x00, 0x00, 0x00);
    private static final Color SELECT = new Color(0x40, 0xE0, 0x40);
    private static final Color PICKED = new Color(0xFF, 0xFF, 0x00);
    private static final Color WORK_HINT = new Color(0xE8, 0xC8, 0x40);

    /**
     * FreeCol building-type id (minus the {@code model.building.} prefix) to
     * {@code BUILDING.SS} frame.  Types absent from the map draw no sprite:
     * {@code depot} and {@code country}, which have no structure of their own
     * in the original, and the ones no frame is tied to yet.
     *
     * <p>Sources (plan D0e; clip008 {@code 01-colony.md} &sect;2.3):
     * <ul>
     *   <li><b>Matched</b> on the original's colony screen at population 1:
     *   the town hall and the six houses a new colony starts with.</li>
     *   <li><b>Inferred</b>: each house's two upgrades are the next two
     *   frames (same size class, the original numbers each chain in a row);
     *   the lumber mill follows the carpenter's, the church and cathedral are
     *   the two other 53&times;37 frames after it; the harbour scenes and
     *   the walls by their size and slot (where tree frame 45 and the fence
     *   16 stand in a new colony).  The schools, chapel, press, storage and
     *   stables are older guesses by look, kept until a developed colony is
     *   seen.</li>
     *   <li><b>Unmapped</b> until a capture shows them: the custom house and
     *   the armory, magazine and arsenal.  The 23&times;27 frames 18-20 are
     *   likely candidates (20 is the newspaper's older guess), but they do
     *   not fit one-to-one.</li>
     * </ul>
     */
    private static final Map<String, Integer> BUILDING_FRAMES = new HashMap<>();
    static {
        // Fortification walls (inferred: the fence 16's size and slot).
        BUILDING_FRAMES.put("stockade", 0);
        BUILDING_FRAMES.put("fort", 1);
        BUILDING_FRAMES.put("fortress", 2);
        // Harbour scenes (inferred: tree frame 45 is the scene without a pier).
        BUILDING_FRAMES.put("docks", 6);
        BUILDING_FRAMES.put("drydock", 7);
        BUILDING_FRAMES.put("shipyard", 8);
        // The town hall (matched).
        BUILDING_FRAMES.put("townHall", 9);
        // The press (by look).
        BUILDING_FRAMES.put("printingPress", 13);
        BUILDING_FRAMES.put("newspaper", 20);
        // Churches: the chapel by look, the two 53x37 frames inferred.
        BUILDING_FRAMES.put("chapel", 12);
        BUILDING_FRAMES.put("church", 37);
        BUILDING_FRAMES.put("cathedral", 38);
        // Schooling (by look).
        BUILDING_FRAMES.put("schoolhouse", 3);
        BUILDING_FRAMES.put("college", 4);
        BUILDING_FRAMES.put("university", 5);
        // The goods chains: each house matched, its upgrades the next frames.
        BUILDING_FRAMES.put("weaverHouse", 21);
        BUILDING_FRAMES.put("weaverShop", 22);
        BUILDING_FRAMES.put("textileMill", 23);
        BUILDING_FRAMES.put("tobacconistHouse", 24);
        BUILDING_FRAMES.put("tobacconistShop", 25);
        BUILDING_FRAMES.put("cigarFactory", 26);
        BUILDING_FRAMES.put("distillerHouse", 27);
        BUILDING_FRAMES.put("rumDistillery", 28);
        BUILDING_FRAMES.put("rumFactory", 29);
        BUILDING_FRAMES.put("furTraderHouse", 32);
        BUILDING_FRAMES.put("furTradingPost", 33);
        BUILDING_FRAMES.put("furFactory", 34);
        BUILDING_FRAMES.put("carpenterHouse", 35);
        BUILDING_FRAMES.put("lumberMill", 36);
        BUILDING_FRAMES.put("blacksmithHouse", 39);
        BUILDING_FRAMES.put("blacksmithShop", 40);
        BUILDING_FRAMES.put("ironWorks", 41);
        // Storage and livestock (by look).
        BUILDING_FRAMES.put("warehouse", 47);
        BUILDING_FRAMES.put("warehouseExpansion", 15);
        BUILDING_FRAMES.put("stables", 46);
    }

    /**
     * The {@code BUILDING.SS} frame of a building type.
     *
     * @param typeId The building type's id, with or without the
     *     {@code model.building.} prefix.
     * @return The frame, or null when the type draws no sprite.
     */
    static Integer buildingFrame(String typeId) {
        return BUILDING_FRAMES.get(shortId(typeId));
    }

    private final FreeColClient freeColClient;
    private final ImageLibrary lib;
    private final Colony colony;

    /** Run when the screen is dismissed (Escape / the exit button). */
    private final Runnable onClose;

    /** Cache of {@code BUILDING.SS} frames, loaded on demand. */
    private final Map<Integer, BufferedImage> buildingCache = new HashMap<>();

    /**
     * Per-building hover targets, rebuilt each paint: virtual-space bounds and
     * the localized name.  The original shows a building's name only when the
     * pointer is over it, which also keeps the names from overlapping.
     */
    private final java.util.List<java.awt.Rectangle> buildingBounds
        = new java.util.ArrayList<>();
    private final java.util.List<String> buildingNames = new java.util.ArrayList<>();

    /** The {@link Building}s parallel to {@link #buildingBounds}, as work targets. */
    private final List<Building> buildingTargets = new ArrayList<>();

    /**
     * While recording: each drawn building's type, frame and place, as
     * the last paint left them, and as last logged (see
     * {@link #recordBuildings}).
     */
    private final StringBuilder buildingsDrawn = new StringBuilder();
    private String buildingsLogged = null;

    /** The stills taken so far, for their names (EDT only). */
    private static int stills = 0;

    /** The construction indicator's clickable band, set on each paint. */
    private final java.awt.Rectangle constructionBounds
        = new java.awt.Rectangle(0, AREA_Y, BUILD_W, CONSTR_H);

    /** Index into {@link #buildingBounds} of the hovered building, or -1. */
    private int hovered = -1;

    /**
     * Work-assignment interaction: select a colonist — standing in the colony,
     * or already working a building/tile — then click a building or work tile
     * to move it there via {@link InGameController#work}. Click-to-select,
     * click-to-target — the same style as Europe boarding, rather than
     * introducing drag-and-drop as a new interaction paradigm.
     */
    private Unit selectedUnit;

    /**
     * Every clickable colonist sprite on this screen (standing in the colony,
     * working a building, or working a tile), rebuilt each paint: virtual-space
     * bounds + unit.
     */
    private final List<Rectangle> unitBounds = new ArrayList<>();
    private final List<Unit> unitTargets = new ArrayList<>();

    /** The work-tile grid's clickable cells (excluding the centre), rebuilt each paint. */
    private final List<Rectangle> tileBounds = new ArrayList<>();
    private final List<ColonyTile> tileTargets = new ArrayList<>();

    /** Device-space scale + origin of the virtual canvas, set on each paint. */
    private int scale = 1;
    private int originX;
    private int originY;


    ClassicColonyPanel(FreeColClient freeColClient, ImageLibrary lib,
                       Colony colony, Runnable onClose) {
        this.freeColClient = freeColClient;
        this.lib = lib;
        this.colony = colony;
        this.onClose = onClose;
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
        addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    onHover(e);
                }
            });
    }


    // Assets

    private static String ssKey(String set, int frame) {
        return String.format("image.classic_original.ss.%s.%03d", set, frame);
    }

    /** Load (and cache) a {@code BUILDING.SS} frame; null when the pack is absent. */
    private BufferedImage buildingImage(int frame) {
        return this.buildingCache.computeIfAbsent(frame,
            n -> ImageLibrary.getUnscaledImage(ssKey("BUILDING.SS", n)));
    }


    // Dismissal

    private void installKeyBindings() {
        final InputMap im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        final ActionMap am = getActionMap();
        im.put(KeyStroke.getKeyStroke("ESCAPE"), "classic_closeColony");
        am.put("classic_closeColony", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    close();
                }
            });
    }

    /**
     * The original's exit button is the red "E" at the bottom right of the
     * {@code COLONY.PIK} chrome; a click anywhere on it closes the screen.
     * Otherwise: a click on a colonist selects it (see {@link #selectUnit}); a
     * click on a building or work tile while one is selected moves it there
     * (see {@link #assignWork}).
     */
    private void onClick(MouseEvent e) {
        final int vx = (e.getX() - this.originX) / this.scale;
        final int vy = (e.getY() - this.originY) / this.scale;
        if (this.constructionBounds.contains(vx, vy)) {
            this.freeColClient.getGUI().showBuildQueuePanel(this.colony);
            return;
        }
        if (vx >= WARE_W && vy >= WARE_Y) { close(); return; }

        for (int i = 0; i < this.unitBounds.size(); i++) {
            if (this.unitBounds.get(i).contains(vx, vy)) {
                selectUnit(this.unitTargets.get(i));
                return;
            }
        }
        if (this.selectedUnit == null) return;
        for (int i = 0; i < this.buildingBounds.size(); i++) {
            if (this.buildingBounds.get(i).contains(vx, vy)) {
                assignWork(this.buildingTargets.get(i));
                return;
            }
        }
        for (int i = 0; i < this.tileBounds.size(); i++) {
            if (this.tileBounds.get(i).contains(vx, vy)) {
                assignWork(this.tileTargets.get(i));
                return;
            }
        }
    }

    /** Select (or, on a second click of the same unit, deselect) a colonist to move. */
    private void selectUnit(Unit unit) {
        this.selectedUnit = (this.selectedUnit == unit) ? null : unit;
        repaint();
    }

    /**
     * Move the selected colonist into {@code target} via the real controller —
     * the same {@link InGameController#work} call {@code ColonyPanel}/
     * {@code UnitLabel} make in the standard UI, for both a building and a work
     * tile ({@code Building}/{@code ColonyTile} both implement
     * {@code WorkLocation}). It already claims an unowned tile and confirms
     * abandoning education when needed, so no extra guarding is required here.
     */
    private void assignWork(net.sf.freecol.common.model.WorkLocation target) {
        igc().work(this.selectedUnit, target);
        this.selectedUnit = null;
        refresh();
    }

    /** Track which building the pointer is over, and repaint if it changed. */
    private void onHover(MouseEvent e) {
        if (this.scale <= 0) return;
        final int vx = (e.getX() - this.originX) / this.scale;
        final int vy = (e.getY() - this.originY) / this.scale;
        int found = -1;
        for (int i = 0; i < this.buildingBounds.size(); i++) {
            if (this.buildingBounds.get(i).contains(vx, vy)) {
                found = i;
                break;
            }
        }
        if (found != this.hovered) {
            this.hovered = found;
            repaint();
        }
    }

    private void close() {
        if (this.onClose != null) this.onClose.run();
    }

    private InGameController igc() {
        return this.freeColClient.getInGameController();
    }

    /** Repaint after a model change (a build-queue pick, production, gold). */
    void refresh() {
        repaint();
    }


    // Painting

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        final Graphics2D g = (Graphics2D) g0.create();

        // Fit the 320x200 canvas into the window at the largest integer scale.
        this.scale = Math.max(1, Math.min(getWidth() / VW, getHeight() / VH));
        this.originX = (getWidth() - VW * this.scale) / 2;
        this.originY = (getHeight() - VH * this.scale) / 2;
        g.translate(this.originX, this.originY);
        g.scale(this.scale, this.scale);
        paintCanvas(g);
        g.dispose();
        recordBuildings();
    }

    /** Paint the 320x200 canvas into {@code g}, in canvas coordinates. */
    private void paintCanvas(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                           RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                           RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.clipRect(0, 0, VW, VH);

        // Rebuilt across paintBuildings/paintWorkTiles/paintPopulation below,
        // so cleared once here rather than per-method (unlike buildingBounds,
        // which only one method populates).
        this.unitBounds.clear();
        this.unitTargets.clear();

        paintTitle(g);
        paintBuildings(g);
        paintConstruction(g);
        paintWorkTiles(g);
        paintBand(g);
        paintHover(g);
    }

    /**
     * While the acceptance recorder runs: log which sprite frame each
     * building got and where ({@code colony-buildings}), and save the
     * canvas as a still, whenever that changes.  This window is not in the
     * HUD copy the recorder samples.
     */
    private void recordBuildings() {
        if (!ClassicFrameRecorder.on()) return;
        final String drawn = this.buildingsDrawn.toString();
        if (drawn.equals(this.buildingsLogged)) return;
        this.buildingsLogged = drawn;
        final String name = "colony-" + (++stills);
        ClassicFrameRecorder.event("colony-buildings",
            name + " " + this.colony.getId() + drawn);
        final BufferedImage still
            = new BufferedImage(VW, VH, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = still.createGraphics();
        try {
            paintCanvas(g);
        } finally {
            g.dispose();
        }
        ClassicFrameRecorder.still(name, still);
    }

    /**
     * The construction indicator: the colony's current build target, an icon
     * and its name, plus the goods still needed to finish it — clicking
     * anywhere in the band opens the build-queue screen. Mirrors FreeCol's own
     * {@code ConstructionPanel} (same click target, same seam), over its own
     * dark plate atop the buildings pane so it reads regardless of what
     * building sprite happens to be underneath.
     */
    private void paintConstruction(Graphics2D g) {
        g.setColor(TAG_BG);
        g.fillRect(0, AREA_Y, BUILD_W, CONSTR_H);
        g.setColor(GOLD);
        g.drawRect(0, AREA_Y, BUILD_W - 1, CONSTR_H - 1);

        final net.sf.freecol.common.model.BuildableType current
            = this.colony.getCurrentlyBuilding();
        g.setFont(font(6f, Font.PLAIN));
        if (current == null) {
            g.setColor(TAG_FG);
            g.drawString(Messages.message("classic.buildQueue.empty"),
                         3, AREA_Y + CONSTR_H - 3);
            return;
        }
        final BufferedImage icon = this.lib.getSmallBuildableTypeImageWithWithSize(
            current, this.colony.getOwner(),
            new Dimension(CONSTR_H - 2, CONSTR_H - 2));
        if (icon != null) g.drawImage(icon, 1, AREA_Y + 1, null);
        g.setColor(TAG_FG);
        g.drawString(Messages.getName(current), CONSTR_H + 2,
                     AREA_Y + CONSTR_H - 3);
    }

    /** The gold-on-black header: colony name, turn, treasury. */
    private void paintTitle(Graphics2D g) {
        g.setColor(TITLE_BG);
        g.fillRect(0, 0, VW, TITLE_H);
        final Game game = this.freeColClient.getGame();
        final Player player = this.freeColClient.getMyPlayer();
        final StringBuilder sb = new StringBuilder(this.colony.getName());
        if (game != null && game.getTurn() != null) {
            sb.append(", ").append(msg(game.getTurn().getLabel()));
        }
        if (player != null) {
            sb.append(", ").append(Messages.message("gold")).append(": ")
                .append(player.getGold());
        }
        g.setColor(GOLD);
        g.setFont(font(7f, Font.BOLD));
        final String s = sb.toString();
        g.drawString(s, (VW - g.getFontMetrics().stringWidth(s)) / 2, 7);
    }

    /**
     * The buildings pane: the sandy ground, then every building in the colony —
     * its sprite, the colonists working inside it, its production tag and its
     * name.  The original places each building at a fixed spot on the ground;
     * this first slice flows them left-to-right in rows instead (a documented
     * deviation — the fixed original layout wants the expert's slot map).
     */
    private void paintBuildings(Graphics2D g) {
        // Ground: the original tan colony ground (TERRAIN.SS.001), tiled.
        final BufferedImage ground = ImageLibrary.getUnscaledImage(ssKey("TERRAIN.SS", 1));
        fillTiled(g, ground, SAND, 0, AREA_Y, BUILD_W, BAND_Y - AREA_Y);

        this.buildingBounds.clear();
        this.buildingNames.clear();
        this.buildingTargets.clear();
        this.buildingsDrawn.setLength(0);
        final boolean record = ClassicFrameRecorder.on();
        int x = 4;
        int y = AREA_Y + CONSTR_H + 5;
        int rowH = 0;
        for (Building b : this.colony.getBuildings()) {
            final Integer frame = buildingFrame(b.getType().getId());
            final BufferedImage img = (frame == null) ? null : buildingImage(frame);
            final int w = (img != null) ? img.getWidth() : 24;
            final int h = (img != null) ? img.getHeight() : 20;
            if (x + w > BUILD_W - 4) {           // wrap
                x = 4;
                y += rowH + 12;
                rowH = 0;
            }
            if (y + h > BAND_Y - 6) break;       // out of ground; rest are hidden
            if (record) {
                this.buildingsDrawn.append(' ')
                    .append(shortId(b.getType().getId())).append('=')
                    .append((frame == null) ? "-" : frame.toString())
                    .append('@').append(x).append(',').append(y);
                if (img != null) {
                    this.buildingsDrawn.append(':').append(w).append('x').append(h);
                }
            }
            if (img != null) {
                g.drawImage(img, x, y, null);
            } else {
                g.setColor(WOOD);
                g.fillRect(x, y + h - 6, w, 6);
            }
            paintWorkers(g, b.getUnitList(), x + 2, y + h - 2);
            paintProductionTag(g, b.getProductionInfo(), x, y - 1);
            // The name is drawn only for the hovered building (see paintHover),
            // as in the original — so record the target instead of drawing now.
            this.buildingBounds.add(new java.awt.Rectangle(x, y, w, h));
            this.buildingNames.add(Messages.getName(b.getType()));
            this.buildingTargets.add(b);
            // A colonist is selected: hint every building it could move into.
            if (this.selectedUnit != null) {
                g.setColor(WORK_HINT);
                g.drawRect(x, y, w - 1, h - 1);
            }
            rowH = Math.max(rowH, h);
            x += w + 6;
        }
    }

    /** Draw the hovered building's name plate over everything, as a tooltip. */
    private void paintHover(Graphics2D g) {
        if (this.hovered < 0 || this.hovered >= this.buildingBounds.size()) return;
        final java.awt.Rectangle r = this.buildingBounds.get(this.hovered);
        paintName(g, this.buildingNames.get(this.hovered),
                  r.x - 8, r.y - 2, r.width + 16);
    }

    /**
     * The work-tile pane: the wood panel and, inset in it, the 3&times;3 grid of
     * the colony's {@link ColonyTile}s — the colony itself in the centre cell and
     * its eight neighbours around it, each drawn with the same terrain art as the
     * map, plus the colonist working it and what that tile yields.
     */
    private void paintWorkTiles(Graphics2D g) {
        final BufferedImage wood = ImageLibrary.getUnscaledImage(ssKey("WOODTILE.SS", 0));
        fillTiled(g, wood, WOOD, BUILD_W, AREA_Y, VW - BUILD_W, BAND_Y - AREA_Y);

        g.setColor(FRAME);
        g.drawRect(GRID_X - 1, GRID_Y - 1, GRID_CELL * 3 + 1, GRID_CELL * 3 + 1);

        this.tileBounds.clear();
        this.tileTargets.clear();
        final Tile centre = this.colony.getTile();
        for (ColonyTile ct : this.colony.getColonyTiles()) {
            final Tile t = ct.getWorkTile();
            if (t == null || centre == null) continue;
            // Place each cell by the work tile's compass Direction from the
            // colony, not its raw (x,y) offset: FreeCol's map is isometric, so
            // the eight neighbours' raw offsets do not fill a -1..+1 square.
            // The centre cell is the colony tile itself.
            final int[] cell = (t == centre)
                ? new int[] { 1, 1 } : cellForDirection(centre.getDirection(t));
            if (cell == null) continue;
            final int sx = GRID_X + cell[0] * GRID_CELL;
            final int sy = GRID_Y + cell[1] * GRID_CELL;

            final BufferedImage terrain = this.lib.getTerrainImage(
                t.getType(), t.getX(), t.getY(), new Dimension(16, 16));
            if (terrain != null) {
                g.drawImage(terrain, sx, sy, GRID_CELL, GRID_CELL, null);
            }
            if (t == centre) {
                final BufferedImage settlement
                    = this.lib.getScaledSettlementImage(this.colony);
                if (settlement != null) {
                    drawFitted(g, settlement, sx, sy, GRID_CELL);
                }
                continue;         // the centre tile works itself; not a target.
            }
            this.tileBounds.add(new Rectangle(sx, sy, GRID_CELL, GRID_CELL));
            this.tileTargets.add(ct);
            // A colonist is selected: hint every work tile it could move into.
            if (this.selectedUnit != null) {
                g.setColor(WORK_HINT);
                g.drawRect(sx, sy, GRID_CELL - 1, GRID_CELL - 1);
            }
            final List<Unit> workers = ct.getUnitList();
            if (!workers.isEmpty()) {
                g.setColor(SELECT);
                g.drawRect(sx, sy, GRID_CELL - 1, GRID_CELL - 1);
                paintWorkers(g, workers, sx + GRID_CELL / 2 - 6,
                             sy + GRID_CELL - 4);
                paintProductionTag(g, ct.getProductionInfo(), sx + 1, sy + 1);
            }
        }
    }

    /** The 3&times;3 grid cell {@code {col,row}} for a compass direction, or null. */
    private static int[] cellForDirection(net.sf.freecol.common.model.Direction d) {
        if (d == null) return null;
        switch (d) {
        case NW: return new int[] { 0, 0 };
        case N:  return new int[] { 1, 0 };
        case NE: return new int[] { 2, 0 };
        case W:  return new int[] { 0, 1 };
        case E:  return new int[] { 2, 1 };
        case SW: return new int[] { 0, 2 };
        case S:  return new int[] { 1, 2 };
        case SE: return new int[] { 2, 2 };
        default: return null;
        }
    }

    /**
     * The bottom band: the original {@code COLONY.PIK} chrome, then the live
     * numbers over it — Sons of Liberty and the units outside the colony (left),
     * the ships in port (middle), the colony's net production (right), and the
     * warehouse row of goods along the bottom.
     */
    private void paintBand(Graphics2D g) {
        final BufferedImage band
            = ImageLibrary.getUnscaledImage("image.background.ColonyPanel");
        if (band != null) {
            g.drawImage(band, 0, BAND_Y, VW, VH - BAND_Y, null);
        }
        paintPopulation(g);
        paintPort(g);
        paintNetProduction(g);
        paintWarehouse(g);
    }

    /** Left panel: the SoL / tory split and the units standing in the colony. */
    private void paintPopulation(Graphics2D g) {
        final int sol = this.colony.getSonsOfLiberty();
        final int count = this.colony.getUnitCount();
        final int rebels = count * sol / 100;
        g.setFont(font(7f, Font.BOLD));
        g.setColor(TAG_FG);
        g.drawString(sol + "% (" + rebels + ")", PANEL1_X + 12, PANEL_Y + 7);
        g.drawString((100 - sol) + "% (" + (count - rebels) + ")",
                     PANEL1_X + 62, PANEL_Y + 7);

        final Tile tile = this.colony.getTile();
        if (tile == null) return;
        int x = PANEL1_X + 3;
        for (Unit u : tile.getUnitList()) {
            if (u.isNaval()) continue;                 // ships go in the port
            if (x > PANEL2_X - 16) break;
            final BufferedImage img = this.lib.getScaledUnitImage(u);
            if (img != null) drawFitted(g, img, x, PANEL_Y + 12, 16);
            final Rectangle r = new Rectangle(x, PANEL_Y + 12, 16, 16);
            if (u == this.selectedUnit) {
                g.setColor(PICKED);
                g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
            }
            this.unitBounds.add(r);
            this.unitTargets.add(u);
            x += 12;
        }
    }

    /** Middle panel: the ships tied up at the dock, or the empty-harbour notice. */
    private void paintPort(Graphics2D g) {
        final Tile tile = this.colony.getTile();
        int x = PANEL2_X + 3;
        boolean any = false;
        if (tile != null) {
            for (Unit u : tile.getUnitList()) {
                if (!u.isNaval()) continue;
                if (x > PANEL3_X - 20) break;
                final BufferedImage img = this.lib.getScaledUnitImage(u);
                if (img != null) drawFitted(g, img, x, PANEL_Y + 10, 20);
                x += 20;
                any = true;
            }
        }
        if (!any) {
            // The original captions the empty dock ("Keine Schiffe im Hafen").
            // FreeCol has no such message, so reuse its own "In Port" caption.
            g.setFont(font(7f, Font.BOLD));
            g.setColor(new Color(0x60, 0x70, 0xA0));
            g.drawString(Messages.message("colonyPanel.inPort"),
                         PANEL2_X + 4, PANEL_Y + 7);
        }
    }

    /** Right panel: what the colony nets each turn, good by good. */
    private void paintNetProduction(Graphics2D g) {
        int x = PANEL3_X + 2;
        int y = PANEL_Y + 2;
        int n = 0;
        for (GoodsType gt : this.colony.getSpecification().getStorableGoodsTypeList()) {
            final int net = this.colony.getNetProductionOf(gt);
            if (net == 0) continue;
            if (n == 4) {                    // second row
                x = PANEL3_X + 2;
                y += 16;
            }
            if (n >= 8) break;
            final BufferedImage img = this.lib.getScaledGoodsTypeImage(gt);
            if (img != null) drawFitted(g, img, x, y, 12);
            tag(g, (net > 0 ? "+" : "") + net, x, y + 12);
            x += 22;
            n++;
        }
    }

    /** The warehouse row: every storable good, with what is in store. */
    private void paintWarehouse(Graphics2D g) {
        final List<GoodsType> goods
            = this.colony.getSpecification().getStorableGoodsTypeList();
        if (goods.isEmpty()) return;
        final int cw = WARE_W / goods.size();
        g.setFont(font(7f, Font.PLAIN));
        for (int i = 0; i < goods.size(); i++) {
            final GoodsType gt = goods.get(i);
            final int x = i * cw;
            final BufferedImage img = this.lib.getScaledGoodsTypeImage(gt);
            if (img != null) {
                drawFitted(g, img, x + (cw - 12) / 2, WARE_Y + 2, 12);
            }
            final String s = String.valueOf(this.colony.getGoodsCount(gt));
            g.setColor(TAG_FG);
            g.drawString(s, x + (cw - g.getFontMetrics().stringWidth(s)) / 2,
                         WARE_Y + WARE_H - 3);
        }
    }


    // Shared drawing helpers

    /** Draw the colonists of a work location in a row, ending at {@code (x, y)}. */
    private void paintWorkers(Graphics2D g, List<Unit> units, int x, int y) {
        for (Unit u : units) {
            final BufferedImage img = this.lib.getScaledUnitImage(u);
            if (img != null) drawFitted(g, img, x, y - 14, 14);
            final Rectangle r = new Rectangle(x, y - 14, 14, 14);
            if (u == this.selectedUnit) {
                g.setColor(PICKED);
                g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
            }
            this.unitBounds.add(r);
            this.unitTargets.add(u);
            x += 10;
        }
    }

    /**
     * The original's production tag: a black plate carrying the amount produced
     * and the goods icon, floating above the building or work tile.
     */
    private void paintProductionTag(Graphics2D g,
                                    net.sf.freecol.common.model.ProductionInfo pi,
                                    int x, int y) {
        if (pi == null) return;
        for (AbstractGoods ag : pi.getProduction()) {
            if (ag.getAmount() <= 0) continue;
            g.setFont(font(7f, Font.BOLD));
            final String s = String.valueOf(ag.getAmount());
            final int tw = g.getFontMetrics().stringWidth(s) + 13;
            g.setColor(TAG_BG);
            g.fillRect(x, y - 8, tw, 9);
            g.setColor(TAG_FG);
            g.drawString(s, x + 1, y - 1);
            final BufferedImage icon = this.lib.getScaledGoodsTypeImage(ag.getType());
            if (icon != null) drawFitted(g, icon, x + tw - 10, y - 8, 9);
            x += tw + 1;
        }
    }

    /** A small caption plate, used for the building names. */
    private void paintName(Graphics2D g, String name, int x, int y, int w) {
        if (name == null || name.isEmpty()) return;
        g.setFont(font(6f, Font.PLAIN));
        final int tw = g.getFontMetrics().stringWidth(name);
        final int tx = x + (w - tw) / 2;
        g.setColor(TAG_BG);
        g.fillRect(tx - 1, y - 6, tw + 2, 7);
        g.setColor(TAG_FG);
        g.drawString(name, tx, y);
    }

    /** A tiny black-plated number, as used under the net-production icons. */
    private void tag(Graphics2D g, String s, int x, int y) {
        g.setFont(font(6f, Font.BOLD));
        g.setColor(TAG_BG);
        g.fillRect(x, y - 5, g.getFontMetrics().stringWidth(s) + 2, 6);
        g.setColor(TAG_FG);
        g.drawString(s, x + 1, y);
    }

    /**
     * Draw {@code img} scaled to fit a {@code size}-px box at {@code (x, y)},
     * preserving aspect.  The original sprites are small (~16px), so this is
     * usually an up-scale; the pack-absent FreeCol art is large and shrinks.
     */
    private void drawFitted(Graphics2D g, BufferedImage img, int x, int y, int size) {
        final double s = Math.min((double) size / img.getWidth(),
                                  (double) size / img.getHeight());
        final int w = Math.max(1, (int) Math.round(img.getWidth() * s));
        final int h = Math.max(1, (int) Math.round(img.getHeight() * s));
        g.drawImage(img, x + (size - w) / 2, y + (size - h) / 2, w, h, null);
    }

    /** Tile {@code texture} over a rectangle, falling back to a flat colour. */
    private void fillTiled(Graphics2D g, BufferedImage texture, Color fallback,
                           int x, int y, int w, int h) {
        if (texture == null) {
            g.setColor(fallback);
            g.fillRect(x, y, w, h);
            return;
        }
        final Graphics2D gg = (Graphics2D) g.create();
        gg.clipRect(x, y, w, h);
        for (int ty = y; ty < y + h; ty += texture.getHeight()) {
            for (int tx = x; tx < x + w; tx += texture.getWidth()) {
                gg.drawImage(texture, tx, ty, null);
            }
        }
        gg.dispose();
    }

    /** The last segment of a FreeCol id ({@code model.building.townHall} → {@code townHall}). */
    private static String shortId(String id) {
        final int i = id.lastIndexOf('.');
        return (i < 0) ? id : id.substring(i + 1);
    }

    /** Render a StringTemplate, guarding against nulls / missing keys. */
    private static String msg(net.sf.freecol.common.model.StringTemplate t) {
        try {
            return (t == null) ? "" : Messages.message(t);
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** A font in <em>virtual</em> pixels — the paint transform scales it up. */
    private Font font(float size, int style) {
        return getFont().deriveFont(style, size);
    }
}
