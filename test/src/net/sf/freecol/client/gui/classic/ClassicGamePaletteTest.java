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
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;

import junit.framework.TestCase;


/**
 * The game palette and its cycling phases (M1c design 10 F3, §4 and
 * §7.3).  The fixture is VICEROY.PAL's cycling entries and the 120-127 of
 * three clip frames, as measured in {@code m1c-sea/10-support/clip_vs_viceroy.txt}.
 */
public class ClassicGamePaletteTest extends TestCase {

    /** VICEROY.PAL 120-127 (F3). */
    static final int[] VICEROY_120 = {
        0x4D65AE, 0x5169B2, 0x4961A6, 0x4159A2, 0x384D9E, 0x30459A, 0x2C3C96, 0x283892
    };

    /** VICEROY.PAL 54-62, the ocean's colours (56 and 59 recur in 120 and 127). */
    static final int[] VICEROY_54 = {
        0x698AC3, 0x5D79BA, 0x4D65AE, 0x4159A6, 0x34499E, 0x283892, 0x202C8A,
        0x181C7D, 0x101075
    };

    /** 120-127 of fog-start #19: VICEROY's order. */
    static final int[] FS19_120 = VICEROY_120;

    /** 120-127 of fog-start #0. */
    static final int[] FS0_120 = {
        0x5169B2, 0x4961A6, 0x4159A2, 0x384D9E, 0x30459A, 0x2C3C96, 0x283892, 0x4D65AE
    };

    /** 120-127 of landfall #0. */
    static final int[] LF0_120 = {
        0x283892, 0x4D65AE, 0x5169B2, 0x4961A6, 0x4159A2, 0x384D9E, 0x30459A, 0x2C3C96
    };

    /** A base palette: distinct greys-and-hues, VICEROY's water entries. */
    static int[] viceroyLike() {
        final int[] b = new int[256];
        for (int i = 0; i < 256; i++) b[i] = 0xFF000000 | (i << 16) | ((255 - i) << 8) | (i * 3 & 0xFF);
        System.arraycopy(VICEROY_120, 0, b, 120, 8);
        System.arraycopy(VICEROY_54, 0, b, 54, 9);
        return b;
    }

    private static int[] at120(int[] e) {
        final int[] out = new int[256];
        System.arraycopy(e, 0, out, 120, 8);
        return out;
    }


    public void testConstants() {
        assertEquals(120, ClassicGamePalette.CYCLE_FIRST);
        assertEquals(8, ClassicGamePalette.CYCLE_COUNT);
        assertEquals(35, ClassicGamePalette.CYCLE_TICKS);
        assertEquals(575.05, ClassicGamePalette.PERIOD_MS, 1e-9);
        final ClassicGamePalette.Cycle d = ClassicGamePalette.Cycle.DEFAULT;
        assertEquals(8, d.count);
        assertEquals(120, d.first);
        assertEquals(35, d.ticks);
        assertEquals(ClassicGamePalette.PERIOD_MS, d.periodMs(), 1e-12);
        for (int[] bad : new int[][] { { 0, 120, 35 }, { 8, -1, 35 }, { 8, 249, 35 },
                                       { 8, 120, 0 } }) {
            try {
                new ClassicGamePalette.Cycle(bad[0], bad[1], bad[2]);
                fail(bad[0] + "@" + bad[1] + "/" + bad[2]);
            } catch (IllegalArgumentException expected) {
                // ok
            }
        }
        new ClassicGamePalette.Cycle(8, 248, 1);   // the last entries
    }

    /**
     * {@code P_p[120 + i] = B[120 + ((i - p) mod 8)]}: phase 0 is B, a step
     * moves each colour one entry up ({@code new[120+i] = old[120+i-1]}),
     * {@code P_8 = P_0}, negative phases wrap, and nothing outside 120-127
     * ever changes.
     */
    public void testPhasesRotateTheCycle() {
        final int[] b = viceroyLike();
        final ClassicGamePalette pal = new ClassicGamePalette(b, null);
        assertEquals(ClassicGamePalette.Cycle.DEFAULT, pal.cycle());
        assertEquals(8, pal.phaseCount());
        for (int i = 0; i < 256; i++) assertEquals(b[i] & 0xFFFFFF, pal.rgb(0, i));
        for (int p = 0; p < 8; p++) {
            final int[] cur = pal.rgb(p), next = pal.rgb(p + 1);
            for (int i = 0; i < 256; i++) {
                if (i < 120 || i > 127) {
                    assertEquals("p" + p + " #" + i, b[i] & 0xFFFFFF, cur[i]);
                } else {
                    assertEquals("p" + p + " #" + i, b[120 + Math.floorMod(i - 120 - p, 8)]
                                 & 0xFFFFFF, cur[i]);
                    assertEquals("step p" + p + " #" + i,
                                 cur[120 + Math.floorMod(i - 121, 8)], next[i]);
                }
            }
        }
        assertTrue(java.util.Arrays.equals(pal.rgb(0), pal.rgb(8)));
        assertTrue(java.util.Arrays.equals(pal.rgb(7), pal.rgb(-1)));
        assertEquals(7, pal.normalise(-1));
        assertEquals(0, pal.normalise(16));
        // Copies: changing one changes nothing inside.
        pal.rgb(0)[120] = 0;
        pal.base()[120] = 0;
        assertEquals(0x4D65AE, pal.rgb(0, 120));
        assertTrue(pal.cycles(120) && pal.cycles(127));
        assertFalse(pal.cycles(119) || pal.cycles(128));
    }

