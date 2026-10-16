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

import javax.imageio.ImageIO;

import net.sf.freecol.common.model.BuildingType;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * The Colonopedia's skill page «Fähigkeiten der Kolonisten» and building
 * page «Koloniegebäude» (fixer A after part X: F1 in the job menu, F1 and
 * the right-click on the colony screen, the right-click in the build list;
 * {@link ClassicPedia#skillPage}, {@link ClassicPedia#buildingPage}) built
 * from the pack against Roger's clips: 049 #3111 «Pelzjäger» (F1 in the
 * job menu), #11775 «Siedler» (a right-click on a free colonist at the
 * fence), 052 #15108 «Tabakpflanzer» (F1 with the bar moved), #16124
 * «Soldat» (F1 without a menu), 046 #71579 «Sägewerk» (a right-click in
 * the build list); each whole frame 0 px off, the original arrow excused.
 */
public class ClassicPediaPagesTest extends FreeColTestCase {

    /** The skill pages: folder under the recordings, frame, NAMES {@code @JOB} row. */
    private static final Object[][] SKILLS = {
        { "fixer-a-analysis/c049", 3111, 4 },
        { "fixer-a-analysis/c049", 11775, ClassicColonyLayout.JOB_COLONIST },
        { "fixer-a-analysis/c052", 15108, 2 },
        { "fixer-a-analysis/c052", 16124, ClassicColonyLayout.JOB_SOLDIER },
    };

    /**
     * The pages' parts from the rules and the texts: every NAMES
     * {@code @JOB} row 0-27 has a skill page, every building type of the
     * original a building page; a type the original lacks none; the
     * building's work and its row.
     */
    public void testEveryPageIsThere() {
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final ClassicText t = ClassicText.load(pack);
        final ClassicPedia pedia = ClassicPedia.load(pack);
        if (t == null || pedia == null) {
            System.err.println(getName() + ": skipped, no pack texts (ant classic-assets)");
            return;
        }
        final ClassicFont tiny = pack.font(ClassicFont.TINY);
        for (int job = 0; job <= 27; job++) {
            assertNotNull("job " + job, ClassicPedia.skillPage(pedia, t, tiny, null, job,
                                                                spec(), null));
        }
        assertNull(ClassicPedia.skillPage(pedia, t, tiny, null, 28, spec(), null));
        assertNull(ClassicPedia.skillPage(pedia, t, tiny, null, -1, spec(), null));
        int pages = 0;
        for (BuildingType bt : spec().getBuildingTypeList()) {
            final int row = ClassicPedia.buildingRow(bt);
            final BufferedImage p = ClassicPedia.buildingPage(pedia, t, tiny, null, bt, null,
                                                              null);
            assertEquals(bt.getId(), row >= 0, p != null);
            if (p != null) pages++;
        }
        assertTrue("pages " + pages, pages >= 35);
        assertEquals(36, ClassicPedia.buildingRow(spec().getBuildingType("model.building.lumberMill")));
        assertEquals(ClassicColonyBuildings.NAMES_STABLES,
                     ClassicPedia.buildingRow(spec().getBuildingType("model.building.stables")));
        assertEquals(13, ClassicPedia.buildingJob(spec().getBuildingType("model.building.lumberMill")));
        assertEquals(17, ClassicPedia.buildingJob(spec().getBuildingType("model.building.townHall")));
        assertEquals(ClassicColonyLayout.JOB_TEACHER,
            ClassicPedia.buildingJob(spec().getBuildingType("model.building.schoolhouse")));
        assertEquals(-1, ClassicPedia.buildingJob(spec().getBuildingType("model.building.stockade")));
        assertEquals(-1, ClassicPedia.buildingRow(null));
    }

