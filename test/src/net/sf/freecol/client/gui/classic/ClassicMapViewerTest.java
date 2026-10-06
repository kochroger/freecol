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

import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Tile;
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

        // The passenger active: drawn instead of its ship, with the stack
        // marker (W18's ON state).
        mv.changeToMoveUnits(passenger);
        assertSame(passenger, mv.displayUnit(sea));
        assertSame(ClassicHud.STACK_MARKER, mv.markerOf(passenger, sea));
        assertTrue(mv.isShownAt(passenger, sea));

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

        // Activation: ON, armed.
        mv.changeToMoveUnits(ship);
        assertTrue(mv.isBlinkArmed());
        assertFalse(mv.isBlinkOff());
        assertTrue(mv.isShownAt(ship, sea));

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

        // The passenger active (W18's case, landfall #13140): OFF shows the
        // bare sea -- the carrier goes with it.
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
}
