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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import net.sf.freecol.client.ClientOptions;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.io.FreeColDirectories;
import net.sf.freecol.common.option.TextOption;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * Part SL, the original's ten save rows ({@link ClassicSaveSlots}; spec
 * {@code freecol-spike-results/prep2/SL-saveload.md}): the row -&gt; file
 * mapping on temporary folders, the save folder's {@code autosave} guard,
 * the labels with "(EMPTY)" rows and their cache, the decade rule (H1),
 * the label cut of the Dutch default leader (D-SL8, with the pack's
 * FONTTINY and the German texts), and the one-step write that never loses
 * the row's old save (a failing writer, a save that cannot be read back, a
 * read-only or open row, a row read just before).
 */
public class ClassicSaveSlotsTest extends FreeColTestCase {

    private File root, saves, autosaves;
    private Object savedSaveDir;

    private static Field dirField(String name) throws Exception {
        final Field f = FreeColDirectories.class.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.root = Files.createTempDirectory("sl-slots-").toFile();
        this.saves = new File(this.root, "save");
        this.autosaves = new File(this.saves, "autosave");
        assertTrue(this.autosaves.mkdirs());
        this.savedSaveDir = dirField("saveDirectory").get(null);
    }

    @Override
    protected void tearDown() throws Exception {
        dirField("saveDirectory").set(null, this.savedSaveDir);
        removeTree(this.root);   // this test's own temporary files only
        super.tearDown();
    }

