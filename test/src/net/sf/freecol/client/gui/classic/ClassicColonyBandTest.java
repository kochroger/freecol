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
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Direction;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.util.test.FreeColTestCase;

import static net.sf.freecol.client.gui.classic.ClassicColonyBuildingsTest.State;


/**
 * The colony screen's band, left panel (build spec M4.5; {@link
 * ClassicColonyBand}, {@link ClassicColonyLayout}): the SoL line's counts
 * and places, the production row against every row the analyses measured
 * (clip 020's five, dago's three), the model of a test colony (FreeCol's
 * food used, stored and short, the fish part, crosses, bells), the flag's
 * cycling canton, the white cursor on a figure; then golden: the state of
 * clip 020's frames rebuilt in a test game (as the building area's test:
 * the colony, its colonists, their places and jobs, the units outside, the
 * selection, the cursor and its blink half, the SoL percentage, a visit
 * replayed where the figure row's order depends on it) and the whole left
 * panel (x 0-118, y 129-177: the band picture, the SoL line, the figure
 * row, the production row) compared with the frame pixel for pixel, the
 * arrow left out; where FreeCol's rules make other amounts than the clip,
 * the test names both and draws the clip's (the row's drawing apart from
 * the rules); and dago's three colonies' SoL lines and production rows
 * drawn by the same painter against dago's frames.  Skips the golden part
 * without the pack or the recordings ({@code -Dclassic.clips}).
 */
public class ClassicColonyBandTest extends FreeColTestCase {

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


    // The SoL line (M4.5 target 1)

    /** The counts in brackets: rounded (V clip 020 ×12, dago ×4), FreeCol's floor not. */
    public void testTheCounts() {
        final int[][] v = {
            { 11, 5, 1 }, { 89, 5, 4 }, { 14, 4, 1 }, { 86, 4, 3 }, { 13, 5, 1 }, { 87, 5, 4 },
            { 11, 6, 1 }, { 89, 6, 5 }, { 9, 8, 1 }, { 91, 8, 7 }, { 24, 8, 2 }, { 76, 8, 6 },
            { 23, 8, 2 }, { 77, 8, 6 }, { 22, 8, 2 }, { 78, 8, 6 }, { 100, 1, 1 },
            { 1, 1, 0 }, { 99, 1, 1 },                               // #35057
            { 100, 19, 19 }, { 34, 14, 5 }, { 66, 14, 9 }, { 100, 15, 15 },   // dago
        };
        for (int[] c : v) {
            assertEquals(c[0] + "% of " + c[1], c[2],
                         ClassicColonyLayout.memberCount(c[0], c[1]));
        }
        // The Tories are the rest, so the two counts add up to the
        // colonists (every pair above fits; the S review: rounded on both
        // sides 36 of 1980 cases gave one colonist too many, 15 % of 10 ->
        // 2 + 9); where no half occurs that is the rounded Tory share.
        final int[] widths = { 8, 8, 8, 8 };
        for (int pop = 0; pop <= 20; pop++) {
            for (int sol = 0; sol <= 100; sol++) {
                final ClassicColonyBand.Model m
                    = new ClassicColonyBand.Model(sol, pop, 0, 0, 0, 0, 0, 0, widths);
                assertEquals(sol + "% of " + pop, pop, m.rebels + m.tories);
                assertEquals(ClassicColonyLayout.memberCount(sol, pop), m.rebels);
                if ((sol * pop) % 100 != 50) {
                    assertEquals(sol + "% of " + pop,
                                 ClassicColonyLayout.memberCount(100 - sol, pop), m.tories);
                }
            }
        }
        final ClassicColonyBand.Model half
            = new ClassicColonyBand.Model(15, 10, 0, 0, 0, 0, 0, 0, widths);
        assertEquals(2, half.rebels);
        assertEquals("15% (2) and 85% (8)", 8, half.tories);
        assertEquals("FreeCol's own count floors (11 % of 5)", 0,
                     Colony.calculateRebelCount(5, 11));
        assertEquals("11% (1)", ClassicColonyLayout.memberText(11, 1));
        assertEquals("100% (1)", ClassicColonyLayout.memberText(100, 1));
        // The places: the flag at (2,132), its words at 15, the crown at
        // (104,132), its words ending at 104 (V: «89% (4)» at 78, «100% (1)»
        // at 74, dago «66% (9)» at 78).
        assertEquals(2, ClassicColonyLayout.SOL_ICON_X);
        assertEquals(132, ClassicColonyLayout.SOL_ICON_Y);
        assertEquals(15, ClassicColonyLayout.SOL_TEXT_X);
        assertEquals(133, ClassicColonyLayout.SOL_Y);
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final ClassicFont tiny = (pack == null) ? null : pack.font(ClassicFont.TINY);
        if (tiny == null) return;
        assertEquals(78, ClassicColonyLayout.toryTextX(tiny.stringWidth("89% (4)")));
        assertEquals(74, ClassicColonyLayout.toryTextX(tiny.stringWidth("100% (1)")));
        assertEquals(78, ClassicColonyLayout.toryTextX(tiny.stringWidth("66% (9)")));
    }

    /**
     * FreeCol's SoL percentage is not the original's in one visit (clip 020
     * visit 9, #46039 / #46350 / #47291: the same liberty, 13 % of 5, 11 %
     * of 6, 9 % of 8 colonists): FreeCol's floor(liberty / 2 pop) cannot
     * show all three for any liberty, the rounding up can (liberty 129 or
     * 130).  The screen shows FreeCol's (its rules use it).
     */
    public void testTheSolPercentageDiffers() {
        final int[][] seen = { { 5, 13 }, { 6, 11 }, { 8, 9 } };
        final List<Integer> floorFits = new ArrayList<>(), ceilFits = new ArrayList<>();
        for (int liberty = 0; liberty <= 400; liberty++) {
            boolean f = true, c = true;
            for (int[] s : seen) {
                final int d = 2 * s[0];
                if (liberty / d != s[1]) f = false;
                if ((liberty + d - 1) / d != s[1]) c = false;
            }
            if (f) floorFits.add(liberty);
            if (c) ceilFits.add(liberty);
        }
        assertEquals("no liberty gives FreeCol's floor 13/11/9", List.of(), floorFits);
        assertEquals(List.of(129, 130), ceilFits);
    }


