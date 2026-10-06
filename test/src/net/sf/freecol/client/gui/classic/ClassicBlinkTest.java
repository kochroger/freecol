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
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;


/**
 * Tests of the blink clock ({@link ClassicBlink}, build spec W3): the
 * 328.5-ms half-period on absolute deadlines, ON first, the reset (not
 * pause) on every arm, the re-base on the panel refresh, the generations
 * that drop stale toggles, the thread on the real clock, and the
 * minimap's blinking dot ({@link ClassicHud.MinimapModel#with}).
 */
public class ClassicBlinkTest extends TestCase {

    private static final long MS = 1_000_000L;

    /** H in nanoseconds. */
    private static final long H = 328_500_000L;

    /** A clock that only moves when the test moves it. */
    private static final class FakeClock implements ClassicSlide.Clock {
        long now;

        FakeClock(long start) {
            this.now = start;
        }

        @Override
        public long now() {
            return this.now;
        }

        @Override
        public void waitUntil(long due) {
            if (due > this.now) this.now = due;
        }
    }

    /** A blink without a thread whose posts the test runs by hand. */
    private static final class Rig {
        final FakeClock clock;
        final List<Runnable> posted = new ArrayList<>();
        final List<Integer> toggles = new ArrayList<>();
        final ClassicBlink blink;

        Rig(long start) {
            this.clock = new FakeClock(start);
            this.blink = new ClassicBlink(this.clock, this.posted::add,
                                          this.toggles::add, false);
        }

        /** Wait for the next due toggle as the thread does, post and run it. */
        long step() {
            final long[] n = this.blink.next();
            assertNotNull(n);
            this.clock.waitUntil(n[2]);
            assertTrue(this.blink.due((int) n[0], (int) n[1]));
            runPosted();
            return n[2];
        }

        void runPosted() {
            final List<Runnable> rs = new ArrayList<>(this.posted);
            this.posted.clear();
            for (Runnable r : rs) r.run();
        }
    }


    public void testSchedule() {
        assertEquals(328.5, ClassicBlink.HALF_PERIOD_MS, 0.0);
        assertEquals(H, ClassicBlink.dueNanos(0L, 1));
        // No drift: toggle 34 (the landfall episode #364-#1124) is exact.
        assertEquals(34 * H, ClassicBlink.dueNanos(0L, 34));
        assertEquals(5 * MS + 1000 * H, ClassicBlink.dueNanos(5 * MS, 1000));
        // ON first: toggle 1 is OFF, then ON, OFF ...
        assertFalse(ClassicBlink.isOff(0));
        assertTrue(ClassicBlink.isOff(1));
        assertFalse(ClassicBlink.isOff(2));
        assertTrue(ClassicBlink.isOff(33));
        // The next toggle: due after now, never one already handed on.
        assertEquals(1, ClassicBlink.nextIndex(0L, 0L, 0));
        assertEquals(1, ClassicBlink.nextIndex(0L, H - 1, 0));
        assertEquals(2, ClassicBlink.nextIndex(0L, H, 1));
        assertEquals(4, ClassicBlink.nextIndex(0L, 1000 * MS, 0));
        assertEquals(6, ClassicBlink.nextIndex(0L, 100L, 5));
        assertEquals(1, ClassicBlink.nextIndex(10 * MS, 0L, 0));
    }

    /** ON at the arm, OFF one half-period later, then alternating on the absolute grid. */
    public void testTogglesFromTheArm() {
        final Rig r = new Rig(7_000 * MS);
        assertFalse(r.blink.isArmed());
        assertNull(r.blink.next());
        r.blink.arm();
        assertTrue(r.blink.isArmed());
        assertEquals(7_000 * MS, r.blink.phaseStart());
        for (int k = 1; k <= 31; k++) {
            assertEquals(7_000 * MS + k * H, r.step());
        }
        assertEquals(31, r.toggles.size());
        for (int k = 1; k <= 31; k++) assertEquals(k, (int) r.toggles.get(k - 1));
        // Least squares over the 31 toggles: exactly H.
        assertEquals(328.5, slopeMs(r, 7_000 * MS, 31), 1e-9);
    }

