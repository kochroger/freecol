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

package net.sf.freecol.client.gui.classic;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import javax.xml.stream.XMLStreamException;

import net.sf.freecol.FreeCol;
import net.sf.freecol.common.io.FreeColRules;
import net.sf.freecol.common.io.FreeColXMLReader;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Nation;
import net.sf.freecol.common.model.NationOptions;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.Topology;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.option.MapGeneratorOptions;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.server.generator.SimpleMapGenerator;
import net.sf.freecol.server.model.ServerGame;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * The true-terrain oracle of the fog ring (M1c design 10 §5, item W6d;
 * Critic 13).  The client games are real client views: the server game
 * written for the Dutch player, as the login sends it, and read back, so
 * unexplored tiles have no type and the client has its own copy of the
 * specification.
 */
public class ClassicTerrainOracleTest extends FreeColTestCase {

    private static final String DUTCH = "model.nation.dutch";


    // Helpers, shared with ClassicTerrainGoldenTest

    /**
     * The client's view of a server game: the game as the server sends it
     * to the player at login ({@code WriteScope.toClient}), read back the
     * way the client reads it ({@code LoginMessage}).
     *
     * @param server The server game.
     * @param player The client's player.
     * @return The client game.
     */
    static Game clientView(Game server, Player player) {
        try {
            final String xml = server.serialize(player);
            try (FreeColXMLReader xr = new FreeColXMLReader(new StringReader(xml))) {
                xr.nextTag();
                return new Game(null, xr);
            }
        } catch (XMLStreamException e) {
            throw new AssertionError("client view: " + e, e);
        }
    }

    /**
     * An oracle over a client view and its server game.
     *
     * @param client The client game.
     * @param singlePlayer The single-player flag.
     * @param server The server game, or null.
     * @return The oracle.
     */
    static ClassicTerrainOracle oracle(Game client, boolean singlePlayer, Game server) {
        return new ClassicTerrainOracle(() -> client, () -> singlePlayer, () -> server);
    }

