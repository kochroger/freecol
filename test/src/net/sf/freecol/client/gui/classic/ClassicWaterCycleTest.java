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
import java.util.concurrent.ConcurrentLinkedQueue;

import junit.framework.TestCase;


/**
 * Tests of the water cycling clock ({@link ClassicWaterCycle}, M1c design
 * 10 §7.3, item W6c): steps at {@code t0 + k * 575.05 ms} with no drift,
 * stale posts dropped, the woodcut's hold and its release (one step at the
 * next frame, then re-based), the pref OFF/ON, and the servicing clock of
 * the blocking waits, which fires every step due before its deadline, each
 * at its own deadline.  A fake clock and no thread, but for one test on the
 * real clock.
 */
public class ClassicWaterCycleTest extends TestCase {

    private static final long MS = 1_000_000L;

    /** P in nanoseconds: 35 ticks of 16.43 ms. */
    private static final long P = 575_050_000L;

    /** A clock that only moves when the test (or a wait) moves it; it logs the waits. */
    static final class FakeClock implements ClassicSlide.Clock {
        long now;
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

    /** One step as the listener saw it: when, the phase, k, due, serviced. */
    static final class Seen {
        final long at, k, due;
        final int phase;
        final boolean serviced;

        Seen(long at, int phase, long k, long due, boolean serviced) {
            this.at = at;
            this.phase = phase;
            this.k = k;
            this.due = due;
            this.serviced = serviced;
        }
    }

    /** A cycle without a thread whose posts the test runs by hand. */
    static final class Rig {
        final FakeClock clock;
        final List<Runnable> posted = new ArrayList<>();
        final List<Seen> seen = new ArrayList<>();
        final boolean[] pref = { true };
        final boolean[] onEdt = { true };
        final ClassicWaterCycle cycle;

        Rig(long start) {
            this.clock = new FakeClock(start);
            this.cycle = new ClassicWaterCycle(this.clock, this.posted::add,
                () -> this.onEdt[0], () -> this.pref[0],
                (p, k, due, s) -> this.seen.add(new Seen(this.clock.now, p, k, due, s)),
                null, false);
        }

        /** Wait for the next due step as the thread does, post and run it. */
        long step() {
            final long[] n = this.cycle.next();
            assertNotNull("a step is due", n);
            this.clock.waitUntil(n[2]);
            assertTrue(this.cycle.pollPref());
            assertTrue(this.cycle.due((int) n[0], n[1]));
            runPosted();
            return n[2];
        }

        void runPosted() {
            final List<Runnable> rs = new ArrayList<>(this.posted);
            this.posted.clear();
            for (Runnable r : rs) r.run();
        }

        Seen last() {
            return this.seen.get(this.seen.size() - 1);
        }
    }


    public void testSchedule() {
        assertEquals(575.05, ClassicGamePalette.PERIOD_MS, 1e-9);
        assertEquals(1000.0 / ClassicFrameRecorder.HZ, ClassicWaterCycle.RELEASE_STEP_MS, 1e-12);
        assertTrue(ClassicWaterCycle.RELEASE_STEP_MS <= 14.3);
        final double pm = ClassicGamePalette.PERIOD_MS;
        assertEquals(P, ClassicWaterCycle.dueNanos(0L, 1, pm));
        // No drift: step 1000 is exact.
        assertEquals(1000 * P, ClassicWaterCycle.dueNanos(0L, 1000, pm));
        assertEquals(7 * MS + 100_000 * P, ClassicWaterCycle.dueNanos(7 * MS, 100_000, pm));
        // The latest step due by now: exact at every deadline.
        assertEquals(0L, ClassicWaterCycle.dueBy(5L, 5L, pm));
        assertEquals(0L, ClassicWaterCycle.dueBy(0L, P - 1, pm));
        for (long k = 1; k <= 2000; k += 37) {
            final long d = ClassicWaterCycle.dueNanos(123L, k, pm);
            assertEquals(k, ClassicWaterCycle.dueBy(123L, d, pm));
            assertEquals(k - 1, ClassicWaterCycle.dueBy(123L, d - 1, pm));
        }
        // The next step: never one handed on, the latest due after a stall.
        assertEquals(1L, ClassicWaterCycle.nextIndex(0L, 0L, 0L, pm));
        assertEquals(2L, ClassicWaterCycle.nextIndex(0L, P, 1L, pm));
        assertEquals(5L, ClassicWaterCycle.nextIndex(0L, 5 * P + 3, 1L, pm));
        assertEquals(9L, ClassicWaterCycle.nextIndex(0L, 2 * P, 8L, pm));
    }

