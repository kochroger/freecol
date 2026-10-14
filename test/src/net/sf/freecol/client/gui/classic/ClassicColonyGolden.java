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
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;


/**
 * The golden tests' tools for the colony screen against clip 020 (build
 * spec M4, {@code playthrough-3-analysis}): the clip's frames, the
 * original arrow found in a frame (so its pixels are named, not counted),
 * the analysis' overlay models ({@code support/layout/ov_*.txt}) read as
 * masks of what the original draws over a region that a later step of M4
 * draws, and the count of differing pixels in a region.  The frames and
 * the models live with the recordings ({@code -Dclassic.clips}), the
 * sprites in the pack; both are git-ignored, so a test without them
 * skips.
 */
final class ClassicColonyGolden {

    /** The clip's folder under the recordings. */
    static final String CLIP = "playthrough-3";

    /** The overlay models' folder under the recordings. */
    static final String MODELS = "playthrough-3-analysis/support/layout";

    /** The recordings, or null. */
    final File recordings;

    /** The pack. */
    final ClassicPackFiles pack;

    private ClassicColonyGolden(File recordings, ClassicPackFiles pack) {
        this.recordings = recordings;
        this.pack = pack;
    }

    /**
     * The tools, or null (with a note) without the recordings or the pack.
     *
     * @param who The test, for the note.
     * @return The tools, or null.
     */
    static ClassicColonyGolden open(String who) {
        final String p = System.getProperty(ClassicTerrainGoldenTest.CLIPS_PROPERTY);
        final File d = (p == null || p.isBlank()) ? null : new File(p);
        if (d == null || !new File(d, CLIP).isDirectory()) {
            System.err.println(who + ": golden check skipped, no recordings (-D"
                + ClassicTerrainGoldenTest.CLIPS_PROPERTY + ")");
            return null;
        }
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        if (pack == null || pack.font(ClassicFont.TINY) == null
            || pack.gamePalette() == null) {
            System.err.println(who + ": golden check skipped, no pack (ant classic-assets)");
            return null;
        }
        return new ClassicColonyGolden(d, pack);
    }

    /**
     * A frame of clip 020 (the last PNG at or before it).
     *
     * @param n The frame.
     * @return It, 320x200.
     * @exception IOException if it cannot be read.
     */
    BufferedImage frame(int n) throws IOException {
        final File f = ClassicTerrainGoldenTest.frameFile(new File(this.recordings, CLIP), n);
        if (f == null) throw new IOException("no frame " + n);
        return ImageIO.read(f);
    }

    /** @return A frame's pixels, 0xRRGGBB, y * 320 + x. */
    static int[] pixels(BufferedImage img) {
        final int[] p = img.getRGB(0, 0, 320, 200, null, 0, 320);
        for (int i = 0; i < p.length; i++) p[i] &= 0xFFFFFF;
        return p;
    }

    /** A 320x200 mask. */
    static final class Mask {

        final boolean[] on = new boolean[320 * 200];

        void set(int x, int y) {
            if (x >= 0 && y >= 0 && x < 320 && y < 200) this.on[y * 320 + x] = true;
        }

        void rect(int x0, int y0, int x1, int y1) {
            for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) set(x, y);
        }

        void rect(Rectangle r) {
            rect(r.x, r.y, r.x + r.width - 1, r.y + r.height - 1);
        }

        /** The opaque pixels of a sprite at (x, y). */
        void sprite(BufferedImage s, int x, int y) {
            if (s == null) return;
            for (int sy = 0; sy < s.getHeight(); sy++) {
                for (int sx = 0; sx < s.getWidth(); sx++) {
                    if ((s.getRGB(sx, sy) >>> 24) > 128) set(x + sx, y + sy);
                }
            }
        }

        boolean get(int x, int y) {
            return this.on[y * 320 + x];
        }

