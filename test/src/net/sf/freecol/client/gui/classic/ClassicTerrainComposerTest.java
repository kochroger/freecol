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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.IntConsumer;

import net.sf.freecol.common.model.TileType;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * The terrain composer's rules (M1c design 10 §6.3, item W6a) on
 * synthetic sheets ({@link ClassicTerrainSheets}: the original's mask
 * positions, patterned frames, no pack): the unexplored tile, the fringe
 * (masks, union, never diagonal, its source), {@code BLEND_BASE}, the
 * side-mask blend, least knowledge (a spy source) and the cycling flag.
 */
public class ClassicTerrainComposerTest extends FreeColTestCase {

    /**
     * A map drawn as rows of letters, with an explored flag per tile and a
     * spy on the types asked of unexplored tiles.  Letters: {@code o}
     * ocean, {@code h} high seas, {@code p} plains, {@code g} grassland,
     * {@code d} desert, {@code f} broadleaf forest, {@code t} tropical
     * forest, {@code ?} unknown (null type, F-W6d-MP).  Upper case is
     * explored.
     */
    static final class Grid implements ClassicTerrainComposer.TerrainSource {
        final String[] rows;
        final List<int[]> darkQueries = new ArrayList<>();
        final java.util.Map<Long, List<Integer>> overlays = new HashMap<>();

        Grid(String... rows) {
            this.rows = rows;
        }

        char at(int x, int y) {
            return this.rows[y].charAt(x);
        }

        @Override
        public boolean onMap(int x, int y) {
            return y >= 0 && y < this.rows.length && x >= 0 && x < this.rows[y].length();
        }

        @Override
        public boolean explored(int x, int y) {
            return onMap(x, y) && Character.isUpperCase(at(x, y));
        }

        @Override
        public TileType type(int x, int y) {
            if (!onMap(x, y)) return null;
            if (!explored(x, y)) this.darkQueries.add(new int[] { x, y });
            final String id;
            switch (Character.toLowerCase(at(x, y))) {
            case 'o': id = "model.tile.ocean"; break;
            case 'h': id = "model.tile.highSeas"; break;
            case 'p': id = "model.tile.plains"; break;
            case 'g': id = "model.tile.grassland"; break;
            case 'd': id = "model.tile.desert"; break;
            case 'f': id = "model.tile.broadleafForest"; break;
            case 't': id = "model.tile.tropicalForest"; break;
            default: return null;
            }
            return spec().getTileType(id);
        }

        @Override
        public void overlays(int x, int y, IntConsumer frames) {
            final List<Integer> f = this.overlays.get(((long)x << 32) | y);
            if (f != null) f.forEach(frames::accept);
        }

        Grid overlay(int x, int y, Integer... frames) {
            this.overlays.put(((long)x << 32) | y, java.util.Arrays.asList(frames));
            return this;
        }

