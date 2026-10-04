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
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

import junit.framework.TestCase;

import net.sf.freecol.tools.classicassets.ClassicAssetDecoderTest;
import net.sf.freecol.tools.classicassets.FfDecoder;


/**
 * Headless tests of the classic bitmap-font renderer ({@link ClassicFont})
 * and the menu-box geometry ({@link ClassicMenuBox}).  The font is a
 * synthetic one built through the real converter path (FfDecoder part ->
 * atlas + metrics), so no game data or pack is needed.  The geometry
 * expectations are the boxes measured in the native captures
 * (title 033, load 055, save 054, dropdown 053).
 */
public class ClassicFontTest extends TestCase {

    /**
     * Height 2: 'A' width 3 (ink at (0,0), (1,1)), 'ä' (code 96) width 2,
     * ' ' width 2, '?' width 2, '.' width 1.
     */
    private static ClassicFont font() {
        FfDecoder.Font f = FfDecoder.decodePart(ClassicAssetDecoderTest.ffPart(2, 3, new int[][] {
            { 65, 3,  1, 0, 0,   0, 1, 0 },
            { 96, 2,  1, 0,   1, 0 },
            { 32, 2,  0, 0,   0, 0 },
            { 63, 2,  1, 1,   0, 0 },
            { 46, 1,  1,   0 },
        }));
        return ClassicFont.fromAtlas(FfDecoder.toAtlas(f), FfDecoder.metrics(f));
    }

    public void testToCode() {
        assertEquals(96, ClassicFont.toCode('ä'));
        assertEquals(28, ClassicFont.toCode('ö'));
        assertEquals(127, ClassicFont.toCode('ü'));
        assertEquals(30, ClassicFont.toCode('Ä'));
        assertEquals(31, ClassicFont.toCode('Ö'));
        assertEquals(92, ClassicFont.toCode('Ü'));
        assertEquals(29, ClassicFont.toCode('ß'));
        assertEquals(65, ClassicFont.toCode('A'));
        assertEquals('-', ClassicFont.toCode('–'));
        assertEquals('e', ClassicFont.toCode('é'));     // é -> e
    }

    public void testWidths() {
        ClassicFont f = font();
        assertEquals(2, f.height());
        assertEquals(3 + 2 + 2, f.stringWidth("A ä"));
        assertEquals(6, f.markedWidth("{A}~A"));
        // 'B' has no glyph: it resolves to '?' (width 2).
        assertEquals('?', f.resolve('B'));
        assertEquals(2, f.charWidth('B'));
        // 'ö' has no glyph and 'o' none either: '?' again.
        assertEquals('?', f.resolve('ö'));
        // The ellipsis character is three dots.
        assertEquals(3, f.stringWidth("…"));
    }

