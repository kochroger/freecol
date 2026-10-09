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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.sf.freecol.common.model.Europe;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.ModelMessage;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TradeRoute;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.server.ServerTestHelper;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * Our voyages (master plan W13, spec R4): the arrivals at a turn start (and
 * none otherwise: no reminder, no load fallback), Europe's close after its
 * last ship sailed, a timed band's end, and the arrival chain's schedule on
 * a test clock, with a Europe behind the map and a failing host.
 */
public class ClassicVoyagesTest extends FreeColTestCase {

    private static final UnitType merchantman
        = spec().getUnitType("model.unit.merchantman");
    private static final UnitType galleon
        = spec().getUnitType("model.unit.galleon");
    private static final UnitType colonist
        = spec().getUnitType("model.unit.freeColonist");

    private static final long MS = 1_000_000L;

    private Game game;
    private Map map;
    private Player dutch;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.game = getStandardGame();
        this.map = getTestMap(spec().getTileType("model.tile.ocean"), true);
        this.game.changeMap(this.map);
        this.dutch = this.game.getPlayerByNationId("model.nation.dutch");
    }

    /** A ship of ours at sea, bound for Europe or the New World. */
    private Unit atSea(UnitType type, boolean toEurope) {
        final Unit u = new ServerUnit(this.game, this.dutch.getEurope(), this.dutch, type);
        u.setLocation(this.dutch.getHighSeas());
        u.setDestination((toEurope) ? this.dutch.getEurope() : this.map);
        u.setWorkLeft(1);
        return u;
    }

    /** What the server does at the arrival in Europe (ServerUnit.csNewTurn). */
    private static void arriveInEurope(Unit u) {
        u.setWorkLeft(0);
        u.setDestination(null);
        u.setLocation(u.getOwner().getEurope());
    }


    /**
     * A ship noted at sea that is in Europe at the next turn start has
     * arrived there; one that is on the map has come back; a ship still
     * at sea, a foreign ship and a ship on a trade route have not.  Taken
     * once per turn, which forgets the notes (R4 verifier, hang 1).
     */
    public void testArrivals() {
        final ClassicVoyages v = new ClassicVoyages();
        final Unit a = atSea(merchantman, true);
        final Unit b = atSea(galleon, false);
        final Unit c = atSea(merchantman, true);   // still at sea next turn
        final Unit d = atSea(merchantman, true);   // a trade route
        final Player english = this.game.getPlayerByNationId("model.nation.english");
        final Unit e = new ServerUnit(this.game, english.getEurope(), english, merchantman);
        e.setLocation(english.getHighSeas());
        v.note(this.dutch);
        v.note(english);   // not ours: never noted as ours
        arriveInEurope(a);
        b.setLocation(this.map.getTile(5, 5));
        arriveInEurope(d);
        d.setTradeRoute(new TradeRoute(this.game, "route", this.dutch));
        arriveInEurope(e);
        final ClassicVoyages.Arrivals got = v.take(this.dutch, 2);
        assertEquals(List.of(a), got.europe);
        assertEquals(List.of(b), got.newWorld);
        assertFalse(got.isEmpty());
        // Once per turn: a second call (the wipe after the chain) gets none,
        // and the next turn too, as the notes are gone.
        assertTrue(v.take(this.dutch, 2).isEmpty());
        v.consumed(a);                              // a late message: same turn
        assertTrue(v.take(this.dutch, 2).isEmpty());
        arriveInEurope(c);
        v.note(this.dutch);                         // turn 2 shown: nothing at sea
        assertTrue(v.take(this.dutch, 3).isEmpty());
        // A fresh object (a load) reports nothing.
        assertTrue(new ClassicVoyages().take(this.dutch, 3).isEmpty());
    }

    /**
     * A consumed FreeCol arrival message counts as an arrival without a
     * note (the arrival seen only through the message), once; a message
     * still kept while our turn is shown is stale and dropped.
     */
    public void testArrivalFromTheMessage() {
        final ClassicVoyages v = new ClassicVoyages();
        final Unit a = atSea(merchantman, true);
        arriveInEurope(a);
        v.consumed(a);
        assertEquals(List.of(a), v.take(this.dutch, 2).europe);
        assertTrue(v.take(this.dutch, 2).isEmpty());
        v.consumed(a);
        v.note(this.dutch);   // our turn is shown: the message is stale
        assertTrue(v.take(this.dutch, 3).isEmpty());
    }

    /** Several ships arriving in Europe come in the port's order. */
    public void testPortOrder() {
        final ClassicVoyages v = new ClassicVoyages();
        final Unit inPort = new ServerUnit(this.game, this.dutch.getEurope(),
                                           this.dutch, galleon);
        final Unit a = atSea(merchantman, true);
        final Unit b = atSea(galleon, true);
        v.note(this.dutch);
        arriveInEurope(b);
        arriveInEurope(a);
        final Europe europe = this.dutch.getEurope();
        final List<Unit> order = new ArrayList<>();
        for (Unit u : europe.getUnitList()) if (u == a || u == b) order.add(u);
        assertEquals(2, order.size());
        assertEquals(order, v.take(this.dutch, 2).europe);
        assertTrue(europe.getUnitList().contains(inPort));
    }

    /**
     * Europe opens by itself only at an arrival (Roger, 2026-10-08; Part
     * H's guard and its load fallback are gone): with everything in Europe
     * (nothing on the map, no colony, no ship at sea), a ship left in port
     * and colonists on the dock, no turn start ever has an arrival, so no
     * chain starts and Europe is never opened as a reminder, turn after
     * turn; nor after a load of the arrival turn (the ship in port with
     * its work at 0, a fresh object as a load builds it).
     */
    public void testEuropeOpensOnlyAtAnArrival() {
        for (Unit u : new ArrayList<>(this.dutch.getUnitSet())) u.dispose();
        final Unit left = new ServerUnit(this.game, this.dutch.getEurope(),
                                         this.dutch, merchantman);
        left.setWorkLeft(-1);
        new ServerUnit(this.game, this.dutch.getEurope(), this.dutch, colonist);
        new ServerUnit(this.game, this.dutch.getEurope(), this.dutch, colonist);
        final ClassicVoyages v = new ClassicVoyages();
        for (int turn = 2; turn <= 61; turn++) {
            v.note(this.dutch);                 // our turn shown: nothing at sea
            assertTrue("turn " + turn, v.take(this.dutch, turn).isEmpty());
        }
        // The arrival turn's state after a load: in port, work 0, no note,
        // no message: no arrival, no Europe.
        final Unit a = atSea(merchantman, true);
        arriveInEurope(a);
        assertEquals(0, a.getWorkLeft());
        assertTrue(new ClassicVoyages().take(this.dutch, 62).isEmpty());
        // The same ship noted at sea in the session: an arrival, once.
        final ClassicVoyages w = new ClassicVoyages();
        final Unit b = atSea(galleon, true);
        w.note(this.dutch);
        arriveInEurope(b);
        assertEquals(List.of(b), w.take(this.dutch, 63).europe);
        assertTrue(w.take(this.dutch, 63).isEmpty());
        assertTrue(w.take(this.dutch, 64).isEmpty());
    }

    /**
     * Europe's close by itself after a ship sailed (Roger, 2026-10-08): no
     * ship left in port; colonists on the dock and ships on the high seas
     * do not keep it open, nor does a ship under repair (it cannot sail;
     * the review of part N); a second ship in port does; no Europe: never.
     */
    public void testClosesAfterSailing() {
        for (Unit u : new ArrayList<>(this.dutch.getUnitSet())) u.dispose();
        final Europe europe = this.dutch.getEurope();
        assertFalse(ClassicVoyages.closesAfterSailing(null));
        assertTrue("an empty port", ClassicVoyages.closesAfterSailing(europe));
        final Unit a = new ServerUnit(this.game, europe, this.dutch, merchantman);
        final Unit b = new ServerUnit(this.game, europe, this.dutch, galleon);
        new ServerUnit(this.game, europe, this.dutch, colonist);
        new ServerUnit(this.game, europe, this.dutch, colonist);
        assertFalse(ClassicVoyages.closesAfterSailing(europe));
        a.setLocation(this.dutch.getHighSeas());   // a sails: b is still in port
        a.setDestination(this.map);
        assertFalse(ClassicVoyages.closesAfterSailing(europe));
        b.setLocation(this.dutch.getHighSeas());   // the last ship sails
        b.setDestination(this.map);
        assertTrue("colonists on the dock", ClassicVoyages.closesAfterSailing(europe));
        atSea(merchantman, true);                  // one bound for Europe
        assertTrue("ships at sea", ClassicVoyages.closesAfterSailing(europe));
        final Unit repair = new ServerUnit(this.game, europe, this.dutch, merchantman);
        repair.setHitPoints(1);
        assertTrue(repair.isDamaged());
        assertTrue(repair.isDamagedAndUnderForcedRepair());
        assertTrue("a ship under repair", ClassicVoyages.closesAfterSailing(europe));
        repair.setHitPoints(repair.getType().getHitPoints());
        assertFalse("repaired, it can sail", ClassicVoyages.closesAfterSailing(europe));
        repair.dispose();
        assertTrue(ClassicVoyages.closesAfterSailing(europe));
    }

    /**
     * The fixer of part J (the review of part J, fidelity lens; Roger's
     * clip opening_015 #1683 -&gt; #1686, V): Set Sail takes the colonists
     * on the dock marked "S" (SENTRY) aboard first, as many as fit, in the
     * dock's order, and asks nothing; a colonist marked "-" stays; then the
     * ship sails, and with no ship left in port Europe still closes by
     * itself.  The controller is a stand-in that does what the server's
     * embark and move do to the model.
     */
    public void testSetSailTakesTheDockColonistsMarkedS() {
        for (Unit u : new ArrayList<>(this.dutch.getUnitSet())) u.dispose();
        final Europe europe = this.dutch.getEurope();
        final Unit ship = new ServerUnit(this.game, europe, this.dutch, merchantman);
        final Unit carpenter = new ServerUnit(this.game, europe, this.dutch, colonist);
        final Unit farmer = new ServerUnit(this.game, europe, this.dutch, colonist);
        final Unit third = new ServerUnit(this.game, europe, this.dutch, colonist);
        assertEquals("FreeCol: S on the dock", Unit.UnitState.SENTRY, carpenter.getState());
        farmer.setState(Unit.UnitState.ACTIVE);                // "-": stays
        final List<String> calls = new ArrayList<>();
        assertEquals(List.of(carpenter, third), ClassicEuropePanel.boarders(europe, ship));
        assertTrue(ClassicEuropePanel.sail(
                (unit, carrier) -> {                      // the server's embark
                    calls.add("board " + unit.getId());
                    unit.setLocation(carrier);
                    return true;
                },
                (unit, dest) -> {                         // the server's move
                    calls.add("sail " + unit.getId() + " aboard=" + unit.getUnitCount());
                    unit.setLocation(unit.getOwner().getHighSeas());
                    unit.setDestination(dest);
                    return true;
                }, europe, ship, this.map));
        assertEquals(List.of("board " + carpenter.getId(), "board " + third.getId(),
                             "sail " + ship.getId() + " aboard=2"), calls);
        assertSame(ship, carpenter.getLocation());
        assertSame(ship, third.getLocation());
        assertTrue("the farmer stays", farmer.isInEurope());
        assertTrue("Europe closes", ClassicVoyages.closesAfterSailing(europe));

        // Only as many as fit: a full hold takes none, a ship with one
        // place left takes the first one marked S.
        assertEquals(List.of(), ClassicEuropePanel.boarders(europe, null));
        assertEquals(List.of(), ClassicEuropePanel.boarders(null, ship));
        final Unit small = new ServerUnit(this.game, europe, this.dutch,
            spec().getUnitType("model.unit.caravel"));
        final int cap = small.getCargoCapacity();
        assertTrue(cap >= 2);
        final List<Unit> s = new ArrayList<>();
        for (int i = 0; i <= cap; i++) {                       // one more than fit
            s.add(new ServerUnit(this.game, europe, this.dutch, colonist));
        }
        assertEquals(s.subList(0, cap), ClassicEuropePanel.boarders(europe, small));
        s.remove(0).setLocation(small);                        // one place taken
        assertEquals(s.subList(0, cap - 1), ClassicEuropePanel.boarders(europe, small));
        for (Unit u : new ArrayList<>(s.subList(0, cap - 1))) u.setLocation(small);
        assertEquals(0, small.getSpaceLeft());
        assertEquals(List.of(), ClassicEuropePanel.boarders(europe, small));
        assertTrue(farmer.isInEurope());

        // No question any more: setSail has no colonist box.
        final String src;
        try {
            src = new String(java.nio.file.Files.readAllBytes(new java.io.File(
                "src/net/sf/freecol/client/gui/classic/ClassicEuropePanel.java").toPath()),
                java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        final int at = src.indexOf("private void setSail()");
        final int end = src.indexOf("static boolean sail(", at);
        assertTrue(at > 0 && end > at);
        assertFalse(src.substring(at, end).contains("modalConfirmDialog"));
        assertTrue(src.substring(at, end).contains(
            "sail(igc()::boardShip, igc()::moveTo, this.europe, ship, map);"));
    }

    /**
     * A timed band's end (europe-voyage D1): while the player has the turn
     * it goes at its time; after our end it stays through the AI phase,
     * the first unit of our next turn takes it away 2 frames later (once);
     * with no unit up the poll takes it once our turn is shown and nothing
     * is pending; a new band forgets it.
     */
    public void testBandEnd() {
        final ClassicVoyages.BandEnd e = new ClassicVoyages.BandEnd();
        assertTrue("the turn goes on", e.timeUp(true));
        assertFalse(e.expired());
        assertFalse(e.timeUp(false));             // our end went out
        assertTrue(e.expired());
        assertFalse("the AI phase", e.unitUp(false));
        assertFalse(e.poll(false, false));
        assertFalse("our turn, its first unit coming", e.poll(true, true));
        assertTrue("the first unit", e.unitUp(true));
        assertFalse("once", e.unitUp(true));
        assertFalse("scheduled already", e.poll(true, false));
        e.reset();                                // the band went
        assertFalse(e.expired());
        assertFalse(e.unitUp(true));
        assertFalse(e.timeUp(false));
        assertTrue("no unit up: the poll", e.poll(true, false));
        e.reset();                                // a new band
        assertFalse(e.poll(true, false));
        assertEquals(2 * ClassicAdvisorLayer.FRAME_MS, ClassicVoyages.BAND_AFTER_UNIT_MS);
    }

    /**
     * With the real server's turns (spec R4 section 7.5): a ship sails for
     * Europe, arrives after {@code turnsToSail} new turns with no
     * destination, and is noted and taken as an arrival, once; a load of
     * that turn (a fresh object) shows none.  Back to the New World it is
     * an arrival there.
     */
    public void testServerVoyage() {
        final Game sg = ServerTestHelper.startServerGame(getTestMap(
            spec().getTileType("model.tile.ocean"), true));
        try {
            final ServerPlayer me = (ServerPlayer) sg.getPlayerByNationId("model.nation.dutch");
            final Unit ship = new ServerUnit(sg, me.getEurope(), me, merchantman);
            final Tile entry = sg.getMap().getTile(10, 10);
            ship.setEntryLocation(entry);
            final ClassicVoyages v = new ClassicVoyages();
            ship.setLocation(me.getHighSeas());
            ship.setDestination(me.getEurope());
            ship.setWorkLeft(ship.getSailTurns());
            assertEquals(3, ship.getSailTurns());
            int turns = 0;
            while (!ship.isInEurope() && turns < 10) {
                v.note(me);
                ServerTestHelper.newTurn();
                turns++;
            }
            assertEquals(3, turns);
            assertEquals(0, ship.getWorkLeft());
            assertNull(ship.getDestination());
            assertTrue("a load of the arrival turn",
                       new ClassicVoyages().take(me, 10).isEmpty());
            assertEquals(List.of(ship), v.take(me, 10).europe);
            assertTrue(v.take(me, 10).isEmpty());
            // Our end (doEndTurn sets SKIPPED) and the next turn: the ship
            // left in port is no arrival.
            ship.setState(Unit.UnitState.SKIPPED);
            v.note(me);
            ServerTestHelper.newTurn();
            assertTrue(v.take(me, 11).isEmpty());
            // Back to the New World.
            ship.setLocation(me.getHighSeas());
            ship.setDestination(sg.getMap());
            ship.setWorkLeft(1);
            v.note(me);
            ServerTestHelper.newTurn();
            assertTrue(ship.hasTile());
            final ClassicVoyages.Arrivals back = v.take(me, 12);
            assertTrue(back.europe.isEmpty());
            assertEquals(List.of(ship), back.newWorld);
        } finally {
            ServerTestHelper.stopServerGame();
        }
    }


    // The chain on a test clock

    /** Records the chain's calls; the box and Europe as the test sets them. */
    private static final class FakeHost implements ClassicVoyages.Chain.Host {
        final List<String> calls = new ArrayList<>();
        boolean boxUp = false, europeOpen = false, hasEurope = true;
        /** Whether Europe opens when asked (false: it fails). */
        boolean opens = true;
        long now = 0L;
        /**
         * The Europe screen is open but behind the map or minimized while
         * the player is at the map (the GUI's {@code raisesEurope}).
         */
        boolean hidden = false;
        /** The host calls that throw (H REVIEW2 L1): their prefixes. */
        List<String> throwsIn = List.of();

        private void call(String s) {
            for (String t : this.throwsIn) {
                if (s.startsWith(t)) throw new IllegalStateException(s + " failed");
            }
            this.calls.add(s + " @" + (this.now / MS));
        }

        @Override public void band(String text) { call("band " + text); }
        @Override public void timedBand(String text) { call("timed " + text); }
        @Override public void clearBand() { call("clear"); }
        @Override public boolean boxUp() { return this.boxUp; }

        @Override
        public boolean openEurope() {
            if (!this.hasEurope) return false;
            if (this.europeOpen) {
                // Open already: brought up (the GUI's raiseEurope).
                call("raise arrival");
                this.hidden = false;
                return true;
            }
            call("open");
            if (this.opens) this.europeOpen = true;
            return true;
        }

        @Override public boolean europeOpen() { return this.europeOpen; }

        @Override
        public void keepEuropeUp() {
            if (!this.hidden) return;
            call("raise hold");
            this.hidden = false;
        }

        @Override public void jumpTo(Unit ship) { call("jump " + ship.getId()); }
        @Override public void done() { call("done"); }
        @Override public void event(String what) { }
    }

    /** Step the chain until {@code until} (ms), as its timer would. */
    private static void run(ClassicVoyages.Chain c, FakeHost h, long[] due, double until) {
        final long end = Math.round(until * MS);
        while (due[0] >= 0 && due[0] <= end) {
            h.now = due[0];
            due[0] = c.step(h.now);
        }
        h.now = end;
    }

    private static ClassicVoyages.Chain chain(FakeHost h, List<Unit> europe,
                                              List<Unit> newWorld) {
        return new ClassicVoyages.Chain(h, new ClassicVoyages.Arrivals(europe, newWorld),
            u -> "E " + u.getId(), u -> "N " + u.getId());
    }

    /**
     * One ship in Europe (landfall 1505): the band at once, Europe asked
     * 542 ms later, the band gone when it is open, the hold until Europe
     * closes, then done.
     */
    public void testChainToEurope() {
        final Unit a = atSea(merchantman, true);
        final FakeHost h = new FakeHost();
        final ClassicVoyages.Chain c = chain(h, List.of(a), List.of());
        final long[] due = { c.step(0L) };
        assertEquals(List.of("band E " + a.getId() + " @0"), h.calls);
        assertEquals(542 * MS, due[0]);
        run(c, h, due, 600);
        assertEquals(List.of("band E " + a.getId() + " @0", "open @542", "clear @592"),
                     h.calls);
        run(c, h, due, 60_000);                 // Europe open a minute: held
        assertFalse(c.isDone());
        assertEquals(3, h.calls.size());
        h.europeOpen = false;                   // the player closes it
        run(c, h, due, 60_100);
        assertTrue(c.isDone());
        assertEquals("done @60042", h.calls.get(3));
        assertEquals(-1L, due[0]);
    }

    /**
     * Two ships: one band each, 542 ms apart, Europe once 542 ms after the
     * last (U2); a box up at that moment: Europe after its close.
     */
    public void testChainTwoShipsAndABox() {
        final Unit a = atSea(merchantman, true), b = atSea(galleon, true);
        final FakeHost h = new FakeHost();
        final ClassicVoyages.Chain c = chain(h, List.of(a, b), List.of());
        final long[] due = { c.step(0L) };
        run(c, h, due, 1000);
        assertEquals(List.of("band E " + a.getId() + " @0",
                             "band E " + b.getId() + " @542"), h.calls);
        h.boxUp = true;                         // an emigration question
        run(c, h, due, 1500);
        assertEquals(2, h.calls.size());
        h.boxUp = false;
        run(c, h, due, 1560);
        assertEquals("open @1534", h.calls.get(2));
    }

    /**
     * Europe that does not open (its build failed, R4 verifier hang 3):
     * the chain goes on after 1 s, the band gone; no Europe at all (after
     * independence): at once.
     */
    public void testChainEuropeFails() {
        final Unit a = atSea(merchantman, true);
        FakeHost h = new FakeHost();
        h.opens = false;
        ClassicVoyages.Chain c = chain(h, List.of(a), List.of());
        long[] due = { c.step(0L) };
        run(c, h, due, 1600);
        assertTrue(c.isDone());
        assertEquals(List.of("band E " + a.getId() + " @0", "open @542",
                             "clear @1542", "done @1542"), h.calls);
        h = new FakeHost();
        h.hasEurope = false;
        c = chain(h, List.of(a), List.of());
        due = new long[] { c.step(0L) };
        run(c, h, due, 600);
        assertTrue(c.isDone());
        assertEquals(List.of("band E " + a.getId() + " @0", "clear @542",
                             "done @542"), h.calls);
    }

    /**
     * Back in the New World (clip008 1510): the view jumps to the ship,
     * its band one frame later, the hold ends 128 ms after the band (the
     * wipe); after a Europe part it comes when Europe closes.
     */
    public void testChainNewWorld() {
        final Unit a = atSea(merchantman, true), b = atSea(galleon, false);
        final FakeHost h = new FakeHost();
        final ClassicVoyages.Chain c = chain(h, List.of(), List.of(b));
        final long[] due = { c.step(0L) };
        run(c, h, due, 1000);
        assertEquals(List.of("jump " + b.getId() + " @0", "timed N " + b.getId() + " @14",
                             "done @142"), h.calls);
        assertEquals(ClassicAdvisorLayer.FRAME_MS, ClassicVoyages.NEW_WORLD_BAND_MS);
        // Europe first, then the New World.
        final FakeHost h2 = new FakeHost();
        final ClassicVoyages.Chain c2 = chain(h2, List.of(a), List.of(b));
        final long[] due2 = { c2.step(0L) };
        run(c2, h2, due2, 2000);
        assertFalse(c2.isDone());
        h2.europeOpen = false;
        run(c2, h2, due2, 3000);
        assertTrue(c2.isDone());
        assertEquals(List.of("band E " + a.getId() + " @0", "open @542", "clear @592",
                             "jump " + b.getId() + " @2042",
                             "timed N " + b.getId() + " @2056", "done @2184"), h2.calls);
    }

    /**
     * H REVIEW2 M1: an arrival while the Europe screen is open but behind
     * the map or minimized brings it up (no second screen); while the hold
     * waits for its close, a Europe that goes behind the map again comes
     * up again at the next poll (50 ms), as often as needed; the hold
     * still ends only with the close.  Meanwhile the chain says it waits
     * for Europe (the E key passes then), before and after not.
     */
    public void testChainBringsAHiddenEuropeUp() {
        final Unit a = atSea(merchantman, true);
        final FakeHost h = new FakeHost();
        h.europeOpen = true;                    // open since the last turn ...
        h.hidden = true;                        // ... behind the map
        final ClassicVoyages.Chain c = chain(h, List.of(a), List.of());
        assertFalse(c.waitsForEurope());
        final long[] due = { c.step(0L) };
        assertFalse(c.waitsForEurope());
        run(c, h, due, 600);
        assertEquals(List.of("band E " + a.getId() + " @0", "raise arrival @542",
                             "clear @592"), h.calls);
        assertFalse(h.hidden);
        assertEquals(ClassicVoyages.Chain.State.EUROPE_CLOSE, c.state());
        assertTrue(c.waitsForEurope());
        for (int i = 1; i <= 3; i++) {          // Alt+Tab, a click on the map, minimized
            h.hidden = true;
            run(c, h, due, 600 + i * 1000);
            assertFalse(h.hidden);
            assertFalse(c.isDone());
        }
        assertEquals(List.of("raise hold @642", "raise hold @1642", "raise hold @2642"),
                     h.calls.subList(3, 6));
        run(c, h, due, 60_000);                 // up: nothing more
        assertEquals(6, h.calls.size());
        h.europeOpen = false;                   // closed (or closed by itself)
        run(c, h, due, 60_100);
        assertTrue(c.isDone());
        assertFalse(c.waitsForEurope());
        assertEquals("done @60042", h.calls.get(6));
    }

    /**
     * H REVIEW2 L1: a host call that throws ends the chain at once: the
     * band goes, the host hears the end (the turn start goes on), the step
     * says "over"; in any state.  A {@code done} that throws after another
     * failure reaches the caller (the GUI's runner drops the chain then);
     * the chain's own last {@code done} that throws does not (the host
     * was told).
     */
    public void testChainFailureEndsIt() {
        final Unit a = atSea(merchantman, true), b = atSea(galleon, false);
        for (String where : new String[] { "band", "open", "jump", "timed" }) {
            final FakeHost h = new FakeHost();
            h.throwsIn = List.of(where);
            final ClassicVoyages.Chain c = chain(h, List.of(a), List.of(b));
            final long[] due = { c.step(0L) };
            run(c, h, due, 1000);
            h.europeOpen = false;
            run(c, h, due, 3000);
            assertTrue(where, c.isDone());
            assertEquals(where, -1L, due[0]);
            final String last = h.calls.get(h.calls.size() - 1);
            assertTrue(where + " " + h.calls, last.startsWith("done"));
            assertTrue(where + " " + h.calls, h.calls.get(h.calls.size() - 2)
                       .startsWith("clear"));
        }
        final FakeHost h = new FakeHost();
        h.throwsIn = List.of("clear");          // the band cannot go: still done
        ClassicVoyages.Chain c = chain(h, List.of(a), List.of());
        long[] due = { c.step(0L) };
        run(c, h, due, 1000);
        assertTrue(c.isDone());
        assertTrue(h.calls.get(h.calls.size() - 1).startsWith("done"));
        // The chain's own end throws: over, nothing thrown.
        final FakeHost h2 = new FakeHost();
        h2.throwsIn = List.of("done");
        h2.hasEurope = false;
        c = chain(h2, List.of(a), List.of());
        due = new long[] { c.step(0L) };
        run(c, h2, due, 1000);
        assertTrue(c.isDone());
        assertEquals(-1L, due[0]);
        // A failure, then its done throws too: the caller hears it.
        final FakeHost h3 = new FakeHost();
        h3.throwsIn = List.of("band", "done");
        c = chain(h3, List.of(a), List.of());
        try {
            c.step(0L);
            fail("done threw");
        } catch (IllegalStateException expected) {
            assertTrue(c.isDone());
            assertEquals(-1L, c.step(100L * MS));
        }
    }

    /**
     * The notices lose FreeCol's "arrived in Europe" of our ships (the
     * chain shows it); the mercenaries' UNIT_ARRIVED, another message and
     * a foreign ship's arrival stay.
     */
    public void testTakeArrivalMessages() {
        final Unit a = atSea(merchantman, true);
        arriveInEurope(a);
        final Player english = this.game.getPlayerByNationId("model.nation.english");
        final Unit e = new ServerUnit(this.game, english.getEurope(), english, merchantman);
        final Europe europe = this.dutch.getEurope();
        final ModelMessage ours = new ModelMessage(ModelMessage.MessageType.UNIT_ARRIVED,
            ClassicVoyages.ARRIVE_IN_EUROPE, europe, a);
        final ModelMessage theirs = new ModelMessage(ModelMessage.MessageType.UNIT_ARRIVED,
            ClassicVoyages.ARRIVE_IN_EUROPE, english.getEurope(), e);
        final ModelMessage mercs = new ModelMessage(ModelMessage.MessageType.UNIT_ARRIVED,
            "model.player.mercenariesArrived", this.dutch, a);
        final ModelMessage other = new ModelMessage(ModelMessage.MessageType.DEFAULT,
            "model.unit.unitImproved", europe, a);
        final List<ModelMessage> ms = new ArrayList<>(List.of(other, ours, mercs, theirs));
        assertEquals(List.of(a), ClassicGUI.takeArrivalMessages(ms, this.game, this.dutch));
        assertEquals(List.of(other, mercs, theirs), ms);
        assertTrue(ClassicGUI.takeArrivalMessages(ms, null, this.dutch).isEmpty());
        assertTrue(ClassicGUI.takeArrivalMessages(ms, this.game, null).isEmpty());
        assertEquals(3, ms.size());
    }

    /** The measured times. */
    public void testTimes() {
        assertEquals(1954.7, ClassicVoyages.BAND_MS, 0.1);   // 137 frames
        assertEquals(542.0, ClassicVoyages.BAND_TO_EUROPE_MS);
        assertEquals(557.0, ClassicVoyages.TUTORIAL_MS);
        assertEquals(128.0, ClassicVoyages.NEW_WORLD_RELEASE_MS);
        assertEquals(1000.0, ClassicVoyages.EUROPE_TIMEOUT_MS);
        // The last ship sailed to the map back: 25 frames (opening_013),
        // 26 frames (opening_014).
        assertEquals(357.0, ClassicVoyages.EUROPE_CLOSE_MS);
        assertTrue(ClassicVoyages.EUROPE_CLOSE_MS >= 25 * ClassicAdvisorLayer.FRAME_MS - 1
                   && ClassicVoyages.EUROPE_CLOSE_MS <= 26 * ClassicAdvisorLayer.FRAME_MS);
        assertEquals(28.5, ClassicVoyages.BAND_AFTER_UNIT_MS, 0.1);
    }

    /** A set of units, in order. */
    private static Set<Unit> set(Unit... us) {
        return new LinkedHashSet<>(List.of(us));
    }

    /** The static rule with explicit sets. */
    public void testArrivalsRule() {
        final Unit a = atSea(merchantman, true);
        arriveInEurope(a);
        assertEquals(List.of(a), ClassicVoyages.arrivals(this.dutch, set(a), set()).europe);
        assertEquals(List.of(a), ClassicVoyages.arrivals(this.dutch, set(), set(a)).europe);
        assertTrue(ClassicVoyages.arrivals(this.dutch, set(), set()).isEmpty());
    }
}
