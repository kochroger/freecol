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

import java.nio.charset.StandardCharsets;


/**
 * The frames of one original SS file as <b>palette indices</b>, read from
 * the pack's {@code ssidx/<NAME>.idx} ({@link ClassicPackFiles#indexSheet}).
 *
 * <p>Why indices next to the PNGs: the PNGs are coloured with each SS
 * file's own palette, and {@code TERRAIN.SS}'s is not the game's (entries
 * 121-126, the water's cycling colours, differ from {@code VICEROY.PAL};
 * M1c design 10 F1).  The map is therefore composed from the original
 * indices and drawn through the game palette at the current cycling phase
 * ({@link ClassicGamePalette}): cycling is a swap of the colour model, the
 * masks, fringes and coast pieces copy indices (so the cycling pixels in
 * them cycle too), and golden checks compare indices with the clips.
 *
 * <p>The file format is the converter's
 * ({@code ClassicAssetConverter.encodeIndexSheet}), little-endian:
 * {@code "CSSI"}, u8 version 1, u16 frame count, then per frame u16 width,
 * u16 height and width x height index bytes, row by row.  Index
 * {@link #TRANSPARENT} (0xFD) is a transparent pixel.  A 0x0 sprite is a
 * 1x1 frame of index 0, as its PNG.
 *
 * <p>Immutable after {@link #parse}; safe to share between threads.
 */
final class ClassicIndexSheet {

    /** The index of a transparent pixel. */
    static final int TRANSPARENT = 0xFD;

    /** The format's magic, its first four bytes. */
    static final String MAGIC = "CSSI";

    /** The format version read here. */
    static final int VERSION = 1;

    /** The SS file name, e.g. {@code TERRAIN.SS} (for messages). */
    private final String name;

    /** Frame sizes. */
    private final int[] widths, heights;

    /** Frame indices, {@code pixels[f][y * w + x]}. */
    private final byte[][] pixels;


    private ClassicIndexSheet(String name, int[] widths, int[] heights,
                              byte[][] pixels) {
        this.name = name;
        this.widths = widths;
        this.heights = heights;
        this.pixels = pixels;
    }

    /**
     * Read an index sheet.
     *
     * @param name The SS file name, e.g. {@code TERRAIN.SS}.
     * @param data The file contents.
     * @return The sheet.
     * @exception IllegalArgumentException if the data is not a version-1
     *     index sheet, is cut short or has bytes left over.
     */
    static ClassicIndexSheet parse(String name, byte[] data) {
        final int head = MAGIC.length() + 3;
        if (data == null || data.length < head
            || !MAGIC.equals(new String(data, 0, MAGIC.length(),
                                        StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException(name + ": not an index sheet");
        }
        final int version = data[MAGIC.length()] & 0xFF;
        if (version != VERSION) {
            throw new IllegalArgumentException(name + ": index sheet version "
                + version + ", expected " + VERSION);
        }
        final int n = u16(data, MAGIC.length() + 1);
        final int[] w = new int[n], h = new int[n];
        final byte[][] px = new byte[n][];
        int p = head;
        for (int f = 0; f < n; f++) {
            if (p + 4 > data.length) {
                throw new IllegalArgumentException(name + ": cut short in frame " + f);
            }
            w[f] = u16(data, p);
            h[f] = u16(data, p + 2);
            p += 4;
            final int len = w[f] * h[f];
            if (p + len > data.length) {
                throw new IllegalArgumentException(name + ": cut short in frame " + f);
            }
            px[f] = new byte[len];
            System.arraycopy(data, p, px[f], 0, len);
            p += len;
        }
        if (p != data.length) {
            throw new IllegalArgumentException(name + ": " + (data.length - p)
                + " bytes after the last frame");
        }
        return new ClassicIndexSheet(name, w, h, px);
    }

    private static int u16(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8);
    }


    /** @return The SS file name, e.g. {@code TERRAIN.SS}. */
    String name() {
        return this.name;
    }

    /** @return The number of frames. */
    int size() {
        return this.pixels.length;
    }

    /**
     * @param f The frame number (the PNG's {@code .NNN}).
     * @return Its width.
     * @exception IndexOutOfBoundsException if there is no such frame.
     */
    int width(int f) {
        return this.widths[f];
    }

    /**
     * @param f The frame number.
     * @return Its height.
     * @exception IndexOutOfBoundsException if there is no such frame.
     */
    int height(int f) {
        return this.heights[f];
    }

    /**
     * The index of one pixel.  Outside the frame is transparent, as it is
     * outside a sprite.
     *
     * @param f The frame number.
     * @param x The column.
     * @param y The row.
     * @return The index 0-255, {@link #TRANSPARENT} outside the frame.
     * @exception IndexOutOfBoundsException if there is no such frame.
     */
    int index(int f, int x, int y) {
        final int w = this.widths[f];
        if (x < 0 || y < 0 || x >= w || y >= this.heights[f]) return TRANSPARENT;
        return this.pixels[f][y * w + x] & 0xFF;
    }

    /**
     * A frame's indices, for composing in bulk.
     *
     * @param f The frame number.
     * @return {@code width(f) * height(f)} bytes, row by row: shared, do
     *     not modify.
     * @exception IndexOutOfBoundsException if there is no such frame.
     */
    byte[] pixels(int f) {
        return this.pixels[f];
    }

    @Override
    public String toString() {
        return "ClassicIndexSheet[" + this.name + ", " + size() + " frames]";
    }
}
