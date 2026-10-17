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

import static net.sf.freecol.common.util.CollectionUtils.any;
import static net.sf.freecol.common.util.CollectionUtils.toList;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;

import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.i18n.NameCache;
import net.sf.freecol.common.io.FreeColModFile;
import net.sf.freecol.common.io.FreeColRules;
import net.sf.freecol.common.option.GameOptions;
import net.sf.freecol.common.option.Option;
import net.sf.freecol.common.option.OptionGroup;
import net.sf.freecol.server.model.ServerBuilding;
import net.sf.freecol.server.model.ServerColony;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * The "levi" rules (data/rules/levi): the classic rules with Roger's
 * choices from the rules table (Regeln-Auswahl.md, "Rogers Wahl") and his
 * house rules.  One test per row that plays as "freecol", one for the
 * rows that stay "classic", one per house rule.
 */
public class LeviRulesTest extends FreeColTestCase {

    private static final String LEVI = "levi";

    /** The game options levi changes against classic, and their values. */
    private static final Map<String, Object> CHANGED = new HashMap<>();
    static {
        CHANGED.put(GameOptions.ENHANCED_MISSIONARIES, Boolean.TRUE);      // row 5
        CHANGED.put(GameOptions.CAPTURE_UNITS_UNDER_REPAIR, Boolean.TRUE); // row 8
        CHANGED.put(GameOptions.FOUND_COLONY_DURING_REBELLION, Boolean.TRUE); // row 11
        CHANGED.put(GameOptions.SAVE_PRODUCTION_OVERFLOW, Boolean.FALSE);  // row 12
        CHANGED.put(GameOptions.ALLOW_STUDENT_SELECTION, Boolean.TRUE);    // row 13
        CHANGED.put(GameOptions.TURNS_TO_SAIL, "2");                       // the original's (W13)
        CHANGED.put(GameOptions.FORTIFY_KEEPS_MOVES, Boolean.TRUE);        // the original's (J2)
        CHANGED.put(GameOptions.FORTIFY_DECLARES_WAR, Boolean.TRUE);       // the original's (B3)
        CHANGED.put(GameOptions.EUROPE_ARRIVAL_DOCKS, Boolean.TRUE);       // the original's (T3)
        CHANGED.put(GameOptions.ORIGINAL_DROP_JOBS, Boolean.TRUE);         // the original's (U, R2/R3)
        CHANGED.put(GameOptions.ORIGINAL_BUILD_PRICE, Boolean.TRUE);       // the original's (V, R1)
        CHANGED.put(GameOptions.ORIGINAL_BUILD_COMPLETION, Boolean.TRUE);  // the original's (V, R4)
        CHANGED.put(GameOptions.ORIGINAL_COLONY_REPORTS, Boolean.TRUE);    // the original's (W, M4.11)
        CHANGED.put(GameOptions.DOCKING_ENDS_MOVES, Boolean.TRUE);         // the original's (W, R5)
        CHANGED.put(GameOptions.ORIGINAL_COLONY_CENTER, Boolean.TRUE);     // the original's (X, 1-2)
        CHANGED.put(GameOptions.ORIGINAL_LUMBER, Boolean.TRUE);            // the original's (X, 3-4)
        CHANGED.put(GameOptions.ORIGINAL_FISHERMAN, Boolean.TRUE);         // the original's (X, 5)
        CHANGED.put(GameOptions.ORIGINAL_HORSE_FOOD, Boolean.TRUE);        // the original's (X, 6)
        CHANGED.put(GameOptions.ORIGINAL_ROAD_SILVER, Boolean.TRUE);       // the original's (X, 7)
        CHANGED.put(GameOptions.ORIGINAL_TORY_PENALTY, Boolean.TRUE);      // the original's (fixer B, must 7)
        CHANGED.put(GameOptions.ORIGINAL_FATHER_OFFER, Boolean.TRUE);      // the original's (fixer B, must 10)
        CHANGED.put(GameOptions.ORIGINAL_TRIBUTE, Boolean.TRUE);          // the original's (Z2.2)
        CHANGED.put(GameOptions.ORIGINAL_NATIVE_WAR, Boolean.TRUE);       // the original's (Z2.3)
        CHANGED.put(GameOptions.CANCEL_KEEPS_MOVE, Boolean.TRUE);          // house rules
        CHANGED.put(GameOptions.REVENGE_MODE, Boolean.FALSE);
        CHANGED.put(GameOptions.LAST_COLONY_DEFEAT, Boolean.TRUE);
        CHANGED.put(GameOptions.VICTORY_DEFEAT_REF, Boolean.FALSE);
        CHANGED.put(GameOptions.VICTORY_DEFEAT_EUROPEANS, Boolean.FALSE);
    }


    /**
     * Collect the values of a group's options.  Booleans as they are,
     * all others by their text (a unit option's value has no equals).
     */
    private static void collect(OptionGroup group, Map<String, Object> out) {
        for (Option<?> o : group.getOptions()) {
            if (o instanceof OptionGroup) {
                collect((OptionGroup)o, out);
            } else {
                final Object v = o.getValue();
                out.put(o.getId(), (v instanceof Boolean) ? v
                    : String.valueOf(v));
            }
        }
    }

    private static Map<String, Object> gameOptions(Specification spec) {
        Map<String, Object> ret = new HashMap<>();
        collect(spec.getGameOptions(), ret);
        return ret;
    }

