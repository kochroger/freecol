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

import java.util.ArrayList;
import java.util.List;

import net.sf.freecol.common.model.EuropeanNationType;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileImprovement;
import net.sf.freecol.common.model.TileImprovementType;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.TradeRoute;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * Tests of the original's unit cycle ({@link ClassicUnitCycle}, master
 * plan W5f): the start ship first and every other unit by its id, the
 * hand-overs of every clip sample (landfall, clip007, clip008, clip006),
 * which units are due (orders, goto once per turn, visits), and the held
 * letters and roads until a visit.
 */
public class ClassicUnitCycleTest extends FreeColTestCase {

    private static final UnitType colonist = spec().getUnitType("model.unit.freeColonist");
    private static final UnitType merchantman = spec().getUnitType("model.unit.merchantman");
    private static final TileType plains = spec().getTileType("model.tile.plains");

    /** A game on the coast map (land x &lt; 10, sea from x 10, high seas at x 19). */
    private static final class Coast {
        final Game game = getStandardGame();
        final Map map = getCoastTestMap(plains, true);
        final Player dutch;

        Coast() {
            this.game.changeMap(this.map);
            this.dutch = this.game.getPlayerByNationId("model.nation.dutch");
        }

        Unit land(int x, int y) {
            return new ServerUnit(this.game, this.map.getTile(x, y), this.dutch, colonist);
        }

        Unit ship(int x, int y) {
            return new ServerUnit(this.game, this.map.getTile(x, y), this.dutch, merchantman);
        }

        Unit aboard(Unit ship) {
            return new ServerUnit(this.game, ship, this.dutch, colonist);
        }

        /** A passenger goes ashore with all its moves (FreeCol's landing). */
        void lands(Unit u, int x, int y) {
            u.setLocation(this.map.getTile(x, y));
            u.setMovesLeft(0);
        }

        /** "An Land gehen" wakes the passengers left aboard (W18). */
        void wake(Unit ship) {
            for (Unit u : ship.getUnitList()) u.setState(Unit.UnitState.ACTIVE);
        }
    }

    @Override
    protected void tearDown() throws Exception {
        ClassicUnitCycle.show(null);
        super.tearDown();
    }


    /**
     * The order: FreeCol makes the start units in one batch, the ship last
     * (c1-edge.fsg: pioneer 5885, soldier 5886, merchantman 5887); the
     * original's start ship is its unit 0.  The empty ship ranks first,
     * also carrying only later units; a ship bought later ranks by its id,
     * after older colonists, also while it carries them; with the start
     * pioneer and soldier gone the start ship is still first; the window
     * is the nation type's start count, none for natives.
     */
    public void testRankStartShipFirst() {
        final Coast c = new Coast();
        final Unit pioneer = c.land(5, 7), soldier = c.land(5, 8);
        final Unit ship = c.ship(10, 7);
        final Unit farmer = c.land(4, 7), scout = c.land(4, 8);
        final int n = ClassicUnitCycle.startCount(c.dutch);
        assertEquals(((EuropeanNationType) c.dutch.getNationType()).getStartingUnits().size(), n);
        assertTrue("the start batch: " + n, n >= 3);
        assertEquals(pioneer.getIdNumber(), ClassicUnitCycle.lowestId(c.dutch));
        assertTrue(ship.getIdNumber() - pioneer.getIdNumber() < n);
        assertTrue(ClassicUnitCycle.startCarrier(ship));
        assertFalse(ClassicUnitCycle.startCarrier(pioneer));
        assertEquals(List.of(ship, pioneer, soldier, farmer, scout),
                     ClassicUnitCycle.ranked(c.dutch.getUnitSet(), c.dutch));
        assertTrue(ClassicUnitCycle.rank(ship) < ClassicUnitCycle.rank(pioneer));

        // Carrying only the later units: still first (clip007 1512 #1928).
        farmer.setLocation(ship);
        scout.setLocation(ship);
        assertEquals(List.of(ship, pioneer, soldier, farmer, scout),
                     ClassicUnitCycle.ranked(c.dutch.getUnitSet(), c.dutch));

        // A ship bought later (made after more units than the start batch).
        final List<Unit> filler = new ArrayList<>();
        Unit later = c.ship(11, 7);
        while (later.getIdNumber() - pioneer.getIdNumber() < n) {
            filler.add(later);
            later = c.ship(11, 7);
        }
        assertFalse(ClassicUnitCycle.startCarrier(later));
        farmer.setLocation(later);
        assertTrue(ClassicUnitCycle.rank(farmer) < ClassicUnitCycle.rank(later));
        assertTrue(ClassicUnitCycle.rank(soldier) < ClassicUnitCycle.rank(later));
        assertEquals(List.of(farmer, scout, later),
                     ClassicUnitCycle.after(soldier, List.of(later, scout, farmer)));

        // The start pioneer and soldier gone: the start ship is still first.
        pioneer.dispose();
        assertTrue(ClassicUnitCycle.startCarrier(ship));
        soldier.dispose();
        assertTrue(ClassicUnitCycle.startCarrier(ship));
        assertSame(ship, ClassicUnitCycle.ranked(c.dutch.getUnitSet(), c.dutch).get(0));

        // Natives have no start ship.
        assertEquals(0, ClassicUnitCycle.startCount(c.game.getPlayerByNationId("model.nation.inca")));
        assertEquals(0, ClassicUnitCycle.startCount(null));
    }

