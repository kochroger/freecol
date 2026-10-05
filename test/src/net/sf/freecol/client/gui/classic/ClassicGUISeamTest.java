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

import net.sf.freecol.client.gui.panel.FreeColPanel;

import junit.framework.TestCase;


/**
 * Tests of {@link ClassicGUI} seams whose base {@code GUI} no-op broke a
 * controller flow (build spec W0a/W0b): the pre-combat question must let
 * the attack through, and the event panel must not be null.
 */
public class ClassicGUISeamTest extends TestCase {

    /**
     * {@code GUI.confirmPreCombat} asks this whenever the default-on
     * option {@code guiShowPreCombat} is set; false cancelled every attack.
     */
    public void testPreCombatLetsTheAttackThrough() {
        final ClassicGUI gui = new ClassicGUI(null);
        assertTrue(gui.showPreCombatDialog(null, null, null));
    }

    /**
     * {@code InGameController.newLandName} adds a closing callback to the
     * event panel; it must get a panel, and the callback must run (nothing
     * is shown, so nothing will close later).
     */
    public void testEventPanelRunsClosingCallbacks() {
        final ClassicGUI gui = new ClassicGUI(null);
        final FreeColPanel panel = gui.showEventPanel("header",
            "image.flavor.event.firstLanding", null);
        assertNotNull(panel);
        final int[] runs = { 0 };
        assertSame(panel, panel.addClosingCallback(() -> runs[0]++));
        assertEquals(1, runs[0]);
        panel.addClosingCallback(null);
        assertEquals(1, runs[0]);
    }
}
