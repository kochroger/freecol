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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Role;
import net.sf.freecol.common.model.Unit;


/**
 * A unit standing in our colony, outside its buildings and fields (the
 * original's "Vorhandene Einheiten"): its options box, @COLONYUNIT with
 * the rows of @UNITOPTIONS (clip 019, {@code wake-in-colony-analysis/
 * 10-spec.md}: how Roger wakes a soldier fortified in his colony).
 * Headless: what {@link ClassicGUI} asks and what the answer does.
 *
 * <ul>
 *   <li><b>The box (V, clip 019 #959, OCR 0 errors; playthrough-2
 *       #45259).</b>  «Optionen für  {(Erfahrene Holzfäller)}
 *       (Dragoner):»: {@code %STRING0} the unit's NAMES.TXT {@code @UNIT}
 *       name (the role's: "Dragoner", "Pioniere"), {@code %STRING1} the
 *       colonist's expert name ({@code @JOB} column 2) in brackets after
 *       a space, empty for a free colonist (playthrough-2: «Optionen für
 *       (Pioniere):», two spaces either way).  No portrait; the unit's
 *       icon with its flag in the box (a unit box,
 *       {@link ClassicAdvisorBox.UnitIcon}): box (31,76,258,48) with three
 *       rows.</li>
 *   <li><b>The rows (V for the two boxes seen; I for the rule).</b>
 *       {@code @UNITOPTIONS} without the rows that would change nothing:
 *       «Nach vorne bewegen.» not for the first unit of the row, «Befehle
 *       aufheben.» not for a unit without orders (the pioneer), «Wache /
 *       An Bord gehen.» not for one on sentry (I), «Befestigen.» not for
 *       one fortified or fortifying (the dragoon); «Keine Veränderungen.»
 *       always.  The bar starts on the first row shown; Escape is «Keine
 *       Veränderungen.» (Roger: Escape changes nothing).</li>
 *   <li><b>What they do (V for "Befehle aufheben."; I for the rest).</b>
 *       «Befehle aufheben.» clears the unit's orders: its flag's F becomes
 *       "-" (#1290 -&gt; #1298); it stays where it is, selected, and does
 *       not come up on the map (#1554: the unit that was up stays up).
 *       «Wache» puts it on sentry (S), «Befestigen.» fortifies it.  «Nach
 *       vorne bewegen.» moves it to the front of the row (I: the
 *       original's meaning is not seen; here only the colony screen's
 *       order).</li>
 * </ul>
 */
final class ClassicColonyUnits {

    /** GAME.TXT's heading of the box. */
    static final String UNIT_SECTION = "COLONYUNIT";

    /** GAME.TXT's rows of the box. */
    static final String OPTIONS_SECTION = "UNITOPTIONS";

    /** The rows of {@code @UNITOPTIONS}, in its order. */
    enum Option {
        /** «Nach vorne bewegen.». */
        FRONT,
        /** «Befehle aufheben.». */
        CLEAR,
        /** «Wache / An Bord gehen.». */
        SENTRY,
        /** «Befestigen.». */
        FORTIFY,
        /** «Keine Veränderungen.». */
        NOTHING
    }


    private ClassicColonyUnits() {}   // static helpers only


    /**
     * The rows a unit's box offers (class comment), in
     * {@code @UNITOPTIONS} order.
     *
     * @param u The unit.
     * @param first Whether it is the first unit of the colony's row.
     * @return The rows, «Keine Veränderungen.» last.
     */
    static List<Option> options(Unit u, boolean first) {
        final List<Option> out = new ArrayList<>();
        if (!first) out.add(Option.FRONT);
        if (u != null && hasOrders(u)) out.add(Option.CLEAR);
        final Unit.UnitState s = (u == null) ? null : u.getState();
        if (s != Unit.UnitState.SENTRY) out.add(Option.SENTRY);
        if (s != Unit.UnitState.FORTIFIED && s != Unit.UnitState.FORTIFYING) {
            out.add(Option.FORTIFY);
        }
        out.add(Option.NOTHING);
        return out;
    }

    /**
     * Whether a unit has orders: its flag shows a letter other than "-"
     * ({@link ClassicHud#ordersRow}: sentry, fortify, a trade route, a
     * destination, a pioneer's work).
     *
     * @param u The unit.
     * @return True if «Befehle aufheben.» would change something.
     */
    static boolean hasOrders(Unit u) {
        return ClassicHud.ordersRow(u) != ClassicHud.ORDERS_NONE;
    }

