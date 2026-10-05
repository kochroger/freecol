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

import java.util.HashSet;
import java.util.Set;

import junit.framework.TestCase;


/**
 * Headless tests of the own intro emblem ({@link ClassicEmblem}).  There is
 * no reference capture for own art, so these are invariants: period,
 * mirroring, geometry, palette, the subtitle laws.  The font-rasterised
 * pixels themselves are not pinned (they depend on the JDK's fonts).
 */
public class ClassicEmblemTest extends TestCase {

    private static final int W = ClassicEmblem.W, H = ClassicEmblem.H;

    private static int[] paint(int frame, int k, int level) {
        final int[] fb = new int[W * H];
        ClassicEmblem.paint(fb, frame, k, level);
        return fb;
    }

    public void testRepeatsAfterSixteen() {
        for (int f = 0; f < 16; f++) {
            assertTrue("frame " + f, java.util.Arrays.equals(
                paint(f, 28, 8), paint(f + 16, 28, 8)));
        }
        // ... and the sixteen phases are all different pictures.
        final Set<Integer> hashes = new HashSet<>();
        for (int f = 0; f < 16; f++) {
            hashes.add(java.util.Arrays.hashCode(ClassicEmblem.prismFrame(f)));
        }
        assertEquals(16, hashes.size());
    }

    public void testSilhouetteSymmetricAt45() {
        // At 45 degrees the two front faces stand symmetric about the axis
        // x = 159.5: face 0 (turned left) covers the mirror image of face 1.
        final double theta = 45.0;
        int covered = 0, mismatch = 0;
        for (int x = ClassicEmblem.BOX_X; x < ClassicEmblem.BOX_X + ClassicEmblem.BOX_W; x++) {
            final int mirror = 319 - x;          // (x + 0.5) mirrored about 159.5
            final boolean f0 = ClassicEmblem.textureColumn(theta, 0, x) >= 0;
            final boolean f1 = ClassicEmblem.textureColumn(theta, 1, mirror) >= 0;
            if (f0) covered++;
            if (f0 != f1) mismatch++;
        }
        assertTrue("face 0 covers " + covered, covered > 60);
        assertTrue("mismatching columns: " + mismatch, mismatch <= 2);   // +-1 px
    }

    public void testBackFaceIsMirrored() {
        // Face-on: the front face reads left to right 1:1, the back face
        // (face 2, 180 degrees) samples the texture columns in reverse.
        int lastFront = -1, lastBack = Integer.MAX_VALUE, front = 0, back = 0;
        for (int x = 0; x < W; x++) {
            final int f = ClassicEmblem.textureColumn(0.0, 0, x);
            if (f >= 0) {
                assertTrue(f > lastFront);
                lastFront = f;
                front++;
            }
            final int b = ClassicEmblem.textureColumn(0.0, 2, x);
            if (b >= 0) {
                assertTrue(b <= lastBack);
                lastBack = b;
                back++;
            }
        }
        assertEquals(ClassicEmblem.TEX_W, front);           // 1:1
        assertEquals(99, firstColumn(0.0, 0));               // x 99..218
        assertTrue("back face narrower: " + back, back < front && back > front * 0.7);
    }

    private static int firstColumn(double theta, int face) {
        for (int x = 0; x < W; x++) {
            if (ClassicEmblem.textureColumn(theta, face, x) >= 0) return x;
        }
        return -1;
    }

    public void testPalette() {
        final int[] pal = ClassicEmblem.palette();
        assertTrue(pal.length <= 64);
        final Set<Integer> set = new HashSet<>();
        for (int c : pal) set.add(c);
        for (int f = 0; f < 16; f++) {
            for (int level = 0; level <= 8; level += 4) {
                for (int c : paint(f, (f * 2) % 29, level)) {
                    assertTrue(Integer.toHexString(c), set.contains(c));
                }
            }
        }
        // Level 0 is black.
        for (int c : paint(3, 28, 0)) assertEquals(0, c);
    }

