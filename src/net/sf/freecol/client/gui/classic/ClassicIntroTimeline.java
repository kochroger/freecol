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
 * The clock of the <b>intro</b> (the Vorspann before the title menu):
 * which phase is on screen after a given number of milliseconds, and which
 * frame of it.  Swing-free; immutable apart from {@link #skip},
 * {@link #setHasChart} and {@link #setEndFrame}, so it is unit-tested
 * headless like {@link ClassicDepartureTimeline}.
 *
 * <pre>
 *  ms from the first picture   phase    what
 *        0 -   300             BLACK    lead-in: covers the audio start-up
 *                                       (&lt;= ~150 ms), so the first note and
 *                                       the first movement coincide
 *      300 - 10,300            EMBLEM   own "Levi's Colonization 2026"
 *                                       emblem ({@link ClassicEmblem})
 *   10,300 - 10,800            FADE     palette fade, 8 steps of 62.5 ms
 *   10,800 - 11,100            BLACK
 *   11,100 - ~128,300          CHART    the original's chart credits and
 *                                       title build-up ({@link ClassicIntro})
 *                              DONE     the title menu
 * </pre>
 *
 * <p><b>Measured</b> (captures {@code screenshots/intro-original}, stamps in
 * its INDEX.md): the original's logo turns at 10.12 frames/s (98.8 ms per
 * frame, residuals within +-0.07 s over 084..103) and its subtitle starts
 * growing 11 logo frames in; the chart runs at 7.603 frames/s (131.52 ms
 * per frame, fitted over 52 captures 104..165, residuals within +-0.1 s,
 * the stamps' precision), its picture freezes at chart frame 769
 * (captures 156..165 are identical and show OPENGUY.049 = 769 - 720), and
 * OPENING.TXT's END row (frame 891) cuts to the title.  <b>Own choices</b>
 * (not captured): the 300 ms lead-in, the emblem length (the original's
 * logo ran at least ~9 s), the fade and the 300 ms black before the chart.
 *
 * <p>Callers pass wall-clock milliseconds (differences of
 * {@code System.nanoTime()}), never tick counts: a stalled thread drops
 * frames instead of stretching the show.
 */
final class ClassicIntroTimeline {

    /** The phases, in order. */
    enum Phase { BLACK, EMBLEM, FADE, CHART, DONE }

    /** Black lead-in before the emblem, ms. */
    static final long LEAD_MS = 300L;

    /** The emblem's frame rate (the original logo's, measured). */
    static final double EMBLEM_FPS = 10.12;

    /** Emblem frames before the subtitle starts growing (measured). */
    static final int SUBTITLE_DELAY_FRAMES = 11;

    /** The subtitle's last growth stage (29 stages, as MPSNAME.SS). */
    static final int SUBTITLE_STAGES = 28;

    /** When the fade starts and how long it takes, ms. */
    static final long FADE_START_MS = 10300L;
    static final long FADE_MS = 500L;

    /** Fade steps (brightness 7/8 .. 0/8). */
    static final int FADE_STEPS = 8;

    /** When chart frame 0 shows, ms. */
    static final long CHART_START_MS = 11100L;

    /** One chart frame, ms (7.603 fps, measured). */
    static final double CHART_FRAME_MS = 131.52;

    /** The chart frame whose picture the original freezes (measured). */
    static final int FREEZE_FRAME = 769;

    /** The chart frame of the title logo (OPENING.TXT, series 8). */
    static final int LOGO_FRAME = 767;

    /** OPENING.TXT's END frame, used until the script has been read. */
    static final int DEFAULT_END_FRAME = 891;


    /** A point of the show; immutable. */
    static final class State {

        final Phase phase;
        /** The emblem frame counter (EMBLEM, FADE), else 0. */
        final int emblemFrame;
        /** The subtitle's growth stage 0..28 (EMBLEM, FADE), else 0. */
        final int subtitleK;
        /** Brightness in eighths: 8 = full, 7..0 during the fade. */
        final int level;
        /** The chart frame to render, already capped at FREEZE (CHART), else 0. */
        final int chartFrame;

        State(Phase phase, int emblemFrame, int subtitleK, int level, int chartFrame) {
            this.phase = phase;
            this.emblemFrame = emblemFrame;
            this.subtitleK = subtitleK;
            this.level = level;
            this.chartFrame = chartFrame;
        }

        /** @return Whether the intro is over. */
        boolean done() {
            return this.phase == Phase.DONE;
        }

        /** Whether two states paint the same picture. */
        boolean samePicture(State o) {
            return o != null && o.phase == this.phase
                && o.emblemFrame % ClassicEmblem.PHASES == this.emblemFrame % ClassicEmblem.PHASES
                && o.subtitleK == this.subtitleK && o.level == this.level
                && o.chartFrame == this.chartFrame;
        }

        @Override
        public String toString() {
            return "State[" + this.phase + " e" + this.emblemFrame + " k"
                + this.subtitleK + " l" + this.level + " f" + this.chartFrame + "]";
        }
    }


    private boolean skipped = false;
    private boolean hasChart = true;
    private int endFrame = DEFAULT_END_FRAME;


    /** End the intro now: every later {@link #at} is DONE. */
    void skip() {
        this.skipped = true;
    }

    /**
     * Whether the chart part will be shown.  False when the opening
     * material is missing or was not loaded in time: the fade then leads
     * straight to the title.
     */
    void setHasChart(boolean b) {
        this.hasChart = b;
    }

    /** @return Whether the chart part will be shown. */
    boolean hasChart() {
        return this.hasChart;
    }

    /**
     * The END frame from the script (OPENING.TXT's -1 row).
     *
     * @param f The frame; values below {@link #FREEZE_FRAME} + 1 are raised
     *     to it, so the freeze always shows.
     */
    void setEndFrame(int f) {
        this.endFrame = Math.max(FREEZE_FRAME + 1, f);
    }

    /** @return When the intro is done, ms. */
    long endMs() {
        return (this.hasChart)
            ? CHART_START_MS + (long) Math.ceil(this.endFrame * CHART_FRAME_MS)
            : FADE_START_MS + FADE_MS;
    }

    /** @return The emblem frame counter at {@code e} ms (e &gt;= LEAD_MS). */
    static int emblemFrame(long e) {
        return (int) Math.floor((e - LEAD_MS) * EMBLEM_FPS / 1000.0);
    }

    /** @return The subtitle stage at emblem frame {@code n}. */
    static int subtitleStage(int n) {
        return Math.max(0, Math.min(SUBTITLE_STAGES, n - SUBTITLE_DELAY_FRAMES));
    }

    /**
     * The state after {@code elapsedMs}.
     *
     * @param elapsedMs Milliseconds since the first picture (negative
     *     counts as 0).
     * @return The state.
     */
    State at(long elapsedMs) {
        final long e = Math.max(0L, elapsedMs);
        if (this.skipped) return new State(Phase.DONE, 0, 0, 8, 0);
        if (e < LEAD_MS) return new State(Phase.BLACK, 0, 0, 8, 0);
        if (e < FADE_START_MS + FADE_MS) {
            final int n = emblemFrame(e);
            if (e < FADE_START_MS) {
                return new State(Phase.EMBLEM, n, subtitleStage(n), 8, 0);
            }
            final int step = (int) Math.min(FADE_STEPS - 1,
                (e - FADE_START_MS) * FADE_STEPS / FADE_MS);
            return new State(Phase.FADE, n, subtitleStage(n),
                             FADE_STEPS - 1 - step, 0);
        }
        if (!this.hasChart) return new State(Phase.DONE, 0, 0, 8, 0);
        if (e < CHART_START_MS) return new State(Phase.BLACK, 0, 0, 8, 0);
        final int f = (int) Math.floor((e - CHART_START_MS) / CHART_FRAME_MS);
        if (f >= this.endFrame) return new State(Phase.DONE, 0, 0, 8, 0);
        return new State(Phase.CHART, 0, 0, 8, Math.min(f, FREEZE_FRAME));
    }
}
