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
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.imageio.ImageIO;

import junit.framework.TestCase;


/**
 * Golden checks of the index pipeline (M1c design 10 §4, item W6e) on the
 * <b>real</b> converted pack ({@link ClassicPackFiles#runtime}).  Without
 * a pack, or with a pack converted before W6e, every test logs a note and
 * passes (Critic 10(d)).  Also the Europe backdrop's palette (W22p),
 * which fails on a pack converted before W22p, on purpose.
 *
 * <p>The expected numbers are measurements, not art: the designer's own
 * decode of the install ({@code m1c-sea/10-support/tools/SsIdx.java},
 * logged in {@code sprite_census.txt}, {@code palettes_and_pack.txt},
 * {@code clip_vs_viceroy.txt}) and the design's facts F1, F3, F4, F6, F8.
 */
public class ClassicIndexGoldenTest extends TestCase {

    /** Cycling px of the PHYS0 rivers 001-015 and 017-031 (sprite_census.txt). */
    private static final int[] RIVER_CYCLING = {
        12, 8, 10, 6, 16, 18, 13, 5, 10, 12, 17, 9, 18, 19, 21,
        12, 8, 10, 6, 16, 11, 12, 5, 10, 12, 16, 9, 12, 12, 21
    };

    /** Transparent px of the same rivers. */
    private static final int[] RIVER_TRANSPARENT = {
        181, 183, 128, 171, 121, 105, 92, 170, 96, 115, 96, 86, 66, 65, 100,
        181, 183, 160, 171, 152, 136, 146, 170, 124, 150, 128, 116, 128, 135, 101
    };

    /** Opaque px of the coast quarters PHYS0 108-139, 8x8 each. */
    private static final int[] QUARTER_OPAQUE = {
        64, 64, 64, 64, 57, 55, 59, 59, 64, 64, 64, 64, 60, 61, 54, 61,
        60, 60, 60, 59, 55, 49, 48, 48, 60, 59, 57, 53, 40, 40, 48, 47
    };

    /** Opaque px of PHYS0 140-153 (river mouths, the dark tile, beach corners). */
    private static final int[] P140_OPAQUE = {
        48, 64, 39, 38, 39, 62, 40, 43, 256, 112, 175, 204, 180, 208
    };

    /** The TERRAIN.SS frame of every FreeCol tile type (design 10 §6.1). */
    private static final Object[][] TERRAIN_SPRITES = {
        { 0, "tundra", "borealForest" }, { 1, "desert", "scrubForest" },
        { 2, "plains", "mixedForest", "hills", "mountains" },
        { 3, "prairie", "broadleafForest" }, { 4, "grassland", "coniferForest" },
        { 5, "savannah", "tropicalForest" }, { 6, "marsh", "wetlandForest" },
        { 7, "swamp", "rainForest" }, { 9, "arctic" },
        { 10, "ocean", "lake", "greatRiver" }, { 11, "highSeas" }
    };

    private ClassicPackFiles pack;


    @Override
    protected void setUp() {
        final ClassicPackFiles p = ClassicPackFiles.runtime();
        final String status = ClassicPackFiles.indexStatus(p);
        if (status.startsWith("index ")) {
            this.pack = p;
        } else {
            System.err.println("ClassicIndexGoldenTest." + getName()
                + ": skipped, " + status + " (convert the pack: ant classic-assets)");
            this.pack = null;
        }
    }

    private ClassicIndexSheet sheet(String ss) {
        final ClassicIndexSheet s = this.pack.indexSheet(ss);
        assertNotNull(ss, s);
        return s;
    }

    private static int count(ClassicIndexSheet s, int f, int lo, int hi) {
        int n = 0;
        for (byte b : s.pixels(f)) {
            final int i = b & 0xFF;
            if (i >= lo && i <= hi) n++;
        }
        return n;
    }

    private static int cycling(ClassicIndexSheet s, int f) {
        return count(s, f, 120, 127);
    }

