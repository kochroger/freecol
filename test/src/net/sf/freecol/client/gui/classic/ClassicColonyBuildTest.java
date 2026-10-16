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
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.sf.freecol.common.model.AbstractGoods;
import net.sf.freecol.common.model.BuildableType;
import net.sf.freecol.common.model.BuildingType;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Direction;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.common.networking.ChangeSet;
import net.sf.freecol.common.option.GameOptions;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.server.ServerTestHelper;
import net.sf.freecol.server.model.ServerColony;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.util.test.FreeColTestCase;

import static net.sf.freecol.client.gui.classic.ClassicColonyBuildingsTest.State;


/**
 * Building on the colony screen (part V, build spec M4.10;
 * {@link ClassicColonyBuild}, the panel's ÄNDERN, KAUFEN and completion):
 * the original's price (levi rule R1: 13 per missing hammer, the tools at
 * the market's ask), the completed building kept as the current build
 * (rule R4, the server's record), a finished unit built again, the hammers
 * carried over; the build list's rows against clip 020's three lists with
 * «(ABGESCHLOSSEN)» (#58196, #73847, #106509) and drawn over them (0 px);
 * @BUYME0 over #94078 (0 px); KAUFEN and ÄNDERN with the real client
 * controller and a real server game; the completion's dissolve (the old
 * and the new picture of the Verlag's slot against #57671 and #57768, 0
 * px) and ÄNDERN's blink.
 */
public class ClassicColonyBuildTest extends FreeColTestCase {

    private static Specification classic() {
        return spec("classic");
    }

    @Override
    protected void tearDown() throws Exception {
        for (Specification s : new Specification[] { spec(), classic() }) {
            s.setBoolean(GameOptions.ORIGINAL_BUILD_PRICE, false);
            s.setBoolean(GameOptions.ORIGINAL_BUILD_COMPLETION, false);
        }
        ClassicColonyPanel.forgetFigureOrders();
        ClassicColonyPanel.forgetSelections();
        ClassicColonyPanel.setTileCursor(ClassicColonyLayout.CENTRE_TILE);
        ClassicColonyPanel.setLastMode(ClassicColonyRight.Mode.HOUSE);
        classic().applyDifficultyLevel("model.difficulty.medium");
        ServerTestHelper.stopServerGame();
        super.tearDown();
    }

    private static GoodsType goods(Specification s, String id) {
        return s.getGoodsType("model.goods." + id);
    }

    private static BuildingType building(Specification s, String id) {
        return s.getBuildingType("model.building." + id);
    }

    /**
     * Whether a colony's completion record is of this turn (R4's turn; the
     * screen asks it no more since part W: only the zoom dissolves).
     */
    private static boolean completedThisTurn(Colony colony) {
        return ClassicColonyBuild.completed(colony) != null && colony.getGame() != null
            && colony.getGame().getTurn() != null
            && colony.getClassicBuiltTurn() == colony.getGame().getTurn().getNumber();
    }


    // R1: the price

    /**
     * KAUFEN's price (V clip 020 #94078: 494 = 13 x 38 for the Lagerhaus
     * with 42 of 80 hammers; dago-colony2: 1248 = 13 x 96; part X with
     * playthrough 4: 046 #119741 484 = 13 x 28 + 6 x 20, 048 #70208 4760 =
     * 2 x (13 x 160 + 6 x 50) with no hammer stored): with the levi rule
     * 13 per missing hammer and 6 per missing tool, doubled while no
     * hammer is stored; without it FreeCol's 20 per hammer and the tools
     * at the ask plus 10 % (every recorded box: {@code LeviBuildPriceTest}).
     */
    public void testThePrice() {
        final State s = ClassicColonyBuildingsTest.base1539();
        s.on("expertFarmer", Direction.NW, "grain");
        s.finish();
        final Colony c = s.colony;
        final GoodsType hammers = goods(classic(), "hammers"), tools = goods(classic(), "tools");
        final List<AbstractGoods> missing = List.of(new AbstractGoods(hammers, 38));
        classic().setBoolean(GameOptions.ORIGINAL_BUILD_PRICE, true);
        c.addGoods(hammers, 42);
        assertEquals("V #94078", 494, c.priceGoodsForBuilding(missing));
        assertEquals("dago-colony2", 1248,
                     c.priceGoodsForBuilding(List.of(new AbstractGoods(hammers, 96))));
        assertEquals("046 #119741: 6 a tool", 13 * 28 + 6 * 20,
                     c.priceGoodsForBuilding(List.of(new AbstractGoods(hammers, 28),
                                                     new AbstractGoods(tools, 20))));
        c.removeGoods(hammers);
        assertEquals("048 #70208: no hammer stored, doubled", 4760,
                     c.priceGoodsForBuilding(List.of(new AbstractGoods(hammers, 160),
                                                     new AbstractGoods(tools, 50))));
        c.addGoods(hammers, 42);
        final int ask = c.getOwner().getMarket().getBidPrice(tools, 20);
        classic().setBoolean(GameOptions.ORIGINAL_BUILD_PRICE, false);
        assertEquals("FreeCol: 20 a hammer", 760, c.priceGoodsForBuilding(missing));
        assertEquals("FreeCol: the tools 10 % dearer", 20 * 10 + ask * 110 / 100,
                     c.priceGoodsForBuilding(List.of(new AbstractGoods(hammers, 10),
                                                     new AbstractGoods(tools, 20))));
        c.removeGoods(hammers);
        assertEquals("FreeCol: no doubling", 760, c.priceGoodsForBuilding(missing));
        // The screen's KAUFEN: the build's missing goods; nothing to buy: -1.
        classic().setBoolean(GameOptions.ORIGINAL_BUILD_PRICE, true);
        c.setCurrentlyBuilding(building(classic(), "warehouse"));
        c.addGoods(hammers, 42);
        assertEquals(494, ClassicColonyBuild.price(c));
        c.setCurrentlyBuilding(null);
        assertEquals(-1, ClassicColonyBuild.price(c));
    }


    // The build list

    /** Clip 020's three lists after a completion (support/boxes.tsv: OCR, err 0). */
    private static final String[] LIST_58196 = {
        "(Keine Produktion)", "EINPFäHLUNG|(64 Hämmer)", "WAFFENKAMMER|(52 Hämmer)",
        "TROCKENDOCK|(80 Hämmer)(50 Werkzeuge)", "SCHULHAUS|(64 Hämmer)",
        "LAGERHAUS|(80 Hämmer)", "STäLLE|(64 Hämmer)", "*VERLAG|(ABGESCHLOSSEN)",
        "DRUCKEREI|(120 Hämmer)(50 Werkzeuge)", "WEBEREI|(64 Hämmer)(20 Werkzeuge)",
        "TABAKLADEN|(64 Hämmer)(20 Werkzeuge)", "RUMBRENNEREI|(64 Hämmer)(20 Werkzeuge)",
        "PELZHANDELSPOSTEN|(56 Hämmer)(20 Werkzeuge)", "KIRCHE|(64 Hämmer)",
        "SCHMIEDE|(64 Hämmer)(20 Werkzeuge)", "WAGENZUG|(40 Hämmer)" };

    private static final String[] LIST_73847 = {
        "(Keine Produktion)", "EINPFäHLUNG|(64 Hämmer)", "WAFFENKAMMER|(52 Hämmer)",
        "TROCKENDOCK|(80 Hämmer)(50 Werkzeuge)", "SCHULHAUS|(64 Hämmer)",
        "LAGERHAUS|(80 Hämmer)", "STäLLE|(64 Hämmer)",
        "DRUCKEREI|(120 Hämmer)(50 Werkzeuge)", "WEBEREI|(64 Hämmer)(20 Werkzeuge)",
        "TABAKLADEN|(64 Hämmer)(20 Werkzeuge)", "RUMBRENNEREI|(64 Hämmer)(20 Werkzeuge)",
        "PELZHANDELSPOSTEN|(56 Hämmer)(20 Werkzeuge)", "KIRCHE|(64 Hämmer)",
        "*SCHMIEDE|(ABGESCHLOSSEN)", "WAGENZUG|(40 Hämmer)" };

    private static final String[] LIST_106509 = {
        "(Keine Produktion)", "EINPFäHLUNG|(64 Hämmer)", "WAFFENKAMMER|(52 Hämmer)",
        "TROCKENDOCK|(80 Hämmer)(50 Werkzeuge)", "SCHULHAUS|(64 Hämmer)",
        "*LAGERHAUS|(ABGESCHLOSSEN)", "LAGERHAUSERWEITERUNG|(80 Hämmer)(20 Werkzeuge)",
        "STäLLE|(64 Hämmer)", "DRUCKEREI|(120 Hämmer)(50 Werkzeuge)",
        "WEBEREI|(64 Hämmer)(20 Werkzeuge)", "TABAKLADEN|(64 Hämmer)(20 Werkzeuge)",
        "RUMBRENNEREI|(64 Hämmer)(20 Werkzeuge)", "PELZHANDELSPOSTEN|(56 Hämmer)(20 Werkzeuge)",
        "KIRCHE|(64 Hämmer)", "WAGENZUG|(40 Hämmer)" };

