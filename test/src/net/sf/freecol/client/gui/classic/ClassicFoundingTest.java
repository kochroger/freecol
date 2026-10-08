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

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * The founding of a colony as in the original (master plan D4,
 * {@link ClassicFounding}): FreeCol's site warnings, @NOPORT, the @COLONY
 * box and its default name, the timeline; with the pack and
 * {@code -Dclassic.clips} the boxes against clip008's frames.
 */
public class ClassicFoundingTest extends FreeColTestCase {

    /** FreeCol's warnings ({@code Tile.getBuildColonyWarnings}'s form). */
    private static StringTemplate warnings(String... keys) {
        final StringTemplate t = StringTemplate.label("\n");
        for (String k : keys) t.add(k);
        return t;
    }

    /**
     * The site warnings: a label of {@code warning.*} keys; a land-locked
     * site is @NOPORT, the other warnings are not the original's.
     */
    public void testSiteWarnings() {
        assertTrue(ClassicFounding.siteWarnings(warnings("warning.landLocked")));
        assertTrue(ClassicFounding.landLocked(warnings("warning.landLocked")));
        assertTrue(ClassicFounding.landLocked(warnings("warning.noFood",
            "warning.landLocked", "warning.nativeLand")));
        assertTrue(ClassicFounding.siteWarnings(warnings("warning.noFood")));
        assertFalse(ClassicFounding.landLocked(warnings("warning.noFood",
            "warning.ownLand", "warning.europeanLand", "warning.nativeLand")));
        final StringTemplate lumber = StringTemplate.label("\n");
        lumber.addStringTemplate(StringTemplate.template("warning.noBuildingMaterials")
            .addName("%goods%", "Nutzholz"));
        assertTrue(ClassicFounding.siteWarnings(lumber));
        assertFalse(ClassicFounding.landLocked(lumber));
        // Other questions are not.
        assertFalse(ClassicFounding.siteWarnings(null));
        assertFalse(ClassicFounding.siteWarnings(StringTemplate.label("\n")));
        assertFalse(ClassicFounding.siteWarnings(StringTemplate.template("learnSkill.text")
            .addName("%skill%", "x")));
        assertFalse(ClassicFounding.siteWarnings(StringTemplate.key("warning.landLocked")));
        assertFalse(ClassicFounding.siteWarnings(warnings("warning.landLocked", "highseas.text")));
        assertFalse(ClassicFounding.landLocked(StringTemplate.key("warning.landLocked")));
        // Only the second row founds.
        assertFalse(ClassicFounding.foundsAnyway(0));
        assertTrue(ClassicFounding.foundsAnyway(1));
        assertFalse(ClassicFounding.foundsAnyway(ClassicAdvisorBox.Bar.DISMISSED));
    }

