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
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.AbstractGoods;
import net.sf.freecol.common.model.Building;
import net.sf.freecol.common.model.BuildableType;
import net.sf.freecol.common.model.BuildingType;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.ProductionInfo;
import net.sf.freecol.common.model.Role;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;


/**
 * The colony screen's <b>right panel</b> in the original's look (build spec
 * M4.7, {@code playthrough-3-analysis/11-spec-M4-colony.md}; L §7, dago
 * {@code 01-layout.md} §6, clip008 {@code 01-colony.md} §2, clip 019
 * {@code 10-spec.md} §4): the three mode buttons (house, musket, hammer;
 * ICONS.SS.067-069, the pressed one sunk by a pixel), and behind them
 * <ul>
 *   <li><b>house</b>: three rows of icons from x 213, the raw goods of the
 *   tiles but food and lumber (row 1), the horses and the made goods (row
 *   2), the lumber and the hammers (row 3), each good what it makes and
 *   its shortfall crossed by the red X 055 ({@link
 *   ClassicColonyLayout#houseRow});</li>
 *   <li><b>musket</b>: «Vorhandene Einheiten» (LABELS {@code @CMISC} 1) and
 *   the units on the colony's tile in 18-px cells, drawn as the map draws
 *   them, the selected one in its green frame (white under the white
 *   cursor);</li>
 *   <li><b>hammer</b>: the current build's name (NAMES), KAUFEN and ÄNDERN
 *   (LABELS {@code @CTITLE} 2, 3) in their boxes, the stored hammers in rows
 *   of cost / 4, «(Benötigt n Werkzeuge)».</li>
 * </ul>
 *
 * <p>A model of what is drawn ({@link #model}), then one painter
 * ({@link #paint}), as {@link ClassicColonyBand}.  The numbers are
 * FreeCol's: what the colony makes (its tiles and buildings), the
 * shortfalls (what its buildings would use at their full production less
 * what is made, and what they would make more), its build, its hammers
 * and tools.
 */
final class ClassicColonyRight {

    private ClassicColonyRight() {}   // static helpers only


    // The modes

    /** The three panels behind the buttons (house, musket, hammer). */
    enum Mode {
        HOUSE, MUSKET, HAMMER;

        /** @return The recorder's word. */
        String word() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** How the colony screen was opened (spec §0.1 decision 8). */
    enum Opening {
        /** The colony was just founded. */
        FOUNDING,
        /** «Zur Kolonie zoomen.» of a @BUILT box (a building). */
        BUILT_BUILDING,
        /** «Zur Kolonie zoomen.» of a @BUILT box (a unit, visit 21). */
        BUILT_UNIT,
        /** «Zur Kolonie zoomen.» of a @NEEDTOOLS box. */
        NEED_TOOLS,
        /** «Zur Kolonie zoomen.» of an @ORE box. */
        ORE,
        /** A click on the map, a unit entering it, @CARGOREADY0's zoom. */
        OTHER
    }

    /**
     * The mode at an opening (spec §0.1 decision 8, L §7.1: 22 openings):
     * a founding in house mode (V ×2), a @BUILT or @NEEDTOOLS zoom in
     * hammer mode (V ×5), an @ORE zoom in house mode (V ×1), otherwise the
     * mode the last colony visit ended in, for all colonies (V ×14, I the
     * rule).
     *
     * @param how How the screen opened.
     * @param last The mode the last visit ended in.
     * @return The mode.
     */
    static Mode modeAtOpening(Opening how, Mode last) {
        switch ((how == null) ? Opening.OTHER : how) {
        case FOUNDING: case ORE:
            return Mode.HOUSE;
        case BUILT_BUILDING: case BUILT_UNIT: case NEED_TOOLS:
            return Mode.HAMMER;
        default:
            return (last == null) ? Mode.HOUSE : last;
        }
    }


    // House mode

    /**
     * The goods of the house panel's rows, in their order (L §7.2, dago
     * §6.1, clip008 §2): row 1 the raw goods of the tiles but food and
     * lumber, row 2 the horses and the made goods, row 3 the lumber and the
     * hammers (V: #2260 sugar | rum | lumber, hammers; #123003 furs, silver
     * | horses; dago tobacco, cotton, ore | horses, cigars, tools, muskets |
     * lumber, hammers).
     */
    static final String[][] HOUSE_GOODS = {
        { "model.goods.sugar", "model.goods.tobacco", "model.goods.cotton",
          "model.goods.furs", "model.goods.ore", "model.goods.silver" },
        { "model.goods.horses", "model.goods.rum", "model.goods.cigars",
          "model.goods.cloth", "model.goods.coats", "model.goods.tools",
          "model.goods.muskets" },
        { "model.goods.lumber", "model.goods.hammers" },
    };