    private static int transparent(ClassicIndexSheet s, int f) {
        return count(s, f, ClassicIndexSheet.TRANSPARENT, ClassicIndexSheet.TRANSPARENT);
    }

    /** The PNG of a frame, read from disk (not cached). */
    private BufferedImage png(String stem) throws IOException {
        final String rel = this.pack.string(ClassicPackFiles.ssKey(stem));
        assertNotNull(stem, rel);
        return ImageIO.read(new File(this.pack.directory(), rel));
    }


    /**
     * The census of the cycling pixels (F4): T011 62 (5/6/9/9/11/8/8/6 on
     * 120-127), T007 2, the rivers, the beach corners 30/30/24/25 and
     * ICONS.123 3; no other frame of TERRAIN, PHYS0 or ICONS has one.
     */
    public void testCyclingCensus() {
        if (this.pack == null) return;
        final ClassicIndexSheet t = sheet(ClassicPackFiles.TERRAIN_SS);
        final ClassicIndexSheet p = sheet(ClassicPackFiles.PHYS0_SS);
        final ClassicIndexSheet ic = sheet("ICONS.SS");
        assertEquals(12, t.size());
        assertEquals(154, p.size());
        assertEquals(131, ic.size());
        assertEquals(62, cycling(t, 11));
        final int[] perIndex = { 5, 6, 9, 9, 11, 8, 8, 6 };
        for (int i = 0; i < 8; i++) {
            assertEquals("T011 #" + (120 + i), perIndex[i], count(t, 11, 120 + i, 120 + i));
        }
        assertEquals(2, cycling(t, 7));
        assertEquals(124, t.index(7, 10, 4));
        assertEquals(123, t.index(7, 4, 6));
        final Map<String, Integer> found = new HashMap<>();
        for (int f = 0; f < t.size(); f++) {
            assertEquals(16, t.width(f));
            assertEquals(16, t.height(f));
            assertEquals("T" + f, 0, transparent(t, f));
            if (cycling(t, f) > 0) found.put("T" + f, cycling(t, f));
        }
        for (int f = 0; f < p.size(); f++) {
            if (cycling(p, f) > 0) found.put("P" + f, cycling(p, f));
        }
        for (int f = 0; f < ic.size(); f++) {
            if (cycling(ic, f) > 0) found.put("I" + f, cycling(ic, f));
        }
        final Map<String, Integer> expected = new HashMap<>();
        expected.put("T7", 2);
        expected.put("T11", 62);
        for (int r = 0; r < 30; r++) {
            final int f = (r < 15) ? 1 + r : 2 + r;
            expected.put("P" + f, RIVER_CYCLING[r]);
            assertEquals("P" + f, RIVER_TRANSPARENT[r], transparent(p, f));
        }
        expected.put("P150", 30);
        expected.put("P151", 30);
        expected.put("P152", 24);
        expected.put("P153", 25);
        expected.put("I123", 3);
        assertEquals(new java.util.TreeMap<>(expected), new java.util.TreeMap<>(found));
        assertEquals(5, cycling(p, 24));       // F5: the river of FS #6543
        assertEquals(13, ic.width(123));
        assertEquals(11, ic.height(123));
        assertEquals(45, transparent(ic, 123));
    }

