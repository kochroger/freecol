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

import java.util.Locale;

import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;


/**
 * The original's end of turn and unit hand-over (build spec W5, spec delta
 * W5): the pauses between the player's last screen change and what comes
 * next, the turn indicator's colour, and which input the map takes
 * meanwhile.  Measured in the landfall clip (13 turn ends, 6 hand-overs)
 * and the landing-slow clip.
 *
 * <ul>
 *   <li><b>Automatic end</b> (W5a): {@link #END_TURN_MS} after the last
 *   change once no unit is left ({@link #noUnitLeft}), or
 *   {@link #END_TURN_VILLAGE_MS} after a cancelled village box.  The turn
 *   ends only if, when the timer fires, it is our turn, no box or screen
 *   is up and no unit can move or go to its destination.  Never FreeCol's
 *   {@code autoEndTurn}, which ends at once.  With the classic pref
 *   {@code endTurnPrompt} on, read at this idle decision, the Spielzugende
 *   mode follows instead ({@link #PROMPT_MS}, W17): the host draws it, and
 *   it waits for Enter, Space or a press on the word; the end forces its
 *   look ON and the request follows {@link #PROMPT_END_MS} later.</li>
 *   <li><b>Hand-over</b> (W5e): once the previous unit ran out of moves
 *   (or was skipped), the next unit of the original's unit cycle comes
 *   ({@link ClassicUnitCycle}, W5f; the controller's choice is put back),
 *   its panel block {@link #HANDOVER_MS} after the last change, and a view
 *   jump it needs at {@link #HANDOVER_JUMP_MS}.  Until then the map and
 *   the panel show the previous unit, as it was before its last move
 *   (W5b).  After a boarding the carrier comes
 *   {@link #BOARDING_HANDOVER_MS} after it ({@link #carrierChosen}, spec
 *   delta W18).</li>
 *   <li><b>Goto units</b> (W5f) move when the cycle reaches them, not in a
 *   batch first: the block, then the first step {@link #GOTO_HANDOVER_MS}
 *   later ({@link #gotoStages}); arrived with moves left the unit stays
 *   the active unit, blinking, with "Keine Befehle" (c6 #5123), else the
 *   next unit comes as after a last move.  A unit whose road, plowing or
 *   fortification was completed at the turn start gets a silent visit:
 *   the jump at the hand-over's block time, the completion
 *   {@link #VISIT_SHOW_MS} later, no block, no blink
 *   ({@link #visitStages}).</li>
 *   <li><b>Turn start</b> (W5e): the panel is wiped and the year changes
 *   in one paint ({@link #wipe}), when the controller first shows our new
 *   turn and no box is up (so FreeCol's turn-start messages come before
 *   the year flips, W5g); the block of the cycle's first unit (from its cursor) follows
 *   {@link #TURN_START_MS} later, or with a jump the jump at
 *   {@link #TURN_START_JUMP_MS} and the block one to two frames after it;
 *   a goto unit's first step {@link #GOTO_START_MS} after its block.</li>
 *   <li><b>Indicator</b> (W5c): the colour of the player whose turn it is
 *   ({@link #indicatorRgb}); the panel paints it, a 50-ms poll
 *   ({@link #tick}) repaints it when the current player changes.  It
 *   lights with the next player's colour in the paint just before our
 *   end-of-turn request goes out, and with ours for one tick just before
 *   our new turn's wipe ({@link #OWN_FLASH_MS}).</li>
 *   <li><b>Input</b> (W5d): the map's keys and clicks do nothing while it
 *   is not our turn and while a pause is pending
 *   ({@link #isInputBlocked}).</li>
 * </ul>
 *
 * <p>"The last change" is the last frame the player saw change: the map
 * reports its final draws, jumps, blink and cursor changes, the panel
 * every paint that changed a pixel outside the indicator, and a box its
 * close ({@link #screenChanged}).  A change while a pause is pending, and
 * before any of its stages ran, re-bases the pause on it; the active
 * unit's blink is held during a hand-over ({@link #holdsBlink}), so its
 * toggles never do.
 *
 * <p>The deadlines run on a {@link ClassicOneShot}; everything else is on
 * the event thread.  What the flow does to the game goes through its
 * {@link Host}, so the schedule runs headless on a test clock.
 */
final class ClassicTurnFlow {

    /** Automatic end of turn after the last change (landfall: 471-485 ms, 13 turns). */
    static final double END_TURN_MS = 485.0;

    /** Automatic end after a cancelled village box (LF#22717 -&gt; #22770). */
    static final double END_TURN_VILLAGE_MS = 756.0;

    /** Spielzugende mode entry after the last own change (landing-slow 35-36 frames). */
    static final double PROMPT_MS = 500.0;

    /** Spielzugende mode entry after a cancelled village box (#4136 -&gt; #4193). */
    static final double PROMPT_VILLAGE_MS = 813.0;

    /**
     * The end command in the Spielzugende mode: the indicator (and our
     * end request) this long after the forced-ON paint (landing-slow
     * #5500 -&gt; #5501, clip004 #2427 -&gt; #2428: one frame; #4957 -&gt; #4960).
     */
    static final double PROMPT_END_MS = 15.0;

    /** Hand-over: the next unit's panel block after the last change (499-514 ms). */
    static final double HANDOVER_MS = 500.0;

    /** Hand-over: the view jump to the next unit, if it needs one (271-285 ms). */
    static final double HANDOVER_JUMP_MS = 280.0;

    /**
     * Hand-over after a boarding: the carrier's block after the boarding
     * slide's final draw (clip007 #4272 -&gt; #4281, #6179 -&gt; #6188,
     * #6980 -&gt; #6989: 9 frames each; spec delta W18).
     */
    static final double BOARDING_HANDOVER_MS = 128.0;

    /**
     * Hand-over after a boarding with a view jump: the carrier's block
     * this long after the jump (I: never seen; one to two frames, as at
     * the turn start).
     */
    static final double BOARDING_BLOCK_MS = 21.0;

    /** Turn start: the first unit's block after the wipe, no jump (270-410 ms). */
    static final double TURN_START_MS = 300.0;

    /** Turn start: the jump to the first unit after the wipe (470-590 ms). */
    static final double TURN_START_JUMP_MS = 500.0;

    /** Turn start with a jump: the block after the jump (1-2 frames). */
    static final double TURN_START_BLOCK_MS = 21.0;

    /** Turn start: a goto unit's first step after its block (the goto gap, W2; LF #24166 -&gt; #24174: 114 ms). */
    static final double GOTO_START_MS = ClassicSlide.GOTO_GAP_MS;

    /**
     * Hand-over to a goto unit: its first step after its block (c6 U25
     * #5095/#5096: the same frame; U26 #5224 -&gt; #5226; U22 #4716 -&gt;
     * #4721: 0-71 ms).  Why it is shorter than at the turn start is not
     * known (I).
     */
    static final double GOTO_HANDOVER_MS = 28.0;

    /**
     * A visit: the completed letter (and road) this long after its jump
     * (c6 #4555 -&gt; #4556 and #4589 -&gt; #4590: 1 frame; #3447 -&gt;
     * #3450: 3 frames).
     */
    static final double VISIT_SHOW_MS = 15.0;

    /**
     * How recent the last change must be to count as the one an idle or a
     * hand-over pause runs from when it is armed (the controller's call
     * comes a few ms after the final draw); else the pause runs from now.
     */
    static final double CHANGE_WINDOW_MS = 200.0;

    /** A village box cancelled this recently decides the next idle pause. */
    static final double VILLAGE_WINDOW_MS = 250.0;

    /**
     * The poll wipes a turn the controller has not shown after this long
     * (a backstop: normally the controller's first view change wipes, after
     * its autosave and turn-start boxes).
     */
    static final double WIPE_GRACE_MS = 3000.0;

    /**
     * How long, from the server's answer, our end-of-turn request may wait
     * for the current player or the turn to change before it counts as
     * refused (the answer and the player change can come in either
     * order).  A request the controller did not send at all is refused at
     * once.
     */
    static final double ENDING_TIMEOUT_MS = 1500.0;

    /** {@link #endingTurn} without an end request to settle. */
    private static final int NO_TURN = Integer.MIN_VALUE;

    /** The indicator poll's interval (ms). */
    static final int POLL_MS = 50;

    /**
     * Our own colour in the indicator just before the wipe: one tick, so a
     * capture shows it in 1 or 2 frames (landfall: 1-2 frames in every
     * end, M1 acceptance F4).
     */
    static final double OWN_FLASH_MS = ClassicSlide.STEP_MS;

