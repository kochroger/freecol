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

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.freecol.client.ClientOptions;
import net.sf.freecol.common.model.ModelMessage;
import net.sf.freecol.common.option.BooleanOption;
import net.sf.freecol.common.option.IntegerOption;


/**
 * The original's two option boxes (build spec W14), SPIEL rows 0 and 1:
 * "Spieloptionen festlegen" (GAME.TXT {@code @GAMEOPTIONS}, 8 rows) and
 * "Koloniebericht-Optionen festlegen" ({@code @COLONYOPTIONS}, 10 rows),
 * both checkbox advisor boxes ({@link ClassicAdvisorBox}, the
 * {@code @checkbox} rule).  Measured on the landing-slow clip
 * ({@code 01-options.md}, V) and confirmed at 0 px by clips 006/007; the
 * colony box is in no clip, and follows the same rules (I).
 *
 * <ul>
 *   <li><b>Look and place (V).</b>  @width 220, so the box is 226 wide and
 *       centred: (47,56,226,88) with 8 rows, (47,48,226,104) with 10.  The
 *       title, the rows with FONTTINY {@code ]} / {@code [} in gold, the bar
 *       on row 1 at every open; no OK row, no button.  It comes in one
 *       paint, {@link #OPEN_DELAY_MS} after the menu row.</li>
 *   <li><b>Live (V).</b>  A flip acts at once, mid-turn (the accelerator's
 *       next slide, the next Spielzugende decision, the water's next step);
 *       the states carry over to the next opening and, through
 *       {@link ClassicPrefs}, to the next session.</li>
 *   <li><b>Close (V).</b>  Escape or a click outside; the screen under the
 *       box comes back exactly ({@link ClassicAdvisorLayer}).</li>
 *   <li><b>The rows.</b>  {@link ClassicPrefs#GAME_ROWS} and
 *       {@link ClassicPrefs#COLONY_ROWS}: a classic pref each, or one of the
 *       three FreeCol options ({@link ClassicPrefs#CLIENT_OPTIONS}), whose
 *       box value is remembered in the prefs too and applied again at the
 *       next game view ({@link #applyRemembered}).</li>
 * </ul>
 * The words are GAME.TXT's, from the pack; without them the rows cannot be
 * named and no box is made ({@link #request} returns null).
 */
final class ClassicOptionBoxes {

    private static final Logger logger = Logger.getLogger(ClassicOptionBoxes.class.getName());

    /** GAME.TXT's "Spieloptionen festlegen". */
    static final String GAME_SECTION = "GAMEOPTIONS";

    /** GAME.TXT's "Koloniebericht-Optionen festlegen". */
    static final String COLONY_SECTION = "COLONYOPTIONS";

    /**
     * How long after the menu row a box comes (ms): 2.5 frames of 70.0863
     * Hz, the middle of the clip's 2-4 frames between the frame without the
     * SPIEL menu and the box (#391 -> #395, #4884/#4885 -> #4887,
     * #7106/#7107 -> #7110).
     */
    static final double OPEN_DELAY_MS = 2.5 * 1000.0 / 70.0863;


    /** Where the rows' states live, and what a change does. */
    interface Store {

        /**
         * @param key A row's key ({@link ClassicPrefs#GAME_ROWS},
         *     {@link ClassicPrefs#COLONY_ROWS}).
         * @return Its state now.
         */
        boolean get(String key);

        /**
         * Set a row and apply it at once.
         *
         * @param key A row's key.
         * @param on Its new state.
         */
        void set(String key, boolean on);
    }


    private ClassicOptionBoxes() {}   // static helpers only


    /**
     * The rows of a box.
     *
     * @param section {@link #GAME_SECTION} or {@link #COLONY_SECTION}.
     * @return The keys, top to bottom, or null for another section.
     */
    static List<String> keys(String section) {
        if (GAME_SECTION.equals(section)) return ClassicPrefs.GAME_ROWS;
        if (COLONY_SECTION.equals(section)) return ClassicPrefs.COLONY_ROWS;
        return null;
    }

    /**
     * An option box: GAME.TXT's title and rows, the rows' states from the
     * store, the bar on row 1 ({@code @default} absent), Escape closing it
     * with no row; a flip sets the store.
     *
     * @param t The original texts, or null.
     * @param section {@link #GAME_SECTION} or {@link #COLONY_SECTION}.
     * @param store The states.
     * @param title The stopgap's window title, or null for the box's own
     *     title line.
     * @return The box, or null without the message, or when it is not the
     *     checkbox message of as many rows as the keys.
     */
    static ClassicAdvisorBox.Request request(ClassicText t, String section,
                                             Store store, String title) {
        final List<String> keys = keys(section);
        final ClassicText.Message m = (t == null || keys == null) ? null
            : t.message(section);
        if (m == null || !m.checkbox || m.text.isEmpty()
            || m.options.size() != keys.size()) {
            logger.info("Classic options: no GAME.TXT @" + section
                + " checkbox box of " + ((keys == null) ? "?" : keys.size())
                + " rows in the pack");
            return null;
        }
        final ClassicAdvisorBox.Builder b = ClassicAdvisorBox.fromGameText(section, m, null);
        if (b == null) return null;
        final boolean[] states = new boolean[keys.size()];
        for (int i = 0; i < states.length; i++) states[i] = store.get(keys.get(i));
        return b.checks(states, (row, on) -> store.set(keys.get(row), on))
            .noCancelRow().openDelay(OPEN_DELAY_MS).stopgap((title != null) ? title
                : ClassicAdvisorBox.plain(String.join(" ", m.text)), null).build();
    }

