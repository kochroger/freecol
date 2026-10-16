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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.freecol.FreeCol;
import net.sf.freecol.client.ClientOptions;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.io.FreeColDirectories;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Turn;


/**
 * The original's <b>ten save rows</b> (part SL; spec
 * {@code freecol-spike-results/prep2/SL-saveload.md}): eight rows the player
 * saves into and two the game fills by itself, each on one fixed file, so
 * the save box ({@code @SAVEGAME}, 8 rows, {@code opening_054}), the load
 * box ({@code @LOADGAME}, 10 rows, {@code opening_055}) and the title's
 * load page (clip006 #0) show the same rows with the same labels.  Plain
 * files and the labels only, no Swing: the boxes are built by
 * {@code ClassicGUI}, the page by {@code ClassicMainMenuPanel}.
 *
 * <p>Evidence (V seen in a capture or a file, I inferred):
 * <ul>
 *   <li><b>Rows and files (V E6).</b>  Row k (1-based) is the original's
 *       {@code COLONY0(k-1).SAV}: 055's row 1 "Dago ... Herbst 1729" is
 *       COLONY00 (its header says 1729, autumn), clip006's row 3
 *       "Probelauf ... Frühjahr 1505" is COLONY02.  Rows 1-8 are written
 *       only by "Spiel sichern"; here they are {@code <save>/COLONY00.fsg}
 *       .. {@code COLONY07.fsg}.  FreeCol's format and extension, so plain
 *       FreeCol loads them too; the messages name the original's file,
 *       "COLONY02.SAV" (I, decision D-SL1).</li>
 *   <li><b>Row 10, the latest autosave (V E8).</b>  055's row 10 is
 *       "Raleigh ... Frühjahr 1492", a new game's first turn.  Here it is
 *       FreeCol's own last-turn autosave,
 *       {@code <save>/autosave/Autosave-last-turn.fsg} (the name from the
 *       client options), which FreeCol writes at each of our turn starts;
 *       so the game a player of an older build played last is on row 10 at
 *       once.</li>
 *   <li><b>Row 9, the decade autosave (V data, I rule; E8, rule H1).</b>
 *       055's row 9 "Dago Frühjahr 1720", clip006's "Dago Frühjahr 1730",
 *       Roger's COLONY08 1570 against COLONY09 1571: on the first turn of
 *       a year divisible by 10 the autosave goes to row 9 <em>instead of</em>
 *       row 10 (clip006: row 10 still "Probelauf Frühjahr 1513" after the
 *       1729 -&gt; 1730 turn of clip005).  Here
 *       {@code <save>/autosave/COLONY08.fsg}: no "Autosave" prefix, so no
 *       FreeCol housekeeping ever counts it as one of its autosaves.  H3
 *       ("no row-10 save on the first turn after a load") fits the data too;
 *       a clip would settle it (spec section 9, wish 4).</li>
 *   <li><b>"(EMPTY)" (V E7).</b>  A row without its file; the word is
 *       English in the German game too (hard-coded in VICEROY.EXE, not in
 *       GAME.TXT).</li>
 *   <li><b>A label too long for the row (I, D-SL8).</b>  The row has room
 *       for 182 px (E14); the Dutch default leader in spring needs 189
 *       ("Entdecker Michiel De Ruyter der Holl., Frühjahr 1496").  How the
 *       original shows it was not recorded; here the leader's name is cut
 *       with "..." and the season and year stay ({@link #fitLabel}).</li>
 * </ul>
 *
 * <p><b>Never lose a save.</b>  A row is written to a temporary file in
 * the row's own folder, read back, and only then moved over the row's file
 * in one step ({@link #writeAtomic}); a failure at any point leaves the
 * row's old file as it was.  Nothing here deletes a saved game: the only
 * delete is of the temporary file of a write that failed.  The directories
 * come from the constructor, so the tests run on temporary folders; the
 * game's are {@link #of}.  Nothing here ever calls
 * {@code FreeColDirectories.setSavegameFile}, which moves the save folder
 * (pitfall 1 of the spec).
 */
final class ClassicSaveSlots {

    private static final Logger logger = Logger.getLogger(ClassicSaveSlots.class.getName());

    /** The rows of the save box (V {@code opening_054}). */
    static final int SAVE_ROWS = 8;

    /** The rows of the load box and of the title's load page (V {@code opening_055}, clip006). */
    static final int LOAD_ROWS = 10;

    /** Row 9 (0-based 8): the decade autosave, the original's COLONY08. */
    static final int DECADE_ROW = 8;

