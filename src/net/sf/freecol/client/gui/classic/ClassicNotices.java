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

import java.awt.Image;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sf.freecol.common.model.FreeColObject;
import net.sf.freecol.common.model.Ownable;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.StringTemplate;


/**
 * The notices (master plan N1): FreeCol's information messages
 * ({@code GUI.showInformationPanel}, which the base GUI drops) and its model
 * messages, each as one advisor box (W7) without rows, in the original's
 * words where GAME.TXT has the same notice, else in FreeCol's.
 *
 * <p><b>The original's words.</b>  {@link #RULES} maps a FreeCol message id
 * to its GAME.TXT section, the portrait and the placeholders' values:
 * <ul>
 *   <li>The chief's answers when a scout speaks to him (clip008 01-scout
 *       section 4): @CHIEFGIFT (V #51430: the tribe, "Holl."
 *       {@code @NATIONABBREV}, the beads' worth with the coin),
 *       @CHIEFGUIDES (V #46396), @CHIEFAREA, @CHIEFBORED, @CHIEFKILL (I),
 *       with the tribe's chief at the right.  @CHIEFHOWDY before them and
 *       @WELLSEASONED after the guides are D11's.</li>
 *   <li>A village that teaches no more (@LEARNALREADY, V text clip008
 *       08:50), one that sends the colonist away (@LEARNMAD) or kills him
 *       (@CHIEFKILL, I), with the chief.</li>
 *   <li>The lost city rumours' results, with the frontiersman (landfall
 *       #20177: @BURIAL1, V): @LOSTCITY1-3, 5-7 and 9, @BURIAL1-3, and
 *       @SCREWED for a burial ground (with the tribe's chief, I).</li>
 *   <li>A unit that cannot found a colony: @ONLYCOL (I).</li>
 * </ul>
 * A rule whose value is missing (a tribe or nation the original does not
 * have) gives FreeCol's words instead.
 *
 * <p><b>Silent.</b>  A key while it is not our turn does nothing (W5d;
 * {@code info.notYourTurn}).  FreeCol's illegal-move messages
 * ({@code move.noAccess*}, {@code move.noAttackWater}, {@code move.noTile})
 * stay silent here too, but only as the backstop of a goto's failed last
 * step: a move key never reaches them, {@link ClassicIllegalMoves} answers
 * it first with the original's refusal box (GAME.TXT has one for most of
 * them, R3; the earlier "the original shows nothing" was an inference,
 * clip008 11-spec-delta.md:106, and wrong).
 *
 * <p><b>Dropped</b> (part K3, Roger 2026-10-08: "den gibt es auch gar nicht
 * im Original - bitte löschen").  Two of FreeCol's notices at a first
 * contact have nothing in GAME.TXT and never come:
 * <ul>
 *   <li>{@link #SCOUT_MEETING}, "Ihr trefft auf einen Späher der %nation%
 *       aus %settlement%.", which the server sends once per village, not
 *       per tribe: when our unit comes next to a village or to one of its
 *       people, and in the natives' turn when one of them comes next to
 *       ours ({@code ServerUnit.csNewContactCheck}).  The meeting stays the
 *       woodcut and the chief's box.</li>
 *   <li>{@link #OFFER_ACCEPTED} and {@link #OFFER_REJECTED}, "%nation% hat
 *       Euer großzügiges Angebot angenommen" (abgelehnt), when they only
 *       echo our own answer to the other nation's proposal
 *       ({@link #isAnswerEcho}).  The server sends the session's result to
 *       both sides ({@code DiplomacySession.completeInternal}) and the
 *       client turns it into this notice for whoever gets it
 *       ({@code InGameController.diplomacyHandler}); at a European first
 *       contact the Classic UI accepts the peace without a box (G1), so the
 *       player made no offer at all.</li>
 * </ul>
 */
final class ClassicNotices {

    /** Who stands at a notice. */
    enum Who {
        /** No portrait. */
        NONE,
        /** The chief of the tribe the notice is about. */
        CHIEF,
        /** The frontiersman (MSS3), as at the rumours. */
        SCOUT,
        /** The admiral (MSS0), as at @SAILHOME. */
        ADMIRAL,
        /** The soldier (MSS1), as at @WHACKINDIANS. */
        SOLDIER
    }