    /** The {@code extends} of a rules file's specification. */
    private static String specExtends(FreeColModFile rules) throws Exception {
        try (InputStream in = rules.getSpecificationInputStream()) {
            XMLStreamReader xr = XMLInputFactory.newInstance()
                .createXMLStreamReader(in);
            xr.nextTag();
            return xr.getAttributeValue(null, "extends");
        }
    }

    private static float minimumColonySize(Specification spec, String id) {
        return spec.getBuildingType(id).apply(0f, new Turn(1),
                                             Modifier.MINIMUM_COLONY_SIZE);
    }


    public void testLeviRulesFile() throws Exception {
        final FreeColModFile levi = FreeColRules.getFreeColRulesFile(LEVI);
        assertNotNull("data/rules/levi is a ruleset", levi);
        assertEquals("classic", levi.getParent());
        assertEquals("mod.xml names the parent the specification extends",
                     specExtends(levi), levi.getParent());
        assertEquals(LEVI, spec(LEVI).getId());
        assertEquals("Levi", Messages.getName("mod.levi"));
    }

    /** Row 1: no stockade lock, on any of the three walls. */
    public void testRow1NoStockadeLock() {
        for (String id : new String[] { "model.building.stockade",
                                        "model.building.fort",
                                        "model.building.fortress" }) {
            assertEquals("classic locks with the " + id,
                         3f, minimumColonySize(spec("classic"), id));
            assertEquals("levi does not lock with the " + id,
                         0f, minimumColonySize(spec(LEVI), id));
        }

        // A colony of one with a fortress may lose its last colonist.
        Colony colony = colonyOfOne(LEVI);
        for (String id : new String[] { "model.building.stockade",
                                        "model.building.fort",
                                        "model.building.fortress" }) {
            colony.addBuilding(new ServerBuilding(colony.getGame(), colony,
                colony.getSpecification().getBuildingType(id)));
        }
        assertTrue(colony.hasStockade());
        assertEquals(1, colony.getUnitCount());
        assertTrue("levi: the last colonist may leave",
                   colony.canReducePopulation());
        assertNull(colony.getReducePopulationMessage());

        // The same colony in classic is locked.
        colony = colonyOfOne("classic");
        colony.addBuilding(new ServerBuilding(colony.getGame(), colony,
            colony.getSpecification().getBuildingType("model.building.stockade")));
        assertFalse("classic: the stockade locks",
                    colony.canReducePopulation());
        assertNotNull(colony.getReducePopulationMessage());
    }

    /**
     * A Dutch colony of one free colonist in a new game of the given
     * rules, everything of those rules (the test builders take the
     * freecol rules' types).
     */
    private static Colony colonyOfOne(String rules) {
        final Game game = getStandardGame(rules);
        final Specification spec = game.getSpecification();
        game.changeMap(getTestMap(spec.getTileType("model.tile.plains"), true));
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final Tile tile = game.getMap().getTile(5, 8);
        final Colony colony = new ServerColony(game, dutch, "Test", tile);
        dutch.addSettlement(colony);
        colony.placeSettlement(true);
        new ServerUnit(game, tile, dutch, spec.getDefaultUnitType())
            .setLocation(colony);
        return colony;
    }

    public void testRow5EnhancedMissionaries() {
        assertFalse(spec("classic").getBoolean(GameOptions.ENHANCED_MISSIONARIES));
        assertTrue(spec(LEVI).getBoolean(GameOptions.ENHANCED_MISSIONARIES));
    }

    public void testRow8CaptureUnitsUnderRepair() {
        assertFalse(spec("classic").getBoolean(GameOptions.CAPTURE_UNITS_UNDER_REPAIR));
        assertTrue(spec(LEVI).getBoolean(GameOptions.CAPTURE_UNITS_UNDER_REPAIR));
    }

    public void testRow11FoundColonyDuringRebellion() {
        assertFalse(spec("classic").getBoolean(GameOptions.FOUND_COLONY_DURING_REBELLION));
        assertTrue(spec(LEVI).getBoolean(GameOptions.FOUND_COLONY_DURING_REBELLION));
    }

    public void testRow12NoProductionOverflow() {
        assertTrue(spec("classic").getBoolean(GameOptions.SAVE_PRODUCTION_OVERFLOW));
        assertFalse(spec(LEVI).getBoolean(GameOptions.SAVE_PRODUCTION_OVERFLOW));
    }

    public void testRow13StudentSelection() {
        assertFalse(spec("classic").getBoolean(GameOptions.ALLOW_STUDENT_SELECTION));
        assertTrue(spec(LEVI).getBoolean(GameOptions.ALLOW_STUDENT_SELECTION));
    }

    /** Row 17: Pocahontas also lifts the mission bans, as in freecol. */
    public void testRow17Pocahontas() {
        final String id = "model.foundingFather.pocahontas";
        final String ban = "model.event.resetBannedMissions";
        assertFalse(any(spec("classic").getFoundingFather(id).getEvents(),
                        e -> ban.equals(e.getId())));
        for (String rules : new String[] { LEVI, "freecol" }) {
            FoundingFather pocahontas = spec(rules).getFoundingFather(id);
            assertTrue(rules, any(pocahontas.getEvents(),
                                  e -> ban.equals(e.getId())));
            assertTrue(rules, any(pocahontas.getEvents(),
                                  e -> "model.event.resetNativeAlarm".equals(e.getId())));
            assertEquals(rules, 50f, pocahontas.apply(100f, new Turn(1),
                    Modifier.NATIVE_ALARM_MODIFIER), 0.001f);
        }
    }

