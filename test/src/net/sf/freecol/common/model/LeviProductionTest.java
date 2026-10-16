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

import net.sf.freecol.common.model.production.OriginalProduction;
import net.sf.freecol.common.option.GameOptions;
import net.sf.freecol.server.model.ServerBuilding;
import net.sf.freecol.server.model.ServerColony;
import net.sf.freecol.server.model.ServerIndianSettlement;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * The original's colony production in the "levi" rules (part X; spec
 * {@code freecol-spike-results/x-prep/production-rules.md}, its golden
 * cases G1-G15 and clip 048's job menus): each state built as a small
 * colony of the levi rules, its numbers asserted with the rules on (the
 * original's, V where a clip frame or a save shows it) and with the
 * rule's option off (FreeCol's numbers, which the classic and freecol
 * rules keep).  Both the colony's production (its production cache, the
 * colony screen's tile rows) and the job menu's number
 * ({@code WorkLocation.getPotentialProduction}) are checked.
 */
public class LeviProductionTest extends FreeColTestCase {

    private static final String LEVI = "levi";

    private static Specification levi() {
        return spec(LEVI);
    }

    @Override
    protected void tearDown() throws Exception {
        for (String id : Specification.ORIGINAL_PRODUCTION_OPTIONS) {
            levi().setBoolean(id, true);
        }
        levi().applyDifficultyLevel("model.difficulty.medium");
        super.tearDown();
    }

    private static TileType tile(String id) {
        return levi().getTileType("model.tile." + id);
    }

    private static GoodsType goods(String id) {
        return levi().getGoodsType("model.goods." + id);
    }

    private static UnitType unit(String id) {
        return levi().getUnitType("model.unit." + id);
    }

    /** All the part X options on (true) or off. */
    private static void rules(boolean on) {
        for (String id : Specification.ORIGINAL_PRODUCTION_OPTIONS) {
            levi().setBoolean(id, on);
        }
    }


    /**
     * A colony of the levi rules at «Entdecker» (clip 020's level, FreeCol's
     * very easy) on a plains map, its centre square of a given type.
     */
    private final class Rig {

        final Game game;
        final Map map;
        final Player dutch;
        final Tile centre;
        final Colony colony;

        Rig(String centreType, String... improvements) {
            this.game = getStandardGame(LEVI);
            levi().applyDifficultyLevel("model.difficulty.veryEasy");
            this.map = new MapBuilder(this.game).setBaseTileType(tile("plains")).build();
            this.game.changeMap(this.map);
            this.dutch = this.game.getPlayerByNationId("model.nation.dutch");
            this.centre = this.map.getTile(5, 8);
            this.centre.setType(tile(centreType));
            for (String i : improvements) improve(this.centre, i);
            this.colony = new ServerColony(this.game, this.dutch, "Base", this.centre);
            this.dutch.addSettlement(this.colony);
            this.colony.placeSettlement(true);
            this.dutch.invalidateCanSeeTiles();
            this.colony.setBuildQueue(new java.util.ArrayList<>());
        }

        /** The neighbour square in a direction, of a type, with improvements. */
        Tile square(Direction d, String type, String... improvements) {
            final Tile t = this.centre.getNeighbourOrNull(d);
            assertNotNull(d.toString(), t);
            t.setType(tile(type));
            for (String i : improvements) improve(t, i);
            this.colony.invalidateCache();
            return t;
        }

        /** A resource on a square. */
        void resource(Tile t, String id) {
            t.addResource(new Resource(this.game, t,
                levi().getResourceType("model.resource." + id)));
            this.colony.invalidateCache();
        }

        /** A colonist of a type on a square, at a good. */
        Unit on(String type, Tile t, String good) {
            final Unit u = new ServerUnit(this.game, this.centre, this.dutch, unit(type));
            final ColonyTile ct = this.colony.getColonyTile(t);
            assertNotNull(ct);
            assertTrue(type + " onto " + t, u.setLocation(ct));
            u.changeWorkType(goods(good));
            this.colony.invalidateCache();
            return u;
        }

        /** A colonist of a type in a building (eats, makes no food). */
        Unit in(String type, String building) {
            final Unit u = new ServerUnit(this.game, this.centre, this.dutch, unit(type));
            final Building b = this.colony.getBuilding(levi().getBuildingType(
                "model.building." + building));
            assertNotNull(building, b);
            assertTrue(u.setLocation(b));
            final ProductionType pt = b.getAvailableProductionTypes(false).get(0);
            u.changeWorkType(pt.getOutputs().findFirst().get().getType());
            this.colony.invalidateCache();
            return u;
        }