    /**
     * A server game (freecol rules) whose map is drawn as rows of letters:
     * {@code o} ocean, {@code h} high seas, {@code p} plains, {@code g}
     * grassland, {@code d} desert, {@code m} mountains.
     *
     * @param rows The map, row 0 first.
     * @return The game, with its map; nothing explored yet.
     */
    private static Game drawnGame(String... rows) {
        final Game game = getStandardGame();
        final Specification spec = game.getSpecification();
        final MapBuilder b = new MapBuilder(game)
            .setDimensions(rows[0].length(), rows.length)
            .setBaseTileType(spec.getTileType("model.tile.ocean"));
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < rows[y].length(); x++) {
                b.setTileType(x, y, spec.getTileType(typeOf(rows[y].charAt(x))));
            }
        }
        game.changeMap(b.build());
        return game;
    }

    private static String typeOf(char c) {
        switch (c) {
        case 'o': return "model.tile.ocean";
        case 'h': return "model.tile.highSeas";
        case 'p': return "model.tile.plains";
        case 'g': return "model.tile.grassland";
        case 'd': return "model.tile.desert";
        case 'm': return "model.tile.mountains";
        default: throw new IllegalArgumentException("map letter " + c);
        }
    }

    /**
     * Explore tiles for the Dutch player on the server.
     *
     * @param game The server game.
     * @param xy Pairs of coordinates.
     * @return The Dutch player.
     */
    private static Player explore(Game game, int... xy) {
        final ServerPlayer dutch = (ServerPlayer)game.getPlayerByNationId(DUTCH);
        final List<Tile> tiles = new ArrayList<>();
        for (int i = 0; i < xy.length; i += 2) {
            tiles.add(game.getMap().getTile(xy[i], xy[i + 1]));
        }
        dutch.exploreTiles(tiles);
        return dutch;
    }

    private static String id(TileType t) {
        return (t == null) ? null : t.getId();
    }

    /** The map of the small tests: land in the west, high seas in the east. */
    private static final String[] COAST = {
        "ppoooooh",
        "pgoooooh",
        "ddoooooh",
        "pmoooooh",
        "ppoooooh",
        "ppoooooh",
    };


    // Tests

    /**
     * The client view itself: explored tiles have their type, unexplored
     * ones none, and the client reads its own specification.
     */
    public void testClientViewHasNoTypeInTheFog() {
        final Game server = drawnGame(COAST);
        final Player dutch = explore(server, 3, 2);
        final Game client = clientView(server, dutch);
        assertEquals(server.getUUID(), client.getUUID());
        assertNotSame(server.getSpecification(), client.getSpecification());
        assertTrue(client.getMap().getTile(3, 2).isExplored());
        assertFalse(client.getMap().getTile(2, 2).isExplored());
        assertNull(client.getMap().getTile(2, 2).getType());
        assertNotNull(server.getMap().getTile(2, 2).getType());
    }

    /**
     * Single player: an unexplored tile of the ring gets the server's true
     * type, as the client specification's object; an explored tile gets
     * the client's type, even after the server's has changed.
     */
    public void testSinglePlayerGivesTheServerTypeInTheRing() {
        final Game server = drawnGame(COAST);
        final Player dutch = explore(server, 2, 2, 3, 2);
        final Game client = clientView(server, dutch);
        final ClassicTerrainOracle o = oracle(client, true, server);
        final Specification cs = client.getSpecification();
        assertTrue(o.knowsTruth());

        // (1,2) is dark desert next to the explored (2,2): its true type.
        final TileType desert = o.trueType(1, 2);
        assertEquals("model.tile.desert", id(desert));
        assertSame(cs.getTileType("model.tile.desert"), desert);
        assertNotSame(server.getSpecification().getTileType("model.tile.desert"), desert);
        // Diagonal neighbours belong to the ring too (the coast bits).
        assertEquals("model.tile.grassland", id(o.trueType(1, 1)));
        assertEquals("model.tile.mountains", id(o.trueType(1, 3)));
        assertEquals("model.tile.ocean", id(o.trueType(4, 3)));

        // Explored: the client's type, whatever the server holds now.
        final TileType known = client.getMap().getTile(2, 2).getType();
        assertSame(known, o.trueType(2, 2));
        server.getMap().getTile(2, 2).setType(server.getSpecification()
            .getTileType("model.tile.plains"));
        assertSame(known, o.trueType(2, 2));
        assertEquals("model.tile.ocean", id(o.trueType(2, 2)));

        // A ring tile follows the server (AI terraforming).
        server.getMap().getTile(1, 2).setType(server.getSpecification()
            .getTileType("model.tile.plains"));
        assertSame(cs.getTileType("model.tile.plains"), o.trueType(1, 2));
        assertEquals(0, o.refusals());
    }

    /**
     * Least knowledge: beyond the ring the oracle answers null even in
     * single player, and counts the question; the ring is exactly the
     * unexplored tiles within Chebyshev distance 1 of an explored tile.
     */
    public void testNothingBeyondTheRing() {
        final Game server = drawnGame(COAST);
        final Player dutch = explore(server, 4, 2);
        final Game client = clientView(server, dutch);
        final Map map = client.getMap();
        final ClassicTerrainOracle o = oracle(client, true, server);
        int ring = 0;
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                final int d = Math.max(Math.abs(x - 4), Math.abs(y - 2));
                assertEquals(x + "," + y, d == 1, ClassicTerrainOracle.inRing(map, x, y));
                if (d == 1) ring++;
                if (d >= 2) assertNull(x + "," + y, o.trueType(x, y));
            }
        }
        assertEquals(8, ring);
        assertEquals(map.getWidth() * map.getHeight() - 9, o.refusals());
        // The dark land two columns west is never revealed by the oracle.
        assertNull(o.trueType(2, 2));
        assertNull(o.trueType(1, 2));
        assertEquals(map.getWidth() * map.getHeight() - 9 + 2, o.refusals());
        // Off the map: nothing, and no refusal.
        assertNull(o.trueType(-1, 2));
        assertNull(o.trueType(map.getWidth(), 0));
        assertEquals(map.getWidth() * map.getHeight() - 7, o.refusals());
    }

    /**
     * Unknown (fallback F-W6d-MP) without a single-player server: a
     * multiplayer client, also the host whose client has a server too
     * (Critic 13), and a client without a server.  Explored tiles still
     * give the client's type.
     */
    public void testUnknownWithoutASinglePlayerServer() {
        final Game server = drawnGame(COAST);
        final Player dutch = explore(server, 2, 2);
        final Game client = clientView(server, dutch);
        final TileType known = client.getMap().getTile(2, 2).getType();
        for (ClassicTerrainOracle o : new ClassicTerrainOracle[] {
                oracle(client, false, server), // multiplayer host
                oracle(client, false, null),   // multiplayer client
                oracle(client, true, null) }) { // no server
            assertFalse(o.knowsTruth());
            assertNull(o.trueType(1, 2));
            assertNull(o.trueType(3, 3));
            assertSame(known, o.trueType(2, 2));
            assertEquals(0, o.refusals());
            assertTrue(o.census(), o.census().startsWith("unknown ring=8 known=0 "));
        }
    }

    /**
     * Loading a game switches maps: the games are looked up on every
     * call; while the server's game is not the client's (another UUID)
     * the ring is unknown, never the other game's terrain.
     */
    public void testAMapSwapIsFollowed() {
        final Game serverA = drawnGame(COAST);
        final Game clientA = clientView(serverA, explore(serverA, 2, 2));
        final Game serverB = drawnGame(
            "oooooooo",
            "oooooooo",
            "ogomoooo",
            "oooooooo",
            "oooooooo",
            "oooooooo");
        final Game clientB = clientView(serverB, explore(serverB, 2, 2));
        final AtomicReference<Game> c = new AtomicReference<>(clientA);
        final AtomicReference<Game> s = new AtomicReference<>(serverA);
        final ClassicTerrainOracle o = new ClassicTerrainOracle(c::get, () -> true, s::get);
        assertEquals("model.tile.desert", id(o.trueType(1, 2)));
        // The server has loaded the new game, the client not yet.
        s.set(serverB);
        assertFalse(o.knowsTruth());
        assertNull(o.trueType(1, 2));
        c.set(clientB);
        assertTrue(o.knowsTruth());
        assertEquals("model.tile.grassland", id(o.trueType(1, 2)));
        assertEquals("model.tile.mountains", id(o.trueType(3, 2)));
        assertSame(clientB.getSpecification().getTileType("model.tile.mountains"),
                   o.trueType(3, 2));
        assertEquals("model.tile.ocean", id(o.trueType(2, 2)));
    }

    /**
     * Robustness: no client game, a failing supplier and a disposed
     * oracle all give null and never throw.
     */
    public void testNullSafe() {
        final Game server = drawnGame(COAST);
        final Game client = clientView(server, explore(server, 2, 2));
        final ClassicTerrainOracle none = oracle(null, true, server);
        assertNull(none.trueType(1, 2));
        assertFalse(none.knowsTruth());
        assertEquals("none", none.census());
        final ClassicTerrainOracle noClient = ClassicTerrainOracle.of(null);
        assertNull(noClient.trueType(1, 2));
        assertEquals("none", noClient.census());

        final ClassicTerrainOracle failing = new ClassicTerrainOracle(
            () -> client, () -> true,
            () -> { throw new IllegalStateException("server gone"); });
        assertNull(failing.trueType(1, 2));
        assertSame(client.getMap().getTile(2, 2).getType(), failing.trueType(2, 2));
        assertFalse(failing.knowsTruth());

        final ClassicTerrainOracle o = oracle(client, true, server);
        assertNotNull(o.trueType(1, 2));
        o.dispose();
        assertNull(o.trueType(1, 2));
        assertNull(o.trueType(2, 2));
        assertFalse(o.knowsTruth());
        assertEquals("none", o.census());
    }

    /** A GUI without a game view has no oracle (it comes with the map viewer). */
    public void testNoOracleWithoutAGameView() {
        assertNull(new ClassicGUI(null).terrainOracle());
    }

    /**
     * The census line of the recorder: mode, ring size, known tiles,
     * refusals and the ring's types.
     */
    public void testCensus() {
        final Game server = drawnGame(COAST);
        final Game client = clientView(server, explore(server, 2, 2));
        final ClassicTerrainOracle o = oracle(client, true, server);
        assertEquals("server ring=8 known=8 refused=0 desert=1 grassland=1"
            + " mountains=1 ocean=5", o.census());
        assertNull(o.trueType(5, 5));
        assertEquals("server ring=8 known=8 refused=1 desert=1 grassland=1"
            + " mountains=1 ocean=5", o.census());
    }

    /**
     * A real new game on the square grid with the classic rules: the
     * Dutch start ship's 3x3 is explored, the ring around it gets the
     * server's types (the never-drawn column x = 57 is high seas, which
     * the fog-ring blend of LF #341 needs), and every other unexplored
     * tile stays unknown.
     */
    public void testNewGameStartRing() {
        final Topology saved = Topology.current();
        try {
            Topology.setCurrent(Topology.SQUARE);
            final Specification spec = FreeCol.loadSpecification(
                FreeColRules.getFreeColRulesFile("classic"), null,
                "model.difficulty.medium");
            spec.setFile(MapGeneratorOptions.IMPORT_FILE, null);
            MapGeneratorOptions.applyTopologyDefaults(spec.getMapGeneratorOptions());
            final Game server = new ServerGame(spec);
            server.setNationOptions(new NationOptions(spec));
            for (Nation n : spec.getNations()) {
                if (n.isUnknownEnemy()) continue;
                final Player p = new ServerPlayer(server, false, n);
                final boolean ai = !n.getType().isEuropean() || n.getType().isREF();
                p.setAI(ai);
                if (ai || server.canAddNewPlayer()) server.addPlayer(p);
            }
            new SimpleMapGenerator(new Random(1))
                .generateMap(server, null, true, new LogBuilder(-1));
            final Player dutch = server.getPlayerByNationId(DUTCH);
            Tile start = null;
            for (Unit u : dutch.getUnitSet()) {
                if (u.isNaval() && u.hasTile()) start = u.getTile();
            }
            assertNotNull("Dutch start ship", start);
            final Game client = clientView(server, dutch);
            final Map map = client.getMap();
            final ClassicTerrainOracle o = oracle(client, true, server);
            final int sx = start.getX(), sy = start.getY();
            int ring = 0, explored = 0;
            final Set<String> ringTypes = new HashSet<>();
            for (int y = 0; y < map.getHeight(); y++) {
                for (int x = 0; x < map.getWidth(); x++) {
                    final int d = Math.max(Math.abs(x - sx), Math.abs(y - sy));
                    final String at = x + "," + y + " start " + sx + "," + sy;
                    final TileType t = o.trueType(x, y);
                    if (map.getTile(x, y).isExplored()) {
                        explored++;
                        assertTrue(at, d <= 1);
                        assertSame(at, map.getTile(x, y).getType(), t);
                    } else if (d == 2) {
                        ring++;
                        assertEquals(at, server.getMap().getTile(x, y).getType().getId(),
                                     id(t));
                        ringTypes.add(id(t));
                        if (x == map.getWidth() - 1) {
                            assertEquals(at, "model.tile.highSeas", id(t));
                        }
                    } else {
                        assertNull(at, t);
                    }
                }
            }
            assertEquals(9, explored);
            // N16: the start is at x = W - 2, so the ring is cut by the edge.
            assertEquals(map.getWidth() - 2, sx);
            assertEquals(11, ring);
            assertTrue(o.census(), o.census().startsWith("server ring=11 known=11 "));
            assertEquals(map.getWidth() * map.getHeight() - 9 - 11, o.refusals());
            assertTrue(ringTypes.toString(), ringTypes.contains("model.tile.highSeas"));
        } finally {
            Topology.setCurrent(saved);
        }
    }
}
