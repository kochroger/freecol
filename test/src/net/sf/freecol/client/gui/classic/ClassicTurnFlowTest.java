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
 * goto units and the visits at their place in the unit cycle (W5f), W
 * and a click, the input block and the turn indicator's colours;
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
            promptPref = false, jump = false;
        int turn = 1;
        Unit active = null;
        Player me, current, next;
        final List<String> calls = new ArrayList<>();
        final List<Runnable> posted = new ArrayList<>();
        /** What endTurn does to the game (the server's answer). */
        Runnable onEndTurn = null;
        /** What endTurn returns: whether the controller asked the server. */
        boolean endTurnSent = true;

        /**
         * The real unit cycle on {@link #me}'s units, or null for the
         * scripted one ({@link #kinds}, {@link #cycle}, {@link #due}):
         * nothing due, so the controller's choice comes.
         */
        ClassicUnitCycle real = null;
        /** The scripted kinds (absent: not due). */
        final java.util.Map<Unit, ClassicUnitCycle.Kind> kinds = new java.util.HashMap<>();
        /** The scripted next unit after an anchor (null: none). */
        java.util.function.UnaryOperator<Unit> cycle = a -> null;
        /** The scripted "anything due". */
        boolean due = false;

        @Override public boolean myTurn() { return this.myTurn; }
        @Override public boolean blocked() { return this.blocked; }
        @Override public boolean hasNextActiveUnit() { return this.nextActive; }

        @Override
        public ClassicUnitCycle.Kind dueKind(Unit unit) {
            return (this.real != null) ? this.real.kind(unit) : this.kinds.get(unit);
        }

        @Override
        public Unit cycleNext(Unit anchor) {
            return (this.real != null) ? this.real.next(anchor, this.me)
                : this.cycle.apply(anchor);
        }

        @Override
        public boolean anyDue() {
            return (this.real != null) ? this.real.anyDue(this.me) : this.due;
        }

        @Override
        public void putBack(Unit unit) {
            this.calls.add("putBack " + unit.getId());
        }

        @Override
        public void turnBegins() {
            this.calls.add("begins");
            if (this.real != null) this.real.turnStarted(this.me);
        }

        @Override
        public void turnEnding() {
            this.calls.add("ending");
            if (this.real != null) this.real.snapshot(this.me);
        }

        @Override
        public void endRefused() {
            this.calls.add("refused");
            if (this.real != null) this.real.endRefused();
        }

        /** What a goto run does to the game (its steps), or null. */
        java.util.function.Consumer<Unit> onGoto = null;

        @Override
        public void runGoto(Unit unit) {
            this.calls.add("goto " + unit.getId());
            if (this.real != null) this.real.ran(unit);
            if (this.onGoto != null) this.onGoto.accept(unit);
        }

        @Override
        public void visit(Unit unit) {
            this.calls.add("visit " + unit.getId());
        }

        @Override
        public void visitShown(Unit unit) {
            this.calls.add("shown " + unit.getId());
            if (this.real != null) this.real.visited(unit);
            if (this.flow != null) this.flow.screenChanged();   // the map's paint
        }

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

        /** Whether a turn-start box must come first (the father choice). */
        boolean hold = false;

        @Override
        public boolean holdTurnStart() {
            if (this.hold) this.calls.add("hold");
            return this.hold;
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

    /**
     * A turn-start box that must come first (the father choice, build spec
     * D8a): the host holds the wipe, the year does not flip and the first
     * unit does not come; once the box is taken the poll wipes, and the
     * first unit follows 300 ms later.  Without units the idle end waits
     * for it too.
     */
    public void testHeldTurnStart() {
        final Unit a = ship(5, 5);
        final Rig r = new Rig(this.game);
        r.flow.endTurnNow("key");
        r.host.turn = 2;
        r.host.myTurn = true;
        r.host.current = r.host.me;
        r.host.hold = true;
        assertTrue(r.flow.unitChosen(a, null));
        assertEquals(0, r.count("wipe"));
        assertEquals(1, r.count("hold"));
        r.flow.tick();
        r.advanceMs(1000);
        assertEquals(0, r.count("wipe"));
        assertEquals(0, r.count("activate " + a.getId()));
        r.host.hold = false;                   // the father is taken
        r.flow.tick();
        assertEquals(1, r.count("wipe"));
        r.advanceMs(300);
        assertEquals(1, r.count("activate " + a.getId()));
        // No unit: the idle end waits for the box as well.
        r.flow.endTurnNow("key");
        r.host.turn = 3;
        r.host.myTurn = true;
        r.host.current = r.host.me;
        r.host.hold = true;
        r.flow.noUnitLeft();
        r.advanceMs(1000);
        assertEquals(1, r.count("wipe"));
        final int ends = r.count("endTurn");
        r.host.hold = false;
        r.flow.tick();
        assertEquals(2, r.count("wipe"));
        r.advanceMs(485);
        assertEquals(ends + 1, r.count("endTurn"));
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

    /** Our end, the AI phase, and our turn {@code turn}, not shown yet. */
    private static void nextTurn(Rig r, int turn) {
        r.flow.endTurnNow("key");
        r.host.turn = turn;
        r.host.myTurn = true;
        r.host.current = r.host.me;
    }

    /** Run the stage due {@code ms} from now on the event thread, not what it posts. */
    private static void runStage(Rig r, double ms) {
        r.clock.advanceMs(ms);
        assertTrue(r.flow.runDue());
        assertEquals(1, r.edt.size());
        r.edt.remove(0).run();
    }

    /**
     * W5f: a goto unit moves when the unit cycle reaches it, not in a
     * batch first (c6 U25, U26, U22).  The controller's choice (by its
     * tile order) goes back, also when it chooses again; the goto unit's
     * block 500 ms after the last change, its first step 28 ms after the
     * block; meanwhile the controller's view changes are dropped and the
     * input is blocked.  Arrived with moves left it stays the active unit
     * (its block again, the blink timed from it: c6 #5123); with none the
     * next unit of the cycle comes 500 ms after its last change (c6 U22
     * #4803 -&gt; #4839).
     */
    public void testGotoInTheCycle() {
        assertEquals("[ACTIVATE@500, GOTO@528]",
                     java.util.Arrays.toString(ClassicTurnFlow.gotoStages(false)));
        assertEquals("[JUMP@280, ACTIVATE@500, GOTO@528]",
                     java.util.Arrays.toString(ClassicTurnFlow.gotoStages(true)));
        // Inside the measured window (0-71 ms mid-turn).
        assertTrue(ClassicTurnFlow.GOTO_HANDOVER_MS >= 0
                   && ClassicTurnFlow.GOTO_HANDOVER_MS <= 71);

        final Unit a = ship(5, 5), b = ship(7, 5), g = ship(9, 5), c = ship(11, 5);
        assertEquals(Unit.UnitState.ACTIVE, g.getState());
        final Rig r = new Rig(this.game);
        r.host.active = a;
        r.host.kinds.put(b, ClassicUnitCycle.Kind.ORDERS);
        r.host.kinds.put(c, ClassicUnitCycle.Kind.ORDERS);
        r.host.kinds.put(g, ClassicUnitCycle.Kind.GOTO);
        r.host.cycle = x -> (x == a) ? g : null;
        a.setMovesLeft(0);
        r.flow.screenChanged();                    // a's last final draw
        r.clock.advanceMs(2);
        assertTrue(r.flow.unitChosen(b, a));       // the controller's: b
        assertEquals(1, r.count("putBack " + b.getId()));
        assertSame(g, r.flow.pending().unit);
        assertTrue(r.flow.isInputBlocked());
        assertTrue(r.flow.unitChosen(c, a));       // it chooses again: kept
        assertEquals(1, r.count("putBack " + c.getId()));
        assertSame(g, r.flow.pending().unit);
        r.advanceMs(497.9);
        assertEquals(0, r.count("activate " + g.getId()));
        r.advanceMs(0.2);
        assertEquals(1, r.count("activate " + g.getId()));
        assertEquals(0, r.count("goto " + g.getId()));
        r.host.active = g;
        runStage(r, 28);
        assertEquals(1, r.count("goto " + g.getId()));
        assertTrue(r.flow.ignoring());
        assertSame(g, r.flow.gotoUnit());
        assertTrue(r.flow.isInputBlocked());
        // The marker: arrived with moves left, it stays the active unit.
        r.run();
        assertFalse(r.flow.ignoring());
        assertNull(r.flow.gotoUnit());
        assertEquals(2, r.count("activate " + g.getId()));
        assertNull(r.flow.pending());
        assertFalse(r.flow.isInputBlocked());
        assertEquals(0, r.count("next"));

        // No moves left after its run: the next unit of the cycle 500 ms
        // after its last step's final draw.
        final Unit h = ship(13, 5);
        r.host.kinds.put(h, ClassicUnitCycle.Kind.GOTO);
        r.host.cycle = x -> (x == g) ? h : (x == h) ? b : null;
        g.setMovesLeft(0);
        r.flow.screenChanged();
        assertTrue(r.flow.unitChosen(b, g));
        assertSame(h, r.flow.pending().unit);
        r.advanceMs(500);
        assertEquals(1, r.count("activate " + h.getId()));
        r.host.active = h;
        r.host.onGoto = u -> {
            u.setMovesLeft(0);
            r.flow.screenChanged();                // the last step's final draw
        };
        runStage(r, 28);
        r.run();                                   // the marker
        assertEquals(ClassicTurnFlow.Kind.HANDOVER, r.flow.pending().kind);
        assertSame(b, r.flow.pending().unit);
        assertEquals(1, r.count("activate " + h.getId()));
        r.advanceMs(499.9);
        assertEquals(0, r.count("activate " + b.getId()));
        r.advanceMs(0.2);
        assertEquals(1, r.count("activate " + b.getId()));
        assertEquals(0, r.count("next"));
    }

    /**
     * The head of the cycle at the turn start is a goto unit (LF 1502: the
     * empty start ship with its destination, then the pioneer): the
     * cycle's turn begins before the choice, the controller's unit goes
     * back, the block 300 ms after the wipe and the first step 100 ms
     * after the block.  With only goto units (or visits) and no
     * candidate, the controller's end view starts the turn start too.
     */
    public void testGotoAtTheTurnStart() {
        final Unit b = ship(5, 5), g = ship(7, 5);
        final Rig r = new Rig(this.game);
        nextTurn(r, 2);
        r.host.kinds.put(b, ClassicUnitCycle.Kind.ORDERS);
        r.host.kinds.put(g, ClassicUnitCycle.Kind.GOTO);
        r.host.cycle = x -> (x == null) ? g : (x == g) ? b : null;
        assertTrue(r.flow.unitChosen(b, null));
        assertTrue(r.host.calls.indexOf("begins") < r.host.calls.indexOf("wipe"));
        assertEquals(1, r.count("putBack " + b.getId()));
        assertEquals(ClassicTurnFlow.Kind.TURN_START, r.flow.pending().kind);
        assertSame(g, r.flow.pending().unit);
        assertEquals("TURN_START ACTIVATE@300 GOTO@400", r.flow.pending().toString());
        r.advanceMs(300);
        assertEquals(1, r.count("activate " + g.getId()));
        r.host.active = g;
        runStage(r, 100);
        assertEquals(1, r.count("goto " + g.getId()));
        g.setMovesLeft(0);
        r.run();
        assertSame(b, r.flow.pending().unit);
        r.advanceMs(500);
        assertEquals(1, r.count("activate " + b.getId()));
        assertEquals(1, r.count("begins"));

        // No candidate, the head is a goto unit: the controller's end
        // view gives the turn start.
        final Rig n = new Rig(this.game);
        nextTurn(n, 2);
        g.setMovesLeft(3);
        n.host.due = true;
        n.host.kinds.put(g, ClassicUnitCycle.Kind.GOTO);
        n.host.cycle = x -> (x == null) ? g : null;
        assertTrue(n.flow.dueInstead(null));
        assertEquals(1, n.count("begins"));
        assertEquals(1, n.count("wipe"));
        assertEquals(ClassicTurnFlow.Kind.TURN_START, n.flow.pending().kind);
        assertSame(g, n.flow.pending().unit);
        assertTrue(n.flow.dueInstead(null));       // again: kept
        n.advanceMs(300);
        assertEquals(1, n.count("activate " + g.getId()));

        // Nothing due: the end view, the wipe and the automatic end.
        final Rig e = new Rig(this.game);
        nextTurn(e, 2);
        assertFalse(e.flow.dueInstead(null));
        assertEquals(1, e.count("begins"));
        e.flow.noUnitLeft();
        assertEquals(1, e.count("begins"));        // once per turn
        assertEquals(1, e.count("wipe"));
        assertEquals(ClassicTurnFlow.Kind.END_TURN, e.flow.pending().kind);
    }

    /**
     * W5f: a unit whose road, plowing or fortification was completed at
     * the turn start gets a silent visit (c6 #3447/#3450, #4555/#4556,
     * #4589/#4590): the jump at the hand-over's 500 ms, the completion 15
     * ms later, never an activation, and the next unit 500 ms after the
     * completion; a visit after a visit; a visit at the turn start; a box
     * holds it.
     */
    public void testVisitStages() {
        assertEquals("[VISIT@500, SHOW@515]",
                     java.util.Arrays.toString(ClassicTurnFlow.visitStages()));
        assertEquals("[VISIT@300, SHOW@315]", java.util.Arrays.toString(
            ClassicTurnFlow.turnStartStages(false, ClassicUnitCycle.Kind.VISIT)));
        assertEquals("[VISIT@500, SHOW@515]", java.util.Arrays.toString(
            ClassicTurnFlow.turnStartStages(true, ClassicUnitCycle.Kind.VISIT)));
        // The measured windows: the jump 471-542 ms after the last change,
        // the completion 1-3 frames after it.
        assertTrue(ClassicTurnFlow.HANDOVER_MS >= 471 && ClassicTurnFlow.HANDOVER_MS <= 542);
        assertTrue(ClassicTurnFlow.VISIT_SHOW_MS >= 14 && ClassicTurnFlow.VISIT_SHOW_MS <= 43);

        final Unit a = ship(5, 5), v = ship(7, 5), w = ship(9, 5), c = ship(11, 5);
        final Rig r = new Rig(this.game);
        r.host.active = a;
        r.host.kinds.put(v, ClassicUnitCycle.Kind.VISIT);
        r.host.kinds.put(w, ClassicUnitCycle.Kind.VISIT);
        r.host.kinds.put(c, ClassicUnitCycle.Kind.ORDERS);
        r.host.cycle = x -> (x == a) ? v : (x == v) ? w : (x == w) ? c : null;
        a.setMovesLeft(0);
        r.flow.screenChanged();
        assertTrue(r.flow.unitChosen(c, a));
        assertSame(v, r.flow.pending().unit);
        r.advanceMs(499.9);
        assertEquals(0, r.count("visit " + v.getId()));
        r.advanceMs(0.2);
        assertEquals(1, r.count("visit " + v.getId()));
        assertTrue(r.flow.isInputBlocked());
        r.advanceMs(14.8);
        assertEquals(0, r.count("shown " + v.getId()));
        r.advanceMs(0.2);
        assertEquals(1, r.count("shown " + v.getId()));
        // The next visit, 500 ms after the completion; a box holds it.
        assertSame(w, r.flow.pending().unit);
        r.host.blocked = true;
        r.advanceMs(500);
        assertEquals(0, r.count("visit " + w.getId()));
        r.host.blocked = false;
        r.flow.boxClosed();
        assertEquals(1, r.count("visit " + w.getId()));
        r.advanceMs(15);
        assertEquals(1, r.count("shown " + w.getId()));
        assertSame(c, r.flow.pending().unit);
        r.advanceMs(499.9);
        assertEquals(0, r.count("activate " + c.getId()));
        r.advanceMs(0.2);
        assertEquals(1, r.count("activate " + c.getId()));
        assertEquals(0, r.count("activate " + v.getId()));
        assertEquals(0, r.count("activate " + w.getId()));

        // A visit at the turn start: at the block's time, no block.
        final Rig t = new Rig(this.game);
        nextTurn(t, 2);
        t.host.kinds.put(v, ClassicUnitCycle.Kind.VISIT);
        t.host.kinds.put(c, ClassicUnitCycle.Kind.ORDERS);
        t.host.cycle = x -> (x == null) ? v : (x == v) ? c : null;
        assertTrue(t.flow.unitChosen(c, null));
        assertSame(v, t.flow.pending().unit);
        t.advanceMs(300);
        assertEquals(1, t.count("visit " + v.getId()));
        t.advanceMs(15);
        assertEquals(1, t.count("shown " + v.getId()));
        t.advanceMs(500);
        assertEquals(1, t.count("activate " + c.getId()));
        assertEquals(0, t.count("activate " + v.getId()));

        // The visited unit gone before its visit: nothing stays held, the
        // cycle goes on.
        final Rig g = new Rig(this.game);
        g.host.active = a;
        g.host.kinds.put(w, ClassicUnitCycle.Kind.VISIT);
        g.host.cycle = x -> (x == a) ? w : (x == w) ? c : null;
        assertTrue(g.flow.unitChosen(c, a));
        w.setLocation(this.game.getPlayerByNationId("model.nation.dutch").getHighSeas());
        g.advanceMs(500);
        assertEquals(0, g.count("visit " + w.getId()));
        assertEquals(1, g.count("shown " + w.getId()));
        assertSame(c, g.flow.pending().unit);
    }

    /**
     * The controller brings no goto unit and no visit (its batch is off):
     * with none of its candidates left, a due one comes through the cycle
     * instead of the end -- from its end view ({@link
     * ClassicTurnFlow#dueInstead}), from a tile selection, and when an
     * armed idle end finds one due.
     */
    public void testIdleWaitsForDueUnits() {
        final Unit a = ship(5, 5), g = ship(7, 5);
        final Rig r = new Rig(this.game);
        r.host.active = a;
        a.setMovesLeft(0);
        r.host.due = true;
        r.host.kinds.put(g, ClassicUnitCycle.Kind.GOTO);
        r.host.cycle = x -> (x == a || x == null) ? g : null;
        r.flow.screenChanged();
        assertTrue(r.flow.dueInstead(a));
        assertEquals(ClassicTurnFlow.Kind.HANDOVER, r.flow.pending().kind);
        assertSame(g, r.flow.pending().unit);
        assertTrue(r.flow.dueInstead(a));          // again: kept
        r.advanceMs(500);
        assertEquals(1, r.count("activate " + g.getId()));
        assertEquals(0, r.count("endTurn"));
        r.host.due = false;
        assertFalse(r.flow.dueInstead(g));

        // A tile selection with a goto unit due and no candidate.
        final Rig m = new Rig(this.game);
        m.host.due = true;
        m.host.kinds.put(g, ClassicUnitCycle.Kind.GOTO);
        m.host.cycle = x -> g;
        m.flow.noUnitLeft();
        assertEquals(ClassicTurnFlow.Kind.HANDOVER, m.flow.pending().kind);
        assertSame(g, m.flow.pending().unit);

        // The idle end armed; a visit becomes due before it fires: kept,
        // the controller brings nothing, the cycle's unit comes.
        final Rig n = new Rig(this.game);
        n.flow.noUnitLeft();
        assertEquals(ClassicTurnFlow.Kind.END_TURN, n.flow.pending().kind);
        n.host.due = true;
        n.host.kinds.put(g, ClassicUnitCycle.Kind.VISIT);
        n.host.cycle = x -> g;
        n.advanceMs(485);
        assertEquals(0, n.count("endTurn"));
        assertEquals(1, n.count("next"));
        assertEquals(ClassicTurnFlow.Kind.HANDOVER, n.flow.pending().kind);
        assertEquals("HANDOVER VISIT@500 SHOW@515", n.flow.pending().toString());
    }

    /**
     * With the real unit cycle: a goto unit runs once per turn.  Its run
     * took no step (a blocked path) and left it active with moves: it
     * stays the player's, due as ORDERS, and is not run again (FreeCol's
     * end-of-turn goto pass must not find it, C FINAL "Open" item 9);
     * skipped, the cycle goes on after it; next turn it is a goto unit
     * again, at its place (c6 #9413: the G of 1730).
     */
    public void testGotoRunsOncePerTurn() {
        final Unit a = ship(5, 5), g = ship(7, 5), b = ship(9, 5);
        g.setDestination(this.map.getTile(12, 12));
        final Rig r = new Rig(this.game);
        r.host.real = new ClassicUnitCycle();
        assertSame(ClassicUnitCycle.Kind.GOTO, r.host.dueKind(g));
        assertSame(ClassicUnitCycle.Kind.ORDERS, r.host.dueKind(b));
        r.host.active = a;
        a.setMovesLeft(0);
        r.flow.screenChanged();
        assertTrue(r.flow.unitChosen(b, a));       // the controller: b
        assertSame(g, r.flow.pending().unit);
        r.advanceMs(500);
        r.host.active = g;
        runStage(r, 28);
        assertEquals(1, r.count("goto " + g.getId()));
        r.run();
        assertEquals(2, r.count("activate " + g.getId()));
        assertNull(r.flow.pending());
        assertSame(ClassicUnitCycle.Kind.ORDERS, r.host.dueKind(g));
        // Space: the cycle goes on after it.
        g.setState(Unit.UnitState.SKIPPED);
        r.flow.screenChanged();
        assertTrue(r.flow.unitChosen(b, g));
        assertSame(b, r.flow.pending().unit);
        assertEquals("HANDOVER ACTIVATE@500", r.flow.pending().toString());
        r.advanceMs(500);
        assertEquals(1, r.count("activate " + b.getId()));
        assertEquals(1, r.count("goto " + g.getId()));
        // The next turn: a goto unit again, the head of the due units.
        nextTurn(r, 2);
        g.setState(Unit.UnitState.ACTIVE);         // the server's new turn
        assertTrue(r.flow.unitChosen(b, null));
        assertSame(g, r.flow.pending().unit);
        assertEquals("TURN_START ACTIVATE@300 GOTO@400", r.flow.pending().toString());
    }

    /**
     * W (I: never recorded): the cycle's next unit after the waiting one
     * comes at once -- an ORDERS unit activated now, a goto unit with its
     * steps, a visit with its completion; with only the waiting unit due
     * nothing changes; blocked input takes no W.
     */
    public void testWaitTakesTheCycle() {
        final Unit a = ship(5, 5), b = ship(7, 5), g = ship(9, 5), v = ship(11, 5);
        final Rig r = new Rig(this.game);
        r.host.active = a;
        r.host.kinds.put(a, ClassicUnitCycle.Kind.ORDERS);
        r.host.kinds.put(b, ClassicUnitCycle.Kind.ORDERS);
        r.host.kinds.put(g, ClassicUnitCycle.Kind.GOTO);
        r.host.kinds.put(v, ClassicUnitCycle.Kind.VISIT);
        r.host.cycle = x -> (x == a) ? b : (x == b) ? g : (x == g) ? v : a;
        assertTrue(r.flow.waited(a));
        assertEquals(1, r.count("activate " + b.getId()));
        assertNull(r.flow.pending());
        r.host.active = b;
        assertTrue(r.flow.waited(b));
        assertNotNull(r.flow.pending());
        assertFalse(r.flow.waited(b));             // blocked meanwhile
        r.run();
        assertEquals(1, r.count("activate " + g.getId()));
        r.host.active = g;
        r.advanceMs(28);                           // the step, then its marker
        assertEquals(1, r.count("goto " + g.getId()));
        assertEquals(2, r.count("activate " + g.getId()));   // stays: moves left
        assertTrue(r.flow.waited(g));
        r.run();
        assertEquals(1, r.count("visit " + v.getId()));
        r.advanceMs(15);
        assertEquals(1, r.count("shown " + v.getId()));
        r.advanceMs(1000);
        // Only the waiting unit due: it stays.
        final Rig s = new Rig(this.game);
        s.host.kinds.put(a, ClassicUnitCycle.Kind.ORDERS);
        s.host.cycle = x -> a;
        assertTrue(s.flow.waited(a));
        assertEquals("[]", s.host.calls.toString());
    }

    /**
     * A click on an own unit is not replaced by the cycle's choice, also
     * when the previous unit ran out (a plain hand-over to it); nothing is
     * put back.  The boarding's carrier is not replaced either.
     */
    public void testClickIsNotReplaced() {
        final Unit a = ship(5, 5), c = ship(7, 5), g = ship(9, 5);
        final Rig r = new Rig(this.game);
        r.host.active = a;
        r.host.kinds.put(g, ClassicUnitCycle.Kind.GOTO);
        r.host.kinds.put(c, ClassicUnitCycle.Kind.GOTO);
        r.host.cycle = x -> g;
        assertFalse(r.flow.unitClicked(c, a));     // a still has moves: at once
        a.setMovesLeft(0);
        assertTrue(r.flow.unitClicked(c, a));
        assertSame(c, r.flow.pending().unit);
        assertEquals("HANDOVER ACTIVATE@500", r.flow.pending().toString());
        assertEquals(0, r.count("putBack " + c.getId()));
        r.flow.noUnitLeft();
        assertTrue(r.flow.carrierChosen(c, a));
        assertSame(c, r.flow.pending().unit);
        assertEquals("HANDOVER ACTIVATE@128", r.flow.pending().toString());
    }

    /**
     * A unit that got orders and kept its moves (a goto order that stopped
     * early at a region to discover, F, S) is no longer a candidate: the
     * cycle's next unit comes at once, as the controller's would (no
     * pause), and the controller's choice goes back.  With no candidate
     * left the same from its end view: a due unit at once, also the goto
     * unit itself, whose goto runs again; the previous unit keeps blinking
     * meanwhile, and its toggles (screen changes) must not hold the unit
     * back (live run g3-g-sq1: the hand-over to a blinking unit was
     * re-based by every toggle and never came, the turn never ended).
     */
    public void testOrdersGivenBringsTheCycleAtOnce() {
        final Unit a = ship(5, 5), b = ship(7, 5), c = ship(9, 5), g = ship(11, 5);
        final Rig r = new Rig(this.game);
        r.host.active = a;
        a.setState(Unit.UnitState.SENTRY);         // S: orders, moves kept
        assertFalse(a.isCandidateForNextActiveUnit());
        assertTrue(a.getMovesLeft() > 0);
        r.host.kinds.put(b, ClassicUnitCycle.Kind.ORDERS);
        r.host.kinds.put(c, ClassicUnitCycle.Kind.ORDERS);
        r.host.cycle = x -> (x == a) ? c : null;
        assertTrue(r.flow.unitChosen(b, a));       // the controller's: b
        assertEquals(1, r.count("putBack " + b.getId()));
        assertEquals(1, r.count("activate " + c.getId()));
        assertNull(r.flow.pending());
        // The cycle agrees with the controller: its unit at once.
        r.host.active = c;
        c.setState(Unit.UnitState.SENTRY);
        r.host.cycle = x -> b;
        assertFalse(r.flow.unitChosen(b, c));

        // No candidate left; the previous unit (its goto stopped early,
        // moves left) blinks on: the goto unit next comes at once.
        a.setState(Unit.UnitState.ACTIVE);
        a.setDestination(this.map.getTile(14, 14));
        r.host.active = a;
        r.host.due = true;
        r.host.kinds.put(g, ClassicUnitCycle.Kind.GOTO);
        r.host.cycle = x -> g;
        assertTrue(r.flow.dueInstead(a));
        for (int i = 0; i < 5; i++) {
            r.flow.screenChanged();                // the blink's toggles
            r.clock.advanceMs(1);
        }
        r.run();
        assertEquals(1, r.count("activate " + g.getId()));
        r.advanceMs(28);
        assertEquals(1, r.count("goto " + g.getId()));
        // The goto unit itself is the next due: its goto runs again once.
        r.host.active = g;
        r.host.kinds.put(g, ClassicUnitCycle.Kind.GOTO);
        r.host.cycle = x -> g;
        assertTrue(r.flow.dueInstead(g));
        r.flow.screenChanged();
        r.run();
        r.advanceMs(28);
        assertEquals(2, r.count("goto " + g.getId()));
        assertEquals(0, r.count("endTurn"));
    }

    /**
     * FreeCol's goto unit with no path is skipped by its controller: after
     * the run the cycle's next unit comes as a hand-over.  With nothing
     * else due the controller's end view arms the automatic end, and when
     * it chooses nothing at all (left in a mode) the flow arms it itself
     * (FINAL "Open" item 8): it never sits with no unit and nothing
     * scheduled.  Disposed meanwhile, the marker does nothing.
     */
    public void testGotoWithoutPathStillBringsAUnit() {
        final Unit g = ship(5, 5), b = ship(7, 5);
        final Rig r = new Rig(this.game);
        nextTurn(r, 2);
        r.host.kinds.put(g, ClassicUnitCycle.Kind.GOTO);
        r.host.kinds.put(b, ClassicUnitCycle.Kind.ORDERS);
        r.host.cycle = x -> (x == null) ? g : (x == g) ? b : null;
        r.host.onGoto = u -> u.setState(Unit.UnitState.SKIPPED);   // no path
        assertTrue(r.flow.unitChosen(g, null));
        r.advanceMs(300);
        runStage(r, 100);
        assertEquals(1, r.count("goto " + g.getId()));
        r.run();
        assertEquals(ClassicTurnFlow.Kind.HANDOVER, r.flow.pending().kind);
        assertSame(b, r.flow.pending().unit);
        r.advanceMs(500);
        assertEquals(1, r.count("activate " + b.getId()));
        assertFalse(r.flow.isInputBlocked());
        assertEquals(0, r.count("next"));

        // Nothing else due: the controller's end view arms the end.
        g.setState(Unit.UnitState.ACTIVE);
        final Rig n = new Rig(this.game);
        nextTurn(n, 2);
        n.host.kinds.put(g, ClassicUnitCycle.Kind.GOTO);
        n.host.cycle = x -> (x == null) ? g : null;
        n.host.onGoto = u -> u.setState(Unit.UnitState.SKIPPED);
        n.host.onNext = () -> {
            n.host.active = null;
            n.flow.noUnitLeft();
        };
        assertTrue(n.flow.unitChosen(g, null));
        n.advanceMs(300);
        runStage(n, 100);
        n.run();
        assertEquals(1, n.count("next"));
        assertEquals(ClassicTurnFlow.Kind.END_TURN, n.flow.pending().kind);

        // The controller chooses nothing: the flow arms the end itself.
        g.setState(Unit.UnitState.ACTIVE);
        final Rig q = new Rig(this.game);
        nextTurn(q, 2);
        q.host.kinds.put(g, ClassicUnitCycle.Kind.GOTO);
        q.host.cycle = x -> (x == null) ? g : null;
        q.host.onGoto = u -> u.setState(Unit.UnitState.SKIPPED);
        assertTrue(q.flow.unitChosen(g, null));
        q.advanceMs(300);
        runStage(q, 100);
        q.run();
        assertEquals(1, q.count("next"));
        assertEquals(ClassicTurnFlow.Kind.END_TURN, q.flow.pending().kind);
        q.advanceMs(485);
        assertEquals(2, q.count("endTurn"));

        // Disposed meanwhile (the game view went): the marker does nothing.
        g.setState(Unit.UnitState.ACTIVE);
        final Rig d = new Rig(this.game);
        nextTurn(d, 2);
        d.host.kinds.put(g, ClassicUnitCycle.Kind.GOTO);
        d.host.cycle = x -> (x == null) ? g : null;
        assertTrue(d.flow.unitChosen(g, null));
        d.advanceMs(300);
        runStage(d, 100);
        d.flow.dispose();
        d.run();
        assertEquals(0, d.count("next"));
        assertEquals(1, d.count("activate " + g.getId()));
    }

    /**
     * D acceptance D1: the goto ship left the map (sailing for Europe)
     * before its block, or by its own run: the cycle has nothing after it,
     * the controller chooses nothing, and with nothing left to move the
     * flow arms the automatic end itself, and the turn ends.  With a unit
     * left to move nothing is armed (the flow never ends a turn then).
     */
    public void testUnitGoneAndNoChoiceStillEnds() {
        final Player dutch = this.game.getPlayerByNationId("model.nation.dutch");
        assertNotNull(dutch.getHighSeas());
        final Unit g = ship(5, 5);
        final Rig r = new Rig(this.game);
        nextTurn(r, 2);
        r.host.kinds.put(g, ClassicUnitCycle.Kind.GOTO);
        r.host.cycle = x -> (x == null) ? g : null;
        assertTrue(r.flow.unitChosen(g, null));
        assertEquals(ClassicTurnFlow.Kind.TURN_START, r.flow.pending().kind);
        g.setLocation(dutch.getHighSeas());     // sailed off before its block
        r.advanceMs(300);                       // the block: gone, nothing chosen
        assertEquals(0, r.count("activate " + g.getId()));
        assertEquals(1, r.count("next"));
        assertNotNull(r.flow.pending());
        assertEquals(ClassicTurnFlow.Kind.END_TURN, r.flow.pending().kind);
        r.advanceMs(484.9);
        assertEquals(1, r.count("endTurn"));
        r.advanceMs(0.2);
        assertEquals(2, r.count("endTurn"));

        // Its own run takes it off; nothing comes.
        final Unit h = ship(7, 5);
        final Rig n = new Rig(this.game);
        nextTurn(n, 2);
        n.host.kinds.put(h, ClassicUnitCycle.Kind.GOTO);
        n.host.cycle = x -> (x == null) ? h : null;
        n.host.onGoto = u -> u.setLocation(dutch.getHighSeas());
        assertTrue(n.flow.unitChosen(h, null));
        n.advanceMs(300);
        assertEquals(1, n.count("activate " + h.getId()));
        runStage(n, 100);
        assertEquals(1, n.count("goto " + h.getId()));
        n.run();                                // the marker
        assertEquals(1, n.count("next"));
        assertEquals(ClassicTurnFlow.Kind.END_TURN, n.flow.pending().kind);
        n.advanceMs(485);
        assertEquals(2, n.count("endTurn"));

        // A unit left to move: no end armed.
        final Unit k = ship(9, 5);
        final Rig m = new Rig(this.game);
        nextTurn(m, 2);
        m.host.kinds.put(k, ClassicUnitCycle.Kind.GOTO);
        m.host.cycle = x -> (x == null) ? k : null;
        assertTrue(m.flow.unitChosen(k, null));
        k.setLocation(dutch.getHighSeas());
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
        r.host.due = true;                        // and a goto unit come
        r.host.kinds.put(a, ClassicUnitCycle.Kind.GOTO);
        r.host.cycle = x -> a;
        r.flow.villageBoxCancelled(null);
        r.flow.boxClosed();
        r.flow.screenChanged();
        r.flow.tick();
        r.flow.noUnitLeft();
        assertFalse(r.flow.dueInstead(a));
        assertFalse(r.flow.waited(a));
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