    /** A clip list as rows ("*" the current one, "|" before the cost). */
    private static List<ClassicColonyBuild.Row> clipRows(String[] list) {
        final List<ClassicColonyBuild.Row> out = new ArrayList<>();
        for (String r : list) {
            final boolean cur = r.startsWith("*");
            final String[] q = ((cur) ? r.substring(1) : r).split("\\|");
            out.add(new ClassicColonyBuild.Row(null, q[0], (q.length > 1) ? q[1] : null, cur,
                                               cur && q[1].contains("ABGESCHLOSSEN")));
        }
        return out;
    }

    /** Our rows as the clip's notation. */
    private static List<String> notation(List<ClassicColonyBuild.Row> rows) {
        final List<String> out = new ArrayList<>();
        for (ClassicColonyBuild.Row r : rows) {
            out.add(((r.current && r.cost != null) ? "*" : "") + r.name
                    + ((r.cost == null) ? "" : "|" + r.cost));
        }
        return out;
    }

    /** Base in 1547 (visit 10): the founding families, Sägewerk, Hafenanlagen, five colonists. */
    private static State base(int year, String... built) {
        final State s = new State("Base", year).build("lumberMill").build("docks");
        for (String b : built) s.build(b);
        s.in("veteranSoldier", "townHall");
        s.in("masterCarpenter", "lumberMill");
        s.on("expertFarmer", Direction.NW, "grain");
        s.on("expertLumberJack", Direction.N, "lumber");
        s.on("masterSugarPlanter", Direction.NE, "ore");
        s.finish();
        return s;
    }

    /**
     * The build list's rows (V clip 020 #58196, #73847, #106509; dago-colony2
     * §4): «(Keine Produktion)», the buildings Base can build in NAMES
     * order, the units; a completed building with «(ABGESCHLOSSEN)» in its
     * NAMES place, the current one; the costs n = max(0, cost - stored),
     * the tools in full; then a finished unit stays current with the
     * hammers kept (#276 «WAGENZUG (4 Hämmer)»).
     */
    public void testTheRowsAsTheClip() {
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        if (t == null) {
            System.err.println(getName() + ": no pack texts, skipped");
            return;
        }
        classic().setBoolean(GameOptions.ORIGINAL_BUILD_COMPLETION, true);
        final State v = base(1547, "printingPress");
        v.colony.setClassicBuilt("model.building.printingPress", 1);
        assertEquals(Arrays.asList(LIST_58196).toString(),
                     notation(ClassicColonyBuild.rows(v.colony, t)).toString());
        final State sch = base(1553, "printingPress", "blacksmithShop");
        sch.colony.setClassicBuilt("model.building.blacksmithShop", 1);
        assertEquals(Arrays.asList(LIST_73847).toString(),
                     notation(ClassicColonyBuild.rows(sch.colony, t)).toString());
        final State l = base(1562, "printingPress", "blacksmithShop", "warehouse");
        l.colony.setClassicBuilt("model.building.warehouse", 1);
        final List<ClassicColonyBuild.Row> rows = ClassicColonyBuild.rows(l.colony, t);
        assertEquals(Arrays.asList(LIST_106509).toString(), notation(rows).toString());
        assertEquals("the bar on it", 5, ClassicColonyBuild.barRow(rows));
        // A unit stays current; the hammers kept (#276: 36 of 40).
        l.colony.setClassicBuilt(null, -1);
        l.colony.setCurrentlyBuilding(classic().getUnitType("model.unit.wagonTrain"));
        l.colony.addGoods(goods(classic(), "hammers"), 36);
        final List<ClassicColonyBuild.Row> after = ClassicColonyBuild.rows(l.colony, t);
        assertEquals("WAGENZUG|(4 Hämmer)", notation(after).get(after.size() - 1).substring(1));
        assertTrue(after.get(after.size() - 1).current);
        assertFalse("no completed row any more", notation(after).toString()
                    .contains("ABGESCHLOSSEN"));
        // Without the levi rule (FreeCol's): no completed row.
        classic().setBoolean(GameOptions.ORIGINAL_BUILD_COMPLETION, false);
        assertNull(ClassicColonyBuild.completed(v.colony));
        assertFalse(notation(ClassicColonyBuild.rows(v.colony, t)).toString()
                    .contains("ABGESCHLOSSEN"));
    }

    /**
     * The three lists drawn over clip 020 (V, 0 px on the whole box): w =
     * max(name + cost) + 26 (196, and 210 with «LAGERHAUSERWEITERUNG
     * (80 Hämmer)(20 Werkzeuge)»), h = 30 + 8·rows, centred; the completed
     * building yellow under the bar; «(F1 für Hilfe)».
     */
    public void testTheListsOverTheClip() throws Exception {
        final ClassicColonyGolden gold = ClassicColonyGolden.open(getName());
        if (gold == null) return;
        final ClassicText t = ClassicText.load(gold.pack);
        final ClassicFont tiny = gold.pack.font(ClassicFont.TINY);
        final BufferedImage wood = gold.pack.image(ClassicMenuBar.WOOD_KEY);
        final Object[][] cases = {
            { 58196, LIST_58196, new Rectangle(62, 21, 196, 158) },
            { 73847, LIST_73847, new Rectangle(62, 25, 196, 150) },
            { 106509, LIST_106509, new Rectangle(55, 25, 210, 150) },
        };
        final StringBuilder out = new StringBuilder();
        int bad = 0;
        for (Object[] c : cases) {
            final List<ClassicColonyBuild.Row> rows = clipRows((String[]) c[1]);
            final ClassicAdvisorBox.Request r = ClassicColonyBuild.request(t, tiny, rows, 0.0);
            final ClassicAdvisorBox.Layout l = ClassicAdvisorBox.layout(r, tiny, null);
            assertEquals("#" + c[0], c[2], l.box);
            final int[] got = ClassicColonyMovesTest.overFrame(gold, gold.frame((Integer) c[0]), l,
                r.defaultRow, wood, tiny);
            out.append(" #").append(c[0]).append(' ').append(got[0]).append('/').append(got[1])
                .append(" arrow ").append(got[2]);
            bad += got[1];
        }
        System.out.println(getName() + ":" + out);
        assertEquals("pixels off:" + out, 0, bad);
    }


    // KAUFEN

    /**
     * @BUYME0 (V #94078): «Kosten für Abschluß von Lagerhaus: 494¤.» /
     * «Schatzkammer: 348¤.», box (77,116,166,30), MSS5 at (130,54), no
     * rows, 0 px over the frame; @BUYME1: «Na, dann nicht.» (the bar,
     * Escape) / «Trotzdem abschließen.».
     */
    public void testTheBuyBoxes() throws Exception {
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        if (t == null) return;
        final ClassicAdvisorBox.Request short0 = ClassicColonyBuild.buyRequest(t, "Lagerhaus",
                                                                              494, 348);
        assertEquals("BUYME0", short0.id);
        assertTrue(short0.isNotice());
        assertEquals("Kosten für Abschluß von Lagerhaus: 494$. Schatzkammer: 348$.",
                     short0.plainText().replace('\n', ' ').replace("¤", "$"));
        final ClassicAdvisorBox.Request yes = ClassicColonyBuild.buyRequest(t, "Lagerhaus",
                                                                           494, 600);
        assertEquals("BUYME1", yes.id);
        assertEquals("[Na, dann nicht., Trotzdem abschließen.]",
                     Arrays.toString(yes.plainRows()));
        assertEquals(0, yes.defaultRow);
        assertEquals("Escape: «Na, dann nicht.»", 0, yes.cancelRow);
        final ClassicColonyGolden gold = ClassicColonyGolden.open(getName());
        if (gold == null) return;
        final ClassicFont tiny = gold.pack.font(ClassicFont.TINY);
        final ClassicAdvisorBox.Layout l = ClassicAdvisorBox.layout(short0, tiny,
            gold.pack.image(ClassicPackFiles.ssKey(short0.portrait.sprite)));
        assertEquals(new Rectangle(77, 116, 166, 30), l.box);
        assertEquals(new java.awt.Point(130, 54), l.portraitAt);
        final int[] got = ClassicColonyMovesTest.overFrame(gold, gold.frame(94078), l, -1,
            gold.pack.image(ClassicMenuBar.WOOD_KEY), tiny);
        System.out.println(getName() + ": @BUYME0 #94078 " + got[0] + "/" + got[1]
                           + " arrow " + got[2]);
        assertEquals(0, got[1]);
        // @BUYME1 (the review of part V, fidelity L3): dago-colony2 #12061
        // «Artillerie» 1248 of 115103, box (77,108,166,46), the bar on
        // «Na, dann nicht.»; playthrough-2 #37082 «Sägewerk» 364 of 534.
        final Object[][] ones = {
            { "dago-colony2", 12061, "Artillerie", 1248, 115103 },
            { "playthrough-2", 37082, "Sägewerk", 364, 534 },
        };
        for (Object[] o : ones) {
            final java.io.File f = ClassicTerrainGoldenTest.frameFile(
                new java.io.File(gold.recordings, (String) o[0]), (Integer) o[1]);
            if (f == null) {
                System.out.println(getName() + ": " + o[0] + " #" + o[1] + " skipped, no frame");
                continue;
            }
            final ClassicAdvisorBox.Request r1 = ClassicColonyBuild.buyRequest(t, (String) o[2],
                (Integer) o[3], (Integer) o[4]);
            assertEquals("BUYME1", r1.id);
            final ClassicAdvisorBox.Layout l1 = ClassicAdvisorBox.layout(r1, tiny,
                gold.pack.image(ClassicPackFiles.ssKey(r1.portrait.sprite)));
            if ("dago-colony2".equals(o[0])) {
                assertEquals(new Rectangle(77, 108, 166, 46), l1.box);
            }
            final int[] g1 = ClassicColonyMovesTest.overFrame(gold, javax.imageio.ImageIO.read(f),
                l1, 0, gold.pack.image(ClassicMenuBar.WOOD_KEY), tiny);
            System.out.println(getName() + ": @BUYME1 " + o[0] + " #" + o[1] + " " + g1[0] + "/"
                               + g1[1] + " arrow " + g1[2]);
            assertEquals(o[0] + " #" + o[1], 0, g1[1]);
        }
    }