    /** Row 10 (0-based 9): the latest autosave, the original's COLONY09. */
    static final int LAST_TURN_ROW = 9;

    /** The original's file of row k (0-based) is {@code COLONY0k.SAV} (E6). */
    static final String SLOT_STEM = "COLONY0";

    /** The extension the messages show (the original's, D-SL1). */
    static final String SHOWN_EXTENSION = ".SAV";

    /** A row without its file (E7: English in the German game too). */
    static final String EMPTY = "(EMPTY)";

    /**
     * The widest row text of both boxes and of the title page: 182 px
     * ({@link ClassicMenuBox#rowTextMaxWidth} of the 196-wide box, E14).
     */
    static final int ROW_TEXT_MAX = 182;

    /** FreeCol's autosave folder inside the save folder. */
    static final String AUTOSAVE_FOLDER = "autosave";

    /**
     * FreeCol's defaults of the autosave prefix and the last-turn name
     * ({@code data/base/client-options.xml}), for the title page before any
     * client exists (the early window).
     */
    static final String DEFAULT_PREFIX = "Autosave", DEFAULT_LAST_TURN = "last-turn";

    /** GAME.TXT's boxes of part SL (E2, E3, E10, E12). */
    static final String SAVE_SECTION = "SAVEGAME", SAVE_GOOD = "SAVEGOOD",
        SAVE_ERROR = "SAVEERROR", LOAD_SECTION = "LOADGAME",
        LOAD_GOOD = "LOADGOOD", LOAD_NOT = "LOADNOT", LOAD_ERROR = "LOADERROR";

    /** GAME.TXT's {@code @width} of all six (190): the fallback's too. */
    static final int BOX_WIDTH = 190;


    /** Writes a save to a file (the server's {@code saveGame}). */
    interface SaveWriter {
        /**
         * @param file The file to write.
         * @exception IOException if the save cannot be written.
         */
        void write(File file) throws IOException;
    }


    /** The save folder (rows 1-8), or null. */
    private final File saveDir;

    /** The autosave folder (rows 9 and 10), or null. */
    private final File autosaveDir;

    /** The file name of FreeCol's last-turn autosave (row 10). */
    private final String lastTurnName;


    /**
     * @param saveDir The save folder (rows 1-8).
     * @param autosaveDir The autosave folder (rows 9 and 10).
     * @param lastTurnName The file name of FreeCol's last-turn autosave.
     */
    ClassicSaveSlots(File saveDir, File autosaveDir, String lastTurnName) {
        this.saveDir = saveDir;
        this.autosaveDir = autosaveDir;
        this.lastTurnName = (lastTurnName == null)
            ? lastTurnName(DEFAULT_PREFIX, DEFAULT_LAST_TURN) : lastTurnName;
    }

    /**
     * The game's rows: the save folder FreeCol uses
     * ({@link #saveRoot}), its {@code autosave} folder, and the last-turn
     * name of the client's options.
     *
     * @param co The client's options, or null for FreeCol's defaults.
     * @return The rows.
     */
    static ClassicSaveSlots of(ClientOptions co) {
        final File save = saveRoot(FreeColDirectories.getSaveDirectory());
        String prefix = DEFAULT_PREFIX, last = DEFAULT_LAST_TURN;
        if (co != null) {
            try {
                prefix = co.getText(ClientOptions.AUTO_SAVE_PREFIX);
                last = co.getText(ClientOptions.LAST_TURN_NAME);
            } catch (RuntimeException e) {
                logger.log(Level.FINE, "Classic save rows: FreeCol's autosave names", e);
            }
        }
        return new ClassicSaveSlots(save,
            (save == null) ? null : new File(save, AUTOSAVE_FOLDER),
            lastTurnName(prefix, last));
    }

    /**
     * FreeCol's last-turn file name ({@code InGameController.autoSaveGame}).
     *
     * @param prefix The autosave prefix.
     * @param last The last-turn name.
     * @return The file name.
     */
    static String lastTurnName(String prefix, String last) {
        return prefix + "-" + last + "." + FreeCol.FREECOL_SAVE_EXTENSION;
    }

    /**
     * The save folder of the rows: FreeCol's, but never its
     * {@code autosave} folder (FreeCol's own load of an autosave makes that
     * the save folder, {@code FreeColDirectories.setSavegameFile}; a session
     * started that way must not put rows 1-8 into it).
     *
     * @param dir FreeCol's save folder, or null.
     * @return The folder, or null.
     */
    static File saveRoot(File dir) {
        if (dir != null && AUTOSAVE_FOLDER.equals(dir.getName())
            && dir.getParentFile() != null) return dir.getParentFile();
        return dir;
    }


