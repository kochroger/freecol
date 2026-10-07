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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import net.sf.freecol.client.ClientOptions;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.ModelMessage;
import net.sf.freecol.common.model.ModelMessage.MessageType;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.option.BooleanOption;
import net.sf.freecol.common.option.IntegerOption;
import net.sf.freecol.tools.classicassets.ClassicAssetDecoderTest;
import net.sf.freecol.tools.classicassets.FfDecoder;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * Tests of the original's two option boxes ({@link ClassicOptionBoxes},
 * build spec W14): their rows and states, the geometry of both boxes
 * (landing-slow 01-options section 2, V; the colony box by the same rule,
 * I), a flip acting at once and kept for the next opening and session, the
 * three FreeCol option rows, and the colony reports the report rows hold
 * back.  Headless, on a synthetic GAME.TXT.  With the converted pack and
 * {@code -Dclassic.clips}: the boxes drawn from the pack's GAME.TXT against
 * the clips' frames (landing-slow, clips 006 and 007), 0 px off apart from
 * the original's mouse arrow, and the 9-px flip.  The original's texts are
 * read from the pack, never written here.
 */
public class ClassicOptionBoxesTest extends FreeColTestCase {

    /** Bytes from a string whose chars are raw byte values. */
    private static byte[] raw(String s) {
        final ByteArrayOutputStream b = new ByteArrayOutputStream();
        for (int i = 0; i < s.length(); i++) b.write(s.charAt(i) & 0xFF);
        return b.toByteArray();
    }

    /** A synthetic checkbox message of n rows "~xa a". */
    private static String box(String section, int n, boolean checkbox) {
        final StringBuilder sb = new StringBuilder("@" + section + "\r\n@width=220\r\n");
        if (checkbox) sb.append("@checkbox\r\n");
        sb.append("a a\r\n@options\r\n");
        for (int i = 0; i < n; i++) sb.append("~").append((char) ('a' + i)).append("a a\r\n");
        return sb.append("\r\n").toString();
    }

    private static final String NAMES = "@COUNTRY\r\nAlpha,  1\r\n\r\n";
    private static final String LABELS = "@MISC\r\nm0\r\n\r\n";

    /** Temporary packs, deleted after each test. */
    private final List<File> dirs = new ArrayList<>();

    @Override
    protected void tearDown() throws Exception {
        for (File d : this.dirs) {
            final File text = new File(d, "text");
            for (String n : new String[] { "GAME.TXT", "NAMES.TXT", "LABELS.TXT" }) {
                new File(text, n).delete();
            }
            text.delete();
            d.delete();
        }
        super.tearDown();
    }

    /** The texts of a synthetic pack with this GAME.TXT. */
    private ClassicText text(String game) throws IOException {
        final File dir = Files.createTempDirectory("classic-options").toFile();
        this.dirs.add(dir);
        final File text = new File(dir, "text");
        assertTrue(text.mkdirs());
        Files.write(new File(text, "GAME.TXT").toPath(), raw(";\r\n\r\n" + game + "@END\r\n"));
        Files.write(new File(text, "NAMES.TXT").toPath(), raw(NAMES));
        Files.write(new File(text, "LABELS.TXT").toPath(), raw(LABELS));
        final ClassicText t = ClassicText.load(ClassicPackFiles.forDirectory(dir));
        assertNotNull(t);
        return t;
    }

    /** Both boxes as GAME.TXT has them: 8 and 10 checkbox rows. */
    private ClassicText goodText() throws IOException {
        return text(box(ClassicOptionBoxes.GAME_SECTION, 8, true)
                    + box(ClassicOptionBoxes.COLONY_SECTION, 10, true));
    }

    /** A store over a map, every set recorded. */
    private static final class MapStore implements ClassicOptionBoxes.Store {

        final Map<String, Boolean> values = new HashMap<>();
        final List<String> sets = new ArrayList<>();

        @Override
        public boolean get(String key) {
            final Boolean v = this.values.get(key);
            return v != null && v;
        }

