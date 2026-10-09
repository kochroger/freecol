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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import net.sf.freecol.common.model.Europe;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Unit;


/**
 * The ships of our voyages between Europe and the New World (master plan
 * W13, spec R4 sections 4-6): which arrived at this turn start, and the
 * original's chain that shows it.  Headless; the GUI drives it.
 *
 * <ul>
 *   <li><b>Arrivals.</b>  The ships of ours at sea are noted while our
 *   turn is shown and when our end goes out ({@link #note}).  At our next
 *   turn start a noted ship now in Europe has arrived there, one now on
 *   the map has arrived in the New World; so has a ship of a consumed
 *   FreeCol message "model.unit.arriveInEurope" ({@link #consumed}).
 *   Taken once per turn ({@link #take}), which forgets the notes and the
 *   messages, so a chain never starts twice (R4 verifier, hang 1).  A
 *   ship on a trade route is left out (FreeCol sends it no message; U8).
 *   After a load nothing is noted, so a load shows no arrival: Europe opens
 *   by itself only when a ship has just arrived at a turn start, never as
 *   a reminder of ships left in port (Roger, 2026-10-08; Part H's guard
 *   and its load fallback are gone).</li>
 *   <li><b>The chain</b> ({@link Chain}), inside the turn flow's hold
 *   before the year flips: one band "... Trifft jetzt ein in Amsterdam"
 *   per ship {@link #BAND_TO_EUROPE_MS} apart, Europe opens that long
 *   after the last (landfall #27878 -&gt; #27916, clip006 #7942 -&gt;
 *   #7980, clip005 #18613 -&gt; #18652: 38-39 frames), the band goes when
 *   Europe is up (its {@code @TUTORIAL17} comes {@link #TUTORIAL_MS}
 *   later, landfall #27956, with any first Europe screen of the game);
 *   when Europe closes, per ship back in the New World the view jumps to
 *   it and its band "... Ankunft aus Amsterdam" follows one frame later
 *   (clip008 #26208/#26209), and the hold ends
 *   {@link #NEW_WORLD_RELEASE_MS} after the last band (the wipe #26218).
 *   Europe that does not open within {@link #EUROPE_TIMEOUT_MS} counts as
 *   closed (R4 verifier, hang 3).  While the hold waits for Europe's close
 *   the host keeps the screen up ({@link Chain.Host#keepEuropeUp}: one
 *   behind the map or minimized comes back, H REVIEW2 M1), and a host
 *   call that throws ends the chain, so the turn start goes on (L1).</li>
 *   <li><b>Europe closes by itself</b> {@link #EUROPE_CLOSE_MS} after the
 *   last ship in its port sailed ({@link #closesAfterSailing}; colonists
 *   on the dock do not keep it open; Roger, 2026-10-08, opening_013
 *   #3958 -&gt; #3983 and opening_014 #3669 -&gt; #3695).</li>
 * </ul>
 */
final class ClassicVoyages {

    /** FreeCol's message of a ship that arrived in Europe. */
    static final String ARRIVE_IN_EUROPE = "model.unit.arriveInEurope";

    /** A band's time on the strip: 137 frames (LF #25735-#25872, c8 #44180-#44317, #26209-#26346). */
    static final double BAND_MS = 137 * ClassicAdvisorLayer.FRAME_MS;

    /**
     * A band whose time ran out while the player did not have the turn
     * goes this long after our next turn's first unit came up (opening_013:
     * the departure band #1536 stays through the AI phase, the pioneer's
     * block #1854, the band gone #1856; europe-voyage D1).
     */
    static final double BAND_AFTER_UNIT_MS = 2 * ClassicAdvisorLayer.FRAME_MS;

    /**
     * The arrival chain's first step after our colour lit in the turn
     * indicator at the hold (landfall #27876 -&gt; #27878: 2 frames;
     * H REVIEW2 L5).
     */
    static final double CHAIN_AFTER_COLOUR_MS = 2 * ClassicAdvisorLayer.FRAME_MS;

