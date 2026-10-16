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

package net.sf.freecol.common.model;

import java.util.List;

import net.sf.freecol.common.option.GameOptions;
import net.sf.freecol.server.model.ServerColony;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * KAUFEN's price in the "levi" rules (levi rule R1, part V; part X): 13
 * per missing hammer and 6 per missing tool, the whole doubled while the
 * colony has no hammer stored.  Every recorded box is a golden case: the
 * 23 KAUFEN boxes of Roger's playthrough 4 (clips 045-048,
 * {@code playthrough-4-analysis/support/allboxes.csv}, the states of
 * {@code 10-new-for-us.md} section 3.1), clip 020 #94078, dago-colony2
 * #12061 and playthrough-2 #37082.  With the option off, FreeCol's price.
 */
public class LeviBuildPriceTest extends FreeColTestCase {

    private static final String LEVI = "levi";

    private static Specification levi() {
        return spec(LEVI);
    }

    @Override
    protected void tearDown() throws Exception {
        levi().setBoolean(GameOptions.ORIGINAL_BUILD_PRICE, true);
        super.tearDown();
    }

    /**
     * One KAUFEN box: the clip and frame, the build (its FreeCol id), the
     * hammers and tools stored, the treasury shown, and the price shown.
     */
    static final class Box {
        final String frame, build;
        final int hammers, tools, treasury, price;

        Box(String frame, String build, int hammers, int tools, int treasury, int price) {
            this.frame = frame;
            this.build = build;
            this.hammers = hammers;
            this.tools = tools;
            this.treasury = treasury;
            this.price = price;
        }

        /** @return Whether the box is @BUYME0 (the treasury short of the price). */
        boolean short0() {
            return this.treasury < this.price;
        }

        @Override
        public String toString() {
            return this.frame + " " + this.build;
        }
    }

    /**
     * The recorded boxes.  The stored hammers and tools follow from the
     * box's price and the build's cost (the clips show the price, the
     * hammer area and the build list, 10-new-for-us 3.1); the treasury is
     * the box's «Schatzkammer».
     */
    static final List<Box> BOXES = List.of(
        // Playthrough 4 (clips 045-048).
        new Box("046 #67815", "building.docks", 48, 0, 371, 52),           // 13 x 4
        new Box("046 #91428", "building.docks", 27, 0, 1427, 325),         // 13 x 25
        new Box("046 #97616", "building.docks", 30, 0, 1427, 286),         // 13 x 22, bought
        new Box("046 #119741", "building.printingPress", 24, 0, 383, 484), // 13 x 28 + 6 x 20
        new Box("048 #10270", "building.printingPress", 52, 0, 195, 120),  // 6 x 20, bought
        new Box("048 #25487", "building.lumberMill", 21, 0, 75, 403),      // 13 x 31
        new Box("048 #43504", "building.customHouse", 12, 0, 75, 2224),    // 13 x 148 + 6 x 50
        new Box("048 #62921", "building.lumberMill", 48, 0, 13, 52),       // 13 x 4
        new Box("048 #70208", "building.customHouse", 0, 0, 13, 4760),     // 2 x (13 x 160 + 6 x 50)
        new Box("048 #81676", "building.customHouse", 120, 0, 13, 820),    // 13 x 40 + 6 x 50
        new Box("048 #95992", "building.customHouse", 66, 0, 13, 1522),    // 13 x 94 + 6 x 50
        new Box("048 #96928", "building.customHouse", 156, 0, 13, 352),    // 13 x 4 + 6 x 50
        new Box("048 #102145", "building.customHouse", 160, 0, 13, 300),   // 6 x 50
        new Box("048 #107290", "building.customHouse", 160, 3, 13, 282),   // 6 x 47
        new Box("048 #114260", "building.customHouse", 160, 8, 13, 252),   // 6 x 42
        new Box("048 #123059", "building.customHouse", 160, 18, 129, 192), // 6 x 32
        new Box("048 #124875", "building.customHouse", 160, 23, 129, 162), // 6 x 27
        new Box("048 #125963", "building.customHouse", 160, 28, 129, 132), // 6 x 22
        new Box("048 #126909", "building.customHouse", 136, 0, 129, 612),  // 13 x 24 + 6 x 50
        new Box("048 #128864", "building.customHouse", 160, 33, 129, 102), // 6 x 17, bought
        new Box("048 #139919", "building.newspaper", 0, 0, 630, 3720),     // 2 x (13 x 120 + 6 x 50)
        new Box("048 #140357", "building.customHouse", 160, 6, 630, 264),  // 6 x 44
        new Box("048 #165326", "building.blacksmithShop", 64, 6, 2350, 84), // 6 x 14
        // The earlier recordings.
        new Box("020 #94078", "building.warehouse", 42, 0, 348, 494),      // 13 x 38
        new Box("dago-colony2 #12061", "unit.artillery", 96, 44, 115103, 1248), // 13 x 96
        new Box("playthrough-2 #37082", "building.lumberMill", 24, 0, 534, 364)); // 13 x 28

    /** The build of a box. */
    static BuildableType buildable(Specification spec, Box b) {
        final String id = "model." + b.build;
        return (b.build.startsWith("unit.")) ? spec.getUnitType(id) : spec.getBuildingType(id);
    }

