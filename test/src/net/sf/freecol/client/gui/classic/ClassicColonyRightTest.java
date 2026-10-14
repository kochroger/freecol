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
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.common.model.BuildableType;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Direction;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;

import static net.sf.freecol.client.gui.classic.ClassicColonyBuildingsTest.State;
import static net.sf.freecol.client.gui.classic.ClassicColonyRight.Mode;


/**
 * The colony screen's right panel (build spec M4.7; {@link
 * ClassicColonyRight}, {@link ClassicColonyLayout}): the house rows' layout
 * against every row measured (clip 020's three panels, dago's stacked
 * one), the hammer rows' offsets, the musket panel's cells, the mode
 * buttons' places, the mode at an opening; the model of a test colony
 * (what it makes, its shortfalls crossed, its build, its units); the
 * presses (a mode button's press and release, a listed unit's box,
 * ÄNDERN's build list); then golden: the states of clip 020's frames
 * (house #2260, #14817, #123003; hammer #9921, #134173, #105810 / #105830
 * with the cursor on ÄNDERN, #130584, #93990), dago's (house #1000, musket
 * #6000, hammer #6400, #11400) and clip 019's (musket #1290, #700) rebuilt
 * in a test game and the right panel with the mode buttons (x 208-319, y
 * 129-177) compared with the frame pixel for pixel, the arrow left out.
 * Skips the golden part without the pack or the recordings
 * ({@code -Dclassic.clips}).
 */
public class ClassicColonyRightTest extends FreeColTestCase {

    private static Specification classic() {
        return spec("classic");
    }

    @Override
    protected void tearDown() throws Exception {
        ClassicColonyPanel.forgetFigureOrders();
        ClassicColonyPort.forgetOrders();
        ClassicColonyPanel.selectWarehouseGood(0);
        ClassicColonyPanel.setTileCursor(ClassicColonyLayout.CENTRE_TILE);
        ClassicColonyPanel.setLastMode(Mode.HOUSE);
        classic().applyDifficultyLevel("model.difficulty.medium");
        super.tearDown();
    }

    private static GoodsType goods(String id) {
        return classic().getGoodsType("model.goods." + id);
    }

    /** {first, pitch, count} as the icons' x. */
    private static int[] xs(int first, int pitch, int n) {
        final int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = first + i * pitch;
        return out;
    }

    private static int[] w(String... ids) {
        final ClassicColonyBuildings.Assets a = ClassicColonyBuildings.Assets.load(
            ClassicPackFiles.runtime());
        final int[] out = new int[ids.length];
        for (int i = 0; i < ids.length; i++) {
            final BufferedImage img = a.icon(ClassicColonyLayout.goodsIcon("model.goods." + ids[i]));
            out[i] = (img == null) ? -1 : img.getWidth();
        }
        return out;
    }


    // The layout (pure)

