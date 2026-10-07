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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import junit.framework.TestCase;


/**
 * Unit tests for the native-Java original-Colonization asset decoder.  Every
 * fixture is a hand-crafted synthetic stream built from the documented
 * formats -- no copyrighted game data is used or required.
 */
public class ClassicAssetDecoderTest extends TestCase {

    // --- byte-buffer helpers -------------------------------------------------

    private static void putU16(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xFF);
        b[off + 1] = (byte) ((v >> 8) & 0xFF);
    }

    private static void putU32(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xFF);
        b[off + 1] = (byte) ((v >> 8) & 0xFF);
        b[off + 2] = (byte) ((v >> 16) & 0xFF);
        b[off + 3] = (byte) ((v >> 24) & 0xFF);
    }

    /** Assemble a MADSPACK 2.0 file from verbatim (uncompressed) parts. */
    private static byte[] madspack(byte[]... parts) {
        int total = 16 + 0xA0;
        for (byte[] p : parts) total += p.length;
        byte[] out = new byte[total];
        byte[] magic = "MADSPACK 2.0".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(magic, 0, out, 0, magic.length);
        putU16(out, 12, 0x1A);                 // marker
        putU16(out, 14, parts.length);         // part count
        int hp = 16;
        int dp = 16 + 0xA0;
        for (byte[] p : parts) {
            putU16(out, hp, 0);                 // flag: uncompressed
            putU32(out, hp + 2, p.length);      // size
            putU32(out, hp + 6, p.length);      // csize == size
            hp += 10;
            System.arraycopy(p, 0, out, dp, p.length);
            dp += p.length;
        }
        return out;
    }

    // --- tests ---------------------------------------------------------------

    public void testMadsPackSplitsParts() {
        byte[] a = { 1, 2, 3 };
        byte[] b = { 4, 5, 6, 7 };
        List<byte[]> parts = MadsPack.read(madspack(a, b));
        assertEquals(2, parts.size());
        assertTrue(java.util.Arrays.equals(a, parts.get(0)));
        assertTrue(java.util.Arrays.equals(b, parts.get(1)));
    }

    public void testMadsPackRejectsBadMagic() {
        byte[] junk = new byte[200];
        try {
            MadsPack.read(junk);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    public void testPaletteVgaScaling() {
        byte[] part = new byte[768];
        part[0] = 0;    // R: 0   -> 0
        part[1] = 63;   // G: 63  -> 255
        part[2] = 32;   // B: 32  -> 130 (DOSBox: (32<<2)|(32>>4))
        Palette pal = Palette.readCol(part);
        assertEquals(0xFF00FF82, pal.argb[0]);
    }

    // --- bitmap fonts (.FF) --------------------------------------------------

    /**
     * Build a decompressed .FF part.  {@code glyphSpec} rows are
     * {@code {code, width, v(0,0), v(1,0), ..., v(w-1,h-1)}} (row-major
     * values 0..3); every glyph is packed 2 bpp, high bits first, at the
     * running data offset starting at 386.  Public because
     * {@code ClassicFontTest} builds its synthetic font with it too.
     */
    public static byte[] ffPart(int height, int maxWidth, int[][] glyphSpec) {
        java.io.ByteArrayOutputStream data = new java.io.ByteArrayOutputStream();
        byte[] head = new byte[FfDecoder.GLYPH_BASE];
        head[0] = (byte) height;
        head[1] = (byte) maxWidth;
        int off = FfDecoder.GLYPH_BASE;
        for (int c = 1; c < 128; c++) putU16(head, 130 + 2 * (c - 1), off);
        for (int[] g : glyphSpec) {
            int c = g[0], w = g[1];
            int bpr = (w - 1) / 4 + 1;
            head[2 + c - 1] = (byte) w;
            putU16(head, 130 + 2 * (c - 1), off);
            for (int y = 0; y < height; y++) {
                byte[] row = new byte[bpr];
                for (int x = 0; x < w; x++) {
                    row[x / 4] |= (byte) (g[2 + y * w + x] << (6 - 2 * (x % 4)));
                }
                data.write(row, 0, bpr);
            }
            off += height * bpr;
        }
        putU16(head, 384, off);
        byte[] out = new byte[off];
        System.arraycopy(head, 0, out, 0, head.length);
        byte[] d = data.toByteArray();
        System.arraycopy(d, 0, out, head.length, d.length);
        return out;
    }

    /** Height 2: 'A' (65) width 5 with all four values, ' ' (32) width 2. */
    private static byte[] sampleFont() {
        return ffPart(2, 5, new int[][] {
            { 65, 5,  1, 2, 3, 0, 1,   0, 0, 0, 0, 3 },
            { 32, 2,  0, 0,   0, 0 },
        });
    }

    public void testFfDecodeGlyphs() {
        byte[] part = sampleFont();
        // The packed rows of 'A' are exactly the documented bytes.
        assertEquals((byte) 0x6C, part[386]);
        assertEquals((byte) 0x40, part[387]);
        assertEquals((byte) 0x00, part[388]);
        assertEquals((byte) 0xC0, part[389]);
        FfDecoder.Font f = FfDecoder.decode(madspack(part));
        assertEquals(2, f.height);
        assertEquals(5, f.maxWidth);
        assertEquals(5, f.width(65));
        assertEquals(0, f.width(66));
        assertEquals(2, f.width(32));
        assertEquals(1, f.pixel(65, 0, 0));
        assertEquals(2, f.pixel(65, 1, 0));
        assertEquals(3, f.pixel(65, 2, 0));
        assertEquals(0, f.pixel(65, 3, 0));
        assertEquals(1, f.pixel(65, 4, 0));   // x=4 lives in the second byte
        assertEquals(0, f.pixel(65, 0, 1));
        assertEquals(3, f.pixel(65, 4, 1));
        assertEquals(0, f.pixel(65, 5, 0));   // beyond the width
        assertEquals(2, f.glyphCount());
    }

    public void testFfRejectsTruncatedGlyph() {
        byte[] part = sampleFont();
        putU16(part, 130 + 2 * (65 - 1), part.length - 1);   // overruns
        try {
            FfDecoder.decodePart(part);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    public void testFfAtlasRoundTrip() throws Exception {
        FfDecoder.Font f = FfDecoder.decodePart(sampleFont());
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(FfDecoder.toAtlas(f), "png", bos);
        BufferedImage img = javax.imageio.ImageIO.read(
            new java.io.ByteArrayInputStream(bos.toByteArray()));
        assertEquals(16 * 5, img.getWidth());
        assertEquals(8 * 2, img.getHeight());
        assertTrue(img.getColorModel() instanceof java.awt.image.IndexColorModel);
        assertEquals(4, ((java.awt.image.IndexColorModel) img.getColorModel())
            .getMapSize());
        int ox = (65 % 16) * 5, oy = (65 / 16) * 2;
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 5; x++) {
                assertEquals("pixel " + x + "," + y, f.pixel(65, x, y),
                    img.getRaster().getSample(ox + x, oy + y, 0));
            }
        }
        net.sf.freecol.common.resources.PropertyList pl
            = new net.sf.freecol.common.resources.PropertyList(FfDecoder.metrics(f));
        assertEquals(1, pl.getInt("format"));
        assertEquals(2, pl.getInt("height"));
        assertEquals(5, pl.getInt("cell"));
        String[] widths = pl.getString("widths").split(";");
        assertEquals(128, widths.length);
        assertEquals("5", widths[65]);
        assertEquals("2", widths[32]);
        assertEquals("0", widths[0]);
    }

    public void testFabLiteralAndHalt() {
        // literal 'h', literal 'i', then HALT
        byte[] fab = {
            'F', 'A', 'B', 0x0C,
            0x0B, 0x00,             // control word: bits 1,1,0,1
            0x68, 0x69,             // 'h', 'i'
            0x00, 0x00, 0x00        // 01 A=0 B=0 C=0 -> HALT
        };
        Fab.Result r = Fab.decode(fab, 0);
        assertEquals("hi", new String(r.data, StandardCharsets.US_ASCII));
        assertEquals(fab.length, r.consumed);
    }

    public void testFabBackReferenceCopy() {
        // literal 'a', then copy len=2 from -1 (=> "aaa"), then HALT
        byte[] fab = {
            'F', 'A', 'B', 0x0C,
            0x41, 0x00,             // control word: bits 1, 0,0, 0,0, 0,1
            0x61,                   // 'a'
            (byte) 0xFF,            // cmd00 back-reference -1
            0x00, 0x00, 0x00        // HALT
        };
        Fab.Result r = Fab.decode(fab, 0);
        assertEquals("aaa", new String(r.data, StandardCharsets.US_ASCII));
        assertEquals(fab.length, r.consumed);
    }

    public void testPikDecode() {
        // 2x1 screen, indices [0, 1]; palette 0=blue, 1=green
        byte[] header = new byte[8];
        putU16(header, 0, 1);       // height
        putU16(header, 2, 2);       // width
        byte[] image = { 0, 1 };
        byte[] palette = new byte[768];
        palette[2] = 63;            // index 0 -> blue
        palette[4] = 63;            // index 1 -> green
        BufferedImage img = PikDecoder.decode(madspack(header, image, palette), null);
        assertEquals(2, img.getWidth());
        assertEquals(1, img.getHeight());
        assertEquals(0xFF0000FF, img.getRGB(0, 0));
        assertEquals(0xFF00FF00, img.getRGB(1, 0));
    }

    public void testPikPaletteLessFallsBackToViceroy() {
        byte[] header = new byte[8];
        putU16(header, 0, 1);
        putU16(header, 2, 1);
        byte[] image = { 5 };
        byte[] viceroyRaw = new byte[1024];
        viceroyRaw[5 * 3] = 63;     // index 5 -> red
        Palette viceroy = Palette.readViceroy(viceroyRaw);
        // Only two parts (header + image), so the decoder must use the fallback.
        BufferedImage img = PikDecoder.decode(madspack(header, image), viceroy);
        assertEquals(0xFFFF0000, img.getRGB(0, 0));
    }

    /**
     * A 2x1 screen of indices 57 and 7, with or without its own palette:
     * 57 = (19,24,42) = #4D61AA (EUROPE.PIK's own market blue), 7 = #282828.
     */
    private static byte[] twoPixelPik(boolean ownPalette) {
        byte[] header = new byte[8];
        putU16(header, 0, 1);
        putU16(header, 2, 2);
        byte[] image = { 57, 7 };
        if (!ownPalette) return madspack(header, image);
        byte[] palette = new byte[768];
        palette[57 * 3] = 19;
        palette[57 * 3 + 1] = 24;
        palette[57 * 3 + 2] = 42;
        palette[7 * 3] = palette[7 * 3 + 1] = palette[7 * 3 + 2] = 10;
        return madspack(header, image, palette);
    }

    /** A VICEROY.PAL: 57 = (16,22,41) = #4159A6 (the game's), 7 = #515151. */
    private static byte[] gameViceroy() {
        byte[] raw = new byte[1024];
        raw[57 * 3] = 16;
        raw[57 * 3 + 1] = 22;
        raw[57 * 3 + 2] = 41;
        raw[7 * 3] = raw[7 * 3 + 1] = raw[7 * 3 + 2] = 20;
        return raw;
    }

    /**
     * W22p: a screen the original draws without loading its palette is
     * decoded under VICEROY.PAL, every other one under its own; the
     * two-argument decode is the latter.  Without VICEROY.PAL such a
     * screen cannot be decoded.
     */
    public void testPikDecodeWithTheGamePalette() {
        Palette viceroy = Palette.readViceroy(gameViceroy());
        byte[] file = twoPixelPik(true);
        BufferedImage own = PikDecoder.decode(file, viceroy, false);
        assertEquals(0xFF4D61AA, own.getRGB(0, 0));
        assertEquals(0xFF282828, own.getRGB(1, 0));
        BufferedImage game = PikDecoder.decode(file, viceroy, true);
        assertEquals(0xFF4159A6, game.getRGB(0, 0));
        assertEquals(0xFF515151, game.getRGB(1, 0));
        assertSamePixels("two-argument decode", own, PikDecoder.decode(file, viceroy));
        assertSamePixels("own palette, no VICEROY", own, PikDecoder.decode(file, null, false));
        // Palette-less: VICEROY.PAL either way.
        assertSamePixels("palette-less", game,
                         PikDecoder.decode(twoPixelPik(false), viceroy, true));
        assertSamePixels("palette-less, own asked", game,
                         PikDecoder.decode(twoPixelPik(false), viceroy, false));
        try {
            PikDecoder.decode(file, null, true);
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // ok
        }
    }

    /** Only EUROPE.PIK is drawn under the game palette (W22p), any case. */
    public void testDrawnWithGamePalette() {
        assertTrue(PikDecoder.drawnWithGamePalette("EUROPE.PIK"));
        assertTrue(PikDecoder.drawnWithGamePalette("europe.pik"));
        for (String name : new String[] { "COLONY.PIK", "CCBKGD.PIK", "WOODPANL.PIK",
                                          "REPORT1.PIK", "NATIONS.PIK", "OPENMENU.PIK",
                                          "LEVN0001.PIK", "EUROPE.SS", "" }) {
            assertFalse(name, PikDecoder.drawnWithGamePalette(name));
        }
        assertFalse(PikDecoder.drawnWithGamePalette(null));
        assertEquals(1, PikDecoder.GAME_PALETTE_PIKS.size());
    }

    public void testSsDecodeSpriteWithTransparency() {
        // 2x1 sprite: pixel 0 = colour index 1 (red), pixel 1 = transparent.
        byte[] header = new byte[152];
        header[0] = 0;              // mode 0 (raw)
        header[0x0C] = 1;           // pflag -> "col" palette
        putU16(header, 0x26, 1);    // nsprites = 1

        byte[] spriteHeaders = new byte[16];
        putU32(spriteHeaders, 0, 0);   // start offset
        putU32(spriteHeaders, 4, 5);   // length
        putU16(spriteHeaders, 8, 2);   // width padded
        putU16(spriteHeaders, 10, 1);  // height padded
        putU16(spriteHeaders, 12, 2);  // width
        putU16(spriteHeaders, 14, 1);  // height

        byte[] palette = new byte[768];
        palette[3] = 63;               // index 1 -> red

        // FE (pixel) mode: index 1, index 0xFD (transparent), end line, end image
        byte[] pixels = { (byte) 0xFE, 0x01, (byte) 0xFD, (byte) 0xFF, (byte) 0xFC };

        List<BufferedImage> frames = SsDecoder.decode(
            madspack(header, spriteHeaders, palette, pixels));
        assertEquals(1, frames.size());
        BufferedImage img = frames.get(0);
        assertEquals(2, img.getWidth());
        assertEquals(1, img.getHeight());
        assertEquals(0xFFFF0000, img.getRGB(0, 0));   // opaque red
        assertEquals(0x00000000, img.getRGB(1, 0));   // transparent
    }


    // --- index sheets (M1c design 10 §4, W6e) --------------------------------

    /**
     * A synthetic {@code .SS} file: mode 0 (raw), a "col" palette, one
     * sprite per {@code sprites} entry of the size {@code sizes[i]}.
     * Public because {@code ClassicIndexSheetTest} builds its sheets from
     * it too.
     *
     * @param colPalette 768 bytes of 6-bit R G B.
     * @param sizes {w, h} per sprite.
     * @param sprites The linemode commands per sprite.
     * @return The file.
     */
    public static byte[] ssFile(byte[] colPalette, int[][] sizes, byte[]... sprites) {
        byte[] header = new byte[152];
        header[0] = 0;                          // mode 0 (raw)
        header[0x0C] = 1;                       // pflag -> "col" palette
        putU16(header, 0x26, sprites.length);
        byte[] spriteHeaders = new byte[16 * sprites.length];
        java.io.ByteArrayOutputStream pixels = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < sprites.length; i++) {
            putU32(spriteHeaders, 16 * i, pixels.size());
            putU32(spriteHeaders, 16 * i + 4, sprites[i].length);
            putU16(spriteHeaders, 16 * i + 8, 100 + i);     // anchor x
            putU16(spriteHeaders, 16 * i + 10, 50);         // anchor y
            putU16(spriteHeaders, 16 * i + 12, sizes[i][0]);
            putU16(spriteHeaders, 16 * i + 14, sizes[i][1]);
            pixels.write(sprites[i], 0, sprites[i].length);
        }
        return madspack(header, spriteHeaders, colPalette, pixels.toByteArray());
    }

    /** A "col" palette whose used entries are all different colours. */
    public static byte[] colPalette() {
        byte[] p = new byte[768];
        for (int i = 0; i < 256; i++) {
            p[i * 3] = (byte) (i % 64);
            p[i * 3 + 1] = (byte) ((i * 5) % 64);
            p[i * 3 + 2] = (byte) ((i * 11 + 7) % 64);
        }
        return p;
    }

    private static byte[] b(int... v) {
        byte[] out = new byte[v.length];
        for (int i = 0; i < v.length; i++) out[i] = (byte) v[i];
        return out;
    }

    /**
     * Four sprites that reach every corner of the linemode decoder: runs in
     * both modes, an explicit transparent run, a line ended early, a whole
     * background line, a run past the right edge, a sprite that stops
     * before its last lines, and a 0x0 sprite.
     */
    public static final int[][] SAMPLE_SIZES = { { 5, 4 }, { 3, 3 }, { 0, 0 }, { 2, 2 } };

    /** The commands of {@link #SAMPLE_SIZES}. */
    public static byte[][] sampleSprites() {
        return new byte[][] {
            b(0xFE, 0x01, 0xFE, 0x03, 0x02, 0xFF,       // 1, 2, 2, 2, line end
              0xFD, 0x02, 0x05, 0x03, 0xFD, 0xFF,       // 5, 5, three transparent
              0xFF,                                     // a background line
              0xFE, 0xFE, 0x07, 0x09, 0xFF,             // 7 x 9 into 5 columns
              0xFC),
            b(0xFE, 0x04, 0xFF, 0xFC),                  // stops after line 0
            b(0xFC),                                    // 0x0
            b(0xFE, 0x00, 0x78, 0xFF,                   // index 0 and 120
              0xFD, 0x02, 0x7F, 0xFF, 0xFC)             // 127, 127
        };
    }

    /**
     * The RGBA decode of an {@code .SS} file as it was before the indices
     * were kept (commit 5739cfc40, {@code SsDecoder.decode}), verbatim: the
     * reference the PNGs must keep matching.
     */
    private static List<BufferedImage> legacyDecode(byte[] file) {
        List<byte[]> parts = MadsPack.read(file);
        byte[] header = parts.get(0);
        int mode = Bytes.u8(header, 0);
        int pflag = Bytes.u8(header, 0x0C);
        int nsprites = Bytes.u16(header, 0x26);
        byte[] spriteHeaders = parts.get(1);
        Palette pal = (pflag != 0)
            ? Palette.readCol(parts.get(2))
            : Palette.readRex(parts.get(2));
        byte[] pixels = parts.get(3);
        List<BufferedImage> frames = new java.util.ArrayList<>(nsprites);
        for (int i = 0; i < nsprites; i++) {
            int base = i * 16;
            int startOffset = (int) Bytes.u32(spriteHeaders, base);
            int length = (int) Bytes.u32(spriteHeaders, base + 4);
            int width = Bytes.u16(spriteHeaders, base + 12);
            int height = Bytes.u16(spriteHeaders, base + 14);
            byte[] data = (mode == 0)
                ? Arrays.copyOfRange(pixels, startOffset, startOffset + length)
                : Fab.decode(pixels, startOffset).data;
            if (width == 0 || height == 0) {
                BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
                img.setRGB(0, 0, pal.argb[0]);
                frames.add(img);
                continue;
            }
            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            int k = 0, x = 0, y = 0;
            int lm = data[k++] & 0xFF;
            while (true) {
                if (lm == 0xFF) {
                    while (x < width) legacyPut(img, x++, y, 0);
                    x = 0;
                    y++;
                    lm = data[k++] & 0xFF;
                } else if (lm == 0xFC) {
                    break;
                } else {
                    int c = data[k++] & 0xFF;
                    if (c == 0xFF) {
                        while (x < width) legacyPut(img, x++, y, 0);
                        x = 0;
                        y++;
                        lm = data[k++] & 0xFF;
                    } else if (lm == 0xFE) {
                        if (c == 0xFE) {
                            int runLen = data[k++] & 0xFF;
                            int ci = data[k++] & 0xFF;
                            for (int n = 0; n < runLen; n++) {
                                legacyPut(img, x++, y, (ci == 0xFD) ? 0 : pal.argb[ci]);
                            }
                        } else {
                            legacyPut(img, x++, y, (c == 0xFD) ? 0 : pal.argb[c]);
                        }
                    } else if (lm == 0xFD) {
                        int ci = data[k++] & 0xFF;
                        for (int n = 0; n < c; n++) {
                            legacyPut(img, x++, y, (ci == 0xFD) ? 0 : pal.argb[ci]);
                        }
                    } else {
                        throw new IllegalStateException("unknown SS linemode: " + lm);
                    }
                }
            }
            frames.add(img);
        }
        return frames;
    }

    private static void legacyPut(BufferedImage img, int x, int y, int argb) {
        if (x >= 0 && x < img.getWidth() && y >= 0 && y < img.getHeight()) {
            img.setRGB(x, y, argb);
        }
    }

    /** A PNG write and read back, as the pack holds an image. */
    private static BufferedImage viaPng(BufferedImage img) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", bos);
        return javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bos.toByteArray()));
    }

    private static void assertSamePixels(String what, BufferedImage a, BufferedImage b) {
        assertEquals(what + " width", a.getWidth(), b.getWidth());
        assertEquals(what + " height", a.getHeight(), b.getHeight());
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                assertEquals(what + " pixel " + x + "," + y, a.getRGB(x, y), b.getRGB(x, y));
            }
        }
    }

    /**
     * The index decode keeps 0xFD as the transparent marker, pre-fills
     * every pixel with it (the rest of a line ended early, the lines after
     * the stop command), keeps the 0x0 sprite as one pixel of index 0, and
     * mapped through the file's palette gives exactly the PNG pixels of the
     * decode before W6e (design 10 §4 item 1; Critic 8: pixels, not bytes).
     */
    public void testDecodeIndexedKeepsTheIndicesAndThePngs() throws Exception {
        byte[] file = ssFile(colPalette(), SAMPLE_SIZES, sampleSprites());
        List<SsDecoder.IndexedFrame> idx = SsDecoder.decodeIndexed(file);
        assertEquals(4, idx.size());
        final int T = SsDecoder.TRANSPARENT_INDEX;
        assertEquals(0xFD, T);
        int[][] expected = {
            { 1, 2, 2, 2, T,   5, 5, T, T, T,   T, T, T, T, T,   9, 9, 9, 9, 9 },
            { 4, T, T,   T, T, T,   T, T, T },
            { 0 },
            { 0, 120,   127, 127 }
        };
        for (int f = 0; f < 4; f++) {
            SsDecoder.IndexedFrame fr = idx.get(f);
            assertEquals("frame " + f, Math.max(1, SAMPLE_SIZES[f][0]), fr.w);
            assertEquals("frame " + f, Math.max(1, SAMPLE_SIZES[f][1]), fr.h);
            assertEquals(fr.w * fr.h, fr.idx.length);
            for (int i = 0; i < expected[f].length; i++) {
                assertEquals("frame " + f + " px " + i, expected[f][i],
                             fr.index(i % fr.w, i / fr.w));
            }
        }

        // Through the palette: the decode before W6e, pixel for pixel, also
        // after a PNG round trip; toImage is what decode does.
        Palette pal = SsDecoder.palette(file);
        assertEquals(Palette.readCol(colPalette()).argb[5], pal.argb[5]);
        List<BufferedImage> legacy = legacyDecode(file);
        List<BufferedImage> now = SsDecoder.decode(file);
        assertEquals(legacy.size(), now.size());
        for (int f = 0; f < legacy.size(); f++) {
            assertSamePixels("decode " + f, legacy.get(f), now.get(f));
            assertSamePixels("toImage " + f, legacy.get(f),
                             SsDecoder.toImage(idx.get(f), pal));
            assertSamePixels("png " + f, viaPng(legacy.get(f)), viaPng(now.get(f)));
        }
        // Transparent exactly where the index is 0xFD; frame 2 is opaque.
        assertEquals(0, now.get(0).getRGB(4, 0));
        assertEquals(0, now.get(1).getRGB(2, 2));
        assertEquals(pal.argb[0], now.get(2).getRGB(0, 0));
    }

    /**
     * The index sheet's documented layout ({@code ssidx/<NAME>.idx}):
     * "CSSI", u8 version 1, u16 frames, then per frame u16 w, u16 h and the
     * w x h index bytes, all little-endian.
     */
    public void testIndexSheetLayout() {
        List<SsDecoder.IndexedFrame> frames = SsDecoder.decodeIndexed(
            ssFile(colPalette(), SAMPLE_SIZES, sampleSprites()));
        byte[] sheet = ClassicAssetConverter.encodeIndexSheet(frames);
        assertEquals("CSSI", new String(sheet, 0, 4, StandardCharsets.US_ASCII));
        assertEquals(ClassicAssetConverter.INDEX_VERSION, sheet[4]);
        assertEquals(1, ClassicAssetConverter.INDEX_VERSION);
        assertEquals(4, Bytes.u16(sheet, 5));
        int p = 7;
        for (SsDecoder.IndexedFrame f : frames) {
            assertEquals(f.w, Bytes.u16(sheet, p));
            assertEquals(f.h, Bytes.u16(sheet, p + 2));
            assertTrue(Arrays.equals(f.idx, Arrays.copyOfRange(sheet, p + 4,
                                                               p + 4 + f.w * f.h)));
            p += 4 + f.w * f.h;
        }
        assertEquals(sheet.length, p);
        // 7 + (4 + 20) + (4 + 9) + (4 + 1) + (4 + 4)
        assertEquals(57, sheet.length);
        assertEquals(7, ClassicAssetConverter.encodeIndexSheet(List.of()).length);
    }

    /**
     * A conversion of a small fake install writes, beside the PNGs, the
     * index sheet of every SS file, the game palette as 768 8-bit bytes,
     * CYCLE.DAT unchanged and WOODCUT.TXT among the texts.
     */
    public void testConverterWritesTheIndexPipeline() throws Exception {
        final Path install = Files.createTempDirectory("classic-install");
        final Path out = install.resolve("pack");
        try {
            byte[] viceroy = new byte[1024];
            viceroy[120 * 3] = 17;             // R 17 -> 0x45 (DOSBox)
            viceroy[120 * 3 + 2] = 63;         // B 63 -> 0xFF
            Files.write(install.resolve("VICEROY.PAL"), viceroy);
            byte[] ss = ssFile(colPalette(), SAMPLE_SIZES, sampleSprites());
            Files.write(install.resolve("TEST.SS"), ss);
            byte[] cycle = b(0x01, 0x00, 0x08, 0x3D, 0x78, 0x23, 0x74, 0x10);
            Files.write(install.resolve("CYCLE.DAT"), cycle);
            Files.write(install.resolve("WOODCUT.TXT"), b(';', '\r', '\n'));
            ClassicAssetConverter.main(new String[] {
                    "--install", install.toString(), "--out", out.toString() });

            assertTrue(Arrays.equals(ClassicAssetConverter.encodeIndexSheet(
                        SsDecoder.decodeIndexed(ss)),
                    Files.readAllBytes(out.resolve("ssidx").resolve("TEST.SS.idx"))));
            byte[] rgb = Files.readAllBytes(out.resolve(ClassicAssetConverter.PALETTE_FILE));
            assertEquals(768, rgb.length);
            assertEquals(0x45, rgb[120 * 3] & 0xFF);
            assertEquals(0x00, rgb[120 * 3 + 1] & 0xFF);
            assertEquals(0xFF, rgb[120 * 3 + 2] & 0xFF);
            assertTrue(Arrays.equals(cycle, Files.readAllBytes(
                        out.resolve(ClassicAssetConverter.CYCLE_FILE))));
            assertTrue(Files.isRegularFile(out.resolve("text").resolve("WOODCUT.TXT")));
            List<BufferedImage> legacy = legacyDecode(ss);
            for (int f = 0; f < legacy.size(); f++) {
                assertSamePixels("pack png " + f, legacy.get(f), javax.imageio.ImageIO.read(
                        out.resolve("resources").resolve("images").resolve("ss")
                        .resolve(String.format("TEST.SS.%03d.png", f)).toFile()));
            }
            // Without CYCLE.DAT the conversion still succeeds (a warning).
            Files.delete(install.resolve("CYCLE.DAT"));
            assertFalse(ClassicAssetConverter.copyCycleTable(install,
                    out.resolve("other").resolve("CYCLE.DAT")));
        } finally {
            try (Stream<Path> s = Files.walk(install)) {
                s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    /**
     * W22p in the converter: of two screens with the same bytes,
     * EUROPE.PIK's PNG has the game palette's colours and the other one's
     * its own palette's.
     */
    public void testConverterDrawsEuropeUnderTheGamePalette() throws Exception {
        final Path install = Files.createTempDirectory("classic-install");
        final Path out = install.resolve("pack");
        try {
            Files.write(install.resolve("VICEROY.PAL"), gameViceroy());
            Files.write(install.resolve("EUROPE.PIK"), twoPixelPik(true));
            Files.write(install.resolve("OTHER.PIK"), twoPixelPik(true));
            ClassicAssetConverter.main(new String[] {
                    "--install", install.toString(), "--out", out.toString() });

            final Path pik = out.resolve("resources").resolve("images").resolve("pik");
            BufferedImage europe = javax.imageio.ImageIO.read(
                pik.resolve("EUROPE.PIK.png").toFile());
            BufferedImage other = javax.imageio.ImageIO.read(
                pik.resolve("OTHER.PIK.png").toFile());
            assertEquals(0xFF4159A6, europe.getRGB(0, 0));
            assertEquals(0xFF515151, europe.getRGB(1, 0));
            assertEquals(0xFF4D61AA, other.getRGB(0, 0));
            assertEquals(0xFF282828, other.getRGB(1, 0));
        } finally {
            try (Stream<Path> s = Files.walk(install)) {
                s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }


    /** The name the fake install below gives a text file (cases vary). */
    private static String onDisk(String name) {
        return name.equals("PEDIA.TXT") ? "pedia.txt"
            : name.equals("COLONY.TXT") ? "Colony.Txt" : name;
    }

    /**
     * The pack's {@code text/} gets every text file the screens read,
     * PEDIA.TXT and COLONY.TXT included (plan D0f): found ignoring case,
     * copied byte for byte under the upper-case name; a missing one is
     * skipped.
     */
    public void testCopyTextsCopiesThePackTexts() throws Exception {
        assertTrue(Arrays.asList(ClassicAssetConverter.TEXT_FILES)
            .containsAll(List.of("GAME.TXT", "PEDIA.TXT", "COLONY.TXT")));
        final Path install = Files.createTempDirectory("classic-install");
        final Path text = install.resolve("pack").resolve("text");
        try {
            int written = 0;
            for (String name : ClassicAssetConverter.TEXT_FILES) {
                if (name.equals("PATH.DAT")) continue;          // a missing file
                Files.write(install.resolve(onDisk(name)), new byte[] {
                        0x1C, '\r', '\n', (byte) written });
                written++;
            }
            assertEquals(written, ClassicAssetConverter.copyTexts(install, text));
            final List<String> names;
            try (Stream<Path> s = Files.list(text)) {
                names = s.map(p -> p.getFileName().toString()).sorted()
                    .collect(Collectors.toList());
            }
            assertEquals("[COLONY.TXT, GAME.TXT, LABELS.TXT, MENU.TXT, NAMES.TXT,"
                + " OPENING.TXT, PEDIA.TXT, WOODCUT.TXT]", names.toString());
            for (String name : names) {
                assertTrue(name, Arrays.equals(
                    Files.readAllBytes(install.resolve(onDisk(name))),
                    Files.readAllBytes(text.resolve(name))));
            }
        } finally {
            try (Stream<Path> s = Files.walk(install)) {
                s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }
}
