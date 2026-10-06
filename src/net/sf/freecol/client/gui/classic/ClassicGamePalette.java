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

import java.awt.image.IndexColorModel;


/**
 * The original's game palette {@code VICEROY.PAL} and its colour cycling:
 * the base palette B and, for each cycling phase p, the palette
 * {@code P_p} and an {@link IndexColorModel} to draw palette indices
 * ({@link ClassicIndexSheet}) through.
 *
 * <p><b>Phases</b> (M1c design 10 F3): entries 120-127 rotate, the rest
 * stay.  {@code P_p[120 + i] = B[120 + ((i - p) mod 8)]}; one step is
 * {@code p -> p + 1}, i.e. {@code new[120 + i] = old[120 + i - 1]}, and
 * {@code P_8 = P_0}.  Phase 0 is VICEROY's own order.  In the clips the
 * fog-start frame #19 is phase 0, its frame #0 phase 7 and the landfall
 * frame #0 phase 1 ({@link #phaseOf}).  At phase 0 entry 120 has the
 * colour of 56 and 127 that of 59, so a colour alone does not name the
 * index of a cycling pixel.
 *
 * <p><b>Speed:</b> a step every 35 game ticks of {@link ClassicSlide#STEP_MS}
 * ({@link #PERIOD_MS} = 575.05 ms; both clips measure 575.0-575.1 ms, F9),
 * the original's {@code CYCLE.DAT} ({@link Cycle}).  The clock itself is
 * item W6c.
 *
 * <p>The colour models have no alpha: every index is opaque (index 0xFD,
 * the sheets' transparent marker, never reaches a composed map, and would
 * show as VICEROY's magenta if it did).  Immutable; safe to share.
 */
final class ClassicGamePalette {

    /** The first cycling entry. */
    static final int CYCLE_FIRST = 120;

    /** The number of cycling entries, and of phases. */
    static final int CYCLE_COUNT = 8;

    /** Game ticks per cycling step ({@code CYCLE.DAT}: 0x23). */
    static final int CYCLE_TICKS = 35;

    /** The cycling step: 35 ticks of {@link ClassicSlide#STEP_MS}, 575.05 ms. */
    static final double PERIOD_MS = CYCLE_TICKS * ClassicSlide.STEP_MS;


    /**
     * One colour cycle as the original's {@code CYCLE.DAT} gives it: the
     * number of entries, the first one and the ticks per step.
     */
    static final class Cycle {

        /** The original's cycle, also the default without CYCLE.DAT. */
        static final Cycle DEFAULT = new Cycle(CYCLE_COUNT, CYCLE_FIRST, CYCLE_TICKS);

        /** Entries in the cycle (the number of phases), the first, ticks per step. */
        final int count, first, ticks;

        /**
         * @exception IllegalArgumentException unless {@code count >= 1},
         *     {@code first >= 0}, {@code first + count <= 256} and
         *     {@code ticks >= 1}.
         */
        Cycle(int count, int first, int ticks) {
            if (count < 1 || first < 0 || first + count > 256 || ticks < 1) {
                throw new IllegalArgumentException("bad cycle " + count + "@"
                    + first + "/" + ticks);
            }
            this.count = count;
            this.first = first;
            this.ticks = ticks;
        }