    /** What the house panel shows of a colony: per row and good the made and crossed amounts. */
    static final class House {

        /** Per row and good ({@link #HOUSE_GOODS}): made, crossed. */
        final int[][] made, crossed;

        /** The goods' icon widths per row and good. */
        final int[][] widths;

        /** The three rows laid out. */
        final ClassicColonyLayout.HouseRow[] rows;

        House(int[][] made, int[][] crossed, int[][] widths) {
            this.made = copy(made);
            this.crossed = copy(crossed);
            this.widths = copy(widths);
            this.rows = new ClassicColonyLayout.HouseRow[HOUSE_GOODS.length];
            for (int r = 0; r < this.rows.length; r++) {
                this.rows[r] = ClassicColonyLayout.houseRow(this.made[r], this.crossed[r],
                                                            this.widths[r], r);
            }
        }

        private static int[][] copy(int[][] a) {
            final int[][] out = new int[a.length][];
            for (int i = 0; i < a.length; i++) out[i] = a[i].clone();
            return out;
        }

        /**
         * The same with every amount given anew (a test's: the clip's,
         * where FreeCol's rules make others), laid out again.
         *
         * @param amounts Per good its id's short form and amounts, e.g.
         *     {@code "lumber 8 4"} (made 8, crossed 4); the goods not named are 0.
         * @return The model.
         */
        House only(String... amounts) {
            final int[][] m = new int[HOUSE_GOODS.length][], x = new int[HOUSE_GOODS.length][];
            for (int r = 0; r < HOUSE_GOODS.length; r++) {
                m[r] = new int[HOUSE_GOODS[r].length];
                x[r] = new int[HOUSE_GOODS[r].length];
            }
            for (String a : amounts) {
                final String[] q = a.trim().split("\\s+");
                final int[] at = find("model.goods." + q[0]);
                if (at == null) throw new IllegalArgumentException(a);
                m[at[0]][at[1]] = Integer.parseInt(q[1]);
                x[at[0]][at[1]] = (q.length > 2) ? Integer.parseInt(q[2]) : 0;
            }
            return new House(m, x, this.widths);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            for (int r = 0; r < HOUSE_GOODS.length; r++) {
                sb.append((r == 0) ? "" : " ").append("row").append(r + 1).append('[');
                boolean any = false;
                for (int g = 0; g < HOUSE_GOODS[r].length; g++) {
                    if (this.made[r][g] == 0 && this.crossed[r][g] == 0) continue;
                    sb.append((any) ? " " : "")
                        .append(ClassicColonyBuildings.shortId(HOUSE_GOODS[r][g]))
                        .append(' ').append(this.made[r][g]);
                    if (this.crossed[r][g] > 0) sb.append('x').append(this.crossed[r][g]);
                    any = true;
                }
                sb.append("] ").append(this.rows[r]);
            }
            return sb.toString();
        }
    }

    /** @return A good's row and index in {@link #HOUSE_GOODS}, or null. */
    static int[] find(String goodsId) {
        for (int r = 0; r < HOUSE_GOODS.length; r++) {
            for (int g = 0; g < HOUSE_GOODS[r].length; g++) {
                if (HOUSE_GOODS[r][g].equals(goodsId)) return new int[] { r, g };
            }
        }
        return null;
    }

