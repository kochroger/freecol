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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Europe;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.Turn;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;
import net.sf.freecol.util.test.FreeColTestUtils;


/**
 * The original's "Gehe zu" list (R2, {@link ClassicDestinations}): which
 * rows a ship and a land unit get, in which order, with which words, and
 * the box's keys (Escape never takes a row).  In the topology the run
 * sets (off and square).
 */
public class ClassicDestinationsTest extends FreeColTestCase {

    private static final TileType ocean = spec().getTileType("model.tile.ocean");
    private static final TileType highSeas = spec().getTileType("model.tile.highSeas");
    private static final TileType plains = spec().getTileType("model.tile.plains");

    /** The test world: its colonies by letter, and the Dutch. */
    private Game game;
    private Map map;
    private Player dutch;
    private Colony a, b, c, d, f;

    /**
     * A 20x15 sea with the high seas at x 18-19; the west land x 0-4 with
     * a lake at (1,12); an island x 9-12, y 1-4.  Dutch colonies (founding
     * turn): B (4,9) 5 and A (4,3) 3, both on the coast, made in that order;
     * C (1,7) inland 6; D (1,13) on the lake 8; F (9,1) on the island 7.
     * An English colony at (12,4).
     */
    private void world() {
        this.game = getStandardGame();
        final MapBuilder builder = new MapBuilder(this.game);
        builder.setDimensions(20, 15).setBaseTileType(ocean).setExploredByAll(true);
        for (int y = 0; y < 15; y++) {
            for (int x = 0; x < 5; x++) builder.setTileType(x, y, plains);
            builder.setTileType(18, y, highSeas);
            builder.setTileType(19, y, highSeas);
        }
        builder.setTileType(1, 12, ocean);
        for (int y = 1; y <= 4; y++) {
            for (int x = 9; x <= 12; x++) builder.setTileType(x, y, plains);
        }
        this.map = builder.build();
        this.game.changeMap(this.map);
        this.dutch = this.game.getPlayerByNationId("model.nation.dutch");
        this.b = colony(this.dutch, "B", 4, 9, 5);
        this.a = colony(this.dutch, "A", 4, 3, 3);
        this.c = colony(this.dutch, "C", 1, 7, 6);
        this.d = colony(this.dutch, "D", 1, 13, 8);
        this.f = colony(this.dutch, "F", 9, 1, 7);
        colony(this.game.getPlayerByNationId("model.nation.english"), "E", 12, 4, 1);
    }

    private Colony colony(Player p, String name, int x, int y, int turn) {
        final Colony col = FreeColTestUtils.getColonyBuilder().player(p)
            .colonyName(name).colonyTile(this.map.getTile(x, y)).build();
        col.setEstablished(new Turn(turn));
        return col;
    }

    private Unit unit(Tile tile, String type) {
        return new ServerUnit(this.game, tile, this.dutch,
                              spec().getUnitType("model.unit." + type));
    }

    private static String labels(List<ClassicDestinations.Row> rows) {
        return rows.toString();
    }

    /**
     * A ship: the coastal colonies it can reach in founding order, then
     * the home port (the stand-in's "Amsterdam (Holland)", Europe); the
     * colony it lies in, an inland colony, a lake colony and a foreign
     * colony never; after independence no home port; a ship in a closed
     * sea nothing at all.
     */
    public void testShipRows() throws Exception {
        world();
        final ClassicText t = texts();
        assertFalse(this.c.isConnectedPort());
        assertFalse(this.d.isConnectedPort());
        assertTrue(this.a.isConnectedPort());
        final Unit ship = unit(this.map.getTile(6, 7), "merchantman");
        assertFalse(ship.getTile().isDirectlyHighSeasConnected());
        List<ClassicDestinations.Row> rows = ClassicDestinations.rows(t, ship);
        assertEquals("[A, B, F, Amsterdam (Holland)]", labels(rows));
        assertSame(this.a, rows.get(0).location);
        assertSame(this.dutch.getEurope(), rows.get(3).location);
        // The order is the founding order, not the settlement list's.
        assertSame(this.b, this.dutch.getColonyList().get(0));
        // Lying in A: A is not listed.
        final Unit docked = unit(this.a.getTile(), "caravel");
        assertSame(this.a, docked.getSettlement());
        assertEquals("[B, F, Amsterdam (Holland)]",
                     labels(ClassicDestinations.rows(t, docked)));
        // Without Europe (independence): the colonies only.
        final Europe europe = this.dutch.getEurope();
        this.dutch.setEurope(null);
        try {
            assertEquals("[A, B, F]", labels(ClassicDestinations.rows(t, ship)));
        } finally {
            this.dutch.setEurope(europe);
        }
        // On the lake: no port and no way to the high seas, so no row.
        final Unit boat = unit(this.map.getTile(1, 12), "caravel");
        assertTrue(ClassicDestinations.rows(t, boat).isEmpty());
        // Off the map (in Europe): no list.
        assertTrue(ClassicDestinations.rows(t, unit(null, "caravel")).isEmpty());
        assertTrue(ClassicDestinations.rows(t, null).isEmpty());
    }

