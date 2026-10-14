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

package net.sf.freecol.common.option;

import net.sf.freecol.FreeCol;
import net.sf.freecol.common.io.FreeColRules;
import net.sf.freecol.common.model.OriginalWorld;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * The original's four world questions as map generator options of the
 * "levi" rules (part P1).
 */
public class OriginalWorldSettingsTest extends FreeColTestCase {

    private static OptionGroup mapOptions(String rules) {
        return FreeCol.loadSpecification(
            FreeColRules.getFreeColRulesFile(rules), null,
            "model.difficulty.veryEasy").getMapGeneratorOptions();
    }

    /**
     * Every set of answers becomes the options of the original's world
     * and is read back from them: LANDMASSE and LANDFORM as their
     * rows, TEMPERATUR Kühl / Gemäßigt / Warm as chilly / temperate /
     * warm, KLIMA Trocken / Normal / Feucht as dry / normal / wet.
     */
    public void testEveryAnswerBecomesTheOptions() {
        final OptionGroup mgo = mapOptions("levi");
        final int[] temperatures = { 1, 2, 3 }, humidities = { 35, 40, 55 };
        int n = 0;
        for (int m = 0; m < 3; m++) for (int f = 0; f < 3; f++)
        for (int t = 0; t < 3; t++) for (int c = 0; c < 3; c++) {
            final OriginalWorldSettings w = new OriginalWorldSettings(m, f, t, c);
            mgo.setInteger(MapGeneratorOptions.LAND_GENERATOR_TYPE,
                           MapGeneratorOptions.LAND_GENERATOR_CLASSIC);
            assertTrue(w.toString(), w.apply(mgo));
            assertEquals(w.toString(), 4,
                mgo.getSelection(MapGeneratorOptions.LAND_GENERATOR_TYPE));
            assertTrue(w.toString(), OriginalWorld.isOriginal(mgo));
            assertEquals(w.toString(), m, OriginalWorld.getLandMass(mgo));
            assertEquals(w.toString(), f, OriginalWorld.getLandForm(mgo));
            assertEquals(w.toString(), temperatures[t],
                mgo.getRange(MapGeneratorOptions.TEMPERATURE));
            assertEquals(w.toString(), humidities[c],
                mgo.getRange(MapGeneratorOptions.HUMIDITY));
            assertEquals(w.toString(), w, OriginalWorldSettings.of(mgo));
            assertEquals(w.toString(), w,
                         OriginalWorldSettings.ofCode(w.getCode()));
            n++;
        }
        assertEquals(81, n);
    }

    /**
     * The presets: everything Normal is the middle row; the title
     * menu's «Neue Welt» of the original is Klein and Inselgruppe (its
     * three games 427, 469 and 650 land tiles, mean 515; OriginalWorld
     * gives that setting 514); the Classic UI's «Neue Welt» stays
     * everything Normal (Roger and Levi play «Individualisieren», all
     * Normal) until the Classic UI has «Individualisieren».  The levi
     * rules' own defaults are everything Normal.
     */
    public void testThePresets() {
        assertEquals("NNgn", OriginalWorldSettings.ALL_NORMAL.getCode());
        assertEquals("KIgn", OriginalWorldSettings.ORIGINAL_NEW_WORLD.getCode());
        assertEquals(OriginalWorldSettings.ALL_NORMAL,
                     OriginalWorldSettings.CLASSIC_UI_NEW_WORLD);
        assertEquals(514, OriginalWorld.getMeanLand(0, 0, 58, 72));
        assertEquals(886, OriginalWorld.getMeanLand(1, 1, 58, 72));
        assertEquals(OriginalWorldSettings.ALL_NORMAL,
                     OriginalWorldSettings.of(mapOptions("levi")));
    }

    /**
     * Rules without the original's world are left alone, and say so.
     */
    public void testOtherRulesAreLeftAlone() {
        for (String rules : new String[] { "classic", "freecol" }) {
            final OptionGroup mgo = mapOptions(rules);
            final int type = mgo.getSelection(MapGeneratorOptions.LAND_GENERATOR_TYPE);
            final int temperature = mgo.getRange(MapGeneratorOptions.TEMPERATURE);
            final int humidity = mgo.getRange(MapGeneratorOptions.HUMIDITY);
            assertFalse(rules, new OriginalWorldSettings(2, 2, 2, 2).apply(mgo));
            assertEquals(rules, type,
                mgo.getSelection(MapGeneratorOptions.LAND_GENERATOR_TYPE));
            assertEquals(rules, temperature,
                         mgo.getRange(MapGeneratorOptions.TEMPERATURE));
            assertEquals(rules, humidity,
                         mgo.getRange(MapGeneratorOptions.HUMIDITY));
            assertNull(rules, OriginalWorldSettings.of(mgo));
        }
    }

    /**
     * An answer is a row of the original's grid, 0 to 2.
     */
    public void testBadAnswers() {
        for (int bad : new int[] { -1, 3 }) {
            try {
                new OriginalWorldSettings(1, bad, 1, 1);
                fail("row " + bad);
            } catch (IllegalArgumentException e) {
                // expected
            }
        }
        try {
            OriginalWorldSettings.ofCode("NNxn");
            fail("code NNxn");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }
}