    /**
     * The house rows against every row measured (L §7.2, dago §6.1): clip
     * 020 #2260 (sugar p 9, rum p 8, lumber 8 at p 2, 4 crossed after 4 px,
     * 12 hammers after 4 px), #14817 (the horse and the crossed horse at
     * 14), #123003 (furs p 10, 2 px, silver p 11); dago #1000 (k 2: 17
     * tobacco and 7 crossed at pitch 1 from 213, cotton «5» from 246, ore
     * from 259; k 4: 3 crossed horses, cigars from 230, tools from 258,
     * muskets from 280; k 2: lumber 15, 1 crossed at 241, hammers from 255),
     * the labels at first + 2.
     */
    public void testTheHouseRows() {
        // The icons' widths: sugar 8, rum 7, lumber 10, hammers 8, horses 13,
        // furs 9, silver 10, tobacco 8, cotton 10, ore 10, cigars 9, tools 9,
        // muskets 6 (the pack's; fixed here so the layout is checked alone).
        ClassicColonyLayout.HouseRow r = ClassicColonyLayout.houseRow(
            new int[] { 3 }, new int[] { 0 }, new int[] { 8 }, 0);
        assertTrue(Arrays.equals(xs(213, 9, 3), r.x[0][0]));
        r = ClassicColonyLayout.houseRow(new int[] { 3 }, new int[] { 0 }, new int[] { 7 }, 1);
        assertTrue(Arrays.equals(xs(213, 8, 3), r.x[0][0]));
        r = ClassicColonyLayout.houseRow(new int[] { 8, 12 }, new int[] { 4, 0 },
                                          new int[] { 10, 8 }, 2);
        assertEquals("#2260 row 3: pitch 2", 2, r.pitch);
        assertTrue(Arrays.equals(xs(213, 2, 8), r.x[0][0]));
        assertTrue("4 px, the crossed", Arrays.equals(xs(241, 2, 4), r.x[0][1]));
        assertTrue("4 px, the hammers", Arrays.equals(xs(261, 2, 12), r.x[1][0]));
        assertEquals(290, r.end);
        assertFalse(r.labelled[0][0] || r.labelled[0][1] || r.labelled[1][0]);
        assertTrue("pitch 3 would end at 311",
                   ClassicColonyLayout.houseRowAt(new int[] { 8, 12 }, new int[] { 4, 0 },
                       new int[] { 10, 8 }, 2, 1, 3).end > ClassicColonyLayout.HOUSE_RIGHT);
        r = ClassicColonyLayout.houseRow(new int[] { 1 }, new int[] { 1 }, new int[] { 13 }, 1);
        assertTrue("#14817 the horse", Arrays.equals(new int[] { 213 }, r.x[0][0]));
        assertTrue("the crossed one at 14", Arrays.equals(new int[] { 227 }, r.x[0][1]));
        r = ClassicColonyLayout.houseRow(new int[] { 3, 3 }, new int[] { 0, 0 },
                                          new int[] { 9, 10 }, 0);
        assertTrue("#123003 furs", Arrays.equals(xs(213, 10, 3), r.x[0][0]));
        assertTrue("2 px, silver", Arrays.equals(xs(244, 11, 3), r.x[1][0]));
        // dago #1000 row 1: tobacco 34 + 14 crossed, cotton 5, ore 32.
        r = ClassicColonyLayout.houseRow(new int[] { 34, 5, 32 }, new int[] { 14, 0, 0 },
                                          new int[] { 8, 10, 10 }, 0);
        assertEquals("dago row 1: k 2", 2, r.k);
        assertTrue(Arrays.equals(xs(213, 1, 17), r.x[0][0]));
        assertTrue("the crossed go on", Arrays.equals(xs(230, 1, 7), r.x[0][1]));
        assertTrue("cotton 5 -> 2", Arrays.equals(xs(246, 1, 2), r.x[1][0]));
        assertTrue(Arrays.equals(xs(259, 1, 16), r.x[2][0]));
        assertTrue(r.labelled[0][0] && r.labelled[0][1] && r.labelled[1][0] && r.labelled[2][0]);
        assertEquals(215, r.labelX[0][0]);
        assertEquals(232, r.labelX[0][1]);
        assertEquals(248, r.labelX[1][0]);
        // Row 2: 14 crossed horses, cigars 72, tools 48, muskets 48.
        r = ClassicColonyLayout.houseRow(new int[] { 0, 72, 48, 48 },
            new int[] { 14, 0, 0, 0 }, new int[] { 13, 9, 9, 6 }, 1);
        assertEquals("dago row 2: k 4", 4, r.k);
        assertEquals(0, r.x[0][0].length);
        assertTrue(Arrays.equals(xs(213, 1, 3), r.x[0][1]));
        assertTrue(Arrays.equals(xs(230, 1, 18), r.x[1][0]));
        assertTrue(Arrays.equals(xs(258, 1, 12), r.x[2][0]));
        assertTrue(Arrays.equals(xs(280, 1, 12), r.x[3][0]));
        // Row 3: lumber 30 + 2 crossed, hammers 32.
        r = ClassicColonyLayout.houseRow(new int[] { 30, 32 }, new int[] { 2, 0 },
                                          new int[] { 10, 8 }, 2);
        assertEquals("dago row 3: k 2", 2, r.k);
        assertTrue(Arrays.equals(xs(213, 1, 15), r.x[0][0]));
        assertTrue("its own group", Arrays.equals(new int[] { 241 }, r.x[0][1]));
        assertTrue(Arrays.equals(xs(255, 1, 16), r.x[1][0]));
        // The pack's widths are the ones used above.
        final int[] pw = w("sugar", "rum", "lumber", "hammers", "horses", "furs", "silver",
                           "tobacco", "cotton", "ore", "cigars", "tools", "muskets");
        if (pw[0] > 0) {
            assertTrue(Arrays.toString(pw), Arrays.equals(
                new int[] { 8, 7, 10, 8, 13, 9, 10, 8, 10, 10, 9, 9, 6 }, pw));
        }
    }

    /**
     * The hammer rows (spec M4.7 target 4, §0.1 decision 1): the table for
     * q 10, 13, 20, 30, 48 (dago #6400: floor(j·82/48), measured anew); the
     * count label only below pitch 2; the rows' counts.
     */
    public void testTheHammerRows() {
        final int[] q10 = { 0, 9, 18, 27, 36, 45, 54, 63, 72, 81 };
        for (int j = 0; j < 10; j++) assertEquals(q10[j], ClassicColonyLayout.hammerOffset(10, j));
        final int[] q13 = { 0, 6, 13, 20, 26, 33, 40, 46, 53, 60, 66, 73, 80 };
        for (int j = 0; j < 13; j++) assertEquals(q13[j], ClassicColonyLayout.hammerOffset(13, j));
        assertEquals("q 20, j 4 (the formula's 16)", 17, ClassicColonyLayout.hammerOffset(20, 4));
        assertEquals(34, ClassicColonyLayout.hammerOffset(20, 8));
        assertEquals(80, ClassicColonyLayout.hammerOffset(20, 19));
        assertEquals("q 30, j 17", 47, ClassicColonyLayout.hammerOffset(30, 17));
        assertEquals(80, ClassicColonyLayout.hammerOffset(30, 29));
        // q 48 (dago #6400, S5): floor(j·82/48), not the spec's floor(j·218/128).
        int off = 0;
        for (int j = 0; j < 48; j++) {
            assertEquals("q 48 (dago)", j * 82 / 48, ClassicColonyLayout.hammerOffset(48, j));
            if (j * 218 / 128 != j * 82 / 48) off++;
        }
        assertEquals("the spec's formula: 6 one off", 6, off);
        assertEquals("q 16 (I): S 683", 15 * 683 / 128, ClassicColonyLayout.hammerOffset(16, 15));
        assertFalse(ClassicColonyLayout.hammerLabelled(30));
        assertFalse(ClassicColonyLayout.hammerLabelled(13));
        assertTrue(ClassicColonyLayout.hammerLabelled(48));
        assertEquals(13, ClassicColonyLayout.hammerRowCount(36, 13, 0));
        assertEquals(10, ClassicColonyLayout.hammerRowCount(36, 13, 2));
        assertEquals(0, ClassicColonyLayout.hammerRowCount(36, 13, 3));
        assertEquals(6, ClassicColonyLayout.hammerRowCount(36, 10, 3));
        assertEquals(2, ClassicColonyLayout.hammerRowCount(42, 20, 2));
    }

