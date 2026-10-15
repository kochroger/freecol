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
import java.awt.image.Raster;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

import net.sf.freecol.common.model.Building;
import net.sf.freecol.common.model.BuildingType;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.ModelMessage;
import net.sf.freecol.common.model.ModelMessage.MessageType;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * Part W (build spec M4.11): the colony's turn reports with their zoom
 * rows ({@link ClassicColonyReports}): FreeCol's messages and the levi
 * rules' as the original's reports, their boxes (rows, bar, Escape, the
 * colonist), the boxes over the clips' frames (clip 020: @NEEDTOOLS,
 * @BUILT, @ORE, @CARGOREADY0 twice, @TUTORIAL6, @TUTORIAL16; clip 040:
 * @BUILT, @CARGOREADY2 and the two reports without rows after a zoom),
 * the groups by colony, and the GUI's run of them: the zoom, the flashed
 * reports, the next colony's after the screen, the hold of the turn start,
 * @TUTORIAL6 after a @CARGOREADY0 closed on its first row.
 */
public class ClassicColonyReportsTest extends FreeColTestCase {

    private static ClassicText texts(String test) {
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        if (t == null) {
            System.err.println("ClassicColonyReportsTest: " + test
                + " skipped, no pack texts (ant classic-assets)");
        }
        return t;
    }

    private static BuildingType building(String id) {
        return spec().getBuildingType("model.building." + id);
    }

    private static GoodsType goods(String id) {
        return spec().getGoodsType("model.goods." + id);
    }

    /** FreeCol's @BUILT of a building. */
    private static ModelMessage built(Colony c, BuildingType bt) {
        return new ModelMessage(MessageType.BUILDING_COMPLETED,
            "model.colony.buildingReady", c).addName("%colony%", c.getName())
            .addNamed("%building%", bt);
    }

    /** The levi rule's report of a build lacking tools. */
    private static ModelMessage needTools(Colony c, BuildingType bt, int need, int have) {
        return new ModelMessage(MessageType.MISSING_GOODS,
            "model.colony.classicNeedTools", c, bt).addName("%colony%", c.getName())
            .addNamed("%buildable%", bt).addAmount("%amount%", need)
            .addAmount("%number%", have).addNamed("%goodsType%", goods("tools"));
    }

    /** The levi rule's new load. */
    private static ModelMessage cargo(Colony c, int variant, GoodsType g, int level,
                                      int amount) {
        return new ModelMessage(MessageType.WAREHOUSE_CAPACITY,
            "model.colony.classicCargoReady" + variant, c, g).addNamed("%goods%", g)
            .addName("%colony%", c.getName()).addAmount("%level%", level)
            .addAmount("%amount%", amount);
    }

    /** FreeCol's building idle for want of its input. */
    private static ModelMessage noInput(Colony c, Building b, GoodsType g) {
        return new ModelMessage(MessageType.MISSING_GOODS, "model.building.noInput", b, g)
            .addNamed("%inputGoods%", g).addNamed("%building%", b)
            .addName("%colony%", c.getName());
    }

    /** A colony of the standard test game, named. */
    private Colony colony(String name, int x, int y) {
        final Colony c = createStandardColony(2, x, y);
        c.setName(name);
        return c;
    }