    /**
     * KAUFEN's look and its box's time (the review of part V, fidelity L1,
     * L2; V dago-colony2 K1, K2): pressed, the box inverted (background 54
     * -&gt; 0, x 216-244, y 138-146, #12044 and #12890) and normal again at
     * the release (#12053); the box 8 frames after the release (#12061,
     * #12912).
     */
    public void testKaufenPressedAndItsTime() throws Exception {
        final Scene s = scene(true);
        final Specification spec = spec();
        s.colony.setBuildQueue(List.<BuildableType>of(building(spec, "printingPress")));
        s.colony.addGoods(goods(spec, "tools"), 20);
        s.dutch.setGold(10000);
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        ClassicColonyServerScene.paint(s.panel);
        final int[] normal = ClassicColonyPanel.pixelsOf(s.panel.canvas());
        ClassicColonyServerScene.mouse(s.panel, java.awt.event.MouseEvent.MOUSE_PRESSED, 230, 142,
                                       false);
        final int[] pressed = ClassicColonyPanel.pixelsOf(s.panel.canvas());
        final Rectangle buy = ClassicColonyLayout.BUY_BOX;
        assertFalse("inverted while held", same(normal, pressed, buy));
        s.boxes.answers.add(0);
        ClassicColonyServerScene.mouse(s.panel, java.awt.event.MouseEvent.MOUSE_RELEASED, 230, 142,
                                       false);
        assertTrue("normal again", same(normal, ClassicColonyPanel.pixelsOf(s.panel.canvas()), buy));
        if (t != null) {
            assertEquals("BUYME1", s.boxes.last().id);
            assertEquals(ClassicColonyLayout.frames(8), s.boxes.last().openDelayMs, 1e-9);
            assertEquals(ClassicColonyLayout.frames(8), ClassicColonyBuild.buyRequest(t, "x", 5, 1)
                         .openDelayMs, 1e-9);
        }
        // Against the clip's press frames (the arrow left out).
        final ClassicColonyGolden gold = ClassicColonyGolden.open(getName());
        if (gold == null) return;
        for (int n : new int[] { 12044, 12890 }) {
            final java.io.File f = ClassicTerrainGoldenTest.frameFile(
                new java.io.File(gold.recordings, "dago-colony2"), n);
            if (f == null) continue;
            final BufferedImage frame = javax.imageio.ImageIO.read(f);
            final ClassicColonyGolden.Diff d = ClassicColonyGolden.compare("KAUFEN #" + n, pressed,
                ClassicColonyGolden.pixels(frame), new Rectangle(216, 138, 29, 9),
                gold.pointer(frame), null);
            System.out.println(getName() + ": " + d);
            assertEquals(d.toString(), 0, d.differing);
        }
    }


    // With the real controller and server

    /** What a server scene works with. */
    private static final class Scene {
        Game game;
        ServerPlayer dutch;
        Colony colony;
        ClassicColonyServerScene.Loopback api;
        ClassicColonyServerScene.Boxes boxes;
        ClassicColonyPanel panel;
    }

    /** A clock the test moves (nanoseconds). */
    private static final class FakeClock implements ClassicSlide.Clock {
        long now = 1_000_000_000L;

        @Override
        public long now() {
            return this.now;
        }

        @Override
        public void waitUntil(long due) {
            if (due > this.now) this.now = due;
        }

        void ms(double ms) {
            this.now += Math.round(ms * 1_000_000.0);
        }
    }

    private Scene scene(boolean original) throws Exception {
        return scene(original, ClassicSlide.SYSTEM);
    }

    private Scene scene(boolean original, ClassicSlide.Clock clock) throws Exception {
        spec().setBoolean(GameOptions.ORIGINAL_BUILD_PRICE, original);
        spec().setBoolean(GameOptions.ORIGINAL_BUILD_COMPLETION, original);
        final Scene s = new Scene();
        s.game = ServerTestHelper.startServerGame(getTestMap(true));
        s.dutch = getServerPlayer(s.game, "model.nation.dutch");
        // A human player (the record is not made for the AI).
        s.dutch.setAI(false);
        s.colony = createStandardColony(4);
        s.api = new ClassicColonyServerScene.Loopback(ServerTestHelper.getServer(), s.dutch);
        s.boxes = new ClassicColonyServerScene.Boxes();
        ClassicColonyPanel.forgetFigureOrders();
        s.panel = new ClassicColonyPanel(ClassicColonyServerScene.client(s.game, s.dutch, s.api),
            ClassicColonyBuildingsTest.library(), s.colony, null, s.boxes, clock, false);
        s.panel.setSize(960, 600);
        s.panel.setMode(ClassicColonyRight.Mode.HAMMER);
        ClassicColonyServerScene.paint(s.panel);
        return s;
    }


    /** A carpenter at work (hammers made: FreeCol warns when they go into nothing). */
    private static void carpenter(Scene s) {
        final Specification spec = spec();
        final net.sf.freecol.common.model.Unit u = s.colony.getUnitList().get(0);
        u.setLocation(s.colony.getBuilding(building(spec, "carpenterHouse")));
        u.changeWorkType(goods(spec, "hammers"));
        s.colony.addGoods(goods(spec, "lumber"), 100);
        s.colony.addGoods(goods(spec, "food"), 100);
        s.colony.invalidateCache();
    }
    /** The colony's new turn on the server (its builds) and its turn's warnings. */
    private static ChangeSet newTurn(Scene s) {
        final ChangeSet cs = new ChangeSet();
        ((ServerColony) s.colony).csNewTurn(new java.util.Random(1), new LogBuilder(0), cs);
        ((ServerColony) s.colony).csNewTurnWarnings(new java.util.Random(1), new LogBuilder(0),
                                                    cs);
        return cs;
    }

