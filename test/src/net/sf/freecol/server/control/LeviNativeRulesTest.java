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

package net.sf.freecol.server.control;

import java.util.List;

import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.CombatModel.CombatEffectType;
import net.sf.freecol.common.model.Direction;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Role;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.model.Tension;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.common.networking.ChangeSet;
import net.sf.freecol.common.option.GameOptions;
import net.sf.freecol.server.ServerTestHelper;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;
import net.sf.freecol.util.test.FreeColTestUtils;


/**
 * The levi rules for the natives of part Z (Z-ITEMS Z2; M3 spec A2, A3),
 * headless on the server, each with its option on and off: the
 * original's tribute ({@code model.option.originalTribute}, Z2.2) and its
 * native war ({@code model.option.originalNativeWar}, Z2.3).
 */
public class LeviNativeRulesTest extends FreeColTestCase {

    private static final UnitType colonistType
        = spec().getUnitType("model.unit.freeColonist");
    private static final Role soldierRole = spec().getRole("model.role.soldier");
    private static final GoodsType furs = spec().getGoodsType("model.goods.furs");
    private static final GoodsType sugar = spec().getGoodsType("model.goods.sugar");
    private static final GoodsType lumber = spec().getGoodsType("model.goods.lumber");

    /** The specification a scene changed, reset after the test. */
    private Specification changed = null;

    @Override
    protected void tearDown() throws Exception {
        if (this.changed != null) {
            this.changed.setBoolean(GameOptions.ORIGINAL_TRIBUTE, false);
            this.changed.setBoolean(GameOptions.ORIGINAL_NATIVE_WAR, false);
        }
        ServerTestHelper.stopServerGame();
        super.tearDown();
    }

    /** A scene: our colony near an Arawak village, a soldier next to it. */
    private static final class Scene {
        Game game;
        InGameController igc;
        ServerPlayer dutch, arawak;
        IndianSettlement village;
        Colony near, far;
        ServerUnit soldier;
    }

    /**
     * The scene on a test map: the Arawak village at (5,8), our Base at
     * (5,5) (3 away) and Far at (12,8) (7 away), a soldier of ours north
     * of the village and two more in Base, so that our land strength
     * passes the tribe's single village; met, at peace, happy.
     */
    private Scene scene(boolean rule) {
        return scene(rule, true);
    }

    /**
     * The scene ({@link #scene(boolean)}), with or without our colonies
     * (without: the two soldiers stand next to the first).
     */
    private Scene scene(boolean rule, boolean colonies) {
        final Scene s = new Scene();
        ServerTestHelper.stopServerGame();
        s.game = ServerTestHelper.startServerGame(getTestMap());
        final Specification spec = s.game.getSpecification();
        spec.setBoolean(GameOptions.ORIGINAL_TRIBUTE, rule);
        this.changed = spec;
        s.igc = ServerTestHelper.getInGameController();
        s.dutch = getServerPlayer(s.game, "model.nation.dutch");
        s.arawak = getServerPlayer(s.game, "model.nation.arawak");
        final Map map = s.game.getMap();
        s.village = new FreeColTestCase.IndianSettlementBuilder(s.game)
            .player(s.arawak).settlementTile(map.getTile(5, 8)).capital(true).build();
        Player.makeContact(s.arawak, s.dutch);
        s.village.setContacted(s.dutch);
        s.village.setAlarm(s.dutch, new Tension(Tension.TENSION_MIN));
        final Tile at = s.village.getTile().getNeighbourOrNull(Direction.N);
        s.soldier = new ServerUnit(s.game, at, s.dutch, colonistType, soldierRole);
        if (!colonies) {
            new ServerUnit(s.game, at, s.dutch, colonistType, soldierRole);
            new ServerUnit(s.game, at, s.dutch, colonistType, soldierRole);
            return s;
        }
        s.near = FreeColTestUtils.getColonyBuilder().player(s.dutch).colonyName("Base")
            .colonyTile(map.getTile(5, 5)).initialColonists(1).build();
        s.far = FreeColTestUtils.getColonyBuilder().player(s.dutch).colonyName("Far")
            .colonyTile(map.getTile(12, 8)).initialColonists(1).build();
        new ServerUnit(s.game, s.near.getTile(), s.dutch, colonistType, soldierRole);
        new ServerUnit(s.game, s.near.getTile(), s.dutch, colonistType, soldierRole);
        assertTrue(s.arawak.getSettlementCount() < s.dutch.calculateStrength(false));
        return s;
    }