    /**
     * FreeCol's messages and the levi rules' as the original's reports:
     * the section, the values in NAMES.TXT's words (the Verlag, not
     * FreeCol's «Druckerei»), how the zoom opens the screen (spec §0.1
     * decision 8); FreeCol's other words where the original has none.
     */
    public void testTheReportsOfFreeColsMessages() {
        final ClassicText t = texts("testTheReportsOfFreeColsMessages");
        if (t == null) return;
        final Game game = getStandardGame();
        game.changeMap(getTestMap(true));
        final Colony base = colony("Base", 5, 8);
        final BuildingType press = building("printingPress");
        ClassicColonyReports.Report r = ClassicColonyReports.of(t, built(base, press), game);
        assertEquals("BUILT", r.section);
        assertEquals(ClassicColonyReports.Kind.BUILT, r.kind);
        assertEquals("Base", r.values.get("STRING0"));
        assertEquals("Verlag", r.values.get("STRING1"));
        assertEquals(ClassicColonyRight.Opening.BUILT_BUILDING, r.opening);
        assertSame(press, r.built);
        // A unit built: the unit's NAMES.TXT name, hammer mode.
        final Unit art = new ServerUnit(game, base.getTile(), base.getOwner(),
            spec().getUnitType("model.unit.artillery"));
        r = ClassicColonyReports.of(t, new ModelMessage(MessageType.UNIT_ADDED,
            "model.colony.unitReady", base, art).addName("%colony%", "Base")
            .addStringTemplate("%unit%", art.getLabel()), game);
        assertEquals("Artillerie", r.values.get("STRING1"));
        assertEquals(ClassicColonyRight.Opening.BUILT_UNIT, r.opening);
        assertNull(r.built);
        // The tools: @NEEDTOOLS with the stock, @NEEDTOOLS0 without.
        r = ClassicColonyReports.of(t, needTools(base, press, 20, 10), game);
        assertEquals("NEEDTOOLS", r.section);
        assertEquals("Verlag", r.values.get("STRING1"));
        assertEquals("20", r.values.get("NUMBER0"));
        assertEquals("10", r.values.get("NUMBER1"));
        assertEquals(ClassicColonyRight.Opening.NEED_TOOLS, r.opening);
        assertEquals("NEEDTOOLS0", ClassicColonyReports.of(t, needTools(base, press, 20, 0),
                                                          game).section);
        // FreeCol's own rule's missing tools: the same box (its numbers
        // from the build); its missing hammers keep FreeCol's words.
        r = ClassicColonyReports.of(t, new ModelMessage(MessageType.MISSING_GOODS,
            "model.colony.buildableNeedsGoods", base, press).addName("%colony%", "Base")
            .addNamed("%buildable%", press).addAmount("%amount%", 5)
            .addNamed("%goodsType%", goods("tools")), game);
        assertEquals("NEEDTOOLS", r.section);
        assertEquals("20", r.values.get("NUMBER0"));
        assertEquals("15", r.values.get("NUMBER1"));
        assertNull(ClassicColonyReports.of(t, new ModelMessage(MessageType.MISSING_GOODS,
            "model.colony.buildableNeedsGoods", base, press).addName("%colony%", "Base")
            .addNamed("%buildable%", press).addAmount("%amount%", 5)
            .addNamed("%goodsType%", goods("hammers")), game));
        // A building idle: @ORE and its siblings, house mode.
        final Building smith = base.getBuilding(building("blacksmithHouse"));
        assertNotNull(smith);
        r = ClassicColonyReports.of(t, noInput(base, smith, goods("ore")), game);
        assertEquals("ORE", r.section);
        assertEquals(ClassicColonyRight.Opening.ORE, r.opening);
        assertEquals("Base", r.values.get("STRING0"));
        final Map<String, String> siblings = new HashMap<>();
        siblings.put("lumber", "LUMBER");
        siblings.put("cotton", "COTTON");
        siblings.put("tobacco", "TOBACCO");
        siblings.put("sugar", "CANESUGAR");
        siblings.put("furs", "FURS");
        siblings.put("tools", "TOOLS");
        for (Map.Entry<String, String> e : siblings.entrySet()) {
            assertEquals(e.getKey(), e.getValue(), ClassicColonyReports.of(t,
                noInput(base, smith, goods(e.getKey())), game).section);
        }
        assertNull("no such report", ClassicColonyReports.of(t,
            noInput(base, smith, goods("horses")), game));
        // A new load: the good's NAMES.TXT name, the capacity, the stock.
        r = ClassicColonyReports.of(t, cargo(base, 0, goods("rum"), 200, 103), game);
        assertEquals("CARGOREADY0", r.section);
        assertEquals("Rum", r.values.get("STRING1"));
        assertEquals(103, r.amount);
        assertEquals(ClassicColonyRight.Opening.OTHER, r.opening);
        r = ClassicColonyReports.of(t, cargo(base, 2, goods("horses"), 300, 300), game);
        assertEquals("CARGOREADY2", r.section);
        assertEquals("Pferde", r.values.get("STRING1"));
        assertEquals("300", r.values.get("NUMBER0"));
        // FreeCol's store warnings under FreeCol's rules: the same boxes.
        assertEquals("CARGOREADY0", ClassicColonyReports.of(t, new ModelMessage(
            MessageType.WAREHOUSE_CAPACITY, "model.colony.warehouseFull", base,
            goods("rum")).addNamed("%goods%", goods("rum")).addName("%colony%", "Base"),
            game).section);
        assertEquals("CARGOREADY1", ClassicColonyReports.of(t, new ModelMessage(
            MessageType.WAREHOUSE_CAPACITY, "model.colony.warehouseOverfull", base,
            goods("rum")).addNamed("%goods%", goods("rum")).addName("%colony%", "Base"),
            game).section);
        // Not a report: FreeCol's words.
        assertNull(ClassicColonyReports.of(t, new ModelMessage(MessageType.WARNING,
            "model.colony.famineFeared", base).addName("%colony%", "Base")
            .addAmount("%number%", 2), game));
        assertFalse(ClassicColonyReports.isReportId("model.colony.famineFeared"));
        assertNull(ClassicColonyReports.of(null, built(base, press), game));
    }

