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

package net.sf.freecol.server.generator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import net.sf.freecol.FreeCol;
import net.sf.freecol.common.io.FreeColRules;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Nation;
import net.sf.freecol.common.model.NationOptions;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Topology;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.option.MapGeneratorOptions;
import net.sf.freecol.common.option.OptionGroup;
import net.sf.freecol.common.option.SelectOption;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.server.model.ServerGame;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * The original game's New World in the "levi" rules (part P1): the land
 * of each answer to the original's world questions LANDMASSE and
 * LANDFORM as in Roger's games in the original (2026-10-13), the land
 * forms, the climate questions TEMPERATUR and KLIMA, and the lanes to
 * Europe, the Pacific and the poles, on both topologies.  The classic
 * and freecol rules keep FreeCol's land generators and their maps.
 *
 * The options are named by their identifiers, so that this test also
 * runs against rules and code without the original's world (it then
 * fails, as it did on part O, 8919dd397).
 */
public class OriginalWorldTest extends FreeColTestCase {

    private static final String TYPE = "model.option.landGeneratorType";
    private static final String MASS = "model.option.originalLandMass";
    private static final String FORM = "model.option.originalLandForm";
    private static final int ORIGINAL = 4;

    /**
     * The mean land of each LANDMASSE (Klein, Normal, Groß) and
     * LANDFORM (Inselgruppe, Normal, Große Kontinente) on the
     * original's 58x72 map.  Roger's games: everything Normal 872 and
     * 901, Klein 673 and 732, Groß 1079 and 1184, Inselgruppe 698,
     * Große Kontinente 1191, Groß + Kontinente (+ Warm + Feucht) 1373;
     * the others a landform's difference at Normal added to the mass.
     */
    private static final int[][] MEAN = {
        { 514, 702, 1007 }, { 698, 886, 1191 }, { 943, 1131, 1373 } };

    private static final String[] MASS_NAMES = { "Klein", "Normal", "Gross" };
    private static final String[] FORM_NAMES = { "Inselgruppe", "Normal",
                                                 "Kontinente" };


    /**
     * The levi rules on the easiest level, with the topology's map
     * options; one per test and topology (see MapGeneratorTest).
     */
    private static Specification levi() {
        Specification spec = FreeCol.loadSpecification(
            FreeColRules.getFreeColRulesFile("levi"), null,
            "model.difficulty.veryEasy");
        spec.setFile(MapGeneratorOptions.IMPORT_FILE, null);
        MapGeneratorOptions.applyTopologyDefaults(spec.getMapGeneratorOptions());
        return spec;
    }

    /** Set the original's four answers (rows 0-2) in a spec. */
    private static void answer(Specification spec, int mass, int form,
                               int temperature, int climate) {
        final OptionGroup mgo = spec.getMapGeneratorOptions();
        mgo.setInteger(TYPE, ORIGINAL);
        mgo.setInteger(MASS, mass);
        mgo.setInteger(FORM, form);
        mgo.setInteger(MapGeneratorOptions.TEMPERATURE,
                       new int[] { 1, 2, 3 }[temperature]);
        mgo.setInteger(MapGeneratorOptions.HUMIDITY,
                       new int[] { 35, 40, 55 }[climate]);
    }

    /** The map of a new game with the Classic UI's players. */
    private static Map newMap(Specification spec, long seed) {
        Game game = new ServerGame(spec);
        game.setNationOptions(new NationOptions(spec));
        for (Nation n : spec.getNations()) {
            if (n.isUnknownEnemy() || n.getType().isREF()) continue;
            Player p = new ServerPlayer(game, false, n);
            p.setAI(!"model.nation.dutch".equals(n.getId()));
            game.addPlayer(p);
        }
        new SimpleMapGenerator(new Random(seed))
            .generateMap(game, null, true, new LogBuilder(-1));
        return game.getMap();
    }

