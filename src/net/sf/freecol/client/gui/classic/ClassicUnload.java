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

import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Market;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Unit;


/**
 * Unloading and dumping cargo on the map as the original does (part O,
 * bundle A3 and A4; Roger 2026-10-13: the original everywhere).  Headless:
 * the Classic UI's U and O ({@code ClassicGUI.classicUnload}) and Shift-D
 * in a colony ({@code ClassicGUI.askDisband}) go through a {@link Cargo}.
 *
 * <p><b>U in a colony</b> (BEFEHLE «Fracht entladen», manual p. 23
 * "Unload most valuable cargo"; manual:115 "repeatedly pressing the
 * unload key eventually unloads an entire ship"): one press unloads one
 * hold, the most valuable one ({@link #mostValuable}: the hold's worth
 * at Amsterdam's bid; I: how the original weighs it is not known), into
 * the warehouse.  Before, FreeCol's U unloaded everything at once.  When
 * no goods are left, U puts the units aboard ashore as FreeCol's U did
 * (I: the manual's "cargo" are goods; whether the original's U lands
 * units is not known).
 *
 * <p><b>A full warehouse</b> (manual:115 "your advisors point this out and
 * ask for further instructions"; GAME.TXT @WAREHOUSEFULL): a hold that
 * would leave more of a good in the colony than its warehouse stores asks
 * first, with the colony, what is stored, the good, the capacity and the
 * amount ({@link #overflow}: FreeCol's own rule of what spoils at the
 * turn's end, {@code ServerColony.csNewTurnWarnings}: storable goods
 * other than food).  «Dann eben nicht.» (the bar's; no {@code @default})
 * and Escape keep the hold aboard and cost nothing (FreeCol takes a
 * carrier's moves only after a real unload, and only when it has moved);
 * «Ladet die Ware trotzdem ab.» unloads it.  Who stands at the box is
 * not known: the colonist (MSS5), the colony advisor of @CARGOREADY (V
 * playthrough-3 #109426), by its family (I).
 *
 * <p><b>O at sea</b> (BEFEHLE «Fracht über Bord werfen», manual p. 23;
 * GAME.TXT @OVERBOARD, a header without rows): the rows are the holds of
 * goods aboard, «100 Felle», «50 Werkzeuge», and «Nichts» (LABELS
 * {@code @MISC}), as the original's @TRADEWHICH lists a cargo (V
 * playthrough-3 #125075: «50 Rum» / «18 Felle» / «Nichts», no portrait,
 * the bar on the first hold); the chosen hold is thrown overboard,
 * «Nichts» and Escape keep everything.  Before, O threw every hold
 * overboard without a word.  Units aboard stay aboard either way.
 */
final class ClassicUnload {

    /** GAME.TXT's warning before an unload into a full warehouse. */
    static final String FULL_SECTION = "WAREHOUSEFULL";

    /** GAME.TXT's header of O's list. */
    static final String OVERBOARD_SECTION = "OVERBOARD";

    /** @WAREHOUSEFULL's rows: «Dann eben nicht.» (the bar's, Escape's), «Ladet die Ware trotzdem ab.». */
    static final int KEEP_ROW = 0, ANYWAY_ROW = 1;

    /** LABELS.TXT {@code @MISC} «Nichts». */
    static final int MISC_NOTHING = 32;

    /** What the screen does with the cargo: the controller in the game, a fake in the tests. */
    interface Cargo {

        /**
         * Put a box and wait for its answer.
         *
         * @param r The box.
         * @return The answer ({@code ClassicGUI.Prompter#ask}).
         */
        int ask(ClassicAdvisorBox.Request r);

        /**
         * Unload goods into the colony ({@code InGameController.unloadCargo}).
         *
         * @param goods The goods, on the carrier.
         * @return True if unloaded.
         */
        boolean unload(Goods goods);

        /**
         * Throw goods overboard ({@code InGameController.unloadCargo}
         * with dump; at sea the server dumps them).
         *
         * @param goods The goods, on the carrier.
         * @return True if dumped.
         */
        boolean dump(Goods goods);

        /**
         * Put a unit ashore ({@code InGameController.leaveShip}).
         *
         * @param unit The unit aboard.
         * @return True if it left the carrier.
         */
        boolean land(Unit unit);
    }

    private ClassicUnload() {}


    // The holds

    /**
     * The holds of goods aboard, in lots of at most 100 (FreeCol's
     * {@code getGoodsList}, the order the Europe screen draws them in).
     *
     * @param carrier The carrier, or null.
     * @return The holds.
     */
    static List<Goods> holds(Unit carrier) {
        final List<Goods> out = new ArrayList<>();
        if (carrier == null) return out;
        for (Goods g : carrier.getGoodsList()) {
            if (g.getAmount() > 0) out.add(g);
        }
        return out;
    }