    // The rows

    /**
     * The file of a row.
     *
     * @param row The row, 0-based (0-9).
     * @return Its file, or null for no row or without the folder.
     */
    File slotFile(int row) {
        if (row < 0 || row >= LOAD_ROWS) return null;
        if (row < SAVE_ROWS) {
            return (this.saveDir == null) ? null
                : new File(this.saveDir, SLOT_STEM + row + "." + FreeCol.FREECOL_SAVE_EXTENSION);
        }
        if (this.autosaveDir == null) return null;
        return (row == DECADE_ROW)
            ? new File(this.autosaveDir, SLOT_STEM + row + "." + FreeCol.FREECOL_SAVE_EXTENSION)
            : new File(this.autosaveDir, this.lastTurnName);
    }

    /**
     * The original's name of a row's file, as the messages show it
     * ({@code %STRING0} of @SAVEGOOD, @LOADGOOD ...: "COLONY00.SAV", E10).
     *
     * @param row The row, 0-based.
     * @return The name.
     */
    static String displayName(int row) {
        return SLOT_STEM + row + SHOWN_EXTENSION;
    }

    /**
     * The name a message shows for any other saved game (the title's page
     * of all saves): the file's name in capitals, as the original writes
     * its own, with FONTTINY's missing '_' as a space.
     *
     * @param f The file.
     * @return The name.
     */
    static String displayName(File f) {
        return (f == null) ? "" : f.getName().toUpperCase(Locale.ROOT).replace('_', ' ');
    }

    /**
     * The name the messages show for a save: "COLONY02.SAV" for a row's
     * file, else the file's own name ({@link #displayName(File)}).
     *
     * @param f The file.
     * @return The name.
     */
    String nameOf(File f) {
        if (f == null) return "";
        for (int i = 0; i < LOAD_ROWS; i++) {
            final File s = slotFile(i);
            if (s != null && s.getAbsoluteFile().equals(f.getAbsoluteFile())) {
                return displayName(i);
            }
        }
        return displayName(f);
    }

    /**
     * @param row The row, 0-based.
     * @return Whether the row has no file ("(EMPTY)").
     */
    boolean isEmpty(int row) {
        final File f = slotFile(row);
        return f == null || !f.isFile();
    }

    /**
     * Whether a row holds a save that can be loaded: its file is there and
     * its head can be read ({@link ClassicSaveGames#peek}).
     *
     * @param row The row, 0-based.
     * @return True if it can.
     */
    boolean readable(int row) {
        return !isEmpty(row) && info(slotFile(row)) != null;
    }

