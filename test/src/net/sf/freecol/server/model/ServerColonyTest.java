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

package net.sf.freecol.server.model;

import static net.sf.freecol.common.util.CollectionUtils.first;
import static net.sf.freecol.common.util.CollectionUtils.transform;

import net.sf.freecol.common.model.AbstractGoods;
import net.sf.freecol.common.model.Building;
import net.sf.freecol.common.model.BuildingType;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoodsContainer;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.ModelMessage;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.common.networking.ChangeSet;
import net.sf.freecol.common.option.GameOptions;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.server.ServerTestHelper;
import net.sf.freecol.util.test.FreeColTestCase;
import net.sf.freecol.util.test.FreeColTestUtils;


public class ServerColonyTest extends FreeColTestCase {

    private static final BuildingType chapelType = spec().getBuildingType("model.building.chapel");
    private static final BuildingType carpenterHouseType = spec().getBuildingType("model.building.carpenterHouse");
    private static final BuildingType depotType = spec().getBuildingType("model.building.depot");
    private static final BuildingType lumberMillType = spec().getBuildingType("model.building.lumberMill");
    private static final BuildingType townHallType = spec().getBuildingType("model.building.townHall");
    private static final BuildingType warehouseType = spec().getBuildingType("model.building.warehouse");

    private static final GoodsType bellsType = spec().getGoodsType("model.goods.bells");
    private static final GoodsType crossesType = spec().getGoodsType("model.goods.crosses");
    private static final GoodsType grainType = spec().getGoodsType("model.goods.grain");
    private static final GoodsType hammerGoodsType = spec().getGoodsType("model.goods.hammers");
    private static final GoodsType lumberGoodsType = spec().getGoodsType("model.goods.lumber");
    private static final GoodsType foodType = spec().getGoodsType("model.goods.food");
    private static final GoodsType foodGoodsType = spec().getPrimaryFoodType();

    private static final TileType coniferForest = spec().getTileType("model.tile.coniferForest");
    private static final TileType marsh = spec().getTileType("model.tile.marsh");
    private static final TileType plains = spec().getTileType("model.tile.plains");
    
    private static final UnitType colonistType = spec().getUnitType("model.unit.freeColonist");


    public void testFoodConsumption() {
        Game game = ServerTestHelper.startServerGame(getTestMap(plains));
        ServerPlayer dutch = getServerPlayer(game, "model.nation.dutch");
        // Setting test colony and colonist
        Colony colony = FreeColTestUtils.getColonyBuilder()
            .colonyTile(game.getMap().getTile(5, 8)).build();
        new ServerUnit(game, colony.getWorkLocationForProducing(bellsType),
                       dutch, colonistType);
        assertEquals(0, colony.getGoodsCount(foodType));

        int quantity = colony.getFoodConsumption() * 2;
        colony.addGoods(foodGoodsType, quantity);
        int foodStored = colony.getGoodsCount(foodGoodsType);
        assertEquals(quantity, foodStored);
        int foodExpected = foodStored - colony.getFoodConsumption()
            + colony.getFoodProduction();

        ServerTestHelper.newTurn();
        assertEquals("Unexpected value for remaining food, ",
                     foodExpected, colony.getGoodsCount(foodGoodsType));
    }

    public void testEqualFoodProductionConsumptionCase() {
        Game game = ServerTestHelper.startServerGame(getTestMap(coniferForest));

        // Setting test colony
        Tile colonyTile = game.getMap().getTile(5, 8);
        Colony colony = FreeColTestUtils.getColonyBuilder()
            .colonyTile(colonyTile).initialColonists(1).build();

        // Set the food production of the center tile of the colony to 2
        // This will be the only food production of the colony
        AbstractGoods foodGoods
            = first(transform(colonyTile.getType().getPossibleProduction(true),
                              AbstractGoods.matches(foodGoodsType)));
        if (foodGoods != null) foodGoods.setAmount(2);

        Unit unit = colony.getUnitList().get(0);
        unit.setLocation(colony.getWorkLocationFor(unit, bellsType));

        // Verify that there is enough food stored
        colony.addGoods(foodGoodsType, colony.getFoodConsumption() * 2);

        assertEquals("Production not equal to consumption",
                     colony.getFoodConsumption(), colony.getFoodProduction());

        int colonists = colony.getUnitCount();
        assertEquals("Unexpected change of colonists in colony", colonists,
                     colony.getUnitCount());

        assertEquals("Unexpected change of production/consumption ratio",
                     colony.getFoodProduction(), colony.getFoodConsumption());
    }

