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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import junit.framework.TestCase;


/**
 * Tests of the departure's captions ({@link ClassicDeparture.Captions}):
 * which placeholder each {@code @BUILDk} gets and Holland's measured extra
 * space.  The text files are SYNTHETIC, in the original's format, with
 * placeholder words only.
 */
public class ClassicDepartureCaptionsTest extends TestCase {

    private File dir;
    private ClassicText text;

    private static String game() {
        final StringBuilder sb = new StringBuilder(";  synthetic\r\n");
        for (int k = 1; k <= 10; k++) {
            sb.append("@BUILD").append(k).append("\r\n@width=310\r\n@y=10\r\n");
            switch (k) {
            case 2: sb.append("^^lvl %STRING0 by %STRING1\r\n^^ second\r\n"); break;
            case 3: sb.append("^^from %STRING0\r\n"); break;
            case 4: case 7: sb.append("^^bless %STRING0 now\r\n"); break;
            default: sb.append("^^step ").append(k).append(" 100%\r\n"); break;
            }
            sb.append("\r\n");
        }
        return sb.append("@END\r\n\u001A").toString();
    }

    private static final String NAMES =
        "@COUNTRY\r\nLandA,  12\r\nLandB,  9\r\nLandC, 14\r\nLandD,   13\r\n\r\n"
        + "@HOMEPORT\r\nPortA\r\nPortB\r\nPortC\r\nPortD\r\n\r\n"
        + "@DIFFICULTY\r\nLevelZero\r\nLevelOne\r\nLevelTwo\r\nLevelThree\r\nLevelFour\r\n\r\n";

    @Override
    protected void setUp() throws IOException {
        this.dir = Files.createTempDirectory("classic-departure").toFile();
        final File g = new File(this.dir, "GAME.TXT"), n = new File(this.dir, "NAMES.TXT"),
            l = new File(this.dir, "LABELS.TXT");
        Files.write(g.toPath(), game().getBytes(StandardCharsets.ISO_8859_1));
        Files.write(n.toPath(), NAMES.getBytes(StandardCharsets.ISO_8859_1));
        Files.write(l.toPath(), "@MISC\r\nm0\r\n".getBytes(StandardCharsets.ISO_8859_1));
        this.text = ClassicText.fromFiles(g, n, l);
    }

    @Override
    protected void tearDown() {
        for (String s : new String[] { "GAME.TXT", "NAMES.TXT", "LABELS.TXT" }) {
            new File(this.dir, s).delete();
        }
        this.dir.delete();
    }

    private static String[] col0(List<String[]> rows) {
        final String[] out = new String[rows.size()];
        for (int i = 0; i < out.length; i++) out[i] = rows.get(i)[0];
        return out;
    }

    private ClassicDeparture.Captions captions(int nation, int difficulty, String leader) {
        return ClassicDeparture.Captions.of(ClassicDeparture.loadCaptions(this.text),
            col0(this.text.names("DIFFICULTY")), col0(this.text.names("HOMEPORT")),
            col0(this.text.names("COUNTRY")), nation, difficulty, leader);
    }

    public void testLoadCaptions() {
        final ClassicText.Message[] m = ClassicDeparture.loadCaptions(this.text);
        assertEquals(ClassicDeparture.STEPS, m.length);
        for (ClassicText.Message x : m) {
            assertNotNull(x);
            assertEquals(Integer.valueOf(310), x.width);
            assertEquals(Integer.valueOf(10), x.y);
        }
        assertEquals("BUILD10", ClassicDeparture.section(10));
        assertEquals("LEVN0001.PIK", ClassicDeparture.picture(1));
        assertEquals("LEVN0010.PIK", ClassicDeparture.picture(10));
    }

