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

import java.util.List;
import java.util.Map;

import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.ColonyTile;
import net.sf.freecol.common.model.Direction;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.ModelMessage;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Resource;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * The original's tutorial tips (master plan W11, {@link ClassicTips}):
 * which unit brings which tip, the tips' values in the original's words,
 * their boxes, and FreeCol's own tutorial messages hidden.
 */
public class ClassicTipsTest extends FreeColTestCase {

    private static ClassicText texts() {
        return ClassicText.load(ClassicPackFiles.runtime());
    }

    private static int bit(int k) {
        return 1 << k;
    }

    /**
     * The unit tips, in their order: an empty ship (11), a pioneer on land
     * (13), a soldier on land (14), a unit that can found the first colony
     * next to a resource (3, not on it).  None aboard a ship, none at sea,
     * none once shown.
     */
    public void testUnitTips() {
        final Game game = getStandardGame();
        final net.sf.freecol.common.model.Map map
            = getCoastTestMap(spec().getTileType("model.tile.plains"), true);
        game.changeMap(map);
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final UnitType colonist = spec().getUnitType("model.unit.freeColonist");
        final Tile land = map.getTile(4, 7), sea = map.getTile(12, 7);
        final Unit ship = new ServerUnit(game, sea, dutch,
            spec().getUnitType("model.unit.merchantman"));
        final Unit passenger = new ServerUnit(game, ship, dutch, colonist,
            spec().getRole("model.role.soldier"));
        // A ship with a passenger or goods: none; empty: 11.
        assertEquals(-1, ClassicTips.unitTip(ship, 0));
        assertEquals(-1, ClassicTips.unitTip(passenger, 0));   // aboard, at sea
        passenger.setLocation(land);
        assertEquals(ClassicTips.SHIP, ClassicTips.unitTip(ship, 0));
        ship.getGoodsContainer().addGoods(spec().getGoodsType("model.goods.furs"), 20);
        assertEquals(-1, ClassicTips.unitTip(ship, 0));
        ship.getGoodsContainer().removeGoods(spec().getGoodsType("model.goods.furs"));
        assertEquals(ClassicTips.SHIP, ClassicTips.unitTip(ship, 0));
        assertEquals(-1, ClassicTips.unitTip(ship, bit(ClassicTips.SHIP)));
        // The soldier, now on land: 14.
        assertEquals(ClassicTips.SOLDIER, ClassicTips.unitTip(passenger, 0));
        assertEquals(-1, ClassicTips.unitTip(passenger, bit(ClassicTips.SOLDIER)));
        // A pioneer on land: 13, before 14 and 3.
        final Unit pioneer = new ServerUnit(game, land, dutch, colonist,
            spec().getRole("model.role.pioneer"));
        assertEquals(ClassicTips.PIONEER, ClassicTips.unitTip(pioneer, 0));
        // A dragoon is not the soldiers' tip (I); a scout neither.
        final Unit dragoon = new ServerUnit(game, land, dutch, colonist,
            spec().getRole("model.role.dragoon"));
        assertEquals(-1, ClassicTips.unitTip(dragoon, bit(ClassicTips.SITE)));
        // No resource around: no site tip.
        final Unit settler = new ServerUnit(game, land, dutch, colonist);
        assertEquals(-1, ClassicTips.unitTip(settler, 0));
        // The game on a neighbour: 3, its goods furs ("Felle").
        final Tile north = land.getNeighbourOrNull(Direction.N);
        north.addResource(new Resource(game, north,
            spec().getResourceType("model.resource.game")));
        assertEquals(ClassicTips.SITE, ClassicTips.unitTip(settler, 0));
        assertEquals(4, ClassicTips.siteCargo(settler));
        assertEquals(ClassicTips.PIONEER, ClassicTips.unitTip(pioneer, 0));
        assertEquals(ClassicTips.SITE, ClassicTips.unitTip(pioneer, bit(ClassicTips.PIONEER)));
        assertEquals(-1, ClassicTips.unitTip(settler, bit(ClassicTips.SITE)));
        // Standing on the resource (landfall turn 8): none.
        settler.setLocation(north);
        assertEquals(-1, ClassicTips.siteCargo(settler));
        settler.setLocation(land);
        // Aboard a ship next to it: none.
        settler.setLocation(ship);
        assertEquals(-1, ClassicTips.unitTip(settler, 0));
        settler.setLocation(land);
        // A ship never brings the site tip.
        assertEquals(-1, ClassicTips.unitTip(ship, bit(ClassicTips.SHIP)));
        // The other resources' goods: the original's @RESOURCE -> @CARGO.
        final String[][] rg = {
            { "model.resource.furs", "4" }, { "model.resource.lumber", "5" },
            { "model.resource.ore", "6" }, { "model.resource.silver", "7" },
            { "model.resource.minerals", "6" }, { "model.resource.grain", "0" },
            { "model.resource.oasis", "0" }, { "model.resource.cotton", "3" },
            { "model.resource.tobacco", "2" }, { "model.resource.sugar", "1" },
        };
        for (String[] r : rg) {
            north.removeResource();
            north.addResource(new Resource(game, north, spec().getResourceType(r[0])));
            assertEquals(r[0], Integer.parseInt(r[1]), ClassicTips.siteCargo(settler));
        }
        // After the first colony: none (the tip speaks of the first).
        final Colony colony = createStandardColony(1, 1, 1);
        assertSame(dutch, colony.getOwner());
        assertEquals(-1, ClassicTips.unitTip(settler, 0));
        assertEquals(ClassicTips.PIONEER, ClassicTips.unitTip(pioneer, 0));
        // Gone: none.
        assertEquals(-1, ClassicTips.unitTip(null, 0));
    }

