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

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.DiplomaticTrade;
import net.sf.freecol.common.model.Direction;
import net.sf.freecol.common.model.FreeColGameObject;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoldTradeItem;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.ModelMessage;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * Tests of the notices (master plan N1, {@link ClassicNotices}): FreeCol's
 * information messages, silent in the base GUI, come as one box each, in
 * GAME.TXT's words where the original has the notice, with its portrait;
 * the illegal moves and a key out of turn stay silent; a value the
 * original does not have gives FreeCol's words.  And the golden check of
 * the boxes the game builds from FreeCol's messages: @BURIAL1 (landfall
 * #20177, from the mounds' "nothing" rumour), @CHIEFGUIDES (clip008
 * #46396), @LEARNALREADY (#37156) and @CHIEFGIFT (#51431), 0 px off on the
 * box and the portrait's pixels (needs the pack and {@code -Dclassic.clips},
 * else skipped with a note).  And FreeCol's first-contact notices that the
 * original does not have (part K3): the meeting with a village's people,
 * and the "accepted" / "rejected" that echoes our own answer.
 */
public class ClassicNoticesTest extends FreeColTestCase {

    /** A classic GUI without a client, our player the test's. */
    private static final class NoticeGUI extends ClassicGUI {

        final Game game;
        final Player me;

        NoticeGUI(Game game, Player me) {
            super(null);
            this.game = game;
            this.me = me;
        }

        @Override
        protected Game getGame() {
            return this.game;
        }

        @Override
        Player myPlayer() {
            return this.me;
        }

        @Override
        java.awt.Image iconOf(net.sf.freecol.common.model.FreeColObject display) {
            return null;   // no image resources here
        }
    }

    /** Keeps the boxes asked; answers {@link #answer} (0 unless set). */
    private static final class Keeper implements ClassicGUI.Prompter {

        final List<ClassicAdvisorBox.Request> boxes = new ArrayList<>();

        int answer = 0;

        @Override
        public int ask(ClassicAdvisorBox.Request r) {
            this.boxes.add(r);
            return this.answer;
        }
    }

    /** The pack's texts, or null (then a test is skipped with a note). */
    private static ClassicText texts(String test) {
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        if (t == null) {
            System.err.println("ClassicNoticesTest: " + test
                + " skipped, no pack texts (ant classic-assets)");
        }
        return t;
    }

    /** An Arawak village on a test map, and the game. */
    private IndianSettlement arawakVillage(Game game) {
        game.changeMap(getTestMap());
        return new IndianSettlementBuilder(game)
            .player(game.getPlayerByNationId("model.nation.arawak")).build();
    }

    /** The silent ones, and only those. */
    public void testSilent() throws Exception {
        for (String id : new String[] { "move.noAccessWater", "move.noAccessBeached",
                "move.noAccessSettlement", "move.noAttackWater", "move.noTile",
                "info.notYourTurn" }) {
            assertTrue(id, ClassicNotices.silent(id));
        }
        for (String id : new String[] { "info.notEnoughGold", "buildColony.badUnit",
                "scoutSettlement.speakBeads", "move.other", null }) {
            assertFalse(String.valueOf(id), ClassicNotices.silent(id));
        }
        // The GUI asks no box for them, one box for any other.
        final Game game = getStandardGame();
        final NoticeGUI gui = new NoticeGUI(game, game.getPlayerByNationId("model.nation.dutch"));
        final Keeper keep = new Keeper();
        gui.prompter = keep;
        assertNull(gui.showInformationPanel(null, StringTemplate.template("move.noAccessWater")
            .addName("%unit%", "x")));
        assertNull(gui.showInformationPanel(null, StringTemplate.key("info.notYourTurn")));
        assertTrue(keep.boxes.isEmpty());
        assertNull(gui.showInformationPanel(null, StringTemplate.key("info.notEnoughGold")));
        assertNull(gui.showInformationPanel(null, (StringTemplate) null));
        SwingUtilities.invokeAndWait(() -> { });       // the posted box
        assertEquals(1, keep.boxes.size());
        final ClassicAdvisorBox.Request r = keep.boxes.get(0);
        assertTrue(r.isNotice());
        assertEquals("notice info.notEnoughGold", r.id);
        assertEquals(Messages.message("info.notEnoughGold"), r.plainText());
    }

    /** The rules: the mapped ids, the ordinary "nothing" rumours, the others none. */
    public void testRules() {
        assertEquals("CHIEFGIFT", ClassicNotices.rule("scoutSettlement.speakBeads").section);
        assertEquals(ClassicNotices.Who.CHIEF,
                     ClassicNotices.rule("info.noMoreSkill").who);
        assertEquals("BURIAL1", ClassicNotices.rule(
            "model.lostCityRumour.nothing.mounds.description").section);
        assertEquals(ClassicNotices.Who.SCOUT, ClassicNotices.rule(
            "model.lostCityRumour.nothing.mounds.description").who);
        assertEquals("LOSTCITY6", ClassicNotices.rule(
            "model.lostCityRumour.nothing.3.description").section);
        assertNull(ClassicNotices.rule("model.lostCityRumour.nothing.mayans.description"));
        assertNull(ClassicNotices.rule("model.lostCityRumour.nothing..description"));
        assertNull(ClassicNotices.rule("info.notEnoughGold"));
        assertNull(ClassicNotices.rule(null));
        // Part O: the unrest's colonist and FreeCol's survival recruit with
        // the priest (B4), the success at a village (B2).
        for (String id : new String[] { "model.player.emigrate",
                                        "model.player.autoRecruit" }) {
            assertEquals(id, "UNREST", ClassicNotices.rule(id).section);
            assertEquals(id, ClassicNotices.Who.PRIEST, ClassicNotices.rule(id).who);
        }
        assertEquals("LEARNDONE", ClassicNotices.rule("learnSkill.done").section);
        assertEquals(ClassicNotices.Who.CHIEF, ClassicNotices.rule("learnSkill.done").who);
        assertEquals(ClassicNotices.DONE_CHAIN_MS,
                     ClassicNotices.rule("learnSkill.done").chainMs);
        assertSame(ClassicAdvisorBox.Portrait.PRIEST,
                   ClassicNotices.portrait(ClassicNotices.Who.PRIEST, -1));
        // The chief's answers' pace (B3): 16-17 frames, the area 42-43.
        assertEquals(ClassicNotices.SPEAK_CHAIN_MS,
                     ClassicNotices.rule("scoutSettlement.speakBeads").chainMs);
        assertEquals(ClassicNotices.SPEAK_CHAIN_MS,
                     ClassicNotices.rule("scoutSettlement.expertScout").chainMs);
        assertEquals(ClassicNotices.SPEAK_CHAIN_MS,
                     ClassicNotices.rule("scoutSettlement.speakNothing").chainMs);
        assertEquals(ClassicNotices.AREA_CHAIN_MS,
                     ClassicNotices.rule("scoutSettlement.speakTales").chainMs);
        // Every rule's section is in GAME.TXT (with the pack).
        final ClassicText t = texts("testRules (sections)");
        if (t == null) return;
        for (ClassicNotices.Rule r : ClassicNotices.RULES.values()) {
            assertNotNull(r.section, t.message(r.section));
            assertNotNull(r.section, t.message(r.section).width);
            assertTrue(r.section, t.message(r.section).options.isEmpty());
        }
        assertNotNull(t.message("LOSTCITY6"));
    }