    // The production row (M4.5 targets 3, 4)

    private static final int CORN = 7, FISH = 7, CROSS = 8, BELL = 10;

    /** The row of the four groups (used fish + corn, stored or short, crosses, bells). */
    private static ClassicColonyLayout.BandRow row(int fish, int corn, int stored, int crosses,
                                                   int bells) {
        return ClassicColonyLayout.bandRow(
            new int[][] { { fish, corn }, { stored }, { crosses }, { bells } },
            new int[][] { { FISH, CORN }, { CORN }, { CROSS }, { BELL } });
    }

    private static int[] xs(int first, int pitch, int n) {
        final int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = first + i * pitch;
        return out;
    }

    private static void assertGroup(String why, ClassicColonyLayout.BandRow r, int g, int part,
                                    int[] expect) {
        assertEquals(why + " group " + g + "." + part, Arrays.toString(expect),
                     Arrays.toString(r.x[g][part]));
    }

    /**
     * The row (L §5.3, dago §4.3) against every row measured: clip 020
     * #2260 (pitch 6), #134173 (3), #14817 (w + 1), #18955 (the shortfall),
     * #46350 (4), #2620, #6560; dago Base (k 2), Fur City (pitch 1, k 1),
     * Fur Town (k 2, a single cross with «2»).
     */
    public void testTheProductionRows() {
        ClassicColonyLayout.BandRow r = row(0, 10, 1, 1, 4);
        assertEquals("#2260", 6, r.pitch);
        assertEquals(1, r.k);
        assertGroup("#2260", r, 0, 1, xs(2, 6, 10));
        assertGroup("#2260", r, 1, 0, new int[] { 67 });
        assertGroup("#2260", r, 2, 0, new int[] { 78 });
        assertGroup("#2260", r, 3, 0, xs(90, 6, 4));
        assertEquals(117, r.end);
        for (boolean b : r.labelled) assertFalse(b);
        r = row(6, 10, 1, 1, 6);
        assertEquals("#134173", 3, r.pitch);
        assertGroup("#134173", r, 0, 0, xs(2, 3, 6));
        assertGroup("#134173", r, 0, 1, xs(20, 3, 10));
        assertGroup("#134173", r, 1, 0, new int[] { 58 });
        assertGroup("#134173", r, 2, 0, new int[] { 69 });
        assertGroup("#134173", r, 3, 0, xs(81, 3, 6));
        r = row(0, 3, 1, 1, 1);
        assertGroup("#14817", r, 0, 1, xs(2, 8, 3));
        assertGroup("#14817", r, 1, 0, new int[] { 29 });
        assertGroup("#14817", r, 2, 0, new int[] { 40 });
        assertGroup("#14817", r, 3, 0, new int[] { 52 });
        r = row(0, 6, 2, 1, 1);
        assertGroup("#18955 used", r, 0, 1, xs(2, 8, 6));
        assertGroup("#18955 the shortfall", r, 1, 0, new int[] { 53, 61 });
        assertGroup("#18955", r, 2, 0, new int[] { 72 });
        assertGroup("#18955", r, 3, 0, new int[] { 84 });
        r = row(6, 6, 5, 1, 4);
        assertEquals("#46350", 4, r.pitch);
        assertGroup("#46350", r, 0, 1, xs(26, 4, 6));
        assertGroup("#46350", r, 1, 0, xs(57, 4, 5));
        assertGroup("#46350", r, 2, 0, new int[] { 84 });
        assertGroup("#46350", r, 3, 0, xs(96, 4, 4));
        r = row(0, 10, 1, 1, 1);
        assertGroup("#2620", r, 0, 1, xs(2, 8, 10));
        assertGroup("#2620", r, 1, 0, new int[] { 85 });
        assertGroup("#2620", r, 3, 0, new int[] { 108 });
        r = row(0, 8, 3, 1, 1);
        assertGroup("#6560", r, 1, 0, xs(69, 8, 3));
        assertGroup("#6560", r, 3, 0, new int[] { 108 });
        // dago Base: 38 used (30 fish, 8 corn), 19 crosses, 32 bells: k 2.
        r = row(30, 8, 0, 19, 32);
        assertEquals("dago Base", 2, r.k);
        assertEquals(1, r.pitch);
        assertGroup("dago Base fish", r, 0, 0, xs(2, 1, 15));
        assertGroup("dago Base corn", r, 0, 1, xs(17, 1, 4));
        assertGroup("dago Base crosses", r, 2, 0, xs(31, 1, 9));
        assertGroup("dago Base bells", r, 3, 0, xs(51, 1, 16));
        assertEquals("«38» at (4,165)", 4, r.labelX[0]);
        assertTrue(r.labelled[0] && r.labelled[2] && r.labelled[3]);
        assertEquals(38, r.total(0));
        assertEquals(33, r.labelX[2]);
        assertEquals(53, r.labelX[3]);
        assertEquals("k = 1 would end at 120", 120, ClassicColonyLayout.bandRowAt(
            new int[][] { { 30, 8 }, { 0 }, { 19 }, { 32 } },
            new int[][] { { FISH, CORN }, { CORN }, { CROSS }, { BELL } }, 1, 1).end);
        assertEquals("#2260 at pitch 7 would end at 129", 129, ClassicColonyLayout.bandRowAt(
            new int[][] { { 0, 10 }, { 1 }, { 1 }, { 4 } },
            new int[][] { { FISH, CORN }, { CORN }, { CROSS }, { BELL } }, 1, 7).end);
        // dago Fur City: 29 used (12 + 17), 1 cross, 20 bells: pitch 1, k 1.
        r = row(12, 17, 0, 1, 20);
        assertEquals("Fur City", 1, r.k);
        assertEquals(1, r.pitch);
        assertGroup("Fur City fish", r, 0, 0, xs(2, 1, 12));
        assertGroup("Fur City corn", r, 0, 1, xs(14, 1, 17));
        assertGroup("Fur City cross", r, 2, 0, new int[] { 41 });
        assertGroup("Fur City bells", r, 3, 0, xs(53, 1, 20));
        assertTrue("«29»", r.labelled[0]);
        assertFalse("its 1 cross has none", r.labelled[2]);
        assertTrue("«20»", r.labelled[3]);
        assertEquals(55, r.labelX[3]);
        // dago Fur Town: 41 used (26 + 15), 10 stored, 2 crosses, 96 bells: k 2.
        r = row(26, 15, 10, 2, 96);
        assertEquals("Fur Town", 2, r.k);
        assertGroup("Fur Town fish", r, 0, 0, xs(2, 1, 13));
        assertGroup("Fur Town corn", r, 0, 1, xs(15, 1, 7));
        assertGroup("Fur Town stored", r, 1, 0, xs(32, 1, 5));
        assertGroup("Fur Town cross", r, 2, 0, new int[] { 47 });
        assertGroup("Fur Town bells", r, 3, 0, xs(59, 1, 48));
        assertTrue("the single cross «2»", r.labelled[2]);
        assertEquals(49, r.labelX[2]);
        assertEquals(34, r.labelX[1]);
        assertEquals(61, r.labelX[3]);
        assertEquals(165, ClassicColonyLayout.BAND_ROW_Y + ClassicColonyLayout.BAND_LABEL_DY);
        // A group of 0 is left out; a part below k keeps one icon (I).
        r = row(0, 4, 0, 0, 2);
        assertEquals(0, r.x[1][0].length);
        assertEquals(0, r.x[2][0].length);
        assertGroup("bells after the corn", r, 3, 0, xs(2 + 3 * 8 + 7 + 4, 11, 2));
        r = row(0, 200, 0, 1, 200);
        assertTrue(r.k > 1);
        assertEquals("1 cross at k > 1 keeps its icon", 1, r.x[2][0].length);
        assertTrue(r.end <= ClassicColonyLayout.BAND_ROW_RIGHT);
    }


