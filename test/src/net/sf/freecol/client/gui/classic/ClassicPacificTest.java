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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.function.BiPredicate;

import net.sf.freecol.FreeCol;
import net.sf.freecol.common.io.FreeColRules;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Nation;
import net.sf.freecol.common.model.NationOptions;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Region;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.Topology;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.networking.ChangeSet;
import net.sf.freecol.common.networking.NewRegionNameMessage;
import net.sf.freecol.common.option.MapGeneratorOptions;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.server.generator.SimpleMapGenerator;
import net.sf.freecol.server.model.ServerGame;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.server.model.ServerRegion;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * The original's Pacific (I1, I-prep pacific.md): its shape on the Classic
 * UI's square maps (F1, a row scan, golden against bit 0x20 of the MASK
 * layer of the original's saves) and its woodcut on sight (F2).  Each test
 * sets the topology it needs and restores it, so the results are the same
 * under -Dfreecol.topology=square and isometric.
 */
public class ClassicPacificTest extends FreeColTestCase {

    private static final int W = 58, H = 72;

    /** The original's install folder (as for {@code ant classic-assets}). */
    private static final String INSTALL = System.getProperty("col.install",
        "C:/Program Files (x86)/Steam/steamapps/common/Sid Meier's Colonization/MPS/COLONIZE");


    /** A W x H map of ocean with land where asked, its fixed regions made. */
    private static Map map(Game game, Topology topology, int w, int h,
                           BiPredicate<Integer, Integer> land) {
        final Topology saved = Topology.current();
        try {
            Topology.setCurrent(topology);
            final TileType ocean = spec().getTileType("model.tile.ocean");
            final TileType plains = spec().getTileType("model.tile.plains");
            final Map map = new Map(game, w, h);
            game.changeMap(map);
            map.populateTiles((x, y) -> new Tile(game,
                    land.test(x, y) ? plains : ocean, x, y));
            ServerRegion.requireFixedRegions(map, new LogBuilder(-1));
            return map;
        } finally {
            Topology.setCurrent(saved);
        }
    }

    private static boolean pacific(Tile t) {
        return t.getRegion() != null && t.getRegion().isPacific();
    }

    private static String key(Tile t) {
        return (t.getRegion() == null) ? null : t.getRegion().getKey();
    }


    // F1: the shape

    /**
     * The row scan (pacific.md §2, F1 tests): an island at (5,10) ends row
     * 10's Pacific at x 4, the water behind it is Atlantic; a row without
     * land is Pacific at x 1..28 only (not x 0, not x 29), north of the
     * middle row in the North Pacific, else the South; a pond at the west
     * edge stays a lake (no region, for createLakeRegions) and ends its
     * row; every other water tile has an ocean region, no land tile has
     * one.
     */
    public void testRowScan() {
        final Game game = getStandardGame();
        final Map map = map(game, Topology.SQUARE, W, H, (x, y) ->
            (x == 5 && y == 10)
            // a ring of land around the pond (1,30)
            || (x <= 2 && y >= 29 && y <= 31 && !(x == 1 && y == 30)));
        for (int x = 1; x <= 4; x++) {
            assertEquals("(" + x + ",10)", "model.region.northPacific",
                         key(map.getTile(x, 10)));
        }
        assertEquals("model.region.northAtlantic", key(map.getTile(6, 10)));
        assertEquals("model.region.northAtlantic", key(map.getTile(28, 10)));
        for (int x = 1; x <= 28; x++) {
            assertEquals("(" + x + ",40)", "model.region.southPacific",
                         key(map.getTile(x, 40)));
            assertEquals("(" + x + ",35)", "model.region.northPacific",
                         key(map.getTile(x, 35)));
            assertEquals("(" + x + ",36)", "model.region.southPacific",
                         key(map.getTile(x, 36)));
        }
        assertEquals("model.region.southAtlantic", key(map.getTile(0, 40)));
        assertEquals("model.region.southAtlantic", key(map.getTile(29, 40)));
        assertEquals("model.region.northAtlantic", key(map.getTile(0, 10)));
        assertNull("the pond", map.getTile(1, 30).getRegion());
        assertEquals("behind the pond", "model.region.northAtlantic",
                     key(map.getTile(3, 30)));
        int pacific = 0;
        for (Tile t : map.getTileList(t0 -> true)) {
            if (t.isLand()) {
                assertFalse("land " + t, pacific(t));
                continue;
            }
            if (t.getX() == 1 && t.getY() == 30) continue;
            assertNotNull("water " + t, t.getRegion());
            if (pacific(t)) {
                pacific++;
                assertTrue(t.toString(), t.getX() >= 1 && t.getX() <= 28);
            }
        }
        // 72 rows of 28 minus row 10 (24 cut) and rows 29-31 (28 each).
        assertEquals(72 * 28 - 24 - 3 * 28, pacific);
        final Region p = map.getRegionByKey("model.region.pacific");
        assertTrue(p.getDiscoverable());
        assertEquals(ServerRegion.PACIFIC_SCORE_VALUE, p.getScoreValue());
    }