    public void testDeathByStarvation() {
        ServerTestHelper.startServerGame(getTestMap(marsh));

        int consumption, production, unitsBeforeNewTurn = 3;
        Colony colony = createStandardColony(unitsBeforeNewTurn);

        final Building townHall = colony.getBuilding(townHallType);
        for (Unit u : colony.getUnitList()) {
            u.setLocation(townHall);
        }
        colony.removeGoods(foodGoodsType);
        colony.invalidateCache();

        consumption = colony.getFoodConsumption();
        production = colony.getFoodProduction();
        assertTrue("Food consumption (" + consumption
            + ") should be higher than production (" + production + ")",
            consumption > production);
        assertEquals("No food stored in colony", 0,
            colony.getGoodsCount(foodType));
        assertEquals("Wrong number of units in colony", unitsBeforeNewTurn,
            colony.getUnitCount());

        ServerTestHelper.newTurn();

        consumption = colony.getFoodConsumption();
        production = colony.getFoodProduction();
        assertTrue("Food consumption (" + consumption
            + ") should be higher than production (" + production + ")",
            consumption > production);
        assertEquals("No food stored in colony", 0,
            colony.getGoodsCount(foodType));
        assertEquals("Wrong number of units in colony", unitsBeforeNewTurn-1,
            colony.getUnitCount());
    }

    public void testAvoidStarvation() {
        ServerTestHelper.startServerGame(getTestMap(marsh));

        int unitsBeforeNewTurn = 3;
        Colony colony = createStandardColony(unitsBeforeNewTurn);
        assertEquals("Wrong number of units in colony", unitsBeforeNewTurn,
            colony.getUnitCount());

        final Building townHall = colony.getBuilding(townHallType);
        for (Unit u : colony.getUnitList()) {
            u.setLocation(townHall);
        }
        colony.removeGoods(foodGoodsType);
        colony.invalidateCache();

        int consumption = colony.getFoodConsumption();
        int production = colony.getTile().getType()
            .getPotentialProduction(grainType, null);
        assertEquals(6, consumption);
        assertEquals(3, production);
        assertEquals(-3, colony.getNetProductionOf(foodType));
        assertEquals(0, colony.getGoodsCount(foodType));
        assertEquals(0, colony.getTile().getUnitCount());

        colony.addGoods(foodType, 202);
        ServerTestHelper.newTurn();
        assertEquals(199, colony.getGoodsCount(foodType));
        assertEquals(0, colony.getTile().getUnitCount());
        assertEquals(3, colony.getUnitCount());

        colony.addGoods(foodType, 15);
        ServerTestHelper.newTurn();
        assertEquals(11, colony.getGoodsCount(foodType));
        assertEquals(1, colony.getTile().getUnitCount());
    }

    /**
     * Tests completion of buildable
     */
    public void testBuildingCompletion() {
        ServerTestHelper.startServerGame(getTestMap(true));

        Colony colony = createStandardColony();
        ServerBuilding initialWarehouse
            = new ServerBuilding(getGame(), colony, depotType);
        colony.addBuilding(initialWarehouse);
        assertTrue("Colony should be able to build warehouse",
                   colony.canBuild(warehouseType));

        // Simulate that the build is done
        colony.setCurrentlyBuilding(warehouseType);
        colony.addGoods(hammerGoodsType, 90);
        assertFalse("Colony should not have warehouse",
                    colony.getWarehouse().getType() == warehouseType);

        ServerTestHelper.newTurn();

        assertTrue("Colony should have warehouse",
                   colony.getWarehouse().getType() == warehouseType);
    }

    /**
     * Tests invalid completion of buildable, having enough resources
     */
    public void testInvalidCompletion() {
        Game game = ServerTestHelper.startServerGame(getTestMap(true));

        Colony colony = createStandardColony(2);
        Building carpenterHouse = colony.getBuilding(carpenterHouseType);
        assertEquals("Colony should not have lumber mill", carpenterHouse,
                     colony.getBuilding(lumberMillType));
        assertFalse("Colony should not be able to build lumber mill",
                    colony.canBuild(lumberMillType));
        colony.setCurrentlyBuilding(lumberMillType);
        assertEquals("Colony should be building lumber mill", lumberMillType,
                     colony.getCurrentlyBuilding());
        // Add sufficient units and goods to build lumber mill.
        Unit unit = new ServerUnit(game, colony.getTile(), colony.getOwner(),
                                   colonistType);
        unit.setLocation(colony);
        for (AbstractGoods ag : lumberMillType.getRequiredGoodsList()) {
            GoodsType type = ag.getType();
            int amount = ag.getAmount() + 1;
            colony.addGoods(type, amount);
            assertEquals("Wrong quantity of " + type, amount,
                         colony.getGoodsCount(type));
        }

        // Allow the building to finish
        ServerTestHelper.newTurn();

        assertEquals("Colony should have lumber mill", lumberMillType,
                     colony.getBuilding(lumberMillType).getType());
        assertFalse("Colony should no longer be building lumber mill",
                    colony.getCurrentlyBuilding() == lumberMillType);
    }