    /** What a placeholder takes. */
    enum Value {
        /** NAMES {@code @TRIBES}: the tribe ("Araukaner"). */
        TRIBE,
        /** NAMES {@code @NATIONABBREV}: our nation ("Holl."). */
        NATION_ABBREV,
        /** NAMES {@code @COUNTRY}: our country ("Holland"). */
        COUNTRY,
        /** FreeCol's {@code %amount%}. */
        AMOUNT,
        /** FreeCol's {@code %money%}. */
        MONEY
    }

    /** One notice in the original's words. */
    static final class Rule {

        /** The GAME.TXT section. */
        final String section;

        /** Who stands at it. */
        final Who who;

        /** The placeholders: GAME.TXT's name ({@code STRING0}) and value, in pairs. */
        final Object[] values;

        Rule(String section, Who who, Object... values) {
            this.section = section;
            this.who = who;
            this.values = values;
        }
    }

    /** The prefix of FreeCol's ordinary "nothing" rumours ({@code NameCache}). */
    private static final String NOTHING = "model.lostCityRumour.nothing.";

    /** FreeCol's notices in the original's words (class comment). */
    static final Map<String, Rule> RULES = new HashMap<>();
    static {
        RULES.put("scoutSettlement.speakBeads", new Rule("CHIEFGIFT", Who.CHIEF,
            "STRING0", Value.TRIBE, "STRING1", Value.NATION_ABBREV,
            "NUMBER0", Value.AMOUNT));
        RULES.put("scoutSettlement.expertScout", new Rule("CHIEFGUIDES", Who.CHIEF,
            "STRING0", Value.TRIBE));
        RULES.put("scoutSettlement.speakTales", new Rule("CHIEFAREA", Who.CHIEF,
            "STRING0", Value.TRIBE));
        RULES.put("scoutSettlement.speakNothing", new Rule("CHIEFBORED", Who.CHIEF,
            "STRING0", Value.TRIBE, "STRING1", Value.NATION_ABBREV));
        RULES.put("scoutSettlement.speakDie", new Rule("CHIEFKILL", Who.CHIEF,
            "STRING0", Value.TRIBE));
        RULES.put("info.noMoreSkill", new Rule("LEARNALREADY", Who.CHIEF,
            "STRING0", Value.TRIBE));
        RULES.put("learnSkill.leave", new Rule("LEARNMAD", Who.CHIEF));
        RULES.put("learnSkill.die", new Rule("CHIEFKILL", Who.CHIEF,
            "STRING0", Value.TRIBE));
        RULES.put("buildColony.badUnit", new Rule("ONLYCOL", Who.NONE));
        RULES.put("model.lostCityRumour.burialGround.description",
            new Rule("SCREWED", Who.CHIEF, "STRING0", Value.TRIBE));
        RULES.put("model.lostCityRumour.expeditionVanishes.description",
            new Rule("LOSTCITY5", Who.SCOUT));
        RULES.put("model.lostCityRumour.nothing.mounds.description",
            new Rule("BURIAL1", Who.SCOUT));
        RULES.put("model.lostCityRumour.tribalChief.description",
            new Rule("LOSTCITY7", Who.SCOUT, "NUMBER0", Value.MONEY));
        RULES.put("model.lostCityRumour.tribalChief.mounds.description",
            new Rule("BURIAL2", Who.SCOUT, "NUMBER0", Value.MONEY));
        RULES.put("model.lostCityRumour.ruins.description",
            new Rule("LOSTCITY3", Who.SCOUT, "NUMBER0", Value.MONEY));
        RULES.put("model.lostCityRumour.ruins.mounds.description",
            new Rule("BURIAL3", Who.SCOUT, "NUMBER1", Value.MONEY));
        RULES.put("model.lostCityRumour.cibola.description",
            new Rule("LOSTCITY2", Who.SCOUT, "NUMBER1", Value.MONEY));
        RULES.put("model.lostCityRumour.fountainOfYouth.description",
            new Rule("LOSTCITY1", Who.SCOUT));
        RULES.put("model.lostCityRumour.colonist.description",
            new Rule("LOSTCITY9", Who.SCOUT, "STRING0", Value.COUNTRY));
    }

