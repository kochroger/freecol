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

import java.util.HashMap;
import java.util.Map;

import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.GoodsContainer;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Market;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Unit;


/**
 * Buying and selling a part of a hold in Europe (gap list B1; dialog
 * catalogue C09, C19; 03-europe-ships sections 2.10 and 2.11).  Headless:
 * the Europe screen ({@link ClassicEuropePanel}) asks the boxes and the
 * controller through a {@link Trader}.
 *
 * <p>What the original does (V, playthrough-1, Roger's 2nd Europe visit,
 * 144 gold, tools at 2):
 * <ul>
 *   <li>A plain drop of a good on the ship buys 100 (or the hold's free
 *       space).  One the gold cannot pay buys nothing: the red line
 *       "Werkzeuge (200) zu teuer!" (W22, not built) and, 38 frames after
 *       the drop, {@code @TUTORIAL18} (once per game, with Tutortips;
 *       {@link ClassicTips#PART}).</li>
 *   <li>A drop with Shift held brings {@code @HOWMUCH4} 27 frames later:
 *       "Wieviel {Werkzeuge} (zu {2$}) kaufen und auf Handelsschiff laden
 *       (0-100)?", the field "Menge:" with its preset "100" selected.
 *       The "(0-100)" and the preset are the hold's free space, not what
 *       the gold allows (72).  Enter buys the number; then N7's line and
 *       receipt (W22, not built).</li>
 * </ul>
 * Our Europe screen drags as the original does (N1): a market slot
 * dropped on a ship in port or on the selected ship's holds is the drop,
 * with Shift held the Shift-drop; a hold dropped on the market row sells
 * it all, with Shift {@code @HOWMUCH5}.  Both boxes open with "100" in
 * the field whatever their "(0-max)" ({@link #PRESET}): {@code @HOWMUCH5}
 * as the original (V clip 020 #24920: "(0-24)" with "100" in the field),
 * {@code @HOWMUCH4} as Roger asked (2026-10-09: "Soll zuerst 100 im Feld
 * stehen? Ja"; also over the rest of a partly filled hold, e.g. "(0-70)",
 * the review of part N, fidelity lens; I: no clip shows a partly filled
 * hold).  Enter on "100" over a smaller "(0-max)" takes the max: all of
 * the hold sold, the rest of the hold bought.  Escape, 0 or an
 * empty field: nothing, it costs nothing (Roger).  A number the gold
 * cannot pay, the preset included: nothing (the original's red line,
 * W22, not built).
 */
final class ClassicTrade {

    /** {@code @HOWMUCH4}: the Shift-drop to the box (V: #54317 -&gt; #54344, 27 frames). */
    static final double HOWMUCH_MS = 27 * ClassicAdvisorLayer.FRAME_MS;

    /** {@code @TUTORIAL18}: the drop to the tip (V: #52751 -&gt; #52789, 38 frames). */
    static final double TIP_MS = 38 * ClassicAdvisorLayer.FRAME_MS;

    /** GAME.TXT's amount boxes: buying in Europe, selling there. */
    static final String BUY_SECTION = "HOWMUCH4", SELL_SECTION = "HOWMUCH5";

    /**
     * The amount boxes' preset: "100", also over a smaller "(0-max)" (V
     * clip 020 #24920: @HOWMUCH5 "(0-24)" with "100" in the field; Roger
     * 2026-10-09 for @HOWMUCH4).
     */
    static final int PRESET = GoodsContainer.CARGO_SIZE;

    /** {@code @HOWMUCH5}'s preset ({@link #PRESET}). */
    static final int SELL_PRESET = PRESET;

    /** {@code @HOWMUCH4}'s preset ({@link #PRESET}). */
    static final int BUY_PRESET = PRESET;

    /** What a trade needs from the screen. */
    interface Trader {

        /**
         * Ask an amount in a {@code @HOWMUCH} box and wait.
         *
         * @param buying True for {@code @HOWMUCH4}, false for {@code @HOWMUCH5}.
         * @param type The goods.
         * @param max The box's "(0-max)".
         * @param preset The field's preset.
         * @return The number taken by Enter (0..max), or -1 (Escape, no box).
         */
        int ask(boolean buying, GoodsType type, int max, int preset);

