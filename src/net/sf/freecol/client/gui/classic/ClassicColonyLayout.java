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


/**
 * The original colony screen's <b>geometry, text formulas, inks and
 * timings</b> as pure functions (no Swing, no game): the build spec M4
 * ({@code playthrough-3-analysis/11-spec-M4-colony.md}) §1, measured on clip
 * 020 and re-checked by its Checkers (V unless a javadoc says I).  The
 * painters ({@link ClassicColonyChrome}, {@link ClassicColonyPanel}) and the
 * tests take every number from here, so a number is measured once.
 *
 * <p>Coordinates are the native 320x200 ones, inclusive in the javadocs; a
 * sprite's x,y is its top-left corner.  "Ink n" is index n of the game
 * palette ({@code VICEROY}, which the colony screen shows unchanged below
 * index 152; the band picture {@code COLONY.PIK} brings 152-203).  Text is
 * FONTTINY everywhere on this screen; "w" is the sum of its advances (a
 * space is 2).  One frame is {@link #FRAME_MS} (70.0863 Hz).
 */
final class ClassicColonyLayout {

    private ClassicColonyLayout() {}   // pure helpers only


    // Regions (spec §1.1)

    /** The canvas. */
    static final int W = 320, H = 200;

    /** The top line: WOODTILE.SS.000 tiled from (0,0), x 0-319, y 0-6. */
    static final Rectangle TOP_LINE = new Rectangle(0, 0, W, 7);

    /** The black row under the top line. */
    static final int TOP_SEPARATOR_Y = 7;

    /** The building area: PARCH.SS.000 tiled from (0,0), x 0-198, y 8-127. */
    static final Rectangle BUILDING_AREA = new Rectangle(0, 8, 199, 120);

    /** The black column between the building area and the wood panel, y 8-127. */
    static final int AREA_SEPARATOR_X = 199;

    /** The wood panel: WOODTILE.SS.000 tiled from (0,0), x 200-319, y 8-127. */
    static final Rectangle WOOD_PANEL = new Rectangle(200, 8, 120, 120);

    /** The 3x3 map's black 1-px frame, x 223-296, y 31-104. */
    static final Rectangle MAP_FRAME = new Rectangle(223, 31, 74, 74);

    /** The 3x3 map's tiles: 24x24 at (224 + 24c, 32 + 24r). */
    static final int TILE = 24, TILES_X = 224, TILES_Y = 32;

    /** The band: COLONY.PIK at (0,128), its first row drawn black. */
    static final int BAND_Y = 128;

    /** The band's three panels (the left one, the port, the right one), y 129-177 (I: the PIK's frames). */
    static final Rectangle LEFT_PANEL = new Rectangle(0, 129, 119, 49);
    static final Rectangle PORT_PANEL = new Rectangle(122, 129, 83, 49);
    static final Rectangle RIGHT_PANEL = new Rectangle(208, 129, 95, 49);

    /** The three mode buttons (house, musket, hammer): 15x14 at x 303, y 132 / 147 / 162. */
    static final int MODE_X = 303, MODE_W = 15, MODE_H = 14;
    static final int[] MODE_Y = { 132, 147, 162 };

    /** The exit: the red E, x 305-319, y 179-199, «Abb» white at (306,179) over it. */
    static final Rectangle EXIT = new Rectangle(305, 179, 15, 21);
    static final int EXIT_LABEL_X = 306, EXIT_LABEL_Y = 179;

    /**
     * The exit's label.  Not in the original's text files (GAME, LABELS,
     * NAMES, MENU, COLONY.TXT): the program's own word, as Europe's
     * (V clip 020 #2260; clip008 §3.7).
     */
    static final String EXIT_LABEL = "Abb";


    // The warehouse (spec §1.1, M4.1 target 2)

    /** The 16 boxes: box i is x 19i ... 19i + 19, y 179-199 (the grid is in the PIK). */
    static final int WAREHOUSE_Y = 179, WAREHOUSE_PITCH = 19, WAREHOUSE_BOX_W = 20,
        WAREHOUSE_BOX_H = 21, WAREHOUSE_BOXES = 16;