    public void testNoBuildingMaterialsProductionWhenBuildingNothing() {
        Game game = getGame();
        game.changeMap(getTestMap(true));

        Colony colony = createStandardColony(4);
        Building carpenterHouse = colony.getBuilding(carpenterHouseType);
        Unit unit = colony.getFirstUnit();
        // necessary for work production
        int initialLumber = 100;
        int initialHammers = 0;
        colony.addGoods(lumberGoodsType, initialLumber);
        colony.setCurrentlyBuilding(null);

        assertEquals("Wrong initial lumber quantity.",
                     initialLumber, colony.getGoodsCount(lumberGoodsType));
        assertEquals("Colony should not have initial hammers.",
                     initialHammers, colony.getGoodsCount(hammerGoodsType));

        unit.setLocation(carpenterHouse);

        assertTrue("Colony should be producing hammers.",
                   colony.getTotalProductionOf(hammerGoodsType) > 0);

        ServerTestHelper.newTurn();

        assertEquals("Colony should not have produced hammers.",
                     initialHammers, colony.getGoodsCount(hammerGoodsType));
        assertEquals("Wrong final lumber quantity.",
                     initialLumber, colony.getGoodsCount(lumberGoodsType));
    }

    public void testLibertyAndImmigration() {
        ServerTestHelper.startServerGame(getTestMap(true));

        final int population = 3;
        Colony colony = createStandardColony(population);

        ServerBuilding townHall
            = (ServerBuilding)colony.getBuilding(townHallType);
        Unit statesman = colony.getUnitList().get(0);
        townHall.setWorkFor(statesman);
        assertEquals(bellsType, statesman.getWorkType());
        
        ServerBuilding church
            = (ServerBuilding)colony.getBuilding(chapelType);
        church.upgrade();
        Unit preacher = colony.getUnitList().get(1);
        church.setWorkFor(preacher);
        assertEquals(crossesType, preacher.getWorkType());

        assertEquals(population, colony.getUnitCount());
        assertEquals(1, townHall.getUnitCount());
        assertEquals(1, church.getUnitCount());

        int bells0 = 3;
        assertEquals(0, colony.getGoodsCount(bellsType));
        assertEquals(bells0, colony.getNetProductionOf(bellsType));
        assertEquals(0, colony.getLiberty());

        ServerTestHelper.newTurn();
        assertEquals(bells0, colony.getGoodsCount(bellsType));
        assertEquals(bells0, colony.getNetProductionOf(bellsType));
        assertEquals(bells0, colony.getLiberty());

        colony.addGoods(bellsType, 7);
        int bells1 = bells0 + 7;
        assertEquals(bells1, colony.getGoodsCount(bellsType));
        assertEquals(bells0, colony.getNetProductionOf(bellsType));
        assertEquals(bells1, colony.getLiberty());

        colony.removeGoods(bellsType, 5);
        int bells2 = bells1 - 5;
        assertEquals(bells2, colony.getGoodsCount(bellsType));
        assertEquals(bells0, colony.getNetProductionOf(bellsType));
        assertEquals(bells2, colony.getLiberty());

        int crosses = colony.getTotalProductionOf(crossesType)
            - colony.getConsumptionOf(crossesType);
        assertEquals(crosses, colony.getNetProductionOf(crossesType));
        assertEquals(crosses, colony.getGoodsCount(crossesType));
        assertEquals(crosses, colony.getImmigration());

        colony.addGoods(crossesType, 7);
        crosses += 7;
        assertEquals(crosses, colony.getGoodsCount(crossesType));
        assertEquals(crosses, colony.getImmigration());

        colony.removeGoods(crossesType, 5);
        crosses -= 5;
        assertEquals(crosses, colony.getGoodsCount(crossesType));
        assertEquals(crosses, colony.getImmigration());

    }

