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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.xml.stream.XMLStreamException;

import net.sf.freecol.FreeCol;
import net.sf.freecol.common.FreeColException;
import net.sf.freecol.common.io.FreeColDirectories;
import net.sf.freecol.common.io.FreeColRules;
import net.sf.freecol.common.model.Direction;
import net.sf.freecol.common.model.FreeColObject;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Nation;
import net.sf.freecol.common.model.NationOptions;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Region;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileImprovement;
import net.sf.freecol.common.model.Topology;
import net.sf.freecol.common.model.Turn;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.option.MapGeneratorOptions;
import net.sf.freecol.common.option.OptionGroup;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.server.FreeColServer;
import net.sf.freecol.server.ServerTestHelper;
import net.sf.freecol.server.model.ServerGame;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;


public class MapGeneratorTest extends FreeColTestCase {

    public void testWithNoIndians() {
        spec().setFile(MapGeneratorOptions.IMPORT_FILE, null);

        Game g = getStandardGame();
        g.setNationOptions(new NationOptions(spec()));

        // A new game has no map
        assertNull("No new map", g.getMap());

        MapGenerator gen = new SimpleMapGenerator(new Random(1));

        for (Nation n : spec().getNations()) {
            if (n.getType().isEuropean() && !n.getType().isREF()
                && !n.isUnknownEnemy()) {
                g.addPlayer(new ServerPlayer(g, false, n));
            }
        }

        gen.generateMap(g, null, true, new LogBuilder(-1));
        assertNotNull("New map", g.getMap());
    }

    public void testSinglePlayerOnSmallMap() {
        spec().setFile(MapGeneratorOptions.IMPORT_FILE, null);

        Game g = getStandardGame();
        g.setNationOptions(new NationOptions(spec()));

        // A new game has no map
        assertNull("No new map", g.getMap());

        MapGenerator gen = new SimpleMapGenerator(new Random(1));
        Nation nation = spec().getNation("model.nation.dutch");

        g.addPlayer(new ServerPlayer(g, false, nation));

        gen.generateMap(g, null, true, new LogBuilder(-1));
        assertNotNull("New map", g.getMap());

        // Check that the map is created at all
        assertNotNull(g.getMap());

        assertEquals(g.getMapGeneratorOptions().getInteger(MapGeneratorOptions.MAP_WIDTH),
                     g.getMap().getWidth());
        assertEquals(g.getMapGeneratorOptions().getInteger(MapGeneratorOptions.MAP_HEIGHT),
                     g.getMap().getHeight());

    }

    public void testMapGenerator() {
        spec().setFile(MapGeneratorOptions.IMPORT_FILE, null);

        Game g = getStandardGame();
        g.setNationOptions(new NationOptions(spec()));

        // A new game has no map
        assertNull("No new map", g.getMap());

        // Apply the difficulty level
        //spec().applyDifficultyLevel("model.difficulty.medium");

        MapGenerator gen = new SimpleMapGenerator(new Random(1));
        gen.generateMap(g, null, true, new LogBuilder(-1));
        assertNotNull("New map", g.getMap());

        // Map of correct size?
        Map m = g.getMap();
        assertEquals(m.getWidth(),
                     g.getMapGeneratorOptions().getInteger(MapGeneratorOptions.MAP_WIDTH));
        assertEquals(m.getHeight(),
                     g.getMapGeneratorOptions().getInteger(MapGeneratorOptions.MAP_HEIGHT));

        // Sufficient land?
        int total = m.getWidth() * m.getHeight();
        int land = m.getTileSet(Tile::isLand).size();
        // Land Mass requirement fulfilled?
        assertTrue(100 * land / total >= g.getMapGeneratorOptions()
                   .getInteger(MapGeneratorOptions.LAND_MASS));

        // Does the wholeMapIterator visit all fields?
        assertEquals(total,
                     g.getMapGeneratorOptions().getInteger(MapGeneratorOptions.MAP_WIDTH)
                     * g.getMapGeneratorOptions().getInteger(MapGeneratorOptions.MAP_HEIGHT));
    }

    /**
     * Make sure that each tribe has exactly one capital
     *
     */
    public void testIndianCapital() {
        spec().setFile(MapGeneratorOptions.IMPORT_FILE, null);

        Game g = getStandardGame();
        g.setNationOptions(new NationOptions(spec()));

        MapGenerator gen = new SimpleMapGenerator(new Random(1));

        List<Player> players = new ArrayList<>();
        for (Nation n : spec().getNations()) {
            if (n.isUnknownEnemy()) continue;
            Player p = new ServerPlayer(g, false, n);
            p.setAI(!n.getType().isEuropean() || n.getType().isREF());
            g.addPlayer(p);
            players.add(p);
        }

        gen.generateMap(g, null, true, new LogBuilder(-1));

        // Check that the map is created at all
        assertNotNull(g.getMap());

        for (Player p : players) {
            if (!p.isIndian())
                continue;

            // Check that every indian player has exactly one capital if s/he
            // has at least one settlement.
            int settlements = 0;
            int capitals = 0;
            for (IndianSettlement s : p.getIndianSettlementList()) {
                settlements++;
                if (s.isCapital()) capitals++;
            }
            if (settlements > 0) assertEquals(1, capitals);
        }
    }