    /** COLONY.TXT: the lists up to @STOP, the years dropped; the first unused name. */
    public void testColonyNames() {
        final String file = ";\r\n;  COLONIZATION Colony Names List\r\n;\r\n\r\n"
            + "@ENGLISH\r\nJamestown,1607\r\nPlymouth,1620\r\n@STOP\r\n\r\n"
            + "@DUTCH\r\nNew Amsterdam\r\nFort Orange\r\nCuracao\r\n@STOP\r\n";
        final Map<String, List<String>> m = ClassicText.colonyLists(
            file.getBytes(StandardCharsets.ISO_8859_1));
        assertEquals(List.of("Jamestown", "Plymouth"), m.get("ENGLISH"));
        assertEquals(List.of("New Amsterdam", "Fort Orange", "Curacao"), m.get("DUTCH"));
        assertNull(m.get("STOP"));
        // The section of a nation.
        final Game game = getStandardGame();
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        assertEquals("DUTCH", ClassicFounding.section(dutch));
        assertEquals("ENGLISH", ClassicFounding.section(
            game.getPlayerByNationId("model.nation.english")));
        assertNull(ClassicFounding.section(null));
        // The first name no settlement has.
        final List<String> names = m.get("DUTCH");
        assertEquals("New Amsterdam", ClassicFounding.defaultName(names, null));
        assertNull(ClassicFounding.defaultName(null, game));
        game.changeMap(getTestMap(true));
        final Colony colony = createStandardColony();
        final String taken = colony.getName();
        final List<String> withTaken = List.of(taken, "Fort Orange");
        assertEquals("Fort Orange", ClassicFounding.defaultName(withTaken, game));
        assertNull(ClassicFounding.defaultName(List.of(taken), game));
        // The pack's file: the Dutch list starts with New Amsterdam.
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final List<String> dutchNames = ClassicText.colonyNames(pack, "DUTCH");
        if (dutchNames != null) {
            assertEquals("New Amsterdam", dutchNames.get(0));
            assertEquals("Fort Orange", dutchNames.get(1));
            assertEquals("Naiack", dutchNames.get(dutchNames.size() - 1));
            assertEquals(32, dutchNames.size());
            assertEquals("Jamestown", ClassicText.colonyNames(pack, "ENGLISH").get(0));
            for (String n : dutchNames) {
                assertTrue(n, n.length() <= ClassicAdvisorBox.Field.MAX_CHARS);
            }
            assertNull(ClassicText.colonyNames(pack, "STOP"));
        }
    }

    /** The timeline (V clip008): 29 and 34 frames after the box's close. */
    public void testTheTimeline() {
        assertEquals(413.8, ClassicFounding.SPRITE_AFTER_PROMPT_MS, 0.1);
        assertEquals(485.1, ClassicFounding.BLACK_AFTER_PROMPT_MS, 0.1);
        final long ms = 1_000_000L;
        // The black 485 ms after the close when the colony came in time,
        // and when the server's round trip made it later (live: 462 ms).
        assertEquals(485_116_000L, ClassicGUI.blackAfterFounding(0L, 413 * ms), 1000L);
        assertEquals(485_116_000L, ClassicGUI.blackAfterFounding(0L, 462 * ms), 1000L);
        // A colony later still: on the map one frame before the black.
        assertEquals(494_268_000L, ClassicGUI.blackAfterFounding(0L, 480 * ms), 1000L);
        // No box (the stopgap or no pack): 72 ms after the paint.
        assertEquals(1072 * ms, ClassicGUI.blackAfterFounding(Long.MIN_VALUE, 1000 * ms));
    }

