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
 *   <li><b>Hand-over</b> (W5e): when the controller brings a different
 *   unit after the previous one ran out of moves, its panel block comes
 *   {@link #HANDOVER_MS} after the last change, and a view jump it needs
 *   at {@link #HANDOVER_JUMP_MS}.  Until then the map and the panel show
 *   the previous unit, as it was before its last move (W5b).  A skipped
 *   unit counts as done; after a boarding the carrier comes
 *   {@link #BOARDING_HANDOVER_MS} after it ({@link #carrierChosen}, spec
 *   delta W18).</li>
 *   <li><b>Turn start</b> (W5e): the panel is wiped and the year changes
 *   in one paint ({@link #wipe}), when the controller first shows our new
 *   turn and no box is up (so FreeCol's turn-start messages come before
 *   the year flips, W5g); the first unit's block follows
 *   {@link #TURN_START_MS} later, or with a jump the jump at
 *   {@link #TURN_START_JUMP_MS} and the block one to two frames after it.
 *   Units with a destination move first (W5f).</li>
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
 * before any of its stages ran, re-bases the pause on it.
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

    /** Turn start: a goto unit's first step after its block (the goto gap, W2). */
    static final double GOTO_START_MS = ClassicSlide.GOTO_GAP_MS;

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

        /** @return Whether a unit can still be made active. */
        boolean hasNextActiveUnit();

        /** @return Whether a unit still goes to a destination. */
        boolean hasNextGoingToUnit();

        /** @return The first unit with a destination, or null. */
        Unit firstGoingToUnit();

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

        /** Run the goto orders ({@code executeGotoOrders}). */
        void runGotoOrders();

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
    }

    /** The pauses. */
    enum Kind { END_TURN, PROMPT, PROMPT_END, HANDOVER, TURN_START }

    /** What a pause does at one of its moments. */
    enum Action { END, PROMPT, END_NOW, JUMP, ACTIVATE, GOTO }

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

    /**
     * How often the controller chose a unit or reported none
     * ({@link #unitChosen}, {@link #noUnitLeft}): {@link #gotosDone} sees
     * whether its call brought anything.
     */
    private int choices = 0;

    /** After the goto orders the next unit is a hand-over. */
    private boolean afterGoto = false;

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
     * The stages of a turn start.
     *
     * @param jump The first unit needs a view jump.
     * @param gotos It is a unit with a destination: its goto orders run
     *     after its block.
     * @return Block at 300 ms, or jump at 500 ms and block 21 ms later;
     *     then the goto orders 100 ms after the block.
     */
    static Stage[] turnStartStages(boolean jump, boolean gotos) {
        final double block = (jump) ? TURN_START_JUMP_MS + TURN_START_BLOCK_MS
            : TURN_START_MS;
        final java.util.List<Stage> s = new java.util.ArrayList<>(3);
        if (jump) s.add(new Stage(TURN_START_JUMP_MS, Action.JUMP));
        s.add(new Stage(block, Action.ACTIVATE));
        if (gotos) s.add(new Stage(block + GOTO_START_MS, Action.GOTO));
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
        this.afterGoto = false;
        this.idleWanted = this.host.myTurn() && !this.ending;
        if (!this.idleWanted) return;   // our end's own view change
        if (!this.turnStarted) {
            this.activatedTurn = this.host.turnNumber();
            if (!wipe()) return;   // a box is up: the poll wipes and arms later
        }
        armIdle(lastChangeBase());
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
        return unitChosen(unit, previous, false);
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
        return unitChosen(carrier, boarded, true);
    }

    /**
     * {@link #unitChosen(Unit, Unit)}, after a boarding or not.
     *
     * @param unit The unit, or null.
     * @param previous The map's active unit now, or null.
     * @param boarding The previous unit has just boarded {@code unit}.
     * @return True if the flow takes it over.
     */
    private boolean unitChosen(Unit unit, Unit previous, boolean boarding) {
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
        if (p != null && (p.kind == Kind.TURN_START
                || (p.kind == Kind.HANDOVER
                    && (p.unit == unit || unit == previous)))) {
            // The turn start brings its unit (and then asks again); a
            // hand-over keeps its unit, also when the shown one is
            // re-selected meanwhile.
            return true;
        }
        cancel("unit");
        leavePrompt();
        this.idleWanted = false;
        final int turn = this.host.turnNumber();
        if (!this.turnStarted || turn != this.activatedTurn) {
            this.activatedTurn = turn;
            this.afterGoto = false;
            final Unit first = (this.host.hasNextGoingToUnit())
                ? this.host.firstGoingToUnit() : null;
            final Unit target = (first != null) ? first : unit;
            final boolean wiped = wipe();
            start(new Pending(Kind.TURN_START, target,
                    (wiped) ? this.wipeNanos : 0L,
                    turnStartStages(this.host.wouldJump(target), first != null)));
            return true;
        }
        if (unit == previous) {   // the re-selection after a move
            this.afterGoto = false;
            return false;
        }
        if (this.afterGoto || ranOut(previous)) {
            this.afterGoto = false;
            final boolean jump = this.host.wouldJump(unit);
            if (boarding) {
                ClassicFrameRecorder.event("handover", "after boarding: carrier "
                    + unit.getId() + " (boarded " + previous.getId() + ")");
            }
            start(new Pending(Kind.HANDOVER, unit, lastChangeBase(),
                    (boarding) ? boardingStages(jump) : handOverStages(jump)));
            return true;
        }
        return false;
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
        this.afterGoto = false;
        ClassicFrameRecorder.event("end-turn", why);
        this.ending = true;
        this.endingSince = this.clock.now();
        this.endingTurn = this.host.turnNumber();
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
            this.afterGoto = false;
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
                    p.base = this.wipeNanos;
                    schedule(p, "wiped");
                }
            } else if (this.idleWanted && p == null) {
                if (wipe()) armIdle(this.wipeNanos);
            }
            return;
        }
        if (this.idleWanted && p == null && !this.prompt
            && this.host.activeUnit() == null) {
            armIdle(lastChangeBase());
        }
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
     * Whether the controller's view changes are dropped now: the goto
     * orders run at the start of our turn (W5f) and FreeCol queues a view
     * change for each unit it moves, which would arrive after the moves.
     *
     * @return True while they are dropped.
     */
    boolean ignoring() {
        return this.gotoRuns > 0;
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
        if (this.host.hasNextActiveUnit() || this.host.hasNextGoingToUnit()) {
            this.idleWanted = false;   // the controller brings a unit
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
                    nextUnitOrIdle("unit gone");
                }
                break;
            case GOTO:
                runGotos();
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
        if (this.host.hasNextActiveUnit() || this.host.hasNextGoingToUnit()) {
            // A unit can move again (one arrived meanwhile): the
            // controller brings it up.
            ClassicFrameRecorder.event("endturn-timer-fire", "kept: a unit can move");
            this.idleWanted = false;
            this.host.nextActiveUnit();
            return;
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
     * W5f: the goto orders of the turn start, then the next unit as a
     * hand-over from the last goto step.  The view changes FreeCol queues
     * meanwhile are dropped until a marker posted after them.
     */
    private void runGotos() {
        this.gotoRuns++;
        ClassicFrameRecorder.event("handover", "goto orders");
        try {
            this.host.runGotoOrders();
        } finally {
            this.host.post(this::gotosDone);
        }
    }

    /**
     * The marker behind the goto orders' queued view changes: the next
     * unit comes as a hand-over from the last goto step.  FreeCol's
     * controller leaves its goto mode on the first {@code nextActiveUnit}
     * after the orders and brings no unit then when the orders stopped
     * early (a goto unit with no path, which it skips): if that call chose
     * nothing, it is asked once more, so a unit always comes up (FINAL
     * "Open" item 8), or the idle end ({@link #nextUnitOrIdle}).  Nothing
     * once the flow is disposed (item 11).
     */
    private void gotosDone() {
        this.gotoRuns--;
        if (this.disposed) return;
        this.afterGoto = true;
        ClassicFrameRecorder.event("handover", "goto orders done");
        final int before = this.choices;
        this.host.nextActiveUnit();
        if (this.choices == before && !this.disposed && this.pending == null
            && this.turnStarted && !this.ending && this.host.myTurn()) {
            // Once: a unit that can move, or the controller's end view
            // (and so the automatic end).
            ClassicFrameRecorder.event("handover", "goto orders: no unit came, next unit again");
            nextUnitOrIdle("goto orders");
        }
        updateProbe();
    }

    /**
     * Ask the controller for the next unit; if it chose none (neither a
     * unit nor its end view) while our turn is shown and nothing can move
     * or go to its destination, arm the idle end here, as its end view
     * would ({@link #noUnitLeft}).  FreeCol's controller stays in its goto
     * mode when goto orders took a unit off the map (a ship sailing for
     * Europe) and no unit is active: its {@code nextActiveUnit} then
     * changes nothing, and the turn never ended (D acceptance D1, the
     * turn start's goto orders).
     *
     * @param why What asks (for the recorder).
     */
    private void nextUnitOrIdle(String why) {
        final int before = this.choices;
        this.host.nextActiveUnit();
        if (this.choices != before || this.disposed || this.pending != null
            || !this.turnStarted || this.ending || !this.host.myTurn()
            || this.host.hasNextActiveUnit() || this.host.hasNextGoingToUnit()) {
            return;
        }
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
