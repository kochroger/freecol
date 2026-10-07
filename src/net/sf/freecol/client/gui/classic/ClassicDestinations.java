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
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import net.sf.freecol.client.gui.action.ReturnToEuropeAction;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Europe;
import net.sf.freecol.common.model.Location;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Unit;


/**
 * The original's "Gehe zu" list (R2, master plan W8e, N11): G, or BEFEHLE
 * "Zum Hafen gehen" / "Zum Ort gehen", opens a box with no portrait,
 * GAME.TXT {@code @SAILPORT} "Wählen Sie einen Zielhafen:" for a ship and
 * {@code @TRAVELPLACE} "Wählen Sie eine Kolonie als Reiseziel:" for a land
 * unit, one row per destination the unit can reach (the original manual,
 * "The GO TO Menu": "the friendly named destinations that the
 * currently-active unit could reach").  Pure and headless: it only reads
 * the model.
 *
 * <ul>
 *   <li><b>A ship</b>: our colonies with a port on water that leads to the
 *   high seas ({@code Settlement.isConnectedPort}), not the one it lies
 *   in, that it can reach; then the home port, "Amsterdam (Holland)"
 *   ({@code @HOMEPORT} + " (" + {@code @COUNTRY} + ")"; V landfall #23247,
 *   Steam capture opening_051 "London (England)"), while its player still
 *   has Europe and the ship can get there (FreeCol's own destination
 *   dialog's rule, {@code SelectDestinationDialog.loadDestinations}).</li>
 *   <li><b>A land unit on the map</b>: our colonies on its landmass, not
 *   the one it stands in, that it can reach; no home port.  A unit aboard
 *   a ship, or off the map, gets no list.</li>
 *   <li><b>The order</b> is not recorded (both captures have one row): the
 *   colonies in the order they were founded, the home port last
 *   ({@link #HOME_PORT_LAST}; I, Roger's question).</li>
 *   <li><b>The box</b> ({@link #request}): the rows at box + 15
 *   ({@link ClassicMenuBox#PORT_INDENT}, landfall #23300), the bar on
 *   {@code @default=1}, Escape and a click outside choose nothing
 *   ({@code noCancelRow}: the builder's default would send the unit to the
 *   last row's destination).</li>
 * </ul>
 */
final class ClassicDestinations {

    /** GAME.TXT's ships' destination box. */
    static final String SAIL_PORT_SECTION = "SAILPORT";

    /** GAME.TXT's land units' destination box. */
    static final String TRAVEL_PLACE_SECTION = "TRAVELPLACE";

    /**
     * Whether the home port comes after the colonies (I: no capture shows
     * a list with a colony; one switch for when a clip settles it).
     */
    static final boolean HOME_PORT_LAST = true;

    /** One row: its text (markup-safe) and where it leads. */
    static final class Row {

        /** The row as drawn. */
        final String label;

        /** The destination. */
        final Location location;

        Row(String label, Location location) {
            this.label = label;
            this.location = location;
        }

        @Override
        public String toString() {
            return this.label;
        }
    }

    private ClassicDestinations() {}

    /**
     * The colonies in the order they were founded: the turn, then FreeCol's
     * id number (stable across a save and a load, unlike the player's
     * settlement list).
     */
    static final Comparator<Colony> FOUNDING_ORDER
        = Comparator.comparingInt((Colony c) -> (c.getEstablished() == null) ? 0
                                  : c.getEstablished().getNumber())
            .thenComparingInt(Colony::getIdNumber);

    /**
     * The rows of a unit's destination list (class comment).
     *
     * @param t The original texts, or null (then FreeCol's name of Europe).
     * @param unit The unit, or null.
     * @return The rows, empty when there is nowhere to go.
     */
    static List<Row> rows(ClassicText t, Unit unit) {
        final List<Row> out = new ArrayList<>();
        if (unit == null || !unit.hasTile() || unit.isOnCarrier()) return out;
        final Player owner = unit.getOwner();
        if (owner == null) return out;
        final boolean naval = unit.isNaval();
        final List<Colony> colonies = new ArrayList<>(owner.getColonyList());
        colonies.sort(FOUNDING_ORDER);
        final List<Row> ports = new ArrayList<>();
        for (Colony c : colonies) {
            if (c == unit.getSettlement() || c.getTile() == null) continue;
            if (naval) {
                if (!c.isConnectedPort()) continue;
            } else if (!Map.isSameContiguity(unit.getLocation(), c.getTile())) {
                continue;
            }
            if (unit.getTurnsToReach(c) >= Unit.MANY_TURNS) continue;
            ports.add(new Row(ClassicAdvisorBox.literal(c.getName()), c));
        }
        final Row home = (naval) ? homePortRow(t, unit) : null;
        if (home != null && !HOME_PORT_LAST) out.add(home);
        out.addAll(ports);
        if (home != null && HOME_PORT_LAST) out.add(home);
        return out;
    }