    /** Disabled.  Currently no reliable way to count messages.

    public void testLimitsMessageDelivery() {
        Game game = ServerTestHelper.startServerGame(getTestMap());

        final int lowLevel = 10;
        final int highLevel = 90;
        final int exportLevel = 50;
        final int overfullLevel = 201;
        final boolean canExport = true;
        int foodStart= lowLevel;

        Colony colony = getStandardColony(1);

        //Setup
        colony.addGoods(new AbstractGoods(foodGoodsType, foodStart));
        assertEquals("Setup error, wrong food count", foodStart, colony.getGoodsCount(foodGoodsType));
        colony.setExportData(new ExportData(foodGoodsType,canExport,lowLevel,highLevel,exportLevel));
        assertEquals("Setup error, wrong food low level",lowLevel,colony.getExportData(foodGoodsType).getLowLevel());
        colony.getGoodsContainer().saveState();

        // Test current condition, no warnings
        assertTrue("Setup error, no messages should have bee received yet", colony.getOwner().getModelMessages().isEmpty());

        ServerTestHelper.newTurn();

        assertEquals("Player should not have received any messages",
                     0, colony.getOwner().getModelMessages().size());

        // Simulate consumption of food
        colony.getGoodsContainer().removeGoods(foodGoodsType, 1);

        ServerTestHelper.newTurn();

        // Test new condition food below limits
        assertEquals("Player should have received one message",
                     1, colony.getOwner().getModelMessages().size());

        // Stuff the colony overfull with food.  This should *not* trigger
        // a warning because upper limits are ignored for food.
        colony.getGoodsContainer().addGoods(foodGoodsType, overfullLevel);
        colony.getOwner().clearModelMessages();

        ServerTestHelper.newTurn();

        assertTrue("Food does not have a storage limit", foodGoodsType.limitIgnored());
        assertEquals("Player should not receive a message",
                     0, colony.getOwner().getModelMessages().size());
    }
    */

    /**
     * The original's report of a build that lacks only tools (part W,
     * build spec M4.11, @NEEDTOOLS; V clip 020 #35, #69, #75, #85, #91: the
     * Verlag's 52 hammers stored, the tools 10, 10, 13, 16, 19 of 20): with
     * all its hammers and fewer tools than it needs, every turn; not while
     * a hammer is missing, not with the tools there; FreeCol's rules warn
     * only when nobody makes the missing goods.
     */
    public void testOriginalNeedTools() {
        Game game = ServerTestHelper.startServerGame(getTestMap(plains));
        final Specification spec = game.getSpecification();
        final BuildingType press = spec.getBuildingType("model.building.printingPress");
        final GoodsType tools = spec.getGoodsType("model.goods.tools");
        final ServerColony colony = (ServerColony)createStandardColony(4);
        colony.setBuildQueue(java.util.List.<net.sf.freecol.common.model.BuildableType>of(press));
        final int hammers = AbstractGoods.getCount(hammerGoodsType, press.getRequiredGoodsList());
        final int need = AbstractGoods.getCount(tools, press.getRequiredGoodsList());
        assertTrue(hammers > 0 && need > 0);
        colony.addGoods(hammerGoodsType, hammers);
        colony.addGoods(tools, need / 2);
        ModelMessage m = colony.originalNeedTools(press);
        assertNotNull(m);
        assertEquals("model.colony.classicNeedTools", m.getId());
        assertEquals(String.valueOf(need), m.getReplacement("%amount%").getId());
        assertEquals(String.valueOf(need / 2), m.getReplacement("%number%").getId());
        assertEquals(colony.getName(), m.getReplacement("%colony%").getId());
        assertEquals(press.getNameKey(), m.getReplacement("%buildable%").getId());
        // No tools at all (@NEEDTOOLS0 on the screen): the stock 0.
        colony.removeGoods(tools);
        assertEquals("0", colony.originalNeedTools(press).getReplacement("%number%").getId());
        // A hammer missing: nothing.
        colony.removeGoods(hammerGoodsType, 1);
        assertNull(colony.originalNeedTools(press));
        colony.addGoods(hammerGoodsType, 1);
        // The tools there: nothing.
        colony.addGoods(tools, need);
        assertNull(colony.originalNeedTools(press));
        colony.removeGoods(tools);
        // At the turn's warnings: the original's report instead of
        // FreeCol's; without the option FreeCol's rule.
        spec.setBoolean(GameOptions.ORIGINAL_COLONY_REPORTS, true);
        try {
            final ChangeSet cs = new ChangeSet();
            colony.csNewTurnWarnings(new java.util.Random(1), new LogBuilder(0), cs);
            assertTrue(cs.toString(), cs.toString().contains("model.colony.classicNeedTools"));
            assertFalse(cs.toString(), cs.toString().contains("buildableNeedsGoods"));
        } finally {
            spec.setBoolean(GameOptions.ORIGINAL_COLONY_REPORTS, false);
        }
        final ChangeSet cs = new ChangeSet();
        colony.csNewTurnWarnings(new java.util.Random(1), new LogBuilder(0), cs);
        assertFalse(cs.toString(), cs.toString().contains("model.colony.classicNeedTools"));
    }

