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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import javax.swing.KeyStroke;

import net.sf.freecol.client.gui.GUI;
import net.sf.freecol.client.gui.action.ColopediaAction;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;


/**
 * What the original's six in-game menus contain and what each item does
 * here: pure data plus predicates, no Swing, so the strip
 * ({@link ClassicMenuStrip}), the preview harness and the unit tests share it.
 *
 * <p><b>Strings.</b>  Tracked code holds only MENU.TXT section names and item
 * indices (0 = the first item after the title line); the words come from
 * the pack ({@link ClassicText#menu}).  The '~' markup of MENU.TXT gives the
 * gold hotkey letters.
 *
 * <p><b>Groups.</b>  MENU.TXT has no separators; the game hard-codes them.
 * The groups below reproduce every captured dropdown with 0 differing pixels
 * (Steam {@code 001}-{@code 005}, start-sequence {@code 053}).  A separator
 * is drawn between two groups that both have a visible item; an empty group
 * adds none.  BEFEHLE's grouping of items never seen (cargo, ships, pillage)
 * is inferred; COLONIPÄDIE has no capture and is one group.
 *
 * <p><b>Visible, greyed, usable -- three separate questions.</b>
 * <ul>
 *   <li><em>Visible</em> ({@link #isVisible}): BEFEHLE lists only the orders
 *       that apply to the active unit's kind and tile (the manual, and
 *       capture 001: a colonist on a forest tile with a road and no colony
 *       shows 10 of 20 orders).  The other menus list every item.</li>
 *   <li><em>Greyed</em> ({@link #isGreyed}) is how a row LOOKS, and follows
 *       the original: only a listed BEFEHLE order that does not fit the
 *       unit's situation right now is drawn grey (001: clearing without
 *       tools, a road where one exists) -- i.e. the order has a wired engine
 *       action and {@code FreeColAction.isEnabled} (which already follows
 *       the active unit) says no.  Every other row is drawn in the normal
 *       ink, as in the captures: 053 shows the independence row in normal
 *       ink in 1492, when it cannot possibly be used, and 001-005 show every
 *       SPIEL/ANSICHT/BERICHTE/HANDEL row in normal ink.</li>
 *   <li><em>Usable</em> ({@link #isLive}) is what a row DOES: it has an engine
 *       action, its classic GUI seam is wired (not in {@link #NOOP_SEAMS})
 *       and the action is enabled.  A row in normal ink that is not usable --
 *       no engine equivalent, or a no-op seam -- is <em>inert</em>: the bar
 *       may sit on it, and firing it only tells the player that the feature
 *       follows later ({@code ClassicMenuStrip.Host#unavailable}).</li>
 * </ul>
 * (An earlier version greyed every inert row, which put 7 of the 9 SPIEL
 * rows in grey in 1492; an independent review diffed that rule against
 * 053/003/004 at 837/862/788 px instead of the arrow alone.)
 */
final class ClassicMenuModel {

    /** MENU.TXT sections of the six menus, in bar order (@CUP is the cheat menu, never shown). */
    static final String[] SECTIONS = { "GAME", "VIEW", "ORDERS", "REPORTS", "TRADE", "PEDIA" };

    /** Menu indices. */
    static final int SPIEL = 0, ANSICHT = 1, BEFEHLE = 2, BERICHTE = 3,
        HANDEL = 4, PEDIA = 5;

