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
 * A one-shot timer on an absolute deadline, for the end of turn and the
 * hand-over to the next unit (build spec W5): one task at a time, due at
 * a {@code System.nanoTime} moment, run on the poster's thread (the event
 * thread in the game).
 *
 * <p>Not a {@code javax.swing.Timer}: its queue waits with
 * {@code LockSupport.parkNanos}, which wakes up to 15 ms late on Windows
 * (W1), and the original's pauses must hold to a frame (485 &plusmn; 17 ms,
 * 500 &plusmn; 15 ms).  A daemon thread waits with the recorder's
 * {@code Thread.sleep} plus spin ({@link ClassicSlide#waitUntil}), as the
 * blink clock does ({@link ClassicBlink}).
 *
 * <p>{@link #schedule} replaces the task, {@link #cancel} drops it.  Each
 * starts a new generation, and a task posted under an older one is dropped
 * when it arrives, so a task that was already queued behind a slide can
 * never run after it was cancelled or replaced.
 *
 * <p>Without the thread (tests) {@link #runIfDue} does the waiting thread's
 * job against the injected clock.
 */
final class ClassicOneShot {

    private static final Logger logger = Logger.getLogger(ClassicOneShot.class.getName());

    private final ClassicSlide.Clock clock;

    /** Runs a due task on its thread ({@code SwingUtilities::invokeLater}). */
    private final Consumer<Runnable> poster;

    /** Whether a thread of its own waits for the deadline. */
    private final boolean threaded;

    /** The waiting thread's name. */
    private final String name;

    /** The task, or null when none is scheduled. */
    private Runnable task = null;

    /** When the task is due. */
    private long due = 0L;

    /** The generation of the task; a task of another one is dropped. */
    private int generation = 0;

    /** Set once the task of this generation was handed to the poster. */
    private boolean posted = false;

    /** Set by {@link #close}: the thread ends. */
    private boolean closed = false;

    /** The waiting thread, started on the first schedule. */
    private Thread thread = null;


    /**
     * @param clock The clock the deadlines are on.
     * @param poster Runs a due task (in the game: on the event thread).
     * @param threaded Wait on a thread of its own; false leaves that to
     *     {@link #runIfDue} (tests).
     * @param name The thread's name.
     */
    ClassicOneShot(ClassicSlide.Clock clock, Consumer<Runnable> poster,
                   boolean threaded, String name) {
        this.clock = clock;
        this.poster = poster;
        this.threaded = threaded;
        this.name = name;
    }


    /**
     * Run {@code task} at {@code due}, in place of any task scheduled
     * before.  A deadline in the past runs it as soon as possible.
     *
     * @param due The {@code System.nanoTime} moment.
     * @param task The task.
     */
    synchronized void schedule(long due, Runnable task) {
        if (this.closed || task == null) return;
        this.task = task;
        this.due = due;
        newGeneration();
        startThread();
        notifyAll();
    }

    /** Drop the scheduled task, if any. */
    synchronized void cancel() {
        if (this.task == null) return;
        this.task = null;
        newGeneration();
    }

    /** @return Whether a task waits for its deadline (or to be run). */
    synchronized boolean isScheduled() {
        return this.task != null;
    }

    /** @return The scheduled task's deadline; meaningless without one. */
    synchronized long dueNanos() {
        return this.due;
    }

    /** End the timer and its thread for good. */
    void close() {
        final Thread t;
        synchronized (this) {
            this.closed = true;
            this.task = null;
            newGeneration();
            t = this.thread;
            this.thread = null;
            notifyAll();
        }
        if (t != null) t.interrupt();
    }

    /**
     * The waiting thread's job, for a timer without one (tests): if the
     * task is due on the clock, hand it to the poster.
     *
     * @return True if a task was posted.
     */
    boolean runIfDue() {
        final int g;
        synchronized (this) {
            if (this.task == null || this.posted
                || this.clock.now() < this.due) return false;
            g = this.generation;
        }
        return post(g);
    }

    /**
     * Hand the task of generation {@code g} to the poster, once; it runs
     * there only if its generation is still the current one.
     *
     * @param g The generation.
     * @return False if the task changed or was posted already.
     */
    private boolean post(int g) {
        synchronized (this) {
            if (this.task == null || g != this.generation || this.posted) {
                return false;
            }
            this.posted = true;
        }
        this.poster.accept(() -> {
                final Runnable r;
                synchronized (this) {
                    if (g != this.generation || this.task == null) return;
                    r = this.task;
                    this.task = null;
                }
                r.run();
            });
        return true;
    }

    private void newGeneration() {
        this.generation++;
        this.posted = false;
        if (this.thread != null) this.thread.interrupt();
    }

    private void startThread() {
        if (!this.threaded || this.thread != null) return;
        final Thread t = new Thread(this::loop, this.name);
        t.setDaemon(true);
        this.thread = t;
        t.start();
    }

    /** The thread: wait for the deadline of the current task and post it. */
    private void loop() {
        for (;;) {
            final int g;
            final long d;
            synchronized (this) {
                while (!this.closed && (this.task == null || this.posted)) {
                    try {
                        wait();
                    } catch (InterruptedException ie) {
                        // re-check
                    }
                }
                if (this.closed) return;
                g = this.generation;
                d = this.due;
            }
            try {
                this.clock.waitUntil(d);
            } catch (InterruptedException ie) {
                continue;   // replaced, cancelled or closed: re-read
            }
            try {
                post(g);
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Classic timer " + this.name
                    + ": task not posted", e);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public synchronized String toString() {
        return "ClassicOneShot[" + this.name + " scheduled="
            + (this.task != null) + " gen=" + this.generation + "]";
    }
}