    /**
     * The fog pieces (F8, §6.1): P148 is T010 + 2 (60 x 56, 61 x 165,
     * 62 x 35); the side masks P104-107 are 15 px of index 0 each and
     * overlap in exactly (13,0), (0,2), (15,13), (2,15); T011 has 5/2/4/3
     * cycling px under the N/E/S/W masks.
     */
    public void testFogPieces() {
        if (this.pack == null) return;
        final ClassicIndexSheet t = sheet(ClassicPackFiles.TERRAIN_SS);
        final ClassicIndexSheet p = sheet(ClassicPackFiles.PHYS0_SS);
        final int[] hist = new int[256];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                assertEquals(x + "," + y, t.index(10, x, y) + 2, p.index(148, x, y));
                hist[p.index(148, x, y)]++;
            }
        }
        assertEquals(56, hist[60]);
        assertEquals(165, hist[61]);
        assertEquals(35, hist[62]);
        final boolean[][] mask = new boolean[4][256];
        final int[] seaLaneUnder = { 5, 2, 4, 3 };
        for (int d = 0; d < 4; d++) {
            int n = 0, cyc = 0;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    final int i = p.index(104 + d, x, y);
                    if (i == ClassicIndexSheet.TRANSPARENT) continue;
                    assertEquals("mask " + (104 + d), 0, i);
                    mask[d][y * 16 + x] = true;
                    n++;
                    if (t.index(11, x, y) >= 120 && t.index(11, x, y) <= 127) cyc++;
                }
            }
            assertEquals("mask " + (104 + d), 15, n);
            assertEquals("T011 under mask " + (104 + d), seaLaneUnder[d], cyc);
        }
        // N=0 E=1 S=2 W=3: the four corner overlaps, and nothing else.
        final int[][] pairs = { { 0, 1, 13, 0 }, { 0, 3, 0, 2 }, { 2, 1, 15, 13 },
                                { 2, 3, 2, 15 } };
        for (int[] q : pairs) {
            final Set<Integer> both = new TreeSet<>();
            for (int k = 0; k < 256; k++) if (mask[q[0]][k] && mask[q[1]][k]) both.add(k);
            assertEquals(q[0] + "&" + q[1], Set.of(q[3] * 16 + q[2]), both);
        }
        for (int k = 0; k < 256; k++) {
            assertFalse("N&S " + k, mask[0][k] && mask[2][k]);
            assertFalse("E&W " + k, mask[1][k] && mask[3][k]);
        }
        int all = 0;
        for (int k = 0; k < 256; k++) {
            if (mask[0][k] || mask[1][k] || mask[2][k] || mask[3][k]) all++;
        }
        assertEquals(56, all);
    }

    /**
     * The coast and beach pieces keep their own sizes and key (F6, F7):
     * quarters P108-139 are 8x8 with the census' opaque counts and
     * 108-111 are 64 px of index 0 ("keep"); P140-153 are 16x16.  T008 and
     * T001 share 193 px.
     */
    public void testCoastPieces() {
        if (this.pack == null) return;
        final ClassicIndexSheet t = sheet(ClassicPackFiles.TERRAIN_SS);
        final ClassicIndexSheet p = sheet(ClassicPackFiles.PHYS0_SS);
        for (int q = 0; q < 32; q++) {
            final int f = 108 + q;
            assertEquals("P" + f, 8, p.width(f));
            assertEquals("P" + f, 8, p.height(f));
            assertEquals("P" + f, QUARTER_OPAQUE[q], 64 - transparent(p, f));
            if (q < 4) assertEquals("P" + f, 64, count(p, f, 0, 0));
        }
        for (int k = 0; k < P140_OPAQUE.length; k++) {
            assertEquals("P" + (140 + k), 16, p.width(140 + k));
            assertEquals("P" + (140 + k), P140_OPAQUE[k], 256 - transparent(p, 140 + k));
        }
        int same = 0;
        for (int k = 0; k < 256; k++) if (t.pixels(8)[k] == t.pixels(1)[k]) same++;
        assertEquals(193, same);
    }

    /**
     * The game palette is VICEROY.PAL, DOSBox-exact (F2, F3): its water
     * entries, the default cycle 8@120/35 from CYCLE.DAT, and the three
     * clip frames as phases 0, 7 and 1.
     */
    public void testGamePalette() {
        if (this.pack == null) return;
        final ClassicGamePalette pal = ClassicGamePalette.of(this.pack);
        assertNotNull(pal);
        assertEquals(ClassicGamePalette.Cycle.DEFAULT, this.pack.cycleSpec());
        assertTrue(new File(this.pack.directory(), ClassicPackFiles.CYCLE_FILE).isFile());
        final int[] b = pal.base();
        assertTrue(Arrays.equals(ClassicGamePaletteTest.VICEROY_120,
                                 Arrays.copyOfRange(b, 120, 128)));
        assertTrue(Arrays.equals(ClassicGamePaletteTest.VICEROY_54,
                                 Arrays.copyOfRange(b, 54, 63)));
        final int[] e = b.clone();
        System.arraycopy(ClassicGamePaletteTest.FS0_120, 0, e, 120, 8);
        assertEquals(7, pal.phaseOf(e));
        System.arraycopy(ClassicGamePaletteTest.LF0_120, 0, e, 120, 8);
        assertEquals(1, pal.phaseOf(e));
        assertEquals(0, pal.phaseOf(b));
    }

    /**
     * The sheets agree with the PNGs (F1): every PHYS0 and ICONS pixel is
     * the game palette's colour of its index (their own palettes equal
     * VICEROY where used), and a 0xFD index is a transparent PNG pixel.
     * TERRAIN differs exactly at its 53 px of 121-126 (T011 51, T007 2),
     * and T011's PNG shows the 4 colours that are not in the game palette
     * (W1 finding 6), which drawing through the indices removes.
     */
    public void testSheetsAgreeWithThePngs() throws IOException {
        if (this.pack == null) return;
        final int[] b = this.pack.gamePalette();
        final Set<Integer> game = new TreeSet<>();
        for (int c : b) game.add(c);
        int terrainOff = 0;
        final Set<Integer> notInGame = new TreeSet<>();
        for (String ss : new String[] { ClassicPackFiles.TERRAIN_SS,
                                        ClassicPackFiles.PHYS0_SS, "ICONS.SS" }) {
            final ClassicIndexSheet s = sheet(ss);
            for (int f = 0; f < s.size(); f++) {
                final BufferedImage img = png(String.format("%s.%03d", ss, f));
                assertEquals(ss + f, s.width(f), img.getWidth());
                assertEquals(ss + f, s.height(f), img.getHeight());
                for (int y = 0; y < s.height(f); y++) {
                    for (int x = 0; x < s.width(f); x++) {
                        final int i = s.index(f, x, y);
                        final int argb = img.getRGB(x, y);
                        if (i == ClassicIndexSheet.TRANSPARENT) {
                            assertEquals(ss + f + " " + x + "," + y, 0, argb >>> 24);
                            continue;
                        }
                        assertEquals(ss + f + " " + x + "," + y, 0xFF, argb >>> 24);
                        if ((argb & 0xFFFFFF) == b[i]) continue;
                        assertEquals(ss + f + " " + x + "," + y + " #" + i,
                                     ClassicPackFiles.TERRAIN_SS, ss);
                        assertTrue("#" + i, i >= 121 && i <= 126);
                        terrainOff++;
                        if (f == 11 && !game.contains(argb & 0xFFFFFF)) {
                            notInGame.add(argb & 0xFFFFFF);
                        }
                    }
                }
            }
        }
        assertEquals(53, terrainOff);
        assertEquals(Set.of(0x5D79B2, 0x556DAE, 0x4155A2, 0x34499A), notInGame);
    }

    /**
     * Every SS file of the pack has its sheet, with as many frames as it
     * has PNGs and the PNGs' sizes; within a sheet one index is always one
     * PNG colour, and 0xFD always a transparent pixel.
     */
    public void testEverySsFileHasItsSheet() throws IOException {
        if (this.pack == null) return;
        final File[] files = new File(this.pack.directory(), ClassicPackFiles.INDEX_DIR)
            .listFiles((d, n) -> n.endsWith(".SS" + ClassicPackFiles.INDEX_SUFFIX));
        assertNotNull(files);
        int frames = 0;
        for (File file : files) {
            final String ss = file.getName().substring(0, file.getName().length()
                - ClassicPackFiles.INDEX_SUFFIX.length());
            final ClassicIndexSheet s = sheet(ss);
            assertNull(ss, this.pack.string(ClassicPackFiles.ssKey(
                String.format("%s.%03d", ss, s.size()))));
            final int[] colour = new int[256];
            Arrays.fill(colour, -1);
            for (int f = 0; f < s.size(); f++) {
                final BufferedImage img = png(String.format("%s.%03d", ss, f));
                assertEquals(ss + f, s.width(f), img.getWidth());
                assertEquals(ss + f, s.height(f), img.getHeight());
                for (int y = 0; y < s.height(f); y++) {
                    for (int x = 0; x < s.width(f); x++) {
                        final int i = s.index(f, x, y);
                        final int argb = img.getRGB(x, y);
                        if (i == ClassicIndexSheet.TRANSPARENT) {
                            assertEquals(ss + f, 0, argb >>> 24);
                        } else if (colour[i] < 0) {
                            colour[i] = argb;
                        } else {
                            assertEquals(ss + f + " #" + i, colour[i], argb);
                        }
                    }
                }
                frames++;
            }
        }
        int pngs = 0;
        for (String key : new TreeSet<>(Arrays.asList(new File(this.pack.directory(),
                 "resources/images/ss").list()))) {
            if (key.endsWith(".png")) pngs++;
        }
        assertEquals(pngs, frames);
    }

    /** EUROPE.PIK's own blues 54-59, lighter than the game's (W22p). */
    private static final int[] EUROPE_OWN_54 = {
        0x718EC7, 0x657DBE, 0x5971B2, 0x4D61AA, 0x41519E, 0x384596
    };

    /** EUROPE.PIK's px of 54-59: the sea under the piers, 57 the market fill. */
    private static final int[] EUROPE_BLUES = { 1036, 511, 460, 6071, 595, 619 };

    /**
     * The Europe frames (W22p): the frame, its px showing one of the game
     * blues 54-59 where the backdrop shows another colour (the game's own
     * drawing: the three buttons at x 272-319, y 80-127, goods icons), and
     * its px differing from the backdrop at all (the title, ships, holds,
     * goods, prices, labels, the arrow).  The backdrop of a pack converted
     * before W22p gives 7,610 / 7,890 / 7,890 / 8,034 / 8,034 / 7,990 blues
     * and 13,744 / 13,693 / 13,693 / 13,546 / 13,386 / 13,406 differences.
     */
    private static final Object[][] EUROPE_FRAMES = {
        { "clip005/frame_018653.png", 141, 6275 },  // opens after the arrival
        { "clip006/frame_007980.png", 141, 5944 },  // opens, no palette change
        { "clip006/frame_008120.png", 141, 5944 },  // the plan's acceptance frame
        { "clip008/frame_017007.png", 152, 5664 },  // E1, 0.071 s after the menu
        { "clip008/frame_052974.png", 141, 5493 },  // E2, the merchantman in port
        { "clip008/frame_053113.png", 141, 5557 },  // E2, the title line
    };

    /** The pack's EUROPE.PIK backdrop, read from disk (not cached). */
    private BufferedImage europe() throws IOException {
        final String rel = this.pack.string(ClassicPackFiles.pikKey("EUROPE.PIK"));
        assertNotNull("EUROPE.PIK", rel);
        return ImageIO.read(new File(this.pack.directory(), rel));
    }

    /**
     * W22p: the original opens the Europe screen without loading
     * EUROPE.PIK's palette, so the pack's backdrop has only game palette
     * colours, its 54-59 the game's blues in their exact counts and none
     * of the file's own lighter ones.  Fails on a pack converted before
     * W22p: re-run ant classic-assets.
     */
    public void testEuropeBackdropUsesTheGamePalette() throws IOException {
        if (this.pack == null) return;
        final BufferedImage img = europe();
        assertEquals(320, img.getWidth());
        assertEquals(200, img.getHeight());
        final Set<Integer> game = new TreeSet<>();
        for (int c : this.pack.gamePalette()) game.add(c);
        final Map<Integer, Integer> count = new HashMap<>();
        int notInGame = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                final int c = img.getRGB(x, y) & 0xFFFFFF;
                count.merge(c, 1, Integer::sum);
                if (!game.contains(c)) notInGame++;
            }
        }
        assertEquals("px not in the game palette (a pack from before W22p?"
                     + " re-run ant classic-assets)", 0, notInGame);
        for (int k = 0; k < EUROPE_BLUES.length; k++) {
            assertEquals("#" + (54 + k), EUROPE_BLUES[k],
                count.getOrDefault(ClassicGamePaletteTest.VICEROY_54[k], 0).intValue());
            assertNull("own #" + (54 + k), count.get(EUROPE_OWN_54[k]));
        }
    }

    /**
     * W22p against the clips (needs {@code -Dclassic.clips}): over six
     * Europe frames of three clips ({@link #EUROPE_FRAMES}) the backdrop
     * shows the frame's blues wherever the game drew none over them, and
     * differs from the frame only in the measured px of the game's drawing.
     * (The G4 spec's own decode of EUROPE.PIK's indices: 0 px off on every
     * pixel where the frame shows the picture's index.)
     */
    public void testEuropeBackdropAgainstTheClips() throws IOException {
        if (this.pack == null) return;
        final String clips = System.getProperty(ClassicTerrainGoldenTest.CLIPS_PROPERTY);
        final File dir = (clips == null) ? null : new File(clips);
        if (dir == null || !new File(dir, "clip008").isDirectory()) {
            System.err.println("ClassicIndexGoldenTest." + getName()
                + ": skipped, no recordings (-D"
                + ClassicTerrainGoldenTest.CLIPS_PROPERTY + ")");
            return;
        }
        final BufferedImage img = europe();
        final Set<Integer> blues = new TreeSet<>();
        for (int k = 0; k < EUROPE_BLUES.length; k++) {
            blues.add(ClassicGamePaletteTest.VICEROY_54[k]);
        }
        final StringBuilder got = new StringBuilder();
        for (Object[] f : EUROPE_FRAMES) {
            final BufferedImage frame = ImageIO.read(new File(dir, (String) f[0]));
            assertNotNull((String) f[0], frame);
            int blueOff = 0, off = 0;
            for (int y = 0; y < 200; y++) {
                for (int x = 0; x < 320; x++) {
                    final int fc = frame.getRGB(x, y) & 0xFFFFFF;
                    final int pc = img.getRGB(x, y) & 0xFFFFFF;
                    if (fc == pc) continue;
                    off++;
                    if (blues.contains(fc)) blueOff++;
                }
            }
            got.append(' ').append(f[0]).append('=').append(blueOff).append('/').append(off);
            assertEquals((String) f[0] + " blues", ((Integer) f[1]).intValue(), blueOff);
            assertEquals((String) f[0] + " all", ((Integer) f[2]).intValue(), off);
        }
        System.out.println("ClassicIndexGoldenTest: Europe backdrop vs clips," + got);
    }

    /** The alias table gives each tile type its TERRAIN.SS frame (§6.1). */
    public void testTerrainSpriteForEveryTileType() {
        if (this.pack == null) return;
        int n = 0;
        for (Object[] row : TERRAIN_SPRITES) {
            for (int k = 1; k < row.length; k++) {
                assertEquals((String) row[k], ((Integer) row[0]).intValue(),
                    this.pack.terrainSpriteFor("model.tile." + row[k]));
                n++;
            }
        }
        assertEquals(23, n);
        assertEquals(-1, this.pack.terrainSpriteFor("model.tile.nothing"));
    }
}
