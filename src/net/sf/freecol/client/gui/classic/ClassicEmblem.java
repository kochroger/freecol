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
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;


/**
 * The intro's own emblem <b>"Levi's / COLONIZATION"</b> with the subtitle
 * <b>"Levi's Colonization 2026"</b>, drawn procedurally in the STYLE of the
 * original's MicroProse "MPS LABS" logo animation -- the owner asked for
 * "Levi's Colonization 2026, animated like MPS Labs".  Static, Swing-free
 * and headless, like every classic painter.
 *
 * <p><b>Own art, no MicroProse material.</b>  Nothing here comes from the
 * original files: no sprite, no name, no palette file.  The design only
 * reproduces the measured <em>style</em> of the original logo (captures
 * {@code screenshots/intro-original/opening_084..103}, analysed in
 * scratch, see the README "Opening" section):
 * <ul>
 *   <li>a square prism with the same face on all four sides, turning about
 *       its vertical axis, 5.625 degrees per frame (16 frames per quarter
 *       turn) at 10.12 frames/s, the front face turning away to the LEFT
 *       (clockwise seen from above);</li>
 *   <li>the space between the letters is see-through, so the BACK faces
 *       show through mirrored, lower and narrower -- here not drawn but
 *       the automatic result of the perspective: camera distance
 *       {@link #DIST} = 7.7 x the half face width (the original's back band
 *       is 0.77 x as wide as its front band) and the eye line {@link #EYE}
 *       91 px below the band (the original is seen from below; its back
 *       band sits ~20 px lower);</li>
 *   <li>a tall blue-gradient script (16 steps from cyan to royal blue with
 *       a brushed streak, echoing the measured rows of capture 091) above a
 *       band of two gold lines with white block capitals;</li>
 *   <li>faces shaded by angle; the mirrored script of the back faces is
 *       darkened further so it does not clutter the front, but the back
 *       band stays bright, as in the original;</li>
 *   <li>a fixed palette of at most 64 colours, no anti-aliasing on the
 *       prism, an opaque black box around it;</li>
 *   <li>the subtitle grows horizontally only, out from UNDER the box: it is
 *       drawn first and the box covers its first stages, with the two
 *       smoothstep laws measured on the original's subtitle (width
 *       24 -&gt; 300 px over 28 stages, bottom 115 -&gt; 170 over 18).</li>
 * </ul>
 *
 * <p>The word "Levi's" and the subtitle are rasterised once from the
 * logical font {@code Serif} (Times New Roman on Windows): the script at 4x
 * without anti-aliasing and reduced by 4x4 majority to a crisp 1-bit mask,
 * the subtitle at 4x with anti-aliasing, box-filtered and quantised to the
 * palette's five grey levels.  Pixels may therefore differ on another JDK
 * or OS; the tests check geometry and palette invariants only.  The band's
 * capitals are an own 7x9 bitmap font defined below.
 */
final class ClassicEmblem {

    /** The canvas. */
    static final int W = 320, H = 200;

    /** Distinct prism pictures: the four faces are identical, so the
        picture repeats after a quarter turn = 16 frames. */
    static final int PHASES = 16;

    /** The turn per frame, degrees. */
    static final double STEP_DEG = 90.0 / PHASES;

    /** The face texture. */
    static final int TEX_W = 120, TEX_H = 88;

    /** Texture rows of the script (the rest is the band). */
    static final int SCRIPT_ROWS = 70;

    /** Prism half width (= TEX_W / 2), camera distance, eye row, the screen
        row of texture row 0 face-on, the axis column. */
    static final double HALF = 60.0, DIST = 7.7 * HALF, EYE = 187.0,
        Y0 = 25.0, AXIS_X = 159.5;

    /** The opaque black box the prism is drawn in. */
    static final int BOX_X = 82, BOX_Y = 22, BOX_W = 156, BOX_H = 119;

    /** The subtitle sprite at full size; its baseline row. */
    static final int SUB_W = 300, SUB_H = 25, SUB_BASELINE = 20;

    /** The script word and the subtitle (typographic apostrophe). */
    static final String SCRIPT = "Levi’s";
    static final String SUBTITLE = "Levi’s Colonization 2026";

