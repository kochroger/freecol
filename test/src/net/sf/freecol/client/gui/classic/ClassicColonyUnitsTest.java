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
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Role;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.Topology;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;
import net.sf.freecol.util.test.FreeColTestUtils;

import static net.sf.freecol.client.gui.classic.ClassicColonyUnits.Option.CLEAR;
import static net.sf.freecol.client.gui.classic.ClassicColonyUnits.Option.FORTIFY;
import static net.sf.freecol.client.gui.classic.ClassicColonyUnits.Option.FRONT;
import static net.sf.freecol.client.gui.classic.ClassicColonyUnits.Option.NOTHING;
import static net.sf.freecol.client.gui.classic.ClassicColonyUnits.Option.SENTRY;


/**
 * Waking a soldier fortified in our colony (clip 019,
 * {@code wake-in-colony-analysis/10-spec.md}; {@link ClassicColonyUnits}):
 * the unit box @COLONYUNIT -- its heading, its rows for each state, its
 * bar and Escape, the unit's icon and the 22 px column, its place, 0 px
 * against the clip's frame #959 and playthrough-2's #45170 -- and what its
 * rows do in the game ({@link ClassicGUI#colonyUnitOptions}): «Befehle
 * aufheben.» frees the unit without bringing it up on the map.
 */
public class ClassicColonyUnitsTest extends FreeColTestCase {

    private static final TileType plains = spec().getTileType("model.tile.plains");

    private static final Role dragoon = spec().getRole("model.role.dragoon");
    private static final Role pioneer = spec().getRole("model.role.pioneer");
    private static final Role soldier = spec().getRole("model.role.soldier");