    /**
     * Actions whose classic GUI seam is still the base {@code GUI} no-op, so
     * firing them would do nothing visible: shown greyed until a classic
     * screen exists.  Remove an id when its seam is wired.
     * <ul>
     *   <li>preferencesAction: {@code showClientOptionsDialog} (GUI.java:1966)</li>
     *   <li>saveAction: {@code showSaveDialog} returns null (GUI.java:2475)</li>
     *   <li>openAction: {@code showLoadSaveFileDialog} (not overridden)</li>
     *   <li>declareIndependenceAction: {@code showDeclarationPanel} (GUI.java:2039)</li>
     *   <li>findSettlementAction: {@code showFindSettlementPanel} (GUI.java:2129)</li>
     *   <li>gotoAction: {@code showSelectDestinationDialog} (GUI.java:2504)</li>
     *   <li>assignTradeRouteAction, tradeRouteAction: {@code showTradeRoutePanel} (GUI.java:2577)</li>
     *   <li>colopediaAction.*: {@code showColopediaPanel} (GUI.java:2001)</li>
     * </ul>
     * (zoomIn/zoomOut need no entry: they disable themselves, GUI.canZoomInMap
     * is false, GUI.java:1701.)
     */
    static final Set<String> NOOP_SEAMS = Collections.unmodifiableSet(new HashSet<>(
        Arrays.asList("preferencesAction", "saveAction", "openAction",
                      "declareIndependenceAction", "findSettlementAction",
                      "gotoAction", "assignTradeRouteAction", "tradeRouteAction",
                      pedia(ColopediaAction.PanelType.GOODS),
                      pedia(ColopediaAction.PanelType.UNITS),
                      pedia(ColopediaAction.PanelType.TERRAIN),
                      pedia(ColopediaAction.PanelType.BUILDINGS),
                      pedia(ColopediaAction.PanelType.FATHERS),
                      pedia(ColopediaAction.PanelType.CONCEPTS))));


    /**
     * The active unit's situation, as far as the menus care.  Plain data:
     * built from the game by {@link #of}, or by hand in tests and the
     * preview harness.
     */
    static final class Context {

        /** No active unit, MOVE_UNITS-less view. */
        static final Context NONE = new Context(false, false, false, false,
                                                false, false, false);

        /** Whether there is an active unit. */
        final boolean unit;

        /** Whether it is a ship. */
        final boolean naval;

        /** Whether it carries units or goods (ships, wagon trains). */
        final boolean carrier;

        /** Whether it is an armed land unit (soldier, dragoon, artillery). */
        final boolean armed;

        /** Whether its tile is forested. */
        final boolean forest;

        /** Whether a colony is on its tile. */
        final boolean colony;

        /** Whether the view is in TERRAIN mode (else MOVE_UNITS/END_TURN). */
        final boolean terrainMode;

        Context(boolean unit, boolean naval, boolean carrier, boolean armed,
                boolean forest, boolean colony, boolean terrainMode) {
            this.unit = unit;
            this.naval = naval;
            this.carrier = carrier;
            this.armed = armed;
            this.forest = forest;
            this.colony = colony;
            this.terrainMode = terrainMode;
        }

        /** An active land unit. */
        boolean land() {
            return this.unit && !this.naval;
        }

        /**
         * The context of the live view.
         *
         * @param u The active unit, or null.
         * @param mode The view mode, or null.
         */
        static Context of(Unit u, GUI.ViewMode mode) {
            final boolean terrain = mode == GUI.ViewMode.TERRAIN;
            if (u == null) {
                return new Context(false, false, false, false, false, false,
                                   terrain);
            }
            final Tile t = u.getTile();
            boolean armed = false;
            try {
                armed = !u.isNaval() && u.isOffensiveUnit();
            } catch (RuntimeException e) {
                armed = false;
            }
            return new Context(true, u.isNaval(),
                u.isCarrier(),
                armed,
                t != null && t.isForested(),
                t != null && t.getColony() != null,
                terrain);
        }
    }

    /** One item of a menu. */
    static final class Item {

        /** Menu index ({@link #SPIEL} ...). */
        final int menu;

        /** 0-based item index after the MENU.TXT title line. */
        final int index;

        /** Separator group within the menu. */
        final int group;

        /** The engine action, or null when there is no equivalent. */
        final String actionId;

        /** The original's key for it (shown in the item's text), or null. */
        final KeyStroke key;

        /** Whether the item is listed in this context. */
        final Predicate<Context> visible;

        /**
         * Whether firing does something in this context (else it is ignored,
         * the row stays as drawn): the two view-mode items, which share one
         * toggling action but each mean one direction.
         */
        final Predicate<Context> fires;

        Item(int menu, int index, int group, String actionId, String key,
             Predicate<Context> visible, Predicate<Context> fires) {
            this.menu = menu;
            this.index = index;
            this.group = group;
            this.actionId = actionId;
            this.key = (key == null) ? null : KeyStroke.getKeyStroke(key);
            this.visible = visible;
            this.fires = fires;
        }

        @Override
        public String toString() {
            return SECTIONS[this.menu] + "[" + this.index + "]=" + this.actionId;
        }
    }

    /** One dropdown slot: a separator (item null) or a listed item. */
    static final class Slot {

