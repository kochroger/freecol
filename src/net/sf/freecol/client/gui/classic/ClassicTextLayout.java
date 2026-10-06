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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * The original's <b>text-page layout</b> for GAME.TXT messages, as a pure
 * function: raw message lines in, positioned lines out.
 *
 * <p>Measured against the native captures of the nation pages
 * ({@code opening_066}/{@code 067}, England {@code 037}/{@code 038}, all in
 * FONTINTR) and the audience scroll ({@code 068}/{@code 039}, FONTKING); the
 * rules reproduce every line break and position there:
 * <ul>
 *   <li>'_' is an invisible placeholder (FONTINTR even has a 7-px '_'
 *       glyph, so it is removed before anything is measured).</li>
 *   <li>A line starting with {@code ^^} is a <em>centred</em> hard line at
 *       {@code x = L + ceil((W - width(rtrim(rest))) / 2)}: leading spaces
 *       count, trailing ones do not.  A line starting with one {@code ^} is
 *       a left-aligned hard line (a bare {@code ^} is an empty line).  A
 *       {@code ^} anywhere else is simply removed.</li>
 *   <li>All other lines are joined with ONE space -- also after a line that
 *       ends in '-', which is why the Holland page really shows a split
 *       compound with a space after its hyphen -- runs of spaces collapse,
 *       and the result is wrapped greedily at spaces, never at hyphens.</li>
 *   <li>The wrap measure is not the drawn width: each '{', '}' and 'ß'
 *       counts one extra pixel, and a line fits if this measure is at most
 *       {@code W - 2}.  Plain pixel width contradicts the captures (on 066 a
 *       298-px line fits while two other 298-px lines -- one with a 'ß', one
 *       with a highlighted word -- break).  Whether ö/Ä/Ö/ü/Ü also count
 *       extra is not pinned down by any capture (ä does not).</li>
 *   <li>A highlight '{' still open at a line break is closed at the end of
 *       that line and re-opened on the next, for drawing only.</li>
 *   <li>{@code L = @x} when given, else {@code (320 - W) / 2}.  All lines
 *       count (hard, blank, wrapped); the block's top is {@code @y + 3} when
 *       the message has {@code @y} (the caller passes it), else the block is
 *       centred: {@code (200 - pitch * lines) / 2}.  Pitch = font height + 1
 *       (FONTINTR 10, FONTKING 8).</li>
 * </ul>
 *
 * <p><b>The advisor boxes</b> ({@link ClassicAdvisorBox}, FONTTINY at
 * pitch 6) wrap by another measure ({@link #BOX}): the plain advance width
 * of the line, spaces collapsed, the braces taking no room, at most
 * {@code W - 6}.  Checked on the 161 line ends of the landfall clip's 21
 * GAME.TXT boxes ({@code landfall 05-dialogs-and-events.md} section 3.1:
 * every line that stands, and every line plus the next word that broke):
 * one exception, {@code @TUTORIAL13}'s first line, which the original keeps
 * at 216 px of a 214-px measure.  The page rule above misses four of them
 * ({@code @INDIANWELCOME}, {@code @VILLAGESAVAGE}, {@code @LEARNSTAY},
 * {@code @TUTORIAL13}).
 *
 * <p>{@link ClassicFont#wrap} is deliberately not used: it wraps at plain
 * width {@code <= max}, which breaks the 066 page differently (the two
 * counter-examples above).
 */
final class ClassicTextLayout {

    /** The original's screen. */
    private static final int SCREEN_W = 320, SCREEN_H = 200;

    /** One laid-out line: where to draw which marked text. */
    static final class Line {

        final int x, y;

        /** The text, markup kept ('{' '}' for the highlight colour). */
        final String marked;

        Line(int x, int y, String marked) {
            this.x = x;
            this.y = y;
            this.marked = marked;
        }

        @Override
        public String toString() {
            return "(" + x + "," + y + ") " + marked;
        }
    }

    /** How a line is measured against the message's width. */
    interface Fit {

        /**
         * @param f The font.
         * @param marked The candidate line, markup kept.
         * @param width W, the message's {@code @width}.
         * @return Whether the line fits.
         */
        boolean fits(ClassicFont f, String marked, int width);
    }

    /** The text pages' rule ({@link #fits}), the default of {@link #layout}. */
    static final Fit PAGE = ClassicTextLayout::fits;

    /**
     * The advisor boxes' rule (class comment): the plain width at most
     * {@code W - 6}.
     */
    static final Fit BOX = (f, marked, width) -> f.markedWidth(marked) <= width - 6;

    private ClassicTextLayout() {}

    /**
     * Lay out a message.
     *
     * @param f The font (for widths).
     * @param raw The message's text lines, markup kept.
     * @param width W, the message's {@code @width}.
     * @param left The left edge ({@code @x}), or null to centre W on the
     *     screen.
     * @param top The first line's top ({@code @y + 3}), or null to centre the
     *     block vertically.
     * @param pitch The line pitch.
     * @return The lines, top to bottom.
     */
    static List<Line> layout(ClassicFont f, List<String> raw, int width,
                             Integer left, Integer top, int pitch) {
        return layout(f, raw, width, left, top, pitch, PAGE);
    }

    /**
     * Lay out a message with a given wrap rule ({@link #PAGE},
     * {@link #BOX}), otherwise as
     * {@link #layout(ClassicFont, List, int, Integer, Integer, int)}.
     *
     * @param fit The wrap rule.
     * @return The lines, top to bottom.
     */
    static List<Line> layout(ClassicFont f, List<String> raw, int width,
                             Integer left, Integer top, int pitch, Fit fit) {
        if (raw == null || raw.isEmpty()) return Collections.emptyList();
        final int l = (left != null) ? left : (SCREEN_W - width) / 2;
        // {x, text} pairs before vertical placement.
        final List<Object[]> rows = new ArrayList<>();
        StringBuilder para = null;
        for (String r : raw) {
            final String s = r.replace("_", "");
            if (s.startsWith("^")) {
                if (para != null) {
                    flow(f, para.toString(), width, l, rows, fit);
                    para = null;
                }
                if (s.startsWith("^^")) {
                    final String t = rtrim(s.substring(2).replace("^", ""));
                    final int w = f.markedWidth(t);
                    rows.add(new Object[] { l + Math.floorDiv(width - w + 1, 2), t });
                } else {
                    rows.add(new Object[] { l, s.substring(1).replace("^", "") });
                }
            } else {
                final String t = s.replace("^", "");
                if (para == null) {
                    para = new StringBuilder(t);
                } else {
                    para.append(' ').append(t);
                }
            }
        }
        if (para != null) flow(f, para.toString(), width, l, rows, fit);

        final int y0 = (top != null) ? top : (SCREEN_H - pitch * rows.size()) / 2;
        final List<Line> out = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            out.add(new Line((Integer) rows.get(i)[0], y0 + i * pitch,
                             (String) rows.get(i)[1]));
        }
        return out;
    }

    /**
     * The original's wrap measure: the drawn width plus one for every '{',
     * '}' and 'ß'.
     */
    static int measure(ClassicFont f, String marked) {
        int extra = 0;
        for (int i = 0; i < marked.length(); i++) {
            final char c = marked.charAt(i);
            if (c == '{' || c == '}' || c == 'ß') extra++;
        }
        return f.markedWidth(marked) + extra;
    }

    /** Whether a line fits a message of width W. */
    static boolean fits(ClassicFont f, String marked, int width) {
        return measure(f, marked) <= width - 2;
    }

    /** Greedy wrap of one joined paragraph into left-aligned rows. */
    private static void flow(ClassicFont f, String para, int width, int left,
                             List<Object[]> rows, Fit fit) {
        final String p = para.trim();
        if (p.isEmpty()) return;
        final String[] words = p.split(" +");
        final List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String w : words) {
            if (line.length() == 0) {
                line.append(w);
                continue;
            }
            final String cand = line + " " + w;
            if (fit.fits(f, cand, width)) {
                line.setLength(0);
                line.append(cand);
            } else {
                lines.add(line.toString());
                line = new StringBuilder(w);
            }
        }
        lines.add(line.toString());
        // Carry an open highlight over the breaks (drawing only).
        boolean open = false;
        for (String s : lines) {
            final boolean startOpen = open;
            for (int i = 0; i < s.length(); i++) {
                if (s.charAt(i) == '{') open = true;
                else if (s.charAt(i) == '}') open = false;
            }
            rows.add(new Object[] { left,
                (startOpen ? "{" : "") + s + (open ? "}" : "") });
        }
    }

    private static String rtrim(String s) {
        int e = s.length();
        while (e > 0 && s.charAt(e - 1) == ' ') e--;
        return s.substring(0, e);
    }
}
