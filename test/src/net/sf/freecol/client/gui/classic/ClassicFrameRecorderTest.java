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
        Thread.sleep(120);
        // A partial repaint: only the clip is painted and published.
        g.setClip(0, 0, 32, 32);
        rec.paintThrough(pane, g, new Rectangle(0, 0, 640, 400), 2, cg -> {
                cg.setColor(new Color(200, 200, 200));
                cg.fillRect(0, 0, 640, 400);
            });
        g.dispose();
        assertEquals(200, screen.getRGB(0, 0) & 0xFF);       // shown on the screen
        assertEquals(40, screen.getRGB(100, 100) & 0xFF);
        Thread.sleep(120);
        rec.log(System.nanoTime(), "test", "a,b c");
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
        for (String l : ev) {
            if (l.endsWith(",test,a,b c")) test = true;
        }
        assertTrue(test);
        assertTrue(ev.get(ev.size() - 1).contains(",recorder-stop,"));
        final List<String> sum = Files.readAllLines(new File(d, "summary.txt").toPath(),
                                                    StandardCharsets.UTF_8);
        assertTrue(sum.toString(), sum.contains("palette: greys"));
        assertTrue(sum.toString(), sum.contains("paletteMissPixels: 0"));
        for (File f : d.listFiles()) f.deleteOnExit();
    }
}
