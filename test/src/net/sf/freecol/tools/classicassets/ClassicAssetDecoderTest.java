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
                + " OPENING.TXT, PEDIA.TXT]", names.toString());
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
