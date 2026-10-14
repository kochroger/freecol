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
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import net.sf.freecol.FreeCol;
import net.sf.freecol.common.io.FreeColRules;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Nation;
import net.sf.freecol.common.model.NationOptions;
import net.sf.freecol.common.model.OriginalWorld;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Settlement;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Topology;
import net.sf.freecol.common.option.IntegerOption;
import net.sf.freecol.common.option.MapGeneratorOptions;
import net.sf.freecol.common.option.OptionGroup;
import net.sf.freecol.common.option.OriginalWorldSettings;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.server.model.ServerGame;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * The natives of a new map of the "levi" rules as the original's (part
 * P2, Roger 2026-10-13: "auf jeder Stufe 6 bis 21 Dörfer pro Stamm",
 * "Indianerland wie im Original", his 13 games in the original): the
 * same villages on every level, 6 to 21 a tribe, one capital each, at
 * most 84, never two next to each other; as many as the original puts
 * on that much land; the Inca and Aztec in the west (the Inca at the
 * Pacific), the Iroquois and Arawak in the east, north and south drawn
 * per map, the other four anywhere; the original's home radius and
 * sizes and no grown land; on both topologies.
 *
 * The options are named by their identifiers and the bands of the land
 * are measured here, so that this test also runs against rules and code
 * without the original's natives (it then fails, as on part P1,
 * ebf166486).
 */
public class OriginalNativesTest extends FreeColTestCase {

    private static final String SPACING = "model.option.nativeSettlementSpacing";
    private static final String MINIMUM = "model.option.nativeSettlementsMinimum";
    private static final String MAXIMUM = "model.option.nativeSettlementsMaximum";
    private static final String TOTAL = "model.option.nativeSettlementsTotal";

    private static final String[] TRIBES = { "model.nation.inca",
        "model.nation.aztec", "model.nation.arawak", "model.nation.iroquois",
        "model.nation.cherokee", "model.nation.apache", "model.nation.sioux",
        "model.nation.tupi" };

    private static final String[] LEVELS = { "model.difficulty.veryEasy",
        "model.difficulty.easy", "model.difficulty.medium",
        "model.difficulty.hard", "model.difficulty.veryHard" };

    /** The original's populations at the start, by settlement type. */
    private static final java.util.Map<String, Integer> SIZE = new HashMap<>();
    /** The original's home radius, by settlement type. */
    private static final java.util.Map<String, Integer> RADIUS = new HashMap<>();
    static {
        SIZE.put("model.settlement.camp", 3);
        SIZE.put("model.settlement.camp.capital", 4);
        SIZE.put("model.settlement.village", 5);
        SIZE.put("model.settlement.village.capital", 7);
        SIZE.put("model.settlement.aztec", 7);
        SIZE.put("model.settlement.aztec.capital", 10);
        SIZE.put("model.settlement.inca", 9);
        SIZE.put("model.settlement.inca.capital", 13);
        for (String id : SIZE.keySet()) {
            RADIUS.put(id, (id.contains("aztec") || id.contains("inca")) ? 2 : 1);
        }
    }


    private static Specification rules(String rules, String level) {
        FreeColRules.loadRules();
        return FreeCol.loadSpecification(
            FreeColRules.getFreeColRulesFile(rules), null, level);
    }

    /**
     * A new game's map as the Classic UI makes it: the topology's map
     * options, the world's answers, four Europeans and eight tribes.
     */
    private static Game newGame(Specification spec, String world, long seed) {
        spec.setFile(MapGeneratorOptions.IMPORT_FILE, null);
        final OptionGroup mgo = spec.getMapGeneratorOptions();
        MapGeneratorOptions.applyTopologyDefaults(mgo);
        if (world != null) OriginalWorldSettings.ofCode(world).apply(mgo);
        final Game game = new ServerGame(spec);
        game.setNationOptions(new NationOptions(spec));
        for (Nation n : spec.getNations()) {
            if (n.isUnknownEnemy() || n.getType().isREF()) continue;
            final Player p = new ServerPlayer(game, false, n);
            p.setAI(!n.getType().isEuropean()
                || !"model.nation.dutch".equals(n.getId()));
            game.addPlayer(p);
        }
        new SimpleMapGenerator(new Random(seed))
            .generateMap(game, null, true, new LogBuilder(-1));
        return game;
    }

