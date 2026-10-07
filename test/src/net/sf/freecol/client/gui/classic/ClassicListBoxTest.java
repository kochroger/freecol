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
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.io.File;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;

import junit.framework.TestCase;

import net.sf.freecol.tools.classicassets.ClassicAssetDecoderTest;
import net.sf.freecol.tools.classicassets.FfDecoder;


/**
 * Tests of the list-box extensions of the advisor box (build spec D2): the
 * deeper row indent, the gold footer "(F1 für Hilfe)", the right column,
 * the yellow current row and the grey unavailable ones, the caller's start
 * row, press and release, the F1 hook, a box Escape does not answer, a
 * full-screen page and the chain time.  Headless with a synthetic font;
 * and the golden check: the build menu (clip008 #11142) and both job
 * menus (#42043, #42539) drawn with the pack's FONTTINY and wood over the
 * frames, 0 px off on the whole box (needs the pack and
 * {@code -Dclassic.clips}, else skipped with a note).  The rows' words are
 * the clip's, as the analysis read them; the boxes that will make them are
 * D6's and D7's.
 */
public class ClassicListBoxTest extends TestCase {

    /** A glyph of the synthetic font: code, width, one ink column. */
    private static int[] glyph(int code, int w) {
        final int[] g = new int[2 + w];
        g[0] = code;
        g[1] = w;
        g[2] = 1;
        return g;
    }

    /** 'a' 3 wide, ' ' 2, 'b' 5, '(' 2, ')' 2. */
    private static ClassicFont font() {
        final FfDecoder.Font f = FfDecoder.decodePart(ClassicAssetDecoderTest.ffPart(1, 6,
            new int[][] { glyph('a', 3), glyph(' ', 2), glyph('b', 5),
                          glyph('(', 2), glyph(')', 2) }));
        return ClassicFont.fromAtlas(FfDecoder.toAtlas(f), FfDecoder.metrics(f));
    }

    /**
     * The geometry: the footer adds 6 px (H = 6P + 8R + 18 + 6) and sits
     * flush right at y + H - 9; the rows at the indent; the right column
     * ends as far from the right edge as the rows start from the left, or
     * at the caller's inset; a row without a cell has none.
     */
    public void testGeometry() {
        final ClassicFont tiny = font();
        final ClassicAdvisorBox.Request r = ClassicAdvisorBox.Request.builder("list")
            .gameText(Arrays.asList("^a a", "^a a", "^a a")).width(230)
            .rows("a", "b", "a", "b", "a").rowIndent(ClassicMenuBox.LIST_INDENT)
            .footer("ab").right(Arrays.asList("ab", null, "aa")).build();
        final ClassicAdvisorBox.Layout l = ClassicAdvisorBox.layout(r, tiny, null);
        // The father box's size: 236 x 82, centred at (42,59).
        assertEquals(new Rectangle(42, 59, 236, 82), l.box);
        assertEquals(3, l.promptLines());
        assertEquals(42 + 13, l.rows.get(0).x);
        assertEquals(90, l.rows.get(0).y);
        // Footer: width 8 ("ab"), x = 42 + 236 - 2 - 8, top = 59 + 82 - 9.
        assertEquals(42 + 236 - 2 - 8, l.footer.x);
        assertEquals(59 + 82 - 9, l.footer.y);
        // The right column ends 13 px (the indent) from the right edge.
        assertEquals(42 + 236 - 13 - 8, l.rightOf(0).x);
        assertEquals(l.rows.get(0).y, l.rightOf(0).y);
        assertNull(l.rightOf(1));
        assertEquals(42 + 236 - 13 - 6, l.rightOf(2).x);
        assertNull(l.rightOf(3));                     // beyond the cells
        assertNull(l.rightOf(9));
        // Without a footer the box is 6 px lower; an explicit inset.
        final ClassicAdvisorBox.Layout m = ClassicAdvisorBox.layout(
            ClassicAdvisorBox.Request.builder("menu").gameText(Arrays.asList("a"))
                .width(190).rows("a", "b").right(Arrays.asList("a", "b"), 20).build(),
            tiny, null);
        assertEquals(ClassicMenuBox.dialogHeight(1, 2), m.box.height);
        assertNull(m.footer);
        assertEquals(m.box.x + ClassicMenuBox.ROW_INDENT, m.rows.get(0).x);
        assertEquals(m.box.x + m.box.width - 20 - 3, m.rightOf(0).x);
        assertEquals(m.box.x + m.box.width - 20 - 5, m.rightOf(1).x);
        // The defaults: no indent change, no footer, no column, no help.
        final ClassicAdvisorBox.Request d = ClassicAdvisorBox.Request.builder("d")
            .rows("a").build();
        assertEquals(ClassicMenuBox.ROW_INDENT, d.rowIndent);
        assertNull(d.footer);
        assertNull(d.right);
        assertNull(d.help);
        assertTrue(d.escapes);
        assertFalse(d.isCurrent(0));
        assertTrue(d.chainMs < 0);
        assertNull(d.picture);
    }

