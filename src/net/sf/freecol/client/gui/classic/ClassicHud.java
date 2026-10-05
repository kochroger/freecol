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
import java.util.List;

import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.AbstractGoods;
import net.sf.freecol.common.model.Player;
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
 *       "$" + two spaces + taxLabel + " " + tax} -- no space after the
 *       colon (120/120 ink pixels; with a space 101/120), and FONTTINY's '$'
 *       is the small coin glyph.  It is clipped only by the screen edge
 *       (Steam 000 cuts it inside the tax label at x = 319).</li>
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
     * The original keeps the active unit in view column 7 and row 6 of the
     * 15x12 map (ring and ship in 083: column 11 only because the view is
     * clamped at the map's right edge).  Assumed for the column, measured
     * for the row.
     */
    static final int UNIT_COL = 7, UNIT_ROW = 6;

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

        PanelModel(MinimapModel minimap, String season, String gold,
                   boolean scene) {
            this(minimap, season, gold, scene, null, null);
        }

        PanelModel(MinimapModel minimap, String season, String gold,
                   boolean scene, UnitFacts active, List<UnitFacts> list) {
            this.minimap = minimap;
            this.season = season;
            this.gold = gold;
            this.scene = scene;
            this.active = active;
            this.list = (list == null) ? new ArrayList<>() : list;
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

        UnitFacts(BufferedImage sprite, int fill, int dark, int nation,
                  int unitRow, String plainName, int moves, int x, int y,
                  int ordersRow, String terrainId, boolean road, int jobRow,
                  boolean roleless, int qualifier, int tools) {
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
        }

        /**
         * The facts of a live unit.
         *
         * @param u The unit.
         * @param sprite Its map sprite (any size; scaled down to 16x16 when
         *     larger, i.e. without the pack).
         * @return The facts.
         */
        static UnitFacts of(Unit u, BufferedImage sprite) {
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
            return new UnitFacts(fit16(sprite), nationRgb(owner),
                nationDark(owner), nation, unitRow(type, role), plain,
                Math.max(0, u.getMovesLeft()),
                (t == null) ? 0 : t.getX(), (t == null) ? 0 : t.getY(),
                ordersRow(u),
                (t == null || t.getType() == null) ? null : t.getType().getId(),
                t != null && t.hasRoad(),
                person ? jobRow(type) : -1, roleless,
                qualifier(type, role), tools);
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
            clamp(x - UNIT_COL, 1, mapWidth - VIEW_COLS - 1),
            clamp(y - UNIT_ROW, 1, mapHeight - VIEW_ROWS - 1)
        };
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

    /** The season and gold lines, clipped at the screen's right edge. */
    static void paintStatus(Graphics2D g, ClassicFont font, String season,
                            String gold) {
        final Graphics2D gg = (Graphics2D) g.create();
        try {
            gg.clipRect(PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
            ClassicMenuBox.text(gg, font, season, STATUS_X, SEASON_Y,
                                ClassicMenuBox.GAME, false);
            ClassicMenuBox.text(gg, font, gold, STATUS_X, GOLD_Y,
                                ClassicMenuBox.GAME, false);
        } finally {
            gg.dispose();
        }
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
     * mode -- the unit block and list ({@link #paintUnits}).
     *
     * @param text The pack's texts for the unit lines, or null (then only
     *     the units' plain names are written).
     */
    static void paintPanel(Graphics2D g, ClassicFont font, BufferedImage wood,
                           ClassicText text, PanelModel p) {
        paintChrome(g, wood);
        paintMinimap(g, p.minimap);
        paintStatus(g, font, p.season, p.gold);
        if (!p.scene) paintUnits(g, font, text, p.active, p.list);
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
     * The active unit's lines (032, 052, 000, 007): moves label + N at (260,70),
     * position label + ' (x, y)' at (260,77), the name at (242,86) -- nationality and
     * {@code @UNIT} name, green -- then 7 px apart from y 93: the colonist's
     * skill ({@code @JOB}, gold), the tools as '(' + number and, on its own
     * line, the tools word + ')' (gold, 007), the orders (gold), the terrain
     * in parentheses (green) and '(' + road + ')' (green) on a road.
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
        final String job = (f.jobRow < 0) ? null : cell(t, "JOB", f.jobRow);
        if (job != null) {
            out.add(new TextLine(job, CELL_X, y, true));
            y += LINE_PITCH;
        }
        final String tools = cell(t, "CARGO", CARGO_TOOLS);
        if (f.tools >= 0 && tools != null) {
            out.add(new TextLine("(" + f.tools, CELL_X, y, true));
            y += LINE_PITCH;
            out.add(new TextLine(tools + ")", CELL_X, y, true));
            y += LINE_PITCH;
        }
        final String orders = cell(t, "ORDERS", f.ordersRow);
        if (orders != null) {
            out.add(new TextLine(orders, CELL_X, y, true));
            y += LINE_PITCH;
        }
        final String terrain = terrainName(t, f.terrainId);
        if (terrain != null) {
            out.add(new TextLine("(" + terrain + ")", CELL_X, y, false));
            y += LINE_PITCH;
        }
        final String road = t.misc(MISC_ROAD);
        if (f.road && road != null) {
            out.add(new TextLine("(" + road.trim() + ")", CELL_X, y, false));
        }
        return out;
    }

    /** Nationality and {@code @UNIT} name, or the plain name. */
    static String name(ClassicText t, UnitFacts f) {
        final String unit = (f.unitRow < 0) ? null : cell(t, "UNIT", f.unitRow);
        if (unit == null) return f.plainName;
        final String nat = (f.nation < 0) ? null : cell(t, "NATIONALITY", f.nation);
        return (nat == null) ? unit : nat + " " + unit;
    }

    /**
     * A list entry's lines, all gold at x = 260 from {@code spriteY + 4}
     * (032, 000, 007): the qualifier -- the veteran word, the expert word
     * plus the tool count for an expert pioneer, else the bare tool count,
     * else the skill of a colonist without a role or with a skill that is not
     * the free colonist's -- with the tools word on its own line after a
     * count, 7 px apart; then the orders 6 px below the last of them (or at
     * the first line when there is none).
     */
    static List<TextLine> listLines(ClassicText t, UnitFacts f, int spriteY) {
        final List<TextLine> out = new ArrayList<>();
        final int y0 = spriteY + LIST_TEXT_DY;
        if (t == null) {
            out.add(new TextLine(f.plainName, INFO_X, y0, true));
            return out;
        }
        final List<String> q = new ArrayList<>();
        final String toolsWord = cell(t, "CARGO", CARGO_TOOLS);
        final String count = (f.tools >= 0) ? Integer.toString(f.tools) : null;
        if (f.qualifier != QUAL_NONE) {
            final String w = t.misc(f.qualifier == QUAL_VETERAN ? MISC_VETERAN
                                    : MISC_EXPERT);
            if (w != null) q.add((count == null) ? w.trim() : w.trim() + " " + count);
            else if (count != null) q.add(count);
        } else {
            final boolean showJob = f.jobRow >= 0
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
        final String orders = cell(t, "ORDERS", f.ordersRow);
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
     * One unit icon (proved order: 032, 000): the black silhouette shifted
     * {@link #SHADOW_DX}, then the flag -- black ring, nation fill, the
     * {@code @ORDERS} letter in FONTTINY at ring + (2,2), black for '-' and
     * the darker nation shade otherwise -- then the sprite.
     */
    static void paintIcon(Graphics2D g, ClassicFont font, String letter,
                          UnitFacts f, int cellX, int cellY) {
        final BufferedImage sp = f.sprite;
        final int w = (sp == null) ? 16 : sp.getWidth();
        final int sx = cellX + spriteOffset(w);
        if (sp != null) {
            g.setColor(Color.BLACK);
            for (int y = 0; y < sp.getHeight(); y++) {
                for (int x = 0; x < sp.getWidth(); x++) {
                    if ((sp.getRGB(x, y) >>> 24) == 0) continue;
                    g.fillRect(sx + x + SHADOW_DX, cellY + y, 1, 1);
                }
            }
        }
        final Rectangle r = flagRing(cellX, cellY, w);
        g.setColor(Color.BLACK);
        ring(g, r.x, r.y, r.width, r.height);
        g.setColor(new Color(f.fill & 0xFFFFFF));
        g.fillRect(r.x + 1, r.y + 1, r.width - 2, r.height - 2);
        if (font != null && letter != null && !letter.isEmpty()) {
            final int ink = "-".equals(letter) ? 0x000000 : (f.dark & 0xFFFFFF);
            font.draw(g, letter, r.x + 2, r.y + 2, ClassicFont.colours(ink));
        }
        if (sp != null) g.drawImage(sp, sx, cellY, null);
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
        final String l = (t == null) ? null : cell(t, "ORDERS", f.ordersRow, 1);
        return (l == null || l.isEmpty()) ? "-" : l.substring(0, 1);
    }

    /**
     * The active-unit block and the unit list, clipped to the panel.  The
     * list's first sprite is {@link #LIST_GAP} below the block's last line
     * (032: 100 -&gt; 110; 000: 114 -&gt; 124); an entry is skipped once its
     * sprite would cross the screen's bottom (the original's overflow is
     * unknown).
     */
    static void paintUnits(Graphics2D g, ClassicFont font, ClassicText t,
                           UnitFacts active, List<UnitFacts> list) {
        if (active == null) return;
        final Graphics2D gg = (Graphics2D) g.create();
        try {
            gg.clipRect(PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
            paintIcon(gg, font, letter(t, active), active, CELL_X, ACTIVE_CELL_Y);
            final List<TextLine> lines = activeLines(t, active);
            drawLines(gg, font, lines);
            int last = NAME_Y;
            for (TextLine l : lines) last = Math.max(last, l.y);
            int y = last + LIST_GAP;
            for (UnitFacts f : list) {
                if (y + 16 > PANEL_Y + PANEL_H) break;
                paintIcon(gg, font, letter(t, f), f, CELL_X, y);
                final List<TextLine> ls = listLines(t, f, y);
                drawLines(gg, font, ls);
                y = nextListY(y, ls);
            }
        } finally {
            gg.dispose();
        }
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
        return g.trim() + gold + "$  " + x.trim() + " " + tax;
    }


    // From the game

    /**
     * A nation's colour on the minimap: the measured fill for the four
     * European nations of the original, else FreeCol's nation colour.
     */
    static int nationRgb(Player p) {
        if (p == null) return 0xFFFFFF;
        final int n = Arrays.asList(ClassicNewWorldScreens.NATION_IDS)
            .indexOf(p.getNationId());
        if (n >= 0 && NATION_FILL[n] >= 0) return NATION_FILL[n];
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
     * The minimap of the player's known map: unexplored black, a tile with
     * a colony or unit in its owner's colour, ocean {@link #OCEAN_RGB},
     * land in FreeCol's minimap terrain colour (approximate: the original's
     * land colours are not mapped yet).
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
                int c;
                if (owner != null) {
                    c = nationRgb(owner);
                } else if (tile.isLand() && tile.getType() != null) {
                    final Color lc = ImageLibrary.getMinimapPoliticsColor(tile.getType());
                    c = (lc == null) ? GREEN : (lc.getRGB() & 0xFFFFFF);
                } else {
                    c = OCEAN_RGB;
                }
                rgb[y * w + x] = c;
            }
        }
        return new MinimapModel(w, h, rgb, c0, r0);
    }
}