    /** The tribe: the village's owner, else the message's %nation%. */
    public void testTribe() {
        final Game game = getStandardGame();
        final IndianSettlement is = arawakVillage(game);
        assertEquals(2, ClassicNotices.tribe(StringTemplate.key("x"), is));
        final Player sioux = game.getPlayerByNationId("model.nation.sioux");
        assertEquals(6, ClassicNotices.tribe(StringTemplate.template("x")
            .addStringTemplate("%nation%", sioux.getNationLabel()), null));
        // Our own unit is no tribe: the %nation% decides.
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        assertEquals(-1, ClassicNotices.tribe(StringTemplate.key("x"), dutch));
        assertEquals(-1, ClassicNotices.tribe(null, null));
        assertEquals(-1, ClassicNotices.tribe(StringTemplate.template("x")
            .addStringTemplate("%nation%", StringTemplate.name("Somewhere")), null));
    }

    /**
     * The boxes with the pack: the chief's gift in the original's words
     * with the tribe, "Holl." and the coin; the chief stands at it; a
     * notice without a known tribe, or of a nation the original does not
     * have, keeps FreeCol's words; @ONLYCOL without a portrait.
     */
    public void testTheOriginalsWords() {
        final ClassicText t = texts("testTheOriginalsWords");
        if (t == null) return;
        final Game game = getStandardGame();
        final IndianSettlement is = arawakVillage(game);
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final StringTemplate beads = StringTemplate.template("scoutSettlement.speakBeads")
            .add("%amount%", "640");
        final ClassicAdvisorBox.Request r = ClassicNotices.request(t, "notice",
            beads, Messages.message(beads), is, dutch, "t", null);
        assertEquals("CHIEFGIFT", r.id);
        assertEquals("IND2A0.SS.000", r.portrait.sprite);
        assertTrue(r.isNotice());
        final String text = r.plainText();
        assertTrue(text, text.contains("Araukaner"));
        assertTrue(text, text.contains("Holl."));
        assertTrue(text, text.contains("640$"));
        // Without the village: FreeCol's words, no portrait.
        final ClassicAdvisorBox.Request f = ClassicNotices.request(t, "notice x",
            beads, Messages.message(beads), null, dutch, "t", null);
        assertEquals("notice x", f.id);
        assertEquals(ClassicAdvisorBox.Portrait.NONE, f.portrait);
        assertEquals(Messages.message(beads), f.plainText());
        // A nation the original does not have: FreeCol's words.
        final Player danes = game.getPlayerByNationId("model.nation.danish");
        if (danes != null) {
            assertEquals("notice y", ClassicNotices.request(t, "notice y", beads,
                "w", is, danes, "t", null).id);
        }
        // Without the texts: FreeCol's words.
        assertEquals("notice z", ClassicNotices.request(null, "notice z", beads,
            "w", is, dutch, "t", null).id);
        // @ONLYCOL.
        final ClassicAdvisorBox.Request c = ClassicNotices.request(t, "notice",
            StringTemplate.template("buildColony.badUnit").addName("%unit%", "x"),
            "w", null, dutch, "t", null);
        assertEquals("ONLYCOL", c.id);
        assertEquals(120, c.width);
        assertEquals(ClassicAdvisorBox.Portrait.NONE, c.portrait);
        // A rumour's result with the frontiersman and the money.
        final ModelMessage ruins = new ModelMessage(
            ModelMessage.MessageType.LOST_CITY_RUMOUR,
            "model.lostCityRumour.ruins.description", dutch).addAmount("%money%", 300);
        final ClassicAdvisorBox.Request ru = ClassicNotices.request(t, "message",
            ruins, Messages.message(ruins), null, dutch, "t", null);
        assertEquals("LOSTCITY3", ru.id);
        assertEquals(ClassicAdvisorBox.Portrait.SCOUT, ru.portrait);
        assertTrue(ru.plainText(), ru.plainText().contains("300$"));
    }

    /**
     * The GUI's notices: from the event thread the box is asked at once,
     * from another thread it is posted (no server message waits); the
     * model messages go the same way, in the original's words where it
     * has them.
     */
    public void testTheGuiAsksThem() throws Exception {
        final Game game = getStandardGame();
        final IndianSettlement is = arawakVillage(game);
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final NoticeGUI gui = new NoticeGUI(game, dutch);
        final Keeper keep = new Keeper();
        gui.prompter = keep;
        gui.showInformationPanel(is, StringTemplate.template("scoutSettlement.speakTales"));
        assertEquals(0, keep.boxes.size());                // posted
        SwingUtilities.invokeAndWait(() -> { });
        assertEquals(1, keep.boxes.size());
        SwingUtilities.invokeAndWait(() ->
            gui.showInformationPanel(is, StringTemplate.key("info.noMoreSkill")));
        assertEquals(2, keep.boxes.size());
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        if (t != null) {
            assertEquals("CHIEFAREA", keep.boxes.get(0).id);
            assertEquals("LEARNALREADY", keep.boxes.get(1).id);
        }
        gui.showModelMessages(List.of(new ModelMessage(
            ModelMessage.MessageType.LOST_CITY_RUMOUR,
            "model.lostCityRumour.nothing.mounds.description", dutch)));
        assertEquals(3, keep.boxes.size());
        if (t != null) assertEquals("BURIAL1", keep.boxes.get(2).id);
    }


    // Part O, bundle B: the chief's greeting, the unrest, the learning

