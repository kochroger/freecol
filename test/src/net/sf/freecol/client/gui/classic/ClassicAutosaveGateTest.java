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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.swing.SwingUtilities;

import junit.framework.TestCase;


/**
 * Part Z1, C1 of part Z's check: the gate the save, the load and every
 * server stop wait at while the turn start's autosave is written
 * ({@link ClassicAutosaveGate}).  The autosave is held open on a thread of
 * its own, as FreeCol's network thread writes it; the waits run on another
 * thread and on the event thread, whose secondary loop keeps handling
 * events (no frozen screen, no lost input).
 */
public class ClassicAutosaveGateTest extends TestCase {

    /** An autosave held open on its own thread until released. */
    private static final class Autosave {

        final ClassicAutosaveGate gate;
        final CountDownLatch begun = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicLong endedAt = new AtomicLong(0L);
        final Thread thread;

        Autosave(ClassicAutosaveGate gate) {
            this.gate = gate;
            this.thread = new Thread(() -> {
                    this.gate.begin();
                    try {
                        this.begun.countDown();
                        this.release.await(60, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        this.endedAt.set(System.nanoTime());
                        this.gate.end();
                    }
                }, "test autosave");
            this.thread.start();
        }

        void awaitBegun() throws InterruptedException {
            assertTrue(this.begun.await(30, TimeUnit.SECONDS));
        }

        void finish() throws InterruptedException {
            this.release.countDown();
            this.thread.join(30000L);
            assertFalse(this.thread.isAlive());
        }
    }

    /** Runs on the event thread later; the test thread is never the EDT. */
    private static void later(Runnable r) {
        SwingUtilities.invokeLater(r);
    }

    /** Begun twice, ended twice: idle only after the second end; an extra end harms nothing. */
    public void testTheAutosavesNest() {
        final ClassicAutosaveGate g = new ClassicAutosaveGate();
        assertFalse(g.running());
        g.begin();
        g.begin();
        assertTrue(g.running());
        g.end();
        assertTrue(g.running());
        g.end();
        assertFalse(g.running());
        g.end();
        assertFalse(g.running());
        g.begin();
        assertTrue(g.running());
        g.end();
        assertFalse(g.running());
        assertTrue(g.awaitIdle(0L));
    }

    /**
     * Another thread waits until the autosave has ended (not before), and
     * then sees the gate idle.
     */
    public void testAThreadWaitsForTheAutosave() throws Exception {
        final ClassicAutosaveGate g = new ClassicAutosaveGate();
        final Autosave a = new Autosave(g);
        a.awaitBegun();
        assertTrue(g.running());
        assertTrue(g.blocking());
        final AtomicLong returnedAt = new AtomicLong(0L);
        final AtomicBoolean idle = new AtomicBoolean(false);
        final Thread w = new Thread(() -> {
                idle.set(g.awaitIdle(30000L));
                returnedAt.set(System.nanoTime());
            }, "test waiter");
        w.start();
        Thread.sleep(300L);
        assertTrue(w.isAlive());
        assertEquals(0L, returnedAt.get());
        a.finish();
        w.join(30000L);
        assertTrue(idle.get());
        assertTrue(returnedAt.get() >= a.endedAt.get());
        assertFalse(g.running());
        assertFalse(g.blocking());
    }

    /**
     * An autosave that hangs: the wait gives up after its bound, on another
     * thread and on the event thread, and says so.
     */
    public void testTheWaitIsBounded() throws Exception {
        final ClassicAutosaveGate g = new ClassicAutosaveGate();
        final Autosave a = new Autosave(g);
        a.awaitBegun();
        try {
            long t0 = System.nanoTime();
            assertFalse(g.awaitIdle(250L));
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            assertTrue("waited " + ms + " ms", ms >= 200L && ms < 10000L);
            final AtomicBoolean idle = new AtomicBoolean(true);
            final AtomicLong waited = new AtomicLong(-1L);
            SwingUtilities.invokeAndWait(() -> {
                    final long s = System.nanoTime();
                    idle.set(g.awaitIdle(250L));
                    waited.set((System.nanoTime() - s) / 1_000_000L);
                });
            assertFalse(idle.get());
            assertTrue("waited " + waited.get() + " ms",
                       waited.get() >= 200L && waited.get() < 10000L);
            assertTrue(g.running());
        } finally {
            a.finish();
        }
    }

