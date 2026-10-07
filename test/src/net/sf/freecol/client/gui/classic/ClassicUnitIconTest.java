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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import net.sf.freecol.client.ClientOptions;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.tools.classicassets.ClassicAssetDecoderTest;
import net.sf.freecol.tools.classicassets.FfDecoder;
import net.sf.freecol.util.test.FreeColTestCase;

import junit.framework.TestCase;


/**
 * Tests of the map's unit icon (build spec W2, spec delta W2.3): the 1:1
 * icon -- shadow, flag, second flag, sprite -- against the pixel classes of
 * the original's frames, the settlement overhang, which foreign moves the
 * classic prefs show, and the session option the slide needs.
 *
 * <p>The ship's mask is ICONS.SS 006 (the merchantman, 13x16), copied here
 * so the test does not need the generated pack.  Classes: B = black
 * (index 0 or 250), F = the Dutch flag fill (index 13, #FF7100), . = any
 * other colour (sprite or water).
 */
public class ClassicUnitIconTest extends TestCase {

    /** ICONS.SS 006 (merchantman), # = opaque; its one black pixel is k. */
    private static final String[] SHIP = {
        ".....###.....",
        ".....#..#....",
        "....###......",
        "...#######...",
        "...######.#..",
        "...#######...",
        "..#########..",
        "..#########..",
        "..#######....",
        "..######k##..",
        "...#.#.#####.",
        "...#.########",
        "#############",
        "..###########",
        "..##########.",
        "...########..",
    };

    /**
     * The empty ship on sea, clip007 #3697 at cell (8,3), without the
     * flag's '-' (FONTTINY is not loaded here; row 4 is "BFBBBF" there).
     */
    private static final String[] EMPTY = {
        "BBBBBBBB........",
        "BFFFFFB..B......",
        "BFFFFFB.........",
        "BFFFFF..........",
        "BFFFFF..........",
        "BFFFFF..........",
        "BFFFF...........",
        "BFFFF...........",
        "BBBBB...........",
        "...BB......B....",
        "....B....B......",
        "....B..B........",
        ".BB.............",
        "...BB...........",
        "...BB...........",
        "....BB..........",
    };

    /** The laden ship: landfall #343 (14,6) and clip007 #2374; rows 9-10 differ. */
    private static final String[] LADEN = {
        "BBBBBBBB........",
        "BFFFFFB..B......",
        "BFFFFFB.........",
        "BFFFFF..........",
        "BFFFFF..........",
        "BFFFFF..........",
        "BFFFF...........",
        "BFFFF...........",
        "BBBBB...........",
        "..BFF......B....",
        "..BBBB.B.B......",
        "....B..B........",
        ".BB.............",
        "...BB...........",
        "...BB...........",
        "....BB..........",
    };

    private static final int DUTCH = 0xFF7100, DUTCH_DARK = 0xAA4900;

    /** NAMES.TXT @UNIT row of the merchantman (ICONS.SS 006). */
    private static final int MERCHANTMAN = 14;

    private static final int SEA = 0x2C3C96;

    private static BufferedImage sprite(String[] mask) {
        final BufferedImage img = new BufferedImage(mask[0].length(), mask.length,
                                                    BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < mask.length; y++) {
            for (int x = 0; x < mask[y].length(); x++) {
                final char c = mask[y].charAt(x);
                if (c == '#') img.setRGB(x, y, 0xFFDBDBDB);
                else if (c == 'k') img.setRGB(x, y, 0xFF000000);
            }
        }
        return img;
    }

    /** Paint one icon at native size on sea and classify its pixels. */
    private static String[] icon(BufferedImage sp, int[] marker) {
        final BufferedImage out = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = out.createGraphics();
        try {
            g.setColor(new Color(SEA));
            g.fillRect(0, 0, 16, 16);
            ClassicHud.paintIcon(g, null, "-", sp, DUTCH,
                ClassicHud.letterInk(ClassicHud.ORDERS_NONE, DUTCH_DARK),
                MERCHANTMAN, 0, 0, marker);
        } finally {
            g.dispose();
        }
        final String[] rows = new String[16];
        for (int y = 0; y < 16; y++) {
            final StringBuilder sb = new StringBuilder(16);
            for (int x = 0; x < 16; x++) {
                final int c = out.getRGB(x, y) & 0xFFFFFF;
                sb.append((c == 0) ? 'B' : (c == DUTCH) ? 'F' : '.');
            }
            rows[y] = sb.toString();
        }
        return rows;
    }

