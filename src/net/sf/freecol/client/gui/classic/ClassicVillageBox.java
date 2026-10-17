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

import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Tension;


/**
 * The box a unit of ours gets on entering a native village (part Z,
 * Z-ITEMS Z2.1; M3 spec A1 with the verifier's W2; Roger's test of
 * 2026-10-16, events #11 and #16: FreeCol's mood sentence and its rows
 * «Einen Tribut fordern / Angriff / Abbrechen»).  Pure and headless:
 * {@link ClassicGUI}'s village seams ask it.
 *
 * <p><b>The original (V).</b>  One box, no portrait, the prompt chosen by
 * the village's mood, the rows from NAMES {@code @ACTIONS} at the box's x
 * + 9, the bar on row 1, the box's height from its lines and rows (h = 6
 * lines + 8 rows + 18, y = (200 - h) div 2):
 * <ul>
 *   <li>the scout: «Mit dem Häuptling sprechen / Zoll fordern / Dorf
 *       angreifen / Handlung abbrechen» (playthrough-1 #46977 (42,66,236,68)
 *       Apache; playthrough-4 046 #114127 and #125403 @VILLAGESAVAGE
 *       (42,63,236,74));</li>
 *   <li>a colonist (free colonist, servant, criminal, expert, pioneer):
 *       «Mit den Ureinwohnern leben / Handlung abbrechen» (playthrough-1
 *       #8480 (42,74,236,52) Iroquois; landfall's pioneer
 *       @VILLAGESAVAGE);</li>
 *   <li>a soldier or dragoon: «Dorf angreifen / Zoll fordern / Handlung
 *       abbrechen», the bar on «Dorf angreifen», so Enter attacks (Roger's
 *       decision F4, built as the original: playthrough-4 045 #30725
 *       @VILLAGESAVAGE (42,67,236,66) and 048 #44568 @VILLAGEHAPPY
 *       (42,70,236,60); war-french #16955 @VILLAGEMEDIUM (42,67,236,66),
 *       #22020 @VILLAGEBAD (42,73,236,54));</li>
 *   <li>the missionary: «Mission gründen / Indianer aufwiegeln / Handlung
 *       abbrechen» (playthrough-3 #107633 (42,70,236,60) Aztec); with
 *       another nation's mission there «Span. Mission der Ketzerei
 *       beschuldigen» takes row 1's place (playthrough-4 047 #9331, NAMES'
 *       {@code %Fs} the mission's nation, {@code @NATIONABBREV}); with our
 *       own mission there neither row (I: not seen; FreeCol offers
 *       neither);</li>
 *   <li>a wagon train or a ship with goods: «Mit Dorf handeln / Handlung
 *       abbrechen» (playthrough-3 #124831 (42,74,236,52) Iroquois).</li>
 * </ul>
 * The prompt's {@code %STRING0} is NAMES {@code @LEVELS} column 2 at the
 * tribe's level («ein Lager», «ein Dorf», «eine Stadt»: V for the Apache,
 * Tupi, Sioux, Iroquois, Arawak, Inca and Aztec), a capital's «eine
 * Hauptstadt» ({@code @LEVELS} row 5; I for this box: V in @LOOT,
 * playthrough-4 048 #8605 «… plündern {eine Hauptstadt} der Sioux»),
 * {@code %STRING1} the tribe.
 *
 * <p><b>The mood (I, one table, {@link #moodTag}).</b>  The village's
 * alarm towards us, FreeCol's: HAPPY and CONTENT @VILLAGEHAPPY
 * (@VILLAGESAVAGE for the Arawak, the only tribe it was seen with),
 * DISPLEASED @VILLAGEMEDIUM, ANGRY @VILLAGEBAD, HATEFUL or at war
 * @VILLAGEWAR (never seen; Roger's event #16 had FreeCol's hateful line
 * there).
 *
 * <p>Not offered: NAMES' «Feindliches Dorf betreten» (never seen) and the
 * scout's «Indianer aufwiegeln» (in NAMES, never offered to a scout,
 * playthrough-1 #46977; FreeCol has no such scout action).  An artillery
 * gets the armed unit's rows (I: the manual names soldiers, dragoons and
 * scouts for the tribute; FreeCol lets any armed unit demand it).
 */
final class ClassicVillageBox {

    /** The unit's kind of box. */
    enum Kind {
        /** A scout. */
        SCOUT,
        /** A colonist who may live with them (FreeCol's learn path). */
        COLONIST,
        /** An armed unit (soldier, dragoon, artillery). */
        ARMED,
        /** A missionary. */
        MISSIONARY,
        /** A wagon train or a ship with goods. */
        TRADE
    }

    /** NAMES {@code @ACTIONS}' rows, 0-based. */
    static final int TRADE = 0, HOSTILE = 1, ESTABLISH = 2, DENOUNCE = 3,
        LIVE = 4, SPEAK = 5, INCITE = 6, TRIBUTE = 7, ATTACK = 8, CANCEL = 9;