    /**
     * F3 on the clip frames: fog-start #19 is phase 0, fog-start #0 phase 7
     * and landfall #0 phase 1; at phase 0, 120 shows 56's colour and 127
     * shows 59's.  An order no phase has is -1.
     */
    public void testTheClipsArePhasesOfViceroy() {
        final ClassicGamePalette pal = new ClassicGamePalette(viceroyLike(), null);
        assertEquals(0, pal.phaseOf(at120(FS19_120)));
        assertEquals(7, pal.phaseOf(at120(FS0_120)));
        assertEquals(1, pal.phaseOf(at120(LF0_120)));
        for (int p = 0; p < 8; p++) assertEquals(p, pal.phaseOf(pal.rgb(p)));
        final int[] odd = at120(FS19_120);
        odd[123] = odd[124];
        assertEquals(-1, pal.phaseOf(odd));
        assertEquals(-1, pal.phaseOf(new int[100]));
        assertEquals(-1, pal.phaseOf(null));
        assertEquals(pal.rgb(0, 56), pal.rgb(0, 120));
        assertEquals(pal.rgb(0, 59), pal.rgb(0, 127));
        // FS #0 steps to #19's phase 0 at FS #7: one step from phase 7.
        assertTrue(java.util.Arrays.equals(pal.rgb(7 + 1), pal.rgb(0)));
    }

    /** One opaque 8-bit, 256-entry colour model per phase, shared. */
    public void testColourModels() {
        final ClassicGamePalette pal = new ClassicGamePalette(viceroyLike(), null);
        for (int p = 0; p < 8; p++) {
            final IndexColorModel cm = pal.colorModel(p);
            assertSame(cm, pal.colorModel(p + 8));
            assertEquals(256, cm.getMapSize());
            assertEquals(8, cm.getPixelSize());
            assertFalse(cm.hasAlpha());
            assertEquals(-1, cm.getTransparentPixel());
            for (int i = 0; i < 256; i++) {
                assertEquals(0xFF000000 | pal.rgb(p, i), cm.getRGB(i));
            }
        }
        assertNotSame(pal.colorModel(0), pal.colorModel(1));
    }

    /**
     * Swapping the colour model is the whole palette step: one index
     * raster seen through two phases, drawn scaled x3 and x5 with nearest
     * neighbour, shows {@code P_p[index]} in every pixel.
     */
    public void testIndexRasterDrawsThroughAnyPhase() {
        final ClassicGamePalette pal = new ClassicGamePalette(viceroyLike(), null);
        final int w = 7, h = 3;
        final BufferedImage base = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_INDEXED,
                                                     pal.colorModel(0));
        final WritableRaster r = base.getRaster();
        final int[] idx = { 56, 59, 120, 121, 127, 0, 253 };
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) r.setSample(x, y, 0, idx[(x + y) % idx.length]);
        }
        for (int p : new int[] { 0, 3, 7 }) {
            final BufferedImage view = new BufferedImage(pal.colorModel(p), r, false, null);
            for (int s : new int[] { 3, 5 }) {
                final BufferedImage dst = new BufferedImage(w * s, h * s,
                                                            BufferedImage.TYPE_INT_RGB);
                final Graphics2D g = dst.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                   RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g.drawImage(view, 0, 0, w * s, h * s, null);
                g.dispose();
                for (int y = 0; y < h * s; y++) {
                    for (int x = 0; x < w * s; x++) {
                        assertEquals("p" + p + " s" + s + " " + x + "," + y,
                            pal.rgb(p, idx[(x / s + y / s) % idx.length]),
                            dst.getRGB(x, y) & 0xFFFFFF);
                    }
                }
            }
        }
    }

    /** A pack's palette with its cycle; none without a palette file. */
    public void testOfPack() {
        assertNull(ClassicGamePalette.of(null));
        try {
            new ClassicGamePalette(new int[255], null);
            fail("255 entries");
        } catch (IllegalArgumentException expected) {
            // ok
        }
        final ClassicGamePalette four = new ClassicGamePalette(viceroyLike(),
            new ClassicGamePalette.Cycle(4, 10, 2));
        assertEquals(4, four.phaseCount());
        assertEquals(viceroyLike()[13] & 0xFFFFFF, four.rgb(1, 10));
        assertEquals(viceroyLike()[120] & 0xFFFFFF, four.rgb(1, 120));
    }
}
