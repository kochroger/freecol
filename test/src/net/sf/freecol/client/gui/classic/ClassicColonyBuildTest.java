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
        ClassicColonyPanel.forgetCompletions();
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


    // R1: the price

    /**
     * KAUFEN's price (V clip 020 #94078: 494 = 13 x 38 for the Lagerhaus
     * with 42 of 80 hammers; dago-colony2: 1248 = 13 x 96): with the levi
     * rule 13 per missing hammer, the tools at the market's ask; without it
     * FreeCol's 20 per hammer and the tools 10 % dearer.
     */
    public void testThePrice() {
        final State s = ClassicColonyBuildingsTest.base1539();
        s.on("expertFarmer", Direction.NW, "grain");
        s.finish();
        final Colony c = s.colony;
        final GoodsType hammers = goods(classic(), "hammers"), tools = goods(classic(), "tools");
        final List<AbstractGoods> missing = List.of(new AbstractGoods(hammers, 38));
        classic().setBoolean(GameOptions.ORIGINAL_BUILD_PRICE, true);
        assertEquals("V #94078", 494, c.priceGoodsForBuilding(missing));
        assertEquals("dago-colony2", 1248,
                     c.priceGoodsForBuilding(List.of(new AbstractGoods(hammers, 96))));
        final int ask = c.getOwner().getMarket().getBidPrice(tools, 20);
        assertEquals("the tools at the market's ask", 13 * 10 + ask,
                     c.priceGoodsForBuilding(List.of(new AbstractGoods(hammers, 10),
                                                     new AbstractGoods(tools, 20))));
        classic().setBoolean(GameOptions.ORIGINAL_BUILD_PRICE, false);
        assertEquals("FreeCol: 20 a hammer", 760, c.priceGoodsForBuilding(missing));
        assertEquals("FreeCol: the tools 10 % dearer", 20 * 10 + ask * 110 / 100,
                     c.priceGoodsForBuilding(List.of(new AbstractGoods(hammers, 10),
                                                     new AbstractGoods(tools, 20))));
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

    private Scene scene(boolean original) throws Exception {
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
            ClassicColonyBuildingsTest.library(), s.colony, null, s.boxes, ClassicSlide.SYSTEM,
            false);
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
        assertTrue(ClassicColonyBuild.completedThisTurn(s.colony));
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
        // KAUFEN: 80 hammers missing (the Verlag's 52 spent) = 1040 at 13.
        final int price = ClassicColonyBuild.price(s.colony);
        assertEquals(13 * (80 - s.colony.getGoodsCount(goods(spec, "hammers"))), price);
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
     * The completion on the screen (targets 4, 5): the first opening in the
     * completion's turn dissolves the new building in (its slot, from 19
     * frames after the screen is complete, in 55 frames) and blinks ÄNDERN
     * in hammer mode; a press ends the blink; a second opening neither.
     */
    public void testTheDissolveAndTheBlink() throws Exception {
        final Scene s = scene(true);
        final Specification spec = spec();
        final BuildingType press = building(spec, "printingPress");
        s.colony.setBuildQueue(List.<BuildableType>of(press));
        s.colony.addGoods(goods(spec, "hammers"), 52);
        s.colony.addGoods(goods(spec, "tools"), 20);
        newTurn(s);
        final ClassicColonyPanel p = s.panel;
        p.opened(ClassicColonyRight.Opening.OTHER);
        assertEquals(ClassicColonyRight.Mode.HAMMER, p.mode());
        assertEquals(ClassicColonyPanel.CURSOR_CHANGE, ClassicColonyPanel.tileCursor());
        assertTrue(p.completionBlink());
        final ClassicColonyDissolve d = p.dissolve();
        assertNotNull("the dissolve", d);
        assertTrue(d.changed() > 100);
        final Rectangle r = d.rects().get(0);
        // Inside the Verlag's slot.
        Rectangle slot = null;
        for (ClassicColonyBuildings.Placed pl : p.area().slots) {
            if (pl.frame == 19) slot = pl.slot.bounds();
        }
        assertNotNull(slot);
        assertTrue(r + " in " + slot, slot.contains(r));
        assertEquals(d.changed(), d.pending(ClassicColonyDissolve.START_MS - 1).length);
        assertEquals(0, d.pending(ClassicColonyDissolve.START_MS
                                  + ClassicColonyLayout.frames(54) + 0.5).length);
        // A press ends the blink.
        ClassicColonyServerScene.click(s.panel, 230, 40);
        assertFalse(p.completionBlink());
        // A second opening: neither.
        final ClassicColonyPanel q = new ClassicColonyPanel(
            ClassicColonyServerScene.client(s.game, s.dutch, s.api),
            ClassicColonyBuildingsTest.library(), s.colony, null, s.boxes, ClassicSlide.SYSTEM,
            false);
        q.setMode(ClassicColonyRight.Mode.HOUSE);
        q.opened(ClassicColonyRight.Opening.OTHER);
        assertNull(q.dissolve());
        assertFalse(q.completionBlink());
        assertEquals(ClassicColonyRight.Mode.HOUSE, q.mode());
    }

    /**
     * The dissolve's two pictures against clip 020 (V visit 10: the tree
     * in H2 at #57671, the Verlag at #57768; visit 20: the Lagerhaus' slot
     * M1 at #105689 and #105790): the old and the new picture of the
     * dissolving rectangle, 0 px apart from the arrow.  (The original also
     * dissolves the Rathaus' bells and the band's row: ours change at once,
     * an Unsichtbarkeit.)
     */
    public void testTheDissolveOverTheClip() throws Exception {
        final ClassicColonyGolden gold = ClassicColonyGolden.open(getName());
        if (gold == null) return;
        classic().setBoolean(GameOptions.ORIGINAL_BUILD_COMPLETION, true);
        final StringBuilder out = new StringBuilder();
        int bad = 0;
        final Object[][] cases = {
            { "printingPress", 57671, 57768, new String[] { "printingPress" } },
            { "warehouse", 105689, 105790, new String[] { "printingPress", "blacksmithShop",
                                                          "warehouse" } },
        };
        for (Object[] c : cases) {
            final State st = base(1547, (String[]) c[3]);
            st.colony.setClassicBuilt("model.building." + c[0], st.game.getTurn().getNumber());
            final ClassicColonyPanel p = ClassicColonyBuildingsTest.panel(st,
                ClassicColonyBuildingsTest.BASE);
            p.canvas();
            p.opened(ClassicColonyRight.Opening.BUILT_BUILDING);
            final ClassicColonyDissolve d = p.dissolve();
            assertNotNull((String) c[0], d);
            final Rectangle r = d.rects().get(0);
            final int[] to = ClassicColonyPanel.pixelsOf(p.canvasAt(ClassicColonyDissolve.START_MS
                + ClassicColonyLayout.frames(60)));
            // The old picture: what shows before the dissolve starts.
            final int[] from = ClassicColonyPanel.pixelsOf(p.canvasAt(0.0));
            for (int k = 0; k < 2; k++) {
                final BufferedImage f = gold.frame((Integer) c[1 + k]);
                final ClassicColonyGolden.Diff diff = ClassicColonyGolden.compare(
                    c[0] + " #" + c[1 + k], (k == 0) ? from : to, ClassicColonyGolden.pixels(f),
                    r, gold.pointer(f), null);
                out.append("\n  ").append(diff);
                bad += diff.differing;
            }
        }
        System.out.println(getName() + ":" + out);
        assertEquals("pixels off:" + out, 0, bad);
    }
}