    /**
     * The original's tribute, goods: a happy village with 60 furs and 30
     * sugar gives half its furs, 30, to Base, the nearer colony
     * (@EXTORTSTUFF's message with the amount, the goods and the colony);
     * the village's alarm rises by FreeCol's amount, the tribute's turn is
     * noted, the soldier's moves are spent.  With 300 furs it gives 100
     * (one load).  The first demand of a game is never "lately" (the
     * village's last tribute 0).
     */
    public void testTheOriginalTributeInGoods() {
        for (int stock : new int[] { 60, 300 }) {
            final Scene s = scene(true);
            s.village.addGoods(furs, stock);
            s.village.addGoods(sugar, 30);
            s.village.addGoods(lumber, 500);   // not New World goods
            final int furs0 = s.near.getGoodsCount(furs);
            final int alarm0 = s.village.getAlarm(s.dutch).getValue();
            final int gold0 = s.dutch.getGold();
            final ChangeSet cs = s.igc.demandTribute(s.dutch, s.soldier, s.village);
            final int paid = Math.min(stock / 2, InGameController.TRIBUTE_GOODS_MAXIMUM);
            assertTrue(cs.toString(), cs.toString().contains("scoutSettlement.tributeGoods"));
            assertEquals(furs0 + paid, s.near.getGoodsCount(furs));
            assertEquals(0, s.far.getGoodsCount(furs));
            assertEquals(stock - paid, s.village.getGoodsCount(furs));
            assertEquals(30, s.village.getGoodsCount(sugar));
            assertTrue("the anger rises", s.village.getAlarm(s.dutch).getValue() > alarm0);
            assertEquals(s.game.getTurn().getNumber(), s.village.getLastTribute());
            assertEquals(0, s.soldier.getMovesLeft());
            assertEquals("no gold", gold0, s.dutch.getGold());
        }
    }

    /**
     * The original's refusals: «no» (@EXTORTNO) from a village hateful
     * towards us, or a tribe at war with us (the box @VILLAGEWAR, I);
     * «laugh» (@EXTORTLAUGH) from a tribe with as many settlements as our
     * land strength (the soldiers gone); «poor» (@EXTORTPOOR) after a
     * tribute in the last five turns, with fewer than 10 to give, or
     * without a colony of ours.  No goods move.
     */
    public void testTheOriginalTributesRefusals() {
        final Object[][] cases = {
            { "hateful", "scoutSettlement.tributeNo" },
            { "war", "scoutSettlement.tributeNo" },
            { "weak", "scoutSettlement.tributeLaugh" },
            { "lately", "scoutSettlement.tributePoor" },
            { "little", "scoutSettlement.tributePoor" },
            { "no colony", "scoutSettlement.tributePoor" } };
        for (Object[] c : cases) {
            final Scene s = scene(true, !"no colony".equals(c[0]));
            s.village.addGoods(furs, ("little".equals(c[0])) ? 19 : 80);
            switch ((String) c[0]) {
            case "hateful":
                s.village.setAlarm(s.dutch, new Tension(Tension.Level.HATEFUL.getLimit()));
                break;
            case "war":
                s.arawak.setStance(s.dutch, Stance.WAR);
                s.dutch.setStance(s.arawak, Stance.WAR);
                break;
            case "weak":
                for (net.sf.freecol.common.model.Unit u : new java.util.ArrayList<>(
                         s.near.getTile().getUnitList())) {
                    u.changeRole(spec().getDefaultRole(), 0);
                }
                s.soldier.changeRole(spec().getDefaultRole(), 0);
                assertTrue(s.arawak.getSettlementCount() >= s.dutch.calculateStrength(false));
                break;
            case "lately":
                s.village.setLastTribute(Math.max(1, s.game.getTurn().getNumber() - 3));
                break;
            case "no colony":
                assertTrue(s.dutch.getColonyList().isEmpty());
                break;
            default:
                break;
            }
            final int had = s.village.getGoodsCount(furs);
            final ChangeSet cs = s.igc.demandTribute(s.dutch, s.soldier, s.village);
            assertTrue(c[0] + " " + cs, cs.toString().contains((String) c[1]));
            assertFalse(c[0] + "", cs.toString().contains("scoutSettlement.tributeGoods"));
            assertEquals(c[0] + "", had, s.village.getGoodsCount(furs));
            assertEquals(0, s.soldier.getMovesLeft());
        }
    }

