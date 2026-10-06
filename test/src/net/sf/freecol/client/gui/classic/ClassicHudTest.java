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
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.util.test.FreeColTestCase;

import junit.framework.TestCase;


/**
 * Tests of the right panel's scene-mode logic ({@link ClassicHud}), the
 * strip's band position ({@link ClassicMenuBar}) and the scene layer's
 * canvas ({@link ClassicHudOverlay}).  Expected values are measured in the
 * native captures (083/049/032/052/000); text files are SYNTHETIC.
 */
public class ClassicHudTest extends TestCase {

    /** The original's 58x72 map. */
    private static final int W = 58, H = 72;

    /** 083: ship at column 53 -> view c0 42, ring (293,22). */
    public void testViewAndRingOf083() {
        final int[] v = ClassicHud.viewFor(W, H, 53, 26);
        assertEquals(42, v[0]);
        assertEquals(20, v[1]);
        final ClassicHud.MinimapModel m = model(v[0], v[1]);
        assertEquals(new Rectangle(293, 22, 15, 12), ClassicHud.viewportRing(m));
        assertEquals(1, ClassicHud.minimapOriginX(W, 42));
        assertEquals(7, ClassicHud.minimapOriginY(H, 20));
    }

    /**
     * The view is clamped to columns/rows 1 .. size-2; only a tile on the
     * outer ring itself moves it just far enough to show that tile.
     */
    public void testViewClamped() {
        assertTrue(Arrays.equals(new int[] { 1, 1 }, ClassicHud.viewFor(W, H, 2, 2)));
        assertTrue(Arrays.equals(new int[] { 42, 59 }, ClassicHud.viewFor(W, H, 56, 70)));
        assertTrue(Arrays.equals(new int[] { 43, 60 }, ClassicHud.viewFor(W, H, 57, 71)));
        assertTrue(Arrays.equals(new int[] { 20, 30 }, ClassicHud.viewFor(W, H, 27, 36)));
    }

    /** The minimap scrolls vertically, the ring at row 13 unless clamped. */
    public void testMinimapScroll() {
        assertEquals(1, ClassicHud.minimapOriginY(H, 5));     // top clamp
        assertEquals(32, ClassicHud.minimapOriginY(H, 59));   // bottom clamp (72-40)
        assertEquals(22, ClassicHud.viewportRing(model(42, 20)).y);
        assertEquals(9 + 59 - 32, ClassicHud.viewportRing(model(42, 59)).y);
        // A map no taller than the window never scrolls.
        assertEquals(1, ClassicHud.minimapOriginY(30, 10));
        // A wider FreeCol map scrolls horizontally around the ring (assumed).
        assertEquals(29, ClassicHud.minimapOriginX(100, 50));
        assertEquals(1, ClassicHud.minimapOriginX(100, 3));
        assertEquals(43, ClassicHud.minimapOriginX(100, 90));
    }

    /** Painting: frame, explored pixels, ship colour, ring, black elsewhere. */
    public void testPaintMinimap() {
        final int[] rgb = new int[W * H];
        Arrays.fill(rgb, ClassicHud.UNEXPLORED);
        for (int y = 25; y <= 27; y++) {
            for (int x = 52; x <= 54; x++) rgb[y * W + x] = ClassicHud.OCEAN_RGB;
        }
        rgb[26 * W + 53] = 0xFF7100;
        final ClassicHud.MinimapModel m = new ClassicHud.MinimapModel(W, H, rgb, 42, 20);
        final BufferedImage img = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = img.createGraphics();
        ClassicHud.paintChrome(g, null);
        ClassicHud.paintMinimap(g, m);
        g.dispose();
        assertEquals(0xFF7100, rgb(img, 304, 28));
        assertEquals(ClassicHud.OCEAN_RGB, rgb(img, 303, 27));
        assertEquals(ClassicHud.OCEAN_RGB, rgb(img, 305, 29));
        assertEquals(0x000000, rgb(img, 300, 30));
        assertEquals(0xFFFFFF, rgb(img, 293, 22));
        assertEquals(0xFFFFFF, rgb(img, 307, 33));
        assertEquals(ClassicHud.FRAME_RGB, rgb(img, 251, 8));
        assertEquals(ClassicHud.FRAME_RGB, rgb(img, 308, 48));
        assertEquals(0x000000, rgb(img, 240, 100));
        assertEquals(0x000000, rgb(img, 280, 49));
    }