    /** The arrival band to Europe drawn (38 frames: LF, c6, c5 0.542-0.556 s). */
    static final double BAND_TO_EUROPE_MS = 542.0;

    /** Europe drawn to {@code @TUTORIAL17} (LF #27917 -&gt; #27956: 0.557 s). */
    static final double TUTORIAL_MS = 557.0;

    /** The view's jump to a ship back in the New World, to its band (1 frame). */
    static final double NEW_WORLD_BAND_MS = ClassicAdvisorLayer.FRAME_MS;

    /** Its band to the end of the hold, the wipe (c8 #26209 -&gt; #26218: 9 frames). */
    static final double NEW_WORLD_RELEASE_MS = 128.0;

    /** Europe asked for and not open after this long: it counts as closed. */
    static final double EUROPE_TIMEOUT_MS = 1000.0;

    /**
     * The last ship in port sailed, to the map back (opening_013 "Jawohl,
     * setzt alle Segel" #3958 -&gt; #3983: 25 frames, 0.357 s; opening_014
     * #3669 -&gt; #3695: 26 frames; the pointer still both times).
     */
    static final double EUROPE_CLOSE_MS = 357.0;

    /** How often the chain looks again while it waits for a box or Europe. */
    static final double POLL_MS = 50.0;

    /** Nanoseconds per millisecond. */
    private static final double NS_PER_MS = 1_000_000.0;


    /** The ships at sea noted while our turn was shown. */
    private final Set<Unit> atSea = new LinkedHashSet<>();

    /** The ships of the consumed arrival messages. */
    private final Set<Unit> messaged = new LinkedHashSet<>();

    /** The turn whose arrivals were taken (-1: none). */
    private int takenTurn = -1;


    /** The arrivals of a turn start, in the order they are shown. */
    static final class Arrivals {

        /** The ships now in Europe, in the port's order. */
        final List<Unit> europe;

        /** The ships now on the map. */
        final List<Unit> newWorld;

        Arrivals(List<Unit> europe, List<Unit> newWorld) {
            this.europe = Collections.unmodifiableList(new ArrayList<>(europe));
            this.newWorld = Collections.unmodifiableList(new ArrayList<>(newWorld));
        }

        /** @return Whether nothing arrived. */
        boolean isEmpty() {
            return this.europe.isEmpty() && this.newWorld.isEmpty();
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return "europe=" + ids(this.europe) + " newWorld=" + ids(this.newWorld);
        }
    }

    /** No arrival. */
    static final Arrivals NONE = new Arrivals(List.of(), List.of());


    /**
     * Note the ships of ours at sea now (while our turn is shown, and when
     * our end goes out).
     *
     * @param me Our player, or null.
     */
    void note(Player me) {
        if (me == null) return;
        // An arrival message comes at a turn start, before its take; one
        // still here while our turn is shown is stale.
        this.messaged.clear();
        for (Unit u : me.getUnitSet()) {
            if (u.isNaval() && u.isAtSea() && !u.isDisposed()) this.atSea.add(u);
        }
    }

    /**
     * A ship of a FreeCol arrival message, taken out of the notices.
     *
     * @param ship The ship.
     */
    void consumed(Unit ship) {
        if (ship != null) this.messaged.add(ship);
    }

    /** Forget everything (a new game view). */
    void clear() {
        this.atSea.clear();
        this.messaged.clear();
        this.takenTurn = -1;
    }

    /**
     * The arrivals of our turn {@code turn}, once: a second call in the
     * same turn gets none.  The notes and the messages are forgotten.
     *
     * @param me Our player, or null.
     * @param turn The turn number.
     * @return The arrivals.
     */
    Arrivals take(Player me, int turn) {
        if (turn == this.takenTurn || me == null) return NONE;
        this.takenTurn = turn;
        final Arrivals a = arrivals(me, this.atSea, this.messaged);
        this.atSea.clear();
        this.messaged.clear();
        return a;
    }