        /**
         * Buy goods onto the ship ({@code InGameController.buyGoods}).
         *
         * @param type The goods.
         * @param amount The amount.
         * @param ship The ship.
         * @return True if bought.
         */
        boolean buy(GoodsType type, int amount, Unit ship);

        /**
         * Sell goods off their ship ({@code InGameController.unloadCargo},
         * which sells in Europe).
         *
         * @param goods The goods, their amount the amount sold.
         * @return True if sold.
         */
        boolean sell(Goods goods);

        /**
         * A plain buy the gold cannot pay: {@code @TUTORIAL18} once per
         * game with Tutortips, else nothing.
         *
         * @param type The goods.
         */
        void cannotPay(GoodsType type);
    }

    private ClassicTrade() {}

    /**
     * @param ship The ship, or null.
     * @param type The goods, or null.
     * @return What one drop loads: the free space for the goods in the
     *     hold, at most one hold (100); 0 for none.
     */
    static int hold(Unit ship, GoodsType type) {
        if (ship == null || type == null) return 0;
        return Math.max(0, Math.min(GoodsContainer.CARGO_SIZE,
                                    ship.getLoadableAmount(type)));
    }

    /**
     * @param p The player.
     * @param type The goods.
     * @param max The most wanted.
     * @return The most of {@code max} the gold pays at the market's price
     *     (FreeCol's check, {@code askLoadGoods}); 0 without a market.
     */
    static int affordable(Player p, GoodsType type, int max) {
        final Market m = (p == null) ? null : p.getMarket();
        if (m == null || type == null) return 0;
        int n = Math.max(0, max);
        while (n > 0 && !p.checkGold(m.getBidPrice(type, n))) n--;
        return n;
    }

    /**
     * A market slot dropped on a ship in port or its holds (class
     * comment).
     *
     * @param p Our player.
     * @param ship The ship in port.
     * @param type The goods clicked.
     * @param shift Whether Shift was held.
     * @param t The screen.
     * @return The amount bought (0: nothing).
     */
    static int buy(Player p, Unit ship, GoodsType type, boolean shift, Trader t) {
        if (p == null || ship == null || type == null || t == null
            || !ship.isNaval() || !p.canTrade(type)) return 0;
        final int max = hold(ship, type);
        if (max <= 0) return 0;
        int n = max;
        if (shift) {
            n = t.ask(true, type, max, BUY_PRESET);   // "100", also over "(0-70)"
            if (n <= 0) return 0;              // Escape, 0, empty: nothing
            n = Math.min(n, max);              // Enter on "100": all that fits
        }
        if (affordable(p, type, n) < n) {
            // Nothing is bought: the original's red line (W22) and, after
            // a plain drop, @TUTORIAL18.
            if (!shift) t.cannotPay(type);
            return 0;
        }
        return (t.buy(type, n, ship)) ? n : 0;
    }

    /**
     * A hold of the selected ship dropped on the market row: all of it
     * is sold; with Shift, {@code @HOWMUCH5} asks how much (preset
     * {@link #SELL_PRESET}; Enter on it sells all of it).
     *
     * @param p Our player.
     * @param goods The goods in the hold (located on the ship).
     * @param shift Whether Shift was held.
     * @param t The screen.
     * @return The amount sold (0: nothing).
     */
    static int sell(Player p, Goods goods, boolean shift, Trader t) {
        if (goods == null || goods.getType() == null || goods.getAmount() <= 0
            || t == null) return 0;
        if (!shift) return (t.sell(goods)) ? goods.getAmount() : 0;
        if (p != null && !p.canTrade(goods.getType())) return 0;
        final int max = goods.getAmount();
        final int n = Math.min(max, t.ask(false, goods.getType(), max, SELL_PRESET));
        if (n <= 0) return 0;                  // Escape, 0, empty: nothing
        final Goods part = (n == max) ? goods
            : new Goods(goods.getGame(), goods.getLocation(), goods.getType(), n);
        return (t.sell(part)) ? n : 0;
    }

    /**
     * @param t The original texts, or null.
     * @param type The goods.
     * @return NAMES.TXT {@code @CARGO}'s name ("Werkzeuge"), else FreeCol's.
     */
    static String goodsName(ClassicText t, GoodsType type) {
        final String s = ClassicTips.cargoName(t, ClassicTips.cargoRow(type.getId()));
        return (s != null) ? s : Messages.getName(type);
    }

