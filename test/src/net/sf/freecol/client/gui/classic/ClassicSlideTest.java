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
import java.util.List;

import junit.framework.TestCase;


/**
 * Headless tests of the slide schedule ({@link ClassicSlide}, build spec
 * W2 and spec delta W2) on a fake clock: the step for both settings of
 * the accelerator, the absolute deadlines, the hold, offset 0 first, the
 * pauses between chained slides, and what a 70.0863 Hz capture of the
 * schedule shows (the clip's acceptance signatures).
 */
public class ClassicSlideTest extends TestCase {

    private static final long MS = 1_000_000L;

    /** A clock that jumps to each deadline (and records the waits). */
    private static final class FakeClock implements ClassicSlide.Clock {
        long now;
        /** Added to the clock by every paint (a slow paint). */
        long paintCost = 0L;
        final List<Long> waits = new ArrayList<>();

        FakeClock(long start) {
            this.now = start;
        }

        @Override
        public long now() {
            return this.now;
        }

        @Override
        public void waitUntil(long due) {
            this.waits.add(due);
            if (due > this.now) this.now = due;
        }
    }

    /** Runs a slide and records {offset, time} of every paint. */
    private static List<long[]> run(FakeClock clock, long step, boolean redraw)
        throws InterruptedException {
        final List<long[]> paints = new ArrayList<>();
        ClassicSlide.run(clock, step, redraw, k -> {
                paints.add(new long[] { k, clock.now });
                clock.now += clock.paintCost;
            });
        return paints;
    }

    public void testSteps() {
        assertEquals(16_430_000L, ClassicSlide.stepNanos(false));
        assertEquals(13_250_000L, ClassicSlide.stepNanos(true));
        assertEquals(15, ClassicSlide.LAST_STEP);
        assertEquals(16, ClassicSlide.CELL);
        assertEquals(100 * MS, ClassicSlide.gapNanos(true));
        assertEquals(60 * MS, ClassicSlide.gapNanos(false));
    }

    /** Offsets 1..15 at t1 + (k-1) S, nothing more, then the hold. */
    public void testScheduleOff() throws InterruptedException {
        final long t0 = 5_000 * MS;
        final FakeClock c = new FakeClock(t0);
        final long s = ClassicSlide.stepNanos(false);
        final List<long[]> p = run(c, s, false);
        assertEquals(15, p.size());
        for (int k = 1; k <= 15; k++) {
            assertEquals(k, p.get(k - 1)[0]);
            assertEquals(t0 + (k - 1) * s, p.get(k - 1)[1]);
        }
        // Offsets 1 -> 15 in 230.02 ms (clip 230.6), final due 72 ms later:
        // offset 1 -> final 302.02 ms (clip about 300).
        assertEquals(230_020_000L, p.get(14)[1] - p.get(0)[1]);
        assertEquals(t0 + 302_020_000L, c.now);
        assertEquals(t0 + 302_020_000L, ClassicSlide.holdEndNanos(t0, s));
    }

    /** The accelerator: offsets 1 -> 15 in 185.5 ms, the same hold. */
    public void testScheduleAccelerator() throws InterruptedException {
        final FakeClock c = new FakeClock(0L);
        final List<long[]> p = run(c, ClassicSlide.stepNanos(true), false);
        assertEquals(185_500_000L, p.get(14)[1] - p.get(0)[1]);
        assertEquals(185_500_000L + 72 * MS, c.now);
    }

    /** Blink OFF / a passenger / a view jump: offset 0 at once, offset 1 one step later. */
    public void testRedrawFirst() throws InterruptedException {
        final FakeClock c = new FakeClock(1_000 * MS);
        final long s = ClassicSlide.stepNanos(false);
        final List<long[]> p = run(c, s, true);
        assertEquals(16, p.size());
        assertEquals(0, p.get(0)[0]);
        assertEquals(1_000 * MS, p.get(0)[1]);
        assertEquals(1, p.get(1)[0]);
        assertEquals(1_000 * MS + s, p.get(1)[1]);
        assertEquals(1_000 * MS + s + 14 * s + 72 * MS, c.now);
    }