        /** The item, or null for a separator line. */
        final Item item;

        /** Whether firing the row runs its engine action now ({@link #isLive}). */
        final boolean enabled;

        /** Whether the row is drawn in the grey ink ({@link #isGreyed}). */
        final boolean greyed;

        Slot(Item item, boolean enabled, boolean greyed) {
            this.item = item;
            this.enabled = enabled;
            this.greyed = greyed;
        }

        /** Whether the bar may sit on this slot: a row in the normal ink. */
        boolean selectable() {
            return this.item != null && !this.greyed;
        }
    }


    private static final Predicate<Context> ALWAYS = c -> true;
    private static final Predicate<Context> NEVER = c -> false;
    private static final Predicate<Context> UNIT = c -> c.unit;
    private static final Predicate<Context> LAND = Context::land;
    private static final Predicate<Context> NAVAL = c -> c.unit && c.naval;
    private static final Predicate<Context> CARRIER = c -> c.unit && c.carrier;

    /** Every item, menu by menu, in MENU.TXT order. */
    private static final List<Item> ITEMS = Collections.unmodifiableList(Arrays.asList(
        // SPIEL (@GAME, MENU.TXT:17-26).  Groups [0,1] [2,3] [4,5] [6] [7,8].
        new Item(SPIEL, 0, 0, "preferencesAction", null, ALWAYS, ALWAYS),
        new Item(SPIEL, 1, 0, null, null, ALWAYS, ALWAYS),     // colony report options
        new Item(SPIEL, 2, 1, null, null, ALWAYS, ALWAYS),     // sound options
        new Item(SPIEL, 3, 1, null, null, ALWAYS, ALWAYS),     // choose music
        new Item(SPIEL, 4, 2, "saveAction", null, ALWAYS, ALWAYS),
        new Item(SPIEL, 5, 2, "openAction", null, ALWAYS, ALWAYS),
        new Item(SPIEL, 6, 3, "declareIndependenceAction", null, ALWAYS, ALWAYS),
        new Item(SPIEL, 7, 4, "retireAction", null, ALWAYS, ALWAYS),
        new Item(SPIEL, 8, 4, "quitAction", null, ALWAYS, ALWAYS),
        // ANSICHT (@VIEW, :29-41).  Groups [0-2] [3] [4,5] [6-9] [10,11].
        // Rows 0/1 share the toggle; each fires only towards its own mode.
        new Item(ANSICHT, 0, 0, "toggleViewModeAction", "M", ALWAYS, c -> c.terrainMode),
        new Item(ANSICHT, 1, 0, "toggleViewModeAction", "V", ALWAYS, c -> !c.terrainMode),
        new Item(ANSICHT, 2, 0, "europeAction", "E", ALWAYS, ALWAYS),
        new Item(ANSICHT, 3, 1, "findSettlementAction", null, ALWAYS, ALWAYS),
        new Item(ANSICHT, 4, 2, "zoomInAction", "Z", ALWAYS, ALWAYS),
        new Item(ANSICHT, 5, 2, "zoomOutAction", "X", ALWAYS, ALWAYS),
        new Item(ANSICHT, 6, 3, null, null, ALWAYS, ALWAYS),   // zoom level presets
        new Item(ANSICHT, 7, 3, null, null, ALWAYS, ALWAYS),
        new Item(ANSICHT, 8, 3, null, null, ALWAYS, ALWAYS),
        new Item(ANSICHT, 9, 3, null, null, ALWAYS, ALWAYS),
        new Item(ANSICHT, 10, 4, null, "H", ALWAYS, ALWAYS),   // hidden terrain
        new Item(ANSICHT, 11, 4, "centerAction", "C", ALWAYS, ALWAYS),
        // BEFEHLE (@ORDERS, :44-64).  Groups [0-4] [5-9] [10-12] [13-16] [17] [18,19].
        new Item(BEFEHLE, 0, 0, "clearOrdersAction", "A", ALWAYS, ALWAYS),
        new Item(BEFEHLE, 1, 0, "waitAction", "W", ALWAYS, ALWAYS),
        new Item(BEFEHLE, 2, 0, "fortifyAction", "F", UNIT, ALWAYS),
        new Item(BEFEHLE, 3, 0, "fortifyAction", null, NEVER, ALWAYS), // 2nd line: context unknown
        new Item(BEFEHLE, 4, 0, "sentryAction", "S", UNIT, ALWAYS),
        new Item(BEFEHLE, 5, 1, "buildColonyAction", "B", c -> c.land() && !c.colony, ALWAYS),
        new Item(BEFEHLE, 6, 1, "buildColonyAction", "B", c -> c.land() && c.colony, ALWAYS),
        new Item(BEFEHLE, 7, 1, "clearForestAction", "P", c -> c.land() && c.forest, ALWAYS),
        new Item(BEFEHLE, 8, 1, "plowAction", "P", c -> c.land() && !c.forest, ALWAYS),
        new Item(BEFEHLE, 9, 1, "roadAction", "R", LAND, ALWAYS),
        new Item(BEFEHLE, 10, 2, "loadAction", "L", CARRIER, ALWAYS),
        new Item(BEFEHLE, 11, 2, "unloadAction", "U", c -> CARRIER.test(c) && c.colony, ALWAYS),
        new Item(BEFEHLE, 12, 2, null, null, c -> c.land() && c.armed, ALWAYS), // pillage
        new Item(BEFEHLE, 13, 3, "gotoAction", "G", NAVAL, ALWAYS),
        new Item(BEFEHLE, 14, 3, "gotoAction", "G", LAND, ALWAYS),
        new Item(BEFEHLE, 15, 3, "assignTradeRouteAction", "T", CARRIER, ALWAYS),
        new Item(BEFEHLE, 16, 3, null, null, NAVAL, ALWAYS),   // back to Europe
        new Item(BEFEHLE, 17, 4, "skipUnitAction", "SPACE", ALWAYS, ALWAYS),
        new Item(BEFEHLE, 18, 5, "unloadAction", "O",
                 c -> CARRIER.test(c) && c.naval && !c.colony, ALWAYS),
        new Item(BEFEHLE, 19, 5, "disbandUnitAction", "shift D", UNIT, ALWAYS),
        // BERICHTE (@REPORTS, :67-77).  Groups [0] [1-4] [5-8] [9].
        new Item(BERICHTE, 0, 0, null, "F1", ALWAYS, ALWAYS),  // terrain information
        new Item(BERICHTE, 1, 1, "reportReligionAction", "F2", ALWAYS, ALWAYS),
        new Item(BERICHTE, 2, 1, "reportCongressAction", "F3", ALWAYS, ALWAYS),
        new Item(BERICHTE, 3, 1, "reportLabourAction", "F4", ALWAYS, ALWAYS),
        new Item(BERICHTE, 4, 1, "reportTradeAction", "F5", ALWAYS, ALWAYS),
        new Item(BERICHTE, 5, 2, "reportColonyAction", "F6", ALWAYS, ALWAYS),
        new Item(BERICHTE, 6, 2, "reportNavalAction", "F7", ALWAYS, ALWAYS),
        new Item(BERICHTE, 7, 2, "reportForeignAction", "F8", ALWAYS, ALWAYS),
        new Item(BERICHTE, 8, 2, "reportIndianAction", "F9", ALWAYS, ALWAYS),
        new Item(BERICHTE, 9, 3, "reportHighScoresAction", "F10", ALWAYS, ALWAYS),
        // HANDEL (@TRADE, :81-84).  One group; all three are FreeCol's one panel.
        new Item(HANDEL, 0, 0, "tradeRouteAction", null, ALWAYS, ALWAYS),
        new Item(HANDEL, 1, 0, "tradeRouteAction", null, ALWAYS, ALWAYS),
        new Item(HANDEL, 2, 0, "tradeRouteAction", null, ALWAYS, ALWAYS),
        // COLONIPÄDIE (@PEDIA, :101-109).  One group (no capture).
        new Item(PEDIA, 0, 0, pedia(ColopediaAction.PanelType.GOODS), null, ALWAYS, ALWAYS),
        new Item(PEDIA, 1, 0, pedia(ColopediaAction.PanelType.UNITS), null, ALWAYS, ALWAYS),
        new Item(PEDIA, 2, 0, pedia(ColopediaAction.PanelType.TERRAIN), null, ALWAYS, ALWAYS),
        new Item(PEDIA, 3, 0, null, null, ALWAYS, ALWAYS),     // colonist skills
        new Item(PEDIA, 4, 0, pedia(ColopediaAction.PanelType.BUILDINGS), null, ALWAYS, ALWAYS),
        new Item(PEDIA, 5, 0, pedia(ColopediaAction.PanelType.FATHERS), null, ALWAYS, ALWAYS),
        new Item(PEDIA, 6, 0, pedia(ColopediaAction.PanelType.CONCEPTS), null, ALWAYS, ALWAYS),
        new Item(PEDIA, 7, 0, null, null, ALWAYS, ALWAYS)      // complete
    ));