    // The model of a test colony (FreeCol's numbers)

    /** FreeCol's food, crosses and bells as the row shows them, the SoL from the colony's liberty. */
    public void testTheModel() {
        final ClassicColonyBuildings.Assets a = ClassicColonyBuildings.Assets.load(
            ClassicPackFiles.runtime());
        final State s = ClassicColonyBuildingsTest.base1539();
        s.in("veteranSoldier", "townHall");
        s.on("expertFarmer", Direction.NW, "grain");
        s.colony.getTile().getNeighbourOrNull(Direction.E)
            .setType(classic().getTileType("model.tile.ocean"));
        s.on("expertFisherman", Direction.E, "fish");
        s.finish();
        final Colony c = s.colony;
        final Specification spec = c.getSpecification();
        final GoodsType food = spec.getGoodsType("model.goods.food");
        final GoodsType fish = spec.getGoodsType("model.goods.fish");
        c.invalidateCache();
        final ClassicColonyBand.Model m = ClassicColonyBand.model(c, a);
        final int made = c.getFoodProduction();
        final int f = c.getTotalProductionOf(fish);
        System.err.println(getName() + ": made " + made + " (fish " + f + "), eaten "
            + c.getFoodConsumption() + ", net " + c.getNetProductionOf(food) + " -> " + m);
        assertTrue("the fisherman's fish", f > 0);
        assertEquals("3 colonists eat 6", 6, c.getFoodConsumption());
        assertTrue("enough food: " + made, made >= 6);
        assertEquals("the colonists' and the horses'", 6 + horses(c, made),
                     m.fishUsed + m.cornUsed);
        assertEquals("the fish first", Math.min(f, m.fishUsed + m.cornUsed), m.fishUsed);
        assertEquals(made, m.fishUsed + m.cornUsed + m.stored);
        assertEquals(0, m.shortfall);
        assertEquals("FreeCol's chapel", 1, m.crosses);
        assertEquals("the Staatsmann's bells (the Rathaus' row)",
                     ClassicColonyBuildings.shown(c.getBuilding(s.type("townHall"))).getAmount(),
                     m.bells);
        assertEquals(0, m.sol);
        assertEquals(100, m.tory);
        // Horses breed from the surplus (FreeCol's country): their food is
        // used too, the rest stored.
        c.addGoods(spec.getGoodsType("model.goods.horses"), 26);
        c.invalidateCache();
        final ClassicColonyBand.Model hm = ClassicColonyBand.model(c, a);
        final int hc = horses(c, made);
        System.err.println(getName() + ": with 26 horses: they take " + hc + " -> " + hm);
        assertTrue("FreeCol's horses take food", hc > 0);
        assertEquals(6 + hc, hm.fishUsed + hm.cornUsed);
        assertEquals(made - 6 - hc, hm.stored);
        c.removeGoods(spec.getGoodsType("model.goods.horses"));
        c.invalidateCache();
        // Liberty: 11 % of 3 colonists (66 liberty) is FreeCol's 11 %; the
        // counts the original's rounding.
        c.modifyLiberty(66);
        final ClassicColonyBand.Model l = ClassicColonyBand.model(c, a);
        assertEquals(11, l.sol);
        assertEquals(89, l.tory);
        assertEquals(0, l.rebels);
        assertEquals(3, l.tories);
        // A shortfall: the fisherman leaves the tile (2 colonists' 4 food
        // against what the centre and the farmer make, without the fish).
        final State t = ClassicColonyBuildingsTest.base1539();
        t.in("veteranSoldier", "townHall");
        t.in("masterCarpenter", "lumberMill");
        t.in("masterSugarPlanter", "distillerHouse");
        t.in("masterBlacksmith", "blacksmithHouse");
        t.finish();
        t.colony.invalidateCache();
        final ClassicColonyBand.Model sh = ClassicColonyBand.model(t.colony, a);
        final int made2 = t.colony.getFoodProduction();
        System.err.println(getName() + ": short: made " + made2 + ", eaten "
            + t.colony.getFoodConsumption() + " -> " + sh);
        assertTrue(made2 < 8);
        assertEquals("all of it used", made2, sh.cornUsed + sh.fishUsed);
        assertEquals("the rest short", 8 - made2, sh.shortfall);
        assertEquals(0, sh.stored);
        // The model's string (the recorder).
        assertTrue(sh.toString(), sh.toString().contains("short=" + (8 - made2)));
    }


