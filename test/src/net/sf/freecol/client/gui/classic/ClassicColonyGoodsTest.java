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
import java.util.ArrayList;
import java.util.List;

import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.server.ServerTestHelper;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;

import static net.sf.freecol.client.gui.classic.ClassicColonyBuildingsTest.State;


/**
 * Goods between the warehouse and a carrier (part V, build spec M4.9;
 * {@link ClassicColonyGoods}, the panel's goods drags): the words, the
 * amounts, the amount boxes; then with FreeCol's real client controller
 * and a real server game, on the screen with synthetic mouse events: a
 * load and an unload without Shift (the whole hold, min(stock, 100)), with
 * Shift (@HOWMUCH1 / @HOWMUCH2, the maximum preset), the drop on another
 * carrier (the selection stays), the drops that move nothing, the notices
 * and their times, the redraw 8 frames after the drop, the cell's yellow
 * frame.  The goods drags' place in the clips: clip 020 #10058 -&gt;
 * #10116 -&gt; #10124, #47642, #118966, #134733, #134899; clip 040 §1.6.
 */
public class ClassicColonyGoodsTest extends FreeColTestCase {

    private static GoodsType goods(String id) {
        return spec().getGoodsType("model.goods." + id);
    }

    /** The texts, or null (the word checks then skip with a note). */
    private ClassicText texts() {
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        if (t == null) System.err.println(getName() + ": no pack texts, word checks skipped");
        return t;
    }


    // The rules, the words, the boxes

    /**
     * The words (V clip 020 #10058, #10116, #134313, #134376; clip 040
     * #65 889): «Einladen Erz», «Ausladen Felle», «24 Erz bewegt nach
     * Handelsschiff», «18 Felle bewegt nach Base»; the times in frames.
     */
    public void testTheWords() {
        assertEquals(2, ClassicColonyGoods.NOTICE_FRAMES);
        assertEquals(1, ClassicColonyGoods.AFTER_BOX_FRAMES);
        assertEquals(8, ClassicColonyGoods.REDRAW_FRAMES);
        assertEquals(17, ClassicColonyGoods.YELLOW_FRAMES);
        assertEquals(27 * ClassicAdvisorLayer.FRAME_MS, ClassicColonyGoods.HOWMUCH_MS, 0.01);
        final ClassicText t = texts();
        if (t == null) return;
        assertEquals("Einladen Erz", ClassicColonyGoods.dragNotice(t, true, goods("ore")));
        assertEquals("Ausladen Felle", ClassicColonyGoods.dragNotice(t, false, goods("furs")));
        assertEquals("24 Erz bewegt nach Handelsschiff",
                     ClassicColonyGoods.movedNotice(t, 24, goods("ore"), "Handelsschiff"));
        assertEquals("100 Musketen bewegt nach Galeone",
                     ClassicColonyGoods.movedNotice(t, 100, goods("muskets"), "Galeone"));
        assertEquals("18 Felle bewegt nach Base",
                     ClassicColonyGoods.movedNotice(t, 18, goods("furs"), "Base"));
    }

    /**
     * A load's most (V 24, 12, 18 without Shift; @HOWMUCH1 «(0-100)» with
     * 121 rum): min(stock, 100, what the carrier takes).
     */
    public void testTheLoadMax() {
        assertEquals(24, ClassicColonyGoods.loadMax(24, 400));
        assertEquals(100, ClassicColonyGoods.loadMax(121, 200));
        assertEquals(50, ClassicColonyGoods.loadMax(121, 50));
        assertEquals(0, ClassicColonyGoods.loadMax(121, 0));
        assertEquals(0, ClassicColonyGoods.loadMax(0, 100));
    }