    /** The band word, in the own block capitals. */
    static final String BAND = "COLONIZATION";

    /** The script's vertical ramp, top to bottom (own values). */
    static final int[] RAMP = {
        0x45FFFF, 0x3FEDF4, 0x39DBEE, 0x33C8E8, 0x2DB4E1, 0x27A2DC, 0x2191D8,
        0x1A85D1, 0x1675CB, 0x1266C8, 0x0F5EC8, 0x0A49BB, 0x0432AF, 0x0024AE,
        0x001A9E, 0x00148E
    };

    static final int GOLD = 0xFBFB45, GOLD_DARK = 0xBEBE3C,
        WHITE = 0xFBFBFB, OUTLINE = 0x000848;

    /** The subtitle's grey levels 1..4 (= WHITE at 1/4 .. 4/4). */
    static final int[] GREYS = { 0x3E3E3E, 0x7D7D7D, 0xBCBCBC, 0xFBFBFB };

    /**
     * The own 7x9 block capitals, one int per row (bit 6 = left column),
     * only the letters of {@link #BAND}.
     */
    private static final String CAPS_LETTERS = "COLNIZAT";
    private static final int[][] CAPS = {
        { 0b0111110, 0b1100011, 0b1100000, 0b1100000, 0b1100000,
          0b1100000, 0b1100000, 0b1100011, 0b0111110 },                // C
        { 0b0111110, 0b1100011, 0b1100011, 0b1100011, 0b1100011,
          0b1100011, 0b1100011, 0b1100011, 0b0111110 },                // O
        { 0b1100000, 0b1100000, 0b1100000, 0b1100000, 0b1100000,
          0b1100000, 0b1100000, 0b1100000, 0b1111111 },                // L
        { 0b1100011, 0b1110011, 0b1110011, 0b1101011, 0b1101011,
          0b1101011, 0b1100111, 0b1100111, 0b1100011 },                // N
        { 0b0111110, 0b0011100, 0b0011100, 0b0011100, 0b0011100,
          0b0011100, 0b0011100, 0b0011100, 0b0111110 },                // I
        { 0b1111111, 0b0000011, 0b0000110, 0b0001100, 0b0011000,
          0b0110000, 0b1100000, 0b1100000, 0b1111111 },                // Z
        { 0b0011100, 0b0110110, 0b1100011, 0b1100011, 0b1111111,
          0b1100011, 0b1100011, 0b1100011, 0b1100011 },                // A
        { 0b1111111, 0b0011100, 0b0011100, 0b0011100, 0b0011100,
          0b0011100, 0b0011100, 0b0011100, 0b0011100 }                 // T
    };

    /** Band geometry in the texture. */
    private static final int BAND_TOP = 71, CAPS_TOP = 74, BAND_BOTTOM = 84,
        BAND_X0 = 4, BAND_X1 = 115, CAPS_X = 12;

    /** The palette, built once. */
    private static final int[] PALETTE = buildPalette();

    /** Caches (built lazily, then shared read-only). */
    private static int[] texture = null;
    private static int[] subtitle = null;
    private static final int[][] prismFrames = new int[PHASES][];


    private ClassicEmblem() {}   // static only


    // Palette

    /**
     * Black, the outline colour, the 16 ramp colours at 1.0 / 0.6 / 0.3,
     * the gold pair at 1.0 / 0.6 / 0.3 and white at 1, 3/4, 1/2, 1/4: 60
     * entries, so the emblem keeps the look of a 256-colour VGA logo.
     */
    private static int[] buildPalette() {
        final int[] p = new int[2 + RAMP.length * 3 + 6 + 4];
        int n = 0;
        p[n++] = 0x000000;
        p[n++] = OUTLINE;
        for (double f : new double[] { 1.0, 0.6, 0.3 }) {
            for (int c : RAMP) p[n++] = scale(c, f);
            p[n++] = scale(GOLD, f);
            p[n++] = scale(GOLD_DARK, f);
        }
        for (int c : GREYS) p[n++] = c;
        return p;
    }