        @Override
        public void set(String key, boolean on) {
            this.values.put(key, on);
            this.sets.add(key + "=" + on);
        }
    }

    /** A store in state A (landing-slow #395) and the colony rows all on. */
    private static MapStore stateA() {
        final MapStore s = new MapStore();
        for (String k : ClassicPrefs.GAME_ROWS) s.values.put(k, Boolean.TRUE);
        s.values.put(ClassicPrefs.MOVE_ACCELERATOR, Boolean.FALSE);
        s.values.put(ClassicPrefs.END_TURN_PROMPT, Boolean.FALSE);
        for (String k : ClassicPrefs.COLONY_ROWS) s.values.put(k, Boolean.TRUE);
        return s;
    }

    private static int[] glyph(int code, int w) {
        final int[] g = new int[2 + w];
        g[0] = code;
        g[1] = w;
        g[2] = 1;
        return g;
    }

    /** 'a' 3 wide, ' ' 2: enough to lay out the synthetic boxes. */
    private static ClassicFont font() {
        final FfDecoder.Font f = FfDecoder.decodePart(ClassicAssetDecoderTest.ffPart(1, 6,
            new int[][] { glyph('a', 3), glyph(' ', 2) }));
        return ClassicFont.fromAtlas(FfDecoder.toAtlas(f), FfDecoder.metrics(f));
    }

    /** The client's options with the three rows' FreeCol options. */
    private static ClientOptions clientOptions(int period, boolean combat, boolean tips) {
        final ClientOptions co = new ClientOptions();
        final IntegerOption autosave = new IntegerOption(ClientOptions.AUTOSAVE_PERIOD, null);
        autosave.setValue(period);
        co.add(autosave);
        final BooleanOption pre = new BooleanOption(
            ClassicPrefs.CLIENT_OPTIONS.get(ClassicPrefs.COMBAT_ANALYSIS), null);
        pre.setValue(combat);
        co.add(pre);
        final BooleanOption tut = new BooleanOption(
            ClassicPrefs.CLIENT_OPTIONS.get(ClassicPrefs.TUTOR_TIPS), null);
        tut.setValue(tips);
        co.add(tut);
        return co;
    }


    // The boxes

    public void testTheRowsOfTheBoxes() {
        assertEquals(ClassicPrefs.GAME_ROWS, ClassicOptionBoxes.keys(ClassicOptionBoxes.GAME_SECTION));
        assertEquals(ClassicPrefs.COLONY_ROWS,
                     ClassicOptionBoxes.keys(ClassicOptionBoxes.COLONY_SECTION));
        assertNull(ClassicOptionBoxes.keys("SAILHOME"));
        assertEquals(8, ClassicPrefs.GAME_ROWS.size());
        assertEquals(10, ClassicPrefs.COLONY_ROWS.size());
    }

