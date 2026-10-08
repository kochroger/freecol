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
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * The original's Colonopedia text (PEDIA.TXT, copied into the pack's
 * {@code text/} folder by {@code ant classic-assets}) and its founding
 * father page (build spec D8b, the page alone: F1 in the father box).
 *
 * <p><b>The file.</b>  The same byte codes as GAME.TXT
 * ({@link ClassicText#decode}).  A section starts with a line
 * {@code @NAME} ({@code @FATHER2}); {@code @width=n} and {@code @SMALLFONT}
 * are its directives; a line {@code @;} is a comment and ends the section
 * (the file puts one before each entry).  So the sections cannot go through
 * {@link ClassicText}, whose sections would end at {@code @SMALLFONT}.
 *
 * <p><b>The father page</b> (clip008 01-fathers section 3.1; V, 0 px on
 * #40109 Minuit, #48645 Stuyvesant, #49256 Hudson, #49900 Drake, the
 * pointer apart):
 * <ul>
 *   <li>{@code WOODPANL.PIK} over the whole screen, no picture;</li>
 *   <li>LABELS {@code @MISC} 107 "COLONIZATION-ENZYKLOPÄDIE" and
 *       "(<i>name</i>: Gründerväter)" (NAMES {@code @FATHERS},
 *       PEDIA {@code @PEDIA} 5) in gold, glyph tops 5 and 13, each at
 *       {@code x = (322 - w) div 2};</li>
 *   <li>the entry at {@code x0 = (321 - @width) div 2}: its first line (the
 *       title, without its {@code ^}) at y 36, then its text reflowed
 *       greedily at {@code 320 - 2 x0}, 7 px apart; {@code ^} ends a
 *       paragraph and leaves one blank line (a run of them counts once);
 *       {@code {..}} is gold and carries over line ends; {@code %%} is a
 *       {@code %}, {@code $} FONTTINY's coin; {@code @SMALLFONT} draws the
 *       same font, only its {@code @width} counts.</li>
 * </ul>
 */
final class ClassicPedia {

    private static final Logger logger = Logger.getLogger(ClassicPedia.class.getName());

    /** The file, as the converter names it in {@code <pack>/text}. */
    static final String FILE = "PEDIA.TXT";

    /** The pack key of the page's background. */
    static final String WOODPANL_KEY = "image.classic_original.pik.WOODPANL.PIK";

    /** LABELS.TXT {@code @MISC}: "COLONIZATION-ENZYKLOPÄDIE". */
    static final int MISC_HEADER = 107;

    /** PEDIA.TXT {@code @PEDIA}: the founding fathers' category. */
    static final int CATEGORY_FATHERS = 5;

    /** The glyph tops of the two header lines and of the title. */
    static final int HEADER1_TOP = 5, HEADER2_TOP = 13, TITLE_TOP = 36;

    /** The body's line pitch. */
    static final int PITCH = 7;

    /** The width of an entry without {@code @width}. */
    static final int DEFAULT_WIDTH = 300;

    /** The ink (index 68) and the gold (index 149). */
    static final int INK = 0x559634, GOLD = 0xC7A220;

    /** Loaded texts by pack directory. */
    private static final Map<File, ClassicPedia> CACHE = new HashMap<>();


    /** One section: its directives and its raw lines (markup kept). */
    static final class Entry {

        /** {@code @width}, or {@link #DEFAULT_WIDTH}. */
        final int width;

        /** {@code @SMALLFONT}. */
        final boolean smallFont;

        /** The lines, trailing blank ones dropped. */
        final List<String> lines;

        Entry(int width, boolean smallFont, List<String> lines) {
            this.width = width;
            this.smallFont = smallFont;
            this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
        }
    }

    /** The sections by name (without '@'). */
    private final Map<String, Entry> sections;


    private ClassicPedia(Map<String, Entry> sections) {
        this.sections = sections;
    }

    /**
     * The Colonopedia of a pack, read once per pack directory.
     *
     * @param pack The pack, or null.
     * @return The texts, or null without the pack's PEDIA.TXT (re-run
     *     {@code ant classic-assets}).
     */
    static ClassicPedia load(ClassicPackFiles pack) {
        if (pack == null) return null;
        synchronized (CACHE) {
            ClassicPedia p = CACHE.get(pack.directory());
            if (p != null) return p;
            final File f = pack.textFile(FILE);
            if (f == null) {
                logger.info("classic_original pack has no text/" + FILE
                    + " -- no Colonopedia pages (re-run ant classic-assets)");
                return null;
            }
            try {
                p = parse(ClassicText.decode(Files.readAllBytes(f.toPath())));
            } catch (IOException e) {
                logger.log(Level.WARNING, "Unreadable " + f, e);
                return null;
            }
            CACHE.put(pack.directory(), p);
            return p;
        }
    }

    /**
     * Read decoded lines (class comment).
     *
     * @param lines The file's lines ({@link ClassicText#decode}).
     * @return The texts.
     */
    static ClassicPedia parse(List<String> lines) {
        final Map<String, Entry> out = new HashMap<>();
        String name = null;
        int width = DEFAULT_WIDTH;
        boolean small = false;
        final List<String> body = new ArrayList<>();
        for (String raw : lines) {
            final String l = raw.trim();
            final boolean header = isHeader(l);
            if (header || l.startsWith("@;")) {
                if (name != null) out.put(name, entry(width, small, body));
                name = (header) ? l.substring(1) : null;
                width = DEFAULT_WIDTH;
                small = false;
                body.clear();
                continue;
            }
            if (name == null) continue;
            if (l.startsWith("@width=")) {
                try {
                    width = Integer.parseInt(l.substring(7).trim());
                } catch (NumberFormatException e) {
                    width = DEFAULT_WIDTH;
                }
                continue;
            }
            if (l.equals("@SMALLFONT")) {
                small = true;
                continue;
            }
            body.add(raw);
        }
        if (name != null) out.put(name, entry(width, small, body));
        return new ClassicPedia(out);
    }

    /** Whether a trimmed line names a section: '@' and capitals or digits, not a directive. */
    private static boolean isHeader(String l) {
        if (l.length() < 2 || l.charAt(0) != '@' || l.equals("@SMALLFONT")) return false;
        for (int i = 1; i < l.length(); i++) {
            final char c = l.charAt(i);
            if (!(c >= 'A' && c <= 'Z') && !(c >= '0' && c <= '9')) return false;
        }
        return true;
    }

    private static Entry entry(int width, boolean small, List<String> body) {
        final List<String> b = new ArrayList<>(body);
        while (!b.isEmpty() && b.get(b.size() - 1).trim().isEmpty()) {
            b.remove(b.size() - 1);
        }
        return new Entry(width, small, b);
    }

    /**
     * A section.
     *
     * @param section Its name without '@' ({@code FATHER2}).
     * @return The entry, or null.
     */
    Entry entry(String section) {
        return this.sections.get(section);
    }

    /**
     * A category name of {@code @PEDIA} ("Gründerväter" is 5).
     *
     * @param index The 0-based index (blank lines skipped).
     * @return The name, or null.
     */
    String category(int index) {
        final Entry e = entry("PEDIA");
        if (e == null || index < 0) return null;
        int i = 0;
        for (String l : e.lines) {
            if (l.trim().isEmpty()) continue;
            if (i++ == index) return l.trim();
        }
        return null;
    }


    // The father page

    /**
     * The page of a founding father (class comment).
     *
     * @param pedia The Colonopedia.
     * @param text The original's other texts (LABELS, NAMES).
     * @param tiny FONTTINY.
     * @param wood {@code WOODPANL.PIK}, or null (then the page is black).
     * @param n The father's row in NAMES {@code @FATHERS}
     *     ({@link ClassicFathers#index}).
     * @return The 320x200 picture, or null without the entry, the texts or
     *     the font.
     */
    static BufferedImage fatherPage(ClassicPedia pedia, ClassicText text,
                                    ClassicFont tiny, BufferedImage wood, int n) {
        if (pedia == null || text == null || tiny == null) return null;
        final Entry e = pedia.entry("FATHER" + n);
        final String name = ClassicFathers.name(text, n);
        final String header = text.misc(MISC_HEADER);
        final String category = pedia.category(CATEGORY_FATHERS);
        if (e == null || e.lines.isEmpty() || name == null || header == null
            || category == null) return null;
        final BufferedImage img = new BufferedImage(ClassicMenuBox.VW,
            ClassicMenuBox.VH, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = img.createGraphics();
        try {
            if (wood != null) g.drawImage(wood, 0, 0, null);
            final int[] ink = ClassicFont.colours(INK), gold = ClassicFont.colours(GOLD);
            final String h2 = "(" + name + ": " + category + ")";
            tiny.draw(g, header, (322 - tiny.stringWidth(header)) / 2, HEADER1_TOP, gold);
            tiny.draw(g, h2, (322 - tiny.stringWidth(h2)) / 2, HEADER2_TOP, gold);
            final int x0 = (321 - e.width) / 2;
            for (Line l : body(e, tiny)) {
                tiny.drawMarked(g, l.text, x0, l.top, ink, gold);
            }
        } finally {
            g.dispose();
        }
        return img;
    }

    /** One line of a page: its glyph top and its marked text. */
    static final class Line {

        final int top;
        final String text;

        Line(int top, String text) {
            this.top = top;
            this.text = text;
        }

        @Override
        public String toString() {
            return this.top + ":" + this.text;
        }
    }

    /**
     * An entry's lines as the page draws them (class comment): the title,
     * then the paragraphs reflowed, each line opening with '{' when the
     * gold runs on into it.
     *
     * @param e The entry.
     * @param tiny FONTTINY.
     * @return The lines, top to bottom.
     */
    static List<Line> body(Entry e, ClassicFont tiny) {
        final List<Line> out = new ArrayList<>();
        if (e.lines.isEmpty()) return out;
        final int limit = 320 - 2 * ((321 - e.width) / 2);
        int y = TITLE_TOP;
        String title = e.lines.get(0).trim();
        if (title.startsWith("^")) title = title.substring(1);
        out.add(new Line(y, title));
        y += PITCH;
        final StringBuilder sb = new StringBuilder();
        for (int i = 1; i < e.lines.size(); i++) sb.append(e.lines.get(i)).append(' ');
        final String all = sb.toString().replace("%%", "%");
        boolean gold = false, first = true;
        for (String para : all.split("\\^")) {
            final String p = para.replace('\t', ' ').trim();
            if (p.isEmpty()) continue;
            if (!first) y += PITCH;   // one blank line between paragraphs
            first = false;
            String cur = "";
            for (String w : p.split(" +")) {
                final String t = cur.isEmpty() ? w : cur + " " + w;
                if (cur.isEmpty() || tiny.markedWidth(t) <= limit) {
                    cur = t;
                    continue;
                }
                out.add(new Line(y, (gold ? "{" : "") + cur));
                gold = goldAfter(cur, gold);
                y += PITCH;
                cur = w;
            }
            if (!cur.isEmpty()) {
                out.add(new Line(y, (gold ? "{" : "") + cur));
                gold = goldAfter(cur, gold);
                y += PITCH;
            }
        }
        return out;
    }

    /** Whether the gold is on after a line that began with it {@code on}. */
    private static boolean goldAfter(String s, boolean on) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '{') on = true;
            if (s.charAt(i) == '}') on = false;
        }
        return on;
    }


    // The menu's lists (COLONIPÄDIE -> Gründerväter, build spec D8b; I: no recording)

    /** The lists' text width: the father box's {@code @width}. */
    static final int LIST_WIDTH = 230;

    /** The number of father types (NAMES {@code @FOUNDING}). */
    static final int TYPES = 5;

    /**
     * A type's row: NAMES {@code @FOUNDING} and LABELS {@code @MISC} 102,
     * as in the father box ("Handels- Berater").
     *
     * @param t The texts.
     * @param type The type (0 trade ... 4 religion).
     * @return The row, or null.
     */
    static String typeRow(ClassicText t, int type) {
        final List<String[]> types = (t == null) ? null : t.names("FOUNDING");
        final String advisor = (t == null) ? null : t.misc(ClassicFathers.MISC_ADVISOR);
        if (types == null || advisor == null || type < 0 || type >= types.size()) {
            return null;
        }
        return types.get(type)[0] + " " + advisor;
    }

    /**
     * A type's fathers in NAMES {@code @FATHERS} order.
     *
     * @param t The texts.
     * @param type The type.
     * @return Their rows.
     */
    static List<Integer> fathersOfType(ClassicText t, int type) {
        final List<Integer> out = new ArrayList<>();
        for (int n = 0; n < ClassicFathers.IDS.size(); n++) {
            if (ClassicFathers.type(t, n) == type) out.add(n);
        }
        return out;
    }

    /**
     * The first list: the category ("Gründerväter", PEDIA {@code @PEDIA}
     * 5) and one row per type.
     *
     * @param pedia The Colonopedia.
     * @param t The texts.
     * @param bar The barred row.
     * @return The list box, or null without the texts.
     */
    static ClassicAdvisorBox.Request typeList(ClassicPedia pedia, ClassicText t, int bar) {
        final String category = (pedia == null) ? null : pedia.category(CATEGORY_FATHERS);
        if (category == null) return null;
        final List<String> rows = new ArrayList<>();
        for (int i = 0; i < TYPES; i++) {
            final String r = typeRow(t, i);
            if (r == null) return null;
            rows.add(r);
        }
        return list("PEDIA fathers", category, rows, bar, -1.0);
    }

    /**
     * The second list: "Gründerväter (Handels- Berater)" and the type's
     * fathers by their names (NAMES {@code @FATHERS}).
     *
     * @param pedia The Colonopedia.
     * @param t The texts.
     * @param type The type.
     * @param bar The barred row.
     * @return The list box, or null without the texts.
     */
    static ClassicAdvisorBox.Request fatherList(ClassicPedia pedia, ClassicText t,
                                                int type, int bar) {
        return fatherList(pedia, t, type, bar, -1.0);
    }

    /**
     * {@link #fatherList(ClassicPedia, ClassicText, int, int)} asked again
     * after a page.
     *
     * @param pedia The Colonopedia.
     * @param t The texts.
     * @param type The type.
     * @param bar The barred row.
     * @param chainMs The least time after the page (ms), or negative.
     * @return The list box, or null without the texts.
     */
    static ClassicAdvisorBox.Request fatherList(ClassicPedia pedia, ClassicText t,
                                                int type, int bar, double chainMs) {
        final String category = (pedia == null) ? null : pedia.category(CATEGORY_FATHERS);
        final String head = typeRow(t, type);
        if (category == null || head == null) return null;
        final List<String> rows = new ArrayList<>();
        for (int n : fathersOfType(t, type)) {
            final String name = ClassicFathers.name(t, n);
            if (name == null) return null;
            rows.add(name);
        }
        if (rows.isEmpty()) return null;
        return list("PEDIA fathers " + type, category + " (" + head + ")", rows, bar,
                    chainMs);
    }

    /** A list box of the father box's form, Escape and a click beside it close it. */
    private static ClassicAdvisorBox.Request list(String id, String prompt,
                                                  List<String> rows, int bar,
                                                  double chainMs) {
        return ClassicAdvisorBox.Request.builder(id).chain(chainMs)
            .gameText(Collections.singletonList(prompt)).width(LIST_WIDTH)
            .rows(rows).defaultRow(Math.max(0, Math.min(bar, rows.size() - 1)))
            .noCancelRow().rowIndent(ClassicMenuBox.LIST_INDENT)
            .stopgap(prompt, null).build();
    }
}
