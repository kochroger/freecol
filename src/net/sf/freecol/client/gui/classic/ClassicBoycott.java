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
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Market;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.StringTemplate;


/**
 * A boycotted good in Europe as the original handles it (part O, bundle
 * A1; Roger 2026-10-13: the original everywhere).  Headless: the Europe
 * screen ({@link ClassicEuropePanel}) asks these boxes.
 *
 * <p>What the original does (M manual:197, :199; GAME.TXT tags only, no
 * clip shows a lift):
 * <ul>
 *   <li>A boycotted good cannot be sold or bought in the home port.  Its
 *       cargo <b>stays aboard</b>: a sale of it does nothing, and after an
 *       unload that left some cargo aboard {@code @SOMEBOYCOTT} says so,
 *       once (a notice, no rows).  FreeCol's server would dump it in the
 *       harbour without a word, and its own Europe screen offers to dump
 *       it (pay / dump / cancel); both are gone here.</li>
 *   <li>A click on the boycotted good in the market (and, I, a drag of
 *       it onto a ship) brings {@code @KISSUP}: «Das ist Besteuerung ohne
 *       Stimmrecht! Ungerecht!» (row 1) refuses, «N zahlen.» (row 2)
 *       pays the arrears and lifts the boycott.  GAME.TXT has no
 *       {@code @default}: the bar is on row 1; Escape answers row 1 too
 *       (the Escape trap: our reader's default would put Escape on the
 *       last row, the payment).</li>
 *   <li>Row 2 with too little gold: {@code @KISSSORRY} with our gold (I:
 *       its {@code %NUMBER0} is read as our treasury), nothing paid.</li>
 * </ul>
 * The arrears are FreeCol's (bid times the level's arrears factor, the
 * tea party's, {@code ServerPlayer.csRaiseTax}); the original's back
 * taxes are not known (Roger: FreeCol's formula).  Nothing costs a move:
 * Europe needs none.  Who stands at the boxes is not known (I): none.
 *
 * <p>The market shows the boycott as the original draws it: ICONS.SS 055,
 * the red X, over the good's slot at (19k + 6, 184) (V clip006 #8146,
 * playthrough-3 #24114), and the hover label «Werkzeuge (Boykott)» (V
 * clip006 #8146/#8149, LABELS {@code @MISC} «Boykott»; {@link
 * ClassicEuropePanel#marketLabel}).
 */
final class ClassicBoycott {

    /** GAME.TXT's notice after an unload that left boycotted cargo aboard. */
    static final String SOME_SECTION = "SOMEBOYCOTT";

    /** GAME.TXT's question at a click on a boycotted good. */
    static final String KISS_SECTION = "KISSUP";

    /** GAME.TXT's notice when the gold cannot pay the arrears. */
    static final String SORRY_SECTION = "KISSSORRY";

    /** {@code @KISSUP}'s rows: refuse (the bar's, Escape's), pay. */
    static final int REFUSE_ROW = 0, PAY_ROW = 1;

    /** ICONS.SS frame of the market's red X (8 x 12, V clip006 #8146). */
    static final int ICON_X = 55;

    /** The X's place in market slot k: (19k + {@link #X_DX}, {@link #X_Y}). */
    static final int X_DX = 6, X_Y = 184;

    /** FreeCol's fallback words of {@code @SOMEBOYCOTT} (no pack). */
    static final String SOME_FALLBACK = "classic.boycott.some";

    private ClassicBoycott() {}


    /**
     * @param p Our player, or null.
     * @param type The goods, or null.
     * @return Whether the good is boycotted in Europe (FreeCol's arrears).
     */
    static boolean boycotted(Player p, GoodsType type) {
        return p != null && type != null && !p.canTrade(type);
    }

    /**
     * {@code @SOMEBOYCOTT}: a notice, no portrait (I).
     *
     * @param t The original texts, or null (then FreeCol-style words).
     * @param title The stopgap's window title.
     * @return The box.
     */
    static ClassicAdvisorBox.Request someRequest(ClassicText t, String title) {
        ClassicAdvisorBox.Builder b = ClassicAdvisorBox.fromGameText(SOME_SECTION,
            (t == null) ? null : t.message(SOME_SECTION), new HashMap<>());
        if (b == null) {
            b = ClassicAdvisorBox.Request.builder(SOME_SECTION)
                .freeColText(Messages.message(SOME_FALLBACK));
        }
        return b.portrait(ClassicAdvisorBox.Portrait.NONE).stopgap(title, null).build();
    }

