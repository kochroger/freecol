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
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;


/**
 * The original's <b>menu box</b> family, drawn pixel-exactly in the 320x200
 * virtual canvas: the title menu ({@code opening_033}), the save and load
 * boxes ({@code 054}/{@code 055}) -- {@link #paintDialog DIALOG} style -- and
 * the SPIEL dropdown ({@code 053}) -- {@link #paintDropdown DROPDOWN} style.
 * Stateless: every method takes all it needs, so the same code paints the
 * live panels and the headless preview harness.
 *
 * <p>Every rule below comes from the game's own menu definitions (GAME.TXT
 * {@code @BEGINMENU @width=160 @y=91}, {@code @SAVEGAME}/{@code @LOADGAME}
 * {@code @width=190}) checked against the native captures, where a renderer
 * built from them reproduced all four boxes with 0 differing pixels:
 * <ul>
 *   <li>{@code @width} is the <em>interior</em> width; the outer width is
 *       {@code @width + 6} (three 1-px rings each side).</li>
 *   <li>The box is centred horizontally: {@code x = (320 - outer) / 2}; its
 *       top is {@code @y}, or vertically centred when there is none.</li>
 *   <li>Height {@code H = 6 * promptLines + 8 * rows + 18}: prompt lines
 *       are 6 px apart, option rows 8 px.  Measured on every captured box:
 *       one prompt line with 5 (title {@code 033}), 8 (save {@code 054}) and
 *       10 (load {@code 055}) rows; five prompt lines and no rows (the first
 *       scene's advisor box, {@code opening_083}/{@code 049}: H = 48); three
 *       and two prompt lines with 4 and 7 rows in the Europe advisor boxes
 *       ({@code opening_011}/{@code 013}: 68 and 86, plus a 6-px
 *       "(F1 ...)" footer line).  The older inference {@code 8 * (P + R) +
 *       16} agrees only for P = 1, the only case the title screen has.</li>
 *   <li>The fill is the 32x24 sprite {@code OPENTILE.SS}/{@code WOODTILE.SS}
 *       tiled from the box's <em>outer</em> top-left corner -- not the large
 *       {@code WOODPANL.PIK}, which is why {@link ClassicWood} is not reused
 *       here.</li>
 *   <li>Frame: black ring, a flat mid-brown ring, then a bevel (light top
 *       row and right column, dark left column and bottom row).</li>
 *   <li>The selection bar is a flat rectangle in the dark bevel colour from
 *       one row above the capitals through the descender row; text on it
 *       keeps its normal colour.  There is no text shadow.</li>
 * </ul>
 */
final class ClassicMenuBox {

    /** The virtual canvas. */
    static final int VW = 320;
    static final int VH = 200;

    /** Line pitch of option rows (and dropdown slots). */
    static final int PITCH = 8;

    /**
     * Line pitch of prompt lines: 6, not 8.  The multi-line prompts of
     * {@code opening_083}/{@code 049} (glyph tops 121, 127, 133, 139, 145)
     * and {@code 011} (111, 117, 123) are 6 px apart.  With one prompt line
     * the pitch never shows, which is why the title, save and load boxes
     * could not tell.
     */
    static final int PROMPT_PITCH = 6;

    /** The Swing font used only when the pack has no bitmap font. */
    private static final Font FALLBACK_FONT = new Font(Font.DIALOG, Font.PLAIN, 7);

    /** Measures {@link #FALLBACK_FONT} without a Graphics. */
    private static final FontRenderContext FRC = new FontRenderContext(null, false, false);


    /** A colour scheme plus fill tile. */
    static final class Theme {

        final Color ring1, light, dark, ink, highlight, fallbackFill;

        /** Pack key of the 32x24 fill tile. */
        final String fillKey;

        /** Bitmap-font colour tables for {@link #ink} / {@link #highlight}. */
        final int[] inkColours, highlightColours;

        private Theme(int ring1, int light, int dark, int ink, int highlight,
                      String fillKey, int fallbackFill) {
            this.ring1 = new Color(ring1);
            this.light = new Color(light);
            this.dark = new Color(dark);
            this.ink = new Color(ink);
            this.highlight = new Color(highlight);
            this.fillKey = fillKey;
            this.fallbackFill = new Color(fallbackFill);
            this.inkColours = ClassicFont.colours(ink & 0xFFFFFF);
            this.highlightColours = ClassicFont.colours(highlight & 0xFFFFFF);
        }
    }

