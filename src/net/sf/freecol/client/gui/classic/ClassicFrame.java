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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;

import javax.swing.JFrame;
import javax.swing.WindowConstants;


/**
 * The Classic UI's main window and its full-screen / windowed state.  EDT
 * only.
 *
 * <p><b>Why a class of its own.</b>  Two parties create the main window:
 * {@link ClassicStartupScreen}, which opens it within about a second of
 * launch with the title already painted (the fast start), and
 * {@link ClassicGUI#startGUI}, which used to create it ~25 s later and
 * still does when there is no early window (no pack, {@code --no-splash}).
 * ClassicGUI then adopts the early window instead of opening a second one.
 * Both must produce exactly the same window -- borderless full screen over
 * the whole monitor, or the decorated {@code --windowsize} window -- and
 * Alt+Enter must keep working on an adopted window, which needs the same
 * remembered windowed bounds.  Keeping the frame, its mode and that memory
 * together here (moved out of ClassicGUI unchanged) makes "the same window"
 * a fact rather than two copies to keep in step.
 *
 * <p>The window listener (closeRequested) and the keys (Alt+Enter, Alt+F4)
 * are not set here: they belong to whoever owns the window at the time --
 * {@link ClassicStartupScreen} until the client exists, then
 * {@link ClassicGUI}.
 */
final class ClassicFrame {

    /**
     * The main window's title.  Unchanged on purpose: in full screen it is
     * never seen (only in the taskbar / Alt+Tab), and the live-test harness
     * finds the window by it (README "Testing live").
     */
    static final String FRAME_TITLE = "FreeCol — Classic UI (experimental)";

    /** The window. */
    final JFrame frame;

    /** The explicit {@code --windowsize}, or null when none was given. */
    private final Dimension explicitWindowSize;

    /**
     * Whether {@link #frame} is in borderless full screen; see
     * {@link #applyFrameMode}.  Sub-windows read the mode off the frame's
     * decoration instead ({@code ClassicGUI.isBorderless}).
     */
    private boolean fullScreen = false;

    /**
     * The decorated window's normal bounds when the player last left it for
     * full screen, or null before that; and whether it was maximised.
     */
    private Rectangle windowedBounds = null;
    private int windowedState = Frame.NORMAL;


    /**
     * Create the (not yet shown) main window.
     *
     * @param desiredWindowSize The {@code --windowsize}; FreeCol passes
     *     {@code Dimension(-1,-1)} (WINDOWSIZE_FALLBACK) or null when none
     *     was given, meaning full screen -- see {@link #explicitSize}.
     */
    ClassicFrame(Dimension desiredWindowSize) {
        this.explicitWindowSize = explicitSize(desiredWindowSize);
        this.frame = new JFrame(FRAME_TITLE);
        // Never exit on a bare close: Alt+F4 (FrameKeys synthesises it for
        // the borderless window), the decorated window's X and a WM_CLOSE
        // all arrive as WINDOW_CLOSING and go through the owner's listener
        // (ClassicGUI.closeRequested), which asks first.
        this.frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        // Black is the letterbox colour of every 320x200 canvas; making the
        // frame black too means a toggle or resize never flashes the
        // default grey around them.
        this.frame.setBackground(Color.BLACK);
        this.frame.getRootPane().setBackground(Color.BLACK);
    }

    /**
     * The explicit window size, if {@code d} is one.  A plain null-check
     * would treat FreeCol's {@code Dimension(-1,-1)} "no size" sentinel as a
     * real size and yield a 1x1 window, so only a size with positive
     * dimensions counts; anything else means borderless full screen.
     *
     * @param d The {@code --windowsize} value, or null.
     * @return A copy of {@code d}, or null for full screen.
     */
    static Dimension explicitSize(Dimension d) {
        return (d != null && d.width > 0 && d.height > 0) ? new Dimension(d) : null;
    }

    /** @return Whether the window is in borderless full screen. */
    boolean isFullScreen() {
        return this.fullScreen;
    }

    /**
     * Show the window for the first time: borderless full screen, or with
     * an explicit {@code --windowsize} the decorated window of that size it
     * always was.
     *
     * <p>In full screen it also asks for the foreground: Windows hides the
     * taskbar only behind the FOREGROUND window that covers its monitor.
     * Shown early by {@link ClassicStartupScreen}, this window is the first
     * this process shows after the double click, which is what lets the
     * request succeed; on the late path the black start-up screen
     * (FreeCol.createClassicSplashScreen) has kept the process in front.
     */
    void showFirst() {
        if (this.explicitWindowSize != null) {
            this.fullScreen = false;
            this.frame.setSize(this.explicitWindowSize);
            this.frame.setLocationByPlatform(true);
            this.frame.setVisible(true);
        } else {
            applyFrameMode(true);
            this.frame.toFront();
            this.frame.requestFocus();
        }
    }