    /**
     * Every other game option and every difficulty option plays as
     * classic (rows 2-4, 6, 7, 9, 10, 14, 19 and all the rest), and the
     * horses (row 15), Coronado (row 16) and the REF's sail (row 18) are
     * classic's.
     */
    public void testOtherRowsAreClassic() {
        final Specification classic = spec("classic");
        final Specification levi = spec(LEVI);
        final Map<String, Object> c = gameOptions(classic);
        final Map<String, Object> l = gameOptions(levi);
        assertEquals(new TreeSet<>(c.keySet()), new TreeSet<>(l.keySet()));
        for (String id : c.keySet()) {
            if (CHANGED.containsKey(id)) {
                assertEquals(id, CHANGED.get(id), l.get(id));
                assertFalse(id + " differs from classic",
                            Objects.equals(c.get(id), l.get(id)));
            } else {
                assertEquals(id, c.get(id), l.get(id));
            }
        }
        assertFalse(levi.getBoolean(GameOptions.AMPHIBIOUS_MOVES));       // 2
        assertFalse(levi.getBoolean(GameOptions.EMPTY_TRADERS));          // 3
        assertFalse(levi.getBoolean(GameOptions.EXPLORATION_POINTS));     // 4
        assertTrue(levi.getBoolean(GameOptions.TELEPORT_REF));            // 6
        assertTrue(levi.getBoolean(GameOptions.BELL_ACCUMULATION_CAPPED)); // 7
        assertTrue(levi.getBoolean(GameOptions.CUSTOM_IGNORE_BOYCOTT));   // 9
        assertTrue(levi.getBoolean(GameOptions.DISEMBARK_IN_COLONY));     // 10
        assertTrue(levi.getBoolean(GameOptions.CLAIM_ALL_TILES));         // 14

        for (OptionGroup level : classic.getDifficultyLevels()) {
            Map<String, Object> cl = new HashMap<>(), ll = new HashMap<>();
            collect(level, cl);
            collect(levi.getDifficultyOptionGroup(level.getId()), ll);
            assertEquals(level.getId(), cl, ll);                          // 19
        }

        // 15: horses breed from any food surplus.
        assertEquals(classic.getPrimaryFoodType().getId(),
                     levi.getGoodsType("model.goods.horses").getInputType().getId());
        assertEquals("model.goods.grain", spec("freecol")
                     .getGoodsType("model.goods.horses").getInputType().getId());
        // 16: Coronado shows the colonies once.
        final String coronado = "model.foundingFather.franciscoDeCoronado";
        assertFalse(levi.getFoundingFather(coronado)
                    .hasAbility(Ability.SEE_ALL_COLONIES));
        assertTrue(spec("freecol").getFoundingFather(coronado)
                   .hasAbility(Ability.SEE_ALL_COLONIES));
        // 18: the REF sails as long as the others.
        for (EuropeanNationType ref : levi.getREFNationTypes()) {
            assertEquals(ref.getId(), toList(classic.getNationType(ref.getId())
                    .getModifiers(Modifier.SAIL_HIGH_SEAS)).size(),
                toList(ref.getModifiers(Modifier.SAIL_HIGH_SEAS)).size());
        }
    }

    /**
     * The house-rule options: on in levi, off in the other rules, which
     * get them with FreeCol's behaviour (Specification.fixGameOptions).
     */
    public void testHouseRuleOptions() {
        for (String rules : new String[] { "classic", "freecol" }) {
            Specification spec = spec(rules);
            assertFalse(rules, spec.getBoolean(GameOptions.CANCEL_KEEPS_MOVE));
            assertTrue(rules, spec.getBoolean(GameOptions.REVENGE_MODE));
            assertFalse(rules, spec.getBoolean(GameOptions.LAST_COLONY_DEFEAT));
            assertTrue(rules, spec.getBoolean(GameOptions.VICTORY_DEFEAT_REF));
        }
        Specification levi = spec(LEVI);
        assertTrue(levi.getBoolean(GameOptions.CANCEL_KEEPS_MOVE));
        assertFalse("no revenge mode", levi.getBoolean(GameOptions.REVENGE_MODE));
        assertTrue(levi.getBoolean(GameOptions.LAST_COLONY_DEFEAT));
        // Open end after independence: no victory, and no founding
        // fathers after the declaration, as FreeCol.
        assertFalse(levi.getBoolean(GameOptions.VICTORY_DEFEAT_REF));
        assertFalse(levi.getBoolean(GameOptions.VICTORY_DEFEAT_EUROPEANS));
        assertFalse(levi.getBoolean(GameOptions.VICTORY_DEFEAT_HUMANS));
        assertFalse(levi.getBoolean(GameOptions.CONTINUE_FOUNDING_FATHER_RECRUITMENT));
        // The house rules sit where the game options dialog shows them.
        assertNotNull(levi.getOptionGroup(GameOptions.GAMEOPTIONS_MAP)
                      .getOption(GameOptions.CANCEL_KEEPS_MOVE));
        assertNotNull(levi.getOptionGroup(GameOptions.GAMEOPTIONS_VICTORY_CONDITIONS)
                      .getOption(GameOptions.REVENGE_MODE));
        assertNotNull(spec("classic").getOptionGroup(GameOptions.GAMEOPTIONS_VICTORY_CONDITIONS)
                      .getOption(GameOptions.LAST_COLONY_DEFEAT));
    }

