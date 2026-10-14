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
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.common.model.Building;
import net.sf.freecol.common.model.BuildingType;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.ColonyTile;
import net.sf.freecol.common.model.Direction;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Role;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Turn;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.common.resources.ImageCache;
import net.sf.freecol.server.model.ServerBuilding;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;
import net.sf.freecol.util.test.FreeColTestUtils;

import static net.sf.freecol.client.gui.classic.ClassicColonyBuildings.Family;
import static net.sf.freecol.client.gui.classic.ClassicColonyBuildings.Founding;
import static net.sf.freecol.client.gui.classic.ClassicColonyLayout.Row;
import static net.sf.freecol.client.gui.classic.ClassicColonyLayout.Slot;
import static net.sf.freecol.client.gui.classic.ClassicColonyLayout.SlotClass;


/**
 * The colony screen's building area (build spec M4.2, M4.3; {@link
 * ClassicColonyBuildings}, {@link ClassicColonyLayout}): the slot table,
 * the frames, the slot assignment, the production rows, the workers, the
 * units outside and the band's figure row as pure functions against every
 * case the analyses measured; then golden: the state of clip 020's frames
 * rebuilt in a test game on the classic rules (the colony's buildings and
 * their slots, its colonists, their places and jobs, the units outside,
 * the selection, the hovered slot; a visit replayed where the order of
 * the figure row depends on it), the screen's canvas
 * ({@link ClassicColonyPanel#canvas}) compared with the frame pixel for
 * pixel: the whole building area (x 0-198, y 8-127) and the band's figure
 * row (x 0-118, y 142-158), the original arrow left out, and where a
 * later step draws (the SoL flag and crown, the blinking cursor) those
 * pixels left out and counted.  Skips the golden part without the pack or
 * the recordings ({@code -Dclassic.clips}).
 */
public class ClassicColonyBuildingsTest extends FreeColTestCase {

    private static Specification classic() {
        return spec("classic");
    }

    private static Slot slot(String id) {
        return ClassicColonyLayout.slot(id);
    }

    @Override
    protected void tearDown() throws Exception {
        ClassicColonyPanel.forgetFigureOrders();
        ClassicColonyPanel.selectWarehouseGood(0);
        classic().applyDifficultyLevel("model.difficulty.medium");
        super.tearDown();
    }


    // The slot table (spec M4.2 target 1)

    /** The 15 slots of L §3.1 in dago's draw order, their classes and placeholders. */
    public void testTheSlots() {
        final StringBuilder ids = new StringBuilder();
        for (Slot s : ClassicColonyLayout.SLOTS) ids.append(s.id).append(' ');
        assertEquals("M1 H2 H1 L1 H3 H4 H5 M2 H7 H6 W M3 L2 M4 X", ids.toString().trim());
        final Object[][] t = {
            { "M1", 6, 14, SlotClass.MID }, { "H2", 145, 15, SlotClass.HOUSE },
            { "H1", 56, 13, SlotClass.HOUSE }, { "L1", 87, 11, SlotClass.LARGE },
            { "H3", 173, 18, SlotClass.HOUSE }, { "H4", 8, 41, SlotClass.HOUSE },
            { "H5", 37, 45, SlotClass.HOUSE }, { "M2", 128, 53, SlotClass.MID },
            { "H7", 96, 53, SlotClass.HOUSE }, { "H6", 67, 54, SlotClass.HOUSE },
            { "W", 123, 55, SlotClass.DOCKS }, { "M3", 10, 76, SlotClass.MID },
            { "L2", 66, 87, SlotClass.LARGE }, { "M4", 15, 102, SlotClass.MID },
            { "X", 123, 106, SlotClass.FENCE },
        };
        for (int i = 0; i < t.length; i++) {
            final Slot s = ClassicColonyLayout.SLOTS[i];
            assertEquals(t[i][0], s.id);
            assertEquals(s.id, t[i][1], s.x);
            assertEquals(s.id, t[i][2], s.y);
            assertEquals(s.id, t[i][3], s.cls);
            assertEquals(i, s.index);
            assertTrue(s.id, ClassicColonyLayout.BUILDING_AREA.contains(s.bounds()));
        }
        assertEquals(44, SlotClass.HOUSE.placeholder);
        assertEquals(43, SlotClass.MID.placeholder);
        assertEquals(42, SlotClass.LARGE.placeholder);
        assertEquals(45, SlotClass.DOCKS.placeholder);
        assertEquals(16, SlotClass.FENCE.placeholder);
        // Only M2 and W overlap (dago, buildset §2).
        for (Slot a : ClassicColonyLayout.SLOTS) {
            for (Slot b : ClassicColonyLayout.SLOTS) {
                if (a.index >= b.index) continue;
                final boolean pair = (a.id.equals("M2") && b.id.equals("W"));
                assertEquals(a.id + "/" + b.id, pair, a.bounds().intersects(b.bounds()));
            }
        }
        // Each frame's sprite has its class's size (with the pack).
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        if (pack == null) return;
        for (BuildingType bt : classic().getBuildingTypeList()) {
            final Integer f = ClassicColonyBuildings.frameOf(bt.getId());
            final Family fam = ClassicColonyBuildings.familyOf(bt.getId());
            if (f == null) continue;
            final BufferedImage s = pack.image(ClassicPackFiles.ssKey(
                String.format("BUILDING.SS.%03d", f)));
            if (s == null) continue;
            assertEquals(bt.getId(), fam.cls.w, s.getWidth());
            assertEquals(bt.getId(), fam.cls.h, s.getHeight());
        }
    }

    /**
     * The hover label's box y (dago §2.1, clip008 §2.3, clip 020): the
     * top for built houses, mid slots and the fortification slot, centred
     * for the large slots, the harbour and the trees.
     */
    public void testTheHoverLabelsY() {
        assertEquals("Rathaus (V #2398)", 26, ClassicColonyLayout.hoverY(slot("L1"), false));
        assertEquals("Schmiede (V #96680)", 18, ClassicColonyLayout.hoverY(slot("H3"), false));
        assertEquals("Hafenanlagen", 76, ClassicColonyLayout.hoverY(slot("W"), false));
        assertEquals("water 045", 76, ClassicColonyLayout.hoverY(slot("W"), true));
        assertEquals("Bäume 043", 22, ClassicColonyLayout.hoverY(slot("M1"), true));
        assertEquals("Bäume 044", 25, ClassicColonyLayout.hoverY(slot("H2"), true));
        assertEquals("Bäume 042", 102, ClassicColonyLayout.hoverY(slot("L2"), true));
        assertEquals("Zimmerei", 76, ClassicColonyLayout.hoverY(slot("M3"), false));
        assertEquals("the fence", 106, ClassicColonyLayout.hoverY(slot("X"), true));
        assertEquals("Festung", 106, ClassicColonyLayout.hoverY(slot("X"), false));
        // The box's x: «Bäume» 17-39 over M1, 145-167 over H2 (clip008).
        final ClassicFont tiny = (ClassicPackFiles.runtime() == null) ? null
            : ClassicPackFiles.runtime().font(ClassicFont.TINY);
        if (tiny == null) return;
        final int w = tiny.stringWidth("Bäume");
        assertEquals(17, ClassicColonyLayout.hoverBoxX(6, 44, w));
        assertEquals(145, ClassicColonyLayout.hoverBoxX(145, 23, w));
        assertEquals(81, ClassicColonyLayout.hoverBoxX(66, 53, w));
        assertEquals("« (Außerhalb der Kolonie)» clamped (V clip008 #7808, clip 020 #6609)",
                     110, ClassicColonyLayout.hoverBoxX(123, 73,
                                                   tiny.stringWidth(" (Außerhalb der Kolonie)")));
        assertEquals("«Festung (Außerhalb der Kolonie)» (V dago x 83-198)", 83,
                     ClassicColonyLayout.hoverBoxX(123, 73,
                                                   tiny.stringWidth("Festung (Außerhalb der Kolonie)")));
    }


    // The frames and the families (M4.2 target 3)

