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
 * water's own sprite never bleeds into land, everything else does, and
 * explored coast water bleeds its <em>land face</em> into land
 * ({@link #LAND_FACE}, W6b: the sprite of its land layer, below).  The
 * side masks are the 15 opaque pixels of {@code PHYS0.SS.104-107} (N, E,
 * S, W), 3 px deep along their edge; a side copies another sprite's pixels
 * <b>at the same in-tile positions</b>.
 *
 * <ul>
 *   <li><b>Unexplored cell:</b> {@code PHYS0.SS.148} (TERRAIN.SS.010 + 2:
 *       indices 60/61/62), then for each side whose neighbour is
 *       <em>explored</em> its fringe: the neighbour's {@code blendSpr} if
 *       it bleeds into this tile's true type; unexplored land next to
 *       explored water shows the water's land face, which is its own land
 *       when it is the water's last land side (the first island shows a
 *       move before it is explored); an unknown own type takes the
 *       neighbour's.  A diagonal neighbour adds nothing, and nothing else is
 *       drawn on a dark cell.</li>
 *   <li><b>Explored cell:</b> {@code TERRAIN[spr(type)]}, then for each side
 *       whose neighbour's type is known (explored or the true type of the
 *       ring) and bleeds in: that neighbour's {@code blendSpr} (explored
 *       coast water's land face into land) at the side's mask, skipped
 *       when an explored neighbour's sprite is the tile's own (an
 *       unexplored one's is copied all the same:
 *       {@link #DARK_SIDES_ALWAYS}; either is a no-op except at the 4 px
 *       where two masks overlap).  Then the overlays
 *       ({@link ClassicTileArt#overlayFrames}): PHYS0 indices, 0xFD
 *       transparent, index 0 drawn (black).  An explored tile is never
 *       darkened: the fringe lies inside the unexplored tile only.</li>
 * </ul>
 *
 * <p><b>The coast</b> (design 10 §8, item W6b) of an <em>explored water</em>
 * cell with land among its 8 neighbours ({@code type()}, so land still dark
 * counts: the coastline shows one move before the land is explored; off
 * the map and unknown count as water).  Two layers are composed: the
 * <em>water layer</em> above (its base and blends) and the <em>land
 * layer</em>, the cell composed as land: the {@code blendSpr} of its last
 * land side in N, E, S, W order ({@link #LAND_SIDE_ORDER}) with the
 * side-mask blends of its land neighbours (no water bleeds into it, not
 * even coast water's land face).
 * Then:
 * <ul>
 *   <li><b>Beach corner</b> ({@link #BEACH_RULE}): exactly two adjacent
 *       sides land, the other two and the diagonal opposite them water: the
 *       land layer, then
 *       {@code PHYS0.SS.150-153} (land N+W, N+E, S+W, S+E) over it, 0xFD
 *       keeping the land; no quarters (Critic 2).</li>
 *   <li>Else the <b>coast quarters</b> {@code PHYS0.SS.108 + 4c + q}
 *       ({@link ClassicTileArt#coastQuarter}; q = 0 NW, 1 NE, 2 SE, 3 SW at
 *       (0,0), (8,0), (8,8), (0,8); c = b0 + 2 b1 + 4 b2, the land bits of
 *       the corner's three neighbours clockwise), drawn over the water layer:
 *       index 0 keeps the water layer, 0xFD shows the land layer, any other
 *       index is drawn.  c = 0 draws nothing.</li>
 * </ul>
 * The overlays come last.  Pinned against the clips
 * ({@code ClassicTerrainGoldenTest}): the land layer's sprite and its
 * blends in every quarter's transparent pixels, not the quarter's own
 * orthogonal neighbour (fog-start #3162 cell (5,5), 2,207 cells of four
 * clips, {@link #LAND_FILL}); the beach base is the same last land side,
 * which is the horizontal one but for 153, whose is the vertical one
 * (fog-start #4027, {@link #BEACH_BASE}).
 *
 * <p><b>Least knowledge</b> (design §5): the composer asks the type of an
 * unexplored tile only within Chebyshev distance 1 of an explored one: the
 * own type of a dark cell only when an explored orthogonal neighbour is
 * water (the only case its fringe depends on it), the 8 neighbours of an
 * explored cell (blends, coast), the 4 sides of an explored water tile
 * (its land face), and the overlays' connectivity around an
 * explored cell.
 *
 * <p>Immutable apart from a sprite cache and a scratch cell; EDT only (they
 * are not synchronized).
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

    /** {@code PHYS0.SS}: the coast quarters, 32 frames of 8x8 from here. */
    static final int QUARTER_FIRST = 108;

    /** A coast quarter's size in native pixels. */
    static final int QUARTER = 8;

    /** {@code PHYS0.SS}: the beach corners, 4 frames of 16x16 from here. */
    static final int BEACH_FIRST = 150;

    /** TERRAIN.SS frames needed, and PHYS0.SS frames needed. */
    static final int TERRAIN_FRAMES = 12, PHYS0_FRAMES = BEACH_FIRST + 4;

    /**
     * The corners NW, NE, SE, SW: their three neighbours {dx, dy} clockwise,
     * bits b0, b1, b2 of the quarter's c (design 10 §8.1).
     */
    static final int[][][] CORNERS = {
        { { -1, 0 }, { -1, -1 }, { 0, -1 } },   // NW: W, NW, N
        { { 0, -1 }, { 1, -1 }, { 1, 0 } },     // NE: N, NE, E
        { { 1, 0 }, { 1, 1 }, { 0, 1 } },       // SE: E, SE, S
        { { 0, 1 }, { -1, 1 }, { -1, 0 } }      // SW: S, SW, W
    };

    /**
     * The land layer of a coast cell takes the sprite of its <b>last</b>
     * land side in this order (N, E, S, W): W before S before E before N.
     * Measured: all 2,207 coast cells of four clips whose land sides show
     * different sprites (none against) fill their quarters' and beach
     * corners' transparent pixels with that side's sprite, so a beach
     * corner takes it as its base (150 W, 151 E, 152 W, 153 S;
     * freecol-spike-results/d/w6b LandLayer).
     */
    static final int[] LAND_SIDE_ORDER = { 0, 1, 2, 3 };

    /** What the transparent pixels of a coast quarter show (O1). */
    enum LandFill {
        /** The cell's land layer ({@link #LAND_SIDE_ORDER}); committed. */
        LAND_LAYER,
        /**
         * Design 10 §8.1: the sprite of the quarter's own orthogonal land
         * neighbour; with both, the vertical one.
         */
        CORNER_VERTICAL,
        /** The same; with both, the horizontal one. */
        CORNER_HORIZONTAL,
        /** The same; with both, the one whose edge is nearer per pixel. */
        CORNER_NEARER
    }

    /** {@link LandFill#LAND_LAYER}, pinned by the clips (O1). */
    static final LandFill LAND_FILL = LandFill.LAND_LAYER;

    /** When a coast cell carries a beach corner (O2). */
    enum BeachRule {
        /**
         * Exactly two adjacent sides land, the other two water, and the
         * diagonal opposite the corner water; committed.
         */
        EXACT_PAIR,
        /** The same whatever the opposite diagonal (design 10 §8.1). */
        PAIR_OF_SIDES,
        /** Two adjacent sides land, the others whatever. */
        ANY_PAIR,
        /** Never: quarters only (the design's safe fallback). */
        OFF
    }

    /**
     * {@link BeachRule#EXACT_PAIR}, measured on nine clips: a third land side
     * gives quarters (landfall: N, E, W land, 234 cells, no beach); exactly
     * two adjacent land sides with the opposite diagonal water give a beach
     * corner (2,787 cells; 1 against, a blank screen), with that diagonal
     * land quarters (9 cells at 4 places, e.g. clip005 #1046; the 44
     * beaches seen there had a ship or a box on the diagonal).  The corner's
     * own diagonal does not matter (clip006 #6475: water, a beach).
     */
    static final BeachRule BEACH_RULE = BeachRule.EXACT_PAIR;

    /** The base under a beach corner (O2, Critic 2). */
    enum BeachBase {
        /** The land layer ({@link #LAND_SIDE_ORDER}); committed. */
        LAND_LAYER,
        /** The horizontal land neighbour's sprite (Critic 2's candidate). */
        HORIZONTAL,
        /** The vertical land neighbour's. */
        VERTICAL,
        /** The diagonal neighbour's between the two (Critic 2's other). */
        DIAGONAL
    }

    /** {@link BeachBase#LAND_LAYER}, pinned by fog-start #4027 (O2). */
    static final BeachBase BEACH_BASE = BeachBase.LAND_LAYER;

    /**
     * Explored coast water bleeds its land face ({@link #landFace}, the
     * sprite of its land layer) into its land neighbours, explored or dark:
     * "water never bleeds into land" holds for the water itself only.
     * Measured: a land tile's mask toward explored coast water shows that
     * sprite in all 345 cells of four clips where it differs from the
     * tile's own, and never the own (LandFromCoast); fog-start #3162 (41,44)
     * S shows T007 over its own T003.  Dark water is left out (least
     * knowledge; I).
     */
    static final boolean LAND_FACE = true;


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


    /** The overlays and the coast pieces. */
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

    /**
     * The pinned rules, or candidates for them (the golden test's "pin by
     * golden", design 10 §8.2).  Immutable.
     */
    static final class Rules {

        /** The committed rules. */
        static final Rules COMMITTED = new Rules(DARK_SIDES_ALWAYS, SIDE_ORDER,
            LAND_FILL, BEACH_RULE, BEACH_BASE, LAND_FACE);

        final boolean darkSidesAlways, landFace;
        final int[] sideOrder;
        final LandFill landFill;
        final BeachRule beachRule;
        final BeachBase beachBase;

        private Rules(boolean darkSidesAlways, int[] sideOrder, LandFill landFill,
                      BeachRule beachRule, BeachBase beachBase, boolean landFace) {
            if (sideOrder == null || sideOrder.length != 4 || landFill == null
                || beachRule == null || beachBase == null) {
                throw new IllegalArgumentException("incomplete rules");
            }
            this.darkSidesAlways = darkSidesAlways;
            this.sideOrder = sideOrder.clone();
            this.landFill = landFill;
            this.beachRule = beachRule;
            this.beachBase = beachBase;
            this.landFace = landFace;
        }

        /** @return These rules with {@link #DARK_SIDES_ALWAYS} as given. */
        Rules darkSidesAlways(boolean b) {
            return new Rules(b, this.sideOrder, this.landFill, this.beachRule,
                             this.beachBase, this.landFace);
        }

        /** @return These rules with another {@link #SIDE_ORDER}. */
        Rules sideOrder(int[] o) {
            return new Rules(this.darkSidesAlways, o, this.landFill, this.beachRule,
                             this.beachBase, this.landFace);
        }

        /** @return These rules with another {@link #LAND_FILL}. */
        Rules landFill(LandFill f) {
            return new Rules(this.darkSidesAlways, this.sideOrder, f, this.beachRule,
                             this.beachBase, this.landFace);
        }

        /** @return These rules with another {@link #BEACH_RULE}. */
        Rules beachRule(BeachRule r) {
            return new Rules(this.darkSidesAlways, this.sideOrder, this.landFill, r,
                             this.beachBase, this.landFace);
        }

        /** @return These rules with another {@link #BEACH_BASE}. */
        Rules beachBase(BeachBase b) {
            return new Rules(this.darkSidesAlways, this.sideOrder, this.landFill,
                             this.beachRule, b, this.landFace);
        }

        /** @return These rules with {@link #LAND_FACE} as given. */
        Rules landFace(boolean b) {
            return new Rules(this.darkSidesAlways, this.sideOrder, this.landFill,
                             this.beachRule, this.beachBase, b);
        }

        @Override
        public String toString() {
            final StringBuilder o = new StringBuilder();
            for (int d : this.sideOrder) o.append("NESW".charAt(d));
            return "darkSidesAlways=" + this.darkSidesAlways + " sideOrder=" + o
                + " landFill=" + this.landFill + " beachRule=" + this.beachRule
                + " beachBase=" + this.beachBase + " landFace=" + this.landFace;
        }
    }

    /** {@link #DARK_SIDES_ALWAYS}, or its alternative (the golden test's pin). */
    private final boolean darkSidesAlways;

    /** {@link #SIDE_ORDER}, or another order (the golden test's candidates, O3). */
    private final int[] sideOrder;

    /** {@link #LAND_FILL}, or a candidate (O1). */
    private final LandFill landFill;

    /** {@link #BEACH_RULE}, or a candidate (O2). */
    private final BeachRule beachRule;

    /** {@link #BEACH_BASE}, or a candidate (O2). */
    private final BeachBase beachBase;

    /** {@link #LAND_FACE}, or its alternative. */
    private final boolean landFace;

    /** The coast quarters (8x8 each) and the beach corners (16x16 each). */
    private final byte[][] quarters = new byte[32][], beaches = new byte[4][];

    /** A coast cell's land layer, 16x16 (EDT only). */
    private final byte[] landLayer = new byte[CELL * CELL];


    /**
     * @param terrain {@code TERRAIN.SS} as indices (at least 12 frames of
     *     16x16).
     * @param phys {@code PHYS0.SS} as indices (at least 154 frames; 104-107,
     *     148 and 150-153 16x16, 108-139 8x8).
     * @param alias The TERRAIN.SS frame of a tile type id, -1 for none
     *     ({@link ClassicPackFiles#terrainSpriteFor}).
     * @exception IllegalArgumentException if a sheet lacks a frame.
     */
    ClassicTerrainComposer(ClassicIndexSheet terrain, ClassicIndexSheet phys,
                           ToIntFunction<String> alias) {
        this(terrain, phys, alias, DARK_SIDES_ALWAYS, SIDE_ORDER);
    }

    /**
     * A composer with a choice of the pinned fog and blend rules, for the
     * golden test.
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
        this(terrain, phys, alias, Rules.COMMITTED.darkSidesAlways(darkSidesAlways)
             .sideOrder(sideOrder));
    }

    /**
     * A composer with a choice of every pinned rule, for the golden test
     * (design 10 §8.2, "pin by golden").
     *
     * @param terrain {@code TERRAIN.SS} as indices.
     * @param phys {@code PHYS0.SS} as indices.
     * @param alias The TERRAIN.SS frame of a tile type id, -1 for none.
     * @param rules The rules ({@link Rules#COMMITTED} or candidates).
     * @exception IllegalArgumentException if a sheet lacks a frame.
     */
    ClassicTerrainComposer(ClassicIndexSheet terrain, ClassicIndexSheet phys,
                           ToIntFunction<String> alias, Rules rules) {
        if (terrain == null || phys == null || alias == null || rules == null) {
            throw new IllegalArgumentException("sheets, alias and rules needed");
        }
        this.darkSidesAlways = rules.darkSidesAlways;
        this.sideOrder = rules.sideOrder.clone();
        this.landFill = rules.landFill;
        this.beachRule = rules.beachRule;
        this.beachBase = rules.beachBase;
        this.landFace = rules.landFace;
        if (terrain.size() < TERRAIN_FRAMES || phys.size() < PHYS0_FRAMES) {
            throw new IllegalArgumentException("short sheets: " + terrain + ", " + phys);
        }
        this.phys = phys;
        this.alias = alias;
        for (int f = 0; f < TERRAIN_FRAMES; f++) this.bases[f] = full(terrain, f);
        this.dark = full(phys, DARK);
        for (int i = 0; i < this.quarters.length; i++) {
            this.quarters[i] = sized(phys, QUARTER_FIRST + i, QUARTER);
        }
        for (int i = 0; i < this.beaches.length; i++) {
            this.beaches[i] = full(phys, BEACH_FIRST + i);
        }
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
        return sized(sheet, f, CELL);
    }

    /** A square frame of a given size. */
    private static byte[] sized(ClassicIndexSheet sheet, int f, int size) {
        if (sheet.width(f) != size || sheet.height(f) != size) {
            throw new IllegalArgumentException(sheet.name() + "." + f + " is "
                + sheet.width(f) + "x" + sheet.height(f) + ", not " + size
                + "x" + size);
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
                // Water bleeds its water into water, its land face into
                // dark land (which is one of its land sides).
                if (!ownAsked) {
                    own = src.type(x, y);
                    ownAsked = true;
                }
                if (own != null && !own.isWater()) {
                    final int face = (this.landFace) ? landFace(src, nx, ny) : -1;
                    s = (face >= 0) ? face : blendSpr(own);
                }
            }
            copyMask(d, s, dst, off, stride);
        }
    }

    /**
     * An explored cell: its base, the side-mask blends, the coast of
     * explored water and the overlays.
     */
    private void composeExplored(TerrainSource src, int x, int y, byte[] dst,
                                 int off, int stride) {
        final TileType t = src.type(x, y);
        final int base = (t == null) ? UNKNOWN_WATER : spr(t);
        copy(this.bases[base], dst, off, stride);
        if (t != null) {
            blendSides(src, x, y, (t.isWater()) ? Into.WATER : Into.LAND, base,
                       dst, off, stride);
            if (t.isWater()) coast(src, x, y, dst, off, stride);
        }
        src.overlays(x, y, f -> overlay(f, dst, off, stride));
    }

    /** What a cell is drawn as, for the side-mask blends. */
    private enum Into {
        /** Water: every known neighbour bleeds in. */
        WATER,
        /**
         * Land: land neighbours bleed in, and explored coast water its
         * {@link #landFace}.
         */
        LAND,
        /** A coast cell's land layer: land neighbours only. */
        LAND_LAYER
    }

    /**
     * The side-mask blends of an explored cell (or of a coast cell's land
     * layer): each side whose neighbour's type is known and bleeds in.
     * Water bleeds into water; into land only an explored coast water tile
     * does, with its land face ({@link #landFace}; measured: a land tile's
     * mask toward explored coast water shows the water's land layer sprite
     * in every one of 345 cells of four clips where they differ,
     * freecol-spike-results/d/w6b LandFromCoast; dark water stays out:
     * least knowledge, I), and into a coast cell's land layer none (700
     * cells, FaceTest).
     *
     * @param src The map.
     * @param x The tile's column.
     * @param y The tile's row.
     * @param into What the cell is drawn as.
     * @param base The cell's TERRAIN.SS base frame.
     * @param dst The index buffer.
     * @param off The offset of the cell's top-left pixel.
     * @param stride The buffer's row length.
     */
    private void blendSides(TerrainSource src, int x, int y, Into into,
                            int base, byte[] dst, int off, int stride) {
        for (int d : this.sideOrder) {
            final int nx = x + SIDES[d][0], ny = y + SIDES[d][1];
            if (!src.onMap(nx, ny)) continue;
            final TileType tn = src.type(nx, ny);
            if (tn == null) continue;   // unknown: no blend (F-W6d-MP)
            int s = blendSpr(tn);
            if (tn.isWater() && into != Into.WATER) {
                if (into == Into.LAND_LAYER || !this.landFace || !src.explored(nx, ny)) continue;
                // Its land face, unless that is this tile itself (fog-start
                // #1042: the T008 tile (44,46) keeps its own under the
                // face T001 of the water N of it).
                final int fd = landFaceSide(src, nx, ny);
                if (fd < 0 || (nx + SIDES[fd][0] == x && ny + SIDES[fd][1] == y)) continue;
                s = blendSpr(src.type(nx + SIDES[fd][0], ny + SIDES[fd][1]));
            }
            // The own sprite again: a no-op but at the overlaps, and
            // copied toward a dark neighbour (DARK_SIDES_ALWAYS).
            if (s == base && !(this.darkSidesAlways && !src.explored(nx, ny))) continue;
            copyMask(d, s, dst, off, stride);
        }
    }

    /**
     * The land face of a water tile: the {@code blendSpr} of its last land
     * side in {@link #LAND_SIDE_ORDER}, the sprite of its land layer.  It
     * is what the tile bleeds into its land neighbours, explored or dark.
     *
     * @param src The map.
     * @param x The water tile's column.
     * @param y The water tile's row.
     * @return The TERRAIN.SS frame, or -1 without an orthogonal land side.
     */
    int landFace(TerrainSource src, int x, int y) {
        final int d = landFaceSide(src, x, y);
        return (d < 0) ? -1 : blendSpr(src.type(x + SIDES[d][0], y + SIDES[d][1]));
    }

    /**
     * @param src The map.
     * @param x A water tile's column.
     * @param y Its row.
     * @return The side N, E, S, W its land face comes from, or -1.
     */
    private static int landFaceSide(TerrainSource src, int x, int y) {
        final boolean[] side = new boolean[4];
        for (int d = 0; d < 4; d++) side[d] = land(src, x + SIDES[d][0], y + SIDES[d][1]);
        return lastLandSide(side);
    }

    /** Whether a tile is land: off the map and unknown count as water. */
    private static boolean land(TerrainSource src, int x, int y) {
        if (!src.onMap(x, y)) return false;
        final TileType t = src.type(x, y);
        return t != null && !t.isWater();
    }

    /**
     * The coast of an explored water cell (design 10 §8, W6b), over its
     * water layer: a beach corner on its land layer, or the coast quarters.
     */
    private void coast(TerrainSource src, int x, int y, byte[] dst, int off,
                       int stride) {
        final boolean[] side = new boolean[4];
        boolean any = false;
        for (int d = 0; d < 4; d++) {
            side[d] = land(src, x + SIDES[d][0], y + SIDES[d][1]);
            any |= side[d];
        }
        final int[] c = new int[4];
        for (int q = 0; q < 4; q++) {
            for (int b = 0; b < 3; b++) {
                final int[] nb = CORNERS[q][b];
                if (land(src, x + nb[0], y + nb[1])) c[q] |= 1 << b;
            }
            any |= c[q] != 0;
        }
        if (!any) return;
        final int beach = beachCorner(side, c);
        if (beach >= 0) {
            landLayer(src, x, y, beachBaseSide(side, beach));
            final byte[] corner = this.beaches[beach - BEACH_FIRST];
            for (int j = 0; j < CELL; j++) {
                for (int i = 0; i < CELL; i++) {
                    final byte v = corner[j * CELL + i];
                    dst[off + j * stride + i] = ((v & 0xFF) == ClassicIndexSheet.TRANSPARENT)
                        ? this.landLayer[j * CELL + i] : v;
                }
            }
            return;
        }
        final int fillSide = lastLandSide(side);
        if (fillSide >= 0 && this.landFill == LandFill.LAND_LAYER) {
            landLayer(src, x, y, fillSide);
        }
        for (int q = 0; q < 4; q++) {
            final int f = ClassicTileArt.coastQuarter(q, c[q]);
            if (f < 0) continue;
            final byte[] px = this.quarters[f - QUARTER_FIRST];
            final int qx = (q == 1 || q == 2) ? QUARTER : 0;
            final int qy = (q >= 2) ? QUARTER : 0;
            // The original's transparent pixels need an orthogonal land
            // side (c = 2, diagonal only, is opaque); without one keep.
            final boolean land = (this.landFill == LandFill.LAND_LAYER)
                ? fillSide >= 0 : (c[q] & 5) != 0;
            for (int j = 0; j < QUARTER; j++) {
                for (int i = 0; i < QUARTER; i++) {
                    final int v = px[j * QUARTER + i] & 0xFF;
                    if (v == 0) continue;   // keep the water layer
                    if (v == ClassicIndexSheet.TRANSPARENT && !land) continue;
                    final int k = (qy + j) * CELL + qx + i;
                    dst[off + (qy + j) * stride + qx + i] = (v == ClassicIndexSheet.TRANSPARENT)
                        ? fill(src, x, y, q, c[q], k) : (byte)v;
                }
            }
        }
    }

    /**
     * The land a quarter's transparent pixel shows ({@link #LAND_FILL}).
     *
     * @param src The map.
     * @param x The tile's column.
     * @param y The tile's row.
     * @param q The quarter.
     * @param c Its land bits (b0 or b2 set: a transparent pixel needs an
     *     orthogonal land side).
     * @param k The pixel's cell offset.
     * @return Its index.
     */
    private byte fill(TerrainSource src, int x, int y, int q, int c, int k) {
        if (this.landFill == LandFill.LAND_LAYER) return this.landLayer[k];
        final int[] o0 = CORNERS[q][0], o2 = CORNERS[q][2];
        final boolean b0 = (c & 1) != 0, b2 = (c & 4) != 0;
        int[] o;
        if (b0 && b2) {
            final int[] vert = (o0[0] == 0) ? o0 : o2, horz = (o0[0] == 0) ? o2 : o0;
            switch (this.landFill) {
            case CORNER_HORIZONTAL:
                o = horz;
                break;
            case CORNER_NEARER: {
                final int px = k % CELL, py = k / CELL;
                final int dv = (vert[1] < 0) ? py : CELL - 1 - py;
                final int dh = (horz[0] < 0) ? px : CELL - 1 - px;
                o = (dv <= dh) ? vert : horz;
                break;
            }
            default:
                o = vert;
                break;
            }
        } else {
            o = (b0) ? o0 : o2;
        }
        final TileType t = src.type(x + o[0], y + o[1]);
        return this.bases[(t == null) ? UNKNOWN_LAND : blendSpr(t)][k];
    }

    /**
     * @param side Whether each side N, E, S, W is land.
     * @return The last land side in {@link #LAND_SIDE_ORDER}, or -1.
     */
    static int lastLandSide(boolean[] side) {
        int last = -1;
        for (int d : LAND_SIDE_ORDER) if (side[d]) last = d;
        return last;
    }

    /**
     * The beach corner of a coast cell ({@link #BEACH_RULE}).
     *
     * @param side Whether each side N, E, S, W is land.
     * @param c The land bits of the corners NW, NE, SE, SW.
     * @return The {@code PHYS0.SS} frame, or -1 for quarters.
     */
    int beachCorner(boolean[] side, int[] c) {
        if (this.beachRule == BeachRule.OFF) return -1;
        int n = 0;
        for (boolean s : side) if (s) n++;
        if (n < 2 || (n > 2 && this.beachRule != BeachRule.ANY_PAIR)) return -1;
        for (boolean north : new boolean[] { true, false }) {
            for (boolean west : new boolean[] { true, false }) {
                if (!side[north ? 0 : 2] || !side[west ? 3 : 1]) continue;
                // The opposite corner (SE for N+W, ...): its diagonal is b1.
                final int opposite = (north) ? ((west) ? 2 : 3) : ((west) ? 1 : 0);
                if (this.beachRule == BeachRule.EXACT_PAIR && (c[opposite] & 2) != 0) {
                    return -1;
                }
                return ClassicTileArt.beachCorner(north, west);
            }
        }
        return -1;   // two opposite sides
    }

    /**
     * The side (or, for {@link BeachBase#DIAGONAL}, the side whose sprite
     * stands for the diagonal) giving a beach corner's base
     * ({@link #BEACH_BASE}).
     *
     * @param side Whether each side N, E, S, W is land.
     * @param beach The beach corner frame.
     * @return A side N, E, S, W, or 4 + the corner for the diagonal.
     */
    private int beachBaseSide(boolean[] side, int beach) {
        final boolean north = beach - BEACH_FIRST < 2;
        final boolean west = (beach - BEACH_FIRST) % 2 == 0;
        switch (this.beachBase) {
        case HORIZONTAL:
            return (west) ? 3 : 1;
        case VERTICAL:
            return (north) ? 0 : 2;
        case DIAGONAL:
            return 4 + ((north) ? ((west) ? 0 : 1) : ((west) ? 3 : 2));
        default:
            return lastLandSide(side);
        }
    }

    /**
     * Compose a coast cell's land layer into {@link #landLayer}: the cell
     * drawn as land of one neighbour's sprite, with the side-mask blends of
     * its land neighbours.
     *
     * @param src The map.
     * @param x The tile's column.
     * @param y The tile's row.
     * @param from The side N, E, S, W whose {@code blendSpr} is the base, or
     *     4 + a corner (NW, NE, SE, SW) for the diagonal neighbour's.
     */
    private void landLayer(TerrainSource src, int x, int y, int from) {
        final int dx = (from < 4) ? SIDES[from][0] : CORNERS[from - 4][1][0];
        final int dy = (from < 4) ? SIDES[from][1] : CORNERS[from - 4][1][1];
        final TileType t = src.type(x + dx, y + dy);
        final int base = (t == null || t.isWater()) ? UNKNOWN_LAND : blendSpr(t);
        copy(this.bases[base], this.landLayer, 0, CELL);
        blendSides(src, x, y, Into.LAND_LAYER, base, this.landLayer, 0, CELL);
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
