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
 * each with our map's art of the tile scaled to 24 px (the original's finer
 * 24-px art comes from a scaler nobody has found, L §4.2: the one element
 * that is not the original's), the colony's sprite on the centre, the
 * production rows (the centre's food at y + 1 and its other good at y + 14),
 * the workers at tile + (14,6) with their black shadows, the red
 * prohibition sign over the good of a worker who makes nothing, the totem
 * on native land, an enemy at war on his tile, the selected colonist's
 * green frame, the blinking white cursor, the tile's hover label.
 *
 * <p>A pure model ({@link #model}) of what is drawn, then one painter
 * ({@link #paint}), as {@link ClassicColonyBuildings}; every number comes
 * from {@link ClassicColonyLayout}.  Draw order (V: the frames over the
 * rows, #2260; the worker after his row, dago; the centre's second row
 * over the tile below, #2260 y 80): the nine tiles' art, then per tile in
 * their order the totem, the rows with their count labels, the sign, the
 * worker, the enemy; then the green frame and over it the white cursor.
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

        Cell(int index, Tile tile, ColonyTile colonyTile, List<TileRow> rows, int zero,
             Figure worker, boolean totem, Unit occupier, String label) {
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
                // His job's good: what the tile makes of it.  FreeCol puts
                // a colonist whose job the tile cannot do on the tile's own
                // best good instead (I: no FreeCol job menu offers that):
                // then what it makes is shown.  Nothing made: the good's
                // icon under the prohibition sign (V #48712, #50260).
                final GoodsType job = u.getWorkType();
                int n = 0;
                for (AbstractGoods ag : made) if (ag.getType() == job) n += ag.getAmount();
                final AbstractGoods shown = (n > 0) ? new AbstractGoods(job, n)
                    : (made.isEmpty()) ? null : made.get(0);
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
                        label(text, t));
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


    // The art: our map's tile scaled to 24 px (L §4.2, the open point)

    /**
     * The terrain of the 3x3 map: each tile as our map composes it (the
     * pack's TERRAIN and PHYS0 index sheets through the game palette at
     * the water's phase, {@link ClassicTerrainComposer}; the RGBA tiles of
     * the map's fallback without them), the colony's sprite on the centre
     * where the map draws it, the 16 px scaled to 24 by source pixel
     * floor((2d + 1)/3) (L §4.2's closest of the rules tried: 9-28 % of the
     * original's pixels, so this is ours, not the original's).  The
     * original's palette cycles the water of its 3x3 map with the map's
     * (V clip 020: 144 px every 40-41 frames, #2262, #2303, #2343): ours
     * too, a palette step repaints the cells that hold a cycling index.
     */
    static final class Art {

        private final ClassicTerrainComposer composer;
        private final ClassicGamePalette palette;

        /** The fallback's art (no index sheets), or null. */
        private final ImageLibrary lib;
        private ClassicTileArt tileArt = null;

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
         * A tile's 24x24 picture now.
         *
         * @param map The map.
         * @param t The tile.
         * @param settlement The sprite on it (the colony's on the centre,
         *     as the map draws it), or null.
         * @param phase The water's cycling phase.
         * @param cycles Set to true when the tile holds a cycling index.
         * @return The picture, or null without any art.
         */
        BufferedImage tile(net.sf.freecol.common.model.Map map, Tile t, BufferedImage settlement,
                           int phase, boolean[] cycles) {
            final int cell = ClassicTerrainComposer.CELL;
            final int[] rgb = new int[cell * cell];
            if (indexed()) {
                final byte[] idx = new byte[cell * cell];
                final boolean c = this.composer.composeCell(source(map), t.getX(), t.getY(),
                                                            idx, 0, cell);
                if (cycles != null && c) cycles[0] = true;
                final IndexColorModel cm = this.palette.colorModel(phase);
                for (int k = 0; k < idx.length; k++) rgb[k] = cm.getRGB(idx[k] & 0xFF) | 0xFF000000;
            } else if (this.lib != null && t.getType() != null) {
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
                // overhanging both sides (ClassicHud.settlementOffset).
                final int ox = ClassicHud.settlementOffset(settlement.getWidth());
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
     * A square picture scaled up by the source pixel floor((2d + 1) s /
     * (2 S)) for S = 24, s = 16: floor((2d + 1)/3).
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
                final int sx = Math.min(s - 1, ((2 * x + 1) * s) / (2 * size));
                out.setRGB(x, y, rgb[sy * s + sx]);
            }
        }
        return out;
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
                if (t != null && t.getType() != null) {
                    ClassicTileArt.overlayFrames(map, t, ClassicTileArt.modelTypes(map), frames);
                }
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