    /** @return A copy of the palette. */
    static int[] palette() {
        return PALETTE.clone();
    }

    /** {@code rgb} with every channel multiplied by {@code f}, truncated. */
    static int scale(int rgb, double f) {
        final int r = (int) (((rgb >> 16) & 0xFF) * f);
        final int g = (int) (((rgb >> 8) & 0xFF) * f);
        final int b = (int) ((rgb & 0xFF) * f);
        return (r << 16) | (g << 8) | b;
    }

    /** The palette entry nearest to {@code rgb} (squared RGB distance). */
    static int snap(int rgb) {
        final int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        int best = 0;
        long bd = Long.MAX_VALUE;
        for (int c : PALETTE) {
            final long dr = r - ((c >> 16) & 0xFF), dg = g - ((c >> 8) & 0xFF),
                db = b - (c & 0xFF);
            final long d = dr * dr + dg * dg + db * db;
            if (d < bd) {
                bd = d;
                best = c;
            }
        }
        return best;
    }


    // The face texture

    /**
     * The face texture, TEX_W x TEX_H ARGB, 0 = see-through: rows 0..69 the
     * script with its 1-px outline, rows 71..85 the band.  Built once.
     *
     * @return The shared texture (do not modify).
     */
    static synchronized int[] faceTexture() {
        if (texture == null) texture = buildTexture();
        return texture;
    }

    private static int[] buildTexture() {
        final int[] t = new int[TEX_W * TEX_H];
        final boolean[] mask = scriptMask();
        int top = SCRIPT_ROWS, bot = -1;
        for (int y = 0; y < SCRIPT_ROWS; y++) {
            for (int x = 0; x < TEX_W; x++) {
                if (mask[y * TEX_W + x]) {
                    top = Math.min(top, y);
                    bot = Math.max(bot, y);
                }
            }
        }
        // Fill: the ramp over the glyph rows, a brushed streak one step
        // darker (deterministic: seeded per row).
        for (int y = top; y <= bot; y++) {
            final int base = (y - top) * RAMP.length / (bot - top + 1);
            final boolean[] dark = new boolean[TEX_W];
            final SplittableRandom rnd = new SplittableRandom(2026L + y);
            int x = rnd.nextInt(0, 12);
            while (x < TEX_W) {
                final int run = rnd.nextInt(3, 9);
                for (int i = 0; i < run && x + i < TEX_W; i++) dark[x + i] = true;
                x += run + rnd.nextInt(8, 26);
            }
            for (x = 0; x < TEX_W; x++) {
                if (!mask[y * TEX_W + x]) continue;
                final int idx = Math.min(RAMP.length - 1, base + (dark[x] ? 1 : 0));
                t[y * TEX_W + x] = 0xFF000000 | RAMP[idx];
            }
        }
        // The outline, outside the mask, so the script separates from the
        // band and from the faces behind it.
        for (int y = 0; y < SCRIPT_ROWS; y++) {
            for (int x = 0; x < TEX_W; x++) {
                if (mask[y * TEX_W + x]) continue;
                boolean near = false;
                for (int dy = -1; dy <= 1 && !near; dy++) {
                    for (int dx = -1; dx <= 1 && !near; dx++) {
                        final int yy = y + dy, xx = x + dx;
                        near = yy >= 0 && yy < SCRIPT_ROWS && xx >= 0 && xx < TEX_W
                            && mask[yy * TEX_W + xx];
                    }
                }
                if (near) t[y * TEX_W + x] = 0xFF000000 | OUTLINE;
            }
        }
        // The band: gold line, darker gold line, the capitals, again.
        for (int x = BAND_X0; x <= BAND_X1; x++) {
            t[BAND_TOP * TEX_W + x] = 0xFF000000 | GOLD;
            t[(BAND_TOP + 1) * TEX_W + x] = 0xFF000000 | GOLD_DARK;
            t[BAND_BOTTOM * TEX_W + x] = 0xFF000000 | GOLD;
            t[(BAND_BOTTOM + 1) * TEX_W + x] = 0xFF000000 | GOLD_DARK;
        }
        int cx = CAPS_X;
        for (int i = 0; i < BAND.length(); i++) {
            final int[] g = CAPS[CAPS_LETTERS.indexOf(BAND.charAt(i))];
            for (int r = 0; r < g.length; r++) {
                for (int c = 0; c < 7; c++) {
                    if ((g[r] & (1 << (6 - c))) != 0) {
                        t[(CAPS_TOP + r) * TEX_W + cx + c] = 0xFF000000 | WHITE;
                    }
                }
            }
            cx += 8;
        }
        return t;
    }