    /** A late wake-up never repeats a toggle and keeps the grid. */
    public void testLateWakeUp() {
        final Rig r = new Rig(0L);
        r.blink.arm();
        r.step();                                   // 1 at H
        r.clock.now = 3 * H + 5 * MS;               // overslept past 2 and 3
        final long[] n = r.blink.next();
        assertEquals(4, n[1]);
        assertEquals(4 * H, n[2]);
        assertFalse(r.blink.due((int) n[0], 1));    // already handed on
    }

    /**
     * Reset, not pause (landfall: last ON #7703, dialog #7720-#8163, OFF
     * #8187): a new arm starts a new phase at once, ON first.
     */
    public void testRearmResets() {
        final Rig r = new Rig(0L);
        r.blink.arm();
        r.step();                                   // OFF at H
        r.clock.now = 500 * MS;
        final long[] stale = r.blink.next();        // 2, due at 2H
        r.blink.arm();
        assertFalse(r.blink.due((int) stale[0], (int) stale[1]));
        final long[] n = r.blink.next();
        assertEquals(1, n[1]);
        assertEquals(500 * MS + H, n[2]);
    }

    /** A toggle posted in one phase and run after a re-arm or stop is dropped. */
    public void testStaleTogglesAreDropped() {
        final Rig r = new Rig(0L);
        r.blink.arm();
        final long[] n = r.blink.next();
        r.clock.waitUntil(n[2]);
        assertTrue(r.blink.due((int) n[0], (int) n[1]));
        assertEquals(1, r.posted.size());
        r.blink.arm();                              // e.g. a slide ended meanwhile
        r.runPosted();
        assertTrue(r.toggles.isEmpty());
        final long[] m = r.blink.next();
        r.clock.waitUntil(m[2]);
        r.blink.due((int) m[0], (int) m[1]);
        r.blink.stop();                             // e.g. END_TURN
        r.runPosted();
        assertTrue(r.toggles.isEmpty());
        assertFalse(r.blink.isArmed());
        assertNull(r.blink.next());
        r.blink.stop();                             // twice is harmless
    }

    /**
     * The first OFF comes one half-period after the panel refresh (57
     * episodes): every panel paint within 100 ms of the arm re-bases
     * the phase, a later one does not.
     */
    public void testPanelRefreshRebases() {
        final Rig r = new Rig(1_000 * MS);
        r.blink.arm();
        r.clock.now = 1_014 * MS;                   // the refresh, one frame later
        assertTrue(r.blink.panelPainted());
        r.clock.now = 1_029 * MS;                   // a second paint of it
        assertTrue(r.blink.panelPainted());
        assertEquals(1_029 * MS, r.blink.phaseStart());
        r.clock.now = 1_101 * MS;                   // past the window
        assertFalse(r.blink.panelPainted());
        assertEquals(1_029 * MS, r.blink.phaseStart());
        assertEquals(1_029 * MS + H, r.step());
        // The blink's own dot repaints never move the phase.
        assertFalse(r.blink.panelPainted());
        assertEquals(1_029 * MS + 2 * H, r.step());
        // Without an arm a paint changes nothing.
        r.blink.stop();
        assertFalse(r.blink.panelPainted());
    }

    /** close() ends the phase for good. */
    public void testClose() {
        final Rig r = new Rig(0L);
        r.blink.arm();
        r.blink.close();
        assertFalse(r.blink.isArmed());
        r.blink.arm();
        assertFalse(r.blink.isArmed());
    }