    public void testSubstitutionsEngland() {
        final ClassicDeparture.Captions c = captions(0, 0, "Leader Name");
        assertNotNull(c);
        assertEquals("^^lvl LevelZero by Leader Name", c.step(2).text.get(0));
        assertEquals("^^ second", c.step(2).text.get(1));
        assertEquals("^^from PortA", c.step(3).text.get(0));
        assertEquals("^^bless LandA now", c.step(4).text.get(0));
        assertEquals("^^bless LandA now", c.step(7).text.get(0));
        // No placeholders elsewhere; a lone '%' stays.
        assertEquals("^^step 1 100%", c.step(1).text.get(0));
        assertEquals("^^step 10 100%", c.step(10).text.get(0));
        // Directives survive the substitution.
        assertEquals(Integer.valueOf(310), c.step(4).width);
    }

    public void testDutchPrefix() {
        final ClassicDeparture.Captions c = captions(ClassicDeparture.DUTCH, 3, "D");
        assertEquals("^^bless  LandD now", c.step(4).text.get(0));
        assertEquals("^^bless  LandD now", c.step(7).text.get(0));
        assertEquals(" LandD", c.country);
        assertEquals("^^lvl LevelThree by D", c.step(2).text.get(0));
        assertEquals("^^from PortD", c.step(3).text.get(0));
        // France and Spain get no prefix (unverified, see the Javadoc).
        assertEquals("LandB", captions(1, 0, "x").country);
        assertEquals("LandC", captions(2, 0, "x").country);
    }

    public void testMissing() {
        assertNull(captions(4, 0, "x"));
        assertNull(captions(0, 5, "x"));
        assertNull(captions(-1, 0, "x"));
        final ClassicText.Message[] m = ClassicDeparture.loadCaptions(this.text);
        m[5] = null;
        assertNull(ClassicDeparture.Captions.of(m, new String[] { "a" },
            new String[] { "p" }, new String[] { "c" }, 0, 0, "x"));
        assertEquals(ClassicDeparture.STEPS, ClassicDeparture.loadCaptions(null).length);
    }

    public void testSetupMapping() {
        final ClassicNewWorldScreens.Texts t = texts(
            ClassicDeparture.loadCaptions(this.text));
        final ClassicDeparture.Captions c = ClassicDeparture.Captions.of(t,
            new ClassicGUI.NewWorldSetup("model.difficulty.hard",
                                         "model.nation.dutch", "Someone"));
        assertEquals(ClassicDeparture.DUTCH, c.nation);
        assertEquals("LevelThree", c.difficultyTitle);
        assertEquals("Someone", c.leader);
        assertEquals("PortD", c.homePort);
        assertNull(ClassicDeparture.Captions.of(t,
            new ClassicGUI.NewWorldSetup("model.difficulty.custom",
                                         "model.nation.dutch", "x")));
    }

    /** Chain texts holding only what the departure reads. */
    private ClassicNewWorldScreens.Texts texts(ClassicText.Message[] build) {
        return new ClassicNewWorldScreens.Texts(new String[5], new String[5],
            col0(this.text.names("COUNTRY")), new String[4], new String[2],
            new String[2], "", "", 22, new String[4],
            new ClassicText.Message[4], new ClassicText.Message[4],
            new ClassicText.Message[4], build,
            col0(this.text.names("DIFFICULTY")), col0(this.text.names("HOMEPORT")));
    }

    /** A missing picture or caption makes the departure unavailable. */
    public void testAvailable() {
        final BufferedImage px = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        final BufferedImage[] levn = new BufferedImage[ClassicDeparture.STEPS];
        for (int i = 0; i < levn.length; i++) levn[i] = px;
        final ClassicNewWorldScreens.Assets a = new ClassicNewWorldScreens.Assets(
            null, null, null, null, null, null, null, null, null, null, null,
            null, levn);
        final ClassicNewWorldScreens.Texts t = texts(ClassicDeparture.loadCaptions(this.text));
        assertTrue(ClassicDeparture.available(a, t));
        levn[6] = null;
        assertFalse(ClassicDeparture.available(a, t));
        levn[6] = px;
        final ClassicText.Message[] m = ClassicDeparture.loadCaptions(this.text);
        m[0] = null;
        assertFalse(ClassicDeparture.available(a, texts(m)));
        assertFalse(ClassicDeparture.available(null, t));
    }
}