    /** The moods' GAME.TXT sections. */
    static final String HAPPY = "VILLAGEHAPPY", SAVAGE = "VILLAGESAVAGE",
        MEDIUM = "VILLAGEMEDIUM", BAD = "VILLAGEBAD", WAR = "VILLAGEWAR";

    /** The Arawak's {@link ClassicGUI#TRIBES} index (@VILLAGESAVAGE). */
    static final int ARAWAK = 2;

    /** NAMES {@code @LEVELS}' row of a capital («eine Hauptstadt»). */
    static final int CAPITAL_LEVEL = 4;

    /** NAMES' placeholder of the heresy row: the other mission's nation. */
    static final String MISSION_NATION = "Fs";

    /** One frame of the clips (70.0863 Hz), in ms. */
    private static final double FRAME_MS = 1000.0 / 70.0863;

    /**
     * The chief's refusal after the village box (@LEARNMASTER,
     * @LEARNCRIMINAL): 18 frames after its close (V playthrough-4 048
     * #20677 -&gt; #20695, the M3 verifier's W5), the chief's palette 3 of
     * them ({@link ClassicAdvisorLayer#PALETTE_LEAD_FRAMES}).
     */
    static final double ANSWER_CHAIN_MS = 15 * FRAME_MS;

    /**
     * @LEARNALREADY after the village box: 20 frames after its close (V
     * playthrough-4 048 #44164 -&gt; #44184, measured as W5 asks), the
     * chief's palette 3 of them.
     */
    static final double ALREADY_CHAIN_MS = 17 * FRAME_MS;

    /**
     * @LEARNSTAY after the village box: 19 frames after its close (V
     * playthrough-1 #9008 -&gt; #9027), the chief's palette 3 of them.
     */
    static final double STAY_CHAIN_MS = 16 * FRAME_MS;

    /**
     * The chief's answer to «Zoll fordern» (@EXTORTSTUFF, @EXTORTPOOR,
     * @EXTORTLAUGH, @EXTORTNO; part Z, Z2.2): 15-18 frames after the
     * village box's close (the M3 verifier's W4: 045 #30817 -&gt; #30832,
     * 048 #44709 -&gt; #44727, #48430 -&gt; #48446, war-french #17321 -&gt;
     * #17339), 17 here, the chief's palette 3 of them.
     */
    static final double TRIBUTE_CHAIN_MS = 14 * FRAME_MS;

    private ClassicVillageBox() {}

    /**
     * The rows of a unit's box (class comment), as NAMES {@code @ACTIONS}
     * rows, «Handlung abbrechen» last.
     *
     * @param kind The unit's kind.
     * @param canEstablish For a missionary: no mission there yet.
     * @param canDenounce For a missionary: another nation's mission there.
     * @return The rows.
     */
    static int[] rows(Kind kind, boolean canEstablish, boolean canDenounce) {
        switch (kind) {
        case SCOUT:
            return new int[] { SPEAK, TRIBUTE, ATTACK, CANCEL };
        case COLONIST:
            return new int[] { LIVE, CANCEL };
        case ARMED:
            return new int[] { ATTACK, TRIBUTE, CANCEL };
        case MISSIONARY:
            return (canEstablish) ? new int[] { ESTABLISH, INCITE, CANCEL }
                : (canDenounce) ? new int[] { DENOUNCE, INCITE, CANCEL }
                : new int[] { INCITE, CANCEL };
        case TRADE: default:
            return new int[] { TRADE, CANCEL };
        }
    }

    /**
     * The mood's section (class comment; I).
     *
     * @param level The village's alarm towards us, or null (as HAPPY).
     * @param tribe The tribe's {@link ClassicGUI#TRIBES} index.
     * @param atWar Whether the tribe is at war with us.
     * @return The section.
     */
    static String moodTag(Tension.Level level, int tribe, boolean atWar) {
        if (atWar || level == Tension.Level.HATEFUL) return WAR;
        if (level == Tension.Level.ANGRY) return BAD;
        if (level == Tension.Level.DISPLEASED) return MEDIUM;
        return (tribe == ARAWAK) ? SAVAGE : HAPPY;
    }

    /**
     * The mood of a village towards a player (class comment).
     *
     * @param is The village.
     * @param me Our player.
     * @return The section.
     */
    static String moodTag(IndianSettlement is, Player me) {
        final Player owner = is.getOwner();
        final Tension alarm = is.getAlarm(me);
        return moodTag((alarm == null) ? null : alarm.getLevel(),
            ClassicGUI.tribeIndex(owner), owner != null && owner.atWarWith(me));
    }