    /** FreeCol's notices the original does not show (class comment). */
    static final List<String> SILENT = Arrays.asList("move.noAttackWater",
        "move.noTile", "info.notYourTurn");

    /** FreeCol's notice of a meeting with a village's people (class comment). */
    static final String SCOUT_MEETING = "model.unit.nativeSettlementContact";

    /** FreeCol's notice that the other nation accepted "our" offer. */
    static final String OFFER_ACCEPTED = "diplomacy.offerAccepted";

    /** FreeCol's notice that the other nation rejected "our" offer. */
    static final String OFFER_REJECTED = "diplomacy.offerRejected";

    /** FreeCol's model messages that never come (class comment). */
    static final List<String> DROPPED = Arrays.asList(SCOUT_MEETING);


    private ClassicNotices() {}   // static helpers only

    /**
     * Whether a notice is never shown (class comment).
     *
     * @param id The message id, or null.
     * @return True if the original shows nothing there.
     */
    static boolean silent(String id) {
        return id != null && (id.startsWith("move.noAccess") || SILENT.contains(id));
    }

    /**
     * Whether a model message never comes (class comment): FreeCol's
     * meeting with a village's people.
     *
     * @param id The message id, or null.
     * @return True if it is dropped.
     */
    static boolean dropped(String id) {
        return id != null && DROPPED.contains(id);
    }

    /**
     * The key of a nation in {@link #isAnswerEcho}: the id of its label,
     * as FreeCol's notice names it ({@code Player.getNationLabel}).
     *
     * @param p The player, or null.
     * @return The key, or null.
     */
    static String nationKey(Player p) {
        final StringTemplate t = (p == null) ? null : p.getNationLabel();
        return (t == null) ? null : t.getId();
    }

    /**
     * Whether a notice only echoes our own answer to another nation's
     * proposal (class comment): "accepted" or "rejected" about a nation
     * whose proposal we answered.  Our own proposals are not built yet
     * ({@code ClassicSeams.isOwnProposal}: the "not yet" notice, nothing is
     * sent); once they are, sending one must take its nation out of
     * {@code answered}, so that its real answer is shown.
     *
     * @param t The notice (a model message or an information message), or null.
     * @param answered The keys ({@link #nationKey}) of the nations whose
     *     proposal we answered last.
     * @return True if it is dropped.
     */
    static boolean isAnswerEcho(StringTemplate t, Set<String> answered) {
        if (t == null || answered == null
            || !(OFFER_ACCEPTED.equals(t.getId()) || OFFER_REJECTED.equals(t.getId()))) {
            return false;
        }
        final StringTemplate n = t.getReplacement("%nation%");
        return n != null && n.getId() != null && answered.contains(n.getId());
    }

    /**
     * The rule of a message id, the ordinary "nothing" rumours included.
     *
     * @param id The id, or null.
     * @return The rule, or null for FreeCol's words.
     */
    static Rule rule(String id) {
        if (id == null) return null;
        final Rule r = RULES.get(id);
        if (r != null) return r;
        if (id.startsWith(NOTHING) && id.endsWith(".description")) {
            final String mid = id.substring(NOTHING.length(),
                id.length() - ".description".length());
            if (!mid.isEmpty() && mid.chars().allMatch(Character::isDigit)) {
                return new Rule("LOSTCITY6", Who.SCOUT);
            }
        }
        return null;
    }

    /**
     * The tribe a notice is about: the owner of its display object (a
     * village), else the nation named by its {@code %nation%} (a burial
     * ground's message).
     *
     * @param template The message.
     * @param display The display object, or null.
     * @return The tribe's {@link ClassicGUI#TRIBES} index, or -1.
     */
    static int tribe(StringTemplate template, FreeColObject display) {
        if (display instanceof Ownable) {
            final int i = ClassicGUI.tribeIndex(((Ownable) display).getOwner());
            if (i >= 0) return i;
        }
        final StringTemplate n = (template == null) ? null
            : template.getReplacement("%nation%");
        final String id = (n == null) ? null : n.getId();
        if (id == null || !id.startsWith("model.nation.")) return -1;
        final String rest = id.substring("model.nation.".length());
        final int dot = rest.indexOf('.');
        return ClassicGUI.TRIBES.indexOf((dot < 0) ? rest : rest.substring(0, dot));
    }

