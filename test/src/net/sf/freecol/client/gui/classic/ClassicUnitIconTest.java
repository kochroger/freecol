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
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import net.sf.freecol.client.ClientOptions;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Unit;
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
            ClassicHud.paintIcon(g, null, "-", sp, DUTCH, DUTCH_DARK, 0, 0, marker);
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
}