    /**
     * Every hand-over the clips show (spec section 1.2), with FreeCol's
     * real start order.  The row clip007 #3697 is the one F2's carrier
     * rank failed (it put the pioneer before the empty ship).
     */
    public void testClipSequences() {
        // Landfall, every turn start 1498-1503: ship, pioneer, soldier.
        Coast c = new Coast();
        Unit pioneer = c.land(5, 7), soldier = c.land(5, 8), ship = c.ship(10, 7);
        final ClassicUnitCycle cycle = new ClassicUnitCycle();
        assertSame("LF turn start", ship, cycle.next(null, c.dutch));
        ship.setMovesLeft(0);
        assertSame("LF 1502 #24331", pioneer, cycle.next(ship, c.dutch));
        pioneer.setMovesLeft(0);
        assertSame("LF turn start, third", soldier, cycle.next(pioneer, c.dutch));
        soldier.setMovesLeft(0);
        assertNull(cycle.next(soldier, c.dutch));

        // Landfall L: the pioneer lands, the soldier aboard comes
        // (#13118); after the soldier, the ship (#13649).
        c = new Coast();
        pioneer = c.land(5, 7);
        soldier = c.land(5, 8);
        ship = c.ship(10, 7);
        pioneer.setLocation(ship);
        soldier.setLocation(ship);
        c.lands(pioneer, 9, 7);
        c.wake(ship);
        assertSame("LF #13118", soldier, cycle.next(pioneer, c.dutch));
        c.lands(soldier, 9, 7);
        assertSame("LF #13649", ship, cycle.next(soldier, c.dutch));

        // clip007 1512: the ship brings the farmer and the scout bought in
        // Europe; the pioneer and the soldier stand on land with moves.
        c = new Coast();
        pioneer = c.land(5, 7);
        soldier = c.land(5, 8);
        ship = c.ship(10, 7);
        final Unit farmer = c.aboard(ship), scout = c.aboard(ship);
        assertSame("c7 #1928", ship, cycle.next(null, c.dutch));
        c.lands(farmer, 9, 7);
        c.wake(ship);
        assertSame("c7 #3107", scout, cycle.next(farmer, c.dutch));
        c.lands(scout, 9, 7);
        assertTrue(ship.getUnitList().isEmpty());
        assertTrue(pioneer.getMovesLeft() > 0 && soldier.getMovesLeft() > 0);
        assertSame("c7 #3697: the empty ship", ship, cycle.next(scout, c.dutch));
        ship.setMovesLeft(0);
        assertSame("c7 #4579", pioneer, cycle.next(ship, c.dutch));
        // Landing 2 (1513): the soldier boarded before the pioneer.
        ship.setMovesLeft(3);
        farmer.setMovesLeft(1);
        scout.setMovesLeft(0);
        soldier.setLocation(ship);
        pioneer.setLocation(ship);
        assertSame("c7 #5093", ship, cycle.next(null, c.dutch));
        c.lands(soldier, 9, 7);
        c.wake(ship);
        assertSame("c7 #5804: the farmer, not the pioneer aboard", farmer,
                   cycle.next(soldier, c.dutch));
        // Landing 3: after the pioneer, the scout on land (the soldier and
        // the farmer have no moves).
        farmer.setMovesLeft(0);
        scout.setMovesLeft(1);
        c.lands(pioneer, 9, 6);
        assertSame("c7 #6643", scout, cycle.next(pioneer, c.dutch));

        // clip008 1511: the ship docked with no moves left: the pioneer
        // (#32963); the empty ship heads the next turns (#35590, #40835).
        c = new Coast();
        pioneer = c.land(5, 7);
        soldier = c.land(5, 8);
        ship = c.ship(10, 7);
        assertSame("c8 #35590", ship, cycle.next(null, c.dutch));
        ship.setMovesLeft(0);
        assertSame("c8 #32963", pioneer, cycle.next(ship, c.dutch));
    }