    /** The unit tips' values: the ship and the home port; the resource's goods. */
    public void testUnitValues() {
        final ClassicText t = texts();
        if (t == null) {
            System.err.println("ClassicTipsTest: no pack texts, values skipped");
            return;
        }
        final Game game = getStandardGame();
        final net.sf.freecol.common.model.Map map
            = getCoastTestMap(spec().getTileType("model.tile.plains"), true);
        game.changeMap(map);
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final Unit ship = new ServerUnit(game, map.getTile(12, 7), dutch,
            spec().getUnitType("model.unit.merchantman"));
        assertEquals(Map.of("STRING0", "Handelsschiff", "STRING1", "Amsterdam"),
                     ClassicTips.unitValues(t, ship, ClassicTips.SHIP));
        final Tile land = map.getTile(4, 7);
        final Tile north = land.getNeighbourOrNull(Direction.N);
        north.addResource(new Resource(game, north,
            spec().getResourceType("model.resource.game")));
        final Unit settler = new ServerUnit(game, land, dutch,
            spec().getUnitType("model.unit.freeColonist"));
        assertEquals(Map.of("STRING0", "Felle"),
                     ClassicTips.unitValues(t, settler, ClassicTips.SITE));
        assertTrue(ClassicTips.unitValues(t, settler, ClassicTips.PIONEER).isEmpty());
    }

    /**
     * {@code @TUTORIAL5}'s values (landfall #18279): "Amsterdam" and the
     * colonist as NAMES.TXT @JOB's second column; FreeCol's name without
     * the pack.
     */
    public void testUnrestValues() {
        final Game game = getStandardGame();
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final Unit farmer = new ServerUnit(game, dutch.getEurope(), dutch,
            spec().getUnitType("model.unit.expertFarmer"));
        final Unit free = new ServerUnit(game, dutch.getEurope(), dutch,
            spec().getUnitType("model.unit.freeColonist"));
        final Map<String, String> none = ClassicTips.unrestValues(null, dutch, farmer);
        assertNull(none.get("STRING0"));
        assertNotNull(none.get("STRING1"));
        final ClassicText t = texts();
        if (t == null) return;
        assertEquals(Map.of("STRING0", "Amsterdam", "STRING1", "Erfahrene Farmer"),
                     ClassicTips.unrestValues(t, dutch, farmer));
        assertEquals("Freie Siedler", ClassicTips.unrestValues(t, dutch, free).get("STRING1"));
        assertEquals(Map.of("STRING0", "Amsterdam"), ClassicTips.unrestValues(t, dutch, null));
    }

    /**
     * {@code @TUTORIAL4}'s values (clip008 #4068 "Felle" / "Nutzholz"):
     * what the colonist makes on his tile, and of sugar .. silver the one
     * the tile gives him most of; in a building, its goods and lumber.
     */
    public void testColonyValues() {
        final ClassicText t = texts();
        if (t == null) {
            System.err.println("ClassicTipsTest: no pack texts, colony values skipped");
            return;
        }
        final Game game = getStandardGame();
        game.changeMap(getTestMap(spec().getTileType("model.tile.mixedForest"), true));
        final Colony colony = createStandardColony(1, 5, 8);
        final Unit u = colony.getUnitList().get(0);
        final Tile next = colony.getTile().getNeighbourOrNull(Direction.N);
        final ColonyTile ct = colony.getColonyTile(next);
        assertNotNull(ct);
        u.setLocation(ct);
        u.changeWorkType(spec().getGoodsType("model.goods.furs"));
        assertEquals(Map.of("STRING0", "Felle", "STRING1", "Nutzholz"),
                     ClassicTips.colonyValues(t, colony));
        u.changeWorkType(spec().getGoodsType("model.goods.lumber"));
        assertEquals(Map.of("STRING0", "Nutzholz", "STRING1", "Felle"),
                     ClassicTips.colonyValues(t, colony));
        u.changeWorkType(spec().getGoodsType("model.goods.grain"));
        assertEquals("Nahrungsmittel", ClassicTips.colonyValues(t, colony).get("STRING0"));
        assertEquals("Nutzholz", ClassicTips.colonyValues(t, colony).get("STRING1"));
        // In a building: its goods, and lumber.
        u.setLocation(colony.getBuilding(spec().getBuildingType("model.building.carpenterHouse")));
        u.changeWorkType(spec().getGoodsType("model.goods.hammers"));
        assertEquals(Map.of("STRING0", "Hämmer", "STRING1", "Nutzholz"),
                     ClassicTips.colonyValues(t, colony));
        assertEquals(Map.of("STRING0", colony.getName()), ClassicTips.dockValues(colony));
        assertTrue(ClassicTips.colonyValues(t, null).isEmpty());
    }

