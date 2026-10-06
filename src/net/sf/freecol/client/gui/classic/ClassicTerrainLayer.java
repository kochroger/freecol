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
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.util.logging.Logger;


/**
 * The map's terrain on the HUD view (15x12 cells, 240x192 native pixels)
 * as one buffer of <b>palette indices</b>, composed by
 * {@link ClassicTerrainComposer} and drawn through the game palette at the
 * current cycling phase (M1c design 10 §2, §6.2; item W6a).
 *
 * <p>The buffer is wrapped in one raster that 8 images share, one per
 * cycling phase ({@link ClassicGamePalette#colorModel}): a palette step
 * (item W6c) is a swap of the image drawn, nothing is composed again.
 * {@link #paint} composes only the cells the clip touches, rounded out to
 * whole cells, and draws exactly those cells scaled by the HUD's integer
 * scale with nearest neighbour (dst = src x S, never a fractional source:
 * Critic 7).  Nothing is cached between paints, so nothing goes stale.
 *
 * <p>Units, settlements and the cursor stay RGBA, drawn over it by the map
 * viewer.  EDT only.
 */
final class ClassicTerrainLayer {

    private static final Logger logger = Logger.getLogger(ClassicTerrainLayer.class.getName());

    /** The view in cells, and in native pixels. */
    static final int COLS = ClassicHud.VIEW_COLS, ROWS = ClassicHud.VIEW_ROWS;
    static final int CELL = ClassicTerrainComposer.CELL;
    static final int WIDTH = COLS * CELL, HEIGHT = ROWS * CELL;

    /** The rules. */
    private final ClassicTerrainComposer composer;

    /** The game palette. */
    private final ClassicGamePalette palette;

    /** The composed indices, {@code buf[y * WIDTH + x]}. */
    private final byte[] buf;

    /** One image per cycling phase over the same raster. */
    private final BufferedImage[] views;

    /** Per cell ({@code r * COLS + c}): its last composition holds 120-127. */
    private final boolean[] cycling = new boolean[COLS * ROWS];

    /** The phase drawn from the next paint on. */
    private int phase = 0;

    /**
     * The phase the screen shows the layer at: that of its last paint, or
     * of a step with no cycling cell on the screen ({@link #markShown}).
     * The recorder names each frame's palette by it (design 10 §9.2).
     */
    private int paintedPhase = 0;


    /**
     * @param composer The rules.
     * @param palette The game palette.
     */
    ClassicTerrainLayer(ClassicTerrainComposer composer, ClassicGamePalette palette) {
        if (composer == null || palette == null) {
            throw new IllegalArgumentException("composer and palette needed");
        }
        this.composer = composer;
        this.palette = palette;
        final BufferedImage base = new BufferedImage(WIDTH, HEIGHT,
            BufferedImage.TYPE_BYTE_INDEXED, palette.colorModel(0));
        final WritableRaster raster = base.getRaster();
        this.buf = ((DataBufferByte)raster.getDataBuffer()).getData();
        this.views = new BufferedImage[palette.phaseCount()];
        for (int p = 0; p < this.views.length; p++) {
            this.views[p] = new BufferedImage(palette.colorModel(p), raster, false, null);
        }
    }

    /**
     * The layer of a pack: its TERRAIN and PHYS0 index sheets, its alias
     * table and its game palette.
     *
     * @param pack The pack, or null.
     * @return The layer, or null when the pack lacks the index pipeline
     *     (the map then draws the RGBA fallback; {@link
     *     ClassicPackFiles#indexStatus} has logged it).
     */
    static ClassicTerrainLayer create(ClassicPackFiles pack) {
        if (pack == null) return null;
        final ClassicIndexSheet t = pack.indexSheet(ClassicPackFiles.TERRAIN_SS);
        final ClassicIndexSheet p = pack.indexSheet(ClassicPackFiles.PHYS0_SS);
        final ClassicGamePalette pal = ClassicGamePalette.of(pack);
        if (t == null || p == null || pal == null) return null;
        try {
            return new ClassicTerrainLayer(
                new ClassicTerrainComposer(t, p, pack::terrainSpriteFor), pal);
        } catch (IllegalArgumentException e) {
            logger.warning("ClassicTerrainLayer: unusable index sheets, the map"
                + " falls back to the RGBA tiles: " + e.getMessage());
            return null;
        }
    }


    /** @return The rules. */
    ClassicTerrainComposer composer() {
        return this.composer;
    }

    /** @return The game palette. */
    ClassicGamePalette palette() {
        return this.palette;
    }

    /** @return The cycling phase drawn. */
    int phase() {
        return this.phase;
    }

    /**
     * Draw at another cycling phase from the next paint on (item W6c).
     *
     * @param p The phase (taken modulo the phase count).
     */
    void setPhase(int p) {
        this.phase = this.palette.normalise(p);
    }