    /**
     * The NAMES.TXT {@code @UNIT} name of a unit (its role's for a
     * colonist: "Dragoner", "Pioniere"), the box's {@code %STRING0}.
     *
     * @param t The original texts, or null.
     * @param u The unit.
     * @return The name; FreeCol's (literal) for a unit the original lacks.
     */
    static String unitName(ClassicText t, Unit u) {
        final int row = ClassicHud.unitRow(u);
        final List<String[]> rows = (t == null || row < 0) ? null : t.names("UNIT");
        if (rows != null && row < rows.size() && rows.get(row).length > 0
            && !rows.get(row)[0].trim().isEmpty()) {
            return rows.get(row)[0].trim();
        }
        String name;
        try {
            name = Messages.getName(u.getType());
        } catch (RuntimeException e) {
            name = u.getType().getId();
        }
        return ClassicAdvisorBox.literal(name);
    }

    /**
     * The colonist's expert part of the heading, the box's
     * {@code %STRING1}: a space and the {@code @JOB} column 2 name in
     * brackets (V: " (Erfahrene Holzfäller)", the space giving the two
     * spaces before the bracket); empty for a free colonist (V: the
     * pioneer of playthrough-2), a unit that is no colonist and a type
     * the original lacks (I).
     *
     * @param t The original texts, or null.
     * @param u The unit.
     * @return The part, possibly "".
     */
    static String expertPart(ClassicText t, Unit u) {
        if (t == null || u == null || !u.isPerson()) return "";
        final String type = Role.getRoleIdSuffix(u.getType().getId());
        if ("freeColonist".equals(type)) return "";
        final int row = ClassicHud.jobRow(type);
        final List<String[]> rows = (row < 0) ? null : t.names("JOB");
        if (rows == null || row >= rows.size() || rows.get(row).length < 2) return "";
        final String expert = rows.get(row)[1].trim();
        return (expert.isEmpty()) ? "" : " (" + expert + ")";
    }

    /**
     * The box of a unit (class comment).
     *
     * @param t The original texts, or null.
     * @param u The unit.
     * @param shown The rows offered ({@link #options}).
     * @param icon The unit's icon in the box, or null (then no icon
     *     column: not the original's box).
     * @param title The stopgap's window title.
     * @return The box, or null without the texts.
     */
    static ClassicAdvisorBox.Request request(ClassicText t, Unit u,
                                             List<Option> shown,
                                             ClassicAdvisorBox.UnitIcon icon,
                                             String title) {
        final ClassicText.Message head = (t == null) ? null : t.message(UNIT_SECTION);
        final ClassicText.Message rows = (t == null) ? null : t.message(OPTIONS_SECTION);
        if (u == null || head == null || head.text.isEmpty() || head.width == null
            || rows == null || rows.text.size() <= Option.NOTHING.ordinal()
            || shown == null || shown.isEmpty()) return null;
        final Map<String, String> values = new HashMap<>();
        values.put("STRING0", unitName(t, u));
        values.put("STRING1", expertPart(t, u));
        final List<String> lines = new ArrayList<>(head.text.size());
        for (String s : head.text) lines.add(ClassicText.substitute(s, values));
        final List<String> labels = new ArrayList<>(shown.size());
        for (Option o : shown) labels.add(rows.text.get(o.ordinal()).trim());
        return ClassicAdvisorBox.Request.builder(UNIT_SECTION).gameText(lines)
            .width(head.width).rows(labels).defaultRow(0)
            .cancelRow(labels.size() - 1).unitIcon(icon)
            .stopgap(title, null).build();
    }

    /**
     * What an answer of the box takes.
     *
     * @param shown The rows offered.
     * @param answer The row the box returned (-1: closed with none).
     * @return The option; «Keine Veränderungen.» for no row.
     */
    static Option chosen(List<Option> shown, int answer) {
        return (shown == null || answer < 0 || answer >= shown.size())
            ? Option.NOTHING : shown.get(answer);
    }

    /**
     * The colony's row of units as the screen shows it: the units moved
     * to the front by «Nach vorne bewegen.», the last moved first, then
     * the rest in the tile's order.
     *
     * @param units The units standing in the colony, in the tile's order.
     * @param fronted Unit ids, the last moved to the front first.
     * @return The row.
     */
    static List<Unit> order(List<Unit> units, List<String> fronted) {
        if (units == null) return Collections.emptyList();
        final List<Unit> out = new ArrayList<>(units.size());
        if (fronted != null) {
            for (String id : fronted) {
                for (Unit u : units) {
                    if (u.getId().equals(id) && !out.contains(u)) out.add(u);
                }
            }
        }
        for (Unit u : units) {
            if (!out.contains(u)) out.add(u);
        }
        return out;
    }

    /**
     * Note a unit moved to the front ({@link #order}).
     *
     * @param fronted The ids, the last moved first; changed.
     * @param u The unit.
     */
    static void front(List<String> fronted, Unit u) {
        if (fronted == null || u == null) return;
        fronted.remove(u.getId());
        fronted.add(0, u.getId());
    }
}
