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

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JPanel;

import junit.framework.TestCase;


/**
 * Headless tests of the acceptance frame recorder
 * ({@link ClassicFrameRecorder}): the colour to index map, the indexed PNG
 * writer, the downsampling, the frame diff, ZmbvExtract's timeline format
 * and one short recording end to end.  Synthetic palettes only.
 */
public class ClassicFrameRecorderTest extends TestCase {

    /** A palette of 256 distinct colours, with 120 = 59 and 121 = 56. */
    private static int[] entries() {
        final int[] e = new int[256];
        for (int i = 0; i < 256; i++) e[i] = (i << 16) | ((255 - i) << 8) | (i * 7 & 0xFF);
        e[120] = e[59];
        e[121] = e[56];
        return e;
    }

    private static File tempDir() throws IOException {
        final File d = Files.createTempDirectory("classic-recorder-test").toFile();
        d.deleteOnExit();
        return d;
    }

    public void testExactColoursAndTiesTakeTheLowestIndex() {
        final int[] e = entries();
        final ClassicFrameRecorder.Palette p = ClassicFrameRecorder.Palette.of("t", e);
        for (int i = 0; i < 256; i++) {
            final int want = (i == 120) ? 59 : (i == 121) ? 56 : i;
            assertEquals("entry " + i, want, p.index(e[i]));
        }
        // Alpha / high bits do not matter.
        assertEquals(17, p.index(0xFF000000 | e[17]));
        assertEquals(0, p.missPixels);
    }

    public void testNearestColourIsCountedAsAMiss() {
        final int[] e = new int[256];
        for (int i = 0; i < 256; i++) e[i] = (i << 16) | (i << 8) | i;  // greys
        final ClassicFrameRecorder.Palette p = ClassicFrameRecorder.Palette.of("t", e);
        // (100,100,101) is nearest to grey 100.
        assertEquals(100, p.index(0x646465));
        assertEquals(100, p.index(0x646465));   // the one-entry cache counts too
        assertEquals(2, p.missPixels);
        assertEquals(7, p.index(0x070707));
        assertEquals(2, p.missPixels);
        assertEquals(100, p.index(0x646465));   // the nearest cache
        assertEquals(3, p.missPixels);
        assertTrue(p.missedColours(), p.missedColours().startsWith("1 distinct #646465->100 x3"));
        // Equally near to grey 10 and grey 11: the lower index.
        final int[] two = new int[256];
        two[0] = 0x000000;
        two[1] = 0x0A0A0A;
        two[2] = 0x0C0C0C;
        for (int i = 3; i < 256; i++) two[i] = 0xFFFFFF;
        final ClassicFrameRecorder.Palette q = ClassicFrameRecorder.Palette.of("t", two);
        assertEquals(1, q.index(0x0B0B0B));
    }

    public void testAdaptivePaletteHandsOutIndicesInOrder() {
        final ClassicFrameRecorder.Palette p = ClassicFrameRecorder.Palette.adaptive();
        assertEquals(0, p.index(0x123456));
        assertEquals(1, p.index(0x000000));
        assertEquals(0, p.index(0x123456));
        assertEquals(2, p.index(0xFFFFFF));
        final byte[] plte = p.plte();
        assertEquals(768, plte.length);
        assertEquals(0x12, plte[0] & 0xFF);
        assertEquals(0x56, plte[2] & 0xFF);
        assertEquals(0xFF, plte[6] & 0xFF);
    }

