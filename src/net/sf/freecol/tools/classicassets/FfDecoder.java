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
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.util.List;


/**
 * Decoder for the original game's bitmap fonts ({@code *.FF}: FONTTINY,
 * FONTSMAL, FONTINTR, FONTKING, FONT-NP), plus the atlas and metrics writers
 * the {@link ClassicAssetConverter} uses to put them into the local pack.
 *
 * <p>Clean-room implementation, written from the byte layout as measured on
 * all five fonts of the user's own install: every glyph offset agrees with
 * the running sum of the glyph sizes (0 mismatches), the glyph sizes plus the
 * 386-byte header add up to the part size exactly, and no bit is set beyond a
 * glyph's width.
 *
 * <p>Each {@code .FF} file is a {@link MadsPack} container with a single
 * (FAB-compressed) part laid out as:
 * <pre>
 *   +0          u8      height (rows per glyph, the same for every glyph)
 *   +1          u8      maxWidth
 *   +2+c-1      u8      width of code c, for c = 1..127 (code 0 is empty)
 *   +129        u8      pad (always 0, ignored)
 *   +130+2(c-1) u16LE   offset of the rows of code c, from the part start
 *   +384        u16LE   end marker (= part length, "offset of code 128")
 *   +386        ...     glyph data: height rows of ceil(w/4) bytes each,
 *                       2 bits per pixel, high bits first (the leftmost
 *                       pixel is bits 7-6); value 0 is transparent
 * </pre>
 * Pixel values 1..3 are not colours: the game picks the colours per call
 * (FONTTINY only uses 1 = ink; FONTINTR uses 1 ink, 2 a background shade,
 * 3 a shadow).  So the atlas keeps the raw values as palette indices.
 */
public final class FfDecoder {

    /** Header size: 2 metric bytes + 128 width bytes + 128 offset words. */
    public static final int GLYPH_BASE = 2 + 128 + 256;

    /** Number of character codes a font covers (0..127). */
    public static final int CODES = 128;

    /** Atlas grid: 16 columns by 8 rows of cells, code c at (c%16, c/16). */
    public static final int ATLAS_COLS = 16;
    public static final int ATLAS_ROWS = 8;

    private FfDecoder() {}


    /** One decoded bitmap font: metrics plus the raw 2-bit pixel values. */
    public static final class Font {

        /** Height of every glyph cell (byte 0). */
        public final int height;

        /** Widest glyph (byte 1). */
        public final int maxWidth;

        private final int[] widths = new int[CODES];

        /** Per code: values 0..3, row-major, length height*width. */
        private final byte[][] pixels = new byte[CODES][];

        private Font(int height, int maxWidth) {
            this.height = height;
            this.maxWidth = maxWidth;
        }

        /** The advance (= glyph width) of {@code code}; 0 if out of range. */
        public int width(int code) {
            return (code < 0 || code >= CODES) ? 0 : this.widths[code];
        }

        /** The raw value 0..3 of a glyph pixel; 0 when out of range. */
        public int pixel(int code, int x, int y) {
            final int w = width(code);
            if (w <= 0 || x < 0 || x >= w || y < 0 || y >= this.height) return 0;
            return this.pixels[code][y * w + x];
        }

        /** The number of codes that carry a glyph (width &gt; 0). */
        public int glyphCount() {
            int n = 0;
            for (int w : this.widths) if (w > 0) n++;
            return n;
        }
    }