    /**
     * The original's sailing time (W13): a levi ship takes two turns
     * between Europe and the New World (landfall 1503 -&gt; 1505, clip008
     * 1513 -&gt; 1515 and 1508 -&gt; 1510), the classic and freecol rules
     * three; Magellan's -1 still applies.
     */
    public void testSailingTime() {
        assertEquals(2, spec(LEVI).getInteger(GameOptions.TURNS_TO_SAIL));
        assertEquals(3, spec("classic").getInteger(GameOptions.TURNS_TO_SAIL));
        assertEquals(3, spec("freecol").getInteger(GameOptions.TURNS_TO_SAIL));
        final Game game = getStandardGame(LEVI);
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final Unit ship = new ServerUnit(game, dutch.getEurope(), dutch,
            game.getSpecification().getUnitType("model.unit.merchantman"));
        assertEquals(2, ship.getSailTurns());
        dutch.addFather(game.getSpecification()
            .getFoundingFather("model.foundingFather.ferdinandMagellan"));
        assertEquals(1, ship.getSailTurns());
    }

    /**
     * The original's arrival in Europe (T3, Roger's screenshots of
     * 2026-10-14): the passengers go onto the dock in levi, not in classic
     * and freecol (which get the option from Specification.fixGameOptions),
     * and a levi save from before the rule gets it too; it sits where the
     * game options dialog shows it.  The server's side:
     * {@code ServerUnitTest.testArrivalInEuropePutsThePassengersOnTheDock}.
     */
    public void testEuropeArrivalDocks() {
        assertTrue(spec(LEVI).getBoolean(GameOptions.EUROPE_ARRIVAL_DOCKS));
        assertFalse(spec("classic").getBoolean(GameOptions.EUROPE_ARRIVAL_DOCKS));
        assertFalse(spec("freecol").getBoolean(GameOptions.EUROPE_ARRIVAL_DOCKS));
        assertNotNull(spec(LEVI).getOptionGroup(GameOptions.GAMEOPTIONS_MAP)
                      .getOption(GameOptions.EUROPE_ARRIVAL_DOCKS));
        assertEquals("Arrival in Europe: all onto the dock",
                     Messages.getName(GameOptions.EUROPE_ARRIVAL_DOCKS));
        // A levi save from before the rule plays it; any other does not.
        assertTrue(Specification.europeArrivalDocksDefault(LEVI));
        assertFalse(Specification.europeArrivalDocksDefault("classic"));
        assertFalse(Specification.europeArrivalDocksDefault("freecol"));
    }

    /**
     * The original's jobs after a move on the colony screen (part U, M4.8
     * R2/R3): on in levi, off in classic and freecol (which get the option
     * from Specification.fixGameOptions), a levi save from before the rule
     * gets it; in the colony group.  The screen's side:
     * {@code ClassicColonyMovesTest.testBuildingToTileMakesAFarmer},
     * {@code testTileToTileKeepsTheWork}.
     */
    public void testOriginalDropJobs() {
        assertTrue(spec(LEVI).getBoolean(GameOptions.ORIGINAL_DROP_JOBS));
        assertFalse(spec("classic").getBoolean(GameOptions.ORIGINAL_DROP_JOBS));
        assertFalse(spec("freecol").getBoolean(GameOptions.ORIGINAL_DROP_JOBS));
        assertNotNull(spec(LEVI).getOptionGroup(GameOptions.GAMEOPTIONS_COLONY)
                      .getOption(GameOptions.ORIGINAL_DROP_JOBS));
        assertEquals("Moving colonists: the original jobs",
                     Messages.getName(GameOptions.ORIGINAL_DROP_JOBS));
        assertTrue(Specification.originalDropJobsDefault(LEVI));
        assertFalse(Specification.originalDropJobsDefault("classic"));
        assertFalse(Specification.originalDropJobsDefault("freecol"));
    }

    /**
     * The original's build price and completed build (part V, M4.10 R1 and
     * R4): on in levi, off in classic and freecol (which get the options
     * from Specification.fixGameOptions), a levi save from before the
     * rules gets them; in the colony group, with their names.  The rules'
     * effects: {@code ClassicColonyBuildTest}.
     */
    public void testOriginalBuildRules() {
        for (String id : new String[] { GameOptions.ORIGINAL_BUILD_PRICE,
                                        GameOptions.ORIGINAL_BUILD_COMPLETION }) {
            assertTrue(id, spec(LEVI).getBoolean(id));
            assertFalse(id, spec("classic").getBoolean(id));
            assertFalse(id, spec("freecol").getBoolean(id));
            assertNotNull(id, spec(LEVI).getOptionGroup(GameOptions.GAMEOPTIONS_COLONY)
                          .getOption(id));
        }
        assertEquals("Buying a build: the original price",
                     Messages.getName(GameOptions.ORIGINAL_BUILD_PRICE));
        assertEquals("Finished buildings stay the current build",
                     Messages.getName(GameOptions.ORIGINAL_BUILD_COMPLETION));
        assertTrue(Specification.originalBuildDefault(LEVI));
        assertFalse(Specification.originalBuildDefault("classic"));
        assertFalse(Specification.originalBuildDefault("freecol"));
    }