    /**
     * The colours: a current row is gold, its cell too; a greyed row and
     * its cell are grey (index 8); the footer is gold; the others ink.
     */
    public void testRowColours() {
        final ClassicFont tiny = font();
        final ClassicAdvisorBox.Request r = ClassicAdvisorBox.Request.builder("c")
            .gameText(Arrays.asList("a")).width(100).rows("a", "a", "a")
            .right(Arrays.asList("b", "b", "b")).current(new boolean[] { false, true })
            .disabled(new boolean[] { false, false, true }).footer("b").build();
        final ClassicAdvisorBox.Layout l = ClassicAdvisorBox.layout(r, tiny, null);
        final BufferedImage img = ClassicAdvisorBox.render(l, -1, null, tiny);
        final int ink = ClassicMenuBox.GAME.ink.getRGB() & 0xFFFFFF;
        final int gold = ClassicMenuBox.GAME.highlight.getRGB() & 0xFFFFFF;
        final int grey = ClassicMenuBox.DISABLED_INK;
        assertEquals(ink, inkOf(img, l.rows.get(0)));
        assertEquals(ink, inkOf(img, l.rightOf(0)));
        assertEquals(gold, inkOf(img, l.rows.get(1)));
        assertEquals(gold, inkOf(img, l.rightOf(1)));
        assertEquals(grey, inkOf(img, l.rows.get(2)));
        assertEquals(grey, inkOf(img, l.rightOf(2)));
        assertEquals(gold, inkOf(img, l.footer));
        // Grey is palette index 8 (DOSBox's (v<<2)|(v>>4) of 0x15).
        assertEquals(0x555555, grey);
    }

    /** The colour of the first glyph's ink column at a line. */
    private static int inkOf(BufferedImage img, ClassicTextLayout.Line line) {
        return img.getRGB(line.x, line.y) & 0xFFFFFF;
    }

    /**
     * The keys: the bar starts on the caller's row; F1 tells the hook the
     * barred row and closes with HELP; without a hook F1 is any other
     * key; a box without Escape keeps Escape and a click outside from
     * answering; press marks a row, release takes it.
     */
    public void testKeysAndHelp() {
        final int[] told = { -9 };
        final ClassicAdvisorBox.Request r = ClassicAdvisorBox.Request.builder("f")
            .rows("a", "b", "c").defaultRow(2).noEscape().outsideCancels(false)
            .help(row -> told[0] = row).build();
        final int open = ClassicAdvisorBox.Bar.OPEN;
        ClassicAdvisorBox.Bar bar = new ClassicAdvisorBox.Bar(r);
        assertEquals(2, bar.row());
        assertEquals(open, bar.escape());
        assertEquals(open, r.escapeAnswer());
        bar.press(-1, false);
        assertEquals(2, bar.row());                    // a press outside keeps it
        assertEquals(open, bar.release(-1, false));
        assertEquals(open, bar.up());
        assertEquals(ClassicAdvisorBox.Bar.HELP, bar.help());
        assertEquals(1, told[0]);
        bar = new ClassicAdvisorBox.Bar(r);
        bar.press(0, true);
        assertEquals(0, bar.row());
        assertEquals(0, bar.release(0, true));
        // Without a hook: F1 is any other key.
        final ClassicAdvisorBox.Request q = ClassicAdvisorBox.Request.builder("q")
            .rows("a", "b").build();
        assertEquals(open, new ClassicAdvisorBox.Bar(q).help());
        final ClassicAdvisorBox.Request n = ClassicAdvisorBox.Request.builder("n")
            .freeColText("x").build();
        assertEquals(0, new ClassicAdvisorBox.Bar(n).help());
        // A box with Escape still answers it.
        assertEquals(1, new ClassicAdvisorBox.Bar(q).escape());
        // A cancel row of a box without Escape is never taken by Escape.
        final ClassicAdvisorBox.Request c = ClassicAdvisorBox.Request.builder("c")
            .rows("a", "b").cancelRow(1).noEscape().build();
        assertEquals(open, new ClassicAdvisorBox.Bar(c).escape());
    }