    /**
     * {@code @HOWMUCH4}'s values: {@code %STRING0} the goods,
     * {@code %NUMBER1} the price of one, {@code %STRING1} the ship
     * ("Handelsschiff"), {@code %NUMBER0} the "(0-max)" (V #54344).
     *
     * @param t The original texts, or null.
     * @param type The goods.
     * @param price The price of one.
     * @param ship The ship.
     * @param max The largest amount.
     * @return The values.
     */
    static Map<String, String> buyValues(ClassicText t, GoodsType type, int price,
                                         Unit ship, int max) {
        final Map<String, String> v = new HashMap<>();
        v.put("STRING0", goodsName(t, type));
        v.put("NUMBER1", Integer.toString(price));
        String s = (ship == null) ? null : ClassicTips.unitName(t, ship);
        if (s == null && ship != null) s = Messages.getName(ship.getType());
        if (s != null) v.put("STRING1", s);
        v.put("NUMBER0", Integer.toString(max));
        return v;
    }

    /**
     * {@code @HOWMUCH5}'s values (I, never seen): {@code %STRING0} the
     * goods, {@code %NUMBER1} what one fetches, {@code %STRING2} the home
     * port ("Amsterdam"), {@code %NUMBER0} the "(0-max)".
     *
     * @param t The original texts, or null.
     * @param type The goods.
     * @param price What one fetches.
     * @param p Our player.
     * @param max The largest amount.
     * @return The values.
     */
    static Map<String, String> sellValues(ClassicText t, GoodsType type, int price,
                                          Player p, int max) {
        final Map<String, String> v = new HashMap<>();
        v.put("STRING0", goodsName(t, type));
        v.put("NUMBER1", Integer.toString(price));
        String port = (p == null) ? null : ClassicTips.homePort(t, p);
        if (port == null && p != null && p.getEurope() != null) {
            port = Messages.message(p.getEurope().getNameKey());
        }
        if (port != null) v.put("STRING2", port);
        v.put("NUMBER0", Integer.toString(max));
        return v;
    }

    /**
     * {@code @TUTORIAL18}'s values (V #53000): {@code %STRING0} the goods,
     * {@code %NUMBER0} the price of one, {@code %NUMBER1} our gold.
     *
     * @param t The original texts, or null.
     * @param type The goods.
     * @param price The price of one.
     * @param gold Our gold.
     * @return The values.
     */
    static Map<String, String> tipValues(ClassicText t, GoodsType type, int price,
                                         int gold) {
        final Map<String, String> v = new HashMap<>();
        v.put("STRING0", goodsName(t, type));
        v.put("NUMBER0", Integer.toString(price));
        v.put("NUMBER1", Integer.toString(gold));
        return v;
    }

    /**
     * A {@code @HOWMUCH} box (V #54344: (42,79,236,43), no portrait, the
     * field "Menge:" at box + (33, 24), its preset selected), coming
     * {@link #HOWMUCH_MS} after the drop.  The preset may lie past the
     * "(0-max)" ({@link #PRESET}, both boxes); Enter on it takes the max.
     *
     * @param t The original texts, or null.
     * @param buying True for {@code @HOWMUCH4}, false for {@code @HOWMUCH5}.
     * @param values Its values ({@link #buyValues}, {@link #sellValues}).
     * @param max The largest amount.
     * @param preset The field's preset.
     * @return The box, or null without the text.
     */
    static ClassicAdvisorBox.Request howMuchRequest(ClassicText t, boolean buying,
                                                   Map<String, String> values,
                                                   int max, int preset) {
        final String s = (buying) ? BUY_SECTION : SELL_SECTION;
        final ClassicAdvisorBox.Builder b = ClassicAdvisorBox.fromGameText(
            s, (t == null) ? null : t.message(s), values);
        return (b == null) ? null : b.portrait(ClassicAdvisorBox.Portrait.NONE)
            .amountField(max, preset, true).openDelay(HOWMUCH_MS)
            .stopgap(Messages.message("classic.dialog.messages"), null).build();
    }
}
