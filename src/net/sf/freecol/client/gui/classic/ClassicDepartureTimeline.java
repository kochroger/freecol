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
 * <p>Frame 0 is <b>black</b>, frames 1..10 the pictures LEVN0001..0010
 * with their captions.  Transition {@code k} (0..9) dissolves frame
 * {@code k} into frame {@code k+1}: it starts at {@link #onsetMs
 * onsetMs(k)}, reveals its changed pixels evenly over {@link #DISSOLVE_MS}
 * however many there are, and the new frame then stays until the next
 * onset.  At {@link #END_MS} the show is {@link State#done}.  Captions
 * change with their pictures: they are part of the dissolving frame.
 *
 * <p><b>MEASURED</b> on the timed Dutch run
 * {@code screenshots/departure-dutch-timed/opening_167..340} (Enter on the
 * audience pressed immediately before 167, one capture every ~0.53 s,
 * times from the file stamps, t = 0 at 167; two gaps of ~24 s), every
 * capture classified against the production frames
 * ({@link ClassicDeparture#renderStep}, 0 px on every settled one):
 * <ul>
 *   <li><b>Black first, not the audience.</b>  167 is all black; 168 and
 *       169 hold only black and LEVN0001 pixels (1.2 % and 70.5 %).  The
 *       original cuts the audience to black on the key and dissolves
 *       picture 1 in from black, starting ~0.56 s later.</li>
 *   <li><b>Constant-time dissolve.</b>  All twelve mid-dissolve captures
 *       on file (ten timed, Dutch 077, English 042: seven different
 *       transitions in three runs) nest -- every pixel new in a capture
 *       with a smaller fraction is new in every one with a larger
 *       fraction, 0 exceptions in 66 pairs.  So the original walks one
 *       fixed order over all 64,000 screen positions in every dissolve,
 *       unchanged positions included, and the time does not depend on
 *       how many pixels change.  The ship steps prove it: 256 and 257,
 *       0.51 s apart, show 22 % and 88 % of the 3,994 pixels of 7 -&gt; 8,
 *       which the old estimate (39,000 changed px/s) would have finished
 *       in 0.1 s.  One duration for all seven caught transitions fits
 *       every mid and bracketing capture within 0.05 s, the stamps' own
 *       precision, at 0.90 s.  (The full-screen night steps lean towards
 *       ~1.0 s and the ship steps towards ~0.75 s; a size-dependent
 *       duration would fit within 0.02 s, but the difference is below
 *       what the stamps resolve and DOSBox itself lagged on the full
 *       screen steps -- the captures taken during them were stored
 *       ~0.17 s late -- so it is not modelled.)</li>
 *   <li><b>Onsets per picture, not one hold.</b>  The pictures do not
 *       stay equally long: picture 1 ~11.1 s, picture 2 ~10.8 s,
 *       pictures 3-5 ~9.7 s on average, pictures 6-8 9.3-9.5 s (from the
 *       start of one dissolve to the start of the next).  Why the early
 *       ones last longer is unknown; the table keeps them as measured.</li>
 * </ul>
 *
 * <pre>
 *  k  picture   onset ms   source (timed captures)
 *  0  LEVN0001       560   168, 169 (mid); 170 settled
 *  1  LEVN0002    11,700   188 settled old; 189, 190 (mid); 191 settled
 *  2  LEVN0003    22,530   209 old; 210 (mid); 211 settled
 *  3  LEVN0004    32,190   INTERPOLATED (capture gap 25.0-48.6 s):
 *  4  LEVN0005    41,850     22,530..51,510 split in three
 *  5  LEVN0006    51,510   220 old; 221 (mid); 222 settled
 *  6  LEVN0007    60,910   238 old; 239 (mid); 240 settled
 *  7  LEVN0008    70,240   255 old; 256, 257 (mid); 258 settled
 *  8  LEVN0009    79,690   273 old; 274 (mid); 275 settled
 *  9  LEVN0010    89,090   EXTRAPOLATED (gap 80.6-105.5 s): + 9,400,
 *                           the mean period of pictures 6-8
 *    END          98,480   EXTRAPOLATED: one more such period
 * </pre>
 *
 * The first game scene was on screen at the next capture (276, +105.5 s)
 * and stayed to the end of the series; the engine's own 1-2 s start after
 * {@link #END_MS} (see {@code ClassicMainMenuPanel.startDeparture}) lands
 * the first scene at ~100 s, inside that bound.  The older hand-stamped
 * runs ({@code start-sequence-dutch/opening_069..083},
 * {@code start-sequence/opening_040..049}) show no contradiction when
 * their stamps are read as upper bounds, but the Dutch one must have
 * reached picture 3 at least 0.5 s sooner than the timed run: the early
 * pictures vary a little from run to run.
 *
 * <p>Callers pass wall-clock milliseconds since the key that left the
 * audience (differences of {@code System.nanoTime()}), never tick counts,
 * so a stalled event thread cannot slow the show down -- it only skips
 * ahead.
 */
final class ClassicDepartureTimeline {

    /**
     * How long every dissolve takes, ms, whatever the number of changed
     * pixels (measured, see above).
     */
    static final long DISSOLVE_MS = 900L;

    /**
     * When transition {@code k} starts, ms after the key that left the
     * audience; the screen is black before {@code ONSET_MS[0]}.  See the
     * table above for which entries are measured.
     */
    private static final long[] ONSET_MS = {
        560L, 11700L, 22530L, 32190L, 41850L,
        51510L, 60910L, 70240L, 79690L, 89090L
    };

    /** When the last picture's hold ends and the show is done, ms. */
    static final long END_MS = 98480L;

    /** Number of transitions: black -> 1, 1 -> 2, ..., 9 -> 10. */
    static final int TRANSITIONS = ClassicDeparture.STEPS;


    /** A point of the show; immutable. */
    static final class State {

        /**
         * The transition in progress or being held, 0..9: frame
         * {@code transition} dissolving into frame {@code transition + 1}.
         * Before the first onset it is 0 with nothing revealed (black).
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

    /** Set by {@link #skip}. */
    private boolean skipped = false;


    /**
     * Schedule a show.
     *
     * @param changed The number of differing pixels between frame
     *     {@code k} and frame {@code k+1}, for k = 0..9 (negative values
     *     count as 0).  They set how many pixels each dissolve reveals,
     *     not how long it takes.
     */
    ClassicDepartureTimeline(int[] changed) {
        if (changed == null || changed.length != TRANSITIONS) {
            throw new IllegalArgumentException("need " + TRANSITIONS + " counts");
        }
        this.changed = new int[TRANSITIONS];
        for (int k = 0; k < TRANSITIONS; k++) {
            this.changed[k] = Math.max(0, changed[k]);
        }
    }

    /**
     * When transition {@code k} starts.
     *
     * @param k The transition, 0..9.
     * @return Milliseconds after the key that left the audience.
     */
    static long onsetMs(int k) {
        return ONSET_MS[k];
    }

    /**
     * How many of {@code changedPixels} a dissolve has revealed
     * {@code sinceOnsetMs} after it began: evenly over
     * {@link #DISSOLVE_MS}, all of them at its end.
     *
     * @param changedPixels The pixels the dissolve reveals.
     * @param sinceOnsetMs Milliseconds since its onset.
     * @return The number revealed, 0..changedPixels.
     */
    static int revealed(int changedPixels, long sinceOnsetMs) {
        if (changedPixels <= 0 || sinceOnsetMs <= 0L) return 0;
        if (sinceOnsetMs >= DISSOLVE_MS) return changedPixels;
        return (int) (changedPixels * sinceOnsetMs / DISSOLVE_MS);
    }

    /** @return The start of transition {@code k}, ms. */
    long startMs(int k) {
        return onsetMs(k);
    }

    /** @return The end of the whole show, ms. */
    long endMs() {
        return END_MS;
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
     * @param elapsedMs Wall-clock milliseconds since the key that left
     *     the audience (negative counts as 0).
     * @return The state.
     */
    State at(long elapsedMs) {
        final long e = Math.max(0L, elapsedMs);
        if (this.skipped || e >= END_MS) {
            final int last = TRANSITIONS - 1;
            return new State(last, this.changed[last], true);
        }
        int k = TRANSITIONS - 1;
        while (k > 0 && ONSET_MS[k] > e) k--;
        return new State(k, revealed(this.changed[k], e - ONSET_MS[k]), false);
    }
}
