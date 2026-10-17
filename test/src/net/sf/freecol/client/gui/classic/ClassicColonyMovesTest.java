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
import net.sf.freecol.server.model.ServerBuilding;
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
        // Tile to tile: the work kept, the food work following the square
        // (the review of part Y: a Fischer onto land a Farmer, V clip 053;
        // a Farmer onto water a Fischer, I).
        final int fisher = ClassicColonyLayout.JOB_FISHERMAN;
        assertEquals(0, ClassicColonyMoves.keptJob(fisher, false));
        assertEquals(fisher, ClassicColonyMoves.keptJob(0, true));
        assertEquals(fisher, ClassicColonyMoves.keptJob(fisher, true));
        assertEquals(0, ClassicColonyMoves.keptJob(0, false));
        for (int job = 1; job < fisher; job++) {
            assertEquals(job + " on land", job, ClassicColonyMoves.keptJob(job, false));
            assertEquals(job + " on water", job, ClassicColonyMoves.keptJob(job, true));
        }
        assertEquals(-1, ClassicColonyMoves.keptJob(-1, true));
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
        // Clip 042/043 (the part U fixer): the fence unit's menu from the
        // release as J2, the fence click's menu, the labels after a move,
        // the unit on the fence after «Ja».
        assertEquals(71.3, ClassicColonyMoves.ms(ClassicColonyMoves.J2_FRAMES), 0.1);
        assertEquals(42.8, ClassicColonyMoves.ms(ClassicColonyMoves.FENCE_CLICK_FRAMES), 0.1);
        assertEquals(185.5, ClassicColonyMoves.ms(ClassicColonyMoves.HOVER_TILE_FRAMES), 0.1);
        assertEquals(228.3, ClassicColonyMoves.ms(ClassicColonyMoves.HOVER_BUILDING_FRAMES), 0.1);
        assertEquals(42.8, ClassicColonyMoves.ms(ClassicColonyMoves.HOVER_REFUSED_FRAMES), 0.1);
        assertEquals(42.8, ClassicColonyMoves.ms(ClassicColonyMoves.ABANDON_FENCE_FRAMES), 0.1);
        // @NODOCKS on screen 7 frames after the drop (Z-ITEMS Z1.3, V clip
        // 053 #50460 -> #50467).
        assertEquals(99.9, ClassicColonyMoves.ms(ClassicColonyMoves.NODOCKS_FRAMES), 0.1);
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
            // «Dragoner» yellow for an Erfahrene Soldaten (playthrough 4,
            // ClassicColonyJobs.yellowFor), drawn grey here: no horses.
            final String roles = " | Siedler | Pionier~( | Soldat*( | Späher~( | Dragoner*~(";
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
        return over(gold, gold.frame(frame), l, bar, wood, tiny);
    }

    /**
     * {@link #over} on a frame of any clip, for the other colony tests
     * (part V: the build list, the carriers' and the amount boxes).
     *
     * @return {compared, off, off on the arrow, off on the first row's rectangle}.
     */
    static int[] overFrame(ClassicColonyGolden gold, BufferedImage f, ClassicAdvisorBox.Layout l,
                           int bar, BufferedImage wood, ClassicFont tiny) throws Exception {
        return over(gold, f, l, bar, wood, tiny);
    }

    /** {@link #over} on a frame of any clip. */
    private static int[] over(ClassicColonyGolden gold, BufferedImage f, ClassicAdvisorBox.Layout l,
                              int bar, BufferedImage wood, ClassicFont tiny) throws Exception {
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

        /** Whether the server refuses the abandon (a turn gone meanwhile). */
        boolean refuseAbandon = false;

        @Override
        public boolean abandonColony(Colony colony) {
            if (this.refuseAbandon) {
                this.log.add("abandon " + colony.getName() + " refused");
                return false;
            }
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

        /**
         * The row F1 (a {@link ClassicAdvisorBox.Bar#HELP} answer) is
         * pressed on: the bar's default row, or this one when set (the bar
         * moved there first, fixer A after part X).
         */
        int helpOn = -1;

        @Override
        public int ask(ClassicAdvisorBox.Request r) {
            this.asked.add(r);
            final int a = (this.answers.isEmpty()) ? ClassicAdvisorBox.Bar.DISMISSED
                : this.answers.poll();
            if (a == ClassicAdvisorBox.Bar.HELP && r.help != null) {
                // As the real bar: the hook is told the barred row.
                r.help.help((this.helpOn >= 0) ? this.helpOn : r.defaultRow);
                this.helpOn = -1;
            }
            return a;
        }

        /** The skill pages asked (their NAMES @JOB rows) and their delays (ms). */
        final List<Integer> skills = new ArrayList<>();
        final List<Double> delays = new ArrayList<>();

        /** The building pages asked. */
        final List<net.sf.freecol.common.model.BuildingType> buildings = new ArrayList<>();

        @Override
        public void skillPage(int job, double delayMs) {
            this.skills.add(job);
            this.delays.add(delayMs);
        }

        @Override
        public void buildingPage(net.sf.freecol.common.model.BuildingType type,
                                 double delayMs) {
            this.buildings.add(type);
            this.delays.add(delayMs);
        }

        @Override
        public void terrainPage(Tile tile, double delayMs) {
            this.pages.add(tile);
            this.delays.add(delayMs);
        }

        /** @return The last box's rows, plain. */
        String last() {
            final ClassicAdvisorBox.Request r = this.asked.get(this.asked.size() - 1);
            return r.id + " " + Arrays.toString(r.plainRows()) + " bar=" + r.defaultRow;
        }

        /** The colonies whose food a change made short (part W, @TUTORIAL16). */
        final List<Colony> shortOf = new ArrayList<>();

        /** The tiles whose Colonopedia page F1 asked (part W). */
        final List<Tile> pages = new ArrayList<>();

        @Override
        public void foodRanShort(Colony colony) {
            this.shortOf.add(colony);
        }

        @Override
        public void terrainPage(Tile tile) {
            this.pages.add(tile);
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
        // The fence click on another unit only selects (V clip 042 #11157);
        // a click on the selected one: his menu, «(Pionier)» in the title,
        // the bar on row 1, 5 frames after the release (clip 042 #15426,
        // #15901); Schnapsbrenner puts him in.
        paint(s.panel);
        final int[] f = at(s.panel, u);
        s.panel.select(us.get(1));
        click(s.panel, f[0], f[1]);
        assertSame("the first click selects", u, s.panel.selected());
        assertEquals("no menu", 2, s.boxes.asked.size());
        s.boxes.answers.add(9);         // Farmer 0 ... Schnapsbrenner 9
        click(s.panel, f[0], f[1]);
        assertEquals(3, s.boxes.asked.size());
        final ClassicAdvisorBox.Request fm = s.boxes.asked.get(2);
        assertEquals(0, fm.defaultRow);
        assertEquals("Farmer", fm.plainRows()[0]);
        assertTrue(fm.plainText(), fm.plainText().contains("(Pionier):"));
        assertEquals("5 frames after the release", ClassicColonyMoves.ms(5), fm.openDelayMs,
                     0.01);
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
     * Part Y4 (Roger 2026-10-16: "ein Klick auf das Feld-Quadrat muss
     * reichen, um das Menü des Kolonisten zu öffnen"): a tile's worker
     * answers a press anywhere in his tile's 24x24 square, not only on his
     * figure at tile + (14,6): the first click (here in the square's
     * top-left corner, I: never seen) selects him with the white cursor on
     * his tile, the next (left of his shadow, tile + (11,16) as clip 020
     * #79104 -&gt; J2 #79143) opens J2 on his own tile; a drag from the
     * square's corner moves him into a building; the right button there
     * still asks the tile's terrain page.
     */
    public void testATileWorkerAnswersHisWholeSquare() throws Exception {
        final Scene s = scene(3, true);
        final List<Unit> us = s.colony.getUnitList();
        final Unit worker = us.get(1);
        us.get(0).setLocation(building(s.colony, "townHall"));
        worker.setLocation(tileOf(s.colony, Direction.S));
        worker.changeWorkType(ClassicColonyJobs.goodsOf(spec(), 0));
        us.get(2).setLocation(building(s.colony, "carpenterHouse"));
        s.colony.invalidateCache();
        paint(s.panel);
        final Rectangle sq = ClassicColonyLayout.tileCell(7);   // S
        assertSame(worker, s.panel.hitAt(sq.x, sq.y).unit);
        assertSame(worker, s.panel.hitAt(sq.x + 23, sq.y + 23).unit);
        assertSame(worker, s.panel.hitAt(sq.x + 11, sq.y + 16).unit);
        assertNull("not the next square", s.panel.hitAt(sq.x + 24, sq.y + 12));
        assertNull("an empty square", s.panel.hitAt(ClassicColonyLayout.tileCell(3).x + 5,
                                                    ClassicColonyLayout.tileCell(3).y + 5));
        s.panel.select(us.get(0));
        click(s.panel, sq.x + 1, sq.y + 1);
        assertSame("the first click selects him", worker, s.panel.selected());
        assertEquals(7, ClassicColonyPanel.tileCursor());
        assertTrue("no menu yet", s.boxes.asked.isEmpty());
        assertSame("no move", tileOf(s.colony, Direction.S), worker.getLocation());
        click(s.panel, sq.x + 11, sq.y + 16);   // Escape
        assertEquals("J2", 1, s.boxes.asked.size());
        assertTrue(s.boxes.asked.get(0).plainText(),
                   s.boxes.asked.get(0).plainText().contains(":"));
        assertSame("still on his tile", tileOf(s.colony, Direction.S), worker.getLocation());
        // The right button on his square: the tile's terrain page, as before.
        s.panel.rightPressAt(sq.x + 1, sq.y + 1);
        s.panel.rightReleaseAt(sq.x + 1, sq.y + 1);
        assertEquals(List.of(tileOf(s.colony, Direction.S).getWorkTile()), s.boxes.pages);
        assertTrue(s.boxes.skills.isEmpty());
        // A drag from the square's corner: into the Rathaus.
        final int[] hall = slotOf(s.panel, building(s.colony, "townHall"));
        drag(s.panel, sq.x + 2, sq.y + 21, hall[0], hall[1]);
        assertSame(s.api.log.toString(), building(s.colony, "townHall"), worker.getLocation());
        ClassicColonyPanel.setTileCursor(ClassicColonyLayout.CENTRE_TILE);
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
        // Escape on @ABANDON (DISMISSED): nothing either (the review of
        // part U, regression L1, mutant M12).
        paint(s.panel);
        final int[] e = at(s.panel, u);
        s.boxes.answers.add(4);         // J1 Späher, then no answer: Escape
        drag(s.panel, e[0], e[1], FENCE[0], FENCE[1]);
        assertEquals(4, s.boxes.asked.size());
        assertEquals("ABANDON2", s.boxes.asked.get(3).id);
        assertSame("Escape: nothing", tileOf(s.colony, Direction.N), u.getLocation());
        assertTrue(s.later.isEmpty());
        // «Ja»: outside and the colony given up at once; only the close
        // waits (the unit on the fence +3 frames, the map +31; the review
        // of part U, regression F1).
        paint(s.panel);
        final int[] b = at(s.panel, u);
        s.boxes.answers.add(4);
        s.boxes.answers.add(0);
        final Tile tile = s.colony.getTile();
        drag(s.panel, b[0], b[1], FENCE[0], FENCE[1]);
        assertSame(s.api.log.toString(), tile, u.getLocation());
        assertEquals("model.role.scout", u.getRole().getId());
        assertFalse("given up at once " + s.api.log, tile.hasSettlement());
        assertTrue(s.colony.isDisposed());
        assertTrue(s.panel.leaving());
        assertEquals("[later 43, later 442]", s.events.toString());
        // Nothing reacts meanwhile: Escape, the exit, a mode button, the
        // fence, and the screen paints the colony as it was.
        s.panel.getActionMap().get("classic_closeColony").actionPerformed(null);
        click(s.panel, 312, 190);
        click(s.panel, ClassicColonyLayout.RIGHT_PANEL.x + 2, ClassicColonyLayout.RIGHT_PANEL.y + 2);
        click(s.panel, FENCE[0], FENCE[1]);
        assertEquals(6, s.boxes.asked.size());
        assertEquals("[later 43, later 442]", s.events.toString());
        // The colony as it was, the unit on the fence 3 frames later.
        final BufferedImage was = s.panel.canvas();
        s.later.get(0).run();
        final BufferedImage fence = s.panel.canvas();
        int changed = 0;
        for (int y = 0; y < 200; y++) {
            for (int x = 0; x < 320; x++) {
                if (was.getRGB(x, y) != fence.getRGB(x, y)) changed++;
            }
        }
        assertTrue("the unit comes onto the fence: " + changed, changed > 20);
        paint(s.panel);
        s.later.get(1).run();
        assertEquals("[later 43, later 442, closed]", s.events.toString());
        assertSame(tile, u.getLocation());
        assertFalse(s.panel.leaving());
    }

    /**
     * A refused abandon (the turn gone meanwhile): he goes back where he
     * worked, so no colony stays with nobody in it, and the screen stays
     * (the review of part U, regression F1, play L3).
     */
    public void testARefusedAbandonPutsHimBack() throws Exception {
        final Scene s = scene(1, true);
        final Unit u = s.colony.getUnitList().get(0);
        u.setLocation(tileOf(s.colony, Direction.N));
        u.changeWorkType(sugar);
        s.colony.addGoods(muskets, 50);
        s.colony.invalidateCache();
        s.api.refuseAbandon = true;
        paint(s.panel);
        final int[] a = at(s.panel, u);
        s.boxes.answers.add(3);         // J1: Zuckerpflanzer, Siedler, Pionier, Soldat
        s.boxes.answers.add(0);         // «Ja»
        drag(s.panel, a[0], a[1], FENCE[0], FENCE[1]);
        assertTrue(s.api.log.toString(), s.api.log.contains("abandon " + s.colony.getName()
                                                            + " refused"));
        assertSame("back on his tile " + s.api.log, tileOf(s.colony, Direction.N),
                   u.getLocation());
        assertEquals(sugar, u.getWorkType());
        assertEquals("the muskets back", 50, s.colony.getGoodsCount(muskets));
        assertEquals(1, s.colony.getUnitCount());
        assertFalse(s.panel.leaving());
        assertTrue(s.later.isEmpty());
        assertFalse(s.events.contains("closed"));
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


    // The part U fixer: the reviews' findings and clips 042 / 043

    /** A building added to a scene's colony. */
    private static Building addBuilding(Scene s, String id) {
        final Building b = new ServerBuilding(s.game, s.colony,
            spec().getBuildingType("model.building." + id));
        s.colony.addBuilding(b);
        s.colony.invalidateCache();
        return b;
    }

    /** A point of the fence picture (slot X) with no unit on it. */
    private static int[] fenceFree(ClassicColonyPanel p) {
        final Rectangle r = ClassicColonyLayout.slot("X").bounds();
        for (int x = r.x + 2; x < r.x + r.width - 2; x++) {
            for (int y = r.y + r.height / 2; y < r.y + r.height - 2; y++) {
                if (p.hitAt(x, y) == null && ClassicColonyPanel.slotAt(x, y) >= 0
                    && ClassicColonyLayout.SLOTS[ClassicColonyPanel.slotAt(x, y)].cls
                    == ClassicColonyLayout.SlotClass.FENCE) return new int[] { x, y };
            }
        }
        fail("no free point on the fence");
        return null;
    }

    /** The fence unit's menu → «(Weitere)» → Pionier, its cell as given. */
    private void pickPioneer(Scene s, ClassicText t, Unit p, String cell) {
        final ClassicColonyJobs.Menu m1 = ClassicColonyJobs.menu(t, s.colony, p,
            ClassicColonyJobs.Kind.OUTSIDE, 1);
        final ClassicColonyJobs.Menu m2 = ClassicColonyJobs.menu(t, s.colony, p,
            ClassicColonyJobs.Kind.OUTSIDE, 2);
        final int more = m1.rowOf(ClassicColonyJobs.Pick.MORE, -1);
        final int pio = m2.rowOf(ClassicColonyJobs.Pick.ROLE, ClassicColonyLayout.JOB_PIONEER);
        assertEquals(cell, m2.rows.get(pio).cell);
        assertFalse("not grey", m2.rows.get(pio).grey);
        s.boxes.answers.add(more);
        s.boxes.answers.add(pio);
        paint(s.panel);
        s.panel.select(p);
        final int[] f = at(s.panel, p);
        click(s.panel, f[0], f[1]);
    }

    /**
     * A pioneer at the fence picks «Pionier» again (the review of part U,
     * play M1): the count from the warehouse and what he carries; his own
     * count is his own row, the word alone (fixer A after part X: the
     * original's own role row, clip 048 #80466; I for the pioneer) and
     * changes nothing; another count gives his tools back first, so no
     * tool is lost or made.
     */
    public void testAPioneerAtTheFenceKeepsHisTools() throws Exception {
        final ClassicText t = texts();
        if (t == null) return;
        final Scene s = scene(2, true);
        final List<Unit> us = s.colony.getUnitList();
        us.get(0).setLocation(building(s.colony, "townHall"));
        final Unit p = us.get(1);
        p.setLocation(s.colony.getTile());
        final Role pioneer = spec().getRole("model.role.pioneer");
        p.changeRole(pioneer, 5);                       // 100 tools carried
        s.colony.invalidateCache();
        assertEquals(0, s.colony.getGoodsCount(tools));
        pickPioneer(s, t, p, null);                 // his own row, the word alone
        assertEquals(s.api.log.toString(), 5, p.getRoleCount());
        assertEquals("nothing lost", 0, s.colony.getGoodsCount(tools));
        p.changeRole(pioneer, 1);                       // 20 carried, 100 stored
        s.colony.addGoods(tools, 100);
        pickPioneer(s, t, p, "(100 Werkzeuge)");
        assertEquals(s.api.log.toString(), pioneer, p.getRole());
        assertEquals(5, p.getRoleCount());
        assertEquals("nothing made: 120 in all", 20, s.colony.getGoodsCount(tools));
        assertSame(s.colony.getTile(), p.getLocation());
    }

    /**
     * The school and the docks say why not, with the original's boxes,
     * before the server is asked (the review of part U, play M2, M3, L1,
     * fidelity M3): @NOTEACHER, @TEACHCONVERT, @NEEDCOLLEGE with the
     * work, a teacher in, then @SCHOOL1; «Lehrer» offered only to whom the
     * school takes, and its pick on a full school @SCHOOL1 too.
     */
    public void testTheSchoolSaysWhyNot() throws Exception {
        final ClassicText t = texts();
        if (t == null) return;
        final Scene s = scene(5, true);
        final List<Unit> us = s.colony.getUnitList();
        final Building school = addBuilding(s, "schoolhouse");
        final String[] types = { "freeColonist", "indianConvert", "masterBlacksmith",
                                 "expertFarmer", "expertFisherman" };
        final Direction[] at = { Direction.S, Direction.SE, Direction.SW, Direction.W,
                                 Direction.NW };
        for (int i = 0; i < 5; i++) {
            us.get(i).setLocation(building(s.colony, (i < 3) ? "townHall" : "carpenterHouse"));
        }
        for (int i = 0; i < 5; i++) {
            us.get(i).changeType(spec().getUnitType("model.unit." + types[i]));
            us.get(i).setLocation(tileOf(s.colony, at[i]));
            if (i != 1) assertSame(types[i], tileOf(s.colony, at[i]), us.get(i).getLocation());
            us.get(i).changeWorkType(ClassicColonyJobs.goodsOf(spec(), 0));
        }
        s.colony.invalidateCache();
        assertFalse("no Lehrer for a free colonist", ClassicColonyJobs.works(s.colony,
            us.get(0).getType()).contains(ClassicColonyLayout.JOB_TEACHER));
        assertTrue("Lehrer for the expert farmer", ClassicColonyJobs.works(s.colony,
            us.get(3).getType()).contains(ClassicColonyLayout.JOB_TEACHER));
        final String[] boxes = { "NOTEACHER", "TEACHCONVERT", "NEEDCOLLEGE", null, "SCHOOL1" };
        for (int i = 0; i < 5; i++) {
            paint(s.panel);
            final Unit u = us.get(i);
            final Object was = u.getLocation();
            final int calls = s.api.log.size(), asked = s.boxes.asked.size();
            final int[] a = at(s.panel, u);
            final int[] b = slotOf(s.panel, school);
            drag(s.panel, a[0], a[1], b[0], b[1]);
            if (boxes[i] == null) {
                assertSame(types[i] + " teaches " + s.api.log, school, u.getLocation());
                assertEquals(asked, s.boxes.asked.size());
                continue;
            }
            assertSame(types[i] + " stays", was, u.getLocation());
            assertEquals(types[i] + ": nothing asked of the server " + s.api.log, calls,
                         s.api.log.size());
            assertEquals(types[i], asked + 1, s.boxes.asked.size());
            final ClassicAdvisorBox.Request r = s.boxes.asked.get(asked);
            assertEquals(types[i], boxes[i], r.id);
            assertSame(ClassicAdvisorBox.Portrait.COLONIST, r.portrait);
            if ("NEEDCOLLEGE".equals(boxes[i])) {
                assertTrue(r.plainText(), r.plainText().contains("(Schmied)"));
            }
        }
        // The fisherman picks «Lehrer» in J2 (page 2): @SCHOOL1, he stays.
        final Unit fisher = us.get(4);
        final ClassicColonyJobs.Menu m1 = ClassicColonyJobs.menu(t, s.colony, fisher,
            ClassicColonyJobs.Kind.WORKER, 1);
        final ClassicColonyJobs.Menu m2 = ClassicColonyJobs.menu(t, s.colony, fisher,
            ClassicColonyJobs.Kind.WORKER, 2);
        final int teacher = m2.rowOf(ClassicColonyJobs.Pick.JOB, ClassicColonyLayout.JOB_TEACHER);
        assertTrue(m2.toString(), teacher > 0);
        paint(s.panel);
        s.panel.select(fisher);
        final int asked0 = s.boxes.asked.size();
        final int calls = s.api.log.size();
        s.boxes.answers.add(m1.rowOf(ClassicColonyJobs.Pick.MORE, -1));
        s.boxes.answers.add(teacher);
        final int[] f = at(s.panel, fisher);
        click(s.panel, f[0], f[1]);
        assertEquals(asked0 + 3, s.boxes.asked.size());
        assertEquals("SCHOOL1", s.boxes.asked.get(s.boxes.asked.size() - 1).id);
        assertSame(s.boxes.last() + " / " + s.api.log, tileOf(s.colony, Direction.NW), fisher.getLocation());
        assertEquals(calls, s.api.log.size());
        assertFalse(s.api.log.toString(), s.api.log.toString().contains("refused"));
    }

    /**
     * The ocean (the review of part U, play M4, L1, fidelity M3): without
     * docks a drop on the water says @NODOCKS and nothing moves, and so
     * does a click on the water with a colonist selected (V clip 053
     * #50365 - #50461, #31382); the box has the admiral and is on screen 7
     * frames after the drop or the release (Z-ITEMS Z1.3; V #50460 -&gt;
     * #50467), @MORETHANTHREE keeps MSS5 and its 32 frames; with the docks
     * a colonist from a building becomes a Fischer there, not a Farmer (I).
     */
    public void testTheOceanAndTheDocks() throws Exception {
        final Scene s = scene(2, true);
        final Tile e = s.colony.getTile().getNeighbourOrNull(Direction.E);
        e.setType(spec().getTileType("model.tile.ocean"));
        final List<Unit> us = s.colony.getUnitList();
        final Unit u = us.get(0);
        final Building hall = building(s.colony, "townHall");
        u.setLocation(hall);
        us.get(1).setLocation(tileOf(s.colony, Direction.S));
        s.colony.invalidateCache();
        paint(s.panel);
        final int calls = s.api.log.size();
        int[] a = at(s.panel, u);
        final int[] ocean = tile(5);
        final long seven = Math.round(ClassicColonyMoves.ms(ClassicColonyMoves.NODOCKS_FRAMES)
                                      * 1e6);
        long before = ClassicSlide.SYSTEM.now();
        drag(s.panel, a[0], a[1], ocean[0], ocean[1]);
        long after = ClassicSlide.SYSTEM.now();
        assertSame(hall, u.getLocation());
        assertEquals(calls, s.api.log.size());
        final ClassicAdvisorBox.Request nd = s.boxes.asked.get(0);
        assertEquals("NODOCKS", nd.id);
        assertSame("the admiral", ClassicAdvisorBox.Portrait.ADMIRAL, nd.portrait);
        assertTrue(nd.isNotice());
        assertEquals("at its time, not after a delay", 0.0, nd.openDelayMs, 0.0);
        assertTrue("on screen 7 frames after the drop: " + (nd.showAtNanos - before),
                   nd.showAtNanos >= before + seven && nd.showAtNanos <= after + seven);
        // The click on the water with him selected: the same box.
        assertSame(u, s.panel.selected());
        before = ClassicSlide.SYSTEM.now();
        click(s.panel, ocean[0], ocean[1]);
        after = ClassicSlide.SYSTEM.now();
        assertSame(hall, u.getLocation());
        assertEquals(calls, s.api.log.size());
        assertEquals(2, s.boxes.asked.size());
        final ClassicAdvisorBox.Request nc = s.boxes.asked.get(1);
        assertEquals("NODOCKS", nc.id);
        assertSame(ClassicAdvisorBox.Portrait.ADMIRAL, nc.portrait);
        assertTrue("7 frames after the release",
                   nc.showAtNanos >= before + seven && nc.showAtNanos <= after + seven);
        // @MORETHANTHREE as before: MSS5, 32 frames (with the palette lead).
        final ClassicAdvisorBox.Request full = ClassicColonyPanel.refusalRequest(
            ClassicText.load(ClassicPackFiles.runtime()),
            new ClassicColonyJobs.Refusal("MORETHANTHREE", null), "t", 0L);
        if (full != null) {
            assertSame(ClassicAdvisorBox.Portrait.COLONIST, full.portrait);
            assertEquals(0L, full.showAtNanos);
            assertEquals(ClassicColonyMoves.ms(32) - ClassicAdvisorLayer.PALETTE_LEAD_MS,
                         full.openDelayMs, 0.01);
        }
        addBuilding(s, "docks");
        paint(s.panel);
        a = at(s.panel, u);
        drag(s.panel, a[0], a[1], ocean[0], ocean[1]);
        assertSame(s.api.log.toString(), tileOf(s.colony, Direction.E), u.getLocation());
        assertEquals("a Fischer on the water", ClassicColonyJobs.goodsOf(spec(),
            ClassicColonyLayout.JOB_FISHERMAN), u.getWorkType());
        assertEquals(2, s.boxes.asked.size());
    }

    /**
     * @NODOCKS over clip 053's frames (Z-ITEMS Z1.3, the golden #31394;
     * the pack and {@code -Dclassic.clips}): the admiral at box + (-4,
     * -71), the box (44, 121, 236, 30), the words with «Hafenanlagen» in
     * gold, 0 px off box and portrait, after the click on the empty water
     * (#31394) and after the drag onto it (#50468).
     */
    public void testTheNoDocksBoxAsClip053() throws Exception {
        final ClassicText t = texts();
        if (t == null) return;
        final ClassicColonyGolden gold = ClassicColonyGolden.open(getName());
        if (gold == null) return;
        final ClassicPackFiles pack = gold.pack;
        final ClassicFont tiny = pack.font(ClassicFont.TINY);
        final BufferedImage wood = pack.image(ClassicMenuBar.WOOD_KEY);
        final ClassicAdvisorBox.Request r = ClassicColonyPanel.refusalRequest(t,
            new ClassicColonyJobs.Refusal(ClassicColonyJobs.NODOCKS, null), "Test", 0L);
        assertNotNull(r);
        final ClassicAdvisorBox.Layout l = ClassicAdvisorBox.layout(r, tiny,
            pack.image(ClassicPackFiles.ssKey(r.portrait.sprite)));
        assertEquals(new Rectangle(44, 121, 236, 30), l.box);
        assertEquals(40, l.portraitAt.x);
        assertEquals(50, l.portraitAt.y);
        final java.io.File key = new java.io.File(gold.recordings, "colony-flow-analysis/key");
        final StringBuilder out = new StringBuilder();
        int bad = 0, n = 0;
        for (String f : new String[] { "c053_031394_nodocks-click-admiral.png",
                                       "c053_050468_nodocks-drag-admiral.png" }) {
            final java.io.File file = new java.io.File(key, f);
            if (!file.isFile()) {
                System.err.println(getName() + ": no frame " + file + ", skipped");
                continue;
            }
            final int[] got = overFrame(gold, javax.imageio.ImageIO.read(file), l, -1, wood,
                                        tiny);
            out.append(' ').append(f).append(' ').append(got[0]).append('/').append(got[1])
                .append(" arrow ").append(got[2]);
            bad += got[1];
            n++;
        }
        System.out.println(getName() + ": " + n + " frames," + out);
        assertEquals("pixels off:" + out, 0, bad);
    }

    /**
     * The food work follows the square on a move from tile to tile (the
     * review of part Y, fidelity: since part Y3 a work the square cannot
     * make makes nothing, so a kept fish on land made 0 under the red
     * sign; {@link ClassicColonyMoves#keptJob}): a Fischer dragged from the
     * ocean onto the plains is a Farmer there, corn and no sign (V clip
     * 053 #3500 -&gt; #3920, #25812 -&gt; #26045); clicked onto the ocean
     * again, a Fischer (I, by symmetry); a lumberjack dragged onto the
     * ocean keeps his lumber and makes nothing, the sign (R3; I on water).
     */
    public void testTheFoodWorkFollowsTheSquare() throws Exception {
        final Scene s = scene(3, true);
        final Tile c = s.colony.getTile();
        c.getNeighbourOrNull(Direction.E).setType(spec().getTileType("model.tile.ocean"));
        c.getNeighbourOrNull(Direction.SE).setType(spec().getTileType("model.tile.ocean"));
        addBuilding(s, "docks");
        final GoodsType fish = ClassicColonyJobs.goodsOf(spec(), ClassicColonyLayout.JOB_FISHERMAN);
        final GoodsType grain = ClassicColonyJobs.goodsOf(spec(), 0);
        final List<Unit> us = s.colony.getUnitList();
        final Unit fisher = us.get(0), cutter = us.get(1);
        for (Unit x : us) assertTrue(x.setLocation(building(s.colony, "townHall")));
        assertTrue(fisher.setLocation(tileOf(s.colony, Direction.E)));
        fisher.changeWorkType(fish);
        assertTrue(cutter.setLocation(tileOf(s.colony, Direction.N)));
        cutter.changeWorkType(lumber);
        s.colony.invalidateCache();
        paint(s.panel);
        assertTrue("he fishes", made(tileOf(s.colony, Direction.E), fish) > 0);
        // The Fischer onto W, the plains: a Farmer, corn, no sign.
        int[] a = at(s.panel, fisher);
        final int[] w = tile(3);
        drag(s.panel, a[0], a[1], w[0], w[1]);
        assertSame(s.api.log.toString(), tileOf(s.colony, Direction.W), fisher.getLocation());
        assertEquals("a Farmer on land " + s.api.log, grain, fisher.getWorkType());
        assertTrue("corn", made(tileOf(s.colony, Direction.W), grain) > 0);
        ClassicColonyTiles.Model m = tilesModel(s);
        assertFalse("a row", m.cell(3).rows.isEmpty());
        assertEquals("no sign", -1, m.cell(3).zero);
        // He stays selected: a click on the empty ocean E takes him there,
        // a Fischer again (I).
        assertSame(fisher, s.panel.selected());
        final int[] e = tile(5);
        click(s.panel, e[0], e[1]);
        assertSame(s.api.log.toString(), tileOf(s.colony, Direction.E), fisher.getLocation());
        assertEquals("a Fischer on the water " + s.api.log, fish, fisher.getWorkType());
        assertTrue("fish", made(tileOf(s.colony, Direction.E), fish) > 0);
        assertEquals("no sign", -1, tilesModel(s).cell(5).zero);
        // Any other work is kept, on the water too: nothing, the sign.
        a = at(s.panel, cutter);
        final int[] se = tile(8);
        drag(s.panel, a[0], a[1], se[0], se[1]);
        assertSame(s.api.log.toString(), tileOf(s.colony, Direction.SE), cutter.getLocation());
        assertEquals("R3: still the lumberjack", lumber, cutter.getWorkType());
        assertEquals(0, made(tileOf(s.colony, Direction.SE), lumber));
        assertEquals(0, made(tileOf(s.colony, Direction.SE), fish));
        m = tilesModel(s);
        assertEquals("the lumber under the sign",
                     ClassicColonyLayout.goodsIcon(lumber.getId()), m.cell(8).zero);
        assertTrue("no box", s.boxes.asked.isEmpty());
    }

    /** What a work tile makes of a good now (the server's production). */
    private static int made(ColonyTile ct, GoodsType g) {
        ct.getColony().invalidateCache();
        int n = 0;
        for (net.sf.freecol.common.model.AbstractGoods ag
                 : ct.getProductionInfo().getProduction()) {
            if (ag.getType() == g) n += ag.getAmount();
        }
        return n;
    }

    /** The 3x3 map's model of a scene's colony now. */
    private static ClassicColonyTiles.Model tilesModel(Scene s) {
        s.colony.invalidateCache();
        return ClassicColonyTiles.model(s.colony, ClassicText.load(ClassicPackFiles.runtime()),
            ClassicColonyBuildings.Assets.load(ClassicPackFiles.runtime()));
    }

    /**
     * The white cursor after a move off a tile (the review of part U,
     * fidelity M1; clip 042 #6047, #2457): it stays on the emptied tile
     * where it stood; on the centre it goes to his figure in the band.
     */
    public void testTheCursorAfterAMoveOffATile() throws Exception {
        final Scene s = scene(3, true);
        final List<Unit> us = s.colony.getUnitList();
        final Unit w = us.get(0), x = us.get(1);
        w.setLocation(tileOf(s.colony, Direction.N));
        w.changeWorkType(lumber);
        x.setLocation(tileOf(s.colony, Direction.S));
        us.get(2).setLocation(building(s.colony, "townHall"));
        s.colony.invalidateCache();
        paint(s.panel);
        final int[] a = at(s.panel, w);
        final int[] carp = slotOf(s.panel, building(s.colony, "carpenterHouse"));
        drag(s.panel, a[0], a[1], carp[0], carp[1]);
        assertSame(building(s.colony, "carpenterHouse"), w.getLocation());
        assertEquals("the cursor stays on the emptied N", 1, ClassicColonyPanel.tileCursor());
        ClassicColonyPanel.setTileCursor(ClassicColonyLayout.CENTRE_TILE);
        s.panel.select(x);
        paint(s.panel);
        final int[] hall = slotOf(s.panel, building(s.colony, "townHall"));
        click(s.panel, hall[0], hall[1]);
        assertSame(building(s.colony, "townHall"), x.getLocation());
        assertEquals("from the centre to his figure", ClassicColonyPanel.CURSOR_OFF_TILES,
                     ClassicColonyPanel.tileCursor());
    }

    /**
     * The press's look (the review of part U, fidelity M2; clip 042): an
     * unselected building worker or fence unit changes nothing at the
     * press, the release selects him (#11157 → #11168, dago-colony2 #7520
     * → #7532), the drag's start too (#3374); a tile worker is selected at
     * the press with the white cursor on his tile (#5829).
     */
    public void testThePressLook() throws Exception {
        final Scene s = scene(3, true);
        final List<Unit> us = s.colony.getUnitList();
        final Unit a = us.get(0), b = us.get(1), c = us.get(2);
        a.setLocation(building(s.colony, "townHall"));
        b.setLocation(tileOf(s.colony, Direction.S));
        c.setLocation(s.colony.getTile());
        s.colony.invalidateCache();
        paint(s.panel);
        s.panel.select(b);
        ClassicColonyPanel.setTileCursor(7);
        for (Unit u : new Unit[] { a, c }) {
            s.panel.select(b);
            ClassicColonyPanel.setTileCursor(7);
            paint(s.panel);
            final int[] p = at(s.panel, u);
            mouse(s.panel, MouseEvent.MOUSE_PRESSED, p[0], p[1]);
            assertSame("nothing at the press", b, s.panel.selected());
            assertEquals(7, ClassicColonyPanel.tileCursor());
            mouse(s.panel, MouseEvent.MOUSE_RELEASED, p[0], p[1]);
            assertSame("the release selects", u, s.panel.selected());
            assertEquals(ClassicColonyPanel.CURSOR_OFF_TILES, ClassicColonyPanel.tileCursor());
            assertTrue("a first click: no menu", s.boxes.asked.isEmpty());
        }
        paint(s.panel);
        final int[] pb = at(s.panel, b);
        mouse(s.panel, MouseEvent.MOUSE_PRESSED, pb[0], pb[1]);
        assertSame("a tile worker at the press", b, s.panel.selected());
        assertEquals(7, ClassicColonyPanel.tileCursor());
        mouse(s.panel, MouseEvent.MOUSE_RELEASED, pb[0], pb[1]);
        paint(s.panel);
        final int[] pa = at(s.panel, a);
        mouse(s.panel, MouseEvent.MOUSE_PRESSED, pa[0], pa[1]);
        mouse(s.panel, MouseEvent.MOUSE_DRAGGED, pa[0] + 3, pa[1]);
        assertTrue(s.panel.dragging());
        assertSame("the drag's start selects", a, s.panel.selected());
        mouse(s.panel, MouseEvent.MOUSE_RELEASED, pa[0], pa[1]);
        assertFalse(s.panel.dragging());
    }

    /**
     * A click on an empty spot of the fence opens the selected colonist's
     * role menu; he stays where he is, only a pick acts (V clip 043 #3326,
     * #7048, #8471; clip 042 #15119; Roger 2026-10-15): a colonist in the
     * colony gets J1 (his work, the bar on it, then the roles), a unit at
     * the fence the roles with the bar on his own, its row without a cell;
     * +3 frames after the release.
     */
    public void testTheFenceClickOpensTheRoleMenu() throws Exception {
        if (texts() == null) return;
        final Scene s = scene(3, true);
        final List<Unit> us = s.colony.getUnitList();
        final Unit w = us.get(0), v = us.get(1), sold = us.get(2);
        w.setLocation(tileOf(s.colony, Direction.N));
        w.changeWorkType(lumber);
        v.setLocation(building(s.colony, "townHall"));
        sold.setLocation(s.colony.getTile());
        sold.changeRole(spec().getRole("model.role.soldier"), 1);
        s.colony.addGoods(horses, 50);
        s.colony.addGoods(tools, 44);
        s.colony.invalidateCache();
        paint(s.panel);
        s.panel.select(w);
        final int calls = s.api.log.size();
        int[] f = fenceFree(s.panel);
        click(s.panel, f[0], f[1]);
        assertEquals(1, s.boxes.asked.size());
        final ClassicAdvisorBox.Request j1 = s.boxes.asked.get(0);
        assertEquals("[Holzfäller, Siedler, Pionier, Soldat, Späher, Dragoner]",
                     Arrays.toString(j1.plainRows()));
        assertEquals(0, j1.defaultRow);
        assertEquals("(40 Werkzeuge)", ClassicAdvisorBox.plain(j1.rightOf(2)));
        assertEquals("+3 frames", ClassicColonyMoves.ms(3), j1.openDelayMs, 0.01);
        assertSame("he stays (Escape)", tileOf(s.colony, Direction.N), w.getLocation());
        assertEquals(calls, s.api.log.size());
        // The soldier at the fence: the roles, the bar on Soldat, no cell there.
        s.panel.select(sold);
        paint(s.panel);
        f = fenceFree(s.panel);
        s.boxes.answers.add(2);        // Soldat: his own, no change
        click(s.panel, f[0], f[1]);
        final ClassicAdvisorBox.Request r = s.boxes.asked.get(1);
        assertEquals("[Siedler, Pionier, Soldat, Späher, Dragoner]",
                     Arrays.toString(r.plainRows()));
        assertEquals("the bar on his role", 2, r.defaultRow);
        assertNull("his role's row without a cell", r.rightOf(2));
        assertEquals("(50 Musketen) (50 Pferde)", ClassicAdvisorBox.plain(r.rightOf(4)));
        assertTrue(r.plainText(), r.plainText().contains("(Soldat):"));
        assertEquals(calls, s.api.log.size());
        assertEquals("model.role.soldier", sold.getRole().getId());
        // Siedler: the muskets back, he stays at the fence.
        s.boxes.answers.add(0);
        click(s.panel, f[0], f[1]);
        assertTrue(sold.hasDefaultRole());
        assertEquals(50, s.colony.getGoodsCount(muskets));
        assertSame(s.colony.getTile(), sold.getLocation());
        // The colonist on N picks «Soldat»: at once at the fence as a
        // soldier (V Roger 2026-10-15).
        s.panel.select(w);
        paint(s.panel);
        f = fenceFree(s.panel);
        s.boxes.answers.add(3);
        click(s.panel, f[0], f[1]);
        assertSame(s.api.log.toString(), s.colony.getTile(), w.getLocation());
        assertEquals("model.role.soldier", w.getRole().getId());
        assertEquals(0, s.colony.getGoodsCount(muskets));
        assertEquals("the cursor stays off: it was not on N",
                     ClassicColonyPanel.CURSOR_OFF_TILES, ClassicColonyPanel.tileCursor());
    }

    /**
     * J1's first row, his work, is green for an expert at his own work too
     * (V clip 042 #3512 «Tabakhändler» of a Meister-Tabakhändler, clip 043
     * #3326 «Tabakpflanzer» of a Meister-Tabakpflanzer).
     */
    public void testJ1sOwnWorkIsGreen() throws Exception {
        final ClassicText t = texts();
        if (t == null) return;
        final ClassicColonyBuildingsTest.State s = ClassicColonyBuildingsTest.base1539();
        final Unit u = s.on("masterTobaccoPlanter", Direction.N, "tobacco");
        s.finish();
        final ClassicColonyJobs.Menu m = ClassicColonyJobs.menu(t, s.colony, u,
            ClassicColonyJobs.Kind.FENCE_DROP, 1);
        assertEquals("Beruf wählen für Meister-Tabakpflanzer:", m.title);
        assertEquals("Tabakpflanzer", m.rows.get(0).label);
        assertEquals(0, m.bar);
        assertFalse("green", m.rows.get(0).yellow);
    }

    /**
     * A click's target is where the button goes up (V clip 042 #2371 →
     * #2427: pressed on the Zigarrenfabrik, released over the Kathedrale,
     * he went into the Kathedrale).
     */
    public void testTheTargetIsWhereTheButtonGoesUp() throws Exception {
        final Scene s = scene(2, true);
        final List<Unit> us = s.colony.getUnitList();
        final Unit w = us.get(0);
        w.setLocation(tileOf(s.colony, Direction.S));
        us.get(1).setLocation(building(s.colony, "townHall"));
        s.colony.invalidateCache();
        paint(s.panel);
        s.panel.select(w);
        final int[] carp = slotOf(s.panel, building(s.colony, "carpenterHouse"));
        final int[] hall = slotOf(s.panel, building(s.colony, "townHall"));
        drag(s.panel, carp[0], carp[1], hall[0], hall[1]);
        assertSame(s.api.log.toString(), building(s.colony, "townHall"), w.getLocation());
    }

    /**
     * A drop on a worked tile: nothing, no box, he stays selected (V clip
     * 042 #4150, #4839); the hover label comes back by itself +3 frames
     * after it, +13 after a drop on a free tile, +16 after one into a
     * building (#4344, #2876, #4716).
     */
    public void testADropOnAWorkedTileAndTheLabels() throws Exception {
        final Scene s = scene(3, true);
        final List<Unit> us = s.colony.getUnitList();
        final Unit a = us.get(0);
        final Building hall = building(s.colony, "townHall");
        a.setLocation(hall);
        us.get(1).setLocation(tileOf(s.colony, Direction.S));
        us.get(2).setLocation(tileOf(s.colony, Direction.SE));
        s.colony.invalidateCache();
        paint(s.panel);
        int[] p = at(s.panel, a);
        final int[] south = tile(7);
        drag(s.panel, p[0], p[1], south[0], south[1]);
        assertSame("nothing moves", hall, a.getLocation());
        assertTrue("no box", s.boxes.asked.isEmpty());
        assertSame(a, s.panel.selected());
        assertEquals("[later 43]", s.events.toString());
        s.later.get(0).run();
        assertEquals(7, s.panel.hoveredTile());
        paint(s.panel);
        p = at(s.panel, a);
        final int[] west = tile(3);
        drag(s.panel, p[0], p[1], west[0], west[1]);
        assertSame(tileOf(s.colony, Direction.W), a.getLocation());
        assertEquals("[later 43, later 185]", s.events.toString());
        s.later.get(1).run();
        assertEquals(3, s.panel.hoveredTile());
        paint(s.panel);
        p = at(s.panel, a);
        final int[] h = slotOf(s.panel, hall);
        drag(s.panel, p[0], p[1], h[0], h[1]);
        assertSame(hall, a.getLocation());
        assertEquals("[later 43, later 185, later 228]", s.events.toString());
        s.later.get(2).run();
        assertEquals(ClassicColonyPanel.slotAt(h[0], h[1]), s.panel.hovered());
    }

    /**
     * A tile work picked from a building prefers the colony's own tiles
     * to a native's (the review of part U, play L7: no unasked land
     * purchase).
     */
    public void testATileWorkPrefersTheColonysOwnLand() throws Exception {
        final Scene s = scene(1, true);
        // In the Rathaus first (in the square topology he works N, and a
        // tile changing hands sends its worker away).
        final Unit u = s.colony.getUnitList().get(0);
        u.setLocation(building(s.colony, "townHall"));
        final Tile n = s.colony.getTile().getNeighbourOrNull(Direction.N);
        final ServerPlayer natives = getServerPlayer(s.game, "model.nation.arawak");
        n.changeOwnership(natives, null);
        s.colony.invalidateCache();
        final ColonyTile ct = s.colony.getColonyTile(n);
        assertEquals(net.sf.freecol.common.model.UnitLocation.NoAddReason.CLAIM_REQUIRED,
                     ct.getNoWorkReason());
        final UnitType ut = u.getType();
        assertTrue("the forest is the best for lumber",
                   ct.getPotentialProduction(lumber, ut) > 0);
        final ColonyTile best = ClassicColonyJobs.bestFreeTile(s.colony, ut, lumber);
        assertNotNull(best);
        assertNotSame("not the native's forest", ct, best);
        assertSame(s.colony, best.getWorkTile().getOwningSettlement());
    }

    /**
     * The fence unit's menus over clip 042 (#15426 the soldier's J2, two
     * title lines, LÖSCHEN barred, Waffenschmied yellow; #15119 the fence
     * click's roles, the bar on Soldat, no cell there): the boxes drawn
     * over the frames with the clip's numbers, 0 px off but the arrow.
     */
    /**
     * The role rows as Roger's playthrough 4 shows them (fixer A after part
     * X, the colony review's must 3): a unit's own role is the word alone,
     * never grey (clip 048 #80466 and #90785 «{Soldat}», #160461 «Späher»),
     * «Dragoner» is yellow for an Erfahrene Soldaten (#3136, #80466) and
     * grey where the warehouse cannot equip it (#90785: the grey wins); each
     * box drawn over its frame at 0 px apart from the original arrow (J1
     * #3136 with the clip's first row «Fischer», his work there).
     */
    public void testTheRoleRowsOverPlaythrough4() throws Exception {
        final ClassicText t = texts();
        if (t == null) return;
        final ClassicColonyBuildingsTest.State s = ClassicColonyBuildingsTest.base1539();
        final Unit vet = s.in("veteranSoldier", "carpenterHouse");
        s.finish();
        final Unit sold = s.outside("veteranSoldier", "soldier");
        final Unit scout = s.outside("freeColonist", "scout");
        s.colony.removeGoods(tools);
        s.colony.removeGoods(muskets);
        s.colony.removeGoods(horses);
        s.colony.addGoods(tools, 10);
        s.colony.addGoods(horses, 50);
        final ClassicColonyJobs.Menu m80466 = ClassicColonyJobs.menu(t, s.colony, sold,
            ClassicColonyJobs.Kind.OUTSIDE, 2);
        assertEquals("Beruf wählen für Erfahrene Soldaten: | (Weitere) | Siedler"
            + " | Pionier~( | Soldat*^ | Späher( | Dragoner*(", shape(m80466));
        final ClassicColonyJobs.Menu m160461 = ClassicColonyJobs.menu(t, s.colony, scout,
            ClassicColonyJobs.Kind.OUTSIDE, 2);
        assertEquals("Beruf wählen für Freie Siedler(Späher): | (Weitere) | Siedler"
            + " | Pionier~( | Soldat~( | Späher^ | Dragoner~(", shape(m160461));
        s.colony.addGoods(muskets, 50);
        final ClassicColonyJobs.Menu m3136 = ClassicColonyJobs.menu(t, s.colony, vet,
            ClassicColonyJobs.Kind.FENCE_DROP, 1);
        assertEquals("Beruf wählen für Erfahrene Soldaten(Schreiner): | Schreiner^ | Siedler"
            + " | Pionier~( | Soldat*( | Späher( | Dragoner*(", shape(m3136));
        s.colony.removeGoods(horses);
        final ClassicColonyJobs.Menu m90785 = ClassicColonyJobs.menu(t, s.colony, sold,
            ClassicColonyJobs.Kind.OUTSIDE, 2);
        assertEquals("Beruf wählen für Erfahrene Soldaten: | (Weitere) | Siedler"
            + " | Pionier~( | Soldat*^ | Späher~( | Dragoner*~(", shape(m90785));
        // The pick of his own row changes nothing.
        assertEquals(sold.getRoleCount(), m90785.rows.get(m90785.bar).count);
        final ClassicColonyGolden gold = ClassicColonyGolden.open(getName());
        if (gold == null) return;
        final java.io.File dir = new java.io.File(System.getProperty(
            ClassicTerrainGoldenTest.CLIPS_PROPERTY), "fixer-a-analysis/c048");
        if (!dir.isDirectory()) {
            System.err.println(getName() + ": golden check skipped, no clip 048 frames");
            return;
        }
        final ClassicFont tiny = gold.pack.font(ClassicFont.TINY);
        final BufferedImage wood = gold.pack.image(ClassicMenuBar.WOOD_KEY);
        final Object[][] cases = {
            { m80466, null, null, 80466 }, { m90785, null, null, 90785 },
            { m160461, null, null, 160461 },
            { m3136, "Beruf wählen für Erfahrene Soldaten(Fischer):", "Fischer", 3136 },
        };
        final StringBuilder out = new StringBuilder();
        int bad = 0;
        for (Object[] c : cases) {
            final ClassicColonyJobs.Menu m0 = (ClassicColonyJobs.Menu) c[0];
            final List<ClassicColonyJobs.Row> rows = new ArrayList<>(m0.rows);
            if (c[2] != null) {
                final ClassicColonyJobs.Row r = rows.get(0);
                rows.set(0, new ClassicColonyJobs.Row(r.pick, r.job, (String) c[2], null,
                                                      false, false, 0));
            }
            final ClassicColonyJobs.Menu m = new ClassicColonyJobs.Menu(m0.kind, m0.page,
                (c[1] == null) ? m0.title : (String) c[1], rows, m0.bar);
            final ClassicAdvisorBox.Layout l = ClassicAdvisorBox.layout(
                ClassicColonyJobs.request(t, m, 0.0, -1.0), tiny, null);
            assertEquals("#" + c[3], new Rectangle(62, 61, 196, 78), l.box);
            final BufferedImage frame = javax.imageio.ImageIO.read(
                ClassicTerrainGoldenTest.frameFile(dir, (Integer) c[3]));
            final int[] got = over(gold, frame, l, m.bar, wood, tiny);
            out.append(" #").append(c[3]).append(' ').append(got[0]).append('/')
                .append(got[1]).append(" arrow ").append(got[2]);
            bad += got[1];
        }
        System.out.println(getName() + ":" + out);
        assertEquals("pixels off:" + out, 0, bad);
    }

    public void testTheFenceMenusOverClip042() throws Exception {
        final ClassicText t = texts();
        if (t == null) return;
        final ClassicColonyGolden gold = ClassicColonyGolden.open(getName());
        if (gold == null) return;
        final java.io.File dir = new java.io.File(System.getProperty(
            ClassicTerrainGoldenTest.CLIPS_PROPERTY), "colony-moves-analysis/frames");
        if (!dir.isDirectory()) {
            System.err.println(getName() + ": skipped, no clip 042 frames");
            return;
        }
        final ClassicColonyBuildingsTest.State s = ClassicColonyBuildingsTest.base1539();
        s.on("freeColonist", Direction.NW, "grain");
        s.finish();
        s.build("armory");
        s.build("church");
        s.colony.addGoods(tools, 44);
        s.colony.addGoods(horses, 50);
        s.colony.addGoods(muskets, 50);
        final Unit sold = s.outside("masterGunsmith", "soldier");
        final ClassicFont tiny = gold.pack.font(ClassicFont.TINY);
        final BufferedImage wood = gold.pack.image(ClassicMenuBar.WOOD_KEY);
        // #15426.
        final ClassicColonyJobs.Menu j2 = ClassicColonyJobs.menu(t, s.colony, sold,
            ClassicColonyJobs.Kind.OUTSIDE, 1);
        assertEquals("Beruf wählen für Meister-Waffenschmiede(Soldat):", j2.title);
        assertEquals(0, j2.bar);
        assertEquals("(SPEZIALFÄHIGKEIT LÖSCHEN)", j2.rows.get(0).label);
        assertEquals("Waffenschmied", j2.rows.get(16).label);
        assertTrue(j2.rows.get(16).yellow);
        assertEquals("(Weitere)", j2.rows.get(17).label);
        final String[] c15426 = { null, "0 Nahrungsmittel", "0 Zucker", "0 Tabak",
            "0 Baumwolle", "0 Felle", "0 Nutzholz", "0 Erz", "0 Silber", "0 Nahrungsmittel",
            "5 Rum", "12 Zigarren", "5 Stoff", "5 Mäntel", "10 Hämmer", "12 Werkzeuge",
            "24 Musketen", null };
        // #15119.
        final ClassicColonyJobs.Menu fr = ClassicColonyJobs.menu(t, s.colony, sold,
            ClassicColonyJobs.Kind.FENCE_ROLE, 1);
        assertEquals("Beruf wählen für Meister-Waffenschmiede(Soldat): / Siedler / Pionier  "
                     + "(40 Werkzeuge) / [Soldat] / Späher  (50 Pferde) / Dragoner  (50 Musketen)"
                     + " (50 Pferde) / Missionar", fr.toString());
        final String[] c15119 = { null, "(40 Werkzeuge)", null, "(50 Pferde)",
            "(50 Musketen) (50 Pferde)", null };
        final StringBuilder out = new StringBuilder();
        int bad = 0;
        final Object[][] cases = { { j2, c15426, 15426, new Rectangle(62, 10, 196, 180) },
                                   { fr, c15119, 15119, new Rectangle(62, 58, 196, 84) } };
        for (Object[] c : cases) {
            final ClassicColonyJobs.Menu m0 = (ClassicColonyJobs.Menu) c[0];
            final String[] cells = (String[]) c[1];
            final List<ClassicColonyJobs.Row> rows = new ArrayList<>();
            for (int i = 0; i < m0.rows.size(); i++) {
                final ClassicColonyJobs.Row r = m0.rows.get(i);
                rows.add(new ClassicColonyJobs.Row(r.pick, r.job, r.label, cells[i], r.yellow,
                                                   r.grey, r.count));
            }
            final ClassicColonyJobs.Menu m = new ClassicColonyJobs.Menu(m0.kind, 1, m0.title,
                                                                        rows, m0.bar);
            final ClassicAdvisorBox.Layout l = ClassicAdvisorBox.layout(
                ClassicColonyJobs.request(t, m, 0.0, -1.0), tiny, null);
            assertEquals("#" + c[2], c[3], l.box);
            final BufferedImage frame = javax.imageio.ImageIO.read(
                ClassicTerrainGoldenTest.frameFile(dir, (Integer) c[2]));
            final int[] got = over(gold, frame, l, m.bar, wood, tiny);
            out.append(" #").append(c[2]).append(' ').append(got[0]).append('/')
                .append(got[1]).append(" arrow ").append(got[2]);
            bad += got[1];
        }
        System.out.println(getName() + ":" + out);
        assertEquals("pixels off:" + out, 0, bad);
    }
    /** Run one of the screen's key actions (its binding's press or release). */
    private static void action(ClassicColonyPanel p, String name) {
        p.getActionMap().get(name).actionPerformed(
            new java.awt.event.ActionEvent(p, java.awt.event.ActionEvent.ACTION_PERFORMED, name));
    }

    /**
     * The exit on the release (part W, build spec M4.11 target 3; V clip
     * 042: the press #17651 stops the blink, the release #17658, the map
     * #17689): a press on «Abb» does nothing yet, a release elsewhere
     * nothing at all; the release on it leaves the screen as it is, takes
     * no input and closes it 30 frames (428 ms) later.
     */
    public void testTheExitOnTheRelease() throws Exception {
        final Scene s = scene(2, true);
        s.panel.blink().arm();   // as the shown screen's addNotify
        mouse(s.panel, MouseEvent.MOUSE_PRESSED, 312, 190);
        assertFalse(s.panel.leaving());
        assertFalse("the press stops the blink (V clip 042 #17662)",
                    s.panel.blink().isArmed());
        mouse(s.panel, MouseEvent.MOUSE_RELEASED, 200, 190);
        paint(s.panel);
        assertFalse("released elsewhere", s.panel.leaving());
        assertTrue("the blink goes on (I; the review of part W, R12)",
                   s.panel.blink().isArmed());
        assertTrue(s.events.toString(), s.events.isEmpty());
        final BufferedImage before = s.panel.canvas();
        click(s.panel, 312, 190);
        assertTrue(s.panel.leaving());
        assertEquals("[later 428]", s.events.toString());
        assertEquals(30, ClassicColonyMoves.EXIT_MAP_FRAMES);
        // Meanwhile: the same picture, no input (Escape, a tile, the exit).
        assertTrue(Arrays.equals(ClassicColonyPanel.pixelsOf(before),
                                 ClassicColonyPanel.pixelsOf(s.panel.canvas())));
        action(s.panel, "classic_escapeDown");
        action(s.panel, "classic_closeColony");
        final int[] t = tile(1);
        click(s.panel, t[0], t[1]);
        click(s.panel, 312, 190);
        assertEquals("[later 428]", s.events.toString());
        s.later.get(0).run();
        assertEquals("[later 428, closed]", s.events.toString());
        assertFalse(s.panel.leaving());
    }

    /**
     * Escape acts on its release (part W, M4.11 target 3; the manual's key
     * card: "Exit and return to the Map . . . [ESC]"): the press alone
     * nothing, a release without the screen's press (an Escape that closed
     * a box over it) nothing, the press and release the exit.
     */
    public void testTheExitByEscape() throws Exception {
        final Scene s = scene(2, true);
        s.panel.blink().arm();   // as the shown screen's addNotify
        action(s.panel, "classic_closeColony");
        assertFalse("a release alone", s.panel.leaving());
        action(s.panel, "classic_escapeDown");
        assertFalse("the press alone", s.panel.leaving());
        assertTrue(s.panel.blink().isArmed());
        action(s.panel, "classic_closeColony");
        assertTrue(s.panel.leaving());
        assertFalse("the screen stands still until the map (the review of part W, R11)",
                    s.panel.blink().isArmed());
        assertEquals("[later 428]", s.events.toString());
        s.later.get(0).run();
        assertEquals("[later 428, closed]", s.events.toString());
    }

    /**
     * The trigger of @TUTORIAL16 (part W, M4.11 target 2; V clip 020
     * #18949: J2 moved the farmer into the smithy, the food ran short):
     * the screen tells the GUI once a change made the colony's food short,
     * not while it stays sufficient, not when it was short already.
     */
    public void testAChangeThatMakesTheFoodShort() throws Exception {
        final Scene s = scene(4, true);
        final List<Unit> all = s.colony.getUnitList();
        final Unit farmer = all.get(0);
        farmer.setLocation(tileOf(s.colony, Direction.S));
        farmer.changeWorkType(ClassicColonyJobs.goodsOf(spec(), 0));
        for (int i = 1; i < all.size(); i++) {
            all.get(i).setLocation(building(s.colony, "carpenterHouse"));
        }
        s.colony.invalidateCache();
        paint(s.panel);
        final int made = s.colony.getFoodProduction(), eaten = s.colony.getFoodConsumption();
        final int his = farmer.getWorkTile().getMaximumPotential(
            ClassicColonyJobs.goodsOf(spec(), 0), farmer.getType());
        if (made < eaten || made - his >= eaten) {
            System.err.println(getName() + ": the test map's food does not suit ("
                + made + " made, " + eaten + " eaten, " + his + " his), skipped");
            return;
        }
        // A move that keeps the food sufficient: nothing.
        final Unit other = all.get(1);
        final int[] o = at(s.panel, other);
        final int[] w = tile(7);
        drag(s.panel, o[0], o[1], w[0], w[1]);
        assertTrue(s.boxes.shortOf.toString(), s.boxes.shortOf.isEmpty());
        // The farmer into a building: the food runs short, once.
        final int[] f = at(s.panel, farmer);
        final int[] b = slotOf(s.panel, building(s.colony, "blacksmithHouse"));
        drag(s.panel, f[0], f[1], b[0], b[1]);
        assertTrue(s.api.log.toString(), farmer.getLocation() instanceof Building);
        assertTrue(s.colony.getFoodProduction() < s.colony.getFoodConsumption());
        assertEquals(List.of(s.colony), s.boxes.shortOf);
        // Short already: no second word.
        final int[] o2 = at(s.panel, other);
        final int[] w2 = tile(3);
        drag(s.panel, o2[0], o2[1], w2[0], w2[1]);
        assertEquals(1, s.boxes.shortOf.size());
    }

    /**
     * The same trigger from a job menu (V clip 020 #18949: it was J2 that
     * moved the farmer into the smithy; the review of part W, regression
     * L7, R13): the pick that makes the food short tells the GUI, once.
     */
    public void testAJobMenuPickThatMakesTheFoodShort() throws Exception {
        final Scene s = scene(4, true);
        final List<Unit> all = s.colony.getUnitList();
        final Unit farmer = all.get(0);
        farmer.setLocation(tileOf(s.colony, Direction.S));
        farmer.changeWorkType(ClassicColonyJobs.goodsOf(spec(), 0));
        for (int i = 1; i < all.size(); i++) {
            all.get(i).setLocation(building(s.colony, "carpenterHouse"));
        }
        s.colony.invalidateCache();
        paint(s.panel);
        final int made = s.colony.getFoodProduction(), eaten = s.colony.getFoodConsumption();
        final int his = farmer.getWorkTile().getMaximumPotential(
            ClassicColonyJobs.goodsOf(spec(), 0), farmer.getType());
        if (made < eaten || made - his >= eaten) {
            System.err.println(getName() + ": the test map's food does not suit ("
                + made + " made, " + eaten + " eaten, " + his + " his), skipped");
            return;
        }
        final int[] f = at(s.panel, farmer);
        click(s.panel, f[0], f[1]);           // select
        final int before = s.boxes.asked.size();
        click(s.panel, f[0], f[1]);           // J2, Escape: no change
        assertEquals(before + 1, s.boxes.asked.size());
        final String[] rows = s.boxes.asked.get(before).plainRows();
        int smith = -1;
        for (int i = 0; i < rows.length; i++) if (rows[i].startsWith("Schmied")) smith = i;
        assertTrue(Arrays.toString(rows), smith >= 0);
        assertTrue(s.boxes.shortOf.isEmpty());
        paint(s.panel);
        s.boxes.answers.add(smith);
        click(s.panel, f[0], f[1]);           // J2: «Schmied»
        assertTrue(s.api.log.toString(), farmer.getLocation() instanceof Building);
        assertTrue(s.colony.getFoodProduction() < s.colony.getFoodConsumption());
        assertEquals(List.of(s.colony), s.boxes.shortOf);
    }

    /**
     * The white cursor left on a carrier (a press on one in another colony,
     * a docking) goes to the centre in a colony without a carrier in port
     * (fixer A after part X, the colony review's later item: it stood
     * nowhere); with one in port it stays on it.
     */
    public void testTheCarrierCursorInAColonyWithoutOne() throws Exception {
        final Scene s = scene(2, true);
        ClassicColonyPanel.setTileCursor(ClassicColonyPanel.CURSOR_CARRIER);
        s.panel.opened(ClassicColonyRight.Opening.OTHER);
        assertEquals(ClassicColonyLayout.CENTRE_TILE, ClassicColonyPanel.tileCursor());
        new ServerUnit(s.game, s.colony.getTile(), s.dutch,
                       spec().getUnitType("model.unit.wagonTrain"));
        ClassicColonyPanel.setTileCursor(ClassicColonyPanel.CURSOR_CARRIER);
        s.panel.opened(ClassicColonyRight.Opening.OTHER);
        assertEquals(ClassicColonyPanel.CURSOR_CARRIER, ClassicColonyPanel.tileCursor());
    }

    /** A click of another button than the left (BUTTON2, BUTTON3) at a canvas point. */
    private static void clickWith(ClassicColonyPanel p, int vx, int vy, int button) {
        final int mask = (button == MouseEvent.BUTTON3) ? InputEvent.BUTTON3_DOWN_MASK
            : InputEvent.BUTTON2_DOWN_MASK;
        p.dispatchEvent(new MouseEvent(p, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                                       mask, vx * 3 + 1, vy * 3 + 1, 1, false, button));
        p.dispatchEvent(new MouseEvent(p, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(),
                                       0, vx * 3 + 1, vy * 3 + 1, 1, false, button));
        paint(p);
    }

    /**
     * The right-click (fixer A after part X, the colony review's must 4
     * and its later item: V clip 049 with the keylog): on a field of the
     * 3x3 map the white cursor goes there at the press and the release
     * asks its terrain page 22 frames after the press (#4175 -&gt; #4198);
     * on a colonist his type's skill page (#11762 «Siedler» for a free
     * colonist with tools at the fence); the right and the middle button
     * never act as the left: no exit on «Abb», no mode switch, no
     * selection, no menu.
     */
    public void testTheRightClick() throws Exception {
        final Scene s = scene(2, true);
        final List<Unit> all = s.colony.getUnitList();
        all.get(0).setLocation(tileOf(s.colony, Direction.N));
        all.get(1).setLocation(building(s.colony, "carpenterHouse"));
        final Unit pioneer = new ServerUnit(s.game, s.colony.getTile(), s.dutch,
            spec().getUnitType("model.unit.freeColonist"), spec().getRole("model.role.pioneer"));
        s.colony.invalidateCache();
        paint(s.panel);
        ClassicColonyPanel.setTileCursor(ClassicColonyLayout.CENTRE_TILE);
        s.panel.select(all.get(1));
        paint(s.panel);
        final Rectangle ne = ClassicColonyLayout.tileCell(2);
        clickWith(s.panel, ne.x + 12, ne.y + 12, MouseEvent.BUTTON3);
        assertEquals(2, ClassicColonyPanel.tileCursor());
        assertEquals(List.of(s.colony.getTile().getNeighbourOrNull(Direction.NE)),
                     s.boxes.pages);
        final double d = s.boxes.delays.get(0);
        assertTrue("22 frames after the press: " + d,
                   d > 0.0 && d <= ClassicColonyMoves.ms(22) + 1e-9);
        assertSame("the selection stays", all.get(1), s.panel.selected());
        // The building worker and the pioneer at the fence: their types' pages.
        final int[] b = at(s.panel, all.get(1));
        clickWith(s.panel, b[0], b[1], MouseEvent.BUTTON3);
        final int[] f = at(s.panel, pioneer);
        clickWith(s.panel, f[0], f[1], MouseEvent.BUTTON3);
        assertEquals(List.of(ClassicColonyLayout.JOB_COLONIST, ClassicColonyLayout.JOB_COLONIST),
                     s.boxes.skills);
        assertSame("a right-click selects nobody", all.get(1), s.panel.selected());
        assertTrue("no menu", s.boxes.asked.isEmpty());
        // «Abb» and the mode buttons: nothing, with the right or the middle button.
        final ClassicColonyRight.Mode mode = s.panel.mode();
        final Rectangle exit = ClassicColonyLayout.EXIT;
        for (int button : new int[] { MouseEvent.BUTTON3, MouseEvent.BUTTON2 }) {
            clickWith(s.panel, exit.x + exit.width / 2, exit.y + exit.height / 2, button);
            for (int m = 0; m < 3; m++) {
                final Rectangle mb = ClassicColonyLayout.modeCell(m);
                clickWith(s.panel, mb.x + mb.width / 2, mb.y + mb.height / 2, button);
            }
        }
        assertFalse(s.panel.leaving());
        assertTrue(s.events.toString(), s.events.isEmpty());
        assertSame(mode, s.panel.mode());
        assertEquals(2, s.boxes.skills.size());
        assertEquals(1, s.boxes.pages.size());
        // The middle button on a field asks nothing and leaves the cursor.
        final Rectangle sw = ClassicColonyLayout.tileCell(6);
        clickWith(s.panel, sw.x + 12, sw.y + 12, MouseEvent.BUTTON2);
        assertEquals(2, ClassicColonyPanel.tileCursor());
        assertEquals(1, s.boxes.pages.size());
        // A right press on a field released on another: nothing (I).
        final MouseEvent press = new MouseEvent(s.panel, MouseEvent.MOUSE_PRESSED,
            System.currentTimeMillis(), InputEvent.BUTTON3_DOWN_MASK, sw.x * 3 + 30,
            sw.y * 3 + 30, 1, false, MouseEvent.BUTTON3);
        s.panel.dispatchEvent(press);
        s.panel.dispatchEvent(new MouseEvent(s.panel, MouseEvent.MOUSE_RELEASED,
            System.currentTimeMillis(), 0, ne.x * 3 + 30, ne.y * 3 + 30, 1, false,
            MouseEvent.BUTTON3));
        assertEquals("the cursor went at the press", 6, ClassicColonyPanel.tileCursor());
        assertEquals(1, s.boxes.pages.size());
    }

    /**
     * F1 (part W, M4.11 target 4; fixer A after part X): the white
     * cursor's tile's terrain page (V clip 020 #76921, #78325); with the
     * cursor on the selected colonist's figure his type's skill page (V
     * clip 052 #16124 «Soldat» for an Erfahrene Soldaten), 22 frames after
     * the key; nothing with the cursor on a carrier.  In the job menu the
     * skill page of the job under the bar (V clip 049 #3106, clip 052
     * #15108 «Tabakpflanzer» with the bar moved there), then the same menu
     * again with its bar on its default row, 7 frames after the page (V
     * clip 052 #15840), his work unchanged.
     */
    public void testF1AsksTheCursorsTile() throws Exception {
        final Scene s = scene(2, true);
        final List<Unit> all = s.colony.getUnitList();
        all.get(0).setLocation(tileOf(s.colony, Direction.N));
        all.get(1).setLocation(building(s.colony, "carpenterHouse"));
        s.colony.invalidateCache();
        paint(s.panel);
        ClassicColonyPanel.setTileCursor(2);
        paint(s.panel);
        action(s.panel, "classic_colonyPedia");
        assertEquals(List.of(s.colony.getTile().getNeighbourOrNull(Direction.NE)),
                     s.boxes.pages);
        assertEquals(ClassicColonyMoves.ms(22), s.boxes.delays.get(0), 1e-9);
        ClassicColonyPanel.setTileCursor(ClassicColonyPanel.CURSOR_OFF_TILES);
        s.panel.select(all.get(0));
        action(s.panel, "classic_colonyPedia");
        final int type0 = ClassicColonyPanel.typePage(all.get(0));
        assertTrue(type0 >= 0);
        assertEquals(List.of(type0), s.boxes.skills);
        assertEquals("no work tile's page any more", 1, s.boxes.pages.size());
        s.panel.select(all.get(1));
        ClassicColonyPanel.setTileCursor(ClassicColonyPanel.CURSOR_OFF_TILES);
        action(s.panel, "classic_colonyPedia");
        assertEquals(List.of(type0, ClassicColonyPanel.typePage(all.get(1))), s.boxes.skills);
        ClassicColonyPanel.setTileCursor(ClassicColonyPanel.CURSOR_CARRIER);
        action(s.panel, "classic_colonyPedia");
        assertEquals("the cursor on a carrier: none", 2, s.boxes.skills.size());
        assertEquals(1, s.boxes.pages.size());
        assertFalse(s.panel.leaving());
        // F1 in the tile worker's job menu (J2, «(F1 für Hilfe)») with the
        // bar moved to «Tabakpflanzer»: the menu goes, that job's skill page
        // comes, then the same menu again, his work stays.
        paint(s.panel);
        final Unit worker = all.get(0);
        final Object work = worker.getWorkType();
        final int[] w = at(s.panel, worker);
        click(s.panel, w[0], w[1]);     // select
        final ClassicText t = texts();
        final int boxes = s.boxes.asked.size();
        if (t != null) {
            s.boxes.helpOn = ClassicColonyJobs.menu(t, s.colony, worker,
                ClassicColonyJobs.Kind.WORKER, 1).rowOf(ClassicColonyJobs.Pick.JOB, 2);
            assertTrue(s.boxes.helpOn >= 0);
        }
        s.boxes.answers.add(ClassicAdvisorBox.Bar.HELP);
        click(s.panel, w[0], w[1]);     // J2, F1, J2 again (Escape)
        final ClassicAdvisorBox.Request j2 = s.boxes.asked.get(boxes);
        assertTrue(j2.id, j2.id.startsWith("jobs "));
        assertNotNull("F1 answers", j2.help);
        if (t != null) {
            assertEquals(boxes + 2, s.boxes.asked.size());
            assertEquals("the job under the bar", Integer.valueOf(2),
                         s.boxes.skills.get(s.boxes.skills.size() - 1));
            final ClassicAdvisorBox.Request again = s.boxes.asked.get(boxes + 1);
            assertEquals(j2.id, again.id);
            assertEquals("the bar on its default row", j2.defaultRow, again.defaultRow);
            assertEquals(ClassicColonyMoves.ms(7), again.chainMs, 1e-9);
        }
        assertEquals(1, s.boxes.pages.size());
        assertSame(work, worker.getWorkType());
        assertFalse(s.panel.leaving());
        // L and U stay unmapped on the screen (build spec M4.12).
        final javax.swing.InputMap im
            = s.panel.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        assertNull(im.get(javax.swing.KeyStroke.getKeyStroke("L")));
        assertNull(im.get(javax.swing.KeyStroke.getKeyStroke("U")));
    }
}