    /**
     * FreeCol's isometric maps keep FreeCol's flood fill: there the west
     * column is Pacific and the water behind the island too.
     */
    public void testIsometricKeepsFreeColsPacific() {
        final Game game = getStandardGame();
        final Map map = map(game, Topology.ISOMETRIC, 40, 100,
                            (x, y) -> x == 5 && y == 10);
        assertFalse(ServerRegion.hasClassicPacific(map));
        assertTrue(pacific(map.getTile(0, 10)));
        assertTrue(pacific(map.getTile(6, 10)));
    }

    /** The SAV's 16-bit little-endian word at {@code o}. */
    private static int u16(byte[] s, int o) {
        return (s[o] & 0xFF) | ((s[o + 1] & 0xFF) << 8);
    }

    /**
     * Where a SAV's TERRAIN layer starts (MASK follows it): the records in
     * front of the map are counted in the header, colonies (0x2E, 202
     * bytes each, from byte 390), units (0x2C, 28 bytes) and dwellings
     * (0x2A, 18 bytes), the rest is fixed (I fixer: every on-map unit
     * record has MASK bit 0x01 at this offset in saves 00, 01, 08 and 09,
     * which a search for the row rule cannot tell from offsets whole map
     * rows away).
     *
     * @param s The save.
     * @return The offset, or -1 if the header is not a 58x72 map whose
     *     four layers fit in the file.
     */
    static int terrainOffset(byte[] s) {
        if (s.length < 0x30 || u16(s, 0x0C) != W || u16(s, 0x0E) != H) return -1;
        final int t = 3005 + 18 * u16(s, 0x2A) + 28 * u16(s, 0x2C) + 202 * u16(s, 0x2E);
        return (t + 4 * W * H <= s.length) ? t : -1;
    }

