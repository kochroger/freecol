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

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.function.IntConsumer;

import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.common.model.Direction;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Resource;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileImprovement;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.Topology;
import net.sf.freecol.common.resources.ResourceManager;


/**
 * The per-tile terrain-feature overlays (Phase 1 item (e)) — forest trees,
 * hills, mountains, rivers, roads, plowed fields, the lost-city rumour and
 * resource markers — of a square classic map cell: which {@code PHYS0.SS}
 * frames a tile shows ({@link #overlayFrames}), and drawing them over the
 * base terrain in the RGBA fallback ({@link #paintOverlays}).  The index
 * composer ({@link ClassicTerrainComposer}, M1c design 10 §6, W6a) copies
 * the same frames as palette indices.
 *
 * <h2>Why {@code PHYS0.SS}, not {@code TERRAIN.SS}</h2>
 * The 1994 game drew a map cell as a base terrain tile plus <em>overlay</em>
 * sprites for its physical features, all cut as square 16&times;16 tiles.
 * {@code TERRAIN.SS} holds only the 12 base terrains; the feature overlays
 * live in {@code PHYS0.SS} (154 frames).  Those square sprites composite
 * cleanly onto the classic rectangular grid, where FreeCol's own
 * isometric-diamond overlays would skew — which is why item (e) waited on
 * sourcing them.  The frames are exposed by the {@code classic_original}
 * pack under the keys {@code image.classic_original.ss.PHYS0.SS.NNN}; when
 * the pack is absent we fall back to FreeCol's own (imperfect) overlay art
 * so the build always runs.
 *
 * <h2>Frame layout (verified by pixel edge-analysis of the extracted frames)</h2>
 * All the <em>directional</em> feature sets share one 4-bit connectivity
 * encoding — the frame within a set is
 * {@code (E?1:0) | (W?2:0) | (S?4:0) | (N?8:0)} over the raw-grid cardinal
 * neighbours that also carry the feature:
 * <ul>
 *   <li>minor river {@code 0..15}, major river {@code 16..31},
 *       mountains {@code 32..47}, hills {@code 48..63}, forest {@code 64..79};</li>
 *   <li>roads are composited instead: {@code 80} is the centre hub and
 *       {@code 81..88} are the eight directional spokes
 *       (N, NE, E, SE, S, SW, W, NW clockwise), one drawn per raw neighbour
 *       that has a road;</li>
 *   <li>{@code 103} lost-city rumour, {@code 149} plowed field, and the
 *       resource markers {@code 89..102}.</li>
 * </ul>
 *
 * <h2>Coastline: the frames {@code 108..139} and {@code 150..153}</h2>
 * The original's coast quarter-tiles (32 small 8&times;8 sprites,
 * {@code 4 corners &times; 8 configs}) were once drawn here from the PNGs
 * and then removed, because they showed a "green fleck" along the wave
 * crest that a reference screenshot seemed to lack.  That fleck is the
 * original's own rim (indices 67-71, landfall 04 section 5.2), not an
 * extraction fault: the frames are index-exact against the clips (M1c
 * design 10 F6).  The composer draws them as palette indices on explored
 * water (item W6b): quarter {@code q} is {@link #coastQuarter}
 * {@code P(108 + 4c + q)}, and a tile with exactly two adjacent land sides
 * gets a beach corner {@link #beachCorner} {@code 150..153} instead.  The
 * RGBA fallback draws no coast.  The river mouths {@code 140..147} are not
 * drawn (O9).
 *
 * <h2>Connectivity on the classic grid</h2>
 * Connectivity is computed from <em>raw-grid</em> neighbours (the tiles drawn
 * directly up/down/left/right and at the corners) rather than FreeCol's
 * isometric {@link net.sf.freecol.common.model.Direction}s, so features blend
 * with whatever is <em>visually</em> adjacent on the square grid.  Area
 * features (forest / hills / mountains) use the four cardinal neighbours,
 * whose types come from a {@link TypeLookup}: the composer's reaches into
 * the fog ring with the true terrain (design 10 O8), the fallback's
 * ({@link #modelTypes}) knows only the client's explored tiles.  Rivers are
 * linear and — because FreeCol lays them out along the isometric
 * long-sides, which flatten to raw diagonals — fold each diagonal neighbour
 * into its two adjacent cardinal bits so a diagonal river still reads as
 * connected (on the square {@link Topology} the model's own N/E/S/W river
 * connections are used instead).  Roads simply draw a spoke toward every one
 * of the eight raw neighbours that has a road.
 */
