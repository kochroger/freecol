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
 * synthetic pixel counts and frames.
 */
public class ClassicDepartureTimelineTest extends TestCase {

    /** Roughly the real show: audience, four dawn steps, five ship steps. */
    private static final int[] TYPICAL = {
        64000, 62000, 62000, 62000, 62000, 8900, 6000, 4000, 2500, 2000
    };

    private static final int[] SMALL = { 39000, 0, 3900, 1, 2, 3, 4, 5, 6, 39001 };

    public void testDissolveMs() {
        assertEquals(0L, ClassicDepartureTimeline.dissolveMs(0));
        assertEquals(1L, ClassicDepartureTimeline.dissolveMs(1));
        assertEquals(1000L, ClassicDepartureTimeline.dissolveMs(39000));
        assertEquals(1001L, ClassicDepartureTimeline.dissolveMs(39001));
        assertEquals(100L, ClassicDepartureTimeline.dissolveMs(3900));
    }

    public void testScheduleBoundaries() {
        final ClassicDepartureTimeline t = new ClassicDepartureTimeline(SMALL);
        final long h = ClassicDepartureTimeline.HOLD_MS;
        assertEquals(0L, t.startMs(0));
        assertEquals(1000L + h, t.startMs(1));
        assertEquals(1000L + 2 * h, t.startMs(2));        // transition 1 has 0 px
        assertEquals(1100L + 3 * h, t.startMs(3));
        long sum = 0;
        for (int px : SMALL) sum += ClassicDepartureTimeline.dissolveMs(px);
        assertEquals(sum + 10 * h, t.endMs());

        // Exactly at a boundary the next transition begins with nothing revealed.
        ClassicDepartureTimeline.State s = t.at(t.startMs(2));
        assertEquals(2, s.transition);
        assertEquals(0, s.revealed);
        assertFalse(s.done);
        // One ms before it, the previous one is held, fully revealed.
        s = t.at(t.startMs(2) - 1);
        assertEquals(1, s.transition);
        assertEquals(0, s.revealed);
        s = t.at(t.startMs(1) - 1);
        assertEquals(0, s.transition);
        assertEquals(39000, s.revealed);
        // Half a dissolve.
        s = t.at(500);
        assertEquals(0, s.transition);
        assertEquals(19500, s.revealed);
    }

    public void testRevealMonotonicAndCapped() {
        final ClassicDepartureTimeline t = new ClassicDepartureTimeline(TYPICAL);
        int lastT = 0, lastR = 0;
        for (long ms = 0; ms <= t.endMs() + 50; ms += 7) {
            final ClassicDepartureTimeline.State s = t.at(ms);
            assertTrue(s.transition >= lastT);
            if (s.transition == lastT) assertTrue(s.revealed >= lastR);
            assertTrue(s.revealed >= 0);
            assertTrue(s.revealed <= TYPICAL[s.transition]);
            lastT = s.transition;
            lastR = s.revealed;
        }
        // Each transition is complete by the end of its dissolve.
        for (int k = 0; k < TYPICAL.length; k++) {
            final ClassicDepartureTimeline.State s = t.at(t.startMs(k)
                + ClassicDepartureTimeline.dissolveMs(TYPICAL[k]));
            assertEquals(k, s.transition);
            assertEquals(TYPICAL[k], s.revealed);
        }
    }

    public void testDoneAtEndNotBefore() {
        final ClassicDepartureTimeline t = new ClassicDepartureTimeline(TYPICAL);
        assertFalse(t.at(t.endMs() - 1).done);
        assertEquals(9, t.at(t.endMs() - 1).transition);
        assertTrue(t.at(t.endMs()).done);
        assertTrue(t.at(t.endMs() + 100000).done);
        assertEquals(TYPICAL[9], t.at(t.endMs()).revealed);
        assertFalse(t.at(-5).done);
        assertEquals(0, t.at(-5).revealed);
    }

    public void testSkip() {
        final ClassicDepartureTimeline t = new ClassicDepartureTimeline(TYPICAL);
        assertFalse(t.at(3000).done);
        t.skip();
        final ClassicDepartureTimeline.State s = t.at(3000);
        assertTrue(s.done);
        assertEquals(9, s.transition);
        assertTrue(t.at(0).done);
    }

    public void testTypicalLength() {
        final ClassicDepartureTimeline t = new ClassicDepartureTimeline(TYPICAL);
        assertTrue("end " + t.endMs(), t.endMs() >= 97000L && t.endMs() <= 100000L);
        // The captures: Dutch picture 1 settled by +4.6 s.
        final ClassicDepartureTimeline.State s = t.at(4600);
        assertEquals(0, s.transition);
        assertEquals(TYPICAL[0], s.revealed);
    }

    public void testBadInput() {
        try {
            new ClassicDepartureTimeline(new int[3]);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }
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
