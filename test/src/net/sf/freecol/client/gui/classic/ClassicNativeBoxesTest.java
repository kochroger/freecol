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

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;

import net.sf.freecol.client.ClientOptions;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.control.InGameController;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Direction;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.common.networking.AskSkillMessage;
import net.sf.freecol.common.networking.AttackMessage;
import net.sf.freecol.common.networking.ChangeSet;
import net.sf.freecol.common.networking.Connection;
import net.sf.freecol.common.networking.DemandTributeMessage;
import net.sf.freecol.common.networking.FirstContactMessage;
import net.sf.freecol.common.networking.LearnSkillMessage;
import net.sf.freecol.common.networking.ScoutIndianSettlementMessage;
import net.sf.freecol.common.networking.ServerAPI;
import net.sf.freecol.common.option.GameOptions;
import net.sf.freecol.server.FreeColServer;
import net.sf.freecol.server.ServerTestHelper;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * Part O, bundle B through FreeCol's real client controller (prep
 * {@code o/prep/B-boxes.md}; Roger 2026-10-13): the first contact (B1:
 * «Ja» peace, «Nein» war, Escape nothing, the chain after the answer) and
 * the learning at a village (B2: @LEARNSTAY, @LEARNDONE, @LEARNLATER; a
 * refusal keeps the move), with the Classic UI as the GUI and a real
 * server game: the client's server API hands each request to the server's
 * own handler (as {@link ClassicLossesTest} does), the keys are pressed on
 * the real bar ({@link ClassicGUISeamTest.KeyPrompter}).  Before part O
 * the first contact's answer never reached the server without a land
 * offer (so «Nein» cost nothing), and a success at a village had no word.
 */
public class ClassicNativeBoxesTest extends FreeColTestCase {

    private static final UnitType colonist = spec().getUnitType("model.unit.freeColonist");
    private static final UnitType cottonPlanter
        = spec().getUnitType("model.unit.masterCottonPlanter");
    private static final TileType plains = spec().getTileType("model.tile.plains");


    // The harness (ClassicLossesTest's)

    /** The client's server API: each request to the server's own handler. */
    private static final class Loopback extends ServerAPI {

        final FreeColServer server;
        final ServerPlayer player;
        final List<String> log = new ArrayList<>();

        Loopback(FreeColServer server, ServerPlayer player) {
            this.server = server;
            this.player = player;
        }

        private boolean handle(String what, ChangeSet cs) {
            final String s = (cs == null) ? "" : cs.toString();
            this.log.add(what + ((s.contains("error") || s.contains("reject")) ? " refused" : ""));
            return true;
        }

        @Override
        public boolean firstContact(Player player, Player other, Tile tile,
                                    boolean result) {
            return handle("firstContact " + other.getNation().getSuffix() + " " + result,
                new FirstContactMessage(player, other, tile).setResult(result)
                    .serverHandler(this.server, this.player));
        }

        @Override
        public boolean askSkill(Unit unit, Direction direction) {
            return handle("askSkill",
                new AskSkillMessage(unit, direction).serverHandler(this.server, this.player));
        }

        @Override
        public boolean learnSkill(Unit unit, Direction direction) {
            return handle("learnSkill",
                new LearnSkillMessage(unit, direction).serverHandler(this.server, this.player));
        }

        // Part Z, Z2.1: the village boxes' actions.

        @Override
        public boolean attack(Unit unit, Direction direction) {
            return handle("attack",
                new AttackMessage(unit, direction).serverHandler(this.server, this.player));
        }

        @Override
        public boolean demandTribute(Unit unit, Direction direction) {
            return handle("demandTribute", new DemandTributeMessage(unit, direction)
                .serverHandler(this.server, this.player));
        }

        @Override
        public boolean scoutSettlement(Unit unit, Direction direction) {
            return handle("scoutSettlement", new ScoutIndianSettlementMessage(unit,
                direction).serverHandler(this.server, this.player));
        }

        @Override
        public boolean scoutSpeakToChief(Unit unit, IndianSettlement is) {
            this.log.add("scoutSpeakToChief");   // its result is the handler's
            return true;
        }

        @Override
        public boolean missionary(Unit unit, Direction direction, boolean denounce) {
            this.log.add("missionary " + ((denounce) ? "denounce" : "establish"));
            return true;
        }

        @Override
        public boolean newNativeTradeSession(Unit unit, IndianSettlement is) {
            this.log.add("newNativeTradeSession");   // FreeCol's session behind it
            return true;
        }

        @Override
        public Connection connect(String name, String host, int port) {
            return null;
        }

        @Override
        public boolean disconnect() {
            return true;
        }

