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

import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * The original's blink clock (build spec W3): one clock with a half-period
 * of {@link #HALF_PERIOD_MS} (20 game ticks), 50 % duty, ON first.
 *
 * <p>{@link #arm} starts a phase: the state is ON, and toggle {@code n}
 * (n = 1, 2, ...) is due at {@code t0 + n * H} on the absolute clock, OFF
 * for odd {@code n} and ON for even {@code n} ({@link #isOff}).  Arming
 * again <em>resets</em> the phase (landfall #7703 / #8163 / #8187: the
 * counter restarts at the dialog's close, it is not paused), and
 * {@link #stop} ends it.
 *
 * <p>The original measures its first OFF from the panel refresh that
 * follows a (re)activation (57 episodes: first OFF one half-period after
 * it).  So a phase is re-based on every panel paint that comes within
 * {@link #PANEL_WINDOW_MS} of the arm ({@link #panelPainted}); without one
 * it stays at the arm.
 *
 * <p>A daemon thread waits for each deadline (the recorder's
 * {@code Thread.sleep} plus spin: {@code LockSupport.parkNanos} wakes up to
 * 15 ms late on Windows, W1) and hands the toggle to the poster, the event
 * thread in the game.  Every arm, re-base and stop starts a new
 * generation, and a toggle of an older one is dropped when it arrives
 * ({@link #isCurrent}), so a toggle queued behind a slide or a dialog can
 * never land in the new phase.  What a toggle does -- and whether it is
 * held because a slide, a dialog or the AI phase is on -- is the
 * listener's business ({@code ClassicMapViewer}).
 *
 * <p>The schedule ({@link #dueNanos}, {@link #nextIndex}, {@link #isOff})
 * and the phase bookkeeping run without the thread, on an injectable clock,
 * for the tests.
 */
final class ClassicBlink {

    private static final Logger logger = Logger.getLogger(ClassicBlink.class.getName());

    /** The half-period: 20 game ticks of 16.43 ms (landfall: 328.54 ms over 187 intervals). */
    static final double HALF_PERIOD_MS = 328.5;

    /**
     * How long after an arm a panel paint still counts as the arm's panel
     * refresh (the original's refresh comes 1-3 frames after the final
     * draw, ours 0-3).
     */
    static final double PANEL_WINDOW_MS = 100.0;

    /** Nanoseconds per millisecond. */
    private static final double NS_PER_MS = 1_000_000.0;

    /** What a toggle is handed to (the event thread). */
    interface Listener {

        /**
         * Toggle {@code n} of the current phase is due: OFF for odd
         * {@code n}, ON for even {@code n}.
         *
         * @param n The toggle, 1, 2, ...
         */
        void toggle(int n);
    }


    private final ClassicSlide.Clock clock;

    /** Runs a toggle on the listener's thread ({@code SwingUtilities::invokeLater}). */
    private final Consumer<Runnable> poster;

    private final Listener listener;

    /** Whether a thread of its own waits for the toggles (else the caller does, tests). */
    private final boolean threaded;

    /** The phase: toggle n is due at {@code t0 + n * H}. */
    private long t0 = 0L;

    /** When the phase was armed (the panel window counts from here). */
    private long armedAt = 0L;

    /** Whether a phase runs. */
    private boolean armed = false;

    /** Whether a panel paint may still re-base the phase. */
    private boolean awaitPanel = false;

    /** The phase's generation; a toggle of another one is dropped. */
    private int generation = 0;

    /** The last toggle handed to the poster in this generation, else 0. */
    private int lastPosted = 0;

    /** Set by {@link #close}: the thread ends. */
    private boolean closed = false;

    /** The waiting thread, started on the first arm. */
    private Thread thread = null;


    /**
     * @param clock The clock to schedule against.
     * @param poster Runs each toggle (in the game: on the event thread).
     * @param listener Is told each toggle of the current phase.
     * @param threaded Start a thread of its own on the first arm, which
     *     waits for every due toggle and posts it; false leaves that to
     *     the caller ({@link #next}, {@link #due}; tests).
     */
    ClassicBlink(ClassicSlide.Clock clock, Consumer<Runnable> poster,
                 Listener listener, boolean threaded) {
        this.clock = clock;
        this.poster = poster;
        this.listener = listener;
        this.threaded = threaded;
    }


    // The schedule

    /**
     * When toggle {@code n} of a phase is due.
     *
     * @param t0 The phase's start.
     * @param n The toggle, 1, 2, ...
     * @return {@code t0 + n * H}, rounded to the nanosecond (no drift).
     */
    static long dueNanos(long t0, int n) {
        return t0 + Math.round(n * HALF_PERIOD_MS * NS_PER_MS);
    }

    /**
     * The next toggle to wait for: the first one due after {@code now},
     * and never one already handed on.
     *
     * @param t0 The phase's start.
     * @param now The time now.
     * @param last The last toggle handed on, 0 for none.
     * @return The toggle number, at least {@code last + 1}.
     */
    static int nextIndex(long t0, long now, int last) {
        int n = last + 1;
        if (now > t0) {
            final long k = (long) Math.floor((now - t0) / (HALF_PERIOD_MS * NS_PER_MS)) + 1;
            if (k > n) n = (int) Math.min(Integer.MAX_VALUE, k);
        }
        return n;
    }

    /**
     * The state after toggle {@code n}.
     *
     * @param n The toggle, 0 for the start of the phase.
     * @return True for OFF (odd {@code n}).
     */
    static boolean isOff(int n) {
        return (n & 1) == 1;
    }


    // The phase (any thread; the game calls these on the event thread)

    /**
     * Start a new phase now: ON, first OFF one half-period from now (or
     * from the panel refresh that follows within {@link #PANEL_WINDOW_MS}).
     */
    synchronized void arm() {
        if (this.closed) return;
        final long now = this.clock.now();
        this.t0 = now;
        this.armedAt = now;
        this.armed = true;
        this.awaitPanel = true;
        newGeneration();
        startThread();
    }

    /**
     * Start a new phase whose ON comes {@code delayMs} from now, as toggle
     * 2 (ON), then OFF one half-period after it, and so on: the
     * Spielzugende mode's restart one frame after a box closes (build spec
     * W17, landing-slow #5320 -&gt; #5321).  No panel re-basing.
     *
     * @param delayMs The delay of the ON, less than a half-period.
     */
    synchronized void armDelayed(double delayMs) {
        if (this.closed) return;
        final long now = this.clock.now();
        this.t0 = now + Math.round(delayMs * NS_PER_MS)
            - Math.round(2 * HALF_PERIOD_MS * NS_PER_MS);
        this.armedAt = now;
        this.armed = true;
        this.awaitPanel = false;
        newGeneration();
        this.lastPosted = 1;   // toggle 1 (OFF) lies before now: never posted
        startThread();
    }

    /**
     * The panel was painted: if this is the refresh of the current arm,
     * the phase starts now instead.
     *
     * @return True if the phase was re-based.
     */
    synchronized boolean panelPainted() {
        if (!this.armed || !this.awaitPanel) return false;
        final long now = this.clock.now();
        if (now - this.armedAt > Math.round(PANEL_WINDOW_MS * NS_PER_MS)) {
            this.awaitPanel = false;
            return false;
        }
        this.t0 = now;
        newGeneration();
        return true;
    }

    /** End the phase: no more toggles until the next {@link #arm}. */
    synchronized void stop() {
        if (!this.armed) return;
        this.armed = false;
        this.awaitPanel = false;
        newGeneration();
    }

    /** End the phase and the thread for good. */
    void close() {
        final Thread t;
        synchronized (this) {
            this.closed = true;
            this.armed = false;
            newGeneration();
            t = this.thread;
            this.thread = null;
        }
        if (t != null) t.interrupt();
    }

    /** @return Whether a phase runs. */
    synchronized boolean isArmed() {
        return this.armed;
    }

    /** @return The current phase's start ({@code System.nanoTime} units). */
    synchronized long phaseStart() {
        return this.t0;
    }

    /** @return The current generation. */
    synchronized int generation() {
        return this.generation;
    }

    /**
     * Whether a toggle of generation {@code g} still belongs to the
     * running phase.
     *
     * @param g The toggle's generation.
     * @return True if it does.
     */
    synchronized boolean isCurrent(int g) {
        return this.armed && g == this.generation;
    }

    /**
     * The next toggle to wait for: the first one due after now that has
     * not been handed on yet.  No side effect ({@link #due} claims it).
     *
     * @return {generation, n, due}, or null without a phase.
     */
    synchronized long[] next() {
        if (!this.armed) return null;
        final int n = nextIndex(this.t0, this.clock.now(), this.lastPosted);
        return new long[] { this.generation, n, dueNanos(this.t0, n) };
    }

    /**
     * Toggle {@code n} of generation {@code g} has come due: claim it and
     * hand it to the listener through the poster (where it is dropped if
     * its generation has gone by then).
     *
     * @param g The toggle's generation.
     * @param n The toggle.
     * @return False if the phase has changed or the toggle was handed on
     *     already (nothing is posted).
     */
    boolean due(int g, int n) {
        synchronized (this) {
            if (!this.armed || g != this.generation || n <= this.lastPosted) {
                return false;
            }
            this.lastPosted = n;
        }
        this.poster.accept(() -> {
                if (isCurrent(g)) this.listener.toggle(n);
            });
        return true;
    }

    private void newGeneration() {
        this.generation++;
        this.lastPosted = 0;
        if (this.thread != null) this.thread.interrupt();
    }

    private void startThread() {
        if (!this.threaded || this.thread != null) return;
        final Thread t = new Thread(this::loop, "ClassicBlink");
        t.setDaemon(true);
        this.thread = t;
        t.start();
    }

    /** The thread: wait for each due toggle of the current phase and post it. */
    private void loop() {
        for (;;) {
            final long[] n;
            synchronized (this) {
                while (!this.closed && !this.armed) {
                    try {
                        wait();
                    } catch (InterruptedException ie) {
                        // re-check
                    }
                }
                if (this.closed) return;
                n = next();
            }
            if (n == null) continue;
            try {
                this.clock.waitUntil(n[2]);
            } catch (InterruptedException ie) {
                continue;   // a new phase (or close): re-read
            }
            try {
                due((int) n[0], (int) n[1]);
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Classic blink: toggle not posted", e);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public synchronized String toString() {
        return "ClassicBlink[armed=" + this.armed + " gen=" + this.generation
            + " last=" + this.lastPosted + "]";
    }
}