    public void testIndexedPngRoundTripAndPaletteLoad() throws IOException {
        final File d = tempDir();
        final int[] e = entries();
        final ClassicFrameRecorder.Palette p = ClassicFrameRecorder.Palette.of("t", e);
        final byte[] pix = new byte[ClassicFrameRecorder.W * ClassicFrameRecorder.H];
        for (int i = 0; i < pix.length; i++) pix[i] = (byte)(i * 31);
        final File f = new File(d, "frame_000000.png");
        ClassicFrameRecorder.writeIndexedPng(f, ClassicFrameRecorder.W,
            ClassicFrameRecorder.H, pix, p.plte());
        final BufferedImage img = ImageIO.read(f);
        assertEquals(320, img.getWidth());
        assertEquals(200, img.getHeight());
        assertTrue(img.getColorModel() instanceof IndexColorModel);
        final IndexColorModel cm = (IndexColorModel)img.getColorModel();
        assertEquals(256, cm.getMapSize());
        for (int i = 0; i < 256; i++) assertEquals(e[i], cm.getRGB(i) & 0xFFFFFF);
        final int[] got = new int[pix.length];
        img.getRaster().getPixels(0, 0, 320, 200, got);
        for (int i = 0; i < pix.length; i++) assertEquals(pix[i] & 0xFF, got[i]);
        f.deleteOnExit();

        // The written frame is a palette file, and so is a raw 768-byte one.
        final ClassicFrameRecorder.Palette fromPng = ClassicFrameRecorder.Palette.load(f);
        assertEquals(59, fromPng.index(e[120]));
        assertEquals(200, fromPng.index(e[200]));
        final File raw = new File(d, "pal.bin");
        Files.write(raw.toPath(), p.plte());
        raw.deleteOnExit();
        final ClassicFrameRecorder.Palette fromRaw = ClassicFrameRecorder.Palette.load(raw);
        assertEquals(56, fromRaw.index(e[121]));
        assertEquals(255, fromRaw.index(e[255]));
        // Unreadable: adaptive, not a failure.
        assertTrue(ClassicFrameRecorder.Palette.fromProperty(new File(d, "none.png").getPath())
            .source.startsWith("adaptive"));
        assertTrue(ClassicFrameRecorder.Palette.fromProperty(null).source.startsWith("adaptive"));
    }

    public void testDownsampleReadsTheBlockCentres() {
        final int[] e = entries();
        final ClassicFrameRecorder.Palette p = ClassicFrameRecorder.Palette.of("t", e);
        final int s = 3;
        final Rectangle canvas = new Rectangle(20, 50, 320 * s, 200 * s);
        final BufferedImage img = new BufferedImage(1000, 650, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 200; y++) {
            for (int x = 0; x < 320; x++) {
                final int c = e[(x + 3 * y) & 0xFF];
                // Only the centre pixel of the block carries the colour: the
                // rest is noise the sampler must not read.
                for (int dy = 0; dy < s; dy++) {
                    for (int dx = 0; dx < s; dx++) {
                        final int px = canvas.x + x * s + dx, py = canvas.y + y * s + dy;
                        if (py >= img.getHeight()) continue;
                        img.setRGB(px, py, (dx == 1 && dy == 1) ? c : 0x0F0F0F);
                    }
                }
            }
        }
        final byte[] out = new byte[320 * 200];
        ClassicFrameRecorder.downsample(img, canvas, s, p, out);
        for (int y = 0; y < 200; y++) {
            for (int x = 0; x < 320; x++) {
                final int want = canvas.y + y * s + 1 >= img.getHeight()
                    ? p.index(0)   // below the image: black
                    : p.index(e[(x + 3 * y) & 0xFF]);
                assertEquals("(" + x + "," + y + ")", want, out[y * 320 + x] & 0xFF);
            }
        }
        // No copy yet: all black.
        ClassicFrameRecorder.downsample(null, null, 1, p, out);
        for (byte b : out) assertEquals(p.index(0), b & 0xFF);
    }

    public void testDiffCountsPixelsAndZmbvBlocks() {
        assertEquals(260, ClassicFrameRecorder.BLOCKS);
        final byte[] a = new byte[320 * 200];
        final byte[] b = a.clone();
        int[] d = ClassicFrameRecorder.diff(a, b);
        assertEquals(0, d[0]);
        assertEquals(0, d[1]);
        b[0] = 1;
        b[15 * 320 + 15] = 1;          // same block
        b[16] = 1;                     // the next block
        b[199 * 320 + 319] = 1;        // the last, half-height block row
        d = ClassicFrameRecorder.diff(a, b);
        assertEquals(4, d[0]);
        assertEquals(3, d[1]);
    }

