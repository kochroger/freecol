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
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.IntConsumer;

import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.AbstractGoods;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.ColonyTile;
import net.sf.freecol.common.model.Direction;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.ProductionInfo;
import net.sf.freecol.common.model.ResourceType;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.Unit;

import static net.sf.freecol.client.gui.classic.ClassicColonyBuildings.Figure;


/**
 * The colony screen's <b>3x3 colony map</b> in the original's look (build
 * spec M4.4, {@code playthrough-3-analysis/11-spec-M4-colony.md}; L §4, dago
 * {@code 01-layout.md} §3, clip 020): the nine tiles in their 24-px cells,
 * each with our map's art of the tile enlarged to 24 px as the original
 * enlarges its 16-px map (part Y5: its geometry and its fixed shade
 * pattern, {@link #enlarge}; L §4.2's open point), the resource markers
 * unscaled over it, the colony's sprite on the centre, the
 * production rows (the centre's food at y + 1 and its other good at y + 14),
 * the workers at tile + (14,6) with their black shadows, the red
 * prohibition sign over the good of a worker who makes nothing, the totem
 * on native land, an enemy at war on his tile, the red frame of a tile
 * another colony works (fixer A after part X), the selected colonist's
 * green frame, the blinking white cursor, the tile's hover label.
 *
 * <p>A pure model ({@link #model}) of what is drawn, then one painter
 * ({@link #paint}), as {@link ClassicColonyBuildings}; every number comes
 * from {@link ClassicColonyLayout}.  Draw order (V: the frames over the
 * rows, #2260; the worker after his row, dago; the centre's second row
 * over the tile below, #2260 y 80): the nine tiles' art, then per tile in
 * their order the totem, the rows with their count labels, the sign, the
 * worker, the enemy; then the red frames, the green frame and over it the
 * white cursor.
 */
final class ClassicColonyTiles {

    private ClassicColonyTiles() {}   // static helpers only


    // The model

    /** A production row on a tile: its icon's ICONS.SS frame and its geometry. */
    static final class TileRow {

        final int icon;
        final ClassicColonyLayout.Row row;

        TileRow(int icon, ClassicColonyLayout.Row row) {
            this.icon = icon;
            this.row = row;
        }

        @Override
        public String toString() {
            return String.format("%03dx%d", this.icon, this.row.n);
        }
    }

    /** One tile of the 3x3 map as drawn. */
    static final class Cell {

        /** Its place, 0-8 (3 * row + column; the centre 4). */
        final int index;

        /** The tile and its work place (null in a model built by hand). */
        final Tile tile;
        final ColonyTile colonyTile;

        /** The production rows (the centre's two; a worker's one). */
        final List<TileRow> rows;

        /**
         * The good a worker makes nothing of (its icon under the
         * prohibition sign), its ICONS.SS frame, or -1.
         */
        final int zero;

        /** The worker's figure, or null. */
        final Figure worker;

        /** Whether the totem stands on it (native land). */
        final boolean totem;

        /** An enemy at war standing on it, or null. */
        final Unit occupier;

        /** The hover label. */
        final String label;

        /**
         * Whether another colony works it (its owning settlement): the
         * original frames it red (fixer A after part X, the colony review's
         * later item: V clip 048 #86873, Guadelupe's SW, S and SE tiles
         * next to the French Montreal; I the cause).
         */
        final boolean taken;

        Cell(int index, Tile tile, ColonyTile colonyTile, List<TileRow> rows, int zero,
             Figure worker, boolean totem, Unit occupier, String label) {
            this(index, tile, colonyTile, rows, zero, worker, totem, occupier, label, false);
        }

        Cell(int index, Tile tile, ColonyTile colonyTile, List<TileRow> rows, int zero,
             Figure worker, boolean totem, Unit occupier, String label, boolean taken) {
            this.taken = taken;
            this.index = index;
            this.tile = tile;
            this.colonyTile = colonyTile;
            this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
            this.zero = zero;
            this.worker = worker;
            this.totem = totem;
            this.occupier = occupier;
            this.label = label;
        }

        /** @return Its cell's rectangle. */
        Rectangle bounds() {
            return ClassicColonyLayout.tileCell(this.index);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder(ClassicColonyLayout.TILE_NAMES[this.index])
                .append('=');
            final int len = sb.length();
            for (TileRow r : this.rows) {
                if (sb.length() > len) sb.append('+');
                sb.append(r);
            }
            if (this.zero >= 0) sb.append(String.format("%03dx0", this.zero));
            if (sb.length() == len) sb.append('-');
            if (this.worker != null) sb.append(" fig ").append(this.worker);
            if (this.totem) sb.append(" totem");
            if (this.occupier != null) sb.append(" enemy");
            if (this.taken) sb.append(" taken");
            return sb.toString();
        }
    }

    /** The 3x3 map of a colony as drawn: its cells by place (null where the map ends). */
    static final class Model {

        final List<Cell> cells;

        Model(Cell[] cells) {
            final List<Cell> l = new ArrayList<>();
            for (int i = 0; i < ClassicColonyLayout.TILE_COUNT; i++) {
                l.add((cells != null && i < cells.length) ? cells[i] : null);
            }
            this.cells = Collections.unmodifiableList(l);
        }

        /**
         * @param i A place, 0-8.
         * @return Its cell, or null.
         */
        Cell cell(int i) {
            return (i < 0 || i >= this.cells.size()) ? null : this.cells.get(i);
        }

        /**
         * The place of the tile a unit works, or -1.
         *
         * @param u The unit, or null.
         * @return The place.
         */
        int placeOf(Unit u) {
            if (u == null) return -1;
            for (Cell c : this.cells) {
                if (c != null && c.worker != null && c.worker.unit == u) return c.index;
            }
            return -1;
        }

        /**
         * The place of a work tile (the white cursor after a move onto it,
         * M4.8), or -1.
         *
         * @param ct The work tile, or null.
         * @return The place.
         */
        int placeOfTile(ColonyTile ct) {
            if (ct == null) return -1;
            for (Cell c : this.cells) {
                if (c != null && c.colonyTile == ct) return c.index;
            }
            return -1;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            for (Cell c : this.cells) {
                if (c == null) continue;
                if (sb.length() > 0) sb.append(' ');
                sb.append(c);
            }
            return sb.toString();
        }
    }

