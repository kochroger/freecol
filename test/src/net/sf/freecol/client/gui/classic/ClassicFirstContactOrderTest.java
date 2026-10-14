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
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

import javax.swing.SwingUtilities;

import net.sf.freecol.FreeCol;
import net.sf.freecol.client.ClientOptions;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.control.InGameController;
import net.sf.freecol.common.io.FreeColXMLReader;
import net.sf.freecol.common.io.FreeColXMLWriter;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Player.NoClaimReason;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.common.networking.AnimateMoveMessage;
import net.sf.freecol.common.networking.ChangeSet;
import net.sf.freecol.common.networking.Connection;
import net.sf.freecol.common.networking.FirstContactMessage;
import net.sf.freecol.common.networking.Message;
import net.sf.freecol.common.networking.MultipleMessage;
import net.sf.freecol.common.networking.NewLandNameMessage;
import net.sf.freecol.common.networking.ServerAPI;
import net.sf.freecol.server.FreeColServer;
import net.sf.freecol.server.ServerTestHelper;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.server.model.Session;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * The first contact with the client's own game, as in the live game (the
 * O fixer, review of part O, F1): the client's game is the server's as
 * sent at login, every server reply goes over XML (as the connection
 * writes it) into the client's game, and its messages go through the
 * client's real handlers in their order.  {@link ClassicNativeBoxesTest}
 * shares one game between client and server, so it cannot see the order.
 *
 * <p>The contact made by our own move is answered while the move's reply
 * is handled (a key on the event thread, the box nested there).  While
 * {@code FirstContactMessage} came first in that reply, the reply's own
 * provisional peace and its copy of the tile came after the answer's
 * reply: after «Nein» the client was at peace while the server was at war
 * (until about turn 27 live), after «Ja» at the first landing the gift
 * was the tribe's again on the client (B there asked @INDIANLAND for
 * 400$), and the chief spoke while our unit still stood on its old tile.
 * The chain of one contact also comes before the next one, and holds the
 * turn flow between its boxes (acceptance F2, F3).
 */
public class ClassicFirstContactOrderTest extends FreeColTestCase {

    private static final UnitType colonist = spec().getUnitType("model.unit.freeColonist");
    private static final UnitType cottonPlanter
        = spec().getUnitType("model.unit.masterCottonPlanter");
    private static final TileType plains = spec().getTileType("model.tile.plains");


    // The rig

    /** A server reply as the client gets it: over XML into its game, the children in order. */
    static List<Message> toClient(Message m, ServerPlayer to, Game clientGame) throws Exception {
        final List<Message> out = new ArrayList<>();
        if (m == null) return out;
        final StringWriter sw = new StringWriter();
        try (FreeColXMLWriter xw = new FreeColXMLWriter(sw,
                 FreeColXMLWriter.WriteScope.toClient(to))) {
            m.toXML(xw);
        }
        final Message cm;
        try (FreeColXMLReader xr = new FreeColXMLReader(new StringReader(sw.toString()))) {
            xr.nextTag();
            cm = Message.read(clientGame, xr);
        }
        if (!(cm instanceof MultipleMessage)) {
            out.add(cm);
            return out;
        }
        final Field f = MultipleMessage.class.getDeclaredField("messages");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        final List<Message> ms = (List<Message>)f.get(cm);
        out.addAll(ms);
        return out;
    }

    /**
     * Handle a reply's messages in order through the client's handlers,
     * as {@code MultipleMessage.clientHandler} does (the move's animation
     * and the land's name, which need a real view or server, left out).
     */
    static void handle(List<Message> reply, FreeColClient fcc, List<String> trace) {
        for (Message m : reply) {
            if (m instanceof AnimateMoveMessage || m instanceof NewLandNameMessage) continue;
            try {
                m.clientHandler(fcc);
            } catch (Exception e) {
                trace.add(m.getType() + " threw " + e);
            }
        }
    }

    /** The client's server API: the first contact's answer to the server, its reply back. */
    private static final class Loopback extends ServerAPI {

        final FreeColServer server;
        final ServerPlayer player;
        final List<String> log = new ArrayList<>();
        FreeColClient fcc;

        Loopback(FreeColServer server, ServerPlayer player) {
            this.server = server;
            this.player = player;
        }