    /** The frame table of buildset §6, every classic building in a family. */
    public void testTheFrames() {
        final Object[][] t = {
            { "stockade", 0 }, { "fort", 1 }, { "fortress", 2 },
            { "armory", 3 }, { "magazine", 4 }, { "arsenal", 5 },
            { "docks", 6 }, { "drydock", 7 }, { "shipyard", 8 }, { "townHall", 9 },
            { "schoolhouse", 12 }, { "college", 13 }, { "university", 14 },
            { "warehouse", 15 }, { "warehouseExpansion", 15 }, { "customHouse", 18 },
            { "printingPress", 19 }, { "newspaper", 20 },
            { "weaverHouse", 21 }, { "weaverShop", 22 }, { "textileMill", 23 },
            { "tobacconistHouse", 24 }, { "tobacconistShop", 25 }, { "cigarFactory", 26 },
            { "distillerHouse", 27 }, { "rumDistillery", 28 }, { "rumFactory", 29 },
            { "furTraderHouse", 32 }, { "furTradingPost", 33 }, { "furFactory", 34 },
            { "carpenterHouse", 35 }, { "lumberMill", 36 }, { "church", 37 },
            { "cathedral", 38 }, { "blacksmithHouse", 39 }, { "blacksmithShop", 40 },
            { "ironWorks", 41 }, { "stables", 46 },
        };
        for (Object[] e : t) {
            assertEquals((String) e[0], e[1], ClassicColonyBuildings.frameOf(
                "model.building." + e[0]));
        }
        for (String s : new String[] { "chapel", "depot", "country", "noSuchBuilding" }) {
            assertNull(s, ClassicColonyBuildings.frameOf(s));
        }
        assertEquals(Integer.valueOf(15), ClassicColonyBuildings.lagerFrame(true, false));
        assertEquals(Integer.valueOf(47), ClassicColonyBuildings.lagerFrame(true, true));
        assertEquals(Integer.valueOf(46), ClassicColonyBuildings.lagerFrame(false, true));
        assertNull(ClassicColonyBuildings.lagerFrame(false, false));
        // Every building type of the classic and levi rules has its family,
        // and every family's types exist (no misspelt key).
        final Set<String> types = new HashSet<>();
        for (String rules : new String[] { "classic", "levi" }) {
            for (BuildingType bt : spec(rules).getBuildingTypeList()) {
                assertNotNull(rules + " " + bt.getId(),
                              ClassicColonyBuildings.familyOf(bt.getId()));
                types.add(bt.getSuffix());
            }
        }
        int n = 0;
        for (Family f : Family.values()) {
            for (String s : f.types) {
                assertTrue(f + " " + s, types.contains(s));
                n++;
            }
        }
        assertEquals(types.size(), n);
        assertEquals(15, Family.values().length);
        // The seven of a new colony are FreeCol's automatic buildings, and
        // only those of the original's founding set draw a sprite.
        for (BuildingType bt : classic().getBuildingTypeList()) {
            if (!bt.isAutomaticBuild()) continue;
            final Family f = ClassicColonyBuildings.familyOf(bt.getId());
            assertEquals(bt.getId(), f.founding,
                         ClassicColonyBuildings.frameOf(bt.getId()) != null);
        }
        // The names are NAMES.TXT's (with the pack).
        final ClassicText text = ClassicText.load(ClassicPackFiles.runtime());
        if (text == null) return;
        final List<String[]> names = text.names("BUILDING");
        assertEquals("Rathaus", names.get(9)[0]);
        assertEquals("Hafenanlagen", names.get(6)[0]);
        assertEquals("Sägewerk", names.get(36)[0]);
        assertEquals("Haus des Gerbers", names.get(32)[0]);
        assertEquals("Haus des Schmieds", names.get(39)[0]);
        assertEquals("Schmiede", names.get(40)[0]);
        assertEquals("Verlag", names.get(19)[0]);
        assertEquals("Lagerhaus", names.get(15)[0]);
        assertEquals("Lagerhauserweiterung", names.get(ClassicColonyBuildings.NAMES_EXPANSION)[0]);
        assertEquals("Ställe", names.get(ClassicColonyBuildings.NAMES_STABLES)[0]);
        assertEquals("Bäume", text.misc(ClassicColonyBuildings.MISC_TREES).trim());
        assertEquals("(Außerhalb der Kolonie)",
                     text.misc(ClassicColonyBuildings.MISC_OUTSIDE).trim());
    }


    // The slot assignment (M4.2 target 2)

    /**
     * A colony's founding layout from its id: the Rathaus in L1, the
     * Zimmerei in a mid slot, the five craft houses in five house slots;
     * the same for the same id, different between colonies.
     */
    public void testTheFoundingLayout() {
        final Set<String> layouts = new HashSet<>();
        final Set<String> carpenters = new HashSet<>();
        for (long seed = 0; seed < 500; seed++) {
            final Founding f = Founding.of(seed);
            assertEquals(slot("L1"), f.slots.get(Family.RATHAUS));
            assertEquals(SlotClass.MID, f.slots.get(Family.ZIMMEREI).cls);
            final Set<Slot> houses = new HashSet<>();
            for (Family c : ClassicColonyBuildings.CRAFTS) {
                assertEquals(SlotClass.HOUSE, f.slots.get(c).cls);
                houses.add(f.slots.get(c));
            }
            assertEquals(5, houses.size());
            assertEquals(f.slots, Founding.of(seed).slots);
            layouts.add(f.slots.toString());
            carpenters.add(f.slots.get(Family.ZIMMEREI).id);
        }
        assertTrue(layouts.size() > 400);
        assertEquals(4, carpenters.size());
    }

    /**
     * The rest of the families (V clip 020: Base's Verlag took H2 with H1
     * free, its Lagerhaus M1 with M2 and M3 free): the first free slot of
     * the class in the draw order, in the order they were built; the
     * harbour W, the fortification X, the Kirche the other large slot;
     * each keeps its slot when another is built.
     */
    public void testTheAssignment() {
        final Founding base = Founding.of("L1", "M4", "H5", "H6", "H4", "H7", "H3");
        final Map<Family, Slot> a = ClassicColonyBuildings.assign(base,
            List.of(Family.LAGER, Family.PRESSE));
        assertEquals("V #57464", slot("H2"), a.get(Family.PRESSE));
        assertEquals("V #105544", slot("M1"), a.get(Family.LAGER));
        assertEquals(slot("L2"), a.get(Family.KIRCHE));
        assertEquals(slot("W"), a.get(Family.HAFEN));
        assertEquals(slot("X"), a.get(Family.FORT));
        assertEquals(slot("H3"), a.get(Family.SCHMIED));
        assertNull("not built, no slot yet", ClassicColonyBuildings.assign(base,
            List.of(Family.PRESSE)).get(Family.LAGER));
        final Map<Family, Slot> b = ClassicColonyBuildings.assign(base,
            List.of(Family.PRESSE, Family.ZOLL));
        assertEquals(slot("H2"), b.get(Family.PRESSE));
        assertEquals(slot("H1"), b.get(Family.ZOLL));
        final Map<Family, Slot> c = ClassicColonyBuildings.assign(base,
            List.of(Family.ZOLL, Family.PRESSE));
        assertEquals(slot("H2"), c.get(Family.ZOLL));
        assertEquals(slot("H1"), c.get(Family.PRESSE));
        // dago (buildset §2): the Lagerhaus after the College, in the order
        // they were built, each the first free mid slot.
        final Founding sm1 = Founding.of("L1", "M1", "H2", "H5", "H1", "H4", "H7");
        final Map<Family, Slot> dagoBase = ClassicColonyBuildings.assign(sm1,
            List.of(Family.WAFFEN, Family.SCHULE, Family.LAGER));
        assertEquals("Base: Waffenarsenal M2", slot("M2"), dagoBase.get(Family.WAFFEN));
        assertEquals("Base: College M3", slot("M3"), dagoBase.get(Family.SCHULE));
        assertEquals("Base: Lagerhaus + Ställe M4", slot("M4"), dagoBase.get(Family.LAGER));
        final Map<Family, Slot> furCity = ClassicColonyBuildings.assign(sm1,
            List.of(Family.SCHULE, Family.LAGER));
        assertEquals("Fur City: College M2", slot("M2"), furCity.get(Family.SCHULE));
        assertEquals("Fur City: Lagerhaus M3", slot("M3"), furCity.get(Family.LAGER));
        final Map<Family, Slot> furTownM = ClassicColonyBuildings.assign(sm1,
            List.of(Family.SCHULE, Family.LAGER, Family.WAFFEN));
        assertEquals("Fur Town: Waffenkammer M4", slot("M4"), furTownM.get(Family.WAFFEN));
        // Fur Town (dago): the Rathaus in L2, the Kirche then in L1.
        final Founding furTown = Founding.of("L2", "M1", "H3", "H5", "H2", "H7", "H6");
        assertEquals(slot("L1"), ClassicColonyBuildings.assign(furTown, List.of())
                     .get(Family.KIRCHE));
        // What the colony keeps comes first: the College built before the
        // Lagerhaus keeps M1 though FreeCol's depot (the Lagerhaus' object)
        // is older.
        final Map<Family, Slot> kept = ClassicColonyBuildings.parse(
            ClassicColonyBuildings.format(ClassicColonyBuildings.assign(base,
                List.of(Family.SCHULE))));
        assertEquals(slot("M1"), kept.get(Family.SCHULE));
        final Map<Family, Slot> later = ClassicColonyBuildings.assign(base, kept,
            List.of(Family.LAGER, Family.SCHULE));
        assertEquals(slot("M1"), later.get(Family.SCHULE));
        assertEquals(slot("M2"), later.get(Family.LAGER));
        assertEquals("without what was kept: by FreeCol's ids", slot("M1"),
                     ClassicColonyBuildings.assign(base,
                         List.of(Family.LAGER, Family.SCHULE)).get(Family.LAGER));
        // A kept entry that does not fit is dropped.
        final Map<Family, Slot> bad = ClassicColonyBuildings.parse(
            "SCHULE=H1 PRESSE=H2 ZOLL=H2 NOSUCH=M1 RUM=Q9");
        assertEquals(slot("H2"), bad.get(Family.ZOLL));
        final Map<Family, Slot> fixed = ClassicColonyBuildings.assign(base, bad,
            List.of(Family.SCHULE, Family.PRESSE, Family.ZOLL));
        assertEquals("another class: the first free mid slot", slot("M1"),
                     fixed.get(Family.SCHULE));
        assertEquals("H2 taken twice: the first family keeps it", slot("H2"),
                     fixed.get(Family.ZOLL));
        assertEquals("the other the first free", slot("H1"), fixed.get(Family.PRESSE));
        assertEquals(slot("H4"), fixed.get(Family.RUM));
        assertEquals("RATHAUS=L1 ZIMMEREI=M4", ClassicColonyBuildings.format(
            ClassicColonyBuildings.parse("ZIMMEREI=M4 RATHAUS=L1")));
        // Every slot holds one family at most.
        for (Map<Family, Slot> m : List.of(a, b, c, dagoBase, furCity, furTownM, later, fixed)) {
            assertEquals(m.size(), new HashSet<>(m.values()).size());
        }
    }