    /**
     * The house panel's model of a colony now (FreeCol's numbers; L §7.2:
     * a shortfall is the input good's icons, each crossed): a good's made
     * amount is what the colony makes of it (its tiles and buildings); its
     * crossed amount the larger of what its buildings would make more at
     * their full production (V clip008: the Zimmerei's 3 hammers crossed
     * without lumber; #14817: the second horse) and what the buildings
     * using it would use at their full production less what is made (V
     * #2260: the Sägewerk 12, the lumberjack 8, 4 lumber crossed and the 12
     * hammers made from the stock; dago: 34 tobacco made, 14 crossed for 72
     * cigars at the factory's 3:2).  Food is the band's, not shown here.
     *
     * @param colony The colony.
     * @param a The sprites (the icons' widths).
     * @return The model.
     */
    static House house(Colony colony, ClassicColonyBuildings.Assets a) {
        final Specification spec = colony.getSpecification();
        final int[][] made = new int[HOUSE_GOODS.length][], crossed = new int[HOUSE_GOODS.length][];
        for (int r = 0; r < HOUSE_GOODS.length; r++) {
            made[r] = new int[HOUSE_GOODS[r].length];
            crossed[r] = new int[HOUSE_GOODS[r].length];
            for (int g = 0; g < HOUSE_GOODS[r].length; g++) {
                final GoodsType gt = spec.getGoodsType(HOUSE_GOODS[r][g]);
                if (gt == null) continue;
                final int p = Math.max(0, colony.getTotalProductionOf(gt));
                int more = 0, need = 0;
                for (Building b : colony.getBuildings()) {
                    final ProductionInfo pi = colony.getProductionInfo(b);
                    if (pi == null) continue;
                    final int prod = amount(pi.getProduction(), gt);
                    final int max = (pi.getMaximumProduction().isEmpty()) ? prod
                        : Math.max(prod, amount(pi.getMaximumProduction(), gt));
                    more += Math.max(0, max - prod);
                    need += (pi.getMaximumConsumption().isEmpty())
                        ? amount(pi.getConsumption(), gt)
                        : amount(pi.getMaximumConsumption(), gt);
                }
                made[r][g] = p;
                crossed[r][g] = Math.max(0, Math.max(more, need - p));
            }
        }
        return new House(made, crossed, widths(a));
    }

    /** The amount of a good in a list. */
    private static int amount(List<AbstractGoods> list, GoodsType gt) {
        int n = 0;
        for (AbstractGoods ag : list) if (ag.getType() == gt) n += ag.getAmount();
        return Math.max(0, n);
    }

    /** The house goods' icon widths (the pack's; 8 without it). */
    static int[][] widths(ClassicColonyBuildings.Assets a) {
        final int[][] out = new int[HOUSE_GOODS.length][];
        for (int r = 0; r < HOUSE_GOODS.length; r++) {
            out[r] = new int[HOUSE_GOODS[r].length];
            for (int g = 0; g < HOUSE_GOODS[r].length; g++) {
                final BufferedImage img = (a == null) ? null
                    : a.icon(ClassicColonyLayout.goodsIcon(HOUSE_GOODS[r][g]));
                out[r][g] = (img == null) ? 8 : img.getWidth();
            }
        }
        return out;
    }


    // Musket mode

    /** One unit of «Vorhandene Einheiten»: the unit, its cell, its flag's letter and ink. */
    static final class Listed {

        final Unit unit;
        final Rectangle cell;
        final String letter;
        final int ink;

        Listed(Unit unit, Rectangle cell, String letter, int ink) {
            this.unit = unit;
            this.cell = cell;
            this.letter = letter;
            this.ink = ink;
        }

        @Override
        public String toString() {
            return Role.getRoleIdSuffix(this.unit.getType().getId())
                + ((this.unit.hasDefaultRole()) ? ""
                   : "/" + Role.getRoleIdSuffix(this.unit.getRole().getId()))
                + "@" + this.cell.x + "[" + this.letter + "]";
        }
    }

    /**
     * The units «Vorhandene Einheiten» lists (dago §6.2, clip 019 §4): the
     * colony tile's land units that are not in the colony, the persons
     * outside (who also stand on the fence and in the figure row) and the
     * rest (artillery, a treasure; V clip 019: the dragoon and 3
     * artillery, dago #6000: a colonist, the dragoon, 3 artillery), in the
     * screen's order (the units moved to the front first, M2); neither a
     * ship nor a wagon train, the port's (V clip 040 #85 396: a galleon in
     * port, #87 800: 3 galleons and 3 wagon trains in port, the list only
     * the 4 artillery).
     *
     * @param colony The colony.
     * @param order The screen's order of the units on the tile, or null
     *     (the tile's).
     * @return The units.
     */
    static List<Unit> listed(Colony colony, Function<List<Unit>, List<Unit>> order) {
        final Tile tile = colony.getTile();
        final List<Unit> standing = new ArrayList<>();
        if (tile == null) return standing;
        for (Unit u : tile.getUnitList()) {
            if (!u.isNaval() && !u.canCarryGoods()) standing.add(u);
        }
        final List<Unit> out = new ArrayList<>((order == null) ? standing
                                               : order.apply(standing));
        out.retainAll(standing);
        return out;
    }