    /**
     * The boxes: the two LABELS.TXT rows «Runde fortsetzen.» (the bar,
     * Escape) and «Zur Kolonie zoomen.», the colonist; without rows a box
     * that closes by itself after 57 frames.
     */
    public void testTheBoxes() {
        final ClassicText t = texts("testTheBoxes");
        if (t == null) return;
        final Game game = getStandardGame();
        game.changeMap(getTestMap(true));
        final Colony base = colony("Base", 5, 8);
        final ClassicColonyReports.Report r = ClassicColonyReports.of(t,
            built(base, building("printingPress")), game);
        final ClassicAdvisorBox.Request q = ClassicColonyReports.request(t, r, true, "t");
        assertEquals("[Runde fortsetzen., Zur Kolonie zoomen.]",
                     java.util.Arrays.toString(q.plainRows()));
        assertEquals(ClassicColonyReports.ROW_CONTINUE, q.defaultRow);
        assertEquals(ClassicColonyReports.ROW_CONTINUE, q.escapeAnswer());
        assertSame(ClassicAdvisorBox.Portrait.COLONIST, q.portrait);
        assertEquals(0.0, q.closeAfterMs);
        assertEquals("Kolonie Base produziert Verlag.", q.plainText().trim());
        final ClassicAdvisorBox.Request f = ClassicColonyReports.request(t, r, false, "t");
        assertEquals(0, f.rows.size());
        assertEquals(ClassicColonyLayout.frames(57), f.closeAfterMs, 1e-9);
        assertEquals("7 frames after the box before (clip 040 #85 054 -> #85 061)",
                     ClassicColonyLayout.frames(7), f.chainMs, 1e-9);
        assertTrue("a row box keeps the boxes' usual distance", q.chainMs < 0.0);
        assertSame(ClassicAdvisorBox.Portrait.COLONIST, f.portrait);
    }

    /**
     * The groups: by colony in the order of its first report, each
     * colony's in theirs.
     */
    public void testTheGroups() {
        final ClassicText t = texts("testTheGroups");
        if (t == null) return;
        final Game game = getStandardGame();
        game.changeMap(getTestMap(true));
        final Colony a = colony("Base", 5, 8);
        final Colony b = colony("Silver City", 10, 4);
        final List<ClassicColonyReports.Report> in = new ArrayList<>();
        in.add(ClassicColonyReports.of(t, built(a, building("printingPress")), game));
        in.add(ClassicColonyReports.of(t, needTools(b, building("printingPress"), 20, 3), game));
        in.add(ClassicColonyReports.of(t, cargo(a, 0, goods("rum"), 200, 103), game));
        in.add(null);
        in.add(ClassicColonyReports.of(t, cargo(b, 0, goods("sugar"), 200, 101), game));
        final List<List<ClassicColonyReports.Report>> g = ClassicColonyReports.grouped(in);
        assertEquals(2, g.size());
        assertEquals("[BUILT Base, CARGOREADY0 Base]", g.get(0).toString());
        assertEquals("[NEEDTOOLS Silver City, CARGOREADY0 Silver City]", g.get(1).toString());
        assertTrue(ClassicColonyReports.grouped(null).isEmpty());
    }