    /**
     * U's hold: the most valuable one at Amsterdam's bid, the first of
     * equals (class comment); the first hold without a market.
     *
     * @param carrier The carrier.
     * @param market Our market, or null.
     * @return The hold, or null without goods.
     */
    static Goods mostValuable(Unit carrier, Market market) {
        Goods best = null;
        int worth = -1;
        for (Goods g : holds(carrier)) {
            final int w = (market == null) ? 0
                : market.getSalePrice(g.getType(), g.getAmount());
            if (w > worth) {
                best = g;
                worth = w;
            }
        }
        return best;
    }

    /**
     * What of a good would spoil at the turn's end after an unload
     * (FreeCol's rule, class comment).
     *
     * @param colony The colony.
     * @param type The good.
     * @param adding The amount unloaded.
     * @return The amount over the warehouse's capacity, 0 when it fits
     *     or the good never spoils.
     */
    static int overflow(Colony colony, GoodsType type, int adding) {
        if (colony == null || type == null || !type.isStorable()
            || type.limitIgnored()) return 0;
        return Math.max(0, colony.getGoodsCount(type) + adding
                        - colony.getWarehouseCapacity());
    }


    // The boxes

    /**
     * @WAREHOUSEFULL's values: {@code %STRING0} the colony,
     * {@code %NUMBER0} what is stored, {@code %STRING1} the good (NAMES
     * {@code @CARGO}), {@code %NUMBER1} the capacity, {@code %NUMBER2}
     * the amount unloaded.
     *
     * @param t The original texts, or null.
     * @param colony The colony.
     * @param goods The hold.
     * @return The values.
     */
    static Map<String, String> fullValues(ClassicText t, Colony colony, Goods goods) {
        final Map<String, String> v = new HashMap<>();
        v.put("STRING0", colony.getName());
        v.put("NUMBER0", Integer.toString(colony.getGoodsCount(goods.getType())));
        v.put("STRING1", ClassicTrade.goodsName(t, goods.getType()));
        v.put("NUMBER1", Integer.toString(colony.getWarehouseCapacity()));
        v.put("NUMBER2", Integer.toString(goods.getAmount()));
        return v;
    }

    /**
     * @WAREHOUSEFULL (class comment): GAME.TXT's two rows, the bar and
     * Escape on «Dann eben nicht.», the colonist (I).  Without the pack:
     * FreeCol's trade route question with a cancel row first.
     *
     * @param t The original texts, or null.
     * @param colony The colony.
     * @param goods The hold.
     * @param title The stopgap's window title.
     * @return The box.
     */
    static ClassicAdvisorBox.Request fullRequest(ClassicText t, Colony colony,
                                                 Goods goods, String title) {
        final ClassicText.Message m = (t == null) ? null : t.message(FULL_SECTION);
        ClassicAdvisorBox.Builder b = (m == null || m.options.size() < 2) ? null
            : ClassicAdvisorBox.fromGameText(FULL_SECTION, m, fullValues(t, colony, goods));
        if (b == null) {
            final Unit carrier = (goods.getLocation() instanceof Unit)
                ? (Unit) goods.getLocation() : null;
            final StringTemplate q = StringTemplate.template("traderoute.warehouseCapacity")
                .addStringTemplate("%unit%", (carrier == null)
                    ? StringTemplate.name("") : carrier.getLabel())
                .addName("%colony%", colony.getName())
                .addAmount("%amount%", overflow(colony, goods.getType(), goods.getAmount()))
                .addNamed("%goods%", goods.getType());
            b = ClassicAdvisorBox.Request.builder(FULL_SECTION)
                .freeColText(Messages.message(q))
                .rows(ClassicAdvisorBox.literal(Messages.message("cancel")),
                      ClassicAdvisorBox.literal(Messages.message("classic.warehouseFull.unload")));
        }
        return b.defaultRow(KEEP_ROW).cancelRow(KEEP_ROW)
            .portrait(ClassicAdvisorBox.Portrait.COLONIST).stopgap(title, null).build();
    }

    /**
     * @param answer @WAREHOUSEFULL's answer.
     * @return Whether to unload anyway.
     */
    static boolean unloadsAnyway(int answer) {
        return answer == ANYWAY_ROW;
    }

    /**
     * A hold's row in O's list: «100 Felle» (the amount, NAMES
     * {@code @CARGO}; V @TRADEWHICH «50 Rum»).
     *
     * @param t The original texts, or null (FreeCol's name).
     * @param g The hold.
     * @return The row.
     */
    static String holdRow(ClassicText t, Goods g) {
        return g.getAmount() + " " + ClassicTrade.goodsName(t, g.getType());
    }