final class ClassicTileArt {

    /**
     * The terrain type at a raw map position, for the area features'
     * connectivity.
     */
    interface TypeLookup {

        /**
         * @param x The column.
         * @param y The row.
         * @return The type, or null (unknown, off the map).
         */
        TileType typeAt(int x, int y);
    }

    // PHYS0.SS frame bases for each directional feature set.
    private static final int RIVER_MINOR = 0;
    private static final int RIVER_MAJOR = 16;
    private static final int MOUNTAINS = 32;
    private static final int HILLS = 48;
    private static final int FOREST = 64;

    /** Road centre-hub frame; the eight spokes follow at {@code ROAD_HUB+1..+8}. */
    private static final int ROAD_HUB = 80;

    private static final int LOST_CITY = 103;
    private static final int PLOWED = 149;

    /** The first coast quarter; quarter q of land bits c is +4c+q. */
    private static final int COAST_QUARTER = 108;

    /** The first beach corner: land N+W, then N+E, S+W, S+E. */
    private static final int BEACH_CORNER = 150;

    /** Connectivity bits (see the class comment): the frame is their sum. */
    private static final int E = 1, W = 2, S = 4, N = 8;

    /** Number of frames in {@code PHYS0.SS} (used to bound the cache/lookups). */
    private static final int PHYS0_FRAMES = 154;

    /**
     * Raw-grid offsets and spoke frame for each of the eight road directions,
     * clockwise from N: {@code {dx, dy, frame}}.  N=up, NE=up-right, E=right,
     * SE=down-right, S=down, SW=down-left, W=left, NW=up-left.
     */
    private static final int[][] ROAD_SPOKES = {
        { 0, -1, 81 }, { 1, -1, 82 }, { 1, 0, 83 }, { 1, 1, 84 },
        { 0,  1, 85 }, { -1, 1, 86 }, { -1, 0, 87 }, { -1, -1, 88 },
    };

    private final ImageLibrary lib;

    /** Whether the {@code classic_original} pack (hence {@code PHYS0.SS}) is loaded. */
    private final boolean packPresent;

    /** Lazily-loaded cache of the native 16&times;16 {@code PHYS0.SS} frames. */
    private final BufferedImage[] frames = new BufferedImage[PHYS0_FRAMES];


    ClassicTileArt(ImageLibrary lib) {
        this.lib = lib;
        this.packPresent = ResourceManager.getImageResource(phys0Key(0), false) != null;
    }


    private static String phys0Key(int n) {
        return String.format("image.classic_original.ss.PHYS0.SS.%03d", n);
    }

    /** Load (and cache) a {@code PHYS0.SS} frame, or null if out of range. */
    private BufferedImage frame(int n) {
        if (n < 0 || n >= PHYS0_FRAMES) return null;
        if (this.frames[n] == null) {
            this.frames[n] = ImageLibrary.getUnscaledImage(phys0Key(n));
        }
        return this.frames[n];
    }

    /** Draw a {@code PHYS0.SS} frame scaled to fill the cell at {@code (sx, sy)}. */
    private void drawFrame(Graphics2D g, int n, int sx, int sy, int w, int h) {
        final BufferedImage img = frame(n);
        if (img != null) g.drawImage(img, sx, sy, w, h, null);
    }

    /**
     * The types of the client's map: an unexplored tile has none.
     *
     * @param map The map.
     * @return The lookup.
     */
    static TypeLookup modelTypes(final Map map) {
        return (x, y) -> {
            final Tile t = map.getTile(x, y);
            return (t == null) ? null : t.getType();
        };
    }


    /**
     * Composite every terrain-feature overlay for {@code tile} into the cell at
     * {@code (sx, sy)} sized {@code w}&times;{@code h}, on top of the already-drawn
     * base terrain and below the settlement/unit sprite (the RGBA fallback).
     */
    void paintOverlays(Graphics2D g, Map map, Tile tile, int sx, int sy,
                       int w, int h) {
        if (this.packPresent) {
            overlayFrames(map, tile, modelTypes(map),
                          f -> drawFrame(g, f, sx, sy, w, h));
        } else {
            paintFallback(g, tile, sx, sy, w, h);
        }
    }

