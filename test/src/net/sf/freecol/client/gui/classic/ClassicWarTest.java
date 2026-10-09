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
import java.util.List;

import javax.imageio.ImageIO;

import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Direction;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.Topology;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;
import net.sf.freecol.util.test.FreeColTestUtils;


/**
 * The original's treaty breach ({@link ClassicWar}, gap list B3; clip
 * opening_018, {@code war-french-analysis/10-spec.md}): the @HAVETREATY box
 * F asks next to a French colony at peace -- its words, rows, bar, Escape,
 * soldier and place, 0 px against the clip's frame #640 -- and what its
 * answer tells the controller; the French soldier fortified next to our
 * colony as the map and the colony screen draw him (the screenshot, #53766).
 */
public class ClassicWarTest extends FreeColTestCase {

    private static final TileType plains = spec().getTileType("model.tile.plains");

    /** The topology in use before the test. */
    private Topology savedTopology;


    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.savedTopology = Topology.current();
    }

    @Override
    protected void tearDown() throws Exception {
        Topology.setCurrent(this.savedTopology);
        super.tearDown();
    }

    /** The pack's texts, or null (then a note: the pack-bound checks are skipped). */
    private ClassicText texts(String test) {
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        if (t == null || t.message(ClassicWar.TREATY_SECTION) == null) {
            System.err.println(getClass().getSimpleName() + "." + test
                + ": no pack texts (ant classic-assets), skipped");
            return null;
        }
        return t;
    }

    private static Player player(Game game, String nation) {
        return game.getPlayerByNationId("model.nation." + nation);
    }


    /**
     * %STRING0: NAMES.TXT @NATIONALITY of the colony's owner (V: «Frz.»),
     * FreeCol's name for a nation the original lacks and without the pack.
     */
    public void testNationality() {
        final Game game = getStandardGame();
        assertNull(ClassicWar.nationality(null, null));
        assertNotNull(ClassicWar.nationality(null, player(game, "french")));
        final ClassicText t = texts("testNationality");
        if (t == null) return;
        assertEquals("Engl.", ClassicWar.nationality(t, player(game, "english")));
        assertEquals("Frz.", ClassicWar.nationality(t, player(game, "french")));
        assertEquals("Span.", ClassicWar.nationality(t, player(game, "spanish")));
        assertEquals("Holl.", ClassicWar.nationality(t, player(game, "dutch")));
        // A nation the original does not have: FreeCol's name, literal.
        final String danish = ClassicWar.nationality(t, player(game, "danish"));
        assertNotNull(danish);
        assertFalse(danish, danish.contains("{"));
    }

    /**
     * The box (V, #640): GAME.TXT @HAVETREATY's words with «Frz.», its two
     * rows, the bar and Escape on «Handlung abbrechen.», the soldier; only
     * «Friedensvertrag brechen.» breaks the treaty.
     */
    public void testTreatyBox() {
        assertTrue(ClassicWar.breaksTreaty(1));
        assertFalse(ClassicWar.breaksTreaty(0));
        assertFalse(ClassicWar.breaksTreaty(-1));
        assertFalse(ClassicWar.breaksTreaty(2));
        final Game game = getStandardGame();
        final Player french = player(game, "french");
        assertNull(ClassicWar.treatyRequest(null, french, "t"));
        final ClassicText t = texts("testTreatyBox");
        if (t == null) return;
        assertNull(ClassicWar.treatyRequest(t, null, "t"));
        final ClassicAdvisorBox.Request r = ClassicWar.treatyRequest(t, french, "Montreal");
        assertEquals(ClassicWar.TREATY_SECTION, r.id);
        assertEquals("\"Wir haben einen Friedensvertrag mit den Frz. unterzeichnet,"
            + " Eure Exzellenz.\"", r.plainText());
        assertTrue(r.paragraphs.toString(), r.paragraphs.get(0).get(0).contains("{Frz.}"));
        assertEquals(List.of("Handlung abbrechen.", "Friedensvertrag brechen."),
                     List.of(r.plainRows()));
        assertEquals(0, r.defaultRow);
        assertEquals("Escape: Handlung abbrechen.", 0, r.escapeAnswer());
        assertSame(ClassicAdvisorBox.Portrait.SOLDIER, r.portrait);
        assertEquals(230, r.width);
        assertNull(r.unitIcon);
        // The keys (Roger): Enter on the bar's row and Escape do nothing;
        // Down and Enter break the treaty.
        final ClassicGUISeamTest.KeyPrompter keys = new ClassicGUISeamTest.KeyPrompter();
        assertEquals(0, keys.answer(r, "ENTER"));
        assertEquals(0, keys.answer(r, "ESC"));
        assertEquals(1, keys.answer(r, "DOWN", "ENTER"));
        assertEquals(0, keys.answer(r, "DOWN", "UP", "ENTER"));
        assertEquals(0, keys.answer(r, "OUT"));
    }

    /**
     * The place (V, #640): box (34,116,236,46), the soldier (72x139) at
     * (215,39), drawn over the box; the bar on row 1 at y 140-146, x
     * 38-265; the prompt's first glyphs at box + (5,9).
     */
    public void testTreatyBoxPlace() {
        final ClassicText t = texts("testTreatyBoxPlace");
        if (t == null) return;
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final ClassicFont tiny = pack.font(ClassicFont.TINY);
        final BufferedImage soldier = pack.image(ClassicPackFiles.ssKey(
            ClassicAdvisorBox.Portrait.SOLDIER.sprite));
        assertNotNull(tiny);
        assertNotNull(soldier);
        final ClassicAdvisorBox.Request r = ClassicWar.treatyRequest(t,
            player(getStandardGame(), "french"), "Montreal");
        final ClassicAdvisorBox.Layout l = ClassicAdvisorBox.layout(r, tiny, soldier);
        assertEquals(new Rectangle(34, 116, 236, 46), l.box);
        assertEquals(new Point(215, 39), l.portraitAt);
        assertFalse("the soldier stands over the box", l.under);
        assertEquals(2, l.promptLines());
        assertEquals(new Rectangle(38, 140, 228, 7), l.barRect(0));
        assertEquals(new Rectangle(38, 148, 228, 7), l.barRect(1));
        assertEquals(39, l.prompt.get(0).x);
        assertEquals(125, l.prompt.get(0).y);
        assertEquals(131, l.prompt.get(1).y);
        assertEquals("Eure Exzellenz.\"", ClassicAdvisorBox.plain(l.prompt.get(1).marked));
        assertEquals(43, l.rows.get(0).x);
        assertEquals(141, l.rows.get(0).y);
        assertEquals(149, l.rows.get(1).y);
    }

    /**
     * The game asks it ({@link ClassicGUI#confirmFortifyWar}): the box above
     * about the colony's owner; only «Friedensvertrag brechen.» says
     * "fortify", Escape and a box that could not open say no.  Without the
     * pack FreeCol's question about the colony's owner instead.
     */
    public void testTheGameAsksTheTreatyBox() {
        Topology.setCurrent(Topology.SQUARE);
        final Game game = getStandardGame();
        final Map map = getTestMap(plains);
        game.changeMap(map);
        final Player dutch = player(game, "dutch");
        final Player french = player(game, "french");
        dutch.setStance(french, Stance.PEACE);
        final Colony montreal = FreeColTestUtils.getColonyBuilder().player(french)
            .colonyTile(map.getTile(5, 8)).initialColonists(1).build();
        final Unit dragoon = new ServerUnit(game,
            montreal.getTile().getNeighbourOrNull(Direction.E), dutch,
            spec().getUnitType("model.unit.veteranSoldier"),
            spec().getRole("model.role.dragoon"));
        final ClassicGUI gui = new ClassicGUI(null);
        final ClassicGUISeamTest.KeyPrompter keys = new ClassicGUISeamTest.KeyPrompter();
        gui.prompter = keys;
        if (texts("testTheGameAsksTheTreatyBox") == null) return;
        final boolean pack = true;
        final Object[][] cases = {
            { new String[] { "ENTER" }, false },
            { new String[] { "ESC" }, false },
            { new String[] { "DOWN", "ENTER" }, true },
            { new String[] {}, false },          // left open: as closed
        };
        for (Object[] c : cases) {
            keys.press((String[]) c[0]);
            assertEquals(String.join(",", (String[]) c[0]), c[1],
                         gui.confirmFortifyWar(dragoon, montreal));
            final ClassicAdvisorBox.Request r = keys.boxes.get(keys.boxes.size() - 1);
            if (pack) {
                assertEquals(ClassicWar.TREATY_SECTION, r.id);
                assertTrue(r.plainText(), r.plainText().contains("Frz."));
            } else {
                assertEquals("confirm confirmHostile.peace", r.id);
            }
        }
        assertEquals(cases.length, keys.boxes.size());
        assertFalse("no colony: no box", gui.confirmFortifyWar(dragoon, null));
        assertEquals(cases.length, keys.boxes.size());
    }

    /**
     * The French soldier fortified next to our colony (the screenshot =
     * #53766): on the map the flag's F is the dark nation shade (fortified),
     * black while still fortifying, as any unit's ({@link ClassicHud#letterInk});
     * in the colony screen he stands on our work tile at war
     * ({@link ClassicColonyPanel#occupier}), fortified or not (#13962: a
     * soldier with "-"), no one at peace, never our own unit.
     */
    public void testTheFrenchSoldierAtOurColony() {
        for (Topology topology : new Topology[] { Topology.ISOMETRIC,
                                                  Topology.SQUARE }) {
            Topology.setCurrent(topology);
            final Game game = getStandardGame();
            final Map map = getTestMap(plains);
            game.changeMap(map);
            final Player dutch = player(game, "dutch");
            final Player french = player(game, "french");
            final Colony base = FreeColTestUtils.getColonyBuilder().player(dutch)
                .colonyTile(map.getTile(5, 8)).initialColonists(1).build();
            final Tile west = base.getTile().getNeighbourOrNull(Direction.W);
            final Unit soldier = new ServerUnit(game, west, french,
                spec().getUnitType("model.unit.veteranSoldier"),
                spec().getRole("model.role.soldier"));
            final Unit ours = new ServerUnit(game, west, dutch,
                spec().getUnitType("model.unit.veteranSoldier"),
                spec().getRole("model.role.soldier"));
            dutch.setStance(french, Stance.PEACE);
            french.setStance(dutch, Stance.PEACE);
            assertNull(topology.toString(), ClassicColonyPanel.occupier(west, dutch));
            dutch.setStance(french, Stance.WAR);
            french.setStance(dutch, Stance.WAR);
            assertSame(topology.toString(), soldier,
                       ClassicColonyPanel.occupier(west, dutch));
            soldier.setState(Unit.UnitState.FORTIFYING);
            assertSame(soldier, ClassicColonyPanel.occupier(west, dutch));
            assertEquals(0x000000, ClassicHud.letterInk(ClassicHud.ordersRow(soldier),
                                                        ClassicHud.nationDark(french)));
            soldier.setState(Unit.UnitState.FORTIFIED);
            assertEquals(ClassicHud.ORDERS_FORTIFIED, ClassicHud.ordersRow(soldier));
            assertEquals(ClassicHud.nationDark(french) & 0xFFFFFF,
                ClassicHud.letterInk(ClassicHud.ordersRow(soldier),
                                     ClassicHud.nationDark(french)));
            assertSame(soldier, ClassicColonyPanel.occupier(west, dutch));
            ours.setLocation(base.getTile());
            soldier.setLocation(map.getTile(12, 8));
            assertNull(ClassicColonyPanel.occupier(west, dutch));
            assertNull(ClassicColonyPanel.occupier(null, dutch));
            assertNull(ClassicColonyPanel.occupier(west, null));
        }
    }


    // The golden check

    /**
     * The golden check: the box as the game asks it, with the soldier,
     * drawn over the clip's frame #640 (opening_018), must give the frame's
     * pixels on the box and on the soldier's opaque pixels, 0 px off.
     */
    public void testGoldenAgainstTheClip() throws Exception {
        final String clips = System.getProperty(ClassicTerrainGoldenTest.CLIPS_PROPERTY);
        final File frameFile = (clips == null) ? null
            : new File(clips, "war-french/frame_000640.png");
        if (frameFile == null || !frameFile.isFile()) {
            System.err.println(getClass().getSimpleName()
                + ": golden check skipped, no recordings (-D"
                + ClassicTerrainGoldenTest.CLIPS_PROPERTY + ")");
            return;
        }
        final ClassicText t = texts("testGoldenAgainstTheClip");
        if (t == null) return;
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final ClassicAdvisorBox.Request r = ClassicWar.treatyRequest(t,
            player(getStandardGame(), "french"), "Montreal");
        final BufferedImage frame = ImageIO.read(frameFile);
        assertNotNull(frame);
        final int[] got = compare(frame, r, pack, 0);
        System.out.println(getClass().getSimpleName() + ": golden check, @HAVETREATY #640, "
            + got[0] + " px compared, " + got[1] + " off");
        assertTrue("compared " + got[0], got[0] > 236 * 46);
        assertEquals("pixels off", 0, got[1]);
    }

    /**
     * A box drawn over a full frame: the box and the portrait's opaque
     * pixels compared with the frame's.
     *
     * @param frame The 320x200 frame.
     * @param r The box.
     * @param pack The pack.
     * @param bar The barred row.
     * @return {compared, off}.
     */
    static int[] compare(BufferedImage frame, ClassicAdvisorBox.Request r,
                         ClassicPackFiles pack, int bar) {
        final ClassicFont tiny = pack.font(ClassicFont.TINY);
        final BufferedImage pic = (r.portrait.sprite == null) ? null
            : pack.image(ClassicPackFiles.ssKey(r.portrait.sprite));
        final ClassicAdvisorBox.Layout l = ClassicAdvisorBox.layout(r, tiny, pic);
        final BufferedImage canvas = new BufferedImage(320, 200,
            BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = canvas.createGraphics();
        g.drawImage(frame, 0, 0, null);
        ClassicAdvisorBox.paint(g, l, bar, pack.image(ClassicMenuBar.WOOD_KEY), tiny);
        g.dispose();
        final int[] out = new int[2];
        for (int y = 0; y < 200; y++) {
            for (int x = 0; x < 320; x++) {
                boolean on = l.box.contains(x, y);
                if (!on && pic != null) {
                    final int px = x - l.portraitAt.x, py = y - l.portraitAt.y;
                    on = px >= 0 && py >= 0 && px < pic.getWidth() && py < pic.getHeight()
                        && (pic.getRGB(px, py) >>> 24) != 0;
                }
                if (!on) continue;
                out[0]++;
                if ((frame.getRGB(x, y) & 0xFFFFFF) != (canvas.getRGB(x, y) & 0xFFFFFF)) {
                    out[1]++;
                }
            }
        }
        return out;
    }
}
