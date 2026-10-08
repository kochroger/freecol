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
import net.sf.freecol.common.model.Europe;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Unit;


/**
 * The original's voyage bands on the top strip (master plan W13, spec R4
 * section 3) and the text of {@code @TUTORIAL17}.  Headless.
 *
 * <p>Every band is one gold line, centred ({@link ClassicMenuBar#paintBand}):
 * NAMES.TXT {@code @NATIONALITY[n]} + ' ' + {@code @UNIT[ship type]} + ' ' +
 * LABELS.TXT {@code @MISC} k + ' ' + {@code @HOMEPORT[n]}, the ship's type,
 * never its name:
 * <ul>
 *   <li>k = 5 "Ziel:" when a ship leaves the map for Europe, "Holl.
 *       Handelsschiff Ziel: Amsterdam" (landfall #25735, clip008 #44180);</li>
 *   <li>k = 7 "Trifft jetzt ein in" when it arrives there (landfall #27878,
 *       clip005 #18613, clip006 #7942, clip008 #52677);</li>
 *   <li>k = 6 "Ankunft aus" when it comes back to the New World (clip008
 *       #26209; the first scene's band, {@link ClassicFirstScene#bandText}).</li>
 * </ul>
 * A nation or ship type the original lacks, or a pack without the texts,
 * gets FreeCol's words for the missing pieces, never an empty band.
 */
final class ClassicBands {

    /** LABELS.TXT {@code @MISC} "Ziel:" (LABELS.TXT:20). */
    static final int MISC_DESTINATION = 5;

    /** LABELS.TXT {@code @MISC} "Ankunft aus" (LABELS.TXT:21). */
    static final int MISC_ARRIVAL_FROM = ClassicFirstScene.MISC_ARRIVAL;

    /** LABELS.TXT {@code @MISC} "Trifft jetzt ein in" (LABELS.TXT:22). */
    static final int MISC_ARRIVES_IN = 7;

    /** GAME.TXT's tip at the first Europe screen of a game. */
    static final String TUTORIAL_EUROPE = "TUTORIAL17";

    /** Its number (the bit of {@code Player.classicTips}). */
    static final int TUTORIAL_EUROPE_NUMBER = 17;


    private ClassicBands() {}   // static helpers only


    /**
     * "Holl. Handelsschiff Ziel: Amsterdam".
     *
     * @param t The original texts, or null.
     * @param ship The ship.
     * @return The band.
     */
    static String departure(ClassicText t, Unit ship) {
        return band(t, ship, MISC_DESTINATION);
    }

    /**
     * "Holl. Handelsschiff Trifft jetzt ein in Amsterdam".
     *
     * @param t The original texts, or null.
     * @param ship The ship.
     * @return The band.
     */
    static String arrivalEurope(ClassicText t, Unit ship) {
        return band(t, ship, MISC_ARRIVES_IN);
    }

    /**
     * "Holl. Handelsschiff Ankunft aus Amsterdam".
     *
     * @param t The original texts, or null.
     * @param ship The ship.
     * @return The band.
     */
    static String arrivalNewWorld(ClassicText t, Unit ship) {
        return band(t, ship, MISC_ARRIVAL_FROM);
    }

    /**
     * A band of a ship of an original nation: nationality, ship type,
     * label, home port.
     *
     * @param t The original texts, or null.
     * @param nation The original nation index (England 0 .. Holland 3).
     * @param shipRow The ship type's {@code @UNIT} row.
     * @param misc The LABELS.TXT {@code @MISC} index.
     * @return The band, or null when a piece is missing.
     */
    static String band(ClassicText t, int nation, int shipRow, int misc) {
        if (t == null) return null;
        final String nat = cell(t, "NATIONALITY", nation);
        final String ship = cell(t, "UNIT", shipRow);
        final String label = label(t, misc);
        final String port = cell(t, "HOMEPORT", nation);
        if (nat == null || ship == null || label == null || port == null) return null;
        return nat + " " + ship + " " + label + " " + port;
    }