    /**
     * Both boxes from GAME.TXT: the title, one checkbox row per key in the
     * store's states, the bar on row 1, Escape with no row; centred at
     * @width 220: (47,56,226,88) and (47,48,226,104), rows at glyph top
     * y + 19 + 8i, the bar x 51-268.
     */
    public void testTheBoxesFromGameTxt() throws Exception {
        final ClassicText t = goodText();
        final MapStore s = stateA();
        final ClassicAdvisorBox.Request g = ClassicOptionBoxes.request(t,
            ClassicOptionBoxes.GAME_SECTION, s, null);
        assertNotNull(g);
        assertEquals(ClassicOptionBoxes.GAME_SECTION, g.id);
        assertTrue(g.isCheckbox());
        assertEquals(8, g.rows.size());
        assertEquals("XXooXXXX", ClassicAdvisorBox.checkString(g.checks));
        assertEquals(220, g.width);
        assertEquals(0, g.defaultRow);
        assertEquals(-1, g.cancelRow);
        assertTrue(g.outsideCancels);
        // 2.5 frames after the menu row (the clip: 2-4).
        assertEquals(35.67, g.openDelayMs, 0.01);
        assertEquals("a a", g.title);                 // the stopgap's: the title line
        assertEquals("a a", g.plainText());
        final ClassicAdvisorBox.Layout gl = ClassicAdvisorBox.layout(g, font(), null);
        assertEquals(new Rectangle(47, 56, 226, 88), gl.box);
        assertEquals(1, gl.promptLines());
        for (int i = 0; i < 8; i++) {
            assertEquals(56, gl.rows.get(i).x);
            assertEquals(75 + 8 * i, gl.rows.get(i).y);
            assertEquals(new Rectangle(51, 74 + 8 * i, 218, 7), gl.barRect(i));
        }
        final ClassicAdvisorBox.Request c = ClassicOptionBoxes.request(t,
            ClassicOptionBoxes.COLONY_SECTION, s, "T");
        assertNotNull(c);
        assertEquals(10, c.rows.size());
        assertEquals("XXXXXXXXXX", ClassicAdvisorBox.checkString(c.checks));
        assertEquals("T", c.title);
        final ClassicAdvisorBox.Layout cl = ClassicAdvisorBox.layout(c, font(), null);
        assertEquals(new Rectangle(47, 48, 226, 104), cl.box);
        assertEquals(67 + 8 * 9, cl.rows.get(9).y);
        // A flip sets its own row's key, at once.
        final ClassicAdvisorBox.Bar bar = new ClassicAdvisorBox.Bar(g);
        bar.press(3, true);
        bar.release(3, true);
        bar.down();
        bar.enter();
        assertEquals(Arrays.asList(ClassicPrefs.END_TURN_PROMPT + "=true",
                                   ClassicPrefs.AUTO_SAVE + "=false"), s.sets);
        final ClassicAdvisorBox.Bar cb = new ClassicAdvisorBox.Bar(c);
        cb.down();
        cb.space();
        assertEquals(ClassicPrefs.GOODS_TERRAIN_LABELS + "=false", s.sets.get(2));
        // The next opening reads the store again.
        assertEquals("XXoXoXXX", ClassicAdvisorBox.checkString(ClassicOptionBoxes.request(t,
            ClassicOptionBoxes.GAME_SECTION, s, null).checks));
    }

    /**
     * No box without the texts, for another section, or when GAME.TXT's
     * message is not the checkbox box of as many rows as the keys.
     */
    public void testABoxNeedsItsCheckboxMessage() throws Exception {
        final MapStore s = stateA();
        assertNull(ClassicOptionBoxes.request(null, ClassicOptionBoxes.GAME_SECTION, s, null));
        assertNull(ClassicOptionBoxes.request(goodText(), "SAILHOME", s, null));
        final ClassicText bad = text(box(ClassicOptionBoxes.GAME_SECTION, 7, true)
                                     + box(ClassicOptionBoxes.COLONY_SECTION, 10, false));
        assertNull(ClassicOptionBoxes.request(bad, ClassicOptionBoxes.GAME_SECTION, s, null));
        assertNull(ClassicOptionBoxes.request(bad, ClassicOptionBoxes.COLONY_SECTION, s, null));
        assertNull(ClassicOptionBoxes.request(text(box("OTHER", 8, true)),
                                              ClassicOptionBoxes.GAME_SECTION, s, null));
        assertTrue(s.sets.isEmpty());
    }

