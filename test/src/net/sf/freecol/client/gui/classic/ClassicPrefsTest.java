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
                                   "moveAccelerator", "endTurnPrompt", "waterCycling",
                                   "buildingLabels", "goodsTerrainLabels",
                                   "reportTrained", "reportFoodShortage",
                                   "reportRawMaterialShortage", "reportToolsNeeded",
                                   "reportBadGovernment", "reportNewGoods",
                                   "reportSonsOfLiberty", "reportRebelMajority"),
                     new ArrayList<>(ClassicPrefs.DEFAULTS.keySet()));
        // The colony report options: all on (I, no clip shows the box).
        for (String k : ClassicPrefs.COLONY_ROWS) assertTrue(k, p.is(k));
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

    /**
     * The two boxes' rows in GAME.TXT's order: every key is a classic pref
     * or a FreeCol option row, each once.
     */
    public void testTheBoxesRows() {
        assertEquals(Arrays.asList("showNativeMoves", "showEuropeanMoves",
                                   "moveAccelerator", "endTurnPrompt", "autoSave",
                                   "combatAnalysis", "waterCycling", "tutorTips"),
                     ClassicPrefs.GAME_ROWS);
        assertEquals(10, ClassicPrefs.COLONY_ROWS.size());
        final java.util.Set<String> seen = new java.util.HashSet<>();
        for (String k : ClassicPrefs.GAME_ROWS) {
            assertTrue(k, ClassicPrefs.isKnown(k) || ClassicPrefs.CLIENT_OPTIONS.containsKey(k));
            assertTrue(k, seen.add(k));
        }
        for (String k : ClassicPrefs.COLONY_ROWS) {
            assertTrue(k, ClassicPrefs.isKnown(k));
            assertTrue(k, seen.add(k));
        }
        assertEquals(ClassicPrefs.DEFAULTS.size() + ClassicPrefs.CLIENT_OPTIONS.size(),
                     seen.size());
    }

    /**
     * What the box sets for a FreeCol option row is remembered in the file
     * (null before), and only for those rows.
     */
    public void testFreeColRowsAreRemembered() throws Exception {
        final File f = File.createTempFile("classic-options", ".properties");
        f.deleteOnExit();
        assertTrue(f.delete());
        final ClassicPrefs p = new ClassicPrefs(f);
        assertNull(p.remembered(ClassicPrefs.AUTO_SAVE));
        p.remember(ClassicPrefs.AUTO_SAVE, false);
        p.remember(ClassicPrefs.TUTOR_TIPS, true);
        final ClassicPrefs q = new ClassicPrefs(f);
        assertEquals(Boolean.FALSE, q.remembered(ClassicPrefs.AUTO_SAVE));
        assertEquals(Boolean.TRUE, q.remembered(ClassicPrefs.TUTOR_TIPS));
        assertNull(q.remembered(ClassicPrefs.COMBAT_ANALYSIS));
        try {
            p.remember(ClassicPrefs.WATER_CYCLING, true);
            fail();
        } catch (IllegalArgumentException e) {
            // expected: a classic pref, set()
        }
        try {
            p.remembered("nope");
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * A report row off holds back exactly its notices; the founding
     * fathers (SONS_OF_LIBERTY too) and unknown ids are always shown.
     */
    public void testReportRows() {
        final ClassicPrefs p = new ClassicPrefs(null);
        for (String id : ClassicPrefs.REPORTS.keySet()) assertTrue(id, p.showsReport(id));
        assertTrue(p.showsReport(null));
        p.set(ClassicPrefs.REPORT_FOOD, false);
        assertFalse(p.showsReport("model.colony.famineFeared"));
        assertTrue(p.showsReport("model.colony.colonistStarved"));
        assertTrue(p.showsReport("model.building.unitEducated"));
        p.set(ClassicPrefs.REPORT_SONS_OF_LIBERTY, false);
        assertFalse(p.showsReport("model.colony.soLIncrease"));
        assertFalse(p.showsReport("model.colony.soLDecrease"));
        assertTrue(p.showsReport("model.player.foundingFatherJoinedCongress"));
        assertTrue(p.showsReport("model.player.soLIncrease"));
        assertTrue(p.showsReport("model.colony.veryGoodGovernment"));
        // Every report pref is a row of the colony box, and every report
        // row but the two label rows switches a notice.
        final java.util.Set<String> used = new java.util.HashSet<>(ClassicPrefs.REPORTS.values());
        for (String k : used) assertTrue(k, ClassicPrefs.COLONY_ROWS.contains(k));
        assertEquals(ClassicPrefs.COLONY_ROWS.size() - 2, used.size());
        assertFalse(used.contains(ClassicPrefs.BUILDING_LABELS));
        assertFalse(used.contains(ClassicPrefs.GOODS_TERRAIN_LABELS));
    }
}
