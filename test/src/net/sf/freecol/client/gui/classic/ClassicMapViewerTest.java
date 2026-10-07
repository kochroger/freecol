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

import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * Headless tests of what the classic map draws on a tile and when a slide
 * starts at offset 0 (build spec W2): the unit in front, the cargo and
 * stack markers, the blink-OFF tile, and the passenger leaving its ship.
 */
public class ClassicMapViewerTest extends FreeColTestCase {

    public void testWhatATileShows() {
        final Game game = getStandardGame();
        final Map map = getCoastTestMap(spec().getTileType("model.tile.plains"), true);
        game.changeMap(map);
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final UnitType colonistType = spec().getUnitType("model.unit.freeColonist");
        final Tile sea = map.getTile(15, 7);
        final Tile land = map.getTile(5, 7);
        assertTrue(sea.isExplored() && !sea.isLand());
        assertTrue(land.isLand());

        final ClassicMapViewer mv = new ClassicMapViewer(null, null, null, false);
        final Unit ship = new ServerUnit(game, sea, dutch,
            spec().getUnitType("model.unit.merchantman"));
        assertSame(ship, mv.displayUnit(sea));
        assertSame(ClassicHud.NO_MARKER, mv.markerOf(ship, sea));
        assertTrue(mv.isShownAt(ship, sea));

        // A passenger: the ship carries the cargo marker, the passenger
        // is not on screen -- leaving the ship starts at offset 0.
        final Unit passenger = new ServerUnit(game, ship, dutch, colonistType);
        assertSame(ship, mv.displayUnit(sea));
        assertSame(ClassicHud.CARGO_MARKER, mv.markerOf(ship, sea));
        assertFalse(mv.isShownAt(passenger, sea));
        assertTrue(ClassicMapViewer.startsAtOffsetZero(false, true, false));

        // The ship active and blinked OFF: the tile is bare, a key starts
        // the slide at offset 0; ON it starts at offset 1.
        mv.setFocus(sea);
        mv.changeToMoveUnits(ship);
        assertTrue(mv.isShownAt(ship, sea));
        assertFalse(ClassicMapViewer.startsAtOffsetZero(false, true, true));
        mv.setBlinkOff(true);
        assertTrue(mv.isBlinkOff());
        assertFalse(mv.isShownAt(ship, sea));
        mv.setBlinkOff(false);

        // A passenger that has just boarded is asleep: while it is still
        // the active unit, until the hand-over, the ship is drawn (clip007
        // #4272).
        assertEquals(Unit.UnitState.SENTRY, passenger.getState());
        mv.changeToMoveUnits(passenger);
        assertSame(ship, mv.displayUnit(sea));
        assertFalse(mv.isShownAt(passenger, sea));

        // A woken passenger active: drawn instead of its ship, with the
        // stack marker (W18's ON state, clip007 #3107).
        passenger.setState(Unit.UnitState.ACTIVE);
        mv.changeToMoveUnits(passenger);
        assertSame(passenger, mv.displayUnit(sea));
        assertSame(ClassicHud.STACK_MARKER, mv.markerOf(passenger, sea));
        assertTrue(mv.isShownAt(passenger, sea));

        // The landing: the landed unit is the active unit, with no moves
        // left, no blink and no view test; the ship is drawn again.
        passenger.setLocation(land);
        passenger.setMovesLeft(0);
        mv.changeToMoveUnits(ship);
        assertTrue(mv.isBlinkArmed());
        mv.finishedUnit(passenger);
        assertSame(passenger, mv.getActiveUnit());
        assertFalse(mv.isBlinkArmed());
        assertFalse(mv.isBlinkOff());
        assertSame(ship, mv.displayUnit(sea));
        assertSame(passenger, mv.displayUnit(land));
        assertSame(ClassicHud.NO_MARKER, mv.markerOf(ship, sea));
        passenger.setLocation(ship);
        passenger.setMovesLeft(3);

        // Land: one unit plain, a second one adds the stack marker.
        final Unit a = new ServerUnit(game, land, dutch, colonistType);
        assertSame(a, mv.displayUnit(land));
        assertSame(ClassicHud.NO_MARKER, mv.markerOf(a, land));
        final Unit b = new ServerUnit(game, land, dutch, colonistType);
        assertSame(ClassicHud.STACK_MARKER, mv.markerOf(mv.displayUnit(land), land));
        assertNotNull(b);

        // A view jump always shows offset 0 first; a foreign unit that was
        // not on screen appears at offset 1.
        assertTrue(ClassicMapViewer.startsAtOffsetZero(true, false, false));
        assertFalse(ClassicMapViewer.startsAtOffsetZero(false, false, false));
        mv.dispose();
    }

