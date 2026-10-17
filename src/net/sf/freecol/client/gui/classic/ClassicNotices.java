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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Europe;
import net.sf.freecol.common.model.FreeColObject;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.ModelMessage;
import net.sf.freecol.common.model.Ownable;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Settlement;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;


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
 *       with the tribe's chief on its side (Q11).  Before each of them but
 *       @CHIEFKILL the chief's greeting @CHIEFHOWDY with the village's
 *       skill and wanted goods, and after the guides the frontiersman's
 *       @WELLSEASONED (part O B3, {@link #requests}).</li>
 *   <li>A village that teaches no more (@LEARNALREADY, V text clip008
 *       08:50), one that sends the colonist away (@LEARNMAD) or kills him
 *       (@CHIEFKILL, I), with the chief; a colonist who learnt there
 *       @LEARNDONE (part O B2).</li>
 *   <li>A colonist the religious unrest brought to the docks: @UNREST with
 *       the priest (part O B4), also for FreeCol's survival recruit.</li>
 *   <li>The lost city rumours' results, with the frontiersman (landfall
 *       #20177: @BURIAL1, V): @LOSTCITY1-3, 5-7 and 9, @BURIAL1-3, and
 *       @SCREWED for a burial ground (with the tribe's chief, I).</li>
 *   <li>A unit that cannot found a colony: @ONLYCOL (I).</li>
 *   <li>The chief's answer to «Zoll fordern» under the levi tribute
 *       (part Z, Z2.2): @EXTORTSTUFF with our title, the tribe, the
 *       goods and the colony they go to, @EXTORTPOOR, @EXTORTLAUGH,
 *       @EXTORTNO, with the chief.</li>
 *   <li>A ship repaired: @REFIT with the admiral, «Handelsschiff:
 *       Reparaturen in Amsterdam abgeschlossen.» (fixer B after part X; V
 *       clip 049 #16294; the box's place not measured, and the original
 *       then opens Europe by itself, #16513, which is not built).</li>
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
        SOLDIER,
        /** The priest (MSS4), as at @UNREST (part O B4). */
        PRIEST
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
        MONEY,
        /** NAMES {@code @HOMEPORT}: our port ("Amsterdam"; part O B4). */
        HOME_PORT,
        /**
         * NAMES {@code @JOB} column 2 of the display unit's type ("Freie
         * Siedler", "Meister-Weber"; part O B4, {@link ClassicTips#unrestValues}).
         */
        UNIT_JOB2,
        /**
         * NAMES {@code @JOB} column 1 of the unit type FreeCol's
         * {@code %skill%} names ("Baumwollpflanzer"; part O B2).
         */
        SKILL_JOB1,
        /**
         * NAMES {@code @UNIT}: the type of the ship the message is from
         * ("Handelsschiff"; fixer B after part X, @REFIT).
         */
        SHIP,
        /**
         * Where a ship was repaired: our port ("Amsterdam") for Europe,
         * else the colony's name (fixer B after part X, @REFIT).
         */
        REPAIR_PLACE,
        /**
         * Our title: NAMES {@code @DIFFICULTY} column 1 at the game's level
         * («Entdecker»; part Z, @EXTORTSTUFF «Große Entdecker», V
         * war-french #17339, 048 #44727).
         */
        TITLE,
        /** Our leader's name (part Z, @EXTORTNO «%STRING0 %STRING1», I). */
        LEADER,
        /**
         * NAMES {@code @CARGO} of FreeCol's {@code %goods%} («Felle»; part
         * Z, @EXTORTSTUFF).
         */
        CARGO,
        /** FreeCol's {@code %colony%}, a colony's name (part Z, @EXTORTSTUFF). */
        COLONY
    }

    /** One notice in the original's words. */
    static final class Rule {

        /** The GAME.TXT section. */
        final String section;

        /** Who stands at it. */
        final Who who;

        /** The placeholders: GAME.TXT's name ({@code STRING0}) and value, in pairs. */
        final Object[] values;

        /**
         * The least time after the box before (ms), or a negative value
         * for {@link ClassicAdvisorLayer#CHAIN_MS}.
         */
        final double chainMs;

        Rule(String section, Who who, Object... values) {
            this(section, -1.0, who, values);
        }

        Rule(String section, double chainMs, Who who, Object... values) {
            this.section = section;
            this.who = who;
            this.values = values;
            this.chainMs = chainMs;
        }
    }

    /**
     * The original's pace between the chief's boxes, from the clips at 70
     * frames a second (DOSBox): 13 frames between the first contact's
     * boxes (playthrough-3 #219-#221: 0.185 s twice; playthrough-1 12-13).
     */
    static final double CONTACT_CHAIN_MS = 185.0;

    /**
     * 16-17 frames from the scout's menu to @CHIEFHOWDY and from it to
     * @CHIEFGIFT, @CHIEFGUIDES and @CHIEFBORED (playthrough-1 C30/C31,
     * playthrough-2 N10/N11, clip008 #45746-#47096).
     */
    static final double SPEAK_CHAIN_MS = 236.0;

    /** 42-43 frames from @CHIEFHOWDY to @CHIEFAREA (playthrough-2, P6). */
    static final double AREA_CHAIN_MS = 607.0;

    /** 49 frames from @CHIEFGUIDES to @WELLSEASONED (clip008 #47096). */
    static final double SEASONED_CHAIN_MS = 700.0;

    /** 17-18 frames from @LEARNSTAY's «no» to @LEARNLATER (playthrough-2 #27250). */
    static final double LATER_CHAIN_MS = 250.0;

    /**
     * 19 frames from @LEARNSTAY's «yes» to @LEARNDONE (playthrough-1
     * C27-C29); ours counts from the box before, the server's answer
     * included.
     */
    static final double DONE_CHAIN_MS = 271.0;

    /** The prefix of FreeCol's ordinary "nothing" rumours ({@code NameCache}). */
    private static final String NOTHING = "model.lostCityRumour.nothing.";

    /** FreeCol's result of a scout who became a seasoned scout ("expert"). */
    static final String EXPERT_SCOUT = "scoutSettlement.expertScout";

    /**
     * The controller's notice of a colonist who learnt at a village (part
     * O B2; FreeCol's controller had no success branch).
     */
    static final String LEARN_DONE = "learnSkill.done";

    /** The levi tribute's goods (part Z, Z2.2; @EXTORTSTUFF). */
    static final String TRIBUTE_GOODS = "scoutSettlement.tributeGoods";

    /** FreeCol's notice that another nation declared war on us (part Z, Z2.3). */
    static final String WAR_DECLARED = "model.player.stance.war.declared";

    /** FreeCol's notices in the original's words (class comment). */
    static final Map<String, Rule> RULES = new HashMap<>();
    static {
        RULES.put("scoutSettlement.speakBeads", new Rule("CHIEFGIFT",
            SPEAK_CHAIN_MS, Who.CHIEF,
            "STRING0", Value.TRIBE, "STRING1", Value.NATION_ABBREV,
            "NUMBER0", Value.AMOUNT));
        RULES.put(EXPERT_SCOUT, new Rule("CHIEFGUIDES", SPEAK_CHAIN_MS, Who.CHIEF,
            "STRING0", Value.TRIBE));
        RULES.put("scoutSettlement.speakTales", new Rule("CHIEFAREA", AREA_CHAIN_MS,
            Who.CHIEF, "STRING0", Value.TRIBE));
        RULES.put("scoutSettlement.speakNothing", new Rule("CHIEFBORED",
            SPEAK_CHAIN_MS, Who.CHIEF,
            "STRING0", Value.TRIBE, "STRING1", Value.NATION_ABBREV));
        RULES.put("scoutSettlement.speakDie", new Rule("CHIEFKILL", Who.CHIEF,
            "STRING0", Value.TRIBE));
        // Part Z, Z2.1: after the village box, 20 frames after its close.
        RULES.put("info.noMoreSkill", new Rule("LEARNALREADY",
            ClassicVillageBox.ALREADY_CHAIN_MS, Who.CHIEF, "STRING0", Value.TRIBE));
        RULES.put("learnSkill.leave", new Rule("LEARNMAD", Who.CHIEF));
        RULES.put("learnSkill.die", new Rule("CHIEFKILL", Who.CHIEF,
            "STRING0", Value.TRIBE));
        // Part O B2: the controller's new success notice.
        RULES.put(LEARN_DONE, new Rule("LEARNDONE", DONE_CHAIN_MS, Who.CHIEF,
            "STRING0", Value.TRIBE, "STRING1", Value.SKILL_JOB1));
        // Part O B4: the unrest's colonist, and FreeCol's survival recruit
        // (no such rule in the original, I: the same box).
        RULES.put(ClassicTips.EMIGRATE, new Rule("UNREST", Who.PRIEST,
            "COUNTRY", Value.COUNTRY, "STRING0", Value.HOME_PORT,
            "STRING1", Value.UNIT_JOB2));
        RULES.put(ClassicTips.AUTO_RECRUIT, new Rule("UNREST", Who.PRIEST,
            "COUNTRY", Value.COUNTRY, "STRING0", Value.HOME_PORT,
            "STRING1", Value.UNIT_JOB2));
        RULES.put("buildColony.badUnit", new Rule("ONLYCOL", Who.NONE));
        // Part Z, Z2.2: the chief's answers to «Zoll fordern» (the levi
        // rule's messages, server InGameController.originalTribute), 15-18
        // frames after the village box: @EXTORTSTUFF (V war-french #17339,
        // 048 #44727), @EXTORTPOOR (V 048 #48446), @EXTORTLAUGH (V 045
        // #30832), @EXTORTNO (never seen; its %STRING0 %STRING1 our title
        // and name, I).
        RULES.put(TRIBUTE_GOODS, new Rule("EXTORTSTUFF",
            ClassicVillageBox.TRIBUTE_CHAIN_MS, Who.CHIEF,
            "STRING0", Value.TITLE, "STRING1", Value.TRIBE, "NUMBER0", Value.AMOUNT,
            "STRING2", Value.CARGO, "STRING3", Value.COLONY));
        RULES.put("scoutSettlement.tributePoor", new Rule("EXTORTPOOR",
            ClassicVillageBox.TRIBUTE_CHAIN_MS, Who.CHIEF,
            "STRING0", Value.TITLE, "STRING1", Value.TRIBE));
        RULES.put("scoutSettlement.tributeLaugh", new Rule("EXTORTLAUGH",
            ClassicVillageBox.TRIBUTE_CHAIN_MS, Who.CHIEF, "STRING0", Value.TRIBE));
        // Part Z, Z2.3: a tribe's own declaration of war (its tension
        // turned hateful; under the levi rule an attack starts none) in
        // GAME.TXT's @INDIANWAR words with the chief (SRC: never seen;
        // the chief, I); a European's keeps FreeCol's words (no tribe).
        RULES.put(WAR_DECLARED, new Rule("INDIANWAR", Who.CHIEF,
            "STRING0", Value.TRIBE));
        RULES.put("scoutSettlement.tributeNo", new Rule("EXTORTNO",
            ClassicVillageBox.TRIBUTE_CHAIN_MS, Who.CHIEF,
            "STRING0", Value.TITLE, "STRING1", Value.LEADER, "STRING2", Value.TRIBE));
        // A ship repaired (fixer B after part X; V clip 049 #16294:
        // «Handelsschiff: Reparaturen in Amsterdam abgeschlossen.» with the
        // admiral MSS0; the box's place not measured, I).
        RULES.put("model.unit.unitRepaired", new Rule("REFIT", Who.ADMIRAL,
            "STRING0", Value.SHIP, "STRING1", Value.REPAIR_PLACE));
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

    /**
     * FreeCol's notices of a won fight to the winner, «Sieg bei …» (their
     * keys with or without the loser's type: {@code
     * combat.unitSlaughtered.enemy.brave}).  After our attack on natives
     * they never come (part Z, Z2.3, {@link #isOurWinOverNatives}).
     */
    static final List<String> WIN_NOTICES = Arrays.asList("combat.unitSlaughtered.enemy",
        "combat.unitDemoted.enemy", "combat.unitDemotedToUnarmed.enemy");

    /**
     * The results of speaking to a chief that @CHIEFHOWDY comes before
     * (part O B3, Roger's D-B3b): not before @CHIEFKILL (I: a "peaceful
     * village" greeting before the killing makes no sense; V for
     * @CHIEFGIFT, @CHIEFGUIDES, @CHIEFAREA, I for @CHIEFBORED).
     */
    static final List<String> SPEAK = Arrays.asList("scoutSettlement.speakBeads",
        EXPERT_SCOUT, "scoutSettlement.speakTales", "scoutSettlement.speakNothing");


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
     * Whether an attack goes against natives: the settlement on the
     * target, else its defender, belongs to a native player (part Z,
     * Z2.3).
     *
     * @param attacker The attacking unit, or null.
     * @param tile The target tile, or null.
     * @return True for an attack on natives.
     */
    static boolean attacksNatives(Unit attacker, Tile tile) {
        if (attacker == null || tile == null) return false;
        final Settlement s = tile.getSettlement();
        final Unit defender = (s != null) ? null : tile.getDefendingUnit(attacker);
        final Player p = (s != null) ? s.getOwner()
            : (defender != null) ? defender.getOwner() : null;
        return p != null && p.isIndian();
    }

    /**
     * Whether a notice is FreeCol's «Sieg bei …» of an attack of ours on
     * natives (part Z, Z2.3; Roger's test, events #14 and #17: «Sieg bei
     * Grasland … hat Krieger (Irokesen) abgeschlachtet!»): a win notice
     * ({@link #WIN_NOTICES}) whose source, the winner, is one of the
     * units that attacked natives.  The original shows no result box when
     * our attack on natives wins (V ×5: playthrough-4 048 #30249, #55062,
     * #61284, #66085).  A convert, a destroyed village and a tribe's end
     * keep their notices (B5).
     *
     * @param m The notice, or null.
     * @param attackers The ids of our units that attacked natives.
     * @return True if it is dropped.
     */
    static boolean isOurWinOverNatives(ModelMessage m, Set<String> attackers) {
        if (m == null || attackers == null || m.getId() == null
            || !attackers.contains(m.getSourceId())) return false;
        for (String w : WIN_NOTICES) {
            if (m.getId().equals(w) || m.getId().startsWith(w + ".")) return true;
        }
        return false;
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
     * The boxes of a notice, in their order (part O B3): the notice
     * ({@link #request}), and around a result of speaking to a chief in the
     * original's words the chief's greeting @CHIEFHOWDY before it
     * ({@link #SPEAK}, {@link #howdy}) and, after the guides, the
     * frontiersman's @WELLSEASONED ({@link #wellSeasoned}).  The original's
     * order (V: playthrough-1 C30/C31, playthrough-2 N10/N11, clip008
     * #45746-#47096): the menu, 16-17 frames, @CHIEFHOWDY, 16-17 frames
     * (@CHIEFAREA 42-43), the result, 49 frames after @CHIEFGUIDES
     * @WELLSEASONED.
     *
     * @param t The pack's texts, or null.
     * @param id What it is, for the recorder.
     * @param template The FreeCol message.
     * @param freeColText FreeCol's words for it.
     * @param display Its display object, or null.
     * @param me Our player, or null.
     * @param title The stopgap's window title.
     * @param icon The stopgap's illustration, or null.
     * @return The boxes, at least the notice's.
     */
    static List<ClassicAdvisorBox.Request> requests(ClassicText t, String id,
                                                    StringTemplate template,
                                                    String freeColText,
                                                    FreeColObject display,
                                                    Player me, String title,
                                                    Image icon) {
        final List<ClassicAdvisorBox.Request> out = new ArrayList<>(3);
        final String mid = (template == null) ? null : template.getId();
        final ClassicAdvisorBox.Builder b = original(t, template, display, me);
        if (b != null && SPEAK.contains(mid) && display instanceof IndianSettlement) {
            final ClassicAdvisorBox.Builder h = howdy(t, (IndianSettlement) display);
            if (h != null) {
                out.add(h.stopgap(title, icon).build());
            } else if (ClassicFrameRecorder.on()) {
                ClassicFrameRecorder.event("notice-howdy-skipped", mid
                    + " (a value unknown: skill or three wanted goods)");
            }
        }
        out.add(((b != null) ? b : ClassicAdvisorBox.Request.builder(id)
                 .freeColText(freeColText)).stopgap(title, icon).build());
        if (b != null && EXPERT_SCOUT.equals(mid)) {
            final ClassicAdvisorBox.Builder w = wellSeasoned(t);
            if (w != null) out.add(w.stopgap(title, icon).build());
        }
        return out;
    }

    /**
     * The chief's greeting before a result of speaking to him (part O B3):
     * GAME.TXT @CHIEFHOWDY with the tribe's chief, {@code %STRING0} the
     * village's skill, NAMES {@code @JOB} column 2 ("Erfahrene Pelzjäger",
     * "Kampferprobte Späher", "Erfahr. Silber-Bergarb."; V playthrough-1
     * #47458, playthrough-2 #15301, clip008 #45746), {@code %STRING1-3}
     * its three wanted goods, NAMES {@code @CARGO} (V: «Werkzeuge»,
     * «Handelswaren», «Rum»).  The skill is the one it teaches, else the
     * one it taught (Roger's D-B3c: the original names it also after it
     * was taught, V clip008 @LEARNALREADY at 08:50, then «Erfahrene
     * Pelzjäger» at #45746; {@link IndianSettlement#getTaughtSkill}).
     *
     * @param t The pack's texts, or null.
     * @param is The village.
     * @return The box, or null without the text or when a value is not
     *     known (no skill, as in a save from before part O, or fewer than
     *     three wanted goods: D-B3d).
     */
    static ClassicAdvisorBox.Builder howdy(ClassicText t, IndianSettlement is) {
        final ClassicText.Message m = (t == null) ? null : t.message("CHIEFHOWDY");
        final int tribe = (is == null) ? -1 : ClassicGUI.tribeIndex(is.getOwner());
        if (m == null || tribe < 0) return null;
        final Map<String, String> values = howdyValues(t, is);
        if (values == null) return null;
        final ClassicAdvisorBox.Builder b = ClassicAdvisorBox.fromGameText("CHIEFHOWDY",
            m, values);
        return (b == null) ? null
            : b.portrait(ClassicAdvisorBox.Portrait.chief(tribe)).chain(SPEAK_CHAIN_MS);
    }

    /**
     * @CHIEFHOWDY's values ({@link #howdy}).
     *
     * @param t The pack's texts.
     * @param is The village.
     * @return The values, or null when one is not known.
     */
    static Map<String, String> howdyValues(ClassicText t, IndianSettlement is) {
        UnitType skill = is.getLearnableSkill();
        if (skill == null) skill = is.getTaughtSkill();
        final String job = jobName(t, skill, 1);
        if (job == null) return null;
        final Map<String, String> values = new HashMap<>();
        values.put("STRING0", job);
        for (int i = 0; i < IndianSettlement.WANTED_GOODS_COUNT; i++) {
            final GoodsType g = is.getWantedGoods(i);
            final String name = (g == null) ? null
                : ClassicTips.cargoName(t, ClassicTips.cargoRow(g.getId()));
            if (name == null) return null;
            values.put("STRING" + (i + 1), name);
        }
        return values;
    }

    /**
     * The frontiersman's word after the guides (part O B3): GAME.TXT
     * @WELLSEASONED, box (42,132,236,24) with MSS3 at (86,45) (V clip008
     * #47096), 49 frames after @CHIEFGUIDES.
     *
     * @param t The pack's texts, or null.
     * @return The box, or null without the text.
     */
    static ClassicAdvisorBox.Builder wellSeasoned(ClassicText t) {
        final ClassicText.Message m = (t == null) ? null : t.message("WELLSEASONED");
        final ClassicAdvisorBox.Builder b = ClassicAdvisorBox.fromGameText("WELLSEASONED",
            m, null);
        return (b == null) ? null
            : b.portrait(ClassicAdvisorBox.Portrait.SCOUT).chain(SEASONED_CHAIN_MS);
    }

    /**
     * The chief's offer to teach (part O B2): GAME.TXT @LEARNSTAY with the
     * tribe's chief (V playthrough-1 C28, landfall #16263, playthrough-2
     * N13), {@code %STRING0} the tribe, {@code %STRING1} NAMES {@code @JOB}
     * column 1 of the skill («Zuckerpflanzer», «Pelzjäger»; the template
     * adds «Meister-»), its two rows from the file, the bar on row 1 (no
     * {@code @default}), Escape on row 2 «Im Moment nicht, danke.»; 19
     * frames after the village box's close (part Z, Z2.1, V playthrough-1
     * #9008 -&gt; #9027, {@link ClassicVillageBox#STAY_CHAIN_MS}).
     *
     * @param t The pack's texts, or null.
     * @param is The village.
     * @param skill What it teaches.
     * @return The box, or null without the text or a value.
     */
    static ClassicAdvisorBox.Builder learnStay(ClassicText t, IndianSettlement is,
                                               UnitType skill) {
        final ClassicText.Message m = (t == null) ? null : t.message("LEARNSTAY");
        final int tribe = (is == null) ? -1 : ClassicGUI.tribeIndex(is.getOwner());
        final String name = (tribe < 0 || m == null) ? null
            : column(t.names("TRIBES"), tribe);
        final String job = jobName(t, skill, 0);
        if (name == null || job == null || m.options.size() != 2) return null;
        final Map<String, String> values = new HashMap<>();
        values.put("STRING0", name);
        values.put("STRING1", job);
        final ClassicAdvisorBox.Builder b = ClassicAdvisorBox.fromGameText("LEARNSTAY",
            m, values);
        return (b == null) ? null : b.portrait(ClassicAdvisorBox.Portrait.chief(tribe))
            .defaultRow(0).cancelRow(1).chain(ClassicVillageBox.STAY_CHAIN_MS);
    }

    /**
     * The chief after a «no» to his offer (part O B2): GAME.TXT
     * @LEARNLATER, one line, with the chief (V playthrough-2 #27250, box
     * (7,88,236,24) for the Arawak), 17-18 frames after @LEARNSTAY.
     *
     * @param t The pack's texts, or null.
     * @param tribe The tribe's {@link ClassicGUI#TRIBES} index.
     * @return The box, or null without the text or the tribe.
     */
    static ClassicAdvisorBox.Builder learnLater(ClassicText t, int tribe) {
        final ClassicText.Message m = (t == null || tribe < 0) ? null
            : t.message("LEARNLATER");
        final ClassicAdvisorBox.Builder b = ClassicAdvisorBox.fromGameText("LEARNLATER",
            m, null);
        return (b == null) ? null : b.portrait(ClassicAdvisorBox.Portrait.chief(tribe))
            .chain(LATER_CHAIN_MS);
    }

    /**
     * The first contact's question (part O B1): GAME.TXT @INDIANWELCOME
     * with the tribe's chief, {@code %STRING0} the tribe, {@code %NUMBER0}
     * its settlements, {@code %STRING1} the plural of its kind
     * ({@link #levelPlural}; the template adds the «n»: «7 Städten», «18
     * Dörfern», «13 Lagern», V in every clip), the rows «Ja» / «Nein»
     * from the file, the bar on «Ja» (no {@code @default}).  Escape and
     * the cancel row are the caller's.
     *
     * @param t The pack's texts, or null.
     * @param tribe The tribe's {@link ClassicGUI#TRIBES} index.
     * @param settlements Its settlement count.
     * @return The box, or null without the text or a value.
     */
    static ClassicAdvisorBox.Builder welcome(ClassicText t, int tribe, int settlements) {
        final ClassicText.Message m = (t == null || tribe < 0) ? null
            : t.message("INDIANWELCOME");
        final String name = (m == null) ? null : column(t.names("TRIBES"), tribe);
        final String kind = (m == null) ? null : levelPlural(t, tribe);
        if (name == null || kind == null || settlements < 0
            || m.options.size() != 2) return null;
        final Map<String, String> values = new HashMap<>();
        values.put("STRING0", name);
        values.put("NUMBER0", Integer.toString(settlements));
        values.put("STRING1", kind);
        final ClassicAdvisorBox.Builder b = ClassicAdvisorBox.fromGameText(
            "INDIANWELCOME", m, values);
        return (b == null) ? null
            : b.portrait(ClassicAdvisorBox.Portrait.chief(tribe)).defaultRow(0);
    }

    /**
     * A notice of the first contact's chain (part O B1), 13 frames after
     * the box before, with the tribe's chief: @INDIANPEACE ({@code
     * %STRING0} the tribe, {@code %STRING1} our @NATIONABBREV «Holl.»),
     * @INDIANCOME and @INDIANSHUN ({@code %STRING0} the tribe).
     *
     * @param t The pack's texts, or null.
     * @param section The section.
     * @param tribe The tribe's {@link ClassicGUI#TRIBES} index.
     * @param me Our player (for the nation), or null.
     * @return The box, or null without the text or a value.
     */
    static ClassicAdvisorBox.Builder contactNotice(ClassicText t, String section,
                                                   int tribe, Player me) {
        final ClassicText.Message m = (t == null || tribe < 0) ? null
            : t.message(section);
        final String name = (m == null) ? null : column(t.names("TRIBES"), tribe);
        if (name == null) return null;
        final Map<String, String> values = new HashMap<>();
        values.put("STRING0", name);
        if ("INDIANPEACE".equals(section)) {
            final String abbrev = column(t.names("NATIONABBREV"), nationIndex(me));
            if (abbrev == null) return null;
            values.put("STRING1", abbrev);
        }
        final ClassicAdvisorBox.Builder b = ClassicAdvisorBox.fromGameText(section, m,
            values);
        return (b == null) ? null : b.portrait(ClassicAdvisorBox.Portrait.chief(tribe))
            .chain(CONTACT_CHAIN_MS);
    }

    /**
     * The plural of a tribe's kind of settlement (part O B1): NAMES
     * {@code @LEVELS} column 3 at the tribe's tech level ({@code @TRIBES}
     * column 4): Inka and Azteken «Städte», Araukaner, Irokesen and
     * Cherokee «Dörfer», Apachen, Sioux and Tupi «Lager».
     *
     * @param t The pack's texts.
     * @param tribe The tribe's {@link ClassicGUI#TRIBES} index.
     * @return The plural, or null.
     */
    static String levelPlural(ClassicText t, int tribe) {
        final List<String[]> tribes = (t == null) ? null : t.names("TRIBES");
        if (tribes == null || tribe < 0 || tribe >= tribes.size()
            || tribes.get(tribe).length < 4) return null;
        final int level;
        try {
            level = Integer.parseInt(tribes.get(tribe)[3]);
        } catch (NumberFormatException e) {
            return null;
        }
        return column(t.names("LEVELS"), level, 2);
    }

    /**
     * A unit type's NAMES {@code @JOB} name.
     *
     * @param t The pack's texts, or null.
     * @param type The type, or null.
     * @param col 0 for the job («Pelzjäger»), 1 for the expert
     *     («Erfahrene Pelzjäger»).
     * @return The name, or null.
     */
    static String jobName(ClassicText t, UnitType type, int col) {
        return (type == null) ? null : jobName(t, type.getSuffix(), col);
    }

    /** A unit type's NAMES {@code @JOB} name by its id's suffix, or null. */
    private static String jobName(ClassicText t, String suffix, int col) {
        if (t == null || suffix == null) return null;
        return column(t.names("JOB"), ClassicHud.jobRow(suffix), col);
    }

    /** Our nation's index in {@link ClassicNewWorldScreens#NATION_IDS}, or -1. */
    private static int nationIndex(Player me) {
        return (me == null) ? -1 : Arrays.asList(
            ClassicNewWorldScreens.NATION_IDS).indexOf(me.getNationId());
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
        final int nation = nationIndex(me);
        final Map<String, String> values = new HashMap<>();
        for (int i = 0; i + 1 < r.values.length; i += 2) {
            final String v = value(t, (Value) r.values[i + 1], template, display,
                                   me, tribe, nation);
            if (v == null) return null;
            values.put((String) r.values[i], v);
        }
        if (r.who == Who.CHIEF && tribe < 0) return null;
        final ClassicAdvisorBox.Portrait p = portrait(r.who, tribe);
        final ClassicAdvisorBox.Builder b = ClassicAdvisorBox.fromGameText(r.section,
            m, values);
        if (b != null && r.chainMs >= 0.0) b.chain(r.chainMs);
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
        case PRIEST:
            return ClassicAdvisorBox.Portrait.PRIEST;
        default:
            return ClassicAdvisorBox.Portrait.NONE;
        }
    }

    /** The prefix and suffix of a unit type's name key ({@code %skill%}). */
    private static final String UNIT_PREFIX = "model.unit.", NAME_SUFFIX = ".name";

    /** A placeholder's value, or null when it is not known. */
    private static String value(ClassicText t, Value v, StringTemplate template,
                                FreeColObject display, Player me, int tribe,
                                int nation) {
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
        case HOME_PORT:
            return ClassicTips.homePort(t, me);
        case SHIP: {
            final String id = (template instanceof ModelMessage)
                ? ((ModelMessage) template).getSourceId() : null;
            final Unit ship = (id == null || me == null || me.getGame() == null) ? null
                : me.getGame().getFreeColGameObject(id, Unit.class);
            return (ship == null || !ship.isNaval()) ? null : ClassicBands.shipName(t, ship);
        }
        case REPAIR_PLACE:
            return (display instanceof Europe) ? ClassicTips.homePort(t, me)
                : (display instanceof Colony) ? ((Colony) display).getName()
                : null;
        case UNIT_JOB2:
            return (display instanceof Unit && me != null)
                ? ClassicTips.unrestValues(t, me, (Unit) display).get("STRING1")
                : null;
        case SKILL_JOB1: {
            // FreeCol's %skill% is the type's name key, "model.unit.X.name".
            final String key = replacement(template, "%skill%");
            if (key == null || !key.startsWith(UNIT_PREFIX)
                || !key.endsWith(NAME_SUFFIX)) return null;
            return jobName(t, key.substring(UNIT_PREFIX.length(),
                key.length() - NAME_SUFFIX.length()), 0);
        }
        case TITLE:
            return title(t, me);
        case LEADER:
            return (me == null || me.getName() == null || me.getName().isEmpty())
                ? null : me.getName();
        case CARGO: {
            // FreeCol's %goods% is the type's name key, "model.goods.X.name".
            final String key = replacement(template, "%goods%");
            if (key == null || !key.endsWith(NAME_SUFFIX)) return null;
            final int row = ClassicTips.cargoRow(key.substring(0,
                key.length() - NAME_SUFFIX.length()));
            return ClassicTips.cargoName(t, row);
        }
        case COLONY:
            return replacement(template, "%colony%");
        default:
            return null;
        }
    }

    /**
     * Our title: NAMES {@code @DIFFICULTY} column 1 at the game's level
     * («Entdecker» on the easiest).
     *
     * @param t The pack's texts, or null.
     * @param me Our player, or null.
     * @return The title, or null when the level is not known.
     */
    static String title(ClassicText t, Player me) {
        final String level = (me == null || me.getSpecification() == null) ? null
            : me.getSpecification().getDifficultyLevel();
        final int i = (level == null) ? -1
            : Arrays.asList(ClassicNewWorldScreens.DIFFICULTY_IDS).indexOf(level);
        return (t == null) ? null : column(t.names("DIFFICULTY"), i);
    }

    /** A NAMES.TXT row's first column, or null. */
    private static String column(List<String[]> rows, int i) {
        return column(rows, i, 0);
    }

    /** A NAMES.TXT row's column, or null (also when empty). */
    private static String column(List<String[]> rows, int i, int col) {
        if (rows == null || i < 0 || i >= rows.size() || col < 0
            || rows.get(i).length <= col) return null;
        final String s = rows.get(i)[col];
        return (s == null || s.isEmpty()) ? null : s;
    }

    /** A FreeCol placeholder's value (a name or an amount), or null. */
    private static String replacement(StringTemplate template, String key) {
        final StringTemplate r = template.getReplacement(key);
        final String s = (r == null) ? null : r.getId();
        return (s == null || s.isEmpty()) ? null : s;
    }
}
