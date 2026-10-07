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

import net.sf.freecol.client.ClientTestHelper;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Direction;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.Topology;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.server.ServerTestHelper;
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
}