        /**
         * A building added (a first level) or the family upgraded in place,
         * whatever the population (as the server's upgrade does it,
         * {@code Building.setType}).
         */
        void build(String id) {
            final BuildingType bt = levi().getBuildingType("model.building." + id);
            final Building had = (bt.getUpgradesFrom() == null) ? null
                : this.colony.getBuilding(bt.getUpgradesFrom());
            if (had != null && had.getType() == bt.getUpgradesFrom()) {
                try {
                    final java.lang.reflect.Method m = Building.class
                        .getDeclaredMethod("setType", BuildingType.class);
                    m.setAccessible(true);
                    m.invoke(had, bt);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(id, e);
                }
            } else {
                this.colony.addBuilding(new ServerBuilding(this.game, this.colony, bt));
            }
            assertSame(id, bt, this.colony.getBuilding(bt).getType());
            this.colony.invalidateCache();
        }

        /** The colony's Sons of Liberty bonus (0, 1 at 50 %, 2 at 100 %). */
        void bonus(int n) {
            setProductionBonus(this.colony, n);
            assertEquals(n, this.colony.getProductionBonus());
        }

        /** What a square makes of a good now (the production cache). */
        int made(Tile t, String good) {
            this.colony.invalidateCache();
            final ProductionInfo pi = this.colony.getColonyTile(t).getProductionInfo();
            int n = 0;
            for (AbstractGoods ag : pi.getProduction()) {
                if (ag.getType() == goods(good)) n += ag.getAmount();
            }
            return n;
        }

        /** The job menu's number: a unit type on a square at a good. */
        int menu(Tile t, String type, String good) {
            return this.colony.getColonyTile(t).getPotentialProduction(goods(good),
                (type == null) ? null : unit(type));
        }
    }

    private static void improve(Tile t, String id) {
        final TileImprovementType tit = levi().getTileImprovementType("model.improvement." + id);
        assertNotNull(id, tit);
        final TileImprovement ti = new TileImprovement(t.getGame(), t, tit, null);
        ti.setTurnsToComplete(0);
        t.add(ti);
    }

    /** The centre's two goods (food and the second) with the rules on and off. */
    private void assertCentre(String what, Rig r, String second, int food, int other,
                              int freecolFood, int freecolOther) {
        rules(true);
        assertEquals(what + ": food", food, r.made(r.centre, "grain"));
        assertEquals(what + ": " + second, other, r.made(r.centre, second));
        assertEquals(what + ": food (menu)", food, r.menu(r.centre, null, "grain"));
        assertEquals(what + ": " + second + " (menu)", other, r.menu(r.centre, null, second));
        levi().setBoolean(GameOptions.ORIGINAL_COLONY_CENTER, false);
        assertEquals(what + ", FreeCol: food", freecolFood, r.made(r.centre, "grain"));
        assertEquals(what + ", FreeCol: " + second, freecolOther, r.made(r.centre, second));
        assertEquals(what + ", FreeCol: food (menu)", freecolFood,
                     r.menu(r.centre, null, "grain"));
        levi().setBoolean(GameOptions.ORIGINAL_COLONY_CENTER, true);
    }


    // The colony centre square (change 1 and 2)

    /**
     * G1, clip 020 #2260 and #134173 (Base 1539-1571, "Sumpfland/Straße/
     * Gepflügt", Sons of Liberty under 50 %): 6 food and 3 sugar, FreeCol
     * 4 + 2.
     */
    public void testG1SwampCentrePlowedWithARoad() {
        final Rig r = new Rig("swamp", "plow", "road");
        r.in("freeColonist", "townHall");
        assertCentre("G1 swamp", r, "sugar", 6, 3, 4, 2);
    }

    /** G2, clip 020 #14817 (Provisorische Kolonie, "Regen- Wald/Straße"): 4 + 2 sugar, FreeCol 2 + 1. */
    public void testG2RainForestCentreWithARoad() {
        final Rig r = new Rig("rainForest", "road");
        r.in("freeColonist", "townHall");
        assertCentre("G2 rain forest", r, "sugar", 4, 2, 2, 1);
    }

    /**
     * G3, clip 020 #123003 (Silver City, "Laub- Wald", COLONY03 0x0B) and
     * dago's Fur City (34 %): 4 food + 3 furs, FreeCol 2 + 2; dago's Fur
     * Town at 100 %: 6 + 5, FreeCol 4 + 4.
     */
    public void testG3BroadleafCentreAt0And100Percent() {
        final Rig r = new Rig("broadleafForest");
        r.in("freeColonist", "townHall");
        assertCentre("G3 broadleaf", r, "furs", 4, 3, 2, 2);
        r.bonus(2);
        assertCentre("G3 broadleaf at 100 %", r, "furs", 6, 5, 4, 4);
    }

