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

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import net.sf.freecol.FreeCol;
import net.sf.freecol.common.io.FreeColRules;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Nation;
import net.sf.freecol.common.model.NationOptions;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Topology;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.option.MapGeneratorOptions;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.server.generator.SimpleMapGenerator;
import net.sf.freecol.server.model.ServerGame;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * The original's view rule (build spec W4) replayed on the landfall clip:
 * every slide start ({@code 02-support/margin_check.txt} plus the five
 * from {@code 00-timeline.md}) and every unit selection, in the clip's
 * order, on its 58x72 map.  The rule must give exactly the 13 jumps of
 * landfall 02 section 4.1 and the 14 views V1-V14 (00-timeline "View
 * offsets"), and nothing else.  Once on the rule alone
 * ({@link ClassicHud#needsRecentre}, {@link ClassicHud#viewFor}), once
 * through the map viewer's stored origin.
 */
public class ClassicViewRuleTest extends FreeColTestCase {

    /** The original's map. */
    private static final int W = 58, H = 72;

    /**
     * Event kinds: the game's start, a selection at a turn's start, the
     * next unit selected, a slide start of an own unit, of a native, and
     * an attack lunge.
     */
    private static final int START = 0, TURN = 1, SELECT = 2, MOVE = 3,
        NATIVE = 4, LUNGE = 5;

    /** The units of the clip (the native moves need none). */
    private static final int NONE = -1, SHIP = 0, SOLDIER = 1, PIONEER = 2;

    /**
     * The clip's events, {frame, kind, unit, x, y}: the start, the 27 unit
     * selections (12 at a turn's start) and the 86 slide starts (the
     * moving unit's source tile; two of them are the soldier's attack
     * lunges, which MarginCheck counted as slides).  A slide that jumped
     * carries its jump frame (one before the slide's first frame).  Map
     * tiles are the cells of margin_check.txt plus the view of the time;
     * the selections, the units and the five extra slides come from
     * 00-timeline.md.
     */
    private static final int[][] EVENTS = {
        { 0, START, SHIP, 56, 42 },           // (14,6) of V1: east clamp
        // Turn 1 (1492)
        { 1147, MOVE, SHIP, 56, 42 },         // (14,6) W: east clamp, no jump
        { 1385, MOVE, SHIP, 55, 42 },         // (13,6): east clamp
        { 2085, MOVE, SHIP, 54, 42 },
        { 4039, MOVE, SHIP, 53, 41 },
        { 4214, MOVE, SHIP, 52, 41 },
        // Turn 2
        { 4392, TURN, SHIP, 51, 40 },
        { 4564, MOVE, SHIP, 51, 40 },
        { 4703, MOVE, SHIP, 52, 39 },
        { 4854, MOVE, SHIP, 51, 38 },         // row 2: no jump
        { 4975, MOVE, SHIP, 50, 39 },
        { 5112, MOVE, SHIP, 49, 39 },
        // Turn 3
        { 5306, TURN, SHIP, 48, 39 },
        { 6500, MOVE, SHIP, 48, 39 },
        { 6571, MOVE, SHIP, 49, 39 },
        { 6718, MOVE, SHIP, 50, 39 },
        { 6856, MOVE, SHIP, 51, 39 },
        { 6967, MOVE, SHIP, 52, 39 },
        // Turn 4
        { 7159, TURN, SHIP, 53, 39 },
        { 7293, MOVE, SHIP, 53, 39 },
        { 7398, MOVE, SHIP, 54, 39 },
        { 7497, MOVE, SHIP, 55, 39 },         // (13,3): east clamp
        { 8357, MOVE, SHIP, 56, 39 },         // (14,3): east clamp
        { 8782, MOVE, SHIP, 55, 40 },         // (13,4): east clamp
        // Turn 5
        { 8994, TURN, SHIP, 54, 40 },         // (12,4)
        { 9084, MOVE, SHIP, 54, 40 },
        { 9183, MOVE, SHIP, 53, 40 },
        { 9291, MOVE, SHIP, 52, 41 },
        { 9372, MOVE, SHIP, 53, 42 },
        { 9516, MOVE, SHIP, 52, 43 },
        { 9594, NATIVE, NONE, 50, 42 },
        // Turn 6
        { 9769, TURN, SHIP, 51, 44 },         // (9,8)
        { 9872, MOVE, SHIP, 51, 44 },
        { 10077, MOVE, SHIP, 50, 45 },        // row 9; arrives in row 10
        { 10269, MOVE, SHIP, 49, 46 },        // jump #1: row 10 (slide 10270)
        { 11576, MOVE, PIONEER, 48, 45 },     // the pioneer goes ashore
        { 13118, SELECT, SOLDIER, 48, 45 },   // the soldier, still aboard
        { 13318, MOVE, SOLDIER, 48, 45 },
        { 13649, SELECT, SHIP, 48, 45 },      // the ship again
        { 13833, MOVE, SHIP, 48, 45 },
        { 13948, MOVE, SHIP, 48, 46 },
        { 14028, NATIVE, NONE, 47, 44 },
        { 14053, NATIVE, NONE, 51, 43 },
        // Turn 7
        { 14247, TURN, SHIP, 47, 47 },        // (5,7)
        { 14483, MOVE, SHIP, 47, 47 },
        { 14612, MOVE, SHIP, 46, 47 },
        { 14738, MOVE, SHIP, 45, 48 },
        { 14876, MOVE, SHIP, 44, 47 },        // column 2; arrives in column 1
        { 15010, MOVE, SHIP, 43, 48 },        // jump #2: column 1 (slide 15012)
        { 15268, SELECT, PIONEER, 47, 46 },   // the pioneer (11,4)
        { 17442, SELECT, SOLDIER, 48, 44 },   // the soldier (12,2)
        { 17593, MOVE, SOLDIER, 48, 44 },
        { 17670, NATIVE, NONE, 45, 45 },
        { 17712, NATIVE, NONE, 52, 42 },      // jump #3: off the view
        // Turn 8
        { 18602, TURN, SHIP, 42, 48 },        // jump #4
        { 18740, MOVE, SHIP, 42, 48 },
        { 18814, MOVE, SHIP, 41, 48 },
        { 18889, MOVE, SHIP, 40, 48 },
        { 18980, MOVE, SHIP, 39, 48 },
        { 19083, MOVE, SHIP, 38, 49 },
        { 19142, SELECT, PIONEER, 47, 46 },   // the pioneer (12,4)
        { 19462, MOVE, PIONEER, 47, 46 },     // 04:37.686 (00-timeline)
        { 19506, SELECT, SOLDIER, 49, 43 },   // jump #5: the soldier at (14,1)
        { 19703, MOVE, SOLDIER, 49, 43 },
        { 20585, NATIVE, NONE, 45, 43 },
        { 20612, NATIVE, NONE, 46, 46 },      // (4,9)
        // Turn 9
        { 20813, TURN, SHIP, 37, 49 },        // jump #6
        { 21022, MOVE, SHIP, 37, 49 },
        { 21127, MOVE, SHIP, 36, 50 },
        { 21226, MOVE, SHIP, 35, 49 },
        { 21316, MOVE, SHIP, 34, 50 },
        { 21439, MOVE, SHIP, 33, 51 },
        { 21480, SELECT, PIONEER, 47, 45 },   // jump #7
        { 22137, MOVE, PIONEER, 47, 45 },
        { 22198, SELECT, SOLDIER, 50, 44 },   // the village box is cancelled
        { 22790, NATIVE, NONE, 46, 44 },
        { 22816, NATIVE, NONE, 45, 47 },
        { 22840, NATIVE, NONE, 50, 42 },
        // Turn 10
        { 23029, TURN, SHIP, 32, 52 },        // jump #8
        { 23381, MOVE, SHIP, 32, 52 },        // goto to Amsterdam
        { 23411, MOVE, SHIP, 33, 52 },
        { 23438, MOVE, SHIP, 34, 52 },
        { 23465, MOVE, SHIP, 35, 52 },
        { 23497, MOVE, SHIP, 36, 52 },
        { 23537, SELECT, PIONEER, 48, 44 },   // jump #9
        { 23688, MOVE, PIONEER, 48, 44 },
        { 23748, SELECT, SOLDIER, 50, 44 },
        { 23838, MOVE, SOLDIER, 50, 44 },
        { 23919, NATIVE, NONE, 47, 45 },      // 05:41.279 (00-timeline)
        { 23944, NATIVE, NONE, 44, 46 },      // 05:41.636 (00-timeline)
        { 23969, NATIVE, NONE, 49, 43 },      // 05:41.993 (00-timeline)
        // Turn 11
        { 24165, TURN, SHIP, 37, 52 },        // jump #10
        { 24174, MOVE, SHIP, 37, 52 },
        { 24207, MOVE, SHIP, 38, 52 },
        { 24230, MOVE, SHIP, 39, 52 },
        { 24253, MOVE, SHIP, 40, 52 },
        { 24275, MOVE, SHIP, 41, 52 },
        { 24315, SELECT, PIONEER, 47, 43 },   // jump #11
        { 24498, MOVE, PIONEER, 47, 43 },
        { 24558, SELECT, SOLDIER, 50, 43 },
        { 25349, LUNGE, SOLDIER, 50, 43 },
        { 25480, NATIVE, NONE, 47, 46 },      // (7,9)
        // Turn 12
        { 25687, TURN, SHIP, 42, 52 },        // jump #12, then its goto
        { 25690, MOVE, SHIP, 42, 52 },
        { 25713, MOVE, SHIP, 43, 52 },
        { 25754, SELECT, PIONEER, 46, 42 },   // jump #13
        { 25898, MOVE, PIONEER, 46, 42 },
        { 25958, SELECT, SOLDIER, 50, 43 },
        { 26785, LUNGE, SOLDIER, 50, 43 },
        // Turn 13
        { 27114, TURN, PIONEER, 45, 43 },
        { 27307, MOVE, PIONEER, 45, 43 },
        { 27367, SELECT, SOLDIER, 50, 43 },
        { 27613, MOVE, SOLDIER, 50, 43 },
        { 27693, NATIVE, NONE, 43, 45 },      // 06:35.127 (00-timeline); (4,9)
    };

    /** The views V1-V14: {first frame, vx, vy} (00-timeline, 02 section 4.1). */
    private static final int[][] VIEWS = {
        { 0, 42, 36 }, { 10269, 42, 40 }, { 15010, 36, 42 }, { 17712, 42, 36 },
        { 18602, 35, 42 }, { 19506, 42, 37 }, { 20813, 30, 43 },
        { 21480, 40, 39 }, { 23029, 25, 46 }, { 23537, 41, 38 },
        { 24165, 30, 46 }, { 24315, 40, 37 }, { 25687, 35, 46 },
        { 25754, 39, 36 }
    };

    /** The view the clip shows after an event of {@code frame}. */
    private static int[] viewAt(int frame) {
        int[] v = null;
        for (int[] w : VIEWS) {
            if (w[0] <= frame) v = new int[] { w[1], w[2] };
        }
        return v;
    }

    private static int count(int kind) {
        int n = 0;
        for (int[] e : EVENTS) if (e[1] == kind) n++;
        return n;
    }

    /** The replay's input is the clip's: 86 slide starts, 27 selections. */
    public void testEventCounts() {
        assertEquals(86, count(MOVE) + count(NATIVE) + count(LUNGE));
        assertEquals(27, count(TURN) + count(SELECT));
        assertEquals(12, count(TURN));
        assertEquals(1, count(START));
        for (int i = 1; i < EVENTS.length; i++) {
            assertTrue("in order at " + EVENTS[i][0],
                       EVENTS[i - 1][0] < EVENTS[i][0]);
        }
    }

    /** The rule alone gives exactly the clip's 13 jumps and V1-V14. */
    public void testRuleReplaysLandfall() {
        int[] v = null;
        final List<Integer> jumps = new ArrayList<>();
        final List<String> cells = new ArrayList<>();
        for (int[] e : EVENTS) {
            final int x = e[3], y = e[4];
            if (e[1] == START) {
                v = ClassicHud.viewFor(W, H, x, y);
            } else if (ClassicHud.needsRecentre(W, H, v[0], v[1], x, y)) {
                cells.add("(" + (x - v[0]) + "," + (y - v[1]) + ")");
                v = ClassicHud.viewFor(W, H, x, y);
                jumps.add(e[0]);
            }
            assertTrue("view after #" + e[0] + ": " + Arrays.toString(v),
                       Arrays.equals(viewAt(e[0]), v));
        }
        assertEquals(Arrays.asList(10269, 15010, 17712, 18602, 19506, 20813,
                                   21480, 23029, 23537, 24165, 24315, 25687,
                                   25754), jumps);
        // The deciding cells in the old view: row 10, column 1, off the
        // view, (0,12), the soldier at (14,1), then the off-view selections.
        assertEquals(Arrays.asList("(7,10)", "(1,8)", "(16,0)", "(0,12)",
                                   "(14,1)", "(-5,12)", "(17,2)", "(-8,13)",
                                   "(23,-2)", "(-4,14)", "(17,-3)", "(2,15)",
                                   "(11,-4)"), cells);
    }

    /** The named cases of the spec's acceptance, one by one. */
    public void testNamedCases() {
        final int[] v1 = { 42, 36 }, v2 = { 42, 40 }, v5 = { 35, 42 };
        // #10269: the ship starts its move in row 10 -> V2.
        assertTrue(jumps(v1, 49, 46));
        assertTrue(Arrays.equals(v2, ClassicHud.viewFor(W, H, 49, 46)));
        // #15010: column 1 -> V3, both axes (only the column was in the
        // margin; the rows moved too).
        assertTrue(jumps(v2, 43, 48));
        assertTrue(Arrays.equals(new int[] { 36, 42 },
                                 ClassicHud.viewFor(W, H, 43, 48)));
        // #19506: the soldier selected on screen at (14,1) -> V6.
        assertTrue(jumps(v5, 49, 43));
        assertTrue(Arrays.equals(new int[] { 42, 37 },
                                 ClassicHud.viewFor(W, H, 49, 43)));
        // None at #10077 (row 9) or #14876 (column 2).
        assertFalse(jumps(v1, 50, 45));
        assertFalse(jumps(v2, 44, 47));
        // None at the east clamp: #1147 (14,6), #1385 (13,6), #7497 (13,3),
        // #8357 (14,3), #8782 (13,4); a recentre would have given (42,33)
        // and (42,34) for the last three.
        for (int[] t : new int[][] { { 56, 42 }, { 55, 42 }, { 55, 39 },
                                     { 56, 39 }, { 55, 40 } }) {
            assertFalse("east clamp " + t[0] + "," + t[1],
                        jumps(v1, t[0], t[1]));
        }
        // #17712: the brave off the view at (16,0) -> V4, x clamped, the
        // brave in (10,6).
        assertTrue(jumps(new int[] { 36, 42 }, 52, 42));
        assertTrue(Arrays.equals(new int[] { 42, 36 },
                                 ClassicHud.viewFor(W, H, 52, 42)));
    }

    private static boolean jumps(int[] v, int x, int y) {
        return ClassicHud.needsRecentre(W, H, v[0], v[1], x, y);
    }

    /**
     * The margins on a view away from every edge: columns 2..12 and rows
     * 2..9 are safe; at a clamp that side never counts.
     */
    public void testMarginsAndClamps() {
        final int vx = 20, vy = 30;
        for (int c = -2; c <= 16; c++) {
            for (int r = -2; r <= 13; r++) {
                final boolean safe = c >= 2 && c <= 12 && r >= 2 && r <= 9;
                assertEquals("cell " + c + "," + r, !safe,
                    ClassicHud.needsRecentre(W, H, vx, vy, vx + c, vy + r));
            }
        }
        // Each side at its clamp: its two outer lines do not count.
        assertEquals(42, ClassicHud.maxViewX(W));
        assertEquals(59, ClassicHud.maxViewY(H));
        for (int k = 0; k < 2; k++) {
            assertFalse(ClassicHud.needsRecentre(W, H, 42, 30, 42 + 13 + k, 35));
            assertFalse(ClassicHud.needsRecentre(W, H, 1, 30, 1 + k, 35));
            assertFalse(ClassicHud.needsRecentre(W, H, 20, 1, 27, 1 + k));
            assertFalse(ClassicHud.needsRecentre(W, H, 20, 59, 27, 59 + 10 + k));
            // ... but the other axis still does.
            assertTrue(ClassicHud.needsRecentre(W, H, 42, 30, 42 + 13 + k, 31));
        }
        // The clamps: the outer ring is not shown for any tile inside it.
        assertTrue(Arrays.equals(new int[] { 1, 1 }, ClassicHud.viewFor(W, H, 1, 1)));
        assertTrue(Arrays.equals(new int[] { 42, 59 }, ClassicHud.viewFor(W, H, 56, 70)));
        for (int x = 1; x <= W - 2; x++) {
            for (int y = 1; y <= H - 2; y++) {
                final int[] v = ClassicHud.viewFor(W, H, x, y);
                assertTrue(v[0] >= 1 && v[0] <= 42 && v[1] >= 1 && v[1] <= 59);
                assertTrue(x - v[0] >= 0 && x - v[0] < 15
                           && y - v[1] >= 0 && y - v[1] < 12);
            }
        }
        assertTrue(Arrays.equals(new int[] { 1, 1 }, ClassicHud.clampView(W, H, -5, -9)));
        assertTrue(Arrays.equals(new int[] { 42, 59 }, ClassicHud.clampView(W, H, 50, 80)));
        assertTrue(Arrays.equals(new int[] { 20, 30 }, ClassicHud.clampView(W, H, 20, 30)));
        // A map smaller than the view: the origin stays at 1, no jumps.
        assertTrue(Arrays.equals(new int[] { 1, 1 }, ClassicHud.viewFor(10, 8, 9, 7)));
        assertFalse(ClassicHud.needsRecentre(10, 8, 1, 1, 9, 7));
    }

    /**
     * FreeCol's outer ring (live run m1-w4-west: the ship started at
     * (57,60), the last column, which the clamped view never shows): a
     * unit there is off the view, so it jumps even at the clamp, and the
     * view moves just far enough to show it; once shown, the clamped side
     * does not count.
     */
    public void testOuterRing() {
        assertTrue(ClassicHud.needsRecentre(W, H, 42, 54, 57, 60));
        assertTrue(Arrays.equals(new int[] { 43, 54 }, ClassicHud.viewFor(W, H, 57, 60)));
        assertFalse(ClassicHud.needsRecentre(W, H, 43, 54, 57, 60));
        assertFalse(ClassicHud.needsRecentre(W, H, 43, 54, 56, 60));
        assertTrue(Arrays.equals(new int[] { 0, 0 }, ClassicHud.viewFor(W, H, 0, 0)));
        assertTrue(Arrays.equals(new int[] { 43, 60 }, ClassicHud.viewFor(W, H, 57, 71)));
        assertTrue(ClassicHud.needsRecentre(W, H, 1, 30, 0, 35));
        assertFalse(ClassicHud.needsRecentre(W, H, 0, 30, 0, 35));
        // The free pan never goes past the clamp.
        assertTrue(Arrays.equals(new int[] { 42, 59 }, ClassicHud.clampView(W, H, 43, 60)));
    }

    /**
     * The start (build spec W4 acceptance, landfall #340): the ship at
     * (56,42) gives the origin (42,36) with the ship in cell (14,6), and
     * the minimap ring at y 22..33; at vy = 46 at y 23..34.
     */
    public void testStartViewAndRing() {
        final int[] v = ClassicHud.viewFor(W, H, 56, 42);
        assertTrue(Arrays.equals(new int[] { 42, 36 }, v));
        assertEquals(14, 56 - v[0]);
        assertEquals(6, 42 - v[1]);
        assertEquals(new Rectangle(293, 22, 15, 12), ring(42, 36));
        assertEquals(new Rectangle(276, 23, 15, 12), ring(25, 46));
        assertEquals(new Rectangle(281, 23, 15, 12), ring(30, 46));
        assertEquals(22, ring(40, 39).y);
    }

    /**
     * The start of new games (N16, build spec W4 #340): on a square map
     * from the map generator every European ship starts in the last
     * drawn column, so the start view (activation, then the focus, as
     * {@code ClassicGUI.reconnectGUI}) clamps at the east edge with the
     * ship in cell (14,6); for a ship at (56,42) that is the origin
     * (42,36) of the original.
     */
    public void testNewGamesStartInCell14x6() {
        final Topology saved = Topology.current();
        try {
            Topology.setCurrent(Topology.SQUARE);
            int ships = 0;
            for (int seed = 1; seed <= 3; seed++) {
                Specification spec = FreeCol.loadSpecification(
                    FreeColRules.getFreeColRulesFile("freecol"), null,
                    "model.difficulty.medium");
                spec.setFile(MapGeneratorOptions.IMPORT_FILE, null);
                MapGeneratorOptions.applyTopologyDefaults(
                    spec.getMapGeneratorOptions());
                Game game = new ServerGame(spec);
                game.setNationOptions(new NationOptions(spec));
                for (Nation n : spec.getNations()) {
                    if (n.isUnknownEnemy()) continue;
                    Player p = new ServerPlayer(game, false, n);
                    boolean ai = !n.getType().isEuropean() || n.getType().isREF();
                    p.setAI(ai);
                    if (ai || game.canAddNewPlayer()) game.addPlayer(p);
                }
                new SimpleMapGenerator(new Random(seed))
                    .generateMap(game, null, true, new LogBuilder(-1));
                final Map map = game.getMap();
                assertEquals(W, map.getWidth());
                assertEquals(H, map.getHeight());
                for (Player p : game.getLiveEuropeanPlayerList()) {
                    for (Unit u : p.getUnitSet()) {
                        if (!u.isNaval() || !u.hasTile()) continue;
                        final Tile t = u.getTile();
                        final ClassicMapViewer mv
                            = new ClassicMapViewer(null, null, null, false);
                        mv.changeToMoveUnits(u);
                        mv.setFocus(t);
                        final int[] v = mv.peekViewOrigin();
                        final String at = "seed " + seed + " " + p.getNationId()
                            + " at " + t.getX() + "," + t.getY()
                            + " view " + Arrays.toString(v);
                        assertEquals(at, 14, t.getX() - v[0]);
                        assertEquals(at, 6, t.getY() - v[1]);
                        ships++;
                    }
                }
            }
            assertTrue("ships " + ships, ships >= 12);
        } finally {
            Topology.setCurrent(saved);
        }
    }

    private static Rectangle ring(int c0, int r0) {
        final int[] rgb = new int[W * H];
        Arrays.fill(rgb, ClassicHud.UNEXPLORED);
        return ClassicHud.viewportRing(new ClassicHud.MinimapModel(W, H, rgb, c0, r0));
    }

    /**
     * The same replay through the map viewer: the start centres on the
     * ship, every selection goes through {@code changeToMoveUnits} (at a
     * turn's start after {@code changeToEndTurn}), every slide start
     * through the test {@code animateMove} makes on its source tile, and
     * the stored origin follows V1-V14 exactly.  The three units are
     * ships here, on an all-ocean map: only who is active matters.
     */
    public void testViewerReplaysLandfall() {
        final Game game = getStandardGame();
        final Map map = new MapBuilder(game).setDimensions(W, H)
            .setBaseTileType(spec().getTileType("model.tile.ocean"))
            .setExploredByAll(true).build();
        game.changeMap(map);
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final Unit[] units = new Unit[3];
        for (int i = 0; i < units.length; i++) {
            units[i] = new ServerUnit(game, map.getTile(56, 42), dutch,
                spec().getUnitType("model.unit.merchantman"));
        }
        final ClassicMapViewer mv = new ClassicMapViewer(null, null, null, false);
        assertNull(mv.peekViewOrigin());

        final List<Integer> jumps = new ArrayList<>();
        for (int[] e : EVENTS) {
            final Tile t = map.getTile(e[3], e[4]);
            final Unit u = (e[2] == NONE) ? null : units[e[2]];
            final int[] before = mv.peekViewOrigin();
            switch (e[1]) {
            case START:
                // ClassicGUI.reconnectGUI: activation, then the focus.
                mv.changeToMoveUnits(u);
                mv.setFocus(u.getTile());
                break;
            case TURN:
                mv.changeToEndTurn();
                u.setLocation(t);
                mv.changeToMoveUnits(u);
                break;
            case SELECT:
                u.setLocation(t);
                mv.changeToMoveUnits(u);
                break;
            case MOVE: case LUNGE:
                u.setLocation(t);
                // The controller's redisplay after the previous move,
                // the unit on its new tile: the arrival, never tested.
                if (mv.getActiveUnit() == u) mv.changeToMoveUnits(u);
                mv.jumpIfNeeded(t, "move");
                break;
            case NATIVE:
                // The same test on the brave's source tile (spec delta W4).
                mv.jumpIfNeeded(t, "foreign-move");
                break;
            default:
                fail();
            }
            final int[] after = mv.peekViewOrigin();
            assertTrue("view after #" + e[0] + ": " + Arrays.toString(after),
                       Arrays.equals(viewAt(e[0]), after));
            if (before != null && !Arrays.equals(before, after)) jumps.add(e[0]);
        }
        assertEquals(13, jumps.size());
        assertEquals(Arrays.asList(10269, 15010, 17712, 18602, 19506, 20813,
                                   21480, 23029, 23537, 24165, 24315, 25687,
                                   25754), jumps);

        // The focus is the view's cell (7,6): at the start (49,42), the
        // ship itself in (14,6) beside it.
        mv.setFocus(map.getTile(56, 42));
        assertTrue(Arrays.equals(new int[] { 42, 36 }, mv.viewOrigin()));
        // The origin is the viewer's own: a copy comes back.
        mv.viewOrigin()[0] = 0;
        assertTrue(Arrays.equals(new int[] { 42, 36 }, mv.peekViewOrigin()));

        // TERRAIN follows the same jumps: a cursor in the safe zone keeps
        // the view, one in the margin recentres it.
        mv.changeToTerrain(map.getTile(50, 40));
        assertTrue(Arrays.equals(new int[] { 42, 36 }, mv.peekViewOrigin()));
        mv.changeToTerrain(map.getTile(50, 47));
        assertTrue(Arrays.equals(new int[] { 42, 41 }, mv.peekViewOrigin()));
        // The end of the turn never moves the view.
        mv.changeToEndTurn();
        assertTrue(Arrays.equals(new int[] { 42, 41 }, mv.peekViewOrigin()));
        mv.dispose();
    }

    /**
     * Never on arrival (landfall #10077 -> #10269): the controller selects
     * the moving unit again after each of its moves, which must not test
     * the view; its next move does, on its source tile.  A unit that
     * becomes active (another unit, or the same one at the next turn's
     * start) is tested.
     */
    public void testArrivalNeverJumps() {
        final Game game = getStandardGame();
        final Map map = new MapBuilder(game).setDimensions(W, H)
            .setBaseTileType(spec().getTileType("model.tile.ocean"))
            .setExploredByAll(true).build();
        game.changeMap(map);
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final Unit ship = new ServerUnit(game, map.getTile(56, 42), dutch,
            spec().getUnitType("model.unit.merchantman"));
        final Unit other = new ServerUnit(game, map.getTile(56, 42), dutch,
            spec().getUnitType("model.unit.caravel"));
        final ClassicMapViewer mv = new ClassicMapViewer(null, null, null, false);
        final int[] v1 = { 42, 36 }, v2 = { 42, 40 };
        mv.changeToMoveUnits(ship);
        assertTrue(Arrays.equals(v1, mv.peekViewOrigin()));

        // (8,9) -> (7,10): the move from row 9 stays, the arrival in row 10
        // (the controller's redisplay) stays too.
        assertFalse(mv.jumpIfNeeded(map.getTile(50, 45), "move"));
        ship.setLocation(map.getTile(49, 46));
        mv.changeToMoveUnits(ship);
        assertTrue(Arrays.equals(v1, mv.peekViewOrigin()));
        // The next move starts in row 10: the jump, before the slide.
        assertTrue(mv.jumpIfNeeded(ship.getTile(), "move"));
        assertTrue(Arrays.equals(v2, mv.peekViewOrigin()));

        // Another unit becoming active in the margin: tested.
        other.setLocation(map.getTile(55, 51));          // (13,11) of V2
        mv.changeToMoveUnits(other);
        assertTrue(Arrays.equals(new int[] { 42, 45 }, mv.peekViewOrigin()));
        // The same unit at the next turn's start (END_TURN in between): tested.
        ship.setLocation(map.getTile(49, 56));           // (7,11) of (42,45)
        mv.changeToMoveUnits(ship);
        assertTrue(Arrays.equals(new int[] { 42, 50 }, mv.peekViewOrigin()));
        ship.setLocation(map.getTile(49, 61));           // arrival in row 11
        mv.changeToMoveUnits(ship);
        assertTrue(Arrays.equals(new int[] { 42, 50 }, mv.peekViewOrigin()));
        mv.changeToEndTurn();
        assertTrue(Arrays.equals(new int[] { 42, 50 }, mv.peekViewOrigin()));
        mv.changeToMoveUnits(ship);
        assertTrue(Arrays.equals(new int[] { 42, 55 }, mv.peekViewOrigin()));
        mv.dispose();
    }
}
