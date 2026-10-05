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
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * The painter of the <b>intro</b> (Vorspann): the original opening's sea
 * chart with the credit scrolls and the title build-up, faithful, plus the
 * dispatch of a whole intro picture by {@link ClassicIntroTimeline} phase
 * (the own emblem is {@link ClassicEmblem}).  Static and headless: the
 * preview harness renders exactly these pictures and diffs them against
 * the native captures.
 *
 * <p><b>The chart rules</b>, every one verified to 0 differing pixels
 * against the captures {@code screenshots/intro-original/opening_104..165}
 * (59 single frames; 123, 134 and 141 are DOSBox captures taken mid-redraw
 * and equal two consecutive frames row for row):
 * <ol>
 *   <li>The frame is OPENBORD.PIK (wood, rope border, a black window at
 *       rows 24..155).</li>
 *   <li>OPENING.PIK, the 960x132 chart, fills rows 24..155 at x offset
 *       {@code max(0, 640 - f)}: one pixel per frame from the Europe side
 *       westwards, still from frame 640 on.</li>
 *   <li>The ship OPENSHIP.SS.(f mod 8) sits at
 *       {@code (PATH[f-1].x - 11 - offset, PATH[f-1].y - 12)} while
 *       {@code f < 701} (the 701 PATH.DAT points); then the OPENBONK
 *       series (the ship running aground) takes over.</li>
 *   <li>The {@code @OPENING} entries in table order, from their start
 *       frame: sprite index {@code f - start}; repeats 0 = play once and
 *       HOLD the last frame (the beached ship, the man with the flag, the
 *       title logo stay); repeats R &gt; 0 = R+1 plays and then the sprite
 *       VANISHES; a later started entry of the same series supersedes an
 *       earlier one (the first wind starts at 78 and again at 97).
 *       Top-left = the sprite's anchor top-left + (baseX - offset, 0).</li>
 *   <li>The SUN is the one exception: it keeps animating slowly, about 21
 *       chart frames per sprite frame ({@link #sunFrame}, FITTED -- exact
 *       on frames 143..201, unverified outside, &lt;= 12 px).</li>
 *   <li>Ship and animations are clipped to rows 24..155.</li>
 *   <li>Credits: the first {@code @CREDITS} row with start &lt;= f &lt;= end
 *       draws OPENCRD{series+1}.SS.{sprite-1} centred in the bottom wood
 *       strip at {@code (160 - w/2, 183 - h/2)}, unclipped; appearing and
 *       vanishing are cuts.  One credit at a time.</li>
 * </ol>
 * The title logo OPENLOGO (series 8) arrives at frame 767 as a cut, and the
 * whole picture freezes at frame 769 ({@link ClassicIntroTimeline}); at the
 * END row the screen cuts to the live title menu -- OPENMENU.PIK is a
 * separate picture (the man and flag vanish, the ship moves, ~2.3k sea
 * pixels change between captures 165 and 166), so the cut is faithful.
 */
final class ClassicIntro {

    /** The canvas. */
    static final int W = 320, H = 200;

    /** The chart window of OPENBORD: screen rows 24..155. */
    static final int CHART_TOP = 24, CHART_ROWS = 132;

    /** Where a credit scroll is centred. */
    static final int CREDIT_CX = 160, CREDIT_CY = 183;

    /** The ship sprite's offset from its path point. */
    static final int SHIP_DX = -11, SHIP_DY = -12;

    /** The system property that switches the intro off ("false"). */
    static final String PROPERTY = "freecol.classic.intro";


    private ClassicIntro() {}   // static only

    /**
     * Whether the intro runs on a normal launch.  Only the system property
     * {@code -Dfreecol.classic.intro=false} switches it off (and
     * {@code --fast}, a save argument or a debug start, which never show a
     * menu).  FreeCol's own {@code --no-intro} is deliberately NOT honoured:
     * it means "skip FreeCol's intro video", which the Classic UI never
     * shows, and the owner's desktop shortcut passes it.
     *
     * @return False only with the property set to "false".
     */
    static boolean enabled() {
        return !"false".equalsIgnoreCase(System.getProperty(PROPERTY));
    }


    /** A sprite frame: its pixels and where its anchor puts it. */
    static final class Sprite {

        final int w, h;
        /** ARGB, alpha 0 = transparent. */
        final int[] argb;
        /** The anchor top-left (0,0 when there is no anchor). */
        final int x, y;

        Sprite(int w, int h, int[] argb, int x, int y) {
            this.w = w;
            this.h = h;
            this.argb = argb;
            this.x = x;
            this.y = y;
        }

        static Sprite of(BufferedImage img, Point topLeft) {
            final int w = img.getWidth(), h = img.getHeight();
            final int[] p = img.getRGB(0, 0, w, h, null, 0, w);
            return new Sprite(w, h, p, (topLeft == null) ? 0 : topLeft.x,
                              (topLeft == null) ? 0 : topLeft.y);
        }
    }

    /** The opening's pictures and sprites, loaded together; immutable. */
    static final class Assets {

        /** OPENBORD.PIK, 320x200 RGB. */
        final int[] bord;
        /** OPENING.PIK, chartW x 132 RGB. */
        final int[] chart;
        final int chartW;
        /** OPENSHIP.SS frames. */
        final Sprite[] ship;
        /** The ten @OPENING series, by series number. */
        final Sprite[][] series;
        /** OPENCRD1..3. */
        final Sprite[][] credits;

        Assets(int[] bord, int[] chart, int chartW, Sprite[] ship,
               Sprite[][] series, Sprite[][] credits) {
            this.bord = bord;
            this.chart = chart;
            this.chartW = chartW;
            this.ship = ship;
            this.series = series;
            this.credits = credits;
        }

        /**
         * Load from the pack (~250 small PNGs and three pictures; the
         * player does it on its own thread, never on the EDT).
         *
         * @param p The pack, or null.
         * @return The assets, or null when the pack lacks the opening
         *     ({@link ClassicPackFiles#hasOpening}) or a picture is
         *     unreadable.
         */
        static Assets fromPackFiles(ClassicPackFiles p) {
            if (p == null || !p.hasOpening()) return null;
            final BufferedImage bord = p.image(ClassicPackFiles.pikKey("OPENBORD.PIK"));
            final BufferedImage chart = p.image(ClassicPackFiles.pikKey("OPENING.PIK"));
            if (bord == null || chart == null || bord.getWidth() != W
                || bord.getHeight() != H || chart.getWidth() < W
                || chart.getHeight() < CHART_ROWS) return null;
            final int cw = chart.getWidth();
            final Sprite[] ship = frames(p, "OPENSHIP", false);
            final Sprite[][] series = new Sprite[ClassicOpeningScript.SERIES_STEMS.length][];
            for (int i = 0; i < series.length; i++) {
                series[i] = frames(p, ClassicOpeningScript.SERIES_STEMS[i], true);
            }
            final Sprite[][] credits = new Sprite[ClassicOpeningScript.CREDIT_STEMS.length][];
            for (int i = 0; i < credits.length; i++) {
                credits[i] = frames(p, ClassicOpeningScript.CREDIT_STEMS[i], false);
            }
            return new Assets(rgb(bord, W, H), rgb(chart, cw, CHART_ROWS), cw,
                              ship, series, credits);
        }

        /** Every frame of a series, until the first missing one. */
        private static Sprite[] frames(ClassicPackFiles p, String stem,
                                       boolean anchored) {
            final List<Sprite> l = new ArrayList<>();
            for (int k = 0; k < 1000; k++) {
                final String f = String.format("%s.SS.%03d", stem, k);
                final BufferedImage img = p.image(ClassicPackFiles.ssKey(f));
                if (img == null) break;
                l.add(Sprite.of(img, anchored ? p.spriteTopLeft(f) : null));
            }
            return l.toArray(new Sprite[0]);
        }

        private static int[] rgb(BufferedImage img, int w, int h) {
            final int[] p = img.getRGB(0, 0, w, h, null, 0, w);
            for (int i = 0; i < p.length; i++) p[i] &= 0xFFFFFF;
            return p;
        }
    }


    // The chart

    /**
     * The sun's sprite frame at chart frame {@code f}: about 21 chart
     * frames per sprite frame, cycling.  FITTED on captures with
     * frames 143..201 (index 1 @143-149, 2 @154-169, 3 @175-190,
     * 4 @196-201), where it is exact; OPENING.TXT does not explain it, and
     * outside that range it is unverified (the sun is at most 12 px).
     */
    static int sunFrame(int f, int frames) {
        return Math.floorMod(Math.floorDiv(f - 110, 21), Math.max(1, frames));
    }

    /** The chart's x offset at frame {@code f}. */
    static int chartOffset(int chartW, int f) {
        return Math.max(0, (chartW - W) - f);
    }

    /**
     * One chart frame (see the class comment for the rules).
     *
     * @param fb The 320x200 RGB canvas, overwritten.
     * @param a The assets.
     * @param s The script.
     * @param f The chart frame (the caller caps it at the freeze).
     */
    static void paintChart(int[] fb, Assets a, ClassicOpeningScript s, int f) {
        System.arraycopy(a.bord, 0, fb, 0, W * H);
        final int ox = chartOffset(a.chartW, f);
        for (int y = 0; y < CHART_ROWS; y++) {
            System.arraycopy(a.chart, y * a.chartW + ox, fb, (CHART_TOP + y) * W, W);
        }
        final int clipTop = CHART_TOP, clipBottom = CHART_TOP + CHART_ROWS;
        if (f < s.pathLength() && a.ship.length > 0) {
            final int i = Math.max(0, f - 1);
            blit(fb, a.ship[Math.floorMod(f, a.ship.length)],
                 s.pathX(i) + SHIP_DX - ox, s.pathY(i) + SHIP_DY,
                 clipTop, clipBottom);
        }
        final List<ClassicOpeningScript.Anim> anims = s.anims;
        for (int n = 0; n < anims.size(); n++) {
            final ClassicOpeningScript.Anim e = anims.get(n);
            if (f < e.start || superseded(anims, n, f)) continue;
            final Sprite[] fr = a.series[e.series];
            if (fr.length == 0) continue;
            int idx = f - e.start;
            if (e.repeats == 0) {
                idx = Math.min(idx, fr.length - 1);
            } else if (idx >= fr.length * (e.repeats + 1)) {
                continue;                            // played out: gone
            } else {
                idx %= fr.length;
            }
            if (e.series == ClassicOpeningScript.SERIES_SUN) idx = sunFrame(f, fr.length);
            final Sprite sp = fr[idx];
            blit(fb, sp, sp.x + e.baseX - ox, sp.y, clipTop, clipBottom);
        }
        for (ClassicOpeningScript.Credit c : s.credits) {
            if (f < c.start || f > c.end) continue;
            final Sprite[] fr = a.credits[c.series];
            if (c.sprite - 1 < fr.length) {
                final Sprite sp = fr[c.sprite - 1];
                blit(fb, sp, CREDIT_CX - sp.w / 2, CREDIT_CY - sp.h / 2, 0, H);
            }
            break;                                   // one credit at a time
        }
    }

    /** Whether a later entry of the same series has started by {@code f}. */
    private static boolean superseded(List<ClassicOpeningScript.Anim> anims,
                                      int n, int f) {
        final ClassicOpeningScript.Anim e = anims.get(n);
        for (ClassicOpeningScript.Anim o : anims) {
            if (o != e && o.series == e.series && o.start > e.start && f >= o.start) {
                return true;
            }
        }
        return false;
    }

    /** Draw the opaque pixels of {@code s} at (x0, y0), rows clipped. */
    static void blit(int[] fb, Sprite s, int x0, int y0, int clipTop, int clipBottom) {
        for (int y = 0; y < s.h; y++) {
            final int sy = y0 + y;
            if (sy < clipTop || sy >= clipBottom || sy < 0 || sy >= H) continue;
            for (int x = 0; x < s.w; x++) {
                final int sx = x0 + x;
                if (sx < 0 || sx >= W) continue;
                final int p = s.argb[y * s.w + x];
                if ((p >>> 24) != 0) fb[sy * W + sx] = p & 0xFFFFFF;
            }
        }
    }


    // The whole intro picture

    /**
     * The live title screen (OPENMENU.PIK and the menu, first item barred)
     * as pixels: the intro's last picture, so ending it is no jump.
     *
     * @param ma The menu assets.
     * @param items The title items.
     * @return 320x200 RGB.
     */
    static int[] titlePixels(ClassicMainMenuPanel.MenuAssets ma, List<String> items) {
        final BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = img.createGraphics();
        try {
            ClassicMainMenuPanel.paintTitleScreen(g, ma,
                ClassicMainMenuPanel.TITLE_LINE, items, 0);
        } finally {
            g.dispose();
        }
        final int[] p = pixels(img).clone();
        for (int i = 0; i < p.length; i++) p[i] &= 0xFFFFFF;   // RGB only
        return p;
    }

    /** @return The live pixel array of a TYPE_INT_RGB image. */
    static int[] pixels(BufferedImage img) {
        return ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
    }

    /**
     * Render one intro picture.
     *
     * @param out The 320x200 RGB canvas, overwritten.
     * @param st The timeline state.
     * @param a The opening assets, or null.
     * @param s The script, or null.
     * @param title The title picture ({@link #titlePixels}), or null.
     */
    static void frame(int[] out, ClassicIntroTimeline.State st, Assets a,
                      ClassicOpeningScript s, int[] title) {
        switch (st.phase) {
        case EMBLEM: case FADE:
            ClassicEmblem.paint(out, st.emblemFrame, st.subtitleK, st.level);
            break;
        case CHART:
            if (a != null && s != null) {
                paintChart(out, a, s, st.chartFrame);
            } else {
                Arrays.fill(out, 0, W * H, 0);
            }
            break;
        case DONE:
            if (title != null) {
                System.arraycopy(title, 0, out, 0, W * H);
            } else {
                Arrays.fill(out, 0, W * H, 0);
            }
            break;
        case BLACK: default:
            Arrays.fill(out, 0, W * H, 0);
            break;
        }
    }
}
