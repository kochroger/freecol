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
import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import net.sf.freecol.client.gui.classic.ClassicIntro.Sprite;


/**
 * Headless tests of the chart painter ({@link ClassicIntro#paintChart}) with
 * a SYNTHETIC 960x132 chart, one-pixel sprites whose colour names series
 * and frame, and a synthetic script.  The real material is checked against
 * the native captures by the preview harness (0 px on 104..165).
 */
public class ClassicIntroTest extends TestCase {

    private static final int W = 320, H = 200;
    private static final int BORD = 0x010203;

    /** A 1x1 sprite of colour {@code c} with its anchor top-left at (x,y). */
    private static Sprite px(int c, int x, int y) {
        return new Sprite(1, 1, new int[] { 0xFF000000 | c }, x, y);
    }

    /** {@code n} frames of series {@code s}: colour 0x(s+1)00 0kk. */
    private static Sprite[] series(int s, int n, int x, int y) {
        final Sprite[] r = new Sprite[n];
        for (int k = 0; k < n; k++) r[k] = px(((s + 1) << 16) | k, x, y);
        return r;
    }

    private static ClassicIntro.Assets assets() {
        final int[] bord = new int[W * H];
        Arrays.fill(bord, BORD);
        final int[] chart = new int[960 * 132];
        for (int y = 0; y < 132; y++) {
            for (int x = 0; x < 960; x++) chart[y * 960 + x] = 0x800000 | (y << 10) | x;
        }
        final Sprite[] ship = { px(0xAA0000, 0, 0), px(0xAA0001, 0, 0) };
        final Sprite[][] ser = new Sprite[10][];
        for (int i = 0; i < 10; i++) ser[i] = new Sprite[0];
        ser[0] = series(0, 3, 10, 50);          // wind: R=1, vanishes
        ser[1] = series(1, 7, 20, 40);          // sun: fitted rule
        ser[2] = series(2, 3, 30, 70);          // R=0: holds
        ser[3] = series(3, 4, 40, 80);          // superseded
        ser[4] = series(4, 2, 50, 20);          // above the chart window: clipped
        final int[] crd = new int[4 * 3];
        Arrays.fill(crd, 0xFFCC0000);
        final Sprite[][] credits = {
            { new Sprite(4, 3, crd, 0, 0) }, new Sprite[0], new Sprite[0] };
        return new ClassicIntro.Assets(bord, chart, 960, ship, ser, credits);
    }

    private static ClassicOpeningScript script() {
        final List<String> path = new ArrayList<>();
        for (int i = 0; i < 10; i++) path.add((700 + i) + ", " + (60 + i));
        return ClassicOpeningScript.parse(Arrays.asList(
            "@CREDITS",
            "10, 12, 0, 1",
            "@OPENING",
            "0, 5, 1, 640",
            "1, 40, 0, 640",
            "2, 5, 0, 640",
            "3, 5, 0, 640",
            "3, 8, 0, 640",
            "4, 5, 0, 640",
            "-1, 891, 0, 0",
            "0, 0, 0, 0"), path);
    }

    private static int[] paint(int f) {
        final int[] fb = new int[W * H];
        ClassicIntro.paintChart(fb, assets(), script(), f);
        return fb;
    }

    private static int at(int[] fb, int x, int y) {
        return fb[y * W + x];
    }

    public void testPan() {
        assertEquals(640, ClassicIntro.chartOffset(960, 0));
        assertEquals(540, ClassicIntro.chartOffset(960, 100));
        assertEquals(0, ClassicIntro.chartOffset(960, 640));
        assertEquals(0, ClassicIntro.chartOffset(960, 769));
        // Chart row 0 at screen row 24, column ox; frame and wood untouched.
        int[] fb = paint(100);
        assertEquals(0x800000 | 540, at(fb, 0, 24));
        assertEquals(0x800000 | (131 << 10) | 540 + 319, at(fb, 319, 155));
        assertEquals(BORD, at(fb, 0, 23));
        assertEquals(BORD, at(fb, 0, 156));
        fb = paint(700);
        assertEquals(0x800000 | 5, at(fb, 5, 24));
    }