    /**
     * Make sure we can import all distributed maps.
     *
     * The bundled maps predate the stored topology, so they are
     * isometric: whatever topology is in use (the square one in a
     * Classic UI session or under -Dfreecol.topology=square), reading
     * one puts the isometric topology in use, and the game made from it
     * is isometric too.  Their river connections must then match their
     * neighbours (under the square topology only about 30% would).
     */
    public void testImportMap() {
        MapGenerator gen = new SimpleMapGenerator(new Random(1));
        Map importMap = null;
        long connections = 0, matching = 0;
        for (File importFile : FreeColDirectories.getMapFileList()) {
            Game game = getStandardGame();
            Specification spec = game.getSpecification();
            spec.setFile(MapGeneratorOptions.IMPORT_FILE, importFile);
            System.gc(); // Try to clean up before reading a big map
            final Topology before = Topology.current();
            try {
                importMap = FreeColServer.readMap(importFile, spec);
            } catch (FreeColException|IOException|XMLStreamException ex) {
                fail("Map read of " + importFile.getName() + " failed: "
                    + ex.toString());
            }
            assertEquals(importFile.getName(), Topology.ISOMETRIC,
                         importMap.getTopology());
            assertEquals(importFile.getName(), Topology.ISOMETRIC,
                         Topology.current());
            for (Tile tile : importMap) {
                TileImprovement river = tile.getRiver();
                if (river == null) continue;
                for (Direction d : Topology.current().edgeDirections()) {
                    if (!river.isConnectedTo(d)) continue;
                    connections++;
                    Tile t = tile.getNeighbourOrNull(d);
                    if (t != null && (!t.isLand()
                            || (t.getRiver() != null && t.getRiver()
                                .isConnectedTo(d.getReverseDirection())))) {
                        matching++;
                    }
                }
            }
            try {
                assertNotNull(gen.generateMap(game, importMap, true,
                                              new LogBuilder(-1)));
            } catch (Exception ex) {
                fail("Map generate of " + importFile.getName() + " failed: "
                    + ex.toString());
            }
            assertEquals(importFile.getName(), Topology.ISOMETRIC,
                         game.getMap().getTopology());
            // Clear import file option from a standard spec!
            spec.setFile(MapGeneratorOptions.IMPORT_FILE, null);
            Topology.setCurrent(before);
        }
        assertTrue("river connections " + matching + "/" + connections,
                   connections > 0 && matching * 100 >= connections * 99);
    }

    /**
     * A river may reach the sea right next to the map edge, where its
     * delta finds no neighbour.  That used to throw in River.delta and
     * abort the whole map.  A warm climate lets rivers into the polar
     * rows of a square map of the original's size; with seeds 28 and 38
     * a mouth lands on such a tile in column 0 or 57.
     */
    public void testRiverMouthAtMapEdge() {
        final Topology saved = Topology.current();
        try {
            Topology.setCurrent(Topology.SQUARE);
            for (int seed : new int[] { 28, 38 }) {
                Specification spec = FreeCol.loadSpecification(
                    FreeColRules.getFreeColRulesFile("freecol"), null,
                    "model.difficulty.medium");
                spec.setFile(MapGeneratorOptions.IMPORT_FILE, null);
                OptionGroup mgo = spec.getMapGeneratorOptions();
                MapGeneratorOptions.applyTopologyDefaults(mgo);
                mgo.setInteger(MapGeneratorOptions.TEMPERATURE,
                               MapGeneratorOptions.TEMPERATURE_WARM);
                Game game = new ServerGame(spec);
                NationOptions nationOptions = new NationOptions(spec);
                for (Nation n : spec.getEuropeanNations()) {
                    nationOptions.setNationState(n,
                        NationOptions.NationState.AVAILABLE);
                }
                game.setNationOptions(nationOptions);
                for (Nation n : spec.getNations()) {
                    if (n.isUnknownEnemy()) continue;
                    Player p = new ServerPlayer(game, false, n);
                    boolean ai = !n.getType().isEuropean()
                        || n.getType().isREF();
                    p.setAI(ai);
                    if (ai || game.canAddNewPlayer()) game.addPlayer(p);
                }
                MapGenerator gen = new SimpleMapGenerator(new Random(seed));
                gen.generateMap(game, null, false, new LogBuilder(-1));
                assertNotNull("map for seed " + seed, game.getMap());
                assertEquals(58, game.getMap().getWidth());
            }
        } finally {
            Topology.setCurrent(saved);
        }
    }

