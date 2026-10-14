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

import java.awt.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.SwingUtilities;


/**
 * The classic screens hosted in the main window (master plan M4.0, build
 * spec {@code prep2/M40-mainwindow.md}): the colony screen, its build list
 * and the Europe screen replace the map in the one 320x200 picture, as in
 * the original (V clip 020 #2260, #24106 -&gt; #24111, #28207 -&gt;
 * #28238; the manual l.83: "you are immediately shown the colony
 * display").  Before, each was a {@code JFrame} of its own.
 *
 * <p>The screens lie in the HUD pane ({@link ClassicHudPane}) on
 * {@link ClassicHudPane#SCREEN_LAYER}, above the menu strip's dropdowns,
 * each with its wood boxes on {@link ClassicHudPane#SCREEN_BOX_LAYER} just
 * above; the map's own box layer ({@link ClassicHudPane#BOX_LAYER}) and the
 * arrow stay on top of both, so no box can be hidden under a screen.  While
 * a screen is up the game parts (the map, the right panel, the strip and
 * its dropdowns) are hidden, not removed, so they keep their state and
 * their window-wide keys are skipped as not showing; only the top screen
 * of the stack is visible.  Each screen covers the whole pane and
 * letterboxes itself with the HUD's own formula
 * ({@link ClassicHudOverlay#canvas}), so its pixels lie on the HUD's grid
 * and on the recorder's sampling grid.
 *
 * <p>A screen may belong over another one (the build list over its
 * colony): its pop goes with its parent's.  A screen popped from under
 * another one (the older screen when a newer one replaced it, decision H5)
 * goes without any visible change; when the top goes, the next one down,
 * or the game parts, show in the same paint (V #11159: 1-2 frames, one torn
 * frame).  Swing only, no game: tested headless
 * ({@code ClassicScreenStackTest}).  EDT only.
 */
final class ClassicScreenStack {

    /** What a hosted screen is (the recorder's names). */
    enum Kind {
        COLONY("colony"), BUILD_LIST("buildlist"), EUROPE("europe");

        /** The recorder's name. */
        final String id;

        Kind(String id) {
            this.id = id;
        }
    }

    /** One hosted screen: what it is, its component, its boxes, its name. */
    static final class Slot {

        final Kind kind;
        final JComponent screen;

        /** The wood boxes over it, or null (the build list: its colony's). */
        final ClassicAdvisorLayer boxes;

        /** The colony's or Europe's name (for the recorder). */
        final String name;

        /** The screen it belongs over (the build list's colony), or null. */
        final JComponent parent;

        Slot(Kind kind, JComponent screen, ClassicAdvisorLayer boxes,
             String name, JComponent parent) {
            this.kind = kind;
            this.screen = screen;
            this.boxes = boxes;
            this.name = name;
            this.parent = parent;
        }

        @Override
        public String toString() {
            return this.kind.id + ":" + this.name;
        }
    }

    /** The pane the screens lie in (the HUD pane). */
    private final JLayeredPane pane;

    /** The game parts hidden while a screen is up. */
    private final List<Component> parts;

    /** Run before a push (the strip's open menu closes), or null. */
    private final Runnable beforePush;

    /**
     * Gives the keys to whatever should have them after a push or a pop
     * ({@code ClassicGUI.focusHome}), or null: the top screen, else
     * nothing.
     */
    private final Runnable focusHome;

    /** The screens, the bottom first. */
    private final List<Slot> slots = new ArrayList<>();

    /** The game parts' visibility when the first screen came. */
    private final Map<Component, Boolean> partsShown = new LinkedHashMap<>();

    /** The top screen for the harness's probe (any thread), or null. */
    private volatile String probe = null;


    /**
     * @param pane The pane the screens lie in.
     * @param parts The game parts, hidden while a screen is up.
     * @param beforePush Run before each push (the strip's menu closes), or
     *     null.
     * @param focusHome Gives the keys back after a push or a pop, or null
     *     (then the top screen gets them).
     */
    ClassicScreenStack(JLayeredPane pane, List<? extends Component> parts,
                       Runnable beforePush, Runnable focusHome) {
        this.pane = pane;
        this.parts = Collections.unmodifiableList(new ArrayList<>(parts));
        this.beforePush = beforePush;
        this.focusHome = focusHome;
    }


    // State

    /** @return Whether a screen is hosted. */
    boolean isHosting() {
        return !this.slots.isEmpty();
    }

    /** @return The top screen's slot, or null. */
    Slot top() {
        return (this.slots.isEmpty()) ? null : this.slots.get(this.slots.size() - 1);
    }

    /** @return The top screen, or null. */
    JComponent topScreen() {
        final Slot s = top();
        return (s == null) ? null : s.screen;
    }

    /**
     * @return The box layer over the top screen: its own, else the
     *     nearest one under it (the build list shows its colony's boxes);
     *     null without a screen or boxes.
     */
    ClassicAdvisorLayer topBoxes() {
        for (int i = this.slots.size() - 1; i >= 0; i--) {
            final ClassicAdvisorLayer l = this.slots.get(i).boxes;
            if (l != null) return l;
        }
        return null;
    }

    /**
     * @param screen A component.
     * @return Whether it is a hosted screen.
     */
    boolean hosts(Component screen) {
        return indexOf(screen) >= 0;
    }

    /**
     * @param kind A kind.
     * @return The highest hosted screen of that kind, or null.
     */
    Slot find(Kind kind) {
        for (int i = this.slots.size() - 1; i >= 0; i--) {
            if (this.slots.get(i).kind == kind) return this.slots.get(i);
        }
        return null;
    }

    /** @return The screens, the bottom first (tests, the harness). */
    List<Slot> slots() {
        return new ArrayList<>(this.slots);
    }

