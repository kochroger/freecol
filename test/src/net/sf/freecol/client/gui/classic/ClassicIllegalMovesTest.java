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

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

import net.sf.freecol.client.gui.GUI;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Direction;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileImprovement;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.server.model.ServerColony;
import net.sf.freecol.server.model.ServerIndianSettlement;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * Tests of the original's refusals (R3, {@link ClassicIllegalMoves}): every
 * refused order the Classic UI's keys reach gets GAME.TXT's box or nothing,
 * from FreeCol's own rule, in the classic and in Roger's levi rules; the
 * key map hands a key that cannot fire to the refusal.  The GUI's boxes and
 * the map's keys are in {@link ClassicGUISeamTest#testRefusalBoxes}.
 */
public class ClassicIllegalMovesTest extends FreeColTestCase {

    /** The rules every test runs in. */
    private static final String[] RULES = { "classic", "levi" };

    /**
     * A 20x15 test world in one ruleset: land at x &lt; 10 (plains), sea
     * at x &gt;= 10, the high seas from x = 15; the Dutch, the French, the
     * Arawaks.
     */
    private static final class World {

        final String rules;
        final Specification spec;
        final Game game;
        final Map map;
        final Player dutch, french, arawak;

        World(String rules) {
            this.rules = rules;
            this.game = getStandardGame(rules);
            this.spec = this.game.getSpecification();
            final MapBuilder b = new MapBuilder(this.game);
            b.setDimensions(20, 15).setBaseTileType(this.spec.getTileType("model.tile.ocean"))
                .setExploredByAll(true);
            for (int y = 0; y < 15; y++) {
                for (int x = 0; x < 10; x++) {
                    b.setTileType(x, y, this.spec.getTileType("model.tile.plains"));
                }
                for (int x = 15; x < 20; x++) {
                    b.setTileType(x, y, this.spec.getTileType("model.tile.highSeas"));
                }
            }
            this.map = b.build();
            this.game.changeMap(this.map);
            this.dutch = this.game.getPlayerByNationId("model.nation.dutch");
            this.french = this.game.getPlayerByNationId("model.nation.french");
            this.arawak = this.game.getPlayerByNationId("model.nation.arawak");
        }

        Tile tile(int x, int y) {
            return this.map.getTile(x, y);
        }

        Unit unit(Object loc, Player owner, String type) {
            return new ServerUnit(this.game, (net.sf.freecol.common.model.Location) loc,
                owner, this.spec.getUnitType("model.unit." + type));
        }

        Unit unit(Object loc, Player owner, String type, String role) {
            return new ServerUnit(this.game, (net.sf.freecol.common.model.Location) loc,
                owner, this.spec.getUnitType("model.unit." + type),
                this.spec.getRole("model.role." + role));
        }

        /** The direction from one tile to the next one east. */
        Direction east(Tile from) {
            final Direction d = this.map.getDirection(from,
                this.tile(from.getX() + 1, from.getY()));
            assertNotNull(d);
            return d;
        }

        Colony colony(Player owner, String name, Tile t) {
            final Colony c = new ServerColony(this.game, owner, name, t);
            owner.addSettlement(c);
            c.placeSettlement(true);
            return c;
        }

        IndianSettlement village(Tile t) {
            final IndianSettlement is = new ServerIndianSettlement(this.game,
                this.arawak, "Camp", t, false, null, null);
            this.arawak.addSettlement(is);
            final Unit brave = new ServerUnit(this.game, is, this.arawak,
                this.spec.getUnitType("model.unit.brave"));
            is.addOwnedUnit(brave);
            is.placeSettlement(true);
            return is;
        }

        void meet(Player a, Player b, Stance s) {
            a.setStance(b, s);
            b.setStance(a, s);
        }
    }

    private static void assertVerdict(String what, String section,
                                      ClassicNotices.Who who,
                                      ClassicIllegalMoves.Verdict v) {
        assertNotNull(what + ": refused", v);
        assertEquals(what, section, v.section);
        if (section != null) assertEquals(what, who, v.who);
    }


