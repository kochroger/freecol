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
import java.util.Arrays;

import junit.framework.TestCase;


/**
 * The colony screen's pure geometry and formulas
 * ({@link ClassicColonyLayout}) against the numbers measured on clip 020
 * (build spec M4 §1.1, §1.2, §1.4, the analysis' L §2 and §8 with their
 * Checkers): every formula with the measured cases, and with the pack the
 * FONTTINY widths of the measured texts, so the formula and the font
 * together give the measured x.
 */
public class ClassicColonyLayoutTest extends TestCase {

    /** The regions of §1.1. */
    public void testTheRegions() {
        assertEquals(new Rectangle(0, 0, 320, 7), ClassicColonyLayout.TOP_LINE);
        assertEquals(7, ClassicColonyLayout.TOP_SEPARATOR_Y);
        assertEquals(new Rectangle(0, 8, 199, 120), ClassicColonyLayout.BUILDING_AREA);
        assertEquals(199, ClassicColonyLayout.AREA_SEPARATOR_X);
        assertEquals(new Rectangle(200, 8, 120, 120), ClassicColonyLayout.WOOD_PANEL);
        // The 3x3 frame x 223-296, y 31-104; the tiles 24 px from (224,32).
        final Rectangle f = ClassicColonyLayout.MAP_FRAME;
        assertEquals(223, f.x);
        assertEquals(296, f.x + f.width - 1);
        assertEquals(31, f.y);
        assertEquals(104, f.y + f.height - 1);
        assertEquals(ClassicColonyLayout.TILES_X - 1, f.x);
        assertEquals(ClassicColonyLayout.TILES_X + 3 * ClassicColonyLayout.TILE, f.x + f.width - 1);
        assertEquals(128, ClassicColonyLayout.BAND_Y);
        assertEquals(new Rectangle(305, 179, 15, 21), ClassicColonyLayout.EXIT);
        assertEquals(306, ClassicColonyLayout.EXIT_LABEL_X);
        assertEquals(179, ClassicColonyLayout.EXIT_LABEL_Y);
        assertEquals(Arrays.toString(new int[] { 132, 147, 162 }),
                     Arrays.toString(ClassicColonyLayout.MODE_Y));
    }