    /** BEFEHLE items listed even without an active unit (activate, wait, no orders). */
    private static final Set<Integer> ORDERS_WITHOUT_UNIT
        = new HashSet<>(Arrays.asList(0, 1, 17));


    private ClassicMenuModel() {}   // data only


    /** The FreeCol id of a Colopedia panel's action (as FreeColMenuBar.java:238-250 builds it). */
    static String pedia(ColopediaAction.PanelType type) {
        return ColopediaAction.id + type.getKey();
    }

    /** @return Every item of menu {@code menu}, in MENU.TXT order. */
    static List<Item> items(int menu) {
        final List<Item> out = new ArrayList<>();
        for (Item it : ITEMS) {
            if (it.menu == menu) out.add(it);
        }
        return out;
    }

    /** @return Every item of every menu. */
    static List<Item> allItems() {
        return ITEMS;
    }

    /** Whether {@code it} is listed in context {@code c}. */
    static boolean isVisible(Item it, Context c) {
        if (it.menu == BEFEHLE && !c.unit) return ORDERS_WITHOUT_UNIT.contains(it.index);
        return it.visible.test(c);
    }

    /**
     * Whether an item can be used through the engine: it has an action id,
     * the id is not a no-op seam, and the action says it is enabled.
     *
     * @param it The item.
     * @param actionEnabled Whether an action id is present and enabled.
     */
    static boolean isLive(Item it, Predicate<String> actionEnabled) {
        return it.actionId != null && !NOOP_SEAMS.contains(it.actionId)
            && actionEnabled.test(it.actionId);
    }

