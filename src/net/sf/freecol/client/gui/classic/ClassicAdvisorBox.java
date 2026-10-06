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
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;


/**
 * The original's <b>advisor box</b> (build spec W7): every question, choice
 * and notice of the game, drawn into the 320x200 canvas over the map and the
 * panel.  This class is the box itself, stateless and headless: what a box
 * says ({@link Request}), where it goes ({@link #layout}), how it looks
 * ({@link #paint}) and how its selection bar answers keys and the mouse
 * ({@link Bar}).  {@link ClassicAdvisorLayer} shows it in the game.
 *
 * <p>Every rule here is measured on the landfall clip's 24 GAME.TXT boxes
 * ({@code landfall 05-dialogs-and-events.md} sections 2 and 3), clips
 * 004-007 (the Sioux chief, the King) and the dago-colony clips (the bar);
 * V where verified on the pixels, I where inferred:
 * <ul>
 *   <li><b>Look (V).</b>  {@link ClassicMenuBox#paintDialogFrame} in the
 *       GAME theme, WOODTILE from the box's corner; no title bar, no
 *       buttons, no page counter.  FONTTINY, green ink 68 with {@code {..}}
 *       gold 149, prompt at box + (5, 9), 6 px apart; rows at x + 9, glyph
 *       top {@code y + 13 + 6P + 8i}; the bar a flat index-138 strip
 *       {@code (x + 4, rowTop - 1, w - 8, 7)} under the row's text, which
 *       keeps its colour.  A greyed row is drawn in
 *       {@link ClassicMenuBox#DISABLED_INK} (dago-colony2, the job menu).</li>
 *   <li><b>Size (V).</b>  {@code w = @width + 6}, {@code h = 6P + 8R + 18}
 *       ({@link ClassicMenuBox#dialogHeight}), the text reflowed to the box
 *       ({@link ClassicTextLayout#BOX}: GAME.TXT's own line breaks are
 *       ignored).</li>
 *   <li><b>Place (V).</b>  Without a portrait the box is centred,
 *       {@code x = (321 - w) div 2}, {@code y = (201 - h) div 2}, or
 *       {@code y = @y} when the message has one (@TUTORIAL17).  With an
 *       advisor ({@link Portrait#over}) the union of box and portrait is
 *       centred the same way ({@link ClassicFirstScene#place}) and the
 *       portrait is drawn OVER the box at the advisor's offset.  A chief
 *       ({@link Portrait#chief}) stands at the right, under the box:
 *       {@code chief.x = min(246, 317 - pw)}, {@code chief.y = (197 - ph)
 *       div 2} (Arawak (246,8), Sioux (210,10)), box right edge 3 px left
 *       of him, at least x = 0 (box x 7 and 0), the box drawn over him.  The
 *       King ({@link Portrait#KING}) is the exception the other way round:
 *       he stands at the left, {@code (0, (197 - ph) div 2)} = (0,18), the
 *       box flush right, {@code x = 320 - w} (clip005/006: (84,68,236,64)).
 *       The box is centred vertically in all three.</li>
 *   <li><b>Bar (V).</b>  It starts on GAME.TXT's {@code @default=n}
 *       (1-based), else on row 1; it moves one row per key and is redrawn in
 *       one paint; Enter takes the row it is on, Escape the box's cancel
 *       row (Roger's rule).  The mouse never moves it by hovering: a press
 *       on a row puts it there, the release on that row takes it; a press
 *       outside the box removes it and the release closes the box as
 *       Escape (clip004, the options box).  A box without rows is a notice:
 *       any key, or a click, dismisses it.</li>
 * </ul>
 */
final class ClassicAdvisorBox {

    /** The GAME theme's greyed rows (D2, dago-colony2). */
    private static final int DISABLED = ClassicMenuBox.DISABLED_INK;

    /** The screen. */
    private static final int VW = ClassicMenuBox.VW, VH = ClassicMenuBox.VH;

    /** The widest box a long FreeCol text may widen to (@TUTORIAL17's 300). */
    static final int MAX_WIDTH = 300;