    /**
     * A new game for the map generator, with every European nation
     * available, as a Classic UI new game sets it up: under the square
     * topology with the square map options (58x72).
     *
     * @return The new {@code Game}, without a map.
     */
    private static Game makeNewGame() {
        Specification spec = FreeCol.loadSpecification(
            FreeColRules.getFreeColRulesFile("freecol"), null,
            "model.difficulty.medium");
        spec.setFile(MapGeneratorOptions.IMPORT_FILE, null);
        MapGeneratorOptions.applyTopologyDefaults(spec.getMapGeneratorOptions());
        Game game = new ServerGame(spec);
        NationOptions nationOptions = new NationOptions(spec);
        for (Nation n : spec.getEuropeanNations()) {
            nationOptions.setNationState(n,
                NationOptions.NationState.AVAILABLE);
        }
        game.setNationOptions(nationOptions);
        for (Nation n : spec.getNations()) {
            if (n.isUnknownEnemy()) continue;
            Player p = new ServerPlayer(game, false, n);
            boolean ai = !n.getType().isEuropean() || n.getType().isREF();
            p.setAI(ai);
            if (ai || game.canAddNewPlayer()) game.addPlayer(p);
        }
        return game;
    }

    /**
     * A square map (the Classic UI's) has the outer ring, which the
     * Classic UI never draws: the European ships start in the column
     * just inside it, on a tile that leads to Europe; nothing stands on
     * the ring, and no native settlement or rumour is put there.  An
     * isometric map has no ring, and its ships start on FreeCol's
     * innermost high seas tile of their row, as before.
     */
    public void testStartsKeepOffTheOuterRing() {
        final Topology saved = Topology.current();
        try {
            for (Topology topology : Topology.values()) {
                Topology.setCurrent(topology);
                final boolean square = topology == Topology.SQUARE;
                for (int seed = 1; seed <= 3; seed++) {
                    final String what = topology + " seed " + seed;
                    Game game = makeNewGame();
                    new SimpleMapGenerator(new Random(seed))
                        .generateMap(game, null, true, new LogBuilder(-1));
                    final Map map = game.getMap();
                    assertEquals(what, square, map.hasOuterRing());
                    int ships = 0;
                    for (Player p : game.getLiveEuropeanPlayerList()) {
                        for (Unit u : p.getUnitSet()) {
                            if (!u.isNaval() || !u.hasTile()) continue;
                            final Tile t = u.getTile();
                            final String at = what + " " + p.getNationId()
                                + " " + t.getX() + "," + t.getY();
                            assertTrue(at, t.isDirectlyHighSeasConnected());
                            assertEquals(at, t, p.getEntryTile());
                            final boolean east = t.getX() > map.getWidth() / 2;
                            if (square) {
                                assertEquals(at, (east) ? map.getWidth() - 2
                                    : 1, t.getX());
                            } else {
                                // FreeCol's rule: the innermost high seas
                                // tile of the row, counted from the edge.
                                final int step = (east) ? 1 : -1;
                                for (int x = t.getX(); x >= 0
                                         && x < map.getWidth(); x += step) {
                                    assertTrue(at, map.getTile(x, t.getY())
                                        .isDirectlyHighSeasConnected());
                                }
                                assertFalse(at, map.getTile(t.getX() - step,
                                    t.getY()).isDirectlyHighSeasConnected());
                            }
                            ships++;
                        }
                    }
                    assertTrue(what + " ships " + ships, ships >= 4);
                    for (Tile t : map.getTileList(Tile::isOuterRing)) {
                        assertEquals(what + " " + t, 0, t.getUnitCount());
                        assertFalse(what + " " + t, t.hasSettlement());
                        assertFalse(what + " " + t, t.hasLostCityRumour());
                    }
                }
            }
        } finally {
            Topology.setCurrent(saved);
        }
    }

    /**
     * The terrain of a map and the tiles of the European ships, as text.
     *
     * @param game The {@code Game} with the map.
     * @return The map's fingerprint.
     */
    private static String fingerprint(Game game) {
        StringBuilder sb = new StringBuilder();
        for (Tile t : game.getMap()) sb.append(t.getType().getSuffix()).append(' ');
        for (Player p : game.getLiveEuropeanPlayerList()) {
            for (Unit u : p.getUnitSet()) {
                if (u.isNaval() && u.hasTile()) {
                    sb.append(p.getNationId()).append('@')
                        .append(u.getTile().getX()).append(',')
                        .append(u.getTile().getY()).append(' ');
                }
            }
        }
        return sb.toString();
    }

