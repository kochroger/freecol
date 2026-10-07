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
import net.sf.freecol.tools.classicassets.ClassicAssetDecoderTest;
import net.sf.freecol.tools.classicassets.FfDecoder;
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
                 + "@OTHER_NAMES\r\nWoods\r\nRiv\r\nBigRiv\r\nSmallRiv\r\nUnexp\r\n\r\n");
        n.append("@RESOURCE\r\n");
        for (int i = 0; i < 14; i++) n.append("R").append(i).append(", 6\r\n");
        n.append("\r\n@COLONYNAME\r\nNewE\r\nNewF\r\nNewS\r\nNewH\r\n\r\n"
                 + "@HOMEPORT\r\nP0\r\nP1\r\nP2\r\nHome3\r\n\r\n@TRIBES\r\n");
        final int[] levels = { 3, 2, 1, 1, 1, 0, 0, 0 };   // as in NAMES.TXT
        for (int i = 0; i < 8; i++) {
            n.append("Tribe").append(i).append(", Tribe").append(i).append(", Gifts, ")
                .append(levels[i]).append(", 54\r\n");
        }
        n.append("\r\n@LEVELS\r\nLv0, a camp, camps\r\nLv1, a village, villages\r\n"
                 + "Lv2, a town, towns\r\nLv3, a town, towns\r\nAll, a capital, capitals\r\n\r\n");
        final StringBuilder l = new StringBuilder("@INFO\r\nMv:\r\nAt:\r\nWith:\r\nWith:\r\n"
            + "\r\n@CTITLE\r\nPop:\r\nCoin:\r\n\r\n@MISC\r\n");
        for (int i = 0; i < 106; i++) {
            l.append(i == 2 ? "AA" : i == 4 ? "Exp" : i == 18 ? "Land" : i == 31 ? "Path"
                     : i == 64 ? "Vet" : i == 82 ? "Plow" : i == 104 ? "+ More +"
                     : "m" + i).append("\r\n");
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
            // A ship in a list: its type name over the orders (clip007
            // #3107: "Handelsschiff" / "Keine Befehle"); the next entry 18 lower.
            final List<ClassicHud.TextLine> s = ClassicHud.listLines(t, facts(13, "caravel",
                null, ClassicHud.ORDERS_NONE, -1, null, false, 12, 0, 0), 150);
            assertEquals("[(260,154,gold) U13, (260,160,gold) O0]", lines(s));
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
        // The tribes' flags in their @TRIBES colours (clip004 #5090: the
        // Sioux brave's flag #920000, not FreeCol's #900000; the Araukaner's
        // 54), FreeCol's colour for a nation the original has not.
        assertEquals(0x920000, ClassicHud.nationRgb(game.getPlayerByNationId(
            "model.nation.sioux")));
        assertEquals(0x698AC3, ClassicHud.nationRgb(game.getPlayerByNationId(
            "model.nation.arawak")));
        assertEquals(0x6D3C18, ClassicHud.nationRgb(game.getPlayerByNationId(
            "model.nation.iroquois")));
        // All eight tribes (H4, R1 fix C): the palette entries of NAMES.TXT
        // @TRIBES, never FreeCol's, which swap Sioux/Apache and
        // Iroquois/Cherokee; the flags, the minimap and the turn indicator
        // agree, and the letter's dark shade follows the fill.
        final String[][] tribes = {
            { "inca", "F7F3C7" }, { "aztec", "C7A220" }, { "arawak", "698AC3" },
            { "iroquois", "6D3C18" }, { "cherokee", "75A64D" }, { "apache", "C3AE86" },
            { "sioux", "920000" }, { "tupi", "045D04" }
        };
        for (String[] t : tribes) {
            final Player p = game.getPlayerByNationId("model.nation." + t[0]);
            assertNotNull(t[0], p);
            final int want = Integer.parseInt(t[1], 16);
            assertEquals(t[0], want, ClassicHud.nationRgb(p));
            assertEquals(t[0], want, ClassicHud.indicatorRgb(p));
            assertEquals(t[0], (((want >> 16) & 0xFF) * 2 / 3 << 16)
                | (((want >> 8) & 0xFF) * 2 / 3 << 8) | ((want & 0xFF) * 2 / 3),
                ClassicHud.nationDark(p));
        }
        final Player ref = game.getPlayerByNationId("model.nation.dutchREF");
        if (ref != null) {
            assertEquals(ref.getNationColor().getRGB() & 0xFFFFFF, ClassicHud.nationRgb(ref));
        }
    }

    /**
     * The minimap's land colours (build spec W16, G2 spec 3.1): every tile
     * type of the classic rules, a forest in its base terrain's colour,
     * water as ocean.  Each line names a clip tile whose minimap pixel has
     * this palette entry (landfall {@code minitile_terrain.txt},
     * {@code g2-mini-clip004..008.txt}).
     */
    public void testMinimapLandColours() {
        final Object[][] want = {
            { "tundra", 0xBABA41 },          // 72: clip006 #2037 (22,18)
            { "borealForest", 0xBABA41 },    // 72: the pairing (I)
            { "desert", 0xCFB28E },          // 88: TERRAIN.SS.008 tiles
            { "scrubForest", 0xCFB28E },     // 88: clip008 (43,43)
            { "plains", 0x867151 },          // 92: clip005 (14,57)
            { "mixedForest", 0x867151 },     // 92: landfall (48,44)
            { "prairie", 0x8A8E3C },         // 75: clip005 (25,20)
            { "broadleafForest", 0x8A8E3C }, // 75: landfall (47,46)
            { "grassland", 0x1C6D10 },       // 70: clip005 (24,23)
            { "coniferForest", 0x1C6D10 },   // 70: landfall (49,43)
            { "savannah", 0x75A64D },        // 67: clip006 #2037 (25,31)
            { "tropicalForest", 0x75A64D },  // 67: landfall (46,42)
            { "marsh", 0x34499E },           // 58: clip006 #2037 (22,14)
            { "wetlandForest", 0x34499E },   // 58: landfall (47,45)
            { "swamp", 0x75A64D },           // 67: the pairing (I)
            { "rainForest", 0x75A64D },      // 67: landfall (50,44)
            { "hills", 0xBAA27D },           // 89: clip005 (23,16)
            { "mountains", 0xDBCFAE },       // 108: landfall (47,43), (52,42)
            { "arctic", 0xE3E3E3 },          // in no clip (I)
        };
        for (Object[] w : want) {
            assertEquals((String) w[0], ((Integer) w[1]).intValue(),
                         ClassicHud.minimapLandRgb("model.tile." + w[0], true));
        }
        for (String water : new String[] { "ocean", "highSeas", "lake", "greatRiver" }) {
            assertEquals(water, ClassicHud.OCEAN_RGB,
                         ClassicHud.minimapLandRgb("model.tile." + water, false));
        }
        // An unknown land type: plains (I).
        assertEquals(0x867151, ClassicHud.minimapLandRgb("model.tile.unknown", true));
        assertEquals(0x867151, ClassicHud.minimapLandRgb(null, true));
        // Every land type of the classic rules is in the table.
        final net.sf.freecol.common.model.Specification spec
            = FreeColTestCase.spec("classic");
        for (net.sf.freecol.common.model.TileType tt : spec.getTileTypeList()) {
            if (tt.isWater()) continue;
            final String s = net.sf.freecol.common.model.Role.getRoleIdSuffix(tt.getId());
            boolean found = false;
            for (Object[] w : want) found |= w[0].equals(s);
            assertTrue(tt.getId(), found);
        }
    }

    /**
     * The minimap of a live map (build spec W16): unexplored black, a Dutch
     * colony and a French unit in their fills, an Araukaner village and a
     * Sioux brave in their {@code @TRIBES} colours (54 and 118), plain
     * tiles in the land table, water as ocean.
     */
    public void testMinimapOwners() {
        final Game game = FreeColTestCase.getStandardGame();
        final net.sf.freecol.common.model.Specification spec = FreeColTestCase.spec();
        final FreeColTestCase.MapBuilder mb = new FreeColTestCase.MapBuilder(game);
        mb.setBaseTileType(spec.getTileType("model.tile.plains")).setExploredByAll(true)
            .setTileType(2, 2, spec.getTileType("model.tile.coniferForest"))
            .setTileType(3, 2, spec.getTileType("model.tile.mountains"))
            .setTileType(4, 2, spec.getTileType("model.tile.ocean"));
        final net.sf.freecol.common.model.Map map = mb.build();
        game.changeMap(map);
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final Player french = game.getPlayerByNationId("model.nation.french");
        final Player arawak = game.getPlayerByNationId("model.nation.arawak");
        final Player sioux = game.getPlayerByNationId("model.nation.sioux");
        net.sf.freecol.util.test.FreeColTestUtils.getColonyBuilder().player(dutch)
            .colonyTile(map.getTile(5, 8)).build();
        new FreeColTestCase.IndianSettlementBuilder(game).player(arawak)
            .settlementTile(map.getTile(8, 8)).build();
        final net.sf.freecol.common.model.UnitType colonist
            = spec.getUnitType("model.unit.freeColonist");
        new net.sf.freecol.server.model.ServerUnit(game, map.getTile(9, 3), french, colonist);
        new net.sf.freecol.server.model.ServerUnit(game, map.getTile(10, 3), sioux,
            spec.getUnitType("model.unit.brave"));
        map.getTile(12, 12).setType(null);   // unexplored
        final ClassicHud.MinimapModel m = ClassicHud.minimapOf(map, 1, 1);
        assertEquals(0xFF7100, m.at(5, 8));
        assertEquals(0x698AC3, m.at(8, 8));
        assertEquals(0x5555FF, m.at(9, 3));
        assertEquals(0x920000, m.at(10, 3));
        assertEquals(0x867151, m.at(1, 1));
        assertEquals(0x1C6D10, m.at(2, 2));
        assertEquals(0xDBCFAE, m.at(3, 2));
        assertEquals(ClassicHud.OCEAN_RGB, m.at(4, 2));
        assertEquals(ClassicHud.UNEXPLORED, m.at(12, 12));
    }

    /**
     * With the pack (skipped without it): every minimap colour is the
     * game palette's entry at the index measured in the clips.
     */
    public void testMinimapColoursAreGamePaletteEntries() {
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final int[] pal = (pack == null) ? null : pack.gamePalette();
        if (pal == null) {
            System.err.println("testMinimapColoursAreGamePaletteEntries skipped: no pack");
            return;
        }
        final Object[][] want = {
            { "tundra", 72 }, { "desert", 88 }, { "plains", 92 }, { "prairie", 75 },
            { "grassland", 70 }, { "savannah", 67 }, { "marsh", 58 }, { "swamp", 67 },
            { "borealForest", 72 }, { "scrubForest", 88 }, { "mixedForest", 92 },
            { "broadleafForest", 75 }, { "coniferForest", 70 }, { "tropicalForest", 67 },
            { "wetlandForest", 58 }, { "rainForest", 67 }, { "hills", 89 },
            { "mountains", 108 }, { "arctic", 19 },
        };
        for (Object[] w : want) {
            assertEquals((String) w[0], pal[(Integer) w[1]],
                         ClassicHud.minimapLandRgb("model.tile." + w[0], true));
        }
        assertEquals(pal[60], ClassicHud.OCEAN_RGB);
        // The tribes' colours (NAMES.TXT @TRIBES): Araukaner 54, Sioux 118.
        final Game game = FreeColTestCase.getStandardGame();
        assertEquals(pal[54], ClassicHud.indicatorRgb(
            game.getPlayerByNationId("model.nation.arawak")));
        assertEquals(pal[118], ClassicHud.indicatorRgb(
            game.getPlayerByNationId("model.nation.sioux")));
        assertEquals(pal[13], ClassicHud.indicatorRgb(
            game.getPlayerByNationId("model.nation.dutch")));
    }

    /**
     * The minimap-only repaint (C3, a blink's dot, a jump's ring, a native
     * cue's pixel): the panel painted with one minimap and then only its
     * minimap area with another is pixel for pixel the panel painted with
     * the other; and only a clip within the minimap's frame takes it.
     */
    public void testMinimapOnlyRepaint() {
        final ClassicHud.MinimapModel a = model(42, 20);
        final ClassicHud.MinimapModel b = a.with(50, 25, ClassicHud.BLINK_DOT_RGB)
            .with(48, 22, 0xFF7100);
        final ClassicHud.MinimapModel c = model(30, 30);   // the view jumped: the ring moves
        for (ClassicHud.MinimapModel next : new ClassicHud.MinimapModel[] { b, c, null }) {
            final BufferedImage want = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
            final BufferedImage got = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = want.createGraphics();
            ClassicHud.paintPanel(g, null, null, null, new ClassicHud.PanelModel(
                next, null, null, false, null, null, 0x6D3C18));
            g.dispose();
            g = got.createGraphics();
            ClassicHud.paintPanel(g, null, null, null, new ClassicHud.PanelModel(
                a, null, null, false, null, null, 0x6D3C18));
            ClassicHud.paintMinimapArea(g, next);
            g.dispose();
            for (int y = 0; y < 200; y++) {
                for (int x = 0; x < 320; x++) {
                    assertEquals(x + "," + y, rgb(want, x, y), rgb(got, x, y));
                }
            }
        }
        // The panel component's clips at scale 4: the minimap (or a part)
        // is minimap-only, the indicator or the whole panel is not.
        final int s = 4;
        assertTrue(ClassicInfoPanel.minimapOnly(new Rectangle(
            (ClassicHud.MINIMAP.x - ClassicHud.PANEL_X) * s,
            (ClassicHud.MINIMAP.y - ClassicHud.PANEL_Y) * s,
            ClassicHud.MINIMAP.width * s, ClassicHud.MINIMAP.height * s), s));
        assertTrue(ClassicInfoPanel.minimapOnly(new Rectangle(
            (ClassicHud.MINIMAP_FRAME.x - ClassicHud.PANEL_X) * s,
            (ClassicHud.MINIMAP_FRAME.y - ClassicHud.PANEL_Y) * s,
            ClassicHud.MINIMAP_FRAME.width * s, ClassicHud.MINIMAP_FRAME.height * s), s));
        assertFalse(ClassicInfoPanel.minimapOnly(new Rectangle(
            (ClassicHud.INDICATOR.x - ClassicHud.PANEL_X) * s,
            (ClassicHud.INDICATOR.y - ClassicHud.PANEL_Y) * s, 20, 12), s));
        assertFalse(ClassicInfoPanel.minimapOnly(new Rectangle(0, 0, 320, 768), s));
        assertFalse(ClassicInfoPanel.minimapOnly(null, s));
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

    // The tile mode of the Spielzugende mode (build spec W17).

    private static ClassicHud.TileFacts tileFacts(boolean land, String name, int tribe,
                                                  int river, boolean road, boolean plowed,
                                                  int resource, ClassicHud.SettlementFacts s,
                                                  ClassicHud.UnitFacts... units) {
        return new ClassicHud.TileFacts(50, 43, ClassicHud.REGION_UNKNOWN, land, name, 3,
            tribe, land ? "model.tile.rainForest" : "model.tile.ocean", river, road,
            plowed, resource, s, new java.util.ArrayList<>(Arrays.asList(units)));
    }

    /**
     * The lines (landing-slow #2306/#4193/#6182, clip005 #18186): "Ort"
     * at (242,68), the land name on land only, the terrain, then river,
     * road, plowing and resource, 7 px apart; the land's name is the
     * player's, the nation's default, or the tribe's at its village.
     */
    public void testTileModeLines() throws IOException {
        final File dir = Files.createTempDirectory("classic-hud-t").toFile();
        try {
            final ClassicText t = unitTexts(dir);
            assertEquals("[(242,68) At: (50, 43) 1, (242,75) NewH, (242,82) (F7- Woods)]",
                lines(ClassicHud.tileLines(t, tileFacts(true, null, -1, 0, false, false, -1,
                                                        null))));
            assertEquals("[(242,68) At: (50, 43) 1, (242,75) (Oce)]",
                lines(ClassicHud.tileLines(t, tileFacts(false, "Mine", -1, 0, false, false,
                                                        -1, null))));
            assertEquals("[(242,68) At: (50, 43) 1, (242,75) Mine, (242,82) (F7- Woods), "
                + "(242,89) (SmallRiv), (242,96) (Path), (242,103) (Plow), (242,110) (R10)]",
                lines(ClassicHud.tileLines(t, tileFacts(true, "Mine", -1, 1, true, true,
                    ClassicHud.resourceRow("model.resource.lumber"), null))));
            assertEquals("(BigRiv)", ClassicHud.tileLines(t, tileFacts(true, null, -1, 2,
                false, false, -1, null)).get(3).text);
            assertEquals("Tribe2 Land", ClassicHud.landName(t, tileFacts(true, "Mine", 2, 0,
                false, false, -1, null)));
            assertNull(ClassicHud.landName(t, tileFacts(false, "Mine", 2, 0, false, false,
                                                        -1, null)));
            // The tables.
            assertEquals(10, ClassicHud.resourceRow("model.resource.lumber"));
            assertEquals(9, ClassicHud.resourceRow("model.resource.game"));
            assertEquals(8, ClassicHud.resourceRow("model.resource.furs"));
            assertEquals(-1, ClassicHud.resourceRow("model.resource.unknown"));
            assertEquals(2, ClassicHud.tribeRow("model.nation.arawak"));
            assertEquals(7, ClassicHud.tribeRow("model.nation.tupi"));
            assertEquals(-1, ClassicHud.tribeRow("model.nation.dutch"));
            // A village entry: tribe and kind, green at x 262, cell + 4 / + 10.
            assertEquals("[(262,103) Tribe2, (262,109) a village]", lines(
                ClassicHud.settlementLines(t, new ClassicHud.SettlementFacts(null, 2, false),
                                           99)));
            assertEquals("[(262,103) Tribe2, (262,109) a capital]", lines(
                ClassicHud.settlementLines(t, new ClassicHud.SettlementFacts(null, 2, true),
                                           99)));
            assertEquals("AA", ClassicHud.promptWord(t));
        } finally {
            dir.delete();
        }
    }

    /**
     * The layout: the entries 10 below the last line, the word 7 below
     * where the next entry would go -- 117 under one unit at 92 (clip004
     * #1974, landing-slow #2306), 124 at 99 (#6182), 129 under a village
     * at 99 (#4193) -- 15 below the last line without an entry, never
     * below 192 (clip005 #15391).
     */
    public void testTileModeLayout() throws IOException {
        final File dir = Files.createTempDirectory("classic-hud-t").toFile();
        try {
            final ClassicText t = unitTexts(dir);
            final ClassicHud.UnitFacts vet = facts(8, "veteranSoldier", "soldier",
                ClassicHud.ORDERS_NONE, -1, null, false, 0, 50, 43);
            assertTrue(Arrays.equals(new int[] { 117, 92 }, ClassicHud.tileLayout(t,
                tileFacts(true, null, -1, 0, false, false, -1, null, vet))));
            assertTrue(Arrays.equals(new int[] { 124, 99 }, ClassicHud.tileLayout(t,
                tileFacts(true, null, -1, 0, false, false, 10, null, vet))));
            assertTrue(Arrays.equals(new int[] { 129, 99 }, ClassicHud.tileLayout(t,
                tileFacts(true, null, 2, 0, true, false, -1,
                          new ClassicHud.SettlementFacts(null, 2, false)))));
            assertTrue(Arrays.equals(new int[] { 90 }, ClassicHud.tileLayout(t,
                tileFacts(false, null, -1, 0, false, false, -1, null))));
            // Many units: the word stops at 192; entries past the bottom are skipped.
            final ClassicHud.UnitFacts[] many = new ClassicHud.UnitFacts[8];
            Arrays.fill(many, vet);
            final int[] lay = ClassicHud.tileLayout(t,
                tileFacts(true, null, -1, 0, false, false, -1, null, many));
            assertEquals(192, lay[0]);
            assertEquals(92, lay[1]);
            assertEquals(110, lay[2]);
            assertEquals(182, lay[6]);
            assertEquals(-1, lay[7]);
            // Its press area: x 242, the word's glyph box.
            assertEquals(new Rectangle(242, 117, 45, 7), ClassicHud.promptWordBounds(null, t,
                tileFacts(true, null, -1, 0, false, false, -1, null, vet)));
        } finally {
            dir.delete();
        }
    }

    /**
     * The paint: the panel's tile mode instead of the unit block, the word
     * white while ON and black while OFF at its place, nothing else
     * different; the map's square is the cell's 60 border pixels.
     */
    public void testTileModePaint() throws IOException {
        final File dir = Files.createTempDirectory("classic-hud-t").toFile();
        try {
            final ClassicText t = unitTexts(dir);
            final FfDecoder.Font ff = FfDecoder.decodePart(ClassicAssetDecoderTest.ffPart(
                2, 3, new int[][] { { 65, 3,  1, 0, 0,   0, 1, 0 } }));
            final ClassicFont font = ClassicFont.fromAtlas(FfDecoder.toAtlas(ff),
                                                           FfDecoder.metrics(ff));
            final ClassicHud.TileFacts f = tileFacts(false, null, -1, 0, false, false, -1,
                                                     null);
            final BufferedImage on = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
            final BufferedImage off = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = on.createGraphics();
            ClassicHud.paintPanel(g, font, null, t, new ClassicHud.PanelModel(null, null,
                null, false, null, null, -1, f, ClassicHud.PROMPT_ON_RGB));
            g.dispose();
            g = off.createGraphics();
            ClassicHud.paintPanel(g, font, null, t, new ClassicHud.PanelModel(null, null,
                null, false, null, null, -1, f, ClassicHud.PROMPT_OFF_RGB));
            g.dispose();
            final int wy = ClassicHud.tileLayout(t, f)[0];
            int changed = 0;
            for (int y = 0; y < 200; y++) {
                for (int x = 0; x < 320; x++) {
                    if (on.getRGB(x, y) == off.getRGB(x, y)) continue;
                    changed++;
                    assertTrue(x + "," + y, x >= 242 && x < 248 && y >= wy && y < wy + 2);
                    assertEquals(0xFFFFFF, rgb(on, x, y));
                    assertEquals(0x000000, rgb(off, x, y));
                }
            }
            assertEquals(4, changed);   // "AA": two ink pixels per glyph

            // The square: 60 native pixels, at any scale.
            for (int s : new int[] { 1, 3 }) {
                final BufferedImage m = new BufferedImage(64 * s, 64 * s,
                                                          BufferedImage.TYPE_INT_RGB);
                g = m.createGraphics();
                ClassicHud.paintPromptSquare(g, 16 * s, 16 * s, s);
                g.dispose();
                int white = 0;
                for (int y = 0; y < m.getHeight(); y += s) {
                    for (int x = 0; x < m.getWidth(); x += s) {
                        if (rgb(m, x, y) != 0xFFFFFF) continue;
                        white++;
                        final int cx = x / s - 16, cy = y / s - 16;
                        assertTrue(cx + "," + cy, cx >= 0 && cx < 16 && cy >= 0 && cy < 16
                            && (cx == 0 || cx == 15 || cy == 0 || cy == 15));
                    }
                }
                assertEquals(60, white);
            }
        } finally {
            dir.delete();
        }
    }

    /** The facts of a live tile: units in order, the tribe at its village, the extras. */
    public void testTileFactsOf() {
        final Game game = FreeColTestCase.getStandardGame();
        final net.sf.freecol.common.model.Map map = FreeColTestCase.getTestMap(
            FreeColTestCase.spec().getTileType("model.tile.plains"), true);
        game.changeMap(map);
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final net.sf.freecol.common.model.Tile tile = map.getTile(5, 5);
        final ClassicHud.UnitFacts u = facts(8, "veteranSoldier", "soldier",
            ClassicHud.ORDERS_NONE, -1, null, false, 0, 5, 5);
        ClassicHud.TileFacts f = ClassicHud.TileFacts.of(tile, dutch, null, Arrays.asList(u));
        assertEquals(5, f.x);
        assertEquals(5, f.y);
        assertTrue(f.land);
        assertEquals(3, f.nation);
        assertEquals(-1, f.tribeRow);
        assertNull(f.settlement);
        assertNull(f.landName);
        assertEquals("model.tile.plains", f.terrainId);
        assertEquals(1, f.units.size());
        dutch.setNewLandName("Mine");
        f = ClassicHud.TileFacts.of(tile, dutch, null, null);
        assertEquals("Mine", f.landName);
        assertEquals(0, f.units.size());
        assertEquals(ClassicHud.REGION_UNKNOWN, f.region);
    }

    // The panel's completeness (build spec W21b), on synthetic texts.

    /** Hand-built unit facts with the W21b fields. */
    private static final class F {
        private final String type, role;
        private int w = 8, orders = ClassicHud.ORDERS_NONE, tools = -1, moves = 3,
            x = 1, y = 1, treasure = -1, river = 0, resource = -1;
        private String terrain = null, dest = null;
        private boolean road = false, plowed = false, home = false;
        private List<ClassicHud.GoodsIcon> cargo = null;
        private BufferedImage sprite = null;

        F(String type, String role) {
            this.type = type;
            this.role = role;
        }

        F w(int v) { this.w = v; return this; }
        F moves(int v) { this.moves = v; return this; }
        F sprite(BufferedImage v) { this.sprite = v; return this; }
        F cargo(List<ClassicHud.GoodsIcon> v) { this.cargo = v; return this; }
        F orders(int v) { this.orders = v; return this; }
        F tools(int v) { this.tools = v; return this; }
        F at(int px, int py) { this.x = px; this.y = py; return this; }
        F treasure(int v) { this.treasure = v; return this; }
        F terrain(String v) { this.terrain = v; return this; }
        F dest(String v) { this.orders = ClassicHud.ORDERS_GOTO; this.dest = v; return this; }
        F home() { this.orders = ClassicHud.ORDERS_GOTO; this.home = true; return this; }
        F extras(int r, boolean rd, boolean pl, int res) {
            this.river = r; this.road = rd; this.plowed = pl; this.resource = res;
            return this;
        }
        F cargo(int... frames) {
            this.cargo = new java.util.ArrayList<>();
            for (int f : frames) {
                final boolean full = f < ClassicHud.ICON_GOODS_GREY;
                this.cargo.add(new ClassicHud.GoodsIcon(f - (full ? ClassicHud.ICON_GOODS
                    : ClassicHud.ICON_GOODS_GREY), full, new BufferedImage(10, 10,
                        BufferedImage.TYPE_INT_ARGB)));
            }
            return this;
        }

        ClassicHud.UnitFacts build() {
            final int job = ClassicHud.jobRow(this.type);
            return new ClassicHud.UnitFacts((this.sprite != null) ? this.sprite
                : new BufferedImage(this.w, 16, BufferedImage.TYPE_INT_ARGB),
                0xFF7100, 0xAA4900, 3,
                ClassicHud.unitRow(this.type, this.role), this.type, this.moves, this.x,
                this.y, this.orders, this.terrain, this.road, job,
                job >= 0 && this.role == null, ClassicHud.qualifier(this.type, this.role),
                this.tools, this.treasure, this.dest, this.home, this.river, this.plowed,
                this.resource, this.cargo);
        }
    }

    /** The detail lines after the name (y 93 on). */
    private static String details(ClassicText t, ClassicHud.UnitFacts f) {
        final List<ClassicHud.TextLine> out = new java.util.ArrayList<>();
        for (ClassicHud.TextLine l : ClassicHud.activeLines(t, f)) {
            if (l.y >= ClassicHud.DETAIL_Y) out.add(l);
        }
        return out.toString();
    }

    /**
     * The block's second line: the qualifier when the unit has one and no
     * tools (clip007 #3107 the scout "Experte", #1449 the veteran
     * "Erfahren"), else the skill (clip006 #5351 the hardy pioneer with
     * tools "Pionier", clip005 #14827 the free-colonist soldier
     * "Siedler", clip007 #1306 the fur-trapper pioneer).
     */
    public void testActiveQualifierLine() throws IOException {
        final File dir = Files.createTempDirectory("classic-hud-w").toFile();
        try {
            final ClassicText t = unitTexts(dir);
            assertEquals("[(242,93,gold) Exp, (242,100,gold) O0]",
                details(t, new F("seasonedScout", "scout").build()));
            assertEquals("[(242,93,gold) Vet, (242,100,gold) O0]",
                details(t, new F("veteranSoldier", "soldier").build()));
            assertEquals("[(242,93,gold) J20, (242,100,gold) (100, (242,107,gold) Tools), "
                + "(242,114,gold) O0]",
                details(t, new F("hardyPioneer", "pioneer").tools(100).build()));
            assertEquals("[(242,93,gold) J19, (242,100,gold) O0]",
                details(t, new F("freeColonist", "soldier").build()));
            assertEquals("[(242,93,gold) J4, (242,100,gold) (100, (242,107,gold) Tools), "
                + "(242,114,gold) O0]",
                details(t, new F("expertFurTrapper", "pioneer").tools(100).build()));
            // A roleless veteran: its skill (no qualifier without the role).
            assertEquals("[(242,93,gold) J21, (242,100,gold) O0]",
                details(t, new F("veteranSoldier", null).build()));
        } finally {
            dir.delete();
        }
    }

    /**
     * After the terrain, one green line each for a river, a road, plowing
     * and a resource, in the tile mode's order, 7 apart (clip005 #14481
     * river and road, clip006 #5351 road and plowed, clip008 #4009 road
     * and resource).
     */
    public void testActiveExtrasOrder() throws IOException {
        final File dir = Files.createTempDirectory("classic-hud-w").toFile();
        try {
            final ClassicText t = unitTexts(dir);
            assertEquals("[(242,93,gold) J19, (242,100,gold) O0, (242,107) (T3), "
                + "(242,114) (SmallRiv), (242,121) (Path), (242,128) (Plow), (242,135) (R10)]",
                details(t, new F("freeColonist", null).terrain("model.tile.prairie")
                    .extras(1, true, true, 10).build()));
            assertEquals("[(242,93,gold) O0, (242,100) (F2- Woods), (242,107) (BigRiv)]",
                details(t, new F("artillery", null).w(14).terrain("model.tile.mixedForest")
                    .extras(2, false, false, -1).build()));
        } finally {
            dir.delete();
        }
    }

    /**
     * A goto to a colony shows the colony's name in gold instead of the
     * orders, the flag letter stays G (clip005 #14481: the artillery's
     * "Fur Town" at 93, no skill line; #14827: the soldier's "Base" at
     * 100 after its skill; the blacksmith's list entry "Schmied / Base");
     * a goto to Europe shows the orders' word and the home port ("Ziel
     * Amsterdam", R2, landfall #23375); another goto keeps the orders'
     * word ("Ziel", I).
     */
    public void testGotoDestination() throws IOException {
        final File dir = Files.createTempDirectory("classic-hud-w").toFile();
        try {
            final ClassicText t = unitTexts(dir);
            final ClassicHud.UnitFacts art = new F("artillery", null).w(14)
                .dest("Fur Town").build();
            assertEquals("[(242,93,gold) Fur Town]", details(t, art));
            assertEquals("G", ClassicHud.orderLetter(t, art.ordersRow));
            assertEquals("[(242,93,gold) J19, (242,100,gold) Base]",
                details(t, new F("freeColonist", "soldier").dest("Base").build()));
            assertEquals("[(260,128,gold) J14, (260,134,gold) Base]", lines(
                ClassicHud.listLines(t, new F("masterBlacksmith", null).dest("Base").build(),
                                     124)));
            assertEquals("[(242,93,gold) O3]",
                details(t, new F("artillery", null).w(14).orders(ClassicHud.ORDERS_GOTO)
                    .build()));
            // Europe: "Ziel Amsterdam" (the stand-in's O3 and Home3), letter G.
            final ClassicHud.UnitFacts ship = new F("merchantman", null).w(13).home().build();
            assertEquals("O3 Home3", ClassicHud.ordersText(t, ship));
            assertEquals("G", ClassicHud.orderLetter(t, ship.ordersRow));
            assertTrue(details(t, ship), details(t, ship).contains("gold) O3 Home3]"));
            // A colony's name wins; without a goto the home port is not shown.
            assertEquals("Base", ClassicHud.ordersText(t,
                new F("merchantman", null).home().dest("Base").build()));
            assertEquals("O0", ClassicHud.ordersText(t,
                new F("merchantman", null).home().orders(ClassicHud.ORDERS_NONE).build()));
        } finally {
            dir.delete();
        }
    }

    /**
     * A treasure: "(Gold: 10000)" in the block (clip005 #14594), "Gold:
     * 10000" over the orders in the list (#13993), the gold label of the
     * status line.
     */
    public void testTreasureLines() throws IOException {
        final File dir = Files.createTempDirectory("classic-hud-w").toFile();
        try {
            final ClassicText t = unitTexts(dir);
            final ClassicHud.UnitFacts gold = new F("treasureTrain", null).w(14)
                .treasure(10000).build();
            assertEquals("[(242,93,gold) (Coin: 10000), (242,100,gold) O0]",
                         details(t, gold));
            assertEquals("[(260,114,gold) Coin: 10000, (260,120,gold) O0]",
                         lines(ClassicHud.listLines(t, gold, 110)));
        } finally {
            dir.delete();
        }
    }

    /**
     * The list's names: the artillery and the wagon train by their
     * {@code @UNIT} name (clip005 #14364, #14481); a pioneer with tools and
     * no qualifier shows its tools without its skill (clip007 #5093, #6884);
     * a dragoon of another skill keeps its skill (clip006 #5135); a carrier
     * with goods only its orders, 10 below its cell (clip006 #9345).
     */
    public void testListNamesAndPioneer() throws IOException {
        final File dir = Files.createTempDirectory("classic-hud-w").toFile();
        try {
            final ClassicText t = unitTexts(dir);
            assertEquals("[(260,164,gold) U11, (260,170,gold) O6]", lines(
                ClassicHud.listLines(t, new F("artillery", null).w(14)
                    .orders(ClassicHud.ORDERS_FORTIFIED).build(), 160)));
            assertEquals("[(260,132,gold) U12, (260,138,gold) O0]", lines(
                ClassicHud.listLines(t, new F("wagonTrain", null).w(14).build(), 128)));
            assertEquals("[(260,114,gold) 100, (260,121,gold) Tools, (260,127,gold) O1]",
                lines(ClassicHud.listLines(t, new F("expertFurTrapper", "pioneer").tools(100)
                    .orders(ClassicHud.ORDERS_SENTRY).build(), 110)));
            assertEquals("[(260,171,gold) J5, (260,177,gold) O0]", lines(
                ClassicHud.listLines(t, new F("expertLumberJack", "dragoon").w(14).build(),
                                     167)));
            final List<ClassicHud.TextLine> wagon = ClassicHud.listLines(t,
                new F("wagonTrain", null).w(14).cargo(25, 25).build(), 173);
            assertEquals("[(260,183,gold) O0]", lines(wagon));
            assertEquals(191, ClassicHud.nextListY(173, wagon));
        } finally {
            dir.delete();
        }
    }

    /**
     * The places under the lines (G2 spec 4.2): clip006 #9345 (the wagon's
     * "Mit:" 126, icons from 259 at 124; the colony N 147, sprite 137,
     * "Mit:" 162; the list 173; "+ Weiter +" 191), clip005 #14852 (N 141,
     * list 167, Weiter 185), clip005 #14364 (N 134, list 160 and 178, no
     * Weiter: nothing remains), clip008 #29463 ("Mit:" 119), clip008
     * #35590 (cargo 126, N 147) and the tile mode of clip005 #15391 (N 116,
     * list 142, 160, 178, Weiter 196, the word at 192).
     */
    public void testCargoAndColonyLayout() throws IOException {
        final File dir = Files.createTempDirectory("classic-hud-w").toFile();
        try {
            final ClassicText t = unitTexts(dir);
            final ClassicHud.UnitFacts wagon = new F("wagonTrain", null).w(14)
                .terrain("model.tile.prairie").extras(0, true, true, -1).cargo(25).build();
            int last = last(ClassicHud.activeLines(t, wagon));
            assertEquals(114, last);
            final ClassicHud.UnitFacts wagon2 = new F("wagonTrain", null).w(14)
                .cargo(25, 25).build();
            ClassicHud.BlockLayout l = ClassicHud.blockLayout(t, last, true, true, false,
                Arrays.asList(wagon2, wagon2));
            assertEquals(126, l.cargoY);
            assertEquals(147, l.colonyY);
            assertEquals(173, l.entryY[0]);
            assertEquals(-1, l.entryY[1]);
            assertEquals(191, l.moreY);
            // clip005 #14852: the soldier on Base's road and plowed plains.
            final ClassicHud.UnitFacts soldier = new F("freeColonist", "soldier")
                .terrain("model.tile.plains").extras(0, true, true, -1).build();
            last = last(ClassicHud.activeLines(t, soldier));
            assertEquals(121, last);
            final ClassicHud.UnitFacts dragoon = new F("expertLumberJack", "dragoon")
                .w(14).build();
            l = ClassicHud.blockLayout(t, last, false, true, false,
                                       Arrays.asList(dragoon, dragoon));
            assertEquals(-1, l.cargoY);
            assertEquals(141, l.colonyY);
            assertEquals(167, l.entryY[0]);
            assertEquals(185, l.moreY);
            // clip005 #14364: two entries that fit, nothing remains: no Weiter.
            l = ClassicHud.blockLayout(t, 114, false, true, false, Arrays.asList(
                new F("expertFarmer", null).build(),
                new F("artillery", null).w(14).orders(ClassicHud.ORDERS_FORTIFIED).build()));
            assertEquals(134, l.colonyY);
            assertEquals(160, l.entryY[0]);
            assertEquals(178, l.entryY[1]);
            assertEquals(-1, l.moreY);
            // clip008 #29463 / #35590: a ship's "Mit:", and the colony after it.
            assertEquals(119, ClassicHud.blockLayout(t, 107, true, false, false,
                new java.util.ArrayList<>()).cargoY);
            l = ClassicHud.blockLayout(t, 114, true, true, false, new java.util.ArrayList<>());
            assertEquals(126, l.cargoY);
            assertEquals(147, l.colonyY);
            // Without a colony the list follows the last line 10 below (032).
            l = ClassicHud.blockLayout(t, 100, false, false, false, Arrays.asList(dragoon));
            assertEquals(110, l.entryY[0]);
            // The tile mode of clip005 #15391: Silver City under (Biber).
            final ClassicHud.ColonyFacts silver = new ClassicHud.ColonyFacts(null,
                "Silver City", null);
            final ClassicHud.UnitFacts galleon = new F("galleon", null).w(14).cargo(36, 30)
                .build();
            final ClassicHud.UnitFacts art = new F("artillery", null).w(14).build();
            final ClassicHud.TileFacts tile = new ClassicHud.TileFacts(17, 45,
                ClassicHud.REGION_UNKNOWN, true, null, 3, -1, "model.tile.mixedForest", 0,
                true, false, 9, null, new java.util.ArrayList<>(Arrays.asList(galleon, art,
                    art, art)), silver);
            assertEquals(96, last(ClassicHud.tileLines(t, tile)));
            l = ClassicHud.tileBlockLayout(t, tile);
            assertEquals(116, l.colonyY);
            assertEquals(142, l.entryY[0]);
            assertEquals(160, l.entryY[1]);
            assertEquals(178, l.entryY[2]);
            assertEquals(-1, l.entryY[3]);
            assertEquals(196, l.moreY);
            assertEquals(192, ClassicHud.tileLayout(t, tile)[0]);
        } finally {
            dir.delete();
        }
    }

    private static int last(List<ClassicHud.TextLine> lines) {
        int last = ClassicHud.NAME_Y;
        for (ClassicHud.TextLine l : lines) last = Math.max(last, l.y);
        return last;
    }

    /**
     * The paint: "+ Weiter +" only when an entry remains; the colony's
     * sprite at (242, N - 10) with its name over it; the goods icons from
     * x 259 two rows above "Mit:", 1 px apart, and in a list entry from
     * x 260 on the cell's top row.
     */
    public void testColonyAndCargoPaint() throws IOException {
        final File dir = Files.createTempDirectory("classic-hud-w").toFile();
        try {
            final ClassicText t = unitTexts(dir);
            final ClassicHud.UnitFacts ship = new F("merchantman", null).w(13)
                .terrain("model.tile.ocean").build();
            // Icons: solid 3x2 red, 4x2 green; a 21x16 sprite, solid blue.
            final BufferedImage red = solid(3, 2, 0xFFFF0000), green = solid(4, 2, 0xFF00FF00);
            final List<ClassicHud.GoodsIcon> goods = Arrays.asList(
                new ClassicHud.GoodsIcon(0, true, red), new ClassicHud.GoodsIcon(4, false, green));
            final ClassicHud.UnitFacts carrier = new ClassicHud.UnitFacts(ship.sprite,
                ship.fill, ship.dark, 3, ship.unitRow, "s", 3, 1, 1, ClassicHud.ORDERS_NONE,
                "model.tile.ocean", false, -1, false, ClassicHud.QUAL_NONE, -1, -1, null,
                0, false, -1, goods);
            final ClassicHud.ColonyFacts colony = new ClassicHud.ColonyFacts(
                solid(21, 16, 0xFF0000FF), "", goods);
            final BufferedImage img = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
            final Graphics2D g = img.createGraphics();
            ClassicHud.paintUnits(g, null, t, carrier, new java.util.ArrayList<>(), colony);
            g.dispose();
            // The ship: name 86, orders 93, (Oce) 100 -> "Mit:" 112, icons at 110.
            assertEquals(0xFF0000, rgb(img, 259, 110));
            assertEquals(0xFF0000, rgb(img, 261, 111));
            assertEquals(0x00FF00, rgb(img, 263, 110));   // 259 + 3 + 1
            assertEquals(0x00FF00, rgb(img, 266, 111));
            // The colony: N = 112 + 21 = 133, sprite at (242, 123), "Mit:" 148.
            assertEquals(0x0000FF, rgb(img, 242, 123));
            assertEquals(0x0000FF, rgb(img, 262, 138));
            assertEquals(0xFF0000, rgb(img, 259, 146));
            assertEquals(0x00FF00, rgb(img, 263, 146));
            // A carrier in the list: icons from 260 on the cell's top row.
            final BufferedImage li = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
            final Graphics2D lg = li.createGraphics();
            ClassicHud.paintUnits(lg, null, t, ship, Arrays.asList(carrier), null);
            lg.dispose();
            assertEquals(0xFF0000, rgb(li, 260, 110));
            assertEquals(0x00FF00, rgb(li, 264, 110));
        } finally {
            dir.delete();
        }
    }

    private static BufferedImage solid(int w, int h, int argb) {
        final BufferedImage b = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) b.setRGB(x, y, argb);
        }
        return b;
    }

    /**
     * The flag of the treasure, the artillery and the wagon train sits at
     * cell + (6,0) (fill x 249-253 for cell 242: clip005 #13993, #14364,
     * #14481, #14594, clip006 #9345); galleon and frigate keep + (9,0),
     * the merchantman the cell's top-left.
     */
    public void testFlagRingOfWagonArtilleryTreasure() {
        for (int row : new int[] { ClassicHud.UNIT_TREASURE, ClassicHud.UNIT_ARTILLERY,
                                   ClassicHud.UNIT_WAGON }) {
            assertEquals(new Rectangle(248, 68, 7, 9), ClassicHud.flagRing(242, 68, 14, row));
        }
        assertEquals(new Rectangle(251, 68, 7, 9),
                     ClassicHud.flagRing(242, 68, 14, ClassicHud.UNIT_GALLEON));
        assertEquals(new Rectangle(251, 68, 7, 9),
                     ClassicHud.flagRing(242, 68, 13, ClassicHud.UNIT_FRIGATE));
        assertEquals(new Rectangle(242, 68, 7, 9), ClassicHud.flagRing(242, 68, 13, 14));
        assertEquals(11, ClassicHud.unitRow("artillery", null));
        assertEquals(12, ClassicHud.unitRow("wagonTrain", null));
        assertEquals(10, ClassicHud.unitRow("treasureTrain", null));
    }

    /**
     * The colony sprite's flag: its #4159A6 pixels take the nation's fill,
     * its #34499E pixels the dark shade (Holland orange, V); France keeps
     * the sprite's blue (V, Quebec); nothing else changes; cached.
     */
    public void testColonyFlagRecolour() {
        final BufferedImage sp = new BufferedImage(21, 16, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < 11; i++) sp.setRGB(i, 0, 0xFF000000 | ClassicHud.COLONY_FLAG_RGB);
        for (int i = 0; i < 4; i++) sp.setRGB(i, 1, 0xFF000000 | ClassicHud.COLONY_FLAG_DARK_RGB);
        sp.setRGB(5, 5, 0xFF123456);
        final Game game = FreeColTestCase.getStandardGame();
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final Player french = game.getPlayerByNationId("model.nation.french");
        final BufferedImage nl = ClassicHud.colonyFlag(sp, dutch);
        assertNotSame(sp, nl);
        assertSame(nl, ClassicHud.colonyFlag(sp, dutch));
        for (int i = 0; i < 11; i++) assertEquals(0xFF7100, rgb(nl, i, 0));
        for (int i = 0; i < 4; i++) assertEquals(0xAA4900, rgb(nl, i, 1));
        assertEquals(0x123456, rgb(nl, 5, 5));
        assertEquals(0, nl.getRGB(20, 15) >>> 24);
        assertSame(sp, ClassicHud.colonyFlag(sp, french));
        assertNull(ClassicHud.colonyFlag(null, dutch));
    }

    /**
     * The facts of live units: a treasure's amount, a goto to a colony
     * names it, a wagon with 150 cotton and 40 furs has one icon per hold
     * by goods type, the full hold first; a colony's "Mit:" row from its
     * warehouse, coloured from 100, at most five.
     */
    public void testUnitAndColonyFactsOfLiveUnits() {
        final Game game = FreeColTestCase.getStandardGame();
        final net.sf.freecol.common.model.Specification spec = FreeColTestCase.spec();
        final net.sf.freecol.common.model.Map map = FreeColTestCase.getTestMap(
            spec.getTileType("model.tile.plains"), true);
        game.changeMap(map);
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final net.sf.freecol.common.model.Colony colony
            = net.sf.freecol.util.test.FreeColTestUtils.getColonyBuilder().player(dutch)
                .colonyName("Base").colonyTile(map.getTile(5, 8)).build();
        final java.util.function.IntFunction<BufferedImage> icons
            = n -> new BufferedImage(n % 7 + 3, 9, BufferedImage.TYPE_INT_ARGB);
        // A treasure train with 10000 gold.
        final net.sf.freecol.common.model.Unit gold = new net.sf.freecol.server.model.ServerUnit(
            game, map.getTile(6, 8), dutch, spec.getUnitType("model.unit.treasureTrain"));
        gold.setTreasureAmount(10000);
        ClassicHud.UnitFacts f = ClassicHud.UnitFacts.of(gold, null, icons);
        assertEquals(10000, f.treasure);
        assertEquals(ClassicHud.UNIT_TREASURE, f.unitRow);
        // A wagon train going to Base, with 150 cotton: a full and a part hold.
        final net.sf.freecol.common.model.Unit wagon = new net.sf.freecol.server.model.ServerUnit(
            game, map.getTile(7, 8), dutch, spec.getUnitType("model.unit.wagonTrain"));
        wagon.setDestination(colony);
        wagon.addGoods(spec.getGoodsType("model.goods.cotton"), 150);
        f = ClassicHud.UnitFacts.of(wagon, null, icons);
        assertEquals(ClassicHud.ORDERS_GOTO, f.ordersRow);
        assertEquals("Base", f.destination);
        assertEquals("[025, 041]", f.cargo.toString());
        assertNotNull(f.cargo.get(0).icon);
        assertEquals(-1, f.treasure);
        // Without a picture lookup: the icons' rows, no pictures.
        assertNull(ClassicHud.UnitFacts.of(wagon, null).cargo.get(0).icon);
        // A galleon's holds by goods type, a full hold before a part one.
        final net.sf.freecol.common.model.Unit galleon
            = new net.sf.freecol.server.model.ServerUnit(game, null, dutch,
                spec.getUnitType("model.unit.galleon"));
        galleon.addGoods(spec.getGoodsType("model.goods.ore"), 100);
        galleon.addGoods(spec.getGoodsType("model.goods.furs"), 40);
        galleon.addGoods(spec.getGoodsType("model.goods.cotton"), 150);
        assertEquals("[025, 041, 042, 028]",
                     ClassicHud.UnitFacts.of(galleon, null, icons).cargo.toString());
        assertNull(ClassicHud.UnitFacts.of(galleon, null, icons).destination);
        assertFalse(ClassicHud.UnitFacts.of(galleon, null, icons).homePort);
        // A goto to Europe (R2): no colony name, the home-port line.
        galleon.setDestination(dutch.getEurope());
        f = ClassicHud.UnitFacts.of(galleon, null, icons);
        assertEquals(ClassicHud.ORDERS_GOTO, f.ordersRow);
        assertNull(f.destination);
        assertTrue(f.homePort);
        assertFalse(ClassicHud.UnitFacts.of(wagon, null, icons).homePort);
        // The colony's row: 7 goods in store, the first five by row.
        for (String[] g : new String[][] { { "food", "120" }, { "sugar", "5" },
                { "furs", "100" }, { "lumber", "40" }, { "ore", "99" }, { "horses", "60" },
                { "muskets", "200" } }) {
            colony.addGoods(spec.getGoodsType("model.goods." + g[0]), Integer.parseInt(g[1]));
        }
        final ClassicHud.ColonyFacts c = ClassicHud.ColonyFacts.of(colony, null, icons);
        assertEquals("Base", c.name);
        assertEquals("[022, 039, 026, 043, 044]", c.goods.toString());
        assertNull(c.sprite);
    }

    // The golden check of the unit block against the clips (build spec W21b).

    /** One golden frame: the clip, the frame, its stored file, what the panel shows. */
    private static final class Golden {
        final String clip;
        final int frame, stored;
        ClassicHud.UnitFacts active;
        List<ClassicHud.UnitFacts> list = new java.util.ArrayList<>();
        ClassicHud.ColonyFacts colony;
        ClassicHud.TileFacts tile;
        int word = -1;

        Golden(String clip, int frame, int stored) {
            this.clip = clip;
            this.frame = frame;
            this.stored = stored;
        }

        Golden active(F f) { this.active = f.build(); return this; }
        Golden list(F... fs) {
            for (F f : fs) this.list.add(f.build());
            return this;
        }
        Golden colony(ClassicHud.ColonyFacts c) { this.colony = c; return this; }
    }

    /** An ICONS.SS frame of the pack. */
    private static BufferedImage icon(ClassicPackFiles pack, int n) {
        return pack.image(ClassicPackFiles.ssKey(String.format("ICONS.SS.%03d", n)));
    }

    /** The goods icons of these ICONS.SS frames (022-037 coloured, 038-053 grey). */
    private static List<ClassicHud.GoodsIcon> goods(ClassicPackFiles pack, int... frames) {
        final List<ClassicHud.GoodsIcon> out = new java.util.ArrayList<>();
        for (int n : frames) {
            final boolean full = n < ClassicHud.ICON_GOODS_GREY;
            out.add(new ClassicHud.GoodsIcon(n - (full ? ClassicHud.ICON_GOODS
                : ClassicHud.ICON_GOODS_GREY), full, icon(pack, n)));
        }
        return out;
    }

    /** A Dutch colony as the panel shows it: ICONS.SS sprite, flag recoloured. */
    private static ClassicHud.ColonyFacts colony(ClassicPackFiles pack, Game game, int sprite,
                                                 String name, int... goods) {
        return new ClassicHud.ColonyFacts(ClassicHud.colonyFlag(icon(pack, sprite),
            game.getPlayerByNationId("model.nation.dutch")), name, goods(pack, goods));
    }

    /**
     * The frames, their facts read off the frames (the panel's words,
     * G2Rows/G2Goods/G2Best in the G2 spec), the sprites by number.
     */
    private static List<Golden> goldenFrames(ClassicPackFiles pack, Game game) {
        final int none = ClassicHud.ORDERS_NONE, sentry = ClassicHud.ORDERS_SENTRY,
            fortified = ClassicHud.ORDERS_FORTIFIED;
        final java.util.function.IntFunction<BufferedImage> s = n -> icon(pack, n);
        final java.util.function.Supplier<F> ship = () -> new F("merchantman", null)
            .sprite(s.apply(6)).terrain("model.tile.ocean");
        final java.util.function.Supplier<F> scout = () -> new F("seasonedScout", "scout")
            .sprite(s.apply(103));
        final java.util.function.Supplier<F> vet = () -> new F("veteranSoldier", "soldier")
            .sprite(s.apply(102));
        final java.util.function.Supplier<F> farmer = () -> new F("expertFarmer", null)
            .sprite(s.apply(81));
        final java.util.function.Supplier<F> art = () -> new F("artillery", null)
            .sprite(s.apply(9));
        final java.util.function.Supplier<F> wagon = () -> new F("wagonTrain", null)
            .sprite(s.apply(8));
        final java.util.function.Supplier<F> gold = () -> new F("treasureTrain", null)
            .sprite(s.apply(16)).treasure(10000);
        final java.util.function.Supplier<F> galleon = () -> new F("galleon", null)
            .sprite(s.apply(7)).cargo(goods(pack, 36, 30, 30, 30, 26));
        final java.util.function.Supplier<F> soldier = () -> new F("freeColonist", "soldier")
            .sprite(s.apply(74));
        final List<Golden> out = new java.util.ArrayList<>();
        // clip007: the ship and its passengers, the scout and the farmer.
        out.add(new Golden("clip007", 723, 723)
            .active(ship.get().moves(15).at(44, 53).terrain("model.tile.highSeas"))
            .list(scout.get().orders(sentry), farmer.get().orders(sentry)));
        out.add(new Golden("clip007", 3107, 3107)
            .active(scout.get().moves(12).at(48, 45).terrain("model.tile.ocean"))
            .list(ship.get()));
        out.add(new Golden("clip007", 4281, 4281)
            .active(ship.get().moves(6).at(48, 45)).list(vet.get().orders(sentry)));
        out.add(new Golden("clip007", 5093, 5093)
            .active(ship.get().moves(15).at(50, 45))
            .list(new F("expertFurTrapper", "pioneer").sprite(s.apply(73)).tools(100)
                      .orders(sentry), vet.get().orders(sentry)));
        out.add(new Golden("clip007", 6643, 6643)
            .active(scout.get().moves(12).at(48, 44).terrain("model.tile.mixedForest")));
        out.add(new Golden("clip007", 6989, 6989)
            .active(ship.get().moves(15).at(50, 45))
            .list(scout.get().orders(sentry), farmer.get()));
        out.add(new Golden("clip007", 1306, 1306)
            .active(new F("expertFurTrapper", "pioneer").sprite(s.apply(73)).tools(100)
                    .at(50, 44).terrain("model.tile.rainForest")));
        out.add(new Golden("clip007", 1449, 1449)
            .active(vet.get().at(49, 45).terrain("model.tile.coniferForest")
                    .extras(0, false, false, 10)));
        out.add(new Golden("clip007", 5804, 5804)
            .active(farmer.get().at(49, 44).terrain("model.tile.coniferForest")));
        // clip005: the treasure, the artillery's goto, the colonies.
        out.add(new Golden("clip005", 13993, 13993)
            .active(vet.get().at(27, 19).terrain("model.tile.broadleafForest"))
            .list(gold.get()));
        out.add(new Golden("clip005", 14594, 14594)
            .active(gold.get().at(27, 19).terrain("model.tile.broadleafForest"))
            .list(vet.get()));
        out.add(new Golden("clip005", 14481, 14481)
            .active(art.get().at(14, 55).dest("Fur Town").terrain("model.tile.mixedForest")
                    .extras(1, true, false, -1))
            .list(wagon.get()));
        out.add(new Golden("clip005", 14827, 14827)
            .active(soldier.get().at(38, 56).dest("Base").terrain("model.tile.hills")
                    .extras(0, true, false, -1))
            .list(new F("masterBlacksmith", null).sprite(s.apply(95)).dest("Base")));
        out.add(new Golden("clip005", 14364, 14364)
            .active(new F("expertFurTrapper", null).sprite(s.apply(85)).at(30, 60)
                    .terrain("model.tile.broadleafForest").extras(0, true, false, -1))
            .colony(colony(pack, game, 3, "Fur City", 50, 30, 38, 43))
            .list(farmer.get(), art.get().orders(fortified)));
        out.add(new Golden("clip005", 14852, 14852)
            .active(soldier.get().moves(2).at(37, 55).terrain("model.tile.plains")
                    .extras(0, true, true, -1))
            .colony(colony(pack, game, 2, "Base", 50, 48, 49, 25, 30))
            .list(new F("expertLumberJack", "dragoon").sprite(s.apply(76)), farmer.get()));
        // clip006: the same artillery, Base again, Cotton Town, Northern Sugar ...
        out.add(new Golden("clip006", 4716, 4716)
            .active(art.get().at(14, 55).dest("Fur Town").terrain("model.tile.mixedForest")
                    .extras(1, true, false, -1))
            .list(wagon.get()));
        out.add(new Golden("clip006", 5135, 5127)
            .active(soldier.get().moves(2).at(37, 55).terrain("model.tile.plains")
                    .extras(0, true, true, -1))
            .colony(colony(pack, game, 2, "Base", 50, 30, 48, 49, 25))
            .list(new F("expertLumberJack", "dragoon").sprite(s.apply(76)), farmer.get()));
        out.add(new Golden("clip006", 9345, 9343)
            .active(wagon.get().moves(6).at(46, 57).terrain("model.tile.prairie")
                    .extras(0, true, true, -1).cargo(goods(pack, 25)))
            .colony(colony(pack, game, 1, "Cotton Town", 30, 49, 25, 38, 27))
            .list(wagon.get().cargo(goods(pack, 25, 25)), farmer.get()));
        out.add(new Golden("clip006", 5351, 5351)
            .active(new F("hardyPioneer", "pioneer").sprite(s.apply(101)).tools(100)
                    .at(18, 36).terrain("model.tile.grassland").extras(0, true, true, -1))
            .colony(colony(pack, game, 3, "Northern Sugar", 47, 27, 40, 38))
            .list(new F("masterFurTrader", "dragoon").sprite(s.apply(76))
                      .orders(fortified)));
        out.add(new Golden("clip006", 5517, 5517)
            .active(new F("freeColonist", null).sprite(s.apply(100)).at(33, 55)
                    .terrain("model.tile.grassland").extras(0, true, true, -1))
            .colony(colony(pack, game, 0, "Horse Town", 30, 50, 38, 40, 37))
            .list(art.get().orders(fortified), farmer.get()));
        out.add(new Golden("clip006", 5597, 5597)
            .active(art.get().at(17, 45).terrain("model.tile.mixedForest")
                    .extras(0, true, false, 8))
            .colony(colony(pack, game, 2, "Silver City", 50, 47, 30, 28, 26))
            .list(galleon.get(), art.get().orders(fortified), farmer.get()));
        // clip008: a ship's holds, a pioneer and a ship in Base.
        out.add(new Golden("clip008", 29463, 29463)
            .active(ship.get().moves(12).at(47, 47).extras(0, false, false, 7)
                    .cargo(goods(pack, 36))));
        out.add(new Golden("clip008", 32928, 32928)
            .active(ship.get().moves(0).at(49, 46).cargo(goods(pack, 44, 42))));
        out.add(new Golden("clip008", 35590, 35590)
            .active(ship.get().moves(15).at(49, 45).terrain("model.tile.coniferForest")
                    .extras(0, true, false, 10).cargo(goods(pack, 44, 42)))
            .colony(colony(pack, game, 3, "Base", 42, 38, 36, 53)));
        out.add(new Golden("clip008", 4009, 4009)
            .active(new F("expertFurTrapper", "pioneer").sprite(s.apply(73)).tools(100)
                    .moves(0).at(49, 45).terrain("model.tile.coniferForest")
                    .extras(0, true, false, 10))
            .colony(colony(pack, game, 3, "Base", 53)));
        // The tile mode (Spielzugende) on Silver City, clip005 #15391.
        final Golden c15391 = new Golden("clip005", 15391, 15391);
        c15391.tile = new ClassicHud.TileFacts(17, 45, 2, true, null, 3, -1,
            "model.tile.mixedForest", 0, true, false, 8, null,
            new java.util.ArrayList<>(Arrays.asList(galleon.get().build(), art.get().build(),
                art.get().orders(fortified).build(), farmer.get().build())),
            colony(pack, game, 2, "Silver City", 47, 50, 45, 28, 26));
        c15391.word = ClassicHud.PROMPT_ON_RGB;
        out.add(c15391);
        // The tile mode on Prov., dago-colony2 #4045 (no word).
        final Golden prov = new Golden("dago-colony2", 4045, 4045);
        prov.tile = new ClassicHud.TileFacts(28, 20, 2, true, null, 3, -1,
            "model.tile.mixedForest", 0, true, false, -1, null, null,
            colony(pack, game, 3, "Prov.", 42, 38, 49, 46));
        out.add(prov);
        return out;
    }

    /**
     * The golden check (build spec W21b, G2 spec 4.7): each frame's panel
     * below the status lines (x 241-319, y 64-199), drawn from hand-built
     * facts with the pack's FONTTINY, texts, wood and sprites, must give
     * the frame's pixels, 0 px off.  The original's mouse arrow (frame
     * indices 0, 7, 15 where we differ, in one cluster at most 12x20) is
     * counted and excused.  Skipped without -Dclassic.clips or the pack.
     */
    public void testPanelGoldenAgainstTheClips() throws Exception {
        final String clips = System.getProperty(ClassicTerrainGoldenTest.CLIPS_PROPERTY);
        if (clips == null || !new File(clips).isDirectory()) {
            System.err.println("testPanelGoldenAgainstTheClips skipped: no recordings (-D"
                + ClassicTerrainGoldenTest.CLIPS_PROPERTY + ")");
            return;
        }
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final ClassicText t = (pack == null) ? null : ClassicText.load(pack);
        final ClassicFont tiny = (pack == null) ? null : pack.font(ClassicFont.TINY);
        final BufferedImage wood = (pack == null) ? null : pack.image(ClassicMenuBar.WOOD_KEY);
        if (t == null || tiny == null || wood == null || icon(pack, 6) == null) {
            System.err.println("testPanelGoldenAgainstTheClips skipped: no pack (ant classic-assets)");
            return;
        }
        final Game game = FreeColTestCase.getStandardGame();
        final StringBuilder fails = new StringBuilder(), report = new StringBuilder();
        int compared = 0;
        for (Golden c : goldenFrames(pack, game)) {
            final File f = new File(new File(clips, c.clip),
                                    String.format("frame_%06d.png", c.stored));
            final BufferedImage frame = javax.imageio.ImageIO.read(f);
            assertNotNull(f.toString(), frame);
            final BufferedImage canvas = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
            final Graphics2D g = canvas.createGraphics();
            ClassicHud.paintChrome(g, wood);
            if (c.tile != null) {
                ClassicHud.paintTileMode(g, tiny, t, c.tile, c.word);
            } else {
                ClassicHud.paintUnits(g, tiny, t, c.active, c.list, c.colony);
            }
            g.dispose();
            final java.awt.image.Raster idx
                = (frame.getColorModel() instanceof java.awt.image.IndexColorModel)
                ? frame.getRaster() : null;
            int diff = 0, arrow = 0, ax0 = 999, ay0 = 999, ax1 = -1, ay1 = -1;
            final StringBuilder where = new StringBuilder();
            for (int y = 64; y < 200; y++) {
                for (int x = 241; x < 320; x++) {
                    compared++;
                    final int want = frame.getRGB(x, y) & 0xFFFFFF;
                    final int got = canvas.getRGB(x, y) & 0xFFFFFF;
                    if (want == got) continue;
                    final int i = (idx == null) ? -1 : idx.getSample(x, y, 0);
                    if (i == 0 || i == 7 || i == 15) {
                        arrow++;
                        ax0 = Math.min(ax0, x);
                        ay0 = Math.min(ay0, y);
                        ax1 = Math.max(ax1, x);
                        ay1 = Math.max(ay1, y);
                    } else {
                        if (diff < 6) {
                            where.append(String.format(" (%d,%d) %06X/%06X", x, y, want, got));
                        }
                        diff++;
                    }
                }
            }
            final boolean cluster = arrow == 0 || (ax1 - ax0 < 12 && ay1 - ay0 < 20);
            report.append(String.format("%s #%d: %d px off, arrow %d%s%n", c.clip, c.frame,
                diff, arrow, (arrow == 0) ? ""
                    : String.format(" in (%d,%d)-(%d,%d)", ax0, ay0, ax1, ay1)));
            if (diff > 0 || !cluster) {
                fails.append(c.clip).append(" #").append(c.frame).append(": ").append(diff)
                    .append(" px off").append(where)
                    .append(cluster ? "" : ", excused pixels spread").append('\n');
            }
        }
        System.err.print("testPanelGoldenAgainstTheClips (" + compared + " px):\n" + report);
        assertEquals(fails.toString(), "", fails.toString());
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
        // No-op seams never fire (T: the trade route panel); G is the
        // destination list now (R2).
        assertNull(ClassicKeyMap.pick(find("T"), id -> true));
        assertEquals("gotoAction", ClassicKeyMap.pick(find("G"), id -> true));
        assertNull(ClassicKeyMap.pick(find("G"), id -> false));
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

    /**
     * The original's keys are inert while the player waits (build spec
     * W5d; FINAL "Open" item 7): in the pauses and the AI phase F, S, the
     * reports ... do nothing, as the map's own keys; Ctrl+N (FreeCol's way
     * back to the title, which asks first) still works.
     */
    public void testKeysWhileWaiting() {
        assertFalse(ClassicKeyMap.waitAllows(find("F"), true));
        assertTrue(ClassicKeyMap.waitAllows(find("F"), false));
        assertFalse(ClassicKeyMap.waitAllows(find("F7"), true));
        assertFalse(ClassicKeyMap.waitAllows(find("P"), true));
        assertTrue(ClassicKeyMap.waitAllows(find("control N"), true));
        for (ClassicKeyMap.Binding b : ClassicKeyMap.bindings()) {
            assertTrue(b.toString(), ClassicKeyMap.waitAllows(b, false));
            assertEquals(b.toString(), b.actionIds.contains("newAction"),
                         ClassicKeyMap.waitAllows(b, true));
        }

        // The installed bindings: the action fires only while not waiting.
        final javax.swing.JPanel host = new javax.swing.JPanel();
        final java.util.List<String> fired = new java.util.ArrayList<>();
        final java.util.Map<String, javax.swing.Action> actions = new java.util.HashMap<>();
        for (String id : new String[] { "fortifyAction", "newAction" }) {
            actions.put(id, new javax.swing.AbstractAction() {
                    @Override
                    public void actionPerformed(java.awt.event.ActionEvent e) {
                        fired.add(id);
                    }
                });
        }
        final boolean[] waiting = { true };
        ClassicKeyMap.install(host, null, actions::get,
            () -> net.sf.freecol.client.gui.GUI.ViewMode.MOVE_UNITS,
            () -> ClassicMenuModel.Context.NONE, () -> false, () -> waiting[0]);
        final javax.swing.InputMap im = host.getInputMap(
            javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        final javax.swing.Action f = host.getActionMap().get(
            im.get(javax.swing.KeyStroke.getKeyStroke("F")));
        final javax.swing.Action n = host.getActionMap().get(
            im.get(javax.swing.KeyStroke.getKeyStroke("control N")));
        final java.awt.event.ActionEvent e = new java.awt.event.ActionEvent(host,
            java.awt.event.ActionEvent.ACTION_PERFORMED, "key");
        f.actionPerformed(e);
        n.actionPerformed(e);
        assertEquals("[newAction]", fired.toString());
        waiting[0] = false;
        f.actionPerformed(e);
        assertEquals("[newAction, fortifyAction]", fired.toString());
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