    /**
     * An angry village pays (the fidelity review of part Z: V war-french
     * #42284 the Tupi camp's @VILLAGEBAD, «Zoll fordern» at #42477, then
     * #42537 @EXTORTSTUFF, 64 furs; before the fixer FreeCol's ANGRY,
     * which the box shows as @VILLAGEBAD, said «no»): the tribe and the
     * village angry, or the tribe hateful with the village angry (the
     * village's own mood decides, as its box), or both displeased: half
     * of its 80 furs to Base.
     */
    public void testAnAngryVillagePays() {
        final Tension.Level[][] cases = {
            { Tension.Level.ANGRY, Tension.Level.ANGRY },
            { Tension.Level.HATEFUL, Tension.Level.ANGRY },
            { Tension.Level.DISPLEASED, Tension.Level.DISPLEASED } };
        for (Tension.Level[] c : cases) {
            final Scene s = scene(true);
            s.village.addGoods(furs, 80);
            s.arawak.getTension(s.dutch).setValue(c[0].getLimit());
            s.village.setAlarm(s.dutch, new Tension(c[1].getLimit()));
            assertEquals(c[1], s.village.getAlarm(s.dutch).getLevel());
            final int furs0 = s.near.getGoodsCount(furs);
            final ChangeSet cs = s.igc.demandTribute(s.dutch, s.soldier, s.village);
            final String what = c[0] + "/" + c[1];
            assertTrue(what + " " + cs, cs.toString().contains("scoutSettlement.tributeGoods"));
            assertFalse(what, cs.toString().contains("scoutSettlement.tributeNo"));
            assertEquals(what, furs0 + 40, s.near.getGoodsCount(furs));
            assertEquals(what, 40, s.village.getGoodsCount(furs));
        }
    }

    /**
     * Without the rule (classic, freecol): FreeCol's gold from a happy
     * village, no goods move.
     */
    public void testFreeColsTributeWithoutTheRule() {
        final Scene s = scene(false);
        s.village.addGoods(furs, 80);
        final int gold0 = s.dutch.getGold();
        final ChangeSet cs = s.igc.demandTribute(s.dutch, s.soldier, s.village);
        assertTrue(cs.toString(), cs.toString().contains("scoutSettlement.tribute"));
        assertFalse(cs.toString(), cs.toString().contains("scoutSettlement.tributeGoods"));
        assertEquals(80, s.village.getGoodsCount(furs));
        assertEquals(0, s.near.getGoodsCount(furs));
        assertTrue("FreeCol's gold (or its refusal)", s.dutch.getGold() >= gold0);
    }

    /** The goods and the colony of the original's tribute: the most plentiful, the nearest. */
    public void testTheTributesGoodsAndColony() {
        final Scene s = scene(true);
        assertNull("none", InGameController.tributeGoods(s.village));
        s.village.addGoods(sugar, 40);
        s.village.addGoods(furs, 40);
        assertSame("a tie: the specification's order",
                   sugar, InGameController.tributeGoods(s.village));
        s.village.addGoods(furs, 1);
        assertSame(furs, InGameController.tributeGoods(s.village));
        assertSame(s.near, InGameController.tributeColony(s.dutch, s.village.getTile()));
        assertSame(s.far, InGameController.tributeColony(s.dutch,
                                                         s.game.getMap().getTile(13, 8)));
    }


    // Z2.3: the original's native war

    /** The brave type. */
    private static final UnitType braveType = spec().getUnitType("model.unit.brave");