    /**
     * The game view's store: a classic row is its pref (in the file, so the
     * next opening and the next session see it); a FreeCol row without the
     * client reads what the box remembered, else on; the after-hook runs
     * after each set.
     */
    public void testTheStoreKeepsAFlip() throws Exception {
        final File f = File.createTempFile("classic-options", ".properties");
        f.deleteOnExit();
        assertTrue(f.delete());
        final ClassicPrefs prefs = new ClassicPrefs(f);
        final List<String> applied = new ArrayList<>();
        final ClassicOptionBoxes.Store s = ClassicOptionBoxes.store(prefs, null,
            (k, on) -> applied.add(k + "=" + on));
        assertFalse(s.get(ClassicPrefs.MOVE_ACCELERATOR));
        assertTrue(s.get(ClassicPrefs.WATER_CYCLING));
        assertTrue(s.get(ClassicPrefs.AUTO_SAVE));        // never set: on
        assertTrue(s.get(ClassicPrefs.REPORT_FOOD));
        final ClassicAdvisorBox.Request g = ClassicOptionBoxes.request(goodText(),
            ClassicOptionBoxes.GAME_SECTION, s, null);
        assertEquals("XXooXXXX", ClassicAdvisorBox.checkString(g.checks));
        final ClassicAdvisorBox.Bar bar = new ClassicAdvisorBox.Bar(g);
        bar.letter('C');                                  // row 2, the accelerator
        bar.letter('G');                                  // row 6, the water
        bar.letter('E');                                  // row 4, autosave
        assertEquals(Arrays.asList("moveAccelerator=true", "waterCycling=false",
                                   "autoSave=false"), applied);
        assertTrue(prefs.is(ClassicPrefs.MOVE_ACCELERATOR));
        assertEquals(Boolean.FALSE, prefs.remembered(ClassicPrefs.AUTO_SAVE));
        assertFalse(s.get(ClassicPrefs.AUTO_SAVE));
        // The next session.
        final ClassicPrefs next = new ClassicPrefs(f);
        assertTrue(next.is(ClassicPrefs.MOVE_ACCELERATOR));
        assertFalse(next.is(ClassicPrefs.WATER_CYCLING));
        assertEquals("XXXooXoX", ClassicAdvisorBox.checkString(ClassicOptionBoxes.request(
            goodText(), ClassicOptionBoxes.GAME_SECTION,
            ClassicOptionBoxes.store(next, null, null), null).checks));
    }

    /**
     * Autom. Sichern, Kampfanalyse and Tutortips are FreeCol options: the
     * box reads and sets them there (autosave on keeps a period already set,
     * else 1; off is 0) and remembers them; the next game view puts the
     * remembered values back, the others keep FreeCol's.
     */
    public void testTheFreeColRows() {
        final ClientOptions co = clientOptions(5, true, true);
        assertTrue(ClassicOptionBoxes.clientValue(co, ClassicPrefs.AUTO_SAVE));
        ClassicOptionBoxes.setClientValue(co, ClassicPrefs.AUTO_SAVE, true);
        assertEquals(5, (int) co.getOption(ClientOptions.AUTOSAVE_PERIOD,
                                            IntegerOption.class).getValue());
        ClassicOptionBoxes.setClientValue(co, ClassicPrefs.AUTO_SAVE, false);
        assertEquals(0, (int) co.getOption(ClientOptions.AUTOSAVE_PERIOD,
                                            IntegerOption.class).getValue());
        assertFalse(ClassicOptionBoxes.clientValue(co, ClassicPrefs.AUTO_SAVE));
        ClassicOptionBoxes.setClientValue(co, ClassicPrefs.AUTO_SAVE, true);
        assertEquals(1, (int) co.getOption(ClientOptions.AUTOSAVE_PERIOD,
                                            IntegerOption.class).getValue());
        try {
            ClassicOptionBoxes.clientValue(co, ClassicPrefs.WATER_CYCLING);
            fail();
        } catch (IllegalArgumentException e) {
            // expected: a classic pref
        }
        // The store with the client: reads and sets the option, remembers it.
        final ClassicPrefs prefs = new ClassicPrefs(null);
        final ClassicOptionBoxes.Store s = ClassicOptionBoxes.store(prefs, co, null);
        assertTrue(s.get(ClassicPrefs.COMBAT_ANALYSIS));
        s.set(ClassicPrefs.COMBAT_ANALYSIS, false);
        assertFalse(co.getOption(ClassicPrefs.CLIENT_OPTIONS.get(ClassicPrefs.COMBAT_ANALYSIS),
                                 BooleanOption.class).getValue());
        assertEquals(Boolean.FALSE, prefs.remembered(ClassicPrefs.COMBAT_ANALYSIS));
        assertFalse(s.get(ClassicPrefs.COMBAT_ANALYSIS));
        // The next game view: the remembered row comes back, the others stay.
        final ClientOptions fresh = clientOptions(3, true, false);
        ClassicOptionBoxes.applyRemembered(fresh, prefs);
        assertFalse(ClassicOptionBoxes.clientValue(fresh, ClassicPrefs.COMBAT_ANALYSIS));
        assertEquals(3, (int) fresh.getOption(ClientOptions.AUTOSAVE_PERIOD,
                                               IntegerOption.class).getValue());
        assertFalse(ClassicOptionBoxes.clientValue(fresh, ClassicPrefs.TUTOR_TIPS));
        ClassicOptionBoxes.applyRemembered(null, prefs);  // no client: nothing
        // A client without the options: the store falls back to the prefs.
        final ClassicOptionBoxes.Store empty = ClassicOptionBoxes.store(prefs,
            new ClientOptions(), null);
        assertFalse(empty.get(ClassicPrefs.COMBAT_ANALYSIS));
        assertTrue(empty.get(ClassicPrefs.TUTOR_TIPS));
        empty.set(ClassicPrefs.TUTOR_TIPS, false);
        assertEquals(Boolean.FALSE, prefs.remembered(ClassicPrefs.TUTOR_TIPS));
    }

