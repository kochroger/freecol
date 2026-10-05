/**
 *  Copyright (C) 2002-2024   The FreeCol Team
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

package net.sf.freecol.common.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import net.sf.freecol.client.ClientTestHelper;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.server.ServerTestHelper;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * The map tests with isometric coordinates written in
 * (MapTest#testGetSurroundingTiles, #testCircleIterator,
 * #testShortestPathObstructed, MovementTest#testMoveAlongRiver,
 * MoveTest#testSimpleMove), translated to the {@link Topology#SQUARE}
 * map, plus an exact check of the circle iterator on every tile of a
 * map the size of the original's.  Every test runs under the square
 * topology, whatever is in use.
 */
public class SquareMapTest extends FreeColTestCase {

    private static final TileType ocean
        = spec().getTileType("model.tile.ocean");
    private static final TileType plains
        = spec().getTileType("model.tile.plains");

    private static final UnitType colonistType
        = spec().getUnitType("model.unit.freeColonist");
    private static final UnitType pioneerType
        = spec().getUnitType("model.unit.hardyPioneer");

    /** The topology in use before the test. */
    private Topology savedTopology;


    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.savedTopology = Topology.current();
        Topology.setCurrent(Topology.SQUARE);
    }

    @Override
    protected void tearDown() throws Exception {
        ServerTestHelper.stopServerGame();
        Topology.setCurrent(this.savedTopology);
        super.tearDown();
    }

    private static <T> List<T> toList(Iterator<T> it) {
        List<T> result = new ArrayList<>();
        while (it.hasNext()) result.add(it.next());
        return result;
    }

    private static <T> List<T> toList(Iterable<T> it) {
        return toList(it.iterator());
    }

    private static Set<Tile> tiles(Map map, int[][] xy) {
        Set<Tile> result = new HashSet<>();
        for (int[] p : xy) result.add(map.getTile(p[0], p[1]));
        return result;
    }

    private static List<Tile> tileList(Map map, int[][] xy) {
        List<Tile> result = new ArrayList<>();
        for (int[] p : xy) result.add(map.getTile(p[0], p[1]));
        return result;
    }


    /** MapTest#testGetSurroundingTiles: the 3x3 block around a tile. */
    public void testGetSurroundingTiles() {
        Game game = getStandardGame();
        Map map = new MapBuilder(game).setDimensions(10, 15).build();
        game.changeMap(map);
        assertEquals(Topology.SQUARE, map.getTopology());

        assertEquals(tiles(map, new int[][] {
                    {3,7}, {4,7}, {5,7}, {3,8}, {5,8}, {3,9}, {4,9}, {5,9} }),
            new HashSet<>(toList(map.getTile(4, 8).getSurroundingTiles(1))));
        // The corner tile has three neighbours.
        assertEquals(tiles(map, new int[][] { {1,0}, {0,1}, {1,1} }),
            new HashSet<>(toList(map.getTile(0, 0).getSurroundingTiles(1))));
        assertEquals(24, toList(map.getTile(4, 8).getSurroundingTiles(2)).size());
        assertEquals(149, toList(map.getTile(4, 8).getSurroundingTiles(10)).size());
    }

    /**
     * MapTest#testCircleIterator: the spiral runs ring 1 clockwise from
     * N, then ring 2 from one N step past the last ring-1 tile along E,
     * S, W and N (the isometric walk turned by -45 degrees).
     */
    public void testCircleIterator() {
        Game game = getStandardGame();
        Map map = getCoastTestMap(plains, true);
        game.changeMap(map);
        Tile center = map.getTile(10, 10);

        assertEquals(0, toList(map.getCircleIterator(center, false, -1)).size());
        assertEquals(0, toList(map.getCircleIterator(center, false, 0)).size());
        assertEquals(8, toList(map.getCircleIterator(center, false, 1)).size());
        assertEquals(16, toList(map.getCircleIterator(center, false, 2)).size());
        List<Tile> filled = toList(map.getCircleIterator(center, true, 2));
        assertEquals(tileList(map, new int[][] {
                    {10,9}, {11,9}, {11,10}, {11,11}, {10,11}, {9,11},
                    {9,10}, {9,9},
                    {9,8}, {10,8}, {11,8}, {12,8}, {12,9}, {12,10},
                    {12,11}, {12,12}, {11,12}, {10,12}, {9,12}, {8,12},
                    {8,11}, {8,10}, {8,9}, {8,8} }), filled);
        for (int i = 0; i < filled.size(); i++) {
            assertEquals("distance of " + filled.get(i), (i < 8) ? 1 : 2,
                         center.getDistanceTo(filled.get(i)));
        }
        assertEquals("Radius 1 in corner", 5,
            toList(map.getCircleIterator(map.getTile(0, 1), false, 1)).size());
        assertEquals("Radius 1 in corner2", 5,
            toList(map.getCircleIterator(map.getTile(map.getWidth() - 1, 1),
                                         false, 1)).size());
    }

    /**
     * On every tile of a 58x72 map, edges and corners included, the
     * circle iterator returns each valid tile at distance 1..r (filled)
     * or exactly r (ring) once, for r = 1..4.
     */
    public void testCircleIteratorAllTiles() {
        final int width = 58, height = 72;
        Game game = getStandardGame();
        Map map = new MapBuilder(game).setDimensions(width, height).build();
        game.changeMap(map);
        for (Tile c : map) {
            for (int r = 1; r <= 4; r++) {
                for (boolean filled : new boolean[] { false, true }) {
                    final String where = c + " r=" + r + " filled=" + filled;
                    Set<Tile> expect = new HashSet<>();
                    for (int x = c.getX() - r; x <= c.getX() + r; x++) {
                        for (int y = c.getY() - r; y <= c.getY() + r; y++) {
                            if (!map.isValid(x, y)) continue;
                            int d = Math.max(Math.abs(x - c.getX()),
                                             Math.abs(y - c.getY()));
                            if (d == r || (filled && d > 0)) {
                                expect.add(map.getTile(x, y));
                            }
                        }
                    }
                    List<Tile> got = toList(map.getCircleIterator(c, filled, r));
                    assertEquals("duplicates at " + where,
                                 got.size(), new HashSet<>(got).size());
                    assertEquals("tiles at " + where,
                                 expect, new HashSet<>(got));
                }
            }
        }
    }

    /**
     * MapTest#testShortestPathObstructed, the map redrawn on squares:
     * the short way runs through the tile of the camp to come, and the
     * path found after the camp is built goes round it.
     */
    public void testShortestPathObstructed() {
        Game game = getStandardGame();
        MapBuilder builder = new MapBuilder(game);
        builder.setBaseTileType(ocean);
        for (int[] p : new int[][] {
                {1,9},                                          // start
                {2,8}, {3,7},                                   // short
                {0,8}, {0,7}, {0,6}, {0,5}, {1,4}, {2,4}, {3,5}, // long
                {4,6},                                          // common
                {5,5} }) {                                      // finish
            builder.setTileType(p[0], p[1], plains);
        }
        Map map = builder.build();
        game.changeMap(map);

        Player dutch = game.getPlayerByNationId("model.nation.dutch");
        Tile settlementTile = map.getTile(2, 8);
        Tile destination = map.getTile(5, 5);
        Unit colonist = new ServerUnit(game, map.getTile(1, 9), dutch,
                                       colonistType);

        PathNode free = colonist.findPath(destination);
        assertNotNull("free path", free);
        boolean viaCamp = false;
        for (PathNode p = free; p != null; p = p.next) {
            if (p.getTile() == settlementTile) viaCamp = true;
        }
        assertTrue("free path takes the short way", viaCamp);

        new FreeColTestCase.IndianSettlementBuilder(game)
            .settlementTile(settlementTile).build();
        colonist.setDestination(destination);
        PathNode path = colonist.findPath(destination);
        assertNotNull("A path should be available", path);
        for (PathNode p = path; p != null; p = p.next) {
            assertNotSame("path avoids the camp", settlementTile, p.getTile());
        }
        assertEquals(destination, path.getLastNode().getTile());
    }

    /**
     * MovementTest#testMoveAlongRiver: rivers cross the N, E, S and W
     * edges, so "0101" is E+W, and two rivers joined across the N/S edge
     * cut the move cost.
     */
    public void testMoveAlongRiver() {
        Game game = getStandardGame();
        Map map = getTestMap(ocean);
        game.changeMap(map);
        final List<Direction> edges = Topology.current().edgeDirections();
        assertEquals(List.of(Direction.N, Direction.E, Direction.S, Direction.W),
                     edges);

        Player dutch = game.getPlayerByNationId("model.nation.dutch");
        Tile tile1 = map.getTile(5, 8);
        Tile tile2 = tile1.getNeighbourOrNull(Direction.N);
        assertEquals(map.getTile(5, 7), tile2);
        tile1.setType(plains);
        tile2.setType(plains);
        tile1.setExplored(dutch, true);
        tile2.setExplored(dutch, true);
        assertEquals(Direction.N, map.getDirection(tile1, tile2));
        assertEquals(Direction.S, map.getDirection(tile2, tile1));

        TileImprovement river1 = tile1.addRiver(1, "0101");
        TileImprovement river2 = tile2.addRiver(1, "0101");
        assertTrue(river1.isComplete());
        assertTrue(river2.isComplete());
        for (TileImprovement r : List.of(river1, river2)) {
            assertFalse(r.isConnectedTo(Direction.N));
            assertTrue(r.isConnectedTo(Direction.E));
            assertFalse(r.isConnectedTo(Direction.S));
            assertTrue(r.isConnectedTo(Direction.W));
        }
        Unit colonist = new ServerUnit(game, tile1, dutch, colonistType);
        assertEquals("Parallel rivers, no reduction", 3,
                     colonist.getMoveCost(tile2));

        river1.updateRiverConnections("1000");
        river2.updateRiverConnections("0010");
        assertTrue(river1.isConnectedTo(Direction.N));
        assertTrue(river2.isConnectedTo(Direction.S));
        assertFalse(river1.isConnectedTo(Direction.E));
        assertFalse(river2.isConnectedTo(Direction.E));
        assertEquals("Connected rivers, reduction", 1,
                     colonist.getMoveCost(tile2));
        // A river never connects across a corner.
        for (Direction d : Topology.current().cornerDirections()) {
            assertFalse(river1.isConnectedTo(d));
        }
    }

    /** MoveTest#testSimpleMove: (5,7) is the N neighbour of (5,8). */
    public void testSimpleMove() {
        Game game = ServerTestHelper.startServerGame(getTestMap(plains));
        Map map = game.getMap();
        assertEquals(Topology.SQUARE, map.getTopology());

        FreeColClient client = null;
        try {
            client = ClientTestHelper
                .startClient(ServerTestHelper.getServer(), spec());
            assertEquals(Topology.SQUARE, Topology.current());

            Player dutch = game.getPlayerByNationId("model.nation.dutch");
            Tile plain1 = map.getTile(5, 8);
            plain1.setExplored(dutch, true);
            Tile plain2 = map.getTile(5, 7);
            plain2.setExplored(dutch, true);
            Unit hardyPioneer = new ServerUnit(game, plain1, dutch,
                                               pioneerType);

            client.getPreGameController().startGameHandler();
            assertEquals(plain2, plain1.getNeighbourOrNull(Direction.N));
            client.getInGameController().moveDirection(hardyPioneer,
                                                       Direction.N, false);
        } finally {
            if (client != null) ClientTestHelper.stopClient(client);
        }
    }
}