    /** Status lines from the pack's labels, spacing as measured. */
    public void testStatusLines() throws IOException {
        final File dir = Files.createTempDirectory("classic-hud").toFile();
        try {
            final File g = new File(dir, "GAME.TXT"), n = new File(dir, "NAMES.TXT"),
                l = new File(dir, "LABELS.TXT");
            Files.write(g.toPath(), "@X\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
            Files.write(n.toPath(), "@SEASONS\r\nSeasonA\r\nSeasonB\r\n\r\n"
                .getBytes(StandardCharsets.ISO_8859_1));
            Files.write(l.toPath(), ("@MISC\r\nm0\r\n\r\n@CTITLE\r\nPop:\r\nCoin:\r\nc2\r\nc3\r\n"
                + "c4\r\nc5\r\nc6\r\nc7\r\nc8\r\nLevy:\r\n\r\n@CMESSAGE\r\nx\r\n")
                .getBytes(StandardCharsets.ISO_8859_1));
            final ClassicText t = ClassicText.fromFiles(g, n, l);
            assertEquals("Coin:", t.label("CTITLE", ClassicHud.CTITLE_GOLD));
            assertEquals("Levy:", t.label("CTITLE", ClassicHud.CTITLE_TAX));
            assertNull(t.label("CTITLE", 10));
            assertNull(t.label("NOPE", 0));
            assertEquals("m0", t.misc(0));
            assertEquals("Coin:1000$  Levy: 0", ClassicHud.goldLine(t, 1000, 0));
            assertEquals("SeasonA 1492", ClassicHud.seasonLine(t, -1, 1492));
            assertEquals("SeasonA 1600", ClassicHud.seasonLine(t, 0, 1600));
            assertEquals("SeasonB 1600", ClassicHud.seasonLine(t, 1, 1600));
            assertNull(ClassicHud.goldLine(null, 1, 1));
            for (String s : new String[] { "GAME.TXT", "NAMES.TXT", "LABELS.TXT" }) {
                new File(dir, s).delete();
            }
        } finally {
            dir.delete();
        }
    }

    /** The scene layer's canvas: largest whole scale, centred. */
    public void testOverlayCanvas() {
        assertEquals(5, ClassicHudOverlay.scale(1920, 1080));
        assertEquals(new Rectangle(160, 40, 1600, 1000), ClassicHudOverlay.canvas(1920, 1080));
        assertEquals(2, ClassicHudOverlay.scale(800, 600));
        assertEquals(new Rectangle(80, 100, 640, 400), ClassicHudOverlay.canvas(800, 600));
        assertEquals(1, ClassicHudOverlay.scale(200, 100));
    }

    /**
     * The layer: letterbox black, the map area transparent over the live
     * map and black where no map lies under it.
     */
    public void testOverlayPaint() {
        final BufferedImage scene = new BufferedImage(320, 200, BufferedImage.TYPE_INT_ARGB);
        final BufferedImage out = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = out.createGraphics();
        g.setColor(new java.awt.Color(0x123456));
        g.fillRect(0, 0, 800, 600);
        // The live map covers only the left half of the canvas's map area.
        ClassicHudOverlay.paintOverlay(g, 800, 600, scene, new Rectangle(0, 0, 400, 600));
        g.dispose();
        assertEquals(0x000000, rgb(out, 10, 10));       // letterbox
        assertEquals(0x123456, rgb(out, 200, 300));     // map over the live map
        assertEquals(0x000000, rgb(out, 500, 300));     // map, nothing under it
    }

    /** The strip: wood (here the flat fallback) rows 0..6, black row 7. */
    public void testStripChrome() {
        final BufferedImage img = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = img.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, 320, 200);
        ClassicMenuBar.paintBand(g, null, null, null);
        g.dispose();
        final int fill = ClassicMenuBox.GAME.fallbackFill.getRGB() & 0xFFFFFF;
        for (int x = 0; x < 320; x += 17) {
            assertEquals(fill, rgb(img, x, 0));
            assertEquals(fill, rgb(img, x, 6));
            assertEquals(0x000000, rgb(img, x, 7));
            assertEquals(0xFFFFFF, rgb(img, x, 8));
        }
    }

    // The unit block (032/052/000/007), on synthetic texts.