    static void removeTree(File f) {
        if (f == null) return;
        final File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) removeTree(k);
        f.setWritable(true);
        f.delete();
    }

    private ClassicSaveSlots slots() {
        return new ClassicSaveSlots(this.saves, this.autosaves, "Autosave-last-turn.fsg");
    }

    /**
     * A save's head as FreeCol writes it, up to the owner's player: what
     * {@link ClassicSaveGames#peek} reads (a zip with {@code savegame.xml}).
     */
    static void writeSave(File f, String owner, int turn, String difficulty,
                          String nation) throws IOException {
        final String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<savedGame owner=\"" + owner + "\" singlePlayer=\"true\" version=\"15\">\n"
            + "<game id=\"game\" turn=\"" + turn + "\">\n"
            + "<freecol-specification id=\"levi\" difficulty-level=\"" + difficulty + "\">\n"
            + "<integerOption id=\"model.option.startingYear\" value=\"1492\"/>\n"
            + "<integerOption id=\"model.option.seasonYear\" value=\"1600\"/>\n"
            + "<integerOption id=\"model.option.seasons\" value=\"2\"/>\n"
            + "</freecol-specification>\n"
            + "<player id=\"player:1\" username=\"" + owner + "\" nationId=\"" + nation + "\"/>\n"
            + "</game>\n</savedGame>\n";
        try (OutputStream os = Files.newOutputStream(f.toPath());
             ZipOutputStream zip = new ZipOutputStream(os)) {
            zip.putNextEntry(new ZipEntry("savegame.properties"));
            zip.write("map.width=40\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("savegame.xml"));
            zip.write(xml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

    private static byte[] bytes(File f) throws IOException {
        return Files.readAllBytes(f.toPath());
    }

    /** The names in a folder that are not saves (a write's temporary files). */
    private static List<String> strays(File dir) {
        final List<String> out = new ArrayList<>();
        final String[] names = dir.list();
        if (names != null) {
            for (String n : names) {
                if (!n.toLowerCase(Locale.ROOT).endsWith(".fsg")
                    && !new File(dir, n).isDirectory()) out.add(n);
            }
        }
        return out;
    }


    /**
     * Rows 1-8 are {@code save/COLONY00.fsg} .. {@code COLONY07.fsg}, row 9
     * {@code save/autosave/COLONY08.fsg}, row 10 FreeCol's last-turn
     * autosave (V E6, E8; spec section 3); the messages name the
     * original's files "COLONY00.SAV" .. "COLONY09.SAV" (D-SL1).
     */
    public void testTheTenRowsAndTheirFiles() {
        final ClassicSaveSlots s = slots();
        for (int i = 0; i < 8; i++) {
            assertEquals(new File(this.saves, "COLONY0" + i + ".fsg"), s.slotFile(i));
        }
        assertEquals(new File(this.autosaves, "COLONY08.fsg"), s.slotFile(8));
        assertEquals(new File(this.autosaves, "Autosave-last-turn.fsg"), s.slotFile(9));
        assertNull(s.slotFile(-1));
        assertNull(s.slotFile(10));
        assertEquals("COLONY00.SAV", ClassicSaveSlots.displayName(0));
        assertEquals("COLONY08.SAV", ClassicSaveSlots.displayName(8));
        assertEquals("COLONY09.SAV", ClassicSaveSlots.displayName(9));
        assertEquals(8, ClassicSaveSlots.SAVE_ROWS);
        assertEquals(10, ClassicSaveSlots.LOAD_ROWS);
        assertEquals("AUTOSAVE-4D46FD9D NIEDERLÄNDISCH 1496.FSG",
            ClassicSaveSlots.displayName(new File("Autosave-4d46fd9d_Niederländisch_1496.fsg")));
        assertEquals("Autosave-last-turn.fsg",
                     ClassicSaveSlots.lastTurnName("Autosave", "last-turn"));
    }

    /**
     * The game's rows follow FreeCol's save folder, but never its
     * {@code autosave} folder (FreeCol's own load of an autosave makes that
     * the save folder; pitfall 1), and the client's autosave names.
     */
    public void testTheGamesRowsAndTheAutosaveGuard() throws Exception {
        dirField("saveDirectory").set(null, this.saves);
        ClassicSaveSlots s = ClassicSaveSlots.of(null);
        assertEquals(new File(this.saves, "COLONY03.fsg"), s.slotFile(3));
        assertEquals(new File(this.autosaves, "COLONY08.fsg"), s.slotFile(8));
        assertEquals(new File(this.autosaves, "Autosave-last-turn.fsg"), s.slotFile(9));
        // An older session moved the save folder into autosave: the rows
        // stay where they belong.
        dirField("saveDirectory").set(null, this.autosaves);
        s = ClassicSaveSlots.of(null);
        assertEquals(new File(this.saves, "COLONY03.fsg"), s.slotFile(3));
        assertEquals(new File(this.autosaves, "COLONY08.fsg"), s.slotFile(8));
        assertEquals(this.saves, ClassicSaveSlots.saveRoot(this.autosaves));
        assertEquals(this.saves, ClassicSaveSlots.saveRoot(this.saves));
        assertNull(ClassicSaveSlots.saveRoot(null));
        // The client's names of the last-turn autosave.
        final ClientOptions co = new ClientOptions();
        co.load(FreeColDirectories.getBaseClientOptionsFile());
        co.fixClientOptions();
        co.getOption(ClientOptions.AUTO_SAVE_PREFIX, TextOption.class).setValue("Auto");
        co.getOption(ClientOptions.LAST_TURN_NAME, TextOption.class).setValue("zuletzt");
        s = ClassicSaveSlots.of(co);
        assertEquals(new File(this.autosaves, "Auto-zuletzt.fsg"), s.slotFile(9));
    }

    /**
     * The labels: "(EMPTY)" for a row without its file (V E7), the save's
     * label otherwise, its file name and date when its head cannot be
     * read; a rewritten row is read again (the cache goes by time and
     * size), and only rows 1-8 are the save box's.
     */
    public void testTheLabels() throws Exception {
        final ClassicSaveSlots s = slots();
        List<String> load = s.labels(10, null, ClassicSaveSlots.ROW_TEXT_MAX);
        assertEquals(10, load.size());
        assertEquals(Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
                     ClassicSaveSlots.emptyRows(load));
        assertEquals(8, s.labels(8, null, 182).size());
        writeSave(s.slotFile(2), "Dago", 9, "model.difficulty.veryEasy",
                  "model.nation.dutch");
        writeSave(s.slotFile(9), "Raleigh", 1, "model.difficulty.veryEasy",
                  "model.nation.english");
        Files.write(s.slotFile(5).toPath(), "no save".getBytes(StandardCharsets.UTF_8));
        load = s.labels(10, null, 182);
        assertEquals(Arrays.asList(0, 1, 3, 4, 6, 7, 8), ClassicSaveSlots.emptyRows(load));
        assertTrue(load.get(2), load.get(2).contains("Dago"));
        assertTrue(load.get(2), load.get(2).contains("1500"));
        assertTrue(load.get(9), load.get(9).contains("Raleigh"));
        assertTrue(load.get(9), load.get(9).contains("1492"));
        assertTrue(load.get(5), load.get(5).startsWith("COLONY05  "));   // name and date
        assertTrue(s.readable(2));
        assertFalse(s.readable(5));
        assertFalse(s.readable(4));
        assertFalse(s.isEmpty(5));
        assertTrue(s.isEmpty(4));
        // Rewritten (another owner, an older time): read again.
        final long t2 = s.slotFile(2).lastModified();
        writeSave(s.slotFile(2), "Probelauf", 14, "model.difficulty.veryEasy",
                  "model.nation.dutch");
        assertTrue(s.slotFile(2).setLastModified(t2 - 60000L));
        load = s.labels(10, null, 182);
        assertTrue(load.get(2), load.get(2).contains("Probelauf"));
        assertTrue(load.get(2), load.get(2).contains("1505"));
    }

    /**
     * The labels in the original's words (the German texts): 055's and
     * clip006's rows, and the Dutch default leader cut to the row's 182 px
     * with the pack's FONTTINY (I, D-SL8; E14: 189 px uncut), "Herbst
     * 1729" uncut at 180 px.
     */
    public void testTheOriginalsLabelsAndTheCut() {
        try {
            Messages.loadMessageBundle(Locale.GERMANY);
            final ClassicSaveGames.Info dago = new ClassicSaveGames.Info("Dago",
                "model.difficulty.veryEasy", "model.nation.dutch", 368, 1492, 1600, 2);
            final ClassicSaveGames.Info raleigh = new ClassicSaveGames.Info("Walter Raleigh",
                "model.difficulty.veryEasy", "model.nation.english", 1, 1492, 1600, 2);
            final ClassicSaveGames.Info michiel = new ClassicSaveGames.Info("Michiel De Ruyter",
                "model.difficulty.veryEasy", "model.nation.dutch", 5, 1492, 1600, 2);
            final ClassicSaveGames.Info dago1720 = new ClassicSaveGames.Info("Dago",
                "model.difficulty.veryEasy", "model.nation.dutch", 349, 1492, 1600, 2);
            assertEquals("Entdecker Dago der Holl., Herbst 1729",
                         ClassicSaveGames.formatLabel(dago));
            assertEquals("Entdecker Walter Raleigh der Engl., Frühjahr 1492",
                         ClassicSaveGames.formatLabel(raleigh));
            assertEquals("Entdecker Michiel De Ruyter der Holl., Frühjahr 1496",
                         ClassicSaveGames.formatLabel(michiel));
            assertEquals("Entdecker Dago der Holl., Frühjahr 1720",
                         ClassicSaveGames.formatLabel(dago1720));
            // Without a font nothing is cut.
            assertEquals("Entdecker Michiel De Ruyter der Holl., Frühjahr 1496",
                         ClassicSaveSlots.fitLabel(null, michiel, 182));
            final ClassicPackFiles pack = ClassicPackFiles.runtime();
            final ClassicFont tiny = (pack == null) ? null : pack.font(ClassicFont.TINY);
            if (tiny == null) {
                System.err.println(getClass().getSimpleName()
                    + ": the label cut not measured, no pack (ant classic-assets)");
                return;
            }
            assertEquals(189, tiny.stringWidth(ClassicSaveGames.formatLabel(michiel)));
            // 055's row 10 (the capture's ink 71..249).
            assertEquals(180, tiny.stringWidth(ClassicSaveGames.formatLabel(raleigh)));
            final String cut = ClassicSaveSlots.fitLabel(tiny, michiel, 182);
            assertEquals("Entdecker Michiel De R... der Holl., Frühjahr 1496", cut);
            assertEquals(182, tiny.stringWidth(cut));
            assertEquals("Entdecker Dago der Holl., Herbst 1729",
                         ClassicSaveSlots.fitLabel(tiny, dago, 182));
            // The same leader in an autumn: 180 px, it fits (E14).
            final ClassicSaveGames.Info michielAutumn = new ClassicSaveGames.Info(
                "Michiel De Ruyter", "model.difficulty.veryEasy", "model.nation.dutch",
                368, 1492, 1600, 2);
            assertEquals("Entdecker Michiel De Ruyter der Holl., Herbst 1729",
                         ClassicSaveSlots.fitLabel(tiny, michielAutumn, 182));
            assertEquals(180, tiny.stringWidth(ClassicSaveGames.formatLabel(michielAutumn)));
            // A name no cut can save: the end of the label goes.
            final String tight = ClassicSaveSlots.fitLabel(tiny, michiel, 100);
            assertTrue(tight, tight.endsWith("...") && tiny.stringWidth(tight) <= 100);
        } finally {
            Messages.loadMessageBundle(Locale.US);
        }
    }

    /**
     * The decade rule (I, H1, E8; spec section 3): the first turn of a year
     * divisible by 10 -- before the seasons every turn, after them the
     * spring only.
     */
    public void testTheDecadeRule() {
        final Object[][] table = {
            { 1492, -1, false }, { 1500, -1, true }, { 1501, -1, false },
            { 1570, -1, true }, { 1571, -1, false }, { 1599, -1, false },
            { 1600, 0, true }, { 1600, 1, false }, { 1610, 0, true },
            { 1720, 0, true }, { 1729, 1, false }, { 1730, 0, true }, { 1730, 1, false },
        };
        for (Object[] r : table) {
            assertEquals(Arrays.toString(r), r[2],
                ClassicSaveSlots.isDecadeTurn((Integer) r[0], (Integer) r[1]));
        }
        assertFalse(ClassicSaveSlots.isDecadeTurn(null));
    }

    /**
     * A row is written in one step: the new save replaces the old one, and
     * no temporary file stays; the folder is made when it is missing.
     */
    public void testAWriteReplacesTheRow() throws Exception {
        final ClassicSaveSlots s = slots();
        final File row = s.slotFile(3);
        writeSave(row, "Alt", 9, "model.difficulty.veryEasy", "model.nation.dutch");
        ClassicSaveSlots.writeAtomic(row, f -> writeSave(f, "Neu", 14,
            "model.difficulty.veryEasy", "model.nation.dutch"));
        assertEquals("Neu", ClassicSaveGames.peek(row).owner);
        assertEquals(new ArrayList<String>(), strays(this.saves));
        removeTree(this.autosaves);
        ClassicSaveSlots.writeAtomic(s.slotFile(8), f -> writeSave(f, "Zehn", 9,
            "model.difficulty.veryEasy", "model.nation.dutch"));
        assertEquals("Zehn", ClassicSaveGames.peek(s.slotFile(8)).owner);
        assertEquals(new ArrayList<String>(), strays(this.autosaves));
    }

    /**
     * A failure never loses the row's old save (pitfall 2): a writer that
     * fails half-way, one that writes no readable save, one that throws a
     * runtime error -- each time the old bytes stay and no temporary file
     * is left.
     */
    public void testAFailedWriteKeepsTheOldRow() throws Exception {
        final ClassicSaveSlots s = slots();
        final File row = s.slotFile(1);
        writeSave(row, "Alt", 9, "model.difficulty.veryEasy", "model.nation.dutch");
        final byte[] old = bytes(row);
        final long oldTime = row.lastModified();
        final ClassicSaveSlots.SaveWriter halfway = f -> {
            Files.write(f.toPath(), Arrays.copyOf(old, old.length / 2));
            throw new IOException("disk full (test)");
        };
        final ClassicSaveSlots.SaveWriter garbage = f ->
            Files.write(f.toPath(), "PK not a save".getBytes(StandardCharsets.UTF_8));
        final ClassicSaveSlots.SaveWriter crash = f -> {
            Files.write(f.toPath(), "x".getBytes(StandardCharsets.UTF_8));
            throw new IllegalStateException("crash (test)");
        };
        for (ClassicSaveSlots.SaveWriter w : Arrays.asList(halfway, garbage, crash)) {
            try {
                ClassicSaveSlots.writeAtomic(row, w);
                fail("the write should fail");
            } catch (IOException | IllegalStateException e) {
                // expected
            }
            assertTrue(Arrays.equals(old, bytes(row)));
            assertEquals(oldTime, row.lastModified());
            assertEquals(new ArrayList<String>(), strays(this.saves));
        }
        // An empty row stays empty after a failed write.
        final File empty = s.slotFile(6);
        try {
            ClassicSaveSlots.writeAtomic(empty, halfway);
            fail("the write should fail");
        } catch (IOException e) {
            // expected
        }
        assertFalse(empty.exists());
        assertEquals(new ArrayList<String>(), strays(this.saves));
    }

    /**
     * A row the system will not let us replace -- read-only (acceptance A9),
     * or open elsewhere -- keeps its old save, and the write says it
     * failed.  Where the file system allows the replace anyway (a read-only
     * file in a writable folder on Linux), the new save is complete.
     */
    public void testALockedRowKeepsItsSave() throws Exception {
        final ClassicSaveSlots s = slots();
        final File row = s.slotFile(5);
        writeSave(row, "Alt", 9, "model.difficulty.veryEasy", "model.nation.dutch");
        final byte[] old = bytes(row);
        final ClassicSaveSlots.SaveWriter neu = f -> writeSave(f, "Neu", 14,
            "model.difficulty.veryEasy", "model.nation.dutch");
        assertTrue(row.setReadOnly());
        boolean refused;
        try {
            ClassicSaveSlots.writeAtomic(row, neu);
            refused = false;
        } catch (IOException e) {
            refused = true;
        }
        if (refused) {
            assertTrue(Arrays.equals(old, bytes(row)));
        } else {
            System.err.println(getClass().getSimpleName()
                + ": this file system replaces a read-only file");
            assertEquals("Neu", ClassicSaveGames.peek(row).owner);
        }
        assertEquals(new ArrayList<String>(), strays(this.saves));
        assertTrue(row.setWritable(true));
        // Open elsewhere (Windows forbids replacing an open file).
        writeSave(row, "Alt", 9, "model.difficulty.veryEasy", "model.nation.dutch");
        final byte[] old2 = bytes(row);
        try (FileInputStream lock = new FileInputStream(row)) {
            try {
                ClassicSaveSlots.writeAtomic(row, neu);
                refused = false;
            } catch (IOException e) {
                refused = true;
            }
        }
        if (refused) {
            assertTrue(Arrays.equals(old2, bytes(row)));
        } else {
            assertEquals("Neu", ClassicSaveGames.peek(row).owner);
        }
        assertEquals(new ArrayList<String>(), strays(this.saves));
    }

    /**
     * A row saved over keeps the save it held (I, the ground rule "never
     * lose a save" where the original's question is unknown, D-SL6): a
     * copy in {@code save/ersetzt}, named by the row and the time of the
     * replacement, with the old bytes and the old file time; a second
     * replacement in the same second gets its own name; an empty row and a
     * failed write keep nothing; without the folder argument nothing (row
     * 9); a copy that cannot be made stops the save, the row keeps its game
     * (part Z1, C2 of part Z's check), and an empty row is still written.
     */
    public void testAReplacedRowIsKept() throws Exception {
        final ClassicSaveSlots s = slots();
        final File keep = s.keepDir();
        assertEquals(new File(this.saves, "ersetzt"), keep);
        final File row = s.slotFile(2);
        ClassicSaveSlots.writeAtomic(row, f -> writeSave(f, "Erst", 9,
            "model.difficulty.veryEasy", "model.nation.dutch"), keep);
        assertFalse("an empty row keeps nothing", keep.exists());
        assertTrue(row.setLastModified(row.lastModified() - 3_600_000L));
        final byte[] erst = bytes(row);
        final long erstTime = row.lastModified();
        ClassicSaveSlots.writeAtomic(row, f -> writeSave(f, "Zweit", 14,
            "model.difficulty.veryEasy", "model.nation.dutch"), keep);
        assertEquals("Zweit", ClassicSaveGames.peek(row).owner);
        String[] kept = keep.list();
        assertEquals(1, kept.length);
        assertTrue(kept[0], kept[0].matches("COLONY02_\\d{4}-\\d\\d-\\d\\d_\\d{6}\\.fsg"));
        assertTrue(Arrays.equals(erst, bytes(new File(keep, kept[0]))));
        assertEquals(erstTime, new File(keep, kept[0]).lastModified());
        final byte[] zweit = bytes(row);
        ClassicSaveSlots.writeAtomic(row, f -> writeSave(f, "Dritt", 15,
            "model.difficulty.veryEasy", "model.nation.dutch"), keep);
        kept = keep.list();
        assertEquals(2, kept.length);
        int both = 0;
        for (String k : kept) {
            final byte[] b = bytes(new File(keep, k));
            if (Arrays.equals(erst, b) || Arrays.equals(zweit, b)) both++;
        }
        assertEquals(2, both);
        // A failed write: the old row stays, no copy of it.
        try {
            ClassicSaveSlots.writeAtomic(row, f -> Files.write(f.toPath(),
                "PK not a save".getBytes(StandardCharsets.UTF_8)), keep);
            fail("the write should fail");
        } catch (IOException e) {
            // expected
        }
        assertEquals(2, keep.list().length);
        assertEquals("Dritt", ClassicSaveGames.peek(row).owner);
        // No folder given (row 9): nothing kept.
        final File nine = s.slotFile(8);
        writeSave(nine, "Neun", 9, "model.difficulty.veryEasy", "model.nation.dutch");
        ClassicSaveSlots.writeAtomic(nine, f -> writeSave(f, "Neun", 19,
            "model.difficulty.veryEasy", "model.nation.dutch"));
        assertEquals(2, keep.list().length);
        assertFalse(new File(this.autosaves, "ersetzt").exists());
        // A folder that cannot be made (a file in its place): no copy, so
        // the row is not saved over (part Z1, C2): the write fails, the
        // row keeps its game and its time, no temporary file is left.
        final File blocked = new File(this.root, "blocked");
        Files.write(blocked.toPath(), "x".getBytes(StandardCharsets.UTF_8));
        assertNull(ClassicSaveSlots.keepReplaced(row, blocked));
        final byte[] dritt = bytes(row);
        final long drittTime = row.lastModified();
        try {
            ClassicSaveSlots.writeAtomic(row, f -> writeSave(f, "Viert", 16,
                "model.difficulty.veryEasy", "model.nation.dutch"), blocked);
            fail("a row whose game cannot be kept is not saved over");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("not saved over"));
        }
        assertTrue(Arrays.equals(dritt, bytes(row)));
        assertEquals(drittTime, row.lastModified());
        assertEquals("Dritt", ClassicSaveGames.peek(row).owner);
        assertEquals(new ArrayList<String>(), strays(this.saves));
        assertEquals(2, keep.list().length);
        // An empty row has no game to keep: it is written all the same.
        final File empty = s.slotFile(7);
        ClassicSaveSlots.writeAtomic(empty, f -> writeSave(f, "Leer", 16,
            "model.difficulty.veryEasy", "model.nation.dutch"), blocked);
        assertEquals("Leer", ClassicSaveGames.peek(empty).owner);
        assertEquals(new ArrayList<String>(), strays(this.saves));
        assertNull(new ClassicSaveSlots(null, null, null).keepDir());
    }

    /**
     * The copy into {@code ersetzt} is made in a temporary file, flushed,
     * then given its name (part Z1): a copy that fails half-way leaves no
     * temporary file and no broken copy there; a read-only row is copied
     * (Windows copies its attribute, which the flush would refuse) and the
     * copy keeps the row's bytes and time.
     */
    public void testTheCopyOfAReplacedRowIsWhole() throws Exception {
        final ClassicSaveSlots s = slots();
        final File keep = s.keepDir();
        // A "row" that cannot be copied as a file (a folder): no copy,
        // nothing left in ersetzt.
        final File folder = new File(this.saves, "COLONY03.fsg");
        assertTrue(folder.mkdirs());
        assertNull(ClassicSaveSlots.keepReplaced(folder, keep));
        assertTrue(keep.isDirectory());
        assertEquals(Arrays.asList(), Arrays.asList(keep.list()));
        // A read-only row: copied whole, with its time.
        final File row = s.slotFile(4);
        writeSave(row, "Alt", 9, "model.difficulty.veryEasy", "model.nation.dutch");
        assertTrue(row.setLastModified(row.lastModified() - 7_200_000L));
        assertTrue(row.setReadOnly());
        try {
            final File copy = ClassicSaveSlots.keepReplaced(row, keep);
            assertNotNull(copy);
            assertTrue(Arrays.equals(bytes(row), bytes(copy)));
            assertEquals(row.lastModified(), copy.lastModified());
            assertEquals(new ArrayList<String>(), strays(keep));
        } finally {
            assertTrue(row.setWritable(true));
        }
    }

    /**
     * Row 10, the turn start's autosave, written in one step (part Z1, C1
     * of part Z's check; FreeCol wrote it straight into the file): while
     * the new save is being written the row keeps its old save whole and
     * the before-last-turn autosave is untouched; then the old row 10 is
     * the before-last-turn autosave (its bytes and time, as FreeCol's move)
     * and the new save is row 10.  A write that fails leaves both as they
     * were; an old row 10 that cannot be read is not copied (the
     * before-last-turn keeps its older game); a before-last-turn that
     * cannot be replaced never stops the turn's save; no temporary file is
     * left.
     */
    public void testTheLastTurnIsWrittenInOneStep() throws Exception {
        final ClassicSaveSlots s = slots();
        final File last = s.slotFile(ClassicSaveSlots.LAST_TURN_ROW);
        final File before = new File(this.autosaves, "Autosave-before-last-turn.fsg");
        writeSave(before, "Vorvorher", 7, "model.difficulty.veryEasy", "model.nation.dutch");
        writeSave(last, "Vorher", 8, "model.difficulty.veryEasy", "model.nation.dutch");
        assertTrue(last.setLastModified(last.lastModified() - 3_600_000L));
        final byte[] old = bytes(last), older = bytes(before);
        final long oldTime = last.lastModified();

        // A slow write, watched half-way.
        final java.util.concurrent.CountDownLatch half
            = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch go
            = new java.util.concurrent.CountDownLatch(1);
        final Throwable[] failed = new Throwable[1];
        final Thread t = new Thread(() -> {
                try {
                    ClassicSaveSlots.writeLastTurn(last, before, f -> {
                            Files.write(f.toPath(), Arrays.copyOf(old, old.length / 2));
                            half.countDown();
                            try {
                                go.await(30, java.util.concurrent.TimeUnit.SECONDS);
                            } catch (InterruptedException e) {
                                throw new IOException(e);
                            }
                            writeSave(f, "Neu", 9, "model.difficulty.veryEasy",
                                      "model.nation.dutch");
                        });
                } catch (Throwable e) {
                    failed[0] = e;
                }
            }, "slow autosave");
        t.start();
        assertTrue(half.await(30, java.util.concurrent.TimeUnit.SECONDS));
        assertTrue(Arrays.equals(old, bytes(last)));
        assertEquals("Vorher", ClassicSaveGames.peek(last).owner);
        assertTrue(Arrays.equals(older, bytes(before)));
        assertEquals(1, strays(this.autosaves).size());       // the temporary file
        go.countDown();
        t.join(30000L);
        assertNull(String.valueOf(failed[0]), failed[0]);
        assertEquals("Neu", ClassicSaveGames.peek(last).owner);
        assertTrue(Arrays.equals(old, bytes(before)));
        assertEquals(oldTime, before.lastModified());
        assertEquals(new ArrayList<String>(), strays(this.autosaves));

        // A write that fails: both as they were.
        final byte[] neu = bytes(last), vorher = bytes(before);
        final long neuTime = last.lastModified(), vorherTime = before.lastModified();
        try {
            ClassicSaveSlots.writeLastTurn(last, before, f -> {
                    Files.write(f.toPath(), Arrays.copyOf(neu, neu.length / 2));
                    throw new IOException("disk full (test)");
                });
            fail("the write should fail");
        } catch (IOException e) {
            // expected
        }
        assertTrue(Arrays.equals(neu, bytes(last)));
        assertEquals(neuTime, last.lastModified());
        assertTrue(Arrays.equals(vorher, bytes(before)));
        assertEquals(vorherTime, before.lastModified());
        assertEquals(new ArrayList<String>(), strays(this.autosaves));

        // A broken row 10: not copied, the before-last-turn keeps its game.
        Files.write(last.toPath(), "PK broken".getBytes(StandardCharsets.UTF_8));
        ClassicSaveSlots.writeLastTurn(last, before, f -> writeSave(f, "Danach", 10,
            "model.difficulty.veryEasy", "model.nation.dutch"));
        assertEquals("Danach", ClassicSaveGames.peek(last).owner);
        assertTrue(Arrays.equals(vorher, bytes(before)));
        assertEquals(new ArrayList<String>(), strays(this.autosaves));

        // A before-last-turn that cannot be replaced (a folder in its
        // place): the turn is saved all the same.
        assertTrue(before.renameTo(new File(this.root, "before.keep")));
        assertTrue(before.mkdirs());
        Files.write(new File(before, "keep.txt").toPath(), "x".getBytes(StandardCharsets.UTF_8));
        ClassicSaveSlots.writeLastTurn(last, before, f -> writeSave(f, "Zuletzt", 11,
            "model.difficulty.veryEasy", "model.nation.dutch"));
        assertEquals("Zuletzt", ClassicSaveGames.peek(last).owner);
        assertTrue(new File(before, "keep.txt").isFile());
        assertEquals(new ArrayList<String>(), strays(this.autosaves));
        // No before-last-turn file given: row 10 alone.
        ClassicSaveSlots.writeLastTurn(last, null, f -> writeSave(f, "Allein", 12,
            "model.difficulty.veryEasy", "model.nation.dutch"));
        assertEquals("Allein", ClassicSaveGames.peek(last).owner);
    }

    /**
     * A row whose label was just read (the box opens with the labels) can
     * be saved over at once: the peek leaves no file open, which on Windows
     * would refuse the replace.
     */
    public void testAReadRowCanBeSavedOver() throws Exception {
        final ClassicSaveSlots s = slots();
        final File row = s.slotFile(0);
        writeSave(row, "Alt", 9, "model.difficulty.veryEasy", "model.nation.dutch");
        assertNotNull(ClassicSaveSlots.info(row));
        assertNotNull(ClassicSaveGames.peek(row));
        assertEquals(1, s.labels(1, null, 182).size());
        for (int i = 0; i < 3; i++) {
            final String who = "Neu" + i;
            ClassicSaveSlots.writeAtomic(row, f -> writeSave(f, who, 14,
                "model.difficulty.veryEasy", "model.nation.dutch"));
            assertEquals(who, ClassicSaveSlots.info(row).owner);
        }
    }
}
