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

import net.sf.freecol.util.test.FreeColTestCase;


/**
 * Consistency checks of the map geometry for both {@link Topology}s:
 * neighbours, distances, the circle iterator and directions must agree
 * on every tile of a map the size of the original game's.
 */
public class TopologyTest extends FreeColTestCase {

    private static final int WIDTH = 58, HEIGHT = 72;

    /** The largest ring radius checked. */
    private static final int MAX_RADIUS = 3;

    /** The topology to restore after each test. */
    private Topology saved;


    @Override
    protected void setUp() throws Exception {
        super.setUp();
        saved = Topology.current();
    }

    @Override
    protected void tearDown() throws Exception {
        Topology.setCurrent(saved);
        super.tearDown();
    }

    /**
     * Make a plain map with the given topology in use.
     *
     * @param topology The {@code Topology} to use.
     * @return The new {@code Map}, attached to a standard game.
     */
    private Map makeMap(Topology topology) {
        Topology.setCurrent(topology);
        Game game = getStandardGame();
        Map map = new MapBuilder(game).setDimensions(WIDTH, HEIGHT).build();
        game.changeMap(map);
        return map;
    }

    /**
     * Is a tile far enough from the map edges for a full ring of the
     * given radius, in either topology?
     */
    private static boolean isInterior(Tile tile, int radius) {
        return tile.getX() >= radius && tile.getX() < WIDTH - radius
            && tile.getY() >= 2 * radius && tile.getY() < HEIGHT - 2 * radius;
    }

    private static <T> List<T> toList(Iterator<T> iterator) {
        List<T> result = new ArrayList<>();
        while (iterator.hasNext()) result.add(iterator.next());
        return result;
    }

    private void checkNeighbours(Topology topology) {
        final Map map = makeMap(topology);
        for (Tile tile : map) {
            if (!isInterior(tile, 1)) continue;
            Set<Tile> neighbours = new HashSet<>();
            for (Direction d : Direction.values()) {
                Tile n = tile.getNeighbourOrNull(d);
                assertNotNull(topology + " " + d + " of " + tile, n);
                assertEquals(topology + " distance " + tile + " to " + n,
                             1, tile.getDistanceTo(n));
                assertEquals(topology + " direction " + tile + " to " + n,
                             d, map.getDirection(tile, n));
                assertEquals(topology + " back from " + n,
                             tile, n.getNeighbourOrNull(d.getReverseDirection()));
                neighbours.add(n);
            }
            assertEquals(topology + " distinct neighbours of " + tile,
                         8, neighbours.size());

            List<Tile> surrounding = toList(tile.getSurroundingTiles(1)
                                                .iterator());
            assertEquals(topology + " surrounding size of " + tile,
                         8, surrounding.size());
            assertEquals(topology + " surrounding of " + tile,
                         neighbours, new HashSet<>(surrounding));
        }
    }

    private void checkRings(Topology topology) {
        final Map map = makeMap(topology);
        for (Tile tile : map) {
            if (!isInterior(tile, MAX_RADIUS)) continue;
            for (int r = 1; r <= MAX_RADIUS; r++) {
                List<Tile> ring = toList(map.getCircleIterator(tile, false, r));
                assertEquals(topology + " ring " + r + " size at " + tile,
                             8 * r, ring.size());
                assertEquals(topology + " ring " + r + " distinct at " + tile,
                             8 * r, new HashSet<>(ring).size());
                for (Tile t : ring) {
                    assertEquals(topology + " ring " + r + " at " + tile
                        + " has " + t, r, tile.getDistanceTo(t));
                }
            }
            // The filled circle returns the innermost ring first.
            List<Tile> filled = toList(map.getCircleIterator(tile, true,
                                                              MAX_RADIUS));
            assertEquals(topology + " filled size at " + tile,
                         (2 * MAX_RADIUS + 1) * (2 * MAX_RADIUS + 1) - 1,
                         filled.size());
            int previous = 1;
            for (Tile t : filled) {
                int d = tile.getDistanceTo(t);
                assertTrue(topology + " filled order at " + tile + ": " + t,
                           d == previous || d == previous + 1);
                previous = d;
            }
            assertEquals(topology + " filled ends at " + tile,
                         MAX_RADIUS, previous);
        }
    }

