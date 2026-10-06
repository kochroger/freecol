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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * Golden checks of the terrain against named frames of the original
 * (M1c design 10 §12), driven by the fixtures in
 * {@code test/expected-data/classic-terrain/} (§12.1, Critic 10(b)).
 *
 * <p>Item <b>W6d</b> (§5 acceptance): the fog ring's <b>true terrain</b>
 * in the LF #1407 and FS #876 situations, plus FS #1042.  For each check
 * frame the fixture's map becomes a server game, the tiles explored by
 * then are explored for the Dutch player, and the client view is the
 * game as the login sends it ({@link ClassicTerrainOracleTest#clientView}).
 * Then:
 * <ul>
 *   <li><b>oracle</b> (always): every explored tile gives the client's
 *       type, every unexplored tile of the ring the fixture's true type,
 *       every other tile null (least knowledge);</li>
 *   <li><b>derivation</b> (always): the fringe, blend and coast-quarter
 *       sprites of the fixture's check lines follow from those types by
 *       the design's rules (§6.1, §8.1; reference code here until W6a's
 *       composer exists), and the multiplayer fallback (unknown ring,
 *       F-W6d-MP) gets every {@code truth} line wrong;</li>
 *   <li><b>pixels</b> (with the converted pack and {@code -Dclassic.clips},
 *       else skipped with a note, Critic 10(a)(d)): those sprites' pixels
 *       equal the clip frame's indices at the mask or quarter, 0 px off;
 *       "frame n" is the last PNG at or before n (Critic 10(c)).</li>
 * </ul>
 * W6a and W6b extend this class with the composer's whole-frame checks.
 */
public class ClassicTerrainGoldenTest extends FreeColTestCase {

    /** The fixtures' folder, relative to the repo. */
    static final File FIXTURES = new File("test/expected-data/classic-terrain");

    /** The system property naming the recordings folder. */
    static final String CLIPS_PROPERTY = "classic.clips";

    /** The TERRAIN.SS frame of every FreeCol tile type (design 10 §6.1). */
    private static final java.util.Map<String, Integer> ALIAS = new HashMap<>();
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
    private static final int[][] SIDES = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };
    private static final String SIDE_NAMES = "NESW";

    /** The quarters NW, NE, SE, SW: their 3 neighbours, clockwise (§8.1). */
    private static final int[][][] CORNERS = {
        { { -1, 0 }, { -1, -1 }, { 0, -1 } },  // NW: W, NW, N
        { { 0, -1 }, { 1, -1 }, { 1, 0 } },    // NE: N, NE, E
        { { 1, 0 }, { 1, 1 }, { 0, 1 } },      // SE: E, SE, S
        { { 0, 1 }, { -1, 1 }, { -1, 0 } }     // SW: S, SW, W
    };
    private static final String[] QUARTER_NAMES = { "NW", "NE", "SE", "SW" };


    // The fixture

    /** One tile line. */
    static final class FixtureTile {
        final String type;   // null for '?'
        final int explored;  // the first frame showing it explored, or -1
        final int base;      // the original's own TERRAIN frame, or -1

        FixtureTile(String type, int explored, int base) {
            this.type = type;
            this.explored = explored;
            this.base = base;
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

    /** A fixture file (§12.1, plus the W6d check lines). */
    static final class Fixture {
        final String name;
        String clip, defaultType;
        int width, height, vx, vy;
        final java.util.Map<Long, FixtureTile> tiles = new LinkedHashMap<>();
        final List<Check> checks = new ArrayList<>();

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

        /** @return The frames the checks look at, in order. */
        TreeSet<Integer> frames() {
            final TreeSet<Integer> s = new TreeSet<>();
            for (Check c : this.checks) s.add(c.frame);
            return s;
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
                default: {
                    final int[] xy = xy(w[0]);
                    int explored = -1, base = -1;
                    for (int i = 2; i < w.length; i++) {
                        if (w[i].startsWith("explored@")) {
                            explored = Integer.parseInt(w[i].substring(9));
                        } else if (w[i].startsWith("base=")) {
                            base = Integer.parseInt(w[i].substring(5));
                        }
                    }
                    fx.tiles.put(key(xy[0], xy[1]), new FixtureTile(
                        ("?".equals(w[1])) ? null : w[1], explored, base));
                    break;
                }
                }
            }
            if (fx.clip == null || fx.defaultType == null || fx.width <= 0) {
                throw new IOException(f + ": needs clip, map and default lines");
            }
            return fx;
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
    }


    // The rules (design 10 §6.1, §8.1) -- reference code until W6a

    /** The sprite results of one check. */
    private static final class Derived {
        int sprite = -1;           // the derived sprite (-1: none)
        int[] pixels;              // the composed 16x16 cell (-1 unknown)
        int[][] compare;           // the {px, py} the check compares
    }

    /**
     * The own sprite of a type at a tile: the fixture's base where it
     * overrides the alias table for that tile's own type (T008), else the
     * alias table; -1 for null.
     */
    private static int own(Fixture fx, int x, int y, TileType t) {
        if (t == null) return -1;
        final FixtureTile ft = fx.tile(x, y);
        if (ft != null && ft.base >= 0 && t.getId().equals(fx.typeAt(x, y))) return ft.base;
        final Integer s = ALIAS.get(t.getId());
        assertNotNull(t.getId(), s);
        return s;
    }

    /** blendSpr: the own sprite with BLEND_BASE {8: 1} (FS §3.3). */
    private static int blendSpr(Fixture fx, int x, int y, TileType t) {
        final int s = own(fx, x, y, t);
        return (s == 8) ? 1 : s;
    }

    /** bleeds(n -> t): water never bleeds into land. */
    private static boolean bleeds(TileType n, TileType t) {
        return !(n.isWater() && !t.isWater());
    }

    private static boolean isLand(TileType t) {
        return t != null && !t.isWater();
    }

    /**
     * Derive one check's sprite (and, with sheets, the composed pixels)
     * from an oracle.
     *
     * @param f The frame's games.
     * @param o The oracle (truth or unknown).
     * @param c The check.
     * @param terrain TERRAIN.SS indices, or null.
     * @param phys PHYS0.SS indices, or null.
     * @return The result.
     */
    private static Derived derive(Frame f, ClassicTerrainOracle o, Check c,
                                  ClassicIndexSheet terrain, ClassicIndexSheet phys) {
        final Fixture fx = f.fx;
        final Map map = f.client.getMap();
        final Tile tile = map.getTile(c.x, c.y);
        final TileType tt = o.trueType(c.x, c.y);
        final Derived d = new Derived();
        final boolean sheets = terrain != null && phys != null;
        if ("quarter".equals(c.kind)) {
            assertTrue(f.at() + " " + c.line + ": explored water",
                       tile.isExplored() && tile.getType().isWater());
            int bits = 0;
            for (int k = 0; k < 3; k++) {
                final int[] nb = CORNERS[c.part][k];
                if (isLand(o.trueType(c.x + nb[0], c.y + nb[1]))) bits |= 1 << k;
            }
            d.sprite = (bits == 0) ? -1 : 108 + 4 * bits + c.part;
            if (sheets && d.sprite >= 0) {
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
        final boolean dark = !tile.isExplored();
        assertEquals(f.at() + " " + c.line + ": " + ("fringe".equals(c.kind)
                ? "a dark tile" : "an explored tile"), "fringe".equals(c.kind), dark);
        final int base = (dark) ? 148 : own(fx, c.x, c.y, tt);
        if (sheets) {
            d.pixels = new int[256];
            for (int k = 0; k < 256; k++) {
                d.pixels[k] = (dark) ? phys.index(148, k % 16, k / 16)
                    : terrain.index(base, k % 16, k / 16);
            }
        }
        for (int s = 0; s < 4; s++) {
            final int nx = c.x + SIDES[s][0], ny = c.y + SIDES[s][1];
            final Tile n = map.getTile(nx, ny);
            if (n == null) continue;
            // §6.1 unexplored cell: only explored neighbours open a side
            // (and only those may be asked: least knowledge).
            if (dark && !n.isExplored()) continue;
            final TileType tn = o.trueType(nx, ny);
            int src;
            if (dark) {
                src = (tt == null || bleeds(tn, tt)) ? blendSpr(fx, nx, ny, tn)
                    : blendSpr(fx, c.x, c.y, tt);
            } else {
                // §6.1 explored cell: any neighbour of known type blends in.
                if (tn == null) continue;
                src = blendSpr(fx, nx, ny, tn);
                if (src == base || !bleeds(tn, tt)) continue;
            }
            if (s == c.part) d.sprite = src;
            if (sheets) {
                for (int k = 0; k < 256; k++) {
                    if (phys.index(104 + s, k % 16, k / 16) != ClassicIndexSheet.TRANSPARENT) {
                        d.pixels[k] = terrain.index(src, k % 16, k / 16);
                    }
                }
            }
        }
        if (!dark && d.sprite < 0) d.sprite = base; // no blend: the own sprite
        if (sheets) {
            final List<int[]> cmp = new ArrayList<>();
            for (int k = 0; k < 256; k++) {
                if (phys.index(104 + c.part, k % 16, k / 16) != ClassicIndexSheet.TRANSPARENT) {
                    cmp.add(new int[] { k % 16, k / 16 });
                }
            }
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
        final int ox = 16 * (c.x - fx.vx), oy = 8 + 16 * (c.y - fx.vy);
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
        for (Fixture fx : fixtures()) {
            assertEquals(fx.name, 58, fx.width);
            assertEquals(fx.name, 72, fx.height);
            assertFalse(fx.name, fx.checks.isEmpty());
            for (Check c : fx.checks) {
                final int cx = c.x - fx.vx, cy = c.y - fx.vy;
                assertTrue(fx.name + " " + c.line, cx >= 0 && cx < 15 && cy >= 0 && cy < 12);
                assertTrue(fx.name + " " + c.line, ALIAS.containsKey(fx.typeAt(c.x, c.y)));
            }
        }
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
     * The W6d acceptance: with the true terrain the check lines' sprites
     * follow, and the clip's pixels agree (with the pack and the clips);
     * the multiplayer fallback gets every {@code truth} line wrong.
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
        for (Fixture fx : fixtures()) {
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
                    final Derived t = derive(f, f.truth, c, terrain, phys);
                    final Derived u = derive(f, f.unknown, c, terrain, phys);
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
}