    /**
     * A land unit: its landmass's colonies it can reach in founding order
     * (inland and lake colonies too), not the one it stands in, never the
     * home port; a passenger aboard a ship gets nothing.
     */
    public void testLandRows() throws Exception {
        world();
        final ClassicText t = texts();
        final Unit colonist = unit(this.map.getTile(3, 7), "freeColonist");
        final List<ClassicDestinations.Row> rows = ClassicDestinations.rows(t, colonist);
        assertEquals("[A, B, C, D]", labels(rows));
        assertSame(this.d, rows.get(3).location);
        assertEquals("[A, B, D]", labels(ClassicDestinations.rows(t,
            unit(this.c.getTile(), "hardyPioneer"))));
        assertEquals("[F]", labels(ClassicDestinations.rows(t,
            unit(this.map.getTile(10, 3), "seasonedScout"))));
        final Unit ship = unit(this.map.getTile(5, 7), "caravel");
        final Unit passenger = new ServerUnit(this.game, ship, this.dutch,
            spec().getUnitType("model.unit.freeColonist"));
        assertTrue(passenger.isOnCarrier());
        assertTrue(ClassicDestinations.rows(t, passenger).isEmpty());
    }

    /**
     * The home port's row: @HOMEPORT and @COUNTRY of the original's four
     * nations (V: "Amsterdam (Holland)" landfall #23247, "London
     * (England)" opening_051); without the texts FreeCol's name of the
     * player's Europe; a colony's name is drawn as written.
     */
    public void testLabels() throws Exception {
        final Game g = getStandardGame();
        final ClassicText t = texts();
        final String[][] expect = {
            { "model.nation.english", "London (England)" },
            { "model.nation.french", "La Rochelle (Frankreich)" },
            { "model.nation.spanish", "Sevilla (Spanien)" },
            { "model.nation.dutch", "Amsterdam (Holland)" },
        };
        for (String[] e : expect) {
            final Player p = g.getPlayerByNationId(e[0]);
            assertEquals(e[1], ClassicDestinations.homePortLabel(t, p));
        }
        final Player dutchP = g.getPlayerByNationId("model.nation.dutch");
        assertEquals(3, ClassicDestinations.nation(dutchP));
        assertEquals("Amsterdam", ClassicDestinations.homePort(t, 3));
        assertNull(ClassicDestinations.homePort(t, -1));
        assertNull(ClassicDestinations.homePort(null, 3));
        final String fallback = ClassicDestinations.homePortLabel(null, dutchP);
        assertFalse(fallback.isEmpty());
        assertEquals(ClassicAdvisorBox.literal(Messages.message(
            dutchP.getEurope().getLocationLabelFor(dutchP))), fallback);
        // Braces in a colony's name are no gold markup.
        world();
        this.a.setName("Fort {X}");
        final List<ClassicDestinations.Row> rows = ClassicDestinations.rows(t,
            unit(this.map.getTile(6, 7), "merchantman"));
        assertEquals("Fort (X)", rows.get(0).label);
    }