    /** An Arawak village teaching {@code skill}, wanting tools, trade goods and rum. */
    private IndianSettlement howdyVillage(Game game, String skill) {
        final IndianSettlement is = arawakVillage(game);
        is.setLearnableSkill((skill == null) ? null : spec().getUnitType(skill));
        is.setWantedGoods(0, spec().getGoodsType("model.goods.tools"));
        is.setWantedGoods(1, spec().getGoodsType("model.goods.tradeGoods"));
        is.setWantedGoods(2, spec().getGoodsType("model.goods.rum"));
        return is;
    }

    /** The ids of boxes, in order. */
    private static List<String> ids(List<ClassicAdvisorBox.Request> boxes) {
        final List<String> out = new ArrayList<>();
        for (ClassicAdvisorBox.Request r : boxes) out.add(r.id);
        return out;
    }

    /**
     * Part O B3 (Roger's D-B3b): before every result of speaking to a
     * chief but his killing, his greeting @CHIEFHOWDY with the village's
     * skill (NAMES @JOB column 2, «Erfahrene Pelzjäger») and its three
     * wanted goods (@CARGO: «Werkzeuge», «Handelswaren», «Rum»; V
     * playthrough-2 #15301), the tribe's chief, 236 ms (16-17 frames)
     * after the menu; the result 236 ms after it (@CHIEFAREA 607 ms);
     * after @CHIEFGUIDES the frontiersman's @WELLSEASONED 700 ms (49
     * frames) later.  Posted from another thread, they come in one go,
     * in order.  Without the pack: FreeCol's words alone.
     */
    public void testChiefHowdyChain() throws Exception {
        final Game game = getStandardGame();
        final IndianSettlement is = howdyVillage(game, "model.unit.expertFurTrapper");
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final NoticeGUI gui = new NoticeGUI(game, dutch);
        final Keeper keep = new Keeper();
        gui.prompter = keep;
        final ClassicText t = texts("testChiefHowdyChain");
        final StringTemplate beads = StringTemplate.template("scoutSettlement.speakBeads")
            .add("%amount%", "640");
        final StringTemplate guides = StringTemplate.template("scoutSettlement.expertScout")
            .addNamed("%unit%", spec().getUnitType("model.unit.seasonedScout"));
        final StringTemplate tales = StringTemplate.template("scoutSettlement.speakTales");
        final StringTemplate bored = StringTemplate.template("scoutSettlement.speakNothing")
            .addStringTemplate("%nation%", dutch.getNationLabel());
        final StringTemplate die = StringTemplate.template("scoutSettlement.speakDie");
        final Object[][] cases = {
            { beads, List.of("CHIEFHOWDY", "CHIEFGIFT") },
            { guides, List.of("CHIEFHOWDY", "CHIEFGUIDES", "WELLSEASONED") },
            { tales, List.of("CHIEFHOWDY", "CHIEFAREA") },
            { bored, List.of("CHIEFHOWDY", "CHIEFBORED") },
            { die, List.of("CHIEFKILL") },
        };
        for (Object[] c : cases) {
            final StringTemplate st = (StringTemplate) c[0];
            keep.boxes.clear();
            gui.showInformationPanel(is, st);               // posted
            SwingUtilities.invokeAndWait(() -> { });
            if (t == null) {
                assertEquals(st.getId(), 1, keep.boxes.size());
                continue;
            }
            assertEquals(st.getId(), c[1], ids(keep.boxes));
        }
        if (t == null) return;
        // The greeting's words, chief and pace.
        keep.boxes.clear();
        SwingUtilities.invokeAndWait(() -> gui.showInformationPanel(is, tales));
        final ClassicAdvisorBox.Request howdy = keep.boxes.get(0);
        assertEquals("IND2A0.SS.000", howdy.portrait.sprite);
        assertTrue(howdy.isNotice());
        for (String s : new String[] { "Erfahrene Pelzjäger", "Werkzeuge",
                                       "Handelswaren", "Rum" }) {
            assertTrue(s + " in " + howdy.plainText(), howdy.plainText().contains(s));
        }
        assertEquals(ClassicNotices.SPEAK_CHAIN_MS, howdy.chainMs);
        assertEquals(ClassicNotices.AREA_CHAIN_MS, keep.boxes.get(1).chainMs);
        keep.boxes.clear();
        SwingUtilities.invokeAndWait(() -> gui.showInformationPanel(is, guides));
        assertEquals(ClassicNotices.SPEAK_CHAIN_MS, keep.boxes.get(1).chainMs);
        final ClassicAdvisorBox.Request seasoned = keep.boxes.get(2);
        assertSame(ClassicAdvisorBox.Portrait.SCOUT, seasoned.portrait);
        assertTrue(seasoned.isNotice());
        assertEquals(ClassicNotices.SEASONED_CHAIN_MS, seasoned.chainMs);
        assertTrue(seasoned.plainText(), seasoned.plainText().contains("Kampferprobten"));
        // D-B3c: a taught skill is still named.
        is.setLearnableSkill(null);
        is.setTaughtSkill(spec().getUnitType("model.unit.masterCottonPlanter"));
        keep.boxes.clear();
        SwingUtilities.invokeAndWait(() -> gui.showInformationPanel(is, beads));
        assertEquals(List.of("CHIEFHOWDY", "CHIEFGIFT"), ids(keep.boxes));
        assertTrue(keep.boxes.get(0).plainText(),
                   keep.boxes.get(0).plainText().contains("Meister-Baumwollpflanzer"));
        // No skill at all (a save from before part O): the result alone.
        is.setTaughtSkill(null);
        keep.boxes.clear();
        SwingUtilities.invokeAndWait(() -> gui.showInformationPanel(is, beads));
        assertEquals(List.of("CHIEFGIFT"), ids(keep.boxes));
        // Two wanted goods only (D-B3d): the result alone.
        is.setLearnableSkill(spec().getUnitType("model.unit.seasonedScout"));
        is.setWantedGoods(2, null);
        keep.boxes.clear();
        SwingUtilities.invokeAndWait(() -> gui.showInformationPanel(is, beads));
        assertEquals(List.of("CHIEFGIFT"), ids(keep.boxes));
        is.setWantedGoods(2, spec().getGoodsType("model.goods.horses"));
        keep.boxes.clear();
        SwingUtilities.invokeAndWait(() -> gui.showInformationPanel(is, beads));
        assertEquals(List.of("CHIEFHOWDY", "CHIEFGIFT"), ids(keep.boxes));
        assertTrue(keep.boxes.get(0).plainText().contains("Kampferprobte Späher"));
        assertTrue(keep.boxes.get(0).plainText().contains("Pferde"));
    }