    /**
     * Roger's case: a merchantman, a caravel or a galleon ordered into a
     * foreign ship gets @SHIPCOMBAT with the admiral; a privateer and a
     * frigate attack (the controller's).  In both rulesets the trading
     * ships have no offence.
     */
    public void testShipCombat() {
        for (String rules : RULES) {
            final World w = new World(rules);
            final Tile at = w.tile(12, 6), next = w.tile(13, 6);
            w.unit(next, w.french, "caravel");
            for (String type : new String[] { "merchantman", "caravel", "galleon" }) {
                final Unit ship = w.unit(at, w.dutch, type);
                assertEquals(rules + " " + type, Unit.MoveType.MOVE_NO_ATTACK_CIVILIAN,
                             ship.getMoveType(w.east(at)));
                assertVerdict(rules + " " + type, "SHIPCOMBAT",
                    ClassicNotices.Who.ADMIRAL, ClassicIllegalMoves.judge(ship, w.east(at)));
                ship.dispose();
            }
            for (String type : new String[] { "privateer", "frigate" }) {
                final Unit ship = w.unit(at, w.dutch, type);
                assertNull(rules + " " + type, ClassicIllegalMoves.judge(ship, w.east(at)));
                ship.dispose();
            }
            // Without moves the refusal is the same (the rule, not the moves).
            final Unit ship = w.unit(at, w.dutch, "merchantman");
            ship.setMovesLeft(0);
            assertVerdict(rules + " no moves", "SHIPCOMBAT", ClassicNotices.Who.ADMIRAL,
                ClassicIllegalMoves.judge(ship, w.east(at)));
        }
    }

    /**
     * A civilian on land into a foreign unit: @CANNOTATTACK with the
     * soldier.  A soldier and a scout attack.  A colonist into a foreign
     * colony meets the colony's box (part N6, {@link #testForeignColonyAudit}).
     */
    public void testCannotAttack() {
        for (String rules : RULES) {
            final World w = new World(rules);
            final Tile at = w.tile(3, 4);
            w.unit(w.tile(4, 4), w.french, "veteranSoldier", "soldier");
            final Unit[] civilians = {
                w.unit(at, w.dutch, "freeColonist"),
                w.unit(at, w.dutch, "wagonTrain"),
                w.unit(at, w.dutch, "hardyPioneer", "pioneer"),
                w.unit(at, w.dutch, "treasureTrain"),
            };
            for (Unit u : civilians) {
                assertVerdict(rules + " " + u, "CANNOTATTACK", ClassicNotices.Who.SOLDIER,
                    ClassicIllegalMoves.judge(u, w.east(at)));
            }
            assertNull(ClassicIllegalMoves.judge(w.unit(at, w.dutch, "veteranSoldier",
                "soldier"), w.east(at)));
            assertNull(ClassicIllegalMoves.judge(w.unit(at, w.dutch, "seasonedScout",
                "scout"), w.east(at)));
            // A colonist at a foreign colony: no longer @CANNOTATTACK (N6).
            final Tile c = w.tile(3, 8);
            w.colony(w.french, "Port", w.tile(4, 8));
            final Unit colonist = w.unit(c, w.dutch, "freeColonist");
            assertEquals(Unit.MoveType.MOVE_NO_ACCESS_SETTLEMENT,
                         colonist.getMoveType(w.east(c)));
            assertSame(rules + " colony", ClassicIllegalMoves.VISIT,
                ClassicIllegalMoves.judge(colonist, w.east(c)));
        }
    }

