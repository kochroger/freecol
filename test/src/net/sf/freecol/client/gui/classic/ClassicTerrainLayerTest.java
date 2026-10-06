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
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import net.sf.freecol.util.test.FreeColTestCase;


/**
 * The terrain layer (M1c design 10 §6.2, W6a; Critic 7): whole cells
 * composed for any clip, drawn exactly (dst = src x S) through the game
 * palette at its phase, and the cycling cells.
 */
public class ClassicTerrainLayerTest extends FreeColTestCase {

    /** A palette whose 256 entries all differ (red = the index). */
    static ClassicGamePalette palette() {
        final int[] b = new int[256];
        for (int i = 0; i < 256; i++) b[i] = (i << 16) | ((255 - i) << 8) | (i ^ 0x5A);
        return new ClassicGamePalette(b, ClassicGamePalette.Cycle.DEFAULT);
    }

    /** A layer over the pattern sheets with the design's alias table. */
    static ClassicTerrainLayer layer() {
        return new ClassicTerrainLayer(new ClassicTerrainComposer(
            ClassicTerrainSheets.patternTerrain(), ClassicTerrainSheets.patternPhys(),
            id -> ClassicTerrainGoldenTest.ALIAS.getOrDefault(id, -1)), palette());
    }

    /** A 15x12 view: sea, a sea lane, land, explored and dark. */
    private static ClassicTerrainComposerTest.Grid view() {
        return new ClassicTerrainComposerTest.Grid(
            "ooooooooooooooo",
            "ooooooooooooooo",
            "oooooHHHooooooo",
            "ooooOOOHhoooooo",
            "oooOOOOOOoooooo",
            "ooooOPPGoooooog",
            "ooooppgggoooooo",
            "ooooooooooooooo",
            "ooooooooooooooo",
            "ooooooooooooooo",
            "ooooooooooooooo",
            "ooooooooooooooo");
    }

    /** The clip's whole cells, rounded out. */
    public void testCellsRoundOut() {
        assertTrue(java.util.Arrays.equals(new int[] { 0, 0, 15, 12 },
                                           ClassicTerrainLayer.cells(null, 3)));
        assertTrue(java.util.Arrays.equals(new int[] { 0, 0, 1, 1 },
            ClassicTerrainLayer.cells(new Rectangle(5, 7, 1, 1), 3)));
        assertTrue(java.util.Arrays.equals(new int[] { 0, 0, 2, 2 },
            ClassicTerrainLayer.cells(new Rectangle(47, 47, 2, 2), 3)));
        assertTrue(java.util.Arrays.equals(new int[] { 2, 1, 3, 2 },
            ClassicTerrainLayer.cells(new Rectangle(161, 80, 79, 79), 5)));
        // Clamped to the view, empty outside it.
        assertTrue(java.util.Arrays.equals(new int[] { 14, 11, 15, 12 },
            ClassicTerrainLayer.cells(new Rectangle(713, 560, 400, 400), 3)));
        assertNull(ClassicTerrainLayer.cells(new Rectangle(720, 0, 10, 10), 3));
        assertNull(ClassicTerrainLayer.cells(new Rectangle(5, 5, 0, 10), 3));
    }

    /**
     * Paint at S = 3 and 5 with odd clips: exactly the rounded-out cells
     * are drawn, every screen pixel the palette colour of its native
     * index, nothing outside them (Critic 7).
     */
    public void testPaintIsExact() {
        final ClassicTerrainComposerTest.Grid g = view();
        for (int s : new int[] { 3, 5 }) {
            for (Rectangle clip : new Rectangle[] { null, new Rectangle(7, 11, 50, 23),
                         new Rectangle(16 * s * 4 + 1, 16 * s * 3 - 2, 3, 3) }) {
                final ClassicTerrainLayer l = layer();
                final BufferedImage img = new BufferedImage(240 * s, 192 * s,
                                                            BufferedImage.TYPE_INT_RGB);
                final Graphics2D gr = img.createGraphics();
                gr.setColor(java.awt.Color.MAGENTA);
                gr.fillRect(0, 0, img.getWidth(), img.getHeight());
                gr.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                final int[] c = l.paint(gr, g, 0, 0, clip, s);
                gr.dispose();
                assertNotNull(c);
                int drawn = 0;
                for (int y = 0; y < img.getHeight(); y++) {
                    for (int x = 0; x < img.getWidth(); x++) {
                        final int col = x / (16 * s), row = y / (16 * s);
                        final boolean in = col >= c[0] && col < c[2]
                            && row >= c[1] && row < c[3];
                        final int want = (in) ? l.palette().rgb(0, l.index(x / s, y / s))
                            : 0xFF00FF;
                        assertEquals("S=" + s + " " + clip + " (" + x + "," + y + ")",
                                     want, img.getRGB(x, y) & 0xFFFFFF);
                        if (in) drawn++;
                    }
                }
                assertEquals((c[2] - c[0]) * (c[3] - c[1]) * 256 * s * s, drawn);
            }
        }
    }

    /**
     * The phase: a cycling index is drawn with the phase's colour; the
     * cycling cells are the sea lane, its blends and its fringes.
     */
    public void testPhaseAndCyclingCells() {
        final ClassicTerrainLayer l = layer();
        final ClassicTerrainComposerTest.Grid g = view();
        final BufferedImage img = new BufferedImage(240, 192, BufferedImage.TYPE_INT_RGB);
        Graphics2D gr = img.createGraphics();
        l.paint(gr, g, 0, 0, null, 1);
        gr.dispose();
        // The sea lane (5,2): a sparkle at offset 35 = (3,2), in no mask.
        final int x = 5 * 16 + 3, y = 2 * 16 + 2;
        final int v = l.index(x, y);
        assertTrue("a cycling index " + v, v >= 120 && v < 128);
        assertEquals(l.palette().rgb(0, v), img.getRGB(x, y) & 0xFFFFFF);
        l.setPhase(3);
        assertEquals(3, l.phase());
        gr = img.createGraphics();
        l.paint(gr, g, 0, 0, new Rectangle(x, y, 1, 1), 1);
        gr.dispose();
        assertEquals(l.palette().rgb(3, v), img.getRGB(x, y) & 0xFFFFFF);
        assertFalse(l.palette().rgb(0, v) == l.palette().rgb(3, v));
        l.setPhase(11);
        assertEquals(3, l.phase());
        // The cycling cells: the lane (5..7,2), (7,3); the ocean it blends
        // into; the dark tiles with its fringe.  None in the land rows.
        assertTrue(l.cycles(5, 2) && l.cycles(7, 3));
        assertTrue("dark (8,3) gets the lane's W fringe", l.cycles(8, 3));
        assertFalse(l.cycles(0, 0));
        assertFalse(l.cycles(6, 6));
        final int[] box = l.cyclingCells();
        assertNotNull(box);
        for (int r = 0; r < 12; r++) {
            for (int c = 0; c < 15; c++) {
                final boolean inBox = c >= box[0] && c < box[2] && r >= box[1] && r < box[3];
                if (l.cycles(c, r)) assertTrue("(" + c + "," + r + ") in " , inBox);
            }
        }
    }

    /** No pack, no layer: the RGBA fallback. */
    public void testNoPackNoLayer() {
        assertNull(ClassicTerrainLayer.create(null));
        assertNull(ClassicTerrainLayer.create(ClassicPackFiles.forDirectory(
            new java.io.File("no-such-pack"))));
    }
}