    /**
     * The blink (build spec W3): armed ON on activation, OFF on odd
     * toggles with the tile bare (no unit, no carrier, no stack), held ON
     * by a box and re-armed ON at its close, stopped without an active
     * unit; the minimap dot nation colour while ON, white while OFF.
     */
    public void testBlink() {
        final Game game = getStandardGame();
        final Map map = getCoastTestMap(spec().getTileType("model.tile.plains"), true);
        game.changeMap(map);
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final UnitType colonistType = spec().getUnitType("model.unit.freeColonist");
        final Tile sea = map.getTile(15, 7);
        final Tile land = map.getTile(5, 7);
        final ClassicMapViewer mv = new ClassicMapViewer(null, null, null, false);
        final Unit ship = new ServerUnit(game, sea, dutch,
            spec().getUnitType("model.unit.merchantman"));
        final Unit passenger = new ServerUnit(game, ship, dutch, colonistType);
        mv.setFocus(sea);
        assertFalse(mv.isBlinkArmed());

        // Nothing timed is due: the background preload may run (F6).
        assertFalse(mv.holdsPreload());
        // Activation: ON, armed.
        mv.changeToMoveUnits(ship);
        assertTrue(mv.isBlinkArmed());
        assertFalse(mv.isBlinkOff());
        assertTrue(mv.isShownAt(ship, sea));
        // Just armed: its panel paint runs, the preload waits.
        assertTrue(mv.holdsPreload());

        // Toggle 1 OFF: the tile is bare -- the laden ship, its cargo
        // marker and its passenger are all gone; toggle 2 ON again.
        mv.blinkToggle(1);
        assertTrue(mv.isBlinkOff());
        assertFalse(mv.isShownAt(ship, sea));
        assertFalse(mv.isShownAt(passenger, sea));
        mv.blinkToggle(2);
        assertFalse(mv.isBlinkOff());
        assertTrue(mv.isShownAt(ship, sea));

        // A box opens while OFF: the unit is redrawn ON at once and held;
        // its close re-arms ON (reset, not pause).
        mv.blinkToggle(3);
        assertTrue(mv.isBlinkOff());
        mv.holdBlink("dialog");
        assertFalse(mv.isBlinkOff());
        assertTrue(mv.isBlinkHeld());
        mv.resumeBlink("dialog");
        assertFalse(mv.isBlinkHeld());
        assertTrue(mv.isBlinkArmed());
        assertFalse(mv.isBlinkOff());
        // A hold that ended without a close hook: the next toggle re-arms
        // instead of toggling.
        mv.holdBlink("screen");
        mv.blinkToggle(1);
        assertFalse(mv.isBlinkHeld());
        assertFalse(mv.isBlinkOff());
        mv.blinkToggle(1);
        assertTrue(mv.isBlinkOff());

        // The minimap dot: nation colour while ON, white while OFF.
        final ClassicHud.MinimapModel mm = ClassicHud.minimapOf(map, 8, 1);
        assertEquals(0xFF7100, mm.at(15, 7));
        assertEquals(ClassicHud.BLINK_DOT_RGB,
            ClassicInfoPanel.blinkDot(mm, ship, true).at(15, 7));
        assertEquals(0xFF7100, ClassicInfoPanel.blinkDot(mm, ship, false).at(15, 7));
        assertEquals(mm.at(14, 7), ClassicInfoPanel.blinkDot(mm, ship, true).at(14, 7));
        assertSame(mm, ClassicInfoPanel.blinkDot(mm, null, true));
        assertNull(ClassicInfoPanel.blinkDot(null, ship, true));

        // The woken passenger active (W18's case, landfall #13140): OFF
        // shows the bare sea -- the carrier goes with it.
        passenger.setState(Unit.UnitState.ACTIVE);
        mv.changeToMoveUnits(passenger);
        assertTrue(mv.isShownAt(passenger, sea));
        mv.blinkToggle(1);
        assertFalse(mv.isShownAt(passenger, sea));
        assertFalse(mv.isShownAt(ship, sea));

        // Another unit activated while OFF: ON again, a new phase.
        final Unit a = new ServerUnit(game, land, dutch, colonistType);
        mv.changeToMoveUnits(a);
        assertFalse(mv.isBlinkOff());
        assertTrue(mv.isBlinkArmed());
        assertTrue(mv.isShownAt(ship, sea));
        mv.blinkToggle(1);
        assertFalse(mv.isShownAt(a, land));
        assertTrue(mv.isShownAt(ship, sea));

        // A visit while the active unit blinks (one that came at once
        // after W, F or S; G review): the unit is drawn ON at once and
        // held; the next activation re-arms it.  Without the GUI's hold
        // (the turn flow's hand-over) the next toggle resumes it.
        assertTrue(mv.isBlinkOff());
        mv.visit(ship);
        assertFalse(mv.isBlinkOff());
        assertTrue(mv.isBlinkHeld());
        assertTrue(mv.isShownAt(a, land));
        mv.changeToMoveUnits(a);
        assertFalse(mv.isBlinkHeld());
        assertTrue(mv.isBlinkArmed());
        mv.visit(ship);
        assertTrue(mv.isBlinkHeld());
        mv.blinkToggle(1);                         // no hold reason here
        assertFalse(mv.isBlinkHeld());
        assertFalse(mv.isBlinkOff());
        mv.blinkToggle(1);
        assertTrue(mv.isBlinkOff());

        // A skipped unit with moves left (FreeCol's trade route without a
        // path) stops the blink at its next toggle, as rearmBlink does.
        a.setState(Unit.UnitState.SKIPPED);
        assertTrue(a.getMovesLeft() > 0);
        mv.blinkToggle(2);
        assertFalse(mv.isBlinkArmed());
        assertFalse(mv.isBlinkOff());
        assertTrue(mv.isShownAt(a, land));
        a.setState(Unit.UnitState.ACTIVE);

        // No active unit: no blink at all.
        mv.changeToEndTurn();
        assertFalse(mv.isBlinkArmed());
        assertFalse(mv.isBlinkOff());
        assertTrue(mv.isShownAt(a, land));
        mv.blinkToggle(1);
        assertFalse(mv.isBlinkOff());
        mv.changeToMoveUnits(a);
        assertTrue(mv.isBlinkArmed());
        mv.changeToTerrain(land);
        assertFalse(mv.isBlinkArmed());
        mv.dispose();
    }

    /**
     * Build spec W17: the Spielzugende square on the last unit's tile, ON
     * first and toggled by its own clock, frozen in its phase while a box
     * or a menu is up and restarted ON after it, forced ON and frozen by
     * the end command, gone with the next activation (frozen: also with a
     * jump); its minimap pixel white while it is drawn.
     */
    public void testPromptSquare() {
        final Game game = getStandardGame();
        final Map map = new MapBuilder(game).setDimensions(58, 72)
            .setBaseTileType(spec().getTileType("model.tile.ocean"))
            .setExploredByAll(true).build();
        game.changeMap(map);
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final Tile sea = map.getTile(30, 30);
        final ClassicMapViewer mv = new ClassicMapViewer(null, null, null, false);
        final Unit ship = new ServerUnit(game, sea, dutch,
            spec().getUnitType("model.unit.merchantman"));
        mv.setFocus(sea);
        mv.changeToMoveUnits(ship);
        ship.setMovesLeft(0);
        mv.changeToEndTurn();
        assertNull(mv.getActiveUnit());
        assertSame(sea, mv.promptTileFor());     // the last unit's tile

        mv.enterPrompt(sea);
        assertSame(sea, mv.promptTile());
        assertTrue(mv.isPromptShown());
        assertTrue(mv.isPromptArmed());
        assertFalse(mv.isBlinkArmed());          // no unit blinks in the mode
        mv.promptToggle(1);
        assertFalse(mv.isPromptShown());
        mv.promptToggle(2);
        assertTrue(mv.isPromptShown());
        mv.promptToggle(3);
        // A menu opens while OFF: the phase stays (no forced ON), the clock
        // waits; the close restarts it (ON one frame later).
        mv.holdBlink("menu");
        assertTrue(mv.isPromptHeld());
        assertFalse(mv.isPromptShown());
        assertFalse(mv.isPromptArmed());
        mv.resumeBlink("menu");
        assertFalse(mv.isPromptHeld());
        assertTrue(mv.isPromptArmed());
        assertFalse(mv.isPromptShown());
        mv.promptToggle(2);
        assertTrue(mv.isPromptShown());
        // The end command while OFF: ON at once, frozen.
        mv.promptToggle(3);
        assertFalse(mv.isPromptShown());
        mv.freezePrompt();
        assertTrue(mv.isPromptShown());
        assertTrue(mv.isPromptFrozen());
        assertFalse(mv.isPromptArmed());
        mv.promptToggle(5);
        assertTrue(mv.isPromptShown());

        // The minimap pixel: white while the square is drawn.
        final ClassicHud.MinimapModel mm = ClassicHud.minimapOf(map, 23, 24);
        assertEquals(0xFF7100, mm.at(30, 30));
        assertEquals(0xFFFFFF, ClassicInfoPanel.promptDot(mm, sea, true).at(30, 30));
        assertSame(mm, ClassicInfoPanel.promptDot(mm, sea, false));
        assertSame(mm, ClassicInfoPanel.promptDot(mm, null, true));

        // Frozen: a jump takes it away; live: it stays on its tile.
        final int[] o = mv.viewOrigin();
        assertTrue(mv.jumpTo(map.getTile(o[0] + 7, o[1] + 11), "test"));
        assertNull(mv.promptTile());
        assertFalse(mv.isPromptShown());
        mv.enterPrompt(sea);
        assertTrue(mv.jumpTo(map.getTile(o[0] + 7, o[1] + 6), "test"));
        assertSame(sea, mv.promptTile());
        // The next activation ends it.
        ship.setMovesLeft(3);
        mv.changeToMoveUnits(ship);
        assertNull(mv.promptTile());
        assertFalse(mv.isPromptArmed());
        mv.enterPrompt(sea);
        mv.changeToTerrain(sea);
        assertNull(mv.promptTile());
        mv.enterPrompt(null);
        assertNull(mv.promptTile());
        mv.dispose();
    }

