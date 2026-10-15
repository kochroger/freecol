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

import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.KeyStroke;

import net.sf.freecol.client.gui.classic.ClassicMainMenuPanel.Mode;
import net.sf.freecol.client.gui.classic.ClassicNewWorldChain.Step;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * Part T1: an input counts only for a screen the player has seen (Roger
 * 2026-10-14 ~22:50, "Ich kann gar nicht auswählen, ob ich in der Neuen
 * Welt, in Amerika oder in einer individualisierten Welt starten will";
 * t/menu-skip.md §4 F1, §5).  Presses queued while the EDT was blocked
 * (the client constructor during the intro's first ~3 s) arrive in a burst
 * with their real {@code getWhen}; the burst now ends on the title, which
 * waits, and every later screen needs its own press made after it was
 * painted.  Headless: the panel is gated off screen
 * ({@code gateOffScreen}), the clock is the test's, and
 * {@code screenPainted()} stands for the paint.
 */
public class ClassicMenuTypeAheadTest extends FreeColTestCase {

    /** The test's clock (ms). */
    private final long[] now = { 100000L };

    private final List<ClassicGUI.NewWorldSetup> started = new ArrayList<>();
    private int quits = 0;

    private ClassicMainMenuPanel panel() {
        final ClassicMainMenuPanel p = new ClassicMainMenuPanel(
            new ClassicMainMenuPanel.Actions() {
                @Override public void newWorld(ClassicGUI.NewWorldSetup s) { started.add(s); }
                @Override public void loadGame(File f) {}
                @Override public void hallOfFame() {}
                @Override public void quit() { quits++; }
            });
        p.setSize(320, 200);
        p.gateOffScreen = true;
        p.clock = () -> now[0];
        return p;
    }

    private static boolean havePack() {
        return ClassicPackFiles.runtime() != null
            && ClassicText.load(ClassicPackFiles.runtime()) != null;
    }

    /**
     * A key as AWT delivers it to the focused panel: the press through
     * processKeyEvent and, when nothing consumed it, the window binding
     * with the press's time (the KeyboardManager path, which has no window
     * here); then the typed character and the release.
     *
     * @return Whether the press was swallowed by the gate.
     */
    private static boolean key(ClassicMainMenuPanel p, int code, char ch, long when) {
        final KeyEvent d = new KeyEvent(p, KeyEvent.KEY_PRESSED, when, 0, code, ch);
        p.processKeyEvent(d);
        if (!d.isConsumed()) binding(p, code, when);
        final boolean swallowed = p.typedAheadSwallowedLast;
        if (ch != KeyEvent.CHAR_UNDEFINED) {
            p.processKeyEvent(new KeyEvent(p, KeyEvent.KEY_TYPED, when, 0,
                                           KeyEvent.VK_UNDEFINED, ch));
        }
        p.processKeyEvent(new KeyEvent(p, KeyEvent.KEY_RELEASED, when + 50, 0, code, ch));
        return swallowed;
    }

    private static boolean key(ClassicMainMenuPanel p, int code, long when) {
        final char ch = (code == KeyEvent.VK_ENTER) ? '\n'
            : (code == KeyEvent.VK_SPACE) ? ' '
            : (code == KeyEvent.VK_ESCAPE) ? (char) 27
            : (code >= KeyEvent.VK_A && code <= KeyEvent.VK_Z) ? (char) ('a' + code - KeyEvent.VK_A)
            : KeyEvent.CHAR_UNDEFINED;
        return key(p, code, ch, when);
    }

    /** The menu's window binding for a key, run with that time. */
    private static void binding(ClassicMainMenuPanel p, int code, long when) {
        final Object name = p.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .get(KeyStroke.getKeyStroke(code, 0));
        if (name == null) return;
        p.getActionMap().get(name).actionPerformed(new ActionEvent(p,
            ActionEvent.ACTION_PERFORMED, name.toString(), when, 0));
    }

    /** A left (or right) press and release at a virtual point. */
    private static void click(ClassicMainMenuPanel p, int x, int y, int button, long when) {
        final int mask = (button == MouseEvent.BUTTON3) ? InputEvent.BUTTON3_DOWN_MASK
            : InputEvent.BUTTON1_DOWN_MASK;
        p.processMouseEvent(new MouseEvent(p, MouseEvent.MOUSE_PRESSED, when, mask,
                                           x, y, 1, false, button));
        p.processMouseEvent(new MouseEvent(p, MouseEvent.MOUSE_RELEASED, when + 80, 0,
                                           x, y, 1, false, button));
    }

    private static int[] titleRow(int item) {
        final Rectangle r = ClassicMenuBox.rowHitRect(ClassicMainMenuPanel.titleBounds(5), 1, item);
        return new int[] { r.x + r.width / 2, r.y + r.height / 2 };
    }

    /**
     * The intro, then a key made at {@code t} that skips it, dispatched
     * 2 s later (the EDT was blocked): the title, not yet painted.
     */
    private ClassicMainMenuPanel skippedIntro(int code, long t) {
        final ClassicMainMenuPanel p = panel();
        p.enterIntro();
        p.screenPainted();
        now[0] = t + 2000;
        assertFalse("the intro takes any key", key(p, code, t));
        assertEquals(Mode.TITLE, p.mode());
        return p;
    }

    /** §5.1: Enter, Enter before the paint, Enter in the settle, Enter after it. */
    public void testTheBurstEndsOnTheTitle() {
        final long t0 = now[0];
        final ClassicMainMenuPanel p = skippedIntro(KeyEvent.VK_ENTER, t0);
        // Roger's case: the 2nd and 3rd Enter were made 300 / 600 ms after the
        // 1st, all before the title existed, dispatched in one burst.
        now[0] = t0 + 2500;
        assertTrue(key(p, KeyEvent.VK_ENTER, t0 + 300));
        assertTrue(key(p, KeyEvent.VK_ENTER, t0 + 600));
        assertEquals(Mode.TITLE, p.mode());
        assertEquals(ClassicMainMenuPanel.ITEM_NEW_WORLD, p.selectedItem());
        now[0] = t0 + 2600;
        p.screenPainted();
        final long paint = now[0];
        assertTrue("in the settle", key(p, KeyEvent.VK_ENTER, paint + 100));
        // 600 ms after the paint but within the burst gap of the last one.
        assertTrue("continues the burst", key(p, KeyEvent.VK_ENTER, paint + 450));
        assertEquals(Mode.TITLE, p.mode());
        if (!havePack()) return;
        now[0] = paint + 1000;
        assertFalse(key(p, KeyEvent.VK_ENTER, paint + 1000));
        assertEquals(Mode.NEW_WORLD, p.mode());
        assertEquals(Step.DIFFICULTY, p.chainView().step);
    }

    /** §5.2: a click on NEUE WELT made before the paint; after the settle it opens the chain. */
    public void testAClickBeforeThePaint() {
        final long t0 = now[0];
        final ClassicMainMenuPanel p = panel();
        p.enterIntro();
        now[0] = t0 + 2000;      // dispatched after the block
        click(p, 160, 100, MouseEvent.BUTTON1, t0);
        assertEquals(Mode.TITLE, p.mode());
        final int[] at = titleRow(ClassicMainMenuPanel.ITEM_NEW_WORLD);
        // Roger's second click, on the NEUE WELT row, 700 ms after the first.
        click(p, at[0], at[1], MouseEvent.BUTTON1, t0 + 700);
        assertEquals(Mode.TITLE, p.mode());
        p.screenPainted();
        click(p, at[0], at[1], MouseEvent.BUTTON1, now[0] + 300);
        assertEquals("the settle", Mode.TITLE, p.mode());
        if (!havePack()) return;
        click(p, at[0], at[1], MouseEvent.BUTTON1, now[0] + 900);
        assertEquals(Mode.NEW_WORLD, p.mode());
    }

    /** §5.2b: Enters every 300 ms after the paint are all ignored; a pause, then one works. */
    public void testMashingStaysOnTheTitle() {
        final long t0 = now[0];
        final ClassicMainMenuPanel p = skippedIntro(KeyEvent.VK_ENTER, t0);
        now[0] += 50;
        p.screenPainted();
        final long paint = now[0];
        for (long d = 300; d <= 3000; d += 300) {
            now[0] = paint + d;
            assertTrue("Enter at +" + d, key(p, KeyEvent.VK_ENTER, paint + d));
        }
        assertEquals(Mode.TITLE, p.mode());
        // Arrows in the burst do not move the bar either.
        now[0] = paint + 3200;
        assertTrue(key(p, KeyEvent.VK_DOWN, paint + 3200));
        assertEquals(ClassicMainMenuPanel.ITEM_NEW_WORLD, p.selectedItem());
        now[0] = paint + 3700;
        assertFalse(key(p, KeyEvent.VK_DOWN, paint + 3700));
        assertEquals(ClassicMainMenuPanel.ITEM_AMERICA, p.selectedItem());
    }

    /**
     * §5.3: every skipping input leaves the title up; a typed-ahead Escape
     * does not open the quit box, a typed-ahead Space or letter does nothing.
     */
    public void testEverySkipEndsOnTheTitle() {
        final int[] keys = { KeyEvent.VK_ENTER, KeyEvent.VK_SPACE, KeyEvent.VK_ESCAPE,
                             KeyEvent.VK_A };
        for (int skip : keys) {
            for (int next : keys) {
                now[0] += 10000;
                final long t0 = now[0];
                final ClassicMainMenuPanel p = skippedIntro(skip, t0);
                now[0] = t0 + 2500;
                assertTrue(key(p, next, t0 + 400));
                assertEquals(skip + "/" + next, Mode.TITLE, p.mode());
            }
        }
        for (int button : new int[] { MouseEvent.BUTTON1, MouseEvent.BUTTON3 }) {
            now[0] += 10000;
            final long t0 = now[0];
            final ClassicMainMenuPanel p = panel();
            p.enterIntro();
            now[0] = t0 + 2000;
            click(p, 10, 10, button, t0);
            assertEquals(Mode.TITLE, p.mode());
            now[0] = t0 + 2500;
            assertTrue(key(p, KeyEvent.VK_ESCAPE, t0 + 500));
            assertEquals(Mode.TITLE, p.mode());
        }
        // Deliberate, after the paint and the settle: Escape asks to quit.
        now[0] += 10000;
        final long t0 = now[0];
        final ClassicMainMenuPanel p = skippedIntro(KeyEvent.VK_ENTER, t0);
        p.screenPainted();
        now[0] = t0 + 3000;
        assertFalse(key(p, KeyEvent.VK_ESCAPE, t0 + 3000));
        assertEquals(Mode.QUIT, p.mode());
        // The quit box itself needs a press after its paint: "Nein" barred,
        // a queued Enter does nothing.
        assertTrue(key(p, KeyEvent.VK_ENTER, t0 + 3100));
        assertEquals(Mode.QUIT, p.mode());
        assertEquals(0, quits);
    }

    /**
     * §5.4: on each chain screen and the departure, an Enter or click made
     * before the paint (or less than REACTION_MS after) does nothing; one
     * made after advances exactly one screen; on NAME a typed-ahead
     * character is not added.
     */
    public void testEveryChainScreenWaits() throws Exception {
        if (!havePack()) {
            System.err.println(getClass().getSimpleName()
                + ": chain check skipped, no pack (ant classic-assets)");
            return;
        }
        final Throwable[] failure = new Throwable[1];
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            try {
                chainWalk();
            } catch (Throwable t) {
                failure[0] = t;
            }
        });
        if (failure[0] instanceof Error) throw (Error) failure[0];
        if (failure[0] instanceof Exception) throw (Exception) failure[0];
        assertEquals(1, started.size());
        assertEquals("model.nation.dutch", started.get(0).nationId);
    }

    private void chainWalk() {
        final ClassicMainMenuPanel p = panel();
        p.showTitle(null);
        p.screenPainted();
        long t = now[0] + 1000;
        now[0] = t;
        assertFalse(key(p, KeyEvent.VK_ENTER, t));
        assertEquals(Step.DIFFICULTY, p.chainView().step);
        // Enter queued behind the change, made before it: nothing.
        assertTrue(key(p, KeyEvent.VK_ENTER, t + 20));
        assertEquals(Step.DIFFICULTY, p.chainView().step);
        Step[] order = { Step.DIFFICULTY, Step.NATION, Step.NAME, Step.PAGE_A, Step.PAGE_B,
                         Step.AUDIENCE };
        for (int i = 0; i < order.length; i++) {
            final Step s = order[i];
            assertEquals(s, p.chainView().step);
            // Painted now; a press 100 ms after the paint is too quick.
            t += 1000;
            now[0] = t;
            p.screenPainted();
            if (s == Step.NAME) {
                final String before = p.chainView().name;
                assertTrue(key(p, KeyEvent.VK_X, t + 100));
                assertEquals("no typed-ahead letter", before, p.chainView().name);
                t += 600;
                now[0] = t;
                assertFalse(key(p, KeyEvent.VK_X, t));
                assertTrue(p.chainView().name, p.chainView().name.endsWith("x"));
                assertFalse(before.equals(p.chainView().name));
                t += 500;
            } else {
                if (i % 2 == 0) {
                    assertTrue(key(p, KeyEvent.VK_ENTER, t + 100));
                } else {
                    click(p, 160, 100, MouseEvent.BUTTON1, t + 100);
                }
                assertEquals(s, p.chainView().step);
            }
            if (s == Step.NATION) {
                // Holland, deliberately: three steps right, each after the gap.
                for (int r = 0; r < 3; r++) {
                    t += 500;
                    now[0] = t;
                    assertFalse(key(p, KeyEvent.VK_RIGHT, t));
                }
                p.screenPainted();
            }
            t += 600;
            now[0] = t;
            assertFalse(key(p, KeyEvent.VK_ENTER, t));
            if (i + 1 < order.length) {
                assertEquals(order[i + 1], p.chainView().step);
            }
        }
        // The departure (or STARTING without it).
        if (p.mode() == Mode.DEPARTURE) {
            assertTrue(key(p, KeyEvent.VK_ENTER, t + 30));
            assertEquals(Mode.DEPARTURE, p.mode());
            t += 1000;
            now[0] = t;
            p.screenPainted();
            click(p, 160, 100, MouseEvent.BUTTON1, t + 50);
            assertEquals(Mode.DEPARTURE, p.mode());
            t += 600;
            now[0] = t;
            click(p, 160, 100, MouseEvent.BUTTON1, t);
        }
        assertEquals(Mode.STARTING, p.mode());
        assertEquals(1, started.size());
    }

    /**
     * §5.5: a swallowed press's typed character, auto-repeat and release are
     * swallowed; a fresh press of the same key works afterwards; a
     * swallowed mouse press's release fires nothing.
     */
    public void testTheBookkeeping() {
        final long t0 = now[0];
        final ClassicMainMenuPanel p = panel();
        p.showTitle(null);
        now[0] = t0 + 50;
        p.screenPainted();
        // Down, swallowed (made 30 ms after the paint), held: its repeats go too.
        final KeyEvent d = new KeyEvent(p, KeyEvent.KEY_PRESSED, t0 + 80, 0,
                                        KeyEvent.VK_DOWN, KeyEvent.CHAR_UNDEFINED);
        p.processKeyEvent(d);
        assertTrue(d.isConsumed());
        for (int i = 1; i <= 5; i++) {
            final KeyEvent r = new KeyEvent(p, KeyEvent.KEY_PRESSED, t0 + 2000 + 40 * i, 0,
                                            KeyEvent.VK_DOWN, KeyEvent.CHAR_UNDEFINED);
            p.processKeyEvent(r);
            assertTrue(r.isConsumed());
        }
        final KeyEvent u = new KeyEvent(p, KeyEvent.KEY_RELEASED, t0 + 2300, 0,
                                        KeyEvent.VK_DOWN, KeyEvent.CHAR_UNDEFINED);
        p.processKeyEvent(u);
        assertTrue(u.isConsumed());
        assertEquals(ClassicMainMenuPanel.ITEM_NEW_WORLD, p.selectedItem());
        // A fresh Down: works (nothing stuck).
        now[0] = t0 + 3000;
        assertFalse(key(p, KeyEvent.VK_DOWN, t0 + 3000));
        assertEquals(ClassicMainMenuPanel.ITEM_AMERICA, p.selectedItem());
        // The quit box, deliberately; presses made before it came: their
        // releases answer nothing.
        now[0] = t0 + 3500;
        assertFalse(key(p, KeyEvent.VK_ESCAPE, t0 + 3500));
        assertEquals(Mode.QUIT, p.mode());
        for (int y = 60; y <= 140; y += 4) {
            click(p, 160, y, MouseEvent.BUTTON1, t0 + 3450);
            assertTrue(p.typedAheadSwallowedLast);
        }
        assertEquals(Mode.QUIT, p.mode());
        assertEquals(0, quits);
        // A typed-ahead Enter's typed character is swallowed with it.
        final KeyEvent e = new KeyEvent(p, KeyEvent.KEY_PRESSED, t0 + 3460, 0,
                                        KeyEvent.VK_ENTER, '\n');
        p.processKeyEvent(e);
        assertTrue(e.isConsumed());
        final KeyEvent ty = new KeyEvent(p, KeyEvent.KEY_TYPED, t0 + 3460, 0,
                                         KeyEvent.VK_UNDEFINED, '\n');
        p.processKeyEvent(ty);
        assertTrue(ty.isConsumed());
        assertEquals(Mode.QUIT, p.mode());
    }

    /** §5.6: the window binding with a stale time does not open NEUE WELT. */
    public void testTheWindowBindingPath() {
        final long t0 = now[0];
        final ClassicMainMenuPanel p = panel();
        p.showTitle(null);
        now[0] = t0 + 3000;
        binding(p, KeyEvent.VK_ENTER, t0 - 500);
        assertEquals(Mode.TITLE, p.mode());
        p.screenPainted();
        binding(p, KeyEvent.VK_ENTER, t0 + 3050);
        assertEquals(Mode.TITLE, p.mode());
        // A null event (a test or a program call) is never gated.
        p.getActionMap().get("classic_menuEscape").actionPerformed(null);
        assertEquals(Mode.QUIT, p.mode());
    }

    /** §5.7: no lock-out -- a screen never painted takes input made long after its change. */
    public void testNoLockOut() {
        final long t0 = now[0];
        final ClassicMainMenuPanel p = panel();
        p.showTitle(null);
        now[0] = t0 + ClassicMainMenuPanel.UNPAINTED_GRACE_MS - 1;
        assertTrue(key(p, KeyEvent.VK_ESCAPE, now[0]));
        now[0] = t0 + ClassicMainMenuPanel.UNPAINTED_GRACE_MS + 500;
        assertFalse(key(p, KeyEvent.VK_ESCAPE, now[0]));
        assertEquals(Mode.QUIT, p.mode());
    }

    /**
     * Single deliberate inputs and the intro ending by itself behave as
     * before: one Enter skips, the title waits; after the intro's own end a
     * press 200 ms after the paint works (no settle); held keys, chords and
     * modifiers are never gated.
     */
    public void testSingleInputsAsBefore() {
        final long t0 = now[0];
        ClassicMainMenuPanel p = skippedIntro(KeyEvent.VK_ENTER, t0);
        assertEquals(Mode.TITLE, p.mode());
        // The intro's end by itself: REACTION_MS only.
        p = panel();
        p.enterIntro();
        p.finishIntro(false);
        now[0] = t0 + 130000;
        p.screenPainted();
        assertTrue(key(p, KeyEvent.VK_DOWN, now[0] + ClassicMainMenuPanel.REACTION_MS - 1));
        now[0] += 1000;
        assertFalse(key(p, KeyEvent.VK_DOWN, now[0]));
        assertEquals(ClassicMainMenuPanel.ITEM_AMERICA, p.selectedItem());
        // A chord and a bare modifier pass even before the paint.
        p.showTitle(null);
        final KeyEvent alt = new KeyEvent(p, KeyEvent.KEY_PRESSED, now[0] - 5000,
            InputEvent.ALT_DOWN_MASK, KeyEvent.VK_ENTER, '\n');
        p.processKeyEvent(alt);
        assertFalse(p.typedAheadSwallowedLast);
        final KeyEvent shift = new KeyEvent(p, KeyEvent.KEY_PRESSED, now[0] - 5000,
            InputEvent.SHIFT_DOWN_MASK, KeyEvent.VK_SHIFT, KeyEvent.CHAR_UNDEFINED);
        p.processKeyEvent(shift);
        assertFalse(p.typedAheadSwallowedLast);
        // Off screen without the test flag: never gated (the other panel tests).
        final ClassicMainMenuPanel q = panel();
        q.gateOffScreen = false;
        q.showTitle(null);
        assertFalse(q.typedAhead(now[0] - 100000));
    }
}