        int count() {
            int n = 0;
            for (boolean b : this.on) if (b) n++;
            return n;
        }
    }

    /**
     * Where the original arrow (CURSOR.SS.000) is in a frame: every
     * opaque pixel of the sprite equal to the frame there.
     *
     * @param frame The frame.
     * @return Its pixels as a mask (empty when the arrow is not found
     *     whole, e.g. at the edge).
     */
    Mask pointer(BufferedImage frame) {
        final Mask m = new Mask();
        // The arrow as the game draws it (its two corner key pixels cleared).
        final BufferedImage s = ClassicMainMenuPanel.cursorSprite(
            this.pack.image(ClassicPackFiles.ssKey("CURSOR.SS.000")));
        if (s == null) return m;
        final int[] f = pixels(frame);
        final List<int[]> opaque = new ArrayList<>();
        for (int y = 0; y < s.getHeight(); y++) {
            for (int x = 0; x < s.getWidth(); x++) {
                final int c = s.getRGB(x, y);
                if ((c >>> 24) > 128) opaque.add(new int[] { x, y, c & 0xFFFFFF });
            }
        }
        for (int y = 0; y + s.getHeight() <= 200; y++) {
            next: for (int x = 0; x + s.getWidth() <= 320; x++) {
                for (int[] o : opaque) {
                    if (f[(y + o[1]) * 320 + x + o[0]] != o[2]) continue next;
                }
                m.sprite(s, x, y);
            }
        }
        return m;
    }

    /**
     * What an overlay model draws over its region, as a mask: every
     * sprite, icon, figure (with its shadow), text, label, frame and rect,
     * and the masks that lie inside the region (the arrow, a flag box the
     * model does not draw); not its backgrounds (the tiled ground, the
     * band picture) and not the masks that only bound its region (they
     * reach out of it: "the rest of the screen").
     *
     * @param name The model's file name, e.g. {@code ov_base_2260_buildings.txt},
     *     or null.
     * @param region The model's region.
     * @return The mask, or null when there is no such model.
     * @exception IOException if it cannot be read.
     */
    Mask content(String name, Rectangle region) throws IOException {
        if (name == null) return null;
        final File f = new File(new File(this.recordings, MODELS), name);
        if (!f.isFile()) return null;
        final Mask m = new Mask();
        final ClassicFont tiny = this.pack.font(ClassicFont.TINY);
        for (String raw : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
            final String l = raw.trim();
            if (l.isEmpty() || l.startsWith("#")) continue;
            final String[] q = l.split("\\s+");
            switch (q[0]) {
            case "tile":
                break;                                   // a background
            case "spr":
                if (q[1].contains(":")) break;           // the band picture
                m.sprite(sprite(q[1]), i(q[2]), i(q[3]));
                break;
            case "icons":
                for (int k = 3; k < q.length; k++) m.sprite(sprite(q[1]), i(q[k]), i(q[2]));
                break;
            case "smear":
                for (int x = i(q[2]); x <= i(q[3]); x++) m.sprite(sprite(q[1]), x, i(q[4]));
                break;
            case "fig": {
                final BufferedImage s = sprite(q[1]);
                m.sprite(s, i(q[2]) - 2, i(q[3]));
                m.sprite(s, i(q[2]), i(q[3]));
                break;
            }
            case "rect":
                m.rect(i(q[1]), i(q[2]), i(q[3]), i(q[4]));
                break;
            case "mask": {
                final Rectangle r = new Rectangle(i(q[1]), i(q[2]),
                    i(q[3]) - i(q[1]) + 1, i(q[4]) - i(q[2]) + 1);
                if (region.contains(r)) m.rect(r);
                break;
            }
            case "frame": {
                final int x0 = i(q[1]), y0 = i(q[2]), x1 = i(q[3]), y1 = i(q[4]);
                m.rect(x0, y0, x1, y0);
                m.rect(x0, y1, x1, y1);
                m.rect(x0, y0, x0, y1);
                m.rect(x1, y0, x1, y1);
                break;
            }
            case "text": {
                final String s = textOf(l, 4);
                glyphs(m, tiny, s, i(q[1]), i(q[2]));
                break;
            }
            case "label": {
                final String s = textOf(l, 3);
                final int w = tiny.stringWidth(s);
                m.rect(i(q[1]), i(q[2]), i(q[1]) + w, i(q[2]) + 6);
                break;
            }
            default:
                throw new IOException(name + ": op " + q[0]);
            }
        }
        return m;
    }

    /** A model line's text: everything after its first {@code skip} words. */
    private static String textOf(String line, int skip) {
        String s = line;
        for (int k = 0; k < skip; k++) {
            s = s.substring(s.indexOf(' ') + 1).replaceFirst("^\\s+", "");
        }
        // The models write the coin as '¤', FONTTINY draws it as '$'.
        return s.replace('¤', '$');
    }

    /** The glyph pixels of a FONTTINY text at (x, y). */
    private static void glyphs(Mask m, ClassicFont tiny, String s, int x, int y) {
        final BufferedImage img = new BufferedImage(320, 200, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g = img.createGraphics();
        tiny.draw(g, s, x, y, ClassicFont.colours(0xFFFFFF));
        g.dispose();
        for (int yy = 0; yy < 200; yy++) {
            for (int xx = 0; xx < 320; xx++) {
                if ((img.getRGB(xx, yy) >>> 24) != 0) m.set(xx, yy);
            }
        }
    }

    private BufferedImage sprite(String name) {
        return this.pack.image(ClassicPackFiles.ssKey(name));
    }

    private static int i(String s) {
        return Integer.parseInt(s);
    }

    /** One region's comparison. */
    static final class Diff {

        final String name;

        /** Pixels compared, and how many differ. */
        int compared = 0, differing = 0;

        /** Pixels left out: the arrow's, and what a later step draws. */
        int pointer = 0, masked = 0;

        /** The first differing pixels, "(x,y) ours/theirs". */
        final StringBuilder first = new StringBuilder();

        Diff(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return String.format("%-14s compared %5d px, differing %d (left out: arrow %d,"
                + " later steps %d)%s", this.name, this.compared, this.differing,
                this.pointer, this.masked,
                (this.first.length() == 0) ? "" : " first:" + this.first);
        }
    }

    /**
     * Compare a region of ours with the frame, pixel for pixel.
     *
     * @param name The region's name.
     * @param ours Our pixels.
     * @param theirs The frame's.
     * @param region The region.
     * @param pointer The arrow's pixels (left out), or null.
     * @param later What a later step draws there (left out), or null.
     * @return The result.
     */
    static Diff compare(String name, int[] ours, int[] theirs, Rectangle region,
                        Mask pointer, Mask later) {
        final Diff d = new Diff(name);
        for (int y = region.y; y < region.y + region.height; y++) {
            for (int x = region.x; x < region.x + region.width; x++) {
                if (pointer != null && pointer.get(x, y)) {
                    d.pointer++;
                    continue;
                }
                if (later != null && later.get(x, y)) {
                    d.masked++;
                    continue;
                }
                d.compared++;
                final int i = y * 320 + x;
                if (ours[i] != theirs[i]) {
                    d.differing++;
                    if (d.first.length() < 160) {
                        d.first.append(String.format(" (%d,%d) %06X/%06X", x, y,
                                                     ours[i], theirs[i]));
                    }
                }
            }
        }
        return d;
    }
}