    /**
     * Who is due: a skipped, sentried, fortified unit, one without moves
     * and one in Europe are not; a woken passenger aboard is (W18).  The
     * cycle wraps to the head, the head comes from null, and the anchor
     * itself comes last (W: the waiting unit after the wrap).
     */
    public void testNextWrapsAndSkipsDone() {
        final Coast c = new Coast();
        final Unit ship = c.ship(10, 7);
        final Unit a = c.land(1, 1), b = c.land(2, 2), s = c.land(3, 3),
            f = c.land(4, 4), m = c.land(5, 5), e = c.land(6, 6);
        final Unit p = c.aboard(ship);
        b.setState(Unit.UnitState.SKIPPED);
        s.setState(Unit.UnitState.SENTRY);
        f.setState(Unit.UnitState.FORTIFYING);
        f.setState(Unit.UnitState.FORTIFIED);
        m.setMovesLeft(0);
        e.setLocation(c.dutch.getEurope());
        final ClassicUnitCycle cycle = new ClassicUnitCycle();
        for (Unit u : List.of(b, s, f, m, e)) assertNull(u.toString(), cycle.kind(u));
        assertNull(cycle.kind(p));                        // asleep aboard
        p.setState(Unit.UnitState.ACTIVE);                // woken: due
        assertSame(ClassicUnitCycle.Kind.ORDERS, cycle.kind(p));
        assertSame(ship, cycle.next(null, c.dutch));
        assertSame(a, cycle.next(ship, c.dutch));
        assertSame(p, cycle.next(a, c.dutch));
        assertSame(ship, cycle.next(p, c.dutch));         // the wrap
        ship.setMovesLeft(0);
        p.setMovesLeft(0);
        assertSame(a, cycle.next(a, c.dutch));            // only itself left
        assertTrue(cycle.anyDue(c.dutch));
        a.setMovesLeft(0);
        assertNull(cycle.next(a, c.dutch));
        assertNull(cycle.next(null, c.dutch));
        assertFalse(cycle.anyDue(c.dutch));
        assertNull(cycle.kind(null));
        assertNull(cycle.next(null, null));
        assertFalse(cycle.anyDue(null));
    }

    /**
     * Clip006 (Herbst 1729, 30 units) rules out "the nearest unit first"
     * and FreeCol's tile order: after U2's last move to (37,53) came U3 at
     * (23,16), though U10 and U13 at (35,56) and U25 at (38,56) could move
     * three tiles away; after U3 at (22,17) came U4 at (26,19), with U15
     * and U17 at (23,15) two tiles away; and the turn began with U1 at row
     * 55, where the tile order begins at row 15.
     */
    public void testClip006RefutesNearestAndTileOrder() {
        final Game game = getStandardGame();
        final Map map = new MapBuilder(game).setDimensions(60, 70)
            .setBaseTileType(plains).setExploredByAll(true).build();
        game.changeMap(map);
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final int[][] at = { { 39, 55 }, { 39, 55 }, { 23, 16 }, { 26, 19 },
                             { 35, 56 }, { 35, 56 }, { 23, 15 }, { 23, 15 }, { 38, 56 } };
        final List<Unit> u = new ArrayList<>();
        for (int[] xy : at) {
            u.add(new ServerUnit(game, map.getTile(xy[0], xy[1]), dutch, colonist));
        }
        final Unit u1 = u.get(0), u2 = u.get(1), u3 = u.get(2), u4 = u.get(3);
        final ClassicUnitCycle cycle = new ClassicUnitCycle();
        assertSame(u1, cycle.next(null, dutch));
        final List<Unit> byTile = new ArrayList<>(dutch.getUnitSet());
        byTile.sort(Unit.locComparator);
        assertFalse(byTile.get(0) == u1);                 // the tile order: row 15
        u1.setMovesLeft(0);
        assertSame(u2, cycle.next(u1, dutch));
        u2.setLocation(map.getTile(37, 53));
        u2.setMovesLeft(0);
        assertSame(u3, cycle.next(u2, dutch));            // not U10 nearby
        u3.setLocation(map.getTile(22, 17));
        u3.setMovesLeft(0);
        assertSame(u4, cycle.next(u3, dutch));            // not U15 nearby
    }

