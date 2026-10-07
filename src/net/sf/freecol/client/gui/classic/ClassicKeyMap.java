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

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

import net.sf.freecol.client.gui.GUI;


/**
 * The original's order and report <b>keys</b>, bound explicitly to the
 * engine's {@code FreeColAction}s.
 *
 * <p><b>Why this table exists.</b>  FreeCol's accelerators (F, S, L, the
 * F-keys ...) only ever fired because its Swing {@code JMenuBar} was
 * installed: Swing hands a menu item's accelerator to its action through the
 * window's menu bar.  The in-game HUD now paints the original's menu strip
 * ({@link ClassicMenuStrip}) and has no {@code JMenuBar}, so without this
 * table every key but the map viewer's own (arrows, numpad, Enter, Space, W,
 * B) would silently stop working.  It also gives the keys the original's
 * meaning (the manual's key list and the hotkeys MENU.TXT prints) rather
 * than FreeCol's, which ends the old F7 clash between the Naval and Military
 * reports.
 *
 * <p><b>Precedence.</b>  The bindings are {@code WHEN_IN_FOCUSED_WINDOW} on
 * the HUD pane and skip every keystroke the map viewer already binds
 * ({@link ClassicMapViewer#BOUND_KEYS}): with two such bindings for one key
 * Swing's {@code KeyboardManager} would pick by registration order, while
 * the map's own keys (moves, end of turn, wait, build colony) must keep
 * winning as they did over the old menu bar's accelerators.
 *
 * <p>A binding with several actions fires the first one that is enabled
 * (P: clear the forest, else plough).  Actions whose classic screen does not
 * exist yet ({@link ClassicMenuModel#NOOP_SEAMS}) are skipped.  The four
 * FreeCol reports without an original menu entry keep a key: Cargo
 * (Shift+F1), Exploration (Shift+F2) and Production (Shift+F4) on their
 * FreeCol keys, Military moved from F7 (now Naval, as in the original) to
 * Shift+F7 -- an owner decision, flagged in the README.
 *
 * <p>A binding may also carry the visibility rule of the BEFEHLE row that
 * prints its key ({@link Binding#when}), so the key fires exactly where the
 * row is listed: U only in a colony, O only for a ship outside one (both are
 * FreeCol's {@code unloadAction}, which would otherwise unload a whole ship
 * in a colony on O, or dump cargo at sea on U without asking).
 *
 * <p>Ctrl+N is FreeCol's "new game" ({@code newAction}): the in-game way
 * back to the classic title, which dropping the {@code JMenuBar} had
 * removed.  It has no original key (the original leaves a game only by
 * retiring or quitting); it asks before it stops the game.
 */
final class ClassicKeyMap {

    private static final Logger logger = Logger.getLogger(ClassicKeyMap.class.getName());

    /** When a binding may fire, by view mode. */
    static final int ANY_MODE = 0, TERRAIN_ONLY = 1, NOT_TERRAIN = 2;

    /** One key. */
    static final class Binding {

        /** The keystroke. */
        final KeyStroke key;

        /** The actions, the first enabled one fires. */
        final List<String> actionIds;

        /** {@link #ANY_MODE}, {@link #TERRAIN_ONLY} or {@link #NOT_TERRAIN}. */
        final int mode;

        /**
         * When the key may fire in the active unit's situation, or null for
         * always: the visibility rule of the BEFEHLE row that prints the key,
         * so a key does exactly what its menu row would.
         */
        final Predicate<ClassicMenuModel.Context> when;

        Binding(String key, int mode, String... actionIds) {
            this(key, mode, null, actionIds);
        }

        Binding(String key, int mode, Predicate<ClassicMenuModel.Context> when,
                String... actionIds) {
            this.key = KeyStroke.getKeyStroke(key);
            this.mode = mode;
            this.when = when;
            this.actionIds = Collections.unmodifiableList(Arrays.asList(actionIds));
        }

        @Override
        public String toString() {
            return this.key + "->" + this.actionIds;
        }
    }