    /** The width FreeCol's own texts are laid out at (most GAME.TXT boxes). */
    static final int FREECOL_WIDTH = 230;


    /** Who stands at the box, and where. */
    static final class Portrait {

        /** How the portrait is placed. */
        enum Kind {
            /** An advisor: over the box, at an offset from it. */
            OVER,
            /** A native chief: at the right, under the box. */
            CHIEF,
            /** The King: at the left, under the box, the box flush right. */
            KING
        }

        /** No portrait: the box alone, centred. */
        static final Portrait NONE = new Portrait(null, Kind.OVER, Anchor.LEFT, 0, 0);

        /** The admiral (MSS0, 75x91) at box + (-4, -71) (TUTORIAL1, SAILHOME). */
        static final Portrait ADMIRAL = new Portrait("MSS0.SS.000", Kind.OVER,
            Anchor.LEFT, ClassicFirstScene.MSS0_OFF_X, ClassicFirstScene.MSS0_OFF_Y);

        /** The soldier (MSS1, 72x139): right edge at box right + 17, -77. */
        static final Portrait SOLDIER = new Portrait("MSS1.SS.000", Kind.OVER,
            Anchor.RIGHT, -55, -77);

        /** The trade advisor (MSS2, 122x84), centred, -78 (Europe 011/013). */
        static final Portrait TRADE = new Portrait("MSS2.SS.000", Kind.OVER,
            Anchor.CENTRE, 0, ClassicFirstScene.MSS2_OFF_Y);

        /** The frontiersman (MSS3, 149x95), centred, -87 (LANDFALL). */
        static final Portrait SCOUT = new Portrait("MSS3.SS.000", Kind.OVER,
            Anchor.CENTRE, 0, -87);

        /** The priest (MSS4, 93x59), centred, -52 (UNREST). */
        static final Portrait PRIEST = new Portrait("MSS4.SS.000", Kind.OVER,
            Anchor.CENTRE, 0, -52);

        /** The colonist (MSS5, 60x68), centred, -62 (ABANDON, dago-colony2). */
        static final Portrait COLONIST = new Portrait("MSS5.SS.000", Kind.OVER,
            Anchor.CENTRE, 0, -62);

        /** The King (KING, 79x161): at the left, the box flush right. */
        static final Portrait KING = new Portrait("KING.SS.000", Kind.KING,
            Anchor.LEFT, 0, 0);

        /** Where an advisor's x offset counts from. */
        enum Anchor {
            /** The box's left edge. */
            LEFT,
            /** The box's right edge. */
            RIGHT,
            /** Centred over the box: {@code (w - pw + 1) div 2}. */
            CENTRE
        }

        /** The sprite frame ({@code MSS0.SS.000}), null for none. */
        final String sprite;

        /** The placement. */
        final Kind kind;

        /** Where {@link #dx} counts from (OVER only). */
        private final Anchor anchor;

        /** The offset from the box's top-left (OVER only). */
        private final int dx, dy;

        private Portrait(String sprite, Kind kind, Anchor anchor, int dx, int dy) {
            this.sprite = sprite;
            this.kind = kind;
            this.anchor = anchor;
            this.dx = dx;
            this.dy = dy;
        }

        /**
         * The chief of an original tribe: {@code IND<n>A0.SS.000}, NAMES.TXT
         * {@code @TRIBES} order (Inca 0, Aztec 1, Arawak 2, Iroquois 3,
         * Cherokee 4, Apache 5, Sioux 6, Tupi 7).
         *
         * @param tribe The tribe's index.
         * @return The portrait, {@link #NONE} out of range.
         */
        static Portrait chief(int tribe) {
            if (tribe < 0 || tribe > 7) return NONE;
            return new Portrait("IND" + tribe + "A0.SS.000", Kind.CHIEF,
                                Anchor.LEFT, 0, 0);
        }

        /**
         * An advisor's place against a box.
         *
         * @param boxW The box width.
         * @param pw The portrait width.
         * @return The offset from the box's top-left.
         */
        Point offset(int boxW, int pw) {
            switch (this.anchor) {
            case CENTRE: return new Point((boxW - pw + 1) / 2, this.dy);
            case RIGHT: return new Point(boxW + this.dx, this.dy);
            default: return new Point(this.dx, this.dy);
            }
        }

