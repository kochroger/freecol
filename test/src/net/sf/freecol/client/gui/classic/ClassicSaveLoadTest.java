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
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;

import net.sf.freecol.FreeCol;
import net.sf.freecol.client.ClientOptions;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.control.InGameController;
import net.sf.freecol.client.control.PreGameController;
import net.sf.freecol.common.io.FreeColDirectories;
import net.sf.freecol.common.io.FreeColSavegameFile;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Turn;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.option.IntegerOption;
import net.sf.freecol.server.FreeColServer;
import net.sf.freecol.server.ServerTestHelper;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * Part SL, saving and loading in game as the original (spec
 * {@code freecol-spike-results/prep2/SL-saveload.md}): SPIEL «Spiel
 * sichern» and «Spiel laden» on FreeCol's real client controller and a
 * real server game, pointed at temporary save folders -- the save box's
 * rows and its row's file, the load box's empty, broken and good rows,
 * a save's round trip through the server (units, colony, the game's
 * options and the client's travel with it; the save folder never moves),
 * the autosave rows 9 and 10 (rule H1, Autom. Sichern off, a new game's
 * 1492), the title's load page, and FreeCol's save-folder move pinned
 * out of the Classic UI.  With the pack and {@code -Dclassic.clips} the
 * golden frames: the save box over {@code opening_054}, the load box over
 * {@code opening_055} (copied to {@code recordings/start-sequence}),
 * @LOADGOOD over clip005 #0 and clip006 #243, and the title's load page
 * over clip006 #0, 0 px off apart from the original's mouse arrow.
 */
public class ClassicSaveLoadTest extends FreeColTestCase {

    // The rig: temporary save folders, a client of a server game.

    private static final String[] DIR_FIELDS = {
        "saveDirectory", "autosaveDirectory", "savegameFile", "clientOptionsFile"
    };

    private final Object[] savedDirs = new Object[DIR_FIELDS.length];
    private boolean savedClassic;
    private File root, saves, autosaves;

    private static Field dirField(String name) throws Exception {
        final Field f = FreeColDirectories.class.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.savedClassic = FreeCol.getClassic();
        for (int i = 0; i < DIR_FIELDS.length; i++) {
            this.savedDirs[i] = dirField(DIR_FIELDS[i]).get(null);
        }
        this.root = Files.createTempDirectory("sl-load-").toFile();
        this.saves = new File(this.root, "save");
        this.autosaves = new File(this.saves, "autosave");
        assertTrue(this.autosaves.mkdirs());
        dirField("saveDirectory").set(null, this.saves);
        dirField("autosaveDirectory").set(null, this.autosaves);
        dirField("savegameFile").set(null, null);
        dirField("clientOptionsFile").set(null, new File(this.root, "no-options.xml"));
        FreeCol.setClassic(true);
    }

    @Override
    protected void tearDown() throws Exception {
        FreeCol.setClassic(this.savedClassic);
        ServerTestHelper.stopServerGame();
        for (int i = 0; i < DIR_FIELDS.length; i++) {
            dirField(DIR_FIELDS[i]).set(null, this.savedDirs[i]);
        }
        ClassicSaveSlotsTest.removeTree(this.root);   // this test's own files only
        super.tearDown();
    }

    /** The boxes asked, each answered from a queue (a notice: 0; none left: Escape). */
    static final class Answers implements ClassicGUI.Prompter {

        final List<ClassicAdvisorBox.Request> boxes = new ArrayList<>();
        final Deque<Integer> rows = new ArrayDeque<>();

        Answers(Integer... r) {
            this.rows.addAll(Arrays.asList(r));
        }

        @Override
        public int ask(ClassicAdvisorBox.Request r) {
            this.boxes.add(r);
            if (r.isNotice()) return 0;
            return (this.rows.isEmpty()) ? ClassicAdvisorBox.Bar.DISMISSED : this.rows.poll();
        }

        List<String> ids() {
            final List<String> out = new ArrayList<>();
            for (ClassicAdvisorBox.Request r : this.boxes) out.add(r.id);
            return out;
        }

        ClassicAdvisorBox.Request last() {
            return this.boxes.get(this.boxes.size() - 1);
        }
    }

    /** The classic GUI with the switch to a save noted, not made (no window here). */
    static final class SlGUI extends ClassicGUI {

        final List<File> switched = new ArrayList<>();

        SlGUI(FreeColClient fcc) {
            super(fcc);
        }

        @Override
        void switchToSave(File file) {
            this.switched.add(file);
        }
    }

    private static void set(FreeColClient fcc, String name, Object value) throws Exception {
        final Field f = FreeColClient.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(fcc, value);
    }

    /** A client object without its constructor (as ClassicKeepSavesTest). */
    private static FreeColClient bareClient() throws Exception {
        final Class<?> uc = Class.forName("sun.misc.Unsafe");
        final Field theUnsafe = uc.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        return (FreeColClient)uc.getMethod("allocateInstance", Class.class)
            .invoke(theUnsafe.get(null), FreeColClient.class);
    }

    /** FreeCol's own client options, Autom. Sichern on (every turn). */
    private static ClientOptions baseOptions() {
        final ClientOptions co = new ClientOptions();
        co.load(FreeColDirectories.getBaseClientOptionsFile());
        co.fixClientOptions();
        co.getOption(ClientOptions.AUTOSAVE_PERIOD, IntegerOption.class).setValue(1);
        return co;
    }

    /** The GUI of the client below. */
    private SlGUI gui;