    /**
     * The production row's count labels follow «Beschriftung an Waren und
     * Terrain» (I: the option's meaning in the original, as the tiles'):
     * dago Base's k-scaled row with its «38», «19», «32», then without.
     */
    public void testTheLabelsFollowTheOption() throws Exception {
        final State s = ClassicColonyBuildingsTest.base1539();
        s.on("expertFarmer", Direction.NW, "grain");
        s.finish();
        final ClassicColonyPanel p = ClassicColonyBuildingsTest.panel(s,
            ClassicColonyBuildingsTest.BASE);
        p.setBandAdjust(m -> m.with(30, 8, 0, 0, 19, 32));
        final java.lang.reflect.Field shared = ClassicPrefs.class.getDeclaredField("shared");
        shared.setAccessible(true);
        final Object was = shared.get(null);
        final ClassicPrefs prefs = new ClassicPrefs(null);
        shared.set(null, prefs);
        try {
            prefs.set(ClassicPrefs.GOODS_TERRAIN_LABELS, true);
            final BufferedImage on = p.canvas();
            assertTrue(p.band().row.labelled[0]);
            assertEquals(4, p.band().row.labelX[0]);
            prefs.set(ClassicPrefs.GOODS_TERRAIN_LABELS, false);
            final BufferedImage off = p.canvas();
            int black = 0, differ = 0;
            for (int y = 165; y < 172; y++) {
                for (int x = 4; x < 4 + 9; x++) {
                    if ((on.getRGB(x, y) & 0xFFFFFF) == 0) black++;
                    if (on.getRGB(x, y) != off.getRGB(x, y)) differ++;
                }
            }
            assertTrue("«38»'s box: " + black, black > 20);
            assertTrue("gone without the option: " + differ, differ > 20);
        } finally {
            shared.set(null, was);
        }
    }


    // The flag and the cursor on a figure

    /**
     * The SoL flag's canton cycles with the water (its 3 pixels of indices
     * 122, 123; ICONS.SS's only cycling pixels): drawn from the index sheet
     * through the game palette at the phase, the other pixels the pack's
     * PNG's; the screen knows when it drew the flag (its palette step
     * repaints it then).
     */
    public void testTheFlagCycles() throws Exception {
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final ClassicGamePalette pal = ClassicGamePalette.of(pack);
        if (pack == null || pal == null || pack.indexSheet(ClassicColonyBand.ICONS_SS) == null) {
            System.err.println(getName() + ": skipped, no pack");
            return;
        }
        final ClassicColonyBuildings.Assets a = ClassicColonyBuildings.Assets.load(pack);
        final BufferedImage png = a.icon(ClassicColonyLayout.ICON_SOL);
        final int[][] cycling = { { 1, 2, 122 }, { 4, 2, 123 }, { 2, 4, 122 } };
        final java.util.Set<String> seen = new java.util.HashSet<>();
        for (int p = 0; p < pal.phaseCount(); p++) {
            final BufferedImage f = ClassicColonyBand.flag(pack, a, p);
            assertEquals(png.getWidth(), f.getWidth());
            assertEquals(png.getHeight(), f.getHeight());
            int differs = 0;
            for (int y = 0; y < f.getHeight(); y++) {
                for (int x = 0; x < f.getWidth(); x++) {
                    final int o = png.getRGB(x, y), q = f.getRGB(x, y);
                    assertEquals((o >>> 24) > 128, (q >>> 24) > 128);
                    if ((o >>> 24) > 128 && (o & 0xFFFFFF) != (q & 0xFFFFFF)) differs++;
                }
            }
            final StringBuilder sb = new StringBuilder();
            for (int[] c : cycling) {
                assertEquals("phase " + p + " (" + c[0] + "," + c[1] + ")", pal.rgb(p, c[2]),
                             f.getRGB(c[0], c[1]) & 0xFFFFFF);
                sb.append(Integer.toHexString(f.getRGB(c[0], c[1]))).append(' ');
            }
            seen.add(sb.toString());
            assertTrue("only the canton's 3 differ from the PNG", differs <= 3);
            if (p == 0) assertEquals("phase 0 is the PNG's", 0, differs);
        }
        assertEquals("every phase another canton", pal.phaseCount(), seen.size());
        // The screen: the flag only with members.
        final State s = ClassicColonyBuildingsTest.base1539();
        s.on("expertFarmer", Direction.NW, "grain");
        s.finish();
        final ClassicColonyPanel p = ClassicColonyBuildingsTest.panel(s,
            ClassicColonyBuildingsTest.BASE);
        p.canvas();
        assertFalse(p.flagCycles());
        s.colony.modifyLiberty(100);
        p.canvas();
        assertTrue(p.flagCycles());
        assertEquals(50, p.band().sol);
    }