    /**
     * The places: the mode buttons' sprites and cells, the list's cells and
     * frames (V clip 019: 212-229; dago 213 ... 285), KAUFEN and ÄNDERN.
     */
    public void testThePlaces() {
        assertEquals(new java.awt.Point(303, 147), ClassicColonyLayout.modeSprite(1, false));
        assertEquals(new java.awt.Point(304, 133), ClassicColonyLayout.modeSprite(0, true));
        assertEquals(new java.awt.Point(304, 163), ClassicColonyLayout.modeSprite(2, true));
        assertEquals(0, ClassicColonyLayout.modeAt(310, 140));
        assertEquals(1, ClassicColonyLayout.modeAt(303, 147));
        assertEquals(2, ClassicColonyLayout.modeAt(317, 175));
        assertEquals(-1, ClassicColonyLayout.modeAt(310, 146));
        assertEquals(-1, ClassicColonyLayout.modeAt(302, 140));
        for (int n = 1; n <= 5; n++) assertEquals(18, ClassicColonyLayout.unitListPitch(n));
        assertTrue(ClassicColonyLayout.unitListPitch(6) < 18);
        for (int n = 2; n <= 40; n++) {
            final Rectangle last = ClassicColonyLayout.unitListCell(n - 1, n);
            assertTrue(n + ": " + last, last.x + 15 <= ClassicColonyLayout.HOUSE_RIGHT);
        }
        assertEquals(new Rectangle(285, 158, 16, 16), ClassicColonyLayout.unitListCell(4, 5));
        assertEquals(new Rectangle(212, 157, 18, 18),
                     ClassicColonyLayout.unitListFrame(ClassicColonyLayout.unitListCell(0, 4)));
        assertEquals(new Rectangle(216, 138, 29, 9), ClassicColonyLayout.BUY_BOX);
        assertEquals(298, ClassicColonyLayout.CHANGE_BOX.x + ClassicColonyLayout.CHANGE_BOX.width - 1);
    }

    /** The mode at an opening (spec §0.1 decision 8). */
    public void testTheModeAtAnOpening() {
        for (Mode last : Mode.values()) {
            assertEquals(Mode.HOUSE,
                ClassicColonyRight.modeAtOpening(ClassicColonyRight.Opening.FOUNDING, last));
            assertEquals(Mode.HAMMER, ClassicColonyRight.modeAtOpening(
                ClassicColonyRight.Opening.BUILT_BUILDING, last));
            assertEquals(Mode.HAMMER, ClassicColonyRight.modeAtOpening(
                ClassicColonyRight.Opening.BUILT_UNIT, last));
            assertEquals(Mode.HAMMER, ClassicColonyRight.modeAtOpening(
                ClassicColonyRight.Opening.NEED_TOOLS, last));
            assertEquals(Mode.HOUSE,
                ClassicColonyRight.modeAtOpening(ClassicColonyRight.Opening.ORE, last));
            assertEquals(last,
                ClassicColonyRight.modeAtOpening(ClassicColonyRight.Opening.OTHER, last));
        }
        assertEquals(Mode.HOUSE, ClassicColonyRight.modeAtOpening(null, null));
    }


    /**
     * The inks of the panel in the game palette (V, the frames' colours):
     * the buttons' shadow 63 #08086D and face 54 #698AC3, the boxes' light
     * edge 48 #DBEFFF and dark one 57 #4159A6, the red label 12 #FF0000.
     */
    public void testTheInks() {
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        if (pack == null || pack.gamePalette() == null) return;
        final ClassicColonyChrome.Assets c = ClassicColonyChrome.Assets.load(pack);
        assertEquals(0x08086D, c.rgb(ClassicColonyLayout.INK_BUTTON_SHADOW) & 0xFFFFFF);
        assertEquals(0x698AC3, c.rgb(ClassicColonyLayout.INK_BUTTON_FACE) & 0xFFFFFF);
        assertEquals(0xDBEFFF, c.rgb(ClassicColonyLayout.INK_BOX_LIGHT) & 0xFFFFFF);
        assertEquals(0x4159A6, c.rgb(ClassicColonyLayout.INK_CAPTION) & 0xFFFFFF);
        assertEquals(0xFF0000, c.rgb(ClassicColonyLayout.INK_RED) & 0xFFFFFF);
        // The button sprites are 14x13 (their bevel in them).
        final ClassicColonyBuildings.Assets a = ClassicColonyBuildings.Assets.load(pack);
        for (int i = 0; i < 3; i++) {
            final BufferedImage s = a.icon(ClassicColonyLayout.ICON_MODE_HOUSE + i);
            assertEquals(14, s.getWidth());
            assertEquals(13, s.getHeight());
        }
    }