    /**
     * A client of a running server game: FreeCol's real controllers, the
     * classic GUI, our Dutch player (the admin of a single player game),
     * a colony of three.
     */
    private FreeColClient gameClient(ClientOptions co) throws Exception {
        ServerTestHelper.stopServerGame();
        final Game game = ServerTestHelper.startServerGame(getTestMap());
        final ServerPlayer dutch = getServerPlayer(game, "model.nation.dutch");
        dutch.setAI(false);
        final Field admin = Player.class.getDeclaredField("admin");
        admin.setAccessible(true);
        admin.set(dutch, Boolean.TRUE);
        final FreeColClient fcc = bareClient();
        this.gui = new SlGUI(fcc);
        set(fcc, "gui", this.gui);
        set(fcc, "clientOptions", co);
        set(fcc, "freeColServer", ServerTestHelper.getServer());
        set(fcc, "inGameController", new InGameController(fcc));
        set(fcc, "preGameController", new PreGameController(fcc));
        fcc.setGame(game);
        fcc.setMyPlayer(dutch);
        game.setCurrentPlayer(dutch);
        createStandardColony(3);
        return fcc;
    }

    private static List<String> names(File dir) {
        final String[] n = dir.list();
        final List<String> out = (n == null) ? new ArrayList<>()
            : new ArrayList<>(Arrays.asList(n));
        Collections.sort(out);
        return out;
    }

    /** Name -> size and time of the files in a folder. */
    private static Map<String, String> census(File dir) {
        final Map<String, String> out = new TreeMap<>();
        final File[] kids = dir.listFiles();
        if (kids != null) {
            for (File k : kids) {
                if (k.isFile()) out.put(k.getName(), k.length() + "@" + k.lastModified());
            }
        }
        return out;
    }

    private static String entry(File save, String name) throws IOException {
        try (ZipFile z = new ZipFile(save)) {
            return new String(z.getInputStream(z.getEntry(name)).readAllBytes(),
                              StandardCharsets.UTF_8);
        }
    }

    /** The plain text of a box. */
    private static String text(ClassicAdvisorBox.Request r) {
        return r.plainText();
    }

    /**
     * The saves' owner as a row shows it: FreeCol's name (the test's user
     * name), its '_' a space (FONTTINY has none).
     */
    private static String owner() {
        return ClassicAdvisorBox.plain(ClassicAdvisorBox.literal(FreeCol.getName()));
    }


    // The menu rows

    /**
     * SPIEL rows 4 and 5 are the Classic UI's own actions (no FreeCol
     * saveAction/openAction any more, which ask for a file and move the
     * save folder), usable only with a game view.
     */
    public void testTheMenuRowsHaveTheirActions() throws Exception {
        final ClassicGUI none = new ClassicGUI(null);
        final javax.swing.Action save = none.classicAction(ClassicMenuModel.SAVE_GAME);
        final javax.swing.Action load = none.classicAction(ClassicMenuModel.LOAD_GAME);
        assertNotNull(save);
        assertNotNull(load);
        assertNotSame(save, load);
        assertSame(save, none.classicAction(ClassicMenuModel.SAVE_GAME));
        assertFalse(save.isEnabled());
        assertFalse(load.isEnabled());
        assertNull(none.classicAction("saveAction"));
        assertNull(none.classicAction("openAction"));
        // A game, but no game view (no map yet): still not usable.
        final FreeColClient fcc = gameClient(baseOptions());
        assertTrue(fcc.canSaveCurrentGame());
        assertFalse(this.gui.classicAction(ClassicMenuModel.SAVE_GAME).isEnabled());
        assertFalse(this.gui.classicAction(ClassicMenuModel.LOAD_GAME).isEnabled());
        assertFalse(this.gui.canSaveNow());
        assertFalse(this.gui.canLoadNow());
    }


    // Spiel sichern

    /**
     * The save box (V E2): GAME.TXT's @SAVEGAME, 8 rows, the bar on row 1,
     * "(EMPTY)" rows, Escape no row; row 3 writes {@code save/COLONY02.fsg}
     * through the real server and says @SAVEGOOD with "COLONY02.SAV" and
     * the row's new label; an occupied row is saved over without a
     * question (D-SL6); Escape saves nothing; the save folder stays.
     */
    public void testSaveIntoARow() throws Exception {
        final FreeColClient fcc = gameClient(baseOptions());
        Answers a = new Answers(2);
        this.gui.prompter = a;
        assertEquals(2, this.gui.showSaveBox());
        assertEquals(Arrays.asList(ClassicSaveSlots.SAVE_SECTION, ClassicSaveSlots.SAVE_GOOD),
                     a.ids());
        final ClassicAdvisorBox.Request box = a.boxes.get(0);
        assertEquals(8, box.rows.size());
        assertEquals(0, box.defaultRow);
        assertEquals(ClassicAdvisorBox.Bar.DISMISSED, box.escapeAnswer());
        assertTrue(box.outsideCancels);
        assertEquals(190, box.width);
        for (String r : box.plainRows()) assertEquals(ClassicSaveSlots.EMPTY, r);
        final File row3 = new File(this.saves, "COLONY02.fsg");
        assertTrue(row3.isFile());
        assertEquals(Arrays.asList("COLONY02.fsg", "autosave"), names(this.saves));
        assertTrue(text(a.last()), text(a.last()).contains("COLONY02.SAV"));
        assertTrue(text(a.last()), text(a.last()).contains(owner()));
        assertEquals(this.saves, FreeColDirectories.getSaveDirectory());
        assertNull(FreeColDirectories.getSavegameFile());

        // The box again: row 3 shows the save; saved over without a question.
        a = new Answers(2);
        this.gui.prompter = a;
        assertEquals(2, this.gui.showSaveBox());
        assertEquals(Arrays.asList(ClassicSaveSlots.SAVE_SECTION, ClassicSaveSlots.SAVE_GOOD),
                     a.ids());
        assertFalse(ClassicSaveSlots.EMPTY.equals(a.boxes.get(0).plainRows()[2]));
        assertEquals(ClassicSaveSlots.EMPTY, a.boxes.get(0).plainRows()[3]);

        // Escape: nothing.
        final Map<String, String> before = census(this.saves);
        a = new Answers();
        this.gui.prompter = a;
        assertEquals(-1, this.gui.showSaveBox());
        assertEquals(Arrays.asList(ClassicSaveSlots.SAVE_SECTION), a.ids());
        assertEquals(before, census(this.saves));
        assertNotNull(fcc.getFreeColServer());
    }

