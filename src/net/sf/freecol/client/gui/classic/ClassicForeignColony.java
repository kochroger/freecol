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
import net.sf.freecol.common.model.Constants.ScoutColonyAction;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Settlement;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;


/**
 * A unit of ours at a foreign colony (part N6).  Headless: the boxes
 * {@link ClassicGUI#getScoutForeignColonyChoice} and
 * {@link ClassicGUI#illegalMoveKey} ask.
 *
 * <ul>
 *   <li><b>The scout (V, playthrough-1 #73, catalogue C38).</b>  Moved into
 *       a colony of another European: GAME.TXT {@code @SCOUTCOLONY}, «Unsere
 *       {Späher} haben die Außenbezirke von {Montreal} erreicht, Eure
 *       Exzellenz. Was sollen sie jetzt tun?», {@code %STRING0} the
 *       colony's name, the frontiersman (MSS3) over the box, rows
 *       «Bürgermeister treffen» (the bar's) / «Kolonie infiltrieren» /
 *       «Kolonie angreifen» / «Nichts» (Escape's: nothing happens, nothing
 *       is spent).  At 320x200 box (42,113,236,62), MSS3 at (86,26).  The
 *       rows take FreeCol's actions: negotiate, spy, attack.  «Bürgermeister
 *       treffen» is greyed where FreeCol cannot negotiate (the REF's
 *       colonies).</li>
 *   <li><b>Any other colonist (Roger, 2026-10-09 09:50: "Als ich mit dem
 *       Pionier eine Kolonie der Franzosen betreten wollte, kam
 *       fälschlicherweise: Diese Art von Einheit kann nicht angreifen.
 *       Richtig wäre der Dialog: "Bürgermeister treffen usw.").</b>  A
 *       free colonist, an expert, a pioneer, a missionary (FreeCol's
 *       {@code MOVE_NO_ACCESS_SETTLEMENT} from land) gets the same box (I:
 *       no clip shows it; the text keeps GAME.TXT's «{Späher}»), with
 *       «Kolonie infiltrieren» and «Kolonie angreifen» greyed: only a scout
 *       infiltrates (FreeCol's {@code SPY_ON_COLONY}, the manual) and only a
 *       soldier attacks (I).  «Bürgermeister treffen» takes the scout's
 *       way; «Nichts» and Escape do nothing and spend nothing.</li>
 * </ul>
 */
final class ClassicForeignColony {

    /** GAME.TXT's box of a unit at a foreign colony. */
    static final String SECTION = "SCOUTCOLONY";

    /** «Bürgermeister treffen». */
    static final int MEET_ROW = 0;

    /** «Kolonie infiltrieren». */
    static final int SPY_ROW = 1;

    /** «Kolonie angreifen». */
    static final int ATTACK_ROW = 2;

    /** «Nichts»: Escape's row. */
    static final int NOTHING_ROW = 3;


    private ClassicForeignColony() {}   // static helpers only


    /**
     * Whether a unit's move goes into a colony of another European and is
     * one of the moves FreeCol refuses there for a colonist that is no
     * scout ({@code MOVE_NO_ACCESS_SETTLEMENT}, from land): the second case
     * of the class comment.
     *
     * @param unit The unit.
     * @param to The tile it is ordered onto.
     * @return True if the box comes instead of the refusal.
     */
    static boolean visits(Unit unit, Tile to) {
        if (unit == null || to == null || unit.isNaval()
            || !unit.hasTile() || unit.isOnCarrier()
            || !unit.getTile().isLand() || !unit.isColonist()) return false;
        final Settlement s = to.getSettlement();
        if (!(s instanceof Colony)) return false;
        final Player other = s.getOwner();
        return other != null && other != unit.getOwner() && other.isEuropean();
    }

