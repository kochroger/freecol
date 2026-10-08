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
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntFunction;

import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.AbstractGoods;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Direction;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.GoodsContainer;
import net.sf.freecol.common.model.Location;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.ResourceType;
import net.sf.freecol.common.model.Role;
import net.sf.freecol.common.model.Settlement;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileImprovement;
import net.sf.freecol.common.model.Unit;


/**
 * The original's <b>right-hand panel</b> (x 240..319, y 8..199 of the
 * 320x200 screen), as static painters over plain models so the live HUD
 * and the headless preview harness draw the same pixels.
 *
 * <p>Two modes.  The first game scene (Dutch {@code opening_083}, English
 * {@code 049}) shows the chrome, the minimap and the season and gold lines
 * only -- the <em>scene mode</em>.  In play the panel adds the
 * <b>active-unit block</b> and below it the <b>unit list</b> (the cargo of
 * a carrier, else the other units on the tile): Steam {@code 032} (Dutch
 * merchantman with a veteran soldier and a pioneer aboard), start-sequence
 * {@code 052} (English caravel, same cargo), Steam {@code 000} (late game:
 * a settler on a forest road with a hardy pioneer and a fortified veteran
 * dragoon).  All five are reproduced with 0 differing pixels over the whole
 * panel (the minimap's terrain pixels copied from the capture for 000).
 * The unit block is described at {@link #paintUnits}.
 *
 * <p>Measured constants (all 320x200 pixels; colours as DOSBox expands the
 * palette):
 * <ul>
 *   <li>Chrome: a black column at x = 240 (y 8..199), then
 *       {@code WOODTILE.SS.000} over x 241..319, y 8..199 with its phase
 *       anchored at screen (0,0) -- continuous with the top strip's wood
 *       ({@link ClassicMenuBar}); {@code 032}'s lower panel matches it in
 *       3,792/3,792 pixels.  Not {@code WOODPANL.PIK}.</li>
 *   <li>Minimap: a 1-px {@code 0xAA5500} ring at (251,8,58,41) around a
 *       56x39 interior at (252,9), black where unexplored, ONE pixel per
 *       map tile.  Explored ocean is {@code 0x202C8A}; a tile with a unit
 *       or colony takes its owner's colour (Holland {@code 0xFF7100}, the
 *       ship pixel (304,28) in 083).</li>
 *   <li>Window: horizontally {@code px = 251 + mapX} on the original's
 *       58-column map (no horizontal scroll); vertically it scrolls so the
 *       viewport ring sits at minimap rows 13..24 (y 22..33) unless clamped
 *       at the map edge (Steam 000: ring at y = 26).</li>
 *   <li>Viewport ring: white, 15x12 (the map shows 15x12 tiles of 16 px),
 *       drawn last; at (293,22) in 032/052/083.</li>
 *   <li>A black row at y = 49 (x 240..319); the season line at (242,51) and
 *       the gold line at (242,58), FONTTINY in the green ink
 *       {@code 0x559634}.  The gold line is exactly {@code goldLabel + gold +
 *       "$" + two spaces + taxLabel + " " + tax + "%"} -- no space after the
 *       colon (120/120 ink pixels; with a space 101/120), and FONTTINY's '$'
 *       is the small coin glyph ("Gold:44¤  Steuer: 0%", playthrough-1
 *       #23934 on).  A glyph that would not end by x = 319 is not drawn at
 *       all ({@link #fitting}): with 4-digit gold the "%" would start at
 *       x 318 and x 318-319 stay empty ("Gold:1000¤  Steuer: 0", #2436,
 *       #20153; gap list Q3).</li>
 * </ul>
 */
final class ClassicHud {

    /** The panel's rectangle. */
    static final int PANEL_X = 240, PANEL_Y = 8, PANEL_W = 80, PANEL_H = 192;

    /** Ink colours: the GAME theme's green and gold. */
    static final int GREEN = 0x559634, GOLD = 0xC7A220;

    /** The minimap frame, explored ocean and viewport ring colours. */
    static final int FRAME_RGB = 0xAA5500, OCEAN_RGB = 0x202C8A,
        RING_RGB = 0xFFFFFF;

    /** The minimap's frame (outer, 1 px) and interior. */
    static final Rectangle MINIMAP_FRAME = new Rectangle(251, 8, 58, 41),
        MINIMAP = new Rectangle(252, 9, 56, 39);

    /** The map view's size in tiles (the viewport ring's size). */
    static final int VIEW_COLS = 15, VIEW_ROWS = 12;

    /**
     * The original puts the unit it recentres on in view column 7 and row
     * 6 of the 15x12 map (ring and ship in 083: column 11 only because the
     * view is clamped at the map's right edge).  Both measured: landfall
     * 02 section 4.1, column 7 in 12 of 12 unclamped jumps, row 6 in 13 of
     * 13.
     */
    static final int UNIT_COL = 7, UNIT_ROW = 6;

    /**
     * The view's margin in cells: a unit in the outer two columns or rows
     * makes the view jump (landfall 02 section 4.2, V: columns 2..12 and
     * rows 2..9 are safe; column 1 and row 10 jumped, column 2 and row 9
     * never did).
     */
    static final int VIEW_MARGIN = 2;

    /** The minimap row at which the viewport ring sits unless clamped. */
    static final int RING_ROW = 13;

    /** The black rule under the minimap and the status lines. */
    static final int STATUS_RULE_Y = 49, STATUS_X = 242, SEASON_Y = 51,
        GOLD_Y = 58;

    /** LABELS.TXT {@code @CTITLE} indices of the gold (:256) and tax (:264) labels. */
    static final int CTITLE_GOLD = 1, CTITLE_TAX = 9;

    /** A minimap cell that is not explored (drawn black). */
    static final int UNEXPLORED = -1;

    /**
     * The active unit's minimap pixel while its blink is OFF: white, index
     * 15 (landfall: 13 while the sprite is ON, 15 while OFF, 485 of 490
     * toggles, {@code minimap_dot.txt}).
     */
    static final int BLINK_DOT_RGB = 0xFFFFFF;

    /**
     * The nations' fill colours in the original order (England, France,
     * Spain, Holland); -1 = not measured, use FreeCol's nation colour.
     * Measured: England {@code 0xFF0000} (049/052 flags), Holland
     * {@code 0xFF7100} (083/032), France {@code 0x5555FF} and Spain
     * {@code 0xFFFF55} (palette indices 9 and 14, the turn indicator of
     * the landfall clip; they match NAMES.TXT {@code @COUNTRY}).
     */
    private static final int[] NATION_FILL
        = { 0xFF0000, 0x5555FF, 0xFFFF55, 0xFF7100 };

    /**
     * The turn indicator (build spec W5c): a solid 5x3 box in the panel's
     * bottom-right corner, over the wood, in the colour of the player
     * whose turn it is ({@link #indicatorRgb}); the wood is back when it
     * goes (landfall 03 section 2).
     */
    static final Rectangle INDICATOR = new Rectangle(315, 197, 5, 3);

    /**
     * The turn indicator's colours (landfall 03 section 2, V: the NAMES.TXT
     * {@code @TRIBES} last column and {@code @COUNTRY} as DOSBox shows
     * them), by FreeCol nation id.  Holland's is ours, shown while our
     * turn-start boxes are up.
     */
    private static final java.util.Map<String, Integer> INDICATOR_RGB
        = java.util.Map.ofEntries(
            java.util.Map.entry("model.nation.inca", 0xF7F3C7),
            java.util.Map.entry("model.nation.aztec", 0xC7A220),
            java.util.Map.entry("model.nation.arawak", 0x698AC3),
            java.util.Map.entry("model.nation.iroquois", 0x6D3C18),
            java.util.Map.entry("model.nation.cherokee", 0x75A64D),
            java.util.Map.entry("model.nation.apache", 0xC3AE86),
            java.util.Map.entry("model.nation.sioux", 0x920000),
            java.util.Map.entry("model.nation.tupi", 0x045D04),
            java.util.Map.entry("model.nation.english", 0xFF0000),
            java.util.Map.entry("model.nation.french", 0x5555FF),
            java.util.Map.entry("model.nation.spanish", 0xFFFF55),
            java.util.Map.entry("model.nation.dutch", 0xFF7100));

    /**
     * The minimap's land colours by FreeCol tile-type suffix (build spec
     * W16; G2 spec 3.1): the original's palette entries, measured over all
     * matched frames of clips 004-008 and the landfall clip.  A forest has
     * its base terrain's colour (V six times: mixed = plains, broadleaf =
     * prairie, conifer = grassland, tropical = savannah, wetland = marsh,
     * scrub = desert); roads, rivers, plowing and resources do not change
     * it (clip005 (14,57) "Flachland (Straße)", landfall (47,46) "(Wild)").
     */
    private static final java.util.Map<String, Integer> MINIMAP_LAND_RGB
        = java.util.Map.ofEntries(
            // Index 72: clip006 #2037 (22,18) .. (24,20), unforested tiles
            // whose cell is TERRAIN.SS.000; boreal by the pairing (I).
            java.util.Map.entry("tundra", 0xBABA41),
            java.util.Map.entry("borealForest", 0xBABA41),
            // 88: tiles of TERRAIN.SS.001/008; scrub clip008 (43,43), (47,41).
            java.util.Map.entry("desert", 0xCFB28E),
            java.util.Map.entry("scrubForest", 0xCFB28E),
            // 92: clip005 (14,57) "Flachland"; mixed clip008 (44,44).
            java.util.Map.entry("plains", 0x867151),
            java.util.Map.entry("mixedForest", 0x867151),
            // 75: clip005 (25,20) "Prärie"; broadleaf landfall (47,46).
            java.util.Map.entry("prairie", 0x8A8E3C),
            java.util.Map.entry("broadleafForest", 0x8A8E3C),
            // 70: clip005 (24,23) "Grünland"; conifer landfall (49,43).
            java.util.Map.entry("grassland", 0x1C6D10),
            java.util.Map.entry("coniferForest", 0x1C6D10),
            // 67: savannah from the map art only (clip006 #2037 (25,31));
            // tropical landfall (46,42).
            java.util.Map.entry("savannah", 0x75A64D),
            java.util.Map.entry("tropicalForest", 0x75A64D),
            // 58: marsh from the map art only (clip006 #2037 (22,14));
            // wetland landfall and clip008 (47,45).
            java.util.Map.entry("marsh", 0x34499E),
            java.util.Map.entry("wetlandForest", 0x34499E),
            // 67: rain forest landfall, clip008 (50,44); swamp by the pairing (I).
            java.util.Map.entry("swamp", 0x75A64D),
            java.util.Map.entry("rainForest", 0x75A64D),
            // 89: clip005 (23,16) "Hügellandschaft"; 108: landfall (47,43).
            java.util.Map.entry("hills", 0xBAA27D),
            java.util.Map.entry("mountains", 0xDBCFAE),
            // Arctic is in no clip: the light grey of index 19 (I), kept
            // apart from the white blink dot and ring.
            java.util.Map.entry("arctic", 0xE3E3E3));


    /**
     * What the minimap shows: one colour per map tile plus where the map
     * view is.  Plain data, built from the game by {@link #minimapOf} or by
     * hand in the preview harness.
     */
    static final class MinimapModel {

        /** The map's size in tiles. */
        final int mapWidth, mapHeight;

        /** Row-major RGB per tile, {@link #UNEXPLORED} for black. */
        final int[] rgb;

        /** The map view's top-left tile. */
        final int c0, r0;

        MinimapModel(int mapWidth, int mapHeight, int[] rgb, int c0, int r0) {
            this.mapWidth = mapWidth;
            this.mapHeight = mapHeight;
            this.rgb = rgb;
            this.c0 = c0;
            this.r0 = r0;
        }

        /** @return The colour of tile (x, y), {@link #UNEXPLORED} outside. */
        int at(int x, int y) {
            if (x < 0 || y < 0 || x >= this.mapWidth || y >= this.mapHeight) {
                return UNEXPLORED;
            }
            return this.rgb[y * this.mapWidth + x];
        }

        /**
         * This minimap with one tile's pixel replaced (the active unit's
         * blinking dot, {@link #BLINK_DOT_RGB}).
         *
         * @param x The tile's column.
         * @param y The tile's row.
         * @param c The colour.
         * @return A new model; this one if (x, y) is off the map.
         */
        MinimapModel with(int x, int y, int c) {
            if (x < 0 || y < 0 || x >= this.mapWidth || y >= this.mapHeight) {
                return this;
            }
            final int[] p = this.rgb.clone();
            p[y * this.mapWidth + x] = c;
            return new MinimapModel(this.mapWidth, this.mapHeight, p,
                                    this.c0, this.r0);
        }
    }

    /** Everything the panel paints. */
    static final class PanelModel {

        /** The minimap, or null for an empty (black) one. */
        final MinimapModel minimap;

        /** The season line (season name and year) and the gold line. */
        final String season, gold;

        /** Scene mode: no unit block (the first scene). */
        final boolean scene;

        /** The active unit, or null. */
        final UnitFacts active;

        /** The unit list below it (cargo, or the tile's other units). */
        final List<UnitFacts> list;

        /** The turn indicator's colour ({@link #INDICATOR}), or -1 for none. */
        final int indicator;

        /**
         * The tile mode's tile (the Spielzugende mode, build spec W17),
         * shown instead of the unit block, or null.
         */
        final TileFacts tile;

        /** The word "Spielzugende" under the tile mode in this colour, or -1 for none. */
        final int promptRgb;

        /** The colony on the active unit's tile (build spec W21b), or null. */
        final ColonyFacts colony;

        PanelModel(MinimapModel minimap, String season, String gold,
                   boolean scene) {
            this(minimap, season, gold, scene, null, null);
        }

        PanelModel(MinimapModel minimap, String season, String gold,
                   boolean scene, UnitFacts active, List<UnitFacts> list) {
            this(minimap, season, gold, scene, active, list, -1);
        }

        PanelModel(MinimapModel minimap, String season, String gold,
                   boolean scene, UnitFacts active, List<UnitFacts> list,
                   int indicator) {
            this(minimap, season, gold, scene, active, list, indicator, null, -1);
        }