    // Hammer mode

    /** What the hammer panel shows of a colony. */
    static final class Hammer {

        /** The build's name (NAMES), or «(Keine Produktion)». */
        final String caption;

        /** The build's hammers (0: none), the hammers stored. */
        final int cost, stored;

        /** «(Benötigt n Werkzeuge)», or null when the build needs no tools; its ink. */
        final String tools;
        final int toolsInk;

        Hammer(String caption, int cost, int stored, String tools, int toolsInk) {
            this.caption = caption;
            this.cost = Math.max(0, cost);
            this.stored = Math.max(0, stored);
            this.tools = tools;
            this.toolsInk = toolsInk;
        }

        /** @return The icons of a full row (cost / 4). */
        int q() {
            return this.cost / 4;
        }

        /**
         * @param stored Other hammers stored (a test's: the clip's).
         * @return The same with them.
         */
        Hammer withStored(int stored) {
            return new Hammer(this.caption, this.cost, stored, this.tools, this.toolsInk);
        }

        @Override
        public String toString() {
            return "\"" + this.caption + "\" " + this.stored + "/" + this.cost
                + " q" + q() + ((this.tools == null) ? "" : " \"" + this.tools + "\" ink"
                                + this.toolsInk);
        }
    }

    /** LABELS {@code @CTITLE} rows: KAUFEN, ÄNDERN, «(Keine Produktion)». */
    static final int CTITLE_BUY = 2, CTITLE_CHANGE = 3, CTITLE_NOTHING = 5;

    /** LABELS {@code @CMISC} row of «Vorhandene Einheiten». */
    static final int CMISC_UNITS = 1;

    /** LABELS {@code @MISC} row of «Benötigt» (LABELS.TXT:58). */
    static final int MISC_NEEDS = 42;

    /**
     * The hammer panel's model of a colony now: its current build (FreeCol's
     * build queue's first), its name as NAMES.TXT has it (a building's
     * {@code @BUILDING} row, a unit's {@code @UNIT} row; FreeCol's for one
     * the original lacks), its hammers and tools (FreeCol's costs: the
     * original's for every build seen, V: Verlag 52 / 20, Druckerei 120 /
     * 50, Lagerhaus 80 / 0, Wagenzug 40, Artillerie 192 / 40), the hammers
     * stored; «(Benötigt n Werkzeuge)» (LABELS «Benötigt», NAMES's tools)
     * white while the colony has fewer tools, grey when enough (V).
     *
     * @param colony The colony.
     * @param t The pack's texts, or null.
     * @return The model.
     */
    static Hammer hammer(Colony colony, ClassicText t) {
        final Specification spec = colony.getSpecification();
        // A completed building stays the current build (levi rule R4,
        // M4.10: «Verlag» with its old «(Benötigt 20 Werkzeuge)», V #57768).
        final BuildableType build = ClassicColonyBuild.current(colony);
        final GoodsType hammers = spec.getGoodsType("model.goods.hammers");
        final GoodsType toolsType = spec.getGoodsType("model.goods.tools");
        final int stored = (hammers == null) ? 0 : colony.getGoodsCount(hammers);
        if (build == null) {
            final String none = (t == null) ? null : t.label("CTITLE", CTITLE_NOTHING);
            return new Hammer((none == null) ? "(Keine Produktion)" : none.trim(), 0, stored,
                              null, ClassicColonyLayout.INK_WHITE);
        }
        int cost = 0, tools = 0;
        for (AbstractGoods ag : build.getRequiredGoodsList()) {
            if (ag.getType() == hammers) cost += ag.getAmount();
            if (ag.getType() == toolsType) tools += ag.getAmount();
        }
        final int stock = (toolsType == null) ? 0 : colony.getGoodsCount(toolsType);
        String line = null;
        if (tools > 0) {
            final String needs = (t == null) ? null : t.misc(MISC_NEEDS);
            line = "(" + ((needs == null) ? "Benötigt" : needs.trim()) + " " + tools + " "
                + ((toolsType == null) ? "" : ClassicTrade.goodsName(t, toolsType)) + ")";
        }
        return new Hammer(buildName(t, build), cost, stored, line,
                          ClassicColonyLayout.toolsInk(stock, tools));
    }

