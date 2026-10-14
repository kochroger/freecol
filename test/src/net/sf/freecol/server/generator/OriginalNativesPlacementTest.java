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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

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
import net.sf.freecol.common.option.MapGeneratorOptions;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.server.model.ServerGame;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * The steps of {@link OriginalNatives} (part P2): how many villages a
 * land gets, the tribes' homes and lands, the least and most of a tribe,
 * the map's most, and what a tribe with too few gets.
 */
public class OriginalNativesPlacementTest extends FreeColTestCase {

    /** The generator's test of a village site. */
    private static boolean suitable(Tile tile) {
        if (!tile.getType().canSettle()) return false;
        int good = 0, n = 0;
        for (Tile t : tile.getSurroundingTiles(1)) {
            if (t.getType().canSettle()) good++;
            n++;
        }
        return good >= n / 2;
    }

    private static Game levi(long seed) {
        FreeColRules.loadRules();
        final Specification spec = FreeCol.loadSpecification(
            FreeColRules.getFreeColRulesFile("levi"), null,
            "model.difficulty.veryEasy");
        spec.setFile(MapGeneratorOptions.IMPORT_FILE, null);
        MapGeneratorOptions.applyTopologyDefaults(spec.getMapGeneratorOptions());
        final Game game = new ServerGame(spec);
        game.setNationOptions(new NationOptions(spec));
        for (Nation n : spec.getNations()) {
            if (n.isUnknownEnemy() || n.getType().isREF()) continue;
            final Player p = new ServerPlayer(game, false, n);
            p.setAI(true);
            game.addPlayer(p);
        }
        new SimpleMapGenerator(new Random(seed))
            .generateMap(game, null, true, new LogBuilder(-1));
        return game;
    }

    /** The generator's spaced sites: 4 apart, then 3. */
    private static List<Tile> spaced(List<Tile> all, Predicate<Tile> keep) {
        final Set<Tile> ret = new LinkedHashSet<>();
        for (int spacing : new int[] { 4, 3 }) {
            for (Tile t : all) {
                if (t.isPolar() || t.isOuterRing() || !suitable(t)
                    || !keep.test(t)) continue;
                boolean ok = true;
                for (Tile s : ret) {
                    if (s.getDistanceTo(t) < spacing) {
                        ok = false;
                        break;
                    }
                }
                if (ok) ret.add(t);
            }
        }
        return new ArrayList<>(ret);
    }

    private static OriginalNatives.Placement place(Game game,
        List<Tile> candidates, List<Tile> all, long seed, int min, int max,
        int total) {
        return new OriginalNatives.Placement(game.getMap(),
            game.getLiveNativePlayerList(), candidates, all,
            OriginalNativesPlacementTest::suitable, new Random(seed),
            min, max, total).place(new LogBuilder(-1));
    }

    /** No two sites next to each other; the added ones 2 apart at least. */
    private static void assertSpaced(String what, List<Tile> sites,
                                     List<Tile> candidates) {
        for (Tile a : sites) {
            for (Tile b : sites) {
                if (a == b) continue;
                final int d = a.getDistanceTo(b);
                assertTrue(what + " " + a + " " + b, d >= 2);
                if (candidates.contains(a) && candidates.contains(b)) {
                    assertTrue(what + " " + a + " " + b, d >= 3);
                }
            }
        }
    }