    /**
     * The white cursor on the selected one's figure (V #123003-#123095,
     * #6540-#6600): white in the blink's on half, nothing in the off half,
     * no green under it; the cursor on a tile leaves the figure its green
     * frame (#2260).
     */
    public void testTheFigureCursor() throws Exception {
        final State s = ClassicColonyBuildingsTest.base1539();
        final Unit farmer = s.on("expertFarmer", Direction.NW, "grain");
        s.finish();
        final ClassicColonyPanel p = ClassicColonyBuildingsTest.panel(s,
            ClassicColonyBuildingsTest.BASE);
        final ClassicColonyChrome.Assets c = ClassicColonyChrome.Assets.load(
            ClassicPackFiles.runtime());
        final int green = c.rgb(ClassicColonyLayout.INK_GREEN);
        final int white = c.rgb(ClassicColonyLayout.INK_WHITE);
        p.select(farmer);
        ClassicColonyPanel.setTileCursor(ClassicColonyLayout.CENTRE_TILE);
        BufferedImage img = p.canvas();
        final ClassicColonyBuildings.Figure f = p.rowFigures().get(0);
        assertSame(farmer, f.unit);
        final Rectangle r = ClassicColonyLayout.figureFrame(f.x, f.w);
        assertEquals("the green frame (cursor on a tile)", green, img.getRGB(r.x, r.y) & 0xFFFFFF);
        ClassicColonyPanel.setTileCursor(ClassicColonyPanel.CURSOR_OFF_TILES);
        p.blinkPhase(false);
        img = p.canvas();
        assertEquals("the cursor, on half", white, img.getRGB(r.x, r.y) & 0xFFFFFF);
        assertEquals(white, img.getRGB(r.x + r.width - 1, r.y + r.height - 1) & 0xFFFFFF);
        p.blinkPhase(true);
        img = p.canvas();
        final int off = img.getRGB(r.x, r.y) & 0xFFFFFF;
        assertTrue("nothing in the off half", off != white && off != green);
    }


    // Golden against clip 020 and dago

    /** A frame's palette phase of the water (its PLTE's 120-127). */
    static int phaseOf(ClassicColonyGolden gold, BufferedImage frame) {
        final ClassicGamePalette pal = ClassicGamePalette.of(gold.pack);
        if (pal == null || !(frame.getColorModel() instanceof IndexColorModel)) return 0;
        final IndexColorModel cm = (IndexColorModel) frame.getColorModel();
        final int[] e = new int[cm.getMapSize()];
        cm.getRGBs(e);
        return Math.max(0, pal.phaseOf(e));
    }

    /** A rebuilt frame's left panel. */
    private static final class Shot {
        final int frame;
        final BufferedImage canvas;
        final String model, freecol;

        Shot(int frame, BufferedImage canvas, String model, String freecol) {
            this.frame = frame;
            this.canvas = canvas;
            this.model = model;
            this.freecol = freecol;
        }
    }

    /**
     * Paint a state as a frame shows it: the white cursor's place and its
     * blink half, the water's phase (the flag), the clip's amounts in the
     * row where FreeCol's rules make others.
     *
     * @param clip {fish used, corn used, stored, short, crosses, bells}.
     */
    private static Shot shot(ClassicColonyGolden gold, int frame, ClassicColonyPanel p,
                             Colony colony, int cursor, boolean off, int[] clip)
        throws Exception {
        final int phase = phaseOf(gold, gold.frame(frame));
        p.setWaterPhase(() -> phase);
        ClassicColonyPanel.setTileCursor(cursor);
        p.blinkPhase(off);
        final ClassicColonyBand.Model fc = ClassicColonyBand.model(colony,
            ClassicColonyBuildings.Assets.load(gold.pack));
        p.setBandAdjust((clip == null) ? null
                        : m -> m.with(clip[0], clip[1], clip[2], clip[3], clip[4], clip[5]));
        final BufferedImage img = p.canvas();
        return new Shot(frame, img, p.band() + " fig " + p.rowFigures(), fc.toString());
    }

    private static void liberty(Colony c, int target) {
        c.modifyLiberty(target - c.getLiberty());
    }

