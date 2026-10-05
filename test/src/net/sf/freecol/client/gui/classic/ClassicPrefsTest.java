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

import java.io.File;
import java.util.Arrays;
import java.util.ArrayList;

import junit.framework.TestCase;


/**
 * Headless tests of the classic prefs ({@link ClassicPrefs}): the
 * original's defaults (landing-slow clip #395, state A) and the file.
 */
public class ClassicPrefsTest extends TestCase {

    public void testTheOriginalsDefaults() {
        final ClassicPrefs p = new ClassicPrefs(null);
        assertTrue(p.is(ClassicPrefs.SHOW_NATIVE_MOVES));
        assertTrue(p.is(ClassicPrefs.SHOW_EUROPEAN_MOVES));
        assertFalse(p.is(ClassicPrefs.MOVE_ACCELERATOR));
        assertFalse(p.is(ClassicPrefs.END_TURN_PROMPT));
        assertTrue(p.is(ClassicPrefs.WATER_CYCLING));
        assertEquals(Arrays.asList("showNativeMoves", "showEuropeanMoves",
                                   "moveAccelerator", "endTurnPrompt", "waterCycling"),
                     new ArrayList<>(ClassicPrefs.DEFAULTS.keySet()));
        assertEquals(Arrays.asList("autoSave", "combatAnalysis", "tutorTips"),
                     new ArrayList<>(ClassicPrefs.CLIENT_OPTIONS.keySet()));
        assertFalse(ClassicPrefs.isKnown("autoSave"));
        try {
            p.is("nope");
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            p.set("autoSave", true);
            fail();
        } catch (IllegalArgumentException e) {
            // expected: a FreeCol option, not a classic pref
        }
    }

    public void testValuesAreStoredInTheFile() throws Exception {
        final File f = File.createTempFile("classic-options", ".properties");
        f.deleteOnExit();
        assertTrue(f.delete());
        final ClassicPrefs p = new ClassicPrefs(f);
        assertFalse(p.is(ClassicPrefs.MOVE_ACCELERATOR));
        p.set(ClassicPrefs.MOVE_ACCELERATOR, true);
        p.set(ClassicPrefs.WATER_CYCLING, false);
        assertTrue(p.is(ClassicPrefs.MOVE_ACCELERATOR));
        assertTrue(f.isFile());
        final ClassicPrefs q = new ClassicPrefs(f);
        assertTrue(q.is(ClassicPrefs.MOVE_ACCELERATOR));
        assertFalse(q.is(ClassicPrefs.WATER_CYCLING));
        assertFalse(q.is(ClassicPrefs.END_TURN_PROMPT));   // never set: default
        // In memory only: nothing written, still settable.
        final ClassicPrefs m = new ClassicPrefs(null);
        m.set(ClassicPrefs.END_TURN_PROMPT, true);
        assertTrue(m.is(ClassicPrefs.END_TURN_PROMPT));
    }
}
