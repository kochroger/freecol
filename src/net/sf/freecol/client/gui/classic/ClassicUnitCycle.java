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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ObjLongConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import net.sf.freecol.common.model.EuropeanNationType;
import net.sf.freecol.common.model.NationType;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileImprovement;
import net.sf.freecol.common.model.TileImprovementType;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.Unit;


/**
 * The original's unit cycle (master plan W5f, F FINAL "Open" item 8): ONE
 * order for the whole turn, used at the turn start, at every hand-over
 * (after a last move, a skip, a landing, a goto run, a visit) and for W.
 * FreeCol's tile order ({@code Unit.locComparator}) is not used for them
 * in the Classic UI.  Swing-free, so the tests run it headless.
 *
 * <ul>
 *   <li><b>The order</b> ({@link #rank}): the original's unit list, the
 *   order the units came into the game, which FreeCol's id numbers keep,
 *   except that the start ship comes first (the original's unit 0;
 *   FreeCol makes it after the land units of the start: pioneer 5885,
 *   soldier 5886, merchantman 5887).  Landfall: ship, pioneer, soldier at
 *   every turn start 1498-1503; clip007 #1928, #5093, #9274; clip008
 *   #35590, #40835; the empty ship after the last passenger landed,
 *   clip007 #3697.  A carrier with older passengers is not put before them
 *   (F2's rule, which put the empty start ship behind the pioneer).</li>
 *   <li><b>Where it goes on</b> ({@link #next}): after a unit that has just
 *   finished, the first due unit after it, wrapping round to the head.
 *   Clip006 rules out "the nearest unit first" and the tile order (U2
 *   -&gt; U3 at (23,16), U3 -&gt; U4).  With no such unit (the turn start,
 *   back from a colony or Europe, a box, the terrain view, a load): from
 *   the <b>cursor</b>, never the head by default.</li>
 *   <li><b>The cursor</b> (I-prep cycle.md 3A, {@link #cursor}): a place in
 *   the order, -1 for the head, kept on the player
 *   ({@code Player.classicCycleCursor}, in the save).  A unit that becomes
 *   active puts it on itself ({@link #activated}); one that finishes (its
 *   last move, Space, its goto ran, its visit shown, gone) moves it past
 *   itself ({@link #finished}); W does not.  From the cursor the first due
 *   unit ranked at or after it comes, wrapping round to the head; a unit
 *   gone or not due there is passed over, a new unit (higher id) comes
 *   before the wrap.  The turn start too: at our end the cursor is settled
 *   ({@link #turnEnds}), past the unit if it is done, on it if it is still
 *   due; with the classic pref {@code turnStartFromCursor} off (the clips:
 *   every turn start at the head, cycle.md section 2) it goes back to the
 *   head instead, so a turn-start autosave shows the same.</li>
 *   <li><b>Due units</b> ({@link #kind}): a unit that can take orders
 *   ({@code isCandidateForNextActiveUnit}, also W18's woken passengers);
 *   a goto or trade-route unit on the map that has not run yet this turn
 *   ({@link Kind#GOTO}: it moves when the cycle reaches it, c6 U22, U25,
 *   U26); one that ran and is still active with moves (it stays the
 *   player's, {@link Kind#ORDERS}); and a unit whose road, plowing or
 *   fortification was completed at this turn start ({@link Kind#VISIT}: a
 *   silent jump, the letter and the road change, c6 #3447/#3450,
 *   #4555/#4556, #4589/#4590).</li>
 *   <li><b>Visits</b>: FreeCol completes the work before our turn is
 *   shown, so the state of our end of turn is kept ({@link #snapshot}).
 *   Until the visit the map and the panel show the old letter (R, the
 *   black F) and no new road ({@link #ordersRowShown},
 *   {@link #roadShown}).  A road under construction is never drawn or
 *   listed (clip008 #45293).</li>
 * </ul>
 *
 * <p>The cursor is saved: a load goes on at the saved unit (a save in the
 * middle of a turn holds the active unit, clip006 began a load at U1),
 * or, from a turn-start autosave, where the new turn starts.  A save
 * without it (older builds) starts at the head.  The rest of the per-turn
 * state is not saved: the completions of a turn start before an
 * (auto)save get no visit.  Event thread only, except the static display
 * helpers, which read what the event thread last set.
 */
