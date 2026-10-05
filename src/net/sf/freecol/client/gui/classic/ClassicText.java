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
 * The original game's own <b>text files</b>, parsed at runtime.
 *
 * <p>The 1994 game keeps every German string the new-game screens draw in
 * three DOS text files -- {@code GAME.TXT} (prompts, nation pages, the
 * audience scroll), {@code NAMES.TXT} (nation, leader and difficulty names)
 * and {@code LABELS.TXT} (picker titles, subtitles, bonus words).  They are
 * copyrighted, so they are never pasted into this repository: {@code ant
 * classic-assets} copies them byte for byte into the git-ignored pack
 * ({@code <pack>/text/}), and this class reads them from there by plain file
 * access (see {@link ClassicPackFiles}).  Tracked code therefore names only
 * sections ({@code @NATION3A}) and indices, never a sentence.
 *
 * <p><b>Decoding.</b> The files are 7-bit DOS text, CRLF, ending in
 * {@code @END} and the EOF byte 0x1A.  The German letters sit on the game's
 * own codes -- the same ones {@link ClassicFont#toCode} maps -- and three of
 * them (ä 0x60, Ü 0x5C, ü 0x7F) are on printable ASCII, so decoding is a
 * fixed 7-entry {@link #TABLE}, never a charset.  Lines starting with ';' are
 * comments.  A line {@code @NAME} (upper case after the '@') starts a section;
 * lines {@code @width=}, {@code @x=}, {@code @y=}, {@code @default=},
 * {@code @options}, {@code @checkbox}, {@code @smallfont} are directives of a
 * GAME.TXT message.
 *
 * <p><b>Markup</b> is kept as written ({@code { } ^ _ %}); the layout
 * ({@link ClassicTextLayout}) and the font ({@link ClassicFont#drawMarked})
 * interpret it.
 *
 * <p><b>MENU.TXT</b> (optional, the in-game menu bar) has the same codes and
 * section layout: {@code @GAME}, {@code @VIEW}, {@code @ORDERS},
 * {@code @REPORTS}, {@code @TRADE}, {@code @CUP}, {@code @PEDIA}, each a
 * title line and then the items indented by two spaces, '~' marking the
 * gold hotkey letters.  Packs converted before it was copied lack it; the
 * menu strip then shows its wood only ({@link #hasMenus}).
 */
final class ClassicText {

    private static final Logger logger = Logger.getLogger(ClassicText.class.getName());

    /** The three files, as the converter names them in {@code <pack>/text}. */
    static final String GAME = "GAME.TXT", NAMES = "NAMES.TXT", LABELS = "LABELS.TXT";

    /** The optional fourth file: the in-game menu bar's titles and items. */
    static final String MENU = "MENU.TXT";

    /**
     * The game's codes for the German letters, as {code, char} pairs.  Every
     * other byte stands for itself.  Matches {@link ClassicFont#toCode}
     * (ClassicFont.java:385-401), so decoded text draws back on the same
     * glyphs.
     */
    static final char[][] TABLE = {
        { 0x1C, 'ö' }, { 0x1D, 'ß' }, { 0x1E, 'Ä' }, { 0x1F, 'Ö' },
        { 0x5C, 'Ü' }, { 0x60, 'ä' }, { 0x7F, 'ü' }
    };

    /**
     * LABELS.TXT {@code @MISC} indices used by the new-game screens.  The
     * convention: blank lines are SKIPPED and the count starts at 0 with the
     * first line after the header.  Checked against the file: index 160 is
     * LABELS.TXT:176, 161 is :177, 162 is :178, 164..168 are :180-184, 169
     * is :185, 170 is :186, 172..175 are :188-191 (the one blank line, :51,
     * is skipped).
     */
    static final int MISC_DONE_CLICK = 160, MISC_CHOOSE_DIFF = 161,
        MISC_DIFFICULTY = 162, MISC_DIFF_SUB0 = 164, MISC_CHOOSE_NATION = 169,
        MISC_POWER = 170, MISC_BONUS0 = 172;

    /** Whether the "texts missing" INFO line was logged already. */
    private static boolean missLogged = false;

    /** Loaded texts by pack directory. */
    private static final Map<File, ClassicText> CACHE = new HashMap<>();


    /**
     * One GAME.TXT message: its directives, its text lines (up to the first
     * blank line) and its options (after {@code @options}, or after the
     * first blank line, up to the next blank line).  Lines keep their markup.
     */
    static final class Message {

        /** {@code @width=}, {@code @x=}, {@code @y=}, {@code @default=}; null if absent. */
        final Integer width, x, y, defaultOption;

        /** The flags {@code @options}, {@code @checkbox}, {@code @smallfont}. */
        final boolean hasOptions, checkbox, smallFont;

        /** The message text lines. */
        final List<String> text;

        /** The option lines. */
        final List<String> options;

        Message(Integer width, Integer x, Integer y, Integer defaultOption,
                boolean hasOptions, boolean checkbox, boolean smallFont,
                List<String> text, List<String> options) {
            this.width = width;
            this.x = x;
            this.y = y;
            this.defaultOption = defaultOption;
            this.hasOptions = hasOptions;
            this.checkbox = checkbox;
            this.smallFont = smallFont;
            this.text = Collections.unmodifiableList(new ArrayList<>(text));
            this.options = Collections.unmodifiableList(new ArrayList<>(options));
        }

        /** @return This message with its text lines replaced. */
        Message withText(List<String> newText) {
            return new Message(this.width, this.x, this.y, this.defaultOption,
                this.hasOptions, this.checkbox, this.smallFont, newText,
                this.options);
        }
    }


    /** Sections of each file: name (without '@') -> body lines. */
    private final Map<String, List<String>> game, names, labels;

    /** The sections of MENU.TXT, or null when the pack has no such file. */
    private final Map<String, List<String>> menus;


    private ClassicText(Map<String, List<String>> game,
                        Map<String, List<String>> names,
                        Map<String, List<String>> labels,
                        Map<String, List<String>> menus) {
        this.game = game;
        this.names = names;
        this.labels = labels;
        this.menus = menus;
    }

    /**
     * The texts of a pack, read once per pack directory.
     *
     * @param pack The pack (may be null).
     * @return The texts, or null when the pack lacks any of the three files
     *     (it predates them: re-run {@code ant classic-assets}).
     */
    static ClassicText load(ClassicPackFiles pack) {
        if (pack == null) return logMiss("no classic_original pack");
        synchronized (CACHE) {
            ClassicText t = CACHE.get(pack.directory());
            if (t != null) return t;
            final File g = pack.textFile(GAME), n = pack.textFile(NAMES),
                l = pack.textFile(LABELS);
            if (g == null || n == null || l == null) {
                return logMiss("classic_original pack has no text/ folder");
            }
            final File m = pack.textFile(MENU);
            if (m == null) {
                logger.info("classic_original pack has no text/" + MENU
                    + " -- the in-game menu strip shows no titles"
                    + " (re-run ant classic-assets)");
            }
            try {
                t = fromFiles(g, n, l, m);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Unreadable original texts", e);
                return null;
            }
            CACHE.put(pack.directory(), t);
            return t;
        }
    }

    private static synchronized ClassicText logMiss(String why) {
        if (!missLogged) {
            missLogged = true;
            logger.info(why + " -- the original new-game screens are skipped"
                + " (re-run ant classic-assets)");
        }
        return null;
    }

    /**
     * Parse three files in the original format.
     *
     * @throws IOException if one cannot be read.
     */
    static ClassicText fromFiles(File game, File names, File labels) throws IOException {
        return fromFiles(game, names, labels, null);
    }

    /**
     * Parse three files in the original format plus the optional menu file.
     *
     * @param menu MENU.TXT, or null when there is none.
     * @throws IOException if one cannot be read.
     */
    static ClassicText fromFiles(File game, File names, File labels, File menu)
        throws IOException {
        return new ClassicText(sections(decode(Files.readAllBytes(game.toPath()))),
                               sections(decode(Files.readAllBytes(names.toPath()))),
                               sections(decode(Files.readAllBytes(labels.toPath()))),
                               (menu == null) ? null
                                   : sections(decode(Files.readAllBytes(menu.toPath()))));
    }


    // Decoding

    /** @return The Unicode character for a byte of the game's text. */
    static char decodeByte(int b) {
        for (char[] e : TABLE) {
            if (e[0] == b) return e[1];
        }
        return (char) b;
    }

    /**
     * Decode a whole file into lines: stop at the EOF byte 0x1A or a line
     * {@code @END}, drop CRs, drop ';' comment lines.  Blank lines are kept
     * (they end messages).
     */
    static List<String> decode(byte[] bytes) {
        final List<String> lines = new ArrayList<>();
        final StringBuilder sb = new StringBuilder();
        boolean ended = false;
        for (int i = 0; i <= bytes.length && !ended; i++) {
            final int b = (i < bytes.length) ? (bytes[i] & 0xFF) : -1;
            if (b == '\r') continue;
            if (b == '\n' || b == 0x1A || b < 0) {
                final String line = sb.toString();
                sb.setLength(0);
                if (line.equals("@END")) break;
                if (b != '\n' && line.isEmpty()) break;    // EOF, nothing pending
                if (!line.startsWith(";")) lines.add(line);
                if (b != '\n') ended = true;
            } else {
                sb.append(decodeByte(b));
            }
        }
        return lines;
    }

    /** Whether a line starts a section ('@' + an upper-case letter or digit). */
    private static boolean isSection(String line) {
        return line.length() > 1 && line.charAt(0) == '@'
            && (Character.isUpperCase(line.charAt(1)) || Character.isDigit(line.charAt(1)));
    }

    /** Split decoded lines into sections (the header's trailing blanks trimmed). */
    private static Map<String, List<String>> sections(List<String> lines) {
        final Map<String, List<String>> m = new HashMap<>();
        List<String> body = null;
        for (String l : lines) {
            if (isSection(l)) {
                body = new ArrayList<>();
                m.put(l.substring(1).trim(), body);
            } else if (body != null) {
                body.add(l);
            }
        }
        return m;
    }


    // GAME.TXT

    /**
     * A GAME.TXT message.
     *
     * @param section The section name without '@', e.g. {@code NATION3A}.
     * @return The message, or null when there is no such section.
     */
    Message message(String section) {
        final List<String> body = this.game.get(section);
        if (body == null) return null;
        Integer width = null, x = null, y = null, def = null;
        boolean opts = false, checkbox = false, small = false;
        final List<String> text = new ArrayList<>();
        final List<String> options = new ArrayList<>();
        int part = 0;     // 0 text, 1 options, 2 done
        for (String l : body) {
            if (part == 2) break;
            if (l.startsWith("@")) {
                final String d = l.substring(1).trim();
                final int eq = d.indexOf('=');
                final String key = (eq < 0) ? d : d.substring(0, eq).trim();
                final Integer val = (eq < 0) ? null : parseInt(d.substring(eq + 1));
                switch (key) {
                case "width": width = val; break;
                case "x": x = val; break;
                case "y": y = val; break;
                case "default": def = val; break;
                case "options": opts = true; part = 1; break;
                case "checkbox": checkbox = true; break;
                case "smallfont": small = true; break;
                default: break;
                }
                continue;
            }
            if (l.trim().isEmpty()) {
                part = (part == 0 && !opts) ? 1 : 2;
                if (part == 1 && text.isEmpty()) part = 2;
                continue;
            }
            if (part == 0) text.add(l); else options.add(l);
        }
        return new Message(width, x, y, def, opts, checkbox, small, text, options);
    }

    private static Integer parseInt(String s) {
        try {
            return Integer.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }


    // NAMES.TXT

    /**
     * The rows of a NAMES.TXT section up to its first blank line, split at
     * ',' and trimmed (e.g. {@code @COUNTRY}: name, colour index).
     *
     * @param section The section name without '@'.
     * @return The rows; empty when there is no such section.
     */
    List<String[]> names(String section) {
        final List<String[]> rows = new ArrayList<>();
        final List<String> body = this.names.get(section);
        if (body == null) return rows;
        for (String l : body) {
            if (l.trim().isEmpty()) break;
            final String[] cols = l.split(",", -1);
            for (int i = 0; i < cols.length; i++) cols[i] = cols[i].trim();
            rows.add(cols);
        }
        return rows;
    }


    // LABELS.TXT

    /**
     * An entry of LABELS.TXT {@code @MISC}: blank lines skipped, 0-based
     * (see {@link #MISC_DONE_CLICK} for the checked index convention).
     *
     * @param index The index.
     * @return The entry, or null when out of range.
     */
    String misc(int index) {
        return label("MISC", index);
    }

    /**
     * An entry of any LABELS.TXT section, with the {@link #misc} convention
     * (blank lines skipped, 0-based).  The in-game screens need more than
     * {@code @MISC}: {@code @CTITLE} 1 and 9 are the right panel's gold and
     * tax labels (LABELS.TXT:256/264), {@code @INFO} 0 and 1 its moves and
     * position labels (:9-10).
     *
     * @param section The section name without '@', e.g. {@code CTITLE}.
     * @param index The index.
     * @return The entry, or null when the section or index is missing.
     */
    String label(String section, int index) {
        final List<String> body = this.labels.get(section);
        if (body == null || index < 0) return null;
        int i = 0;
        for (String l : body) {
            if (l.trim().isEmpty()) continue;
            if (i++ == index) return l;
        }
        return null;
    }


    // MENU.TXT

    /** @return Whether MENU.TXT was in the pack. */
    boolean hasMenus() {
        return this.menus != null;
    }

    /**
     * One menu of MENU.TXT: the title line first, then the items in file
     * order, up to the section's first blank line.  The title is trimmed (the
     * file indents the first one, {@code @GAME}, like an item); each item
     * loses exactly its two-space indent, so a deliberate trailing space
     * stays.  The '~' markup and the digit-width blank '#' are kept:
     * {@link ClassicFont#drawMarked} draws both as the original does.
     *
     * @param section The section name without '@', e.g. {@code ORDERS}.
     * @return The lines, or null when MENU.TXT or the section is missing.
     */
    List<String> menu(String section) {
        if (this.menus == null) return null;
        final List<String> body = this.menus.get(section);
        if (body == null) return null;
        final List<String> out = new ArrayList<>();
        for (String l : body) {
            if (l.trim().isEmpty()) {
                if (out.isEmpty()) continue;
                break;
            }
            if (out.isEmpty()) {
                out.add(l.trim());
            } else {
                out.add(l.startsWith("  ") ? l.substring(2) : l.trim());
            }
        }
        return out.isEmpty() ? null : Collections.unmodifiableList(out);
    }


    // Helpers

    /**
     * Fill the original's placeholders: {@code %COUNTRY}, {@code %STRINGn},
     * {@code %NUMBERn} -- the map keys without '%', e.g. {@code COUNTRY}.
     * {@code %%} is a literal '%'; any other '%' (as in a "50%-" bonus) and
     * placeholders without a value stay as written.
     */
    static String substitute(String s, Map<String, String> values) {
        if (s == null || s.indexOf('%') < 0) return s;
        final StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (c != '%') {
                sb.append(c);
                continue;
            }
            if (i + 1 < s.length() && s.charAt(i + 1) == '%') {
                sb.append('%');
                i++;
                continue;
            }
            String best = null;
            if (values != null) {
                for (String k : values.keySet()) {
                    if (s.startsWith(k, i + 1)
                        && (best == null || k.length() > best.length())) best = k;
                }
            }
            if (best == null) {
                sb.append('%');
            } else {
                sb.append(values.get(best));
                i += best.length();
            }
        }
        return sb.toString();
    }

    /**
     * Upper-case a-z only.  The original upper-cases its picker labels this
     * way: the most difficult level's label keeps its lowercase 'ö'
     * ({@code opening_060}), which {@link String#toUpperCase} would turn into
     * a different glyph.
     */
    static String upperAscii(String s) {
        if (s == null) return null;
        final char[] cs = s.toCharArray();
        for (int i = 0; i < cs.length; i++) {
            if (cs[i] >= 'a' && cs[i] <= 'z') cs[i] = (char) (cs[i] - 'a' + 'A');
        }
        return new String(cs);
    }
}