    /**
     * The home port's row, if the ship can sail there: the same test as
     * BEFEHLE "Zurück nach Europa" ({@code ReturnToEuropeAction}), and a
     * way to the high seas (one path search, as FreeCol's own dialog).
     *
     * @param t The original texts, or null.
     * @param unit The ship.
     * @return The row, or null.
     */
    private static Row homePortRow(ClassicText t, Unit unit) {
        final Player owner = unit.getOwner();
        if (!ReturnToEuropeAction.canReturnToEurope(unit)
            || !owner.canMoveToEurope()) return null;
        final Europe europe = owner.getEurope();
        if (unit.getTurnsToReach(europe) >= Unit.MANY_TURNS) return null;
        return new Row(homePortLabel(t, owner), europe);
    }

    /**
     * The original nation index of a player (England 0 .. Holland 3).
     *
     * @param p The player, or null.
     * @return The index, or -1 for any other nation.
     */
    static int nation(Player p) {
        return (p == null) ? -1
            : Arrays.asList(ClassicNewWorldScreens.NATION_IDS).indexOf(p.getNationId());
    }

    /**
     * The home port's name, NAMES.TXT {@code @HOMEPORT} ("Amsterdam").
     *
     * @param t The original texts, or null.
     * @param nation The original nation index.
     * @return The name, or null.
     */
    static String homePort(ClassicText t, int nation) {
        return column(t, "HOMEPORT", nation);
    }

    /**
     * The home port's row: {@code @HOMEPORT} + " (" + {@code @COUNTRY} +
     * ")", "Amsterdam (Holland)" (V landfall #23247, opening_051); without
     * the pack, or for another nation, FreeCol's name of the player's
     * Europe.
     *
     * @param t The original texts, or null.
     * @param p The player.
     * @return The row's text.
     */
    static String homePortLabel(ClassicText t, Player p) {
        final String label = homePortLabel(t, nation(p));
        if (label != null) return label;
        final Europe europe = (p == null) ? null : p.getEurope();
        return ClassicAdvisorBox.literal((europe == null) ? ""
            : Messages.message(europe.getLocationLabelFor(p)));
    }

    /**
     * The home port's row of an original nation, "Amsterdam (Holland)".
     *
     * @param t The original texts, or null.
     * @param nation The original nation index.
     * @return The row's text, or null without the texts.
     */
    static String homePortLabel(ClassicText t, int nation) {
        final String port = homePort(t, nation), country = column(t, "COUNTRY", nation);
        return (port == null || country == null) ? null : port + " (" + country + ")";
    }

    /** A NAMES.TXT row's first column, trimmed, or null. */
    private static String column(ClassicText t, String section, int row) {
        if (t == null || row < 0) return null;
        final List<String[]> rows = t.names(section);
        if (rows == null || row >= rows.size() || rows.get(row).length == 0) return null;
        final String s = rows.get(row)[0].trim();
        return (s.isEmpty()) ? null : s;
    }

    /**
     * The destination box (class comment): GAME.TXT's @SAILPORT or
     * @TRAVELPLACE with the rows, else FreeCol's "Zielort auswählen" with
     * the same rows and keys.
     *
     * @param t The original texts, or null.
     * @param naval A ship's list (@SAILPORT), else a land unit's.
     * @param rows The rows.
     * @return The box.
     */
    static ClassicAdvisorBox.Request request(ClassicText t, boolean naval,
                                             List<Row> rows) {
        final String section = (naval) ? SAIL_PORT_SECTION : TRAVEL_PLACE_SECTION;
        ClassicAdvisorBox.Builder b = ClassicAdvisorBox.fromGameText(section,
            (t == null) ? null : t.message(section), null);
        if (b == null) {
            b = ClassicAdvisorBox.Request.builder(section)
                .freeColText(Messages.message("selectDestinationDialog.text"))
                .defaultRow(0);
        }
        final List<String> labels = new ArrayList<>(rows.size());
        for (Row r : rows) labels.add(r.label);
        return b.rows(labels).rowIndent(ClassicMenuBox.PORT_INDENT)
            .noCancelRow().portrait(ClassicAdvisorBox.Portrait.NONE)
            .outsideCancels(true)
            .stopgap(Messages.message("selectDestinationDialog.text"), null)
            .list().build();
    }
}
