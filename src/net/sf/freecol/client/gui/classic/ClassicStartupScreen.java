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

import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.SwingUtilities;

import net.sf.freecol.FreeCol;


/**
 * <b>Fast start:</b> the Classic UI's main window, opened about a second
 * after launch with the title picture and the live menu already painted,
 * long before the client exists; {@link ClassicGUI#startGUI} adopts it
 * later.  EDT only.
 *
 * <p><b>Why.</b>  Until now the first picture came ~30 s after the double
 * click (the owner perceived up to a minute on a cold start): FreeCol
 * builds its client -- resource mappings, actions, options, the GUI -- in
 * the {@code FreeColClient} constructor, and only then opened the window.
 * Two fixes made that constructor fast (the directory-listing cache in
 * FreeColDataFile, and starting the Classic UI without waiting for the
 * full resource preload, FreeColClient.startClassicGui), but it still takes
 * ~3 s and it must stay on the EDT: FreeColAction.addImageIcons posts EDT
 * tasks that dereference the GUI, which the constructor creates only near
 * its end (FreeColAction.java:258-277; moving the constructor off the EDT
 * produced 15 NullPointerExceptions in a headless harness).  So the earliest
 * possible picture has to be painted <em>before</em> the constructor runs,
 * from plain files: {@code FreeCol.createClassicSplashScreen} calls
 * {@link #show} where the black start-up screen used to be shown, and the
 * title comes from the pack files ({@link ClassicPackFiles},
 * {@link ClassicMainMenuPanel.MenuAssets#fromPackFiles}) and the menu
 * strings from {@code Messages} (loaded at FreeCol.java:323).
 *
 * <p><b>One window, no swap.</b>  The window is built by the same
 * {@link ClassicFrame} as before, so borderless full screen and the
 * decorated {@code --windowsize} window are exactly what they were, and the
 * panel is the real {@link ClassicMainMenuPanel}: ClassicGUI takes over the
 * frame, its mode and the panel ({@link #take}) instead of opening a second
 * window.  The panel is painted with {@code paintImmediately} right after
 * it is shown, so the picture is on screen while the constructor blocks the
 * EDT: Swing's back buffer then also answers the OS's expose requests
 * without the EDT.  During those ~3 s the window cannot react; the keys and
 * clicks are queued by AWT and handled once the client is attached, and the
 * Windows arrow stays visible (the panel draws its own arrow only once it
 * sees the mouse move on a responsive EDT).
 *
 * <p><b>Early choices.</b>  In practice almost no input reaches this window
 * before the constructor starts: {@link #show} runs in the very EDT task
 * that queues the constructor (FreeCol.startClient), so what the player
 * types or clicks while the title is up but the client is not is queued
 * behind the constructor and dispatched after ClassicGUI has adopted the
 * panel -- by the real actions, but still ahead of the start-up task that
 * calls {@code ClassicGUI.showMainPanel}.  That call therefore leaves a
 * start or load the player has typed ahead alone instead of resetting it
 * to the title ({@code ClassicGUI.startupTitlePending}).
 * Should input be handled here before the constructor (a slow machine,
 * a future reordering), the panel deals with it itself (the bar, the load
 * box, the whole new-game chain need no client); a choice that needs the
 * client -- starting the game at the end of the chain, loading a save, the
 * hall of fame -- goes to {@link DeferringActions}, which keeps the FIRST
 * one; the panel is then already showing its STARTING / BUSY screen and
 * ignores further input, and ClassicGUI replays it once the client is
 * ready ({@code ClassicGUI.showMainPanel}).  "Ja" in the quit box ends the
 * program at once ({@code FreeCol.quit}): there is nothing to save yet.
 *
 * <p><b>Intro and music.</b>  On a normal launch the window opens on the
 * intro (Vorspann, {@code ClassicMainMenuPanel} mode INTRO): a black first
 * picture, then the own emblem, the original's chart credits and title
 * build-up, drawn by a {@link ClassicIntroPlayer} thread because the EDT is
 * blocked by the client constructor right now; it ends on the live title.
 * The title piece ({@link ClassicEarlyMusic}) starts with that first
 * picture.  Typed-ahead input still works as described above, except that
 * its first fresh key or click is spent skipping the intro.
 *
 * <p>Without the pack (or if anything here fails) {@link #show} returns
 * false and the old start-up runs unchanged: the black screen, and the
 * window when the client is ready.
 */