    /**
     * The original's colony reports and docking (part W, M4.11 and M4.12
     * rule R5): on in levi, off in classic and freecol (which get the
     * options from Specification.fixGameOptions); in the colony group,
     * with their names.  The rules' effects: {@code ServerColonyTest},
     * {@code ServerUnitTest}, {@code ClassicColonyReportsTest}.
     */
    public void testOriginalFlowRules() {
        for (String id : new String[] { GameOptions.ORIGINAL_COLONY_REPORTS,
                                        GameOptions.DOCKING_ENDS_MOVES }) {
            assertTrue(id, spec(LEVI).getBoolean(id));
            assertFalse(id, spec("classic").getBoolean(id));
            assertFalse(id, spec("freecol").getBoolean(id));
            assertNotNull(id, spec(LEVI).getOptionGroup(GameOptions.GAMEOPTIONS_COLONY)
                          .getOption(id));
        }
        assertEquals("Colony reports: the original ones",
                     Messages.getName(GameOptions.ORIGINAL_COLONY_REPORTS));
        assertEquals("Docking ends the ship's moves",
                     Messages.getName(GameOptions.DOCKING_ENDS_MOVES));
        assertTrue(Specification.originalFlowDefault(LEVI));
        assertFalse(Specification.originalFlowDefault("classic"));
        assertFalse(Specification.originalFlowDefault("freecol"));
    }

    /**
     * The original's colony production (part X, changes 1-7 of the
     * spec): on in levi, off in classic and freecol (which get the
     * options from Specification.fixGameOptions); in the colony group,
     * with their names.  The rules' effects: {@code LeviProductionTest}.
     */
    public void testOriginalProductionRules() {
        assertEquals(5, Specification.ORIGINAL_PRODUCTION_OPTIONS.size());
        for (String id : Specification.ORIGINAL_PRODUCTION_OPTIONS) {
            assertTrue(id, spec(LEVI).getBoolean(id));
            assertFalse(id, spec("classic").getBoolean(id));
            assertFalse(id, spec("freecol").getBoolean(id));
            assertNotNull(id, spec(LEVI).getOptionGroup(GameOptions.GAMEOPTIONS_COLONY)
                          .getOption(id));
            assertTrue(id, CHANGED.containsKey(id));
        }
        assertEquals("The colony's own square: the original yield",
                     Messages.getName(GameOptions.ORIGINAL_COLONY_CENTER));
        assertEquals("Lumber as in the original",
                     Messages.getName(GameOptions.ORIGINAL_LUMBER));
        assertEquals("Expert fisherman as in the original",
                     Messages.getName(GameOptions.ORIGINAL_FISHERMAN));
        assertEquals("Horse breeding as in the original",
                     Messages.getName(GameOptions.ORIGINAL_HORSE_FOOD));
        assertEquals("Roads and silver as in the original",
                     Messages.getName(GameOptions.ORIGINAL_ROAD_SILVER));
        assertTrue(Specification.originalProductionDefault(LEVI));
        assertFalse(Specification.originalProductionDefault("classic"));
        assertFalse(Specification.originalProductionDefault("freecol"));
        assertFalse(Specification.originalProductionDefault(null));
        // The expert fisherman's modifier itself stays classic's in levi:
        // the rule is the option's (an old levi save gets it too).
        assertEquals(toList(spec("classic").getUnitType("model.unit.expertFisherman")
                            .getModifiers("model.goods.fish")).get(0).getValue(),
                     toList(spec(LEVI).getUnitType("model.unit.expertFisherman")
                            .getModifiers("model.goods.fish")).get(0).getValue(), 0f);
    }

    /**
     * The original's Tory penalty and father offer (fixer B after part X,
     * FIX-LIST must 7 and 10): on in levi, off in classic and freecol
     * (from Specification.fixGameOptions), in the colony and the map
     * group, with their names.  The effects: {@code LeviProductionTest}
     * (the penalty), {@code ServerPlayerTest} (the offer).
     */
    public void testOriginalToryAndFatherRules() {
        final String[][] rows = {
            { GameOptions.ORIGINAL_TORY_PENALTY, GameOptions.GAMEOPTIONS_COLONY,
              "Tory penalty as in the original" },
            { GameOptions.ORIGINAL_FATHER_OFFER, GameOptions.GAMEOPTIONS_MAP,
              "Declined founding fathers: a new offer" } };
        for (String[] r : rows) {
            assertTrue(r[0], spec(LEVI).getBoolean(r[0]));
            assertFalse(r[0], spec("classic").getBoolean(r[0]));
            assertFalse(r[0], spec("freecol").getBoolean(r[0]));
            assertNotNull(r[0], spec(LEVI).getOptionGroup(r[1]).getOption(r[0]));
            assertEquals(r[2], Messages.getName(r[0]));
        }
        // The difficulty levels' limits stay classic's: the rule adds its
        // offset (OriginalProduction.TORY_LIMIT_OFFSET).
        assertEquals(spec("classic").getInteger(GameOptions.BAD_GOVERNMENT_LIMIT),
                     spec(LEVI).getInteger(GameOptions.BAD_GOVERNMENT_LIMIT));
        assertEquals(spec("classic").getInteger(GameOptions.VERY_BAD_GOVERNMENT_LIMIT),
                     spec(LEVI).getInteger(GameOptions.VERY_BAD_GOVERNMENT_LIMIT));
    }