    // The rows (M4.3 target 1)

    private static void row(String why, String slot, boolean stables, int n, int w,
                            int first, int pitch, int m, int y, boolean stacked) {
        final Row r = ClassicColonyLayout.buildingRow(slot(slot), stables, n, w);
        assertNotNull(why, r);
        assertEquals(why + " first", first, r.first);
        assertEquals(why + " pitch", pitch, r.pitch);
        assertEquals(why + " icons", m, r.m);
        assertEquals(why + " y", y, r.y);
        assertEquals(why + " stacked", stacked, r.stacked);
    }

    /**
     * dago §2.6's model with S_large 30 against every row the analyses
     * measured (clip 020 L §3.4 and its frames, dago's 18, clip008's 4).
     */
    public void testTheRows() {
        // Clip 020.
        row("#2260 Rathaus 4 bells", "L1", false, 4, 10, 88, 6, 4, 13, false);
        row("#134173 Rathaus 6 bells", "L1", false, 6, 10, 87, 4, 6, 13, false);
        row("pop 1 Rathaus 1 bell", "L1", false, 1, 10, 97, 11, 1, 13, false);
        row("#2260 3 rum", "H4", false, 3, 7, 3, 6, 3, 39, false);
        row("#134173 9 rum", "H4", false, 9, 7, 5, 1, 9, 39, true);
        row("#2260 12 hammers", "M4", false, 12, 8, 16, 1, 12, 104, true);
        row("#3500 18 hammers", "M4", false, 18, 8, 18, 1, 9, 104, true);
        row("#2620 3 coats", "H7", false, 3, 8, 91, 6, 3, 51, false);
        row("#2747 3 cigars", "H6", false, 3, 9, 62, 5, 3, 52, false);
        row("#46039 3 tools", "H3", false, 3, 9, 168, 5, 3, 16, false);
        row("#47291 6 tools", "H3", false, 6, 9, 168, 2, 6, 16, false);
        row("#47291 6 rum", "H4", false, 6, 7, 4, 2, 6, 39, false);
        // dago.
        row("Base Sägewerk 32", "M1", false, 32, 8, 9, 1, 8, 16, true);
        row("Base Eisenhütte 48", "H1", false, 48, 9, 51, 1, 12, 11, true);
        row("Base Rathaus 32", "L1", false, 32, 10, 89, 1, 16, 13, true);
        row("Base Zigarrenfabrik 72", "H5", false, 72, 9, 33, 1, 9, 43, true);
        row("Base Waffenarsenal 48", "M2", false, 48, 6, 130, 1, 12, 55, true);
        row("Base Kathedrale 19", "L2", false, 19, 8, 68, 1, 19, 89, true);
        row("Base Ställe 14", "M4", true, 14, 13, 16, 1, 7, 113, true);
        row("#4300 Rathaus 50", "L1", false, 50, 10, 91, 1, 12, 13, true);
        row("Fur City Sägewerk 12", "M1", false, 12, 8, 7, 1, 12, 16, true);
        row("Fur City Pelzfabrik 54", "H1", false, 54, 8, 54, 1, 6, 11, true);
        row("Fur City Rathaus 20", "L1", false, 20, 10, 87, 1, 20, 13, true);
        row("Fur City Ställe 20", "M3", true, 20, 13, 10, 1, 10, 87, true);
        row("Fur Town Kirche 2", "L1", false, 2, 8, 93, 9, 2, 13, false);
        row("Fur Town Pelzfabrik 72", "H7", false, 72, 8, 93, 1, 9, 51, true);
        row("Fur Town Ställe 16", "M3", true, 16, 13, 11, 1, 8, 87, true);
        row("Fur Town Rathaus 96", "L2", false, 96, 10, 70, 1, 12, 89, true);
        // clip008.
        row("Zimmerei 3 hammers", "M3", false, 3, 8, 10, 7, 3, 78, false);
        row("Schmied 3 tools", "H7", false, 3, 9, 91, 5, 3, 51, false);
        row("Weber 3 cloth", "H6", false, 3, 8, 62, 6, 3, 52, false);
        // The labels: (first + 2, icon y + 1).
        final Row r = ClassicColonyLayout.buildingRow(slot("M4"), false, 12, 8);
        assertEquals(18, r.labelX());
        assertEquals(105, r.labelY());
        assertEquals(7, ClassicColonyLayout.buildingRow(slot("H4"), false, 9, 7).labelX());
        assertEquals(40, ClassicColonyLayout.buildingRow(slot("H4"), false, 9, 7).labelY());
        assertEquals(14, ClassicColonyLayout.buildingRow(slot("L1"), false, 32, 10).labelY());
        // None for nothing, none on the harbour or the fence.
        assertNull(ClassicColonyLayout.buildingRow(slot("L1"), false, 0, 10));
        assertNull(ClassicColonyLayout.buildingRow(slot("W"), false, 3, 8));
        assertNull(ClassicColonyLayout.buildingRow(slot("X"), false, 3, 8));
    }


    // The figures (M4.3 targets 2, 3)

    private static String offs(String slot, int n, int w) {
        return Arrays.toString(ClassicColonyLayout.workerOffsets(slot(slot), n, w));
    }

