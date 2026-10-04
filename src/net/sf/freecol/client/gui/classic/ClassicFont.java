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
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.common.resources.PropertyList;
import net.sf.freecol.common.resources.ResourceManager;


/**
 * One of the original game's <b>bitmap fonts</b>, drawn pixel-exactly.
 *
 * <p>The 1994 game draws every text with one of five {@code .FF} bitmap
 * fonts, which {@code ant classic-assets} converts into the local pack (see
 * {@code net.sf.freecol.tools.classicassets.FfDecoder}).  Measured against
 * the native DOSBox captures (0 differing pixels each):
 * <ul>
 *   <li>{@link #TINY} -- menus and dialogs (the title menu {@code opening_033},
 *       the load box {@code 055}), the in-game info panel and menu bar
 *       ({@code 032}), colony labels and the score screen;</li>
 *   <li>{@link #INTRO} -- the nation texts ({@code 037}) and the departure
 *       captions ({@code 041});</li>
 *   <li>{@link #KING} -- the king's scroll ({@code 039});</li>
 *   <li>{@link #SMALL} and {@link #NP} -- seen in no capture yet, exported for
 *       later screens.</li>
 * </ul>
 *
 * <p><b>Metrics.</b> The advance of a character is exactly its glyph width:
 * the one-pixel gap between letters is built into each glyph, so no extra
 * spacing is ever added.  {@code y} in the draw methods is the <em>top</em> of
 * the glyph cell, which for FONTTINY is the top of the capitals (capitals are
 * rows 0-4, the descender row is 5).  The line pitch is the caller's choice,
 * as in the original: 8 px in menus and dialogs, 7 in the info panel, 6 in
 * unit lists, 10 for FONTINTR and 8 for FONTKING.
 *
 * <p><b>Colours.</b> Glyph pixels hold values 1..3, not colours; every draw
 * call passes a colour table (see {@link #colours(int)}).  FONTTINY and
 * FONTSMAL use value 1 only (menu ink {@code 0x559634}, highlight
 * {@code 0xC7A220}); FONTINTR uses 1 = ink, 2 = a lighter background shade
 * and 3 = the shadow {@code 0x0C0C0C}; FONTKING uses 1 = soft edge
 * {@code 0x715545} and 3 = black ink.
 *
 * <p><b>Why an atlas.</b> The pack stores each font as one 2-bit palette PNG
 * (16x8 cells) plus a metrics string, not one PNG per glyph: FreeCol lists
 * the whole directory for every image resource it maps (~2 s of startup for
 * ~480 glyph files), and zero-width glyphs cannot be stored as images.  The
 * 2-bit raster is kept as is; recolouring just wraps it in a new
 * {@code IndexColorModel}, so no pixels are ever copied.
 *
 * <p><b>Character codes.</b> The game's texts use their own codes for the
 * German letters (see {@link #toCode}).  A literal {@code \}, {@code `} or
 * {@code |} therefore draws as Ü, ä or ö -- exactly as in the game's own
 * text files.
 */
final class ClassicFont {

    private static final Logger logger = Logger.getLogger(ClassicFont.class.getName());

    /** Menus, dialogs, info panel, menu bar (6 px high). */
    static final String TINY = "FONTTINY.FF";
    /** Bold small caps without umlauts (6 px high); use not yet seen. */
    static final String SMALL = "FONTSMAL.FF";
    /** Nation texts and departure captions (9 px high, 3 colours). */
    static final String INTRO = "FONTINTR.FF";
    /** The king's scroll (7 px high). */
    static final String KING = "FONTKING.FF";
    /** Outlined capitals (8 px high); use not yet seen. */
    static final String NP = "FONT-NP.FF";

    /** Pack key prefix; the metrics live under the same key + ".properties". */
    static final String KEY_PREFIX = "image.classic_original.ff.";

    /** Number of codes per font. */
    private static final int CODES = 128;

    /** Atlas grid columns (code c at cell (c%16, c/16)). */
    private static final int COLS = 16;

