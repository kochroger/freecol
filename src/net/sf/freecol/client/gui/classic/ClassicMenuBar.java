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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;


/**
 * The original's <b>top strip</b>, the 8-px row above the map, drawn in the
 * 320x200 virtual canvas.  Stateless static painters and geometry, so the
 * live HUD ({@link ClassicMenuStrip}) and the headless preview harness share
 * one renderer.
 *
 * <p>The strip carries either the <b>menu bar</b> (the six titles of
 * MENU.TXT, SPIEL ... COLONIPÄDIE) or, during the first game scene, a
 * centred <b>title band</b> (nationality, ship, "arriving from", home port).
 * Both sit on the same chrome, measured with 0 differing pixels on Dutch
 * {@code opening_083} (band, 1,955 wood pixels), English {@code 049} (band)
 * and Steam {@code 032}/{@code 000} and start-sequence {@code 052} (menu
 * bar):
 * <ul>
 *   <li>rows 0..6: the 32x24 {@code WOODTILE.SS.000} -- the in-game box
 *       fill ({@link ClassicMenuBox#GAME}) -- tiled with its phase anchored
 *       at screen (0,0); only the tile's first 7 rows show.  Not
 *       {@code WOODPANL.PIK} and not a flat colour;</li>
 *   <li>row 7: black, all 320 px (the map starts at y = 8);</li>
 *   <li>text: FONTTINY, glyph top y = 1, no shadow.  The band is entirely
 *       gold {@code 0xC7A220} (the GAME theme's highlight), centred at
 *       {@code x = (320 - w) / 2} (083: x = 83 for w = 154; 049: x = 98 for
 *       w = 124).</li>
 * </ul>
 *
 * <p><b>Menu titles</b> (Steam {@code 032}, {@code 000}, start-sequence
 * {@code 052}): green {@code 0x559634}, the '~' letter gold.  The first title
 * starts at x = 13, each next one 14 px after the previous one's end, and the
 * last (COLONIPÄDIE) is right-aligned so it ends 13 px before the screen
 * edge: FONTTINY widths 26/34/34/32/30/44 give x = 13, 53, 101, 149, 195,
 * 263.  An open menu's title sits on a flat {@code 0x3C2018} rectangle (the
 * GAME theme's selection bar) at {@code (x-1, 0, w+4, 7)}: Steam
 * {@code 001}-{@code 005}, start-sequence {@code 053}.
 *
 * <p><b>Dropdowns</b> hang one row below the strip (y = 9), their left edge
 * one pixel left of the title, and are as wide as the widest item of the
 * WHOLE MENU.TXT section plus 10 -- also when context hides that item (every
 * capture agrees; BEFEHLE's widest item happens to be visible in 001).  A box
 * that would leave the screen is pushed left (assumed for COLONIPÄDIE: no
 * capture).  Rows, separators and the bar are
 * {@link ClassicMenuBox#paintDropdown}'s.
 */
final class ClassicMenuBar {

    /** Height of the strip, black row included. */
    static final int HEIGHT = 8;

    /** Wood rows at the top of the strip (row 7 is black). */
    static final int WOOD_ROWS = 7;

    /** Glyph top of the strip's text. */
    static final int TEXT_TOP = 1;

    /** The strip's width: the whole virtual screen. */
    static final int WIDTH = ClassicMenuBox.VW;

    /** Pack key of the strip's (and the in-game boxes') wood tile. */
    static final String WOOD_KEY = ClassicMenuBox.GAME.fillKey;

    /** Title layout: first x, gap after a title, right margin of the last. */
    static final int TITLE_LEFT = 13, TITLE_GAP = 14, TITLE_RIGHT = 13;

    /** Dropdown: outer top, and outer width minus the widest item. */
    static final int DROP_TOP = 9, DROP_PAD = 10;


    private ClassicMenuBar() {}   // static painters only


    // Geometry

    /**
     * Where each title starts (see the class comment for the measured rule).
     *
     * @param markedWidths Each title's width ('~' markup excluded).
     * @return The x of each title's first glyph cell.
     */
    static int[] titleX(int[] markedWidths) {
        final int n = markedWidths.length;
        final int[] x = new int[n];
        int cx = TITLE_LEFT;
        for (int i = 0; i < n; i++) {
            if (i == n - 1 && n > 1) {
                x[i] = WIDTH - TITLE_RIGHT - markedWidths[i];
            } else {
                x[i] = cx;
                cx += markedWidths[i] + TITLE_GAP;
            }
        }
        return x;
    }