    /**
     * The waits' sleeps (FINAL "Open" item 5): to about 1.5 ms before the
     * deadline, then the spin, and never a multiple of 10 ms, whose sleep
     * Windows would wake on the 15.6-ms tick.
     */
    public void testSleepsAreNeverWholeTensOfMs() {
        final long ms = 1_000_000L;
        assertEquals(0L, ClassicFrameRecorder.sleepMillis(-5 * ms));
        assertEquals(0L, ClassicFrameRecorder.sleepMillis(0L));
        assertEquals(0L, ClassicFrameRecorder.sleepMillis(2 * ms));
        assertEquals(0L, ClassicFrameRecorder.sleepMillis(2 * ms + 400_000L));   // 0.9 ms -> spin
        assertEquals(1L, ClassicFrameRecorder.sleepMillis(3 * ms));
        assertEquals(9L, ClassicFrameRecorder.sleepMillis(11_500_000L));          // 10 -> 9
        assertEquals(19L, ClassicFrameRecorder.sleepMillis(21_600_000L));         // 20 -> 19
        assertEquals(327L, ClassicFrameRecorder.sleepMillis(328_500_000L));       // the blink
        assertEquals(14L, ClassicFrameRecorder.sleepMillis(16_430_000L));         // a slide step
        for (long rem = 0; rem <= 700 * ms; rem += 37_000L) {
            final long s = ClassicFrameRecorder.sleepMillis(rem);
            assertTrue(rem + ": " + s, s >= 0);
            assertTrue(rem + ": " + s, s == 0 || s % 10 != 0);
            assertTrue(rem + ": wakes before the deadline", s * ms <= rem - 1_500_000L || s == 0);
            if (rem > 13 * ms) {
                assertTrue(rem + ": sleeps to within 2.5 ms", rem - s * ms <= 2_500_000L + ms);
            }
        }
        assertEquals(50L, ClassicFrameRecorder.EVENTS_PROBE_MS);
        assertEquals(0L, ClassicFrameRecorder.EVENTS_PROBE_MS % 10);
    }

    public void testTimelineRowsMatchZmbvExtract() {
        assertEquals("frameIndex,timeMs,keyframe,paletteChanged,pixelsChanged,"
            + "changedBlocks,pngFile,paletteEntriesChanged,status",
            ClassicFrameRecorder.TIMELINE_HEADER);
        // Rows copied from the landfall clip's timeline.csv.
        assertEquals("0,0.000,1,1,64000,260,frame_000000.png,256,ok\n",
            ClassicFrameRecorder.timelineRow(0, 64000, 260, "frame_000000.png"));
        assertEquals("1,14.268,0,0,0,0,,0,ok\n",
            ClassicFrameRecorder.timelineRow(1, 0, 0, ""));
        assertEquals("28639,408624.778,0,0,0,0,,0,ok\n",
            ClassicFrameRecorder.timelineRow(28639, 0, 0, ""));
        assertEquals("1148,16379.805,0,0,138,4,frame_001148.png,0,ok\n",
            ClassicFrameRecorder.timelineRow(1148, 138, 4, "frame_001148.png"));
        // W6c: palette steps, as the fog-start clip's rows #7 and #169.
        assertEquals("7,99.877,0,1,0,0,frame_000007.png,8,ok\n",
            ClassicFrameRecorder.timelineRow(7, 0, 0, "frame_000007.png", 8));
        assertEquals("169,2411.313,0,1,127,4,frame_000169.png,8,ok\n",
            ClassicFrameRecorder.timelineRow(169, 127, 4, "frame_000169.png", 8));
        assertEquals("0,0.000,1,1,64000,260,frame_000000.png,256,ok\n",
            ClassicFrameRecorder.timelineRow(0, 64000, 260, "frame_000000.png", 8));
        final byte[] a = new byte[768], b = a.clone();
        assertEquals(0, ClassicFrameRecorder.entriesChanged(a, b));
        b[3 * 120 + 2] = 1;
        b[3 * 127] = 1;
        assertEquals(2, ClassicFrameRecorder.entriesChanged(a, b));
    }