    /** The warehouse (§8): the boxes, the icons' and numbers' x, the digits' inks. */
    public void testTheWarehouse() {
        assertEquals(16, ClassicColonyLayout.WAREHOUSE_BOXES);
        assertEquals(16, ClassicColonyLayout.WAREHOUSE_GOODS.length);
        assertEquals(new Rectangle(0, 179, 20, 21), ClassicColonyLayout.warehouseBox(0));
        assertEquals(new Rectangle(171, 179, 20, 21), ClassicColonyLayout.warehouseBox(9));
        assertEquals(new Rectangle(285, 179, 20, 21), ClassicColonyLayout.warehouseBox(15));
        // Numbers (#2260, #118448): "4" at 9, "78" at 26, "121" at 176,
        // "50" at 292, "0" at 294 (the L Checker).
        assertEquals(9, ClassicColonyLayout.warehouseNumberX(0, 1));
        assertEquals(26, ClassicColonyLayout.warehouseNumberX(1, 2));
        assertEquals(176, ClassicColonyLayout.warehouseNumberX(9, 3));
        assertEquals(292, ClassicColonyLayout.warehouseNumberX(15, 2));
        assertEquals(294, ClassicColonyLayout.warehouseNumberX(15, 1));
        // Icons: 19i + 10 - floor(w/2) (food 7 wide at 7; ore 10 wide at 119).
        assertEquals(7, ClassicColonyLayout.warehouseIconX(0, 7));
        assertEquals(119, ClassicColonyLayout.warehouseIconX(6, 10));
        assertEquals(292, ClassicColonyLayout.warehouseIconX(15, 6));
        // Digits: the last two in ink 61, higher ones in 148 ("1"07, "1"21).
        assertEquals("[61]", Arrays.toString(ClassicColonyLayout.digitInks(4)));
        assertEquals("[61, 61]", Arrays.toString(ClassicColonyLayout.digitInks(78)));
        assertEquals("[148, 61, 61]", Arrays.toString(ClassicColonyLayout.digitInks(107)));
        assertEquals("[148, 61, 61]", Arrays.toString(ClassicColonyLayout.digitInks(121)));
        assertEquals("[148, 148, 61, 61]", Arrays.toString(ClassicColonyLayout.digitInks(1000)));
        assertEquals("[61]", Arrays.toString(ClassicColonyLayout.digitInks(-3)));
        // An exported good (dago: the last two digits green, ink 10).
        assertEquals("[148, 10, 10]", Arrays.toString(ClassicColonyLayout.digitInks(185, true)));
        assertEquals("[10]", Arrays.toString(ClassicColonyLayout.digitInks(5, true)));
        // The hover areas: box i is x 19i ... 19i + 18, y 179-199; x 304 is none.
        assertEquals(0, ClassicColonyLayout.warehouseBoxAt(0, 179));
        assertEquals(0, ClassicColonyLayout.warehouseBoxAt(18, 199));
        assertEquals(1, ClassicColonyLayout.warehouseBoxAt(19, 190));
        assertEquals(15, ClassicColonyLayout.warehouseBoxAt(303, 190));
        assertEquals(-1, ClassicColonyLayout.warehouseBoxAt(304, 190));
        assertEquals(-1, ClassicColonyLayout.warehouseBoxAt(100, 178));
        assertEquals(-1, ClassicColonyLayout.warehouseBoxAt(100, 200));
        // The hover label: centred, clamped to end at 304 (clip008:
        // «Musketen (50 Tonnen)» from 227 for w 77).
        assertEquals(227, ClassicColonyLayout.warehouseLabelX(15, 77));
        assertEquals(164, ClassicColonyLayout.warehouseLabelX(10, 71));
        assertEquals(0, ClassicColonyLayout.warehouseLabelX(0, 40));
    }

    /** The text formulas of §1.2 with the measured widths and places. */
    public void testTheTextFormulas() {
        // Top line: w 125 / 187 / 147 at 98 / 67 / 87; dago 128 / 140 / 144
        // at 97 / 91 / 89; clip008 129 at 96.
        assertEquals(98, ClassicColonyLayout.topLineX(125));
        assertEquals(67, ClassicColonyLayout.topLineX(187));
        assertEquals(87, ClassicColonyLayout.topLineX(147));
        assertEquals(97, ClassicColonyLayout.topLineX(128));
        assertEquals(91, ClassicColonyLayout.topLineX(140));
        assertEquals(89, ClassicColonyLayout.topLineX(144));
        assertEquals(96, ClassicColonyLayout.topLineX(129));
        // Notices: w 44 at 138, w 119 at 100.
        assertEquals(138, ClassicColonyLayout.noticeX(44));
        assertEquals(100, ClassicColonyLayout.noticeX(119));
        // The tools line: w 87 at 213.
        assertEquals(213, ClassicColonyLayout.rightCaptionX(87));
        assertEquals(ClassicColonyLayout.INK_WHITE, ClassicColonyLayout.toolsInk(10, 20));
        assertEquals(ClassicColonyLayout.INK_WHITE, ClassicColonyLayout.toolsInk(15, 50));
        assertEquals(ClassicColonyLayout.INK_GREY, ClassicColonyLayout.toolsInk(44, 40));
        assertEquals(ClassicColonyLayout.INK_GREY, ClassicColonyLayout.toolsInk(40, 40));
        // Hover labels: "Rathaus" over the town hall (87,11) 53 wide: box
        // x 99 for w 27; "Schmiede" over H3 (173,18) 23 wide: clamped to
        // 198 - 33 = 165; never left of 0.
        assertEquals(99, ClassicColonyLayout.hoverBoxX(87, 53, 27));
        assertEquals(165, ClassicColonyLayout.hoverBoxX(173, 23, 33));
        assertEquals(0, ClassicColonyLayout.hoverBoxX(0, 23, 60));
        // Tory text ends at 104: "89% (4)" at 78, "100% (1)" at 74.
        assertEquals(78, ClassicColonyLayout.toryTextX(26));
        assertEquals(74, ClassicColonyLayout.toryTextX(30));
        assertEquals(16, ClassicColonyLayout.countLabelX(14));
        assertEquals("Base.   Frühjahr, 1539.  Gold: 170$",
            ClassicColonyLayout.topLine("Base", "Frühjahr", 1539, "Gold:", 170));
    }