public final class ClassicStartupScreen {

    private static final Logger logger = Logger.getLogger(ClassicStartupScreen.class.getName());

    /** What {@link ClassicGUI} takes over (see {@link #take}). */
    static final class Taken {

        /** The shown main window and its full-screen state. */
        final ClassicFrame frame;

        /** The title panel, the frame's content pane. */
        final ClassicMainMenuPanel panel;

        /** The temporary close listener, to be removed by the new owner. */
        final WindowListener closeListener;

        /** The queued menu choice, run against the real actions; or null. */
        final Consumer<ClassicMainMenuPanel.Actions> queued;

        Taken(ClassicFrame frame, ClassicMainMenuPanel panel,
              WindowListener closeListener,
              Consumer<ClassicMainMenuPanel.Actions> queued) {
            this.frame = frame;
            this.panel = panel;
            this.closeListener = closeListener;
            this.queued = queued;
        }
    }

    /**
     * The early menu's actions before the client exists: the first choice
     * that needs the client is kept, later ones are dropped (the panel shows
     * STARTING or BUSY by then and ignores input anyway).
     */
    static final class DeferringActions implements ClassicMainMenuPanel.Actions {

        /** The first choice, or null. */
        private Consumer<ClassicMainMenuPanel.Actions> queued = null;

        private void queue(String what,
                           Consumer<ClassicMainMenuPanel.Actions> c) {
            if (this.queued != null) {
                logger.info("Classic start-up: '" + what
                    + "' ignored, a choice is already waiting.");
                return;
            }
            logger.info("Classic start-up: '" + what
                + "' chosen before the client is ready; waiting for it.");
            this.queued = c;
        }

        @Override
        public void newWorld(final ClassicGUI.NewWorldSetup setup) {
            queue("new world", a -> a.newWorld(setup));
        }

        @Override
        public void loadGame(final File file) {
            queue("load game", a -> a.loadGame(file));
        }

        @Override
        public void hallOfFame() {
            queue("hall of fame", a -> a.hallOfFame());
        }

        @Override
        public void quit() {
            // Nothing runs yet that could be saved or stopped.
            FreeCol.quit(0);
        }

        /** @return The queued choice, or null. */
        Consumer<ClassicMainMenuPanel.Actions> queued() {
            return this.queued;
        }
    }


    /** The shown, not yet adopted window; null before and after. */
    private static ClassicFrame frame = null;
    private static ClassicMainMenuPanel panel = null;
    private static DeferringActions actions = null;
    private static WindowListener closeListener = null;


    private ClassicStartupScreen() {}   // static only

    /**
     * Milliseconds since the JVM started, for the start-up log lines the
     * live tests read.
     *
     * @return The time since launch.
     */
    static long sinceLaunchMs() {
        try {
            return System.currentTimeMillis()
                - ManagementFactory.getRuntimeMXBean().getStartTime();
        } catch (RuntimeException | Error e) {   // no management support
            return -1L;
        }
    }

    /**
     * Open the main window now, with the title.  EDT only, once, before the
     * client is constructed (FreeCol.createClassicSplashScreen).
     *
     * @param windowSize The {@code --windowsize}, or null / (-1,-1) for
     *     borderless full screen.
     * @param menu True for the live title menu; false for the passive
     *     picture ({@code --fast}, a debug start or a save argument go
     *     straight to a game and must not show a menu).
     * @return True if the window is up (the caller then shows no black
     *     start-up screen); false if there is no pack or anything failed --
     *     then nothing is shown and the old start-up runs.
     */
    public static boolean show(Dimension windowSize, boolean menu) {
        return show(windowSize, menu, false);
    }