    /** A levi colony with a box's stock. */
    private Colony colony(Box b) {
        final Game game = getStandardGame(LEVI);
        final Map map = new MapBuilder(game).setExploredByAll(true)
            .setBaseTileType(levi().getTileType("model.tile.plains")).build();
        game.changeMap(map);
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final Colony c = new ServerColony(game, dutch, "Base", map.getTile(5, 8));
        dutch.addSettlement(c);
        c.placeSettlement(true);
        stock(c, b);
        return c;
    }

    /** Set a colony's hammers and tools to a box's. */
    static void stock(Colony c, Box b) {
        final Specification spec = c.getSpecification();
        final GoodsType hammers = spec.getGoodsType("model.goods.hammers");
        final GoodsType tools = spec.getGoodsType("model.goods.tools");
        c.removeGoods(hammers);
        c.removeGoods(tools);
        if (b.hammers > 0) c.addGoods(hammers, b.hammers);
        if (b.tools > 0) c.addGoods(tools, b.tools);
        assertEquals(b.hammers, c.getGoodsCount(hammers));
        assertEquals(b.tools, c.getGoodsCount(tools));
    }

    /** All 26 recorded KAUFEN prices, as the original shows them. */
    public void testEveryRecordedBox() {
        assertEquals(26, BOXES.size());
        for (Box b : BOXES) {
            final Colony c = colony(b);
            final BuildableType bt = buildable(levi(), b);
            assertNotNull(b.toString(), bt);
            assertEquals(b.toString(), b.price, c.getPriceForBuilding(bt));
        }
    }

    /** The two boxes with the hammer area empty are the doubled ones (V 048 #70208, #139919). */
    public void testDoubledOnlyWithNoHammer() {
        int doubled = 0;
        for (Box b : BOXES) {
            final Colony c = colony(b);
            final BuildableType bt = buildable(levi(), b);
            final int hammersMissing = Math.max(0, required(bt, "hammers") - b.hammers);
            final int toolsMissing = Math.max(0, required(bt, "tools") - b.tools);
            final int plain = Colony.ORIGINAL_HAMMER_PRICE * hammersMissing
                + Colony.ORIGINAL_TOOL_PRICE * toolsMissing;
            if (b.hammers == 0) {
                doubled++;
                assertEquals(b.toString(), 2 * plain, c.getPriceForBuilding(bt));
            } else {
                assertEquals(b.toString(), plain, c.getPriceForBuilding(bt));
            }
        }
        assertEquals(2, doubled);
        // One hammer is enough: no doubling (the rule "no hammer stored").
        final Box one = new Box("one hammer", "building.customHouse", 1, 0, 0,
            13 * 159 + 6 * 50);
        assertEquals(one.price, colony(one).getPriceForBuilding(buildable(levi(), one)));
    }

    private static int required(BuildableType bt, String good) {
        final GoodsType gt = levi().getGoodsType("model.goods." + good);
        int n = 0;
        for (AbstractGoods ag : bt.getRequiredGoodsList()) {
            if (ag.getType() == gt) n += ag.getAmount();
        }
        return n;
    }

    /** With the option off (FreeCol, the classic and freecol rules): 20 a hammer, the tools at the ask plus 10 %. */
    public void testFreeColsPriceWithTheOptionOff() {
        final Box b = BOXES.get(3);   // 046 #119741: 28 hammers and 20 tools missing
        final Colony c = colony(b);
        final BuildableType bt = buildable(levi(), b);
        levi().setBoolean(GameOptions.ORIGINAL_BUILD_PRICE, false);
        final GoodsType tools = levi().getGoodsType("model.goods.tools");
        final int ask = c.getOwner().getMarket().getBidPrice(tools, 20);
        assertEquals(20 * 28 + ask * 110 / 100, c.getPriceForBuilding(bt));
        final Box empty = BOXES.get(8);   // 048 #70208, no hammer: FreeCol does not double
        final Colony e = colony(empty);
        final int askE = e.getOwner().getMarket().getBidPrice(tools, 50);
        assertEquals(20 * 160 + askE * 110 / 100,
                     e.getPriceForBuilding(buildable(levi(), empty)));
        levi().setBoolean(GameOptions.ORIGINAL_BUILD_PRICE, true);
        assertEquals(b.price, c.getPriceForBuilding(bt));
    }

    /** The classic rules: FreeCol's price (no option there, Specification.fixGameOptions gives it off). */
    public void testTheClassicRulesKeepFreeColsPrice() {
        assertFalse(spec("classic").getBoolean(GameOptions.ORIGINAL_BUILD_PRICE));
        final Game game = getStandardGame("classic");
        final Map map = new MapBuilder(game).setExploredByAll(true)
            .setBaseTileType(spec("classic").getTileType("model.tile.plains")).build();
        game.changeMap(map);
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final Colony c = new ServerColony(game, dutch, "Base", map.getTile(5, 8));
        dutch.addSettlement(c);
        c.placeSettlement(true);
        final BuildableType docks = spec("classic").getBuildingType("model.building.docks");
        assertEquals("no doubling, 20 a hammer", 20 * 52, c.getPriceForBuilding(docks));
    }
}