        /** @return The step in ms: {@code ticks * ClassicSlide.STEP_MS}. */
        double periodMs() {
            return this.ticks * ClassicSlide.STEP_MS;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Cycle)) return false;
            final Cycle c = (Cycle) o;
            return c.count == this.count && c.first == this.first
                && c.ticks == this.ticks;
        }

        @Override
        public int hashCode() {
            return (this.count * 257 + this.first) * 31 + this.ticks;
        }

        /** @return E.g. {@code 8@120/35} (count, first, ticks). */
        @Override
        public String toString() {
            return this.count + "@" + this.first + "/" + this.ticks;
        }
    }


    /** The base palette B, 0xRRGGBB. */
    private final int[] base;

    /** The cycle. */
    private final Cycle cycle;

    /** {@code P_p} for each phase, 0xRRGGBB. */
    private final int[][] phases;

    /** The colour model of each phase. */
    private final IndexColorModel[] models;


    /**
     * @param base The 256 entries of B, 0xRRGGBB (higher bits ignored).
     * @param cycle The cycle ({@link Cycle#DEFAULT} for the original's).
     * @exception IllegalArgumentException if {@code base} is not 256 long.
     */
    ClassicGamePalette(int[] base, Cycle cycle) {
        if (base == null || base.length != 256) {
            throw new IllegalArgumentException("a palette needs 256 entries");
        }
        this.base = new int[256];
        for (int i = 0; i < 256; i++) this.base[i] = base[i] & 0xFFFFFF;
        this.cycle = (cycle == null) ? Cycle.DEFAULT : cycle;
        final int n = this.cycle.count;
        this.phases = new int[n][];
        this.models = new IndexColorModel[n];
        for (int p = 0; p < n; p++) {
            final int[] e = this.base.clone();
            for (int i = 0; i < n; i++) {
                e[this.cycle.first + i] = this.base[this.cycle.first + Math.floorMod(i - p, n)];
            }
            this.phases[p] = e;
            final byte[] r = new byte[256], g = new byte[256], b = new byte[256];
            for (int i = 0; i < 256; i++) {
                r[i] = (byte) (e[i] >> 16);
                g[i] = (byte) (e[i] >> 8);
                b[i] = (byte) e[i];
            }
            this.models[p] = new IndexColorModel(8, 256, r, g, b);
        }
    }

    /**
     * The game palette of a pack: {@code palette/VICEROY.rgb} with the
     * cycle of {@code data/CYCLE.DAT} ({@link ClassicPackFiles#cycleSpec}).
     *
     * @param pack The pack, or null.
     * @return The palette, or null without a pack or palette file.
     */
    static ClassicGamePalette of(ClassicPackFiles pack) {
        final int[] b = (pack == null) ? null : pack.gamePalette();
        return (b == null) ? null : new ClassicGamePalette(b, pack.cycleSpec());
    }


    /** @return The cycle. */
    Cycle cycle() {
        return this.cycle;
    }

    /** @return The number of phases (8). */
    int phaseCount() {
        return this.cycle.count;
    }

    /**
     * @param p A phase; any int, taken modulo the phase count.
     * @return It in 0..phaseCount()-1.
     */
    int normalise(int p) {
        return Math.floorMod(p, this.cycle.count);
    }

    /** @return A copy of the base palette B, 0xRRGGBB. */
    int[] base() {
        return this.base.clone();
    }

    /**
     * @param p A phase (modulo the phase count).
     * @return A copy of {@code P_p}, 0xRRGGBB.
     */
    int[] rgb(int p) {
        return this.phases[normalise(p)].clone();
    }

    /**
     * @param p A phase (modulo the phase count).
     * @param index A palette index, 0-255.
     * @return {@code P_p[index]}, 0xRRGGBB.
     */
    int rgb(int p, int index) {
        return this.phases[normalise(p)][index];
    }

    /**
     * @param index A palette index.
     * @return Whether it cycles (120-127).
     */
    boolean cycles(int index) {
        return index >= this.cycle.first && index < this.cycle.first + this.cycle.count;
    }

    /**
     * The colour model to draw indices through at a phase: 8 bits, 256
     * entries, no alpha.  Shared, one per phase.
     *
     * @param p A phase (modulo the phase count).
     * @return The colour model of {@code P_p}.
     */
    IndexColorModel colorModel(int p) {
        return this.models[normalise(p)];
    }

    /**
     * The phase a palette is at: the phase whose cycling entries equal its
     * entries {@code first .. first + count - 1} (the rest are not looked
     * at).  For a clip frame's PLTE, and the recorder's record palette
     * (design 10 §9.2).
     *
     * @param entries At least {@code first + count} entries, 0xRRGGBB
     *     (higher bits ignored).
     * @return The lowest such phase, or -1 when none matches.
     */
    int phaseOf(int[] entries) {
        final int f = this.cycle.first, n = this.cycle.count;
        if (entries == null || entries.length < f + n) return -1;
        outer:
        for (int p = 0; p < n; p++) {
            for (int i = 0; i < n; i++) {
                if ((entries[f + i] & 0xFFFFFF) != this.phases[p][f + i]) continue outer;
            }
            return p;
        }
        return -1;
    }
}