    public void testDrawPlainAndMarked() {
        ClassicFont f = font();
        BufferedImage img = new BufferedImage(16, 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        int end = f.draw(g, "A", 0, 0, ClassicFont.colours(0x559634));
        g.dispose();
        assertEquals(3, end);
        assertEquals(0xFF559634, img.getRGB(0, 0));
        assertEquals(0xFF559634, img.getRGB(1, 1));
        assertEquals(0, img.getRGB(1, 0));       // transparent stays untouched
        assertEquals(0, img.getRGB(2, 1));

        img = new BufferedImage(16, 8, BufferedImage.TYPE_INT_ARGB);
        g = img.createGraphics();
        end = f.drawMarked(g, "{A}A", 0, 0, ClassicFont.colours(0x559634),
                           ClassicFont.colours(0xC7A220));
        g.dispose();
        assertEquals(6, end);
        assertEquals(0xFFC7A220, img.getRGB(0, 0));   // first glyph gold
        assertEquals(0xFF559634, img.getRGB(3, 0));   // second glyph green
    }

    public void testFitAndWrap() {
        ClassicFont f = font();
        assertEquals("AA", f.fit("AA", 6));
        String cut = f.fit("AAAA", 7);
        assertTrue(cut, cut.endsWith("..."));
        assertTrue(f.stringWidth(cut) <= 7);
        List<String> lines = f.wrap("AA AA AA", 14);
        assertEquals(2, lines.size());
        assertEquals("AA AA", lines.get(0));
        assertEquals("AA", lines.get(1));
        assertEquals(2, f.wrap("A\nA", 100).size());
    }

    public void testMenuBoxGeometry() {
        Rectangle title = ClassicMenuBox.dialogBounds(160, 1, 5, 91);
        assertEquals(new Rectangle(77, 91, 166, 64), title);
        Rectangle load = ClassicMenuBox.dialogBounds(190, 1, 10, -1);
        assertEquals(new Rectangle(62, 48, 196, 104), load);
        assertEquals(new Rectangle(62, 56, 196, 88),
                     ClassicMenuBox.dialogBounds(190, 1, 8, -1));
        assertEquals(110, ClassicMenuBox.rowTop(title, 1, 0));
        assertEquals(118, ClassicMenuBox.rowTop(title, 1, 1));
        assertEquals(new Rectangle(81, 109, 158, 7), ClassicMenuBox.barRect(title, 1, 0));
        assertEquals(67, ClassicMenuBox.rowTop(load, 1, 0));
        assertEquals(new Rectangle(66, 66, 188, 7), ClassicMenuBox.barRect(load, 1, 0));
        assertEquals(100, ClassicMenuBox.promptTop(title, 0));
        assertEquals(57, ClassicMenuBox.promptTop(load, 0));
        assertEquals(82, ClassicMenuBox.promptX(title));
        assertEquals(86, ClassicMenuBox.rowX(title));
        assertEquals(71, ClassicMenuBox.rowX(load));
        assertEquals(109, ClassicMenuBox.dropdownBounds(12, 9, 100, 13).height);
        assertEquals(new Rectangle(80, 109, 160, 8), ClassicMenuBox.rowHitRect(title, 1, 0));
    }

    public void testFramePixels() {
        BufferedImage img = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        ClassicMenuBox.Theme t = ClassicMenuBox.TITLE;
        ClassicMenuBox.paintDialogFrame(g, ClassicMenuBox.dialogBounds(160, 1, 5, 91),
                                        t, null);
        g.dispose();
        assertEquals(0x000000, img.getRGB(77, 91) & 0xFFFFFF);
        assertEquals(0x000000, img.getRGB(242, 154) & 0xFFFFFF);
        assertEquals(0x593028, img.getRGB(78, 92) & 0xFFFFFF);
        assertEquals(0x794934, img.getRGB(79, 93) & 0xFFFFFF);
        assertEquals(0x794934, img.getRGB(240, 93) & 0xFFFFFF);
        assertEquals(0x794934, img.getRGB(240, 151) & 0xFFFFFF);
        assertEquals(0x382018, img.getRGB(79, 152) & 0xFFFFFF);
        assertEquals(0x382018, img.getRGB(240, 152) & 0xFFFFFF);
        assertEquals(0x382018, img.getRGB(79, 94) & 0xFFFFFF);
        assertEquals(0x5C3A22, img.getRGB(80, 94) & 0xFFFFFF);
    }

    /** Drawing must not leave the caller's Graphics in nearest-neighbour. */
    public void testDrawKeepsCallerHints() {
        ClassicFont f = font();
        BufferedImage img = new BufferedImage(16, 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Object before = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        f.draw(g, "A", 0, 0, ClassicFont.colours(0x559634));
        f.drawMarked(g, "{A}", 0, 0, ClassicFont.colours(0x559634),
                     ClassicFont.colours(0xC7A220));
        assertEquals(before, g.getRenderingHint(RenderingHints.KEY_INTERPOLATION));
        g.dispose();
    }

    /** Notices: missing glyphs mapped, long texts capped to fit the canvas. */
    public void testNoticeSanitiseAndCap() {
        assertEquals("C:/Users/koch-/a (b) x: 1",
            ClassicMainMenuPanel.sanitiseNotice("C:\\Users\\koch_\\a <b> *= 1"));
        assertEquals("(x)", ClassicMainMenuPanel.sanitiseNotice("{x}"));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 400; i++) sb.append("word ");
        ClassicMainMenuPanel.MenuAssets none
            = new ClassicMainMenuPanel.MenuAssets(null, null, null, null);
        List<String> lines = ClassicMainMenuPanel.wrapNotice(none, sb.toString());
        assertEquals(ClassicMainMenuPanel.NOTICE_MAX_LINES, lines.size());
        assertTrue(lines.get(lines.size() - 1).endsWith("..."));
        Rectangle b = ClassicMenuBox.dialogBounds(
            ClassicMainMenuPanel.DIALOG_INNER_WIDTH, lines.size(), 0, -1);
        assertTrue(b.y >= 0 && b.y + b.height <= ClassicMenuBox.VH);
        assertEquals(1, ClassicMainMenuPanel.wrapNotice(none, "kurz").size());
    }

    /** The cursor's two key-colour corner pixels are cleared, nothing else. */
    public void testCursorSprite() {
        BufferedImage raw = new BufferedImage(17, 17, BufferedImage.TYPE_INT_ARGB);
        raw.setRGB(16, 0, 0xFF5555FF);
        raw.setRGB(0, 16, 0xFF5555FF);
        raw.setRGB(1, 0, 0xFF000000);
        raw.setRGB(1, 1, 0xFFAAAAAA);
        BufferedImage c = ClassicMainMenuPanel.cursorSprite(raw);
        assertEquals(0, c.getRGB(16, 0) >>> 24);
        assertEquals(0, c.getRGB(0, 16) >>> 24);
        assertEquals(0xFF000000, c.getRGB(1, 0));
        assertEquals(0xFFAAAAAA, c.getRGB(1, 1));
        assertNull(ClassicMainMenuPanel.cursorSprite(null));
    }
}