    public void testNothingOutsideBoxButSubtitle() {
        for (int f = 0; f < 16; f++) {
            final int[] fb = paint(f, 28, 8);
            for (int i = 0; i < fb.length; i++) {
                final int x = i % W, y = i / W;
                final boolean box = x >= ClassicEmblem.BOX_X
                    && x < ClassicEmblem.BOX_X + ClassicEmblem.BOX_W
                    && y >= ClassicEmblem.BOX_Y && y < ClassicEmblem.BOX_Y + ClassicEmblem.BOX_H;
                final boolean sub = y >= 146 && y <= 170 && x >= 10 && x <= 309;
                if (!box && !sub) assertEquals("(" + x + "," + y + ")", 0, fb[i]);
            }
        }
    }

    public void testSubtitleLaws() {
        assertEquals(24, ClassicEmblem.subtitleWidth(0));
        assertEquals(300, ClassicEmblem.subtitleWidth(28));
        assertEquals(115, ClassicEmblem.subtitleBottom(0));
        assertEquals(170, ClassicEmblem.subtitleBottom(18));
        assertEquals(170, ClassicEmblem.subtitleBottom(28));
        for (int k = 1; k <= 28; k++) {
            assertTrue(ClassicEmblem.subtitleWidth(k) >= ClassicEmblem.subtitleWidth(k - 1));
            assertTrue(ClassicEmblem.subtitleBottom(k) >= ClassicEmblem.subtitleBottom(k - 1));
            final double t = k / 28.0, s = t * t * (3 - 2 * t);
            assertEquals(24 + 276 * s, ClassicEmblem.subtitleWidth(k), 1.0);
            final double t2 = Math.min(k, 18) / 18.0, s2 = t2 * t2 * (3 - 2 * t2);
            assertEquals(115 + 55 * s2, ClassicEmblem.subtitleBottom(k), 1.0);
        }
    }

    public void testEarlySubtitleHiddenUnderBox() {
        // Stages 0..8 lie entirely under the box: the subtitle seems to
        // grow out from under the emblem.
        final int[] none = paint(0, 0, 8);
        for (int k = 0; k <= 8; k++) {
            assertTrue("k=" + k, java.util.Arrays.equals(none, paint(0, k, 8)));
        }
        // From the final stage on it is visible below the box.
        int lit = 0;
        for (int c : paint(0, 28, 8)) if (c != 0) lit++;
        int litNone = 0;
        for (int c : none) if (c != 0) litNone++;
        assertTrue(lit > litNone + 300);
        // The full sprite has ink and only the four greys.
        final int[] sub = ClassicEmblem.subtitleSprite();
        int ink = 0;
        for (int c : sub) {
            if (c == 0) continue;
            ink++;
            boolean grey = false;
            for (int g : ClassicEmblem.GREYS) grey |= (g == c);
            assertTrue(grey);
        }
        assertTrue(ink > 500);
    }

    public void testBandOnTheFrontFace() {
        // Face-on (phase 0) the band is at screen rows 96..110: gold at 96,
        // the darker gold at 97, gold again at 109.
        final int[] fb = paint(0, 0, 8);
        assertEquals(ClassicEmblem.GOLD, fb[96 * W + 159]);
        assertEquals(ClassicEmblem.GOLD_DARK, fb[97 * W + 159]);
        assertEquals(ClassicEmblem.GOLD, fb[109 * W + 159]);
        assertEquals(ClassicEmblem.GOLD_DARK, fb[110 * W + 159]);
        // White capitals in between.
        int white = 0;
        for (int y = 99; y <= 107; y++) {
            for (int x = 99; x < 219; x++) if (fb[y * W + x] == ClassicEmblem.WHITE) white++;
        }
        assertTrue("capitals: " + white, white > 200);
        // Blue script above the band.
        int blue = 0;
        for (int y = 25; y < 95; y++) {
            for (int x = 99; x < 219; x++) {
                final int c = fb[y * W + x];
                if ((c & 0xFF) > ((c >> 16) & 0xFF) + 40) blue++;
            }
        }
        assertTrue("script: " + blue, blue > 1000);
    }
}