    /**
     * Part N6 (Roger 2026-10-09 09:50: the pioneer at a French colony got
     * "Diese Art von Einheit kann nicht angreifen", "Richtig wäre der
     * Dialog: "Bürgermeister treffen usw.""): every unit type moved into
     * a colony of another European at peace and at war, and into a
     * contacted village, from land.
     * <ul>
     *   <li>free colonist, expert, pioneer, missionary at the colony: the
     *       colony's box ({@link ClassicIllegalMoves#VISIT}), at peace and
     *       at war; a passenger aboard a ship: @LANDFIRST; the treasure
     *       train: @CANNOTATTACK (I);</li>
     *   <li>soldier, dragoon, artillery: the controller's attack (the
     *       treaty question, {@link ClassicGUI#confirmHostileAction}); the
     *       scout: the controller's @SCOUTCOLONY; the wagon train: the
     *       trade refusals ({@link #testTradeRefusals});</li>
     *   <li>at the village: nothing is @CANNOTATTACK (the controller's
     *       village boxes, or the village refusals).</li>
     * </ul>
     */
    public void testForeignColonyAudit() {
        for (String rules : RULES) {
            for (Stance stance : new Stance[] { Stance.PEACE, Stance.WAR }) {
                final World w = new World(rules);
                w.meet(w.dutch, w.french, stance);
                w.colony(w.french, "Port", w.tile(4, 6));
                final Tile at = w.tile(3, 6);
                final String what = rules + " " + stance + " ";
                final Unit[] visitors = {
                    w.unit(at, w.dutch, "freeColonist"),
                    w.unit(at, w.dutch, "masterCarpenter"),
                    w.unit(at, w.dutch, "hardyPioneer", "pioneer"),
                    w.unit(at, w.dutch, "freeColonist", "pioneer"),
                    w.unit(at, w.dutch, "jesuitMissionary", "missionary"),
                    w.unit(at, w.dutch, "freeColonist", "missionary"),
                };
                for (Unit u : visitors) {
                    assertEquals(what + u, Unit.MoveType.MOVE_NO_ACCESS_SETTLEMENT,
                                 u.getMoveType(w.east(at)));
                    assertSame(what + u, ClassicIllegalMoves.VISIT,
                               ClassicIllegalMoves.judge(u, w.east(at)));
                }
                final Unit treasure = w.unit(at, w.dutch, "treasureTrain");
                assertVerdict(what + "treasure", "CANNOTATTACK",
                    ClassicNotices.Who.SOLDIER, ClassicIllegalMoves.judge(treasure,
                        w.east(at)));
                for (String[] armed : new String[][] {
                        { "veteranSoldier", "soldier" }, { "freeColonist", "dragoon" },
                        { "artillery", null }, { "seasonedScout", "scout" } }) {
                    final Unit u = (armed[1] == null) ? w.unit(at, w.dutch, armed[0])
                        : w.unit(at, w.dutch, armed[0], armed[1]);
                    assertNull(what + u, ClassicIllegalMoves.judge(u, w.east(at)));
                }
                assertEquals(Unit.MoveType.ENTER_FOREIGN_COLONY_WITH_SCOUT,
                    w.unit(at, w.dutch, "seasonedScout", "scout").getMoveType(w.east(at)));
                assertEquals(Unit.MoveType.ATTACK_SETTLEMENT,
                    w.unit(at, w.dutch, "artillery").getMoveType(w.east(at)));
                // A passenger next to the colony: go ashore first.
                final Tile sea = w.tile(10, 6);
                final Colony harbour = w.colony(w.french, "Harbour", w.tile(9, 6));
                assertNotNull(harbour);
                final Unit ship = w.unit(sea, w.dutch, "caravel");
                final Unit passenger = w.unit(ship, w.dutch, "freeColonist");
                final Direction west = w.map.getDirection(sea, w.tile(9, 6));
                assertEquals(Unit.MoveType.MOVE_NO_ACCESS_SETTLEMENT,
                             passenger.getMoveType(west));
                assertVerdict(what + "passenger", "LANDFIRST", ClassicNotices.Who.SCOUT,
                    ClassicIllegalMoves.judge(passenger, west));

                // The village: never @CANNOTATTACK.
                w.village(w.tile(4, 10));
                w.meet(w.dutch, w.arawak, Stance.PEACE);
                final Tile v = w.tile(3, 10);
                for (String[] type : new String[][] {
                        { "freeColonist", null }, { "masterCarpenter", null },
                        { "hardyPioneer", "pioneer" }, { "jesuitMissionary", "missionary" },
                        { "veteranSoldier", "soldier" }, { "freeColonist", "dragoon" },
                        { "artillery", null }, { "seasonedScout", "scout" },
                        { "wagonTrain", null } }) {
                    final Unit u = (type[1] == null) ? w.unit(v, w.dutch, type[0])
                        : w.unit(v, w.dutch, type[0], type[1]);
                    final ClassicIllegalMoves.Verdict x
                        = ClassicIllegalMoves.judge(u, w.east(v));
                    assertTrue(what + "village " + u + " " + u.getMoveType(w.east(v))
                        + " " + x, x == null || !"CANNOTATTACK".equals(x.section));
                    assertNotSame(what + "village " + u, ClassicIllegalMoves.VISIT, x);
                }
                // The treasure train cannot go in anywhere (I).
                assertVerdict(what + "village treasure", "CANNOTATTACK",
                    ClassicNotices.Who.SOLDIER, ClassicIllegalMoves.judge(
                        w.unit(v, w.dutch, "treasureTrain"), w.east(v)));
            }
        }
    }

