/**
 *  Copyright (C) 2002-2024   The FreeCol Team
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

package net.sf.freecol.tools.classicassets;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * Decoder for {@code .SS} "shape set" sprite files -- multi-frame sets of
 * units, terrain, buildings and UI icons from Sid Meier's Colonization
 * (1994).  Clean-room implementation from the published format
 * documentation.
 *
 * An {@code .SS} is a MADSPACK with four parts: a header (part 0), a table of
 * per-sprite headers (part 1), a palette (part 2) and the packed pixel data
 * (part 3).  Each sprite is run-length encoded with a per-line "linemode"
 * command scheme; the palette index {@code 0xFD} is the transparent
 * background, decoded here to a fully transparent pixel.
 *
 * <p>The core decode is to palette indices ({@link #decodeIndexed}): the
 * classic UI composes its map from them and draws them through the game
 * palette at the current palette-cycling phase (M1c design 10 §4, item
 * W6e).  {@link #decode} is that decode mapped through the file's own
 * palette, so the PNGs are exactly what they were before the indices were
 * kept.
 */
public final class SsDecoder {

    private static final int PFLAG_OFFSET = 0x0C;
    private static final int NSPRITES_OFFSET = 0x26;
    private static final int SPRITE_HEADER_SIZE = 16;

    /** The palette index of the transparent background. */
    public static final int TRANSPARENT_INDEX = 0xFD;

    private static final int TRANSPARENT = 0x00000000;

    private SsDecoder() {}


    /**
     * One frame as palette indices: {@code w * h} bytes, row by row, with
     * {@link #TRANSPARENT_INDEX} for every pixel the sprite leaves
     * transparent (its background, the rest of a line it ends early, and
     * the lines it never reaches).
     */
    public static final class IndexedFrame {

        /** The size in pixels (a 0x0 sprite is a 1x1 frame of index 0). */
        public final int w, h;

        /** The indices, {@code idx[y * w + x]}. */
        public final byte[] idx;

        IndexedFrame(int w, int h, byte[] idx) {
            this.w = w;
            this.h = h;
            this.idx = idx;
        }

        /** @return The index at (x, y), 0-255. */
        public int index(int x, int y) {
            return this.idx[y * this.w + x] & 0xFF;
        }
    }


    /**
     * Decode every frame of an {@code .SS} file.
     *
     * @param file The whole {@code .SS} file contents.
     * @return The frames as ARGB images, in file order: {@link #decodeIndexed}
     *     through the file's own palette ({@link #toImage}).
     */
    public static List<BufferedImage> decode(byte[] file) {
        final List<byte[]> parts = MadsPack.read(file);
        final Palette pal = palette(parts);
        final List<BufferedImage> frames = new ArrayList<>();
        for (IndexedFrame f : decodeIndexed(parts)) frames.add(toImage(f, pal));
        return frames;
    }

    /**
     * Decode every frame of an {@code .SS} file to palette indices, with
     * {@link #TRANSPARENT_INDEX} kept as the transparent marker.
     *
     * @param file The whole {@code .SS} file contents.
     * @return The frames, in file order.
     */
    public static List<IndexedFrame> decodeIndexed(byte[] file) {
        return decodeIndexed(MadsPack.read(file));
    }

    /**
     * The file's own palette (part 2).  It is not always the game palette:
     * {@code TERRAIN.SS}'s differs from {@code VICEROY.PAL} in entries
     * 121-126, the cycling water colours (design 10 F1).
     *
     * @param file The whole {@code .SS} file contents.
     * @return The palette.
     */
    public static Palette palette(byte[] file) {
        return palette(MadsPack.read(file));
    }

    /**
     * A frame in colour: {@link #TRANSPARENT_INDEX} fully transparent, every
     * other index the palette's opaque entry.
     *
     * @param f The frame.
     * @param pal The palette.
     * @return A new ARGB image.
     */
    public static BufferedImage toImage(IndexedFrame f, Palette pal) {
        final BufferedImage img = new BufferedImage(f.w, f.h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < f.h; y++) {
            for (int x = 0; x < f.w; x++) {
                img.setRGB(x, y, colour(pal, f.index(x, y)));
            }
        }
        return img;
    }

    private static Palette palette(List<byte[]> parts) {
        final int pflag = Bytes.u8(parts.get(0), PFLAG_OFFSET);
        return (pflag != 0)
            ? Palette.readCol(parts.get(2))
            : Palette.readRex(parts.get(2));
    }

    private static List<IndexedFrame> decodeIndexed(List<byte[]> parts) {
        byte[] header = parts.get(0);
        int mode = Bytes.u8(header, 0);              // part-3 encoding: 0 raw, 1 FAB
        int nsprites = Bytes.u16(header, NSPRITES_OFFSET);

        byte[] spriteHeaders = parts.get(1);
        byte[] pixels = parts.get(3);

        List<IndexedFrame> frames = new ArrayList<>(nsprites);
        for (int i = 0; i < nsprites; i++) {
            int base = i * SPRITE_HEADER_SIZE;
            int startOffset = (int) Bytes.u32(spriteHeaders, base);
            int length = (int) Bytes.u32(spriteHeaders, base + 4);
            int width = Bytes.u16(spriteHeaders, base + 12);
            int height = Bytes.u16(spriteHeaders, base + 14);
            frames.add(decodeSprite(pixels, startOffset, length, width, height, mode));
        }
        return frames;
    }

