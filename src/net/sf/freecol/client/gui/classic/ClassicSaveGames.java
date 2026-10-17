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
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.swing.SwingUtilities;
import javax.xml.stream.XMLStreamConstants;

import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.io.FreeColDirectories;
import net.sf.freecol.common.io.FreeColSavegameFile;
import net.sf.freecol.common.io.FreeColXMLReader;
import net.sf.freecol.common.model.StringTemplate;


/**
 * The label of a saved game, cheap and in the original's style, such as
 * "Entdecker Dago der Holl., Herbst 1729" ({@link #peek},
 * {@link #formatLabel}), and the list of <b>every</b> saved game on disk.
 *
 * <p>The original has 8 fixed save slots plus 2 autosave rows; the boxes
 * and the title's load page show exactly those 10 rows
 * ({@link ClassicSaveSlots}, part SL).  This list -- every manual save,
 * then every autosave, each newest first, no "(EMPTY)" rows -- is the
 * title page's second page, "all saved games" (PageDown on the page;
 * {@code ClassicMainMenuPanel} mode ARCHIVE), so the saves of the builds
 * before part SL (the numbered autosaves above all) stay loadable from
 * the game (I, decision D-SL5 for Roger).
 *
 * <p>Reading a whole save to label it would take seconds, so {@link #peek}
 * streams {@code savegame.xml} only up to the owner's {@code <player>}
 * element, before the map (about 30 ms per save); the list labels its rows
 * on a background thread, and until then a row shows its file name and
 * date.
 */
final class ClassicSaveGames {

    private static final Logger logger = Logger.getLogger(ClassicSaveGames.class.getName());

    private static final DateTimeFormatter DATE
        = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private ClassicSaveGames() {}   // static helpers only


    /** One loadable save. */
    static final class Entry {

        final File file;
        final long modified;

        /** The row text; EDT only.  Starts as {@link #fallbackLabel}. */
        String label;

        /**
         * The save's facts once peeked ({@link #fillLabelsAsync}), else
         * null; EDT only.  The page draws the row from them with the
         * leader's name cut and the season and year kept
         * ({@link ClassicSaveSlots#fitLabel}), as the ten rows.
         */
        Info info;

        Entry(File file) {
            this.file = file;
            this.modified = file.lastModified();
            this.label = fallbackLabel(file);
        }
    }

    /** What {@link #peek} reads out of a save. */
    static final class Info {

        final String owner, difficultyId, nationId;
        final int turn, startingYear, seasonYear, seasons;

        Info(String owner, String difficultyId, String nationId, int turn,
             int startingYear, int seasonYear, int seasons) {
            this.owner = owner;
            this.difficultyId = difficultyId;
            this.nationId = nationId;
            this.turn = turn;
            this.startingYear = startingYear;
            this.seasonYear = seasonYear;
            this.seasons = seasons;
        }
    }


    /**
     * All loadable saves: manual saves, then autosaves, then the save rows'
     * files kept when they were saved over ({@code <save>/ersetzt},
     * {@link ClassicSaveSlots#keepReplaced}), each newest first.  Never
     * calls {@code FreeColDirectories.setSavegameFile}, which would
     * relocate the save directory (see {@code InGameController.loadGame}).
     */
    static List<Entry> list() {
        final List<Entry> out = new ArrayList<>();
        final File save = FreeColDirectories.getSaveDirectory();
        addSaves(out, save);
        addSaves(out, FreeColDirectories.getAutosaveDirectory());
        final File root = ClassicSaveSlots.saveRoot(save);
        if (root != null) addSaves(out, new File(root, ClassicSaveSlots.REPLACED_FOLDER));
        return out;
    }

    private static void addSaves(List<Entry> out, File dir) {
        if (dir == null) return;
        final File[] files = dir.listFiles(f -> f.isFile()
            && f.getName().toLowerCase(Locale.ROOT).endsWith(".fsg"));
        if (files == null) return;
        final List<Entry> group = new ArrayList<>();
        for (File f : files) group.add(new Entry(f));
        group.sort(Comparator.comparingLong((Entry e) -> e.modified).reversed());
        out.addAll(group);
    }