    /**
     * Goto units (W5f): due as GOTO once per turn; after their run as
     * ORDERS while still active with moves (stopped early: the player's,
     * never run twice); not due skipped or without moves; again GOTO next
     * turn.  A trade-route unit is a GOTO unit; a unit with a destination
     * off the map (in Europe) or aboard is not.
     */
    public void testGotoKind() {
        final Coast c = new Coast();
        final Unit g = c.ship(12, 7);
        g.setDestination(c.map.getTile(15, 12));
        final ClassicUnitCycle cycle = new ClassicUnitCycle();
        assertSame(ClassicUnitCycle.Kind.GOTO, cycle.kind(g));
        cycle.ran(g);
        assertSame(ClassicUnitCycle.Kind.ORDERS, cycle.kind(g));
        assertSame(g, cycle.next(null, c.dutch));
        g.setState(Unit.UnitState.SKIPPED);
        assertNull(cycle.kind(g));
        g.setState(Unit.UnitState.ACTIVE);
        g.setMovesLeft(0);
        assertNull(cycle.kind(g));
        g.setMovesLeft(3);
        cycle.turnStarted(c.dutch);
        assertSame(ClassicUnitCycle.Kind.GOTO, cycle.kind(g));

        final Unit t = c.ship(13, 7);
        t.setTradeRoute(new TradeRoute(c.game, "route", c.dutch));
        assertTrue(t.isReadyToTrade());
        assertSame(ClassicUnitCycle.Kind.GOTO, cycle.kind(t));

        final Unit eu = c.ship(14, 7);
        eu.setLocation(c.dutch.getEurope());
        eu.setDestination(c.map.getTile(15, 12));
        assertTrue(eu.goingToDestination());
        assertNull(cycle.kind(eu));                       // FreeCol's end of turn sends it

        final Unit p = c.aboard(g);
        p.setState(Unit.UnitState.ACTIVE);
        p.setDestination(c.map.getTile(5, 5));
        assertNull(cycle.kind(p));
    }