    /**
     * The original's new load (part W, build spec M4.11, @CARGOREADY0-2;
     * V clip 020 #236: rum 103 in a colony with a Lagerhaus; clip 040
     * #84 666: horses at the capacity 300): a stock that passed a multiple
     * of 100 this turn; at the capacity variant 1 while the warehouse can
     * grow, 2 when it cannot; the turn's warnings give it instead of
     * FreeCol's store warnings (with the option).
     */
    public void testOriginalCargoReady() {
        Game game = ServerTestHelper.startServerGame(getTestMap(plains));
        final Specification spec = game.getSpecification();
        final GoodsType rum = spec.getGoodsType("model.goods.rum");
        final ServerColony colony = (ServerColony)createStandardColony(4);
        assertNull("no load passed", colony.originalCargoReady(rum, 103, 110, 200));
        assertNull(colony.originalCargoReady(rum, 0, 99, 200));
        ModelMessage m = colony.originalCargoReady(rum, 95, 103, 200);
        assertEquals("model.colony.classicCargoReady0", m.getId());
        assertEquals("103", m.getReplacement("%amount%").getId());
        assertEquals("200", m.getReplacement("%level%").getId());
        assertEquals(rum.getNameKey(), m.getReplacement("%goods%").getId());
        assertEquals("model.colony.classicCargoReady0",
                     colony.originalCargoReady(rum, 150, 260, 300).getId());
        // At the capacity: the depot or warehouse can grow (1).
        assertEquals("model.colony.classicCargoReady1",
                     colony.originalCargoReady(rum, 90, 100, 100).getId());
        // The expansion built: it cannot (2).
        colony.getWarehouse().upgrade();
        colony.getWarehouse().upgrade();
        assertNull(colony.getWarehouse().getType().getUpgradesTo());
        assertEquals("model.colony.classicCargoReady2",
                     colony.originalCargoReady(rum, 280, 300, 300).getId());
        // At the turn's warnings, with the option: a new load, no FreeCol
        // warning of the store; without it FreeCol's.
        // The cases: the stock before and now, the levi rules' report, FreeCol's.
        final GoodsContainer gc = colony.getGoodsContainer();
        final int limit = colony.getWarehouseCapacity();
        final int high = colony.getExportData(rum).getHighLevel() * limit
            / GoodsContainer.CARGO_SIZE;
        assertTrue("the high-water mark between two loads", high > 105 && high < limit - 5);
        final Object[][] cases = {
            { 95, 103, "model.colony.classicCargoReady0", null },
            { high - 5, high + 5, null, "model.colony.warehouseFull" },
            // (the warehouse at its last level from above: 2)
            { limit - 5, limit, "model.colony.classicCargoReady2",
              "model.colony.warehouseOverfull" },
        };
        for (Object[] c : cases) {
            final int before = (Integer) c[0], now = (Integer) c[1];
            for (boolean original : new boolean[] { true, false }) {
                colony.removeGoods(rum);
                colony.addGoods(rum, before);
                gc.saveState();
                colony.addGoods(rum, now - before);
                spec.setBoolean(GameOptions.ORIGINAL_COLONY_REPORTS, original);
                final String all;
                try {
                    final ChangeSet cs = new ChangeSet();
                    colony.csNewTurnWarnings(new java.util.Random(1), new LogBuilder(0), cs);
                    all = cs.toString();
                } finally {
                    spec.setBoolean(GameOptions.ORIGINAL_COLONY_REPORTS, false);
                }
                final String what = before + " -> " + now + " original=" + original + ": " + all;
                if (original) {
                    if (c[2] != null) assertTrue(what, all.contains((String) c[2]));
                    assertFalse(what, all.contains("warehouseFull"));
                    assertFalse(what, all.contains("warehouseOverfull"));
                } else {
                    if (c[3] != null) assertTrue(what, all.contains((String) c[3]));
                    assertFalse(what, all.contains("classicCargoReady"));
                }
            }
        }
    }
}