    /**
     * Build spec W5: after its last move the unit stays on screen and does
     * not blink (landfall 03 section 1); the panel keeps the block from
     * before that move until another unit comes up (W5b); the hand-over
     * asks whether the next unit needs a jump without making it.
     */
    public void testAfterTheLastMove() {
        final Game game = getStandardGame();
        final Map map = new MapBuilder(game).setDimensions(58, 72)
            .setBaseTileType(spec().getTileType("model.tile.ocean"))
            .setExploredByAll(true).build();
        game.changeMap(map);
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final Tile sea = map.getTile(30, 30);
        final ClassicMapViewer mv = new ClassicMapViewer(null, null, null, false);
        final Unit ship = new ServerUnit(game, sea, dutch,
            spec().getUnitType("model.unit.merchantman"));
        mv.setFocus(sea);
        mv.changeToMoveUnits(ship);
        assertTrue(mv.isBlinkArmed());
        mv.blinkToggle(1);
        assertTrue(mv.isBlinkOff());
        // The last move: the re-selection with no moves left stops the
        // clock ON, and a toggle still queued does nothing.
        ship.setMovesLeft(0);
        mv.changeToMoveUnits(ship);
        assertFalse(mv.isBlinkArmed());
        assertFalse(mv.isBlinkOff());
        mv.blinkToggle(1);
        assertFalse(mv.isBlinkOff());
        assertTrue(mv.isShownAt(ship, sea));
        // Skipped (Space) with its moves: no blink either (clip007 #1397).
        ship.setMovesLeft(3);
        mv.changeToMoveUnits(ship);
        assertTrue(mv.isBlinkArmed());
        ship.setState(Unit.UnitState.SKIPPED);
        mv.rearmBlink("skip");
        assertFalse(mv.isBlinkArmed());
        assertFalse(mv.isBlinkOff());
        ship.setState(Unit.UnitState.ACTIVE);
        ship.setMovesLeft(0);

        // The stale block: live while it can move, remembered after.
        assertTrue(ClassicInfoPanel.showsLive(ship, null));
        assertFalse(ClassicInfoPanel.showsLive(ship, ship));
        assertFalse(ClassicInfoPanel.showsLive(null, ship));
        ship.setMovesLeft(3);
        assertTrue(ClassicInfoPanel.showsLive(ship, ship));
        final Unit other = new ServerUnit(game, sea, dutch,
            spec().getUnitType("model.unit.merchantman"));
        other.setMovesLeft(0);
        assertTrue(ClassicInfoPanel.showsLive(other, ship));
        // A landing holds the block as it was (W8b): never live then.
        assertTrue(ClassicInfoPanel.liveBlock(false, other, ship));
        assertFalse(ClassicInfoPanel.liveBlock(true, other, ship));
        assertFalse(ClassicInfoPanel.liveBlock(true, ship, null));

        // The cargo list: the passenger that boarded last on top (W18).
        final UnitType colonistType = spec().getUnitType("model.unit.freeColonist");
        final Unit first = new ServerUnit(game, ship, dutch, colonistType);
        final Unit second = new ServerUnit(game, ship, dutch, colonistType);
        assertEquals(java.util.List.of(first, second), ship.getUnitList());
        assertEquals(java.util.List.of(second, first),
                     ClassicInfoPanel.cargoNewestFirst(ship));

        // The jump question: the origin stays; a tile in the margin or
        // off the view would jump, one inside would not.
        final int[] o = mv.viewOrigin();
        assertFalse(mv.wouldJump(map.getTile(o[0] + 7, o[1] + 6)));
        assertTrue(mv.wouldJump(map.getTile(o[0] + 7, o[1] + 10)));
        assertTrue(java.util.Arrays.equals(o, mv.viewOrigin()));
        assertFalse(mv.wouldJump(null));
        assertTrue(mv.jumpTo(map.getTile(o[0] + 7, o[1] + 10), "test"));
        assertFalse(java.util.Arrays.equals(o, mv.viewOrigin()));
        assertFalse(mv.wouldJump(map.getTile(mv.viewOrigin()[0] + 7,
                                             mv.viewOrigin()[1] + 6)));
        mv.dispose();
    }

    /**
     * M1 acceptance F5 and build spec W19: a foreign move the server
     * announced keeps the unit at its source until its slide starts, also
     * once the model has moved it (no frame of it at its destination
     * before its slide); several queued moves in order; a move that does
     * not slide is taken off too.
     */
    public void testQueuedForeignMoveStaysAtItsSource() {
        final Game game = getStandardGame();
        final Map map = getCoastTestMap(spec().getTileType("model.tile.plains"), true);
        game.changeMap(map);
        final Player inca = game.getPlayerByNationId("model.nation.inca");
        final Tile a = map.getTile(5, 7), b = map.getTile(6, 7), c = map.getTile(7, 7);
        assertTrue(a.isLand() && b.isLand() && c.isLand());
        final ClassicMapViewer mv = new ClassicMapViewer(null, null, null, false);
        final Unit brave = new ServerUnit(game, a, inca,
            spec().getUnitType("model.unit.brave"));
        assertSame(brave, mv.displayUnit(a));
        assertNull(mv.queuedSource(brave));

        // The animation arrives first, then the update moves the unit.
        mv.moveQueued(brave, a, b);
        assertSame(brave, mv.displayUnit(a));
        brave.setLocation(b);
        assertSame(a, mv.queuedSource(brave));
        assertSame("still at its source", brave, mv.displayUnit(a));
        assertNull("not at its new tile before its slide", mv.displayUnit(b));

        // A second move is announced before the first slid: in order.
        mv.moveQueued(brave, b, c);
        brave.setLocation(c);
        assertSame(brave, mv.displayUnit(a));
        assertNull(mv.displayUnit(b));
        assertNull(mv.displayUnit(c));
        mv.moveDequeued(brave, c, a);              // no such move: nothing
        assertSame(a, mv.queuedSource(brave));
        mv.moveDequeued(brave, a, b);              // the first slide starts
        assertSame(b, mv.queuedSource(brave));
        assertNull(mv.displayUnit(a));
        assertSame(brave, mv.displayUnit(b));
        assertNull(mv.displayUnit(c));
        mv.moveDequeued(brave, b, c);
        assertNull(mv.queuedSource(brave));
        assertSame(brave, mv.displayUnit(c));
        assertNull(mv.displayUnit(b));

        // A unit standing where a queued one comes from is drawn there.
        final Unit other = new ServerUnit(game, a, inca,
            spec().getUnitType("model.unit.brave"));
        mv.moveQueued(brave, c, b);
        assertSame(other, mv.displayUnit(a));
        assertSame(brave, mv.displayUnit(c));
        // A move that does not slide (not shown, both tiles unexplored):
        // animateMove takes it off (headless the map is not showing).
        mv.animateMove(brave, c, b);
        assertNull(mv.queuedSource(brave));
        mv.dispose();
    }

