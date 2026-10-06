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

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import junit.framework.TestCase;

import net.sf.freecol.tools.classicassets.ClassicAssetDecoderTest;
import net.sf.freecol.tools.classicassets.FfDecoder;


/**
 * Tests of the original's advisor box ({@link ClassicAdvisorBox}, build
 * spec W7): the geometry of every measured box (landfall 05 sections 2.4
 * and 3; clip004's Sioux chief; clip005/006's King and Tea Party;
 * dago-colony2's @ABANDON), the bar's keys and mouse, the text helpers and
 * the portrait palette, all headless with a synthetic font and sprites of
 * the measured sizes.  And the golden check: GAME.TXT's boxes drawn from the
 * pack against the landfall clip's pixel-exact crops
 * ({@code landfall/analysis/img/*_1x.png}), 0 px off apart from the mouse
 * arrow -- it needs the converted pack and {@code -Dclassic.clips}, else it
 * is skipped with a note.  The original's texts are read from the pack,
 * never written here.
 */
public class ClassicAdvisorBoxTest extends TestCase {

    /** A glyph of the synthetic font: code, width, one ink column. */
    private static int[] glyph(int code, int w) {
        final int[] g = new int[2 + w];
        g[0] = code;
        g[1] = w;
        g[2] = 1;
        return g;
    }

    /** 'a' 3 wide, ' ' 2, 'b' 5: enough to lay out lines. */
    private static ClassicFont font() {
        final FfDecoder.Font f = FfDecoder.decodePart(ClassicAssetDecoderTest.ffPart(1, 6,
            new int[][] { glyph('a', 3), glyph(' ', 2), glyph('b', 5),
                          glyph('.', 2) }));
        return ClassicFont.fromAtlas(FfDecoder.toAtlas(f), FfDecoder.metrics(f));
    }

    /** An opaque sprite of a size. */
    private static BufferedImage sprite(int w, int h) {
        final BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g = img.createGraphics();
        g.setColor(java.awt.Color.RED);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }

    /** A request of P hard lines, R rows, at a width and with a portrait. */
    private static ClassicAdvisorBox.Request request(int width, int p, int r,
                                                     ClassicAdvisorBox.Portrait who,
                                                     Integer y) {
        final List<String> lines = new ArrayList<>();
        for (int i = 0; i < p; i++) lines.add("^a a");
        final List<String> rows = new ArrayList<>();
        for (int i = 0; i < r; i++) rows.add("a");
        return ClassicAdvisorBox.Request.builder("test").gameText(lines)
            .width(width).y(y).rows(rows).portrait(who).build();
    }

