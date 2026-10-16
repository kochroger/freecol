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

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import net.sf.freecol.common.model.ResourceType;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * The Colonopedia's terrain page (part W, build spec M4.11 target 4: F1 on
 * the colony screen; {@link ClassicPedia#terrainPage}): its rows from the
 * rules and the pack's texts, and the whole page against the three pages
 * of clip 020 (#78325 TROPENWALD and #76922 REGENWALD from the colony,
 * #121397 LAUBWALD from the map) and the NADELWALD of Roger's playthrough
 * 4 (clip 048 #15234 from the colony, clip 046 #11119 from the map).
 */
public class ClassicPediaTerrainTest extends FreeColTestCase {

    /** The pages: folder under the clips, frame, tile type, PEDIA {@code @TERRAINk}. */
    private static final Object[][] PAGES = {
        { "playthrough-3", "frame_078325.png", "model.tile.tropicalForest", 13 },
        { "playthrough-3", "frame_076922.png", "model.tile.rainForest", 15 },
        { "playthrough-3", "frame_121397.png", "model.tile.broadleafForest", 11 },
        // Roger's playthrough 4: the conifer forest from the colony (048
        // #15234) and from the map (046 #11119).
        { "colony-flow-analysis/key", "c048_015234_pedia-nadelwald.png",
          "model.tile.coniferForest", 12 },
        { "colony-flow-analysis/key", "c046_011119_pedia-nadelwald-map.png",
          "model.tile.coniferForest", 12 },
    };

    private static ClassicText texts(String test) {
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        if (t == null) {
            System.err.println("ClassicPediaTerrainTest: " + test
                + " skipped, no pack texts (ant classic-assets)");
        }
        return t;
    }

    private static List<String> rows(ClassicText t, TileType type) {
        final List<String> out = new ArrayList<>();
        for (ClassicPedia.TerrainRow r
                 : ClassicPedia.terrainRows(t, type, ClassicPedia.pageResource(type))) {
            out.add(r.toString());
        }
        return out;
    }

    /**
     * The rows of the three pages as the frames write them: the works the
     * type yields a free colonist (the rules' production: the tropical
     * forest's Farmer 3 and Holzfäller 4, not NAMES.TXT's 2 and 2), the
     * bonus by the goods, the expert ("+3" for the farmer, the original's
     * page), the resource on the rows of its goods (Wild on the farmer's
     * and the trapper's, "+2/4" with the expert's), no row for the rain
     * forest's silver (Mineralien's undocumented +1); the movement and
     * defence line; the entries by the type.
     */
    public void testTheRows() {
        final ClassicText t = texts("testTheRows");
        if (t == null) return;
        final TileType tropical = spec().getTileType("model.tile.tropicalForest");
        final TileType rain = spec().getTileType("model.tile.rainForest");
        final TileType broadleaf = spec().getTileType("model.tile.broadleafForest");
        assertEquals(List.of(
                "Farmer: 3 | Pflügen/Fluß: +1 | Experte: +3",
                "Zuckerpflanzer: 1 | Pflügen/Fluß: +1 | Experte: x2",
                "Pelzjäger: 2 | Straße/Fluß: +2 | Experte: x2",
                "Holzfäller: 4 | Straße/Fluß: +2 | Experte: x2 | Bestes Holz: +4 @99"),
            rows(t, tropical));
        assertEquals(List.of(
                "Farmer: 2 | Pflügen/Fluß: +1 | Experte: +3",
                "Zuckerpflanzer: 1 | Pflügen/Fluß: +1 | Experte: x2",
                "Pelzjäger: 1 | Straße/Fluß: +2 | Experte: x2",
                "Holzfäller: 4 | Straße/Fluß: +2 | Experte: x2",
                "Erz-Bergarbeiter: 1 | Straße/Fluß: +1 | Experte: x2 | Mineralien: +3 @95"),
            rows(t, rain));
        assertEquals(List.of(
                "Farmer: 2 | Pflügen/Fluß: +1 | Experte: +3 | Wild: +2/4 @98",
                "Baumwollpflanzer: 1 | Pflügen/Fluß: +1 | Experte: x2",
                "Pelzjäger: 2 | Straße/Fluß: +2 | Experte: x2 | Wild: +2 @98",
                "Holzfäller: 4 | Straße/Fluß: +2 | Experte: x2"),
            rows(t, broadleaf));
        assertEquals(List.of(
                "Farmer: 2 | Pflügen/Fluß: +1 | Experte: +3",
                "Tabakpflanzer: 1 | Pflügen/Fluß: +1 | Experte: x2",
                "Pelzjäger: 2 | Straße/Fluß: +2 | Experte: x2",
                "Holzfäller: 6 | Straße/Fluß: +2 | Experte: x2 | Bestes Holz: +4 @99"),
            rows(t, spec().getTileType("model.tile.coniferForest")));
        assertEquals("Bewegungskosten: 2", ClassicPedia.movesText(t, tropical));
        assertEquals("Bewegungskosten: 3", ClassicPedia.movesText(t, rain));
        assertEquals("Verteidigungs- oder Geländebonus: +50%",
                     ClassicPedia.defenceText(t, tropical));
        assertEquals("Verteidigungs- oder Geländebonus: +75%",
                     ClassicPedia.defenceText(t, rain));
        assertEquals(13, ClassicPedia.terrainEntry(tropical));
        assertEquals(15, ClassicPedia.terrainEntry(rain));
        assertEquals(11, ClassicPedia.terrainEntry(broadleaf));
        assertEquals(-1, ClassicPedia.terrainEntry(null));
        assertNull(ClassicPedia.pageResource(null));
        // A type of every kind has a page (the rows' rules, I).
        for (TileType type : spec().getTileTypeList()) {
            if (ClassicPedia.terrainEntry(type) < 0) continue;
            final List<String> rs = rows(t, type);
            for (String r : rs) assertFalse(type.getId() + " " + r, r.contains("null"));
        }
        // The fisherman's coast (I) and the plains' grain (+2/4, I).
        assertEquals(List.of("Fischer: 2 | Küste: +2 | Experte: +3 | Fischerei: +3/6 @96"),
                     rows(t, spec().getTileType("model.tile.ocean")));
        final List<String> plains = rows(t, spec().getTileType("model.tile.plains"));
        assertEquals("Farmer: 5 | Pflügen/Fluß: +1 | Experte: +3 | Weizen: +2/4 @91",
                     plains.get(0));
        // Every tile type has a page: a water type the original lacks
        // (FreeCol's great river) the ocean's, the others their own (the
        // review of part W, regression L9: the great river had none).
        final TileType ocean = spec().getTileType("model.tile.ocean");
        final TileType great = spec().getTileType("model.tile.greatRiver");
        assertNotNull(great);
        assertEquals(-1, ClassicPedia.terrainEntry(great));
        assertSame(ocean, ClassicGUI.pediaType(great));
        assertNull(ClassicGUI.pediaType(null));
        for (TileType type : spec().getTileTypeList()) {
            final TileType page = ClassicGUI.pediaType(type);
            assertTrue(type.getId(), ClassicPedia.terrainEntry(page) >= 0);
            if (type != great) assertSame(type, page);
        }
    }

    /**
     * The picture's overlays: the forest connected inside the block only,
     * the river and the road's spokes on land, the resource on the centre;
     * water without river and road.
     */
    public void testThePicturesFrames() {
        final TileType tropical = spec().getTileType("model.tile.tropicalForest");
        final ResourceType lumber = spec().getResourceType("model.resource.lumber");
        final int[][] expect = {
            { 69 }, { 71 }, { 70, 82, 85 },
            { 77, 22 }, { 79, 99 }, { 78, 81, 84 },
            { 73, 26 }, { 75 }, { 74 },
        };
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                final List<Integer> got = new ArrayList<>();
                ClassicTileArt.pediaFrames(tropical, lumber, x, y, got::add);
                final List<Integer> want = new ArrayList<>();
                for (int f : expect[y * 3 + x]) want.add(f);
                assertEquals("cell " + x + "," + y, want, got);
            }
        }
        final List<Integer> water = new ArrayList<>();
        ClassicTileArt.pediaFrames(spec().getTileType("model.tile.ocean"), null, 0, 1,
                                   water::add);
        assertTrue(water.isEmpty());
    }

    /**
     * The golden check: each page built from the pack (PEDIA.TXT, the
     * texts, FONTTINY, WOODPANL.PIK, the figures and the markers, the
     * picture composed by the map's composer) against the whole frame:
     * 0 px off, the original's arrow excused (indices 0, 7, 15), the
     * picture's water at the frame's own cycling phase (the original's
     * river cycles on the page; ours stands at phase 0).
     */
    public void testTheGoldenPages() throws Exception {
        final String clips = System.getProperty(ClassicTerrainGoldenTest.CLIPS_PROPERTY);
        final File dir = (clips == null) ? null : new File(clips, "playthrough-3");
        if (dir == null || !dir.isDirectory()) {
            System.err.println("ClassicPediaTerrainTest: golden check skipped, no recordings (-D"
                + ClassicTerrainGoldenTest.CLIPS_PROPERTY + ")");
            return;
        }
        final ClassicText t = texts("testTheGoldenPages");
        if (t == null) return;
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final ClassicPedia pedia = ClassicPedia.load(pack);
        assertNotNull(pedia);
        final ClassicFont tiny = pack.font(ClassicFont.TINY);
        final BufferedImage wood = pack.image(ClassicPedia.WOODPANL_KEY);
        final ClassicColonyTiles.Art art = ClassicColonyTiles.Art.load(pack, null);
        assertTrue("the pack's index sheets", art.indexed());
        final StringBuilder fails = new StringBuilder();
        int arrow = 0, compared = 0;
        for (Object[] p : PAGES) {
            final TileType type = spec().getTileType((String) p[2]);
            assertEquals((int) (Integer) p[3], ClassicPedia.terrainEntry(type));
            final File folder = new File(dir.getParentFile(), (String) p[0]);
            final BufferedImage frame = ImageIO.read(new File(folder, (String) p[1]));
            final Raster idx = (frame.getColorModel() instanceof IndexColorModel)
                ? frame.getRaster() : null;
            int best = Integer.MAX_VALUE, bestArrow = 0, bestPhase = -1;
            for (int phase = 0; phase < ClassicGamePalette.CYCLE_COUNT; phase++) {
                final int ph = phase;
                final BufferedImage page = ClassicPedia.terrainPage(pedia, t, tiny, wood, type,
                    name -> pack.image(ClassicPackFiles.ssKey(name)),
                    (tt, r) -> art.pediaPicture(tt, r, ph));
                assertNotNull(page);
                int off = 0, arr = 0;
                for (int y = 0; y < 200; y++) {
                    for (int x = 0; x < 320; x++) {
                        if ((frame.getRGB(x, y) & 0xFFFFFF) == (page.getRGB(x, y) & 0xFFFFFF)) {
                            continue;
                        }
                        final int i = (idx == null) ? -1 : idx.getSample(x, y, 0);
                        if (i == 0 || i == 7 || i == 15) arr++; else off++;
                    }
                }
                if (off < best) {
                    best = off;
                    bestArrow = arr;
                    bestPhase = phase;
                }
            }
            compared += 64000;
            arrow += bestArrow;
            if (best > 0) fails.append(' ').append(p[1]).append('=').append(best);
            System.out.println("ClassicPediaTerrainTest: " + p[1] + " best at phase "
                + bestPhase + ": " + best + " px off");
        }
        System.out.println("ClassicPediaTerrainTest: golden check, " + PAGES.length
            + " pages, " + compared + " px compared, off:"
            + ((fails.length() == 0) ? " none" : fails.toString()) + ", "
            + arrow + " arrow px excused");
        assertEquals("pixels off:" + fails, 0, fails.length());
        assertTrue("arrow pixels " + arrow, arrow <= 100 * PAGES.length);
    }
}