    /**
     * The house rows' count labels follow «Beschriftung an Waren und
     * Terrain» (I, as the band's and the tiles'): dago's «34» box at (215,
     * 136) with the option, gone without it; the hammer rows' «48» stays (I:
     * a count of the build, not a goods label).
     */
    public void testTheLabelsFollowTheOption() throws Exception {
        final State s = ClassicColonyBuildingsTest.base(1729);
        s.on("expertFarmer", Direction.NW, "grain");
        s.finish();
        final ClassicColonyPanel p = ClassicColonyBuildingsTest.panel(s,
            ClassicColonyBuildingsTest.BASE);
        p.setMode(Mode.HOUSE);
        house(p, "tobacco 34 14", "cotton 5", "ore 32");
        final java.lang.reflect.Field shared = ClassicPrefs.class.getDeclaredField("shared");
        shared.setAccessible(true);
        final Object was = shared.get(null);
        final ClassicPrefs prefs = new ClassicPrefs(null);
        shared.set(null, prefs);
        try {
            prefs.set(ClassicPrefs.GOODS_TERRAIN_LABELS, true);
            final BufferedImage on = p.canvas();
            prefs.set(ClassicPrefs.GOODS_TERRAIN_LABELS, false);
            final BufferedImage off = p.canvas();
            int differ = 0;
            for (int y = 136; y < 143; y++) {
                for (int x = 215; x < 215 + 9; x++) {
                    if (on.getRGB(x, y) != off.getRGB(x, y)) differ++;
                }
            }
            assertTrue("«34» gone without the option: " + differ, differ > 10);
            p.setRightAdjust(null);
            p.setMode(Mode.HAMMER);
            build(s, unitType("artillery"), 96, 44);
            final BufferedImage h = p.canvas();
            int black = 0;
            for (int x = 214; x <= 222; x++) if ((h.getRGB(x, 148) & 0xFFFFFF) == 0) black++;
            assertEquals("the hammer row's «48» box stays", 9, black);
        } finally {
            shared.set(null, was);
        }
    }


    // The model of a test colony

    /** A unit of a type, with a role, outside the colony on its tile. */
    private static Unit outside(State s, String type, String role) {
        return s.outside(type, role);
    }

    /**
     * The house panel of a test colony (FreeCol's numbers): the Sägewerk's
     * shortfall crossed whatever the stock (V #2260), the hammers made from
     * the stock or crossed without it (V clip008), the distiller's sugar
     * and rum; the horses' breeding short of food crossed; food never.
     */
    public void testTheHouseModel() {
        final State s = ClassicColonyBuildingsTest.base1539();
        s.in("masterCarpenter", "lumberMill");
        s.in("masterSugarPlanter", "distillerHouse");
        s.on("expertFarmer", Direction.NW, "grain");
        s.finish();
        final ClassicColonyBuildings.Assets a = ClassicColonyBuildings.Assets.load(
            ClassicPackFiles.runtime());
        // No lumber, no sugar: 12 lumber and 12 hammers crossed, 3 sugar and 3 rum.
        ClassicColonyRight.House h = ClassicColonyRight.house(s.colony, a);
        assertEquals(h.toString(), 0, h.made[2][0]);
        assertEquals("lumber short", 12, h.crossed[2][0]);
        assertEquals("hammers not made", 12, h.crossed[2][1]);
        assertEquals("sugar short", 3, h.crossed[0][0]);
        assertEquals("rum not made", 3, h.crossed[1][1]);
        // 100 lumber in stock: the hammers made, the 12 lumber still short (#2260).
        s.colony.addGoods(goods("lumber"), 100);
        s.colony.invalidateCache();
        h = ClassicColonyRight.house(s.colony, a);
        assertEquals(h.toString(), 12, h.made[2][1]);
        assertEquals(0, h.crossed[2][1]);
        assertEquals(12, h.crossed[2][0]);
        // The centre's cotton (FreeCol's plains): row 1, made, nothing crossed.
        assertEquals(h.toString(), 2, h.made[0][2]);
        assertEquals(0, h.crossed[0][2]);
        // A lumberjack on a mixed forest: the lumber made counts whole (what
        // the tile makes, V #2260's 8), not net of the Sägewerk's use; the
        // rest of the 12 crossed.
        s.colony.getTile().getNeighbourOrNull(Direction.N)
            .setType(classic().getTileType("model.tile.mixedForest"));
        s.work(s.outside("freeColonist", null), Direction.N, "lumber");
        h = ClassicColonyRight.house(s.colony, a);
        assertTrue(h.toString(), h.made[2][0] > 0 && h.made[2][0] < 12);
        assertEquals(h.toString(), 12 - h.made[2][0], h.crossed[2][0]);
        // Many horses, little food: the breeding's rest crossed.
        s.colony.addGoods(goods("horses"), 52);
        s.colony.invalidateCache();
        h = ClassicColonyRight.house(s.colony, a);
        assertTrue(h.toString(), h.made[1][0] > 0);
        // The test adjust: only the clip's amounts.
        final ClassicColonyRight.House c = h.only("lumber 8 4", "hammers 12", "sugar 3", "rum 3");
        assertEquals("row1[sugar 3] k1 p9 | 3@213 row2[rum 3] k1 p8 | 3@213 row3[lumber 8x4"
                     + " hammers 12] k1 p2 | 8@213 x4@241 | 12@261", c.toString());
    }