    /**
     * The amount boxes (V clip 020 #47642, #118966, #134733; clip 040
     * #73 655): @HOWMUCH2 «Wieviel {Erz} von Handelsschiff auf Base laden
     * (0-4)?», @HOWMUCH1 «Wieviel {Rum} auf Wagenzug laden (0-100)?»; box
     * (42,82,236,37), field (75,100,36,10), «Menge:», no portrait, no
     * rows; the preset the maximum (4, 100, 50; not Europe's «100» over
     * any max); 27 frames after the drop.
     */
    public void testTheAmountBoxes() {
        final ClassicText t = texts();
        if (t == null) return;
        final ClassicAdvisorBox.Request un = ClassicColonyGoods.howMuch(t, false, goods("ore"),
            "Handelsschiff", "Base", 4);
        assertEquals("HOWMUCH2", un.id);
        assertEquals("Wieviel Erz von Handelsschiff auf Base laden (0-4)?", un.plainText());
        assertEquals("4", un.field.initial);
        assertEquals(4, un.field.max);
        assertTrue(un.isNotice() == false && un.rows.isEmpty());
        assertEquals(ClassicAdvisorBox.Portrait.NONE, un.portrait);
        assertEquals(ClassicColonyGoods.HOWMUCH_MS, un.openDelayMs, 0.01);
        final ClassicAdvisorBox.Request load = ClassicColonyGoods.howMuch(t, true, goods("rum"),
            "Wagenzug", "Base", 100);
        assertEquals("HOWMUCH1", load.id);
        assertEquals("Wieviel Rum auf Wagenzug laden (0-100)?", load.plainText());
        assertEquals("100", load.field.initial);
        assertEquals("50 of (0-50): the maximum", "50",
                     ClassicColonyGoods.howMuch(t, false, goods("ore"), "Wagenzug", "Base", 50)
                     .field.initial);
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final ClassicFont tiny = (pack == null) ? null : pack.font(ClassicFont.TINY);
        if (tiny == null) return;
        final ClassicAdvisorBox.Layout l = ClassicAdvisorBox.layout(un, tiny, null);
        assertEquals(new Rectangle(42, 82, 236, 37), l.box);
        assertEquals(new Rectangle(75, 100, 36, 10), l.field);
        assertEquals(new Rectangle(42, 82, 236, 37),
                     ClassicAdvisorBox.layout(load, tiny, null).box);
    }


    /**
     * Golden against clip 020 (0 px, the arrow left out): the top line's
     * notices «Einladen Erz» (#10058, x 138) and «24 Erz bewegt nach
     * Handelsschiff» (#10116, x 100) in ink 149; the pressed ore cell's
     * frame yellow (#10058) and green 17 frames later (#10075); the amount
     * boxes @HOWMUCH2 «(0-4)» preset «4» (#47642), @HOWMUCH1 «(0-100)»
     * preset «100» (#118966), @HOWMUCH2 of a wagon train «(0-50)» preset
     * «50» (#134733).
     */
    public void testTheNoticesAndBoxesOverClip020() throws Exception {
        final ClassicColonyGolden gold = ClassicColonyGolden.open(getName());
        if (gold == null) return;
        final ClassicText t = ClassicText.load(gold.pack);
        final ClassicFont tiny = gold.pack.font(ClassicFont.TINY);
        final BufferedImage wood = gold.pack.image(ClassicMenuBar.WOOD_KEY);
        final State s = ClassicColonyBuildingsTest.base1539();
        s.on("expertFarmer", net.sf.freecol.common.model.Direction.NW, "grain");
        s.finish();
        final Clock clock = new Clock();
        ClassicColonyPanel.forgetFigureOrders();
        final ClassicColonyPanel p = new ClassicColonyPanel(
            ClassicColonyBuildingsTest.client(s.game, s.owner),
            ClassicColonyBuildingsTest.library(), s.colony, null, null, clock, false);
        p.setFounding(ClassicColonyBuildingsTest.BASE);
        final StringBuilder out = new StringBuilder();
        int bad = 0;
        final Rectangle top = new Rectangle(0, 0, 320, 7);
        final Rectangle oreBox = ClassicColonyLayout.warehouseBox(6);
        // #10058: the press on the ore cell: the notice, the yellow frame.
        p.holdNotice(ClassicColonyGoods.dragNotice(t, true, spec("classic")
            .getGoodsType("model.goods.ore")));
        ClassicColonyPanel.selectWarehouseGood(6);
        final java.lang.reflect.Field pressed = ClassicColonyPanel.class
            .getDeclaredField("boxPressedAt");
        pressed.setAccessible(true);
        pressed.set(p, clock.now());
        final Object[][] cases = {
            { 10058, top }, { 10058, oreBox },
        };
        for (Object[] c : cases) {
            bad += region(gold, p.canvas(), (Integer) c[0], (Rectangle) c[1], out);
        }
        clock.frames(17);
        bad += region(gold, p.canvas(), 10075, oreBox, out);
        p.showNotice(ClassicColonyGoods.movedNotice(t, 24, spec("classic")
            .getGoodsType("model.goods.ore"), "Handelsschiff"));
        bad += region(gold, p.canvas(), 10116, top, out);
        // The amount boxes.
        final Object[][] boxes = {
            { 47642, ClassicColonyGoods.howMuch(t, false, goods("ore"), "Handelsschiff", "Base", 4) },
            { 118966, ClassicColonyGoods.howMuch(t, true, goods("rum"), "Wagenzug", "Base", 100) },
            { 134733, ClassicColonyGoods.howMuch(t, false, goods("ore"), "Wagenzug", "Base", 50) },
        };
        for (Object[] b : boxes) {
            final ClassicAdvisorBox.Layout l = ClassicAdvisorBox.layout(
                (ClassicAdvisorBox.Request) b[1], tiny, null);
            final int[] got = ClassicColonyMovesTest.overFrame(gold, gold.frame((Integer) b[0]),
                l, -1, wood, tiny);
            out.append(" #").append(b[0]).append(' ').append(got[0]).append('/').append(got[1])
                .append(" arrow ").append(got[2]);
            bad += got[1];
        }
        System.out.println(getName() + ":" + out);
        assertEquals("pixels off:" + out, 0, bad);
    }