    /**
     * From aboard a ship onto land a foreign unit holds: @LANDFIRST with
     * the frontiersman, for the ship's order and the passenger's own; an
     * empty ship does nothing; onto free land the landing (W8b) is the
     * controller's, an empty ship's order does nothing.
     */
    public void testLandFirst() {
        for (String rules : RULES) {
            final World w = new World(rules);
            final Tile sea = w.tile(10, 5), shore = w.tile(9, 5);
            final Direction west = w.map.getDirection(sea, shore);
            w.unit(shore, w.arawak, "brave");
            final Unit ship = w.unit(sea, w.dutch, "caravel");
            assertEquals(Unit.MoveType.MOVE_NO_ACCESS_LAND, ship.getMoveType(west));
            assertEquals(ClassicIllegalMoves.SILENT, ClassicIllegalMoves.judge(ship, west));
            final Unit colonist = w.unit(ship, w.dutch, "freeColonist");
            final Unit soldier = w.unit(ship, w.dutch, "veteranSoldier", "soldier");
            assertVerdict(rules + " ship", "LANDFIRST", ClassicNotices.Who.SCOUT,
                ClassicIllegalMoves.judge(ship, west));
            assertVerdict(rules + " colonist aboard", "LANDFIRST", ClassicNotices.Who.SCOUT,
                ClassicIllegalMoves.judge(colonist, west));
            assertEquals(Unit.MoveType.MOVE_NO_ATTACK_MARINE, soldier.getMoveType(west));
            assertVerdict(rules + " soldier aboard", "LANDFIRST", ClassicNotices.Who.SCOUT,
                ClassicIllegalMoves.judge(soldier, west));
            // Free land: the landing box, the controller's.
            final Tile sea2 = w.tile(10, 9), shore2 = w.tile(9, 9);
            final Direction west2 = w.map.getDirection(sea2, shore2);
            final Unit loaded = w.unit(sea2, w.dutch, "caravel");
            w.unit(loaded, w.dutch, "freeColonist");
            assertNull(ClassicIllegalMoves.judge(loaded, west2));
            final Unit empty = w.unit(w.tile(10, 11), w.dutch, "caravel");
            assertEquals(ClassicIllegalMoves.SILENT, ClassicIllegalMoves.judge(empty,
                w.map.getDirection(w.tile(10, 11), w.tile(9, 11))));
        }
    }