    /** G4, dago's Base (#1000; dago2 #13354 "(Flachland) (Straße) (Gepflügt)") at 100 %: 8 corn + 5 cotton, FreeCol 6 + 4. */
    public void testG4PlainsCentrePlowedAt100Percent() {
        final Rig r = new Rig("plains", "plow", "road");
        r.in("freeColonist", "townHall");
        r.bonus(2);
        assertCentre("G4 plains", r, "cotton", 8, 5, 6, 4);
    }

    /** G5, clip008 #4032 (Base 1506-1513, "Nadel- Wald/Straße"): 4 food + 3 furs, the road no furs, FreeCol 2 + 2. */
    public void testG5ConiferCentreWithARoad() {
        final Rig r = new Rig("coniferForest", "road");
        r.in("freeColonist", "townHall");
        assertCentre("G5 conifer", r, "furs", 4, 3, 2, 2);
    }

    /** The other centres of the spec's table (I by the same rule): + 2 food, + 1 of the second good. */
    public void testTheCentreTable() {
        final String[][] table = {
            // type, second good, FreeCol food, FreeCol second
            { "grassland", "tobacco", "3", "3" }, { "prairie", "cotton", "3", "3" },
            { "savannah", "sugar", "3", "3" }, { "marsh", "tobacco", "3", "2" },
            { "tundra", "ore", "3", "2" }, { "mixedForest", "furs", "2", "3" },
            { "tropicalForest", "furs", "2", "2" }, { "wetlandForest", "furs", "2", "2" },
            { "borealForest", "furs", "2", "3" }, { "hills", "ore", "2", "4" },
        };
        for (String[] row : table) {
            final Rig r = new Rig(row[0]);
            r.in("freeColonist", "townHall");
            final int f = Integer.parseInt(row[2]), o = Integer.parseInt(row[3]);
            assertCentre(row[0], r, row[1], f + 2, o + 1, f, o);
        }
    }

    /** The centre's bonus is the colony's own: an unclaimed square and a native settlement's land keep FreeCol's numbers. */
    public void testTheNativesAndThePlannersKeepFreeColsNumbers() {
        final Rig r = new Rig("broadleafForest");
        r.in("freeColonist", "townHall");
        rules(true);
        assertEquals(4, r.made(r.centre, "grain"));
        // The planners' site values (Tile.getPotentialProduction and
        // getMaximumPotential, TentativeColonyPlan): FreeCol's, the same
        // with the rules on and off.
        final Tile far = r.map.getTile(15, 4);
        far.setType(tile("broadleafForest"));
        assertEquals(2, r.centre.getPotentialProduction(goods("grain"), null));
        assertEquals(2, far.getPotentialProduction(goods("grain"), null));
        final int maxOn = far.getMaximumPotential(goods("grain"), null);
        final int centreMaxOn = r.centre.getMaximumPotential(goods("grain"), null);
        rules(false);
        assertEquals(2, r.centre.getPotentialProduction(goods("grain"), null));
        assertEquals(maxOn, far.getMaximumPotential(goods("grain"), null));
        assertEquals(centreMaxOn, r.centre.getMaximumPotential(goods("grain"), null));
        rules(true);
        // A native settlement's production: the same with the rules on and off.
        final Player sioux = r.game.getPlayerByNationId("model.nation.sioux");
        final Tile home = r.map.getTile(14, 4);
        home.setType(tile("broadleafForest"));
        final IndianSettlement is = new ServerIndianSettlement(r.game, sioux, "Camp",
            home, false, null, null);
        sioux.addSettlement(is);
        for (int i = 0; i < 3; i++) {
            final Unit brave = new ServerUnit(r.game, is, sioux,
                levi().getDefaultUnitType(sioux));
            is.addOwnedUnit(brave);
        }
        is.placeSettlement(true);
        assertTrue(is.getTotalProductionOf(goods("grain")) > 0);
        final int[] on = new int[3], off = new int[3];
        final String[] g = { "grain", "furs", "lumber" };
        for (int i = 0; i < g.length; i++) on[i] = is.getTotalProductionOf(goods(g[i]));
        rules(false);
        for (int i = 0; i < g.length; i++) off[i] = is.getTotalProductionOf(goods(g[i]));
        rules(true);
        for (int i = 0; i < g.length; i++) assertEquals(g[i], off[i], on[i]);
    }


    // Lumber (change 3 and 4)