        /** @return The SS file of the sprite ({@code MSS0.SS}), or null. */
        String sheet() {
            if (this.sprite == null) return null;
            final int dot = this.sprite.lastIndexOf('.');
            return this.sprite.substring(0, dot);
        }

        @Override
        public String toString() {
            return (this.sprite == null) ? "none" : this.sprite;
        }
    }


    /** What a box says: its text, rows, bar and portrait. */
    static final class Request {

        /** What it is (a GAME.TXT section, a FreeCol message id), for logs. */
        final String id;

        /**
         * The text as paragraphs of raw lines, markup kept: a GAME.TXT
         * message is one paragraph; FreeCol's text one per line.
         */
        final List<List<String>> paragraphs;

        /** GAME.TXT's {@code @width}: the box is 6 wider. */
        final int width;

        /** GAME.TXT's {@code @y}, or null to centre. */
        final Integer y;

        /** The rows, markup kept; empty for a notice. */
        final List<String> rows;

        /** Per row, whether it is greyed (it cannot be taken). */
        final boolean[] disabled;

        /** The row the bar starts on; -1 without rows. */
        final int defaultRow;

        /** Escape's row, or -1: Escape then closes with {@link Bar#DISMISSED}. */
        final int cancelRow;

        /** Who stands at the box. */
        final Portrait portrait;

        /** The stopgap's window title, when the box cannot be drawn. */
        final String title;

        /** The stopgap's illustration, or null. */
        final Image icon;

        /** Whether the stopgap is the selection list (a choice). */
        final boolean list;

        private Request(Builder b) {
            this.id = b.id;
            final List<List<String>> ps = new ArrayList<>();
            for (List<String> p : b.paragraphs) {
                ps.add(Collections.unmodifiableList(new ArrayList<>(p)));
            }
            this.paragraphs = Collections.unmodifiableList(ps);
            this.width = b.width;
            this.y = b.y;
            this.rows = Collections.unmodifiableList(new ArrayList<>(b.rows));
            this.disabled = Arrays.copyOf(b.disabled, this.rows.size());
            final int n = this.rows.size();
            this.defaultRow = (n == 0) ? -1 : ClassicHud.clamp(b.defaultRow, 0, n - 1);
            this.cancelRow = (b.cancelRow < 0 || b.cancelRow >= n) ? -1 : b.cancelRow;
            this.portrait = (b.portrait == null) ? Portrait.NONE : b.portrait;
            this.title = (b.title == null) ? "" : b.title;
            this.icon = b.icon;
            this.list = b.list;
        }

        /** @return Whether it is a notice (no rows). */
        boolean isNotice() {
            return this.rows.isEmpty();
        }

        /** @return Whether row {@code i} can be taken. */
        boolean enabled(int i) {
            return i >= 0 && i < this.rows.size() && !this.disabled[i];
        }

        /**
         * @return What Escape answers: the cancel row, or
         *     {@link Bar#DISMISSED}; a notice's 0.
         */
        int escapeAnswer() {
            if (isNotice()) return 0;
            return (this.cancelRow >= 0) ? this.cancelRow : Bar.DISMISSED;
        }

        /** @return The text as one plain string (markup dropped), for logs and the stopgap. */
        String plainText() {
            final StringBuilder sb = new StringBuilder();
            for (List<String> p : this.paragraphs) {
                final String s = plain(String.join(" ", p));
                if (s.isEmpty()) continue;
                if (sb.length() > 0) sb.append('\n');
                sb.append(s);
            }
            return sb.toString();
        }

        /** @return The rows as plain strings (markup dropped). */
        String[] plainRows() {
            final String[] out = new String[this.rows.size()];
            for (int i = 0; i < out.length; i++) out[i] = plain(this.rows.get(i));
            return out;
        }

        @Override
        public String toString() {
            return this.id + " rows=" + this.rows.size() + " bar=" + this.defaultRow
                + " esc=" + this.cancelRow + " portrait=" + this.portrait;
        }