    /** The thread that writes the autosave never waits for itself. */
    public void testTheWriterNeverWaitsForItself() throws Exception {
        final ClassicAutosaveGate g = new ClassicAutosaveGate();
        final AtomicBoolean idle = new AtomicBoolean(false);
        final AtomicBoolean blocks = new AtomicBoolean(true);
        final AtomicLong ms = new AtomicLong(-1L);
        final Thread t = new Thread(() -> {
                g.begin();
                try {
                    blocks.set(g.blocking() || !g.running());
                    final long s = System.nanoTime();
                    idle.set(g.awaitIdle(20000L));
                    ms.set((System.nanoTime() - s) / 1_000_000L);
                } finally {
                    g.end();
                }
            }, "test writer");
        t.start();
        t.join(30000L);
        assertFalse("the writer would wait for itself", blocks.get());
        assertTrue(idle.get());
        assertTrue("waited " + ms.get() + " ms", ms.get() >= 0L && ms.get() < 2000L);
        assertFalse(g.running());
    }

    /**
     * On the event thread the wait is a secondary event loop: events posted
     * meanwhile are handled before the autosave ends (the screen is drawn,
     * keys and clicks come through); a second wait nested in the first
     * also ends with the autosave; both answer idle only after the end.
     */
    public void testTheEventThreadIsNotFrozen() throws Exception {
        final ClassicAutosaveGate g = new ClassicAutosaveGate();
        final Autosave a = new Autosave(g);
        a.awaitBegun();
        final List<String> order = Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch outerIn = new CountDownLatch(1);
        final CountDownLatch innerIn = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(2);
        final AtomicBoolean outerIdle = new AtomicBoolean(false);
        final AtomicBoolean innerIdle = new AtomicBoolean(false);
        final AtomicLong innerOnAt = new AtomicLong(0L);
        later(() -> {
                order.add("outer waits");
                outerIn.countDown();
                outerIdle.set(g.awaitIdle(30000L));
                order.add("outer goes on");
                done.countDown();
            });
        assertTrue(outerIn.await(30, TimeUnit.SECONDS));
        // An event while the outer wait runs: handled at once.
        final CountDownLatch ping = new CountDownLatch(1);
        later(() -> {
                order.add("ping");
                ping.countDown();
            });
        assertTrue("the event thread is frozen", ping.await(10, TimeUnit.SECONDS));
        later(() -> {
                order.add("inner waits");
                innerIn.countDown();
                innerIdle.set(g.awaitIdle(30000L));
                innerOnAt.set(System.nanoTime());
                order.add("inner goes on");
                done.countDown();
            });
        assertTrue(innerIn.await(30, TimeUnit.SECONDS));
        final CountDownLatch ping2 = new CountDownLatch(1);
        later(ping2::countDown);
        assertTrue("the event thread is frozen", ping2.await(10, TimeUnit.SECONDS));
        Thread.sleep(200L);
        assertEquals("neither went on", 2L, done.getCount());
        // One more event just before the end: the JDK's secondary loop
        // would only return by itself about a second after the last event,
        // the gate's end wakes it at once.
        final CountDownLatch ping3 = new CountDownLatch(1);
        later(ping3::countDown);
        assertTrue("the event thread is frozen", ping3.await(10, TimeUnit.SECONDS));
        order.add("autosave ends");
        a.finish();
        assertTrue(done.await(30, TimeUnit.SECONDS));
        assertTrue(outerIdle.get());
        assertTrue(innerIdle.get());
        final long wakeMs = (innerOnAt.get() - a.endedAt.get()) / 1_000_000L;
        assertTrue("woken " + wakeMs + " ms after the end", wakeMs >= 0L && wakeMs < 500L);
        final List<String> got = new ArrayList<>(order);
        assertEquals(got.toString(), "outer waits", got.get(0));
        assertTrue(got.toString(), got.indexOf("ping") < got.indexOf("autosave ends"));
        assertTrue(got.toString(), got.indexOf("autosave ends") < got.indexOf("inner goes on"));
        assertTrue(got.toString(), got.indexOf("autosave ends") < got.indexOf("outer goes on"));
        assertFalse(g.running());
        // Idle: no wait at all, also on the event thread.
        final AtomicBoolean now = new AtomicBoolean(false);
        SwingUtilities.invokeAndWait(() -> now.set(g.awaitIdle(30000L)));
        assertTrue(now.get());
    }
}