    /**
     * The original's native rules of part Z: on in levi, off in classic
     * and freecol (from Specification.fixGameOptions), in the map group,
     * with their names.  The effects: {@code LeviNativeRulesTest} on the
     * server.
     */
    public void testOriginalNativeRules() {
        final String[][] rows = {
            { GameOptions.ORIGINAL_TRIBUTE, "Tribute as in the original" },
            { GameOptions.ORIGINAL_NATIVE_WAR, "Native war as in the original" } };
        for (String[] r : rows) {
            assertTrue(r[0], spec(LEVI).getBoolean(r[0]));
            assertFalse(r[0], spec("classic").getBoolean(r[0]));
            assertFalse(r[0], spec("freecol").getBoolean(r[0]));
            assertNotNull(r[0], spec(LEVI).getOptionGroup(GameOptions.GAMEOPTIONS_MAP)
                          .getOption(r[0]));
            assertEquals(r[1], Messages.getName(r[0]));
        }
        assertTrue(Specification.originalNativesDefault(LEVI));
        assertFalse(Specification.originalNativesDefault("classic"));
        assertFalse(Specification.originalNativesDefault(null));
    }

    /**
     * A save from before part V (its specification without the two
     * options) gets them through Specification.fixGameOptions: on for a
     * levi game, off for classic (the review of part V, regression L2);
     * the same for part W's two (the original's colony reports, docking)
     * and part X's five (the original's colony production).
     */
    public void testOriginalBuildRulesInAnOldSave() throws Exception {
        final String[] ids = { GameOptions.ORIGINAL_BUILD_PRICE,
                               GameOptions.ORIGINAL_BUILD_COMPLETION,
                               GameOptions.ORIGINAL_COLONY_REPORTS,
                               GameOptions.DOCKING_ENDS_MOVES,
                               GameOptions.ORIGINAL_COLONY_CENTER,
                               GameOptions.ORIGINAL_LUMBER,
                               GameOptions.ORIGINAL_FISHERMAN,
                               GameOptions.ORIGINAL_HORSE_FOOD,
                               GameOptions.ORIGINAL_ROAD_SILVER,
                               GameOptions.ORIGINAL_TORY_PENALTY,
                               GameOptions.ORIGINAL_FATHER_OFFER,
                               GameOptions.ORIGINAL_TRIBUTE,
                               GameOptions.ORIGINAL_NATIVE_WAR };
        for (String rules : new String[] { LEVI, "classic" }) {
            final java.io.StringWriter sw = new java.io.StringWriter();
            try (net.sf.freecol.common.io.FreeColXMLWriter xw
                 = new net.sf.freecol.common.io.FreeColXMLWriter(sw,
                     net.sf.freecol.common.io.FreeColXMLWriter.WriteScope.toSave())) {
                spec(rules).toXML(xw);
            }
            String xml = sw.toString();
            for (String id : ids) {
                final String before = xml;
                xml = xml.replaceAll("<[A-Za-z]+Option[^>]*id=\"" + java.util.regex.Pattern.quote(id)
                                     + "\"[^>]*/>", "");
                if (before.equals(xml)) {
                    xml = xml.replaceAll("<[A-Za-z]+Option[^>]*id=\""
                        + java.util.regex.Pattern.quote(id) + "\"[^>]*>.*?</[A-Za-z]+Option>", "");
                }
                assertFalse(rules + " " + id + " taken out", xml.contains("\"" + id + "\""));
            }
            // Reading it runs the fixes (Specification.fixGameOptions).
            final Specification old = new Specification(
                new java.io.ByteArrayInputStream(xml.getBytes("UTF-8")));
            for (String id : ids) {
                assertEquals(rules + " " + id, LEVI.equals(rules), old.getBoolean(id));
            }
        }
    }

    /**
     * The original's fortification (J2): a completed fortification keeps
     * the turn's moves in levi, not in classic and freecol (which get the
     * option from Specification.fixGameOptions); it sits where the game
     * options dialog shows it.  The server's side:
     * {@code ServerUnitTest.testFortifyKeepsMoves}.
     */
    public void testFortifyKeepsMoves() {
        assertTrue(spec(LEVI).getBoolean(GameOptions.FORTIFY_KEEPS_MOVES));
        assertFalse(spec("classic").getBoolean(GameOptions.FORTIFY_KEEPS_MOVES));
        assertFalse(spec("freecol").getBoolean(GameOptions.FORTIFY_KEEPS_MOVES));
        assertNotNull(spec(LEVI).getOptionGroup(GameOptions.GAMEOPTIONS_MAP)
                      .getOption(GameOptions.FORTIFY_KEEPS_MOVES));
        assertEquals("Fortifying keeps the moves",
                     Messages.getName(GameOptions.FORTIFY_KEEPS_MOVES));
    }