    /**
     * Build spec W19: the cue goes only before a native's move whose
     * source fails the view rule (the view jumps for it); never for the
     * player's own or a European's unit, never inside the view.  Its
     * square needs the source cell on the screen.
     */
    public void testNativeCueRule() {
        assertTrue(ClassicMapViewer.needsNativeCue(false, true, true));
        assertFalse(ClassicMapViewer.needsNativeCue(false, true, false));
        assertFalse(ClassicMapViewer.needsNativeCue(false, false, true));
        assertFalse(ClassicMapViewer.needsNativeCue(true, true, true));
        assertFalse(ClassicMapViewer.needsNativeCue(true, false, true));

        final Game game = getStandardGame();
        final Map map = getCoastTestMap(spec().getTileType("model.tile.plains"), true);
        game.changeMap(map);
        final int[] o = { 3, 2 };
        assertTrue(ClassicMapViewer.cellInView(map.getTile(3, 2), o));
        assertTrue(ClassicMapViewer.cellInView(map.getTile(17, 13), o));   // cell (14,11)
        assertFalse(ClassicMapViewer.cellInView(map.getTile(18, 13), o));
        assertFalse(ClassicMapViewer.cellInView(map.getTile(17, 14), o));
        assertFalse(ClassicMapViewer.cellInView(map.getTile(2, 5), o));
        // The timing: 2 capture frames after the final draw (its paints add
        // about one), 250 ms to the jump.
        assertEquals(28.54, ClassicMapViewer.CUE_GAP_MS, 0.01);
        assertEquals(250.0, ClassicMapViewer.CUE_MS);
    }

    /**
     * C FINAL trap 3 (acceptance A3): the TERRAIN mode's cursor is drawn
     * only while the player can use it.  In a turn without a unit the
     * controller selects its fallback tile and the turn ends by itself:
     * the player waits, so no white square; the AI phase and the
     * Spielzugende mode (its own square) draw none either.
     */
    public void testCursorOnlyWhileThePlayerCanUseIt() {
        final Game game = getStandardGame();
        final Map map = new MapBuilder(game).setDimensions(58, 72)
            .setBaseTileType(spec().getTileType("model.tile.ocean"))
            .setExploredByAll(true).build();
        game.changeMap(map);
        final Tile sea = map.getTile(30, 30);
        final boolean[] waiting = { false };
        final boolean[] prompt = { false };
        final ClassicGUI gui = new ClassicGUI(null) {
                @Override
                boolean turnInputBlocked() {
                    return waiting[0];
                }

                @Override
                boolean turnPrompt() {
                    return prompt[0];
                }
            };
        final ClassicMapViewer mv = new ClassicMapViewer(null, gui, null, false);
        mv.setFocus(sea);
        assertFalse(mv.isCursorShown());       // nothing selected
        mv.changeToTerrain(sea);
        assertTrue(mv.isCursorShown());        // the player's own selection
        waiting[0] = true;                     // the automatic end, the AI
        assertFalse(mv.isCursorShown());
        waiting[0] = false;
        prompt[0] = true;                      // the Spielzugende square
        assertFalse(mv.isCursorShown());
        prompt[0] = false;
        assertTrue(mv.isCursorShown());
        mv.changeToEndTurn();
        assertFalse(mv.isCursorShown());
        mv.dispose();

        // Without a GUI (the preview harness): as before.
        final ClassicMapViewer bare = new ClassicMapViewer(null, null, null, false);
        bare.setFocus(sea);
        bare.changeToTerrain(sea);
        assertTrue(bare.isCursorShown());
        bare.dispose();
    }

    /** A server game: an all-ocean 30x30 map, a Dutch ship on (15,15), its 3x3 explored. */
    private static Game seaGame() {
        final Game server = getStandardGame();
        final Map map = new MapBuilder(server).setDimensions(30, 30)
            .setBaseTileType(spec().getTileType("model.tile.ocean")).build();
        server.changeMap(map);
        final net.sf.freecol.server.model.ServerPlayer dutch
            = (net.sf.freecol.server.model.ServerPlayer)server
            .getPlayerByNationId("model.nation.dutch");
        final Unit ship = new ServerUnit(server, map.getTile(15, 15), dutch,
            spec().getUnitType("model.unit.merchantman"));
        assertNotNull(ship);
        // The raw 3x3 in any topology (the square grid's line of sight 1).
        final java.util.List<Tile> seen = new java.util.ArrayList<>();
        for (int y = 14; y <= 16; y++) {
            for (int x = 14; x <= 16; x++) seen.add(map.getTile(x, y));
        }
        dutch.exploreTiles(seen);
        return server;
    }

    /** The colour the layer draws at a native view pixel. */
    private static int rgbAt(ClassicTerrainLayer l, int x, int y) {
        return l.palette().rgb(l.phase(), l.index(x, y));
    }

