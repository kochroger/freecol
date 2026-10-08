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
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

import net.sf.freecol.common.model.FoundingFather;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * Tests of the original's father join (build spec D8c,
 * {@link ClassicCongress}) and of the Colonopedia's fathers' lists (D8b's
 * menu part, {@link ClassicPedia#typeList}): the trigger (the father set
 * grew), the @FREEDOM box, the hall back to front, its screen for the
 * layer, and the golden checks against clip008: @FREEDOM at #39296, the
 * empty hall at #39719 and the hall with Minuit at #39775, 0 px off (apart
 * from the original's mouse arrow).  The pack's parts are skipped with a
 * note without the pack, the golden checks without {@code -Dclassic.clips}.
 */
public class ClassicCongressTest extends FreeColTestCase {

    private static FoundingFather father(String id) {
        return spec().getFoundingFather("model.foundingFather." + id);
    }

    /** The pack with its hall, or null (then a test is skipped with a note). */
    private static ClassicPackFiles pack(String test) {
        final ClassicPackFiles p = ClassicPackFiles.runtime();
        if (p == null || p.image(ClassicCongress.HALL_KEY) == null
            || ClassicText.load(p) == null) {
            System.err.println("ClassicCongressTest: " + test
                + " skipped, no pack (ant classic-assets)");
            return null;
        }
        return p;
    }

    /** The clip008 frames, or null (then the check is skipped). */
    private File clipDir() {
        final String clips = System.getProperty(ClassicTerrainGoldenTest.CLIPS_PROPERTY);
        final File dir = (clips == null) ? null : new File(clips, "clip008");
        if (dir == null || !dir.isDirectory()) {
            System.err.println(getClass().getSimpleName()
                + ": golden check skipped, no recordings (-D"
                + ClassicTerrainGoldenTest.CLIPS_PROPERTY + ")");
            return null;
        }
        return dir;
    }

    /**
     * The trigger: the fathers not seen yet, in NAMES order, taken only
     * when asked; none without a known set or a player.
     */
    public void testJoined() {
        final Game game = getStandardGame();
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final Set<String> known = new HashSet<>();
        assertTrue(ClassicCongress.joined(known, dutch, true).isEmpty());
        assertTrue(ClassicCongress.joined(null, dutch, true).isEmpty());
        assertTrue(ClassicCongress.joined(known, null, true).isEmpty());
        dutch.addFather(father("williamPenn"));
        dutch.addFather(father("peterMinuit"));
        assertEquals(List.of(father("peterMinuit"), father("williamPenn")),
                     ClassicCongress.joined(known, dutch, false));
        assertTrue(known.isEmpty());                       // not taken
        assertEquals(List.of(father("peterMinuit"), father("williamPenn")),
                     ClassicCongress.joined(known, dutch, true));
        assertTrue(ClassicCongress.joined(known, dutch, true).isEmpty());
        dutch.addFather(father("adamSmith"));
        assertEquals(List.of(father("adamSmith")), ClassicCongress.joined(known, dutch, true));
        assertEquals("CC-02.SS.000", ClassicCongress.figure(2));
        assertEquals("CC-24.SS.000", ClassicCongress.figure(24));
    }

    /**
     * The fixer of part J (the review of part I, regress lens): the turn
     * start's poll asks while the connection thread clears and refills the
     * player's live set of fathers (Player.copyIn).  The question never
     * throws; a father it misses meanwhile comes at a later poll, and none
     * is reported twice.
     */
    public void testJoinedWhileTheSetIsRefilled() throws Exception {
        final Game game = getStandardGame();
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final List<FoundingFather> all = new ArrayList<>(spec().getFoundingFathers());
        final java.util.Set<FoundingFather> live = dutch.getFoundingFathers();
        final java.util.concurrent.atomic.AtomicBoolean stop
            = new java.util.concurrent.atomic.AtomicBoolean(false);
        final Thread copyIn = new Thread(() -> {
                while (!stop.get()) {
                    live.clear();
                    live.addAll(all);
                }
            }, "copyIn");
        final Set<String> known = new HashSet<>();
        final List<String> seen = new ArrayList<>();
        copyIn.start();
        try {
            final long end = System.nanoTime() + 300_000_000L;
            while (System.nanoTime() < end) {
                for (FoundingFather ff : ClassicCongress.joined(known, dutch, true)) {
                    seen.add(ff.getId());
                }
            }
        } finally {
            stop.set(true);
            copyIn.join(5_000L);
        }
        live.clear();
        live.addAll(all);
        for (FoundingFather ff : ClassicCongress.joined(known, dutch, true)) {
            seen.add(ff.getId());
        }
        assertEquals(all.size(), seen.size());
        assertEquals(all.size(), new HashSet<>(seen).size());
    }

    /**
     * @FREEDOM: GAME.TXT's words, "Holl." and the father's NAMES name in
     * gold, centred at (42,85) 236x30 without a portrait, a notice, 342 ms
     * after it is asked at the turn start; FreeCol's notice without the
     * pack's texts.
     */
    public void testTheFreedomBox() {
        final Game game = getStandardGame();
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final ClassicAdvisorBox.Request plain = ClassicCongress.freedom(null, dutch,
            father("peterMinuit"), 0.0);
        assertTrue(plain.isNotice());
        assertFalse(plain.plainText().isEmpty());
        assertEquals(0.0, plain.openDelayMs);
        final ClassicPackFiles pack = pack("testTheFreedomBox");
        if (pack == null) return;
        final ClassicText t = ClassicText.load(pack);
        final ClassicAdvisorBox.Request r = ClassicCongress.freedom(t, dutch,
            father("peterMinuit"), ClassicCongress.FREEDOM_AFTER_TURN_MS);
        assertEquals(ClassicCongress.SECTION, r.id);
        assertTrue(r.isNotice());
        assertEquals(342.0, r.openDelayMs);
        assertEquals("Holl. Gründerväter geben bekannt, daß Peter Minuit dem"
            + " Kontinentalkongreß beigetreten ist!", r.plainText().replace('\n', ' '));
        final ClassicAdvisorBox.Layout l = ClassicAdvisorBox.layout(r,
            pack.font(ClassicFont.TINY), null);
        assertEquals(new Rectangle(42, 85, 236, 30), l.box);
        final ClassicAdvisorBox.Request cortez = ClassicCongress.freedom(t, dutch,
            father("hernanCortes"), 0.0);
        assertTrue(cortez.plainText(), cortez.plainText().contains("Hernando Cortez"));
    }

    /**
     * The hall: CCBKGD alone without fathers; each figure at its anchor
     * (Minuit (268,61) 52x126, clip008 01-fathers 4.2); the order of the
     * list does not matter (back to front by the anchor's y); a figure the
     * pack lacks is left out.
     */
    public void testTheHall() {
        final ClassicPackFiles pack = pack("testTheHall");
        if (pack == null) return;
        assertEquals(new Rectangle(268, 61, 52, 126), ClassicCongress.figureBounds(pack, 2));
        assertNull(ClassicCongress.figureBounds(pack, 25));
        assertNull(ClassicCongress.figureBounds(pack, -1));
        final BufferedImage bg = pack.image(ClassicCongress.HALL_KEY);
        assertTrue(same(bg, ClassicCongress.hall(pack, List.of())));
        assertTrue(same(bg, ClassicCongress.hall(pack, List.of(25))));
        final List<Integer> all = new ArrayList<>();
        for (int n = 0; n < 25; n++) all.add(n);
        final List<Integer> back = new ArrayList<>(all);
        java.util.Collections.reverse(back);
        assertTrue(same(ClassicCongress.hall(pack, all), ClassicCongress.hall(pack, back)));
        assertFalse(same(bg, ClassicCongress.hall(pack, List.of(2))));
        // Two figures that overlap: the one standing lower is in front.
        int a = -1, b = -1;
        for (int i = 0; i < 25 && a < 0; i++) {
            for (int j = 0; j < 25; j++) {
                final Rectangle ri = ClassicCongress.figureBounds(pack, i);
                final Rectangle rj = ClassicCongress.figureBounds(pack, j);
                if (i != j && ri.intersects(rj)
                    && ri.y + ri.height > rj.y + rj.height) {
                    a = i;
                    b = j;
                    break;
                }
            }
        }
        assertTrue("two overlapping figures", a >= 0);
        final BufferedImage both = ClassicCongress.hall(pack, List.of(a, b));
        final BufferedImage front = pack.image(ClassicPackFiles.ssKey(ClassicCongress.figure(a)));
        final Rectangle ra = ClassicCongress.figureBounds(pack, a);
        for (int y = 0; y < ra.height; y++) {
            for (int x = 0; x < ra.width; x++) {
                if ((front.getRGB(x, y) >>> 24) == 0) continue;
                assertEquals((front.getRGB(x, y) & 0xFFFFFF),
                             both.getRGB(ra.x + x, ra.y + y) & 0xFFFFFF);
            }
        }
    }

    /**
     * The hall's screen: not a woodcut, its own times and name, the hall
     * with the fathers before him as the frame, only his figure's pixels
     * dissolving (repainted in his rectangle), his page after the black.
     */
    public void testTheScreen() {
        final ClassicPackFiles pack = pack("testTheScreen");
        if (pack == null) return;
        final BufferedImage page = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
        page.setRGB(5, 5, 0x123456);
        final ClassicWoodcut.Screen s = ClassicCongress.screen(pack, List.of(3, 2), 2, page);
        assertNotNull(s);
        assertEquals(ClassicCongress.HALL, s.k);
        assertFalse(s.woodcut);
        assertEquals("congress_2", s.name);
        assertEquals("congress_2", s.tag());
        assertEquals(ClassicWoodcut.FRAME_MS, s.frameAfterBlackMs, 1e-9);
        assertEquals(3 * ClassicWoodcut.FRAME_MS, s.dissolveAfterFrameMs, 1e-9);
        assertEquals(15 * ClassicWoodcut.FRAME_MS, s.closeMs, 1e-9);
        assertEquals(2 * ClassicWoodcut.FRAME_MS, s.chainMs, 1e-9);
        assertEquals(new Rectangle(268, 61, 52, 126), s.dirty);
        assertTrue(s.hasPage());
        assertTrue(s.changed() > 3000 && s.changed() <= 52 * 126);
        s.frame();
        assertTrue(same(s.image, ClassicCongress.hall(pack, List.of(3))));
        s.dissolveTo(s.changed());
        assertTrue(s.complete());
        assertTrue(same(s.image, ClassicCongress.hall(pack, List.of(2, 3))));
        s.page();
        assertEquals(0x123456, s.image.getRGB(5, 5) & 0xFFFFFF);
        assertNull(ClassicCongress.screen(pack, List.of(), 25, page));
        // A woodcut keeps its own.
        final ClassicWoodcut.Screen w = ClassicWoodcut.Screen.of(
            ClassicWoodcutTest.syntheticArt(), 3);
        assertTrue(w.woodcut);
        assertEquals("woodcut_3", w.name);
        assertEquals("3", w.tag());
        assertFalse(w.hasPage());
        assertEquals(ClassicWoodcut.FRAME_AFTER_BLACK_MS, w.frameAfterBlackMs);
        assertEquals(ClassicWoodcut.MAP_BACK_MS, w.closeMs);
        assertEquals(ClassicWoodcut.PICTURE, w.dirty);
        assertTrue(w.chainMs < 0.0);
    }

    /**
     * The Colonopedia's fathers' lists (D8b, I): "Gründerväter" with the
     * five types as the father box words them, then a type's five fathers
     * by their NAMES names; list boxes of the father box's form that fit
     * on the screen, Escape closes them, the bar where asked.
     */
    public void testThePediaLists() {
        final ClassicPackFiles pack = pack("testThePediaLists");
        if (pack == null) return;
        final ClassicText t = ClassicText.load(pack);
        final ClassicPedia pedia = ClassicPedia.load(pack);
        final ClassicAdvisorBox.Request types = ClassicPedia.typeList(pedia, t, 2);
        assertEquals(Arrays.asList("Handels- Berater", "Erkundungs- Berater",
            "Militär- Berater", "Politik- Berater", "Religions- Berater"), types.rows);
        assertEquals("Gründerväter", types.plainText());
        assertEquals(2, types.defaultRow);
        assertEquals(ClassicAdvisorBox.Bar.DISMISSED, types.escapeAnswer());
        final ClassicFont tiny = pack.font(ClassicFont.TINY);
        assertNotNull(ClassicAdvisorBox.layout(types, tiny, null));
        final ClassicAdvisorBox.Request trade = ClassicPedia.fatherList(pedia, t, 0, 9);
        assertEquals(Arrays.asList("Adam Smith", "Jakob Fugger", "Peter Minuit",
            "Peter Stuyvesant", "Jan de Witt"), trade.rows);
        assertEquals("Gründerväter (Handels- Berater)", trade.plainText());
        assertEquals(4, trade.defaultRow);
        assertNotNull(ClassicAdvisorBox.layout(trade, tiny, null));
        int all = 0;
        for (int i = 0; i < ClassicPedia.TYPES; i++) {
            final ClassicAdvisorBox.Request r = ClassicPedia.fatherList(pedia, t, i, 0);
            assertEquals(5, r.rows.size());
            all += r.rows.size();
        }
        assertEquals(25, all);
        assertEquals(ClassicFathers.REOPEN_CHAIN_MS,
                     ClassicPedia.fatherList(pedia, t, 1, 0,
                                             ClassicFathers.REOPEN_CHAIN_MS).chainMs);
        assertNull(ClassicPedia.fatherList(pedia, t, 5, 0));
        assertNull(ClassicPedia.typeList(null, t, 0));
    }

    /**
     * The golden checks: @FREEDOM with Minuit over #39296 (its whole box),
     * the empty hall against #39719 and the hall with Minuit against
     * #39775 (the whole screen): 0 px off, the original's arrow apart.
     */
    public void testGolden() throws Exception {
        final File dir = clipDir();
        final ClassicPackFiles pack = pack("testGolden");
        if (dir == null || pack == null) return;
        final Game game = getStandardGame();
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final ClassicText t = ClassicText.load(pack);
        final ClassicFont tiny = pack.font(ClassicFont.TINY);
        final ClassicAdvisorBox.Request r = ClassicCongress.freedom(t, dutch,
            father("peterMinuit"), 0.0);
        final int[] box = ClassicListBoxTest.compare(new File(dir, "frame_039296.png"),
            ClassicAdvisorBox.layout(r, tiny, null), -1,
            pack.image(ClassicMenuBar.WOOD_KEY), tiny);
        final int[] empty = compare(new File(dir, "frame_039719.png"),
                                    ClassicCongress.hall(pack, List.of()));
        final int[] minuit = compare(new File(dir, "frame_039775.png"),
                                     ClassicCongress.hall(pack, List.of(2)));
        System.out.println(getClass().getSimpleName() + ": golden check, @FREEDOM "
            + box[0] + " px, off " + box[1] + " (+" + box[2] + " arrow); empty hall"
            + " off " + empty[0] + " (+" + empty[1] + " arrow); hall with Minuit off "
            + minuit[0] + " (+" + minuit[1] + " arrow)");
        assertEquals(236 * 30, box[0]);
        assertEquals(0, box[1] + box[2]);
        assertEquals(0, empty[0]);
        assertEquals(0, minuit[0]);
        assertTrue("arrow pixels " + minuit[1], minuit[1] <= 100);
    }

    /** @return {off, off on the original's arrow (indices 0, 7, 15)} over the screen. */
    private static int[] compare(File file, BufferedImage mine) throws Exception {
        final BufferedImage frame = ImageIO.read(file);
        assertNotNull(file.toString(), frame);
        final Raster idx = (frame.getColorModel() instanceof IndexColorModel)
            ? frame.getRaster() : null;
        final int[] out = new int[2];
        for (int y = 0; y < 200; y++) {
            for (int x = 0; x < 320; x++) {
                if ((frame.getRGB(x, y) & 0xFFFFFF) == (mine.getRGB(x, y) & 0xFFFFFF)) continue;
                final int i = (idx == null) ? -1 : idx.getSample(x, y, 0);
                out[(i == 0 || i == 7 || i == 15) ? 1 : 0]++;
            }
        }
        return out;
    }

    /** @return Whether two 320x200 pictures have the same colours. */
    private static boolean same(BufferedImage a, BufferedImage b) {
        if (a == null || b == null) return false;
        for (int y = 0; y < 200; y++) {
            for (int x = 0; x < 320; x++) {
                if ((a.getRGB(x, y) & 0xFFFFFF) != (b.getRGB(x, y) & 0xFFFFFF)) return false;
            }
        }
        return true;
    }
}