    private static final UnitType artillery = spec().getUnitType("model.unit.artillery");
    private static final UnitType colonist = spec().getUnitType("model.unit.freeColonist");
    private static final UnitType hardyPioneer = spec().getUnitType("model.unit.hardyPioneer");
    private static final UnitType lumberJack = spec().getUnitType("model.unit.expertLumberJack");
    private static final UnitType servant = spec().getUnitType("model.unit.indenturedServant");
    private static final UnitType veteran = spec().getUnitType("model.unit.veteranSoldier");

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
        if (t == null || t.message(ClassicColonyUnits.UNIT_SECTION) == null) {
            System.err.println(getClass().getSimpleName() + "." + test
                + ": no pack texts (ant classic-assets), skipped");
            return null;
        }
        return t;
    }

    /** A game with our colony Base on (5,8), standing units on its tile. */
    private Colony base(Game game) {
        final Map map = getTestMap(plains);
        game.changeMap(map);
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        return FreeColTestUtils.getColonyBuilder().player(dutch)
            .colonyTile(map.getTile(5, 8)).initialColonists(1).build();
    }

    private static Unit unit(Colony c, UnitType type, Role role) {
        return (role == null)
            ? new ServerUnit(c.getGame(), c.getTile(), c.getOwner(), type)
            : new ServerUnit(c.getGame(), c.getTile(), c.getOwner(), type, role);
    }


    /**
     * The rows (V: the fortified dragoon, first: «Befehle aufheben.»,
     * «Wache ...», «Keine Veränderungen.»; the pioneer without orders,
     * first: «Wache ...», «Befestigen.», «Keine Veränderungen.»; I for the
     * rest): a row that would change nothing is not offered.
     */
    public void testRows() {
        final Colony c = base(getStandardGame());
        final Unit d = unit(c, lumberJack, dragoon);
        d.setState(Unit.UnitState.FORTIFYING);
        assertEquals(List.of(CLEAR, SENTRY, NOTHING), ClassicColonyUnits.options(d, true));
        d.setState(Unit.UnitState.FORTIFIED);
        assertEquals(List.of(CLEAR, SENTRY, NOTHING), ClassicColonyUnits.options(d, true));
        assertEquals(List.of(FRONT, CLEAR, SENTRY, NOTHING),
                     ClassicColonyUnits.options(d, false));
        final Unit p = unit(c, colonist, pioneer);
        assertEquals(List.of(SENTRY, FORTIFY, NOTHING), ClassicColonyUnits.options(p, true));
        assertEquals(List.of(FRONT, SENTRY, FORTIFY, NOTHING),
                     ClassicColonyUnits.options(p, false));
        p.setState(Unit.UnitState.SENTRY);
        assertEquals(List.of(CLEAR, FORTIFY, NOTHING), ClassicColonyUnits.options(p, true));
        p.setState(Unit.UnitState.ACTIVE);
        // A goto order is an order ("G"): it can be cleared.
        p.setDestination(c.getTile().getNeighbourOrNull(
            net.sf.freecol.common.model.Direction.N));
        assertTrue(ClassicColonyUnits.hasOrders(p));
        assertEquals(List.of(CLEAR, SENTRY, FORTIFY, NOTHING),
                     ClassicColonyUnits.options(p, true));
        // The answers: the row shown, nothing for none.
        final List<ClassicColonyUnits.Option> shown = List.of(CLEAR, SENTRY, NOTHING);
        assertEquals(CLEAR, ClassicColonyUnits.chosen(shown, 0));
        assertEquals(SENTRY, ClassicColonyUnits.chosen(shown, 1));
        assertEquals(NOTHING, ClassicColonyUnits.chosen(shown, 2));
        assertEquals(NOTHING, ClassicColonyUnits.chosen(shown, -1));
        assertEquals(NOTHING, ClassicColonyUnits.chosen(shown, 3));
        assertEquals(NOTHING, ClassicColonyUnits.chosen(null, 0));
    }

    /**
     * The heading's names (V: «(Erfahrene Holzfäller)» «(Dragoner)»;
     * playthrough-2 «(Pioniere)» with nothing before it; I for the rest):
     * %STRING0 the NAMES.TXT @UNIT name of the role or type, %STRING1 the
     * expert's @JOB name after a space, empty for a free colonist and a
     * unit that is no colonist.
     */
    public void testHeadingNames() {
        final ClassicText t = texts("testHeadingNames");
        final Colony c = base(getStandardGame());
        final Unit d = unit(c, lumberJack, dragoon);
        assertEquals("", ClassicColonyUnits.expertPart(null, d));
        if (t == null) return;
        assertEquals("Dragoner", ClassicColonyUnits.unitName(t, d));
        assertEquals(" (Erfahrene Holzfäller)", ClassicColonyUnits.expertPart(t, d));
        final Unit p = unit(c, colonist, pioneer);
        assertEquals("Pioniere", ClassicColonyUnits.unitName(t, p));
        assertEquals("", ClassicColonyUnits.expertPart(t, p));
        final Unit a = unit(c, artillery, null);
        assertEquals("Artillerie", ClassicColonyUnits.unitName(t, a));
        assertEquals("", ClassicColonyUnits.expertPart(t, a));
        final Unit v = unit(c, veteran, soldier);
        assertEquals("Soldaten", ClassicColonyUnits.unitName(t, v));
        assertEquals(" (Erfahrene Soldaten)", ClassicColonyUnits.expertPart(t, v));
        final Unit h = unit(c, hardyPioneer, pioneer);
        assertEquals(" (Gestählte Pioniere)", ClassicColonyUnits.expertPart(t, h));
        final Unit s = unit(c, servant, null);
        assertEquals("Siedler", ClassicColonyUnits.unitName(t, s));
        assertEquals(" (Verdingte Knechte)", ClassicColonyUnits.expertPart(t, s));
    }

    /** The clip's dragoon's icon in the box (the pack's ICONS.SS 076, the Dutch flag, F). */
    private static ClassicAdvisorBox.UnitIcon icon(ClassicPackFiles pack, Player owner,
                                                   String frame, String letter,
                                                   int unitRow) {
        final BufferedImage sp = (pack == null) ? null
            : pack.image(ClassicPackFiles.ssKey("ICONS.SS." + frame));
        return new ClassicAdvisorBox.UnitIcon(sp, ClassicHud.nationRgb(owner), letter,
                                              unitRow);
    }

    /**
     * The box (V, #959, OCR 0 errors): «Optionen für  {(Erfahrene
     * Holzfäller)} (Dragoner):» -- two spaces before the bracket, the
     * yellow part in brackets --, its three rows, the bar on row 1, Escape
     * «Keine Veränderungen.», no portrait, the unit's icon.  Its place: box
     * (31,76,258,48), the icon at (36,82), the prompt at x 58 (glyph top
     * 85), the rows at x 62 (tops 95, 103, 111), the bar x 57-284, y 94-100.
     */
    public void testTheBox() {
        final ClassicText t = texts("testTheBox");
        final Colony c = base(getStandardGame());
        final Unit d = unit(c, lumberJack, dragoon);
        d.setState(Unit.UnitState.FORTIFYING);
        final List<ClassicColonyUnits.Option> shown = ClassicColonyUnits.options(d, true);
        assertNull(ClassicColonyUnits.request(null, d, shown, null, "Base"));
        if (t == null) return;
        assertNull(ClassicColonyUnits.request(t, null, shown, null, "Base"));
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final ClassicAdvisorBox.Request r = ClassicColonyUnits.request(t, d, shown,
            icon(pack, c.getOwner(), "076", "F", 4), "Base");
        assertEquals(ClassicColonyUnits.UNIT_SECTION, r.id);
        assertEquals("Optionen für { (Erfahrene Holzfäller)} (Dragoner):",
                     r.paragraphs.get(0).get(0));
        assertEquals(List.of("Befehle aufheben.", "Wache / An Bord gehen.",
                             "Keine Veränderungen."), List.of(r.plainRows()));
        assertEquals(0, r.defaultRow);
        assertEquals("Escape: Keine Veränderungen.", 2, r.escapeAnswer());
        assertSame(ClassicAdvisorBox.Portrait.NONE, r.portrait);
        assertNotNull(r.unitIcon);
        assertEquals(ClassicAdvisorBox.ICON_COLUMN, r.iconColumn());
        assertEquals(230, r.width);
        final ClassicGUISeamTest.KeyPrompter keys = new ClassicGUISeamTest.KeyPrompter();
        assertEquals(0, keys.answer(r, "ENTER"));
        assertEquals(2, keys.answer(r, "ESC"));
        assertEquals(1, keys.answer(r, "DOWN", "ENTER"));

        final ClassicFont tiny = pack.font(ClassicFont.TINY);
        final ClassicAdvisorBox.Layout l = ClassicAdvisorBox.layout(r, tiny, null);
        assertEquals(new Rectangle(31, 76, 258, 48), l.box);
        assertEquals(new Point(36, 82), l.iconAt);
        assertEquals(1, l.promptLines());
        assertEquals(58, l.prompt.get(0).x);
        assertEquals(85, l.prompt.get(0).y);
        for (int i = 0; i < 3; i++) {
            assertEquals(62, l.rows.get(i).x);
            assertEquals(95 + 8 * i, l.rows.get(i).y);
        }
        assertEquals(new Rectangle(57, 94, 228, 7), l.barRect(0));
        assertEquals(new Rectangle(57, 102, 228, 7), l.barRect(1));
        // An ordinary box keeps its geometry.
        final ClassicAdvisorBox.Request plainBox = ClassicAdvisorBox.Request
            .builder("x").gameText(r.paragraphs.get(0)).width(230)
            .rows("a", "b", "c").build();
        final ClassicAdvisorBox.Layout pl = ClassicAdvisorBox.layout(plainBox, tiny, null);
        assertEquals(0, plainBox.iconColumn());
        assertEquals(236, pl.box.width);
        assertNull(pl.iconAt);
        assertEquals(pl.box.x + 4, pl.barRect(0).x);
        assertEquals(pl.box.width - 8, pl.barRect(0).width);
    }


    // What the rows do in the game

    /** The colony screen's unit orders, noted (no controller in a test). */
    private static final class FakeOrders implements ClassicGUI.UnitOrders {

        final List<String> log = new ArrayList<>();
        final ClassicGUI gui;

        FakeOrders(ClassicGUI gui) {
            this.gui = gui;
        }

        @Override
        public boolean clearOrders(Unit unit) {
            // The controller's choice of the freed unit, from inside the
            // state change, is dropped (clip 019 #1554).
            this.log.add("clear " + unit.getId() + " freeing="
                + ((this.gui.colonyWaking() == unit) ? "it" : "-")
                + " dropped=" + ClassicGUI.freedInColony(unit, this.gui.colonyWaking()));
            unit.setState(Unit.UnitState.ACTIVE);
            return true;
        }

        @Override
        public boolean changeState(Unit unit, Unit.UnitState state) {
            this.log.add("state " + unit.getId() + " " + state
                + " freeing=" + ((this.gui.colonyWaking() == null) ? "-" : "?"));
            unit.setState(state);
            return true;
        }
    }

    /**
     * {@link ClassicGUI#colonyUnitOptions}: the box asked about the unit
     * clicked, and its row done: «Befehle aufheben.» clears the orders
     * while the controller's choice of the unit to bring up is dropped for
     * it alone (it stays in the colony, the unit up on the map stays up),
     * «Wache» and «Befestigen.» give their orders, «Nach vorne bewegen.»
     * puts it first in the row, «Keine Veränderungen.» and Escape nothing.
     */
    public void testTheRowsInTheGame() {
        final ClassicText t = texts("testTheRowsInTheGame");
        if (t == null) return;
        final Colony c = base(getStandardGame());
        final Unit d = unit(c, lumberJack, dragoon);
        final Unit a1 = unit(c, artillery, null);
        final Unit a2 = unit(c, artillery, null);
        d.setState(Unit.UnitState.FORTIFYING);
        final ClassicGUI gui = new ClassicGUI(null);
        final ClassicGUISeamTest.KeyPrompter keys = new ClassicGUISeamTest.KeyPrompter();
        gui.colonyPrompter = keys;
        final FakeOrders orders = new FakeOrders(gui);
        gui.unitOrders = orders;
        gui.unitArt = u -> null;   // no game resources in a test

        // Escape: nothing.
        keys.press("ESC");
        gui.colonyUnitOptions(d, true);
        assertTrue(orders.log.toString(), orders.log.isEmpty());
        assertEquals(Unit.UnitState.FORTIFYING, d.getState());
        assertEquals(ClassicColonyUnits.UNIT_SECTION, keys.boxes.get(0).id);
        assertNotNull(keys.boxes.get(0).unitIcon);
        assertEquals("F", keys.boxes.get(0).unitIcon.letter);

        // Enter on the bar's row: «Befehle aufheben.» (V, clip 019).
        keys.press("ENTER");
        gui.colonyUnitOptions(d, true);
        assertEquals(List.of("clear " + d.getId() + " freeing=it dropped=true"),
                     orders.log);
        assertNull("freed only during the change", gui.colonyWaking());
        assertEquals(Unit.UnitState.ACTIVE, d.getState());
        assertFalse(ClassicGUI.freedInColony(a1, d));
        assertFalse(ClassicGUI.freedInColony(null, null));
        orders.log.clear();

        // Without orders now: the rows «Wache», «Befestigen.», «Keine ...».
        keys.press("DOWN", "ENTER");
        gui.colonyUnitOptions(d, true);
        assertEquals(List.of("Wache / An Bord gehen.", "Befestigen.",
                             "Keine Veränderungen."),
                     List.of(keys.boxes.get(keys.boxes.size() - 1).plainRows()));
        assertEquals(List.of("state " + d.getId() + " FORTIFYING freeing=-"), orders.log);
        orders.log.clear();
        keys.press("ENTER");
        gui.colonyUnitOptions(a1, false);   // «Nach vorne bewegen.» first
        assertTrue(orders.log.isEmpty());
        final ClassicColonyPanel.Units row = gui.colonyUnits();
        assertEquals(List.of(a1, d, a2), row.order(List.of(d, a1, a2)));
        keys.press("DOWN", "ENTER");
        gui.colonyUnitOptions(a2, false);   // «Wache / An Bord gehen.»
        assertEquals(List.of("state " + a2.getId() + " SENTRY freeing=-"), orders.log);
        keys.press("ENTER");
        gui.colonyUnitOptions(a2, false);
        assertEquals(List.of(a2, a1, d), row.order(List.of(d, a1, a2)));
    }

    /**
     * The row's order: the units moved to the front, the last first, then
     * the rest in the tile's order; and the cells' step: the original's 18
     * px while they fit, closer beyond, never off the band.
     */
    public void testTheRow() {
        final Colony c = base(getStandardGame());
        final Unit u1 = unit(c, artillery, null), u2 = unit(c, artillery, null),
            u3 = unit(c, artillery, null);
        final List<String> fronted = new ArrayList<>();
        assertEquals(List.of(u1, u2, u3), ClassicColonyUnits.order(List.of(u1, u2, u3), fronted));
        ClassicColonyUnits.front(fronted, u3);
        ClassicColonyUnits.front(fronted, u2);
        assertEquals(List.of(u2, u3, u1), ClassicColonyUnits.order(List.of(u1, u2, u3), fronted));
        ClassicColonyUnits.front(fronted, u3);
        assertEquals(List.of(u3, u2, u1), ClassicColonyUnits.order(List.of(u1, u2, u3), fronted));
        // A fronted unit that has gone is skipped.
        assertEquals(List.of(u2, u1), ClassicColonyUnits.order(List.of(u1, u2), fronted));
        assertTrue(ClassicColonyUnits.order(null, fronted).isEmpty());
        for (int n = 0; n <= 6; n++) assertEquals(18, ClassicColonyPanel.bandPitch(n));
        assertTrue(ClassicColonyPanel.bandPitch(7) < 18);
        for (int n = 7; n <= 40; n++) {
            final int p = ClassicColonyPanel.bandPitch(n);
            assertTrue(n + ": " + p, p >= 1 && 3 + 3 + p * (n - 1) + 16 <= 122);
        }
    }


    // The golden check

    /**
     * The golden check: the box as the game asks it, with the unit's icon,
     * drawn over the clip's frame (clip 019 #959: the fortified expert
     * lumberjack as dragoon; playthrough-2 #45170: a pioneer without
     * orders, a free colonist (I)), must give the frame's pixels on the
     * box, 0 px off (the icon's shadow included).
     */
    public void testGoldenAgainstTheClips() throws Exception {
        final String clips = System.getProperty(ClassicTerrainGoldenTest.CLIPS_PROPERTY);
        final File dir = (clips == null) ? null : new File(clips);
        if (dir == null || !new File(dir, "wake-in-colony/frame_000959.png").isFile()) {
            System.err.println(getClass().getSimpleName()
                + ": golden check skipped, no recordings (-D"
                + ClassicTerrainGoldenTest.CLIPS_PROPERTY + ")");
            return;
        }
        final ClassicText t = texts("testGoldenAgainstTheClips");
        if (t == null) return;
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final Colony c = base(getStandardGame());
        final Unit d = unit(c, lumberJack, dragoon);
        d.setState(Unit.UnitState.FORTIFYING);
        final Unit p = unit(c, colonist, pioneer);
        final Object[][] cases = {
            { "wake-in-colony/frame_000959.png", d, "076", "F", 4 },
            { "playthrough-2/frame_045170.png", p, "073", "-", 2 },
        };
        final StringBuilder fails = new StringBuilder();
        int compared = 0, arrow = 0;
        for (Object[] k : cases) {
            final Unit u = (Unit) k[1];
            final ClassicAdvisorBox.Request r = ClassicColonyUnits.request(t, u,
                ClassicColonyUnits.options(u, true),
                icon(pack, c.getOwner(), (String) k[2], (String) k[3], (Integer) k[4]),
                "Base");
            final File f = new File(dir, (String) k[0]);
            if (!f.isFile()) {
                System.err.println(getClass().getSimpleName() + ": no " + f + ", skipped");
                continue;
            }
            final int[] got = compare(ImageIO.read(f), r, pack);
            compared += got[0];
            arrow += got[2];
            if (got[1] > 0) fails.append(' ').append(k[0]).append('=').append(got[1]);
        }
        System.out.println(getClass().getSimpleName() + ": golden check, "
            + cases.length + " boxes, " + compared + " px compared, off:"
            + ((fails.length() == 0) ? " none" : fails.toString()) + ", "
            + arrow + " arrow px excused");
        assertEquals("pixels off:" + fails, 0, fails.length());
        // Not even one on the arrow's indices: the pointer is not on these
        // boxes, and the icon's black shadow is the original's too.
        assertEquals("pixels off on the arrow's indices", 0, arrow);
    }

    /**
     * A unit box drawn over a full frame, the bar on row 1: the box's
     * pixels compared with the frame's; a difference on an index the
     * original's arrow is drawn in (0, 7, 15) counts apart.
     *
     * @return {compared, off, off on the arrow's indices}.
     */
    private static int[] compare(BufferedImage frame, ClassicAdvisorBox.Request r,
                                 ClassicPackFiles pack) {
        final ClassicFont tiny = pack.font(ClassicFont.TINY);
        final ClassicAdvisorBox.Layout l = ClassicAdvisorBox.layout(r, tiny, null);
        final BufferedImage canvas = new BufferedImage(320, 200,
            BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = canvas.createGraphics();
        g.drawImage(frame, 0, 0, null);
        ClassicAdvisorBox.paint(g, l, 0, pack.image(ClassicMenuBar.WOOD_KEY), tiny);
        g.dispose();
        final Raster idx = (frame.getColorModel() instanceof IndexColorModel)
            ? frame.getRaster() : null;
        final int[] out = new int[3];
        for (int y = l.box.y; y < l.box.y + l.box.height; y++) {
            for (int x = l.box.x; x < l.box.x + l.box.width; x++) {
                out[0]++;
                if ((frame.getRGB(x, y) & 0xFFFFFF) == (canvas.getRGB(x, y) & 0xFFFFFF)) {
                    continue;
                }
                final int i = (idx == null) ? -1 : idx.getSample(x, y, 0);
                out[(i == 0 || i == 7 || i == 15) ? 2 : 1]++;
            }
        }
        return out;
    }
}