        /**
         * A request.
         *
         * @param id What it is (for logs).
         * @return A builder.
         */
        static Builder builder(String id) {
            return new Builder(id);
        }
    }

    /** Builds a {@link Request}. */
    static final class Builder {

        private final String id;
        private final List<List<String>> paragraphs = new ArrayList<>();
        private int width = FREECOL_WIDTH;
        private Integer y = null;
        private final List<String> rows = new ArrayList<>();
        private boolean[] disabled = new boolean[0];
        private int defaultRow = 0;
        private int cancelRow = Integer.MIN_VALUE;
        private Portrait portrait = Portrait.NONE;
        private String title = null;
        private Image icon = null;
        private boolean list = false;

        private Builder(String id) {
            this.id = (id == null) ? "box" : id;
        }

        /** A GAME.TXT message's lines: one paragraph, reflowed. */
        Builder gameText(List<String> lines) {
            this.paragraphs.add(new ArrayList<>(lines));
            return this;
        }

        /** FreeCol's own text: literal, one paragraph per line. */
        Builder freeColText(String text) {
            this.paragraphs.addAll(literalParagraphs(text));
            return this;
        }

        /** @param w GAME.TXT's {@code @width}. */
        Builder width(int w) {
            this.width = w;
            return this;
        }

        /** @param yy GAME.TXT's {@code @y}, or null. */
        Builder y(Integer yy) {
            this.y = yy;
            return this;
        }

        /** The rows, markup kept (GAME.TXT's own, or {@link #literal} ones). */
        Builder rows(List<String> rs) {
            this.rows.addAll(rs);
            return this;
        }

        /** The rows, markup kept. */
        Builder rows(String... rs) {
            return rows(Arrays.asList(rs));
        }

        /** Per row, whether it is greyed. */
        Builder disabled(boolean[] d) {
            this.disabled = (d == null) ? new boolean[0] : d.clone();
            return this;
        }

        /** The bar's first row (0-based). */
        Builder defaultRow(int i) {
            this.defaultRow = i;
            return this;
        }

        /** Escape's row (0-based); without one Escape is the last row. */
        Builder cancelRow(int i) {
            this.cancelRow = i;
            return this;
        }

        /** Escape closes with no row ({@link Bar#DISMISSED}). */
        Builder noCancelRow() {
            this.cancelRow = -1;
            return this;
        }

        /** Who stands at the box. */
        Builder portrait(Portrait p) {
            this.portrait = p;
            return this;
        }

        /** The stopgap's window title and illustration. */
        Builder stopgap(String t, Image i) {
            this.title = t;
            this.icon = i;
            return this;
        }

        /** The stopgap is the selection list. */
        Builder list() {
            this.list = true;
            return this;
        }

        /** @return The request. */
        Request build() {
            if (this.cancelRow == Integer.MIN_VALUE) this.cancelRow = this.rows.size() - 1;
            if (this.disabled.length < this.rows.size()) {
                this.disabled = Arrays.copyOf(this.disabled, this.rows.size());
            }
            return new Request(this);
        }
    }


    /** Where everything of one box goes, in 320x200 pixels. */
    static final class Layout {

        /** The request. */
        final Request request;

        /** The box's outer bounds. */
        final Rectangle box;

        /** The laid-out prompt lines (screen coordinates). */
        final List<ClassicTextLayout.Line> prompt;

        /** The rows (screen coordinates: x, glyph top, marked text). */
        final List<ClassicTextLayout.Line> rows;

        /** The portrait, or null. */
        final BufferedImage portrait;

        /** Its top-left, or null. */
        final Point portraitAt;

        /** Whether the portrait is under the box (a chief, the King). */
        final boolean under;

        Layout(Request request, Rectangle box, List<ClassicTextLayout.Line> prompt,
               List<ClassicTextLayout.Line> rows, BufferedImage portrait,
               Point portraitAt, boolean under) {
            this.request = request;
            this.box = box;
            this.prompt = Collections.unmodifiableList(new ArrayList<>(prompt));
            this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
            this.portrait = portrait;
            this.portraitAt = portraitAt;
            this.under = under;
        }