        @Override
        public Connection reconnect() {
            return null;
        }

        @Override
        public Connection getConnection() {
            return null;
        }
    }

    private static void set(FreeColClient fcc, String name, Object value) throws Exception {
        final Field f = FreeColClient.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(fcc, value);
    }

    /** The Classic UI of a test, the boxes are pressed keys. */
    private static final class Rig {
        Game game;
        ServerPlayer dutch;
        Loopback api;
        FreeColClient fcc;
        ClassicGUI gui;
        Unit active;
        final ClassicGUISeamTest.KeyPrompter keys = new ClassicGUISeamTest.KeyPrompter();

        InGameController igc() {
            return this.fcc.getInGameController();
        }

        /** The ids of the boxes asked so far. */
        List<String> asked() {
            final List<String> out = new ArrayList<>();
            for (ClassicAdvisorBox.Request r : this.keys.boxes) out.add(r.id);
            return out;
        }
    }

    /**
     * A client of a coast map's server game (land x 0-9, sea from 10)
     * with FreeCol's real controller and the Classic UI as its GUI, our
     * (human) player's turn.
     */
    private Rig rig() throws Exception {
        final Rig r = new Rig();
        ServerTestHelper.stopServerGame();
        r.game = ServerTestHelper.startServerGame(getCoastTestMap(plains, true));
        r.dutch = getServerPlayer(r.game, "model.nation.dutch");
        r.dutch.setAI(false);
        r.api = new Loopback(ServerTestHelper.getServer(), r.dutch);
        final Class<?> uc = Class.forName("sun.misc.Unsafe");
        final Field theUnsafe = uc.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        r.fcc = (FreeColClient)uc.getMethod("allocateInstance", Class.class)
            .invoke(theUnsafe.get(null), FreeColClient.class);
        r.gui = new ClassicGUI(r.fcc) {
                @Override
                public Unit getActiveUnit() {
                    return r.active;
                }

                @Override
                public void playSound(String sound) {}

                @Override
                public net.sf.freecol.client.gui.ImageLibrary getFixedImageLibrary() {
                    return LIBRARY;
                }

                @Override
                java.awt.Image meetingIcon(Player other) {
                    return null;   // no image resources here
                }

                @Override
                java.awt.Image iconOf(net.sf.freecol.common.model.FreeColObject d) {
                    return null;
                }
            };
        r.gui.prompter = r.keys;
        r.gui.colonyPrompter = r.keys;
        r.gui.woodcutter = (k, notBefore, followMs) -> 1L;
        set(r.fcc, "gui", r.gui);
        set(r.fcc, "serverAPI", r.api);
        set(r.fcc, "clientOptions", new ClientOptions() {
                @Override
                public boolean getBoolean(String id) {
                    return false;
                }
            });
        set(r.fcc, "inGameController", new InGameController(r.fcc));
        r.fcc.setGame(r.game);
        r.fcc.setMyPlayer(r.dutch);
        set(r.fcc, "inGame", Boolean.TRUE);
        r.game.setCurrentPlayer(r.dutch);
        return r;
    }

    /** Any picture for every key. */
    private static final net.sf.freecol.client.gui.ImageLibrary LIBRARY
        = new net.sf.freecol.client.gui.ImageLibrary(1f,
            new net.sf.freecol.common.resources.ImageCache() {
                @Override
                public BufferedImage getScaledImage(String key, float scale, boolean grey) {
                    return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                }

                @Override
                public BufferedImage getSizedImage(String key, java.awt.Dimension size,
                                                   boolean grey, int seed) {
                    return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                }
            });