    /**
     * Whether a listed item is drawn in the grey ink: only a BEFEHLE order
     * whose engine action exists, is wired, and is disabled right now (see
     * the class comment; 001's two grey rows).  Rows without an engine
     * equivalent and rows behind a no-op seam keep the normal ink.
     *
     * @param it The item.
     * @param actionEnabled Whether an action id is present and enabled.
     */
    static boolean isGreyed(Item it, Predicate<String> actionEnabled) {
        return it.menu == BEFEHLE && it.actionId != null
            && !NOOP_SEAMS.contains(it.actionId)
            && !actionEnabled.test(it.actionId);
    }

    /**
     * The slots of a dropdown: the visible items, with a separator between
     * groups that both list something, each with its usable ({@link #isLive})
     * and greyed ({@link #isGreyed}) state.  This is the production rule: the
     * strip and the preview harness both call it, so the harness diffs what
     * the player sees.
     *
     * @param menu The menu index.
     * @param c The context.
     * @param actionEnabled Whether an engine action id is present and
     *     enabled (production: the action manager).
     * @return The slots, top to bottom.
     */
    static List<Slot> slots(int menu, Context c, Predicate<String> actionEnabled) {
        final List<Slot> out = new ArrayList<>();
        int lastGroup = -1;
        for (Item it : items(menu)) {
            if (!isVisible(it, c)) continue;
            if (lastGroup >= 0 && it.group != lastGroup) {
                out.add(new Slot(null, false, false));
            }
            lastGroup = it.group;
            out.add(new Slot(it, isLive(it, actionEnabled),
                             isGreyed(it, actionEnabled)));
        }
        return out;
    }

    /**
     * The hotkey of a MENU.TXT item: its only '~'-marked character, as an
     * upper-case letter, or 0 when the item marks none or several (the
     * F-key rows of BERICHTE mark every character of "F1").
     */
    static char hotkey(String marked) {
        if (marked == null) return 0;
        char found = 0;
        for (int i = 0; i + 1 < marked.length(); i++) {
            if (marked.charAt(i) != '~') continue;
            if (found != 0) return 0;
            found = Character.toUpperCase(marked.charAt(i + 1));
            i++;
        }
        return Character.isLetter(found) ? found : 0;
    }
}