    public void testIsometricNeighbours() {
        checkNeighbours(Topology.ISOMETRIC);
    }

    public void testSquareNeighbours() {
        checkNeighbours(Topology.SQUARE);
    }

    public void testIsometricRings() {
        checkRings(Topology.ISOMETRIC);
    }

    public void testSquareRings() {
        checkRings(Topology.SQUARE);
    }

    public void testIsometricSteps() {
        final Map map = makeMap(Topology.ISOMETRIC);
        // Same expectations as MapTest.testGetSurroundingTiles.
        final Tile tile = map.getTile(4, 8);
        assertEquals(map.getTile(4, 6), tile.getNeighbourOrNull(Direction.N));
        assertEquals(map.getTile(4, 7), tile.getNeighbourOrNull(Direction.NE));
        assertEquals(map.getTile(5, 8), tile.getNeighbourOrNull(Direction.E));
        assertEquals(map.getTile(4, 9), tile.getNeighbourOrNull(Direction.SE));
        assertEquals(map.getTile(4, 10), tile.getNeighbourOrNull(Direction.S));
        assertEquals(map.getTile(3, 9), tile.getNeighbourOrNull(Direction.SW));
        assertEquals(map.getTile(3, 8), tile.getNeighbourOrNull(Direction.W));
        assertEquals(map.getTile(3, 7), tile.getNeighbourOrNull(Direction.NW));
        assertEquals(Direction.longSides,
                     Topology.ISOMETRIC.edgeDirections());
        assertEquals(Direction.corners,
                     Topology.ISOMETRIC.cornerDirections());
    }

    public void testSquareSteps() {
        final Map map = makeMap(Topology.SQUARE);
        final int x = 20, y = 31; // Odd row, parity must not matter
        for (int dy = 0; dy < 2; dy++) {
            final Tile tile = map.getTile(x, y + dy);
            final int ty = tile.getY();
            assertEquals(map.getTile(x, ty-1), tile.getNeighbourOrNull(Direction.N));
            assertEquals(map.getTile(x+1, ty-1), tile.getNeighbourOrNull(Direction.NE));
            assertEquals(map.getTile(x+1, ty), tile.getNeighbourOrNull(Direction.E));
            assertEquals(map.getTile(x+1, ty+1), tile.getNeighbourOrNull(Direction.SE));
            assertEquals(map.getTile(x, ty+1), tile.getNeighbourOrNull(Direction.S));
            assertEquals(map.getTile(x-1, ty+1), tile.getNeighbourOrNull(Direction.SW));
            assertEquals(map.getTile(x-1, ty), tile.getNeighbourOrNull(Direction.W));
            assertEquals(map.getTile(x-1, ty-1), tile.getNeighbourOrNull(Direction.NW));
        }
        assertEquals(5, map.getDistance(map.getTile(10, 10),
                                        map.getTile(15, 7)));
        assertEquals(List.of(Direction.N, Direction.E, Direction.S, Direction.W),
                     Topology.SQUARE.edgeDirections());
        assertEquals(List.of(Direction.NE, Direction.SE, Direction.SW, Direction.NW),
                     Topology.SQUARE.cornerDirections());
        assertEquals(1, Topology.SQUARE.polarHeight());
        assertEquals(1, Topology.SQUARE.rowFactor());
    }

    public void testSquareRingOrder() {
        final Map map = makeMap(Topology.SQUARE);
        final Tile tile = map.getTile(10, 10);
        // Clockwise from N, as the isometric ring starts at its "top".
        assertEquals(List.of(map.getTile(10, 9), map.getTile(11, 9),
                             map.getTile(11, 10), map.getTile(11, 11),
                             map.getTile(10, 11), map.getTile(9, 11),
                             map.getTile(9, 10), map.getTile(9, 9)),
                     toList(map.getCircleIterator(tile, false, 1)));
        // Ring 2 starts next to the N corner and walks clockwise.
        List<Tile> ring2 = toList(map.getCircleIterator(tile, false, 2));
        assertEquals(map.getTile(9, 8), ring2.get(0));
        assertEquals(map.getTile(10, 8), ring2.get(1));
        assertEquals(map.getTile(12, 8), ring2.get(3));
        assertEquals(map.getTile(8, 8), ring2.get(15));
    }
}