    /**
     * The rule of {@link #take}.
     *
     * @param me Our player.
     * @param noted The ships noted at sea.
     * @param messaged The ships of consumed arrival messages.
     * @return The arrivals: ships in Europe in the port's order, ships now
     *     with a tile in the order noted; ours, not disposed, no trade route.
     */
    static Arrivals arrivals(Player me, Set<Unit> noted, Set<Unit> messaged) {
        final Europe europe = me.getEurope();
        final List<Unit> inEurope = new ArrayList<>();
        if (europe != null) {
            for (Unit u : europe.getUnitList()) {
                if (!u.isNaval() || !shown(u, me)) continue;
                if (noted.contains(u) || messaged.contains(u)) inEurope.add(u);
            }
        }
        final List<Unit> onMap = new ArrayList<>();
        for (Unit u : noted) {
            if (shown(u, me) && u.hasTile()) onMap.add(u);
        }
        return new Arrivals(inEurope, onMap);
    }

    /** A ship whose arrival is shown: ours, alive, no trade route. */
    private static boolean shown(Unit u, Player me) {
        return u != null && !u.isDisposed() && u.getOwner() == me
            && u.getTradeRoute() == null;
    }

    /**
     * Whether the Europe screen closes by itself after a ship sailed from
     * it (Roger, 2026-10-08): no ship is left in its port.  The colonists
     * on the dock do not keep it open, nor do the ships on the high seas
     * (both clips: "Keine Schiffe im Hafen", the map back 0.357 s later),
     * nor does a ship under repair: it cannot sail
     * ({@link ClassicEuropePanel#underRepair}; the review of part N: it
     * kept Europe open after the last ship that can sail had sailed, and
     * then stood selected; I: never recorded).
     *
     * @param europe Our Europe, or null.
     * @return True if no ship that can sail is in its port (false without
     *     a Europe).
     */
    static boolean closesAfterSailing(Europe europe) {
        if (europe == null) return false;
        for (Unit u : europe.getUnitList()) {
            if (u.isNaval() && !u.isDisposed()
                && !ClassicEuropePanel.underRepair(u)) return false;
        }
        return true;
    }

    /**
     * A timed band's end (europe-voyage D1): its time is up only while the
     * player has the turn; one that runs out after our end went out stays
     * through the AI phase until our next turn's first unit came up, and
     * goes {@link #BAND_AFTER_UNIT_MS} after it (opening_013 #1536 -&gt;
     * #1854 -&gt; #1856; I: the original checks the band's expiry only
     * while the player has the turn); with no unit up, once our turn is
     * shown and no unit is coming up.  The GUI asks; EDT only.
     */
    static final class BandEnd {

        /** The band's time ran out while the player did not have the turn. */
        private boolean expired = false;

        /** Its end after a unit came up is scheduled. */
        private boolean afterUnit = false;

        /** A band was shown or taken away: nothing pending. */
        void reset() {
            this.expired = this.afterUnit = false;
        }

        /**
         * The band's 137 frames are over.
         *
         * @param playerHasTurn Whether the player has the turn now.
         * @return True to take the band away now; false: it stays.
         */
        boolean timeUp(boolean playerHasTurn) {
            if (playerHasTurn) return true;
            this.expired = true;
            return false;
        }

        /**
         * A unit of ours came up.
         *
         * @param playerHasTurn Whether the player has the turn now.
         * @return True to take an expired band away
         *     {@link #BAND_AFTER_UNIT_MS} from now (once).
         */
        boolean unitUp(boolean playerHasTurn) {
            if (!this.expired || this.afterUnit || !playerHasTurn) return false;
            this.afterUnit = true;
            return true;
        }

        /**
         * The poll, for a turn with no unit up.
         *
         * @param playerHasTurn Whether the player has the turn now.
         * @param unitComing Whether a unit is on its way up (a turn start
         *     or a hand-over pending).
         * @return True to take an expired band away now.
         */
        boolean poll(boolean playerHasTurn, boolean unitComing) {
            return this.expired && !this.afterUnit && playerHasTurn && !unitComing;
        }

        /** @return Whether a band is kept past its time (tests, the recorder). */
        boolean expired() {
            return this.expired;
        }
    }