final class ClassicUnitCycle {

    private static final Logger logger = Logger.getLogger(ClassicUnitCycle.class.getName());

    /** Why a unit is due in the cycle. */
    enum Kind {
        /** It takes the player's orders: the hand-over's block, the blink. */
        ORDERS,
        /** It goes to its destination now: its block, then its steps. */
        GOTO,
        /** Its multi-turn order was completed: the silent visit. */
        VISIT
    }

    /** What our end of turn saw of a unit with a multi-turn order. */
    private static final class Snap {

        /** FORTIFYING or IMPROVING. */
        final Unit.UnitState state;

        /** The NAMES.TXT {@code @ORDERS} row then (R, P, the black F). */
        final int row;

        /** Its tile then. */
        final Tile tile;

        /** The improvement it worked on, or null. */
        final TileImprovementType work;

        /** The tile's type then (a forest cleared changes it). */
        final TileType tileType;

        /** The work is a road (its tile's road is held until the visit). */
        final boolean road;

        Snap(Unit.UnitState state, int row, Tile tile,
             TileImprovementType work, boolean road) {
            this.state = state;
            this.row = row;
            this.tile = tile;
            this.work = work;
            this.tileType = tile.getType();
            this.road = road;
        }
    }

    /**
     * The cycle of the game view, whose held letters and roads the map and
     * the panel show ({@link #show}), or null.
     */
    private static volatile ClassicUnitCycle shown = null;

    /** The units whose goto ran this turn. */
    private final Set<Unit> gotoRan
        = Collections.newSetFromMap(new IdentityHashMap<>());

    /** Our last end of turn's units with a multi-turn order; then the visits' ones. */
    private final Map<Unit, Snap> snapshot = new IdentityHashMap<>();

    /** The visits still to come this turn. */
    private final Set<Unit> visits
        = Collections.newSetFromMap(new IdentityHashMap<>());

    /** From our end of turn to the next turn start every snapshot row is held. */
    private boolean holding = false;