    /**
     * The original's boxes over the frames that show them (the pack and
     * {@code -Dclassic.clips}): box, words, bar and the colonist 0 px off,
     * the original's mouse arrow excused.  Clip 020: @NEEDTOOLS #18277
     * (42,105,236,52), @BUILT #57544 (87,111,146,40), @ORE #96400
     * (42,108,236,46), @CARGOREADY0 #109426 and #129679 (42,111,236,40),
     * @TUTORIAL6 #109790 (49,115,226,42, the admiral), @TUTORIAL16 #19215
     * (5,10,226,96, no portrait).  Clip 040: @BUILT «Artillerie» #84900,
     * @CARGOREADY2 «Pferde» «(300)» #84666 (42,105,236,52), the reports
     * without rows after the zoom #85061 (42,116,236,30) and #85132
     * (42,119,236,24).  Two residues are named and not counted (each must
     * still differ): @ORE #96400 and the Musketen box #85132, where the
     * original keeps a line of 225 and 226 px on one line that our box
     * measure ({@link ClassicTextLayout}'s W - 6 = 224, pinned on the
     * landfall's 161 line ends) wraps; no other measure tried kept every
     * other golden (part W's notes).
     */
    public void testTheReportsAgainstTheClips() throws Exception {
        final String clips = System.getProperty(ClassicTerrainGoldenTest.CLIPS_PROPERTY);
        final File dir = (clips == null) ? null : new File(clips);
        if (dir == null || !new File(dir, "playthrough-3").isDirectory()) {
            System.err.println(getClass().getSimpleName()
                + ": golden check skipped, no recordings (-D"
                + ClassicTerrainGoldenTest.CLIPS_PROPERTY + ")");
            return;
        }
        final ClassicText t = texts("testTheReportsAgainstTheClips");
        if (t == null) return;
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final Game game = getStandardGame();
        game.changeMap(getTestMap(true));
        final Colony base = colony("Base", 5, 8);
        final Colony inka = colony("Inka Cotton", 10, 4);
        final Player dutch = base.getOwner();
        final Building smith = base.getBuilding(building("blacksmithHouse"));
        final Unit art = new ServerUnit(game, base.getTile(), dutch,
            spec().getUnitType("model.unit.artillery"));
        final File p3 = new File(dir, "playthrough-3");
        final File key = new File(dir, "colony-flow-analysis/key");
        final ClassicColonyReports.Report rum = ClassicColonyReports.of(t,
            cargo(base, 0, goods("rum"), 200, 103), game);
        final Object[][] cases = {
            // frame, request, the box
            { ClassicTerrainGoldenTest.frameFile(p3, 18277), request(t, game,
                needTools(base, building("printingPress"), 20, 10), true),
              new Rectangle(42, 105, 236, 52) },
            { ClassicTerrainGoldenTest.frameFile(p3, 57544), request(t, game,
                built(base, building("printingPress")), true),
              new Rectangle(87, 111, 146, 40) },
            { ClassicTerrainGoldenTest.frameFile(p3, 96400), request(t, game,
                noInput(base, smith, goods("ore")), true), new Rectangle(42, 108, 236, 46),
              "the original keeps «Schmiede» on line 1 (225 px of the 224 our measure allows)" },
            { ClassicTerrainGoldenTest.frameFile(p3, 109426), request(t, game,
                cargo(base, 0, goods("rum"), 200, 103), true),
              new Rectangle(42, 111, 236, 40) },
            { ClassicTerrainGoldenTest.frameFile(p3, 129679), request(t, game,
                cargo(base, 0, goods("sugar"), 200, 103), true),
              new Rectangle(42, 111, 236, 40) },
            { ClassicTerrainGoldenTest.frameFile(p3, 109790), ClassicTips.request(t,
                ClassicTips.CARGO, ClassicColonyReports.cargoTipValues(t, rum)),
              new Rectangle(49, 115, 226, 42) },
            { ClassicTerrainGoldenTest.frameFile(p3, 19215), ClassicTips.request(t,
                ClassicTips.FOOD, new HashMap<>()), new Rectangle(5, 10, 226, 96) },
            { new File(key, "f084900_built-artillerie.png"), request(t, game,
                new ModelMessage(MessageType.UNIT_ADDED, "model.colony.unitReady", base, art)
                    .addName("%colony%", "Base").addStringTemplate("%unit%", art.getLabel()),
                true), new Rectangle(87, 111, 146, 40) },
            { new File(key, "f084666_cargoready2-pferde-300.png"), request(t, game,
                cargo(inka, 2, goods("horses"), 300, 300), true),
              new Rectangle(42, 105, 236, 52) },
            { new File(key, "f085061_rowless-baumwolle.png"), request(t, game,
                cargo(base, 0, goods("cotton"), 200, 100), false),
              new Rectangle(42, 116, 236, 30) },
            { new File(key, "f085132_rowless-musketen.png"), request(t, game,
                cargo(base, 0, goods("muskets"), 200, 100), false),
              new Rectangle(42, 116, 236, 30),
              "the original keeps the line in one (226 px of the 224 our measure allows):"
              + " its box (42,119,236,24)" },
        };
        final StringBuilder fails = new StringBuilder();
        int compared = 0, arrow = 0, n = 0;
        for (Object[] c : cases) {
            final File f = (File) c[0];
            if (f == null || !f.isFile()) {
                System.err.println(getName() + ": no frame " + f + ", skipped");
                continue;
            }
            final ClassicAdvisorBox.Request r = (ClassicAdvisorBox.Request) c[1];
            assertNotNull(f.getName(), r);
            final int bar = (r.rows.isEmpty()) ? -1 : r.defaultRow;
            final int[] got = compare(ImageIO.read(f), r, pack, (Rectangle) c[2], bar);
            if (c.length > 3) {
                // A known residue of the box wrap measure (ClassicTextLayout.BOX,
                // checked on the landfall's 161 line ends): named, not counted.
                System.out.println(getName() + ": residue " + f.getName() + " "
                    + got[1] + " px: " + c[3]);
                assertTrue(f.getName(), got[1] > 0);
                continue;
            }
            compared += got[0];
            arrow += got[2];
            n++;
            if (got[1] > 0) fails.append(' ').append(f.getName()).append('=').append(got[1]);
        }
        System.out.println(getClass().getSimpleName() + ": golden check, " + n
            + " boxes, " + compared + " px compared, off:"
            + ((fails.length() == 0) ? " none" : fails.toString()) + ", "
            + arrow + " arrow px excused");
        assertEquals("pixels off:" + fails, 0, fails.length());
    }