    /**
     * Phase 0 at the start, then one step every P on the absolute grid:
     * phases 1..7, 0, 1 ... (one colour up per step), 1,000 steps with no
     * drift, every step at its own deadline.
     */
    public void testStepsFromTheStart() {
        final long t0 = 4_000 * MS;
        final Rig r = new Rig(t0);
        assertNull("not started", r.cycle.next());
        assertFalse(r.cycle.isRunning());
        r.cycle.start();
        assertTrue(r.cycle.isRunning());
        assertEquals(0, r.cycle.phase());
        assertEquals(t0, r.cycle.scheduleStart());
        assertEquals(t0 + P, r.cycle.dueNanos());
        for (int k = 1; k <= 1000; k++) {
            assertEquals(t0 + k * P, r.step());
            final Seen s = r.last();
            assertEquals(k, s.k);
            assertEquals(k % 8, s.phase);
            assertEquals(t0 + k * P, s.due);
            assertEquals(s.due, s.at);
            assertFalse(s.serviced);
        }
        assertEquals(1000, r.seen.size());
        assertEquals(1000 % 8, r.cycle.phase());
        // Least squares over the 1000 steps: exactly P.
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (Seen s : r.seen) {
            sx += s.k;
            sy += (s.at - t0) / 1e6;
            sxx += (double) s.k * s.k;
            sxy += s.k * ((s.at - t0) / 1e6);
        }
        final int n = r.seen.size();
        assertEquals(575.05, (n * sxy - sx * sy) / (n * sxx - sx * sx), 1e-6);
        // A second start is a no-op.
        r.cycle.start();
        assertEquals(t0, r.cycle.scheduleStart());
    }

    /**
     * Stale posts: a step a blocking wait applied first, a step of an older
     * schedule, a step handed on twice.
     */
    public void testStalePostsAreDropped() {
        final Rig r = new Rig(0L);
        r.cycle.start();
        long[] n = r.cycle.next();
        r.clock.waitUntil(n[2]);
        assertTrue(r.cycle.due((int) n[0], n[1]));
        assertFalse("handed on once", r.cycle.due((int) n[0], n[1]));
        // The event thread is blocked: the servicing clock applies it.
        assertTrue(r.cycle.serviceDue());
        assertEquals(1, r.seen.size());
        assertTrue(r.last().serviced);
        r.runPosted();
        assertEquals("the post came too late", 1, r.seen.size());
        // A post of an older schedule (a hold came between).
        n = r.cycle.next();
        r.clock.waitUntil(n[2]);
        assertTrue(r.cycle.due((int) n[0], n[1]));
        r.cycle.hold("woodcut");
        r.cycle.release("woodcut");
        r.runPosted();
        assertEquals("older generation", 1, r.seen.size());
        // A stalled thread: the latest due step is applied, the phase absolute.
        final Rig s = new Rig(0L);
        s.cycle.start();
        s.clock.now = 10 * P + 5;
        n = s.cycle.next();
        assertEquals(10L, n[1]);
        assertEquals(10 * P, n[2]);
        assertTrue(s.cycle.due((int) n[0], n[1]));
        s.runPosted();
        assertEquals(10 % 8, s.last().phase);
        assertEquals(11L, s.cycle.next()[1]);
    }