    /**
     * The original's treaty breach (B3, clip opening_018): F next to a
     * colony of a European at peace declares war in levi, not in classic
     * and freecol (which get the option from Specification.fixGameOptions,
     * as an older levi save would); it sits where the game options dialog
     * shows it.  The rule's effects: {@code UnitTest.testFortifyWarColony},
     * {@code InGameControllerTest.testFortifyBreaksTheTreaty}.
     */
    public void testFortifyDeclaresWar() {
        assertTrue(spec(LEVI).getBoolean(GameOptions.FORTIFY_DECLARES_WAR));
        assertFalse(spec("classic").getBoolean(GameOptions.FORTIFY_DECLARES_WAR));
        assertFalse(spec("freecol").getBoolean(GameOptions.FORTIFY_DECLARES_WAR));
        assertNotNull(spec(LEVI).getOptionGroup(GameOptions.GAMEOPTIONS_MAP)
                      .getOption(GameOptions.FORTIFY_DECLARES_WAR));
        assertEquals("Fortifying breaks a treaty",
                     Messages.getName(GameOptions.FORTIFY_DECLARES_WAR));
        // A save that lacks it (a game begun before the rule): on for a
        // levi game, off for any other (Specification.fixGameOptions).
        assertEquals(Boolean.TRUE, Specification.fortifyDeclaresWarDefault(LEVI));
        assertEquals(Boolean.FALSE, Specification.fortifyDeclaresWarDefault("classic"));
        assertEquals(Boolean.FALSE, Specification.fortifyDeclaresWarDefault("freecol"));
        assertEquals(Boolean.FALSE, Specification.fortifyDeclaresWarDefault(null));
    }

    /**
     * The original's colony names: levi has none of its own and falls back
     * to its parent's, classic's, not to freecol's.
     */
    public void testColonyNamesOfTheParent() {
        Game game = getStandardGame(LEVI);
        Player dutch = game.getPlayerByNationId("model.nation.dutch");
        assertEquals("New Amsterdam", NameCache.getSettlementName(dutch, null));
        assertEquals("Fort Orange", NameCache.getSettlementName(dutch, null));
        Player spanish = game.getPlayerByNationId("model.nation.spanish");
        assertEquals("Isabella", NameCache.getSettlementName(spanish, null));

        game = getStandardGame("classic");
        dutch = game.getPlayerByNationId("model.nation.dutch");
        assertEquals("New Amsterdam", NameCache.getSettlementName(dutch, null));

        game = getStandardGame("freecol");
        dutch = game.getPlayerByNationId("model.nation.dutch");
        assertEquals("Nieuw Amsterdam", NameCache.getSettlementName(dutch, null));
    }

    /**
     * The AI Europeans are named after the original's leaders (NAMES.TXT
     * {@code @LEADERNAME}); their kings stay the rulers.
     */
    public void testLeaderNames() {
        final String[][] leaders = {
            { "model.nation.english", "Walter Raleigh", "Elizabeth I" },
            { "model.nation.french", "Jacques Cartier", "Louis XIV" },
            { "model.nation.spanish", "Christopher Columbus", "Philip II" },
            { "model.nation.dutch", "Michiel De Ruyter", "William I" } };
        Game game = getStandardGame(LEVI);
        for (String[] l : leaders) {
            Player p = game.getPlayerByNationId(l[0]);
            assertEquals(l[0], l[1], p.getName());
            assertEquals(l[0], l[1],
                NameCache.getLeaderName(game.getSpecification(), p.getNation()));
            assertEquals("the king keeps his name", l[2],
                         Messages.message(p.getMonarch().getNameKey()));
        }
        // The REF and the natives keep their rulers.
        Nation englishREF = game.getSpecification()
            .getNation("model.nation.englishREF");
        assertEquals(englishREF.getRulerName(),
            NameCache.getLeaderName(game.getSpecification(), englishREF));

        game = getStandardGame("freecol");
        for (String[] l : leaders) {
            assertEquals(l[0], l[2], game.getPlayerByNationId(l[0]).getName());
        }
        game = getStandardGame("classic");
        for (String[] l : leaders) {
            assertEquals(l[0], l[2], game.getPlayerByNationId(l[0]).getName());
        }
    }