    /**
     * The band of {@code ship} with label {@code misc}: the original's
     * pieces where it has them, FreeCol's words for the rest.
     *
     * @param t The original texts, or null.
     * @param ship The ship.
     * @param misc The LABELS.TXT {@code @MISC} index.
     * @return The band, never empty.
     */
    static String band(ClassicText t, Unit ship, int misc) {
        final Player owner = (ship == null) ? null : ship.getOwner();
        final int nation = ClassicDestinations.nation(owner);
        final int row = (ship == null) ? -1
            : ClassicFirstScene.shipRow(ship.getType().getId());
        final String all = band(t, nation, row, misc);
        if (all != null) return all;
        String nat = cell(t, "NATIONALITY", nation);
        if (nat == null && owner != null) nat = Messages.message(owner.getNationLabel());
        String type = cell(t, "UNIT", row);
        if (type == null && ship != null) type = Messages.getName(ship.getType());
        String port = cell(t, "HOMEPORT", nation);
        final Europe europe = (owner == null) ? null : owner.getEurope();
        if (port == null && europe != null) port = Messages.message(europe.getNameKey());
        final StringBuilder sb = new StringBuilder();
        for (String s : new String[] { nat, type, label(t, misc), port }) {
            if (s == null || s.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(s);
        }
        return (sb.length() == 0) ? "-" : sb.toString();
    }

    /**
     * {@code @TUTORIAL17}'s values: {@code %STRING0} the home port,
     * {@code %STRING1} the country, {@code %STRING2} the New World's name
     * (the player's, else NAMES.TXT {@code @COLONYNAME}, "Neuholland").
     *
     * @param t The original texts, or null.
     * @param p The player.
     * @return The values (missing ones left out).
     */
    static Map<String, String> tutorialValues(ClassicText t, Player p) {
        final Map<String, String> v = new HashMap<>();
        final int nation = ClassicDestinations.nation(p);
        put(v, "STRING0", cell(t, "HOMEPORT", nation));
        put(v, "STRING1", cell(t, "COUNTRY", nation));
        final String land = landName(p);
        put(v, "STRING2", (land != null) ? land : defaultLandName(t, p));
        return v;
    }

    /**
     * The player's name for the New World (W10): FreeCol's, set at the
     * first landing, else the one taken at the first sighting
     * ({@code Player.classicLandName}) that the first landing will send.
     *
     * @param p The player, or null.
     * @return The name, or null (not named yet: the nation's default).
     */
    static String landName(Player p) {
        if (p == null) return null;
        final String n = p.getNewLandName();
        if (n != null && !n.isEmpty()) return n;
        return p.getClassicLandName();
    }

    /**
     * The nation's default name for the New World: NAMES.TXT
     * {@code @COLONYNAME} ("Neuholland" for the Dutch; landfall #2664,
     * GAME.TXT's {@code @default=America} is not used).
     *
     * @param t The original texts, or null.
     * @param p The player, or null.
     * @return The name, or null.
     */
    static String defaultLandName(ClassicText t, Player p) {
        return cell(t, "COLONYNAME", ClassicDestinations.nation(p));
    }

    private static void put(Map<String, String> v, String k, String s) {
        if (s != null) v.put(k, s);
    }

    /** A LABELS.TXT {@code @MISC} entry, trimmed, or null. */
    private static String label(ClassicText t, int misc) {
        final String s = (t == null) ? null : t.misc(misc);
        return (s == null || s.trim().isEmpty()) ? null : s.trim();
    }

    /** A NAMES.TXT row's first column, trimmed, or null. */
    private static String cell(ClassicText t, String section, int row) {
        if (t == null || row < 0) return null;
        final List<String[]> rows = t.names(section);
        if (rows == null || row >= rows.size() || rows.get(row).length == 0) return null;
        final String s = rows.get(row)[0].trim();
        return (s.isEmpty()) ? null : s;
    }
}