    /**
     * A woodcut (W9) holds the phase: no step while it is up, however long;
     * its release gives one step at the next frame, and the schedule runs
     * on from that step (landfall #2078 -&gt; #2638/#2639, fog-start #1572
     * -&gt; #1573).  Holds nest.
     */
    public void testHoldAndRelease() {
        final Rig r = new Rig(0L);
        r.cycle.start();
        r.step();
        r.step();
        r.step();
        assertEquals(3, r.cycle.phase());
        final int before = r.seen.size();
        r.clock.now = 3 * P + 100 * MS;
        r.cycle.hold("woodcut");
        assertTrue(r.cycle.isHeld());
        assertFalse(r.cycle.isRunning());
        assertNull(r.cycle.next());
        assertEquals(Long.MAX_VALUE, r.cycle.dueNanos());
        // Eight seconds under the woodcut: nothing.
        r.clock.now += 8_000 * MS;
        assertFalse(r.cycle.serviceDue());
        assertEquals(before, r.seen.size());
        assertEquals(3, r.cycle.phase());
        // Nested: a second reason keeps it held.
        r.cycle.hold("other");
        r.cycle.release("woodcut");
        assertTrue(r.cycle.isHeld());
        assertNull(r.cycle.next());
        r.cycle.release("nothing-held");
        assertTrue(r.cycle.isHeld());
        // The last release: one step one frame later, at phase 4.
        final long rel = r.clock.now;
        r.cycle.release("other");
        assertFalse(r.cycle.isHeld());
        final long first = r.step();
        assertEquals(rel + Math.round(ClassicWaterCycle.RELEASE_STEP_MS * MS), first);
        assertTrue(first - rel <= 14_300_000L);
        assertEquals(4, r.last().phase);
        assertEquals(1L, r.last().k);
        // Re-based to that step: every P from it.
        for (int k = 1; k <= 20; k++) {
            assertEquals(first + k * P, r.step());
            assertEquals((4 + k) % 8, r.last().phase);
        }
    }

    /**
     * The pref {@code waterCycling} (read at each due step): OFF freezes
     * the phase, ON resumes from it one period later.
     */
    public void testPrefOffFreezesAndOnResumes() {
        final Rig r = new Rig(0L);
        r.cycle.start();
        r.step();
        r.step();
        assertEquals(2, r.cycle.phase());
        r.pref[0] = false;
        final long[] n = r.cycle.next();
        r.clock.waitUntil(n[2]);
        assertFalse("the step finds the pref off", r.cycle.pollPref());
        assertFalse(r.cycle.due((int) n[0], n[1]));
        r.runPosted();
        assertFalse(r.cycle.isEnabled());
        assertFalse(r.cycle.isRunning());
        assertNull(r.cycle.next());
        r.clock.now += 30 * P;
        assertFalse(r.cycle.serviceDue());
        assertEquals(2, r.cycle.phase());
        assertEquals(2, r.seen.size());
        // ON: from phase 2, step 1 one period after the read.
        r.pref[0] = true;
        final long on = r.clock.now;
        assertTrue(r.cycle.pollPref());
        assertTrue(r.cycle.isRunning());
        assertEquals(on + P, r.step());
        assertEquals(3, r.last().phase);
        // Off from the start: started frozen.
        final Rig s = new Rig(0L);
        s.pref[0] = false;
        s.cycle.start();
        assertTrue(s.cycle.isStarted());
        assertFalse(s.cycle.isRunning());
        assertEquals(0, s.cycle.phase());
        // setEnabled is the same switch (W14 will call it).
        s.cycle.setEnabled(true);
        assertTrue(s.cycle.isRunning());
        s.pref[0] = true;
        assertEquals(P, s.step());
        // Held and off: the release does not step; ON then resumes.
        final Rig h = new Rig(0L);
        h.cycle.start();
        h.cycle.hold("woodcut");
        h.cycle.setEnabled(false);
        h.cycle.release("woodcut");
        assertFalse(h.cycle.isRunning());
        assertNull(h.cycle.next());
        h.cycle.setEnabled(true);
        assertEquals(P, h.cycle.dueNanos());
    }