    /**
     * A ship or wagon train at a foreign colony: at war, or not contacted,
     * @TRADEATWAR (its words name both); contacted at peace without de
     * Witt @TRADEMERCANTILISM in FreeCol's words; with de Witt the trade is
     * the controller's.
     */
    public void testTradeRefusals() {
        for (String rules : RULES) {
            final World w = new World(rules);
            final Tile at = w.tile(3, 6);
            w.colony(w.french, "Port", w.tile(4, 6));
            final Unit wagon = w.unit(at, w.dutch, "wagonTrain");
            assertFalse(w.dutch.hasContacted(w.french));
            assertEquals(Unit.MoveType.MOVE_NO_ACCESS_TRADE, wagon.getMoveType(w.east(at)));
            assertVerdict(rules + " uncontacted", "TRADEATWAR", ClassicNotices.Who.NONE,
                ClassicIllegalMoves.judge(wagon, w.east(at)));
            w.meet(w.dutch, w.french, Stance.WAR);
            assertEquals(Unit.MoveType.MOVE_NO_ACCESS_WAR, wagon.getMoveType(w.east(at)));
            assertVerdict(rules + " war", "TRADEATWAR", ClassicNotices.Who.NONE,
                ClassicIllegalMoves.judge(wagon, w.east(at)));
            w.meet(w.dutch, w.french, Stance.PEACE);
            final ClassicIllegalMoves.Verdict m = ClassicIllegalMoves.judge(wagon, w.east(at));
            assertVerdict(rules + " peace", "TRADEMERCANTILISM", ClassicNotices.Who.NONE, m);
            assertTrue(m.freeColOnly);
            assertEquals("move.noAccessTrade", m.freeCol.getId());
            final ClassicAdvisorBox.Request r = ClassicIllegalMoves.request(
                ClassicText.load(ClassicPackFiles.runtime()), m, 0L, "x");
            assertEquals(Messages.message(m.freeCol), r.plainText());
            w.dutch.addFather(w.spec.getFoundingFather("model.foundingFather.janDeWitt"));
            assertNull(rules + " de Witt", ClassicIllegalMoves.judge(wagon, w.east(at)));
        }
    }

    /**
     * At a village: an empty wagon train @TRADENOCARGO with the tribe's
     * chief; a ship at a village not yet contacted @DONTKNOWSHIPS with the
     * admiral; an expert and a colonist without moves are the controller's.
     */
    public void testVillageRefusals() {
        for (String rules : RULES) {
            final World w = new World(rules);
            final IndianSettlement is = w.village(w.tile(9, 7));
            w.meet(w.dutch, w.arawak, Stance.PEACE);
            final Tile land = w.tile(8, 7), sea = w.tile(10, 7);
            final Unit wagon = w.unit(land, w.dutch, "wagonTrain");
            final ClassicIllegalMoves.Verdict v = ClassicIllegalMoves.judge(wagon, w.east(land));
            assertVerdict(rules + " empty wagon", "TRADENOCARGO", ClassicNotices.Who.CHIEF, v);
            assertEquals(ClassicGUI.TRIBES.indexOf("arawak"), v.tribe);
            final Unit ship = w.unit(sea, w.dutch, "merchantman");
            final Direction west = w.map.getDirection(sea, is.getTile());
            assertEquals(Unit.MoveType.MOVE_NO_ACCESS_CONTACT, ship.getMoveType(west));
            assertVerdict(rules + " ship", "DONTKNOWSHIPS", ClassicNotices.Who.ADMIRAL,
                ClassicIllegalMoves.judge(ship, west));
            final Unit expert = w.unit(land, w.dutch, "expertFarmer");
            assertEquals(Unit.MoveType.MOVE_NO_ACCESS_SKILL, expert.getMoveType(w.east(land)));
            assertNull(ClassicIllegalMoves.judge(expert, w.east(land)));
            final Unit tired = w.unit(w.tile(5, 5), w.dutch, "freeColonist");
            tired.setMovesLeft(0);
            assertEquals(Unit.MoveType.MOVE_NO_MOVES, tired.getMoveType(w.east(tired.getTile())));
            assertNull(ClassicIllegalMoves.judge(tired, w.east(tired.getTile())));
        }
    }

    /**
     * Refused moves the original has no words for do nothing: a colonist
     * into the sea without our ship, onto a full ship, off the map; a
     * legal move and the null cases are the controller's.
     */
    public void testSilentAndLegal() {
        for (String rules : RULES) {
            final World w = new World(rules);
            final Tile shore = w.tile(9, 3), sea = w.tile(10, 3);
            final Unit colonist = w.unit(shore, w.dutch, "freeColonist");
            assertEquals(Unit.MoveType.MOVE_NO_ACCESS_EMBARK, colonist.getMoveType(w.east(shore)));
            assertEquals(ClassicIllegalMoves.SILENT, ClassicIllegalMoves.judge(colonist, w.east(shore)));
            final Unit caravel = w.unit(sea, w.dutch, "caravel");
            while (caravel.getSpaceLeft() > 0) w.unit(caravel, w.dutch, "freeColonist");
            assertEquals(Unit.MoveType.MOVE_NO_ACCESS_FULL, colonist.getMoveType(w.east(shore)));
            assertEquals(ClassicIllegalMoves.SILENT, ClassicIllegalMoves.judge(colonist, w.east(shore)));
            final Tile edge = w.tile(0, 3);
            final Unit out = w.unit(edge, w.dutch, "freeColonist");
            for (Direction d : Direction.values()) {
                if (edge.getNeighbourOrNull(d) != null) continue;
                assertEquals(ClassicIllegalMoves.SILENT, ClassicIllegalMoves.judge(out, d));
            }
            final Unit walker = w.unit(w.tile(4, 10), w.dutch, "freeColonist");
            assertNull(ClassicIllegalMoves.judge(walker, w.east(walker.getTile())));
            assertNull(ClassicIllegalMoves.judge(null, Direction.E));
            assertNull(ClassicIllegalMoves.judge(walker, null));
        }
    }

