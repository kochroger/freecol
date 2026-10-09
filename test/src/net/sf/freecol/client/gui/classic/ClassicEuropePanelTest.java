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
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

import javax.swing.JComponent;
import javax.swing.KeyStroke;

import net.sf.freecol.client.ClientOptions;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.control.InGameController;
import net.sf.freecol.client.gui.GUI;
import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.common.model.Direction;
import net.sf.freecol.common.model.Europe;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Location;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Role;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.common.networking.ChangeStateMessage;
import net.sf.freecol.common.networking.Connection;
import net.sf.freecol.common.networking.DisembarkMessage;
import net.sf.freecol.common.networking.EmbarkMessage;
import net.sf.freecol.common.networking.EmigrateUnitMessage;
import net.sf.freecol.common.networking.EquipForRoleMessage;
import net.sf.freecol.common.networking.LoadGoodsMessage;
import net.sf.freecol.common.networking.MoveToMessage;
import net.sf.freecol.common.networking.ServerAPI;
import net.sf.freecol.common.networking.TrainUnitInEuropeMessage;
import net.sf.freecol.common.networking.UnloadGoodsMessage;
import net.sf.freecol.common.resources.ImageCache;
import net.sf.freecol.server.FreeColServer;
import net.sf.freecol.server.ServerTestHelper;
import net.sf.freecol.common.networking.ChangeSet;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * The Europe screen made usable (part N1; Roger 2026-10-09: "In Europa
 * kann ich gar nichts tun ... das Schiff kommt nie an"): the original's
 * places (clip008 {@code 01-europe.md}, clip 020), the first ship
 * selected, its holds, and every action done with the mouse as the
 * original does it, on the real {@link ClassicEuropePanel} with FreeCol's
 * real client controller and a real server game (the client's server API
 * hands each request to the server's own handler, client and server share
 * the game), by synthetic AWT mouse events on the screen's places.
 */
public class ClassicEuropePanelTest extends FreeColTestCase {

    private static final UnitType merchantman = spec().getUnitType("model.unit.merchantman");
    private static final UnitType caravel = spec().getUnitType("model.unit.caravel");
    private static final UnitType galleon = spec().getUnitType("model.unit.galleon");
    private static final UnitType colonist = spec().getUnitType("model.unit.freeColonist");
    private static final UnitType veteran = spec().getUnitType("model.unit.veteranSoldier");
    private static final UnitType artillery = spec().getUnitType("model.unit.artillery");
    private static final GoodsType tools = spec().getGoodsType("model.goods.tools");
    private static final GoodsType muskets = spec().getGoodsType("model.goods.muskets");
    private static final GoodsType horses = spec().getGoodsType("model.goods.horses");
    private static final GoodsType furs = spec().getGoodsType("model.goods.furs");
    private static final Role pioneer = spec().getRole("model.role.pioneer");
    private static final Role soldier = spec().getRole("model.role.soldier");

    /** The panel's size in the tests: scale 3, no letterbox. */
    private static final int W = ClassicEuropePanel.VW * 3, H = ClassicEuropePanel.VH * 3;


    // Geometry

    /**
     * The original's places (V clip008 §2.4-2.8, clip 020 #28000): ships
     * in port from (146,146) 18 apart, hold k at (147 + 12k, 165), the
     * docks from (233,138) 17 apart, three per row, the next row at y 161,
     * ships under way at box x + 1 with their passengers 17 apart (73, 90,
     * 107, 124 in box 2), the market slots 19 wide from y 179, the exit at
     * x 305.
     */
    public void testThePlacesAreTheOriginals() {
        assertEquals(new Rectangle(146, 146, 16, 16), ClassicEuropePanel.shipCell(0, 1));
        assertEquals(new Rectangle(164, 146, 16, 16), ClassicEuropePanel.shipCell(1, 2));
        assertEquals(200, ClassicEuropePanel.shipCell(3, 4).x);
        final Rectangle last = ClassicEuropePanel.shipCell(5, 6);
        assertTrue("six ships stay in box 3", last.x + 16 <= 223);
        assertEquals(new Rectangle(147, 165, 10, 12), ClassicEuropePanel.holdCell(0));
        assertEquals(new Rectangle(207, 165, 10, 12), ClassicEuropePanel.holdCell(5));
        assertEquals(new Rectangle(145, 163, 12, 14), ClassicEuropePanel.holdTarget(0));
        assertEquals(new Rectangle(233, 138, 16, 16), ClassicEuropePanel.dockCell(0, 4));
        assertEquals(250, ClassicEuropePanel.dockCell(1, 4).x);
        assertEquals(267, ClassicEuropePanel.dockCell(2, 4).x);
        assertEquals(new Rectangle(233, 161, 16, 16), ClassicEuropePanel.dockCell(3, 4));
        assertTrue("ten on the quay", ClassicEuropePanel.dockCell(4, 10).x + 16 <= 320);
        final int[] box2 = { 73, 90, 107, 124 };
        for (int i = 0; i < box2.length; i++) {
            assertEquals(new Rectangle(box2[i], 146, 16, 16),
                ClassicEuropePanel.sailingCell(ClassicEuropePanel.BOX2, i, box2.length));
        }
        assertEquals(2, ClassicEuropePanel.sailingCell(ClassicEuropePanel.BOX1, 0, 1).x);
        assertEquals(new Rectangle(266, 179, 19, 21), ClassicEuropePanel.marketSlot(14));
        assertEquals(305, ClassicEuropePanel.EXIT.x);
        assertEquals(new Rectangle(72, 118, 70, 60), ClassicEuropePanel.BOX2);
    }

    /**
     * The boxes' captions (V clip008 §2.4, 0 px): «Bald erwartet» at x 12,
     * «Ziel:» at 99 over «Neuholland» at 89, «Einladen:» at 166 over
     * «Handelsschiff» at 159, «Keine Schiffe im Hafen» at 143; the words
     * from LABELS.TXT and NAMES.TXT («Einladen:» 1 px right of the clip,
     * {@link ClassicEuropePanel#captionX}).
     */
    public void testCaptionsWithThePack() {
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final ClassicText t = ClassicText.load(pack);
        final ClassicFont tiny = (pack == null) ? null : pack.font(ClassicFont.TINY);
        if (t == null || tiny == null) {
            System.err.println(getClass().getSimpleName() + ": captions skipped, no pack");
            return;
        }
        final Game game = getStandardGame();
        final ServerPlayer dutch = getServerPlayer(game, "model.nation.dutch");
        final Unit ship = new ServerUnit(game, dutch.getEurope(), dutch, merchantman);
        final String[][] c = ClassicEuropePanel.captions(t, dutch, ship);
        assertEquals("Bald erwartet", c[0][0]);
        assertEquals("Ziel:", c[1][0]);
        assertEquals("Neuholland", c[1][1]);
        assertEquals("Einladen:", c[2][0]);
        assertEquals("Handelsschiff", c[2][1]);
        assertEquals("Keine Schiffe im Hafen",
                     ClassicEuropePanel.captions(t, dutch, null)[2][0]);
        final Object[][] at = {
            { "Bald erwartet", ClassicEuropePanel.BOX1, 12 },
            { "Ziel:", ClassicEuropePanel.BOX2, 99 },
            { "Neuholland", ClassicEuropePanel.BOX2, 89 },
            { "Einladen:", ClassicEuropePanel.BOX3, 167 },        // the clip: 166
            { "Handelsschiff", ClassicEuropePanel.BOX3, 159 },
            { "Keine Schiffe im Hafen", ClassicEuropePanel.BOX3, 143 },
        };
        for (Object[] a : at) {
            assertEquals((String)a[0], ((Integer)a[2]).intValue(),
                ClassicEuropePanel.captionX((Rectangle)a[1], tiny.stringWidth((String)a[0])));
        }
    }

    /**
     * The holds: the colonists aboard first (each in its own hold), then
     * the goods in holds of at most 100, then the empty holds, then crates
     * for the holds the ship lacks (the merchantman's 4 of 6: crates at 4
     * and 5, V clip008 §2.5); six crates without a ship.
     */
    public void testHolds() {
        final Game game = getStandardGame();
        final ServerPlayer dutch = getServerPlayer(game, "model.nation.dutch");
        final Unit ship = new ServerUnit(game, dutch.getEurope(), dutch, merchantman);
        assertEquals("[empty, empty, empty, empty, crate, crate]",
                     ClassicEuropePanel.holds(ship).toString());
        new ServerUnit(game, ship, dutch, colonist, pioneer);
        new ServerUnit(game, ship, dutch, veteran, soldier);
        ship.addGoods(tools, 150);
        assertEquals("[freeColonist, veteranSoldier, tools:100, tools:50, crate, crate]",
                     ClassicEuropePanel.holds(ship).toString());
        assertEquals("[crate, crate, crate, crate, crate, crate]",
                     ClassicEuropePanel.holds(null).toString());
        final Unit big = new ServerUnit(game, dutch.getEurope(), dutch, galleon);
        assertEquals(6, ClassicEuropePanel.holds(big).stream()
                     .filter(ClassicEuropePanel.Hold::empty).count());
    }


    // The screen with FreeCol's controller and a server

    /** The screen's boxes: noted, answered by {@link #answers} in turn (none left: Escape). */
    private static final class Boxes implements ClassicEuropePanel.Boxes {

        final List<String> asked = new ArrayList<>();
        final List<ClassicAdvisorBox.Request> requests = new ArrayList<>();
        final List<ToIntFunction<ClassicAdvisorBox.Request>> answers = new ArrayList<>();
        final List<String> cannotPay = new ArrayList<>();

        @Override
        public int ask(ClassicAdvisorBox.Request r) {
            this.asked.add(r.id);
            this.requests.add(r);
            if (this.answers.isEmpty()) return r.escapeAnswer();
            return this.answers.remove(0).applyAsInt(r);
        }

        @Override
        public void cannotPay(GoodsType type) {
            this.cannotPay.add(type.getSuffix());
        }

        /** Answer the next box with a row. */
        Boxes row(int row) {
            this.answers.add(r -> row);
            return this;
        }

        /** Answer the next box (an amount box) by typing, then Enter. */
        Boxes typed(String keys) {
            this.answers.add(r -> {
                    final ClassicAdvisorBox.Bar bar = new ClassicAdvisorBox.Bar(r);
                    for (char k : keys.toCharArray()) {
                        if (k == '<') {
                            bar.backspace();
                        } else {
                            bar.type(k, null);
                        }
                    }
                    return bar.enter();
                });
            return this;
        }
    }

    /**
     * The client's server API: each request goes to the server's own
     * handler for our player, as over the connection; refusals are noted.
     */
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
        public boolean loadGoods(Location loc, GoodsType type, int amount, Unit carrier) {
            return handle("load " + type.getSuffix() + " " + amount,
                new LoadGoodsMessage(loc, type, amount, carrier)
                    .serverHandler(this.server, this.player));
        }

        @Override
        public boolean unloadGoods(GoodsType type, int amount, Unit carrier) {
            return handle("unload " + type.getSuffix() + " " + amount,
                new UnloadGoodsMessage(type, amount, carrier)
                    .serverHandler(this.server, this.player));
        }

        @Override
        public boolean embark(Unit unit, Unit carrier, Direction direction) {
            return handle("embark " + unit.getId(), new EmbarkMessage(unit, carrier, direction)
                .serverHandler(this.server, this.player));
        }

        @Override
        public boolean disembark(Unit unit) {
            return handle("disembark " + unit.getId(), new DisembarkMessage(unit)
                .serverHandler(this.server, this.player));
        }

        @Override
        public boolean emigrate(int slot) {
            return handle("emigrate " + slot, new EmigrateUnitMessage(slot)
                .serverHandler(this.server, this.player));
        }

        @Override
        public boolean trainUnitInEurope(UnitType type) {
            return handle("train " + type.getSuffix(), new TrainUnitInEuropeMessage(type)
                .serverHandler(this.server, this.player));
        }

        @Override
        public boolean moveTo(Unit unit, Location destination) {
            return handle("moveTo " + unit.getId(), new MoveToMessage(unit, destination)
                .serverHandler(this.server, this.player));
        }

        @Override
        public boolean changeState(Unit unit, Unit.UnitState state) {
            return handle("state " + unit.getId() + " " + state,
                new ChangeStateMessage(unit, state).serverHandler(this.server, this.player));
        }

        @Override
        public boolean equipUnitForRole(Unit unit, Role role, int roleCount) {
            return handle("equip " + unit.getId() + " " + role.getSuffix(),
                new EquipForRoleMessage(unit, role, roleCount)
                    .serverHandler(this.server, this.player));
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

    /**
     * A client of the server's game with FreeCol's real controller, our
     * player's turn.  FreeCol's constructor would start a game of its own,
     * so the object is made without it (as ClassicPacificTest's client);
     * its GUI plays no sounds, its options are all off.
     */
    private static FreeColClient client(Game game, ServerPlayer me, Loopback api)
        throws Exception {
        final Class<?> uc = Class.forName("sun.misc.Unsafe");
        final Field theUnsafe = uc.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        final FreeColClient fcc = (FreeColClient)uc
            .getMethod("allocateInstance", Class.class)
            .invoke(theUnsafe.get(null), FreeColClient.class);
        set(fcc, "gui", new GUI(fcc) {
                @Override
                public void playSound(String sound) {}
            });
        set(fcc, "serverAPI", api);
        set(fcc, "clientOptions", new ClientOptions() {
                @Override
                public boolean getBoolean(String id) {
                    return false;
                }
            });
        set(fcc, "inGameController", new InGameController(fcc));
        fcc.setGame(game);
        fcc.setMyPlayer(me);
        set(fcc, "inGame", Boolean.TRUE);
        game.setCurrentPlayer(me);
        return fcc;
    }

    /** Any picture for every key: the screen draws without the art. */
    private static ImageLibrary library() {
        return new ImageLibrary(1f, new ImageCache() {
                @Override
                public BufferedImage getScaledImage(String key, float scale,
                                                    boolean grayscale) {
                    return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                }

                @Override
                public BufferedImage getSizedImage(String key, java.awt.Dimension size,
                                                   boolean grayscale, int seed) {
                    return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                }
            });
    }

    /** What a test works with: Roger's 1496 in port, the screen, its boxes. */
    private static final class Scene {
        Game game;
        ServerPlayer dutch;
        Europe europe;
        Unit ship, pioneerUnit, soldierUnit;
        Loopback api;
        FreeColClient fcc;
        Boxes boxes;
        ClassicEuropePanel panel;
        final List<String> events = new ArrayList<>();
    }

    /**
     * Roger's game of 2026-10-09 0953, 1496 (the N1 prep §1): the
     * merchantman in port, a pioneer (100 tools) and a veteran soldier
     * aboard, both "S", no goods, an empty dock, 1000 gold; the screen as
     * the arrival opens it.
     */
    private Scene rogers1496() throws Exception {
        final Scene s = new Scene();
        s.game = ServerTestHelper.startServerGame(getTestMap(true));
        s.dutch = getServerPlayer(s.game, "model.nation.dutch");
        s.dutch.setGold(1000);
        s.europe = s.dutch.getEurope();
        s.ship = new ServerUnit(s.game, s.europe, s.dutch, merchantman);
        s.pioneerUnit = new ServerUnit(s.game, s.ship, s.dutch, colonist, pioneer);
        s.pioneerUnit.setRoleCount(pioneer.getMaximumCount());
        s.soldierUnit = new ServerUnit(s.game, s.ship, s.dutch, veteran, soldier);
        s.pioneerUnit.setState(Unit.UnitState.SENTRY);
        s.soldierUnit.setState(Unit.UnitState.SENTRY);
        s.api = new Loopback(ServerTestHelper.getServer(), s.dutch);
        s.fcc = client(s.game, s.dutch, s.api);
        s.boxes = new Boxes();
        s.panel = new ClassicEuropePanel(s.fcc, library(), s.europe,
            () -> s.events.add("closed"), u -> s.events.add("sailed " + u.getId()),
            s.boxes);
        s.panel.setSize(W, H);
        s.panel.chooser = (title, prompt, options) -> 0;
        return s;
    }

    /** A synthetic mouse event at a canvas point (scale 3), dispatched to the screen. */
    private static void mouse(ClassicEuropePanel p, int id, int vx, int vy, boolean shift) {
        int mods = (shift) ? InputEvent.SHIFT_DOWN_MASK : 0;
        if (id == MouseEvent.MOUSE_PRESSED || id == MouseEvent.MOUSE_DRAGGED) {
            mods |= InputEvent.BUTTON1_DOWN_MASK;
        }
        final int button = (id == MouseEvent.MOUSE_DRAGGED) ? MouseEvent.NOBUTTON
            : MouseEvent.BUTTON1;
        p.dispatchEvent(new MouseEvent(p, id, System.currentTimeMillis(), mods,
                                       vx * 3 + 1, vy * 3 + 1, 1, false, button));
    }

    /** Press, release at the same point. */
    private static void click(ClassicEuropePanel p, int vx, int vy) {
        mouse(p, MouseEvent.MOUSE_PRESSED, vx, vy, false);
        mouse(p, MouseEvent.MOUSE_RELEASED, vx, vy, false);
    }

    /** Press at a, drag over the way to b, release at b. */
    private static void drag(ClassicEuropePanel p, int ax, int ay, int bx, int by,
                             boolean shift) {
        mouse(p, MouseEvent.MOUSE_PRESSED, ax, ay, shift);
        for (int i = 1; i <= 4; i++) {
            mouse(p, MouseEvent.MOUSE_DRAGGED, ax + (bx - ax) * i / 4,
                  ay + (by - ay) * i / 4, shift);
        }
        mouse(p, MouseEvent.MOUSE_RELEASED, bx, by, shift);
    }

    /** The centre of a rectangle. */
    private static int cx(Rectangle r) {
        return r.x + r.width / 2;
    }

    private static int cy(Rectangle r) {
        return r.y + r.height / 2;
    }

    /** The market slot of a good. */
    private static Rectangle slot(GoodsType gt) {
        return ClassicEuropePanel.marketSlot(spec().getStorableGoodsTypeList().indexOf(gt));
    }

    /**
     * Roger's arrival (R1-R3 of the prep): the ship is selected at once, in
     * box 3 at (146,146), not in box 1; its holds show the pioneer and the
     * soldier; the screen paints; Europe's places answer the mouse.
     */
    public void testRogersArrivalShowsTheShipInPort() throws Exception {
        final Scene s = rogers1496();
        assertSame("selected at once (R1)", s.ship, s.panel.selectedShip());
        assertEquals(ClassicEuropePanel.Kind.SHIP, s.panel.hitAt(154, 154).kind);
        assertSame(s.ship, s.panel.hitAt(154, 154).unit);
        assertEquals("nothing in box 1 (R2)", ClassicEuropePanel.Kind.BOX1,
                     s.panel.hitAt(14, 130).kind);
        final ClassicEuropePanel.Hit h0 = s.panel.hitAt(152, 170);
        assertEquals(ClassicEuropePanel.Kind.HOLD, h0.kind);
        assertSame("the pioneer in hold 0 (R3)", s.pioneerUnit, h0.hold.unit);
        assertSame(s.soldierUnit, s.panel.hitAt(164, 170).hold.unit);
        assertTrue(s.panel.hitAt(200, 170).hold.crate);
        final BufferedImage img = s.panel.canvas();
        assertEquals("the green frame round the ship", ClassicEuropePanel.FRAME.getRGB(),
                     img.getRGB(145, 150));
        s.panel.paintTargets();
        assertTrue(s.events.isEmpty());
    }

    /**
     * Buying and selling as the original (clip008 §3.4, §3.5): a market
     * slot dropped on a hold buys a hold (gold - 100 x ask); with Shift on
     * the ship {@code @HOWMUCH4}, "50" typed buys 50; a click on a market
     * slot buys nothing (the cursor moves); a hold dropped anywhere on the
     * market row sells it; with Shift {@code @HOWMUCH5} opens with "100"
     * over "(0-50)" (V clip 020 #24920) and Enter on it sells all 50.
     */
    public void testBuyAndSellByDragging() throws Exception {
        final Scene s = rogers1496();
        final int ask = s.dutch.getMarket().getCostToBuy(tools);
        final Rectangle toolsSlot = slot(tools);
        click(s.panel, cx(toolsSlot), cy(toolsSlot));
        assertEquals(1000, s.dutch.getGold());
        assertEquals(spec().getStorableGoodsTypeList().indexOf(tools), s.panel.marketCursor());
        assertEquals(0, s.ship.getGoodsCount(tools));
        // Dropped on a hold of the ship (here hold 4, a crate: any hold, I).
        drag(s.panel, cx(toolsSlot), cy(toolsSlot), 196, 170, false);
        assertEquals(100, s.ship.getGoodsCount(tools));
        assertEquals(1000 - 100 * ask, s.dutch.getGold());
        assertTrue(s.boxes.asked.isEmpty());
        final ClassicEuropePanel.Hit h2 = s.panel.hitAt(176, 170);
        assertSame(tools, h2.hold.goods.getType());

        if (ClassicText.load(ClassicPackFiles.runtime()) != null) {
            final int gold = s.dutch.getGold();
            final int mAsk = s.dutch.getMarket().getCostToBuy(muskets);
            s.boxes.typed("50");
            drag(s.panel, cx(slot(muskets)), cy(slot(muskets)), 154, 154, true);
            assertEquals(List.of(ClassicTrade.BUY_SECTION), s.boxes.asked);
            assertEquals("100", s.boxes.requests.get(0).field.initial);
            assertEquals(50, s.ship.getGoodsCount(muskets));
            assertEquals(gold - 50 * mAsk, s.dutch.getGold());
            // The muskets' hold (grey: 50) dropped on the Rum slot, with
            // Shift: @HOWMUCH5 "(0-50)" with "100"; Enter sells all.
            s.boxes.asked.clear();
            s.boxes.requests.clear();
            final int gold2 = s.dutch.getGold();
            s.boxes.typed("");
            final int musketHold = holdOf(s.panel, muskets);
            drag(s.panel, cx(ClassicEuropePanel.holdCell(musketHold)), 170,
                 cx(ClassicEuropePanel.marketSlot(9)), 190, true);
            assertEquals(List.of(ClassicTrade.SELL_SECTION), s.boxes.asked);
            final ClassicAdvisorBox.Request r = s.boxes.requests.get(0);
            assertEquals("100", r.field.initial);
            assertTrue(r.plainText(), r.plainText().contains("(0-50)"));
            assertEquals(0, s.ship.getGoodsCount(muskets));
            assertTrue("sold", s.dutch.getGold() > gold2);
        }
        // The tools' hold dropped on the market: all 100 sold.
        final int gold3 = s.dutch.getGold();
        final int bid = s.dutch.getMarket().getPaidForSale(tools);
        drag(s.panel, cx(ClassicEuropePanel.holdCell(holdOf(s.panel, tools))), 170,
             cx(ClassicEuropePanel.marketSlot(0)), 190, false);
        assertEquals(0, s.ship.getGoodsCount(tools));
        assertTrue(s.dutch.getGold() >= gold3 + 100 * bid - 100 * bid / 10);
        assertTrue(s.dutch.getGold() > gold3);
        // A hold dropped back on itself or on the sky: nothing.
        s.ship.addGoods(furs, 100);
        final int fursHold = holdOf(s.panel, furs);
        final int gold4 = s.dutch.getGold();
        drag(s.panel, cx(ClassicEuropePanel.holdCell(fursHold)), 170, 100, 40, false);
        drag(s.panel, cx(ClassicEuropePanel.holdCell(fursHold)), 170, 100, 150, false);
        assertEquals(100, s.ship.getGoodsCount(furs));
        assertEquals(gold4, s.dutch.getGold());
    }

    /** @return The hold of the selected ship with a good. */
    private static int holdOf(ClassicEuropePanel p, GoodsType gt) {
        final List<ClassicEuropePanel.Hold> h = ClassicEuropePanel.holds(p.selectedShip());
        for (int k = 0; k < h.size(); k++) {
            if (h.get(k).goods != null && h.get(k).goods.getType() == gt) return k;
        }
        fail("no hold of " + gt);
        return -1;
    }

    /**
     * Colonists between ship and dock: the soldier's hold dropped on the
     * docks puts him on the dock (FreeCol's leaveShip; he keeps his "S");
     * dropped from the dock on the ship he boards again; a drop elsewhere
     * does nothing.
     */
    public void testColonistsLeaveAndBoardByDragging() throws Exception {
        final Scene s = rogers1496();
        drag(s.panel, 164, 170, 150, 40, false);                 // to the sky
        assertSame(s.ship, s.soldierUnit.getLocation());
        drag(s.panel, 164, 170, 270, 150, false);                // to the docks
        assertSame(s.europe, s.soldierUnit.getLocation());
        assertEquals(List.of(s.soldierUnit), s.panel.dockUnits());
        assertEquals(ClassicEuropePanel.Kind.DOCK, s.panel.hitAt(240, 145).kind);
        assertEquals("[freeColonist, empty, empty, empty, crate, crate]",
                     ClassicEuropePanel.holds(s.ship).toString());
        drag(s.panel, 240, 145, 100, 40, false);                 // nowhere
        assertSame(s.europe, s.soldierUnit.getLocation());
        drag(s.panel, 240, 145, 154, 154, false);                // onto the ship
        assertSame(s.ship, s.soldierUnit.getLocation());
        assertTrue(s.panel.dockUnits().isEmpty());
        drag(s.panel, 152, 170, 300, 170, false);                // the pioneer off
        assertSame(s.europe, s.pioneerUnit.getLocation());
        drag(s.panel, 240, 145, 190, 170, false);                // onto an empty hold
        assertSame(s.ship, s.pioneerUnit.getLocation());
    }

    /**
     * The dock's box {@code @EUROPEARM} (clip008 §3.3): a click on a
     * colonist asks it; «Nicht aufs nächste Schiff gehen.» takes his "S"
     * (he then stays when the ship sails), «An Bord des nächsten Schiffes
     * gehen.» gives it back; «Mit Musketen bewaffnen» makes a free
     * colonist a soldier for 50 x ask; Escape changes nothing.
     */
    public void testTheDockBox() throws Exception {
        final Scene s = rogers1496();
        final Unit c = new ServerUnit(s.game, s.europe, s.dutch, colonist);
        c.setState(Unit.UnitState.SENTRY);
        final List<ClassicEuropeOptions.ArmOption> rows = ClassicEuropeOptions.armOptions(c);
        assertEquals("[STAY, ARM, TOOLS, HORSES, MISSIONARY, NOTHING]", rows.toString());
        final int[] prices = ClassicEuropeOptions.armPrices(c, rows, s.dutch.getMarket());
        assertEquals(s.dutch.getMarket().getBidPrice(muskets, 50), prices[0]);
        assertEquals(s.dutch.getMarket().getBidPrice(tools, 100), prices[1]);
        assertEquals(s.dutch.getMarket().getBidPrice(horses, 50), prices[2]);
        assertEquals("[STAY, DISARM, TOOLS, HORSES, NOTHING]",
                     ClassicEuropeOptions.armOptions(s.soldierUnit).toString());
        assertEquals("[STAY, ARM, SELL_TOOLS, HORSES, NOTHING]",
                     ClassicEuropeOptions.armOptions(s.pioneerUnit).toString());
        final boolean texts = ClassicText.load(ClassicPackFiles.runtime()) != null;
        if (!texts) {
            System.err.println(getClass().getSimpleName() + ": the dock box skipped, no pack");
            return;
        }
        final Rectangle cell = ClassicEuropePanel.dockCell(0, 1);
        click(s.panel, cx(cell), cy(cell));                       // Escape
        assertEquals(List.of(ClassicEuropeOptions.ARM_SECTION), s.boxes.asked);
        final ClassicAdvisorBox.Request r = s.boxes.requests.get(0);
        assertEquals("Nicht aufs nächste Schiff gehen.",
                     ClassicAdvisorBox.plain(r.rows.get(0)));
        assertTrue(ClassicAdvisorBox.plain(r.rows.get(1)),
                   ClassicAdvisorBox.plain(r.rows.get(1)).startsWith("Mit Musketen bewaffnen (Kosten "
                       + prices[0]));
        assertEquals("Keine Veränderungen.", ClassicAdvisorBox.plain(r.rows.get(5)));
        assertNotNull("the colonist's icon in the box", r.unitIcon);
        assertEquals(Unit.UnitState.SENTRY, c.getState());
        s.boxes.row(0);
        click(s.panel, cx(cell), cy(cell));
        assertEquals(Unit.UnitState.ACTIVE, c.getState());
        assertEquals(List.of(), ClassicEuropePanel.boarders(s.europe, s.ship));
        s.boxes.row(0);                                           // now «An Bord ...»
        click(s.panel, cx(cell), cy(cell));
        assertEquals(Unit.UnitState.SENTRY, c.getState());
        final int gold = s.dutch.getGold();
        s.boxes.row(1);                                           // muskets
        click(s.panel, cx(cell), cy(cell));
        assertEquals("model.role.soldier", c.getRole().getId());
        assertEquals(gold - prices[0], s.dutch.getGold());
    }

    /**
     * The ship's box {@code @EUROPESHIPCLICK} (clip 020 #27512): a click
     * on the selected ship asks it with four rows, the ship's name and
     * icon; «Alle Waren entladen.» sells every hold; «Nach vorne
     * bewegen.» puts a second ship first; a click on the other ship selects
     * it.
     */
    public void testTheShipBox() throws Exception {
        final Scene s = rogers1496();
        if (ClassicText.load(ClassicPackFiles.runtime()) == null) {
            System.err.println(getClass().getSimpleName() + ": the ship box skipped, no pack");
            return;
        }
        s.ship.addGoods(furs, 100);
        s.ship.addGoods(tools, 30);
        click(s.panel, 154, 154);                                  // Escape
        assertEquals(List.of(ClassicEuropeOptions.SHIP_SECTION), s.boxes.asked);
        final ClassicAdvisorBox.Request r = s.boxes.requests.get(0);
        assertEquals("Europäische Hafenanlagen-Optionen für Handelsschiff:", r.plainText());
        assertEquals(4, r.rows.size());
        assertEquals("Segel setzen in die Neue Welt.", ClassicAdvisorBox.plain(r.rows.get(1)));
        assertEquals(0, r.defaultRow);
        assertEquals(new Rectangle(31, 72, 258, 56), ClassicAdvisorBox.layout(r,
            ClassicPackFiles.runtime().font(ClassicFont.TINY), null).box);
        assertEquals(100, s.ship.getGoodsCount(furs));
        final int gold = s.dutch.getGold();
        s.boxes.row(2);                                            // Alle Waren entladen.
        click(s.panel, 154, 154);
        assertEquals(0, s.ship.getGoodsCount(furs));
        assertEquals(0, s.ship.getGoodsCount(tools));
        assertTrue(s.dutch.getGold() > gold);
        // A second ship: a click selects it, its box's first row puts it first.
        final Unit second = new ServerUnit(s.game, s.europe, s.dutch, caravel);
        s.panel.refresh();
        assertSame(s.ship, s.panel.selectedShip());
        click(s.panel, 172, 154);
        assertSame(second, s.panel.selectedShip());
        s.boxes.row(0);
        click(s.panel, 172, 154);
        assertSame(second, s.panel.portShips().get(0));
        assertSame(second, s.panel.hitAt(154, 154).unit);
        assertSame(second, s.panel.selectedShip());
    }

    /**
     * Sailing (Roger's J1; clip008 §3.6): the ship dropped back in box 3
     * stays; dropped on box 2 «Ziel:» it asks {@code @SAILAWAY} with the
     * admiral, «Nein» keeps it, «Jawohl» sails it with the dock's "S"
     * colonists and its passengers, the screen is told (Europe then
     * closes by itself), and the ship is drawn in box 2 with its
     * passengers; a colonist marked "-" stays on the dock.
     */
    public void testSailingByDraggingTheShipToZiel() throws Exception {
        final Scene s = rogers1496();
        final Unit stay = new ServerUnit(s.game, s.europe, s.dutch, colonist);
        stay.setState(Unit.UnitState.ACTIVE);
        final Unit go = new ServerUnit(s.game, s.europe, s.dutch, colonist);
        go.setState(Unit.UnitState.SENTRY);
        drag(s.panel, 154, 154, 200, 130, false);                 // within box 3
        assertTrue(s.ship.isInEurope());
        final boolean texts = ClassicText.load(ClassicPackFiles.runtime()) != null;
        if (texts) {
            s.boxes.row(1);                                        // «Nein»
            drag(s.panel, 154, 154, 100, 150, false);
            assertEquals(List.of(ClassicEuropeOptions.SAIL_SECTION), s.boxes.asked);
            final ClassicAdvisorBox.Request r = s.boxes.requests.get(0);
            assertSame(ClassicAdvisorBox.Portrait.ADMIRAL, r.portrait);
            assertEquals(0, r.defaultRow);
            assertTrue(s.ship.isInEurope());
            s.boxes.row(0);                                        // «Jawohl»
        }
        drag(s.panel, 154, 154, 100, 150, false);
        assertFalse(s.ship.isInEurope());
        assertSame(s.dutch.getHighSeas(), s.ship.getLocation());
        assertSame(s.game.getMap(), s.ship.getDestination());
        assertEquals(List.of("sailed " + s.ship.getId()), s.events);
        assertSame(s.ship, go.getLocation());
        assertSame(s.europe, stay.getLocation());
        assertNull("no ship left in port", s.panel.selectedShip());
        assertEquals(List.of(s.ship), s.panel.sailing(false));
        final ClassicEuropePanel.Hit h = s.panel.hitAt(80, 154);
        assertEquals(ClassicEuropePanel.Kind.SAILING, h.kind);
        assertSame(s.ship, h.unit);
        assertEquals("[crate, crate, crate, crate, crate, crate]",
                     ClassicEuropePanel.holds(s.panel.selectedShip()).toString());
        s.panel.canvas();
    }

    /**
     * «Segel setzen» (the stopgap's button) sails the selected ship on
     * the release; a press on it released elsewhere does nothing.
     */
    public void testTheSetSailButton() throws Exception {
        final Scene s = rogers1496();
        final Rectangle b = ClassicEuropePanel.buttonBounds(3);
        mouse(s.panel, MouseEvent.MOUSE_PRESSED, cx(b), cy(b), false);
        assertTrue("acts on the release", s.ship.isInEurope());
        mouse(s.panel, MouseEvent.MOUSE_DRAGGED, 100, 40, false);
        mouse(s.panel, MouseEvent.MOUSE_RELEASED, 100, 40, false);
        assertTrue(s.ship.isInEurope());
        click(s.panel, cx(b), cy(b));
        assertFalse(s.ship.isInEurope());
        assertEquals(List.of("sailed " + s.ship.getId()), s.events);
    }

    /**
     * A ship of box 1 (bound for Europe) dropped on box 2 turns back to
     * the New World (the manual).
     */
    public void testTurningAShipUnderWay() throws Exception {
        final Scene s = rogers1496();
        final Unit back = new ServerUnit(s.game, s.dutch.getHighSeas(), s.dutch, caravel);
        back.setDestination(s.europe);
        back.setWorkLeft(1);
        assertEquals(List.of(back), s.panel.sailing(true));
        final Rectangle c = ClassicEuropePanel.sailingCell(ClassicEuropePanel.BOX1, 0, 1);
        drag(s.panel, cx(c), cy(c), 100, 150, false);
        assertSame(s.game.getMap(), back.getDestination());
        assertEquals(List.of(back), s.panel.sailing(false));
    }

    /**
     * Anwerben, Ausbilden, Kaufen with the real controller: a recruit
     * stands on the dock (the newest at the left), a trained expert too,
     * a bought caravel lies in port as the second ship.
     */
    public void testRecruitTrainAndPurchase() throws Exception {
        final Scene s = rogers1496();
        s.dutch.setGold(20000);
        click(s.panel, cx(ClassicEuropePanel.buttonBounds(0)), cy(ClassicEuropePanel.buttonBounds(0)));
        assertEquals(1, s.panel.dockUnits().size());
        final Unit recruit = s.panel.dockUnits().get(0);
        assertTrue(s.dutch.getGold() < 20000);
        click(s.panel, cx(ClassicEuropePanel.buttonBounds(2)), cy(ClassicEuropePanel.buttonBounds(2)));
        assertEquals(2, s.panel.dockUnits().size());
        assertNotSame("the newest at the left", recruit, s.panel.dockUnits().get(0));
        final int[] row = { -1 };
        s.panel.chooser = (title, prompt, options) -> {
            for (int i = 0; i < options.length; i++) {
                if (options[i].startsWith(net.sf.freecol.common.i18n.Messages.getName(caravel))) {
                    row[0] = i;
                }
            }
            return row[0];
        };
        click(s.panel, cx(ClassicEuropePanel.buttonBounds(1)), cy(ClassicEuropePanel.buttonBounds(1)));
        assertTrue(row[0] >= 0);
        assertEquals(2, s.panel.portShips().size());
        assertSame("the first ship stays selected", s.ship, s.panel.selectedShip());
    }

    /**
     * The keys: Escape and E close; R, K, A and 1-3 are the buttons;
     * Enter is the selected ship's box.
     */
    public void testKeys() throws Exception {
        final Scene s = rogers1496();
        for (String k : new String[] { "ESCAPE", "E", "R", "K", "A", "1", "2", "3", "ENTER", "U" }) {
            assertNotNull(k, s.panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                          .get(KeyStroke.getKeyStroke(k)));
        }
        run(s.panel, "ESCAPE");
        run(s.panel, "E");
        assertEquals(List.of("closed", "closed"), s.events);
        s.dutch.setGold(5000);
        run(s.panel, "R");
        assertEquals(1, s.panel.dockUnits().size());
        if (ClassicText.load(ClassicPackFiles.runtime()) != null) {
            run(s.panel, "ENTER");
            assertEquals(List.of(ClassicEuropeOptions.SHIP_SECTION), s.boxes.asked);
        }
    }

    /**
     * @TUTORIAL17 is asked over the Europe screen, on its own box layer
     * (prep R7: through the map's prompter it stood on the map's layer
     * behind Europe in a windowed game, and while unanswered it held
     * Europe's close after the last ship sailed).
     */
    public void testTheEuropeTipStandsOverEurope() throws Exception {
        final String src = new String(java.nio.file.Files.readAllBytes(new java.io.File(
            "src/net/sf/freecol/client/gui/classic/ClassicGUI.java").toPath()),
            java.nio.charset.StandardCharsets.UTF_8);
        final int at = src.indexOf("private void europeTip()");
        final int end = src.indexOf("static ClassicAdvisorBox.Request europeTipRequest", at);
        assertTrue(at > 0 && end > at);
        final String body = src.substring(at, end);
        assertTrue(body.contains("europeBox(r);"));
        assertFalse(body.contains("prompter.ask"));
    }

    /** Run a key's action as its binding does. */
    private static void run(ClassicEuropePanel p, String key) {
        final Object name = p.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .get(KeyStroke.getKeyStroke(key));
        p.getActionMap().get(name).actionPerformed(new ActionEvent(p, 0, key));
    }
}