    /** Nanoseconds per millisecond. */
    private static final double NS_PER_MS = 1_000_000.0;


    /** What the flow drives and asks.  Event thread only. */
    interface Host {

        /** @return Whether it is our turn ({@code currentPlayerIsMyPlayer}). */
        boolean myTurn();

        /**
         * @return Whether a box, the first scene, a menu or a classic
         *     screen is up: nothing may end the turn or come up then.
         */
        boolean blocked();

        /** @return Whether the controller can still make a unit active. */
        boolean hasNextActiveUnit();

        /**
         * A hand-over to {@code unit} has started (W11): its tutorial tip
         * comes at the switch, before the unit, which comes up with the
         * tip's close (landfall 03 section 6).
         *
         * @param unit The unit coming.
         * @param baseNanos The pause's base (the last change).
         */
        default void unitComing(Unit unit, long baseNanos) {}

        /**
         * @param unit A unit.
         * @return Why the unit cycle has it due now, or null
         *     ({@link ClassicUnitCycle#kind}).
         */
        ClassicUnitCycle.Kind dueKind(Unit unit);

        /**
         * @param anchor The unit that has just finished, or null for the
         *     cycle's cursor (the turn start, and any choice with no unit
         *     that has just finished).
         * @return The cycle's next due unit, or null
         *     ({@link ClassicUnitCycle#next}).
         */
        Unit cycleNext(Unit anchor);

        /**
         * A unit is finished for this turn (its last move, Space, its goto
         * ran, its visit shown, gone): the cycle's cursor moves past it
         * ({@link ClassicUnitCycle#finished}).
         *
         * @param unit The unit.
         */
        void finished(Unit unit);

        /** @return Whether any unit is due in the cycle. */
        boolean anyDue();

        /**
         * The cycle brings another unit than the controller chose: its
         * choice goes back to the front of FreeCol's cycle
         * ({@code Player.putBackActiveUnit}).
         *
         * @param unit The controller's choice.
         */
        void putBack(Unit unit);

        /** @return The game's turn number, -1 without one. */
        int turnNumber();

        /** @return The classic pref {@code endTurnPrompt}, read now. */
        boolean promptPref();

        /** @return The map's active unit, or null. */
        Unit activeUnit();

        /**
         * @param unit A unit.
         * @return Whether the view would jump to show it (W4).
         */
        boolean wouldJump(Unit unit);

        /**
         * Jump the view to the unit now, the map and the minimap ring in
         * one paint; the panel's block stays as it is.
         *
         * @param unit The unit.
         */
        void jumpTo(Unit unit);

        /**
         * Make the unit the map's active unit and paint its block now, in
         * the one panel refresh the blink is timed from.
         *
         * @param unit The unit.
         */
        void activate(Unit unit);

        /**
         * The turn-start wipe: no active unit, the remembered block
         * forgotten, then the panel painted at once (new season line, no
         * block, no indicator).
         */
        void wipe();

        /** Paint the panel's turn indicator now. */
        void paintIndicator();

        /**
         * Ask the controller to end the turn ({@code endTurn(false)}).
         *
         * @return True if the end request went to the server and was
         *     answered; false if the controller kept the turn without
         *     asking (a goto or trade-route unit to look at, a panel up).
         */
        boolean endTurn();

        /**
         * Run one goto (or trade-route) unit's orders now, the unit the
         * active one ({@code InGameController.moveToDestination(Unit)});
         * the cycle counts it as run this turn.
         *
         * @param unit The unit.
         */
        void runGoto(Unit unit);

        /**
         * A silent visit (W5f): the view jumps to the unit if it needs to,
         * the map and the minimap ring painted at once; the panel's block
         * stays as it is, nothing blinks.
         *
         * @param unit The unit visited.
         */
        void visit(Unit unit);

        /**
         * The visit's completion: the unit's held letter and road go, its
         * cell and the neighbours (the road's spokes) painted at once.
         *
         * @param unit The unit visited.
         */
        void visitShown(Unit unit);

        /** Ask the controller for the next active unit. */
        void nextActiveUnit();

        /**
         * The Spielzugende mode begins (W17): the panel's tile mode, the
         * word and the map's square on the cursor tile, blinking.
         *
         * @param village The village whose box was cancelled just before
         *     (the cursor goes there, landing-slow #4193), or null for the
         *     last unit's tile.
         */
        void enterPrompt(Tile village);

        /**
         * The end command in the Spielzugende mode: the word, the square
         * and the minimap pixel ON at once and frozen (W17 item 7).
         */
        void freezePrompt();

        /** The Spielzugende mode ends without an end (a unit was activated). */
        void leavePrompt();

        /** @return The player whose turn it is, or null. */
        Player currentPlayer();

        /** @return The client's player, or null. */
        Player myPlayer();

        /** @return The player after the current one, or null. */
        Player nextPlayer();

        /**
         * Run {@code r} later on the event thread, after what is queued.
         *
         * @param r The task.
         */
        void post(Runnable r);

        /**
         * Asked just before our new turn's wipe: whether a turn-start box
         * must come first (the father choice, build spec D8a).  The host
         * then brings that box; the wipe waits for it as for any box.
         *
         * @return True if the wipe must wait.
         */
        default boolean holdTurnStart() {
            return false;
        }

        /**
         * Whether the Europe screen exists (showing, behind the map or
         * minimized) or was just asked for: the automatic end waits for
         * its close, also while windowed with the map in front (W13: a
         * click on the map must not let the turns run on behind Europe).
         *
         * @return True while it holds the end.
         */
        default boolean europeOpen() {
            return false;
        }

        /**
         * @return Whether a band is on the top strip (W13): the automatic
         *     end waits for it ({@link #bandEnded}).
         */
        default boolean bandUp() {
            return false;
        }

        /**
         * Asked at the automatic end when nothing can move (W13 guard,
         * spec R4 section 5.7): open Europe instead of ending the turn,
         * when nothing of ours is in the New World or at sea and Europe
         * was not shown in this turn yet.  The end comes after its close.
         *
         * @return True if Europe opens instead.
         */
        default boolean openEuropeInstead() {
            return false;
        }

        /**
         * Our new turn begins, before its first unit is chosen: the unit
         * cycle's turn (no goto has run, the visits found).
         */
        default void turnBegins() {
        }

        /** Our end-of-turn request goes out: the unit cycle's snapshot. */
        default void turnEnding() {
        }

        /** Our end-of-turn request was refused: the turn goes on. */
        default void endRefused() {
        }
    }

    /** The pauses. */
    enum Kind { END_TURN, PROMPT, PROMPT_END, HANDOVER, TURN_START }

    /** What a pause does at one of its moments. */
    enum Action { END, PROMPT, END_NOW, JUMP, ACTIVATE, GOTO, VISIT, SHOW }

    /** One moment of a pause: {@code ms} after its base. */
    static final class Stage {

        final double ms;
        final Action action;

        Stage(double ms, Action action) {
            this.ms = ms;
            this.action = action;
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return action + "@" + Math.round(ms);
        }
    }

    /** A pending pause. */
    static final class Pending {

        final Kind kind;

        /** The unit it brings up (HANDOVER, TURN_START), else null. */
        final Unit unit;

        final Stage[] stages;

        /** Its base on the clock; 0 while a turn start waits for the wipe. */
        long base;

        /** The next stage to run. */
        int next = 0;

        /** The next stage came due while a box was up: it runs at the close. */
        boolean held = false;

        Pending(Kind kind, Unit unit, long base, Stage... stages) {
            this.kind = kind;
            this.unit = unit;
            this.base = base;
            this.stages = stages;
        }

        /** @return Whether a stage ran (no re-basing after that). */
        boolean started() {
            return this.next > 0;
        }

        /** @return When the next stage is due. */
        long dueNanos() {
            return this.base + nanos(this.stages[this.next].ms);
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder(kind.toString());
            for (Stage s : stages) sb.append(' ').append(s);
            return sb.toString();
        }
    }


    private final Host host;

    private final ClassicSlide.Clock clock;

    /** The deadline of the pending pause's next stage. */
    private final ClassicOneShot timer;

    /** The pending pause, or null. */
    private Pending pending = null;

    /** When the player last saw the screen change (0: never). */
    private long lastChange = 0L;

    /** When a village box was last cancelled (0: never). */
    private long villageCancel = 0L;

    /** The tile of the village whose box was last cancelled, or null. */
    private Tile villageTile = null;