    /**
     * R4 on the server (levi): a building finished with nothing else
     * queued is recorded as the completed build, the colony's current one
     * on the screen «(ABGESCHLOSSEN)», with its old «(Benötigt …)» line in
     * the hammer panel; the next turns warn of nothing; the player's choice
     * clears it, and the stored hammers carry over (#131120).  Without the
     * rule FreeCol's: nothing recorded, «(Keine Produktion)».
     */
    public void testTheCompletedBuildStaysCurrent() throws Exception {
        final Scene s = scene(true);
        final Specification spec = spec();
        final BuildingType press = building(spec, "printingPress");
        s.colony.setBuildQueue(List.<BuildableType>of(press));
        s.colony.addGoods(goods(spec, "hammers"), 52);
        s.colony.addGoods(goods(spec, "tools"), 20);
        final ChangeSet cs = newTurn(s);
        assertNotNull("built", s.colony.getBuilding(press));
        assertEquals(press.getId(), s.colony.getClassicBuilt());
        assertEquals(s.game.getTurn().getNumber(), s.colony.getClassicBuiltTurn());
        assertSame(press, ClassicColonyBuild.current(s.colony));
        assertTrue(completedThisTurn(s.colony));
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        if (t != null) {
            final ClassicColonyRight.Hammer h = ClassicColonyRight.hammer(s.colony, t);
            assertEquals("Verlag", h.caption);
            assertEquals("(Benötigt 20 Werkzeuge)", h.tools);
        }
        carpenter(s);
        // The next turn: no «nothing is being built» warning.
        final ChangeSet next = newTurn(s);
        assertFalse(next.toString(), next.toString().contains("notBuildingAnything"));
        assertEquals(press.getId(), s.colony.getClassicBuilt());
        // The player's choice: through the server, the record gone, the
        // hammers kept.
        s.colony.addGoods(goods(spec, "hammers"), 36 - s.colony.getGoodsCount(goods(spec, "hammers")));
        final BuildingType news = building(spec, "newspaper");
        assertTrue(s.api.setBuildQueue(s.colony, List.of(news)));
        assertNull(s.colony.getClassicBuilt());
        assertEquals("hammers kept (#131120)", 36,
                     s.colony.getGoodsCount(goods(spec, "hammers")));
        assertNotNull(cs);
    }

    /**
     * The completed build's record in a save: written for the owner, valid
     * against the game schema, read back with its turn (a copy, as the
     * client gets it).
     */
    public void testTheRecordInASave() throws Exception {
        final State s = ClassicColonyBuildingsTest.base1539();
        s.on("expertFarmer", Direction.NW, "grain");
        s.finish();
        s.colony.setClassicBuilt("model.building.printingPress", 47);
        final String xml = s.game.serialize();
        assertTrue(xml.contains("classicBuilt=\"model.building.printingPress\""));
        assertTrue(xml.contains("classicBuiltTurn=\"47\""));
        final javax.xml.validation.Validator v = javax.xml.validation.SchemaFactory
            .newInstance("http://www.w3.org/2001/XMLSchema")
            .newSchema(new java.io.File("schema/data/data-game.xsd")).newValidator();
        try {
            v.validate(new javax.xml.transform.stream.StreamSource(new java.io.StringReader(xml)));
        } catch (org.xml.sax.SAXParseException e) {
            fail(e.getMessage() + " at " + e.getLineNumber() + ":" + e.getColumnNumber());
        }
        final Colony again = s.colony.copy(s.game, Colony.class);
        assertEquals("model.building.printingPress", again.getClassicBuilt());
        assertEquals(47, again.getClassicBuiltTurn());
        s.colony.setClassicBuilt(null, 5);
        assertEquals(-1, s.colony.getClassicBuiltTurn());
        assertFalse(s.game.serialize().contains("classicBuilt"));
    }

    /** Without the levi rule: FreeCol's queue, nothing recorded. */
    public void testFreeColsCompletion() throws Exception {
        final Scene s = scene(false);
        final Specification spec = spec();
        final BuildingType press = building(spec, "printingPress");
        s.colony.setBuildQueue(List.<BuildableType>of(press));
        s.colony.addGoods(goods(spec, "hammers"), 52);
        s.colony.addGoods(goods(spec, "tools"), 20);
        newTurn(s);
        assertNotNull("built", s.colony.getBuilding(press));
        assertNull(s.colony.getClassicBuilt());
        assertNull(ClassicColonyBuild.current(s.colony));
        // FreeCol warns that nothing is being built (the levi rule does not: above).
        carpenter(s);
        final ChangeSet next = newTurn(s);
        assertTrue(next.toString(), next.toString().contains("notBuildingAnything"));
    }

    /**
     * A finished unit stays the current build and is built again (V #276;
     * FreeCol's queue of one unit, REMOVE_EXCEPT_LAST): here artillery
     * (with a Waffenkammer).  (A wagon train
     * over FreeCol's limit of one per colony leaves the queue: FreeCol's
     * limit, the original's not seen.)
     */
    public void testAFinishedUnitIsBuiltAgain() throws Exception {
        final Scene s = scene(true);
        final Specification spec = spec();
        s.colony.addBuilding(new net.sf.freecol.server.model.ServerBuilding(s.game, s.colony,
            building(spec, "armory")));
        final UnitType art = spec.getUnitType("model.unit.artillery");
        s.colony.setBuildQueue(List.<BuildableType>of(art));
        s.colony.addGoods(goods(spec, "hammers"), 200);
        s.colony.addGoods(goods(spec, "tools"), 40);
        newTurn(s);
        assertEquals("one artillery", 1, s.colony.getTile().getUnitList().stream()
                     .filter(u -> u.getType() == art).count());
        assertSame("still the build", art, s.colony.getCurrentlyBuilding());
        assertNull(s.colony.getClassicBuilt());
    }

