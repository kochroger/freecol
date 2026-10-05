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


/**
 * Headless tests of the opening-script parser ({@link ClassicOpeningScript})
 * on SYNTHETIC files in the original's format -- no original content.
 */
public class ClassicOpeningScriptTest extends TestCase {

    private static final List<String> OPENING = Arrays.asList(
        ";",
        ";  synthetic test data",
        ";  start_frame, end_frame, series, sprite",
        "@CREDITS",
        "10,  20, 0, 1  ; first banner",
        "30, 45, 1, 2",
        "",
        "50,60,2,3;no space before the comment",
        ";",
        "@OPENING",
        "0,  5,  1, 640          ; wind",
        "1, 7, 0, 320",
        "9,  40, 0, 0",
        "-1, 99, 0, 0            ; END",
        "0,  0,   0, 0",
        "7, 1, 1, 1              ; after the terminator: ignored",
        "",
        "@MESSAGES",
        "Some message text");

    private static final List<String> PATH = Arrays.asList(
        "100, 50", " 99,51 ", "", "98, 52");

    public void testParsesBothSections() {
        final ClassicOpeningScript s = ClassicOpeningScript.parse(OPENING, PATH);
        assertEquals(3, s.credits.size());
        final ClassicOpeningScript.Credit c = s.credits.get(1);
        assertEquals(30, c.start);
        assertEquals(45, c.end);
        assertEquals(1, c.series);
        assertEquals(2, c.sprite);              // 1-based, as in the file
        assertEquals(3, s.credits.get(2).sprite);

        assertEquals(3, s.anims.size());        // END row and terminator excluded
        final ClassicOpeningScript.Anim a = s.anims.get(0);
        assertEquals(0, a.series);
        assertEquals(5, a.start);
        assertEquals(1, a.repeats);
        assertEquals(640, a.baseX);
        assertEquals(9, s.anims.get(2).series);
        assertEquals(99, s.endFrame);
    }

    public void testPath() {
        final ClassicOpeningScript s = ClassicOpeningScript.parse(OPENING, PATH);
        assertEquals(3, s.pathLength());        // the blank line is skipped
        assertEquals(100, s.pathX(0));
        assertEquals(51, s.pathY(1));
        assertEquals(98, s.pathX(2));
        // Clamped at both ends.
        assertEquals(100, s.pathX(-5));
        assertEquals(52, s.pathY(99));
    }

    public void testSeriesTables() {
        assertEquals(10, ClassicOpeningScript.SERIES_STEMS.length);
        assertEquals("OPENSUN", ClassicOpeningScript.SERIES_STEMS[ClassicOpeningScript.SERIES_SUN]);
        assertEquals("OPENBONK", ClassicOpeningScript.SERIES_STEMS[9]);
        assertEquals("OPENCRD1", ClassicOpeningScript.CREDIT_STEMS[0]);
    }

    public void testMalformed() {
        assertBad(Arrays.asList("@CREDITS", "1, 2, 3"), PATH);          // 3 numbers
        assertBad(Arrays.asList("@CREDITS", "1, x, 0, 1", "@OPENING", "-1, 9, 0, 0"), PATH);
        assertBad(Arrays.asList("@CREDITS", "1, 2, 3, 1", "@OPENING", "-1, 9, 0, 0"), PATH); // series 3
        assertBad(Arrays.asList("@CREDITS", "1, 2, 0, 0", "@OPENING", "-1, 9, 0, 0"), PATH); // sprite 0
        assertBad(Arrays.asList("@CREDITS", "5, 2, 0, 1", "@OPENING", "-1, 9, 0, 0"), PATH); // end < start
        assertBad(Arrays.asList("@OPENING", "10, 1, 0, 0", "-1, 9, 0, 0"), PATH);           // series 10
        assertBad(Arrays.asList("@OPENING", "1, 1, 0, 0"), PATH);                           // no END
        assertBad(OPENING, Arrays.asList("1, 2, 3"));
        assertBad(OPENING, Arrays.asList("", "  "));                                        // no path
    }

    private static void assertBad(List<String> opening, List<String> path) {
        try {
            ClassicOpeningScript.parse(opening, path);
            fail("accepted " + opening + " / " + path);
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }
}
