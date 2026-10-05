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

package net.sf.freecol.server;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.TreeMap;

import net.sf.freecol.common.FreeColException;
import net.sf.freecol.common.io.FreeColSavegameFile;
import net.sf.freecol.common.model.Direction;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileImprovement;
import net.sf.freecol.common.model.Topology;
import net.sf.freecol.common.option.FileOption;
import net.sf.freecol.common.option.MapGeneratorOptions;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.util.test.FreeColTestCase;


public class SaveLoadTest extends FreeColTestCase {

    /** The topology in use before the test. */
    private Topology savedTopology;


    @Override
    public void setUp() throws Exception {
        super.setUp();
        this.savedTopology = Topology.current();
    }

    @Override
    public void tearDown() throws Exception {
        ServerTestHelper.stopServer();
        Topology.setCurrent(this.savedTopology);
        super.tearDown();
    }

    /**
     * Get the river of every tile of a map.
     *
     * @param map The {@code Map} to look at.
     * @return A map from "x,y" to "magnitude:style" for each river tile.
     */
    private static java.util.Map<String, String> rivers(Map map) {
        final java.util.Map<String, String> result = new TreeMap<>();
        for (Tile t : map) {
            TileImprovement river = t.getRiver();
            if (river == null) continue;
            result.put(t.getX() + "," + t.getY(), river.getMagnitude()
                + ":" + river.getStyle().getString());
        }
        return result;
    }

    /**
     * Count the river connections of a map that point at a river that
     * connects back, or at water, under the topology in use.
     *
     * @param map The {@code Map} to look at.
     * @return The number of connections and the number of good ones.
     */
    private static int[] riverConnections(Map map) {
        int all = 0, good = 0;
        for (Tile t : map) {
            TileImprovement river = t.getRiver();
            if (river == null) continue;
            for (Direction d : Topology.current().edgeDirections()) {
                if (!river.isConnectedTo(d)) continue;
                all++;
                Tile n = t.getNeighbourOrNull(d);
                if (n == null) continue;
                TileImprovement other = n.getRiver();
                if ((other != null
                        && other.isConnectedTo(d.getReverseDirection()))
                    || (other == null && !n.isLand())) good++;
            }
        }
        return new int[] { all, good };
    }

    /**
     * Save a new random game generated under one topology, load it in a
     * session where the other one is in use (as after a restart, or in
     * the Classic UI, which starts new games square), and check that
     * the map keeps its topology and every river.
     *
     * @param topology The {@code Topology} to generate under.
     */
    private void checkRoundTrip(Topology topology) throws IOException {
        final Topology other = (topology == Topology.SQUARE)
            ? Topology.ISOMETRIC : Topology.SQUARE;
        final File file = File.createTempFile("topology-", ".fsg");
        try {
            Topology.setCurrent(topology);
            FreeColServer server = ServerTestHelper.startServer(false, true);
            try {
                server.startGame();
            } catch (FreeColException e) {
                fail(e.getMessage());
            }
            Map map = server.getGame().getMap();
            assertEquals(topology, map.getTopology());
            // A few generated maps have a broken river link, which the
            // load would repair; repair it now, so only the topology
            // can make a difference.
            server.getGame().checkIntegrity(true, new LogBuilder(0));
            final java.util.Map<String, String> before = rivers(map);
            assertFalse("rivers generated", before.isEmpty());
            int[] connections = riverConnections(map);
            assertEquals(connections[0], connections[1]);
            server.saveGame(file, null, null);
            ServerTestHelper.stopServer();

            Topology.setCurrent(other);
            // The real load path: read, then check integrity with fixes.
            server = ServerTestHelper.startServer(file, false, true);
            map = server.getGame().getMap();
            assertEquals(topology, map.getTopology());
            assertEquals(topology, Topology.current());
            assertEquals(before, rivers(map));
            assertEquals(connections[0], riverConnections(map)[1]);
        } finally {
            file.delete();
        }
    }

    /** A square game saved and loaded again keeps its rivers. */
    public void testSquareRoundTrip() throws IOException {
        checkRoundTrip(Topology.SQUARE);
    }

    /** An isometric game saved and loaded again keeps its rivers. */
    public void testIsometricRoundTrip() throws IOException {
        checkRoundTrip(Topology.ISOMETRIC);
    }

    /**
     * A map saved before the topology was stored (here a bundled map)
     * is read as isometric, also when the square topology is in use.
     */
    public void testOldSaveIsIsometric() throws Exception {
        final File file = new File(FreeColTestCase.STANDARD_MAPS[0]);
        final String xml;
        try (InputStream in = new FreeColSavegameFile(file)
                 .getSavegameInputStream()) {
            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(xml.contains("<map "));
        assertFalse(xml.contains("topology="));

        Topology.setCurrent(Topology.SQUARE);
        Map map = FreeColServer.readMap(file, spec());
        assertEquals(Topology.ISOMETRIC, map.getTopology());
        assertEquals(Topology.ISOMETRIC, Topology.current());
    }

    public void testDelayedLoading() {
        File file = ServerTestHelper.createRandomSaveGame();
        ServerTestHelper.stopServer();

        FreeColServer server = ServerTestHelper.startServer(false, true);
        try {
            server.loadGame(new FreeColSavegameFile(file));
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
        assertNotNull(server.getGame());
        assertNotNull(server.getGame().getMap());
        file.delete();
        assertFalse(file.exists());
    }

    public void testImmediateLoading() {
        File file = ServerTestHelper.createRandomSaveGame();
        ServerTestHelper.stopServer();
        FreeColServer server = ServerTestHelper.startServer(file, false, true);
        assertNotNull(server.getGame());
        assertNotNull(server.getGame().getMap());
        file.delete();
        assertFalse(file.exists());
    }
    
    public void testImport() {
        File file = ServerTestHelper.createRandomSaveGame();
        ServerTestHelper.stopServer();

        FreeColServer server = ServerTestHelper.startServer(false, true);
        FileOption importOption = server.getSpecification()
            .getMapGeneratorOptions()
            .getOption(MapGeneratorOptions.IMPORT_FILE, FileOption.class);
        importOption.setValue(file);
        try {
            server.startGame();
        } catch (FreeColException e) {
            fail(e.getMessage());
        }
        importOption.setValue(null);

        assertEquals(FreeColServer.ServerState.IN_GAME,
                     server.getServerState());
        assertNotNull(server.getGame());
        assertNotNull(server.getGame().getMap());
        file.delete();
        assertFalse(file.exists());
    }
}