    /** Clip 020's frames as rebuilt states. */
    private static List<Shot> shots(ClassicColonyGolden gold) throws Exception {
        final List<Shot> out = new ArrayList<>();
        final int OFF = ClassicColonyPanel.CURSOR_OFF_TILES;
        // #2260 Base 1539: 11 % of 5; the farmer selected, the cursor on the
        // centre (on); 10 corn used, 1 stored, 1 cross, 4 bells.  #2620,
        // #2747: the Staatsmann dragged into the Haus des Gerbers, then the
        // Tabakhändler, selected, the cursor on his figure (on): 1 bell.
        {
            final State s = ClassicColonyBuildingsTest.base1539();
            final Unit vet = s.in("veteranSoldier", "townHall");
            s.in("masterSugarPlanter", "distillerHouse");
            s.in("masterCarpenter", "lumberMill");
            final Unit farmer = s.on("expertFarmer", Direction.NW, "grain");
            s.on("expertLumberJack", Direction.N, "lumber");
            s.finish();
            liberty(s.colony, 110);
            final ClassicColonyPanel p = ClassicColonyBuildingsTest.panel(s,
                ClassicColonyBuildingsTest.BASE);
            p.select(farmer);
            out.add(shot(gold, 2260, p, s.colony, ClassicColonyLayout.CENTRE_TILE, false,
                         new int[] { 0, 10, 1, 0, 1, 4 }));
            s.move(vet, "furTraderHouse");
            p.select(vet);
            out.add(shot(gold, 2620, p, s.colony, OFF, false, new int[] { 0, 10, 1, 0, 1, 1 }));
            s.move(vet, "tobacconistHouse");
            out.add(shot(gold, 2747, p, s.colony, OFF, false, new int[] { 0, 10, 1, 0, 1, 1 }));
        }
        // #6560: the veteran outside as Soldat, selected, the cursor on his
        // figure (off); 14 % of 4; 8 used, 3 stored, 1 cross, 1 bell.
        {
            final State s = ClassicColonyBuildingsTest.base1539();
            s.in("masterSugarPlanter", "distillerHouse");
            s.in("masterCarpenter", "lumberMill");
            s.on("expertFarmer", Direction.NW, "grain");
            s.on("expertLumberJack", Direction.N, "lumber");
            s.finish();
            final Unit soldier = s.outside("veteranSoldier", "soldier");
            liberty(s.colony, 112);
            final ClassicColonyPanel p = ClassicColonyBuildingsTest.panel(s,
                ClassicColonyBuildingsTest.BASE);
            p.select(soldier);
            out.add(shot(gold, 6560, p, s.colony, OFF, true, new int[] { 0, 8, 3, 0, 1, 1 }));
        }
        // #18955 Base 1542 (visit 4): the farmer from NW into the Haus des
        // Schmieds (J2 #37; he keeps his place in the row), selected, the
        // cursor on NW (on); 6 used, 2 short (crossed), 1 cross, 1 bell.
        {
            final State s = ClassicColonyBuildingsTest.base(1542);
            final Unit farmer = s.on("expertFarmer", Direction.NW, "grain");
            s.on("expertLumberJack", Direction.N, "lumber");
            s.in("masterSugarPlanter", "distillerHouse");
            s.in("masterCarpenter", "lumberMill");
            s.finish();
            s.outside("veteranSoldier", "soldier");
            liberty(s.colony, 112);
            final ClassicColonyPanel p = ClassicColonyBuildingsTest.panel(s,
                ClassicColonyBuildingsTest.BASE);
            p.canvas();
            s.move(farmer, "blacksmithHouse");
            p.select(farmer);
            out.add(shot(gold, 18955, p, s.colony, 0, false, new int[] { 0, 6, 0, 2, 1, 1 }));
        }
        // #46039 / #46350 / #47291 Base 1546 (visit 9, as the building
        // area's test): three arrivals outside; the farmer selected, the
        // cursor on N (on); then the fisherman on E, selected, the cursor on
        // his figure (off); then the distiller, the Schreiner and the
        // blacksmith, the blacksmith selected (off).  The same liberty: the
        // original 13 %, 11 %, 9 % (FreeCol's floor cannot, see
        // testTheSolPercentageDiffers): set for each.
        {
            final State s = ClassicColonyBuildingsTest.base(1546);
            s.in("veteranSoldier", "townHall");
            final Unit carpenter = s.in("masterCarpenter", "blacksmithHouse");
            final Unit farmer = s.on("expertFarmer", Direction.NW, "grain");
            s.on("expertLumberJack", Direction.N, "lumber");
            s.on("masterSugarPlanter", Direction.NE, "ore");
            s.finish();
            final Unit fisher = s.outside("expertFisherman", null);
            final Unit distiller = s.outside("masterDistiller", null);
            final Unit smith = s.outside("masterBlacksmith", null);
            liberty(s.colony, 130);
            final ClassicColonyPanel p = ClassicColonyBuildingsTest.panel(s,
                ClassicColonyBuildingsTest.BASE);
            p.select(farmer);
            out.add(shot(gold, 46039, p, s.colony, 1, false, new int[] { 0, 10, 1, 0, 1, 4 }));
            s.work(fisher, Direction.E, "fish");
            liberty(s.colony, 132);
            p.select(fisher);
            out.add(shot(gold, 46350, p, s.colony, OFF, true, new int[] { 6, 6, 5, 0, 1, 4 }));
            s.move(distiller, "distillerHouse");
            p.canvas();
            s.move(carpenter, "lumberMill");
            s.move(smith, "blacksmithHouse");
            liberty(s.colony, 144);
            p.select(smith);
            out.add(shot(gold, 47291, p, s.colony, OFF, true, new int[] { 6, 10, 1, 0, 1, 4 }));
        }
        // #96680 Base 1561 / #134173 Base 1571: 8 colonists, the farmer
        // selected, the cursor on N (off); 22 % / 24 %; 6 fish + 10 corn
        // used, 1 stored, 1 cross, 6 bells.
        {
            final State s = new State("Base", 1561).build("lumberMill").build("docks")
                .build("printingPress").build("blacksmithShop");
            s.in("veteranSoldier", "townHall");
            s.in("masterDistiller", "distillerHouse");
            s.in("masterCarpenter", "lumberMill");
            s.in("masterBlacksmith", "blacksmithHouse");
            final Unit farmer = s.on("expertFarmer", Direction.NW, "grain");
            s.on("masterSugarPlanter", Direction.SW, "sugar");
            s.on("expertLumberJack", Direction.N, "lumber");
            s.on("expertFisherman", Direction.E, "fish");
            s.finish();
            liberty(s.colony, 352);
            final ClassicColonyPanel p = ClassicColonyBuildingsTest.panel(s,
                ClassicColonyBuildingsTest.BASE);
            p.select(farmer);
            out.add(shot(gold, 96680, p, s.colony, 1, true, new int[] { 6, 10, 1, 0, 1, 6 }));
        }
        {
            final State s = new State("Base", 1571).build("lumberMill").build("docks")
                .build("printingPress").build("blacksmithShop").build("warehouse");
            s.in("veteranSoldier", "townHall");
            s.in("masterSugarPlanter", "distillerHouse");
            s.in("masterDistiller", "distillerHouse");
            s.in("masterCarpenter", "lumberMill");
            final Unit farmer = s.on("expertFarmer", Direction.NW, "grain");
            s.on("expertLumberJack", Direction.N, "lumber");
            s.on("masterBlacksmith", Direction.NE, "ore");
            s.on("expertFisherman", Direction.E, "fish");
            s.finish();
            liberty(s.colony, 384);
            final ClassicColonyPanel p = ClassicColonyBuildingsTest.panel(s,
                ClassicColonyBuildingsTest.BASE);
            p.select(farmer);
            out.add(shot(gold, 134173, p, s.colony, 1, true, new int[] { 6, 10, 1, 0, 1, 6 }));
        }
        // #14817 / #35057 Provisorische Kolonie 1541 / 1544: the scout on N,
        // selected; 0 % (no flag) / 1 % (0) with the cursor on his figure
        // (on); #14817 has no blink at all (the cursor on N in its off half
        // gives the same frame); 3 corn used, 1 stored, 1 cross, 1 bell.
        for (int[] k : new int[][] { { 14817, 1541 }, { 35057, 1544 } }) {
            final State s = new State("Provisorische Kolonie", k[1]);
            final Unit scout = s.on("seasonedScout", Direction.N, "sugar");
            s.finish();
            if (k[0] == 35057) liberty(s.colony, 2);
            final ClassicColonyPanel p = ClassicColonyBuildingsTest.panel(s,
                ClassicColonyBuildingsTest.PROV);
            p.select(scout);
            out.add((k[0] == 14817)
                    ? shot(gold, 14817, p, s.colony, 1, true, new int[] { 0, 3, 1, 0, 1, 1 })
                    : shot(gold, 35057, p, s.colony, OFF, false, new int[] { 0, 3, 1, 0, 1, 1 }));
        }
        // #123003 Silver City 1566 (founding): the scout on W, selected, the
        // cursor on his figure (off: the blink's white from #123026).
        {
            final State s = new State("Silver City", 1566);
            final Unit scout = s.on("seasonedScout", Direction.W, "silver");
            s.finish();
            final ClassicColonyPanel p = ClassicColonyBuildingsTest.panel(s,
                ClassicColonyBuildingsTest.SILVER);
            p.select(scout);
            out.add(shot(gold, 123003, p, s.colony, OFF, true, new int[] { 0, 3, 1, 0, 1, 1 }));
        }
        return out;
    }