    /**
     * The store of the game view: the classic prefs, the FreeCol options
     * for their three rows (the value is remembered in the prefs as well),
     * then {@code applied} for what must follow at once (the water cycle).
     *
     * @param prefs The prefs.
     * @param co The client's options, or null (then the FreeCol rows read
     *     the remembered value, else on).
     * @param applied Runs after each set with the key and the value, or
     *     null.
     * @return The store.
     */
    static Store store(ClassicPrefs prefs, ClientOptions co,
                       BiConsumer<String, Boolean> applied) {
        return new Store() {
            @Override
            public boolean get(String key) {
                if (!ClassicPrefs.CLIENT_OPTIONS.containsKey(key)) return prefs.is(key);
                if (co != null) {
                    try {
                        return clientValue(co, key);
                    } catch (RuntimeException e) {
                        logger.log(Level.WARNING, "Classic options: no FreeCol option "
                            + key, e);
                    }
                }
                final Boolean r = prefs.remembered(key);
                return r == null || r;
            }

            @Override
            public void set(String key, boolean on) {
                if (ClassicPrefs.CLIENT_OPTIONS.containsKey(key)) {
                    if (co != null) {
                        try {
                            setClientValue(co, key, on);
                        } catch (RuntimeException e) {
                            logger.log(Level.WARNING, "Classic options: no FreeCol option "
                                + key, e);
                        }
                    }
                    prefs.remember(key, on);
                    ClassicFrameRecorder.event("pref", key + "=" + on + " ("
                        + ClassicPrefs.CLIENT_OPTIONS.get(key) + ")");
                } else {
                    prefs.set(key, on);
                }
                if (applied != null) applied.accept(key, on);
            }
        };
    }


    // The FreeCol option rows

    /**
     * The state of an original row that is a FreeCol option.
     *
     * @param co The client's options.
     * @param name A name of {@link ClassicPrefs#CLIENT_OPTIONS}.
     * @return On: {@code autosavePeriod} above 0, the boolean options true.
     */
    static boolean clientValue(ClientOptions co, String name) {
        final String id = ClassicPrefs.CLIENT_OPTIONS.get(name);
        if (id == null) throw new IllegalArgumentException("No FreeCol option row " + name);
        if (ClientOptions.AUTOSAVE_PERIOD.equals(id)) {
            return co.getOption(id, IntegerOption.class).getValue() > 0;
        }
        return co.getOption(id, BooleanOption.class).getValue();
    }

    /**
     * Set an original row that is a FreeCol option: Autom. Sichern on keeps
     * a period already set and else saves every turn (1), off is 0.
     *
     * @param co The client's options.
     * @param name A name of {@link ClassicPrefs#CLIENT_OPTIONS}.
     * @param on The state.
     */
    static void setClientValue(ClientOptions co, String name, boolean on) {
        final String id = ClassicPrefs.CLIENT_OPTIONS.get(name);
        if (id == null) throw new IllegalArgumentException("No FreeCol option row " + name);
        if (ClientOptions.AUTOSAVE_PERIOD.equals(id)) {
            final IntegerOption o = co.getOption(id, IntegerOption.class);
            if (!on) {
                o.setValue(0);
            } else if (o.getValue() <= 0) {
                o.setValue(1);
            }
            return;
        }
        co.getOption(id, BooleanOption.class).setValue(on);
    }

    /**
     * Put what the box set last for the FreeCol option rows back into the
     * client's options (the game view's start): FreeCol keeps its own
     * option file only through its own options dialog, which the Classic
     * UI does not show.  Rows the box never set keep FreeCol's value.
     *
     * @param co The client's options, or null.
     * @param prefs The prefs.
     */
    static void applyRemembered(ClientOptions co, ClassicPrefs prefs) {
        if (co == null) return;
        for (String name : ClassicPrefs.CLIENT_OPTIONS.keySet()) {
            final Boolean v = prefs.remembered(name);
            if (v == null) continue;
            try {
                setClientValue(co, name, v);
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Classic options: no FreeCol option "
                    + name, e);
            }
        }
    }


    // The colony reports

    /**
     * The notices the colony report options let through
     * ({@link ClassicPrefs#REPORTS}); each one held back is logged
     * ({@code report-off}).
     *
     * @param messages The notices.
     * @param prefs The prefs.
     * @return A new list, in their order.
     */
    static List<ModelMessage> reportsShown(List<ModelMessage> messages,
                                           ClassicPrefs prefs) {
        final List<ModelMessage> out = new ArrayList<>();
        if (messages == null) return out;
        for (ModelMessage m : messages) {
            if (m == null) continue;
            if (prefs.showsReport(m.getId())) {
                out.add(m);
            } else {
                ClassicFrameRecorder.event("report-off", m.getId() + " "
                    + ClassicPrefs.REPORTS.get(m.getId()) + "=false");
            }
        }
        return out;
    }
}
