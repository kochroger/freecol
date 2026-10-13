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
import java.util.List;
import java.util.Map;

import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Constants.ClaimAction;


/**
 * A claim of native land as the original asks it (part O, bundle A2;
 * Roger 2026-10-13: the original everywhere): GAME.TXT @INDIANLAND with
 * the tribe's chief, its rows «Gut, wir werden Eure Wünsche
 * respektieren.» (leave), «Wir bieten Euch N für dieses Land.» (pay) and
 * «Ihr irrt Euch, dies ist jetzt UNSER Land!» (take), the bar on the
 * first, the peaceful leave (V clip008 #6382-#7050, frame
 * {@code c008_0136_colony_indianland}: the Araucanian chief at the right,
 * the box (7,73,236,54), the bar on row 1 with 1000 gold, closed on it,
 * nothing moved).  Escape leaves too (the Escape trap: our GAME.TXT reader
 * would put it on the last row, taking the land).  FreeCol's box had the
 * bar on "pay": Enter spent the gold (prep A2, probe: 1000 -&gt; 600).
 *
 * <p>Leave costs nothing: FreeCol's {@code askClaimTile} returns before
 * asking the server, the colonist stays, a colony is not founded, a
 * pioneer job does not start.  The pay row is greyed when the gold cannot
 * pay it (FreeCol's rule; I: the original's is not known).  FreeCol also
 * asks when a colony is founded on native land and before a pioneer's job
 * there; both get this box (I: the original's road and forest boxes,
 * @INDIANROAD and @INDIANFOREST, come with the pioneer work, M6).
 * Without the pack, for a tribe the original does not have, or for a
 * tribe not yet met (FreeCol's {@code indianLand.unknown}, no pay row):
 * FreeCol's words in the same order, leave first, with the same bar and
 * Escape.
 */
final class ClassicNativeLand {

    /** GAME.TXT's claim box. */
    static final String SECTION = "INDIANLAND";

    /** The rows: leave (the bar's, Escape's), pay, take. */
    static final int LEAVE_ROW = 0, PAY_ROW = 1, TAKE_ROW = 2;

    private ClassicNativeLand() {}

    /**
     * @INDIANLAND's values: {@code %STRING0} the tribe (NAMES
     * {@code @TRIBES}, «Araukaner», «{%STRING0-Land}» gives
     * «Araukaner-Land»), {@code %NUMBER1} the price.
     *
     * @param t The original texts.
     * @param tribe The tribe's {@link ClassicGUI#TRIBES} index.
     * @param price The price.
     * @return The values, or null for a tribe without a name.
     */
    static Map<String, String> values(ClassicText t, int tribe, int price) {
        final List<String[]> rows = (t == null) ? null : t.names("TRIBES");
        if (rows == null || tribe < 0 || tribe >= rows.size()
            || rows.get(tribe).length == 0) return null;
        final String name = rows.get(tribe)[0].trim();
        if (name.isEmpty()) return null;
        final Map<String, String> v = new HashMap<>();
        v.put("STRING0", name);
        v.put("NUMBER1", Integer.toString(price));
        return v;
    }

    /**
     * The claim's box (class comment).
     *
     * @param t The original texts, or null.
     * @param owner The tribe owning the land.
     * @param contacted Whether we have met the tribe (else FreeCol's
     *     words without a pay row).
     * @param price The price.
     * @param canPay Whether the gold pays it (else the pay row is greyed).
     * @param title The stopgap's window title.
     * @return The box.
     */
    static ClassicAdvisorBox.Request claimRequest(ClassicText t, Player owner,
                                                  boolean contacted, int price,
                                                  boolean canPay, String title) {
        final int tribe = ClassicGUI.tribeIndex(owner);
        final ClassicText.Message m = (t == null || !contacted) ? null
            : t.message(SECTION);
        final Map<String, String> values = (m == null) ? null : values(t, tribe, price);
        ClassicAdvisorBox.Builder b = (values == null || m.options.size() < 3) ? null
            : ClassicAdvisorBox.fromGameText(SECTION, m, values);
        if (b != null) {
            b.disabled(new boolean[] { false, !canPay, false });
        } else if (contacted) {
            b = ClassicAdvisorBox.Request.builder(SECTION)
                .freeColText(Messages.message(StringTemplate.template("indianLand.text")
                    .addStringTemplate("%player%", owner.getNationLabel())))
                .rows(ClassicAdvisorBox.literal(Messages.message("indianLand.cancel")),
                      ClassicAdvisorBox.literal(Messages.message(StringTemplate
                          .template("indianLand.pay").addAmount("%amount%", price))),
                      ClassicAdvisorBox.literal(Messages.message("indianLand.take")))
                .disabled(new boolean[] { false, !canPay, false });
        } else {
            b = ClassicAdvisorBox.Request.builder(SECTION)
                .freeColText(Messages.message("indianLand.unknown"))
                .rows(ClassicAdvisorBox.literal(Messages.message("indianLand.cancel")),
                      ClassicAdvisorBox.literal(Messages.message("indianLand.take")));
        }
        return b.defaultRow(LEAVE_ROW).cancelRow(LEAVE_ROW)
            .portrait(ClassicAdvisorBox.Portrait.chief(tribe)).stopgap(title, null).build();
    }

    /**
     * The claim's answer.
     *
     * @param r The box asked.
     * @param answer Its answer.
     * @return FreeCol's {@code CLAIM_ACCEPT} (pay) or {@code CLAIM_STEAL}
     *     (take), or null (leave, Escape, a greyed row, no answer).
     */
    static ClaimAction action(ClassicAdvisorBox.Request r, int answer) {
        if (r == null || !r.enabled(answer) || answer == LEAVE_ROW) return null;
        if (r.rows.size() == 2) return (answer == 1) ? ClaimAction.CLAIM_STEAL : null;
        switch (answer) {
        case PAY_ROW: return ClaimAction.CLAIM_ACCEPT;
        case TAKE_ROW: return ClaimAction.CLAIM_STEAL;
        default: return null;
        }
    }
}