    /**
     * The rows' labels, each fitted to {@code max} ({@link #label}).  EDT
     * only (the labels use {@link Messages}).
     *
     * @param rows How many rows: {@link #SAVE_ROWS} or {@link #LOAD_ROWS}.
     * @param font FONTTINY, or null (no fitting).
     * @param max The widest row text.
     * @return The labels.
     */
    List<String> labels(int rows, ClassicFont font, int max) {
        final List<String> out = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) out.add(label(i, font, max));
        return out;
    }

    /**
     * A row's label: "(EMPTY)" without its file, the original-style label
     * of the save ("Entdecker Dago der Holl., Herbst 1729",
     * {@link ClassicSaveGames#formatLabel}) fitted to the row, or for a
     * file whose head cannot be read its name and date
     * ({@link ClassicSaveGames#fallbackLabel}).  EDT only.
     *
     * @param row The row, 0-based.
     * @param font FONTTINY, or null (no fitting).
     * @param max The widest row text.
     * @return The label.
     */
    String label(int row, ClassicFont font, int max) {
        final File f = slotFile(row);
        if (f == null || !f.isFile()) return EMPTY;
        final ClassicSaveGames.Info i = info(f);
        if (i == null) {
            final String s = ClassicSaveGames.fallbackLabel(f);
            return (font == null) ? s : font.fit(s, max);
        }
        return fitLabel(font, i, max);
    }

    /**
     * The original-style label fitted to a row (I, D-SL8): as it is when
     * it fits; else the leader's name cut, as long as it can stay, and
     * ended with "...", the difficulty, the nation and the season and year
     * kept ("Entdecker Michiel De R... der Holl., Frühjahr 1496", 182 px);
     * only if no cut of the name is enough, the end of the label
     * ({@link ClassicFont#fit}).  EDT only.
     *
     * @param font FONTTINY, or null (no fitting).
     * @param i The save's facts.
     * @param max The widest row text.
     * @return The label.
     */
    static String fitLabel(ClassicFont font, ClassicSaveGames.Info i, int max) {
        final String full = ClassicSaveGames.formatLabel(i);
        if (font == null || font.stringWidth(full) <= max) return full;
        final String name = (i.owner == null) ? "" : i.owner;
        for (int n = name.length() - 1; n > 0; n--) {
            final String cut = name.substring(0, n).trim();
            if (cut.isEmpty()) break;
            final String s = ClassicSaveGames.formatLabel(i, cut + "...");
            if (font.stringWidth(s) <= max) return s;
        }
        return font.fit(full, max);
    }


    // The labels' peeks, remembered

    /** One peek, with the file's time and size when it was made. */
    private static final class Peeked {

        final long modified, length;

        /** The facts, or null: the head could not be read. */
        final ClassicSaveGames.Info info;

        Peeked(long modified, long length, ClassicSaveGames.Info info) {
            this.modified = modified;
            this.length = length;
            this.info = info;
        }
    }

    /**
     * The peeks so far, by path: a box opens with at most the rows written
     * since it last opened to read (a peek takes 10-12 ms warm, 76 ms cold;
     * spec section 2).  Any thread.
     */
    private static final Map<String, Peeked> PEEKS = new ConcurrentHashMap<>();

    /**
     * The facts of a save ({@link ClassicSaveGames#peek}), read again only
     * when the file's time or size changed.  Any thread.
     *
     * @param f The file.
     * @return The facts, or null without the file or when its head cannot
     *     be read.
     */
    static ClassicSaveGames.Info info(File f) {
        if (f == null || !f.isFile()) return null;
        final long modified = f.lastModified(), length = f.length();
        final String key = f.getAbsolutePath();
        final Peeked p = PEEKS.get(key);
        if (p != null && p.modified == modified && p.length == length) return p.info;
        final ClassicSaveGames.Info i = ClassicSaveGames.peek(f);
        PEEKS.put(key, new Peeked(modified, length, i));
        return i;
    }


    // The decade rule

    /**
     * Whether a turn's autosave goes to row 9 (I, rule H1, E8): the first
     * turn of a year divisible by 10 -- before the seasons every turn is a
     * year's first, after them only the spring.  1500 yes, 1501 no, 1600
     * spring yes, 1600 autumn no, 1720 spring yes, 1492 no.
     *
     * @param year The turn's year.
     * @param season Its season: negative before the seasons, 0 spring.
     * @return True for row 9.
     */
    static boolean isDecadeTurn(int year, int season) {
        return year % 10 == 0 && season <= 0;
    }

    /**
     * {@link #isDecadeTurn(int, int)} of a turn of the game being played
     * (the {@code Turn} statics are that game's).
     *
     * @param turn The turn, or null.
     * @return True for row 9.
     */
    static boolean isDecadeTurn(Turn turn) {
        return turn != null && isDecadeTurn(turn.getYear(), turn.getSeason());
    }


    // Writing a row

    /**
     * Write a save over a row's file in one step: into a temporary file in
     * the same folder, read it back ({@link ClassicSaveGames#peek}), then
     * move it over the file ({@code ATOMIC_MOVE}, or a plain replacing move
     * where the file system has none).  If the writer fails, the save
     * cannot be read back or the move is refused (the old file read-only,
     * open elsewhere), the temporary file is removed and the old file stays
     * exactly as it was (pitfall 2 of the spec: FreeCol's own saveGame
     * writes into the target and a failure destroys it).
     *
     * @param target The row's file.
     * @param writer Writes the save.
     * @exception IOException if the row could not be written; the old file
     *     is then unchanged.
     */
    static void writeAtomic(File target, SaveWriter writer) throws IOException {
        final File dir = target.getAbsoluteFile().getParentFile();
        if (dir == null) throw new IOException("No folder for " + target);
        if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) {
            throw new IOException("Cannot make the folder " + dir);
        }
        final String name = target.getName();
        final String stem = (name.indexOf('.') > 0)
            ? name.substring(0, name.indexOf('.')) : name;
        final Path tmp = Files.createTempFile(dir.toPath(), stem + "-", ".tmp");
        boolean moved = false;
        try {
            writer.write(tmp.toFile());
            if (ClassicSaveGames.peek(tmp.toFile()) == null) {
                throw new IOException("The new save cannot be read back: " + tmp);
            }
            try {
                Files.move(tmp, target.toPath(), StandardCopyOption.REPLACE_EXISTING,
                           StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                try {
                    Files.deleteIfExists(tmp);    // our temporary file, never a save
                } catch (IOException | SecurityException e) {
                    logger.log(Level.WARNING, "Classic save rows: cannot remove " + tmp, e);
                }
            }
        }
    }


    // The boxes and their messages

    /**
     * The save box ({@code @SAVEGAME}, V E2 {@code opening_054}) or the load
     * box ({@code @LOADGAME}, V E3 {@code opening_055}): GAME.TXT's prompt
     * and {@code @width}, one row per label, the bar on row 1 (V in every
     * capture, D-SL14), no portrait, centred; Escape and a click outside
     * answer no row ({@link ClassicAdvisorBox.Bar#DISMISSED}).  Without the
     * pack's text a FreeCol prompt of the same width.
     *
     * @param t The original texts, or null.
     * @param section {@link #SAVE_SECTION} or {@link #LOAD_SECTION}.
     * @param labels The rows' labels ({@link #labels}).
     * @param bar The row the bar starts on.
     * @param title The stopgap's window title.
     * @return The box.
     */
    static ClassicAdvisorBox.Request boxRequest(ClassicText t, String section,
                                                List<String> labels, int bar,
                                                String title) {
        ClassicAdvisorBox.Builder b = ClassicAdvisorBox.fromGameText(section,
            (t == null) ? null : t.message(section), new HashMap<>());
        if (b == null) {
            b = ClassicAdvisorBox.Request.builder(section).width(BOX_WIDTH)
                .freeColText(Messages.message(SAVE_SECTION.equals(section)
                    ? "classic.save.prompt" : "classic.mainMenu.loadPrompt"));
        }
        final List<String> rows = new ArrayList<>(labels.size());
        for (String s : labels) rows.add(ClassicAdvisorBox.literal(s));
        return b.rows(rows).defaultRow(bar).noCancelRow()
            .portrait(ClassicAdvisorBox.Portrait.NONE).stopgap(title, null).build();
    }

    /**
     * A notice of part SL: @SAVEGOOD ({@code %STRING0} the row's file,
     * {@code %STRING1} its new label, I D-SL13), @SAVEERROR, @LOADGOOD
     * (V E10: "COLONY00.SAV erfolgreich geladen.", box (62,88,196,24)),
     * @LOADNOT or @LOADERROR, from GAME.TXT; without the pack's text
     * FreeCol's words.
     *
     * @param t The original texts, or null.
     * @param section The message.
     * @param file The file's name as shown ({@link #displayName(int)}).
     * @param label The row's label ({@code %STRING1}), or null.
     * @param title The stopgap's window title.
     * @return The notice.
     */
    static ClassicAdvisorBox.Request noticeRequest(ClassicText t, String section,
                                                   String file, String label,
                                                   String title) {
        final Map<String, String> values = new HashMap<>();
        values.put("STRING0", (file == null) ? "" : file);
        values.put("STRING1", (label == null) ? "" : label);
        ClassicAdvisorBox.Builder b = ClassicAdvisorBox.fromGameText(section,
            (t == null) ? null : t.message(section), values);
        if (b == null) {
            final String key = SAVE_GOOD.equals(section) ? "classic.save.saved"
                : SAVE_ERROR.equals(section) ? "classic.save.failed"
                : LOAD_GOOD.equals(section) ? "classic.load.loaded"
                : LOAD_ERROR.equals(section) ? "classic.load.failed"
                : "classic.load.invalid";
            b = ClassicAdvisorBox.Request.builder(section).width(BOX_WIDTH)
                .freeColText(Messages.message(StringTemplate.template(key)
                    .addName("%file%", values.get("STRING0"))
                    .addName("%label%", values.get("STRING1"))));
        }
        return b.portrait(ClassicAdvisorBox.Portrait.NONE).stopgap(title, null).build();
    }

    /**
     * A notice's text as plain lines' source: GAME.TXT's lines with the
     * values filled in and joined (the title page wraps them itself), or
     * FreeCol's words without the pack.
     *
     * @param t The original texts, or null.
     * @param section The message.
     * @param file The file's name as shown.
     * @return The text.
     */
    static String noticeText(ClassicText t, String section, String file) {
        final ClassicAdvisorBox.Request r = noticeRequest(t, section, file, null, "");
        return r.plainText();
    }

    /** @return The rows of {@code labels} that are {@link #EMPTY} (tests, the logs). */
    static List<Integer> emptyRows(List<String> labels) {
        final List<Integer> out = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            if (EMPTY.equals(labels.get(i))) out.add(i);
        }
        return Collections.unmodifiableList(out);
    }

    @Override
    public String toString() {
        return "save rows " + this.saveDir + " | " + this.autosaveDir + " | "
            + this.lastTurnName;
    }
}