    /**
     * A build's name as the original's hammer panel has it: NAMES.TXT
     * {@code @BUILDING}'s row of a building type (the sprite frame's row;
     * the warehouse expansion 16, the stables 17), {@code @UNIT}'s of a unit
     * type; FreeCol's (literal) for a type the original lacks.
     *
     * @param t The pack's texts, or null.
     * @param build The build.
     * @return The name.
     */
    static String buildName(ClassicText t, BuildableType build) {
        if (build == null) return "";
        if (t != null && build instanceof BuildingType) {
            final String id = ClassicColonyBuildings.shortId(build.getId());
            final Integer row = ("warehouseExpansion".equals(id)) ? Integer.valueOf(16)
                : ("stables".equals(id)) ? Integer.valueOf(17)
                : ClassicColonyBuildings.frameOf(id);
            final List<String[]> rows = (row == null) ? null : t.names("BUILDING");
            if (rows != null && row < rows.size() && rows.get(row).length > 0
                && !rows.get(row)[0].trim().isEmpty()) return rows.get(row)[0].trim();
        }
        if (t != null && build instanceof UnitType) {
            final int row = ClassicHud.unitRow(Role.getRoleIdSuffix(build.getId()), null);
            final List<String[]> rows = (row < 0) ? null : t.names("UNIT");
            if (rows != null && row < rows.size() && rows.get(row).length > 0
                && !rows.get(row)[0].trim().isEmpty()) return rows.get(row)[0].trim();
        }
        String name;
        try {
            name = Messages.getName(build);
        } catch (RuntimeException e) {
            name = build.getId();
        }
        return ClassicAdvisorBox.literal(name);
    }


    // The model

    /** Where the white cursor stands on the right panel, if there. */
    enum Cursor { NONE, LIST, BUY, CHANGE }

    /** What the right panel shows. */
    static final class Model {

        /** The pressed button, and the panel shown (the press moves the first, the release the second). */
        final Mode buttons, mode;

        /** House mode's rows, or null. */
        final House house;

        /** Musket mode's caption and units, or null; the selected one. */
        final String unitsCaption;
        final List<Listed> units;
        final Unit selected;

        /** Hammer mode's panel, or null; KAUFEN's and ÄNDERN's words. */
        final Hammer hammer;
        final String buy, change;

        Model(Mode buttons, Mode mode, House house, String unitsCaption, List<Listed> units,
              Unit selected, Hammer hammer, String buy, String change) {
            this.buttons = buttons;
            this.mode = mode;
            this.house = house;
            this.unitsCaption = unitsCaption;
            this.units = (units == null) ? Collections.<Listed>emptyList() : units;
            this.selected = selected;
            this.hammer = hammer;
            this.buy = buy;
            this.change = change;
        }

        /** @return The same with another house panel (a test's). */
        Model withHouse(House h) {
            return new Model(this.buttons, this.mode, h, this.unitsCaption, this.units,
                             this.selected, this.hammer, this.buy, this.change);
        }

        /** @return The same with another hammer panel (a test's). */
        Model withHammer(Hammer h) {
            return new Model(this.buttons, this.mode, this.house, this.unitsCaption,
                             this.units, this.selected, h, this.buy, this.change);
        }

        /**
         * @param u A unit.
         * @return Its place in the list, or null.
         */
        Listed listed(Unit u) {
            for (Listed l : this.units) if (l.unit == u) return l;
            return null;
        }

        @Override
        public String toString() {
            final String s = (this.buttons == this.mode) ? this.mode.word()
                : this.mode.word() + " (" + this.buttons.word() + " pressed)";
            switch (this.mode) {
            case HOUSE:
                return s + " " + this.house;
            case MUSKET:
                return s + " \"" + this.unitsCaption + "\" " + this.units
                    + ((this.selected == null) ? "" : " selected="
                       + Role.getRoleIdSuffix(this.selected.getType().getId()));
            default:
                return s + " " + this.hammer;
            }
        }
    }