    /**
     * Decode a whole {@code .FF} file.
     *
     * @param ffFile The file contents (a MADSPACK container).
     * @return The decoded font.
     * @throws IllegalArgumentException if the file is not a valid font.
     */
    public static Font decode(byte[] ffFile) {
        final List<byte[]> parts = MadsPack.read(ffFile);
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("font container has no parts");
        }
        return decodePart(parts.get(0));
    }

    /**
     * Decode the (already decompressed) font part; see the class comment for
     * the layout.
     *
     * @param part The decompressed part.
     * @return The decoded font.
     * @throws IllegalArgumentException on a truncated or inconsistent part.
     */
    public static Font decodePart(byte[] part) {
        if (part.length < GLYPH_BASE) {
            throw new IllegalArgumentException("font part too small: "
                + part.length + " bytes");
        }
        final int height = Bytes.u8(part, 0);
        final int maxWidth = Bytes.u8(part, 1);
        if (height < 1 || height > 32) {
            throw new IllegalArgumentException("bad font height " + height);
        }
        // The end marker at +384 equals the part length in every original
        // font.  It is deliberately not required: the per-glyph bounds check
        // below is what actually guards the decode.

        final Font f = new Font(height, maxWidth);
        for (int c = 1; c < CODES; c++) {
            final int w = Bytes.u8(part, 2 + c - 1);
            if (w == 0) continue;
            final int off = Bytes.u16(part, 130 + 2 * (c - 1));
            final int bpr = (w - 1) / 4 + 1;
            final int size = height * bpr;
            if (off < GLYPH_BASE || off + size > part.length) {
                throw new IllegalArgumentException("glyph " + c + " (width "
                    + w + ") at offset " + off + " overruns the font part ("
                    + part.length + " bytes)");
            }
            final byte[] px = new byte[height * w];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < w; x++) {
                    final int b = part[off + y * bpr + x / 4] & 0xFF;
                    px[y * w + x] = (byte) ((b >> (6 - 2 * (x % 4))) & 3);
                }
            }
            f.widths[c] = w;
            f.pixels[c] = px;
        }
        return f;
    }

    /**
     * Render a font into one 2-bit palette atlas: 16x8 cells of
     * {@code maxWidth} x {@code height}, code c at cell (c%16, c/16), pixel
     * index = raw font value.  Index 0 is transparent; 1..3 are white and two
     * greys purely so the PNG stays readable by eye -- the runtime swaps in
     * its own colours per draw call.
     *
     * <p>One atlas per font (instead of one PNG per glyph) keeps the pack at
     * five extra files: FreeCol lists the whole directory for every image
     * resource it maps (about 2 s for ~480 glyph files), and a zero-width
     * glyph cannot be stored as an image at all.
     *
     * @param f The font.
     * @return A {@code TYPE_BYTE_BINARY} image with a 2-bit palette.
     */
    public static BufferedImage toAtlas(Font f) {
        final int cellW = Math.max(1, f.maxWidth);
        final int cellH = f.height;
        final byte[] r = { 0, (byte) 0xFF, (byte) 0xAA, 0x55 };
        final byte[] g = r.clone();
        final byte[] b = r.clone();
        final byte[] a = { 0, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF };
        final IndexColorModel icm = new IndexColorModel(2, 4, r, g, b, a);
        final BufferedImage img = new BufferedImage(ATLAS_COLS * cellW,
            ATLAS_ROWS * cellH, BufferedImage.TYPE_BYTE_BINARY, icm);
        final WritableRaster raster = img.getRaster();
        for (int c = 0; c < CODES; c++) {
            final int w = f.width(c);
            if (w <= 0) continue;
            final int ox = (c % ATLAS_COLS) * cellW;
            final int oy = (c / ATLAS_COLS) * cellH;
            for (int y = 0; y < cellH; y++) {
                for (int x = 0; x < w && x < cellW; x++) {
                    raster.setSample(ox + x, oy + y, 0, f.pixel(c, x, y));
                }
            }
        }
        return img;
    }

    /**
     * The metrics string stored next to the atlas as a quoted
     * {@code StringResource}:
     * {@code format=1,height=H,cell=W,widths=w0;w1;...;w127}.
     * Widths are ';'-separated because FreeCol's {@code PropertyList} splits
     * pairs on ',' and keys from values on '='.
     *
     * @param f The font.
     * @return The metrics string.
     */
    public static String metrics(Font f) {
        final StringBuilder sb = new StringBuilder();
        sb.append("format=1,height=").append(f.height)
          .append(",cell=").append(Math.max(1, f.maxWidth))
          .append(",widths=");
        for (int c = 0; c < CODES; c++) {
            if (c > 0) sb.append(';');
            sb.append(f.width(c));
        }
        return sb.toString();
    }
}