        /** @return The prompt's line count P. */
        int promptLines() {
            return this.prompt.size();
        }

        /**
         * The row under a 320x200 point.
         *
         * @param vx The x.
         * @param vy The y.
         * @return The row, or -1.
         */
        int rowAt(int vx, int vy) {
            for (int i = 0; i < this.rows.size(); i++) {
                if (ClassicMenuBox.rowHitRect(this.box, promptLines(), i)
                    .contains(vx, vy)) return i;
            }
            return -1;
        }

        /** @return Whether a 320x200 point is on the box. */
        boolean inBox(int vx, int vy) {
            return this.box.contains(vx, vy);
        }

        /** @return The bar's rectangle at row {@code i}. */
        Rectangle barRect(int i) {
            return ClassicMenuBox.barRect(this.box, promptLines(), i);
        }

        /** @return Everything the box paints: box and portrait. */
        Rectangle bounds() {
            final Rectangle r = new Rectangle(this.box);
            if (this.portrait != null && this.portraitAt != null) {
                r.add(new Rectangle(this.portraitAt.x, this.portraitAt.y,
                    this.portrait.getWidth(), this.portrait.getHeight()));
            }
            return r.intersection(new Rectangle(0, 0, VW, VH));
        }
    }


    private ClassicAdvisorBox() {}   // static helpers only


    // Text

    /**
     * FreeCol's own text as box text: its characters that are markup here
     * ({@code { } ~ ^ _}) replaced, so the layout and the font draw it as
     * written.
     *
     * @param s The text, or null.
     * @return The literal text.
     */
    static String literal(String s) {
        if (s == null) return "";
        return s.replace('{', '(').replace('}', ')').replace('~', '-')
            .replace("^", "").replace('_', ' ').replace('\t', ' ')
            .replace('\r', ' ');
    }

    /**
     * FreeCol's text as paragraphs: one per line, each literal; blank lines
     * dropped.
     *
     * @param s The text, or null.
     * @return The paragraphs.
     */
    static List<List<String>> literalParagraphs(String s) {
        final List<List<String>> out = new ArrayList<>();
        if (s == null) return out;
        for (String line : s.split("\n")) {
            final String l = literal(line).trim();
            if (!l.isEmpty()) out.add(Collections.singletonList(l));
        }
        return out;
    }

    /**
     * A GAME.TXT line without its markup: braces and the '~' dropped,
     * blanks collapsed.
     *
     * @param s The line.
     * @return The plain text.
     */
    static String plain(String s) {
        if (s == null) return "";
        return s.replace("{", "").replace("}", "").replace("~", "")
            .replace("^", "").replace("_", "").replaceAll("\\s+", " ").trim();
    }

    /**
     * A GAME.TXT message as a request: its lines and options with the
     * values filled in, its {@code @width} and {@code @y}, the bar on
     * {@code @default} (1-based) or row 1; Escape takes the last row unless
     * the caller says otherwise.
     *
     * @param section The message's section (the request's id).
     * @param m The message.
     * @param values The placeholders' values ({@link ClassicText#substitute}),
     *     or null.
     * @return A builder, or null without a message or its {@code @width}.
     */
    static Builder fromGameText(String section, ClassicText.Message m,
                                Map<String, String> values) {
        if (m == null || m.width == null) return null;
        final List<String> lines = new ArrayList<>(m.text.size());
        for (String s : m.text) lines.add(ClassicText.substitute(s, values));
        final List<String> rows = new ArrayList<>(m.options.size());
        for (String s : m.options) rows.add(ClassicText.substitute(s, values).trim());
        return Request.builder(section).gameText(lines).width(m.width).y(m.y)
            .rows(rows).defaultRow(defaultRow(m));
    }