    /**
     * The label shown until (or instead of) the peeked one: the file name
     * without {@code .fsg}, '_' as spaces (FONTTINY has no '_'), and the
     * modification date.
     */
    static String fallbackLabel(File f) {
        String name = f.getName();
        if (name.toLowerCase(Locale.ROOT).endsWith(".fsg")) {
            name = name.substring(0, name.length() - 4);
        }
        return name.replace('_', ' ') + "  "
            + DATE.format(Instant.ofEpochMilli(f.lastModified())
                                 .atZone(ZoneId.systemDefault()));
    }

    /**
     * Read the label facts from a save without loading it: the owner, the
     * turn, the difficulty, the year options, and the owner's nation -- then
     * stop before the map.
     *
     * <p>The save is read through a {@link ZipFile} that is closed again
     * here, not through {@code FreeColSavegameFile}'s jar URLs: a jar URL
     * may keep its file open for the rest of the session (the JDK caches
     * the first jar it opens), and on Windows an open file can neither be
     * replaced nor moved, so the next save into that slot would fail (part
     * SL, {@link ClassicSaveSlots#writeAtomic}).
     *
     * @return The facts, or {@code null} if the save cannot be read (for
     *     example an old sandbox save whose XML comment contains "--").
     */
    static Info peek(File f) {
        String owner = null, difficulty = null, nation = null;
        int turn = 1, startingYear = 1492, seasonYear = 1600, seasons = 2;
        boolean gotStart = false, gotSeasonYear = false, gotSeasons = false;
        try (ZipFile zip = new ZipFile(f);
             FreeColXMLReader xr = new FreeColXMLReader(savegameStream(zip))) {
            xr.nextTag();                                   // <savedGame>
            owner = xr.getAttribute("owner", (String) null);
            while (xr.hasNext()) {
                if (xr.next() != XMLStreamConstants.START_ELEMENT) continue;
                final String tag = xr.getLocalName();
                if ("game".equals(tag)) {
                    turn = parse(xr.getAttribute("turn", (String) null), turn);
                } else if ("freecol-specification".equals(tag)) {
                    difficulty = xr.getAttribute("difficulty-level", (String) null);
                } else if ("integerOption".equals(tag)) {
                    final String id = xr.getAttribute("id", (String) null);
                    final String v = xr.getAttribute("value", (String) null);
                    if ("model.option.startingYear".equals(id) && !gotStart) {
                        startingYear = parse(v, startingYear);
                        gotStart = true;
                    } else if ("model.option.seasonYear".equals(id) && !gotSeasonYear) {
                        seasonYear = parse(v, seasonYear);
                        gotSeasonYear = true;
                    } else if ("model.option.seasons".equals(id) && !gotSeasons) {
                        seasons = parse(v, seasons);
                        gotSeasons = true;
                    }
                } else if ("player".equals(tag) && owner != null
                    && owner.equals(xr.getAttribute("username", (String) null))) {
                    nation = xr.getAttribute("nationId", (String) null);
                    break;                                  // before the map
                }
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "Cannot peek save " + f, e);
            return null;
        }
        if (owner == null) return null;
        return new Info(owner, difficulty, nation, turn, startingYear,
                        seasonYear, Math.max(1, seasons));
    }

    /**
     * The {@code savegame.xml} of a save: the entry of that name, or one
     * in a folder (as {@code FreeColDataFile} allows a save to keep its
     * entries in one).
     *
     * @param zip The save.
     * @return The entry's stream.
     * @exception IOException if the save has none.
     */
    private static InputStream savegameStream(ZipFile zip) throws IOException {
        ZipEntry e = zip.getEntry(FreeColSavegameFile.SAVEGAME_FILE);
        if (e == null) {
            final Enumeration<? extends ZipEntry> all = zip.entries();
            while (all.hasMoreElements()) {
                final ZipEntry z = all.nextElement();
                if (z.getName().endsWith("/" + FreeColSavegameFile.SAVEGAME_FILE)) {
                    e = z;
                    break;
                }
            }
        }
        if (e == null) throw new IOException("No " + FreeColSavegameFile.SAVEGAME_FILE
                                             + " in " + zip.getName());
        return zip.getInputStream(e);
    }