    /**
     * Critic 5 and 4 (design 10 §6.1, W6a): the layer reads the explored
     * state as shown.  A slide whose model already holds the reveal (a
     * server-pushed move) paints no new tile before its final draw, not
     * even in its 3-px margins; the final draw shows it and paints the
     * panel's minimap in the same pass, once; a unit on a tile the map
     * does not show explored is not drawn.
     */
    public void testTheRevealComesWithTheFinalDraw() throws Exception {
        final Game server = seaGame();
        final Player sDutch = server.getPlayerByNationId("model.nation.dutch");
        final Game client = ClassicTerrainOracleTest.clientView(server, sDutch);
        final Map map = client.getMap();
        final Tile src = map.getTile(15, 15), dst = map.getTile(14, 15);
        final Unit ship = src.getFirstUnit();
        assertNotNull(ship);
        assertTrue(src.isExplored() && dst.isExplored());
        assertFalse(map.getTile(13, 15).isExplored());
        final int[] dots = { 0 };
        final ClassicGUI gui = new ClassicGUI(null) {
                @Override
                void paintBlinkDot() {
                    dots[0]++;
                }
            };
        final ClassicMapViewer mv = new ClassicMapViewer(null, gui, null, false);
        final ClassicTerrainLayer layer = ClassicTerrainLayerTest.layer();
        mv.setFixedScale(1);
        mv.setTerrain(layer, ClassicTerrainOracleTest.oracle(client, true, server));
        final java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
            240, 192, java.awt.image.BufferedImage.TYPE_INT_RGB);
        mv.paintOffscreen(img);
        mv.setFocus(src);
        final int[] o = mv.peekViewOrigin();
        assertEquals(7, 15 - o[0]);
        assertEquals(6, 15 - o[1]);
        // The first paint takes the explored state.
        final java.awt.Graphics2D g = img.createGraphics();
        mv.paintComponent(g);
        g.dispose();
        // (13,15) is cell (5,6): its in-cell pixel (14,4) is dark, its E
        // side the fringe of the explored (14,15).
        final int px = 5 * 16 + 14, py = 6 * 16 + 4;
        final int darkRgb = rgbAt(layer, px, py);
        assertEquals(img.getRGB(px, py) & 0xFFFFFF, darkRgb);
        assertEquals(200 + (4 * 16 + 14) % 3, layer.index(px, py));

