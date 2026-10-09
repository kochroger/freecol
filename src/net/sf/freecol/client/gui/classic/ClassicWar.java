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


/**
 * The original's treaty breach (gap list B3; clip opening_018,
 * {@code war-french-analysis/10-spec.md}).  Headless: the box
 * {@link ClassicGUI#confirmFortifyWar} asks.
 *
 * <ul>
 *   <li><b>When (V, #640; I for the rule).</b>  F with an offensive unit
 *       of ours next to, or on the land of, a colony of a European we are
 *       at peace (or cease fire) with, under the rules' treaty breach
 *       ({@code Unit.getFortifyWarColony}, the levi rules).  At war F is a
 *       plain fortification with no box (V, the scout at #53979); S never
 *       asks (Roger).</li>
 *   <li><b>The box (V, #640, 0 px against the clip).</b>  GAME.TXT
 *       {@code @HAVETREATY}: «"Wir haben einen Friedensvertrag mit den
 *       {Frz.} unterzeichnet, Eure Exzellenz."», {@code %STRING0} the
 *       colony owner's NAMES.TXT {@code @NATIONALITY} (Engl. / Frz. /
 *       Span. / Holl.), the soldier (MSS1) over the box; rows «Handlung
 *       abbrechen.» (the bar's, Escape's: nothing happens, nothing is
 *       spent; Roger) and «Friedensvertrag brechen.» (war).  At 320x200:
 *       box (34,116,236,46), the soldier at (215,39).</li>
 *   <li><b>After "brechen" (V #1174; I for the war).</b>  The unit
 *       fortifies (its flag's F in black), the server declares the war,
 *       and no notice of any kind comes (the server tells us nothing,
 *       {@code ServerPlayer.csChangeStance}); the next unit comes up.</li>
 * </ul>
 */
final class ClassicWar {

    /** GAME.TXT's question when F breaks a peace treaty. */
    static final String TREATY_SECTION = "HAVETREATY";

    /** «Handlung abbrechen.»: the bar's row and Escape's. */
    static final int CANCEL_ROW = 0;

    /** «Friedensvertrag brechen.». */
    static final int BREAK_ROW = 1;


    private ClassicWar() {}   // static helpers only


    /**
     * The NAMES.TXT {@code @NATIONALITY} word of a European nation
     * ("Frz."), the box's {@code %STRING0} (V: «{Frz.}» #640).
     *
     * @param t The original texts, or null.
     * @param p The player, or null.
     * @return The word; FreeCol's name of the nation for one the original
     *     does not have (literal); null without a player.
     */
    static String nationality(ClassicText t, Player p) {
        if (p == null) return null;
        final int n = ClassicDestinations.nation(p);
        final List<String[]> rows = (t == null || n < 0) ? null
            : t.names("NATIONALITY");
        if (rows != null && n < rows.size() && rows.get(n).length > 0
            && !rows.get(n)[0].trim().isEmpty()) {
            return rows.get(n)[0].trim();
        }
        String name;
        try {
            name = Messages.message(p.getNationLabel());
        } catch (RuntimeException e) {
            name = p.getNationId();
        }
        return ClassicAdvisorBox.literal(name);
    }

    /**
     * The {@code @HAVETREATY} box (class comment): its two rows, the bar
     * and Escape on «Handlung abbrechen.», the soldier.
     *
     * @param t The original texts, or null.
     * @param other The colony's owner, the nation at peace with us.
     * @param title The stopgap's window title.
     * @return The box, or null without the text (FreeCol's question then).
     */
    static ClassicAdvisorBox.Request treatyRequest(ClassicText t, Player other,
                                                  String title) {
        final ClassicText.Message m = (t == null) ? null : t.message(TREATY_SECTION);
        if (m == null || m.text.isEmpty() || m.options.size() <= BREAK_ROW
            || other == null) return null;
        final Map<String, String> values = new HashMap<>();
        values.put("STRING0", nationality(t, other));
        final ClassicAdvisorBox.Builder b
            = ClassicAdvisorBox.fromGameText(TREATY_SECTION, m, values);
        return (b == null) ? null
            : b.defaultRow(CANCEL_ROW).cancelRow(CANCEL_ROW)
                .portrait(ClassicAdvisorBox.Portrait.SOLDIER)
                .stopgap(title, null).build();
    }

    /**
     * The answer of the {@code @HAVETREATY} box: only «Friedensvertrag
     * brechen.» breaks the treaty; the other row, Escape and a box that
     * closed or could not open ({@code -1}) do nothing.
     *
     * @param chosen The row the box returned.
     * @return True for war.
     */
    static boolean breaksTreaty(int chosen) {
        return chosen == BREAK_ROW;
    }
}