    /** @return The top screen as "kind:name" for the harness probe (any thread), or null. */
    String probe() {
        return this.probe;
    }

    /** @return The game parts (tests). */
    List<Component> parts() {
        return this.parts;
    }

    private int indexOf(Component screen) {
        if (screen == null) return -1;
        for (int i = 0; i < this.slots.size(); i++) {
            if (this.slots.get(i).screen == screen) return i;
        }
        return -1;
    }


    // Push and pop

    /**
     * Put a screen on top: the strip's menu closes, the screen covers the
     * pane at {@link ClassicHudPane#SCREEN_LAYER} with its boxes just above
     * it, the screen under it or the game parts are hidden, Tab stays a
     * key of the screen (no focus traversal), and the keys go to it, now
     * and once more after the focus moves that hiding the map caused
     * ({@code Component.hide} hands the focus on by itself).  A screen
     * that is hosted already is left as it is.
     *
     * @param kind What it is.
     * @param screen The screen.
     * @param boxes Its wood boxes, or null.
     * @param name Its name (for the recorder).
     * @param parent The hosted screen it belongs over, or null.
     * @return Its slot.
     */
    Slot push(Kind kind, JComponent screen, ClassicAdvisorLayer boxes,
              String name, JComponent parent) {
        if (kind == null || screen == null) {
            throw new IllegalArgumentException("push: no screen");
        }
        final int at = indexOf(screen);
        if (at >= 0) return this.slots.get(at);
        if (this.beforePush != null) this.beforePush.run();
        final Slot below = top();
        final Slot s = new Slot(kind, screen, boxes, name,
                                (parent != null && hosts(parent)) ? parent : null);
        final int w = this.pane.getWidth(), h = this.pane.getHeight();
        screen.setBounds(0, 0, w, h);
        screen.setFocusTraversalKeysEnabled(false);
        screen.setVisible(true);
        this.pane.add(screen, ClassicHudPane.SCREEN_LAYER, 0);
        if (boxes != null) {
            boxes.setBounds(0, 0, w, h);
            this.pane.add(boxes, ClassicHudPane.SCREEN_BOX_LAYER, 0);
        }
        if (below != null) {
            below.screen.setVisible(false);
        } else {
            hideParts();
        }
        this.slots.add(s);
        updateProbe();
        ClassicFrameRecorder.event("screen-push", kind.id + " " + name);
        this.pane.revalidate();
        this.pane.repaint();
        giveKeys();
        SwingUtilities.invokeLater(() -> {
                if (topScreen() == screen) giveKeys();
            });
        return s;
    }

    /**
     * Take a screen away, with the screens that belong over it (the build
     * list with its colony): their boxes answer "dismissed" first
     * ({@link ClassicAdvisorLayer#dispose}), then they leave the pane.  When
     * the top went, the next screen down or the game parts show in the
     * same paint and get the keys; a screen popped from under another one
     * goes without a visible change.
     *
     * @param screen The screen.
     * @param why Why it goes (for the recorder).
     * @return False if it was not hosted.
     */
    boolean pop(JComponent screen, String why) {
        final int at = indexOf(screen);
        if (at < 0) return false;
        final Slot before = top();
        final List<Slot> going = new ArrayList<>();
        going.add(this.slots.get(at));
        // A screen comes after the one it belongs over: one pass finds them.
        for (int i = at + 1; i < this.slots.size(); i++) {
            final Slot s = this.slots.get(i);
            for (int j = 0; j < going.size(); j++) {
                if (s.parent != null && s.parent == going.get(j).screen) {
                    going.add(s);
                    break;
                }
            }
        }
        // The highest first: a build list goes before its colony.
        for (int i = going.size() - 1; i >= 0; i--) remove(going.get(i), why);
        final Slot after = top();
        if (after != before) {
            if (after != null) {
                after.screen.setVisible(true);
            } else {
                showParts();
            }
            this.pane.revalidate();
            // The swap in one paint (E1: 1-2 frames).
            this.pane.paintImmediately(0, 0, this.pane.getWidth(), this.pane.getHeight());
            giveKeys();
            SwingUtilities.invokeLater(this::giveKeys);
        }
        updateProbe();
        return true;
    }

    /**
     * Take every screen away (the game view goes): their boxes answer
     * "dismissed", the game parts show again.
     *
     * @param why Why (for the recorder).
     */
    void popAll(String why) {
        while (!this.slots.isEmpty()) pop(this.slots.get(0).screen, why);
    }

    /** One slot off the stack and the pane. */
    private void remove(Slot s, String why) {
        this.slots.remove(s);
        if (s.boxes != null) {
            s.boxes.dispose();
            this.pane.remove(s.boxes);
        }
        this.pane.remove(s.screen);
        ClassicFrameRecorder.event("screen-pop", s.kind.id + " " + why);
    }

    /** Hide the game parts, remembering who was visible. */
    private void hideParts() {
        this.partsShown.clear();
        for (Component c : this.parts) {
            if (c == null) continue;
            this.partsShown.put(c, c.isVisible());
            c.setVisible(false);
        }
    }

    /** Show the game parts as they were. */
    private void showParts() {
        for (Component c : this.parts) {
            if (c == null) continue;
            final Boolean was = this.partsShown.get(c);
            c.setVisible(was == null || was);
        }
        this.partsShown.clear();
    }

    /** The keys after a push or a pop. */
    private void giveKeys() {
        if (this.focusHome != null) {
            this.focusHome.run();
            return;
        }
        final JComponent t = topScreen();
        if (t != null) t.requestFocusInWindow();
    }

    private void updateProbe() {
        final Slot s = top();
        this.probe = (s == null) ? null : s.toString();
    }
}