        PanelModel(MinimapModel minimap, String season, String gold,
                   boolean scene, UnitFacts active, List<UnitFacts> list,
                   int indicator, TileFacts tile, int promptRgb) {
            this(minimap, season, gold, scene, active, list, indicator, tile,
                 promptRgb, null);
        }

        PanelModel(MinimapModel minimap, String season, String gold,
                   boolean scene, UnitFacts active, List<UnitFacts> list,
                   int indicator, TileFacts tile, int promptRgb,
                   ColonyFacts colony) {
            this.minimap = minimap;
            this.season = season;
            this.gold = gold;
            this.scene = scene;
            this.active = active;
            this.list = (list == null) ? new ArrayList<>() : list;
            this.indicator = indicator;
            this.tile = tile;
            this.promptRgb = promptRgb;
            this.colony = colony;
        }
    }

    /**
     * One goods icon of a "Mit:" row or of a carrier's list entry (build
     * spec W21b): ICONS.SS {@link #ICON_GOODS} + the {@code @CARGO} row
     * for a full hold (a colony: at least 100), the grey
     * {@link #ICON_GOODS_GREY} + row otherwise (clip008 #32928: ore and
     * furs grey in a ship's partly filled holds; dago-colony2 #4045: all
     * grey in a warehouse under 100).
     */
    static final class GoodsIcon {

        /** NAMES.TXT {@code @CARGO} row. */
        final int cargoRow;

        /** A full hold: the coloured icon. */
        final boolean full;

        /** The icon, or null without the pack (then only the place is kept). */
        final BufferedImage icon;

        GoodsIcon(int cargoRow, boolean full, BufferedImage icon) {
            this.cargoRow = cargoRow;
            this.full = full;
            this.icon = icon;
        }

        /** @return The ICONS.SS frame of this icon. */
        int frame() {
            return (this.full ? ICON_GOODS : ICON_GOODS_GREY) + this.cargoRow;
        }

        @Override
        public String toString() {
            return String.format("%03d", frame());
        }
    }

    /**
     * A colony as the unit block and the tile mode show it under the
     * lines (build spec W21b; clip005 #14364, #14852, #15391, clip006
     * #9345, clip008 #4009, #14868, #35590, dago-colony2 #4045): its sprite
     * with the nation's flag, its name, and a "Mit:" row of goods icons.
     */
    static final class ColonyFacts {

        /** The colony's sprite, the flag in the nation's colours, or null. */
        final BufferedImage sprite;

        /** The colony's name. */
        final String name;

        /** The "Mit:" row's icons. */
        final List<GoodsIcon> goods;

        ColonyFacts(BufferedImage sprite, String name, List<GoodsIcon> goods) {
            this.sprite = sprite;
            this.name = (name == null) ? "" : name;
            this.goods = (goods == null) ? new ArrayList<>() : goods;
        }

        /**
         * The facts of a live colony.  Its "Mit:" row (I: which goods, in
         * which order and when coloured are in no rule the clips give; Base
         * showed 5 of its 11 goods): the storable goods in the warehouse,
         * by {@code @CARGO} row, coloured from 100, at most
         * {@link #COLONY_GOODS_MAX}.
         *
         * @param c The colony.
         * @param sprite Its sprite as the map draws it, or null.
         * @param icons ICONS.SS frame to picture, or null without the pack.
         * @return The facts.
         */
        static ColonyFacts of(Colony c, BufferedImage sprite,
                              IntFunction<BufferedImage> icons) {
            final List<GoodsIcon> goods = new ArrayList<>();
            for (Goods g : c.getCompactGoodsList()) {
                if (g.getType() == null || !g.getType().isStorable()
                    || g.getAmount() <= 0) continue;
                final int row = cargoRow(g.getType().getId());
                if (row < 0) continue;
                goods.add(goodsIcon(row, g.getAmount() >= GoodsContainer.CARGO_SIZE,
                                    icons));
            }
            goods.sort(Comparator.comparingInt((GoodsIcon i) -> i.cargoRow));
            return new ColonyFacts(colonyFlag(fitSettlement(sprite), c.getOwner()),
                c.getName(), new ArrayList<>(goods.subList(0,
                    Math.min(goods.size(), COLONY_GOODS_MAX))));
        }
    }

    /**
     * What the panel needs to know about one unit: plain data, built from a
     * FreeCol unit by {@link #of} or by hand in the preview harness, so the
     * painter never touches the model.
     */
    static final class UnitFacts {

        /** The unit's map sprite, at most 16x16 (ICONS.SS in the pack). */
        final BufferedImage sprite;

        /** Flag fill and the darker letter shade. */
        final int fill, dark;

        /** Original nation index (england 0 .. dutch 3), or -1. */
        final int nation;

        /** NAMES.TXT {@code @UNIT} row, or -1 (then {@link #plainName}). */
        final int unitRow;

        /** The name when there is no {@code @UNIT} row (FreeCol's label). */
        final String plainName;

        /** Moves left, in thirds. */
        final int moves;

        /** Map position. */
        final int x, y;

        /** NAMES.TXT {@code @ORDERS} row ({@link #ORDERS_NONE} ...). */
        final int ordersRow;

        /** FreeCol tile type id of its tile (for the terrain line), or null. */
        final String terrainId;

        /** Whether its tile has a road. */
        final boolean road;

        /** NAMES.TXT {@code @JOB} row (the colonist's skill), or -1. */
        final int jobRow;

        /** A colonist without a role (no soldier/pioneer/... equipment). */
        final boolean roleless;

        /** {@link #QUAL_NONE}, {@link #QUAL_VETERAN} or {@link #QUAL_EXPERT}. */
        final int qualifier;

        /** Tools carried (pioneers), or -1. */
        final int tools;

        /** The gold of a treasure train, or -1 (build spec W21b). */
        final int treasure;

        /** The colony a goto order leads to, or null (then the orders' name). */
        final String destination;

        /** A goto order leads to the owner's Europe ("Ziel Amsterdam", R2). */
        final boolean homePort;

        /** Its tile's river (0 none, 1 minor, 2 major), plowing and {@code @RESOURCE} row (or -1). */
        final int river;
        final boolean plowed;
        final int resourceRow;

        /** The goods aboard, one icon per hold, or empty. */
        final List<GoodsIcon> cargo;

        UnitFacts(BufferedImage sprite, int fill, int dark, int nation,
                  int unitRow, String plainName, int moves, int x, int y,
                  int ordersRow, String terrainId, boolean road, int jobRow,
                  boolean roleless, int qualifier, int tools) {
            this(sprite, fill, dark, nation, unitRow, plainName, moves, x, y,
                 ordersRow, terrainId, road, jobRow, roleless, qualifier, tools,
                 -1, null, 0, false, -1, null);
        }

        UnitFacts(BufferedImage sprite, int fill, int dark, int nation,
                  int unitRow, String plainName, int moves, int x, int y,
                  int ordersRow, String terrainId, boolean road, int jobRow,
                  boolean roleless, int qualifier, int tools, int treasure,
                  String destination, int river, boolean plowed,
                  int resourceRow, List<GoodsIcon> cargo) {
            this(sprite, fill, dark, nation, unitRow, plainName, moves, x, y,
                 ordersRow, terrainId, road, jobRow, roleless, qualifier, tools,
                 treasure, destination, false, river, plowed, resourceRow, cargo);
        }

        UnitFacts(BufferedImage sprite, int fill, int dark, int nation,
                  int unitRow, String plainName, int moves, int x, int y,
                  int ordersRow, String terrainId, boolean road, int jobRow,
                  boolean roleless, int qualifier, int tools, int treasure,
                  String destination, boolean homePort, int river,
                  boolean plowed, int resourceRow, List<GoodsIcon> cargo) {
            this.sprite = sprite;
            this.fill = fill;
            this.dark = dark;
            this.nation = nation;
            this.unitRow = unitRow;
            this.plainName = plainName;
            this.moves = moves;
            this.x = x;
            this.y = y;
            this.ordersRow = ordersRow;
            this.terrainId = terrainId;
            this.road = road;
            this.jobRow = jobRow;
            this.roleless = roleless;
            this.qualifier = qualifier;
            this.tools = tools;
            this.treasure = treasure;
            this.destination = destination;
            this.homePort = homePort;
            this.river = river;
            this.plowed = plowed;
            this.resourceRow = resourceRow;
            this.cargo = (cargo == null) ? new ArrayList<>() : cargo;
        }

        /**
         * The facts of a live unit, without goods icons.
         *
         * @param u The unit.
         * @param sprite Its map sprite (any size; scaled down to 16x16 when
         *     larger, i.e. without the pack).
         * @return The facts.
         */
        static UnitFacts of(Unit u, BufferedImage sprite) {
            return of(u, sprite, null);
        }

        /**
         * The facts of a live unit.
         *
         * @param u The unit.
         * @param sprite Its map sprite (any size; scaled down to 16x16 when
         *     larger, i.e. without the pack).
         * @param icons ICONS.SS frame to picture for the goods aboard, or
         *     null (the icons' places only).
         * @return The facts.
         */
        static UnitFacts of(Unit u, BufferedImage sprite,
                            IntFunction<BufferedImage> icons) {
            final Player owner = u.getOwner();
            final int nation = (owner == null) ? -1
                : Arrays.asList(ClassicNewWorldScreens.NATION_IDS)
                    .indexOf(owner.getNationId());
            final Tile t = u.getTile();
            final String role = (u.getRole() == null) ? null
                : u.getRole().getRoleSuffix();
            final String type = Role.getRoleIdSuffix(u.getType().getId());
            final boolean person = u.isPerson();
            final boolean roleless = person
                && (u.getRole() == null || u.getRole().isDefaultRole());
            int tools = -1;
            if ("pioneer".equals(role)) {
                tools = 0;
                for (AbstractGoods ag
                         : u.getRole().getRequiredGoodsList(u.getRoleCount())) {
                    if (ag.getType() != null
                        && "model.goods.tools".equals(ag.getType().getId())) {
                        tools += ag.getAmount();
                    }
                }
            }
            String plain;
            try {
                plain = Messages.message(u.getLabel());
            } catch (RuntimeException e) {
                plain = type;
            }
            final int orders = ClassicUnitCycle.ordersRowShown(u);   // held until a visit (W5f)
            final List<GoodsIcon> cargo = new ArrayList<>();
            if (u.isCarrier()) {
                for (Goods g : u.getGoodsList()) {
                    final int row = (g.getType() == null) ? -1
                        : cargoRow(g.getType().getId());
                    if (row < 0 || g.getAmount() <= 0) continue;
                    cargo.add(goodsIcon(row,
                        g.getAmount() >= GoodsContainer.CARGO_SIZE, icons));
                }
                // FreeCol keeps no loading order (a hash map): by goods
                // type, full holds first (the original's order is the
                // loading order, clip008 01-shipcolony; I).
                cargo.sort(Comparator.comparingInt((GoodsIcon i) -> i.cargoRow)
                    .thenComparing(i -> !i.full));
            }
            return new UnitFacts(fit16(sprite), nationRgb(owner),
                nationDark(owner), nation, unitRow(type, role), plain,
                Math.max(0, u.getMovesLeft()),
                (t == null) ? 0 : t.getX(), (t == null) ? 0 : t.getY(),
                orders,
                (t == null || t.getType() == null) ? null : t.getType().getId(),
                ClassicUnitCycle.roadShown(t),
                person ? jobRow(type) : -1, roleless,
                qualifier(type, role), tools,
                u.canCarryTreasure() ? u.getTreasureAmount() : -1,
                destinationName(u, orders), toHomePort(u, orders),
                (t == null) ? 0 : riverOf(t), t != null && plowed(t),
                (t == null) ? -1 : resourceRowOf(t), cargo);
        }

        /**
         * The name of the colony a goto order leads to (clip005 #14481:
         * the artillery's "Fur Town" in gold, letter G), or null for no
         * goto or another destination (a tile, Europe, a village: the
         * orders' "Ziel"; I).
         */
        static String destinationName(Unit u, int ordersRow) {
            if (ordersRow != ORDERS_GOTO) return null;
            final Location d = u.getDestination();
            final Colony c = (d == null) ? null : d.getColony();
            return (c == null) ? null : c.getName();
        }

        /**
         * Whether a goto order leads to the owner's Europe: the orders line
         * "Ziel Amsterdam" (R2, landfall #23375; {@link #ordersText}).
         */
        static boolean toHomePort(Unit u, int ordersRow) {
            if (ordersRow != ORDERS_GOTO || u.getOwner() == null) return false;
            final Location d = u.getDestination();
            return d != null && d == u.getOwner().getEurope();
        }
    }

    /**
     * What the tile mode shows of one tile (build spec W17, Spielzugende
     * mode; clip004 analysis W21 item 1): plain data, built from a FreeCol
     * tile by {@link #of} or by hand in the tests and the preview harness.
     */
    static final class TileFacts {

        /** Map position. */
        final int x, y;

        /**
         * The number after the position: "1" in six of seven samples, "2"
         * once (landing-slow, clips 004/005); its meaning is unknown, so it
         * is always {@link #REGION_UNKNOWN} until Roger answers.
         */
        final int region;

        /** The tile is land: then the land name line comes first. */
        final boolean land;

        /** The player's own name for the new land (@LANDHO), or null: the nation's default. */
        final String landName;

        /** Original nation index of the player (@COLONYNAME row of the default name), or -1. */
        final int nation;

        /** NAMES.TXT {@code @TRIBES} row of a native settlement on the tile, or -1. */
        final int tribeRow;

        /** FreeCol tile type id (the terrain line), or null. */
        final String terrainId;

        /** River: 0 none, 1 minor, 2 major. */
        final int river;

        /** Road, plowed. */
        final boolean road, plowed;

        /** NAMES.TXT {@code @RESOURCE} row, or -1. */
        final int resourceRow;

        /** The native settlement's entry, or null. */
        final SettlementFacts settlement;

        /** The units on the tile, in the tile's order. */
        final List<UnitFacts> units;

        /** The colony on the tile (build spec W21b; clip005 #15391), or null. */
        final ColonyFacts colony;

