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

package net.sf.freecol.client.control;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ImageIcon;

import net.sf.freecol.client.ClientTestHelper;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.GUI;
import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Location;
import net.sf.freecol.common.model.LostCityRumour;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Direction;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Region;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.Topology;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.common.networking.Connection;
import net.sf.freecol.common.networking.ServerAPI;
import net.sf.freecol.common.resources.ImageCache;
import net.sf.freecol.server.ServerTestHelper;
import net.sf.freecol.server.model.ServerRegion;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;


public class MoveTest extends FreeColTestCase {

    private static final TileType plains
        = spec().getTileType("model.tile.plains");

    private static final UnitType pioneerType
        = spec().getUnitType("model.unit.hardyPioneer");


    /** The topology in use before the test. */
    private Topology savedTopology;


    /**
     * The test coordinates are isometric ((5,7) is NE of (5,8)), so run
     * under the isometric topology whatever is in use.  The square
     * version is in {@code SquareMapTest}.
     */
    @Override
    public void setUp() throws Exception {
        super.setUp();
        this.savedTopology = Topology.current();
        Topology.setCurrent(Topology.ISOMETRIC);
    }

    @Override
    public void tearDown() throws Exception {
        ServerTestHelper.stopServerGame();
        Topology.setCurrent(this.savedTopology);
        super.tearDown();
    }


    public void testSimpleMove() {
        Game game = ServerTestHelper.startServerGame(getTestMap(plains));
        Map map = game.getMap();

        FreeColClient client = null;
        try {
            client = ClientTestHelper
                    .startClient(ServerTestHelper.getServer(), spec());

            Player dutch = game.getPlayerByNationId("model.nation.dutch");
            Tile plain1 = map.getTile(5, 8);
            plain1.setExplored(dutch, true);
            Tile plain2 = map.getTile(5, 7);
            plain2.setExplored(dutch, true);

            Unit hardyPioneer = new ServerUnit(game, plain1, dutch,
                    pioneerType);

            client.getPreGameController().startGameHandler();
            assertEquals(plain1.getNeighbourOrNull(Direction.NE), plain2);
            client.getInGameController().moveDirection(hardyPioneer,
                    Direction.NE, false);
        } finally {
            if (client != null) {
                ClientTestHelper.stopClient(client);
            }
        }
    }

    /**
     * The goto batch flag (the Classic UI's unit cycle, master plan W5f):
     * on by default, as FreeCol has it, so the standard GUI keeps its goto
     * batch; only the Classic UI switches it off (and on again when its
     * game view goes).  The single-unit goto refuses no unit.
     */
    public void testGotoBatchFlag() {
        final InGameController igc = new InGameController(null);
        assertTrue(igc.isGotoBatch());
        igc.setGotoBatch(false);
        assertFalse(igc.isGotoBatch());
        igc.setGotoBatch(true);
        assertTrue(igc.isGotoBatch());
        assertFalse(igc.moveToDestination(null));
    }

    /**
     * The goto's stop at a region still to discover (BR#2707, G review):
     * on by default, as FreeCol has it, so the standard GUI's player can
     * name the region; the Classic UI switches it off while its game view
     * is up (it answers the naming at once, and the original's goto runs
     * on, clip006 U22).  Only a tile whose region is still to discover
     * stops a goto.
     */
    public void testRegionStopsFlag() {
        final Game game = getStandardGame();
        final Map map = getTestMap(plains);
        game.changeMap(map);
        final Tile t = map.getTile(5, 8);
        t.setRegion(null);
        final InGameController igc = new InGameController(null);
        assertTrue(igc.isRegionStops());
        assertFalse(igc.stopsForRegion(null));
        assertFalse(igc.stopsForRegion(t));              // no region
        final Region land = new ServerRegion(game, Region.RegionType.LAND);
        t.setRegion(land);
        assertTrue(land.getDiscoverable());
        assertTrue(igc.stopsForRegion(t));
        igc.setRegionStops(false);
        assertFalse(igc.isRegionStops());
        assertFalse(igc.stopsForRegion(t));
        igc.setRegionStops(true);
        land.setDiscoverable(false);                     // named: discovered
        assertFalse(igc.stopsForRegion(t));
    }

    private static void set(FreeColClient fcc, String name, Object value)
        throws Exception {
        final Field f = FreeColClient.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(fcc, value);
    }