    /** Let the boxes the controller posted come (twice: a box may post one). */
    private static void flush() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });
    }

    /** The pack's texts, or null (then the words are not checked). */
    private ClassicText pack(String what) {
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        if (t == null) {
            System.err.println(getClass().getSimpleName() + ": " + what
                + " without the pack (ant classic-assets): the words not checked");
        }
        return t;
    }

    /** FreeCol's classic flag before the test (the war is the Classic UI's). */
    private boolean savedClassic;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.savedClassic = net.sf.freecol.FreeCol.getClassic();
        net.sf.freecol.FreeCol.setClassic(true);
    }

    @Override
    protected void tearDown() throws Exception {
        net.sf.freecol.FreeCol.setClassic(this.savedClassic);
        ServerTestHelper.stopServerGame();
        super.tearDown();
    }


    // B1: the first contact

    /**
     * The first contact through the controller ({@code firstContactHandler},
     * as the server's {@code FirstContactMessage} calls it), no land offer:
     * Enter takes «Ja», the peace, then @INDIANPEACE and @INDIANCOME (our
     * turn); Down Enter «Nein»: the answer reaches the server (before part
     * O it never did without a land offer) and it is war both ways, then
     * @INDIANSHUN; Escape does nothing (Escape then Enter is the peace, not
     * the war, as Escape was before).
     */
    public void testFirstContactThroughTheController() throws Exception {
        final ClassicText t = pack("testFirstContactThroughTheController");
        for (String keys : new String[] { "ENTER", "DOWN ENTER", "ESC ENTER",
                                          "ESC ESC DOWN UP ENTER" }) {
            final Rig r = rig();
            final ServerPlayer arawak = getServerPlayer(r.game, "model.nation.arawak");
            r.dutch.csNativeFirstContact(arawak, null, new ChangeSet());   // the server's
            assertEquals(keys, Stance.PEACE, arawak.getStance(r.dutch));
            r.keys.press(keys.split(" "));
            r.igc().firstContactHandler(r.dutch, arawak, null, 6);
            flush();
            final boolean war = "DOWN ENTER".equals(keys);
            assertEquals(keys, List.of("firstContact arawak " + !war), r.api.log);
            assertEquals(keys, (war) ? Stance.WAR : Stance.PEACE, r.dutch.getStance(arawak));
            assertEquals(keys, (war) ? Stance.WAR : Stance.PEACE, arawak.getStance(r.dutch));
            final List<String> asked = r.asked();
            assertEquals(keys, "first-contact arawak", asked.get(0));
            if (t == null) continue;
            assertEquals(keys, (war) ? List.of("first-contact arawak", "INDIANSHUN")
                : List.of("first-contact arawak", "INDIANPEACE", "INDIANCOME"), asked);
            assertEquals("IND2A0.SS.000", r.keys.boxes.get(1).portrait.sprite);
        }
    }

    /**
     * A contact one of theirs made in the natives' turn (acceptance F3):
     * the server's message comes then, on the client's network thread,
     * with the tribe as the current player; the box comes only after the
     * natives' moves, when the current player is already ours.  Whose
     * turn it was is the message's time: «Ja» brings @INDIANPEACE and no
     * @INDIANCOME (Roger's D-B1c; clip004 #5094, the Sioux met in their
     * phase).  In our own turn the same call brings @INDIANCOME.
     */
    public void testAContactInTheNativesTurn() throws Exception {
        final ClassicText t = pack("testAContactInTheNativesTurn");
        for (boolean theirs : new boolean[] { true, false }) {
            final Rig r = rig();
            final ServerPlayer sioux = getServerPlayer(r.game, "model.nation.sioux");
            r.dutch.csNativeFirstContact(sioux, null, new ChangeSet());
            r.game.setCurrentPlayer((theirs) ? sioux : r.dutch);
            r.keys.press("ENTER");
            // The event thread is busy (the natives' moves) until our turn.
            final java.util.concurrent.CountDownLatch slides
                = new java.util.concurrent.CountDownLatch(1);
            SwingUtilities.invokeLater(() -> {
                    try {
                        slides.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            assertFalse(SwingUtilities.isEventDispatchThread());
            r.igc().firstContactHandler(r.dutch, sioux, null, 21);
            r.game.setCurrentPlayer(r.dutch);   // our turn, before the box
            slides.countDown();
            flush();
            assertEquals("theirs=" + theirs, List.of("firstContact sioux true"), r.api.log);
            assertEquals(Stance.PEACE, r.dutch.getStance(sioux));
            if (t == null) continue;
            assertEquals("theirs=" + theirs, (theirs)
                ? List.of("first-contact sioux", "INDIANPEACE")
                : List.of("first-contact sioux", "INDIANPEACE", "INDIANCOME"), r.asked());
        }
    }


    // B2: learning at a village

    /** An Arawak village teaching cotton planting, met, next to our colonist. */
    private Unit learner(Rig r) {
        final ServerPlayer arawak = getServerPlayer(r.game, "model.nation.arawak");
        final IndianSettlement is = new FreeColTestCase.IndianSettlementBuilder(r.game)
            .player(arawak).settlementTile(r.game.getMap().getTile(5, 8))
            .skillToTeach(cottonPlanter.getId()).capital(false).build();
        Player.makeContact(arawak, r.dutch);
        is.setContacted(r.dutch);
        final Tile at = is.getTile().getNeighbourOrNull(Direction.N);
        final Unit u = new ServerUnit(r.game, at, r.dutch, colonist);
        r.active = u;
        return u;
    }

    /**
     * The colonist moves onto the village (FreeCol's {@code moveLearnSkill},
     * Roger's house rule {@code cancelKeepsMove} on): first the village box
     * (part Z, Z2.1, @VILLAGESAVAGE for the Arawak, «Mit den Ureinwohnern
     * leben / Handlung abbrechen», the bar on row 1; V playthrough-1 #8480
     * then @LEARNSTAY #9027); its «Handlung abbrechen» and Escape send
     * nothing and keep the move.  After «leben» @LEARNSTAY with the chief,
     * 19 frames after the box's close, the bar on «Dann will ich ein
     * Meister-Baumwollpflanzer werden.»; Enter learns and @LEARNDONE
     * follows (before: no word at all), the village keeps the skill it
     * taught (D-B3c); Down Enter and Escape bring @LEARNLATER and keep the
     * colonist and his moves; Down Up Enter learns.
     */
    public void testLearningThroughTheController() throws Exception {
        final ClassicText t = pack("testLearningThroughTheController");
        if (t == null) return;
        final String[][][] cases = {
            // the village box's keys, @LEARNSTAY's keys
            { { "ENTER" }, { "ENTER" } },
            { { "ENTER" }, { "DOWN", "ENTER" } },
            { { "ENTER" }, { "ESC" } },
            { { "ENTER" }, { "DOWN", "UP", "ENTER" } },
            { { "DOWN", "ENTER" } },
            { { "ESC" } },
        };
        for (String[][] keys : cases) {
            final String what = java.util.Arrays.deepToString(keys);
            final Rig r = rig();
            final Specification spec = r.game.getSpecification();
            final boolean rule = spec.getBoolean(GameOptions.CANCEL_KEEPS_MOVE);
            spec.setBoolean(GameOptions.CANCEL_KEEPS_MOVE, true);
            try {
                final Unit u = learner(r);
                final IndianSettlement is = (IndianSettlement)u.getTile()
                    .getNeighbourOrNull(Direction.S).getSettlement();
                final int moves = u.getMovesLeft();
                assertTrue(moves > 0);
                r.keys.press("ENTER").pressEach(keys);
                r.igc().moveDirection(u, Direction.S, true);
                flush();
                final boolean live = keys.length > 1;
                final boolean learn = live && keys[1][keys[1].length - 1].equals("ENTER")
                    && keys[1].length != 2;
                if (learn) {
                    assertEquals(what + " " + r.api.log, List.of("askSkill", "learnSkill"),
                                 r.api.log);
                    assertSame(what, cottonPlanter, u.getType());
                    assertEquals(what, 0, u.getMovesLeft());
                    assertNull(what, is.getLearnableSkill());
                    assertSame("the village keeps it", cottonPlanter, is.getTaughtSkill());
                } else {
                    assertEquals(what, (live) ? List.of("askSkill") : List.of(), r.api.log);
                    assertSame(what, colonist, u.getType());
                    assertEquals("the refusal keeps the move", moves, u.getMovesLeft());
                    assertSame(what, cottonPlanter, is.getLearnableSkill());
                }
                final ClassicAdvisorBox.Request box = r.keys.boxes.get(0);
                assertEquals(ClassicVillageBox.SAVAGE, box.id);
                assertEquals(List.of("Mit den Ureinwohnern leben", "Handlung abbrechen"),
                             List.of(box.plainRows()));
                assertEquals(0, box.defaultRow);
                assertEquals(1, box.escapeAnswer());
                assertSame(ClassicAdvisorBox.Portrait.NONE, box.portrait);
                if (!live) {
                    assertEquals(what, List.of(ClassicVillageBox.SAVAGE), r.asked());
                    continue;
                }
                assertEquals(what, List.of(ClassicVillageBox.SAVAGE, "LEARNSTAY",
                                           (learn) ? "LEARNDONE" : "LEARNLATER"), r.asked());
                final ClassicAdvisorBox.Request stay = r.keys.boxes.get(1);
                assertEquals("IND2A0.SS.000", stay.portrait.sprite);
                assertEquals(2, stay.rows.size());
                assertEquals(0, stay.defaultRow);
                assertEquals(1, stay.escapeAnswer());
                assertEquals(ClassicVillageBox.STAY_CHAIN_MS, stay.chainMs);
                assertTrue(stay.plainRows()[0], stay.plainRows()[0]
                    .contains("Meister-Baumwollpflanzer"));
                assertTrue(stay.plainText(), stay.plainText().contains("Araukaner"));
                final ClassicAdvisorBox.Request after = r.keys.boxes.get(2);
                assertEquals("IND2A0.SS.000", after.portrait.sprite);
                assertEquals(what, (learn) ? ClassicNotices.DONE_CHAIN_MS
                             : ClassicNotices.LATER_CHAIN_MS, after.chainMs);
                if (learn) {
                    assertTrue(after.plainText(),
                               after.plainText().contains("Meister-Baumwollpflanzer"));
                }
            } finally {
                spec.setBoolean(GameOptions.CANCEL_KEEPS_MOVE, rule);
            }
        }
    }

    /**
     * After the lesson the chief still names the village's skill (Roger's
     * D-B3c, V clip008): a scout's speak result at that village comes
     * with @CHIEFHOWDY naming «Meister-Baumwollpflanzer» (FreeCol had
     * forgotten it), through the controller's handler; after the guides
     * @WELLSEASONED.
     */
    public void testTheChiefNamesTheTaughtSkill() throws Exception {
        final ClassicText t = pack("testTheChiefNamesTheTaughtSkill");
        if (t == null) return;
        final Rig r = rig();
        final Specification spec = r.game.getSpecification();
        final boolean rule = spec.getBoolean(GameOptions.CANCEL_KEEPS_MOVE);
        spec.setBoolean(GameOptions.CANCEL_KEEPS_MOVE, true);
        try {
            final Unit u = learner(r);
            final IndianSettlement is = (IndianSettlement)u.getTile()
                .getNeighbourOrNull(Direction.S).getSettlement();
            is.setWantedGoods(0, spec().getGoodsType("model.goods.tools"));
            is.setWantedGoods(1, spec().getGoodsType("model.goods.tradeGoods"));
            is.setWantedGoods(2, spec().getGoodsType("model.goods.rum"));
            r.keys.press("ENTER");
            r.igc().moveDirection(u, Direction.S, true);
            flush();
            assertNull(is.getLearnableSkill());
            r.keys.boxes.clear();
            final Unit scout = new ServerUnit(r.game, u.getTile(), r.dutch,
                spec().getUnitType("model.unit.freeColonist"),
                spec().getRole("model.role.scout"));
            r.keys.press("ENTER");
            r.igc().scoutSpeakToChiefHandler(scout, is, "expert");
            flush();
            assertEquals(List.of("CHIEFHOWDY", "CHIEFGUIDES", "WELLSEASONED"), r.asked());
            final String howdy = r.keys.boxes.get(0).plainText();
            assertTrue(howdy, howdy.contains("Meister-Baumwollpflanzer"));
            assertTrue(howdy, howdy.contains("Werkzeuge"));
        } finally {
            spec.setBoolean(GameOptions.CANCEL_KEEPS_MOVE, rule);
        }
    }

    /**
     * The learn question without the original's words (no village next to
     * the colonist that teaches the named skill): FreeCol's box, as
     * before, its «no» on Escape.
     */
    public void testTheLearnQuestionWithoutAVillage() {
        final ClassicGUI gui = new ClassicGUI(null);
        gui.woodcutter = (k, notBefore, followMs) -> 1L;
        final ClassicGUISeamTest.KeyPrompter keys = new ClassicGUISeamTest.KeyPrompter();
        gui.prompter = keys;
        final StringTemplate q = StringTemplate.template(ClassicGUI.LEARN_QUESTION)
            .addNamed("%skill%", cottonPlanter);
        assertNull(ClassicGUI.learnVillage(null, q));
        keys.press("ESC");
        assertFalse(gui.modalConfirmDialog(null, q, (ImageIcon) null, "learnSkill.yes",
                                           "learnSkill.no", true));
        assertEquals(1, keys.boxes.size());
        assertEquals("confirm " + ClassicGUI.LEARN_QUESTION, keys.boxes.get(0).id);
        assertEquals(Messages.message("learnSkill.yes"), keys.boxes.get(0).plainRows()[0]);
    }


    // Part Z, Z2.1: the village box for every unit

    /**
     * A village that has taught already: the village box, then after
     * «Mit den Ureinwohnern leben» the chief's @LEARNALREADY, 20 frames
     * after the box's close (V playthrough-4 048 #44105, closed #44164,
     * @LEARNALREADY #44184); before part Z it came at once, without the
     * box.  The move is kept (Roger's house rule).
     */
    public void testAVillageThatTaughtAnswersAfterTheBox() throws Exception {
        final ClassicText t = pack("testAVillageThatTaughtAnswersAfterTheBox");
        if (t == null) return;
        final Rig r = rig();
        final Specification spec = r.game.getSpecification();
        final boolean rule = spec.getBoolean(GameOptions.CANCEL_KEEPS_MOVE);
        spec.setBoolean(GameOptions.CANCEL_KEEPS_MOVE, true);
        try {
            final Unit u = learner(r);
            final IndianSettlement is = u.getTile().getNeighbourOrNull(Direction.S)
                .getIndianSettlement();
            is.setLearnableSkill(null);
            final int moves = u.getMovesLeft();
            r.keys.press("ENTER");
            r.igc().moveDirection(u, Direction.S, true);
            flush();
            assertEquals(List.of("askSkill"), r.api.log);
            assertEquals(List.of(ClassicVillageBox.SAVAGE, "LEARNALREADY"), r.asked());
            final ClassicAdvisorBox.Request already = r.keys.boxes.get(1);
            assertEquals("IND2A0.SS.000", already.portrait.sprite);
            assertEquals(ClassicVillageBox.ALREADY_CHAIN_MS, already.chainMs);
            assertEquals(moves, u.getMovesLeft());
        } finally {
            spec.setBoolean(GameOptions.CANCEL_KEEPS_MOVE, rule);
        }
    }

    /**
     * A village of a tribe at (5,8), met, at peace, and a unit of ours
     * north of it (the active unit).
     *
     * @param r The rig.
     * @param nation The tribe's nation suffix.
     * @param type The unit's type id.
     * @param role The unit's role id, or null.
     * @return The unit.
     */
    private Unit atVillage(Rig r, String nation, String type, String role) {
        final ServerPlayer tribe = getServerPlayer(r.game, "model.nation." + nation);
        final IndianSettlement is = new FreeColTestCase.IndianSettlementBuilder(r.game)
            .player(tribe).settlementTile(r.game.getMap().getTile(5, 8))
            .skillToTeach(cottonPlanter.getId()).capital(false).build();
        Player.makeContact(tribe, r.dutch);
        is.setContacted(r.dutch);
        final Tile at = is.getTile().getNeighbourOrNull(Direction.N);
        final Unit u = (role == null)
            ? new ServerUnit(r.game, at, r.dutch, spec().getUnitType(type))
            : new ServerUnit(r.game, at, r.dutch, spec().getUnitType(type),
                             spec().getRole(role));
        r.active = u;
        return u;
    }

    /**
     * The armed unit's box through the controller ({@code
     * moveAttackSettlement}): @VILLAGEHAPPY for the Iroquois (Roger's event
     * #11 had FreeCol's «Einen Tribut fordern / Angriff / Abbrechen»), the
     * rows «Dorf angreifen / Zoll fordern / Handlung abbrechen», the bar on
     * «Dorf angreifen» (Roger's decision F4, built as the original: Enter
     * attacks, with no question, V landfall #26161), «ein Dorf der
     * Irokesen»; «Handlung abbrechen» and Escape send nothing and keep the
     * move.
     */
    public void testTheArmedUnitsVillageBox() throws Exception {
        final ClassicText t = pack("testTheArmedUnitsVillageBox");
        if (t == null) return;
        for (String keys : new String[] { "ENTER", "ESC", "DOWN DOWN ENTER" }) {
            final Rig r = rig();
            final Unit u = atVillage(r, "iroquois", "model.unit.freeColonist",
                                     "model.role.dragoon");
            assertEquals(Unit.MoveType.ATTACK_SETTLEMENT, u.getMoveType(Direction.S));
            final int moves = u.getMovesLeft();
            r.keys.press(keys.split(" "));
            r.igc().moveDirection(u, Direction.S, true);
            flush();
            final ClassicAdvisorBox.Request box = r.keys.boxes.get(0);
            assertEquals(keys, ClassicVillageBox.HAPPY, box.id);
            assertEquals(List.of("Dorf angreifen", "Zoll fordern", "Handlung abbrechen"),
                         List.of(box.plainRows()));
            assertEquals(0, box.defaultRow);
            assertEquals(2, box.escapeAnswer());
            assertTrue(box.plainText(), box.plainText().startsWith(
                "Ihre Expedition hat ein Dorf der Irokesen erreicht."));
            if ("ENTER".equals(keys)) {
                assertEquals(keys, List.of("attack"), r.api.log);
                assertEquals("no question before it", 1, r.keys.boxes.size());
            } else {
                assertEquals(keys, List.of(), r.api.log);
                assertEquals(keys, List.of(ClassicVillageBox.HAPPY), r.asked());
                assertEquals("the move kept", moves, u.getMovesLeft());
            }
        }
    }

    /**
     * The scout's box ({@code moveScoutIndianSettlement}): four rows «Mit
     * dem Häuptling sprechen / Zoll fordern / Dorf angreifen / Handlung
     * abbrechen», the bar on row 1 (V playthrough-1 #46977); Enter
     * speaks, row 3 attacks, Escape sends nothing more.
     */
    public void testTheScoutsVillageBox() throws Exception {
        final ClassicText t = pack("testTheScoutsVillageBox");
        if (t == null) return;
        for (String keys : new String[] { "ENTER", "DOWN DOWN ENTER", "ESC" }) {
            final Rig r = rig();
            final Unit u = atVillage(r, "apache", "model.unit.freeColonist",
                                     "model.role.scout");
            final Player apache = getServerPlayer(r.game, "model.nation.apache");
            r.dutch.putNationSummary(apache,   // the server's, at the scout's move
                new net.sf.freecol.common.model.NationSummary(apache, r.dutch));
            assertEquals(Unit.MoveType.ENTER_INDIAN_SETTLEMENT_WITH_SCOUT,
                         u.getMoveType(Direction.S));
            r.keys.press(keys.split(" "));
            r.igc().moveDirection(u, Direction.S, true);
            flush();
            final ClassicAdvisorBox.Request box = r.keys.boxes.get(0);
            assertEquals(ClassicVillageBox.HAPPY, box.id);
            assertEquals(List.of("Mit dem Häuptling sprechen", "Zoll fordern",
                                 "Dorf angreifen", "Handlung abbrechen"),
                         List.of(box.plainRows()));
            assertEquals(0, box.defaultRow);
            assertEquals(3, box.escapeAnswer());
            assertTrue(box.plainText(), box.plainText().contains("ein Lager der Apachen"));
            assertEquals(keys, ("ENTER".equals(keys))
                ? List.of("scoutSettlement", "scoutSpeakToChief")
                : ("ESC".equals(keys)) ? List.of("scoutSettlement")
                : List.of("scoutSettlement", "attack"), r.api.log);
        }
    }

    /**
     * The missionary's box ({@code getMissionaryChoice}): «Mission gründen
     * / Indianer aufwiegeln / Handlung abbrechen» (V playthrough-3
     * #107633); with a Spanish mission there «Span. Mission der Ketzerei
     * beschuldigen» in row 1's place (V playthrough-4 047 #9331); Enter
     * takes row 1, Escape nothing.
     */
    public void testTheMissionarysVillageBox() throws Exception {
        final ClassicText t = pack("testTheMissionarysVillageBox");
        if (t == null) return;
        for (boolean spanish : new boolean[] { false, true }) {
            for (String keys : new String[] { "ENTER", "ESC" }) {
                final Rig r = rig();
                final Unit u = atVillage(r, "sioux", "model.unit.jesuitMissionary",
                                         "model.role.missionary");
                final IndianSettlement is = u.getTile().getNeighbourOrNull(Direction.S)
                    .getIndianSettlement();
                if (spanish) {
                    final ServerPlayer spain = getServerPlayer(r.game, "model.nation.spanish");
                    is.setContacted(spain);
                    is.setMissionary(new ServerUnit(r.game, null, spain,
                        spec().getUnitType("model.unit.jesuitMissionary")));
                }
                assertEquals(Unit.MoveType.ENTER_INDIAN_SETTLEMENT_WITH_MISSIONARY,
                             u.getMoveType(Direction.S));
                r.keys.press(keys.split(" "));
                r.igc().moveDirection(u, Direction.S, true);
                flush();
                final String what = keys + " spanish=" + spanish;
                final ClassicAdvisorBox.Request box = r.keys.boxes.get(0);
                assertEquals(what, ClassicVillageBox.HAPPY, box.id);
                assertEquals(what, List.of((spanish) ? "Span. Mission der Ketzerei beschuldigen"
                                           : "Mission gründen", "Indianer aufwiegeln",
                                           "Handlung abbrechen"), List.of(box.plainRows()));
                assertEquals(0, box.defaultRow);
                assertEquals(2, box.escapeAnswer());
                assertEquals(what, ("ENTER".equals(keys))
                    ? List.of("missionary " + ((spanish) ? "denounce" : "establish"))
                    : List.of(), r.api.log);
            }
        }
    }

    /**
     * A wagon train with goods ({@code moveTrade}): «Mit Dorf handeln /
     * Handlung abbrechen» before FreeCol's session opens (V playthrough-3
     * #124831); Escape opens none and keeps the move.
     */
    public void testTheTradersVillageBox() throws Exception {
        final ClassicText t = pack("testTheTradersVillageBox");
        if (t == null) return;
        for (String keys : new String[] { "ENTER", "ESC" }) {
            final Rig r = rig();
            final Unit u = atVillage(r, "iroquois", "model.unit.wagonTrain", null);
            u.addGoods(spec().getGoodsType("model.goods.tradeGoods"), 100);
            assertEquals(Unit.MoveType.ENTER_SETTLEMENT_WITH_CARRIER_AND_GOODS,
                         u.getMoveType(Direction.S));
            final int moves = u.getMovesLeft();
            r.keys.press(keys.split(" "));
            r.igc().moveDirection(u, Direction.S, true);
            flush();
            final ClassicAdvisorBox.Request box = r.keys.boxes.get(0);
            assertEquals(ClassicVillageBox.HAPPY, box.id);
            assertEquals(List.of("Mit Dorf handeln", "Handlung abbrechen"),
                         List.of(box.plainRows()));
            assertEquals(1, box.escapeAnswer());
            if ("ENTER".equals(keys)) {
                assertEquals(List.of("newNativeTradeSession"), r.api.log);
            } else {
                assertEquals(List.of(), r.api.log);
                assertEquals(moves, u.getMovesLeft());
            }
        }
    }

    /**
     * An expert or a petty criminal ordered into the village by key
     * ({@link ClassicGUI#illegalMoveKey}, FreeCol's {@code
     * MOVE_NO_ACCESS_SKILL}): the village box first, «Mit den Ureinwohnern
     * leben» brings @LEARNMASTER (@LEARNCRIMINAL) with the chief 18 frames
     * after its close (V playthrough-4 048 #20572 then #20695); «Handlung
     * abbrechen» and Escape nothing.  The controller never sees the order.
     */
    public void testTheExpertsVillageBoxBeforeTheRefusal() throws Exception {
        final ClassicText t = pack("testTheExpertsVillageBoxBeforeTheRefusal");
        if (t == null) return;
        final Object[][] cases = {
            { "model.unit.masterCottonPlanter", "LEARNMASTER" },
            { "model.unit.pettyCriminal", "LEARNCRIMINAL" } };
        for (Object[] c : cases) {
            for (String keys : new String[] { "ENTER", "ESC", "DOWN ENTER" }) {
                final Rig r = rig();
                final Unit u = atVillage(r, "sioux", (String) c[0], null);
                assertEquals(Unit.MoveType.MOVE_NO_ACCESS_SKILL, u.getMoveType(Direction.S));
                final int moves = u.getMovesLeft();
                r.keys.press(keys.split(" "));
                final boolean[] refused = new boolean[1];
                SwingUtilities.invokeAndWait(() ->
                    refused[0] = r.gui.illegalMoveKey(u, Direction.S));
                assertTrue(refused[0]);
                assertEquals(List.of(), r.api.log);
                assertEquals(moves, u.getMovesLeft());
                final String what = c[0] + " " + keys;
                if ("ENTER".equals(keys)) {
                    assertEquals(what, List.of(ClassicVillageBox.HAPPY, c[1]), r.asked());
                    final ClassicAdvisorBox.Request no = r.keys.boxes.get(1);
                    assertEquals("IND6A0.SS.000", no.portrait.sprite);
                    assertEquals(ClassicVillageBox.ANSWER_CHAIN_MS, no.chainMs);
                } else {
                    assertEquals(what, List.of(ClassicVillageBox.HAPPY), r.asked());
                }
                assertEquals(List.of("Mit den Ureinwohnern leben", "Handlung abbrechen"),
                             List.of(r.keys.boxes.get(0).plainRows()));
            }
        }
    }


    // Part Z, Z2.2: the tribute without FreeCol's question

    /**
     * «Zoll fordern» asks nothing more (Roger's event #12, frame_064125:
     * FreeCol's «Es ist schwer, um sicher zu sein …» before it; the
     * original's chief answers 15-18 frames after the village box, the
     * M3 verifier's W4): the soldier's row 2 and the scout's row 2 send
     * the demand at once, with no box between.
     */
    public void testTheTributeAsksNothingMore() throws Exception {
        final ClassicText t = pack("testTheTributeAsksNothingMore");
        if (t == null) return;
        final Object[][] cases = {
            { "iroquois", "model.role.soldier", List.of("demandTribute") },
            { "apache", "model.role.scout", List.of("scoutSettlement", "demandTribute") } };
        for (Object[] c : cases) {
            final Rig r = rig();
            final Unit u = atVillage(r, (String) c[0], "model.unit.freeColonist",
                                     (String) c[1]);
            final Player tribe = getServerPlayer(r.game, "model.nation." + c[0]);
            r.dutch.putNationSummary(tribe,
                new net.sf.freecol.common.model.NationSummary(tribe, r.dutch));
            r.keys.press("DOWN", "ENTER");
            r.igc().moveDirection(u, Direction.S, true);
            flush();
            assertEquals(c[1] + "", c[2], r.api.log);
            assertEquals(c[1] + " no question", List.of(ClassicVillageBox.HAPPY), r.asked());
            assertEquals(0, u.getMovesLeft());
        }
    }
}
