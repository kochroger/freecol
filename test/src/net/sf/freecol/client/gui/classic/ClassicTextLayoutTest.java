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

import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import net.sf.freecol.tools.classicassets.ClassicAssetDecoderTest;
import net.sf.freecol.tools.classicassets.FfDecoder;


/**
 * Tests of the original's text-page layout ({@link ClassicTextLayout}) with
 * a synthetic font of fixed widths: 'a' 'b' 'c' 3, ' ' 2, '-' 2, 'ß' 3,
 * '_' 7 (like FONTINTR's visible underscore).  '{' and '}' have no glyph.
 */
public class ClassicTextLayoutTest extends TestCase {

    private static int[] glyph(int code, int w) {
        final int[] g = new int[2 + w];
        g[0] = code;
        g[1] = w;
        g[2] = 1;
        return g;
    }

    private static ClassicFont font() {
        final FfDecoder.Font f = FfDecoder.decodePart(ClassicAssetDecoderTest.ffPart(1, 7,
            new int[][] { glyph('a', 3), glyph('b', 3), glyph('c', 3),
                          glyph(' ', 2), glyph('-', 2), glyph(29, 3),
                          glyph('_', 7) }));
        return ClassicFont.fromAtlas(FfDecoder.toAtlas(f), FfDecoder.metrics(f));
    }

    private static List<ClassicTextLayout.Line> lay(int width, String... raw) {
        return ClassicTextLayout.layout(font(), Arrays.asList(raw), width, 0, 0, 10);
    }

    public void testMeasureCountsBracesAndSharpS() {
        final ClassicFont f = font();
        assertEquals(9, f.markedWidth("a{b}ß"));
        assertEquals(12, ClassicTextLayout.measure(f, "a{b}ß"));
    }

    public void testLimitIsWidthMinusTwo() {
        // "aa aa" = 3+3+2+3+3 = 14: fits W=16 (14 <= 14), breaks at W=15.
        assertEquals(1, lay(16, "aa aa").size());
        final List<ClassicTextLayout.Line> two = lay(15, "aa aa");
        assertEquals(2, two.size());
        assertEquals("aa", two.get(0).marked);
        assertEquals("aa", two.get(1).marked);
        // A brace costs one: "a {b}" measures 3+2+3+2 = 10 -> W=12 fits, 11 breaks.
        assertEquals(1, lay(12, "a {b}").size());
        assertEquals(2, lay(11, "a {b}").size());
    }

    public void testJoinAfterHyphenHasASpace() {
        final List<ClassicTextLayout.Line> l = lay(100, "a-", "b");
        assertEquals(1, l.size());
        assertEquals("a- b", l.get(0).marked);
    }

    public void testNeverBreaksAtHyphens() {
        final List<ClassicTextLayout.Line> l = lay(10, "aa-bb c");
        assertEquals(2, l.size());
        assertEquals("aa-bb", l.get(0).marked);    // too wide, but one word
        assertEquals("c", l.get(1).marked);
    }

    public void testSpacesCollapse() {
        final List<ClassicTextLayout.Line> l = lay(100, "a  ", "  b");
        assertEquals("a b", l.get(0).marked);
    }

    public void testCentredLineUsesCeilAndLeadingSpaces() {
        // " a " -> rtrim " a" = 5 px in W=10: x = ceil(5/2) = 3.
        final List<ClassicTextLayout.Line> l = lay(10, "^^ a ");
        assertEquals(1, l.size());
        assertEquals(3, l.get(0).x);
        assertEquals(" a", l.get(0).marked);
        // Even remainder: "aa" = 6 in W=10 -> x = 2.
        assertEquals(2, lay(10, "^^aa").get(0).x);
    }

    public void testPlaceholdersAndCaretsStripped() {
        final List<ClassicTextLayout.Line> l = lay(100, "__a_b^c");
        assertEquals("abc", l.get(0).marked);
        assertEquals(0, l.get(0).x);
        // '^^_' is a blank centred line, '^' a blank left line.
        final List<ClassicTextLayout.Line> h = lay(100, "^^aa", "^^_", "^", "^b", "c");
        assertEquals(5, h.size());
        assertEquals("", h.get(1).marked);
        assertEquals("", h.get(2).marked);
        assertEquals("b", h.get(3).marked);
        assertEquals(0, h.get(3).x);
        assertEquals(10, h.get(1).y);
        assertEquals(40, h.get(4).y);
    }

    public void testOpenHighlightCarriesOver() {
        // "{aa aa}": measure 3+3+2+3+3+2 = 16 -> breaks at W=17.
        final List<ClassicTextLayout.Line> l = lay(17, "{aa aa}");
        assertEquals(2, l.size());
        assertEquals("{aa}", l.get(0).marked);
        assertEquals("{aa}", l.get(1).marked);
        assertEquals(1, lay(18, "{aa aa}").size());
    }

    public void testPlacement() {
        final ClassicFont f = font();
        // No @x: centred W; no top: the block is centred vertically.
        final List<ClassicTextLayout.Line> c = ClassicTextLayout.layout(f,
            Arrays.asList("^a", "^b"), 300, null, null, 10);
        assertEquals(10, c.get(0).x);
        assertEquals(90, c.get(0).y);           // (200 - 2*10) / 2
        assertEquals(100, c.get(1).y);
        // @x and @y + 3 come from the caller.
        final List<ClassicTextLayout.Line> s = ClassicTextLayout.layout(f,
            Arrays.asList("^a", "b"), 81, 235, 19, 8);
        assertEquals(235, s.get(0).x);
        assertEquals(19, s.get(0).y);
        assertEquals(27, s.get(1).y);
        assertTrue(ClassicTextLayout.layout(f, Arrays.<String>asList(), 81, 0, 0, 8).isEmpty());
    }
}