    /**
     * O's list (class comment): @OVERBOARD's header, a row per hold and
     * «Nichts» last, the bar on the first hold, Escape on «Nichts», no
     * portrait.  Without the pack the same rows in FreeCol's words.
     *
     * @param t The original texts, or null.
     * @param holds The holds aboard ({@link #holds}), not empty.
     * @param title The stopgap's window title.
     * @return The box.
     */
    static ClassicAdvisorBox.Request overboardRequest(ClassicText t, List<Goods> holds,
                                                     String title) {
        final ClassicText.Message m = (t == null) ? null : t.message(OVERBOARD_SECTION);
        final List<String> rows = new ArrayList<>();
        for (Goods g : holds) rows.add(ClassicAdvisorBox.literal(holdRow(t, g)));
        final String nothing = (t == null) ? null : t.misc(MISC_NOTHING);
        ClassicAdvisorBox.Builder b = ClassicAdvisorBox.fromGameText(OVERBOARD_SECTION, m,
                                                                     new HashMap<>());
        if (b == null || nothing == null) {
            b = ClassicAdvisorBox.Request.builder(OVERBOARD_SECTION)
                .freeColText(Messages.message("classic.overboard.text"));
            rows.add(ClassicAdvisorBox.literal(Messages.message("classic.overboard.nothing")));
        } else {
            rows.add(ClassicAdvisorBox.literal(nothing.trim()));
        }
        return b.rows(rows).defaultRow(0).cancelRow(rows.size() - 1)
            .portrait(ClassicAdvisorBox.Portrait.NONE).stopgap(title, null).build();
    }


    // The orders

    /**
     * U in our colony, one press (class comment): the most valuable hold,
     * with @WAREHOUSEFULL first when it would overflow; with no goods
     * aboard, the units ashore.
     *
     * @param carrier The carrier in the colony.
     * @param colony The colony.
     * @param market Our market, or null.
     * @param t The original texts, or null.
     * @param title The stopgap's window title.
     * @param c The cargo's controller.
     * @return What happened, for the recorder.
     */
    static String unloadOne(Unit carrier, Colony colony, Market market, ClassicText t,
                            String title, Cargo c) {
        if (carrier == null || colony == null || c == null) return "nothing";
        final Goods g = mostValuable(carrier, market);
        if (g != null) return unloadHold(carrier, colony, g, t, title, c);
        int landed = 0;
        for (Unit u : new ArrayList<>(carrier.getUnitList())) {
            if (c.land(u)) landed++;
        }
        return "landed " + landed;
    }

    /**
     * One hold into the colony, asking @WAREHOUSEFULL first when it would
     * overflow.
     *
     * @return What happened, for the recorder.
     */
    private static String unloadHold(Unit carrier, Colony colony, Goods g, ClassicText t,
                                     String title, Cargo c) {
        final String what = g.getAmount() + " " + g.getType().getSuffix();
        if (overflow(colony, g.getType(), g.getAmount()) > 0) {
            final int a = c.ask(fullRequest(t, colony, g, title));
            if (!unloadsAnyway(a)) return what + " kept aboard (" + FULL_SECTION
                + " chosen=" + a + ")";
            return what + ((c.unload(g)) ? " unloaded anyway" : " refused");
        }
        return what + ((c.unload(g)) ? " unloaded" : " refused");
    }

    /**
     * Everything aboard ashore in our colony, the units first, then each
     * good (all its holds, one question) with @WAREHOUSEFULL when it
     * would overflow (Shift-D in a colony,
     * part O A5: before, the units and the goods aboard died with the
     * ship).
     *
     * @param carrier The carrier in the colony.
     * @param colony The colony.
     * @param t The original texts, or null.
     * @param title The stopgap's window title.
     * @param c The cargo's controller.
     * @return True if nothing is left aboard.
     */
    static boolean unloadAll(Unit carrier, Colony colony, ClassicText t, String title,
                             Cargo c) {
        if (carrier == null || colony == null || c == null) return false;
        for (Unit u : new ArrayList<>(carrier.getUnitList())) c.land(u);
        // One question per good, not per hold.
        for (Goods g : carrier.getCompactGoodsList()) {
            if (g.getAmount() > 0) unloadHold(carrier, colony, g, t, title, c);
        }
        return carrier.getUnitCount() == 0 && !carrier.hasGoodsCargo();
    }

    /**
     * O outside a colony (class comment): the list, the chosen hold
     * thrown overboard.
     *
     * @param carrier The carrier.
     * @param t The original texts, or null.
     * @param title The stopgap's window title.
     * @param c The cargo's controller.
     * @return What happened, for the recorder.
     */
    static String overboard(Unit carrier, ClassicText t, String title, Cargo c) {
        final List<Goods> holds = holds(carrier);
        if (holds.isEmpty() || c == null) return "no goods aboard";
        final int a = c.ask(overboardRequest(t, holds, title));
        if (a < 0 || a >= holds.size()) return "nothing (" + a + ")";
        final Goods g = holds.get(a);
        return g.getAmount() + " " + g.getType().getSuffix()
            + ((c.dump(g)) ? " overboard" : " refused");
    }
}