    /**
     * Each tip's box: GAME.TXT's section with its advisor (the landfall
     * crops; clip008 #4068 and #30059 the colonist), no rows, a notice.
     */
    public void testRequests() {
        final ClassicText t = texts();
        assertNull(ClassicTips.request(null, ClassicTips.LAND_HO, Map.of()));
        if (t == null) return;
        final Object[][] tips = {
            { ClassicTips.LAND_HO, ClassicAdvisorBox.Portrait.ADMIRAL },
            { ClassicTips.SITE, ClassicAdvisorBox.Portrait.SCOUT },
            { ClassicTips.COLONY, ClassicAdvisorBox.Portrait.COLONIST },
            { ClassicTips.UNREST, ClassicAdvisorBox.Portrait.ADMIRAL },
            { ClassicTips.SHIP, ClassicAdvisorBox.Portrait.ADMIRAL },
            { ClassicTips.DOCK, ClassicAdvisorBox.Portrait.COLONIST },
            { ClassicTips.PIONEER, ClassicAdvisorBox.Portrait.SCOUT },
            { ClassicTips.SOLDIER, ClassicAdvisorBox.Portrait.SOLDIER },
        };
        for (Object[] tip : tips) {
            final int k = (Integer) tip[0];
            final ClassicAdvisorBox.Request r = ClassicTips.request(t, k, Map.of());
            assertNotNull("TUTORIAL" + k, r);
            assertEquals("TUTORIAL" + k, r.id);
            assertSame("TUTORIAL" + k, tip[1], r.portrait);
            assertTrue("TUTORIAL" + k, r.rows.isEmpty());
            assertTrue("TUTORIAL" + k, r.isNotice());
        }
        final ClassicAdvisorBox.Request r = ClassicTips.request(t, ClassicTips.UNREST,
            Map.of("STRING0", "Amsterdam", "STRING1", "Erfahrene Farmer"));
        assertTrue(r.plainText(), r.plainText().contains("Amsterdam"));
        assertTrue(r.plainText(), r.plainText().contains("Erfahrene Farmer"));
    }

    /** NAMES.TXT {@code @CARGO} rows of FreeCol's goods (grain and fish are food). */
    public void testCargoRows() {
        assertEquals(0, ClassicTips.cargoRow("model.goods.grain"));
        assertEquals(0, ClassicTips.cargoRow("model.goods.fish"));
        assertEquals(4, ClassicTips.cargoRow("model.goods.furs"));
        assertEquals(5, ClassicTips.cargoRow("model.goods.lumber"));
        assertEquals(15, ClassicTips.cargoRow("model.goods.muskets"));
        assertEquals(16, ClassicTips.cargoRow("model.goods.hammers"));
        assertEquals(17, ClassicTips.cargoRow("model.goods.crosses"));
        assertEquals(18, ClassicTips.cargoRow("model.goods.bells"));
        assertEquals(-1, ClassicTips.cargoRow(null));
        final ClassicText t = texts();
        if (t == null) return;
        assertEquals("Felle", ClassicTips.cargoName(t, 4));
        assertEquals("Freiheitsglocken", ClassicTips.cargoName(t, 18));
        assertNull(ClassicTips.cargoName(t, -1));
    }

    /** FreeCol's own tutorial messages never come up in the Classic UI. */
    public void testFreeColTutorialMessagesHidden() {
        final Game game = getStandardGame();
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final ModelMessage tip = new ModelMessage(ModelMessage.MessageType.TUTORIAL,
            "some.tutorial", dutch, dutch);
        final ModelMessage other = new ModelMessage(ModelMessage.MessageType.WARNING,
            "model.colony.famineFeared", dutch, dutch);
        assertEquals(List.of(other), ClassicGUI.withoutStartMessage(List.of(tip, other)));
        // The unrest's notice is the trigger of @TUTORIAL5.
        final Unit u = new ServerUnit(game, dutch.getEurope(), dutch,
            spec().getUnitType("model.unit.expertFarmer"));
        final ModelMessage emigrate = dutch.getEmigrationMessage(u);
        assertSame(u, ClassicGUI.unrestUnit(emigrate, u, dutch));
        assertNull(ClassicGUI.unrestUnit(other, u, dutch));
        assertNull(ClassicGUI.unrestUnit(emigrate, u,
            game.getPlayerByNationId("model.nation.english")));
        assertNull(ClassicGUI.unrestUnit(emigrate, dutch, dutch));
    }
}
