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
import java.awt.Container;
import java.awt.Dialog;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JFrame;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import net.sf.freecol.client.ClientOptions;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.option.BooleanOption;
import net.sf.freecol.common.option.IntegerOption;


/**
 * The acceptance harness in the running game: binds the
 * {@link ClassicFrameRecorder} (its state probe) and the
 * {@link ClassicScriptDriver} (its host) to the client.  Installed once by
 * {@link ClassicGUI#startGUI}; does nothing unless
 * {@value ClassicFrameRecorder#DIR_PROPERTY} or
 * {@value ClassicScriptDriver#SCRIPT_PROPERTY} is set.
 *
 * <p><b>Keys</b> are dispatched on the EDT to the component a real key
 * would reach -- the focus owner, else (window not focused, which an
 * unattended run cannot rule out) the open modal dialog's or the frame's
 * most recent focus owner, else the map -- with
 * {@code Component.dispatchEvent}, so they pass the same
 * {@code KeyboardFocusManager} path as a real press: the frame's
 * dispatchers (Alt+Enter, the menu strip) first, then the focused
 * component's key bindings, then the {@code WHEN_IN_FOCUSED_WINDOW} map
 * bindings.  (Posting to the event queue instead would retarget the event
 * to the focus owner and drop it while the window has no focus.)  A
 * press, {@link ClassicScriptDriver#KEY_HOLD_MS} held, then the release;
 * a key that types a character also sends its {@code KEY_TYPED}.
 */
final class ClassicTestHarness {

    private static final Logger logger
        = Logger.getLogger(ClassicTestHarness.class.getName());

    /** Whether {@link #install} ran. */
    private static boolean installed = false;

    /** The client. */
    private final FreeColClient fcc;

    /** The GUI. */
    private final ClassicGUI gui;


    private ClassicTestHarness(FreeColClient fcc, ClassicGUI gui) {
        this.fcc = fcc;
        this.gui = gui;
    }

    /**
     * Start the recorder and the script, if asked for.  Once.
     *
     * @param fcc The client.
     * @param gui The GUI.
     */
    static synchronized void install(FreeColClient fcc, ClassicGUI gui) {
        if (installed) return;
        installed = true;
        final ClassicTestHarness h = new ClassicTestHarness(fcc, gui);
        final ClassicFrameRecorder rec = ClassicFrameRecorder.get();
        if (rec != null) rec.setProbe(h::probe);
        final String script = System.getProperty(ClassicScriptDriver.SCRIPT_PROPERTY);
        if (script != null && !script.isBlank()) {
            ClassicScriptDriver.start(new File(script), h.new Host());
        }
    }

    /**
     * The state the recorder logs whenever it changes: turn, whose turn,
     * view mode, the active unit (id, tile, moves left in thirds), the view
     * origin, the turn flow (build spec W5: its pending pause, waiting for
     * our turn, the Spielzugende mode), whether a dialog or the first scene
     * is up and the open
     * dialog windows.  Runs on the sampler thread: plain reads only.
     *
     * @return The state line.
     */
    String probe() {
        final Game game = this.fcc.getGame();
        final StringBuilder sb = new StringBuilder(96);
        if (game == null) {
            sb.append("game=none");
        } else {
            sb.append("turn=").append((game.getTurn() == null) ? -1
                : game.getTurn().getNumber());
            final Player cur = game.getCurrentPlayer();
            final Player me = this.fcc.getMyPlayer();
            sb.append(" cur=").append((cur == null) ? "-" : shortId(cur.getNationId()));
            sb.append(" mine=").append(cur != null && cur == me);
        }
        final ClassicMapViewer mv = this.gui.currentMapViewer();
        if (mv != null) {
            sb.append(" mode=").append(mv.getViewMode());
            final Unit u = mv.getActiveUnit();
            if (u != null) {
                final Tile t = u.getTile();
                sb.append(" unit=").append(u.getId());
                if (t != null) sb.append('@').append(t.getX()).append(',').append(t.getY());
                sb.append(" moves=").append(u.getMovesLeft());
            }
            final int[] v = mv.peekViewOrigin();
            if (v != null) sb.append(" view=").append(v[0]).append(',').append(v[1]);
            if (mv.isAnimating()) sb.append(" slide");
            sb.append(" flow=").append(this.gui.turnFlowState());
        }
        sb.append(" dlg=").append(this.gui.isDialogShowing());
        for (Window w : Window.getWindows()) {
            if (w instanceof Dialog && w.isShowing()) {
                sb.append(" [").append(((Dialog)w).getTitle()).append(']');
            }
        }
        return sb.toString();
    }

    /** "model.nation.dutch" -> "dutch". */
    private static String shortId(String id) {
        if (id == null) return "-";
        final int dot = id.lastIndexOf('.');
        return (dot < 0) ? id : id.substring(dot + 1);
    }

