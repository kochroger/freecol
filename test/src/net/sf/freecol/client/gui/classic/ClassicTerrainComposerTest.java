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
