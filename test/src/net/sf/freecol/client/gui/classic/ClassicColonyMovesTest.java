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
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import net.sf.freecol.client.ClientOptions;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.control.InGameController;
import net.sf.freecol.client.gui.GUI;
import net.sf.freecol.common.model.Building;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.ColonyTile;
import net.sf.freecol.common.model.Direction;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Role;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.common.model.WorkLocation;
import net.sf.freecol.common.networking.AbandonColonyMessage;
import net.sf.freecol.common.networking.ChangeSet;
import net.sf.freecol.common.networking.ChangeWorkTypeMessage;
import net.sf.freecol.common.networking.Connection;
import net.sf.freecol.common.networking.EquipForRoleMessage;
import net.sf.freecol.common.networking.PutOutsideColonyMessage;
import net.sf.freecol.common.networking.ServerAPI;
import net.sf.freecol.common.networking.WorkMessage;
import net.sf.freecol.common.option.GameOptions;
import net.sf.freecol.server.FreeColServer;
import net.sf.freecol.server.ServerTestHelper;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * Moving colonists on the colony screen (part U, build spec M4.8): the drop
 * matrix ({@link ClassicColonyMoves#resolve}), the pioneer's tools, the
 * pick among overlapping figures, the selection at the opening; the job
 * menus J2, «(Weitere)», J1 and a fence unit's against clip 020's menus
 * (the structure exactly, FreeCol's numbers reported beside the clip's;
 * the boxes drawn over the clip's frames at 0 px with the clip's numbers,
 * {@code -Dclassic.clips} and the pack, else skipped); and the moves on
 * the real {@link ClassicColonyPanel} with FreeCol's real client
 * controller and a real server game (the client's server API hands each
 * request to the server's own handler, client and server share the game,
 * as {@code ClassicEuropePanelTest}).
 */
public class ClassicColonyMovesTest extends FreeColTestCase {

    private static final GoodsType tools = spec().getGoodsType("model.goods.tools");
    private static final GoodsType muskets = spec().getGoodsType("model.goods.muskets");
    private static final GoodsType horses = spec().getGoodsType("model.goods.horses");
    private static final GoodsType food = spec().getGoodsType("model.goods.food");
    private static final GoodsType sugar = spec().getGoodsType("model.goods.sugar");
    private static final GoodsType ore = spec().getGoodsType("model.goods.ore");
    private static final GoodsType lumber = spec().getGoodsType("model.goods.lumber");

    // The pure rules

    /** The drop matrix (M4.8 target 1; V C1-C15, dago D3; I for the rule). */
    public void testTheDropMatrix() {
        final ClassicColonyMoves.Place B = ClassicColonyMoves.Place.BUILDING,
            T = ClassicColonyMoves.Place.TILE, F = ClassicColonyMoves.Place.FENCE,
            N = ClassicColonyMoves.Place.NONE;
        final Object[][] cases = {
            // from, to, same, blocked, original, expected
            { B, B, false, false, true, ClassicColonyMoves.Action.BUILDING_JOB },     // C1-C3
            { B, T, false, false, true, ClassicColonyMoves.Action.TILE_FARMER },      // C5, C10
            { T, T, false, false, true, ClassicColonyMoves.Action.TILE_KEEP_JOB },    // C14
            { T, B, false, false, true, ClassicColonyMoves.Action.BUILDING_JOB },     // C4, C15
            { F, B, false, false, true, ClassicColonyMoves.Action.BUILDING_JOB },     // C8, C9, C13
            { F, T, false, false, true, ClassicColonyMoves.Action.TILE_FARMER },      // I
            { B, F, false, false, true, ClassicColonyMoves.Action.ROLE_MENU },        // C7
            { T, F, false, false, true, ClassicColonyMoves.Action.ROLE_MENU },        // C11
            { F, F, false, false, true, ClassicColonyMoves.Action.NOTHING },
            { B, B, true, false, true, ClassicColonyMoves.Action.NOTHING },           // dago P1
            { T, T, true, false, true, ClassicColonyMoves.Action.NOTHING },
            { B, B, false, true, true, ClassicColonyMoves.Action.FULL },              // @MORETHANTHREE
            { T, B, false, true, true, ClassicColonyMoves.Action.FULL },
            { B, T, false, true, true, ClassicColonyMoves.Action.NOTHING },           // occupied
            { B, N, false, false, true, ClassicColonyMoves.Action.NOTHING },
            { N, B, false, false, true, ClassicColonyMoves.Action.NOTHING },
            // FreeCol's rules: the colony chooses the work on a tile.
            { B, T, false, false, false, ClassicColonyMoves.Action.TILE_FREECOL },
            { T, T, false, false, false, ClassicColonyMoves.Action.TILE_FREECOL },
            { B, B, false, false, false, ClassicColonyMoves.Action.BUILDING_JOB },
            { B, F, false, false, false, ClassicColonyMoves.Action.ROLE_MENU },
        };
        for (Object[] c : cases) {
            assertEquals(Arrays.toString(c), c[5], ClassicColonyMoves.resolve(
                (ClassicColonyMoves.Place) c[0], (ClassicColonyMoves.Place) c[1],
                (Boolean) c[2], (Boolean) c[3], (Boolean) c[4]));
        }
    }

    /** The pioneer's tools (V #254 «(60 Werkzeuge)», 75 → 15 at #121100; dago-colony2). */
    public void testPioneerTools() {
        assertEquals(60, ClassicColonyJobs.pioneerTools(75));
        assertEquals(20, ClassicColonyJobs.pioneerTools(0));
        assertEquals(20, ClassicColonyJobs.pioneerTools(10));
        assertEquals(40, ClassicColonyJobs.pioneerTools(44));
        assertEquals(100, ClassicColonyJobs.pioneerTools(100));
        assertEquals(100, ClassicColonyJobs.pioneerTools(250));
    }

    /** Overlapping figures: the first drawn wins (target 10, I by the buildings' rule). */
    public void testTheFirstDrawnFigureWins() {
        final List<Rectangle> r = Arrays.asList(new Rectangle(10, 0, 10, 16),
            new Rectangle(12, 0, 10, 16), new Rectangle(14, 0, 10, 16));
        assertEquals(0, ClassicColonyMoves.pick(r, 13, 5));
        assertEquals(0, ClassicColonyMoves.pick(r, 19, 5));
        assertEquals(1, ClassicColonyMoves.pick(r, 20, 5));
        assertEquals(2, ClassicColonyMoves.pick(r, 23, 5));
        assertEquals(-1, ClassicColonyMoves.pick(r, 24, 5));
    }

    /** The selection at the opening (target 9): founder, else the last, else the first. */
    public void testTheOpeningSelectionRule() {
        final List<String> row = Arrays.asList("a", "b", "c");
        assertEquals("b", ClassicColonyMoves.openingSelection(row, "b", "c"));
        assertEquals("c", ClassicColonyMoves.openingSelection(row, null, "c"));
        assertEquals("a", ClassicColonyMoves.openingSelection(row, null, "x"));
        assertEquals("a", ClassicColonyMoves.openingSelection(row, "x", null));
        assertNull(ClassicColonyMoves.openingSelection(new ArrayList<String>(), null, null));
    }

    /** The boxes' times, frames → ms at 70.0863 Hz (spec §1.4). */
    public void testTheTimes() {
        assertEquals(71.3, ClassicColonyMoves.ms(ClassicColonyMoves.J1_FRAMES), 0.1);
        assertEquals(42.8, ClassicColonyMoves.ms(ClassicColonyMoves.MORE_FRAMES), 0.1);
        assertEquals(114.1, ClassicColonyMoves.ms(ClassicColonyMoves.ABANDON_FRAMES), 0.1);
        assertEquals(442.3, ClassicColonyMoves.ms(ClassicColonyMoves.ABANDON_MAP_FRAMES), 0.1);
        assertEquals(456.6, ClassicColonyMoves.ms(ClassicColonyMoves.FULL_FRAMES), 0.1);
        assertEquals(627.8, ClassicColonyMoves.ms(ClassicColonyMoves.FENCE_MENU_FRAMES), 0.1);
    }


    // The menus against clip 020

    /** The texts, or null (then the menu tests skip with a note). */
    private ClassicText texts() {
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        if (t == null || t.label("CTITLE", ClassicColonyJobs.CTITLE_MORE) == null) {
            System.err.println(getName() + ": skipped, no pack texts (ant classic-assets)");
            return null;
        }
        return t;
    }

    /**
     * A menu's shape: the title, then per row its label with «*» for
     * yellow, «~» for grey, «/» when its cell is «a/b», «#» for a cell «n
     * Ware», «(» for a role's amounts, and «^» on the bar's row.
     */
    private static String shape(ClassicColonyJobs.Menu m) {
        final StringBuilder sb = new StringBuilder(m.title);
        for (int i = 0; i < m.rows.size(); i++) {
            final ClassicColonyJobs.Row r = m.rows.get(i);
            sb.append(" | ").append(r.label);
            if (r.yellow) sb.append('*');
            if (r.grey) sb.append('~');
            if (r.cell != null) {
                sb.append((r.cell.startsWith("(")) ? "(" : (r.cell.contains("/")) ? "/" : "#");
            }
            if (i == m.bar) sb.append('^');
        }
        return sb.toString();
    }

    /** The 16 works of clip 020's Base (no armory, church or school), each with its cell form. */
    private static String works(String tile, String yellow) {
        final String[] w = { "Farmer", "Zuckerpflanzer", "Tabakpflanzer", "Baumwollpflanzer",
            "Pelzjäger", "Holzfäller", "Erz-Bergarbeiter", "Silber-Bergarbeiter", "Fischer",
            "Schnapsbrenner", "Tabakhändler", "Weber", "Pelzhändler", "Schreiner", "Schmied",
            "Staatsmann" };
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < w.length; i++) {
            sb.append(" | ").append(w[i]).append((w[i].equals(yellow)) ? "*" : "")
                .append((i < 9) ? tile : "#");
        }
        return sb.toString();
    }

    /** The clip's cells of a menu, row by row (null: none), for the report and the golden. */
    private static final String[] CELLS_13 = { null, "3/3 Nahrungsmittel", "1/1 Zucker",
        "0/0 Tabak", "0/0 Baumwolle", "2/2 Felle", "8/8 Nutzholz", "0/4 Erz", "0/0 Silber",
        "0/4 Nahrungsmittel", "3 Rum", "3 Zigarren", "3 Stoff", "3 Mäntel", "6 Hämmer",
        "3 Werkzeuge", "3 Freiheitsglocken", null };
    private static final String[] CELLS_37 = { null, "5/5 Nahrungsmittel", "0/1 Zucker",
        "3/3 Tabak", "0/0 Baumwolle", "0/1 Felle", "0/4 Nutzholz", "0/4 Erz", "0/0 Silber",
        "0/4 Nahrungsmittel", "3 Rum", "3 Zigarren", "3 Stoff", "3 Mäntel", "6 Hämmer",
        "3 Werkzeuge", "3 Freiheitsglocken", null };
    private static final String[] CELLS_96 = { null, "2 Nahrungsmittel", "1 Zucker",
        "0 Tabak", "0 Baumwolle", "1 Felle", "4 Nutzholz", "1 Erz", "0 Silber",
        "6 Nahrungsmittel", "3 Rum", "3 Zigarren", "3 Stoff", "3 Mäntel", "6 Hämmer",
        "3 Werkzeuge", "3 Freiheitsglocken", null };
    private static final String[] CELLS_103 = { null, "2/2 Nahrungsmittel", "0/2 Zucker",
        "0/0 Tabak", "0/0 Baumwolle", "0/1 Felle", "0/4 Nutzholz", "5/5 Erz", "0/0 Silber",
        "0/4 Nahrungsmittel", "3 Rum", "3 Zigarren", "3 Stoff", "3 Mäntel", "6 Hämmer",
        "3 Werkzeuge", "3 Freiheitsglocken", null };
    private static final String[] CELLS_253 = { "2 Nahrungsmittel", "1 Zucker", "0 Tabak",
        "0 Baumwolle", "1 Felle", "4 Nutzholz", "1 Erz", "0 Silber", "4 Nahrungsmittel",
        "3 Rum", "3 Zigarren", "3 Stoff", "3 Mäntel", "6 Hämmer", "6 Werkzeuge",
        "4 Freiheitsglocken", null };
    private static final String[] CELLS_ROLES_20 = { null, null, "(20 Werkzeuge)",
        "(50 Musketen)", "(50 Pferde)", "(50 Musketen) (50 Pferde)" };
    private static final String[] CELLS_254 = { null, null, "(60 Werkzeuge)",
        "(50 Musketen)", "(50 Pferde)", "(50 Musketen) (50 Pferde)" };

    /** One menu case: the state, the expected shape, the clip's frame, cells and bar. */
    private static final class Case {
        final String name;
        final ClassicColonyJobs.Menu menu;
        final String shape;
        final int frame;
        final String[] cells;

        Case(String name, ClassicColonyJobs.Menu menu, String shape, int frame,
             String[] cells) {
            this.name = name;
            this.menu = menu;
            this.shape = shape;
            this.frame = frame;
            this.cells = cells;
        }
    }

    /**
     * The menus of clip 020 rebuilt in the clip's states, each against
     * its index row (#13, #16, #18, #37, #77, #96, #103, #253, #254).
     */
    private List<Case> clipMenus(ClassicText t) {
        final List<Case> out = new ArrayList<>();
        final String clear = "(SPEZIALFÄHIGKEIT LÖSCHEN)";
        final String more = "(Weitere)";
        {
            // #13: the lumberjack expert dropped from the Sägewerk onto N, a Farmer.
            final ClassicColonyBuildingsTest.State s = ClassicColonyBuildingsTest.base1539();
            final Unit u = s.on("expertLumberJack", Direction.N, "grain");
            s.finish();
            out.add(new Case("#13", ClassicColonyJobs.menu(t, s.colony, u,
                    ClassicColonyJobs.Kind.WORKER, 1),
                "Beruf wählen für Erfahrene Holzfäller(Farmer): | " + clear
                    + works("/", "Holzfäller").replaceFirst("Farmer/", "Farmer/^")
                    + " | " + more, 4150, CELLS_13));
        }
        {
            // #16 and #18: the veteran soldier as Staatsmann (Musketen 50, Werkzeuge 10).
            final ClassicColonyBuildingsTest.State s = ClassicColonyBuildingsTest.base1539();
            final Unit u = s.in("veteranSoldier", "townHall");
            s.finish();
            s.colony.addGoods(muskets, 50);
            s.colony.addGoods(tools, 10);
            final String roles = " | Siedler | Pionier~( | Soldat*( | Späher~( | Dragoner~(";
            out.add(new Case("#16", ClassicColonyJobs.menu(t, s.colony, u,
                    ClassicColonyJobs.Kind.FENCE_DROP, 1),
                "Beruf wählen für Erfahrene Soldaten(Staatsmann): | Staatsmann^" + roles,
                6400, new String[] { null, null, "(20 Werkzeuge)", "(50 Musketen)",
                                     "(50 Pferde)", "(50 Musketen) (50 Pferde)" }));
            out.add(new Case("#18", ClassicColonyJobs.menu(t, s.colony, u,
                    ClassicColonyJobs.Kind.WORKER, 2),
                "Beruf wählen für Erfahrene Soldaten(Staatsmann): | " + more + "^" + roles,
                7250, CELLS_ROLES_20));
        }
        {
            // #37: the expert farmer on NW, at his own work.
            final ClassicColonyBuildingsTest.State s = ClassicColonyBuildingsTest.base1539();
            final Unit u = s.on("expertFarmer", Direction.NW, "grain");
            s.finish();
            out.add(new Case("#37", ClassicColonyJobs.menu(t, s.colony, u,
                    ClassicColonyJobs.Kind.WORKER, 1),
                "Beruf wählen für Erfahrene Farmer: | " + clear
                    + works("/", "Farmer").replaceFirst("Farmer\\*/", "Farmer*/^")
                    + " | " + more, 18800, CELLS_37));
        }
        {
            // #77: Prov.'s seasoned scout planting sugar on N, dropped on the fence (Pferde 50).
            final ClassicColonyBuildingsTest.State s = ClassicColonyBuildingsTest.base1539();
            final Unit u = s.on("seasonedScout", Direction.N, "sugar");
            s.finish();
            s.colony.addGoods(horses, 50);
            out.add(new Case("#77", ClassicColonyJobs.menu(t, s.colony, u,
                    ClassicColonyJobs.Kind.FENCE_DROP, 1),
                "Beruf wählen für Kampferprobte Späher(Zuckerpflanzer): | Zuckerpflanzer^"
                    + " | Siedler | Pionier~( | Soldat~( | Späher*( | Dragoner~(",
                35300, CELLS_ROLES_20));
        }
        {
            // #96: an expert fisherman standing at the fence, clicked.
            final ClassicColonyBuildingsTest.State s = ClassicColonyBuildingsTest.base1539();
            s.on("freeColonist", Direction.NW, "grain");
            s.finish();
            final Unit u = s.outside("expertFisherman", null);
            out.add(new Case("#96", ClassicColonyJobs.menu(t, s.colony, u,
                    ClassicColonyJobs.Kind.OUTSIDE, 1),
                "Beruf wählen für Erfahrene Fischer: | " + clear + "^"
                    + works("#", "Fischer") + " | " + more, 46150, CELLS_96));
        }
        {
            // #103: the sugar expert mining ore on NO.
            final ClassicColonyBuildingsTest.State s = ClassicColonyBuildingsTest.base1539();
            final Unit u = s.on("masterSugarPlanter", Direction.NE, "ore");
            s.finish();
            out.add(new Case("#103", ClassicColonyJobs.menu(t, s.colony, u,
                    ClassicColonyJobs.Kind.WORKER, 1),
                "Beruf wählen für Meister-Zuckerpflanzer(Erz-Bergarbeiter): | " + clear
                    + works("/", "Zuckerpflanzer").replaceFirst("Erz-Bergarbeiter/",
                                                                "Erz-Bergarbeiter/^")
                    + " | " + more, 48550, CELLS_103));
        }
        {
            // #253 and #254: a free colonist at the fence (Werkzeuge 75, Musketen 50).
            final ClassicColonyBuildingsTest.State s = ClassicColonyBuildingsTest.base1539();
            s.on("freeColonist", Direction.NW, "grain");
            s.finish();
            s.colony.addGoods(tools, 75);
            s.colony.addGoods(muskets, 50);
            final Unit u = s.outside("freeColonist", null);
            out.add(new Case("#253", ClassicColonyJobs.menu(t, s.colony, u,
                    ClassicColonyJobs.Kind.OUTSIDE, 1),
                "Beruf wählen für Freie Siedler:"
                    + works("#", "-").replaceFirst("Farmer#", "Farmer#^") + " | " + more,
                120690, CELLS_253));
            out.add(new Case("#254", ClassicColonyJobs.menu(t, s.colony, u,
                    ClassicColonyJobs.Kind.OUTSIDE, 2),
                "Beruf wählen für Freie Siedler: | " + more
                    + " | Siedler^ | Pionier( | Soldat( | Späher~( | Dragoner~(",
                120850, CELLS_254));
        }
        return out;
    }

    /**
     * The menus' structure as clip 020 shows it (targets 3-6): the title
     * with «(job)» only off the speciality, «LÖSCHEN» only for an expert,
     * the 16 works and «(Weitere)», the roles with their amounts, yellow
     * and grey rows, «a/b» only for a tile worker's tile works, the bar.
     * FreeCol's numbers are printed beside the clip's (they differ where
     * FreeCol's yields do: S3's open question).
     */
    public void testTheMenusAsTheClip() {
        final ClassicText t = texts();
        if (t == null) return;
        final StringBuilder report = new StringBuilder();
        int same = 0, cells = 0;
        for (Case c : clipMenus(t)) {
            assertNotNull(c.name, c.menu);
            assertEquals(c.name, c.shape, shape(c.menu));
            for (int i = 0; i < c.menu.rows.size(); i++) {
                final String ours = c.menu.rows.get(i).cell;
                final String clip = (i < c.cells.length) ? c.cells[i] : null;
                if (clip == null) {
                    assertNull(c.name + " row " + i, ours);
                    continue;
                }
                cells++;
                if (clip.equals(ours)) {
                    same++;
                } else {
                    report.append(' ').append(c.name).append(':').append(c.menu.rows.get(i).label)
                        .append(" clip «").append(clip).append("» ours «").append(ours).append('»');
                }
            }
        }
        System.out.println(getName() + ": " + same + " of " + cells
            + " cells as the clip; FreeCol's numbers elsewhere:" + report);
        // The roles' amounts are the original's (50, the tools rule).
        assertTrue(report.toString(), !report.toString().contains("Werkzeuge)")
                   && !report.toString().contains("Musketen)"));
    }

    /**
     * The J2 rules beyond the clip's colonies (dago-colony2 §3.2): an
     * armory adds Waffenschmied as the 16th work and moves Staatsmann
     * onto the «(Weitere)» page; FreeCol's chapel offers no Prediger; a
     * church offers Missionar; a teacher's school Lehrer.
     */
    public void testTheWorksOfAColony() {
        final ClassicText t = texts();
        if (t == null) return;
        final ClassicColonyBuildingsTest.State s = ClassicColonyBuildingsTest.base1539();
        final Unit u = s.in("masterBlacksmith", "blacksmithHouse");
        s.finish();
        assertEquals(-1, ClassicColonyJobs.menu(t, s.colony, u, ClassicColonyJobs.Kind.WORKER, 1)
                     .rowOf(ClassicColonyJobs.Pick.JOB, 16));
        s.build("armory");
        final ClassicColonyJobs.Menu m = ClassicColonyJobs.menu(t, s.colony, u,
            ClassicColonyJobs.Kind.WORKER, 1);
        assertEquals("16 works + LÖSCHEN + (Weitere)", 18, m.rows.size());
        assertEquals("Waffenschmied", m.rows.get(16).label);
        assertEquals(-1, m.rowOf(ClassicColonyJobs.Pick.JOB, 17));
        final ClassicColonyJobs.Menu p2 = ClassicColonyJobs.menu(t, s.colony, u,
            ClassicColonyJobs.Kind.WORKER, 2);
        assertEquals("Staatsmann", p2.rows.get(1).label);
        assertEquals(-1, p2.rowOf(ClassicColonyJobs.Pick.ROLE, ClassicColonyLayout.JOB_MISSIONARY));
        assertEquals("the bar on (Weitere): his work is on page 1", 0, p2.bar);
        assertEquals("the master blacksmith's work barred and yellow", m.bar,
                     m.rowOf(ClassicColonyJobs.Pick.JOB, 14));
        assertTrue(m.rows.get(m.bar).yellow);
        s.build("church");
        final ClassicColonyJobs.Menu c2 = ClassicColonyJobs.menu(t, s.colony, u,
            ClassicColonyJobs.Kind.WORKER, 2);
        assertEquals("Prediger", c2.rows.get(1).label);
        assertTrue(c2.rowOf(ClassicColonyJobs.Pick.ROLE, ClassicColonyLayout.JOB_MISSIONARY) > 0);
    }

    /**
     * The boxes drawn over clip 020's frames (targets 3-6, 8): our menus
     * with the clip's numbers (FreeCol's differ, see above), our @ABANDON
     * of «Provisorische Kolonie» with MSS5, each 0 px off apart from the
     * original arrow; J1 #77 only off on its first row, which the clip
     * shows yellow for a reason unknown (built green, target 5).
     */
    public void testTheBoxesOverTheClip() throws Exception {
        final ClassicText t = texts();
        if (t == null) return;
        final ClassicColonyGolden gold = ClassicColonyGolden.open(getName());
        if (gold == null) return;
        final ClassicPackFiles pack = gold.pack;
        final ClassicFont tiny = pack.font(ClassicFont.TINY);
        final BufferedImage wood = pack.image(ClassicMenuBar.WOOD_KEY);
        final StringBuilder out = new StringBuilder();
        int bad = 0;
        for (Case c : clipMenus(t)) {
            final List<ClassicColonyJobs.Row> rows = new ArrayList<>();
            for (int i = 0; i < c.menu.rows.size(); i++) {
                final ClassicColonyJobs.Row r = c.menu.rows.get(i);
                final boolean yellow = r.yellow || ("#77".equals(c.name) && i == 0
                                                    && this.clipYellow);
                rows.add(new ClassicColonyJobs.Row(r.pick, r.job, r.label,
                    (i < c.cells.length) ? c.cells[i] : null, yellow, r.grey, r.count));
            }
            final ClassicColonyJobs.Menu m = new ClassicColonyJobs.Menu(c.menu.kind,
                c.menu.page, c.menu.title, rows, c.menu.bar);
            final ClassicAdvisorBox.Request r = ClassicColonyJobs.request(t, m, 0.0, -1.0);
            final int[] got = over(gold, c.frame, ClassicAdvisorBox.layout(r, tiny, null),
                                   m.bar, wood, tiny);
            out.append(' ').append(c.name).append(" #").append(c.frame).append(' ')
                .append(got[0]).append('/').append(got[1]).append(" arrow ").append(got[2]);
            if ("#77".equals(c.name)) {
                // The first row: our green against the clip's yellow.
                assertTrue("#77 off only on row 1: " + got[1], got[1] == got[3]);
                assertTrue("#77's first row differs", got[3] > 0);
                continue;
            }
            bad += got[1];
        }
        // @ABANDON (#35450: Prov., another colony exists, the bar on «Niemals!»).
        final java.util.Map<String, String> v = new java.util.HashMap<>();
        v.put("STRING0", "Provisorische Kolonie");
        final ClassicAdvisorBox.Request ab = ClassicAdvisorBox.fromGameText("ABANDON",
            t.message("ABANDON"), v).portrait(ClassicAdvisorBox.Portrait.COLONIST).build();
        final ClassicAdvisorBox.Layout al = ClassicAdvisorBox.layout(ab, tiny,
            pack.image(ClassicPackFiles.ssKey(ab.portrait.sprite)));
        assertEquals(new Rectangle(42, 105, 236, 52), al.box);
        assertEquals(1, ab.defaultRow);
        assertEquals("Escape: «Niemals!»", 1, ab.cancelRow);
        final int[] a = over(gold, 35450, al, 1, wood, tiny);
        out.append(" @ABANDON #35450 ").append(a[0]).append('/').append(a[1])
            .append(" arrow ").append(a[2]);
        bad += a[1];
        System.out.println(getName() + ":" + out);
        assertEquals("pixels off:" + out, 0, bad);
    }

    /** Whether the J1 #77 pass forces the clip's yellow (false: ours, green). */
    private boolean clipYellow = false;

    /**
     * Draw a box over a clip frame and compare the box and its portrait.
     *
     * @return {compared, off, off on the arrow, off on the first row's rectangle}.
     */
    private static int[] over(ClassicColonyGolden gold, int frame, ClassicAdvisorBox.Layout l,
                              int bar, BufferedImage wood, ClassicFont tiny) throws Exception {
        final BufferedImage f = gold.frame(frame);
        final ClassicColonyGolden.Mask arrow = gold.pointer(f);
        final BufferedImage canvas = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = canvas.createGraphics();
        g.drawImage(f, 0, 0, null);
        ClassicAdvisorBox.paint(g, l, bar, wood, tiny);
        g.dispose();
        final Rectangle row0 = (l.rows.isEmpty()) ? new Rectangle()
            : new Rectangle(l.box.x, l.rows.get(0).y - 1, l.box.width, 7);
        final int[] out = new int[4];
        for (int y = 0; y < 200; y++) {
            for (int x = 0; x < 320; x++) {
                final boolean inPortrait = l.portrait != null && l.portraitAt != null
                    && x >= l.portraitAt.x && y >= l.portraitAt.y
                    && x < l.portraitAt.x + l.portrait.getWidth()
                    && y < l.portraitAt.y + l.portrait.getHeight()
                    && (l.portrait.getRGB(x - l.portraitAt.x, y - l.portraitAt.y) >>> 24) > 128;
                if (!l.box.contains(x, y) && !inPortrait) continue;
                out[0]++;
                if ((f.getRGB(x, y) & 0xFFFFFF) == (canvas.getRGB(x, y) & 0xFFFFFF)) continue;
                if (arrow.get(x, y)) {
                    out[2]++;
                    continue;
                }
                out[1]++;
                if (row0.contains(x, y)) out[3]++;
            }
        }
        return out;
    }


    // The moves on the screen, with the real controller and server

    /**
     * The client's server API: each request goes to the server's own
     * handler for our player (as over the connection), noted in a log.
     */
    private static final class Loopback extends ServerAPI {

        final FreeColServer server;
        final ServerPlayer player;
        final List<String> log = new ArrayList<>();

        Loopback(FreeColServer server, ServerPlayer player) {
            this.server = server;
            this.player = player;
        }

        private boolean handle(String what, ChangeSet cs) {
            final String s = (cs == null) ? "" : cs.toString();
            this.log.add(what + ((s.contains("error") || s.contains("reject")) ? " refused" : ""));
            return true;
        }

        @Override
        public boolean work(Unit unit, WorkLocation wl) {
            return handle("work " + unit.getId() + " " + wl.getId(),
                new WorkMessage(unit, wl).serverHandler(this.server, this.player));
        }

        @Override
        public boolean changeWorkType(Unit unit, GoodsType type) {
            return handle("workType " + unit.getId() + " " + type.getSuffix(),
                new ChangeWorkTypeMessage(unit, type).serverHandler(this.server, this.player));
        }

        @Override
        public boolean putOutsideColony(Unit unit) {
            return handle("outside " + unit.getId(),
                new PutOutsideColonyMessage(unit).serverHandler(this.server, this.player));
        }

        @Override
        public boolean equipUnitForRole(Unit unit, Role role, int roleCount) {
            return handle("equip " + unit.getId() + " " + role.getSuffix() + " " + roleCount,
                new EquipForRoleMessage(unit, role, roleCount)
                    .serverHandler(this.server, this.player));
        }

        @Override
        public boolean abandonColony(Colony colony) {
            return handle("abandon " + colony.getName(),
                new AbandonColonyMessage(colony).serverHandler(this.server, this.player));
        }

        @Override
        public Connection connect(String name, String host, int port) {
            return null;
        }

        @Override
        public boolean disconnect() {
            return true;
        }

        @Override
        public Connection reconnect() {
            return null;
        }

        @Override
        public Connection getConnection() {
            return null;
        }
    }

    private static void set(FreeColClient fcc, String name, Object value) throws Exception {
        final Field f = FreeColClient.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(fcc, value);
    }

    /** A client of the server's game with FreeCol's real controller (as the Europe test's). */
    private static FreeColClient client(Game game, ServerPlayer me, Loopback api)
        throws Exception {
        final Class<?> uc = Class.forName("sun.misc.Unsafe");
        final Field theUnsafe = uc.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        final FreeColClient fcc = (FreeColClient)uc
            .getMethod("allocateInstance", Class.class)
            .invoke(theUnsafe.get(null), FreeColClient.class);
        set(fcc, "gui", new GUI(fcc) {
                @Override
                public void playSound(String sound) {}
            });
        set(fcc, "serverAPI", api);
        set(fcc, "clientOptions", new ClientOptions() {
                @Override
                public boolean getBoolean(String id) {
                    return false;
                }
            });
        set(fcc, "inGameController", new InGameController(fcc));
        fcc.setGame(game);
        fcc.setMyPlayer(me);
        set(fcc, "inGame", Boolean.TRUE);
        game.setCurrentPlayer(me);
        return fcc;
    }

    /** The screen's boxes: the answers given in turn, the requests kept. */
    private static final class Boxes implements ClassicColonyPanel.Units {
        final Deque<Integer> answers = new ArrayDeque<>();
        final List<ClassicAdvisorBox.Request> asked = new ArrayList<>();

        @Override
        public List<Unit> order(List<Unit> standing) {
            return standing;
        }

        @Override
        public void options(Unit unit, boolean first) {}

        @Override
        public int ask(ClassicAdvisorBox.Request r) {
            this.asked.add(r);
            return (this.answers.isEmpty()) ? ClassicAdvisorBox.Bar.DISMISSED
                : this.answers.poll();
        }

        /** @return The last box's rows, plain. */
        String last() {
            final ClassicAdvisorBox.Request r = this.asked.get(this.asked.size() - 1);
            return r.id + " " + Arrays.toString(r.plainRows()) + " bar=" + r.defaultRow;
        }
    }

    /** What a screen test works with. */
    private static final class Scene {
        Game game;
        ServerPlayer dutch;
        Colony colony;
        Loopback api;
        FreeColClient fcc;
        Boxes boxes;
        ClassicColonyPanel panel;
        final List<String> events = new ArrayList<>();
        final List<Runnable> later = new ArrayList<>();
    }

    /**
     * A colony of the Dutch on a plains map with a mountains tile NE (ore, no grain) and
     * a forest N (lumber), its colonists as given (the colony builder's
     * free colonists, made the types given and put in buildings or on
     * tiles by the test), the screen over it (scale 3).
     */
    private Scene scene(int colonists, boolean original) throws Exception {
        final Scene s = new Scene();
        spec().setBoolean(GameOptions.ORIGINAL_DROP_JOBS, original);
        s.game = ServerTestHelper.startServerGame(getTestMap(true));
        s.dutch = getServerPlayer(s.game, "model.nation.dutch");
        s.colony = createStandardColony(colonists);
        final Tile c = s.colony.getTile();
        c.getNeighbourOrNull(Direction.NE).setType(spec().getTileType("model.tile.mountains"));
        c.getNeighbourOrNull(Direction.N).setType(spec().getTileType("model.tile.mixedForest"));
        s.colony.invalidateCache();
        s.api = new Loopback(ServerTestHelper.getServer(), s.dutch);
        s.fcc = client(s.game, s.dutch, s.api);
        s.boxes = new Boxes();
        ClassicColonyPanel.forgetFigureOrders();
        ClassicColonyPanel.forgetSelections();
        s.panel = new ClassicColonyPanel(s.fcc, ClassicColonyBuildingsTest.library(), s.colony,
            () -> s.events.add("closed"), s.boxes, ClassicSlide.SYSTEM, false);
        s.panel.setLater((ms, r) -> {
                s.events.add("later " + ms);
                s.later.add(r);
            });
        s.panel.setSize(960, 600);
        paint(s.panel);
        return s;
    }

    @Override
    public void tearDown() throws Exception {
        spec().setBoolean(GameOptions.ORIGINAL_DROP_JOBS, false);
        ClassicColonyPanel.setTileCursor(ClassicColonyLayout.CENTRE_TILE);
        ClassicColonyPanel.forgetSelections();
        ClassicColonyPanel.forgetFigureOrders();
        ServerTestHelper.stopServerGame();
        super.tearDown();
    }

    /** Paint the screen as Swing would (scale 3): the models and the hit places exist. */
    private static BufferedImage paint(ClassicColonyPanel p) {
        final BufferedImage img = new BufferedImage(960, 600, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = img.createGraphics();
        p.paint(g);
        g.dispose();
        return img;
    }

    /** A synthetic mouse event at a canvas point (scale 3), dispatched to the screen. */
    private static void mouse(ClassicColonyPanel p, int id, int vx, int vy) {
        int mods = 0;
        if (id == MouseEvent.MOUSE_PRESSED || id == MouseEvent.MOUSE_DRAGGED) {
            mods |= InputEvent.BUTTON1_DOWN_MASK;
        }
        final int button = (id == MouseEvent.MOUSE_DRAGGED) ? MouseEvent.NOBUTTON
            : MouseEvent.BUTTON1;
        p.dispatchEvent(new MouseEvent(p, id, System.currentTimeMillis(), mods,
                                       vx * 3 + 1, vy * 3 + 1, 1, false, button));
    }

    /** Press at a, drag over the way to b (painting meanwhile), release at b. */
    private static void drag(ClassicColonyPanel p, int ax, int ay, int bx, int by) {
        mouse(p, MouseEvent.MOUSE_PRESSED, ax, ay);
        for (int i = 1; i <= 4; i++) {
            mouse(p, MouseEvent.MOUSE_DRAGGED, ax + (bx - ax) * i / 4, ay + (by - ay) * i / 4);
        }
        mouse(p, MouseEvent.MOUSE_RELEASED, bx, by);
        paint(p);
    }

    /** Press and release at a point. */
    private static void click(ClassicColonyPanel p, int vx, int vy) {
        mouse(p, MouseEvent.MOUSE_PRESSED, vx, vy);
        mouse(p, MouseEvent.MOUSE_RELEASED, vx, vy);
        paint(p);
    }

    /** The centre of a tile of the 3x3 map (its place 0-8). */
    private static int[] tile(int place) {
        return new int[] { ClassicColonyLayout.TILES_X + 24 * (place % 3) + 12,
                           ClassicColonyLayout.TILES_Y + 24 * (place / 3) + 12 };
    }

    /** A point on a unit's figure where a press picks him (the screen's own hit test). */
    private static int[] at(ClassicColonyPanel p, Unit u) {
        for (int y = 8; y < 128; y++) {
            for (int x = 0; x < 320; x++) {
                final ClassicColonyPanel.Hit h = p.hitAt(x, y);
                if (h != null && h.unit == u) return new int[] { x, y };
            }
        }
        fail("no figure of " + u);
        return null;
    }

    /** A free point of a building's slot (its sprite away from figures). */
    private static int[] slotOf(ClassicColonyPanel p, Building b) {
        for (ClassicColonyBuildings.Placed pl : p.area().slots) {
            if (pl.target != b) continue;
            final Rectangle r = pl.slot.bounds();
            for (int y = r.y; y < r.y + r.height; y++) {
                for (int x = r.x; x < r.x + r.width; x++) {
                    if (p.hitAt(x, y) == null && ClassicColonyPanel.slotAt(x, y) == pl.slot.index) {
                        return new int[] { x, y };
                    }
                }
            }
        }
        fail("no slot of " + b);
        return null;
    }

    /** The fence picture (slot X), clear of the units on it. */
    private static final int[] FENCE = { 128, 110 };

    /** The colony's building of a type. */
    private static Building building(Colony c, String id) {
        return c.getBuilding(spec().getBuildingType("model.building." + id));
    }

    /** A colonist's place on a tile (direction from the colony). */
    private static ColonyTile tileOf(Colony c, Direction d) {
        return c.getColonyTile(c.getTile().getNeighbourOrNull(d));
    }

    /**
     * R2 (levi): a colonist dragged from a building onto a tile becomes a
     * Farmer, the expert too (V C5 #4006: the lumberjack expert; C10): here
     * an expert ore miner onto the mountains, where FreeCol gives him ore;
     * without the rule FreeCol's choice, one call only.
     */
    public void testBuildingToTileMakesAFarmer() throws Exception {
        buildingToTile(true);
    }

    /** Without the levi rule: FreeCol's choice of work, no second call. */
    public void testBuildingToTileFreeColsChoice() throws Exception {
        buildingToTile(false);
    }

    private void buildingToTile(boolean original) throws Exception {
        final Scene s = scene(2, original);
        final List<Unit> all = s.colony.getUnitList();
        final Unit u = all.get(0);
        u.changeType(spec().getUnitType("model.unit.expertOreMiner"));
        u.setLocation(building(s.colony, "carpenterHouse"));
        all.get(1).setLocation(tileOf(s.colony, Direction.S));
        all.get(1).changeWorkType(ClassicColonyJobs.goodsOf(spec(), 0));
        s.colony.addGoods(food, 100);
        s.colony.invalidateCache();
        paint(s.panel);
        final int[] a = at(s.panel, u);
        final int[] b = tile(2);    // NE, the mountains
        drag(s.panel, a[0], a[1], b[0], b[1]);
        assertSame(original + " " + s.api.log, tileOf(s.colony, Direction.NE), u.getLocation());
        if (original) {
            assertEquals("R2: Farmer " + s.api.log, ClassicColonyJobs.goodsOf(spec(), 0),
                         u.getWorkType());
            assertTrue(s.api.log.toString(), s.api.log.get(s.api.log.size() - 1)
                       .startsWith("workType"));
        } else {
            assertEquals("only the work call " + s.api.log, 1, s.api.log.size());
            System.out.println(getName() + ": FreeCol's choice on the mountains: "
                               + u.getWorkType());
        }
        assertSame("he stays selected", u, s.panel.selected());
        assertEquals("the cursor on his tile", 2, ClassicColonyPanel.tileCursor());
        assertFalse(s.panel.dragging());
    }

    /**
     * R3 (levi): from tile to tile he keeps his work, even where it makes
     * nothing (V C14 #50256: the sugar planter on the hills, the red sign;
     * ours on the mountains); the click on an empty tile moves him the same way (target
     * 2, V #48808).
     */
    public void testTileToTileKeepsTheWork() throws Exception {
        final Scene s = scene(2, true);
        final List<Unit> all = s.colony.getUnitList();
        final Unit u = all.get(0);
        u.setLocation(tileOf(s.colony, Direction.W));
        u.changeWorkType(sugar);
        all.get(1).setLocation(building(s.colony, "townHall"));
        s.colony.invalidateCache();
        paint(s.panel);
        final int[] a = at(s.panel, u);
        final int[] ne = tile(2);
        drag(s.panel, a[0], a[1], ne[0], ne[1]);
        assertSame(s.api.log.toString(), tileOf(s.colony, Direction.NE), u.getLocation());
        assertEquals("R3: still the sugar planter " + s.api.log, sugar, u.getWorkType());
        assertEquals("nothing on the mountains", 0,
                     tileOf(s.colony, Direction.NE).getPotentialProduction(sugar, u.getType()));
        // The click on an empty tile: he goes there with his work (no menu).
        final int[] sw = tile(6);
        click(s.panel, sw[0], sw[1]);
        assertSame(tileOf(s.colony, Direction.SW), u.getLocation());
        assertEquals(sugar, u.getWorkType());
        assertTrue("no box", s.boxes.asked.isEmpty());
    }

    /**
     * A drop on the fence → J1 (+5 frames) → Soldat: outside with the
     * warehouse's 50 muskets (V #6315, #6481: 50 → 0); the drag back onto
     * the Rathaus returns them (V C8 #6767: 0 → 50) and he is the
     * Staatsmann again; a drop on a full building asks @MORETHANTHREE and
     * nothing moves.
     */
    public void testTheFenceAndTheJoin() throws Exception {
        final Scene s = scene(4, true);
        final List<Unit> us = s.colony.getUnitList();
        final Unit vet = us.get(0);
        vet.changeType(spec().getUnitType("model.unit.veteranSoldier"));
        final Building hall = building(s.colony, "townHall");
        vet.setLocation(hall);
        us.get(1).setLocation(tileOf(s.colony, Direction.S));
        us.get(2).setLocation(tileOf(s.colony, Direction.SE));
        us.get(3).setLocation(tileOf(s.colony, Direction.SW));
        s.colony.addGoods(muskets, 50);
        s.colony.invalidateCache();
        paint(s.panel);
        final int[] a = at(s.panel, vet);
        s.boxes.answers.add(3);          // J1: Staatsmann, Siedler, Pionier, Soldat
        drag(s.panel, a[0], a[1], FENCE[0], FENCE[1]);
        assertEquals(1, s.boxes.asked.size());
        final ClassicAdvisorBox.Request j1 = s.boxes.asked.get(0);
        assertEquals("[Staatsmann, Siedler, Pionier, Soldat, Späher, Dragoner]",
                     Arrays.toString(j1.plainRows()));
        assertEquals(0, j1.defaultRow);
        assertEquals("+5 frames", ClassicColonyMoves.ms(5), j1.openDelayMs, 0.01);
        assertTrue("Soldat yellow for the veteran", j1.isCurrent(3));
        assertSame(s.api.log.toString(), s.colony.getTile(), vet.getLocation());
        assertEquals("model.role.soldier", vet.getRole().getId());
        assertEquals("muskets 50 → 0", 0, s.colony.getGoodsCount(muskets));
        assertEquals(3, s.colony.getUnitCount());
        // He stands on the fence; the drag back into the Rathaus.
        paint(s.panel);
        final int[] f = at(s.panel, vet);
        assertTrue("on the fence picture", ClassicColonyLayout.slot("X").bounds()
                   .contains(f[0], f[1]));
        final int[] h = slotOf(s.panel, hall);
        drag(s.panel, f[0], f[1], h[0], h[1]);
        assertSame(s.api.log.toString(), hall, vet.getLocation());
        assertEquals("muskets back: 0 → 50", 50, s.colony.getGoodsCount(muskets));
        assertTrue(vet.hasDefaultRole());
        assertEquals(1, s.boxes.asked.size());
        // A full building: three on the tiles into the Rathaus, the fourth refused.
        for (int i = 1; i <= 2; i++) {
            paint(s.panel);
            final int[] w = at(s.panel, us.get(i));
            drag(s.panel, w[0], w[1], h[0], h[1]);
            assertSame(hall, us.get(i).getLocation());
        }
        paint(s.panel);
        final Unit fourth = us.get(3);
        final Object was = fourth.getLocation();
        final int[] w = at(s.panel, fourth);
        drag(s.panel, w[0], w[1], h[0], h[1]);
        assertSame("nothing moves", was, fourth.getLocation());
        assertEquals(2, s.boxes.asked.size());
        final ClassicAdvisorBox.Request full = s.boxes.asked.get(1);
        assertEquals("MORETHANTHREE", full.id);
        assertSame(ClassicAdvisorBox.Portrait.COLONIST, full.portrait);
        assertTrue(full.isNotice());
        assertSame("the refused one selected", fourth, s.panel.selected());
    }

    /**
     * A click on the selected colonist opens J2 (a first click only
     * selects, dago-colony2 #7520/#7607); «(Weitere)» (+3 frames) → Pionier
     * takes 60 of 75 tools (V #121100: 75 → 15) and he leaves the colony;
     * the click on a unit at the fence opens his menu with the bar on row
     * 1 (V #96, #253), and a building's work puts him in.
     */
    public void testTheJobMenusAndTheFenceClick() throws Exception {
        final Scene s = scene(3, true);
        final List<Unit> us = s.colony.getUnitList();
        final Unit u = us.get(0);
        final Building hall = building(s.colony, "townHall");
        u.setLocation(hall);
        us.get(1).setLocation(tileOf(s.colony, Direction.S));
        us.get(2).setLocation(tileOf(s.colony, Direction.SE));
        s.colony.addGoods(tools, 75);
        s.colony.invalidateCache();
        paint(s.panel);
        s.panel.select(us.get(1));
        final int[] a = at(s.panel, u);
        click(s.panel, a[0], a[1]);
        assertSame(u, s.panel.selected());
        assertTrue("a first click only selects", s.boxes.asked.isEmpty());
        s.boxes.answers.add(16);        // page 1: 16 works, then (Weitere) at 16 (no LÖSCHEN)
        s.boxes.answers.add(2);         // page 2: (Weitere), Siedler, Pionier
        click(s.panel, a[0], a[1]);
        assertEquals(2, s.boxes.asked.size());
        final ClassicAdvisorBox.Request j2 = s.boxes.asked.get(0);
        assertEquals("(Weitere)", j2.plainRows()[16]);
        assertEquals("the bar on Staatsmann", "Staatsmann", j2.plainRows()[j2.defaultRow]);
        assertEquals("+5 frames after the release", ClassicColonyMoves.ms(5), j2.openDelayMs,
                     0.01);
        final ClassicAdvisorBox.Request p2 = s.boxes.asked.get(1);
        assertEquals("(Weitere)", p2.plainRows()[0]);
        assertEquals("(60 Werkzeuge)", ClassicAdvisorBox.plain(p2.rightOf(2)));
        assertEquals("+3 frames", ClassicColonyMoves.ms(3), p2.chainMs, 0.01);
        assertSame(s.api.log.toString(), s.colony.getTile(), u.getLocation());
        assertEquals("model.role.pioneer", u.getRole().getId());
        assertEquals(3, u.getRoleCount());
        assertEquals("75 → 15", 15, s.colony.getGoodsCount(tools));
        // The fence click: his menu, the bar on row 1; Schnapsbrenner puts him in.
        paint(s.panel);
        final int[] f = at(s.panel, u);
        s.boxes.answers.add(9);         // Farmer 0 ... Schnapsbrenner 9
        click(s.panel, f[0], f[1]);
        assertEquals(3, s.boxes.asked.size());
        final ClassicAdvisorBox.Request fm = s.boxes.asked.get(2);
        assertEquals(0, fm.defaultRow);
        assertEquals("Farmer", fm.plainRows()[0]);
        assertTrue("44 frames after the press", fm.openDelayMs > ClassicColonyMoves.ms(30));
        assertEquals("Schnapsbrenner", fm.plainRows()[9]);
        assertSame(s.api.log.toString(), building(s.colony, "distillerHouse"), u.getLocation());
        assertEquals("the tools back", 75, s.colony.getGoodsCount(tools));
        // J2 for a tile worker: a tile's work on his own tile (V #48712, no move).
        paint(s.panel);
        final Unit t = us.get(1);
        final Object place = t.getLocation();
        final int[] tp = at(s.panel, t);
        click(s.panel, tp[0], tp[1]);   // select
        s.boxes.answers.add(6);         // Erz-Bergarbeiter
        click(s.panel, tp[0], tp[1]);
        assertSame("no move", place, t.getLocation());
        assertEquals(ore, t.getWorkType());
    }

    /**
     * Abandoning (target 8; V #35379 → #35606): the last colonist dropped on
     * the fence, J1 → Späher → @ABANDON2 (the only colony) with MSS5, the
     * bar and Escape on «Niemals!»; «Niemals!» changes nothing; «Ja» puts
     * him on the fence with the horses and gives the colony up 31 frames
     * later, the screen goes.
     */
    public void testAbandon() throws Exception {
        final Scene s = scene(1, true);
        final Unit u = s.colony.getUnitList().get(0);
        u.changeType(spec().getUnitType("model.unit.seasonedScout"));
        u.setLocation(tileOf(s.colony, Direction.N));
        u.changeWorkType(sugar);
        s.colony.addGoods(horses, 50);
        s.colony.invalidateCache();
        paint(s.panel);
        final int[] a = at(s.panel, u);
        s.boxes.answers.add(4);         // J1: Zuckerpflanzer, Siedler, Pionier, Soldat, Späher
        s.boxes.answers.add(1);         // «Niemals!»
        drag(s.panel, a[0], a[1], FENCE[0], FENCE[1]);
        assertEquals(2, s.boxes.asked.size());
        final ClassicAdvisorBox.Request ab = s.boxes.asked.get(1);
        assertEquals("ABANDON2", ab.id);
        assertEquals(1, ab.defaultRow);
        assertEquals(1, ab.cancelRow);
        assertSame(ClassicAdvisorBox.Portrait.COLONIST, ab.portrait);
        assertEquals("8 frames with the palette lead", ClassicColonyMoves.ms(5), ab.chainMs, 0.01);
        assertTrue(ab.plainText().contains(s.colony.getName()));
        assertSame("«Niemals!»: nothing", tileOf(s.colony, Direction.N), u.getLocation());
        assertEquals(50, s.colony.getGoodsCount(horses));
        assertTrue(s.later.isEmpty());
        // «Ja».
        paint(s.panel);
        final int[] b = at(s.panel, u);
        s.boxes.answers.add(4);
        s.boxes.answers.add(0);
        drag(s.panel, b[0], b[1], FENCE[0], FENCE[1]);
        assertSame(s.api.log.toString(), s.colony.getTile(), u.getLocation());
        assertEquals("model.role.scout", u.getRole().getId());
        assertTrue(s.panel.leaving());
        assertEquals("[later 442]", s.events.toString());
        // Nothing reacts meanwhile.
        click(s.panel, FENCE[0], FENCE[1]);
        assertEquals(4, s.boxes.asked.size());
        final Tile tile = s.colony.getTile();
        s.later.get(0).run();
        assertFalse(s.api.log.toString(), tile.hasSettlement());
        assertTrue(s.colony.isDisposed());
        assertEquals("[later 442, closed]", s.events.toString());
        assertSame(tile, u.getLocation());
        assertFalse(s.panel.leaving());
    }

    /**
     * The selection at the opening (target 9): the founder at a founding
     * (V #14817), else the one selected last here (V #19585 → #19980),
     * else the first figure (I).
     */
    public void testTheOpeningSelection() throws Exception {
        final Scene s = scene(2, true);
        final List<Unit> us = s.colony.getUnitList();
        us.get(0).setLocation(building(s.colony, "townHall"));
        us.get(1).setLocation(tileOf(s.colony, Direction.S));
        s.colony.invalidateCache();
        s.panel.opened(ClassicColonyRight.Opening.OTHER);
        paint(s.panel);
        final Unit first = s.panel.rowFigures().get(0).unit;
        assertSame("the first figure", first, s.panel.selected());
        final Unit other = (first == us.get(0)) ? us.get(1) : us.get(0);
        final int[] o = at(s.panel, other);
        click(s.panel, o[0], o[1]);
        assertSame(other, s.panel.selected());
        // A new visit: the one selected last.
        final ClassicColonyPanel again = new ClassicColonyPanel(s.fcc,
            ClassicColonyBuildingsTest.library(), s.colony, null, s.boxes, ClassicSlide.SYSTEM,
            false);
        again.opened(ClassicColonyRight.Opening.OTHER);
        assertSame("the last one", other, again.selected());
    }

    /**
     * The golden opening of #14817 without a test hook: Provisorische
     * Kolonie just founded, opened as a founding, draws exactly what
     * {@code ClassicColonyBuildingsTest}'s golden #14817 draws with its
     * hand-set selection (the scout's green frames on his figure and tile
     * N; that golden is 0 px against the clip but for the terrain).
     */
    public void testTheFoundingOpensAsClip14817() throws Exception {
        final BufferedImage[] shots = new BufferedImage[2];
        for (int k = 0; k < 2; k++) {
            ClassicColonyPanel.setLastMode(ClassicColonyRight.Mode.HOUSE);
            ClassicColonyPanel.setTileCursor(ClassicColonyLayout.CENTRE_TILE);
            ClassicColonyPanel.forgetSelections();
            final ClassicColonyBuildingsTest.State s
                = new ClassicColonyBuildingsTest.State("Provisorische Kolonie", 1541);
            final Unit scout = s.on("seasonedScout", Direction.N, "sugar");
            s.finish();
            final ClassicColonyPanel p = ClassicColonyBuildingsTest.panel(s,
                ClassicColonyBuildingsTest.PROV);
            if (k == 0) {
                p.select(scout);
            } else {
                p.opened(ClassicColonyRight.Opening.FOUNDING);
                assertSame(scout, p.selected());
            }
            p.blinkPhase(true);
            shots[k] = p.canvas();
        }
        int differ = 0;
        for (int y = 0; y < 200; y++) {
            for (int x = 0; x < 320; x++) {
                if (shots[0].getRGB(x, y) != shots[1].getRGB(x, y)) differ++;
            }
        }
        assertEquals("the opening's selection as the golden's", 0, differ);
    }

    /** At a founding the founder is selected (target 9, V #14817, #123003, clip008 #4032). */
    public void testTheFounderIsSelected() throws Exception {
        final Scene f = scene(1, true);
        final Unit founder = f.colony.getUnitList().get(0);
        f.panel.opened(ClassicColonyRight.Opening.FOUNDING);
        assertSame(founder, f.panel.selected());
    }

    /**
     * The drag's look (M4.0/M4.8): the arrow's flag during the drag, the
     * ghost (his figure at the hotspot − (1,8) with a black silhouette 2 px
     * left), no hover label, the source keeps its figure.
     */
    public void testTheGhost() throws Exception {
        final Scene s = scene(1, true);
        final Unit u = s.colony.getUnitList().get(0);
        u.setLocation(building(s.colony, "townHall"));
        s.colony.invalidateCache();
        paint(s.panel);
        final int[] a = at(s.panel, u);
        final BufferedImage before = s.panel.canvas();
        mouse(s.panel, MouseEvent.MOUSE_PRESSED, a[0], a[1]);
        mouse(s.panel, MouseEvent.MOUSE_DRAGGED, 280, 44);
        assertTrue(s.panel.dragging());
        final BufferedImage during = s.panel.canvas();
        final ClassicColonyBuildings.Assets art = ClassicColonyBuildings.Assets.load(
            ClassicPackFiles.runtime());
        final String key = s.panel.hitAt(a[0], a[1]).key;
        final BufferedImage sprite = (art == null) ? null : art.image(key);
        if (sprite != null) {
            int ghost = 0;
            for (int y = 0; y < sprite.getHeight(); y++) {
                for (int x = 0; x < sprite.getWidth(); x++) {
                    if ((sprite.getRGB(x, y) >>> 24) < 128) continue;
                    if ((sprite.getRGB(x, y) & 0xFFFFFF)
                        == (during.getRGB(279 + x, 36 + y) & 0xFFFFFF)) ghost++;
                }
            }
            assertTrue("the ghost at (279,36): " + ghost, ghost > 20);
            // The source keeps its figure.
            assertEquals(before.getRGB(a[0], a[1]), during.getRGB(a[0], a[1]));
        }
        mouse(s.panel, MouseEvent.MOUSE_RELEASED, 280, 44);
        assertFalse(s.panel.dragging());
        assertSame("dropped on a tile of nobody: on it", tileOf(s.colony, Direction.NE),
                   u.getLocation());
    }
}