    /**
     * The title screen's scheme ({@code opening_033}): OPENMENU palette
     * indices 46/253/55/254/252, as DOSBox expands them -- equal to the
     * regenerated pack's pixels since the converter uses the same expansion.
     */
    static final Theme TITLE = new Theme(0x593028, 0x794934, 0x382018,
        0x559634, 0xC7A220, "image.classic_original.ss.OPENTILE.SS.000", 0x5C3A22);

    /** The in-game scheme, measured from the save/load boxes 054/055. */
    static final Theme GAME = new Theme(0x593424, 0x794934, 0x3C2018,
        0x559634, 0xC7A220, "image.classic_original.ss.WOODTILE.SS.000", 0x5C3A22);

    /**
     * The ink of a greyed dropdown row, hotkey letter included (Steam
     * {@code opening_001}: the forest-clearing and the road order).
     */
    static final int DISABLED_INK = 0x555555;

    /** {@link #GAME} with both text colours {@link #DISABLED_INK}. */
    private static final Theme DISABLED = new Theme(0x593424, 0x794934, 0x3C2018,
        DISABLED_INK, DISABLED_INK, "image.classic_original.ss.WOODTILE.SS.000",
        0x5C3A22);


    private ClassicMenuBox() {}   // static helpers only


    // Geometry (all in virtual pixels)

    /**
     * Outer height of a dialog with {@code promptLines} and {@code rows}:
     * {@code 6P + 8R + 18} (the captures are listed in the class comment).
     */
    static int dialogHeight(int promptLines, int rows) {
        return PROMPT_PITCH * promptLines + PITCH * rows + 18;
    }

    /**
     * Outer bounds of a dialog box.
     *
     * @param innerWidth GAME.TXT's {@code @width}.
     * @param promptLines Number of prompt lines.
     * @param rows Number of selectable rows.
     * @param y GAME.TXT's {@code @y}, or negative to centre vertically.
     * @return The outer rectangle.
     */
    static Rectangle dialogBounds(int innerWidth, int promptLines, int rows, int y) {
        final int w = innerWidth + 6;
        final int h = dialogHeight(promptLines, rows);
        return new Rectangle((VW - w) / 2, (y < 0) ? (VH - h) / 2 : y, w, h);
    }

    /** Left edge of the prompt text. */
    static int promptX(Rectangle b) {
        return b.x + 5;
    }

    /** Glyph top of prompt line {@code k} (lines 6 px apart). */
    static int promptTop(Rectangle b, int k) {
        return b.y + 9 + PROMPT_PITCH * k;
    }

    /** Left edge of the row text (indented 4 px against the prompt). */
    static int rowX(Rectangle b) {
        return b.x + 9;
    }

    /**
     * Glyph top of row {@code i}: {@code y + 13 + 6P + 8i}, so the first row
     * sits 10 px below the last prompt line's top (011: last prompt line
     * 123, first row 133).  Equal to the older {@code y + 11 + 8P + 8i} for
     * one prompt line.
     */
    static int rowTop(Rectangle b, int promptLines, int i) {
        return b.y + 13 + PROMPT_PITCH * promptLines + PITCH * i;
    }

    /** The selection bar behind row {@code i}: capitals-1 .. descender. */
    static Rectangle barRect(Rectangle b, int promptLines, int i) {
        return new Rectangle(b.x + 4, rowTop(b, promptLines, i) - 1, b.width - 8, 7);
    }

    /** The mouse target of row {@code i}: the full interior width, 8 rows. */
    static Rectangle rowHitRect(Rectangle b, int promptLines, int i) {
        return new Rectangle(b.x + 3, rowTop(b, promptLines, i) - 1, b.width - 6, PITCH);
    }

    /** The widest row text that stays inside the interior. */
    static int rowTextMaxWidth(Rectangle b) {
        return b.width - 14;
    }

    /** Outer bounds of a dropdown: 3 px top, 8 px per row, 2 px bottom. */
    static Rectangle dropdownBounds(int x, int y, int outerWidth, int rows) {
        return new Rectangle(x, y, outerWidth, PITCH * rows + 5);
    }

    /** Top of dropdown slot {@code k}. */
    static int dropdownSlotTop(Rectangle b, int k) {
        return b.y + 3 + PITCH * k;
    }


    // Painting