    /**
     * The boxes with the pack: @NOPORT's rows, bar, Escape and portrait,
     * box (62,112,196,64); @COLONY's field, label, box (71,113,178,37),
     * Enter, typing, Escape.
     */
    public void testTheBoxes() {
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final ClassicText t = ClassicText.load(pack);
        final ClassicFont tiny = (pack == null) ? null : pack.font(ClassicFont.TINY);
        assertNull(ClassicFounding.noPortRequest(null, "x"));
        assertNull(ClassicFounding.colonyRequest(null, "New Amsterdam", "x"));
        if (t == null || tiny == null) {
            System.err.println(getClass().getSimpleName() + ": boxes skipped, no pack");
            return;
        }
        final ClassicAdvisorBox.Request np = ClassicFounding.noPortRequest(t, "x");
        assertEquals(ClassicFounding.NOPORT_SECTION, np.id);
        assertEquals(2, np.rows.size());
        assertEquals("\"Oh, daran hatte ich nicht gedacht.\"", np.plainRows()[0]);
        assertEquals("\"Und das ist genau das, was ich vorhatte.\"", np.plainRows()[1]);
        assertTrue(np.plainText().startsWith("Dieses Kartenquadrat hat keinen Zugang zum Meer"));
        assertEquals(0, np.defaultRow);
        assertSame(ClassicAdvisorBox.Portrait.SCOUT, np.portrait);
        assertEquals(0, new ClassicAdvisorBox.Bar(np).escape());
        assertEquals(0, new ClassicAdvisorBox.Bar(np).enter());
        final ClassicAdvisorBox.Bar down = new ClassicAdvisorBox.Bar(np);
        down.down();
        assertEquals(1, down.enter());
        final ClassicAdvisorBox.Layout nl = ClassicAdvisorBox.layout(np, tiny,
            pack.image(ClassicPackFiles.ssKey(np.portrait.sprite)));
        assertEquals(new Rectangle(62, 112, 196, 64), nl.box);

        final ClassicAdvisorBox.Request c = ClassicFounding.colonyRequest(t,
            "New Amsterdam", "x");
        assertEquals(ClassicFounding.COLONY_SECTION, c.id);
        assertTrue(c.hasField());
        assertEquals("New Amsterdam", c.field.initial);
        assertEquals("Name:", ClassicAdvisorBox.plain(c.fieldLabel));
        assertEquals("Wie sollen wir diese Kolonie nennen?", c.plainText());
        assertSame(ClassicAdvisorBox.Portrait.COLONIST, c.portrait);
        final ClassicAdvisorBox.Layout cl = ClassicAdvisorBox.layout(c, tiny,
            pack.image(ClassicPackFiles.ssKey(c.portrait.sprite)));
        assertEquals(new Rectangle(71, 113, 178, 37), cl.box);
        assertEquals(new Rectangle(100, 131, 144, 10), cl.field);
        // Enter: the default; typing replaces it; Escape and a box that
        // could not open found nothing.
        ClassicAdvisorBox.Request r = ClassicFounding.colonyRequest(t, "New Amsterdam", "x");
        assertEquals("New Amsterdam", ClassicFounding.answered(r, new ClassicAdvisorBox.Bar(r).enter()));
        r = ClassicFounding.colonyRequest(t, "New Amsterdam", "x");
        ClassicAdvisorBox.Bar b = new ClassicAdvisorBox.Bar(r);
        for (char ch : "Base".toCharArray()) assertTrue(b.type(ch, tiny));
        assertEquals("Base", b.fieldText());
        assertEquals("Base", ClassicFounding.answered(r, b.enter()));
        r = ClassicFounding.colonyRequest(t, "New Amsterdam", "x");
        b = new ClassicAdvisorBox.Bar(r);
        b.type('B', tiny);
        assertNull(ClassicFounding.answered(r, b.escape()));
        r = ClassicFounding.colonyRequest(t, "New Amsterdam", "x");
        assertNull(ClassicFounding.answered(r, ClassicAdvisorBox.Bar.DISMISSED));
        assertNull(ClassicFounding.answered(null, 0));
        // An emptied field: the default.
        r = ClassicFounding.colonyRequest(t, "New Amsterdam", "x");
        b = new ClassicAdvisorBox.Bar(r);
        b.backspace();
        assertEquals("New Amsterdam", ClassicFounding.answered(r, b.enter()));
    }