    /** The table (see the class comment and the classic README). */
    private static final List<Binding> BINDINGS = Collections.unmodifiableList(Arrays.asList(
        // BEFEHLE
        new Binding("A", ANY_MODE, "clearOrdersAction"),
        new Binding("F", ANY_MODE, "fortifyAction"),
        new Binding("S", ANY_MODE, "sentryAction"),
        new Binding("L", ANY_MODE, "loadAction"),
        // U and O share FreeCol's unloadAction, which unloads everything in a
        // colony and silently dumps the goods elsewhere
        // (InGameController.unload); each key fires only where its BEFEHLE
        // row is listed: U in a colony (row 11), O for a ship outside one
        // (row 18, dump cargo).
        new Binding("U", ANY_MODE, c -> orderListed(11, c), "unloadAction"),
        new Binding("O", ANY_MODE, c -> orderListed(18, c), "unloadAction"),
        new Binding("T", ANY_MODE, "assignTradeRouteAction"),
        new Binding("shift D", ANY_MODE, "disbandUnitAction"),
        new Binding("P", ANY_MODE, "clearForestAction", "plowAction"),
        // R: the gold letter of St~raße bauen (land units) and of Zu~rück
        // nach Europa (ships); each action is enabled only for its kind.
        new Binding("R", ANY_MODE, "roadAction", "returnToEuropeAction"),
        new Binding("G", ANY_MODE, "gotoAction"),
        // ANSICHT
        new Binding("M", TERRAIN_ONLY, "toggleViewModeAction"),
        new Binding("V", NOT_TERRAIN, "toggleViewModeAction"),
        new Binding("E", ANY_MODE, "europeAction"),
        new Binding("Z", ANY_MODE, "zoomInAction"),
        new Binding("X", ANY_MODE, "zoomOutAction"),
        new Binding("C", ANY_MODE, "centerAction"),
        // BERICHTE
        new Binding("F2", ANY_MODE, "reportReligionAction"),
        new Binding("F3", ANY_MODE, "reportCongressAction"),
        new Binding("F4", ANY_MODE, "reportLabourAction"),
        new Binding("F5", ANY_MODE, "reportTradeAction"),
        new Binding("F6", ANY_MODE, "reportColonyAction"),
        new Binding("F7", ANY_MODE, "reportNavalAction"),
        new Binding("F8", ANY_MODE, "reportForeignAction"),
        new Binding("F9", ANY_MODE, "reportIndianAction"),
        new Binding("F10", ANY_MODE, "reportHighScoresAction"),
        // FreeCol's extra reports (no original menu entry)
        new Binding("shift F1", ANY_MODE, "reportCargoAction"),
        new Binding("shift F2", ANY_MODE, "reportExplorationAction"),
        new Binding("shift F4", ANY_MODE, "reportProductionAction"),
        new Binding("shift F7", ANY_MODE, "reportMilitaryAction"),
        // FreeCol's own way back to the title (no original key: the
        // original leaves only by retiring or quitting to DOS).  It asks
        // first (confirmStopGame), then lands on the classic title
        // (ClassicGUI.showNewPanel -> showMainPanel).
        new Binding("control N", ANY_MODE, "newAction")
    ));

    /**
     * The actions whose key still fires while the player waits (build spec
     * W5d): only Ctrl+N, FreeCol's way back to the title, which asks first.
     * Every original key is inert in the pause before the end of turn, the
     * next unit or the turn's first unit, and in the AI phase, as the
     * map's own keys are (FINAL "Open" item 7).
     */
    static final Set<String> WHILE_WAITING = Set.of("newAction");

    /**
     * Whether a binding may fire while the player waits
     * ({@link #WHILE_WAITING}).
     *
     * @param b The binding.
     * @param waiting Whether the player waits now.
     * @return True if it may fire.
     */
    static boolean waitAllows(Binding b, boolean waiting) {
        return !waiting || WHILE_WAITING.containsAll(b.actionIds);
    }

    /** Whether BEFEHLE row {@code index} is listed in context {@code c}. */
    static boolean orderListed(int index, ClassicMenuModel.Context c) {
        return ClassicMenuModel.isVisible(
            ClassicMenuModel.items(ClassicMenuModel.BEFEHLE).get(index), c);
    }

    /**
     * Whether a binding may fire in the active unit's situation.
     *
     * @param b The binding.
     * @param c The context, or null when unknown (then only unconditional
     *     bindings fire).
     */
    static boolean contextAllows(Binding b, ClassicMenuModel.Context c) {
        if (b.when == null) return true;
        return c != null && b.when.test(c);
    }


    private ClassicKeyMap() {}   // static table only


    /** @return The whole table. */
    static List<Binding> bindings() {
        return BINDINGS;
    }

    /** The map viewer's keystrokes ({@link ClassicMapViewer#BOUND_KEYS}). */
    static Set<KeyStroke> mapViewerKeys() {
        final Set<KeyStroke> out = new HashSet<>();
        for (String k : ClassicMapViewer.BOUND_KEYS) out.add(KeyStroke.getKeyStroke(k));
        return out;
    }