    /** Compare a region of our canvas with a frame (the box's outline only for a warehouse box). */
    private static int region(ClassicColonyGolden gold, BufferedImage ours, int frame,
                              Rectangle r, StringBuilder out) throws Exception {
        final BufferedImage f = gold.frame(frame);
        ClassicColonyGolden.Mask inside = null;
        if (r.height > 7) {
            // A warehouse box: only its frame (the icon and number are the
            // colony's, here Base 1539's, not 1540's).
            inside = new ClassicColonyGolden.Mask();
            inside.rect(r.x + 1, r.y + 1, r.x + r.width - 2, r.y + r.height - 2);
        }
        final ClassicColonyGolden.Diff d = ClassicColonyGolden.compare("#" + frame + " " + r,
            ClassicColonyGolden.pixels(ours), ClassicColonyGolden.pixels(f), r, gold.pointer(f),
            inside);
        out.append("\n  ").append(d);
        return d.differing;
    }


    // On the screen, with the real controller and server

    /** A clock the test sets (nanoseconds). */
    private static final class Clock implements ClassicSlide.Clock {
        long now = 1_000_000_000L;

        @Override
        public long now() {
            return this.now;
        }

        @Override
        public void waitUntil(long due) {
            if (due > this.now) this.now = due;
        }

        void frames(int n) {
            this.now += Math.round(ClassicColonyLayout.frames(n) * 1_000_000.0);
        }
    }

    /** What a screen test works with. */
    private static final class Scene {
        Game game;
        ServerPlayer dutch;
        Colony colony;
        ClassicColonyServerScene.Loopback api;
        ClassicColonyServerScene.Boxes boxes;
        ClassicColonyPanel panel;
        final Clock clock = new Clock();
        final List<Integer> laterMs = new ArrayList<>();
        final List<Runnable> later = new ArrayList<>();

        /** Run the things due later, in order (as their timers would). */
        void runLater() {
            final List<Runnable> rs = new ArrayList<>(this.later);
            this.later.clear();
            this.laterMs.clear();
            for (Runnable r : rs) r.run();
            ClassicColonyServerScene.paint(this.panel);
        }

        Unit carrier(String type) {
            return new ServerUnit(this.game, this.colony.getTile(), this.dutch,
                                  spec().getUnitType("model.unit." + type));
        }
    }

    private Scene scene() throws Exception {
        final Scene s = new Scene();
        s.game = ServerTestHelper.startServerGame(getTestMap(true));
        s.dutch = getServerPlayer(s.game, "model.nation.dutch");
        s.colony = createStandardColony(3);
        s.api = new ClassicColonyServerScene.Loopback(ServerTestHelper.getServer(), s.dutch);
        s.boxes = new ClassicColonyServerScene.Boxes();
        ClassicColonyPanel.forgetFigureOrders();
        ClassicColonyPanel.forgetSelections();
        ClassicColonyPort.forgetOrders();
        return s;
    }

    /** The screen over the scene's colony (after its carriers came). */
    private static void open(Scene s) throws Exception {
        s.panel = new ClassicColonyPanel(
            ClassicColonyServerScene.client(s.game, s.dutch, s.api),
            ClassicColonyBuildingsTest.library(), s.colony, null, s.boxes, s.clock, false);
        s.panel.setLater((ms, r) -> {
                s.laterMs.add(ms);
                s.later.add(r);
            });
        s.panel.setSize(960, 600);
        ClassicColonyServerScene.paint(s.panel);
    }

    @Override
    public void tearDown() throws Exception {
        ClassicColonyPanel.setTileCursor(ClassicColonyLayout.CENTRE_TILE);
        ClassicColonyPanel.selectWarehouseGood(0);
        ClassicColonyPanel.forgetSelections();
        ClassicColonyPanel.forgetFigureOrders();
        ClassicColonyPort.forgetOrders();
        ServerTestHelper.stopServerGame();
        super.tearDown();
    }