    /**
     * With the pack: FONTTINY's widths of the measured texts, so the
     * formula and the font together give each measured x (the analysis'
     * L §2, §6, §7 and their Checkers).
     */
    public void testTheMeasuredTextsWithTheFont() {
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final ClassicFont f = (pack == null) ? null : pack.font(ClassicFont.TINY);
        if (f == null) {
            System.err.println(getName() + ": skipped, no pack (ant classic-assets)");
            return;
        }
        assertEquals(2, f.stringWidth(" "));
        assertEquals(98, ClassicColonyLayout.topLineX(f.stringWidth(
            "Base.   Frühjahr, 1539.  Gold: 170$")));
        assertEquals(67, ClassicColonyLayout.topLineX(f.stringWidth(
            "Provisorische Kolonie.   Frühjahr, 1541.  Gold: 170$")));
        assertEquals(87, ClassicColonyLayout.topLineX(f.stringWidth(
            "Silver City.   Frühjahr, 1566.  Gold: 348$")));
        assertEquals(138, ClassicColonyLayout.noticeX(f.stringWidth("Einladen Erz")));
        assertEquals(100, ClassicColonyLayout.noticeX(f.stringWidth(
            "24 Erz bewegt nach Handelsschiff")));
        assertEquals(123, ClassicColonyLayout.portCaptionX(f.stringWidth(
            "Keine Schiffe im Hafen")));
        assertEquals(129, ClassicColonyLayout.portCaptionX(f.stringWidth(
            "Einladen: Wagenzug")));
        assertEquals(121, ClassicColonyLayout.portCaptionX(f.stringWidth(
            "Einladen: Handelsschiff")));
        assertEquals(245, ClassicColonyLayout.rightCaptionX(f.stringWidth("Verlag")));
        assertEquals(239, ClassicColonyLayout.rightCaptionX(f.stringWidth("Druckerei")));
        assertEquals(239, ClassicColonyLayout.rightCaptionX(f.stringWidth("Lagerhaus")));
        assertEquals(213, ClassicColonyLayout.rightCaptionX(f.stringWidth(
            "(Benötigt 20 Werkzeuge)")));
        assertEquals(99, ClassicColonyLayout.hoverBoxX(87, 53, f.stringWidth("Rathaus")));
        assertEquals(165, ClassicColonyLayout.hoverBoxX(173, 23, f.stringWidth("Schmiede")));
        assertEquals(78, ClassicColonyLayout.toryTextX(f.stringWidth("89% (4)")));
        assertEquals(74, ClassicColonyLayout.toryTextX(f.stringWidth("100% (1)")));
        for (char c = '0'; c <= '9'; c++) {
            assertEquals("digit " + c, 4, f.charWidth(c));
        }
    }

    /** The timings of §1.4. */
    public void testTheTimings() {
        assertEquals(14.268, ClassicColonyLayout.FRAME_MS, 0.001);
        assertEquals(328.2, ClassicColonyLayout.frames(ClassicColonyLayout.BLINK_FRAMES), 0.1);
        // The blink clock the screen uses is within a frame of 23 frames.
        assertEquals(ClassicColonyLayout.frames(ClassicColonyLayout.BLINK_FRAMES),
                     ClassicBlink.HALF_PERIOD_MS, ClassicColonyLayout.FRAME_MS);
        assertEquals(1969.0, ClassicColonyNotice.RESULT_MS, 0.5);
        assertEquals(271.1, ClassicColonyDissolve.START_MS, 0.1);
        assertEquals(55, ClassicColonyLayout.DISSOLVE_FRAMES);
        assertEquals(ClassicWoodcut.DISSOLVE_FRAMES, ClassicColonyLayout.DISSOLVE_FRAMES);
    }
}