    /**
     * The place of a work tile in the 3x3 map: by its compass direction
     * from the colony, not by its raw (x, y) offset (on FreeCol's
     * isometric map the eight neighbours' offsets do not fill a square).
     *
     * @param d The direction, or null.
     * @return The place, or -1.
     */
    static int placeOf(Direction d) {
        if (d == null) return -1;
        switch (d) {
        case NW: return 0;
        case N:  return 1;
        case NE: return 2;
        case W:  return 3;
        case E:  return 5;
        case SW: return 6;
        case S:  return 7;
        case SE: return 8;
        default: return -1;
        }
    }

    /**
     * The 3x3 map of a colony now.
     *
     * @param colony The colony.
     * @param text The pack's texts, or null (FreeCol's names for the labels).
     * @param a The sprites (the icons and the figures).
     * @return The model.
     */
    static Model model(Colony colony, ClassicText text, ClassicColonyBuildings.Assets a) {
        final Tile centre = colony.getTile();
        final Cell[] cells = new Cell[ClassicColonyLayout.TILE_COUNT];
        if (centre == null) return new Model(cells);
        for (ColonyTile ct : colony.getColonyTiles()) {
            final Tile t = ct.getWorkTile();
            if (t == null) continue;
            final int i = (t == centre) ? ClassicColonyLayout.CENTRE_TILE
                : placeOf(centre.getDirection(t));
            if (i < 0 || cells[i] != null) continue;
            cells[i] = cell(colony, ct, t, i, text, a);
        }
        return new Model(cells);
    }

    /** One tile's cell. */
    private static Cell cell(Colony colony, ColonyTile ct, Tile t, int i, ClassicText text,
                             ClassicColonyBuildings.Assets a) {
        final Rectangle r = ClassicColonyLayout.tileCell(i);
        final List<AbstractGoods> made = made(ct);
        final List<TileRow> rows = new ArrayList<>();
        int zero = -1;
        Figure worker = null;
        if (i == ClassicColonyLayout.CENTRE_TILE) {
            // The centre works itself: its food at y + 1, its other good
            // at y + 14 (V); FreeCol's food first, then its first other.
            AbstractGoods food = null, other = null;
            for (AbstractGoods ag : made) {
                if (ag.getType().isFoodType()) {
                    if (food == null) food = ag;
                } else if (other == null) {
                    other = ag;
                }
            }
            final List<AbstractGoods> shown = new ArrayList<>();
            if (food != null) shown.add(food);
            if (other != null) shown.add(other);
            for (int k = 0; k < shown.size(); k++) {
                final TileRow tr = row(a, shown.get(k), r.x, r.y + ((k == 0)
                    ? ClassicColonyLayout.TILE_ROW_DY : ClassicColonyLayout.TILE_SECOND_ROW_DY));
                if (tr != null) rows.add(tr);
            }
        } else {
            final Unit u = ct.getUnitList().isEmpty() ? null : ct.getUnitList().get(0);
            if (u != null) {
                // His job's good: what the tile makes of it.  None of it:
                // the good's icon under the prohibition sign, no number
                // (V clip 020 #48712, #50256, #50260; clip 046 #98141 the
                // corn of a farmer on a mountain, no ore), also where
                // FreeCol's rules give him the square's best other good
                // instead (the classic and freecol rules; the levi rules
                // give him nothing there, part Y3, ColonyTile's
                // getBestProductionType): the original's picture, never
                // that other good (Roger's test 2026-10-16: 4 corn shown
                // for an expert farmer made Holzfäller on grassland).  A
                // colonist with no job: what the square makes.
                final GoodsType job = u.getWorkType();
                int n = 0;
                for (AbstractGoods ag : made) if (ag.getType() == job) n += ag.getAmount();
                final AbstractGoods shown = (job == null)
                    ? ((made.isEmpty()) ? null : made.get(0))
                    : (n > 0) ? new AbstractGoods(job, n) : null;
                final TileRow tr = (shown == null) ? null
                    : row(a, shown, r.x, r.y + ClassicColonyLayout.TILE_ROW_DY);
                if (tr != null) {
                    rows.add(tr);
                } else if (shown == null && job != null) {
                    zero = ClassicColonyLayout.goodsIcon(job.getId());
                }
                final String k = ClassicColonyBuildings.figureKey(a, u, false);
                final BufferedImage s = a.image(k);
                worker = new Figure(u, k, ClassicColonyBuildings.iconFrame(a, k),
                                    r.x + ClassicColonyLayout.TILE_WORKER_DX,
                                    r.y + ClassicColonyLayout.TILE_WORKER_DY,
                                    (s == null) ? 8 : s.getWidth());
            }
        }
        final boolean totem = i != ClassicColonyLayout.CENTRE_TILE && nativeLand(colony, t);
        return new Cell(i, t, ct, rows, zero, worker, totem, occupier(t, colony.getOwner()),
                        label(text, t),
                        i != ClassicColonyLayout.CENTRE_TILE && takenByAnother(colony, t));
    }

    /**
     * Whether a tile belongs to another colony (ours or a foreign one: its
     * owning settlement), which frames it red (V clip 048 #86873; I: a
     * native settlement's land shows the totem instead).
     *
     * @param colony The colony.
     * @param t The tile, or null.
     * @return True if so.
     */
    static boolean takenByAnother(Colony colony, Tile t) {
        final net.sf.freecol.common.model.Settlement s = (t == null) ? null
            : t.getOwningSettlement();
        return s instanceof Colony && s != colony;
    }

    /** The goods a work place makes now, those of an amount above 0. */
    private static List<AbstractGoods> made(ColonyTile ct) {
        final List<AbstractGoods> out = new ArrayList<>();
        final ProductionInfo pi = ct.getProductionInfo();
        if (pi == null) return out;
        for (AbstractGoods ag : pi.getProduction()) {
            if (ag.getType() != null && ag.getAmount() > 0) out.add(ag);
        }
        return out;
    }

    /** A row of a good at a y ({@link ClassicColonyLayout#tileRow}), or null without an icon. */
    private static TileRow row(ClassicColonyBuildings.Assets a, AbstractGoods ag, int tileX,
                               int y) {
        final int icon = ClassicColonyLayout.goodsIcon(ag.getType().getId());
        if (icon < 0) return null;
        final BufferedImage img = a.icon(icon);
        final ClassicColonyLayout.Row row = ClassicColonyLayout.tileRow(tileX, y,
            ag.getAmount(), (img == null) ? 8 : img.getWidth());
        return (row == null) ? null : new TileRow(icon, row);
    }