    /** A report's box as the GUI asks it. */
    private static ClassicAdvisorBox.Request request(ClassicText t, Game game,
                                                     ModelMessage m, boolean rows) {
        final ClassicColonyReports.Report r = ClassicColonyReports.of(t, m, game);
        assertNotNull(m.getId(), r);
        return ClassicColonyReports.request(t, r, rows, "t");
    }

    /**
     * A box drawn over a frame, the box and its portrait compared: {
     * compared, off, off on the arrow's colours }.
     */
    private static int[] compare(BufferedImage frame, ClassicAdvisorBox.Request r,
                                 ClassicPackFiles pack, Rectangle box, int bar) {
        final ClassicFont tiny = pack.font(ClassicFont.TINY);
        final BufferedImage pic = (r.portrait.sprite == null) ? null
            : pack.image(ClassicPackFiles.ssKey(r.portrait.sprite));
        final ClassicAdvisorBox.Layout l = ClassicAdvisorBox.layout(r, tiny, pic);
        assertEquals(r.id, box, l.box);
        final BufferedImage canvas = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = canvas.createGraphics();
        g.drawImage(frame, 0, 0, null);
        ClassicAdvisorBox.paint(g, l, bar, pack.image(ClassicMenuBar.WOOD_KEY), tiny);
        g.dispose();
        final Raster idx = (frame.getColorModel() instanceof IndexColorModel)
            ? frame.getRaster() : null;
        final int[] out = new int[3];
        for (int y = 0; y < 200; y++) {
            for (int x = 0; x < 320; x++) {
                boolean on = l.box.contains(x, y);
                if (!on && pic != null) {
                    final int px = x - l.portraitAt.x, py = y - l.portraitAt.y;
                    on = px >= 0 && py >= 0 && px < pic.getWidth() && py < pic.getHeight()
                        && (pic.getRGB(px, py) >>> 24) != 0;
                }
                if (!on) continue;
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

    /**
     * {@code @TUTORIAL6}'s values: the good, the colony, its stock, the
     * home port (V clip 020 #237 «Sir,in {Base} sind {103 Rum}» ...
     * «Amsterdam»).
     */
    public void testTheCargoTipsValues() {
        final ClassicText t = texts("testTheCargoTipsValues");
        if (t == null) return;
        final Game game = getStandardGame();
        game.changeMap(getTestMap(true));
        final Colony base = colony("Base", 5, 8);
        final Map<String, String> v = ClassicColonyReports.cargoTipValues(t,
            ClassicColonyReports.of(t, cargo(base, 0, goods("rum"), 200, 103), game));
        assertEquals("Rum", v.get("STRING0"));
        assertEquals("Base", v.get("STRING1"));
        assertEquals("103", v.get("NUMBER0"));
        assertEquals("Amsterdam", v.get("STRING2"));
        final ClassicAdvisorBox.Request q = ClassicTips.request(t, ClassicTips.CARGO, v);
        assertTrue(q.plainText(), q.plainText().contains("Base sind 103 Rum"));
        assertSame(ClassicAdvisorBox.Portrait.ADMIRAL, q.portrait);
        assertSame(ClassicAdvisorBox.Portrait.NONE, ClassicTips.portrait(ClassicTips.FOOD));
    }


    // The GUI's run of the reports

    /** A classic GUI without a client: the test's game, clock, boxes and screens. */
    private static final class RunGUI extends ClassicGUI {

        final Game game;
        final Player me;
        final ClassicScreenStackTestClock clock = new ClassicScreenStackTestClock();

        /** The answer per box id prefix (the rest: row 0), and the boxes asked. */
        final Map<String, Integer> answers = new HashMap<>();
        final List<ClassicAdvisorBox.Request> asked = new ArrayList<>();

        /** The screens opened: "colony how built". */
        final List<String> opened = new ArrayList<>();

        RunGUI(Game game, Player me) {
            super(null);
            this.game = game;
            this.me = me;
            this.prompter = r -> {
                this.asked.add(r);
                final Integer a = this.answers.get(r.id);
                return (a == null) ? 0 : a;
            };
        }

        @Override
        protected Game getGame() {
            return this.game;
        }

        @Override
        Player myPlayer() {
            return this.me;
        }

        @Override
        protected Player getMyPlayer() {
            return this.me;
        }

        @Override
        ClassicSlide.Clock waitClock() {
            return this.clock;
        }

        @Override
        boolean tipsOn() {
            return true;
        }

        @Override
        boolean openColonyScreen(Colony colony, Unit carrier,
                                 ClassicColonyRight.Opening how, BuildingType built,
                                 boolean docked) {
            this.opened.add(colony.getName() + " " + how + " "
                + ((built == null) ? "-" : built.getSuffix()));
            return true;
        }

        List<String> askedIds() {
            final List<String> out = new ArrayList<>();
            for (ClassicAdvisorBox.Request r : this.asked) {
                out.add(r.id + ((r.rows.isEmpty()) ? " (flash)" : ""));
            }
            return out;
        }
    }

    /** A clock the test moves. */
    private static final class ClassicScreenStackTestClock implements ClassicSlide.Clock {

        volatile long now = 1_000_000_000L;

        @Override
        public long now() {
            return this.now;
        }

        @Override
        public void waitUntil(long due) {
            // never waits
        }
    }

    private static void edt(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
    }

    private static void flush() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });
    }