    /** A warehouse cell's centre. */
    private static int[] cell(String goodsId) {
        for (int i = 0; i < ClassicColonyLayout.WAREHOUSE_GOODS.length; i++) {
            if (ClassicColonyLayout.WAREHOUSE_GOODS[i].equals("model.goods." + goodsId)) {
                return new int[] { 19 * i + 10, 189 };
            }
        }
        throw new IllegalArgumentException(goodsId);
    }

    /** A hold's centre. */
    private static int[] hold(int k) {
        return new int[] { 127 + 12 * k + 5, 170 };
    }

    /** A full-size slot's centre. */
    private static int[] slot(int i) {
        return new int[] { 130 + 18 * i + 8, 154 };
    }

    /**
     * Without Shift (V clip 020 #10058 -&gt; #10116 -&gt; #10124): the
     * press holds «Einladen Erz», selects the ore cell (its frame yellow,
     * green 17 frames later); the drop on the holds loads the whole stock
     * (24), the ship's frame white during the drag; the result notice 2
     * frames after the drop, the hold and the number 8 frames after it
     * (until then the old ones); then the furs: the hold dragged onto any
     * warehouse cell (the silver one) unloads the whole hold (V #134312).
     */
    public void testLoadAndUnloadWithoutShift() throws Exception {
        final Scene s = scene();
        final Unit ship = s.carrier("merchantman");
        s.colony.addGoods(goods("ore"), 24);
        ship.addGoods(goods("furs"), 18);
        open(s);
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        final int[] ore = cell("ore");
        ClassicColonyServerScene.mouse(s.panel, java.awt.event.MouseEvent.MOUSE_PRESSED,
                                       ore[0], ore[1], false);
        assertTrue(s.panel.goodsPressed());
        assertEquals(6, ClassicColonyPanel.warehouseSelection());
        if (t != null) assertEquals("Einladen Erz", s.panel.noticeShown());
        final ClassicColonyChrome.Assets c = ClassicColonyChrome.Assets.load(
            ClassicPackFiles.runtime());
        BufferedImage img = s.panel.canvas();
        final Rectangle box = ClassicColonyLayout.warehouseBox(6);
        assertEquals("yellow at the press", c.rgb(ClassicColonyLayout.INK_YELLOW),
                     img.getRGB(box.x, box.y) & 0xFFFFFF);
        s.clock.frames(17);
        img = s.panel.canvas();
        assertEquals("green 17 frames later", c.rgb(ClassicColonyLayout.INK_GREEN),
                     img.getRGB(box.x, box.y) & 0xFFFFFF);
        // The drag: the ship's frame white.
        final int[] h0 = hold(0);
        ClassicColonyServerScene.mouse(s.panel, java.awt.event.MouseEvent.MOUSE_DRAGGED,
                                       h0[0], h0[1], false);
        img = s.panel.canvas();
        final Rectangle f = ClassicColonyLayout.carrierFrame(true, 0);
        assertEquals("the carrier white during the drag", c.rgb(ClassicColonyLayout.INK_WHITE),
                     img.getRGB(f.x, f.y) & 0xFFFFFF);
        ClassicColonyServerScene.mouse(s.panel, java.awt.event.MouseEvent.MOUSE_RELEASED,
                                       h0[0], h0[1], false);
        assertEquals("all 24 aboard " + s.api.log, 24, ship.getGoodsCount(goods("ore")));
        assertEquals(0, s.colony.getGoodsCount(goods("ore")));
        assertTrue(s.api.log.toString(), s.api.log.get(0).startsWith("load 24 ore"));
        // Until the redraw: the old number and holds; the notice still the drag's.
        assertEquals("the redraw +8 frames", ClassicColonyGoods.ms(8),
                     s.laterMs.get(s.laterMs.size() - 2), 1.0);
        assertEquals("the notice +2 frames", ClassicColonyGoods.ms(2),
                     s.laterMs.get(s.laterMs.size() - 1), 1.0);
        ClassicColonyServerScene.paint(s.panel);
        assertEquals("the old holds until the redraw", "[furs:18, empty, empty, empty, crate, crate]",
                     s.panel.portShown().holds.toString());
        assertEquals("the old number until the redraw", 24, s.panel.amountsShown()[6]);
        if (t != null) assertEquals("Einladen Erz", s.panel.noticeShown());
        s.runLater();
        if (t != null) assertEquals("24 Erz bewegt nach Handelsschiff", s.panel.noticeShown());
        assertEquals("[furs:18, ore:24, empty, empty, crate, crate]",
                     s.panel.portShown().holds.toString());
        assertEquals(0, s.panel.amountsShown()[6]);
        // The notice lasts 138 frames.
        s.clock.frames(137);
        if (t != null) assertNotNull(s.panel.noticeShown());
        s.clock.frames(2);
        assertNull(s.panel.noticeShown());
        // Unload: the furs hold onto the silver cell.
        final int[] silver = cell("silver");
        ClassicColonyServerScene.drag(s.panel, h0[0], h0[1], silver[0], silver[1], false);
        assertEquals("the whole hold " + s.api.log, 0, ship.getGoodsCount(goods("furs")));
        assertEquals(18, s.colony.getGoodsCount(goods("furs")));
        s.runLater();
        if (t != null) {
            assertEquals("18 Felle bewegt nach " + s.colony.getName(), s.panel.noticeShown());
        }
        assertEquals("the holds close up (V #134899)", "[ore:24, empty, empty, empty, crate, crate]",
                     s.panel.port().holds.toString());
        assertFalse(s.panel.dragging());
    }