    /** The village tile the armed idle pause's Spielzugende mode shows, or null. */
    private Tile idleVillage = null;

    /** What ended the Spielzugende mode (for the recorder), while its end is pending. */
    private String promptEndWhy = null;

    /** Our turn has been wiped and shown (false from our end until the next wipe). */
    private boolean turnStarted = true;

    /** The turn whose first unit was handed control (the start's is the session's). */
    private int activatedTurn;

    /** The unit the view opened with from the cycle's cursor, until the next choice. */
    private Unit heldUp = null;

    /** When the last wipe was painted. */
    private long wipeNanos = 0L;

    /** Since when the poll saw our turn not yet wiped (0: not). */
    private long unwipedSince = 0L;

    /** No unit is left and the idle decision is still to come. */
    private boolean idleWanted = false;

    /** The Spielzugende mode is on (W17): Enter or Space ends the turn. */
    private boolean prompt = false;

    /**
     * Our end-of-turn request is out and the current player has not
     * changed yet: the indicator shows the next player.
     */
    private boolean ending = false;

    /** When {@link #ending} began. */
    private long endingSince = 0L;

    /**
     * The turn our last end-of-turn request was made in, until the flow
     * sees it go through ({@link #settleEnding}); kept after a refusal, so
     * that a turn change that comes late still ends the old turn.
     * {@link #NO_TURN} for none.
     */
    private int endingTurn = NO_TURN;

    /** The flow is stopped for good ({@link #dispose}). */
    private boolean disposed = false;

    /** Goto orders run, the controller's view changes are dropped ({@link #ignoring}). */
    private int gotoRuns = 0;

    /** The unit whose goto runs now, or null ({@link #gotoUnit}). */
    private Unit gotoUnit = null;

    /**
     * How often the controller chose a unit or reported none
     * ({@link #unitChosen}, {@link #noUnitLeft}, {@link #dueInstead}):
     * {@link #nextUnitOrIdle} sees whether its call brought anything.
     */
    private int choices = 0;

    /** The turn the unit cycle's turn was begun for ({@link #beginTurn}). */
    private int cycleTurn;

    /** The indicator colour of the panel's last paint (-1 none). */
    private int shownIndicator = -1;

    /** Our colour's tick just before the wipe ({@link #flashOwnColour}). */
    private boolean ownFlash = false;

    /** The state for the recorder's probe (another thread). */
    private volatile String probeState = "idle";


    /**
     * @param host What the flow drives.
     * @param clock The clock.
     * @param poster Runs a due stage (in the game: on the event thread).
     * @param threaded Whether the timer waits on a thread of its own
     *     (false for tests, which call {@link #runDue}).
     */
    ClassicTurnFlow(Host host, ClassicSlide.Clock clock,
                    java.util.function.Consumer<Runnable> poster,
                    boolean threaded) {
        this.host = host;
        this.clock = clock;
        this.timer = new ClassicOneShot(clock, poster, threaded, "ClassicTurnFlow");
        this.activatedTurn = host.turnNumber();
        this.cycleTurn = this.activatedTurn;
    }


    // Schedule arithmetic

    /** @return {@code ms} in nanoseconds. */
    static long nanos(double ms) {
        return Math.round(ms * NS_PER_MS);
    }

    /**
     * The automatic end's (or the Spielzugende mode's) pause.
     *
     * @param prompt The classic pref {@code endTurnPrompt}.
     * @param village A village box was just cancelled.
     * @return The pause in ms.
     */
    static double idleMs(boolean prompt, boolean village) {
        return (prompt) ? ((village) ? PROMPT_VILLAGE_MS : PROMPT_MS)
            : ((village) ? END_TURN_VILLAGE_MS : END_TURN_MS);
    }

    /**
     * The stages of a hand-over to a unit.
     *
     * @param jump The unit needs a view jump.
     * @return Jump at 280 ms (if needed), block at 500 ms.
     */
    static Stage[] handOverStages(boolean jump) {
        return (jump)
            ? new Stage[] { new Stage(HANDOVER_JUMP_MS, Action.JUMP),
                            new Stage(HANDOVER_MS, Action.ACTIVATE) }
            : new Stage[] { new Stage(HANDOVER_MS, Action.ACTIVATE) };
    }

    /**
     * The stages of the hand-over to a carrier after a unit boarded it
     * (spec delta W18).
     *
     * @param jump The carrier needs a view jump.
     * @return Block at 128 ms, or jump at 128 ms and block 21 ms later.
     */
    static Stage[] boardingStages(boolean jump) {
        return (jump)
            ? new Stage[] { new Stage(BOARDING_HANDOVER_MS, Action.JUMP),
                            new Stage(BOARDING_HANDOVER_MS + BOARDING_BLOCK_MS,
                                      Action.ACTIVATE) }
            : new Stage[] { new Stage(BOARDING_HANDOVER_MS, Action.ACTIVATE) };
    }

    /**
     * The stages of a hand-over to a goto unit (W5f, c6 U22, U25, U26).
     *
     * @param jump The unit needs a view jump.
     * @return Jump at 280 ms (if needed), block at 500 ms, the first step
     *     28 ms after the block.
     */
    static Stage[] gotoStages(boolean jump) {
        final java.util.List<Stage> s = new java.util.ArrayList<>(3);
        if (jump) s.add(new Stage(HANDOVER_JUMP_MS, Action.JUMP));
        s.add(new Stage(HANDOVER_MS, Action.ACTIVATE));
        s.add(new Stage(HANDOVER_MS + GOTO_HANDOVER_MS, Action.GOTO));
        return s.toArray(new Stage[0]);
    }

    /**
     * The stages of a silent visit (W5f, c6 #3447/#3450, #4555/#4556,
     * #4589/#4590: the jump 471-542 ms after the last change).
     *
     * @return The jump (if needed) at 500 ms, the completion 15 ms later.
     */
    static Stage[] visitStages() {
        return new Stage[] { new Stage(HANDOVER_MS, Action.VISIT),
                             new Stage(HANDOVER_MS + VISIT_SHOW_MS, Action.SHOW) };
    }

    /**
     * The stages of a hand-over to a unit the cycle has due.
     *
     * @param kind Why it is due (null as ORDERS).
     * @param jump The unit needs a view jump.
     * @return {@link #handOverStages}, {@link #gotoStages} or
     *     {@link #visitStages}.
     */
    static Stage[] handOverStages(ClassicUnitCycle.Kind kind, boolean jump) {
        if (kind == ClassicUnitCycle.Kind.GOTO) return gotoStages(jump);
        if (kind == ClassicUnitCycle.Kind.VISIT) return visitStages();
        return handOverStages(jump);
    }

    /**
     * The stages of a turn start.
     *
     * @param jump The first unit needs a view jump.
     * @param gotos It is a unit with a destination: its goto orders run
     *     after its block.
     * @return Block at 300 ms, or jump at 500 ms and block 21 ms later;
     *     then the goto orders 100 ms after the block.
     */
    static Stage[] turnStartStages(boolean jump, boolean gotos) {
        return turnStartStages(jump, (gotos) ? ClassicUnitCycle.Kind.GOTO
                               : ClassicUnitCycle.Kind.ORDERS);
    }

    /**
     * The stages of a turn start for the head of the cycle.
     *
     * @param jump The first unit needs a view jump.
     * @param kind Why it is due (null as ORDERS).
     * @return Block at 300 ms, or jump at 500 ms and block 21 ms later; a
     *     goto unit's first step 100 ms after the block (LF 1502); a visit
     *     at the block's time (I), its completion 15 ms later.
     */
    static Stage[] turnStartStages(boolean jump, ClassicUnitCycle.Kind kind) {
        if (kind == ClassicUnitCycle.Kind.VISIT) {
            final double at = (jump) ? TURN_START_JUMP_MS : TURN_START_MS;
            return new Stage[] { new Stage(at, Action.VISIT),
                                 new Stage(at + VISIT_SHOW_MS, Action.SHOW) };
        }
        final double block = (jump) ? TURN_START_JUMP_MS + TURN_START_BLOCK_MS
            : TURN_START_MS;
        final java.util.List<Stage> s = new java.util.ArrayList<>(3);
        if (jump) s.add(new Stage(TURN_START_JUMP_MS, Action.JUMP));
        s.add(new Stage(block, Action.ACTIVATE));
        if (kind == ClassicUnitCycle.Kind.GOTO) {
            s.add(new Stage(block + GOTO_START_MS, Action.GOTO));
        }
        return s.toArray(new Stage[0]);
    }