    /**
     * The bar's start row of a GAME.TXT message: {@code @default=n}
     * (1-based) when given, else row 1 (landfall: @SAILHOME, @LANDFALL;
     * dago-colony2: @ABANDON's 2, @BUYME1's 1; every box without one starts
     * on row 1).
     *
     * @param m The message.
     * @return The 0-based row.
     */
    static int defaultRow(ClassicText.Message m) {
        if (m == null || m.defaultOption == null) return 0;
        return Math.max(0, m.defaultOption - 1);
    }


    // Layout

    /**
     * Where a box goes (class comment).
     *
     * @param r The request.
     * @param tiny FONTTINY.
     * @param portrait The portrait's sprite, or null (then none is drawn,
     *     and an advisor box is centred alone).
     * @return The layout, or null without the font, or when the rows do not
     *     fit on the screen.
     */
    static Layout layout(Request r, ClassicFont tiny, BufferedImage portrait) {
        if (r == null || tiny == null) return null;
        final int rows = r.rows.size();
        int width = Math.max(10, r.width);
        List<ClassicTextLayout.Line> probe = lines(r, tiny, width, 0, 0);
        // A FreeCol text too long for the screen gets the widest box.
        if (ClassicMenuBox.dialogHeight(probe.size(), rows) > VH && width < MAX_WIDTH) {
            width = MAX_WIDTH;
            probe = lines(r, tiny, width, 0, 0);
        }
        int p = probe.size();
        final int room = (VH - ClassicMenuBox.dialogHeight(0, rows)) / ClassicMenuBox.PROMPT_PITCH;
        if (room < 0) return null;
        final boolean cut = p > room;
        if (cut) p = room;
        final int w = width + 6, h = ClassicMenuBox.dialogHeight(p, rows);
        final Portrait who = r.portrait;
        final BufferedImage sprite = (who.sprite == null) ? null : portrait;
        final Point boxAt, picAt;
        boolean under = false;
        if (sprite == null) {
            boxAt = new Point((VW - w + 1) / 2,
                (r.y != null) ? r.y : (VH - h + 1) / 2);
            picAt = null;
        } else if (who.kind == Portrait.Kind.CHIEF) {
            final int cx = Math.min(246, 317 - sprite.getWidth());
            final int cy = (197 - sprite.getHeight()) / 2;
            boxAt = new Point(Math.max(0, cx - 3 - w), (VH - h + 1) / 2);
            picAt = new Point(cx, cy);
            under = true;
        } else if (who.kind == Portrait.Kind.KING) {
            boxAt = new Point(VW - w, (VH - h + 1) / 2);
            picAt = new Point(0, (197 - sprite.getHeight()) / 2);
            under = true;
        } else {
            final Point off = who.offset(w, sprite.getWidth());
            final Point[] at = ClassicFirstScene.place(w, h, sprite.getWidth(),
                sprite.getHeight(), off.x, off.y);
            boxAt = at[0];
            picAt = at[1];
        }
        final Rectangle box = new Rectangle(boxAt.x, boxAt.y, w, h);
        List<ClassicTextLayout.Line> prompt = lines(r, tiny, width,
            ClassicMenuBox.promptX(box), ClassicMenuBox.promptTop(box, 0));
        if (cut) {
            prompt = new ArrayList<>(prompt.subList(0, p));
            if (p > 0) {
                final ClassicTextLayout.Line last = prompt.get(p - 1);
                prompt.set(p - 1, new ClassicTextLayout.Line(last.x, last.y,
                    last.marked + " ..."));
            }
        }
        final List<ClassicTextLayout.Line> rowLines = new ArrayList<>(rows);
        final int max = ClassicMenuBox.rowTextMaxWidth(box);
        for (int i = 0; i < rows; i++) {
            String s = r.rows.get(i);
            if (tiny.markedWidth(s) > max) s = tiny.fit(plain(s), max);
            rowLines.add(new ClassicTextLayout.Line(ClassicMenuBox.rowX(box),
                ClassicMenuBox.rowTop(box, p, i), s));
        }
        return new Layout(r, box, prompt, rowLines, sprite, picAt, under);
    }