    private static void assertRows(String[] want, String[] got) {
        for (int y = 0; y < want.length; y++) {
            assertEquals("row " + y, want[y], got[y]);
        }
    }

    /** The empty ship: shadow 2 px left, flag at the cell's top-left, sprite on top. */
    public void testEmptyShipIsTheOriginals() {
        assertRows(EMPTY, icon(sprite(SHIP), ClassicHud.NO_MARKER));
    }

    /** A passenger aboard: the cargo marker's edge, 7 px against the empty ship. */
    public void testLadenShipIsTheOriginals() {
        final String[] laden = icon(sprite(SHIP), ClassicHud.CARGO_MARKER);
        assertRows(LADEN, laden);
        final String[] empty = icon(sprite(SHIP), ClassicHud.NO_MARKER);
        final List<String> diff = new ArrayList<>();
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                if (laden[y].charAt(x) != empty[y].charAt(x)) diff.add(x + "," + y);
            }
        }
        // Spec delta W2.3: x 2-6, y 8-10 of the cell; 7 px.
        assertEquals("[2,9, 3,9, 4,9, 2,10, 3,10, 5,10, 7,10]", diff.toString());
    }

    /**
     * A land unit's flag at the sprite's lower right (landfall #19187, the
     * pioneer at (12,4): ring x 8-14, y 7-15), and the stack marker 2 px up
     * and left of it (I).
     */
    public void testLandFlagAndStackMarker() {
        final BufferedImage pioneer = sprite(new String[] {
            ".......", ".......", "..###..", ".####..", "#.##...",
            "..####.", ".######", "#######", "######.", "######.",
            "#####..", ".####..", "..##...", "..##...", "..##...",
            ".####..",
        });
        final String[] p = icon(pioneer, ClassicHud.NO_MARKER);
        assertEquals("ring top right", 'B', p[7].charAt(14));
        assertEquals("ring bottom", "BBBBBBB", p[15].substring(8, 15));
        for (int y = 8; y <= 14; y++) {
            assertEquals("ring right " + y, 'B', p[y].charAt(14));
            assertEquals("fill " + y, "FFFF", p[y].substring(10, 14));
        }
        assertEquals("no flag above", '.', p[6].charAt(12));
        final String[] s = icon(pioneer, ClassicHud.STACK_MARKER);
        assertEquals("BBBB", s[5].substring(9, 13));
        assertEquals("FFB", s[6].substring(10, 13));
        // The flag itself is unchanged.
        for (int y = 7; y < 16; y++) {
            assertEquals("row " + y, p[y].substring(8), s[y].substring(8));
        }
    }

    /**
     * Build spec W21a / W21 item 7 (clip006 deep 4.2): the flag ring per
     * sprite -- galleon (ICONS.SS 007, 14 wide) and frigate (015, 13 wide)
     * at cell + (9,0), the merchantman (006, 13 wide) and the 14-wide
     * dragoon at the top-left as before -- and the frigate's sprite at +2.
     */
    public void testFlagSidePerSprite() {
        assertEquals(new Rectangle(217, 104, 7, 9),
                     ClassicHud.flagRing(208, 104, 14, ClassicHud.UNIT_GALLEON));   // c5 #20440
        assertEquals(new Rectangle(251, 68, 7, 9),
                     ClassicHud.flagRing(242, 68, 13, ClassicHud.UNIT_FRIGATE));
        assertEquals(new Rectangle(242, 68, 7, 9),
                     ClassicHud.flagRing(242, 68, 13, MERCHANTMAN));                  // 032 ship
        assertEquals(new Rectangle(242, 149, 7, 9),
                     ClassicHud.flagRing(242, 149, 14, 4));                            // 000 dragoon
        assertEquals(new Rectangle(250, 117, 7, 9),
                     ClassicHud.flagRing(242, 110, 8, 1));                             // 032 soldier
        assertEquals(2, ClassicHud.spriteOffset(13, ClassicHud.UNIT_FRIGATE));
        assertEquals(3, ClassicHud.spriteOffset(13, MERCHANTMAN));
        assertEquals(2, ClassicHud.spriteOffset(14, ClassicHud.UNIT_GALLEON));
        assertEquals(3, ClassicHud.spriteOffset(7, -1));
        assertEquals(15, ClassicHud.unitRow("galleon", null));
        assertEquals(17, ClassicHud.unitRow("frigate", null));

        // Painted: the galleon's flag fill at cell x 10-14 (c5 #20440: fill
        // x 218-222 for cell 208), nothing of it at the top-left.
        final BufferedImage galleon = sprite(new String[] {
            "..............", "..............", "....##..##....", "...###.####...",
            "..####.#####..", ".#####.######.", "##############", "##############",
            ".############.", "..##########..", "..............", "..............",
            "..............", "..............", "..............", ".............."
        });
        final String[] g = iconOf(galleon, ClassicHud.UNIT_GALLEON, null, 0);
        assertEquals("BBBBBBB", g[0].substring(9, 16));
        assertEquals("BFFFFFB", g[1].substring(9, 16));
        assertEquals(".........", g[0].substring(0, 9));
        assertEquals(".........", g[1].substring(0, 9));
        // The merchantman's layout for the same sprite: the top-left.
        final String[] m = iconOf(galleon, MERCHANTMAN, null, 0);
        assertEquals("BBBBBBB", m[0].substring(0, 7));
        assertEquals("BFFFFFB", m[1].substring(0, 7));
    }

    /**
     * The natives' and the unequipped experts' places (H4, R1): the brave
     * (ICONS.SS 109, 8 wide, row 19) at +2 with its flag at the lower right
     * (landfall #14022: sprite x 146 in cell 144, the flag's fill pixel
     * (154,65) #698AC3); the armed brave (110, 9 wide) centred, its flag at
     * the lower right; the mounted ones (111, 112, 14 wide) at +2 with the
     * flag at the top left like the dragoon 076 (I, never in a clip); the
     * experts without equipment (058-061, 6 wide, row 0) at +3 (clip005
     * #709: 059 at x 115 in cell 112) with the settler's flag.
     */
    public void testNativesAndUnequippedExperts() {
        assertEquals(19, ClassicHud.unitRow("brave", null));
        assertEquals(20, ClassicHud.unitRow("brave", "armedBrave"));
        assertEquals(21, ClassicHud.unitRow("brave", "mountedBrave"));
        assertEquals(22, ClassicHud.unitRow("brave", "nativeDragoon"));
        assertEquals(2, ClassicHud.spriteOffset(8, 19));
        final Rectangle brave = ClassicHud.flagRing(144, 56, 8, 19);
        assertEquals(new Rectangle(152, 63, 7, 9), brave);
        assertTrue(new Rectangle(brave.x + 1, brave.y + 1, 5, 7).contains(154, 65));
        assertEquals(3, ClassicHud.spriteOffset(9, 20));
        assertEquals(new Rectangle(154, 63, 7, 9), ClassicHud.flagRing(144, 56, 9, 20));
        assertEquals(new Rectangle(144, 56, 7, 9), ClassicHud.flagRing(144, 56, 14, 21));
        assertEquals(new Rectangle(144, 56, 7, 9), ClassicHud.flagRing(144, 56, 14, 22));
        assertEquals(0, ClassicHud.unitRow("veteranSoldier", null));
        assertEquals(3, ClassicHud.spriteOffset(6, 0));
        assertEquals(new Rectangle(119, 143, 7, 9), ClassicHud.flagRing(112, 136, 6, 0));
    }

    /**
     * The letter after the sprite, and its ink (build spec W21a; clip006
     * deep 4.2): the galleon's pixel (9,4) under the '-' shows the black
     * letter (map #4471, Europe #8120); the darker nation shade only for
     * Befestigt and Wache, black for '-', G, R, P and F while fortifying.
     */
    public void testLetterAfterTheSpriteAndItsInk() {
        assertEquals(0x000000, ClassicHud.letterInk(ClassicHud.ORDERS_NONE, DUTCH_DARK));
        assertEquals(0x000000, ClassicHud.letterInk(ClassicHud.ORDERS_GOTO, DUTCH_DARK));
        assertEquals(0x000000, ClassicHud.letterInk(ClassicHud.ORDERS_ROAD, DUTCH_DARK));
        assertEquals(0x000000, ClassicHud.letterInk(ClassicHud.ORDERS_PLOW, DUTCH_DARK));
        assertEquals(0x000000, ClassicHud.letterInk(ClassicHud.ORDERS_FORTIFY, DUTCH_DARK));
        assertEquals(0x000000, ClassicHud.letterInk(ClassicHud.ORDERS_TRADE, DUTCH_DARK));
        assertEquals(DUTCH_DARK, ClassicHud.letterInk(ClassicHud.ORDERS_FORTIFIED, DUTCH_DARK));
        assertEquals(DUTCH_DARK, ClassicHud.letterInk(ClassicHud.ORDERS_SENTRY, DUTCH_DARK));

        // A 14-wide sprite, opaque everywhere: the '-' of a synthetic
        // FONTTINY (3 wide, ink in its row 2) at ring + (2,2) = (11..13, 4)
        // lies over the sprite and must show.
        final String[] full = new String[16];
        java.util.Arrays.fill(full, "##############");
        final ClassicFont font = dashFont();
        final String[] dash = iconOf(sprite(full), ClassicHud.UNIT_GALLEON, font, 0x000000);
        assertEquals("BBB", dash[4].substring(11, 14));
        assertEquals('.', dash[3].charAt(12));
        // A dark letter (Befestigt) in the dark shade, also over the sprite.
        final String[] dark = iconOf(sprite(full), ClassicHud.UNIT_GALLEON, font,
            ClassicHud.letterInk(ClassicHud.ORDERS_FORTIFIED, DUTCH_DARK));
        assertEquals("DDD", dark[4].substring(11, 14));
    }

    /** A FONTTINY stand-in: '-' 3 wide, height 5, ink in row 2. */
    private static ClassicFont dashFont() {
        final FfDecoder.Font f = FfDecoder.decodePart(ClassicAssetDecoderTest.ffPart(5, 3,
            new int[][] { { 45, 3,  0, 0, 0,  0, 0, 0,  1, 1, 1,  0, 0, 0,  0, 0, 0 } }));
        return ClassicFont.fromAtlas(FfDecoder.toAtlas(f), FfDecoder.metrics(f));
    }

    /**
     * Paint one icon at native size on sea with the letter '-' in
     * {@code ink}; B black, F the Dutch fill, D the Dutch dark shade, .
     * anything else.
     */
    private static String[] iconOf(BufferedImage sp, int unitRow, ClassicFont font,
                                   int ink) {
        final BufferedImage out = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = out.createGraphics();
        try {
            g.setColor(new Color(SEA));
            g.fillRect(0, 0, 16, 16);
            ClassicHud.paintIcon(g, font, "-", sp, DUTCH, ink, unitRow, 0, 0,
                                 ClassicHud.NO_MARKER);
        } finally {
            g.dispose();
        }
        final String[] rows = new String[16];
        for (int y = 0; y < 16; y++) {
            final StringBuilder sb = new StringBuilder(16);
            for (int x = 0; x < 16; x++) {
                final int c = out.getRGB(x, y) & 0xFFFFFF;
                sb.append((c == 0) ? 'B' : (c == DUTCH) ? 'F'
                          : (c == DUTCH_DARK) ? 'D' : '.');
            }
            rows[y] = sb.toString();
        }
        return rows;
    }

    /** Settlements: centred, the 21-px village 2 px over the left edge (#13302). */
    public void testSettlementOffset() {
        assertEquals(-2, ClassicHud.settlementOffset(21));
        assertEquals(0, ClassicHud.settlementOffset(16));
        assertEquals(1, ClassicHud.settlementOffset(14));
    }

    /** The flag letter: '-' without the pack's texts. */
    public void testOrderLetterWithoutTexts() {
        assertEquals("-", ClassicHud.orderLetter(null, ClassicHud.ORDERS_NONE));
        assertEquals("-", ClassicHud.orderLetter(null, ClassicHud.ORDERS_SENTRY));
    }

    /**
     * Which classic pref shows a unit's moves: none for one's own, the
     * natives' row for a native unit, the Europeans' row for the others.
     */
    public void testMovesPref() {
        final Game game = FreeColTestCase.getStandardGame();
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final Player french = game.getPlayerByNationId("model.nation.french");
        final Player inca = game.getPlayerByNationId("model.nation.inca");
        assertNotNull(dutch);
        assertNotNull(french);
        assertNotNull(inca);
        final Unit own = unit(game, "unit:9001", dutch);
        final Unit fr = unit(game, "unit:9002", french);
        final Unit native1 = unit(game, "unit:9003", inca);
        assertNull(ClassicGUI.movesPref(own, dutch));
        assertEquals(ClassicPrefs.SHOW_EUROPEAN_MOVES, ClassicGUI.movesPref(fr, dutch));
        assertEquals(ClassicPrefs.SHOW_NATIVE_MOVES, ClassicGUI.movesPref(native1, dutch));
        assertNull(ClassicGUI.movesPref(null, dutch));
        // The prefs are read live: the stored value decides.
        final ClassicPrefs prefs = new ClassicPrefs(null);
        assertTrue(prefs.is(ClassicGUI.movesPref(native1, dutch)));
        prefs.set(ClassicPrefs.SHOW_NATIVE_MOVES, false);
        assertFalse(prefs.is(ClassicGUI.movesPref(native1, dutch)));
        assertTrue(prefs.is(ClassicGUI.movesPref(fr, dutch)));
    }

    /** A bare unit of {@code owner} (only the owner matters here). */
    private static Unit unit(Game game, String id, Player owner) {
        final Unit u = new Unit(game, id);
        u.setOwner(owner);
        return u;
    }

    /**
     * FreeCol's 300-ms sleep after a unit's last move (W2) and its
     * {@code autoEndTurn}, which ends at once (W5a: the Classic UI ends
     * the turn itself, 485 ms after the last change), are off while the
     * classic map is up.
     */
    public void testSessionOptions() {
        assertEquals(Boolean.FALSE,
            ClassicGUI.SESSION_OPTIONS.get(ClientOptions.UNIT_LAST_MOVE_DELAY));
        assertEquals(Boolean.FALSE,
            ClassicGUI.SESSION_OPTIONS.get(ClientOptions.AUTO_END_TURN));
        assertEquals(2, ClassicGUI.SESSION_OPTIONS.size());
    }

    /**
     * W5f: a unit whose order FreeCol completed at the turn start keeps
     * its letter until its visit, on the map and in the panel (the unit
     * facts): the black F of a fortification, then the dark one (c6 #4555
     * -&gt; #4556: palette index 0 -&gt; 5, #AA4900); the R of a road,
     * then '-' (c6 #3447 -&gt; #3450).
     */
    public void testHeldLetterUntilTheVisit() {
        final Game game = FreeColTestCase.getStandardGame();
        final net.sf.freecol.common.model.Map map = FreeColTestCase.getTestMap(
            FreeColTestCase.spec().getTileType("model.tile.plains"), true);
        game.changeMap(map);
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final net.sf.freecol.common.model.UnitType colonist
            = FreeColTestCase.spec().getUnitType("model.unit.freeColonist");
        final Unit soldier = new net.sf.freecol.server.model.ServerUnit(game,
            map.getTile(4, 4), dutch, colonist);
        final Unit pioneer = new net.sf.freecol.server.model.ServerUnit(game,
            map.getTile(6, 6), dutch, colonist);
        final net.sf.freecol.common.model.TileImprovement road
            = map.getTile(6, 6).addRoad();
        pioneer.setWorkImprovement(road);
        pioneer.setState(Unit.UnitState.IMPROVING);
        soldier.setState(Unit.UnitState.FORTIFYING);
        final BufferedImage sp = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        final ClassicUnitCycle cycle = new ClassicUnitCycle();
        ClassicUnitCycle.show(cycle);
        try {
            cycle.snapshot(dutch);
            // FreeCol's turn start.
            soldier.setState(Unit.UnitState.FORTIFIED);
            road.setTurnsToComplete(0);
            pioneer.setState(Unit.UnitState.ACTIVE);
            pioneer.setMovesLeft(0);
            cycle.turnStarted(dutch);
            assertEquals(ClassicHud.ORDERS_FORTIFY, ClassicUnitCycle.ordersRowShown(soldier));
            assertEquals(ClassicHud.ORDERS_FORTIFY,
                         ClassicHud.UnitFacts.of(soldier, sp).ordersRow);
            assertEquals(0x000000, ClassicHud.letterInk(
                ClassicUnitCycle.ordersRowShown(soldier), DUTCH_DARK));
            assertEquals(ClassicHud.ORDERS_ROAD, ClassicHud.UnitFacts.of(pioneer, sp).ordersRow);
            assertFalse(ClassicHud.UnitFacts.of(pioneer, sp).road);   // no road yet
            if (t != null) {
                assertEquals("R", ClassicHud.orderLetter(t, ClassicUnitCycle.ordersRowShown(pioneer)));
            }
            cycle.visited(soldier);
            assertEquals(ClassicHud.ORDERS_FORTIFIED,
                         ClassicHud.UnitFacts.of(soldier, sp).ordersRow);
            assertEquals(DUTCH_DARK, ClassicHud.letterInk(
                ClassicUnitCycle.ordersRowShown(soldier), DUTCH_DARK));
            cycle.visited(pioneer);
            assertEquals(ClassicHud.ORDERS_NONE, ClassicHud.UnitFacts.of(pioneer, sp).ordersRow);
            assertTrue(ClassicHud.UnitFacts.of(pioneer, sp).road);    // "(Straße)" now
            assertEquals("-", ClassicHud.orderLetter(t, ClassicUnitCycle.ordersRowShown(pioneer)));
        } finally {
            ClassicUnitCycle.show(null);
        }
    }
}
