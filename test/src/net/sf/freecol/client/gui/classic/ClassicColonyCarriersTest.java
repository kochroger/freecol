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
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import net.sf.freecol.common.model.Direction;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;

import static net.sf.freecol.client.gui.classic.ClassicColonyBuildingsTest.State;


/**
 * Several carriers in the colony's port and their box (part V, clip 040,
 * {@code port-landlocked-analysis/10-port-carriers.md}): the slots' and the
 * tiny icons' geometry, the box's rows by the carrier; then golden: the
 * port panel of clip 040's frames rebuilt (the carriers in their order,
 * the selected one's frame and holds) and their boxes, @HOWMUCH1 with a
 * wagon train and @RENAMECOLONY, drawn over the frames, 0 px apart from
 * the arrow.  The frames are the analysis' key frames
 * ({@code key/f&lt;n&gt;_*.png}, git-ignored with the recordings).
 */
public class ClassicColonyCarriersTest extends FreeColTestCase {

    private static Specification classic() {
        return spec("classic");
    }

    @Override
    protected void tearDown() throws Exception {
        ClassicColonyPanel.forgetFigureOrders();
        ClassicColonyPort.forgetOrders();
        ClassicColonyPanel.setTileCursor(ClassicColonyLayout.CENTRE_TILE);
        classic().applyDifficultyLevel("model.difficulty.medium");
        super.tearDown();
    }


    // The geometry and the rows

    /**
     * The slots (V clip 040 §1.2): x 130 + 18i; a ship's cell at y 146 in
     * slot 0 and 145 in slots 1-3, a wagon train's at 147; the galleon
     * ICONS.SS.007 (14 wide) at (132 + 18i); the frames; the tiny icons at
     * (123 + 5k, 139) with their 5x6 frames; the holds' press areas.
     */
    public void testTheGeometry() {
        assertEquals(new Rectangle(130, 146, 16, 16), ClassicColonyLayout.carrierCell(true, 0));
        assertEquals(new Rectangle(148, 145, 16, 16), ClassicColonyLayout.carrierCell(true, 1));
        assertEquals(new Rectangle(184, 145, 16, 16), ClassicColonyLayout.carrierCell(true, 3));
        assertEquals(new Rectangle(166, 147, 16, 16), ClassicColonyLayout.carrierCell(false, 2));
        assertEquals("galleon at 132", 132, 130 + ClassicHud.spriteOffset(14));
        assertEquals("ship frame (129,145)-(146,162)", new Rectangle(129, 145, 18, 18),
                     ClassicColonyLayout.carrierFrame(true, 0));
        assertEquals("#62 060 (147,144)-(164,161)", new Rectangle(147, 144, 18, 18),
                     ClassicColonyLayout.carrierFrame(true, 1));
        assertEquals("#66 399 (183,146)-(200,163)", new Rectangle(183, 146, 18, 18),
                     ClassicColonyLayout.carrierFrame(false, 3));
        assertEquals(new Rectangle(123, 139, 4, 4), ClassicColonyLayout.tinyIcon(0));
        assertEquals(new Rectangle(128, 139, 4, 4), ClassicColonyLayout.tinyIcon(1));
        assertEquals(new Rectangle(123, 138, 5, 6), ClassicColonyLayout.tinyFrame(0));
        assertEquals(new Rectangle(128, 138, 5, 6), ClassicColonyLayout.tinyFrame(1));
        assertEquals(0, ClassicColonyLayout.holdAt(127, 165));
        assertEquals(5, ClassicColonyLayout.holdAt(196, 176));
        assertEquals(-1, ClassicColonyLayout.holdAt(126, 170));
        assertEquals(-1, ClassicColonyLayout.holdAt(130, 162));
        assertEquals(-1, ClassicColonyLayout.holdAt(199, 170));
    }

    private static Unit carrier(State s, String type) {
        final UnitType ut = classic().getUnitType("model.unit." + type);
        return new ServerUnit(s.game, s.colony.getTile(), s.owner, ut);
    }