    /**
     * Golden (M4.5 acceptance): the whole left panel (x 0-118, y 129-177)
     * of 13 frames of clip 020 rebuilt, 0 differing pixels, the arrow left
     * out: the SoL flag at the frame's phase and «p% (n)», the crown and its
     * words; the figure row with the green frame or the white cursor (on,
     * off); the production row at pitch 8, 6, 4, 3, the fish part, the
     * stored corn, the shortfall's crossed corn (#18955).  The output names
     * FreeCol's amounts next to the clip's drawn ones.
     */
    public void testTheLeftPanelAgainstClip020() throws Exception {
        final ClassicColonyGolden gold = ClassicColonyGolden.open(getName());
        if (gold == null) return;
        final StringBuilder report = new StringBuilder();
        final StringBuilder fails = new StringBuilder();
        for (Shot s : shots(gold)) {
            final BufferedImage frame = gold.frame(s.frame);
            dump(s.canvas, frame, s.frame, "left");
            final ClassicColonyGolden.Diff d = ClassicColonyGolden.compare(
                "left panel #" + s.frame, ClassicColonyGolden.pixels(s.canvas),
                ClassicColonyGolden.pixels(frame), ClassicColonyLayout.LEFT_PANEL,
                gold.pointer(frame), null);
            report.append("  ").append(d).append('\n');
            report.append("    drawn: ").append(s.model).append('\n');
            report.append("    FreeCol: ").append(s.freecol).append('\n');
            if (d.differing != 0) fails.append(' ').append(d.name).append(": ").append(d.differing);
            assertTrue(d.name, d.compared > 4000);
        }
        System.err.println(getName() + ":\n" + report);
        assertEquals("differing pixels:" + fails + "\n" + report, 0, fails.length());
    }

    /**
     * Golden (dago, 0 px in its analysis): Base #1000 («100% (19)», no
     * crown; 38 used at k 2 «38», 19 crosses «19», 32 bells «32»), Fur City
     * #9560 («34% (5)», «66% (9)»; pitch 1: «29», one cross without a
     * label, «20»), Fur Town #11360 («100% (15)»; «41», 10 stored «10», the
     * single cross «2», «96»): the SoL line (y 129-141) and the production
     * row (y 159-177) drawn by the same painter from dago's amounts against
     * dago's frames; the figure row between them is not rebuilt here (its
     * x by the figure row's test) and is counted as left out.
     */
    public void testTheLeftPanelAgainstDago() throws Exception {
        final ClassicColonyGolden gold = ClassicColonyGolden.open(getName());
        if (gold == null) return;
        final File dir = new File(gold.recordings, "dago-colony");
        if (ClassicTerrainGoldenTest.frameFile(dir, 1000) == null) {
            System.err.println(getName() + ": no dago frames, skipped");
            return;
        }
        final ClassicColonyBuildings.Assets a = ClassicColonyBuildings.Assets.load(gold.pack);
        final ClassicColonyChrome.Assets c = ClassicColonyChrome.Assets.load(gold.pack);
        final int[] w = ClassicColonyBand.widths(a);
        // {frame, sol, population, fish, corn, stored, short, crosses, bells}
        final int[][] cases = {
            { 1000, 100, 19, 30, 8, 0, 0, 19, 32 },
            { 9560, 34, 14, 12, 17, 0, 0, 1, 20 },
            { 11360, 100, 15, 26, 15, 10, 0, 2, 96 },
        };
        final StringBuilder report = new StringBuilder();
        final StringBuilder fails = new StringBuilder();
        for (int[] k : cases) {
            final BufferedImage frame = javax.imageio.ImageIO.read(
                ClassicTerrainGoldenTest.frameFile(dir, k[0]));
            final ClassicColonyBand.Model m = new ClassicColonyBand.Model(k[1], k[2], k[3], k[4],
                k[5], k[6], k[7], k[8], w);
            final BufferedImage img = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
            final Graphics2D g = img.createGraphics();
            ClassicColonyChrome.paintBand(g, c);
            ClassicColonyBand.paint(g, c, a, ClassicColonyBand.flag(gold.pack, a,
                phaseOf(gold, frame)), m, true);
            g.dispose();
            final ClassicColonyGolden.Mask figures = new ClassicColonyGolden.Mask();
            figures.rect(0, 142, 118, 158);
            final ClassicColonyGolden.Diff d = ClassicColonyGolden.compare("dago #" + k[0],
                ClassicColonyGolden.pixels(img), ClassicColonyGolden.pixels(frame),
                ClassicColonyLayout.LEFT_PANEL, gold.pointer(frame), figures);
            report.append("  ").append(d).append("  ").append(m).append('\n');
            if (d.differing != 0) fails.append(' ').append(d.name).append(": ").append(d.differing);
            assertTrue(d.name, d.compared > 3000);
        }
        System.err.println(getName() + ":\n" + report);
        assertEquals("differing pixels:" + fails + "\n" + report, 0, fails.length());
    }