    /**
     * The report rows hold back exactly their notices, in order; every
     * other notice passes, whatever its type.
     */
    public void testTheReportRowsHoldBackTheirNotices() {
        final Game game = getStandardGame();
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final ModelMessage famine = new ModelMessage(MessageType.WARNING,
            "model.colony.famineFeared", dutch, dutch);
        final ModelMessage soL = new ModelMessage(MessageType.SONS_OF_LIBERTY,
            "model.colony.soLIncrease", dutch, dutch);
        final ModelMessage father = new ModelMessage(MessageType.SONS_OF_LIBERTY,
            "model.player.foundingFatherJoinedCongress", dutch, dutch);
        final ModelMessage needs = new ModelMessage(MessageType.MISSING_GOODS,
            "model.colony.buildableNeedsGoods", dutch, dutch);
        final List<ModelMessage> all = Arrays.asList(famine, soL, father, needs, null);
        final ClassicPrefs prefs = new ClassicPrefs(null);
        assertEquals(Arrays.asList(famine, soL, father, needs),
                     ClassicOptionBoxes.reportsShown(all, prefs));
        prefs.set(ClassicPrefs.REPORT_FOOD, false);
        prefs.set(ClassicPrefs.REPORT_SONS_OF_LIBERTY, false);
        assertEquals(Arrays.asList(father, needs), ClassicOptionBoxes.reportsShown(all, prefs));
        prefs.set(ClassicPrefs.REPORT_TOOLS, false);
        assertEquals(Arrays.asList(father), ClassicOptionBoxes.reportsShown(all, prefs));
        assertTrue(ClassicOptionBoxes.reportsShown(null, prefs).isEmpty());
    }


    // With the pack and the clips

    /** The pack's texts and FONTTINY, or null (then the test is skipped). */
    private static Object[] pack(String test) {
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final ClassicText t = ClassicText.load(pack);
        final ClassicFont tiny = (pack == null) ? null : pack.font(ClassicFont.TINY);
        if (t == null || tiny == null) {
            System.err.println("ClassicOptionBoxesTest: " + test
                + " skipped, no pack texts or FONTTINY (ant classic-assets)");
            return null;
        }
        return new Object[] { pack, t, tiny, pack.image(ClassicMenuBar.WOOD_KEY) };
    }

    /** A store with the states of a clip ("XXooXXXX"). */
    private static ClassicOptionBoxes.Store states(String section, String xo) {
        final MapStore s = new MapStore();
        final List<String> keys = ClassicOptionBoxes.keys(section);
        for (int i = 0; i < keys.size(); i++) s.values.put(keys.get(i), xo.charAt(i) == 'X');
        return s;
    }

