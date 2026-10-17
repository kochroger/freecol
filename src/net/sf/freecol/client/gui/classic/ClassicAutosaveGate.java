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

import java.awt.SecondaryLoop;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.swing.SwingUtilities;
import javax.swing.Timer;


/**
 * The turn start's autosave, for those who must wait for it (part Z1, C1
 * of part Z's check): FreeCol writes the autosave of our turn's start on
 * its network thread ({@code InGameController.setCurrentPlayer}), and the
 * Classic UI's «Spiel sichern», «Spiel laden» and every stop of the server
 * (a load, the quit) wait until it is written.  The controller opens the
 * gate's autosave before the turn becomes ours ({@link #begin}) and closes
 * it once the save is written ({@link #end}, also when it failed).
 *
 * <p>The wait ({@link #awaitIdle}) never freezes the screen: on the event
 * thread it runs a secondary event loop, so the map is drawn and every key
 * and click is handled as usual meanwhile (I: the original, one program,
 * takes no input while it saves; its save takes no noticeable time).  It
 * is bounded: an autosave that hangs lets the waiting request go on after
 * the bound (logged); the rows are written in one step
 * ({@link ClassicSaveSlots#writeLastTurn}), so even then no row breaks.
 * The thread that writes the autosave never waits for itself.  Any thread.
 */
final class ClassicAutosaveGate {

    private static final Logger logger = Logger.getLogger(ClassicAutosaveGate.class.getName());

    /** The autosaves begun and not yet ended (nested ones count). */
    private int running = 0;

    /** The thread of the autosave being written, or null. */
    private Thread writer = null;

    /** The event thread's waits, woken when the last autosave ends. */
    private final List<SecondaryLoop> loops = new ArrayList<>();


    /** An autosave begins (the network thread, before the turn is ours). */
    synchronized void begin() {
        this.running++;
        this.writer = Thread.currentThread();
    }

    /** An autosave has ended, written or not (in a {@code finally}). */
    void end() {
        final List<SecondaryLoop> wake;
        synchronized (this) {
            if (this.running > 0) this.running--;
            if (this.running > 0) return;
            this.writer = null;
            wake = new ArrayList<>(this.loops);
            this.loops.clear();
            notifyAll();
        }
        for (SecondaryLoop l : wake) l.exit();
    }

    /** @return Whether an autosave is being written. */
    synchronized boolean running() {
        return this.running > 0;
    }

    /**
     * @return Whether the calling thread would wait ({@link #awaitIdle}):
     *     an autosave is being written by another thread.
     */
    synchronized boolean blocking() {
        return this.running > 0 && this.writer != Thread.currentThread();
    }

    /**
     * Wait until no autosave is being written, at most {@code maxMs}: on
     * the event thread in a secondary event loop (events go on being
     * handled), on any other thread on this gate's monitor; on the thread
     * that writes the autosave not at all.
     *
     * @param maxMs The longest wait, in ms.
     * @return True when no autosave is being written (now, or by the
     *     writing thread itself); false when the bound ran out first.
     */
    boolean awaitIdle(long maxMs) {
        final long deadline = System.nanoTime() + Math.max(0L, maxMs) * 1_000_000L;
        final boolean edt = SwingUtilities.isEventDispatchThread();
        while (true) {
            final SecondaryLoop loop;
            final long leftMs;
            synchronized (this) {
                if (this.running == 0) return true;
                if (this.writer == Thread.currentThread()) return true;
                leftMs = (deadline - System.nanoTime()) / 1_000_000L;
                if (leftMs <= 0L) return false;
                if (!edt) {
                    try {
                        wait(leftMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return this.running == 0;
                    }
                    continue;
                }
                loop = Toolkit.getDefaultToolkit().getSystemEventQueue()
                    .createSecondaryLoop();
                this.loops.add(loop);
            }
            // The bound, on the event thread itself.  A secondary loop also
            // returns by itself (JDK 21: after about a second without
            // events, or when one it is nested in exits, ClassicAdvisorLayer),
            // so the gate is asked again each time; end() wakes it at once.
            final Timer bound = new Timer((int) Math.min(Integer.MAX_VALUE, leftMs),
                                          e -> loop.exit());
            bound.setRepeats(false);
            bound.start();
            boolean entered;
            try {
                entered = loop.enter();
            } finally {
                bound.stop();
                synchronized (this) {
                    this.loops.remove(loop);
                }
            }
            if (!entered && running()) {
                // Not exited before it was entered: the loop would not wait.
                logger.warning("Classic autosave gate: the event loop did not wait");
                return false;
            }
        }
    }
}