    /**
     * W6c (design 10 §9.2.2): the record palette's colour cycle rotates
     * with the frame's phase -- the PLTE is the game palette at that phase
     * whatever the file's own phase (landfall #0 is phase 1) -- and the
     * colour to index map follows; a colour two entries hold takes the
     * lower, as before.  An adaptive palette does not rotate.
     */
    public void testThePaletteRotatesWithThePhase() {
        final ClassicGamePalette gp = new ClassicGamePalette(
            ClassicGamePaletteTest.viceroyLike(), null);
        final ClassicFrameRecorder.Palette p
            = ClassicFrameRecorder.Palette.of("lf0", gp.rgb(1));
        assertEquals(1, gp.phaseOf(p.fileEntries()));
        assertEquals(0, p.rotation());
        assertFalse(p.rotate(0, 120, 8));
        for (int phase = 0; phase < 16; phase++) {
            p.rotate(phase - 1, 120, 8);
            assertEquals(Math.floorMod(phase - 1, 8), p.rotation());
            final byte[] plte = p.plte();
            for (int i = 0; i < 256; i++) {
                assertEquals("phase " + phase + " #" + i, gp.rgb(phase, i), p.entry(i));
                assertEquals(gp.rgb(phase, i), ((plte[3 * i] & 0xFF) << 16)
                    | ((plte[3 * i + 1] & 0xFF) << 8) | (plte[3 * i + 2] & 0xFF));
            }
            for (int i = 120; i < 128; i++) {
                final int c = gp.rgb(phase, i);
                final int want = (c == gp.rgb(0, 56)) ? 56 : (c == gp.rgb(0, 59)) ? 59 : i;
                assertEquals("phase " + phase + " #" + i, want, p.index(c));
            }
        }
        assertEquals(0, p.missPixels);
        assertTrue(java.util.Arrays.equals(gp.rgb(1), p.fileEntries()));
        final ClassicFrameRecorder.Palette ad = ClassicFrameRecorder.Palette.adaptive();
        assertNull(ad.fileEntries());
        assertFalse(ad.rotate(3, 120, 8));
    }

    /**
     * W9: a woodcut's palette goes into the record palette entry for entry
     * at the current rotation (a cycle entry shows its woodcut colour, not
     * a rotated one), its colours map to their own indices, and the
     * entries of before (the game's, a portrait's) come back with
     * {@code restore}; an adaptive palette is left alone.
     */
    public void testTheWoodcutPaletteAndItsReturn() {
        final ClassicGamePalette gp = new ClassicGamePalette(
            ClassicGamePaletteTest.viceroyLike(), null);
        final ClassicFrameRecorder.Palette p
            = ClassicFrameRecorder.Palette.of("lf0", gp.rgb(1));
        p.rotate(3, 120, 8);
        final int[] portrait = new int[256];
        java.util.Arrays.fill(portrait, -1);
        portrait[200] = 0x010203;
        assertTrue(p.overlay(portrait));
        final int[] before = new int[256];
        for (int i = 0; i < 256; i++) before[i] = p.entry(i);
        final int[] w = new int[256];
        java.util.Arrays.fill(w, -1);
        w[7] = ClassicWoodcut.ARROW_DIM;
        w[10] = ClassicWoodcut.FILL;
        w[121] = 0xABCDEF;
        w[127] = 0x123456;
        w[200] = 0x445566;
        assertTrue(p.woodcut(w, 120, 8));
        for (int i = 0; i < 256; i++) {
            assertEquals("#" + i, (w[i] >= 0) ? w[i] : before[i], p.entry(i));
        }
        assertEquals(121, p.index(0xABCDEF));
        assertEquals(7, p.index(ClassicWoodcut.ARROW_DIM));
        assertFalse(p.rotate(3, 120, 8));               // the water stands still
        assertEquals(0x123456, p.entry(127));
        assertFalse(p.woodcut(w, 120, 8));              // the same again: nothing
        assertTrue(p.restore(120, 8));
        for (int i = 0; i < 256; i++) assertEquals("#" + i, before[i], p.entry(i));
        assertFalse(p.restore(120, 8));
        assertEquals(0x010203, p.entry(200));           // the portrait's slot is back
        // The cycle runs on from the game's entries.
        p.rotate(4, 120, 8);
        for (int i = 0; i < 256; i++) {
            if (i == 200) continue;
            assertEquals("#" + i, gp.rgb(5, i), p.entry(i));
        }
        assertFalse(ClassicFrameRecorder.Palette.adaptive().woodcut(w, 120, 8));
    }