        // The server pushes the move W: its reveal (x = 13) is in the
        // model before the slide.
        for (int y = 14; y <= 16; y++) {
            map.getTile(13, y).setType(spec().getTileType("model.tile.ocean"));
        }
        final Tile revealed = map.getTile(13, 15);
        assertTrue(revealed.isExplored());
        assertFalse("not shown yet", mv.shownExplored(revealed));
        final int before = dots[0];
        mv.animateMove(ship, src, dst);
        // The slide's margins reach into (13,15): still dark there.
        assertEquals(darkRgb, img.getRGB(px, py) & 0xFFFFFF);
        assertFalse(mv.shownExplored(revealed));
        // The model runs on: the ship's next move puts it on the revealed
        // tile, which the map does not show explored yet: not drawn there.
        ship.setLocation(revealed);
        assertFalse(mv.isShownAt(ship, revealed));
        // The final draw: the reveal, and the minimap once.
        mv.finalDraw();
        assertTrue(mv.shownExplored(revealed));
        assertTrue(mv.isShownAt(ship, revealed));
        assertEquals(10 * 8 + (14 + 2 * 4) % 8, layer.index(px, py));
        assertEquals(rgbAt(layer, px, py), img.getRGB(px, py) & 0xFFFFFF);
        assertEquals(before + 1, dots[0]);
        mv.finalDraw();   // nothing pending: nothing painted
        assertEquals(before + 1, dots[0]);
        mv.dispose();
    }

    /**
     * The held first slide (R2, W8b), on a fake clock: a unit sent off by
     * the destination list gets its panel painted at the panel's moment
     * (close + 42 ms), then its slide starts at close + 128 ms, once; a
     * dropped hold and a hold more than a second old hold nothing and
     * paint nothing; the landing's hold waits without a panel.
     */
    public void testHeldFirstSlide() throws Exception {
        final Game server = seaGame();
        final Player sDutch = server.getPlayerByNationId("model.nation.dutch");
        final Game client = ClassicTerrainOracleTest.clientView(server, sDutch);
        final Map map = client.getMap();
        final Tile src = map.getTile(15, 15), dst = map.getTile(14, 15);
        final Unit ship = src.getFirstUnit();
        assertNotNull(ship);
        final ClassicMapViewer mv = new ClassicMapViewer(null, new ClassicGUI(null),
                                                         null, false);
        mv.setFixedScale(1);
        mv.setTerrain(ClassicTerrainLayerTest.layer(),
                      ClassicTerrainOracleTest.oracle(client, true, server));
        mv.paintOffscreen(new java.awt.image.BufferedImage(240, 192,
            java.awt.image.BufferedImage.TYPE_INT_RGB));
        mv.setFocus(src);
        final ClassicWaterCycleTest.FakeClock clock
            = new ClassicWaterCycleTest.FakeClock(System.nanoTime());
        mv.setSlideClock(clock);
        final long ms = 1_000_000L;
        final long close = clock.now;
        final long panelDue = close + Math.round(ClassicMapViewer.GOTO_PANEL_MS * ms);
        final long due = close + Math.round(ClassicMapViewer.GOTO_SLIDE_MS * ms);
        assertEquals(close + 42 * ms, panelDue);
        assertEquals(close + 128 * ms, due);
        final List<Long> painted = new ArrayList<>();
        mv.holdFirstSlide(ship, panelDue, () -> painted.add(clock.now), due);
        mv.animateMove(ship, src, dst);
        assertEquals(List.of(panelDue), painted);
        final int ip = clock.waits.indexOf(panelDue);
        assertTrue(ip >= 0);
        assertEquals(due, (long) clock.waits.get(ip + 1));
        // The slide's steps come after the start at the hold's end.
        final List<Long> after = clock.waits.subList(ip + 1, clock.waits.size());
        for (long w : after) assertTrue(w >= due);
        assertTrue(after.contains(due + ClassicSlide.stepNanos(false)));
        mv.finalDraw();

        // Used once: the next slide is not held again.
        int n = clock.waits.size();
        long t = clock.now;
        mv.animateMove(ship, dst, src);
        assertEquals(1, painted.size());
        assertFalse(clock.waits.subList(n, clock.waits.size()).contains(due));
        mv.finalDraw();
        // A dropped hold holds nothing.
        t = clock.now + 500 * ms;
        mv.holdFirstSlide(ship, t, () -> painted.add(clock.now), t + 100 * ms);
        mv.holdFirstSlide(null, 0L, null, 0L);
        n = clock.waits.size();
        mv.animateMove(ship, src, dst);
        assertEquals(1, painted.size());
        assertFalse(clock.waits.subList(n, clock.waits.size()).contains(t + 100 * ms));
        mv.finalDraw();
        // A stale hold (over a second past its start): no wait, no paint.
        t = clock.now - 2000 * ms;
        mv.holdFirstSlide(ship, t, () -> painted.add(clock.now), t + 86 * ms);
        n = clock.waits.size();
        mv.animateMove(ship, dst, src);
        assertEquals(1, painted.size());
        assertFalse(clock.waits.subList(n, clock.waits.size()).contains(t));
        mv.finalDraw();
        // The landing's hold: the wait, no panel.
        t = clock.now + 300 * ms;
        mv.landingSlide(ship, t);
        n = clock.waits.size();
        mv.animateMove(ship, src, dst);
        assertEquals(1, painted.size());
        assertEquals(t, (long) clock.waits.get(clock.waits.indexOf(t)));
        assertTrue(clock.waits.indexOf(t) >= n);
        mv.dispose();
    }

    /**
     * W9: a final draw that shows land as explored tells the GUI (woodcut
     * 1, the sighting: V landfall #2107, fog-start #1042); a reveal of sea
     * does not, nor land in the fog ring around the explored tiles (it is
     * not explored), nor a first paint of a map that already shows land
     * (a loaded game).  The once per game is the GUI's.
     */
    public void testTheLandSightingAtTheFinalDraw() throws Exception {
        final Game server = seaGame();
        final Player sDutch = server.getPlayerByNationId("model.nation.dutch");
        final Game client = ClassicTerrainOracleTest.clientView(server, sDutch);
        final Map map = client.getMap();
        final TileType plains = spec().getTileType("model.tile.plains");
        final Tile src = map.getTile(15, 15), dst = map.getTile(14, 15);
        final Unit ship = src.getFirstUnit();
        // Land in the fog ring: two columns west, not explored.
        server.getMap().getTile(12, 15).setType(plains);
        assertFalse(map.getTile(12, 15).isExplored());
        final List<Long> sighted = new ArrayList<>();
        final ClassicGUI gui = new ClassicGUI(null) {
                @Override
                void landSighted(long finalNanos) {
                    sighted.add(finalNanos);
                }
            };
        final ClassicMapViewer mv = new ClassicMapViewer(null, gui, null, false);
        mv.setFixedScale(1);
        mv.setTerrain(ClassicTerrainLayerTest.layer(),
                      ClassicTerrainOracleTest.oracle(client, true, server));
        final java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
            240, 192, java.awt.image.BufferedImage.TYPE_INT_RGB);
        mv.paintOffscreen(img);
        mv.setFocus(src);
        final java.awt.Graphics2D g = img.createGraphics();
        mv.paintComponent(g);
        g.dispose();
        // A sea reveal: nothing.
        for (int y = 14; y <= 16; y++) map.getTile(13, y).setType(spec().getTileType("model.tile.ocean"));
        mv.animateMove(ship, src, dst);
        mv.finalDraw();
        assertTrue(sighted.isEmpty());
        assertTrue(mv.lastFinalNanos() != 0L);
        // Land explored by the next move: the GUI hears of it, with the
        // final draw's time.
        map.getTile(12, 15).setType(plains);
        for (int y = 14; y <= 16; y += 2) map.getTile(12, y).setType(spec().getTileType("model.tile.ocean"));
        ship.setLocation(dst);
        mv.animateMove(ship, dst, map.getTile(13, 15));
        mv.finalDraw();
        assertEquals(1, sighted.size());
        assertEquals(mv.lastFinalNanos(), (long) sighted.get(0));
        assertTrue(ClassicMapViewer.revealsLand(map, bits(map, 12, 15)));
        assertFalse(ClassicMapViewer.revealsLand(map, bits(map, 13, 15)));
        assertFalse(ClassicMapViewer.revealsLand(null, bits(map, 12, 15)));
        mv.dispose();
        // A map that shows land from its first paint (a loaded game): no sighting.
        final ClassicMapViewer mv2 = new ClassicMapViewer(null, gui, null, false);
        mv2.setFixedScale(1);
        mv2.setTerrain(ClassicTerrainLayerTest.layer(),
                       ClassicTerrainOracleTest.oracle(client, true, server));
        mv2.paintOffscreen(img);
        mv2.setFocus(map.getTile(13, 15));
        final java.awt.Graphics2D g2 = img.createGraphics();
        mv2.paintComponent(g2);
        g2.dispose();
        assertEquals(1, sighted.size());
        mv2.dispose();
    }

    /** The bit of one tile in a map's tile set. */
    private static java.util.BitSet bits(Map map, int x, int y) {
        final java.util.BitSet b = new java.util.BitSet();
        b.set(y * map.getWidth() + x);
        return b;
    }

    /**
     * Without the pack's index sheets (and in the adaptive layout) the map
     * draws the RGBA fallback: an unexplored tile flat in the dark sea's
     * colour, VICEROY's index 61 (design 10 §4).
     */
    public void testFallbackDrawsUnexploredDark() {
        final Game server = seaGame();
        final Game client = ClassicTerrainOracleTest.clientView(server,
            server.getPlayerByNationId("model.nation.dutch"));
        final Map map = client.getMap();
        final ClassicMapViewer mv = new ClassicMapViewer(null, null, null, false);
        mv.setFixedScale(1);
        final java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
            240, 192, java.awt.image.BufferedImage.TYPE_INT_RGB);
        mv.paintOffscreen(img);
        mv.setFocus(map.getTile(15, 15));
        assertNull(mv.terrainLayer());
        final java.awt.Graphics2D g = img.createGraphics();
        mv.paintComponent(g);
        g.dispose();
        assertEquals(0x181C7D, img.getRGB(8, 8) & 0xFFFFFF);   // (8,9): dark
        assertEquals(0x181C7D, ClassicMapViewer.DARK_SEA.getRGB() & 0xFFFFFF);
        mv.dispose();
    }

    /**
     * A server game for the water cycle: {@link #seaGame} with a sea lane
     * (high seas) at (16,14..16) and at the view's corners (8,9) and
     * (22,20), all explored, so the cycling cells span the whole view of
     * the ship at (15,15).
     */
    private static Game laneGame() {
        final Game server = seaGame();
        final Map map = server.getMap();
        final net.sf.freecol.common.model.TileType lane
            = spec().getTileType("model.tile.highSeas");
        final java.util.List<Tile> seen = new java.util.ArrayList<>();
        for (int[] xy : new int[][] { { 16, 14 }, { 16, 15 }, { 16, 16 }, { 8, 9 },
                                      { 22, 20 } }) {
            final Tile t = map.getTile(xy[0], xy[1]);
            t.setType(lane);
            seen.add(t);
        }
        ((net.sf.freecol.server.model.ServerPlayer)server
            .getPlayerByNationId("model.nation.dutch")).exploreTiles(seen);
        return server;
    }

    /**
     * The water cycle on the map (M1c design 10 §7.2/§7.3, W6c; Critic 3):
     * a palette step due in the middle of a slide is painted at its own
     * deadline, between two slide steps, with the new colours at once; a
     * step while the slide's final draw is due paints that final draw (the
     * whole map, the minimap once); any other step paints the cycling
     * cells silently -- no explored state taken, no change told to the turn
     * flow, a pending change kept.
     */
    public void testPaletteSteps() throws Exception {
        final Game server = laneGame();
        final Player sDutch = server.getPlayerByNationId("model.nation.dutch");
        final Game client = ClassicTerrainOracleTest.clientView(server, sDutch);
        final Map map = client.getMap();
        final Tile src = map.getTile(15, 15), dst = map.getTile(14, 15);
        final Unit ship = src.getFirstUnit();
        assertNotNull(ship);
        final int[] dots = { 0 }, changes = { 0 };
        final ClassicGUI gui = new ClassicGUI(null) {
                @Override
                void paintBlinkDot() {
                    dots[0]++;
                }

                @Override
                void screenChanged() {
                    changes[0]++;
                }
            };
        final ClassicMapViewer mv = new ClassicMapViewer(null, gui, null, false);
        final ClassicTerrainLayer layer = ClassicTerrainLayerTest.layer();
        mv.setFixedScale(1);
        mv.setTerrain(layer, ClassicTerrainOracleTest.oracle(client, true, server));
        final java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
            240, 192, java.awt.image.BufferedImage.TYPE_INT_RGB);
        mv.paintOffscreen(img);
        mv.setFocus(src);
        assertEquals(-1, new ClassicMapViewer(null, null, null, false).paintedPhase());
        java.awt.Graphics2D g = img.createGraphics();
        mv.paintComponent(g);
        g.dispose();
        assertEquals(0, mv.paintedPhase());
        assertSame(layer.palette(), mv.gamePalette());
        // The sea lane (16,14) is cell (8,5); its offset 35 = (3,2), in no
        // mask, is a sparkle: 123.
        final int lx = 8 * 16 + 3, ly = 5 * 16 + 2;
        assertEquals(123, layer.index(lx, ly));
        assertEquals(layer.palette().rgb(0, 123), img.getRGB(lx, ly) & 0xFFFFFF);
        // The whole view cycles: the lane at both corners.
        assertTrue(java.util.Arrays.equals(new int[] { 0, 0, 15, 12 }, layer.cyclingCells()));
        final byte[] hint = new byte[240 * 192];
        assertTrue(mv.indexHint(hint));
        assertEquals(123, hint[ly * 240 + lx] & 0xFF);
        assertEquals(0, hint[1] & 0xFF);

        // The cycle on a fake clock: step 1 is due 100 ms into the slide.
        final ClassicWaterCycleTest.FakeClock clock
            = new ClassicWaterCycleTest.FakeClock(System.nanoTime());
        final java.util.List<long[]> seen = new java.util.ArrayList<>();
        final java.util.List<Runnable> posts = new java.util.ArrayList<>();
        final ClassicWaterCycle cycle = new ClassicWaterCycle(clock, posts::add,
            () -> true, null, (p, k, due, sv) -> {
                seen.add(new long[] { clock.now, p, k, sv ? 1 : 0 });
                mv.paletteStep(p, k, due, sv);
            }, null, false);
        mv.setSlideClock(cycle.servicing(clock));
        final long period = Math.round(ClassicGamePalette.PERIOD_MS * 1_000_000L);
        final long t1 = clock.now;
        clock.now = t1 - period + 100_000_000L;
        cycle.start();
        clock.now = t1;
        mv.animateMove(ship, src, dst);
        assertEquals(1, seen.size());
        assertEquals("at its deadline", t1 + 100_000_000L, seen.get(0)[0]);
        assertEquals(1, seen.get(0)[1]);
        assertEquals(1, seen.get(0)[3]);
        // Between offsets 7 and 8: the waits around it are slide deadlines.
        final long s = ClassicSlide.stepNanos(false);
        final int w = clock.waits.indexOf(t1 + 100_000_000L);
        assertTrue(w > 0);
        assertEquals(t1 + 6 * s, (long) clock.waits.get(w - 1));
        assertEquals(t1 + 7 * s, (long) clock.waits.get(w + 1));
        assertEquals(t1 + 302_020_000L, clock.now);
        // The new colours were painted at once, outside the slide's cells.
        assertEquals(1, layer.phase());
        assertEquals(1, mv.paintedPhase());
        assertEquals(layer.palette().rgb(1, 123), img.getRGB(lx, ly) & 0xFFFFFF);
        assertFalse(layer.palette().rgb(0, 123) == layer.palette().rgb(1, 123));

        // Step 2 while the slide's final draw is due: it is that final
        // draw (Critic 3), the minimap once; then nothing is pending.
        final int dots0 = dots[0], changes0 = changes[0];
        ship.setLocation(dst);   // the model has the move
        clock.now = t1 - period + 100_000_000L + 2 * period;
        assertTrue(cycle.serviceDue());
        assertEquals(2, layer.phase());
        assertEquals(dots0 + 1, dots[0]);
        assertEquals(changes0 + 1, changes[0]);
        mv.finalDraw();
        assertEquals("nothing pending", dots0 + 1, dots[0]);
        assertTrue(mv.isShownAt(ship, dst));

        // Step 3 with nothing pending: the cycling cells -- the whole view
        // here -- silently.  A reveal the model already has stays dark, a
        // pending change stays pending.
        final Tile revealed = map.getTile(13, 15);
        assertFalse(revealed.isExplored());
        for (int y = 14; y <= 16; y++) {
            map.getTile(13, y).setType(spec().getTileType("model.tile.ocean"));
        }
        assertTrue(revealed.isExplored());
        assertFalse(mv.shownExplored(revealed));
        mv.changeToEndTurn();
        mv.setBlinkOff(true);   // no active unit: a change to show, no paint
        final int changes1 = changes[0];
        final int px = 5 * 16 + 8, py = 6 * 16 + 8;   // (13,15) = cell (5,6)
        final int dark = img.getRGB(px, py);
        clock.now += period;
        assertTrue(cycle.serviceDue());
        assertEquals(3, layer.phase());
        assertEquals(layer.palette().rgb(3, 123), img.getRGB(lx, ly) & 0xFFFFFF);
        assertEquals("silent", changes1, changes[0]);
        assertFalse("no explored state taken", mv.shownExplored(revealed));
        assertEquals(dark, img.getRGB(px, py));
        // The next ordinary paint shows the reveal and the pending change.
        g = img.createGraphics();
        mv.paintComponent(g);
        g.dispose();
        assertTrue(mv.shownExplored(revealed));
        assertEquals(changes1 + 1, changes[0]);
        // The thread's posts of the steps the waits fired are stale.
        for (Runnable r : posts) r.run();
        assertEquals(3, seen.size());
        mv.dispose();
        cycle.close();
    }

    /**
     * Nothing cycles in view: a palette step paints nothing, and the screen
     * counts as shown at the new phase (every phase looks alike there).
     */
    public void testPaletteStepWithNothingCycling() {
        final Game server = seaGame();
        final Game client = ClassicTerrainOracleTest.clientView(server,
            server.getPlayerByNationId("model.nation.dutch"));
        final ClassicMapViewer mv = new ClassicMapViewer(null, null, null, false);
        final ClassicTerrainLayer layer = ClassicTerrainLayerTest.layer();
        mv.setFixedScale(1);
        mv.setTerrain(layer, ClassicTerrainOracleTest.oracle(client, true, server));
        final java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
            240, 192, java.awt.image.BufferedImage.TYPE_INT_RGB);
        mv.paintOffscreen(img);
        mv.setFocus(client.getMap().getTile(15, 15));
        final java.awt.Graphics2D g = img.createGraphics();
        mv.paintComponent(g);
        g.dispose();
        assertNull(layer.cyclingCells());
        final int[] before = img.getRGB(0, 0, 240, 192, null, 0, 240);
        mv.paletteStep(5, 5, System.nanoTime(), false);
        assertEquals(5, layer.phase());
        assertEquals(5, mv.paintedPhase());
        assertTrue(java.util.Arrays.equals(before, img.getRGB(0, 0, 240, 192, null, 0, 240)));
        // Without a layer: nothing at all.
        final ClassicMapViewer bare = new ClassicMapViewer(null, null, null, false);
        bare.paletteStep(3, 3, 0L, false);
        assertEquals(-1, bare.paintedPhase());
        assertFalse(bare.indexHint(new byte[240 * 192]));
        assertNull(bare.gamePalette());
        mv.dispose();
        bare.dispose();
    }

    /**
     * G1 (N15): a click on a native village centres the view and then,
     * posted (the click is a press), shows the village's notice; a foreign
     * colony is only centred; the Spielzugende mode ignores both.
     */
    public void testClickOnAVillage() throws Exception {
        final Game game = getStandardGame();
        final Map map = getTestMap(true);
        game.changeMap(map);
        final Player french = game.getPlayerByNationId("model.nation.french");
        final net.sf.freecol.common.model.IndianSettlement is
            = new IndianSettlementBuilder(game)
            .player(game.getPlayerByNationId("model.nation.arawak"))
            .settlementTile(map.getTile(5, 8)).build();
        final net.sf.freecol.common.model.Colony colony
            = createStandardColony(1, 15, 10);           // the Dutch: foreign
        assertFalse(french.owns(colony));
        final java.util.List<String> log = new java.util.ArrayList<>();
        final boolean[] prompt = { false };
        final ClassicGUI gui = new ClassicGUI(null) {
                @Override
                public void setFocus(Tile t) {
                    log.add("focus " + t.getX() + "," + t.getY());
                }

                @Override
                public net.sf.freecol.client.gui.panel.FreeColPanel
                    showIndianSettlementPanel(
                        net.sf.freecol.common.model.IndianSettlement s) {
                    log.add("village " + s.getName());
                    return null;
                }

                @Override
                boolean turnPrompt() {
                    return prompt[0];
                }
            };
        final ClassicMapViewer mv = new ClassicMapViewer(null, gui, null, false);
        try {
            mv.clickOn(is.getTile(), french);
            assertEquals(java.util.List.of("focus 5,8"), log);   // posted
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
            assertEquals(java.util.List.of("focus 5,8", "village " + is.getName()), log);
            log.clear();
            mv.clickOn(colony.getTile(), french);
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
            assertEquals(java.util.List.of("focus 15,10"), log);
            log.clear();
            prompt[0] = true;
            mv.clickOn(is.getTile(), french);
            mv.clickOn(colony.getTile(), french);
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
            assertTrue(log.toString(), log.isEmpty());
        } finally {
            mv.dispose();
        }
    }

    /** The road frames ({@code PHYS0} 80 the hub, 81-88 the spokes) a tile shows. */
    private static List<Integer> roadFrames(Map map, Tile t) {
        final List<Integer> out = new java.util.ArrayList<>();
        ClassicTileArt.overlayFrames(map, t, ClassicTileArt.modelTypes(map), f -> {
                if (f >= ClassicTileArt.ROAD_HUB && f <= ClassicTileArt.ROAD_HUB + 8) out.add(f);
            });
        return out;
    }

    /**
     * W5f: a road under construction is never drawn (clip008 #45293: the
     * tile and its neighbour unchanged), a complete one with a spoke into
     * each neighbour with a complete road; a road completed at the turn
     * start only from its builder's visit on (c6 #3447 -&gt; #3450: the hub
     * and the spokes appear with the completion).  The visited unit is
     * drawn on top of its tile until the next activation.
     */
    public void testRoadsAndTheVisit() {
        final Game game = getStandardGame();
        final Map map = getCoastTestMap(spec().getTileType("model.tile.plains"), true);
        game.changeMap(map);
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final UnitType colonistType = spec().getUnitType("model.unit.freeColonist");
        final Tile a = map.getTile(3, 3), b = map.getTile(4, 3);
        final Unit other = new ServerUnit(game, a, dutch, colonistType);
        final Unit pioneer = new ServerUnit(game, a, dutch, colonistType);
        final net.sf.freecol.common.model.TileImprovement ra = a.addRoad();
        final net.sf.freecol.common.model.TileImprovement rb = b.addRoad();
        assertEquals(List.of(), roadFrames(map, a));
        assertEquals(List.of(), roadFrames(map, b));
        rb.setTurnsToComplete(0);
        assertEquals(List.of(ClassicTileArt.ROAD_HUB), roadFrames(map, b));   // no spoke to a
        pioneer.setWorkImprovement(ra);
        pioneer.setState(Unit.UnitState.IMPROVING);

        final ClassicUnitCycle cycle = new ClassicUnitCycle();
        ClassicUnitCycle.show(cycle);
        final ClassicMapViewer mv = new ClassicMapViewer(null, null, null, false);
        try {
            cycle.snapshot(dutch);
            ra.setTurnsToComplete(0);                    // FreeCol's turn start
            pioneer.setState(Unit.UnitState.ACTIVE);
            pioneer.setMovesLeft(0);
            cycle.turnStarted(dutch);
            assertEquals(List.of(), roadFrames(map, a));                        // held
            assertEquals(List.of(ClassicTileArt.ROAD_HUB), roadFrames(map, b));
            assertSame(other, mv.displayUnit(a));
            mv.setFocus(a);
            mv.visit(pioneer);
            assertSame(pioneer, mv.displayUnit(a));      // on top from the jump on
            assertEquals(List.of(), roadFrames(map, a)); // the completion comes next
            cycle.visited(pioneer);
            mv.visitShown(pioneer);
            assertEquals(2, roadFrames(map, a).size());  // hub + the spoke to b
            assertEquals(2, roadFrames(map, b).size());
            assertEquals(ClassicTileArt.ROAD_HUB, (int) roadFrames(map, a).get(0));
            mv.changeToMoveUnits(other);
            assertSame(other, mv.displayUnit(a));
            mv.changeToEndTurn();
            assertSame(other, mv.displayUnit(a));
        } finally {
            ClassicUnitCycle.show(null);
            mv.dispose();
        }
        // Without a game view's cycle: complete roads only, as FreeCol's map.
        assertEquals(2, roadFrames(map, a).size());
        ra.setTurnsToComplete(3);
        assertEquals(List.of(ClassicTileArt.ROAD_HUB), roadFrames(map, b));
    }
}