    /**
     * The Tory penalty's red line (fixer B after part X, FIX-LIST must 7):
     * the whole SoL line in palette 4 while the colony's production bonus
     * is below 0, else white; the flag and the crown unchanged.  Golden
     * (clip 049, Base 1602, kept in
     * {@code recordings/fixer-b-analysis/c049}): #39280 «26% (4) 74%
     * (10)» red, #40710 «28% (4) 72% (9)» white, the line's rows y 129-141
     * at 0 px; and the model from a colony: red only with the penalty.
     */
    public void testTheToryPenaltysRedLine() throws Exception {
        final State s = ClassicColonyBuildingsTest.base1539();
        s.in("veteranSoldier", "townHall");
        s.finish();
        final Colony col = s.colony;
        setProductionBonus(col, -1);
        final ClassicColonyBand.Model red = ClassicColonyBand.model(col, null);
        assertTrue(red.toString(), red.penalty);
        assertTrue(red.with(1, 2, 3, 0, 4, 5).penalty);
        assertTrue(red.withSol(50).penalty);
        setProductionBonus(col, 0);
        assertFalse(ClassicColonyBand.model(col, null).penalty);
        setProductionBonus(col, 1);
        assertFalse(ClassicColonyBand.model(col, null).penalty);
        final ClassicColonyGolden gold = ClassicColonyGolden.open(getName());
        if (gold == null) return;
        final File dir = new File(gold.recordings, "fixer-b-analysis/c049");
        if (ClassicTerrainGoldenTest.frameFile(dir, 39280) == null) {
            System.err.println(getName() + ": no clip 049 frames, skipped");
            return;
        }
        final ClassicColonyBuildings.Assets a = ClassicColonyBuildings.Assets.load(gold.pack);
        final ClassicColonyChrome.Assets c = ClassicColonyChrome.Assets.load(gold.pack);
        final int[] w = ClassicColonyBand.widths(a);
        // {frame, sol, population, penalty}
        final int[][] cases = { { 39280, 26, 14, 1 }, { 40710, 28, 13, 0 } };
        final StringBuilder report = new StringBuilder();
        final StringBuilder fails = new StringBuilder();
        for (int[] k : cases) {
            final BufferedImage frame = javax.imageio.ImageIO.read(
                ClassicTerrainGoldenTest.frameFile(dir, k[0]));
            final ClassicColonyBand.Model m = new ClassicColonyBand.Model(k[1], k[2], 0, 0,
                0, 0, 0, 0, w, k[3] == 1);
            final BufferedImage img = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
            final Graphics2D g = img.createGraphics();
            ClassicColonyChrome.paintBand(g, c);
            ClassicColonyBand.paint(g, c, a, ClassicColonyBand.flag(gold.pack, a,
                phaseOf(gold, frame)), m, true);
            g.dispose();
            final ClassicColonyGolden.Diff d = ClassicColonyGolden.compare("049 #" + k[0],
                ClassicColonyGolden.pixels(img), ClassicColonyGolden.pixels(frame),
                new java.awt.Rectangle(0, 129, 119, 13), gold.pointer(frame), null);
            report.append("  ").append(d).append("  ").append(m).append('\n');
            if (d.differing != 0) fails.append(' ').append(d.name).append(": ").append(d.differing);
            assertTrue(d.name, d.compared > 1000);
        }
        System.err.println(getName() + ":\n" + report);
        assertEquals("differing pixels:" + fails + "\n" + report, 0, fails.length());
    }

    /**
     * With {@code -Dclassic.colony.dump=<folder>}: our canvas and the
     * frame side by side, 3x (for the report's eyes; nothing is asserted).
     */
    static void dump(BufferedImage ours, BufferedImage frame, int n, String what)
        throws java.io.IOException {
        final String d = System.getProperty("classic.colony.dump");
        if (d == null || d.isBlank()) return;
        final BufferedImage out = new BufferedImage(2 * 960 + 12, 600,
                                                    BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = out.createGraphics();
        g.drawImage(ours, 0, 0, 960, 600, null);
        g.drawImage(frame, 972, 0, 960, 600, null);
        g.dispose();
        javax.imageio.ImageIO.write(out, "png",
            new File(d, "s4-" + what + "-ours-vs-" + n + ".png"));
    }

    /** The food FreeCol's horses take (the country's input). */
    private static int horses(Colony c, int made) {
        int n = 0;
        for (net.sf.freecol.common.model.Building b : c.getBuildings()) {
            final net.sf.freecol.common.model.ProductionInfo pi = c.getProductionInfo(b);
            if (pi == null) continue;
            for (net.sf.freecol.common.model.AbstractGoods ag : pi.getConsumption()) {
                if (ag.getType().isFoodType()) n += ag.getAmount();
            }
        }
        return Math.min(n, Math.max(0, made - c.getFoodConsumption()));
    }
}