    /**
     * G6, clip 020 #75440 (index row 161, an expert lumberjack on the N
     * square, tropical forest with a road: «Holzfäller 10/10»,
     * «Pelzjäger 4/4») and #93178 (row 200, a master sugar planter
     * there: «Holzfäller 0/6»): the expert 10 (FreeCol 12: the road's 2
     * doubled), a colonist 6, furs 4.
     */
    public void testG6TropicalForestWithARoad() {
        final Rig r = new Rig("plains");
        final Tile n = r.square(Direction.N, "tropicalForest", "road");
        r.on("expertLumberJack", n, "lumber");
        rules(true);
        assertEquals("G6 expert", 10, r.made(n, "lumber"));
        assertEquals("G6 expert (menu)", 10, r.menu(n, "expertLumberJack", "lumber"));
        assertEquals("G6 colonist (menu)", 6, r.menu(n, "masterSugarPlanter", "lumber"));
        assertEquals("G6 colonist furs (menu)", 4, r.menu(n, "masterSugarPlanter", "furs"));
        assertEquals("G6 the expert's furs (menu)", 4, r.menu(n, "expertLumberJack", "furs"));
        levi().setBoolean(GameOptions.ORIGINAL_LUMBER, false);
        assertEquals("G6 expert, FreeCol", 12, r.made(n, "lumber"));
        assertEquals("G6 colonist, FreeCol (menu)", 6,
                     r.menu(n, "masterSugarPlanter", "lumber"));
    }

    /**
     * G7, dago's Fur City S (34 %, no bonus: 14) and Fur Town E (100 %:
     * 22): an expert lumberjack on mixed forest with a road; FreeCol 16
     * and 20.  At 100 % the original counts the bonus twice for lumber
     * (I+), a colonist's 4.
     */
    public void testG7MixedForestWithARoadAt34And100Percent() {
        final Rig r = new Rig("plains");
        final Tile s = r.square(Direction.S, "mixedForest", "road");
        r.on("expertLumberJack", s, "lumber");
        rules(true);
        assertEquals("G7 at 34 %", 14, r.made(s, "lumber"));
        r.bonus(2);
        assertEquals("G7 at 100 %", 22, r.made(s, "lumber"));
        assertEquals("G7 a colonist at 100 % (menu)", 12, r.menu(s, "freeColonist", "lumber"));
        r.bonus(1);
        assertEquals("G7 at 50 %", 18, r.made(s, "lumber"));
        assertEquals("G7 a colonist at 50 % (menu)", 10, r.menu(s, "freeColonist", "lumber"));
        r.bonus(2);
        levi().setBoolean(GameOptions.ORIGINAL_LUMBER, false);
        assertEquals("G7 at 100 %, FreeCol", 20, r.made(s, "lumber"));
        r.bonus(0);
        assertEquals("G7 at 34 %, FreeCol", 16, r.made(s, "lumber"));
    }

    /** dago's Base NE (V, prime timber, conifer with a road, 100 %): 30, the bonus twice and the road once; FreeCol 28. */
    public void testPrimeTimberAt100Percent() {
        final Rig r = new Rig("plains");
        final Tile ne = r.square(Direction.NE, "coniferForest", "road");
        r.resource(ne, "lumber");
        r.on("expertLumberJack", ne, "lumber");
        r.bonus(2);
        rules(true);
        assertEquals(30, r.made(ne, "lumber"));
        levi().setBoolean(GameOptions.ORIGINAL_LUMBER, false);
        assertEquals(28, r.made(ne, "lumber"));
    }

    /**
     * The penalty of the Tories stays FreeCol's for lumber (I: not seen):
     * an expert lumberjack on mixed forest with a road at -1, 6 x 2 - 1 +
     * 2 = 13 (FreeCol 6 x 2 - 1 + 4 = 15).
     */
    public void testTheLumberPenaltyKeepsFreeColsRule() {
        final Rig r = new Rig("plains");
        final Tile s = r.square(Direction.S, "mixedForest", "road");
        r.on("expertLumberJack", s, "lumber");
        rules(true);
        r.bonus(-1);
        assertEquals(13, r.made(s, "lumber"));
        assertEquals("a colonist: 6 - 1 + 2", 7, r.menu(s, "freeColonist", "lumber"));
        levi().setBoolean(GameOptions.ORIGINAL_LUMBER, false);
        assertEquals(15, r.made(s, "lumber"));
    }


    // The expert fisherman (change 5)

    /** A coastal ocean square (FreeCol's coast bonus) worked from the colony with docks. */
    private static Tile coast(Rig r, Direction d) {
        r.build("docks");
        final Tile t = r.square(d, "ocean");
        improve(t, "fishBonusLand");
        r.colony.invalidateCache();
        return t;
    }