    /**
     * With Shift (V clip 020 #47642, #118966; clip 040 #73 655): @HOWMUCH2
     * from a hold of 4 ore preset «4», «2» typed: 2 unloaded, «2 Erz bewegt
     * nach …» 1 frame after the box; @HOWMUCH1 with 121 rum in stock and a
     * wagon train: «(0-100)» preset «100», «50» typed: 50 loaded; Escape
     * moves nothing and the notice goes.
     */
    public void testWithShift() throws Exception {
        final Scene s = scene();
        final Unit wagon = s.carrier("wagonTrain");
        final Unit ship = s.carrier("merchantman");
        ship.addGoods(goods("ore"), 4);
        s.colony.addGoods(goods("rum"), 121);
        open(s);
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        assertSame("the ship came last: in front", ship, s.panel.selectedCarrier());
        s.boxes.answers.add("2");
        final int[] h0 = hold(0), ore = cell("ore");
        ClassicColonyServerScene.drag(s.panel, h0[0], h0[1], ore[0], ore[1], true);
        if (t != null) {
            final ClassicAdvisorBox.Request r = s.boxes.last();
            assertEquals("HOWMUCH2", r.id);
            assertEquals("4", r.field.initial);
            assertEquals("Wieviel Erz von Handelsschiff auf " + s.colony.getName()
                         + " laden (0-4)?", r.plainText());
            assertEquals(2, ship.getGoodsCount(goods("ore")));
            assertEquals(2, s.colony.getGoodsCount(goods("ore")));
            assertEquals("the notice +1 frame after the box", ClassicColonyGoods.ms(1),
                         s.laterMs.get(s.laterMs.size() - 1), 1.0);
            s.runLater();
            assertEquals("2 Erz bewegt nach " + s.colony.getName(), s.panel.noticeShown());
        }
        // The wagon train: a press selects it; rum with Shift.
        final int[] w = slot(1);
        ClassicColonyServerScene.click(s.panel, w[0], w[1]);
        assertSame(wagon, s.panel.selectedCarrier());
        s.boxes.answers.add("50");
        final int[] rum = cell("rum");
        ClassicColonyServerScene.drag(s.panel, rum[0], rum[1], h0[0], h0[1], true);
        if (t != null) {
            final ClassicAdvisorBox.Request r = s.boxes.last();
            assertEquals("HOWMUCH1", r.id);
            assertEquals("100", r.field.initial);
            assertEquals("Wieviel Rum auf Wagenzug laden (0-100)?", r.plainText());
            assertEquals(50, wagon.getGoodsCount(goods("rum")));
            assertEquals(71, s.colony.getGoodsCount(goods("rum")));
            s.runLater();
            assertEquals("50 Rum bewegt nach Wagenzug", s.panel.noticeShown());
        }
        // Escape: nothing moves, the notice goes.
        final int before = s.boxes.asked.size();
        ClassicColonyServerScene.drag(s.panel, rum[0], rum[1], h0[0], h0[1], true);
        if (t != null) {
            assertEquals(before + 1, s.boxes.asked.size());
            assertEquals(50, wagon.getGoodsCount(goods("rum")));
            assertNull("the drag's notice gone", s.panel.noticeShown());
        }
    }

