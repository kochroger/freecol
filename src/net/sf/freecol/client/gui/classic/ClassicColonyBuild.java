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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.freecol.common.model.Ability;
import net.sf.freecol.common.model.AbstractGoods;
import net.sf.freecol.common.model.BuildableType;
import net.sf.freecol.common.model.Building;
import net.sf.freecol.common.model.BuildingType;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Role;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.common.option.GameOptions;


/**
 * The colony screen's <b>building</b> (build spec M4.10,
 * {@code playthrough-3-analysis/11-spec-M4-colony.md}; C §3, O §8-§9,
 * dago-colony2 {@code 01-buildlist.md}, clip008 #11142): what ÄNDERN's
 * build list shows, what KAUFEN costs and asks, and which build is the
 * current one after a completion.  Headless: the panel asks the boxes and
 * makes the controller's calls.
 *
 * <ul>
 *   <li><b>The build list</b> (V ×5 in clip 020, 5 lists in dago-colony2,
 *       0 px): «Objekt zum Bauen wählen» (LABELS {@code @CTITLE} 4), the
 *       row «(Keine Produktion)», then the buildings the colony can build
 *       in NAMES {@code @BUILDING} order, then the units (Artillerie,
 *       Wagenzug, the ships: NAMES {@code @UNIT} order); the names upper
 *       case with a small «ä» ({@link ClassicText#upperAscii}); the right
 *       column «(n Hämmer)» and «(m Werkzeuge)» with n = max(0, cost -
 *       stored), m the full tools, no blank between; the current build
 *       yellow with the bar on it; the footer «(F1 für Hilfe)»; w =
 *       max(name + cost) + 26, h = 30 + 8·rows, centred.  The bar moves on
 *       a press, the release takes the row (the list box's).</li>
 *   <li><b>A completed building stays the current build</b> (levi rule
 *       R4, {@code model.option.originalBuildCompletion}; V #113, #159,
 *       #229): its row «NAME (ABGESCHLOSSEN)» (LABELS 142 in brackets)
 *       in its NAMES place, yellow, with the bar; the right panel keeps its
 *       name and its «(Benötigt …)» line; a list after another choice no
 *       longer has it.  A finished unit stays current and is built again
 *       («WAGENZUG (4 Hämmer)», V #276: FreeCol's queue of one unit does
 *       the same).  The stored hammers carry over (V #131120).</li>
 *   <li><b>KAUFEN</b> (V #94078; dago-colony2): the gold short of the
 *       price, {@code @BUYME0} with MSS5 and no rows («Kosten für
 *       Abschluß von Lagerhaus: 494¤.» / «Schatzkammer: 348¤.»), closed
 *       by any key or click (I); enough gold, {@code @BUYME1} with «Na,
 *       dann nicht.» (the bar, Escape) / «Trotzdem abschließen.».  The
 *       price is the colony's ({@code Colony.priceGoodsForBuilding}, the
 *       server's too): in levi 13 per missing hammer and 6 per missing
 *       tool, doubled while no hammer is stored (rule R1, part X,
 *       {@code model.option.originalBuildPrice}; V all 23 boxes of
 *       playthrough 4, e.g. 048 #70208 4760 with the hammer area
 *       empty).</li>
 * </ul>
 */
final class ClassicColonyBuild {

    private ClassicColonyBuild() {}   // static helpers only

    /** LABELS {@code @CTITLE}: «Objekt zum Bauen wählen» (LABELS.TXT:259). */
    static final int CTITLE_PICK = 4;

    /** LABELS {@code @MISC}: «ABGESCHLOSSEN» (LABELS.TXT:142). */
    static final int MISC_DONE = 126;

    /** GAME.TXT's KAUFEN boxes: too little gold, enough gold. */
    static final String BUY_SHORT = "BUYME0", BUY = "BUYME1";

    /** @BUYME1's rows: «Na, dann nicht.» (the bar, Escape), «Trotzdem abschließen.». */
    static final int BUY_NO = 0, BUY_YES = 1;

