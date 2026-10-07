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

import static net.sf.freecol.common.util.CollectionUtils.count;

import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;


public class PlayerTest extends FreeColTestCase {
    
    private static final UnitType freeColonist = spec().getUnitType("model.unit.freeColonist");

    public void testUnits() {
        Game game = getStandardGame();
        Map map = getTestMap(spec().getTileType("model.tile.plains"));
        game.changeMap(map);

        Player dutch = game.getPlayerByNationId("model.nation.dutch");
        Player french = game.getPlayerByNationId("model.nation.french");
        map.getTile(4, 7).setExplored(dutch, true);
        map.getTile(4, 8).setExplored(dutch, true);
        map.getTile(5, 7).setExplored(dutch, true);
        map.getTile(5, 8).setExplored(dutch, true);

        UnitType freeColonist = spec().getUnitType("model.unit.freeColonist");

        final Unit unit1 = new ServerUnit(game, map.getTile(4, 7), dutch, freeColonist);
        final Unit unit2 = new ServerUnit(game, map.getTile(4, 8), dutch, freeColonist);
        new ServerUnit(game, map.getTile(5, 7), dutch, freeColonist);
        new ServerUnit(game, map.getTile(5, 8), dutch, freeColonist);

        int count = count(dutch.getUnitSet());
        assertTrue(count == 4);

        unit1.dispose();
        assertFalse(dutch.hasUnit(unit1));

        unit2.changeOwner(french);
        assertFalse(dutch.hasUnit(unit2));
        assertTrue(french.hasUnit(unit2));
    }

    public void testEuropeanPlayer(Player player) {
        assertTrue(player.canBuildColonies());
        assertTrue(player.canHaveFoundingFathers());
        assertTrue(player.canMoveToEurope());
        assertTrue(player.isColonial());
        assertFalse(player.isDead());
        assertTrue(player.isEuropean());
        assertFalse(player.isIndian());
        assertFalse(player.isREF());
        assertEquals(2, player.getMaximumFoodConsumption());
    }

    public void testIndianPlayer(Player player) {
        assertFalse(player.canBuildColonies());
        assertFalse(player.canHaveFoundingFathers());
        assertFalse(player.canMoveToEurope());
        assertFalse(player.isColonial());
        assertFalse(player.isDead());
        assertFalse(player.isEuropean());
        assertTrue(player.isIndian());
        assertFalse(player.isREF());
        assertEquals(2, player.getMaximumFoodConsumption());
    }

    public void testRoyalPlayer(Player player) {
        assertFalse(player.canBuildColonies());
        assertFalse(player.canHaveFoundingFathers());
        assertTrue(player.canMoveToEurope());
        assertFalse(player.isColonial());
        assertEquals(player.getPlayerType(), Player.PlayerType.ROYAL);
        assertFalse(player.isDead());
        assertTrue(player.isEuropean());
        assertFalse(player.isIndian());
        assertTrue(player.isREF());
        assertEquals(2, player.getMaximumFoodConsumption());
    }

    public void testClassicPlayers() {
        Game game = getStandardGame("classic");

        // europeans
        Player dutch = game.getPlayerByNationId("model.nation.dutch");
        Player french = game.getPlayerByNationId("model.nation.french");
        Player english = game.getPlayerByNationId("model.nation.english");
        Player spanish = game.getPlayerByNationId("model.nation.spanish");

        testEuropeanPlayer(dutch);
        testEuropeanPlayer(french);
        testEuropeanPlayer(english);
        testEuropeanPlayer(spanish);

        // indians
        Player inca = game.getPlayerByNationId("model.nation.inca");
        Player aztec = game.getPlayerByNationId("model.nation.aztec");
        Player arawak = game.getPlayerByNationId("model.nation.arawak");
        Player cherokee = game.getPlayerByNationId("model.nation.cherokee");
        Player iroquois = game.getPlayerByNationId("model.nation.iroquois");
        Player sioux = game.getPlayerByNationId("model.nation.sioux");
        Player apache = game.getPlayerByNationId("model.nation.apache");
        Player tupi = game.getPlayerByNationId("model.nation.tupi");
        testIndianPlayer(inca);
        testIndianPlayer(aztec);
        testIndianPlayer(arawak);
        testIndianPlayer(cherokee);
        testIndianPlayer(iroquois);
        testIndianPlayer(sioux);
        testIndianPlayer(apache);
        testIndianPlayer(tupi);

        // royal
        Player dutchREF = game.getPlayerByNationId("model.nation.dutchREF");
        Player frenchREF = game.getPlayerByNationId("model.nation.frenchREF");
        Player englishREF = game.getPlayerByNationId("model.nation.englishREF");
        Player spanishREF = game.getPlayerByNationId("model.nation.spanishREF");
        testRoyalPlayer(dutchREF);
        testRoyalPlayer(frenchREF);
        testRoyalPlayer(englishREF);
        testRoyalPlayer(spanishREF);
        assertEquals(dutchREF, dutch.getREFPlayer());
        assertEquals(frenchREF, french.getREFPlayer());
        assertEquals(englishREF, english.getREFPlayer());
        assertEquals(spanishREF, spanish.getREFPlayer());

    }