    /**
     * Part O B4: the colonist the religious unrest brought is the
     * original's @UNREST with the priest (V playthrough-1 #15383, landfall
     * #18247, playthrough-3 #99674): «Holland», «Amsterdam» and the
     * colonist's @JOB column 2 («Erfahrene Farmer», «Freie Siedler»); the
     * same for FreeCol's survival recruit (I); without the colonist (a
     * notice about the player) FreeCol's words.
     */
    public void testUnrestNotice() {
        final ClassicText t = texts("testUnrestNotice");
        if (t == null) return;
        final Game game = getStandardGame();
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final Unit farmer = new ServerUnit(game, dutch.getEurope(), dutch,
            spec().getUnitType("model.unit.expertFarmer"));
        final Unit free = new ServerUnit(game, dutch.getEurope(), dutch,
            spec().getUnitType("model.unit.freeColonist"));
        final ModelMessage m = dutch.getEmigrationMessage(farmer);
        final ClassicAdvisorBox.Request r = ClassicNotices.request(t, "message",
            m, Messages.message(m), farmer, dutch, "t", null);
        assertEquals("UNREST", r.id);
        assertSame(ClassicAdvisorBox.Portrait.PRIEST, r.portrait);
        assertTrue(r.isNotice());
        for (String s : new String[] { "Holland", "Amsterdam", "Erfahrene Farmer" }) {
            assertTrue(s + " in " + r.plainText(), r.plainText().contains(s));
        }
        final ModelMessage auto = new ModelMessage(ModelMessage.MessageType.UNIT_ADDED,
            "model.player.autoRecruit", dutch, free)
            .addNamed("%europe%", dutch.getEurope())
            .addStringTemplate("%unit%", free.getLabel());
        final ClassicAdvisorBox.Request a = ClassicNotices.request(t, "message",
            auto, Messages.message(auto), free, dutch, "t", null);
        assertEquals("UNREST", a.id);
        assertTrue(a.plainText(), a.plainText().contains("Freie Siedler"));
        // Without the colonist: FreeCol's words.
        assertEquals("message", ClassicNotices.request(t, "message", m,
            Messages.message(m), dutch, dutch, "t", null).id);
    }

    /**
     * Part O B2: the controller's new success notice is the original's
     * @LEARNDONE with the tribe and the job («Araukaner», «Meister-
     * Baumwollpflanzer»; V playthrough-1 C29), the chief, 271 ms (19
     * frames) after the box before.
     */
    public void testLearnDoneNotice() {
        final ClassicText t = texts("testLearnDoneNotice");
        final Game game = getStandardGame();
        final IndianSettlement is = arawakVillage(game);
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final StringTemplate done = StringTemplate.template("learnSkill.done")
            .addNamed("%skill%", spec().getUnitType("model.unit.masterCottonPlanter"));
        assertTrue(Messages.containsKey("learnSkill.done"));
        final ClassicAdvisorBox.Request r = ClassicNotices.request(t, "notice",
            done, Messages.message(done), is, dutch, "t", null);
        if (t == null) {
            assertEquals("notice", r.id);
            return;
        }
        assertEquals("LEARNDONE", r.id);
        assertEquals("IND2A0.SS.000", r.portrait.sprite);
        assertTrue(r.plainText(), r.plainText().contains("Araukaner"));
        assertTrue(r.plainText(), r.plainText().contains("Meister-Baumwollpflanzer"));
        assertEquals(ClassicNotices.DONE_CHAIN_MS, r.chainMs);
    }


    // K3: FreeCol's notices of a first contact that the original does not have

    /** FreeCol's meeting notice as the server builds it ({@code ServerUnit.csNewContactCheck}). */
    private static ModelMessage meeting(FreeColGameObject source, IndianSettlement is) {
        final ModelMessage m = new ModelMessage(ModelMessage.MessageType.FOREIGN_DIPLOMACY,
            ClassicNotices.SCOUT_MEETING, source, is);
        m.addStringTemplate("%nation%", is.getOwner().getNationLabel());
        m.addName("%settlement%", is.getName());
        return m;
    }

    /** FreeCol's "accepted" as the client builds it ({@code InGameController.diplomacyHandler}). */
    private static ModelMessage accepted(Player other) {
        final ModelMessage m = new ModelMessage(ModelMessage.MessageType.FOREIGN_DIPLOMACY,
            ClassicNotices.OFFER_ACCEPTED, other);
        m.addStringTemplate("%nation%", other.getNationLabel());
        return m;
    }

    /** FreeCol's "rejected" as the client builds it (an information message). */
    private static StringTemplate rejected(Player other) {
        return StringTemplate.template(ClassicNotices.OFFER_REJECTED)
            .addStringTemplate("%nation%", other.getNationLabel());
    }

    /** An ordinary notice (the mounds' "nothing", @BURIAL1 with the pack). */
    private static ModelMessage mounds(Player p) {
        return new ModelMessage(ModelMessage.MessageType.LOST_CITY_RUMOUR,
            "model.lostCityRumour.nothing.mounds.description", p);
    }

    /**
     * K3 (Roger 2026-10-08, screenshot 1): FreeCol's "Ihr trefft auf einen
     * Späher der Tupi aus Paraná-mirim." never comes, in both of the
     * server's variants (our unit next to a village, one of its people
     * next to ours in the natives' turn): not alone, not among other
     * notices, not in the turn report.  The other notices keep their
     * boxes and their order.
     */
    public void testTheMeetingNoticeNeverComes() throws Exception {
        final Game game = getStandardGame();
        final IndianSettlement is = arawakVillage(game);
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final Unit ours = new ServerUnit(game, is.getTile().getNeighbourOrNull(Direction.N),
            dutch, spec().getUnitType("model.unit.freeColonist"));
        final Unit brave = is.getOwnedUnitList().get(0);
        final ModelMessage ourMove = meeting(ours, is), theirMove = meeting(brave, is);
        final ModelMessage other = mounds(dutch), other2 = mounds(dutch);
        assertTrue(ClassicNotices.dropped(ClassicNotices.SCOUT_MEETING));
        assertFalse(ClassicNotices.dropped(other.getId()));
        assertFalse(ClassicNotices.dropped(ClassicNotices.OFFER_ACCEPTED));   // only as an echo
        assertFalse(ClassicNotices.dropped(null));
        assertNull("not one of the original's notices",
                   ClassicNotices.rule(ClassicNotices.SCOUT_MEETING));
        final NoticeGUI gui = new NoticeGUI(game, dutch);
        final Keeper keep = new Keeper();
        gui.prompter = keep;
        assertEquals(List.of(other, other2),
                     gui.noticesShown(List.of(ourMove, other, theirMove, other2)));
        assertTrue(gui.noticesShown(List.of(ourMove, theirMove)).isEmpty());
        // The input list stays as it was.
        final List<ModelMessage> in = new ArrayList<>(List.of(ourMove, other));
        gui.noticesShown(in);
        assertEquals(List.of(ourMove, other), in);
        // Through the GUI's two channels: no box for it.
        gui.showModelMessages(List.of(ourMove));
        gui.showReportTurnPanel(List.of(theirMove, ourMove));
        assertTrue(keep.boxes.isEmpty());
        gui.showModelMessages(List.of(theirMove, other));
        assertEquals(1, keep.boxes.size());
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        assertEquals((t != null) ? "BURIAL1" : "message " + other.getId(),
                     keep.boxes.get(0).id);
    }