    /**
     * {@code @TUTORIAL16}'s time: 44 frames after the change on the screen
     * that made the colony's food run short (V clip 020 #18949 -&gt;
     * #18993), for that colony, no portrait; once per game.
     */
    public void testTheFoodTip() throws Exception {
        final Game game = getStandardGame();
        game.changeMap(getTestMap(true));
        final Colony a = colony("Base", 5, 8);
        final Player dutch = a.getOwner();
        dutch.setClassicTips(0);
        final RunGUI gui = new RunGUI(game, dutch);
        edt(() -> {
                gui.installScreens(null);
                gui.colonyFoodShort(a, gui.clock.now);
                gui.colonyFoodShort(a, gui.clock.now + 5);
                gui.colonyFoodShort(null, gui.clock.now);
            });
        assertEquals("one on its way", 1, gui.pendingTips().size());
        final ClassicGUI.Tip tip = gui.pendingTips().get(0);
        assertEquals(ClassicTips.FOOD, tip.k);
        assertSame(a, tip.colony);
        assertEquals(Math.round(44 * 1000.0 / 70.0863 * 1e6), tip.due - gui.clock.now);
        assertSame(ClassicAdvisorBox.Portrait.NONE, ClassicTips.portrait(ClassicTips.FOOD));
    }

    /**
     * The run: each report with its rows, colony by colony; «Runde
     * fortsetzen.» on a @CARGOREADY0 puts @TUTORIAL6 33 frames after its
     * close; «Zur Kolonie zoomen.» shows the same colony's other reports
     * without rows, then its screen 23 frames after the last close in the
     * report's mode (the building's dissolve), and the next colony's
     * reports come once that screen is gone; a turn start's reports wait
     * for its hold (after our ships' arrivals) and hold the turn flow
     * while they run.
     */
    public void testTheRunOfTheReports() throws Exception {
        final ClassicText t = texts("testTheRunOfTheReports");
        if (t == null) return;
        final Game game = getStandardGame();
        game.changeMap(getTestMap(true));
        final Colony a = colony("Base", 5, 8);
        final Colony b = colony("Silver City", 10, 4);
        final Player dutch = a.getOwner();
        dutch.setClassicTips(0);
        final BuildingType press = building("printingPress");
        final Building smith = b.getBuilding(building("blacksmithHouse"));
        final List<ClassicColonyReports.Report> in = new ArrayList<>();
        in.add(ClassicColonyReports.of(t, built(a, press), game));
        in.add(ClassicColonyReports.of(t, needTools(b, press, 20, 3), game));
        in.add(ClassicColonyReports.of(t, cargo(a, 0, goods("rum"), 200, 103), game));
        in.add(ClassicColonyReports.of(t, noInput(b, smith, goods("ore")), game));
        final List<List<ClassicColonyReports.Report>> groups = ClassicColonyReports.grouped(in);

        // 1. All «Runde fortsetzen.»: in their order, @TUTORIAL6 after the rum.
        RunGUI gui = new RunGUI(game, dutch);
        final RunGUI g1 = gui;
        edt(() -> {
                g1.installScreens(null);
                g1.addReports(groups, true);
            });
        assertEquals("[report BUILT, report CARGOREADY0, report NEEDTOOLS, report ORE]",
                     gui.askedIds().toString());
        assertNull("over", gui.reportRun());
        assertTrue(gui.opened.isEmpty());
        assertEquals(1, gui.pendingTips().size());
        final ClassicGUI.Tip tip = gui.pendingTips().get(0);
        assertEquals(ClassicTips.CARGO, tip.k);
        assertEquals(gui.clock.now + ClassicVoyages.nanos(33 * 1000.0 / 70.0863), tip.due);
        assertEquals("Rum", tip.values.get("STRING0"));
        assertEquals("103", tip.values.get("NUMBER0"));

        // 2. The zoom: Base's other report without rows, then its screen
        // 23 frames later; Silver City's after the screen went.
        gui = new RunGUI(game, dutch);
        gui.answers.put("report BUILT", ClassicColonyReports.ROW_ZOOM);
        final RunGUI g2 = gui;
        edt(() -> {
                g2.installScreens(null);
                g2.addReports(groups, true);
            });
        assertEquals("[report BUILT, report CARGOREADY0 (flash)]", gui.askedIds().toString());
        assertTrue(gui.pendingTips().isEmpty());
        assertNotNull(gui.reportRun());
        assertSame(a, gui.reportRun().zoomed);
        final boolean[] blocked = new boolean[1];
        edt(() -> blocked[0] = g2.turnBlocked());
        assertTrue("the run holds the turn flow", blocked[0]);
        final ClassicOneShot timer = gui.colonyTimer();
        assertTrue(timer.isScheduled());
        assertEquals(gui.clock.now + ClassicVoyages.nanos(
            ClassicColonyLayout.frames(23)), timer.dueNanos());
        gui.clock.now = timer.dueNanos();
        assertTrue(timer.runIfDue());
        flush();
        assertEquals("[Base BUILT_BUILDING printingPress]", gui.opened.toString());
        assertEquals(2, gui.asked.size());
        edt(() -> g2.reportScreenGone(a));
        flush();
        assertEquals("[report BUILT, report CARGOREADY0 (flash), report NEEDTOOLS, report ORE]",
                     gui.askedIds().toString());
        assertNull(gui.reportRun());

        // 3. A turn start's reports: at its hold only, holding it.
        gui = new RunGUI(game, dutch);
        final RunGUI g3 = gui;
        edt(() -> {
                g3.installScreens(null);
                g3.addReports(groups, false);
            });
        assertTrue(gui.asked.isEmpty());
        final boolean[] blocked3 = new boolean[1];
        edt(() -> blocked3[0] = g3.turnBlocked());
        assertFalse("waiting for the hold", blocked3[0]);
        final boolean[] held = new boolean[1];
        edt(() -> held[0] = g3.holdTurnStart());
        assertTrue("the turn start's hold", held[0]);
        flush();
        assertEquals(4, gui.asked.size());
        edt(() -> held[0] = g3.holdForReports());
        assertFalse("done", held[0]);
        edt(() -> held[0] = g3.holdTurnStart());
        assertFalse(held[0]);
    }
}