    /**
     * @SAVEERROR, and the row's old save untouched: a row that cannot be
     * replaced (a folder in its place; on Windows also a read-only file,
     * acceptance A9) and a game without a server.
     */
    public void testAFailedSaveKeepsTheRow() throws Exception {
        gameClient(baseOptions());
        // A folder with a file where row 5's save goes: no move can replace it.
        final File blocker = new File(this.saves, "COLONY04.fsg");
        assertTrue(blocker.mkdirs());
        Files.write(new File(blocker, "keep.txt").toPath(), "x".getBytes(StandardCharsets.UTF_8));
        Answers a = new Answers(4);
        this.gui.prompter = a;
        assertEquals(-1, this.gui.showSaveBox());
        assertEquals(Arrays.asList(ClassicSaveSlots.SAVE_SECTION, ClassicSaveSlots.SAVE_ERROR),
                     a.ids());
        assertTrue(text(a.last()), text(a.last()).contains("COLONY04.SAV"));
        assertTrue(new File(blocker, "keep.txt").isFile());
        assertEquals(Arrays.asList("COLONY04.fsg", "autosave"), names(this.saves));

        // A read-only row 6 (A9).
        a = new Answers(5);
        this.gui.prompter = a;
        assertEquals(5, this.gui.showSaveBox());
        final File row6 = new File(this.saves, "COLONY05.fsg");
        final byte[] old = Files.readAllBytes(row6.toPath());
        assertTrue(row6.setReadOnly());
        a = new Answers(5);
        this.gui.prompter = a;
        final int got = this.gui.showSaveBox();
        if (got < 0) {
            assertEquals(ClassicSaveSlots.SAVE_ERROR, a.last().id);
            assertTrue(Arrays.equals(old, Files.readAllBytes(row6.toPath())));
        } else {
            System.err.println(getClass().getSimpleName()
                + ": this file system replaces a read-only file");
        }
        assertTrue(row6.setWritable(true));
        assertEquals(Arrays.asList("COLONY04.fsg", "COLONY05.fsg", "autosave"),
                     names(this.saves));

        // No server: the error, nothing written.
        final SlGUI lone = new SlGUI(null);
        a = new Answers(0);
        lone.prompter = a;
        assertEquals(-1, lone.showSaveBox());
        assertEquals(ClassicSaveSlots.SAVE_ERROR, a.last().id);
        assertFalse(new File(this.saves, "COLONY00.fsg").exists());
    }

    /**
     * A save's round trip through the real server: the colony, its
     * colonists, our units, the game's own options (the original's eight
     * rows, Player.classicOptions) and the client's options (in the save's
     * {@code client-options.xml}) come back from row 3; the save folder
     * never moves (pitfall 1).
     */
    public void testTheRoundTrip() throws Exception {
        final ClientOptions co = baseOptions();
        final FreeColClient fcc = gameClient(co);
        final Game game = fcc.getGame();
        final ServerPlayer dutch = getServerPlayer(game, "model.nation.dutch");
        dutch.setClassicOptions(0x53);
        final Colony colony = dutch.getColonyList().get(0);
        final String colonyName = colony.getName();
        final int colonists = colony.getUnitCount();
        final int units = dutch.getUnitCount();
        co.getOption(ClientOptions.AUTOSAVE_PERIOD, IntegerOption.class).setValue(0);
        this.gui.prompter = new Answers(2);
        assertEquals(2, this.gui.showSaveBox());
        final File row3 = new File(this.saves, "COLONY02.fsg");
        assertTrue(entry(row3, FreeColSavegameFile.CLIENT_OPTIONS),
            entry(row3, FreeColSavegameFile.CLIENT_OPTIONS)
                .matches("(?s).*model\\.option\\.autosavePeriod\"[^>]*value=\"0\".*"));
        ServerTestHelper.stopServerGame();

        final FreeColServer server = ServerTestHelper.startServer(row3, false, true);
        final Game loaded = server.getGame();
        final Player back = loaded.getPlayerByNationId("model.nation.dutch");
        assertNotNull(back);
        assertEquals(0x53, back.getClassicOptions());
        assertEquals(units, back.getUnitCount());
        assertEquals(1, back.getColonyList().size());
        final Colony c = back.getColonyList().get(0);
        assertEquals(colonyName, c.getName());
        assertEquals(colonists, c.getUnitCount());
        assertEquals(game.getSpecification().getDifficultyLevel(),
                     loaded.getSpecification().getDifficultyLevel());
        assertEquals(this.saves, FreeColDirectories.getSaveDirectory());
        assertEquals(this.autosaves, FreeColDirectories.getAutosaveDirectory());
        assertNull(FreeColDirectories.getSavegameFile());
        // And the loaded game saves into its row again (the load let go
        // of the file; on Windows an open file cannot be replaced).
        final Map<String, String> was = census(this.saves);
        ClassicSaveSlots.writeAtomic(row3, f -> server.saveGame(f, co, (Unit) null));
        assertTrue(row3.isFile());
        assertEquals(was.keySet(), census(this.saves).keySet());
    }


    // Spiel laden