    /**
     * A new game on a server started with a seed (--seed N): the map
     * generator and the start positions take the server's random
     * numbers, so two servers with the same seed make the same map and
     * put the ships on the same tiles (master plan N18).  Without a
     * seed, two servers make different maps.
     */
    public void testSeedFixesTheNewGame() throws Exception {
        final Topology saved = Topology.current();
        final java.lang.reflect.Field seeded
            = net.sf.freecol.common.FreeColSeed.class.getDeclaredField("seeded");
        final java.lang.reflect.Field seed
            = net.sf.freecol.common.FreeColSeed.class.getDeclaredField("freeColSeed");
        seeded.setAccessible(true);
        seed.setAccessible(true);
        final boolean wasSeeded = seeded.getBoolean(null);
        final long oldSeed = seed.getLong(null);
        try {
            Topology.setCurrent(Topology.SQUARE);
            // The first game in a JVM also shuffles FreeCol's static name
            // lists (NameCache) with the server's random numbers, so it
            // draws more of them than a later game: game 0 warms up.
            final String[] prints = new String[5];
            for (int i = 0; i < prints.length; i++) {
                if (i < 3) {
                    net.sf.freecol.common.FreeColSeed.setFreeColSeed("7");
                } else {
                    seeded.setBoolean(null, false);
                }
                Game template = makeNewGame();
                FreeColServer server = ServerTestHelper.startServer(false,
                    true, template.getSpecification());
                Game game = server.getGame();
                game.setNationOptions(template.getNationOptions());
                for (Player p : template.getPlayers(p -> true)
                         .collect(java.util.stream.Collectors.toList())) {
                    Player q = new ServerPlayer(game, false, p.getNation());
                    q.setAI(p.isAI());
                    game.addPlayer(q);
                }
                server.getMapGenerator().generateMap(game, null, true,
                                                     new LogBuilder(-1));
                prints[i] = fingerprint(game);
                assertTrue(prints[i].contains("@56,"));
                ServerTestHelper.stopServer();
            }
            assertEquals("same seed, same map and start", prints[1], prints[2]);
            assertFalse("no seed, another map", prints[3].equals(prints[4]));
        } finally {
            seeded.setBoolean(null, wasSeeded);
            seed.setLong(null, oldSeed);
            ServerTestHelper.stopServer();
            Topology.setCurrent(saved);
        }
    }

    public void testRegions() {
        spec().setFile(MapGeneratorOptions.IMPORT_FILE, null);
        Game game = getStandardGame();
        MapGenerator gen = new SimpleMapGenerator(new Random(1));
        gen.generateMap(game, null, true, new LogBuilder(-1));
        
        Map map = game.getMap();
        Region pacific = map.getRegionByKey("model.region.pacific");
        assertNotNull(pacific);
        assertTrue(pacific.isPacific());
        assertEquals(pacific, pacific.getDiscoverableRegion());

        Region southPacific = map.getRegionByKey("model.region.southPacific");
        assertNotNull(southPacific);
        assertFalse(southPacific.getDiscoverable());
        assertTrue(southPacific.isPacific());
        assertEquals(pacific, southPacific.getParent());
        assertEquals(pacific, southPacific.getDiscoverableRegion());

        Player player = new Player(game, FreeColObject.ID_ATTRIBUTE_TAG);
        ServerUnit unit = new ServerUnit(game, null, player,
            spec().getUnitType("model.unit.caravel"));
        assertTrue(pacific.checkDiscover(unit));
        List<Region> discovered = pacific.discover(player, unit, new Turn(1));
        // The Pacific sub-regions are not discoverable
        assertEquals(1, discovered.size());
        assertEquals(pacific, discovered.get(0));

        assertFalse(pacific.getDiscoverable());
        assertNull(pacific.getDiscoverableRegion());
        assertFalse(southPacific.getDiscoverable());
        assertTrue(southPacific.isPacific());
        assertEquals(pacific, southPacific.getParent());
        assertNull(southPacific.getDiscoverableRegion());

        Region atlantic = map.getRegionByKey("model.region.atlantic");
        assertNotNull(atlantic);
        assertFalse(atlantic.isPacific());
        assertFalse(atlantic.getDiscoverable());
        assertNull(atlantic.getDiscoverableRegion());

        Region northAtlantic = map.getRegionByKey("model.region.northAtlantic");
        assertNotNull(northAtlantic);
        assertFalse(northAtlantic.isPacific());
        assertFalse(northAtlantic.getDiscoverable());
        assertNull(northAtlantic.getDiscoverableRegion());
    }
}