    /**
     * The hammer panel of a test colony: the build's NAMES name and its
     * hammers and tools (FreeCol's costs, the original's for these),
     * «(Benötigt 20 Werkzeuge)» white below the stock, grey at it;
     * «(Keine Produktion)» without a build; a unit's NAMES name.
     */
    public void testTheHammerModel() {
        final State s = ClassicColonyBuildingsTest.base1539();
        s.on("expertFarmer", Direction.NW, "grain");
        s.finish();
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        ClassicColonyRight.Hammer h = ClassicColonyRight.hammer(s.colony, t);
        assertNull(h.tools);
        assertEquals(0, h.cost);
        if (t != null) assertEquals("(Keine Produktion)", h.caption);
        s.colony.setBuildQueue(new ArrayList<BuildableType>(
            List.of(classic().getBuildingType("model.building.printingPress"))));
        s.colony.addGoods(goods("hammers"), 36);
        s.colony.addGoods(goods("tools"), 10);
        h = ClassicColonyRight.hammer(s.colony, t);
        assertEquals(52, h.cost);
        assertEquals(13, h.q());
        assertEquals(36, h.stored);
        assertEquals(ClassicColonyLayout.INK_WHITE, h.toolsInk);
        if (t != null) {
            assertEquals("Verlag", h.caption);
            assertEquals("(Benötigt 20 Werkzeuge)", h.tools);
        }
        s.colony.addGoods(goods("tools"), 10);
        assertEquals(ClassicColonyLayout.INK_GREY, ClassicColonyRight.hammer(s.colony, t).toolsInk);
        s.colony.setBuildQueue(new ArrayList<BuildableType>(
            List.of(classic().getUnitType("model.unit.wagonTrain"))));
        h = ClassicColonyRight.hammer(s.colony, t);
        assertEquals(40, h.cost);
        assertNull(h.tools);
        if (t != null) assertEquals("Wagenzug", h.caption);
        s.colony.setBuildQueue(new ArrayList<BuildableType>(
            List.of(classic().getUnitType("model.unit.artillery"))));
        h = ClassicColonyRight.hammer(s.colony, t);
        assertEquals(192, h.cost);
        if (t != null) {
            assertEquals("Artillerie", h.caption);
            assertEquals("(Benötigt 40 Werkzeuge)", h.tools);
        }
        if (t != null) {
            for (String[] k : new String[][] { { "newspaper", "Druckerei" },
                    { "warehouse", "Lagerhaus" }, { "warehouseExpansion", "Lagerhauserweiterung" },
                    { "stables", "Ställe" }, { "fort", "Fort" }, { "docks", "Hafenanlagen" } }) {
                assertEquals(k[1], ClassicColonyRight.buildName(t,
                    classic().getBuildingType("model.building." + k[0])));
            }
        }
    }

    /**
     * «Vorhandene Einheiten»: the tile's land units not in the colony (the
     * persons outside, the artillery, the wagon train; no ship), the
     * screen's order, the first selected; the map's letter inks (F of a
     * fortified unit dark, of a fortifying one black: V clip 019).
     */
    public void testTheUnitsModel() throws Exception {
        final State s = ClassicColonyBuildingsTest.base1539();
        s.on("expertFarmer", Direction.NW, "grain");
        s.finish();
        final Unit d = outside(s, "expertLumberJack", "dragoon");
        final Unit art = outside(s, "artillery", null);
        final Unit wagon = outside(s, "wagonTrain", null);
        final Unit ship = outside(s, "merchantman", null);
        d.setState(Unit.UnitState.FORTIFYING);
        art.setState(Unit.UnitState.FORTIFYING);
        art.setState(Unit.UnitState.FORTIFIED);
        final List<Unit> listed = ClassicColonyRight.listed(s.colony, null);
        assertEquals(List.of(d, art, wagon), listed);
        assertFalse(listed.contains(ship));
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        final ClassicColonyRight.Model m = ClassicColonyRight.model(s.colony, t,
            ClassicColonyBuildings.Assets.load(ClassicPackFiles.runtime()), Mode.MUSKET,
            Mode.MUSKET, null, null);
        assertSame("the first framed", d, m.selected);
        assertEquals(213, m.units.get(0).cell.x);
        assertEquals(231, m.units.get(1).cell.x);
        assertEquals("fortifying: black", 0x000000, m.units.get(0).ink);
        assertEquals("fortified: the dark orange", 0xAA4900, m.units.get(1).ink);
        if (t != null) {
            assertEquals("F", m.units.get(0).letter);
            assertEquals("F", m.units.get(1).letter);
            assertEquals("Vorhandene Einheiten", m.unitsCaption);
        }
    }


    // The presses

    /** A screen whose build list opens are counted, with a units' box recorder. */
    private static ClassicColonyPanel screen(State s, int[] lists, List<Unit> boxes)
        throws Exception {
        final FreeColClient fcc = ClassicColonyBuildingsTest.client(s.game, s.owner);
        final ClassicGUI gui = new ClassicGUI(null) {
                @Override
                public net.sf.freecol.client.gui.panel.FreeColPanel showBuildQueuePanel(
                    Colony colony) {
                    lists[0]++;
                    return null;
                }
            };
        final Field f = FreeColClient.class.getDeclaredField("gui");
        f.setAccessible(true);
        f.set(fcc, gui);
        ClassicColonyPanel.forgetFigureOrders();
        final ClassicColonyPanel p = new ClassicColonyPanel(fcc,
            ClassicColonyBuildingsTest.library(), s.colony, null,
            new ClassicColonyPanel.Units() {
                @Override
                public List<Unit> order(List<Unit> standing) {
                    return standing;
                }

                @Override
                public void options(Unit unit, boolean first) {
                    boxes.add(unit);
                }
            }, ClassicSlide.SYSTEM, false);
        p.setFounding(ClassicColonyBuildingsTest.BASE);
        return p;
    }