    /**
     * G8, clip 020 #46108 (index row 96: «{Fischer} {6}») and #134173 (E
     * 6), a non-expert «Fischer 0/4» (#4086, row 13); dago, three squares
     * 10 at 100 %; clip 048 #3430 6/6 and an indentured servant #56496 4/4:
     * the expert 6 and 10, a colonist 4; FreeCol 7 and 11.
     */
    public void testG8CoastalOcean() {
        final Rig r = new Rig("plains");
        final Tile e = coast(r, Direction.E);
        r.on("expertFisherman", e, "fish");
        rules(true);
        assertEquals("G8 expert", 6, r.made(e, "fish"));
        assertEquals("G8 colonist (menu)", 4, r.menu(e, "freeColonist", "fish"));
        assertEquals("G8 servant (menu, 048 #56496)", 4, r.menu(e, "indenturedServant", "fish"));
        r.bonus(2);
        assertEquals("G8 expert at 100 %", 10, r.made(e, "fish"));
        levi().setBoolean(GameOptions.ORIGINAL_FISHERMAN, false);
        assertEquals("G8 expert at 100 %, FreeCol", 11, r.made(e, "fish"));
        r.bonus(0);
        assertEquals("G8 expert, FreeCol", 7, r.made(e, "fish"));
        assertEquals("G8 colonist, FreeCol (menu)", 4, r.menu(e, "freeColonist", "fish"));
    }

    /**
     * Clip 048 #13896 and #13627 (Base's W square, «Ozean» with a fish):
     * the expert fisherman 12, an expert farmer fishing 7/7; FreeCol 13.
     * The fishery's own extra for experts stays (I: FreeCol's +3 fits).
     */
    public void testTheFisheryOfClip048() {
        final Rig r = new Rig("plains");
        final Tile w = coast(r, Direction.W);
        r.resource(w, "fish");
        r.on("expertFisherman", w, "fish");
        rules(true);
        assertEquals("048 #13896", 12, r.made(w, "fish"));
        assertEquals("048 #13627 (menu)", 7, r.menu(w, "expertFarmer", "fish"));
        levi().setBoolean(GameOptions.ORIGINAL_FISHERMAN, false);
        assertEquals("048 #13896, FreeCol", 13, r.made(w, "fish"));
    }


    // Equal in both (the spec's "equal" rows)

    /**
     * G9, clip 020 #2260 (Grünland, an expert farmer: 5) and dago's Fur
     * Town S (prairie, plowed, a road, an expert farmer at 100 %: 10):
     * the same with the rules on and off.
     */
    public void testG9ExpertFarmer() {
        final Rig r = new Rig("plains");
        final Tile nw = r.square(Direction.NW, "grassland");
        final Tile s = r.square(Direction.S, "prairie", "plow", "road");
        r.on("expertFarmer", nw, "grain");
        r.on("expertFarmer", s, "grain");
        for (boolean on : new boolean[] { true, false }) {
            rules(on);
            r.bonus(0);
            assertEquals("G9 grassland " + on, 5, r.made(nw, "grain"));
            r.bonus(2);
            assertEquals("G9 prairie at 100 % " + on, 10, r.made(s, "grain"));
        }
    }

    /** G10, clip 020 #105689 NE (hills and a road, a colonist's ore: 5) and dago SE (an expert ore miner at 100 %: 14). */
    public void testG10Ore() {
        final Rig r = new Rig("plains");
        final Tile ne = r.square(Direction.NE, "hills", "road");
        r.on("expertOreMiner", ne, "ore");
        for (boolean on : new boolean[] { true, false }) {
            rules(on);
            r.bonus(0);
            assertEquals("G10 colonist " + on, 5, r.menu(ne, "freeColonist", "ore"));
            r.bonus(2);
            assertEquals("G10 expert at 100 % " + on, 14, r.made(ne, "ore"));
        }
    }


    // Roads and silver (change 7)

    /**
     * G15 (the manual, p. 27): mountains with a road, a colonist's silver
     * 1 without a deposit (FreeCol 2), 4 with one (both); clip 047 #5375:
     * an expert silver miner on Silver City's deposit with a road, 8, and
     * the ore there 5.  Clip 048 #2902, #4083, #27916: «Erz 5, Silber 1»
     * with no deposit fits too (I: the menu's square is not known).
     */
    public void testG15RoadsAndSilver() {
        final Rig r = new Rig("plains");
        final Tile w = r.square(Direction.W, "mountains", "road");
        r.on("freeColonist", w, "silver");
        rules(true);
        assertEquals("G15 no deposit", 1, r.made(w, "silver"));
        assertEquals("G15 no deposit (menu)", 1, r.menu(w, "freeColonist", "silver"));
        assertEquals("the ore still gets the road", 5, r.menu(w, "freeColonist", "ore"));
        levi().setBoolean(GameOptions.ORIGINAL_ROAD_SILVER, false);
        assertEquals("G15 no deposit, FreeCol", 2, r.made(w, "silver"));
        levi().setBoolean(GameOptions.ORIGINAL_ROAD_SILVER, true);
        r.resource(w, "silver");
        assertEquals("G15 deposit", 4, r.made(w, "silver"));
        assertEquals("047 #5375 expert", 8, r.menu(w, "expertSilverMiner", "silver"));
        levi().setBoolean(GameOptions.ORIGINAL_ROAD_SILVER, false);
        assertEquals("G15 deposit, FreeCol", 4, r.made(w, "silver"));
    }