    /**
     * The golden check: the four skill pages and the Sägewerk's page built
     * from the pack (PEDIA.TXT, NAMES, LABELS, FONTTINY, WOODPANL.PIK, the
     * figures, the goods' icons, BUILDING.SS) against the whole frames:
     * 0 px off, the original arrow excused (indices 0, 7, 15).
     */
    public void testTheGoldenPages() throws Exception {
        final String clips = System.getProperty(ClassicTerrainGoldenTest.CLIPS_PROPERTY);
        final File dir = (clips == null) ? null : new File(clips, "fixer-a-analysis");
        if (dir == null || !dir.isDirectory()) {
            System.err.println(getName() + ": golden check skipped, no recordings (-D"
                + ClassicTerrainGoldenTest.CLIPS_PROPERTY + ")");
            return;
        }
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final ClassicText t = ClassicText.load(pack);
        final ClassicPedia pedia = ClassicPedia.load(pack);
        if (t == null || pedia == null) {
            System.err.println(getName() + ": skipped, no pack texts (ant classic-assets)");
            return;
        }
        final ClassicFont tiny = pack.font(ClassicFont.TINY);
        final BufferedImage wood = pack.image(ClassicPedia.WOODPANL_KEY);
        final ClassicColonyBuildings.Assets art = ClassicColonyBuildings.Assets.load(pack);
        final StringBuilder fails = new StringBuilder();
        int arrow = 0, pages = 0;
        for (Object[] p : SKILLS) {
            final BufferedImage page = ClassicPedia.skillPage(pedia, t, tiny, wood,
                (Integer) p[2], spec(), name -> pack.image(ClassicPackFiles.ssKey(name)));
            assertNotNull(page);
            final int[] got = compare(new File(dir.getParentFile(), (String) p[0]),
                                      (Integer) p[1], page);
            arrow += got[1];
            pages++;
            if (got[0] > 0) fails.append(" #").append(p[1]).append('=').append(got[0]);
            System.out.println(getName() + ": skill page #" + p[1] + " " + got[0] + " px off, "
                + got[1] + " arrow");
        }
        final BufferedImage mill = ClassicPedia.buildingPage(pedia, t, tiny, wood,
            spec().getBuildingType("model.building.lumberMill"), art::building,
            name -> pack.image(ClassicPackFiles.ssKey(name)));
        assertNotNull(mill);
        final int[] got = compare(new File(dir, "c046"), 71579, mill);
        arrow += got[1];
        pages++;
        if (got[0] > 0) fails.append(" #71579=").append(got[0]);
        System.out.println(getName() + ": building page #71579 " + got[0] + " px off, "
            + got[1] + " arrow");
        assertEquals("pixels off:" + fails, 0, fails.length());
        assertTrue("arrow pixels " + arrow, arrow <= 100 * pages);
    }

    /**
     * The pages over the colony screen as the game asks them
     * ({@link ClassicGUI#pageRequest}): a full-screen picture that comes
     * its delay after it is asked and goes 16 frames after its key (V clip
     * 052: 14-17); the GUI's pictures from the pack.
     */
    public void testThePagesRequest() {
        final BufferedImage pic = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
        final ClassicAdvisorBox.Request r = ClassicGUI.pageRequest("pedia skill 4", "x", pic,
            ClassicColonyMoves.ms(ClassicPedia.PAGE_FRAMES));
        assertSame(pic, r.picture);
        assertTrue(r.isNotice());
        assertEquals(ClassicColonyMoves.ms(22), r.openDelayMs, 1e-9);
        assertEquals(ClassicColonyMoves.ms(16), r.closeLagMs, 1e-9);
        assertEquals(0.0, r.closeAfterMs);
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final ClassicText t = ClassicText.load(pack);
        if (t == null || ClassicPedia.load(pack) == null) return;
        assertNotNull(ClassicGUI.skillPageImage(pack, t, 4, spec()));
        assertNotNull(ClassicGUI.buildingPageImage(pack, t,
            spec().getBuildingType("model.building.lumberMill")));
        assertNull(ClassicGUI.skillPageImage(null, t, 4, spec()));
        assertNull(ClassicGUI.buildingPageImage(pack, t, null));
    }

    /** @return {off, arrow} of a page against a frame of a folder. */
    private static int[] compare(File folder, int frame, BufferedImage page) throws Exception {
        final BufferedImage f = ImageIO.read(ClassicTerrainGoldenTest.frameFile(folder, frame));
        final Raster idx = (f.getColorModel() instanceof IndexColorModel) ? f.getRaster() : null;
        int off = 0, arr = 0;
        for (int y = 0; y < 200; y++) {
            for (int x = 0; x < 320; x++) {
                if ((f.getRGB(x, y) & 0xFFFFFF) == (page.getRGB(x, y) & 0xFFFFFF)) continue;
                final int i = (idx == null) ? -1 : idx.getSample(x, y, 0);
                if (i == 0 || i == 7 || i == 15) arr++; else off++;
            }
        }
        return new int[] { off, arr };
    }
}
