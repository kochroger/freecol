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

import junit.framework.TestCase;

import net.sf.freecol.client.gui.classic.ClassicIntroTimeline.Phase;
import net.sf.freecol.client.gui.classic.ClassicIntroTimeline.State;


/**
 * Headless tests of the intro clock ({@link ClassicIntroTimeline}).
 */
public class ClassicIntroTimelineTest extends TestCase {

    public void testBlackLeadIn() {
        final ClassicIntroTimeline tl = new ClassicIntroTimeline();
        assertEquals(Phase.BLACK, tl.at(-50).phase);
        assertEquals(Phase.BLACK, tl.at(0).phase);
        assertEquals(Phase.BLACK, tl.at(299).phase);
        assertEquals(Phase.EMBLEM, tl.at(300).phase);
    }

    public void testEmblemFrames() {
        final ClassicIntroTimeline tl = new ClassicIntroTimeline();
        final double ms = 1000.0 / ClassicIntroTimeline.EMBLEM_FPS;   // 98.8
        assertEquals(98.8, ms, 0.05);
        for (int n = 0; n < 100; n++) {
            final long at = 300L + (long) Math.ceil(n * ms);
            assertEquals("frame " + n, n, tl.at(at).emblemFrame);
            assertEquals(8, tl.at(at).level);
        }
    }

    public void testSubtitleStages() {
        final ClassicIntroTimeline tl = new ClassicIntroTimeline();
        final double ms = 1000.0 / ClassicIntroTimeline.EMBLEM_FPS;
        for (int n = 0; n <= 11; n++) {
            assertEquals(0, tl.at(300L + (long) Math.ceil(n * ms)).subtitleK);
        }
        assertEquals(1, tl.at(300L + (long) Math.ceil(12 * ms)).subtitleK);
        assertEquals(27, tl.at(300L + (long) Math.ceil(38 * ms)).subtitleK);
        for (int n = 39; n < 100; n++) {
            assertEquals(28, tl.at(300L + (long) Math.ceil(n * ms)).subtitleK);
        }
    }

    public void testFade() {
        final ClassicIntroTimeline tl = new ClassicIntroTimeline();
        assertEquals(Phase.EMBLEM, tl.at(10299).phase);
        for (int j = 0; j < 8; j++) {
            final State st = tl.at(10300L + (long) Math.ceil(j * 62.5));
            assertEquals(Phase.FADE, st.phase);
            assertEquals("step " + j, 7 - j, st.level);
        }
        assertEquals(0, tl.at(10799).level);
        assertEquals(Phase.BLACK, tl.at(10800).phase);
        assertEquals(Phase.BLACK, tl.at(11099).phase);
    }

    public void testChartFrames() {
        final ClassicIntroTimeline tl = new ClassicIntroTimeline();
        tl.setEndFrame(891);
        State st = tl.at(11100);
        assertEquals(Phase.CHART, st.phase);
        assertEquals(0, st.chartFrame);
        // The pan reaches America (f = 640) at ~95.3 s.
        assertEquals(640, tl.at(11100L + (long) Math.ceil(640 * 131.52)).chartFrame);
        assertEquals(639, tl.at(95272L).chartFrame);
        assertEquals(640, tl.at(95273L).chartFrame);
        // The title logo at 767, the freeze at 769.
        assertEquals(767, tl.at(11100L + (long) Math.ceil(767 * 131.52)).chartFrame);
        assertEquals(769, tl.at(11100L + (long) Math.ceil(800 * 131.52)).chartFrame);
        assertEquals(769, tl.at(11100L + (long) Math.ceil(890 * 131.52)).chartFrame);
        // END at frame 891, ~128.3 s.
        assertEquals(Phase.DONE, tl.at(11100L + (long) Math.ceil(891 * 131.52)).phase);
        assertEquals(128285L, tl.endMs(), 2.0);
        assertTrue(tl.at(tl.endMs()).done());
        assertFalse(tl.at(tl.endMs() - 2).done());
    }

    public void testMonotonic() {
        final ClassicIntroTimeline tl = new ClassicIntroTimeline();
        Phase last = Phase.BLACK;
        int lastFrame = -1;
        boolean sawEmblem = false;
        for (long t = 0; t < 135000; t += 7) {
            final State st = tl.at(t);
            if (st.phase == Phase.EMBLEM) sawEmblem = true;
            if (st.phase == Phase.CHART) {
                assertTrue(st.chartFrame >= lastFrame);
                lastFrame = st.chartFrame;
            }
            // Phases never go back (BLACK appears before EMBLEM and CHART).
            if (st.phase != Phase.BLACK) {
                assertTrue(st.phase + " after " + last, st.phase.ordinal() >= last.ordinal());
                last = st.phase;
            }
        }
        assertTrue(sawEmblem);
        assertEquals(Phase.DONE, last);
        assertEquals(ClassicIntroTimeline.FREEZE_FRAME, lastFrame);
    }

    public void testSkip() {
        final ClassicIntroTimeline tl = new ClassicIntroTimeline();
        assertEquals(Phase.EMBLEM, tl.at(2000).phase);
        tl.skip();
        assertTrue(tl.at(2000).done());
        assertTrue(tl.at(0).done());
    }

    public void testWithoutChart() {
        final ClassicIntroTimeline tl = new ClassicIntroTimeline();
        tl.setHasChart(false);
        assertEquals(Phase.FADE, tl.at(10500).phase);
        assertTrue(tl.at(10800).done());
        assertTrue(tl.at(50000).done());
        assertEquals(10800L, tl.endMs());
    }

    public void testEndFrameFloor() {
        final ClassicIntroTimeline tl = new ClassicIntroTimeline();
        tl.setEndFrame(10);                      // too early: the freeze still shows
        assertEquals(Phase.CHART, tl.at(11100L + (long) Math.ceil(769 * 131.52)).phase);
    }
}