    /**
     * The pack's two boxes: GAME.TXT's 8 and 10 checkbox rows, the gold
     * letters of the 8 (I F S E A C Y T; the colony rows mark whole words),
     * the measured places with the real FONTTINY, and the flip of one row
     * changes exactly the 3x3 inside of its bullet: 9 px (landing-slow
     * #1784 -> #1785, row 3).
     */
    public void testThePacksBoxes() {
        final Object[] p = pack("testThePacksBoxes");
        if (p == null) return;
        final ClassicText t = (ClassicText) p[1];
        final ClassicFont tiny = (ClassicFont) p[2];
        final BufferedImage wood = (BufferedImage) p[3];
        final ClassicAdvisorBox.Request a = ClassicOptionBoxes.request(t,
            ClassicOptionBoxes.GAME_SECTION, states(ClassicOptionBoxes.GAME_SECTION,
                                                    "XXooXXXX"), null);
        assertNotNull(a);
        final StringBuilder keys = new StringBuilder();
        for (String r : a.rows) keys.append(ClassicMenuModel.hotkey(r));
        assertEquals("IFSEACYT", keys.toString());
        final ClassicAdvisorBox.Layout l = ClassicAdvisorBox.layout(a, tiny, null);
        assertEquals(new Rectangle(47, 56, 226, 88), l.box);
        assertEquals(1, l.promptLines());
        final ClassicAdvisorBox.Request c = ClassicOptionBoxes.request(t,
            ClassicOptionBoxes.COLONY_SECTION, states(ClassicOptionBoxes.COLONY_SECTION,
                                                      "XXXXXXXXXX"), null);
        assertNotNull(c);
        for (String r : c.rows) assertEquals(r, 0, ClassicMenuModel.hotkey(r));
        assertEquals(new Rectangle(47, 48, 226, 104),
                     ClassicAdvisorBox.layout(c, tiny, null).box);
        // The bullet takes 6 + 2 px: the text starts at box + 17.
        assertEquals(8, tiny.markedWidth(ClassicAdvisorBox.checkRow("", true)));
        assertEquals(8, tiny.markedWidth(ClassicAdvisorBox.checkRow("", false)));
        // State A against state B, the bar on row 3: 9 px, the inside.
        final BufferedImage before = ClassicAdvisorBox.render(l, 3,
            new boolean[] { true, true, false, false, true, true, true, true }, wood, tiny);
        final BufferedImage after = ClassicAdvisorBox.render(l, 3,
            new boolean[] { true, true, false, true, true, true, true, true }, wood, tiny);
        int diff = 0;
        for (int y = 0; y < 200; y++) {
            for (int x = 0; x < 320; x++) {
                if (before.getRGB(x, y) == after.getRGB(x, y)) continue;
                diff++;
                assertTrue(x + "," + y, x >= 57 && x <= 59 && y >= 100 && y <= 102);
            }
        }
        assertEquals(9, diff);
    }

    /**
     * The clips' frames of the options box: (frame, states, bar row,
     * whether the original's arrow lies on the box).  landing-slow
     * {@code 01-support/optscan_all.txt}: state A at the first open (#395),
     * state B with the bar on row 3 after toggle 1 (#1868) and with the bar
     * removed by the press outside (#1933), session 2 opening in B (#4887),
     * C after toggle 2 (#5040), session 3 opening in C (#7110), D after
     * toggle 3 (#7257), E after toggle 4 (#7708); clip006 harness L (#845)
     * and clip007 (#468, and #714 with the bar removed), state A.
     */
    private static final Object[][] FRAMES = {
        { "landing-slow/frame_000395.png", "XXooXXXX", 0, false },
        { "landing-slow/frame_001868.png", "XXoXXXXX", 3, true },
        { "landing-slow/frame_001933.png", "XXoXXXXX", -1, false },
        { "landing-slow/frame_004887.png", "XXoXXXXX", 0, false },
        { "landing-slow/frame_005040.png", "XXXXXXXX", 2, true },
        { "landing-slow/frame_007110.png", "XXXXXXXX", 0, false },
        { "landing-slow/frame_007257.png", "XXoXXXXX", 2, true },
        { "landing-slow/frame_007708.png", "XXooXXXX", 3, true },
        { "clip006/frame_000845.png", "XXooXXXX", 0, true },
        { "clip007/frame_000468.png", "XXooXXXX", 0, true },
        { "clip007/frame_000714.png", "XXooXXXX", -1, true },
    };