    /**
     * M1 acceptance F3: offset 0 stays one whole step on the screen even
     * when its paint is slow (a jump repaints the whole map, 8-27 ms under
     * the recorder): offset 1 is due one step after that paint returns,
     * and the steps after it keep the absolute schedule from there.
     */
    public void testSlowOffsetZeroKeepsItsStep() throws InterruptedException {
        for (boolean fast : new boolean[] { false, true }) {
            final long s = ClassicSlide.stepNanos(fast);
            final FakeClock c = new FakeClock(2_000 * MS);
            final List<long[]> paints = new ArrayList<>();
            final long t1 = ClassicSlide.run(c, s, true, k -> {
                    paints.add(new long[] { k, c.now });
                    c.now += (k == 0) ? 21 * MS : 0L;   // the jump's full repaint
                });
            assertEquals(16, paints.size());
            final long zeroDone = 2_000 * MS + 21 * MS;
            assertEquals(zeroDone + s, t1);
            assertEquals(zeroDone + s, paints.get(1)[1]);
            assertEquals("offset 0 shown one step", s, paints.get(1)[1] - zeroDone);
            for (int k = 1; k <= 15; k++) {
                assertEquals(k, paints.get(k)[0]);
                assertEquals(t1 + (k - 1) * s, paints.get(k)[1]);
            }
            assertEquals(ClassicSlide.holdEndNanos(t1, s), c.now);
        }

        // The painter reports when offset 0 was on the screen (its map
        // paint, before the minimap and the event): offset 1 is due one
        // step after that, not after the painter returned.
        final long s = ClassicSlide.stepNanos(false);
        final FakeClock c = new FakeClock(3_000 * MS);
        final long[] shown = { 0L };
        final List<long[]> paints = new ArrayList<>();
        final long t1 = ClassicSlide.run(c, s, true, k -> {
                paints.add(new long[] { k, c.now });
                if (k == 0) {
                    c.now += 12 * MS;          // the map
                    shown[0] = c.now;
                    c.now += 5 * MS;           // the minimap, the event
                }
            }, () -> shown[0]);
        assertEquals(3_000 * MS + 12 * MS + s, t1);
        assertEquals(t1, paints.get(1)[1]);
        // Without a time from the painter: when it returned.
        final FakeClock d = new FakeClock(0L);
        final long u1 = ClassicSlide.run(d, s, true, k -> d.now += (k == 0) ? 17 * MS : 0L,
                                         () -> 0L);
        assertEquals(17 * MS + s, u1);
    }

    /** A slow paint delays its own frame, never the deadlines after it. */
    public void testAbsoluteDeadlines() throws InterruptedException {
        final FakeClock c = new FakeClock(0L);
        c.paintCost = 9 * MS;   // a recorded step costs about 5 ms
        final long s = ClassicSlide.stepNanos(true);
        final List<long[]> p = run(c, s, false);
        for (int k = 2; k <= 15; k++) {
            assertEquals(k, p.get(k - 1)[0]);
            assertEquals((k - 1) * s, p.get(k - 1)[1]);
        }
        assertEquals(14 * s + 72 * MS, c.now);
        // Slower than a step: the frames come late but none is skipped and
        // the slide does not stretch beyond its last paint.
        final FakeClock d = new FakeClock(0L);
        d.paintCost = 20 * MS;
        final List<long[]> q = run(d, s, false);
        assertEquals(15, q.size());
        assertEquals(14 * 20 * MS, q.get(14)[1]);
        assertEquals(14 * 20 * MS + 20 * MS, d.now);
    }

    /** Whole screen pixels per step, straight and diagonal, and the isometric two-row step. */
    public void testScreenOffset() {
        assertEquals(0, ClassicSlide.screenOffset(0, 5, -1));
        assertEquals(-5, ClassicSlide.screenOffset(1, 5, -1));
        assertEquals(-75, ClassicSlide.screenOffset(15, 5, -1));
        assertEquals(-80, ClassicSlide.screenOffset(16, 5, -1));
        assertEquals(45, ClassicSlide.screenOffset(15, 3, 1));
        assertEquals(0, ClassicSlide.screenOffset(7, 3, 0));
        // A diagonal moves s screen pixels on each axis per step.
        for (int k = 0; k <= 16; k++) {
            assertEquals(-4 * k, ClassicSlide.screenOffset(k, 4, -1));
            assertEquals(4 * k, ClassicSlide.screenOffset(k, 4, 1));
        }
        // The isometric model's north/south step is two raw rows.
        assertEquals(-96, ClassicSlide.screenOffset(16, 3, -2));
    }

