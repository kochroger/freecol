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
 * Tests of the departure's clock ({@link ClassicDepartureTimeline}) and of
 * the dissolve order ({@link ClassicDeparture#dissolveOrder}), on
 * synthetic frames and on the measured timing of the timed Dutch run.
 */
public class ClassicDepartureTimelineTest extends TestCase {

    /**
     * The changed-pixel counts of the real Dutch show (black -> 1, four
     * full-screen dawn steps, five ship steps), as the production frames
     * give them.
     */
    private static final int[] DUTCH = {
        64000, 61933, 61911, 62586, 62952, 8886, 5213, 3994, 3843, 4041
    };

    private static final int[] SMALL = { 900, 0, 9, 1, 2, 3, 4, 5, 6, 64000 };

    /**
     * The timed Dutch captures ({@code departure-dutch-timed}, ms after
     * capture 167 = the key): {ms, transition, percent of its changed
     * pixels revealed (100 = settled)}.  Every mid-dissolve capture and
     * the settled ones on either side of each dissolve.
     */
    private static final int[][] TIMED = {
        { 0, 0, 0 },                                             // 167 black
        { 531, 0, 1 }, { 1230, 0, 71 }, { 1594, 0, 100 },        // 168-170
        { 11195, 0, 100 }, { 11872, 1, 24 }, { 12423, 1, 75 },   // 188-190
        { 12786, 1, 100 },                                       // 191
        { 22380, 1, 100 }, { 23083, 2, 61 }, { 23434, 2, 100 },  // 209-211
        { 25019, 2, 100 }, { 48630, 4, 100 },                    // 214, 215
        { 51303, 4, 100 }, { 51833, 5, 32 }, { 52371, 5, 100 },  // 220-222
        { 60862, 5, 100 }, { 61403, 6, 55 }, { 61920, 6, 100 },  // 238-240
        { 69930, 6, 100 }, { 70478, 7, 22 }, { 70990, 7, 88 },   // 255-257
        { 71527, 7, 100 },                                       // 258
        { 79525, 7, 100 }, { 80056, 8, 40 }, { 80596, 8, 100 },  // 273-275
    };

    public void testRevealedIsConstantTime() {
        final long d = ClassicDepartureTimeline.DISSOLVE_MS;
        // The same duration whatever the size: half way = half revealed.
        assertEquals(32000, ClassicDepartureTimeline.revealed(64000, d / 2));
        assertEquals(1997, ClassicDepartureTimeline.revealed(3994, d / 2));
        for (int n : new int[] { 1, 3994, 64000 }) {
            assertEquals(0, ClassicDepartureTimeline.revealed(n, 0));
            assertEquals(0, ClassicDepartureTimeline.revealed(n, -5));
            assertTrue(ClassicDepartureTimeline.revealed(n, d - 1) < n);
            assertEquals(n, ClassicDepartureTimeline.revealed(n, d));
            assertEquals(n, ClassicDepartureTimeline.revealed(n, d + 5000));
        }
        assertEquals(0, ClassicDepartureTimeline.revealed(0, d / 2));
    }

    public void testOnsetTable() {
        assertEquals(560L, ClassicDepartureTimeline.onsetMs(0));
        for (int k = 1; k < ClassicDepartureTimeline.TRANSITIONS; k++) {
            // Each picture is fully in before the next one starts.
            assertTrue("onset " + k, ClassicDepartureTimeline.onsetMs(k)
                >= ClassicDepartureTimeline.onsetMs(k - 1)
                + ClassicDepartureTimeline.DISSOLVE_MS);
        }
        assertTrue(ClassicDepartureTimeline.END_MS
            >= ClassicDepartureTimeline.onsetMs(9) + ClassicDepartureTimeline.DISSOLVE_MS);
        // The first scene was up by +105.5 s (capture 276), after the
        // engine's own 1-2 s start.
        assertTrue(ClassicDepartureTimeline.END_MS <= 103500L);
        final ClassicDepartureTimeline t = new ClassicDepartureTimeline(DUTCH);
        for (int k = 0; k < ClassicDepartureTimeline.TRANSITIONS; k++) {
            assertEquals(ClassicDepartureTimeline.onsetMs(k), t.startMs(k));
        }
        assertEquals(ClassicDepartureTimeline.END_MS, t.endMs());
    }

    public void testBlackLeadIn() {
        final ClassicDepartureTimeline t = new ClassicDepartureTimeline(DUTCH);
        for (long ms : new long[] { -5, 0, 300, ClassicDepartureTimeline.onsetMs(0) }) {
            final ClassicDepartureTimeline.State s = t.at(ms);
            assertEquals(0, s.transition);
            assertEquals(0, s.revealed);
            assertFalse(s.done);
        }
        assertTrue(t.at(ClassicDepartureTimeline.onsetMs(0) + 1).revealed > 0);
    }

    public void testMatchesTimedCaptures() {
        final ClassicDepartureTimeline t = new ClassicDepartureTimeline(DUTCH);
        for (int[] c : TIMED) {
            // The stamps are good to about 0.05 s; allow that much.
            boolean ok = false;
            String seen = "";
            for (long dt = -50; dt <= 50 && !ok; dt += 5) {
                final ClassicDepartureTimeline.State s = t.at(c[0] + dt);
                final int pct = (int) Math.round(100.0 * s.revealed / DUTCH[s.transition]);
                if (c[2] == 100) {
                    ok = s.transition == c[1] && s.revealed == DUTCH[c[1]];
                } else {
                    ok = s.transition == c[1] && s.revealed < DUTCH[c[1]]
                        && Math.abs(pct - c[2]) <= 6;
                }
                if (dt == 0) seen = s + " " + pct + "%";
            }
            assertTrue("capture at " + c[0] + " ms: model " + seen, ok);
        }
        // The first game scene: done long before +105.5 s.
        assertTrue(t.at(105517).done);
    }

    public void testScheduleBoundaries() {
        final ClassicDepartureTimeline t = new ClassicDepartureTimeline(SMALL);
        final long d = ClassicDepartureTimeline.DISSOLVE_MS;
        // Exactly at an onset the transition begins with nothing revealed.
        ClassicDepartureTimeline.State s = t.at(t.startMs(2));
        assertEquals(2, s.transition);
        assertEquals(0, s.revealed);
        assertFalse(s.done);
        // One ms before it, the previous one is held, fully revealed
        // (transition 1 has no changed pixels).
        s = t.at(t.startMs(2) - 1);
        assertEquals(1, s.transition);
        assertEquals(0, s.revealed);
        s = t.at(t.startMs(1) - 1);
        assertEquals(0, s.transition);
        assertEquals(900, s.revealed);
        // Half a dissolve, 1 px per ms for 900 px over 900 ms.
        s = t.at(t.startMs(0) + d / 2);
        assertEquals(0, s.transition);
        assertEquals(450, s.revealed);
        // A full-screen step takes no longer than a 9-pixel one.
        assertEquals(9, t.at(t.startMs(2) + d).revealed);
        assertEquals(64000, t.at(t.startMs(9) + d).revealed);
    }

    public void testRevealMonotonicAndCapped() {
        final ClassicDepartureTimeline t = new ClassicDepartureTimeline(DUTCH);
        int lastT = 0, lastR = 0;
        for (long ms = 0; ms <= t.endMs() + 50; ms += 7) {
            final ClassicDepartureTimeline.State s = t.at(ms);
            assertTrue(s.transition >= lastT);
            if (s.transition == lastT) assertTrue(s.revealed >= lastR);
            assertTrue(s.revealed >= 0);
            assertTrue(s.revealed <= DUTCH[s.transition]);
            lastT = s.transition;
            lastR = s.revealed;
        }
        // Each transition is complete by the end of its dissolve.
        for (int k = 0; k < DUTCH.length; k++) {
            final ClassicDepartureTimeline.State s = t.at(t.startMs(k)
                + ClassicDepartureTimeline.DISSOLVE_MS);
            assertEquals(k, s.transition);
            assertEquals(DUTCH[k], s.revealed);
        }
    }

    public void testDoneAtEndNotBefore() {
        final ClassicDepartureTimeline t = new ClassicDepartureTimeline(DUTCH);
        assertFalse(t.at(t.endMs() - 1).done);
        assertEquals(9, t.at(t.endMs() - 1).transition);
        assertEquals(DUTCH[9], t.at(t.endMs() - 1).revealed);
        assertTrue(t.at(t.endMs()).done);
        assertTrue(t.at(t.endMs() + 100000).done);
        assertEquals(DUTCH[9], t.at(t.endMs()).revealed);
    }

    public void testSkip() {
        final ClassicDepartureTimeline t = new ClassicDepartureTimeline(DUTCH);
        assertFalse(t.at(3000).done);
        t.skip();
        final ClassicDepartureTimeline.State s = t.at(3000);
        assertTrue(s.done);
        assertEquals(9, s.transition);
        assertTrue(t.at(0).done);
    }

    public void testBadInput() {
        try {
            new ClassicDepartureTimeline(new int[3]);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            new ClassicDepartureTimeline(null);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }
        // Negative counts count as 0.
        final int[] neg = DUTCH.clone();
        neg[3] = -7;
        assertEquals(0, new ClassicDepartureTimeline(neg).changed(3));
    }

    public void testDissolveOrder() {
        final int n = ClassicDeparture.PIXELS;
        final int[] a = new int[n], b = new int[n];
        for (int i = 0; i < n; i += 3) b[i] = 1;
        final int[] o = ClassicDeparture.dissolveOrder(a, b);
        assertEquals((n + 2) / 3, o.length);
        final Set<Integer> seen = new HashSet<>();
        for (int idx : o) {
            assertEquals(0, idx % 3);
            assertTrue(seen.add(idx));
        }
        // Fixed: the same frames give the same order; it is not sequential.
        final int[] o2 = ClassicDeparture.dissolveOrder(a, b);
        for (int i = 0; i < o.length; i++) assertEquals(o[i], o2[i]);
        boolean sorted = true;
        for (int i = 1; i < o.length && sorted; i++) sorted = o[i] > o[i - 1];
        assertFalse(sorted);
        // Applying the whole order turns a into b.
        ClassicDeparture.apply(a, b, o, 0, o.length);
        for (int i = 0; i < n; i++) assertEquals(b[i], a[i]);
        // The permutation is one: every index exactly once.
        final int[] p = ClassicDeparture.permutation();
        final boolean[] hit = new boolean[n];
        for (int idx : p) {
            assertFalse(hit[idx]);
            hit[idx] = true;
        }
    }
}