    /**
     * The load box (V E3): @LOADGAME, 10 rows (8 save rows, the 2 autosave
     * rows), the bar on row 1; an "(EMPTY)" row does nothing and the box
     * comes back with the bar on it (D-SL7); a row that is no save says
     * @LOADNOT and the game goes on; a good row @LOADGOOD with
     * "COLONY02.SAV", then the switch to that file; row 10 is FreeCol's
     * last-turn autosave; Escape loads nothing; the save folder stays.
     */
    public void testTheLoadBox() throws Exception {
        final FreeColClient fcc = gameClient(baseOptions());
        final FreeColServer server = fcc.getFreeColServer();
        final ClassicSaveSlots slots = ClassicSaveSlots.of(fcc.getClientOptions());
        ClassicSaveSlots.writeAtomic(slots.slotFile(2),
            f -> server.saveGame(f, fcc.getClientOptions(), (Unit) null));
        ClassicSaveSlots.writeAtomic(slots.slotFile(9),
            f -> server.saveGame(f, fcc.getClientOptions(), (Unit) null));
        Files.write(slots.slotFile(4).toPath(), "no save".getBytes(StandardCharsets.UTF_8));
        assertEquals(new File(this.autosaves, "Autosave-last-turn.fsg"), slots.slotFile(9));

        Answers a = new Answers(1, 2);
        this.gui.prompter = a;
        assertTrue(this.gui.showLoadBox());
        assertEquals(Arrays.asList(ClassicSaveSlots.LOAD_SECTION, ClassicSaveSlots.LOAD_SECTION,
                                   ClassicSaveSlots.LOAD_GOOD), a.ids());
        final ClassicAdvisorBox.Request box = a.boxes.get(0);
        assertEquals(10, box.rows.size());
        assertEquals(0, box.defaultRow);
        assertEquals(1, a.boxes.get(1).defaultRow);      // the bar stays on the empty row
        assertEquals(ClassicAdvisorBox.Bar.DISMISSED, box.escapeAnswer());
        final String[] rows = box.plainRows();
        assertEquals(ClassicSaveSlots.EMPTY, rows[1]);
        assertTrue(rows[2], rows[2].contains(owner()));
        assertTrue(rows[9], rows[9].contains(owner()));
        assertEquals(ClassicSaveSlots.EMPTY, rows[8]);
        assertTrue(text(a.last()), text(a.last()).contains("COLONY02.SAV"));
        assertEquals(Arrays.asList(slots.slotFile(2)), this.gui.switched);

        a = new Answers(4);
        this.gui.prompter = a;
        assertFalse(this.gui.showLoadBox());
        assertEquals(Arrays.asList(ClassicSaveSlots.LOAD_SECTION, ClassicSaveSlots.LOAD_NOT),
                     a.ids());
        assertTrue(text(a.last()), text(a.last()).contains("COLONY04.SAV"));
        assertEquals(1, this.gui.switched.size());

        a = new Answers();
        this.gui.prompter = a;
        assertFalse(this.gui.showLoadBox());
        assertEquals(Arrays.asList(ClassicSaveSlots.LOAD_SECTION), a.ids());

        a = new Answers(9);
        this.gui.prompter = a;
        assertTrue(this.gui.showLoadBox());
        assertEquals(slots.slotFile(9), this.gui.switched.get(1));
        assertTrue(text(a.last()), text(a.last()).contains("COLONY09.SAV"));
        assertEquals(this.saves, FreeColDirectories.getSaveDirectory());
        assertNull(FreeColDirectories.getSavegameFile());
    }


    /**
     * A classic load that fails says the original's @LOADERROR ("Fehler
     * beim Laden von COLONY02.SAV.", V E12; I D-SL15) on the title, where
     * the failed load lands, once, instead of FreeCol's error box; an error
     * with no load under way keeps FreeCol's box.  (Here the load fails at
     * once: the GUI has no client.)
     */
    public void testAFailedLoadSaysLoadError() throws Exception {
        final List<String> titles = Collections.synchronizedList(new ArrayList<>());
        final ClassicGUI g = new ClassicGUI(null) {
                @Override
                public net.sf.freecol.client.gui.panel.FreeColPanel showMainPanel(String msg) {
                    titles.add(String.valueOf(msg));
                    return null;
                }
            };
        final Answers a = new Answers();
        g.prompter = a;
        final File row3 = new File(this.saves, "COLONY02.fsg");
        // The load, and a passing warning while it is under way (no
        // callback back to the title): that one keeps FreeCol's box.
        javax.swing.SwingUtilities.invokeAndWait(() -> {
                g.loadSavedGame(row3);
                g.showErrorPanel("a warning", null);
            });
        assertEquals(Arrays.asList("error"), a.ids());
        for (int i = 0; i < 3; i++) javax.swing.SwingUtilities.invokeAndWait(() -> {});
        assertFalse(titles.isEmpty());
        assertTrue(titles.toString(), titles.get(0).contains("COLONY02.SAV"));
        for (String t : titles.subList(1, titles.size())) assertEquals("null", t);
        assertEquals("no error box for the load", Arrays.asList("error"), a.ids());
        // No load under way: FreeCol's box.
        javax.swing.SwingUtilities.invokeAndWait(() -> g.showErrorPanel("boom", () -> {}));
        assertEquals(Arrays.asList("error", "error"), a.ids());
        // Another file's name as the file's own.
        final ClassicSaveSlots slots = ClassicSaveSlots.of(null);
        assertEquals("COLONY02.SAV", slots.nameOf(row3));
        assertEquals("COLONY09.SAV", slots.nameOf(new File(this.autosaves,
                                                            "Autosave-last-turn.fsg")));
        assertEquals("OLD-GAME.FSG", slots.nameOf(new File(this.saves, "old-game.fsg")));
    }


    // The autosave rows

    /** InGameController.autoSaveGame, the autosave at our turn's start. */
    private static void autoSave(FreeColClient fcc) {
        fcc.getInGameController().autoSaveGame();
    }

    /** The first turn whose year is {@code year} (spring). */
    private static Turn turnOf(int year) {
        for (int n = 1; n < 1000; n++) {
            final Turn t = new Turn(n);
            if (t.getYear() == year && t.getSeason() <= 0) return t;
        }
        throw new IllegalStateException("no turn in " + year);
    }