    /**
     * ÄNDERN and KAUFEN on the screen (the real controller and server):
     * the list's bar on the completed building, its row changes nothing,
     * another row sets the build; KAUFEN short of gold @BUYME0 and nothing;
     * enough gold @BUYME1: «Na, dann nicht.» nothing, «Trotzdem
     * abschließen.» pays 13 per missing hammer.
     */
    public void testChangeAndBuyOnTheScreen() throws Exception {
        final Scene s = scene(true);
        final Specification spec = spec();
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        if (t == null) return;
        final BuildingType press = building(spec, "printingPress");
        s.colony.setBuildQueue(List.<BuildableType>of(press));
        s.colony.addGoods(goods(spec, "hammers"), 52);
        s.colony.addGoods(goods(spec, "tools"), 20);
        newTurn(s);
        // ÄNDERN: its row (the bar's) changes nothing.
        final List<ClassicColonyBuild.Row> rows = ClassicColonyBuild.rows(s.colony, t);
        final int bar = ClassicColonyBuild.barRow(rows);
        assertTrue(rows.get(bar).done);
        s.boxes.answers.add(bar);
        ClassicColonyServerScene.click(s.panel, 280, 142);
        assertEquals("buildlist", s.boxes.last().id);
        assertEquals(bar, s.boxes.last().defaultRow);
        assertEquals("the list +6 frames after the release", ClassicColonyGoods.ms(6),
                     s.boxes.last().openDelayMs, 0.01);
        assertSame(press, ClassicColonyBuild.current(s.colony));
        assertTrue(s.api.log.toString(), s.api.log.isEmpty());
        // Another row: the Lagerhaus.
        int lager = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).type == building(spec, "warehouse")) lager = i;
        }
        assertTrue(lager > 0);
        s.boxes.answers.add(lager);
        ClassicColonyServerScene.click(s.panel, 280, 142);
        assertSame(building(spec, "warehouse"), s.colony.getCurrentlyBuilding());
        assertNull(s.colony.getClassicBuilt());
        // KAUFEN: 80 hammers missing (the Verlag's 52 spent, none stored):
        // 2 x 13 x 80 = 2080 (part X, V 048 #139919: doubled after a
        // completion emptied the hammer area).
        final int price = ClassicColonyBuild.price(s.colony);
        assertEquals(0, s.colony.getGoodsCount(goods(spec, "hammers")));
        assertEquals(2 * 13 * 80, price);
        s.dutch.setGold(price - 1);
        ClassicColonyServerScene.click(s.panel, 230, 142);
        assertEquals("BUYME0", s.boxes.last().id);
        assertEquals(price - 1, s.dutch.getGold());
        s.dutch.setGold(price + 100);
        s.boxes.answers.add(ClassicColonyBuild.BUY_NO);
        ClassicColonyServerScene.click(s.panel, 230, 142);
        assertEquals("BUYME1", s.boxes.last().id);
        assertEquals(price + 100, s.dutch.getGold());
        s.boxes.answers.add(ClassicColonyBuild.BUY_YES);
        ClassicColonyServerScene.click(s.panel, 230, 142);
        assertEquals(s.api.log.toString(), 100, s.dutch.getGold());
        assertEquals(0, s.colony.getPriceForBuilding());
    }

    /**
     * F1 or a right-click in the build list (fixer A after part X, the
     * colony review's must 4: V clip 046 #71516-#71721): a building's row
     * shows its Colonopedia page 29 frames after the list went, the build
     * stays, the list comes again 7 frames after the page with its bar on
     * the current build; a unit's row (the wagon train) and «(Keine
     * Produktion)» have no help.
     */
    public void testTheBuildListsPage() throws Exception {
        final Scene s = scene(true);
        final Specification spec = spec();
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        if (t == null) return;
        final BuildingType press = building(spec, "printingPress");
        s.colony.setBuildQueue(List.<BuildableType>of(press));
        final List<ClassicColonyBuild.Row> rows = ClassicColonyBuild.rows(s.colony, t);
        int mill = -1, wagon = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).type == building(spec, "lumberMill")) mill = i;
            if (rows.get(i).type instanceof UnitType) wagon = i;
        }
        assertTrue(mill > 0);
        assertTrue(wagon > 0);
        assertFalse(ClassicColonyBuild.hasPage(rows.get(wagon)));
        assertFalse(ClassicColonyBuild.hasPage(rows.get(0)));
        assertTrue(ClassicColonyBuild.hasPage(rows.get(mill)));
        s.boxes.answers.add(new ClassicColonyServerScene.Boxes.Help(mill));
        ClassicColonyServerScene.click(s.panel, 280, 142);      // ÄNDERN
        assertEquals(List.of(building(spec, "lumberMill")), s.boxes.pages);
        assertEquals(ClassicColonyMoves.ms(29), s.boxes.pageDelays.get(0), 1e-9);
        final ClassicAdvisorBox.Request first = s.boxes.asked.get(s.boxes.asked.size() - 2);
        final ClassicAdvisorBox.Request again = s.boxes.last();
        assertEquals("buildlist", first.id);
        assertEquals("buildlist", again.id);
        assertEquals("the bar on the current build", first.defaultRow, again.defaultRow);
        assertEquals(ClassicColonyMoves.ms(7), again.chainMs, 1e-9);
        assertNotNull(again.help);
        assertFalse(again.help.covers(wagon));
        assertSame("the build stays", press, s.colony.getCurrentlyBuilding());
        assertTrue(s.api.log.toString(), s.api.log.isEmpty());
    }

    /**
     * KAUFEN through the real controller and server (part X): the price
     * the server takes is the original's.  046 #119741's Verlag with 24
     * hammers and no tools: 484 = 13 x 28 + 6 x 20, the gold drops by
     * exactly that and the colony has its 52 hammers and 20 tools; with
     * the hammer area empty the price is doubled (048 #70208's rule):
     * 2 x (13 x 52 + 6 x 20) = 1592, paid in full by the server too.
     */
    public void testTheServerTakesTheOriginalsPrice() throws Exception {
        final Scene s = scene(true);
        final Specification spec = spec();
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        if (t == null) return;
        final GoodsType hammers = goods(spec, "hammers"), tools = goods(spec, "tools");
        final BuildingType press = building(spec, "printingPress");
        assertEquals(52, press.getRequiredAmountOf(hammers));
        assertEquals(20, press.getRequiredAmountOf(tools));
        s.colony.setBuildQueue(List.<BuildableType>of(press));
        s.colony.removeGoods(hammers);
        s.colony.removeGoods(tools);
        assertEquals("doubled", 1592, ClassicColonyBuild.price(s.colony));
        s.colony.addGoods(hammers, 24);
        assertEquals("046 #119741", 484, ClassicColonyBuild.price(s.colony));
        s.dutch.setGold(1000);
        s.boxes.answers.add(ClassicColonyBuild.BUY_YES);
        ClassicColonyServerScene.click(s.panel, 230, 142);
        assertEquals("BUYME1", s.boxes.last().id);
        assertEquals(s.api.log.toString(), 1000 - 484, s.dutch.getGold());
        assertEquals(52, s.colony.getGoodsCount(hammers));
        assertEquals(20, s.colony.getGoodsCount(tools));
        assertEquals(0, s.colony.getPriceForBuilding());
        // Empty again: the server takes the doubled price.
        s.colony.removeGoods(hammers);
        s.colony.removeGoods(tools);
        s.dutch.setGold(2000);
        assertTrue(s.api.payForBuilding(s.colony));
        assertEquals(2000 - 1592, s.dutch.getGold());
        assertEquals(52, s.colony.getGoodsCount(hammers));
        // Short of it: @BUYME0, nothing paid.
        s.colony.removeGoods(hammers);
        s.colony.removeGoods(tools);
        s.dutch.setGold(1591);
        ClassicColonyServerScene.click(s.panel, 230, 142);
        assertEquals("BUYME0", s.boxes.last().id);
        assertEquals(1591, s.dutch.getGold());
        assertFalse("the server refuses it too", s.api.payForBuilding(s.colony));
        assertEquals(1591, s.dutch.getGold());
    }

    /**
     * Renaming (V clip 040 #48 939, #49 813): a click on the colony's name
     * in the top line opens @RENAMECOLONY with the name preset; Enter with
     * another name renames it (the server's rename), the same name, an
     * empty one or another colony's name changes nothing, Escape keeps it;
     * a click beside the name does nothing; at most 23 characters.
     */
    public void testTheRename() throws Exception {
        final Scene s = scene(true);
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        if (t == null) return;
        final String old = s.colony.getName();
        int x = -1;
        for (int i = 0; i < 320 && x < 0; i++) if (s.panel.nameAt(i, 3)) x = i;
        assertTrue("the name is clickable", x >= 0);
        assertFalse(s.panel.nameAt(x, 8));
        assertFalse(s.panel.nameAt(310, 3));
        s.boxes.answers.add("Cotton Town");
        ClassicColonyServerScene.click(s.panel, x + 2, 3);
        assertEquals("RENAMECOLONY", s.boxes.last().id);
        assertEquals(old, s.boxes.last().field.initial);
        assertEquals("Wie soll diese Kolonie jetzt heißen?", s.boxes.last().plainText());
        assertEquals("Cotton Town", s.colony.getName());
        // Escape: no change.
        ClassicColonyServerScene.click(s.panel, x + 2, 3);
        assertEquals("Cotton Town", s.colony.getName());
        // An empty name: no change.
        s.boxes.answers.add("");
        ClassicColonyServerScene.click(s.panel, x + 2, 3);
        assertEquals("Cotton Town", s.colony.getName());
        // Beside the name (the gold): no box.
        final int asked = s.boxes.asked.size();
        ClassicColonyServerScene.click(s.panel, 300, 3);
        assertEquals(asked, s.boxes.asked.size());
        // 23 characters at most (V #49 476: the 24th key ignored).
        assertTrue(s.boxes.last().field.accepts("Man kann hier umbenenne"));
        assertFalse(s.boxes.last().field.accepts("Man kann hier umbenennen"));
    }

    /**
     * The completion on the screen (targets 4, 5): «Zur Kolonie zoomen.» of
     * the building's @BUILT (part W, M4.11: the trigger is the zoom row)
     * dissolves the new building in (its slot, from 19 frames after the
     * screen is complete, in 55 frames) and blinks ÄNDERN in hammer mode; a
     * press ends the blink; another opening neither.
     * The blink (the review of part V, fidelity M2; V clip 020 #57671 -&gt;
     * #57768 -&gt; #57791): the white cursor stays on the selected
     * colonist's tile; while the dissolve is due or runs nothing blinks
     * (cursor shown, ÄNDERN normal, whatever the blink's half); after it
     * the blink starts again, its first toggle 24 frames after the
     * dissolve's last frame, ÄNDERN inverted in the cursor's off half.
     */
    public void testTheDissolveAndTheBlink() throws Exception {
        final FakeClock clock = new FakeClock();
        final Scene s = scene(true, clock);
        final Specification spec = spec();
        final BuildingType press = building(spec, "printingPress");
        s.colony.setBuildQueue(List.<BuildableType>of(press));
        s.colony.addGoods(goods(spec, "hammers"), 52);
        s.colony.addGoods(goods(spec, "tools"), 20);
        newTurn(s);
        final ClassicColonyPanel p = s.panel;
        // The cursor on a worked tile (the colonist selected last).
        int tile = -1;
        for (int i = 0; i < ClassicColonyLayout.TILE_COUNT; i++) {
            final ClassicColonyTiles.Cell c = p.tiles().cell(i);
            if (i != ClassicColonyLayout.CENTRE_TILE && c != null && c.worker != null) tile = i;
        }
        assertTrue("a worked tile", tile >= 0);
        ClassicColonyPanel.setTileCursor(tile);
        p.blink().arm();   // as the shown screen's addNotify
        final long complete = clock.now();
        p.opened(ClassicColonyRight.Opening.BUILT_BUILDING, press, null);
        assertEquals(ClassicColonyRight.Mode.HAMMER, p.mode());
        assertEquals("the cursor stays", tile, ClassicColonyPanel.tileCursor());
        assertTrue(p.completionBlink());
        final ClassicColonyDissolve d = p.dissolve();
        assertNotNull("the dissolve", d);
        assertTrue(d.changed() > 100);
        // The Verlag's slot is in it.
        Rectangle slot = null;
        for (ClassicColonyBuildings.Placed pl : p.area().slots) {
            if (pl.frame == 19) slot = pl.slot.bounds();
        }
        assertNotNull(slot);
        boolean inSlot = false;
        for (Rectangle r : d.rects()) inSlot |= r.intersects(slot);
        assertTrue(d.describe() + " " + slot, inSlot);
        assertEquals(d.changed(), d.pending(ClassicColonyDissolve.START_MS - 1).length);
        assertEquals(0, d.pending(ClassicColonyDissolve.START_MS
                                  + ClassicColonyLayout.frames(54) + 0.5).length);
        // During the dissolve: the off half shows the on half's picture.
        final Rectangle change = ClassicColonyLayout.CHANGE_BOX;
        final Rectangle cell = p.tiles().cell(tile).bounds();
        clock.ms(ClassicColonyDissolve.START_MS + ClassicColonyLayout.frames(20));
        p.blinkPhase(false);
        final int[] on = ClassicColonyPanel.pixelsOf(p.canvas());
        p.blinkPhase(true);
        final int[] offDuring = ClassicColonyPanel.pixelsOf(p.canvas());
        assertTrue(same(on, offDuring, change));
        assertTrue(same(on, offDuring, cell));
        assertNotNull("still running", p.dissolve());
        // After its last frame: the blink's new phase, the first toggle
        // 24 frames after that frame.
        clock.ms(ClassicColonyLayout.frames(36));
        p.canvas();
        assertNull("over", p.dissolve());
        assertEquals(complete + Math.round((ClassicColonyDissolve.START_MS
            + ClassicColonyLayout.frames(54 + 24)) * 1e6), p.blink().phaseStart()
            + Math.round(ClassicBlink.HALF_PERIOD_MS * 1e6), 2);
        p.blinkPhase(false);
        final int[] on2 = ClassicColonyPanel.pixelsOf(p.canvas());
        p.blinkPhase(true);
        final int[] off = ClassicColonyPanel.pixelsOf(p.canvas());
        assertFalse("ÄNDERN inverted in the off half", same(on2, off, change));
        assertFalse("the cursor off in the off half", same(on2, off, cell));
        assertTrue("the same picture in the on half", same(on, on2, change));
        // A press ends the blink.
        ClassicColonyServerScene.click(s.panel, 230, 40);
        assertFalse(p.completionBlink());
        p.blinkPhase(true);
        assertTrue("ÄNDERN normal", same(on2, ClassicColonyPanel.pixelsOf(p.canvas()), change));
        // A second opening, by a click in that turn: no dissolve, ÄNDERN
        // blinks (fixer A after part X: V clip 049 #33109 -> #33133; I that
        // a second opening blinks too), in the mode the last visit ended in.
        final ClassicColonyPanel q = new ClassicColonyPanel(
            ClassicColonyServerScene.client(s.game, s.dutch, s.api),
            ClassicColonyBuildingsTest.library(), s.colony, null, s.boxes, ClassicSlide.SYSTEM,
            false);
        q.setMode(ClassicColonyRight.Mode.HOUSE);
        q.opened(ClassicColonyRight.Opening.OTHER);
        assertNull(q.dissolve());
        assertTrue(q.completionBlink());
        assertEquals(ClassicColonyRight.Mode.HOUSE, q.mode());
    }

    /**
     * The dissolve's two pictures against clip 020 (V visit 10: the tree
     * in H2 at #57671, the Verlag at #57768; visit 20: the Lagerhaus' slot
     * M1 at #105689 and #105790): the old and the new picture of the
     * dissolving rectangles, 0 px apart from the arrow: the Verlag's the
     * slot with the Rathaus' bells (x 88-167, y 15-41, 4 bells before, 6
     * after) and the band's production row (y 163-174), the old ones from a
     * copy of the colony without the Verlag, in the same 55 frames (the
     * review of part V, fidelity M1); the Lagerhaus' its slot alone (no
     * row changes).  The clip's colony has 8 colonists, ours 5: the row's
     * food and cross are the clip's (6 fish and 10 corn used, 1 stored, 1
     * cross), the bells ours.
     */
    public void testTheDissolveOverTheClip() throws Exception {
        final ClassicColonyGolden gold = ClassicColonyGolden.open(getName());
        if (gold == null) return;
        classic().setBoolean(GameOptions.ORIGINAL_BUILD_COMPLETION, true);
        final StringBuilder out = new StringBuilder();
        int bad = 0;
        final Object[][] cases = {
            { "printingPress", 57671, 57768, new String[] { "printingPress" }, 57767 },
            { "warehouse", 105689, 105790, new String[] { "printingPress", "blacksmithShop",
                                                          "warehouse" }, 105805 },
        };
        for (Object[] c : cases) {
            final State st = base(1547, (String[]) c[3]);
            st.colony.setClassicBuilt("model.building." + c[0], st.game.getTurn().getNumber());
            final ClassicColonyPanel p = ClassicColonyBuildingsTest.panel(st,
                ClassicColonyBuildingsTest.BASE);
            // The clip's colony has 8 colonists (ours 5): its row's food
            // and cross (16 corn used, 1 stored, 1 cross), the bells ours
            // (6 with the Verlag, 4 in the old picture from the copy).
            final boolean press = "printingPress".equals(c[0]);
            if (press) p.setBandAdjust(m -> m.with(6, 10, 1, 0, 1, m.bells));
            p.setMode(ClassicColonyRight.Mode.HAMMER);
            p.canvas();
            // The white cursor: on the selected colonist's tile NE (the
            // Verlag), on the band's first figure (the Lagerhaus).
            if (press) {
                // The colonist selected in his building (the clip's off
                // half shows the tile without a green frame).
                ClassicColonyPanel.setTileCursor(2);
                p.select(p.rowFigures().get(0).unit);
            } else {
                ClassicColonyPanel.setTileCursor(ClassicColonyPanel.CURSOR_OFF_TILES);
                p.select(p.rowFigures().get(0).unit);
            }
            p.canvas();
            p.opened(ClassicColonyRight.Opening.BUILT_BUILDING);
            final ClassicColonyDissolve d = p.dissolve();
            assertNotNull((String) c[0], d);
            out.append("\n  ").append(c[0]).append(' ').append(d.describe());
            // The Verlag: the slot with the Rathaus' bells (V x 88-167, y
            // 15-41) and the band's row; the Lagerhaus: its slot alone.
            assertEquals(d.describe(), (press) ? 2 : 1, d.rects().size());
            if (press) {
                assertEquals(new Rectangle(88, 15, 80, 27), d.rects().get(0));
                assertTrue(d.describe(), d.rects().get(1).y >= 163
                           && d.rects().get(1).y + d.rects().get(1).height <= 175);
                // One dissolve: both regions half done at its 28th frame,
                // done together after its 55th (V #57690-#57744).
                final int[] half = d.pending(ClassicColonyDissolve.START_MS
                                             + ClassicColonyLayout.frames(27) + 0.5);
                int top = 0, row = 0;
                for (int i : half) {
                    if (i / 320 < 100) top++;
                    else row++;
                }
                assertTrue(top + " " + row, top > 0 && row > 0);
                assertEquals(0, d.pending(ClassicColonyDissolve.START_MS
                                          + ClassicColonyLayout.frames(54) + 0.5).length);
            }
            final int[] to = ClassicColonyPanel.pixelsOf(p.canvasAt(ClassicColonyDissolve.START_MS
                + ClassicColonyLayout.frames(60)));
            // The old picture: what shows before the dissolve starts.
            final int[] from = ClassicColonyPanel.pixelsOf(p.canvasAt(0.0));
            for (int k = 0; k < 2; k++) {
                final BufferedImage f = gold.frame((Integer) c[1 + k]);
                final List<Rectangle> regions = new ArrayList<>(d.rects());
                // The band's whole production row (the clip's: x 3-116, y 163-174).
                if (press) regions.add(new Rectangle(0, 163, 119, 12));
                for (Rectangle r : regions) {
                    final ClassicColonyGolden.Diff diff = ClassicColonyGolden.compare(
                        c[0] + " #" + c[1 + k] + " " + r.x + "," + r.y + "," + r.width + "x"
                        + r.height, (k == 0) ? from : to, ClassicColonyGolden.pixels(f),
                        r, gold.pointer(f), null);
                    out.append("\n  ").append(diff);
                    bad += diff.differing;
                }
            }
            // The blink after the dissolve (fidelity M2): the cursor's on
            // half with ÄNDERN normal, its off half with ÄNDERN inverted
            // (#57767 / #57768, #105805 / #105806): ÄNDERN's box and the
            // cursor's place (the NE tile, the band's first figure).
            p.finishDissolve();
            assertNull(p.dissolve());
            // The tile's cursor: its frame's four edges (the rebuilt map's
            // terrain inside is not the clip's); in the off half no white
            // or green pixel on them, in ours and in the clip.
            final Rectangle t = p.tiles().cell(2).bounds();
            final Rectangle[] ring = (press)
                ? new Rectangle[] { new Rectangle(t.x, t.y, t.width, 1),
                                    new Rectangle(t.x, t.y + t.height - 1, t.width, 1),
                                    new Rectangle(t.x, t.y, 1, t.height),
                                    new Rectangle(t.x + t.width - 1, t.y, 1, t.height) }
                : new Rectangle[] { new Rectangle(1, 143, 10, 16) };
            for (int k = 0; k < 2; k++) {
                final int n = (Integer) c[4] + k;
                p.blinkPhase(k == 1);
                final int[] ours = ClassicColonyPanel.pixelsOf(p.canvas());
                final BufferedImage f = gold.frame(n);
                final List<Rectangle> rs = new ArrayList<>();
                rs.add(ClassicColonyLayout.CHANGE_BOX);
                if (press && k == 1) {
                    final int[] th = ClassicColonyGolden.pixels(f);
                    for (Rectangle r : ring) {
                        for (int y = r.y; y < r.y + r.height; y++) {
                            for (int x = r.x; x < r.x + r.width; x++) {
                                for (int[] pic : new int[][] { ours, th }) {
                                    final int v = pic[y * 320 + x];
                                    assertTrue(n + " (" + x + "," + y + ")",
                                               v != 0xFFFFFF && v != 0x55FF55);
                                }
                            }
                        }
                    }
                } else {
                    rs.addAll(Arrays.asList(ring));
                }
                for (Rectangle r : rs) {
                    final ClassicColonyGolden.Diff diff = ClassicColonyGolden.compare(
                        c[0] + " blink #" + n + " " + r.x + "," + r.y + "," + r.width + "x"
                        + r.height, ours, ClassicColonyGolden.pixels(f), r, gold.pointer(f),
                        null);
                    out.append("\n  ").append(diff);
                    bad += diff.differing;
                }
            }
        }
        System.out.println(getName() + ":" + out);
        assertEquals("pixels off:" + out, 0, bad);
    }


    // The review of part V: the record's way, its edges, KAUFEN's guards

    /**
     * The record reaches the owner's client the way an update does (the
     * owner's serialized copy into the client's colony, {@code copyIn});
     * a foreign player's copy has none; the server's clear arrives too
     * (regression L1).
     */
    public void testTheRecordReachesTheClient() throws Exception {
        final Scene s = scene(true);
        final Specification spec = spec();
        // The update as the server sends it: the owner's serialized copy
        // (a colony whose colonist stands outside: a copy's units cannot
        // be copied in here, the scene shares the server's objects).
        final Colony c = createStandardColony(1, 10, 8);
        for (net.sf.freecol.common.model.Unit u : c.getUnitList()) u.setLocation(c.getTile());
        c.setClassicBuilt("model.building.printingPress", 7);
        final Colony update = c.copy(s.game, Colony.class, s.dutch);
        assertEquals("model.building.printingPress", update.getClassicBuilt());
        final ServerPlayer french = getServerPlayer(s.game, "model.nation.french");
        assertNull(c.copy(s.game, Colony.class, french).getClassicBuilt());
        // The client's colony without it, brought up to date by the update.
        c.setClassicBuilt(null, -1);
        assertTrue(c.copyIn(update));
        assertEquals("model.building.printingPress", c.getClassicBuilt());
        assertEquals(7, c.getClassicBuiltTurn());
        // The server's clear arrives the same way.
        update.setClassicBuilt(null, -1);
        assertTrue(c.copyIn(update));
        assertNull(c.getClassicBuilt());
        assertEquals(-1, c.getClassicBuiltTurn());
        // And the server's own clear at a choice.
        s.colony.setClassicBuilt("model.building.printingPress", 7);
        assertTrue(s.api.setBuildQueue(s.colony, List.of(building(spec, "printingPress"))));
    }

    /** An AI colony keeps FreeCol's queue: no record (regression L3). */
    public void testNoRecordForTheAI() throws Exception {
        final Scene s = scene(true);
        final Specification spec = spec();
        s.dutch.setAI(true);
        final BuildingType press = building(spec, "printingPress");
        s.colony.setBuildQueue(List.<BuildableType>of(press));
        s.colony.addGoods(goods(spec, "hammers"), 52);
        s.colony.addGoods(goods(spec, "tools"), 20);
        newTurn(s);
        assertNotNull("built", s.colony.getBuilding(press));
        assertNull(s.colony.getClassicBuilt());
        assertNull(ClassicColonyBuild.current(s.colony));
    }

    /**
     * «(Keine Produktion)» picked while the completed building is current
     * sends an empty queue, which clears the record (regression L5); a
     * pick with an old FreeCol queue replaces only its first item (play
     * L4).
     */
    public void testThePicksAndTheQueue() throws Exception {
        final Scene s = scene(true);
        final Specification spec = spec();
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        if (t == null) return;
        final BuildingType press = building(spec, "printingPress");
        s.colony.setBuildQueue(List.<BuildableType>of(press));
        s.colony.addGoods(goods(spec, "hammers"), 52);
        s.colony.addGoods(goods(spec, "tools"), 20);
        newTurn(s);
        assertSame(press, ClassicColonyBuild.current(s.colony));
        s.boxes.answers.add(0);   // «(Keine Produktion)»
        ClassicColonyServerScene.click(s.panel, 280, 142);
        assertTrue(s.api.log.toString(), s.api.log.contains("buildQueue []"));
        assertNull(s.colony.getClassicBuilt());
        assertNull(ClassicColonyBuild.current(s.colony));
        // An old queue of three: the pick replaces the first.
        final BuildingType lager = building(spec, "warehouse");
        final BuildingType school = building(spec, "schoolhouse");
        final UnitType wagon = spec.getUnitType("model.unit.wagonTrain");
        s.colony.setBuildQueue(new ArrayList<BuildableType>(List.of(lager, school, wagon)));
        final List<ClassicColonyBuild.Row> rows = ClassicColonyBuild.rows(s.colony, t);
        int stockade = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).type == building(spec, "stockade")) stockade = i;
        }
        assertTrue(stockade > 0);
        s.boxes.answers.add(stockade);
        ClassicColonyServerScene.click(s.panel, 280, 142);
        assertEquals(List.of(building(spec, "stockade"), school, wagon), s.colony.getBuildQueue());
        // The rule itself.
        assertEquals("a building not twice", List.of(school, wagon),
                     ClassicColonyBuild.picked(List.of(lager, school, wagon), school));
        assertEquals(List.of(wagon, school, wagon),
                     ClassicColonyBuild.picked(List.of(lager, school, wagon), wagon));
        assertEquals(List.of(), ClassicColonyBuild.picked(List.of(lager, school), null));
        assertEquals(List.of(lager), ClassicColonyBuild.picked(List.of(), lager));
    }

    /**
     * The dissolve and the blink only at «Zur Kolonie zoomen.» of @BUILT
     * (part W, M4.11; before: the first opening in the completion's turn,
     * regression L6 of part V): an opening from the map in that turn
     * neither, a founding neither, a turn later neither.
     */
    public void testTheDissolveOnlyInItsTurn() throws Exception {
        final Scene s = scene(true);
        final Specification spec = spec();
        final BuildingType press = building(spec, "printingPress");
        s.colony.setBuildQueue(List.<BuildableType>of(press));
        s.colony.addGoods(goods(spec, "hammers"), 52);
        s.colony.addGoods(goods(spec, "tools"), 20);
        newTurn(s);
        assertTrue(completedThisTurn(s.colony));
        // A founding's opening in that turn: neither.
        s.panel.opened(ClassicColonyRight.Opening.FOUNDING);
        assertNull(s.panel.dissolve());
        assertFalse(s.panel.completionBlink());
        // An opening from the map in that turn: no dissolve, but ÄNDERN
        // blinks (fixer A after part X: V clip 049 #33109 the new sprite at
        // once, #33133 the first blink).
        assertTrue(ClassicColonyBuild.completedThisTurn(s.colony));
        s.panel.opened(ClassicColonyRight.Opening.OTHER);
        assertNull(s.panel.dissolve());
        assertTrue(s.panel.completionBlink());
        // The next turn: neither.
        s.game.setTurn(new net.sf.freecol.common.model.Turn(
            s.game.getTurn().getNumber() + 1));
        assertFalse(completedThisTurn(s.colony));
        assertFalse(ClassicColonyBuild.completedThisTurn(s.colony));
        assertSame("still the current build", press, ClassicColonyBuild.current(s.colony));
        s.panel.opened(ClassicColonyRight.Opening.OTHER);
        assertNull(s.panel.dissolve());
        assertFalse(s.panel.completionBlink());
    }

    /**
     * The completed row's defensive edges (regression L7, play L6): a
     * record whose building the colony does not have (any more) is no
     * completed build, nor one with something queued; the server drops
     * such a record at the turn's warnings and warns again; a capture
     * drops it (regression L8).
     */
    public void testTheRecordsEdges() throws Exception {
        final Scene s = scene(true);
        final Specification spec = spec();
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        // A record of a building the colony lacks.
        s.colony.setBuildQueue(List.<BuildableType>of());
        s.colony.setClassicBuilt("model.building.printingPress", s.game.getTurn().getNumber());
        assertNull(ClassicColonyBuild.completed(s.colony));
        assertNull(ClassicColonyBuild.current(s.colony));
        assertFalse(s.colony.hasClassicBuilt());
        if (t != null) {
            for (ClassicColonyBuild.Row r : ClassicColonyBuild.rows(s.colony, t)) {
                assertFalse(r.name, r.done);
            }
        }
        carpenter(s);
        ChangeSet cs = newTurn(s);
        assertNull("dropped", s.colony.getClassicBuilt());
        assertTrue(cs.toString(), cs.toString().contains("notBuildingAnything"));
        // A record with something queued: the queue's item.
        final BuildingType press = building(spec, "printingPress");
        s.colony.addBuilding(new net.sf.freecol.server.model.ServerBuilding(s.game, s.colony,
                                                                            press));
        s.colony.setClassicBuilt(press.getId(), s.game.getTurn().getNumber());
        assertTrue(s.colony.hasClassicBuilt());
        assertSame(press, ClassicColonyBuild.completed(s.colony));
        final BuildingType lager = building(spec, "warehouse");
        s.colony.setBuildQueue(List.<BuildableType>of(lager));
        assertNull(ClassicColonyBuild.completed(s.colony));
        assertSame(lager, ClassicColonyBuild.current(s.colony));
        // A capture: the new owner has no record.
        s.colony.setBuildQueue(List.<BuildableType>of());
        assertSame(press, ClassicColonyBuild.completed(s.colony));
        final ServerPlayer french = getServerPlayer(s.game, "model.nation.french");
        ((ServerColony) s.colony).csChangeOwner(french, false, "capture", new ChangeSet());
        assertNull(s.colony.getClassicBuilt());
    }

    /**
     * KAUFEN's guards (play L1, regression L11): a missing good boycotted
     * is not bought (no box, no purchase; the server refuses it too);
     * FreeCol's payForBuilding off: nothing; the manual's B key buys
     * (fidelity L6).
     */
    public void testKaufensGuards() throws Exception {
        final Scene s = scene(true);
        final Specification spec = spec();
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        final GoodsType tools = goods(spec, "tools");
        s.colony.setBuildQueue(List.<BuildableType>of(building(spec, "printingPress")));
        s.dutch.setGold(5000);
        s.dutch.getMarket().setArrears(tools, 500);
        assertSame(tools, ClassicColonyBuild.boycotted(s.colony));
        assertEquals(-1, ClassicColonyBuild.price(s.colony));
        final int asked = s.boxes.asked.size();
        ClassicColonyServerScene.click(s.panel, 230, 142);
        assertEquals("no box", asked, s.boxes.asked.size());
        assertFalse(s.api.log.toString(), s.api.log.stream().anyMatch(l -> l.startsWith("pay")));
        assertFalse("the server refuses", s.api.payForBuilding(s.colony));
        assertEquals(5000, s.dutch.getGold());
        assertEquals(0, s.colony.getGoodsCount(tools));
        // The boycott lifted: the box; the tools in stock: no boycott matters.
        s.dutch.getMarket().setArrears(tools, 0);
        assertNull(ClassicColonyBuild.boycotted(s.colony));
        if (t != null) {
            s.boxes.answers.add(ClassicColonyBuild.BUY_NO);
            ClassicColonyServerScene.click(s.panel, 230, 142);
            assertEquals("BUYME1", s.boxes.last().id);
        }
        s.colony.addGoods(tools, 20);
        s.dutch.getMarket().setArrears(tools, 500);
        assertNull("nothing boycotted is missing", ClassicColonyBuild.boycotted(s.colony));
        assertTrue(ClassicColonyBuild.price(s.colony) > 0);
        // The B key: KAUFEN.
        assertEquals("classic_buyBuild", s.panel.getInputMap(
            javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).get(
                javax.swing.KeyStroke.getKeyStroke("B")));
        if (t != null) {
            final int n = s.boxes.asked.size();
            s.boxes.answers.add(ClassicColonyBuild.BUY_NO);
            s.panel.getActionMap().get("classic_buyBuild").actionPerformed(null);
            assertEquals(n + 1, s.boxes.asked.size());
            assertEquals("BUYME1", s.boxes.last().id);
        }
        // payForBuilding off: nothing.
        final boolean was = spec.getBoolean(GameOptions.PAY_FOR_BUILDING);
        try {
            spec.setBoolean(GameOptions.PAY_FOR_BUILDING, false);
            final int n = s.boxes.asked.size();
            ClassicColonyServerScene.click(s.panel, 230, 142);
            assertEquals("no box", n, s.boxes.asked.size());
        } finally {
            spec.setBoolean(GameOptions.PAY_FOR_BUILDING, was);
        }
    }

    /**
     * No record while something else is queued (the reviewer's R07): the
     * Verlag done with the Lagerhaus queued behind it, the Lagerhaus is
     * the build.
     */
    public void testNoRecordWithAQueue() throws Exception {
        final Scene s = scene(true);
        final Specification spec = spec();
        final BuildingType press = building(spec, "printingPress");
        final BuildingType lager = building(spec, "warehouse");
        s.colony.setBuildQueue(new ArrayList<BuildableType>(List.of(press, lager)));
        s.colony.addGoods(goods(spec, "hammers"), 52);
        s.colony.addGoods(goods(spec, "tools"), 20);
        newTurn(s);
        assertNotNull("built", s.colony.getBuilding(press));
        assertNull(s.colony.getClassicBuilt());
        assertSame(lager, ClassicColonyBuild.current(s.colony));
    }

    /**
     * The zoom's dissolve in every game (regression L11 of part V, now
     * part W: the trigger is «Zur Kolonie zoomen.»): two games whose colony
     * (the same id) finishes a building in the same turn each dissolve it
     * at the zoom; an opening from the map in that turn does not, it only
     * blinks ÄNDERN (fixer A after part X, V clip 049 #33109).
     */
    public void testTheZoomDissolvesInEveryGame() throws Exception {
        String id = null;
        for (int k = 0; k < 2; k++) {
            final Scene s = scene(true);
            final Specification spec = spec();
            final BuildingType press = building(spec, "printingPress");
            s.colony.setBuildQueue(List.<BuildableType>of(press));
            s.colony.addGoods(goods(spec, "hammers"), 52);
            s.colony.addGoods(goods(spec, "tools"), 20);
            newTurn(s);
            if (k == 1) assertEquals("the same colony id", id, s.colony.getId());
            id = s.colony.getId();
            s.panel.opened(ClassicColonyRight.Opening.OTHER);
            assertNull("a click's opening, game " + k, s.panel.dissolve());
            // Its blink (fixer A after part X: V clip 049 #33109, #33133).
            assertTrue(s.panel.completionBlink());
            s.panel.opened(ClassicColonyRight.Opening.BUILT_BUILDING, press, null);
            assertNotNull("the zoom, game " + k, s.panel.dissolve());
            assertTrue(s.panel.completionBlink());
            ServerTestHelper.stopServerGame();
        }
    }

    /** A rename to another colony's name changes nothing (regression L9). */
    public void testTheRenameToAnotherColonysName() throws Exception {
        final Scene s = scene(true);
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        if (t == null) return;
        final Colony other = createStandardColony(1, 10, 8);
        other.setName("Zweite Kolonie");
        assertSame(s.dutch, other.getOwner());
        final String old = s.colony.getName();
        assertFalse(old.equals(other.getName()));
        int x = -1;
        for (int i = 0; i < 320 && x < 0; i++) if (s.panel.nameAt(i, 3)) x = i;
        s.boxes.answers.add(other.getName());
        ClassicColonyServerScene.click(s.panel, x + 2, 3);
        assertEquals("RENAMECOLONY", s.boxes.last().id);
        assertEquals(old, s.colony.getName());
        assertFalse(s.api.log.toString(), s.api.log.stream().anyMatch(l -> l.startsWith("rename")));
    }

    /** @return Whether two pictures are equal inside a rectangle. */
    static boolean same(int[] a, int[] b, Rectangle r) {
        for (int y = r.y; y < r.y + r.height; y++) {
            for (int x = r.x; x < r.x + r.width; x++) {
                if (a[y * 320 + x] != b[y * 320 + x]) return false;
            }
        }
        return true;
    }
}
