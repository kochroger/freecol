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

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;

import junit.framework.TestCase;

import net.sf.freecol.tools.classicassets.ClassicAssetConverter;
import net.sf.freecol.tools.classicassets.ClassicAssetDecoderTest;
import net.sf.freecol.tools.classicassets.SsDecoder;


/**
 * The index pipeline's client side (M1c design 10 §4, item W6e):
 * {@link ClassicIndexSheet} reads what the converter writes, and
 * {@link ClassicPackFiles} finds the sheets, the game palette, the colour
 * cycle and the alias table in a pack, or says "fallback" without them.
 * Synthetic packs only; the real pack is {@code ClassicIndexGoldenTest}'s.
 */
public class ClassicIndexSheetTest extends TestCase {

    private Path dir;

    @Override
    protected void setUp() throws IOException {
        this.dir = Files.createTempDirectory("classic-index");
    }

    @Override
    protected void tearDown() throws IOException {
        try (Stream<Path> s = Files.walk(this.dir)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    /** The sample SS file of the converter's tests. */
    private static byte[] sampleSs() {
        return ClassicAssetDecoderTest.ssFile(ClassicAssetDecoderTest.colPalette(),
            ClassicAssetDecoderTest.SAMPLE_SIZES, ClassicAssetDecoderTest.sampleSprites());
    }

    private static byte[] bytes(int... v) {
        final byte[] b = new byte[v.length];
        for (int i = 0; i < v.length; i++) b[i] = (byte) v[i];
        return b;
    }

    private void write(String rel, byte[] data) throws IOException {
        final Path p = this.dir.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.write(p, data);
    }

    private void write(String rel, String text) throws IOException {
        write(rel, text.getBytes(StandardCharsets.UTF_8));
    }

    private ClassicPackFiles pack() {
        return ClassicPackFiles.forDirectory(this.dir.toFile());
    }

    /** A complete index pipeline: two sheets, the palette, the cycle. */
    private void writePipeline(byte[] cycle) throws IOException {
        final byte[] sheet = ClassicAssetConverter.encodeIndexSheet(
            SsDecoder.decodeIndexed(sampleSs()));
        write("ssidx/TERRAIN.SS.idx", sheet);
        write("ssidx/PHYS0.SS.idx", sheet);
        final byte[] rgb = new byte[768];
        for (int i = 0; i < 768; i++) rgb[i] = (byte) (i * 7);
        write("palette/VICEROY.rgb", rgb);
        if (cycle != null) write("data/CYCLE.DAT", cycle);
    }

    /** Counts the warnings of {@link ClassicPackFiles}' logger. */
    private static final class Warnings extends Handler {
        final List<String> seen = new ArrayList<>();

        @Override
        public void publish(LogRecord r) {
            if (r.getLevel().intValue() >= Level.WARNING.intValue()) seen.add(r.getMessage());
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
    }


    /**
     * The converter's file read back: every frame's size and index, the
     * 0xFD marker, and through the SS palette the same pixels as the
     * converter's PNG decode.
     */
    public void testSheetReadsWhatTheConverterWrites() {
        final byte[] ss = sampleSs();
        final List<SsDecoder.IndexedFrame> frames = SsDecoder.decodeIndexed(ss);
        final ClassicIndexSheet sheet = ClassicIndexSheet.parse("TEST.SS",
            ClassicAssetConverter.encodeIndexSheet(frames));
        assertEquals("TEST.SS", sheet.name());
        assertEquals(frames.size(), sheet.size());
        assertEquals(SsDecoder.TRANSPARENT_INDEX, ClassicIndexSheet.TRANSPARENT);
        final List<BufferedImage> png = SsDecoder.decode(ss);
        final int[] pal = SsDecoder.palette(ss).argb;
        for (int f = 0; f < frames.size(); f++) {
            final SsDecoder.IndexedFrame fr = frames.get(f);
            assertEquals(fr.w, sheet.width(f));
            assertEquals(fr.h, sheet.height(f));
            assertEquals(fr.w * fr.h, sheet.pixels(f).length);
            for (int y = 0; y < fr.h; y++) {
                for (int x = 0; x < fr.w; x++) {
                    final int i = sheet.index(f, x, y);
                    assertEquals(fr.index(x, y), i);
                    assertEquals("frame " + f + " " + x + "," + y,
                        (i == ClassicIndexSheet.TRANSPARENT) ? 0 : pal[i],
                        png.get(f).getRGB(x, y));
                }
            }
        }
        assertEquals(1, sheet.index(0, 0, 0));
        assertEquals(ClassicIndexSheet.TRANSPARENT, sheet.index(0, 4, 0));
        assertEquals(127, sheet.index(3, 1, 1));
        assertEquals(0, sheet.index(2, 0, 0));          // the 0x0 sprite
        assertEquals(1, sheet.width(2));
    }

    /** Outside a frame is transparent; a frame that is not there throws. */
    public void testBounds() {
        final ClassicIndexSheet sheet = ClassicIndexSheet.parse("TEST.SS",
            ClassicAssetConverter.encodeIndexSheet(SsDecoder.decodeIndexed(sampleSs())));
        assertEquals(5, sheet.width(0));
        assertEquals(4, sheet.height(0));
        for (int[] p : new int[][] { { -1, 0 }, { 0, -1 }, { 5, 0 }, { 0, 4 } }) {
            assertEquals(ClassicIndexSheet.TRANSPARENT, sheet.index(0, p[0], p[1]));
        }
        for (int f : new int[] { -1, 4 }) {
            try {
                sheet.index(f, 0, 0);
                fail("frame " + f);
            } catch (IndexOutOfBoundsException expected) {
                // ok
            }
        }
    }

    /** Anything but a whole version-1 sheet is refused. */
    public void testParseRefusesBrokenSheets() {
        final byte[] good = ClassicAssetConverter.encodeIndexSheet(
            SsDecoder.decodeIndexed(sampleSs()));
        final List<byte[]> bad = new ArrayList<>();
        bad.add(null);
        bad.add(new byte[0]);
        bad.add(bytes('C', 'S', 'S'));
        bad.add(bytes('X', 'S', 'S', 'I', 1, 0, 0));
        bad.add(bytes('C', 'S', 'S', 'I', 2, 0, 0));          // version 2
        bad.add(bytes('C', 'S', 'S', 'I', 1, 1, 0));          // one frame, no header
        bad.add(bytes('C', 'S', 'S', 'I', 1, 1, 0, 2, 0, 2, 0, 1, 2, 3));  // 3 of 4 px
        bad.add(java.util.Arrays.copyOf(good, good.length - 1));
        bad.add(java.util.Arrays.copyOf(good, good.length + 1));         // a byte left over
        for (byte[] b : bad) {
            try {
                ClassicIndexSheet.parse("BAD.SS", b);
                fail("accepted " + ((b == null) ? "null" : b.length + " bytes"));
            } catch (IllegalArgumentException expected) {
                // ok
            }
        }
        assertEquals(0, ClassicIndexSheet.parse("EMPTY.SS",
                                                bytes('C', 'S', 'S', 'I', 1, 0, 0)).size());
    }

    /**
     * A converted pack: the sheets (read once and shared), the palette (a
     * copy each time), the cycle of CYCLE.DAT, the TERRAIN.SS frame of a
     * tile type from its alias line, and the status line "index ...".
     */
    public void testPackLoadsThePipeline() throws IOException {
        writePipeline(bytes(0x01, 0x00, 0x08, 0x3D, 0x78, 0x23, 0x74, 0x10));
        write("resources.properties", "image.classic_original.ss.TERRAIN.SS.002"
            + "=resources/images/ss/TERRAIN.SS.002.png\n"
            + "image.tile.model.tile.plains.center"
            + "=resource:image.classic_original.ss.TERRAIN.SS.002\n"
            + "image.tile.model.tile.highSeas.center"
            + "=resource:image.classic_original.ss.TERRAIN.SS.011\n"
            + "image.tile.model.tile.odd.center=resource:image.classic_original.ss.PHYS0.SS.002\n"
            + "image.tile.model.tile.junk.center=resource:image.classic_original.ss.TERRAIN.SS.x\n");
        final ClassicPackFiles pack = pack();
        final ClassicIndexSheet t = pack.indexSheet(ClassicPackFiles.TERRAIN_SS);
        assertNotNull(t);
        assertEquals(4, t.size());
        assertSame(t, pack.indexSheet(ClassicPackFiles.TERRAIN_SS));
        assertNotNull(pack.indexSheet(ClassicPackFiles.PHYS0_SS));
        assertNull(pack.indexSheet("ICONS.SS"));

        final int[] pal = pack.gamePalette();
        assertEquals(256, pal.length);
        assertEquals(0x00070E, pal[0]);
        assertEquals((((120 * 21) & 0xFF) << 16) | (((120 * 21 + 7) & 0xFF) << 8)
                     | ((120 * 21 + 14) & 0xFF), pal[120]);
        pal[0] = 0x123456;
        assertEquals(0x00070E, pack.gamePalette()[0]);    // a copy

        assertEquals(ClassicGamePalette.Cycle.DEFAULT, pack.cycleSpec());
        assertEquals("8@120/35", pack.cycleSpec().toString());

        assertEquals(2, pack.terrainSpriteFor("model.tile.plains"));
        assertEquals(11, pack.terrainSpriteFor("model.tile.highSeas"));
        assertEquals(-1, pack.terrainSpriteFor("model.tile.ocean"));   // no alias
        assertEquals(-1, pack.terrainSpriteFor("model.tile.odd"));     // not TERRAIN
        assertEquals(-1, pack.terrainSpriteFor("model.tile.junk"));

        assertEquals("index TERRAIN.SS=4 PHYS0.SS=4 palette=VICEROY cycle=8@120/35",
                     pack.indexStatus());
        assertEquals(pack.indexStatus(), ClassicPackFiles.indexStatus(pack));
    }

    /** CYCLE.DAT's own values win; without it, or unusable, the defaults. */
    public void testCycleTable() throws IOException {
        writePipeline(bytes(0x02, 0x00, 0x04, 0x00, 0x10, 0x05, 0x08, 0x00, 0x78, 0x23));
        assertEquals("4@16/5", pack().cycleSpec().toString());
        assertEquals(5 * ClassicSlide.STEP_MS, pack().cycleSpec().periodMs(), 1e-9);

        Files.delete(this.dir.resolve("data/CYCLE.DAT"));
        assertSame(ClassicGamePalette.Cycle.DEFAULT, pack().cycleSpec());
        assertTrue(pack().indexStatus(), pack().indexStatus().endsWith(" (default)"));

        for (byte[] b : new byte[][] {
                bytes(0x01, 0x00, 0x08, 0x3D, 0x78),          // cut short
                bytes(0x00, 0x00, 0x08, 0x3D, 0x78, 0x23),    // no cycle
                bytes(0x01, 0x00, 0x00, 0x3D, 0x78, 0x23),    // 0 entries
                bytes(0x01, 0x00, 0x08, 0x3D, 0xFC, 0x23),    // past 255
                bytes(0x01, 0x00, 0x08, 0x3D, 0x78, 0x00) }) { // 0 ticks
            write("data/CYCLE.DAT", b);
            assertSame(ClassicGamePalette.Cycle.DEFAULT, pack().cycleSpec());
        }
    }

    /**
     * A pack converted before W6e: no sheets, no palette, the default
     * cycle, the status "fallback no ssidx/TERRAIN.SS.idx", and one warning
     * however often it is asked.  A broken sheet or palette counts as
     * missing, and no pack at all is "fallback no pack".
     */
    public void testPackWithoutThePipelineFallsBack() throws IOException {
        write("resources.properties", "x=y\n");
        final Logger log = Logger.getLogger(ClassicPackFiles.class.getName());
        final Warnings w = new Warnings();
        log.addHandler(w);
        try {
            final ClassicPackFiles pack = pack();
            assertNull(pack.indexSheet(ClassicPackFiles.TERRAIN_SS));
            assertNull(pack.gamePalette());
            assertSame(ClassicGamePalette.Cycle.DEFAULT, pack.cycleSpec());
            assertEquals("fallback no ssidx/TERRAIN.SS.idx", pack.indexStatus());
            assertEquals("fallback no ssidx/TERRAIN.SS.idx", pack.indexStatus());
            assertEquals(w.seen.toString(), 1, w.seen.size());
            assertTrue(w.seen.get(0), w.seen.get(0).contains("ant classic-assets"));
            assertNull(ClassicGamePalette.of(pack));

            writePipeline(null);
            write("ssidx/PHYS0.SS.idx", bytes('C', 'S', 'S', 'I', 1, 1, 0));
            assertEquals("fallback no ssidx/PHYS0.SS.idx", pack().indexStatus());
            Files.delete(this.dir.resolve("ssidx/PHYS0.SS.idx"));
            assertEquals("fallback no ssidx/PHYS0.SS.idx", pack().indexStatus());
            writePipeline(null);
            write("palette/VICEROY.rgb", new byte[767]);
            assertNull(pack().gamePalette());
            assertEquals("fallback no palette/VICEROY.rgb", pack().indexStatus());
            assertEquals("fallback no pack", ClassicPackFiles.indexStatus(null));
        } finally {
            log.removeHandler(w);
        }
    }

    /** The converter and the client agree on every pack path. */
    public void testPackPathsMatchTheConverter() {
        assertEquals(ClassicAssetConverter.INDEX_DIR, ClassicPackFiles.INDEX_DIR);
        assertEquals(ClassicAssetConverter.INDEX_SUFFIX, ClassicPackFiles.INDEX_SUFFIX);
        assertEquals(ClassicAssetConverter.PALETTE_FILE, ClassicPackFiles.PALETTE_FILE);
        assertEquals(ClassicAssetConverter.CYCLE_FILE, ClassicPackFiles.CYCLE_FILE);
        assertEquals(ClassicAssetConverter.INDEX_MAGIC, ClassicIndexSheet.MAGIC);
        assertEquals(ClassicAssetConverter.INDEX_VERSION, ClassicIndexSheet.VERSION);
        assertTrue(new File("x", ClassicPackFiles.PALETTE_FILE).getPath().endsWith("VICEROY.rgb"));
    }
}