    /**
     * Whether a tile shows the totem: land of a native nation that the
     * colony's owner would have to buy or take (V Prov. #14817: four of
     * its tiles; none in Silver City 1566 after Peter Minuit, whose land
     * costs nothing: I, as clip008).
     *
     * @param colony The colony.
     * @param t The tile.
     * @return True for the totem.
     */
    static boolean nativeLand(Colony colony, Tile t) {
        final Player owner = (t == null) ? null : t.getOwner();
        final Player us = colony.getOwner();
        if (owner == null || us == null || owner == us || !owner.isIndian()) return false;
        try {
            return us.getLandPrice(t) > 0;
        } catch (RuntimeException e) {
            return false;   // a client without the game's turn (tests)
        }
    }

    /**
     * The enemy unit occupying a work tile of the colony at war, which no
     * colonist can then work (war-french #13962: a French dragoon on
     * Cotton Town's west tile, a French soldier on its south-east one,
     * fortified or not): an offensive unit of a player at war with the
     * colony's owner.  None at peace (I).
     *
     * @param t The work tile.
     * @param owner The colony's owner.
     * @return The unit, or null.
     */
    static Unit occupier(Tile t, Player owner) {
        if (t == null || owner == null) return null;
        for (Unit u : t.getUnitList()) {
            final Player p = u.getOwner();
            if (p != null && p != owner && u.isOffensiveUnit()
                && owner.atWarWith(p)) return u;
        }
        return null;
    }

    /**
     * A tile's hover label (dago §3.5, clip 020 ×40): its terrain as the
     * map's panel names it and its extras, joined by '/' («Grünland»,
     * «Tropen- Wald», «Hügellandschaft/Straße», «Sumpfland/Straße/Gepflügt»;
     * the river and the resource in the panel's order, I).
     *
     * @param text The texts, or null (FreeCol's name).
     * @param t The tile.
     * @return The label, or "" for an unknown tile.
     */
    static String label(ClassicText text, Tile t) {
        final TileType type = (t == null) ? null : t.getType();
        if (type == null) return "";
        final String name = ClassicHud.terrainName(text, type.getId());
        if (name == null) return Messages.getName(type);
        final StringBuilder sb = new StringBuilder(name.trim());
        for (String w : ClassicHud.tileExtras(text, ClassicHud.riverOf(t),
                ClassicUnitCycle.roadShown(t), ClassicHud.plowed(t),
                ClassicHud.resourceRowOf(t))) {
            if (w != null && !w.trim().isEmpty()) sb.append('/').append(w.trim());
        }
        return sb.toString();
    }


    // The art: the original's 24-px tiles (part Y5, L §4.2 closed)

    /**
     * The terrain of the 3x3 map as the original draws it (part Y5, Roger's
     * colony test 2026-10-16 15:21; the prep's
     * {@code freecol-spike-results/y/prep/3x3-scaler.md}): each tile as our
     * map composes it in palette indices (the pack's TERRAIN and PHYS0
     * index sheets, {@link ClassicTerrainComposer}) with the colony's
     * sprite on the centre where the map draws it, its overhang in the W
     * and E cells (the review of part Y), but without its
     * resource marker and rumour; the 16 px enlarged to 24 as the original
     * ({@link #enlarge}: its geometry and its fixed shade pattern); the
     * markers over that unscaled at tile + (4,4) with no shades (V the
     * fish on Provisorische Kolonie's NE, clip 020 #14817, 70 of 70 px;
     * the other markers and the rumour I, alike); then the game palette at
     * the water's phase.  The original's palette cycles the water of its
     * 3x3 map with the map's (V clip 020: 144 px every 40-41 frames,
     * #2262, #2303, #2343): ours too, a palette step repaints the cells
     * that hold a cycling index.  Without the index sheets: the map's RGBA
     * tiles in the original's geometry ({@link #scale}) with no shades
     * (ours: there is no palette to shade in).
     */
    static final class Art {

        private final ClassicTerrainComposer composer;
        private final ClassicGamePalette palette;

        /** The fallback's art (no index sheets), or null. */
        private final ImageLibrary lib;
        private ClassicTileArt tileArt = null;

        /** RGB to the lowest palette index of that colour (the sprites' colours), built once. */
        private Map<Integer, Integer> indexOf = null;

        private Art(ClassicTerrainComposer composer, ClassicGamePalette palette,
                    ImageLibrary lib) {
            this.composer = composer;
            this.palette = palette;
            this.lib = lib;
        }

        /** The composers of the packs seen (they are the map's rules; built once). */
        private static final Map<ClassicPackFiles, Object[]> RULES = new WeakHashMap<>();

        /**
         * The art of a pack.
         *
         * @param pack The pack, or null.
         * @param lib The images for the fallback (FreeCol's tiles), or null.
         * @return The art.
         */
        static Art load(ClassicPackFiles pack, ImageLibrary lib) {
            ClassicTerrainComposer c = null;
            ClassicGamePalette p = null;
            if (pack != null) {
                synchronized (RULES) {
                    Object[] r = RULES.get(pack);
                    if (r == null) {
                        final ClassicTerrainLayer layer = ClassicTerrainLayer.create(pack);
                        r = (layer == null) ? new Object[2]
                            : new Object[] { layer.composer(), layer.palette() };
                        RULES.put(pack, r);
                    }
                    c = (ClassicTerrainComposer) r[0];
                    p = (ClassicGamePalette) r[1];
                }
            }
            return new Art(c, p, lib);
        }

        /** @return Whether the tiles are composed from the pack's indices. */
        boolean indexed() {
            return this.composer != null && this.palette != null;
        }

        /**
         * A tile's 24x24 picture now, the sprite given standing on it.
         *
         * @param map The map.
         * @param t The tile.
         * @param slot Its cell in the 3x3 map, 0-8 ({@link
         *     ClassicColonyLayout#tileCell}; the shade pattern is anchored
         *     on the whole 3x3).
         * @param settlement The sprite on it (the colony's on the centre,
         *     as the map draws it), or null.
         * @param phase The water's cycling phase.
         * @param cycles Set to true when the tile holds a cycling index.
         * @return The picture, or null without any art.
         */
        BufferedImage tile(net.sf.freecol.common.model.Map map, Tile t, int slot,
                           BufferedImage settlement, int phase, boolean[] cycles) {
            return tile(map, t, slot, settlement, 0, phase, cycles);
        }