    /**
     * K3 (Roger 2026-10-08, screenshot 2: "Spanien hat Euer großzügiges
     * Angebot angenommen." — "ich habe den Spaniern gar kein Angebot
     * gemacht"): the "accepted" and "rejected" that the server's result
     * brings back after our own answer to another nation's proposal never
     * come: the first contact's peace (accepted without a box, sent first
     * to us or to them) and a proposal answered in the box ("Abbrechen"
     * and "Annehmen").  Those of a nation whose proposal we did not answer
     * still come, in FreeCol's words.
     */
    public void testTheEchoOfOurAnswerNeverComes() throws Exception {
        final Game game = getStandardGame();
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final Player spanish = game.getPlayerByNationId("model.nation.spanish");
        final Player french = game.getPlayerByNationId("model.nation.french");
        final Player english = game.getPlayerByNationId("model.nation.english");
        final NoticeGUI gui = new NoticeGUI(game, dutch);
        final Keeper keep = new Keeper();
        gui.prompter = keep;
        final ModelMessage other = mounds(dutch);

        // The rule itself.
        final java.util.Set<String> spain = java.util.Set.of(ClassicNotices.nationKey(spanish));
        assertTrue(ClassicNotices.isAnswerEcho(accepted(spanish), spain));
        assertTrue(ClassicNotices.isAnswerEcho(rejected(spanish), spain));
        assertFalse(ClassicNotices.isAnswerEcho(accepted(french), spain));
        assertFalse(ClassicNotices.isAnswerEcho(accepted(spanish), java.util.Set.of()));
        assertFalse(ClassicNotices.isAnswerEcho(accepted(spanish), null));
        assertFalse(ClassicNotices.isAnswerEcho(null, spain));
        assertFalse(ClassicNotices.isAnswerEcho(StringTemplate.template("model.x")
            .addStringTemplate("%nation%", spanish.getNationLabel()), spain));
        assertFalse(ClassicNotices.isAnswerEcho(
            StringTemplate.template(ClassicNotices.OFFER_ACCEPTED), spain));
        assertNull(ClassicNotices.nationKey(null));
        assertNull(ClassicNotices.rule(ClassicNotices.OFFER_ACCEPTED));
        assertNull(ClassicNotices.rule(ClassicNotices.OFFER_REJECTED));

        // Before any answer, Spain's "accepted" is no echo.
        final ModelMessage fromSpain = accepted(spanish);
        assertEquals(List.of(fromSpain, other), gui.noticesShown(List.of(fromSpain, other)));

        // The first contact's peace, sent first to us (our pioneer met
        // Isabella, Roger's turn 6): accepted without a box, no echo.
        final List<DiplomaticTrade> got = new ArrayList<>();
        final DiplomaticTrade peace = DiplomaticTrade.makePeaceTreaty(
            DiplomaticTrade.TradeContext.CONTACT, dutch, spanish);
        SwingUtilities.invokeAndWait(() ->
            gui.showNegotiationDialog(null, null, peace, null, got::add));
        assertEquals(List.of(peace), got);
        assertEquals(DiplomaticTrade.TradeStatus.ACCEPT_TRADE, peace.getStatus());
        assertTrue(keep.boxes.isEmpty());
        assertEquals(List.of(other), gui.noticesShown(List.of(fromSpain, other)));
        assertEquals("twice, as for a notice held for the first scene",
                     List.of(other), gui.noticesShown(List.of(fromSpain, other)));
        gui.showModelMessages(List.of(fromSpain));
        gui.showReportTurnPanel(List.of(accepted(spanish)));
        assertTrue(keep.boxes.isEmpty());
        // Another nation's "accepted" still comes.
        final ModelMessage fromFrance = accepted(french);
        assertEquals(List.of(fromFrance), gui.noticesShown(List.of(fromFrance)));

        // The peace sent first to them (their unit met ours): the same.
        final DiplomaticTrade theirs = DiplomaticTrade.makePeaceTreaty(
            DiplomaticTrade.TradeContext.CONTACT, english, dutch);
        SwingUtilities.invokeAndWait(() ->
            gui.showNegotiationDialog(null, null, theirs, null, got::add));
        assertEquals(DiplomaticTrade.TradeStatus.ACCEPT_TRADE, theirs.getStatus());
        assertTrue(gui.noticesShown(List.of(accepted(english))).isEmpty());
        assertTrue(keep.boxes.isEmpty());

        // France's proposal in the box: "Abbrechen", then France's
        // "rejected" (an information notice) and "accepted" are echoes.
        final DiplomaticTrade gold = new DiplomaticTrade(game,
            DiplomaticTrade.TradeContext.DIPLOMATIC, french, dutch,
            List.of(new GoldTradeItem(game, french, dutch, 100)), 1);
        keep.answer = 1;
        SwingUtilities.invokeAndWait(() ->
            gui.showNegotiationDialog(null, null, gold, null, got::add));
        assertEquals(DiplomaticTrade.TradeStatus.REJECT_TRADE, gold.getStatus());
        assertEquals(1, keep.boxes.size());                 // the proposal's box
        assertNull(gui.showInformationPanel(null, rejected(french)));
        SwingUtilities.invokeAndWait(() -> { });            // a posted box
        SwingUtilities.invokeAndWait(() -> gui.showInformationPanel(null, rejected(french)));
        assertEquals(1, keep.boxes.size());
        assertTrue(gui.noticesShown(List.of(fromFrance)).isEmpty());
        // "Annehmen" the same.
        keep.answer = 0;
        gold.setStatus(DiplomaticTrade.TradeStatus.PROPOSE_TRADE);
        SwingUtilities.invokeAndWait(() ->
            gui.showNegotiationDialog(null, null, gold, null, got::add));
        assertEquals(DiplomaticTrade.TradeStatus.ACCEPT_TRADE, gold.getStatus());
        assertEquals(2, keep.boxes.size());
        assertTrue(gui.noticesShown(List.of(accepted(french))).isEmpty());

        // A GUI that answered nothing: both come, in FreeCol's words.
        final NoticeGUI fresh = new NoticeGUI(game, dutch);
        final Keeper keep2 = new Keeper();
        fresh.prompter = keep2;
        SwingUtilities.invokeAndWait(() -> fresh.showInformationPanel(null, rejected(french)));
        fresh.showModelMessages(List.of(accepted(french)));
        assertEquals(2, keep2.boxes.size());
        assertEquals("notice " + ClassicNotices.OFFER_REJECTED, keep2.boxes.get(0).id);
        assertEquals(Messages.message(rejected(french)), keep2.boxes.get(0).plainText());
        assertEquals("message " + ClassicNotices.OFFER_ACCEPTED, keep2.boxes.get(1).id);
    }

