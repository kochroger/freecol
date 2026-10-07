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

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import javax.imageio.ImageIO;

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

    /**
     * A colonist without a role is ICONS.SS 081 + its NAMES.TXT @JOB row
     * (build spec W21b; 0 px: the expert farmer 081 clip007 #5804, the fur
     * trapper 085 clip005 #14364, the blacksmith 095 clip005 #14827, the
     * free colonist 100; the others by the sheet's order) -- but the four
     * experts of a role and the convert ({@link #rolelessFrame}).
     */
    public void testRolelessColonistsFollowTheJobRow() throws IOException {
        final Properties p = aliases();
        assertEquals(ICONS + "081", p.getProperty(UNIT + "expertFarmer"));
        assertEquals(ICONS + "085", p.getProperty(UNIT + "expertFurTrapper"));
        assertEquals(ICONS + "087", p.getProperty(UNIT + "expertOreMiner"));
        assertEquals(ICONS + "095", p.getProperty(UNIT + "masterBlacksmith"));
        assertEquals(ICONS + "098", p.getProperty(UNIT + "elderStatesman"));
        assertEquals(ICONS + "100", p.getProperty(UNIT + "freeColonist"));
        int n = 0, byRow = 0;
        for (String key : p.stringPropertyNames()) {
            if (!key.startsWith(UNIT) || key.indexOf('.', UNIT.length()) >= 0) continue;
            final String type = key.substring(UNIT.length());
            final int row = ClassicHud.jobRow(type);
            if (row < 0) continue;
            assertEquals(key, ICONS + String.format("%03d", rolelessFrame(type)),
                         p.getProperty(key));
            n++;
            if (rolelessFrame(type) == 81 + row) byRow++;
        }
        assertEquals(26, n);
        assertEquals(21, byRow);
    }

    /**
     * The four experts of a role without its equipment (H4; R1 verifier
     * V2.1): the hardy pioneer without tools 058, the veteran soldier
     * without muskets 059 (V: clip008, after he left his 50 muskets in
     * Base, #4032 and #42042; the job menu names him "Erfahrene Soldaten"
     * at #42043), the seasoned scout without horses 060, the jesuit
     * without the cross 061 (I: their clothes are those of 101, 103 and
     * 105).  The row formula would draw them equipped (101-105).  In their
     * own role they keep their own figure.
     */
    public void testTheExpertsWithoutTheirEquipment() throws IOException {
        final Properties p = aliases();
        final String[][] want = {
            { "hardyPioneer", "058" }, { "hardyPioneer.pioneer", "101" },
            { "veteranSoldier", "059" }, { "veteranSoldier.soldier", "102" },
            { "veteranSoldier.dragoon", "104" },
            { "seasonedScout", "060" }, { "seasonedScout.scout", "103" },
            { "jesuitMissionary", "061" }, { "jesuitMissionary.missionary", "105" },
        };
        for (String[] w : want) {
            assertEquals(w[0], ICONS + w[1], p.getProperty(UNIT + w[0]));
        }
    }

    /**
     * The convert is 066, the native in blue trousers (I: no clip shows a
     * convert); the row formula's 108 is the totem pole (clip008 #4032 on
     * native land), which no unit may show.
     */
    public void testTheConvertIsNotTheTotem() throws IOException {
        final Properties p = aliases();
        assertEquals(ICONS + "066", p.getProperty(UNIT + "indianConvert"));
        for (String key : p.stringPropertyNames()) {
            if (key.startsWith(UNIT)) {
                assertFalse(key, (ICONS + "108").equals(p.getProperty(key)));
            }
        }
    }

    /**
     * The whole frame table (H4): every unit alias is the frame its type
     * and role show in the original ({@link #frameOf}), and every alias is
     * one the table knows.
     */
    public void testEveryUnitAliasFollowsTheFrameTable() throws IOException {
        final Properties p = aliases();
        int n = 0;
        for (String key : p.stringPropertyNames()) {
            if (!key.startsWith(UNIT)) continue;
            final String rest = key.substring(UNIT.length());
            final int dot = rest.indexOf('.');
            final String type = (dot < 0) ? rest : rest.substring(0, dot);
            final String role = (dot < 0) ? null : rest.substring(dot + 1);
            final int want = frameOf(type, role);
            assertTrue("not in the table: " + key, want >= 0);
            assertEquals(key, ICONS + String.format("%03d", want), p.getProperty(key));
            n++;
        }
        assertTrue("unit aliases: " + n, n >= 170);
    }

    /**
     * The golden check of the frames the clips show (H4): each frame's
     * opaque pixels are the clip's at the place the original drew it, 0 px
     * off -- the unarmed veteran 059 (clip008 #4032 on the colony's north
     * tile and in the left band, #42042; on the main map clip005 #709 and
     * dago-colony2 #3976, both with the Dutch "G" flag), the same veteran
     * armed 102 in the panel before (clip008 #1821), the brave 109
     * (landfall #14022, two Araukaner) and the totem 108 (clip008 #4032,
     * no unit).  The frames come from the aliases where a unit has one.
     * Skipped without -Dclassic.clips or the pack.
     */
    public void testTheFramesAgainstTheClips() throws Exception {
        final String clips = System.getProperty(ClassicTerrainGoldenTest.CLIPS_PROPERTY);
        if (clips == null || !new File(clips).isDirectory()) {
            System.err.println("testTheFramesAgainstTheClips skipped: no recordings (-D"
                + ClassicTerrainGoldenTest.CLIPS_PROPERTY + ")");
            return;
        }
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        if (pack == null) {
            System.err.println("testTheFramesAgainstTheClips skipped: no pack (ant classic-assets)");
            return;
        }
        final Properties p = aliases();
        final Object[][] seen = {
            { "veteranSoldier", "clip008", 4032, 262, 38 },
            { "veteranSoldier", "clip008", 4032, 2, 142 },
            { "veteranSoldier", "clip008", 42042, 2, 142 },
            { "veteranSoldier", "clip005", 709, 115, 136 },
            { "veteranSoldier", "dago-colony2", 3976, 51, 168 },
            { "veteranSoldier.soldier", "clip008", 1821, 244, 68 },
            { "brave", "landfall", 14022, 146, 56 },
            { "brave", "landfall", 14022, 82, 72 },
            { "108", "clip008", 4032, 232, 36 },
        };
        for (Object[] s : seen) {
            final String what = (String) s[0];
            final String frame = (what.charAt(0) >= '0' && what.charAt(0) <= '9') ? what
                : p.getProperty(UNIT + what).substring(ICONS.length());
            final BufferedImage sp = pack.image(ClassicPackFiles.ssKey("ICONS.SS." + frame));
            assertNotNull(frame, sp);
            final File f = new File(new File(clips, (String) s[1]),
                String.format("frame_%06d.png", (Integer) s[2]));
            final BufferedImage clip = ImageIO.read(f);
            assertNotNull(f.toString(), clip);
            final String where = what + " " + frame + " " + s[1] + " #" + s[2]
                + " (" + s[3] + "," + s[4] + ")";
            assertEquals(where, 0, pixelsOff(clip, sp, (Integer) s[3], (Integer) s[4]));
        }
        // The figures are told apart there: the armed veteran 102 (the row
        // formula's frame) does not match where the unarmed one stands.
        final BufferedImage armed = pack.image(ClassicPackFiles.ssKey("ICONS.SS.102"));
        final BufferedImage c4032 = ImageIO.read(new File(new File(clips, "clip008"),
                                                          "frame_004032.png"));
        assertTrue(pixelsOff(c4032, armed, 262, 38) > 0);
    }

    /** The opaque pixels of {@code sp} at (x0,y0) that differ from {@code clip}. */
    private static int pixelsOff(BufferedImage clip, BufferedImage sp, int x0, int y0) {
        int off = 0;
        for (int y = 0; y < sp.getHeight(); y++) {
            for (int x = 0; x < sp.getWidth(); x++) {
                final int s = sp.getRGB(x, y);
                if ((s >>> 24) == 0) continue;
                if ((clip.getRGB(x0 + x, y0 + y) & 0xFFFFFF) != (s & 0xFFFFFF)) off++;
            }
        }
        return off;
    }

    /**
     * A colonist type's figure without a role: 081 + its NAMES.TXT @JOB
     * row, but the four experts of a role without their equipment 058-061
     * and the convert 066 (H4); -1 for no colonist.
     */
    private static int rolelessFrame(String type) {
        switch (type) {
        case "hardyPioneer": return 58;
        case "veteranSoldier": return 59;
        case "seasonedScout": return 60;
        case "jesuitMissionary": return 61;
        case "indianConvert": return 66;
        default: break;
        }
        final int row = ClassicHud.jobRow(type);
        return (row < 0) ? -1 : 81 + row;
    }

    /**
     * The frame table (H4): the ICONS.SS frame of a FreeCol unit type in a
     * role (null: none), or -1.  The units NAMES.TXT @UNIT gives an icon
     * show it minus one (ships, wagon, treasure, artillery, regulars, the
     * four braves); a colonist without a role {@link #rolelessFrame}; the
     * expert of a role in that role its own figure (101 pioneer, 102 soldier, 103
     * scout, 104 dragoon, 105 missionary); any other unit in a role the
     * shared figure (073 pioneer, 074 soldier, 075 scout, 076 dragoon, 077
     * missionary).  FreeCol-only types: the damaged artillery 065 (I),
     * the colonial regular without a role 059 (the unarmed veteran), the
     * revenge mode's undead 058 and revenger 014, the flying dutchman 127.
     */
    private static int frameOf(String type, String role) {
        if (role == null) {
            switch (type) {
            case "caravel": return 5;
            case "merchantman": return 6;
            case "galleon": return 7;
            case "wagonTrain": return 8;
            case "artillery": return 9;
            case "privateer": case "revenger": return 14;
            case "frigate": return 15;
            case "treasureTrain": return 16;
            case "damagedArtillery": return 65;
            case "brave": return 109;
            case "kingsRegular": return 125;
            case "manOWar": case "flyingDutchman": return 127;
            case "colonialRegular": return 59;
            case "undead": return 58;
            default: return rolelessFrame(type);
            }
        }
        switch (role) {
        case "armedBrave": return "brave".equals(type) ? 110 : -1;
        case "mountedBrave": return "brave".equals(type) ? 111 : -1;
        case "nativeDragoon": return "brave".equals(type) ? 112 : -1;
        case "infantry": return "kingsRegular".equals(type) ? 125 : -1;
        case "cavalry": return "kingsRegular".equals(type) ? 126 : -1;
        case "pioneer": return "hardyPioneer".equals(type) ? 101 : 73;
        case "soldier":
            return "veteranSoldier".equals(type) ? 102
                : "colonialRegular".equals(type) ? 128 : 74;
        case "scout": return "seasonedScout".equals(type) ? 103 : 75;
        case "dragoon":
            return "veteranSoldier".equals(type) ? 104
                : "colonialRegular".equals(type) ? 129 : 76;
        case "missionary": return "jesuitMissionary".equals(type) ? 105 : 77;
        default: return -1;
        }
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
            if (!key.equals(UNIT + "undead")              // FreeCol's revenge mode only
                && !key.equals(UNIT + "hardyPioneer")) {  // without tools (H4)
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