    /**
     * {@code @KISSUP}'s values: {@code %STRING1} the home port
     * ("Amsterdam", NAMES {@code @HOMEPORT}), {@code %NUMBER0} the arrears.
     *
     * @param t The original texts, or null.
     * @param p Our player, or null.
     * @param arrears The arrears.
     * @return The values.
     */
    static Map<String, String> kissValues(ClassicText t, Player p, int arrears) {
        final Map<String, String> v = new HashMap<>();
        String port = (p == null) ? null : ClassicTips.homePort(t, p);
        if (port == null && p != null && p.getEurope() != null) {
            port = Messages.message(p.getEurope().getNameKey());
        }
        if (port != null) v.put("STRING1", port);
        v.put("NUMBER0", Integer.toString(arrears));
        return v;
    }

    /**
     * {@code @KISSUP} (class comment): GAME.TXT's two rows, the bar and
     * Escape on «Das ist Besteuerung ohne Stimmrecht! Ungerecht!», no
     * portrait (I).  Without the pack: FreeCol's question with its cancel
     * row first, the same bar and Escape.
     *
     * @param t The original texts, or null.
     * @param p Our player, or null.
     * @param arrears The arrears.
     * @param title The stopgap's window title.
     * @return The box.
     */
    static ClassicAdvisorBox.Request kissRequest(ClassicText t, Player p, int arrears,
                                                 String title) {
        final ClassicText.Message m = (t == null) ? null : t.message(KISS_SECTION);
        ClassicAdvisorBox.Builder b = (m == null || m.options.size() < 2) ? null
            : ClassicAdvisorBox.fromGameText(KISS_SECTION, m, kissValues(t, p, arrears));
        if (b == null) {
            b = ClassicAdvisorBox.Request.builder(KISS_SECTION)
                .freeColText(Messages.message(StringTemplate.template("payArrears.text")
                    .addAmount("%amount%", arrears)))
                .rows(ClassicAdvisorBox.literal(Messages.message("cancel")),
                      ClassicAdvisorBox.literal(Messages.message("payArrears")));
        }
        return b.defaultRow(REFUSE_ROW).cancelRow(REFUSE_ROW)
            .portrait(ClassicAdvisorBox.Portrait.NONE).stopgap(title, null).build();
    }

    /**
     * @param answer {@code @KISSUP}'s answer.
     * @return Whether it pays.
     */
    static boolean pays(int answer) {
        return answer == PAY_ROW;
    }

    /**
     * {@code @KISSSORRY}: a notice with our gold (I), no portrait (I).
     * Without the pack: FreeCol's words for the arrears.
     *
     * @param t The original texts, or null.
     * @param gold Our gold.
     * @param arrears The arrears (FreeCol's words only).
     * @param title The stopgap's window title.
     * @return The box.
     */
    static ClassicAdvisorBox.Request sorryRequest(ClassicText t, int gold, int arrears,
                                                  String title) {
        final Map<String, String> v = new HashMap<>();
        v.put("NUMBER0", Integer.toString(gold));
        ClassicAdvisorBox.Builder b = ClassicAdvisorBox.fromGameText(SORRY_SECTION,
            (t == null) ? null : t.message(SORRY_SECTION), v);
        if (b == null) {
            b = ClassicAdvisorBox.Request.builder(SORRY_SECTION)
                .freeColText(Messages.message(StringTemplate.template("payArrears.noGold")
                    .addAmount("%amount%", arrears)));
        }
        return b.portrait(ClassicAdvisorBox.Portrait.NONE).stopgap(title, null).build();
    }

    /**
     * @param p Our player, or null.
     * @param type The goods.
     * @return FreeCol's arrears of the good, 0 without a market.
     */
    static int arrears(Player p, GoodsType type) {
        final Market m = (p == null) ? null : p.getMarket();
        return (m == null || type == null) ? 0 : p.getArrears(type);
    }
}