    /** The widths of {@code titles} as {@link #paintBar} draws them. */
    static int[] titleWidths(ClassicFont font, List<String> titles) {
        final int[] w = new int[titles.size()];
        for (int i = 0; i < w.length; i++) {
            w[i] = ClassicMenuBox.textWidth(font, titles.get(i), true);
        }
        return w;
    }

    /**
     * The flat rectangle behind an open title, and the title's mouse target.
     *
     * @param x The title's x.
     * @param w The title's marked width.
     */
    static Rectangle titleHighlight(int x, int w) {
        return new Rectangle(x - 1, 0, w + 4, WOOD_ROWS);
    }

    /**
     * The dropdown's outer box.
     *
     * @param titleX The menu title's x.
     * @param widest The widest item of the whole MENU.TXT section.
     * @param slots Rows plus separators.
     */
    static Rectangle dropdownBounds(int titleX, int widest, int slots) {
        final int w = widest + DROP_PAD;
        final int x = Math.max(0, Math.min(titleX - 1, WIDTH - w));
        return ClassicMenuBox.dropdownBounds(x, DROP_TOP, w, slots);
    }

    /** The widest marked string of {@code items}. */
    static int widest(ClassicFont font, List<String> items) {
        int w = 0;
        for (String s : items) w = Math.max(w, ClassicMenuBox.textWidth(font, s, true));
        return w;
    }


    // Painting

    /**
     * The chrome: wood rows 0..6 tiled from (0,0), black row 7.
     *
     * @param g The graphics, in 320x200 virtual pixels.
     * @param wood {@code WOODTILE.SS.000}, or null for the flat fallback.
     */
    static void paintStrip(Graphics2D g, BufferedImage wood) {
        ClassicMenuBox.fillTiled(g, wood, new Rectangle(0, 0, WIDTH, WOOD_ROWS),
                                 0, 0, ClassicMenuBox.GAME.fallbackFill);
        g.setColor(Color.BLACK);
        g.fillRect(0, WOOD_ROWS, WIDTH, HEIGHT - WOOD_ROWS);
    }

    /**
     * The menu bar: chrome, the open title's highlight, the titles.
     *
     * @param g The graphics, in 320x200 virtual pixels.
     * @param font FONTTINY, or null for the Swing fallback.
     * @param wood {@code WOODTILE.SS.000}, or null.
     * @param titles The titles with their '~' markup; empty paints the
     *     chrome only (a pack without MENU.TXT).
     * @param openIndex The open menu, or -1.
     */
    static void paintBar(Graphics2D g, ClassicFont font, BufferedImage wood,
                         List<String> titles, int openIndex) {
        paintStrip(g, wood);
        if (titles == null || titles.isEmpty()) return;
        final int[] w = titleWidths(font, titles);
        final int[] x = titleX(w);
        for (int i = 0; i < titles.size(); i++) {
            if (i == openIndex) {
                final Rectangle h = titleHighlight(x[i], w[i]);
                g.setColor(ClassicMenuBox.GAME.dark);
                g.fillRect(h.x, h.y, h.width, h.height);
            }
            ClassicMenuBox.text(g, font, titles.get(i), x[i], TEXT_TOP,
                                ClassicMenuBox.GAME, true);
        }
    }

    /**
     * Where a centred band text starts: {@code (320 - w) / 2}.
     *
     * @param font FONTTINY, or null for the Swing fallback's width.
     * @param text The band text (plain, no markup).
     * @return The x of the first glyph.
     */
    static int bandX(ClassicFont font, String text) {
        return (WIDTH - ClassicMenuBox.textWidth(font, text, false)) / 2;
    }

    /**
     * The strip with a title band instead of the menu titles (the first
     * game scene, {@code opening_083}/{@code 049}): chrome, then the whole
     * line in gold, centred.
     *
     * @param g The graphics, in 320x200 virtual pixels.
     * @param font FONTTINY, or null for the Swing fallback.
     * @param wood {@code WOODTILE.SS.000}, or null.
     * @param text The band text, drawn literally (no markup); null or empty
     *     paints the chrome only.
     */
    static void paintBand(Graphics2D g, ClassicFont font, BufferedImage wood,
                          String text) {
        paintStrip(g, wood);
        if (text == null || text.isEmpty()) return;
        final int x = bandX(font, text);
        if (font != null) {
            font.draw(g, text, x, TEXT_TOP, ClassicMenuBox.GAME.highlightColours);
        } else {
            // The fallback has no "all highlight" call; braces make it one.
            ClassicMenuBox.text(g, null, "{" + text + "}", x, TEXT_TOP,
                                ClassicMenuBox.GAME, true);
        }
    }
}