    /**
     * The autosave rows (I, rule H1, E8; spec SL6) through FreeCol's real
     * autosave: an ordinary turn writes row 10 (the last-turn autosave, the
     * one before goes to before-last-turn) and FreeCol's numbered copy; the
     * first turn of 1500 writes row 9 instead and leaves row 10 as it was;
     * the next turn row 10 again, row 9 stays; with Autom. Sichern off
     * nothing at all is written (D-SL2, the original); a row 9 that cannot
     * be written gives the turn to row 10.
     */
    public void testTheAutosaveRows() throws Exception {
        final FreeColClient fcc = gameClient(baseOptions());
        final Game game = fcc.getGame();
        final File last = new File(this.autosaves, "Autosave-last-turn.fsg");
        final File before = new File(this.autosaves, "Autosave-before-last-turn.fsg");
        final File decade = new File(this.autosaves, "COLONY08.fsg");

        game.setTurn(turnOf(1495));
        autoSave(fcc);
        assertTrue(last.isFile());
        assertFalse(decade.exists());
        final int numbered = names(this.autosaves).size();
        assertEquals(2, numbered);                 // last-turn and the numbered 1495
        assertTrue(last.setLastModified(last.lastModified() - 120000L));
        final byte[] turn1495 = Files.readAllBytes(last.toPath());
        final long time1495 = last.lastModified();

        game.setTurn(turnOf(1500));
        assertTrue(ClassicSaveSlots.isDecadeTurn(game.getTurn()));
        autoSave(fcc);
        assertTrue(decade.isFile());
        assertEquals("row 10 kept", time1495, last.lastModified());
        assertTrue(Arrays.equals(turn1495, Files.readAllBytes(last.toPath())));
        assertFalse(before.exists());
        assertEquals(numbered + 2, names(this.autosaves).size());   // COLONY08, numbered 1500
        final ClassicSaveGames.Info nine = ClassicSaveSlots.info(decade);
        assertNotNull(nine);
        assertEquals(game.getTurn().getNumber(), nine.turn);
        assertTrue(decade.setLastModified(decade.lastModified() - 60000L));
        final long time1500 = decade.lastModified();

        game.setTurn(turnOf(1501));
        autoSave(fcc);
        assertEquals(time1500, decade.lastModified());
        assertTrue(before.isFile());
        assertTrue(Arrays.equals(turn1495, Files.readAllBytes(before.toPath())));
        assertEquals(game.getTurn().getNumber(), ClassicSaveSlots.info(last).turn);

        // Autom. Sichern off: nothing, also on a decade turn.
        fcc.getClientOptions().getOption(ClientOptions.AUTOSAVE_PERIOD, IntegerOption.class)
            .setValue(0);
        final Map<String, String> off = census(this.autosaves);
        game.setTurn(turnOf(1502));
        autoSave(fcc);
        game.setTurn(turnOf(1510));
        autoSave(fcc);
        assertEquals(off, census(this.autosaves));

        // Row 9 blocked (a folder in its place): the turn goes to row 10.
        fcc.getClientOptions().getOption(ClientOptions.AUTOSAVE_PERIOD, IntegerOption.class)
            .setValue(1);
        final File oldDecade = new File(this.autosaves, "COLONY08.fsg");
        final byte[] nineBytes = Files.readAllBytes(oldDecade.toPath());
        final File elsewhere = new File(this.root, "COLONY08.keep");
        Files.move(oldDecade.toPath(), elsewhere.toPath());
        assertTrue(oldDecade.mkdirs());
        Files.write(new File(oldDecade, "keep.txt").toPath(), "x".getBytes(StandardCharsets.UTF_8));
        game.setTurn(turnOf(1520));
        autoSave(fcc);
        assertEquals(game.getTurn().getNumber(), ClassicSaveSlots.info(last).turn);
        assertTrue(new File(oldDecade, "keep.txt").isFile());
        assertTrue(Arrays.equals(nineBytes, Files.readAllBytes(elsewhere.toPath())));
    }

    /**
     * A new game's first turn (1492) goes to row 10 (V E8, D-SL17), when
     * the turn is ours and Autom. Sichern is on; FreeCol alone saves first
     * at the second turn.
     */
    public void testANewGamesFirstTurn() throws Exception {
        final FreeColClient fcc = gameClient(baseOptions());
        final Game game = fcc.getGame();
        final File last = new File(this.autosaves, "Autosave-last-turn.fsg");
        game.setTurn(new Turn(1));
        fcc.changeClientState(true);
        // Not our turn: nothing.
        final Player other = getServerPlayer(game, "model.nation.french");
        game.setCurrentPlayer(other);
        this.gui.autosaveFirstTurn();
        assertFalse(last.exists());
        // Ours: row 10 says 1492.
        game.setCurrentPlayer(fcc.getMyPlayer());
        this.gui.autosaveFirstTurn();
        assertTrue(last.isFile());
        final ClassicSaveGames.Info i = ClassicSaveSlots.info(last);
        assertEquals(1, i.turn);
        // Autom. Sichern off: none.
        ClassicSaveSlotsTest.removeTree(this.autosaves);
        assertTrue(this.autosaves.mkdirs());
        fcc.getClientOptions().getOption(ClientOptions.AUTOSAVE_PERIOD, IntegerOption.class)
            .setValue(0);
        this.gui.autosaveFirstTurn();
        assertEquals(new ArrayList<String>(), names(this.autosaves));
        // A GUI without a client does nothing.
        new ClassicGUI(null).autosaveFirstTurn();
    }

    /**
     * Pitfall 1 pinned: FreeCol moves its save folder only in
     * {@code FreeColDirectories.setSavegameFile}, called by a save on the
     * command line (FreeCol.java) and by FreeCol's own in-game load and
     * save (InGameController.loadGame, saveGame); the Classic UI calls
     * none of them.  A new call makes this test fail, so that it is checked
     * against the save rows first.
     */
    public void testTheSaveFolderNeverMoves() throws Exception {
        final File src = new File("src/net/sf/freecol");
        if (!src.isDirectory()) {
            System.err.println(getClass().getSimpleName() + ": no src folder, not checked");
            return;
        }
        final Pattern p = Pattern.compile("\\bsetSavegameFile\\(");
        final Map<String, Integer> found = new TreeMap<>();
        scan(src, src, p, found);
        final Map<String, Integer> known = new TreeMap<>();
        known.put("FreeCol.java", 2);                          // the command line
        known.put("client/control/InGameController.java", 2);  // FreeCol's own load and save
        known.put("common/io/FreeColDirectories.java", 1);     // the method itself
        known.put("client/FreeColClient.java", 1);             // ResourceManager's, not the folder
        known.put("common/resources/ResourceManager.java", 1);   // its own, not the folder
        assertEquals(known, found);
        // The Classic UI never asks FreeCol's controller to load or save.
        final Pattern igc = Pattern.compile(
            "(getInGameController\\(\\)|igc\\(\\))\\s*\\.\\s*(loadGame|saveGame)\\(");
        final Map<String, Integer> calls = new TreeMap<>();
        scan(src, new File(src, "client/gui/classic"), igc, calls);
        assertEquals(new TreeMap<String, Integer>(), calls);
    }