    /**
     * A planned road (the planners' maximum potential, which counts a
     * bonus of 2) follows the same rule: 1 + 2 without the road's silver,
     * FreeCol 1 + 2 + 1.
     */
    public void testAPlannedRoadAndSilver() {
        final Rig r = new Rig("plains");
        final Tile w = r.square(Direction.W, "mountains");
        rules(true);
        assertEquals(3, w.getMaximumPotential(goods("silver"), unit("freeColonist")));
        levi().setBoolean(GameOptions.ORIGINAL_ROAD_SILVER, false);
        assertEquals(4, w.getMaximumPotential(goods("silver"), unit("freeColonist")));
    }


    // Horses (change 6)

    private static int horsesMade(Rig r) {
        r.colony.invalidateCache();
        return r.colony.getNetProductionOf(goods("horses"));
    }

    /**
     * G11, clip 020 #14817 and COLONY08 -&gt; COLONY03 (Silver City, one
     * colonist, the broadleaf centre's 4 food, 50 horses: food 4 -&gt; 5,
     * horses 54 -&gt; 55): 1 horse bred, 1 food stored; FreeCol none (its
     * centre makes 2).
     */
    public void testG11OneColonistAndFiftyHorses() {
        final Rig r = new Rig("broadleafForest");
        r.in("freeColonist", "townHall");
        r.colony.addGoods(goods("horses"), 50);
        rules(true);
        assertEquals(1, horsesMade(r));
        assertEquals(1, r.colony.getNetProductionOf(goods("food")));
        rules(false);
        assertEquals(0, horsesMade(r));
        assertEquals(0, r.colony.getNetProductionOf(goods("food")));
    }

    /**
     * G12, dago's Fur City (a surplus of 1, the horses take 1; the band's
     * «29» used) and Fur Town (21: 11 bred, 10 stored; stables, 200
     * horses): half the surplus rounded up; FreeCol 0 and 10.
     */
    public void testG12TheHorsesHalfRoundedUp() {
        // A surplus of 1: the plains centre's 5 food, two colonists eat 4.
        final Rig r = new Rig("plains");
        r.in("freeColonist", "townHall");
        r.in("freeColonist", "carpenterHouse");
        r.colony.addGoods(goods("horses"), 50);
        rules(true);
        assertEquals(1, horsesMade(r));
        levi().setBoolean(GameOptions.ORIGINAL_HORSE_FOOD, false);
        assertEquals(0, horsesMade(r));
        // A surplus of 21: the plowed centre's 6, two expert farmers on
        // plowed plains 8 each, a colonist on plains 5; three eat 6.
        final Rig t = new Rig("plains", "plow");
        t.on("expertFarmer", t.square(Direction.N, "plains", "plow"), "grain");
        t.on("expertFarmer", t.square(Direction.E, "plains", "plow"), "grain");
        t.on("freeColonist", t.square(Direction.S, "plains"), "grain");
        t.build("stables");
        t.build("warehouse");
        t.build("warehouseExpansion");
        t.colony.addGoods(goods("horses"), 200);
        rules(true);
        assertEquals(11, horsesMade(t));
        assertEquals("10 stored", 10, t.colony.getNetProductionOf(goods("food")));
        levi().setBoolean(GameOptions.ORIGINAL_HORSE_FOOD, false);
        assertEquals(10, horsesMade(t));
        assertEquals(11, t.colony.getNetProductionOf(goods("food")));
    }

    /** G13, dago's Ställe rows: 154 / 239 / 200 horses breed at most 14 / 20 / 16 (the same in both). */
    public void testG13TheStablesPotential() {
        final int[][] cases = { { 154, 14 }, { 239, 20 }, { 200, 16 } };
        for (boolean on : new boolean[] { true, false }) {
            for (int[] c : cases) {
                rules(on);
                final Rig r = new Rig("plains");
                r.in("freeColonist", "townHall");
                r.build("stables");
                r.build("warehouse");
                r.build("warehouseExpansion");
                r.colony.addGoods(goods("horses"), c[0]);
                r.colony.invalidateCache();
                final Building stables = r.colony.getBuilding(
                    levi().getBuildingType("model.building.stables"));
                // The potential: the maximum, or the production when the
                // food is not short (FreeCol lists the maximum only then).
                final ProductionInfo pi = r.colony.getProductionInfo(stables);
                int max = 0;
                for (AbstractGoods ag : pi.getMaximumProduction()) {
                    if (ag.getType() == goods("horses")) max = Math.max(max, ag.getAmount());
                }
                for (AbstractGoods ag : pi.getProduction()) {
                    if (ag.getType() == goods("horses")) max = Math.max(max, ag.getAmount());
                }
                assertEquals(c[0] + " horses " + on, c[1], max);
            }
        }
    }


