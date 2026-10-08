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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

import net.sf.freecol.common.model.FoundingFather;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.HistoryEvent;
import net.sf.freecol.common.model.ModelMessage;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * Tests of the original's founding father choice (build spec D8a,
 * {@link ClassicFathers}) and its F1 page ({@link ClassicPedia}, D8b's
 * page): the index table, the rows, the box, the keys (Enter takes, F1
 * shows the page and the box comes back on row 1, Escape postpones the
 * choice to the next turn start),
 * when it comes (after the turn start's price messages, before the year
 * flips; never after independence or in the turn a father joined), and
 * the golden checks against clip008: the box at #12765, #13304, #48314,
 * #49787, #50129 and the pages at #40109, #48645, #49256, #49900, 0 px off
 * (the pages: apart from the original's mouse arrow).  The golden checks
 * need the pack and {@code -Dclassic.clips}, else they are skipped with a
 * note.
 */
public class ClassicFathersTest extends FreeColTestCase {

    /**
     * Answers each box with the next of its key lists on the box's real
     * bar; keys UP, DOWN, ENTER, ESC, F1, OUT (a click beside the box).  A
     * box left open answers DISMISSED.
     */
    private static final class ScriptPrompter implements ClassicGUI.Prompter {

        final Deque<String[]> script = new ArrayDeque<>();
        final List<ClassicAdvisorBox.Request> boxes = new ArrayList<>();

        ScriptPrompter then(String... keys) {
            this.script.add(keys);
            return this;
        }

        @Override
        public int ask(ClassicAdvisorBox.Request r) {
            this.boxes.add(r);
            final String[] keys = this.script.isEmpty() ? new String[0]
                : this.script.poll();
            final ClassicAdvisorBox.Bar bar = new ClassicAdvisorBox.Bar(r);
            for (String k : keys) {
                final int a;
                switch (k) {
                case "UP": a = bar.up(); break;
                case "DOWN": a = bar.down(); break;
                case "ENTER": a = bar.enter(); break;
                case "ESC": a = bar.escape(); break;
                case "F1": a = bar.help(); break;
                case "OUT":
                    bar.press(-1, false);
                    a = bar.release(-1, false);
                    break;
                default: a = bar.otherKey(); break;
                }
                if (a != ClassicAdvisorBox.Bar.OPEN) return a;
            }
            return ClassicAdvisorBox.Bar.DISMISSED;
        }
    }

    /** A classic GUI without a client: its game, player and prefs are the test's. */
    private static final class FatherGUI extends ClassicGUI {

        final Game game;
        final Player me;
        final ClassicPrefs mine = new ClassicPrefs(null);

        FatherGUI(Game game, Player me) {
            super(null);
            this.game = game;
            this.me = me;
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
        java.awt.Image iconOf(net.sf.freecol.common.model.FreeColObject display) {
            return null;   // no image resources here
        }

        @Override
        ClassicPrefs prefs() {
            return this.mine;
        }
    }

    /** Fathers by their short ids ("peterMinuit"). */
    private static List<FoundingFather> fathers(Specification s, String... ids) {
        final List<FoundingFather> out = new ArrayList<>();
        for (String id : ids) out.add(s.getFoundingFather("model.foundingFather." + id));
        return out;
    }

    /** The pack's texts, or null (then a test is skipped with a note). */
    private static ClassicText texts(String test) {
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        if (t == null) {
            System.err.println("ClassicFathersTest: " + test
                + " skipped, no pack texts (ant classic-assets)");
        }
        return t;
    }

    /**
     * The index table: 25 fathers, each in the rules, in NAMES @FATHERS
     * order (by their names and types where the pack is there), the type
     * column agreeing with FreeCol's.
     */
    public void testIndexTable() {
        final Specification s = spec();
        assertEquals(25, ClassicFathers.IDS.size());
        assertEquals(25, s.getFoundingFathers().size());
        for (String id : ClassicFathers.IDS) assertNotNull(id, s.getFoundingFather(id));
        assertEquals(2, ClassicFathers.index(s.getFoundingFather(
            "model.foundingFather.peterMinuit")));
        assertEquals(13, ClassicFathers.index(s.getFoundingFather(
            "model.foundingFather.francisDrake")));
        assertEquals(-1, ClassicFathers.index(null));
        final ClassicText t = texts("testIndexTable (names)");
        if (t == null) return;
        assertEquals("Adam Smith", ClassicFathers.name(t, 0));
        assertEquals("Hernando Cortez", ClassicFathers.name(t, 10));
        assertEquals("Bartolome de las Casas", ClassicFathers.name(t, 24));
        assertNull(ClassicFathers.name(t, 25));
        for (int n = 0; n < 25; n++) {
            final FoundingFather ff = s.getFoundingFather(ClassicFathers.IDS.get(n));
            assertEquals(ff.getId(), ff.getType().ordinal(), ClassicFathers.type(t, n));
        }
    }

    /** The rows: "name (type- Berater)", NAMES' spelling. */
    public void testRows() {
        final ClassicText t = texts("testRows");
        if (t == null) return;
        final Specification s = spec();
        final List<FoundingFather> ffs = fathers(s, "peterMinuit", "laSalle",
            "hernanCortes", "thomasJefferson", "fatherJeanDeBrebeuf");
        final List<String> rows = new ArrayList<>();
        for (FoundingFather ff : ffs) rows.add(ClassicFathers.row(t, ff));
        assertEquals(Arrays.asList("Peter Minuit (Handels- Berater)",
            "La Salle (Erkundungs- Berater)", "Hernando Cortez (Militär- Berater)",
            "Thomas Jefferson (Politik- Berater)",
            "Jean de Brebeuf (Religions- Berater)"), rows);
    }

    /**
     * The box: @WHICHFREEDOM at (42,59) 236x82, three prompt lines, the
     * rows at + 13, the gold footer, the bar on row 1, Escape closing it
     * with no row (G review: Roger's "Esc = Nein" but at the King's), no
     * click beside it, F1 to the hook; without the pack FreeCol's words
     * with the same keys.
     */
    public void testTheBox() {
        final Specification s = spec();
        final List<FoundingFather> ffs = fathers(s, "peterStuyvesant", "henryHudson",
            "francisDrake", "thomasJefferson", "fatherJeanDeBrebeuf");
        final int[] help = { -1 };
        final ClassicAdvisorBox.Request fb = ClassicFathers.request(null, ffs,
            row -> help[0] = row);
        assertEquals(5, fb.rows.size());
        assertEquals(0, fb.defaultRow);
        assertTrue(fb.escapes);
        assertEquals(ClassicAdvisorBox.Bar.DISMISSED, fb.escapeAnswer());
        assertFalse(fb.outsideCancels);
        assertEquals(ClassicMenuBox.LIST_INDENT, fb.rowIndent);
        assertNotNull(fb.help);
        assertTrue(fb.chainMs < 0);
        assertTrue(ClassicFathers.request(null, ffs, null,
            ClassicFathers.REOPEN_CHAIN_MS).chainMs > 0);
        final ClassicText t = texts("testTheBox (pack)");
        if (t == null) return;
        final ClassicAdvisorBox.Request r = ClassicFathers.request(t, ffs,
            row -> help[0] = row);
        assertEquals(ClassicFathers.SECTION, r.id);
        assertEquals("(F1 für Hilfe)", r.footer);
        assertEquals("Peter Stuyvesant (Handels- Berater)", r.rows.get(0));
        final ClassicFont tiny = ClassicPackFiles.runtime().font(ClassicFont.TINY);
        final ClassicAdvisorBox.Layout l = ClassicAdvisorBox.layout(r, tiny, null);
        assertEquals(new Rectangle(42, 59, 236, 82), l.box);
        assertEquals(3, l.promptLines());
        assertEquals(55, l.rows.get(0).x);
        assertEquals(90, l.rows.get(0).y);
        assertEquals(122, l.rows.get(4).y);
        assertEquals(132, l.footer.y);
        assertEquals(276, l.footer.x + tiny.stringWidth(r.footer));
        assertEquals(ClassicAdvisorBox.Bar.DISMISSED, new ClassicAdvisorBox.Bar(r).escape());
        final ClassicAdvisorBox.Bar bar = new ClassicAdvisorBox.Bar(r);
        bar.press(-1, false);
        assertEquals(ClassicAdvisorBox.Bar.OPEN, bar.release(-1, false));
        bar.down();
        bar.down();
        assertEquals(ClassicAdvisorBox.Bar.HELP, bar.help());
        assertEquals(2, help[0]);
    }

    /** The clip's five father boxes: frame, offer, bar row. */
    private static final Object[][] BOXES = {
        { "frame_012765.png", new String[] { "peterMinuit", "laSalle", "hernanCortes",
            "thomasJefferson", "williamPenn" }, 0 },
        { "frame_013304.png", new String[] { "peterMinuit", "laSalle", "hernanCortes",
            "thomasJefferson", "williamPenn" }, 4 },
        { "frame_048314.png", new String[] { "peterStuyvesant", "henryHudson",
            "francisDrake", "thomasJefferson", "fatherJeanDeBrebeuf" }, 0 },
        { "frame_049787.png", new String[] { "peterStuyvesant", "henryHudson",
            "francisDrake", "thomasJefferson", "fatherJeanDeBrebeuf" }, 2 },
        { "frame_050129.png", new String[] { "peterStuyvesant", "henryHudson",
            "francisDrake", "thomasJefferson", "fatherJeanDeBrebeuf" }, 0 },
    };

    /** The clip's four pages: frame, father row. */
    private static final Object[][] PAGES = {
        { "frame_040109.png", 2 }, { "frame_048645.png", 3 },
        { "frame_049256.png", 8 }, { "frame_049900.png", 13 },
    };

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
     * The golden check of the box: the clip's five father boxes, built by
     * {@link ClassicFathers#request} from the pack's texts and the offered
     * fathers, with the bar on the frame's row, drawn over the frame: 0 px
     * off on the whole box (42,59,236,82); no mouse arrow lies on it.
     */
    public void testGoldenBoxes() throws Exception {
        final File dir = clipDir();
        final ClassicText t = texts("testGoldenBoxes");
        if (dir == null || t == null) return;
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final ClassicFont tiny = pack.font(ClassicFont.TINY);
        final BufferedImage wood = pack.image(ClassicMenuBar.WOOD_KEY);
        final StringBuilder fails = new StringBuilder();
        int compared = 0;
        for (Object[] b : BOXES) {
            final ClassicAdvisorBox.Request r = ClassicFathers.request(t,
                fathers(spec(), (String[]) b[1]), null);
            final ClassicAdvisorBox.Layout l = ClassicAdvisorBox.layout(r, tiny, null);
            final int[] got = ClassicListBoxTest.compare(new File(dir, (String) b[0]),
                l, (Integer) b[2], wood, tiny);
            compared += got[0];
            if (got[1] + got[2] > 0) {
                fails.append(' ').append(b[0]).append('=').append(got[1])
                    .append('+').append(got[2]);
            }
        }
        System.out.println(getClass().getSimpleName() + ": golden check, "
            + BOXES.length + " father boxes, " + compared + " px compared, off:"
            + ((fails.length() == 0) ? " none" : fails.toString()));
        assertEquals("pixels off:" + fails, 0, fails.length());
        assertEquals(BOXES.length * 236 * 82, compared);
    }

    /**
     * The golden check of the F1 pages: Minuit, Stuyvesant, Hudson (an
     * {@code @SMALLFONT} entry at 315) and Drake from the pack's
     * PEDIA.TXT, against the whole screen: 0 px off apart from the
     * original's arrow (indices 0, 7, 15).
     */
    public void testGoldenPages() throws Exception {
        final File dir = clipDir();
        final ClassicText t = texts("testGoldenPages");
        if (dir == null || t == null) return;
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final ClassicPedia pedia = ClassicPedia.load(pack);
        assertNotNull("PEDIA.TXT in the pack", pedia);
        final ClassicFont tiny = pack.font(ClassicFont.TINY);
        final BufferedImage wood = pack.image(ClassicPedia.WOODPANL_KEY);
        assertNotNull(wood);
        final StringBuilder fails = new StringBuilder();
        int arrow = 0;
        for (Object[] p : PAGES) {
            final BufferedImage page = ClassicPedia.fatherPage(pedia, t, tiny, wood,
                                                               (Integer) p[1]);
            assertNotNull(page);
            final BufferedImage frame = ImageIO.read(new File(dir, (String) p[0]));
            final Raster idx = (frame.getColorModel() instanceof IndexColorModel)
                ? frame.getRaster() : null;
            int off = 0;
            for (int y = 0; y < 200; y++) {
                for (int x = 0; x < 320; x++) {
                    if ((frame.getRGB(x, y) & 0xFFFFFF) == (page.getRGB(x, y) & 0xFFFFFF)) {
                        continue;
                    }
                    final int i = (idx == null) ? -1 : idx.getSample(x, y, 0);
                    if (i == 0 || i == 7 || i == 15) arrow++; else off++;
                }
            }
            if (off > 0) fails.append(' ').append(p[0]).append('=').append(off);
        }
        System.out.println(getClass().getSimpleName() + ": golden check, "
            + PAGES.length + " pages, " + (PAGES.length * 64000) + " px compared, off:"
            + ((fails.length() == 0) ? " none" : fails.toString()) + ", "
            + arrow + " arrow px excused");
        assertEquals("pixels off:" + fails, 0, fails.length());
        assertTrue("arrow pixels " + arrow, arrow <= 100 * PAGES.length);
    }

    /**
     * PEDIA.TXT's sections: {@code @width}, {@code @SMALLFONT} as a
     * directive (not a section), a {@code @;} comment ending a section,
     * trailing blank lines dropped, the categories; and the page's lines:
     * the title without its '^', paragraphs with one blank line, the gold
     * carried over a line end, {@code %%} as '%'.
     */
    public void testPediaText() {
        final ClassicPedia p = ClassicPedia.parse(Arrays.asList(
            "@; head", "", "@PEDIA", "Ware", "Einheit", "", "@; x",
            "@FATHER8", "@width=315", "@SMALLFONT", "^{Henry Hudson (1)}",
            "a b {c d", "e} f 100%%.", "^", "^", "g", "", "",
            "@; next", "@FATHER9", "^{La Salle}", "h"));
        assertEquals("Einheit", p.category(1));
        assertNull(p.category(2));
        final ClassicPedia.Entry h = p.entry("FATHER8");
        assertEquals(315, h.width);
        assertTrue(h.smallFont);
        assertEquals(6, h.lines.size());
        assertNull(p.entry("SMALLFONT"));
        assertEquals(ClassicPedia.DEFAULT_WIDTH, p.entry("FATHER9").width);
        assertFalse(p.entry("FATHER9").smallFont);
        // Laid out with a font of 'a' 3 px and blank 2 px: one word a line
        // at a narrow width.
        final ClassicFont tiny = ClassicPackFiles.runtime() == null ? null
            : ClassicPackFiles.runtime().font(ClassicFont.TINY);
        if (tiny == null) return;
        final List<ClassicPedia.Line> lines = ClassicPedia.body(h, tiny);
        assertEquals("{Henry Hudson (1)}", lines.get(0).text);
        assertEquals(ClassicPedia.TITLE_TOP, lines.get(0).top);
        assertEquals("a b {c d e} f 100%.", lines.get(1).text);
        assertEquals(43, lines.get(1).top);
        assertEquals("g", lines.get(2).text);
        assertEquals(57, lines.get(2).top);         // one blank line for "^ ^"
        // A narrow entry wraps, the gold carried into the next line.
        final ClassicPedia.Entry narrow = new ClassicPedia.Entry(30,
            false, Arrays.asList("^T", "aaaa {bbbb cccc} dddd"));
        final List<ClassicPedia.Line> w = ClassicPedia.body(narrow, tiny);
        assertTrue(w.size() > 2);
        boolean carried = false;
        for (ClassicPedia.Line l : w) carried |= l.text.startsWith("{cccc");
        assertTrue(w.toString(), carried);
    }

    /**
     * When the box is withheld: after independence (Roger), in the turn a
     * father joined (the original offers the next a turn later).
     */
    public void testWithheld() {
        final Game game = getStandardGame();
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        assertNull(ClassicFathers.withheld(dutch, game.getTurn()));
        assertNotNull(ClassicFathers.withheld(null, game.getTurn()));
        dutch.addHistory(new HistoryEvent(game.getTurn(),
            HistoryEvent.HistoryEventType.FOUNDING_FATHER, dutch));
        assertEquals("a father joined this turn",
                     ClassicFathers.withheld(dutch, game.getTurn()));
        final Player french = game.getPlayerByNationId("model.nation.french");
        assertNull(ClassicFathers.withheld(french, game.getTurn()));
        french.changePlayerType(Player.PlayerType.REBEL);
        assertEquals("after independence", ClassicFathers.withheld(french, game.getTurn()));
        french.changePlayerType(Player.PlayerType.INDEPENDENT);
        assertEquals("after independence", ClassicFathers.withheld(french, game.getTurn()));
    }

    /**
     * The flow at a turn start: the offer waits; the turn report asks its
     * price messages, then the father box, then the other notices.  In
     * the box a click beside it does nothing, F1 shows the page
     * of the barred father (with the pack) and the box comes back on row
     * 1, a little later; Enter takes the father under the bar, whom the
     * handler gets, once.
     */
    public void testTheTurnStart() {
        final Game game = getStandardGame();
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final FatherGUI gui = new FatherGUI(game, dutch);
        final ScriptPrompter keys = new ScriptPrompter();
        gui.prompter = keys;
        final List<FoundingFather> ffs = fathers(spec(), "peterStuyvesant",
            "henryHudson", "francisDrake", "thomasJefferson", "fatherJeanDeBrebeuf");
        final List<FoundingFather> chosen = new ArrayList<>();
        gui.showChooseFoundingFatherDialog(ffs, chosen::add);
        assertTrue(keys.boxes.isEmpty());                 // it waits
        final ModelMessage price = new ModelMessage(
            ModelMessage.MessageType.MARKET_PRICES, "model.market.priceIncrease",
            dutch, dutch);
        final ModelMessage famine = new ModelMessage(
            ModelMessage.MessageType.WARNING, "model.colony.famineFeared", dutch, dutch);
        final boolean page = ClassicPedia.load(ClassicPackFiles.runtime()) != null
            && ClassicText.load(ClassicPackFiles.runtime()) != null;
        keys.then("X")                                    // the price notice
            .then("OUT", "DOWN", "F1");                   // the box: Hudson's page
        if (page) keys.then("X");                         // the page
        keys.then("DOWN", "DOWN", "ENTER")                // the box again: Drake
            .then("X");                                   // the famine notice
        gui.showReportTurnPanel(List.of(famine, price));
        final List<String> ids = new ArrayList<>();
        for (ClassicAdvisorBox.Request r : keys.boxes) ids.add(r.id);
        final List<String> want = new ArrayList<>(Arrays.asList(
            "message model.market.priceIncrease", ClassicFathers.SECTION));
        if (page) want.add("pedia model.foundingFather.henryHudson");
        want.addAll(Arrays.asList(ClassicFathers.SECTION,
                                  "message model.colony.famineFeared"));
        assertEquals(want, ids);
        assertEquals(List.of(spec().getFoundingFather("model.foundingFather.francisDrake")),
                     chosen);
        final ClassicAdvisorBox.Request again = keys.boxes.get(page ? 3 : 2);
        assertEquals(0, again.defaultRow);
        assertEquals(ClassicFathers.REOPEN_CHAIN_MS, again.chainMs);
        if (page) {
            final ClassicAdvisorBox.Request p = keys.boxes.get(2);
            assertNotNull(p.picture);
            assertEquals(ClassicFathers.PAGE_CHAIN_MS, p.chainMs);
        }
        // Taken: the next report has no box for it, the turn start no hold.
        keys.boxes.clear();
        keys.then("X");
        gui.showReportTurnPanel(List.of(price));
        assertEquals(1, keys.boxes.size());
        assertFalse(gui.holdTurnStart());
        assertEquals(1, chosen.size());
    }

    /**
     * Escape in the father box (G review: Roger's rule, Escape is "Nein"
     * everywhere but at the King's decisions): the box closes with no
     * father, the handler is not called and nothing is held; the server
     * offers the same choice again at the next turn start, and then a
     * father is taken.
     */
    public void testEscapePostponesTheChoice() {
        final Game game = getStandardGame();
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final FatherGUI gui = new FatherGUI(game, dutch);
        final ScriptPrompter keys = new ScriptPrompter();
        gui.prompter = keys;
        final List<FoundingFather> ffs = fathers(spec(), "adamSmith", "laSalle",
            "paulRevere", "pocahontas", "williamPenn");
        final List<FoundingFather> chosen = new ArrayList<>();
        gui.showChooseFoundingFatherDialog(ffs, chosen::add);
        keys.then("DOWN", "ESC", "ENTER");
        gui.askFathers(gui.takePendingFathers());
        assertEquals(1, keys.boxes.size());               // not asked again now
        assertTrue(chosen.isEmpty());
        assertFalse(gui.holdTurnStart());
        // The next turn start: the same offer, now a father is taken.
        gui.showChooseFoundingFatherDialog(ffs, chosen::add);
        keys.then("DOWN", "ENTER");
        gui.askFathers(gui.takePendingFathers());
        assertEquals(List.of(spec().getFoundingFather("model.foundingFather.laSalle")),
                     chosen);
    }

    /**
     * Without notices the offer is asked just before the year flips: the
     * turn flow's hold posts it once and holds until it is taken.  Out of
     * the turn report (an out-of-turn notice) it stays pending; a box left
     * without a choice (the game view went) calls no handler; after
     * independence nothing is asked.
     */
    public void testTheHoldBeforeTheYearFlips() throws Exception {
        final Game game = getStandardGame();
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final FatherGUI gui = new FatherGUI(game, dutch);
        final ScriptPrompter keys = new ScriptPrompter();
        gui.prompter = keys;
        final List<FoundingFather> chosen = new ArrayList<>();
        assertFalse(gui.holdTurnStart());
        gui.showChooseFoundingFatherDialog(fathers(spec(), "adamSmith", "laSalle",
            "paulRevere", "pocahontas", "williamPenn"), chosen::add);
        // An out-of-turn notice does not ask it.
        keys.then("X");
        gui.showModelMessages(List.of(new ModelMessage(
            ModelMessage.MessageType.WARNING, "model.colony.famineFeared", dutch, dutch)));
        assertEquals(1, keys.boxes.size());
        keys.then("ENTER");
        SwingUtilities.invokeAndWait(() -> {
                assertTrue(gui.holdTurnStart());
                assertTrue(gui.holdTurnStart());          // posted once
            });
        SwingUtilities.invokeAndWait(() -> { });          // the posted box
        assertEquals(2, keys.boxes.size());
        assertEquals(ClassicFathers.SECTION, keys.boxes.get(1).id);
        assertEquals(List.of(spec().getFoundingFather("model.foundingFather.adamSmith")),
                     chosen);
        assertFalse(gui.holdTurnStart());
        // Left open (the game view went): no handler.
        gui.showChooseFoundingFatherDialog(fathers(spec(), "adamSmith"), chosen::add);
        gui.askFathers(gui.takePendingFathers());
        assertEquals(1, chosen.size());
        // After independence: no box.
        keys.boxes.clear();
        dutch.changePlayerType(Player.PlayerType.REBEL);
        gui.showChooseFoundingFatherDialog(fathers(spec(), "adamSmith"), chosen::add);
        keys.then("ENTER");
        gui.askFathers(gui.takePendingFathers());
        assertTrue(keys.boxes.isEmpty());
        assertEquals(1, chosen.size());
        // A null or empty offer is nothing.
        gui.showChooseFoundingFatherDialog(new ArrayList<>(), chosen::add);
        assertNull(gui.takePendingFathers());
    }

    /** The congress seam, recorded in the order of the boxes ("hall n before"). */
    private static ClassicGUI.Congress hallSeam(ScriptPrompter keys, List<String> log,
                                                boolean shown) {
        return (ff, before) -> {
            log.add(keys.boxes.size() + ":hall " + ClassicFathers.index(ff) + " " + before);
            return (shown) ? 1L : ClassicAdvisorLayer.NOT_SHOWN;
        };
    }

    /**
     * D8c at a turn start: a father joined (the set grew, his history
     * event of this turn): @FREEDOM first, 342 ms after it is asked, then
     * the hall with the fathers before him, then the price notice, then the
     * emigration notice (F A3: FreeCol's order was the other way round);
     * FreeCol's own join notice is not shown; the offer of that turn is
     * withheld.  The next turn's report shows no join again; a second join
     * brings the hall with the first father in it.  A father seen at the
     * view's build never joins.
     */
    public void testTheJoinSequence() throws Exception {
        final Game game = getStandardGame();
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        dutch.addFather(spec().getFoundingFather("model.foundingFather.williamPenn"));
        final FatherGUI gui = new FatherGUI(game, dutch);
        final ScriptPrompter keys = new ScriptPrompter();
        gui.prompter = keys;
        final List<String> log = new ArrayList<>();
        gui.congress = hallSeam(keys, log, true);
        gui.noteFathers();                                 // Penn: known
        assertFalse(gui.joinsDue());
        final FoundingFather minuit = spec().getFoundingFather(
            "model.foundingFather.peterMinuit");
        dutch.addFather(minuit);
        dutch.addHistory(new HistoryEvent(game.getTurn(),
            HistoryEvent.HistoryEventType.FOUNDING_FATHER, dutch));
        assertTrue(gui.joinsDue());
        final List<FoundingFather> chosen = new ArrayList<>();
        gui.showChooseFoundingFatherDialog(fathers(spec(), "adamSmith", "laSalle",
            "paulRevere", "pocahontas", "williamPenn"), chosen::add);
        final ModelMessage emigrate = new ModelMessage(
            ModelMessage.MessageType.UNIT_ADDED, "model.player.emigrate", dutch, dutch);
        final ModelMessage joined = new ModelMessage(
            ModelMessage.MessageType.SONS_OF_LIBERTY, ClassicCongress.JOINED_MESSAGE,
            dutch, dutch);
        final ModelMessage price = new ModelMessage(
            ModelMessage.MessageType.MARKET_PRICES, "model.market.priceIncrease",
            dutch, dutch);
        keys.then("X").then("X").then("X").then("ENTER");
        gui.showReportTurnPanel(List.of(emigrate, joined, price));
        final List<String> ids = new ArrayList<>();
        for (ClassicAdvisorBox.Request r : keys.boxes) ids.add(r.id);
        assertEquals(Arrays.asList(ClassicCongress.SECTION,
            "message model.market.priceIncrease", "message model.player.emigrate"), ids);
        assertEquals(ClassicCongress.FREEDOM_AFTER_TURN_MS, keys.boxes.get(0).openDelayMs);
        assertEquals(List.of("1:hall 2 [21]"), log);       // after @FREEDOM, Penn in it
        assertTrue(chosen.isEmpty());                      // withheld this turn
        assertFalse(gui.joinsDue());
        assertFalse(gui.holdTurnStart());
        // The next turn: no join again.
        keys.boxes.clear();
        keys.script.clear();
        keys.then("X");
        gui.showReportTurnPanel(List.of(price));
        assertEquals(1, keys.boxes.size());
        assertEquals(1, log.size());
        // A second join: the hall has Minuit and Penn; without the hall
        // his page alone (when the pack has it).
        keys.boxes.clear();
        keys.script.clear();
        gui.congress = hallSeam(keys, log, false);
        dutch.addFather(spec().getFoundingFather("model.foundingFather.adamSmith"));
        keys.then("X").then("X");
        gui.showModelMessages(List.of(joined));
        assertEquals("1:hall 0 [2, 21]", log.get(1));
        assertEquals(ClassicCongress.SECTION, keys.boxes.get(0).id);
        assertEquals(ClassicCongress.FREEDOM_AFTER_TURN_MS, keys.boxes.get(0).openDelayMs);
        if (keys.boxes.size() > 1) {
            assertEquals("pedia model.foundingFather.adamSmith", keys.boxes.get(1).id);
            assertNotNull(keys.boxes.get(1).picture);
            assertEquals(ClassicCongress.PAGE_AFTER_BLACK_MS, keys.boxes.get(1).chainMs);
        }
        // Without notices: the turn flow's hold posts it, holds, and the
        // year flips after it.
        keys.boxes.clear();
        keys.script.clear();
        gui.congress = hallSeam(keys, log, true);
        dutch.addFather(spec().getFoundingFather("model.foundingFather.laSalle"));
        keys.then("X");
        SwingUtilities.invokeAndWait(() -> {
                assertTrue(gui.holdTurnStart());
                assertTrue(gui.holdTurnStart());          // posted once
            });
        SwingUtilities.invokeAndWait(() -> { });          // the posted sequence
        assertEquals(1, keys.boxes.size());
        assertEquals("1:hall 9 [0, 2, 21]", log.get(2));
        assertFalse(gui.holdTurnStart());
    }

    /**
     * D8b's menu part: COLONIPAEDIE, Gruendervaeter shows the types' list,
     * Enter a type's fathers, Enter a father's page; a key on the page
     * brings his list back with the bar on him (a little later), Escape
     * goes back to the types (the bar on that type), Escape again to the
     * map.  A father's id opens his page alone; another part of the
     * Colonopedia is still nothing.
     */
    public void testThePediaMenu() throws Exception {
        final ClassicText t = texts("testThePediaMenu");
        if (t == null || ClassicPedia.load(ClassicPackFiles.runtime()) == null) return;
        final Game game = getStandardGame();
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final FatherGUI gui = new FatherGUI(game, dutch);
        final ScriptPrompter keys = new ScriptPrompter();
        gui.prompter = keys;
        keys.then("DOWN", "ENTER")                         // Erkundungs- Berater
            .then("DOWN", "DOWN", "ENTER")                 // the third explorer
            .then("X")                                     // his page
            .then("ESC")                                   // his list: back
            .then("ESC");                                  // the types: the map
        SwingUtilities.invokeAndWait(() -> assertNull(gui.showColopediaPanel("colopediaAction.fathers")));
        final List<String> ids = new ArrayList<>();
        for (ClassicAdvisorBox.Request r : keys.boxes) ids.add(r.id);
        final int third = ClassicPedia.fathersOfType(t, 1).get(2);
        final String id = ClassicFathers.IDS.get(third);
        assertEquals(Arrays.asList("PEDIA fathers", "PEDIA fathers 1", "pedia " + id,
            "PEDIA fathers 1", "PEDIA fathers"), ids);
        assertEquals(0, keys.boxes.get(0).defaultRow);
        assertEquals(ClassicFathers.PAGE_CHAIN_MS, keys.boxes.get(2).chainMs);
        assertEquals(2, keys.boxes.get(3).defaultRow);
        assertEquals(ClassicFathers.REOPEN_CHAIN_MS, keys.boxes.get(3).chainMs);
        assertEquals(1, keys.boxes.get(4).defaultRow);
        // A father's id: his page alone; another part: nothing.
        keys.boxes.clear();
        keys.then("X");
        SwingUtilities.invokeAndWait(() -> gui.showColopediaPanel("model.foundingFather.henryHudson"));
        assertEquals(1, keys.boxes.size());
        assertEquals("pedia model.foundingFather.henryHudson", keys.boxes.get(0).id);
        SwingUtilities.invokeAndWait(() -> gui.showColopediaPanel("colopediaAction.units"));
        SwingUtilities.invokeAndWait(() -> gui.showColopediaPanel("model.unit.freeColonist"));
        gui.showColopediaPanel(null);
        assertEquals(1, keys.boxes.size());
    }
}