    /**
     * Switch the main window between <b>borderless full screen</b>
     * ({@code full}) and a normal decorated window.  EDT only.
     *
     * <p><b>Borderless, not exclusive.</b>  Full screen here is an
     * undecorated {@code JFrame} whose bounds are the <em>whole</em> bounds of
     * the monitor it is on ({@code GraphicsConfiguration.getBounds()}, so it
     * covers the taskbar too; Windows hides the taskbar behind a focused
     * window that covers its monitor), not
     * {@code GraphicsDevice.setFullScreenWindow}.  Exclusive mode is the
     * wrong tool for this UI: the classic screens are separate top-level
     * windows (colony, Europe, reports) and modal dialogs, and on Windows an
     * exclusive full-screen window minimises or flickers out of its mode as
     * soon as another top-level window takes focus, Alt+Tab behaves badly,
     * and a modal dialog can end up hidden behind it.  A borderless window is
     * an ordinary window to the OS, so all of those keep working, and on the
     * owner's 16:10 1920x1200 monitor the 320x200 canvases still scale by a
     * whole x6 with no black bars at all (they size themselves from the
     * content pane, which now is the full monitor).  Exclusive mode would
     * not even change the resolution usefully: the canvases are drawn at an
     * integer scale already.
     *
     * <p><b>Why dispose.</b>  {@code Frame.setUndecorated} may only be called
     * while the frame is not displayable, so a switch disposes the frame
     * (destroying only the native window), flips the decoration, and shows
     * it again.  The Swing component tree survives a dispose intact -- the
     * content pane, the {@code JMenuBar}, and every component's key bindings
     * (they live in the components' {@code InputMap}s, not in the native
     * window) -- and the frame object stays the same, so every reference to
     * the frame (dialog owners, {@code setContentPane} in
     * {@code ClassicGUI.reconnectGUI}) stays valid.  Only keyboard focus is
     * lost, which {@code ClassicGUI.toggleFullScreen} restores.
     * {@code dispose} does not post {@code WINDOW_CLOSING}, so
     * {@code closeRequested} is not triggered.
     *
     * <p>The decorated window gets back the bounds (and maximised state) it
     * had when the player last left it; the first time it is the largest
     * whole multiple of 320x200 that fits the monitor's work area (x5 =
     * 1600x1000 on the owner's screen), centred, or the explicit
     * {@code --windowsize}.
     *
     * @param full True for borderless full screen, false for a window.
     */
    void applyFrameMode(boolean full) {
        final JFrame f = this.frame;
        // The monitor the frame is on now (the default screen before the
        // first show), so the switch stays on the same monitor.
        final GraphicsConfiguration gc = f.getGraphicsConfiguration();
        if (f.isDisplayable()) {
            if (!this.fullScreen) {
                // Remember the window to come back to.  While maximised,
                // getBounds() is the maximised size: keep the earlier normal
                // bounds and just remember the state.
                this.windowedState = f.getExtendedState() & Frame.MAXIMIZED_BOTH;
                if (this.windowedState == Frame.NORMAL) {
                    this.windowedBounds = f.getBounds();
                }
            }
            f.dispose();
        }
        this.fullScreen = full;
        f.setUndecorated(full);
        // A maximised frame is clipped to the work area by Windows (the
        // taskbar stays visible): full screen must start from NORMAL.
        f.setExtendedState(Frame.NORMAL);
        if (full) {
            f.setBounds(gc.getBounds());
        } else {
            // Create the native peer without showing it, so getInsets()
            // knows the title bar and border sizes before we size the frame.
            f.addNotify();
            f.setBounds((this.windowedBounds != null) ? this.windowedBounds
                : defaultWindowedBounds(gc, f.getInsets()));
        }
        f.setVisible(true);
        if (!full && this.windowedState != Frame.NORMAL) {
            f.setExtendedState(this.windowedState);
        }
    }

    /**
     * The decorated window's first bounds: the explicit {@code --windowsize}
     * if there was one, else the largest whole multiple of the 320x200
     * canvas (plus the frame's insets) that fits the monitor's work area --
     * so even windowed the canvas has no letterbox on the title screen --
     * centred in that work area.
     */
    private Rectangle defaultWindowedBounds(GraphicsConfiguration gc,
                                            Insets in) {
        final Rectangle screen = gc.getBounds();
        final Insets taskbar = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        final Rectangle work = new Rectangle(screen.x + taskbar.left,
            screen.y + taskbar.top,
            screen.width - taskbar.left - taskbar.right,
            screen.height - taskbar.top - taskbar.bottom);
        final int w, h;
        if (this.explicitWindowSize != null) {
            w = this.explicitWindowSize.width;
            h = this.explicitWindowSize.height;
        } else {
            final int availW = work.width - in.left - in.right;
            final int availH = work.height - in.top - in.bottom;
            final int s = Math.max(1, Math.min(availW / ClassicMainMenuPanel.VW,
                                               availH / ClassicMainMenuPanel.VH));
            w = ClassicMainMenuPanel.VW * s + in.left + in.right;
            h = ClassicMainMenuPanel.VH * s + in.top + in.bottom;
        }
        return new Rectangle(work.x + Math.max(0, (work.width - w) / 2),
                             work.y + Math.max(0, (work.height - h) / 2), w, h);
    }
}