    /**
     * The script's 1-bit mask, rows 0..69: "Levi's" in Serif bold italic,
     * stretched so the 'L' is 64 px tall and the word 116 px wide (the
     * original's script is similarly tall and narrow), drawn at 4x without
     * anti-aliasing and reduced by 4x4 majority (8 of 16) -- crisp and
     * deterministic for a given JDK.
     */
    private static boolean[] scriptMask() {
        final Font font = new Font(Font.SERIF, Font.BOLD | Font.ITALIC, 100);
        final FontRenderContext frc = new FontRenderContext(null, false, false);
        final GlyphVector gv = font.createGlyphVector(frc, SCRIPT);
        final Rectangle2D word = gv.getVisualBounds();
        final Rectangle2D capL = font.createGlyphVector(frc, "L").getVisualBounds();
        final double xs = 116.0 / word.getWidth();
        final double ys = Math.min(64.0 / capL.getHeight(), 67.0 / word.getHeight());
        final int ss = 4;
        final AffineTransform at = new AffineTransform();
        at.scale(ss, ss);
        at.translate(TEX_W / 2.0 - word.getWidth() * xs / 2.0, 1.0);
        at.scale(xs, ys);
        at.translate(-word.getX(), -word.getY());
        final Shape s = at.createTransformedShape(gv.getOutline());
        final BufferedImage img = new BufferedImage(TEX_W * ss, SCRIPT_ROWS * ss,
                                                    BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                               RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setColor(Color.WHITE);
            g.fill(s);
        } finally {
            g.dispose();
        }
        final boolean[] m = new boolean[TEX_W * SCRIPT_ROWS];
        for (int y = 0; y < SCRIPT_ROWS; y++) {
            for (int x = 0; x < TEX_W; x++) {
                int on = 0;
                for (int dy = 0; dy < ss; dy++) {
                    for (int dx = 0; dx < ss; dx++) {
                        if ((img.getRGB(x * ss + dx, y * ss + dy) >>> 24) != 0) on++;
                    }
                }
                m[y * TEX_W + x] = on >= 8;
            }
        }
        return m;
    }


    // The prism

    /** The angle of face {@code i} at prism angle {@code thetaDeg}, radians:
        decreasing, so the front face turns away to the left. */
    private static double faceAngle(double thetaDeg, int i) {
        return Math.toRadians(i * 90.0 - thetaDeg);
    }

    /**
     * Which texture column face {@code i} shows at screen column
     * {@code x}, by inverting the perspective of the face's plane.
     *
     * @param thetaDeg The prism angle, degrees.
     * @param i The face, 0..3.
     * @param x The screen column.
     * @return The texture column 0..TEX_W-1, or -1 where the face is not.
     */
    static int textureColumn(double thetaDeg, int i, int x) {
        final double u = faceU(faceAngle(thetaDeg, i), x);
        return (Double.isNaN(u)) ? -1 : (int) Math.floor(u + HALF);
    }

    /** The face coordinate u in [-HALF, HALF) at column x, or NaN. */
    private static double faceU(double phi, int x) {
        final double sin = Math.sin(phi), cos = Math.cos(phi);
        final double cx = HALF * sin, cz = HALF * cos, f = DIST - HALF;
        final double sx = x + 0.5 - AXIS_X;
        final double denom = sx * sin - f * cos;
        if (Math.abs(denom) < 1e-9) return Double.NaN;
        final double u = (f * cx - sx * (DIST - cz)) / denom;
        return (u < -HALF || u >= HALF) ? Double.NaN : u;
    }