    /** @return The phase the screen shows the layer at ({@link #paintedPhase}). */
    int paintedPhase() {
        return this.paintedPhase;
    }

    /**
     * The screen shows the current phase without a paint: no cell of the
     * last composition holds a cycling index, so every phase looks alike
     * (a palette step with nothing to repaint, item W6c).
     */
    void markShown() {
        this.paintedPhase = this.phase;
    }

    /**
     * The recorder's index hint (design 10 §9.2.4): a copy of the buffer
     * with every index outside the cycle as 0.  At some phases a cycling
     * pixel has the colour of 56 or 59, so the colour alone cannot name it.
     *
     * @param out At least {@code WIDTH * HEIGHT} bytes, {@code y * WIDTH + x}.
     * @return The pixels holding a cycling index.
     */
    int indexHint(byte[] out) {
        final int first = this.palette.cycle().first;
        final int count = this.palette.cycle().count;
        int n = 0;
        for (int i = 0; i < this.buf.length; i++) {
            final int v = (this.buf[i] & 0xFF) - first;
            if (v >= 0 && v < count) {
                out[i] = this.buf[i];
                n++;
            } else {
                out[i] = 0;
            }
        }
        return n;
    }

    /**
     * Compose the cells a clip touches and draw them.
     *
     * @param g The viewer's graphics (cell (0,0) at its origin).
     * @param src The map.
     * @param vx The view's first column.
     * @param vy The view's first row.
     * @param clip The area to paint (viewer pixels), or null for all.
     * @param s The HUD scale.
     * @return The cells painted {c0, r0, c1, r1} (exclusive ends), or null
     *     when the clip misses the view.
     */
    int[] paint(Graphics2D g, ClassicTerrainComposer.TerrainSource src,
                int vx, int vy, Rectangle clip, int s) {
        final int[] c = cells(clip, s);
        if (c == null) return null;
        compose(src, vx, vy, c);
        final int cs = CELL * s;
        g.drawImage(this.views[this.phase],
                    c[0] * cs, c[1] * cs, c[2] * cs, c[3] * cs,
                    c[0] * CELL, c[1] * CELL, c[2] * CELL, c[3] * CELL, null);
        this.paintedPhase = this.phase;
        return c;
    }

    /**
     * The whole cells a clip touches.
     *
     * @param clip The clip (viewer pixels), or null for the whole view.
     * @param s The HUD scale.
     * @return {c0, r0, c1, r1} (exclusive ends), or null for none.
     */
    static int[] cells(Rectangle clip, int s) {
        if (clip == null) return new int[] { 0, 0, COLS, ROWS };
        final int cs = CELL * Math.max(1, s);
        final int c0 = Math.max(0, Math.floorDiv(clip.x, cs));
        final int r0 = Math.max(0, Math.floorDiv(clip.y, cs));
        final int c1 = Math.min(COLS, -Math.floorDiv(-(clip.x + clip.width), cs));
        final int r1 = Math.min(ROWS, -Math.floorDiv(-(clip.y + clip.height), cs));
        return (clip.width <= 0 || clip.height <= 0 || c0 >= c1 || r0 >= r1) ? null
            : new int[] { c0, r0, c1, r1 };
    }

    /**
     * Compose cells into the buffer.
     *
     * @param src The map.
     * @param vx The view's first column.
     * @param vy The view's first row.
     * @param c The cells {c0, r0, c1, r1}.
     */
    void compose(ClassicTerrainComposer.TerrainSource src, int vx, int vy, int[] c) {
        for (int r = c[1]; r < c[3]; r++) {
            for (int col = c[0]; col < c[2]; col++) {
                this.cycling[r * COLS + col] = this.composer.composeCell(src,
                    vx + col, vy + r, this.buf, r * CELL * WIDTH + col * CELL, WIDTH);
            }
        }
    }

    /**
     * @param x A native x in the view (0-239).
     * @param y A native y (0-191).
     * @return The last composed index there.
     */
    int index(int x, int y) {
        return this.buf[y * WIDTH + x] & 0xFF;
    }

    /**
     * @param col A cell column.
     * @param row A cell row.
     * @return Whether the cell's last composition holds a cycling index.
     */
    boolean cycles(int col, int row) {
        return this.cycling[row * COLS + col];
    }

    /**
     * The cells whose last composition holds a cycling index, as one box:
     * what a palette step must repaint (item W6c).
     *
     * @return {c0, r0, c1, r1} (exclusive ends), or null for none.
     */
    int[] cyclingCells() {
        int c0 = COLS, r0 = ROWS, c1 = -1, r1 = -1;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (!this.cycling[r * COLS + c]) continue;
                c0 = Math.min(c0, c);
                r0 = Math.min(r0, r);
                c1 = Math.max(c1, c + 1);
                r1 = Math.max(r1, r + 1);
            }
        }
        return (c1 < 0) ? null : new int[] { c0, r0, c1, r1 };
    }
}