    /**
     * K3: GAME.TXT has none of the dropped notices (Roger: "den gibt es
     * auch gar nicht im Original"): no "trefft", no offer "angenommen" or
     * "abgelehnt" (needs the pack, else skipped with a note).
     */
    public void testTheOriginalHasNoneOfThem() throws Exception {
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final File g = (pack == null) ? null : pack.textFile(ClassicText.GAME);
        if (g == null) {
            System.err.println("ClassicNoticesTest: testTheOriginalHasNoneOfThem"
                + " skipped, no pack texts (ant classic-assets)");
            return;
        }
        final List<String> lines = ClassicText.decode(
            java.nio.file.Files.readAllBytes(g.toPath()));
        assertTrue(lines.size() > 1000);
        int greetings = 0;
        for (String l : lines) {
            assertFalse(l, l.contains("trefft"));
            assertFalse(l, l.contains("Angebot angenommen"));
            assertFalse(l, l.contains("Angebot abgelehnt"));
            if (l.startsWith("@HELLOFIRST") || l.startsWith("@INDIANWELCOME")) greetings++;
        }
        assertEquals("the original's own meeting boxes are there", 2, greetings);
    }

    /**
     * K3 on the server: the meeting notice the Classic UI drops is the one
     * FreeCol's server sends us, in both variants: our unit next to a
     * village, and one of a village's people next to our unit in the
     * natives' turn (each village once).
     */
    public void testTheServerSendsTheDroppedMeetingNotice() throws Exception {
        final Game game = net.sf.freecol.server.ServerTestHelper
            .startServerGame(getTestMap(true));
        try {
            final net.sf.freecol.server.model.ServerPlayer dutch
                = getServerPlayer(game, "model.nation.dutch");
            final net.sf.freecol.common.model.Map map = game.getMap();
            final net.sf.freecol.common.model.UnitType colonist
                = spec().getUnitType("model.unit.freeColonist");
            // Our unit next to a Tupi village.
            final IndianSettlement tupi = new IndianSettlementBuilder(game)
                .settlementTile(map.getTile(5, 8)).build();
            final Tile a = tupi.getTile().getNeighbourOrNull(Direction.N);
            final ServerUnit ours = new ServerUnit(game, a, dutch, colonist);
            dutch.exploreTile(tupi.getTile());   // the server sends it for an explored village only
            net.sf.freecol.common.networking.ChangeSet cs
                = new net.sf.freecol.common.networking.ChangeSet();
            ours.csNewContactCheck(a, false, cs);
            assertMeeting(cs, dutch, tupi);
            // An Arawak brave next to our other unit, far from both villages.
            final IndianSettlement arawak = new IndianSettlementBuilder(game)
                .player(game.getPlayerByNationId("model.nation.arawak"))
                .settlementTile(map.getTile(14, 3)).build();
            final ServerUnit brave = (ServerUnit) arawak.getOwnedUnitList().get(0);
            dutch.exploreTile(arawak.getTile());
            final Tile b = map.getTile(14, 11);
            brave.setLocation(b);
            new ServerUnit(game, b.getNeighbourOrNull(Direction.N), dutch, colonist);
            cs = new net.sf.freecol.common.networking.ChangeSet();
            brave.csNewContactCheck(b, false, cs);
            assertMeeting(cs, dutch, arawak);
        } finally {
            net.sf.freecol.server.model.Session.clearAll();
            net.sf.freecol.server.ServerTestHelper.stopServerGame();
        }
    }

    /** One meeting notice in {@code cs}, to {@code to}, about {@code is}. */
    private static void assertMeeting(net.sf.freecol.common.networking.ChangeSet cs,
                                      Player to, IndianSettlement is) {
        String found = null;
        for (String line : cs.toString().split("\n")) {
            if (!line.contains(ClassicNotices.SCOUT_MEETING)) continue;
            assertNull("one only: " + cs, found);
            found = line;
        }
        assertNotNull(cs.toString(), found);
        assertTrue(found, found.contains(" to " + to.getId()));
        assertTrue(found, found.contains(is.getName()));
    }


    // The golden check

    /**
     * A notice the game builds, drawn over a frame: the box and the
     * portrait's opaque pixels compared.
     *
     * @return {compared, off, off on the original's arrow}.
     */
    private static int[] compare(BufferedImage frame, int ox, int oy,
                                 ClassicAdvisorBox.Request r, ClassicPackFiles pack,
                                 Rectangle box) {
        return compare(frame, ox, oy, r, pack, box, -1);
    }