    /**
     * W6c (design 10 §9.2.4): the index hint resolves a cycling pixel whose
     * colour a lower index holds too -- 127 at phase 0 has 59's colour, 120
     * has 56's -- only in the map area, only where the colour is the hint's
     * entry.
     */
    public void testTheHintResolves127AtPhase0Against59() {
        final ClassicGamePalette gp = new ClassicGamePalette(
            ClassicGamePaletteTest.viceroyLike(), null);
        final ClassicFrameRecorder.Palette p
            = ClassicFrameRecorder.Palette.of("fs19", gp.rgb(0));
        final BufferedImage img = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
        final int c59 = gp.rgb(0, 127), c56 = gp.rgb(0, 120);
        assertEquals(gp.rgb(0, 59), c59);
        assertEquals(gp.rgb(0, 56), c56);
        img.setRGB(10, 20, c59);    // hint 127
        img.setRGB(11, 20, c59);    // no hint: 59
        img.setRGB(12, 20, c56);    // hint 120
        img.setRGB(13, 20, gp.rgb(0, 30));   // hint 127, another colour: the colour
        img.setRGB(250, 20, c59);   // outside the map area: 59
        img.setRGB(10, 3, c59);     // the menu strip: 59
        final byte[] hint = new byte[ClassicFrameRecorder.MAP_W * ClassicFrameRecorder.MAP_H];
        hint[(20 - 8) * 240 + 10] = (byte) 127;
        hint[(20 - 8) * 240 + 12] = (byte) 120;
        hint[(20 - 8) * 240 + 13] = (byte) 127;
        final byte[] out = new byte[320 * 200];
        assertEquals(2, ClassicFrameRecorder.downsample(img, new Rectangle(0, 0, 320, 200),
                                                        1, p, hint, out));
        assertEquals(127, out[20 * 320 + 10] & 0xFF);
        assertEquals(59, out[20 * 320 + 11] & 0xFF);
        assertEquals(120, out[20 * 320 + 12] & 0xFF);
        assertEquals(30, out[20 * 320 + 13] & 0xFF);
        assertEquals(59, out[20 * 320 + 250] & 0xFF);
        assertEquals(59, out[3 * 320 + 10] & 0xFF);
        // Without the hint: the lowest index, as before.
        ClassicFrameRecorder.downsample(img, new Rectangle(0, 0, 320, 200), 1, p, out);
        assertEquals(59, out[20 * 320 + 10] & 0xFF);
        assertEquals(56, out[20 * 320 + 12] & 0xFF);
    }

