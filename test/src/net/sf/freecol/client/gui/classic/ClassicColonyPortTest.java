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

import net.sf.freecol.common.model.Direction;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;

import static net.sf.freecol.client.gui.classic.ClassicColonyBuildingsTest.State;


/**
 * The colony screen's port panel (build spec M4.6; {@link
 * ClassicColonyPort}, {@link ClassicColonyLayout}): the caption's words
 * and place, the carrier's cell and frame, the holds' places, the holds
 * of a test carrier (the loading order, holds of 100 and the rest, the
 * close-up after an unloading, the crates, units aboard), which carrier,
 * the white cursor on it; then golden: the state of clip 020's frames
 * rebuilt in a test game (the carrier, its goods in the order they came
 * aboard, the cursor and its blink half) and the port panel (x 119-207,
 * y 129-177, with the band picture's frame lines) compared with the frame
 * pixel for pixel, the arrow left out.  Skips the golden part without the
 * pack or the recordings ({@code -Dclassic.clips}).
 */
public class ClassicColonyPortTest extends FreeColTestCase {

    private static Specification classic() {
        return spec("classic");
    }

    @Override
    protected void tearDown() throws Exception {
        ClassicColonyPanel.forgetFigureOrders();
        ClassicColonyPort.forgetOrders();
        ClassicColonyPanel.selectWarehouseGood(0);
        ClassicColonyPanel.setTileCursor(ClassicColonyLayout.CENTRE_TILE);
        classic().applyDifficultyLevel("model.difficulty.medium");
        super.tearDown();
    }

    private static GoodsType goods(String id) {
        return classic().getGoodsType("model.goods." + id);
    }

    /** A carrier of a type on the colony's tile. */
    private static Unit carrier(State s, String type) {
        final UnitType ut = classic().getUnitType("model.unit." + type);
        return new ServerUnit(s.game, s.colony.getTile(), s.owner, ut);
    }


    // The geometry (M4.6 targets 1-3)

    /** The caption's x, the carrier's cell and frame, the holds' places (V clip 020, clip008). */
    public void testTheGeometry() {
        assertEquals(new Rectangle(130, 146, 16, 16), ClassicColonyLayout.carrierCell(true));
        assertEquals(new Rectangle(130, 147, 16, 16), ClassicColonyLayout.carrierCell(false));
        assertEquals("ship frame (129,145)-(146,162)", new Rectangle(129, 145, 18, 18),
                     ClassicColonyLayout.carrierFrame(true));
        assertEquals("wagon frame (129,146)-(146,163)", new Rectangle(129, 146, 18, 18),
                     ClassicColonyLayout.carrierFrame(false));
        assertEquals("ICONS.SS.006 at 133", 133, 130 + ClassicHud.spriteOffset(13));
        assertEquals("ICONS.SS.008 at 132", 132, 130 + ClassicHud.spriteOffset(14));
        assertEquals("ore w 10 in hold 0", 127, ClassicColonyLayout.holdIconX(0, 10));
        assertEquals("ore in hold 1", 139, ClassicColonyLayout.holdIconX(1, 10));
        assertEquals("rum w 7 in hold 0", 129, ClassicColonyLayout.holdIconX(0, 7));
        assertEquals("rum in hold 1", 141, ClassicColonyLayout.holdIconX(1, 7));
        assertEquals("furs w 9 in hold 0", 128, ClassicColonyLayout.holdIconX(0, 9));
        assertEquals("furs in hold 1", 140, ClassicColonyLayout.holdIconX(1, 9));
        assertEquals(175, ClassicColonyLayout.crateX(4));
        assertEquals(187, ClassicColonyLayout.crateX(5));
        assertEquals(151, ClassicColonyLayout.crateX(2));
        assertEquals(165, ClassicColonyLayout.HOLD_Y);
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final ClassicText t = ClassicText.load(pack);
        final ClassicFont tiny = (pack == null) ? null : pack.font(ClassicFont.TINY);
        if (t == null || tiny == null) return;
        final State s = ClassicColonyBuildingsTest.base1539();
        s.on("expertFarmer", Direction.NW, "grain");
        s.finish();
        assertEquals("Keine Schiffe im Hafen", ClassicColonyPort.caption(t, null));
        assertEquals("Einladen: Handelsschiff",
                     ClassicColonyPort.caption(t, carrier(s, "merchantman")));
        assertEquals("Einladen: Wagenzug", ClassicColonyPort.caption(t, carrier(s, "wagonTrain")));
        assertEquals(123, ClassicColonyLayout.portCaptionX(tiny.stringWidth("Keine Schiffe im Hafen")));
        assertEquals(129, ClassicColonyLayout.portCaptionX(tiny.stringWidth("Einladen: Wagenzug")));
        assertEquals("over the separator", 121,
                     ClassicColonyLayout.portCaptionX(tiny.stringWidth("Einladen: Handelsschiff")));
    }