    /**
     * Golden: on the original's own terrain (saves 00, 01, 08 and 09, three
     * maps), the Pacific is exactly the tiles with bit 0x20 of the MASK
     * layer (pacific.md §0 and §1.2).  The layers' offset comes from the
     * header's record counts ({@link #terrainOffset}) and is checked by the
     * units' tiles, so a save Roger plays on again still checks; a file
     * whose header does not fit is skipped with a note, as a missing one.
     */
    public void testGoldenAgainstTheOriginalSaves() throws IOException {
        final String[] saves = { "COLONY09.SAV", "COLONY08.SAV",
                                 "COLONY01.SAV", "COLONY00.SAV" };
        for (int i = 0; i < saves.length; i++) {
            final Path f = Path.of(INSTALL, saves[i]);
            if (!Files.isRegularFile(f)) {
                System.err.println("ClassicPacificTest: " + f
                    + " missing, golden check skipped (-Dcol.install)");
                continue;
            }
            final byte[] s = Files.readAllBytes(f);
            final int t0 = terrainOffset(s), m0 = t0 + W * H;
            if (t0 < 0) {
                System.err.println("ClassicPacificTest: " + f
                    + " has no 58x72 map where its header says, golden check skipped");
                continue;
            }
            // The offset is the map's: every unit record on the map (x,y
            // its first two bytes) has the MASK's unit bit 0x01.
            final int units = u16(s, 0x2C), u0 = 390 + 202 * u16(s, 0x2E);
            int onMap = 0, unitBit = 0;
            for (int k = 0; k < units; k++) {
                final int x = s[u0 + 28 * k] & 0xFF, y = s[u0 + 28 * k + 1] & 0xFF;
                if (x < 1 || x > W - 2 || y < 1 || y > H - 2) continue;
                onMap++;
                if ((s[m0 + y * W + x] & 0x01) != 0) unitBit++;
            }
            assertTrue(saves[i] + " units on the map", onMap > 0);
            assertEquals(saves[i] + " unit tiles with MASK bit 0x01 at " + t0,
                         onMap, unitBit);
            final Game game = getStandardGame();
            final Map map = map(game, Topology.SQUARE, W, H, (x, y) -> {
                    final int v = s[t0 + y * W + x] & 31;
                    return v != 0x19 && v != 0x1A;
                });
            int n = 0, wrong = 0;
            final StringBuilder sb = new StringBuilder();
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    final boolean want = (s[m0 + y * W + x] & 0x20) != 0;
                    final boolean got = pacific(map.getTile(x, y));
                    if (want) n++;
                    if (want != got) {
                        wrong++;
                        if (sb.length() < 200) {
                            sb.append(" (").append(x).append(',').append(y)
                                .append(want ? " original)" : " ours)");
                        }
                    }
                }
            }
            assertTrue(saves[i] + " bit 0x20 on " + n + " tiles", n > 500);   // 696..983 so far
            assertEquals(saves[i] + " mismatches:" + sb, 0, wrong);
        }
    }

    /**
     * A new square game from the map generator: its Pacific is the row
     * scan of its own terrain (stopping at land or a lake), every water
     * tile has a region (lakes their lake regions) and no Pacific tile lies
     * east of x = width/2 - 1.
     */
    public void testGeneratedSquareMap() {
        final Topology saved = Topology.current();
        try {
            Topology.setCurrent(Topology.SQUARE);
            for (int seed = 1; seed <= 3; seed++) {
                final Map map = generated("classic", seed);
                assertTrue(ServerRegion.hasClassicPacific(map));
                final int w = map.getWidth();
                int pacific = 0;
                for (int y = 0; y < map.getHeight(); y++) {
                    boolean on = true;
                    for (int x = 0; x < w; x++) {
                        final Tile t = map.getTile(x, y);
                        if (!t.isLand()) {
                            assertNotNull("seed " + seed + " " + t, t.getRegion());
                        }
                        if (x >= 1 && on && (t.isLand() || x >= w / 2
                                || t.getRegion().getType() == Region.RegionType.LAKE)) {
                            on = false;
                        }
                        final boolean want = x >= 1 && on;
                        assertEquals("seed " + seed + " " + t, want, pacific(t));
                        if (want) pacific++;
                    }
                }
                assertTrue("seed " + seed + ": " + pacific, pacific > 200);
            }
        } finally {
            Topology.setCurrent(saved);
        }
    }

    /** A map from the map generator with these rules and seed. */
    private static Map generated(String rules, int seed) {
        final Specification spec = FreeCol.loadSpecification(
            FreeColRules.getFreeColRulesFile(rules), null,
            "model.difficulty.medium");
        spec.setFile(MapGeneratorOptions.IMPORT_FILE, null);
        MapGeneratorOptions.applyTopologyDefaults(spec.getMapGeneratorOptions());
        final Game game = new ServerGame(spec);
        game.setNationOptions(new NationOptions(spec));
        for (Nation n : spec.getNations()) {
            if (n.isUnknownEnemy()) continue;
            final Player p = new ServerPlayer(game, false, n);
            final boolean ai = !n.getType().isEuropean() || n.getType().isREF();
            p.setAI(ai);
            if (ai || game.canAddNewPlayer()) game.addPlayer(p);
        }
        new SimpleMapGenerator(new Random(seed))
            .generateMap(game, null, true, new LogBuilder(-1));
        return game.getMap();
    }


    // F2: the trigger

    /**
     * Roger's clip (pacific.md §1.3): row 66's Pacific ends at x 16, row
     * 67's at x 17 (unseen land at (17,66) and (18,67)), row 68 runs to
     * x 28; rows 69 and 70 end at x 19.  A Dutch merchantman starts at
     * (31,67).
     */
    private Map rogersMap(Game game, Topology topology) {
        return map(game, topology, W, H, (x, y) ->
            (x == 17 && y == 66) || (x == 18 && y == 67)
            || (x == 20 && (y == 69 || y == 70)));
    }

    private ServerUnit ship(Game game, Map map) {
        final ServerPlayer dutch = (ServerPlayer)game
            .getPlayerByNationId("model.nation.dutch");
        final ServerUnit ship = new ServerUnit(game, map.getTile(31, 67), dutch,
            spec().getUnitType("model.unit.merchantman"));
        dutch.exploreForUnit(ship);
        dutch.invalidateCanSeeTiles();
        return ship;
    }

    /** Move the ship by one tile; @return the change set. */
    private static ChangeSet move(ServerUnit ship, int x, int y) {
        ship.setMovesLeft(ship.getInitialMovesLeft());
        final ChangeSet cs = new ChangeSet();
        ship.csMove(ship.getGame().getMap().getTile(x, y), new Random(1), cs);
        return cs;
    }

    private static boolean names(ChangeSet cs) {
        return cs.toString().contains("[" + NewRegionNameMessage.TAG);
    }

    /**
     * F2: the step (30,67) to (29,67) brings the Pacific tile (28,68) into
     * the ship's 3x3 for the first time and sends the discovery although
     * neither tile is Pacific; the step before it does not.  A step that
     * brings no new Pacific tile into sight sends nothing more (the desynch
     * work-around of Region.checkDiscover would answer true again), and
     * after the naming a new Pacific tile in sight sends nothing either.
     */
    public void testWoodcutOnSight() {
        final Topology saved = Topology.current();
        try {
            Topology.setCurrent(Topology.SQUARE);
            final Game game = getStandardGame();
            final Map map = rogersMap(game, Topology.SQUARE);
            assertTrue(pacific(map.getTile(28, 68)));
            assertFalse(pacific(map.getTile(28, 67)));
            assertFalse(pacific(map.getTile(28, 66)));
            assertFalse(pacific(map.getTile(28, 69)));
            assertFalse(pacific(map.getTile(29, 67)));
            final Region p = map.getRegionByKey("model.region.pacific");
            final ServerUnit ship = ship(game, map);

            assertFalse(names(move(ship, 30, 67)));
            assertNull(p.getDiscoverer());

            final ChangeSet cs = move(ship, 29, 67);
            assertTrue(cs.toString(), names(cs));
            assertEquals(ship.getId(), p.getDiscoverer());
            assertTrue(cs.toString().contains("tile:28:68")
                || cs.toString().contains(map.getTile(28, 68).getId()));

            // (28,68) stays in sight, (28..30,69) are new and not Pacific.
            assertFalse(names(move(ship, 29, 68)));

            ((ServerRegion)p).csDiscover(ship.getOwner(), ship, game.getTurn(),
                                         "Pazifik", new ChangeSet());
            assertFalse(p.getDiscoverable());
            assertFalse(names(move(ship, 28, 68)));     // (27,67..69) new
            assertFalse(names(move(ship, 27, 68)));     // into the Pacific
        } finally {
            Topology.setCurrent(saved);
        }
    }

    /**
     * F2: a step into the Pacific that also brings a new Pacific tile into
     * sight, before the answer to the first discovery, sends one message,
     * not one for the sight and one for the tile entered (both pass
     * Region.checkDiscover for the same unit); a move by another unit of
     * the same nation does not take the discovery from the first one.
     */
    public void testOneDiscoveryPerMove() {
        final Topology saved = Topology.current();
        try {
            Topology.setCurrent(Topology.SQUARE);
            final Game game = getStandardGame();
            final Map map = rogersMap(game, Topology.SQUARE);
            final Region p = map.getRegionByKey("model.region.pacific");
            final ServerUnit ship = ship(game, map);
            assertFalse(names(move(ship, 30, 68)));
            assertTrue(names(move(ship, 29, 68)));     // (28,68) in sight
            final ChangeSet cs = move(ship, 28, 68);   // in, (27,68) new
            final String s = cs.toString();
            assertTrue(s, names(cs));
            assertEquals(s, s.indexOf("[" + NewRegionNameMessage.TAG),
                         s.lastIndexOf("[" + NewRegionNameMessage.TAG));
            final ServerUnit other = ship(game, map);
            move(other, 30, 66);
            assertFalse(names(move(other, 29, 65)));    // (28,64..66) new
            assertEquals(ship.getId(), p.getDiscoverer());
        } finally {
            Topology.setCurrent(saved);
        }
    }

    /**
     * FreeCol's isometric maps keep FreeCol's trigger: the step to (29,67)
     * sends nothing, the discovery comes on entering FreeCol's Pacific.
     */
    public void testIsometricKeepsTheEnteredTileRule() {
        final Topology saved = Topology.current();
        try {
            Topology.setCurrent(Topology.ISOMETRIC);
            final Game game = getStandardGame();
            final Map map = rogersMap(game, Topology.ISOMETRIC);
            assertFalse(ServerRegion.hasClassicPacific(map));
            final ServerUnit ship = ship(game, map);
            assertFalse(names(move(ship, 30, 67)));
            assertFalse(names(move(ship, 29, 67)));
            assertTrue(pacific(map.getTile(28, 67)));
            assertTrue(names(move(ship, 28, 67)));
        } finally {
            Topology.setCurrent(saved);
        }
    }
}