    /**
     * The original's villages on so much land: the means of Roger's
     * settings («Neue Welt» 515 land tiles / 43.7 villages, Klein and
     * Inselgruppe 701 / 54.3, everything Normal 886.5 / 60.5, Groß
     * 1131.5 / 81.5, Große Kontinente 1191 / 82), in between on a line,
     * no more above, and the map's number within the rules' least and
     * most.
     */
    public void testVillagesOfTheLand() {
        assertEquals(43.67, OriginalNatives.getVillages(515), 0.05);
        assertEquals(54.33, OriginalNatives.getVillages(701), 0.01);
        assertEquals(60.5, OriginalNatives.getVillages(886), 0.1);
        assertEquals(81.5, OriginalNatives.getVillages(1131), 0.2);
        assertEquals(82.0, OriginalNatives.getVillages(1191), 0.01);
        assertEquals(82.0, OriginalNatives.getVillages(1373), 0.01);
        assertEquals(82.0, OriginalNatives.getVillages(1679), 0.01);
        // The «Neue Welt» games themselves: 427 / 39, 469 / 41, 650 / 51.
        assertEquals(39, OriginalNatives.getVillages(427), 1.5);
        assertEquals(41, OriginalNatives.getVillages(469), 1.5);
        assertEquals(51, OriginalNatives.getVillages(650), 1.5);
        double last = 0;
        for (int land = 100; land <= 2000; land += 7) {
            final double v = OriginalNatives.getVillages(land);
            assertTrue(land + ": " + v, v >= last && v >= 0);
            last = v;
        }
        assertEquals(48, OriginalNatives.getTarget(427, 8, 6, 21, 84));
        assertEquals(60, OriginalNatives.getTarget(886, 8, 6, 21, 84));
        assertEquals(62, OriginalNatives.getTarget(901, 8, 6, 21, 84));
        assertEquals(82, OriginalNatives.getTarget(1373, 8, 6, 21, 84));
        assertEquals(70, OriginalNatives.getTarget(1373, 8, 6, 21, 70));
        assertEquals(56, OriginalNatives.getTarget(886, 8, 6, 7, 84));
        assertEquals(40, OriginalNatives.getTarget(300, 8, 5, 21, 84));
    }

    /**
     * The bands of the land and the tribes' homes: the Inca's home in
     * the western third of its half at the Pacific, the Aztec's in the
     * western third of the other half, the Iroquois' and the Arawak's
     * likewise in the east; the bands' tribes' sites only in their band;
     * the map's number of villages, 6 to 21 a tribe; the same seed the
     * same placement.
     */
    public void testHomesAndLands() {
        final Topology saved = Topology.current();
        try {
            for (Topology top : new Topology[] { Topology.SQUARE,
                                                 Topology.ISOMETRIC }) {
                Topology.setCurrent(top);
                for (long seed = 1; seed <= 4; seed++) {
                    final Game game = levi(seed);
                    final Map map = game.getMap();
                    final List<Tile> all = map.getShuffledTiles(new Random(seed));
                    final List<Tile> candidates = spaced(all, t -> true);
                    final OriginalNatives.Placement p
                        = place(game, candidates, all, seed, 6, 21, 84);
                    final String what = top + " seed " + seed;
                    final OriginalNatives.Bands bands = p.getBands();
                    final int[][] pacific = OriginalNatives.getPacificDistances(map);
                    final java.util.Map<String, Tile> home = new java.util.HashMap<>();
                    for (java.util.Map.Entry<Player, Tile> e : p.getHomes().entrySet()) {
                        home.put(e.getKey().getNationId(), e.getValue());
                    }
                    assertEquals(what, 8, home.size());
                    final Tile inca = home.get("model.nation.inca"),
                        aztec = home.get("model.nation.aztec"),
                        iroquois = home.get("model.nation.iroquois"),
                        arawak = home.get("model.nation.arawak");
                    assertTrue(what, bands.isWest(inca) && bands.isWest(aztec));
                    assertTrue(what, bands.isEast(iroquois) && bands.isEast(arawak));
                    assertEquals(what, p.isIncaNorth(), bands.isNorth(inca));
                    assertEquals(what, !p.isIncaNorth(), bands.isNorth(aztec));
                    assertEquals(what, p.isIroquoisNorth(), bands.isNorth(iroquois));
                    assertEquals(what, !p.isIroquoisNorth(), bands.isNorth(arawak));
                    assertTrue(what + " Inca's home " + inca,
                        pacific[inca.getX()][inca.getY()]
                            <= OriginalNatives.PACIFIC_HOME_DISTANCE);
                    assertEquals(what, new HashSet<>(home.values()).size(), 8);

                    int total = 0;
                    for (java.util.Map.Entry<Player, Set<Tile>> e
                             : p.getAreas().entrySet()) {
                        final String id = e.getKey().getNationId();
                        final int n = e.getValue().size();
                        assertTrue(what + " " + id + " " + n, n >= 6 && n <= 21);
                        total += n;
                        for (Tile t : e.getValue()) {
                            switch (OriginalNatives.getBand(id)) {
                            case WEST: assertTrue(what + " " + id + " " + t, bands.isWest(t)); break;
                            case EAST: assertTrue(what + " " + id + " " + t, bands.isEast(t)); break;
                            default: break;
                            }
                        }
                    }
                    assertEquals(what, p.getSites().size(), total);
                    assertEquals(what, p.getTarget(), total);
                    assertSpaced(what, p.getSites(), candidates);
                    // The same seed, the same placement.
                    final OriginalNatives.Placement again
                        = place(game, candidates, all, seed, 6, 21, 84);
                    assertEquals(what, p.getSites(), again.getSites());
                }
            }
        } finally {
            Topology.setCurrent(saved);
        }
    }