    /**
     * Whether a unit is done for this turn: its last move is done, it was
     * skipped ("Keine Befehle", Space: the next unit comes as after a last
     * move, clip007 #1397 -&gt; #1449, I), or it is gone.
     *
     * @param u The unit, or null.
     * @return True if it ran out of moves, was skipped or no longer exists.
     */
    static boolean ranOut(Unit u) {
        return u != null && (u.isDisposed() || !u.hasTile() || u.getMovesLeft() <= 0
            || u.getState() == Unit.UnitState.SKIPPED);
    }


    // The game's calls (event thread)

    /**
     * The player saw the screen change now.  A pending pause that has not
     * started yet runs from here.
     */
    void screenChanged() {
        if (this.disposed) return;
        final long now = this.clock.now();
        this.lastChange = now;
        final Pending p = this.pending;
        if (p != null && !p.started() && !p.held && p.base != 0L
            && p.kind != Kind.TURN_START) {
            p.base = now;
            schedule(p, "rebase");
        }
    }

    /**
     * A box (or a menu) closed: its close is a change, and an idle end it
     * kept from firing is armed again from now (W5a).
     */
    void boxClosed() {
        if (this.disposed) return;
        screenChanged();
        catchUp();
    }

    /**
     * A band on the top strip ended (W13): the idle end it held is armed
     * from now.  Unlike a box's close it does not re-base a pending
     * hand-over: the next unit comes under the band as usual (clip008
     * #44199 and #44215, R4 verifier item 5).
     */
    void bandEnded() {
        if (this.disposed) return;
        this.lastChange = this.clock.now();
        catchUp();
    }

    /**
     * A village box was cancelled ("Handlung abbrechen").
     *
     * @param village The village's tile, or null.
     */
    void villageBoxCancelled(Tile village) {
        if (this.disposed) return;
        this.villageCancel = this.clock.now();
        this.villageTile = village;
        ClassicFrameRecorder.event("endturn-village", "cancel"
            + ((village == null) ? "" : " at=" + village.getX() + "," + village.getY()));
    }

    /**
     * The controller has no unit left to show (its end-of-turn view or its
     * fallback tile; the player's own tile selections come here too).  On
     * our turn with nothing left to move, the idle pause is armed.
     */
    void noUnitLeft() {
        if (this.disposed) return;
        this.choices++;
        settleEnding();   // our end may have gone through unseen
        final Pending p = this.pending;
        if (p != null && p.kind == Kind.PROMPT_END) return;   // the end is out
        cancel("no-unit");
        this.idleWanted = this.host.myTurn() && !this.ending;
        if (!this.idleWanted) return;   // our end's own view change
        if (!this.turnStarted || this.host.turnNumber() != this.activatedTurn) {
            this.activatedTurn = this.host.turnNumber();
            beginTurn();
            if (!wipe()) return;   // a box is up: the poll wipes and arms later
        }
        armIdle(lastChangeBase());
    }

    /**
     * The controller has no unit left to make active (its end view or its
     * fallback tile), but the unit cycle has one due: a goto unit, a
     * visit, or a goto unit that ran and is still active with moves.  It
     * comes as a hand-over from the previous unit, or as the turn start's
     * first unit (from the cycle's cursor); the map keeps the previous unit meanwhile (W5b).
     * FreeCol's controller no longer brings goto units in the Classic UI
     * (its batch is off, W5f).
     *
     * @param previous The map's active unit now, or null.
     * @return True if a due unit comes (the caller shows no end view);
     *     false for the end view and {@link #noUnitLeft}.
     */
    boolean dueInstead(Unit previous) {
        if (this.disposed) return false;
        settleEnding();
        final Pending p = this.pending;
        if (!this.host.myTurn() || this.ending
            || (p != null && p.kind == Kind.PROMPT_END)) return false;
        final int turn = this.host.turnNumber();
        final boolean newTurn = !this.turnStarted || turn != this.activatedTurn;
        if (newTurn) {
            beginTurn();
        } else if (ranOut(previous)) {
            this.host.finished(previous);   // also the turn's last unit
        }
        if (!this.host.anyDue()) return false;
        this.choices++;
        if (p != null && (p.kind == Kind.TURN_START || p.kind == Kind.HANDOVER)) {
            return true;   // a due unit is on its way already
        }
        cancel("due");
        leavePrompt();
        this.idleWanted = false;
        if (newTurn) {
            this.activatedTurn = turn;
            startTurn(cycleChoice(null, null, "turn start, no candidate"));
            return true;
        }
        bring(cycleChoice(null, previous, "no candidate"), previous, lastChangeBase());
        return true;
    }

    /**
     * The controller (or the player) chose {@code unit} as the active unit.
     *
     * @param unit The unit, or null.
     * @param previous The map's active unit now, or null.
     * @return True if the flow takes it over: the unit comes up later (a
     *     hand-over, a turn start), or not at all (not our turn); false to
     *     make it active at once.
     */
    boolean unitChosen(Unit unit, Unit previous) {
        return unitChosen(unit, previous, false, false);
    }

    /**
     * The player clicked an own unit: it is not replaced by the unit
     * cycle's choice (as {@link #unitChosen(Unit, Unit)} otherwise).
     *
     * @param unit The unit.
     * @param previous The map's active unit now, or null.
     * @return As {@link #unitChosen(Unit, Unit)}.
     */
    boolean unitClicked(Unit unit, Unit previous) {
        return unitChosen(unit, previous, false, true);
    }

    /**
     * A unit has boarded a carrier that can still move: the carrier comes
     * next, {@link #BOARDING_HANDOVER_MS} after the boarding's final draw
     * instead of the hand-over's {@link #HANDOVER_MS} (spec delta W18).
     *
     * @param carrier The carrier.
     * @param boarded The unit that boarded it (the map's active unit).
     * @return As {@link #unitChosen(Unit, Unit)}.
     */
    boolean carrierChosen(Unit carrier, Unit boarded) {
        return unitChosen(carrier, boarded, true, true);
    }

    /**
     * {@link #unitChosen(Unit, Unit)}, after a boarding or not.  At the
     * turn start and at a hand-over the unit cycle's choice comes instead
     * of the controller's ({@link #cycleChoice}), unless the unit is the
     * player's or the boarding's own choice.
     *
     * @param unit The unit, or null.
     * @param previous The map's active unit now, or null.
     * @param boarding The previous unit has just boarded {@code unit}.
     * @param fixed The unit is not the controller's choice (a click, the
     *     boarding's carrier): no cycle choice at a hand-over.
     * @return True if the flow takes it over.
     */
    private boolean unitChosen(Unit unit, Unit previous, boolean boarding,
                               boolean fixed) {
        if (this.disposed) return false;
        this.choices++;
        // Our end may have gone through without the poll seeing another
        // player (the event thread busy with queued native slides): then
        // this is our new turn's first unit, with its wipe.
        settleEnding();
        final Pending p = this.pending;
        if (unit == null) {
            if (p == null || p.kind != Kind.PROMPT_END) cancel("no-unit");
            return false;
        }
        if (!this.host.myTurn() || this.ending) {
            // The controller re-selects after our end (doEndTurn's goto
            // pass): no unit comes up until our next turn.
            if (ClassicFrameRecorder.on()) {
                ClassicFrameRecorder.event("handover", "ignored " + unit.getId()
                    + " not our turn");
            }
            return true;
        }
        final Unit held = this.heldUp;
        this.heldUp = null;
        if (p != null && (p.kind == Kind.TURN_START
                || (p.kind == Kind.HANDOVER
                    && (p.unit == unit || unit == previous
                        || (!fixed && ranOut(previous)))))) {
            // The turn start brings its unit (and then asks again); a
            // hand-over keeps its unit, also when the shown one is
            // re-selected meanwhile, or when the controller chooses again
            // (its choice goes back: the cycle chose already).
            if (!fixed && unit != p.unit && unit != previous) this.host.putBack(unit);
            return true;
        }
        cancel("unit");
        leavePrompt();
        this.idleWanted = false;
        final int turn = this.host.turnNumber();
        if (!this.turnStarted || turn != this.activatedTurn) {
            this.activatedTurn = turn;
            beginTurn();
            startTurn(cycleChoice(unit, null, "turn start"));
            return true;
        }
        if (held != null && !fixed && p == null && previous == held && unit != held
            && !ranOut(held) && held.isCandidateForNextActiveUnit()) {
            // The view opened with the cycle's unit from its cursor (a
            // load, I2): FreeCol's own startup choice that follows goes
            // back (its iterator, which brought the ship again).  Only
            // while that unit can still take orders: after S, F or a goto
            // order that kept its moves (FreeCol often makes no startup
            // choice) this is the choice after those orders, below.
            ClassicFrameRecorder.event("cycle", "kept " + id(held) + ", controller="
                + id(unit) + " (load)");
            this.host.putBack(unit);
            return true;
        }
        if (unit == previous) return false;   // the re-selection after a move
        if (!fixed && previous == null) {
            // No unit up in the middle of the turn (back from a colony or
            // Europe, after a box or the terrain view): the cycle's choice
            // from its cursor, not the controller's (its own order, which
            // brought the ship again), at once as the controller's.
            final Unit target = cycleChoice(unit, null, "no unit up");
            if (target == unit) return false;
            bringNow(target);
            return true;
        }
        if (ranOut(previous)) {
            this.host.finished(previous);
            if (boarding) {
                ClassicFrameRecorder.event("handover", "after boarding: carrier "
                    + unit.getId() + " (boarded " + previous.getId() + ")");
                start(new Pending(Kind.HANDOVER, unit, lastChangeBase(),
                        boardingStages(this.host.wouldJump(unit))));
                return true;
            }
            if (fixed) {   // a click: that unit, as a plain hand-over
                start(new Pending(Kind.HANDOVER, unit, lastChangeBase(),
                        handOverStages(this.host.wouldJump(unit))));
            } else {
                startHandOver(cycleChoice(unit, previous, "hand-over"), lastChangeBase());
            }
            return true;
        }
        if (!fixed && previous != null && previous.hasTile()
            && !previous.isCandidateForNextActiveUnit()) {
            // The previous unit got orders and kept its moves (a goto
            // order that stopped early, F, S): the controller's choice
            // comes at once, so the cycle's does.
            final Unit target = cycleChoice(unit, previous, "orders given");
            if (target == unit) return false;
            bringNow(target);
            return true;
        }
        return false;
    }