    // The holds and the carrier

    /**
     * A carrier's six holds: the open ones by its capacity, crates on the
     * rest; the goods in the order they came aboard (V #120499: rum before
     * furs), a good already aboard when first seen in the goods' order;
     * 100 a full hold (colours), the rest a part (grey); an emptied hold
     * closes up (V #134899); units aboard first.
     */
    public void testTheHolds() {
        final State s = ClassicColonyBuildingsTest.base1539();
        s.on("expertFarmer", Direction.NW, "grain");
        s.finish();
        assertEquals("[crate, crate, crate, crate, crate, crate]",
                     ClassicColonyPort.holds(null).toString());
        final Unit ship = carrier(s, "merchantman");
        assertEquals("[empty, empty, empty, empty, crate, crate]",
                     ClassicColonyPort.holds(ship).toString());
        final Unit wagon = carrier(s, "wagonTrain");
        assertEquals("[empty, empty, crate, crate, crate, crate]",
                     ClassicColonyPort.holds(wagon).toString());
        // The loading order: rum, then furs (not the goods' order).
        wagon.addGoods(goods("rum"), 50);
        assertEquals("[rum:50, empty, crate, crate, crate, crate]",
                     ClassicColonyPort.holds(wagon).toString());
        wagon.addGoods(goods("furs"), 18);
        assertEquals("[rum:50, furs:18, crate, crate, crate, crate]",
                     ClassicColonyPort.holds(wagon).toString());
        // Unloaded: the holds close up (#134899), a new good at the end.
        wagon.removeGoods(goods("rum"));
        assertEquals("[furs:18, empty, crate, crate, crate, crate]",
                     ClassicColonyPort.holds(wagon).toString());
        // Already aboard when first seen: the goods' order (ore 6 before rum 9).
        ship.addGoods(goods("rum"), 12);
        ship.addGoods(goods("ore"), 24);
        assertEquals("[ore:24, rum:12, empty, empty, crate, crate]",
                     ClassicColonyPort.holds(ship).toString());
        // 150 tools: a full hold and a part; the full one's colours.
        ship.addGoods(goods("tools"), 150);
        final List<ClassicColonyPort.Hold> h = ClassicColonyPort.holds(ship);
        assertEquals("[ore:24, rum:12, tools:100, tools:50, crate, crate]", h.toString());
        assertTrue(h.get(2).full());
        assertFalse(h.get(3).full());
        assertEquals(36, ClassicColonyPort.holdIcon(goods("tools"), true));
        assertEquals(52, ClassicColonyPort.holdIcon(goods("tools"), false));
        assertEquals("grey ore", 44, ClassicColonyPort.holdIcon(goods("ore"), false));
        assertEquals("grey furs", 42, ClassicColonyPort.holdIcon(goods("furs"), false));
        assertEquals("grey rum", 47, ClassicColonyPort.holdIcon(goods("rum"), false));
        // Units aboard first, each in as many holds as it takes.
        final Unit galleon = carrier(s, "galleon");
        galleon.addGoods(goods("sugar"), 100);
        final Unit colonist = new ServerUnit(s.game, galleon, s.owner,
            classic().getUnitType("model.unit.freeColonist"));
        assertSame(galleon, colonist.getLocation());
        final List<ClassicColonyPort.Hold> g = ClassicColonyPort.holds(galleon);
        assertEquals("[freeColonist, sugar:100, empty, empty, empty, empty]", g.toString());
        assertSame(colonist, g.get(0).unit);
        // Only one game's orders are kept (no old game held).
        assertEquals(3, ClassicColonyPort.remembered());
        final State t = ClassicColonyBuildingsTest.base1539();
        t.on("expertFarmer", Direction.NW, "grain");
        t.finish();
        ClassicColonyPort.holds(carrier(t, "caravel"));
        assertEquals(1, ClassicColonyPort.remembered());
    }