    private static String ids(List<Unit> us) {
        final StringBuilder sb = new StringBuilder();
        for (Unit u : us) {
            if (sb.length() > 0) sb.append('|');
            sb.append(u.getId());
        }
        return (sb.length() == 0) ? "-" : sb.toString();
    }

    /** @return {@code ms} in nanoseconds. */
    static long nanos(double ms) {
        return Math.round(ms * NS_PER_MS);
    }


    /**
     * The original's arrival chain (class comment), a step at a time on
     * the event thread: {@link #step} does what is due and says when to
     * come back.
     */
    static final class Chain {

        /** What the chain drives and asks.  Event thread only. */
        interface Host {

            /**
             * Show a band on the strip until another one or
             * {@link #clearBand}.
             *
             * @param text The band.
             */
            void band(String text);

            /**
             * Show a band for {@link #BAND_MS}; the turn flow hears its
             * end.
             *
             * @param text The band.
             */
            void timedBand(String text);

            /** Take the band away (the menu bar again). */
            void clearBand();

            /** @return Whether a box is up (Europe waits for its close). */
            boolean boxUp();

            /**
             * Open the Europe screen, or bring an open one up to date and
             * up (restored if minimized, in front of the map; H REVIEW2
             * M1).
             *
             * @return False if there is no Europe to open.
             */
            boolean openEurope();

            /** @return Whether the Europe screen is open. */
            boolean europeOpen();

            /**
             * The hold waits for Europe's close (polled while it is open):
             * a Europe screen behind the map or minimized while the player
             * is at the map comes up again, since every game key is dead
             * until it closes (H REVIEW2 M1).
             */
            default void keepEuropeUp() {
            }

            /**
             * Jump the view to a ship back in the New World.
             *
             * @param ship The ship.
             */
            void jumpTo(Unit ship);

            /** The chain is over: the turn start goes on. */
            void done();

            /**
             * A line for the recorder.
             *
             * @param what The event.
             */
            void event(String what);
        }

        /** Where the chain is. */
        enum State { EUROPE_BAND, EUROPE_OPEN, EUROPE_WAIT,
                     EUROPE_CLOSE, NEW_WORLD_JUMP, NEW_WORLD_BAND, RELEASE, DONE }

        private final Host host;
        private final Arrivals arrivals;
        private final Function<Unit, String> europeBand, newWorldBand;

        private State state;

        /** The next ship of the current part. */
        private int next = 0;

        /** When Europe was asked for. */
        private long askedAt = 0L;

        /**
         * @param host What the chain drives.
         * @param arrivals The arrivals.
         * @param europeBand The band of a ship in Europe.
         * @param newWorldBand The band of a ship back in the New World.
         */
        Chain(Host host, Arrivals arrivals, Function<Unit, String> europeBand,
              Function<Unit, String> newWorldBand) {
            this.host = host;
            this.arrivals = arrivals;
            this.europeBand = europeBand;
            this.newWorldBand = newWorldBand;
            this.state = (arrivals.europe.isEmpty()) ? State.NEW_WORLD_JUMP
                : State.EUROPE_BAND;
            if (arrivals.newWorld.isEmpty() && this.state == State.NEW_WORLD_JUMP) {
                this.state = State.RELEASE;
            }
        }

        /** @return Where the chain is (tests, the recorder). */
        State state() {
            return this.state;
        }

        /** @return Whether the chain is over. */
        boolean isDone() {
            return this.state == State.DONE;
        }

        /**
         * @return Whether the chain waits for the Europe screen (asked for,
         *     or open until its close): the E key brings it up then.
         */
        boolean waitsForEurope() {
            return this.state == State.EUROPE_WAIT || this.state == State.EUROPE_CLOSE;
        }

        /**
         * Do what is due now.  A host call that throws ends the chain (the
         * band goes, the turn start goes on, H REVIEW2 L1), as the turn
         * flow's own stages do: a chain left with nothing scheduled would
         * hold the turn start for good.
         *
         * @param now The clock now (ns).
         * @return When to step again (ns), or -1 when the chain is over.
         */
        long step(long now) {
            try {
                for (int guard = 0; guard < 32; guard++) {
                    final long due = stepOnce(now);
                    if (due != now) return due;
                }
                return now + nanos(POLL_MS);
            } catch (RuntimeException e) {
                abort(e);
                return -1L;
            }
        }