    /**
     * The box (class comment).
     *
     * @param t The original texts, or null.
     * @param colonyName The colony's name.
     * @param canMeet Whether FreeCol can negotiate there.
     * @param scout Whether the unit is a scout (else infiltrate and attack
     *     are greyed).
     * @param title The stopgap's window title.
     * @param showAtNanos When it should be on screen, 0 for at once.
     * @return The box, or null without the text.
     */
    static ClassicAdvisorBox.Request request(ClassicText t, String colonyName,
                                             boolean canMeet, boolean scout,
                                             String title, long showAtNanos) {
        final ClassicText.Message m = (t == null) ? null : t.message(SECTION);
        if (m == null || m.text.isEmpty() || m.options.size() <= NOTHING_ROW
            || colonyName == null) return null;
        final Map<String, String> values = new HashMap<>();
        values.put("STRING0", colonyName);
        final ClassicAdvisorBox.Builder b
            = ClassicAdvisorBox.fromGameText(SECTION, m, values);
        if (b == null) return null;
        return b.defaultRow(firstRow(canMeet, scout)).cancelRow(NOTHING_ROW)
            .disabled(greyed(canMeet, scout))
            .portrait(ClassicAdvisorBox.Portrait.SCOUT)
            .showAt(showAtNanos).stopgap(title, null).build();
    }

    /**
     * The box in FreeCol's words, without the pack: FreeCol's own scout
     * question and rows, greyed as the original's.
     *
     * @param unit The unit.
     * @param colony The colony.
     * @param canMeet Whether FreeCol can negotiate there.
     * @param scout Whether the unit is a scout.
     * @param title The stopgap's window title.
     * @param showAtNanos When it should be on screen, 0 for at once.
     * @return The box.
     */
    static ClassicAdvisorBox.Request freeColRequest(Unit unit, Colony colony,
                                                    boolean canMeet,
                                                    boolean scout, String title,
                                                    long showAtNanos) {
        final String text = Messages.message(StringTemplate
            .template("scoutColony.text")
            .addStringTemplate("%unit%", unit.getLabel(Unit.UnitLabelType.NATIONAL))
            .addName("%colony%", colony.getName()));
        final List<String> rows = new ArrayList<>();
        rows.add(ClassicAdvisorBox.literal(Messages.message("scoutColony.negotiate")));
        rows.add(ClassicAdvisorBox.literal(Messages.message("scoutColony.spy")));
        rows.add(ClassicAdvisorBox.literal(Messages.message("scoutColony.attack")));
        rows.add(ClassicAdvisorBox.literal(Messages.message("cancel")));
        return ClassicAdvisorBox.Request.builder(SECTION).freeColText(text)
            .rows(rows).disabled(greyed(canMeet, scout))
            .defaultRow(firstRow(canMeet, scout))
            .cancelRow(NOTHING_ROW).portrait(ClassicAdvisorBox.Portrait.SCOUT)
            .showAt(showAtNanos).stopgap(title, null).build();
    }

    /**
     * The bar's first row: «Bürgermeister treffen» (V), else the first row
     * that can be taken.
     *
     * @param canMeet Whether FreeCol can negotiate there.
     * @param scout Whether the unit is a scout.
     * @return The row.
     */
    static int firstRow(boolean canMeet, boolean scout) {
        return (canMeet) ? MEET_ROW : (scout) ? SPY_ROW : NOTHING_ROW;
    }

    /**
     * The greyed rows.
     *
     * @param canMeet Whether FreeCol can negotiate there.
     * @param scout Whether the unit is a scout.
     * @return Per row, whether it is greyed.
     */
    static boolean[] greyed(boolean canMeet, boolean scout) {
        final boolean[] d = new boolean[NOTHING_ROW + 1];
        d[MEET_ROW] = !canMeet;
        d[SPY_ROW] = !scout;
        d[ATTACK_ROW] = !scout;
        return d;
    }

    /**
     * The box's answer as FreeCol's action.
     *
     * @param r The box.
     * @param chosen The row it returned, -1 if dismissed.
     * @return The action, or null for «Nichts», Escape, a greyed row or a
     *     box that closed or could not open.
     */
    static ScoutColonyAction action(ClassicAdvisorBox.Request r, int chosen) {
        if (r == null || !r.enabled(chosen)) return null;
        switch (chosen) {
        case MEET_ROW:
            return ScoutColonyAction.SCOUT_COLONY_NEGOTIATE;
        case SPY_ROW:
            return ScoutColonyAction.SCOUT_COLONY_SPY;
        case ATTACK_ROW:
            return ScoutColonyAction.SCOUT_COLONY_ATTACK;
        default:
            return null;
        }
    }
}
