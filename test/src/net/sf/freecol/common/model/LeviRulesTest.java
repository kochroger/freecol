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