    /**
     * The prompt's values (class comment): {@code STRING0} NAMES
     * {@code @LEVELS} column 2 at the tribe's level ({@code @TRIBES}
     * column 4), a capital's «eine Hauptstadt»; {@code STRING1} the tribe.
     *
     * @param t The original texts.
     * @param tribe The tribe's {@link ClassicGUI#TRIBES} index.
     * @param capital Whether the village is the tribe's capital.
     * @return The values, or null when one is not known.
     */
    static Map<String, String> values(ClassicText t, int tribe, boolean capital) {
        final List<String[]> tribes = (t == null) ? null : t.names("TRIBES");
        if (tribes == null || tribe < 0 || tribe >= tribes.size()
            || tribes.get(tribe).length < 4) return null;
        int level;
        try {
            level = Integer.parseInt(tribes.get(tribe)[3]);
        } catch (NumberFormatException e) {
            return null;
        }
        if (capital) level = CAPITAL_LEVEL;
        final List<String[]> levels = t.names("LEVELS");
        if (level < 0 || level >= levels.size() || levels.get(level).length < 2) {
            return null;
        }
        final String kind = levels.get(level)[1], name = tribes.get(tribe)[0];
        if (kind.isEmpty() || name.isEmpty()) return null;
        final Map<String, String> v = new HashMap<>();
        v.put("STRING0", kind);
        v.put("STRING1", name);
        return v;
    }

    /**
     * The box (class comment): the mood's prompt, the unit's rows, the bar
     * on row 1, Escape on «Handlung abbrechen».
     *
     * @param t The original texts, or null.
     * @param tag The mood's section ({@link #moodTag}).
     * @param tribe The tribe's {@link ClassicGUI#TRIBES} index.
     * @param capital Whether the village is the tribe's capital.
     * @param actions The rows ({@link #rows}).
     * @param missionNation For the heresy row: NAMES {@code @NATIONABBREV}
     *     of the other mission's nation («Span.»), else null.
     * @return The box's builder, or null without the texts or a value
     *     (FreeCol's box then).
     */
    static ClassicAdvisorBox.Builder builder(ClassicText t, String tag, int tribe,
                                             boolean capital, int[] actions,
                                             String missionNation) {
        final ClassicText.Message m = (t == null) ? null : t.message(tag);
        final Map<String, String> values = (m == null) ? null
            : values(t, tribe, capital);
        final List<String[]> names = (values == null) ? null : t.names("ACTIONS");
        if (names == null || actions == null || actions.length == 0) return null;
        final Map<String, String> rowValues = new HashMap<>();
        if (missionNation != null) rowValues.put(MISSION_NATION, missionNation);
        final List<String> rows = new ArrayList<>(actions.length);
        for (int a : actions) {
            if (a < 0 || a >= names.size() || names.get(a).length == 0
                || names.get(a)[0].isEmpty()) return null;
            final String row = ClassicText.substitute(names.get(a)[0], rowValues);
            if (row.indexOf('%') >= 0) return null;   // a value not known
            rows.add(row);
        }
        final ClassicAdvisorBox.Builder b = ClassicAdvisorBox.fromGameText(tag, m, values);
        return (b == null) ? null
            : b.rows(rows).defaultRow(0).cancelRow(rows.size() - 1);
    }

    /**
     * A nation's NAMES {@code @NATIONABBREV} («Span.»), for the heresy row.
     *
     * @param t The original texts, or null.
     * @param p The nation's player, or null.
     * @return The abbreviation, or null for a nation the original does not
     *     have.
     */
    static String nationAbbrev(ClassicText t, Player p) {
        final int i = (p == null) ? -1 : java.util.Arrays.asList(
            ClassicNewWorldScreens.NATION_IDS).indexOf(p.getNationId());
        final List<String[]> rows = (t == null) ? null : t.names("NATIONABBREV");
        if (rows == null || i < 0 || i >= rows.size() || rows.get(i).length == 0
            || rows.get(i)[0].isEmpty()) return null;
        return rows.get(i)[0];
    }

    /**
     * The row taken.
     *
     * @param r The box asked.
     * @param actions Its rows ({@link #rows}).
     * @param answer Its answer.
     * @return The NAMES {@code @ACTIONS} row taken, or -1 for «Handlung
     *     abbrechen», Escape, a greyed row or no answer.
     */
    static int action(ClassicAdvisorBox.Request r, int[] actions, int answer) {
        if (r == null || actions == null || !r.enabled(answer)
            || answer >= actions.length || actions[answer] == CANCEL) return -1;
        return actions[answer];
    }

    /**
     * The recorder's words of a box ({@code village-box <unit> <tag>
     * <rows> <bar>}).
     *
     * @param unitId The unit's id, or null.
     * @param r The box.
     * @return The event's text.
     */
    static String event(String unitId, ClassicAdvisorBox.Request r) {
        return ((unitId == null) ? "-" : unitId) + " " + r.id + " "
            + r.rows.size() + " " + r.defaultRow;
    }
}