    /**
     * Without Shift a load is a hold's worth: min(stock, 100) (121 rum:
     * 100), at most what the carrier takes (a full wagon train: nothing,
     * and no notice); a drop on another carrier loads that one and the
     * selection stays (V clip 040 #67 183: muskets dropped on the tiny
     * wagon while the tiny galleon is selected); the notice names the
     * receiving carrier's type.
     */
    public void testHowMuchAndWhichCarrier() throws Exception {
        final Scene s = scene();
        final Unit wagon = s.carrier("wagonTrain");
        final Unit galleon = s.carrier("galleon");
        s.colony.addGoods(goods("rum"), 121);
        s.colony.addGoods(goods("muskets"), 80);
        open(s);
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        assertSame(galleon, s.panel.selectedCarrier());
        final int[] rum = cell("rum"), h0 = hold(0);
        ClassicColonyServerScene.drag(s.panel, rum[0], rum[1], h0[0], h0[1], false);
        assertEquals("100 of 121", 100, galleon.getGoodsCount(goods("rum")));
        // The muskets dropped on the wagon train's sprite (slot 1): into it.
        final int[] muskets = cell("muskets"), w = slot(1);
        ClassicColonyServerScene.drag(s.panel, muskets[0], muskets[1], w[0], w[1], false);
        assertEquals(80, wagon.getGoodsCount(goods("muskets")));
        assertSame("the selection stays", galleon, s.panel.selectedCarrier());
        s.runLater();
        if (t != null) assertEquals("80 Musketen bewegt nach Wagenzug", s.panel.noticeShown());
        // The rest of the rum (21) into the wagon train's second hold, then
        // what that hold still takes (79 of 150), then nothing: it is full.
        ClassicColonyServerScene.drag(s.panel, rum[0], rum[1], w[0], w[1], false);
        assertEquals(21, wagon.getGoodsCount(goods("rum")));
        assertEquals(0, s.colony.getGoodsCount(goods("rum")));
        s.colony.addGoods(goods("rum"), 150);
        ClassicColonyServerScene.drag(s.panel, rum[0], rum[1], w[0], w[1], false);
        assertEquals("what the hold takes", 100, wagon.getGoodsCount(goods("rum")));
        final int log = s.api.log.size();
        ClassicColonyServerScene.drag(s.panel, rum[0], rum[1], w[0], w[1], false);
        assertEquals("full: no call", log, s.api.log.size());
        assertEquals(71, s.colony.getGoodsCount(goods("rum")));
        assertNull("and no notice", s.panel.noticeShown());
    }

    /**
     * Drops that move nothing (V clip 040 #77 106-#77 168, #65 121-#65 379):
     * a warehouse good back on the warehouse, a hold on the holds, a good
     * dropped nowhere; a press without motion on a warehouse cell flashes
     * the notice and moves nothing (#77 000-#77 002); the next press ends a
     * result's notice early (#67 183 -&gt; #67 230).
     */
    public void testDropsThatMoveNothing() throws Exception {
        final Scene s = scene();
        final Unit ship = s.carrier("merchantman");
        ship.addGoods(goods("ore"), 30);
        s.colony.addGoods(goods("rum"), 40);
        open(s);
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        final int[] rum = cell("rum"), ore = cell("ore"), h0 = hold(0), h1 = hold(1);
        ClassicColonyServerScene.drag(s.panel, rum[0], rum[1], ore[0], ore[1], false);
        ClassicColonyServerScene.drag(s.panel, h0[0], h0[1], h1[0], h1[1], false);
        ClassicColonyServerScene.drag(s.panel, rum[0], rum[1], 60, 60, false);
        assertTrue(s.api.log.toString(), s.api.log.isEmpty());
        assertNull(s.panel.noticeShown());
        ClassicColonyServerScene.click(s.panel, rum[0], rum[1]);
        assertTrue(s.api.log.isEmpty());
        assertNull("the flash ends with the release", s.panel.noticeShown());
        assertFalse(s.panel.goodsPressed());
        // A result's notice ends at the next press.
        ClassicColonyServerScene.drag(s.panel, rum[0], rum[1], h0[0], h0[1], false);
        s.runLater();
        if (t != null) assertNotNull(s.panel.noticeShown());
        ClassicColonyServerScene.click(s.panel, 230, 40);
        assertNull("ended early", s.panel.noticeShown());
    }