    /**
     * A full-screen page: a notice of the whole screen whose picture is
     * the box; any key or click closes it; a chain time is kept.
     */
    public void testPage() {
        final BufferedImage page = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
        page.setRGB(5, 7, 0x123456);
        final ClassicAdvisorBox.Request r = ClassicAdvisorBox.Request.builder("pedia")
            .freeColText("Peter Minuit").rows("ignored").picture(page).chain(142.0)
            .build();
        assertTrue(r.isNotice());
        assertEquals(142.0, r.chainMs);
        final ClassicAdvisorBox.Layout l = ClassicAdvisorBox.layout(r, font(), null);
        assertEquals(new Rectangle(0, 0, 320, 200), l.box);
        assertTrue(l.rows.isEmpty());
        assertTrue(l.inBox(0, 0));
        assertTrue(l.inBox(319, 199));
        final BufferedImage img = ClassicAdvisorBox.render(l, -1, null, font());
        assertEquals(0x123456, img.getRGB(5, 7) & 0xFFFFFF);
        assertEquals(0xFF000000, img.getRGB(6, 7));    // the page is opaque
        final ClassicAdvisorBox.Bar bar = new ClassicAdvisorBox.Bar(r);
        assertEquals(0, bar.otherKey());
        assertEquals(0, bar.help());
        bar.press(-1, true);
        assertEquals(0, bar.release(-1, true));
    }


    // The golden check against clip008

    /** The build menu's rows (#11142), as NAMES' upper case leaves 'ä'. */
    private static final List<String> BUILD_ROWS = Arrays.asList(
        "(Keine Produktion)", "WAFFENKAMMER", "HAFENANLAGEN", "LAGERHAUS",
        "STäLLE", "VERLAG", "WEBEREI", "TABAKLADEN", "RUMBRENNEREI",
        "PELZHANDELSPOSTEN", "SCHMIEDE", "WAGENZUG");

    /** Its costs: two goods with no blank between them. */
    private static final List<String> BUILD_COSTS = Arrays.asList(null,
        "(52 Hämmer)", "(52 Hämmer)", "(80 Hämmer)", "(64 Hämmer)",
        "(52 Hämmer)(20 Werkzeuge)", "(64 Hämmer)(20 Werkzeuge)",
        "(64 Hämmer)(20 Werkzeuge)", "(64 Hämmer)(20 Werkzeuge)",
        "(56 Hämmer)(20 Werkzeuge)", "(64 Hämmer)(20 Werkzeuge)", "(40 Hämmer)");

    /** The job menu (#42043). */
    private static final List<String> JOB_ROWS = Arrays.asList(
        "(SPEZIALFÄHIGKEIT LÖSCHEN)", "Farmer", "Zuckerpflanzer", "Tabakpflanzer",
        "Baumwollpflanzer", "Pelzjäger", "Holzfäller", "Erz-Bergarbeiter",
        "Silber-Bergarbeiter", "Fischer", "Schnapsbrenner", "Tabakhändler", "Weber",
        "Pelzhändler", "Schreiner", "Schmied", "Staatsmann", "(Weitere)");

    private static final List<String> JOB_CELLS = Arrays.asList(null,
        "2/3 Nahrungsmittel", "0/1 Zucker", "1/1 Tabak", "0/1 Baumwolle", "2/3 Felle",
        "6/6 Nutzholz", "0/1 Erz", "0/0 Silber", "0/0 Nahrungsmittel", "3 Rum",
        "3 Zigarren", "3 Stoff", "3 Mäntel", "3 Hämmer", "3 Werkzeuge",
        "3 Freiheitsglocken", null);

    /** The job sub-list (#42539): Soldat yellow, Späher and Dragoner grey. */
    private static final List<String> MORE_ROWS = Arrays.asList("(Weitere)",
        "Siedler", "Pionier", "Soldat", "Späher", "Dragoner");

    private static final List<String> MORE_CELLS = Arrays.asList(null, null,
        "(100 Werkzeuge)", "(50 Musketen)", "(50 Pferde)",
        "(50 Musketen) (50 Pferde)");

    /** The job title of both job menus. */
    private static final String JOB_TITLE = "Beruf wählen für Erfahrene Soldaten(Farmer):";