    /**
     * Tile {@code tile} over {@code r}, phase-anchored at
     * ({@code anchorX}, {@code anchorY}); a flat {@code fallback} without it.
     */
    static void fillTiled(Graphics2D g, BufferedImage tile, Rectangle r,
                          int anchorX, int anchorY, Color fallback) {
        if (r.width <= 0 || r.height <= 0) return;
        if (tile == null || tile.getWidth() <= 0 || tile.getHeight() <= 0) {
            g.setColor(fallback);
            g.fillRect(r.x, r.y, r.width, r.height);
            return;
        }
        final int tw = tile.getWidth();
        final int th = tile.getHeight();
        final Graphics2D gg = (Graphics2D) g.create();
        try {
            gg.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            gg.clipRect(r.x, r.y, r.width, r.height);
            final int x0 = anchorX + Math.floorDiv(r.x - anchorX, tw) * tw;
            final int y0 = anchorY + Math.floorDiv(r.y - anchorY, th) * th;
            for (int y = y0; y < r.y + r.height; y += th) {
                for (int x = x0; x < r.x + r.width; x += tw) {
                    gg.drawImage(tile, x, y, null);
                }
            }
        } finally {
            gg.dispose();
        }
    }

    /** A 1-px rectangle outline from four fills (exact at any scale). */
    private static void ring(Graphics2D g, int x, int y, int w, int h) {
        g.fillRect(x, y, w, 1);
        g.fillRect(x, y + h - 1, w, 1);
        g.fillRect(x, y, 1, h);
        g.fillRect(x + w - 1, y, 1, h);
    }

    /**
     * The DIALOG frame: fill, black ring, flat ring, bevel -- in exactly this
     * order, so the corners come out as captured: (X+2,Y+2) and (X+W-3,Y+2)
     * light, (X+2,Y+H-3) and (X+W-3,Y+H-3) dark.
     */
    static void paintDialogFrame(Graphics2D g, Rectangle b, Theme t,
                                 BufferedImage tile) {
        final int x = b.x, y = b.y, w = b.width, h = b.height;
        fillTiled(g, tile, b, x, y, t.fallbackFill);
        g.setColor(Color.BLACK);
        ring(g, x, y, w, h);
        g.setColor(t.ring1);
        ring(g, x + 1, y + 1, w - 2, h - 2);
        g.setColor(t.light);
        g.fillRect(x + 2, y + 2, w - 4, 1);
        g.fillRect(x + w - 3, y + 2, 1, h - 5);
        g.setColor(t.dark);
        g.fillRect(x + 2, y + 3, 1, h - 5);
        g.fillRect(x + 2, y + h - 3, w - 4, 1);
    }

    /**
     * A complete DIALOG box: frame, selection bar, prompt and rows.
     *
     * @param prompt The prompt lines, drawn with markup ({@code {..}} gold).
     * @param rows The rows, drawn literally, always in the ink colour.
     * @param selected The barred row, or -1 for none.
     */
    static void paintDialog(Graphics2D g, Rectangle b, Theme t, BufferedImage tile,
                            ClassicFont font, List<String> prompt,
                            List<String> rows, int selected) {
        paintDialog(g, b, t, tile, font, prompt, true, rows, selected);
    }

    /**
     * A complete DIALOG box, choosing whether the prompt honours markup.
     * Free-form text (engine messages, file paths) must be drawn plain, or
     * its '{', '}' and '~' vanish and colour the following glyphs gold.
     *
     * @param markedPrompt Whether the prompt lines are the game's own marked
     *     strings ({@code true}) or literal text ({@code false}).
     */
    static void paintDialog(Graphics2D g, Rectangle b, Theme t, BufferedImage tile,
                            ClassicFont font, List<String> prompt,
                            boolean markedPrompt, List<String> rows,
                            int selected) {
        paintDialogFrame(g, b, t, tile);
        final int p = prompt.size();
        if (selected >= 0 && selected < rows.size()) {
            final Rectangle bar = barRect(b, p, selected);
            g.setColor(t.dark);
            g.fillRect(bar.x, bar.y, bar.width, bar.height);
        }
        for (int k = 0; k < p; k++) {
            text(g, font, prompt.get(k), promptX(b), promptTop(b, k), t,
                 markedPrompt);
        }
        for (int i = 0; i < rows.size(); i++) {
            text(g, font, rows.get(i), rowX(b), rowTop(b, p, i), t, false);
        }
    }

    /**
     * A DROPDOWN menu ({@code opening_053}): fill and a black ring only, a
     * green separator line for each {@code null} row, the bar, and marked
     * text ('~' hotkey letters in gold).  The in-game menu strip
     * ({@link ClassicMenuStrip}) draws its six menus with the overload
     * below; every row here is enabled.
     */
    static void paintDropdown(Graphics2D g, Rectangle b, Theme t, BufferedImage tile,
                              ClassicFont font, List<String> rows, int selected) {
        paintDropdown(g, b, t, tile, font, rows, null, selected);
    }

