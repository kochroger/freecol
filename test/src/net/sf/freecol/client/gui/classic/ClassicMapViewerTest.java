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

        final ClassicMapViewer mv = new ClassicMapViewer(null, null, null);
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
}