    /**
     * A client of the test's game for a move onto burial mounds: its GUI
     * answers the rumour question "yes" and the mounds question "no"
     * ("Haltet Euch davon fern!") and notes them; its server API notes
     * the calls and sets a destination it is asked to.  FreeCol's
     * constructor would start a game of its own, so the object is made
     * without it (as ClassicPacificTest's naming client).
     */
    private static FreeColClient moundsClient(Game game, Player me,
                                              List<String> log)
        throws Exception {
        final Class<?> uc = Class.forName("sun.misc.Unsafe");
        final Field theUnsafe = uc.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        final FreeColClient fcc = (FreeColClient)uc
            .getMethod("allocateInstance", Class.class)
            .invoke(theUnsafe.get(null), FreeColClient.class);
        // The question's unit icon: any image.
        final ImageLibrary lib = new ImageLibrary(1f, new ImageCache() {
                @Override
                public BufferedImage getScaledImage(String key, float scale,
                                                    boolean grayscale) {
                    return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
                }
            });
        set(fcc, "gui", new GUI(fcc) {
                @Override
                public boolean modalConfirmDialog(Tile tile, StringTemplate t,
                                                  ImageIcon icon, String okKey,
                                                  String cancelKey,
                                                  boolean defaultOk) {
                    log.add("ask " + t.getId());
                    return !"exploreMoundsRumour.text".equals(t.getId());
                }

                @Override
                public ImageLibrary getFixedImageLibrary() {
                    return lib;
                }
            });
        set(fcc, "serverAPI", new ServerAPI() {
                @Override
                public boolean declineMounds(Unit unit, Direction direction) {
                    log.add("declineMounds " + direction);
                    return true;
                }

                @Override
                public boolean move(Unit unit, Direction direction) {
                    log.add("move " + direction);
                    return true;
                }

                @Override
                public boolean setDestination(Unit unit, Location destination) {
                    log.add("setDestination " + destination);
                    unit.setDestination(destination);
                    return true;
                }

                @Override
                public Connection connect(String name, String host, int port) {
                    return null;
                }

                @Override
                public boolean disconnect() {
                    return true;
                }

                @Override
                public Connection reconnect() {
                    return null;
                }

                @Override
                public Connection getConnection() {
                    return null;
                }
            });
        set(fcc, "inGameController", new InGameController(fcc));
        fcc.setGame(game);
        fcc.setMyPlayer(me);
        return fcc;
    }

    /**
     * Burial mounds declined (M1; Roger, 2026-10-09: "Die Hügel bleiben.
     * Es ist, als hätte man nichts angetastet"): FreeCol's default takes
     * them away ({@code declineMounds}; a goto keeps its destination);
     * with the switch off, as the Classic UI has it while its game view
     * is up, nothing goes to the server: the mounds stay, the colonist
     * keeps its tile and its moves and is not moved.  A goto through the
     * mounds ends there (its destination cleared), or it would ask again
     * at its next step.  Both topologies: the step is east, the same
     * neighbour on both.
     */
    public void testDeclinedMounds() throws Exception {
        for (Topology topology : new Topology[] { Topology.ISOMETRIC,
                                                  Topology.SQUARE }) {
            Topology.setCurrent(topology);
            for (int c = 0; c < 4; c++) {
                final boolean go = c == 0 || c == 3, gotoUnit = c >= 2;
                final String what = topology + " go=" + go + " goto=" + gotoUnit;
                final Game game = getStandardGame();
                final Map map = getTestMap(plains);
                game.changeMap(map);
                final Player dutch = game.getPlayerByNationId("model.nation.dutch");
                final Tile from = map.getTile(5, 8);
                final Tile mounds = from.getNeighbourOrNull(Direction.E);
                assertNotNull(what, mounds);
                for (Tile t : new Tile[] { from, mounds }) t.setExplored(dutch, true);
                mounds.addLostCityRumour(new LostCityRumour(game, mounds,
                    LostCityRumour.RumourType.MOUNDS, "mounds"));
                final Unit unit = new ServerUnit(game, from, dutch,
                    spec().getUnitType("model.unit.freeColonist"));
                final int moves = unit.getMovesLeft();
                assertTrue(what, moves > 0);
                final Tile target = map.getTile(9, 8);
                if (gotoUnit) unit.setDestination(target);
                assertEquals(what, Unit.MoveType.EXPLORE_LOST_CITY_RUMOUR,
                             unit.getMoveType(Direction.E));
                final List<String> log = new ArrayList<>();
                final FreeColClient fcc = moundsClient(game, dutch, log);
                final InGameController igc = fcc.getInGameController();
                assertTrue(igc.isDeclinedMoundsGo());
                igc.setDeclinedMoundsGo(go);
                assertEquals(go, igc.isDeclinedMoundsGo());
                assertFalse(what, igc.moveDirection(unit, Direction.E, true));
                final List<String> expected = new ArrayList<>(List.of(
                    "ask exploreLostCityRumour.text", "ask exploreMoundsRumour.text"));
                if (go) {
                    expected.add("declineMounds E");
                } else if (gotoUnit) {
                    expected.add("setDestination null");
                }
                assertEquals(what, expected, log);
                assertSame(what, (go && gotoUnit) ? target : null,
                           unit.getDestination());
                assertSame(what, from, unit.getTile());
                assertEquals(what, moves, unit.getMovesLeft());
                assertTrue(what, mounds.hasLostCityRumour());
            }
        }
    }
}