    /**
     * Every measured box: (portrait, sprite w, h, @width, P, R, @y) ->
     * box (x, y, w, h) and portrait (x, y).  Landfall 05 section 3 (V),
     * clip004 (the Sioux chief), clip005/006 (the King box, the Tea Party
     * soldier), dago-colony2 (@ABANDON's colonist).
     */
    public void testGeometryOfTheMeasuredBoxes() {
        final ClassicAdvisorBox.Portrait a = ClassicAdvisorBox.Portrait.ADMIRAL,
            s = ClassicAdvisorBox.Portrait.SCOUT,
            so = ClassicAdvisorBox.Portrait.SOLDIER,
            pr = ClassicAdvisorBox.Portrait.PRIEST,
            co = ClassicAdvisorBox.Portrait.COLONIST,
            k = ClassicAdvisorBox.Portrait.KING,
            n = ClassicAdvisorBox.Portrait.NONE,
            arawak = ClassicAdvisorBox.Portrait.chief(2),
            sioux = ClassicAdvisorBox.Portrait.chief(6);
        final Object[][] boxes = {
            // who, pw, ph, @width, P, R, @y, box x, y, w, h, pic x, y
            { "TUTORIAL1", a, 75, 91, 230, 5, 0, null, 44, 112, 236, 48, 40, 41 },
            { "TUTORIAL2", a, 75, 91, 230, 3, 0, null, 44, 118, 236, 36, 40, 47 },
            { "SAILHOME", a, 75, 91, 230, 2, 2, null, 44, 113, 236, 46, 40, 42 },
            { "LANDFALL", s, 149, 95, 190, 2, 2, null, 62, 121, 196, 46, 86, 34 },
            { "INDIANWELCOME", arawak, 65, 181, 230, 5, 2, null, 7, 68, 236, 64, 246, 8 },
            { "INDIANPEACE", arawak, 65, 181, 230, 3, 0, null, 7, 82, 236, 36, 246, 8 },
            { "TUTORIAL11", a, 75, 91, 220, 7, 0, null, 49, 106, 226, 60, 45, 35 },
            { "TUTORIAL13", s, 149, 95, 220, 13, 0, null, 47, 96, 226, 96, 86, 9 },
            { "VILLAGESAVAGE", n, 0, 0, 230, 4, 2, null, 42, 71, 236, 58, -1, -1 },
            { "LEARNSTAY", arawak, 65, 181, 230, 3, 2, null, 7, 74, 236, 52, 246, 8 },
            { "LEARNDONE", arawak, 65, 181, 230, 4, 0, null, 7, 79, 236, 42, 246, 8 },
            { "TUTORIAL14", so, 72, 139, 220, 10, 0, null, 39, 100, 226, 78, 210, 23 },
            { "UNREST", pr, 93, 59, 220, 3, 0, null, 47, 108, 226, 36, 114, 56 },
            { "TUTORIAL5", a, 75, 91, 220, 4, 0, null, 49, 115, 226, 42, 45, 44 },
            { "LOSTCITY4", s, 149, 95, 230, 1, 2, null, 42, 124, 236, 40, 86, 37 },
            { "BURIAL1", s, 149, 95, 230, 1, 0, null, 42, 132, 236, 24, 86, 45 },
            { "TUTORIAL3", s, 149, 95, 230, 3, 0, null, 42, 126, 236, 36, 86, 39 },
            { "VILLAGESAVAGE soldier", n, 0, 0, 230, 4, 3, null, 42, 67, 236, 66, -1, -1 },
            { "SAILPORT", n, 0, 0, 230, 1, 1, null, 42, 84, 236, 32, -1, -1 },
            { "WHACKINDIANS", so, 72, 139, 230, 1, 2, null, 34, 119, 236, 40, 215, 42 },
            { "TUTORIAL17", n, 0, 0, 300, 12, 0, 10, 7, 10, 306, 90, -1, -1 },
            { "Sioux WELCOME", sioux, 107, 177, 230, 5, 2, null, 0, 68, 236, 64, 210, 10 },
            { "Sioux PEACE", sioux, 107, 177, 230, 3, 0, null, 0, 82, 236, 36, 210, 10 },
            { "KINGSTAMPACT", k, 79, 161, 230, 5, 2, null, 84, 68, 236, 64, 0, 18 },
            { "TEAPARTY", so, 72, 139, 220, 6, 0, null, 39, 112, 226, 54, 210, 35 },
            { "ABANDON", co, 60, 68, 230, 2, 2, null, 42, 108, 236, 46, 130, 46 },
        };
        final ClassicFont f = font();
        for (Object[] b : boxes) {
            final String what = (String) b[0];
            final ClassicAdvisorBox.Portrait who = (ClassicAdvisorBox.Portrait) b[1];
            final BufferedImage pic = (who.sprite == null) ? null
                : sprite((Integer) b[2], (Integer) b[3]);
            final ClassicAdvisorBox.Layout l = ClassicAdvisorBox.layout(
                request((Integer) b[4], (Integer) b[5], (Integer) b[6], who,
                        (Integer) b[7]), f, pic);
            assertNotNull(what, l);
            assertEquals(what + " P", ((Integer) b[5]).intValue(), l.promptLines());
            assertEquals(what + " box", new Rectangle((Integer) b[8], (Integer) b[9],
                (Integer) b[10], (Integer) b[11]), l.box);
            if (pic == null) {
                assertNull(what, l.portraitAt);
            } else {
                assertEquals(what + " portrait", new Point((Integer) b[12],
                    (Integer) b[13]), l.portraitAt);
                assertEquals(what + " under", who.kind != ClassicAdvisorBox.Portrait.Kind.OVER,
                             l.under);
            }
            // Prompt at box + (5, 9), 6 apart; rows at x + 9, 8 apart.
            for (int i = 0; i < l.prompt.size(); i++) {
                assertEquals(what, l.box.x + 5, l.prompt.get(i).x);
                assertEquals(what, l.box.y + 9 + 6 * i, l.prompt.get(i).y);
            }
            for (int i = 0; i < l.rows.size(); i++) {
                assertEquals(what, l.box.x + 9, l.rows.get(i).x);
                assertEquals(what, l.box.y + 13 + 6 * l.promptLines() + 8 * i,
                             l.rows.get(i).y);
                assertEquals(what + " bar", new Rectangle(l.box.x + 4,
                    l.rows.get(i).y - 1, l.box.width - 8, 7), l.barRect(i));
            }
        }
        // The SAILHOME bar of the clip: rows at y 138 and 146 (05a, 05b).
        final ClassicAdvisorBox.Layout sail = ClassicAdvisorBox.layout(
            request(230, 2, 2, a, null), f, sprite(75, 91));
        assertEquals(new Rectangle(48, 137, 228, 7), sail.barRect(0));
        assertEquals(new Rectangle(48, 145, 228, 7), sail.barRect(1));
        // @y counts only without a portrait (TUTORIAL1's @y=40 is unused).
        final ClassicAdvisorBox.Layout t1 = ClassicAdvisorBox.layout(
            request(230, 5, 0, a, 40), f, sprite(75, 91));
        assertEquals(112, t1.box.y);
        // An advisor without its sprite: the box alone, centred.
        final ClassicAdvisorBox.Layout bare = ClassicAdvisorBox.layout(
            request(230, 2, 2, a, null), f, null);
        assertEquals(new Rectangle(42, 77, 236, 46), bare.box);
        assertNull(bare.portrait);
    }