    /** The prompt laid out at a width, its first line at (left, top). */
    private static List<ClassicTextLayout.Line> lines(Request r, ClassicFont tiny,
                                                      int width, int left,
                                                      int top) {
        final List<ClassicTextLayout.Line> out = new ArrayList<>();
        for (List<String> para : r.paragraphs) {
            final int y = top + ClassicMenuBox.PROMPT_PITCH * out.size();
            out.addAll(ClassicTextLayout.layout(tiny, para, width, left, y,
                ClassicMenuBox.PROMPT_PITCH, ClassicTextLayout.BOX));
        }
        return out;
    }


    // Painting

    /**
     * Paint a box: a chief or the King, the frame, the bar, the prompt, the
     * rows, then an advisor over it all.
     *
     * @param g The graphics, in 320x200 pixels.
     * @param l The layout.
     * @param bar The barred row, or -1 for none.
     * @param wood {@code WOODTILE.SS.000}, or null (flat colour).
     * @param tiny FONTTINY.
     */
    static void paint(Graphics2D g, Layout l, int bar, BufferedImage wood,
                      ClassicFont tiny) {
        if (l.under && l.portrait != null) {
            g.drawImage(l.portrait, l.portraitAt.x, l.portraitAt.y, null);
        }
        final ClassicMenuBox.Theme t = ClassicMenuBox.GAME;
        ClassicMenuBox.paintDialogFrame(g, l.box, t, wood);
        if (bar >= 0 && bar < l.rows.size()) {
            final Rectangle b = l.barRect(bar);
            g.setColor(t.dark);
            g.fillRect(b.x, b.y, b.width, b.height);
        }
        for (ClassicTextLayout.Line line : l.prompt) {
            ClassicMenuBox.text(g, tiny, line.marked, line.x, line.y, t, true);
        }
        for (int i = 0; i < l.rows.size(); i++) {
            final ClassicTextLayout.Line line = l.rows.get(i);
            if (l.request.enabled(i)) {
                ClassicMenuBox.text(g, tiny, line.marked, line.x, line.y, t, true);
            } else {
                final int[] grey = ClassicFont.colours(DISABLED);
                tiny.draw(g, plain(line.marked), line.x, line.y, grey);
            }
        }
        if (!l.under && l.portrait != null) {
            g.drawImage(l.portrait, l.portraitAt.x, l.portraitAt.y, null);
        }
    }

