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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.freecol.common.io.FreeColDirectories;


/**
 * The Classic UI's own options: the rows of the original's
 * "Spieloptionen festlegen" box (GAME.TXT {@code @GAMEOPTIONS}) that have no
 * FreeCol client option, and the ten rows of its
 * "Koloniebericht-Optionen festlegen" box ({@code @COLONYOPTIONS}), stored in
 * {@value #FILE_NAME} in the user config directory so the core data stays
 * untouched (build spec section 2).  {@link ClassicOptionBoxes} shows both
 * boxes (W14).
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
 * {@code guiShowTutorial}; all three default on, as in the original.  What
 * the box sets for them is also remembered here ({@link #remember}), so it
 * holds in the next session ({@link ClassicOptionBoxes#applyRemembered}).
 *
 * <p>The colony report options (no clip shows the box; every default on,
 * I: Roger's games show the labels and the reports):
 * <table>
 *   <caption>Colony report prefs</caption>
 *   <tr><th>Key</th><th>Original row</th><th>Here</th></tr>
 *   <tr><td>{@value #BUILDING_LABELS}</td><td>Beschriftung an Geb&auml;uden</td><td>the colony screen's numbers on the buildings</td></tr>
 *   <tr><td>{@value #GOODS_TERRAIN_LABELS}</td><td>Beschriftung an Waren und Terrain</td><td>its numbers on the work tiles and the net production</td></tr>
 *   <tr><td>{@value #REPORT_TRAINED} ... {@value #REPORT_REBEL_MAJORITY}</td><td>Bericht, wenn / bei ...</td><td>the matching FreeCol notices ({@link #REPORTS})</td></tr>
 * </table>
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

    /** Autom. Sichern (a FreeCol option, {@link #CLIENT_OPTIONS}). */
    static final String AUTO_SAVE = "autoSave";

    /** Kampfanalyse (a FreeCol option, {@link #CLIENT_OPTIONS}). */
    static final String COMBAT_ANALYSIS = "combatAnalysis";

    /** Tutortips (a FreeCol option, {@link #CLIENT_OPTIONS}). */
    static final String TUTOR_TIPS = "tutorTips";

    /** Beschriftung an Gebaeuden: the colony screen's numbers on the buildings. */
    static final String BUILDING_LABELS = "buildingLabels";

    /**
     * Beschriftung an Waren und Terrain: the colony screen's numbers on the
     * work tiles and under the net production.
     */
    static final String GOODS_TERRAIN_LABELS = "goodsTerrainLabels";

    /** Bericht, wenn Siedler ausgebildet. */
    static final String REPORT_TRAINED = "reportTrained";

    /** Bericht bei Nahrungsmittel-Knappheit. */
    static final String REPORT_FOOD = "reportFoodShortage";

    /** Bericht bei Rohstoff-Knappheit. */
    static final String REPORT_RAW_MATERIALS = "reportRawMaterialShortage";

    /** Bericht, wenn Werkzeuge fuer Produktion benoetigt. */
    static final String REPORT_TOOLS = "reportToolsNeeded";

    /** Bericht bei unfaehiger Regierung. */
    static final String REPORT_GOVERNMENT = "reportBadGovernment";

    /** Bericht, wenn neue Waren erhaeltlich. */
    static final String REPORT_NEW_GOODS = "reportNewGoods";

    /** Bericht ueber Soehne der Freiheit-Mitgliedschaft. */
    static final String REPORT_SONS_OF_LIBERTY = "reportSonsOfLiberty";

    /** Bericht bei Rebellen-Mehrheiten. */
    static final String REPORT_REBEL_MAJORITY = "reportRebelMajority";

    /** The classic prefs and their defaults, in the boxes' order. */
    static final Map<String, Boolean> DEFAULTS;
    static {
        final Map<String, Boolean> m = new LinkedHashMap<>();
        m.put(SHOW_NATIVE_MOVES, Boolean.TRUE);
        m.put(SHOW_EUROPEAN_MOVES, Boolean.TRUE);
        m.put(MOVE_ACCELERATOR, Boolean.FALSE);
        m.put(END_TURN_PROMPT, Boolean.FALSE);
        m.put(WATER_CYCLING, Boolean.TRUE);
        m.put(BUILDING_LABELS, Boolean.TRUE);
        m.put(GOODS_TERRAIN_LABELS, Boolean.TRUE);
        m.put(REPORT_TRAINED, Boolean.TRUE);
        m.put(REPORT_FOOD, Boolean.TRUE);
        m.put(REPORT_RAW_MATERIALS, Boolean.TRUE);
        m.put(REPORT_TOOLS, Boolean.TRUE);
        m.put(REPORT_GOVERNMENT, Boolean.TRUE);
        m.put(REPORT_NEW_GOODS, Boolean.TRUE);
        m.put(REPORT_SONS_OF_LIBERTY, Boolean.TRUE);
        m.put(REPORT_REBEL_MAJORITY, Boolean.TRUE);
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
        m.put(AUTO_SAVE, "model.option.autosavePeriod");
        m.put(COMBAT_ANALYSIS, "model.option.guiShowPreCombat");
        m.put(TUTOR_TIPS, "model.option.guiShowTutorial");
        CLIENT_OPTIONS = Collections.unmodifiableMap(m);
    }

    /**
     * The rows of "Spieloptionen festlegen" (GAME.TXT {@code @GAMEOPTIONS}),
     * top to bottom: a classic pref or a {@link #CLIENT_OPTIONS} name each.
     */
    static final List<String> GAME_ROWS = Collections.unmodifiableList(Arrays.asList(
        SHOW_NATIVE_MOVES, SHOW_EUROPEAN_MOVES, MOVE_ACCELERATOR, END_TURN_PROMPT,
        AUTO_SAVE, COMBAT_ANALYSIS, WATER_CYCLING, TUTOR_TIPS));

    /**
     * The rows of "Koloniebericht-Optionen festlegen" (GAME.TXT
     * {@code @COLONYOPTIONS}), top to bottom.
     */
    static final List<String> COLONY_ROWS = Collections.unmodifiableList(Arrays.asList(
        BUILDING_LABELS, GOODS_TERRAIN_LABELS, REPORT_TRAINED, REPORT_FOOD,
        REPORT_RAW_MATERIALS, REPORT_TOOLS, REPORT_GOVERNMENT, REPORT_NEW_GOODS,
        REPORT_SONS_OF_LIBERTY, REPORT_REBEL_MAJORITY));

    /**
     * The FreeCol notices each report row of {@code @COLONYOPTIONS} switches:
     * message id to its pref.  Read off the server's messages and the
     * original's texts of the same matter (I: no clip shows a report row
     * off):
     * <ul>
     *   <li>trained: the education done ({@code unitEducated}, the
     *       original's @TRAINPROFESSION, @TRAININDENTURED, @TRAINCRIMINAL)
     *       and a teacher without a pupil (@TRAINFAIL);</li>
     *   <li>food: famine feared (@FOODLOW, @FOOD1/2); a colonist who
     *       starves is a loss and always reported (@STARVE1/2);</li>
     *   <li>raw materials: a building that stopped or slowed for want of
     *       its input (@LUMBER, @TOOLS);</li>
     *   <li>tools: a build that needs goods to finish (@NEEDTOOLS);</li>
     *   <li>government: bad or very bad government, and its improvement
     *       while still bad (the tory penalty);</li>
     *   <li>new goods: a store past its high-water mark or full
     *       (@CARGOREADY0-2); goods thrown away stay reported (@SPOIL);</li>
     *   <li>Sons of Liberty: a colony's membership crossing a tenth
     *       (@SONSUP, @SONSDOWN);</li>
     *   <li>rebel majorities: the 50 % and 100 % marks won or lost
     *       (@REBELMAJORITY, @REBELUNANIMOUS, @TORYMINORITY,
     *       @TORYMAJORITY).</li>
     * </ul>
     * Everything else is shown as FreeCol's own options say: the founding
     * fathers (also of type SONS_OF_LIBERTY), the nation's rebel share
     * ({@code model.player.soL*}, not a colony report), new colonists, the
     * buildings finished.
     */
    static final Map<String, String> REPORTS;
    static {
        final Map<String, String> m = new LinkedHashMap<>();
        m.put("model.building.unitEducated", REPORT_TRAINED);
        m.put("model.building.noStudent", REPORT_TRAINED);
        m.put("model.colony.famineFeared", REPORT_FOOD);
        m.put("model.building.noInput", REPORT_RAW_MATERIALS);
        m.put("model.building.notEnoughInput", REPORT_RAW_MATERIALS);
        m.put("model.colony.buildableNeedsGoods", REPORT_TOOLS);
        m.put("model.colony.badGovernment", REPORT_GOVERNMENT);
        m.put("model.colony.veryBadGovernment", REPORT_GOVERNMENT);
        m.put("model.colony.governmentImproved1", REPORT_GOVERNMENT);
        m.put("model.colony.governmentImproved2", REPORT_GOVERNMENT);
        m.put("model.colony.warehouseFull", REPORT_NEW_GOODS);
        m.put("model.colony.warehouseOverfull", REPORT_NEW_GOODS);
        m.put("model.colony.soLIncrease", REPORT_SONS_OF_LIBERTY);
        m.put("model.colony.soLDecrease", REPORT_SONS_OF_LIBERTY);
        m.put("model.colony.goodGovernment", REPORT_REBEL_MAJORITY);
        m.put("model.colony.veryGoodGovernment", REPORT_REBEL_MAJORITY);
        m.put("model.colony.lostGoodGovernment", REPORT_REBEL_MAJORITY);
        m.put("model.colony.lostVeryGoodGovernment", REPORT_REBEL_MAJORITY);
        REPORTS = Collections.unmodifiableMap(m);
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
        put(key, value);
        ClassicFrameRecorder.event("pref", key + "=" + value);
    }

    /**
     * Remember what the options box set for one of the original's rows
     * that are FreeCol options ({@link #CLIENT_OPTIONS}), and store the
     * file: the FreeCol option itself is set by the caller.
     *
     * @param name A name of {@link #CLIENT_OPTIONS}.
     * @param value The value.
     */
    synchronized void remember(String name, boolean value) {
        if (!CLIENT_OPTIONS.containsKey(name)) {
            throw new IllegalArgumentException("No FreeCol option row " + name);
        }
        put(name, value);
    }

    /**
     * What the options box last set for a FreeCol option row.
     *
     * @param name A name of {@link #CLIENT_OPTIONS}.
     * @return The value, or null if it never set one.
     */
    synchronized Boolean remembered(String name) {
        if (!CLIENT_OPTIONS.containsKey(name)) {
            throw new IllegalArgumentException("No FreeCol option row " + name);
        }
        final String v = this.values.getProperty(name);
        return (v == null) ? null : Boolean.valueOf(Boolean.parseBoolean(v.trim()));
    }

    /**
     * Whether a FreeCol notice is shown by the colony report options
     * ({@link #REPORTS}).
     *
     * @param messageId The notice's message id, or null.
     * @return False only when a report row switches it, and is off.
     */
    boolean showsReport(String messageId) {
        final String key = (messageId == null) ? null : REPORTS.get(messageId);
        return key == null || is(key);
    }

    /** Store a value and write the file. */
    private void put(String key, boolean value) {
        this.values.setProperty(key, Boolean.toString(value));
        if (this.file == null) return;
        try (OutputStream out = new FileOutputStream(this.file)) {
            this.values.store(out, "Classic UI options (the original's Spieloptionen"
                + " and Koloniebericht-Optionen)");
        } catch (IOException e) {
            logger.log(Level.WARNING, "Classic prefs: " + this.file
                + " not written", e);
        }
    }
}