    /**
     * A DROPDOWN menu with greyed rows.  A disabled row draws every glyph --
     * its '~' letter included -- in {@link #DISABLED_INK}: Steam
     * {@code opening_001}'s two unusable orders are 141 pixels of
     * {@code 0x555555} and nothing green or gold.  Everything else is the
     * enabled painter's; the in-game menus ({@code 001}-{@code 005},
     * {@code 053}) match it with 0 differing pixels.
     *
     * @param disabled Per row (same index as {@code rows}), whether it is
     *     greyed; null or a short array means enabled.
     */
    static void paintDropdown(Graphics2D g, Rectangle b, Theme t, BufferedImage tile,
                              ClassicFont font, List<String> rows,
                              boolean[] disabled, int selected) {
        fillTiled(g, tile, b, b.x, b.y, t.fallbackFill);
        g.setColor(Color.BLACK);
        ring(g, b.x, b.y, b.width, b.height);
        for (int k = 0; k < rows.size(); k++) {
            final int top = dropdownSlotTop(b, k);
            final String s = rows.get(k);
            if (s == null) {
                g.setColor(t.ink);
                g.fillRect(b.x + 1, top + 3, b.width - 2, 1);
                continue;
            }
            if (k == selected) {
                g.setColor(t.dark);
                g.fillRect(b.x + 2, top, b.width - 4, 7);
            }
            if (disabled != null && k < disabled.length && disabled[k]) {
                text(g, font, s, b.x + 5, top + 1, DISABLED, true);
            } else {
                text(g, font, s, b.x + 5, top + 1, t, true);
            }
        }
    }


    // Text (bitmap font, or the Swing fallback without the pack)

    /**
     * Draw one line of box text.
     *
     * @param y The glyph top (capital top for FONTTINY).
     * @param marked Whether to honour the {..} / ~ markup.
     * @return The x after the text.
     */
    static int text(Graphics2D g, ClassicFont font, String s, int x, int y,
                    Theme t, boolean marked) {
        if (s == null || s.isEmpty()) return x;
        if (font != null) {
            return marked
                ? font.drawMarked(g, s, x, y, t.inkColours, t.highlightColours)
                : font.draw(g, s, x, y, t.inkColours);
        }
        // Swing fallback: a 7 px font, aliased, baseline at the descender row.
        final Graphics2D gg = (Graphics2D) g.create();
        try {
            gg.setFont(FALLBACK_FONT);
            gg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                                RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            gg.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_OFF);
            for (Segment seg : segments(s, marked)) {
                gg.setColor(seg.highlight ? t.highlight : t.ink);
                gg.drawString(seg.text, x, y + 5);
                x += fallbackWidth(seg.text);
            }
        } finally {
            gg.dispose();
        }
        return x;
    }

    /** The width {@link #text} will use for {@code s}. */
    static int textWidth(ClassicFont font, String s, boolean marked) {
        if (s == null || s.isEmpty()) return 0;
        if (font != null) {
            return marked ? font.markedWidth(s) : font.stringWidth(s);
        }
        int w = 0;
        for (Segment seg : segments(s, marked)) w += fallbackWidth(seg.text);
        return w;
    }

    /** Cut plain {@code s} to {@code maxWidth} with "...", with either font. */
    static String fit(ClassicFont font, String s, int maxWidth) {
        if (s == null) return "";
        if (font != null) return font.fit(s, maxWidth);
        if (fallbackWidth(s) <= maxWidth) return s;
        String cut = s;
        while (!cut.isEmpty() && fallbackWidth(cut + "...") > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut.trim() + "...";
    }

    private static int fallbackWidth(String s) {
        return (int) Math.ceil(FALLBACK_FONT.getStringBounds(s, FRC).getWidth());
    }

    /** A run of text in one colour. */
    private static final class Segment {
        final String text;
        final boolean highlight;

        Segment(String text, boolean highlight) {
            this.text = text;
            this.highlight = highlight;
        }
    }

    /** Split marked text into colour runs (markup characters dropped). */
    private static List<Segment> segments(String s, boolean marked) {
        final List<Segment> out = new ArrayList<>();
        if (!marked) {
            out.add(new Segment(s, false));
            return out;
        }
        final StringBuilder sb = new StringBuilder();
        boolean on = false, once = false, cur = false;
        for (int i = 0; i < s.length(); i++) {
            final char ch = s.charAt(i);
            if (ch == '{') { on = true; continue; }
            if (ch == '}') { on = false; continue; }
            if (ch == '~') { once = true; continue; }
            final boolean hl = on || once;
            once = false;
            if (hl != cur && sb.length() > 0) {
                out.add(new Segment(sb.toString(), cur));
                sb.setLength(0);
            }
            cur = hl;
            sb.append(ch);
        }
        if (sb.length() > 0) out.add(new Segment(sb.toString(), cur));
        return out;
    }
}