    /** The workers' places (dago §2.7, L §3.5 and clip 020's frames). */
    public void testTheWorkers() {
        assertEquals("[35]", offs("L1", 1, 6));
        assertEquals("[31, 40]", offs("L1", 2, 6));
        assertEquals("[27, 35, 43]", offs("L2", 3, 6));
        for (String m : new String[] { "M1", "M2", "M3", "M4" }) {
            assertEquals(m, "[27]", offs(m, 1, 8));
            assertEquals(m, "[27]", offs(m, 1, 6));
        }
        assertEquals("[22, 33]", offs("M1", 2, 8));
        assertEquals("[22, 33]", offs("M4", 2, 8));
        assertEquals("[23, 33]", offs("M2", 2, 7));
        assertEquals("[23, 33]", offs("M2", 2, 8));
        assertEquals("[18, 27, 36]", offs("M3", 3, 8));
        assertEquals("H4 (V #2398)", "[9]", offs("H4", 1, 6));
        assertEquals("H6 (V #2747)", "[9]", offs("H6", 1, 6));
        assertEquals("H7 (V #2620)", "[8]", offs("H7", 1, 6));
        assertEquals("H3 (V #19349, #21450)", "[8]", offs("H3", 1, 8));
        assertEquals("H3 (V #21450: 082)", "[8]", offs("H3", 1, 6));
        assertEquals("[9]", offs("H1", 1, 6));
        assertEquals("[5, 12]", offs("H1", 2, 8));
        assertEquals("H4 (V #134173)", "[5, 14]", offs("H4", 2, 6));
        assertEquals("[5, 8, 11]", offs("H1", 3, 8));
        assertEquals("[5, 9, 13]", offs("H5", 3, 6));
        assertEquals("[]", offs("W", 2, 8));
        // The selection frame: x - 3 ... x + w, y + 1 ... y + 16 (V #3500).
        assertEquals(new Rectangle(34, 112, 12, 16), ClassicColonyLayout.workerFrame(37, 111, 8));
    }

    /** The units outside on the fortification slot (p1; V #6560, #46350, #46039). */
    public void testTheUnitsOutside() {
        assertEquals("[157]", Arrays.toString(ClassicColonyLayout.outsideX(1, 8)));
        assertEquals("[152, 163]", Arrays.toString(ClassicColonyLayout.outsideX(2, 8)));
        assertEquals("[146, 157, 168]", Arrays.toString(ClassicColonyLayout.outsideX(3, 8)));
        assertEquals("[]", Arrays.toString(ClassicColonyLayout.outsideX(0, 8)));
        for (int n = 1; n <= 30; n++) {
            final int[] x = ClassicColonyLayout.outsideX(n, 14);
            assertTrue(n + "", x[0] >= 123);
            assertTrue(n + "", x[n - 1] + 14 <= 199);
            for (int i = 1; i < n; i++) assertTrue(n + "", x[i] > x[i - 1]);
        }
    }

    /**
     * The figure row's x (L §5.2, dago §4.2): pitch w + 2, 4 px more
     * before the units outside, closing until the row ends at x 96.
     */
    public void testTheFigureRow() {
        final int[][][] cases = {
            { { 8, 8, 6, 8, 6 }, { 5 }, { 2, 12, 22, 30, 40 } },                  // #2260
            { { 8, 8, 6, 8, 8 }, { 4 }, { 2, 12, 22, 30, 44 } },                  // #6560
            { { 8, 8, 8, 8, 6, 6, 8, 6 }, { 8 }, { 2, 12, 22, 32, 42, 50, 58, 68 } }, // #134173
            { { 8, 8, 6, 8, 6, 8, 6, 8 }, { 5 }, { 2, 12, 22, 30, 40, 52, 62, 70 } }, // #46039
            { { 8, 8, 6, 8, 6, 8, 6, 8 }, { 6 }, { 2, 12, 22, 30, 40, 48, 62, 70 } }, // #46350
            { { 6 }, { 1 }, { 2 } },                                              // #14817
            // dago Base: 19 colonists (083 083 086 087 087 089 089 089 091 091
            // 091 094 094 095 095 096 096 097 098) and the dragoon (076): c 3.
            { { 6, 6, 8, 8, 8, 8, 8, 8, 6, 6, 6, 8, 8, 8, 8, 7, 7, 7, 6, 14 }, { 19 },
              { 2, 4, 6, 10, 14, 18, 22, 26, 30, 32, 34, 36, 40, 44, 48, 52, 55, 58, 61, 67 } },
        };
        for (int[][] c : cases) {
            assertEquals(Arrays.toString(c[0]), Arrays.toString(c[2]),
                         Arrays.toString(ClassicColonyLayout.figureRow(c[0], c[1][0])));
        }
        // Many: every step at least 1, the row still from 2.
        final int[] many = new int[60];
        Arrays.fill(many, 8);
        final int[] x = ClassicColonyLayout.figureRow(many, 60);
        assertEquals(2, x[0]);
        for (int i = 1; i < x.length; i++) assertTrue(x[i] > x[i - 1]);
        assertEquals(new Rectangle(1, 143, 10, 16), ClassicColonyLayout.figureFrame(2, 8));
        // The job index by the good.
        assertEquals(0, ClassicColonyLayout.jobOf("model.goods.food"));
        assertEquals(0, ClassicColonyLayout.jobOf("model.goods.grain"));
        assertEquals(5, ClassicColonyLayout.jobOf("model.goods.lumber"));
        assertEquals(8, ClassicColonyLayout.jobOf("model.goods.fish"));
        assertEquals(9, ClassicColonyLayout.jobOf("model.goods.rum"));
        assertEquals(13, ClassicColonyLayout.jobOf("model.goods.hammers"));
        assertEquals(17, ClassicColonyLayout.jobOf("model.goods.bells"));
        assertEquals(-1, ClassicColonyLayout.jobOf("model.goods.horses"));
        assertEquals(54, ClassicColonyLayout.goodsIcon("model.goods.hammers"));
        assertEquals(62, ClassicColonyLayout.goodsIcon("model.goods.bells"));
        assertEquals(31, ClassicColonyLayout.goodsIcon("model.goods.rum"));
        assertEquals(57, ClassicColonyLayout.goodsIcon("model.goods.fish"));
        assertEquals(22, ClassicColonyLayout.goodsIcon("model.goods.grain"));
    }

    /** The figure row's order during a visit: kept, the newcomers at the end. */
    public void testTheFigureRowKeepsItsOrder() {
        final Game game = getStandardGame("classic");
        final Unit[] u = new Unit[4];
        for (int i = 0; i < u.length; i++) {
            u[i] = new ServerUnit(game, null, game.getPlayerByNationId("model.nation.dutch"),
                                  classic().getUnitType("model.unit.freeColonist"));
        }
        assertEquals(List.of(u[0], u[1]), ClassicColonyBuildings.keepOrder(null,
            List.of(u[0], u[1])));
        assertEquals(List.of(u[1], u[0], u[2]), ClassicColonyBuildings.keepOrder(
            List.of(u[1], u[0]), List.of(u[0], u[2], u[1])));
        assertEquals(List.of(u[1], u[3]), ClassicColonyBuildings.keepOrder(
            List.of(u[1], u[0]), List.of(u[3], u[1])));
    }


    // A test colony

