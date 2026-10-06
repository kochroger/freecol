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

import java.awt.image.Raster;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.IntConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Resource;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * Golden checks of the terrain against named frames of the original
 * (M1c design 10 §12), driven by the fixtures in
 * {@code test/expected-data/classic-terrain/} (§12.1, Critic 10(b)).
 * For each check frame the fixture's map becomes a server game, the tiles
 * explored by then are explored for the Dutch player, and the client view
 * is the game as the login sends it
 * ({@link ClassicTerrainOracleTest#clientView}); the terrain composer
 * ({@link ClassicTerrainComposer}) reads it through the oracle, as the map
 * viewer does.
 *
 * <ul>
 *   <li><b>W6d, the fog ring's true terrain</b> (§5 acceptance, LF #1407,
 *       FS #876 and #1042): the oracle gives every explored tile the
 *       client's type, every unexplored ring tile the fixture's true type,
 *       every other tile null (least knowledge); the fixture's
 *       {@code fringe}/{@code blend} lines name the sprite the composer
 *       puts at that side (always, with tag sheets), the {@code quarter}
 *       lines the coast quarter of the rule {@code 108 + 4c + q}
 *       (reference code until W6b), and the multiplayer fallback (unknown
 *       ring, F-W6d-MP) gets every {@code truth} line wrong.</li>
 *   <li><b>W6a, whole frames</b> (§6.4, §12.2): each {@code compare} line
 *       composes all 180 cells of the view with the pack's index sheets and
 *       compares them with the clip frame index for index: 0 px off, the
 *       counted pointer pixels (indices 0/7/15 where we differ) excused,
 *       the unit cells excluded, and until W6b the explored water cells
 *       with land around them (the coast quarters).  Plus the
 *       {@code histogram} and {@code cycling} cell lines.</li>
 *   <li><b>W6c, the water cycling</b> (§7.4, §12.2): the {@code palette},
 *       {@code phases} and {@code sprite} lines -- the clips' palettes are
 *       phases of VICEROY.PAL, 8 consecutive phases of a sea lane and of
 *       its ocean neighbour equal our composed cells in index and colour,
 *       and the cycling river of fog-start #6543 keeps its cycling pixels
 *       ({@link #testCyclingMatchesTheOriginal}).</li>
 *   <li>The pixel checks need the converted pack and
 *       {@code -Dclassic.clips}, else they are skipped with a note (Critic
 *       10(a)(d)); "frame n" is the last PNG at or before n (Critic
 *       10(c)).</li>
 * </ul>
 * W6b extends this class with the coast quarters and the beach corners.
 */
public class ClassicTerrainGoldenTest extends FreeColTestCase {

    /** The fixtures' folder, relative to the repo. */
    static final File FIXTURES = new File("test/expected-data/classic-terrain");

    /** The system property naming the recordings folder. */
    static final String CLIPS_PROPERTY = "classic.clips";

    /** The TERRAIN.SS frame of every FreeCol tile type (design 10 §6.1). */
    static final java.util.Map<String, Integer> ALIAS = new HashMap<>();
    static {
        final Object[][] table = {
            { 0, "tundra", "borealForest" }, { 1, "desert", "scrubForest" },
            { 2, "plains", "mixedForest", "hills", "mountains" },
            { 3, "prairie", "broadleafForest" }, { 4, "grassland", "coniferForest" },
            { 5, "savannah", "tropicalForest" }, { 6, "marsh", "wetlandForest" },
            { 7, "swamp", "rainForest" }, { 9, "arctic" },
            { 10, "ocean", "lake", "greatRiver" }, { 11, "highSeas" }
        };
        for (Object[] row : table) {
            for (int i = 1; i < row.length; i++) {
                ALIAS.put("model.tile." + row[i], (Integer)row[0]);
            }
        }
    }

    /** The sides N, E, S, W: offsets (the side masks are PHYS0 104 + d). */
    private static final int[][] SIDES = ClassicTerrainComposer.SIDES;
    private static final String SIDE_NAMES = "NESW";

    /** The quarters NW, NE, SE, SW: their 3 neighbours, clockwise (§8.1). */
    private static final int[][][] CORNERS = {
        { { -1, 0 }, { -1, -1 }, { 0, -1 } },  // NW: W, NW, N
        { { 0, -1 }, { 1, -1 }, { 1, 0 } },    // NE: N, NE, E
        { { 1, 0 }, { 1, 1 }, { 0, 1 } },      // SE: E, SE, S
        { { 0, 1 }, { -1, 1 }, { -1, 0 } }     // SW: S, SW, W
    };
    private static final String[] QUARTER_NAMES = { "NW", "NE", "SE", "SW" };

    /** The view: 15x12 cells, the map area at y = 8 of the 320x200 screen. */
    private static final int COLS = 15, ROWS = 12, MAP_Y = 8;


    // The fixture

    /** One tile line. */
    static final class FixtureTile {
        final String type;   // null for '?'
        final int explored;  // the first frame showing it explored, or -1
        final int base;      // the original's own TERRAIN frame, or -1
        final String resource; // a resource type id, or null

        FixtureTile(String type, int explored, int base, String resource) {
            this.type = type;
            this.explored = explored;
            this.base = base;
            this.resource = resource;
        }
    }

    /** One check line. */
    static final class Check {
        final String kind;   // fringe, blend, quarter
        final int frame, x, y, part, expected; // part: side or quarter; -1 = none
        final boolean truth;
        final String line;

        Check(String kind, int frame, int x, int y, int part, int expected,
              boolean truth, String line) {
            this.kind = kind;
            this.frame = frame;
            this.x = x;
            this.y = y;
            this.part = part;
            this.expected = expected;
            this.truth = truth;
            this.line = line;
        }
    }

    /**
     * One whole-frame line: {@code compare <frame> px=<n> pointer=<n>
     * cells=<explored>,<fringe>,<dark> [exclude=<x>,<y>;...] [w6b=<n>]}.
     */
    static final class Compare {
        final int frame, px, pointer, explored, fringe, dark, w6b;
        final Set<Long> exclude = new HashSet<>();
        final String line;

        Compare(int frame, int px, int pointer, int[] cells, int w6b, String line) {
            this.frame = frame;
            this.px = px;
            this.pointer = pointer;
            this.explored = cells[0];
            this.fringe = cells[1];
            this.dark = cells[2];
            this.w6b = w6b;
            this.line = line;
        }
    }

    /**
     * One cell line: {@code histogram <frame> <c>,<r> <index>x<n> ...} (the
     * composed cell's indices) or {@code cycling <frame> <x>,<y> <n>} (the
     * composed cell's pixels in 120-127).
     */
    static final class CellCheck {
        final String kind, line;
        final int frame, a, b;
        final TreeMap<Integer, Integer> counts = new TreeMap<>();
        int cycling = -1;

        CellCheck(String kind, int frame, int a, int b, String line) {
            this.kind = kind;
            this.frame = frame;
            this.a = a;
            this.b = b;
            this.line = line;
        }
    }

    /**
     * One W6c line (design 10 §7.4, §12.2):
     * <ul>
     *   <li>{@code palette <frame> <phase>}: the frame's PLTE is the game
     *       palette at that phase (its entries 120-127);</li>
     *   <li>{@code phases <x>,<y> <cycling> <frame>...}: the tile's
     *       composed cell holds that many cycling pixels at every frame,
     *       equals each frame's cell index for index, its colours through
     *       the game palette at each frame's phase are the frame's, and the
     *       frames' phases follow one another (8 frames: all 8);</li>
     *   <li>{@code sprite <frame> <c>,<r> <Pnnn|Tnnn> <opaque> <cycling>}:
     *       screen cell (c,r) holds the sprite at its opaque pixels, its
     *       cycling ones included, in the colours of the frame's phase;
     *       through the composer as an overlay it keeps them.</li>
     * </ul>
     */
    static final class CycleCheck {
        final String kind, line;
        final int[] frames;
        int a, b, expected = -1, sprite = -1, opaque = -1, cycling = -1;
        boolean terrainSheet;

        CycleCheck(String kind, int[] frames, String line) {
            this.kind = kind;
            this.frames = frames;
            this.line = line;
        }
    }

    /** A fixture file (§12.1, plus the W6d check lines and W6a's frames). */
    static final class Fixture {
        final String name;
        String clip, defaultType;
        int width, height, vx, vy;
        final java.util.Map<Long, FixtureTile> tiles = new LinkedHashMap<>();
        final List<Check> checks = new ArrayList<>();
        final List<Compare> compares = new ArrayList<>();
        final List<CellCheck> cells = new ArrayList<>();
        final List<CycleCheck> cycles = new ArrayList<>();

        Fixture(String name) {
            this.name = name;
        }

        static long key(int x, int y) {
            return ((long)x << 32) | (y & 0xFFFFFFFFL);
        }

        FixtureTile tile(int x, int y) {
            return this.tiles.get(key(x, y));
        }

        /** @return The type id at a tile (the default for unlisted and '?'). */
        String typeAt(int x, int y) {
            final FixtureTile t = tile(x, y);
            return (t == null || t.type == null) ? this.defaultType : t.type;
        }

        /** @return Whether a tile is explored at a frame. */
        boolean exploredAt(int x, int y, int frame) {
            final FixtureTile t = tile(x, y);
            return t != null && t.explored >= 0 && t.explored <= frame;
        }

        /** @return The frames the W6d checks look at, in order. */
        TreeSet<Integer> frames() {
            final TreeSet<Integer> s = new TreeSet<>();
            for (Check c : this.checks) s.add(c.frame);
            return s;
        }

        /**
         * The alias table of this fixture: the design's, with a tile's
         * {@code base=} for its type (the T008 tile gets a type mapped to
         * 8, §12.1; the fixture must give every tile of that type the same
         * base).
         *
         * @return Tile type id to TERRAIN frame.
         */
        java.util.Map<String, Integer> alias() {
            final java.util.Map<String, Integer> a = new HashMap<>(ALIAS);
            for (FixtureTile t : this.tiles.values()) {
                if (t.base >= 0 && t.type != null) a.put(t.type, t.base);
            }
            for (FixtureTile t : this.tiles.values()) {
                if (t.type != null && t.base < 0 && a.get(t.type) != (int)ALIAS.get(t.type)) {
                    throw new IllegalStateException(this.name + ": type " + t.type
                        + " has a base= on another tile");
                }
            }
            return a;
        }

        static Fixture load(File f) throws IOException {
            final Fixture fx = new Fixture(f.getName());
            int n = 0;
            for (String raw : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                n++;
                final String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                final String[] w = line.split("\\s+");
                final String at = f.getName() + ":" + n + ": ";
                switch (w[0]) {
                case "clip": fx.clip = w[1]; break;
                case "default": fx.defaultType = w[1]; break;
                case "map": {
                    final int[] xy = xy(w[1]);
                    fx.width = xy[0];
                    fx.height = xy[1];
                    break;
                }
                case "view": {
                    final int[] xy = xy(w[1]);
                    fx.vx = xy[0];
                    fx.vy = xy[1];
                    break;
                }
                case "fringe": case "blend": case "quarter": {
                    final int frame = Integer.parseInt(w[1]);
                    final int[] xy = xy(w[2]);
                    final int part = ("quarter".equals(w[0]))
                        ? java.util.Arrays.asList(QUARTER_NAMES).indexOf(w[3])
                        : SIDE_NAMES.indexOf(w[3]);
                    if (part < 0 || w[3].length() > 2) {
                        throw new IOException(at + "bad side or quarter " + w[3]);
                    }
                    final int expected = ("-".equals(w[4])) ? -1
                        : Integer.parseInt(w[4].substring(1));
                    final boolean truth = w.length > 5 && "truth".equals(w[5]);
                    fx.checks.add(new Check(w[0], frame, xy[0], xy[1], part,
                                            expected, truth, line));
                    break;
                }
                case "compare": {
                    final java.util.Map<String, String> kv = keyValues(w, 2, at);
                    if (!kv.containsKey("px") || !kv.containsKey("pointer")
                        || !kv.containsKey("cells")) {
                        throw new IOException(at + "compare needs px=, pointer=, cells=");
                    }
                    final String[] cs = kv.get("cells").split(",");
                    final Compare c = new Compare(Integer.parseInt(w[1]),
                        Integer.parseInt(kv.get("px")),
                        Integer.parseInt(kv.get("pointer")),
                        new int[] { Integer.parseInt(cs[0]), Integer.parseInt(cs[1]),
                                    Integer.parseInt(cs[2]) },
                        Integer.parseInt(kv.getOrDefault("w6b", "0")), line);
                    if (kv.containsKey("exclude")) {
                        for (String e : kv.get("exclude").split(";")) {
                            final int[] xy = xy(e);
                            c.exclude.add(key(xy[0], xy[1]));
                        }
                    }
                    fx.compares.add(c);
                    break;
                }
                case "histogram": {
                    final int[] cr = xy(w[2]);
                    final CellCheck c = new CellCheck(w[0], Integer.parseInt(w[1]),
                                                      cr[0], cr[1], line);
                    for (int i = 3; i < w.length; i++) {
                        final String[] p = w[i].split("x");
                        c.counts.put(Integer.parseInt(p[0]), Integer.parseInt(p[1]));
                    }
                    fx.cells.add(c);
                    break;
                }
                case "cycling": {
                    final int[] xy = xy(w[2]);
                    final CellCheck c = new CellCheck(w[0], Integer.parseInt(w[1]),
                                                      xy[0], xy[1], line);
                    c.cycling = Integer.parseInt(w[3]);
                    fx.cells.add(c);
                    break;
                }
                case "palette": {
                    final CycleCheck c = new CycleCheck(w[0],
                        new int[] { Integer.parseInt(w[1]) }, line);
                    c.expected = Integer.parseInt(w[2]);
                    fx.cycles.add(c);
                    break;
                }
                case "phases": {
                    final int[] xy = xy(w[1]);
                    final int[] frames = new int[w.length - 3];
                    for (int i = 3; i < w.length; i++) frames[i - 3] = Integer.parseInt(w[i]);
                    if (frames.length < 2) throw new IOException(at + "phases needs frames");
                    final CycleCheck c = new CycleCheck(w[0], frames, line);
                    c.a = xy[0];
                    c.b = xy[1];
                    c.cycling = Integer.parseInt(w[2]);
                    fx.cycles.add(c);
                    break;
                }
                case "sprite": {
                    final int[] cr = xy(w[2]);
                    final CycleCheck c = new CycleCheck(w[0],
                        new int[] { Integer.parseInt(w[1]) }, line);
                    c.a = cr[0];
                    c.b = cr[1];
                    if (!w[3].matches("[PT]\\d+")) throw new IOException(at + "bad sprite " + w[3]);
                    c.terrainSheet = w[3].charAt(0) == 'T';
                    c.sprite = Integer.parseInt(w[3].substring(1));
                    c.opaque = Integer.parseInt(w[4]);
                    c.cycling = Integer.parseInt(w[5]);
                    fx.cycles.add(c);
                    break;
                }
                default: {
                    final int[] xy = xy(w[0]);
                    int explored = -1, base = -1;
                    String res = null;
                    for (int i = 2; i < w.length; i++) {
                        if (w[i].startsWith("explored@")) {
                            explored = Integer.parseInt(w[i].substring(9));
                        } else if (w[i].startsWith("base=")) {
                            base = Integer.parseInt(w[i].substring(5));
                        } else if (w[i].startsWith("res=")) {
                            res = w[i].substring(4);
                        }
                    }
                    fx.tiles.put(key(xy[0], xy[1]), new FixtureTile(
                        ("?".equals(w[1])) ? null : w[1], explored, base, res));
                    break;
                }
                }
            }
            if (fx.clip == null || fx.defaultType == null || fx.width <= 0) {
                throw new IOException(f + ": needs clip, map and default lines");
            }
            return fx;
        }

        private static java.util.Map<String, String> keyValues(String[] w, int from,
                                                               String at)
            throws IOException {
            final java.util.Map<String, String> kv = new HashMap<>();
            for (int i = from; i < w.length; i++) {
                final int eq = w[i].indexOf('=');
                if (eq <= 0) throw new IOException(at + "expected key=value: " + w[i]);
                kv.put(w[i].substring(0, eq), w[i].substring(eq + 1));
            }
            return kv;
        }

        private static int[] xy(String s) {
            final String[] p = s.split(",");
            return new int[] { Integer.parseInt(p[0]), Integer.parseInt(p[1]) };
        }
    }


    // The games of a check frame

    /** A check frame's games and oracles. */
    private static final class Frame {
        final Fixture fx;
        final int frame;
        final Game server, client;
        final ClassicTerrainOracle truth, unknown;

        Frame(Fixture fx, int frame) {
            this.fx = fx;
            this.frame = frame;
            this.server = getStandardGame("classic");
            final Specification spec = this.server.getSpecification();
            final MapBuilder b = new MapBuilder(this.server)
                .setDimensions(fx.width, fx.height)
                .setBaseTileType(type(spec, fx.defaultType));
            for (java.util.Map.Entry<Long, FixtureTile> e : fx.tiles.entrySet()) {
                final int x = (int)(e.getKey() >> 32), y = (int)(long)e.getKey();
                b.setTileType(x, y, type(spec, fx.typeAt(x, y)));
            }
            this.server.changeMap(b.build());
            for (java.util.Map.Entry<Long, FixtureTile> e : fx.tiles.entrySet()) {
                if (e.getValue().resource == null) continue;
                final int x = (int)(e.getKey() >> 32), y = (int)(long)e.getKey();
                final Tile t = this.server.getMap().getTile(x, y);
                t.addResource(new Resource(this.server, t,
                    spec.getResourceType(e.getValue().resource)));
            }
            final ServerPlayer dutch = (ServerPlayer)this.server
                .getPlayerByNationId("model.nation.dutch");
            final List<Tile> seen = new ArrayList<>();
            for (java.util.Map.Entry<Long, FixtureTile> e : fx.tiles.entrySet()) {
                final int x = (int)(e.getKey() >> 32), y = (int)(long)e.getKey();
                if (fx.exploredAt(x, y, frame)) seen.add(this.server.getMap().getTile(x, y));
            }
            dutch.exploreTiles(seen);
            this.client = ClassicTerrainOracleTest.clientView(this.server, dutch);
            this.truth = ClassicTerrainOracleTest.oracle(this.client, true, this.server);
            this.unknown = ClassicTerrainOracleTest.oracle(this.client, false, this.server);
        }

        private static TileType type(Specification spec, String id) {
            final TileType t = spec.getTileType(id);
            assertNotNull(id, t);
            return t;
        }

        String at() {
            return this.fx.name + " #" + this.frame;
        }

        /** @return The client's map as the composer reads it, through an oracle. */
        Source source(ClassicTerrainOracle o) {
            return new Source(this.client.getMap(), o);
        }
    }

    /**
     * The map as the viewer's layer reads it: the client's explored state
     * and types, the oracle's types for the fog ring.
     */
    static final class Source implements ClassicTerrainComposer.TerrainSource {
        final Map map;
        final ClassicTerrainOracle oracle;

        Source(Map map, ClassicTerrainOracle oracle) {
            this.map = map;
            this.oracle = oracle;
        }

        @Override
        public boolean onMap(int x, int y) {
            return this.map.getTile(x, y) != null;
        }

        @Override
        public boolean explored(int x, int y) {
            final Tile t = this.map.getTile(x, y);
            return t != null && t.isExplored();
        }

        @Override
        public TileType type(int x, int y) {
            return this.oracle.trueType(x, y);
        }

        @Override
        public void overlays(int x, int y, IntConsumer frames) {
            final Tile t = this.map.getTile(x, y);
            if (t != null && t.isExplored()) {
                ClassicTileArt.overlayFrames(this.map, t, this::type, frames);
            }
        }
    }


    // The rules

    /** A composer over sheets with the fixture's alias table. */
    private static ClassicTerrainComposer composer(Fixture fx, ClassicIndexSheet terrain,
                                                   ClassicIndexSheet phys) {
        return composer(fx, terrain, phys, ClassicTerrainComposer.DARK_SIDES_ALWAYS);
    }

    /** The same with a choice of the pinned rule. */
    private static ClassicTerrainComposer composer(Fixture fx, ClassicIndexSheet terrain,
                                                   ClassicIndexSheet phys,
                                                   boolean darkSidesAlways) {
        final java.util.Map<String, Integer> alias = fx.alias();
        return new ClassicTerrainComposer(terrain, phys,
                                          id -> alias.getOrDefault(id, -1),
                                          darkSidesAlways, ClassicTerrainComposer.SIDE_ORDER);
    }

    /** The sprite results of one check. */
    private static final class Derived {
        int sprite = -1;           // the derived sprite (-1: none)
        int[] pixels;              // the composed 16x16 cell (-1 unknown)
        int[][] compare;           // the {px, py} the check compares
    }

    private static boolean isLand(TileType t) {
        return t != null && !t.isWater();
    }

    /**
     * Derive one check's sprite (with the tag composer) and, with the
     * pack's sheets, the composed pixels it compares.
     *
     * @param f The frame's games.
     * @param o The oracle (truth or unknown).
     * @param c The check.
     * @param tag The composer over the tag sheets.
     * @param real The composer over the pack's sheets, or null.
     * @param phys The pack's PHYS0.SS, or null.
     * @return The result.
     */
    private static Derived derive(Frame f, ClassicTerrainOracle o, Check c,
                                  ClassicTerrainComposer tag,
                                  ClassicTerrainComposer real, ClassicIndexSheet phys) {
        final Map map = f.client.getMap();
        final Tile tile = map.getTile(c.x, c.y);
        final Derived d = new Derived();
        if ("quarter".equals(c.kind)) {
            // The coast quarter rule, reference code until W6b.
            assertTrue(f.at() + " " + c.line + ": explored water",
                       tile.isExplored() && tile.getType().isWater());
            int bits = 0;
            for (int k = 0; k < 3; k++) {
                final int[] nb = CORNERS[c.part][k];
                if (isLand(o.trueType(c.x + nb[0], c.y + nb[1]))) bits |= 1 << k;
            }
            d.sprite = (bits == 0) ? -1 : 108 + 4 * bits + c.part;
            if (phys != null && d.sprite >= 0) {
                final int qx = (c.part == 1 || c.part == 2) ? 8 : 0;
                final int qy = (c.part >= 2) ? 8 : 0;
                d.pixels = new int[256];
                java.util.Arrays.fill(d.pixels, -1);
                final List<int[]> cmp = new ArrayList<>();
                for (int j = 0; j < 8; j++) {
                    for (int i = 0; i < 8; i++) {
                        final int v = phys.index(d.sprite, i, j);
                        // 0 keeps what is below, FD is the land fill (O1).
                        if (v == 0 || v == ClassicIndexSheet.TRANSPARENT) continue;
                        d.pixels[(qy + j) * 16 + qx + i] = v;
                        cmp.add(new int[] { qx + i, qy + j });
                    }
                }
                d.compare = cmp.toArray(new int[0][]);
            }
            return d;
        }
        assertEquals(f.at() + " " + c.line + ": " + ("fringe".equals(c.kind)
                ? "a dark tile" : "an explored tile"), "fringe".equals(c.kind),
            !tile.isExplored());
        final Source src = f.source(o);
        // The sprite: the tag at the side's own mask pixels (not those it
        // shares with another side).
        final byte[] t = new byte[256];
        tag.composeCell(src, c.x, c.y, t, 0, 16);
        int sprite = -2;
        for (int k : tag.mask(c.part)) {
            boolean shared = false;
            for (int s = 0; s < 4; s++) {
                if (s == c.part) continue;
                for (int k2 : tag.mask(s)) shared |= k2 == k;
            }
            if (shared) continue;
            final int v = t[k] & 0xFF;
            final int s = (v >= ClassicTerrainSheets.TAG
                && v < ClassicTerrainSheets.TAG + 12) ? v - ClassicTerrainSheets.TAG : -1;
            assertTrue(f.at() + " " + c.line + ": one sprite on the side",
                       sprite == -2 || sprite == s);
            sprite = s;
        }
        d.sprite = sprite;
        if (real != null) {
            final byte[] px = new byte[256];
            real.composeCell(src, c.x, c.y, px, 0, 16);
            d.pixels = new int[256];
            for (int k = 0; k < 256; k++) d.pixels[k] = px[k] & 0xFF;
            final List<int[]> cmp = new ArrayList<>();
            for (int k : real.mask(c.part)) cmp.add(new int[] { k % 16, k / 16 });
            d.compare = cmp.toArray(new int[0][]);
        }
        return d;
    }

    /**
     * Compare a derived cell with a clip frame.
     *
     * @return { matching px, compared px }.
     */
    private static int[] match(Derived d, Raster r, Fixture fx, Check c) {
        final int ox = 16 * (c.x - fx.vx), oy = MAP_Y + 16 * (c.y - fx.vy);
        int ok = 0;
        for (int[] p : d.compare) {
            if (r.getSample(ox + p[0], oy + p[1], 0) == d.pixels[p[1] * 16 + p[0]]) ok++;
        }
        return new int[] { ok, d.compare.length };
    }

    private static String sprite(Check c, int s) {
        return (s < 0) ? "-" : (("quarter".equals(c.kind)) ? "P" : "T")
            + String.format("%03d", s);
    }

    /**
     * Whether an explored water tile carries a coast quarter: a land tile
     * among its 8 neighbours (W6b draws it; until then the cell is not
     * compared).
     */
    private static boolean coastCell(Frame f, int x, int y) {
        final Tile tile = f.client.getMap().getTile(x, y);
        if (tile == null || !tile.isExplored() || !tile.getType().isWater()) return false;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if ((dx != 0 || dy != 0) && isLand(f.truth.trueType(x + dx, y + dy))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** @return 0 explored, 1 fringe (an explored orthogonal neighbour), 2 plain dark. */
    private static int cellClass(Map map, int x, int y) {
        final Tile t = map.getTile(x, y);
        if (t.isExplored()) return 0;
        for (int[] s : SIDES) {
            final Tile n = map.getTile(x + s[0], y + s[1]);
            if (n != null && n.isExplored()) return 1;
        }
        return 2;
    }


    // Inputs

    private static List<Fixture> fixtures() throws IOException {
        final File[] files = FIXTURES.listFiles((d, n) -> n.endsWith(".txt"));
        assertNotNull("fixtures in " + FIXTURES.getAbsolutePath(), files);
        java.util.Arrays.sort(files);
        final List<Fixture> ret = new ArrayList<>();
        for (File f : files) ret.add(Fixture.load(f));
        assertTrue("fixtures", ret.size() >= 2);
        return ret;
    }

    /** @return The recordings folder, or null (with a note) without one. */
    private File clips() {
        final String p = System.getProperty(CLIPS_PROPERTY);
        final File d = (p == null || p.isBlank()) ? null : new File(p);
        if (d == null || !d.isDirectory()) {
            System.err.println("ClassicTerrainGoldenTest." + getName()
                + ": pixel checks skipped, no recordings (-D" + CLIPS_PROPERTY
                + "=<video/recordings>, got " + p + ")");
            return null;
        }
        return d;
    }

    /** @return The pack, or null (with a note) without index sheets. */
    private ClassicPackFiles pack() {
        final ClassicPackFiles p = ClassicPackFiles.runtime();
        final String status = ClassicPackFiles.indexStatus(p);
        if (!status.startsWith("index ")) {
            System.err.println("ClassicTerrainGoldenTest." + getName()
                + ": pixel checks skipped, " + status + " (ant classic-assets)");
            return null;
        }
        return p;
    }

    /** The PNG names of a clip folder by frame number. */
    private static final java.util.Map<File, TreeMap<Integer, File>> FRAMES = new HashMap<>();

    /** @return The last PNG at or before a frame (Critic 10(c)). */
    static File frameFile(File clipDir, int frame) {
        final TreeMap<Integer, File> m = FRAMES.computeIfAbsent(clipDir, d -> {
                final TreeMap<Integer, File> t = new TreeMap<>();
                final Pattern p = Pattern.compile("frame_(\\d+)\\.png");
                final File[] fs = d.listFiles();
                if (fs != null) {
                    for (File f : fs) {
                        final Matcher mt = p.matcher(f.getName());
                        if (mt.matches()) t.put(Integer.parseInt(mt.group(1)), f);
                    }
                }
                return t;
            });
        final java.util.Map.Entry<Integer, File> e = m.floorEntry(frame);
        return (e == null) ? null : e.getValue();
    }


    // Tests

    /** The fixtures parse, and every check names a tile of its view. */
    public void testFixturesParse() throws IOException {
        int compares = 0;
        for (Fixture fx : fixtures()) {
            assertEquals(fx.name, 58, fx.width);
            assertEquals(fx.name, 72, fx.height);
            assertFalse(fx.name, fx.checks.isEmpty());
            assertFalse(fx.name, fx.compares.isEmpty());
            fx.alias();
            for (Check c : fx.checks) {
                final int cx = c.x - fx.vx, cy = c.y - fx.vy;
                assertTrue(fx.name + " " + c.line, cx >= 0 && cx < COLS && cy >= 0 && cy < ROWS);
                assertTrue(fx.name + " " + c.line, ALIAS.containsKey(fx.typeAt(c.x, c.y)));
            }
            for (Compare c : fx.compares) {
                assertEquals(fx.name + " " + c.line, COLS * ROWS,
                             c.explored + c.fringe + c.dark);
            }
            compares += fx.compares.size();
        }
        assertTrue("compare lines " + compares, compares >= 6);
        // Frame lookup: the last PNG at or before n.
        final File tmp = Files.createTempDirectory("clips").toFile();
        try {
            for (int n : new int[] { 714, 718 }) {
                assertTrue(new File(tmp, String.format("frame_%06d.png", n)).createNewFile());
            }
            assertNull(frameFile(tmp, 713));
            assertEquals("frame_000714.png", frameFile(tmp, 716).getName());
            assertEquals("frame_000718.png", frameFile(tmp, 718).getName());
        } finally {
            for (File f : tmp.listFiles()) f.delete();
            tmp.delete();
        }
    }

    /**
     * The oracle on every check frame's client view: explored tiles give
     * the client's type, the ring's unexplored tiles the fixture's true
     * type, all other tiles null.
     */
    public void testOracleGivesTheRingsTrueTerrain() throws IOException {
        for (Fixture fx : fixtures()) {
            for (int frame : fx.frames()) {
                final Frame f = new Frame(fx, frame);
                final Map map = f.client.getMap();
                int explored = 0, ring = 0;
                for (int y = 0; y < map.getHeight(); y++) {
                    for (int x = 0; x < map.getWidth(); x++) {
                        final String at = f.at() + " (" + x + "," + y + ")";
                        final boolean e = fx.exploredAt(x, y, frame);
                        assertEquals(at, e, map.getTile(x, y).isExplored());
                        final TileType t = f.truth.trueType(x, y);
                        if (e) {
                            explored++;
                            assertSame(at, map.getTile(x, y).getType(), t);
                            assertEquals(at, fx.typeAt(x, y), t.getId());
                        } else if (ClassicTerrainOracle.inRing(map, x, y)) {
                            ring++;
                            assertNotNull(at, t);
                            assertEquals(at, fx.typeAt(x, y), t.getId());
                            assertSame(at, f.client.getSpecification()
                                .getTileType(t.getId()), t);
                            assertNull(at, f.unknown.trueType(x, y));
                        } else {
                            assertNull(at, t);
                        }
                    }
                }
                assertTrue(f.at(), explored > 0 && ring > 0);
                assertTrue(f.truth.census(), f.truth.census()
                    .startsWith("server ring=" + ring + " known=" + ring + " "));
                System.out.println("ClassicTerrainGoldenTest: " + f.at()
                    + " explored=" + explored + " ring=" + ring
                    + " refused=" + f.truth.refusals());
            }
        }
    }

    /**
     * The W6d acceptance, now through the composer: with the true terrain
     * the check lines' sprites follow, and the clip's pixels agree (with
     * the pack and the clips); the multiplayer fallback gets every
     * {@code truth} line wrong.
     */
    public void testTrueTerrainMatchesTheOriginal() throws IOException {
        final ClassicPackFiles pack = pack();
        final File clips = clips();
        final ClassicIndexSheet terrain = (pack == null) ? null
            : pack.indexSheet(ClassicPackFiles.TERRAIN_SS);
        final ClassicIndexSheet phys = (pack == null) ? null
            : pack.indexSheet(ClassicPackFiles.PHYS0_SS);
        if (pack != null) {
            for (java.util.Map.Entry<String, Integer> e : ALIAS.entrySet()) {
                assertEquals(e.getKey(), (int)e.getValue(), pack.terrainSpriteFor(e.getKey()));
            }
        }
        int checks = 0, truthChecks = 0, pixelChecks = 0;
        final StringBuilder report = new StringBuilder();
        final List<String> failures = new ArrayList<>();
        for (Fixture fx : fixtures()) {
            final ClassicTerrainComposer tag = composer(fx,
                ClassicTerrainSheets.tagTerrain(), ClassicTerrainSheets.tagPhys());
            final ClassicTerrainComposer real = (pack == null) ? null
                : composer(fx, terrain, phys);
            for (int frame : fx.frames()) {
                final Frame f = new Frame(fx, frame);
                Raster r = null;
                if (clips != null && pack != null) {
                    final File png = frameFile(new File(clips, fx.clip), frame);
                    assertNotNull(fx.clip + " #" + frame, png);
                    r = ImageIO.read(png).getRaster();
                }
                for (Check c : fx.checks) {
                    if (c.frame != frame) continue;
                    checks++;
                    final String at = f.at() + " " + c.line;
                    final int refused = f.truth.refusals();
                    final Derived t = derive(f, f.truth, c, tag, real, phys);
                    final Derived u = derive(f, f.unknown, c, tag, real, phys);
                    // The rules ask nothing beyond the ring (least knowledge).
                    assertEquals(at + ": refusals", refused, f.truth.refusals());
                    assertEquals(at + ": refusals", 0, f.unknown.refusals());
                    assertEquals(at + ": derived", sprite(c, c.expected), sprite(c, t.sprite));
                    String px = "", upx = "";
                    boolean unknownWrong = u.sprite != t.sprite;
                    if (r != null && t.compare != null) {
                        final int[] m = match(t, r, fx, c);
                        assertEquals(at + ": px", m[1], m[0]);
                        assertTrue(at + ": px", m[1] > 0);
                        px = " " + m[0] + "/" + m[1] + " px";
                        pixelChecks++;
                        if (u.compare != null) {
                            final int[] um = match(u, r, fx, c);
                            upx = " " + um[0] + "/" + um[1] + " px";
                            unknownWrong |= um[0] != um[1];
                        }
                    }
                    if (c.truth) {
                        truthChecks++;
                        assertTrue(at + ": the fallback must fail it", unknownWrong);
                    } else {
                        assertFalse(at + ": needs no truth", unknownWrong);
                    }
                    report.append("  ").append(at).append(": ")
                        .append(sprite(c, t.sprite)).append(px)
                        .append(" | unknown ").append(sprite(c, u.sprite)).append(upx)
                        .append('\n');
                }
            }
        }
        assertTrue(checks >= 20);
        assertTrue(truthChecks >= 14);
        System.out.println("ClassicTerrainGoldenTest: " + checks + " checks, "
            + truthChecks + " need the truth, " + pixelChecks + " compared with the"
            + " clips\n" + report);
    }

    /**
     * W6a's whole frames (design 10 §6.4, §12.2): every {@code compare}
     * line's view composed cell by cell from the pack's index sheets
     * equals the clip frame, 0 px off.  Always: the cell classes
     * (explored, fringe, plain dark) and the least-knowledge rule over the
     * whole view; with the pack and the clips: the pixels.
     */
    public void testWholeFramesMatchTheOriginal() throws IOException {
        final ClassicPackFiles pack = pack();
        final File clips = clips();
        final ClassicIndexSheet terrain = (pack == null) ? null
            : pack.indexSheet(ClassicPackFiles.TERRAIN_SS);
        final ClassicIndexSheet phys = (pack == null) ? null
            : pack.indexSheet(ClassicPackFiles.PHYS0_SS);
        final StringBuilder report = new StringBuilder();
        final List<String> failures = new ArrayList<>();
        int frames = 0, pixelFrames = 0, altOff = 0;
        final StringBuilder altAt = new StringBuilder();
        for (Fixture fx : fixtures()) {
            final ClassicTerrainComposer tag = composer(fx,
                ClassicTerrainSheets.tagTerrain(), ClassicTerrainSheets.tagPhys());
            final ClassicTerrainComposer real = (pack == null) ? null
                : composer(fx, terrain, phys);
            // The alternative of the pinned rule DARK_SIDES_ALWAYS.
            final ClassicTerrainComposer alt = (pack == null) ? null
                : composer(fx, terrain, phys, !ClassicTerrainComposer.DARK_SIDES_ALWAYS);
            for (Compare cmp : fx.compares) {
                frames++;
                final Frame f = new Frame(fx, cmp.frame);
                final Map map = f.client.getMap();
                final String at = f.at() + " " + cmp.line;
                final Source src = f.source(f.truth);
                final int[] classes = new int[3];
                for (int r = 0; r < ROWS; r++) {
                    for (int c = 0; c < COLS; c++) {
                        classes[cellClass(map, fx.vx + c, fx.vy + r)]++;
                    }
                }
                final String cells = "cells=" + classes[0] + "," + classes[1]
                    + "," + classes[2];
                final String wantCells = "cells=" + cmp.explored + "," + cmp.fringe
                    + "," + cmp.dark;
                if (!wantCells.equals(cells)) failures.add(at + ": " + cells);
                // Least knowledge over the whole view.
                final byte[] cell = new byte[256];
                for (int r = 0; r < ROWS; r++) {
                    for (int c = 0; c < COLS; c++) {
                        tag.composeCell(src, fx.vx + c, fx.vy + r, cell, 0, 16);
                    }
                }
                if (f.truth.refusals() != 0) failures.add(at + ": refusals");
                if (real == null || clips == null) continue;
                final File png = frameFile(new File(clips, fx.clip), cmp.frame);
                assertNotNull(fx.clip + " #" + cmp.frame, png);
                final Raster ras = ImageIO.read(png).getRaster();
                int compared = 0, off = 0, pointer = 0, excluded = 0, w6b = 0;
                final StringBuilder bad = new StringBuilder();
                for (int r = 0; r < ROWS; r++) {
                    for (int c = 0; c < COLS; c++) {
                        final int x = fx.vx + c, y = fx.vy + r;
                        if (cmp.exclude.contains(Fixture.key(x, y))) {
                            excluded++;
                            continue;
                        }
                        if (coastCell(f, x, y)) {
                            w6b++;
                            continue;
                        }
                        alt.composeCell(src, x, y, cell, 0, 16);
                        for (int k = 0; k < 256; k++) {
                            final int v = ras.getSample(16 * c + k % 16,
                                                        MAP_Y + 16 * r + k / 16, 0);
                            if (v != (cell[k] & 0xFF) && v != 0 && v != 7 && v != 15) {
                                altOff++;
                                altAt.append(" ").append(f.at()).append(" (").append(x)
                                    .append(',').append(y).append(") (").append(k % 16)
                                    .append(',').append(k / 16).append(')');
                            }
                        }
                        real.composeCell(src, x, y, cell, 0, 16);
                        int cellOff = 0;
                        final StringBuilder first = new StringBuilder();
                        for (int k = 0; k < 256; k++) {
                            final int v = ras.getSample(16 * c + k % 16,
                                                        MAP_Y + 16 * r + k / 16, 0);
                            final int ours = cell[k] & 0xFF;
                            if (v == ours) {
                                compared++;
                            } else if (v == 0 || v == 7 || v == 15) {
                                pointer++;
                            } else {
                                compared++;
                                off++;
                                if (++cellOff <= 4) {
                                    first.append(" (").append(k % 16).append(',')
                                        .append(k / 16).append(") ").append(ours)
                                        .append('/').append(v);
                                }
                            }
                        }
                        if (cellOff > 0) {
                            bad.append(" (").append(x).append(',').append(y)
                                .append(")=").append(cellOff).append(" [ours/clip")
                                .append(first).append(']');
                        }
                    }
                }
                pixelFrames++;
                final String line = "  " + at + ": " + cells + " px=" + compared
                    + " off=" + off + " pointer=" + pointer + " excluded=" + excluded
                    + " w6b=" + w6b + ((bad.length() > 0) ? ", off at" + bad : "");
                report.append(line).append('\n');
                if (off != 0 || cmp.px != compared || cmp.pointer != pointer
                    || cmp.exclude.size() != excluded || cmp.w6b != w6b) failures.add(line);
                if (f.truth.refusals() != 0) failures.add(at + ": refusals");
            }
        }
        System.out.println("ClassicTerrainGoldenTest: " + frames + " whole frames, "
            + pixelFrames + " compared with the clips\n" + report);
        System.out.println("ClassicTerrainGoldenTest: DARK_SIDES_ALWAYS="
            + !ClassicTerrainComposer.DARK_SIDES_ALWAYS + " would be " + altOff
            + " px off:" + altAt);
        assertTrue("whole frames: " + failures, failures.isEmpty());
        if (pixelFrames > 0) {
            assertTrue("DARK_SIDES_ALWAYS is pinned by the clips", altOff > 0);
        }
        assertTrue("compare lines " + frames, frames >= 6);
    }

    /**
     * Open item O3, the side order (design 10 §8.2, pin by golden): every
     * order of the 4 sides on the whole frames.  The committed
     * {@link ClassicTerrainComposer#SIDE_ORDER} must give the fewest
     * mismatches; the orders that give as few are reported (the clips do
     * not tell them apart where no two different sprites meet at an
     * overlap pixel).
     */
    public void testSideOrderCandidates() throws IOException {
        final ClassicPackFiles pack = pack();
        final File clips = clips();
        if (pack == null || clips == null) return;
        final ClassicIndexSheet terrain = pack.indexSheet(ClassicPackFiles.TERRAIN_SS);
        final ClassicIndexSheet phys = pack.indexSheet(ClassicPackFiles.PHYS0_SS);
        final List<int[]> orders = new ArrayList<>();
        permute(new int[] { 0, 1, 2, 3 }, 0, orders);
        assertEquals(24, orders.size());
        final int[] off = new int[orders.size()];
        final byte[] cell = new byte[256];
        for (Fixture fx : fixtures()) {
            final java.util.Map<String, Integer> alias = fx.alias();
            final List<ClassicTerrainComposer> comps = new ArrayList<>();
            for (int[] o : orders) {
                comps.add(new ClassicTerrainComposer(terrain, phys,
                    id -> alias.getOrDefault(id, -1),
                    ClassicTerrainComposer.DARK_SIDES_ALWAYS, o));
            }
            for (Compare cmp : fx.compares) {
                final Frame f = new Frame(fx, cmp.frame);
                final Source src = f.source(f.truth);
                final Raster ras = ImageIO.read(frameFile(new File(clips, fx.clip),
                                                          cmp.frame)).getRaster();
                for (int r = 0; r < ROWS; r++) {
                    for (int c = 0; c < COLS; c++) {
                        final int x = fx.vx + c, y = fx.vy + r;
                        if (cmp.exclude.contains(Fixture.key(x, y))
                            || coastCell(f, x, y)) continue;
                        for (int i = 0; i < comps.size(); i++) {
                            comps.get(i).composeCell(src, x, y, cell, 0, 16);
                            for (int k = 0; k < 256; k++) {
                                final int v = ras.getSample(16 * c + k % 16,
                                                            MAP_Y + 16 * r + k / 16, 0);
                                if (v != (cell[k] & 0xFF) && v != 0 && v != 7 && v != 15) {
                                    off[i]++;
                                }
                            }
                        }
                    }
                }
            }
        }
        int best = Integer.MAX_VALUE, committed = -1;
        for (int i = 0; i < orders.size(); i++) {
            best = Math.min(best, off[i]);
            if (java.util.Arrays.equals(orders.get(i), ClassicTerrainComposer.SIDE_ORDER)) {
                committed = off[i];
            }
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < orders.size(); i++) {
            final StringBuilder name = new StringBuilder();
            for (int d : orders.get(i)) name.append(SIDE_NAMES.charAt(d));
            sb.append(' ').append(name).append('=').append(off[i]);
        }
        System.out.println("ClassicTerrainGoldenTest: side orders, px off on the whole"
            + " frames:" + sb);
        assertEquals("SIDE_ORDER gives the fewest mismatches:" + sb, best, committed);
    }

    private static void permute(int[] a, int k, List<int[]> out) {
        if (k == a.length) {
            out.add(a.clone());
            return;
        }
        for (int i = k; i < a.length; i++) {
            int t = a[k]; a[k] = a[i]; a[i] = t;
            permute(a, k + 1, out);
            t = a[k]; a[k] = a[i]; a[i] = t;
        }
    }

    /**
     * The cell lines: the dark tile's histogram (LF #345 cell (0,0), with
     * the clip) and the sea lane's sparkles in the start's fringes (FS
     * #19), with the pack.
     */
    public void testCellChecks() throws IOException {
        final ClassicPackFiles pack = pack();
        if (pack == null) return;
        final File clips = clips();
        final ClassicIndexSheet terrain = pack.indexSheet(ClassicPackFiles.TERRAIN_SS);
        final ClassicIndexSheet phys = pack.indexSheet(ClassicPackFiles.PHYS0_SS);
        int n = 0;
        for (Fixture fx : fixtures()) {
            final ClassicTerrainComposer real = composer(fx, terrain, phys);
            for (CellCheck cc : fx.cells) {
                n++;
                final Frame f = new Frame(fx, cc.frame);
                final byte[] cell = new byte[256];
                final int x = ("histogram".equals(cc.kind)) ? fx.vx + cc.a : cc.a;
                final int y = ("histogram".equals(cc.kind)) ? fx.vy + cc.b : cc.b;
                real.composeCell(f.source(f.truth), x, y, cell, 0, 16);
                final TreeMap<Integer, Integer> counts = new TreeMap<>();
                int cyc = 0;
                for (byte b : cell) {
                    counts.merge(b & 0xFF, 1, Integer::sum);
                    if ((b & 0xFF) >= 120 && (b & 0xFF) < 128) cyc++;
                }
                final String at = f.at() + " " + cc.line;
                if ("histogram".equals(cc.kind)) {
                    assertEquals(at, cc.counts, counts);
                    if (clips != null) {
                        final Raster ras = ImageIO.read(frameFile(
                            new File(clips, fx.clip), cc.frame)).getRaster();
                        for (int k = 0; k < 256; k++) {
                            assertEquals(at + " px " + k, cell[k] & 0xFF, ras.getSample(
                                16 * cc.a + k % 16, MAP_Y + 16 * cc.b + k / 16, 0));
                        }
                    }
                } else {
                    assertEquals(at, cc.cycling, cyc);
                }
            }
        }
        assertTrue("cell lines " + n, n >= 5);
    }

    /**
     * W6c, the water cycling against the original (design 10 §7.4, §12.2):
     * the clips' palettes are phases of the game palette (fog-start #0 is
     * 7, #19 is 0, landfall #0 is 1); 8 consecutive phases of a sea-lane
     * tile and of the ocean next to it (landfall #5420-#5702,
     * {@code img/04-04}) equal our composed cells index for index and in
     * colour at every phase; the major river {@code PHYS0.SS.024} of
     * fog-start #6543 cell (6,1) holds its 5 cycling pixels at three phases
     * in a row (F5: rivers cycle), and the composer keeps them.  The counts
     * need the pack; the frames need {@code -Dclassic.clips}.
     */
    public void testCyclingMatchesTheOriginal() throws IOException {
        final ClassicPackFiles pack = pack();
        if (pack == null) return;
        final File clips = clips();
        final ClassicIndexSheet terrain = pack.indexSheet(ClassicPackFiles.TERRAIN_SS);
        final ClassicIndexSheet phys = pack.indexSheet(ClassicPackFiles.PHYS0_SS);
        final ClassicGamePalette gp = ClassicGamePalette.of(pack);
        assertNotNull(gp);
        final int lo = ClassicGamePalette.CYCLE_FIRST;
        final int hi = lo + ClassicGamePalette.CYCLE_COUNT;
        int n = 0, framesCompared = 0;
        final StringBuilder report = new StringBuilder();
        for (Fixture fx : fixtures()) {
            final ClassicTerrainComposer real = composer(fx, terrain, phys);
            for (CycleCheck cc : fx.cycles) {
                n++;
                final String at = fx.name + " " + cc.line;
                switch (cc.kind) {
                case "palette": {
                    if (clips == null) break;
                    final java.awt.image.IndexColorModel cm = colourModel(clips, fx, cc.frames[0]);
                    assertEquals(at, cc.expected, gp.phaseOf(entries(cm)));
                    for (int i = lo; i < hi; i++) {
                        assertEquals(at + " #" + i, gp.rgb(cc.expected, i),
                                     cm.getRGB(i) & 0xFFFFFF);
                    }
                    framesCompared++;
                    report.append("\n  ").append(at).append(": the clip's 120-127 = phase ")
                        .append(cc.expected);
                    break;
                }
                case "phases": {
                    final List<Integer> phases = new ArrayList<>();
                    for (int frame : cc.frames) {
                        final Frame f = new Frame(fx, frame);
                        final byte[] cell = new byte[256];
                        final boolean cyc = real.composeCell(f.source(f.truth), cc.a, cc.b,
                                                             cell, 0, 16);
                        int count = 0;
                        for (byte v : cell) if ((v & 0xFF) >= lo && (v & 0xFF) < hi) count++;
                        assertEquals(f.at() + " " + cc.line, cc.cycling, count);
                        assertEquals(f.at() + " " + cc.line, cc.cycling > 0, cyc);
                        if (clips == null) continue;
                        final java.awt.image.BufferedImage img
                            = ImageIO.read(frameFile(new File(clips, fx.clip), frame));
                        final java.awt.image.IndexColorModel cm
                            = (java.awt.image.IndexColorModel) img.getColorModel();
                        final int ph = gp.phaseOf(entries(cm));
                        assertTrue(f.at() + " " + cc.line + ": a phase", ph >= 0);
                        phases.add(ph);
                        final Raster ras = img.getRaster();
                        final int c = cc.a - fx.vx, r = cc.b - fx.vy;
                        for (int k = 0; k < 256; k++) {
                            final int clip = ras.getSample(16 * c + k % 16,
                                                           MAP_Y + 16 * r + k / 16, 0);
                            assertEquals(f.at() + " " + cc.line + " px " + k,
                                         cell[k] & 0xFF, clip);
                            assertEquals(f.at() + " " + cc.line + " rgb " + k,
                                         gp.rgb(ph, cell[k] & 0xFF), cm.getRGB(clip) & 0xFFFFFF);
                        }
                        framesCompared++;
                    }
                    if (clips == null) break;
                    final java.util.Set<Integer> distinct = new HashSet<>(phases);
                    assertEquals(at + " " + phases, Math.min(8, phases.size()), distinct.size());
                    for (int i = 1; i < phases.size(); i++) {
                        assertEquals(at + " " + phases, (phases.get(i - 1) + 1) % 8,
                                     (int) phases.get(i));
                    }
                    report.append("\n  ").append(at).append(": phases ").append(phases)
                        .append(", ").append(phases.size()).append(" x 256 px index-exact and"
                            + " colour-exact, ").append(cc.cycling).append(" cycling px");
                    break;
                }
                case "sprite": {
                    final ClassicIndexSheet sheet = (cc.terrainSheet) ? terrain : phys;
                    final int w = sheet.width(cc.sprite), h = sheet.height(cc.sprite);
                    final byte[] px = sheet.pixels(cc.sprite);
                    int opaque = 0, cyc = 0;
                    for (byte v : px) {
                        if ((v & 0xFF) == ClassicIndexSheet.TRANSPARENT) continue;
                        opaque++;
                        if ((v & 0xFF) >= lo && (v & 0xFF) < hi) cyc++;
                    }
                    assertEquals(at, cc.opaque, opaque);
                    assertEquals(at, cc.cycling, cyc);
                    if (!cc.terrainSheet) {
                        // As an overlay of an explored land cell the composer
                        // keeps every opaque pixel, the cycling ones included.
                        final byte[] cell = new byte[256];
                        assertEquals(at, cc.cycling > 0,
                                     real.composeCell(overlaySource(cc.sprite), 1, 1, cell, 0, 16));
                        for (int j = 0; j < Math.min(16, h); j++) {
                            for (int i = 0; i < Math.min(16, w); i++) {
                                final int v = px[j * w + i] & 0xFF;
                                if (v == ClassicIndexSheet.TRANSPARENT) continue;
                                assertEquals(at + " composed px " + i + "," + j, v,
                                             cell[j * 16 + i] & 0xFF);
                            }
                        }
                    }
                    if (clips == null) break;
                    final java.awt.image.BufferedImage img
                        = ImageIO.read(frameFile(new File(clips, fx.clip), cc.frames[0]));
                    final java.awt.image.IndexColorModel cm
                        = (java.awt.image.IndexColorModel) img.getColorModel();
                    final int ph = gp.phaseOf(entries(cm));
                    assertTrue(at + ": a phase", ph >= 0);
                    final Raster ras = img.getRaster();
                    int match = 0;
                    for (int j = 0; j < h; j++) {
                        for (int i = 0; i < w; i++) {
                            final int v = px[j * w + i] & 0xFF;
                            if (v == ClassicIndexSheet.TRANSPARENT) continue;
                            final int clip = ras.getSample(16 * cc.a + i, MAP_Y + 16 * cc.b + j, 0);
                            assertEquals(at + " px " + i + "," + j, v, clip);
                            assertEquals(at + " rgb " + i + "," + j, gp.rgb(ph, v),
                                         cm.getRGB(clip) & 0xFFFFFF);
                            match++;
                        }
                    }
                    assertEquals(at, cc.opaque, match);
                    framesCompared++;
                    report.append("\n  ").append(at).append(": ").append(match).append("/")
                        .append(cc.opaque).append(" px at phase ").append(ph).append(", ")
                        .append(cc.cycling).append(" cycling; kept by the composer");
                    break;
                }
                default:
                    fail(at);
                }
            }
        }
        assertTrue("W6c lines " + n, n >= 8);
        if (clips != null) assertTrue("frames " + framesCompared, framesCompared >= 22);
        System.err.println("ClassicTerrainGoldenTest: W6c " + n + " lines, "
            + framesCompared + " clip frames compared" + report);
    }

    /** A 3x3 of explored plains whose centre (1,1) has one overlay frame. */
    private static ClassicTerrainComposer.TerrainSource overlaySource(int frame) {
        final TileType plains = spec().getTileType("model.tile.plains");
        return new ClassicTerrainComposer.TerrainSource() {
                @Override
                public boolean onMap(int x, int y) {
                    return x >= 0 && x < 3 && y >= 0 && y < 3;
                }

                @Override
                public boolean explored(int x, int y) {
                    return onMap(x, y);
                }

                @Override
                public TileType type(int x, int y) {
                    return (onMap(x, y)) ? plains : null;
                }

                @Override
                public void overlays(int x, int y, IntConsumer frames) {
                    if (x == 1 && y == 1) frames.accept(frame);
                }
            };
    }

    /** @return A clip frame's colour model (the last PNG at or before it). */
    private static java.awt.image.IndexColorModel colourModel(File clips, Fixture fx,
                                                               int frame)
        throws IOException {
        final File f = frameFile(new File(clips, fx.clip), frame);
        assertNotNull(fx.name + " #" + frame, f);
        return (java.awt.image.IndexColorModel) ImageIO.read(f).getColorModel();
    }

    /** @return A colour model's 256 entries, 0xRRGGBB. */
    private static int[] entries(java.awt.image.IndexColorModel cm) {
        final int[] e = new int[256];
        for (int i = 0; i < Math.min(256, cm.getMapSize()); i++) e[i] = cm.getRGB(i) & 0xFFFFFF;
        return e;
    }
}
