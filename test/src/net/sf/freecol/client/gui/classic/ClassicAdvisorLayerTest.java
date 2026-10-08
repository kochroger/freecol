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

        /** The woodcut palette calls (k, 0 for back), and arrow changes. */
        final List<Integer> woodcutPalettes = new ArrayList<>();
        int arrowChanges = 0;

        @Override
        public void woodcutPalette(int k, int[] entries) {
            this.woodcutPalettes.add(k);
        }

        @Override
        public void arrowChanged() {
            this.arrowChanges++;
        }

        /** The map's last final draw + 57 ms, or 0. */
        long notBefore = 0L;

        @Override
        public long woodcutNotBefore() {
            return this.notBefore;
        }

        /** The woodcuts that ended after they were on the screen. */
        final List<Integer> ended = new ArrayList<>();

        @Override
        public void woodcutEnded(int k) {
            this.ended.add(k);
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

    /** A woodcut's end, when it comes. */
    private static final class Ended {

        volatile long value = 0L;
        final CountDownLatch done = new CountDownLatch(1);

        long get() throws InterruptedException {
            assertTrue("the woodcut never ended", done.await(5, TimeUnit.SECONDS));
            return this.value;
        }

        boolean isDone() {
            return this.done.getCount() == 0;
        }
    }

    /** Ask for woodcut 3 of the synthetic art on the EDT. */
    private Ended woodcut(long notBefore, double followMs) {
        final Ended e = new Ended();
        final ClassicWoodcut.Screen s = ClassicWoodcut.Screen.of(
            ClassicWoodcutTest.syntheticArt(), 3);
        SwingUtilities.invokeLater(() -> {
                e.value = this.layer.showWoodcut(s, null, notBefore, followMs);
                e.done.countDown();
            });
        return e;
    }

    private String probe() throws Exception {
        return edt(() -> this.layer.probe());
    }

    /** Run the woodcut's dissolve frame by frame; @return its frames. */
    private int dissolve() throws Exception {
        int frames = 0;
        for (int i = 0; i < 200 && !"woodcut_3:held".equals(probe()); i++) {
            final int before = edt(() -> this.layer.currentWoodcut().revealed());
            runTimer();
            if (edt(() -> this.layer.currentWoodcut().revealed()) > before) frames++;
            this.clock.advanceMs(ClassicWoodcut.FRAME_MS);
        }
        return frames;
    }

    private static ClassicAdvisorBox.Request question(String id) {
        return ClassicAdvisorBox.Request.builder(id).freeColText("a a a")
            .rows("a", "a a", "a a a").cancelRow(2).build();
    }

    /** @NOPORT's shape ({@link ClassicFounding#noPortRequest}) in the test font. */
    private static ClassicAdvisorBox.Request noPort() {
        return ClassicAdvisorBox.Request.builder(ClassicFounding.NOPORT_SECTION)
            .freeColText("a a a").rows("a", "a a").defaultRow(0).cancelRow(0)
            .portrait(ClassicAdvisorBox.Portrait.SCOUT).build();
    }

    /** Run the timer and the clock until the asked box is up (a palette lead). */
    private void showWhenDue() throws Exception {
        for (int i = 0; i < 100 && !up(); i++) {
            runTimer();
            if (!up()) this.clock.advanceMs(5);
        }
        assertTrue(up());
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

    /** A typed character, as the key's KEY_TYPED. */
    private void typed(char c) throws Exception {
        final long when = System.currentTimeMillis() + 5;
        edt(() -> {
                this.layer.onTyped(new KeyEvent(this.layer, KeyEvent.KEY_TYPED, when,
                    0, KeyEvent.VK_UNDEFINED, c));
                return null;
            });
    }

    private String fieldText() throws Exception {
        return edt(() -> this.layer.currentFieldText());
    }

    /**
     * W10: a name box on the layer.  Its key presses do not answer it
     * (letters, arrows, Space, F1), the typed characters go into the
     * field (the first one replaces the default), Backspace takes one
     * back, the box is redrawn; a character typed before the box was
     * drawn, a control character or one with Ctrl does nothing; a click
     * outside does nothing.  Enter answers 0 with the text taken, Escape
     * dismisses it with the default kept.  Typing in a box without a
     * field does nothing.
     */
    public void testANameField() throws Exception {
        final ClassicAdvisorBox.Request r = ClassicAdvisorBox.Request.builder("name")
            .freeColText("a a").rows("a").field("a a").build();
        final Answer a = ask(r);
        flush();
        assertTrue(up());
        assertEquals("name:-1", probe());
        assertEquals("a a", fieldText());
        key(KeyEvent.VK_A);
        key(KeyEvent.VK_DOWN);
        key(KeyEvent.VK_SPACE);
        key(KeyEvent.VK_F1);
        mouse(MouseEvent.MOUSE_PRESSED, 2, 2);
        mouse(MouseEvent.MOUSE_RELEASED, 2, 2);
        assertTrue(up());
        assertEquals("a a", fieldText());
        typed('a');
        assertEquals("a", fieldText());
        typed('\n');
        typed((char) 8);
        typed('*');
        assertEquals("a", fieldText());
        edt(() -> {
                this.layer.onTyped(new KeyEvent(this.layer, KeyEvent.KEY_TYPED,
                    System.currentTimeMillis() - 5000, 0, KeyEvent.VK_UNDEFINED, 'a'));
                this.layer.onTyped(new KeyEvent(this.layer, KeyEvent.KEY_TYPED,
                    System.currentTimeMillis() + 5, KeyEvent.CTRL_DOWN_MASK,
                    KeyEvent.VK_UNDEFINED, 'a'));
                return null;
            });
        assertEquals("a", fieldText());
        typed(' ');
        typed('a');
        typed('a');
        assertEquals("a aa", fieldText());
        key(KeyEvent.VK_BACK_SPACE);
        assertEquals("a a", fieldText());
        assertFalse(a.isDone());
        key(KeyEvent.VK_ENTER);
        assertEquals(0, a.get());
        assertEquals("a a", r.field.answer());
        assertTrue(r.field.taken());
        assertFalse(up());
        // Escape: the default, whatever was typed.
        this.clock.advanceMs(1000);
        final ClassicAdvisorBox.Request r2 = ClassicAdvisorBox.Request.builder("name2")
            .freeColText("a a").rows("a").field("aaa").build();
        final Answer b = ask(r2);
        flush();
        typed('a');
        assertEquals("a", fieldText());
        key(KeyEvent.VK_ESCAPE);
        assertEquals(ClassicAdvisorBox.Bar.DISMISSED, b.get());
        assertEquals("aaa", r2.field.answer());
        assertFalse(r2.field.taken());
        // A box without a field: typing does nothing, its key answers.
        this.clock.advanceMs(1000);
        final Answer c = ask(question("q"));
        flush();
        typed('a');
        assertNull(fieldText());
        assertTrue(up());
        key(KeyEvent.VK_ENTER);
        assertEquals(0, c.get());
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
     * G1: in the King's decision boxes Escape does nothing (Roger: "man
     * muss sich entscheiden"): the box and its bar stay, in the canvas;
     * Enter then takes the barred row.
     */
    public void testEscapeInTheKingsBox() throws Exception {
        final ClassicAdvisorBox.Request r = ClassicAdvisorBox.Request.builder("king")
            .freeColText("a a a").rows("a", "a a").cancelRow(1).noEscape()
            .portrait(ClassicAdvisorBox.Portrait.KING).outsideCancels(false).build();
        final Answer a = ask(r);
        flush();
        runTimer();                                    // the palette
        this.clock.advanceMs(ClassicAdvisorLayer.PALETTE_LEAD_MS);
        runTimer();
        assertTrue(up());
        key(KeyEvent.VK_ESCAPE);
        flush();
        assertFalse(a.isDone());
        assertTrue(up());
        assertEquals(0, bar());
        key(KeyEvent.VK_DOWN);
        key(KeyEvent.VK_ESCAPE);
        flush();
        assertFalse(a.isDone());
        assertTrue(up());
        assertEquals(1, bar());
        key(KeyEvent.VK_ENTER);
        assertEquals(1, a.get());
    }

    /**
     * D4, Roger's freeze report: a declined site warning (@NOPORT's first
     * row, Enter where the bar stands) answers its caller at once and
     * leaves the layer idle and hidden, so the map's keys go on (the turn
     * continues); the same box again, Escape: its first row too; again,
     * the second row; then the name box, Escape: dismissed, founds
     * nothing.  Every caller has its own answer, none before its key (G5's
     * nested-loop check, 1.5 s).
     */
    public void testADeclinedSiteWarningHoldsNothing() throws Exception {
        // B on a land-locked site: Enter on the bar's row, "Oh, daran
        // hatte ich nicht gedacht.": founds nothing.
        final Answer a = ask(noPort());
        flush();
        showWhenDue();
        assertEquals(0, bar());
        key(KeyEvent.VK_ENTER);
        assertEquals(0, a.get());
        assertFalse(ClassicFounding.foundsAnyway(0));
        assertFalse(up());
        assertFalse(edt(() -> this.layer.isBusy()));
        assertFalse(edt(() -> this.layer.isVisible()));   // the map's keys again
        assertEquals(1, this.host.idle);
        // B again: Escape, the same.
        this.clock.advanceMs(1000);
        final Answer b = ask(noPort());
        flush();
        showWhenDue();
        key(KeyEvent.VK_ESCAPE);
        assertEquals(0, b.get());
        assertEquals(2, this.host.idle);
        // B again: the second row founds; the name box after it, Escape.
        this.clock.advanceMs(1000);
        final Answer c = ask(noPort());
        flush();
        showWhenDue();
        key(KeyEvent.VK_DOWN);
        key(KeyEvent.VK_ENTER);
        assertEquals(1, c.get());
        assertTrue(ClassicFounding.foundsAnyway(1));
        final ClassicAdvisorBox.Request name = ClassicAdvisorBox.Request
            .builder(ClassicFounding.COLONY_SECTION).freeColText("a a").rows("a")
            .portrait(ClassicAdvisorBox.Portrait.COLONIST).field("a a").build();
        final Answer d = ask(name);
        flush();
        this.clock.advanceMs(ClassicAdvisorLayer.CHAIN_MS);
        showWhenDue();
        Thread.sleep(1500);                      // G5: no answer before its key
        assertFalse(d.isDone());
        key(KeyEvent.VK_ESCAPE);
        assertEquals(ClassicAdvisorBox.Bar.DISMISSED, d.get());
        assertNull(ClassicFounding.answered(name, d.get()));
        assertFalse(up());
        assertFalse(edt(() -> this.layer.isBusy()));
        assertEquals(4, this.host.idle);
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
        // G5: the JDK ends the second box's event loop a second after the
        // first one's exited; its caller still waits for its answer.
        Thread.sleep(1500);
        assertFalse(b.isDone());
        assertFalse(a.isDone());
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

    /**
     * W9: a woodcut's timeline on the clock: black at its time (the
     * palette in, busy from the ask), the frame 86 ms later, the dissolve
     * 43 ms after that over 55 frames (the arrow hidden meanwhile), then
     * held; keys before the end of the dissolve and held keys do nothing;
     * a fresh key: black, the palette back one frame later, the map 300 ms
     * after the black; the next box no earlier than the follow-up.
     */
    public void testWoodcutTimeline() throws Exception {
        final long t0 = this.clock.now();
        final Ended e = woodcut(t0 + 57_000_000L, 171.0);
        flush();
        assertTrue(edt(() -> this.layer.isBusy()));    // due at once
        assertEquals("due", probe());
        assertEquals(1, this.host.opened);
        this.clock.advanceMs(56);
        runTimer();
        assertEquals("due", probe());
        this.clock.advanceMs(1);
        runTimer();
        assertEquals("woodcut_3:black", probe());
        assertTrue(edt(() -> this.layer.coversScreen()));
        assertEquals(List.of(3), this.host.woodcutPalettes);
        for (int p : edt(() -> this.layer.currentWoodcut().pixels())) assertEquals(0, p);
        key(KeyEvent.VK_SPACE);                        // not yet: nothing
        this.clock.advanceMs(85);
        runTimer();
        assertEquals("woodcut_3:black", probe());
        this.clock.advanceMs(1);
        runTimer();
        assertEquals("woodcut_3:frame", probe());
        assertFalse(edt(() -> this.layer.hidesArrow()));
        key(KeyEvent.VK_ENTER);
        this.clock.advanceMs(43);
        final int arrows = this.host.arrowChanges;
        final int frames = dissolve();
        assertTrue("dissolve frames " + frames, Math.abs(frames - 55) <= 2);
        assertEquals(arrows + 2, this.host.arrowChanges);   // hidden, then back
        assertFalse(edt(() -> this.layer.hidesArrow()));
        assertEquals("woodcut_3:held", probe());
        assertTrue(edt(() -> this.layer.currentWoodcut().complete()));
        // Held keys and keys of before the picture was complete: nothing.
        this.host.repeat = true;
        key(KeyEvent.VK_ESCAPE);
        this.host.repeat = false;
        edt(() -> {
                this.layer.onKey(new KeyEvent(this.layer, KeyEvent.KEY_PRESSED,
                    System.currentTimeMillis() - 5000, 0, KeyEvent.VK_A,
                    KeyEvent.CHAR_UNDEFINED));
                return null;
            });
        key(KeyEvent.VK_SHIFT);
        this.clock.advanceMs(60_000);                  // no timeout
        runTimer();
        assertEquals("woodcut_3:held", probe());
        assertFalse(e.isDone());
        // Escape ends it (a woodcut has no "Nein").
        key(KeyEvent.VK_ESCAPE);
        final long closed = this.clock.now();
        assertEquals("woodcut_3:closing", probe());
        for (int p : edt(() -> this.layer.currentWoodcut().pixels())) assertEquals(0, p);
        assertEquals(List.of(3), this.host.woodcutPalettes);
        this.clock.advanceMs(ClassicWoodcut.FRAME_MS);
        runTimer();
        assertEquals(List.of(3, 0), this.host.woodcutPalettes);
        assertTrue(up());
        this.clock.advanceMs(300 - ClassicWoodcut.FRAME_MS - 0.5);
        runTimer();
        assertTrue(up());
        this.clock.advanceMs(0.5);
        runTimer();
        assertEquals(closed + 300_000_000L, e.get());
        assertEquals(List.of(3), this.host.ended);
        assertFalse(up());
        assertFalse(edt(() -> this.layer.isBusy()));
        assertFalse(edt(() -> this.layer.coversScreen()));
        assertEquals(1, this.host.closed);
        assertEquals(1, this.host.idle);
        assertEquals(e.get() + 171_000_000L, (long) edt(() -> this.layer.holdUntilNanos()));
        // The next box (a new portrait): its palette in the lead, the box
        // at the follow-up time.
        final ClassicAdvisorBox.Request chief = ClassicAdvisorBox.Request.builder("chief")
            .freeColText("a a").rows("a", "a a").cancelRow(1)
            .portrait(ClassicAdvisorBox.Portrait.chief(6)).build();
        final Answer a = ask(chief);
        flush();
        this.clock.advanceMs(171 - ClassicAdvisorLayer.PALETTE_LEAD_MS - 1);
        runTimer();
        assertTrue(this.host.palettes.isEmpty());
        this.clock.advanceMs(1);
        runTimer();
        assertEquals(1, this.host.palettes.size());
        assertFalse(up());
        this.clock.advanceMs(ClassicAdvisorLayer.PALETTE_LEAD_MS);
        runTimer();
        assertTrue(up());
        key(KeyEvent.VK_ENTER);
        assertEquals(0, a.get());
    }

    /**
     * W9: a woodcut asked while a box is up waits for it (200 ms after its
     * close), a box asked during a woodcut waits for the woodcut; a click
     * (press and release on the canvas) ends a complete woodcut.  Each
     * caller resumes only when its own box or woodcut is done, as with
     * stacked dialogs, also after the JDK ended its nested event loop (a
     * second after the one below it exited).
     */
    public void testWoodcutsAndBoxesWait() throws Exception {
        final Answer a = ask(question("q"));
        flush();
        assertTrue(up());
        final Ended e = woodcut(0L, 0.0);
        flush();
        assertEquals("q:0", probe());                  // the box stays first
        key(KeyEvent.VK_ENTER);
        Thread.sleep(1500);                            // the JDK ends a nested
        assertFalse(e.isDone());                       // loop a second after
        assertEquals("due", probe());                  // the one below it
        assertFalse(a.isDone());                       // stacked: the woodcut first
        this.clock.advanceMs(ClassicAdvisorLayer.CHAIN_MS - 1);
        runTimer();
        assertEquals("due", probe());
        this.clock.advanceMs(1);
        runTimer();
        assertEquals("woodcut_3:black", probe());
        final Answer b = ask(question("q2"));
        flush();
        this.clock.advanceMs(ClassicWoodcut.FRAME_AFTER_BLACK_MS);
        runTimer();
        this.clock.advanceMs(ClassicWoodcut.DISSOLVE_AFTER_FRAME_MS);
        dissolve();
        assertFalse(b.isDone());
        mouse(MouseEvent.MOUSE_PRESSED, 5, 5);
        assertEquals("woodcut_3:held", probe());
        mouse(MouseEvent.MOUSE_RELEASED, 100, 100);
        assertEquals("woodcut_3:closing", probe());
        this.clock.advanceMs(ClassicWoodcut.FRAME_MS);
        runTimer();
        this.clock.advanceMs(ClassicWoodcut.MAP_BACK_MS);
        runTimer();
        assertTrue(up());                              // the box at once (follow 0)
        assertEquals("q2:0", probe());
        Thread.sleep(1500);                            // stacked: no early return
        assertFalse(e.isDone());
        assertFalse(b.isDone());
        key(KeyEvent.VK_ESCAPE);
        assertEquals(2, b.get());
        assertTrue(e.get() != ClassicAdvisorLayer.NOT_SHOWN);
        assertEquals(0, a.get());
    }

    /**
     * I1 (Roger: any click closes a woodcut; I-prep cycle.md 3C): in a
     * window wider than the canvas, a press and release on the letterbox
     * border close a complete woodcut; the same click during the dissolve
     * does nothing.
     */
    public void testWoodcutClosesOnTheLetterbox() throws Exception {
        edt(() -> {
                this.layer.setSize(640, 480);   // scale 2: canvas y 40..439
                return null;
            });
        final Ended e = woodcut(0L, 0.0);
        flush();
        assertEquals("woodcut_3:black", probe());
        this.clock.advanceMs(ClassicWoodcut.FRAME_AFTER_BLACK_MS);
        runTimer();
        this.clock.advanceMs(ClassicWoodcut.DISSOLVE_AFTER_FRAME_MS);
        runTimer();
        mouse(MouseEvent.MOUSE_PRESSED, 320, 10);
        mouse(MouseEvent.MOUSE_RELEASED, 320, 10);
        assertFalse(probe(), "woodcut_3:closing".equals(probe()));
        dissolve();
        assertEquals("woodcut_3:held", probe());
        mouse(MouseEvent.MOUSE_PRESSED, 320, 10);      // the top border
        assertEquals("woodcut_3:held", probe());
        mouse(MouseEvent.MOUSE_RELEASED, 320, 470);    // the bottom border
        assertEquals("woodcut_3:closing", probe());
        this.clock.advanceMs(ClassicWoodcut.FRAME_MS);
        runTimer();
        this.clock.advanceMs(ClassicWoodcut.MAP_BACK_MS);
        runTimer();
        assertTrue(e.get() != ClassicAdvisorLayer.NOT_SHOWN);
        assertEquals(List.of(3), this.host.ended);
    }

    /**
     * W9: a woodcut due while the map still paints (slides queued before
     * it, the AI phase) comes 57 ms after the map's last final draw, as
     * after its trigger.
     */
    public void testWoodcutAfterTheLastFinalDraw() throws Exception {
        final long t0 = this.clock.now();
        this.host.notBefore = t0 + 57_000_000L;
        final Ended e = woodcut(0L, 0.0);
        flush();
        assertEquals("due", probe());
        this.clock.advanceMs(56);
        runTimer();
        assertEquals("due", probe());
        this.host.notBefore = t0 + 100_000_000L;      // another slide ended meanwhile
        this.clock.advanceMs(1);
        runTimer();
        assertEquals("due", probe());
        this.clock.advanceMs(43);
        runTimer();
        assertEquals("woodcut_3:black", probe());
        edt(() -> {
                this.layer.abort();
                return null;
            });
        assertTrue(e.get() != ClassicAdvisorLayer.NOT_SHOWN);
    }

    /**
     * W9: the game view goes during a woodcut: it ends at once, counts as
     * shown and its palette goes back; a woodcut that never reached the
     * screen is not shown.
     */
    public void testWoodcutAbort() throws Exception {
        final Ended e = woodcut(0L, 0.0);
        flush();
        assertEquals("woodcut_3:black", probe());
        final Ended later = woodcut(0L, 0.0);
        flush();
        this.clock.advanceMs(ClassicWoodcut.FRAME_AFTER_BLACK_MS
                             + ClassicWoodcut.DISSOLVE_AFTER_FRAME_MS);
        runTimer();
        runTimer();
        edt(() -> {
                this.layer.abort();
                return null;
            });
        assertTrue(e.get() != ClassicAdvisorLayer.NOT_SHOWN);
        assertEquals(ClassicAdvisorLayer.NOT_SHOWN, later.get());
        assertEquals(List.of(3), this.host.ended);              // the drawn one only
        assertEquals(List.of(3, 0), this.host.woodcutPalettes);
        assertFalse(edt(() -> this.layer.isBusy()));
        assertFalse(edt(() -> this.layer.hidesArrow()));
        assertEquals(ClassicAdvisorLayer.NOT_SHOWN, (long) edt(
            () -> this.layer.showWoodcut(null, null, 0L, 0.0)));
    }

    /** A synthetic congress hall: grey, a 10x10 red figure at (100,100), a blue page. */
    private static ClassicWoodcut.Screen hall() {
        final BufferedImage from = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
        final BufferedImage to = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
        final BufferedImage page = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 200; y++) {
            for (int x = 0; x < 320; x++) {
                from.setRGB(x, y, 0x808080);
                to.setRGB(x, y, (x >= 100 && x < 110 && y >= 100 && y < 110)
                          ? 0xFF0000 : 0x808080);
                page.setRGB(x, y, 0x0000FF);
            }
        }
        return new ClassicWoodcut.Screen(ClassicCongress.HALL, false, "congress_7",
            from, to, page, ClassicCongress.HALL_AFTER_BLACK_MS,
            ClassicCongress.DISSOLVE_AFTER_HALL_MS, ClassicCongress.PAGE_AFTER_BLACK_MS,
            ClassicCongress.BLACK_AFTER_BOX_MS, new java.awt.Rectangle(100, 100, 10, 10));
    }

    /**
     * D8c: the congress hall on the clock (clip008 #39714 - #40109): its
     * black two frames after the @FREEDOM box went (not the usual 200
     * ms), the hall one frame later with the arrow hidden from there, the
     * figure's dissolve three frames after the hall over 55 frames, held
     * until a fresh key; then black, the palette back a frame later, the
     * page 15 frames (0.21 s) after the black in the same entry, which a
     * click ends with the map at once; never marked as a woodcut; the next
     * box no earlier than the follow-up.
     */
    public void testCongressHallTimeline() throws Exception {
        final Answer box = ask(ClassicAdvisorBox.Request.builder("FREEDOM")
            .freeColText("a a").build());
        flush();
        assertTrue(up());
        key(KeyEvent.VK_ENTER);
        assertEquals(0, box.get());
        final long closed = this.clock.now();
        final Ended e = new Ended();
        final ClassicWoodcut.Screen s = hall();
        SwingUtilities.invokeLater(() -> {
                e.value = this.layer.showWoodcut(s, null, 0L, ClassicCongress.FOLLOW_MS);
                e.done.countDown();
            });
        flush();
        assertEquals("due", probe());
        this.clock.advanceMs(2 * ClassicWoodcut.FRAME_MS - 0.5);
        runTimer();
        assertEquals("due", probe());
        this.clock.advanceMs(0.5);
        runTimer();
        assertEquals("congress_7:black", probe());
        assertEquals(List.of(ClassicCongress.HALL), this.host.woodcutPalettes);
        assertFalse(edt(() -> this.layer.hidesArrow()));
        this.clock.advanceMs(ClassicWoodcut.FRAME_MS);
        runTimer();
        assertEquals("congress_7:frame", probe());
        assertTrue(edt(() -> this.layer.hidesArrow()));       // from the hall on
        key(KeyEvent.VK_ENTER);                                // not yet: nothing
        this.clock.advanceMs(3 * ClassicWoodcut.FRAME_MS - 0.5);
        runTimer();
        assertEquals("congress_7:frame", probe());
        this.clock.advanceMs(0.5);
        int frames = 0;
        for (int i = 0; i < 200 && !"congress_7:held".equals(probe()); i++) {
            final int before = edt(() -> this.layer.currentWoodcut().revealed());
            runTimer();
            if (edt(() -> this.layer.currentWoodcut().revealed()) > before) frames++;
            assertTrue(edt(() -> this.layer.hidesArrow())
                       || "congress_7:held".equals(probe()));
            this.clock.advanceMs(ClassicWoodcut.FRAME_MS);
        }
        assertEquals(55, frames);
        assertEquals("congress_7:held", probe());
        assertFalse(edt(() -> this.layer.hidesArrow()));
        assertEquals(100, s.changed());
        // A fresh key: black, the palette back a frame later, the page
        // 15 frames after the black.
        key(KeyEvent.VK_SPACE);
        final long black = this.clock.now();
        assertEquals("congress_7:closing", probe());
        for (int p : edt(() -> this.layer.currentWoodcut().pixels())) assertEquals(0, p);
        this.clock.advanceMs(ClassicWoodcut.FRAME_MS);
        runTimer();
        assertEquals(List.of(ClassicCongress.HALL, 0), this.host.woodcutPalettes);
        this.clock.advanceMs(14 * ClassicWoodcut.FRAME_MS - 0.5);
        runTimer();
        assertEquals("congress_7:closing", probe());
        this.clock.advanceMs(0.5);
        runTimer();
        assertEquals("congress_7:page", probe());
        assertTrue(edt(() -> this.layer.coversScreen()));
        for (int p : edt(() -> this.layer.currentWoodcut().pixels())) assertEquals(0x0000FF, p);
        assertEquals(black + Math.round(15 * ClassicWoodcut.FRAME_MS * 1e6),
                     this.clock.now(), 1_000_000L);
        // Held keys do nothing; no timeout; a click ends it with the map.
        this.host.repeat = true;
        key(KeyEvent.VK_ENTER);
        this.host.repeat = false;
        this.clock.advanceMs(60_000);
        runTimer();
        assertEquals("congress_7:page", probe());
        assertFalse(e.isDone());
        mouse(MouseEvent.MOUSE_PRESSED, 10, 10);
        mouse(MouseEvent.MOUSE_RELEASED, 10, 10);
        assertEquals(this.clock.now(), e.get());
        assertFalse(up());
        assertFalse(edt(() -> this.layer.coversScreen()));
        assertTrue("never marked: " + this.host.ended, this.host.ended.isEmpty());
        assertEquals(e.get() + Math.round(ClassicCongress.FOLLOW_MS * 1e6),
                     (long) edt(() -> this.layer.holdUntilNanos()));
        assertTrue(closed < black);
        // A second hall: a fresh key ends its page too.
        final Ended e2 = new EndedHall(hall()).ask();
        flush();
        for (int i = 0; i < 400 && !"congress_7:held".equals(probe()); i++) {
            this.clock.advanceMs(ClassicWoodcut.FRAME_MS);
            runTimer();
        }
        assertEquals("congress_7:held", probe());
        key(KeyEvent.VK_ENTER);
        for (int i = 0; i < 40 && !"congress_7:page".equals(probe()); i++) {
            this.clock.advanceMs(ClassicWoodcut.FRAME_MS);
            runTimer();
        }
        assertEquals("congress_7:page", probe());
        key(KeyEvent.VK_A);
        assertEquals(this.clock.now(), e2.get());
        assertFalse(up());
    }

    /** A hall asked on the EDT. */
    private final class EndedHall {

        final ClassicWoodcut.Screen s;

        EndedHall(ClassicWoodcut.Screen s) {
            this.s = s;
        }

        Ended ask() {
            final Ended e = new Ended();
            SwingUtilities.invokeLater(() -> {
                    e.value = layer.showWoodcut(this.s, null, 0L, 0.0);
                    e.done.countDown();
                });
            return e;
        }
    }
}