    /**
     * Whether a normal launch shows the intro (Vorspann) before the title:
     * see {@link ClassicIntro#enabled}.
     *
     * @return False only with {@code -Dfreecol.classic.intro=false}.
     */
    public static boolean introEnabled() {
        return ClassicIntro.enabled();
    }

    /**
     * Open the main window now, with the intro or the title.  EDT only,
     * once, before the client is constructed
     * (FreeCol.createClassicSplashScreen).
     *
     * <p>With {@code intro} the first picture is black and the intro
     * thread starts right after it is on screen ({@link ClassicIntroPlayer});
     * the title items are prepared at once, because the intro ends on the
     * title.  With {@code menu}, the early title music
     * ({@link ClassicEarlyMusic#go}) starts together with that first
     * picture, intro or not -- the owner wants the music from the first
     * second, not from client attach ~4 s later.
     *
     * @param windowSize The {@code --windowsize}, or null / (-1,-1) for
     *     borderless full screen.
     * @param menu True for the live title menu; false for the passive
     *     picture.
     * @param intro True to show the intro first (only with {@code menu}).
     * @return True if the window is up; false if there is no pack or
     *     anything failed.
     */
    public static boolean show(Dimension windowSize, boolean menu, boolean intro) {
        if (!SwingUtilities.isEventDispatchThread() || frame != null) return false;
        final boolean withIntro = menu && intro;
        ClassicFrame f = null;
        try {
            final ClassicMainMenuPanel.MenuAssets a
                = ClassicMainMenuPanel.MenuAssets.fromPackFiles(
                    ClassicPackFiles.runtime());
            if (a == null) {
                logger.info("Classic start-up: no classic_original pack title;"
                    + " the window opens once the client is ready.");
                return false;
            }
            f = new ClassicFrame(windowSize);
            final DeferringActions da = new DeferringActions();
            final ClassicMainMenuPanel p = new ClassicMainMenuPanel(da);
            p.useAssets(a);
            if (withIntro) {
                p.prepareTitleItems();
                p.enterIntro();
            } else if (menu) {
                p.showTitle(null);
            } else {
                p.showPassive();
            }
            f.frame.setContentPane(p);
            final WindowListener wl = new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    // Before the client exists: the quit box if the menu
                    // is live, else just end (nothing to save yet).
                    if (!p.offerQuit()) FreeCol.quit(0);
                }
            };
            f.frame.addWindowListener(wl);
            f.showFirst();
            p.requestFocusInWindow();
            // Paint now: the client's constructor is queued right behind
            // this task and blocks the EDT for ~3 s; a normal repaint would
            // wait for it, and the window would stay black.
            f.frame.validate();
            final long t0 = System.nanoTime();
            p.paintImmediately(0, 0, p.getWidth(), p.getHeight());
            Toolkit.getDefaultToolkit().sync();
            frame = f;
            panel = p;
            actions = da;
            closeListener = wl;
            // The music and the intro start with this first picture.
            if (menu) ClassicEarlyMusic.go();
            if (withIntro) p.startIntro(t0);
            logger.info("Classic start-up: " + (withIntro ? "intro" : "title")
                + " shown after " + sinceLaunchMs()
                + " ms (" + (menu ? "live menu" : "passive picture") + ", "
                + (f.isFullScreen() ? "borderless full screen" : "windowed")
                + ", " + f.frame.getBounds() + ").");
            return true;
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Classic start-up window failed;"
                + " using the old start-up.", e);
            if (f != null) f.frame.dispose();
            frame = null;
            panel = null;
            actions = null;
            closeListener = null;
            return false;
        }
    }

    /**
     * Hand the early window over (ClassicGUI.startGUI).  EDT only; the
     * window is no longer this class's afterwards.
     *
     * @return The window, panel and queued choice, or null when no early
     *     window was shown.
     */
    static Taken take() {
        if (frame == null) return null;
        final Taken t = new Taken(frame, panel, closeListener,
                                  (actions == null) ? null : actions.queued());
        frame = null;
        panel = null;
        actions = null;
        closeListener = null;
        return t;
    }
}