    /**
     * A notice box (class comment).
     *
     * @param t The pack's texts, or null.
     * @param id What it is, for the recorder ("notice info.noMoreSkill").
     * @param template The FreeCol message.
     * @param freeColText FreeCol's words for it.
     * @param display Its display object, or null.
     * @param me Our player, or null.
     * @param title The stopgap's window title.
     * @param icon The stopgap's illustration, or null.
     * @return The request.
     */
    static ClassicAdvisorBox.Request request(ClassicText t, String id,
                                             StringTemplate template,
                                             String freeColText,
                                             FreeColObject display, Player me,
                                             String title, Image icon) {
        final ClassicAdvisorBox.Builder b = original(t, template, display, me);
        return ((b != null) ? b : ClassicAdvisorBox.Request.builder(id)
                .freeColText(freeColText))
            .stopgap(title, icon).build();
    }

    /**
     * The original's box of a notice, if it has one and every value is
     * known.
     *
     * @return The builder, or null for FreeCol's words.
     */
    static ClassicAdvisorBox.Builder original(ClassicText t, StringTemplate template,
                                              FreeColObject display, Player me) {
        final Rule r = (template == null) ? null : rule(template.getId());
        final ClassicText.Message m = (r == null || t == null) ? null
            : t.message(r.section);
        if (m == null) return null;
        final int tribe = tribe(template, display);
        final int nation = (me == null) ? -1 : Arrays.asList(
            ClassicNewWorldScreens.NATION_IDS).indexOf(me.getNationId());
        final Map<String, String> values = new HashMap<>();
        for (int i = 0; i + 1 < r.values.length; i += 2) {
            final String v = value(t, (Value) r.values[i + 1], template, tribe, nation);
            if (v == null) return null;
            values.put((String) r.values[i], v);
        }
        if (r.who == Who.CHIEF && tribe < 0) return null;
        final ClassicAdvisorBox.Portrait p = portrait(r.who, tribe);
        final ClassicAdvisorBox.Builder b = ClassicAdvisorBox.fromGameText(r.section,
            m, values);
        return (b == null) ? null : b.portrait(p);
    }

    /**
     * Who stands at a box, as its portrait.
     *
     * @param who Who.
     * @param tribe The tribe's {@link ClassicGUI#TRIBES} index (the chief's).
     * @return The portrait; {@link ClassicAdvisorBox.Portrait#NONE} for a
     *     chief of no original tribe.
     */
    static ClassicAdvisorBox.Portrait portrait(Who who, int tribe) {
        switch (who) {
        case CHIEF:
            return ClassicAdvisorBox.Portrait.chief(tribe);
        case SCOUT:
            return ClassicAdvisorBox.Portrait.SCOUT;
        case ADMIRAL:
            return ClassicAdvisorBox.Portrait.ADMIRAL;
        case SOLDIER:
            return ClassicAdvisorBox.Portrait.SOLDIER;
        default:
            return ClassicAdvisorBox.Portrait.NONE;
        }
    }

    /** A placeholder's value, or null when it is not known. */
    private static String value(ClassicText t, Value v, StringTemplate template,
                                int tribe, int nation) {
        switch (v) {
        case TRIBE:
            return column(t.names("TRIBES"), tribe);
        case NATION_ABBREV:
            return column(t.names("NATIONABBREV"), nation);
        case COUNTRY:
            return column(t.names("COUNTRY"), nation);
        case AMOUNT:
            return replacement(template, "%amount%");
        case MONEY:
            return replacement(template, "%money%");
        default:
            return null;
        }
    }

    /** A NAMES.TXT row's first column, or null. */
    private static String column(List<String[]> rows, int i) {
        return (rows == null || i < 0 || i >= rows.size()) ? null : rows.get(i)[0];
    }

    /** A FreeCol placeholder's value (a name or an amount), or null. */
    private static String replacement(StringTemplate template, String key) {
        final StringTemplate r = template.getReplacement(key);
        final String s = (r == null) ? null : r.getId();
        return (s == null || s.isEmpty()) ? null : s;
    }
}