    /**
     * The {@code PHYS0.SS} overlay frames of a tile, in drawing order:
     * relief (forest, else mountains, else hills), plowed field, river,
     * road hub and spokes, resource marker, lost-city rumour.
     *
     * @param map The map (rivers, roads).
     * @param tile The tile.
     * @param types The types for the area features' connectivity.
     * @param frames Receives each frame number.
     */
    static void overlayFrames(Map map, Tile tile, TypeLookup types,
                              IntConsumer frames) {
        final int x = tile.getX();
        final int y = tile.getY();
        final TileType type = tile.getType();

        // Terrain relief: forest trees, or the hill/mountain massif.  These are
        // area features, so connectivity is over the four cardinal neighbours.
        if (type != null && type.isForested()) {
            frames.accept(FOREST + areaMask(types, x, y, Feature.FOREST));
        } else if (isType(type, "model.tile.mountains")) {
            frames.accept(MOUNTAINS + areaMask(types, x, y, Feature.MOUNTAINS));
        } else if (isType(type, "model.tile.hills")) {
            frames.accept(HILLS + areaMask(types, x, y, Feature.HILLS));
        }

        // A plowed field sits on open, cleared ground.
        if (isPlowed(tile)) frames.accept(PLOWED);

        // River: minor (magnitude 1) or major (>= 2), connectivity folded from
        // the raw-grid neighbours (see class comment).
        final TileImprovement river = tile.getRiver();
        if (river != null) {
            final int base = (river.getMagnitude() >= 2) ? RIVER_MAJOR : RIVER_MINOR;
            frames.accept(base + riverMask(map, x, y));
        }

        // Road: centre hub plus a spoke toward each raw neighbour with a road.
        if (tile.hasRoad()) {
            frames.accept(ROAD_HUB);
            for (int[] spoke : ROAD_SPOKES) {
                if (hasRoad(map, x + spoke[0], y + spoke[1])) frames.accept(spoke[2]);
            }
        }

        // Resource marker, then a lost-city rumour on top of everything.
        if (tile.hasResource()) {
            final int r = resourceFrame(tile.getResource());
            if (r >= 0) frames.accept(r);
        }
        if (tile.hasLostCityRumour()) frames.accept(LOST_CITY);
    }

    /**
     * The coast quarter of a water tile's corner (M1c design 10 §8.1,
     * landfall 04 §5.2: 64 of 71 quarters with land exact).
     *
     * @param q The corner: 0 NW at (0,0), 1 NE at (8,0), 2 SE at (8,8),
     *     3 SW at (0,8).
     * @param c Its land bits {@code b0 + 2 b1 + 4 b2}, the corner's three
     *     neighbours clockwise: NW (W, NW, N), NE (N, NE, E), SE (E, SE, S),
     *     SW (S, SW, W).
     * @return The {@code PHYS0.SS} frame {@code 108 + 4c + q}, or -1 for
     *     c = 0 (108-111 are all index 0: nothing drawn).
     */
    static int coastQuarter(int q, int c) {
        if (q < 0 || q > 3 || c < 0 || c > 7) {
            throw new IllegalArgumentException("quarter " + q + ", bits " + c);
        }
        return (c == 0) ? -1 : COAST_QUARTER + 4 * c + q;
    }

    /**
     * The beach corner of a water tile whose two adjacent land sides are
     * given (sand along them, landfall 04 §5.3; which tiles carry one is
     * the composer's rule).
     *
     * @param north Land on the N side (else S).
     * @param west Land on the W side (else E).
     * @return The {@code PHYS0.SS} frame: 150 N+W, 151 N+E, 152 S+W,
     *     153 S+E.
     */
    static int beachCorner(boolean north, boolean west) {
        return BEACH_CORNER + ((north) ? 0 : 2) + ((west) ? 0 : 1);
    }

    /**
     * Fallback path when the pack is absent: draw FreeCol's own overlay/forest/
     * river art shrunk into the cell.  Isometric-shaped, so imperfect on the
     * square grid, but it keeps the build running without the original assets.
     */
    private void paintFallback(Graphics2D g, Tile tile, int sx, int sy,
                               int w, int h) {
        final Dimension size = new Dimension(w, h);
        if (tile.isForested()) {
            g.drawImage(this.lib.getForestImage(tile.getType(), size), sx, sy, w, h, null);
        } else if (tile.getType() != null && tile.getType().isElevation()) {
            g.drawImage(this.lib.getSizedOverlayImage(tile.getType(), size),
                        sx, sy, w, h, null);
        }
        final TileImprovement river = tile.getRiver();
        if (river != null && river.getStyle() != null) {
            g.drawImage(this.lib.getRiverImage(river.getStyle().getString(), size),
                        sx, sy, w, h, null);
        }
    }


