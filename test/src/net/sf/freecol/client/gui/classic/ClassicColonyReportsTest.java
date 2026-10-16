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
 * advisor, a row-less box's time from its words), the boxes over the
 * clips' frames (clip 020: @NEEDTOOLS, @BUILT, @ORE, @CARGOREADY0 twice,
 * @TUTORIAL6, @TUTORIAL16; clip 040: @BUILT, @CARGOREADY2 and the two
 * reports without rows after a zoom; clips 046-048: @CARGOREADY1 with its
 * rows and without, @CARGOREADY0 without, @NEEDTOOLS0, @LUMBER, @SPOIL1,
 * @DEPLETION, @SONSUP, @TUTORIAL6 after a zoom), the groups by colony, and
 * the GUI's run of them: the zoom with its map jump, the flashed reports,
 * the next colony's after the screen, the hold of the turn start,
 * @TUTORIAL6 after a new load's box whatever row, before a zoom's screen.
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

    /** FreeCol's goods thrown away at the store's capacity. */
    private static ModelMessage waste(Colony c, GoodsType g, int waste) {
        return new ModelMessage(MessageType.WAREHOUSE_CAPACITY,
            "model.colony.warehouseWaste", c, g).addNamed("%goods%", g)
            .addAmount("%waste%", waste).addAmount("%level%", 90)
            .addName("%colony%", c.getName());
    }

    /** FreeCol's worked-out resource near a colony. */
    private static ModelMessage depleted(Colony c) {
        return new ModelMessage(MessageType.WARNING,
            "model.colonyTile.resourceExhausted", c)
            .addNamed("%resource%", spec().getResourceType("model.resource.minerals"))
            .addName("%colony%", c.getName());
    }

    /** FreeCol's Sons of Liberty up (or down). */
    private static ModelMessage sons(Colony c, int oldSoL, int newSoL) {
        return new ModelMessage(MessageType.SONS_OF_LIBERTY,
            (newSoL > oldSoL) ? "model.colony.soLIncrease" : "model.colony.soLDecrease",
            c, goods("bells")).addAmount("%oldSoL%", oldSoL)
            .addAmount("%newSoL%", newSoL).addName("%colony%", c.getName());
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
        // Goods thrown away: @SPOIL1 (clip 048 #5690), the colonist; its
        // zoom in house mode (V #5690 -> #5884/#5887 after a visit that
        // ended in hammer mode; the review of part W, fidelity M3).
        r = ClassicColonyReports.of(t, waste(base, goods("sugar"), 2), game);
        assertEquals("SPOIL1", r.section);
        assertEquals(ClassicColonyReports.Kind.SPOILED, r.kind);
        assertEquals("Zucker", r.values.get("STRING1"));
        assertEquals("2", r.values.get("NUMBER0"));
        assertEquals(ClassicColonyRight.Opening.ORE, r.opening);
        assertSame(ClassicAdvisorBox.Portrait.COLONIST, ClassicColonyReports.portrait(r.kind));
        assertTrue(ClassicColonyReports.request(t, r, true, "t").plainText()
                   .contains("Lagerhaus"));
        assertNull("nothing thrown away", ClassicColonyReports.of(t,
            waste(base, goods("sugar"), 0), game));
        // A worked-out mine: @DEPLETION with the frontiersman (#115237).
        r = ClassicColonyReports.of(t, depleted(base), game);
        assertEquals("DEPLETION", r.section);
        assertEquals("Base", r.values.get("STRING0"));
        assertSame(ClassicAdvisorBox.Portrait.SCOUT, ClassicColonyReports.portrait(r.kind));
        // The Sons of Liberty: @SONSUP / @SONSDOWN with the soldier (046 #66519).
        r = ClassicColonyReports.of(t, sons(base, 9, 11), game);
        assertEquals("SONSUP", r.section);
        assertEquals("11", r.values.get("NUMBER0"));
        assertSame(ClassicAdvisorBox.Portrait.SOLDIER, ClassicColonyReports.portrait(r.kind));
        assertEquals("SONSDOWN", ClassicColonyReports.of(t, sons(base, 21, 19), game).section);
        // Not a report: FreeCol's words (@FOOD1 has the rows too, but
        // FreeCol sends nothing when the food store runs empty).
        assertNull(ClassicColonyReports.of(t, new ModelMessage(MessageType.WARNING,
            "model.colony.famineFeared", base).addName("%colony%", "Base")
            .addAmount("%number%", 2), game));
        assertFalse(ClassicColonyReports.isReportId("model.colony.famineFeared"));
        assertNull(ClassicColonyReports.of(null, built(base, press), game));
        // At the largest warehouse (it cannot grow): @SPOIL3, without the
        // warehouse sentence (GAME.TXT; the review of part W, play L7 and
        // fidelity L2), house mode too; FreeCol's capacity warning as
        // @CARGOREADY2 there.
        assertTrue(ClassicColonyReports.storeCanGrow(base));
        base.getWarehouse().upgrade();
        base.getWarehouse().upgrade();
        assertNull(base.getWarehouse().getType().getUpgradesTo());
        assertFalse(ClassicColonyReports.storeCanGrow(base));
        r = ClassicColonyReports.of(t, waste(base, goods("sugar"), 7), game);
        assertEquals("SPOIL3", r.section);
        assertEquals(ClassicColonyRight.Opening.ORE, r.opening);
        final String spoil3 = ClassicColonyReports.request(t, r, true, "t").plainText();
        assertTrue(spoil3, spoil3.contains("7 Tonnen Zucker"));
        assertFalse(spoil3, spoil3.contains("Lagerhaus"));
        assertEquals("CARGOREADY2", ClassicColonyReports.of(t, new ModelMessage(
            MessageType.WAREHOUSE_CAPACITY, "model.colony.warehouseOverfull", base,
            goods("rum")).addNamed("%goods%", goods("rum")).addName("%colony%", "Base"),
            game).section);
        assertFalse(ClassicColonyReports.storeCanGrow(null));
    }

    /**
     * The boxes: the two LABELS.TXT rows «Runde fortsetzen.» (the bar,
     * Escape) and «Zur Kolonie zoomen.», the colonist; without rows a box
     * that closes by itself after a time from its words.
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
        // Its time from its words: 31 + 0.44 per character (I, the four
        // seen: 58 characters 60 frames, 63 52, 64 63, 194 117).
        final int chars = "Kolonie Base produziert Verlag.".length();
        assertEquals(ClassicColonyLayout.frames(31 + Math.round(0.44 * chars)),
                     f.closeAfterMs, 1e-9);
        assertEquals(57, ClassicColonyReports.flashFrames(58));
        assertEquals(59, ClassicColonyReports.flashFrames(63));
        assertEquals(116, ClassicColonyReports.flashFrames(194));
        assertEquals(31, ClassicColonyReports.flashFrames(0));
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
        // A colony's Sons of Liberty first (V clip 048 #106927 @SONSUP ->
        // #107109 @NEEDTOOLS, FreeCol's order the other way round), the
        // rest in theirs; several goods thrown away in one turn as one
        // @SPOIL2 where the first stood (GAME.TXT, I), one good alone as
        // @SPOIL1.
        final List<ClassicColonyReports.Report> in2 = new ArrayList<>();
        in2.add(ClassicColonyReports.of(t, needTools(a, building("printingPress"), 20, 3), game));
        in2.add(ClassicColonyReports.of(t, waste(a, goods("sugar"), 2), game));
        in2.add(ClassicColonyReports.of(t, waste(b, goods("furs"), 4), game));
        in2.add(ClassicColonyReports.of(t, cargo(a, 0, goods("rum"), 200, 103), game));
        in2.add(ClassicColonyReports.of(t, waste(a, goods("cotton"), 5), game));
        in2.add(ClassicColonyReports.of(t, sons(a, 20, 30), game));
        in2.add(ClassicColonyReports.of(t, sons(b, 40, 30), game));
        final List<List<ClassicColonyReports.Report>> g2 = ClassicColonyReports.grouped(in2);
        assertEquals("[SONSUP Base, NEEDTOOLS Base, SPOIL2 Base, CARGOREADY0 Base]",
                     g2.get(0).toString());
        assertEquals("[SONSDOWN Silver City, SPOIL1 Silver City]", g2.get(1).toString());
        final ClassicColonyReports.Report two = g2.get(0).get(2);
        assertEquals(ClassicColonyReports.Kind.SPOILED, two.kind);
        assertEquals(ClassicColonyRight.Opening.ORE, two.opening);
        assertEquals("Base", two.values.get("STRING0"));
        final String spoil2 = ClassicColonyReports.request(t, two, true, "t").plainText();
        assertTrue(spoil2, spoil2.contains("Ein Teil unserer Waren wurde weggeworfen."));
        assertTrue(spoil2, spoil2.contains("Lagerhaus"));
        // The warehouse at its last level: @SPOIL4, without the sentence.
        a.getWarehouse().upgrade();
        a.getWarehouse().upgrade();
        final ClassicColonyReports.Report four = ClassicColonyReports.spoiled(List.of(
            ClassicColonyReports.of(t, waste(a, goods("sugar"), 2), game),
            ClassicColonyReports.of(t, waste(a, goods("cotton"), 3), game))).get(0);
        assertEquals("SPOIL4", four.section);
        final String spoil4 = ClassicColonyReports.request(t, four, true, "t").plainText();
        assertTrue(spoil4, spoil4.contains("Ein Teil unserer Waren wurde weggeworfen."));
        assertFalse(spoil4, spoil4.contains("Lagerhaus"));
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
     * (42,119,236,24).  Clips 046-048 (Roger's playthrough 4): @CARGOREADY1
     * 046 #116549 (42,102,236,58), @TUTORIAL6 after its zoom 046 #116740,
     * the row-less @CARGOREADY1 048 #135687 (42,110,236,42) and
     * @CARGOREADY0 048 #128660 (42,119,236,24), @NEEDTOOLS0 048 #10023
     * (62,105,196,52), @LUMBER 046 #96631 and @SPOIL1 048 #5690
     * (42,105,236,52), @DEPLETION with MSS3 048 #115237 (77,124,166,40),
     * @SONSUP with MSS1 046 #66521 (39,110,226,58).  Two residues are
     * named and not counted (each must
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
        final Colony silver = colony("Silver City", 15, 11);
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
            // Clips 046-048 (Roger's playthrough 4).
            { new File(key, "c046_116549_cargoready1-zucker.png"), request(t, game,
                cargo(base, 1, goods("sugar"), 100, 100), true),
              new Rectangle(42, 102, 236, 58) },
            { new File(key, "c046_116740_tutorial6-after-zoom.png"), ClassicTips.request(t,
                ClassicTips.CARGO, ClassicColonyReports.cargoTipValues(t,
                    ClassicColonyReports.of(t, cargo(base, 1, goods("sugar"), 100, 100),
                                            game))),
              new Rectangle(49, 115, 226, 42) },
            { new File(key, "c048_135687_rowless-cargoready1-felle.png"), request(t, game,
                cargo(silver, 1, goods("furs"), 100, 100), false),
              new Rectangle(42, 110, 236, 42) },
            { new File(key, "c048_128660_rowless-cargoready0-rum.png"), request(t, game,
                cargo(base, 0, goods("rum"), 200, 100), false),
              new Rectangle(42, 119, 236, 24) },
            { new File(key, "c048_010023_needtools0-verlag.png"), request(t, game,
                needTools(base, building("printingPress"), 20, 0), true),
              new Rectangle(62, 105, 196, 52) },
            { new File(key, "c046_096631_lumber-silver-city.png"), request(t, game,
                noInput(silver, silver.getBuilding(building("carpenterHouse")),
                        goods("lumber")), true),
              new Rectangle(42, 105, 236, 52) },
            { new File(key, "c048_005690_spoil1-zucker.png"), request(t, game,
                waste(base, goods("sugar"), 2), true),
              new Rectangle(42, 105, 236, 52) },
            { new File(key, "c048_115237_depletion-silver-city.png"), request(t, game,
                depleted(silver), true),
              new Rectangle(77, 124, 166, 40) },
            { new File(key, "c046_066521_sonsup-11.png"), request(t, game,
                sons(base, 9, 11), true),
              new Rectangle(39, 110, 226, 58) },
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

        /** The colonies the map jumped to at a zoom. */
        final List<Colony> jumps = new ArrayList<>();

        @Override
        void zoomJump(Colony colony) {
            this.jumps.add(colony);
            super.zoomJump(colony);
        }

        /**
         * The colonies out of view (the map jumps to them before their
         * first report, once), and those the map jumped to (fidelity M1).
         */
        final java.util.Set<Colony> outOfView = new java.util.HashSet<>();
        final List<Colony> reportJumps = new ArrayList<>();

        @Override
        boolean reportJump(Colony colony) {
            if (!this.outOfView.remove(colony)) return false;
            this.reportJumps.add(colony);
            return true;
        }

        /** Whether a colony screen comes up (false: no view, a failed open). */
        boolean screenUp = true;

        /** Whether our ships' arrivals hold the turn start. */
        boolean arrivals = false;

        @Override
        boolean holdForArrivals() {
            return this.arrivals;
        }

        @Override
        java.awt.Image iconOf(net.sf.freecol.common.model.FreeColObject d) {
            return null;   // no image resources in the test
        }

        /** The box id the prompter throws at (L2), or null. */
        String throwAt = null;

        RunGUI(Game game, Player me) {
            super(null);
            this.game = game;
            this.me = me;
            this.prompter = r -> {
                this.asked.add(r);
                if (r.id.equals(this.throwAt)) throw new IllegalStateException("test: " + r.id);
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
                + ((built == null) ? "-" : built.getSuffix())
                + ((docked) ? " docked" : ""));
            return this.screenUp;
        }

        List<String> askedIds() {
            final List<String> out = new ArrayList<>();
            for (ClassicAdvisorBox.Request r : this.asked) {
                out.add(r.id + ((r.id.startsWith("report ") && r.rows.isEmpty()) ? " (flash)" : ""));
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
        assertEquals("[report BUILT, report CARGOREADY0, TUTORIAL6, report NEEDTOOLS,"
                     + " report ORE]", gui.askedIds().toString());
        assertNull("over", gui.reportRun());
        assertTrue(gui.opened.isEmpty());
        assertTrue(gui.jumps.isEmpty());
        final ClassicAdvisorBox.Request tip = gui.asked.get(2);
        assertEquals("33 frames after the rum's box closed",
                     gui.clock.now + ClassicVoyages.nanos(33 * 1000.0 / 70.0863),
                     tip.showAtNanos);
        assertTrue(tip.plainText(), tip.plainText().contains("103 Rum"));
        assertTrue("asked in the run, not by the tips' timer", gui.pendingTips().isEmpty());
        assertTrue(ClassicGUI.tipShown(dutch, ClassicTips.CARGO));

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
        assertEquals("the map jumps to the colony", List.of(a), gui.jumps);
        assertTrue(gui.pendingTips().isEmpty());
        assertNotNull(gui.reportRun());
        assertSame(a, gui.reportRun().zoomed);
        final boolean[] blocked = new boolean[1];
        edt(() -> blocked[0] = g2.turnBlocked());
        assertTrue("the run holds the turn flow", blocked[0]);
        final ClassicOneShot timer = gui.zoomTimer();
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

        // 4. «Zur Kolonie zoomen.» on a new load (V clip 046 #116708): the
        // map jumps to the colony, @TUTORIAL6 comes first, the screen 23
        // frames after the tip's close (V #116872 -> #116896: 24).
        dutch.setClassicTips(0);
        final List<ClassicColonyReports.Report> one = new ArrayList<>();
        one.add(ClassicColonyReports.of(t, cargo(a, 1, goods("sugar"), 100, 100), game));
        final List<List<ClassicColonyReports.Report>> cargoOnly
            = ClassicColonyReports.grouped(one);
        gui = new RunGUI(game, dutch);
        gui.answers.put("report CARGOREADY1", ClassicColonyReports.ROW_ZOOM);
        final RunGUI g4 = gui;
        edt(() -> {
                g4.installScreens(null);
                g4.addReports(cargoOnly, true);
            });
        assertEquals("[report CARGOREADY1, TUTORIAL6]", gui.askedIds().toString());
        assertEquals(List.of(a), gui.jumps);
        final ClassicOneShot t4 = gui.zoomTimer();
        assertTrue(t4.isScheduled());
        assertEquals(gui.clock.now + ClassicVoyages.nanos(ClassicColonyLayout.frames(23)),
                     t4.dueNanos());
        gui.clock.now = t4.dueNanos();
        assertTrue(t4.runIfDue());
        flush();
        assertEquals("[Base OTHER -]", gui.opened.toString());
        // The tip once per game: a second zoom has none.
        gui = new RunGUI(game, dutch);
        gui.answers.put("report CARGOREADY1", ClassicColonyReports.ROW_ZOOM);
        final RunGUI g5 = gui;
        edt(() -> {
                g5.installScreens(null);
                g5.addReports(cargoOnly, true);
            });
        assertEquals("[report CARGOREADY1]", gui.askedIds().toString());
    }

    /**
     * The map shows each colony before its first report (the review of
     * part W, fidelity M1: V clip 048 #135474 view on Base -&gt; #135504 on
     * Silver City -&gt; #135526 its @NEEDTOOLS0, #118906 -&gt; #118915 nine
     * frames): a colony out of view gets the jump and its first box
     * {@link ClassicColonyReports#JUMP_FRAMES} later, its next boxes and a
     * colony in view nothing of the kind (V 046 #66519, 048 #19552); after
     * a zoom the next colony's jump comes when that screen went (V 048
     * #115205 -&gt; #115228 -&gt; #115237).
     */
    public void testTheMapShowsTheColonyBeforeItsReports() throws Exception {
        final ClassicText t = texts("testTheMapShowsTheColonyBeforeItsReports");
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
        in.add(ClassicColonyReports.of(t, needTools(a, press, 20, 3), game));
        in.add(ClassicColonyReports.of(t, needTools(b, press, 20, 3), game));
        in.add(ClassicColonyReports.of(t, noInput(b, smith, goods("ore")), game));
        final List<List<ClassicColonyReports.Report>> groups = ClassicColonyReports.grouped(in);
        assertEquals(9, ClassicColonyReports.JUMP_FRAMES);
        final long nine = ClassicVoyages.nanos(ClassicColonyLayout.frames(9));

        // Base out of view, Silver City in view; every box «Runde fortsetzen.».
        final RunGUI g1 = new RunGUI(game, dutch);
        g1.outOfView.add(a);
        edt(() -> {
                g1.installScreens(null);
                g1.addReports(groups, true);
            });
        assertEquals("[report BUILT, report NEEDTOOLS, report NEEDTOOLS, report ORE]",
                     g1.askedIds().toString());
        assertEquals("the map jumped to Base only", List.of(a), g1.reportJumps);
        assertEquals("Base's first box 9 frames after the jump", g1.clock.now + nine,
                     g1.asked.get(0).showAtNanos);
        assertEquals("its next box: no jump", 0L, g1.asked.get(1).showAtNanos);
        assertEquals("Silver City in view: as soon as it can", 0L, g1.asked.get(2).showAtNanos);
        assertEquals(0L, g1.asked.get(3).showAtNanos);
        assertTrue("no zoom row chosen", g1.jumps.isEmpty());

        // Both out of view, the zoom on Base's first: Silver City's jump
        // only after Base's screen went.
        final RunGUI g2 = new RunGUI(game, dutch);
        g2.outOfView.add(a);
        g2.outOfView.add(b);
        g2.answers.put("report BUILT", ClassicColonyReports.ROW_ZOOM);
        edt(() -> {
                g2.installScreens(null);
                g2.addReports(groups, true);
            });
        assertEquals(List.of(a), g2.reportJumps);
        assertEquals("[report BUILT, report NEEDTOOLS (flash)]", g2.askedIds().toString());
        final ClassicOneShot zt = g2.zoomTimer();
        g2.clock.now = zt.dueNanos();
        assertTrue(zt.runIfDue());
        flush();
        assertEquals("[Base BUILT_BUILDING printingPress]", g2.opened.toString());
        assertEquals("not before the screen went", List.of(a), g2.reportJumps);
        g2.clock.now += 1_000_000_000L;
        final long closed = g2.clock.now;
        edt(() -> g2.reportScreenGone(a));
        flush();
        assertEquals(List.of(a, b), g2.reportJumps);
        assertEquals("[report BUILT, report NEEDTOOLS (flash), report NEEDTOOLS, report ORE]",
                     g2.askedIds().toString());
        assertEquals(closed + nine, g2.asked.get(2).showAtNanos);
        assertNull(g2.reportRun());
    }

    /**
     * The run's wiring (the review of part W, regression L2, L3 and play
     * L2): reports that come while a run is on its way join it and come
     * after the zoom's screen (R20); a zoom whose screen does not come up
     * (no view, a failed open) lets the run go on at once (R42); a throw
     * inside the run ends it, so the turn flow is free again.
     */
    public void testTheRunsWiring() throws Exception {
        final ClassicText t = texts("testTheRunsWiring");
        if (t == null) return;
        final Game game = getStandardGame();
        game.changeMap(getTestMap(true));
        final Colony a = colony("Base", 5, 8);
        final Colony b = colony("Silver City", 10, 4);
        final Player dutch = a.getOwner();
        dutch.setClassicTips(0);
        final BuildingType press = building("printingPress");
        final Building smith = b.getBuilding(building("blacksmithHouse"));
        final List<List<ClassicColonyReports.Report>> baseOnly = ClassicColonyReports.grouped(
            List.of(ClassicColonyReports.of(t, built(a, press), game)));
        final List<List<ClassicColonyReports.Report>> silverOnly = ClassicColonyReports.grouped(
            List.of(ClassicColonyReports.of(t, needTools(b, press, 20, 3), game)));

        // 1. Added to a run on its way: after the zoom's screen.
        final RunGUI g1 = new RunGUI(game, dutch);
        g1.answers.put("report BUILT", ClassicColonyReports.ROW_ZOOM);
        edt(() -> {
                g1.installScreens(null);
                g1.addReports(baseOnly, true);
            });
        assertSame(a, g1.reportRun().zoomed);
        edt(() -> g1.addReports(silverOnly, true));
        assertEquals("[report BUILT]", g1.askedIds().toString());
        final ClassicOneShot t1 = g1.zoomTimer();
        g1.clock.now = t1.dueNanos();
        assertTrue(t1.runIfDue());
        flush();
        assertEquals("[Base BUILT_BUILDING printingPress]", g1.opened.toString());
        edt(() -> g1.reportScreenGone(a));
        flush();
        assertEquals("[report BUILT, report NEEDTOOLS]", g1.askedIds().toString());
        assertNull(g1.reportRun());

        // 2. No screen for the zoom: the run goes on at once.
        final RunGUI g2 = new RunGUI(game, dutch);
        g2.screenUp = false;
        g2.answers.put("report BUILT", ClassicColonyReports.ROW_ZOOM);
        final List<List<ClassicColonyReports.Report>> both = new ArrayList<>(baseOnly);
        both.addAll(silverOnly);
        edt(() -> {
                g2.installScreens(null);
                g2.addReports(both, true);
            });
        final ClassicOneShot t2 = g2.zoomTimer();
        g2.clock.now = t2.dueNanos();
        assertTrue(t2.runIfDue());
        flush();
        assertEquals("[Base BUILT_BUILDING printingPress]", g2.opened.toString());
        assertEquals("[report BUILT, report NEEDTOOLS]", g2.askedIds().toString());
        assertNull("the run is over", g2.reportRun());
        final boolean[] blocked = new boolean[1];
        edt(() -> blocked[0] = g2.turnBlocked());
        assertFalse(blocked[0]);

        // 3. A throw in the run's second box: the run ends there.
        final RunGUI g3 = new RunGUI(game, dutch);
        g3.throwAt = "report NEEDTOOLS";
        final List<List<ClassicColonyReports.Report>> three = ClassicColonyReports.grouped(
            List.of(ClassicColonyReports.of(t, built(a, press), game),
                    ClassicColonyReports.of(t, needTools(b, press, 20, 3), game),
                    ClassicColonyReports.of(t, noInput(b, smith, goods("ore")), game)));
        edt(() -> {
                g3.installScreens(null);
                g3.addReports(three, true);
            });
        assertEquals("[report BUILT, report NEEDTOOLS]", g3.askedIds().toString());
        assertNull("the run ended at the throw", g3.reportRun());
        edt(() -> blocked[0] = g3.turnBlocked() || g3.turnInputBlocked());
        assertFalse("the turn flow and the map are free", blocked[0]);
        // A later run comes as usual.
        g3.throwAt = null;
        edt(() -> g3.addReports(silverOnly, true));
        assertEquals("[report BUILT, report NEEDTOOLS, report NEEDTOOLS]",
                     g3.askedIds().toString());
        assertNull(g3.reportRun());
    }

    /**
     * The turn start's reports with a real turn flow (the review of part
     * W, regression L3): FreeCol's turn report while our new turn is not
     * shown yet: its notices now, its colony reports parked for the hold
     * (R02: held; R03: taken out of the notices), the hold asks our ships'
     * arrivals first (R36) and the reports after them, holding meanwhile;
     * the same messages outside a turn start come at once.
     */
    public void testTheTurnStartsReports() throws Exception {
        final ClassicText t = texts("testTheTurnStartsReports");
        if (t == null) return;
        final Game game = getStandardGame();
        game.changeMap(getTestMap(true));
        final Colony a = colony("Base", 5, 8);
        final Player dutch = a.getOwner();
        dutch.setClassicTips(0);
        final BuildingType press = building("printingPress");
        final ModelMessage famine = new ModelMessage(MessageType.WARNING,
            "model.colony.famineFeared", a).addName("%colony%", "Base")
            .addAmount("%number%", 2);
        final RunGUI gui = new RunGUI(game, dutch);
        final ClassicGuiFlowHost host = new ClassicGuiFlowHost(gui, dutch, gui.clock);
        final ClassicTurnFlow flow = new ClassicTurnFlow(host, gui.clock, host.posted::add,
                                                         false);
        final boolean[] b = new boolean[1];
        edt(() -> {
                gui.installScreens(null);
                gui.useTurnFlow(flow);
                host.myTurn = false;           // the AI phase
                flow.tick();
                host.myTurn = true;            // our new turn, not shown yet
                b[0] = flow.isWaiting();
            });
        assertTrue(b[0]);
        gui.arrivals = true;
        edt(() -> gui.showReportTurnPanel(List.of(built(a, press), famine)));
        flush();
        assertEquals("the notice now, the report at the hold",
                     "[message model.colony.famineFeared]", gui.askedIds().toString());
        assertNotNull(gui.reportRun());
        assertFalse(gui.reportRun().started);
        edt(() -> b[0] = gui.holdTurnStart());
        assertTrue("our ships' arrivals hold first", b[0]);
        flush();
        assertEquals("the reports wait for the arrivals", 1, gui.asked.size());
        assertNotNull(gui.reportRun());
        assertFalse(gui.reportRun().started);
        gui.arrivals = false;
        edt(() -> b[0] = gui.holdTurnStart());
        assertTrue("then the reports hold", b[0]);
        flush();
        assertEquals("[message model.colony.famineFeared, report BUILT]",
                     gui.askedIds().toString());
        assertNull(gui.reportRun());
        edt(() -> b[0] = gui.holdTurnStart());
        assertFalse(b[0]);
        // Outside a turn start: a notice batch at once; the turn report
        // too while the flow shows our turn.
        edt(() -> gui.showModelMessages(List.of(built(a, building("lumberMill")))));
        flush();
        assertEquals(3, gui.asked.size());
        assertEquals("report BUILT", gui.asked.get(2).id);
        final ClassicTurnFlow shown = new ClassicTurnFlow(host, gui.clock, host.posted::add,
                                                          false);
        edt(() -> {
                flow.dispose();
                gui.useTurnFlow(shown);
                b[0] = shown.isWaiting();
                gui.showReportTurnPanel(List.of(built(a, building("lumberMill"))));
            });
        flush();
        assertFalse(b[0]);
        assertEquals(4, gui.asked.size());
        assertEquals("report BUILT", gui.asked.get(3).id);
        edt(() -> shown.dispose());
    }

    /**
     * The zoom and a docking on timers of their own (the review of part W,
     * play L1 and regression L1: on one shared timer the task scheduled
     * second dropped the first, whose hold then stayed for good): a
     * docking's band and a zoom in either order each come at their time,
     * and nothing holds the turn flow afterwards; a docking without its
     * screen (the tip shown) neither.
     */
    public void testTheZoomAndTheDockingKeepTheirTimes() throws Exception {
        final ClassicText t = texts("testTheZoomAndTheDockingKeepTheirTimes");
        if (t == null) return;
        final Game game = getStandardGame();
        game.changeMap(getCoastTestMap(spec().getTileType("model.tile.plains"), true));
        final Colony a = colony("Base", 2, 9);
        final Colony b = colony("Silver City", 2, 4);
        final Player dutch = a.getOwner();
        final Unit ship = new ServerUnit(game, b.getTile(), dutch,
            spec().getUnitType("model.unit.merchantman"));
        final List<List<ClassicColonyReports.Report>> zoomOn = ClassicColonyReports.grouped(
            List.of(ClassicColonyReports.of(t, built(a, building("printingPress")), game)));
        for (int order = 0; order < 3; order++) {
            dutch.setClassicTips((order == 2) ? (1 << ClassicTips.DOCK) : 0);
            final RunGUI gui = new RunGUI(game, dutch);
            gui.answers.put("report BUILT", ClassicColonyReports.ROW_ZOOM);
            edt(() -> {
                    final javax.swing.JLayeredPane pane = new javax.swing.JLayeredPane();
                    pane.setLayout(null);
                    pane.setSize(1280, 800);
                    final javax.swing.JPanel map = new javax.swing.JPanel();
                    pane.add(map, javax.swing.JLayeredPane.DEFAULT_LAYER);
                    gui.installScreens(new ClassicScreenStack(pane, List.of(map), null, null));
                });
            if (order == 1) {
                edt(() -> gui.addReports(zoomOn, true));
                gui.clock.now += 5_000_000L;
                edt(() -> gui.shipDocks(b, ship));
            } else {
                edt(() -> gui.shipDocks(b, ship));
                gui.clock.now += 5_000_000L;
                edt(() -> gui.addReports(zoomOn, true));
            }
            final ClassicOneShot zt = gui.zoomTimer(), dt = gui.dockTimer();
            assertTrue("order " + order, zt.isScheduled());
            assertTrue("order " + order, dt.isScheduled());
            assertTrue(zt.dueNanos() < dt.dueNanos());
            gui.clock.now = zt.dueNanos();
            assertTrue(zt.runIfDue());
            flush();
            assertTrue("the docking still due, order " + order, dt.isScheduled());
            gui.clock.now = dt.dueNanos();
            assertTrue(dt.runIfDue());
            flush();
            assertEquals("order " + order, (order == 2) ? "[Base BUILT_BUILDING printingPress]"
                         : "[Base BUILT_BUILDING printingPress, Silver City OTHER - docked]",
                         gui.opened.toString());
            edt(() -> gui.reportScreenGone(a));
            flush();
            final boolean[] blocked = new boolean[1];
            edt(() -> blocked[0] = gui.turnBlocked() || gui.turnInputBlocked());
            assertFalse("nothing holds, order " + order, blocked[0]);
            assertNull(gui.reportRun());
        }
    }
}
