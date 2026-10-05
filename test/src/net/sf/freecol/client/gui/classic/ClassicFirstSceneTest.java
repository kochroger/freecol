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

import java.awt.Point;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.sf.freecol.common.model.ModelMessage;

import junit.framework.TestCase;


/**
 * Tests of the first game scene's non-visual logic ({@link
 * ClassicFirstScene}): the advisor placement rule, the ship rows, the band
 * and advisor texts with their placeholder, and the start-message filter of
 * {@link ClassicGUI}.  The text files are SYNTHETIC, in the original's
 * format, with placeholder words only.
 */
public class ClassicFirstSceneTest extends TestCase {

    private File dir;
    private ClassicText text;

    private static final String GAME =
        ";  synthetic\r\n"
        + "@TUTORIAL1\r\n@width=230\r\n@x=10\r\n@y=40\r\n"
        + "Our {%STRING0} sails   \r\nwith two men.\r\n\r\n@END\r\n\u001A";

    private static final String NAMES =
        "@NATIONALITY\r\nNatA.\r\nNatB.\r\nNatC.\r\nNatD.\r\n\r\n"
        + "@HOMEPORT\r\nPortA\r\nPortB\r\nPortC\r\nPortD\r\n\r\n"
        + "@UNIT\r\n" + units() + "\r\n";

    private static String units() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            sb.append("Unit").append(i).append(",  101, 1, 0\r\n");
        }
        return sb.toString();
    }

    private static final String LABELS =
        "@MISC\r\nm0\r\nm1\r\nm2\r\nm3\r\nm4\r\nm5\r\nArrivesFrom\r\nm7\r\n\r\n";

    @Override
    protected void setUp() throws IOException {
        this.dir = Files.createTempDirectory("classic-firstscene").toFile();
        final File g = new File(this.dir, "GAME.TXT"), n = new File(this.dir, "NAMES.TXT"),
            l = new File(this.dir, "LABELS.TXT");
        Files.write(g.toPath(), GAME.getBytes(StandardCharsets.ISO_8859_1));
        Files.write(n.toPath(), NAMES.getBytes(StandardCharsets.ISO_8859_1));
        Files.write(l.toPath(), LABELS.getBytes(StandardCharsets.ISO_8859_1));
        this.text = ClassicText.fromFiles(g, n, l);
    }

    @Override
    protected void tearDown() {
        for (String s : new String[] { "GAME.TXT", "NAMES.TXT", "LABELS.TXT" }) {
            new File(this.dir, s).delete();
        }
        this.dir.delete();
    }

    /** MSS0 (75x91) at box + (-4,-71), box 236x48: 083/049. */
    public void testPlaceAdmiral() {
        final Point[] at = ClassicFirstScene.place(236, 48, 75, 91,
            ClassicFirstScene.MSS0_OFF_X, ClassicFirstScene.MSS0_OFF_Y);
        assertEquals(new Point(44, 112), at[0]);
        assertEquals(new Point(40, 41), at[1]);
    }

    /** MSS2 (122x84) at box + (57,-78): Europe 011 and 013. */
    public void testPlaceTradeAdvisor() {
        Point[] at = ClassicFirstScene.place(236, 74, 122, 84,
            ClassicFirstScene.MSS2_OFF_X, ClassicFirstScene.MSS2_OFF_Y);
        assertEquals(new Point(42, 102), at[0]);
        assertEquals(new Point(99, 24), at[1]);
        at = ClassicFirstScene.place(236, 92, 122, 84,
            ClassicFirstScene.MSS2_OFF_X, ClassicFirstScene.MSS2_OFF_Y);
        assertEquals(new Point(42, 93), at[0]);
        assertEquals(new Point(99, 15), at[1]);
    }

    /** A box alone (no portrait overhang) is simply centred. */
    public void testPlaceBoxOnly() {
        final Point[] at = ClassicFirstScene.place(100, 50, 10, 10, 0, 0);
        assertEquals(new Point(110, 75), at[0]);
    }

    /** FreeCol ship types to NAMES.TXT @UNIT rows 13..18. */
    public void testShipRows() {
        assertEquals(13, ClassicFirstScene.shipRow("model.unit.caravel"));
        assertEquals(14, ClassicFirstScene.shipRow("model.unit.merchantman"));
        assertEquals(15, ClassicFirstScene.shipRow("model.unit.galleon"));
        assertEquals(16, ClassicFirstScene.shipRow("model.unit.privateer"));
        assertEquals(17, ClassicFirstScene.shipRow("model.unit.frigate"));
        assertEquals(18, ClassicFirstScene.shipRow("model.unit.manOWar"));
        assertEquals(-1, ClassicFirstScene.shipRow("model.unit.freeColonist"));
        assertEquals(-1, ClassicFirstScene.shipRow(null));
    }

    /** Band = NATIONALITY + UNIT + misc(6) + HOMEPORT, single spaces. */
    public void testBandText() {
        assertEquals("NatD. Unit14 ArrivesFrom PortD",
                     ClassicFirstScene.bandText(this.text, 3, 14));
        assertEquals("NatA. Unit13 ArrivesFrom PortA",
                     ClassicFirstScene.bandText(this.text, 0, 13));
        assertNull(ClassicFirstScene.bandText(this.text, 4, 13));
        assertNull(ClassicFirstScene.bandText(this.text, 0, 99));
        assertNull(ClassicFirstScene.bandText(null, 0, 13));
    }

    /** TUTORIAL1's %STRING0 is the ship's name; directives kept. */
    public void testAdvisorText() {
        final ClassicText.Message m = ClassicFirstScene.advisorText(this.text, 13);
        assertNotNull(m);
        assertEquals(Integer.valueOf(230), m.width);
        assertEquals(Integer.valueOf(40), m.y);
        assertEquals("Our {Unit13} sails   ", m.text.get(0));
        assertEquals("with two men.", m.text.get(1));
        assertNull(ClassicFirstScene.advisorText(this.text, 99));
    }

    /** build() refuses missing pieces instead of painting half a scene. */
    public void testBuildNeedsEverything() {
        assertNull(ClassicFirstScene.build(null, null, null, 3, 14));
        assertNull(ClassicFirstScene.build(this.text, null, null, 3, 14));
    }

    /** FreeCol's start message is dropped, everything else kept in order. */
    public void testStartMessageDropped() {
        final ModelMessage a = message("model.unit.unitImproved");
        final ModelMessage start = message(ClassicGUI.START_GAME_MESSAGE);
        final ModelMessage b = message("model.colony.starving");
        final List<ModelMessage> in = new ArrayList<>(Arrays.asList(a, start, null, b));
        final List<ModelMessage> out = ClassicGUI.withoutStartMessage(in);
        assertEquals(Arrays.asList(a, b), out);
        assertEquals("input untouched", 4, in.size());
        assertTrue(ClassicGUI.withoutStartMessage(Arrays.asList(start)).isEmpty());
        assertTrue(ClassicGUI.withoutStartMessage(null).isEmpty());
    }

    private static ModelMessage message(String id) {
        final ModelMessage m = new ModelMessage();
        m.setId(id);
        return m;
    }
}