        TileFacts(int x, int y, int region, boolean land, String landName,
                  int nation, int tribeRow, String terrainId, int river,
                  boolean road, boolean plowed, int resourceRow,
                  SettlementFacts settlement, List<UnitFacts> units) {
            this(x, y, region, land, landName, nation, tribeRow, terrainId,
                 river, road, plowed, resourceRow, settlement, units, null);
        }

        TileFacts(int x, int y, int region, boolean land, String landName,
                  int nation, int tribeRow, String terrainId, int river,
                  boolean road, boolean plowed, int resourceRow,
                  SettlementFacts settlement, List<UnitFacts> units,
                  ColonyFacts colony) {
            this.x = x;
            this.y = y;
            this.region = region;
            this.land = land;
            this.landName = landName;
            this.nation = nation;
            this.tribeRow = tribeRow;
            this.terrainId = terrainId;
            this.river = river;
            this.road = road;
            this.plowed = plowed;
            this.resourceRow = resourceRow;
            this.settlement = settlement;
            this.units = (units == null) ? new ArrayList<>() : units;
            this.colony = colony;
        }

        /**
         * The facts of a live tile, as the player knows it, without its
         * colony's block.
         *
         * @param tile The tile.
         * @param player The player whose land name is shown, or null.
         * @param settlementSprite The sprite of a native settlement on the
         *     tile, or null.
         * @param units The units' facts, in the tile's order.
         * @return The facts.
         */
        static TileFacts of(Tile tile, Player player, BufferedImage settlementSprite,
                            List<UnitFacts> units) {
            return of(tile, player, settlementSprite, units, null);
        }

        /**
         * The facts of a live tile, as the player knows it.
         *
         * @param tile The tile.
         * @param player The player whose land name is shown, or null.
         * @param settlementSprite The sprite of a native settlement on the
         *     tile, or null.
         * @param units The units' facts, in the tile's order.
         * @param colony The block of the colony on the tile, or null.
         * @return The facts.
         */
        static TileFacts of(Tile tile, Player player, BufferedImage settlementSprite,
                            List<UnitFacts> units, ColonyFacts colony) {
            final int nation = (player == null) ? -1
                : Arrays.asList(ClassicNewWorldScreens.NATION_IDS)
                    .indexOf(player.getNationId());
            final Settlement s = tile.getSettlement();
            final int tribe = (s == null || s.getOwner() == null
                               || !s.getOwner().isIndian()) ? -1
                : tribeRow(s.getOwner().getNationId());
            SettlementFacts sf = null;
            if (tribe >= 0) {
                sf = new SettlementFacts(fitSettlement(settlementSprite), tribe,
                                         s.isCapital());
            }
            return new TileFacts(tile.getX(), tile.getY(), REGION_UNKNOWN,
                tile.isLand(), ClassicBands.landName(player),
                nation, tribe, (tile.getType() == null) ? null : tile.getType().getId(),
                riverOf(tile), ClassicUnitCycle.roadShown(tile), plowed(tile),
                resourceRowOf(tile), sf, units, colony);
        }
    }

    /** A tile's river: 0 none, 1 minor, 2 major. */
    static int riverOf(Tile tile) {
        if (!tile.hasRiver()) return 0;
        final TileImprovement r = tile.getRiver();
        return (r != null && r.getMagnitude() >= TileImprovement.LARGE_RIVER) ? 2 : 1;
    }

    /** Whether a tile has a completed plow improvement. */
    static boolean plowed(Tile tile) {
        for (TileImprovement imp : tile.getCompleteTileImprovements()) {
            if (imp.getType() != null
                && "model.improvement.plow".equals(imp.getType().getId())) {
                return true;
            }
        }
        return false;
    }

    /** NAMES.TXT {@code @RESOURCE} row of a tile's resource, or -1. */
    static int resourceRowOf(Tile tile) {
        final ResourceType rt = (tile.getResource() == null) ? null
            : tile.getResource().getType();
        return (rt == null) ? -1 : resourceRow(rt.getId());
    }

    /**
     * A native settlement as the tile mode lists it (landing-slow #4193:
     * the hut, then the tribe and "ein Dorf" in green).
     */
    static final class SettlementFacts {

        /** The settlement's sprite (at most 22 wide, 16 high), or null. */
        final BufferedImage sprite;

        /** NAMES.TXT {@code @TRIBES} row. */
        final int tribeRow;

        /** The tribe's capital ({@code @LEVELS} row 4). */
        final boolean capital;

        SettlementFacts(BufferedImage sprite, int tribeRow, boolean capital) {
            this.sprite = sprite;
            this.tribeRow = tribeRow;
            this.capital = capital;
        }
    }


    private ClassicHud() {}   // static painters only


    // Geometry

    /**
     * The first map column the minimap shows.  The original's map is 58
     * columns wide and its minimap shows columns 1..56 without scrolling
     * ({@code px = 251 + mapX}).  A wider FreeCol map scrolls so the
     * viewport ring stays near the middle (assumed: the original never
     * needs to).
     */
    static int minimapOriginX(int mapWidth, int c0) {
        if (mapWidth <= MINIMAP.width + 2) return 1;
        return clamp(c0 + VIEW_COLS / 2 - MINIMAP.width / 2, 1,
                     mapWidth - MINIMAP.width - 1);
    }

    /**
     * The first map row the minimap shows: the ring at minimap row 13
     * (032/052/083: ring y = 22), clamped at the map's ends (000: y = 26).
     */
    static int minimapOriginY(int mapHeight, int r0) {
        return clamp(r0 - RING_ROW, 1, mapHeight - MINIMAP.height - 1);
    }

    /** The viewport ring (15x12) in screen pixels. */
    static Rectangle viewportRing(MinimapModel m) {
        return new Rectangle(
            MINIMAP.x + m.c0 - minimapOriginX(m.mapWidth, m.c0),
            MINIMAP.y + m.r0 - minimapOriginY(m.mapHeight, m.r0),
            VIEW_COLS, VIEW_ROWS);
    }

    /**
     * The original's framing of the map view around a unit: the unit in
     * view column {@link #UNIT_COL} and row {@link #UNIT_ROW}, clamped so
     * the view stays within map columns/rows 1 .. size-2 (083: ship at
     * column 53 of 58 gives {@code c0 = 42}, the ship in view column 11).
     *
     * @return {c0, r0}.
     */
    static int[] viewFor(int mapWidth, int mapHeight, int x, int y) {
        return new int[] {
            cover(clamp(x - UNIT_COL, 1, maxViewX(mapWidth)), x, VIEW_COLS),
            cover(clamp(y - UNIT_ROW, 1, maxViewY(mapHeight)), y, VIEW_ROWS)
        };
    }

    /**
     * A view origin moved just far enough that tile {@code t} is inside
     * its {@code n} cells.  Changes nothing for the tiles the clamped view
     * can show; it only reaches the map's outer ring, where the original
     * never puts a unit but FreeCol can (its entry location may be the
     * last column, x = 57 of 58).
     */
    private static int cover(int v, int t, int n) {
        return Math.max(t - n + 1, Math.min(v, t));
    }

    /**
     * The largest view origin column: the view's right edge on map column
     * {@code mapWidth - 2}, the outer ring is never shown (landfall 02
     * section 6: 42 on the original's 58 columns, V).
     */
    static int maxViewX(int mapWidth) {
        return mapWidth - VIEW_COLS - 1;
    }

    /** The largest view origin row (59 on 72 rows; I, by symmetry). */
    static int maxViewY(int mapHeight) {
        return mapHeight - VIEW_ROWS - 1;
    }

    /**
     * The last map column the view ever shows, the right screen edge at
     * the east clamp: {@code mapWidth - 2} (56 of 58, {@link #maxViewX}).
     */
    static int lastViewColumn(int mapWidth) {
        return mapWidth - 2;
    }

    /**
     * Whether a step goes east past the last column the view shows: E, NE
     * or SE (6, 9, 3) from that column (or the never-drawn ring beyond it)
     * onto the ring or off the map.  It is the step Roger's Europe question
     * answers at the east edge (build spec W8a,
     * {@code ClassicGUI.asksSailHome}).
     *
     * @param mapWidth The map's width.
     * @param d The direction.
     * @param fromX The unit's column.
     * @param toX The target tile's column, or -1 off the map.
     * @return True if the step leaves the drawn map eastward.
     */
    static boolean eastPastView(int mapWidth, Direction d, int fromX, int toX) {
        if (d != Direction.E && d != Direction.NE && d != Direction.SE) return false;
        final int last = lastViewColumn(mapWidth);
        return fromX >= last && (toX < 0 || toX > last);
    }

    /**
     * The first map column the view ever shows, the left screen edge at
     * the west clamp: 1, the outer ring (column 0) is never shown
     * ({@link #clampView}).
     */
    static int firstViewColumn() {
        return 1;
    }

    /**
     * Whether a step goes west past the first column the view shows: W, NW
     * or SW (4, 7, 1) from that column (or the never-drawn ring before it)
     * onto the ring or off the map.  The mirror of {@link #eastPastView}:
     * Roger's Europe question is asked at the west edge too (E1).
     *
     * @param d The direction.
     * @param fromX The unit's column.
     * @param toX The target tile's column, or -1 off the map.
     * @return True if the step leaves the drawn map westward.
     */
    static boolean westPastView(Direction d, int fromX, int toX) {
        if (d != Direction.W && d != Direction.NW && d != Direction.SW) return false;
        final int first = firstViewColumn();
        return fromX <= first && toX < first;
    }

    /**
     * Whether a step leaves the drawn map sideways, east past the last
     * column the view shows or west past the first one
     * ({@link #eastPastView}, {@link #westPastView}).  It is the step
     * Roger's Europe question answers ({@code ClassicGUI.asksSailHome}).
     *
     * @param mapWidth The map's width.
     * @param d The direction.
     * @param fromX The unit's column.
     * @param toX The target tile's column, or -1 off the map.
     * @return True if the step leaves the drawn map east or west.
     */
    static boolean sidePastView(int mapWidth, Direction d, int fromX, int toX) {
        return eastPastView(mapWidth, d, fromX, toX)
            || westPastView(d, fromX, toX);
    }

    /**
     * {@code (c0, r0)} clamped to the view origins a map allows
     * ({@link #viewFor}'s range).
     *
     * @return {c0, r0}.
     */
    static int[] clampView(int mapWidth, int mapHeight, int c0, int r0) {
        return new int[] { clamp(c0, 1, maxViewX(mapWidth)),
                           clamp(r0, 1, maxViewY(mapHeight)) };
    }

    /**
     * The original's jump test (build spec W4, landfall 02 section 9): a
     * unit on map tile {@code (x, y)} makes the view {@code (c0, r0)}
     * jump when its cell is in the margin ({@link #VIEW_MARGIN}) on a side
     * where the map goes on.  A side whose origin is already at its clamp
     * does not count (landfall #1147/#1385/#7497/#8357/#8782: columns 13
     * and 14 at the east clamp never jumped); a unit off the view always
     * makes it jump (in the clip every such side was free; at a clamp only
     * a unit on FreeCol's outer ring can be off the view).  Whoever calls
     * it recentres both axes ({@link #viewFor}).
     *
     * @return True if the view must jump.
     */
    static boolean needsRecentre(int mapWidth, int mapHeight, int c0, int r0,
                                 int x, int y) {
        final int c = x - c0, r = y - r0;
        if (c < 0 || c >= VIEW_COLS || r < 0 || r >= VIEW_ROWS) return true;
        return (c < VIEW_MARGIN && c0 > 1)
            || (c > VIEW_COLS - 1 - VIEW_MARGIN && c0 < maxViewX(mapWidth))
            || (r < VIEW_MARGIN && r0 > 1)
            || (r > VIEW_ROWS - 1 - VIEW_MARGIN && r0 < maxViewY(mapHeight));
    }