    /**
     * The rebels' ship ordered past the edge from the high seas: Europe is
     * closed (@EUROPENOTLEAVE, the admiral); before the war it is Roger's
     * Europe question's, not a refusal.
     */
    public void testEuropeClosedInTheWar() {
        final World w = new World("levi");
        final Tile edge = w.tile(18, 7);
        final Unit ship = w.unit(edge, w.dutch, "merchantman");
        assertTrue(ClassicGUI.asksSailHome(ship, Direction.E));
        assertNull(ClassicIllegalMoves.judge(ship, Direction.E));
        w.dutch.changePlayerType(Player.PlayerType.REBEL);
        w.dutch.setEurope(null);
        assertFalse(ClassicGUI.asksSailHome(ship, Direction.E));
        assertVerdict("rebels", "EUROPENOTLEAVE", ClassicNotices.Who.ADMIRAL,
            ClassicIllegalMoves.judge(ship, Direction.E));
        assertNull(ClassicIllegalMoves.judge(ship, Direction.W));
    }

    /**
     * B with a unit that cannot found a colony now: a ship @SEACOLONY, a
     * type that cannot found colonies @ONLYCOL, a colonist without moves
     * nothing, the war of independence @NOCOLONIESEITHER only where the
     * option is off (classic; never in levi, Roger's row 11).
     */
    public void testColonyRefusals() {
        for (String rules : RULES) {
            final World w = new World(rules);
            final Tile land = w.tile(4, 4);
            assertVerdict(rules + " ship", "SEACOLONY", ClassicNotices.Who.SCOUT,
                ClassicIllegalMoves.colonyRefusal(w.unit(w.tile(12, 4), w.dutch, "merchantman")));
            for (String type : new String[] { "wagonTrain", "artillery", "treasureTrain" }) {
                assertVerdict(rules + " " + type, "ONLYCOL", ClassicNotices.Who.NONE,
                    ClassicIllegalMoves.colonyRefusal(w.unit(land, w.dutch, type)));
            }
            final Unit colonist = w.unit(land, w.dutch, "freeColonist");
            assertTrue(colonist.canBuildColony());
            assertNull(ClassicIllegalMoves.colonyRefusal(colonist));
            colonist.setMovesLeft(0);
            assertEquals(ClassicIllegalMoves.SILENT, ClassicIllegalMoves.colonyRefusal(colonist));
            colonist.setMovesLeft(3);
            w.dutch.changePlayerType(Player.PlayerType.REBEL);
            final ClassicIllegalMoves.Verdict war = ClassicIllegalMoves.colonyRefusal(colonist);
            if ("levi".equals(rules)) {
                assertTrue(colonist.canBuildColony());
                assertNull(war);
            } else {
                assertFalse(colonist.canBuildColony());
                assertVerdict("classic war", "NOCOLONIESEITHER", ClassicNotices.Who.NONE, war);
            }
        }
    }