    /**
     * What a 70.0863 Hz capture shows of offsets 1..15 (spec delta W2
     * acceptance), over 200 phases: OFF 16 frame steps from offset 1 to
     * offset 15 (sometimes 17), with repeated frames and no 2-px advance;
     * ON 13 frame steps, exactly one 2-px advance and no repeat.
     */
    public void testCaptureSignatures() throws InterruptedException {
        final double frame = 1e9 / 70.086303;
        int off16 = 0, off17 = 0, on13 = 0;
        for (int i = 0; i < 200; i++) {
            final double phase = frame * i / 200.0;
            final int[] off = capture(false, phase, frame);
            assertTrue(off[0] == 16 || off[0] == 17);
            if (off[0] == 16) off16++; else off17++;
            assertEquals(0, off[2]);
            assertEquals(off[0] + 1 - 15, off[1]);
            final int[] on = capture(true, phase, frame);
            assertEquals(0, on[1]);
            if (on[0] == 13) {
                on13++;
                assertEquals(1, on[2]);
            } else {
                // 14 x 13.25 = 185.50 ms is 0.02 ms longer than 13
                // frames: a capture right on offset 1 sees 14.
                assertEquals(14, on[0]);
            }
        }
        // The clip: 16 frames in 36 of 43 clean slides, 17 in 7.
        assertTrue(off16 > 4 * off17);
        assertTrue(on13 >= 198);
    }

    /**
     * Sample a slide's offsets 1..15 at {@code frame} intervals.
     *
     * @return {frames from offset 1 to 15, repeated frames, 2-px advances}.
     */
    private static int[] capture(boolean fast, double phase, double frame)
        throws InterruptedException {
        final FakeClock c = new FakeClock(0L);
        final List<long[]> p = run(c, ClassicSlide.stepNanos(fast), false);
        int first = -1, last = -1, prev = 0, repeats = 0, jumps = 0;
        for (int f = 0; f < 40; f++) {
            final double t = phase + f * frame;
            int shown = 0;
            for (long[] e : p) if (e[1] <= t) shown = (int) e[0];
            if (shown < 1) continue;
            if (first < 0) first = f;
            // The unit stood at offset 0 before: a first capture at
            // offset 2 is the 2-px advance (the clip's "1->3").
            if (f > first && shown == prev) repeats++;
            if (shown - prev >= 2) jumps++;
            prev = shown;
            if (shown == 15) {
                last = f;
                break;
            }
        }
        return new int[] { last - first, repeats, jumps };
    }

    /**
     * The water cycle's servicing clock (M1c design 10 §7.2, W6c): a
     * palette step due in the middle of a slide fires at its own deadline,
     * between two slide steps, and every slide deadline stays exactly as
     * without it (the slide's schedule is unchanged).
     */
    public void testServicingClockKeepsEverySlideDeadline() throws InterruptedException {
        final long t0 = 9_000 * MS;
        final FakeClock c = new FakeClock(t0);
        final long s = ClassicSlide.stepNanos(false);
        final List<long[]> events = new ArrayList<>();
        final ClassicWaterCycle cycle = new ClassicWaterCycle(c, r -> fail("no post"),
            () -> true, null, (p, k, due, sv) -> events.add(new long[] { -1, c.now, p }),
            null, false);
        // Start the cycle so that step 1 is due 100 ms into the slide.
        final long p = Math.round(ClassicGamePalette.PERIOD_MS * MS);
        c.now = t0 - p + 100 * MS;
        cycle.start();
        c.now = t0;
        final ClassicSlide.Clock sc = cycle.servicing(c);
        ClassicSlide.run(sc, s, false, k -> events.add(new long[] { k, c.now, 0 }));
        assertEquals(16, events.size());
        int at = -1;
        for (int i = 0, k = 1; i < events.size(); i++) {
            final long[] e = events.get(i);
            if (e[0] < 0) {
                at = i;
                assertEquals("at its deadline", t0 + 100 * MS, e[1]);
                assertEquals(1, e[2]);
                continue;
            }
            assertEquals(k, e[0]);
            assertEquals("slide deadline " + k, t0 + (k - 1) * s, e[1]);
            k++;
        }
        // Between offsets 7 (98.58 ms) and 8 (115.01 ms).
        assertEquals(7, at);
        assertEquals(t0 + 302_020_000L, c.now);
        assertEquals(1, cycle.phase());
    }
}