        /**
         * A tile's 24x24 picture now.  A sprite wider than the 16-px cell
         * overhangs into the tiles beside its own as on the map (the
         * colony's 21-px huts at cell x - 2, {@link
         * ClassicHud#settlementOffset}): its columns past the cell's edges
         * are pasted into the W and E neighbours' cells before the
         * enlargement, as into its own (the review of part Y, fidelity: V
         * clip 020 #1700, Base's E x 112-113, y 121-127 hold ICONS.SS.003's
         * columns 18-19, rows 1-7, and the 3x3's E #2260 shows them
         * enlarged and shaded at its x 0-2; before, ours cut the huts' roof
         * off at the centre's edge; the stockade's, fort's and fortress's
         * columns 0 and 20 into W and E alike, I).  The neighbours keep
         * their markers apart, drawn over the cell unscaled (I where a
         * marker meets the overhang: never seen).
         *
         * @param map The map.
         * @param t The tile.
         * @param slot Its cell in the 3x3 map, 0-8.
         * @param settlement The sprite on this tile or beside it, or null.
         * @param spriteDx Where the sprite's own cell lies from this one:
         *     0 on it, 16 when this cell is its W neighbour, -16 its E.
         * @param phase The water's cycling phase.
         * @param cycles Set to true when the tile holds a cycling index.
         * @return The picture, or null without any art.
         */
        BufferedImage tile(net.sf.freecol.common.model.Map map, Tile t, int slot,
                           BufferedImage settlement, int spriteDx, int phase,
                           boolean[] cycles) {
            if (indexed()) {
                return indexedTile(map, t, slot, settlement, spriteDx, phase, cycles);
            }
            final int cell = ClassicTerrainComposer.CELL;
            final int[] rgb = new int[cell * cell];
            if (this.lib != null && t.getType() != null) {
                final BufferedImage b = new BufferedImage(cell, cell, BufferedImage.TYPE_INT_ARGB);
                final Graphics2D g = b.createGraphics();
                try {
                    final BufferedImage terrain = this.lib.getTerrainImage(t.getType(),
                        t.getX(), t.getY(), new Dimension(cell, cell));
                    if (terrain != null) g.drawImage(terrain, 0, 0, cell, cell, null);
                    if (this.tileArt == null) this.tileArt = new ClassicTileArt(this.lib);
                    this.tileArt.paintOverlays(g, map, t, 0, 0, cell, cell);
                } finally {
                    g.dispose();
                }
                b.getRGB(0, 0, cell, cell, rgb, 0, cell);
            } else {
                return null;
            }
            if (settlement != null) {
                // Where the map draws it: centred, a wider sprite
                // overhanging both sides (ClassicHud.settlementOffset),
                // into a neighbour's cell from its own.
                final int ox = ClassicHud.settlementOffset(settlement.getWidth()) + spriteDx;
                for (int y = 0; y < Math.min(cell, settlement.getHeight()); y++) {
                    for (int x = 0; x < settlement.getWidth(); x++) {
                        final int p = settlement.getRGB(x, y);
                        final int cx = x + ox;
                        if ((p >>> 24) > 128 && cx >= 0 && cx < cell) rgb[y * cell + cx] = p | 0xFF000000;
                    }
                }
            }
            return scale(rgb, cell, ClassicColonyLayout.TILE);
        }