    /**
     * The right panel's model of a colony now.
     *
     * @param colony The colony.
     * @param t The pack's texts, or null.
     * @param a The sprites.
     * @param buttons The pressed button.
     * @param mode The panel shown.
     * @param order The screen's order of the tile's units, or null.
     * @param selected The list's selected unit, or null (the first).
     * @return The model.
     */
    static Model model(Colony colony, ClassicText t, ClassicColonyBuildings.Assets a,
                       Mode buttons, Mode mode, Function<List<Unit>, List<Unit>> order,
                       Unit selected) {
        House house = null;
        String unitsCaption = null;
        final List<Listed> units = new ArrayList<>();
        Unit sel = null;
        Hammer hammer = null;
        String buy = null, change = null;
        switch (mode) {
        case HOUSE:
            house = house(colony, a);
            break;
        case MUSKET: {
            final String c = (t == null) ? null : t.label("CMISC", CMISC_UNITS);
            unitsCaption = (c == null) ? "Vorhandene Einheiten" : c.trim();
            final List<Unit> list = listed(colony, order);
            for (int i = 0; i < list.size(); i++) {
                final Unit u = list.get(i);
                units.add(new Listed(u, ClassicColonyLayout.unitListCell(i, list.size()),
                    ClassicHud.orderLetter(t, ClassicUnitCycle.ordersRowShown(u)),
                    ClassicHud.letterInk(ClassicUnitCycle.ordersRowShown(u),
                                         ClassicHud.nationDark(u.getOwner()))));
            }
            // The first unit framed at the opening (V clip 019 #366, dago #6000).
            sel = (selected != null && list.contains(selected)) ? selected
                : (list.isEmpty()) ? null : list.get(0);
            break;
        }
        default: {
            hammer = hammer(colony, t);
            final String b = (t == null) ? null : t.label("CTITLE", CTITLE_BUY);
            final String ch = (t == null) ? null : t.label("CTITLE", CTITLE_CHANGE);
            buy = (b == null) ? "KAUFEN" : b.trim();
            change = (ch == null) ? "ÄNDERN" : ch.trim();
            break;
        }
        }
        return new Model(buttons, mode, house, unitsCaption, units, sel, hammer, buy, change);
    }


    // Painting

    /**
     * The right panel as the model says, over the band picture (the
     * chrome's): the three mode buttons, then the shown mode's panel.
     *
     * @param g The canvas.
     * @param c The chrome's assets (the font, the inks).
     * @param a The sprites.
     * @param tiny FONTTINY (the flags' letters), or null.
     * @param m The model.
     * @param cursor Where the white cursor stands on the panel.
     * @param cursorOn Whether the blink is in its on half.
     * @param labels Whether the house rows carry their count labels
     *     («Beschriftung an Waren und Terrain», I as the band's).
     */
    static void paint(Graphics2D g, ClassicColonyChrome.Assets c,
                      ClassicColonyBuildings.Assets a, ClassicFont tiny, Model m,
                      Cursor cursor, boolean cursorOn, boolean labels) {
        paintButtons(g, c, a, m.buttons);
        switch (m.mode) {
        case HOUSE:
            paintHouse(g, c, a, m.house, labels);
            break;
        case MUSKET:
            paintUnits(g, c, a, tiny, m, cursor == Cursor.LIST && cursorOn);
            break;
        default:
            paintHammer(g, c, a, m, (cursorOn) ? cursor : Cursor.NONE);
            break;
        }
    }

    /**
     * The three mode buttons: each its sprite (067-069) at (303, y) with
     * its ink-63 shadow on the right and below, the pressed one one pixel
     * lower and to the right with the shadow on its top and left (V #2260
     * house pressed, #9921 hammer pressed; clip 019 musket).
     */
    static void paintButtons(Graphics2D g, ClassicColonyChrome.Assets c,
                             ClassicColonyBuildings.Assets a, Mode pressed) {
        g.setColor(new Color(c.rgb(ClassicColonyLayout.INK_BUTTON_SHADOW)));
        for (int i = 0; i < ClassicColonyLayout.MODE_Y.length; i++) {
            final boolean down = pressed != null && pressed.ordinal() == i;
            final BufferedImage s = a.icon(ClassicColonyLayout.ICON_MODE_HOUSE + i);
            final Point p = ClassicColonyLayout.modeSprite(i, down);
            final int w = (s == null) ? ClassicColonyLayout.MODE_W - 1 : s.getWidth();
            final int h = (s == null) ? ClassicColonyLayout.MODE_H - 1 : s.getHeight();
            if (down) {
                g.fillRect(p.x - 1, p.y - 1, w, 1);
                g.fillRect(p.x - 1, p.y - 1, 1, h);
            } else {
                g.fillRect(p.x + w, p.y + 1, 1, h);
                g.fillRect(p.x + 1, p.y + h, w, 1);
            }
            if (s != null) g.drawImage(s, p.x, p.y, null);
        }
    }