    /**
     * Visits (W5f, c6): the snapshot at our end of turn, then FreeCol's
     * turn start completes the work (a road: the pioneer ACTIVE with no
     * moves, the road complete; a fortification: FORTIFIED; a plowing);
     * each is a VISIT, its letter held (R, P, the black F) and the road
     * not drawn until the visit.  A road still in progress gives no visit,
     * nor a unit gone; an incomplete road is never shown; a refused end
     * releases the holds; clear forgets everything.
     */
    public void testVisitsFromTheSnapshot() {
        final Coast c = new Coast();
        final Tile rt = c.map.getTile(3, 3), pt = c.map.getTile(5, 5),
            wt = c.map.getTile(7, 7);
        final Unit roader = c.land(3, 3), soldier = c.land(4, 4),
            plower = c.land(5, 5), worker = c.land(7, 7), lost = c.land(6, 6);
        final TileImprovement road = rt.addRoad();
        assertFalse(road.isComplete());
        assertFalse(ClassicUnitCycle.roadShown(rt));     // never while in progress
        roader.setWorkImprovement(road);
        roader.setState(Unit.UnitState.IMPROVING);
        final TileImprovementType plowType
            = spec().getTileImprovementType("model.improvement.plow");
        final TileImprovement plow = new TileImprovement(c.game, pt, plowType, null);
        pt.add(plow);
        plower.setWorkImprovement(plow);
        plower.setState(Unit.UnitState.IMPROVING);
        final TileImprovement slow = wt.addRoad();       // still in progress next turn
        worker.setWorkImprovement(slow);
        worker.setState(Unit.UnitState.IMPROVING);
        soldier.setState(Unit.UnitState.FORTIFYING);
        lost.setState(Unit.UnitState.FORTIFYING);
        final ClassicUnitCycle cycle = new ClassicUnitCycle();
        ClassicUnitCycle.show(cycle);
        assertEquals(ClassicHud.ORDERS_ROAD, ClassicUnitCycle.ordersRowShown(roader));

        // Our end of turn; the AI phase; FreeCol's turn start.
        cycle.snapshot(c.dutch);
        road.setTurnsToComplete(0);
        roader.setState(Unit.UnitState.ACTIVE);
        roader.setMovesLeft(0);
        plow.setTurnsToComplete(0);
        plower.setState(Unit.UnitState.ACTIVE);
        plower.setMovesLeft(0);
        soldier.setState(Unit.UnitState.FORTIFIED);
        lost.dispose();
        // Held before the turn start already (the server's changes come first).
        assertEquals(ClassicHud.ORDERS_ROAD, ClassicUnitCycle.ordersRowShown(roader));
        assertEquals(ClassicHud.ORDERS_FORTIFY, ClassicUnitCycle.ordersRowShown(soldier));
        assertFalse(ClassicUnitCycle.roadShown(rt));
        cycle.turnStarted(c.dutch);
        assertEquals(3, cycle.pendingVisits());
        assertSame(ClassicUnitCycle.Kind.VISIT, cycle.kind(roader));
        assertSame(ClassicUnitCycle.Kind.VISIT, cycle.kind(soldier));
        assertSame(ClassicUnitCycle.Kind.VISIT, cycle.kind(plower));
        assertNull(cycle.kind(worker));                  // no visit, still R
        assertEquals(ClassicHud.ORDERS_ROAD, ClassicUnitCycle.ordersRowShown(worker));
        assertEquals(ClassicHud.ORDERS_ROAD, ClassicUnitCycle.ordersRowShown(roader));
        assertEquals(ClassicHud.ORDERS_PLOW, ClassicUnitCycle.ordersRowShown(plower));
        assertEquals(ClassicHud.ORDERS_FORTIFY, ClassicUnitCycle.ordersRowShown(soldier));
        assertFalse(ClassicUnitCycle.roadShown(rt));     // held until the visit
        assertFalse(ClassicUnitCycle.roadShown(wt));     // not complete
        // The visits come in the cycle's order, the units have no moves.
        assertSame(roader, cycle.next(null, c.dutch));
        cycle.visited(roader);
        assertEquals(ClassicHud.ORDERS_NONE, ClassicUnitCycle.ordersRowShown(roader));
        assertTrue(ClassicUnitCycle.roadShown(rt));
        assertNull(cycle.kind(roader));
        assertSame(soldier, cycle.next(roader, c.dutch));
        cycle.visited(soldier);
        assertEquals(ClassicHud.ORDERS_FORTIFIED, ClassicUnitCycle.ordersRowShown(soldier));
        cycle.visited(plower);
        assertEquals(ClassicHud.ORDERS_NONE, ClassicUnitCycle.ordersRowShown(plower));
        assertFalse(cycle.anyDue(c.dutch));               // the visits done

        // A refused end releases the holds; clear forgets everything.
        final TileImprovement road2 = c.map.getTile(8, 8).addRoad();
        final Unit r2 = c.land(8, 8);
        r2.setWorkImprovement(road2);
        r2.setState(Unit.UnitState.IMPROVING);
        cycle.snapshot(c.dutch);
        r2.setState(Unit.UnitState.ACTIVE);
        assertEquals(ClassicHud.ORDERS_ROAD, ClassicUnitCycle.ordersRowShown(r2));
        cycle.endRefused();
        assertEquals(ClassicHud.ORDERS_NONE, ClassicUnitCycle.ordersRowShown(r2));
        cycle.snapshot(c.dutch);
        cycle.clear();
        assertEquals(0, cycle.pendingVisits());
        // Without a shown cycle: the unit's own row, complete roads only.
        ClassicUnitCycle.show(null);
        assertEquals(ClassicHud.ORDERS_ROAD, ClassicUnitCycle.ordersRowShown(worker));
        assertTrue(ClassicUnitCycle.roadShown(rt));
        assertFalse(ClassicUnitCycle.roadShown(wt));
        assertFalse(ClassicUnitCycle.roadShown(null));
    }

    /**
     * The order survives a save and a load: it comes from the ids, which
     * a copy (the save format) keeps.
     */
    public void testRanksSurviveSave() {
        final Coast c = new Coast();
        final Unit pioneer = c.land(5, 7), soldier = c.land(5, 8);
        final Unit ship = c.ship(10, 7);
        final Unit copy = ship.copy(c.game);
        assertEquals(ship.getId(), copy.getId());
        assertEquals(ClassicUnitCycle.rank(ship), ClassicUnitCycle.rank(copy));
        assertTrue(ClassicUnitCycle.startCarrier(copy));
        final Unit pc = pioneer.copy(c.game);
        assertEquals(ClassicUnitCycle.rank(pioneer), ClassicUnitCycle.rank(pc));
        assertTrue(ClassicUnitCycle.rank(pc) < ClassicUnitCycle.rank(soldier));
    }
}
