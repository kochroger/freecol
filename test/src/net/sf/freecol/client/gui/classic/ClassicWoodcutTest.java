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
import java.awt.image.IndexColorModel;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

import junit.framework.TestCase;

import net.sf.freecol.tools.classicassets.ClassicAssetDecoderTest;
import net.sf.freecol.tools.classicassets.FfDecoder;


/**
 * Tests of the woodcuts' painters and rules ({@link ClassicWoodcut}, master
 * plan W9): the geometry with a synthetic font and art, the dissolve's
 * order and rate, the WOODCUT.TXT loader, the arrow's dim grey; and, with
 * the pack and {@code -Dclassic.clips}, the composed screen against the
 * six recorded woodcuts (landfall k=1/3/7, fog-start k=1, clip008 k=2/9:
 * 0 px in 12 whole frames, the mouse arrow's box excused, and in the three
 * analysis crops) and the woodcut's palette against the clip's PLTE.  The
 * titles come from the pack, never from here.
 */
public class ClassicWoodcutTest extends TestCase {

    private static final String CLIPS_PROPERTY = "classic.clips";

    private static final int FRAME_COLOUR = 0x102030, RIBBON_L = 0x400000,
        RIBBON_M = 0x004000, RIBBON_R = 0x000040, PICTURE_COLOUR = 0x808080;


    // Synthetic art

    /** A FONT-NP-like font: 'A' 5 wide with values 1, 2 and 3; ' ' 3 wide. */
    static ClassicFont npFont() {
        final int h = 8;
        final int[] a = new int[2 + 5 * h];
        a[0] = 'A';
        a[1] = 5;
        for (int i = 0; i < 5 * h; i++) a[2 + i] = 1 + (i % 3);
        final int[] sp = new int[2 + 3 * h];
        sp[0] = ' ';
        sp[1] = 3;
        final FfDecoder.Font f = FfDecoder.decodePart(ClassicAssetDecoderTest.ffPart(h, 5,
            new int[][] { a, sp }));
        return ClassicFont.fromAtlas(FfDecoder.toAtlas(f), FfDecoder.metrics(f));
    }