    /**
     * The box's rows by the carrier (V clip 040 §1.5, five boxes): a
     * galleon in slot 1, empty: Nach vorne / Wache / Im Hafen ankern /
     * Keine (#50 654); in slot 0, loaded: Wache / ankern / Alle Waren /
     * Keine (#69 235); in slot 1, loaded: all five (#72 028); a wagon
     * train in slot 2 with cotton: Nach vorne / Wache / Alle Waren / Keine
     * (#51 100); empty in slot 3: Nach vorne / Wache / Keine (#51 450).
     */
    public void testTheBoxRows() {
        final State s = ClassicColonyBuildingsTest.base1539();
        s.on("expertFarmer", Direction.NW, "grain");
        s.finish();
        final Unit g = carrier(s, "galleon");
        final Unit w = carrier(s, "wagonTrain");
        final GoodsType cotton = classic().getGoodsType("model.goods.cotton");
        assertEquals("[FRONT, SENTRY, ANCHOR, NOTHING]",
                     ClassicColonyUnits.carrierOptions(g, false).toString());
        g.addGoods(cotton, 100);
        assertEquals("[SENTRY, ANCHOR, UNLOAD, NOTHING]",
                     ClassicColonyUnits.carrierOptions(g, true).toString());
        assertEquals("[FRONT, SENTRY, ANCHOR, UNLOAD, NOTHING]",
                     ClassicColonyUnits.carrierOptions(g, false).toString());
        assertEquals("[FRONT, SENTRY, NOTHING]",
                     ClassicColonyUnits.carrierOptions(w, false).toString());
        w.addGoods(cotton, 20);
        assertEquals("[FRONT, SENTRY, UNLOAD, NOTHING]",
                     ClassicColonyUnits.carrierOptions(w, false).toString());
        g.setState(Unit.UnitState.SENTRY);
        assertEquals("with orders: «Befehle aufheben.» (I)", "[CLEAR, SENTRY, ANCHOR, UNLOAD, "
                     + "NOTHING]", ClassicColonyUnits.carrierOptions(g, true).toString());
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        if (t == null) return;
        final ClassicAdvisorBox.Request r = ClassicColonyUnits.carrierRequest(t, w,
            ClassicColonyUnits.carrierOptions(w, false), null, "x");
        // The box's two spaces (no expert part) are one in the plain text; the
        // pixels: testTheBoxesAgainstClip040.
        assertEquals("Optionen für (Wagenzug):", r.plainText());
        assertEquals("[Nach vorne bewegen., Wache., Alle Waren entladen., "
                     + "Keine Veränderungen.]", java.util.Arrays.toString(r.plainRows()));
        assertEquals(0, r.defaultRow);
        assertEquals("Escape: «Keine Veränderungen.»", 3, r.cancelRow);
    }


    // Golden against clip 040

    /** The clip's key frames. */
    private static final String KEY = "port-landlocked-analysis/key";

    /** A key frame of clip 040, or null without it. */
    private static BufferedImage key(ClassicColonyGolden gold, int n) throws IOException {
        final File[] fs = new File(gold.recordings, KEY).listFiles();
        if (fs == null) return null;
        final String prefix = String.format("f%06d_", n);
        for (File f : fs) {
            if (f.getName().startsWith(prefix)) return ImageIO.read(f);
        }
        return null;
    }

    /** A rebuilt port. */
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

    /**
     * A colony with carriers arriving in this order (the newest in front),
     * then put in the order given (a list of indices into the arrivals,
     * front first; null: the arrival order).
     */
    private static final class Port {
        final State s;
        final List<Unit> arrived = new ArrayList<>();
        ClassicColonyPanel p;

        Port(String... types) {
            this.s = new State("Cotton Town", 1730);
            this.s.on("expertFarmer", Direction.NW, "grain");
            this.s.finish();
            ClassicColonyPort.forgetOrders();
            for (String t : types) this.arrived.add(carrier(this.s, t));
        }

        Port goods(int i, String goods, int n) {
            this.arrived.get(i).addGoods(classic().getGoodsType("model.goods." + goods), n);
            return this;
        }

        /** The port order, front first, as indices into the arrivals. */
        Port order(int... front) {
            for (int k = front.length - 1; k >= 0; k--) {
                ClassicColonyPort.front(this.s.colony, this.arrived.get(front[k]));
            }
            return this;
        }

        Shot shot(int frame, int selected, boolean white) throws Exception {
            if (this.p == null) {
                this.p = ClassicColonyBuildingsTest.panel(this.s, ClassicColonyBuildingsTest.BASE);
            }
            this.p.canvas();
            if (selected >= 0) {
                final Unit u = this.arrived.get(selected);
                final java.lang.reflect.Field f = ClassicColonyPanel.class
                    .getDeclaredField("selectedCarrier");
                f.setAccessible(true);
                f.set(this.p, u);
            }
            ClassicColonyPanel.setTileCursor((white) ? ClassicColonyPanel.CURSOR_CARRIER
                                             : ClassicColonyLayout.CENTRE_TILE);
            this.p.blinkPhase(false);
            final BufferedImage img = this.p.canvas();
            return new Shot(frame, img, String.valueOf(this.p.port()));
        }
    }

