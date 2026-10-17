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

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.sf.freecol.client.ClientOptions;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.control.InGameController;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Constants.ClaimAction;
import net.sf.freecol.common.model.FreeColGameObject;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.common.networking.ChangeSet;
import net.sf.freecol.common.networking.ClaimLandMessage;
import net.sf.freecol.common.networking.Connection;
import net.sf.freecol.common.networking.DisbandUnitMessage;
import net.sf.freecol.common.networking.DisembarkMessage;
import net.sf.freecol.common.networking.ServerAPI;
import net.sf.freecol.common.networking.UnloadGoodsMessage;
import net.sf.freecol.server.FreeColServer;
import net.sf.freecol.server.ServerTestHelper;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * Part O, bundle A: nothing is lost without a word (prep
 * {@code o/prep/A-losses.md}; Roger 2026-10-13: the original everywhere).
 * The native land claim (A2, {@link ClassicNativeLand}), U into a full
 * warehouse and U's one hold (A3), O's list at sea (A4,
 * {@link ClassicUnload}), Shift-D on a ship with units aboard (A5,
 * {@link ClassicIllegalMoves#disbandRefusal}) and FreeCol's arrears
 * question (A1's equip path, {@link ClassicBoycott}): the boxes against
 * GAME.TXT and the clips, and each order through the Classic UI with
 * FreeCol's real client controller and a real server game (the client's
 * server API hands each request to the server's own handler, as
 * {@link ClassicEuropePanelTest} does), its keys pressed on the real bar
 * ({@link ClassicGUISeamTest.KeyPrompter}).  Every test was run against
 * the code before the fix (the prep's probes at 48e69f0c8: Enter paid the
 * land, U stored 180 muskets in a warehouse of 100, O dumped every hold,
 * Shift-D killed the passengers).
 */
public class ClassicLossesTest extends FreeColTestCase {

    private static final UnitType merchantman = spec().getUnitType("model.unit.merchantman");
    private static final UnitType wagonTrain = spec().getUnitType("model.unit.wagonTrain");
    private static final UnitType colonist = spec().getUnitType("model.unit.freeColonist");
    private static final GoodsType furs = spec().getGoodsType("model.goods.furs");
    private static final GoodsType tools = spec().getGoodsType("model.goods.tools");
    private static final GoodsType muskets = spec().getGoodsType("model.goods.muskets");
    private static final GoodsType silver = spec().getGoodsType("model.goods.silver");
    private static final GoodsType food = spec().getGoodsType("model.goods.food");
    private static final TileType plains = spec().getTileType("model.tile.plains");


    // The harness

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
        public boolean unloadGoods(GoodsType type, int amount, Unit carrier) {
            return handle("unload " + type.getSuffix() + " " + amount,
                new UnloadGoodsMessage(type, amount, carrier).serverHandler(this.server, this.player));
        }

        @Override
        public boolean disembark(Unit unit) {
            return handle("disembark " + unit.getId(),
                new DisembarkMessage(unit).serverHandler(this.server, this.player));
        }

        @Override
        public boolean claimTile(Tile tile, FreeColGameObject claimant, int price) {
            return handle("claim " + tile.getX() + "," + tile.getY() + " " + price,
                new ClaimLandMessage(tile, claimant, price).serverHandler(this.server, this.player));
        }

        @Override
        public boolean disbandUnit(Unit unit) {
            return handle("disband " + unit.getId(),
                new DisbandUnitMessage(unit).serverHandler(this.server, this.player));
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

    /** The Classic UI of a test: the active unit is the test's, the boxes are pressed keys. */
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

        /**
         * U or O as the key map fires it: the Classic UI's own action of
         * the id, else FreeCol's ({@code UnloadAction}: the controller's
         * {@code unload} of the active unit).
         */
        void unloadKey() {
            final javax.swing.Action own = this.gui.classicAction("unloadAction");
            if (own != null) {
                own.actionPerformed(null);
            } else {
                igc().unload(this.active);
            }
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
     * turn.  FreeCol's constructor would start a game of its own, so the
     * object is made without it (as ClassicEuropePanelTest's client).
     */
    private Rig rig() throws Exception {
        final Rig r = new Rig();
        ServerTestHelper.stopServerGame();     // the map is built in a fresh game
        r.game = ServerTestHelper.startServerGame(getCoastTestMap(plains, true));
        r.dutch = getServerPlayer(r.game, "model.nation.dutch");
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
            };
        r.gui.prompter = r.keys;
        r.gui.colonyPrompter = r.keys;
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

    /** Any picture for every key: FreeCol's questions draw the unit without the art. */
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

    /** The pack's texts, or null (then a test says it is skipped). */
    private ClassicText pack(String what) {
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        if (t == null) {
            System.err.println(getClass().getSimpleName() + ": " + what
                               + " skipped, no pack (ant classic-assets)");
        }
        return t;
    }


    // A2: the native land claim

    /**
     * @INDIANLAND as the original's (V clip008 #6382, frame
     * c008_0136_colony_indianland): «Ihr seid in Araukaner-Land
     * eingedrungen ...», rows leave / «Wir bieten Euch 146$ für dieses
     * Land.» / take, the bar and Escape on leave, the Araucanian chief at
     * the right, the box at (7,73,236,54) 0 px; the pay row greyed
     * without the gold; without the pack and for a tribe not met
     * FreeCol's words, leave first, the same bar and Escape.
     */
    public void testTheClaimBoxIsTheOriginals() {
        final Game game = getStandardGame();
        final Player arawak = game.getPlayerByNationId("model.nation.arawak");
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        for (ClassicText tt : new ClassicText[] { null, t }) {
            if (tt == null && t == null) continue;
            final ClassicAdvisorBox.Request r = ClassicNativeLand.claimRequest(tt, arawak,
                true, 146, true, "Base");
            assertEquals(3, r.rows.size());
            assertEquals("the bar on leave", ClassicNativeLand.LEAVE_ROW, r.defaultRow);
            assertEquals("Escape leaves", ClassicNativeLand.LEAVE_ROW, r.escapeAnswer());
            assertTrue(r.enabled(1));
            assertEquals("IND2A0.SS.000", r.portrait.sprite);
            assertNull(ClassicNativeLand.action(r, 0));
            assertEquals(ClaimAction.CLAIM_ACCEPT, ClassicNativeLand.action(r, 1));
            assertEquals(ClaimAction.CLAIM_STEAL, ClassicNativeLand.action(r, 2));
            assertNull(ClassicNativeLand.action(r, ClassicAdvisorBox.Bar.DISMISSED));
            final ClassicAdvisorBox.Request poor = ClassicNativeLand.claimRequest(tt, arawak,
                true, 146, false, "Base");
            assertFalse("pay greyed", poor.enabled(1));
            assertNull("a greyed row takes nothing", ClassicNativeLand.action(poor, 1));
            assertEquals(0, new ClassicAdvisorBox.Bar(poor).enter());
        }
        // A tribe not met without the pack: FreeCol's words, leave and take.
        final ClassicAdvisorBox.Request unknown = ClassicNativeLand.claimRequest(null, arawak,
            false, 146, true, "Base");
        assertEquals("leave and take", 2, unknown.rows.size());
        assertEquals(0, unknown.defaultRow);
        assertEquals(0, unknown.escapeAnswer());
        assertEquals(ClaimAction.CLAIM_STEAL, ClassicNativeLand.action(unknown, 1));
        if (t == null) {
            pack("the claim box's text");
            return;
        }
        // With the pack the original's box for a tribe not met too (part
        // Z, Z2.4, N-F9; Roger's event #6): ClassicNativeNoticesTest.
        final ClassicAdvisorBox.Request unmet = ClassicNativeLand.claimRequest(t, arawak,
            false, 146, true, "Base");
        assertEquals(3, unmet.rows.size());
        assertTrue(unmet.plainText(), unmet.plainText().startsWith("\"Ihr seid in Araukaner-Land"));
        final ClassicAdvisorBox.Request r = ClassicNativeLand.claimRequest(t, arawak, true,
            146, true, "Base");
        assertEquals(ClassicNativeLand.SECTION, r.id);
        assertTrue(r.plainText(), r.plainText().startsWith("\"Ihr seid in Araukaner-Land"));
        assertTrue(ClassicAdvisorBox.plain(r.rows.get(0)).startsWith("\"Gut, wir werden"));
        assertEquals("\"Wir bieten Euch 146$ für dieses Land.\"",
                     ClassicAdvisorBox.plain(r.rows.get(1)));
        assertTrue(ClassicAdvisorBox.plain(r.rows.get(2)).contains("UNSER Land"));
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final BufferedImage chief = pack.image(ClassicPackFiles.ssKey(r.portrait.sprite));
        final ClassicAdvisorBox.Layout l = ClassicAdvisorBox.layout(r,
            pack.font(ClassicFont.TINY), chief);
        assertEquals("V c008_0136", new Rectangle(7, 73, 236, 54), l.box);
    }

    /** A colony next to land of the Incas, met; a tile of theirs west of it. */
    private Tile incaLand(Rig r, int gold) {
        final ServerPlayer inca = getServerPlayer(r.game, "model.nation.inca");
        Player.makeContact(r.dutch, inca);
        r.dutch.setGold(gold);
        createStandardColony(1, 8, 8);
        final Tile t = r.game.getMap().getTile(7, 8);
        t.changeOwnership(inca, null);
        return t;
    }

    /**
     * The claim through FreeCol's controller (a click on the tribe's tile
     * in the colony screen, {@code claimTile}): Enter and Escape leave,
     * nothing paid, the land still the tribe's (before: Enter paid,
     * 1000 -&gt; 600, prep A2); Down Enter pays the price; with too little
     * gold the pay row is greyed and Enter leaves (before: the bar sat on
     * the greyed row and Enter did nothing).
     */
    public void testTheClaimThroughTheController() throws Exception {
        for (String keys : new String[] { "ENTER", "ESC", "DOWN ENTER", "DOWN DOWN ENTER" }) {
            final Rig r = rig();
            final Tile t = incaLand(r, 1000);
            final Colony colony = r.game.getMap().getTile(8, 8).getColony();
            final Player inca = t.getOwner();
            final int price = r.dutch.getLandPrice(t);
            assertTrue(price > 0);
            r.keys.press(keys.split(" "));
            final boolean ok = r.igc().claimTile(t, colony);
            assertEquals(keys + " " + r.api.log, List.of(ClassicNativeLand.SECTION), r.asked());
            switch (keys) {
            case "ENTER": case "ESC":
                assertFalse(keys, ok);
                assertEquals(keys, 1000, r.dutch.getGold());
                assertSame(keys, inca, t.getOwner());
                assertTrue(keys + " no server call", r.api.log.isEmpty());
                break;
            case "DOWN ENTER":
                assertTrue(keys, ok);
                assertEquals(keys, 1000 - price, r.dutch.getGold());
                assertSame(keys, r.dutch, t.getOwner());
                break;
            default:
                assertTrue(keys, ok);
                assertEquals("taken, not paid", 1000, r.dutch.getGold());
                assertSame(keys, r.dutch, t.getOwner());
                break;
            }
        }
        final Rig r = rig();
        final Tile t = incaLand(r, 10);
        final Colony colony = r.game.getMap().getTile(8, 8).getColony();
        r.keys.press("ENTER");
        assertFalse(r.igc().claimTile(t, colony));
        final ClassicAdvisorBox.Request box = r.keys.boxes.get(0);
        assertFalse("pay greyed", box.enabled(ClassicNativeLand.PAY_ROW));
        assertEquals(10, r.dutch.getGold());
        assertNotSame(r.dutch, t.getOwner());
    }


    // A3: U in a colony

    /** What a fake cargo controller was asked, and its box answers. */
    private static final class FakeCargo implements ClassicUnload.Cargo {
        final List<String> calls = new ArrayList<>();
        final List<ClassicAdvisorBox.Request> boxes = new ArrayList<>();
        int answer = ClassicAdvisorBox.Bar.DISMISSED;

        @Override
        public int ask(ClassicAdvisorBox.Request r) {
            this.boxes.add(r);
            this.calls.add("ask " + r.id);
            return this.answer;
        }

        @Override
        public boolean unload(Goods goods) {
            this.calls.add("unload " + goods.getAmount() + " " + goods.getType().getSuffix());
            return true;
        }

        @Override
        public boolean dump(Goods goods) {
            this.calls.add("dump " + goods.getAmount() + " " + goods.getType().getSuffix());
            return true;
        }

        @Override
        public boolean land(Unit unit) {
            this.calls.add("land " + unit.getType().getSuffix());
            return true;
        }
    }

    /**
     * What spoils is FreeCol's rule (storable goods but food, over the
     * warehouse); U's hold is the most valuable at Amsterdam's bid
     * (manual p. 23), the first of equals.
     */
    public void testOverflowAndTheMostValuableHold() {
        final Game game = getStandardGame();
        game.changeMap(getCoastTestMap(plains, true));
        final Colony colony = createStandardColony(1, 9, 8);
        final int cap = colony.getWarehouseCapacity();
        colony.addGoods(muskets, cap - 20);
        assertEquals(80, ClassicUnload.overflow(colony, muskets, 100));
        assertEquals(0, ClassicUnload.overflow(colony, muskets, 20));
        assertEquals("food never spoils", 0, ClassicUnload.overflow(colony, food, 1000));
        final Player dutch = colony.getOwner();
        final Unit ship = new ServerUnit(game, colony.getTile(), dutch, merchantman);
        ship.addGoods(tools, 100);
        ship.addGoods(silver, 30);
        ship.addGoods(furs, 150);
        final net.sf.freecol.common.model.Market m = dutch.getMarket();
        Goods best = null;
        for (Goods g : ClassicUnload.holds(ship)) {
            if (best == null || m.getSalePrice(g.getType(), g.getAmount())
                > m.getSalePrice(best.getType(), best.getAmount())) best = g;
        }
        final Goods got = ClassicUnload.mostValuable(ship, m);
        assertSame(best.getType(), got.getType());
        assertEquals(best.getAmount(), got.getAmount());
        assertTrue("one hold at most", got.getAmount() <= 100);
        assertEquals(4, ClassicUnload.holds(ship).size());
        assertNull(ClassicUnload.mostValuable(new ServerUnit(game, colony.getTile(), dutch,
                                                             merchantman), m));
    }

    /**
     * @WAREHOUSEFULL (GAME.TXT 1168; manual:115): asked once with the
     * colony, 80 stored, «Musketen», capacity 100 and 100 unloaded; the
     * bar and Escape on «Dann eben nicht.», which keep the hold aboard;
     * «Ladet die Ware trotzdem ab.» unloads; a hold that fits and food
     * are unloaded without a box; with no goods aboard the units go
     * ashore.
     */
    public void testUnloadOneHold() {
        final Game game = getStandardGame();
        game.changeMap(getCoastTestMap(plains, true));
        final Colony colony = createStandardColony(1, 9, 8);
        final int cap = colony.getWarehouseCapacity();
        colony.addGoods(muskets, cap - 20);
        final Player dutch = colony.getOwner();
        final Unit ship = new ServerUnit(game, colony.getTile(), dutch, merchantman);
        ship.addGoods(muskets, 100);
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        final FakeCargo c = new FakeCargo();
        ClassicUnload.unloadOne(ship, colony, dutch.getMarket(), t, "Base", c);
        assertEquals(List.of("ask " + ClassicUnload.FULL_SECTION), c.calls);
        final ClassicAdvisorBox.Request r = c.boxes.get(0);
        assertEquals(2, r.rows.size());
        assertEquals(0, r.defaultRow);
        assertEquals("Escape keeps", ClassicUnload.KEEP_ROW, r.escapeAnswer());
        if (t != null) {
            final String s = r.plainText();
            assertTrue(s, s.contains(colony.getName()));
            assertTrue(s, s.contains("bereits " + (cap - 20) + " Einheiten Musketen"));
            assertTrue(s, s.contains("nur " + cap + "."));
            assertTrue(s, s.contains("Wenn wir 100 Einheiten"));
            assertEquals("Dann eben nicht.", ClassicAdvisorBox.plain(r.rows.get(0)));
            assertEquals("MSS5.SS.000", r.portrait.sprite);
        }
        c.calls.clear();
        c.answer = ClassicUnload.ANYWAY_ROW;
        ClassicUnload.unloadOne(ship, colony, dutch.getMarket(), t, "Base", c);
        assertEquals(List.of("ask " + ClassicUnload.FULL_SECTION, "unload 100 muskets"), c.calls);
        // A hold that fits: no box.
        final Unit wagon = new ServerUnit(game, colony.getTile(), dutch, wagonTrain);
        wagon.addGoods(tools, 50);
        c.calls.clear();
        ClassicUnload.unloadOne(wagon, colony, dutch.getMarket(), t, "Base", c);
        assertEquals(List.of("unload 50 tools"), c.calls);
        final Unit foodShip = new ServerUnit(game, colony.getTile(), dutch, merchantman);
        foodShip.addGoods(food, 100);
        colony.addGoods(food, cap * 3);
        c.calls.clear();
        ClassicUnload.unloadOne(foodShip, colony, dutch.getMarket(), t, "Base", c);
        assertEquals(List.of("unload 100 food"), c.calls);
        final Unit carrying = new ServerUnit(game, colony.getTile(), dutch, merchantman);
        new ServerUnit(game, carrying, dutch, colonist);
        c.calls.clear();
        ClassicUnload.unloadOne(carrying, colony, dutch.getMarket(), t, "Base", c);
        assertEquals(List.of("land freeColonist"), c.calls);
    }

    /** A ship of ours in a colony at (9,8) with 80 muskets stored and 100 aboard. */
    private Unit fullWarehouse(Rig r) {
        final Colony colony = createStandardColony(1, 9, 8);
        colony.addGoods(muskets, colony.getWarehouseCapacity() - 20);
        final Unit ship = new ServerUnit(r.game, colony.getTile(), r.dutch, merchantman);
        ship.addGoods(muskets, 100);
        r.active = ship;
        return ship;
    }

    /**
     * U (BEFEHLE row 11) through the Classic UI and FreeCol's controller:
     * into a full warehouse Enter and Escape keep the 100 muskets aboard
     * (before: 180 stored, 80 thrown away at the turn's end, prep A3);
     * Down Enter unloads them; a ship with two goods unloads one hold per
     * press, the most valuable first, then the other, then its passenger.
     */
    public void testUThroughTheController() throws Exception {
        for (String keys : new String[] { "ENTER", "ESC", "DOWN ENTER" }) {
            final Rig r = rig();
            final Unit ship = fullWarehouse(r);
            final Colony colony = ship.getTile().getColony();
            final int stored = colony.getGoodsCount(muskets);
            r.keys.press(keys.split(" "));
            r.unloadKey();
            assertEquals(keys, List.of(ClassicUnload.FULL_SECTION), r.asked());
            if ("DOWN ENTER".equals(keys)) {
                assertEquals(0, ship.getGoodsCount(muskets));
                assertEquals(stored + 100, colony.getGoodsCount(muskets));
            } else {
                assertEquals(keys, 100, ship.getGoodsCount(muskets));
                assertEquals(keys, stored, colony.getGoodsCount(muskets));
                assertTrue(keys + " no server call", r.api.log.isEmpty());
                assertEquals(keys + " no move spent", ship.getInitialMovesLeft(),
                             ship.getMovesLeft());
            }
        }
        final Rig r = rig();
        final Colony colony = createStandardColony(1, 9, 8);
        final Unit ship = new ServerUnit(r.game, colony.getTile(), r.dutch, merchantman);
        ship.addGoods(furs, 100);
        ship.addGoods(tools, 60);
        final Unit passenger = new ServerUnit(r.game, ship, r.dutch, colonist);
        r.active = ship;
        final GoodsType first = ClassicUnload.mostValuable(ship, r.dutch.getMarket()).getType();
        final GoodsType second = (first == furs) ? tools : furs;
        r.unloadKey();
        assertEquals(0, ship.getGoodsCount(first));
        assertTrue(ship.getGoodsCount(second) > 0);
        assertSame(ship, passenger.getLocation());
        r.unloadKey();
        assertFalse(ship.hasGoodsCargo());
        assertSame("the passenger last", ship, passenger.getLocation());
        r.unloadKey();
        assertNotSame(ship, passenger.getLocation());
        assertSame(colony.getTile(), passenger.getTile());
        assertTrue("no box: everything fitted", r.keys.boxes.isEmpty());
        assertEquals(100, colony.getGoodsCount(furs));
        assertEquals(60, colony.getGoodsCount(tools));
    }


    // A4: O at sea

    /**
     * O (BEFEHLE row 18) at sea through the Classic UI and FreeCol's
     * controller: @OVERBOARD's header, a row per hold «100 Felle», «50
     * Werkzeuge», «Nichts» last, the bar on the first hold, Escape on
     * «Nichts»; the chosen hold goes overboard, the other and the
     * passenger stay; «Nichts» and Escape keep everything (before: every
     * hold went overboard without a list, prep A4).
     */
    public void testOAtSea() throws Exception {
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        for (String keys : new String[] { "ESC", "DOWN DOWN ENTER", "ENTER", "DOWN ENTER" }) {
            final Rig r = rig();
            final Unit ship = new ServerUnit(r.game, r.game.getMap().getTile(14, 8), r.dutch,
                                             merchantman);
            ship.addGoods(furs, 100);
            ship.addGoods(tools, 50);
            final Unit passenger = new ServerUnit(r.game, ship, r.dutch, colonist);
            r.active = ship;
            final List<Goods> holds = ClassicUnload.holds(ship);
            r.keys.press(keys.split(" "));
            r.unloadKey();
            assertEquals(keys, List.of(ClassicUnload.OVERBOARD_SECTION), r.asked());
            final ClassicAdvisorBox.Request box = r.keys.boxes.get(0);
            assertEquals(3, box.rows.size());
            assertEquals(0, box.defaultRow);
            assertEquals("Escape: Nichts", 2, box.escapeAnswer());
            assertTrue(box.portrait.sprite == null);
            final Set<String> rows = new HashSet<>();
            for (String s : box.plainRows()) rows.add(s);
            if (t != null) {
                assertEquals(Set.of("100 Felle", "50 Werkzeuge", "Nichts"), rows);
                assertEquals("Nichts", box.plainRows()[2]);
                assertTrue(box.plainText(), box.plainText().startsWith("Welche Ware"));
                assertEquals(ClassicUnload.holdRow(t, holds.get(0)), box.plainRows()[0]);
            }
            assertSame(keys, ship, passenger.getLocation());
            if ("ENTER".equals(keys) || "DOWN ENTER".equals(keys)) {
                final Goods gone = holds.get(("ENTER".equals(keys)) ? 0 : 1);
                final Goods kept = holds.get(("ENTER".equals(keys)) ? 1 : 0);
                assertEquals(keys, 0, ship.getGoodsCount(gone.getType()));
                assertEquals(keys, kept.getAmount(), ship.getGoodsCount(kept.getType()));
            } else {
                assertEquals(keys, 100, ship.getGoodsCount(furs));
                assertEquals(keys, 50, ship.getGoodsCount(tools));
                assertTrue(keys, r.api.log.isEmpty());
            }
        }
    }


    // A5: Shift-D

    /**
     * Shift-D's refusal (GAME.TXT @DISBANDSHIP): a ship outside a colony
     * with units aboard, with the admiral; none for an empty ship, a ship
     * in a colony or a land unit.
     */
    public void testTheDisbandRefusal() {
        final Game game = getStandardGame();
        game.changeMap(getCoastTestMap(plains, true));
        final Colony colony = createStandardColony(1, 9, 8);
        final Player dutch = colony.getOwner();
        final Unit atSea = new ServerUnit(game, game.getMap().getTile(14, 8), dutch, merchantman);
        assertNull("empty", ClassicIllegalMoves.disbandRefusal(atSea));
        new ServerUnit(game, atSea, dutch, colonist);
        final ClassicIllegalMoves.Verdict v = ClassicIllegalMoves.disbandRefusal(atSea);
        assertEquals("DISBANDSHIP", v.section);
        assertEquals(ClassicNotices.Who.ADMIRAL, v.who);
        final Unit inPort = new ServerUnit(game, colony.getTile(), dutch, merchantman);
        new ServerUnit(game, inPort, dutch, colonist);
        assertNull("in a colony", ClassicIllegalMoves.disbandRefusal(inPort));
        assertNull("a land unit", ClassicIllegalMoves.disbandRefusal(
            new ServerUnit(game, game.getMap().getTile(5, 5), dutch, colonist)));
        assertNull(ClassicIllegalMoves.disbandRefusal(null));
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        final ClassicAdvisorBox.Request r = ClassicIllegalMoves.disbandRequest(t, atSea, "x");
        assertEquals(2, r.rows.size());
        assertEquals("the bar on Ja (no @default)", 0, r.defaultRow);
        assertEquals("Escape: Nein", 1, r.escapeAnswer());
        if (t != null) {
            assertEquals("Handelsschiff wirklich auflösen?", r.plainText());
            assertEquals("Ja", r.plainRows()[0]);
            assertEquals("Nein", r.plainRows()[1]);
            final ClassicAdvisorBox.Request no = ClassicIllegalMoves.request(t, v, 0L, "x");
            assertTrue(no.isNotice());
            assertTrue(no.plainText(), no.plainText().startsWith("Wir können ein Schiff auf hoher See"));
            assertEquals("MSS0.SS.000", no.portrait.sprite);
        }
    }

    /**
     * Shift-D through FreeCol's controller ({@code disbandUnit}, as
     * {@code DisbandUnitAction}): at sea with two colonists aboard
     * @DISBANDSHIP and nothing else, the ship and both colonists alive
     * (before: all three gone, prep A5); an empty ship at sea asks
     * @SUREDISBAND, Enter («Ja») disbands, Escape keeps it.
     */
    public void testShiftDAtSea() throws Exception {
        final Rig r = rig();
        final Unit ship = new ServerUnit(r.game, r.game.getMap().getTile(14, 8), r.dutch,
                                         merchantman);
        final Unit p1 = new ServerUnit(r.game, ship, r.dutch, colonist);
        final Unit p2 = new ServerUnit(r.game, ship, r.dutch, colonist);
        r.active = ship;
        r.keys.press("ENTER");
        assertFalse(r.igc().disbandUnit(ship));
        assertEquals(List.of("DISBANDSHIP"), r.asked());
        assertTrue(r.keys.boxes.get(0).isNotice());
        assertFalse(ship.isDisposed() || p1.isDisposed() || p2.isDisposed());
        assertTrue("no server call", r.api.log.isEmpty());
        assertEquals("no move spent", ship.getInitialMovesLeft(), ship.getMovesLeft());
        for (String keys : new String[] { "ESC", "ENTER" }) {
            final Rig e = rig();
            final Unit empty = new ServerUnit(e.game, e.game.getMap().getTile(14, 8), e.dutch,
                                              merchantman);
            new ServerUnit(e.game, e.game.getMap().getTile(3, 3), e.dutch, colonist);
            e.active = empty;
            e.keys.press(keys);
            final boolean gone = e.igc().disbandUnit(empty);
            assertEquals(keys, List.of(ClassicIllegalMoves.SURE_DISBAND), e.asked());
            assertEquals(keys, "ENTER".equals(keys), gone);
            assertEquals(keys, "ENTER".equals(keys), empty.isDisposed());
        }
    }

    /**
     * Shift-D on a ship in our colony with a colonist and 100 furs
     * aboard: «Ja» puts the colonist ashore and the furs into the
     * warehouse, then the empty ship is disbanded (before: the colonist
     * and the furs died with it, prep A5); with a full warehouse
     * «Dann eben nicht.» keeps the muskets aboard and the ship.
     */
    public void testShiftDInAColony() throws Exception {
        final Rig r = rig();
        final Colony colony = createStandardColony(1, 9, 8);
        final Unit ship = new ServerUnit(r.game, colony.getTile(), r.dutch, merchantman);
        final Unit p = new ServerUnit(r.game, ship, r.dutch, colonist);
        ship.addGoods(furs, 100);
        r.active = ship;
        r.keys.press("ENTER");
        assertTrue(r.igc().disbandUnit(ship));
        assertEquals(List.of(ClassicIllegalMoves.SURE_DISBAND), r.asked());
        assertTrue(ship.isDisposed());
        assertFalse("the colonist lives", p.isDisposed());
        assertSame(colony.getTile(), p.getTile());
        assertEquals(100, colony.getGoodsCount(furs));
        final Rig f = rig();
        final Unit full = fullWarehouse(f);
        f.keys.press("ENTER");    // @SUREDISBAND «Ja», then @WAREHOUSEFULL «Dann eben nicht.»
        assertFalse(f.igc().disbandUnit(full));
        assertEquals(List.of(ClassicIllegalMoves.SURE_DISBAND, ClassicUnload.FULL_SECTION),
                     f.asked());
        assertFalse(full.isDisposed());
        assertEquals(100, full.getGoodsCount(muskets));
    }


    // A1's equip path

    /**
     * FreeCol's arrears question (its equip path in Europe) is the
     * original's @KISSUP: the bar and Escape on the refusal, «N zahlen.»
     * pays (before: FreeCol's words with the bar on "pay"); its
     * "too little gold" is @KISSSORRY with our gold.
     */
    public void testFreeColsArrearsQuestionIsKissUp() {
        final ClassicGUISeamTest.KeyPrompter keys = new ClassicGUISeamTest.KeyPrompter();
        final ClassicGUI gui = new ClassicGUI(null);
        gui.prompter = keys;
        final StringTemplate q = StringTemplate.template("payArrears.text")
            .addAmount("%amount%", 750);
        keys.press("ENTER");
        assertFalse(gui.modalConfirmDialog(null, q, (javax.swing.ImageIcon)null, "ok", "cancel", true));
        keys.press("ESC");
        assertFalse(gui.modalConfirmDialog(null, q, (javax.swing.ImageIcon)null, "ok", "cancel", true));
        keys.press("DOWN", "ENTER");
        assertTrue(gui.modalConfirmDialog(null, q, (javax.swing.ImageIcon)null, "ok", "cancel", true));
        assertEquals(3, keys.boxes.size());
        final ClassicAdvisorBox.Request r = keys.boxes.get(0);
        assertEquals(ClassicBoycott.KISS_SECTION, r.id);
        assertEquals(0, r.defaultRow);
        assertEquals(0, r.escapeAnswer());
        if (ClassicText.load(ClassicPackFiles.runtime()) != null) {
            assertEquals("750$ zahlen.", r.plainRows()[1]);
        }
        assertEquals(750, ClassicGUI.amountOf(q));
        assertEquals(0, ClassicGUI.amountOf(null));
        assertEquals("FreeCol's words name the unit", Messages.message("disbandUnit.yes"),
                     ClassicIllegalMoves.disbandRequest(null, null, "x").plainRows()[0]);
    }
}