    /**
     * The servicing clock of the blocking waits: {@code waitUntil(d)} fires
     * every step due before d, in order, each at its own deadline, then
     * waits for d; not on another thread, and not past a hold.
     */
    public void testServicingClockFiresTheStepsOnTime() {
        final Rig r = new Rig(1_000 * MS);
        r.cycle.start();
        final ClassicSlide.Clock c = r.cycle.servicing(r.clock);
        assertEquals(r.clock.now, c.now());
        // A wait over three deadlines.
        try {
            c.waitUntil(1_000 * MS + 3 * P + 10 * MS);
        } catch (InterruptedException e) {
            fail();
        }
        assertEquals(3, r.seen.size());
        for (int i = 0; i < 3; i++) {
            final Seen s = r.seen.get(i);
            assertEquals(i + 1L, s.k);
            assertEquals(1_000 * MS + (i + 1) * P, s.at);
            assertEquals(s.due, s.at);
            assertTrue(s.serviced);
        }
        assertEquals(1_000 * MS + 3 * P + 10 * MS, r.clock.now);
        assertEquals(java.util.Arrays.asList(1_000 * MS + P, 1_000 * MS + 2 * P,
            1_000 * MS + 3 * P, 1_000 * MS + 3 * P + 10 * MS), r.clock.waits);
        // A wait that ends right at a deadline does not fire it.
        r.clock.waits.clear();
        try {
            c.waitUntil(1_000 * MS + 4 * P);
        } catch (InterruptedException e) {
            fail();
        }
        assertEquals(3, r.seen.size());
        // The thread's post for step 4 runs, then the next wait fires 5 only.
        final long[] n = r.cycle.next();
        assertEquals(4L, n[1]);
        assertTrue(r.cycle.due((int) n[0], n[1]));
        r.runPosted();
        assertEquals(4, r.seen.size());
        try {
            c.waitUntil(1_000 * MS + 5 * P + 1);
        } catch (InterruptedException e) {
            fail();
        }
        assertEquals(5, r.seen.size());
        assertEquals(5L, r.last().k);
        assertEquals(5 % 8, r.last().phase);
        // Not on the listener's thread: a plain wait.
        r.onEdt[0] = false;
        try {
            c.waitUntil(1_000 * MS + 7 * P + 1);
        } catch (InterruptedException e) {
            fail();
        }
        assertEquals(5, r.seen.size());
        r.onEdt[0] = true;
        // Held: a plain wait as well.
        r.cycle.hold("woodcut");
        try {
            c.waitUntil(1_000 * MS + 9 * P + 1);
        } catch (InterruptedException e) {
            fail();
        }
        assertEquals(5, r.seen.size());
    }

    /** Close ends it: no step, no post, no restart. */
    public void testClose() {
        final Rig r = new Rig(0L);
        r.cycle.start();
        final long[] n = r.cycle.next();
        r.cycle.close();
        assertTrue(r.cycle.isClosed());
        assertFalse(r.cycle.isRunning());
        assertNull(r.cycle.next());
        r.clock.waitUntil(n[2]);
        assertFalse(r.cycle.due((int) n[0], n[1]));
        assertFalse(r.cycle.serviceDue());
        r.cycle.start();
        r.cycle.release("x");
        assertFalse(r.cycle.isRunning());
        r.cycle.close();   // idempotent
    }

    /**
     * The thread on the real clock: three steps, each posted within a few
     * ms of its deadline (the poster here runs at once on the thread); a
     * hold stops them, close ends the thread.
     */
    public void testThreadOnTheSystemClock() throws Exception {
        final ConcurrentLinkedQueue<long[]> got = new ConcurrentLinkedQueue<>();
        final ClassicWaterCycle c = new ClassicWaterCycle(ClassicSlide.SYSTEM,
            Runnable::run, () -> true, null,
            (p, k, due, s) -> got.add(new long[] { System.nanoTime(), p, k, due }),
            new ClassicGamePalette.Cycle(8, 120, 3), true);   // 49.29 ms
        try {
            c.start();
            final long end = System.nanoTime() + 5_000 * MS;
            while (got.size() < 3 && System.nanoTime() < end) Thread.sleep(5);
            assertTrue("steps: " + got.size(), got.size() >= 3);
            int i = 0;
            for (long[] g : got) {
                if (i++ >= 3) break;
                assertEquals(i, g[2]);
                assertEquals(i % 8, g[1]);
                final long late = g[0] - g[3];
                assertTrue("late " + late / 1e6 + " ms", late >= 0 && late < 40 * MS);
            }
            c.hold("woodcut");
            Thread.sleep(20);
            final int held = got.size();
            Thread.sleep(150);
            assertEquals("no step while held", held, got.size());
            c.release("woodcut");
            final long rel = System.nanoTime();
            final long end2 = rel + 5_000 * MS;
            while (got.size() == held && System.nanoTime() < end2) Thread.sleep(2);
            assertTrue(got.size() > held);
        } finally {
            c.close();
        }
        final int closed = got.size();
        Thread.sleep(150);
        assertEquals(closed, got.size());
    }
}
