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
            if (key.equals(UNIT + "seasonedScout")) continue;   // roleless, 081 + @JOB row
            assertFalse(key, (ICONS + "103").equals(p.getProperty(key)));
        }
    }

    /**
     * A colonist without a role is ICONS.SS 081 + its NAMES.TXT @JOB row
     * (build spec W21b; 0 px: the expert farmer 081 clip007 #5804, the fur
     * trapper 085 clip005 #14364, the blacksmith 095 clip005 #14827, the
     * free colonist 100; the others by the sheet's order).
     */
    public void testRolelessColonistsFollowTheJobRow() throws IOException {
        final Properties p = aliases();
        assertEquals(ICONS + "081", p.getProperty(UNIT + "expertFarmer"));
        assertEquals(ICONS + "085", p.getProperty(UNIT + "expertFurTrapper"));
        assertEquals(ICONS + "087", p.getProperty(UNIT + "expertOreMiner"));
        assertEquals(ICONS + "095", p.getProperty(UNIT + "masterBlacksmith"));
        assertEquals(ICONS + "100", p.getProperty(UNIT + "freeColonist"));
        int n = 0;
        for (String key : p.stringPropertyNames()) {
            if (!key.startsWith(UNIT) || key.indexOf('.', UNIT.length()) >= 0) continue;
            final int row = ClassicHud.jobRow(key.substring(UNIT.length()));
            if (row < 0) continue;
            assertEquals(key, ICONS + String.format("%03d", 81 + row), p.getProperty(key));
            n++;
        }
        assertEquals(26, n);
    }

    /**
     * The roles: every pioneer is 073 but the hardy pioneer's 101 (clip007
     * #1306, clip004 #2786: a fur trapper with tools is 073, not the
     * farmer's 081); every soldier 074 but the veteran's 102 and the
     * continental army's 128; every missionary 077 but the jesuit's 105.
     */
    public void testRoleSprites() throws IOException {
        final Properties p = aliases();
        int pioneers = 0;
        for (String key : p.stringPropertyNames()) {
            if (!key.startsWith(UNIT)) continue;
            final String v = p.getProperty(key);
            if (key.endsWith(".pioneer")) {
                pioneers++;
                assertEquals(key, ICONS + (key.equals(UNIT + "hardyPioneer.pioneer")
                                           ? "101" : "073"), v);
            } else if (key.endsWith(".soldier")) {
                assertEquals(key, ICONS + (key.equals(UNIT + "veteranSoldier.soldier") ? "102"
                    : key.equals(UNIT + "colonialRegular.soldier") ? "128" : "074"), v);
            } else if (key.endsWith(".missionary")) {
                assertEquals(key, ICONS + (key.equals(UNIT + "jesuitMissionary.missionary")
                                           ? "105" : "077"), v);
            }
            if (!key.startsWith(UNIT + "undead")) {   // FreeCol's revenge mode only
                assertFalse(key, (ICONS + "058").equals(v));
            }
        }
        assertTrue("pioneer aliases: " + pioneers, pioneers >= 20);
    }

    /**
     * The units NAMES.TXT @UNIT gives an icon: its icon column minus one
     * (artillery 10 -&gt; 009 at 0 px, clip005 #14481; the Sioux brave
     * 110 -&gt; 109 at 0 px, clip004 #5090; the wagon train, treasure and
     * ships as before).
     */
    public void testUnitIconColumn() throws IOException {
        final Properties p = aliases();
        final String[][] want = {
            { "artillery", "009" }, { "wagonTrain", "008" }, { "treasureTrain", "016" },
            { "caravel", "005" }, { "merchantman", "006" }, { "galleon", "007" },
            { "privateer", "014" }, { "frigate", "015" }, { "manOWar", "127" },
            { "brave", "109" }, { "brave.armedBrave", "110" }, { "brave.mountedBrave", "111" },
            { "brave.nativeDragoon", "112" }, { "kingsRegular", "125" },
            { "kingsRegular.infantry", "125" }, { "kingsRegular.cavalry", "126" },
            { "colonialRegular.soldier", "128" }, { "colonialRegular.dragoon", "129" },
        };
        for (String[] w : want) {
            assertEquals(w[0], ICONS + w[1], p.getProperty(UNIT + w[0]));
        }
    }

    /**
     * The generated pack carries the committed aliases (skipped without
     * the pack): a forgotten {@code ant classic-assets} after an alias
     * change would show the old sprites without any error.
     */
    public void testThePackHasTheseAliases() throws IOException {
        final Path pack = Path.of("data", "mods", "classic_original", "resources.properties");
        if (!Files.isRegularFile(pack)) {
            System.err.println("testThePackHasTheseAliases skipped: no pack (ant classic-assets)");
            return;
        }
        final Properties have = new Properties();
        try (Reader r = Files.newBufferedReader(pack, StandardCharsets.UTF_8)) {
            have.load(r);
        }
        final Properties p = aliases();
        for (String key : p.stringPropertyNames()) {
            if (!key.startsWith(UNIT)) continue;
            assertEquals("re-run ant classic-assets: " + key, p.getProperty(key),
                         have.getProperty(key));
        }
    }
}