    /**
     * Bring the cycle's next unit: as a hand-over (its pause from the last
     * change) after a unit that ran out, else at once ({@link #bringNow}),
     * as FreeCol's controller brings its choice after orders that keep
     * the moves.  (The previous unit's blink is held during any hand-over,
     * {@link #holdsBlink}; before that, a goto order that stopped early
     * left a blinking unit whose toggles re-based the pause for good.)
     *
     * @param next The unit.
     * @param previous The unit that has just finished, or null.
     * @param base When the last change was.
     */
    private void bring(Unit next, Unit previous, long base) {
        if (next == null) return;
        if (previous != null && previous == this.host.activeUnit() && !ranOut(previous)) {
            bringNow(next);
            return;
        }
        startHandOver(next, base);
    }

    /**
     * Bring a unit of the cycle at once, with no hand-over pause (W; a
     * unit that got orders and kept its moves): an ORDERS unit is made
     * active now, a goto unit runs {@link #GOTO_HANDOVER_MS} after its
     * block, a visit shows its completion {@link #VISIT_SHOW_MS} after its
     * jump.  A goto unit that is the previous unit itself (its goto order
     * stopped early, at a region to discover) runs again once.
     *
     * @param next The unit.
     */
    private void bringNow(Unit next) {
        final ClassicUnitCycle.Kind k = this.host.dueKind(next);
        final long now = this.clock.now();
        if (k == ClassicUnitCycle.Kind.GOTO) {
            start(new Pending(Kind.HANDOVER, next, now, new Stage(0.0, Action.ACTIVATE),
                              new Stage(GOTO_HANDOVER_MS, Action.GOTO)));
        } else if (k == ClassicUnitCycle.Kind.VISIT) {
            start(new Pending(Kind.HANDOVER, next, now, new Stage(0.0, Action.VISIT),
                              new Stage(VISIT_SHOW_MS, Action.SHOW)));
        } else {
            this.host.activate(next);
        }
    }

    /**
     * The game view opened with the unit cycle's unit from its cursor
     * (ClassicGUI.firstUnit, a load, I2): the controller's next choice of
     * another unit while that one is still up goes back, once.
     *
     * @param unit The unit up.
     */
    void cycleUnitUp(Unit unit) {
        this.heldUp = unit;
    }

    /**
     * W (wait, I: never recorded): the next unit of the cycle after the
     * waiting one comes at once (a goto unit with its steps, a visit with
     * its completion); the waiting unit comes again after the wrap.
     * Instead of FreeCol's {@code waitUnit}, whose pick follows its tile
     * order.
     *
     * @param waiting The active unit.
     * @return True if the flow took the key (false while the input is
     *     blocked).
     */
    boolean waited(Unit waiting) {
        if (this.disposed || isInputBlocked()) return false;
        final Unit next = this.host.cycleNext(waiting);
        if (ClassicFrameRecorder.on()) {
            ClassicFrameRecorder.event("cycle", "wait " + id(waiting)
                + " next=" + id(next) + " kind=" + this.host.dueKind(next));
        }
        if (next == null || next == waiting) return true;
        leavePrompt();
        bringNow(next);
        return true;
    }

    /**
     * The unit the cycle brings instead of the controller's choice
     * {@code chosen}: the next due unit after {@code anchor} (without one, from the
     * cycle's cursor: the turn start, no unit up).  The controller's choice is put back to the front
     * of FreeCol's cycle when another unit comes.
     *
     * @param chosen The controller's choice, or null.
     * @param anchor The unit that has just finished, or null.
     * @param why What hands over (for the recorder).
     * @return The unit to bring.
     */
    private Unit cycleChoice(Unit chosen, Unit anchor, String why) {
        Unit target = this.host.cycleNext(anchor);
        if (target == null) {
            target = chosen;
        } else if (chosen != null && target != chosen) {
            this.host.putBack(chosen);
        }
        if (ClassicFrameRecorder.on()) {
            ClassicFrameRecorder.event("cycle", "next=" + id(target)
                + " kind=" + this.host.dueKind(target) + " anchor=" + id(anchor)
                + " controller=" + id(chosen) + " (" + why + ")");
        }
        return target;
    }

    /**
     * Start the turn start for the cycle's first unit, with the wipe now if
     * no box is up (else at the box's close).
     *
     * @param target The unit.
     */
    private void startTurn(Unit target) {
        final boolean wiped = wipe();
        start(new Pending(Kind.TURN_START, target, (wiped) ? this.wipeNanos : 0L,
                turnStartStages(this.host.wouldJump(target), this.host.dueKind(target))));
    }

    /**
     * Start the hand-over to a unit the cycle has due, by its kind.
     *
     * @param target The unit.
     * @param base When the last change was.
     */
    private void startHandOver(Unit target, long base) {
        start(new Pending(Kind.HANDOVER, target, base,
                handOverStages(this.host.dueKind(target), this.host.wouldJump(target))));
    }

    /**
     * After a goto run, a visit or a unit gone: the next due unit of the
     * cycle as a hand-over from {@code anchor}, 500 ms after the last
     * change, also after a visit that came at once (I: the clip's visits
     * are 0.43-0.54 s apart, c6 #4556, #4589, #4619; the unit still up
     * meanwhile does not blink, {@link #holdsBlink}); with none, the
     * controller's end view and the idle end ({@link #nextUnitOrIdle}).
     *
     * @param anchor The unit that has just finished.
     * @param why What hands over (for the recorder).
     */
    private void afterUnit(Unit anchor, String why) {
        if (this.disposed || !this.host.myTurn() || this.ending) return;
        this.host.finished(anchor);   // not after our end: it settled the cursor
        if (!this.turnStarted) return;
        final Unit next = this.host.cycleNext(anchor);
        if (next != null) {
            if (ClassicFrameRecorder.on()) {
                ClassicFrameRecorder.event("cycle", "next=" + id(next) + " kind="
                    + this.host.dueKind(next) + " anchor=" + id(anchor) + " (" + why + ")");
            }
            startHandOver(next, lastChangeBase());
            return;
        }
        nextUnitOrIdle(why);
    }

    /**
     * Begin the unit cycle's turn once per turn: before the first unit of
     * our new turn is chosen.
     */
    private void beginTurn() {
        final int turn = this.host.turnNumber();
        if (turn == this.cycleTurn) return;
        this.cycleTurn = turn;
        this.host.turnBegins();
    }

    /** A unit's id for the recorder ("-" for none). */
    private static String id(Unit u) {
        return (u == null) ? "-" : u.getId();
    }