    /**
     * Which carriers the panel shows (part V, clip 040 §1.3, the S
     * interim's "the first ship, else the first wagon train" replaced):
     * the carriers on the tile in the port order, ships and wagon trains
     * alike, the newest arrival in front (V #46 925, #52 642, #71 550,
     * #76 135), «Nach vorne bewegen.» puts one in front (V #51 167), one
     * that leaves is taken out and the rest keep their order (V #69 042);
     * the front one selected (V 17 openings); none: «Keine Schiffe im
     * Hafen»; a wagon train is a carrier (V #118448); not the artillery or
     * a person; neither a ship nor a wagon train in the figure row or in
     * «Vorhandene Einheiten» (V clip 040 #85 396, #87 800).
     */
    public void testTheCarrier() throws Exception {
        final State s = ClassicColonyBuildingsTest.base1539();
        s.on("expertFarmer", Direction.NW, "grain");
        s.finish();
        s.outside("artillery", null);
        assertNull(ClassicColonyPort.carrier(s.colony));
        final Unit wagon = carrier(s, "wagonTrain");
        assertSame("a wagon train alone", wagon, ClassicColonyPort.carrier(s.colony));
        final Unit ship = carrier(s, "merchantman");
        assertEquals("the ship came last: in front (V #76 135)", List.of(ship, wagon),
                     ClassicColonyPort.carriers(s.colony));
        assertTrue(ClassicColonyPort.isCarrier(ship));
        assertTrue(ClassicColonyPort.isCarrier(wagon));
        assertFalse(ClassicColonyPort.isCarrier(s.colony.getUnitList().get(0)));
        final ClassicColonyPanel p = ClassicColonyBuildingsTest.panel(s,
            ClassicColonyBuildingsTest.BASE);
        p.canvas();
        assertSame("the front one selected", ship, p.port().carrier);
        assertEquals(List.of(ship, wagon), p.port().carriers);
        assertTrue(p.port().naval());
        assertEquals("Einladen: Handelsschiff", p.port().caption);
        assertFalse("no wagon train listed (clip 040)", ClassicColonyRight.listed(s.colony, null)
                    .contains(wagon));
        assertFalse("no ship listed", ClassicColonyRight.listed(s.colony, null)
                    .contains(ship));
        for (ClassicColonyBuildings.Figure f : p.rowFigures()) {
            assertNotSame("no wagon train in the figure row", wagon, f.unit);
            assertNotSame(ship, f.unit);
        }
        assertEquals("the artillery not in the row (M4.7: the musket panel's)", 1,
                     p.rowFigures().size());
        // A wagon train arriving goes in front of the ship (V #52 642, #71 550:
        // ships are not sorted first).
        final Unit wagon2 = carrier(s, "wagonTrain");
        assertEquals(List.of(wagon2, ship, wagon), ClassicColonyPort.carriers(s.colony));
        // «Nach vorne bewegen.»: the wagon train in front, the rest keep theirs.
        ClassicColonyPort.front(s.colony, wagon);
        assertEquals(List.of(wagon, wagon2, ship), ClassicColonyPort.carriers(s.colony));
        // One leaves: the rest keep their order (V #69 042).
        wagon2.setLocation(null);
        assertEquals(List.of(wagon, ship), ClassicColonyPort.carriers(s.colony));
        p.canvas();
        assertSame("the wagon train when it is in front", wagon, p.port().carrier);
        assertFalse(p.port().naval());
        assertEquals("Einladen: Wagenzug", p.port().caption);
        ship.setLocation(null);
        wagon.setLocation(null);
        p.canvas();
        assertNull(p.port().carrier);
        assertEquals("Keine Schiffe im Hafen", p.port().caption);
    }

