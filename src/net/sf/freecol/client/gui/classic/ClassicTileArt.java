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

import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.common.model.Direction;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Resource;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileImprovement;
import net.sf.freecol.common.model.Topology;
import net.sf.freecol.common.resources.ResourceManager;


/**
 * Composites the per-tile terrain-feature overlays (Phase 1 item (e)) —
 * forest trees, hills, mountains, rivers, roads, plowed fields, the
 * lost-city rumour and resource markers — onto a square classic map cell,
 * on top of the base terrain drawn by {@link ClassicMapViewer#paintTile}.
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
 * <h2>Coastline — no longer sourced from frames {@code 108..139}</h2>
 * The 1994 game's own coast quarter-tiles were originally re-implemented here
 * (32 small 8&times;8 sprites, {@code 4 corners &times; 8 configs}, composited
 * onto the four quadrants of each water cell). Live comparison against the
 * original reference screenshots (see {@code screenshots/initial/}) found the
 * <em>extracted</em> frames render a scattered green fleck along the wave crest
 * that the original never shows (a clean grey/white foam fringe instead) — a
 * likely palette/extraction fidelity issue in the asset pack, not a rendering
 * bug in this class. Rather than ship a fringe that doesn't match the source
 * material, the water side of the coastline is now painted procedurally, the
 * same call made for the land side after Q7 found no faithful land-land border
 * sprite either — see {@code ClassicMapViewer.blendWaterBorders}. The estuary/
 * river-mouth pieces ({@code 140..147} ocean corner-hints, {@code 150..153}
 * diagonal sand strips) were never wired regardless.
 *
 * <h2>Connectivity on the classic grid</h2>
 * Connectivity is computed from <em>raw-grid</em> neighbours (the tiles drawn
 * directly up/down/left/right and at the corners) rather than FreeCol's
 * isometric {@link net.sf.freecol.common.model.Direction}s, so features blend
 * with whatever is <em>visually</em> adjacent on the square grid.  Area
 * features (forest / hills / mountains) use the four cardinal neighbours.
 * Rivers are linear and — because FreeCol lays them out along the isometric
 * long-sides, which flatten to raw diagonals — fold each diagonal neighbour
 * into its two adjacent cardinal bits so a diagonal river still reads as
 * connected (on the square {@link Topology} the model's own N/E/S/W river
 * connections are used instead).  Roads simply draw a spoke toward every one
 * of the eight raw neighbours that has a road.
 */
final class ClassicTileArt {

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
     * Composite every terrain-feature overlay for {@code tile} into the cell at
     * {@code (sx, sy)} sized {@code w}&times;{@code h}, on top of the already-drawn
     * base terrain and below the settlement/unit sprite.
     */
    void paintOverlays(Graphics2D g, Map map, Tile tile, int sx, int sy,
                       int w, int h) {
        if (this.packPresent) {
            paintFromPack(g, map, tile, sx, sy, w, h);
        } else {
            paintFallback(g, tile, sx, sy, w, h);
        }
    }

    /** The classic path: composite the square {@code PHYS0.SS} overlays. */
    private void paintFromPack(Graphics2D g, Map map, Tile tile, int sx, int sy,
                               int w, int h) {
        final int x = tile.getX();
        final int y = tile.getY();

        // Coastline feathering used to be drawn here from the extracted 8x8 beach
        // quarter-tiles (frames 108..139); that mechanism was removed after live
        // comparison against the original reference screenshots showed the
        // extracted frames render a green-flecked fringe the original never had
        // (a likely palette/extraction fidelity issue, not a rendering bug in this
        // class). The water-side coastline is now handled procedurally instead --
        // see ClassicMapViewer.blendWaterBorders.

        // Terrain relief: forest trees, or the hill/mountain massif.  These are
        // area features, so connectivity is over the four cardinal neighbours.
        if (tile.isForested()) {
            drawFrame(g, FOREST + areaMask(map, x, y, Feature.FOREST), sx, sy, w, h);
        } else if (isType(tile, "model.tile.mountains")) {
            drawFrame(g, MOUNTAINS + areaMask(map, x, y, Feature.MOUNTAINS), sx, sy, w, h);
        } else if (isType(tile, "model.tile.hills")) {
            drawFrame(g, HILLS + areaMask(map, x, y, Feature.HILLS), sx, sy, w, h);
        }

        // A plowed field sits on open, cleared ground.
        if (isPlowed(tile)) {
            drawFrame(g, PLOWED, sx, sy, w, h);
        }

        // River: minor (magnitude 1) or major (>= 2), connectivity folded from
        // the raw-grid neighbours (see class comment).
        final TileImprovement river = tile.getRiver();
        if (river != null) {
            final int base = (river.getMagnitude() >= 2) ? RIVER_MAJOR : RIVER_MINOR;
            drawFrame(g, base + riverMask(map, x, y), sx, sy, w, h);
        }

        // Road: centre hub plus a spoke toward each raw neighbour with a road.
        if (tile.hasRoad()) {
            drawFrame(g, ROAD_HUB, sx, sy, w, h);
            for (int[] spoke : ROAD_SPOKES) {
                if (hasRoad(map, x + spoke[0], y + spoke[1])) {
                    drawFrame(g, spoke[2], sx, sy, w, h);
                }
            }
        }

        // Resource marker, then a lost-city rumour on top of everything.
        if (tile.hasResource()) {
            final int r = resourceFrame(tile.getResource());
            if (r >= 0) drawFrame(g, r, sx, sy, w, h);
        }
        if (tile.hasLostCityRumour()) {
            drawFrame(g, LOST_CITY, sx, sy, w, h);
        }
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
    private boolean hasFeature(Map map, int x, int y, Feature feature) {
        final Tile t = map.getTile(x, y);
        if (t == null) return false;
        switch (feature) {
        case FOREST:    return t.isForested();
        case HILLS:     return isType(t, "model.tile.hills");
        case MOUNTAINS: return isType(t, "model.tile.mountains");
        default:        return false;
        }
    }

    /** 4-bit cardinal-neighbour connectivity mask for an area feature. */
    private int areaMask(Map map, int x, int y, Feature feature) {
        int m = 0;
        if (hasFeature(map, x, y - 1, feature)) m |= N;
        if (hasFeature(map, x + 1, y, feature)) m |= E;
        if (hasFeature(map, x, y + 1, feature)) m |= S;
        if (hasFeature(map, x - 1, y, feature)) m |= W;
        return m;
    }

    private boolean hasRiver(Map map, int x, int y) {
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
    private int riverMask(Map map, int x, int y) {
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

    private boolean hasRoad(Map map, int x, int y) {
        final Tile t = map.getTile(x, y);
        return t != null && t.hasRoad();
    }


    // Tile-feature predicates.

    private static boolean isType(Tile tile, String typeId) {
        return tile.getType() != null && typeId.equals(tile.getType().getId());
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