    /**
     * The thread on the real clock: toggles 1, 2, 3, ... posted at
     * t0 + n H (the recorder's sleep-plus-spin wait), none after close.
     */
    public void testThreadOnTheRealClock() throws InterruptedException {
        final List<long[]> posts = new ArrayList<>();
        final ClassicBlink b = new ClassicBlink(ClassicSlide.SYSTEM, run -> {
                synchronized (posts) {
                    posts.add(new long[] { System.nanoTime(), 0 });
                }
                run.run();
            }, n -> {
                synchronized (posts) {
                    posts.get(posts.size() - 1)[1] = n;
                }
            }, true);
        b.arm();
        final long t0 = b.phaseStart();
        Thread.sleep(1_450);
        b.close();
        final List<long[]> got;
        synchronized (posts) {
            got = new ArrayList<>(posts);
        }
        assertTrue("toggles: " + got.size(), got.size() >= 4);
        for (int i = 0; i < got.size(); i++) {
            assertEquals(i + 1, got.get(i)[1]);
            final long late = got.get(i)[0] - ClassicBlink.dueNanos(t0, i + 1);
            assertTrue("toggle " + (i + 1) + " late " + late / 1e6 + " ms",
                       late >= 0 && late < 25 * MS);
        }
        Thread.sleep(400);
        synchronized (posts) {
            assertEquals(got.size(), posts.size());
        }
    }

    /** The minimap's dot: one tile replaced, the ring still drawn over it. */
    public void testMinimapDot() {
        final int w = 58, h = 72;
        final int[] rgb = new int[w * h];
        Arrays.fill(rgb, ClassicHud.UNEXPLORED);
        rgb[26 * w + 53] = 0xFF7100;
        rgb[26 * w + 56] = 0xFF7100;
        final ClassicHud.MinimapModel m = new ClassicHud.MinimapModel(w, h, rgb, 42, 20);
        final ClassicHud.MinimapModel off = m.with(53, 26, ClassicHud.BLINK_DOT_RGB);
        assertNotSame(m, off);
        assertEquals(0xFF7100, m.at(53, 26));
        assertEquals(ClassicHud.BLINK_DOT_RGB, off.at(53, 26));
        assertEquals(0xFF7100, off.at(56, 26));
        assertEquals(42, off.c0);
        assertEquals(20, off.r0);
        assertSame(m, m.with(-1, 3, 0));
        assertSame(m, m.with(58, 3, 0));
        assertEquals(0xFFFFFF, ClassicHud.BLINK_DOT_RGB);

        final BufferedImage on = paint(m), dot = paint(off);
        assertEquals(0xFF7100, on.getRGB(304, 28) & 0xFFFFFF);
        assertEquals(0xFFFFFF, dot.getRGB(304, 28) & 0xFFFFFF);
        // Only the dot differs.
        int diff = 0;
        for (int y = 0; y < 200; y++) {
            for (int x = 0; x < 320; x++) {
                if (on.getRGB(x, y) != dot.getRGB(x, y)) diff++;
            }
        }
        assertEquals(1, diff);
        // In the ring's column (view column 14) the ring hides the dot.
        final BufferedImage edge = paint(m.with(56, 26, ClassicHud.BLINK_DOT_RGB));
        assertEquals(0xFFFFFF, edge.getRGB(307, 28) & 0xFFFFFF);
        assertEquals(0xFFFFFF, on.getRGB(307, 28) & 0xFFFFFF);
    }

    private static BufferedImage paint(ClassicHud.MinimapModel m) {
        final BufferedImage img = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = img.createGraphics();
        ClassicHud.paintChrome(g, null);
        ClassicHud.paintMinimap(g, m);
        g.dispose();
        return img;
    }

    /** Least-squares slope (ms per toggle) of the rig's first n deadlines. */
    private static double slopeMs(Rig r, long t0, int n) {
        double mx = (n + 1) / 2.0, my = 0;
        for (int k = 1; k <= n; k++) my += ClassicBlink.dueNanos(t0, k) / 1e6;
        my /= n;
        double sxx = 0, sxy = 0;
        for (int k = 1; k <= n; k++) {
            sxx += (k - mx) * (k - mx);
            sxy += (k - mx) * (ClassicBlink.dueNanos(t0, k) / 1e6 - my);
        }
        return sxy / sxx;
    }
}