    /**
     * The hit rows, the box test, and what the box paints: transparent
     * everywhere but the box and its portrait, so its close leaves the
     * screen as it was.
     */
    public void testHitsAndPicture() {
        final ClassicFont f = font();
        final ClassicAdvisorBox.Layout l = ClassicAdvisorBox.layout(
            request(230, 2, 2, ClassicAdvisorBox.Portrait.ADMIRAL, null), f,
            sprite(75, 91));
        // Rows 1 and 2 at glyph tops 138 and 146: hit 137..144, 145..152.
        assertEquals(0, l.rowAt(100, 137));
        assertEquals(0, l.rowAt(100, 144));
        assertEquals(1, l.rowAt(100, 145));
        assertEquals(1, l.rowAt(100, 152));
        assertEquals(-1, l.rowAt(100, 153));
        assertEquals(-1, l.rowAt(100, 125));       // the prompt
        assertEquals(-1, l.rowAt(46, 140));        // the frame
        assertTrue(l.inBox(44, 113));
        assertFalse(l.inBox(43, 113));
        assertFalse(l.inBox(100, 159));
        final BufferedImage img = ClassicAdvisorBox.render(l, 0, null, f);
        final Rectangle box = l.box;
        final Rectangle pic = new Rectangle(l.portraitAt.x, l.portraitAt.y, 75, 91);
        int outside = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                final boolean drawn = (img.getRGB(x, y) >>> 24) != 0;
                final boolean ours = box.contains(x, y) || pic.contains(x, y);
                if (drawn != ours) outside++;
            }
        }
        assertEquals(0, outside);
        assertEquals(box.union(pic), l.bounds());
        // The bar in index 138's colour under row 2, row 1 bare.
        final BufferedImage barred = ClassicAdvisorBox.render(l, 1, null, f);
        assertEquals(ClassicMenuBox.GAME.dark.getRGB(), barred.getRGB(270, 146));
        assertTrue(ClassicMenuBox.GAME.dark.getRGB() != barred.getRGB(270, 138));
    }

    /** The bar's keys: one row per key, never past the ends; Enter, Escape. */
    public void testBarKeys() {
        final ClassicAdvisorBox.Request r = ClassicAdvisorBox.Request.builder("t")
            .rows("a", "b", "c").defaultRow(1).cancelRow(2).build();
        final ClassicAdvisorBox.Bar bar = new ClassicAdvisorBox.Bar(r);
        final int open = ClassicAdvisorBox.Bar.OPEN;
        assertEquals(1, bar.row());
        assertEquals(open, bar.up());
        assertEquals(0, bar.row());
        assertEquals(open, bar.up());
        assertEquals(0, bar.row());               // no wrap
        assertEquals(open, bar.down());
        assertEquals(open, bar.down());
        assertEquals(open, bar.down());
        assertEquals(2, bar.row());               // no wrap
        assertEquals(open, bar.otherKey());
        assertEquals(2, bar.enter());
        assertEquals(2, bar.escape());            // the cancel row
        // Without a cancel row Escape dismisses.
        final ClassicAdvisorBox.Request nc = ClassicAdvisorBox.Request.builder("t")
            .rows("a", "b").noCancelRow().build();
        assertEquals(-1, nc.cancelRow);
        assertEquals(ClassicAdvisorBox.Bar.DISMISSED, new ClassicAdvisorBox.Bar(nc).escape());
        // The default cancel row is the last.
        assertEquals(1, ClassicAdvisorBox.Request.builder("t").rows("a", "b")
                     .build().cancelRow);
        // A greyed row: the bar may rest on it, Enter does nothing.
        final ClassicAdvisorBox.Request grey = ClassicAdvisorBox.Request.builder("t")
            .rows("a", "b").disabled(new boolean[] { true, false }).build();
        final ClassicAdvisorBox.Bar g = new ClassicAdvisorBox.Bar(grey);
        assertEquals(open, g.enter());
        g.down();
        assertEquals(1, g.enter());
        // A notice: every key dismisses it, with 0.
        final ClassicAdvisorBox.Request n = ClassicAdvisorBox.Request.builder("t")
            .freeColText("x").build();
        assertTrue(n.isNotice());
        final ClassicAdvisorBox.Bar nb = new ClassicAdvisorBox.Bar(n);
        assertEquals(-1, nb.row());
        assertEquals(0, nb.enter());
        assertEquals(0, nb.escape());
        assertEquals(0, nb.otherKey());
        assertEquals(0, nb.up());
        assertEquals(0, nb.down());
        // The default row is clamped to the rows.
        assertEquals(2, ClassicAdvisorBox.Request.builder("t").rows("a", "b", "c")
                     .defaultRow(7).build().defaultRow);
    }

    /**
     * The bar and the mouse: hovering never moves it (there is no motion
     * input), a press on a row puts it there and the release on that row
     * takes it; a release elsewhere takes nothing; a press outside the box
     * removes the bar and its release outside closes as Escape.
     */
    public void testBarMouse() {
        final ClassicAdvisorBox.Request r = ClassicAdvisorBox.Request.builder("t")
            .rows("a", "b", "c").cancelRow(2).build();
        final int open = ClassicAdvisorBox.Bar.OPEN;
        ClassicAdvisorBox.Bar bar = new ClassicAdvisorBox.Bar(r);
        assertEquals(open, bar.release(1, true));      // no press: nothing
        bar.press(1, true);
        assertEquals(1, bar.row());
        assertEquals(1, bar.release(1, true));
        bar = new ClassicAdvisorBox.Bar(r);
        bar.press(1, true);
        assertEquals(open, bar.release(0, true));      // moved off the row
        assertEquals(1, bar.row());                    // the mark stays
        assertEquals(1, bar.enter());
        bar = new ClassicAdvisorBox.Bar(r);
        bar.press(-1, true);                           // the text
        assertEquals(0, bar.row());
        assertEquals(open, bar.release(-1, true));
        bar.press(-1, false);                          // outside: no bar
        assertEquals(-1, bar.row());
        assertEquals(open, bar.enter());
        assertEquals(2, bar.release(-1, false));       // released outside: Escape
        bar = new ClassicAdvisorBox.Bar(r);
        bar.press(-1, false);
        assertEquals(open, bar.release(1, true));      // back on the box: stays
        assertEquals(-1, bar.row());
        assertEquals(open, bar.down());                // a key brings the bar back
        assertEquals(0, bar.row());
        // A greyed row can be marked, not taken.
        final ClassicAdvisorBox.Bar g = new ClassicAdvisorBox.Bar(
            ClassicAdvisorBox.Request.builder("t").rows("a", "b")
                .disabled(new boolean[] { false, true }).build());
        g.press(1, true);
        assertEquals(1, g.row());
        assertEquals(open, g.release(1, true));
        // A notice: any press and release.
        final ClassicAdvisorBox.Bar n = new ClassicAdvisorBox.Bar(
            ClassicAdvisorBox.Request.builder("t").freeColText("x").build());
        assertEquals(open, n.release(-1, true));
        n.press(-1, false);
        assertEquals(0, n.release(-1, false));
    }

    /** FreeCol's text as box text, GAME.TXT's markup dropped for the stopgap. */
    public void testText() {
        assertEquals("a (b) c-d e", ClassicAdvisorBox.literal("a {b} c~d^ e"));
        assertEquals("x y", ClassicAdvisorBox.literal("x_y"));
        assertEquals("", ClassicAdvisorBox.literal(null));
        assertEquals(List.of(List.of("one"), List.of("two (2)")),
            ClassicAdvisorBox.literalParagraphs("one\n\n  two {2}\n"));
        assertEquals("hoher See, ja", ClassicAdvisorBox.plain("{hoher See},  ~ja"));
        final ClassicAdvisorBox.Request r = ClassicAdvisorBox.Request.builder("t")
            .gameText(List.of("Wir sind {hoher", "See}.")).rows("{Ja}", "Nein")
            .build();
        assertEquals("Wir sind hoher See.", r.plainText());
        assertEquals(Arrays.asList("Ja", "Nein"), Arrays.asList(r.plainRows()));
    }

    /** GAME.TXT's @default is 1-based; without it the bar starts on row 1. */
    public void testDefaultRows() throws Exception {
        final File dir = java.nio.file.Files.createTempDirectory("advisor").toFile();
        try {
            final File game = new File(dir, "GAME.TXT");
            final File names = new File(dir, "NAMES.TXT");
            final File labels = new File(dir, "LABELS.TXT");
            java.nio.file.Files.write(game.toPath(), ("@ONE\r\n@width=190\r\n"
                + "Text %STRING0.\r\n\r\nYes\r\nNo\r\n\r\n"
                + "@TWO\r\n@width=230\r\n@default=2\r\nText.\r\n\r\nYes\r\nNo\r\n\r\n"
                + "@NOWIDTH\r\nText.\r\n\r\n@END\r\n")
                .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            java.nio.file.Files.write(names.toPath(), "@END\r\n".getBytes(
                java.nio.charset.StandardCharsets.US_ASCII));
            java.nio.file.Files.write(labels.toPath(), "@END\r\n".getBytes(
                java.nio.charset.StandardCharsets.US_ASCII));
            final ClassicText t = ClassicText.fromFiles(game, names, labels);
            final Map<String, String> v = new HashMap<>();
            v.put("STRING0", "here");
            final ClassicAdvisorBox.Request one = ClassicAdvisorBox
                .fromGameText("ONE", t.message("ONE"), v).build();
            assertEquals(0, one.defaultRow);
            assertEquals(1, one.cancelRow);
            assertEquals(190, one.width);
            assertEquals(List.of(List.of("Text here.")), one.paragraphs);
            assertEquals(List.of("Yes", "No"), one.rows);
            final ClassicAdvisorBox.Request two = ClassicAdvisorBox
                .fromGameText("TWO", t.message("TWO"), null).build();
            assertEquals(1, two.defaultRow);
            assertNull(ClassicAdvisorBox.fromGameText("NOWIDTH",
                t.message("NOWIDTH"), null));
            assertNull(ClassicAdvisorBox.fromGameText("NONE", null, null));
        } finally {
            for (File f : dir.listFiles()) f.delete();
            dir.delete();
        }
    }

    /**
     * A FreeCol text too long for a 236 box gets the 306 one, and what
     * still does not fit is cut with "..."; rows that cannot fit give no
     * box (the stopgap list then).  Without the font there is no box.
     */
    public void testLongTextsAndTooManyRows() {
        final ClassicFont f = font();
        final List<String> lines = new ArrayList<>();
        for (int i = 0; i < 40; i++) lines.add("^a");
        final ClassicAdvisorBox.Layout l = ClassicAdvisorBox.layout(
            ClassicAdvisorBox.Request.builder("t").gameText(lines).build(), f, null);
        assertEquals(306, l.box.width);
        assertTrue(l.box.height <= 200);
        assertEquals((200 - 18) / 6, l.promptLines());
        assertTrue(l.prompt.get(l.promptLines() - 1).marked.endsWith("..."));
        final List<String> rows = new ArrayList<>();
        for (int i = 0; i < 30; i++) rows.add("a");
        assertNull(ClassicAdvisorBox.layout(ClassicAdvisorBox.Request.builder("t")
            .freeColText("a").rows(rows).build(), f, null));
        assertNull(ClassicAdvisorBox.layout(request(230, 1, 1,
            ClassicAdvisorBox.Portrait.NONE, null), null, null));
    }

    /** The portraits' table and the chiefs' sprites. */
    public void testPortraits() {
        assertEquals("MSS0.SS.000", ClassicAdvisorBox.Portrait.ADMIRAL.sprite);
        assertEquals("MSS0.SS", ClassicAdvisorBox.Portrait.ADMIRAL.sheet());
        assertEquals(new Point(-4, -71), ClassicAdvisorBox.Portrait.ADMIRAL.offset(236, 75));
        assertEquals(new Point(181, -77), ClassicAdvisorBox.Portrait.SOLDIER.offset(236, 72));
        assertEquals(new Point(57, -78), ClassicAdvisorBox.Portrait.TRADE.offset(236, 122));
        assertEquals(new Point(44, -87), ClassicAdvisorBox.Portrait.SCOUT.offset(236, 149));
        assertEquals("IND6A0.SS.000", ClassicAdvisorBox.Portrait.chief(6).sprite);
        assertSame(ClassicAdvisorBox.Portrait.NONE, ClassicAdvisorBox.Portrait.chief(8));
        assertSame(ClassicAdvisorBox.Portrait.NONE, ClassicAdvisorBox.Portrait.chief(-1));
        assertNull(ClassicAdvisorBox.Portrait.NONE.sheet());
    }

    /**
     * The portrait's palette: the colours of its pixels in the slot
     * (152-223, 251-255), nothing else.
     */
    public void testPortraitPalette() {
        // A 3x1 sheet: indices 152, 5, 253 (transparent).
        final ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.writeBytes("CSSI".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        b.write(1);
        b.write(1); b.write(0);              // one frame
        b.write(3); b.write(0); b.write(1); b.write(0);
        b.write(152); b.write(5); b.write(253);
        final ClassicIndexSheet sheet = ClassicIndexSheet.parse("T.SS", b.toByteArray());
        final BufferedImage img = new BufferedImage(3, 1, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, 0xFF123456);
        img.setRGB(1, 0, 0xFF654321);
        img.setRGB(2, 0, 0x00000000);
        final int[] p = ClassicAdvisorBox.portraitPalette(sheet, 0, img);
        assertNotNull(p);
        assertEquals(0x123456, p[152]);
        assertEquals(-1, p[5]);
        assertEquals(-1, p[253]);
        int set = 0;
        for (int e : p) if (e >= 0) set++;
        assertEquals(1, set);
        assertNull(ClassicAdvisorBox.portraitPalette(sheet, 0, new BufferedImage(2, 1,
            BufferedImage.TYPE_INT_ARGB)));
        assertNull(ClassicAdvisorBox.portraitPalette(null, 0, img));
        assertTrue(ClassicAdvisorBox.inPortraitSlot(223));
        assertFalse(ClassicAdvisorBox.inPortraitSlot(224));
        assertTrue(ClassicAdvisorBox.inPortraitSlot(251));
        assertFalse(ClassicAdvisorBox.inPortraitSlot(ClassicIndexSheet.TRANSPARENT));
    }


    // The golden check against the landfall clip

    /** One crop: its file, section, values, portrait, bar, place, rows. */
    private static final Object[][] CROPS = {
        // crop, section, values, portrait, bar, crop x, y, NAMES @ACTIONS rows
        { "01_TUTORIAL1_admiral", "TUTORIAL1", "STRING0=Handelsschiff", "ADMIRAL", -1, 40, 41, null },
        { "04_TUTORIAL2_admiral", "TUTORIAL2", "", "ADMIRAL", -1, 40, 47, null },
        { "05a_SAILHOME_bar_Jawohl", "SAILHOME", "", "ADMIRAL", 0, 40, 42, null },
        { "05b_SAILHOME_bar_Nein", "SAILHOME", "", "ADMIRAL", 1, 40, 42, null },
        { "06a_LANDFALL_bar_bleiben", "LANDFALL", "", "SCOUT", 0, 62, 34, null },
        { "06b_LANDFALL_bar_an_Land", "LANDFALL", "", "SCOUT", 1, 62, 34, null },
        { "08_INDIANWELCOME_chief", "INDIANWELCOME",
          "STRING0=Araukaner;NUMBER0=6;STRING1=Dörfer", "CHIEF2", 0, 7, 8, null },
        { "09_INDIANPEACE_chief", "INDIANPEACE", "STRING0=Araukaner;STRING1=Holl.",
          "CHIEF2", -1, 7, 8, null },
        { "10_INDIANCOME_chief", "INDIANCOME", "STRING0=Araukaner", "CHIEF2", -1, 7, 8, null },
        { "11_TUTORIAL11_admiral", "TUTORIAL11", "STRING0=Handelsschiff;STRING1=Amsterdam",
          "ADMIRAL", -1, 45, 35, null },
        { "12_TUTORIAL13_frontiersman", "TUTORIAL13", "", "SCOUT", -1, 47, 9, null },
        { "14_VILLAGESAVAGE_pioneer", "VILLAGESAVAGE", "STRING0=ein Dorf;STRING1=Araukaner",
          "NONE", 0, 42, 71, new int[] { 4, 9 } },
        { "15_LEARNSTAY_chief", "LEARNSTAY", "STRING0=Araukaner;STRING1=Pelzjäger",
          "CHIEF2", 0, 7, 8, null },
        { "16_LEARNDONE_chief", "LEARNDONE", "STRING0=Araukaner;STRING1=Pelzjäger",
          "CHIEF2", -1, 7, 8, null },
        { "17_TUTORIAL14_soldier", "TUTORIAL14", "", "SOLDIER", -1, 39, 23, null },
        { "18_UNREST_priest", "UNREST",
          "COUNTRY=Holland;STRING0=Amsterdam;STRING1=Erfahrene Farmer", "PRIEST", -1, 47, 56, null },
        { "19_TUTORIAL5_admiral", "TUTORIAL5", "STRING0=Amsterdam;STRING1=Erfahrene Farmer",
          "ADMIRAL", -1, 45, 44, null },
        { "20_LOSTCITY4_frontiersman", "LOSTCITY4", "", "SCOUT", 0, 42, 37, null },
        { "21_BURIAL1_frontiersman", "BURIAL1", "", "SCOUT", -1, 42, 45, null },
        { "22_TUTORIAL3_frontiersman", "TUTORIAL3", "STRING0=Felle", "SCOUT", -1, 42, 39, null },
        // The bar on rows 1 and 3 (#22400; #22600 and #22700 are both past
        // the move to "Handlung abbrechen" at #22592).
        { "23a_VILLAGESAVAGE_soldier_bar1", "VILLAGESAVAGE", "STRING0=ein Dorf;STRING1=Araukaner",
          "NONE", 0, 42, 67, new int[] { 8, 7, 9 } },
        { "23b_VILLAGESAVAGE_soldier_bar2", "VILLAGESAVAGE", "STRING0=ein Dorf;STRING1=Araukaner",
          "NONE", 2, 42, 67, new int[] { 8, 7, 9 } },
        { "23c_VILLAGESAVAGE_soldier_bar3", "VILLAGESAVAGE", "STRING0=ein Dorf;STRING1=Araukaner",
          "NONE", 2, 42, 67, new int[] { 8, 7, 9 } },
        { "25_WHACKINDIANS_soldier", "WHACKINDIANS", "STRING0=Araukaner", "SOLDIER", 0, 34, 42, null },
        { "28_VILLAGESAVAGE_soldier_2nd", "VILLAGESAVAGE", "STRING0=ein Dorf;STRING1=Araukaner",
          "NONE", 0, 42, 67, new int[] { 8, 7, 9 } },
        { "31_TUTORIAL17_europe", "TUTORIAL17",
          "STRING0=Amsterdam;STRING1=Holland;STRING2=Neuholland", "NONE", -1, 7, 10, null },
    };

    /**
     * The one wrap the box rule misses (ClassicTextLayout, class comment):
     * @TUTORIAL13 keeps 216 px on its first line, so its lines 1-4 (glyph
     * tops 105-123) break differently; its box, its portrait and the lines
     * from 5 on are checked.
     */
    private static final String WRAP_EXCEPTION = "12_TUTORIAL13_frontiersman";
    private static final int WRAP_EXCEPTION_Y0 = 104, WRAP_EXCEPTION_Y1 = 129;

    private static ClassicAdvisorBox.Portrait portrait(String name) {
        switch (name) {
        case "ADMIRAL": return ClassicAdvisorBox.Portrait.ADMIRAL;
        case "SCOUT": return ClassicAdvisorBox.Portrait.SCOUT;
        case "SOLDIER": return ClassicAdvisorBox.Portrait.SOLDIER;
        case "PRIEST": return ClassicAdvisorBox.Portrait.PRIEST;
        case "CHIEF2": return ClassicAdvisorBox.Portrait.chief(2);
        default: return ClassicAdvisorBox.Portrait.NONE;
        }
    }

    private static Map<String, String> values(String s) {
        final Map<String, String> m = new HashMap<>();
        if (s.isEmpty()) return m;
        for (String kv : s.split(";")) {
            final String[] p = kv.split("=", 2);
            m.put(p[0], p[1]);
        }
        return m;
    }

    /**
     * The golden check: each box drawn from the pack's GAME.TXT (the values
     * as the clip shows them), over the crop, must give the crop's pixels
     * on the box and on the portrait's opaque pixels, 0 px off; the map
     * around them is not compared, and the original's mouse arrow (crop
     * indices 0, 7, 15 where we differ, TUTORIAL17 only) is counted and
     * excused.  Every box's place is the measured one.
     */
    public void testGoldenAgainstTheLandfallClip() throws Exception {
        final String clips = System.getProperty(ClassicTerrainGoldenTest.CLIPS_PROPERTY);
        final File dir = (clips == null) ? null
            : new File(clips, "landfall/analysis/img");
        if (dir == null || !dir.isDirectory()) {
            System.err.println(getClass().getSimpleName()
                + ": golden check skipped, no recordings (-D"
                + ClassicTerrainGoldenTest.CLIPS_PROPERTY + ")");
            return;
        }
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final ClassicText t = ClassicText.load(pack);
        final ClassicFont tiny = (pack == null) ? null : pack.font(ClassicFont.TINY);
        if (t == null || tiny == null) {
            System.err.println(getClass().getSimpleName()
                + ": golden check skipped, no pack texts or FONTTINY (ant classic-assets)");
            return;
        }
        final BufferedImage wood = pack.image(ClassicMenuBar.WOOD_KEY);
        final List<String[]> actions = t.names("ACTIONS");
        final StringBuilder fails = new StringBuilder();
        int compared = 0, arrow = 0;
        for (Object[] c : CROPS) {
            final String name = (String) c[0];
            final ClassicAdvisorBox.Builder b = ClassicAdvisorBox.fromGameText(
                (String) c[1], t.message((String) c[1]), values((String) c[2]));
            assertNotNull(name, b);
            if (c[7] != null) {
                final List<String> rows = new ArrayList<>();
                for (int i : (int[]) c[7]) rows.add(actions.get(i)[0]);
                b.rows(rows);
            }
            final ClassicAdvisorBox.Portrait who = portrait((String) c[3]);
            b.portrait(who);
            final BufferedImage pic = (who.sprite == null) ? null
                : pack.image(ClassicPackFiles.ssKey(who.sprite));
            final ClassicAdvisorBox.Layout l = ClassicAdvisorBox.layout(b.build(), tiny, pic);
            assertNotNull(name, l);
            final File f = new File(dir, name + "_1x.png");
            final BufferedImage crop = ImageIO.read(f);
            assertNotNull(f.toString(), crop);
            final int ox = (Integer) c[5], oy = (Integer) c[6];
            // The union of box and portrait is the crop (its place: crops.txt).
            assertEquals(name, new Rectangle(ox, oy, crop.getWidth(), crop.getHeight()),
                         unionOf(l));
            final BufferedImage canvas = new BufferedImage(320, 200,
                BufferedImage.TYPE_INT_RGB);
            final Graphics2D g = canvas.createGraphics();
            g.drawImage(crop, ox, oy, null);
            ClassicAdvisorBox.paint(g, l, (Integer) c[4], wood, tiny);
            g.dispose();
            final Raster idx = (crop.getColorModel() instanceof IndexColorModel)
                ? crop.getRaster() : null;
            int diff = 0;
            for (int y = 0; y < crop.getHeight(); y++) {
                for (int x = 0; x < crop.getWidth(); x++) {
                    final int sx = ox + x, sy = oy + y;
                    if (!l.box.contains(sx, sy) && !opaque(l, sx, sy)) continue;
                    if (name.equals(WRAP_EXCEPTION) && sy >= WRAP_EXCEPTION_Y0
                        && sy <= WRAP_EXCEPTION_Y1 && sx > l.box.x + 3
                        && sx < l.box.x + l.box.width - 3) continue;
                    compared++;
                    if ((crop.getRGB(x, y) & 0xFFFFFF) == (canvas.getRGB(sx, sy) & 0xFFFFFF)) {
                        continue;
                    }
                    final int i = (idx == null) ? -1 : idx.getSample(x, y, 0);
                    if (i == 0 || i == 7 || i == 15) {
                        arrow++;
                    } else {
                        diff++;
                    }
                }
            }
            if (diff > 0) fails.append(' ').append(name).append('=').append(diff);
        }
        System.out.println(getClass().getSimpleName() + ": golden check, "
            + CROPS.length + " crops, " + compared + " px compared, off:"
            + ((fails.length() == 0) ? " none" : fails.toString())
            + ", " + arrow + " arrow px excused");
        assertEquals("pixels off:" + fails, 0, fails.length());
        assertTrue(compared > 400000);
        assertTrue("arrow pixels " + arrow, arrow <= 20);
    }

    /** @return The box and the portrait, as the crops cut them. */
    private static Rectangle unionOf(ClassicAdvisorBox.Layout l) {
        final Rectangle r = new Rectangle(l.box);
        if (l.portrait != null) {
            r.add(new Rectangle(l.portraitAt.x, l.portraitAt.y, l.portrait.getWidth(),
                                l.portrait.getHeight()));
        }
        return r;
    }

    /** Whether the portrait has an opaque pixel at a screen point. */
    private static boolean opaque(ClassicAdvisorBox.Layout l, int sx, int sy) {
        if (l.portrait == null) return false;
        final int px = sx - l.portraitAt.x, py = sy - l.portraitAt.y;
        return px >= 0 && py >= 0 && px < l.portrait.getWidth()
            && py < l.portrait.getHeight()
            && (l.portrait.getRGB(px, py) >>> 24) != 0;
    }
}