    /**
     * End the turn now: the timer, Enter or Space (with no unit, or in the
     * Spielzugende mode), a press on the word.  The indicator lights with
     * the next player's colour first, in its own paint.  In the
     * Spielzugende mode the word, the square and the minimap pixel are
     * first forced ON and frozen, and the indicator and the request follow
     * {@link #PROMPT_END_MS} later (W17 item 7).
     *
     * @param why What ends it (for the recorder).
     */
    void endTurnNow(String why) {
        if (this.disposed) return;
        if (this.prompt) {
            this.prompt = false;
            this.promptEndWhy = why;
            cancel("prompt-end");
            ClassicFrameRecorder.event("endturn-prompt", "end " + why);
            final long command = this.clock.now();   // the paints take time
            this.host.freezePrompt();
            start(new Pending(Kind.PROMPT_END, null, command,
                    new Stage(PROMPT_END_MS, Action.END_NOW)));
            return;
        }
        cancel("end");
        this.prompt = false;
        this.idleWanted = false;
        ClassicFrameRecorder.event("end-turn", why);
        this.ending = true;
        this.endingSince = this.clock.now();
        this.endingTurn = this.host.turnNumber();
        // The state the visits of our next turn start compare with (W5f).
        this.host.turnEnding();
        this.host.paintIndicator();
        final boolean sent = this.host.endTurn();
        // The wait for the player change runs from the server's answer: a
        // slow server's answer must not count as a refusal and let a second
        // end request out (FINAL "Open" item 9).
        this.endingSince = this.clock.now();
        if (!sent && this.ending && this.endingTurn == this.host.turnNumber()
            && this.host.myTurn()) {
            // The controller did not ask the server at all (a goto or
            // trade-route unit with something to show, a panel up): no
            // need to wait for a player change that cannot come.
            refuseEnd("not sent");
        }
        settleEnding();
        updateProbe();
    }

    /**
     * The 50-ms poll (W5c): follow the current player with the
     * indicator, notice a turn that ended or started without the flow,
     * and catch up with what a box or a screen held up.
     */
    void tick() {
        if (this.disposed) return;
        settleEnding();
        final boolean mine = this.host.myTurn();
        final long now = this.clock.now();
        if (!mine) {
            this.unwipedSince = 0L;
            if (this.turnStarted && !this.ending) {
                cancel("not-our-turn");
                leavePrompt();
                this.idleWanted = false;
                waiting();
            }
        } else if (!this.turnStarted && this.host.blocked()) {
            this.unwipedSince = 0L;   // the grace counts unblocked time only
        } else if (!this.turnStarted && this.pending == null && !this.idleWanted) {
            // Our turn, but the controller has not shown it yet.
            if (this.unwipedSince == 0L) {
                this.unwipedSince = now;
            } else if (now - this.unwipedSince >= nanos(WIPE_GRACE_MS)) {
                ClassicFrameRecorder.event("turn-wipe", "late");
                if (wipe()) this.host.nextActiveUnit();
            }
        }
        catchUp();
        if (indicatorRgb() != this.shownIndicator) this.host.paintIndicator();
    }

    /**
     * Our end-of-turn request is settled once the current player or the
     * turn number has changed (the server's answer and the player change
     * come in either order; with native slides queued on the event thread
     * the whole AI phase can pass between two polls, and our next turn is
     * then current again with a new number), or it counts as refused after
     * {@link #ENDING_TIMEOUT_MS}.  A refusal keeps the request's turn, so
     * a turn change that comes later still ends the old turn here, and it
     * leaves a live state ({@link #recoverRefused}).  Called first by
     * every entry that reads {@link #ending}.
     */
    private void settleEnding() {
        if (this.endingTurn == NO_TURN) return;
        if (!this.host.myTurn() || this.host.turnNumber() != this.endingTurn) {
            final boolean late = !this.ending;
            this.ending = false;
            this.endingTurn = NO_TURN;
            if (late) ClassicFrameRecorder.event("end-turn", "settled late");
            cancel("end-settled");
            leavePrompt();
            this.idleWanted = false;
            waiting();
            if (!late) this.host.paintIndicator();   // the prediction ends
        } else if (this.ending && this.clock.now() - this.endingSince
                   >= nanos(ENDING_TIMEOUT_MS)) {
            refuseEnd("timeout");
        }
    }

    /**
     * Our end request counts as refused: the input is free again, the
     * indicator's prediction goes, and the recovery is queued
     * ({@link #recoverRefused}).  The request's turn is kept
     * ({@link #settleEnding}).
     *
     * @param why Why (for the recorder).
     */
    private void refuseEnd(String why) {
        this.ending = false;
        ClassicFrameRecorder.event("end-turn", "refused " + why);
        this.host.endRefused();
        this.host.paintIndicator();
        this.host.post(this::recoverRefused);
    }

    /**
     * After a refused end, behind what is queued (a turn change that was
     * queued settles first): unless a unit or a pause came meanwhile, ask
     * the controller for the next unit, so the flow never sits with no
     * unit, no pause and nothing scheduled.  It brings the unit it
     * re-selected while the end was out (doEndTurn's goto and trade-route
     * pass), or its end view arms the idle end again.
     */
    private void recoverRefused() {
        if (this.disposed) return;
        settleEnding();
        if (this.pending != null || this.ending || !this.turnStarted
            || this.prompt || this.gotoRuns > 0 || !this.host.myTurn()
            || this.host.activeUnit() != null) return;
        ClassicFrameRecorder.event("end-turn", "refused: next unit");
        this.host.nextActiveUnit();
        updateProbe();
    }

    /**
     * Do what a box, a menu or a screen held up, once none is up: the
     * wipe a waiting turn start needs, the wipe and idle end of a turn
     * start with no unit, an idle end that could not fire.
     */
    private void catchUp() {
        if (!this.host.myTurn() || this.ending || this.host.blocked()) return;
        final Pending p = this.pending;
        if (p != null && p.held) {
            fire(p);   // the stage a box held up
            return;
        }
        if (!this.turnStarted) {
            if (p != null && p.base == 0L) {
                if (wipe()) {
                    // The view may have moved while the start waited (the
                    // arrival chain's jump to a ship back from Europe,
                    // clip008 #26208): its jump is decided now.
                    final Pending q = (p.kind == Kind.TURN_START && !p.started())
                        ? new Pending(Kind.TURN_START, p.unit, this.wipeNanos,
                            turnStartStages(this.host.wouldJump(p.unit),
                                            this.host.dueKind(p.unit)))
                        : p;
                    q.base = this.wipeNanos;
                    this.pending = q;
                    schedule(q, "wiped");
                }
            } else if (this.idleWanted && p == null) {
                if (wipe()) armIdle(this.wipeNanos);
            }
            return;
        }
        if (this.idleWanted && p == null && !this.prompt
            && this.host.activeUnit() == null && !endHeld()) {
            armIdle(lastChangeBase());
        }
    }

    /**
     * Whether a band or the Europe screen holds the automatic end now
     * (W13); the band's end ({@link #bandEnded}) or Europe's close
     * ({@link #boxClosed}) arms it again.
     *
     * @return True if held.
     */
    private boolean endHeld() {
        return this.host.bandUp() || this.host.europeOpen();
    }

    /**
     * The colour of the turn indicator now (W5c): the current player's
     * while it is not our turn, the next player's while our end-of-turn
     * request goes out, and none while our turn is shown.
     *
     * <p>Our new turn before its wipe: ours while its turn-start boxes are
     * up, and for one tick just before the wipe ({@link #OWN_FLASH_MS});
     * until then the colour shown stays.  FreeCol autosaves and prepares
     * its turn report between the player change and the controller's
     * first view change (0.16-0.41 s), where our colour used to light; the
     * original shows it for 1-2 frames (landfall, every end; M1 acceptance
     * F4).
     *
     * @return The RGB, or -1 for none.
     */
    int indicatorRgb() {
        final Player me = this.host.myPlayer();
        final Player cur = this.host.currentPlayer();
        if (me == null || cur == null) return -1;
        if (cur == me) {
            if (this.ending) {
                final Player n = this.host.nextPlayer();
                return (n == null || n == me) ? -1 : ClassicHud.indicatorRgb(n);
            }
            if (this.turnStarted) return -1;
            final int own = ClassicHud.indicatorRgb(me);
            if (this.ownFlash || this.shownIndicator < 0 || this.host.blocked()) {
                return own;
            }
            return this.shownIndicator;
        }
        return ClassicHud.indicatorRgb(cur);
    }

