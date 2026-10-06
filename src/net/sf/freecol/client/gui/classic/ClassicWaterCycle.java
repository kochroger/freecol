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

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * The original's <b>water cycling</b> clock (M1c design 10 §7, item W6c):
 * palette entries 120-127 rotate one step every {@link #periodNanos} (35
 * game ticks, 575.05 ms; {@link ClassicGamePalette#PERIOD_MS}), and the
 * listener (the map viewer) swaps its colour model and repaints the
 * cycling cells.
 *
 * <p><b>Schedule.</b>  {@link #start} sets phase 0 (VICEROY's order, the
 * fog-start clip's #19) at the first in-game view; step {@code k} (k = 1,
 * 2, ...) is due at {@code t0 + k * P} on the absolute clock and shows the
 * phase {@code base + k} (mod 8).  A step that comes late never shifts the
 * later ones.
 *
 * <p><b>Running and frozen.</b>  The clock runs under dialogs, menus, the
 * options box and the Europe and colony screens (the listener paints only
 * what is showing).  {@link #hold} freezes the phase -- a woodcut (W9), the
 * original's only freeze -- and the last {@link #release} gives one step
 * at the next frame ({@link #RELEASE_STEP_MS}), the new schedule's step 1
 * (landfall #2638 -&gt; #2639, fog-start #1572 -&gt; #1573).  The classic pref
 * {@code waterCycling} OFF freezes it as well, read where it is used: at
 * every due step ({@link #pollPref}); ON again resumes from the frozen
 * phase one period later (the original's OFF is not observed, design 10
 * O12).
 *
 * <p><b>Threads.</b>  Modelled on {@link ClassicBlink}: a daemon thread
 * waits for each due step ({@code Thread.sleep} plus spin, never
 * {@code parkNanos}: the master plan's timing rule) and posts it to the
 * listener's thread (the event thread); every hold, release, re-base and
 * close starts a new generation, and a post of an older one, or of a step
 * already applied, is dropped when it arrives.  The event thread is often
 * held by blocking waits (a slide, its hold, the gap before a chained
 * slide), where a posted step would land up to 300 ms late; those waits use
 * {@link #servicing}, a clock that fires every step due before the wait's
 * own deadline, each at its own deadline.
 *
 * <p>Everything but the thread runs on an injectable clock without a
 * thread, for the tests.
 */
final class ClassicWaterCycle {

    private static final Logger logger = Logger.getLogger(ClassicWaterCycle.class.getName());

    /**
     * From the last release of a hold to its step: one frame of the
     * original's 70.0863 Hz capture, so the step shows in the frame after
     * the restore (landfall #2638 -&gt; #2639, fog-start #1572 -&gt; #1573).
     */
    static final double RELEASE_STEP_MS = 1000.0 / ClassicFrameRecorder.HZ;

    /** Nanoseconds per millisecond. */
    private static final double NS_PER_MS = 1_000_000.0;

    /** What a step is handed to (on the listener's thread). */
    interface Listener {

        /**
         * A step is due: show phase {@code phase} now.
         *
         * @param phase The new phase, 0 .. phases - 1.
         * @param k The step of the current schedule, 1, 2, ...
         * @param due When it was due ({@link ClassicSlide.Clock#now} units).
         * @param serviced True if a blocking wait fired it ({@link #servicing}),
         *     false if the thread's post did.
         */
        void paletteStep(int phase, long k, long due, boolean serviced);
    }


    private final ClassicSlide.Clock clock;

    /** Runs a step on the listener's thread ({@code SwingUtilities::invokeLater}). */
    private final Consumer<Runnable> poster;

    /** Whether the caller is the listener's thread (the servicing clock fires only there). */
    private final BooleanSupplier onListenerThread;

    /** The classic pref {@code waterCycling}, or null for always on. */
    private final BooleanSupplier pref;

    private final Listener listener;

    /** Whether a thread of its own waits for the steps (else the caller does, tests). */
    private final boolean threaded;

    /** The number of phases (8). */
    private final int phases;

    /** The period in ms (575.05). */
    private final double periodMs;

    /** Step k is due at {@code t0 + k * P}. */
    private long t0 = 0L;

    /** The phase at k = 0. */
    private int base = 0;

    /** The last step applied in this schedule, 0 for none. */
    private long applied = 0L;

    /** The last step handed to the poster in this schedule, 0 for none. */
    private long posted = 0L;

    /** The schedule's generation; a post of another one is dropped. */
    private int generation = 0;

    /** Whether {@link #start} ran. */
    private boolean started = false;

    /** The pref's state as last read. */
    private boolean enabled = true;

    /** Why the phase is held (woodcuts), in hold order; empty while it runs. */
    private final Set<String> holds = new LinkedHashSet<>();

    /** Set by {@link #close}: the thread ends. */
    private boolean closed = false;

    /** The waiting thread, started by {@link #start}. */
    private Thread thread = null;


    /**
     * @param clock The clock to schedule against.
     * @param poster Runs each step (in the game: on the event thread).
     * @param onListenerThread Whether the calling thread is the poster's
     *     (in the game {@code SwingUtilities::isEventDispatchThread}); only
     *     there does {@link #servicing} fire steps.
     * @param pref The pref {@code waterCycling}, or null (always on).
     * @param listener Is told each step.
     * @param cycle The cycle (its count and ticks), or null for the original's.
     * @param threaded Start a thread of its own with {@link #start}, which
     *     waits for every due step and posts it; false leaves that to the
     *     caller ({@link #next}, {@link #due}; tests).
     */
    ClassicWaterCycle(ClassicSlide.Clock clock, Consumer<Runnable> poster,
                      BooleanSupplier onListenerThread, BooleanSupplier pref,
                      Listener listener, ClassicGamePalette.Cycle cycle,
                      boolean threaded) {
        final ClassicGamePalette.Cycle c = (cycle == null)
            ? ClassicGamePalette.Cycle.DEFAULT : cycle;
        this.clock = clock;
        this.poster = poster;
        this.onListenerThread = onListenerThread;
        this.pref = pref;
        this.listener = listener;
        this.threaded = threaded;
        this.phases = c.count;
        this.periodMs = c.periodMs();
    }


    // The schedule

    /**
     * When step {@code k} of a schedule is due.
     *
     * @param t0 The schedule's start.
     * @param k The step, 0, 1, 2, ...
     * @param periodMs The period in ms.
     * @return {@code t0 + k * P}, rounded to the nanosecond (no drift).
     */
    static long dueNanos(long t0, long k, double periodMs) {
        return t0 + Math.round(k * periodMs * NS_PER_MS);
    }

    /**
     * The step to wait for next: the latest one due by {@code now} if it
     * has not been handed on, else the first one after it; never one
     * already handed on.  A late wake-up skips to the latest due step (the
     * phase is absolute, so nothing is lost).
     *
     * @param t0 The schedule's start.
     * @param now The time now.
     * @param last The last step handed on, 0 for none.
     * @param periodMs The period in ms.
     * @return The step, at least {@code last + 1}.
     */
    static long nextIndex(long t0, long now, long last, double periodMs) {
        return Math.max(last + 1, dueBy(t0, now, periodMs));
    }

    /**
     * The latest step due by {@code now}: the largest k with
     * {@code dueNanos(t0, k) <= now} (exact at the deadline, whatever the
     * rounding of {@link #dueNanos}).
     *
     * @param t0 The schedule's start.
     * @param now The time now.
     * @param periodMs The period in ms.
     * @return The step, 0 for none yet.
     */
    static long dueBy(long t0, long now, double periodMs) {
        if (now <= t0) return 0L;
        long k = (long) Math.floor((now - t0) / (periodMs * NS_PER_MS));
        while (dueNanos(t0, k + 1, periodMs) <= now) k++;
        while (k > 0 && dueNanos(t0, k, periodMs) > now) k--;
        return k;
    }

    /** @return The period in nanoseconds (rounded). */
    long periodNanos() {
        return Math.round(this.periodMs * NS_PER_MS);
    }


    // State (any thread; the game calls these on the event thread)

    /**
     * Start cycling now at phase 0: step 1 one period from now.  Once only.
     */
    synchronized void start() {
        if (this.started || this.closed) return;
        this.started = true;
        this.base = 0;
        this.enabled = readPref();
        rebase(this.clock.now());
        ClassicFrameRecorder.event("palette-start", "p=0 period="
            + this.periodMs + "ms" + (this.enabled ? "" : " pref=off"));
        startThread();
    }

    /** @return Whether {@link #start} ran. */
    synchronized boolean isStarted() {
        return this.started;
    }

    /** @return The phase now, 0 .. phases - 1. */
    synchronized int phase() {
        return phaseOf(this.applied);
    }

    /** @return The number of phases. */
    int phaseCount() {
        return this.phases;
    }

    /** @return Whether steps come now: started, not held, the pref on, not closed. */
    synchronized boolean isRunning() {
        return this.started && !this.closed && this.enabled && this.holds.isEmpty();
    }

    /** @return Whether the phase is held (a woodcut). */
    synchronized boolean isHeld() {
        return !this.holds.isEmpty();
    }

    /** @return Whether the pref lets it cycle (as last read). */
    synchronized boolean isEnabled() {
        return this.enabled;
    }

    /** @return The current schedule's start: step k is due at {@code t0 + k * P}. */
    synchronized long scheduleStart() {
        return this.t0;
    }

    /** @return The current generation. */
    synchronized int generation() {
        return this.generation;
    }

    /**
     * When the next step is due, if one comes.
     *
     * @return Its deadline, or {@code Long.MAX_VALUE} while frozen.
     */
    synchronized long dueNanos() {
        if (!isRunning()) return Long.MAX_VALUE;
        return dueNanos(this.t0, this.applied + 1, this.periodMs);
    }

    /**
     * Freeze the phase (a woodcut, W9): no step until the last hold is
     * released.  Holds by different reasons nest; a reason held twice
     * counts once.
     *
     * @param reason Why, e.g. "woodcut".
     */
    void hold(String reason) {
        final int p;
        synchronized (this) {
            if (this.closed || !this.holds.add(reason)) return;
            if (this.holds.size() > 1) return;
            freeze();
            p = phase();
        }
        ClassicFrameRecorder.event("palette-hold", reason + " p=" + p);
    }

    /**
     * Release a hold.  The last one gives one step at the next frame
     * ({@link #RELEASE_STEP_MS}) and the schedule runs on from it.
     *
     * @param reason The hold's reason.
     */
    void release(String reason) {
        final int p;
        final boolean runs;
        synchronized (this) {
            if (!this.holds.remove(reason) || !this.holds.isEmpty()) return;
            p = phase();
            runs = isRunning();
            if (runs) {
                // Step 1 of the new schedule comes one frame from now.
                rebase(this.clock.now() + Math.round(RELEASE_STEP_MS * NS_PER_MS)
                       - periodNanos());
            } else {
                newGeneration();   // the thread reads the pref again
            }
        }
        ClassicFrameRecorder.event("palette-release", reason + " p=" + p
            + (runs ? " step in " + String.format(java.util.Locale.ROOT, "%.2fms",
                                                  RELEASE_STEP_MS) : " frozen"));
    }

    /**
     * Switch the cycling on or off (the pref {@code waterCycling}).  OFF
     * freezes the phase; ON resumes from it, step 1 one period later.
     *
     * @param on The pref's value.
     */
    void setEnabled(boolean on) {
        final int p;
        synchronized (this) {
            if (this.closed || on == this.enabled) return;
            final boolean was = isRunning();
            this.enabled = on;
            if (!this.started) return;
            if (was && !on) {
                freeze();
            } else if (isRunning()) {
                rebase(this.clock.now());
            }
            p = phase();
        }
        ClassicFrameRecorder.event("palette-pref", (on ? "on" : "off") + " p=" + p);
    }

    /**
     * Read the pref and follow it ({@link #setEnabled}): the thread does
     * so at every due step and, while it is off, every period.
     *
     * @return The pref's value.
     */
    boolean pollPref() {
        final boolean on = readPref();
        setEnabled(on);
        return on;
    }

    private boolean readPref() {
        if (this.pref == null) return true;
        try {
            return this.pref.getAsBoolean();
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Classic water cycle: pref unreadable", e);
            return true;
        }
    }

    /** End the cycle and its thread for good. */
    void close() {
        final Thread t;
        synchronized (this) {
            if (this.closed) return;
            this.closed = true;
            this.generation++;
            t = this.thread;
            this.thread = null;
            notifyAll();
        }
        if (t != null) t.interrupt();
    }

    /** @return Whether {@link #close} ran. */
    synchronized boolean isClosed() {
        return this.closed;
    }

    /**
     * The next step to wait for: the latest due one not handed on yet, or
     * the next.  No side effect ({@link #due} claims it).
     *
     * @return {generation, k, due}, or null while frozen.
     */
    synchronized long[] next() {
        if (!isRunning()) return null;
        final long k = nextIndex(this.t0, this.clock.now(),
                                 Math.max(this.posted, this.applied), this.periodMs);
        return new long[] { this.generation, k, dueNanos(this.t0, k, this.periodMs) };
    }

    /**
     * Step {@code k} of generation {@code g} has come due: claim it and
     * hand it to the listener through the poster (where it is dropped if
     * its generation has gone or a blocking wait has applied it already).
     *
     * @param g The step's generation.
     * @param k The step.
     * @return False if the schedule has changed or the step was handed on
     *     already (nothing is posted).
     */
    boolean due(int g, long k) {
        synchronized (this) {
            if (!isRunning() || g != this.generation || k <= this.posted
                || k <= this.applied) return false;
            this.posted = k;
        }
        this.poster.accept(() -> apply(g, k, false));
        return true;
    }

    /**
     * Fire every step due by now, on the listener's thread: the latest one
     * is applied (the phase is absolute), the earlier ones are skipped.
     * What {@link #servicing} calls at each deadline.
     *
     * @return True if a step was applied.
     */
    boolean serviceDue() {
        final long k;
        final int g;
        synchronized (this) {
            if (!isRunning()) return false;
            final long kNow = dueBy(this.t0, this.clock.now(), this.periodMs);
            if (kNow <= this.applied) return false;
            k = kNow;
            g = this.generation;
        }
        return apply(g, k, true);
    }

    /**
     * Apply step {@code k} of generation {@code g} on the listener's
     * thread, unless it is stale.
     *
     * @return True if it was applied.
     */
    private boolean apply(int g, long k, boolean serviced) {
        final int p;
        final long due;
        synchronized (this) {
            if (!isRunning() || g != this.generation || k <= this.applied) return false;
            this.applied = k;
            if (this.posted < k) this.posted = k;
            p = phaseOf(k);
            due = dueNanos(this.t0, k, this.periodMs);
        }
        try {
            this.listener.paletteStep(p, k, due, serviced);
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Classic water cycle: step " + k + " failed", e);
        }
        return true;
    }

    /**
     * A clock for the blocking waits of the listener's thread (the slide,
     * its hold, the gap before a chained slide, the native cue, the panel
     * tick): {@code now} is the base clock's; {@code waitUntil(due)} first
     * waits for every step due before {@code due} and fires it at its own
     * deadline ({@link #serviceDue}), in order, then waits for {@code due}.
     * On any other thread it is the base clock.  The slide's own schedule
     * ({@link ClassicSlide#run}) does not change.
     *
     * @param base The clock to wait on (the cycle's own in the game).
     * @return The servicing clock.
     */
    ClassicSlide.Clock servicing(ClassicSlide.Clock base) {
        return new ClassicSlide.Clock() {
                @Override
                public long now() {
                    return base.now();
                }

                @Override
                public void waitUntil(long due) throws InterruptedException {
                    if (onListenerThread == null || onListenerThread.getAsBoolean()) {
                        for (;;) {
                            final long c = dueNanos();
                            if (c >= due) break;
                            base.waitUntil(c);
                            if (!serviceDue()) break;   // frozen meanwhile
                        }
                    }
                    base.waitUntil(due);
                }
            };
    }

    private int phaseOf(long k) {
        return (int) Math.floorMod(this.base + k, (long) this.phases);
    }

    /** Freeze at the current phase (held, or the pref off). */
    private void freeze() {
        this.base = phaseOf(this.applied);
        this.applied = 0L;
        this.posted = 0L;
        newGeneration();
    }

    /**
     * A new schedule from the current phase: step k due at
     * {@code t0 + k * P}.
     */
    private void rebase(long t0) {
        this.base = phaseOf(this.applied);
        this.t0 = t0;
        this.applied = 0L;
        this.posted = 0L;
        newGeneration();
    }

    private void newGeneration() {
        this.generation++;
        notifyAll();
        if (this.thread != null) this.thread.interrupt();
    }

    private void startThread() {
        if (!this.threaded || this.thread != null || this.closed) return;
        final Thread t = new Thread(this::loop, "ClassicWaterCycle");
        t.setDaemon(true);
        this.thread = t;
        t.start();
    }

    /**
     * The thread: wait for each due step of the current schedule and post
     * it; while the pref is off, read it every period.
     */
    private void loop() {
        for (;;) {
            final long[] n;
            final boolean prefOff;
            synchronized (this) {
                // Wait while held (or not started); a cycle frozen by the
                // pref alone reads it again below, every period.
                while (!this.closed && (!this.started || !this.holds.isEmpty())) {
                    try {
                        wait();
                    } catch (InterruptedException ie) {
                        // re-check
                    }
                }
                if (this.closed) return;
                n = next();
                prefOff = n == null;
            }
            try {
                if (prefOff) {
                    // Frozen by the pref alone: look again one period on.
                    this.clock.waitUntil(this.clock.now() + periodNanos());
                    pollPref();
                    continue;
                }
                this.clock.waitUntil(n[2]);
            } catch (InterruptedException ie) {
                continue;   // a new schedule (or close): re-read
            }
            try {
                if (pollPref()) due((int) n[0], n[1]);
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Classic water cycle: step not posted", e);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public synchronized String toString() {
        return "ClassicWaterCycle[phase=" + phase() + " started=" + this.started
            + " held=" + this.holds + " enabled=" + this.enabled + " gen="
            + this.generation + " applied=" + this.applied + "]";
    }
}