    /**
     * A carrier that leaves and comes back between two visits is a new
     * arrival: in front, selected at the next opening (V clip 040 ×6, the
     * review of part V, fidelity M3): the map's move off the colony's tile
     * (ClassicGUI.animateUnitMove), a refresh that sees it elsewhere
     * (ClassicGUI.refresh, e.g. in Europe); one that stays keeps its place.
     */
    public void testACarrierBackFromATripIsInFront() throws Exception {
        final State s = ClassicColonyBuildingsTest.base1539();
        s.on("expertFarmer", Direction.NW, "grain");
        s.finish();
        final Tile home = s.colony.getTile();
        Tile away = null;
        for (Tile t : home.getSurroundingTiles(1)) if (t.getColony() == null) away = t;
        assertNotNull(away);
        final Unit galleon = carrier(s, "galleon");
        final Unit wagon = carrier(s, "wagonTrain");
        // Last seen W G.
        final ClassicColonyPanel p = ClassicColonyBuildingsTest.panel(s,
            ClassicColonyBuildingsTest.BASE);
        p.canvas();
        assertEquals(List.of(wagon, galleon), p.port().carriers);
        // The galleon sails off (the map's move) and comes back unseen.
        final ClassicGUI gui = new ClassicGUI(null) {
            @Override
            protected net.sf.freecol.common.model.Game getGame() {
                return s.game;
            }
        };
        galleon.setLocation(away);
        gui.animateUnitMove(galleon, home, away);
        galleon.setLocation(home);
        ClassicColonyPanel q = ClassicColonyBuildingsTest.panel(s, ClassicColonyBuildingsTest.BASE);
        q.canvas();
        assertEquals("G* W", List.of(galleon, wagon), q.port().carriers);
        assertSame("the newcomer selected", galleon, q.port().carrier);
        // The wagon train to Europe's way and back, seen at a refresh.
        wagon.setLocation(null);
        gui.refresh();
        javax.swing.SwingUtilities.invokeAndWait(() -> { });   // the refresh's look, on the event thread
        wagon.setLocation(home);
        q = ClassicColonyBuildingsTest.panel(s, ClassicColonyBuildingsTest.BASE);
        q.canvas();
        assertEquals("W* G", List.of(wagon, galleon), q.port().carriers);
        // Nothing moved: the order kept.
        gui.refresh();
        javax.swing.SwingUtilities.invokeAndWait(() -> { });   // the refresh's look, on the event thread
        gui.animateUnitMove(galleon, away, home);
        assertEquals(List.of(wagon, galleon), ClassicColonyPort.carriers(s.colony));
    }

    /** A screen whose boxes are noted (their ids and rows), all dismissed. */
    private static ClassicColonyPanel screen(State s, List<String> asked) throws Exception {
        final ClassicColonyPanel p = new ClassicColonyPanel(
            ClassicColonyBuildingsTest.client(s.game, s.owner),
            ClassicColonyBuildingsTest.library(), s.colony, null,
            new ClassicColonyPanel.Units() {
                @Override
                public List<Unit> order(List<Unit> standing) {
                    return standing;
                }

                @Override
                public void options(Unit unit, boolean first) {
                    asked.add("options " + unit.getType().getSuffix());
                }

                @Override
                public int ask(ClassicAdvisorBox.Request r) {
                    asked.add(r.id + " " + java.util.Arrays.toString(r.plainRows()));
                    return ClassicAdvisorBox.Bar.DISMISSED;
                }
            }, ClassicSlide.SYSTEM, false);
        p.setFounding(ClassicColonyBuildingsTest.BASE);
        return p;
    }