    /**
     * The least and the most: no tribe above the rules' most; a map
     * whose spaced sites are all in the west half still gives every tribe
     * its least (spaced sites of tribes with more first, then new ones 2
     * apart at least, the bands' tribes in their band); a map without
     * spaced sites gives every tribe its least of new ones; the map's
     * most is kept.
     */
    public void testLeastAndMost() {
        final Topology saved = Topology.current();
        try {
            Topology.setCurrent(Topology.SQUARE);
            final Game game = levi(1);
            final Map map = game.getMap();
            final List<Tile> all = map.getShuffledTiles(new Random(1));
            final List<Tile> candidates = spaced(all, t -> true);

            OriginalNatives.Placement p = place(game, candidates, all, 1, 6, 7, 84);
            for (Set<Tile> s : p.getAreas().values()) {
                assertTrue(s.size() + "", s.size() >= 6 && s.size() <= 7);
            }

            p = place(game, candidates, all, 1, 6, 21, 50);
            assertEquals(50, p.getSites().size());

            final List<Tile> west = spaced(all, t -> t.getX() < map.getWidth() / 2);
            p = place(game, west, all, 1, 6, 21, 84);
            assertTrue("sites added " + p.getTopUps(), p.getTopUps() > 0);
            assertEquals(0, p.getMissing());
            for (java.util.Map.Entry<Player, Set<Tile>> e : p.getAreas().entrySet()) {
                final String id = e.getKey().getNationId();
                assertTrue(id + " " + e.getValue().size(), e.getValue().size() >= 6);
                for (Tile t : e.getValue()) {
                    if (OriginalNatives.getBand(id) == OriginalNatives.Band.EAST) {
                        assertTrue(id + " " + t, p.getBands().isEast(t));
                    }
                }
            }
            assertSpaced("west only", p.getSites(), west);

            p = place(game, new ArrayList<>(), all, 1, 6, 21, 84);
            assertEquals(48, p.getSites().size());
            assertEquals(48, p.getTopUps());
            for (Set<Tile> s : p.getAreas().values()) assertEquals(6, s.size());
            assertSpaced("no spaced sites", p.getSites(), new ArrayList<>());

            // A land too small for the least of every tribe: as many as
            // fit, never two next to each other, the rest missing.
            p = place(game, candidates, all, 1, 30, 40, 999);
            assertTrue("missing " + p.getMissing(), p.getMissing() > 0);
            assertSpaced("full land", p.getSites(), candidates);

            // A tribe without spaced sites of its own takes those of a
            // tribe with more than its least before it gets new ones.
            final OriginalNatives.Placement full
                = place(game, candidates, all, 1, 6, 21, 84);
            final Player tupi = game.getPlayerByNationId("model.nation.tupi");
            final Set<Tile> own = full.getAreas().get(tupi);
            final List<Tile> fewer = new ArrayList<>(candidates);
            for (Tile t : candidates) {
                if (full.getOwner(t) == tupi) fewer.remove(t);
            }
            p = place(game, fewer, all, 1, 6, 21, 84);
            assertTrue("moved " + p.getMoved(), p.getMoved() >= 6);
            assertEquals(6, p.getAreas().get(tupi).size());
            for (Tile t : p.getAreas().get(tupi)) {
                assertTrue(t + " is a spaced site", fewer.contains(t));
                assertFalse(t + " was not the Tupi's", own.contains(t));
            }
        } finally {
            Topology.setCurrent(saved);
        }
    }
}
