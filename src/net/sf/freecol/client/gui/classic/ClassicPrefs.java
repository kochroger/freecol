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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.freecol.common.io.FreeColDirectories;


/**
 * The Classic UI's own options: the rows of the original's
 * "Spieloptionen festlegen" box (GAME.TXT {@code @GAMEOPTIONS}) that have no
 * FreeCol client option, stored in {@value #FILE_NAME} in the user config
 * directory so the core data stays untouched (build spec section 2).
 *
 * <p>Defaults are the original's, read from Roger's box (landing-slow clip
 * #395, state A):
 * <table>
 *   <caption>Classic prefs</caption>
 *   <tr><th>Key</th><th>Original row</th><th>Default</th></tr>
 *   <tr><td>{@value #SHOW_NATIVE_MOVES}</td><td>Bewegungen der Indianer zeigen</td><td>on</td></tr>
 *   <tr><td>{@value #SHOW_EUROPEAN_MOVES}</td><td>Bewegungen der anderen Europ&auml;er zeigen</td><td>on</td></tr>
 *   <tr><td>{@value #MOVE_ACCELERATOR}</td><td>Bewegungsbeschleuniger</td><td>off</td></tr>
 *   <tr><td>{@value #END_TURN_PROMPT}</td><td>Spielzugende</td><td>off</td></tr>
 *   <tr><td>{@value #WATER_CYCLING}</td><td>Farbwechsel f&uuml;r Wasser</td><td>on</td></tr>
 * </table>
 * The other three rows map onto FreeCol client options and live there
 * ({@link #CLIENT_OPTIONS}): Autom. Sichern = {@code autosavePeriod} 1/0,
 * Kampfanalyse = {@code guiShowPreCombat}, Tutortips =
 * {@code guiShowTutorial}; all three default on, as in the original.
 *
 * <p>Read a pref where it is used ({@code ClassicPrefs.get().is(...)}), not
 * once at start: the original applies a change in the same turn (the
 * Spielzugende row is read at the idle decision).  Unknown keys are a
 * programming error ({@link IllegalArgumentException}).
 */
final class ClassicPrefs {

    private static final Logger logger = Logger.getLogger(ClassicPrefs.class.getName());

    /** The file in the user config directory. */
    static final String FILE_NAME = "classic-options.properties";

    /** Bewegungen der Indianer zeigen. */
    static final String SHOW_NATIVE_MOVES = "showNativeMoves";

    /** Bewegungen der anderen Europaeer zeigen. */
    static final String SHOW_EUROPEAN_MOVES = "showEuropeanMoves";

    /** Bewegungsbeschleuniger: 13.25 instead of 16.43 ms per slide pixel. */
    static final String MOVE_ACCELERATOR = "moveAccelerator";

    /** Spielzugende: wait in the Spielzugende mode instead of ending (W17). */
    static final String END_TURN_PROMPT = "endTurnPrompt";

    /** Farbwechsel fuer Wasser: palette cycling (W6c). */
    static final String WATER_CYCLING = "waterCycling";

    /** The classic prefs and their defaults, in the box's order. */
    static final Map<String, Boolean> DEFAULTS;
    static {
        final Map<String, Boolean> m = new LinkedHashMap<>();
        m.put(SHOW_NATIVE_MOVES, Boolean.TRUE);
        m.put(SHOW_EUROPEAN_MOVES, Boolean.TRUE);
        m.put(MOVE_ACCELERATOR, Boolean.FALSE);
        m.put(END_TURN_PROMPT, Boolean.FALSE);
        m.put(WATER_CYCLING, Boolean.TRUE);
        DEFAULTS = Collections.unmodifiableMap(m);
    }

    /**
     * The original's rows that are FreeCol client options: classic name to
     * option id (all boolean except {@code autosavePeriod}, where on = 1 and
     * off = 0).
     */
    static final Map<String, String> CLIENT_OPTIONS;
    static {
        final Map<String, String> m = new LinkedHashMap<>();
        m.put("autoSave", "model.option.autosavePeriod");
        m.put("combatAnalysis", "model.option.guiShowPreCombat");
        m.put("tutorTips", "model.option.guiShowTutorial");
        CLIENT_OPTIONS = Collections.unmodifiableMap(m);
    }

    /** The shared instance, bound to the user config directory. */
    private static ClassicPrefs shared = null;

    /** The file, or null (in memory only). */
    private final File file;

    /** The stored values (only keys that were set). */
    private final Properties values = new Properties();


    /**
     * Prefs stored in {@code file}, loaded now.
     *
     * @param file The file, or null to keep them in memory only.
     */
    ClassicPrefs(File file) {
        this.file = file;
        if (file != null && file.isFile()) {
            try (InputStream in = new FileInputStream(file)) {
                this.values.load(in);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Classic prefs: " + file
                    + " unreadable, defaults used", e);
            }
        }
    }

    /**
     * The shared prefs, in {@value #FILE_NAME} in the user config directory
     * (in memory only while there is none).
     *
     * @return The prefs.
     */
    static synchronized ClassicPrefs get() {
        if (shared == null) {
            final File dir = FreeColDirectories.getUserConfigDirectory();
            shared = new ClassicPrefs((dir == null) ? null : new File(dir, FILE_NAME));
        }
        return shared;
    }

    /**
     * Is this a classic pref?
     *
     * @param key The key.
     * @return True for the keys of {@link #DEFAULTS}.
     */
    static boolean isKnown(String key) {
        return DEFAULTS.containsKey(key);
    }

    /**
     * The value of a pref.
     *
     * @param key A key of {@link #DEFAULTS}.
     * @return The stored value, else the default.
     */
    synchronized boolean is(String key) {
        final Boolean d = DEFAULTS.get(key);
        if (d == null) throw new IllegalArgumentException("No classic pref " + key);
        final String v = this.values.getProperty(key);
        return (v == null) ? d : Boolean.parseBoolean(v.trim());
    }

    /**
     * Set a pref and store the file.
     *
     * @param key A key of {@link #DEFAULTS}.
     * @param value The value.
     */
    synchronized void set(String key, boolean value) {
        if (!isKnown(key)) throw new IllegalArgumentException("No classic pref " + key);
        this.values.setProperty(key, Boolean.toString(value));
        ClassicFrameRecorder.event("pref", key + "=" + value);
        if (this.file == null) return;
        try (OutputStream out = new FileOutputStream(this.file)) {
            this.values.store(out, "Classic UI options (the original's Spieloptionen)");
        } catch (IOException e) {
            logger.log(Level.WARNING, "Classic prefs: " + this.file
                + " not written", e);
        }
    }
}
