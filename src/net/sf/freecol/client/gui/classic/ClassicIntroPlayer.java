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

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.SwingUtilities;


/**
 * Drives the <b>intro</b> on its own daemon thread: samples the
 * {@link ClassicIntroTimeline} by wall clock, renders each new picture with
 * the static painters ({@link ClassicIntro#frame}) and puts it on screen.
 *
 * <p><b>Why a thread and not a Swing Timer</b> (as the departure uses):
 * the intro starts in the early window, and {@code FreeCol.startClient}
 * queues the ~3 s {@code FreeColClient} constructor on the EDT right behind
 * that window (FreeCol.java:1613-1632, ClassicStartupScreen.show).  A timer
 * would freeze the first three seconds of the show -- exactly when the
 * emblem should start turning with the first note of the music.  So the
 * pictures are drawn from this thread straight onto the panel
 * ({@code getGraphics}, the same whole-number scale and letterbox as
 * {@code paintComponent}, {@link ClassicMainMenuPanel#canvasPlacement}),
 * and also published ({@link #latest}) for the panel's own
 * {@code paintComponent}, which draws nothing else in INTRO.  Every
 * published picture is a new image, never written again, so the EDT can
 * paint it at any time without tearing.
 *
 * <p>The chart's ~250 sprites and three pictures load on a second daemon
 * thread while the emblem turns (never on the EDT, never delaying the first
 * picture).  If they are missing, or not ready when the fade starts, the
 * timeline goes from the emblem straight to the title.
 *
 * <p>At the end the thread posts {@code finishIntro(false)} to the EDT and
 * keeps the title picture on screen until the panel stops it -- the EDT may
 * still be busy, and the title is what the panel will show anyway.
 * {@link #stop} (skip, quit box, any mode change) takes the drawing lock,
 * so no intro picture can land on the screen after it.
 */
final class ClassicIntroPlayer {

    private static final Logger logger = Logger.getLogger(ClassicIntroPlayer.class.getName());

    /** Sampling period, ms (the pictures change at most ~16 times/s). */
    static final long TICK_MS = 15L;

    /** Redraw the current picture at least this often (repairs exposes). */
    static final long REFRESH_NS = 250_000_000L;

    /** Consecutive render failures after which the intro ends. */
    private static final int MAX_FAILURES = 3;

    private final ClassicMainMenuPanel panel;

    /** System.nanoTime() of the first picture. */
    private final long t0;

    private final ClassicMainMenuPanel.MenuAssets menuAssets;
    private final List<String> titleItems;

    private final ClassicIntroTimeline timeline = new ClassicIntroTimeline();

    /** Guards {@link #running} against the on-screen drawing. */
    private final Object lock = new Object();

    /** Cleared by {@link #stop}; read under {@link #lock} before drawing. */
    private volatile boolean running = true;

    /** The latest finished picture (a fresh image each time), or null. */
    private volatile BufferedImage latest = null;

    /** Whether the end was posted to the EDT. */
    private boolean donePosted = false;


    /**
     * Create a player; {@link #start} runs it.
     *
     * @param panel The panel to draw on.
     * @param t0 System.nanoTime() of the first (black) picture.
     * @param menuAssets The title's assets (for the last picture).
     * @param titleItems The title items (for the last picture).
     */
    ClassicIntroPlayer(ClassicMainMenuPanel panel, long t0,
                       ClassicMainMenuPanel.MenuAssets menuAssets,
                       List<String> titleItems) {
        this.panel = panel;
        this.t0 = t0;
        this.menuAssets = menuAssets;
        this.titleItems = titleItems;
    }