    private static void scan(File base, File dir, Pattern p, Map<String, Integer> found)
        throws IOException {
        final File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File f : kids) {
            if (f.isDirectory()) {
                scan(base, f, p, found);
            } else if (f.getName().endsWith(".java")) {
                int n = 0;
                for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                    final String t = line.trim();
                    if (t.startsWith("//") || t.startsWith("*") || t.startsWith("import ")) continue;
                    final Matcher m = p.matcher(line);
                    while (m.find()) n++;
                }
                if (n > 0) {
                    found.put(base.toPath().relativize(f.toPath()).toString()
                              .replace('\\', '/'), n);
                }
            }
        }
    }


    // The title's load page

    private static void mouse(ClassicMainMenuPanel p, int id, Point at) {
        final MouseEvent e = new MouseEvent(p, id, System.currentTimeMillis(),
            (id == MouseEvent.MOUSE_PRESSED) ? InputEvent.BUTTON1_DOWN_MASK : 0,
            at.x, at.y, 1, false, MouseEvent.BUTTON1);
        for (java.awt.event.MouseListener l : p.getMouseListeners()) {
            if (id == MouseEvent.MOUSE_PRESSED) l.mousePressed(e);
            if (id == MouseEvent.MOUSE_RELEASED) l.mouseReleased(e);
        }
    }

    private static void click(ClassicMainMenuPanel p, Point at) {
        mouse(p, MouseEvent.MOUSE_PRESSED, at);
        mouse(p, MouseEvent.MOUSE_RELEASED, at);
    }

    private static Point centre(Rectangle r) {
        return new Point(r.x + r.width / 2, r.y + r.height / 2);
    }

    private static void key(ClassicMainMenuPanel p, String binding) {
        p.getActionMap().get(binding).actionPerformed(null);
    }

    /**
     * The title's «Spiel LADEN» (spec SL7): the load page with the same ten
     * rows (scale 1 before the first paint: the events are the canvas's
     * pixels); the keys move one row, no wrap; an "(EMPTY)" row does
     * nothing; a broken row @LOADNOT and back; a good row @LOADGOOD, its
     * dismissal loads that file over black; Escape to the title; PageDown
     * the page of all saves, Escape back to the page.  Without any save
     * the page shows ten "(EMPTY)" rows.
     */
    public void testTheTitlesLoadPage() throws Exception {
        final List<File> loaded = new ArrayList<>();
        final ClassicMainMenuPanel p = new ClassicMainMenuPanel(
            new ClassicMainMenuPanel.Actions() {
                @Override public void newWorld(ClassicGUI.NewWorldSetup s) {}
                @Override public void loadGame(File file) { loaded.add(file); }
                @Override public void hallOfFame() {}
                @Override public void quit() {}
            });
        final ClassicSaveSlots slots = new ClassicSaveSlots(this.saves, this.autosaves,
                                                            "Autosave-last-turn.fsg");
        p.useSaveSlots(() -> slots);
        p.showTitle(null);
        final Point loadItem = centre(ClassicMenuBox.rowHitRect(
            ClassicMainMenuPanel.titleBounds(5), 1, ClassicMainMenuPanel.ITEM_LOAD));
        click(p, loadItem);
        assertEquals(ClassicMainMenuPanel.Mode.LOAD, p.mode());
        assertEquals(10, p.pageLabels().size());
        assertEquals(Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
                     ClassicSaveSlots.emptyRows(p.pageLabels()));
        assertEquals(0, p.pageRow());
        key(p, "classic_menuEscape");
        assertEquals(ClassicMainMenuPanel.Mode.TITLE, p.mode());

        ClassicSaveSlotsTest.writeSave(slots.slotFile(2), "Dago", 9,
            "model.difficulty.veryEasy", "model.nation.dutch");
        ClassicSaveSlotsTest.writeSave(slots.slotFile(9), "Raleigh", 1,
            "model.difficulty.veryEasy", "model.nation.english");
        Files.write(slots.slotFile(5).toPath(), "no save".getBytes(StandardCharsets.UTF_8));
        click(p, loadItem);
        assertEquals(ClassicMainMenuPanel.Mode.LOAD, p.mode());
        assertEquals(Arrays.asList(0, 1, 3, 4, 6, 7, 8),
                     ClassicSaveSlots.emptyRows(p.pageLabels()));
        assertTrue(p.pageLabels().get(2).contains("Dago"));
        // Keys: one row each, no wrap.
        key(p, "classic_menuUp");
        assertEquals(0, p.pageRow());
        key(p, "classic_menuDown");
        key(p, "classic_menuDown");
        assertEquals(2, p.pageRow());
        key(p, "classic_menuEnd");
        assertEquals(9, p.pageRow());
        key(p, "classic_menuDown");
        assertEquals(9, p.pageRow());
        key(p, "classic_menuHome");
        assertEquals(0, p.pageRow());
        // An empty row: nothing.
        click(p, centre(ClassicMainMenuPanel.pageRowHitRect(1)));
        assertEquals(ClassicMainMenuPanel.Mode.LOAD, p.mode());
        assertEquals(1, p.pageRow());
        key(p, "classic_menuEnter");
        assertEquals(ClassicMainMenuPanel.Mode.LOAD, p.mode());
        assertTrue(loaded.isEmpty());
        // A broken row: @LOADNOT, then the page again.
        click(p, centre(ClassicMainMenuPanel.pageRowHitRect(5)));
        assertEquals(ClassicMainMenuPanel.Mode.NOTICE, p.mode());
        assertTrue(String.join(" ", p.noticeLines()).contains("COLONY05.SAV"));
        click(p, new Point(10, 10));
        assertEquals(ClassicMainMenuPanel.Mode.LOAD, p.mode());
        assertEquals(5, p.pageRow());
        assertTrue(loaded.isEmpty());
        // PageDown: every save; Escape back to the page.
        key(p, "classic_menuPageDown");
        assertEquals(ClassicMainMenuPanel.Mode.ARCHIVE, p.mode());
        key(p, "classic_menuEscape");
        assertEquals(ClassicMainMenuPanel.Mode.LOAD, p.mode());
        // A good row: @LOADGOOD over the page, its dismissal loads.
        click(p, centre(ClassicMainMenuPanel.pageRowHitRect(2)));
        assertEquals(ClassicMainMenuPanel.Mode.NOTICE, p.mode());
        assertTrue(String.join(" ", p.noticeLines()).contains("COLONY02.SAV"));
        assertTrue(loaded.isEmpty());
        key(p, "classic_menuEnter");
        assertEquals(Arrays.asList(slots.slotFile(2)), loaded);
        assertEquals(ClassicMainMenuPanel.Mode.PASSIVE, p.mode());
        final BufferedImage black = p.stillShown();
        assertNotNull(black);
        assertEquals(0, black.getRGB(160, 100) & 0xFFFFFF);
        // The engine's passive title at the game's start keeps the black.
        p.showPassive();
        assertEquals(ClassicMainMenuPanel.Mode.PASSIVE, p.mode());
        assertSame(black, p.stillShown());
        // An in-game load's picture (the map as it was) the same way.
        final BufferedImage map = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
        p.showPassive(map);
        p.showPassive();
        assertSame(map, p.stillShown());
        // The title again: no picture held.
        p.showTitle(null);
        assertNull(p.stillShown());
        p.showPassive();
        assertNull(p.stillShown());
        // Row 10 from the page.
        p.showTitle(null);
        click(p, loadItem);
        key(p, "classic_menuEnd");
        key(p, "classic_menuEnter");
        assertEquals(ClassicMainMenuPanel.Mode.NOTICE, p.mode());
        assertTrue(String.join(" ", p.noticeLines()).contains("COLONY09.SAV"));
        click(p, new Point(10, 10));
        assertEquals(slots.slotFile(9), loaded.get(1));
    }


    // The golden frames (the pack and -Dclassic.clips)

    /** The capture's pixels against ours inside {@code r}: the points that differ. */
    private static List<Point> diff(BufferedImage capture, BufferedImage ours, Rectangle r) {
        final List<Point> out = new ArrayList<>();
        for (int y = r.y; y < r.y + r.height; y++) {
            for (int x = r.x; x < r.x + r.width; x++) {
                if ((capture.getRGB(x, y) & 0xFFFFFF) != (ours.getRGB(x, y) & 0xFFFFFF)) {
                    out.add(new Point(x, y));
                }
            }
        }
        return out;
    }

    /** The bounds of some points, or null. */
    private static Rectangle bounds(List<Point> ps) {
        Rectangle b = null;
        for (Point q : ps) {
            if (b == null) b = new Rectangle(q.x, q.y, 1, 1); else b.add(new Rectangle(q.x, q.y, 1, 1));
        }
        return b;
    }

    private static BufferedImage copy(BufferedImage src) {
        final BufferedImage c = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = c.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return c;
    }

    /** The recordings folder, or null (the golden checks are skipped). */
    private File clips(String what) {
        final String clips = System.getProperty(ClassicTerrainGoldenTest.CLIPS_PROPERTY);
        final File dir = (clips == null || clips.isEmpty()) ? null : new File(clips);
        if (dir == null || !dir.isDirectory() || ClassicPackFiles.runtime() == null) {
            System.err.println(getClass().getSimpleName() + ": " + what
                + " skipped, no recordings (-D" + ClassicTerrainGoldenTest.CLIPS_PROPERTY
                + ") or no pack");
            return null;
        }
        return dir;
    }

    /** A box of part SL drawn over a capture: the pixels off inside its box. */
    private static List<Point> boxOver(BufferedImage capture, ClassicAdvisorBox.Request r,
                                       int bar, Rectangle expected) {
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final ClassicFont tiny = pack.font(ClassicFont.TINY);
        final ClassicAdvisorBox.Layout l = ClassicAdvisorBox.layout(r, tiny, null);
        assertNotNull(l);
        assertEquals(r.id, expected, l.box);
        final BufferedImage ours = copy(capture);
        final Graphics2D g = ours.createGraphics();
        ClassicAdvisorBox.paint(g, l, bar, pack.image(ClassicMenuBar.WOOD_KEY), tiny);
        g.dispose();
        return diff(capture, ours, l.box);
    }

    /**
     * The save box over {@code opening_054} (V E2): box (62,56,196,88),
     * the prompt, row 1 "Entdecker Dago der Holl., Herbst 1729" under the
     * bar, seven "(EMPTY)" rows -- 0 px off; and the load box over
     * {@code opening_055} (V E3): (62,48,196,104), rows 9 and 10 the two
     * autosaves -- 0 px off.
     */
    public void testTheBoxesAgainstTheCaptures() throws Exception {
        final File dir = clips("the save and load boxes");
        if (dir == null) return;
        final File s054 = new File(dir, "start-sequence/opening_054.png");
        final File s055 = new File(dir, "start-sequence/opening_055.png");
        if (!s054.isFile() || !s055.isFile()) {
            System.err.println(getClass().getSimpleName()
                + ": copy opening_054/055.png to recordings/start-sequence, skipped");
            return;
        }
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        assertNotNull(t);
        final List<String> save = new ArrayList<>(Collections.nCopies(8, ClassicSaveSlots.EMPTY));
        save.set(0, "Entdecker Dago der Holl., Herbst 1729");
        final List<Point> off054 = boxOver(ImageIO.read(s054),
            ClassicSaveSlots.boxRequest(t, ClassicSaveSlots.SAVE_SECTION, save, 0, ""),
            0, new Rectangle(62, 56, 196, 88));
        final List<String> load = new ArrayList<>(Collections.nCopies(10, ClassicSaveSlots.EMPTY));
        load.set(0, "Entdecker Dago der Holl., Herbst 1729");
        load.set(8, "Entdecker Dago der Holl., Frühjahr 1720");
        load.set(9, "Entdecker Walter Raleigh der Engl., Frühjahr 1492");
        final List<Point> off055 = boxOver(ImageIO.read(s055),
            ClassicSaveSlots.boxRequest(t, ClassicSaveSlots.LOAD_SECTION, load, 0, ""),
            0, new Rectangle(62, 48, 196, 104));
        System.out.println(getClass().getSimpleName() + ": 054 save box " + off054.size()
            + " px off, 055 load box " + off055.size() + " px off");
        assertEquals("054 off at " + bounds(off054), 0, off054.size());
        assertEquals("055 off at " + bounds(off055), 0, off055.size());
    }

    /**
     * @LOADGOOD in game over clip005 #0 (V E10): box (62,88,196,24),
     * "COLONY00.SAV erfolgreich geladen." -- 0 px off.
     */
    public void testLoadGoodInGameAgainstClip005() throws Exception {
        final File dir = clips("@LOADGOOD of clip005");
        if (dir == null) return;
        final File f = new File(dir, "clip005/frame_000000.png");
        if (!f.isFile()) {
            System.err.println(getClass().getSimpleName() + ": no " + f + ", skipped");
            return;
        }
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        final List<Point> off = boxOver(ImageIO.read(f), ClassicSaveSlots.noticeRequest(t,
            ClassicSaveSlots.LOAD_GOOD, "COLONY00.SAV", null, ""), -1,
            new Rectangle(62, 88, 196, 24));
        System.out.println(getClass().getSimpleName() + ": clip005 #0 @LOADGOOD "
            + off.size() + " px off");
        assertEquals("off at " + bounds(off), 0, off.size());
    }

    /**
     * The title's load page over clip006 #0 (V E5, 64,000 px): WOODPANL,
     * the prompt, the ten rows at a pitch of 6 and the bar on row 1 -- 0
     * px off apart from the original's mouse arrow (one small cluster);
     * and @LOADGOOD over the page at #243 (V E11), the arrow excused the
     * same way.
     */
    public void testTheLoadPageAgainstClip006() throws Exception {
        final File dir = clips("the load page of clip006");
        if (dir == null) return;
        final File f0 = new File(dir, "clip006/frame_000000.png");
        final File f243 = new File(dir, "clip006/frame_000243.png");
        if (!f0.isFile() || !f243.isFile()) {
            System.err.println(getClass().getSimpleName() + ": no clip006 frames, skipped");
            return;
        }
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final ClassicMainMenuPanel.MenuAssets a
            = ClassicMainMenuPanel.MenuAssets.fromPackFiles(pack);
        assertNotNull(a);
        assertNotNull("WOODPANL.PIK", a.woodPanel);
        final ClassicText t = ClassicText.load(pack);
        final String prompt = ClassicAdvisorBox.plain(String.join(" ",
            t.message(ClassicSaveSlots.LOAD_SECTION).text));
        final List<String> rows = new ArrayList<>(Collections.nCopies(10, ClassicSaveSlots.EMPTY));
        rows.set(0, "Entdecker Dago der Holl., Herbst 1729");
        rows.set(2, "Entdecker Probelauf der Holl., Frühjahr 1505");
        rows.set(8, "Entdecker Dago der Holl., Frühjahr 1730");
        rows.set(9, "Entdecker Probelauf der Holl., Frühjahr 1513");
        final Rectangle all = new Rectangle(0, 0, 320, 200);
        final BufferedImage c0 = ImageIO.read(f0);
        final BufferedImage ours = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = ours.createGraphics();
        ClassicMainMenuPanel.paintLoadPage(g, a, prompt, rows, 0);
        g.dispose();
        final List<Point> off0 = diff(c0, ours, all);
        final Rectangle arrow0 = bounds(off0);

        final BufferedImage c243 = ImageIO.read(f243);
        final BufferedImage ours243 = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
        g = ours243.createGraphics();
        ClassicMainMenuPanel.paintLoadPage(g, a, prompt, rows, 0);
        ClassicMainMenuPanel.paintNotice(g, a, ClassicMenuBox.GAME,
            ClassicMainMenuPanel.wrapNotice(a, ClassicSaveSlots.noticeText(t,
                ClassicSaveSlots.LOAD_GOOD, "COLONY00.SAV")));
        g.dispose();
        final List<Point> off243 = diff(c243, ours243, all);
        final Rectangle arrow243 = bounds(off243);
        System.out.println(getClass().getSimpleName() + ": clip006 #0 page " + off0.size()
            + " px off at " + arrow0 + ", #243 @LOADGOOD " + off243.size()
            + " px off at " + arrow243);
        // Only the arrow: one cluster no larger than the sprite (11 x 16).
        for (Rectangle r : new Rectangle[] { arrow0, arrow243 }) {
            if (r == null) continue;
            assertTrue("off beyond the arrow: " + r, r.width <= 11 && r.height <= 16);
        }
        assertTrue(off0.size() <= 120);
        assertTrue(off243.size() <= 120);
        // The @LOADGOOD box itself (62,88,196,24), away from the arrow: 0 px.
        final Rectangle box = new Rectangle(62, 88, 196, 24);
        if (arrow243 == null || !arrow243.intersects(box)) {
            assertEquals(0, countIn(off243, box));
        }
    }

    private static int countIn(List<Point> ps, Rectangle r) {
        int n = 0;
        for (Point q : ps) if (r.contains(q)) n++;
        return n;
    }
}