    /** {@code v} clamped to [lo, hi]; {@code lo} wins when hi &lt; lo. */
    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(v, hi));
    }


    // Painting

    /** A 1-px rectangle outline from four fills (exact at any scale). */
    private static void ring(Graphics2D g, int x, int y, int w, int h) {
        g.fillRect(x, y, w, 1);
        g.fillRect(x, y + h - 1, w, 1);
        g.fillRect(x, y, 1, h);
        g.fillRect(x + w - 1, y, 1, h);
    }

    /** The chrome: black column, wood, minimap frame, black rule. */
    static void paintChrome(Graphics2D g, BufferedImage wood) {
        g.setColor(Color.BLACK);
        g.fillRect(PANEL_X, PANEL_Y, 1, PANEL_H);
        ClassicMenuBox.fillTiled(g, wood,
            new Rectangle(PANEL_X + 1, PANEL_Y, PANEL_W - 1, PANEL_H), 0, 0,
            ClassicMenuBox.GAME.fallbackFill);
        g.setColor(new Color(FRAME_RGB));
        ring(g, MINIMAP_FRAME.x, MINIMAP_FRAME.y, MINIMAP_FRAME.width,
             MINIMAP_FRAME.height);
        g.setColor(Color.BLACK);
        g.fillRect(MINIMAP.x, MINIMAP.y, MINIMAP.width, MINIMAP.height);
        g.fillRect(PANEL_X, STATUS_RULE_Y, PANEL_W, 1);
    }

    /**
     * The minimap interior (one pixel per tile) and the viewport ring, which
     * is drawn last and may cover the frame-side pixels (049: the ship's
     * pixel lies under the ring's right edge).
     */
    static void paintMinimap(Graphics2D g, MinimapModel m) {
        if (m == null) return;
        final int mx0 = minimapOriginX(m.mapWidth, m.c0);
        final int my0 = minimapOriginY(m.mapHeight, m.r0);
        for (int dy = 0; dy < MINIMAP.height; dy++) {
            for (int dx = 0; dx < MINIMAP.width; dx++) {
                final int c = m.at(mx0 + dx, my0 + dy);
                if (c == UNEXPLORED) continue;
                g.setColor(new Color(c & 0xFFFFFF));
                g.fillRect(MINIMAP.x + dx, MINIMAP.y + dy, 1, 1);
            }
        }
        final Rectangle r = viewportRing(m);
        final Graphics2D gg = (Graphics2D) g.create();
        try {
            gg.clipRect(MINIMAP_FRAME.x, MINIMAP_FRAME.y, MINIMAP_FRAME.width,
                        MINIMAP_FRAME.height);
            gg.setColor(new Color(RING_RGB));
            ring(gg, r.x, r.y, r.width, r.height);
        } finally {
            gg.dispose();
        }
    }

    /**
     * The minimap's area alone -- its frame, the black interior, the
     * minimap and the viewport ring -- as {@link #paintChrome} and
     * {@link #paintMinimap} draw it within {@link #MINIMAP_FRAME}: the
     * minimap-only repaint of a panel whose other pixels stay (a blink's
     * dot, a jump's ring, a native cue's pixel).
     *
     * @param g The graphics, in canvas pixels.
     * @param m The minimap, or null (the frame and the black interior).
     */
    static void paintMinimapArea(Graphics2D g, MinimapModel m) {
        g.setColor(new Color(FRAME_RGB));
        ring(g, MINIMAP_FRAME.x, MINIMAP_FRAME.y, MINIMAP_FRAME.width,
             MINIMAP_FRAME.height);
        g.setColor(Color.BLACK);
        g.fillRect(MINIMAP.x, MINIMAP.y, MINIMAP.width, MINIMAP.height);
        paintMinimap(g, m);
    }

    /**
     * The season and gold lines: only the glyphs that end by the screen's
     * right edge ({@link #fitting}), and clipped there.
     */
    static void paintStatus(Graphics2D g, ClassicFont font, String season,
                            String gold) {
        final Graphics2D gg = (Graphics2D) g.create();
        try {
            gg.clipRect(PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
            final int right = PANEL_X + PANEL_W;
            ClassicMenuBox.text(gg, font, fitting(font, season, STATUS_X, right),
                                STATUS_X, SEASON_Y, ClassicMenuBox.GAME, false);
            ClassicMenuBox.text(gg, font, fitting(font, gold, STATUS_X, right),
                                STATUS_X, GOLD_Y, ClassicMenuBox.GAME, false);
        } finally {
            gg.dispose();
        }
    }

    /**
     * The start of a plain line whose glyphs all end by {@code right}: the
     * original draws a glyph whole or not at all (Q3, playthrough-1 #2436:
     * the "%" that would start at x 318 leaves x 318-319 empty, though its
     * first column has ink).  A glyph's end is its advance (I: the one
     * case seen does not tell advance from ink).
     *
     * @param font FONTTINY, or null (the line as it is).
     * @param s The line, or null.
     * @param x Where it starts.
     * @param right The first x it must not reach (320).
     * @return The glyphs that fit.
     */
    static String fitting(ClassicFont font, String s, int x, int right) {
        if (font == null || s == null) return s;
        int end = x;
        for (int i = 0; i < s.length(); i++) {
            end += font.charWidth(s.charAt(i));
            if (end > right) return s.substring(0, i);
        }
        return s;
    }

    /**
     * The whole panel in scene mode (or without texts): chrome, minimap,
     * status lines.
     */
    static void paintPanel(Graphics2D g, ClassicFont font, BufferedImage wood,
                           PanelModel p) {
        paintPanel(g, font, wood, null, p);
    }

    /**
     * The whole panel: chrome, minimap, status lines, and -- unless in scene
     * mode -- the unit block and list ({@link #paintUnits}), or in their
     * place the tile mode of the Spielzugende mode ({@link #paintTileMode});
     * last the turn indicator, if any ({@link #paintIndicator}).
     *
     * @param text The pack's texts for the unit lines, or null (then only
     *     the units' plain names are written).
     */
    static void paintPanel(Graphics2D g, ClassicFont font, BufferedImage wood,
                           ClassicText text, PanelModel p) {
        paintChrome(g, wood);
        paintMinimap(g, p.minimap);
        paintStatus(g, font, p.season, p.gold);
        if (!p.scene) {
            if (p.tile != null) {
                paintTileMode(g, font, text, p.tile, p.promptRgb);
            } else {
                paintUnits(g, font, text, p.active, p.list, p.colony);
            }
        }
        paintIndicator(g, p.indicator);
    }

    /**
     * The turn indicator (build spec W5c): {@link #INDICATOR} filled with
     * {@code rgb}, over the wood and anything the unit list drew there.
     *
     * @param rgb The colour, or -1 for none (the wood stays).
     */
    static void paintIndicator(Graphics2D g, int rgb) {
        if (rgb < 0) return;
        g.setColor(new Color(rgb & 0xFFFFFF));
        g.fillRect(INDICATOR.x, INDICATOR.y, INDICATOR.width, INDICATOR.height);
    }

    /**
     * The turn indicator's colour for a player: the measured table for
     * the original's twelve nations (natives, the three European rivals,
     * Holland), else the player's minimap colour ({@link #nationRgb}).
     *
     * @param p The player, or null.
     * @return The RGB, or -1 for no player.
     */
    static int indicatorRgb(Player p) {
        if (p == null) return -1;
        final Integer c = INDICATOR_RGB.get(p.getNationId());
        return (c != null) ? c : nationRgb(p);
    }


    // The unit block

    /** The icon cell of the active unit, and the x of the list's cells. */
    static final int CELL_X = 242, ACTIVE_CELL_Y = 68;

    /** The moves and position lines beside the active unit's icon. */
    static final int INFO_X = 260, MOVES_Y = 70, PLACE_Y = 77;

    /** The active unit's name line and the first detail line under it. */
    static final int NAME_Y = 86, DETAIL_Y = 93;

    /** Line pitch of the detail lines and of a list entry's qualifier lines. */
    static final int LINE_PITCH = 7;

    /** First list sprite: this far below the active block's last line. */
    static final int LIST_GAP = 10;

    /** A list entry: text below its sprite top, orders after the last qualifier, next sprite after the orders. */
    static final int LIST_TEXT_DY = 4, LIST_ORDERS_DY = 6, LIST_NEXT_DY = 8;

    /**
     * The smallest step between two list sprites.  A two-line entry is 18
     * tall (032: 110 -&gt; 128); an entry with only an orders line would be 12
     * by the rule above and overlap the 16-px sprites, so it gets 18 too
     * (assumed: no capture shows one).
     */
    static final int LIST_MIN_STEP = 18;

    /** The unit shadow's offset: the black silhouette, 2 px left. */
    static final int SHADOW_DX = -2;

    /** The order flag: a black ring around a nation-coloured fill. */
    static final int FLAG_W = 7, FLAG_H = 9;

    /** No second flag behind the flag ({@link #paintIcon}). */
    static final int[] NO_MARKER = { 0, 0 };

    /**
     * The cargo marker of a ship with at least one passenger: a second
     * flag 2 px below and right of the flag, whose free edge shows at cell
     * pixels x 2-6, y 8-10 (spec delta W2.3; clip007 #2374/#3072/#4280,
     * missing on the empty ship #3697: 7 px).
     */
    static final int[] CARGO_MARKER = { 2, 2 };

    /**
     * The stack marker of a land unit drawn over others: a second flag
     * 2 px up and left of the flag (I, base spec W2.4: the soldier active
     * aboard at (6,5), landfall #13118, rows 5-6, x 8-12).
     */
    static final int[] STACK_MARKER = { -2, -2 };

    /** LABELS.TXT {@code @INFO} indices of the moves and position labels (:9-10). */
    static final int INFO_MOVES = 0, INFO_PLACE = 1;

    /** LABELS.TXT {@code @MISC} indices: expert (:19), road (:46), veteran (:80). */
    static final int MISC_EXPERT = 4, MISC_ROAD = 31, MISC_VETERAN = 64;

    /** NAMES.TXT {@code @CARGO} row of tools. */
    static final int CARGO_TOOLS = 14;

    /** NAMES.TXT {@code @OTHER_NAMES} row of the forest word. */
    static final int OTHER_NAMES_FOREST = 0;

    /** NAMES.TXT {@code @JOB} row of the free colonist. */
    static final int JOB_FREE_COLONIST = 19;

    /**
     * NAMES.TXT {@code @UNIT} rows of the treasure train (ICONS.SS.016),
     * the artillery (009) and the wagon train (008), 14 wide each, whose
     * flag sits at cell + ({@link #FLAG_DX_TRAIN}, 0): fill x 249-253 for
     * cell 242 in clip005 #13993, #14364, #14481, #14594, clip006 #9345.
     */
    static final int UNIT_TREASURE = 10, UNIT_ARTILLERY = 11, UNIT_WAGON = 12;

    /** The flag's x in the cell of the treasure, the artillery and the wagon train. */
    static final int FLAG_DX_TRAIN = 6;

    /** LABELS.TXT {@code @INFO} index of "Mit:" (:11, the carrier's and the colony's goods). */
    static final int INFO_WITH = 2;

    /**
     * LABELS.TXT {@code @MISC} index of "+ Weiter +" (:120; {@code @MISC}
     * skips the blank line 51), drawn green at x 242 where the next list
     * entry would not fit (clip005 #14852 y 185, clip006 #9345 y 191).
     */
    static final int MISC_MORE = 104;

    /** ICONS.SS frames of the goods: 022 + {@code @CARGO} row coloured, 038 + row grey. */
    static final int ICON_GOODS = 22, ICON_GOODS_GREY = 38;

    /**
     * The "Mit:" rows (build spec W21b): the active carrier's
     * {@link #CARGO_DY} below the block's last line (clip006 #9345 114 -&gt;
     * 126, clip008 #29463 107 -&gt; 119, #32928 100 -&gt; 112); the goods
     * icons from x {@link #GOODS_X}, {@link #GOODS_DY} above the label's
     * glyph top, {@link #GOODS_GAP} px apart (V: clip006 #9345 259, 270
     * ... at y 124 and 160).
     */
    static final int CARGO_DY = 12, GOODS_X = 259, GOODS_DY = -2, GOODS_GAP = 1;

    /**
     * The colony block: its name's glyph top N is {@link #COLONY_DY} below
     * the block's last line (clip005 #14364 114 -&gt; 134, #14852 121 -&gt;
     * 141, #15391 96 -&gt; 116; dago-colony2 #4045 89 -&gt; 109), or
     * {@link #COLONY_AFTER_CARGO_DY} below a carrier's "Mit:" (clip006
     * #9345, clip008 #35590 126 -&gt; 147); the sprite at (242, N -
     * {@link #COLONY_SPRITE_DY}), the name at x {@link #COLONY_NAME_X}
     * over it, "Mit:" {@link #COLONY_WITH_DY} below N; the list
     * {@link #COLONY_LIST_DY} below that "Mit:" (clip005 #14364 160,
     * #14852 167, #15391 142, clip006 #9345 173).
     */
    static final int COLONY_DY = 20, COLONY_AFTER_CARGO_DY = 21, COLONY_SPRITE_DY = 10,
        COLONY_NAME_X = 262, COLONY_WITH_DY = 15, COLONY_LIST_DY = 11;

    /**
     * At most this many goods in a colony's "Mit:" row (I: five is the most
     * any clip shows, in three colonies; Base showed 5 of its 11 goods
     * where a sixth narrow icon would have fit).
     */
    static final int COLONY_GOODS_MAX = 5;

    /**
     * A carrier with goods in the list: its icons from x
     * {@link #LIST_GOODS_X} on the cell's top row, no name line, the
     * orders {@link #LIST_CARGO_ORDERS_DY} below (clip006 #9345 wagon:
     * cotton at 260 and 271, y 173, orders 183; clip005 #15391 galleon).
     */
    static final int LIST_GOODS_X = 260, LIST_CARGO_ORDERS_DY = 10;

    /** The colony sprite's two flag colours, replaced by the nation's fill and dark shade. */
    static final int COLONY_FLAG_RGB = 0x4159A6, COLONY_FLAG_DARK_RGB = 0x34499E;

    /** FreeCol goods-type suffixes by NAMES.TXT {@code @CARGO} row. */
    private static final String[] CARGO_TYPES = {
        "food", "sugar", "tobacco", "cotton", "furs", "lumber", "ore", "silver",
        "horses", "rum", "cigars", "cloth", "coats", "tradeGoods", "tools",
        "muskets"
    };

    /** Colony sprites with the flag recoloured, by sprite and nation (EDT and painters). */
    private static final java.util.Map<BufferedImage, java.util.Map<Long, BufferedImage>>
        COLONY_FLAGS = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /**
     * NAMES.TXT {@code @UNIT} rows of the galleon (ICONS.SS.007) and the
     * frigate (ICONS.SS.015), whose icons have their own layout
     * ({@link #flagRing(int, int, int, int)}, {@link #spriteOffset(int,
     * int)}).
     */
    static final int UNIT_GALLEON = 15, UNIT_FRIGATE = 17;

    /** NAMES.TXT {@code @ORDERS} rows. */
    static final int ORDERS_NONE = 0, ORDERS_SENTRY = 1, ORDERS_TRADE = 2,
        ORDERS_GOTO = 3, ORDERS_FORTIFY = 5, ORDERS_FORTIFIED = 6,
        ORDERS_PLOW = 8, ORDERS_ROAD = 9;

    /** The qualifier line of a list entry. */
    static final int QUAL_NONE = 0, QUAL_VETERAN = 1, QUAL_EXPERT = 2;

    /** Darker nation shades for the order letter, as {@link #NATION_FILL}. */
    private static final int[] NATION_DARK = { 0xAA0000, -1, -1, 0xAA4900 };

    /** FreeCol unit-type suffixes by NAMES.TXT {@code @JOB} row (null: none). */
    private static final String[] JOB_TYPES = {
        "expertFarmer", "masterSugarPlanter", "masterTobaccoPlanter",
        "masterCottonPlanter", "expertFurTrapper", "expertLumberJack",
        "expertOreMiner", "expertSilverMiner", "expertFisherman",
        "masterDistiller", "masterTobacconist", "masterWeaver",
        "masterFurTrader", "masterCarpenter", "masterBlacksmith",
        "masterGunsmith", "firebrandPreacher", "elderStatesman", null,
        "freeColonist", "hardyPioneer", "veteranSoldier", "seasonedScout",
        null, "jesuitMissionary", "indenturedServant", "pettyCriminal",
        "indianConvert"
    };

    /** FreeCol tile-type suffixes by {@code @UNFORESTED} / {@code @FORESTED} row. */
    private static final String[] UNFORESTED = {
        "tundra", "desert", "plains", "prairie", "grassland", "savannah",
        "marsh", "swamp"
    }, FORESTED = {
        "borealForest", "scrubForest", "mixedForest", "broadleafForest",
        "coniferForest", "tropicalForest", "wetlandForest", "rainForest"
    }, OTHER = { "arctic", "ocean", "highSeas", "mountains", "hills" };

    /**
     * NAMES.TXT {@code @UNIT} row of a unit: by role for colonists
     * (settler 0, soldier 1, pioneer 2, missionary 3, dragoon 4, scout 5),
     * by type for the rest (regulars 6..9, treasure 10, artillery 11, wagon
     * train 12, ships 13..18, natives 19..22).  -1 when the original has no
     * such unit.
     *
     * @param type The FreeCol unit-type suffix, e.g. {@code merchantman}.
     * @param role The role suffix, e.g. {@code soldier}, or null.
     */
    static int unitRow(String type, String role) {
        final boolean soldier = "soldier".equals(role) || "infantry".equals(role);
        final boolean dragoon = "dragoon".equals(role) || "cavalry".equals(role);
        switch (type) {
        case "kingsRegular":
            return dragoon ? 8 : 6;
        case "colonialRegular":
            return dragoon ? 7 : 9;
        case "treasureTrain": return 10;
        case "artillery": case "damagedArtillery": return 11;
        case "wagonTrain": return 12;
        case "caravel": return 13;
        case "merchantman": return 14;
        case "galleon": return 15;
        case "privateer": return 16;
        case "frigate": return 17;
        case "manOWar": return 18;
        case "brave":
            if ("nativeDragoon".equals(role)) return 22;
            if ("mountedBrave".equals(role)) return 21;
            if ("armedBrave".equals(role)) return 20;
            return 19;
        default:
            break;
        }
        if (soldier) return 1;
        if (dragoon) return 4;
        if ("pioneer".equals(role)) return 2;
        if ("missionary".equals(role)) return 3;
        if ("scout".equals(role)) return 5;
        return 0;
    }

    /**
     * NAMES.TXT {@code @UNIT} row of a live unit, as its panel block has it
     * ({@link #unitRow(String, String)}).
     *
     * @param u The unit.
     * @return The row, or -1.
     */
    static int unitRow(Unit u) {
        if (u == null || u.getType() == null) return -1;
        final String role = (u.getRole() == null) ? null
            : u.getRole().getRoleSuffix();
        return unitRow(Role.getRoleIdSuffix(u.getType().getId()), role);
    }

    /** NAMES.TXT {@code @CARGO} row of a FreeCol goods type, or -1 (bells, hammers ...). */
    static int cargoRow(String goodsTypeId) {
        if (goodsTypeId == null) return -1;
        final String id = Role.getRoleIdSuffix(goodsTypeId);
        for (int i = 0; i < CARGO_TYPES.length; i++) {
            if (CARGO_TYPES[i].equals(id)) return i;
        }
        return -1;
    }

    /** A goods icon with its picture from {@code icons}, if any. */
    static GoodsIcon goodsIcon(int row, boolean full, IntFunction<BufferedImage> icons) {
        final int frame = (full ? ICON_GOODS : ICON_GOODS_GREY) + row;
        return new GoodsIcon(row, full, (icons == null) ? null : icons.apply(frame));
    }

    /**
     * A colony sprite with its flag in the owner's colours: the sprite's
     * 11 pixels {@link #COLONY_FLAG_RGB} take the nation's fill, its 4
     * pixels {@link #COLONY_FLAG_DARK_RGB} the dark shade (each of
     * ICONS.SS 000-003 has exactly these; V: the Dutch flag orange on the
     * panel, clip005 #14364/#14852, and on the map, clip008 #14868,
     * clip006 #9345).  France keeps the sprite's blue (V: Quebec and
     * Montreal, clip006 #9345); England and Spain like Holland (I).
     * Cached per sprite and nation.
     *
     * @param sprite The colony's sprite, or null.
     * @param owner Its owner, or null (unchanged).
     * @return The sprite to draw.
     */
    static BufferedImage colonyFlag(BufferedImage sprite, Player owner) {
        if (sprite == null || owner == null) return sprite;
        final int n = Arrays.asList(ClassicNewWorldScreens.NATION_IDS)
            .indexOf(owner.getNationId());
        if (n == 1) return sprite;   // France: the sprite's own blue
        final int fill = nationRgb(owner), dark = nationDark(owner);
        final java.util.Map<Long, BufferedImage> byNation
            = COLONY_FLAGS.computeIfAbsent(sprite, k -> new java.util.HashMap<>());
        final long key = ((long) (fill & 0xFFFFFF) << 24) | (dark & 0xFFFFFF);
        synchronized (byNation) {
            return byNation.computeIfAbsent(key, k -> recolour(sprite, fill, dark));
        }
    }

    /** {@code sprite} with the flag colours replaced (a copy). */
    static BufferedImage recolour(BufferedImage sprite, int fill, int dark) {
        final BufferedImage out = new BufferedImage(sprite.getWidth(), sprite.getHeight(),
                                                    BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < sprite.getHeight(); y++) {
            for (int x = 0; x < sprite.getWidth(); x++) {
                final int p = sprite.getRGB(x, y);
                final int c = p & 0xFFFFFF;
                final int a = p & 0xFF000000;
                out.setRGB(x, y, (a == 0) ? p
                    : (c == COLONY_FLAG_RGB) ? (a | (fill & 0xFFFFFF))
                    : (c == COLONY_FLAG_DARK_RGB) ? (a | (dark & 0xFFFFFF)) : p);
            }
        }
        return out;
    }

    /** NAMES.TXT {@code @JOB} row of a colonist type, or -1. */
    static int jobRow(String type) {
        for (int i = 0; i < JOB_TYPES.length; i++) {
            if (type.equals(JOB_TYPES[i])) return i;
        }
        return -1;
    }

    /**
     * The list qualifier of a unit: the veteran word (LABELS misc 64) for
     * a veteran in a military role (032's soldier, 000's dragoon), the expert word
     * (misc 4) for another unit used in its own skill (000's hardy pioneer;
     * scouts and missionaries assumed alike).
     */
    static int qualifier(String type, String role) {
        if (role == null) return QUAL_NONE;
        final boolean military = "soldier".equals(role) || "dragoon".equals(role);
        if (military && ("veteranSoldier".equals(type)
                         || "colonialRegular".equals(type))) return QUAL_VETERAN;
        if (("hardyPioneer".equals(type) && "pioneer".equals(role))
            || ("seasonedScout".equals(type) && "scout".equals(role))
            || ("jesuitMissionary".equals(type) && "missionary".equals(role))) {
            return QUAL_EXPERT;
        }
        return QUAL_NONE;
    }

    /** NAMES.TXT {@code @ORDERS} row of a unit's current orders. */
    static int ordersRow(Unit u) {
        final Unit.UnitState s = u.getState();
        if (s == Unit.UnitState.SENTRY) return ORDERS_SENTRY;
        if (s == Unit.UnitState.FORTIFYING) return ORDERS_FORTIFY;
        if (s == Unit.UnitState.FORTIFIED) return ORDERS_FORTIFIED;
        if (s == Unit.UnitState.IMPROVING) {
            final TileImprovement ti = u.getWorkImprovement();
            return (ti != null && ti.isRoad()) ? ORDERS_ROAD : ORDERS_PLOW;
        }
        if (u.getTradeRoute() != null) return ORDERS_TRADE;
        if (u.getDestination() != null) return ORDERS_GOTO;
        return ORDERS_NONE;
    }

    /**
     * The terrain words of a FreeCol tile type: NAMES {@code @UNFORESTED}
     * name, or the {@code @FORESTED} prefix plus the forest word of
     * {@code @OTHER_NAMES} (prefix + " " + forest word, 000), or an
     * {@code @OTHER} name (high seas: 032).  A lake reads as ocean and the
     * great river as {@code @OTHER_NAMES} 2 (the original has neither).
     *
     * @return The words, or null when unknown or the pack lacks them.
     */
    static String terrainName(ClassicText t, String tileTypeId) {
        if (t == null || tileTypeId == null) return null;
        final String id = Role.getRoleIdSuffix(tileTypeId);
        for (int i = 0; i < UNFORESTED.length; i++) {
            if (UNFORESTED[i].equals(id)) return cell(t, "UNFORESTED", i);
            if (FORESTED[i].equals(id)) {
                final String p = cell(t, "FORESTED", i);
                final String w = cell(t, "OTHER_NAMES", OTHER_NAMES_FOREST);
                return (p == null || w == null) ? null : p + " " + w;
            }
        }
        for (int i = 0; i < OTHER.length; i++) {
            if (OTHER[i].equals(id)) return cell(t, "OTHER", i);
        }
        if ("lake".equals(id)) return cell(t, "OTHER", 1);
        if ("greatRiver".equals(id)) return cell(t, "OTHER_NAMES", 2);
        return null;
    }

    /** Column 0 of a NAMES.TXT row, or null. */
    private static String cell(ClassicText t, String section, int row) {
        final List<String[]> rows = t.names(section);
        return (row < 0 || row >= rows.size()) ? null : rows.get(row)[0];
    }

    /** Column {@code col} of a NAMES.TXT row, or null. */
    private static String cell(ClassicText t, String section, int row, int col) {
        final List<String[]> rows = t.names(section);
        if (row < 0 || row >= rows.size() || rows.get(row).length <= col) return null;
        return rows.get(row)[col];
    }

    /**
     * Moves as written after the moves label: whole moves, a fraction in thirds after
     * a space (the format of fractions is assumed: no capture shows one).
     */
    static String movesText(int thirds) {
        final int n = thirds / 3, r = thirds % 3;
        if (r == 0) return Integer.toString(n);
        return (n == 0) ? r + "/3" : n + " " + r + "/3";
    }

    /** One line of panel text. */
    static final class TextLine {
        final String text;
        final int x, y;
        final boolean gold;

        TextLine(String text, int x, int y, boolean gold) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.gold = gold;
        }

        @Override
        public String toString() {
            return "(" + this.x + "," + this.y + (this.gold ? ",gold) " : ") ")
                + this.text;
        }
    }

    /**
     * The active unit's lines (032, 052, 000, 007; build spec W21b): moves
     * label + N at (260,70), position label + ' (x, y)' at (260,77), the
     * name at (242,86) -- nationality and {@code @UNIT} name, green -- then
     * 7 px apart from y 93, gold: a treasure's '(' + gold label + ' ' +
     * amount + ')' (clip005 #14594); the qualifier word when the unit has
     * one and no tools (clip007 #3107/#6643 "Experte", #1449 "Erfahren"),
     * else the colonist's skill ({@code @JOB}; the hardy pioneer with tools
     * "Pionier", clip006 #5351); the tools as '(' + number and, on its own
     * line, the tools word + ')' (007); the orders, or for a goto to a
     * colony that colony's name (clip005 #14481 "Fur Town"); then green
     * the terrain in parentheses and one line each for a river, a road,
     * plowing and a resource, in the tile mode's order (clip005 #14481
     * river and road, clip006 #5351 road and plowed, clip007 #1449
     * resource).
     */
    static List<TextLine> activeLines(ClassicText t, UnitFacts f) {
        final List<TextLine> out = new ArrayList<>();
        if (t == null) {
            out.add(new TextLine(f.plainName, CELL_X, NAME_Y, false));
            return out;
        }
        final String movesL = t.label("INFO", INFO_MOVES);
        final String placeL = t.label("INFO", INFO_PLACE);
        if (movesL != null) {
            out.add(new TextLine(movesL.trim() + " " + movesText(f.moves),
                                 INFO_X, MOVES_Y, false));
        }
        if (placeL != null) {
            out.add(new TextLine(placeL.trim() + " (" + f.x + ", " + f.y + ")",
                                 INFO_X, PLACE_Y, false));
        }
        out.add(new TextLine(name(t, f), CELL_X, NAME_Y, false));
        int y = DETAIL_Y;
        final String gold = treasureText(t, f);
        if (gold != null) {
            out.add(new TextLine("(" + gold + ")", CELL_X, y, true));
            y += LINE_PITCH;
        }
        final String second = (f.qualifier != QUAL_NONE && f.tools < 0)
            ? qualifierWord(t, f.qualifier)
            : (f.jobRow < 0) ? null : cell(t, "JOB", f.jobRow);
        if (second != null) {
            out.add(new TextLine(second, CELL_X, y, true));
            y += LINE_PITCH;
        }
        final String tools = cell(t, "CARGO", CARGO_TOOLS);
        if (f.tools >= 0 && tools != null) {
            out.add(new TextLine("(" + f.tools, CELL_X, y, true));
            y += LINE_PITCH;
            out.add(new TextLine(tools + ")", CELL_X, y, true));
            y += LINE_PITCH;
        }
        final String orders = ordersText(t, f);
        if (orders != null) {
            out.add(new TextLine(orders, CELL_X, y, true));
            y += LINE_PITCH;
        }
        final List<String> where = new ArrayList<>();
        where.add(terrainName(t, f.terrainId));
        where.addAll(tileExtras(t, f.river, f.road, f.plowed, f.resourceRow));
        for (String w : where) {
            if (w == null) continue;
            out.add(new TextLine("(" + w.trim() + ")", CELL_X, y, false));
            y += LINE_PITCH;
        }
        return out;
    }

    /** The veteran (misc 64) or expert (misc 4) word, or null. */
    private static String qualifierWord(ClassicText t, int qualifier) {
        final String w = (qualifier == QUAL_NONE) ? null
            : t.misc(qualifier == QUAL_VETERAN ? MISC_VETERAN : MISC_EXPERT);
        return (w == null) ? null : w.trim();
    }

    /**
     * A treasure's gold: the gold label ({@code @CTITLE} 1) + ' ' +
     * amount, "Gold: 10000" (clip005 #13993 in the list, #14594 in
     * parentheses in the block; I: which label the original uses, the
     * pixels are the same), or null for any other unit.
     */
    static String treasureText(ClassicText t, UnitFacts f) {
        if (f.treasure < 0 || t == null) return null;
        final String g = t.label("CTITLE", CTITLE_GOLD);
        return (g == null) ? Integer.toString(f.treasure) : g.trim() + " " + f.treasure;
    }

    /**
     * The orders line: the destination colony's name for a goto to a
     * colony (clip005 #14481 active, #14827 "Schmied / Base" in the list),
     * {@code @ORDERS} "Ziel" and the home port for a goto to Europe ("Ziel
     * Amsterdam", R2, landfall #23375), else the {@code @ORDERS} name.
     */
    static String ordersText(ClassicText t, UnitFacts f) {
        if (f.destination != null && f.ordersRow == ORDERS_GOTO) return f.destination;
        if (f.homePort && f.ordersRow == ORDERS_GOTO) {
            final String ziel = cell(t, "ORDERS", ORDERS_GOTO);
            final String port = ClassicDestinations.homePort(t, f.nation);
            if (ziel != null && port != null) return ziel.trim() + " " + port;
        }
        return cell(t, "ORDERS", f.ordersRow);
    }

    /**
     * The words of a tile's extras in the order the panel lists them: the
     * river ({@code @OTHER_NAMES} 3 minor, 2 major), the road
     * ({@code @MISC} 31), plowing ({@code @MISC} 82) and the resource
     * ({@code @RESOURCE}) -- the tile mode's and the active block's order
     * (W21 item 2; clip005 #14481, clip006 #5351, clip008 #4009).
     *
     * @return The words without parentheses; an entry the pack lacks is null.
     */
    static List<String> tileExtras(ClassicText t, int river, boolean road,
                                   boolean plowed, int resourceRow) {
        final List<String> extra = new ArrayList<>();
        if (river > 0) {
            extra.add(cell(t, "OTHER_NAMES", (river > 1)
                ? OTHER_NAMES_MAJOR_RIVER : OTHER_NAMES_MINOR_RIVER));
        }
        if (road) extra.add(t.misc(MISC_ROAD));
        if (plowed) extra.add(t.misc(MISC_PLOWED));
        if (resourceRow >= 0) extra.add(cell(t, "RESOURCE", resourceRow));
        return extra;
    }

    /** Nationality and {@code @UNIT} name, or the plain name. */
    static String name(ClassicText t, UnitFacts f) {
        final String unit = (f.unitRow < 0) ? null : cell(t, "UNIT", f.unitRow);
        if (unit == null) return f.plainName;
        final String nat = (f.nation < 0) ? null : cell(t, "NATIONALITY", f.nation);
        return (nat == null) ? unit : nat + " " + unit;
    }

    /** NAMES.TXT {@code @UNIT} rows of the ships (caravel .. man-of-war). */
    static final int FIRST_SHIP_ROW = 13, LAST_SHIP_ROW = 18;

    /**
     * A list entry's lines, all gold at x = 260 from {@code spriteY + 4}
     * (032, 000, 007; build spec W21b): a carrier with goods only its
     * orders, {@link #LIST_CARGO_ORDERS_DY} below the cell's top, under
     * its goods icons ({@link #paintListCargo}; clip006 #9345, clip005
     * #15391); a treasure its gold ("Gold: 10000", clip005 #13993); a
     * ship, the artillery or a wagon train its {@code @UNIT} name
     * (clip007 #3107 "Handelsschiff", clip005 #14364 "Artillerie", #14481
     * "Wagenzug"); else the qualifier -- the veteran word, the expert word
     * plus the tool count for an expert pioneer -- else a bare tool count
     * without the skill (clip007 #5093, #6884: "100" / "Werkzeuge"), else
     * the skill of a colonist without a role or with a skill that is not
     * the free colonist's (clip005 #14827 "Schmied", clip006 #5135
     * "Holzfäller" for a dragoon) -- with the tools word on its own line
     * after a count, 7 px apart; then the orders, or a goto's colony
     * (clip005 #14827 "Base"), 6 px below the last of them (or at the
     * first line when there is none).
     */
    static List<TextLine> listLines(ClassicText t, UnitFacts f, int spriteY) {
        final List<TextLine> out = new ArrayList<>();
        final int y0 = spriteY + LIST_TEXT_DY;
        if (t == null) {
            out.add(new TextLine(f.plainName, INFO_X, y0, true));
            return out;
        }
        if (!f.cargo.isEmpty()) {
            final String orders = ordersText(t, f);
            if (orders != null) {
                out.add(new TextLine(orders, INFO_X, spriteY + LIST_CARGO_ORDERS_DY,
                                     true));
            }
            return out;
        }
        final List<String> q = new ArrayList<>();
        final String toolsWord = cell(t, "CARGO", CARGO_TOOLS);
        final String count = (f.tools >= 0) ? Integer.toString(f.tools) : null;
        if (f.unitRow == UNIT_TREASURE) {
            final String gold = treasureText(t, f);
            if (gold != null) q.add(gold);
        } else if (f.unitRow >= UNIT_ARTILLERY && f.unitRow <= LAST_SHIP_ROW) {
            // The artillery, a wagon train, a ship: the type's name.
            final String name = cell(t, "UNIT", f.unitRow);
            if (name != null) q.add(name);
        } else if (f.qualifier != QUAL_NONE) {
            final String w = qualifierWord(t, f.qualifier);
            if (w != null) q.add((count == null) ? w : w + " " + count);
            else if (count != null) q.add(count);
        } else {
            final boolean showJob = count == null && f.jobRow >= 0
                && (f.roleless || f.jobRow != JOB_FREE_COLONIST);
            final String job = showJob ? cell(t, "JOB", f.jobRow) : null;
            if (job != null) q.add(job);
            if (count != null) q.add(count);
        }
        if (count != null && toolsWord != null) q.add(toolsWord);
        int y = y0;
        for (int i = 0; i < q.size(); i++) {
            out.add(new TextLine(q.get(i), INFO_X, y, true));
            if (i < q.size() - 1) y += LINE_PITCH;
        }
        final String orders = ordersText(t, f);
        if (orders != null) {
            out.add(new TextLine(orders, INFO_X, q.isEmpty() ? y0 : y + LIST_ORDERS_DY,
                                 true));
        }
        return out;
    }

    /** The next list sprite after an entry whose lines are {@code lines}. */
    static int nextListY(int spriteY, List<TextLine> lines) {
        int last = spriteY + LIST_TEXT_DY;
        for (TextLine l : lines) last = Math.max(last, l.y);
        return Math.max(last + LIST_NEXT_DY, spriteY + LIST_MIN_STEP);
    }

    /**
     * Where a sprite of width {@code w} sits in its 16-px cell: +3 for widths
     * 6, 7 and 13, +2 for 8 and 14 (measured on the panel and the map: ICONS
     * 005, 006, 073, 075, 097, 100, 101 and 016, 074, 076, 095, 102, 104,
     * 109); other widths centred (unmeasured).
     */
    static int spriteOffset(int w) {
        switch (w) {
        case 6: case 7: case 13:
            return 3;
        case 8: case 14:
            return 2;
        default:
            return Math.max(0, (16 - w) / 2);
        }
    }

    /**
     * Where a unit's sprite sits in its cell: {@link #spriteOffset(int)},
     * except the frigate (ICONS.SS.015, 13 wide) at +2 (clip006 Europe,
     * 0 px; build spec W21 item 7).
     *
     * @param w The sprite's width.
     * @param unitRow The unit's NAMES.TXT {@code @UNIT} row, or -1.
     * @return The x offset in the cell.
     */
    static int spriteOffset(int w, int unitRow) {
        return (unitRow == UNIT_FRIGATE) ? 2 : spriteOffset(w);
    }

    /**
     * The order flag's ring: at the cell's top-left for ships and mounted
     * units (sprites 13 or more wide: 032's ship, 000's dragoon), else at
     * the sprite's lower right, {@code (x + w - 2, y + 7)} (032's soldier and
     * pioneer, 000's settler).
     */
    static Rectangle flagRing(int cellX, int cellY, int spriteW) {
        if (spriteW >= 13) return new Rectangle(cellX, cellY, FLAG_W, FLAG_H);
        final int sx = cellX + spriteOffset(spriteW);
        return new Rectangle(sx + spriteW - 2, cellY + 7, FLAG_W, FLAG_H);
    }

    /**
     * The order flag's ring of a unit: per sprite, not per width (build
     * spec W21 item 7, clip006 deep 4.2): the galleon (ICONS.SS.007, 14
     * wide) and the frigate (015, 13 wide) at cell + (9,0) (map #4471,
     * panel #9200, Europe; clip005 #20440: fill x 218-222 for cell 208),
     * the treasure train, the artillery and the wagon train (016, 009,
     * 008, 14 wide) at cell + (6,0) ({@link #FLAG_DX_TRAIN}, build spec
     * W21b), the merchantman (006) and the rest by {@link #flagRing(int,
     * int, int)}.
     *
     * @param cellX The cell's left edge.
     * @param cellY The cell's top edge.
     * @param spriteW The sprite's width.
     * @param unitRow The unit's NAMES.TXT {@code @UNIT} row, or -1.
     * @return The ring.
     */
    static Rectangle flagRing(int cellX, int cellY, int spriteW, int unitRow) {
        if (unitRow == UNIT_GALLEON || unitRow == UNIT_FRIGATE) {
            return new Rectangle(cellX + 9, cellY, FLAG_W, FLAG_H);
        }
        if (unitRow == UNIT_TREASURE || unitRow == UNIT_ARTILLERY
            || unitRow == UNIT_WAGON) {
            return new Rectangle(cellX + FLAG_DX_TRAIN, cellY, FLAG_W, FLAG_H);
        }
        return flagRing(cellX, cellY, spriteW);
    }

    /**
     * The flag letter's ink (build spec W21 item 7, clip006 deep 4.2): the
     * darker nation shade only for the units the cycle skips, Befestigt
     * (F, fortified) and Wache (S, sentry); black for '-', G, R, P and for
     * F while still fortifying (#4555 black -&gt; #4556 dark).
     *
     * @param ordersRow The unit's {@code @ORDERS} row ({@link #ordersRow}).
     * @param dark The nation's darker shade.
     * @return The ink RGB.
     */
    static int letterInk(int ordersRow, int dark) {
        return (ordersRow == ORDERS_FORTIFIED || ordersRow == ORDERS_SENTRY)
            ? (dark & 0xFFFFFF) : 0x000000;
    }

    /**
     * One unit icon (proved order: 032, 000; clip006 deep 4.2): the black
     * silhouette shifted {@link #SHADOW_DX}, then the flag -- black ring,
     * nation fill -- then the sprite, then the {@code @ORDERS} letter in
     * FONTTINY at ring + (2,2), over the sprite where they meet
     * ({@link #letterInk}).
     */
    static void paintIcon(Graphics2D g, ClassicFont font, String letter,
                          UnitFacts f, int cellX, int cellY) {
        paintIcon(g, font, letter, f.sprite, f.fill,
                  letterInk(f.ordersRow, f.dark), f.unitRow, cellX, cellY,
                  NO_MARKER);
    }

    /**
     * One unit icon as {@link #paintIcon(Graphics2D, ClassicFont, String,
     * UnitFacts, int, int)}, optionally with a second flag behind the flag
     * ({@link #CARGO_MARKER}, {@link #STACK_MARKER}): it is drawn after the
     * shadow and before the flag, ring and fill without a letter, so only
     * the edge the flag and the sprite leave free shows.  The map draws
     * icons through here, 1:1 in native pixels (build spec W2, W21a), so a
     * map unit looks exactly as its panel block.
     *
     * @param g The graphics, in native pixels.
     * @param font FONTTINY, or null (no letter).
     * @param letter The flag letter.
     * @param sp The sprite, at most 16x16, or null.
     * @param fill The flag fill.
     * @param ink The letter's ink ({@link #letterInk}).
     * @param unitRow The unit's NAMES.TXT {@code @UNIT} row, or -1: the
     *     sprite's place and the flag's side ({@link #spriteOffset(int,
     *     int)}, {@link #flagRing(int, int, int, int)}).
     * @param cellX The cell's left edge.
     * @param cellY The cell's top edge.
     * @param marker The second flag's offset from the flag {dx, dy}, or
     *     {@link #NO_MARKER}.
     */
    static void paintIcon(Graphics2D g, ClassicFont font, String letter,
                          BufferedImage sp, int fill, int ink, int unitRow,
                          int cellX, int cellY, int[] marker) {
        final int w = (sp == null) ? 16 : sp.getWidth();
        final int sx = cellX + spriteOffset(w, unitRow);
        if (sp != null) {
            g.setColor(Color.BLACK);
            for (int y = 0; y < sp.getHeight(); y++) {
                for (int x = 0; x < sp.getWidth(); x++) {
                    if ((sp.getRGB(x, y) >>> 24) == 0) continue;
                    g.fillRect(sx + x + SHADOW_DX, cellY + y, 1, 1);
                }
            }
        }
        final Rectangle r = flagRing(cellX, cellY, w, unitRow);
        final Color fillColour = new Color(fill & 0xFFFFFF);
        if (marker != null && (marker[0] != 0 || marker[1] != 0)) {
            g.setColor(Color.BLACK);
            ring(g, r.x + marker[0], r.y + marker[1], r.width, r.height);
            g.setColor(fillColour);
            g.fillRect(r.x + marker[0] + 1, r.y + marker[1] + 1,
                       r.width - 2, r.height - 2);
        }
        g.setColor(Color.BLACK);
        ring(g, r.x, r.y, r.width, r.height);
        g.setColor(fillColour);
        g.fillRect(r.x + 1, r.y + 1, r.width - 2, r.height - 2);
        if (sp != null) g.drawImage(sp, sx, cellY, null);
        // The letter last, over the sprite (the galleon's pixel (9,4) shows
        // the black '-': clip006 #4471 map, #8120 Europe).
        if (font != null && letter != null && !letter.isEmpty()) {
            font.draw(g, letter, r.x + 2, r.y + 2,
                      ClassicFont.colours(ink & 0xFFFFFF));
        }
    }

    /**
     * Where a settlement sprite sits in its 16-px cell: centred, and a
     * wider one overhangs both sides (the landfall clip, #13302: the
     * 21-px village ICONS 011 at cell x - 2).
     */
    static int settlementOffset(int w) {
        return (16 - w) / 2;
    }

    /** Draw panel text lines (green or gold). */
    private static void drawLines(Graphics2D g, ClassicFont font,
                                  List<TextLine> lines) {
        for (TextLine l : lines) {
            if (l.text == null || l.text.isEmpty()) continue;
            if (font != null) {
                font.draw(g, l.text, l.x, l.y, l.gold
                          ? ClassicMenuBox.GAME.highlightColours
                          : ClassicMenuBox.GAME.inkColours);
            } else {
                ClassicMenuBox.text(g, null, l.gold ? "{" + l.text + "}" : l.text,
                                    l.x, l.y, ClassicMenuBox.GAME, l.gold);
            }
        }
    }

    /** The {@code @ORDERS} flag letter of a unit, '-' without texts. */
    private static String letter(ClassicText t, UnitFacts f) {
        return orderLetter(t, f.ordersRow);
    }

    /**
     * The flag letter of an {@code @ORDERS} row.
     *
     * @param t The pack's texts, or null.
     * @param ordersRow The row ({@link #ordersRow}).
     * @return The letter, '-' without texts.
     */
    static String orderLetter(ClassicText t, int ordersRow) {
        final String l = (t == null) ? null : cell(t, "ORDERS", ordersRow, 1);
        return (l == null || l.isEmpty()) ? "-" : l.substring(0, 1);
    }

    /**
     * Where the parts under a block's lines go (build spec W21b): the
     * active carrier's "Mit:" line, the colony block, a native settlement's
     * entry (tile mode), the list entries and "+ Weiter +".  One function
     * for the painters, the tile mode's word and the tests.
     */
    static final class BlockLayout {

        /** The carrier's "Mit:" glyph top, or -1. */
        final int cargoY;

        /** The colony's name glyph top N, or -1 (sprite N - 10, "Mit:" N + 15). */
        final int colonyY;

        /** The native settlement's cell top, or -1. */
        final int settlementY;

        /** Each list entry's cell top, -1 where it is not drawn. */
        final int[] entryY;

        /** "+ Weiter +"'s glyph top, or -1 when every entry is drawn. */
        final int moreY;

        /** Where an entry after the last one drawn would go, or -1 with none. */
        final int nextY;

        /** The lowest text line above the list (the Spielzugende word's base without one). */
        final int lastLineY;

        BlockLayout(int cargoY, int colonyY, int settlementY, int[] entryY,
                    int moreY, int nextY, int lastLineY) {
            this.cargoY = cargoY;
            this.colonyY = colonyY;
            this.settlementY = settlementY;
            this.entryY = entryY;
            this.moreY = moreY;
            this.nextY = nextY;
            this.lastLineY = lastLineY;
        }
    }

    /**
     * The block under the lines (build spec W21b): a carrier's "Mit:"
     * {@link #CARGO_DY} below the last line; the colony's name N
     * {@link #COLONY_DY} below the last line or
     * {@link #COLONY_AFTER_CARGO_DY} below the carrier's "Mit:"; the list
     * {@link #COLONY_LIST_DY} below the colony's "Mit:" (after a carrier's
     * "Mit:" without a colony the same, I), else {@link #LIST_GAP} below
     * the last line (032: 100 -&gt; 110; 000: 114 -&gt; 124); a native
     * settlement's entry first, {@link #SETTLEMENT_STEP} tall.  An entry
     * whose cell would cross the screen's bottom is not drawn, and
     * "+ Weiter +" stands where the first of them would have gone (clip005
     * #14852 185, clip006 #5135 185, #5517 185, #9345 191, clip005 #15391
     * 196, clipped), only when an entry remains (I: clip005 #14364 has
     * none at 196).
     *
     * @param t The texts (for the entries' lines), or null.
     * @param last The lowest line drawn above.
     * @param cargo Whether a carrier's goods line follows.
     * @param colony Whether a colony block follows.
     * @param settlement Whether a native settlement's entry comes first.
     * @param list The entries.
     * @return The layout.
     */
    static BlockLayout blockLayout(ClassicText t, int last, boolean cargo,
                                   boolean colony, boolean settlement,
                                   List<UnitFacts> list) {
        final int cargoY = cargo ? last + CARGO_DY : -1;
        int colonyY = -1, lastLine = (cargoY >= 0) ? cargoY : last, y;
        if (colony) {
            colonyY = (cargoY >= 0) ? cargoY + COLONY_AFTER_CARGO_DY : last + COLONY_DY;
            lastLine = colonyY + COLONY_WITH_DY;
            y = lastLine + COLONY_LIST_DY;
        } else if (cargoY >= 0) {
            y = cargoY + COLONY_LIST_DY;
        } else {
            y = last + LIST_GAP;
        }
        int settlementY = -1, next = -1, more = -1;
        if (settlement) {
            settlementY = y;
            y += SETTLEMENT_STEP;
            next = y;
        }
        final int[] entry = new int[list.size()];
        for (int k = 0; k < list.size(); k++) {
            if (y + 16 > PANEL_Y + PANEL_H) {
                entry[k] = -1;
                if (more < 0) more = y;
                continue;
            }
            entry[k] = y;
            y = nextListY(y, listLines(t, list.get(k), y));
            next = y;
        }
        return new BlockLayout(cargoY, colonyY, settlementY, entry, more, next,
                               lastLine);
    }

    /**
     * The active-unit block and the unit list, clipped to the panel,
     * without a colony block.
     */
    static void paintUnits(Graphics2D g, ClassicFont font, ClassicText t,
                           UnitFacts active, List<UnitFacts> list) {
        paintUnits(g, font, t, active, list, null);
    }

    /**
     * The active-unit block, a carrier's goods, the colony on its tile and
     * the unit list, clipped to the panel, as {@link #blockLayout} places
     * them.
     *
     * @param colony The colony on the active unit's tile, or null.
     */
    static void paintUnits(Graphics2D g, ClassicFont font, ClassicText t,
                           UnitFacts active, List<UnitFacts> list,
                           ColonyFacts colony) {
        if (active == null) return;
        final Graphics2D gg = (Graphics2D) g.create();
        try {
            gg.clipRect(PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
            paintIcon(gg, font, letter(t, active), active, CELL_X, ACTIVE_CELL_Y);
            final List<TextLine> lines = activeLines(t, active);
            drawLines(gg, font, lines);
            int last = NAME_Y;
            for (TextLine l : lines) last = Math.max(last, l.y);
            final BlockLayout lay = blockLayout(t, last, !active.cargo.isEmpty(),
                                                colony != null, false, list);
            if (lay.cargoY >= 0) paintWith(gg, font, t, active.cargo, lay.cargoY);
            if (lay.colonyY >= 0) paintColony(gg, font, t, colony, lay.colonyY);
            paintEntries(gg, font, t, list, lay);
        } finally {
            gg.dispose();
        }
    }

    /** The list entries and "+ Weiter +" where {@code lay} puts them. */
    private static void paintEntries(Graphics2D g, ClassicFont font, ClassicText t,
                                     List<UnitFacts> list, BlockLayout lay) {
        for (int k = 0; k < list.size(); k++) {
            final int y = lay.entryY[k];
            if (y < 0) continue;
            final UnitFacts f = list.get(k);
            paintIcon(g, font, letter(t, f), f, CELL_X, y);
            paintGoods(g, f.cargo, LIST_GOODS_X, y);
            drawLines(g, font, listLines(t, f, y));
        }
        final String more = (t == null || lay.moreY < 0) ? null : t.misc(MISC_MORE);
        if (more != null) {
            drawLines(g, font, List.of(new TextLine(more.trim(), CELL_X, lay.moreY, false)));
        }
    }

    /**
     * A "Mit:" line: the label ({@code @INFO} 2, green) at (242, y) and
     * the goods icons from ({@link #GOODS_X}, y + {@link #GOODS_DY}).
     */
    private static void paintWith(Graphics2D g, ClassicFont font, ClassicText t,
                                  List<GoodsIcon> goods, int y) {
        final String with = (t == null) ? null : t.label("INFO", INFO_WITH);
        if (with != null) {
            drawLines(g, font, List.of(new TextLine(with.trim(), CELL_X, y, false)));
        }
        paintGoods(g, goods, GOODS_X, y + GOODS_DY);
    }

    /**
     * Goods icons in a row from {@code (x, y)}, each {@link #GOODS_GAP}
     * after the last one's right edge; the screen's edge cuts the last
     * one (clip005 #15391: the galleon's fifth hold, furs at x 312, shows
     * its first 8 columns).
     */
    static void paintGoods(Graphics2D g, List<GoodsIcon> goods, int x, int y) {
        int gx = x;
        for (GoodsIcon i : goods) {
            if (i.icon == null) continue;
            if (gx >= PANEL_X + PANEL_W) break;
            g.drawImage(i.icon, gx, y, null);
            gx += i.icon.getWidth() + GOODS_GAP;
        }
    }

    /**
     * The colony block (build spec W21b): the sprite at (242, N - 10),
     * then the name green at (262, N) over it, then "Mit:" and its goods
     * at N + 15.
     *
     * @param n The name's glyph top.
     */
    static void paintColony(Graphics2D g, ClassicFont font, ClassicText t,
                            ColonyFacts c, int n) {
        if (c.sprite != null) g.drawImage(c.sprite, CELL_X, n - COLONY_SPRITE_DY, null);
        drawLines(g, font, List.of(new TextLine(c.name, COLONY_NAME_X, n, false)));
        paintWith(g, font, t, c.goods, n + COLONY_WITH_DY);
    }


    // The tile mode and the Spielzugende word (build spec W17)

    /**
     * The tile mode's first line ("Ort: (x, y) n") at the active cell's
     * top, x 242; the lines follow {@link #LINE_PITCH} apart (landing-slow
     * #2306/#4193/#6182, clip004 #1974/#3798, clip005 #18186).
     */
    static final int TILE_X = CELL_X, TILE_Y = ACTIVE_CELL_Y;

    /** The number after the position until its meaning is known. */
    static final int REGION_UNKNOWN = 1;

    /** LABELS.TXT {@code @MISC} rows: the Spielzugende word (:17), "Land" (:33), plowed (:98). */
    static final int MISC_END_TURN = 2, MISC_LAND = 18, MISC_PLOWED = 82;

    /** NAMES.TXT {@code @OTHER_NAMES} rows of the major and the minor river. */
    static final int OTHER_NAMES_MAJOR_RIVER = 2, OTHER_NAMES_MINOR_RIVER = 3;

    /** NAMES.TXT {@code @LEVELS} row of a capital, and its column of the singular. */
    static final int LEVEL_CAPITAL = 4, LEVEL_SINGULAR = 1;

    /** NAMES.TXT {@code @TRIBES} column of the tech level. */
    static final int TRIBE_LEVEL = 3;

    /**
     * A settlement entry: its text at x 262, glyph tops at the cell + 4
     * and + 10 like a unit's, green (landing-slow #4193); the entry is 23
     * tall, the word 30 below its cell (I: one sample).
     */
    static final int SETTLEMENT_TEXT_X = 262, SETTLEMENT_STEP = 23;

    /**
     * The word sits this far below the top the next list entry would have
     * (clip004 #1974: max(102 + 8, 92 + 18) + 7 = 117; landing-slow
     * #6182: 124, #4193 under the hut: 129), and without any entry this far
     * below the last line (I).
     */
    static final int PROMPT_WORD_DY = 7, PROMPT_WORD_NO_LIST_DY = 15;

    /** The word's lowest glyph top: drawn over what lies there (clip005 #15391). */
    static final int PROMPT_WORD_MAX_Y = 192;

    /**
     * The word's colours: white while the blink is ON, black while OFF,
     * no shadow (clip004 section 1.2: indices 15 and 0).
     */
    static final int PROMPT_ON_RGB = 0xFFFFFF, PROMPT_OFF_RGB = 0x000000;

    /** FreeCol resource suffixes by NAMES.TXT {@code @RESOURCE} row (null: none). */
    private static final String[] RESOURCES = {
        null, "oasis", "grain", "cotton", "tobacco", "sugar", "minerals",
        "fish", "furs", "game", "lumber", null, "silver", "ore"
    };

    /** FreeCol native nation suffixes by NAMES.TXT {@code @TRIBES} row. */
    private static final String[] TRIBES = {
        "inca", "aztec", "arawak", "iroquois", "cherokee", "apache", "sioux",
        "tupi"
    };

    /** NAMES.TXT {@code @RESOURCE} row of a FreeCol resource type, or -1. */
    static int resourceRow(String resourceTypeId) {
        final String id = Role.getRoleIdSuffix(resourceTypeId);
        for (int i = 0; i < RESOURCES.length; i++) {
            if (id.equals(RESOURCES[i])) return i;
        }
        return -1;
    }

    /** NAMES.TXT {@code @TRIBES} row of a FreeCol native nation, or -1. */
    static int tribeRow(String nationId) {
        final String id = Role.getRoleIdSuffix(nationId);
        for (int i = 0; i < TRIBES.length; i++) {
            if (id.equals(TRIBES[i])) return i;
        }
        return -1;
    }

    /**
     * The tile mode's land name: the tribe's land on a native settlement's
     * tile ("Araukaner Land", landing-slow #4193), else the player's name
     * for the new land, else the nation's default ({@code @COLONYNAME},
     * "Neuholland"); none on water (clip005 #18186).  The tile next to a
     * village reads "Neuholland" (#2306), so the tribe's name goes with
     * the settlement, not with FreeCol's land claims (I).
     *
     * @return The name, or null.
     */
    static String landName(ClassicText t, TileFacts f) {
        if (!f.land) return null;
        if (f.tribeRow >= 0) {
            final String tribe = (t == null) ? null : cell(t, "TRIBES", f.tribeRow);
            final String land = (t == null) ? null : t.misc(MISC_LAND);
            return (tribe == null || land == null) ? tribe : tribe + " " + land.trim();
        }
        if (f.landName != null) return f.landName;
        return (t == null || f.nation < 0) ? null : cell(t, "COLONYNAME", f.nation);
    }

    /**
     * The tile mode's lines (clip004 section 1.3): "Ort: (x, y) n" at
     * (242,68), then {@link #LINE_PITCH} apart the land name (land only),
     * the terrain in parentheses, and one line each for a river, a road,
     * plowing and a resource, in that order (W21 item 2), all green.
     */
    static List<TextLine> tileLines(ClassicText t, TileFacts f) {
        final List<TextLine> out = new ArrayList<>();
        int y = TILE_Y;
        final String placeL = (t == null) ? null : t.label("INFO", INFO_PLACE);
        out.add(new TextLine(((placeL == null) ? "" : placeL.trim() + " ")
                + "(" + f.x + ", " + f.y + ") " + f.region, TILE_X, y, false));
        y += LINE_PITCH;
        final String land = landName(t, f);
        if (land != null) {
            out.add(new TextLine(land, TILE_X, y, false));
            y += LINE_PITCH;
        }
        if (t == null) return out;
        final List<String> extra = new ArrayList<>();
        extra.add(terrainName(t, f.terrainId));
        extra.addAll(tileExtras(t, f.river, f.road, f.plowed, f.resourceRow));
        for (String e : extra) {
            if (e == null) continue;
            out.add(new TextLine("(" + e.trim() + ")", TILE_X, y, false));
            y += LINE_PITCH;
        }
        return out;
    }

    /** A settlement entry's two green lines: the tribe and its kind ("ein Dorf"). */
    static List<TextLine> settlementLines(ClassicText t, SettlementFacts s,
                                          int cellY) {
        final List<TextLine> out = new ArrayList<>();
        if (t == null) return out;
        final String tribe = cell(t, "TRIBES", s.tribeRow);
        final String lv = cell(t, "TRIBES", s.tribeRow, TRIBE_LEVEL);
        int level = -1;
        try {
            level = (s.capital) ? LEVEL_CAPITAL : Integer.parseInt(lv);
        } catch (NumberFormatException | NullPointerException e) {
            level = -1;
        }
        final String kind = (level < 0) ? null
            : cell(t, "LEVELS", level, LEVEL_SINGULAR);
        if (tribe != null) {
            out.add(new TextLine(tribe, SETTLEMENT_TEXT_X, cellY + LIST_TEXT_DY, false));
        }
        if (kind != null) {
            out.add(new TextLine(kind, SETTLEMENT_TEXT_X,
                cellY + LIST_TEXT_DY + LIST_ORDERS_DY, false));
        }
        return out;
    }

    /**
     * The tile mode's block under its lines ({@link #blockLayout}): the
     * colony on the tile (clip005 #15391: N 116 under the last line 96;
     * dago-colony2 #4045: 109 under 89), or the native settlement, then
     * the units.
     */
    static BlockLayout tileBlockLayout(ClassicText t, TileFacts f) {
        final List<TextLine> lines = tileLines(t, f);
        int last = TILE_Y;
        for (TextLine l : lines) last = Math.max(last, l.y);
        return blockLayout(t, last, false, f.colony != null, f.settlement != null,
                           f.units);
    }

    /**
     * The tile mode's layout: where each list entry's cell goes (the
     * settlement first, then the units, {@link #LIST_GAP} below the last
     * line -- or below the colony block -- and stepped as the unit list)
     * and where the word goes.
     *
     * @return {word y, entry cell y ...}; an entry that would cross the
     *     screen's bottom gets -1 (skipped, as in the unit list).
     */
    static int[] tileLayout(ClassicText t, TileFacts f) {
        final BlockLayout lay = tileBlockLayout(t, f);
        final int s = (f.settlement == null) ? 0 : 1;
        final int[] out = new int[1 + s + f.units.size()];
        if (s > 0) out[1] = lay.settlementY;
        System.arraycopy(lay.entryY, 0, out, 1 + s, lay.entryY.length);
        out[0] = Math.min(PROMPT_WORD_MAX_Y, (lay.nextY < 0)
            ? lay.lastLineY + PROMPT_WORD_NO_LIST_DY : lay.nextY + PROMPT_WORD_DY);
        return out;
    }

    /**
     * Where the word "Spielzugende" is drawn under a tile: its glyph box
     * (a press there ends the turn, build spec W17 item 7).
     *
     * @param font FONTTINY, or null (then a box 7 rows high and 45 wide).
     * @return The box in screen pixels.
     */
    static Rectangle promptWordBounds(ClassicFont font, ClassicText t, TileFacts f) {
        final String w = promptWord(t);
        final int y = tileLayout(t, f)[0];
        final int width = (font == null) ? 45 : font.stringWidth(w);
        final int height = (font == null) ? 7 : font.height();
        return new Rectangle(TILE_X, y, Math.max(1, width), Math.max(1, height));
    }

    /** The word "Spielzugende" (LABELS @MISC row 2), or FreeCol's end-turn label. */
    static String promptWord(ClassicText t) {
        final String w = (t == null) ? null : t.misc(MISC_END_TURN);
        return (w == null) ? Messages.message("endTurnAction.name") : w.trim();
    }

    /**
     * The tile mode in place of the unit block (build spec W17; clip004
     * section 1.3, landing-slow #2306/#4193/#6182): the tile's lines, its
     * colony's block (build spec W21b, clip005 #15391) or the native
     * settlement's entry (its sprite at the map's settlement offset, no
     * shadow), the units' entries as in the unit list with "+ Weiter +",
     * and below them the word "Spielzugende" in {@code wordRgb}, drawn over
     * whatever lies there (clip005 #15391: the word at 192 over the
     * "+ Weiter +" at 196).
     *
     * @param wordRgb The word's colour, or -1 for no word (a tile mode
     *     without the Spielzugende mode, clip005 #18186).
     */
    static void paintTileMode(Graphics2D g, ClassicFont font, ClassicText t,
                              TileFacts f, int wordRgb) {
        if (f == null) return;
        final Graphics2D gg = (Graphics2D) g.create();
        try {
            gg.clipRect(PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
            drawLines(gg, font, tileLines(t, f));
            final BlockLayout lay = tileBlockLayout(t, f);
            if (lay.colonyY >= 0) paintColony(gg, font, t, f.colony, lay.colonyY);
            if (f.settlement != null) {
                final int cy = lay.settlementY;
                final BufferedImage sp = f.settlement.sprite;
                if (sp != null) {
                    gg.drawImage(sp, CELL_X + settlementOffset(sp.getWidth()), cy, null);
                }
                drawLines(gg, font, settlementLines(t, f.settlement, cy));
            }
            paintEntries(gg, font, t, f.units, lay);
            if (wordRgb >= 0) {
                final String w = promptWord(t);
                final int wy = tileLayout(t, f)[0];
                if (font != null) {
                    font.draw(gg, w, TILE_X, wy, ClassicFont.colours(wordRgb & 0xFFFFFF));
                } else {
                    gg.setColor(new Color(wordRgb & 0xFFFFFF));
                    gg.drawString(w, TILE_X, wy + 6);
                }
            }
        } finally {
            gg.dispose();
        }
    }

    /**
     * The Spielzugende mode's cursor square (build spec W17 item 3): a
     * 1-native-px white outline of the cell, its 60 border pixels, drawn
     * over terrain and sprite (clip004 section 1.2).
     *
     * @param g The graphics, in screen pixels.
     * @param sx The cell's left edge.
     * @param sy The cell's top edge.
     * @param s The scale (screen pixels per native pixel).
     */
    static void paintPromptSquare(Graphics2D g, int sx, int sy, int s) {
        g.setColor(new Color(PROMPT_ON_RGB));
        final int n = 16 * s;
        g.fillRect(sx, sy, n, s);
        g.fillRect(sx, sy + n - s, n, s);
        g.fillRect(sx, sy, s, n);
        g.fillRect(sx + n - s, sy, s, n);
    }

    /** A settlement sprite as the map draws it on the HUD (at most 22x16), else fitted. */
    static BufferedImage fitSettlement(BufferedImage img) {
        if (img == null || (img.getWidth() <= 22 && img.getHeight() <= 16)) return img;
        return fit16(img);
    }

    /** Scale a sprite down to fit 16x16 (nearest-neighbour); small ones as they are. */
    static BufferedImage fit16(BufferedImage img) {
        if (img == null || (img.getWidth() <= 16 && img.getHeight() <= 16)) return img;
        final double s = Math.min(16.0 / img.getWidth(), 16.0 / img.getHeight());
        final int w = Math.max(1, (int) Math.round(img.getWidth() * s));
        final int h = Math.max(1, (int) Math.round(img.getHeight() * s));
        final BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(img, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return out;
    }


    // Strings (from the pack)

    /**
     * The season line: NAMES.TXT {@code @SEASONS} (0 spring, 1 autumn) and
     * the year.  FreeCol has no season before its season year (1600 in the
     * classic rules, {@code Turn.getSeason} returns -1), while the original
     * writes the spring name from 1492 on (083/049), so a negative season reads
     * as spring.
     *
     * @return The line, or null when the pack lacks {@code @SEASONS}.
     */
    static String seasonLine(ClassicText t, int season, int year) {
        if (t == null) return null;
        final List<String[]> rows = t.names("SEASONS");
        if (rows.isEmpty()) return null;
        final int s = Math.floorMod(Math.max(0, season), rows.size());
        return rows.get(s)[0] + " " + year;
    }

    /**
     * The gold line, e.g. gold 1000 and tax 0 (see the class comment for the measured
     * spacing).
     *
     * @return The line, or null when the pack lacks a label.
     */
    static String goldLine(ClassicText t, int gold, int tax) {
        if (t == null) return null;
        final String g = t.label("CTITLE", CTITLE_GOLD);
        final String x = t.label("CTITLE", CTITLE_TAX);
        if (g == null || x == null) return null;
        return g.trim() + gold + "$  " + x.trim() + " " + tax + "%";
    }


    // From the game

    /**
     * A nation's flag fill (the units' flags on the map and the panel, the
     * blink dot): the measured fill for the four European nations of the
     * original, a tribe's NAMES.TXT {@code @TRIBES} colour (clip004 #5090:
     * the Sioux brave's flag 118 {@code #920000}; #4987: the Araukaner's
     * 54; the other six as the turn indicator, I), else FreeCol's nation
     * colour.
     */
    static int nationRgb(Player p) {
        if (p == null) return 0xFFFFFF;
        final int n = Arrays.asList(ClassicNewWorldScreens.NATION_IDS)
            .indexOf(p.getNationId());
        if (n >= 0 && NATION_FILL[n] >= 0) return NATION_FILL[n];
        if (tribeRow(p.getNationId()) >= 0) {
            final Integer t = INDICATOR_RGB.get(p.getNationId());
            if (t != null) return t;
        }
        final Color c = p.getNationColor();
        return (c == null) ? 0xFFFFFF : (c.getRGB() & 0xFFFFFF);
    }

    /**
     * A nation's darker shade (the order letter on its flag): measured for
     * England {@code 0xAA0000} and Holland {@code 0xAA4900} (052/032), else
     * two thirds of {@link #nationRgb} (unmeasured).
     */
    static int nationDark(Player p) {
        if (p != null) {
            final int n = Arrays.asList(ClassicNewWorldScreens.NATION_IDS)
                .indexOf(p.getNationId());
            if (n >= 0 && NATION_DARK[n] >= 0) return NATION_DARK[n];
        }
        final int c = nationRgb(p);
        return (((c >> 16) & 0xFF) * 2 / 3 << 16) | (((c >> 8) & 0xFF) * 2 / 3 << 8)
            | ((c & 0xFF) * 2 / 3);
    }

    /**
     * A tile type's minimap colour without an owner (build spec W16):
     * {@link #MINIMAP_LAND_RGB} for land, {@link #OCEAN_RGB} for water
     * (ocean and high seas measured; FreeCol's lake and great river, which
     * the original has not, read as ocean).
     *
     * @param tileTypeId The FreeCol tile type id, e.g. {@code model.tile.plains}.
     * @param land Whether the tile is land.
     * @return The RGB; an unknown land type gets the plains colour (I).
     */
    static int minimapLandRgb(String tileTypeId, boolean land) {
        if (!land) return OCEAN_RGB;
        final Integer c = (tileTypeId == null) ? null
            : MINIMAP_LAND_RGB.get(Role.getRoleIdSuffix(tileTypeId));
        return (c != null) ? c : MINIMAP_LAND_RGB.get("plains");
    }

    /**
     * The minimap of the player's known map (build spec W16): unexplored
     * black, a tile with a colony or unit in its owner's colour -- the
     * Europeans' fill, the tribes' NAMES.TXT {@code @TRIBES} colour
     * ({@link #indicatorRgb}: Araukaner villages 54 in every frame of
     * landfall and clips 004-008, Sioux braves 118 in clip004) -- else the
     * terrain's colour ({@link #minimapLandRgb}).
     *
     * @param map The client's map (the player's knowledge).
     * @param c0 The view's top-left column.
     * @param r0 The view's top-left row.
     * @return The model, or null without a map.
     */
    static MinimapModel minimapOf(net.sf.freecol.common.model.Map map,
                                  int c0, int r0) {
        if (map == null) return null;
        final int w = map.getWidth(), h = map.getHeight();
        if (w <= 0 || h <= 0) return null;
        final int[] rgb = new int[w * h];
        Arrays.fill(rgb, UNEXPLORED);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                final Tile tile = map.getTile(x, y);
                if (tile == null || !tile.isExplored()) continue;
                Player owner = null;
                final Settlement s = tile.getSettlement();
                if (s != null) {
                    owner = s.getOwner();
                } else {
                    final Unit u = tile.getFirstUnit();
                    if (u != null) owner = u.getOwner();
                }
                rgb[y * w + x] = (owner != null) ? indicatorRgb(owner)
                    : minimapLandRgb((tile.getType() == null) ? null
                                     : tile.getType().getId(), tile.isLand());
            }
        }
        return new MinimapModel(w, h, rgb, c0, r0);
    }
}
