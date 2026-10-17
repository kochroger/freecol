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
import java.util.Locale;
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
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.io.FreeColDirectories;
import net.sf.freecol.common.io.FreeColSavegameFile;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Turn;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.option.IntegerOption;
import net.sf.freecol.common.option.TextOption;
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

        /** Per switch, the boxes the prompter had been asked by then. */
        final List<List<String>> boxesAtSwitch = new ArrayList<>();

        /** Per switch, whether the turn start's autosave was still written (part Z1). */
        final List<Boolean> autosaveAtSwitch = new ArrayList<>();

        /** The game's current player each time an autosave began (part Z1). */
        final List<Player> currentAtAutosave
            = Collections.synchronizedList(new ArrayList<>());

        /** Wraps the autosave rows' writer (a slow or failing autosave), or null. */
        volatile java.util.function.UnaryOperator<ClassicSaveSlots.SaveWriter> autosaveWrap
            = null;

        SlGUI(FreeColClient fcc) {
            super(fcc);
        }

        @Override
        void switchToSave(File file) {
            this.switched.add(file);
            this.boxesAtSwitch.add((this.prompter instanceof Answers)
                ? new ArrayList<>(((Answers) this.prompter).ids())
                : Collections.<String>emptyList());
            this.autosaveAtSwitch.add(this.autosaveGate.running());
        }

        @Override
        ClassicSaveSlots.SaveWriter autosaveWriter(FreeColServer server, ClientOptions co) {
            final ClassicSaveSlots.SaveWriter real = super.autosaveWriter(server, co);
            final java.util.function.UnaryOperator<ClassicSaveSlots.SaveWriter> w
                = this.autosaveWrap;
            return (w == null) ? real : w.apply(real);
        }

        @Override
        public void autosaveStarting() {
            final FreeColClient fcc = getFreeColClient();
            this.currentAtAutosave.add((fcc == null || fcc.getGame() == null) ? null
                : fcc.getGame().getCurrentPlayer());
            super.autosaveStarting();
        }

        /** Thrown by the turn's autosave (a crash in it), or null. */
        volatile RuntimeException autosaveCrash = null;

        @Override
        public boolean autosaveTurn(Turn turn, File lastTurnFile, File beforeLastTurnFile) {
            final RuntimeException crash = this.autosaveCrash;
            if (crash != null) throw crash;
            return super.autosaveTurn(turn, lastTurnFile, beforeLastTurnFile);
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
        // In the save box an "(EMPTY)" row is taken as any row.
        for (int i = 0; i < 8; i++) assertTrue(box.enabled(i));
        final File row3 = new File(this.saves, "COLONY02.fsg");
        assertTrue(row3.isFile());
        assertEquals(Arrays.asList("COLONY02.fsg", "autosave"), names(this.saves));
        assertTrue(text(a.last()), text(a.last()).contains("COLONY02.SAV"));
        assertTrue(text(a.last()), text(a.last()).contains(owner()));
        assertEquals(this.saves, FreeColDirectories.getSaveDirectory());
        assertNull(FreeColDirectories.getSavegameFile());

        // The box again: row 3 shows the save; saved over without a
        // question, the save it held kept in save/ersetzt (I).
        final byte[] first = Files.readAllBytes(row3.toPath());
        final long firstTime = row3.lastModified();
        a = new Answers(2);
        this.gui.prompter = a;
        assertEquals(2, this.gui.showSaveBox());
        assertEquals(Arrays.asList(ClassicSaveSlots.SAVE_SECTION, ClassicSaveSlots.SAVE_GOOD),
                     a.ids());
        assertFalse(ClassicSaveSlots.EMPTY.equals(a.boxes.get(0).plainRows()[2]));
        assertEquals(ClassicSaveSlots.EMPTY, a.boxes.get(0).plainRows()[3]);
        final File kept = new File(this.saves, ClassicSaveSlots.REPLACED_FOLDER);
        final List<String> keptNames = names(kept);
        assertEquals(keptNames.toString(), 1, keptNames.size());
        assertTrue(keptNames.get(0),
                   keptNames.get(0).matches("COLONY02_\\d{4}-\\d\\d-\\d\\d_\\d{6}\\.fsg"));
        final File copy = new File(kept, keptNames.get(0));
        assertTrue(Arrays.equals(first, Files.readAllBytes(copy.toPath())));
        assertEquals(firstTime, copy.lastModified());
        assertEquals(Arrays.asList("COLONY02.fsg", "autosave", ClassicSaveSlots.REPLACED_FOLDER),
                     names(this.saves));

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
     * acceptance A9), a row whose game cannot be copied into
     * {@code ersetzt} (part Z1; an empty row is still saved) and a game
     * without a server.
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
        // The copy of the row was made before the move, either way: the
        // old save twice, never none.
        final File kept = new File(this.saves, ClassicSaveSlots.REPLACED_FOLDER);
        assertEquals(1, names(kept).size());
        assertTrue(Arrays.equals(old, Files.readAllBytes(
            new File(kept, names(kept).get(0)).toPath())));
        assertEquals(Arrays.asList("COLONY04.fsg", "COLONY05.fsg", "autosave",
                                   ClassicSaveSlots.REPLACED_FOLDER),
                     names(this.saves));

        // The copy into ersetzt cannot be made (a file where the folder
        // goes): row 6 is not saved over, @SAVEERROR, its game stays
        // (part Z1, C2 of part Z's check: our choice).
        final File keptFolder = new File(this.saves, ClassicSaveSlots.REPLACED_FOLDER);
        final File parked = new File(this.root, "ersetzt.parked");
        Files.move(keptFolder.toPath(), parked.toPath());
        Files.write(keptFolder.toPath(), "x".getBytes(StandardCharsets.UTF_8));
        final byte[] six = Files.readAllBytes(row6.toPath());
        final long sixTime = row6.lastModified();
        a = new Answers(5);
        this.gui.prompter = a;
        assertEquals(-1, this.gui.showSaveBox());
        assertEquals(Arrays.asList(ClassicSaveSlots.SAVE_SECTION, ClassicSaveSlots.SAVE_ERROR),
                     a.ids());
        assertTrue(text(a.last()), text(a.last()).contains("COLONY05.SAV"));
        assertTrue(Arrays.equals(six, Files.readAllBytes(row6.toPath())));
        assertEquals(sixTime, row6.lastModified());
        // An empty row needs no copy: saved.
        a = new Answers(6);
        this.gui.prompter = a;
        assertEquals(6, this.gui.showSaveBox());
        assertEquals(ClassicSaveSlots.SAVE_GOOD, a.last().id);
        assertTrue(keptFolder.delete());
        Files.move(parked.toPath(), keptFolder.toPath());
        assertEquals(Arrays.asList("COLONY04.fsg", "COLONY05.fsg", "COLONY06.fsg", "autosave",
                                   ClassicSaveSlots.REPLACED_FOLDER),
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
     * rows), the bar on row 1; an "(EMPTY)" row is inert, the box never
     * answers it (D-SL7; ClassicAdvisorLayerTest.testAnInertRowKeepsTheBoxUp),
     * and a prompter that does gets the box again with the bar on it; a
     * row that is no save says @LOADNOT and the game goes on; a good row
     * @LOADGOOD with "COLONY02.SAV", then (after it) the switch to that
     * file; row 10 is FreeCol's last-turn autosave; Escape loads nothing;
     * the save folder stays.
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
        // The "(EMPTY)" rows are inert: the box itself never answers them
        // and stays up (D-SL7), drawn as the others, not grey; the other
        // rows, the broken one too, can be taken.
        for (int i = 0; i < 10; i++) {
            final boolean empty = ClassicSaveSlots.EMPTY.equals(rows[i]);
            assertEquals("row " + (i + 1), !empty, box.enabled(i));
            assertFalse("row " + (i + 1), box.greyed(i));
        }
        assertTrue(box.enabled(4));
        assertTrue(text(a.last()), text(a.last()).contains("COLONY02.SAV"));
        assertEquals(Arrays.asList(slots.slotFile(2)), this.gui.switched);
        // The original's order (V E10): «erfolgreich geladen» first, then
        // the switch to the save.
        assertEquals(Arrays.asList(ClassicSaveSlots.LOAD_SECTION, ClassicSaveSlots.LOAD_SECTION,
                                   ClassicSaveSlots.LOAD_GOOD), this.gui.boxesAtSwitch.get(0));

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
     * While another nation's turn is played (the AI phase: the server
     * moves its units on its own threads, a save would read the game with
     * no lock) «Spiel sichern» and «Spiel laden» do nothing: no box, no
     * file (the review of part SL; I, the original takes no input then);
     * in our turn again they work.
     */
    public void testNothingWhileOthersMove() throws Exception {
        final FreeColClient fcc = gameClient(baseOptions());
        final Game game = fcc.getGame();
        final ClassicSaveSlots slots = ClassicSaveSlots.of(fcc.getClientOptions());
        ClassicSaveSlots.writeAtomic(slots.slotFile(9),
            f -> fcc.getFreeColServer().saveGame(f, fcc.getClientOptions(), (Unit) null));
        final Map<String, String> before = census(this.saves);
        game.setCurrentPlayer(getServerPlayer(game, "model.nation.french"));
        assertTrue(this.gui.othersMoving());
        Answers a = new Answers(2, 9);
        this.gui.prompter = a;
        assertEquals(-1, this.gui.showSaveBox());
        assertFalse(this.gui.showLoadBox());
        assertTrue(a.ids().toString(), a.ids().isEmpty());
        assertTrue(this.gui.switched.isEmpty());
        assertEquals(before, census(this.saves));
        // Our turn again.
        game.setCurrentPlayer(fcc.getMyPlayer());
        assertFalse(this.gui.othersMoving());
        a = new Answers(2);
        this.gui.prompter = a;
        assertEquals(2, this.gui.showSaveBox());
        assertTrue(new File(this.saves, "COLONY02.fsg").isFile());
        // No client, no game: never "others".
        assertFalse(new ClassicGUI(null).othersMoving());
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
        // The loaded game arrived (reconnectGUI): an error that ends
        // something now is the game's, FreeCol's box, never @LOADERROR --
        // also one that comes before the queued view build.
        titles.clear();
        javax.swing.SwingUtilities.invokeAndWait(() -> {
                g.loadSavedGame(row3);
                g.reconnectGUI(null, null);
                g.showErrorPanel("a game error", () -> {});
            });
        assertEquals(Arrays.asList("error", "error", "error"), a.ids());
        for (int i = 0; i < 3; i++) javax.swing.SwingUtilities.invokeAndWait(() -> {});
        for (String t : titles) assertFalse(t, t.contains("COLONY02.SAV"));
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

    // The turn start's autosave and those who wait for it (part Z1, C1 of
    // part Z's check)

    /**
     * A turn start's autosave held half-way through row 10's write, on a
     * thread of its own as FreeCol's network thread writes it: either the
     * whole turn change through FreeCol's real controller
     * ({@code setCurrentPlayerHandler}) or its autosave alone (with the
     * gate's seams around it, as the controller calls them).  The writer
     * puts a half file into the temporary file, says so, waits for the
     * release, then writes the real save (or fails).
     */
    private final class HeldAutosave {

        final java.util.concurrent.CountDownLatch half
            = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch release
            = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.atomic.AtomicBoolean written
            = new java.util.concurrent.atomic.AtomicBoolean(false);
        final List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        /** How long the writing thread waited for its own autosave (ms), -1 unasked. */
        final java.util.concurrent.atomic.AtomicLong selfWait
            = new java.util.concurrent.atomic.AtomicLong(-1L);
        final Thread thread;

        HeldAutosave(FreeColClient fcc, boolean turnChange, boolean fail) {
            gui.autosaveWrap = real -> tmp -> {
                Files.write(tmp.toPath(), "PK half".getBytes(StandardCharsets.UTF_8));
                this.half.countDown();
                final long s = System.nanoTime();
                gui.awaitAutosave();
                gui.autosaveGate.awaitIdle(20000L);
                this.selfWait.set((System.nanoTime() - s) / 1_000_000L);
                try {
                    if (!this.release.await(60, java.util.concurrent.TimeUnit.SECONDS)) {
                        throw new IOException("never released (test)");
                    }
                } catch (InterruptedException e) {
                    throw new IOException(e);
                }
                if (fail) throw new IOException("disk full (test)");
                real.write(tmp);
                this.written.set(true);
            };
            final Player me = fcc.getMyPlayer();
            this.thread = new Thread(() -> {
                    try {
                        if (turnChange) {
                            fcc.getInGameController().setCurrentPlayerHandler(me);
                        } else {
                            gui.autosaveStarting();
                            try {
                                fcc.getInGameController().autoSaveGame();
                            } finally {
                                gui.autosaveFinished();
                            }
                        }
                    } catch (Throwable e) {
                        this.errors.add(e);
                    }
                }, "test network thread");
            this.thread.start();
        }

        void awaitHalf() throws InterruptedException {
            assertTrue("the autosave never began",
                this.half.await(30, java.util.concurrent.TimeUnit.SECONDS));
        }

        void finish() throws InterruptedException {
            this.release.countDown();
            this.thread.join(60000L);
            assertFalse("the network thread hangs", this.thread.isAlive());
            gui.autosaveWrap = null;
        }
    }

    /** The names in a folder that are a write's temporary files. */
    private static List<String> temporaries(File dir) {
        final List<String> out = new ArrayList<>();
        for (String n : names(dir)) if (n.endsWith(".tmp")) out.add(n);
        return out;
    }

    /** Wait (at most 10 s) until the event thread waits for the autosave. */
    private void awaitTheWait() throws Exception {
        for (int i = 0; i < 1000 && !this.gui.awaitingAutosave(); i++) Thread.sleep(10L);
        assertTrue("nothing waits for the autosave", this.gui.awaitingAutosave());
    }

    /** Whether the event thread handles an event within 10 s. */
    private static boolean eventThreadAnswers() throws InterruptedException {
        final java.util.concurrent.CountDownLatch ping
            = new java.util.concurrent.CountDownLatch(1);
        javax.swing.SwingUtilities.invokeLater(ping::countDown);
        return ping.await(10, java.util.concurrent.TimeUnit.SECONDS);
    }

    /** The prompter's boxes so far, read on the event thread. */
    private static List<String> idsOnTheEventThread(Answers a) throws Exception {
        final List<String> out = new ArrayList<>();
        javax.swing.SwingUtilities.invokeAndWait(() -> out.addAll(a.ids()));
        return out;
    }

    /**
     * Row 10 through FreeCol's real autosave, held half-way (part Z1, C1):
     * while the turn's save is written row 10 keeps the last turn's save
     * whole (FreeCol had moved it away and wrote into the file), the
     * before-last-turn autosave is untouched, the new save is a temporary
     * file; then the last turn's save is the before-last-turn autosave
     * (bytes and time) and row 10 the new turn.  An autosave that fails
     * keeps both rows' files as they were, still makes the turn's numbered
     * copy, and leaves no temporary file.
     */
    public void testTheTurnsAutosaveIsWrittenInOneStep() throws Exception {
        final FreeColClient fcc = gameClient(baseOptions());
        final Game game = fcc.getGame();
        final File last = new File(this.autosaves, "Autosave-last-turn.fsg");
        final File before = new File(this.autosaves, "Autosave-before-last-turn.fsg");
        game.setTurn(turnOf(1495));
        autoSave(fcc);
        assertTrue(last.setLastModified(last.lastModified() - 120000L));
        final byte[] turn1495 = Files.readAllBytes(last.toPath());
        final long time1495 = last.lastModified();

        game.setTurn(turnOf(1496));
        HeldAutosave h = new HeldAutosave(fcc, false, false);
        h.awaitHalf();
        assertTrue(Arrays.equals(turn1495, Files.readAllBytes(last.toPath())));
        assertEquals(time1495, last.lastModified());
        assertFalse(before.exists());
        assertEquals(1, temporaries(this.autosaves).size());
        assertTrue(this.gui.autosaveGate.running());
        h.finish();
        assertTrue(h.errors.toString(), h.errors.isEmpty());
        assertTrue(h.written.get());
        assertEquals(game.getTurn().getNumber(), ClassicSaveSlots.info(last).turn);
        assertTrue(Arrays.equals(turn1495, Files.readAllBytes(before.toPath())));
        assertEquals(time1495, before.lastModified());
        assertEquals(new ArrayList<String>(), temporaries(this.autosaves));
        assertFalse(this.gui.autosaveGate.running());
        // The writing thread never waited for its own autosave.
        assertTrue("waited " + h.selfWait.get() + " ms",
                   h.selfWait.get() >= 0L && h.selfWait.get() < 2000L);

        // A failing autosave.
        assertTrue(last.setLastModified(last.lastModified() - 60000L));
        final byte[] turn1496 = Files.readAllBytes(last.toPath());
        final long time1496 = last.lastModified();
        final byte[] beforeBytes = Files.readAllBytes(before.toPath());
        final int files = names(this.autosaves).size();
        game.setTurn(turnOf(1497));
        h = new HeldAutosave(fcc, false, true);
        h.awaitHalf();
        h.finish();
        assertTrue(h.errors.toString(), h.errors.isEmpty());
        assertTrue(Arrays.equals(turn1496, Files.readAllBytes(last.toPath())));
        assertEquals(time1496, last.lastModified());
        assertTrue(Arrays.equals(beforeBytes, Files.readAllBytes(before.toPath())));
        assertEquals("the numbered copy of 1497", files + 1, names(this.autosaves).size());
        assertEquals(new ArrayList<String>(), temporaries(this.autosaves));
        assertFalse(this.gui.autosaveGate.running());
    }

    /**
     * «Spiel laden» during the turn start's autosave, through FreeCol's
     * real turn change on a thread of its own (part Z1, C1): the gate
     * closes before the turn becomes ours (the current player is still the
     * French when it closes), and once the turn is ours (the AI-phase
     * guard lets the boxes through) the load box waits for the autosave:
     * no box meanwhile, the event thread goes on handling events, a second
     * request (the save box) is dropped; once the autosave is written the
     * box comes, its row 10 shows the new turn, and the load and its
     * switch come after the autosave.
     */
    public void testALoadWaitsForTheAutosave() throws Exception {
        final FreeColClient fcc = gameClient(baseOptions());
        fcc.setSinglePlayer(true);
        final Game game = fcc.getGame();
        final ClassicSaveSlots slots = ClassicSaveSlots.of(fcc.getClientOptions());
        ClassicSaveSlots.writeAtomic(slots.slotFile(2),
            f -> fcc.getFreeColServer().saveGame(f, fcc.getClientOptions(), (Unit) null));
        game.setTurn(turnOf(1495));
        autoSave(fcc);
        game.setTurn(turnOf(1496));
        final Player french = getServerPlayer(game, "model.nation.french");
        game.setCurrentPlayer(french);
        final Answers a = new Answers(2);
        this.gui.prompter = a;

        final HeldAutosave h = new HeldAutosave(fcc, true, false);
        h.awaitHalf();
        assertEquals(Arrays.asList(french), new ArrayList<>(this.gui.currentAtAutosave));
        assertTrue(this.gui.autosaveGate.running());
        assertFalse("the turn is ours while the autosave is written", this.gui.othersMoving());
        final java.util.concurrent.atomic.AtomicReference<Boolean> loaded
            = new java.util.concurrent.atomic.AtomicReference<>();
        javax.swing.SwingUtilities.invokeLater(() -> loaded.set(this.gui.showLoadBox()));
        awaitTheWait();
        assertTrue("the event thread is frozen", eventThreadAnswers());
        assertEquals(new ArrayList<String>(), idsOnTheEventThread(a));
        assertTrue(this.gui.switched.isEmpty());
        // A second request while the first waits: dropped, no box.
        final java.util.concurrent.atomic.AtomicInteger second
            = new java.util.concurrent.atomic.AtomicInteger(99);
        javax.swing.SwingUtilities.invokeAndWait(() -> second.set(this.gui.showSaveBox()));
        assertEquals(-1, second.get());
        assertEquals(new ArrayList<String>(), idsOnTheEventThread(a));
        assertNull(loaded.get());

        h.finish();
        for (int i = 0; i < 1000 && loaded.get() == null; i++) Thread.sleep(10L);
        assertEquals(Boolean.TRUE, loaded.get());
        assertTrue(h.written.get());
        assertEquals(Arrays.asList(slots.slotFile(2)), this.gui.switched);
        assertEquals(Arrays.asList(Boolean.FALSE), this.gui.autosaveAtSwitch);
        assertEquals(Arrays.asList(ClassicSaveSlots.LOAD_SECTION, ClassicSaveSlots.LOAD_GOOD),
                     idsOnTheEventThread(a));
        final String row10 = a.boxes.get(0).plainRows()[9];
        assertTrue(row10, row10.contains("1496"));
        assertFalse(this.gui.autosaveGate.running());
        assertEquals(game.getTurn().getNumber(),
                     ClassicSaveSlots.info(slots.slotFile(ClassicSaveSlots.LAST_TURN_ROW)).turn);
        if (!h.errors.isEmpty()) {
            System.err.println(getClass().getSimpleName()
                + ": the headless turn change after its autosave: " + h.errors);
        }
    }

    /**
     * A save box answered while the turn start's autosave begins (part Z1):
     * the row's write waits until the autosave is written -- the row is
     * not there while it waits, and comes after it.
     */
    public void testASaveWaitsForTheAutosave() throws Exception {
        final FreeColClient fcc = gameClient(baseOptions());
        final Game game = fcc.getGame();
        game.setTurn(turnOf(1495));
        final File row3 = new File(this.saves, "COLONY02.fsg");
        final HeldAutosave[] held = new HeldAutosave[1];
        final List<String> asked = Collections.synchronizedList(new ArrayList<>());
        this.gui.prompter = r -> {
            asked.add(r.id);
            if (r.isNotice()) return 0;
            // The box is answered as the autosave begins.
            held[0] = new HeldAutosave(fcc, false, false);
            try {
                held[0].awaitHalf();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return 2;
        };
        final java.util.concurrent.atomic.AtomicInteger saved
            = new java.util.concurrent.atomic.AtomicInteger(99);
        javax.swing.SwingUtilities.invokeLater(() -> saved.set(this.gui.showSaveBox()));
        awaitTheWait();
        assertTrue("the event thread is frozen", eventThreadAnswers());
        assertFalse("the row is written while the autosave is", row3.exists());
        assertEquals(99, saved.get());
        held[0].finish();
        for (int i = 0; i < 1000 && saved.get() == 99; i++) Thread.sleep(10L);
        assertEquals(2, saved.get());
        assertTrue(row3.isFile());
        assertTrue(held[0].written.get());
        assertEquals(Arrays.asList(ClassicSaveSlots.SAVE_SECTION, ClassicSaveSlots.SAVE_GOOD),
                     new ArrayList<>(asked));
    }

    /**
     * A load box answered as the turn start's autosave begins (part Z1):
     * the load (its notice and the switch, which stops the server) waits
     * until the autosave is written, and comes after it.
     */
    public void testALoadAnsweredAsTheAutosaveBeginsWaits() throws Exception {
        final FreeColClient fcc = gameClient(baseOptions());
        final Game game = fcc.getGame();
        final ClassicSaveSlots slots = ClassicSaveSlots.of(fcc.getClientOptions());
        ClassicSaveSlots.writeAtomic(slots.slotFile(2),
            f -> fcc.getFreeColServer().saveGame(f, fcc.getClientOptions(), (Unit) null));
        game.setTurn(turnOf(1495));
        final HeldAutosave[] held = new HeldAutosave[1];
        final List<String> asked = Collections.synchronizedList(new ArrayList<>());
        this.gui.prompter = r -> {
            asked.add(r.id);
            if (r.isNotice()) return 0;
            held[0] = new HeldAutosave(fcc, false, false);
            try {
                held[0].awaitHalf();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return 2;
        };
        final java.util.concurrent.atomic.AtomicReference<Boolean> loaded
            = new java.util.concurrent.atomic.AtomicReference<>();
        javax.swing.SwingUtilities.invokeLater(() -> loaded.set(this.gui.showLoadBox()));
        awaitTheWait();
        assertTrue("the event thread is frozen", eventThreadAnswers());
        javax.swing.SwingUtilities.invokeAndWait(() -> {
                assertTrue(this.gui.switched.isEmpty());
            });
        assertEquals(Arrays.asList(ClassicSaveSlots.LOAD_SECTION), new ArrayList<>(asked));
        held[0].finish();
        for (int i = 0; i < 1000 && loaded.get() == null; i++) Thread.sleep(10L);
        assertEquals(Boolean.TRUE, loaded.get());
        assertEquals(Arrays.asList(slots.slotFile(2)), this.gui.switched);
        assertEquals(Arrays.asList(Boolean.FALSE), this.gui.autosaveAtSwitch);
        assertEquals(Arrays.asList(ClassicSaveSlots.LOAD_SECTION, ClassicSaveSlots.LOAD_GOOD),
                     new ArrayList<>(asked));
        assertTrue(held[0].written.get());
    }

    /**
     * An autosave that hangs (part Z1): the load waits only its bound
     * ({@code autosaveWaitMs}, here 300 ms), then goes on; row 10 is the
     * last turn's save, whole (the hanging save is only a temporary file).
     */
    public void testTheWaitIsBounded() throws Exception {
        final FreeColClient fcc = gameClient(baseOptions());
        final Game game = fcc.getGame();
        game.setTurn(turnOf(1495));
        autoSave(fcc);
        final File last = new File(this.autosaves, "Autosave-last-turn.fsg");
        final byte[] turn1495 = Files.readAllBytes(last.toPath());
        game.setTurn(turnOf(1496));
        this.gui.autosaveWaitMs = 300L;
        final Answers a = new Answers(9);
        this.gui.prompter = a;
        final HeldAutosave h = new HeldAutosave(fcc, false, false);
        try {
            h.awaitHalf();
            final java.util.concurrent.atomic.AtomicLong ms
                = new java.util.concurrent.atomic.AtomicLong(-1L);
            final java.util.concurrent.atomic.AtomicBoolean loaded
                = new java.util.concurrent.atomic.AtomicBoolean(false);
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                    final long s = System.nanoTime();
                    loaded.set(this.gui.showLoadBox());
                    ms.set((System.nanoTime() - s) / 1_000_000L);
                });
            assertTrue(loaded.get());
            // The box waited 300 ms, the load once more.
            assertTrue("waited " + ms.get() + " ms", ms.get() >= 500L && ms.get() < 20000L);
            assertEquals(Arrays.asList(last), this.gui.switched);
            assertEquals(Arrays.asList(Boolean.TRUE), this.gui.autosaveAtSwitch);
            assertTrue(Arrays.equals(turn1495, Files.readAllBytes(last.toPath())));
        } finally {
            h.finish();
        }
        assertEquals(game.getTurn().getNumber(), ClassicSaveSlots.info(last).turn);
    }

    /**
     * Every stop of the server (the load's switch, the quit, back to the
     * title) waits for the turn start's autosave (part Z1, C1;
     * {@code FreeColClient.stopServer}): the server runs on until the
     * autosave is written, the event thread goes on meanwhile; then the
     * turn's save is row 10 and the server is stopped.
     */
    public void testAServerStopWaitsForTheAutosave() throws Exception {
        final FreeColClient fcc = gameClient(baseOptions());
        final Game game = fcc.getGame();
        game.setTurn(turnOf(1495));
        final File last = new File(this.autosaves, "Autosave-last-turn.fsg");
        final HeldAutosave h = new HeldAutosave(fcc, false, false);
        h.awaitHalf();
        final java.util.concurrent.CountDownLatch stopped
            = new java.util.concurrent.CountDownLatch(1);
        javax.swing.SwingUtilities.invokeLater(() -> {
                fcc.stopServer();
                stopped.countDown();
            });
        assertFalse(stopped.await(400, java.util.concurrent.TimeUnit.MILLISECONDS));
        assertTrue("the event thread is frozen", eventThreadAnswers());
        assertNotNull("the server stopped during the autosave", fcc.getFreeColServer());
        assertFalse(last.exists());
        h.finish();
        assertTrue(stopped.await(30, java.util.concurrent.TimeUnit.SECONDS));
        assertNull(fcc.getFreeColServer());
        assertTrue(h.errors.toString(), h.errors.isEmpty());
        assertEquals(game.getTurn().getNumber(), ClassicSaveSlots.info(last).turn);
        // FreeCol's own GUI does not wait (its seam is empty).
        new net.sf.freecol.client.gui.GUI(null).awaitAutosave();
    }

    /**
     * The gate opens again when the turn start's autosave throws (part
     * Z1): FreeCol's controller closes it in a {@code finally}, so a failed
     * autosave never leaves the boxes waiting.
     */
    public void testTheGateOpensAfterAFailedAutosave() throws Exception {
        final FreeColClient fcc = gameClient(baseOptions());
        fcc.setSinglePlayer(true);
        final Game game = fcc.getGame();
        game.setTurn(turnOf(1495));
        game.setCurrentPlayer(getServerPlayer(game, "model.nation.french"));
        final IllegalStateException crash = new IllegalStateException("crash (test)");
        this.gui.autosaveCrash = crash;
        final List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        final Thread t = new Thread(() -> {
                try {
                    fcc.getInGameController().setCurrentPlayerHandler(fcc.getMyPlayer());
                } catch (Throwable e) {
                    errors.add(e);
                }
            }, "test network thread");
        t.start();
        t.join(60000L);
        assertFalse(t.isAlive());
        assertEquals(Arrays.asList(crash), errors);
        assertEquals(1, this.gui.currentAtAutosave.size());
        assertFalse("the gate stays closed", this.gui.autosaveGate.running());
        assertFalse(new File(this.autosaves, "Autosave-last-turn.fsg").exists());
        this.gui.autosaveCrash = null;
        // The boxes work at once (no wait).
        final Answers a = new Answers();
        this.gui.prompter = a;
        assertFalse(this.gui.showLoadBox());
        assertEquals(Arrays.asList(ClassicSaveSlots.LOAD_SECTION), a.ids());
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
     * The 1492 save's hand-over (D-SL17): a new single-player game marks
     * its first turn in {@code showStartGamePanel}, and the game view's
     * build ({@code reconnectGUI}, here its step
     * {@code autosaveFirstTurnIfPending}) saves it into row 10 once; a load
     * clears the mark, so a loaded game's view never saves its turn again.
     */
    public void testTheFirstTurnHandOver() throws Exception {
        final FreeColClient fcc = gameClient(baseOptions());
        final Game game = fcc.getGame();
        final File last = new File(this.autosaves, "Autosave-last-turn.fsg");
        game.setTurn(new Turn(1));
        fcc.changeClientState(true);
        this.gui.prompter = new Answers();
        assertFalse(this.gui.autosaveFirstTurnIfPending());
        startGame(fcc);
        assertFalse(last.exists());
        assertTrue(this.gui.autosaveFirstTurnIfPending());
        assertTrue(last.isFile());
        assertEquals(1, ClassicSaveSlots.info(last).turn);
        assertFalse("once", this.gui.autosaveFirstTurnIfPending());
        // A load after the start: no first-turn save at its view.
        startGame(fcc);
        javax.swing.SwingUtilities.invokeAndWait(() ->
            this.gui.loadSavedGame(new File(this.saves, "COLONY02.fsg")));
        assertFalse(this.gui.autosaveFirstTurnIfPending());
        for (int i = 0; i < 3; i++) javax.swing.SwingUtilities.invokeAndWait(() -> {});
    }

    /**
     * {@code showStartGamePanel} of a single-player game (its launch request
     * needs a connection the rig has not: only the marks before it count).
     */
    private void startGame(FreeColClient fcc) {
        try {
            this.gui.showStartGamePanel(fcc.getGame(), fcc.getMyPlayer(), true);
        } catch (RuntimeException e) {
            // the launch request without a server connection
        }
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
        // PageDown: every save; a file there that is no save says
        // @LOADNOT with its name and loads nothing, then the page of all
        // saves again; Escape back to the load page.
        key(p, "classic_menuPageDown");
        assertEquals(ClassicMainMenuPanel.Mode.ARCHIVE, p.mode());
        final int broken = p.archiveFiles().indexOf(slots.slotFile(5));
        assertTrue(p.archiveFiles().toString(), broken >= 0);
        for (int i = 0; i < broken; i++) key(p, "classic_menuDown");
        assertEquals(broken, p.archiveRow());
        key(p, "classic_menuEnter");
        assertEquals(ClassicMainMenuPanel.Mode.NOTICE, p.mode());
        assertTrue(String.join(" ", p.noticeLines()),
                   String.join(" ", p.noticeLines()).contains("COLONY05.FSG"));
        click(p, new Point(10, 10));
        assertEquals(ClassicMainMenuPanel.Mode.ARCHIVE, p.mode());
        assertTrue(loaded.isEmpty());
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


    /**
     * The title's load page takes the client's names of FreeCol's
     * last-turn autosave, as the in-game boxes do: row 10 is the same file
     * on the page and in game.
     */
    public void testTheTitleUsesTheClientsNames() throws Exception {
        final ClientOptions co = baseOptions();
        co.getOption(ClientOptions.LAST_TURN_NAME, TextOption.class).setValue("zuletzt");
        final FreeColClient fcc = gameClient(co);
        final File row10 = new File(this.autosaves, "Autosave-zuletzt.fsg");
        assertEquals(row10, ClassicSaveSlots.of(fcc.getClientOptions()).slotFile(9));
        assertEquals(row10, this.gui.titleSaveSlots().slotFile(9));
    }

    /**
     * The page of all saves (PageDown, D-SL5): manual saves, then
     * autosaves, then the rows' kept copies ({@code save/ersetzt}), each
     * newest first; a long label keeps its season and year with the
     * leader's name cut, as the ten rows (D-SL8; the acceptance of part SL
     * saw "... der Holl., Frühjah" there, every numbered copy of one game
     * alike): with the pack's FONTTINY "Entdecker Michiel De R... der
     * Holl., Frühjahr 1496".
     */
    public void testThePageOfAllSavesKeepsTheYear() throws Exception {
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final ClassicMainMenuPanel p = new ClassicMainMenuPanel(
            new ClassicMainMenuPanel.Actions() {
                @Override public void newWorld(ClassicGUI.NewWorldSetup s) {}
                @Override public void loadGame(File file) {}
                @Override public void hallOfFame() {}
                @Override public void quit() {}
            });
        if (pack != null) p.useAssets(ClassicMainMenuPanel.MenuAssets.fromPackFiles(pack));
        final ClassicSaveSlots slots = new ClassicSaveSlots(this.saves, this.autosaves,
                                                            "Autosave-last-turn.fsg");
        p.useSaveSlots(() -> slots);
        final File numbered = new File(this.autosaves, "Autosave-4d46fd9d_Niederländisch_1496.fsg");
        final File row2 = slots.slotFile(1);
        final File keptDir = new File(this.saves, ClassicSaveSlots.REPLACED_FOLDER);
        assertTrue(keptDir.mkdirs());
        final File kept = new File(keptDir, "COLONY01_2026-10-17_031205.fsg");
        try {
            Messages.loadMessageBundle(Locale.GERMANY);
            ClassicSaveSlotsTest.writeSave(numbered, "Michiel De Ruyter", 5,
                "model.difficulty.veryEasy", "model.nation.dutch");
            ClassicSaveSlotsTest.writeSave(row2, "Dago", 368,
                "model.difficulty.veryEasy", "model.nation.dutch");
            ClassicSaveSlotsTest.writeSave(kept, "Probelauf", 14,
                "model.difficulty.veryEasy", "model.nation.dutch");
            p.showTitle(null);
            click(p, centre(ClassicMenuBox.rowHitRect(
                ClassicMainMenuPanel.titleBounds(5), 1, ClassicMainMenuPanel.ITEM_LOAD)));
            key(p, "classic_menuPageDown");
            assertEquals(ClassicMainMenuPanel.Mode.ARCHIVE, p.mode());
            assertEquals(Arrays.asList(row2, numbered, kept), p.archiveFiles());
            // The labels come from the label thread.
            List<String> rows = p.archiveRows();
            for (int i = 0; i < 200 && !String.join("|", rows).contains("Probelauf"); i++) {
                Thread.sleep(25);
                javax.swing.SwingUtilities.invokeAndWait(() -> {});
                rows = p.archiveRows();
            }
            assertEquals(3, rows.size());
            assertEquals("Entdecker Dago der Holl., Herbst 1729", rows.get(0));
            assertTrue(rows.get(1), rows.get(1).endsWith(", Frühjahr 1496"));
            assertTrue(rows.get(2), rows.get(2).contains("Probelauf"));
            final ClassicFont tiny = (pack == null) ? null : pack.font(ClassicFont.TINY);
            if (tiny == null) {
                System.err.println(getClass().getSimpleName()
                    + ": the page's cut not measured, no pack");
                return;
            }
            assertEquals("Entdecker Michiel De R... der Holl., Frühjahr 1496", rows.get(1));
            assertTrue(tiny.stringWidth(rows.get(1)) <= ClassicSaveSlots.ROW_TEXT_MAX);
        } finally {
            Messages.loadMessageBundle(Locale.US);
        }
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