    /**
     * Pressing a carrier (V clip 040 §1.4): the press brings the white
     * cursor and selects it (frame, caption, holds: #50 594, #50 941); a
     * click on the selected one, the front one at the opening too
     * (#69 178), opens its box «Optionen für  (Wagenzug):» (#51 068) on
     * the release; its frame white in the blink's on half, green in the
     * off half and when the cursor is elsewhere; the selection is not
     * remembered for the next screen (#61 104).
     */
    public void testTheCursorOnTheCarrier() throws Exception {
        final State s = ClassicColonyBuildingsTest.base1539();
        s.on("expertFarmer", Direction.NW, "grain");
        s.finish();
        final Unit wagon = carrier(s, "wagonTrain");
        final Unit ship = carrier(s, "merchantman");
        final List<String> asked = new ArrayList<>();
        ClassicColonyPanel.forgetFigureOrders();
        final ClassicColonyPanel p = screen(s, asked);
        final ClassicColonyChrome.Assets c = ClassicColonyChrome.Assets.load(
            ClassicPackFiles.runtime());
        final int green = c.rgb(ClassicColonyLayout.INK_GREEN);
        final int white = c.rgb(ClassicColonyLayout.INK_WHITE);
        // The ship in front (slot 0), the wagon train in slot 1.
        final Rectangle f0 = ClassicColonyLayout.carrierFrame(true, 0);
        final Rectangle f1 = ClassicColonyLayout.carrierFrame(false, 1);
        BufferedImage img = p.canvas();
        assertSame(ship, p.port().carrier);
        assertEquals("green with the cursor on a tile", green, img.getRGB(f0.x, f0.y) & 0xFFFFFF);
        // The wagon train: a press selects it, no box.
        p.clickAt(f1.x + 5, f1.y + 5);
        assertEquals(ClassicColonyPanel.CURSOR_CARRIER, ClassicColonyPanel.tileCursor());
        assertTrue(asked.toString(), asked.isEmpty());
        p.blinkPhase(false);
        img = p.canvas();
        assertSame(wagon, p.port().carrier);
        assertEquals("Einladen: Wagenzug", p.port().caption);
        assertEquals("white, on half", white, img.getRGB(f1.x, f1.y) & 0xFFFFFF);
        assertEquals(white, img.getRGB(f1.x + 17, f1.y + 17) & 0xFFFFFF);
        p.blinkPhase(true);
        img = p.canvas();
        assertEquals("green, off half", green, img.getRGB(f1.x, f1.y) & 0xFFFFFF);
        // A second click on it: its box (not in front: «Nach vorne bewegen.»).
        p.clickAt(f1.x + 5, f1.y + 5);
        assertEquals(asked.toString(), 1, asked.size());
        if (ClassicText.load(ClassicPackFiles.runtime()) != null) {
            assertEquals("COLONYUNIT [Nach vorne bewegen., Wache., Keine Veränderungen.]",
                         asked.get(0));
        }
        // A press on a tile takes the cursor there again (S3).
        p.clickAt(230, 40);
        assertEquals(0, ClassicColonyPanel.tileCursor());
        // A new screen: the front one selected again, its first click opens its box.
        final ClassicColonyPanel q = screen(s, asked);
        q.canvas();
        assertSame("not remembered (V #61 104)", ship, q.port().carrier);
        q.clickAt(f0.x + 5, f0.y + 5);
        assertEquals(asked.toString(), 2, asked.size());
    }


    // Golden against clip 020

    /** A rebuilt frame's port panel. */
    private static final class Shot {
        final int frame;
        final BufferedImage canvas;
        final String model;

        Shot(int frame, BufferedImage canvas, String model) {
            this.frame = frame;
            this.canvas = canvas;
            this.model = model;
        }
    }

    private static Shot shot(int frame, ClassicColonyPanel p, int cursor, boolean off) {
        ClassicColonyPanel.setTileCursor(cursor);
        p.blinkPhase(off);
        final BufferedImage img = p.canvas();
        return new Shot(frame, img, p.port() + " cursor=" + cursor + (off ? " off" : " on"));
    }

    /** The port region: x 119-207 (the panel and the band picture's frame lines), y 129-177. */
    static final Rectangle REGION = new Rectangle(119, 129, 89, 49);