    private static BufferedImage solid(int w, int h, int rgb) {
        final BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) img.setRGB(x, y, 0xFF000000 | rgb);
        }
        return img;
    }

    /**
     * Art with a frame whose opening is transparent, a 192x115 picture
     * with one transparent pixel at (5,5), and the title "AA A" for k = 3.
     */
    static ClassicWoodcut.Art syntheticArt() {
        final BufferedImage frame = solid(274, 170, FRAME_COLOUR);
        for (int y = 25; y < 138; y++) {
            for (int x = 40; x < 232; x++) frame.setRGB(x, y, 0);
        }
        final BufferedImage pic = solid(192, 115, PICTURE_COLOUR);
        pic.setRGB(5, 5, 0);
        final BufferedImage[] pictures = new BufferedImage[ClassicWoodcut.ENTRIES];
        pictures[3] = pic;
        return new ClassicWoodcut.Art(frame, new BufferedImage[] {
                solid(18, 14, RIBBON_L), solid(16, 14, RIBBON_M), solid(18, 14, RIBBON_R) },
            pictures, npFont(), Arrays.asList("x", "x", "x", "AA A"));
    }

    private static int rgb(BufferedImage img, int x, int y) {
        return img.getRGB(x, y) & 0xFFFFFF;
    }


    // Tests

    /**
     * The ribbon's pieces, its left edge and the title's: the spec's table
     * (advances 225, 181, 214, 216, 120, 141 ...), the odd advances
     * rounding as the box centring does.
     */
    public void testGeometry() {
        assertEquals(15, ClassicWoodcut.ribbonPieces(225));
        assertEquals(12, ClassicWoodcut.ribbonPieces(181));
        assertEquals(14, ClassicWoodcut.ribbonPieces(214));
        assertEquals(14, ClassicWoodcut.ribbonPieces(216));
        assertEquals(8, ClassicWoodcut.ribbonPieces(120));
        assertEquals(9, ClassicWoodcut.ribbonPieces(141));
        assertEquals(7, ClassicWoodcut.ribbonPieces(109));
        assertEquals(1, ClassicWoodcut.ribbonPieces(16));
        assertEquals(2, ClassicWoodcut.ribbonPieces(17));
        assertEquals(0, ClassicWoodcut.ribbonPieces(0));
        assertEquals(22, ClassicWoodcut.ribbonX(15));
        assertEquals(46, ClassicWoodcut.ribbonX(12));
        assertEquals(30, ClassicWoodcut.ribbonX(14));
        assertEquals(78, ClassicWoodcut.ribbonX(8));
        assertEquals(48, ClassicWoodcut.titleX(225));
        assertEquals(70, ClassicWoodcut.titleX(181));
        assertEquals(53, ClassicWoodcut.titleX(214));
        assertEquals(52, ClassicWoodcut.titleX(216));
        assertEquals(63, ClassicWoodcut.titleX(195));
        assertEquals("WDCUT03.SS", ClassicWoodcut.pictureSheet(3));
        assertEquals("WDCUT13.SS", ClassicWoodcut.pictureSheet(13));
        assertEquals(8, ClassicWoodcut.bit(3));
        assertEquals(0, ClassicWoodcut.bit(-1));
    }

    /**
     * The screen as the spec composes it, on synthetic art: black around,
     * the frame at (23,15), the opening's fill in rows 40-151 (row 152
     * stays black), the ribbon at y 162 centred with ceil(18/16) = 2
     * middle pieces, the title in its three colours from y 165, and the
     * picture drawn last over the frame's rows 152-154; a transparent
     * picture pixel shows the fill.
     */
    public void testCompose() {
        final ClassicWoodcut.Art a = syntheticArt();
        assertTrue(a.has(3));
        assertFalse(a.has(1));
        assertFalse(a.has(0));
        final BufferedImage f = ClassicWoodcut.frameOnly(a, 3);
        assertEquals(0, rgb(f, 0, 0));
        assertEquals(0, rgb(f, 22, 15));
        assertEquals(FRAME_COLOUR, rgb(f, 23, 15));
        assertEquals(FRAME_COLOUR, rgb(f, 296, 184));
        assertEquals(0, rgb(f, 297, 184));
        assertEquals(ClassicWoodcut.FILL, rgb(f, 63, 40));
        assertEquals(ClassicWoodcut.FILL, rgb(f, 254, 151));
        assertEquals(0, rgb(f, 63, 152));        // the opening's last row: black
        assertEquals(FRAME_COLOUR, rgb(f, 62, 100));
        // The ribbon: 36 + 2 * 16 = 68 wide, from (320 - 68) / 2 = 126.
        assertEquals(18, a.font.stringWidth("AA A"));
        assertEquals(126, ClassicWoodcut.ribbonX(2));
        assertEquals(RIBBON_L, rgb(f, 126, 162));
        assertEquals(RIBBON_L, rgb(f, 143, 175));
        assertEquals(RIBBON_M, rgb(f, 144, 162));
        assertEquals(RIBBON_M, rgb(f, 175, 162));
        assertEquals(RIBBON_R, rgb(f, 176, 162));
        assertEquals(RIBBON_R, rgb(f, 193, 175));
        assertEquals(FRAME_COLOUR, rgb(f, 125, 162));
        // The title: x = (320 - 18 + 1) / 2 = 151, its three colours.
        final Set<Integer> title = new HashSet<>();
        for (int y = 165; y < 173; y++) {
            for (int x = 151; x < 169; x++) title.add(rgb(f, x, y));
        }
        assertTrue(title.toString(), title.contains(ClassicWoodcut.TITLE_1));
        assertTrue(title.contains(ClassicWoodcut.TITLE_2));
        assertTrue(title.contains(ClassicWoodcut.TITLE_3));
        assertEquals(RIBBON_M, rgb(f, 151, 164));   // above the glyph cell
        assertEquals(ClassicWoodcut.TITLE_1, rgb(f, 151, 165));
        assertEquals(RIBBON_M, rgb(f, 162, 165));   // the blank
        // The finished screen: the picture last, over rows 152-154.
        final BufferedImage d = ClassicWoodcut.finished(a, 3);
        assertEquals(PICTURE_COLOUR, rgb(d, 63, 40));
        assertEquals(PICTURE_COLOUR, rgb(d, 63, 152));
        assertEquals(PICTURE_COLOUR, rgb(d, 254, 154));
        assertEquals(FRAME_COLOUR, rgb(d, 63, 155));
        assertEquals(ClassicWoodcut.FILL, rgb(d, 68, 45));   // transparent: the fill
        // Outside the picture nothing differs.
        for (int y = 0; y < 200; y++) {
            for (int x = 0; x < 320; x++) {
                if (ClassicWoodcut.PICTURE.contains(x, y)) continue;
                assertEquals(x + "," + y, rgb(f, x, y), rgb(d, x, y));
            }
        }
    }

    /**
     * The dissolve: exactly the differing pixels, in one fixed order (two
     * builds equal), spread over the whole picture in every frame; black,
     * the frame, then all of it gives the finished screen.
     */
    public void testScreenAndOrder() {
        final ClassicWoodcut.Art a = syntheticArt();
        assertNull(ClassicWoodcut.Screen.of(a, 1));
        assertNull(ClassicWoodcut.Screen.of(null, 3));
        final ClassicWoodcut.Screen s = ClassicWoodcut.Screen.of(a, 3);
        final ClassicWoodcut.Screen t = ClassicWoodcut.Screen.of(a, 3);
        final int[] fo = ClassicWoodcut.frameOnly(a, 3).getRGB(0, 0, 320, 200, null, 0, 320);
        final int[] fi = ClassicWoodcut.finished(a, 3).getRGB(0, 0, 320, 200, null, 0, 320);
        int diff = 0;
        for (int i = 0; i < fo.length; i++) if (fo[i] != fi[i]) diff++;
        assertEquals(192 * 115 - 1, diff);          // all but the transparent pixel
        assertEquals(diff, s.changed());
        s.black();
        for (int p : s.pixels()) assertEquals(0, p);
        s.frame();
        final int[] shown = s.pixels();
        for (int i = 0; i < shown.length; i++) assertEquals(fo[i] & 0xFFFFFF, shown[i]);
        assertFalse(s.complete());
        // The first 1/55: spread over the picture (its bounding box nearly all of it).
        assertTrue(s.dissolveTo(diff / 55));
        assertFalse(s.dissolveTo(diff / 55));
        int minX = 999, maxX = -1, minY = 999, maxY = -1;
        final int[] p1 = s.pixels();
        for (int i = 0; i < p1.length; i++) {
            if (p1[i] == (fo[i] & 0xFFFFFF)) continue;
            minX = Math.min(minX, i % 320);
            maxX = Math.max(maxX, i % 320);
            minY = Math.min(minY, i / 320);
            maxY = Math.max(maxY, i / 320);
        }
        assertTrue(minX + ".." + maxX, minX < 70 && maxX > 248);
        assertTrue(minY + ".." + maxY, minY < 46 && maxY > 148);
        // Two builds reveal the same pixels.
        t.frame();
        t.dissolveTo(diff / 55);
        assertTrue(Arrays.equals(p1, t.pixels()));
        assertTrue(s.dissolveTo(diff));
        assertTrue(s.complete());
        final int[] done = s.pixels();
        for (int i = 0; i < done.length; i++) assertEquals(fi[i] & 0xFFFFFF, done[i]);
    }

    /**
     * The rate: 1/55 in the dissolve's first frame, all at 770 ms, linear
     * and catching up; simulated at the clip's 70.0863 Hz the first to the
     * last changed frame span 55 +- 2 frames, each 300-500 px for a
     * picture of the recorded size (spec G5 section 2.3).
     */
    public void testRevealRate() {
        final int n = 21_600;
        assertEquals(0, ClassicWoodcut.revealed(n, -0.1));
        assertEquals(0, ClassicWoodcut.revealed(0, 100));
        assertEquals(n / 55, ClassicWoodcut.revealed(n, 0.0));
        assertEquals(n, ClassicWoodcut.revealed(n, ClassicWoodcut.DISSOLVE_MS));
        assertEquals(n, ClassicWoodcut.revealed(n, 5000));
        int last = -1, first = -1, prev = 0;
        for (int frame = 0; frame < 120; frame++) {
            final int r = ClassicWoodcut.revealed(n, frame * 1000.0 / 70.0863);
            assertTrue(r >= prev);
            if (r > prev) {
                if (first < 0) first = frame;
                last = frame;
                assertTrue(frame + ": " + (r - prev), r - prev >= 300 && r - prev <= 500);
            }
            prev = r;
        }
        final int span = last - first + 1;
        assertTrue("span " + span, Math.abs(span - 55) <= 2);
    }

    /**
     * WOODCUT.TXT: the section's lines in order, the trailing blanks
     * dropped, the game's umlaut codes decoded (0x5C is Ü) and drawn back
     * on FONT-NP's own codes.  With the pack: 14 entries, and the five
     * recorded titles have the advances the clips show.
     */
    public void testWoodcutTitles() throws Exception {
        final ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write("; comment\r\n\r\n@WOODCUT\r\nERSTE\r\nZWEI \\BER\r\nDRITTE \u001EX\r\n\r\n\u001A"
            .getBytes(StandardCharsets.ISO_8859_1));
        final List<String> l = ClassicText.woodcutLines(b.toByteArray());
        assertEquals(3, l.size());
        assertEquals("ERSTE", l.get(0));
        assertEquals("ZWEI \u00dcBER", l.get(1));
        assertEquals("DRITTE \u00c4X", l.get(2));
        assertEquals(92, ClassicFont.toCode('\u00dc'));
        assertEquals(30, ClassicFont.toCode('\u00c4'));
        assertTrue(ClassicText.woodcutLines(new byte[0]).isEmpty());
        assertNull(ClassicText.woodcuts(null));
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final List<String> titles = ClassicText.woodcuts(pack);
        if (titles == null) {
            System.err.println("ClassicWoodcutTest.testWoodcutTitles: no pack, its part skipped");
            return;
        }
        assertEquals(ClassicWoodcut.ENTRIES, titles.size());
        final ClassicFont np = pack.font(ClassicFont.NP);
        assertNotNull(np);
        final int[][] adv = { { 1, 225 }, { 2, 181 }, { 3, 225 }, { 7, 214 }, { 9, 216 },
                              { 4, 120 }, { 5, 141 }, { 6, 195 }, { 8, 126 }, { 10, 228 },
                              { 11, 109 }, { 12, 118 }, { 13, 125 } };
        for (int[] e : adv) assertEquals("entry " + e[0], e[1], np.stringWidth(titles.get(e[0])));
        final ClassicWoodcut.Art a = ClassicWoodcut.load(pack);
        assertNotNull(a);
        for (int k = ClassicWoodcut.FIRST; k <= ClassicWoodcut.LAST; k++) assertTrue("k=" + k, a.has(k));
        assertFalse(a.has(0));
    }

    /** The arrow's grey under the woodcut's palette; nothing else changes. */
    public void testDimArrow() {
        assertNull(ClassicWoodcut.dimArrow(null));
        final BufferedImage arrow = new BufferedImage(3, 1, BufferedImage.TYPE_INT_ARGB);
        arrow.setRGB(0, 0, 0xFF000000 | ClassicWoodcut.ARROW_GREY);
        arrow.setRGB(1, 0, 0xFFFFFFFF);
        arrow.setRGB(2, 0, 0);
        final BufferedImage d = ClassicWoodcut.dimArrow(arrow);
        assertEquals(0xFF000000 | ClassicWoodcut.ARROW_DIM, d.getRGB(0, 0));
        assertEquals(0xFFFFFFFF, d.getRGB(1, 0));
        assertEquals(0, d.getRGB(2, 0) >>> 24);
        assertEquals(0xFF000000 | ClassicWoodcut.ARROW_GREY, arrow.getRGB(0, 0));
    }

    /** The triggers' pure rules (spec G5 section 3). */
    public void testRules() {
        assertEquals(ClassicWoodcut.AZTECS, ClassicWoodcut.contactWoodcut("aztec"));
        assertEquals(ClassicWoodcut.INCAS, ClassicWoodcut.contactWoodcut("inca"));
        assertEquals(ClassicWoodcut.NATIVES, ClassicWoodcut.contactWoodcut("sioux"));
        assertEquals(ClassicWoodcut.NATIVES, ClassicWoodcut.contactWoodcut(null));
        assertEquals(ClassicWoodcut.FOUNTAIN, ClassicWoodcut.messageWoodcut(
            "model.lostCityRumour.fountainOfYouth.description", false));
        assertEquals(ClassicWoodcut.CARGO, ClassicWoodcut.messageWoodcut(
            "model.unit.arriveInEurope", true));
        assertEquals(-1, ClassicWoodcut.messageWoodcut("model.unit.arriveInEurope", false));
        assertEquals(ClassicWoodcut.BURNING,
            ClassicWoodcut.messageWoodcut("combat.raid.building", false));
        assertEquals(ClassicWoodcut.DESTROYED,
            ClassicWoodcut.messageWoodcut("combat.colonyBurned.ours", false));
        assertEquals(ClassicWoodcut.RAID,
            ClassicWoodcut.messageWoodcut("combat.raid.ours", false));
        assertEquals(-1, ClassicWoodcut.messageWoodcut("combat.raid.theirs", false));
        assertEquals(-1, ClassicWoodcut.messageWoodcut(null, true));
        assertEquals("model.nation.english",
            ClassicWoodcut.meetNation("sound.event.meet.model.nation.english"));
        assertNull(ClassicWoodcut.meetNation("sound.event.meet."));
        assertNull(ClassicWoodcut.meetNation("sound.event.buildingComplete"));
        assertNull(ClassicWoodcut.meetNation(null));
    }


    // The pack and the clips

    /** @return The recordings folder, or null (with a note). */
    private File clips() {
        final String p = System.getProperty(CLIPS_PROPERTY);
        final File d = (p == null || p.isBlank()) ? null : new File(p);
        if (d == null || !d.isDirectory()) {
            System.err.println("ClassicWoodcutTest." + getName()
                + ": pixel checks skipped, no recordings (-D" + CLIPS_PROPERTY + ")");
            return null;
        }
        return d;
    }

    /** @return The pack with the woodcuts' art and index sheets, or null (with a note). */
    private ClassicPackFiles pack() {
        final ClassicPackFiles p = ClassicPackFiles.runtime();
        if (p == null || ClassicWoodcut.load(p) == null
            || p.indexSheet("WOODFRAM.SS") == null) {
            System.err.println("ClassicWoodcutTest." + getName()
                + ": pixel checks skipped, no pack with the woodcuts (ant classic-assets)");
            return null;
        }
        return p;
    }

    /** One recorded frame: clip, frame, k, finished, the arrow's box. */
    private static final Object[][] GOLDEN = {
        { "landfall", 2118, 1, false, 73, 0, 9, 14 },
        { "landfall", 2175, 1, true, 73, 0, 9, 14 },
        { "landfall", 11611, 3, false, 73, 0, 9, 14 },
        { "landfall", 11670, 3, true, 73, 0, 9, 14 },
        { "landfall", 15431, 7, false, 73, 0, 9, 14 },
        { "landfall", 15489, 7, true, 73, 0, 9, 14 },
        { "clip008", 3381, 2, false, 163, 48, 10, 14 },
        { "clip008", 3442, 2, true, 163, 48, 10, 14 },
        { "clip008", 52688, 9, false, 319, 199, 1, 1 },
        { "clip008", 52745, 9, true, 319, 199, 1, 1 },
        { "fog-start", 1053, 1, false, 234, 149, 10, 14 },
        { "fog-start", 1110, 1, true, 234, 149, 10, 14 },
    };

    /**
     * V: the composed screen against all six recorded woodcuts, the frame
     * before the dissolve and the finished picture, the whole 320x200
     * screen but the mouse arrow's box: 0 px.
     */
    public void testGoldenAgainstTheClips() throws Exception {
        final File clips = clips();
        final ClassicPackFiles pack = pack();
        if (clips == null || pack == null) return;
        final ClassicWoodcut.Art a = ClassicWoodcut.load(pack);
        final List<String> bad = new ArrayList<>();
        for (Object[] g : GOLDEN) {
            final File f = ClassicTerrainGoldenTest.frameFile(new File(clips, (String) g[0]),
                                                               (Integer) g[1]);
            assertNotNull(g[0] + " #" + g[1], f);
            final BufferedImage clip = ImageIO.read(f);
            final int k = (Integer) g[2];
            final BufferedImage ours = ((Boolean) g[3]) ? ClassicWoodcut.finished(a, k)
                : ClassicWoodcut.frameOnly(a, k);
            final java.awt.Rectangle arrow = new java.awt.Rectangle((Integer) g[4],
                (Integer) g[5], (Integer) g[6], (Integer) g[7]);
            int off = 0;
            for (int y = 0; y < 200; y++) {
                for (int x = 0; x < 320; x++) {
                    if (arrow.contains(x, y)) continue;
                    if (rgb(clip, x, y) != rgb(ours, x, y)) off++;
                }
            }
            if (off != 0) bad.add(g[0] + " #" + g[1] + " k=" + k + ": " + off + " px");
        }
        assertTrue(bad.toString(), bad.isEmpty());
        // A dissolve between them settles on the finished picture.
        final ClassicWoodcut.Screen s = ClassicWoodcut.Screen.of(a, 1);
        s.frame();
        s.dissolveTo(s.changed());
        assertTrue(sameRgb(s.pixels(), ClassicWoodcut.finished(a, 1)));
        // The recorded size: 192x113/115 pictures, 20,000-22,100 px change.
        assertTrue(s.changed() + " px", s.changed() > 20_000 && s.changed() <= 192 * 115);
    }

    private static boolean sameRgb(int[] px, BufferedImage img) {
        for (int i = 0; i < px.length; i++) {
            if (px[i] != (img.getRGB(i % 320, i / 320) & 0xFFFFFF)) return false;
        }
        return true;
    }

    /**
     * V: the landfall analysis's three crops of the finished woodcuts
     * (274x170 at (23,15)): 0 of 46,580 px each.
     */
    public void testGoldenCrops() throws Exception {
        final File clips = clips();
        final ClassicPackFiles pack = pack();
        if (clips == null || pack == null) return;
        final ClassicWoodcut.Art a = ClassicWoodcut.load(pack);
        final File img = new File(clips, "landfall/analysis/img");
        final Object[][] crops = { { "02_", 1 }, { "07_", 3 }, { "13_", 7 } };
        for (Object[] c : crops) {
            File crop = null;
            final File[] fs = img.listFiles();
            for (File f : (fs == null) ? new File[0] : fs) {
                if (f.getName().startsWith((String) c[0]) && f.getName().endsWith("_1x.png")) {
                    crop = f;
                }
            }
            assertNotNull("crop " + c[0], crop);
            final BufferedImage cb = ImageIO.read(crop);
            assertEquals(274, cb.getWidth());
            assertEquals(170, cb.getHeight());
            final BufferedImage ours = ClassicWoodcut.finished(a, (Integer) c[1]);
            int off = 0;
            for (int y = 0; y < 170; y++) {
                for (int x = 0; x < 274; x++) {
                    if (rgb(cb, x, y) != rgb(ours, 23 + x, 15 + y)) off++;
                }
            }
            assertEquals(crop.getName(), 0, off);
        }
    }

    /**
     * V: the woodcut's palette (the index sheets' union plus 94) equals the
     * clip's PLTE entry for entry, at the finished picture of each of the
     * six woodcuts; the fill is index 10, the title's colours 92-94, the
     * arrow's dim grey 7.
     */
    public void testPaletteAgainstTheClips() throws Exception {
        final File clips = clips();
        final ClassicPackFiles pack = pack();
        if (clips == null || pack == null) return;
        final ClassicWoodcut.Art a = ClassicWoodcut.load(pack);
        for (Object[] g : GOLDEN) {
            if (!((Boolean) g[3])) continue;
            final int k = (Integer) g[2];
            final int[] pal = ClassicWoodcut.palette(pack, a, k);
            assertNotNull("k=" + k, pal);
            final BufferedImage clip = ImageIO.read(ClassicTerrainGoldenTest.frameFile(
                new File(clips, (String) g[0]), (Integer) g[1]));
            assertTrue(clip.getColorModel() instanceof IndexColorModel);
            final IndexColorModel cm = (IndexColorModel) clip.getColorModel();
            int n = 0;
            for (int i = 0; i < 256; i++) {
                if (pal[i] < 0) continue;
                n++;
                assertEquals(g[0] + " #" + g[1] + " entry " + i,
                             cm.getRGB(i) & 0xFFFFFF, pal[i]);
            }
            assertTrue("k=" + k + ": " + n, n >= 238 && n <= 247);
            assertEquals(ClassicWoodcut.FILL, pal[10]);
            assertEquals(ClassicWoodcut.TITLE_1, pal[92]);
            assertEquals(ClassicWoodcut.TITLE_3, pal[93]);
            assertEquals(ClassicWoodcut.TITLE_2, pal[ClassicWoodcut.TITLE_2_INDEX]);
            assertEquals(ClassicWoodcut.ARROW_DIM, pal[7]);
        }
        assertNull(ClassicWoodcut.palette(null, a, 1));
        assertNull(ClassicWoodcut.palette(pack, a, 0));
    }
}