    /**
     * The list after ÄNDERN's release: 6 frames (V dago-colony2 #2360 -&gt;
     * #2366, the first clip +5/+6, clip008 +6).
     */
    static final int LIST_FRAMES = 6;

    /** NAMES {@code @BUILDING} rows of the two Lagerhaus families' second buildings. */
    private static final int NAMES_EXPANSION = 16, NAMES_STABLES = 17;


    // The current build

    /**
     * Whether the levi rule R4 holds ({@code model.option.originalBuildCompletion}).
     *
     * @param colony The colony.
     * @return True if so; false for rules without the option.
     */
    static boolean originalCompletion(Colony colony) {
        final Specification spec = colony.getSpecification();
        try {
            return spec != null && spec.getBoolean(GameOptions.ORIGINAL_BUILD_COMPLETION);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * The completed building that is still the current build (rule R4):
     * the server's record, while nothing else is queued and the colony has
     * that building.
     *
     * @param colony The colony.
     * @return Its type, or null.
     */
    static BuildingType completed(Colony colony) {
        if (!originalCompletion(colony)) return null;
        final String id = colony.getClassicBuilt();
        if (id == null || !colony.getBuildQueue().isEmpty()) return null;
        BuildingType bt;
        try {
            bt = colony.getSpecification().getBuildingType(id);
        } catch (RuntimeException e) {
            return null;
        }
        final Building b = (bt == null) ? null : colony.getBuilding(bt);
        return (b != null && b.getType() == bt) ? bt : null;
    }

    /**
     * Whether the colony's building was completed in this turn (levi rule
     * R4's record of the turn): an opening by a click then blinks ÄNDERN
     * (fixer A after part X, {@code ClassicColonyPanel.opened}).
     *
     * @param colony The colony.
     * @return True if so.
     */
    static boolean completedThisTurn(Colony colony) {
        return completed(colony) != null && colony.getGame() != null
            && colony.getGame().getTurn() != null
            && colony.getClassicBuiltTurn() == colony.getGame().getTurn().getNumber();
    }

    /**
     * The current build as the original shows it: the queue's first, else
     * the completed building ({@link #completed}).
     *
     * @param colony The colony.
     * @return It, or null for none.
     */
    static BuildableType current(Colony colony) {
        final BuildableType b = colony.getCurrentlyBuilding();
        return (b != null) ? b : completed(colony);
    }

    // The build list

    /** One row of the build list. */
    static final class Row {

        /** The build, or null for «(Keine Produktion)». */
        final BuildableType type;

        /** The name (upper case) and the right column (null for none). */
        final String name, cost;

        /** Whether it is the current build (yellow, the bar). */
        final boolean current;

        /** Whether it is the completed building («(ABGESCHLOSSEN)»). */
        final boolean done;

        Row(BuildableType type, String name, String cost, boolean current, boolean done) {
            this.type = type;
            this.name = name;
            this.cost = cost;
            this.current = current;
            this.done = done;
        }

        @Override
        public String toString() {
            return this.name + ((this.cost == null) ? "" : " " + this.cost)
                + ((this.current) ? "*" : "");
        }
    }

    /**
     * A building type's NAMES {@code @BUILDING} row (its sprite frame's
     * row; the warehouse expansion 16, the stables 17), or a large number
     * for one the original lacks.
     *
     * @param bt The type.
     * @return The row.
     */
    static int namesRow(BuildingType bt) {
        final String id = ClassicColonyBuildings.shortId(bt.getId());
        if ("warehouseExpansion".equals(id)) return NAMES_EXPANSION;
        if ("stables".equals(id)) return NAMES_STABLES;
        final Integer f = ClassicColonyBuildings.frameOf(id);
        return (f == null) ? 1000 : f;
    }

    /**
     * A unit type's NAMES {@code @UNIT} row, or a large number.
     *
     * @param ut The type.
     * @return The row.
     */
    static int namesRow(UnitType ut) {
        final int r = ClassicHud.unitRow(Role.getRoleIdSuffix(ut.getId()), null);
        return (r < 0) ? 1000 : r;
    }

    /**
     * The build list's rows of a colony now (class comment).
     *
     * @param colony The colony.
     * @param t The pack's texts, or null.
     * @return The rows, «(Keine Produktion)» first.
     */
    static List<Row> rows(Colony colony, ClassicText t) {
        final Specification spec = colony.getSpecification();
        final BuildableType building = colony.getCurrentlyBuilding();
        final BuildingType done = completed(colony);
        final List<Row> out = new ArrayList<>();
        final String none = (t == null) ? null : t.label("CTITLE", ClassicColonyRight.CTITLE_NOTHING);
        out.add(new Row(null, (none == null) ? "(Keine Produktion)" : none.trim(), null,
                        building == null && done == null, false));
        final List<BuildingType> buildings = new ArrayList<>();
        for (BuildingType bt : spec.getBuildingTypeList()) {
            if (bt == done || colony.canBuild(bt)) buildings.add(bt);
        }
        buildings.sort((x, y) -> Integer.compare(namesRow(x), namesRow(y)));
        // The units a colony builds with hammers (Artillerie, Wagenzug, the
        // ships), not the colonists FreeCol makes from food (the list has
        // none, V).
        final List<UnitType> units = new ArrayList<>();
        for (UnitType ut : colony.getBuildableUnits()) {
            if (!ut.hasAbility(Ability.PERSON)) units.add(ut);
        }
        units.sort((x, y) -> Integer.compare(namesRow(x), namesRow(y)));
        final List<BuildableType> all = new ArrayList<>(buildings);
        all.addAll(units);
        final String doneWord = (t == null) ? null : t.misc(MISC_DONE);
        for (BuildableType b : all) {
            final String name = ClassicText.upperAscii(ClassicColonyRight.buildName(t, b));
            if (b == done) {
                out.add(new Row(b, name, "(" + ((doneWord == null) ? "ABGESCHLOSSEN"
                                                : doneWord.trim()) + ")", true, true));
            } else {
                out.add(new Row(b, name, cost(colony, t, b), b == building, false));
            }
        }
        return out;
    }

    /**
     * A row's right column: «(n Hämmer)» with n = max(0, cost - stored),
     * then «(m Werkzeuge)» with the full tools when it needs any, no blank
     * between (V dago-colony2 §2.3, clip 020 #229).
     *
     * @param colony The colony.
     * @param t The pack's texts, or null.
     * @param b The build.
     * @return The cell.
     */
    static String cost(Colony colony, ClassicText t, BuildableType b) {
        final Specification spec = colony.getSpecification();
        final GoodsType hammers = spec.getGoodsType("model.goods.hammers");
        final GoodsType tools = spec.getGoodsType("model.goods.tools");
        int h = 0, w = 0;
        for (AbstractGoods ag : b.getRequiredGoodsList()) {
            if (ag.getType() == hammers) h += ag.getAmount();
            if (ag.getType() == tools) w += ag.getAmount();
        }
        final int stored = (hammers == null) ? 0 : colony.getGoodsCount(hammers);
        final StringBuilder sb = new StringBuilder("(").append(Math.max(0, h - stored))
            .append(' ').append(goods(t, hammers, "Hämmer")).append(')');
        if (w > 0) sb.append('(').append(w).append(' ').append(goods(t, tools, "Werkzeuge"))
                         .append(')');
        return sb.toString();
    }

    /** A good's NAMES name, or a fallback. */
    private static String goods(ClassicText t, GoodsType gt, String dflt) {
        if (gt == null) return dflt;
        final String s = ClassicTrade.goodsName(t, gt);
        return (s == null || s.isEmpty()) ? dflt : s;
    }

    /**
     * The build list's box width (V dago-colony2 §2.1): w = max over the
     * rows of (name + cost) + 26 (196 at clip008 #11142, 210 with
     * «LAGERHAUSERWEITERUNG (16 Hämmer)(20 Werkzeuge)»).
     *
     * @param tiny FONTTINY, or null (then 196).
     * @param rows The rows.
     * @return The box's width.
     */
    static int boxWidth(ClassicFont tiny, List<Row> rows) {
        if (tiny == null) return 196;
        int w = 0;
        for (Row r : rows) {
            w = Math.max(w, tiny.stringWidth(r.name)
                         + ((r.cost == null) ? 0 : tiny.stringWidth(r.cost)));
        }
        return w + 26;
    }

    /**
     * The build queue after a pick in the list: the picked build replaces
     * the current one, the rest of an old queue (FreeCol's build-queue
     * screen, before part V) stays behind it (a building picked not twice; the review
     * of part V, play L4); «(Keine Produktion)» empties it (nothing is
     * built, as the player asked).
     *
     * @param queue The queue now.
     * @param pick The build picked, or null for «(Keine Produktion)».
     * @return The new queue.
     */
    static List<BuildableType> picked(List<BuildableType> queue, BuildableType pick) {
        final List<BuildableType> out = new ArrayList<>();
        if (pick == null) return out;
        out.add(pick);
        if (queue != null) {
            for (int i = 1; i < queue.size(); i++) {
                final BuildableType b = queue.get(i);
                if (b != null && !(b == pick && b instanceof BuildingType)) out.add(b);
            }
        }
        return out;
    }

    /**
     * @param rows The rows.
     * @return The bar's row: the current build's, else row 0.
     */
    static int barRow(List<Row> rows) {
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).current) return i;
        return 0;
    }