    /**
     * The golden check: the three list boxes of clip008, built with the
     * extensions and drawn over their frames, 0 px off on the whole box
     * (no mouse arrow lies on them): the build menu (62,37,196,126), rows
     * at + 9, costs ending 9 px from the right, the current build yellow
     * under the bar; the job menu (62,13,196,174), rows at + 13, the goods
     * ending 13 px from the right; its sub-list (62,61,196,78) with the
     * unit's own role yellow and the two horse roles grey.  Each with the
     * gold "(F1 für Hilfe)".
     */
    public void testGoldenAgainstTheClip() throws Exception {
        final String clips = System.getProperty(ClassicTerrainGoldenTest.CLIPS_PROPERTY);
        final File dir = (clips == null) ? null : new File(clips, "clip008");
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
        final String footer = t.misc(ClassicFathers.MISC_F1_HELP);
        assertEquals("(F1 für Hilfe)", footer);
        final boolean[] current = new boolean[12];
        current[2] = true;
        final ClassicAdvisorBox.Request build = ClassicAdvisorBox.Request
            .builder("build").freeColText("Objekt zum Bauen wählen").width(190)
            .rows(BUILD_ROWS).right(BUILD_COSTS).current(current).footer(footer)
            .defaultRow(2).build();
        final ClassicAdvisorBox.Request jobs = ClassicAdvisorBox.Request
            .builder("jobs").freeColText(JOB_TITLE).width(190).rows(JOB_ROWS)
            .right(JOB_CELLS).rowIndent(ClassicMenuBox.LIST_INDENT).footer(footer)
            .defaultRow(1).build();
        final ClassicAdvisorBox.Request more = ClassicAdvisorBox.Request
            .builder("more").freeColText(JOB_TITLE).width(190).rows(MORE_ROWS)
            .right(MORE_CELLS).rowIndent(ClassicMenuBox.LIST_INDENT).footer(footer)
            .current(new boolean[] { false, false, false, true, false, false })
            .disabled(new boolean[] { false, false, false, false, true, true })
            .defaultRow(0).build();
        final Object[][] boxes = {
            { "frame_011142.png", build, 2, new Rectangle(62, 37, 196, 126) },
            { "frame_042043.png", jobs, 1, new Rectangle(62, 13, 196, 174) },
            { "frame_042539.png", more, 0, new Rectangle(62, 61, 196, 78) },
        };
        final StringBuilder fails = new StringBuilder();
        int compared = 0;
        for (Object[] b : boxes) {
            final ClassicAdvisorBox.Request r = (ClassicAdvisorBox.Request) b[1];
            final ClassicAdvisorBox.Layout l = ClassicAdvisorBox.layout(r, tiny, null);
            assertEquals((String) b[0], b[3], l.box);
            final int[] got = compare(new File(dir, (String) b[0]), l, (Integer) b[2],
                                      wood, tiny);
            compared += got[0];
            if (got[1] > 0 || got[2] > 0) {
                fails.append(' ').append(b[0]).append('=').append(got[1])
                    .append('+').append(got[2]);
            }
        }
        System.out.println(getClass().getSimpleName() + ": golden check, "
            + boxes.length + " list boxes, " + compared + " px compared, off:"
            + ((fails.length() == 0) ? " none" : fails.toString()));
        assertEquals("pixels off:" + fails, 0, fails.length());
        assertEquals(196 * (126 + 174 + 78), compared);
    }

    /**
     * Draw a box over a frame and compare the whole box.
     *
     * @return {compared, off, off on the original's arrow (indices 0, 7, 15)}.
     */
    static int[] compare(File file, ClassicAdvisorBox.Layout l, int bar,
                         BufferedImage wood, ClassicFont tiny) throws Exception {
        final BufferedImage frame = ImageIO.read(file);
        assertNotNull(file.toString(), frame);
        final BufferedImage canvas = new BufferedImage(320, 200,
            BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = canvas.createGraphics();
        g.drawImage(frame, 0, 0, null);
        ClassicAdvisorBox.paint(g, l, bar, wood, tiny);
        g.dispose();
        final Raster idx = (frame.getColorModel() instanceof IndexColorModel)
            ? frame.getRaster() : null;
        final int[] out = new int[3];
        for (int y = l.box.y; y < l.box.y + l.box.height; y++) {
            for (int x = l.box.x; x < l.box.x + l.box.width; x++) {
                out[0]++;
                if ((frame.getRGB(x, y) & 0xFFFFFF) == (canvas.getRGB(x, y) & 0xFFFFFF)) {
                    continue;
                }
                final int i = (idx == null) ? -1 : idx.getSample(x, y, 0);
                out[(i == 0 || i == 7 || i == 15) ? 2 : 1]++;
            }
        }
        return out;
    }
}