    /**
     * The golden check: "Spieloptionen festlegen" drawn from the pack's
     * GAME.TXT in each frame's states, with its bar, over the frame, gives
     * the frame's pixels on the whole box (47,56,226,88), 0 px off; only the
     * original's mouse arrow (frame indices 0, 7, 15 where we differ) is
     * counted and excused, and in the frames where it is off the box there
     * is none.
     */
    public void testGoldenAgainstTheClips() throws Exception {
        final String clips = System.getProperty(ClassicTerrainGoldenTest.CLIPS_PROPERTY);
        final File dir = (clips == null) ? null : new File(clips);
        if (dir == null || !new File(dir, "landing-slow").isDirectory()) {
            System.err.println(getClass().getSimpleName()
                + ": golden check skipped, no recordings (-D"
                + ClassicTerrainGoldenTest.CLIPS_PROPERTY + ")");
            return;
        }
        final Object[] p = pack("testGoldenAgainstTheClips");
        if (p == null) return;
        final ClassicText t = (ClassicText) p[1];
        final ClassicFont tiny = (ClassicFont) p[2];
        final BufferedImage wood = (BufferedImage) p[3];
        final StringBuilder fails = new StringBuilder();
        int compared = 0, arrow = 0;
        for (Object[] f : FRAMES) {
            final File file = new File(dir, (String) f[0]);
            final BufferedImage frame = ImageIO.read(file);
            assertNotNull(file.toString(), frame);
            final ClassicAdvisorBox.Request r = ClassicOptionBoxes.request(t,
                ClassicOptionBoxes.GAME_SECTION,
                states(ClassicOptionBoxes.GAME_SECTION, (String) f[1]), null);
            final ClassicAdvisorBox.Layout l = ClassicAdvisorBox.layout(r, tiny, null);
            final BufferedImage canvas = new BufferedImage(320, 200,
                BufferedImage.TYPE_INT_RGB);
            final Graphics2D g = canvas.createGraphics();
            g.drawImage(frame, 0, 0, null);
            ClassicAdvisorBox.paint(g, l, (Integer) f[2], wood, tiny);
            g.dispose();
            final Raster idx = (frame.getColorModel() instanceof IndexColorModel)
                ? frame.getRaster() : null;
            int diff = 0, mine = 0;
            for (int y = l.box.y; y < l.box.y + l.box.height; y++) {
                for (int x = l.box.x; x < l.box.x + l.box.width; x++) {
                    compared++;
                    if ((frame.getRGB(x, y) & 0xFFFFFF) == (canvas.getRGB(x, y) & 0xFFFFFF)) {
                        continue;
                    }
                    final int i = (idx == null) ? -1 : idx.getSample(x, y, 0);
                    if (i == 0 || i == 7 || i == 15) {
                        mine++;
                    } else {
                        diff++;
                    }
                }
            }
            arrow += mine;
            if (diff > 0 || (!(Boolean) f[3] && mine > 0)) {
                fails.append(' ').append(f[0]).append('=').append(diff)
                    .append('+').append(mine);
            }
        }
        System.out.println(getClass().getSimpleName() + ": golden check, "
            + FRAMES.length + " frames, " + compared + " px compared, off:"
            + ((fails.length() == 0) ? " none" : fails.toString())
            + ", " + arrow + " arrow px excused");
        assertEquals("pixels off:" + fails, 0, fails.length());
        assertEquals(FRAMES.length * 226 * 88, compared);
        assertTrue("arrow pixels " + arrow, arrow <= 80 * FRAMES.length);
    }
}