    /**
     * The build list's box (class comment): the title, the rows and their
     * costs, the current one yellow under the bar, the footer; Escape
     * changes nothing.
     *
     * @param t The pack's texts, or null.
     * @param tiny FONTTINY, or null.
     * @param rows The rows.
     * @param delayMs When it comes after now.
     * @return The box.
     */
    static ClassicAdvisorBox.Request request(ClassicText t, ClassicFont tiny, List<Row> rows,
                                            double delayMs) {
        return request(t, tiny, rows, delayMs, -1.0, null);
    }

    /**
     * {@link #request(ClassicText, ClassicFont, List, double)} with F1 and
     * the right-click on a building's row (fixer A after part X: V clip 046
     * #71516 -&gt; #71579, «SÄGEWERK»'s page; the build stays): the hook
     * is told the row, the others' rows have no help (I: a unit's page is
     * not built).
     *
     * @param t The pack's texts, or null.
     * @param tiny FONTTINY, or null.
     * @param rows The rows.
     * @param delayMs When it comes after now.
     * @param chainMs The least time after the box before (the page), or
     *     negative for the usual.
     * @param helpRow Told the row of F1 or the right-click, or null for no help.
     * @return The box.
     */
    static ClassicAdvisorBox.Request request(ClassicText t, ClassicFont tiny, List<Row> rows,
                                            double delayMs, double chainMs,
                                            java.util.function.IntConsumer helpRow) {
        final String title = (t == null) ? null : t.label("CTITLE", CTITLE_PICK);
        final List<String> names = new ArrayList<>(), costs = new ArrayList<>();
        final boolean[] yellow = new boolean[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            names.add(ClassicAdvisorBox.literal(rows.get(i).name));
            costs.add((rows.get(i).cost == null) ? null
                      : ClassicAdvisorBox.literal(rows.get(i).cost));
            yellow[i] = rows.get(i).current;
        }
        final String footer = (t == null) ? null : t.misc(ClassicFathers.MISC_F1_HELP);
        final ClassicAdvisorBox.Builder b = ClassicAdvisorBox.Request.builder("buildlist")
            .freeColText((title == null) ? "Objekt zum Bauen wählen" : title.trim())
            .width(boxWidth(tiny, rows) - 6).rows(names).right(costs).current(yellow)
            .footer((footer == null) ? null : footer.trim()).defaultRow(barRow(rows))
            .noCancelRow().openDelay(delayMs).chain(chainMs).list()
            .stopgap("Objekt zum Bauen wählen", null);
        if (helpRow != null) {
            b.help(new ClassicAdvisorBox.Help() {
                    @Override
                    public void help(int row) {
                        helpRow.accept(row);
                    }

                    @Override
                    public boolean covers(int row) {
                        return row >= 0 && row < rows.size() && hasPage(rows.get(row));
                    }
                });
        }
        return b.build();
    }