    /**
     * W6c end to end: a recording whose terrain changes phase.  The frame
     * after a step is a {@code paletteChanged=1} row with 8 entries and a
     * PNG even with no changed pixel; each PNG's PLTE is the game palette
     * at the frame's phase (the file being phase 1, as landfall #0); the
     * hint keeps the cycling pixels' indices across the step; a step with
     * no cycling pixel on the screen gets its palette frame without a
     * paint; the summary counts it all.
     */
    public void testPaletteStepsInARecording() throws Exception {
        final File d = tempDir();
        final ClassicGamePalette gp = new ClassicGamePalette(
            ClassicGamePaletteTest.viceroyLike(), null);
        final ClassicFrameRecorder rec = ClassicFrameRecorder.open(d,
            ClassicFrameRecorder.Palette.of("lf0", gp.rgb(1)));
        final int[] phase = { 0 };
        final boolean[] cycling = { true };
        rec.setTerrainProbe(new ClassicFrameRecorder.TerrainProbe() {
                @Override
                public int paintedPhase() {
                    return phase[0];
                }

                @Override
                public ClassicGamePalette gamePalette() {
                    return gp;
                }

                @Override
                public boolean indexHint(byte[] out) {
                    java.util.Arrays.fill(out, (byte) 0);
                    if (cycling[0]) {
                        out[(20 - 8) * 240 + 10] = (byte) 127;
                        out[(20 - 8) * 240 + 12] = (byte) 120;
                    }
                    return true;
                }
            });
        final JPanel pane = new JPanel();
        pane.setBounds(0, 0, 320, 200);
        final BufferedImage screen = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
        final Graphics g = screen.getGraphics();
        final java.util.function.IntConsumer paint = ph -> rec.paintThrough(pane, g,
            new Rectangle(0, 0, 320, 200), 1, cg -> {
                cg.setColor(new Color(gp.rgb(0, 200)));   // a plain index
                cg.fillRect(0, 0, 320, 200);
                cg.setColor(new Color(gp.rgb(0, 59)));
                cg.fillRect(11, 20, 1, 1);           // plain ocean 59
                if (cycling[0]) {
                    cg.setColor(new Color(gp.rgb(ph, 127)));
                    cg.fillRect(10, 20, 1, 1);
                    cg.setColor(new Color(gp.rgb(ph, 120)));
                    cg.fillRect(12, 20, 1, 1);
                }
            });
        paint.accept(0);                 // phase 0: 127 shows 59's colour
        awaitFrames(rec, rec.framesSampled() + 6);
        phase[0] = 1;                    // a step: only the palette changes
        paint.accept(1);
        awaitFrames(rec, rec.framesSampled() + 6);
        cycling[0] = false;              // the lane leaves the view
        paint.accept(1);
        awaitFrames(rec, rec.framesSampled() + 6);
        phase[0] = 2;                    // a step with nothing to paint
        rec.notePaletteStep("p=2 k=2 test", 1_000_000L, 2_000_000L, false);
        awaitFrames(rec, rec.framesSampled() + 6);
        g.dispose();
        rec.close();

        final List<String> tl = Files.readAllLines(new File(d, "timeline.csv").toPath(),
                                                   StandardCharsets.UTF_8);
        final java.util.Map<Integer, String> paletteOnly = new java.util.TreeMap<>();
        String phase0 = null;
        for (String row : tl.subList(1, tl.size())) {
            final String[] f = row.split(",", -1);
            if (f[6].isEmpty()) {
                assertEquals(row, "0", f[3]);
                continue;
            }
            final BufferedImage png = ImageIO.read(new File(d, f[6]));
            final IndexColorModel cm = (IndexColorModel) png.getColorModel();
            final int ph = gp.phaseOf(plte(cm));
            assertTrue(row + ": a phase of the game palette", ph >= 0);
            if (f[3].equals("1") && !f[0].equals("0")) assertEquals(row, "8", f[7]);
            if (f[3].equals("1") && f[4].equals("0")) paletteOnly.put(ph, row);
            // The cycling pixels keep their indices (never 56 or 59), the
            // ocean is 59 (frame 0 may come before the first paint).
            final int a = png.getRaster().getSample(10, 20, 0);
            final int c = png.getRaster().getSample(12, 20, 0);
            assertFalse(row + ": " + a, a == 56 || a == 59);
            assertFalse(row + ": " + c, c == 56 || c == 59);
            if (a == 127) {
                assertEquals(row, 120, c);
                if (ph == 0) phase0 = f[6];
            } else if (!f[0].equals("0")) {
                assertEquals(row, 200, a);
            }
            if (!f[0].equals("0")) {
                assertEquals(row, 59, png.getRaster().getSample(11, 20, 0));
            }
            for (int i = 0; i < 256; i++) {
                assertEquals(row + " #" + i, gp.rgb(ph, i), cm.getRGB(i) & 0xFFFFFF);
            }
        }
        assertNotNull("a frame at phase 0 with the lane", phase0);
        assertTrue("palette-only frames " + paletteOnly, paletteOnly.containsKey(1));
        assertTrue("the step without a paint " + paletteOnly, paletteOnly.containsKey(2));
        final List<String> sum = Files.readAllLines(new File(d, "summary.txt").toPath(),
                                                    StandardCharsets.UTF_8);
        String frames = null, hints = null, steps = null;
        for (String l : sum) {
            if (l.startsWith("paletteFrames: ")) frames = l;
            if (l.startsWith("hintPixels: ")) hints = l;
            if (l.startsWith("paletteSteps: ")) steps = l;
        }
        assertNotNull(sum.toString(), frames);
        assertTrue(frames, frames.endsWith("record palette phase 1"));
        assertTrue(frames, frames.contains("(palette only "));
        assertNotNull(hints);
        assertTrue(hints, Long.parseLong(hints.substring(12)) > 0);
        assertTrue(steps, steps.startsWith("paletteSteps: 1; late mean 2.000 max 2.000 ms;"
                                           + " painted 0"));
        final List<String> ev = Files.readAllLines(new File(d, "events.log").toPath(),
                                                   StandardCharsets.UTF_8);
        boolean step = false, file = false;
        for (String l : ev) {
            if (l.endsWith(",palette-step,p=2 k=2 test")) step = true;
            if (l.contains(",palette-file,phase=1 cycle=8@120/35")) file = true;
        }
        assertTrue(step);
        assertTrue(file);
        for (File f : d.listFiles()) f.deleteOnExit();
    }