    /** Start the render thread. */
    void start() {
        final Thread t = new Thread(this::run, "Classic intro");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Stop drawing for good.  Any thread; once it returns no intro picture
     * reaches the screen any more.  The panel repaints itself afterwards.
     */
    void stop() {
        synchronized (this.lock) {
            this.running = false;
        }
    }

    /** @return Whether the player still draws. */
    boolean isRunning() {
        return this.running;
    }

    /** @return The latest picture, or null before the first one. */
    BufferedImage latest() {
        return this.latest;
    }

    /** @return Milliseconds since the first picture. */
    long elapsedMs() {
        return (System.nanoTime() - this.t0) / 1_000_000L;
    }


    // The render thread

    private void run() {
        final FutureTask<Object[]> load = new FutureTask<>(ClassicIntroPlayer::loadOpening);
        final Thread loader = new Thread(load, "Classic intro loader");
        loader.setDaemon(true);
        loader.start();
        // Rasterise the emblem's lettering and first prism picture now,
        // during the black lead-in (the screen is already black), so the
        // first turn starts on time.
        try {
            ClassicEmblem.prismFrame(0);
            ClassicEmblem.subtitleSprite();
        } catch (RuntimeException e) {
            logger.log(Level.FINE, "Emblem warm-up failed", e);
        }

        ClassicIntro.Assets assets = null;
        ClassicOpeningScript script = null;
        boolean chartDecided = false;
        int[] title = null;
        ClassicIntroTimeline.State shown = null;
        long lastBlit = 0L;
        int failures = 0;
        while (this.running) {
            try {
                final long e = elapsedMs();
                if (!chartDecided && load.isDone()) {
                    chartDecided = true;
                    final Object[] r = load.get();
                    if (r != null) {
                        assets = (ClassicIntro.Assets) r[0];
                        script = (ClassicOpeningScript) r[1];
                        this.timeline.setEndFrame(script.endFrame);
                        logger.info("Classic intro: chart assets ready after "
                            + e + " ms");
                    } else {
                        this.timeline.setHasChart(false);
                    }
                } else if (!chartDecided && e >= ClassicIntroTimeline.FADE_START_MS) {
                    chartDecided = true;
                    this.timeline.setHasChart(false);
                    logger.info("Classic intro: opening material not ready after "
                        + e + " ms; emblem then title");
                }
                final ClassicIntroTimeline.State st = this.timeline.at(e);
                if (st.done() && title == null) {
                    title = ClassicIntro.titlePixels(this.menuAssets, this.titleItems);
                }
                final boolean fresh = !st.samePicture(shown);
                if (fresh) {
                    final BufferedImage img = new BufferedImage(ClassicIntro.W,
                        ClassicIntro.H, BufferedImage.TYPE_INT_RGB);
                    ClassicIntro.frame(ClassicIntro.pixels(img), st, assets, script, title);
                    this.latest = img;
                    shown = st;
                }
                final long now = System.nanoTime();
                if (fresh || now - lastBlit > REFRESH_NS) {
                    blit(this.latest);
                    lastBlit = now;
                    // Once the EDT is free, also keep Swing's own back
                    // buffer current (it paints the same latest picture).
                    if (fresh && this.running) this.panel.repaint();
                }
                if (st.done() && !this.donePosted) {
                    this.donePosted = true;
                    SwingUtilities.invokeLater(() -> this.panel.finishIntro(false));
                }
                failures = 0;
            } catch (Exception ex) {     // RuntimeException, or a failed load
                if (++failures >= MAX_FAILURES) {
                    logger.log(Level.WARNING, "Classic intro failed; showing the title", ex);
                    this.timeline.skip();
                    if (!this.donePosted) {
                        this.donePosted = true;
                        SwingUtilities.invokeLater(() -> this.panel.finishIntro(false));
                    }
                } else {
                    logger.log(Level.FINE, "Classic intro tick failed", ex);
                }
            }
            try {
                Thread.sleep(TICK_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * Load the chart part: the assets and the script.
     *
     * @return {assets, script}, or null when the material is missing or
     *     broken (logged once).
     */
    private static Object[] loadOpening() {
        final ClassicPackFiles p = ClassicPackFiles.runtime();
        if (p == null || !p.hasOpening()) {
            logger.info("Classic intro: opening material missing (no pack, or"
                + " re-run ant classic-assets for OPENING.TXT/PATH.DAT);"
                + " emblem then title");
            return null;
        }
        try {
            final ClassicOpeningScript s = ClassicOpeningScript.load(p);
            final ClassicIntro.Assets a = ClassicIntro.Assets.fromPackFiles(p);
            if (a == null) {
                logger.info("Classic intro: opening material missing"
                    + " (unreadable pictures); emblem then title");
                return null;
            }
            return new Object[] { a, s };
        } catch (Exception e) {
            logger.log(Level.INFO, "Classic intro: opening material missing ("
                + e.getMessage() + "); emblem then title", e);
            return null;
        }
    }

    /**
     * Put a picture on screen from this thread.  Skipped while the panel is
     * not showing (Alt+Enter disposes and re-shows the frame) or once
     * stopped; the lock makes {@link #stop} final.
     */
    private void blit(BufferedImage img) {
        if (img == null) return;
        synchronized (this.lock) {
            if (!this.running || !this.panel.isShowing()) return;
            final Graphics g0 = this.panel.getGraphics();
            if (g0 == null) return;
            final Graphics2D g = (Graphics2D) g0;
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                final Rectangle r = ClassicMainMenuPanel.canvasPlacement(
                    this.panel.getWidth(), this.panel.getHeight());
                g.drawImage(img, r.x, r.y, r.width, r.height, null);
            } finally {
                g.dispose();
            }
            Toolkit.getDefaultToolkit().sync();
        }
    }
}