    /** A client of the test's game: its game and our player, nothing else. */
    static FreeColClient client(Game game, Player me) throws Exception {
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
    static ImageLibrary library() {
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

    /** A colony of the classic rules being rebuilt in a clip's state. */
    static final class State {

        final Game game;
        final Colony colony;
        final Player owner;
        private Unit spare;

        State(String name, int year) {
            this.game = getStandardGame("classic");
            // Clip 020's game is on «Entdecker» (the king calls the
            // player so, #15627): FreeCol's very easy level, whose
            // government limits (8, 12 Tories) the colonies stay under.
            classic().applyDifficultyLevel("model.difficulty.veryEasy");
            final net.sf.freecol.common.model.Map map = new MapBuilder(this.game)
                .setBaseTileType(classic().getTileType("model.tile.plains")).build();
            this.game.changeMap(map);
            for (int n = 1; n < 1000; n++) {
                final Turn t = new Turn(n);
                if (t.getYear() == year) {
                    this.game.setTurn(t);
                    break;
                }
            }
            this.owner = this.game.getPlayerByNationId("model.nation.dutch");
            this.colony = FreeColTestUtils.getColonyBuilder().player(this.owner)
                .colonyTile(map.getTile(5, 8)).initialColonists(1).build();
            this.colony.setName(name);
            this.colony.setBuildQueue(new ArrayList<>());
            this.spare = this.colony.getUnitList().get(0);
        }

        BuildingType type(String id) {
            return classic().getBuildingType("model.building." + id);
        }

        /** Build a new family, or upgrade one to this level. */
        State build(String id) {
            final BuildingType bt = type(id);
            final BuildingType from = bt.getUpgradesFrom();
            final Building had = (from == null) ? null : this.colony.getBuilding(from);
            if (had != null && had.getType() == from) {
                // The upgrade in place (the object kept, as the server does),
                // whatever the population: Building.setType.
                try {
                    final java.lang.reflect.Method m = Building.class
                        .getDeclaredMethod("setType", BuildingType.class);
                    m.setAccessible(true);
                    m.invoke(had, bt);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(id, e);
                }
            } else {
                this.colony.addBuilding(new ServerBuilding(this.game, this.colony, bt));
            }
            this.colony.invalidateCache();
            return this;
        }

        /** A unit of the type made (the colony's first colonist reused for the first). */
        Unit unit(String type, String role) {
            final UnitType ut = classic().getUnitType("model.unit." + type);
            final Unit u;
            if (this.spare != null && role == null) {
                u = this.spare;
                this.spare = null;
                u.changeType(ut);
            } else {
                final Role r = (role == null) ? null : classic().getRole("model.role." + role);
                u = (r == null) ? new ServerUnit(this.game, this.colony.getTile(), this.owner, ut)
                    : new ServerUnit(this.game, this.colony.getTile(), this.owner, ut, r);
            }
            return u;
        }

        /** A colonist in a building. */
        Unit in(String type, String building) {
            return move(unit(type, null), building);
        }

        /** Move a unit into a building. */
        Unit move(Unit u, String building) {
            final Building b = this.colony.getBuilding(type(building));
            assertNotNull(building, b);
            // As the server puts a colonist to work there: his work type the
            // building's good, its production type the attended one
            // (whatever the input in stock, which the clip's state has).
            assertTrue(building, u.setLocation(b));
            final net.sf.freecol.common.model.ProductionType pt
                = b.getAvailableProductionTypes(false).get(0);
            final GoodsType out = pt.getOutputs().findFirst().get().getType();
            u.changeWorkType(out);
            if (b.getProductionType() != pt) b.setProductionType(pt);
            this.colony.invalidateCache();
            return u;
        }

        /** A colonist on a work tile, working a good. */
        Unit on(String type, Direction d, String goods) {
            return work(unit(type, null), d, goods);
        }

        /** Move a unit to a work tile and its good. */
        Unit work(Unit u, Direction d, String goods) {
            final Tile t = this.colony.getTile().getNeighbourOrNull(d);
            final ColonyTile ct = this.colony.getColonyTile(t);
            assertNotNull(d.toString(), ct);
            u.setLocation(ct);
            final GoodsType gt = classic().getGoodsType("model.goods." + goods);
            u.changeWorkType(gt);
            this.colony.invalidateCache();
            return u;
        }

        /** A unit standing outside the colony on its tile. */
        Unit outside(String type, String role) {
            final UnitType ut = classic().getUnitType("model.unit." + type);
            return (role == null)
                ? new ServerUnit(this.game, this.colony.getTile(), this.owner, ut)
                : new ServerUnit(this.game, this.colony.getTile(), this.owner, ut,
                                 classic().getRole("model.role." + role));
        }

        /** A unit that stood outside joins a building or a tile. */
        void finish() {
            assertNull("the colony's first colonist was used", this.spare);
        }
    }

    /** Base's founding layout in clip 020 (L §3.2). */
    static final Founding BASE = Founding.of("L1", "M4", "H5", "H6", "H4", "H7", "H3");

    /** Provisorische Kolonie's (#14817). */
    static final Founding PROV = Founding.of("L1", "M2", "H6", "H4", "H7", "H1", "H5");

    /** Silver City's (#123003). */
    static final Founding SILVER = Founding.of("L1", "M1", "H6", "H4", "H2", "H5", "H1");

    /** Base in 1539 (#2260): the Sägewerk and the Hafenanlagen built. */
    static State base1539() {
        return base(1539);
    }

    /** Base in a year with the Sägewerk and the Hafenanlagen. */
    static State base(int year) {
        return new State("Base", year).build("lumberMill").build("docks");
    }

    /** A test colony's model shows its buildings in their slots. */
    public void testTheModelOfATestColony() throws Exception {
        final State s = base1539();
        final Unit vet = s.in("veteranSoldier", "townHall");
        final Unit sugar = s.in("masterSugarPlanter", "distillerHouse");
        s.in("masterCarpenter", "lumberMill");
        s.on("expertFarmer", Direction.NW, "grain");
        s.finish();
        final ClassicColonyBuildings.Assets a = ClassicColonyBuildings.Assets.load(
            ClassicPackFiles.runtime());
        final ClassicColonyBuildings.Model m = ClassicColonyBuildings.model(s.colony,
            ClassicText.load(ClassicPackFiles.runtime()), a, BASE, List.of(), null);
        final String expect = "M1=043 H2=044 H1=044 L1=009 H3=039 H4=027 H5=021 M2=043"
            + " H7=032 H6=024 W=006 M3=043 L2=042 M4=036 X=016";
        final StringBuilder got = new StringBuilder();
        for (ClassicColonyBuildings.Placed p : m.slots) {
            got.append(p.slot.id).append('=').append(String.format("%03d", p.frame)).append(' ');
        }
        assertEquals(expect, got.toString().trim());
        // The work targets: the buildings with workplaces, not the trees.
        assertNotNull(m.slots.get(slot("L1").index).target);
        assertNull(m.slots.get(slot("M1").index).target);
        assertNull("the harbour has no workplaces", m.slots.get(slot("W").index).target);
        // The workers in their buildings.
        assertEquals(vet, m.slots.get(slot("L1").index).figures.get(0).unit);
        assertEquals(sugar, m.slots.get(slot("H4").index).figures.get(0).unit);
        // The colony's own layout (from its id) and later families.
        final ClassicColonyBuildings.Model own = ClassicColonyBuildings.model(s.colony, null,
            a, null, List.of(), null);
        final Founding f = Founding.of(s.colony.getIdNumber());
        assertEquals(36, own.slots.get(f.slots.get(Family.ZIMMEREI).index).frame);
        s.build("printingPress");
        final ClassicColonyBuildings.Model m2 = ClassicColonyBuildings.model(s.colony, null,
            a, BASE, List.of(), null);
        assertEquals("the Verlag in the first free house slot", 19,
                     m2.slots.get(slot("H2").index).frame);
        s.build("customHouse");
        s.build("warehouse");
        s.build("stables");
        s.build("blacksmithShop");
        final ClassicColonyBuildings.Model m3 = ClassicColonyBuildings.model(s.colony, null,
            a, BASE, List.of(), null);
        assertEquals("the Verlag stays", 19, m3.slots.get(slot("H2").index).frame);
        assertEquals("the Zollhaus next", 18, m3.slots.get(slot("H1").index).frame);
        assertEquals("Lagerhaus and Ställe", 47, m3.slots.get(slot("M1").index).frame);
        assertEquals("the upgrade in place", 40, m3.slots.get(slot("H3").index).frame);
        // A copy through FreeCol's serialization (a save and its load keep
        // the ids): the same layout, slot by slot.
        final Colony again = s.colony.copy(s.game, Colony.class);
        assertNotNull(again);
        assertEquals(s.colony.getIdNumber(), again.getIdNumber());
        assertEquals(m3.toString(), ClassicColonyBuildings.model(again, null, a, BASE,
            List.of(), null).toString());
        assertEquals(ClassicColonyBuildings.model(s.colony, null, a, null, List.of(), null)
                     .toString(),
                     ClassicColonyBuildings.model(again, null, a, null, List.of(), null)
                     .toString());
        s.build("warehouseExpansion");
        final ClassicColonyBuildings.Model m4 = ClassicColonyBuildings.model(s.colony,
            ClassicText.load(ClassicPackFiles.runtime()), a, BASE, List.of(), null);
        assertTrue("the expansion's «2»", m4.slots.get(slot("M1").index).expansion);
        if (ClassicText.load(ClassicPackFiles.runtime()) != null) {
            assertEquals("Ställe", m4.slots.get(slot("M1").index).label);
            assertEquals("Bäume", m4.slots.get(slot("M2").index).label);
            assertEquals(" (Außerhalb der Kolonie)", m4.slots.get(slot("X").index).label);
            assertEquals("Sägewerk", m4.slots.get(slot("M4").index).label);
            s.build("stockade");
            final ClassicColonyBuildings.Model m5 = ClassicColonyBuildings.model(s.colony,
                ClassicText.load(ClassicPackFiles.runtime()), a, BASE, List.of(), null);
            assertEquals(0, m5.slots.get(slot("X").index).frame);
            assertEquals("Einpfählung (Außerhalb der Kolonie)",
                         m5.slots.get(slot("X").index).label);
        }
    }


    /**
     * The colony keeps its picture ({@code Colony.classicSlots}, written by
     * the screen, saved with the game): a College that showed first keeps
     * M1 when the Lagerhaus comes (dago's order), though FreeCol's ids
     * alone would put the Lagerhaus' depot first; the layout travels with
     * the colony through FreeCol's serialization (a save, a load, the
     * server's updates), and an update without it keeps ours.
     */
    public void testTheColonyKeepsItsLayout() throws Exception {
        final State s = base1539();
        s.in("veteranSoldier", "townHall");
        s.finish();
        s.build("schoolhouse");
        assertNull(s.colony.getClassicSlots());
        final ClassicColonyPanel p = new ClassicColonyPanel(client(s.game, s.owner),
            library(), s.colony, null, null, ClassicSlide.SYSTEM, false);
        p.setFounding(BASE);
        p.canvas();
        assertEquals("RATHAUS=L1 SCHULE=M1 WEBER=H5 TABAK=H6 RUM=H4 PELZ=H7 ZIMMEREI=M4"
                     + " KIRCHE=L2 SCHMIED=H3", withoutFixed(s.colony.getClassicSlots()));
        s.build("warehouse");
        p.canvas();
        assertEquals("the College stays", 12, p.area().slots.get(slot("M1").index).frame);
        assertEquals("the Lagerhaus in the next free", 15,
                     p.area().slots.get(slot("M2").index).frame);
        assertTrue(s.colony.getClassicSlots().contains("LAGER=M2"));
        // Through FreeCol's serialization.
        final Colony again = s.colony.copy(s.game, Colony.class);
        assertEquals(s.colony.getClassicSlots(), again.getClassicSlots());
        final ClassicColonyBuildings.Assets a = ClassicColonyBuildings.Assets.load(
            ClassicPackFiles.runtime());
        final ClassicColonyBuildings.Model m = ClassicColonyBuildings.model(again, null, a,
            null, List.of(), null);
        assertEquals(12, m.slots.get(slot("M1").index).frame);
        assertEquals(15, m.slots.get(slot("M2").index).frame);
        assertEquals(36, m.slots.get(slot("M4").index).frame);
        // An update without it keeps ours.
        final String kept = s.colony.getClassicSlots();
        again.setClassicSlots(null);
        s.colony.copyIn(again);
        assertEquals(kept, s.colony.getClassicSlots());
        // And without a kept layout: by FreeCol's ids (the depot older).
        s.colony.setClassicSlots(null);
        final ClassicColonyBuildings.Model fresh = ClassicColonyBuildings.model(s.colony,
            null, a, BASE, List.of(), null);
        assertEquals(15, fresh.slots.get(slot("M1").index).frame);
        assertEquals(12, fresh.slots.get(slot("M2").index).frame);
    }

    /**
     * Golden (dago #448, Base 1729): the Lagerhaus with Ställe (047) and
     * the expansion's white «2» at sprite + (21,8) in M4: the white pixels
     * of x 28-46, y 104-120 as the frame's: the glyph's 8 (y 110: x 36-37,
     * 111: 38, 112: 37, 113: 36, 114: 36-38; buildset §3).
     */
    public void testTheExpansionsTwoAgainstDago() throws Exception {
        final ClassicColonyGolden gold = ClassicColonyGolden.open(getName());
        if (gold == null) return;
        final java.io.File f = ClassicTerrainGoldenTest.frameFile(
            new java.io.File(gold.recordings, "dago-colony"), 448);
        if (f == null) {
            System.err.println(getName() + ": no dago frame, skipped");
            return;
        }
        final BufferedImage frame = javax.imageio.ImageIO.read(f);
        final State s = base1539();
        s.in("veteranSoldier", "townHall");
        s.finish();
        s.colony.setClassicSlots("LAGER=M4");
        s.build("warehouse").build("warehouseExpansion").build("stables");
        final ClassicColonyBuildings.Assets a = ClassicColonyBuildings.Assets.load(gold.pack);
        final ClassicColonyBuildings.Model m = ClassicColonyBuildings.model(s.colony, null, a,
            Founding.of("L1", "M1", "H5", "H6", "H4", "H7", "H3"), List.of(), null);
        final ClassicColonyBuildings.Placed lager = m.slots.get(slot("M4").index);
        assertEquals(47, lager.frame);
        assertTrue(lager.expansion);
        final BufferedImage img = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
        final java.awt.Graphics2D g = img.createGraphics();
        ClassicColonyBuildings.paint(g, ClassicColonyChrome.Assets.load(gold.pack), a,
            new ClassicColonyBuildings.Model(List.of(lager)), null, false);
        g.dispose();
        int whites = 0;
        final StringBuilder diff = new StringBuilder();
        for (int y = 104; y <= 120; y++) {
            for (int x = 28; x <= 46; x++) {
                final boolean ours = (img.getRGB(x, y) & 0xFFFFFF) == 0xFFFFFF;
                final boolean theirs = (frame.getRGB(x, y) & 0xFFFFFF) == 0xFFFFFF;
                if (theirs) whites++;
                if (ours != theirs) diff.append(" (").append(x).append(',').append(y).append(')');
            }
        }
        System.err.println(getName() + ": " + whites + " white px in the frame, differing:"
                           + ((diff.length() == 0) ? " none" : diff));
        assertEquals("the glyph's 8 (the sprite has no white there)", 8, whites);
        assertEquals("white pixels differ:" + diff, 0, diff.length());
    }

    /** The kept layout is valid in a save (the game schema knows {@code classicSlots}). */
    public void testTheLayoutIsValidInASave() throws Exception {
        final State s = base1539();
        s.in("veteranSoldier", "townHall");
        s.finish();
        s.colony.setClassicSlots("RATHAUS=L1 ZIMMEREI=M4 LAGER=M1");
        final javax.xml.validation.Validator v = javax.xml.validation.SchemaFactory
            .newInstance("http://www.w3.org/2001/XMLSchema")
            .newSchema(new java.io.File("schema/data/data-game.xsd")).newValidator();
        final String xml = s.game.serialize();
        assertTrue(xml.contains("classicSlots=\"RATHAUS=L1 ZIMMEREI=M4 LAGER=M1\""));
        try {
            v.validate(new javax.xml.transform.stream.StreamSource(new java.io.StringReader(xml)));
        } catch (org.xml.sax.SAXParseException e) {
            fail(e.getMessage() + " at " + e.getLineNumber() + ":" + e.getColumnNumber());
        }
    }

    /** A layout without the slots that have no choice (W, X). */
    private static String withoutFixed(String s) {
        return s.replace("HAFEN=W", "").replace("FORT=X", "").replaceAll("\\s+", " ").trim();
    }

    /**
     * The units outside the colony: the persons on its tile (L §3.6; clip
     * 019 #1290: the fortified dragoon on the Festung, its three artillery
     * not), in the order of their figure's job (I, #46039), ties in the
     * screen's order.
     */
    public void testTheUnitsOutsideAreThePersons() {
        final State s = base1539();
        s.in("veteranSoldier", "townHall");
        s.finish();
        final Unit wagon = s.outside("wagonTrain", null);
        final Unit gun = s.outside("artillery", null);
        final Unit smith = s.outside("masterBlacksmith", null);
        final Unit free = s.outside("freeColonist", null);
        final Unit fisher = s.outside("expertFisherman", null);
        final Unit soldier = s.outside("freeColonist", "soldier");
        final ClassicColonyBuildings.Assets a = ClassicColonyBuildings.Assets.load(
            ClassicPackFiles.runtime());
        final List<Unit> out = ClassicColonyBuildings.outside(a, s.colony, null);
        assertFalse(out.contains(wagon));
        assertFalse(out.contains(gun));
        assertEquals(4, out.size());
        if (ClassicPackFiles.runtime() == null) return;
        // By job: the fisherman 8, the blacksmith 14, the free colonist
        // Siedler 19, the soldier Soldat 21.
        assertEquals(List.of(fisher, smith, free, soldier), out);
        // Ties keep the given order (the units moved to the front first).
        final Unit free2 = s.outside("freeColonist", null);
        assertEquals(List.of(fisher, smith, free2, free, soldier),
            ClassicColonyBuildings.outside(a, s.colony, l -> {
                    final List<Unit> r = new ArrayList<>(l);
                    r.remove(free2);
                    r.add(0, free2);
                    return r;
                }));
        assertEquals("the soldier's role sprite", 74,
                     ClassicColonyBuildings.iconFrame(a, ClassicColonyBuildings.figureKey(a,
                         soldier, true)));
        assertEquals("inside, the type's", 100,
                     ClassicColonyBuildings.iconFrame(a, ClassicColonyBuildings.figureKey(a,
                         soldier, false)));
    }

    /**
     * The presses on the building area (the first slice's select-then-
     * target, unchanged): a worker's figure selects him, a figure on the
     * fence does nothing yet (M4.8), the build list opens from ÄNDERN in the
     * hammer panel (M4.7: the construction plate is gone, the first slice's
     * press anywhere on the right panel too).
     */
    public void testThePresses() throws Exception {
        final State s = base1539();
        final Unit vet = s.in("veteranSoldier", "townHall");
        s.in("masterCarpenter", "lumberMill");
        s.finish();
        s.outside("veteranSoldier", "soldier");
        final int[] lists = { 0 };
        final FreeColClient fcc = client(s.game, s.owner);
        final ClassicGUI gui = new ClassicGUI(null) {
                @Override
                public net.sf.freecol.client.gui.panel.FreeColPanel showBuildQueuePanel(
                    Colony colony) {
                    lists[0]++;
                    return null;
                }
            };
        final Field f = FreeColClient.class.getDeclaredField("gui");
        f.setAccessible(true);
        f.set(fcc, gui);
        final ClassicColonyPanel p = new ClassicColonyPanel(fcc, library(), s.colony, null,
            null, ClassicSlide.SYSTEM, false);
        p.setFounding(BASE);
        p.canvas();
        assertNull(p.selected());
        p.clickAt(125, 40);                    // the Staatsmann in the Rathaus
        assertSame(vet, p.selected());
        p.clickAt(160, 118);                   // the soldier on the fence
        assertSame(vet, p.selected());
        p.clickAt(20, 20);                     // trees (M1): no target, nothing
        assertSame(vet, p.selected());
        p.select(null);
        p.setMode(ClassicColonyRight.Mode.HOUSE);
        p.canvas();
        p.clickAt(250, 150);                   // the right panel's house rows: nothing
        assertEquals(0, lists[0]);
        p.setMode(ClassicColonyRight.Mode.HAMMER);
        p.canvas();
        p.clickAt(280, 142);                   // ÄNDERN (M4.7; the list's look, M4.10)
        assertEquals(1, lists[0]);
        p.clickAt(100, 12);                    // where the plate was: the Rathaus now
        assertEquals(1, lists[0]);
        ClassicColonyPanel.setLastMode(ClassicColonyRight.Mode.HOUSE);
    }


    // Golden against clip 020

    /** What a reference frame's rebuilt screen is compared with. */
    private static final class Shot {
        final int frame;
        final BufferedImage canvas;
        /** The figure row's figure of the unit whose cursor the original shows instead of our frame, or null. */
        final Rectangle cursor;
        /** The left panel's overlay model, or null. */
        final String left;
        /** The building area as drawn (for the report). */
        String model = "";

        Shot(int frame, BufferedImage canvas, Rectangle cursor, String left) {
            this.frame = frame;
            this.canvas = canvas;
            this.cursor = cursor;
            this.left = left;
        }

        Shot with(ClassicColonyPanel p) {
            this.model = p.area().toString() + " row " + p.rowFigures();
            return this;
        }
    }

    static ClassicColonyPanel panel(State s, Founding f) throws Exception {
        ClassicColonyPanel.forgetFigureOrders();
        final ClassicColonyPanel p = new ClassicColonyPanel(client(s.game, s.owner),
            library(), s.colony, null, null, ClassicSlide.SYSTEM, false);
        p.setFounding(f);
        return p;
    }

    /** Clip 020's frames as rebuilt states (L's reference frames and more, see the class javadoc). */
    private List<Shot> shots() throws Exception {
        final List<Shot> out = new ArrayList<>();
        // #2260 Base 1539: Staatsmann (veteran soldier), Schnapsbrenner
        // (sugar planter), Schreiner; the farmer selected.
        {
            final State s = base1539();
            final Unit vet = s.in("veteranSoldier", "townHall");
            s.in("masterSugarPlanter", "distillerHouse");
            s.in("masterCarpenter", "lumberMill");
            final Unit farmer = s.on("expertFarmer", Direction.NW, "grain");
            s.on("expertLumberJack", Direction.N, "lumber");
            s.finish();
            final ClassicColonyPanel p = panel(s, BASE);
            p.select(farmer);
            out.add(new Shot(2260, p.canvas(), null, "ov_base_2260_leftpanel.txt").with(p));
            // #2398: the same, the pointer over the Rathaus (its label, V
            // x 99-126, y 26).
            p.hoverAt(119, 42);
            out.add(new Shot(2398, p.canvas(), null, null).with(p));
            // #2620: the veteran dragged into the Haus des Gerbers (C1),
            // selected; the Hafenanlagen' label up; his figure keeps its
            // place, the cursor is on it (white in the frame).
            s.move(vet, "furTraderHouse");
            p.select(vet);
            p.hoverAt(160, 84);
            out.add(new Shot(2620, p.canvas(), new Rectangle(39, 143, 8, 16), null).with(p));
            // #2747: then into the Haus des Tabakhändlers (C2), no label.
            s.move(vet, "tobacconistHouse");
            p.hoverAt(88, 84);
            out.add(new Shot(2747, p.canvas(), new Rectangle(39, 143, 8, 16), null).with(p));
        }
        // #3500 Base 1539: the Holzfäller from tile N into the Sägewerk
        // (C4), selected; «Bäume» of L2 up.
        {
            final State s = base1539();
            s.in("veteranSoldier", "townHall");
            s.in("masterSugarPlanter", "distillerHouse");
            s.in("masterCarpenter", "lumberMill");
            s.on("expertFarmer", Direction.NW, "grain");
            final Unit lj = s.on("expertLumberJack", Direction.N, "lumber");
            s.finish();
            final ClassicColonyPanel p = panel(s, BASE);
            p.canvas();
            s.move(lj, "lumberMill");
            p.select(lj);
            p.hoverAt(111, 113);
            out.add(new Shot(3500, p.canvas(), null, null).with(p));
        }
        // #6560 Base 1539: the veteran outside as Soldat (C7, J1), selected.
        {
            final State s = base1539();
            s.in("masterSugarPlanter", "distillerHouse");
            s.in("masterCarpenter", "lumberMill");
            s.on("expertFarmer", Direction.NW, "grain");
            s.on("expertLumberJack", Direction.N, "lumber");
            s.finish();
            final Unit soldier = s.outside("veteranSoldier", "soldier");
            final ClassicColonyPanel p = panel(s, BASE);
            p.select(soldier);
            out.add(new Shot(6560, p.canvas(), new Rectangle(43, 143, 10, 16), null).with(p));
        }
        // #46039 / #46350 / #47291 Base 1546 (visit 9): three arrivals on
        // the fence, the Meister-Schreiner in the Haus des Schmieds, the
        // sugar planter on ore; then the fisherman joins (#96: on tile E),
        // the distiller (#97: Schnapsbrenner), the Schreiner back to the
        // Sägewerk (C12) and the blacksmith into the Haus des Schmieds (C13).
        {
            final State s = base(1546);
            s.in("veteranSoldier", "townHall");
            final Unit carpenter = s.in("masterCarpenter", "blacksmithHouse");
            final Unit farmer = s.on("expertFarmer", Direction.NW, "grain");
            s.on("expertLumberJack", Direction.N, "lumber");
            s.on("masterSugarPlanter", Direction.NE, "ore");
            s.finish();
            final Unit fisher = s.outside("expertFisherman", null);
            final Unit distiller = s.outside("masterDistiller", null);
            final Unit smith = s.outside("masterBlacksmith", null);
            final ClassicColonyPanel p = panel(s, BASE);
            p.select(farmer);
            p.hoverAt(149, 120);
            out.add(new Shot(46039, p.canvas(), null, null).with(p));
            s.work(fisher, Direction.E, "fish");
            p.select(fisher);
            p.hoverAt(145, 121);
            out.add(new Shot(46350, p.canvas(), new Rectangle(47, 143, 10, 16), null).with(p));
            s.move(distiller, "distillerHouse");
            p.canvas();
            s.move(carpenter, "lumberMill");
            s.move(smith, "blacksmithHouse");
            p.select(smith);
            p.hoverAt(177, 70);
            out.add(new Shot(47291, p.canvas(), new Rectangle(65, 143, 10, 16), null).with(p));
        }
        // #134173 Base 1571: the Lagerhaus, the Verlag, the Schmiede; two in
        // the Haus des Rumbrenners; the farmer selected.
        {
            final State s = new State("Base", 1571).build("lumberMill").build("docks")
                .build("printingPress").build("blacksmithShop").build("warehouse");
            s.in("veteranSoldier", "townHall");
            s.in("masterSugarPlanter", "distillerHouse");
            s.in("masterDistiller", "distillerHouse");
            s.in("masterCarpenter", "lumberMill");
            final Unit farmer = s.on("expertFarmer", Direction.NW, "grain");
            s.on("expertLumberJack", Direction.N, "lumber");
            s.on("masterBlacksmith", Direction.NE, "ore");
            s.on("expertFisherman", Direction.E, "fish");
            s.finish();
            final ClassicColonyPanel p = panel(s, BASE);
            p.select(farmer);
            out.add(new Shot(134173, p.canvas(), null, "ov_base_134173_leftpanel.txt").with(p));
        }
        // #96680 Base 1561: the Verlag, the Schmiede with the Meister-Schmied,
        // the pointer over it: «Schmiede» clamped to x 165-198, y 18.
        {
            final State s = new State("Base", 1561).build("lumberMill").build("docks")
                .build("printingPress").build("blacksmithShop");
            s.in("veteranSoldier", "townHall");
            s.in("masterDistiller", "distillerHouse");
            s.in("masterCarpenter", "lumberMill");
            s.in("masterBlacksmith", "blacksmithHouse");
            final Unit farmer = s.on("expertFarmer", Direction.NW, "grain");
            s.on("masterSugarPlanter", Direction.SW, "sugar");
            s.on("expertLumberJack", Direction.N, "lumber");
            s.on("expertFisherman", Direction.E, "fish");
            s.finish();
            final ClassicColonyPanel p = panel(s, BASE);
            p.select(farmer);
            p.hoverAt(184, 43);
            out.add(new Shot(96680, p.canvas(), null, null).with(p));
        }
        // #14817 / #35057 Provisorische Kolonie 1541 / 1544: the scout on a tile, selected.
        for (int[] k : new int[][] { { 14817, 1541 }, { 35057, 1544 } }) {
            final State s = new State("Provisorische Kolonie", k[1]);
            final Unit scout = s.on("seasonedScout", Direction.N, "sugar");
            s.finish();
            final ClassicColonyPanel p = panel(s, PROV);
            p.select(scout);
            out.add(new Shot(k[0], p.canvas(),
                             (k[0] == 35057) ? new Rectangle(1, 143, 8, 16) : null,
                             (k[0] == 14817) ? "ov_prov_14817_leftpanel.txt" : null).with(p));
        }
        // #123003 Silver City 1566: the scout on silver (W), the cursor on his figure.
        {
            final State s = new State("Silver City", 1566);
            final Unit scout = s.on("seasonedScout", Direction.W, "silver");
            s.finish();
            final ClassicColonyPanel p = panel(s, SILVER);
            p.select(scout);
            out.add(new Shot(123003, p.canvas(), new Rectangle(1, 143, 8, 16), null).with(p));
        }
        return out;
    }

    /** The building area of a shot against its frame. */
    private static ClassicColonyGolden.Diff area(ClassicColonyGolden gold, Shot s)
        throws Exception {
        final BufferedImage frame = gold.frame(s.frame);
        return ClassicColonyGolden.compare("building area #" + s.frame,
            ClassicColonyGolden.pixels(s.canvas), ClassicColonyGolden.pixels(frame),
            ClassicColonyLayout.BUILDING_AREA, gold.pointer(frame), null);
    }

    /**
     * Golden (M4.2, M4.3 acceptance): the whole building area of clip
     * 020's frames rebuilt in a test game, 0 differing pixels (the arrow
     * left out): the 15 slots with their sprites and placeholders (Base
     * 1539 and 1571 with the Lagerhaus, the Verlag and the Schmiede,
     * Provisorische Kolonie, Silver City), the rows (bells 1, 4, 6; rum 3,
     * 6, 9; hammers 12, 18 stacked with their labels; coats, cigars,
     * tools), the workers in the H, M and L slots with their shadows, the
     * selected one's green frame, the units outside on the fence (1, 2,
     * 3), the hover labels («Hafenanlagen», «Bäume», «(Außerhalb der
     * Kolonie)»).
     */
    public void testTheBuildingAreaAgainstClip020() throws Exception {
        final ClassicColonyGolden gold = ClassicColonyGolden.open(getName());
        if (gold == null) return;
        final StringBuilder report = new StringBuilder();
        final StringBuilder fails = new StringBuilder();
        for (Shot s : shots()) {
            dump(s.canvas, gold.frame(s.frame), s.frame);
            final ClassicColonyGolden.Diff d = area(gold, s);
            System.err.println("  model #" + s.frame + ": " + s.model);
            report.append("  ").append(d).append('\n');
            if (d.differing != 0) fails.append(' ').append(d.name).append(": ").append(d.differing);
            assertTrue(d.name, d.compared > 20000);
        }
        System.err.println(getName() + ":\n" + report);
        assertEquals("differing pixels:" + fails + "\n" + report, 0, fails.length());
    }

    /**
     * Golden (the figure row, L §5.2): x 0-118, y 142-158 of the same
     * frames: the colonists by job (kept during a visit), the units
     * outside after the gap, the selected one's green frame; left out:
     * the arrow, the SoL flag and the Tory crown (M4.5: the left panel's
     * model, or their sprites where no model exists) and the original's
     * blinking cursor where it stands on the selected figure instead of
     * the green frame (M4.4, M4.8: white on, nothing off).
     */
    public void testTheFigureRowAgainstClip020() throws Exception {
        final ClassicColonyGolden gold = ClassicColonyGolden.open(getName());
        if (gold == null) return;
        final Rectangle region = new Rectangle(0, 142, 119, 17);
        final StringBuilder report = new StringBuilder();
        final StringBuilder fails = new StringBuilder();
        for (Shot s : shots()) {
            final BufferedImage frame = gold.frame(s.frame);
            ClassicColonyGolden.Mask later = gold.content(s.left, ClassicColonyLayout.LEFT_PANEL,
                l -> !(l.startsWith("icons ") && l.split("\\s+")[2].equals("142"))
                && !l.startsWith("frame "));
            if (later == null) {
                later = new ClassicColonyGolden.Mask();
                later.sprite(gold.pack.image(ClassicPackFiles.ssKey("ICONS.SS.123")), 2, 132);
                later.sprite(gold.pack.image(ClassicPackFiles.ssKey("ICONS.SS.124")), 104, 132);
            }
            if (s.cursor != null) {
                final Rectangle c = s.cursor;
                later.rect(c.x, c.y, c.x + c.width - 1, c.y);
                later.rect(c.x, c.y + c.height - 1, c.x + c.width - 1, c.y + c.height - 1);
                later.rect(c.x, c.y, c.x, c.y + c.height - 1);
                later.rect(c.x + c.width - 1, c.y, c.x + c.width - 1, c.y + c.height - 1);
            }
            final ClassicColonyGolden.Diff d = ClassicColonyGolden.compare(
                "figure row #" + s.frame, ClassicColonyGolden.pixels(s.canvas),
                ClassicColonyGolden.pixels(frame), region, gold.pointer(frame), later);
            report.append("  ").append(d).append('\n');
            if (d.differing != 0) fails.append(' ').append(d.name).append(": ").append(d.differing);
            assertTrue(d.name, d.compared > 1500);
        }
        System.err.println(getName() + ":\n" + report);
        assertEquals("differing pixels:" + fails + "\n" + report, 0, fails.length());
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
        final java.awt.Graphics2D g = out.createGraphics();
        g.drawImage(ours, 0, 0, 960, 600, null);
        g.drawImage(frame, 972, 0, 960, 600, null);
        g.dispose();
        javax.imageio.ImageIO.write(out, "png", new java.io.File(d, "s2-ours-vs-" + n + ".png"));
    }
}