        @Override
        public boolean firstContact(Player player, Player other, Tile tile,
                                    boolean result) {
            this.log.add("firstContact " + other.getNation().getSuffix() + " " + result
                + ((tile == null) ? "" : " tile"));
            final ChangeSet cs = new FirstContactMessage(player, other, tile)
                .setResult(result).serverHandler(this.server, this.player);
            try {
                // As Connection.request: the reply handled in the asking thread.
                handle(toClient(cs.build(this.player), this.player, this.fcc.getGame()),
                       this.fcc, this.log);
            } catch (Exception e) {
                this.log.add("reply failed " + e);
            }
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

    /** The boxes, and what the client's game said when each was asked. */
    private static final class Watch implements ClassicGUI.Prompter {

        final ClassicGUISeamTest.KeyPrompter keys = new ClassicGUISeamTest.KeyPrompter();
        final List<String> ids = new ArrayList<>();
        final List<String> states = new ArrayList<>();
        java.util.function.Supplier<String> state = () -> "";
        java.util.function.Consumer<ClassicAdvisorBox.Request> also = null;

        @Override
        public int ask(ClassicAdvisorBox.Request r) {
            this.ids.add(r.id);
            this.states.add(this.state.get());
            if (this.also != null) this.also.accept(r);
            return this.keys.ask(r);
        }
    }

    private static void set(FreeColClient fcc, String name, Object value) throws Exception {
        final Field f = FreeColClient.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(fcc, value);
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

    /** A server game, a client with its own game, FreeCol's real controller and the Classic UI. */
    private static final class Rig {
        Game game, clientGame;
        ServerPlayer dutch, arawak;
        Player clientDutch, clientArawak;
        Loopback api;
        FreeColClient fcc;
        ClassicGUI gui;
        Unit active;
        final Watch watch = new Watch();
        final List<String> trace = new ArrayList<>();

        /** "client WE/THEY server WE/THEY". */
        String stances() {
            return "client " + this.clientDutch.getStance(this.clientArawak) + "/"
                + this.clientArawak.getStance(this.clientDutch) + " server "
                + this.dutch.getStance(this.arawak) + "/" + this.arawak.getStance(this.dutch);
        }
    }

    private Rig rig() throws Exception {
        final Rig r = new Rig();
        ServerTestHelper.stopServerGame();
        Session.clearAll();
        r.game = ServerTestHelper.startServerGame(getCoastTestMap(plains, true));
        r.dutch = getServerPlayer(r.game, "model.nation.dutch");
        r.dutch.setAI(false);
        r.arawak = getServerPlayer(r.game, "model.nation.arawak");
        r.api = new Loopback(ServerTestHelper.getServer(), r.dutch);
        final Class<?> uc = Class.forName("sun.misc.Unsafe");
        final Field theUnsafe = uc.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        r.fcc = (FreeColClient)uc.getMethod("allocateInstance", Class.class)
            .invoke(theUnsafe.get(null), FreeColClient.class);
        r.api.fcc = r.fcc;
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
        r.gui.prompter = r.watch;
        r.gui.colonyPrompter = r.watch;
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
        r.game.setCurrentPlayer(r.dutch);
        return r;
    }

    /** The client's game, made after the server's setup (as at login). */
    private static void login(Rig r) throws Exception {
        r.clientGame = ClassicTerrainOracleTest.clientView(r.game, r.dutch);
        r.clientDutch = r.clientGame.getPlayerByNationId("model.nation.dutch");
        r.clientArawak = r.clientGame.getPlayerByNationId("model.nation.arawak");
        r.fcc.setGame(r.clientGame);
        r.fcc.setMyPlayer(r.clientDutch);
        set(r.fcc, "inGame", Boolean.TRUE);
        r.clientGame.setCurrentPlayer(r.clientDutch);
    }

    /** Let the boxes the controller posted come (a box may post one). */
    private static void flush() throws Exception {
        for (int i = 0; i < 4; i++) SwingUtilities.invokeAndWait(() -> { });
    }

    /** An Arawak village at (5,8), not met. */
    private static IndianSettlement village(Rig r) {
        return new FreeColTestCase.IndianSettlementBuilder(r.game)
            .player(r.arawak).settlementTile(r.game.getMap().getTile(5, 8))
            .skillToTeach(cottonPlanter.getId()).capital(false).build();
    }

    /** The owner of a tile, for the messages. */
    private static String owner(Tile t) {
        return (t.getOwner() == null) ? "-" : t.getOwner().getNation().getSuffix();
    }

    /** The pack's texts, or null (then the chain's ids are not checked). */
    private ClassicText pack(String what) {
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        if (t == null) {
            System.err.println(getClass().getSimpleName() + ": " + what
                + " without the pack (ant classic-assets): the chain's boxes not checked");
        }
        return t;
    }

    /** FreeCol's classic flag before the test (the war is the Classic UI's). */
    private boolean savedClassic;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.savedClassic = FreeCol.getClassic();
        FreeCol.setClassic(true);
    }

    @Override
    protected void tearDown() throws Exception {
        FreeCol.setClassic(this.savedClassic);
        ServerTestHelper.stopServerGame();
        super.tearDown();
    }

    /**
     * Our colonist steps from (5,6) onto (5,7), next to the village (onto
     * its land: at the first landing, the land offer); the server's real
     * move reply is handled on the event thread, as after our key.
     *
     * @param r The rig.
     * @param keys The keys for every box.
     * @param landing The move is the game's first landing.
     * @return The unit, on the client.
     */
    private Unit ourMove(Rig r, String keys, boolean landing) throws Exception {
        village(r);
        if (!landing) r.dutch.setNewLandName("Neu-Holland");
        final Tile start = r.game.getMap().getTile(5, 6);
        final Tile dest = r.game.getMap().getTile(5, 7);
        assertSame(r.arawak, dest.getOwner());
        final ServerUnit u = new ServerUnit(r.game, start, r.dutch, colonist);
        u.setMovesLeft(u.getInitialMovesLeft());
        r.dutch.invalidateCanSeeTiles();
        r.dutch.exploreForUnit(u);
        login(r);
        final Unit cu = r.clientGame.getFreeColGameObject(u.getId(), Unit.class);
        r.active = cu;
        final Tile cDest = r.clientGame.getMap().getTile(5, 7);
        assertEquals(Stance.UNCONTACTED, r.clientDutch.getStance(r.clientArawak));
        r.watch.state = () -> "at " + cu.getTile().getX() + "," + cu.getTile().getY()
            + " moves " + cu.getMovesLeft() + " stance "
            + r.clientDutch.getStance(r.clientArawak) + " tile " + owner(cDest)
            + " hold " + r.gui.contactChainHolds();
        final ChangeSet cs = ServerTestHelper.getServer().getInGameController()
            .move(r.dutch, u, dest);
        final List<Message> reply = toClient(cs.build(r.dutch), r.dutch, r.clientGame);
        final List<String> order = new ArrayList<>();
        for (Message m : reply) order.add(m.getType());
        assertTrue(order.toString(), order.contains(FirstContactMessage.TAG));
        r.watch.keys.press(keys.split(" "));
        SwingUtilities.invokeAndWait(() -> handle(reply, r.fcc, r.trace));
        flush();
        assertEquals(r.trace.toString(), 0, r.trace.size());
        return cu;
    }

    /**
     * The order of the move's reply (the O fixer): the contact comes after
     * the provisional peace and the updates, before the land's name.
     */
    public void testTheMoveReplysOrder() throws Exception {
        final Rig r = rig();
        village(r);
        final ServerUnit u = new ServerUnit(r.game, r.game.getMap().getTile(5, 6),
                                            r.dutch, colonist);
        u.setMovesLeft(u.getInitialMovesLeft());
        r.dutch.exploreForUnit(u);
        login(r);
        final ChangeSet cs = ServerTestHelper.getServer().getInGameController()
            .move(r.dutch, u, r.game.getMap().getTile(5, 7));
        final List<String> order = new ArrayList<>();
        for (Message m : toClient(cs.build(r.dutch), r.dutch, r.clientGame)) {
            order.add(m.getType());
        }
        final int contact = order.indexOf(FirstContactMessage.TAG);
        assertTrue(order.toString(), contact > order.lastIndexOf("setStance"));
        assertTrue(order.toString(), contact > order.lastIndexOf("update"));
        assertTrue(order.toString(), contact < order.indexOf(NewLandNameMessage.TAG));
    }

    /**
     * «Nein» at a contact our own move made (F1): war on both sides, also
     * on the client, which before went back to peace when the rest of the
     * move's reply came.  The chief speaks while our colonist stands on
     * its new tile with its moves spent (the review's later item: it
     * stood on its old tile), the provisional peace already known.
     */
    public void testNeinOnOurOwnMove() throws Exception {
        final ClassicText t = pack("testNeinOnOurOwnMove");
        final Rig r = rig();
        final Unit cu = ourMove(r, "DOWN ENTER", false);
        assertEquals(r.api.log.toString(), "client WAR/WAR server WAR/WAR", r.stances());
        assertEquals(List.of("firstContact arawak false"), r.api.log);
        assertEquals("first-contact arawak", r.watch.ids.get(0));
        final String atWelcome = r.watch.states.get(0);
        assertTrue(atWelcome, atWelcome.startsWith("at 5,7 moves "
            + cu.getMovesLeft() + " stance PEACE tile arawak hold true"));
        assertTrue(cu.getMovesLeft() < cu.getInitialMovesLeft());
        assertFalse(r.gui.contactChainHolds());
        if (t == null) return;
        assertEquals(List.of("first-contact arawak", "INDIANSHUN"), r.watch.ids);
    }

    /**
     * «Ja» at the game's first landing, on the tribe's land: the land is
     * ours on the server and on the client (before, the client gave it
     * back to the tribe, and founding a colony there asked @INDIANLAND
     * for 400$, review F1b): nothing to claim, no price.
     */
    public void testJaAtTheFirstLanding() throws Exception {
        final ClassicText t = pack("testJaAtTheFirstLanding");
        final Rig r = rig();
        ourMove(r, "ENTER", true);
        final Tile dest = r.game.getMap().getTile(5, 7);
        final Tile cDest = r.clientGame.getMap().getTile(5, 7);
        assertEquals(List.of("firstContact arawak true tile"), r.api.log);
        assertEquals("client PEACE/PEACE server PEACE/PEACE", r.stances());
        assertEquals("server", "dutch", owner(dest));
        assertEquals("client", "dutch", owner(cDest));
        assertEquals(NoClaimReason.NONE, r.dutch.canClaimToFoundSettlementReason(dest));
        assertEquals(NoClaimReason.NONE, r.clientDutch.canClaimToFoundSettlementReason(cDest));
        assertEquals(0, r.clientDutch.getLandPrice(cDest));
        if (t == null) return;
        assertEquals(List.of("first-contact arawak", "INDIANPEACE", "INDIANCOME"), r.watch.ids);
    }

    /** «Nein» at the first landing: war on both sides, the land the tribe's on both. */
    public void testNeinAtTheFirstLanding() throws Exception {
        final Rig r = rig();
        ourMove(r, "DOWN ENTER", true);
        assertEquals(List.of("firstContact arawak false tile"), r.api.log);
        assertEquals("client WAR/WAR server WAR/WAR", r.stances());
        assertEquals("arawak", owner(r.game.getMap().getTile(5, 7)));
        assertEquals("arawak", owner(r.clientGame.getMap().getTile(5, 7)));
    }

    /**
     * A brave walks next to our colonist in the Arawak turn: the server's
     * message is handled on the client's network thread with the Arawak
     * as the current player, the box comes after the natives' moves, in
     * our turn.  «Nein»: war on both sides; «Ja»: peace, @INDIANPEACE and
     * no @INDIANCOME (acceptance F3, Roger's D-B1c).
     */
    public void testABraveInTheirTurn() throws Exception {
        final ClassicText t = pack("testABraveInTheirTurn");
        for (String keys : new String[] { "DOWN ENTER", "ENTER" }) {
            final Rig r = rig();
            village(r);
            r.dutch.setNewLandName("Neu-Holland");
            final ServerUnit ours = new ServerUnit(r.game, r.game.getMap().getTile(5, 4),
                                                   r.dutch, colonist);
            r.dutch.exploreForUnit(ours);
            final ServerUnit brave = new ServerUnit(r.game, r.game.getMap().getTile(5, 6),
                r.arawak, spec().getDefaultUnitType(r.arawak));
            brave.setMovesLeft(brave.getInitialMovesLeft());
            login(r);
            r.active = r.clientGame.getFreeColGameObject(ours.getId(), Unit.class);
            r.game.setCurrentPlayer(r.arawak);
            r.clientGame.setCurrentPlayer(r.clientArawak);
            final ChangeSet cs = new ChangeSet();
            brave.csMove(r.game.getMap().getTile(5, 5), new Random(1), cs);
            final List<Message> reply = toClient(cs.build(r.dutch), r.dutch, r.clientGame);
            r.watch.keys.press(keys.split(" "));
            // The event thread is busy with the natives' moves until our turn.
            final CountDownLatch slides = new CountDownLatch(1);
            SwingUtilities.invokeLater(() -> {
                    try {
                        slides.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            handle(reply, r.fcc, r.trace);   // the network thread
            r.game.setCurrentPlayer(r.dutch);
            r.clientGame.setCurrentPlayer(r.clientDutch);
            slides.countDown();
            flush();
            assertEquals(r.trace.toString(), 0, r.trace.size());
            final boolean war = keys.startsWith("DOWN");
            assertEquals(keys, List.of("firstContact arawak " + !war), r.api.log);
            assertEquals(keys, (war) ? "client WAR/WAR server WAR/WAR"
                         : "client PEACE/PEACE server PEACE/PEACE", r.stances());
            if (t == null) continue;
            assertEquals(keys, (war) ? List.of("first-contact arawak", "INDIANSHUN")
                         : List.of("first-contact arawak", "INDIANPEACE"), r.watch.ids);
        }
    }

    /** A classic GUI without a client: the game and our player the test's. */
    private static final class ContactGUI extends ClassicGUI {

        final Game game;
        final Player me;

        ContactGUI(Game game, Player me) {
            super(null);
            this.game = game;
            this.me = me;
            this.woodcutter = (k, notBefore, followMs) -> 1L;
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
        protected Player getMyPlayer() {
            return this.me;
        }
    }

    /**
     * Two contacts at once (acceptance F2): the second is posted behind
     * the first and comes while the first's box lets the event thread
     * run (here: inside its question).  It waits until the first's last
     * box has been asked: Sioux WELCOME, Sioux PEACE, Apache WELCOME,
     * Apache PEACE (before: the Apache chain inside the Sioux one, the
     * Sioux peace last).  The turn flow is held from the first question
     * until the last chain's last box is asked (that box is due then, so
     * its close lets the flow go on), and after it nothing holds it.
     */
    public void testOneChainAfterTheOther() throws Exception {
        final ClassicText t = pack("testOneChainAfterTheOther");
        if (t == null) return;
        final Game game = getStandardGame();
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final Player sioux = game.getPlayerByNationId("model.nation.sioux");
        final Player apache = game.getPlayerByNationId("model.nation.apache");
        game.setCurrentPlayer(dutch);
        final ContactGUI gui = new ContactGUI(game, dutch);
        final Watch watch = new Watch();
        gui.prompter = watch;
        watch.state = () -> "hold " + gui.contactChainHolds() + " blocked " + gui.turnBlocked();
        final List<String> answers = new ArrayList<>();
        watch.also = (b) -> {
            if ("first-contact sioux".equals(b.id) && answers.isEmpty()) {
                // The posted second contact, run inside the first box.
                gui.showFirstContactDialog(dutch, apache, null, 13, false,
                    a -> answers.add("apache " + a));
            }
        };
        watch.keys.press("ENTER");
        SwingUtilities.invokeAndWait(() -> gui.showFirstContactDialog(dutch, sioux, null,
            21, false, a -> answers.add("sioux " + a)));
        assertEquals(List.of("first-contact sioux", "INDIANPEACE",
                             "first-contact apache", "INDIANPEACE"), watch.ids);
        assertEquals(List.of("sioux true", "apache true"), answers);
        assertEquals(List.of("hold true blocked true", "hold true blocked true",
                             "hold true blocked true", "hold false blocked false"),
                     watch.states);
        assertTrue(watch.keys.boxes.get(1).plainText().contains("Sioux"));
        assertTrue(watch.keys.boxes.get(3).plainText().contains("Apachen"));
        SwingUtilities.invokeAndWait(() -> {
                assertFalse(gui.contactChainHolds());
                assertFalse(gui.turnBlocked());
            });
    }

    /**
     * The hold ends also when the answer's handler throws, and when no
     * box follows the answer: a chain never holds the turn flow for good.
     */
    public void testTheHoldAlwaysEnds() throws Exception {
        final Game game = getStandardGame();
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final Player sioux = game.getPlayerByNationId("model.nation.sioux");
        game.setCurrentPlayer(dutch);
        final ContactGUI gui = new ContactGUI(game, dutch);
        final Watch watch = new Watch();
        gui.prompter = watch;
        watch.keys.press("ENTER");
        final boolean[] during = { false };
        SwingUtilities.invokeAndWait(() -> gui.showFirstContactDialog(dutch, sioux, null,
            21, true, a -> {
                during[0] = gui.contactChainHolds();
                throw new IllegalStateException("the server failed");
            }));
        assertTrue("held while the answer goes out", during[0]);
        SwingUtilities.invokeAndWait(() -> assertFalse(gui.contactChainHolds()));
        assertEquals(List.of("first-contact sioux"), watch.ids);
    }
}