    /**
     * A colony's new turn on a real server game of the levi rules
     * ({@code ServerColony.csNewTurn}, what the stock shows after the
     * turn): G11's Silver City (COLONY08 -&gt; COLONY03: food +1, horses 54
     * -&gt; 55, here 50 -&gt; 51) and G12's surplus of 21 (11 horses bred,
     * 10 food stored); FreeCol's with the rules off (0 / 0 and 10 / 11).
     */
    public void testANewTurnOnTheServer() {
        try {
            for (boolean on : new boolean[] { true, false }) {
                rules(on);
                // G11: one colonist in the town hall, the broadleaf centre.
                ServerColony c = serverColony("broadleafForest");
                serverUnit(c, "freeColonist", "townHall");
                c.addGoods(goods("horses"), 50);
                newTurn(c);
                assertEquals("G11 horses " + on, (on) ? 51 : 50, c.getGoodsCount(goods("horses")));
                assertEquals("G11 food " + on, (on) ? 1 : 0, c.getGoodsCount(goods("food")));
                // G12: the plowed plains centre, two expert farmers on
                // plowed plains, a colonist on plains; stables, 200 horses.
                c = serverColony("plains");
                improve(c.getTile(), "plow");
                final Tile n = c.getTile().getNeighbourOrNull(Direction.N);
                final Tile e = c.getTile().getNeighbourOrNull(Direction.E);
                final Tile s = c.getTile().getNeighbourOrNull(Direction.S);
                improve(n, "plow");
                improve(e, "plow");
                serverWorker(c, "expertFarmer", n);
                serverWorker(c, "expertFarmer", e);
                serverWorker(c, "freeColonist", s);
                for (String b : new String[] { "country", "depot", "warehouse" }) {
                    final Building had = c.getBuilding(levi().getBuildingType("model.building." + b));
                    upgradeInPlace(had);
                }
                c.addGoods(goods("horses"), 200);
                c.invalidateCache();
                // The surplus 21 (on; FreeCol's centre: 19): 11 (9) bred.
                newTurn(c);
                assertEquals("G12 horses " + on, (on) ? 211 : 209, c.getGoodsCount(goods("horses")));
                assertEquals("G12 food " + on, 10, c.getGoodsCount(goods("food")));
            }
        } finally {
            net.sf.freecol.server.ServerTestHelper.stopServerGame();
        }
    }

    /** A colony on a fresh server game of the levi rules, on a centre of a type. */
    private ServerColony serverColony(String centreType) {
        levi().applyDifficultyLevel("model.difficulty.veryEasy");
        final Game g = getStandardGame(LEVI);
        final Map m = new MapBuilder(g).setBaseTileType(tile("plains")).build();
        final Game game = net.sf.freecol.server.ServerTestHelper.startServerGame(m, levi());
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final Tile t = game.getMap().getTile(5, 8);
        t.setType(tile(centreType));
        final ServerColony c = new ServerColony(game, dutch, "Base", t);
        dutch.addSettlement(c);
        c.placeSettlement(true);
        dutch.invalidateCanSeeTiles();
        c.setBuildQueue(new java.util.ArrayList<>());
        return c;
    }

    private static void serverUnit(Colony c, String type, String building) {
        final Unit u = new ServerUnit(c.getGame(), c.getTile(), c.getOwner(), unit(type));
        final Building b = c.getBuilding(levi().getBuildingType("model.building." + building));
        assertTrue(u.setLocation(b));
        u.changeWorkType(b.getAvailableProductionTypes(false).get(0).getOutputs()
                         .findFirst().get().getType());
        c.invalidateCache();
    }

    private static void serverWorker(Colony c, String type, Tile t) {
        final Unit u = new ServerUnit(c.getGame(), c.getTile(), c.getOwner(), unit(type));
        assertTrue(u.setLocation(c.getColonyTile(t)));
        u.changeWorkType(goods("grain"));
        c.invalidateCache();
    }