    /**
     * The house rows: per good its icons, the crossed ones each under the
     * red X 055 (V #2260, dago), then the parts' count labels, white, a
     * crossed part's in red (ink 12; V dago «14», «2»).
     */
    private static void paintHouse(Graphics2D g, ClassicColonyChrome.Assets c,
                                   ClassicColonyBuildings.Assets a, House h, boolean labels) {
        if (h == null) return;
        final BufferedImage cross = a.icon(ClassicColonyLayout.ICON_SHORTFALL);
        for (int r = 0; r < h.rows.length; r++) {
            final ClassicColonyLayout.HouseRow row = h.rows[r];
            final int y = ClassicColonyLayout.HOUSE_ROW_Y[r];
            for (int gd = 0; gd < row.x.length; gd++) {
                final BufferedImage icon = a.icon(
                    ClassicColonyLayout.goodsIcon(HOUSE_GOODS[r][gd]));
                for (int q = 0; q < 2; q++) {
                    for (int x : row.x[gd][q]) {
                        if (icon != null) g.drawImage(icon, x, y, null);
                        if (q == 1 && cross != null) g.drawImage(cross, x, y, null);
                    }
                }
            }
            if (!labels) continue;
            for (int gd = 0; gd < row.x.length; gd++) {
                for (int q = 0; q < 2; q++) {
                    if (!row.labelled[gd][q]) continue;
                    label(g, c, String.valueOf(row.n[gd][q]), row.labelX[gd][q],
                          y + ClassicColonyLayout.HOUSE_LABEL_DY,
                          (q == 1) ? ClassicColonyLayout.INK_RED : ClassicColonyLayout.INK_WHITE);
                }
            }
        }
    }

    /** A count label: a black (w + 1) x 7 box, the number one pixel in, in an ink. */
    private static void label(Graphics2D g, ClassicColonyChrome.Assets c, String s, int x,
                              int y, int ink) {
        final int w = ClassicColonyChrome.textWidth(c, s);
        g.setColor(new Color(c.rgb(ClassicColonyLayout.INK_BLACK)));
        g.fillRect(x, y, w + 1, ClassicColonyLayout.HOVER_H);
        ClassicColonyChrome.text(g, c, s, x + 1, y + 1, ink);
    }

    /**
     * «Vorhandene Einheiten» and the units: the caption (ink 57 at
     * floor((514 - w)/2), glyph top 132), each unit in its cell as the map
     * draws it (its black shadow, its flag with the order letter, the
     * letter dark for F and S as on the map: V clip 019 and dago, the
     * artillery's F #AA4900, the dragoon's black), the selected one's frame
     * right after it, green, white under the cursor in its on half (V clip
     * 019 #366 white, #388 green).
     */
    private static void paintUnits(Graphics2D g, ClassicColonyChrome.Assets c,
                                   ClassicColonyBuildings.Assets a, ClassicFont tiny,
                                   Model m, boolean white) {
        if (m.unitsCaption != null) {
            ClassicColonyChrome.text(g, c, m.unitsCaption,
                ClassicColonyLayout.rightCaptionX(ClassicColonyChrome.textWidth(c, m.unitsCaption)),
                ClassicColonyLayout.CAPTION_Y, ClassicColonyLayout.INK_CAPTION);
        }
        for (Listed l : m.units) {
            final BufferedImage s = a.image(ClassicColonyBuildings.figureKey(a, l.unit, true));
            ClassicHud.paintIcon(g, tiny, l.letter, s, ClassicHud.nationRgb(l.unit.getOwner()),
                                 l.ink, ClassicHud.unitRow(l.unit), l.cell.x, l.cell.y,
                                 ClassicHud.NO_MARKER);
            if (l.unit != m.selected) continue;
            final Rectangle f = ClassicColonyLayout.unitListFrame(l.cell);
            g.setColor(new Color(c.rgb((white) ? ClassicColonyLayout.INK_WHITE
                                       : ClassicColonyLayout.INK_GREEN)));
            g.fillRect(f.x, f.y, f.width, 1);
            g.fillRect(f.x, f.y + f.height - 1, f.width, 1);
            g.fillRect(f.x, f.y, 1, f.height);
            g.fillRect(f.x + f.width - 1, f.y, 1, f.height);
        }
    }