    /** Clip 040's port frames as rebuilt states. */
    private static List<Shot> shots() throws Exception {
        final List<Shot> out = new ArrayList<>();
        // #46 948: G W W (the galleon came after the two wagons), the
        // galleon selected (green), empty.
        out.add(new Port("wagonTrain", "wagonTrain", "galleon").shot(46948, -1, false));
        // #48 385: G G W W, the front galleon selected.
        out.add(new Port("wagonTrain", "wagonTrain", "galleon", "galleon")
                .shot(48385, -1, false));
        // #50 594: the same, slot 1 selected by a click (white).
        out.add(new Port("wagonTrain", "wagonTrain", "galleon", "galleon")
                .shot(50594, 2, true));
        // #50 941: G G W(2 cotton holds) W, the wagon in slot 2 selected (white).
        out.add(new Port("wagonTrain", "wagonTrain", "galleon", "galleon")
                .goods(1, "cotton", 200).order(3, 2, 1, 0).shot(50941, 1, true));
        // #52 642: W(1 cotton) W W(2 cotton) G + a tiny galleon; the front
        // wagon selected (white).
        out.add(new Port("galleon", "galleon", "wagonTrain", "wagonTrain", "wagonTrain")
                .goods(4, "cotton", 100).goods(2, "cotton", 200).order(4, 3, 2, 1, 0)
                .shot(52642, -1, true));
        // #52 909 / #53 000: the tiny galleon selected, white / green.
        {
            final Port pt = new Port("galleon", "galleon", "wagonTrain", "wagonTrain",
                                     "wagonTrain")
                .goods(4, "cotton", 100).goods(2, "cotton", 200).order(4, 3, 2, 1, 0);
            out.add(pt.shot(52909, 0, true));
            out.add(pt.shot(53000, 0, false));
        }
        // #61 150: G G W(2 cotton) W + tiny W(1 cotton), tiny G (the order
        // the rule does not explain, I); the front galleon selected (green).
        out.add(new Port("galleon", "galleon", "wagonTrain", "wagonTrain", "wagonTrain",
                         "galleon")
                .goods(2, "cotton", 200).goods(4, "cotton", 100).order(5, 1, 2, 3, 4, 0)
                .shot(61150, -1, false));
        return out;
    }

    /**
     * Golden (part V acceptance, clip 040): the port panel (x 119-207, y
     * 129-177) of 8 key frames rebuilt, 0 differing pixels, the arrow left
     * out: up to four carriers full size 18 px apart, a ship one pixel
     * higher outside slot 0, the tiny icons above-left, the selected one's
     * frame (green, white) on a full or a tiny carrier, its caption and its
     * holds.
     */
    public void testThePortAgainstClip040() throws Exception {
        final ClassicColonyGolden gold = ClassicColonyGolden.open(getName());
        if (gold == null) return;
        if (key(gold, 46948) == null) {
            System.err.println(getName() + ": no clip 040 key frames, skipped");
            return;
        }
        final StringBuilder report = new StringBuilder();
        final StringBuilder fails = new StringBuilder();
        for (Shot s : shots()) {
            final BufferedImage frame = key(gold, s.frame);
            assertNotNull("#" + s.frame, frame);
            final ClassicColonyGolden.Diff d = ClassicColonyGolden.compare("port #" + s.frame,
                ClassicColonyGolden.pixels(s.canvas), ClassicColonyGolden.pixels(frame),
                ClassicColonyPortTest.REGION, gold.pointer(frame), null);
            report.append("  ").append(d).append("\n    drawn: ").append(s.model).append('\n');
            if (d.differing != 0) fails.append(' ').append(d.name).append(": ").append(d.differing);
            assertTrue(d.name, d.compared > 3000);
        }
        System.err.println(getName() + ":\n" + report);
        assertEquals("differing pixels:" + fails + "\n" + report, 0, fails.length());
    }