    /**
     * The prism picture of one phase, BOX_W x BOX_H RGB (black ground),
     * built once per phase.  Faces are drawn far to near; per screen
     * column the texture column comes from {@link #faceU}, per row the
     * texture row from the column's scale, nearest neighbour; see-through
     * texels let the faces behind show.
     *
     * @param phase The phase, any int (taken modulo {@link #PHASES}).
     * @return The shared picture (do not modify).
     */
    static int[] prismFrame(int phase) {
        final int p = Math.floorMod(phase, PHASES);
        synchronized (prismFrames) {
            if (prismFrames[p] == null) prismFrames[p] = buildPrism(p * STEP_DEG);
            return prismFrames[p];
        }
    }

    private static int[] buildPrism(double thetaDeg) {
        final int[] tex = faceTexture();
        final int[] out = new int[BOX_W * BOX_H];
        final Integer[] order = { 0, 1, 2, 3 };
        Arrays.sort(order, (a, b) -> Double.compare(
            Math.cos(faceAngle(thetaDeg, a)), Math.cos(faceAngle(thetaDeg, b))));
        final Map<Long, Integer> snapped = new HashMap<>();
        for (int i : order) {
            final double phi = faceAngle(thetaDeg, i);
            final double sin = Math.sin(phi), cos = Math.cos(phi);
            final double bright = 0.22 + 0.78 * Math.abs(cos);
            final double scriptBright = (cos < 0) ? bright * 0.45 : bright;
            for (int x = BOX_X; x < BOX_X + BOX_W; x++) {
                final double u = faceU(phi, x);
                if (Double.isNaN(u)) continue;
                final int tc = Math.min(TEX_W - 1, (int) Math.floor(u + HALF));
                final double pz = HALF * cos - u * sin;
                final double s = (DIST - HALF) / (DIST - pz);
                for (int y = BOX_Y; y < BOX_Y + BOX_H; y++) {
                    final int tv = (int) Math.floor((y + 0.5 - EYE) / s + EYE - Y0);
                    if (tv < 0 || tv >= TEX_H) continue;
                    final int texel = tex[tv * TEX_W + tc];
                    if ((texel >>> 24) == 0) continue;
                    final double b = (tv < SCRIPT_ROWS) ? scriptBright : bright;
                    final long key = (Math.round(b * 1e6) << 24) | (texel & 0xFFFFFF);
                    Integer c = snapped.get(key);
                    if (c == null) {
                        c = snap(scale(texel & 0xFFFFFF, b));
                        snapped.put(key, c);
                    }
                    out[(y - BOX_Y) * BOX_W + (x - BOX_X)] = c;
                }
            }
        }
        return out;
    }


    // The subtitle

    /** smoothstep(t) = 3t^2 - 2t^3 on [0,1]. */
    static double smoothstep(double t) {
        final double c = Math.max(0.0, Math.min(1.0, t));
        return c * c * (3.0 - 2.0 * c);
    }

    /** @return The subtitle's width at stage {@code k} (0..28). */
    static int subtitleWidth(int k) {
        final int kk = Math.max(0, Math.min(ClassicIntroTimeline.SUBTITLE_STAGES, k));
        return (int) Math.round(24 + (SUB_W - 24) * smoothstep(kk / 28.0));
    }

    /** @return The subtitle's bottom row (inclusive) at stage {@code k}. */
    static int subtitleBottom(int k) {
        final int kk = Math.max(0, Math.min(ClassicIntroTimeline.SUBTITLE_STAGES, k));
        return (int) Math.round(115 + 55 * smoothstep(Math.min(kk, 18) / 18.0));
    }

    /**
     * The subtitle sprite at full size, SUB_W x SUB_H, 0 = transparent, else
     * one of {@link #GREYS}: Serif bold with a ~16 px cap height and the
     * baseline at row {@link #SUB_BASELINE}, drawn at 4x with
     * anti-aliasing, box-filtered and quantised to the four greys.
     *
     * @return The shared sprite (do not modify).
     */
    static synchronized int[] subtitleSprite() {
        if (subtitle == null) subtitle = buildSubtitle();
        return subtitle;
    }