    /**
     * A carrier's box with the real controller (I for the rows not tried
     * in clip 040): «Wache.» puts it on sentry, «Im Hafen ankern
     * ("Befestigen").» fortifies it, «Alle Waren entladen.» unloads every
     * good into the colony, «Nach vorne bewegen.» puts it in front,
     * selected (V ×4), «Keine Veränderungen.» changes nothing (V).
     */
    public void testTheCarriersBox() throws Exception {
        final Scene s = scene();
        final Unit wagon = s.carrier("wagonTrain");
        final Unit ship = s.carrier("merchantman");
        ship.addGoods(goods("ore"), 30);
        ship.addGoods(goods("rum"), 120);
        open(s);
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        if (t == null) return;
        final int[] f0 = slot(0), f1 = slot(1);
        // The front ship, loaded: Wache. / Im Hafen ankern / Alle Waren / Keine (#69 235).
        s.boxes.answers.add(1);
        ClassicColonyServerScene.click(s.panel, f0[0], f0[1]);
        assertEquals("COLONYUNIT [Wache., Im Hafen ankern (\"Befestigen\")., "
                     + "Alle Waren entladen., Keine Veränderungen.] bar=0", s.boxes.lastRows());
        // Two spaces in the box (no expert part; its pixels: ClassicColonyCarriersTest),
        // one in the plain text.
        assertEquals("Optionen für (Handelsschiff):", s.boxes.last().plainText());
        assertEquals(Unit.UnitState.FORTIFYING, ship.getState());
        // With orders now: «Befehle aufheben.» first (I); «Wache.» the second.
        s.boxes.answers.add(1);
        ClassicColonyServerScene.click(s.panel, f0[0], f0[1]);
        assertTrue(s.boxes.lastRows(),
                   s.boxes.lastRows().startsWith("COLONYUNIT [Befehle aufheben., Wache."));
        assertEquals(Unit.UnitState.SENTRY, ship.getState());
        // «Alle Waren entladen.»: every good into the colony; no other box,
        // the result in the top line at once (fixer A after part X: V clip
        // 050 #33922 «40 Werkzeuge bewegt nach Base»), the hold redrawn 8
        // frames later, the warehouse 9, the ship still selected with the
        // white cursor on it, its blink's first toggle 33 frames on.
        final int before = s.boxes.asked.size();
        final int[] storeBefore = s.panel.amountsShown();
        s.laterMs.clear();
        s.later.clear();
        s.boxes.answers.add(3);                 // after «Befehle aufheben.», «Wache.», «ankern»
        ClassicColonyServerScene.click(s.panel, f0[0], f0[1]);
        assertEquals(before + 1, s.boxes.asked.size());
        assertFalse(s.api.log.toString(), ship.hasGoodsCargo());
        assertEquals(30, s.colony.getGoodsCount(goods("ore")));
        assertEquals(120, s.colony.getGoodsCount(goods("rum")));
        final String notice = s.panel.noticeShown();
        assertNotNull(notice);
        assertTrue(notice, notice.equals(ClassicColonyGoods.movedNotice(t, 30, goods("ore"),
                       s.colony.getName()))
                   || notice.equals(ClassicColonyGoods.movedNotice(t, 120, goods("rum"),
                       s.colony.getName())));
        assertTrue(s.laterMs.toString(), s.laterMs.contains(
            (int) Math.round(ClassicColonyGoods.ms(8))));
        assertTrue(s.laterMs.toString(), s.laterMs.contains(
            (int) Math.round(ClassicColonyGoods.ms(9))));
        assertTrue("the warehouse as before until its redraw",
                   java.util.Arrays.equals(storeBefore, s.panel.amountsShown()));
        assertSame("the hold as before until its redraw", goods("ore"),
                   s.panel.portShown().holds.get(0).goods);
        assertSame(ship, s.panel.selectedCarrier());
        assertEquals(ClassicColonyPanel.CURSOR_CARRIER, ClassicColonyPanel.tileCursor());
        s.runLater();
        assertFalse(java.util.Arrays.equals(storeBefore, s.panel.amountsShown()));
        assertNull("the holds empty", s.panel.portShown().holds.get(0).goods);
        assertSame(ship, s.panel.selectedCarrier());
        // The wagon train: select, then its box; «Nach vorne bewegen.» (#51 167).
        ClassicColonyServerScene.click(s.panel, f1[0], f1[1]);
        assertSame(wagon, s.panel.selectedCarrier());
        s.boxes.answers.add(0);
        ClassicColonyServerScene.click(s.panel, f1[0], f1[1]);
        assertEquals("COLONYUNIT [Nach vorne bewegen., Wache., Keine Veränderungen.] bar=0",
                     s.boxes.lastRows());
        assertEquals(List.of(wagon, ship), ClassicColonyPort.carriers(s.colony));
        assertSame(wagon, s.panel.selectedCarrier());
        // «Keine Veränderungen.» and Escape: nothing.
        final Unit.UnitState was = wagon.getState();
        s.boxes.answers.add(1);
        ClassicColonyServerScene.click(s.panel, f0[0], f0[1]);
        ClassicColonyServerScene.click(s.panel, f0[0], f0[1]);
        assertEquals(was, wagon.getState());
        assertEquals(List.of(wagon, ship), ClassicColonyPort.carriers(s.colony));
    }


    // The review of part V (play L2, L3; regression L4, L9)