    /**
     * Whether a binding may fire in a view mode.
     *
     * @param b The binding.
     * @param terrainMode Whether the view is in TERRAIN mode.
     */
    static boolean modeAllows(Binding b, boolean terrainMode) {
        switch (b.mode) {
        case TERRAIN_ONLY: return terrainMode;
        case NOT_TERRAIN: return !terrainMode;
        default: return true;
        }
    }

    /**
     * The action a binding fires now: the first of its ids that is not a
     * no-op seam and whose action is enabled.
     *
     * @param lookup The action manager's lookup by id.
     * @return The id, or null when none can fire.
     */
    static String pick(Binding b, Predicate<String> enabled) {
        for (String id : b.actionIds) {
            if (ClassicMenuModel.NOOP_SEAMS.contains(id)) continue;
            if (enabled.test(id)) return id;
        }
        return null;
    }

    /**
     * Bind the table on {@code host}, leaving the keys of the map viewer
     * (which may sit in {@code mapViewer}'s own input map) alone.
     *
     * @param host The HUD pane.
     * @param mapViewer The map viewer, whose {@code WHEN_IN_FOCUSED_WINDOW}
     *     keys win; null to skip only {@link ClassicMapViewer#BOUND_KEYS}.
     * @param lookup The action manager's lookup by id.
     * @param mode The current view mode.
     * @param context The active unit's situation (the menus' context).
     * @param blocked True while input must not act (the first scene, an
     *     open menu).
     * @param waiting True while the player waits (the turn flow's pauses
     *     and the AI phase, {@link #waitAllows}); such a key is logged as
     *     {@code key-blocked} for the recorder.
     * @return The keystrokes bound.
     */
    static List<KeyStroke> install(JComponent host, JComponent mapViewer,
                                   Function<String, Action> lookup,
                                   Supplier<GUI.ViewMode> mode,
                                   Supplier<ClassicMenuModel.Context> context,
                                   BooleanSupplier blocked,
                                   BooleanSupplier waiting) {
        return install(host, mapViewer, lookup, mode, context, blocked, waiting,
                       null);
    }

    /**
     * {@link #install(JComponent, JComponent, Function, Supplier, Supplier,
     * BooleanSupplier, BooleanSupplier)}, and a key whose actions are all
     * disabled now (none can fire) goes to {@code refused}: the Classic UI
     * answers P and R there with the original's refusal (R3,
     * {@link ClassicGUI#orderRefused}).
     *
     * @param refused Takes the binding of a key that cannot fire now, or
     *     null.
     * @return The keystrokes bound.
     */
    static List<KeyStroke> install(JComponent host, JComponent mapViewer,
                                   Function<String, Action> lookup,
                                   Supplier<GUI.ViewMode> mode,
                                   Supplier<ClassicMenuModel.Context> context,
                                   BooleanSupplier blocked,
                                   BooleanSupplier waiting,
                                   Consumer<Binding> refused) {
        final Set<KeyStroke> skip = mapViewerKeys();
        if (mapViewer != null) {
            final KeyStroke[] ks = mapViewer.getInputMap(
                JComponent.WHEN_IN_FOCUSED_WINDOW).allKeys();
            if (ks != null) skip.addAll(Arrays.asList(ks));
        }
        final InputMap im = host.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        final ActionMap am = host.getActionMap();
        final List<KeyStroke> bound = new ArrayList<>();
        for (Binding b : BINDINGS) {
            if (b.key == null || skip.contains(b.key)) continue;
            final String name = "classicKey." + b.key;
            im.put(b.key, name);
            am.put(name, new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        if (blocked.getAsBoolean()) return;
                        if (!waitAllows(b, waiting.getAsBoolean())) {
                            ClassicFrameRecorder.event("key-blocked", b.key.toString());
                            return;
                        }
                        if (!modeAllows(b, mode.get() == GUI.ViewMode.TERRAIN)) return;
                        if (!contextAllows(b, context.get())) return;
                        final String id = pick(b, i -> {
                                final Action a = lookup.apply(i);
                                return a != null && a.isEnabled();
                            });
                        if (id == null) {
                            if (refused != null) refused.accept(b);
                            return;
                        }
                        final Action a = lookup.apply(id);
                        if (a != null) {
                            a.actionPerformed(new ActionEvent(host,
                                ActionEvent.ACTION_PERFORMED, id));
                        }
                    }
                });
            bound.add(b.key);
        }
        logger.info("Classic key map: " + bound.size() + " keys bound.");
        return bound;
    }
}