    /**
     * The panel painted the indicator in {@code rgb}.
     *
     * @param rgb The colour, -1 for none.
     */
    void indicatorShown(int rgb) {
        if (rgb == this.shownIndicator) return;
        this.shownIndicator = rgb;
        if (ClassicFrameRecorder.on()) {
            final Player cur = this.host.currentPlayer();
            ClassicFrameRecorder.event("indicator", ((rgb < 0) ? "off"
                    : String.format(Locale.ROOT, "%06X", rgb))
                + " cur=" + ((cur == null) ? "-" : cur.getNationId())
                + ((this.ending) ? " predicted" : ""));
        }
    }

    /**
     * Whether the map must ignore keys and clicks now (W5d): not our
     * turn, our new turn not shown yet, a pause pending, our end going
     * out, or goto orders running.
     *
     * @return True to ignore input.
     */
    boolean isInputBlocked() {
        return this.pending != null || this.ending || this.gotoRuns > 0
            || !this.turnStarted || !this.host.myTurn();
    }

    /**
     * Whether the map's active unit must stay ON and still now: a
     * hand-over is pending.  The unit shown until the next one comes is
     * the one the cycle has moved past; after a last move or Space it does
     * not blink anyway, but after W, F, S or a goto order that stopped
     * with moves left a visit comes at once ({@link #bringNow}) and the
     * unit is still up.  Its toggles are screen changes and re-based the
     * hand-over after the visit for good (G review: the turn froze).  The
     * map's blink asks this at every toggle.
     *
     * @return True while a hand-over is pending.
     */
    boolean holdsBlink() {
        final Pending p = this.pending;
        return p != null && p.kind == Kind.HANDOVER;
    }

    /**
     * @return The unit a pending hand-over brings, or null (W11: its tip
     *     shows at the switch, before it).
     */
    Unit comingUnit() {
        final Pending p = this.pending;
        return (p != null && p.kind == Kind.HANDOVER) ? p.unit : null;
    }

    /**
     * Whether the controller's view changes are dropped now: a goto unit
     * runs (W5f) and FreeCol asks for a view change after each of its
     * steps and for the next unit after its run, which the cycle decides.
     *
     * @return True while they are dropped.
     */
    boolean ignoring() {
        return this.gotoRuns > 0;
    }

    /**
     * @return The unit whose goto runs now ({@link #ignoring}), or null:
     *     the colony screen FreeCol opens when a land unit arrives at its
     *     destination colony is dropped (c6 U25: no screen).
     */
    Unit gotoUnit() {
        return this.gotoUnit;
    }

    /**
     * Whether the season line and the remembered unit block are frozen:
     * from our end of turn to the next wipe (W5b).
     *
     * @return True while waiting for our next turn to be shown.
     */
    boolean isWaiting() {
        return !this.turnStarted;
    }

    /** @return Whether the Spielzugende mode is on (W17). */
    boolean isPrompt() {
        return this.prompt;
    }

    /**
     * Whether the flow keeps the player waiting: a pause pending, our end
     * going out, goto orders running or our new turn not shown yet (the
     * acceptance harness's idle test).
     *
     * @return True while busy.
     */
    boolean isBusy() {
        return this.pending != null || this.ending || this.gotoRuns > 0
            || (!this.turnStarted && this.host.myTurn());
    }

    /** @return The pending pause, or null (tests). */
    Pending pending() {
        return this.pending;
    }

    /** @return The state for the recorder's probe (any thread). */
    String probeState() {
        return this.probeState;
    }

    /**
     * Run the timer's due stage, for a flow without the timer's thread
     * (tests): the poster gets it.
     *
     * @return True if a stage was due.
     */
    boolean runDue() {
        return this.timer.runIfDue();
    }

    /** Stop the flow for good (the game view is going). */
    void dispose() {
        this.disposed = true;
        this.pending = null;
        this.timer.close();
    }


    // Internals

    /** The base for a pause armed now: the last change if recent, else now. */
    private long lastChangeBase() {
        final long now = this.clock.now();
        return (this.lastChange != 0L
            && now - this.lastChange <= nanos(CHANGE_WINDOW_MS))
            ? this.lastChange : now;
    }

    /**
     * Arm the idle pause if nothing is left to move: the automatic end, or
     * the Spielzugende mode with the pref on (read now, delta W5a).
     *
     * @param base When the last change was.
     */
    private void armIdle(long base) {
        if (this.prompt || !this.host.myTurn()) return;
        if (this.host.hasNextActiveUnit()) {
            this.idleWanted = false;   // the controller brings a unit
            return;
        }
        if (this.host.anyDue()) {
            // A goto unit or a visit, which the controller does not bring
            // (W5f): the cycle's next one comes.
            this.idleWanted = false;
            final Unit active = this.host.activeUnit();
            final Unit next = this.host.cycleNext(active);
            if (next != null && this.pending == null) bring(next, active, base);
            return;
        }
        final boolean village = this.villageCancel != 0L
            && this.clock.now() - this.villageCancel <= nanos(VILLAGE_WINDOW_MS);
        this.idleVillage = (village) ? this.villageTile : null;
        final boolean promptPref = this.host.promptPref();
        start(new Pending((promptPref) ? Kind.PROMPT : Kind.END_TURN, null, base,
                new Stage(idleMs(promptPref, village),
                          (promptPref) ? Action.PROMPT : Action.END)));
    }

    /**
     * Wipe the panel for our new turn, if it is ours and no box is up.
     *
     * @return True if the turn is (now) shown.
     */
    private boolean wipe() {
        if (this.turnStarted) return true;
        if (!this.host.myTurn() || this.host.blocked()) return false;
        if (this.host.holdTurnStart()) return false;   // its box first (D8a)
        flashOwnColour();
        this.turnStarted = true;
        this.unwipedSince = 0L;
        this.wipeNanos = this.clock.now();
        ClassicFrameRecorder.event("turn-wipe", "turn=" + this.host.turnNumber());
        this.host.wipe();
        updateProbe();
        return true;
    }

    /**
     * Just before the wipe: our colour in the indicator for one tick
     * ({@link #OWN_FLASH_MS}, a paint of its own, the event thread held
     * as by a slide), unless it is lit already (a turn-start box was up).
     * The wipe then takes the indicator away with the year.
     */
    private void flashOwnColour() {
        final Player me = this.host.myPlayer();
        if (me == null || this.shownIndicator == ClassicHud.indicatorRgb(me)) return;
        this.ownFlash = true;
        try {
            this.host.paintIndicator();
            this.clock.waitUntil(this.clock.now() + nanos(OWN_FLASH_MS));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } finally {
            this.ownFlash = false;
        }
    }

    /** Our turn is over: freeze the panel until the next wipe. */
    private void waiting() {
        if (!this.turnStarted) return;
        this.turnStarted = false;
        ClassicFrameRecorder.event("turn-wait", "");
        updateProbe();
    }

    private void start(Pending p) {
        this.pending = p;
        // The unit's tutorial tip at the switch (W11), from the last change;
        // not for a goto run or a visit (their last stage is not ACTIVATE).
        if (p.kind == Kind.HANDOVER && p.unit != null && p.base != 0L
            && p.stages.length > 0 && p.stages[p.stages.length - 1].action == Action.ACTIVATE) {
            this.host.unitComing(p.unit, p.base);
        }
        if (p.base == 0L) {
            this.timer.cancel();
            ClassicFrameRecorder.event("endturn-timer-start", p + " waits for the wipe");
            updateProbe();
            return;
        }
        schedule(p, "start");
    }

    private void schedule(Pending p, String why) {
        final long due = p.dueNanos();
        this.timer.schedule(due, () -> fire(p));
        if (ClassicFrameRecorder.on()) {
            ClassicFrameRecorder.event("endturn-timer-start", why + " " + p
                + ((p.unit == null) ? "" : " unit=" + p.unit.getId())
                + String.format(Locale.ROOT, " stage=%d due=+%.1fms base=-%.1fms",
                    p.next, (due - this.clock.now()) / NS_PER_MS,
                    (this.clock.now() - p.base) / NS_PER_MS));
        }
        updateProbe();
    }

    private void cancel(String why) {
        final Pending p = this.pending;
        if (p == null) return;
        this.pending = null;
        this.timer.cancel();
        if (ClassicFrameRecorder.on()) {
            ClassicFrameRecorder.event("endturn-timer-cancel", why + " " + p);
        }
        updateProbe();
    }