        /**
         * A step threw: the chain is over, the band goes and the host hears
         * the end, each in its own guard.
         *
         * @param e What was thrown.
         */
        private void abort(RuntimeException e) {
            final boolean over = this.state == State.DONE;
            this.state = State.DONE;
            try {
                this.host.event("arrival-failed " + e);
            } catch (RuntimeException ignored) {
                // the recorder line is the least of it
            }
            if (over) return;   // its own done() threw: it was told already
            try {
                this.host.clearBand();
            } catch (RuntimeException ignored) {
                // the band stays; the turn start must still go on
            }
            this.host.done();
        }

        /** One state's work; returns {@code now} to go on at once. */
        private long stepOnce(long now) {
            switch (this.state) {
            case EUROPE_BAND: {
                if (this.host.boxUp()) return now + nanos(POLL_MS);
                final Unit ship = this.arrivals.europe.get(this.next++);
                this.host.band(this.europeBand.apply(ship));
                this.host.event("arrival-band europe unit=" + ship.getId());
                if (this.next >= this.arrivals.europe.size()) {
                    this.state = State.EUROPE_OPEN;
                }
                return now + nanos(BAND_TO_EUROPE_MS);
            }
            case EUROPE_OPEN:
                // A box asked meanwhile (an emigration question): Europe
                // comes after its close.
                if (this.host.boxUp()) return now + nanos(POLL_MS);
                if (!this.host.openEurope()) {
                    this.host.event("arrival-europe none");
                    this.host.clearBand();
                    return toNewWorld(now);
                }
                this.askedAt = now;
                this.host.event("arrival-europe asked");
                this.state = State.EUROPE_WAIT;
                return now + nanos(POLL_MS);
            case EUROPE_WAIT:
                if (this.host.europeOpen()) {
                    this.host.clearBand();
                    this.host.event("arrival-europe open");
                    this.state = State.EUROPE_CLOSE;
                    return now + nanos(POLL_MS);
                }
                if (now - this.askedAt >= nanos(EUROPE_TIMEOUT_MS)) {
                    this.host.event("arrival-europe timeout: did not open");
                    this.host.clearBand();
                    return toNewWorld(now);
                }
                return now + nanos(POLL_MS);
            case EUROPE_CLOSE:
                if (this.host.europeOpen()) {
                    this.host.keepEuropeUp();
                    return now + nanos(POLL_MS);
                }
                this.host.event("arrival-europe closed");
                return toNewWorld(now);
            case NEW_WORLD_JUMP: {
                if (this.host.boxUp()) return now + nanos(POLL_MS);
                final Unit ship = this.arrivals.newWorld.get(this.next);
                this.host.jumpTo(ship);
                this.state = State.NEW_WORLD_BAND;
                return now + nanos(NEW_WORLD_BAND_MS);
            }
            case NEW_WORLD_BAND: {
                final Unit ship = this.arrivals.newWorld.get(this.next++);
                this.host.timedBand(this.newWorldBand.apply(ship));
                this.host.event("arrival-band newworld unit=" + ship.getId());
                if (this.next < this.arrivals.newWorld.size()) {
                    this.state = State.NEW_WORLD_JUMP;
                    return now + nanos(BAND_TO_EUROPE_MS);
                }
                this.state = State.RELEASE;
                return now + nanos(NEW_WORLD_RELEASE_MS);
            }
            case RELEASE:
                this.state = State.DONE;
                this.host.event("arrival-done");
                this.host.done();
                return -1L;
            case DONE: default:
                return -1L;
            }
        }

        /** The Europe part is over: the New World's ships, or the end. */
        private long toNewWorld(long now) {
            this.next = 0;
            this.state = (this.arrivals.newWorld.isEmpty()) ? State.RELEASE
                : State.NEW_WORLD_JUMP;
            return now;
        }
    }
}
