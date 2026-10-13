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

package net.sf.freecol.server.ai;

import net.sf.freecol.common.model.Constants;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.model.Tension;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.server.ServerTestHelper;
import net.sf.freecol.server.control.InGameController;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;


public class TensionTest extends FreeColTestCase {

    private static final TileType plainsType
        = spec().getTileType("model.tile.plains");


    @Override
    public void tearDown() throws Exception {
        ServerTestHelper.stopServerGame();
        super.tearDown();
    }


    public void testTension()  {
        Game game = ServerTestHelper.startServerGame(getTestMap(plainsType));
        Map map = game.getMap();
        InGameController igc = ServerTestHelper.getInGameController();
        AIMain aiMain = ServerTestHelper.getServer().getAIMain();

        ServerPlayer european = getServerPlayer(game, "model.nation.dutch");
        ServerPlayer indian = getServerPlayer(game, "model.nation.tupi");
        NativeAIPlayer indianAI = (NativeAIPlayer)aiMain.getAIPlayer(indian);

        // initially, the players are unknown to each other
        Tension tension = indian.getTension(european);
        Stance stance = indian.getStance(european);
        assertTrue(tension != null && tension.getValue() == 0);
        assertEquals(Stance.UNCONTACTED, stance);
        assertFalse(indian.hasContacted(european));

        // create an Indian settlement
        Tile tile = map.getTile(6, 9);

        FreeColTestCase.IndianSettlementBuilder builder = new FreeColTestCase.IndianSettlementBuilder(game);
        IndianSettlement is = builder.player(indian).settlementTile(tile)
            .skillToTeach(null).capital(true).build();

        int unitCount = is.getType().getMaximumSize();
        for (int i = 0; i < unitCount; i++) {
            UnitType unitType = spec().getDefaultUnitType(indian);
            Unit unit = new ServerUnit(game, is, indian, unitType);
            unit.changeHomeIndianSettlement(is);
            if (i == 0) {
                unit.setLocation(tile);
            } else {
                unit.setLocation(is);
            }
        }
        Player.makeContact(indian, european);
        tension = indian.getTension(european);
        stance = indian.getStance(european);
        assertNotNull(tension);
        assertEquals(Stance.PEACE, stance);

        // create 2 unarmed european units next to the indianSettlement
        Tile tile2 = map.getTile(6,8);
        UnitType unitType = spec().getUnitType("model.unit.hardyPioneer");
        Unit unit1 = new ServerUnit(game, tile2, european, unitType);
        european.exploreForUnit(unit1);

        // the european player steals 1 tile from the indians
        assertEquals(indian, tile2.getOwner());
        
        igc.claimLand(european, tile2, null, Constants.STEAL_LAND);
        assertEquals(european, tile2.getOwner());

        // check the tension and stance have expected values
        tension = indian.getTension(european);
        stance = indian.getStance(european);
        assertNotNull(tension);
        assertEquals(Tension.TENSION_ADD_LAND_TAKEN, tension.getValue());
        assertEquals(Stance.PEACE, stance);

        // ask the AI to secure the settlement
        indianAI.secureIndianSettlement(is, new LogBuilder(0));

        // the tension and stance have not changed
        tension = indian.getTension(european);
        stance = indian.getStance(european);
        assertNotNull(tension);
        assertEquals(Tension.TENSION_ADD_LAND_TAKEN, tension.getValue());
        assertEquals(Stance.PEACE, stance);
    }

    /**
     * Part O B1: a «Nein» at the first contact is war from the start (the
     * original's @INDIANSHUN).  The native AI takes it as any war: its
     * turns run without an exception (the first turn's missions, then a
     * later turn's gifts, tribute and missions), it keeps the war (its
     * stance follows the tension, which the refusal and the war made
     * hateful), and it brings no gift to the enemy's colony.  An AI
     * European always accepts ({@code AIPlayer.firstContactHandler}), so
     * it never gets this war.
     */
    public void testWarFromTheFirstContact() {
        Game game = ServerTestHelper.startServerGame(getTestMap(plainsType));
        Map map = game.getMap();
        InGameController igc = ServerTestHelper.getInGameController();
        AIMain aiMain = ServerTestHelper.getServer().getAIMain();
        ServerPlayer european = getServerPlayer(game, "model.nation.dutch");
        european.setAI(false);
        ServerPlayer indian = getServerPlayer(game, "model.nation.tupi");
        NativeAIPlayer indianAI = (NativeAIPlayer)aiMain.getAIPlayer(indian);

        Tile tile = map.getTile(6, 9);
        IndianSettlement is = new FreeColTestCase.IndianSettlementBuilder(game)
            .player(indian).settlementTile(tile).capital(true).build();
        for (int i = 0; i < is.getType().getMaximumSize(); i++) {
            Unit unit = new ServerUnit(game, is, indian,
                                       spec().getDefaultUnitType(indian));
            unit.changeHomeIndianSettlement(is);
            unit.setLocation((i < 2) ? tile : is);
        }
        Tile near = map.getTile(6, 6);
        new ServerUnit(game, near, european,
                       spec().getUnitType("model.unit.freeColonist"));
        european.exploreForUnit(near.getFirstUnit());

        // The contact (peace at once), then «Nein».
        european.csNativeFirstContact(indian, null,
            new net.sf.freecol.common.networking.ChangeSet());
        assertEquals(Stance.PEACE, indian.getStance(european));
        igc.nativeFirstContact(european, indian, null, false);
        assertEquals(Stance.WAR, indian.getStance(european));
        assertEquals(Stance.WAR, european.getStance(indian));
        assertEquals(Tension.Level.HATEFUL, indian.getTension(european).getLevel());

        // The natives' first turn and a later one: no exception (thrown
        // or only logged), still war.
        final java.util.List<java.util.logging.LogRecord> thrown
            = new java.util.ArrayList<>();
        final java.util.logging.Handler catcher = new java.util.logging.Handler() {
                @Override
                public void publish(java.util.logging.LogRecord r) {
                    if (r.getThrown() != null) thrown.add(r);
                }
                @Override
                public void flush() {}
                @Override
                public void close() {}
            };
        final java.util.logging.Logger root = java.util.logging.Logger.getLogger("");
        root.addHandler(catcher);
        try {
            game.setCurrentPlayer(indian);
            indianAI.startWorking();
            assertEquals(Stance.WAR, indian.getStance(european));
            ServerTestHelper.newTurn();
            game.setCurrentPlayer(indian);
            indianAI.startWorking();
        } finally {
            root.removeHandler(catcher);
        }
        for (java.util.logging.LogRecord r : thrown) {
            fail("logged: " + r.getMessage() + " " + r.getThrown());
        }
        assertEquals(Stance.WAR, indian.getStance(european));
        assertEquals(Stance.WAR, european.getStance(indian));
    }
}