    /**
     * A stage of the pending pause is due (event thread).  Whatever the
     * host does in it, a pause with stages left is scheduled on (in a
     * finally: a host call that threw used to leave the pause pending
     * with no timer, and the map's input blocked for good; FINAL "Open"
     * item 6).
     */
    private void fire(Pending p) {
        if (p != this.pending || this.disposed) return;
        final Action a = p.stages[p.next].action;
        if (a != Action.END && a != Action.PROMPT && a != Action.END_NOW
            && this.host.blocked()) {
            // A box came up meanwhile (a tutorial tip at the switch): the
            // unit comes up with its close (landfall 03 section 6).
            p.held = true;
            ClassicFrameRecorder.event("endturn-timer-fire", p.kind + " held: a box is up");
            updateProbe();
            return;
        }
        p.held = false;
        final Stage s = p.stages[p.next++];
        final boolean last = p.next >= p.stages.length;
        if (last) this.pending = null;
        if (ClassicFrameRecorder.on()) {
            ClassicFrameRecorder.event("endturn-timer-fire", p.kind + " " + s
                + String.format(Locale.ROOT, " late=%.2fms",
                    (this.clock.now() - (p.base + nanos(s.ms))) / NS_PER_MS));
        }
        try {
            switch (s.action) {
            case END:
                idleDecision(false);
                break;
            case PROMPT:
                idleDecision(true);
                break;
            case END_NOW:
                endTurnNow((this.promptEndWhy == null) ? "prompt" : this.promptEndWhy);
                this.promptEndWhy = null;
                break;
            case JUMP:
                if (usable(p.unit)) this.host.jumpTo(p.unit);
                break;
            case ACTIVATE:
                if (usable(p.unit)) {
                    this.host.activate(p.unit);
                } else {
                    this.pending = null;
                    this.timer.cancel();
                    afterUnit(p.unit, "unit gone");
                }
                break;
            case GOTO:
                if (usable(p.unit)) {
                    runGoto(p.unit);
                } else {
                    afterUnit(p.unit, "unit gone");
                }
                break;
            case VISIT:
                if (usable(p.unit)) {
                    this.host.visit(p.unit);
                } else {
                    this.pending = null;
                    this.timer.cancel();
                    this.host.visitShown(p.unit);   // nothing stays held
                    afterUnit(p.unit, "unit gone");
                }
                break;
            case SHOW:
                // The next pause runs from the completion's paint.
                this.host.visitShown(p.unit);
                afterUnit(p.unit, "visit");
                break;
            default:
                break;
            }
        } finally {
            if (!last && this.pending == p && !this.disposed) schedule(p, "next");
            updateProbe();
        }
    }

    /** Whether a unit can still be handed control. */
    private boolean usable(Unit u) {
        final Player me = this.host.myPlayer();
        return u != null && !u.isDisposed() && u.hasTile()
            && me != null && u.getOwner() == me && this.host.myTurn();
    }

    /**
     * The idle pause is over: end the turn, or enter the Spielzugende
     * mode, if nothing can move and nothing is up.
     *
     * @param promptMode The pause was the Spielzugende one.
     */
    private void idleDecision(boolean promptMode) {
        if (!this.host.myTurn()) return;
        if (this.host.blocked()) {
            ClassicFrameRecorder.event("endturn-timer-fire", "held: a box or screen is up");
            return;   // idleWanted stays: the close (or the poll) arms again
        }
        if (this.host.hasNextActiveUnit() || this.host.anyDue()) {
            // A unit can move again (one arrived meanwhile): the
            // controller brings it up, or the cycle a due goto unit or
            // visit.
            ClassicFrameRecorder.event("endturn-timer-fire", "kept: a unit can move");
            this.idleWanted = false;
            nextUnitOrIdle("kept");
            return;
        }
        if (!promptMode && endHeld()) {
            // W13: a band runs out first, Europe is closed first.
            ClassicFrameRecorder.event("endturn-timer-fire", "held: "
                + ((this.host.bandUp()) ? "band" : "Europe"));
            return;   // idleWanted stays: bandEnded or the close arms again
        }
        if (!promptMode && this.host.openEuropeInstead()) {
            ClassicFrameRecorder.event("endturn-timer-fire",
                "held: Europe instead of the end (guard)");
            return;   // idleWanted stays: the close arms again
        }
        if (promptMode) {
            this.prompt = true;
            this.idleWanted = false;
            ClassicFrameRecorder.event("endturn-prompt", "on");
            this.host.enterPrompt(this.idleVillage);
            return;
        }
        endTurnNow("auto");
    }

    /** The Spielzugende mode ends without an end: its look goes. */
    private void leavePrompt() {
        if (!this.prompt) return;
        this.prompt = false;
        ClassicFrameRecorder.event("endturn-prompt", "left");
        this.host.leavePrompt();
    }

    /**
     * W5f: one goto (or trade-route) unit moves when the cycle reaches it,
     * after its block; the controller's view changes meanwhile are dropped
     * until a marker posted after them ({@link #gotoDone}).
     *
     * @param unit The unit.
     */
    private void runGoto(Unit unit) {
        this.gotoRuns++;
        this.gotoUnit = unit;
        ClassicFrameRecorder.event("handover", "goto " + unit.getId());
        try {
            this.host.runGoto(unit);
        } finally {
            this.host.post(() -> gotoDone(unit));
        }
    }

    /**
     * The marker behind a goto run's queued view changes.  Arrived (or
     * stopped) on the map with moves left, the unit stays the active unit:
     * its block now ("Keine Befehle", the letter G gone), the blink timed
     * from it (c6 #5123, first OFF #5146); else the next unit of the cycle
     * comes as after a last move, 500 ms after its last change (c6 U22
     * #4803 -&gt; #4839).  A unit stopped early keeps its place as an
     * ORDERS unit and is not run again this turn
     * ({@link ClassicUnitCycle#kind}).  Nothing once the flow is disposed
     * (FINAL "Open" item 11).
     *
     * @param unit The unit that ran.
     */
    private void gotoDone(Unit unit) {
        this.gotoRuns--;
        if (this.gotoRuns <= 0) {
            this.gotoRuns = 0;
            this.gotoUnit = null;
        }
        if (this.disposed) return;
        ClassicFrameRecorder.event("handover", "goto done " + unit.getId()
            + " moves=" + unit.getMovesLeft() + " state=" + unit.getState()
            + ((unit.hasTile()) ? " at=" + unit.getTile().getX() + ","
               + unit.getTile().getY() : " off the map"));
        if (this.pending != null || this.ending || !this.turnStarted
            || !this.host.myTurn()) {
            updateProbe();
            return;
        }
        if (usable(unit) && unit.getMovesLeft() > 0 && !unit.isOnCarrier()
            && unit.getState() == Unit.UnitState.ACTIVE) {
            this.host.activate(unit);
        } else {
            afterUnit(unit, "goto");
        }
        updateProbe();
    }

    /**
     * Ask the controller for the next unit; if it chose none (neither a
     * unit nor its end view) while our turn is shown: the cycle's next due
     * unit, or with nothing to move the idle end, as its end view would
     * ({@link #noUnitLeft}).  FreeCol's controller stays in its goto or
     * end mode after a refused end with no active unit: its
     * {@code nextActiveUnit} then changes nothing, and the turn never
     * ended (D acceptance D1).
     *
     * @param why What asks (for the recorder).
     */
    private void nextUnitOrIdle(String why) {
        final int before = this.choices;
        this.host.nextActiveUnit();
        if (this.choices != before || this.disposed || this.pending != null
            || !this.turnStarted || this.ending || !this.host.myTurn()) {
            return;
        }
        if (this.host.anyDue()) {
            final Unit active = this.host.activeUnit();
            final Unit next = this.host.cycleNext(active);
            if (next != null) {
                ClassicFrameRecorder.event("handover", why + ": no unit came, the cycle's "
                    + next.getId());
                bring(next, active, lastChangeBase());
                return;
            }
        }
        if (this.host.hasNextActiveUnit()) return;
        ClassicFrameRecorder.event("handover", why + ": no unit came, idle end");
        noUnitLeft();
    }

    private void updateProbe() {
        final Pending p = this.pending;
        this.probeState = ((p == null) ? "idle" : p.kind + "/" + p.next)
            + ((this.turnStarted) ? "" : " waiting")
            + ((this.prompt) ? " prompt" : "")
            + ((this.gotoRuns > 0) ? " goto" : "");
    }
}