    // Connectivity helpers (raw-grid neighbours).

    private enum Feature { FOREST, HILLS, MOUNTAINS }

    /** True if the raw cell {@code (x, y)} carries {@code feature}. */
    private static boolean hasFeature(TypeLookup types, int x, int y,
                                      Feature feature) {
        final TileType t = types.typeAt(x, y);
        if (t == null) return false;
        switch (feature) {
        case FOREST:    return t.isForested();
        case HILLS:     return isType(t, "model.tile.hills");
        case MOUNTAINS: return isType(t, "model.tile.mountains");
        default:        return false;
        }
    }

    /** 4-bit cardinal-neighbour connectivity mask for an area feature. */
    private static int areaMask(TypeLookup types, int x, int y, Feature feature) {
        int m = 0;
        if (hasFeature(types, x, y - 1, feature)) m |= N;
        if (hasFeature(types, x + 1, y, feature)) m |= E;
        if (hasFeature(types, x, y + 1, feature)) m |= S;
        if (hasFeature(types, x - 1, y, feature)) m |= W;
        return m;
    }

    private static boolean hasRiver(Map map, int x, int y) {
        final Tile t = map.getTile(x, y);
        return t != null && t.hasRiver();
    }

    /**
     * River connectivity mask.  Rivers run along FreeCol's isometric long-sides,
     * which flatten to raw diagonals, so each diagonal neighbour with a river
     * lights both its adjacent cardinal bits — keeping a diagonally-running
     * river visually connected on the square grid.
     *
     * <p>With the square {@link Topology} the model's rivers already connect
     * across the four sides of a cell, so its own connections (including a
     * mouth into the sea) are the frame bits.
     */
    private static int riverMask(Map map, int x, int y) {
        if (Topology.current() == Topology.SQUARE) {
            final Tile tile = map.getTile(x, y);
            final TileImprovement river = (tile == null) ? null : tile.getRiver();
            if (river == null) return 0;
            int m = 0;
            if (river.isConnectedTo(Direction.N)) m |= N;
            if (river.isConnectedTo(Direction.E)) m |= E;
            if (river.isConnectedTo(Direction.S)) m |= S;
            if (river.isConnectedTo(Direction.W)) m |= W;
            return m;
        }
        final boolean up = hasRiver(map, x, y - 1), dn = hasRiver(map, x, y + 1);
        final boolean lf = hasRiver(map, x - 1, y), rt = hasRiver(map, x + 1, y);
        final boolean ul = hasRiver(map, x - 1, y - 1), ur = hasRiver(map, x + 1, y - 1);
        final boolean dl = hasRiver(map, x - 1, y + 1), dr = hasRiver(map, x + 1, y + 1);
        int m = 0;
        if (up || ul || ur) m |= N;
        if (dn || dl || dr) m |= S;
        if (rt || ur || dr) m |= E;
        if (lf || ul || dl) m |= W;
        return m;
    }

    private static boolean hasRoad(Map map, int x, int y) {
        final Tile t = map.getTile(x, y);
        return t != null && t.hasRoad();
    }


    // Tile-feature predicates.

    private static boolean isType(TileType type, String typeId) {
        return type != null && typeId.equals(type.getId());
    }

    private static boolean isPlowed(Tile tile) {
        for (TileImprovement imp : tile.getCompleteTileImprovements()) {
            if (imp.getType() != null
                && "model.improvement.plow".equals(imp.getType().getId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Map a {@link Resource} to its {@code PHYS0.SS} marker frame, or -1 when
     * unmapped.  <b>Provisional</b> — the sprite-to-resource identification is a
     * best-effort visual read of the extracted frames, pending the expert's
     * validation against the original game.
     */
    private static int resourceFrame(Resource resource) {
        if (resource == null || resource.getType() == null) return -1;
        switch (resource.getType().getId()) {
        case "model.resource.fish":     return 96;
        case "model.resource.game":     return 98;
        case "model.resource.furs":     return 97;
        case "model.resource.minerals": return 95;
        case "model.resource.ore":      return 102;
        case "model.resource.silver":   return 101;
        case "model.resource.lumber":   return 99;
        case "model.resource.tobacco":  return 91;
        case "model.resource.cotton":   return 92;
        case "model.resource.sugar":    return 93;
        default:                        return -1;
        }
    }
}