    /** The scene with the war rule set, and a brave of the tribe east of the soldier. */
    private ServerUnit brave(Scene s, boolean warRule) {
        s.game.getSpecification().setBoolean(GameOptions.ORIGINAL_NATIVE_WAR, warRule);
        final Tile east = s.soldier.getTile().getNeighbourOrNull(Direction.E);
        return new ServerUnit(s.game, east, s.arawak, braveType);
    }

    /** What our player is told of a change set ({@code ChangeSet.build}). */
    private static String toldTo(ChangeSet cs, Player p) {
        return String.valueOf(cs.build(p));
    }

    /**
     * Our attack on natives at peace (Z2.3, Roger's decision F2, built as
     * the original): under the levi rule no war in either direction, the
     * tribe's tension rises (FreeCol's amounts); without it FreeCol's war
     * both ways.  In the Classic UI (FreeCol.getClassic) the attacker is
     * not told «… hat uns den Krieg erklärt!» even then (Roger's event
     * #15, frame_065184); FreeCol's own UI keeps its notice.
     */
    public void testOurAttackOnNativesStartsNoWar() {
        final boolean saved = net.sf.freecol.FreeCol.getClassic();
        try {
            for (boolean rule : new boolean[] { true, false }) {
                for (boolean classic : new boolean[] { true, false }) {
                    net.sf.freecol.FreeCol.setClassic(classic);
                    final Scene s = scene(false);
                    final ServerUnit brave = brave(s, rule);
                    assertEquals(Stance.PEACE, s.dutch.getStance(s.arawak));
                    final int tension0 = s.arawak.getTension(s.dutch).getValue();
                    final ChangeSet cs = new ChangeSet();
                    s.dutch.csCombat(s.soldier, brave, new java.util.ArrayList<>(List.of(
                        CombatEffectType.WIN, CombatEffectType.SLAUGHTER_UNIT)),
                        new java.util.Random(1), cs);
                    final String what = "rule=" + rule + " classic=" + classic;
                    final Stance want = (rule) ? Stance.PEACE : Stance.WAR;
                    assertEquals(what, want, s.dutch.getStance(s.arawak));
                    assertEquals(what, want, s.arawak.getStance(s.dutch));
                    assertTrue(what + " the anger rises",
                               s.arawak.getTension(s.dutch).getValue() > tension0);
                    assertEquals(what, !rule && !classic,
                                 toldTo(cs, s.dutch).contains(Stance.WAR.getStanceChangeKey()));
                }
            }
        } finally {
            net.sf.freecol.FreeCol.setClassic(saved);
        }
    }

    /**
     * The natives' attack on our unit at peace (Z2.3, the M3 verifier's
     * smaller correction 1: ServerPlayer.csCombat's European defender):
     * under the levi rule no war either; without it FreeCol's war.
     */
    public void testTheNativesRaidStartsNoWar() {
        for (boolean rule : new boolean[] { true, false }) {
            final Scene s = scene(false);
            final ServerUnit brave = brave(s, rule);
            final ChangeSet cs = new ChangeSet();
            s.arawak.csCombat(brave, s.soldier, new java.util.ArrayList<>(List.of(
                CombatEffectType.LOSE, CombatEffectType.SLAUGHTER_UNIT)),
                new java.util.Random(1), cs);
            final Stance want = (rule) ? Stance.PEACE : Stance.WAR;
            assertEquals("rule=" + rule, want, s.dutch.getStance(s.arawak));
            assertEquals("rule=" + rule, want, s.arawak.getStance(s.dutch));
        }
    }

    /**
     * The natives may still go to war on their own when their tension
     * passes hateful (FreeCol's AI, {@code AIPlayer.determineStance}), with
     * or without the rule.
     */
    public void testTheNativesStillGoToWarWhenHateful() {
        assertEquals(Stance.WAR, Stance.PEACE.getStanceFromTension(
            new Tension(Tension.TENSION_MAX)));
        assertEquals(Stance.PEACE, Stance.PEACE.getStanceFromTension(
            new Tension(Tension.Level.HATEFUL.getLimit())));
    }
}