    /**
     * Run on the EDT and wait for the answer, at most a second.
     *
     * @param c The task.
     * @param dflt The answer if the EDT does not answer in time.
     * @return The answer.
     */
    private static <T> T onEdt(Callable<T> c, T dflt) {
        if (SwingUtilities.isEventDispatchThread()) {
            try {
                return c.call();
            } catch (Exception e) {
                return dflt;
            }
        }
        final FutureTask<T> f = new FutureTask<>(c);
        SwingUtilities.invokeLater(f);
        try {
            return f.get(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return dflt;
        } catch (Exception e) {
            return dflt;
        }
    }

    /** The open modal dialog the user would be looking at, or null. */
    private static Dialog modalDialog() {
        Dialog found = null;
        for (Window w : Window.getWindows()) {
            if (w instanceof Dialog && w.isShowing() && ((Dialog)w).isModal()) {
                found = (Dialog)w;  // the most recently created one wins
            }
        }
        return found;
    }

    /** The component a real key press would reach now (EDT). */
    private Component keyTarget() {
        final Component owner = KeyboardFocusManager
            .getCurrentKeyboardFocusManager().getFocusOwner();
        if (owner != null && owner.isShowing()) return owner;
        final Dialog d = modalDialog();
        if (d != null) {
            final Component c = d.getMostRecentFocusOwner();
            return (c != null) ? c : d;
        }
        final JFrame f = this.gui.currentFrame();
        if (f != null) {
            final Component c = f.getMostRecentFocusOwner();
            if (c != null && c.isShowing()) return c;
        }
        final ClassicMapViewer mv = this.gui.currentMapViewer();
        if (mv != null && mv.isShowing()) return mv;
        return f;
    }

    /**
     * The topmost component under a pane point that takes mouse events,
     * as AWT's lightweight dispatch finds it.
     */
    private static Component mouseTarget(Container c, Point p) {
        for (Component child : c.getComponents()) {
            if (!child.isVisible() || !child.getBounds().contains(p)) continue;
            final Point q = new Point(p.x - child.getX(), p.y - child.getY());
            if (child instanceof Container) {
                final Component deeper = mouseTarget((Container)child, q);
                if (deeper != null) return deeper;
            }
            if (child.getMouseListeners().length > 0) return child;
        }
        return null;
    }

    /** The {@link ClassicScriptDriver.Host} on this client. */
    private final class Host implements ClassicScriptDriver.Host {

        @Override
        public boolean inGame() {
            return onEdt(() -> gui.currentHudPane() != null
                && fcc.getGame() != null && fcc.getMyPlayer() != null, false);
        }

        @Override
        public boolean isIdle() {
            return onEdt(() -> {
                    final ClassicMapViewer mv = gui.currentMapViewer();
                    return gui.currentHudPane() != null && mv != null
                        && !mv.isAnimating() && fcc.currentPlayerIsMyPlayer()
                        && !gui.isDialogShowing() && modalDialog() == null
                        && !gui.turnFlowBusy();
                }, false);
        }

        @Override
        public int turnNumber() {
            final Game g = fcc.getGame();
            return (g == null || g.getTurn() == null) ? -1 : g.getTurn().getNumber();
        }

        @Override
        public void key(KeyStroke key, long holdMs) throws InterruptedException {
            final int code = key.getKeyCode();
            final int mods = key.getModifiers() & (InputEvent.SHIFT_DOWN_MASK
                | InputEvent.CTRL_DOWN_MASK | InputEvent.META_DOWN_MASK
                | InputEvent.ALT_DOWN_MASK | InputEvent.ALT_GRAPH_DOWN_MASK);
            final char ch = ClassicScript.keyChar(key);
            final int loc = ClassicScript.keyLocation(key);
            final Component[] target = new Component[1];
            final long when = System.currentTimeMillis();
            ClassicFrameRecorder.event("key-post", key.toString());
            SwingUtilities.invokeLater(() -> {
                    final Component t = keyTarget();
                    target[0] = t;
                    if (t == null) {
                        ClassicFrameRecorder.event("key-press", key + " lost: no target");
                        return;
                    }
                    ClassicFrameRecorder.event("key-press", key + " -> "
                        + t.getClass().getSimpleName());
                    t.dispatchEvent(new KeyEvent(t, KeyEvent.KEY_PRESSED, when,
                        mods, code, ch, loc));
                    if (ch != KeyEvent.CHAR_UNDEFINED) {
                        t.dispatchEvent(new KeyEvent(t, KeyEvent.KEY_TYPED, when,
                            mods, KeyEvent.VK_UNDEFINED, ch,
                            KeyEvent.KEY_LOCATION_UNKNOWN));
                    }
                });
            Thread.sleep(holdMs);
            final long up = System.currentTimeMillis();
            SwingUtilities.invokeLater(() -> {
                    Component t = target[0];
                    if (t == null || !t.isShowing()) t = keyTarget();
                    if (t == null) return;
                    t.dispatchEvent(new KeyEvent(t, KeyEvent.KEY_RELEASED, up,
                        mods, code, ch, loc));
                    ClassicFrameRecorder.event("key-release", key.toString());
                });
        }

        @Override
        public void click(int x, int y) throws InterruptedException {
            final long when = System.currentTimeMillis();
            SwingUtilities.invokeLater(() -> {
                    final ClassicHudPane pane = gui.currentHudPane();
                    if (pane == null) {
                        ClassicFrameRecorder.event("click", x + "," + y + " lost: no HUD");
                        return;
                    }
                    final Rectangle cv = ClassicHudOverlay.canvas(pane.getWidth(),
                                                                  pane.getHeight());
                    final int s = ClassicHudOverlay.scale(pane.getWidth(), pane.getHeight());
                    final Point p = new Point(cv.x + x * s + s / 2, cv.y + y * s + s / 2);
                    final Component t = mouseTarget(pane, p);
                    if (t == null) {
                        ClassicFrameRecorder.event("click", x + "," + y + " lost: nothing there");
                        return;
                    }
                    final Point q = SwingUtilities.convertPoint(pane, p, t);
                    ClassicFrameRecorder.event("click", x + "," + y + " -> "
                        + t.getClass().getSimpleName());
                    t.dispatchEvent(new MouseEvent(t, MouseEvent.MOUSE_MOVED, when, 0,
                        q.x, q.y, 0, false, MouseEvent.NOBUTTON));
                    t.dispatchEvent(new MouseEvent(t, MouseEvent.MOUSE_PRESSED, when,
                        InputEvent.BUTTON1_DOWN_MASK, q.x, q.y, 1, false,
                        MouseEvent.BUTTON1));
                });
            Thread.sleep(KEY_HOLD_MS_CLICK);
            final long up = System.currentTimeMillis();
            SwingUtilities.invokeLater(() -> {
                    final ClassicHudPane pane = gui.currentHudPane();
                    if (pane == null) return;
                    final Rectangle cv = ClassicHudOverlay.canvas(pane.getWidth(),
                                                                  pane.getHeight());
                    final int s = ClassicHudOverlay.scale(pane.getWidth(), pane.getHeight());
                    final Point p = new Point(cv.x + x * s + s / 2, cv.y + y * s + s / 2);
                    final Component t = mouseTarget(pane, p);
                    if (t == null) return;
                    final Point q = SwingUtilities.convertPoint(pane, p, t);
                    t.dispatchEvent(new MouseEvent(t, MouseEvent.MOUSE_RELEASED, up, 0,
                        q.x, q.y, 1, false, MouseEvent.BUTTON1));
                    t.dispatchEvent(new MouseEvent(t, MouseEvent.MOUSE_CLICKED, up, 0,
                        q.x, q.y, 1, false, MouseEvent.BUTTON1));
                });
        }

        @Override
        public void pref(String name, boolean value) {
            if (ClassicPrefs.isKnown(name)) {
                ClassicPrefs.get().set(name, value);
                return;
            }
            final String id = ClassicPrefs.CLIENT_OPTIONS.get(name);
            if (id == null) throw new IllegalArgumentException("No pref " + name);
            final ClientOptions co = fcc.getClientOptions();
            if (ClientOptions.AUTOSAVE_PERIOD.equals(id)) {
                co.getOption(id, IntegerOption.class).setValue(value ? 1 : 0);
            } else {
                co.getOption(id, BooleanOption.class).setValue(value);
            }
            ClassicFrameRecorder.event("pref", name + "=" + value + " (" + id + ")");
        }

        @Override
        public void gotoTile(int x, int y) throws ClassicScriptDriver.ScriptException {
            // Check on the EDT, then give the order there without waiting:
            // the unit's first steps slide on the EDT (a waitIdle follows).
            final String err = onEdt(() -> {
                    final ClassicMapViewer mv = gui.currentMapViewer();
                    final Unit u = (mv == null) ? null : mv.getActiveUnit();
                    final Game g = fcc.getGame();
                    final Tile t = (g == null || g.getMap() == null) ? null
                        : g.getMap().getTile(x, y);
                    if (u == null || t == null) {
                        return "no active unit or no tile " + x + "," + y;
                    }
                    final net.sf.freecol.common.model.PathNode path
                        = u.findPath(t);
                    if (path == null) return "no path for " + u.getId() + " to " + x + "," + y;
                    ClassicFrameRecorder.event("goto", u.getId() + " -> " + x + "," + y);
                    SwingUtilities.invokeLater(() -> {
                            if (!fcc.getInGameController().goToTile(u, path)) {
                                ClassicFrameRecorder.event("goto", "refused");
                            }
                        });
                    return null;
                }, "the event thread did not answer");
            if (err != null) throw new ClassicScriptDriver.ScriptException(err);
        }

        @Override
        public void quit() {
            logger.info("Classic script: quit.");
            SwingUtilities.invokeLater(() -> {
                    ClassicFrameRecorder.shutdown();
                    try {
                        fcc.quit();
                    } catch (RuntimeException e) {
                        logger.log(Level.WARNING, "Classic script: quit failed", e);
                        System.exit(0);
                    }
                });
        }
    }

    /** How long the button is held for a click (ms). */
    private static final long KEY_HOLD_MS_CLICK = ClassicScriptDriver.KEY_HOLD_MS;
}
