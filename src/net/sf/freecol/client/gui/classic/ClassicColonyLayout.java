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


    // The building area: the 15 slots (spec M4.2; L §3.1, dago §2.1, buildset §2)

    /**
     * A slot's size class (NAMES.TXT {@code @BUILDING}'s 4th column; V by
     * the sprite sizes): its sprite size, the placeholder drawn while its
     * family is not built, where its production row and its workers go,
     * and the row model's two numbers (dago §2.6).
     */
    enum SlotClass {
        /** 23x27, class 0: the houses; tree 044. */
        HOUSE(23, 27, 44, 4, -2, 13, 20, 12),
        /** 44x22, class 1; tree 043. */
        MID(44, 22, 43, 10, 2, 9, 22, 12),
        /** 53x37, class 2; tree 042. */
        LARGE(53, 37, 42, 14, 2, 23, 30, 20),
        /** 75x48, class 4: the harbour; the water scene 045, no row, no workers. */
        DOCKS(75, 48, 45, 0, 0, 0, 0, 0),
        /** 73x18, class 3: the fortification; the fence 016, the units outside on it. */
        FENCE(73, 18, 16, 0, 0, 5, 0, 0);

        /** The sprite's size. */
        final int w, h;

        /** The placeholder's BUILDING.SS frame. */
        final int placeholder;

        /** The production row's anchor x (from the sprite's x) and its icon y (from its y). */
        final int rowAnchor, rowY;

        /** The workers' y (from the sprite's y); the fence's units outside. */
        final int workerY;

        /**
         * The row model's span S (a spaced row's pitch is at most
         * floor((S - w)/(m - 1))) and its stack threshold T (more than T
         * icons halve: k doubles); dago §2.6, S_large 30 by L §3.4 (V);
         * S_mid 22 or 23 and T_mid 12 or 13, T_large 20-23 (V bounds, the
         * lower taken, I).
         */
        final int span, most;

        SlotClass(int w, int h, int placeholder, int rowAnchor, int rowY, int workerY,
                  int span, int most) {
            this.w = w;
            this.h = h;
            this.placeholder = placeholder;
            this.rowAnchor = rowAnchor;
            this.rowY = rowY;
            this.workerY = workerY;
            this.span = span;
            this.most = most;
        }
    }

    /** One of the 15 fixed places of the building area. */
    static final class Slot {

        /** Its name (ours): M1-M4, H1-H7, L1, L2, W, X. */
        final String id;

        /** Its class. */
        final SlotClass cls;

        /** The sprite's top-left corner. */
        final int x, y;

        /** Its index in {@link #SLOTS} (the draw order). */
        final int index;

        Slot(String id, SlotClass cls, int x, int y, int index) {
            this.id = id;
            this.cls = cls;
            this.x = x;
            this.y = y;
            this.index = index;
        }

        /** @return The sprite's rectangle. */
        Rectangle bounds() {
            return new Rectangle(this.x, this.y, this.cls.w, this.cls.h);
        }

        @Override
        public String toString() {
            return this.id;
        }
    }

    /**
     * The 15 slots in the original's draw order (dago's, V: 0 px in 3 dago
     * colonies and in the 5 building areas of clip 020; only M2 under W
     * overlaps): M1, H2, H1, L1, H3, H4, H5, M2, H7, H6, W, M3, L2, M4, X.
     */
    static final Slot[] SLOTS = slots();

    private static Slot[] slots() {
        final Object[][] t = {
            { "M1", SlotClass.MID, 6, 14 }, { "H2", SlotClass.HOUSE, 145, 15 },
            { "H1", SlotClass.HOUSE, 56, 13 }, { "L1", SlotClass.LARGE, 87, 11 },
            { "H3", SlotClass.HOUSE, 173, 18 }, { "H4", SlotClass.HOUSE, 8, 41 },
            { "H5", SlotClass.HOUSE, 37, 45 }, { "M2", SlotClass.MID, 128, 53 },
            { "H7", SlotClass.HOUSE, 96, 53 }, { "H6", SlotClass.HOUSE, 67, 54 },
            { "W", SlotClass.DOCKS, 123, 55 }, { "M3", SlotClass.MID, 10, 76 },
            { "L2", SlotClass.LARGE, 66, 87 }, { "M4", SlotClass.MID, 15, 102 },
            { "X", SlotClass.FENCE, 123, 106 },
        };
        final Slot[] out = new Slot[t.length];
        for (int i = 0; i < t.length; i++) {
            out[i] = new Slot((String) t[i][0], (SlotClass) t[i][1], (Integer) t[i][2],
                              (Integer) t[i][3], i);
        }
        return out;
    }

    /**
     * A slot by its name.
     *
     * @param id The name, e.g. {@code "H3"}.
     * @return The slot, or null.
     */
    static Slot slot(String id) {
        for (Slot s : SLOTS) if (s.id.equals(id)) return s;
        return null;
    }

    /**
     * The hover label's box top over a slot (dago §2.1, clip008 §2.3; V 30
     * labels): the sprite's top for the built houses, mid slots and the
     * fortification slot; vertically centred, sy + floor((h - 6)/2), for
     * the large slots, the harbour and every placeholder but the fence
     * (042 +15, 043 +8, 044 +10, 045 +21; the fence 016 at its top).
     *
     * @param s The slot.
     * @param placeholder Whether it shows its placeholder.
     * @return The box's y.
     */
    static int hoverY(Slot s, boolean placeholder) {
        final boolean centred = s.cls == SlotClass.LARGE || s.cls == SlotClass.DOCKS
            || (placeholder && s.cls != SlotClass.FENCE);
        return (centred) ? s.y + (s.cls.h - 6) / 2 : s.y;
    }

    /** The Ställe's «2» (the warehouse expansion): white FONTTINY, no box, at sprite + (21,8) (V dago). */
    static final int EXPANSION_DX = 21, EXPANSION_DY = 8;


    // Production rows (spec M4.3 target 1; dago §2.6, L §3.4)

    /** A production row: m icons of one good from {@link #x}(0), a count label when stacked. */
    static final class Row {

        /** The amount shown, the halving factor, the icons drawn and their pitch. */
        final int n, k, m, pitch;

        /** The first icon's x, the icons' y. */
        final int first, y;

        /** Whether the row is a stack (pitch 1) that carries a count label. */
        final boolean stacked;

        Row(int n, int k, int m, int pitch, int first, int y, boolean stacked) {
            this.n = n;
            this.k = k;
            this.m = m;
            this.pitch = pitch;
            this.first = first;
            this.y = y;
            this.stacked = stacked;
        }

        /**
         * @param j The icon, 0 ... m - 1.
         * @return Its x.
         */
        int x(int j) {
            return this.first + j * this.pitch;
        }

        /** @return The count label's box x: first + 2 (dago). */
        int labelX() {
            return countLabelX(this.first);
        }

        /** @return The count label's box y: the icons' y + 1 (dago). */
        int labelY() {
            return this.y + 1;
        }

        @Override
        public String toString() {
            return "n" + this.n + " k" + this.k + " m" + this.m + " p" + this.pitch + " x"
                + this.first + (this.stacked ? " label" : "");
        }
    }

    /**
     * A building's production row (dago §2.6, V 18 rows there and 6 in
     * clip 020): k = 1, m = n; while m &gt; T: k *= 2, m = n / k (integer
     * division); a spaced row's pitch p = min(w + 1, floor((S - w)/(m -
     * 1))); a stack (k &gt; 1, or p &lt; 2) has pitch 1 and a count label n;
     * total = (m - 1)p + w; first = sx + a - floor((total - 1)/2); the icons
     * at the class's y (Ställe: sy + 11).  V: 4 bells pitch 6 from 88 and
     * 6 bells pitch 4 from 87 in the Rathaus (#2260, #134173), 3 rum pitch
     * 6 from 3 and 9 rum stacked from 5 with «9» at (7,40) (H4), 12
     * hammers stacked from 16 with «12» at (18,105) (M4).
     *
     * @param s The slot.
     * @param stables Whether the sprite is the Ställe's (046/047: its row at sy + 11).
     * @param n The amount (at most 0: no row).
     * @param w The icon's width.
     * @return The row, or null.
     */
    static Row buildingRow(Slot s, boolean stables, int n, int w) {
        if (n <= 0 || s.cls.span == 0) return null;
        int k = 1, m = n;
        while (m > s.cls.most) {
            k *= 2;
            m = n / k;
        }
        int p = (m > 1) ? Math.min(w + 1, Math.floorDiv(s.cls.span - w, m - 1)) : w + 1;
        final boolean stacked = k > 1 || (m > 1 && p < 2);
        if (stacked) p = 1;
        final int total = (m - 1) * p + w;
        final int first = s.x + s.cls.rowAnchor - Math.floorDiv(total - 1, 2);
        final int y = s.y + ((stables) ? STABLES_ROW_Y : s.cls.rowY);
        return new Row(n, k, m, p, first, y, stacked);
    }

    /** The Ställe draw their row at sy + 11 (dago, V 3 colonies). */
    static final int STABLES_ROW_Y = 11;


    // Figures (spec M4.3 targets 2, 3; dago §2.7, L §3.5-3.6)

    /**
     * Where the workers of a building stand: x offsets from the sprite's x
     * (y at sy + the class's worker y), each with its 2-px shadow in ink
     * 95 (V).
     * <ul>
     *   <li>large: one +35; two +31, +40; three +27, +35, +43 (V dago);</li>
     *   <li>mid: one +27 (V ×4, widths 6 and 8); two +22, +33 (V M1 dago,
     *   M4 clip 020 #3500), in M2 +23, +33 (V dago ×2); three +18, +27,
     *   +36 (I: centred on +27, pitch 9);</li>
     *   <li>house: one +9 in H4 and H6, +8 in H3 and H7 (V clip 020 #2398,
     *   #2747, #19349, #31050, #47246, #2620; clip008 H6, H7: per slot,
     *   not by width), +9 in H1, H2, H5 (I); two or more from +5 at pitch
     *   floor((15 - w)/(n - 1)), w the widest (V: +5/+12 for 8 px, +5/+14
     *   for 6 px, +5/+8/+11, +5/+9/+13).</li>
     * </ul>
     * More than three (no building of the original holds more) continue
     * the class's pitch (I).
     *
     * @param s The slot.
     * @param n The workers.
     * @param w The widest worker's sprite width.
     * @return The x offsets.
     */
    static int[] workerOffsets(Slot s, int n, int w) {
        final int[] out = new int[Math.max(0, n)];
        if (n <= 0) return out;
        switch (s.cls) {
        case LARGE:
            return pick(n, new int[][] { { 35 }, { 31, 40 }, { 27, 35, 43 } }, 8);
        case MID:
            return pick(n, new int[][] { { 27 },
                    ("M2".equals(s.id)) ? new int[] { 23, 33 } : new int[] { 22, 33 },
                    { 18, 27, 36 } }, 9);
        case HOUSE:
            if (n == 1) {
                out[0] = ("H3".equals(s.id) || "H7".equals(s.id)) ? 8 : 9;
                return out;
            }
            final int p = Math.max(1, Math.floorDiv(15 - w, n - 1));
            for (int i = 0; i < n; i++) out[i] = 5 + i * p;
            return out;
        default:
            return new int[0];          // the harbour, the fortification: no workers
        }
    }

    /** A table's row for n, or the last row continued at the pitch. */
    private static int[] pick(int n, int[][] rows, int pitch) {
        if (n <= rows.length) return rows[n - 1].clone();
        final int[] last = rows[rows.length - 1], out = new int[n];
        for (int i = 0; i < n; i++) {
            out[i] = (i < last.length) ? last[i] : last[last.length - 1] + (i - last.length + 1) * pitch;
        }
        return out;
    }

    /** The figures' shadow: their silhouette at x - 2 in ink 95 (V dago, L). */
    static final int SHADOW_DX = -2;

    /**
     * The units outside the colony on the fortification slot (V #6560,
     * #46350, #46039; p1): x = 163 - ceil(11n/2) + 11i, y 111 (sy + 5): one
     * at 157, two at 152/163, three at 146/157/168.  When the row would
     * leave the slot (more than six, I) the pitch closes so that it stays
     * inside x 123-198, centred (I).
     *
     * @param n The units.
     * @param w The widest one's sprite width.
     * @return Their x.
     */
    static int[] outsideX(int n, int w) {
        final int[] out = new int[Math.max(0, n)];
        if (n <= 0) return out;
        final Slot x = SLOTS[SLOTS.length - 1];
        final int first = 163 - (11 * n + 1) / 2;
        if (first >= x.x && first + 11 * (n - 1) + w <= BUILDING_AREA.width) {
            for (int i = 0; i < n; i++) out[i] = first + 11 * i;
            return out;
        }
        final int room = BUILDING_AREA.width - x.x;      // x 123-198
        final int p = (n > 1) ? Math.max(1, (room - w) / (n - 1)) : 0;
        final int f = x.x + Math.max(0, (room - ((n - 1) * p + w)) / 2);
        for (int i = 0; i < n; i++) out[i] = f + i * p;
        return out;
    }

    /** The units outside stand at y 111 (the fortification slot's sy + 5). */
    static final int OUTSIDE_Y = 111;

    /**
     * The selected figure's green frame (ink 10), around the figure and
     * its shadow: x - 3 ... x + w, y + 1 ... y + 16 (V #3500: the
     * Holzfäller at (37,111), frame x 34-45, y 112-127; clip008 the
     * Zimmerei's x 34-43, y 86-101), drawn right after the figure.
     *
     * @param x The figure's x.
     * @param y The figure's y.
     * @param w Its width.
     * @return The frame.
     */
    static Rectangle workerFrame(int x, int y, int w) {
        return new Rectangle(x - 3, y + 1, w + 4, 16);
    }


    // The figure row in the band (L §5.2, dago §4.2; the look only)

    // The 3x3 colony map (spec M4.4; L §4.1, dago §3; V clip 020, 17 rows at 0 px)

    /** The 3x3 map's tiles in their order: index = 3 * row + column, the centre 4. */
    static final int TILE_COUNT = 9, CENTRE_TILE = 4;

    /** The tiles' names in their order (the recorder, the reports). */
    static final String[] TILE_NAMES = { "NW", "N", "NE", "W", "C", "E", "SW", "S", "SE" };

    /**
     * A tile's cell: 24x24 at (224 + 24c, 32 + 24r).
     *
     * @param i The tile, 0-8 (3 * row + column).
     * @return Its rectangle.
     */
    static Rectangle tileCell(int i) {
        return new Rectangle(TILES_X + TILE * (i % 3), TILES_Y + TILE * (i / 3), TILE, TILE);
    }

    /**
     * The tile under a point.
     *
     * @param x The x.
     * @param y The y.
     * @return Its index, or -1 outside the 3x3 map's cells.
     */
    static int tileAt(int x, int y) {
        if (x < TILES_X || y < TILES_Y || x >= TILES_X + 3 * TILE || y >= TILES_Y + 3 * TILE) {
            return -1;
        }
        return 3 * ((y - TILES_Y) / TILE) + (x - TILES_X) / TILE;
    }

    /**
     * A tile's production row's icon y: the tile's y + 1; the centre's
     * second row (its other good) at y + 14 (V clip 020 #2260, #14817,
     * #123003, #134173; dago Base, Fur City, Fur Town).
     */
    static final int TILE_ROW_DY = 1, TILE_SECOND_ROW_DY = 14;

    /**
     * A tile's production row (dago §3.3, V 29 rows there and 17 in clip
     * 020 at 0 px): R = 24 - w; m = n; while (m &gt; 1 and m - 1 &gt; R) m
     * /= 2; p = min(w + 1, floor(R/(m - 1))); total = (m - 1)p + w; first
     * = tile x + floor((24 - total)/2); a count label n at (first + 2, row
     * y + 1) when p &lt;= 1.  V: corn 5 from 224 at pitch 4, lumber 8 from
     * 248 at 2, corn 6 from 249 at 3, sugar 3 from 248 at 8, ore 5 from 273
     * at 3, fish 6 from 273 at 3, sugar 12 from 250 at 1 with «12» at
     * (252,34), sugar 1 at 256, corn 4 from 249 at 5, sugar 2 from 251 at 9,
     * silver 3 from 224 at 7, furs 3 from 248 at 7 (L §4.1); halved rows
     * dago's 30 lumber (m 15), 18 ore, 22 tobacco, labelled (p 1).  A
     * halved row with p &gt;= 2 (16 lumber: m 8 at 2) carries no label by
     * this rule (I: never seen, L §4.1: it cannot be told from 8).
     *
     * @param tileX The tile's x.
     * @param rowY The icons' y.
     * @param n The amount (at most 0: no row).
     * @param w The icon's width.
     * @return The row, or null.
     */
    static Row tileRow(int tileX, int rowY, int n, int w) {
        if (n <= 0) return null;
        final int r = TILE - w;
        int m = n, k = 1;
        while (m > 1 && m - 1 > r) {
            m /= 2;
            k *= 2;
        }
        final int p = (m > 1) ? Math.min(w + 1, Math.floorDiv(r, m - 1)) : 0;
        final int total = (m - 1) * p + w;
        final int first = tileX + Math.floorDiv(TILE - total, 2);
        return new Row(n, k, m, p, first, rowY, m > 1 && p <= 1);
    }

    /**
     * A tile's worker: his figure at tile + (14,6), whatever its width,
     * after the row, with a 2-px black shadow (ink 0; V clip 020 ×9, dago
     * 24 workers).
     */
    static final int TILE_WORKER_DX = 14, TILE_WORKER_DY = 6;

    /**
     * A worker who makes nothing on his tile: the good's icon at
     * {@link #zeroIconX}, the tile's y + 1, and over it the red prohibition
     * sign ICONS.SS.064 (16x16) at the tile's corner (V: clip 020 #48712,
     * #50260 and playthrough-1 #75026, #75313, each a sugar planter on
     * hills: 0 px), then the worker.
     */
    static final int ICON_NO_PRODUCTION = 64;

    /**
     * The good's icon under the prohibition sign: centred in the sign's 16
     * columns, x = tile x + floor((16 - w)/2) (V: sugar, w 8, at tile + 4;
     * I for the other widths).
     *
     * @param tileX The tile's x.
     * @param w The icon's width.
     * @return Its x.
     */
    static int zeroIconX(int tileX, int w) {
        return tileX + Math.floorDiv(16 - w, 2);
    }

    /** Native land: the totem ICONS.SS.108 at tile + (8,4) (V clip 020 #14817, NW, W, SW, S). */
    static final int ICON_TOTEM = 108, TOTEM_DX = 8, TOTEM_DY = 4;

    /**
     * An enemy unit on a work tile at war: the map's unit icon with its
     * flag at tile + (4,4) (V war-french #13962: the dragoon's flag at
     * (228,60) on W, the soldier's at (284,91) on SE; part M2).
     */
    static final int ENEMY_DX = 4, ENEMY_DY = 4;

    /**
     * A tile's hover label's box x (dago §3.5, V «Ozean» x 226-246 on W,
     * «Grünland/Straße/Gepflügt» x 213-306 on N): tile x + 12 - (w + 1)/2,
     * not clamped; its y the tile's y.
     *
     * @param tileX The tile's x.
     * @param w The label's text width.
     * @return The box's x.
     */
    static int tileLabelX(int tileX, int w) {
        return tileX + TILE / 2 - (w + 1) / 2;
    }

    /** The figure row's y (no shadow). */
    static final int FIGURE_ROW_Y = 142;

    /** The figure row's last pixel column (V: 95 accepted, 97 rejected; I: 96). */
    static final int FIGURE_ROW_END = 96;

    /**
     * The figure row's x (L §5.2, dago §4.2): from 2, each next one at x +
     * w + 1 - c with the least compression c (-3, the pitch w + 2, V clip
     * 020; 0 ... 3, V dago) that keeps the row's last pixel at x 96 at most;
     * 4 px more before the first unit outside the colony (V #6560: 44,
     * #46039: 52).  dago's x(i+1) = x(i) + w(i) - 1 - c.
     *
     * @param widths The figures' widths: the colonists, then the units outside.
     * @param colonists How many are colonists.
     * @return Their x.
     */
    static int[] figureRow(int[] widths, int colonists) {
        final int n = widths.length;
        final int[] out = new int[n];
        int widest = 1;
        for (int w : widths) widest = Math.max(widest, w);
        // Beyond c = widest - 2 every step is 1 px: the row cannot close more.
        for (int c = -3; ; c++) {
            int x = 2;
            for (int i = 0; i < n; i++) {
                if (i > 0) {
                    x += Math.max(1, widths[i - 1] - 1 - c);
                    if (i == colonists) x += 4;
                }
                out[i] = x;
            }
            if (n == 0 || out[n - 1] + widths[n - 1] - 1 <= FIGURE_ROW_END
                || c >= widest - 2) return out;
        }
    }

    /**
     * The selected figure's green frame in the figure row: x - 1 ... x + w,
     * y 143-158 (V #2260, #134173, #14817), drawn right after it.
     *
     * @param x The figure's x.
     * @param w Its width.
     * @return The frame.
     */
    static Rectangle figureFrame(int x, int w) {
        return new Rectangle(x - 1, FIGURE_ROW_Y + 1, w + 2, 16);
    }

    /**
     * The white cursor on a figure of the band's figure row (V clip 020
     * #123003-#123095 Silver City, #6540-#6600, #2620, #35057): the green
     * frame's place, x - 1 ... x + w, y 143-158, white (ink 15) in the
     * blink's on half and nothing in its off half (the figure's green frame
     * is not drawn under it: #6570, #46350, #47291, #123003).
     *
     * @param x The figure's x.
     * @param w Its width.
     * @return The frame.
     */
    static Rectangle figureCursor(int x, int w) {
        return figureFrame(x, w);
    }


    // The band's left panel: the SoL line and the production row (spec
    // M4.5; L §5.1, §5.3, dago §4.1, §4.3; V clip 020 and dago at 0 px)

    /** The SoL flag ICONS.SS.123 (its 3 canton pixels cycle with the water) and the Tory crown 124. */
    static final int ICON_SOL = 123, ICON_TORY = 124;

    /**
     * The count «(n)» of the SoL percentage: the members rounded, (percent
     * * population + 50) div 100 (V: 11 % of 5 -&gt; 1, 14 % of 4 -&gt; 1, 24 %
     * of 8 -&gt; 2, 89 % of 5 -&gt; 4, 86 % of 4 -&gt; 3, 76 % of 8 -&gt; 6; dago
     * 34 % of 14 -&gt; 5, 66 % -&gt; 9; I: a half rounds up, never seen).  The
     * Tories' count is the rest, population - this ({@link
     * ClassicColonyBand.Model}; equal to this formula wherever no half
     * occurs).  FreeCol's own count rounds down (11 % of 5 -&gt; 0).
     *
     * @param percent The percentage, 0-100.
     * @param population The colonists.
     * @return The count.
     */
    static int memberCount(int percent, int population) {
        return Math.max(0, (percent * population + 50) / 100);
    }

    /**
     * The SoL or Tory line's words: {@code "<p>% (<n>)"} («11% (1)», «100%
     * (1)», V).
     *
     * @param percent The percentage.
     * @param count Its count ({@link #memberCount}).
     * @return The words.
     */
    static String memberText(int percent, int count) {
        return percent + "% (" + count + ")";
    }

    /** The production row's icon y, its first x and the gap between its groups. */
    static final int BAND_ROW_Y = 163, BAND_ROW_X = 2, BAND_GROUP_GAP = 4;

    /**
     * The production row's last pixel column (V: #2260 ends at 117 with
     * pitch 6, dago Base at k = 1 would end at 120 and is halved; I: 118,
     * the left panel's last column).
     */
    static final int BAND_ROW_RIGHT = 118;

    /**
     * A group's count label in the production row: its black box at
     * (first + 2, 165), the icon y + 2 (one lower than on the buildings and
     * tiles), the number white one pixel in (V dago #1000 «38» box x 4-12,
     * y 165-171; Fur City «29», «20»; Fur Town «41», «10», «2», «96»).
     */
    static final int BAND_LABEL_DY = 2;

    /** The icons of the production row (dago §4.3): fish 057, corn 022, crosses 056, bells 062; a shortfall's red X 055. */
    static final int ICON_FISH = 57, ICON_CORN = 22, ICON_CROSSES = 56, ICON_BELLS = 62,
        ICON_SHORTFALL = 55;

    /**
     * The band's production row laid out (L §5.3, dago §4.3): its groups,
     * each of one or two parts (food used: the fish part, then the corn
     * part), each part a run of one good's icons.
     */
    static final class BandRow {

        /** Each icon stands for k units; the pitch the icons use at most. */
        final int k, pitch;

        /** The amounts, per group and part, as given. */
        final int[][] n;

        /** The icons' x, per group, part and icon (empty for a group of 0). */
        final int[][][] x;

        /** Whether a group carries its count label, and the label's box x. */
        final boolean[] labelled;
        final int[] labelX;

        /** The row's last pixel column (-1 for an empty row). */
        final int end;

        BandRow(int k, int pitch, int[][] n, int[][][] x, boolean[] labelled, int[] labelX,
                int end) {
            this.k = k;
            this.pitch = pitch;
            this.n = n;
            this.x = x;
            this.labelled = labelled;
            this.labelX = labelX;
            this.end = end;
        }

        /**
         * @param g A group.
         * @return Its amount, the parts' sum (the label's number).
         */
        int total(int g) {
            int s = 0;
            for (int v : this.n[g]) s += Math.max(0, v);
            return s;
        }

        /**
         * @param g A group.
         * @return Its first icon's x, or -1 for an empty group.
         */
        int first(int g) {
            for (int[] part : this.x[g]) if (part.length > 0) return part[0];
            return -1;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("k" + this.k + " p" + this.pitch);
            for (int g = 0; g < this.x.length; g++) {
                sb.append(" |");
                for (int[] part : this.x[g]) {
                    sb.append(' ').append(part.length).append('@')
                        .append((part.length == 0) ? "-" : String.valueOf(part[0]));
                }
                if (this.labelled[g]) sb.append(" \"").append(total(g)).append('"');
            }
            return sb.toString();
        }
    }

    /**
     * The band's production row (L §5.3, dago §4.3; V clip 020 #2260,
     * #134173, #14817, #18955 and dago's three colonies at 0 px): from x 2,
     * each group after the previous one's last icon + its width + 4; one
     * pitch p for the whole row: each step min(w + 1, p), w the icon's
     * width (the fish part's last icon to the corn part's first alike), p
     * the largest for which the row ends at x {@link #BAND_ROW_RIGHT} at
     * most (V: 6 at #2260, 3 at #134173, w + 1 at #14817); only when p = 1
     * does not fit, one scale k = 2, 4 ... for the whole row, each icon k
     * units (a part's icons n div k; I: a group keeps one icon at least),
     * pitch 1 (V dago Base and Fur Town).  A group carries its count label
     * when k &gt; 1 (dago: Fur Town's single cross «2») or when the pitch is
     * 1 and it has more than one icon (Fur City «29», «20», not its 1
     * cross); none otherwise (V: no label at pitch 3 or 6).
     *
     * @param n The amounts per group and part (a group of 0 is left out).
     * @param w The icons' widths per group and part.
     * @return The row.
     */
    static BandRow bandRow(int[][] n, int[][] w) {
        int widest = 1;
        for (int[] g : w) for (int v : g) widest = Math.max(widest, v);
        for (int p = widest + 1; p >= 1; p--) {
            final BandRow r = bandRowAt(n, w, 1, p);
            if (r.end <= BAND_ROW_RIGHT) return r;
        }
        for (int k = 2; ; k *= 2) {
            final BandRow r = bandRowAt(n, w, k, 1);
            if (r.end <= BAND_ROW_RIGHT || k >= (1 << 20)) return r;
        }
    }

    /**
     * The row at a given scale and pitch ({@link #bandRow}; tests).
     *
     * @param n The amounts per group and part.
     * @param w The icons' widths.
     * @param k The scale.
     * @param p The pitch.
     * @return The row.
     */
    static BandRow bandRowAt(int[][] n, int[][] w, int k, int p) {
        final int groups = n.length;
        final int[][][] xs = new int[groups][][];
        final boolean[] labelled = new boolean[groups];
        final int[] labelX = new int[groups];
        int lastX = -1, lastW = 0;
        for (int g = 0; g < groups; g++) {
            final int parts = n[g].length;
            xs[g] = new int[parts][];
            final int[] m = new int[parts];
            int total = 0, icons = 0, lastPart = -1;
            for (int q = 0; q < parts; q++) {
                final int v = Math.max(0, n[g][q]);
                total += v;
                m[q] = (k == 1) ? v : v / k;
                icons += m[q];
                if (v > 0) lastPart = q;
            }
            if (total > 0 && icons == 0) {
                m[lastPart] = 1;                     // I: one icon at least
                icons = 1;
            }
            int x = (lastX < 0) ? BAND_ROW_X : lastX + lastW + BAND_GROUP_GAP;
            boolean firstInGroup = true;
            int prevW = 0;
            for (int q = 0; q < parts; q++) {
                xs[g][q] = new int[m[q]];
                for (int j = 0; j < m[q]; j++) {
                    if (!firstInGroup) x += Math.min(prevW + 1, p);
                    xs[g][q][j] = x;
                    prevW = w[g][q];
                    firstInGroup = false;
                    lastX = x;
                    lastW = w[g][q];
                }
            }
            if (icons > 0) {
                labelled[g] = k > 1 || (p <= 1 && icons > 1);
                int first = -1;
                for (int[] part : xs[g]) {
                    if (part.length > 0) {
                        first = part[0];
                        break;
                    }
                }
                labelX[g] = first + 2;
            }
        }
        return new BandRow(k, p, n, xs, labelled, labelX,
                           (lastX < 0) ? -1 : lastX + lastW - 1);
    }


    // The port panel (spec M4.6; L §6, clip008 §2, dago §5)

    /**
     * The carrier in port: its 16x16 cell at (130,146) for a ship and
     * (130,147) for a wagon train, the sprite in it as the map's
     * ({@link ClassicHud#spriteOffset}: ICONS.SS.006 at (133,146), 008 at
     * (132,147); V #2260, #9921, #45944, #118448, #134173; I: why the
     * wagon train sits one lower).
     */
    static final int CARRIER_X = 130, SHIP_Y = 146, WAGON_Y = 147;

    /**
     * @param naval Whether the carrier is a ship.
     * @return Its 16x16 cell.
     */
    static Rectangle carrierCell(boolean naval) {
        return new Rectangle(CARRIER_X, (naval) ? SHIP_Y : WAGON_Y, 16, 16);
    }

    /**
     * The carrier's 18x18 frame around its cell: green (ink 10), white
     * (ink 15) while the white cursor stands on it in the blink's on half
     * (V: ship (129,145)-(146,162), wagon train (129,146)-(146,163);
     * #120499 white, #120521 green, #2260 green throughout with the cursor
     * on a tile).
     *
     * @param naval Whether the carrier is a ship.
     * @return The frame.
     */
    static Rectangle carrierFrame(boolean naval) {
        final Rectangle c = carrierCell(naval);
        return new Rectangle(c.x - 1, c.y - 1, c.width + 2, c.height + 2);
    }

    /**
     * The port's full-size carriers: at most four in a row, 18 px apart
     * (V clip 040 #46 948, #52 700, #61 150: a fifth would leave the
     * port; the rest are tiny icons, {@link #tinyIcon}).
     */
    static final int FULL_CARRIERS = 4, CARRIER_PITCH = 18;

    /**
     * Slot i's 16x16 cell (V clip 040, {@code 10-port-carriers.md} §1.2): x
     * 130 + 18i; a ship's y 146 in slot 0 and 145 in slots 1-3 (V every
     * frame: the galleon ICONS.SS.007 at (150,145) #61 150, dy -1 for every
     * non-front ship; I: why), a wagon train's 147 in every slot.
     *
     * @param naval Whether the carrier is a ship.
     * @param i The slot, 0-3.
     * @return The cell.
     */
    static Rectangle carrierCell(boolean naval, int i) {
        final int y = (naval) ? SHIP_Y - ((i >= 1) ? 1 : 0) : WAGON_Y;
        return new Rectangle(CARRIER_X + CARRIER_PITCH * i, y, 16, 16);
    }

    /**
     * Slot i's 18x18 frame (V clip 040: ship (129,145)-(146,162) in slot 0,
     * (147,144)-(164,161) in slot 1 #62 060; wagon train (129 + 18i,
     * 146)-(146 + 18i, 163), #52 642, #50 941, #66 399).
     *
     * @param naval Whether the carrier is a ship.
     * @param i The slot, 0-3.
     * @return The frame.
     */
    static Rectangle carrierFrame(boolean naval, int i) {
        final Rectangle c = carrierCell(naval, i);
        return new Rectangle(c.x - 1, c.y - 1, c.width + 2, c.height + 2);
    }

    /** The tiny icons' first x, pitch and y (V clip 040: (123 + 5k, 139)). */
    static final int TINY_X = 123, TINY_PITCH = 5, TINY_Y = 139, TINY_SIZE = 4;

    /**
     * The 5th, 6th ... carrier's tiny icon k (k = 0 for the 5th): 4x4 at
     * (123 + 5k, 139) (V clip 040 #52 642 k 0, #61 150 k 0 and 1, 0 px;
     * I for k 2 on: x 133 as the pitch gives).
     *
     * @param k The tiny icon.
     * @return Its 4x4 rectangle.
     */
    static Rectangle tinyIcon(int k) {
        return new Rectangle(TINY_X + TINY_PITCH * k, TINY_Y, TINY_SIZE, TINY_SIZE);
    }

    /**
     * A selected tiny icon's 1-px frame, 5x6 at (123 + 5k, 138) over the
     * icon's left column (V clip 040 #52 909 white, #53 000 green, #67 090
     * k 1, #67 299 k 0).
     *
     * @param k The tiny icon.
     * @return The frame.
     */
    static Rectangle tinyFrame(int k) {
        return new Rectangle(TINY_X + TINY_PITCH * k, TINY_Y - 1, 5, 6);
    }

    /**
     * The hold under a point (the goods' drags, M4.9): hold k's box x 127 +
     * 12k ... 138 + 12k, y 164-177 (its icon at y 165, the PIK's box y
     * 165-176; I: the press area one pixel round it).
     *
     * @param x The x.
     * @param y The y.
     * @return The hold, 0-5, or -1.
     */
    static int holdAt(int x, int y) {
        if (y < HOLD_Y - 1 || y > HOLD_Y + 12 || x < 127) return -1;
        final int k = (x - 127) / HOLD_PITCH;
        return (k >= 0 && k < HOLDS) ? k : -1;
    }

    /** The six holds: the icons' y 165, pitch 12. */
    static final int HOLDS = 6, HOLD_Y = 165, HOLD_PITCH = 12;

    /**
     * Hold k's goods icon: x = 128 + 12k + floor((9 - w)/2), y 165 (V: ore
     * w 10 at 127, rum w 7 at 129 and 141, furs w 9 at 128 and 140; clip008
     * §2.5).
     *
     * @param k The hold, 0-5.
     * @param w The icon's width.
     * @return Its x.
     */
    static int holdIconX(int k, int w) {
        return 128 + HOLD_PITCH * k + Math.floorDiv(9 - w, 2);
    }

    /**
     * A hold the carrier lacks: the crate ICONS.SS.122 at (127 + 12k, 165)
     * (V: the Handelsschiff's 4 and 5 at 175/187, the wagon train's 2-5,
     * all six without a carrier).
     *
     * @param k The hold.
     * @return Its x.
     */
    static int crateX(int k) {
        return 127 + HOLD_PITCH * k;
    }

    /** The crate over a hold the carrier lacks. */
    static final int ICON_CRATE = 122;


    /**
     * NAMES.TXT {@code @JOB}'s index of a work (the order of the figure
     * row, L §5.2: Farmer 0, Zuckerpflanzer 1 ... Fischer 8, Schnapsbrenner
     * 9 ... Staatsmann 17, Lehrer 18, Siedler 19, Pionier 20, Soldat 21,
     * Späher 22, Dragoner 23, Missionar 24, Knecht 25, Krimineller 26), by
     * the good the work makes.
     *
     * @param goodsId The good's FreeCol id.
     * @return The index, or -1 for none.
     */
    static int jobOf(String goodsId) {
        if (goodsId == null) return -1;
        // FreeCol's farmed food kinds (stored as food): the Farmer.
        if ("model.goods.grain".equals(goodsId) || "model.goods.meat".equals(goodsId)) return 0;
        for (int i = 0; i < JOB_GOODS.length; i++) {
            if (JOB_GOODS[i].equals(goodsId)) return i;
        }
        return -1;
    }

    /** The goods of NAMES {@code @JOB} 0-17, in their order. */
    private static final String[] JOB_GOODS = {
        "model.goods.food", "model.goods.sugar", "model.goods.tobacco",
        "model.goods.cotton", "model.goods.furs", "model.goods.lumber",
        "model.goods.ore", "model.goods.silver", "model.goods.fish",
        "model.goods.rum", "model.goods.cigars", "model.goods.cloth",
        "model.goods.coats", "model.goods.hammers", "model.goods.tools",
        "model.goods.muskets", "model.goods.crosses", "model.goods.bells",
    };

    /**
     * The good a NAMES {@code @JOB} work 0-17 makes (the job menus, M4.8;
     * the inverse of {@link #jobOf}).
     *
     * @param job The index.
     * @return The good's FreeCol id, or null for another index (the
     *     teacher, the roles).
     */
    static String jobGoods(int job) {
        return (job < 0 || job >= JOB_GOODS.length) ? null : JOB_GOODS[job];
    }

    /** The farmed works and the fisherman's, NAMES {@code @JOB} 0-8 (the tiles' jobs). */
    static final int TILE_JOBS = 9;

    /** NAMES {@code @JOB} 8: Fischer (the water tiles' work). */
    static final int JOB_FISHERMAN = 8;

    /** NAMES {@code @JOB}: Lehrer, Siedler, Pionier, Soldat, Späher, Dragoner, Missionar, Knecht, Krimineller. */
    static final int JOB_TEACHER = 18, JOB_COLONIST = 19, JOB_PIONEER = 20,
        JOB_SOLDIER = 21, JOB_SCOUT = 22, JOB_DRAGOON = 23, JOB_MISSIONARY = 24,
        JOB_SERVANT = 25, JOB_CRIMINAL = 26;

    /**
     * The ICONS.SS frame of a good in a production row (dago §2.6): the 16
     * warehouse goods 022-037, fish 057, hammers 054, crosses 056, bells 062.
     *
     * @param goodsId The good's FreeCol id.
     * @return The frame, or -1.
     */
    static int goodsIcon(String goodsId) {
        if (goodsId == null) return -1;
        for (int i = 0; i < WAREHOUSE_GOODS.length; i++) {
            if (WAREHOUSE_GOODS[i].equals(goodsId)) return ICON_GOODS + i;
        }
        switch (goodsId) {
        case "model.goods.grain": case "model.goods.meat": return ICON_GOODS;
        case "model.goods.fish": return 57;
        case "model.goods.hammers": return 54;
        case "model.goods.crosses": return 56;
        case "model.goods.bells": return 62;
        default: return -1;
        }
    }


    // The right panel (spec M4.7; L §7, dago §6, clip008 §2, clip 019 §4)

    /**
     * The three mode buttons' sprites: ICONS.SS.067 (house), 068 (musket),
     * 069 (hammer), 14x13 each, their bevel and picture in one (found in the
     * pack: every opaque pixel equal at clip 020 #2260 and #9921).
     */
    static final int ICON_MODE_HOUSE = 67;

    /**
     * A mode button's shadow: ink 63 (#08086D), one pixel beside the
     * sprite (V #2260, #9921: a raised button's sprite at (303, y) with
     * the shadow on its right column x 317, y + 1 ... y + 13 and its bottom
     * row y + 13, x 304-317; the pressed one's sprite one lower and one to
     * the right, (304, y + 1), the shadow on its top row y, x 303-316 and
     * its left column x 303, y ... y + 12).
     */
    static final int INK_BUTTON_SHADOW = 63;

    /** The raised bevel's light ink (a raised button reads 54 at (304, y + 1): its inside). */
    static final int INK_BUTTON_FACE = 54;

    /**
     * A mode button's sprite place.
     *
     * @param mode The button, 0 house, 1 musket, 2 hammer.
     * @param pressed Whether it is the pressed one.
     * @return The sprite's top-left corner.
     */
    static java.awt.Point modeSprite(int mode, boolean pressed) {
        return new java.awt.Point(MODE_X + ((pressed) ? 1 : 0),
                                  MODE_Y[mode] + ((pressed) ? 1 : 0));
    }

    /**
     * A mode button's 15x14 cell (its press area): x 303-317, y 132-145 /
     * 147-160 / 162-175.
     *
     * @param mode The button.
     * @return The cell.
     */
    static Rectangle modeCell(int mode) {
        return new Rectangle(MODE_X, MODE_Y[mode], MODE_W, MODE_H);
    }

    /**
     * The mode button under a point.
     *
     * @param x The x.
     * @param y The y.
     * @return The button, 0-2, or -1.
     */
    static int modeAt(int x, int y) {
        for (int i = 0; i < MODE_Y.length; i++) if (modeCell(i).contains(x, y)) return i;
        return -1;
    }

    /** The house panel's three rows: icon y 134 / 148 / 162 (V #2260, #14817, #123003; dago #1000). */
    static final int[] HOUSE_ROW_Y = { 134, 148, 162 };

    /** The house panel's rows start at x 213. */
    static final int HOUSE_X = 213;

    /** The gap between two groups: 2 px in rows 1-2, 4 px in row 3 (V #2260, #123003; dago). */
    static final int[] HOUSE_GAP = { 2, 2, 4 };

    /**
     * The house panel's last pixel column: 302, before the mode buttons
     * (V: #2260's row 3 ends at 290 at pitch 2 and would end at 311 at
     * pitch 3; dago's row 3 would end at 309 at scale 1; I: 302).
     */
    static final int HOUSE_RIGHT = 302;

    /** A part's count label: its box at (first + 2, icon y + 2), as the band's (dago #1000: «34» glyphs at y 137). */
    static final int HOUSE_LABEL_DY = 2;

    /** The red count label of a crossed part: its number in ink 12 on the black box (dago #1000 «14», «2»). */
    static final int INK_RED = 12;

    /**
     * A house panel row laid out: per good its two parts, the icons it
     * makes and, after them, the crossed ones (a shortfall).
     */
    static final class HouseRow {

        /** Each icon stands for k units; the pitch the icons use at most. */
        final int k, pitch;

        /** The amounts per good and part (0 made, 1 crossed). */
        final int[][] n;

        /** The icons' x per good, part and icon. */
        final int[][][] x;

        /** Whether a part carries its count label, and the label's box x. */
        final boolean[][] labelled;
        final int[][] labelX;

        /** The row's last pixel column (-1 for an empty row). */
        final int end;

        HouseRow(int k, int pitch, int[][] n, int[][][] x, boolean[][] labelled,
                 int[][] labelX, int end) {
            this.k = k;
            this.pitch = pitch;
            this.n = n;
            this.x = x;
            this.labelled = labelled;
            this.labelX = labelX;
            this.end = end;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("k" + this.k + " p" + this.pitch);
            for (int g = 0; g < this.x.length; g++) {
                if (this.x[g][0].length + this.x[g][1].length == 0) continue;
                sb.append(" |");
                for (int q = 0; q < 2; q++) {
                    if (this.x[g][q].length == 0) continue;
                    sb.append(' ').append((q == 1) ? "x" : "").append(this.x[g][q].length)
                        .append('@').append(this.x[g][q][0]);
                    if (this.labelled[g][q]) sb.append(" \"").append(this.n[g][q]).append('"');
                }
            }
            return sb.toString();
        }
    }

    /**
     * A house panel row (L §7.2, dago §6.1; V clip 020 #2260, #14817,
     * #123003 and dago #1000 at 0 px): from x 213, the goods in their
     * order, each the icons it makes, then its shortfall's icons crossed
     * (the red X 055 over each, V); in rows 1 and 2 a good's crossed icons
     * go on at the good's pitch (V dago: 17 tobacco to x 229, the crossed
     * from 230; #14817: the horse at 213, the crossed one at 227), in row 3
     * they are a group of their own (V #2260: 8 lumber, 4 px, the 4
     * crossed, 4 px, the hammers; dago: lumber, 2 crossed, hammers); a
     * group after the previous one's last icon + its width + the row's gap
     * (2 or 4); one pitch for the whole row, each step min(w + 1, p), p the
     * largest that ends the row at x 302 at most (V #2260: 2, #14817 and
     * #123003: w + 1); only when pitch 1 does not fit, one scale k = 2, 4
     * ... at pitch 1, a part's icons its amount div k (V dago: k 2 in rows
     * 1 and 3, k 4 in row 2; cotton 5 -&gt; 2 icons; I: a part keeps one
     * icon at least).  A part carries its count label when k &gt; 1 or at
     * pitch 1 with more than one icon (V dago: every part of the three
     * rows; as the band's).
     *
     * @param made The amounts made, per good.
     * @param crossed The shortfalls, per good.
     * @param w The goods' icon widths.
     * @param row The row, 0-2.
     * @return The row.
     */
    static HouseRow houseRow(int[] made, int[] crossed, int[] w, int row) {
        int widest = 1;
        for (int g = 0; g < w.length; g++) {
            if (made[g] > 0 || crossed[g] > 0) widest = Math.max(widest, w[g]);
        }
        for (int p = widest + 1; p >= 1; p--) {
            final HouseRow r = houseRowAt(made, crossed, w, row, 1, p);
            if (r.end <= HOUSE_RIGHT) return r;
        }
        for (int k = 2; ; k *= 2) {
            final HouseRow r = houseRowAt(made, crossed, w, row, k, 1);
            if (r.end <= HOUSE_RIGHT || k >= (1 << 20)) return r;
        }
    }

    /**
     * A house panel row at a given scale and pitch ({@link #houseRow}; tests).
     *
     * @param made The amounts made.
     * @param crossed The shortfalls.
     * @param w The icon widths.
     * @param row The row, 0-2.
     * @param k The scale.
     * @param p The pitch.
     * @return The row.
     */
    static HouseRow houseRowAt(int[] made, int[] crossed, int[] w, int row, int k, int p) {
        final int goods = made.length;
        final int gap = HOUSE_GAP[Math.max(0, Math.min(2, row))];
        final boolean apart = row >= 2;
        final int[][] n = new int[goods][2];
        final int[][][] xs = new int[goods][2][];
        final boolean[][] labelled = new boolean[goods][2];
        final int[][] labelX = new int[goods][2];
        int lastX = -1, lastW = 0;
        for (int g = 0; g < goods; g++) {
            n[g][0] = Math.max(0, made[g]);
            n[g][1] = Math.max(0, crossed[g]);
            boolean newGroup = true;
            for (int q = 0; q < 2; q++) {
                final int v = n[g][q];
                int m = (k == 1) ? v : v / k;
                if (v > 0 && m == 0) m = 1;                  // I: one icon at least
                xs[g][q] = new int[m];
                if (m == 0) continue;
                if (q == 1 && apart) newGroup = true;
                int x;
                if (lastX < 0) {
                    x = HOUSE_X;
                } else if (newGroup) {
                    x = lastX + lastW + gap;
                } else {
                    x = lastX + Math.min(lastW + 1, p);
                }
                for (int j = 0; j < m; j++) {
                    if (j > 0) x += Math.min(w[g] + 1, p);
                    xs[g][q][j] = x;
                }
                lastX = x;
                lastW = w[g];
                newGroup = false;
                labelled[g][q] = k > 1 || (p <= 1 && m > 1);
                labelX[g][q] = xs[g][q][0] + 2;
            }
        }
        return new HouseRow(k, p, n, xs, labelled, labelX,
                            (lastX < 0) ? -1 : lastX + lastW - 1);
    }

    /**
     * The musket panel's «Vorhandene Einheiten»: unit i in a 16x16 cell at
     * (213 + 18i, 158), drawn as the map draws it (V clip 019 #1290: the
     * dragoon's cell 213, the artillery's 231, 249, 267; dago #6000: 213 ...
     * 285).
     */
    static final int UNIT_LIST_X = 213, UNIT_LIST_Y = 158, UNIT_LIST_PITCH = 18;

    /**
     * The step between the list's cells: 18 while the cells fit before x
     * 302 (five), closer beyond so that every unit can be pressed (I: never
     * seen; the topmost wins where cells overlap).
     *
     * @param n The units.
     * @return The step.
     */
    static int unitListPitch(int n) {
        if (n <= 1) return UNIT_LIST_PITCH;
        final int room = HOUSE_RIGHT - UNIT_LIST_X + 1 - 16;
        return Math.max(1, Math.min(UNIT_LIST_PITCH, room / (n - 1)));
    }

    /**
     * Unit i's cell in the list.
     *
     * @param i The unit.
     * @param n The units.
     * @return Its 16x16 cell.
     */
    static Rectangle unitListCell(int i, int n) {
        return new Rectangle(UNIT_LIST_X + i * unitListPitch(n), UNIT_LIST_Y, 16, 16);
    }

    /**
     * The selected unit's frame in the list: 18x18 around its cell, green;
     * white while the white cursor stands on it in the blink's on half (V
     * clip 019: x 212-229, y 157-174, white #366, green #388 ...; dago
     * #6000 green x 212-229).
     *
     * @param cell The cell.
     * @return The frame.
     */
    static Rectangle unitListFrame(Rectangle cell) {
        return new Rectangle(cell.x - 1, cell.y - 1, cell.width + 2, cell.height + 2);
    }

    /** The hammer panel's KAUFEN and ÄNDERN boxes: x 216-244 and 270-298, y 138-146 (V). */
    static final Rectangle BUY_BOX = new Rectangle(216, 138, 29, 9);
    static final Rectangle CHANGE_BOX = new Rectangle(270, 138, 29, 9);

    /** Their words: white at box + (3, 2) (V: (219,140), (273,140)). */
    static final int BOX_TEXT_DX = 3, BOX_TEXT_DY = 2;

    /** A box's light edge (its bottom row and right column; the cursor's top and left): ink 48. */
    static final int INK_BOX_LIGHT = 48;

    /**
     * The hammer rows: the stored hammers in rows of q = cost / 4 icons
     * (ICONS.SS.054), up to four, row r at icon y 147 + 6r, icon j at x 212 +
     * {@link #hammerOffset} (V #9921, #134173, #130584, #93990; dago).
     */
    static final int HAMMER_X = 212, HAMMER_Y = 147, HAMMER_ROW_DY = 6, HAMMER_ROWS = 4;

    /** The hammer icon. */
    static final int ICON_HAMMERS = 54;

    /**
     * The offsets of the hammer rows seen (spec §0.1 decision 1, the L
     * Checker's table): q 10 (Wagenzug, #130584), 13 (Verlag, #9921), 20
     * (Lagerhaus, #93990), 30 (Druckerei #134173, dago's Fort), 48
     * (Artillerie, dago Base #6400: measured in S5, not the spec's formula).
     */
    private static final int[][] HAMMER_TABLE = {
        { 10, 0, 9, 18, 27, 36, 45, 54, 63, 72, 81 },
        { 13, 0, 6, 13, 20, 26, 33, 40, 46, 53, 60, 66, 73, 80 },
        { 20, 0, 4, 8, 12, 17, 21, 25, 29, 34, 38, 42, 46, 51, 55, 59, 63, 68, 72, 76, 80 },
        { 30, 0, 2, 5, 8, 11, 13, 16, 19, 22, 24, 27, 30, 33, 35, 38, 41, 44, 47, 49, 52,
          55, 58, 60, 63, 66, 69, 71, 74, 77, 80 },
        // q 48 (Artillerie, dago Base #6400, measured anew in S5: dago's
        // floor(j·82/48); the spec's floor(j·218/128) puts 6 of the 48 one
        // pixel left, j 17, 24, 27, 34, 41, 44).
        { 48, 0, 1, 3, 5, 6, 8, 10, 11, 13, 15, 17, 18, 20, 22, 23, 25, 27, 29, 30, 32, 34,
          35, 37, 39, 41, 42, 44, 46, 47, 49, 51, 52, 54, 56, 58, 59, 61, 63, 64, 66, 68, 70,
          71, 73, 75, 76, 78, 80 },
    };

    /**
     * Icon j's offset in a hammer row of q icons: the table's for the five
     * costs seen (40, 52, 80, 120, 192; V clip 020 and dago), else
     * floor(j·S/128), S = floor(10240/(q - 1)) + 1 (L §7.3; I for every
     * other q, at most 1 px off: spec §4 item 2).
     *
     * @param q The icons of a full row (cost / 4).
     * @param j The icon, 0 ... q - 1.
     * @return Its x offset from 212.
     */
    static int hammerOffset(int q, int j) {
        for (int[] t : HAMMER_TABLE) {
            if (t[0] == q && j >= 0 && j < q) return t[1 + j];
        }
        if (q <= 1) return 0;
        final int s = 10240 / (q - 1) + 1;
        return j * s / 128;
    }

    /**
     * Whether a hammer row carries its count label at (214, y + 1): when its
     * pitch is below 2 (dago: the Artillerie's 48 at 1.7 «48» «48»; none at
     * 2.77 or more, #134173, dago's Fort), i.e. q &gt; 41.
     *
     * @param q The icons of a full row.
     * @return True for a label.
     */
    static boolean hammerLabelled(int q) {
        return q > 1 && hammerOffset(q, q - 1) < 2 * (q - 1);
    }

    /** A hammer row's count label's box: (214, icon y + 1). */
    static final int HAMMER_LABEL_X = 214, HAMMER_LABEL_DY = 1;

    /**
     * The hammers a hammer row holds.
     *
     * @param stored The hammers stored.
     * @param q The icons of a full row.
     * @param r The row, 0-3.
     * @return Its icons.
     */
    static int hammerRowCount(int stored, int q, int r) {
        if (q <= 0) return 0;
        return Math.max(0, Math.min(q, stored - r * q));
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

    /**
     * After a completion's dissolve the blink (the white cursor and ÄNDERN
     * in anti-phase) starts again, its first toggle this many frames after
     * the dissolve's last frame (V clip 020 #57744 -&gt; #57768, the Verlag;
     * the Lagerhaus' #105760 -&gt; #105806 is 46, I: unexplained).
     */
    static final int BLINK_AFTER_DISSOLVE_FRAMES = 24;
}
