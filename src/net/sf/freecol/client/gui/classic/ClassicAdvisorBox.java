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
 *       row (Roger's rule), except where one must choose: the King's
 *       decision boxes ignore Escape ({@link Request#escapes}, G1), as
 *       the father and recruit boxes do.  The mouse never moves it by
 *       hovering: a press on a row puts it there, the release on that row
 *       takes it; a press outside the box removes it and the release
 *       closes the box as Escape (clip004, the options box).  The portrait
 *       counts as the box (I).  A box whose refusal costs dearly (the
 *       King's, a first contact, a native demand:
 *       {@link Request#outsideCancels} off, I) ignores a click outside: the
 *       notices teach "click anywhere to go on", and such a click must not
 *       answer it.  A box without rows is a notice: any key, or a click,
 *       dismisses it.</li>
 *   <li><b>Checkbox boxes (V, landing-slow 01-options).</b>  GAME.TXT's
 *       {@code @checkbox} messages (the two option boxes,
 *       {@link ClassicOptionBoxes}) put FONTTINY {@code ]} (on) or
 *       {@code [} (off) in gold before each row's text, a 2-px space
 *       between; the bar starts on row 1 at every open.  A row does not
 *       close the box: the release of a press on it flips it
 *       ({@link Request#toggles} acts at once), and only the 3x3 inside of
 *       its bullet changes; Enter and Space flip the barred row and the
 *       row's gold letter flips its row (I, never seen); Escape, or a
 *       press and release outside the box, closes it.</li>
 *   <li><b>List boxes (V, clip008; build spec D2).</b>  The father,
 *       recruit and job lists put their rows at x + 13
 *       ({@link Request#rowIndent}); a list's gold footer "(F1 für Hilfe)"
 *       ({@link Request#footer}) sits flush right at {@code y + H - 9} and
 *       adds 6 px; a right column ({@link Request#right}) ends as far from
 *       the right edge as the rows start from the left; the current entry
 *       is yellow, an unavailable one grey, each with its cell; F1 tells
 *       the caller's hook the barred row and closes the box with
 *       {@link Bar#HELP} ({@link Request#help}).  0 px on the father boxes,
 *       the build menu and both job menus.  A box may ignore Escape
 *       ({@link Request#escapes}), come a set time after the box before
 *       ({@link Request#chainMs}), or be a full-screen page
 *       ({@link Request#picture}: the Colonopedia page).</li>
 *   <li><b>Name boxes (V, landfall #2664; clip008 #2789; W10, D4).</b>
 *       @LANDHO and @COLONY: GAME.TXT's option row is the label of a name
 *       field ({@link Field}) with the default name selected; no rows, no
 *       bar; typed characters edit it, Enter takes it, Escape keeps the
 *       default.  The @HOWMUCH boxes' "Menge:" is an amount field
 *       ({@link Field#amount}, V playthrough-1 #54344, gap list B1).</li>
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


    /**
     * What F1 does in a list box ({@link Request#help}, build spec D2): the
     * box closes with {@link Bar#HELP}, and its caller shows the help of
     * the barred row and may ask the box again.
     */
    interface Help {

        /**
         * F1 was pressed with the bar on a row.  EDT only; the box is still
         * up, so this only remembers the row.
         *
         * @param row The barred row (0-based).
         */
        void help(int row);
    }

    /** What a checkbox box's row does when it flips ({@link Request#toggles}). */
    interface Toggles {

        /**
         * A row was flipped: apply it now (the original's options act in
         * the same turn).  EDT only.
         *
         * @param row The row (0-based).
         * @param on Its new state.
         */
        void toggled(int row, boolean on);
    }

    /**
     * A box's name field ({@link Request#field}, build spec W10, D4): the
     * original's @LANDHO and @COLONY prompts.  It holds the name the box
     * was answered with ({@link #answer}), written by the bar that closed
     * the box ({@link Bar#enter}, {@link Bar#escape}), so every prompter
     * (the canvas, the stopgap, a test's keys) answers it the same way.
     *
     * <p>Measured (V): a 1-px rectangle in the ink (index 68), 144 x 10 at
     * box + (29, 12 + 6P) (landfall #2664: x 93-236, y 138-147 in the
     * box (64,114,196,43); clip008 #2789: x 100-243, y 131-140); the
     * text at field + (3, 3) in the ink; the default name on an index-138
     * block from field + 2, 6 rows tall, so it is selected; no caret, in
     * 7.4 s nothing changes (#2665-#3184).  The label (GAME.TXT's option
     * row, "Name:") at box + 5, glyph top field + 3.  The first key
     * replaces the selected name (V clip008 #3134 "B", then "Ba", "Bas",
     * "Base"); Backspace deletes one character and drops the selection
     * (V playthrough-1 #54434: @HOWMUCH4's selected "100" -&gt; "10", gap
     * list Q7); the length limits are I.
     *
     * <p>The <b>amount field</b> ({@link #amount}, gap list B1) of the
     * @HOWMUCH boxes, "Menge:": V playthrough-1 #54344, @HOWMUCH4's
     * (75,103,36,10) in the box (42,79,236,43), so box + (33, 24), 36 wide,
     * the same ink, block and text offset; its preset selected, the first
     * key replaces it.  Only digits, never more than its "(0-max)" (I).
     */
    static final class Field {

        /** The name field's size (V: 144 x 10 in both clips). */
        static final int W = 144, H = 10;

        /** The name field's left edge from the box's (V: + 29 in both clips). */
        static final int X = 29;

        /** The amount field's left edge from the box's and its width (V: #54344). */
        static final int AMOUNT_X = 33, AMOUNT_W = 36;

        /** The text's offset in the field (V: + 3, + 3). */
        static final int TEXT_DX = 3, TEXT_DY = 3;

        /**
         * The longest name (I: COLONY.TXT's names are at most 23
         * characters, SRC); the text also stays inside the field.
         */
        static final int MAX_CHARS = 23;

        /** The name the field opens with, selected. */
        final String initial;

        /** The field's left edge from the box's and its width. */
        final int x, w;

        /** An amount field's largest number ("(0-max)"), or -1 for a name field. */
        final int max;

        /** The name taken (Enter), or null: then {@link #initial}. */
        private volatile String entered = null;

        /** Whether Enter closed the box (else Escape, or not answered). */
        private volatile boolean typed = false;

        /**
         * @param initial The default name, selected when the box opens.
         */
        Field(String initial) {
            this(initial, X, W, -1);
        }

        private Field(String initial, int x, int w, int max) {
            this.initial = (initial == null) ? "" : initial;
            this.x = x;
            this.w = w;
            this.max = max;
        }

        /**
         * An amount field ("Menge:", the @HOWMUCH boxes).
         *
         * @param max The largest amount, the box's "(0-max)".
         * @param preset The amount it opens with, selected (clamped to
         *     0..max).
         * @return The field.
         */
        static Field amount(int max, int preset) {
            final int m = Math.max(0, max);
            return new Field(Integer.toString(Math.max(0, Math.min(m, preset))),
                             AMOUNT_X, AMOUNT_W, m);
        }

        /** @return Whether it is an amount field ({@link #amount}). */
        boolean isAmount() {
            return this.max >= 0;
        }

        /**
         * @return An amount field's answer: the number Enter took (an
         *     empty field: 0), or -1 for Escape, no answer yet, or a name
         *     field.
         */
        int amount() {
            if (!isAmount() || !this.typed) return -1;
            final String e = this.entered;
            final String s = (e == null) ? "" : e.trim();
            if (s.isEmpty()) return 0;
            try {
                return Math.min(this.max, Integer.parseInt(s));
            } catch (NumberFormatException ex) {
                return 0;
            }
        }

        /**
         * Whether a field may hold this text after a key: a name of
         * {@link #typable} characters, at most {@link #MAX_CHARS}; an
         * amount of digits no larger than {@link #max} (I).  The width is
         * checked by the caller.
         *
         * @param next The text.
         * @return True if it may.
         */
        boolean accepts(String next) {
            if (!isAmount()) return next.length() <= MAX_CHARS;
            if (next.isEmpty() || next.length() > Integer.toString(this.max).length()) {
                return next.isEmpty();
            }
            for (int i = 0; i < next.length(); i++) {
                if (next.charAt(i) < '0' || next.charAt(i) > '9') return false;
            }
            return Integer.parseInt(next) <= this.max;
        }

        /**
         * @param c A typed character.
         * @return Whether it can go into this field at all.
         */
        boolean typableHere(char c) {
            return (isAmount()) ? (c >= '0' && c <= '9') : typable(c);
        }

        /**
         * @return The name the box was answered with: the text taken by
         *     Enter, trimmed; the default for an empty text, for Escape
         *     (Roger: Escape keeps the default name) and before an answer.
         */
        String answer() {
            final String e = this.entered;
            final String s = (e == null) ? "" : e.trim();
            return (s.isEmpty()) ? this.initial : s;
        }

        /** @return Whether Enter answered it (false: Escape, or none yet). */
        boolean taken() {
            return this.typed;
        }

        /** Enter: the text in the field. */
        void take(String text) {
            this.entered = text;
            this.typed = true;
        }

        /** Escape: the default name. */
        void keepDefault() {
            this.entered = null;
            this.typed = false;
        }

        /**
         * Whether a typed character goes into a name (I): letters (the
         * umlauts and ß too), digits, the space and {@code - . '}.
         *
         * @param c The character.
         * @return True if it can be typed.
         */
        static boolean typable(char c) {
            if (c == ' ' || c == '-' || c == '.' || c == '\'') return true;
            if (!Character.isLetterOrDigit(c)) return false;
            // A letter the font cannot draw (it would show '?') is refused.
            return ClassicFont.toCode(c) != '?';
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

        /** GAME.TXT's {@code @x}, or null to centre: set only for the tips (@TUTORIAL4, W11). */
        final Integer x;

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

        /**
         * Whether a click outside the box (and its portrait) answers it as
         * Escape, as the options box does (clip004); off where Escape's
         * answer cannot be undone (the King, first contact, a demand).
         */
        final boolean outsideCancels;

        /**
         * A checkbox box's rows' states when it opens, or null for a box
         * whose rows answer it (every box but GAME.TXT's {@code @checkbox}
         * ones).
         */
        final boolean[] checks;

        /** What a flipped row does, or null (checkbox boxes only). */
        final Toggles toggles;

        /**
         * How long after it is asked for the box comes at the earliest (ms):
         * 0, or the option boxes' lead after their menu row (the clip: 2-4
         * frames after the menu goes).
         */
        final double openDelayMs;

        /**
         * When the box should be on screen, on the box layer's clock, or 0
         * for no such time: the landing box comes the original's time after
         * the move key (build spec W8b); a portrait palette goes in
         * {@code ClassicAdvisorLayer.PALETTE_LEAD_MS} before it, or as much
         * of that as is left.
         */
        final long showAtNanos;

        /**
         * The rows' indent from the box's left edge:
         * {@link ClassicMenuBox#ROW_INDENT}, or the list boxes'
         * {@link ClassicMenuBox#LIST_INDENT} (D2).
         */
        final int rowIndent;

        /**
         * The gold footer line, e.g. "(F1 für Hilfe)" (LABELS @MISC 188),
         * or null; it adds {@link ClassicMenuBox#FOOTER_HEIGHT} (D2).
         */
        final String footer;

        /** Per row the right column's text, or null (none in that row); null for none at all. */
        final List<String> right;

        /**
         * Where the right column's text ends: this far left of the box's
         * right edge (by default the rows' indent).
         */
        final int rightInset;

        /** Per row, whether it is the "current" entry, drawn yellow (D2). */
        final boolean[] current;

        /** What F1 does, or null: F1 is then any other key (D2). */
        final Help help;

        /**
         * Whether Escape answers the box; else it does nothing (the father
         * box, D8a; the King's decisions and the recruit box, G1).
         */
        final boolean escapes;

        /**
         * A full-screen page (320x200, opaque) instead of a box: the
         * Colonopedia page of D8b.  It is a notice: any key or click
         * closes it.  Null for a box.
         */
        final BufferedImage picture;

        /**
         * The least time after the previous box's close (ms), or a negative
         * value for {@link ClassicAdvisorLayer#CHAIN_MS}: the father box's
         * F1 page comes 0.13-0.16 s after the box goes, the box again
         * 0.26-0.27 s after the page (clip008).
         */
        final double chainMs;

        /** The stopgap's window title, when the box cannot be drawn. */
        final String title;

        /** The stopgap's illustration, or null. */
        final Image icon;

        /** Whether the stopgap is the selection list (a choice). */
        final boolean list;

        /**
         * The name field ({@link Field}), or null.  A box with a field has
         * no rows: GAME.TXT's option row is its label ({@link #fieldLabel});
         * Enter takes the name, Escape keeps the default.
         */
        final Field field;

        /** The field's label ("Name:"), markup kept; "" without a field. */
        final String fieldLabel;

        private Request(Builder b) {
            this.id = b.id;
            final List<List<String>> ps = new ArrayList<>();
            for (List<String> p : b.paragraphs) {
                ps.add(Collections.unmodifiableList(new ArrayList<>(p)));
            }
            this.paragraphs = Collections.unmodifiableList(ps);
            this.width = b.width;
            this.x = b.x;
            this.y = b.y;
            this.rows = Collections.unmodifiableList(new ArrayList<>(b.rows));
            this.disabled = Arrays.copyOf(b.disabled, this.rows.size());
            final int n = this.rows.size();
            this.defaultRow = (n == 0) ? -1 : ClassicHud.clamp(b.defaultRow, 0, n - 1);
            this.cancelRow = (b.cancelRow < 0 || b.cancelRow >= n) ? -1 : b.cancelRow;
            this.portrait = (b.portrait == null) ? Portrait.NONE : b.portrait;
            this.outsideCancels = b.outsideCancels;
            this.checks = (b.checks == null || n == 0) ? null
                : Arrays.copyOf(b.checks, n);
            this.toggles = (this.checks == null) ? null : b.toggles;
            this.openDelayMs = Math.max(0.0, b.openDelayMs);
            this.showAtNanos = b.showAtNanos;
            this.rowIndent = b.rowIndent;
            this.footer = (b.footer == null || b.footer.isEmpty()) ? null : b.footer;
            if (b.right == null) {
                this.right = null;
            } else {
                final List<String> rs = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    rs.add((i < b.right.size()) ? b.right.get(i) : null);
                }
                this.right = Collections.unmodifiableList(rs);
            }
            this.rightInset = (b.rightInset < 0) ? b.rowIndent : b.rightInset;
            this.current = Arrays.copyOf(b.current, n);
            this.help = b.help;
            this.escapes = b.escapes;
            this.picture = b.picture;
            this.chainMs = b.chainMs;
            this.title = (b.title == null) ? "" : b.title;
            this.icon = b.icon;
            this.list = b.list;
            this.field = b.field;
            this.fieldLabel = (b.fieldLabel == null) ? "" : b.fieldLabel;
        }

        /** @return Whether it has a name field ({@link #field}). */
        boolean hasField() {
            return this.field != null;
        }

        /** @return Whether row {@code i} is the yellow "current" entry. */
        boolean isCurrent(int i) {
            return i >= 0 && i < this.current.length && this.current[i];
        }

        /** @return Row {@code i}'s right column text, or null. */
        String rightOf(int i) {
            return (this.right == null || i < 0 || i >= this.right.size()) ? null
                : this.right.get(i);
        }

        /** @return Whether it is a notice (no rows, no name field). */
        boolean isNotice() {
            return this.rows.isEmpty() && this.field == null;
        }

        /** @return Whether its rows are checkboxes (GAME.TXT {@code @checkbox}). */
        boolean isCheckbox() {
            return this.checks != null;
        }

        /** @return Whether row {@code i} can be taken. */
        boolean enabled(int i) {
            return i >= 0 && i < this.rows.size() && !this.disabled[i];
        }

        /**
         * @return What Escape answers: the cancel row, or
         *     {@link Bar#DISMISSED}; a notice's 0; {@link Bar#OPEN} where
         *     Escape does nothing ({@link #escapes} off).
         */
        int escapeAnswer() {
            if (isNotice()) return 0;
            if (!this.escapes) return Bar.OPEN;
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
                + " esc=" + this.cancelRow + " portrait=" + this.portrait
                + (this.outsideCancels ? "" : " outside=stays")
                + (this.escapes ? "" : " esc=stays")
                + (isCheckbox() ? " checks=" + checkString(this.checks) : "")
                + (hasField() ? " field=" + this.field.initial : "");
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
        private Integer x = null;
        private Integer y = null;
        private final List<String> rows = new ArrayList<>();
        private boolean[] disabled = new boolean[0];
        private int defaultRow = 0;
        private int cancelRow = Integer.MIN_VALUE;
        private Portrait portrait = Portrait.NONE;
        private boolean outsideCancels = true;
        private boolean[] checks = null;
        private Toggles toggles = null;
        private double openDelayMs = 0.0;
        private long showAtNanos = 0L;
        private int rowIndent = ClassicMenuBox.ROW_INDENT;
        private String footer = null;
        private List<String> right = null;
        private int rightInset = -1;
        private boolean[] current = new boolean[0];
        private Help help = null;
        private boolean escapes = true;
        private BufferedImage picture = null;
        private double chainMs = -1.0;
        private String title = null;
        private Image icon = null;
        private boolean list = false;
        private Field field = null;
        private String fieldLabel = null;

        private Builder(String id) {
            this.id = (id == null) ? "box" : id;
        }

        /**
         * @param indent The rows' indent from the box's left edge
         *     ({@link ClassicMenuBox#LIST_INDENT} for the list boxes).
         */
        Builder rowIndent(int indent) {
            this.rowIndent = indent;
            return this;
        }

        /** @param text The gold footer line ("(F1 für Hilfe)"), or null. */
        Builder footer(String text) {
            this.footer = text;
            return this;
        }

        /**
         * A right column: per row its text (null for none), right-aligned
         * as far from the box's right edge as the rows are from its left
         * (V, 0 px: the build menu's costs end 9 px from the edge, its rows
         * at + 9, clip008 #11142; the job menus' goods 13 px, their rows at
         * + 13, #42043 and #42539).
         *
         * @param cells The cells, in row order.
         */
        Builder right(List<String> cells) {
            return right(cells, -1);
        }

        /**
         * A right column ending {@code inset} px left of the box's right
         * edge (a negative inset: the rows' indent, {@link #right(List)}).
         *
         * @param cells The cells, in row order.
         * @param inset The distance of their advance's end from the box's
         *     right edge.
         */
        Builder right(List<String> cells, int inset) {
            this.right = (cells == null) ? null : new ArrayList<>(cells);
            this.rightInset = inset;
            return this;
        }

        /** Per row, whether it is the yellow "current" entry. */
        Builder current(boolean[] c) {
            this.current = (c == null) ? new boolean[0] : c.clone();
            return this;
        }

        /** What F1 does with the barred row (the box closes with {@link Bar#HELP}). */
        Builder help(Help h) {
            this.help = h;
            return this;
        }

        /** Escape does nothing (the father box, D8a; the King's decisions, G1). */
        Builder noEscape() {
            this.escapes = false;
            return this;
        }

        /**
         * A full-screen page instead of a box (D8b): a notice that shows
         * the picture.
         *
         * @param page The 320x200 picture.
         */
        Builder picture(BufferedImage page) {
            this.picture = page;
            return this;
        }

        /** @param ms The least time after the previous box's close. */
        Builder chain(double ms) {
            this.chainMs = ms;
            return this;
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

        /**
         * @param xx GAME.TXT's {@code @x}, or null: the box's left edge, an
         *     advisor stays over the centred box (clip008 #4068: @TUTORIAL4 at x 10).
         *     Only the tips set it; other boxes ignore GAME.TXT's @x (V:
         *     @TUTORIAL1's @x=10 is not used).
         */
        Builder x(Integer xx) {
            this.x = xx;
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

        /**
         * Whether a click outside answers the box as Escape (the default,
         * the options box's rule), or does nothing.
         */
        Builder outsideCancels(boolean c) {
            this.outsideCancels = c;
            return this;
        }

        /**
         * A checkbox box: its rows' states when it opens (one per row), and
         * what a flip does.  Escape then closes it with no row, unless a
         * cancel row is given.
         *
         * @param states The states.
         * @param t What a flipped row does, or null.
         */
        Builder checks(boolean[] states, Toggles t) {
            this.checks = (states == null) ? null : states.clone();
            this.toggles = t;
            return this;
        }

        /** @param ms The box comes no earlier than this after it is asked for. */
        Builder openDelay(double ms) {
            this.openDelayMs = ms;
            return this;
        }

        /** @param nanos When the box should be on screen (the layer's clock), 0 for any time. */
        Builder showAt(long nanos) {
            this.showAtNanos = nanos;
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

        /**
         * A name field ({@link Field}) with its default name selected: the
         * rows given so far become its label (GAME.TXT's "Name:"), and the
         * box has no rows and no bar.  Escape keeps the default; a click
         * outside does nothing (I).
         *
         * @param initial The default name.
         */
        Builder field(String initial) {
            this.field = new Field(initial);
            this.outsideCancels = false;
            return this;
        }

        /**
         * An amount field ({@link Field#amount}, the @HOWMUCH boxes) with
         * its preset selected, as {@link #field}.
         *
         * @param max The largest amount ("(0-max)").
         * @param preset The amount it opens with.
         */
        Builder amountField(int max, int preset) {
            this.field = Field.amount(max, preset);
            this.outsideCancels = false;
            return this;
        }

        /** @return The request. */
        Request build() {
            if (this.field != null) {
                // GAME.TXT's option row is the field's label.
                this.fieldLabel = (this.rows.isEmpty()) ? "" : this.rows.get(0);
                this.rows.clear();
                this.cancelRow = -1;
            }
            if (this.cancelRow == Integer.MIN_VALUE) {
                this.cancelRow = (this.checks != null) ? -1 : this.rows.size() - 1;
            }
            if (this.disabled.length < this.rows.size()) {
                this.disabled = Arrays.copyOf(this.disabled, this.rows.size());
            }
            if (this.picture != null) {
                this.rows.clear();   // a page is a notice
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

        /** Per row its right column (screen coordinates), or null entries; never null. */
        final List<ClassicTextLayout.Line> right;

        /** The footer line (screen coordinates), or null. */
        final ClassicTextLayout.Line footer;

        /** The name field's rectangle (its 1-px frame's outer bounds), or null. */
        final Rectangle field;

        /** The field's label line ("Name:"), or null. */
        final ClassicTextLayout.Line fieldLabel;

        Layout(Request request, Rectangle box, List<ClassicTextLayout.Line> prompt,
               List<ClassicTextLayout.Line> rows, BufferedImage portrait,
               Point portraitAt, boolean under) {
            this(request, box, prompt, rows, portrait, portraitAt, under,
                 Collections.<ClassicTextLayout.Line>emptyList(), null);
        }

        Layout(Request request, Rectangle box, List<ClassicTextLayout.Line> prompt,
               List<ClassicTextLayout.Line> rows, BufferedImage portrait,
               Point portraitAt, boolean under,
               List<ClassicTextLayout.Line> right, ClassicTextLayout.Line footer) {
            this(request, box, prompt, rows, portrait, portraitAt, under, right,
                 footer, null, null);
        }

        Layout(Request request, Rectangle box, List<ClassicTextLayout.Line> prompt,
               List<ClassicTextLayout.Line> rows, BufferedImage portrait,
               Point portraitAt, boolean under,
               List<ClassicTextLayout.Line> right, ClassicTextLayout.Line footer,
               Rectangle field, ClassicTextLayout.Line fieldLabel) {
            this.field = field;
            this.fieldLabel = fieldLabel;
            this.request = request;
            this.box = box;
            this.prompt = Collections.unmodifiableList(new ArrayList<>(prompt));
            this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
            this.portrait = portrait;
            this.portraitAt = portraitAt;
            this.under = under;
            this.right = Collections.unmodifiableList(new ArrayList<>(right));
            this.footer = footer;
        }

        /** @return Row {@code i}'s right column, or null. */
        ClassicTextLayout.Line rightOf(int i) {
            return (i >= 0 && i < this.right.size()) ? this.right.get(i) : null;
        }

        /** @return The prompt's line count P. */
        int promptLines() {
            return this.prompt.size();
        }

        /** @return Where the field's text starts (x, glyph top), or null without a field. */
        Point fieldText() {
            return (this.field == null) ? null
                : new Point(this.field.x + Field.TEXT_DX, this.field.y + Field.TEXT_DY);
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

        /**
         * Whether a 320x200 point is on the box or on its portrait's own
         * rectangle (a click on the admiral, the chief or the King is not
         * outside; I).  Not {@link #bounds}: the King's union with his box
         * is nearly the whole screen.
         *
         * @param vx The x.
         * @param vy The y.
         * @return True if the point counts as the box.
         */
        boolean inBox(int vx, int vy) {
            if (this.box.contains(vx, vy)) return true;
            return this.portrait != null && this.portraitAt != null
                && new Rectangle(this.portraitAt.x, this.portraitAt.y,
                                 this.portrait.getWidth(),
                                 this.portrait.getHeight()).contains(vx, vy);
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

    /** FONTTINY's bullets of a checkbox row: a dot (on), a ring (off). */
    static final char CHECK_ON = ']', CHECK_OFF = '[';

    /**
     * A checkbox row as drawn: the bullet in gold, a space (2 px), the row
     * (landing-slow 01-options section 2.3: the text at box + 17).
     *
     * @param marked The row, markup kept.
     * @param on Its state.
     * @return The marked text.
     */
    static String checkRow(String marked, boolean on) {
        return "{" + (on ? CHECK_ON : CHECK_OFF) + "} " + ((marked == null) ? "" : marked);
    }

    /**
     * Checkbox states as the analysis writes them, X on and o off
     * ({@code XXooXXXX} is state A).
     *
     * @param checks The states, or null.
     * @return The letters, or "" for none.
     */
    static String checkString(boolean[] checks) {
        if (checks == null) return "";
        final StringBuilder sb = new StringBuilder(checks.length);
        for (boolean c : checks) sb.append(c ? 'X' : 'o');
        return sb.toString();
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
        if (r.picture != null) {
            // A full-screen page (D8b): nothing to lay out.
            return new Layout(r, new Rectangle(0, 0, VW, VH),
                Collections.<ClassicTextLayout.Line>emptyList(),
                Collections.<ClassicTextLayout.Line>emptyList(), null, null, false);
        }
        final int rows = r.rows.size();
        final int foot = ((r.footer == null) ? 0 : ClassicMenuBox.FOOTER_HEIGHT)
            + (r.hasField() ? FIELD_HEIGHT : 0);
        int width = Math.max(10, r.width);
        List<ClassicTextLayout.Line> probe = lines(r, tiny, width, 0, 0);
        // A FreeCol text too long for the screen gets the widest box.
        if (ClassicMenuBox.dialogHeight(probe.size(), rows) + foot > VH
            && width < MAX_WIDTH) {
            width = MAX_WIDTH;
            probe = lines(r, tiny, width, 0, 0);
        }
        int p = probe.size();
        final int room = (VH - ClassicMenuBox.dialogHeight(0, rows) - foot)
            / ClassicMenuBox.PROMPT_PITCH;
        if (room < 0) return null;
        final boolean cut = p > room;
        if (cut) p = room;
        final int w = width + 6, h = ClassicMenuBox.dialogHeight(p, rows) + foot;
        final Portrait who = r.portrait;
        final BufferedImage sprite = (who.sprite == null) ? null : portrait;
        final Point boxAt, picAt;
        boolean under = false;
        if (sprite == null) {
            boxAt = new Point((r.x != null) ? r.x : (VW - w + 1) / 2,
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
            // A tip's @x moves the box only; the advisor stays where the
            // centred box would have him (V: clip008 #4068, box x 10,
            // the colonist at x 130 = centred over a box at x 42).
            boxAt = new Point((r.x == null) ? at[0].x : r.x, at[0].y);
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
        final List<ClassicTextLayout.Line> rightLines = new ArrayList<>(rows);
        final int x = box.x + r.rowIndent;
        final int max = box.width - r.rowIndent - 5;   // rowTextMaxWidth at x + 9
        final int bullet = r.isCheckbox()
            ? tiny.markedWidth(checkRow("", true)) : 0;
        for (int i = 0; i < rows; i++) {
            String s = r.rows.get(i);
            if (tiny.markedWidth(s) > max - bullet) s = tiny.fit(plain(s), max - bullet);
            final int top = ClassicMenuBox.rowTop(box, p, i);
            rowLines.add(new ClassicTextLayout.Line(x, top, s));
            final String cell = r.rightOf(i);
            rightLines.add((cell == null || cell.isEmpty()) ? null
                : new ClassicTextLayout.Line(box.x + box.width - r.rightInset
                    - tiny.markedWidth(cell), top, cell));
        }
        final ClassicTextLayout.Line footer = (r.footer == null) ? null
            : new ClassicTextLayout.Line(ClassicMenuBox.footerX(box,
                tiny.stringWidth(r.footer)), ClassicMenuBox.footerTop(box), r.footer);
        Rectangle field = null;
        ClassicTextLayout.Line label = null;
        if (r.hasField()) {
            // V (landfall #2664, clip008 #2789): the field at box + (29,
            // 12 + 6P), the label at box + 5 on the field's text line; an
            // amount field at box + 33, 36 wide (playthrough-1 #54344).
            field = new Rectangle(box.x + r.field.x,
                box.y + FIELD_TOP + ClassicMenuBox.PROMPT_PITCH * p, r.field.w, Field.H);
            label = new ClassicTextLayout.Line(ClassicMenuBox.promptX(box),
                field.y + Field.TEXT_DY, r.fieldLabel);
        }
        return new Layout(r, box, prompt, rowLines, sprite, picAt, under,
                          rightLines, footer, field, label);
    }

    /**
     * What a name field adds to a box without rows (V: @LANDHO's box is 43
     * high with two prompt lines, {@code 6P + 18 + 13}).
     */
    static final int FIELD_HEIGHT = 13;

    /** The field's top from the box's, before the prompt lines (V: + 24 with P = 2). */
    static final int FIELD_TOP = 12;

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
        paint(g, l, bar, l.request.checks, wood, tiny);
    }

    /**
     * Paint a box with its checkbox rows in given states.
     *
     * @param g The graphics, in 320x200 pixels.
     * @param l The layout.
     * @param bar The barred row, or -1 for none.
     * @param checks The rows' states ({@link Bar#checks}), or null for a
     *     box without checkboxes.
     * @param wood {@code WOODTILE.SS.000}, or null (flat colour).
     * @param tiny FONTTINY.
     */
    static void paint(Graphics2D g, Layout l, int bar, boolean[] checks,
                      BufferedImage wood, ClassicFont tiny) {
        final Field f = l.request.field;
        paint(g, l, bar, checks, (f == null) ? null : f.initial, true, wood, tiny);
    }

    /**
     * Paint a box with its checkbox rows in given states and its name field
     * holding a text.
     *
     * @param g The graphics, in 320x200 pixels.
     * @param l The layout.
     * @param bar The barred row, or -1 for none.
     * @param checks The rows' states ({@link Bar#checks}), or null.
     * @param fieldText The field's text ({@link Bar#fieldText}), or null.
     * @param selected Whether the text is selected (the default name before
     *     the first key: on the dark block).
     * @param wood {@code WOODTILE.SS.000}, or null (flat colour).
     * @param tiny FONTTINY.
     */
    static void paint(Graphics2D g, Layout l, int bar, boolean[] checks,
                      String fieldText, boolean selected,
                      BufferedImage wood, ClassicFont tiny) {
        if (l.request.picture != null) {
            g.drawImage(l.request.picture, 0, 0, null);
            return;
        }
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
        final int[] grey = ClassicFont.colours(DISABLED);
        for (int i = 0; i < l.rows.size(); i++) {
            final ClassicTextLayout.Line line = l.rows.get(i);
            final ClassicTextLayout.Line cell = l.rightOf(i);
            final String s = (checks != null && i < checks.length)
                ? checkRow(line.marked, checks[i]) : line.marked;
            if (!l.request.enabled(i)) {
                // Grey, the whole row (clip008 #42539: Späher, Dragoner).
                tiny.draw(g, plain(s), line.x, line.y, grey);
                if (cell != null) tiny.draw(g, plain(cell.marked), cell.x, cell.y, grey);
            } else if (l.request.isCurrent(i)) {
                // Yellow, the whole row (clip008 #42539 Soldat, #11142).
                tiny.draw(g, plain(s), line.x, line.y, t.highlightColours);
                if (cell != null) {
                    tiny.draw(g, plain(cell.marked), cell.x, cell.y, t.highlightColours);
                }
            } else {
                ClassicMenuBox.text(g, tiny, s, line.x, line.y, t, true);
                if (cell != null) {
                    ClassicMenuBox.text(g, tiny, cell.marked, cell.x, cell.y, t, true);
                }
            }
        }
        if (l.footer != null) {
            tiny.draw(g, l.footer.marked, l.footer.x, l.footer.y, t.highlightColours);
        }
        if (l.field != null) paintField(g, l, fieldText, selected, t, tiny);
        if (!l.under && l.portrait != null) {
            g.drawImage(l.portrait, l.portraitAt.x, l.portraitAt.y, null);
        }
    }

    /**
     * The name field ({@link Field}): its 1-px frame in the ink, the label,
     * the text in the ink, a selected text on the dark block from field + 2,
     * 6 rows tall, one pixel left of the text to the end of its advance
     * (V: "Neuholland" x 95-134, "New Amsterdam" x 102-158).  No caret.
     */
    private static void paintField(Graphics2D g, Layout l, String text,
                                   boolean selected, ClassicMenuBox.Theme t,
                                   ClassicFont tiny) {
        final Rectangle f = l.field;
        g.setColor(t.ink);
        g.drawRect(f.x, f.y, f.width - 1, f.height - 1);
        if (l.fieldLabel != null) {
            ClassicMenuBox.text(g, tiny, l.fieldLabel.marked, l.fieldLabel.x,
                                l.fieldLabel.y, t, true);
        }
        if (text == null || text.isEmpty()) return;
        final Point at = l.fieldText();
        if (selected) {
            g.setColor(t.dark);
            g.fillRect(at.x - 1, f.y + 2, selectionWidth(tiny, text), 6);
        }
        tiny.draw(g, text, at.x, at.y, t.inkColours);
    }

    /**
     * @param tiny FONTTINY.
     * @param text A field's text.
     * @return The width of its selection block: the pixel before the text,
     *     its advance and one more (V: "Neuholland" 40 px at x 95-134, the
     *     text at 96; "New Amsterdam" 57 px at x 102-158, the text at 103).
     */
    static int selectionWidth(ClassicFont tiny, String text) {
        return tiny.stringWidth(text) + 2;
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
        return render(l, bar, l.request.checks, wood, tiny);
    }

    /**
     * A box as a 320x200 ARGB picture, its checkbox rows in given states.
     *
     * @param l The layout.
     * @param bar The barred row, or -1.
     * @param checks The rows' states, or null.
     * @param wood {@code WOODTILE.SS.000}, or null.
     * @param tiny FONTTINY.
     * @return The picture.
     */
    static BufferedImage render(Layout l, int bar, boolean[] checks,
                                BufferedImage wood, ClassicFont tiny) {
        final Field f = l.request.field;
        return render(l, bar, checks, (f == null) ? null : f.initial, true,
                      wood, tiny);
    }

    /**
     * A box as a 320x200 ARGB picture as its bar has it now: the barred
     * row, the checkbox states, the name field's text.
     *
     * @param l The layout.
     * @param b The bar.
     * @param wood {@code WOODTILE.SS.000}, or null.
     * @param tiny FONTTINY.
     * @return The picture.
     */
    static BufferedImage render(Layout l, Bar b, BufferedImage wood,
                                ClassicFont tiny) {
        return render(l, b.row(), b.checks(), b.fieldText(), b.fieldSelected(),
                      wood, tiny);
    }

    private static BufferedImage render(Layout l, int bar, boolean[] checks,
                                        String fieldText, boolean selected,
                                        BufferedImage wood, ClassicFont tiny) {
        final BufferedImage img = new BufferedImage(VW, VH,
            BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g = img.createGraphics();
        try {
            paint(g, l, bar, checks, fieldText, selected, wood, tiny);
        } finally {
            g.dispose();
        }
        return img;
    }


    // The portrait's palette

    /**
     * The original palette entries a portrait brings (landfall 05 section
     * 2.4, loaded 2-8 frames before its box, only when the advisor changes;
     * the slot, {@link #inPortraitSlot}, as W22p measured it): entry
     * {@code i} is the colour of the portrait's pixels of index {@code i},
     * or -1 where it has none (and every entry outside the slot).
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

    /**
     * Whether index {@code i} is a portrait's palette slot entry: 16-119
     * and 144-255 but the transparent index.  Seen loaded where a portrait
     * differs from the game palette: 16, 24, 32, 40, 152-229, 252 and 255
     * (the Sioux chief, clip004 #5089), 103, 152-242, 244-251 and 255 (the
     * King, clip005 #17484, clip006 #7375); seen kept: 5, 12, 13, 120-127
     * (they go on cycling) and 139 (the Sioux's own is black).  0-15 and
     * 128-143 as a whole are inferred: no portrait differs from the game
     * palette there.
     *
     * @param i The palette index.
     * @return True for a slot entry.
     */
    static boolean inPortraitSlot(int i) {
        return i >= 16 && i <= 255 && (i < 120 || i > 143)
            && i != ClassicIndexSheet.TRANSPARENT;
    }


    // The bar

    /**
     * The selection bar of one open box: where it is, and what a key or the
     * mouse answers (class comment).  Every input method returns
     * {@link #OPEN} while the box stays, else the answer: a row, or
     * {@link #DISMISSED}; a notice answers 0.  In a checkbox box
     * ({@link Request#isCheckbox}) a row flips instead of answering, and
     * the box stays open: the bar keeps the rows' states, runs
     * {@link Request#toggles} and remembers the flip for the painter
     * ({@link #takeToggled}).  EDT only.
     */
    static final class Bar {

        /** The box stays open. */
        static final int OPEN = Integer.MIN_VALUE;

        /** The box closed with no row (Escape without a cancel row). */
        static final int DISMISSED = -1;

        /**
         * The box closed on F1 ({@link Request#help}): its caller shows the
         * help of the row the hook was told, and may ask the box again.
         */
        static final int HELP = -2;

        /** What the last press was on: a row (>= 0), or one of these. */
        private static final int NO_PRESS = -1, PRESS_TEXT = -2,
            PRESS_OUTSIDE = -3;

        private final Request request;

        /** The barred row, -1 for none (a notice, or after a press outside). */
        private int row;

        /** What the press being held is on. */
        private int pressed = NO_PRESS;

        /** A checkbox box's rows' states now, or null. */
        private final boolean[] checks;

        /** The row flipped by the last input, or -1. */
        private int toggled = -1;

        /** The name field's text now, or null without a field. */
        private final StringBuilder text;

        /** Whether the field's text is still the selected default. */
        private boolean selected;

        /**
         * @param request The box's request.
         */
        Bar(Request request) {
            this.request = request;
            this.row = request.defaultRow;
            this.checks = (request.checks == null) ? null : request.checks.clone();
            this.text = (request.field == null) ? null
                : new StringBuilder(request.field.initial);
            this.selected = request.field != null;
        }

        /** @return The name field's text now, or null without a field. */
        String fieldText() {
            return (this.text == null) ? null : this.text.toString();
        }

        /** @return Whether the field's text is the selected default name. */
        boolean fieldSelected() {
            return this.text != null && this.selected;
        }

        /**
         * A typed character in a box with a name or amount field: the
         * first replaces the selected default (V clip008 #3134,
         * playthrough-1 #54533), the others are added at the end (no
         * caret).  Refused (I): a character that is not
         * {@link Field#typable} (an amount's: not a digit), a name past
         * {@link Field#MAX_CHARS}, an amount past its "(0-max)"
         * ({@link Field#accepts}), or one that would leave the field
         * ({@code tiny}'s advance wider than the field's inside, when the
         * font is known).
         *
         * @param c The character.
         * @param tiny FONTTINY, or null (then only the count limits).
         * @return Whether the text changed.
         */
        boolean type(char c, ClassicFont tiny) {
            final Field f = this.request.field;
            if (this.text == null || f == null || !f.typableHere(c)) return false;
            final String next = (this.selected ? "" : this.text.toString()) + c;
            if (!f.accepts(next)) return false;
            if (tiny != null && tiny.stringWidth(next)
                > f.w - 2 * Field.TEXT_DX) return false;
            this.text.setLength(0);
            this.text.append(next);
            this.selected = false;
            return true;
        }

        /**
         * Backspace in a box with a name or amount field: the last
         * character goes, and a selected default is no longer selected
         * (V playthrough-1 #54344 -&gt; #54434: @HOWMUCH4's selected "100"
         * -&gt; "10", "1", ""; gap list Q7, also the name boxes).
         *
         * @return Whether the text changed.
         */
        boolean backspace() {
            if (this.text == null || this.text.length() == 0) return false;
            this.text.setLength(this.text.length() - 1);
            this.selected = false;
            return true;
        }

        /** @return The barred row, or -1. */
        int row() {
            return this.row;
        }

        /** @return A checkbox box's rows' states now (a copy), or null. */
        boolean[] checks() {
            return (this.checks == null) ? null : this.checks.clone();
        }

        /** @return Whether checkbox row {@code i} is on (false out of range). */
        boolean checked(int i) {
            return this.checks != null && i >= 0 && i < this.checks.length
                && this.checks[i];
        }

        /**
         * The row the last input flipped, once: the painter redraws the box.
         *
         * @return The row, or -1 if none was flipped since the last call.
         */
        int takeToggled() {
            final int t = this.toggled;
            this.toggled = -1;
            return t;
        }

        /**
         * Flip a checkbox row and apply it ({@link Request#toggles}); the
         * box stays open.
         *
         * @param i The row.
         * @return {@link #OPEN}.
         */
        int toggle(int i) {
            if (this.checks == null || !this.request.enabled(i)) return OPEN;
            this.checks[i] = !this.checks[i];
            this.toggled = i;
            if (this.request.toggles != null) {
                this.request.toggles.toggled(i, this.checks[i]);
            }
            return OPEN;
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

        /**
         * @return Enter: the barred row if it can be taken (a checkbox box
         *     flips it and stays); a notice's 0.
         */
        int enter() {
            if (this.request.isNotice()) return 0;
            if (this.text != null) {
                // The name field: the text as it is (empty: the default).
                this.request.field.take(this.text.toString());
                return 0;
            }
            if (this.checks != null) return toggle(this.row);
            return this.request.enabled(this.row) ? this.row : OPEN;
        }

        /**
         * @return Escape: the cancel row ({@link Request#escapeAnswer});
         *     {@link #OPEN} in a box where Escape does nothing.  A name
         *     field keeps its default name (Roger) and closes with
         *     {@link #DISMISSED}.
         */
        int escape() {
            final int a = this.request.escapeAnswer();
            if (this.text != null && a != OPEN) this.request.field.keepDefault();
            return a;
        }

        /**
         * F1 (build spec D2): in a box with a help hook it tells the hook
         * the barred row and closes the box with {@link #HELP}; elsewhere
         * {@link #otherKey} (a notice goes, a question stays).
         *
         * @return The answer.
         */
        int help() {
            if (this.request.help == null || this.row < 0) return otherKey();
            this.request.help.help(this.row);
            return HELP;
        }

        /** @return Any other key: dismisses a notice, nothing in a question. */
        int otherKey() {
            return this.request.isNotice() ? 0 : OPEN;
        }

        /**
         * @return Space: in a checkbox box it flips the barred row (I, as
         *     Enter); elsewhere {@link #otherKey}.
         */
        int space() {
            if (this.checks == null) return otherKey();
            return toggle(this.row);
        }

        /**
         * A letter key: in a checkbox box the row whose gold {@code ~} letter
         * it is gets the bar and flips (I, never seen; @GAMEOPTIONS marks
         * I F S E A C Y T, @COLONYOPTIONS marks none); elsewhere
         * {@link #otherKey}.
         *
         * @param letter The letter, upper case.
         * @return The answer.
         */
        int letter(char letter) {
            if (this.checks == null) return otherKey();
            for (int i = 0; i < this.request.rows.size(); i++) {
                if (ClassicMenuModel.hotkey(this.request.rows.get(i)) == letter
                    && this.request.enabled(i)) {
                    this.row = i;
                    return toggle(i);
                }
            }
            return OPEN;
        }

        /**
         * A mouse press: on a row it puts the bar there; outside the box it
         * removes the bar, unless a click outside does nothing in this box
         * ({@link Request#outsideCancels} off: the bar stays where it is).
         *
         * @param hitRow The row under the press, or -1.
         * @param inBox Whether the press is on the box ({@link Layout#inBox}).
         */
        void press(int hitRow, boolean inBox) {
            if (hitRow >= 0) {
                this.row = hitRow;
                this.pressed = hitRow;
            } else if (!inBox) {
                if (!this.request.isNotice() && this.request.outsideCancels) {
                    this.row = -1;
                }
                this.pressed = PRESS_OUTSIDE;
            } else {
                this.pressed = PRESS_TEXT;
            }
        }

        /**
         * The release of a press: on the row it was pressed on it takes it
         * (a checkbox box flips it and stays open: landing-slow, the dot
         * flips 0.14-0.20 s after the bar moved); after a press outside the
         * box, outside it, it closes as Escape where
         * {@link Request#outsideCancels} (else nothing happens); a notice
         * closes on any release after a press.
         *
         * @param hitRow The row under the release, or -1.
         * @param inBox Whether the release is on the box ({@link Layout#inBox}).
         * @return The answer, or {@link #OPEN}.
         */
        int release(int hitRow, boolean inBox) {
            final int p = this.pressed;
            this.pressed = NO_PRESS;
            if (p == NO_PRESS) return OPEN;
            if (this.request.isNotice()) return 0;
            if (p >= 0 && hitRow == p && this.request.enabled(p)) {
                return (this.checks != null) ? toggle(p) : p;
            }
            if (p == PRESS_OUTSIDE && !inBox && this.request.outsideCancels) {
                return escape();
            }
            return OPEN;
        }
    }
}