    /**
     * The per-frame screen anchors that {@link #decode} drops: bytes 8-11
     * of each 16-byte sprite header hold the position of the sprite's
     * <em>bottom-centre</em> on the original 320x200 screen, as two
     * little-endian 16-bit values (read signed, so a sprite hanging off the
     * left or top edge would come out negative).
     *
     * <p>Why export them: the original composes its scenes from a backdrop
     * {@code .PIK} plus sprites that each know where they stand, so the
     * classic UI needs these numbers to put e.g. the king and the nation
     * banner of the audience screen exactly where the game does -- rather
     * than hard-coding positions measured from captures.  The top-left of a
     * frame of size {@code w x h} is {@code (ax - w/2, ay - h + 1)}
     * (integer division).  Verified on the audience captures
     * {@code opening_068}/{@code 039}: KING1 frame 0 has (ax,ay,w,h) =
     * (94,198,189,187), i.e. top-left (0,12); DUTCH1 gives (34,0), ENGLND1
     * (32,0); FRANCE1 (30,0) and SPAIN1 (35,0) follow from the same rule
     * (no capture shows them).
     *
     * @param file The whole {@code .SS} file contents.
     * @return One {@code {ax, ay, w, h}} per frame, in file order.
     */
    public static List<int[]> anchors(byte[] file) {
        List<byte[]> parts = MadsPack.read(file);
        int nsprites = Bytes.u16(parts.get(0), NSPRITES_OFFSET);
        byte[] spriteHeaders = parts.get(1);
        List<int[]> out = new ArrayList<>(nsprites);
        for (int i = 0; i < nsprites; i++) {
            int base = i * SPRITE_HEADER_SIZE;
            out.add(new int[] {
                (short) Bytes.u16(spriteHeaders, base + 8),
                (short) Bytes.u16(spriteHeaders, base + 10),
                Bytes.u16(spriteHeaders, base + 12),
                Bytes.u16(spriteHeaders, base + 14) });
        }
        return out;
    }

    /**
     * Decode one sprite to indices.  Every pixel starts as
     * {@link #TRANSPARENT_INDEX}, so the pixels the sprite never writes
     * (the rest of a line it ends early, the lines after its stop command)
     * are transparent, as they were in the ARGB decode; a zero-filled
     * array would turn them into index 0, which the coast pieces read as
     * "keep what is below" and the overlays as black (design 10, Critic 8).
     */
    private static IndexedFrame decodeSprite(byte[] pixels, int startOffset,
            int length, int width, int height, int mode) {
        byte[] data = (mode == 0)
            ? Arrays.copyOfRange(pixels, startOffset, startOffset + length)
            : Fab.decode(pixels, startOffset).data;

        // A 0x0 sprite is stored as a lone stop command; represent it as a
        // 1x1 frame of index 0 (PNG cannot hold a 0x0 image).
        if (width == 0 || height == 0) {
            return new IndexedFrame(1, 1, new byte[] { 0 });
        }

        final IndexedFrame img = new IndexedFrame(width, height, new byte[width * height]);
        Arrays.fill(img.idx, (byte) TRANSPARENT_INDEX);
        int k = 0;
        int x = 0;
        int y = 0;
        int lm = data[k++] & 0xFF;

        while (true) {
            if (lm == 0xFF) {
                // fill the rest of this line with background, then next line
                while (x < width) put(img, x++, y, TRANSPARENT_INDEX);
                x = 0;
                y++;
                lm = data[k++] & 0xFF;
            } else if (lm == 0xFC) {
                break;                              // end of image
            } else {
                int c = data[k++] & 0xFF;
                if (c == 0xFF) {
                    while (x < width) put(img, x++, y, TRANSPARENT_INDEX);
                    x = 0;
                    y++;
                    lm = data[k++] & 0xFF;
                } else if (lm == 0xFE) {
                    // pixel mode
                    if (c == 0xFE) {
                        int runLen = data[k++] & 0xFF;
                        int ci = data[k++] & 0xFF;
                        for (int n = 0; n < runLen; n++) put(img, x++, y, ci);
                    } else {
                        put(img, x++, y, c);
                    }
                } else if (lm == 0xFD) {
                    // multipixel mode: c is the run length, next byte the colour
                    int ci = data[k++] & 0xFF;
                    for (int n = 0; n < c; n++) put(img, x++, y, ci);
                } else {
                    throw new IllegalStateException("unknown SS linemode: " + lm);
                }
            }
        }
        return img;
    }

    private static int colour(Palette pal, int index) {
        return (index == TRANSPARENT_INDEX) ? TRANSPARENT : pal.argb[index];
    }

    private static void put(IndexedFrame img, int x, int y, int index) {
        if (x >= 0 && x < img.w && y >= 0 && y < img.h) {
            img.idx[y * img.w + x] = (byte) index;
        }
    }
}