    /**
     * The mode buttons: a press shows the pressed button at once and the
     * panel at the release (I); the mode carries over to the next screen
     * (global, spec §0.1 decision 8); a founding opens in house mode, a
     * building's @BUILT zoom in hammer mode with the cursor on ÄNDERN.
     */
    public void testTheModeButtons() throws Exception {
        final State s = ClassicColonyBuildingsTest.base1539();
        s.on("expertFarmer", Direction.NW, "grain");
        s.finish();
        ClassicColonyPanel.setLastMode(Mode.HOUSE);
        final ClassicColonyPanel p = screen(s, new int[1], new ArrayList<>());
        p.canvas();
        assertEquals(Mode.HOUSE, p.right().mode);
        // The press: the musket button sinks, the panel stays.
        p.dragAt(310, 152, 310, 152, false);
        p.canvas();
        assertEquals(Mode.MUSKET, p.right().mode);
        assertEquals(Mode.MUSKET, ClassicColonyPanel.lastMode());
        // A press without its release (the gesture's press alone).
        final java.lang.reflect.Method press = ClassicColonyPanel.class
            .getDeclaredMethod("pressAt", int.class, int.class);
        press.setAccessible(true);
        press.invoke(p, 310, 168);
        p.canvas();
        assertEquals("the panel waits", Mode.MUSKET, p.right().mode);
        assertEquals("the button is down", Mode.HAMMER, p.right().buttons);
        p.clickAt(310, 168);
        p.canvas();
        assertEquals(Mode.HAMMER, p.right().mode);
        // The next screen opens in the last visit's mode; a founding in house.
        final ClassicColonyPanel q = screen(s, new int[1], new ArrayList<>());
        q.canvas();
        assertEquals(Mode.HAMMER, q.right().mode);
        q.opened(ClassicColonyRight.Opening.FOUNDING);
        q.canvas();
        assertEquals(Mode.HOUSE, q.right().mode);
        q.opened(ClassicColonyRight.Opening.BUILT_BUILDING);
        q.canvas();
        assertEquals(Mode.HAMMER, q.right().mode);
        assertEquals(ClassicColonyPanel.CURSOR_CHANGE, ClassicColonyPanel.tileCursor());
    }

    /**
     * The panels' presses: a listed unit's press selects it and brings the
     * cursor, its box on the release (M2's, now the list's); ÄNDERN opens
     * the build list (the first slice's until M4.10), KAUFEN not (M4.10);
     * the house panel takes none; the figure row holds no artillery.
     */
    public void testThePresses() throws Exception {
        final State s = ClassicColonyBuildingsTest.base1539();
        s.on("expertFarmer", Direction.NW, "grain");
        s.finish();
        final Unit soldier = outside(s, "veteranSoldier", "soldier");
        final Unit art = outside(s, "artillery", null);
        final int[] lists = { 0 };
        final List<Unit> boxes = new ArrayList<>();
        final ClassicColonyPanel p = screen(s, lists, boxes);
        p.setMode(Mode.HOUSE);
        p.canvas();
        p.clickAt(250, 150);
        assertEquals("no build list from the house panel", 0, lists[0]);
        for (ClassicColonyBuildings.Figure f : p.rowFigures()) {
            assertNotSame("no artillery in the figure row", art, f.unit);
        }
        p.setMode(Mode.MUSKET);
        p.canvas();
        assertSame(soldier, p.right().selected);
        p.clickAt(235, 165);                       // the artillery's cell
        assertEquals(ClassicColonyPanel.CURSOR_LIST, ClassicColonyPanel.tileCursor());
        assertEquals(List.of(art), boxes);
        p.canvas();
        assertSame(art, p.right().selected);
        p.setMode(Mode.HAMMER);
        p.canvas();
        p.clickAt(230, 142);                       // KAUFEN
        assertEquals(0, lists[0]);
        assertEquals(ClassicColonyPanel.CURSOR_BUY, ClassicColonyPanel.tileCursor());
        p.clickAt(280, 142);                       // ÄNDERN
        assertEquals(1, lists[0]);
        assertEquals(ClassicColonyPanel.CURSOR_CHANGE, ClassicColonyPanel.tileCursor());
        assertEquals(1, boxes.size());
    }


    // Golden against clip 020, dago and clip 019

    /** The right panel's region: x 208-319 (the panel, the mode buttons, the picture's edge), y 129-177. */
    static final Rectangle REGION = new Rectangle(208, 129, 112, 49);

    /** A rebuilt frame's right panel. */
    private static final class Shot {
        final String clip;
        final int frame;
        final BufferedImage canvas;
        final String model;

        Shot(String clip, int frame, BufferedImage canvas, String model) {
            this.clip = clip;
            this.frame = frame;
            this.canvas = canvas;
            this.model = model;
        }
    }

    private static Shot shot(String clip, int frame, ClassicColonyPanel p, int cursor,
                             boolean off) {
        ClassicColonyPanel.setTileCursor(cursor);
        p.blinkPhase(off);
        final BufferedImage img = p.canvas();
        return new Shot(clip, frame, img, p.right() + " cursor=" + cursor + (off ? " off" : ""));
    }

    /** The clip's house amounts drawn instead of FreeCol's. */
    private static void house(ClassicColonyPanel p, String... amounts) {
        p.setRightAdjust(m -> (m.house == null) ? m : m.withHouse(m.house.only(amounts)));
    }