    private static int parse(String s, int fallback) {
        try {
            return (s == null) ? fallback : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * The original-style label "Entdecker Dago der Holl., Herbst 1729"
     * (difficulty title GAME.TXT:142-146, nation abbreviation NAMES.TXT
     * {@code @NATIONABBREV}).  The year/season is the {@code Turn} arithmetic
     * (Turn.java:165-185) done locally, so the global {@code Turn} statics --
     * which belong to whatever game is running -- are never touched.  EDT
     * only (uses {@link Messages}).
     */
    static String formatLabel(Info i) {
        return formatLabel(i, i.owner);
    }

    /**
     * The label of {@link #formatLabel(Info)} with another leader's name:
     * the name cut to fit the row ({@link ClassicSaveSlots#fitLabel}).
     * EDT only.
     *
     * @param i The facts.
     * @param leader The name to show for the owner.
     * @return The label.
     */
    static String formatLabel(Info i, String leader) {
        final int year = i.turn - 1 + i.startingYear;
        final int displayYear, season;
        if (year < i.seasonYear) {
            season = -1;
            displayYear = year;
        } else {
            displayYear = i.seasonYear + (year - i.seasonYear) / i.seasons;
            season = (year - i.seasonYear) % i.seasons;
        }
        // Before seasons start the original still says "Frühjahr 1492"
        // (opening_032), hence -1 -> spring.
        final String seasonName = (season <= 0)
            ? Messages.message("classic.season.spring")
            : (season == 1) ? Messages.message("classic.season.autumn") : "";
        final String label = Messages.message(StringTemplate
            .template("classic.save.label")
            .addName("%difficulty%", lookup("classic.difficulty.", i.difficultyId))
            .addName("%leader%", leader)
            .addName("%nation%", lookup("classic.nationAbbrev.", i.nationId))
            .addName("%season%", seasonName)
            .addName("%year%", Integer.toString(displayYear)));
        return label.replaceAll("\\s+", " ").replace(" ,", ",").trim();
    }

    /** The classic short name for an id (by its last segment), else FreeCol's. */
    private static String lookup(String prefix, String id) {
        if (id == null) return "";
        final String key = prefix + id.substring(id.lastIndexOf('.') + 1);
        return Messages.containsKey(key) ? Messages.message(key)
            : Messages.getName(id);
    }

    /**
     * Peek every entry in order on one daemon thread, publishing each label
     * on the EDT and then calling {@code onUpdate} with {@code generation}
     * (so the caller can ignore updates for a list it no longer shows).
     *
     * <p>The thread stops as soon as {@code current} no longer holds
     * {@code generation}: the caller bumps it whenever the box closes or
     * reopens.  Otherwise every quick open/close would leave another thread
     * reading the same zips, possibly while one of them is being loaded.
     */
    static void fillLabelsAsync(List<Entry> entries, int generation,
                                AtomicInteger current, IntConsumer onUpdate) {
        final List<Entry> work = Collections.unmodifiableList(new ArrayList<>(entries));
        final Thread t = new Thread(() -> {
                for (Entry e : work) {
                    if (current.get() != generation) return;
                    final Info info = peek(e.file);
                    if (info == null) continue;
                    SwingUtilities.invokeLater(() -> {
                            try {
                                e.label = formatLabel(info);
                                e.info = info;
                            } catch (RuntimeException ex) {
                                logger.log(Level.FINE, "Bad save label", ex);
                            }
                            onUpdate.accept(generation);
                        });
                }
            }, "classic-save-labels");
        t.setDaemon(true);
        t.start();
    }
}
