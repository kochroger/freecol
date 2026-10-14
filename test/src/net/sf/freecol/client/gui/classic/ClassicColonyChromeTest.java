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
import java.awt.image.IndexColorModel;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Turn;
import net.sf.freecol.common.resources.ImageCache;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * The colony screen's chrome and warehouse (build spec M4.1, {@link
 * ClassicColonyChrome}) against clip 020, region by region: the state of
 * each reference frame is rebuilt in a test game (the colony's name, the
 * year, the gold, the 16 goods, the selected good), the screen paints its
 * canvas as in the game ({@link ClassicColonyPanel#canvas}), and every
 * pixel of the regions M4.1 owns is compared with the frame: 0 differing
 * pixels.  The original arrow is found in the frame and left out (named
 * per region).  Where a region carries what a later step of M4 draws (the
 * buildings on the parchment, the band's three panels), our chrome alone
 * is compared, and the pixels the original draws over it -- the
 * analysis' overlay models ({@code support/layout/ov_*.txt}), a whole
 * panel where no model exists -- are left out and counted.  Skips without
 * the pack or the recordings ({@code -Dclassic.clips}).
 */
public class ClassicColonyChromeTest extends FreeColTestCase {

    /** One reference frame's state (spec §1.1 and the analysis' L §2, §8). */
    private static final class Ref {
        final int frame;
        final String name;
        final int year, gold, selected;
        final int[] amounts;
        /** The band's overlay models of the frame (others: their panel left out). */
        final String left, port, right, buildings;

        Ref(int frame, String name, int year, int gold, int selected, int[] amounts,
            String left, String port, String right, String buildings) {
            this.frame = frame;
            this.name = name;
            this.year = year;
            this.gold = gold;
            this.selected = selected;
            this.amounts = amounts;
            this.left = left;
            this.port = port;
            this.right = right;
            this.buildings = buildings;
        }
    }

    /**
     * The five reference frames of M4.1 (V: their top lines and
     * warehouses read off the frames; the selected good is the box with
     * the green frame).
     */
    private static final Ref[] REFS = {
        new Ref(2260, "Base", 1539, 170, 0,
                new int[] { 4, 78, 0, 0, 9, 20, 24, 0, 0, 9, 0, 0, 0, 0, 10, 50 },
                "ov_base_2260_leftpanel.txt", "ov_base_2260_port.txt",
                "ov_base_2260_rightpanel.txt", "ov_base_2260_buildings.txt"),
        new Ref(14817, "Provisorische Kolonie", 1541, 170, 9,
                new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 50, 0, 0, 0, 0, 0, 0, 0 },
                "ov_prov_14817_leftpanel.txt", null, "ov_prov_14817_rightpanel.txt",
                "ov_prov_14817_buildings.txt"),
        new Ref(123003, "Silver City", 1566, 348, 4,
                new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 50, 0, 0, 0, 0, 0, 0, 0 },
                null, null, "ov_silver_123003_rightpanel.txt",
                "ov_silver_123003_buildings.txt"),
        new Ref(134173, "Base", 1571, 598, 4,
                new int[] { 42, 91, 6, 0, 0, 16, 67, 0, 0, 107, 0, 0, 0, 0, 15, 50 },
                "ov_base_134173_leftpanel.txt", null, null,
                "ov_base_134173_buildings.txt"),
        new Ref(118448, "Base", 1566, 348, 9,
                new int[] { 37, 70, 6, 0, 18, 36, 42, 0, 0, 121, 0, 0, 0, 0, 75, 50 },
                null, "ov_base_118448_port.txt", null, null),
    };

    /** The regions the whole screen's canvas is compared in (spec §1.1). */
    private static final Rectangle TOP = ClassicColonyLayout.TOP_LINE;
    private static final Rectangle SEP_ROW = new Rectangle(0, 7, 320, 1);
    private static final Rectangle SEP_COL = new Rectangle(199, 8, 1, 120);
    private static final Rectangle WOOD = ClassicColonyLayout.WOOD_PANEL;
    private static final Rectangle BAND_ROW = new Rectangle(0, 128, 320, 1);
    private static final Rectangle WAREHOUSE = new Rectangle(0, 179, 305, 21);
    private static final Rectangle EXIT = ClassicColonyLayout.EXIT;

    /** The parts of the band whose contents are later steps' (M4.5-M4.7): left out where no model says. */
    private static final Rectangle LEFT_ALL = new Rectangle(0, 129, 120, 49);
    private static final Rectangle PORT_ALL = new Rectangle(120, 129, 87, 49);
    private static final Rectangle RIGHT_ALL = new Rectangle(207, 129, 96, 49);
    private static final Rectangle BUTTONS = new Rectangle(303, 129, 17, 49);


    /** A client of the test's game: its game and our player, nothing else. */
    private static FreeColClient client(Game game, Player me) throws Exception {
        final Class<?> uc = Class.forName("sun.misc.Unsafe");
        final Field theUnsafe = uc.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        final FreeColClient fcc = (FreeColClient)uc
            .getMethod("allocateInstance", Class.class)
            .invoke(theUnsafe.get(null), FreeColClient.class);
        fcc.setGame(game);
        fcc.setMyPlayer(me);
        return fcc;
    }

    /** Any picture for every key: the later steps' painters draw blank art. */
    private static ImageLibrary library() {
        return new ImageLibrary(1f, new ImageCache() {
                @Override
                public BufferedImage getScaledImage(String key, float scale,
                                                    boolean grayscale) {
                    return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                }

                @Override
                public BufferedImage getSizedImage(String key, java.awt.Dimension size,
                                                   boolean grayscale, int seed) {
                    return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                }
            });
    }

    /** The turn of a year (before 1600 FreeCol has one turn a year, spring in the original). */
    private static Turn turnOf(int year) {
        for (int n = 1; n < 1000; n++) {
            final Turn t = new Turn(n);
            if (t.getYear() == year) return t;
        }
        throw new IllegalArgumentException("no turn of " + year);
    }

    /** A test colony in the state of a reference frame. */
    private Colony colonyOf(Game game, Ref r) {
        final Colony colony = createStandardColony(2);
        colony.setName(r.name);
        // No current build: the first slice's construction plate (M4.2
        // removes it) then needs no FreeCol picture, which a headless
        // test cannot make.
        colony.setBuildQueue(new ArrayList<>());
        game.setTurn(turnOf(r.year));
        colony.getOwner().setGold(r.gold);
        for (int i = 0; i < ClassicColonyLayout.WAREHOUSE_BOXES; i++) {
            final GoodsType gt = spec().getGoodsType(ClassicColonyLayout.WAREHOUSE_GOODS[i]);
            colony.removeGoods(gt);
            if (r.amounts[i] > 0) colony.addGoods(gt, r.amounts[i]);
        }
        return colony;
    }


    /**
     * The warehouse's goods are the rules' 16 storable goods, in the
     * original's order (ICONS.SS.022 food ... 037 muskets): the classic
     * rules have each of them, storable.
     */
    public void testTheWarehouseGoodsAreTheRulesStorableGoods() {
        final List<GoodsType> storable = spec().getStorableGoodsTypeList();
        assertEquals(ClassicColonyLayout.WAREHOUSE_BOXES, storable.size());
        for (int i = 0; i < ClassicColonyLayout.WAREHOUSE_BOXES; i++) {
            final GoodsType gt = spec().getGoodsType(ClassicColonyLayout.WAREHOUSE_GOODS[i]);
            assertNotNull(ClassicColonyLayout.WAREHOUSE_GOODS[i], gt);
            assertTrue(gt.getId(), gt.isStorable());
            assertEquals("the rules' order is the original's", storable.get(i), gt);
        }
    }

    /**
     * The chrome's model of a test colony: the top line with the season
     * before 1600 as spring, the gold of our player, the 16 amounts, the
     * selected box.
     */
    public void testTheModelOfATestColony() throws Exception {
        final ClassicText text = ClassicText.load(ClassicPackFiles.runtime());
        if (text == null) {
            System.err.println(getName() + ": skipped, no pack texts (ant classic-assets)");
            return;
        }
        final Game game = getStandardGame();
        final Map map = getTestMap(true);
        game.changeMap(map);
        final Colony colony = colonyOf(game, REFS[0]);
        final ClassicColonyChrome.Model m = ClassicColonyChrome.model(colony, game,
            colony.getOwner(), text, 0);
        assertEquals("Base.   Frühjahr, 1539.  Gold: 170$", m.topLine);
        assertEquals(java.util.Arrays.toString(REFS[0].amounts),
                     java.util.Arrays.toString(m.amounts));
        assertEquals(0, m.selected);
        // After 1600 the season is FreeCol's.
        game.setTurn(new Turn(turnOf(1600).getNumber() + 1));
        final ClassicColonyChrome.Model m2 = ClassicColonyChrome.model(colony, game,
            colony.getOwner(), text, 3);
        assertTrue(m2.topLine, m2.topLine.startsWith("Base.   Herbst, 1600.  Gold: 170$")
                   || m2.topLine.startsWith("Base.   Frühjahr, 1601.  Gold: 170$"));
        assertEquals(3, m2.selected);
        // Without a game (a test view): no year, still no failure.
        assertTrue(ClassicColonyChrome.model(colony, null, null, text, 0)
                   .topLine.startsWith("Base.   Frühjahr, 0."));
        // No custom house: nothing exported (and no export setting made).
        final GoodsType rum = spec().getGoodsType("model.goods.rum");
        assertFalse(java.util.Arrays.toString(m.exported), contains(m.exported, true));
        // A custom house exporting rum: its digits green (dago, I).
        colony.addBuilding(new net.sf.freecol.server.model.ServerBuilding(game, colony,
            spec().getBuildingType("model.building.customHouse")));
        assertTrue(colony.hasAbility(net.sf.freecol.common.model.Ability.EXPORT));
        colony.getExportData(rum).setExported(true);
        final boolean[] ex = ClassicColonyChrome.exported(colony);
        for (int i = 0; i < ex.length; i++) {
            assertEquals(ClassicColonyLayout.WAREHOUSE_GOODS[i],
                         ClassicColonyLayout.WAREHOUSE_GOODS[i].equals(rum.getId()), ex[i]);
        }
    }

    private static boolean contains(boolean[] a, boolean v) {
        for (boolean b : a) if (b == v) return true;
        return false;
    }

    /**
     * Golden (M4.1 acceptance): the top line, the separators, the wood
     * panel with the 3x3 map's frame, the band's black row, the warehouse
     * and the exit as the screen paints them, and the parchment and the
     * band picture as the chrome paints them, against clip 020 #2260,
     * #14817, #123003, #134173 and #118448: 0 differing pixels in every
     * region (the arrow and what later steps draw left out and counted).
     */
    public void testTheChromeAgainstClip020() throws Exception {
        final ClassicColonyGolden gold = ClassicColonyGolden.open(getName());
        if (gold == null) return;
        final ClassicColonyChrome.Assets a = ClassicColonyChrome.Assets.load(gold.pack);
        assertTrue("the pack has every piece", a.complete());
        final StringBuilder report = new StringBuilder();
        final StringBuilder fails = new StringBuilder();
        for (Ref r : REFS) {
            final Game game = getStandardGame();
            game.changeMap(getTestMap(true));
            final Colony colony = colonyOf(game, r);
            ClassicColonyPanel.selectWarehouseGood(r.selected);
            final ClassicColonyPanel panel = new ClassicColonyPanel(
                client(game, colony.getOwner()), library(), colony, null, null,
                ClassicSlide.SYSTEM, false);
            final BufferedImage canvas = panel.canvas();
            final int[] ours = ClassicColonyGolden.pixels(canvas);
            final BufferedImage frame = gold.frame(r.frame);
            dump(canvas, frame, r.frame);
            final int[] theirs = ClassicColonyGolden.pixels(frame);
            final ClassicColonyGolden.Mask arrow = gold.pointer(frame);
            // Our chrome alone, for the regions later steps fill.
            final BufferedImage chromeOnly = new BufferedImage(320, 200,
                BufferedImage.TYPE_INT_RGB);
            final Graphics2D g = chromeOnly.createGraphics();
            ClassicColonyChrome.paintGround(g, a);
            ClassicColonyChrome.paintBand(g, a);
            g.dispose();
            final int[] chrome = ClassicColonyGolden.pixels(chromeOnly);
            // What the original draws on the parchment and the band.
            final ClassicColonyGolden.Mask ground = gold.content(r.buildings,
                ClassicColonyLayout.BUILDING_AREA);
            final ClassicColonyGolden.Mask band = new ClassicColonyGolden.Mask();
            unite(band, gold.content(r.left, LEFT_ALL), LEFT_ALL);
            unite(band, gold.content(r.port, PORT_ALL), PORT_ALL);
            unite(band, gold.content(r.right, RIGHT_ALL), RIGHT_ALL);
            band.rect(BUTTONS);
            // A carrier's flag box in port, which the port models leave
            // out (the wagon train's on top at (136,147)-(142,151), spec
            // §M4.6 target 2; its model masks 136-141 x 147-150 only).
            if (r.port != null) band.rect(136, 147, 142, 151);
            // The 3x3 map's tiles are M4.4's.
            final ClassicColonyGolden.Mask tiles = new ClassicColonyGolden.Mask();
            tiles.rect(ClassicColonyLayout.TILES_X, ClassicColonyLayout.TILES_Y,
                       ClassicColonyLayout.TILES_X + 3 * ClassicColonyLayout.TILE - 1,
                       ClassicColonyLayout.TILES_Y + 3 * ClassicColonyLayout.TILE - 1);
            final List<ClassicColonyGolden.Diff> ds = new ArrayList<>();
            ds.add(ClassicColonyGolden.compare("top line", ours, theirs, TOP, arrow, null));
            ds.add(ClassicColonyGolden.compare("separator y7", ours, theirs, SEP_ROW, arrow, null));
            ds.add(ClassicColonyGolden.compare("separator x199", ours, theirs, SEP_COL, arrow, null));
            ds.add(ClassicColonyGolden.compare("wood panel", ours, theirs, WOOD, arrow, tiles));
            if (ground != null) {
                ds.add(ClassicColonyGolden.compare("parchment", chrome, theirs,
                    ClassicColonyLayout.BUILDING_AREA, arrow, ground));
                // The screen's own ground where its first-slice buildings
                // never draw: below their last row (y 122-127) and left of
                // their first column (x 0-3, under the construction plate).
                ds.add(ClassicColonyGolden.compare("ground (screen)", ours, theirs,
                    new Rectangle(0, 122, 199, 6), arrow, ground));
                ds.add(ClassicColonyGolden.compare("ground left", ours, theirs,
                    new Rectangle(0, 19, 4, 103), arrow, ground));
            }
            ds.add(ClassicColonyGolden.compare("band row y128", ours, theirs, BAND_ROW, arrow, null));
            ds.add(ClassicColonyGolden.compare("band picture", chrome, theirs,
                new Rectangle(0, 129, 320, 50), arrow, band));
            ds.add(ClassicColonyGolden.compare("warehouse", ours, theirs, WAREHOUSE, arrow, null));
            ds.add(ClassicColonyGolden.compare("exit", ours, theirs, EXIT, arrow, null));
            report.append("#").append(r.frame).append(" (arrow ").append(arrow.count())
                .append(" px)\n");
            for (ClassicColonyGolden.Diff d : ds) {
                report.append("  ").append(d).append('\n');
                if (d.differing != 0) {
                    fails.append(" #").append(r.frame).append(' ').append(d.name)
                        .append(": ").append(d.differing);
                }
                assertTrue(r.frame + " " + d.name + ": nothing compared", d.compared > 0);
            }
            // Every colour of these regions is the original palette's (the
            // frame's own palette).
            final Set<Integer> outside = outsidePalette(frame, ours,
                new Rectangle[] { TOP, SEP_ROW, SEP_COL, WAREHOUSE, EXIT, BAND_ROW });
            if (!outside.isEmpty()) {
                fails.append(" #").append(r.frame).append(" colours outside the palette: ")
                    .append(outside.size());
            }
        }
        System.err.println(getName() + ":\n" + report);
        assertEquals("differing pixels:" + fails + "\n" + report, 0, fails.length());
    }

    /**
     * Golden: a warehouse good's hover label «Ware (n Tonnen)» (NAMES.TXT
     * {@code @CARGO}, LABELS «Tonnen») against clip 020 #21430 («Zigarren
     * (0 Tonnen)», centred on box 10) and #21748 («Musketen (50
     * Tonnen)», clamped to end at x 304; the label's first frame #21747
     * caught the arrow mid-draw): its box, 0 differing pixels, the arrow
     * left out; the screen shows it when the pointer is over the box
     * ({@link ClassicColonyPanel#hoverAt}).
     */
    public void testTheWarehouseLabelAgainstClip020() throws Exception {
        final ClassicColonyGolden gold = ClassicColonyGolden.open(getName());
        if (gold == null) return;
        final ClassicText text = ClassicText.load(gold.pack);
        assertNotNull(text);
        final ClassicColonyChrome.Assets a = ClassicColonyChrome.Assets.load(gold.pack);
        final Object[][] cases = {
            { 21430, 10, 0, "Zigarren (0 Tonnen)" },
            { 21748, 15, 50, "Musketen (50 Tonnen)" },
        };
        final StringBuilder fails = new StringBuilder();
        for (Object[] k : cases) {
            final int box = (Integer) k[1];
            final GoodsType gt = spec().getGoodsType(ClassicColonyLayout.WAREHOUSE_GOODS[box]);
            final String label = ClassicColonyChrome.warehouseLabel(text, gt, (Integer) k[2]);
            assertEquals(k[3], label);
            final BufferedImage img = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
            final Graphics2D g = img.createGraphics();
            final Rectangle r = ClassicColonyChrome.paintWarehouseLabel(g, a, label, box);
            g.dispose();
            assertEquals(193, r.y);
            assertTrue(r.x + r.width - 1 <= 304);
            final BufferedImage frame = gold.frame((Integer) k[0]);
            final ClassicColonyGolden.Diff d = ClassicColonyGolden.compare(label,
                ClassicColonyGolden.pixels(img), ClassicColonyGolden.pixels(frame), r,
                gold.pointer(frame), null);
            System.err.println(getName() + " #" + k[0] + ": " + d + " box x" + r.x + "-"
                               + (r.x + r.width - 1));
            if (d.differing != 0) fails.append(" #").append(k[0]).append(": ").append(d);
        }
        // The screen: the pointer over the muskets' box shows the label.
        final Game game = getStandardGame();
        game.changeMap(getTestMap(true));
        final Colony colony = colonyOf(game, REFS[0]);
        final ClassicColonyPanel panel = new ClassicColonyPanel(
            client(game, colony.getOwner()), library(), colony, null, null,
            ClassicSlide.SYSTEM, false);
        panel.hoverAt(295, 185);
        final int[] ours = ClassicColonyGolden.pixels(panel.canvas());
        final BufferedImage frame = gold.frame(21748);
        final Rectangle r = new Rectangle(ClassicColonyLayout.warehouseLabelX(15,
            a.tiny.stringWidth("Musketen (50 Tonnen)")), 193,
            a.tiny.stringWidth("Musketen (50 Tonnen)") + 1, 7);
        final ClassicColonyGolden.Diff d = ClassicColonyGolden.compare("screen's label",
            ours, ClassicColonyGolden.pixels(frame), r, gold.pointer(frame), null);
        System.err.println(getName() + " the screen: " + d);
        if (d.differing != 0) fails.append(" the screen: ").append(d);
        panel.hoverAt(-1, -1);
        final int[] none = ClassicColonyGolden.pixels(panel.canvas());
        assertTrue("the label goes with the pointer",
                   none[194 * 320 + r.x + 2] != ours[194 * 320 + r.x + 2]
                   || none[193 * 320 + r.x] != ours[193 * 320 + r.x]);
        assertEquals(fails.toString(), 0, fails.length());
    }

    /**
     * With {@code -Dclassic.colony.dump=<folder>}: our canvas and the
     * frame side by side, 3x (for the report's eyes; nothing is asserted).
     */
    private static void dump(BufferedImage ours, BufferedImage frame, int n)
        throws java.io.IOException {
        final String d = System.getProperty("classic.colony.dump");
        if (d == null || d.isBlank()) return;
        final BufferedImage out = new BufferedImage(2 * 960 + 12, 600,
                                                    BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = out.createGraphics();
        g.drawImage(ours, 0, 0, 960, 600, null);
        g.drawImage(frame, 972, 0, 960, 600, null);
        g.dispose();
        javax.imageio.ImageIO.write(out, "png", new java.io.File(d, "ours-vs-" + n + ".png"));
    }

    /** A model's content, or the whole panel when there is no model. */
    private static void unite(ClassicColonyGolden.Mask into, ClassicColonyGolden.Mask m,
                              Rectangle whole) {
        if (m == null) {
            into.rect(whole);
            return;
        }
        for (int i = 0; i < m.on.length; i++) if (m.on[i]) into.on[i] = true;
    }

    /** The colours of ours in the regions that the frame's palette has not. */
    private static Set<Integer> outsidePalette(BufferedImage frame, int[] ours,
                                               Rectangle[] regions) {
        final Set<Integer> pal = new HashSet<>();
        final IndexColorModel cm = (IndexColorModel) frame.getColorModel();
        for (int i = 0; i < cm.getMapSize(); i++) pal.add(cm.getRGB(i) & 0xFFFFFF);
        final Set<Integer> out = new HashSet<>();
        for (Rectangle r : regions) {
            for (int y = r.y; y < r.y + r.height; y++) {
                for (int x = r.x; x < r.x + r.width; x++) {
                    final int c = ours[y * 320 + x];
                    if (!pal.contains(c)) out.add(c);
                }
            }
        }
        return out;
    }
}
