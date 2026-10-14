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

import net.sf.freecol.FreeCol;
import net.sf.freecol.common.io.FreeColRules;
import net.sf.freecol.common.model.OriginalWorld;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Topology;
import net.sf.freecol.common.option.MapGeneratorOptions;
import net.sf.freecol.common.option.OptionGroup;
import net.sf.freecol.common.option.OriginalWorldSettings;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * A new game of the Classic UI is made in the original's New World of
 * the setup's answers (parts P1, P3): the title menu's «Neue Welt» in
 * the original's small world (LANDMASSE Klein, LANDFORM Inselgruppe),
 * «Individualisieren»'s answers as they are given, on the topology of
 * the new game.
 */
public class ClassicWorldSetupTest extends FreeColTestCase {

    private static Specification levi() {
        return FreeCol.loadSpecification(FreeColRules.getFreeColRulesFile("levi"),
            null, "model.difficulty.veryEasy");
    }

    /**
     * The title menu's «Neue Welt» (the chain's setup, and FreeCol's
     * defaults when a pack lacks the chain) is the original's «Neue
     * Welt»: Klein and Inselgruppe (P3; P1 had everything Normal while
     * «Individualisieren» had no screen).
     */
    public void testNewWorldIsTheOriginalsSmallWorld() {
        final Topology saved = Topology.current();
        try {
            assertEquals(OriginalWorldSettings.ORIGINAL_NEW_WORLD,
                new ClassicGUI.NewWorldSetup("model.difficulty.veryEasy",
                    "model.nation.dutch", "Levi").world);
            assertEquals(OriginalWorldSettings.ORIGINAL_NEW_WORLD,
                ClassicGUI.NewWorldSetup.defaults().world);
            final Specification spec = levi();
            final OptionGroup mgo = spec.getMapGeneratorOptions();
            // Whatever was set before, the setup's answers count.
            mgo.setInteger(MapGeneratorOptions.LAND_GENERATOR_TYPE, 0);
            mgo.setInteger(MapGeneratorOptions.ORIGINAL_LAND_MASS, 2);
            ClassicGUI.prepareNewWorldMap(spec, ClassicGUI.NewWorldSetup.defaults());
            assertTrue(OriginalWorld.isOriginal(mgo));
            assertEquals("KIgn", OriginalWorldSettings.of(mgo).getCode());
            assertEquals(Topology.forNewGame(Topology.SQUARE), Topology.current());
            if (Topology.current() == Topology.SQUARE) {
                assertEquals(58, mgo.getInteger(MapGeneratorOptions.MAP_WIDTH));
                assertEquals(72, mgo.getInteger(MapGeneratorOptions.MAP_HEIGHT));
            }
        } finally {
            Topology.setCurrent(saved);
        }
    }

    /**
     * The levi rules without the original's world (a program on older
     * data, the final fixer of part P): the new game gets FreeCol's map,
     * and a WARNING says so, not only an INFO line; other rules than the
     * Classic UI's own stay quiet.
     */
    public void testOlderDataSaysSo() {
        final java.util.List<String> warnings = new java.util.ArrayList<>();
        final java.util.logging.Logger log = java.util.logging.Logger
            .getLogger(ClassicGUI.class.getName());
        final java.util.logging.Handler h = new java.util.logging.Handler() {
                @Override public void publish(java.util.logging.LogRecord r) {
                    if (r.getLevel().intValue()
                        >= java.util.logging.Level.WARNING.intValue()) {
                        warnings.add(r.getMessage());
                    }
                }
                @Override public void flush() {}
                @Override public void close() {}
            };
        final Topology saved = Topology.current();
        log.addHandler(h);
        try {
            Specification spec = levi();
            ClassicGUI.prepareNewWorldMap(spec, ClassicGUI.NewWorldSetup.defaults());
            assertTrue(warnings.toString(), warnings.isEmpty());

            spec = levi();
            final OptionGroup mgo = spec.getMapGeneratorOptions();
            assertNotNull(mgo.remove(MapGeneratorOptions.ORIGINAL_LAND_MASS));
            assertNotNull(mgo.remove(MapGeneratorOptions.ORIGINAL_LAND_FORM));
            ClassicGUI.prepareNewWorldMap(spec, new ClassicGUI.NewWorldSetup(
                "model.difficulty.veryEasy", "model.nation.dutch", "Levi",
                OriginalWorldSettings.ofCode("GCwf")));
            assertEquals(warnings.toString(), 1, warnings.size());
            assertTrue(warnings.get(0), warnings.get(0).contains(
                "levi rules have no original world"));
            assertTrue(warnings.get(0), warnings.get(0).contains("(GCwf)"));

            warnings.clear();
            spec = FreeCol.loadSpecification(FreeColRules.getFreeColRulesFile("classic"),
                null, "model.difficulty.veryEasy");
            ClassicGUI.prepareNewWorldMap(spec, ClassicGUI.NewWorldSetup.defaults());
            assertTrue(warnings.toString(), warnings.isEmpty());
        } finally {
            log.removeHandler(h);
            Topology.setCurrent(saved);
        }
    }

    /**
     * Other answers (as «Individualisieren» gives them) reach the
     * new game's options unchanged.
     */
    public void testTheAnswersReachTheGame() {
        final Topology saved = Topology.current();
        try {
            for (String code : new String[] { "GCwf", "KIkt", "NIgn" }) {
                final OriginalWorldSettings w = OriginalWorldSettings.ofCode(code);
                final Specification spec = levi();
                ClassicGUI.prepareNewWorldMap(spec, new ClassicGUI.NewWorldSetup(
                    "model.difficulty.veryEasy", "model.nation.dutch", "Levi", w));
                assertEquals(code, w,
                    OriginalWorldSettings.of(spec.getMapGeneratorOptions()));
            }
        } finally {
            Topology.setCurrent(saved);
        }
    }
}