    /**
     * The original's native land and sizes (part P2): levi's settlement
     * types have the original's home radius (camps and villages 1, the
     * Inca and Aztec cities 2, capitals the same; manual, the colony
     * chapter), the original's populations (camp 3, village 5, Aztec 7,
     * Inca 9; capitals 4 / 7 / 10 / 13, the size the original's capitals
     * have from 1505 on; at its start they too have the tribe's size), no
     * grown land, and a brave claims only inside the home radius (extra
     * radius 1); everything
     * else of each tribe is classic's.  Classic keeps FreeCol's types.
     * The levi rules load without a warning.
     */
    public void testNativeTypesAsTheOriginal() {
        final java.util.List<String> warnings = new java.util.ArrayList<>();
        final java.util.logging.Logger root = java.util.logging.Logger.getLogger("");
        final java.util.logging.Handler h = new java.util.logging.Handler() {
                @Override public void publish(java.util.logging.LogRecord r) {
                    if (r.getLevel().intValue()
                        >= java.util.logging.Level.WARNING.intValue()) {
                        warnings.add(r.getMessage());
                    }
                }
                @Override public void flush() {}
                @Override public void close() {}
            };
        root.addHandler(h);
        final Specification levi;
        try {
            levi = net.sf.freecol.FreeCol.loadSpecification(
                FreeColRules.getFreeColRulesFile(LEVI), null,
                "model.difficulty.veryEasy");
        } finally {
            root.removeHandler(h);
        }
        // Not even the land generator's fifth value warns (part P1 logged
        // "selectOption.setValue invalid value: 4, using fallback: 0"
        // here: the classic group's four values were still there when the
        // levi option's attributes were read; the final fixer of part P).
        assertEquals(warnings.toString(), 0, warnings.size());
        assertEquals(net.sf.freecol.common.option.MapGeneratorOptions
            .LAND_GENERATOR_ORIGINAL, levi.getMapGeneratorOptions()
            .getInteger(net.sf.freecol.common.option.MapGeneratorOptions
                .LAND_GENERATOR_TYPE));

        final Specification classic = spec("classic");
        final Map<String, int[]> original = new HashMap<>();
        // radius, extra radius, size
        original.put("model.settlement.camp", new int[] { 1, 1, 3 });
        original.put("model.settlement.camp.capital", new int[] { 1, 1, 4 });
        original.put("model.settlement.village", new int[] { 1, 1, 5 });
        original.put("model.settlement.village.capital", new int[] { 1, 1, 7 });
        original.put("model.settlement.aztec", new int[] { 2, 1, 7 });
        original.put("model.settlement.aztec.capital", new int[] { 2, 1, 10 });
        original.put("model.settlement.inca", new int[] { 2, 1, 9 });
        original.put("model.settlement.inca.capital", new int[] { 2, 1, 13 });
        int tribes = 0;
        for (IndianNationType nt : levi.getIndianNationTypes()) {
            if (nt.isAbstractType()) continue;
            tribes++;
            final IndianNationType ct = (IndianNationType)classic
                .getNationType(nt.getId());
            final String id = nt.getId();
            assertEquals(id, 2, nt.getSettlementTypes().size());
            assertEquals(id, ct.getSettlementTypes().size(),
                         nt.getSettlementTypes().size());
            for (SettlementType st : nt.getSettlementTypes()) {
                final int[] o = original.get(st.getId());
                assertNotNull(id + " " + st.getId(), o);
                assertEquals(st.getId(), o[0], st.getClaimableRadius());
                assertEquals(st.getId(), o[1], st.getExtraClaimableRadius());
                assertEquals(st.getId(), o[2], st.getMinimumSize());
                assertEquals(st.getId(), o[2], st.getMaximumSize());
                assertEquals(st.getId(), 0, st.getMinimumGrowth());
                assertEquals(st.getId(), 0, st.getMaximumGrowth());
                final SettlementType cst = ct.getSettlementType(st.getId());
                assertEquals(st.getId(), cst.isCapital(), st.isCapital());
                assertEquals(st.getId(), cst.getTradeBonus(), st.getTradeBonus());
                assertEquals(st.getId(), cst.getConvertThreshold(),
                             st.getConvertThreshold());
                assertEquals(st.getId(), cst.getVisibleRadius(),
                             st.getVisibleRadius());
                assertEquals(st.getId(), cst.getPlunderTypes().size(),
                             st.getPlunderTypes().size());
                assertEquals(st.getId(), cst.getGifts().getMinimum(),
                             st.getGifts().getMinimum());
                assertEquals(st.getId(), cst.getGifts().getMaximum(),
                             st.getGifts().getMaximum());
                assertEquals(st.getId(), toList(cst.getModifiers()).size(),
                             toList(st.getModifiers()).size());
            }
            assertEquals(id, ct.getSkills().size(), nt.getSkills().size());
            assertEquals(id, ct.getRegions(), nt.getRegions());
            assertEquals(id, ct.getAggression(), nt.getAggression());
            assertEquals(id, ct.getNumberOfSettlements(),
                         nt.getNumberOfSettlements());
            assertEquals(id, toList(ct.getAbilities()).size(),
                         toList(nt.getAbilities()).size());
        }
        assertEquals(8, tribes);
        // Every tribe's nation takes the new types.
        for (Nation n : levi.getIndianNations()) {
            final boolean city = n.getType().getId().endsWith("inca")
                || n.getType().getId().endsWith("aztec");
            assertEquals(n.getId(), (city) ? 2 : 1,
                n.getType().getSettlementType(false).getClaimableRadius());
            assertEquals(n.getId(), (city) ? 2 : 1,
                n.getType().getSettlementType(true).getClaimableRadius());
        }
        // Classic: FreeCol's.
        final SettlementType camp = classic
            .getNationType("model.nationType.apache").getSettlementType(true);
        assertEquals(2, camp.getClaimableRadius());
        assertEquals(2, camp.getExtraClaimableRadius());
        assertEquals(6, camp.getMinimumSize());
        assertEquals(8, camp.getMaximumSize());
        final SettlementType inca = classic
            .getNationType("model.nationType.inca").getSettlementType(true);
        assertEquals(3, inca.getClaimableRadius());
        assertEquals(14, inca.getMaximumGrowth());
    }

    /** Spain sails home to Sevilla, as in the original. */
    public void testSpainsHomePort() {
        Game game = getStandardGame(LEVI);
        Player spanish = game.getPlayerByNationId("model.nation.spanish");
        assertEquals("model.nation.spanish.europe.levi",
                     spanish.getEuropeNameKey());
        assertEquals("Seville", Messages.message(spanish.getEuropeNameKey()));
        assertEquals("Seville", Messages.message(spanish.getMarketName()));
        Player dutch = game.getPlayerByNationId("model.nation.dutch");
        assertEquals("model.nation.dutch.europe", dutch.getEuropeNameKey());

        game = getStandardGame("freecol");
        spanish = game.getPlayerByNationId("model.nation.spanish");
        assertEquals("Cadiz", Messages.message(spanish.getEuropeNameKey()));
    }
}