    /**
     * Whether a row has a Colonopedia page: a building type of the
     * original's ({@link ClassicPedia#buildingRow}).
     *
     * @param r The row.
     * @return True if so.
     */
    static boolean hasPage(Row r) {
        return r != null && r.type instanceof BuildingType
            && ClassicPedia.buildingRow((BuildingType) r.type) >= 0;
    }


    // KAUFEN

    /**
     * What KAUFEN costs now: the colony's price for the missing goods of
     * its build (levi rule R1 inside {@code Colony.priceGoodsForBuilding}),
     * or -1 when there is nothing to buy (no build, the completed building,
     * everything there) or a missing good cannot be bought in Europe (a
     * boycott: the review of part V, play L1; the server refuses it too;
     * I: what the original does is not seen).
     *
     * @param colony The colony.
     * @return The price, or -1.
     */
    static int price(Colony colony) {
        final BuildableType b = colony.getCurrentlyBuilding();
        if (b == null || boycotted(colony) != null) return -1;
        final int p = colony.getPriceForBuilding(b);
        return (p <= 0) ? -1 : p;
    }

    /**
     * A missing good of the colony's build that cannot be bought in Europe
     * now (Parliament's boycott, {@code Player.canTrade}).
     *
     * @param colony The colony.
     * @return The good, or null.
     */
    static GoodsType boycotted(Colony colony) {
        final BuildableType b = colony.getCurrentlyBuilding();
        final net.sf.freecol.common.model.Player owner = colony.getOwner();
        if (b == null || owner == null) return null;
        for (AbstractGoods ag : colony.getRequiredGoods(b)) {
            final GoodsType gt = ag.getType();
            if (gt.isStorable() && !owner.canTrade(gt, net.sf.freecol.common.model.Market.Access.EUROPE)) {
                return gt;
            }
        }
        return null;
    }