    public void testFreecolPlayers() {
        // the initialization code is basically the same as in
        // getStandardGame(), except that all European nations are
        // available
        Game game = getStandardGame();
        Specification specification = game.getSpecification();
        NationOptions nationOptions = new NationOptions(specification);
        for (Nation nation : specification.getEuropeanNations()) {
            nationOptions.setNationState(nation, NationOptions.NationState.AVAILABLE);
        }
        game.setNationOptions(nationOptions);

        specification.applyDifficultyLevel("model.difficulty.medium");
        for (Nation n : specification.getNations()) {
            if (n.isUnknownEnemy()) continue;
            Player p = new ServerPlayer(game, false, n);
            p.setAI(!n.getType().isEuropean() || n.getType().isREF());
            game.addPlayer(p);
        }

        // europeans
        Player dutch = game.getPlayerByNationId("model.nation.dutch");
        Player french = game.getPlayerByNationId("model.nation.french");
        Player english = game.getPlayerByNationId("model.nation.english");
        Player spanish = game.getPlayerByNationId("model.nation.spanish");
        Player portuguese = game.getPlayerByNationId("model.nation.portuguese");
        Player swedish = game.getPlayerByNationId("model.nation.swedish");
        Player danish = game.getPlayerByNationId("model.nation.danish");
        Player russian = game.getPlayerByNationId("model.nation.russian");

        testEuropeanPlayer(dutch);
        testEuropeanPlayer(french);
        testEuropeanPlayer(english);
        testEuropeanPlayer(spanish);
        testEuropeanPlayer(portuguese);
        testEuropeanPlayer(swedish);
        testEuropeanPlayer(danish);
        testEuropeanPlayer(russian);

        // indians
        Player inca = game.getPlayerByNationId("model.nation.inca");
        Player aztec = game.getPlayerByNationId("model.nation.aztec");
        Player arawak = game.getPlayerByNationId("model.nation.arawak");
        Player cherokee = game.getPlayerByNationId("model.nation.cherokee");
        Player iroquois = game.getPlayerByNationId("model.nation.iroquois");
        Player sioux = game.getPlayerByNationId("model.nation.sioux");
        Player apache = game.getPlayerByNationId("model.nation.apache");
        Player tupi = game.getPlayerByNationId("model.nation.tupi");
        testIndianPlayer(inca);
        testIndianPlayer(aztec);
        testIndianPlayer(arawak);
        testIndianPlayer(cherokee);
        testIndianPlayer(iroquois);
        testIndianPlayer(sioux);
        testIndianPlayer(apache);
        testIndianPlayer(tupi);

        // royal
        Player dutchREF = game.getPlayerByNationId("model.nation.dutchREF");
        Player frenchREF = game.getPlayerByNationId("model.nation.frenchREF");
        Player englishREF = game.getPlayerByNationId("model.nation.englishREF");
        Player spanishREF = game.getPlayerByNationId("model.nation.spanishREF");
        Player portugueseREF = game.getPlayerByNationId("model.nation.portugueseREF");
        Player swedishREF = game.getPlayerByNationId("model.nation.swedishREF");
        Player danishREF = game.getPlayerByNationId("model.nation.danishREF");
        Player russianREF = game.getPlayerByNationId("model.nation.russianREF");
        testRoyalPlayer(dutchREF);
        testRoyalPlayer(frenchREF);
        testRoyalPlayer(englishREF);
        testRoyalPlayer(spanishREF);
        testRoyalPlayer(portugueseREF);
        testRoyalPlayer(swedishREF);
        testRoyalPlayer(danishREF);
        testRoyalPlayer(russianREF);
        assertEquals(dutchREF, dutch.getREFPlayer());
        assertEquals(frenchREF, french.getREFPlayer());
        assertEquals(englishREF, english.getREFPlayer());
        assertEquals(spanishREF, spanish.getREFPlayer());
        assertEquals(portugueseREF, portuguese.getREFPlayer());
        assertEquals(swedishREF, swedish.getREFPlayer());
        assertEquals(danishREF, danish.getREFPlayer());
        assertEquals(russianREF, russian.getREFPlayer());
    }