    /**
     * FreeCol's own refusals of a site in the original's words: at sea
     * (a colonist aboard) @SEACOLONY, mountains @TOOMOUNTAIN, next to a
     * colony @TOONEAR with its name.  Where FreeCol allows the colony
     * (our own free land next to our colony) nothing is refused: no rule
     * is added.
     */
    public void testSiteRefusals() {
        for (String rules : RULES) {
            final World w = new World(rules);
            final String key = "model.noClaimReason.terrain.description";
            final Unit ship = w.unit(w.tile(12, 2), w.dutch, "caravel");
            final Unit aboard = w.unit(ship, w.dutch, "freeColonist");
            assertVerdict(rules + " sea", "SEACOLONY", ClassicNotices.Who.SCOUT,
                ClassicIllegalMoves.siteRefusal(aboard, key));
            final Tile mountain = w.tile(2, 2);
            mountain.setType(w.spec.getTileType("model.tile.mountains"));
            final Unit climber = w.unit(mountain, w.dutch, "freeColonist");
            assertVerdict(rules + " mountains", "TOOMOUNTAIN", ClassicNotices.Who.SCOUT,
                ClassicIllegalMoves.siteRefusal(climber, key));
            assertNull(ClassicIllegalMoves.siteRefusal(climber, "info.notEnoughGold"));
            // Next to a colony: a tile its colony works, and native land.
            final Colony colony = w.colony(w.dutch, "Fort Oranje", w.tile(5, 10));
            final Tile worked = w.tile(5, 9);
            final Unit worker = w.unit(colony.getTile(), w.dutch, "freeColonist");
            worker.setLocation(colony.getColonyTile(worked));
            assertTrue(worked.isInUse());
            final Unit settler = w.unit(worked, w.dutch, "freeColonist");
            final ClassicIllegalMoves.Verdict near = ClassicIllegalMoves.siteRefusal(settler,
                "model.noClaimReason.worked.description");
            assertVerdict(rules + " worked", "TOONEAR", ClassicNotices.Who.SCOUT, near);
            assertEquals("Fort Oranje", near.values.get("STRING0"));
            final ClassicAdvisorBox.Request r = ClassicIllegalMoves.request(null, near, 0L, "x");
            assertTrue(r.plainText(), r.plainText().contains("Fort Oranje"));
            // Our own land the colony does not work: FreeCol allows it.
            final Tile free = w.tile(6, 10);
            assertSame(w.dutch, free.getOwner());
            assertFalse(free.isInUse());
            final Unit other = w.unit(free, w.dutch, "freeColonist");
            assertEquals(Player.NoClaimReason.NONE,
                         w.dutch.canClaimToFoundSettlementReason(free));
            assertNull(ClassicIllegalMoves.siteRefusal(other, key));
        }
    }

    /**
     * P and R when their order cannot be given: no pioneer @ONLYPIO,
     * ploughed land @NOPLOW, a road @NOROAD; a ship and every other cause
     * nothing.
     */
    public void testOrderRefusals() {
        for (String rules : RULES) {
            final World w = new World(rules);
            final Tile t = w.tile(3, 3);
            final Unit colonist = w.unit(t, w.dutch, "freeColonist");
            assertVerdict(rules + " P", "ONLYPIO", ClassicNotices.Who.NONE,
                ClassicIllegalMoves.orderRefusal(colonist, false));
            assertVerdict(rules + " R", "ONLYPIO", ClassicNotices.Who.NONE,
                ClassicIllegalMoves.orderRefusal(colonist, true));
            final Unit pioneer = w.unit(t, w.dutch, "hardyPioneer", "pioneer");
            assertEquals(ClassicIllegalMoves.SILENT, ClassicIllegalMoves.orderRefusal(pioneer, false));
            assertEquals(ClassicIllegalMoves.SILENT, ClassicIllegalMoves.orderRefusal(pioneer, true));
            final TileImprovement plow = new TileImprovement(w.game, t,
                w.spec.getTileImprovementType("model.improvement.plow"), null);
            plow.setTurnsToComplete(0);
            t.add(plow);
            assertVerdict(rules + " ploughed", "NOPLOW", ClassicNotices.Who.NONE,
                ClassicIllegalMoves.orderRefusal(pioneer, false));
            final TileImprovement road = t.addRoad();
            assertEquals(ClassicIllegalMoves.SILENT, ClassicIllegalMoves.orderRefusal(pioneer, true));
            road.setTurnsToComplete(0);
            assertVerdict(rules + " road", "NOROAD", ClassicNotices.Who.NONE,
                ClassicIllegalMoves.orderRefusal(pioneer, true));
            assertEquals(ClassicIllegalMoves.SILENT, ClassicIllegalMoves.orderRefusal(
                w.unit(w.tile(12, 3), w.dutch, "merchantman"), true));
            assertEquals(ClassicIllegalMoves.SILENT, ClassicIllegalMoves.orderRefusal(null, false));
        }
    }