    /** Clip 020's frames as rebuilt states. */
    private static List<Shot> shots() throws Exception {
        final List<Shot> out = new ArrayList<>();
        final int CARRIER = ClassicColonyPanel.CURSOR_CARRIER;
        // #2260 Base 1539: the Handelsschiff, empty; the cursor on the
        // centre (its frame green): 4 holds, 2 crates.  #10483 Base 1540
        // (visit 2): 24 ore, then 12 rum loaded; the cursor on the ship (on).
        {
            final State s = ClassicColonyBuildingsTest.base1539();
            s.on("expertFarmer", Direction.NW, "grain");
            s.finish();
            final Unit ship = carrier(s, "merchantman");
            final ClassicColonyPanel p = ClassicColonyBuildingsTest.panel(s,
                ClassicColonyBuildingsTest.BASE);
            out.add(shot(2260, p, ClassicColonyLayout.CENTRE_TILE, false));
            ship.addGoods(goods("ore"), 24);
            p.canvas();
            ship.addGoods(goods("rum"), 12);
            out.add(shot(10483, p, CARRIER, false));
        }
        // #14817 Provisorische Kolonie: no carrier: six crates.
        {
            final State s = new State("Provisorische Kolonie", 1541);
            s.on("seasonedScout", Direction.N, "sugar");
            s.finish();
            final ClassicColonyPanel p = ClassicColonyBuildingsTest.panel(s,
                ClassicColonyBuildingsTest.PROV);
            out.add(shot(14817, p, 1, true));
        }
        // #118448 Base 1566 (visit 21, opened by @BUILT Wagenzug): the new
        // wagon train, empty, the cursor on a figure (its frame green); then
        // 50 rum (Shift), then 18 furs; #120499 the next visit with the
        // cursor on the wagon train (on), #120521 (off: green), #120637 (the
        // next visit, the cursor on a figure: green).
        {
            final State s = new State("Base", 1566).build("lumberMill").build("docks");
            s.on("expertFarmer", Direction.NW, "grain");
            s.finish();
            final Unit wagon = carrier(s, "wagonTrain");
            final ClassicColonyPanel p = ClassicColonyBuildingsTest.panel(s,
                ClassicColonyBuildingsTest.BASE);
            out.add(shot(118448, p, ClassicColonyPanel.CURSOR_OFF_TILES, false));
            wagon.addGoods(goods("rum"), 50);
            p.canvas();
            wagon.addGoods(goods("furs"), 18);
            out.add(shot(120499, p, CARRIER, false));
            out.add(shot(120521, p, CARRIER, true));
            // #120637 (visit 22): the cursor on a figure (a unit at the fence): green.
            out.add(shot(120637, p, ClassicColonyPanel.CURSOR_OFF_TILES, false));
        }
        // #134173 Base 1571 (visit 25, opened by @CARGOREADY0): the wagon
        // train with 18 furs and 50 ore, the cursor on N (green); then the
        // furs and 20 ore unloaded: #134899 30 ore in hold 1, the cursor on
        // the wagon train (on).
        {
            final State s = new State("Base", 1571).build("lumberMill").build("docks");
            s.on("expertFarmer", Direction.NW, "grain");
            s.finish();
            final Unit wagon = carrier(s, "wagonTrain");
            wagon.addGoods(goods("furs"), 18);
            wagon.addGoods(goods("ore"), 50);
            final ClassicColonyPanel p = ClassicColonyBuildingsTest.panel(s,
                ClassicColonyBuildingsTest.BASE);
            out.add(shot(134173, p, 1, true));
            wagon.removeGoods(goods("furs"));
            wagon.removeGoods(goods("ore"), 20);
            out.add(shot(134899, p, CARRIER, false));
        }
        return out;
    }

    /**
     * Golden (M4.6 acceptance): the port panel of 9 frames of clip 020
     * rebuilt, 0 differing pixels, the arrow left out: «Einladen:
     * Handelsschiff» from x 121, «Einladen: Wagenzug», «Keine Schiffe im
     * Hafen»; the ship (#2260, #10483) and the wagon train one lower
     * (#118448 ...) with their black shadows, orange flags and «-», the
     * frame green or white; the holds: open, crates, grey ore and rum, rum
     * before furs, the close-up after the unloading.
     */
    public void testThePortAgainstClip020() throws Exception {
        final ClassicColonyGolden gold = ClassicColonyGolden.open(getName());
        if (gold == null) return;
        final StringBuilder report = new StringBuilder();
        final StringBuilder fails = new StringBuilder();
        for (Shot s : shots()) {
            final BufferedImage frame = gold.frame(s.frame);
            ClassicColonyBandTest.dump(s.canvas, frame, s.frame, "port");
            final ClassicColonyGolden.Diff d = ClassicColonyGolden.compare("port #" + s.frame,
                ClassicColonyGolden.pixels(s.canvas), ClassicColonyGolden.pixels(frame),
                REGION, gold.pointer(frame), null);
            report.append("  ").append(d).append("\n    drawn: ").append(s.model).append('\n');
            if (d.differing != 0) fails.append(' ').append(d.name).append(": ").append(d.differing);
            assertTrue(d.name, d.compared > 3500);
        }
        System.err.println(getName() + ":\n" + report);
        assertEquals("differing pixels:" + fails + "\n" + report, 0, fails.length());
    }
}
