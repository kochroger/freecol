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


/**
 * The rules shared by the two type-ahead gates, the title menu's
 * ({@code ClassicMainMenuPanel.typedAhead}, part T1) and the first scene's
 * ({@code ClassicHudOverlay.predates}, part T2): one place, so both judge a
 * press the same way (part T's review: the first scene had a clock-sanity
 * rule the menu lacked, and neither survived the wall clock stepping back).
 *
 * <ul>
 *   <li><b>A strange time</b> (an event time more than
 *       {@link #CLOCK_SANITY_MS} away from the clock, e.g. a synthetic
 *       event with time 0) is not gated, and the gate's bookkeeping starts
 *       afresh.</li>
 *   <li><b>The clock stepped back</b> (Windows' time sync after a boot or a
 *       resume, a manual change): a mark the gate took from the clock (a
 *       screen's change or paint) lies in the future, or an event-time mark
 *       (the last ignored press) lies more than {@link #CLOCK_BACK_SLACK_MS}
 *       ahead.  Without a reset every press until the clock caught up would
 *       count as "made before the screen came" -- the gate would be deaf for
 *       as long as the jump (part T's play review, P1: 30 s back, 17 of 20
 *       deliberate presses lost).  The gate then treats its screen as seen
 *       and forgets the last ignored press, so the next press counts.</li>
 *   <li><b>The burst rule</b> (a press made less than {@code BURST_GAP_MS}
 *       after an ignored one is ignored too) holds only for a press made
 *       less than {@link #BURST_WINDOW_MS} after its screen came, or for one
 *       dispatched more than {@link #LATE_DISPATCH_MS} after it was made
 *       (queued behind a block of the EDT, as in Roger's case).  Live
 *       mashing on a screen that has been up longer is served by the plain
 *       reaction rule (part T's play review, P2: a child hammering Enter
 *       must not find the game frozen).</li>
 * </ul>
 */
final class ClassicTypeAhead {

    /** An event time farther than this from the clock is strange (not gated). */
    static final long CLOCK_SANITY_MS = 60_000L;

    /** The burst rule holds for presses made up to this long after the screen came. */
    static final long BURST_WINDOW_MS = 1500L;

    /** A press dispatched more than this after it was made was queued (late). */
    static final long LATE_DISPATCH_MS = 100L;

    /** An event-time mark more than this ahead of the clock means the clock went back. */
    static final long CLOCK_BACK_SLACK_MS = 1000L;

    /**
     * The longest gap between two auto-repeated presses of a held key, as
     * {@code ClassicGUI.FrameKeys.REPEAT_GAP_MS} (Windows' longest keyboard
     * repeat delay is 1 s): a press of a swallowed key more than this after
     * that key's last event is a new press even though its release never
     * came (a release can be lost).
     */
    static final long REPEAT_GAP_MS = 1100L;


    private ClassicTypeAhead() {}   // static only

    /**
     * @param now The clock.
     * @param when The event's time.
     * @return Whether the event time is too far from the clock to judge.
     */
    static boolean strangeTime(long now, long when) {
        return Math.abs(now - when) > CLOCK_SANITY_MS;
    }

    /**
     * @param now The clock.
     * @param clockMark The latest mark the gate took from the clock.
     * @param eventMark The latest mark the gate took from an event's time.
     * @return Whether the clock has stepped back behind the gate's marks.
     */
    static boolean clockWentBack(long now, long clockMark, long eventMark) {
        return now < clockMark || eventMark > now + CLOCK_BACK_SLACK_MS;
    }

    /**
     * @param now The clock.
     * @param when The press's time.
     * @param seenAt When its screen came (painted, or changed if not yet painted).
     * @return Whether the burst rule may hold for this press.
     */
    static boolean burstMayHold(long now, long when, long seenAt) {
        return when < seenAt + BURST_WINDOW_MS || now - when > LATE_DISPATCH_MS;
    }

    /**
     * @param last The time of the key's previous event.
     * @param when The time of this press.
     * @return Whether this press is the auto-repeat of a key held since then.
     */
    static boolean repeatOf(long last, long when) {
        return when >= last && when - last <= REPEAT_GAP_MS;
    }
}