    /**
     * Every box's words: GAME.TXT's with the pack (its {@code @width}),
     * FreeCol's fallback without it; every fallback key exists in both
     * languages.
     */
    public void testWords() {
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        for (String s : new String[] { "SHIPCOMBAT", "CANNOTATTACK", "LANDFIRST",
                "SEACOLONY", "ONLYCOL", "ONLYPIO", "NOPLOW", "NOROAD", "TOOMOUNTAIN",
                "DONTKNOWSHIPS", "TRADEATWAR", "TRADENOCARGO", "EUROPENOTLEAVE",
                "NOCOLONIESEITHER", "TOONEAR" }) {
            final ClassicIllegalMoves.Verdict v = new ClassicIllegalMoves.Verdict(s,
                ClassicNotices.Who.NONE, -1, java.util.Map.of("STRING0", "Nowhere"),
                net.sf.freecol.common.model.StringTemplate.template(
                    ClassicIllegalMoves.FALLBACK + s).addName("%colony%", "Nowhere"),
                false);
            final ClassicAdvisorBox.Request f = ClassicIllegalMoves.request(null, v, 0L, "x");
            assertTrue(s, f.isNotice());
            assertFalse(s, f.plainText().startsWith(ClassicIllegalMoves.FALLBACK));
            if (t == null) continue;
            final ClassicAdvisorBox.Request r = ClassicIllegalMoves.request(t, v, 0L, "x");
            assertTrue(s, r.isNotice());
            assertEquals(s, t.message(s).width.intValue(), r.width);
        }
        if (t == null) {
            System.err.println("ClassicIllegalMovesTest: testWords without the pack texts");
            return;
        }
        final ClassicIllegalMoves.Verdict v = new ClassicIllegalMoves.Verdict("SHIPCOMBAT",
            ClassicNotices.Who.ADMIRAL, -1, null, null, false);
        final ClassicAdvisorBox.Request r = ClassicIllegalMoves.request(t, v, 0L, "x");
        assertEquals("Nur Kaperschiffe und Fregatten können gegnerische Schiffe angreifen.",
                     r.plainText());
        assertEquals(190, r.width);
        assertSame(ClassicAdvisorBox.Portrait.ADMIRAL, r.portrait);
    }

    /**
     * The key map hands a key none of whose actions can fire to the
     * refusal (P, R), and only then.
     */
    public void testKeyMapHandsOverARefusedKey() {
        final JPanel host = new JPanel();
        final List<String> fired = new ArrayList<>();
        final List<String> refused = new ArrayList<>();
        final java.util.Map<String, Action> actions = new HashMap<>();
        final boolean[] on = { false };
        for (String id : new String[] { "plowAction", "clearForestAction" }) {
            actions.put(id, new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        fired.add(id);
                    }

                    @Override
                    public boolean isEnabled() {
                        return on[0];
                    }
                });
        }
        ClassicKeyMap.install(host, null, actions::get,
            () -> GUI.ViewMode.MOVE_UNITS, () -> ClassicMenuModel.Context.NONE,
            () -> false, () -> false, b -> refused.add(b.key.toString()));
        final InputMap im = host.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        final Action p = host.getActionMap().get(im.get(KeyStroke.getKeyStroke("P")));
        final ActionEvent e = new ActionEvent(host, ActionEvent.ACTION_PERFORMED, "key");
        p.actionPerformed(e);
        assertEquals(List.of(KeyStroke.getKeyStroke("P").toString()), refused);
        assertTrue(fired.isEmpty());
        on[0] = true;
        p.actionPerformed(e);
        assertEquals(1, refused.size());
        assertEquals(List.of("clearForestAction"), fired);
    }
}