    /** The 256 entries of a colour model, 0xRRGGBB. */
    private static int[] plte(IndexColorModel cm) {
        final int[] e = new int[256];
        for (int i = 0; i < Math.min(256, cm.getMapSize()); i++) e[i] = cm.getRGB(i) & 0xFFFFFF;
        return e;
    }

    /**
     * Wait until the sampler has written at least {@code n} timeline rows
     * (at least 120 ms), instead of a fixed sleep a loaded machine can
     * starve (FINAL "Open" item 10).
     */
    private static void awaitFrames(ClassicFrameRecorder rec, long n)
        throws InterruptedException {
        final long start = System.nanoTime();
        final long end = start + 10_000_000_000L;
        while ((rec.framesSampled() < n || System.nanoTime() - start < 120_000_000L)
               && System.nanoTime() < end) {
            Thread.sleep(10);
        }
        assertTrue("frames sampled: " + rec.framesSampled() + " < " + n,
                   rec.framesSampled() >= n);
    }

    public void testShortRecording() throws Exception {
        final File d = tempDir();
        final int[] e = new int[256];
        for (int i = 0; i < 256; i++) e[i] = (i << 16) | (i << 8) | i;
        final ClassicFrameRecorder rec = ClassicFrameRecorder.open(d,
            ClassicFrameRecorder.Palette.of("greys", e));
        final JPanel pane = new JPanel();
        pane.setBounds(0, 0, 640, 400);
        final BufferedImage screen = new BufferedImage(640, 400, BufferedImage.TYPE_INT_RGB);
        final Graphics g = screen.getGraphics();
        // Paint grey 40 everywhere, then let the sampler see it.
        rec.paintThrough(pane, g, new Rectangle(0, 0, 640, 400), 2, cg -> {
                cg.setColor(new Color(40, 40, 40));
                cg.fillRect(0, 0, 640, 400);
            });
        awaitFrames(rec, 4);
        // A partial repaint: only the clip is painted and published.
        g.setClip(0, 0, 32, 32);
        rec.paintThrough(pane, g, new Rectangle(0, 0, 640, 400), 2, cg -> {
                cg.setColor(new Color(200, 200, 200));
                cg.fillRect(0, 0, 640, 400);
            });
        g.dispose();
        assertEquals(200, screen.getRGB(0, 0) & 0xFF);       // shown on the screen
        assertEquals(40, screen.getRGB(100, 100) & 0xFF);
        awaitFrames(rec, rec.framesSampled() + 12);
        rec.log(System.nanoTime(), "test", "a,b c");
        // A note is an event and a summary line; the last value wins (W6e).
        rec.addNote("terrain", "fallback no ssidx/TERRAIN.SS.idx");
        rec.addNote("terrain", "index TERRAIN.SS=12");
        rec.close();
        rec.close();   // idempotent

        final List<String> tl = Files.readAllLines(new File(d, "timeline.csv").toPath(),
                                                   StandardCharsets.UTF_8);
        assertEquals(ClassicFrameRecorder.TIMELINE_HEADER, tl.get(0));
        assertTrue("frames: " + tl.size(), tl.size() > 10);
        int pngRows = 0, n = 0;
        String changeRow = null;
        for (String row : tl.subList(1, tl.size())) {
            final String[] f = row.split(",", -1);
            assertEquals(9, f.length);
            assertEquals(n++, Integer.parseInt(f[0]));
            assertEquals("ok", f[8]);
            if (!f[6].isEmpty()) {
                pngRows++;
                assertTrue(new File(d, f[6]).isFile());
                if (!f[0].equals("0")) changeRow = row;
            }
        }
        // Frame 0 is the black/grey start, then exactly one change: the 16x16
        // native pixels of the clip (32x32 at scale 2).
        assertTrue("png rows " + pngRows, pngRows == 2 || pngRows == 3);
        assertNotNull(changeRow);
        assertTrue(changeRow, changeRow.contains(",256,1,frame_")
                   || changeRow.contains(",64000,260,frame_"));
        final BufferedImage last = ImageIO.read(new File(d, changeRow.split(",")[6]));
        assertEquals(200, last.getRaster().getSample(0, 0, 0));
        assertEquals(200, last.getRaster().getSample(15, 15, 0));
        assertEquals(40, last.getRaster().getSample(16, 16, 0));

        final List<String> ev = Files.readAllLines(new File(d, "events.log").toPath(),
                                                   StandardCharsets.UTF_8);
        assertEquals("nanoTime,ms,frame,event,detail", ev.get(0));
        assertTrue(ev.get(1), ev.get(1).contains(",recorder-start,"));
        boolean test = false;
        int terrain = 0;
        for (String l : ev) {
            if (l.endsWith(",test,a,b c")) test = true;
            if (l.endsWith(",terrain,fallback no ssidx/TERRAIN.SS.idx")
                || l.endsWith(",terrain,index TERRAIN.SS=12")) terrain++;
        }
        assertTrue(test);
        assertEquals(2, terrain);
        assertTrue(ev.get(ev.size() - 1).contains(",recorder-stop,"));
        final List<String> sum = Files.readAllLines(new File(d, "summary.txt").toPath(),
                                                    StandardCharsets.UTF_8);
        assertTrue(sum.toString(), sum.contains("palette: greys"));
        assertTrue(sum.toString(), sum.contains("paletteMissPixels: 0"));
        assertTrue(sum.toString(), sum.contains("terrain: index TERRAIN.SS=12"));
        assertFalse(sum.toString(), sum.toString().contains("terrain: fallback"));
        for (File f : d.listFiles()) f.deleteOnExit();
    }
}