    /** Icon top and number glyph top. */
    static final int WAREHOUSE_ICON_Y = 181, WAREHOUSE_NUMBER_Y = 194;

    /** The goods icons ICONS.SS.022-037 (full colour) and 038-053 (grey). */
    static final int ICON_GOODS = 22, ICON_GOODS_GREY = 38;

    /**
     * The goods of the 16 boxes, in the original's order (food first,
     * muskets last; ICONS.SS.(22 + i)): FreeCol's ids.
     */
    static final String[] WAREHOUSE_GOODS = {
        "model.goods.food", "model.goods.sugar", "model.goods.tobacco",
        "model.goods.cotton", "model.goods.furs", "model.goods.lumber",
        "model.goods.ore", "model.goods.silver", "model.goods.horses",
        "model.goods.rum", "model.goods.cigars", "model.goods.cloth",
        "model.goods.coats", "model.goods.tradeGoods", "model.goods.tools",
        "model.goods.muskets"
    };

    /**
     * Box i's frame (the green one of the selected good): x 19i ... 19i +
     * 19, y 179-199.
     *
     * @param i The box, 0-15.
     * @return Its rectangle.
     */
    static Rectangle warehouseBox(int i) {
        return new Rectangle(WAREHOUSE_PITCH * i, WAREHOUSE_Y, WAREHOUSE_BOX_W,
                             WAREHOUSE_BOX_H);
    }

    /**
     * Where box i's icon goes: (19i + 10 - floor(w/2), 181).
     *
     * @param i The box.
     * @param w The icon's width.
     * @return Its x.
     */
    static int warehouseIconX(int i, int w) {
        return WAREHOUSE_PITCH * i + 10 - Math.floorDiv(w, 2);
    }

    /**
     * Where box i's number starts: 19i + 1 + floor((20 - 4d)/2), d the
     * number of digits (all FONTTINY digits are 4 wide).
     *
     * @param i The box.
     * @param digits The number's digits.
     * @return Its x.
     */
    static int warehouseNumberX(int i, int digits) {
        return WAREHOUSE_PITCH * i + 1 + Math.floorDiv(20 - 4 * digits, 2);
    }

    /**
     * The ink of each digit of a warehouse number: the last two digits in
     * ink 61, every higher one in ink 148 («1»03 sugar, «1»07 and «1»21 rum;
     * V clip 020; dago 135, 185, 239).
     *
     * @param amount The amount (negative counts as 0).
     * @return One ink per digit, the first digit first.
     */
    static int[] digitInks(int amount) {
        return digitInks(amount, false);
    }

    /**
     * {@link #digitInks(int)} for an exported good: its last two digits
     * in green, ink 10 (V dago Base and Fur Town: silver, rum, cigars,
     * cloth, coats, trade goods; I: the custom house's export setting; not
     * in clip 020, which has no custom house).
     *
     * @param amount The amount.
     * @param exported Whether the colony's custom house exports the good.
     * @return One ink per digit.
     */
    static int[] digitInks(int amount, boolean exported) {
        final String s = String.valueOf(Math.max(0, amount));
        final int[] inks = new int[s.length()];
        for (int k = 0; k < inks.length; k++) {
            inks[k] = (k < inks.length - 2) ? INK_HIGH_DIGITS
                : (exported) ? INK_GREEN : INK_LOW_DIGITS;
        }
        return inks;
    }

    /**
     * The warehouse box under a point: box i's hover area is x 19i ...
     * 19i + 18 (V dago: «Musketen» from x 285 to 303, x 304 already the
     * grid's line or the E), y 179-199.
     *
     * @param x The x.
     * @param y The y.
     * @return The box, or -1.
     */
    static int warehouseBoxAt(int x, int y) {
        if (y < WAREHOUSE_Y || y >= H || x < 0 || x >= WAREHOUSE_LABEL_RIGHT) return -1;
        final int i = x / WAREHOUSE_PITCH;
        return (i < WAREHOUSE_BOXES) ? i : -1;
    }

