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

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import net.sf.freecol.common.io.FreeColXMLReader;
import net.sf.freecol.common.model.Constants.IntegrityType;
import net.sf.freecol.common.model.Unit.MoveType;
import net.sf.freecol.common.model.pathfinding.CostDeciders;
import net.sf.freecol.common.model.pathfinding.GoalDeciders;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * The outer ring of a Classic UI map ({@link Map#hasOuterRing}): the
 * columns 0 and W-1 and the rows 0 and H-1, which the Classic UI never
 * draws.  No unit may move onto it, start on it or arrive on it from
 * Europe, and the columns just inside it lead to Europe instead.
 * Every test runs under the square topology, whatever is in use.
 */
public class OuterRingTest extends FreeColTestCase {

    private static final TileType highSeas
        = spec().getTileType("model.tile.highSeas");
    private static final TileType ocean
        = spec().getTileType("model.tile.ocean");
    private static final TileType plains
        = spec().getTileType("model.tile.plains");

    private static final UnitType caravelType
        = spec().getUnitType("model.unit.caravel");
    private static final UnitType colonistType
        = spec().getUnitType("model.unit.freeColonist");

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
        Topology.setCurrent(this.savedTopology);
        super.tearDown();
    }

    /**
     * A 20x15 square map in play: land in columns 0-4, ocean, and high
     * seas in the columns from {@code firstHighSeas} to the east edge.
     *
     * @param game The {@code Game} to put the map in.
     * @param firstHighSeas The first high seas column.
     * @param ring Whether the map has the outer ring.
     * @return The new {@code Map}.
     */
    private static Map makeMap(Game game, int firstHighSeas, boolean ring) {
        MapBuilder builder = new MapBuilder(game);
        builder.setDimensions(20, 15).setBaseTileType(ocean)
            .setExploredByAll(true);
        for (int y = 0; y < 15; y++) {
            for (int x = 0; x < 5; x++) builder.setTileType(x, y, plains);
            for (int x = firstHighSeas; x < 20; x++) {
                builder.setTileType(x, y, highSeas);
            }
        }
        Map map = builder.build();
        game.changeMap(map);
        map.setOuterRing(ring);
        map.resetHighSeasCount();
        return map;
    }


    /** The ring is the outermost tile on every side, and only with the flag. */
    public void testRingTiles() {
        Game game = getStandardGame();
        Map map = makeMap(game, 15, false);
        assertFalse(map.hasOuterRing());
        for (Tile t : map.getTileList(t -> true)) {
            assertFalse(t.isOuterRing());
        }

        map.setOuterRing(true);
        assertTrue(map.hasOuterRing());
        int ring = 0;
        for (Tile t : map.getTileList(t -> true)) {
            final boolean edge = t.getX() == 0 || t.getY() == 0
                || t.getX() == 19 || t.getY() == 14;
            assertEquals(t.toString(), edge, t.isOuterRing());
            assertEquals(t.toString(), edge, map.isOuterRing(t));
            if (edge) ring++;
        }
        assertEquals(2 * 20 + 2 * 13, ring);
        assertFalse(map.isOuterRing((Tile)null));
        assertEquals(map.getTile(1, 1), map.getInnerTile(map.getTile(0, 0)));
        assertEquals(map.getTile(18, 7), map.getInnerTile(map.getTile(19, 7)));
        assertEquals(map.getTile(5, 13), map.getInnerTile(map.getTile(5, 14)));
        assertEquals(map.getTile(5, 5), map.getInnerTile(map.getTile(5, 5)));
    }

    /**
     * No unit may move onto the ring, from any side: the move is
     * illegal, as a move off the map is.  Moves off the ring and moves
     * along the column inside it stay legal.  Without the ring the
     * same moves are legal.
     */
    public void testNoMoveOntoTheRing() {
        Game game = getStandardGame();
        Map map = makeMap(game, 15, true);
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final Unit ship = new ServerUnit(game, map.getTile(18, 7), dutch,
                                         caravelType);
        final Unit colonist = new ServerUnit(game, map.getTile(1, 7), dutch,
                                             colonistType);

        for (Direction d : new Direction[] { Direction.E, Direction.NE,
                                             Direction.SE }) {
            assertEquals(d.toString(), MoveType.MOVE_ILLEGAL,
                         ship.getMoveType(d));
            assertEquals(d.toString(), MoveType.MOVE_ILLEGAL,
                         ship.getSimpleMoveType(d));
        }
        assertEquals(MoveType.MOVE_HIGH_SEAS, ship.getMoveType(Direction.N));
        assertEquals(MoveType.MOVE_HIGH_SEAS, ship.getMoveType(Direction.W));
        for (Direction d : new Direction[] { Direction.W, Direction.NW,
                                             Direction.SW }) {
            assertEquals(d.toString(), MoveType.MOVE_ILLEGAL,
                         colonist.getMoveType(d));
        }
        assertEquals(MoveType.MOVE, colonist.getMoveType(Direction.E));

        // The north and south rows.
        ship.setLocation(map.getTile(10, 1));
        assertEquals(MoveType.MOVE_ILLEGAL, ship.getMoveType(Direction.N));
        assertEquals(MoveType.MOVE, ship.getMoveType(Direction.S));
        ship.setLocation(map.getTile(10, 13));
        assertEquals(MoveType.MOVE_ILLEGAL, ship.getMoveType(Direction.S));
        assertEquals(MoveType.MOVE_ILLEGAL, ship.getMoveType(Direction.SW));

        // A unit left on the ring (an old save) can move off it, but not
        // along it.
        ship.setLocation(map.getTile(19, 7));
        assertEquals(MoveType.MOVE_HIGH_SEAS, ship.getMoveType(Direction.W));
        assertEquals(MoveType.MOVE_ILLEGAL, ship.getMoveType(Direction.N));

        // Without the ring the moves are the old ones.
        map.setOuterRing(false);
        ship.setLocation(map.getTile(18, 7));
        assertEquals(MoveType.MOVE_HIGH_SEAS, ship.getMoveType(Direction.E));
        assertEquals(MoveType.MOVE, colonist.getMoveType(Direction.W));
    }

    /**
     * Paths keep off the ring: a ship sails to Europe from a high seas
     * tile inside it, a ship coming from Europe enters inside it, and
     * no path step uses it.
     */
    public void testPathsKeepOffTheRing() {
        Game game = getStandardGame();
        Map map = makeMap(game, 15, true);
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final Europe europe = dutch.getEurope();
        final Unit ship = new ServerUnit(game, map.getTile(8, 1), dutch,
                                         caravelType);

        PathNode path = ship.findPath(ship.getTile(), europe);
        assertNotNull("path to Europe", path);
        assertEquals(europe, path.getLastNode().getLocation());
        assertNoRing(path);
        Tile last = path.getLastNode().previous.getTile();
        assertTrue(last.isDirectlyHighSeasConnected());

        // A ship next to the ring only: its moves onto the ring are no
        // way to Europe.
        ship.setLocation(map.getTile(14, 7));
        assertTrue(ship.canMoveToHighSeas());
        ship.setLocation(map.getTile(10, 7));
        assertFalse(ship.canMoveToHighSeas());

        // From Europe: the best entry tile for a tile near the east
        // edge is inside the ring.
        for (Tile target : new Tile[] { map.getTile(18, 1),
                                        map.getTile(18, 13),
                                        map.getTile(17, 7) }) {
            Tile entry = map.getBestEntryTile(ship, target, null,
                CostDeciders.avoidSettlementsAndBlockingUnits());
            assertNotNull(target.toString(), entry);
            assertFalse(target + " -> " + entry, entry.isOuterRing());
        }
        ship.setLocation(europe);
        path = ship.findPath(europe, map.getTile(10, 7));
        assertNotNull("path from Europe", path);
        assertNoRing(path);
    }

    /**
     * The searches for a high seas tile that move no unit (the entry of
     * the intervention force near a port) skip the ring, although it
     * still has high seas tiles.
     */
    public void testHighSeasSearchSkipsTheRing() {
        Game game = getStandardGame();
        Map map = makeMap(game, 15, true);
        assertEquals(highSeas, map.getTile(15, 0).getType());
        for (Tile start : new Tile[] { map.getTile(14, 1),
                                       map.getTile(14, 13) }) {
            Tile found = map.searchCircle(start,
                GoalDeciders.getSimpleHighSeasGoalDecider(), 3);
            assertNotNull(start.toString(), found);
            assertFalse(start + " -> " + found, found.isOuterRing());
            assertTrue(found.isDirectlyHighSeasConnected());
        }
    }

    /**
     * The columns inside the ring lead to Europe, as FreeCol's map edge
     * columns do: on a map whose only high seas are on the ring, the
     * ocean column inside it becomes the way to Europe, and the ring
     * itself counts as unconnected.
     */
    public void testEdgeColumnsInsideTheRing() {
        Game game = getStandardGame();
        Map map = makeMap(game, 19, true);
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        for (int y = 1; y < 14; y++) {
            final Tile inside = map.getTile(18, y);
            assertTrue(inside.toString(), inside.isDirectlyHighSeasConnected());
            assertEquals(inside.toString(), 0, inside.getHighSeasCount());
            assertEquals(-1, map.getTile(19, y).getHighSeasCount());
            // Land in the west column: never a way to Europe.
            assertFalse(map.getTile(1, y).isDirectlyHighSeasConnected());
        }
        assertTrue("the coast is a port", map.getTile(4, 7).isCoastland());

        final Unit ship = new ServerUnit(game, map.getTile(10, 7), dutch,
                                         caravelType);
        PathNode path = ship.findPath(ship.getTile(), dutch.getEurope());
        assertNotNull("path to Europe", path);
        assertNoRing(path);
        assertEquals(18, path.getLastNode().previous.getTile().getX());
        ship.setLocation(map.getTile(17, 7));
        assertTrue(ship.canMoveToHighSeas());

        // Without the ring: the old rule, the edge column is the way.
        map = makeMap(game, 19, false);
        assertEquals(0, map.getTile(19, 7).getHighSeasCount());
        assertEquals(1, map.getTile(18, 7).getHighSeasCount());
        assertFalse(map.getTile(18, 7).isDirectlyHighSeasConnected());
    }

    /**
     * Start tiles: with the ring, the column inside it on each row that
     * leads to Europe (so the ship starts at the edge of the screen);
     * without it, FreeCol's innermost high seas tile of each row.
     */
    public void testStartingTiles() {
        Game game = getStandardGame();
        Map map = makeMap(game, 15, true);
        List<Tile> east = new ArrayList<>(), west = new ArrayList<>();
        map.collectStartingTiles(east, west);
        assertEquals(13, east.size());
        for (Tile t : east) {
            assertEquals(t.toString(), 18, t.getX());
            assertFalse(t.isOuterRing());
        }
        assertTrue("land in the west", west.isEmpty());

        map = makeMap(game, 15, false);
        map.collectStartingTiles(east, west);
        assertEquals(15, east.size());
        for (Tile t : east) assertEquals(t.toString(), 15, t.getX());
    }

    /**
     * A unit placed near a tile (on return from Europe) never lands on
     * the ring, also when its entry tile is on the ring or taken.
     */
    public void testSafeTileOffTheRing() {
        Game game = getStandardGame();
        Map map = makeMap(game, 15, true);
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final Player french = game.getPlayerByNationId("model.nation.french");

        Tile ringTile = map.getTile(19, 7);
        Tile safe = ringTile.getSafeTile(dutch, null);
        assertNotNull(safe);
        assertFalse(safe.toString(), safe.isOuterRing());
        assertEquals(1, safe.getDistanceTo(ringTile));

        Tile entry = map.getTile(18, 7);
        assertEquals(entry, entry.getSafeTile(dutch, null));
        for (int y = 6; y <= 8; y++) {
            new ServerUnit(game, map.getTile(18, y), french, caravelType);
        }
        for (int i = 0; i < 20; i++) {
            safe = entry.getSafeTile(dutch, new java.util.Random(i));
            assertFalse(safe.toString(), safe.isOuterRing());
            assertNull(safe.getFirstUnit());
        }
    }

    /**
     * The ring is saved with a square map and read back; a square map
     * saved without the attribute (before it existed) has the ring; an
     * isometric map never writes it and never has it.
     */
    public void testXmlKeepsTheRing() throws Exception {
        Game game = getStandardGame();
        for (boolean ring : new boolean[] { true, false }) {
            Map map = makeMap(game, 15, ring);
            final String xml = map.serialize();
            assertTrue(xml, xml.contains(" outerRing=\"" + ring + "\""));
            assertEquals(ring, map.copy(game, Map.class).hasOuterRing());
        }

        Map map = makeMap(game, 15, false);
        final String old = map.serialize().replace(" outerRing=\"false\"", "");
        assertFalse(old.contains("outerRing="));
        try (FreeColXMLReader xr = new FreeColXMLReader(new StringReader(old))) {
            assertTrue(xr.copy(game, Map.class).hasOuterRing());
        }

        Topology.setCurrent(Topology.ISOMETRIC);
        map = makeMap(game, 15, true);
        assertEquals(Topology.ISOMETRIC, map.getTopology());
        final String iso = map.serialize();
        assertFalse(iso.contains("outerRing="));
        try (FreeColXMLReader xr = new FreeColXMLReader(new StringReader(iso))) {
            assertFalse(xr.copy(game, Map.class).hasOuterRing());
        }
    }

    /**
     * Loading a save with a unit or an entry tile on the ring (made
     * before the ring was kept free) moves them inside it; a check
     * without fixing fails.
     */
    public void testIntegrityMovesUnitsOffTheRing() {
        Game game = getStandardGame();
        Map map = makeMap(game, 15, true);
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final Unit ship = new ServerUnit(game, map.getTile(19, 7), dutch,
                                         caravelType);
        final Unit colonist = new ServerUnit(game, ship, dutch, colonistType);
        final Unit pioneer = new ServerUnit(game, map.getTile(2, 0), dutch,
                                            colonistType);
        dutch.setEntryTile(map.getTile(19, 7));
        assertEquals(IntegrityType.INTEGRITY_FAIL,
                     map.checkIntegrity(false, new LogBuilder(0)));
        assertEquals(map.getTile(19, 7), ship.getTile());

        assertEquals(IntegrityType.INTEGRITY_FIXED,
                     map.checkIntegrity(true, new LogBuilder(0)));
        assertFalse(ship.getTile().isOuterRing());
        assertFalse(ship.getTile().isLand());
        assertEquals(1, ship.getTile().getDistanceTo(map.getTile(19, 7)));
        assertEquals(ship, colonist.getLocation());
        assertFalse(pioneer.getTile().isOuterRing());
        assertTrue(pioneer.getTile().isLand());
        assertFalse(dutch.getEntryTile().isOuterRing());
        assertEquals(IntegrityType.INTEGRITY_GOOD,
                     map.checkIntegrity(false, new LogBuilder(0)));

        ship.setLocation(map.getTile(19, 3));
        assertEquals(IntegrityType.INTEGRITY_FAIL,
                     map.checkIntegrity(false, new LogBuilder(0)));
        assertEquals(map.getTile(19, 3), ship.getTile());
        ship.setLocation(map.getTile(18, 3));

        // An entry tile on the water of the ring moves to water, also
        // where the tile straight inside it is land.
        map.getTile(6, 1).setType(plains);
        dutch.setEntryTile(map.getTile(6, 0));
        assertEquals(IntegrityType.INTEGRITY_FIXED,
                     map.checkIntegrity(true, new LogBuilder(0)));
        assertFalse(dutch.getEntryTile().isOuterRing());
        assertFalse(dutch.getEntryTile().isLand());
        assertEquals(1, dutch.getEntryTile().getDistanceTo(map.getTile(6, 0)));
    }

    /**
     * A square save made before the ring has the high seas counts of a
     * map without it: the ring leads to Europe, the column inside it
     * may not.  Loading it makes them again.
     */
    public void testIntegrityRedoesTheHighSeasCounts() {
        Game game = getStandardGame();
        Map map = makeMap(game, 19, false);
        map.setOuterRing(true); // as Map.readAttributes for an old save
        assertEquals(0, map.getTile(19, 7).getHighSeasCount());
        assertFalse(map.getTile(18, 7).isDirectlyHighSeasConnected());
        assertEquals(IntegrityType.INTEGRITY_FAIL,
                     map.checkIntegrity(false, new LogBuilder(0)));

        assertEquals(IntegrityType.INTEGRITY_FIXED,
                     map.checkIntegrity(true, new LogBuilder(0)));
        for (int y = 1; y < 14; y++) {
            assertEquals(-1, map.getTile(19, y).getHighSeasCount());
            assertTrue(map.getTile(18, y).isDirectlyHighSeasConnected());
            assertEquals(0, map.getTile(18, y).getHighSeasCount());
        }
        assertEquals(IntegrityType.INTEGRITY_GOOD,
                     map.checkIntegrity(false, new LogBuilder(0)));
    }

    /** No step of a path is on the ring. */
    private static void assertNoRing(PathNode path) {
        for (PathNode p = path; p != null; p = p.next) {
            if (p.getTile() != null) {
                assertFalse(p.toString(), p.getTile().isOuterRing());
            }
        }
    }
}
