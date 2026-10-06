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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.IntBinaryOperator;


/**
 * Synthetic index sheets for the terrain tests (no pack needed): the
 * original's four side masks at their measured positions (they are
 * measurements, not art: {@code 02-fixtures/w6e-census.txt}), "tag"
 * sheets whose every TERRAIN frame is one index (a fringe or blend then
 * names its sprite), and patterned sheets with cycling pixels.
 */
final class ClassicTerrainSheets {

    /** The opaque pixels {x, y} of PHYS0.SS.104-107 (N, E, S, W), 15 each. */
    static final int[][][] MASKS = {
        { { 1, 0 }, { 4, 0 }, { 6, 0 }, { 7, 0 }, { 10, 0 }, { 13, 0 }, { 15, 0 },
          { 3, 1 }, { 5, 1 }, { 8, 1 }, { 12, 1 }, { 0, 2 }, { 6, 2 }, { 10, 2 },
          { 14, 2 } },
        { { 13, 0 }, { 15, 1 }, { 14, 3 }, { 15, 4 }, { 14, 5 }, { 13, 6 }, { 15, 6 },
          { 15, 7 }, { 14, 8 }, { 13, 10 }, { 15, 10 }, { 14, 12 }, { 15, 13 },
          { 13, 14 }, { 15, 15 } },
        { { 1, 13 }, { 5, 13 }, { 9, 13 }, { 15, 13 }, { 3, 14 }, { 7, 14 }, { 10, 14 },
          { 12, 14 }, { 0, 15 }, { 2, 15 }, { 5, 15 }, { 8, 15 }, { 9, 15 }, { 11, 15 },
          { 14, 15 } },
        { { 0, 0 }, { 2, 1 }, { 0, 2 }, { 1, 3 }, { 0, 5 }, { 2, 5 }, { 1, 7 }, { 0, 8 },
          { 0, 9 }, { 2, 9 }, { 1, 10 }, { 0, 11 }, { 1, 12 }, { 0, 14 }, { 2, 15 } }
    };

    /** A tag sheet's TERRAIN frame f is index TAG + f. */
    static final int TAG = 16;

    /** A tag sheet's dark tile (PHYS0.SS.148) index. */
    static final int TAG_DARK = 1;

    static final int FD = ClassicIndexSheet.TRANSPARENT;


    private ClassicTerrainSheets() {}

    /**
     * A sheet of 16x16 frames.
     *
     * @param name The SS name.
     * @param frames The frame count.
     * @param px The index of frame f at cell offset k (y * 16 + x).
     * @return The sheet.
     */
    static ClassicIndexSheet sheet(String name, int frames, IntBinaryOperator px) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("CSSI".getBytes(StandardCharsets.US_ASCII));
        out.write(1);
        out.write(frames & 0xFF);
        out.write(frames >> 8);
        for (int f = 0; f < frames; f++) {
            out.write(16);
            out.write(0);
            out.write(16);
            out.write(0);
            for (int k = 0; k < 256; k++) out.write(px.applyAsInt(f, k));
        }
        return ClassicIndexSheet.parse(name, out.toByteArray());
    }

    /** @return Whether cell offset k lies in side d's mask. */
    static boolean inMask(int d, int k) {
        for (int[] p : MASKS[d]) {
            if (p[1] * 16 + p[0] == k) return true;
        }
        return false;
    }

    /**
     * A PHYS0.SS of 154 frames: the masks at 104-107, the dark tile at 148
     * from {@code dark}, the other frames from {@code other} (FD for none).
     */
    static ClassicIndexSheet phys(IntBinaryOperator dark, IntBinaryOperator other) {
        return sheet("PHYS0.SS", 154, (f, k) -> (f >= 104 && f <= 107)
            ? (inMask(f - 104, k) ? 0 : FD)
            : (f == 148) ? dark.applyAsInt(f, k) : other.applyAsInt(f, k));
    }

    /** @return TERRAIN.SS with frame f all index TAG + f. */
    static ClassicIndexSheet tagTerrain() {
        return sheet("TERRAIN.SS", 12, (f, k) -> TAG + f);
    }

    /** @return PHYS0.SS with the dark tile all TAG_DARK and no overlays. */
    static ClassicIndexSheet tagPhys() {
        return phys((f, k) -> TAG_DARK, (f, k) -> FD);
    }

    /** The sea lane's (frame 11) cycling pixels: offset k -> 120 + k % 8 when k % 7 == 0. */
    static boolean sparkle(int k) {
        return k % 7 == 0;
    }

    /**
     * TERRAIN.SS with a pattern: frame f at offset k is {@code 8f + (x + 2y) % 8}
     * (0-95), the sea lane (11) has cycling pixels at {@link #sparkle}, the
     * swamp (7) one at offset 17.
     */
    static ClassicIndexSheet patternTerrain() {
        return sheet("TERRAIN.SS", 12, (f, k) -> {
                if (f == 11 && sparkle(k)) return 120 + k % 8;
                if (f == 7 && k == 17) return 123;
                return 8 * f + ((k % 16) + 2 * (k / 16)) % 8;
            });
    }

    /**
     * PHYS0.SS with a pattern: the dark tile {@code 200 + k % 3}, overlay
     * frame f (not 104-107, 148) {@code 100 + f % 20} on the diagonal
     * {@code x == y}, 0 at (15,0) (index 0 is drawn black), FD elsewhere;
     * a river frame (1-31) has a cycling pixel at (8,8).
     */
    static ClassicIndexSheet patternPhys() {
        return phys((f, k) -> 200 + k % 3, (f, k) -> {
                final int x = k % 16, y = k / 16;
                if (f >= 1 && f <= 31 && x == 8 && y == 8) return 124;
                if (x == y) return 100 + f % 20;
                if (x == 15 && y == 0) return 0;
                return FD;
            });
    }
}