    private static List<IndianSettlement> villages(Game game) {
        final List<IndianSettlement> ret = new ArrayList<>();
        for (Player p : game.getLiveNativePlayerList()) {
            ret.addAll(p.getIndianSettlementList());
        }
        return ret;
    }

    private static String fingerprint(Game game) {
        final StringBuilder sb = new StringBuilder();
        for (IndianSettlement is : villages(game)) {
            sb.append(is.getOwner().getNationId()).append('@')
                .append(is.getTile().getX()).append(',')
                .append(is.getTile().getY())
                .append((is.isCapital()) ? "C " : " ");
        }
        return sb.toString();
    }

    private static boolean isLand(Map map, Tile t) {
        return t.isLand() && !map.isPolar(t);
    }

    /**
     * The land's halves and the thirds of each half, measured here: a
     * row's (column's) place is the share of the land before it, its
     * own counted half.
     */
    private static final class Bands {
        final double[] row;
        final double[][] col;

        Bands(Map map) {
            final int w = map.getWidth(), h = map.getHeight();
            final int[] rows = new int[h];
            int land = 0;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (isLand(map, map.getTile(x, y))) {
                        rows[y]++;
                        land++;
                    }
                }
            }
            row = new double[h];
            int before = 0;
            for (int y = 0; y < h; y++) {
                row[y] = (before + rows[y] / 2.0) / land;
                before += rows[y];
            }
            final int[][] cols = new int[2][w];
            final int[] half = new int[2];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (isLand(map, map.getTile(x, y))) {
                        cols[(row[y] < 0.5) ? 0 : 1][x]++;
                        half[(row[y] < 0.5) ? 0 : 1]++;
                    }
                }
            }
            col = new double[2][w];
            for (int k = 0; k < 2; k++) {
                before = 0;
                for (int x = 0; x < w; x++) {
                    col[k][x] = (before + cols[k][x] / 2.0) / half[k];
                    before += cols[k][x];
                }
            }
        }

        boolean north(Tile t) {
            return row[t.getY()] < 0.5;
        }

        double column(Tile t) {
            return col[(north(t)) ? 0 : 1][t.getX()];
        }
    }

    private static int[][] pacificDistances(Map map) {
        final int w = map.getWidth(), h = map.getHeight();
        final int[][] d = new int[w][h];
        final Deque<Tile> q = new ArrayDeque<>();
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                final Tile t = map.getTile(x, y);
                d[x][y] = Integer.MAX_VALUE;
                if (!t.isLand() && t.getRegion() != null
                    && t.getRegion().isPacific()) {
                    d[x][y] = 0;
                    q.add(t);
                }
            }
        }
        while (!q.isEmpty()) {
            final Tile t = q.poll();
            for (Tile n : t.getSurroundingTiles(1)) {
                if (d[n.getX()][n.getY()] > d[t.getX()][t.getY()] + 1) {
                    d[n.getX()][n.getY()] = d[t.getX()][t.getY()] + 1;
                    q.add(n);
                }
            }
        }
        return d;
    }


    /**
     * The levi rules have the four village options with the original's
     * numbers on every level: spacing 3, 6 to 21 a tribe, at most 84;
     * the classic and freecol rules have none of them (FreeCol's
     * placement, by the level's settlement number).
     */
    public void testLeviVillageOptions() {
        for (String level : LEVELS) {
            final OptionGroup mgo = rules("levi", level).getMapGeneratorOptions();
            for (String id : new String[] { SPACING, MINIMUM, MAXIMUM, TOTAL }) {
                assertTrue(level + " " + id, mgo.hasOption(id, IntegerOption.class));
            }
            assertEquals(level, 3, mgo.getInteger(SPACING));
            assertEquals(level, 6, mgo.getInteger(MINIMUM));
            assertEquals(level, 21, mgo.getInteger(MAXIMUM));
            assertEquals(level, 84, mgo.getInteger(TOTAL));
        }
        for (String r : new String[] { "classic", "freecol" }) {
            final OptionGroup mgo = rules(r, "model.difficulty.veryEasy")
                .getMapGeneratorOptions();
            for (String id : new String[] { SPACING, MINIMUM, MAXIMUM, TOTAL }) {
                assertFalse(r + " " + id, mgo.hasOption(id, IntegerOption.class));
            }
        }
    }

    /**
     * Every tribe of a new levi map has 6 to 21 villages and one capital,
     * the map at most 84, no two next to each other, and the map is the
     * same on every level (FreeCol: by level 12 to 31 villages in all,
     * mostly 1 or 2 a tribe on the easiest).
     */
    public void testVillagesPerTribeOnEveryLevel() {
        final Topology saved = Topology.current();
        try {
            for (Topology top : new Topology[] { Topology.SQUARE,
                                                 Topology.ISOMETRIC }) {
                Topology.setCurrent(top);
                final Specification[] specs = new Specification[3];
                final String[] levels = { LEVELS[0], LEVELS[2], LEVELS[4] };
                for (int i = 0; i < specs.length; i++) {
                    specs[i] = rules("levi", levels[i]);
                }
                for (long seed = 1; seed <= 3; seed++) {
                    String first = null;
                    for (int i = 0; i < specs.length; i++) {
                        final Game game = newGame(specs[i], null, seed);
                        final String what = top + " " + levels[i]
                            + " seed " + seed;
                        assertEquals(what, 8, game.getLiveNativePlayerList().size());
                        for (Player p : game.getLiveNativePlayerList()) {
                            final List<IndianSettlement> l = p.getIndianSettlementList();
                            assertTrue(what + " " + p.getNationId() + " has "
                                + l.size(), l.size() >= 6 && l.size() <= 21);
                            int capitals = 0;
                            for (IndianSettlement is : l) {
                                if (is.isCapital()) capitals++;
                            }
                            assertEquals(what + " " + p.getNationId(), 1, capitals);
                        }
                        final List<IndianSettlement> all = villages(game);
                        assertTrue(what + ": " + all.size(), all.size() <= 84);
                        for (IndianSettlement a : all) {
                            for (IndianSettlement b : all) {
                                if (a == b) continue;
                                assertTrue(what + " " + a.getTile() + " "
                                    + b.getTile(),
                                    a.getTile().getDistanceTo(b.getTile()) >= 2);
                            }
                        }
                        final String print = fingerprint(game);
                        if (first == null) {
                            first = print;
                        } else {
                            assertEquals(what, first, print);
                        }
                    }
                }
            }
        } finally {
            Topology.setCurrent(saved);
        }
    }

    /**
     * As many villages as the original puts on that much land (Roger's
     * games: everything Normal 872 / 901 land tiles, 61 / 60 villages;
     * Groß 1079 / 1184, 79 / 84; Große Kontinente 1191, 82; land per
     * village 14.3 and 15.0 with everything Normal), but 6 a tribe at
     * least (Roger's rule: 48 on the original's «Neue Welt» of 427 to 650
     * land tiles, which had 39 to 51) and at most 84.
     */
    public void testVillagesFollowTheLand() {
        final Topology saved = Topology.current();
        try {
            for (Topology top : new Topology[] { Topology.SQUARE,
                                                 Topology.ISOMETRIC }) {
                Topology.setCurrent(top);
                final Specification spec = rules("levi", LEVELS[0]);
                final java.util.Map<String, double[]> mean = new HashMap<>();
                for (String world : new String[] { "KIgn", "NNgn", "GCgn" }) {
                    double villages = 0, land = 0;
                    final int n = 6;
                    for (long seed = 1; seed <= n; seed++) {
                        final Game game = newGame(spec, world, seed);
                        final int v = villages(game).size();
                        final String what = top + " " + world + " seed " + seed;
                        assertTrue(what + ": " + v, v >= 48 && v <= 84);
                        if ("KIgn".equals(world)) assertEquals(what, 48, v);
                        villages += v;
                        land += OriginalWorld.countLand(game.getMap());
                    }
                    mean.put(world, new double[] { villages / n, land / n });
                }
                final double[] nn = mean.get("NNgn"), gc = mean.get("GCgn");
                assertTrue(top + " Normal: " + nn[0], nn[0] >= 55 && nn[0] <= 66);
                assertTrue(top + " land per village " + nn[1] / nn[0],
                           nn[1] / nn[0] >= 13.0 && nn[1] / nn[0] <= 16.0);
                assertTrue(top + " Groß + Kontinente: " + gc[0], gc[0] >= 78);
            }
        } finally {
            Topology.setCurrent(saved);
        }
    }

    /**
     * The nearest other village, as on the original's maps (five maps,
     * 336 villages: 2 tiles away 6 %, 3 tiles 39 %, 4 tiles 39 %, 5 tiles
     * 13 %, more 3 %): never next to each other, mostly 3 or 4 away, at
     * least 4 for 40 to 80 % of them (FreeCol's one spacing of 3 gives
     * about a quarter).
     */
    public void testNearestVillage() {
        final Topology saved = Topology.current();
        try {
            for (Topology top : new Topology[] { Topology.SQUARE,
                                                 Topology.ISOMETRIC }) {
                Topology.setCurrent(top);
                final Specification spec = rules("levi", LEVELS[0]);
                int n = 0, three = 0, four = 0;
                for (long seed = 1; seed <= 6; seed++) {
                    final List<IndianSettlement> all = villages(newGame(spec, null, seed));
                    for (IndianSettlement a : all) {
                        int d = Integer.MAX_VALUE;
                        for (IndianSettlement b : all) {
                            if (a != b) d = Math.min(d, a.getTile().getDistanceTo(b.getTile()));
                        }
                        assertTrue(top + " seed " + seed + " " + a.getTile(), d >= 2);
                        n++;
                        if (d == 3) three++;
                        if (d >= 4) four++;
                    }
                }
                final double atLeastFour = 100.0 * four / n, atThree = 100.0 * three / n;
                assertTrue(top + " at least 4 away: " + atLeastFour + " %",
                           atLeastFour >= 40 && atLeastFour <= 80);
                assertTrue(top + " 3 away: " + atThree + " %", atThree >= 15);
            }
        } finally {
            Topology.setCurrent(saved);
        }
    }

    /**
     * Where the tribes live, as on the original's maps: the Inca and
     * the Aztec in the west, the Inca at the Pacific, the Iroquois and
     * the Arawak in the east, each pair in the two halves (north and
     * south), which half drawn per map; the other four tribes anywhere.
     */
    public void testTribesLiveWhereTheOriginalsDo() {
        final Topology saved = Topology.current();
        try {
            for (Topology top : new Topology[] { Topology.SQUARE,
                                                 Topology.ISOMETRIC }) {
                Topology.setCurrent(top);
                final Specification spec = rules("levi", LEVELS[0]);
                int incaNorth = 0, iroquoisNorth = 0, westApart = 0,
                    eastApart = 0;
                final int maps = 8;
                final java.util.Map<String, List<Double>> share = new HashMap<>();
                for (long seed = 1; seed <= maps; seed++) {
                    final Game game = newGame(spec, null, seed);
                    final Map map = game.getMap();
                    final Bands bands = new Bands(map);
                    final int[][] pacific = pacificDistances(map);
                    final java.util.Map<String, Boolean> north = new HashMap<>();
                    for (Player p : game.getLiveNativePlayerList()) {
                        final String id = p.getNationId();
                        final String what = top + " seed " + seed + " " + id;
                        int n = 0;
                        double x = 0;
                        final List<Integer> pac = new ArrayList<>();
                        final List<IndianSettlement> l = p.getIndianSettlementList();
                        for (IndianSettlement is : l) {
                            final Tile t = is.getTile();
                            if (bands.north(t)) n++;
                            x += bands.column(t);
                            pac.add(pacific[t.getX()][t.getY()]);
                            if (id.endsWith("inca") || id.endsWith("aztec")) {
                                assertTrue(what + " " + t, bands.column(t) < 1.0 / 3);
                            }
                            if (id.endsWith("iroquois") || id.endsWith("arawak")) {
                                assertTrue(what + " " + t, bands.column(t) > 2.0 / 3);
                            }
                        }
                        north.put(id, n * 2 > l.size());
                        share.computeIfAbsent(id, k -> new ArrayList<>())
                            .add(x / l.size());
                        if (id.endsWith("inca")) {
                            Collections.sort(pac);
                            assertTrue(what + " Pacific " + pac,
                                       pac.get(pac.size() / 2) <= 3);
                        }
                    }
                    if (north.get(TRIBES[0])) incaNorth++;
                    if (north.get(TRIBES[3])) iroquoisNorth++;
                    if (north.get(TRIBES[0]) != north.get(TRIBES[1])) westApart++;
                    if (north.get(TRIBES[3]) != north.get(TRIBES[2])) eastApart++;
                }
                assertTrue(top + " Inca north " + incaNorth,
                           incaNorth > 0 && incaNorth < maps);
                assertTrue(top + " Iroquois north " + iroquoisNorth,
                           iroquoisNorth > 0 && iroquoisNorth < maps);
                assertTrue(top + " Inca and Aztec apart " + westApart,
                           westApart >= maps - 2);
                assertTrue(top + " Iroquois and Arawak apart " + eastApart,
                           eastApart >= maps - 2);
                for (int i = 4; i < TRIBES.length; i++) {
                    final List<Double> s = share.get(TRIBES[i]);
                    final double spread = Collections.max(s) - Collections.min(s);
                    assertTrue(top + " " + TRIBES[i] + " lives anywhere: " + s,
                               spread >= 0.4);
                }
            }
        } finally {
            Topology.setCurrent(saved);
        }
    }

    /**
     * The original's native land and sizes: a home radius of 1 for camps
     * and villages, 2 for the Inca and Aztec cities, capitals the same,
     * no land beyond it (FreeCol: capitals one more, 1 to 14 tiles grown
     * at the start); every settlement starts with the original's
     * population (camp 3 / 4, village 5 / 7, Aztec 7 / 10, Inca 9 / 13;
     * FreeCol: random 4 to 12); the natives own about half the land
     * (the original 42 to 63 %).
     */
    public void testNativeLandAsTheOriginal() {
        final Topology saved = Topology.current();
        try {
            for (Topology top : new Topology[] { Topology.SQUARE,
                                                 Topology.ISOMETRIC }) {
                Topology.setCurrent(top);
                final Specification spec = rules("levi", LEVELS[0]);
                for (long seed = 1; seed <= 3; seed++) {
                    final Game game = newGame(spec, null, seed);
                    final Map map = game.getMap();
                    final String what = top + " seed " + seed;
                    for (IndianSettlement is : villages(game)) {
                        final String type = is.getType().getId();
                        assertEquals(what + " " + type, (int)SIZE.get(type),
                                     is.getUnitCount());
                        assertEquals(what + " " + type, (int)RADIUS.get(type),
                                     is.getRadius());
                    }
                    int land = 0, owned = 0;
                    for (Tile t : map.getTileList(t -> isLand(map, t))) {
                        land++;
                        if (t.getOwner() == null || !t.getOwner().isIndian()) continue;
                        owned++;
                        final Settlement s = t.getOwningSettlement();
                        assertNotNull(what + " " + t, s);
                        assertTrue(what + " " + t + " of " + s.getTile(),
                            t.getDistanceTo(s.getTile()) <= RADIUS.get(s.getType().getId()));
                    }
                    final double share = 100.0 * owned / land;
                    assertTrue(what + " native land " + share,
                               share >= 45 && share <= 70);
                }
            }
        } finally {
            Topology.setCurrent(saved);
        }
    }
}