        /** Every dark tile asked lies within Chebyshev 1 of an explored one. */
        void assertLeastKnowledge() {
            for (int[] q : this.darkQueries) {
                boolean near = false;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        near |= explored(q[0] + dx, q[1] + dy);
                    }
                }
                assertTrue("asked (" + q[0] + "," + q[1] + ") beyond the fog ring", near);
            }
        }
    }

    private static final ClassicIndexSheet TERRAIN = ClassicTerrainSheets.patternTerrain();
    private static final ClassicIndexSheet PHYS = ClassicTerrainSheets.patternPhys();

    private static ClassicTerrainComposer composer() {
        return composer(new HashMap<>());
    }

    /** A composer over the pattern sheets, the design's alias table plus overrides. */
    private static ClassicTerrainComposer composer(java.util.Map<String, Integer> extra) {
        final java.util.Map<String, Integer> alias = new HashMap<>(ClassicTerrainGoldenTest.ALIAS);
        alias.putAll(extra);
        return new ClassicTerrainComposer(TERRAIN, PHYS, id -> alias.getOrDefault(id, -1));
    }

    /** Compose one cell into a fresh 16x16 buffer. */
    private static int[] cell(ClassicTerrainComposer c, Grid g, int x, int y, boolean[] cyc) {
        final byte[] b = new byte[256];
        final boolean h = c.composeCell(g, x, y, b, 0, 16);
        if (cyc != null) cyc[0] = h;
        final int[] r = new int[256];
        for (int k = 0; k < 256; k++) r[k] = b[k] & 0xFF;
        return r;
    }

    private static int[] cell(ClassicTerrainComposer c, Grid g, int x, int y) {
        return cell(c, g, x, y, null);
    }

    private static int terrain(int f, int k) {
        return TERRAIN.index(f, k % 16, k / 16);
    }

    private static int dark(int k) {
        return PHYS.index(ClassicTerrainComposer.DARK, k % 16, k / 16);
    }

    /** @return The offsets where a cell differs from the dark tile. */
    private static List<Integer> lit(int[] cell) {
        final List<Integer> r = new ArrayList<>();
        for (int k = 0; k < 256; k++) if (cell[k] != dark(k)) r.add(k);
        return r;
    }

    private static boolean inAnyMask(int k, int... sides) {
        for (int d : sides) if (ClassicTerrainSheets.inMask(d, k)) return true;
        return false;
    }

    private static final int N = 0, E = 1, S = 2, W = 3;


    /** An unexplored tile is PHYS0.SS.148 whatever its true type, with no overlay. */
    public void testUnexploredIsTheDarkTile() {
        final ClassicTerrainComposer c = composer();
        final Grid g = new Grid("ooooo", "opfho", "ooooo");
        for (int x = 1; x <= 3; x++) g.overlay(x, 1, 103, 96, 64);
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 3; y++) {
                final int[] px = cell(c, g, x, y);
                for (int k = 0; k < 256; k++) assertEquals(dark(k), px[k]);
            }
        }
        assertTrue("nothing explored, nothing asked", g.darkQueries.isEmpty());
        // Off the map: black.
        final int[] off = cell(c, g, 9, 9);
        for (int k = 0; k < 256; k++) assertEquals(0, off[k]);
    }

    /**
     * The fringe lies in the masks only, a union of the open sides: 15,
     * 29 (adjacent, 1 px shared), 30 (opposite), 43 and 56 px; an explored
     * diagonal neighbour opens nothing.
     */
    public void testFringeIsTheUnionOfTheOpenMasks() {
        final ClassicTerrainComposer c = composer();
        final Object[][] cases = {
            { new String[] { "ooo", "ooo", "oPo" }, new int[] { S }, 15 },
            { new String[] { "ooo", "Poo", "ooo" }, new int[] { W }, 15 },
            { new String[] { "oPo", "ooP", "ooo" }, new int[] { N, E }, 29 },
            { new String[] { "ooo", "Poo", "oPo" }, new int[] { S, W }, 29 },
            { new String[] { "oPo", "ooo", "oPo" }, new int[] { N, S }, 30 },
            { new String[] { "ooo", "PoP", "ooo" }, new int[] { E, W }, 30 },
            { new String[] { "oPo", "ooP", "oPo" }, new int[] { N, E, S }, 43 },
            { new String[] { "oPo", "PoP", "oPo" }, new int[] { N, E, S, W }, 56 },
            { new String[] { "PoP", "ooo", "PoP" }, new int[] {}, 0 },
        };
        for (Object[] cs : cases) {
            final Grid g = new Grid((String[])cs[0]);
            final List<Integer> lit = lit(cell(c, g, 1, 1));
            final int[] sides = (int[])cs[1];
            assertEquals(java.util.Arrays.toString((String[])cs[0]), (int)cs[2], lit.size());
            for (int k : lit) assertTrue("only mask px change", inAnyMask(k, sides));
        }
    }

    /**
     * The fringe's sprite: land into anything is the neighbour's, water
     * into water the neighbour's, water into dark land the dark tile's own
     * true terrain, and with its own type unknown the neighbour's.
     */
    public void testFringeSource() {
        final ClassicTerrainComposer c = composer();
        final int plains = 2, grass = 4, ocean = 10, lane = 11;
        final Object[][] cases = {
            { "Pg", plains },  // land -> dark land: the neighbour's
            { "Po", plains },  // land -> dark water: the neighbour's
            { "Oh", ocean },   // water -> dark water: the neighbour's
            { "Ho", lane },    // sea lane -> dark ocean: the neighbour's
            { "Og", grass },   // water -> dark land: its own
            { "O?", ocean },   // own type unknown: the neighbour's
        };
        for (Object[] cs : cases) {
            final Grid g = new Grid((String)cs[0]);
            final int[] px = cell(c, g, 1, 0);
            for (int k : c.mask(W)) {
                assertEquals(cs[0] + " px " + k, terrain((int)cs[1], k), px[k]);
            }
            g.assertLeastKnowledge();
        }
        // The dark tile's own type is asked only when water borders it.
        final Grid land = new Grid("Pg");
        cell(c, land, 1, 0);
        assertTrue(land.darkQueries.isEmpty());
    }

    /**
     * {@code BLEND_BASE}: a type drawn T008 shows T001 in its fringes and
     * blends, and T008 as its own base (fog-start §3.3).
     */
    public void testBlendBase() {
        final java.util.Map<String, Integer> t008 = new HashMap<>();
        t008.put("model.tile.desert", 8);
        final ClassicTerrainComposer c = composer(t008);
        assertEquals(8, c.spr(spec().getTileType("model.tile.desert")));
        assertEquals(1, c.blendSpr(spec().getTileType("model.tile.desert")));
        // Its own base.
        final Grid g = new Grid("DoP");
        final int[] own = cell(c, g, 0, 0);
        for (int k = 0; k < 256; k++) assertEquals(terrain(8, k), own[k]);
        // Its fringe into the dark ocean east of it: T001.
        final int[] fringe = cell(c, g, 1, 0);
        for (int k : c.mask(W)) assertEquals(terrain(1, k), fringe[k]);
        // Its blend into the explored plains: T001.
        final Grid h = new Grid("DP");
        final int[] blend = cell(c, h, 1, 0);
        for (int k : c.mask(W)) assertEquals(terrain(1, k), blend[k]);
    }

    /**
     * The side-mask blend of explored tiles: land into land both ways,
     * land into water, ocean and sea lane both ways (the sparkles too),
     * water never into land, the same sprite a no-op toward an explored
     * neighbour, the true type of a dark neighbour.
     */
    public void testBlend() {
        final ClassicTerrainComposer c = composer();
        // Land <-> land: plains (2) and grassland (4).
        final Grid pg = new Grid("PG");
        assertMask(c, pg, 0, 0, E, 4);
        assertMask(c, pg, 1, 0, W, 2);
        // Land -> water; water never into land.
        final Grid po = new Grid("PO");
        assertMask(c, po, 1, 0, W, 2);
        assertUnchanged(c, po, 0, 0, 2);
        // Ocean <-> sea lane, the sparkles included.
        final Grid oh = new Grid("OH");
        assertMask(c, oh, 0, 0, E, 11);
        assertMask(c, oh, 1, 0, W, 10);
        boolean sparkle = false;
        final int[] lane = cell(c, oh, 0, 0);
        for (int k : c.mask(E)) sparkle |= lane[k] >= 120 && lane[k] < 128;
        assertTrue("the sea lane's sparkles in the ocean's blend", sparkle);
        // The same sprite next to an explored tile: nothing (savannah and
        // tropical forest are both T005).
        final Grid tt = new Grid("TT");
        assertUnchanged(c, tt, 0, 0, 5);
        // A dark neighbour's true type blends (the FS #19 sea lane).
        final Grid oDark = new Grid("Oh");
        assertMask(c, oDark, 0, 0, E, 11);
        oDark.assertLeastKnowledge();
        // An unknown dark neighbour gives no blend (F-W6d-MP).
        assertUnchanged(c, new Grid("O?"), 0, 0, 10);
    }

    /**
     * {@link ClassicTerrainComposer#DARK_SIDES_ALWAYS}: toward a dark
     * neighbour the own sprite is copied all the same, which shows only
     * where two masks overlap (FS #1042 (42,46) at (2,15)).
     */
    public void testDarkSideWithTheOwnSprite() {
        final ClassicTerrainComposer c = composer();
        final int overlap = 15 * 16 + 2;   // (2,15): S and W
        assertTrue(ClassicTerrainSheets.inMask(S, overlap));
        assertTrue(ClassicTerrainSheets.inMask(W, overlap));
        // Tropical forest; S plains (T002, explored or dark); W tropical
        // forest dark: the W side rewrites (2,15) with the own T005.
        final Grid dark = new Grid("oTo", "tTo", "opo");
        final int[] a = cell(c, dark, 1, 1);
        assertEquals(terrain(5, overlap), a[overlap]);
        assertEquals(terrain(2, 14 * 16 + 3), a[14 * 16 + 3]);   // S elsewhere
        // The W neighbour explored: skipped, the S blend keeps (2,15).
        final Grid lit = new Grid("oTo", "TTo", "opo");
        final int[] b = cell(c, lit, 1, 1);
        assertEquals(terrain(2, overlap), b[overlap]);
        // The alternative rule would keep T002 there in both cases.
        final ClassicTerrainComposer alt = new ClassicTerrainComposer(TERRAIN, PHYS,
            id -> ClassicTerrainGoldenTest.ALIAS.getOrDefault(id, -1), false,
            ClassicTerrainComposer.SIDE_ORDER);
        assertEquals(terrain(2, overlap), cell(alt, dark, 1, 1)[overlap]);
    }

    /**
     * An explored tile next to a dark one with the same sprite stays
     * 256/256 its own (FS #19 (45,43), with dark ocean W and S).
     */
    public void testExploredNextToDarkIsNotDarkened() {
        final ClassicTerrainComposer c = composer();
        final Grid g = new Grid("ooo", "oOO", "oOO");
        assertUnchanged(c, g, 1, 1, 10);
        g.assertLeastKnowledge();
    }

    /**
     * Least knowledge: composing a whole view asks the type of an
     * unexplored tile only within Chebyshev 1 of an explored one.
     */
    public void testLeastKnowledge() {
        final ClassicTerrainComposer c = composer();
        final Grid g = new Grid(
            "ggggggggggggggg",
            "gooooooooooooog",
            "gooooHHHoooooog",
            "goooOOOHHooooog",
            "gooOOOOOHoopppg",
            "goooOOgpooopggg",
            "goooooooooooooo",
            "ppppppppppppppp");
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 15; x++) cell(c, g, x, y);
        }
        assertFalse(g.darkQueries.isEmpty());
        g.assertLeastKnowledge();
    }

    /**
     * Overlays: PHYS0 indices over the base and blends, 0xFD transparent,
     * index 0 drawn (black), in the order given.
     */
    public void testOverlays() {
        final ClassicTerrainComposer c = composer();
        final Grid g = new Grid("P").overlay(0, 0, 64, 103);
        final int[] px = cell(c, g, 0, 0);
        for (int k = 0; k < 256; k++) {
            final int x = k % 16, y = k / 16;
            final int want = (x == y) ? 100 + 103 % 20 : (x == 15 && y == 0) ? 0
                : terrain(2, k);
            assertEquals("px " + k, want, px[k]);
        }
    }

    /**
     * The cycling flag: a sea lane, a river, an ocean next to a sea lane
     * and a dark tile with a sea-lane fringe hold 120-127; plain ocean,
     * plain dark and land do not.
     */
    public void testCyclingFlag() {
        final ClassicTerrainComposer c = composer();
        final boolean[] cyc = new boolean[1];
        final Grid g = new Grid("ooooo", "oOHOo", "ooooo", "OOPPo");
        g.overlay(2, 3, 5);   // a river
        cell(c, g, 2, 1, cyc);
        assertTrue("sea lane", cyc[0]);
        cell(c, g, 1, 1, cyc);
        assertTrue("ocean next to the sea lane", cyc[0]);
        cell(c, g, 2, 0, cyc);
        assertTrue("dark tile with a sea-lane fringe", cyc[0]);
        cell(c, g, 2, 3, cyc);
        assertTrue("river", cyc[0]);
        cell(c, g, 0, 3, cyc);
        assertFalse("ocean", cyc[0]);
        cell(c, g, 0, 0, cyc);
        assertFalse("dark", cyc[0]);
        cell(c, g, 3, 3, cyc);
        assertFalse("land", cyc[0]);
    }

    // W6b: the coast (design 10 §8.3)

    private static final ClassicIndexSheet COAST = ClassicTerrainSheets.coastPhys();

    /** A composer over the coast pattern with a choice of the coast rules. */
    private static ClassicTerrainComposer coastComposer(ClassicTerrainComposer.Rules rules) {
        return new ClassicTerrainComposer(TERRAIN, COAST,
            id -> ClassicTerrainGoldenTest.ALIAS.getOrDefault(id, -1), rules);
    }

    /** The committed coast rules over the coast pattern. */
    private static ClassicTerrainComposer coastComposer() {
        return coastComposer(ClassicTerrainComposer.Rules.COMMITTED);
    }

    /** The committed rules. */
    private static final ClassicTerrainComposer.Rules RULES
        = ClassicTerrainComposer.Rules.COMMITTED;

    /**
     * A layer by hand: a base sprite, then sprites at side masks in order.
     *
     * @param base The TERRAIN frame.
     * @param sides Pairs {side, TERRAIN frame}, in drawing order.
     * @return The 256 indices.
     */
    private static int[] layer(int base, int[]... sides) {
        final int[] r = new int[256];
        for (int k = 0; k < 256; k++) {
            r[k] = terrain(base, k);
            for (int[] s : sides) {
                if (ClassicTerrainSheets.inMask(s[0], k)) r[k] = terrain(s[1], k);
            }
        }
        return r;
    }

    /** @return The quarter (0 NW, 1 NE, 2 SE, 3 SW) of a cell offset. */
    private static int quarterOf(int k) {
        final int x = k % 16, y = k / 16;
        return (y < 8) ? ((x < 8) ? 0 : 1) : ((x < 8) ? 3 : 2);
    }

    /**
     * The coast quarters by hand over a water layer: 0 keeps it, FD shows
     * the land layer, the rest is the quarter's index.
     *
     * @param water The water layer.
     * @param land The land layer (null: FD keeps too).
     * @param c The land bits of the quarters NW, NE, SE, SW.
     * @return The cell.
     */
    private static int[] quarters(int[] water, int[] land, int... c) {
        final int[] r = water.clone();
        for (int k = 0; k < 256; k++) {
            final int q = quarterOf(k);
            if (c[q] == 0) continue;
            final int f = 108 + 4 * c[q] + q;
            final int kind = ClassicTerrainSheets.quarterKind(k % 16 % 8, k / 16 % 8);
            if (kind == 0) r[k] = ClassicTerrainSheets.quarterIndex(f);
            else if (kind == 2 && land != null) r[k] = land[k];
        }
        return r;
    }

    /** The frame selection: all 32 (c, q) give 108 + 4c + q, c = 0 none; the 4 beach corners. */
    public void testCoastFrames() {
        for (int c = 0; c < 8; c++) {
            for (int q = 0; q < 4; q++) {
                assertEquals("c=" + c + " q=" + q, (c == 0) ? -1 : 108 + 4 * c + q,
                             ClassicTileArt.coastQuarter(q, c));
            }
        }
        assertEquals(150, ClassicTileArt.beachCorner(true, true));
        assertEquals(151, ClassicTileArt.beachCorner(true, false));
        assertEquals(152, ClassicTileArt.beachCorner(false, true));
        assertEquals(153, ClassicTileArt.beachCorner(false, false));
        try {
            ClassicTileArt.coastQuarter(4, 1);
            fail("quarter 4");
        } catch (IllegalArgumentException e) {
            // expected
        }
        final boolean[] t = { true, false, false, true };   // N, W
        assertEquals(3, ClassicTerrainComposer.lastLandSide(t));
        assertEquals(-1, ClassicTerrainComposer.lastLandSide(new boolean[4]));
        assertEquals(2, ClassicTerrainComposer.lastLandSide(new boolean[] { true, true, true, false }));
    }

    /**
     * Coast quarters (fog-start #3162 cell (5,5): land N, W, S): drawn over
     * the water layer, which keeps its land blends; index 0 keeps it; FD
     * shows the land layer: the last land side's sprite (W, grassland T004)
     * with the land blends of N (plains T002) and S (desert T001), in every
     * quarter -- also NE, whose own orthogonal land side is N.  The
     * design's per-corner fill would show T002 there.
     */
    public void testCoastQuartersOverTheWaterLayer() {
        final Grid g = new Grid("PPo", "GOo", "DDo");
        final int[] water = layer(10, new int[] { N, 2 }, new int[] { E, 10 },
                                  new int[] { S, 1 }, new int[] { W, 4 });
        final int[] land = layer(4, new int[] { N, 2 }, new int[] { S, 1 });
        // NW (W, NW, N) 7, NE (N, NE, E) 1, SE (E, SE, S) 4, SW (S, SW, W) 7.
        final int[] want = quarters(water, land, 7, 1, 4, 7);
        final int[] got = cell(coastComposer(), g, 1, 1);
        for (int k = 0; k < 256; k++) assertEquals("px " + k, want[k], got[k]);
        // The quarters' own px win over the blend at a mask px.
        boolean overMask = false;
        for (int k = 0; k < 256; k++) {
            if (ClassicTerrainSheets.inMask(W, k) && got[k] >= 210 && got[k] < 242) overMask = true;
        }
        assertTrue("a quarter px over the W blend", overMask);
        g.assertLeastKnowledge();
        // The design's alternative: each quarter's own orthogonal land side
        // (NE: N's plains; NW and SW with both: the vertical one).
        final int[] alt = cell(coastComposer(RULES.landFill(ClassicTerrainComposer.LandFill.CORNER_VERTICAL)), g, 1, 1);
        final int[] sideSprite = { 2, 2, 1, 1 };   // NW: N, NE: N, SE: S, SW: S
        int differ = 0;
        for (int k = 0; k < 256; k++) {
            final int q = quarterOf(k);
            if (ClassicTerrainSheets.quarterKind(k % 16 % 8, k / 16 % 8) == 2) {
                assertEquals("px " + k, terrain(sideSprite[q], k), alt[k]);
            } else {
                assertEquals("px " + k, want[k], alt[k]);
            }
            if (alt[k] != want[k] && q == 1) differ++;
        }
        assertTrue("the NE quarter tells them apart", differ > 0);
    }

    /**
     * A beach corner (fog-start #4027 cell (7,6): land E and S): no
     * quarters; the land layer -- the last land side S, desert T001, with
     * the E side's grassland T004 blended in -- under the corner, whose FD
     * keeps it; its cycling pixel flags the cell.
     */
    public void testBeachCorner() {
        final Grid g = new Grid("ooo", "oOG", "oDg");
        final boolean[] cyc = new boolean[1];
        final int[] got = cell(coastComposer(), g, 1, 1, cyc);
        final int[] land = layer(1, new int[] { E, 4 });
        for (int k = 0; k < 256; k++) {
            final int x = k % 16, y = k / 16;
            final int want = (ClassicTerrainSheets.beachDrawn(x, y))
                ? ClassicTerrainSheets.beachIndex(153, x, y) : land[k];
            assertEquals("px " + k, want, got[k]);
        }
        assertTrue("the corner's cycling px", cyc[0]);
        g.assertLeastKnowledge();
        // The base candidates: horizontal = E's grassland.
        final int[] h = cell(coastComposer(RULES.beachBase(ClassicTerrainComposer.BeachBase.HORIZONTAL)),
            g, 1, 1);
        final int[] hl = layer(4, new int[] { S, 1 });
        for (int k = 0; k < 256; k++) {
            if (!ClassicTerrainSheets.beachDrawn(k % 16, k / 16)) assertEquals("px " + k, hl[k], h[k]);
        }
        // The other three corners: their frame, base = the last land side.
        final Object[][] cases = {
            { new String[] { "oPo", "GOo", "ooo" }, 150, 4 },   // N+W: W
            { new String[] { "oPo", "oOG", "ooo" }, 151, 4 },   // N+E: E
            { new String[] { "ooo", "GOo", "oPo" }, 152, 4 },   // S+W: W
        };
        for (Object[] cs : cases) {
            final Grid c = new Grid((String[])cs[0]);
            final int[] px = cell(coastComposer(), c, 1, 1);
            int base = 0;
            for (int k = 0; k < 256; k++) {
                final int x = k % 16, y = k / 16;
                if (ClassicTerrainSheets.beachDrawn(x, y)) {
                    assertEquals(cs[1] + " px " + k,
                                 ClassicTerrainSheets.beachIndex((int)cs[1], x, y), px[k]);
                } else if (!inAnyMask(k, N, E, S, W)) {
                    assertEquals(cs[1] + " base px " + k, terrain((int)cs[2], k), px[k]);
                    base++;
                }
            }
            assertTrue(base > 0);
        }
    }

    /**
     * When a beach corner is drawn ({@link ClassicTerrainComposer#BEACH_RULE}):
     * exactly two adjacent land sides and the diagonal opposite them water
     * (its own diagonal may be water too); three sides, two opposite sides,
     * the opposite diagonal land (clip005 #1046) or a diagonal only give
     * quarters; the candidates PAIR_OF_SIDES, ANY_PAIR and OFF.
     */
    public void testBeachRule() {
        final ClassicTerrainComposer c = coastComposer();
        final java.util.function.Predicate<int[]> hasBeach = px -> {
            for (int v : px) if (v >= 244 && v < 248) return true;
            return false;
        };
        final java.util.function.Predicate<int[]> hasQuarter = px -> {
            for (int v : px) if (v >= 210 && v < 242) return true;
            return false;
        };
        final String[][] beach = {
            { "oPo", "GOo", "ooo" },   // N, W
            { "PPo", "GOo", "Poo" },   // N, W, the diagonals NW and SW land
            { "ooo", "oOP", "oPP" },   // E, S, its own diagonal SE land
            { "ooo", "oOP", "oPo" },   // E, S, its own diagonal water
        };
        final String[][] quarters = {
            { "PPo", "GOo", "DDo" },   // N, W, S
            { "oPo", "oOo", "oPo" },   // N, S
            { "oPo", "GOo", "ooP" },   // N, W, the opposite diagonal SE land
            { "Poo", "oOo", "ooo" },   // NW only
            { "oPo", "oOo", "ooo" },   // N only
        };
        for (String[] rows : beach) {
            final int[] px = cell(c, new Grid(rows), 1, 1);
            assertTrue(java.util.Arrays.toString(rows), hasBeach.test(px));
            assertFalse(java.util.Arrays.toString(rows), hasQuarter.test(px));
        }
        for (String[] rows : quarters) {
            final int[] px = cell(c, new Grid(rows), 1, 1);
            assertFalse(java.util.Arrays.toString(rows), hasBeach.test(px));
            assertTrue(java.util.Arrays.toString(rows), hasQuarter.test(px));
        }
        final ClassicTerrainComposer sides = coastComposer(
            RULES.beachRule(ClassicTerrainComposer.BeachRule.PAIR_OF_SIDES));
        assertTrue(hasBeach.test(cell(sides, new Grid(quarters[2]), 1, 1)));
        assertFalse(hasBeach.test(cell(sides, new Grid(quarters[0]), 1, 1)));
        final ClassicTerrainComposer any = coastComposer(
            RULES.beachRule(ClassicTerrainComposer.BeachRule.ANY_PAIR));
        assertTrue(hasBeach.test(cell(any, new Grid(quarters[0]), 1, 1)));
        assertFalse(hasBeach.test(cell(any, new Grid(quarters[1]), 1, 1)));
        final ClassicTerrainComposer off = coastComposer(
            RULES.beachRule(ClassicTerrainComposer.BeachRule.OFF));
        final int[] px = cell(off, new Grid(beach[0]), 1, 1);
        assertFalse(hasBeach.test(px));
        assertTrue(hasQuarter.test(px));
    }

    /**
     * Only explored water gets a coast: explored land next to water is
     * unchanged, dark water next to explored land shows only its fringe, a
     * cell without land (or with an unknown neighbour, F-W6d-MP) is the
     * W6a cell; a diagonal-only quarter keeps the water at its FD px.
     */
    public void testCoastOnlyOnExploredWater() {
        final ClassicTerrainComposer c = coastComposer();
        // Explored land next to explored water: water never bleeds in.
        assertUnchangedBy(c, new Grid("PO"), 0, 0, 2);
        // Dark water next to explored land: the fringe only.
        final Grid dark = new Grid("Po");
        final int[] px = cell(c, dark, 1, 0);
        for (int k = 0; k < 256; k++) {
            assertEquals("px " + k, (ClassicTerrainSheets.inMask(W, k)) ? terrain(2, k)
                         : COAST.index(ClassicTerrainComposer.DARK, k % 16, k / 16), px[k]);
        }
        // No land around, or an unknown neighbour: the W6a cell.
        assertUnchangedBy(c, new Grid("OO"), 0, 0, 10);
        assertUnchangedBy(c, new Grid("O?"), 0, 0, 10);
        // Land diagonal only (NW): quarter 116 (c = 2), FD keeps the water.
        final Grid diag = new Grid("Poo", "oOo", "ooo");
        final int[] want = quarters(layer(10, new int[] { E, 10 }, new int[] { S, 10 },
                                          new int[] { N, 10 }, new int[] { W, 10 }),
                                    null, 2, 0, 0, 0);
        final int[] got = cell(c, diag, 1, 1);
        for (int k = 0; k < 256; k++) assertEquals("px " + k, want[k], got[k]);
        diag.assertLeastKnowledge();
    }

    /**
     * Least knowledge with the coast: a whole view of coast asks the type of
     * an unexplored tile only within Chebyshev 1 of an explored one, and the
     * land still dark gives the explored water its coast (the true type).
     */
    public void testCoastLeastKnowledge() {
        final ClassicTerrainComposer c = coastComposer();
        final Grid g = new Grid(
            "ggggggggggggggg",
            "gooooooooooooog",
            "gooooHHHoooopgg",
            "goooOOOHHOOOOgg",
            "gooOOOOOOOOpppg",
            "goooOOgpOOOpggg",
            "goooooooooooooo",
            "ppppppppppppppp");
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 15; x++) cell(c, g, x, y);
        }
        g.assertLeastKnowledge();
        // (10,4) is explored ocean with the dark plains (11,4) E of it.
        final int[] px = cell(c, g, 10, 4);
        boolean quarter = false;
        for (int v : px) quarter |= v >= 210 && v < 242;
        assertTrue("the coast toward dark land", quarter);
    }

    /**
     * {@link ClassicTerrainComposer#LAND_FACE}: explored coast water bleeds
     * its land face -- its last land side's sprite -- into a land neighbour,
     * explored (fog-start #3162 (41,44) S) or dark (its fringe); dark water
     * does not; without the rule nothing bleeds and the dark tile shows its
     * own.
     */
    public void testLandFace() {
        final ClassicTerrainComposer c = coastComposer();
        // (1,1) water, land N (plains T002) and W (grassland T004): face T004.
        final Grid lit = new Grid("oPo", "GOo", "ooo");
        assertEquals(4, c.landFace(lit, 1, 1));
        assertEquals(-1, c.landFace(new Grid("ooo", "oOo", "ooo"), 1, 1));
        final int[] plains = cell(c, lit, 1, 0);
        final int[] want = layer(2, new int[] { S, 4 });
        for (int k = 0; k < 256; k++) assertEquals("px " + k, want[k], plains[k]);
        lit.assertLeastKnowledge();
        // The plains dark: its fringe from the water is the face, not its own.
        final Grid dark = new Grid("opo", "GOo", "ooo");
        final int[] fringe = cell(c, dark, 1, 0);
        for (int k : c.mask(S)) assertEquals("px " + k, terrain(4, k), fringe[k]);
        dark.assertLeastKnowledge();
        // Dark water bleeds nothing into explored land.
        assertUnchangedBy(c, new Grid("oPo", "Goo", "ooo"), 1, 0, 2);
        // Without the rule.
        final ClassicTerrainComposer off = coastComposer(RULES.landFace(false));
        assertUnchangedBy(off, lit, 1, 0, 2);
        final int[] own = cell(off, dark, 1, 0);
        for (int k : c.mask(S)) assertEquals("px " + k, terrain(2, k), own[k]);
    }

    /** The cell is its own base, 256/256, with a given composer. */
    private static void assertUnchangedBy(ClassicTerrainComposer c, Grid g, int x, int y,
                                          int own) {
        final int[] px = cell(c, g, x, y);
        for (int k = 0; k < 256; k++) assertEquals("px " + k, terrain(own, k), px[k]);
    }

    /** Sheets short of a frame are refused. */
    public void testShortSheetsAreRefused() {
        try {
            new ClassicTerrainComposer(ClassicTerrainSheets.sheet("TERRAIN.SS", 11,
                    (f, k) -> 1), PHYS, id -> 1);
            fail("11 TERRAIN frames");
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            new ClassicTerrainComposer(TERRAIN, ClassicTerrainSheets.sheet("PHYS0.SS",
                    148, (f, k) -> 1), id -> 1);
            fail("no dark tile");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }


    /** The cell's side mask shows a sprite, the rest its own base. */
    private static void assertMask(ClassicTerrainComposer c, Grid g, int x, int y,
                                   int side, int sprite) {
        final int[] px = cell(c, g, x, y);
        final int own = c.spr(g.type(x, y));
        for (int k = 0; k < 256; k++) {
            final int want = (ClassicTerrainSheets.inMask(side, k)) ? terrain(sprite, k)
                : terrain(own, k);
            assertEquals("(" + x + "," + y + ") px " + k, want, px[k]);
        }
    }

    /** The cell is its own base, 256/256. */
    private static void assertUnchanged(ClassicTerrainComposer c, Grid g, int x, int y,
                                        int own) {
        final int[] px = cell(c, g, x, y);
        for (int k = 0; k < 256; k++) assertEquals("px " + k, terrain(own, k), px[k]);
    }
}