    /** The tiles whose road is held (not drawn, not listed) now. */
    private final Set<Tile> heldRoads
        = Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * Where a new cursor goes: the player's field; the game view also puts
     * it on the server's copy of our player, which the saves write
     * ({@link #storeWith}).
     */
    private ObjLongConsumer<Player> store = Player::setClassicCycleCursor;

    /** The player of our end request in flight, or null. */
    private Player endPlayer = null;

    /** The cursor before {@link #turnEnds}, put back if the end is refused. */
    private long beforeEnd = -1L;


    // The order

    /**
     * The number of units a player starts with: the nation type's starting
     * units, one unit each, made in one batch (the start ship last).
     *
     * @param owner The player, or null.
     * @return The count, 0 for a player without start units (natives).
     */
    static int startCount(Player owner) {
        final NationType nt = (owner == null) ? null : owner.getNationType();
        if (!(nt instanceof EuropeanNationType)) return 0;
        try {
            return ((EuropeanNationType) nt).getStartingUnits().size();
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "ClassicUnitCycle: no starting units.", e);
            return 0;
        }
    }

    /**
     * The lowest id number among a player's units.
     *
     * @param owner The player, or null.
     * @return The number, or -1 without units.
     */
    static int lowestId(Player owner) {
        if (owner == null) return -1;
        int min = Integer.MAX_VALUE;
        for (Unit u : owner.getUnitSet()) {
            final int n = u.getIdNumber();
            if (n >= 0 && n < min) min = n;
        }
        return (min == Integer.MAX_VALUE) ? -1 : min;
    }

    /**
     * Whether a unit is a ship of the start (the original's unit 0): a
     * naval carrier whose id is within the start batch, counted from the
     * owner's lowest unit id.  The window finds it while any start unit
     * lives, and when only the ship is left.  A ship made right after the
     * owner's lowest unit, once every start unit before it is gone, would
     * count as one too (rare, I).
     *
     * @param u The unit.
     * @param lowest The owner's lowest unit id ({@link #lowestId}).
     * @param count The owner's start count ({@link #startCount}).
     * @return True for a start ship.
     */
    static boolean startCarrier(Unit u, int lowest, int count) {
        if (!u.isNaval() || !u.canCarryUnits() || lowest < 0) return false;
        final int n = u.getIdNumber();
        return n >= lowest && n - lowest < count;
    }

    /**
     * {@link #startCarrier(Unit, int, int)} with the owner's numbers.
     *
     * @param u The unit.
     * @return True for a start ship.
     */
    static boolean startCarrier(Unit u) {
        final Player owner = u.getOwner();
        return startCarrier(u, lowestId(owner), startCount(owner));
    }

    /**
     * A unit's place in the original's unit list: the start ship(s)
     * first, then every unit by its id number.
     *
     * @param u The unit.
     * @param lowest The owner's lowest unit id.
     * @param count The owner's start count.
     * @return The rank, lower first.
     */
    static long rank(Unit u, int lowest, int count) {
        final long id = u.getIdNumber() & 0xFFFFFFFFL;
        return ((startCarrier(u, lowest, count)) ? 0L : (1L << 32)) | id;
    }

    /**
     * {@link #rank(Unit, int, int)} with the owner's numbers.
     *
     * @param u The unit.
     * @return The rank, lower first.
     */
    static long rank(Unit u) {
        final Player owner = u.getOwner();
        return rank(u, lowestId(owner), startCount(owner));
    }

    /**
     * Units in the original's order.
     *
     * @param units The units (one owner's).
     * @param owner Their owner, for the start ship, or null.
     * @return A new sorted list.
     */
    static List<Unit> ranked(Collection<Unit> units, Player owner) {
        final int lowest = lowestId(owner), count = startCount(owner);
        final List<Unit> l = new ArrayList<>(units);
        l.sort(Comparator.comparingLong(u -> rank(u, lowest, count)));
        return l;
    }

    /**
     * The original's cycle after {@code anchor}: the other units, those
     * ranked after it first, then from the head (landing-slow
     * 02-landing.md section 3.6, clip007).
     *
     * @param anchor The unit that has just finished.
     * @param units The player's units.
     * @return Every other unit, in the cycle's order.
     */
    static List<Unit> after(Unit anchor, Collection<Unit> units) {
        final Player owner = anchor.getOwner();
        final int lowest = lowestId(owner), count = startCount(owner);
        final long r = rank(anchor, lowest, count);
        final List<Unit> sorted = ranked(units, owner);
        final List<Unit> out = new ArrayList<>(sorted.size());
        for (Unit u : sorted) {
            if (u != anchor && rank(u, lowest, count) > r) out.add(u);
        }
        for (Unit u : sorted) {
            if (u != anchor && rank(u, lowest, count) <= r) out.add(u);
        }
        return out;
    }


    // Due units

    /**
     * Why a unit is due now.
     *
     * @param u The unit, or null.
     * @return {@link Kind#VISIT} for a completed order not shown yet,
     *     {@link Kind#GOTO} for a goto or trade-route unit on the map that
     *     has not run this turn, {@link Kind#ORDERS} for a unit that takes
     *     orders (and a goto unit that ran and is still active with moves:
     *     it is not run twice, and FreeCol's end-of-turn goto pass must
     *     not find it, C FINAL "Open" item 9), else null.
     */
    Kind kind(Unit u) {
        if (u == null || u.isDisposed()) return null;
        if (this.visits.contains(u)) return (u.hasTile()) ? Kind.VISIT : null;
        final boolean going = u.goingToDestination() || u.isReadyToTrade();
        if (going && u.hasTile()) {
            return (this.gotoRan.contains(u)) ? Kind.ORDERS : Kind.GOTO;
        }
        return (u.isCandidateForNextActiveUnit()) ? Kind.ORDERS : null;
    }

    /**
     * The next unit of the cycle: the first due unit after {@code anchor}
     * in the original's order, wrapping to the head; the anchor itself
     * last (W: a waiting unit comes again after the wrap).  Without an
     * anchor: the first due unit ranked at or after the player's cursor
     * ({@link #cursor}; -1: the head), wrapping to the head.
     *
     * @param anchor The unit that has just finished, or null for the
     *     cursor (the turn start, and every choice in the middle of a turn
     *     with no unit that has just finished).
     * @param player The player.
     * @return The unit, or null if none is due.
     */
    Unit next(Unit anchor, Player player) {
        if (player == null) return null;
        final int lowest = lowestId(player), count = startCount(player);
        final List<Unit> order = ranked(player.getUnitSet(), player);
        int start = 0;
        if (anchor != null) {
            final long r = rank(anchor, lowest, count);
            while (start < order.size() && rank(order.get(start), lowest, count) <= r) {
                start++;
            }
        } else {
            final long c = cursor(player);
            while (start < order.size() && cursorAt(rank(order.get(start), lowest, count)) < c) {
                start++;
            }
        }
        for (int i = 0; i < order.size(); i++) {
            final Unit u = order.get((start + i) % order.size());
            if (u != anchor && kind(u) != null) return u;
        }
        return (anchor != null && anchor.getOwner() == player
                && kind(anchor) != null) ? anchor : null;
    }

    // The cursor

    /**
     * The cursor on a rank: twice the rank (so "on a unit" and "just past
     * it" are two values that never meet the next unit's, whose id may be
     * the next number).
     *
     * @param rank A rank ({@link #rank}).
     * @return The cursor on it.
     */
    static long cursorAt(long rank) {
        return rank << 1;
    }

    /**
     * @param u A unit.
     * @return The cursor on it (with its owner's numbers).
     */
    static long cursorOn(Unit u) {
        return cursorAt(rank(u));
    }

    /**
     * @param u A unit.
     * @return The cursor just past it (with its owner's numbers).
     */
    static long cursorPast(Unit u) {
        return cursorAt(rank(u)) + 1;
    }

    /**
     * Where a new cursor goes besides the player's field (the game view:
     * also the server's copy of our player, as {@code markWoodcut}).
     *
     * @param s The store, or null for the player's field alone.
     */
    void storeWith(ObjLongConsumer<Player> s) {
        this.store = (s == null) ? Player::setClassicCycleCursor : s;
    }

    /**
     * The player's cursor: -1 for the head, else twice a rank
     * ({@link #cursorAt}), plus one for just past that unit.
     *
     * @param p The player, or null.
     * @return The cursor.
     */
    static long cursor(Player p) {
        return (p == null) ? -1L : p.getClassicCycleCursor();
    }

    /** Put the cursor, if it changes. */
    private void setCursor(Player p, long c, String why) {
        if (p == null) return;
        final long now = (c < 0) ? -1L : c;
        if (now == cursor(p)) return;
        this.store.accept(p, now);
        if (ClassicFrameRecorder.on()) {
            ClassicFrameRecorder.event("cycle-cursor", why + " cursor="
                + ((now < 0) ? "head" : ((now & 1L) == 0 ? "on " : "past ")
                   + Long.toHexString(now >>> 1)));
        }
    }

    /**
     * A unit becomes active (the cycle's choice, a click, the boarding's
     * carrier, a goto run, a visit): the cursor is on it.  A unit that is
     * not due (re-selected after its last move, a landed passenger) does
     * not move it.
     *
     * @param u The unit, or null.
     */
    void activated(Unit u) {
        if (u == null || u.getOwner() == null || kind(u) == null) return;
        setCursor(u.getOwner(), cursorOn(u), "active " + u.getId());
    }

    /**
     * A unit is finished for this turn (its last move, Space, its goto
     * ran, its visit shown, gone: disposed, joined a colony, boarded,
     * sailed): the cursor moves just past it.  W is no finish.
     *
     * @param u The unit.
     * @param p Its owner (a unit gone may have none).
     */
    void finished(Unit u, Player p) {
        if (u == null || p == null) return;
        setCursor(p, cursorAt(rank(u, lowestId(p), startCount(p))) + 1, "finished " + u.getId());
    }

    /**
     * Our end request goes out: the cursor is settled for the next turn
     * start.  On a unit that is done now (finished unseen) it moves past
     * it; on a unit still due (the turn ended while it was up) it stays on
     * it.  With {@code fromCursor} off (the classic pref
     * {@code turnStartFromCursor}) it goes to the head, as every turn start
     * of the clips; the turn-start autosave holds that too.  A refused end
     * puts it back ({@link #endRefused}).
     *
     * @param p Our player, or null.
     * @param fromCursor Whether the next turn starts from the cursor.
     */
    void turnEnds(Player p, boolean fromCursor) {
        this.endPlayer = p;
        this.beforeEnd = cursor(p);
        if (p == null) return;
        long c = this.beforeEnd;
        if (c >= 0 && (c & 1L) == 0) {
            final Unit at = unitAt(p, c >>> 1);
            if (at != null && kind(at) == null) c++;
        }
        setCursor(p, (fromCursor) ? c : -1L, (fromCursor) ? "turn end" : "turn end, head");
    }

    /**
     * The player's unit at a rank.
     *
     * @param p The player.
     * @param r The rank.
     * @return The unit, or null if none is there.
     */
    static Unit unitAt(Player p, long r) {
        final int lowest = lowestId(p), count = startCount(p);
        for (Unit u : p.getUnitSet()) {
            if (rank(u, lowest, count) == r) return u;
        }
        return null;
    }

    /**
     * Whether any unit of the player is due.
     *
     * @param player The player, or null.
     * @return True if one is.
     */
    boolean anyDue(Player player) {
        if (player == null) return false;
        for (Unit u : player.getUnitSet()) {
            if (kind(u) != null) return true;
        }
        return false;
    }

    /**
     * A goto unit runs now: it is not run again this turn.
     *
     * @param u The unit.
     */
    void ran(Unit u) {
        if (u != null) this.gotoRan.add(u);
    }

    /** @return The number of visits still to come (tests, recorder). */
    int pendingVisits() {
        return this.visits.size();
    }


    // Visits

    /**
     * Our end-of-turn request goes out: remember the units with a
     * multi-turn order, and hold their letters (and roads) until the next
     * turn start, where the visits are found ({@link #turnStarted}).
     * Visits not shown are dropped (I: Enter ends the turn only with
     * nothing due).
     *
     * @param player Our player, or null.
     */
    void snapshot(Player player) {
        this.snapshot.clear();
        this.visits.clear();
        if (player != null) {
            for (Unit u : player.getUnitSet()) {
                if (u.isDisposed() || !u.hasTile() || u.isOnCarrier()) continue;
                final Unit.UnitState s = u.getState();
                if (s == Unit.UnitState.FORTIFYING) {
                    this.snapshot.put(u, new Snap(s, ClassicHud.ordersRow(u),
                                                  u.getTile(), null, false));
                } else if (s == Unit.UnitState.IMPROVING
                           && u.getWorkImprovement() != null) {
                    final TileImprovement ti = u.getWorkImprovement();
                    this.snapshot.put(u, new Snap(s, ClassicHud.ordersRow(u),
                        u.getTile(), ti.getType(), ti.isRoad()));
                }
            }
        }
        this.holding = !this.snapshot.isEmpty();
        rehold();
    }

    /**
     * Our end-of-turn request was refused: the turn goes on, so nothing is
     * held any more (the next request takes a new snapshot), and the
     * cursor is where it was before {@link #turnEnds}.
     */
    void endRefused() {
        this.holding = false;
        rehold();
        if (this.endPlayer != null) {
            setCursor(this.endPlayer, this.beforeEnd, "end refused");
            this.endPlayer = null;
        }
    }

    /**
     * Our new turn begins, before its first unit is chosen: no goto has
     * run yet, and the visits are the snapshot's units whose order FreeCol
     * completed at this turn start (FORTIFYING became FORTIFIED; IMPROVING
     * ended with the improvement complete or the tile changed), on the
     * same tile.  Every other held letter and road is released.  The
     * cursor stays as our end settled it ({@link #turnEnds}).
     *
     * @param player Our player, or null.
     */
    void turnStarted(Player player) {
        this.endPlayer = null;
        this.gotoRan.clear();
        this.visits.clear();
        for (Map.Entry<Unit, Snap> e : this.snapshot.entrySet()) {
            final Unit u = e.getKey();
            final Snap s = e.getValue();
            if (u.isDisposed() || player == null || u.getOwner() != player
                || u.getTile() != s.tile) continue;
            if (s.state == Unit.UnitState.FORTIFYING) {
                if (u.getState() == Unit.UnitState.FORTIFIED) this.visits.add(u);
            } else if (u.getState() != Unit.UnitState.IMPROVING && completed(s)) {
                this.visits.add(u);
            }
        }
        this.snapshot.keySet().retainAll(this.visits);
        this.holding = false;
        rehold();
        if (ClassicFrameRecorder.on() && !this.visits.isEmpty()) {
            ClassicFrameRecorder.event("cycle", "visits " + this.visits.stream()
                .map(Unit::getId).sorted().collect(Collectors.joining(",")));
        }
    }

    /** Whether a snapshot's improvement is done now. */
    private static boolean completed(Snap s) {
        if (s.work == null) return false;
        final TileImprovement ti = s.tile.getTileImprovement(s.work);
        return (ti != null && ti.isComplete()) || s.tile.getType() != s.tileType;
    }

    /**
     * The visit shows the completion: the unit's letter and its road are
     * released.
     *
     * @param u The unit visited.
     */
    void visited(Unit u) {
        this.visits.remove(u);
        this.snapshot.remove(u);
        rehold();
    }

    /**
     * The game changes (load, new game) or the view goes: forget everything
     * but the cursor, which is the player's (a load brings its own, a new
     * game's player has none).
     */
    void clear() {
        this.endPlayer = null;
        this.gotoRan.clear();
        this.snapshot.clear();
        this.visits.clear();
        this.holding = false;
        rehold();
    }

    /** Recompute the held roads. */
    private void rehold() {
        this.heldRoads.clear();
        for (Map.Entry<Unit, Snap> e : this.snapshot.entrySet()) {
            if (e.getValue().road && (this.holding || this.visits.contains(e.getKey()))) {
                this.heldRoads.add(e.getValue().tile);
            }
        }
    }

    /**
     * The orders row a unit shows: its snapshot's while held (until our
     * turn starts, and until its visit), else its own.
     *
     * @param u The unit.
     * @return The NAMES.TXT {@code @ORDERS} row.
     */
    int shownRow(Unit u) {
        final Snap s = this.snapshot.get(u);
        return (s != null && (this.holding || this.visits.contains(u)))
            ? s.row : ClassicHud.ordersRow(u);
    }

    /**
     * Whether a tile's road is held.
     *
     * @param t The tile.
     * @return True until the visit of the unit that built it.
     */
    boolean roadHeld(Tile t) {
        return this.heldRoads.contains(t);
    }


    // What the map and the panel show

    /**
     * The game view's cycle is {@code c} (null: none).
     *
     * @param c The cycle, or null.
     */
    static void show(ClassicUnitCycle c) {
        shown = c;
    }

    /**
     * The game view of {@code c} goes: no cycle is shown any more, unless
     * another one has replaced it.
     *
     * @param c The cycle.
     */
    static void unshow(ClassicUnitCycle c) {
        if (shown == c) shown = null;
    }

    /**
     * The orders row the map and the panel show for a unit: the held one
     * (the R, P or black F of our end of turn until the visit), else
     * {@link ClassicHud#ordersRow}.
     *
     * @param u The unit.
     * @return The row.
     */
    static int ordersRowShown(Unit u) {
        final ClassicUnitCycle c = shown;
        return (c == null) ? ClassicHud.ordersRow(u) : c.shownRow(u);
    }

    /**
     * Whether a tile shows a road on the map and "(Straße)" in the panel:
     * only a complete one (a road under construction is never drawn or
     * listed, clip008 #45293; FreeCol's own map skips it too), and one
     * just completed only from its visit on (c6 #3447 -&gt; #3450).
     *
     * @param t The tile, or null.
     * @return True to show the road.
     */
    static boolean roadShown(Tile t) {
        final TileImprovement road = (t == null) ? null : t.getRoad();
        if (road == null || !road.isComplete()) return false;
        final ClassicUnitCycle c = shown;
        return c == null || !c.roadHeld(t);
    }
}
