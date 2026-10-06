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

import java.util.Arrays;
import java.util.HashMap;
import java.util.function.IntConsumer;
import java.util.function.ToIntFunction;

import net.sf.freecol.common.model.TileType;


/**
 * The original's map terrain as <b>palette indices</b>, one 16x16 cell at a
 * time (M1c design 10 §6, item W6a): the unexplored tile, its fog fringe,
 * the side-mask blend between neighbours and the feature overlays.  Pure
 * rules over the index sheets ({@link ClassicIndexSheet}): no Swing, no
 * resource manager; {@link ClassicTerrainLayer} draws the result through
 * the game palette ({@link ClassicGamePalette}).
 *
 * <p>Notation: {@code type(t)} is the client's type of an explored tile and
 * the true type of an unexplored one ({@link ClassicTerrainOracle}, null
 * when unknown); {@code spr(type)} is its TERRAIN.SS frame (the pack's
 * alias table); {@code blendSpr(type)} is {@code spr(type)} with
 * {@link #BLEND_BASE} (T008 draws T001 into its neighbours, fog-start §3.3);
 * water never bleeds into land, everything else does.  The side masks are
 * the 15 opaque pixels of {@code PHYS0.SS.104-107} (N, E, S, W), 3 px deep
 * along their edge; a side copies another sprite's pixels <b>at the same
 * in-tile positions</b>.
 *
 * <ul>
 *   <li><b>Unexplored cell:</b> {@code PHYS0.SS.148} (TERRAIN.SS.010 + 2:
 *       indices 60/61/62), then for each side whose neighbour is
 *       <em>explored</em> its fringe: the neighbour's {@code blendSpr} if
 *       it bleeds into this tile's true type, else this tile's own
 *       (unexplored land next to explored water shows its own land; an
 *       unknown own type takes the neighbour's).  A diagonal neighbour adds
 *       nothing, and nothing else is drawn on a dark cell.</li>
 *   <li><b>Explored cell:</b> {@code TERRAIN[spr(type)]}, then for each side
 *       whose neighbour's type is known (explored or the true type of the
 *       ring) and bleeds in: that neighbour's {@code blendSpr} at the
 *       side's mask, skipped when an explored neighbour's sprite is the
 *       tile's own (an unexplored one's is copied all the same:
 *       {@link #DARK_SIDES_ALWAYS}; either is a no-op except at the 4 px
 *       where two masks overlap).  Then the overlays
 *       ({@link ClassicTileArt#overlayFrames}): PHYS0 indices, 0xFD
 *       transparent, index 0 drawn (black).  An explored tile is never
 *       darkened: the fringe lies inside the unexplored tile only.</li>
 * </ul>
 *
 * <p><b>Least knowledge</b> (design §5): the composer asks the type of an
 * unexplored tile only within Chebyshev distance 1 of an explored one: the
 * own type of a dark cell only when an explored orthogonal neighbour is
 * water (the only case its fringe depends on it), the neighbours of an
 * explored cell, and the overlays' connectivity around an explored cell.
 *
 * <p>W6b adds the coast quarters and the beach corners after the blends of
 * explored water.  Immutable apart from a sprite cache; EDT only (the
 * cache is not synchronized).
 */
final class ClassicTerrainComposer {

    /** A cell's size in native pixels. */
    static final int CELL = 16;

    /** {@code PHYS0.SS}: the unexplored tile (TERRAIN.SS.010 + 2). */
    static final int DARK = 148;

    /** {@code PHYS0.SS}: the first side mask; 104 + d for N, E, S, W. */
    static final int MASK_FIRST = 104;

