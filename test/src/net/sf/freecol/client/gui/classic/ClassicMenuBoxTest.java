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

import java.awt.Rectangle;

import junit.framework.TestCase;


/**
 * Tests of the dialog-box geometry ({@link ClassicMenuBox}): the measured
 * rule {@code H = 6P + 8R + 18} (prompt lines 6 px apart, rows 8 px), which
 * replaced the inferred {@code 8(P + R) + 16}.  Every expected value is a
 * box measured in a native capture.
 */
public class ClassicMenuBoxTest extends TestCase {

    /** The heights of every captured box. */
    public void testDialogHeightMatchesCaptures() {
        assertEquals("title menu 033", 64, ClassicMenuBox.dialogHeight(1, 5));
        assertEquals("save box 054", 88, ClassicMenuBox.dialogHeight(1, 8));
        assertEquals("load box 055", 104, ClassicMenuBox.dialogHeight(1, 10));
        assertEquals("SAILPORT 051", 32, ClassicMenuBox.dialogHeight(1, 1));
        assertEquals("advisor 083/049", 48, ClassicMenuBox.dialogHeight(5, 0));
        assertEquals("Europe 011 (+6 footer = 74)", 68, ClassicMenuBox.dialogHeight(3, 4));
        assertEquals("Europe 013 (+6 footer = 92)", 86, ClassicMenuBox.dialogHeight(2, 7));
    }

    /** One prompt line: the new rule equals the old one for any row count. */
    public void testOnePromptLineUnchanged() {
        final Rectangle b = new Rectangle(77, 91, 166, 64);
        for (int r = 0; r <= 12; r++) {
            assertEquals(8 * (1 + r) + 16, ClassicMenuBox.dialogHeight(1, r));
            for (int i = 0; i < r; i++) {
                assertEquals(b.y + 11 + 8 + 8 * i, ClassicMenuBox.rowTop(b, 1, i));
            }
        }
        assertEquals(b.y + 9, ClassicMenuBox.promptTop(b, 0));
        // Notices without rows: 1 line is 24 px either way.
        assertEquals(24, ClassicMenuBox.dialogHeight(1, 0));
    }

    /** The advisor box of 083/049: five lines at 121, 127, 133, 139, 145. */
    public void testPromptLinesSixApart() {
        final Rectangle b = new Rectangle(44, 112, 236, 48);
        final int[] tops = { 121, 127, 133, 139, 145 };
        for (int k = 0; k < tops.length; k++) {
            assertEquals(tops[k], ClassicMenuBox.promptTop(b, k));
        }
        assertEquals(6, ClassicMenuBox.PROMPT_PITCH);
        assertEquals(8, ClassicMenuBox.PITCH);
    }

    /** Europe 011: prompt 111/117/123, rows 133/141/149/157. */
    public void testRowsAfterMultiLinePrompt() {
        final Rectangle b = new Rectangle(42, 102, 236, 74);
        assertEquals(111, ClassicMenuBox.promptTop(b, 0));
        assertEquals(123, ClassicMenuBox.promptTop(b, 2));
        final int[] rows = { 133, 141, 149, 157 };
        for (int i = 0; i < rows.length; i++) {
            assertEquals(rows[i], ClassicMenuBox.rowTop(b, 3, i));
        }
        // The bar and the hit rect follow the row (011: bar 132..138).
        assertEquals(132, ClassicMenuBox.barRect(b, 3, 0).y);
        assertEquals(7, ClassicMenuBox.barRect(b, 3, 0).height);
        assertEquals(132, ClassicMenuBox.rowHitRect(b, 3, 0).y);
    }

    /** dialogBounds still centres and keeps the title box where it was. */
    public void testTitleBoxBounds() {
        assertEquals(new Rectangle(77, 91, 166, 64),
                     ClassicMenuBox.dialogBounds(160, 1, 5, 91));
        assertEquals(new Rectangle(62, 48, 196, 104),
                     ClassicMenuBox.dialogBounds(190, 1, 10, -1));
    }
}