    /**
     * The hammer panel: the build's name (ink 57 at floor((514 - w)/2),
     * glyph top 132), KAUFEN and ÄNDERN in their boxes (top row and left
     * column ink 57, bottom row and right column ink 48, the band picture
     * inside, the word white; under the cursor inverted: inside white, the
     * word black, top and left 48, bottom and right 57; V #105810 on,
     * #105830 off), the hammer rows (each row's icons left to right, the
     * rows top to bottom, V #9921), their count labels (q &gt; 41, dago),
     * then «(Benötigt …)» over their last pixel row (V).
     */
    private static void paintHammer(Graphics2D g, ClassicColonyChrome.Assets c,
                                    ClassicColonyBuildings.Assets a, Model m, Cursor cursor) {
        final Hammer h = m.hammer;
        if (h == null) return;
        ClassicColonyChrome.text(g, c, h.caption,
            ClassicColonyLayout.rightCaptionX(ClassicColonyChrome.textWidth(c, h.caption)),
            ClassicColonyLayout.CAPTION_Y, ClassicColonyLayout.INK_CAPTION);
        box(g, c, ClassicColonyLayout.BUY_BOX, m.buy, cursor == Cursor.BUY);
        box(g, c, ClassicColonyLayout.CHANGE_BOX, m.change, cursor == Cursor.CHANGE);
        final int q = h.q();
        final BufferedImage icon = a.icon(ClassicColonyLayout.ICON_HAMMERS);
        final int[] counts = new int[ClassicColonyLayout.HAMMER_ROWS];
        for (int r = 0; r < counts.length; r++) {
            counts[r] = ClassicColonyLayout.hammerRowCount(h.stored, q, r);
            final int y = ClassicColonyLayout.HAMMER_Y + r * ClassicColonyLayout.HAMMER_ROW_DY;
            for (int j = 0; j < counts[r] && icon != null; j++) {
                g.drawImage(icon, ClassicColonyLayout.HAMMER_X
                            + ClassicColonyLayout.hammerOffset(q, j), y, null);
            }
        }
        if (ClassicColonyLayout.hammerLabelled(q)) {
            for (int r = 0; r < counts.length; r++) {
                if (counts[r] <= 0) continue;
                label(g, c, String.valueOf(counts[r]), ClassicColonyLayout.HAMMER_LABEL_X,
                      ClassicColonyLayout.HAMMER_Y + r * ClassicColonyLayout.HAMMER_ROW_DY
                      + ClassicColonyLayout.HAMMER_LABEL_DY, ClassicColonyLayout.INK_WHITE);
            }
        }
        if (h.tools != null) {
            ClassicColonyChrome.text(g, c, h.tools,
                ClassicColonyLayout.rightCaptionX(ClassicColonyChrome.textWidth(c, h.tools)),
                ClassicColonyLayout.TOOLS_Y, h.toolsInk);
        }
    }

    /** A KAUFEN / ÄNDERN box, plain or under the cursor (inverted). */
    private static void box(Graphics2D g, ClassicColonyChrome.Assets c, Rectangle r,
                            String word, boolean inverted) {
        final int x0 = r.x, y0 = r.y, x1 = r.x + r.width - 1, y1 = r.y + r.height - 1;
        final int light = ClassicColonyLayout.INK_BOX_LIGHT, dark = ClassicColonyLayout.INK_CAPTION;
        if (inverted) {
            g.setColor(new Color(c.rgb(ClassicColonyLayout.INK_WHITE)));
            g.fillRect(x0 + 1, y0 + 1, r.width - 2, r.height - 2);
        }
        g.setColor(new Color(c.rgb((inverted) ? light : dark)));
        g.fillRect(x0, y0, r.width, 1);
        g.fillRect(x0, y0, 1, r.height);
        g.setColor(new Color(c.rgb((inverted) ? dark : light)));
        g.fillRect(x0 + 1, y1, r.width - 1, 1);
        g.fillRect(x1, y0 + 1, 1, r.height - 1);
        if (word != null) {
            ClassicColonyChrome.text(g, c, word, x0 + ClassicColonyLayout.BOX_TEXT_DX,
                y0 + ClassicColonyLayout.BOX_TEXT_DY,
                (inverted) ? ClassicColonyLayout.INK_BLACK : ClassicColonyLayout.INK_WHITE);
        }
    }
}