    private static int[] buildSubtitle() {
        final int ss = 4;
        final FontRenderContext frc = new FontRenderContext(null, true, true);
        final Font probe = new Font(Font.SERIF, Font.BOLD, 100);
        final double cap = probe.createGlyphVector(frc, "C").getVisualBounds().getHeight();
        float size = (float) (100.0 * 16.0 / cap);
        Font font = probe.deriveFont(size);
        Rectangle2D vb = font.createGlyphVector(frc, SUBTITLE).getVisualBounds();
        if (vb.getWidth() > SUB_W - 4) {
            size = (float) (size * (SUB_W - 4) / vb.getWidth());
            font = probe.deriveFont(size);
            vb = font.createGlyphVector(frc, SUBTITLE).getVisualBounds();
        }
        final BufferedImage img = new BufferedImage(SUB_W * ss, SUB_H * ss,
                                                    BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                               RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                               RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                               RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setColor(Color.WHITE);
            g.setFont(font.deriveFont(size * ss));
            final double x = (SUB_W - vb.getWidth()) / 2.0 - vb.getX();
            g.drawString(SUBTITLE, (float) (x * ss), (float) (SUB_BASELINE * ss));
        } finally {
            g.dispose();
        }
        final int[] out = new int[SUB_W * SUB_H];
        for (int y = 0; y < SUB_H; y++) {
            for (int x = 0; x < SUB_W; x++) {
                int a = 0;
                for (int dy = 0; dy < ss; dy++) {
                    for (int dx = 0; dx < ss; dx++) {
                        a += img.getRGB(x * ss + dx, y * ss + dy) >>> 24;
                    }
                }
                final int lvl = (int) Math.round(a / (255.0 * ss * ss) * GREYS.length);
                if (lvl > 0) out[y * SUB_W + x] = GREYS[Math.min(GREYS.length, lvl) - 1];
            }
        }
        return out;
    }

    /**
     * Draw the subtitle at stage {@code k}: stretched horizontally only,
     * nearest neighbour, centred on x = 160, bottom row
     * {@link #subtitleBottom}.
     */
    static void paintSubtitle(int[] fb, int k) {
        final int[] sub = subtitleSprite();
        final int w = subtitleWidth(k);
        final int left = W / 2 - w / 2, top = subtitleBottom(k) - (SUB_H - 1);
        for (int dy = 0; dy < SUB_H; dy++) {
            final int y = top + dy;
            if (y < 0 || y >= H) continue;
            for (int dx = 0; dx < w; dx++) {
                final int x = left + dx;
                if (x < 0 || x >= W) continue;
                final int c = sub[dy * SUB_W + (int) ((dx + 0.5) * SUB_W / w)];
                if (c != 0) fb[y * W + x] = c;
            }
        }
    }


    // The whole picture

    /**
     * One emblem picture: black, the subtitle (under the box), the box with
     * the prism, then the fade.
     *
     * @param fb The 320x200 RGB canvas, overwritten.
     * @param frame The emblem frame counter (the prism shows
     *     {@code frame mod 16}).
     * @param subtitleK The subtitle stage 0..28.
     * @param level Brightness in eighths, 8 = full, 0 = black (the fade:
     *     every colour scaled and snapped back to the palette, as a VGA
     *     palette fade would).
     */
    static void paint(int[] fb, int frame, int subtitleK, int level) {
        Arrays.fill(fb, 0, W * H, 0);
        paintSubtitle(fb, subtitleK);
        final int[] prism = prismFrame(frame);
        for (int y = 0; y < BOX_H; y++) {
            System.arraycopy(prism, y * BOX_W, fb, (BOX_Y + y) * W + BOX_X, BOX_W);
        }
        if (level >= 8) return;
        final Map<Integer, Integer> faded = new HashMap<>();
        final double f = Math.max(0, level) / 8.0;
        for (int i = 0; i < W * H; i++) {
            final int c = fb[i];
            if (c == 0) continue;
            Integer d = faded.get(c);
            if (d == null) {
                d = snap(scale(c, f));
                faded.put(c, d);
            }
            fb[i] = d;
        }
    }
}