    /** A build, its hammers and the tools in stock. */
    private static void build(State s, BuildableType b, int hammers, int tools) {
        s.colony.setBuildQueue(new ArrayList<BuildableType>(List.of(b)));
        s.colony.removeGoods(goods("hammers"));
        s.colony.removeGoods(goods("tools"));
        if (hammers > 0) s.colony.addGoods(goods("hammers"), hammers);
        if (tools > 0) s.colony.addGoods(goods("tools"), tools);
    }

    private static BuildableType building(String id) {
        return classic().getBuildingType("model.building." + id);
    }

    private static UnitType unitType(String id) {
        return classic().getUnitType("model.unit." + id);
    }

    /** Clip 020's frames as rebuilt states. */
    private static List<Shot> clip020() throws Exception {
        final List<Shot> out = new ArrayList<>();
        final String c = ClassicColonyGolden.CLIP;
        // #2260 Base 1539, house: sugar 3 | rum 3 | lumber 8, 4 crossed, 12 hammers.
        {
            final State s = ClassicColonyBuildingsTest.base1539();
            s.on("expertFarmer", Direction.NW, "grain");
            s.finish();
            final ClassicColonyPanel p = ClassicColonyBuildingsTest.panel(s,
                ClassicColonyBuildingsTest.BASE);
            p.setMode(Mode.HOUSE);
            house(p, "sugar 3", "rum 3", "lumber 8 4", "hammers 12");
            out.add(shot(c, 2260, p, ClassicColonyLayout.CENTRE_TILE, false));
            // #9921 Base 1540, hammer: the Verlag, 36 hammers, 10 tools.
            p.setRightAdjust(null);
            p.setMode(Mode.HAMMER);
            build(s, building("printingPress"), 36, 10);
            out.add(shot(c, 9921, p, ClassicColonyLayout.CENTRE_TILE, false));
            // #93990 Base 1558: the Lagerhaus, 42 hammers.
            build(s, building("warehouse"), 42, 0);
            out.add(shot(c, 93990, p, ClassicColonyLayout.CENTRE_TILE, false));
            // #105810 / #105830 Base 1562, the Lagerhaus just built (it stays
            // the build, R4), no hammers; the cursor on ÄNDERN, on and off.
            build(s, building("warehouse"), 0, 0);
            out.add(shot(c, 105810, p, ClassicColonyPanel.CURSOR_CHANGE, false));
            out.add(shot(c, 105830, p, ClassicColonyPanel.CURSOR_CHANGE, true));
            // #130584 Base 1569: the Wagenzug, 36 hammers.
            build(s, unitType("wagonTrain"), 36, 0);
            out.add(shot(c, 130584, p, ClassicColonyLayout.CENTRE_TILE, false));
            // #134173 Base 1571: the Druckerei, 60 hammers, 15 tools.
            build(s, building("newspaper"), 60, 15);
            out.add(shot(c, 134173, p, 1, true));
        }
        // #14817 Provisorische Kolonie 1541, house: sugar 3 | a horse, one crossed.
        {
            final State s = new State("Provisorische Kolonie", 1541);
            s.on("seasonedScout", Direction.N, "sugar");
            s.finish();
            final ClassicColonyPanel p = ClassicColonyBuildingsTest.panel(s,
                ClassicColonyBuildingsTest.PROV);
            p.setMode(Mode.HOUSE);
            house(p, "sugar 3", "horses 1 1");
            out.add(shot(c, 14817, p, 1, true));
        }
        // #123003 Silver City 1566, house: furs 3, silver 3 | a horse, one crossed.
        {
            final State s = new State("Silver City", 1566);
            s.on("seasonedScout", Direction.W, "silver");
            s.finish();
            final ClassicColonyPanel p = ClassicColonyBuildingsTest.panel(s,
                ClassicColonyBuildingsTest.SILVER);
            p.setMode(Mode.HOUSE);
            house(p, "furs 3", "silver 3", "horses 1 1");
            out.add(shot(c, 123003, p, ClassicColonyPanel.CURSOR_OFF_TILES, true));
        }
        return out;
    }