    /**
     * Golden (the pack and {@code -Dclassic.clips}): @NOPORT over clip008
     * #1002 with the bar on row 1; @COLONY over #2789 with "New Amsterdam"
     * selected and over #3218 with "Base" typed.  The box and the
     * portrait's opaque pixels, the mouse arrow apart.
     */
    public void testGoldenAgainstClip008() throws Exception {
        final String clips = System.getProperty(ClassicTerrainGoldenTest.CLIPS_PROPERTY);
        final File dir = (clips == null) ? null : new File(clips, "clip008");
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final ClassicText t = ClassicText.load(pack);
        final ClassicFont tiny = (pack == null) ? null : pack.font(ClassicFont.TINY);
        if (dir == null || !dir.isDirectory() || t == null || tiny == null) {
            System.err.println(getClass().getSimpleName()
                + ": clip008 founding boxes skipped, no recordings or no pack");
            return;
        }
        final BufferedImage wood = pack.image(ClassicMenuBar.WOOD_KEY);
        final Object[][] cases = {
            { "frame_001002.png", ClassicFounding.noPortRequest(t, "x"), null, 0,
              new Rectangle(160, 40, 14, 24) },
            { "frame_002789.png", ClassicFounding.colonyRequest(t, "New Amsterdam", "x"),
              "New Amsterdam", -1, new Rectangle(160, 48, 14, 18) },
            { "frame_003218.png", ClassicFounding.colonyRequest(t, "New Amsterdam", "x"),
              "Base", -1, new Rectangle(160, 48, 14, 18) },
        };
        // The field's one stray pixel of index 68 (V: clip008 01-colony
        // section 1.2, in every frame of the box; under the selected
        // default it is the text's).
        final Point stray = new Point(134, 139);
        final StringBuilder out = new StringBuilder();
        int bad = 0;
        for (Object[] k : cases) {
            final String name = (String) k[0];
            final ClassicAdvisorBox.Request r = (ClassicAdvisorBox.Request) k[1];
            final BufferedImage pic = pack.image(ClassicPackFiles.ssKey(r.portrait.sprite));
            final ClassicAdvisorBox.Layout l = ClassicAdvisorBox.layout(r, tiny, pic);
            final BufferedImage frame = ImageIO.read(new File(dir, name));
            assertNotNull(name, frame);
            final BufferedImage canvas = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
            final Graphics2D g = canvas.createGraphics();
            g.drawImage(frame, 0, 0, null);
            final String text = (String) k[2];
            ClassicAdvisorBox.paint(g, l, (Integer) k[3], null, text,
                                    "New Amsterdam".equals(text), wood, tiny);
            g.dispose();
            int compared = 0, diff = 0, arrow = 0, strays = 0;
            final Rectangle where = new Rectangle();
            for (int y = 0; y < 200; y++) {
                for (int x = 0; x < 320; x++) {
                    if (!l.box.contains(x, y) && !opaque(l, x, y)) continue;
                    compared++;
                    if ((frame.getRGB(x, y) & 0xFFFFFF) == (canvas.getRGB(x, y) & 0xFFFFFF)) {
                        continue;
                    }
                    if (((Rectangle) k[4]).contains(x, y)) {
                        arrow++;
                        continue;
                    }
                    if (stray.x == x && stray.y == y && text != null) {
                        strays++;
                        continue;
                    }
                    diff++;
                    if (where.isEmpty()) where.setBounds(x, y, 1, 1);
                    else where.add(new Rectangle(x, y, 1, 1));
                }
            }
            out.append(' ').append(name).append(" box ").append(l.box.x).append(',')
               .append(l.box.y).append(',').append(l.box.width).append(',')
               .append(l.box.height).append(" portrait ")
               .append((l.portraitAt == null) ? "-" : l.portraitAt.x + "," + l.portraitAt.y)
               .append(' ').append(compared).append(" px, off ").append(diff)
               .append((diff > 0) ? " in " + where : "").append(", arrow ").append(arrow)
               .append(", stray ").append(strays);
            assertTrue(name + " arrow " + arrow, arrow <= 140);
            assertTrue(name + " stray " + strays, strays <= 1);
            bad += diff;
        }
        System.out.println(getClass().getSimpleName() + ": clip008 founding boxes," + out);
        assertEquals("pixels off:" + out, 0, bad);
    }

    /** Whether the portrait has an opaque pixel at a screen point. */
    private static boolean opaque(ClassicAdvisorBox.Layout l, int sx, int sy) {
        if (l.portrait == null) return false;
        final Point at = l.portraitAt;
        final int px = sx - at.x, py = sy - at.y;
        return px >= 0 && py >= 0 && px < l.portrait.getWidth()
            && py < l.portrait.getHeight()
            && (l.portrait.getRGB(px, py) >>> 24) != 0;
    }
}