    /**
     * A box as a 320x200 ARGB picture, transparent where the box and its
     * portrait are not: what {@link ClassicAdvisorLayer} lays over the map.
     *
     * @param l The layout.
     * @param bar The barred row, or -1.
     * @param wood {@code WOODTILE.SS.000}, or null.
     * @param tiny FONTTINY.
     * @return The picture.
     */
    static BufferedImage render(Layout l, int bar, BufferedImage wood,
                                ClassicFont tiny) {
        final BufferedImage img = new BufferedImage(VW, VH,
            BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g = img.createGraphics();
        try {
            paint(g, l, bar, wood, tiny);
        } finally {
            g.dispose();
        }
        return img;
    }


    // The portrait's palette

    /**
     * The original palette entries a portrait brings (landfall 05 section
     * 2.4: 152-223 and 251-255, loaded 2-8 frames before its box, only when
     * the advisor changes): entry {@code i} is the colour of the portrait's
     * pixels of index {@code i}, or -1 where it has none (and every entry
     * outside the slot).
     *
     * @param sheet The portrait's index sheet ({@code MSS0.SS}), or null.
     * @param frame The frame in it.
     * @param sprite The portrait's sprite (the same frame, ARGB), or null.
     * @return 256 entries 0xRRGGBB or -1, or null without the sheet or sprite.
     */
    static int[] portraitPalette(ClassicIndexSheet sheet, int frame,
                                 BufferedImage sprite) {
        if (sheet == null || sprite == null || frame < 0 || frame >= sheet.size()
            || sheet.width(frame) != sprite.getWidth()
            || sheet.height(frame) != sprite.getHeight()) return null;
        final int[] out = new int[256];
        Arrays.fill(out, -1);
        boolean any = false;
        for (int y = 0; y < sprite.getHeight(); y++) {
            for (int x = 0; x < sprite.getWidth(); x++) {
                final int i = sheet.index(frame, x, y);
                if (!inPortraitSlot(i)) continue;
                final int argb = sprite.getRGB(x, y);
                if ((argb >>> 24) == 0) continue;
                out[i] = argb & 0xFFFFFF;
                any = true;
            }
        }
        return any ? out : null;
    }

    /** @return Whether index {@code i} is a portrait's palette slot entry. */
    static boolean inPortraitSlot(int i) {
        return (i >= 152 && i <= 223)
            || (i >= 251 && i <= 255 && i != ClassicIndexSheet.TRANSPARENT);
    }


    // The bar

    /**
     * The selection bar of one open box: where it is, and what a key or the
     * mouse answers (class comment).  Every input method returns
     * {@link #OPEN} while the box stays, else the answer: a row, or
     * {@link #DISMISSED}; a notice answers 0.  EDT only.
     */
    static final class Bar {

        /** The box stays open. */
        static final int OPEN = Integer.MIN_VALUE;

        /** The box closed with no row (Escape without a cancel row). */
        static final int DISMISSED = -1;

        /** What the last press was on: a row (>= 0), or one of these. */
        private static final int NO_PRESS = -1, PRESS_TEXT = -2,
            PRESS_OUTSIDE = -3;

        private final Request request;

        /** The barred row, -1 for none (a notice, or after a press outside). */
        private int row;

        /** What the press being held is on. */
        private int pressed = NO_PRESS;

        /**
         * @param request The box's request.
         */
        Bar(Request request) {
            this.request = request;
            this.row = request.defaultRow;
        }

        /** @return The barred row, or -1. */
        int row() {
            return this.row;
        }

        /** @return Up one row (none past the first); a notice is dismissed. */
        int up() {
            if (this.request.isNotice()) return 0;
            if (this.row < 0) {
                this.row = this.request.defaultRow;
            } else if (this.row > 0) {
                this.row--;
            }
            return OPEN;
        }

        /** @return Down one row (none past the last); a notice is dismissed. */
        int down() {
            if (this.request.isNotice()) return 0;
            if (this.row < 0) {
                this.row = this.request.defaultRow;
            } else if (this.row < this.request.rows.size() - 1) {
                this.row++;
            }
            return OPEN;
        }

        /** @return Enter: the barred row if it can be taken; a notice's 0. */
        int enter() {
            if (this.request.isNotice()) return 0;
            return this.request.enabled(this.row) ? this.row : OPEN;
        }

        /** @return Escape: the cancel row ({@link Request#escapeAnswer}). */
        int escape() {
            return this.request.escapeAnswer();
        }

        /** @return Any other key: dismisses a notice, nothing in a question. */
        int otherKey() {
            return this.request.isNotice() ? 0 : OPEN;
        }

        /**
         * A mouse press: on a row it puts the bar there; outside the box it
         * removes the bar.
         *
         * @param hitRow The row under the press, or -1.
         * @param inBox Whether the press is on the box.
         */
        void press(int hitRow, boolean inBox) {
            if (hitRow >= 0) {
                this.row = hitRow;
                this.pressed = hitRow;
            } else if (!inBox) {
                if (!this.request.isNotice()) this.row = -1;
                this.pressed = PRESS_OUTSIDE;
            } else {
                this.pressed = PRESS_TEXT;
            }
        }

        /**
         * The release of a press: on the row it was pressed on it takes it;
         * after a press outside the box, outside it, it closes as Escape; a
         * notice closes on any release after a press.
         *
         * @param hitRow The row under the release, or -1.
         * @param inBox Whether the release is on the box.
         * @return The answer, or {@link #OPEN}.
         */
        int release(int hitRow, boolean inBox) {
            final int p = this.pressed;
            this.pressed = NO_PRESS;
            if (p == NO_PRESS) return OPEN;
            if (this.request.isNotice()) return 0;
            if (p >= 0 && hitRow == p && this.request.enabled(p)) return p;
            if (p == PRESS_OUTSIDE && !inBox) return escape();
            return OPEN;
        }
    }
}
