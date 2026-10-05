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


/**
 * The original's unit slide as a schedule (build spec W2, spec delta W2):
 * the sprite moves one native pixel per step, offsets 1 to 15 on an
 * absolute clock {@code t_k = t1 + (k - 1) * S}, holds at offset 15 for
 * {@link #HOLD_MS}, and offset 16 is not a step: it is the final draw, the
 * first ordinary paint after the slide, together with the newly revealed
 * tiles.  Measured in the landfall clip (81 slides: 1 px per step,
 * offsets 1-15 in 230.6 ms, hold 72 ms, offset 1 to final about 300 ms)
 * and the landing-slow clip (the accelerator: 13.25 instead of 16.43 ms).
 *
 * <p>S is {@link #STEP_MS}, or {@link #FAST_STEP_MS} while the classic pref
 * {@code moveAccelerator} is on; the caller reads the pref when each slide
 * starts.  The hold, and the pauses between chained slides, are the same at
 * both speeds.
 *
 * <p>When the unit is not on screen at its source as the slide starts (its
 * blink is OFF, it is a passenger, the view has just jumped), offset 0 is
 * painted at once and offset 1 follows one step later; otherwise offset 1
 * is the first paint.
 *
 * <p>Waits are {@code Thread.sleep} plus a short spin
 * ({@link #waitUntil}): {@code LockSupport.parkNanos} wakes up to 15 ms late
 * on Windows (W1).  Every deadline is absolute, so a slow paint delays one
 * frame and never the rest of the slide.
 */
final class ClassicSlide {

    /** The slide step with the accelerator off: one game tick T (ms). */
    static final double STEP_MS = 16.43;

    /** The slide step with the Bewegungsbeschleuniger on (ms). */
    static final double FAST_STEP_MS = 13.25;

    /** The hold at offset {@link #LAST_STEP} before the final draw (ms). */
    static final double HOLD_MS = 72.0;

    /** The last offset the slide paints; {@link #CELL} is the final draw. */
    static final int LAST_STEP = 15;

    /** Native pixels per cell, the offset of the final draw. */
    static final int CELL = 16;

    /**
     * From a step's final draw to the next step of an automatic (goto)
     * move: 2-12 frames in the clip, the build spec's 100 ms.
     */
    static final double GOTO_GAP_MS = 100.0;

    /**
     * From a foreign unit's final draw to the next foreign slide.  Natives
     * start 340-390 ms apart; with offset 1 to final at about 302 ms this
     * leaves about 60 ms.
     */
    static final double FOREIGN_GAP_MS = 60.0;

    /** Nanoseconds per millisecond. */
    private static final double NS_PER_MS = 1_000_000.0;


    /** A clock to schedule against (the system clock, or a test's). */
    interface Clock {

        /** @return Now, in {@code System.nanoTime} units. */
        long now();

        /**
         * Wait until {@code due}.
         *
         * @param due The deadline.
         * @exception InterruptedException if interrupted.
         */
        void waitUntil(long due) throws InterruptedException;
    }

    /** What the slide paints. */
    interface Painter {

        /**
         * Paint the sliding unit at native offset {@code k} (0..15).
         *
         * @param k The offset.
         */
        void paint(int k);
    }

    /** The system clock ({@link System#nanoTime}, {@link #waitUntil}). */
    static final Clock SYSTEM = new Clock() {
            @Override
            public long now() {
                return System.nanoTime();
            }

            @Override
            public void waitUntil(long due) throws InterruptedException {
                ClassicSlide.waitUntil(due);
            }
        };


    private ClassicSlide() {}   // static helpers only


    /**
     * The step for a slide.
     *
     * @param accelerator The classic pref {@code moveAccelerator}.
     * @return S in nanoseconds.
     */
    static long stepNanos(boolean accelerator) {
        return Math.round((accelerator ? FAST_STEP_MS : STEP_MS) * NS_PER_MS);
    }

    /**
     * When offset {@code k} is due.
     *
     * @param t1 When offset 1 is due.
     * @param step S in nanoseconds.
     * @param k The offset, 1..{@link #LAST_STEP}.
     * @return {@code t1 + (k - 1) * S}.
     */
    static long dueNanos(long t1, long step, int k) {
        return t1 + (k - 1) * step;
    }

    /**
     * When the hold at offset {@link #LAST_STEP} ends and the final draw is
     * due.
     *
     * @param t1 When offset 1 is due.
     * @param step S in nanoseconds.
     * @return The deadline.
     */
    static long holdEndNanos(long t1, long step) {
        return dueNanos(t1, step, LAST_STEP) + millisToNanos(HOLD_MS);
    }

    /**
     * The pause from the last final draw to the next chained slide.
     *
     * @param own True for the player's own unit (a goto step), false for
     *     a foreign unit (natives, AI Europeans).
     * @return The pause in nanoseconds.
     */
    static long gapNanos(boolean own) {
        return millisToNanos(own ? GOTO_GAP_MS : FOREIGN_GAP_MS);
    }

    /**
     * The screen offset of the sprite at native offset {@code k}.
     *
     * @param k The native offset, 0..{@link #CELL}.
     * @param s The integer screen scale.
     * @param d The raw-grid step along one axis (-1, 0, 1; 2 for the
     *     isometric model's north/south moves).
     * @return {@code k * s * d}: whole screen pixels, so every step moves
     *     the sprite by exactly {@code s} pixels per axis.
     */
    static int screenOffset(int k, int s, int d) {
        return k * s * d;
    }

    /**
     * Run the steps of one slide: offset 0 at once when {@code redraw},
     * then offsets 1..15 on their deadlines, then the hold.  The caller
     * paints the final draw afterwards.
     *
     * @param clock The clock.
     * @param step S in nanoseconds.
     * @param redraw Paint offset 0 first (the unit was not on screen at
     *     its source).
     * @param painter Paints each offset.
     * @return When offset 1 was due.
     * @exception InterruptedException if interrupted (the slide stops; the
     *     caller still ends it).
     */
    static long run(Clock clock, long step, boolean redraw, Painter painter)
        throws InterruptedException {
        long t1 = clock.now();
        if (redraw) {
            painter.paint(0);
            t1 += step;
        }
        for (int k = 1; k <= LAST_STEP; k++) {
            clock.waitUntil(dueNanos(t1, step, k));
            painter.paint(k);
        }
        clock.waitUntil(holdEndNanos(t1, step));
        return t1;
    }

    /**
     * Wait until a moment on the clock: sleep to about 1.5 ms before it,
     * then spin (the recorder's clock, {@link ClassicFrameRecorder}).
     *
     * @param due The {@code System.nanoTime} to wait for.
     * @exception InterruptedException if interrupted.
     */
    static void waitUntil(long due) throws InterruptedException {
        ClassicFrameRecorder.waitUntil(due);
    }

    private static long millisToNanos(double ms) {
        return Math.round(ms * NS_PER_MS);
    }
}
