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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * Tests of the end of turn and the hand-over to the next unit
 * ({@link ClassicTurnFlow}, {@link ClassicOneShot}; build spec W5): the
 * 485-ms automatic end from the last change and its re-basing, the
 * conditions it fires under, the 756-ms pause after a cancelled village
 * box, the Spielzugende hand-off, the 500-ms hand-over with its jump at
 * 280 ms, the turn start (wipe, block at 300 ms or jump at 500 ms), the
 * goto units first, the input block and the turn indicator's colours;
 * an end settled by the new turn number, a refused end, and a screen
 * behind the map.
 */
public class ClassicTurnFlowTest extends FreeColTestCase {

    private static final long MS = 1_000_000L;

    /** A clock that only moves when the test moves it. */
    private static final class FakeClock implements ClassicSlide.Clock {
        long now = 1_000_000_000L;

        @Override
        public long now() {
            return this.now;
        }

        @Override
        public void waitUntil(long due) {
            if (due > this.now) this.now = due;
        }

        void advanceMs(double ms) {
            this.now += Math.round(ms * MS);
        }
    }

    /** The game side, recording what the flow asks for. */
    private static final class FakeHost implements ClassicTurnFlow.Host {
        boolean myTurn = true, blocked = false, nextActive = false,
            nextGoingTo = false, promptPref = false, jump = false;
        int turn = 1;
        Unit firstGoingTo = null, active = null;
        Player me, current, next;
        final List<String> calls = new ArrayList<>();
        final List<Runnable> posted = new ArrayList<>();
        /** What endTurn does to the game (the server's answer). */
        Runnable onEndTurn = null;
        /** What endTurn returns: whether the controller asked the server. */
        boolean endTurnSent = true;

        @Override public boolean myTurn() { return this.myTurn; }
        @Override public boolean blocked() { return this.blocked; }
        @Override public boolean hasNextActiveUnit() { return this.nextActive; }
        @Override public boolean hasNextGoingToUnit() { return this.nextGoingTo; }
        @Override public Unit firstGoingToUnit() { return this.firstGoingTo; }
        @Override public int turnNumber() { return this.turn; }
        @Override public boolean promptPref() { return this.promptPref; }
        @Override public Unit activeUnit() { return this.active; }
        @Override public boolean wouldJump(Unit unit) { return this.jump; }

        /** jumpTo throws (a host call that fails). */
        boolean jumpThrows = false;

        @Override
        public void jumpTo(Unit unit) {
            this.calls.add("jump " + unit.getId());
            if (this.jumpThrows) throw new IllegalStateException("jump failed");
        }

        @Override
        public void activate(Unit unit) {
            this.active = unit;
            this.calls.add("activate " + unit.getId());
        }

        @Override
        public void wipe() {
            this.active = null;
            this.calls.add("wipe");
        }

        /** The flow, whose colour a paint shows (as the panel does), or null. */
        ClassicTurnFlow flow = null;

        @Override
        public void paintIndicator() {
            this.calls.add("indicator");
            if (this.flow != null) {
                final int rgb = this.flow.indicatorRgb();
                this.flow.indicatorShown(rgb);
                this.painted.add(rgb);
            }
        }

        /** The colours the indicator paints showed, in order. */
        final List<Integer> painted = new ArrayList<>();

        @Override
        public boolean endTurn() {
            this.calls.add("endTurn");
            if (this.onEndTurn != null) this.onEndTurn.run();
            return this.endTurnSent;
        }

        @Override
        public void runGotoOrders() {
            this.calls.add("gotos");
        }

        /** What the controller does on nextActiveUnit (its view change), or null. */
        Runnable onNext = null;

        @Override
        public void nextActiveUnit() {
            this.calls.add("next");
            if (this.onNext != null) this.onNext.run();
        }

        /** The village tile the last Spielzugende mode was entered with. */
        Tile promptVillage = null;

        @Override
        public void enterPrompt(Tile village) {
            this.promptVillage = village;
            this.calls.add("prompt");
        }

        @Override
        public void freezePrompt() {
            this.calls.add("freeze");
        }

        @Override
        public void leavePrompt() {
            this.calls.add("leave");
        }

        @Override public Player currentPlayer() { return this.current; }
        @Override public Player myPlayer() { return this.me; }
        @Override public Player nextPlayer() { return this.next; }

        @Override
        public void post(Runnable r) {
            this.posted.add(r);
        }
    }

    /** A flow without a thread, on the fake clock and host. */
    private static final class Rig {
        final FakeClock clock = new FakeClock();
        final FakeHost host = new FakeHost();
        final List<Runnable> edt = new ArrayList<>();
        final ClassicTurnFlow flow;

        Rig(Game game) {
            this.host.me = game.getPlayerByNationId("model.nation.dutch");
            this.host.current = this.host.me;
            this.host.next = game.getPlayerByNationId("model.nation.iroquois");
            this.flow = new ClassicTurnFlow(this.host, this.clock, this.edt::add,
                                            false);
            this.host.flow = this.flow;
            // The server's answer to our end: the next player's turn.
            this.host.onEndTurn = () -> {
                this.host.myTurn = false;
                this.host.current = this.host.next;
            };
        }

        /** Advance the clock and run what came due, as the thread and the EDT do. */
        void advanceMs(double ms) {
            this.clock.advanceMs(ms);
            run();
        }

        /** Run due stages and posted tasks until nothing is left. */
        void run() {
            for (int guard = 0; guard < 100; guard++) {
                this.flow.runDue();
                if (this.edt.isEmpty() && this.host.posted.isEmpty()) return;
                final List<Runnable> now = new ArrayList<>(this.edt);
                now.addAll(this.host.posted);
                this.edt.clear();
                this.host.posted.clear();
                for (Runnable r : now) r.run();
            }
            fail("tasks keep coming");
        }

        int count(String call) {
            int n = 0;
            for (String c : this.host.calls) if (c.equals(call)) n++;
            return n;
        }
    }