    /**
     * {@link #compare(BufferedImage, int, int, ClassicAdvisorBox.Request,
     * ClassicPackFiles, Rectangle)} with the bar drawn on a row.
     *
     * @param bar The bar's row, or -1 for none.
     */
    private static int[] compare(BufferedImage frame, int ox, int oy,
                                 ClassicAdvisorBox.Request r, ClassicPackFiles pack,
                                 Rectangle box, int bar) {
        final ClassicFont tiny = pack.font(ClassicFont.TINY);
        final BufferedImage pic = (r.portrait.sprite == null) ? null
            : pack.image(ClassicPackFiles.ssKey(r.portrait.sprite));
        final ClassicAdvisorBox.Layout l = ClassicAdvisorBox.layout(r, tiny, pic);
        assertEquals(r.id, box, l.box);
        final BufferedImage canvas = new BufferedImage(320, 200,
            BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = canvas.createGraphics();
        g.drawImage(frame, ox, oy, null);
        ClassicAdvisorBox.paint(g, l, bar, pack.image(ClassicMenuBar.WOOD_KEY), tiny);
        g.dispose();
        final Raster idx = (frame.getColorModel() instanceof IndexColorModel)
            ? frame.getRaster() : null;
        final int[] out = new int[3];
        for (int y = 0; y < frame.getHeight(); y++) {
            for (int x = 0; x < frame.getWidth(); x++) {
                final int sx = ox + x, sy = oy + y;
                boolean on = l.box.contains(sx, sy);
                if (!on && pic != null) {
                    final int px = sx - l.portraitAt.x, py = sy - l.portraitAt.y;
                    on = px >= 0 && py >= 0 && px < pic.getWidth() && py < pic.getHeight()
                        && (pic.getRGB(px, py) >>> 24) != 0;
                }
                if (!on) continue;
                out[0]++;
                if ((frame.getRGB(x, y) & 0xFFFFFF) == (canvas.getRGB(sx, sy) & 0xFFFFFF)) {
                    continue;
                }
                final int i = (idx == null) ? -1 : idx.getSample(x, y, 0);
                out[(i == 0 || i == 7 || i == 15) ? 2 : 1]++;
            }
        }
        return out;
    }

    /**
     * The golden check (class comment): each notice as the GUI builds it
     * from FreeCol's message, over its frame: 0 px off on the box and the
     * portrait, apart from the original's mouse arrow; each box where the
     * clip has it.
     */
    public void testGoldenAgainstTheClips() throws Exception {
        final String clips = System.getProperty(ClassicTerrainGoldenTest.CLIPS_PROPERTY);
        final File dir = (clips == null) ? null : new File(clips);
        if (dir == null || !new File(dir, "clip008").isDirectory()) {
            System.err.println(getClass().getSimpleName()
                + ": golden check skipped, no recordings (-D"
                + ClassicTerrainGoldenTest.CLIPS_PROPERTY + ")");
            return;
        }
        final ClassicText t = texts("testGoldenAgainstTheClips");
        if (t == null) return;
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final Game game = getStandardGame();
        final IndianSettlement is = arawakVillage(game);
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final Object[][] cases = {
            { "landfall/analysis/img/21_BURIAL1_frontiersman_1x.png", 42, 45,
              new ModelMessage(ModelMessage.MessageType.LOST_CITY_RUMOUR,
                  "model.lostCityRumour.nothing.mounds.description", dutch), null,
              new Rectangle(42, 132, 236, 24) },
            { "clip008/frame_046396.png", 0, 0,
              StringTemplate.template("scoutSettlement.expertScout")
                  .addNamed("%unit%", spec().getUnitType("model.unit.seasonedScout")),
              is, new Rectangle(7, 79, 236, 42) },
            { "clip008/frame_037156.png", 0, 0, StringTemplate.key("info.noMoreSkill"),
              is, null },
            { "clip008/frame_051431.png", 0, 0,
              StringTemplate.template("scoutSettlement.speakBeads").add("%amount%", "640"),
              is, new Rectangle(7, 82, 236, 36) },
        };
        final StringBuilder fails = new StringBuilder();
        int compared = 0, arrow = 0;
        for (Object[] c : cases) {
            final StringTemplate m = (StringTemplate) c[3];
            final ClassicAdvisorBox.Request r = ClassicNotices.request(t, "notice",
                m, Messages.message(m), (IndianSettlement) c[4], dutch, "t", null);
            assertFalse((String) c[0], "notice".equals(r.id));
            final BufferedImage frame = ImageIO.read(new File(dir, (String) c[0]));
            assertNotNull((String) c[0], frame);
            Rectangle box = (Rectangle) c[5];
            if (box == null) {
                box = ClassicAdvisorBox.layout(r, pack.font(ClassicFont.TINY),
                    pack.image(ClassicPackFiles.ssKey(r.portrait.sprite))).box;
            }
            final int[] got = compare(frame, (Integer) c[1], (Integer) c[2], r, pack, box);
            compared += got[0];
            arrow += got[2];
            if (got[1] > 0) fails.append(' ').append(r.id).append('=').append(got[1]);
        }
        System.out.println(getClass().getSimpleName() + ": golden check, "
            + cases.length + " notices, " + compared + " px compared, off:"
            + ((fails.length() == 0) ? " none" : fails.toString()) + ", "
            + arrow + " arrow px excused");
        assertEquals("pixels off:" + fails, 0, fails.length());
        assertTrue("arrow pixels " + arrow, arrow <= 100);
    }

    /** A village of a tribe on the test map, teaching and wanting as given. */
    private static IndianSettlement village(Game game, String nation, int x, int y,
                                            String skill, String taught,
                                            String... wanted) {
        final IndianSettlement is = new IndianSettlementBuilder(game)
            .player(game.getPlayerByNationId(nation))
            .settlementTile(game.getMap().getTile(x, y)).build();
        is.setLearnableSkill((skill == null) ? null
            : spec().getUnitType("model.unit." + skill));
        is.setTaughtSkill((taught == null) ? null
            : spec().getUnitType("model.unit." + taught));
        for (int i = 0; i < wanted.length; i++) {
            is.setWantedGoods(i, spec().getGoodsType("model.goods." + wanted[i]));
        }
        return is;
    }

    /**
     * Part O, bundle B, golden (the pack and {@code -Dclassic.clips}): each
     * new box as the game builds it, from FreeCol's model and the pack's
     * texts, drawn over the frame that shows it: the box, its words and
     * values, its bar and its portrait 0 px off (the original's mouse
     * arrow excused).  @CHIEFHOWDY three times (the Apache's scouts and
     * «Rum», «Nahrungsmittel», «Pferde», playthrough-1 #47458; the Inca's
     * silver miners, playthrough-2 #15301; the Iroquois' sugar planters
     * after they taught one, playthrough-1 #82938, D-B3c), @UNREST three
     * times (playthrough-1 #15418 «Meister-Schreiner», #48094 «Freie
     * Siedler», playthrough-2 #5758 «Erfahrene Fischer»), @LEARNSTAY with
     * its bar (playthrough-1 #9449, playthrough-2 #27101), @LEARNDONE
     * (playthrough-1 #9713), @LEARNLATER (playthrough-2 #27250),
     * @WELLSEASONED (clip008 #47096), @INDIANWELCOME with its bar
     * (playthrough-1 #4742, playthrough-3 #103212), @INDIANPEACE and
     * @INDIANCOME (playthrough-2 #18984, #19091).
     */
    public void testBundleBAgainstTheClips() throws Exception {
        final String clips = System.getProperty(ClassicTerrainGoldenTest.CLIPS_PROPERTY);
        final File dir = (clips == null) ? null : new File(clips);
        if (dir == null || !new File(dir, "playthrough-1").isDirectory()) {
            System.err.println(getClass().getSimpleName()
                + ": bundle B golden skipped, no recordings (-D"
                + ClassicTerrainGoldenTest.CLIPS_PROPERTY + ")");
            return;
        }
        final ClassicText t = texts("testBundleBAgainstTheClips");
        if (t == null) return;
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final Game game = getStandardGame();
        game.changeMap(getTestMap());
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final IndianSettlement apache = village(game, "model.nation.apache", 3, 3,
            "seasonedScout", null, "rum", "food", "horses");
        final IndianSettlement inca = village(game, "model.nation.inca", 12, 3,
            "expertSilverMiner", null, "tools", "tradeGoods", "rum");
        final IndianSettlement iroquois = village(game, "model.nation.iroquois", 3, 14,
            null, "masterSugarPlanter", "tools", "rum", "coats");
        final IndianSettlement arawak = village(game, "model.nation.arawak", 12, 14,
            "expertFarmer", null);
        final StringTemplate done = StringTemplate.template("learnSkill.done")
            .addNamed("%skill%", spec().getUnitType("model.unit.masterSugarPlanter"));
        final Object[][] cases = {
            // frame, the box, its place, the bar
            { "playthrough-1/frame_047458.png", ClassicNotices.howdy(t, apache).build(),
              new Rectangle(84, 79, 236, 42), -1 },
            { "playthrough-2/frame_015301.png", ClassicNotices.howdy(t, inca).build(),
              new Rectangle(78, 79, 236, 42), -1 },
            { "playthrough-1/frame_082938.png", ClassicNotices.howdy(t, iroquois).build(),
              new Rectangle(84, 79, 236, 42), -1 },
            { "playthrough-1/frame_015418.png", unrest(t, dutch, "masterCarpenter"),
              new Rectangle(47, 108, 226, 36), -1 },
            { "playthrough-1/frame_048094.png", unrest(t, dutch, "freeColonist"),
              new Rectangle(47, 108, 226, 36), -1 },
            { "playthrough-2/frame_005758.png", unrest(t, dutch, "expertFisherman"),
              new Rectangle(47, 108, 226, 36), -1 },
            { "playthrough-1/frame_009449.png", ClassicNotices.learnStay(t, iroquois,
                  spec().getUnitType("model.unit.masterSugarPlanter")).build(),
              new Rectangle(84, 74, 236, 52), 0 },
            { "playthrough-2/frame_027101.png", ClassicNotices.learnStay(t, arawak,
                  arawak.getLearnableSkill()).build(),
              new Rectangle(7, 74, 236, 52), 0 },
            { "playthrough-1/frame_009713.png", ClassicNotices.request(t, "notice", done,
                  Messages.message(done), iroquois, dutch, "t", null),
              new Rectangle(84, 79, 236, 42), -1 },
            { "playthrough-2/frame_027250.png", ClassicNotices.learnLater(t, 2).build(),
              new Rectangle(7, 88, 236, 24), -1 },
            { "clip008/frame_047096.png", ClassicNotices.wellSeasoned(t).build(),
              new Rectangle(42, 132, 236, 24), -1 },
            { "playthrough-1/frame_004742.png", ClassicNotices.welcome(t, 3, 18).build(),
              new Rectangle(84, 68, 236, 64), 0 },
            { "playthrough-3/frame_103212.png", ClassicNotices.welcome(t, 1, 6).build(),
              new Rectangle(5, 68, 236, 64), 0 },
            { "playthrough-2/frame_018984.png", ClassicNotices.contactNotice(t,
                  "INDIANPEACE", 2, dutch).build(), new Rectangle(7, 82, 236, 36), -1 },
            { "playthrough-2/frame_019091.png", ClassicNotices.contactNotice(t,
                  "INDIANCOME", 2, dutch).build(), new Rectangle(7, 82, 236, 36), -1 },
        };
        final StringBuilder fails = new StringBuilder();
        int compared = 0, arrow = 0;
        for (Object[] c : cases) {
            final ClassicAdvisorBox.Request r = (ClassicAdvisorBox.Request) c[1];
            final BufferedImage frame = ImageIO.read(new File(dir, (String) c[0]));
            assertNotNull((String) c[0], frame);
            final int[] got = compare(frame, 0, 0, r, pack, (Rectangle) c[2],
                                      (Integer) c[3]);
            compared += got[0];
            arrow += got[2];
            // At most one mouse arrow per frame (its 7x12 sprite: under 90 px).
            assertTrue(c[0] + " arrow px " + got[2], got[2] <= 90);
            if (got[1] > 0) {
                fails.append(' ').append(c[0]).append(' ').append(r.id).append('=')
                    .append(got[1]);
            }
        }
        System.out.println(getClass().getSimpleName() + ": bundle B golden, "
            + cases.length + " boxes, " + compared + " px compared, off:"
            + ((fails.length() == 0) ? " none" : fails.toString()) + ", "
            + arrow + " arrow px excused");
        assertEquals("pixels off:" + fails, 0, fails.length());
        assertTrue(compared > 100000);
    }

    /** @UNREST as the GUI builds it for a colonist of a type on our docks. */
    private static ClassicAdvisorBox.Request unrest(ClassicText t, Player dutch,
                                                    String type) {
        final Unit u = new ServerUnit(dutch.getGame(), dutch.getEurope(), dutch,
            spec().getUnitType("model.unit." + type));
        final ModelMessage m = dutch.getEmigrationMessage(u);
        final ClassicAdvisorBox.Request r = ClassicNotices.request(t, "message", m,
            Messages.message(m), u, dutch, "t", null);
        assertEquals(type, "UNREST", r.id);
        return r;
    }
}