    /**
     * The boxes over clip 040 (0 px on the box): the carriers' «Optionen
     * für  (Galeone):» / «(Wagenzug):» with their icon at box + (5,6)
     * (#50 654 4 rows (31,72,258,56), #69 235, #72 028 5 rows (31,68,258,64),
     * #51 100, #51 450 3 rows (31,76,258,48)); @HOWMUCH1 «Wieviel
     * {Werkzeuge} auf Wagenzug laden (0-100)?» preset 100 (#73 655);
     * @RENAMECOLONY with «Cotton Town» (#48 939, box (71,82,178,37)).
     */
    public void testTheBoxesAgainstClip040() throws Exception {
        final ClassicColonyGolden gold = ClassicColonyGolden.open(getName());
        if (gold == null) return;
        if (key(gold, 50654) == null) {
            System.err.println(getName() + ": no clip 040 key frames, skipped");
            return;
        }
        final ClassicText t = ClassicText.load(gold.pack);
        final ClassicFont tiny = gold.pack.font(ClassicFont.TINY);
        final BufferedImage wood = gold.pack.image(ClassicMenuBar.WOOD_KEY);
        final ClassicColonyBuildings.Assets a = ClassicColonyBuildings.Assets.load(gold.pack);
        final State s = ClassicColonyBuildingsTest.base1539();
        s.on("expertFarmer", Direction.NW, "grain");
        s.finish();
        final Unit g = carrier(s, "galleon");
        final Unit w = carrier(s, "wagonTrain");
        final GoodsType cotton = classic().getGoodsType("model.goods.cotton");
        final StringBuilder out = new StringBuilder();
        int bad = 0;
        final Object[][] cases = {
            { 50654, g, false, 0, new Rectangle(31, 72, 258, 56) },
            { 69235, g, true, 100, new Rectangle(31, 72, 258, 56) },
            { 72028, g, false, 100, new Rectangle(31, 68, 258, 64) },
            { 51100, w, false, 100, new Rectangle(31, 72, 258, 56) },
            { 51450, w, false, 0, new Rectangle(31, 76, 258, 48) },
        };
        for (Object[] c : cases) {
            final Unit u = (Unit) c[1];
            for (Unit x : new Unit[] { g, w }) x.removeGoods(cotton);
            if ((Integer) c[3] > 0) u.addGoods(cotton, (Integer) c[3]);
            final ClassicAdvisorBox.Request r = ClassicColonyUnits.carrierRequest(t, u,
                ClassicColonyUnits.carrierOptions(u, (Boolean) c[2]),
                ClassicColonyPanel.carrierIcon(a, t, u), "x");
            final ClassicAdvisorBox.Layout l = ClassicAdvisorBox.layout(r, tiny, null);
            assertEquals("#" + c[0], c[4], l.box);
            final int[] got = ClassicColonyMovesTest.overFrame(gold, key(gold, (Integer) c[0]), l,
                0, wood, tiny);
            out.append(" #").append(c[0]).append(' ').append(got[0]).append('/').append(got[1])
                .append(" arrow ").append(got[2]);
            bad += got[1];
        }
        // @HOWMUCH1, a wagon train, tools (#73 655).
        final ClassicAdvisorBox.Request hm = ClassicColonyGoods.howMuch(t, true,
            classic().getGoodsType("model.goods.tools"), "Wagenzug", "Cotton Town", 100);
        final ClassicAdvisorBox.Layout hl = ClassicAdvisorBox.layout(hm, tiny, null);
        assertEquals(new Rectangle(42, 82, 236, 37), hl.box);
        final int[] h = ClassicColonyMovesTest.overFrame(gold, key(gold, 73655), hl, -1, wood,
                                                         tiny);
        out.append(" @HOWMUCH1 #73655 ").append(h[0]).append('/').append(h[1])
            .append(" arrow ").append(h[2]);
        bad += h[1];
        // @RENAMECOLONY (#48 939).
        final ClassicAdvisorBox.Request rn = ClassicColonyPanel.renameRequest(t, "Cotton Town");
        final ClassicAdvisorBox.Layout rl = ClassicAdvisorBox.layout(rn, tiny, null);
        assertEquals(new Rectangle(71, 82, 178, 37), rl.box);
        final int[] n = ClassicColonyMovesTest.overFrame(gold, key(gold, 48939), rl, -1, wood,
                                                         tiny);
        out.append(" @RENAMECOLONY #48939 ").append(n[0]).append('/').append(n[1])
            .append(" arrow ").append(n[2]);
        bad += n[1];
        System.out.println(getName() + ":" + out);
        assertEquals("pixels off:" + out, 0, bad);
    }
}