    /** The sides N, E, S, W: neighbour offsets {dx, dy}, by side number. */
    static final int[][] SIDES = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };

    /**
     * The order the side masks are copied in.  It decides only the 4
     * pixels where two adjacent masks overlap (design 10 F8, open item O3);
     * N, E, S, W is the fallback.
     */
    static final int[] SIDE_ORDER = { 0, 1, 2, 3 };

    /**
     * An explored tile's side toward an <b>unexplored</b> neighbour is
     * copied even when the neighbour's sprite is the tile's own; toward an
     * explored one it is skipped.  It shows only at the 4 px where two
     * masks overlap (F8).  Pinned by FS #1042: the tropical forest (42,46)
     * shows its own T005 at (2,15), the S/W overlap, though its S blend is
     * T002 (11/11 px) and its W neighbour (41,46), still dark, is the same
     * forest; the sea lane (47,42) of FS #19-#1042 shows its S neighbour's
     * ocean there, its W neighbour (46,42) being explored sea lane
     * ({@code ClassicTerrainGoldenTest}).  I: the original compares the
     * terrain it shows, "dark", with its own, and copies the true one.
     */
    static final boolean DARK_SIDES_ALWAYS = true;

    /**
     * The sprite a TERRAIN.SS frame shows as a fringe or a blend, where it
     * differs from itself: T008 (sand with cacti) shows T001 (fog-start
     * §3.3, verified on 4 sides).  No FreeCol type draws T008 yet (O6).
     */
    static final java.util.Map<Integer, Integer> BLEND_BASE = java.util.Map.of(8, 1);

    /** The TERRAIN.SS frame of a type without an alias: water, land. */
    static final int UNKNOWN_WATER = 10, UNKNOWN_LAND = 2;

    /** TERRAIN.SS frames needed, and PHYS0.SS frames needed. */
    static final int TERRAIN_FRAMES = 12, PHYS0_FRAMES = DARK + 1;


    /**
     * What the composer reads of the map.  Implemented by the viewer (the
     * explored state as shown, the oracle for the fog ring) and by tests.
     */
    interface TerrainSource {

        /**
         * @param x A column.
         * @param y A row.
         * @return Whether the tile exists.
         */
        boolean onMap(int x, int y);

        /**
         * @param x A column.
         * @param y A row.
         * @return Whether the tile is drawn explored (false off the map).
         */
        boolean explored(int x, int y);

        /**
         * @param x A column.
         * @param y A row.
         * @return The client's type of an explored tile, the true type of
         *     an unexplored one (null: unknown).
         */
        TileType type(int x, int y);

        /**
         * The overlay frames of an explored tile
         * ({@link ClassicTileArt#overlayFrames}), in drawing order.
         *
         * @param x A column.
         * @param y A row.
         * @param frames Receives each {@code PHYS0.SS} frame.
         */
        void overlays(int x, int y, IntConsumer frames);
    }


    /** The overlays (W6b: and the coast pieces). */
    private final ClassicIndexSheet phys;

    /** Tile type id -> TERRAIN.SS frame, or -1. */
    private final ToIntFunction<String> alias;

    /** The cell offsets ({@code y * 16 + x}) of each side mask's pixels. */
    private final int[][] masks = new int[4][];

    /** {@code PHYS0.SS.148}, 256 indices. */
    private final byte[] dark;

    /** The TERRAIN.SS frames, 256 indices each. */
    private final byte[][] bases = new byte[TERRAIN_FRAMES][];

    /** spr() by tile type id. */
    private final HashMap<String, Integer> sprites = new HashMap<>();

    /** {@link #DARK_SIDES_ALWAYS}, or its alternative (the golden test's pin). */
    private final boolean darkSidesAlways;

    /** {@link #SIDE_ORDER}, or another order (the golden test's candidates, O3). */
    private final int[] sideOrder;


    /**
     * @param terrain {@code TERRAIN.SS} as indices (at least 12 frames of
     *     16x16).
     * @param phys {@code PHYS0.SS} as indices (at least 149 frames; 104-107
     *     and 148 16x16).
     * @param alias The TERRAIN.SS frame of a tile type id, -1 for none
     *     ({@link ClassicPackFiles#terrainSpriteFor}).
     * @exception IllegalArgumentException if a sheet lacks a frame.
     */
    ClassicTerrainComposer(ClassicIndexSheet terrain, ClassicIndexSheet phys,
                           ToIntFunction<String> alias) {
        this(terrain, phys, alias, DARK_SIDES_ALWAYS, SIDE_ORDER);
    }

    /**
     * A composer with a choice of the pinned rules, for the golden test.
     *
     * @param terrain {@code TERRAIN.SS} as indices.
     * @param phys {@code PHYS0.SS} as indices.
     * @param alias The TERRAIN.SS frame of a tile type id, -1 for none.
     * @param darkSidesAlways {@link #DARK_SIDES_ALWAYS} or not.
     * @param sideOrder The side order, a permutation of 0-3 ({@link #SIDE_ORDER}).
     * @exception IllegalArgumentException if a sheet lacks a frame.
     */
    ClassicTerrainComposer(ClassicIndexSheet terrain, ClassicIndexSheet phys,
                           ToIntFunction<String> alias, boolean darkSidesAlways,
                           int[] sideOrder) {
        this.darkSidesAlways = darkSidesAlways;
        this.sideOrder = sideOrder.clone();
        if (terrain == null || phys == null || alias == null) {
            throw new IllegalArgumentException("sheets and alias needed");
        }
        if (terrain.size() < TERRAIN_FRAMES || phys.size() < PHYS0_FRAMES) {
            throw new IllegalArgumentException("short sheets: " + terrain + ", " + phys);
        }
        this.phys = phys;
        this.alias = alias;
        for (int f = 0; f < TERRAIN_FRAMES; f++) this.bases[f] = full(terrain, f);
        this.dark = full(phys, DARK);
        for (int d = 0; d < 4; d++) {
            final byte[] m = full(phys, MASK_FIRST + d);
            int n = 0;
            final int[] k = new int[CELL * CELL];
            for (int i = 0; i < m.length; i++) {
                if ((m[i] & 0xFF) != ClassicIndexSheet.TRANSPARENT) k[n++] = i;
            }
            this.masks[d] = Arrays.copyOf(k, n);
        }
    }

    /** A frame that must be a full cell. */
    private static byte[] full(ClassicIndexSheet sheet, int f) {
        if (sheet.width(f) != CELL || sheet.height(f) != CELL) {
            throw new IllegalArgumentException(sheet.name() + "." + f + " is "
                + sheet.width(f) + "x" + sheet.height(f) + ", not 16x16");
        }
        return sheet.pixels(f);
    }


    /**
     * The TERRAIN.SS frame of a type: the alias table's, else the
     * fallback for water or land.
     *
     * @param type The type.
     * @return The frame, or -1 for null.
     */
    int spr(TileType type) {
        if (type == null) return -1;
        Integer s = this.sprites.get(type.getId());
        if (s == null) {
            int a = this.alias.applyAsInt(type.getId());
            if (a < 0 || a >= TERRAIN_FRAMES) {
                a = (type.isWater()) ? UNKNOWN_WATER : UNKNOWN_LAND;
            }
            s = a;
            this.sprites.put(type.getId(), s);
        }
        return s;
    }

    /**
     * The sprite a type shows in a fringe or a blend.
     *
     * @param type The type.
     * @return {@code BLEND_BASE} applied to {@link #spr}, -1 for null.
     */
    int blendSpr(TileType type) {
        final int s = spr(type);
        return BLEND_BASE.getOrDefault(s, s);
    }

    /**
     * Whether a neighbour's terrain bleeds into a tile's: water never
     * bleeds into land, everything else does.
     *
     * @param from The neighbour's type.
     * @param into The tile's type.
     * @return True if it bleeds.
     */
    static boolean bleeds(TileType from, TileType into) {
        return !(from.isWater() && !into.isWater());
    }

    /**
     * @param d A side.
     * @return The cell offsets of its mask pixels (shared, do not modify).
     */
    int[] mask(int d) {
        return this.masks[d];
    }


    /**
     * Compose one cell.
     *
     * @param src The map.
     * @param x The tile's column.
     * @param y The tile's row.
     * @param dst The index buffer.
     * @param off The offset of the cell's top-left pixel in {@code dst}.
     * @param stride The buffer's row length.
     * @return Whether the cell holds a cycling index (120-127).
     */
    boolean composeCell(TerrainSource src, int x, int y, byte[] dst, int off,
                        int stride) {
        if (!src.onMap(x, y)) {
            for (int j = 0; j < CELL; j++) Arrays.fill(dst, off + j * stride,
                                                       off + j * stride + CELL, (byte)0);
            return false;
        }
        if (src.explored(x, y)) {
            composeExplored(src, x, y, dst, off, stride);
        } else {
            composeDark(src, x, y, dst, off, stride);
        }
        final int lo = ClassicGamePalette.CYCLE_FIRST;
        final int hi = lo + ClassicGamePalette.CYCLE_COUNT;
        for (int j = 0; j < CELL; j++) {
            final int row = off + j * stride;
            for (int i = 0; i < CELL; i++) {
                final int v = dst[row + i] & 0xFF;
                if (v >= lo && v < hi) return true;
            }
        }
        return false;
    }

    /** An unexplored cell: the dark tile and its fringes. */
    private void composeDark(TerrainSource src, int x, int y, byte[] dst,
                             int off, int stride) {
        copy(this.dark, dst, off, stride);
        TileType own = null;
        boolean ownAsked = false;
        for (int d : this.sideOrder) {
            final int nx = x + SIDES[d][0], ny = y + SIDES[d][1];
            // Only an explored neighbour opens a side (and only then is
            // this tile in the fog ring: least knowledge).
            if (!src.onMap(nx, ny) || !src.explored(nx, ny)) continue;
            final TileType tn = src.type(nx, ny);
            if (tn == null) continue;
            int s = blendSpr(tn);
            if (tn.isWater()) {
                // Water bleeds only into water: dark land shows its own.
                if (!ownAsked) {
                    own = src.type(x, y);
                    ownAsked = true;
                }
                if (own != null && !own.isWater()) s = blendSpr(own);
            }
            copyMask(d, s, dst, off, stride);
        }
    }

    /** An explored cell: its base, the side-mask blends and the overlays. */
    private void composeExplored(TerrainSource src, int x, int y, byte[] dst,
                                 int off, int stride) {
        final TileType t = src.type(x, y);
        final int base = (t == null) ? UNKNOWN_WATER : spr(t);
        copy(this.bases[base], dst, off, stride);
        if (t != null) {
            for (int d : this.sideOrder) {
                final int nx = x + SIDES[d][0], ny = y + SIDES[d][1];
                if (!src.onMap(nx, ny)) continue;
                final TileType tn = src.type(nx, ny);
                if (tn == null) continue;   // unknown: no blend (F-W6d-MP)
                final int s = blendSpr(tn);
                if (!bleeds(tn, t)) continue;
                // The own sprite again: a no-op but at the overlaps, and
                // copied toward a dark neighbour (DARK_SIDES_ALWAYS).
                if (s == base && !(this.darkSidesAlways && !src.explored(nx, ny))) continue;
                copyMask(d, s, dst, off, stride);
            }
        }
        // W6b: the coast quarters and the beach corner of explored water.
        src.overlays(x, y, f -> overlay(f, dst, off, stride));
    }

    /** Copy a full 16x16 frame into the cell. */
    private static void copy(byte[] frame, byte[] dst, int off, int stride) {
        for (int j = 0; j < CELL; j++) {
            System.arraycopy(frame, j * CELL, dst, off + j * stride, CELL);
        }
    }

    /** Copy a TERRAIN.SS frame's pixels at a side mask. */
    private void copyMask(int d, int sprite, byte[] dst, int off, int stride) {
        final byte[] from = this.bases[sprite];
        for (int k : this.masks[d]) {
            dst[off + (k >> 4) * stride + (k & 15)] = from[k];
        }
    }

    /** Copy a PHYS0.SS overlay's opaque pixels (from the cell's corner). */
    private void overlay(int f, byte[] dst, int off, int stride) {
        if (f < 0 || f >= this.phys.size()) return;
        final int w = Math.min(CELL, this.phys.width(f));
        final int h = Math.min(CELL, this.phys.height(f));
        final int fw = this.phys.width(f);
        final byte[] px = this.phys.pixels(f);
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                final byte v = px[j * fw + i];
                if ((v & 0xFF) != ClassicIndexSheet.TRANSPARENT) {
                    dst[off + j * stride + i] = v;
                }
            }
        }
    }
}
