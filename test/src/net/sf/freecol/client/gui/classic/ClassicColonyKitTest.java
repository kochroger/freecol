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
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.SwingUtilities;

import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * The colony screen's kit (build spec M4.0 targets 2-7): the notice slot,
 * the hover label and the completion dissolve against clip 020 frames,
 * the press / drag / release machine, the screen's blink on a fake clock,
 * and the screen's presses that still act as before (nothing can be
 * dragged yet).  The golden parts skip without the pack or the recordings
 * ({@code -Dclassic.clips}).
 */
public class ClassicColonyKitTest extends FreeColTestCase {

    /** A clock the test moves (nanoseconds). */
    private static final class FakeClock implements ClassicSlide.Clock {
        long now = 1_000_000_000L;

        @Override
        public long now() {
            return this.now;
        }

        @Override
        public void waitUntil(long due) {
            if (due > this.now) this.now = due;
        }

        void ms(double ms) {
            this.now += Math.round(ms * 1_000_000.0);
        }
    }

    private static void flush() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }


    // The notice slot (target 6)

    /**
     * A held notice stays until it is replaced or cleared; a result's
     * stays 138 frames (1969 ms) and the colony's words come back.
     */
    public void testTheNoticeSlotsTimes() {
        final FakeClock c = new FakeClock();
        final ClassicColonyNotice n = new ClassicColonyNotice();
        assertNull(n.at(c.now()));
        assertEquals(0L, n.until());
        n.hold("Einladen Erz");
        c.ms(60_000);
        assertEquals("Einladen Erz", n.at(c.now()));
        assertEquals(Long.MAX_VALUE, n.until());
        n.show("24 Erz bewegt nach Handelsschiff", c.now());
        c.ms(1968.0);
        assertEquals("24 Erz bewegt nach Handelsschiff", n.at(c.now()));
        c.ms(2.0);
        assertNull("138 frames later the words again", n.at(c.now()));
        n.hold("x");
        n.clear();
        assertNull(n.at(c.now()));
    }

    /**
     * Golden: the notice in the top line as the chrome paints it against
     * clip 020 #10058 («Einladen Erz», w 44 at 138) and #10116 («24 Erz
     * bewegt nach Handelsschiff», w 119 at 100): rows 0-7, 0 differing
     * pixels.
     */
    public void testTheNoticeAgainstClip020() throws Exception {
        final ClassicColonyGolden gold = ClassicColonyGolden.open(getName());
        if (gold == null) return;
        final ClassicColonyChrome.Assets a = ClassicColonyChrome.Assets.load(gold.pack);
        final Object[][] cases = {
            { 10058, "Einladen Erz" }, { 10116, "24 Erz bewegt nach Handelsschiff" },
        };
        final StringBuilder fails = new StringBuilder();
        for (Object[] k : cases) {
            final BufferedImage img = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
            final Graphics2D g = img.createGraphics();
            ClassicColonyChrome.paintNotice(g, a, (String) k[1]);
            g.dispose();
            final BufferedImage frame = gold.frame((Integer) k[0]);
            final ClassicColonyGolden.Diff d = ClassicColonyGolden.compare("notice",
                ClassicColonyGolden.pixels(img), ClassicColonyGolden.pixels(frame),
                new Rectangle(0, 0, 320, 8), gold.pointer(frame), null);
            System.err.println(getName() + " #" + k[0] + ": " + d);
            assertEquals(2560, d.compared + d.pointer);
            if (d.differing != 0) fails.append(" #").append(k[0]).append(": ").append(d);
        }
        assertEquals(fails.toString(), 0, fails.length());
    }


    // The hover label (target 5)

    /**
     * Golden: the hover label against clip 020 #2398 («Rathaus», box x
     * 99-126 y 26-32 over the town hall at (87,11)) and #96680
     * («Schmiede», clamped to x 165-198 y 18-24 over H3 at (173,18)):
     * the box, 0 differing pixels.
     */
    public void testTheHoverLabelAgainstClip020() throws Exception {
        final ClassicColonyGolden gold = ClassicColonyGolden.open(getName());
        if (gold == null) return;
        final ClassicColonyChrome.Assets a = ClassicColonyChrome.Assets.load(gold.pack);
        final Object[][] cases = {
            { 2398, "Rathaus", 87, 53, 26, new Rectangle(99, 26, 28, 7) },
            { 96680, "Schmiede", 173, 23, 18, new Rectangle(165, 18, 34, 7) },
        };
        final StringBuilder fails = new StringBuilder();
        for (Object[] k : cases) {
            final BufferedImage img = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
            final Graphics2D g = img.createGraphics();
            final Rectangle box = ClassicColonyChrome.paintHoverLabel(g, a, (String) k[1],
                (Integer) k[2], (Integer) k[3], (Integer) k[4]);
            g.dispose();
            assertEquals(k[1] + "'s box", k[5], box);
            final BufferedImage frame = gold.frame((Integer) k[0]);
            final ClassicColonyGolden.Diff d = ClassicColonyGolden.compare((String) k[1],
                ClassicColonyGolden.pixels(img), ClassicColonyGolden.pixels(frame),
                box, gold.pointer(frame), null);
            System.err.println(getName() + " #" + k[0] + ": " + d);
            if (d.differing != 0) fails.append(" #").append(k[0]).append(": ").append(d);
        }
        assertEquals(fails.toString(), 0, fails.length());
    }


    // The completion dissolve (target 7)

    /**
     * The dissolve's time line and its rectangles: nothing before 19
     * frames, 1/55 in its first frame, all 54 frames later; only the
     * differing pixels inside the rectangles change, in a fixed order.
     */
    public void testTheDissolvesTimeline() {
        final int[] from = new int[320 * 200], to = new int[320 * 200];
        // 1100 differing pixels inside (10,10)-(119,19), 50 outside.
        for (int y = 10; y < 20; y++) for (int x = 10; x < 120; x++) to[y * 320 + x] = 0xFF0000;
        for (int x = 200; x < 250; x++) to[100 * 320 + x] = 0x00FF00;
        final ClassicColonyDissolve d = new ClassicColonyDissolve(from, to,
            List.of(new Rectangle(10, 10, 110, 10)));
        assertEquals(1100, d.changed());
        assertEquals(0, d.due(0.0));
        assertEquals(0, d.due(ClassicColonyDissolve.START_MS - 0.01));
        assertEquals(1100 / 55, d.due(ClassicColonyDissolve.START_MS));
        assertEquals(1100, d.due(ClassicColonyDissolve.START_MS + ClassicWoodcut.DISSOLVE_MS));
        assertEquals(1100, d.due(1e9));
        final int[] shown = from.clone();
        assertFalse(d.step(100.0, shown));
        assertTrue(d.step(ClassicColonyDissolve.START_MS, shown));
        assertEquals(20, d.revealed());
        assertFalse(d.complete());
        assertTrue(d.step(1e9, shown));
        assertTrue(d.complete());
        for (int y = 10; y < 20; y++) for (int x = 10; x < 120; x++) {
            assertEquals(0xFF0000, shown[y * 320 + x]);
        }
        assertEquals("outside the rectangles nothing changes", 0, shown[100 * 320 + 200]);
        assertEquals("x10-119 y10-19 px 1100", d.describe());
        // The same pictures give the same order (fixed).
        assertTrue(Arrays.equals(ClassicColonyDissolve.order(from, to, d.rects()),
                                 ClassicColonyDissolve.order(from, to, d.rects())));
    }

    /**
     * Golden: the three completion dissolves of clip 020, our dissolve run
     * from the clip's complete old screen to its new one: the clip's first
     * changed frame 19, 19, 17 frames after the screen was complete (ours
     * 19), its last 54 frames later (ours too); our pixels per frame
     * within the clip's random spread (the cumulative counts apart by at
     * most 8 % of the dissolve); our end equal to the clip's inside the
     * rectangles, nothing changed outside.
     */
    public void testTheDissolveAgainstClip020() throws Exception {
        final ClassicColonyGolden gold = ClassicColonyGolden.open(getName());
        if (gold == null) return;
        // complete frame, last dissolve frame, rectangles (spec §M4.10 target 4)
        final Object[][] cases = {
            { 57671, 57744, new Rectangle[] { new Rectangle(88, 15, 80, 27),
                                               new Rectangle(0, 163, 119, 12) } },
            { 73443, 73516, new Rectangle[] { new Rectangle(169, 17, 27, 28) } },
            { 105689, 105760, new Rectangle[] { new Rectangle(6, 15, 44, 21) } },
        };
        final StringBuilder report = new StringBuilder();
        for (Object[] k : cases) {
            final int f0 = (Integer) k[0], f1 = (Integer) k[1];
            final List<Rectangle> rects = Arrays.asList((Rectangle[]) k[2]);
            final int[] from = ClassicColonyGolden.pixels(gold.frame(f0));
            final int[] to = ClassicColonyGolden.pixels(gold.frame(f1));
            final ClassicColonyDissolve d = new ClassicColonyDissolve(from, to, rects);
            // The clip's frames inside the rectangles.
            final boolean[] in = new boolean[320 * 200];
            for (Rectangle r : rects) {
                for (int y = r.y; y < r.y + r.height; y++) {
                    for (int x = r.x; x < r.x + r.width; x++) in[y * 320 + x] = true;
                }
            }
            int[] prev = from;
            int first = -1, last = -1;
            final List<Integer> clipCum = new ArrayList<>();
            int cum = 0;
            for (int f = f0 + 1; f <= f1; f++) {
                final int[] cur = ClassicColonyGolden.pixels(gold.frame(f));
                int n = 0;
                for (int i = 0; i < cur.length; i++) if (in[i] && cur[i] != prev[i]) n++;
                if (n > 0) {
                    if (first < 0) first = f - f0;
                    last = f - f0;
                }
                cum += n;
                if (first >= 0) clipCum.add(cum);
                prev = cur;
            }
            assertTrue(f0 + ": the clip starts 17-20 frames after", first >= 17 && first <= 20);
            assertEquals(f0 + ": the clip's 55 frames", 55, last - first + 1);
            assertEquals(f0 + ": all its pixels changed once", d.changed(), cum);
            // Ours, frame by frame from the first one (the start: 19 frames).
            final int[] shown = from.clone();
            int maxDev = 0, ourFirst = -1, ourLast = -1;
            final StringBuilder curve = new StringBuilder();
            for (int j = 0; j < 80; j++) {
                final double since = j * ClassicColonyLayout.FRAME_MS + 0.5;
                final int before = d.revealed();
                d.step(since, shown);
                if (d.revealed() > before) {
                    if (ourFirst < 0) ourFirst = j;
                    ourLast = j;
                }
                final int k2 = j - first;
                if (k2 >= 0 && k2 < clipCum.size()) {
                    maxDev = Math.max(maxDev, Math.abs(d.revealed() - clipCum.get(k2)));
                    if (k2 % 9 == 0) {
                        curve.append(' ').append(k2).append(':').append(d.revealed())
                            .append('/').append(clipCum.get(k2));
                    }
                }
            }
            assertEquals(19, ourFirst);
            assertEquals(19 + 54, ourLast);
            assertTrue(d.complete());
            int inDiff = 0, outDiff = 0;
            for (int i = 0; i < shown.length; i++) {
                if (in[i] && shown[i] != to[i]) inDiff++;
                if (!in[i] && shown[i] != from[i]) outDiff++;
            }
            assertEquals(f0 + ": the new picture inside", 0, inDiff);
            assertEquals(f0 + ": nothing outside", 0, outDiff);
            report.append(String.format(" #%d: %d px, clip from +%d, ours from +%d, max"
                + " cumulative difference %d px (%.1f %%), ours/clip at k:%s%n", f0,
                d.changed(), first, ourFirst, maxDev, 100.0 * maxDev / d.changed(), curve));
            assertTrue(f0 + ": the spread " + maxDev + " of " + d.changed(),
                       maxDev <= 0.08 * d.changed());
        }
        System.err.println(getName() + ":\n" + report);
    }


    // The press / drag / release machine (target 3)

    /**
     * A press and a release without motion is a click; the first motion
     * of a draggable press starts a drag and its release is the drop; a
     * press on nothing draggable never drags; a motion that keeps the
     * device pixel does not start one.
     */
    public void testTheDragGesture() {
        final ClassicDragGesture g = new ClassicDragGesture();
        assertEquals(ClassicDragGesture.State.IDLE, g.state());
        assertEquals(ClassicDragGesture.Result.NONE, g.release());
        g.press(10, 20, 50, 100, true);
        assertEquals(ClassicDragGesture.State.PRESSED, g.state());
        assertEquals("[10, 20]", Arrays.toString(g.pressPoint()));
        assertEquals(ClassicDragGesture.Result.CLICK, g.release());
        g.press(10, 20, 50, 100, true);
        assertFalse("the same device pixel", g.motion(50, 100));
        assertFalse(g.dragging());
        assertTrue("the first motion starts the drag", g.motion(51, 100));
        assertTrue(g.dragging());
        assertFalse("only once", g.motion(60, 100));
        assertEquals(ClassicDragGesture.Result.DROP, g.release());
        assertFalse(g.dragging());
        g.press(10, 20, 50, 100, false);
        assertFalse(g.motion(90, 140));
        assertEquals(ClassicDragGesture.Result.CLICK, g.release());
        g.press(10, 20, 50, 100, true);
        g.motion(60, 100);
        g.cancel();
        assertEquals(ClassicDragGesture.State.IDLE, g.state());
        assertEquals(ClassicDragGesture.Result.NONE, g.release());
        // The lift by holding (part Z D1): the press still down, no motion.
        g.press(10, 20, 50, 100, true);
        final int p = g.presses();
        assertTrue("held: the drag starts", g.hold(p));
        assertTrue(g.dragging());
        assertFalse("only once", g.hold(p));
        assertFalse("then no motion starts it again", g.motion(70, 100));
        assertEquals(ClassicDragGesture.Result.DROP, g.release());
        assertFalse("let go: nothing", g.hold(p));
        g.press(10, 20, 50, 100, true);
        assertFalse("an older press's timer", g.hold(p));
        assertEquals(p + 1, g.presses());
        assertTrue(g.motion(51, 100));
        assertFalse("a drag already", g.hold(p + 1));
        g.release();
        g.press(10, 20, 50, 100, false);
        assertFalse("nothing to drag", g.hold(g.presses()));
        assertEquals(ClassicDragGesture.Result.CLICK, g.release());
    }


    // The screen itself: blink, drags, notice, selection

    private ClassicColonyPanel panel(FakeClock clock) {
        final Game game = getStandardGame();
        game.changeMap(getTestMap(true));
        final Colony colony = createStandardColony(2);
        return new ClassicColonyPanel(null, null, colony, null, null, clock, false);
    }

    /**
     * The screen's blink (target 4) on a fake clock: on first, off after
     * 23 frames (ClassicBlink's 328.5 ms), on again; stopped meanwhile no
     * toggle comes; a painter that asks makes the toggles repaint.
     */
    public void testTheScreensBlink() throws Exception {
        final FakeClock clock = new FakeClock();
        final ClassicColonyPanel p = panel(clock);
        final ClassicBlink b = p.blink();
        assertFalse(b.isArmed());
        b.arm();
        assertFalse(p.blinkOff());
        long[] n = b.next();
        assertEquals(1L, n[1]);
        assertEquals(clock.now + Math.round(ClassicBlink.HALF_PERIOD_MS * 1e6), n[2]);
        clock.waitUntil(n[2]);
        assertTrue(b.due((int) n[0], (int) n[1]));
        flush();
        assertTrue("off after 23 frames", p.blinkOff());
        n = b.next();
        clock.waitUntil(n[2]);
        b.due((int) n[0], (int) n[1]);
        flush();
        assertFalse("on again", p.blinkOff());
        b.stop();
        assertNull("stopped: no toggle", b.next());
        p.dispose();
        b.arm();
        assertFalse("disposed: for good", b.isArmed());
    }

    /**
     * The screen's mouse (target 3) while nothing can be dragged yet: a
     * press, a motion and a release are no drag (no DRAGGING event, the
     * arrow stays), the press acts as before.
     */
    public void testNothingOnTheScreenDragsYet() throws Exception {
        final FakeClock clock = new FakeClock();
        final ClassicColonyPanel p = panel(clock);
        p.setSize(960, 600);
        final List<Object> events = new ArrayList<>();
        p.addPropertyChangeListener(ClassicPointer.DRAGGING, e -> events.add(e.getNewValue()));
        SwingUtilities.invokeAndWait(() -> {
                p.dispatchEvent(new MouseEvent(p, MouseEvent.MOUSE_PRESSED, 0L,
                    InputEvent.BUTTON1_DOWN_MASK, 300, 300, 1, false, MouseEvent.BUTTON1));
                p.dispatchEvent(new MouseEvent(p, MouseEvent.MOUSE_DRAGGED, 0L,
                    InputEvent.BUTTON1_DOWN_MASK, 400, 330, 0, false, MouseEvent.NOBUTTON));
                assertFalse(p.dragging());
                p.dispatchEvent(new MouseEvent(p, MouseEvent.MOUSE_RELEASED, 0L, 0,
                    400, 330, 1, false, MouseEvent.BUTTON1));
            });
        assertTrue(events.toString(), events.isEmpty());
        assertFalse(p.dragging());
        // The harness's drag: a press and a release.
        SwingUtilities.invokeAndWait(() -> p.dragAt(100, 60, 150, 90, false));
        assertTrue(events.toString(), events.isEmpty());
    }

    /**
     * The notice slot on the screen: held, a result's 138 frames, cleared;
     * the warehouse's selection is global.
     */
    public void testTheScreensNoticeAndSelection() throws Exception {
        final FakeClock clock = new FakeClock();
        final ClassicColonyPanel p = panel(clock);
        assertNull(p.noticeShown());
        SwingUtilities.invokeAndWait(() -> p.holdNotice("Einladen Erz"));
        assertEquals("Einladen Erz", p.noticeShown());
        SwingUtilities.invokeAndWait(() -> p.showNotice("24 Erz bewegt nach Handelsschiff"));
        clock.ms(1960);
        assertEquals("24 Erz bewegt nach Handelsschiff", p.noticeShown());
        clock.ms(10);
        assertNull(p.noticeShown());
        SwingUtilities.invokeAndWait(() -> {
                p.holdNotice("x");
                p.clearNotice();
            });
        assertNull(p.noticeShown());
        p.dispose();
        final int was = ClassicColonyPanel.warehouseSelection();
        try {
            ClassicColonyPanel.selectWarehouseGood(9);
            assertEquals(9, ClassicColonyPanel.warehouseSelection());
            ClassicColonyPanel.selectWarehouseGood(16);
            assertEquals("out of range: kept", 9, ClassicColonyPanel.warehouseSelection());
            ClassicColonyPanel.selectWarehouseGood(-1);
            assertEquals(9, ClassicColonyPanel.warehouseSelection());
        } finally {
            ClassicColonyPanel.selectWarehouseGood(was);
        }
    }
}
