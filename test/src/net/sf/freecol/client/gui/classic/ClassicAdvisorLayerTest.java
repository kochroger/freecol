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
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.SwingUtilities;

import junit.framework.TestCase;

import net.sf.freecol.tools.classicassets.ClassicAssetDecoderTest;
import net.sf.freecol.tools.classicassets.FfDecoder;


/**
 * Tests of the advisor boxes' layer ({@link ClassicAdvisorLayer}, build
 * spec W7), headless: a box is modal like a dialog (its {@link
 * ClassicAdvisorLayer#show} returns the answer while the event queue keeps
 * pumping), takes the keys and the mouse, comes 200 ms after the box
 * before it, loads a new portrait's palette three frames before its box,
 * and the boxes asked for meanwhile come in their order.  The clock is a
 * fake one and the timer runs only when the test says so.
 */
public class ClassicAdvisorLayerTest extends TestCase {

    /** A clock the test sets. */
    private static final class FakeClock implements ClassicSlide.Clock {

        volatile long now = 1_000_000_000L;

        @Override
        public long now() {
            return this.now;
        }

        @Override
        public void waitUntil(long due) {
            // never waits: the test advances the clock
        }

        void advanceMs(double ms) {
            this.now += Math.round(ms * 1e6);
        }
    }

    /** The game view, recorded. */
    private static final class FakeHost implements ClassicAdvisorLayer.Host {

        ClassicFont font = tinyFont();
        boolean repeat = false;
        int opened = 0, closed = 0, idle = 0;
        final List<String> palettes = new ArrayList<>();

        @Override
        public ClassicFont font() {
            return this.font;
        }

        @Override
        public BufferedImage wood() {
            return null;
        }

        @Override
        public BufferedImage portrait(String sprite) {
            final BufferedImage img = new BufferedImage(75, 91,
                BufferedImage.TYPE_INT_ARGB);
            final Graphics2D g = img.createGraphics();
            g.setColor(java.awt.Color.RED);
            g.fillRect(0, 0, 75, 91);
            g.dispose();
            return img;
        }

        @Override
        public int[] portraitPalette(ClassicAdvisorBox.Portrait p) {
            this.palettes.add(p.sprite);
            return null;
        }

        @Override
        public void opened() {
            this.opened++;
        }

        @Override
        public void closed() {
            this.closed++;
        }

        @Override
        public void idle() {
            this.idle++;
        }

        @Override
        public boolean isAutoRepeat(KeyEvent e) {
            return this.repeat;
        }
    }

    private static int[] glyph(int code, int w) {
        final int[] g = new int[2 + w];
        g[0] = code;
        g[1] = w;
        g[2] = 1;
        return g;
    }

    private static ClassicFont tinyFont() {
        final FfDecoder.Font f = FfDecoder.decodePart(ClassicAssetDecoderTest.ffPart(1, 6,
            new int[][] { glyph('a', 3), glyph(' ', 2) }));
        return ClassicFont.fromAtlas(FfDecoder.toAtlas(f), FfDecoder.metrics(f));
    }

