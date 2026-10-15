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

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;

import net.sf.freecol.util.test.FreeColTestCase;


/**
 * Part T2: the in-game first scene (the admiral's box, {@link ClassicHudOverlay})
 * is not closed by input typed ahead (t/menu-skip.md F4: presses made while
 * the engine start blocks the EDT on the frozen departure picture; Roger's
 * recording 2026-10-14_2243 closed it 0.8 s after it opened).  A press made
 * before the scene's first paint, or less than {@code REACTION_MS} after it,
 * or less than {@code BURST_GAP_MS} after an ignored press, is ignored; a
 * deliberate press closes it as before.
 */
public class ClassicSceneTypeAheadTest extends FreeColTestCase {

    private final long[] now = { 500000L };
    private int dismissed = 0;

    private ClassicHudOverlay shown() {
        final ClassicHudOverlay o = new ClassicHudOverlay(null, null);
        o.clock = () -> now[0];
        o.setSize(320, 200);
        o.showScene(new BufferedImage(320, 200, BufferedImage.TYPE_INT_ARGB), null,
                    () -> dismissed++);
        assertTrue(o.isSceneShowing());
        return o;
    }

    private static void key(ClassicHudOverlay o, int code, long when) {
        final KeyEvent e = new KeyEvent(o, KeyEvent.KEY_PRESSED, when, 0, code,
                                        KeyEvent.CHAR_UNDEFINED);
        for (KeyListener l : o.getKeyListeners()) l.keyPressed(e);
    }

    private static void press(ClassicHudOverlay o, int button, long when) {
        final MouseEvent e = new MouseEvent(o, MouseEvent.MOUSE_PRESSED, when,
            (button == MouseEvent.BUTTON3) ? InputEvent.BUTTON3_DOWN_MASK
                                           : InputEvent.BUTTON1_DOWN_MASK,
            160, 100, 1, false, button);
        for (MouseListener l : o.getMouseListeners()) l.mousePressed(e);
    }

    /**
     * The agent's {@code p-enters20-early}: an Enter made 78 ms after the
     * show, dispatched after a 169-ms block (the paint after it), then
     * Enters every 382 ms: all ignored (the first typed ahead, the rest one
     * burst); a pause, then one Enter closes the scene.
     */
    public void testTheBurstDoesNotCloseTheScene() {
        final long show = now[0];
        final ClassicHudOverlay o = shown();
        now[0] = show + 169;
        key(o, KeyEvent.VK_ENTER, show + 78);      // dispatched before the paint
        assertEquals(0, dismissed);
        o.scenePainted();
        final long paint = now[0];
        long t = show + 78;
        for (int i = 0; i < 8; i++) {
            t += 382;
            now[0] = t;
            key(o, KeyEvent.VK_ENTER, t);
            assertEquals("Enter " + i, 0, dismissed);
        }
        assertTrue(o.isSceneShowing());
        now[0] = t + 800;
        key(o, KeyEvent.VK_ENTER, t + 800);
        assertEquals(1, dismissed);
        assertTrue(paint > show);
    }

    /** A press made after the show but before the paint (the blocked EDT) is ignored. */
    public void testBeforeThePaint() {
        final long show = now[0];
        final ClassicHudOverlay o = shown();
        now[0] = show + 1500;                      // the EDT was blocked 1.5 s
        press(o, MouseEvent.BUTTON1, show + 600);
        key(o, KeyEvent.VK_SPACE, show + 900);
        assertEquals(0, dismissed);
        o.scenePainted();
        // REACTION_MS after the paint: too quick; and within the burst gap.
        now[0] = show + 1650;
        key(o, KeyEvent.VK_ESCAPE, show + 1650);
        assertEquals(0, dismissed);
        // Deliberate: a right press, well after.
        now[0] = show + 2600;
        press(o, MouseEvent.BUTTON3, show + 2600);
        assertEquals(1, dismissed);
    }

    /** A single deliberate press after the paint closes it as before. */
    public void testADeliberatePressStillCloses() {
        final long show = now[0];
        ClassicHudOverlay o = shown();
        now[0] = show + 20;
        o.scenePainted();
        now[0] = show + 20 + ClassicMainMenuPanel.REACTION_MS;
        key(o, KeyEvent.VK_ENTER, now[0]);
        assertEquals(1, dismissed);
        // The mouse, too.
        now[0] += 5000;
        o = shown();
        now[0] += 30;
        o.scenePainted();
        now[0] += 700;
        press(o, MouseEvent.BUTTON1, now[0]);
        assertEquals(2, dismissed);
        // Chords and modifiers never close it (as before).
        now[0] += 5000;
        o = shown();
        o.scenePainted();
        now[0] += 2000;
        final KeyEvent alt = new KeyEvent(o, KeyEvent.KEY_PRESSED, now[0],
            InputEvent.ALT_DOWN_MASK, KeyEvent.VK_ENTER, '\n');
        for (KeyListener l : o.getKeyListeners()) l.keyPressed(alt);
        key(o, KeyEvent.VK_SHIFT, now[0]);
        assertEquals(2, dismissed);
    }

    /**
     * No lock-out: a scene that is never painted (a minimized window) takes
     * a press made UNPAINTED_GRACE_MS or more after the show; a strange
     * clock (more than a minute off) is not gated.
     */
    public void testNoLockOut() {
        final long show = now[0];
        final ClassicHudOverlay o = shown();
        now[0] = show + ClassicMainMenuPanel.UNPAINTED_GRACE_MS - 10;
        key(o, KeyEvent.VK_ENTER, now[0]);
        assertEquals(0, dismissed);
        now[0] = show + ClassicMainMenuPanel.UNPAINTED_GRACE_MS + 600;
        key(o, KeyEvent.VK_ENTER, now[0]);
        assertEquals(1, dismissed);
        assertFalse(o.scenePaintedYet());
        final ClassicHudOverlay q = shown();
        key(q, KeyEvent.VK_ENTER, 1000L);
        assertEquals(2, dismissed);
    }

    /** A new scene starts afresh: its paint is its own. */
    public void testEachSceneItsOwnPaint() {
        final ClassicHudOverlay o = shown();
        o.scenePainted();
        assertTrue(o.scenePaintedYet());
        o.hideScene();
        o.showScene(new BufferedImage(320, 200, BufferedImage.TYPE_INT_ARGB), null,
                    () -> dismissed++);
        assertFalse(o.scenePaintedYet());
    }
}
