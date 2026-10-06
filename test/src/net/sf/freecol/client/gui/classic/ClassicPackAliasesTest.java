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

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import junit.framework.TestCase;


/**
 * Tests of the committed key map {@code tools/classic_assets/aliases.properties},
 * which the converter appends to the generated pack's
 * {@code resources.properties}: the unit sprites the original's frames
 * pin down (plan D0d).  Needs no pack.
 */
public class ClassicPackAliasesTest extends TestCase {

    private static final String UNIT = "image.unit.model.unit.";
    private static final String ICONS = "resource:image.classic_original.ss.ICONS.SS.";

    private static Properties aliases() throws IOException {
        final Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(
                Path.of("tools", "classic_assets", "aliases.properties"),
                StandardCharsets.UTF_8)) {
            p.load(r);
        }
        return p;
    }

    /**
     * The seasoned scout scouting is ICONS.SS 103 (clip008: the promotion
     * swaps the map sprite 075 to 103, and the panel shows 103).
     */
    public void testTheSeasonedScoutHasItsOwnSprite() throws IOException {
        assertEquals(ICONS + "103", aliases().getProperty(UNIT + "seasonedScout.scout"));
    }

    /** Every other type in the scout role is the mounted colonist 075. */
    public void testEveryOtherScoutIsTheMountedColonist() throws IOException {
        final Properties p = aliases();
        int n = 0;
        for (String key : p.stringPropertyNames()) {
            if (!key.startsWith(UNIT) || !key.endsWith(".scout")
                || key.equals(UNIT + "seasonedScout.scout")) continue;
            assertEquals(key, ICONS + "075", p.getProperty(key));
            n++;
        }
        assertTrue("scout aliases: " + n, n >= 20);
        for (String key : p.stringPropertyNames()) {
            if (key.equals(UNIT + "seasonedScout.scout")) continue;
            assertFalse(key, (ICONS + "103").equals(p.getProperty(key)));
        }
    }
}