    private FakeClock clock;
    private FakeHost host;
    private ClassicAdvisorLayer layer;


    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.clock = new FakeClock();
        this.host = new FakeHost();
        this.layer = edt(() -> {
                final ClassicAdvisorLayer l = new ClassicAdvisorLayer(this.host,
                    this.clock, SwingUtilities::invokeLater, false);
                l.setSize(320, 200);          // scale 1: layer points = screen
                return l;
            });
    }

    @Override
    protected void tearDown() throws Exception {
        edt(() -> {
                this.layer.dispose();
                return null;
            });
        super.tearDown();
    }


    // Helpers

    private static <T> T edt(Callable<T> c) throws Exception {
        final Object[] out = new Object[1];
        final Exception[] err = new Exception[1];
        SwingUtilities.invokeAndWait(() -> {
                try {
                    out[0] = c.call();
                } catch (Exception e) {
                    err[0] = e;
                }
            });
        if (err[0] != null) throw err[0];
        @SuppressWarnings("unchecked")
        final T t = (T) out[0];
        return t;
    }

    /** An answer that comes later. */
    private static final class Answer {

        final AtomicInteger value = new AtomicInteger(Integer.MIN_VALUE);
        final CountDownLatch done = new CountDownLatch(1);

        int get() throws InterruptedException {
            assertTrue("the box never answered", done.await(5, TimeUnit.SECONDS));
            return this.value.get();
        }

        boolean isDone() {
            return this.done.getCount() == 0;
        }
    }

    /** Ask for a box on the EDT; the answer comes when it closes. */
    private Answer ask(ClassicAdvisorBox.Request r) {
        final Answer a = new Answer();
        SwingUtilities.invokeLater(() -> {
                a.value.set(this.layer.show(r));
                a.done.countDown();
            });
        return a;
    }

    /** Let the EDT run everything queued so far. */
    private static void flush() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });
    }

    /** Run the timer if due, then let its task run. */
    private void runTimer() throws Exception {
        this.layer.runDue();
        flush();
    }

    private void key(int code) throws Exception {
        final long when = System.currentTimeMillis() + 5;
        edt(() -> {
                this.layer.onKey(new KeyEvent(this.layer, KeyEvent.KEY_PRESSED, when,
                    0, code, KeyEvent.CHAR_UNDEFINED));
                return null;
            });
    }

    private void mouse(int id, int x, int y) throws Exception {
        final long when = System.currentTimeMillis() + 5;
        edt(() -> {
                final MouseEvent e = new MouseEvent(this.layer, id, when,
                    MouseEvent.BUTTON1_DOWN_MASK, x, y, 1, false, MouseEvent.BUTTON1);
                if (id == MouseEvent.MOUSE_PRESSED) this.layer.onPress(e);
                else this.layer.onRelease(e);
                return null;
            });
    }

    private boolean up() throws Exception {
        return edt(() -> this.layer.isShowingBox());
    }

    private int bar() throws Exception {
        return edt(() -> this.layer.currentBar());
    }

    private static ClassicAdvisorBox.Request question(String id) {
        return ClassicAdvisorBox.Request.builder(id).freeColText("a a a")
            .rows("a", "a a", "a a a").cancelRow(2).build();
    }


    // Tests

    /**
     * A box comes at once and blocks its caller while the event queue
     * pumps; the keys move the bar one row each and Enter answers the
     * barred row; the layer is idle again after it.
     */
    public void testKeysAnswerTheBox() throws Exception {
        final Answer a = ask(question("q"));
        flush();
        assertTrue(up());
        assertFalse(a.isDone());               // modal: the caller waits
        assertEquals(1, this.host.opened);
        assertEquals(0, bar());
        key(KeyEvent.VK_DOWN);
        assertEquals(1, bar());
        key(KeyEvent.VK_KP_DOWN);
        assertEquals(2, bar());
        key(KeyEvent.VK_NUMPAD2);              // the last row: stays
        assertEquals(2, bar());
        key(KeyEvent.VK_UP);
        key(KeyEvent.VK_A);                    // other keys: nothing
        key(KeyEvent.VK_SHIFT);
        assertEquals(1, bar());
        assertFalse(a.isDone());
        key(KeyEvent.VK_ENTER);
        assertEquals(1, a.get());
        assertFalse(up());
        assertFalse(edt(() -> this.layer.isBusy()));
        assertEquals(1, this.host.closed);
        assertEquals(1, this.host.idle);
        // Escape: the cancel row.
        this.clock.advanceMs(1000);
        final Answer b = ask(question("q2"));
        flush();
        assertTrue(up());
        key(KeyEvent.VK_ESCAPE);
        assertEquals(2, b.get());
    }

    /**
     * Enter, Escape and the other keys count only as fresh presses: an
     * auto-repeat of a key held from before does nothing, an arrow's
     * repeat moves the bar.  A key pressed before the box was drawn does
     * nothing either.
     */
    public void testHeldKeysDoNothing() throws Exception {
        final Answer a = ask(question("q"));
        flush();
        this.host.repeat = true;
        key(KeyEvent.VK_ENTER);
        key(KeyEvent.VK_ESCAPE);
        assertTrue(up());
        key(KeyEvent.VK_DOWN);                 // an arrow's repeat moves
        assertEquals(1, bar());
        this.host.repeat = false;
        // Older than the box: nothing.
        edt(() -> {
                this.layer.onKey(new KeyEvent(this.layer, KeyEvent.KEY_PRESSED,
                    System.currentTimeMillis() - 5000, 0, KeyEvent.VK_ENTER,
                    KeyEvent.CHAR_UNDEFINED));
                return null;
            });
        assertTrue(up());
        key(KeyEvent.VK_ENTER);
        assertEquals(1, a.get());
    }

    /**
     * The mouse: a press on a row marks it, the release takes it; a press
     * outside the box removes the bar, its release outside closes as
     * Escape.
     */
    public void testMouse() throws Exception {
        final Answer a = ask(question("q"));
        flush();
        final ClassicAdvisorBox.Layout l = edt(() -> this.layer.currentLayout());
        final java.awt.Rectangle row2 = l.barRect(2);
        mouse(MouseEvent.MOUSE_PRESSED, row2.x + 10, row2.y + 2);
        assertEquals(2, bar());
        assertFalse(a.isDone());
        mouse(MouseEvent.MOUSE_RELEASED, row2.x + 10, row2.y + 2);
        assertEquals(2, a.get());

        this.clock.advanceMs(1000);
        final Answer b = ask(question("q2"));
        flush();
        mouse(MouseEvent.MOUSE_PRESSED, 2, 2);         // outside the box
        assertEquals(-1, bar());
        mouse(MouseEvent.MOUSE_RELEASED, 3, 3);
        assertEquals(2, b.get());                      // as Escape
    }

    /**
     * E acceptance must-fix: on a box like the King's (a click outside does
     * not cancel), neither a click on his portrait nor one outside answers
     * it or moves the bar; Enter then takes the barred row.
     */
    public void testMouseOutsideTheKingsBox() throws Exception {
        final ClassicAdvisorBox.Request r = ClassicAdvisorBox.Request.builder("king")
            .freeColText("a a a").rows("a", "a a").cancelRow(1)
            .portrait(ClassicAdvisorBox.Portrait.KING).outsideCancels(false).build();
        final Answer a = ask(r);
        flush();
        runTimer();                                    // the palette
        this.clock.advanceMs(ClassicAdvisorLayer.PALETTE_LEAD_MS);
        runTimer();
        assertTrue(up());
        final ClassicAdvisorBox.Layout l = edt(() -> this.layer.currentLayout());
        final java.awt.Point king = l.portraitAt;
        assertEquals(0, king.x);
        mouse(MouseEvent.MOUSE_PRESSED, king.x + 30, king.y + 40);   // the King
        assertEquals(0, bar());
        mouse(MouseEvent.MOUSE_RELEASED, king.x + 30, king.y + 40);
        assertFalse(a.isDone());
        key(KeyEvent.VK_DOWN);
        mouse(MouseEvent.MOUSE_PRESSED, l.box.x - 2, 2);             // outside
        assertEquals(1, bar());
        mouse(MouseEvent.MOUSE_RELEASED, l.box.x - 2, 3);
        flush();
        assertFalse(a.isDone());
        assertTrue(up());
        assertEquals(1, bar());
        key(KeyEvent.VK_UP);
        key(KeyEvent.VK_ENTER);
        assertEquals(0, a.get());
    }

    /**
     * Chained boxes: the next one comes no earlier than 200 ms after the
     * close of the one before; the screen is restored in between.  A box
     * asked for while one is up waits its turn, and both callers get their
     * own answers.
     */
    public void testChainedBoxesComeInTurn() throws Exception {
        final Answer a = ask(question("first"));
        flush();
        assertTrue(up());
        // A second box asked for from an event while the first is up.
        final Answer b = ask(question("second"));
        flush();
        assertEquals("first:0", edt(() -> this.layer.probe()));
        key(KeyEvent.VK_DOWN);
        key(KeyEvent.VK_ENTER);                        // the first: row 1
        flush();
        assertFalse(up());                             // restored, the next due
        assertTrue(edt(() -> this.layer.isBusy()));
        assertEquals("due", edt(() -> this.layer.probe()));
        assertEquals(1, this.host.closed);
        assertEquals(0, this.host.idle);
        this.clock.advanceMs(ClassicAdvisorLayer.CHAIN_MS - 1);
        runTimer();
        assertFalse(up());                             // not yet
        this.clock.advanceMs(1);
        runTimer();
        assertTrue(up());
        assertEquals("second:0", edt(() -> this.layer.probe()));
        key(KeyEvent.VK_ESCAPE);
        assertEquals(2, b.get());
        assertEquals(1, a.get());
        assertEquals(2, this.host.closed);
        assertEquals(1, this.host.idle);
        assertEquals(2, this.host.opened);
    }

    /**
     * A box with a new portrait loads its palette three frames before it
     * appears; the same portrait again loads nothing.
     */
    public void testPortraitPaletteLead() throws Exception {
        final ClassicAdvisorBox.Request r = ClassicAdvisorBox.Request.builder("p")
            .freeColText("a").rows("a", "a").portrait(ClassicAdvisorBox.Portrait.ADMIRAL)
            .build();
        final Answer a = ask(r);
        flush();
        assertFalse(up());
        runTimer();                                    // the load, at once
        assertEquals(List.of("MSS0.SS.000"), this.host.palettes);
        assertFalse(up());
        this.clock.advanceMs(ClassicAdvisorLayer.PALETTE_LEAD_MS - 1);
        runTimer();
        assertFalse(up());
        this.clock.advanceMs(1);
        runTimer();
        assertTrue(up());
        key(KeyEvent.VK_ENTER);
        assertEquals(0, a.get());
        // The same admiral again, 200 ms on: no load, at once.
        this.clock.advanceMs(ClassicAdvisorLayer.CHAIN_MS);
        final Answer b = ask(r);
        flush();
        assertTrue(up());
        assertEquals(1, this.host.palettes.size());
        key(KeyEvent.VK_ENTER);
        assertEquals(0, b.get());
        assertEquals(3, ClassicAdvisorLayer.PALETTE_LEAD_FRAMES);
        assertEquals(42.8, ClassicAdvisorLayer.PALETTE_LEAD_MS, 0.05);
    }

    /** A notice goes on any key; without the font no box can be drawn. */
    public void testNoticeAndUnavailable() throws Exception {
        final Answer a = ask(ClassicAdvisorBox.Request.builder("n")
                             .freeColText("a a").build());
        flush();
        assertTrue(up());
        key(KeyEvent.VK_A);
        assertEquals(0, a.get());
        this.host.font = null;
        assertEquals(ClassicAdvisorLayer.UNAVAILABLE,
                     (int) edt(() -> this.layer.show(question("q"))));
        assertFalse(edt(() -> this.layer.isBusy()));
    }

    /** The game view goes: every box up or due answers "dismissed". */
    public void testAbort() throws Exception {
        final Answer a = ask(question("first"));
        final Answer b = ask(question("second"));
        flush();
        assertTrue(up());
        edt(() -> {
                this.layer.abort();
                return null;
            });
        assertEquals(ClassicAdvisorBox.Bar.DISMISSED, a.get());
        assertEquals(ClassicAdvisorBox.Bar.DISMISSED, b.get());
        assertFalse(up());
        assertFalse(edt(() -> this.layer.isBusy()));
    }

    /**
     * A checkbox box (the option boxes, build spec W14): Enter, Space, a
     * row's gold letter and a click (the press marks, the release flips)
     * flip rows, each applied at once, and the box stays up; Escape closes
     * it with no row, and so does a press and release outside.
     */
    public void testACheckboxBoxStaysUp() throws Exception {
        final List<String> flips = new ArrayList<>();
        final ClassicAdvisorBox.Request r = ClassicAdvisorBox.Request.builder("opts")
            .freeColText("a a").rows("a", "~b a", "a a")
            .checks(new boolean[] { true, false, false },
                    (row, on) -> flips.add(row + "=" + on))
            .build();
        final Answer a = ask(r);
        flush();
        assertTrue(up());
        assertEquals(0, bar());
        key(KeyEvent.VK_ENTER);
        key(KeyEvent.VK_SPACE);
        assertTrue(up());
        key(KeyEvent.VK_B);
        assertEquals(1, bar());
        final ClassicAdvisorBox.Layout l = edt(() -> this.layer.currentLayout());
        final java.awt.Rectangle hit = ClassicMenuBox.rowHitRect(l.box, l.promptLines(), 2);
        mouse(MouseEvent.MOUSE_PRESSED, hit.x + 5, hit.y + 3);
        assertEquals(2, bar());
        assertEquals(3, flips.size());                 // the press only marks
        mouse(MouseEvent.MOUSE_RELEASED, hit.x + 5, hit.y + 3);
        assertEquals(java.util.Arrays.asList("0=false", "0=true", "1=true", "2=true"),
                     flips);
        assertFalse(a.isDone());
        assertTrue(up());
        assertEquals(1, this.host.opened);
        assertEquals(0, this.host.closed);
        key(KeyEvent.VK_ESCAPE);
        assertEquals(ClassicAdvisorBox.Bar.DISMISSED, a.get());
        assertFalse(up());
        // Again: the bar on row 1, closed by a click outside.
        this.clock.advanceMs(1000);
        final Answer b = ask(r);
        flush();
        assertTrue(up());
        assertEquals(0, bar());
        mouse(MouseEvent.MOUSE_PRESSED, 2, 2);
        assertEquals(-1, bar());
        mouse(MouseEvent.MOUSE_RELEASED, 2, 2);
        assertEquals(ClassicAdvisorBox.Bar.DISMISSED, b.get());
        assertEquals(4, flips.size());
        assertFalse(edt(() -> this.layer.isBusy()));
    }

    /**
     * A box with an open delay (the option boxes: 2.5 frames after their
     * menu row) is due at once, so the layer is busy and takes the input,
     * but comes only when the delay has passed; a box without one comes at
     * once.
     */
    public void testAnOpenDelay() throws Exception {
        final ClassicAdvisorBox.Request r = ClassicAdvisorBox.Request.builder("opts")
            .freeColText("a a").rows("a", "a a")
            .checks(new boolean[] { true, false }, null)
            .openDelay(ClassicOptionBoxes.OPEN_DELAY_MS).build();
        final Answer a = ask(r);
        flush();
        assertFalse(up());
        assertTrue(edt(() -> this.layer.isBusy()));
        assertEquals(1, this.host.opened);
        runTimer();
        assertFalse(up());                             // not yet due
        this.clock.advanceMs(ClassicOptionBoxes.OPEN_DELAY_MS - 1.0);
        runTimer();
        assertFalse(up());
        this.clock.advanceMs(2.0);
        runTimer();
        assertTrue(up());
        assertEquals(0, bar());
        key(KeyEvent.VK_ESCAPE);
        assertEquals(ClassicAdvisorBox.Bar.DISMISSED, a.get());
        assertEquals(0.0, question("q").openDelayMs, 0.0);
    }

    /**
     * A box due at a set time (the landing box after its key, build spec
     * W8b): it comes then; a new portrait's palette goes in the lead
     * before it, or at once with what is left of the lead (at least a
     * frame); without a load it comes at the time.  A time already past
     * is no wait.
     */
    public void testABoxDueAtATime() throws Exception {
        assertTrue(edt(() -> this.layer.loadsPalette(ClassicAdvisorBox.Portrait.SCOUT)));
        // Due 100 ms on: the palette 42.8 ms before, the box at 100.
        final long t0 = this.clock.now();
        final ClassicAdvisorBox.Request r = ClassicAdvisorBox.Request.builder("land")
            .freeColText("a").rows("a", "a").portrait(ClassicAdvisorBox.Portrait.SCOUT)
            .showAt(t0 + 100_000_000L).build();
        final Answer a = ask(r);
        flush();
        runTimer();
        assertTrue(this.host.palettes.isEmpty());
        this.clock.advanceMs(100 - ClassicAdvisorLayer.PALETTE_LEAD_MS + 0.1);
        runTimer();
        assertEquals(List.of("MSS3.SS.000"), this.host.palettes);
        assertFalse(up());
        this.clock.advanceMs(ClassicAdvisorLayer.PALETTE_LEAD_MS - 0.2);
        runTimer();
        assertFalse(up());
        this.clock.advanceMs(0.2);
        runTimer();
        assertTrue(up());
        assertFalse(edt(() -> this.layer.loadsPalette(ClassicAdvisorBox.Portrait.SCOUT)));
        key(KeyEvent.VK_ENTER);
        assertEquals(0, a.get());

        // The same portrait, due 100 ms on: no load, the box at 100.
        this.clock.advanceMs(ClassicAdvisorLayer.CHAIN_MS);
        final long t1 = this.clock.now();
        final Answer b = ask(ClassicAdvisorBox.Request.builder("land")
            .freeColText("a").rows("a", "a").portrait(ClassicAdvisorBox.Portrait.SCOUT)
            .showAt(t1 + 100_000_000L).build());
        flush();
        this.clock.advanceMs(99.9);
        runTimer();
        assertFalse(up());
        this.clock.advanceMs(0.2);
        runTimer();
        assertTrue(up());
        assertEquals(1, this.host.palettes.size());
        key(KeyEvent.VK_ENTER);
        assertEquals(0, b.get());

        // A new portrait due 20 ms on: the load at once, the box at the
        // time; a time already past: at once.
        this.clock.advanceMs(ClassicAdvisorLayer.CHAIN_MS);
        final long t2 = this.clock.now();
        final Answer c = ask(ClassicAdvisorBox.Request.builder("land")
            .freeColText("a").rows("a", "a").portrait(ClassicAdvisorBox.Portrait.ADMIRAL)
            .showAt(t2 + 20_000_000L).build());
        flush();
        runTimer();
        assertEquals(2, this.host.palettes.size());
        assertFalse(up());
        this.clock.advanceMs(20.0);
        runTimer();
        assertTrue(up());
        key(KeyEvent.VK_ENTER);
        assertEquals(0, c.get());
        this.clock.advanceMs(ClassicAdvisorLayer.CHAIN_MS);
        final Answer d = ask(ClassicAdvisorBox.Request.builder("land")
            .freeColText("a").rows("a", "a").portrait(ClassicAdvisorBox.Portrait.ADMIRAL)
            .showAt(t2).build());
        flush();
        assertTrue(up());
        key(KeyEvent.VK_ENTER);
        assertEquals(0, d.get());
        assertEquals(14.27, ClassicAdvisorLayer.FRAME_MS, 0.01);
    }

    /**
     * A list box with an F1 hook (build spec D2, the father box D8a): F1
     * tells the hook the barred row and closes the box with HELP; Escape
     * does nothing there; a box without a hook takes F1 as any other key.
     * A full-screen page that comes 142 ms after its box (its own chain
     * time, not the usual 200) closes on any key, and the box asked again
     * 264 ms after the page comes then, its bar on row 1.
     */
    public void testF1AndThePage() throws Exception {
        final int[] told = { -1 };
        final ClassicAdvisorBox.Request box = ClassicAdvisorBox.Request.builder("f")
            .freeColText("a a").rows("a", "a a", "a a a").noEscape()
            .outsideCancels(false).help(row -> told[0] = row).build();
        final Answer a = ask(box);
        flush();
        assertTrue(up());
        key(KeyEvent.VK_ESCAPE);                       // nothing
        mouse(MouseEvent.MOUSE_PRESSED, 1, 1);         // nor a click beside it
        mouse(MouseEvent.MOUSE_RELEASED, 1, 1);
        assertTrue(up());
        key(KeyEvent.VK_DOWN);
        key(KeyEvent.VK_F1);
        assertEquals(ClassicAdvisorBox.Bar.HELP, a.get());
        assertEquals(1, told[0]);
        assertFalse(up());
        // The page: 142 ms after the box went.
        final java.awt.image.BufferedImage pic = new java.awt.image.BufferedImage(320,
            200, java.awt.image.BufferedImage.TYPE_INT_RGB);
        final Answer p = ask(ClassicAdvisorBox.Request.builder("page")
            .freeColText("x").picture(pic).chain(142.0).build());
        flush();
        assertFalse(up());
        this.clock.advanceMs(141);
        runTimer();
        assertFalse(up());
        this.clock.advanceMs(1);
        runTimer();
        assertTrue(up());
        assertEquals(new java.awt.Rectangle(0, 0, 320, 200),
                     edt(() -> this.layer.currentLayout()).box);
        key(KeyEvent.VK_F1);                           // any key closes it
        assertEquals(0, p.get());
        // The box again, 264 ms later, on row 1.
        final Answer b = ask(ClassicAdvisorBox.Request.builder("f")
            .freeColText("a a").rows("a", "a a").noEscape().chain(264.0).build());
        flush();
        this.clock.advanceMs(263);
        runTimer();
        assertFalse(up());
        this.clock.advanceMs(1);
        runTimer();
        assertTrue(up());
        assertEquals(0, bar());
        key(KeyEvent.VK_F1);                           // no hook: nothing
        assertTrue(up());
        key(KeyEvent.VK_ENTER);
        assertEquals(0, b.get());
    }
}