    /** A warehouse good's hover label: its box on rows 193-199, the glyphs at 194; it ends at x 304 at most. */
    static final int WAREHOUSE_LABEL_Y = 193, WAREHOUSE_LABEL_RIGHT = 304;

    /**
     * A warehouse good's hover label's box x (V clip008 §2.6, dago §7):
     * centred on the box, 19i + 10 - (w + 1)/2, at most 304 - w (the box
     * is w + 1 wide, the text 1 px in).
     *
     * @param i The box.
     * @param w The label's text width.
     * @return The box's x.
     */
    static int warehouseLabelX(int i, int w) {
        final int x = WAREHOUSE_PITCH * i + 10 - (w + 1) / 2;
        return Math.max(0, Math.min(x, WAREHOUSE_LABEL_RIGHT - w));
    }


    // Inks (spec §1.2 and the reports' palette table)

    /** Black; the separators, the map frame, the hover label's box, a shadow. */
    static final int INK_BLACK = 0;
    /** Grey: «(Benötigt n Werkzeuge)» when the tools suffice. */
    static final int INK_GREY = 7;
    /** Green: the selection frames. */
    static final int INK_GREEN = 10;
    /** Yellow: the warehouse cell's drag frame at the press. */
    static final int INK_YELLOW = 14;
    /** White: the hover label, the SoL text, «Abb», the cursor. */
    static final int INK_WHITE = 15;
    /** Blue: the port's and the right panel's captions, a pressed button. */
    static final int INK_CAPTION = 57;
    /** Dark blue: a warehouse number's last two digits. */
    static final int INK_LOW_DIGITS = 61;
    /** Green: the top line. */
    static final int INK_TOP_LINE = 68;
    /** Brown: the 2-px shadow of a figure in a building. */
    static final int INK_SHADOW = 95;
    /** Yellow: a warehouse number's higher digits. */
    static final int INK_HIGH_DIGITS = 148;
    /** Gold: a notice in the top line. */
    static final int INK_NOTICE = 149;


    // Text placement (spec §1.2)

    /** The glyph tops of the top line, the captions, «(Benötigt …)», the SoL line. */
    static final int TOP_LINE_Y = 1, CAPTION_Y = 132, TOOLS_Y = 170, SOL_Y = 133;

    /**
     * The top line's x: floor((322 - w)/2) (V: 98, 67, 87 for w 125, 187,
     * 147 on #2260, #14817, #123003; dago, clip008).
     *
     * @param w The text's width.
     * @return The x.
     */
    static int topLineX(int w) {
        return Math.floorDiv(322 - w, 2);
    }

    /**
     * A notice's x in the top line: floor((320 - w)/2) (V: «Einladen Erz»
     * w 44 at 138, #10058; «24 Erz bewegt nach Handelsschiff» w 119 at 100,
     * #10116).
     *
     * @param w The text's width.
     * @return The x.
     */
    static int noticeX(int w) {
        return Math.floorDiv(320 - w, 2);
    }

    /**
     * The port's caption x: floor((328 - w)/2); it may start at 121, over
     * the separator (V: 123, 129, 121).
     *
     * @param w The text's width.
     * @return The x.
     */
    static int portCaptionX(int w) {
        return Math.floorDiv(328 - w, 2);
    }

    /**
     * The right panel's caption x (the current build) and the «(Benötigt n
     * Werkzeuge)» line's: floor((514 - w)/2) (V: Verlag 245, Druckerei 239,
     * Lagerhaus 239; the tools line 213 for w 87).
     *
     * @param w The text's width.
     * @return The x.
     */
    static int rightCaptionX(int w) {
        return Math.floorDiv(514 - w, 2);
    }

