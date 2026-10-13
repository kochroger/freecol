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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.freecol.FreeCol;
import net.sf.freecol.client.ClientOptions;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.control.InGameController;
import net.sf.freecol.client.control.PreGameController;
import net.sf.freecol.client.gui.GUI;
import net.sf.freecol.common.io.FreeColDirectories;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Turn;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.util.Utils;
import net.sf.freecol.server.FreeColServer;
import net.sf.freecol.server.ServerTestHelper;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * Part O, step 3: the Classic UI never deletes a saved game by itself
 * (Roger 2026-10-13 ~13:30: "Bitte auf keinen Fall automatisches
 * L&ouml;schen.").  Before, {@code FreeColClient.quit} removed the numbered
 * autosaves older than {@code model.option.autosaveValidity} (default 30
 * days), {@code PreGameController} removed every autosave at a new game
 * when {@code model.option.autosaveDelete} was set, the before-last-turn
 * autosave was deleted before the last-turn one was renamed onto it, and
 * a map import of the map editor's {@code autosave/startMap.fsg} consumed
 * that file.  Each is run here the way the game runs it (FreeCol's real
 * client, controllers, server and directories, pointed at a temporary
 * folder), in the Classic UI and, for comparison, without it.
 */
public class ClassicKeepSavesTest extends FreeColTestCase {

    private static final long DAY = 24L * 60L * 60L * 1000L;

    /** An old numbered autosave, the name FreeCol gives one. */
    private static final String OLD_NUMBERED
        = "Autosave-1a2b3c4d_Niederländisch_1495.fsg";


    // The rig: temporary save folders, the classic switch, the log.

    /** Every log record while a test runs. */
    private static final class Records extends Handler {

        final List<LogRecord> all = new ArrayList<>();

        @Override
        public synchronized void publish(LogRecord r) {
            this.all.add(r);
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}

        /** The messages that start with a text. */
        synchronized List<String> starting(String start) {
            final List<String> out = new ArrayList<>();
            for (LogRecord r : this.all) {
                final String m = r.getMessage();
                if (m != null && m.startsWith(start)) out.add(m);
            }
            return out;
        }
    }

    private static final String[] DIR_FIELDS = {
        "saveDirectory", "autosaveDirectory", "savegameFile", "clientOptionsFile"
    };

    private final Object[] savedDirs = new Object[DIR_FIELDS.length];
    private boolean savedClassic;
    private Records records;
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
        this.root = Files.createTempDirectory("o3-keep-").toFile();
        this.saves = new File(this.root, "save");
        this.autosaves = new File(this.saves, "autosave");
        assertTrue(this.autosaves.mkdirs());
        dirField("saveDirectory").set(null, this.saves);
        dirField("autosaveDirectory").set(null, this.autosaves);
        dirField("savegameFile").set(null, null);
        dirField("clientOptionsFile").set(null, new File(this.root, "no-options.xml"));
        assertEquals(this.autosaves, FreeColDirectories.getAutosaveDirectory());
        this.records = new Records();
        this.records.setLevel(Level.ALL);
        Logger.getLogger("").addHandler(this.records);
    }

    @Override
    protected void tearDown() throws Exception {
        Logger.getLogger("").removeHandler(this.records);
        FreeCol.setClassic(this.savedClassic);
        ServerTestHelper.stopServerGame();
        for (int i = 0; i < DIR_FIELDS.length; i++) {
            dirField(DIR_FIELDS[i]).set(null, this.savedDirs[i]);
        }
        removeTree(this.root);   // this test's own temporary files only
        super.tearDown();
    }

    private static void removeTree(File f) {
        if (f == null) return;
        final File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) removeTree(k);
        f.delete();
    }

    /** A file with some bytes and a modification time {@code days} ago. */
    private static File plant(File dir, String name, String text, int days)
        throws IOException {
        final File f = new File(dir, name);
        Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
        assertTrue(f.setLastModified(System.currentTimeMillis() - days * DAY));
        return f;
    }

    private static String text(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    /** The names in the autosave folder. */
    private List<String> autosaveNames() {
        final String[] names = this.autosaves.list();
        final List<String> out = new ArrayList<>(Arrays.asList(names));
        java.util.Collections.sort(out);
        return out;
    }

    /** No saved game reached the last guard (Utils.deleteFile). */
    private void assertNoLastGuard() {
        assertEquals("a delete of a saved game reached Utils.deleteFile",
            new ArrayList<String>(), this.records.starting(Utils.KEPT_SAVE));
    }

    /** FreeCol's own client options (data/base/client-options.xml). */
    private static ClientOptions baseOptions() {
        final ClientOptions co = new ClientOptions();
        co.load(FreeColDirectories.getBaseClientOptionsFile());
        co.fixClientOptions();
        return co;
    }

    private static void set(FreeColClient fcc, String name, Object value) throws Exception {
        final Field f = FreeColClient.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(fcc, value);
    }

    /** A client object without its constructor (as ClassicNativeBoxesTest). */
    private static FreeColClient bareClient() throws Exception {
        final Class<?> uc = Class.forName("sun.misc.Unsafe");
        final Field theUnsafe = uc.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        return (FreeColClient)uc.getMethod("allocateInstance", Class.class)
            .invoke(theUnsafe.get(null), FreeColClient.class);
    }

    private static Object call(Object target, Class<?> c, String method,
                               Class<?>[] types, Object... args) throws Exception {
        final Method m = c.getDeclaredMethod(method, types);
        m.setAccessible(true);
        try {
            return m.invoke(target, args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception) throw (Exception)e.getCause();
            throw e;
        }
    }

    /** FreeColClient.loadClientOptions, the options a client starts with. */
    private static ClientOptions loadClientOptions(File savedGame) throws Exception {
        return (ClientOptions)call(bareClient(), FreeColClient.class,
            "loadClientOptions", new Class<?>[] { File.class }, savedGame);
    }

    /** The headless GUI, without sounds. */
    private static GUI quietGui(FreeColClient fcc) {
        return new GUI(fcc) {
                @Override
                public void playSound(String sound) {}
            };
    }

    /**
     * A client of a running server game: FreeCol's real controllers, the
     * headless GUI, our Dutch player (the admin of a single player game).
     */
    private FreeColClient gameClient(ClientOptions co) throws Exception {
        ServerTestHelper.stopServerGame();   // the test map is built in the game set
        final Game game = ServerTestHelper.startServerGame(getTestMap());
        final ServerPlayer dutch = getServerPlayer(game, "model.nation.dutch");
        dutch.setAI(false);
        final Field admin = Player.class.getDeclaredField("admin");
        admin.setAccessible(true);
        admin.set(dutch, Boolean.TRUE);
        final FreeColClient fcc = bareClient();
        set(fcc, "gui", quietGui(fcc));
        set(fcc, "clientOptions", co);
        set(fcc, "freeColServer", ServerTestHelper.getServer());
        set(fcc, "inGameController", new InGameController(fcc));
        set(fcc, "preGameController", new PreGameController(fcc));
        fcc.setGame(game);
        fcc.setMyPlayer(dutch);
        game.setCurrentPlayer(dutch);
        return fcc;
    }


    // The options

    /**
     * FreeCol's defaults now keep every autosave: validity 0 (was 30 days,
     * client-options.xml:346), delete at a new game false.
     */
    public void testTheDefaultsKeepEverySave() {
        final ClientOptions co = baseOptions();
        assertEquals(0, co.getInteger(ClientOptions.AUTOSAVE_VALIDITY));
        assertFalse(co.getBoolean(ClientOptions.AUTOSAVE_DELETE));
        // And keepAllSaves sets both, whatever they were.
        co.setInteger(ClientOptions.AUTOSAVE_VALIDITY, 30);
        co.setBoolean(ClientOptions.AUTOSAVE_DELETE, true);
        co.keepAllSaves();
        assertEquals(0, co.getInteger(ClientOptions.AUTOSAVE_VALIDITY));
        assertFalse(co.getBoolean(ClientOptions.AUTOSAVE_DELETE));
    }

    /**
     * An options file of an older build (validity 30, delete at a new
     * game), with or without a saved game whose client options say the
     * same (FreeCol takes only the mods from a save,
     * ClientOptions.merge(FreeColSavegameFile)): the Classic UI's client
     * (FreeColClient.loadClientOptions) starts with 0 / false all the same.
     * Without the Classic UI the options file's choice stands (FreeCol's
     * rule), a save alone brings the defaults.
     */
    public void testAnOlderOptionsFileCannotDelete() throws Exception {
        final ClientOptions old = baseOptions();
        old.setInteger(ClientOptions.AUTOSAVE_VALIDITY, 30);
        old.setBoolean(ClientOptions.AUTOSAVE_DELETE, true);
        final File optionsFile = new File(this.root, "options.xml");
        assertTrue(old.save(optionsFile));
        assertTrue(text(optionsFile).contains("model.option.autosaveValidity"));

        // A saved game with those options inside it.
        ServerTestHelper.stopServerGame();
        final Game game = ServerTestHelper.startServerGame(getTestMap());
        assertNotNull(game);
        final File save = new File(this.saves, "old-build.fsg");
        ServerTestHelper.getServer().saveGame(save, old, (Unit)null);
        ServerTestHelper.stopServerGame();

        for (File savedGame : new File[] { null, save }) {
            for (boolean withFile : new boolean[] { false, true }) {
                dirField("clientOptionsFile").set(null, (withFile) ? optionsFile
                    : new File(this.root, "no-options.xml"));
                if (savedGame == null && !withFile) continue;   // the defaults: above
                final String what = "save " + savedGame + ", options file " + withFile;

                FreeCol.setClassic(true);
                ClientOptions co = loadClientOptions(savedGame);
                assertEquals(what, 0, co.getInteger(ClientOptions.AUTOSAVE_VALIDITY));
                assertFalse(what, co.getBoolean(ClientOptions.AUTOSAVE_DELETE));

                FreeCol.setClassic(false);
                co = loadClientOptions(savedGame);
                assertEquals(what, (withFile) ? 30 : 0,
                             co.getInteger(ClientOptions.AUTOSAVE_VALIDITY));
                assertEquals(what, withFile, co.getBoolean(ClientOptions.AUTOSAVE_DELETE));
            }
        }
        assertTrue(save.exists());
        assertNoLastGuard();
    }


    // Quit

    /**
     * Quit's housekeeping (FreeColClient.quit, the part before the exit):
     * an autosave 40 days old survives in the Classic UI also when the
     * options say 30 days (changed after the start, or loaded before this
     * build); nothing reaches the last guard.  For comparison: FreeCol
     * with the new default (0) keeps it too, FreeCol with the user's 30
     * deletes it (so the rig would see a deletion).
     */
    public void testAnOldAutosaveSurvivesQuit() throws Exception {
        final File numbered = plant(this.autosaves, OLD_NUMBERED, "turn 1495", 40);
        final File last = plant(this.autosaves, "Autosave-last-turn.fsg", "last", 40);
        final File before = plant(this.autosaves, "Autosave-before-last-turn.fsg", "before", 40);
        final File manual = plant(this.saves, "Levi-1520.fsg", "manual", 400);
        final List<String> all = autosaveNames();

        final FreeColClient fcc = bareClient();
        final ClientOptions co = baseOptions();
        set(fcc, "clientOptions", co);

        // FreeCol's new default: nothing outdated.
        FreeCol.setClassic(false);
        assertNull(call(fcc, FreeColClient.class, "removeOutdatedAutosaves", new Class<?>[0]));
        assertEquals(all, autosaveNames());

        // The Classic UI with options that say 30 days.
        FreeCol.setClassic(true);
        co.setInteger(ClientOptions.AUTOSAVE_VALIDITY, 30);
        assertNull(call(fcc, FreeColClient.class, "removeOutdatedAutosaves", new Class<?>[0]));
        assertEquals(all, autosaveNames());
        assertEquals("turn 1495", text(numbered));
        assertTrue(last.exists() && before.exists() && manual.exists());
        // The removal itself refuses in the Classic UI, any number of days.
        assertNull(FreeColDirectories.removeOutdatedAutosaves("Autosave",
                new ArrayList<String>(), 1L));
        assertEquals(all, autosaveNames());
        assertNoLastGuard();

        // FreeCol (not the Classic UI) with the user's 30 days: deleted.
        FreeCol.setClassic(false);
        final Object log = call(fcc, FreeColClient.class, "removeOutdatedAutosaves",
                                new Class<?>[0]);
        assertNotNull(log);
        assertTrue(log.toString(), log.toString().contains(OLD_NUMBERED));
        assertFalse(numbered.exists());
        assertTrue(last.exists() && before.exists() && manual.exists());
    }


    // A new game

    /** PreGameController.startGameInternal, the start of a new game. */
    private static void startGame(FreeColClient fcc) throws Exception {
        call(fcc.getPreGameController(), PreGameController.class,
             "startGameInternal", new Class<?>[0]);
    }

    /**
     * A new game (FreeCol's real PreGameController.startGameInternal) keeps
     * every old autosave in the Classic UI: with the options of an older
     * options file (loaded, so 0 / false) and with options that say
     * "delete" (set after the start); nothing reaches the last guard.  For
     * comparison FreeCol with "delete" set removes them.
     */
    public void testANewGameKeepsOldAutosaves() throws Exception {
        final File numbered = plant(this.autosaves, OLD_NUMBERED, "turn 1495", 3);
        final File last = plant(this.autosaves, "Autosave-last-turn.fsg", "last", 1);
        final File before = plant(this.autosaves, "Autosave-before-last-turn.fsg", "before", 2);
        final List<String> all = autosaveNames();

        // An older options file that says delete.
        final ClientOptions old = baseOptions();
        old.setBoolean(ClientOptions.AUTOSAVE_DELETE, true);
        old.setInteger(ClientOptions.AUTOSAVE_VALIDITY, 1);
        final File optionsFile = new File(this.root, "options.xml");
        assertTrue(old.save(optionsFile));
        dirField("clientOptionsFile").set(null, optionsFile);

        FreeCol.setClassic(true);
        FreeColClient fcc = gameClient(loadClientOptions(null));
        assertFalse(fcc.getClientOptions().getBoolean(ClientOptions.AUTOSAVE_DELETE));
        startGame(fcc);
        assertTrue(fcc.isInGame());
        assertEquals(all, autosaveNames());

        // Options that say delete (set at run time) in the Classic UI.
        fcc = gameClient(baseOptions());
        fcc.getClientOptions().setBoolean(ClientOptions.AUTOSAVE_DELETE, true);
        assertTrue(fcc.getMyPlayer().isAdmin());
        startGame(fcc);
        assertTrue(fcc.isInGame());
        assertEquals(all, autosaveNames());
        assertEquals("turn 1495", text(numbered));
        assertNull(FreeColDirectories.removeAutosaves("Autosave"));
        assertEquals(all, autosaveNames());
        assertNoLastGuard();

        // FreeCol (not the Classic UI) with delete set: all gone.
        FreeCol.setClassic(false);
        fcc = gameClient(baseOptions());
        fcc.getClientOptions().setBoolean(ClientOptions.AUTOSAVE_DELETE, true);
        startGame(fcc);
        assertFalse(numbered.exists() || last.exists() || before.exists());
    }


    // The last-turn autosave

    /** InGameController.autoSaveGame, the autosave at our turn's start. */
    private static void autoSave(FreeColClient fcc) throws Exception {
        call(fcc.getInGameController(), InGameController.class,
             "autoSaveGame", new Class<?>[0]);
    }

    /**
     * The autosave at our turn's start (FreeCol's real
     * InGameController.autoSaveGame and server save): the last-turn
     * autosave becomes the before-last-turn one in one move, the old
     * before-last-turn file is replaced, never deleted first, and every
     * numbered autosave stays; in the Classic UI and without it.  Before,
     * the before-last-turn autosave was deleted first (in the Classic UI
     * now the last guard's case, and a rename onto an existing file fails
     * on Windows).
     */
    public void testTheLastTurnAutosaveMovesWithoutADelete() throws Exception {
        for (boolean classic : new boolean[] { true, false }) {
            removeTree(this.autosaves);
            assertTrue(this.autosaves.mkdirs());
            FreeCol.setClassic(classic);
            final FreeColClient fcc = gameClient(baseOptions());
            final Game game = fcc.getGame();
            final File numberedOld = plant(this.autosaves, OLD_NUMBERED, "turn 1495", 50);
            final File older = plant(this.autosaves, "Autosave-before-last-turn.fsg",
                                     "an older session", 5);
            final File last = new File(this.autosaves, "Autosave-last-turn.fsg");
            final File before = new File(this.autosaves, "Autosave-before-last-turn.fsg");

            game.setTurn(new Turn(4));
            autoSave(fcc);                     // no last-turn yet: only saves
            assertTrue(last.exists());
            assertEquals("an older session", text(before));
            final byte[] turn4 = Files.readAllBytes(last.toPath());
            assertTrue(last.setLastModified(System.currentTimeMillis() - 2 * DAY));
            final long turn4Time = last.lastModified();
            final int numbered4 = autosaveNames().size();

            game.setTurn(new Turn(5));
            autoSave(fcc);                     // last-turn -> before-last-turn
            final String what = (classic) ? "classic" : "FreeCol";
            assertTrue(what, Arrays.equals(turn4, Files.readAllBytes(before.toPath())));
            assertEquals(what, turn4Time, before.lastModified());
            assertTrue(what, last.exists() && last.lastModified() > turn4Time);
            assertTrue(what, numberedOld.exists());
            // One more numbered autosave (turn 5), none gone.
            assertEquals(what, numbered4 + 1, autosaveNames().size());
            assertTrue(what, older.exists());  // the same name, the turn-4 content
            assertEquals(what, new ArrayList<String>(), this.records.starting("Could not move"));
            assertNoLastGuard();
            ServerTestHelper.stopServerGame();
        }
    }


    /**
     * When the last-turn autosave cannot be moved (here: open for reading
     * elsewhere, which on Windows forbids a move), the before-last-turn
     * autosave stays as it was and the new last turn is still saved.
     * Before, it was deleted first, so a failed rename left none (the
     * turn before the last was lost).  On a file system that moves an open
     * file nothing fails and the test only checks the normal move.
     */
    public void testAFailedMoveKeepsTheBeforeLastTurnAutosave() throws Exception {
        FreeCol.setClassic(true);
        final FreeColClient fcc = gameClient(baseOptions());
        final Game game = fcc.getGame();
        final File last = plant(this.autosaves, "Autosave-last-turn.fsg", "turn 4", 2);
        final File before = plant(this.autosaves, "Autosave-before-last-turn.fsg", "turn 3", 3);
        game.setTurn(new Turn(5));
        try (java.io.FileInputStream lock = new java.io.FileInputStream(last)) {
            autoSave(fcc);
        }
        assertTrue("the before-last-turn autosave is kept", before.exists());
        assertTrue(last.exists() && last.length() > 100);   // turn 5 saved
        if (this.records.starting("Could not move").isEmpty()) {
            System.err.println(getClass().getSimpleName()
                + ": an open file was moved here, the failure case not checked");
            assertEquals("turn 4", text(before));
        } else {
            assertEquals("turn 3", text(before));               // untouched
        }
        assertNoLastGuard();
    }


    // The map editor's start map

    /**
     * The map editor's start map ({@code autosave/startMap.fsg}): a map
     * import of it (FreeColServer.readMap, as a new game with a remembered
     * import does) consumes it in FreeCol, never in the Classic UI, where
     * the load box lists it like any save.
     */
    public void testTheStartMapIsNeverConsumed() throws Exception {
        final File startMap = FreeColDirectories.getStartMapFile();
        assertEquals(new File(this.autosaves, "startMap.fsg"), startMap);
        final net.sf.freecol.common.model.Topology topo
            = net.sf.freecol.common.model.Topology.current();
        try {
            ServerTestHelper.stopServerGame();
            ServerTestHelper.startServerGame(getTestMap());
            ServerTestHelper.getServer().saveGame(startMap, null, (Unit)null);
            ServerTestHelper.stopServerGame();
            final long size = startMap.length();
            assertTrue(size > 0);

            FreeCol.setClassic(true);
            assertNotNull(FreeColServer.readMap(startMap, spec()));
            assertTrue(startMap.exists());
            assertEquals(size, startMap.length());
            assertNoLastGuard();

            FreeCol.setClassic(false);
            assertNotNull(FreeColServer.readMap(startMap, spec()));
            assertFalse("FreeCol consumes the start map", startMap.exists());
        } finally {
            net.sf.freecol.common.model.Topology.setCurrent(topo);
        }
    }


    // The last guard

    /**
     * Utils.deleteFile, through which every delete of a saved game in the
     * game goes: in the Classic UI a saved game ({@code .fsg}, any case)
     * stays and a warning says so; any other file is deleted; without the
     * Classic UI a saved game is deleted.
     */
    public void testTheLastGuardKeepsEverySavedGame() throws Exception {
        final File a = plant(this.saves, "Levi.fsg", "a", 1);
        final File b = plant(this.autosaves, "OLD.FSG", "b", 1);
        final File c = plant(this.root, "FreeCol.log", "c", 1);
        final File d = plant(this.root, "COLONY00.SAV", "d", 1);
        FreeCol.setClassic(true);
        assertTrue(Utils.keepsSavedGame(a));
        assertTrue(Utils.keepsSavedGame(b));
        assertFalse(Utils.keepsSavedGame(c));
        assertFalse(Utils.keepsSavedGame(null));
        Utils.deleteFile(a);
        Utils.deleteFiles(Arrays.asList(b, c, d));
        assertTrue(a.exists() && b.exists());
        assertFalse(c.exists() || d.exists());
        assertEquals(2, this.records.starting(Utils.KEPT_SAVE).size());
        assertTrue(this.records.starting(Utils.KEPT_SAVE).get(0).endsWith("Levi.fsg"));

        FreeCol.setClassic(false);
        assertFalse(Utils.keepsSavedGame(a));
        Utils.deleteFile(a);
        assertFalse(a.exists());
    }


    // Every delete in the code

    /**
     * Every place in src that deletes, moves or renames a file, counted
     * per file (O3's list in o/O3.md).  A new one makes this test fail, so
     * that it is checked against Roger's rule (no saved game is ever
     * deleted by the game in the Classic UI) before the list is extended.
     */
    public void testEveryDeleteInTheCodeIsKnown() throws Exception {
        final File src = new File("src/net/sf/freecol");
        if (!src.isDirectory()) {
            System.err.println(getClass().getSimpleName()
                + ": no src folder at " + src.getAbsolutePath() + ", the list not checked");
            return;
        }
        final Pattern p = Pattern.compile(
            "\\bdeleteFiles?\\(|\\.delete\\(\\)|Files\\.delete(IfExists)?\\("
            + "|deleteOnExit\\(|\\.renameTo\\(|Files\\.move\\(|deleteRecursively\\(");
        final Map<String, Integer> found = new TreeMap<>();
        scan(src, p, found);
        final Map<String, Integer> known = new TreeMap<>();
        // Utils: deleteFile and deleteFiles themselves, the File.delete in
        // deleteFile, deleteFiles calling deleteFile (the last guard).
        known.put("common/util/Utils.java", 4);
        // removeOutdatedAutosaves, removeAutosaves (both refuse in the
        // Classic UI); the log file and the comms log file (no saves).
        known.put("common/io/FreeColDirectories.java", 4);
        // readGame: the map editor's start map (never in the Classic UI).
        known.put("server/FreeColServer.java", 1);
        // autoSaveGame: last-turn -> before-last-turn, a move, no delete.
        known.put("client/control/InGameController.java", 1);
        // Tools, never run by the game: the art pack's output folder, a
        // map converter that renames its input to .old.
        known.put("tools/classicassets/ClassicAssetConverter.java", 3);
        known.put("tools/MapConverter.java", 1);
        assertEquals(known, found);
    }

    private static void scan(File dir, Pattern p, Map<String, Integer> found)
        throws IOException {
        final File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File f : kids) {
            if (f.isDirectory()) {
                scan(f, p, found);
            } else if (f.getName().endsWith(".java")) {
                int n = 0;
                for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                    final String t = line.trim();
                    if (t.startsWith("//") || t.startsWith("*") || t.startsWith("import ")) continue;
                    final Matcher m = p.matcher(line);
                    while (m.find()) n++;
                }
                if (n > 0) {
                    final String path = f.getPath().replace('\\', '/');
                    found.put(path.substring(path.indexOf("net/sf/freecol/") + 15), n);
                }
            }
        }
    }
}