    /** Dago's and clip 019's frames (Base 1729, Fur Town) as rebuilt states. */
    private static List<Shot> otherClips() throws Exception {
        final List<Shot> out = new ArrayList<>();
        // dago #1000 Base, house, stacked: tobacco 34 + 14, cotton 5, ore 32 |
        // 14 horses crossed, cigars 72, tools 48, muskets 48 | lumber 30 + 2, hammers 32.
        {
            final State s = ClassicColonyBuildingsTest.base(1729);
            s.on("expertFarmer", Direction.NW, "grain");
            s.finish();
            final ClassicColonyPanel p = ClassicColonyBuildingsTest.panel(s,
                ClassicColonyBuildingsTest.BASE);
            p.setMode(Mode.HOUSE);
            house(p, "tobacco 34 14", "cotton 5", "ore 32", "horses 0 14", "cigars 72",
                  "tools 48", "muskets 48", "lumber 30 2", "hammers 32");
            out.add(shot("dago-colony", 1000, p, ClassicColonyLayout.CENTRE_TILE, false));
            // #6400 hammer: the Artillerie, 96 hammers, 44 tools.
            p.setRightAdjust(null);
            p.setMode(Mode.HAMMER);
            build(s, unitType("artillery"), 96, 44);
            out.add(shot("dago-colony", 6400, p, ClassicColonyLayout.CENTRE_TILE, false));
            // #6000 musket: a master carpenter (094, «-»), a dragoon («-»),
            // three fortified artillery (F dark); the first framed green.
            p.setMode(Mode.MUSKET);
            s.outside("masterCarpenter", null);
            s.outside("freeColonist", "dragoon");
            for (int i = 0; i < 3; i++) {
                final Unit a = s.outside("artillery", null);
                a.setState(Unit.UnitState.FORTIFYING);
                a.setState(Unit.UnitState.FORTIFIED);
            }
            out.add(shot("dago-colony", 6000, p, ClassicColonyLayout.CENTRE_TILE, false));
        }
        // dago #11400 Fur Town, hammer: the Fort, 16 hammers, 100 tools or more.
        {
            final State s = new State("Fur Town", 1729);
            s.on("expertFarmer", Direction.NW, "grain");
            s.finish();
            final ClassicColonyPanel p = ClassicColonyBuildingsTest.panel(s,
                ClassicColonyBuildingsTest.BASE);
            p.setMode(Mode.HAMMER);
            build(s, building("fort"), 16, 150);
            out.add(shot("dago-colony", 11400, p, ClassicColonyLayout.CENTRE_TILE, false));
        }
        // clip 019 #1290 Base 1729, musket: the dragoon (an expert lumberjack,
        // fortifying: F black) under the white cursor, three fortified
        // artillery (F dark); #700 the same, the cursor in the figure row.
        {
            final State s = ClassicColonyBuildingsTest.base(1729);
            s.on("expertFarmer", Direction.NW, "grain");
            s.finish();
            final Unit d = s.outside("expertLumberJack", "dragoon");
            d.setState(Unit.UnitState.FORTIFYING);
            for (int i = 0; i < 3; i++) {
                final Unit a = s.outside("artillery", null);
                a.setState(Unit.UnitState.FORTIFYING);
                a.setState(Unit.UnitState.FORTIFIED);
            }
            final ClassicColonyPanel p = ClassicColonyBuildingsTest.panel(s,
                ClassicColonyBuildingsTest.BASE);
            p.setMode(Mode.MUSKET);
            out.add(shot("wake-in-colony", 1290, p, ClassicColonyPanel.CURSOR_LIST, false));
            out.add(shot("wake-in-colony", 700, p, ClassicColonyPanel.CURSOR_OFF_TILES, false));
        }
        return out;
    }

    /** The shots against their frames; the report and the failures. */
    private static void compare(ClassicColonyGolden gold, List<Shot> shots, StringBuilder report,
                                StringBuilder fails, String what) throws Exception {
        for (Shot s : shots) {
            final File dir = new File(gold.recordings, s.clip);
            final File f = ClassicTerrainGoldenTest.frameFile(dir, s.frame);
            if (f == null) {
                report.append("  ").append(s.clip).append(" #").append(s.frame)
                    .append(": no frame, skipped\n");
                continue;
            }
            final BufferedImage frame = javax.imageio.ImageIO.read(f);
            ClassicColonyBandTest.dump(s.canvas, frame, s.frame, what);
            final ClassicColonyGolden.Diff d = ClassicColonyGolden.compare(
                s.clip + " #" + s.frame, ClassicColonyGolden.pixels(s.canvas),
                ClassicColonyGolden.pixels(frame), REGION, gold.pointer(frame), null);
            report.append("  ").append(d).append("\n    drawn: ").append(s.model).append('\n');
            if (d.differing != 0) fails.append(' ').append(d.name).append(": ").append(d.differing);
            assertTrue(d.name, d.compared > 4500);
        }
    }

    /**
     * Golden (M4.7 acceptance, clip 020): the right panel and the mode
     * buttons of 9 frames rebuilt, 0 differing pixels, the arrow left out:
     * the house rows of #2260 (the 4 crossed lumber), #14817 and #123003
     * (the crossed horse), the hammer panels of #9921 (Verlag, q 13),
     * #93990 (Lagerhaus, q 20), #105810 / #105830 (ÄNDERN under the cursor,
     * on and off), #130584 (Wagenzug, q 10), #134173 (Druckerei, q 30),
     * with KAUFEN, ÄNDERN and «(Benötigt …)».
     */
    public void testTheRightPanelAgainstClip020() throws Exception {
        final ClassicColonyGolden gold = ClassicColonyGolden.open(getName());
        if (gold == null) return;
        final StringBuilder report = new StringBuilder(), fails = new StringBuilder();
        compare(gold, clip020(), report, fails, "right");
        System.err.println(getName() + ":\n" + report);
        assertEquals("differing pixels:" + fails + "\n" + report, 0, fails.length());
    }

    /**
     * Golden (dago, clip 019): dago Base's stacked house rows with their
     * white and red labels (#1000), its «Vorhandene Einheiten» (#6000), its
     * Artillerie's hammer rows with «48» «48» and the grey tools line
     * (#6400), Fur Town's Fort (#11400); clip 019's list with the white
     * cursor on the dragoon (#1290) and without it (#700).
     */
    public void testTheRightPanelAgainstDagoAndClip019() throws Exception {
        final ClassicColonyGolden gold = ClassicColonyGolden.open(getName());
        if (gold == null) return;
        if (!new File(gold.recordings, "dago-colony").isDirectory()
            || !new File(gold.recordings, "wake-in-colony").isDirectory()) {
            System.err.println(getName() + ": no dago or clip 019 frames, skipped");
            return;
        }
        final StringBuilder report = new StringBuilder(), fails = new StringBuilder();
        compare(gold, otherClips(), report, fails, "right");
        System.err.println(getName() + ":\n" + report);
        assertEquals("differing pixels:" + fails + "\n" + report, 0, fails.length());
    }
}