    /**
     * The ink of «(Benötigt n Werkzeuge)»: white while the stock is below
     * n, grey when it is enough (V: 10 &lt; 20 and 15 &lt; 50 white; dago 44
     * &gt;= 40 grey).
     *
     * @param stock The colony's tools.
     * @param need The tools the build needs.
     * @return The ink.
     */
    static int toolsInk(int stock, int need) {
        return (stock < need) ? INK_WHITE : INK_GREY;
    }

    /** The SoL flag (ICONS.SS.123) and its text's x (15, glyph top 133). */
    static final int SOL_ICON_X = 2, SOL_ICON_Y = 132, SOL_TEXT_X = 15;

    /** The Tory crown (ICONS.SS.124) at (104,132); its text ends at x 104. */
    static final int TORY_ICON_X = 104, TORY_ICON_Y = 132;

    /**
     * The Tory text's x: its right edge at 104 (V: «89% (4)» at 78, «100%
     * (1)» at 74).
     *
     * @param w The text's width.
     * @return The x.
     */
    static int toryTextX(int w) {
        return TORY_ICON_X - w;
    }

    /** The hover label's box height: 7 (white glyphs at box y + 1). */
    static final int HOVER_H = 7;

    /**
     * The hover label's box x: sx + sw/2 - (w + 1)/2, clamped to 0 ...
     * 198 - w (V: «Rathaus» box x 99-126 over the town hall at (87,11)
     * 53 wide, #2398; «Schmiede» clamped to x 165-198, #96680).
     *
     * @param sx The sprite's x.
     * @param sw The sprite's width.
     * @param w The label's text width (the box is w + 1 wide).
     * @return The box's x.
     */
    static int hoverBoxX(int sx, int sw, int w) {
        final int x = sx + sw / 2 - (w + 1) / 2;
        return Math.max(0, Math.min(x, BUILDING_AREA.width - 1 - w));
    }

    /**
     * A stacked row's count label: at (first + 2, icon y + 1) (dago).
     *
     * @param first The row's first icon x.
     * @return The label's x.
     */
    static int countLabelX(int first) {
        return first + 2;
    }

    /**
     * The top line's words: {@code "<Name>.   <Jahreszeit>, <Jahr>.  Gold:
     * <n>¤"}, three blanks after the name, two before the gold label
     * (V: «Base.   Frühjahr, 1539.  Gold: 170¤», #2260).  FONTTINY draws
     * its '$' as the coin.
     *
     * @param name The colony's name.
     * @param season The season's name (NAMES.TXT {@code @SEASONS}).
     * @param year The year.
     * @param goldLabel LABELS.TXT {@code @CTITLE} «Gold:».
     * @param gold The treasury.
     * @return The line.
     */
    static String topLine(String name, String season, int year, String goldLabel,
                          int gold) {
        return name + ".   " + season + ", " + year + ".  " + goldLabel + " "
            + gold + "$";
    }


    // Timings (spec §1.4)

    /** One frame of the original: 70.0863 Hz. */
    static final double FRAME_MS = 1000.0 / 70.0863;

    /**
     * @param n Frames.
     * @return Their milliseconds.
     */
    static double frames(double n) {
        return n * FRAME_MS;
    }

    /**
     * The blink's half-period: 23 frames (328.2 ms; V, 33 series).  The
     * colony's blink runs on {@link ClassicBlink}, whose half-period is
     * the map's 20 game ticks (328.5 ms): 0.3 ms longer, inside a frame.
     */
    static final int BLINK_FRAMES = 23;

    /** A result notice in the top line stays 138 frames (1969 ms; V). */
    static final int NOTICE_FRAMES = 138;

    /**
     * The completion dissolve starts 17-20 frames after the screen is
     * complete (V: 19, 19, 17): 19.
     */
    static final int DISSOLVE_START_FRAMES = 19;

    /** The completion dissolve's frames, the first and the last included (V: 55, 3 cases). */
    static final int DISSOLVE_FRAMES = 55;
}