    public void testTension() {
        Game game = getStandardGame();
        ServerPlayer dutch = getServerPlayer(game, "model.nation.dutch");
        ServerPlayer french = getServerPlayer(game, "model.nation.french");

        int initialTension = 500;
        int change = 250;

        dutch.setTension(french, new Tension(initialTension));
        french.setTension(dutch, new Tension(initialTension));

        dutch.getTension(french).modify(change);

        int expectedDutchTension = initialTension + change;
        int expectedFrenchTension = initialTension;

        assertEquals("Dutch tension value should have changed",
            expectedDutchTension, dutch.getTension(french).getValue());
        assertEquals("French tension value should have remained the same",
            expectedFrenchTension, french.getTension(dutch).getValue());
    }

    public void testAddAnotherPlayersUnit(){
        Game game = getStandardGame();
        Map map = getTestMap();
        game.changeMap(map);

        Player dutch =  game.getPlayerByNationId("model.nation.dutch");
        Player french = game.getPlayerByNationId("model.nation.french");

        assertEquals("Wrong number of units for dutch player", 0,
                     dutch.getUnitCount());
        assertEquals("Wrong number of units for french player", 0,
                     french.getUnitCount());

        Unit colonist = new ServerUnit(game, map.getTile(6, 8), dutch,
                                       freeColonist);
        assertTrue("Colonist should be dutch", colonist.getOwner() == dutch);
        assertEquals("Wrong number of units for dutch player", 1,
                     dutch.getUnitCount());

        try{
            french.addUnit(colonist);
            fail("An IllegalStateException should have been raised");
        }
        catch (IllegalStateException e) {
            assertEquals("Colonist owner should not have been changed",
                         dutch, colonist.getOwner());
            assertEquals("Wrong number of units for dutch player", 1,
                         dutch.getUnitCount());
            assertEquals("Wrong number of units for french player", 0,
                         french.getUnitCount());

        }
    }

    /**
     * The Classic UI's woodcuts (W9): the attribute {@code classicWoodcuts}
     * goes into a save and to its owner only while it is not 0, a save or
     * an older one without it reads 0, and an update ({@code copyIn})
     * never takes a woodcut away.
     */
    public void testClassicWoodcuts() throws Exception {
        final Game game = getStandardGame();
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final Player french = game.getPlayerByNationId("model.nation.french");
        assertEquals(0, dutch.getClassicWoodcuts());
        final String none = dutch.serialize(
            net.sf.freecol.common.io.FreeColXMLWriter.WriteScope.toSave());
        assertFalse(none.contains("classicWoodcuts"));
        dutch.setClassicWoodcuts(0b1010);
        final String save = dutch.serialize(
            net.sf.freecol.common.io.FreeColXMLWriter.WriteScope.toSave());
        assertTrue(save.contains("classicWoodcuts=\"10\""));
        assertTrue(dutch.serialize(dutch).contains("classicWoodcuts=\"10\""));
        assertFalse(dutch.serialize(french).contains("classicWoodcuts"));
        final Player read = readPlayer(game, save);
        assertEquals(dutch.getId(), read.getId());
        assertEquals(10, read.getClassicWoodcuts());
        assertEquals(0, readPlayer(game, save.replace("classicWoodcuts=\"10\"", ""))
                     .getClassicWoodcuts());
        // An update adds, never removes.
        read.setClassicWoodcuts(0b100);
        assertTrue(dutch.copyIn(read));
        assertEquals(0b1110, dutch.getClassicWoodcuts());
        read.setClassicWoodcuts(0);
        assertTrue(dutch.copyIn(read));
        assertEquals(0b1110, dutch.getClassicWoodcuts());
    }