    /**
     * One hold of a good spread over two (121 rum: 100 + 21) unloads that
     * hold only (regression L4): the 21 hold moves 21, the 100 stay.
     */
    public void testOneHoldOfTwo() throws Exception {
        final Scene s = scene();
        final Unit galleon = s.carrier("galleon");
        galleon.addGoods(goods("rum"), 121);
        open(s);
        assertEquals(2, s.panel.port().holds.stream().filter(h -> h.goods != null).count());
        assertEquals(21, s.panel.port().holds.get(1).amount);
        final int[] h1 = hold(1), w = cell("rum");
        ClassicColonyServerScene.drag(s.panel, h1[0], h1[1], w[0], w[1], false);
        s.runLater();
        assertEquals("the 21 hold only", 100, galleon.getGoodsCount(goods("rum")));
        assertEquals(21, s.colony.getGoodsCount(goods("rum")));
    }

    /**
     * A press within the 8 frames before a transfer's redraw takes the
     * holds as the screen shows them (play L2): after hold 0 is unloaded
     * the holds close up, but until the redraw hold 1 still shows the
     * second good on the screen, and that good moves.
     */
    public void testAPressBeforeTheRedraw() throws Exception {
        final Scene s = scene();
        final Unit galleon = s.carrier("galleon");
        galleon.addGoods(goods("cloth"), 100);
        galleon.addGoods(goods("furs"), 50);
        open(s);
        s.runLater();
        final GoodsType first = s.panel.port().holds.get(0).goods;
        final GoodsType second = s.panel.port().holds.get(1).goods;
        assertNotNull(first);
        assertNotNull(second);
        final int[] h0 = hold(0), h1 = hold(1), w = cell("cloth");
        ClassicColonyServerScene.drag(s.panel, h0[0], h0[1], w[0], w[1], false);
        assertEquals(0, galleon.getGoodsCount(first));
        // Not redrawn yet: the old holds shown.
        assertEquals(second, s.panel.portShown().holds.get(1).goods);
        ClassicColonyServerScene.drag(s.panel, h1[0], h1[1], w[0], w[1], false);
        s.runLater();
        assertEquals("the good shown in hold 1 moved", 0, galleon.getGoodsCount(second));
        assertFalse(galleon.hasGoodsCargo());
    }

    /**
     * A refused transfer shows the screen as it is at once (regression
     * L9, R25): not our turn, the load refused, no frozen picture.
     */
    public void testARefusedTransferUnfreezes() throws Exception {
        final Scene s = scene();
        final Unit ship = s.carrier("merchantman");
        s.colony.addGoods(goods("ore"), 30);
        open(s);
        s.game.setCurrentPlayer(getServerPlayer(s.game, "model.nation.french"));
        final int[] ore = cell("ore"), h0 = hold(0);
        ClassicColonyServerScene.drag(s.panel, ore[0], ore[1], h0[0], h0[1], false);
        assertEquals("refused", 0, ship.getGoodsCount(goods("ore")));
        assertSame("no frozen picture", s.panel.port(), s.panel.portShown());
        assertTrue(java.util.Arrays.equals(ClassicColonyChrome.amounts(s.colony),
                                           s.panel.amountsShown()));
        assertNull(s.panel.noticeShown());
    }

    /**
     * A carrier's box (regression L9, R14; play L3): only a release on
     * the same carrier opens it; «Im Hafen ankern» only with moves left
     * (FreeCol's fortify needs them).
     */
    public void testTheCarriersBoxGuards() throws Exception {
        final Scene s = scene();
        final Unit ship = s.carrier("merchantman");
        open(s);
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        if (t == null) return;
        final int[] f0 = slot(0);
        // Pressed on the selected ship, released beside it: no box.
        final int asked = s.boxes.asked.size();
        ClassicColonyServerScene.mouse(s.panel, java.awt.event.MouseEvent.MOUSE_PRESSED,
                                       f0[0], f0[1], false);
        ClassicColonyServerScene.mouse(s.panel, java.awt.event.MouseEvent.MOUSE_RELEASED,
                                       f0[0] + 40, f0[1], false);
        assertEquals("no box", asked, s.boxes.asked.size());
        // No moves left: no «Im Hafen ankern».
        ship.setMovesLeft(0);
        assertFalse(ClassicColonyUnits.carrierOptions(ship, true)
                    .contains(ClassicColonyUnits.CarrierOption.ANCHOR));
        ClassicColonyServerScene.click(s.panel, f0[0], f0[1]);
        assertEquals("COLONYUNIT [Wache., Keine Veränderungen.] bar=0", s.boxes.lastRows());
        ship.setMovesLeft(ship.getInitialMovesLeft());
        assertTrue(ClassicColonyUnits.carrierOptions(ship, true)
                   .contains(ClassicColonyUnits.CarrierOption.ANCHOR));
    }
}