    /** Land as the original's saves count it: no polar row, no arctic. */
    private static boolean isLand(Map map, Tile t) {
        return t.isLand() && !map.isPolar(t)
            && !"model.tile.arctic".equals(t.getType().getId());
    }

    private static int land(Map map) {
        int n = 0;
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                if (isLand(map, map.getTile(x, y))) n++;
            }
        }
        return n;
    }

    /** The land masses (8-connected), largest first. */
    private static List<Integer> masses(Map map) {
        final boolean[][] seen = new boolean[map.getWidth()][map.getHeight()];
        final List<Integer> ret = new ArrayList<>();
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                Tile t = map.getTile(x, y);
                if (seen[x][y] || !isLand(map, t)) continue;
                int n = 0;
                Deque<Tile> q = new ArrayDeque<>();
                q.add(t);
                seen[x][y] = true;
                while (!q.isEmpty()) {
                    Tile a = q.poll();
                    n++;
                    for (Tile b : a.getSurroundingTiles(1)) {
                        if (!seen[b.getX()][b.getY()] && isLand(map, b)) {
                            seen[b.getX()][b.getY()] = true;
                            q.add(b);
                        }
                    }
                }
                ret.add(n);
            }
        }
        ret.sort(Collections.reverseOrder());
        return ret;
    }

    private static double coastShare(Map map) {
        int land = 0, coast = 0;
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                Tile t = map.getTile(x, y);
                if (!isLand(map, t)) continue;
                land++;
                for (Tile n : t.getSurroundingTiles(1)) {
                    if (!n.isLand()) {
                        coast++;
                        break;
                    }
                }
            }
        }
        return coast / (double)land;
    }

    /** The share of the flat land of some tile types (and their forests). */
    private static double share(Map map, String... types) {
        int all = 0, n = 0;
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                Tile t = map.getTile(x, y);
                if (!isLand(map, t) || t.getType().isElevation()) continue;
                all++;
                for (String s : types) {
                    if (t.getType().getId().equals("model.tile." + s)) n++;
                }
            }
        }
        return 100.0 * n / all;
    }

    private static final Topology[] BOTH = { Topology.SQUARE,
                                             Topology.ISOMETRIC };


    /**
     * The levi rules play the original's New World by default, with
     * everything Normal; the classic and freecol rules keep FreeCol's
     * classic land generator and have no original's questions.
     */
    public void testLeviPlaysTheOriginalWorld() {
        final Specification levi = FreeCol.loadSpecification(
            FreeColRules.getFreeColRulesFile("levi"), null,
            "model.difficulty.veryEasy");
        final OptionGroup mgo = levi.getMapGeneratorOptions();
        assertEquals("levi: the original's world", ORIGINAL,
                     mgo.getSelection(TYPE));
        assertEquals("LANDMASSE Normal", 1, mgo.getSelection(MASS));
        assertEquals("LANDFORM Normal", 1, mgo.getSelection(FORM));
        assertEquals("TEMPERATUR Gemaessigt", 2,
                     mgo.getRange(MapGeneratorOptions.TEMPERATURE));
        assertEquals("KLIMA Normal", 40,
                     mgo.getRange(MapGeneratorOptions.HUMIDITY));
        assertEquals("FreeCol's four generators and the original's", 5,
                     mgo.getOption(TYPE, SelectOption.class)
                         .getItemValues().size());
        for (String rules : new String[] { "classic", "freecol" }) {
            final OptionGroup o = FreeCol.loadSpecification(
                FreeColRules.getFreeColRulesFile(rules), null,
                "model.difficulty.medium").getMapGeneratorOptions();
            assertEquals(rules + ": FreeCol's classic generator", 0,
                         o.getSelection(TYPE));
            assertEquals(rules + ": FreeCol's four generators", 4,
                         o.getOption(TYPE, SelectOption.class)
                             .getItemValues().size());
            assertFalse(rules + ": no LANDMASSE",
                        o.hasOption(MASS, SelectOption.class));
            assertFalse(rules + ": no LANDFORM",
                        o.hasOption(FORM, SelectOption.class));
        }
    }

    /**
     * Each LANDMASSE and LANDFORM gives the original's land: on average
     * within 5 % of the setting's mean (the original's maps of one
     * setting: within 5 %), every map within 18 % (a map draws its
     * amount 5 % around the mean, at most 12 % away, and its last piece
     * of land adds or misses a little), on both topologies (on the
     * isometric map in proportion to its 4000 tiles).
     * Before part P1 the levi rules had one land amount for everything
     * (square about 760 tiles, landMass 18; isometric about 1030).
     */
    public void testLandOfEverySetting() {
        final Topology saved = Topology.current();
        try {
            for (Topology top : BOTH) {
                Topology.setCurrent(top);
                final Specification spec = levi();
                for (int mass = 0; mass < 3; mass++) {
                    for (int form = 0; form < 3; form++) {
                        answer(spec, mass, form, 1, 1);
                        double sum = 0, lo = 2, hi = 0;
                        int seeds = 0;
                        for (long seed = 1; seed <= 8; seed++) {
                            final Map map = newMap(spec, seed);
                            final double mean = MEAN[mass][form]
                                * map.getWidth() * map.getHeight() / 4176.0;
                            final int n = land(map);
                            final String what = top + " " + MASS_NAMES[mass]
                                + "/" + FORM_NAMES[form] + " seed " + seed
                                + ": land " + n + " for a mean of " + mean;
                            assertTrue(what, n >= mean * 0.82 && n <= mean * 1.18);
                            sum += n / mean;
                            lo = Math.min(lo, n / mean);
                            hi = Math.max(hi, n / mean);
                            seeds++;
                        }
                        final double avg = sum / seeds;
                        assertTrue(top + " " + MASS_NAMES[mass] + "/"
                            + FORM_NAMES[form] + ": average land " + avg
                            + " of the mean", avg >= 0.95 && avg <= 1.05);
                        // Each map draws its own amount: the original's two
                        // games of one setting differ by 3 to 9 %.
                        assertTrue(top + " " + MASS_NAMES[mass] + "/"
                            + FORM_NAMES[form] + ": land from " + lo + " to "
                            + hi + " of the mean", hi - lo >= 0.04);
                    }
                }
            }
        } finally {
            Topology.setCurrent(saved);
        }
    }

    /**
     * LANDFORM shapes the land: Inselgruppe makes more and smaller land
     * masses and more coast than Normal, Große Kontinente fewer and
     * larger ones with less coast (the original's Inselgruppe: 12
     * masses, the largest 50 % of the land, 0.78 coast tiles per land
     * tile; its maps of 1143 to 1679 tiles: 4 to 12, 54 to 93 %, 0.58
     * to 0.69).
     */
    public void testLandformsShapeTheLand() {
        final Topology saved = Topology.current();
        try {
            for (Topology top : BOTH) {
                Topology.setCurrent(top);
                final Specification spec = levi();
                final double[] masses = new double[3], largest = new double[3],
                    coast = new double[3];
                for (int form = 0; form < 3; form++) {
                    answer(spec, 1, form, 1, 1);
                    for (long seed = 1; seed <= 8; seed++) {
                        final Map map = newMap(spec, seed);
                        final List<Integer> m = masses(map);
                        int big = 0;
                        for (int v : m) if (v >= 10) big++;
                        masses[form] += big / 8.0;
                        largest[form] += 100.0 * m.get(0) / land(map) / 8.0;
                        coast[form] += coastShare(map) / 8.0;
                    }
                }
                final String what = top + ": masses of 10+ tiles "
                    + java.util.Arrays.toString(masses) + ", largest % "
                    + java.util.Arrays.toString(largest) + ", coast "
                    + java.util.Arrays.toString(coast);
                assertTrue(what, masses[0] > masses[1] && masses[1] > masses[2]);
                assertTrue(what, largest[0] < largest[1] && largest[1] < largest[2]);
                assertTrue(what, coast[0] > coast[1] && coast[1] > coast[2]);
                assertTrue(what, masses[0] >= 6 && largest[2] >= 60);
                assertTrue(what, coast[1] >= 0.55 && coast[0] >= 0.65);
            }
        } finally {
            Topology.setCurrent(saved);
        }
    }

    /**
     * TEMPERATUR and KLIMA change the terrain, not the land: the same
     * seed gives the same land tiles; Kühl has more tundra and less
     * savannah than Gemäßigt, Warm less tundra and more savannah;
     * Trocken more desert and less marsh and swamp than Normal, Feucht
     * the reverse.  Gemäßigt and Normal give about the original's
     * tundra and savannah (its random maps: tundra 2.4 to 10.8 %, mean
     * 6.6; savannah 5.9 to 17.4 %, mean 11.9), and no land outside the
     * polar rows is arctic (FreeCol's temperate climate gives 15 %
     * tundra and arctic on the land next to the poles).
     */
    public void testTemperatureAndClimateShiftTheTerrain() {
        final Topology saved = Topology.current();
        try {
            Topology.setCurrent(Topology.SQUARE);
            final Specification spec = levi();
            final int[][] variants = { { 1, 1 }, { 0, 1 }, { 2, 1 },
                                       { 1, 0 }, { 1, 2 } };
            final double[] tundra = new double[5], savannah = new double[5],
                desert = new double[5], wet = new double[5];
            final int seeds = 8;
            for (long seed = 1; seed <= seeds; seed++) {
                Set<Integer> land0 = null;
                for (int v = 0; v < variants.length; v++) {
                    answer(spec, 1, 1, variants[v][0], variants[v][1]);
                    final Map map = newMap(spec, seed);
                    final Set<Integer> land = new HashSet<>();
                    for (int y = 0; y < map.getHeight(); y++) {
                        for (int x = 0; x < map.getWidth(); x++) {
                            Tile t = map.getTile(x, y);
                            if (t.isLand() && !map.isPolar(t)) {
                                land.add(y * 1000 + x);
                                assertFalse("arctic off the poles at " + t,
                                    "model.tile.arctic".equals(t.getType().getId()));
                            }
                        }
                    }
                    if (land0 == null) land0 = land;
                    assertEquals("seed " + seed + " variant " + v
                                 + ": the same land", land0, land);
                    tundra[v] += share(map, "tundra", "borealForest") / seeds;
                    savannah[v] += share(map, "savannah", "tropicalForest") / seeds;
                    desert[v] += share(map, "desert", "scrubForest") / seeds;
                    wet[v] += share(map, "marsh", "wetlandForest", "swamp",
                                    "rainForest") / seeds;
                }
            }
            final String what = "Normal/Kuehl/Warm/Trocken/Feucht: tundra "
                + java.util.Arrays.toString(tundra) + " savannah "
                + java.util.Arrays.toString(savannah) + " desert "
                + java.util.Arrays.toString(desert) + " marsh+swamp "
                + java.util.Arrays.toString(wet);
            assertTrue(what, tundra[1] > tundra[0] + 2 && tundra[0] > tundra[2] + 2);
            assertTrue(what, savannah[1] + 2 < savannah[0] && savannah[0] + 2 < savannah[2]);
            assertTrue(what, desert[3] > desert[0] + 1 && desert[0] > desert[4] + 1);
            assertTrue(what, wet[3] + 1 < wet[0] && wet[0] + 1 < wet[4]);
            // KLIMA is mild: FreeCol's own wet humidity would make a
            // quarter of the land swamp (the original: 11.3 to 13.1 %).
            assertTrue(what, wet[4] - wet[0] <= 5 && desert[3] - desert[0] <= 6);
            assertTrue(what, tundra[0] >= 4 && tundra[0] <= 9.5);
            // FreeCol's chilly and warm climates: 20 % tundra, 21 % savannah.
            assertTrue(what, tundra[1] <= 16 && savannah[2] <= 18);
            assertTrue(what, savannah[0] >= 8 && savannah[0] <= 15);
        } finally {
            Topology.setCurrent(saved);
        }
    }

    /**
     * Whatever the answers, every row still leads to Europe on both
     * sides (a start tile on the high seas at the east and the west),
     * the land keeps off the edges (square: columns 6 to 51, rows 2 to
     * 68 of 58x72), every land row of the square map has its Pacific
     * (the original's row scan), the polar rows are all arctic, and our
     * ship starts in reach of the land.
     */
    public void testLanesPacificAndPolesStayRight() {
        final Topology saved = Topology.current();
        try {
            for (Topology top : BOTH) {
                Topology.setCurrent(top);
                final Specification spec = levi();
                final boolean square = top == Topology.SQUARE;
                final int[][] settings = { { 0, 0 }, { 1, 1 }, { 2, 2 },
                                           { 2, 1 } };
                for (int[] s : settings) {
                    answer(spec, s[0], s[1], 1, 1);
                    for (long seed = 1; seed <= 4; seed++) {
                        final Map map = newMap(spec, seed);
                        final String what = top + " " + MASS_NAMES[s[0]] + "/"
                            + FORM_NAMES[s[1]] + " seed " + seed;
                        final List<Tile> east = new ArrayList<>(),
                            west = new ArrayList<>();
                        map.collectStartingTiles(east, west);
                        final Set<Integer> eastRows = new HashSet<>(),
                            westRows = new HashSet<>();
                        for (Tile t : east) eastRows.add(t.getY());
                        for (Tile t : west) westRows.add(t.getY());
                        final Set<Integer> pacificRows = new HashSet<>();
                        for (int y = 0; y < map.getHeight(); y++) {
                            boolean landRow = false;
                            for (int x = 0; x < map.getWidth(); x++) {
                                final Tile t = map.getTile(x, y);
                                if (t.getRegion() != null
                                    && t.getRegion().isPacific()) {
                                    pacificRows.add(y);
                                }
                                if (map.isPolar(t)) {
                                    if (t.isLand()) {
                                        assertEquals(what + " polar " + t,
                                            "model.tile.arctic",
                                            t.getType().getId());
                                    }
                                    continue;
                                }
                                if (!t.isLand()) continue;
                                landRow = true;
                                if (square) {
                                    assertTrue(what + " land off the edge " + t,
                                        x >= 6 && x <= 51 && y >= 2 && y <= 68);
                                } else {
                                    assertTrue(what + " land off the edge " + t,
                                        x >= 5 && x <= map.getWidth() - 6);
                                }
                            }
                            if (map.isPolar(map.getTile(0, y))) continue;
                            assertTrue(what + " row " + y + " leads east",
                                       eastRows.contains(y));
                            assertTrue(what + " row " + y + " leads west",
                                       westRows.contains(y));
                            if (square && landRow) {
                                assertTrue(what + " row " + y + " has Pacific",
                                           pacificRows.contains(y));
                            }
                        }
                        final Player dutch = map.getGame()
                            .getPlayerByNationId("model.nation.dutch");
                        Tile start = null;
                        for (Unit u : dutch.getUnitSet()) {
                            if (u.hasTile()) start = u.getTile();
                        }
                        assertNotNull(what + " our ship", start);
                        assertTrue(what + " starts on a tile to Europe",
                            start.isDirectlyHighSeasConnected());
                        int d = Integer.MAX_VALUE;
                        for (int y = 0; y < map.getHeight(); y++) {
                            for (int x = 0; x < map.getWidth(); x++) {
                                if (isLand(map, map.getTile(x, y))) {
                                    d = Math.min(d, start.getDistanceTo(
                                        map.getTile(x, y)));
                                }
                            }
                        }
                        assertTrue(what + " ship " + d + " from land",
                                   d <= ((square) ? 16 : 25));
                    }
                }
            }
        } finally {
            Topology.setCurrent(saved);
        }
    }

    /**
     * The original's world does not use the land mass option: its
     * mountains, hills and rumours are made for the land it has, so a
     * seed gives the same map whatever the land mass option says, and
     * the rumours of a small and of a large world follow their land.
     */
    public void testTheLandMassOptionIsNotUsed() {
        final Topology saved = Topology.current();
        try {
            for (Topology top : BOTH) {
                Topology.setCurrent(top);
                final Specification spec = levi();
                final int rumourNumber = spec.getMapGeneratorOptions()
                    .getRange(MapGeneratorOptions.RUMOUR_NUMBER);
                for (int[] s : new int[][] { { 0, 0 }, { 2, 2 } }) {
                    answer(spec, s[0], s[1], 1, 1);
                    String first = null;
                    for (int landMass : new int[] { 15, 50 }) {
                        spec.getMapGeneratorOptions()
                            .setInteger(MapGeneratorOptions.LAND_MASS, landMass);
                        final Map map = newMap(spec, 3);
                        int rumours = 0, all = 0;
                        final StringBuilder sb = new StringBuilder();
                        for (int y = 0; y < map.getHeight(); y++) {
                            for (int x = 0; x < map.getWidth(); x++) {
                                Tile t = map.getTile(x, y);
                                sb.append(t.getType().getId())
                                    .append(t.hasLostCityRumour() ? 'R' : '.');
                                if (t.hasLostCityRumour()) rumours++;
                                if (t.isLand() && !map.isPolar(t)) all++;
                            }
                        }
                        final String what = top + " " + MASS_NAMES[s[0]] + "/"
                            + FORM_NAMES[s[1]] + " landMass " + landMass + ": "
                            + rumours + " rumours on " + all + " land tiles";
                        if (first == null) {
                            first = sb.toString();
                        } else {
                            assertEquals(what + ": the same map", first,
                                         sb.toString());
                        }
                        assertTrue(what, rumours <= all / rumourNumber
                                   && rumours >= all / rumourNumber - 3);
                    }
                }
            }
        } finally {
            Topology.setCurrent(saved);
        }
    }

    /**
     * The classic and freecol rules make the same maps as before part
     * P1: the land of the first seeds on both topologies is that of
     * part O (8919dd397, every land tile counted).
     */
    public void testFreeColRulesKeepTheirMaps() {
        final Topology saved = Topology.current();
        final String[] rules = { "classic", "classic", "freecol", "freecol" };
        final Topology[] tops = { Topology.SQUARE, Topology.ISOMETRIC,
                                  Topology.SQUARE, Topology.ISOMETRIC };
        // Part O: classic square seeds 1-2: 933, 920; isometric 1270,
        // 1261; freecol the same.
        final int[][] before = { { 933, 920 }, { 1270, 1261 },
                                 { 933, 920 }, { 1270, 1261 } };
        try {
            for (int i = 0; i < rules.length; i++) {
                Topology.setCurrent(tops[i]);
                Specification spec = FreeCol.loadSpecification(
                    FreeColRules.getFreeColRulesFile(rules[i]), null,
                    "model.difficulty.medium");
                spec.setFile(MapGeneratorOptions.IMPORT_FILE, null);
                MapGeneratorOptions.applyTopologyDefaults(
                    spec.getMapGeneratorOptions());
                for (int seed = 1; seed <= 2; seed++) {
                    final Map map = newMap(spec, seed);
                    int n = 0;
                    for (int y = 0; y < map.getHeight(); y++) {
                        for (int x = 0; x < map.getWidth(); x++) {
                            if (map.getTile(x, y).isLand()) n++;
                        }
                    }
                    assertEquals(rules[i] + " " + tops[i] + " seed " + seed,
                                 before[i][seed - 1], n);
                }
            }
        } finally {
            Topology.setCurrent(saved);
        }
    }
}