    /**
     * The Classic UI's tutorial tips (W13, {@code @TUTORIAL17} once per
     * game): the attribute {@code classicTips} as {@code classicWoodcuts}.
     */
    public void testClassicTips() throws Exception {
        final Game game = getStandardGame();
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final Player french = game.getPlayerByNationId("model.nation.french");
        assertEquals(0, dutch.getClassicTips());
        assertFalse(dutch.serialize(net.sf.freecol.common.io.FreeColXMLWriter
            .WriteScope.toSave()).contains("classicTips"));
        dutch.setClassicTips(1 << 17);
        final String save = dutch.serialize(
            net.sf.freecol.common.io.FreeColXMLWriter.WriteScope.toSave());
        assertTrue(save.contains("classicTips=\"131072\""));
        assertFalse(dutch.serialize(french).contains("classicTips"));
        final Player read = readPlayer(game, save);
        assertEquals(1 << 17, read.getClassicTips());
        assertEquals(0, readPlayer(game, save.replace("classicTips=\"131072\"", ""))
                     .getClassicTips());
        read.setClassicTips(0);
        assertTrue(dutch.copyIn(read));
        assertEquals(1 << 17, dutch.getClassicTips());
    }

    /**
     * The Classic UI's unit cycle cursor (I2): the attribute
     * {@code classicCycleCursor} goes into a save and to its owner only
     * while it is not the head (-1); a save or an older one without it
     * reads -1; a rank past 32 bits survives; an update ({@code copyIn})
     * never moves it (the client's own); a negative value is the head.
     */
    public void testClassicCycleCursor() throws Exception {
        final Game game = getStandardGame();
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final Player french = game.getPlayerByNationId("model.nation.french");
        assertEquals(-1L, dutch.getClassicCycleCursor());
        assertFalse(dutch.serialize(net.sf.freecol.common.io.FreeColXMLWriter
                .WriteScope.toSave()).contains("classicCycleCursor"));
        final long rank = (1L << 32) | 5888L;
        dutch.setClassicCycleCursor(rank);
        final String save = dutch.serialize(
            net.sf.freecol.common.io.FreeColXMLWriter.WriteScope.toSave());
        assertTrue(save.contains("classicCycleCursor=\"" + rank + "\""));
        assertTrue(dutch.serialize(dutch).contains("classicCycleCursor"));
        assertFalse(dutch.serialize(french).contains("classicCycleCursor"));
        final Player read = readPlayer(game, save);
        assertEquals(rank, read.getClassicCycleCursor());
        assertEquals(-1L, readPlayer(game, save.replace("classicCycleCursor=\"" + rank
            + "\"", "")).getClassicCycleCursor());
        read.setClassicCycleCursor(7L);
        assertTrue(dutch.copyIn(read));
        assertEquals(rank, dutch.getClassicCycleCursor());
        read.setClassicCycleCursor(-1L);
        assertTrue(dutch.copyIn(read));
        assertEquals(rank, dutch.getClassicCycleCursor());
        dutch.setClassicCycleCursor(-5L);
        assertEquals(-1L, dutch.getClassicCycleCursor());
    }

    /** A player read from XML, outside the game. */
    private static Player readPlayer(Game game, String xml) throws Exception {
        try (net.sf.freecol.common.io.FreeColXMLReader xr
             = new net.sf.freecol.common.io.FreeColXMLReader(new java.io.StringReader(xml))) {
            return xr.copy(game, Player.class);
        }
    }
}