    /** Size of the per-font recolour cache. */
    private static final int RECOLOUR_CACHE = 32;

    /**
     * Loaded fonts by name.  Misses are stored explicitly as empty: a plain
     * {@code computeIfAbsent} never caches {@code null}, which would re-probe
     * the pack (and re-log) on every paint -- the bug pattern noted at
     * {@code ClassicColonyPanel.buildingImage}.
     */
    private static final Map<String, Optional<ClassicFont>> FONTS = new HashMap<>();

    /** Glyph cell height (= every glyph's height). */
    private final int height;

    /** Atlas cell width (= the widest glyph). */
    private final int cellW;

    /** Advance per code; 0 = no glyph. */
    private final int[] widths;

    /** The glyph values 0..3, 2 bits per pixel, laid out as the atlas. */
    private final WritableRaster values;

    /** Recoloured views of {@link #values}, keyed by colour table. */
    private final Map<String, BufferedImage> recoloured
        = new LinkedHashMap<String, BufferedImage>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> e) {
                return size() > RECOLOUR_CACHE;
            }
        };


    private ClassicFont(int height, int cellW, int[] widths, WritableRaster values) {
        this.height = height;
        this.cellW = cellW;
        this.widths = widths;
        this.values = values;
    }


    // Loading

    /**
     * Build a font from its pack atlas and metrics string.
     *
     * @param atlas The 16x8-cell atlas.  Normally the converter's 2-bit
     *     palette PNG, read raw; an ARGB copy also works (values are then
     *     recovered from the grey coding: transparent, white, light grey,
     *     dark grey = 0, 1, 2, 3).
     * @param metrics {@code format=1,height=H,cell=W,widths=w0;...;w127}.
     * @return The font.
     * @throws IllegalArgumentException on malformed metrics or a too small
     *     atlas.
     */
    static ClassicFont fromAtlas(BufferedImage atlas, String metrics) {
        if (atlas == null || metrics == null) {
            throw new IllegalArgumentException("missing atlas or metrics");
        }
        final int height, cellW;
        final int[] widths = new int[CODES];
        try {
            final PropertyList pl = new PropertyList(metrics);
            if (pl.getInt("format", -1) != 1) {
                throw new IllegalArgumentException("unknown font metrics format");
            }
            height = pl.getInt("height");
            cellW = pl.getInt("cell");
            final String[] w = pl.getString("widths").split(";");
            if (w.length != CODES) {
                throw new IllegalArgumentException("expected " + CODES
                    + " widths, got " + w.length);
            }
            for (int c = 0; c < CODES; c++) {
                widths[c] = Integer.parseInt(w[c].trim());
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {     // PropertyList: missing '=' or key
            throw new IllegalArgumentException("malformed font metrics: "
                + e.getMessage(), e);
        }
        if (height <= 0 || cellW <= 0) {
            throw new IllegalArgumentException("bad font cell " + cellW + "x" + height);
        }
        for (int c = 0; c < CODES; c++) {
            if (widths[c] < 0 || widths[c] > cellW) {
                throw new IllegalArgumentException("bad width " + widths[c]
                    + " for code " + c);
            }
        }
        final int aw = COLS * cellW;
        final int ah = (CODES / COLS) * height;
        if (atlas.getWidth() < aw || atlas.getHeight() < ah) {
            throw new IllegalArgumentException("font atlas " + atlas.getWidth()
                + "x" + atlas.getHeight() + " smaller than " + aw + "x" + ah);
        }

        final WritableRaster v = Raster.createPackedRaster(DataBuffer.TYPE_BYTE,
            aw, ah, 1, 2, null);
        final boolean indexed = atlas.getColorModel() instanceof IndexColorModel
            && ((IndexColorModel) atlas.getColorModel()).getMapSize() <= 4;
        final Raster src = atlas.getRaster();
        for (int y = 0; y < ah; y++) {
            for (int x = 0; x < aw; x++) {
                final int value;
                if (indexed) {
                    value = src.getSample(x, y, 0) & 3;
                } else {
                    final int argb = atlas.getRGB(x, y);
                    final int grey = argb & 0xFF;
                    value = ((argb >>> 24) == 0) ? 0
                        : (grey >= 0xD0) ? 1
                        : (grey >= 0x80) ? 2 : 3;
                }
                v.setSample(x, y, 0, value);
            }
        }
        return new ClassicFont(height, cellW, widths, v);
    }

    /**
     * The named font from the {@code classic_original} pack, loaded once.
     * The probes are silent, so a pack without fonts (never converted, or
     * converted before fonts existed) costs one INFO line, not a warning per
     * paint; callers then fall back to a Swing font.
     *
     * @param name One of {@link #TINY}, {@link #SMALL}, {@link #INTRO},
     *     {@link #KING}, {@link #NP}.
     * @return The font, or {@code null} when the pack does not have it.
     */
    static ClassicFont get(String name) {
        synchronized (FONTS) {
            Optional<ClassicFont> f = FONTS.get(name);
            if (f == null) {
                f = Optional.ofNullable(load(name));
                FONTS.put(name, f);
            }
            return f.orElse(null);
        }
    }

    private static ClassicFont load(String name) {
        final String key = KEY_PREFIX + name;
        try {
            // Silent probes (precedent: ClassicTileArt's constructor); the
            // image comes raw from ImageLibrary.getUnscaledImage, NOT from the
            // sized ImageCache paths, which would re-render it as ARGB.
            if (ResourceManager.getImageResource(key, false) != null) {
                final String metrics = ResourceManager.getString(key + ".properties", null);
                final BufferedImage atlas = ImageLibrary.getUnscaledImage(key);
                if (atlas != null && metrics != null) {
                    return fromAtlas(atlas, metrics);
                }
            }
        } catch (RuntimeException e) {
            // Logged once: misses are cached by get().
            logger.log(Level.WARNING, "Bad bitmap font " + name, e);
        }
        logger.info("classic_original pack has no usable bitmap font " + name
            + " (re-run ant classic-assets)");
        return null;
    }


    // Metrics

    /** @return The glyph cell height in pixels. */
    int height() {
        return this.height;
    }

    /** @return The advance of a raw game code (0 when it has no glyph). */
    int glyphWidth(int code) {
        return (code < 0 || code >= CODES) ? 0 : this.widths[code];
    }

    /** @return The advance of {@code ch} as it will be drawn. */
    int charWidth(char ch) {
        final int code = resolve(ch);
        return (code < 0) ? 0 : this.widths[code];
    }

    /** @return The width of plain (unmarked) text. */
    int stringWidth(String plain) {
        final String s = prepare(plain);
        int w = 0;
        for (int i = 0; i < s.length(); i++) w += charWidth(s.charAt(i));
        return w;
    }

    /** @return The width of marked text: '{', '}' and '~' take no room. */
    int markedWidth(String marked) {
        final String s = prepare(marked);
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            final char ch = s.charAt(i);
            if (ch == '{' || ch == '}' || ch == '~') continue;
            w += charWidth(ch);
        }
        return w;
    }

    /**
     * Cut plain text to {@code maxWidth}, ending in "..." when it had to be
     * cut (the font has no ellipsis glyph).
     */
    String fit(String plain, int maxWidth) {
        final String s = prepare(plain);
        if (stringWidth(s) <= maxWidth) return s;
        final int ell = stringWidth("...");
        final StringBuilder sb = new StringBuilder();
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            final int cw = charWidth(s.charAt(i));
            if (w + cw + ell > maxWidth) break;
            sb.append(s.charAt(i));
            w += cw;
        }
        return sb.toString().trim() + "...";
    }

    /**
     * Greedy word wrap of plain text at spaces, honouring explicit line
     * breaks.  A single word wider than {@code maxWidth} gets a line of its
     * own, cut with {@link #fit}.
     */
    List<String> wrap(String plain, int maxWidth) {
        final List<String> lines = new ArrayList<>();
        for (String para : prepare(plain).split("\n", -1)) {
            final StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                if (word.isEmpty()) continue;
                final String candidate = (line.length() == 0) ? word
                    : line + " " + word;
                if (stringWidth(candidate) <= maxWidth) {
                    line.setLength(0);
                    line.append(candidate);
                } else {
                    if (line.length() > 0) lines.add(line.toString());
                    line.setLength(0);
                    line.append(stringWidth(word) <= maxWidth ? word
                        : fit(word, maxWidth));
                }
            }
            lines.add(line.toString());
        }
        return lines;
    }


    // Character mapping

    /** Expand characters that become several glyphs (the ellipsis). */
    private static String prepare(String s) {
        if (s == null) return "";
        return (s.indexOf('…') < 0) ? s : s.replace("…", "...");
    }

    /**
     * Map a Unicode character onto the game's character code.  The German
     * letters sit on codes the game's own texts use (GAME.TXT, MENU.TXT,
     * LABELS.TXT, confirmed by the glyph shapes): ä 96, ö 28, ü 127, Ä 30,
     * Ö 31, Ü 92, ß 29.  Printable ASCII maps to itself -- so a literal
     * {@code \ ` |} draws Ü ä ö, exactly as in the original.  Dashes and
     * typographic quotes become their ASCII forms; other accented letters
     * lose their accent; anything else becomes '?'.
     *
     * @param ch The character.
     * @return A code 0..127 (0 for control characters).
     */
    static int toCode(char ch) {
        switch (ch) {
        case 'ä': return 96;     // ä
        case 'ö': return 28;     // ö
        case 'ü': return 127;    // ü
        case 'Ä': return 30;     // Ä
        case 'Ö': return 31;     // Ö
        case 'Ü': return 92;     // Ü
        case 'ß': return 29;     // ß
        case '…': return '.';    // … (prepare() expands it to "...")
        case '–': case '—': case '−': return '-';
        case '“': case '”': case '„': case '«': case '»':
            return '"';
        case '‘': case '’': case '‚': return '\'';
        case ' ': return ' ';
        default: break;
        }
        if (ch < 32) return 0;
        if (ch <= 126) return ch;
        final String base = Normalizer.normalize(String.valueOf(ch),
            Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        if (!base.isEmpty() && base.charAt(0) >= 32 && base.charAt(0) <= 126) {
            return base.charAt(0);
        }
        return '?';
    }

    /**
     * The code this font actually draws for {@code ch}: its own glyph, else
     * the umlaut folded to its base letter (FONTSMAL has no umlauts), else
     * '?', else nothing.  FONTTINY has no glyph for {@code _ * < = > @ { } ~ ^};
     * callers replace '_' with a space for file-name labels.
     *
     * @return The code, or -1 to skip the character (advance 0).
     */
    int resolve(char ch) {
        if (ch < 32 && ch != '\t') return -1;
        final int code = toCode(ch == '\t' ? ' ' : ch);
        if (code > 0 && this.widths[code] > 0) return code;
        final int fold;
        switch (ch) {
        case 'ä': fold = 'a'; break;
        case 'ö': fold = 'o'; break;
        case 'ü': fold = 'u'; break;
        case 'Ä': fold = 'A'; break;
        case 'Ö': fold = 'O'; break;
        case 'Ü': fold = 'U'; break;
        case 'ß': fold = 's'; break;
        default: fold = -1; break;
        }
        if (fold > 0 && this.widths[fold] > 0) return fold;
        return (this.widths['?'] > 0) ? '?' : -1;
    }


    // Colours

    /**
     * A colour table for a one-colour font (FONTTINY, FONTSMAL).
     *
     * @param rgb1 The ink for value 1, as {@code 0xRRGGBB}.
     * @return {@code {0, ink, 0, 0}}; an entry of 0 draws nothing.
     */
    static int[] colours(int rgb1) {
        return new int[] { 0, 0xFF000000 | rgb1, 0, 0 };
    }

    /**
     * A colour table for a multi-colour font (FONTINTR, FONTKING, FONT-NP).
     *
     * @return {@code {0, c1, c2, c3}}, all opaque.
     */
    static int[] colours(int rgb1, int rgb2, int rgb3) {
        return new int[] { 0, 0xFF000000 | rgb1, 0xFF000000 | rgb2,
                           0xFF000000 | rgb3 };
    }

    /** The atlas wrapped in a colour model for {@code palette} (zero copy). */
    private synchronized BufferedImage recoloured(int[] palette) {
        final String key = Arrays.toString(palette);
        BufferedImage img = this.recoloured.get(key);
        if (img == null) {
            final int[] cmap = { 0x00000000,
                                 (palette.length > 1) ? palette[1] : 0,
                                 (palette.length > 2) ? palette[2] : 0,
                                 (palette.length > 3) ? palette[3] : 0 };
            final IndexColorModel icm = new IndexColorModel(2, 4, cmap, 0, true,
                -1, DataBuffer.TYPE_BYTE);
            img = new BufferedImage(icm, this.values, false, null);
            this.recoloured.put(key, img);
        }
        return img;
    }


    // Drawing

    /**
     * Draw plain text: every character literally (no markup), for data such
     * as player or file names.
     *
     * @param g The graphics (1:1 or under an integer scale).
     * @param plain The text.
     * @param x The left edge.
     * @param y The top of the glyph cell.
     * @param palette The colour table (see {@link #colours(int)}).
     * @return The x after the last glyph.
     */
    int draw(Graphics2D g, String plain, int x, int y, int[] palette) {
        final String s = prepare(plain);
        // A child Graphics carries the hint, so the caller's stays untouched
        // even when it had none (restoring "none" is not possible).
        final Graphics2D gg = nearest(g);
        try {
            final BufferedImage img = recoloured(palette);
            for (int i = 0; i < s.length(); i++) {
                x = drawGlyph(gg, img, resolve(s.charAt(i)), x, y);
            }
        } finally {
            gg.dispose();
        }
        return x;
    }

    /**
     * Draw text with the original's colour markup (GAME.TXT / MENU.TXT):
     * '{' switches to the highlight colour, '}' back, and '~' highlights
     * just the next glyph (a hotkey letter).
     *
     * @return The x after the last glyph.
     */
    int drawMarked(Graphics2D g, String marked, int x, int y,
                   int[] normal, int[] highlight) {
        final String s = prepare(marked);
        final Graphics2D gg = nearest(g);
        try {
            final BufferedImage normalImg = recoloured(normal);
            final BufferedImage highImg = recoloured(highlight);
            boolean on = false, once = false;
            for (int i = 0; i < s.length(); i++) {
                final char ch = s.charAt(i);
                if (ch == '{') {
                    on = true;
                } else if (ch == '}') {
                    on = false;
                } else if (ch == '~') {
                    once = true;
                } else {
                    x = drawGlyph(gg, (on || once) ? highImg : normalImg,
                                  resolve(ch), x, y);
                    once = false;
                }
            }
        } finally {
            gg.dispose();
        }
        return x;
    }

    /** A child of {@code g} that scales glyphs nearest-neighbour. */
    private static Graphics2D nearest(Graphics2D g) {
        final Graphics2D gg = (Graphics2D) g.create();
        gg.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        return gg;
    }

    /** Blit one glyph cell (its width only) and return the next x. */
    private int drawGlyph(Graphics2D g, BufferedImage img, int code, int x, int y) {
        if (code < 0) return x;
        final int w = this.widths[code];
        if (w <= 0) return x;
        final int sx = (code % COLS) * this.cellW;
        final int sy = (code / COLS) * this.height;
        g.drawImage(img, x, y, x + w, y + this.height,
                    sx, sy, sx + w, sy + this.height, null);
        return x + w;
    }
}