    /** KAUFEN's box comes 8 frames after the release (V dago-colony2 #12053 -&gt; #12061, #12904 -&gt; #12912). */
    static final int BUY_BOX_FRAMES = 8;

    /**
     * KAUFEN's box (class comment): {@code @BUYME0} with the gold short,
     * else {@code @BUYME1}; MSS5 over it.
     *
     * @param t The pack's texts.
     * @param buildName The build's name (NAMES, as the hammer panel's).
     * @param price The price.
     * @param gold The treasury.
     * @return The box, or null without the texts.
     */
    static ClassicAdvisorBox.Request buyRequest(ClassicText t, String buildName, int price,
                                               int gold) {
        final boolean shortOf = gold < price;
        final String section = (shortOf) ? BUY_SHORT : BUY;
        final Map<String, String> values = new HashMap<>();
        values.put("STRING0", buildName);
        values.put("NUMBER0", Integer.toString(price));
        values.put("NUMBER1", Integer.toString(gold));
        final ClassicAdvisorBox.Builder b = ClassicAdvisorBox.fromGameText(section,
            (t == null) ? null : t.message(section), values);
        if (b == null) return null;
        b.portrait(ClassicAdvisorBox.Portrait.COLONIST).stopgap(buildName, null)
            .openDelay(ClassicColonyLayout.frames(BUY_BOX_FRAMES));
        // Escape is «Na, dann nicht.», never the purchase (as part O's rule).
        if (!shortOf) b.cancelRow(BUY_NO);
        return b.build();
    }
}