    public void testShipFollowsThePath() {
        // f = 5: ship frame 5 % 2 = 1 at PATH[4] = (704, 64) - (11, 12),
        // chart offset 635.
        final int[] fb = paint(5);
        assertEquals(0xAA0001, at(fb, 704 - 11 - 635, 64 - 12));
        // f = 0 uses PATH[0].
        assertEquals(0xAA0000, at(paint(0), 700 - 11 - 640, 60 - 12));
        // From the end of the path on there is no ship.
        final int[] late = paint(10);
        // (PATH[9] = (709, 69) would put it at (68, 57) with offset 630.)
        assertEquals(0x800000 | ((57 - 24) << 10) | (68 + 630), at(late, 68, 57));
    }

    public void testRepeatsVanish() {
        // Wind: 3 frames, R = 1 -> two plays, f 5..10, then gone.
        // Top-left = anchor (10,50) + (640 - ox, 0).
        for (int f = 5; f <= 10; f++) {
            final int ox = 640 - f;
            assertEquals("f=" + f, (1 << 16) | ((f - 5) % 3),
                         at(paint(f), 10 + 640 - ox, 50));
        }
        final int[] gone = paint(11);
        // f = 11, offset 629: the sprite would be at (21, 50); the chart shows.
        assertEquals(0x800000 | ((50 - 24) << 10) | (21 + 629), at(gone, 21, 50));
    }

    public void testRepeatsZeroHolds() {
        // Series 2: 3 frames, R = 0 -> frames 0,1,2 then frame 2 for good.
        assertEquals((3 << 16) | 0, at(paint(5), 30 + 5, 70));
        assertEquals((3 << 16) | 2, at(paint(7), 30 + 7, 70));
        assertEquals((3 << 16) | 2, at(paint(30), 30 + 30, 70));
        assertEquals((3 << 16) | 2, at(paint(250), 30 + 250, 70));
    }

    public void testLaterEntrySupersedes() {
        // Series 3 starts at 5 and again at 8: from 8 on only the second
        // entry draws (its frame f - 8).
        assertEquals((4 << 16) | 2, at(paint(7), 40 + 7, 80));
        assertEquals((4 << 16) | 0, at(paint(8), 40 + 8, 80));
        assertEquals((4 << 16) | 1, at(paint(9), 40 + 9, 80));
    }

    public void testClippedToTheChartWindow() {
        // Series 4's anchor is at row 20, above the window (rows 24..155).
        assertEquals(BORD, at(paint(6), 50 + 6, 20));
    }

    public void testCreditWindowInclusiveAndCentred() {
        // 4x3 sprite centred at (160,183): top-left (158,182).
        for (int f : new int[] { 10, 11, 12 }) {
            final int[] fb = paint(f);
            assertEquals("f=" + f, 0xCC0000, at(fb, 158, 182));
            assertEquals(0xCC0000, at(fb, 161, 184));
            assertEquals(BORD, at(fb, 157, 182));
            assertEquals(BORD, at(fb, 158, 185));
        }
        assertEquals(BORD, at(paint(9), 158, 182));
        assertEquals(BORD, at(paint(13), 158, 182));
    }

    public void testSun() {
        // Fitted: idx = floorMod(floorDiv(f - 110, 21), 7).
        assertEquals(1, ClassicIntro.sunFrame(143, 7));
        assertEquals(1, ClassicIntro.sunFrame(149, 7));
        assertEquals(2, ClassicIntro.sunFrame(154, 7));
        assertEquals(2, ClassicIntro.sunFrame(169, 7));
        assertEquals(3, ClassicIntro.sunFrame(175, 7));
        assertEquals(3, ClassicIntro.sunFrame(190, 7));
        assertEquals(4, ClassicIntro.sunFrame(196, 7));
        assertEquals(4, ClassicIntro.sunFrame(201, 7));
        assertEquals(3, ClassicIntro.sunFrame(40, 7));       // floorDiv(-70,21) = -4 -> 3
        // Painted with that index at anchor (20,40) + (640 - ox).
        assertEquals((2 << 16) | 2, at(paint(160), 20 + 160, 40));
    }
}
