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
 * The clock of the <b>departure</b> slideshow ({@link ClassicDeparture}):
 * which of the ten dissolves is running and how many of its pixels are
 * revealed after a given number of milliseconds.  Swing-free and
 * immutable apart from {@link #skip}, so it is unit-tested headless, like
 * {@link ClassicNewWorldChain}.
 *
 * <p>Frame 0 is the audience still, frames 1..10 the pictures
 * LEVN0001..0010 with their captions.  Transition {@code k} (0..9)
 * dissolves frame {@code k} into frame {@code k+1}: it reveals the
 * {@code changed[k]} differing pixels at {@link #DISSOLVE_PX_PER_SEC} and
 * then holds the new frame for {@link #HOLD_MS}.  After the last hold the
 * show is {@link State#done}.
 *
 * <p><b>The two constants are ESTIMATES, not measurements.</b>  They are
 * the best fit to hand-timed captures, whose timestamps are only upper
 * bounds on when a state was first visible (Dutch run
 * {@code start-sequence-dutch/opening_069..083}, English run
 * {@code start-sequence/opening_040..049}): a constant step period fits
 * neither run, but "8.9 s hold after a dissolve that runs at about 39,000
 * changed pixels per second" fits both within 0.25 s -- about 1.6 s for
 * the audience and the four dawn steps (about 62,000 changed pixels each)
 * and 0.05-0.25 s for the ship steps (2,000-8,900).  The two mid-dissolve
 * captures agree: {@code 042} (England, 2->3) 78.0 % and {@code 077}
 * (Holland, 5->6) 72.6 % revealed.  A typical show lasts about 98 s; the
 * captures put the first game scene at +104.8 s (Holland) and +113.5 s
 * (England) after the audience still, including the engine start.  Tune
 * both here once a timed recording of the original exists.
 *
 * <p>Callers pass wall-clock milliseconds (differences of
 * {@code System.nanoTime()}), never tick counts, so a stalled event thread
 * cannot slow the show down -- it only skips ahead.
 */
final class ClassicDepartureTimeline {

    /** Dissolve speed in changed pixels per second (estimate, see above). */
    static final int DISSOLVE_PX_PER_SEC = 39000;

    /** How long each new frame stays after its dissolve, ms (estimate). */
    static final long HOLD_MS = 8900L;

    /** Number of transitions: audience -> 1, 1 -> 2, ..., 9 -> 10. */
    static final int TRANSITIONS = ClassicDeparture.STEPS;


    /** A point of the show; immutable. */
    static final class State {

        /**
         * The transition in progress or being held, 0..9: frame
         * {@code transition} dissolving into frame {@code transition + 1}.
         */
        final int transition;

        /** The pixels of that transition revealed so far (capped). */
        final int revealed;

        /** Whether the show is over (or was skipped). */
        final boolean done;

        State(int transition, int revealed, boolean done) {
            this.transition = transition;
            this.revealed = revealed;
            this.done = done;
        }

        @Override
        public String toString() {
            return "State[" + this.transition + ", " + this.revealed
                + (this.done ? ", done" : "") + "]";
        }
    }


    /** Differing pixels per transition. */
    private final int[] changed;

    /** Start of each transition, ms after the show began. */
    private final long[] start;

    /** End of the show, ms. */
    private final long end;

    /** Set by {@link #skip}. */
    private boolean skipped = false;


    /**
     * Schedule a show.
     *
     * @param changed The number of differing pixels between frame
     *     {@code k} and frame {@code k+1}, for k = 0..9 (negative values
     *     count as 0).
     */
    ClassicDepartureTimeline(int[] changed) {
        if (changed == null || changed.length != TRANSITIONS) {
            throw new IllegalArgumentException("need " + TRANSITIONS + " counts");
        }
        this.changed = new int[TRANSITIONS];
        this.start = new long[TRANSITIONS];
        long t = 0L;
        for (int k = 0; k < TRANSITIONS; k++) {
            this.changed[k] = Math.max(0, changed[k]);
            this.start[k] = t;
            t += dissolveMs(this.changed[k]) + HOLD_MS;
        }
        this.end = t;
    }

    /**
     * How long a dissolve of {@code changedPixels} takes, rounded up so the
     * last pixel is revealed by the end of it.
     *
     * @param changedPixels The pixels to reveal.
     * @return Milliseconds.
     */
    static long dissolveMs(int changedPixels) {
        if (changedPixels <= 0) return 0L;
        return (changedPixels * 1000L + DISSOLVE_PX_PER_SEC - 1) / DISSOLVE_PX_PER_SEC;
    }

    /** @return The start of transition {@code k}, ms. */
    long startMs(int k) {
        return this.start[k];
    }

    /** @return The end of the whole show, ms. */
    long endMs() {
        return this.end;
    }

    /** @return The differing pixels of transition {@code k}. */
    int changed(int k) {
        return this.changed[k];
    }

    /** End the show now: every later {@link #at} is done. */
    void skip() {
        this.skipped = true;
    }

    /**
     * The state after {@code elapsedMs}.
     *
     * @param elapsedMs Wall-clock milliseconds since the show began
     *     (negative counts as 0).
     * @return The state.
     */
    State at(long elapsedMs) {
        final long e = Math.max(0L, elapsedMs);
        if (this.skipped || e >= this.end) {
            final int last = TRANSITIONS - 1;
            return new State(last, this.changed[last], true);
        }
        int k = TRANSITIONS - 1;
        while (k > 0 && this.start[k] > e) k--;
        final long px = (e - this.start[k]) * DISSOLVE_PX_PER_SEC / 1000L;
        return new State(k, (int) Math.min(this.changed[k], px), false);
    }
}