        /**
         * A tile's picture from the pack's indices: composed, the sprite
         * pasted in, enlarged with the original's shades ({@link
         * #enlarge}), the markers over it, the palette at the phase.
         */
        private BufferedImage indexedTile(net.sf.freecol.common.model.Map map, Tile t,
                                          int slot, BufferedImage settlement, int spriteDx,
                                          int phase, boolean[] cycles) {
            final int cell = ClassicTerrainComposer.CELL, size = ClassicColonyLayout.TILE;
            final byte[] idx = new byte[cell * cell];
            // The markers apart, but on the colony's own tile, where the
            // map draws its sprite over them (I: never seen, they stay in
            // the cell as the map composes it, mostly under the sprite).
            final List<Integer> markers = new ArrayList<>();
            final boolean own = settlement != null && spriteDx == 0;
            this.composer.composeCell((own) ? source(map) : source(map, markers::add),
                                      t.getX(), t.getY(), idx, 0, cell);
            // The sprite as indices (V: the colony's sprite and its flag are
            // enlarged and shaded with the terrain, Silver City's centre
            // #123003 row 0, 24 of 24 px; the four nations' flag fills are
            // EGA colours, unshaded); a colour the palette lacks (Spain's
            // unmeasured dark shade, ClassicHud.nationDark) keeps its
            // colour, unshaded.
            int[] fixed = null;
            if (settlement != null) {
                final int ox = ClassicHud.settlementOffset(settlement.getWidth()) + spriteDx;
                for (int y = 0; y < Math.min(cell, settlement.getHeight()); y++) {
                    for (int x = 0; x < settlement.getWidth(); x++) {
                        final int p = settlement.getRGB(x, y);
                        final int cx = x + ox;
                        if ((p >>> 24) <= 128 || cx < 0 || cx >= cell) continue;
                        final Integer i = indexOf(p & 0xFFFFFF);
                        if (i != null) {
                            idx[y * cell + cx] = (byte) i.intValue();
                        } else {
                            if (fixed == null) fixed = new int[cell * cell];
                            fixed[y * cell + cx] = p | 0xFF000000;
                        }
                    }
                }
            }
            final byte[] out = enlarge(idx, slot);
            final byte[] marks = new byte[size * size];
            java.util.Arrays.fill(marks, (byte) ClassicIndexSheet.TRANSPARENT);
            for (int f : markers) {
                this.composer.overlayAt(f, marks, ClassicColonyLayout.TILE_MARKER_DY * size
                                        + ClassicColonyLayout.TILE_MARKER_DX, size);
            }
            final IndexColorModel cm = this.palette.colorModel(phase);
            final BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            boolean c = false;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    final int k = y * size + x;
                    final int m = marks[k] & 0xFF;
                    final int src = enlargedSourceY(y) * cell + enlargedSourceX(x);
                    final int v;
                    if (m != ClassicIndexSheet.TRANSPARENT) {
                        v = m;
                    } else if (fixed != null && fixed[src] != 0) {
                        img.setRGB(x, y, fixed[src]);
                        continue;
                    } else {
                        v = out[k] & 0xFF;
                    }
                    c |= this.palette.cycles(v);
                    img.setRGB(x, y, cm.getRGB(v) | 0xFF000000);
                }
            }
            if (cycles != null && c) cycles[0] = true;
            return img;
        }

        /**
         * The lowest palette index of a colour (the base palette; the
         * lowest is never a cycling one: 0x4D65AE is 56 and 120, 0x283892
         * 59 and 127).
         *
         * @param rgb The colour, 0xRRGGBB.
         * @return The index, or null for a colour the palette lacks.
         */
        private synchronized Integer indexOf(int rgb) {
            if (this.indexOf == null) {
                final int[] base = this.palette.base();
                final Map<Integer, Integer> m = new java.util.HashMap<>();
                for (int i = base.length - 1; i >= 0; i--) m.put(base[i] & 0xFFFFFF, i);
                this.indexOf = m;
            }
            return this.indexOf.get(rgb);
        }

        /** The game palette (tests), or null. */
        ClassicGamePalette palette() {
            return this.palette;
        }

        /**
         * The Colonopedia terrain page's picture (part W): three by three
         * cells of a terrain type composed as the map composes them, with
         * {@link ClassicTileArt#pediaFrames}' overlays.
         *
         * @param type The terrain type.
         * @param resource The page's resource type, or null.
         * @param phase The water's cycling phase.
         * @return The 48x48 picture, or null without the pack's index
         *     sheets.
         */
        BufferedImage pediaPicture(final TileType type, final ResourceType resource,
                                   int phase) {
            if (!indexed() || type == null) return null;
            final int cell = ClassicTerrainComposer.CELL;
            final int n = ClassicTileArt.PEDIA_CELLS, side = n * cell;
            final ClassicTerrainComposer.TerrainSource block
                = new ClassicTerrainComposer.TerrainSource() {
                    @Override
                    public boolean onMap(int x, int y) {
                        return x >= 0 && y >= 0 && x < n && y < n;
                    }

                    @Override
                    public boolean explored(int x, int y) {
                        return onMap(x, y);
                    }

                    @Override
                    public TileType type(int x, int y) {
                        return onMap(x, y) ? type : null;
                    }

                    @Override
                    public void overlays(int x, int y, IntConsumer frames) {
                        ClassicTileArt.pediaFrames(type, resource, x, y, frames);
                    }
                };
            final byte[] idx = new byte[side * side];
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    this.composer.composeCell(block, x, y, idx, y * cell * side + x * cell, side);
                }
            }
            final IndexColorModel cm = this.palette.colorModel(phase);
            final BufferedImage out = new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB);
            for (int k = 0; k < idx.length; k++) {
                out.setRGB(k % side, k / side, cm.getRGB(idx[k] & 0xFF));
            }
            return out;
        }
    }

    /**
     * A square picture scaled up in the original's geometry (the fallback
     * without the pack's indices, no shades): the source pixel (floor(x s
     * / S), floor((2y + 1) s / (2 S))), for S = 24, s = 16 (floor(2x/3),
     * floor((2y + 1)/3)), as {@link #enlargedSourceX} and {@link
     * #enlargedSourceY}.
     *
     * @param rgb The source, s x s ARGB.
     * @param s The source size.
     * @param size The target size.
     * @return The picture.
     */
    static BufferedImage scale(int[] rgb, int s, int size) {
        final BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) {
            final int sy = Math.min(s - 1, ((2 * y + 1) * s) / (2 * size));
            for (int x = 0; x < size; x++) {
                final int sx = Math.min(s - 1, (x * s) / size);
                out.setRGB(x, y, rgb[sy * s + sx]);
            }
        }
        return out;
    }


    // The original's enlargement of its 16-px tiles (part Y5)

    /**
     * The shade pattern on the eight-shade and blue ramps, 192 steps
     * (V all 192 from the colony frames against the map frames, prep y
     * §3.2; the antiperiod {@code NOISE[q + 96] == -NOISE[q]} holds for
     * every q): a 12-px motif [0 -1 0][+1 +1 -1][0 +1 0][-1 -1 +1] with a
     * slip about every 15 px; the middle one of each three (q mod 3 = 1)
     * mostly +-1 (54 of 64) and the only +-3 (6), the others 0, +-1 or
     * +-2.
     */
    static final int[] NOISE = {
         0, -1,  0,  1,  1, -1,  0,  1,  0, -1, -1,  1, -1, -3,  1,  2,  1,  0,  1,  1, -1,  0,  1,  0,
        -1, -1,  1,  0, -2,  1,  2,  1,  0,  1,  1, -1,  0,  1,  0, -1, -1,  1,  0, -1,  1,  2,  1, -2,
         1,  1, -1,  0,  1,  0, -1, -1,  1,  0, -1,  0,  2,  1, -2,  1,  1, -1,  0,  1,  0, -1, -1,  1,
         0, -1,  0,  1,  0, -2,  1,  3, -1,  0,  1,  0, -1, -1,  1,  0, -1,  0,  1,  1, -2,  1,  3, -1,
         0,  1,  0, -1, -1,  1,  0, -1,  0,  1,  1, -1,  1,  3, -1, -2, -1,  0, -1, -1,  1,  0, -1,  0,
         1,  1, -1,  0,  2, -1, -2, -1,  0, -1, -1,  1,  0, -1,  0,  1,  1, -1,  0,  1, -1, -2, -1,  2,
        -1, -1,  1,  0, -1,  0,  1,  1, -1,  0,  1,  0, -2, -1,  2, -1, -1,  1,  0, -1,  0,  1,  1, -1,
         0,  1,  0, -1,  0,  2, -1, -3,  1,  0, -1,  0,  1,  1, -1,  0,  1,  0, -1, -1,  2, -1, -3,  1 };

    /**
     * The shade pattern on the grey ramp 0x10-0x2F, on the same 192 steps
     * (not 4 x {@link #NOISE}: q 4 is 3 against 1, q 7 is 8 against 1).  V
     * from the frames (prep y §3.3), with six middle steps measured anew
     * under {@link #shaded}'s chain, where the prep's rule had misread
     * them: q 37 +8 (was -4), 133 -8 (4), 139 +10 (-4), 169 +10 (-4), 190
     * -12 (4); q 94 +12 (was -4) by the antiperiod, the frames alike for
     * both (I); so the antiperiod {@code GREY_NOISE[q + 96] ==
     * -GREY_NOISE[q]} now holds for every q, the prep's four uncertain
     * steps (43, 73, 139, 169) included.  Ten steps the prep never saw
     * directly are the antiperiod's (I, prep y §3.3).
     */
    static final int[] GREY_NOISE = {
          0, -5,  0,  5,  3, -6,  2,  8, -3, -7, -4,  7, -4,-12,  4,  8,  3,  0,  5,  5, -6,  2,  8, -2,
         -7, -4,  7, -3,-11,  4,  8,  4,  0,  5,  5, -5,  2,  8, -2, -6, -3,  7, -3,-10,  4,  8,  4, -8,
          5,  5, -5,  0,  6, -2, -6, -4,  7, -3,-10,  3,  8,  4, -8,  4,  4, -5,  0,  5, -2, -6, -4,  6,
         -3,-10,  3,  7,  3, -8,  4, 12, -5,  0,  5,  0, -6, -4,  6, -2, -9,  3,  7,  4, -8,  4, 12, -4,
          0,  5,  0, -5, -3,  6, -2, -8,  3,  7,  4, -7,  4, 12, -4, -8, -3,  0, -5, -5,  6, -2, -8,  2,
          7,  4, -7,  3, 11, -4, -8, -4,  0, -5, -5,  5, -2, -8,  2,  6,  3, -7,  3, 10, -4, -8, -4,  8,
         -5, -5,  5,  0, -6,  2,  6,  4, -7,  3, 10, -3, -8, -4,  8, -4, -4,  5,  0, -5,  2,  6,  4, -6,
          3, 10, -3, -7, -3,  8, -4,-12,  5,  0, -5,  0,  6,  4, -6,  2,  9, -3, -7, -4,  8, -4,-12,  4 };

    /** The patterns' period. */
    static final int NOISE_PERIOD = 192;

    /**
     * The original's enlargement of a tile's 16-px cell to its 24-px cell
     * of the 3x3 map (part Y5; V the colony frames against map frames of
     * the same colonies: clip 020 Base #2260 / #1700, Silver City #123003
     * / #122700 and #123300, Provisorische Kolonie #14817 / #14300 and
     * #15500, the prep's {@code 3x3-scaler.md}; the same pattern in the
     * dago game, clip 010 #448 / #400): target pixel (x, y) shows the
     * source pixel ({@link #enlargedSourceX}, {@link #enlargedSourceY})
     * shaded in its colour ramp by a fixed pattern anchored on the whole
     * 72x72 map ({@link #shaded}).  The original has no 24-px terrain art
     * (every sheet holds 16-px tiles, its map zooms only down) and no
     * smoothing (bilinear, area, bicubic: 17-36 %); a plain enlargement in
     * any phase reaches 33 % at best, the prep's rule 97-100 % per clean
     * tile, this one with its chain 6906 of 6912 px (99.9 %) of the twelve
     * clean tiles of those frames, nine of them all 576 (ocean, coast,
     * forest, hills, mountains); the six others are one source pixel at
     * an outer tile's corner each (Base E, Prov. Kolonie SE (20,0); Silver
     * City NW (3-4,22-23)) where the map frame shows land and the 3x3 water:
     * a corner piece of a coast tile next to tiles beyond the 3x3 (I, not
     * understood: the original may compose the 3x3's coast corners from
     * its own nine tiles only; ours composes the true neighbours, as the
     * map).
     *
     * @param src The cell, 16 x 16 palette indices.
     * @param slot Its cell of the 3x3, 0-8 (column slot mod 3, row slot / 3).
     * @return The 24 x 24 indices.
     */
    static byte[] enlarge(byte[] src, int slot) {
        final int s = ClassicTerrainComposer.CELL, n = ClassicColonyLayout.TILE;
        final int ox = n * (slot % 3), oy = n * (slot / 3);
        final byte[] out = new byte[n * n];
        for (int y = 0; y < n; y++) {
            final int row = enlargedSourceY(y) * s;
            for (int x = 0; x < n; x++) {
                out[y * n + x] = (byte) shaded(src[row + enlargedSourceX(x)] & 0xFF,
                                               ox + x, oy + y);
            }
        }
        return out;
    }

    /**
     * The source column of a target column: floor(2x/3), so the columns
     * go two, one, two ... per source pixel (V the hill PHYS0.SS.048 on
     * Base's NE, #2260, pixel for pixel with its hole at (14,13) as (14,19);
     * ours before: floor((2x + 1)/3), a column off in two of three).
     *
     * @param x The target column, 0-23.
     * @return The source column, 0-15.
     */
    static int enlargedSourceX(int x) {
        return (2 * x) / 3;
    }

    /**
     * The source row of a target row: floor((2y + 1)/3), one, two, one,
     * two ... per source row (V, as {@link #enlargedSourceX}).
     *
     * @param y The target row, 0-23.
     * @return The source row, 0-15.
     */
    static int enlargedSourceY(int y) {
        return (2 * y + 1) / 3;
    }

    /**
     * The shade pattern's offset of a row of the 72x72 map (V rows 0-71,
     * Silver City's top tiles and Base's bottom ones alike, prep y §3.1):
     * j = floor(y/3), b = 60 floor(j/2); y mod 3 = 0: b, 1: b + 156 (j
     * even) or b + 84 (j odd), 2: b + 132, all mod 192.  Each is a multiple
     * of 12, so the step's q mod 3 is the column's x mod 3; the pattern
     * repeats every 96 rows.
     *
     * @param y The row of the 3x3, 0-71.
     * @return Its offset, 0-191.
     */
    static int noiseRow(int y) {
        final int j = y / 3, b = 60 * (j / 2);
        switch (y % 3) {
        case 0:
            return b % NOISE_PERIOD;
        case 1:
            return (b + ((j % 2 == 0) ? 156 : 84)) % NOISE_PERIOD;
        default:
            return (b + 132) % NOISE_PERIOD;
        }
    }

    /**
     * The lowest index of the colour ramp a palette index is shaded in,
     * or -1 for one the original leaves (prep y §3.4): the grey 0x10-0x2F
     * (32 shades, {@link #GREY_NOISE}), the blue 0x30-0x3F (16), the
     * eight-shade ramps 0x40-0x6F (0x60-0x67 never seen shaded, I), the
     * water's cycling 0x78-0x7F (V 393 of 399), the browns 0x80-0x87; not
     * the EGA colours 0x00-0x0F (V the colony's orange flag 0x05/0x0D),
     * not 0x88-0x8A (V 0x88 unchanged 47 times; as one ramp 0x80-0x8A
     * Base's NE matches 97.6 % instead of 100 %), and (I) not the reds
     * 0x70-0x77 nor 0x90-0xFF, never seen.
     *
     * @param s The index.
     * @return Its ramp's lowest index, or -1.
     */
    static int rampLow(int s) {
        if (s >= 0x10 && s <= 0x2F) return 0x10;
        if (s >= 0x30 && s <= 0x3F) return 0x30;
        if ((s >= 0x40 && s <= 0x6F) || (s >= 0x78 && s <= 0x87)) return s & ~7;
        return -1;
    }

    /**
     * @param lo A ramp's lowest index ({@link #rampLow}).
     * @return Its highest.
     */
    static int rampHigh(int lo) {
        return (lo == 0x10) ? 0x2F : (lo == 0x30) ? 0x3F : lo + 7;
    }

    /**
     * A source index shaded at a pixel of the 3x3 (X 0-71, Y 0-71): its
     * step q = (X + {@link #noiseRow}(Y)) mod 192 of the pattern ({@link
     * #GREY_NOISE} on the grey, else {@link #NOISE}); s + d where that
     * stays in s's ramp, else s - d (V: s47 +1 -> 46 128 times, s46 +2 ->
     * 44, s48 -1 -> 49), else s.  The middle one of the three target
     * columns of two source pixels (X mod 3 = 1, the second that shows its
     * source pixel) is chained to the one left of it: that pixel plus the
     * pattern's step d(q) - d(q - 1), reflected about it where that leaves
     * the ramp (found by builder 2 of part Y: every pixel the prep's rule
     * missed on the clean tiles was such a middle one at a ramp's end, 32
     * of 32 with +1 after a reflected +2 giving -3 (s46 -> 43, s47 -> 44),
     * 7 of 7 with +3 after a reflected +1 giving -3, 6 of 6 with -1 after
     * a reflected -2 giving +3, the greys alike (+4 after a reflected +8:
     * -12, 9 of 9); the prep's |d| = 3 rule and its "wrap by 4" are this
     * chain; the other columns follow s +- d with no exception, 221 pixels
     * whose s + d leaves the ramp).
     *
     * @param s The source index.
     * @param x The pixel's column in the 3x3, 0-71.
     * @param y Its row, 0-71.
     * @return The shaded index.
     */
    static int shaded(int s, int x, int y) {
        final int lo = rampLow(s);
        if (lo < 0) return s;
        final int hi = rampHigh(lo);
        final int[] steps = (lo == 0x10) ? GREY_NOISE : NOISE;
        final int q = Math.floorMod(x + noiseRow(y), NOISE_PERIOD);
        if (x % 3 != 1) return reflect(s, steps[q], lo, hi);
        final int p = (q + NOISE_PERIOD - 1) % NOISE_PERIOD;
        return reflect(reflect(s, steps[p], lo, hi), steps[q] - steps[p], lo, hi);
    }

    /** s + d in [lo, hi], else s - d, else s. */
    private static int reflect(int s, int d, int lo, int hi) {
        int o = s + d;
        if (o >= lo && o <= hi) return o;
        o = s - d;
        return (o >= lo && o <= hi) ? o : s;
    }

    /** {@link #spriteDx} of a cell the colony's sprite does not reach. */
    static final int NO_SPRITE = Integer.MIN_VALUE;

    /**
     * Where the colony's sprite lies from a cell of the 3x3 map, for
     * {@link Art#tile}: 0 on the centre, 16 from W, -16 from E (its
     * overhang: a sprite is at most 22 px wide, {@link
     * ClassicHud#fitSettlement}, and 16 high, so it reaches no other cell).
     *
     * @param slot The cell, 0-8.
     * @return The offset, or {@link #NO_SPRITE}.
     */
    static int spriteDx(int slot) {
        final int c = ClassicColonyLayout.CENTRE_TILE;
        if (slot == c) return 0;
        if (slot == c - 1) return ClassicTerrainComposer.CELL;
        if (slot == c + 1) return -ClassicTerrainComposer.CELL;
        return NO_SPRITE;
    }

    /**
     * The map as the client knows it, for the composer: a tile is
     * explored when its type is known, its overlays are the map's
     * ({@link ClassicTileArt#overlayFrames}).  (The map viewer's own
     * source also knows the true terrain of the fog ring, which only
     * changes a blend toward an unexplored neighbour.)
     *
     * @param map The map.
     * @return The source.
     */
    static ClassicTerrainComposer.TerrainSource source(final net.sf.freecol.common.model.Map map) {
        return source(map, null);
    }

    /**
     * The map for the composer, a tile's resource marker and lost city
     * rumour handed apart (the 3x3 draws them unscaled over its enlarged
     * tiles, {@link ClassicColonyLayout#TILE_MARKER_DX}).
     *
     * @param map The map.
     * @param markers Receives the markers' {@code PHYS0.SS} frames
     *     instead of the composer, or null to leave them in the overlays.
     * @return The source.
     */
    static ClassicTerrainComposer.TerrainSource source(final net.sf.freecol.common.model.Map map,
                                                       final IntConsumer markers) {
        return new ClassicTerrainComposer.TerrainSource() {
            @Override
            public boolean onMap(int x, int y) {
                return map.getTile(x, y) != null;
            }

            @Override
            public boolean explored(int x, int y) {
                final Tile t = map.getTile(x, y);
                return t != null && t.isExplored();
            }

            @Override
            public TileType type(int x, int y) {
                final Tile t = map.getTile(x, y);
                return (t == null) ? null : t.getType();
            }

            @Override
            public void overlays(int x, int y, IntConsumer frames) {
                final Tile t = map.getTile(x, y);
                if (t == null || t.getType() == null) return;
                if (markers == null) {
                    ClassicTileArt.overlayFrames(map, t, ClassicTileArt.modelTypes(map), frames);
                    return;
                }
                final int resource = (t.hasResource())
                    ? ClassicTileArt.resourceFrame(t.getResource()) : -1;
                final int rumour = (t.hasLostCityRumour()) ? ClassicTileArt.LOST_CITY : -1;
                ClassicTileArt.overlayFrames(map, t, ClassicTileArt.modelTypes(map), f -> {
                    if (f >= 0 && (f == resource || f == rumour)) {
                        markers.accept(f);
                    } else {
                        frames.accept(f);
                    }
                });
            }
        };
    }


    // Painting

    /** What draws a tile's terrain (the game's art; a test's plain fill). */
    interface Ground {

        /**
         * Draw a tile's terrain into its cell (the painter clips to it).
         *
         * @param g The canvas.
         * @param c The cell.
         * @return Whether it holds a cycling index (its water).
         */
        boolean paint(Graphics2D g, Cell c);
    }

    /** What draws an enemy on his tile (the map's unit icon with its flag). */
    interface Standing {

        /**
         * @param g The canvas.
         * @param u The unit.
         * @param x Its icon's x.
         * @param y Its icon's y.
         */
        void paint(Graphics2D g, Unit u, int x, int y);
    }

    /**
     * The 3x3 map as the model says (the wood and the black frame are the
     * chrome's, {@link ClassicColonyChrome#paintWoodPanel}).
     *
     * @param g The canvas.
     * @param c The chrome's assets (the font, the inks).
     * @param a The sprites.
     * @param m The model.
     * @param ground The terrain's painter, or null for none.
     * @param standing The enemies' painter, or null for none.
     * @param green The selected colonist's tile (its green frame,
     *     {@link Model#placeOf}), or -1.
     * @param cursor The white cursor's tile, or -1 when it is elsewhere or
     *     in its blink's off half.
     * @param labels Whether the stacks carry their count labels.
     * @return Whether a drawn tile holds a cycling index.
     */
    static boolean paint(Graphics2D g, ClassicColonyChrome.Assets c,
                         ClassicColonyBuildings.Assets a, Model m, Ground ground,
                         Standing standing, int green, int cursor, boolean labels) {
        boolean cycles = false;
        for (Cell cell : m.cells) {
            if (cell == null || ground == null) continue;
            final Rectangle r = cell.bounds();
            final Graphics2D gg = (Graphics2D) g.create();
            try {
                gg.clipRect(r.x, r.y, r.width, r.height);
                cycles |= ground.paint(gg, cell);
            } finally {
                gg.dispose();
            }
        }
        // Not clipped to the tile: the centre's second row (y + 14, 12
        // high) reaches 2 px into the tile below it, as the original's (V
        // clip 020 #2260 y 80, dago Base #1356, Fur City, Fur Town), all
        // the tiles' terrain having been drawn first; a later tile's
        // overlays go over it.
        final int black = c.rgb(ClassicColonyLayout.INK_BLACK);
        for (Cell cell : m.cells) {
            if (cell == null) continue;
            final Rectangle r = cell.bounds();
            final Graphics2D gg = (Graphics2D) g.create();
            try {
                if (cell.totem) {
                    draw(gg, a.icon(ClassicColonyLayout.ICON_TOTEM),
                         r.x + ClassicColonyLayout.TOTEM_DX, r.y + ClassicColonyLayout.TOTEM_DY);
                }
                for (TileRow tr : cell.rows) {
                    final BufferedImage icon = a.icon(tr.icon);
                    if (icon == null) continue;
                    for (int j = 0; j < tr.row.m; j++) gg.drawImage(icon, tr.row.x(j), tr.row.y, null);
                    if (tr.row.stacked && labels) {
                        ClassicColonyBuildings.countLabel(gg, c, String.valueOf(tr.row.n),
                                                          tr.row.labelX(), tr.row.labelY());
                    }
                }
                if (cell.zero >= 0) {
                    final BufferedImage icon = a.icon(cell.zero);
                    if (icon != null) {
                        gg.drawImage(icon, ClassicColonyLayout.zeroIconX(r.x, icon.getWidth()),
                                     r.y + ClassicColonyLayout.TILE_ROW_DY, null);
                    }
                    draw(gg, a.icon(ClassicColonyLayout.ICON_NO_PRODUCTION), r.x, r.y);
                }
                if (cell.worker != null) {
                    final BufferedImage s = a.image(cell.worker.key);
                    final BufferedImage sh = a.silhouette(cell.worker.key, black);
                    if (sh != null) {
                        gg.drawImage(sh, cell.worker.x + ClassicColonyLayout.SHADOW_DX,
                                     cell.worker.y, null);
                    }
                    draw(gg, s, cell.worker.x, cell.worker.y);
                }
                if (cell.occupier != null && standing != null) {
                    standing.paint(gg, cell.occupier, r.x + ClassicColonyLayout.ENEMY_DX,
                                   r.y + ClassicColonyLayout.ENEMY_DY);
                }
            } finally {
                gg.dispose();
            }
        }
        // A tile another colony works: red (ink 12) on its own border (V
        // clip 048 #86873: x 224-247, y 80-103 and its neighbours, each
        // cell its own frame), under the green and the white.
        for (int i = 0; i < ClassicColonyLayout.TILE_COUNT; i++) {
            final Cell cell = m.cell(i);
            if (cell != null && cell.taken) {
                frame(g, ClassicColonyLayout.tileCell(i), c.rgb(ClassicColonyLayout.INK_RED));
            }
        }
        // The selected colonist's tile green (ink 10), the cursor white (ink
        // 15) over it, both on the tile's own border (V #2260: green on NW
        // x 224-247 y 32-55 over the corn row's first column, white on the
        // centre; #50260: the cursor white on the selected one's tile).
        if (green >= 0 && green < ClassicColonyLayout.TILE_COUNT && m.cell(green) != null) {
            frame(g, ClassicColonyLayout.tileCell(green), c.rgb(ClassicColonyLayout.INK_GREEN));
        }
        if (cursor >= 0 && cursor < ClassicColonyLayout.TILE_COUNT && m.cell(cursor) != null) {
            frame(g, ClassicColonyLayout.tileCell(cursor), c.rgb(ClassicColonyLayout.INK_WHITE));
        }
        return cycles;
    }

    /**
     * A tile's hover label: white FONTTINY on its black (w + 1) x 7 box at
     * {@link ClassicColonyLayout#tileLabelX}, the tile's y, not clamped.
     *
     * @param g The canvas.
     * @param c The chrome's assets.
     * @param cell The tile.
     * @return The box, or null for none.
     */
    static Rectangle paintLabel(Graphics2D g, ClassicColonyChrome.Assets c, Cell cell) {
        if (cell == null || cell.label == null || cell.label.isEmpty()) return null;
        final Rectangle r = cell.bounds();
        final int w = ClassicColonyChrome.textWidth(c, cell.label);
        final Rectangle box = new Rectangle(ClassicColonyLayout.tileLabelX(r.x, w), r.y, w + 1,
                                            ClassicColonyLayout.HOVER_H);
        g.setColor(new Color(c.rgb(ClassicColonyLayout.INK_BLACK)));
        g.fillRect(box.x, box.y, box.width, box.height);
        ClassicColonyChrome.text(g, c, cell.label, box.x + 1, box.y + 1,
                                 ClassicColonyLayout.INK_WHITE);
        return box;
    }

    private static void draw(Graphics2D g, BufferedImage s, int x, int y) {
        if (s != null) g.drawImage(s, x, y, null);
    }

    /** A 1-px frame along a rectangle's border. */
    private static void frame(Graphics2D g, Rectangle r, int rgb) {
        g.setColor(new Color(rgb));
        g.fillRect(r.x, r.y, r.width, 1);
        g.fillRect(r.x, r.y + r.height - 1, r.width, 1);
        g.fillRect(r.x, r.y, 1, r.height);
        g.fillRect(r.x + r.width - 1, r.y, 1, r.height);
    }
}