    private Game game;
    private Map map;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.game = getStandardGame();
        this.map = getTestMap(spec().getTileType("model.tile.ocean"), true);
        this.game.changeMap(this.map);
    }

    private Unit ship(int x, int y) {
        final Player dutch = this.game.getPlayerByNationId("model.nation.dutch");
        final Tile t = this.map.getTile(x, y);
        return new ServerUnit(this.game, t, dutch,
                              spec().getUnitType("model.unit.merchantman"));
    }


    /** The measured pauses and how a hand-over and a turn start are staged. */
    public void testSchedule() {
        assertEquals(485.0, ClassicTurnFlow.idleMs(false, false));
        assertEquals(756.0, ClassicTurnFlow.idleMs(false, true));
        assertEquals(500.0, ClassicTurnFlow.idleMs(true, false));
        assertEquals(813.0, ClassicTurnFlow.idleMs(true, true));

        ClassicTurnFlow.Stage[] s = ClassicTurnFlow.handOverStages(false);
        assertEquals(1, s.length);
        assertEquals(500.0, s[0].ms);
        assertEquals(ClassicTurnFlow.Action.ACTIVATE, s[0].action);
        s = ClassicTurnFlow.handOverStages(true);
        assertEquals(2, s.length);
        assertEquals(280.0, s[0].ms);
        assertEquals(ClassicTurnFlow.Action.JUMP, s[0].action);
        assertEquals(500.0, s[1].ms);

        s = ClassicTurnFlow.turnStartStages(false, false);
        assertEquals(1, s.length);
        assertEquals(300.0, s[0].ms);
        s = ClassicTurnFlow.turnStartStages(true, false);
        assertEquals(500.0, s[0].ms);
        assertEquals(ClassicTurnFlow.Action.JUMP, s[0].action);
        assertEquals(521.0, s[1].ms);   // 1-2 frames after the jump
        s = ClassicTurnFlow.turnStartStages(false, true);
        assertEquals(2, s.length);
        assertEquals(ClassicTurnFlow.Action.GOTO, s[1].action);
        assertEquals(400.0, s[1].ms);
        // Inside the measured windows.
        assertTrue(ClassicTurnFlow.TURN_START_MS >= 270
                   && ClassicTurnFlow.TURN_START_MS <= 410);
        assertTrue(ClassicTurnFlow.TURN_START_JUMP_MS >= 470
                   && ClassicTurnFlow.TURN_START_JUMP_MS <= 590);

        final Unit a = ship(5, 5);
        assertFalse(ClassicTurnFlow.ranOut(a));
        assertFalse(ClassicTurnFlow.ranOut(null));
        a.setMovesLeft(0);
        assertTrue(ClassicTurnFlow.ranOut(a));
        // Skipped (Space) is done too, with its moves.
        final Unit b = ship(6, 5);
        b.setState(Unit.UnitState.SKIPPED);
        assertTrue(b.getMovesLeft() > 0);
        assertTrue(ClassicTurnFlow.ranOut(b));

        // After a boarding (spec delta W18): the carrier at 128 ms.
        s = ClassicTurnFlow.boardingStages(false);
        assertEquals(1, s.length);
        assertEquals(128.0, s[0].ms);
        assertEquals(ClassicTurnFlow.Action.ACTIVATE, s[0].action);
        s = ClassicTurnFlow.boardingStages(true);
        assertEquals(2, s.length);
        assertEquals(ClassicTurnFlow.Action.JUMP, s[0].action);
        assertEquals(128.0, s[0].ms);
        assertEquals(149.0, s[1].ms);
    }

    /**
     * After a boarding the carrier comes 128 ms after the boarding's final
     * draw (clip007 #4272 -&gt; #4281: 9 frames), re-based on a later
     * change as any hand-over; a skipped unit hands over as after its last
     * move (500 ms).
     */
    public void testHandOverAfterBoardingAndSkip() {
        final Unit ship = ship(5, 5), b = ship(7, 5);
        final Unit soldier = new ServerUnit(this.game, ship,
            this.game.getPlayerByNationId("model.nation.dutch"),
            spec().getUnitType("model.unit.freeColonist"));
        soldier.setMovesLeft(0);   // it has just boarded
        final Rig r = new Rig(this.game);
        r.host.active = soldier;
        r.flow.screenChanged();                    // the boarding's final draw
        r.clock.advanceMs(2);
        assertTrue(r.flow.carrierChosen(ship, soldier));
        assertEquals(ClassicTurnFlow.Kind.HANDOVER, r.flow.pending().kind);
        assertSame(ship, r.flow.pending().unit);
        assertTrue(r.flow.isInputBlocked());
        assertTrue(r.flow.unitChosen(ship, soldier));   // asked again: kept
        r.advanceMs(125.9);
        assertEquals(0, r.count("activate " + ship.getId()));
        r.advanceMs(0.2);
        assertEquals(1, r.count("activate " + ship.getId()));
        assertNull(r.flow.pending());

        // Space on the ship: the next unit as after a last move, 500 ms.
        r.host.active = ship;
        ship.setState(Unit.UnitState.SKIPPED);
        r.flow.screenChanged();                    // the skip's redraw
        assertTrue(r.flow.unitChosen(b, ship));
        r.advanceMs(499.9);
        assertEquals(0, r.count("activate " + b.getId()));
        r.advanceMs(0.2);
        assertEquals(1, r.count("activate " + b.getId()));
    }

    /**
     * The automatic end: 485 ms after the last change, the indicator in
     * the next player's colour painted just before the request, then our
     * turn waits (frozen panel) with the input blocked.
     */
    public void testAutomaticEnd() {
        final Rig r = new Rig(this.game);
        r.flow.screenChanged();            // the final draw
        r.clock.advanceMs(3);
        r.flow.noUnitLeft();               // the controller's end view
        assertNotNull(r.flow.pending());
        assertEquals(ClassicTurnFlow.Kind.END_TURN, r.flow.pending().kind);
        assertTrue(r.flow.isInputBlocked());
        r.advanceMs(481.9);                // 484.9 after the change
        assertEquals(0, r.count("endTurn"));
        assertEquals(-1, r.flow.indicatorRgb());
        r.advanceMs(0.2);
        assertEquals(1, r.count("endTurn"));
        final int end = r.host.calls.indexOf("endTurn");
        assertTrue(end > 0);
        assertEquals("indicator", r.host.calls.get(end - 1));
        assertTrue(r.flow.isWaiting());
        assertTrue(r.flow.isInputBlocked());
        // The AI's colour now; the one predicted was the same player.
        assertEquals(0x6D3C18, r.flow.indicatorRgb());
        assertNull(r.flow.pending());
    }

    /** A change before the end re-bases it; nothing ends with a unit left. */
    public void testRebaseAndConditions() {
        final Rig r = new Rig(this.game);
        r.flow.noUnitLeft();
        r.advanceMs(100);
        r.flow.screenChanged();            // e.g. the panel refresh
        r.advanceMs(484);
        assertEquals(0, r.count("endTurn"));
        r.advanceMs(1);
        assertEquals(1, r.count("endTurn"));

        // A unit can move: no pause at all.
        final Rig q = new Rig(this.game);
        q.host.nextActive = true;
        q.flow.noUnitLeft();
        assertNull(q.flow.pending());
        assertFalse(q.flow.isInputBlocked());

        // A unit became movable while the pause ran: kept, and the
        // controller is asked for it.
        final Rig u = new Rig(this.game);
        u.flow.noUnitLeft();
        u.host.nextActive = true;
        u.advanceMs(485);
        assertEquals(0, u.count("endTurn"));
        assertEquals(1, u.count("next"));

        // Not our turn: nothing.
        final Rig n = new Rig(this.game);
        n.host.myTurn = false;
        n.flow.noUnitLeft();
        assertNull(n.flow.pending());
    }

    /** A box up when the pause ends holds it; its close re-arms from the close. */
    public void testBoxHoldsTheEnd() {
        final Rig r = new Rig(this.game);
        r.flow.noUnitLeft();
        r.host.blocked = true;             // e.g. a FreeCol message
        r.advanceMs(485);
        assertEquals(0, r.count("endTurn"));
        assertNull(r.flow.pending());
        r.advanceMs(2000);
        r.flow.tick();                     // still up: nothing
        assertNull(r.flow.pending());
        r.host.blocked = false;
        r.flow.boxClosed();
        assertNotNull(r.flow.pending());
        r.advanceMs(484);
        assertEquals(0, r.count("endTurn"));
        r.advanceMs(1);
        assertEquals(1, r.count("endTurn"));

        // A screen without a close hook: the poll re-arms.
        final Rig s = new Rig(this.game);
        s.flow.noUnitLeft();
        s.host.blocked = true;
        s.advanceMs(485);
        s.host.blocked = false;
        s.flow.tick();
        assertNotNull(s.flow.pending());
        s.advanceMs(485);
        assertEquals(1, s.count("endTurn"));
    }

    /** After a cancelled village box: 756 ms (813 ms to the Spielzugende mode). */
    public void testVillageCancel() {
        final Tile village = this.map.getTile(6, 5);
        final Rig r = new Rig(this.game);
        r.flow.villageBoxCancelled(village);
        r.flow.boxClosed();
        r.flow.noUnitLeft();
        r.advanceMs(755);
        assertEquals(0, r.count("endTurn"));
        r.advanceMs(1);
        assertEquals(1, r.count("endTurn"));

        // The Spielzugende mode: 813 ms, the cursor on the village
        // (landing-slow #4136 -> #4193).
        final Rig p = new Rig(this.game);
        p.host.promptPref = true;
        p.flow.villageBoxCancelled(village);
        p.flow.noUnitLeft();
        p.advanceMs(812);
        assertEquals(0, p.count("prompt"));
        p.advanceMs(1);
        assertEquals(1, p.count("prompt"));
        assertSame(village, p.host.promptVillage);

        // A cancel long ago does not count, nor does its village.
        final Rig o = new Rig(this.game);
        o.flow.villageBoxCancelled(village);
        o.advanceMs(1000);
        o.flow.noUnitLeft();
        o.advanceMs(485);
        assertEquals(1, o.count("endTurn"));
        final Rig q = new Rig(this.game);
        q.host.promptPref = true;
        q.flow.villageBoxCancelled(village);
        q.advanceMs(1000);
        q.flow.noUnitLeft();
        q.advanceMs(500);
        assertEquals(1, q.count("prompt"));
        assertNull(q.host.promptVillage);
    }

    /**
     * The house rule "Handlung abbrechen" keeps the move (D1): after a
     * cancelled learn question or village box the unit can still move,
     * so no automatic end starts (nor the Spielzugende mode), however
     * long the player waits and whatever view change the controller
     * reports; the map takes keys.  Its last move later ends the turn
     * after the normal 485 ms.
     */
    public void testCancelKeepsTheUnitUp() {
        final Tile village = this.map.getTile(6, 5);
        for (boolean promptPref : new boolean[] { false, true }) {
            final Rig r = new Rig(this.game);
            r.host.promptPref = promptPref;
            r.host.active = ship(5, 5);
            r.host.nextActive = true;        // the unit kept its moves
            r.flow.villageBoxCancelled(village);
            r.flow.boxClosed();
            assertNull(r.flow.pending());
            r.flow.noUnitLeft();             // e.g. the controller's view
            assertNull(r.flow.pending());
            r.advanceMs(5000);
            r.flow.tick();
            assertEquals(0, r.count("endTurn"));
            assertEquals(0, r.count("prompt"));
            assertFalse(r.flow.isInputBlocked());

            // The unit's last move, long after the cancel: the normal pause.
            r.host.nextActive = false;
            r.flow.screenChanged();          // its final draw
            r.flow.noUnitLeft();
            assertNotNull(r.flow.pending());
            r.advanceMs(promptPref ? 499 : 484);
            assertEquals(0, r.count(promptPref ? "prompt" : "endTurn"));
            r.advanceMs(1);
            assertEquals(1, r.count(promptPref ? "prompt" : "endTurn"));
        }
    }

    /**
     * The pref is read at the idle decision: on, the Spielzugende mode
     * comes at 500 ms and waits; the player ends the turn (Enter).
     */
    public void testPromptMode() {
        final Rig r = new Rig(this.game);
        r.host.promptPref = true;
        r.flow.noUnitLeft();
        assertEquals(ClassicTurnFlow.Kind.PROMPT, r.flow.pending().kind);
        r.host.promptPref = false;         // read at arm time, not later
        r.advanceMs(500);
        assertEquals(1, r.count("prompt"));
        assertTrue(r.flow.isPrompt());
        assertFalse(r.flow.isInputBlocked());
        r.advanceMs(20000);
        r.flow.tick();
        assertEquals(0, r.count("endTurn"));   // it waits
        assertNull(r.host.promptVillage);       // the last unit's tile
        // The end command: forced ON and frozen at once, the indicator
        // and the request one frame later (landing-slow #5500 -> #5501);
        // the input is blocked meanwhile.
        r.flow.endTurnNow("key");
        assertEquals(1, r.count("freeze"));
        assertEquals(0, r.count("endTurn"));
        assertEquals(0, r.count("indicator"));
        assertFalse(r.flow.isPrompt());
        assertTrue(r.flow.isInputBlocked());
        assertEquals(ClassicTurnFlow.Kind.PROMPT_END, r.flow.pending().kind);
        r.flow.noUnitLeft();                    // the controller's view: kept
        assertEquals(ClassicTurnFlow.Kind.PROMPT_END, r.flow.pending().kind);
        r.advanceMs(14.9);
        assertEquals(0, r.count("endTurn"));
        r.advanceMs(0.2);
        assertEquals(1, r.count("endTurn"));
        final int end = r.host.calls.indexOf("endTurn");
        assertEquals("indicator", r.host.calls.get(end - 1));
        assertTrue(r.host.calls.indexOf("freeze") < end - 1);
        assertEquals(0, r.count("leave"));
        assertNull(r.flow.pending());
    }

    /**
     * W17: the pref switched off while the mode shows does not end the
     * turn (clip004 #4560); a unit made active leaves the mode, and its
     * look goes.
     */
    public void testPromptModeKeepsAndLeaves() {
        final Rig r = new Rig(this.game);
        r.host.promptPref = true;
        r.flow.noUnitLeft();
        r.advanceMs(500);
        assertTrue(r.flow.isPrompt());
        r.host.promptPref = false;
        r.flow.boxClosed();                     // the options box closes
        r.advanceMs(5000);
        r.flow.tick();
        assertTrue(r.flow.isPrompt());
        assertEquals(0, r.count("endTurn"));
        assertNull(r.flow.pending());
        // A click on a unit that can still move.
        final Unit a = ship(5, 5);
        assertFalse(r.flow.unitChosen(a, null));
        assertFalse(r.flow.isPrompt());
        assertEquals(1, r.count("leave"));
        assertEquals(0, r.count("freeze"));
        // The next idle decision reads the pref again: now the automatic end.
        r.host.active = null;
        r.flow.noUnitLeft();
        r.advanceMs(485);
        assertEquals(1, r.count("endTurn"));
        assertEquals(1, r.count("prompt"));
    }

    /** The hand-over: 500 ms, or a jump at 280 ms and the block at 500 ms. */
    public void testHandOver() {
        final Unit a = ship(5, 5), b = ship(7, 5);
        final Rig r = new Rig(this.game);
        r.host.active = a;
        assertFalse(r.flow.unitChosen(a, a));   // the re-selection
        assertFalse(r.flow.unitChosen(b, a));   // a still has moves: at once
        a.setMovesLeft(0);
        r.flow.screenChanged();                 // a's last final draw
        r.clock.advanceMs(2);
        assertFalse(r.flow.unitChosen(a, a));   // re-selected with 0 moves
        assertTrue(r.flow.unitChosen(b, a));
        assertEquals(ClassicTurnFlow.Kind.HANDOVER, r.flow.pending().kind);
        assertTrue(r.flow.isInputBlocked());
        assertTrue(r.flow.unitChosen(b, a));    // again: kept
        assertTrue(r.flow.unitChosen(a, a));    // a re-selected: kept
        r.advanceMs(3);
        r.flow.screenChanged();                 // the panel refresh: re-based
        r.advanceMs(499.9);
        assertEquals(0, r.count("activate " + b.getId()));
        r.advanceMs(0.2);
        assertEquals(1, r.count("activate " + b.getId()));
        assertNull(r.flow.pending());
        assertFalse(r.flow.isInputBlocked());

        // With a jump: at 280 ms, the block at 500 ms; the jump's own
        // paint does not re-base the block.
        final Unit c = ship(9, 5);
        r.host.jump = true;
        b.setMovesLeft(0);
        r.flow.screenChanged();
        assertTrue(r.flow.unitChosen(c, b));
        r.advanceMs(279.9);
        assertEquals(0, r.count("jump " + c.getId()));
        r.advanceMs(0.2);
        assertEquals(1, r.count("jump " + c.getId()));
        r.flow.screenChanged();
        r.advanceMs(219.8);
        assertEquals(0, r.count("activate " + c.getId()));
        r.advanceMs(0.2);
        assertEquals(1, r.count("activate " + c.getId()));
    }

    /** A box up when the next unit is due: it comes up with the close. */
    public void testHandOverHeldByBox() {
        final Unit a = ship(5, 5), b = ship(7, 5);
        final Rig r = new Rig(this.game);
        r.host.active = a;
        a.setMovesLeft(0);
        assertTrue(r.flow.unitChosen(b, a));
        r.advanceMs(100);
        r.host.blocked = true;              // e.g. a tutorial tip
        r.advanceMs(400);
        assertEquals(0, r.count("activate " + b.getId()));
        assertTrue(r.flow.pending().held);
        assertTrue(r.flow.isInputBlocked());
        r.advanceMs(1500);
        r.flow.tick();
        assertEquals(0, r.count("activate " + b.getId()));
        r.host.blocked = false;
        r.flow.boxClosed();
        assertEquals(1, r.count("activate " + b.getId()));
        assertNull(r.flow.pending());
        r.advanceMs(1000);
        assertEquals(1, r.count("activate " + b.getId()));
    }

    /** No unit left while a hand-over waits: it is dropped for the end. */
    public void testEndCancelsHandOver() {
        final Unit a = ship(5, 5), b = ship(7, 5);
        final Rig r = new Rig(this.game);
        r.host.active = a;
        a.setMovesLeft(0);
        assertTrue(r.flow.unitChosen(b, a));
        r.flow.noUnitLeft();
        assertEquals(ClassicTurnFlow.Kind.END_TURN, r.flow.pending().kind);
        r.advanceMs(600);
        assertEquals(0, r.count("activate " + b.getId()));
        assertEquals(1, r.count("endTurn"));
        // After our end the controller's re-selection is ignored.
        assertTrue(r.flow.unitChosen(b, null));
        assertNull(r.flow.pending());
    }

    /**
     * The turn start: the wipe with the controller's first view change of
     * our new turn, the block 300 ms later, or the jump at 500 ms and the
     * block 21 ms after it.
     */
    public void testTurnStart() {
        final Unit a = ship(5, 5);
        final Rig r = new Rig(this.game);
        r.flow.endTurnNow("key");
        assertTrue(r.flow.isWaiting());
        // The AI phase.
        r.flow.tick();
        assertEquals(0x6D3C18, r.flow.indicatorRgb());
        // Our turn comes; the controller has not shown it yet (FreeCol
        // autosaves there): the last colour stays (M1 acceptance F4) ...
        r.host.turn = 2;
        r.host.myTurn = true;
        r.host.current = r.host.me;
        r.flow.tick();
        assertEquals(0x6D3C18, r.flow.indicatorRgb());
        assertTrue(r.flow.isInputBlocked());
        assertTrue(r.flow.isBusy());
        // ... and ours lights for one tick, in a paint of its own, just
        // before the wipe takes it away with the year.
        r.host.painted.clear();
        final long before = r.clock.now;
        assertTrue(r.flow.unitChosen(a, null));
        assertEquals(1, r.count("wipe"));
        assertEquals(List.of(0xFF7100), r.host.painted);
        assertEquals("indicator", r.host.calls.get(r.host.calls.indexOf("wipe") - 1));
        assertEquals(ClassicTurnFlow.nanos(ClassicTurnFlow.OWN_FLASH_MS),
                     r.clock.now - before);
        assertFalse(r.flow.isWaiting());
        assertEquals(-1, r.flow.indicatorRgb());
        assertEquals(ClassicTurnFlow.Kind.TURN_START, r.flow.pending().kind);
        r.flow.screenChanged();                 // no re-basing of a turn start
        r.advanceMs(299.9);
        assertEquals(0, r.count("activate " + a.getId()));
        r.advanceMs(0.2);
        assertEquals(1, r.count("activate " + a.getId()));
        assertFalse(r.flow.isInputBlocked());

        // Turn 3 with a jump, and a box up when it starts.
        r.flow.endTurnNow("key");
        r.host.turn = 3;
        r.host.myTurn = true;
        r.host.current = r.host.me;
        r.host.blocked = true;
        r.host.jump = true;
        assertTrue(r.flow.unitChosen(a, null));
        assertEquals(1, r.count("wipe"));       // not while the box is up
        assertTrue(r.flow.isWaiting());
        r.advanceMs(1000);
        assertEquals(1, r.count("activate " + a.getId()));
        r.host.blocked = false;
        r.flow.boxClosed();                     // the wipe comes with the close
        assertEquals(2, r.count("wipe"));
        r.advanceMs(499.9);
        assertEquals(0, r.count("jump " + a.getId()));
        r.advanceMs(0.2);
        assertEquals(1, r.count("jump " + a.getId()));
        r.advanceMs(20.8);
        assertEquals(1, r.count("activate " + a.getId()));
        r.advanceMs(0.2);
        assertEquals(2, r.count("activate " + a.getId()));
    }

    /** No unit at the start of our turn: the wipe, then the automatic end. */
    public void testTurnStartWithoutUnits() {
        final Rig r = new Rig(this.game);
        r.flow.endTurnNow("auto");
        r.host.turn = 2;
        r.host.myTurn = true;
        r.host.current = r.host.me;
        r.flow.noUnitLeft();
        assertEquals(1, r.count("wipe"));
        r.advanceMs(485);
        assertEquals(2, r.count("endTurn"));
    }

    /**
     * Goto units first (W5f): the goto unit comes up at the turn start,
     * its orders run 100 ms after its block, the controller's view changes
     * meanwhile are dropped, then the next unit is a hand-over.
     */
    public void testGotoFirst() {
        final Unit g = ship(5, 5), b = ship(7, 5);
        final Rig r = new Rig(this.game);
        r.flow.endTurnNow("key");
        r.host.turn = 2;
        r.host.myTurn = true;
        r.host.current = r.host.me;
        r.host.nextGoingTo = true;
        r.host.firstGoingTo = g;
        assertTrue(r.flow.unitChosen(b, null));
        r.advanceMs(300);
        assertEquals(1, r.count("activate " + g.getId()));
        assertEquals(0, r.count("gotos"));
        r.clock.advanceMs(100);
        r.flow.runDue();
        assertEquals(1, r.edt.size());
        r.edt.remove(0).run();
        assertEquals(1, r.count("gotos"));
        assertTrue(r.flow.ignoring());
        assertTrue(r.flow.isInputBlocked());
        r.host.nextGoingTo = false;
        r.flow.screenChanged();                 // the last goto step's final draw
        // The marker after the queued changes: the controller brings b (g
        // has moves, still a hand-over).
        final boolean[] taken = new boolean[1];
        r.host.onNext = () -> taken[0] = r.flow.unitChosen(b, g);
        r.run();
        assertFalse(r.flow.ignoring());
        assertEquals(1, r.count("next"));
        assertTrue(taken[0]);
        assertEquals(ClassicTurnFlow.Kind.HANDOVER, r.flow.pending().kind);
        r.advanceMs(500);
        assertEquals(1, r.count("activate " + b.getId()));
    }

    /**
     * FINAL "Open" item 8: after the goto orders, FreeCol's first
     * nextActiveUnit only leaves its goto mode when the orders stopped
     * early (a goto unit with no path, skipped) and brings no unit; the
     * flow asks once more, and that call's unit comes up as a hand-over.
     * The flow never sits with no unit and nothing scheduled.
     */
    public void testGotoWithoutPathStillBringsAUnit() {
        final Unit g = ship(5, 5), b = ship(7, 5);
        final Rig r = new Rig(this.game);
        r.flow.endTurnNow("key");
        r.host.turn = 2;
        r.host.myTurn = true;
        r.host.current = r.host.me;
        r.host.nextGoingTo = true;
        r.host.firstGoingTo = g;
        assertTrue(r.flow.unitChosen(b, null));
        r.advanceMs(300);
        r.clock.advanceMs(100);
        r.flow.runDue();
        r.edt.remove(0).run();                  // the goto orders: g has no path
        assertEquals(1, r.count("gotos"));
        r.host.nextGoingTo = false;
        r.host.nextActive = true;
        // The controller: the first call brings nothing, the second b.
        final int[] calls = new int[1];
        r.host.onNext = () -> {
            if (++calls[0] == 2) r.flow.unitChosen(b, g);
        };
        r.run();
        assertEquals(2, r.count("next"));
        assertEquals(ClassicTurnFlow.Kind.HANDOVER, r.flow.pending().kind);
        r.advanceMs(500);
        assertEquals(1, r.count("activate " + b.getId()));
        assertFalse(r.flow.isInputBlocked());

        // No unit left at all: the second call's end view arms the
        // automatic end.
        final Rig n = new Rig(this.game);
        n.flow.endTurnNow("key");
        n.host.turn = 2;
        n.host.myTurn = true;
        n.host.current = n.host.me;
        n.host.nextGoingTo = true;
        n.host.firstGoingTo = g;
        assertTrue(n.flow.unitChosen(g, null));
        n.advanceMs(300);
        n.clock.advanceMs(100);
        n.flow.runDue();
        n.edt.remove(0).run();
        n.host.nextGoingTo = false;
        final int[] ncalls = new int[1];
        n.host.onNext = () -> {
            if (++ncalls[0] == 2) {
                n.host.active = null;
                n.flow.noUnitLeft();
            }
        };
        n.run();
        assertEquals(2, n.count("next"));
        assertEquals(ClassicTurnFlow.Kind.END_TURN, n.flow.pending().kind);

        // Disposed meanwhile (the game view went): the marker does nothing.
        final Rig d = new Rig(this.game);
        d.flow.endTurnNow("key");
        d.host.turn = 2;
        d.host.myTurn = true;
        d.host.current = d.host.me;
        d.host.nextGoingTo = true;
        d.host.firstGoingTo = g;
        assertTrue(d.flow.unitChosen(g, null));
        d.advanceMs(300);
        d.clock.advanceMs(100);
        d.flow.runDue();
        d.edt.remove(0).run();
        d.flow.dispose();
        d.run();
        assertEquals(0, d.count("next"));
    }

    /**
     * D acceptance D1: goto orders took the goto ship off the map (a ship
     * sailing for Europe).  At the turn start the controller ran them
     * itself before the ship's block, and FreeCol's controller, left in
     * its goto mode with no active unit, chooses nothing on
     * nextActiveUnit: with nothing left to move the flow arms the
     * automatic end itself, and the turn ends.  The same after the flow's
     * own goto orders when both of its calls bring nothing.  With a unit
     * left to move nothing is armed (the flow never ends a turn then).
     */
    public void testUnitGoneAndNoChoiceStillEnds() {
        final Player dutch = this.game.getPlayerByNationId("model.nation.dutch");
        assertNotNull(dutch.getHighSeas());
        final Unit g = ship(5, 5);
        final Rig r = new Rig(this.game);
        r.flow.endTurnNow("key");
        r.host.turn = 2;
        r.host.myTurn = true;
        r.host.current = r.host.me;
        r.host.nextGoingTo = true;
        r.host.firstGoingTo = g;
        assertTrue(r.flow.unitChosen(g, null));
        assertEquals(ClassicTurnFlow.Kind.TURN_START, r.flow.pending().kind);
        g.setLocation(dutch.getHighSeas());     // sailed off before its block
        r.host.nextGoingTo = false;
        r.advanceMs(300);                       // the block: gone, nothing chosen
        assertEquals(0, r.count("activate " + g.getId()));
        assertEquals(1, r.count("next"));
        assertNotNull(r.flow.pending());
        assertEquals(ClassicTurnFlow.Kind.END_TURN, r.flow.pending().kind);
        r.advanceMs(484.9);
        assertEquals(1, r.count("endTurn"));
        r.advanceMs(0.2);
        assertEquals(2, r.count("endTurn"));

        // The flow's own goto orders take it off; both calls bring nothing.
        final Unit h = ship(7, 5);
        final Rig n = new Rig(this.game);
        n.flow.endTurnNow("key");
        n.host.turn = 2;
        n.host.myTurn = true;
        n.host.current = n.host.me;
        n.host.nextGoingTo = true;
        n.host.firstGoingTo = h;
        assertTrue(n.flow.unitChosen(h, null));
        n.advanceMs(300);
        assertEquals(1, n.count("activate " + h.getId()));
        n.clock.advanceMs(100);
        n.flow.runDue();
        h.setLocation(dutch.getHighSeas());
        n.host.nextGoingTo = false;
        n.edt.remove(0).run();                  // the goto orders
        assertEquals(1, n.count("gotos"));
        n.run();                                // their marker
        assertEquals(2, n.count("next"));
        assertEquals(ClassicTurnFlow.Kind.END_TURN, n.flow.pending().kind);
        n.advanceMs(485);
        assertEquals(2, n.count("endTurn"));

        // A unit left to move: no end armed.
        final Unit k = ship(9, 5);
        final Rig m = new Rig(this.game);
        m.flow.endTurnNow("key");
        m.host.turn = 2;
        m.host.myTurn = true;
        m.host.current = m.host.me;
        m.host.nextGoingTo = true;
        m.host.firstGoingTo = k;
        assertTrue(m.flow.unitChosen(k, null));
        k.setLocation(dutch.getHighSeas());
        m.host.nextGoingTo = false;
        m.host.nextActive = true;
        m.advanceMs(300);
        assertEquals(1, m.count("next"));
        assertNull(m.flow.pending());
        m.advanceMs(1000);
        assertEquals(1, m.count("endTurn"));
    }

    /**
     * Our end goes through without a poll seeing another player: the
     * event thread plays queued native slides while the AI phase runs,
     * and our next turn is current again when it gets to the queue.  The
     * new turn number settles the end, and the controller's first unit of
     * the new turn gets the wipe and the turn start (not dropped as "not
     * our turn").
     */
    public void testEndSettledByTheTurnNumber() {
        final Unit a = ship(5, 5);
        // A poll runs after the turn change, then the controller's unit.
        final Rig r = new Rig(this.game);
        r.host.onEndTurn = null;                // the answer: still our turn ...
        r.flow.endTurnNow("auto");
        assertTrue(r.flow.isInputBlocked());
        r.clock.advanceMs(300);
        r.host.turn = 2;                        // ... and the AI phase is over
        r.flow.tick();
        assertTrue(r.flow.isWaiting());
        // Not shown yet: the last colour stays until the wipe's tick of ours.
        assertEquals(0x6D3C18, r.flow.indicatorRgb());
        r.clock.advanceMs(400);
        assertTrue(r.flow.unitChosen(a, null));
        assertEquals(1, r.count("wipe"));
        assertFalse(r.flow.isWaiting());
        assertEquals(ClassicTurnFlow.Kind.TURN_START, r.flow.pending().kind);
        r.advanceMs(299.9);
        assertEquals(0, r.count("activate " + a.getId()));
        r.advanceMs(0.2);
        assertEquals(1, r.count("activate " + a.getId()));
        assertFalse(r.flow.isInputBlocked());
        r.advanceMs(5000);
        r.flow.tick();
        assertEquals(1, r.count("endTurn"));
        assertEquals(0, r.count("next"));

        // No poll in between: the unit itself settles the end.
        final Rig q = new Rig(this.game);
        q.host.onEndTurn = null;
        q.flow.endTurnNow("auto");
        q.clock.advanceMs(700);
        q.host.turn = 2;
        assertTrue(q.flow.unitChosen(a, null));
        assertEquals(1, q.count("wipe"));
        q.advanceMs(300);
        assertEquals(1, q.count("activate " + a.getId()));

        // A new turn with no unit to move: the wipe, then the automatic end.
        final Rig n = new Rig(this.game);
        n.host.onEndTurn = null;
        n.flow.endTurnNow("auto");
        n.clock.advanceMs(700);
        n.host.turn = 2;
        n.flow.noUnitLeft();
        assertEquals(1, n.count("wipe"));
        assertEquals(ClassicTurnFlow.Kind.END_TURN, n.flow.pending().kind);
        n.advanceMs(485);
        assertEquals(2, n.count("endTurn"));
    }

    /**
     * A refused end (the controller did not send it, e.g. for a goto or
     * trade-route unit to look at) leaves a live state: behind what is
     * queued the controller is asked for a unit, unless one came
     * meanwhile.  A turn change after the refusal still ends the old turn,
     * with a real wipe and the turn start's own pause.
     */
    public void testRefusedEndLeavesALiveState() {
        final Unit a = ship(5, 5);
        final Rig r = new Rig(this.game);
        r.host.onEndTurn = null;
        r.flow.endTurnNow("auto");
        r.clock.advanceMs(1499);
        r.flow.tick();
        assertTrue(r.flow.isInputBlocked());
        r.clock.advanceMs(1);
        r.flow.tick();                          // refused
        assertFalse(r.flow.isInputBlocked());
        assertEquals(0, r.count("next"));
        r.run();                                // the task behind the queue
        assertEquals(1, r.count("next"));

        // A unit came up meanwhile: nothing is asked.
        final Rig u = new Rig(this.game);
        u.host.onEndTurn = null;
        u.flow.endTurnNow("auto");
        u.clock.advanceMs(1500);
        u.flow.tick();
        assertFalse(u.flow.unitChosen(a, null));   // made active at once
        u.host.active = a;
        u.run();
        assertEquals(0, u.count("next"));

        // The new turn was queued behind the slides: it settles first.
        final Rig q = new Rig(this.game);
        q.host.onEndTurn = null;
        q.flow.endTurnNow("auto");
        q.clock.advanceMs(1500);
        q.flow.tick();
        q.host.turn = 2;
        q.run();
        assertEquals(0, q.count("next"));
        assertTrue(q.flow.isWaiting());
        q.clock.advanceMs(200);
        assertTrue(q.flow.unitChosen(a, null));
        assertEquals(1, q.count("wipe"));
        q.advanceMs(299.9);
        assertEquals(0, q.count("activate " + a.getId()));
        q.advanceMs(0.2);
        assertEquals(1, q.count("activate " + a.getId()));

        // The slides outlasted the timeout and the recovery: the late turn
        // change still wipes, and the block keeps its 300 ms.
        final Rig s = new Rig(this.game);
        s.host.onEndTurn = null;
        s.flow.endTurnNow("auto");
        s.clock.advanceMs(1500);
        s.flow.tick();
        s.run();
        assertEquals(1, s.count("next"));
        s.clock.advanceMs(2000);
        s.host.turn = 2;
        assertTrue(s.flow.unitChosen(a, null));
        assertEquals(1, s.count("wipe"));
        s.advanceMs(299.9);
        assertEquals(0, s.count("activate " + a.getId()));
        s.advanceMs(0.2);
        assertEquals(1, s.count("activate " + a.getId()));
    }

    /**
     * FINAL "Open" item 9: an end the controller did not send at all is
     * refused at once (no 1.5 s with the input blocked); a sent one waits
     * for the player change from the server's answer, so a slow answer
     * never counts as a refusal and lets a second request out.
     */
    public void testEndRefusalTimedFromTheAnswer() {
        // Not sent (a goto or trade-route unit to look at): refused now.
        final Rig r = new Rig(this.game);
        r.host.onEndTurn = null;
        r.host.endTurnSent = false;
        r.flow.endTurnNow("auto");
        assertFalse(r.flow.isInputBlocked());
        assertEquals(-1, r.flow.indicatorRgb());   // the prediction is gone
        r.run();                                   // the recovery
        assertEquals(1, r.count("next"));

        // Sent, the answer took 2 s, still our turn: no refusal yet.
        final Rig s = new Rig(this.game);
        s.host.onEndTurn = () -> s.clock.advanceMs(2000);
        s.flow.endTurnNow("auto");
        s.flow.tick();
        assertTrue(s.flow.isInputBlocked());
        s.advanceMs(1499);
        s.flow.tick();
        assertTrue(s.flow.isInputBlocked());       // Enter does nothing yet
        s.clock.advanceMs(1);
        s.flow.tick();
        assertFalse(s.flow.isInputBlocked());      // refused, 1.5 s after the answer
        assertEquals(1, s.count("endTurn"));
    }

    /**
     * FINAL "Open" item 6: a host call that throws in a stage with stages
     * after it leaves the rest of the pause scheduled; the unit still
     * comes up and the input is free again.
     */
    public void testStageThatThrowsKeepsTheSchedule() {
        final Unit a = ship(5, 5), b = ship(7, 5);
        final Rig r = new Rig(this.game);
        r.host.active = a;
        a.setMovesLeft(0);
        r.host.jump = true;
        r.host.jumpThrows = true;
        r.flow.screenChanged();
        assertTrue(r.flow.unitChosen(b, a));
        r.clock.advanceMs(280);
        assertTrue(r.flow.runDue());
        try {
            r.edt.remove(0).run();
            fail("the jump should have thrown");
        } catch (IllegalStateException e) {
            // the event thread's handler logs it in the game
        }
        assertNotNull(r.flow.pending());           // the block is still to come
        r.advanceMs(220);
        assertEquals(1, r.count("activate " + b.getId()));
        assertNull(r.flow.pending());
        assertFalse(r.flow.isInputBlocked());
    }

    /**
     * FINAL "Open" item 11: once disposed (the game view went) the flow
     * ignores every late call -- a queued village cancel, a box close, a
     * poll, the controller's unit -- and asks the host for nothing.
     */
    public void testDisposedFlowIgnoresLateCalls() {
        final Unit a = ship(5, 5);
        final Rig r = new Rig(this.game);
        r.flow.dispose();
        r.host.calls.clear();
        r.host.turn = 2;                          // a unit now would start a turn
        r.host.nextGoingTo = true;
        r.host.firstGoingTo = a;
        r.flow.villageBoxCancelled(null);
        r.flow.boxClosed();
        r.flow.screenChanged();
        r.flow.tick();
        r.flow.noUnitLeft();
        assertFalse(r.flow.unitChosen(a, null));   // made active at once
        r.flow.endTurnNow("key");
        r.run();
        assertEquals("[]", r.host.calls.toString());
        assertNull(r.flow.pending());
    }

    /**
     * F4: our colour also lights while a turn-start box is up (the
     * original shows it while its messages are open); the wipe at the
     * close then needs no tick of its own.
     */
    public void testOwnColourWhileATurnStartBoxIsUp() {
        final Unit a = ship(5, 5);
        final Rig r = new Rig(this.game);
        r.flow.endTurnNow("key");
        r.flow.tick();
        assertEquals(0x6D3C18, r.flow.indicatorRgb());
        r.host.turn = 2;
        r.host.myTurn = true;
        r.host.current = r.host.me;
        r.host.blocked = true;
        r.flow.tick();
        assertEquals(0xFF7100, r.flow.indicatorRgb());
        assertEquals(Integer.valueOf(0xFF7100),
                     r.host.painted.get(r.host.painted.size() - 1));
        assertTrue(r.flow.unitChosen(a, null));
        assertEquals(0, r.count("wipe"));
        r.host.blocked = false;
        r.host.painted.clear();
        final long before = r.clock.now;
        r.flow.boxClosed();
        assertEquals(1, r.count("wipe"));
        assertTrue(r.host.painted.toString(), r.host.painted.isEmpty());
        assertEquals(before, r.clock.now);
        r.advanceMs(300);
        assertEquals(1, r.count("activate " + a.getId()));
    }

    /**
     * A classic screen counts only while the player can be looking at it
     * ({@link ClassicGUI#screenUp}): behind the active map or minimized it
     * holds nothing.  A hand-over held by a screen in front comes up as
     * soon as the player clicks the map, with no close.
     */
    public void testScreenBehindTheMap() {
        assertTrue(ClassicGUI.screenUp(true, false, false));
        assertFalse(ClassicGUI.screenUp(true, false, true));     // behind the map
        assertFalse(ClassicGUI.screenUp(true, true, false));     // minimized
        assertFalse(ClassicGUI.screenUp(false, false, false));   // closed

        final Unit a = ship(5, 5), b = ship(7, 5);
        final Rig r = new Rig(this.game);
        r.host.active = a;
        a.setMovesLeft(0);
        r.host.blocked = ClassicGUI.screenUp(true, false, false);   // in front
        assertTrue(r.flow.unitChosen(b, a));
        r.advanceMs(500);
        assertTrue(r.flow.pending().held);
        assertTrue(r.flow.isInputBlocked());
        r.host.blocked = ClassicGUI.screenUp(true, false, true);    // map clicked
        r.flow.tick();
        assertEquals(1, r.count("activate " + b.getId()));
        assertFalse(r.flow.isInputBlocked());

        // The screen stays behind the map: the next turn starts as usual.
        r.flow.endTurnNow("key");
        r.host.turn = 2;
        r.host.myTurn = true;
        r.host.current = r.host.me;
        assertTrue(r.flow.unitChosen(b, null));
        assertEquals(1, r.count("wipe"));
        r.advanceMs(300);
        assertEquals(2, r.count("activate " + b.getId()));
    }

    /** The indicator's colours: the table, the prediction, ours, none. */
    public void testIndicator() {
        final String[][] table = {
            { "model.nation.inca", "F7F3C7" }, { "model.nation.aztec", "C7A220" },
            { "model.nation.arawak", "698AC3" }, { "model.nation.iroquois", "6D3C18" },
            { "model.nation.cherokee", "75A64D" }, { "model.nation.apache", "C3AE86" },
            { "model.nation.sioux", "920000" }, { "model.nation.tupi", "045D04" },
            { "model.nation.english", "FF0000" }, { "model.nation.french", "5555FF" },
            { "model.nation.spanish", "FFFF55" }, { "model.nation.dutch", "FF7100" }
        };
        for (String[] t : table) {
            final Player p = this.game.getPlayerByNationId(t[0]);
            assertNotNull(t[0], p);
            assertEquals(t[0], Integer.parseInt(t[1], 16), ClassicHud.indicatorRgb(p));
        }
        assertEquals(-1, ClassicHud.indicatorRgb(null));

        final Rig r = new Rig(this.game);
        assertEquals(-1, r.flow.indicatorRgb());
        // While our request goes out: the next player's colour.
        r.host.onEndTurn = () -> {
            r.host.calls.add("rgb " + Integer.toHexString(r.flow.indicatorRgb()));
        };
        r.flow.endTurnNow("key");
        assertTrue(r.host.calls.contains("rgb 6d3c18"));
        // Still ours after the answer: it waits, then counts as refused.
        assertTrue(r.flow.isInputBlocked());
        r.advanceMs(1500);
        r.flow.tick();
        assertFalse(r.flow.isInputBlocked());
        assertEquals(-1, r.flow.indicatorRgb());

        // The poll repaints only when the colour changes.
        r.host.calls.clear();
        r.flow.indicatorShown(-1);
        r.flow.tick();
        assertEquals(0, r.count("indicator"));
        r.host.myTurn = false;
        r.host.current = this.game.getPlayerByNationId("model.nation.sioux");
        r.flow.tick();
        assertEquals(1, r.count("indicator"));
        r.flow.indicatorShown(r.flow.indicatorRgb());
        r.flow.tick();
        assertEquals(1, r.count("indicator"));
    }

    /** The one-shot timer: replace, cancel, stale posts dropped, the thread. */
    public void testOneShot() throws Exception {
        final FakeClock clock = new FakeClock();
        final List<Runnable> edt = new ArrayList<>();
        final List<String> ran = new ArrayList<>();
        final ClassicOneShot t = new ClassicOneShot(clock, edt::add, false, "test");
        t.schedule(clock.now + 100 * MS, () -> ran.add("a"));
        assertFalse(t.runIfDue());
        clock.advanceMs(100);
        assertTrue(t.runIfDue());
        assertFalse(t.runIfDue());             // posted once
        // Replaced before the post ran: the stale post is dropped.
        t.schedule(clock.now + 50 * MS, () -> ran.add("b"));
        edt.remove(0).run();
        assertTrue(ran.isEmpty());
        assertTrue(t.isScheduled());
        clock.advanceMs(50);
        assertTrue(t.runIfDue());
        t.cancel();
        edt.remove(0).run();
        assertTrue(ran.isEmpty());
        t.schedule(clock.now, () -> ran.add("c"));
        assertTrue(t.runIfDue());
        edt.remove(0).run();
        assertEquals(List.of("c"), ran);
        assertFalse(t.isScheduled());
        t.close();

        // The thread on the real clock: never early, and on time to well
        // within a frame.  The best of five tries counts, so a loaded
        // machine (a parallel suite) that delays one wake-up does not fail
        // it, while a timer that is always late does (FINAL "Open" item
        // 10; a sleep on the 15.6-ms Windows tick would be late every time).
        final ClassicOneShot real = new ClassicOneShot(ClassicSlide.SYSTEM,
            Runnable::run, true, "test-real");
        final List<String> tries = new ArrayList<>();
        long best = Long.MAX_VALUE;
        for (int i = 0; i < 5 && best >= 8 * MS; i++) {
            final CountDownLatch done = new CountDownLatch(1);
            final long[] at = new long[1];
            final long due = System.nanoTime() + 120 * MS;
            real.schedule(due, () -> {
                    at[0] = System.nanoTime();
                    done.countDown();
                });
            assertTrue(done.await(2, TimeUnit.SECONDS));
            assertTrue((at[0] - due) / 1e6 + " ms early", at[0] >= due);
            best = Math.min(best, at[0] - due);
            tries.add(String.format(java.util.Locale.ROOT, "%.2f", (at[0] - due) / 1e6));
        }
        assertTrue("late (ms): " + tries, best < 8 * MS);
        real.close();
    }
}