    private static void upgradeInPlace(Building b) {
        try {
            final java.lang.reflect.Method m = Building.class
                .getDeclaredMethod("setType", BuildingType.class);
            m.setAccessible(true);
            m.invoke(b, b.getType().getUpgradesTo());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        b.getColony().invalidateCache();
    }

    private static void newTurn(ServerColony c) {
        c.invalidateCache();
        c.csNewTurn(new java.util.Random(1), new net.sf.freecol.common.util.LogBuilder(0),
                    new net.sf.freecol.common.networking.ChangeSet());
    }


    // Buildings (equal, G14)

    /**
     * G14, dago J2 #15426 and #8890, #6047 at 100 %: a house 5 (rum),
     * the lumber mill 10, a factory 12 (cigars), the arsenal 24 for an
     * expert and 12 for a colonist, the cathedral 10: FreeCol's building
     * model, the same in both.
     */
    public void testG14BuildingsAt100Percent() {
        for (boolean on : new boolean[] { true, false }) {
            rules(on);
            final Rig r = new Rig("plains");
            r.in("freeColonist", "townHall");
            r.build("lumberMill");
            r.build("tobacconistShop");
            r.build("cigarFactory");
            r.build("armory");
            r.build("magazine");
            r.build("arsenal");
            r.build("church");
            r.build("cathedral");
            r.bonus(2);
            assertEquals("house " + on, 5, potential(r, "distillerHouse", "rum", "freeColonist"));
            assertEquals("lumber mill " + on, 10,
                         potential(r, "lumberMill", "hammers", "freeColonist"));
            assertEquals("factory " + on, 12,
                         potential(r, "cigarFactory", "cigars", "freeColonist"));
            assertEquals("arsenal, expert " + on, 24,
                         potential(r, "arsenal", "muskets", "masterGunsmith"));
            assertEquals("arsenal, colonist " + on, 12,
                         potential(r, "arsenal", "muskets", "freeColonist"));
            assertEquals("cathedral, colonist " + on, 10,
                         potential(r, "cathedral", "crosses", "freeColonist"));
        }
    }

    private static int potential(Rig r, String building, String good, String type) {
        final Building b = r.colony.getBuilding(levi().getBuildingType("model.building." + building));
        assertNotNull(building, b);
        return b.getPotentialProduction(goods(good), unit(type));
    }


    // The rules and the other rules

    /** The classic rules keep FreeCol's numbers: none of the options, the same states. */
    public void testTheClassicRulesKeepFreeColsNumbers() {
        for (String id : Specification.ORIGINAL_PRODUCTION_OPTIONS) {
            assertFalse(id, OriginalProduction.on(spec("classic"), id));
            assertFalse(id, OriginalProduction.on(spec("freecol"), id));
            assertTrue(id, OriginalProduction.on(levi(), id));
        }
        assertFalse(OriginalProduction.on(null, GameOptions.ORIGINAL_LUMBER));
    }

    /** The rules' pure parts. */
    public void testThePureRules() {
        assertEquals(1, OriginalProduction.horseFood(levi(), 0.5f));
        assertEquals(11, OriginalProduction.horseFood(levi(), 10.5f));
        assertEquals(10, OriginalProduction.horseFood(levi(), 10.0f));
        assertEquals(0, OriginalProduction.horseFood(spec("classic"), 0.5f));
        assertEquals(10, OriginalProduction.horseFood(spec("classic"), 10.5f));
        assertEquals(2.0f, OriginalProduction.rebelFactor(goods("lumber"), 2), 0f);
        assertEquals(1.0f, OriginalProduction.rebelFactor(goods("lumber"), -1), 0f);
        assertEquals(1.0f, OriginalProduction.rebelFactor(goods("ore"), 2), 0f);
        final TileImprovementType road = levi().getTileImprovementType("model.improvement.road");
        final TileImprovementType river = levi().getTileImprovementType("model.improvement.river");
        assertFalse(OriginalProduction.expertBonusDoubled(levi(), road, goods("lumber")));
        assertTrue(OriginalProduction.expertBonusDoubled(levi(), road, goods("ore")));
        assertTrue(OriginalProduction.expertBonusDoubled(levi(), river, goods("lumber")));
        assertTrue(OriginalProduction.expertBonusDoubled(spec("classic"),
            spec("classic").getTileImprovementType("model.improvement.road"),
            spec("classic").getGoodsType("model.goods.lumber")));
        final List<Modifier> grain = OriginalProduction.centerModifiers(goods("grain"),
            tile("plains")).collect(java.util.stream.Collectors.toList());
        assertEquals(1, grain.size());
        assertEquals(2f, grain.get(0).getValue(), 0f);
        assertEquals(Modifier.COLONYTILE_PRODUCTION_INDEX - 1, grain.get(0).getModifierIndex());
        assertEquals(1f, OriginalProduction.centerModifiers(goods("cotton"), tile("plains"))
                     .findFirst().get().getValue(), 0f);
        assertEquals("nothing for a good the square does not make", 0,
                     OriginalProduction.centerModifiers(goods("furs"), tile("plains")).count());
        assertTrue(OriginalProduction.isExpertFishing(unit("expertFisherman"), goods("fish"),
                                                      tile("ocean")));
        assertFalse(OriginalProduction.isExpertFishing(unit("freeColonist"), goods("fish"),
                                                       tile("ocean")));
        assertFalse(OriginalProduction.isExpertFishing(unit("expertFarmer"), goods("grain"),
                                                       tile("plains")));
    }
}