    /**
     * The box: GAME.TXT's section as its id, its @width, the bar on
     * @default=1, the rows at box + 15, no portrait, Escape and a click
     * outside choose nothing (the builder's default would take the last
     * row); without the texts FreeCol's "Zielort auswählen" with the same
     * rows and keys.
     */
    public void testRequest() throws Exception {
        final ClassicText t = texts();
        final List<ClassicDestinations.Row> rows = new ArrayList<>();
        rows.add(new ClassicDestinations.Row("Amsterdam (Holland)", null));
        final ClassicAdvisorBox.Request ship = ClassicDestinations.request(t, true, rows);
        assertEquals(ClassicDestinations.SAIL_PORT_SECTION, ship.id);
        assertEquals("Choose a port:", ship.plainText());
        assertEquals(230, ship.width);
        assertEquals(List.of("Amsterdam (Holland)"), ship.rows);
        assertEquals(0, ship.defaultRow);
        assertEquals(-1, ship.cancelRow);
        assertEquals(15, ship.rowIndent);
        assertEquals(ClassicMenuBox.PORT_INDENT, ship.rowIndent);
        assertSame(ClassicAdvisorBox.Portrait.NONE, ship.portrait);
        assertTrue(ship.outsideCancels);
        final ClassicAdvisorBox.Request land = ClassicDestinations.request(t, false, rows);
        assertEquals(ClassicDestinations.TRAVEL_PLACE_SECTION, land.id);
        assertEquals("Choose a colony:", land.plainText());
        assertEquals(-1, land.cancelRow);
        assertEquals(15, land.rowIndent);
        final ClassicAdvisorBox.Request bare = ClassicDestinations.request(null, true, rows);
        assertEquals(Messages.message("selectDestinationDialog.text"), bare.plainText());
        assertEquals(0, bare.defaultRow);
        assertEquals(-1, bare.cancelRow);
        assertEquals(List.of("Amsterdam (Holland)"), bare.rows);
    }

    /**
     * The keys on the real bar: Enter takes the barred row, the arrows move
     * it, Escape and a click outside take none -- also with three rows,
     * where the builder's default would have sent the unit to the last.
     */
    public void testKeys() throws Exception {
        final ClassicText t = texts();
        final List<ClassicDestinations.Row> one = new ArrayList<>();
        one.add(new ClassicDestinations.Row("Amsterdam (Holland)", null));
        final ClassicAdvisorBox.Request r1 = ClassicDestinations.request(t, true, one);
        final ClassicGUISeamTest.KeyPrompter k = new ClassicGUISeamTest.KeyPrompter();
        assertEquals(0, k.answer(r1, "ENTER"));
        assertEquals(-1, k.answer(r1, "ESC"));
        assertEquals(-1, k.answer(r1, "OUT"));
        assertEquals(0, k.answer(r1, "UP", "DOWN", "DOWN", "ENTER"));
        final List<ClassicDestinations.Row> three = new ArrayList<>(one);
        three.add(0, new ClassicDestinations.Row("A", null));
        three.add(1, new ClassicDestinations.Row("B", null));
        final ClassicAdvisorBox.Request r3 = ClassicDestinations.request(t, true, three);
        assertEquals(0, k.answer(r3, "ENTER"));
        assertEquals(2, k.answer(r3, "DOWN", "DOWN", "ENTER"));
        assertEquals(1, k.answer(r3, "DOWN", "DOWN", "UP", "ENTER"));
        assertEquals(-1, k.answer(r3, "ESC"));
        assertEquals(-1, k.answer(r3, "DOWN", "DOWN", "ESC"));
        assertEquals(-1, k.answer(r3, "OUT"));
    }

    /**
     * Stand-in texts (the original's are never in the repository): the
     * two boxes, @HOMEPORT and @COUNTRY as NAMES.TXT has them.
     */
    static ClassicText texts() throws Exception {
        final File dir = Files.createTempDirectory("destinations").toFile();
        try {
            final File game = new File(dir, "GAME.TXT");
            final File names = new File(dir, "NAMES.TXT");
            final File labels = new File(dir, "LABELS.TXT");
            Files.write(game.toPath(), ("@SAILPORT\r\n@width=230\r\n@default=1\r\n"
                + "Choose a port:\r\n\r\n@TRAVELPLACE\r\n@width=230\r\n@default=1\r\n"
                + "Choose a colony:\r\n\r\n@END\r\n").getBytes(StandardCharsets.ISO_8859_1));
            Files.write(names.toPath(), ("@COUNTRY\r\nEngland,                12\r\n"
                + "Frankreich,                 9\r\nSpanien,                  14\r\n"
                + "Holland,            13\r\n\r\n@HOMEPORT\r\nLondon\r\nLa Rochelle\r\n"
                + "Sevilla\r\nAmsterdam\r\n\r\n@END\r\n")
                .getBytes(StandardCharsets.ISO_8859_1));
            Files.write(labels.toPath(), "@END\r\n".getBytes(StandardCharsets.ISO_8859_1));
            return ClassicText.fromFiles(game, names, labels);
        } finally {
            for (File x : dir.listFiles()) x.delete();
            dir.delete();
        }
    }
}