    /** Synthetic NAMES/LABELS with the sections the unit block reads. */
    private static ClassicText unitTexts(File dir) throws IOException {
        final StringBuilder n = new StringBuilder("@NATIONALITY\r\nN0\r\nN1\r\nN2\r\nNat3\r\n\r\n");
        n.append("@UNIT\r\n");
        for (int i = 0; i < 23; i++) n.append("U").append(i).append(", 1, 0\r\n");
        n.append("\r\n@JOB\r\n");
        for (int i = 0; i < 28; i++) n.append("J").append(i).append(", JJ").append(i).append("\r\n");
        n.append("\r\n@ORDERS\r\nO0, -\r\nO1, S\r\nO2, T\r\nO3, G\r\nO4, L\r\nO5, F\r\n"
                 + "O6, F\r\nO7, B\r\nO8, P\r\nO9, R\r\n\r\n@CARGO\r\n");
        for (int i = 0; i < 16; i++) n.append(i == 14 ? "Tools" : "C" + i).append(", 1\r\n");
        n.append("\r\n@UNFORESTED\r\n");
        for (int i = 0; i < 8; i++) n.append("T").append(i).append(", 1\r\n");
        n.append("\r\n@FORESTED\r\n");
        for (int i = 0; i < 8; i++) n.append("F").append(i).append("-, 1\r\n");
        n.append("\r\n@OTHER\r\nArc, 1\r\nOce, 1\r\nSea, 1\r\nMnt, 1\r\nHil, 1\r\n\r\n"
                 + "@OTHER_NAMES\r\nWoods\r\nRiv\r\nBigRiv\r\n\r\n");
        final StringBuilder l = new StringBuilder("@INFO\r\nMv:\r\nAt:\r\n\r\n@MISC\r\n");
        for (int i = 0; i < 70; i++) {
            l.append(i == 4 ? "Exp" : i == 31 ? "Path" : i == 64 ? "Vet" : "m" + i).append("\r\n");
            if (i == 36) l.append("\r\n");     // a blank line inside, as in the file
        }
        final File g = new File(dir, "GAME.TXT"), nf = new File(dir, "NAMES.TXT"),
            lf = new File(dir, "LABELS.TXT");
        Files.write(g.toPath(), "@X\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
        Files.write(nf.toPath(), n.toString().getBytes(StandardCharsets.ISO_8859_1));
        Files.write(lf.toPath(), l.toString().getBytes(StandardCharsets.ISO_8859_1));
        try {
            return ClassicText.fromFiles(g, nf, lf);
        } finally {
            g.delete();
            nf.delete();
            lf.delete();
        }
    }

    private static ClassicHud.UnitFacts facts(int w, String type, String role, int orders,
                                              int tools, String terrain, boolean road,
                                              int moves, int x, int y) {
        final BufferedImage sprite = new BufferedImage(w, 16, BufferedImage.TYPE_INT_ARGB);
        final boolean person = !type.equals("merchantman") && !type.equals("caravel");
        return new ClassicHud.UnitFacts(sprite, 0xFF7100, 0xAA4900, 3,
            ClassicHud.unitRow(type, role), type, moves, x, y, orders, terrain, road,
            person ? ClassicHud.jobRow(type) : -1, person && role == null,
            ClassicHud.qualifier(type, role), tools);
    }

    private static String lines(List<ClassicHud.TextLine> ls) {
        return ls.toString();
    }

    /** 032: the ship's lines, then the list at 110 and 128 (veteran/orders, count/tools/orders). */
    public void testUnitBlock032() throws IOException {
        final File dir = Files.createTempDirectory("classic-hud-u").toFile();
        try {
            final ClassicText t = unitTexts(dir);
            final ClassicHud.UnitFacts ship = facts(13, "merchantman", null,
                ClassicHud.ORDERS_NONE, -1, "model.tile.highSeas", false, 15, 56, 28);
            final List<ClassicHud.TextLine> a = ClassicHud.activeLines(t, ship);
            assertEquals("[(260,70) Mv: 5, (260,77) At: (56, 28), (242,86) Nat3 U14, "
                + "(242,93,gold) O0, (242,100) (Sea)]", lines(a));
            final int y0 = 100 + ClassicHud.LIST_GAP;
            assertEquals(110, y0);
            final ClassicHud.UnitFacts vet = facts(8, "veteranSoldier", "soldier",
                ClassicHud.ORDERS_SENTRY, -1, null, false, 3, 0, 0);
            final List<ClassicHud.TextLine> l1 = ClassicHud.listLines(t, vet, y0);
            assertEquals("[(260,114,gold) Vet, (260,120,gold) O1]", lines(l1));
            final int y1 = ClassicHud.nextListY(y0, l1);
            assertEquals(128, y1);
            final ClassicHud.UnitFacts pio = facts(7, "freeColonist", "pioneer",
                ClassicHud.ORDERS_SENTRY, 100, null, false, 3, 0, 0);
            final List<ClassicHud.TextLine> l2 = ClassicHud.listLines(t, pio, y1);
            assertEquals("[(260,132,gold) 100, (260,139,gold) Tools, (260,145,gold) O1]",
                         lines(l2));
            assertEquals(153, ClassicHud.nextListY(y1, l2));
        } finally {
            dir.delete();
        }
    }

    /** 000/007: settler on a forest road; hardy pioneer as expert with 100 tools; a lone orders line. */
    public void testUnitBlock000() throws IOException {
        final File dir = Files.createTempDirectory("classic-hud-u").toFile();
        try {
            final ClassicText t = unitTexts(dir);
            final ClassicHud.UnitFacts settler = facts(6, "freeColonist", null,
                ClassicHud.ORDERS_NONE, -1, "model.tile.mixedForest", true, 3, 39, 55);
            assertEquals("[(260,70) Mv: 1, (260,77) At: (39, 55), (242,86) Nat3 U0, "
                + "(242,93,gold) J19, (242,100,gold) O0, (242,107) (F2- Woods), "
                + "(242,114) (Path)]", lines(ClassicHud.activeLines(t, settler)));
            final ClassicHud.UnitFacts hardy = facts(7, "hardyPioneer", "pioneer",
                ClassicHud.ORDERS_NONE, 100, null, false, 3, 0, 0);
            final List<ClassicHud.TextLine> l = ClassicHud.listLines(t, hardy, 124);
            assertEquals("[(260,128,gold) Exp 100, (260,135,gold) Tools, (260,141,gold) O0]",
                         lines(l));
            assertEquals(149, ClassicHud.nextListY(124, l));
            // 007: the active hardy pioneer: skill, '(' count / word ')'.
            assertEquals("[(260,70) Mv: 1, (260,77) At: (39, 55), (242,86) Nat3 U2, "
                + "(242,93,gold) J20, (242,100,gold) (100, (242,107,gold) Tools), "
                + "(242,114,gold) O0, (242,121) (F2- Woods), (242,128) (Path)]",
                lines(ClassicHud.activeLines(t, facts(7, "hardyPioneer", "pioneer",
                    ClassicHud.ORDERS_NONE, 100, "model.tile.mixedForest", true, 3, 39, 55))));
            // 007: a free colonist without a role in the list: its skill.
            assertEquals("[(260,142,gold) J19, (260,148,gold) O0]", lines(ClassicHud.listLines(t,
                facts(6, "freeColonist", null, ClassicHud.ORDERS_NONE, -1, null, false, 3, 0, 0),
                138)));
            // A ship in a list: only the orders line; the next entry 18 lower.
            final List<ClassicHud.TextLine> s = ClassicHud.listLines(t, facts(13, "caravel",
                null, ClassicHud.ORDERS_NONE, -1, null, false, 12, 0, 0), 150);
            assertEquals("[(260,154,gold) O0]", lines(s));
            assertEquals(168, ClassicHud.nextListY(150, s));
            assertEquals("F2- Woods", ClassicHud.terrainName(t, "model.tile.mixedForest"));
            assertEquals("T2", ClassicHud.terrainName(t, "model.tile.plains"));
            assertEquals("Oce", ClassicHud.terrainName(t, "model.tile.lake"));
            assertEquals("Hil", ClassicHud.terrainName(t, "model.tile.hills"));
            assertNull(ClassicHud.terrainName(t, "model.tile.unknown"));
        } finally {
            dir.delete();
        }
    }

    /** Sprite offsets, flag rings, moves text, the unit tables. */
    public void testUnitGeometryAndTables() {
        assertEquals(3, ClassicHud.spriteOffset(6));
        assertEquals(3, ClassicHud.spriteOffset(7));
        assertEquals(3, ClassicHud.spriteOffset(13));
        assertEquals(2, ClassicHud.spriteOffset(8));
        assertEquals(2, ClassicHud.spriteOffset(14));
        assertEquals(new Rectangle(242, 68, 7, 9), ClassicHud.flagRing(242, 68, 13));  // 032 ship
        assertEquals(new Rectangle(250, 117, 7, 9), ClassicHud.flagRing(242, 110, 8)); // 032 soldier
        assertEquals(new Rectangle(250, 135, 7, 9), ClassicHud.flagRing(242, 128, 7)); // 032 pioneer
        assertEquals(new Rectangle(249, 75, 7, 9), ClassicHud.flagRing(242, 68, 6));   // 000 settler
        assertEquals(new Rectangle(242, 149, 7, 9), ClassicHud.flagRing(242, 149, 14)); // 000 dragoon
        assertEquals("5", ClassicHud.movesText(15));
        assertEquals("1 1/3", ClassicHud.movesText(4));
        assertEquals("2/3", ClassicHud.movesText(2));
        assertEquals(14, ClassicHud.unitRow("merchantman", null));
        assertEquals(13, ClassicHud.unitRow("caravel", null));
        assertEquals(2, ClassicHud.unitRow("freeColonist", "pioneer"));
        assertEquals(1, ClassicHud.unitRow("veteranSoldier", "soldier"));
        assertEquals(4, ClassicHud.unitRow("veteranSoldier", "dragoon"));
        assertEquals(0, ClassicHud.unitRow("expertFarmer", "default"));
        assertEquals(19, ClassicHud.jobRow("freeColonist"));
        assertEquals(20, ClassicHud.jobRow("hardyPioneer"));
        assertEquals(-1, ClassicHud.jobRow("caravel"));
        assertEquals(ClassicHud.QUAL_VETERAN, ClassicHud.qualifier("veteranSoldier", "soldier"));
        assertEquals(ClassicHud.QUAL_EXPERT, ClassicHud.qualifier("hardyPioneer", "pioneer"));
        assertEquals(ClassicHud.QUAL_NONE, ClassicHud.qualifier("freeColonist", "pioneer"));
        assertEquals(ClassicHud.QUAL_NONE, ClassicHud.qualifier("veteranSoldier", null));
    }

    /**
     * The four European nations fill with the original's colours (build
     * spec W0d): England and Holland from 049/052/083/032, France and
     * Spain from the landfall clip's turn indicator.
     */
    public void testNationFill() {
        final Game game = FreeColTestCase.getStandardGame();
        final int[] fill = { 0xFF0000, 0x5555FF, 0xFFFF55, 0xFF7100 };
        for (int i = 0; i < fill.length; i++) {
            final String id = ClassicNewWorldScreens.NATION_IDS[i];
            final Player player = game.getPlayerByNationId(id);
            assertNotNull(id, player);
            assertEquals(id, fill[i], ClassicHud.nationRgb(player));
        }
        assertEquals(0xFFFFFF, ClassicHud.nationRgb(null));
    }

    /**
     * The turn indicator (build spec W5c): a solid 5x3 box at x 315-319,
     * y 197-199 over the wood, nothing else touched, and no box at all
     * without a colour.
     */
    public void testTurnIndicator() {
        final BufferedImage on = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
        final BufferedImage off = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = on.createGraphics();
        ClassicHud.paintPanel(g, null, null, null, new ClassicHud.PanelModel(
            null, null, null, false, null, null, 0x6D3C18));
        g.dispose();
        g = off.createGraphics();
        ClassicHud.paintPanel(g, null, null, null, new ClassicHud.PanelModel(
            null, null, null, false, null, null, -1));
        g.dispose();
        int changed = 0;
        for (int y = 0; y < 200; y++) {
            for (int x = 0; x < 320; x++) {
                if (on.getRGB(x, y) == off.getRGB(x, y)) continue;
                changed++;
                assertTrue(x + "," + y, x >= 315 && y >= 197);
                assertEquals(0x6D3C18, rgb(on, x, y));
            }
        }
        assertEquals(15, changed);
        assertEquals(new Rectangle(315, 197, 5, 3), ClassicHud.INDICATOR);
        // The old constructors paint no indicator.
        assertEquals(-1, new ClassicHud.PanelModel(null, null, null, true).indicator);
    }

    /** The HUD canvas at 1920x1080: scale 5, strip/map/panel on one grid. */
    public void testHudLayout() {
        final Rectangle[] r = ClassicHudPane.layout(1920, 1080);
        assertEquals(new Rectangle(160, 40, 1600, 1000), r[0]);
        assertEquals(new Rectangle(160, 40, 1600, 40), r[1]);
        assertEquals(new Rectangle(160, 80, 1200, 960), r[2]);
        assertEquals(new Rectangle(1360, 80, 400, 960), r[3]);
        final Rectangle[] s = ClassicHudPane.layout(800, 600);
        assertEquals(new Rectangle(80, 100, 640, 16), s[1]);
        assertEquals(new Rectangle(560, 116, 160, 384), s[3]);
    }

    /** The key map: no keystroke twice, none of the map viewer's, as specified. */
    public void testKeyMap() {
        final java.util.Set<javax.swing.KeyStroke> seen = new java.util.HashSet<>();
        final java.util.Set<javax.swing.KeyStroke> map = ClassicKeyMap.mapViewerKeys();
        assertEquals(ClassicMapViewer.BOUND_KEYS.length, map.size());
        for (ClassicKeyMap.Binding b : ClassicKeyMap.bindings()) {
            assertNotNull(b.toString(), b.key);
            assertTrue("duplicate " + b, seen.add(b.key));
            assertFalse("map key " + b, map.contains(b.key));
        }
        assertEquals("[reportNavalAction]", find("F7").actionIds.toString());
        assertEquals("[reportMilitaryAction]", find("shift F7").actionIds.toString());
        assertEquals("[disbandUnitAction]", find("shift D").actionIds.toString());
        // P: clear the forest, else plough.
        final ClassicKeyMap.Binding p = find("P");
        assertEquals("plowAction", ClassicKeyMap.pick(p, id -> id.equals("plowAction")));
        assertEquals("clearForestAction", ClassicKeyMap.pick(p, id -> true));
        assertNull(ClassicKeyMap.pick(p, id -> false));
        // No-op seams never fire.
        assertNull(ClassicKeyMap.pick(find("G"), id -> true));
        // M only in TERRAIN mode, V only outside it.
        assertTrue(ClassicKeyMap.modeAllows(find("M"), true));
        assertFalse(ClassicKeyMap.modeAllows(find("M"), false));
        assertTrue(ClassicKeyMap.modeAllows(find("V"), false));
        assertFalse(ClassicKeyMap.modeAllows(find("V"), true));
        // U only in a colony, O only for a ship outside one (their BEFEHLE
        // rows' rules); others ignore the context.
        final ClassicMenuModel.Context shipAtSea = new ClassicMenuModel.Context(
            true, true, true, false, false, false, false);
        final ClassicMenuModel.Context shipInColony = new ClassicMenuModel.Context(
            true, true, true, false, false, true, false);
        assertFalse(ClassicKeyMap.contextAllows(find("U"), shipAtSea));
        assertTrue(ClassicKeyMap.contextAllows(find("U"), shipInColony));
        assertTrue(ClassicKeyMap.contextAllows(find("O"), shipAtSea));
        assertFalse(ClassicKeyMap.contextAllows(find("O"), shipInColony));
        assertFalse(ClassicKeyMap.contextAllows(find("O"), null));
        assertTrue(ClassicKeyMap.contextAllows(find("L"), null));
        // The in-game way back to the title.
        assertEquals("[newAction]", find("control N").actionIds.toString());
    }

    private static ClassicKeyMap.Binding find(String key) {
        final javax.swing.KeyStroke ks = javax.swing.KeyStroke.getKeyStroke(key);
        for (ClassicKeyMap.Binding b : ClassicKeyMap.bindings()) {
            if (b.key.equals(ks)) return b;
        }
        fail("no binding " + key);
        return null;
    }

    private static ClassicHud.MinimapModel model(int c0, int r0) {
        final int[] rgb = new int[W * H];
        Arrays.fill(rgb, ClassicHud.UNEXPLORED);
        return new ClassicHud.MinimapModel(W, H, rgb, c0, r0);
    }

    private static int rgb(BufferedImage img, int x, int y) {
        return img.getRGB(x, y) & 0xFFFFFF;
    }
}
