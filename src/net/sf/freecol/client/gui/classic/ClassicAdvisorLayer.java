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
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.SecondaryLoop;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.logging.Logger;

import javax.swing.JComponent;


/**
 * The layer of the in-game canvas that shows the advisor boxes
 * ({@link ClassicAdvisorBox}, build spec W7) and takes every input while one
 * is up, in place of the {@code ClassicDialog} windows.
 *
 * <p><b>Where.</b>  On the HUD pane ({@link ClassicHudPane}) above the
 * strip's dropdowns and under the mouse arrow, as large as the pane.  It
 * paints the box as a 320x200 picture on the HUD's own canvas (the same
 * scale and letterbox), so the box lies on the map and the panel as in the
 * original; everything around the box and its portrait stays the live
 * screen, which nothing repaints while a box is up (the blink is held, the
 * turn flow waits), and which is therefore exactly the picture of before the
 * box when it goes (landfall: 0 px in all 30 open intervals).  Only the
 * water keeps cycling under it, as in the original.
 *
 * <p><b>Modal like a dialog.</b>  {@link #show} returns the answer, as the
 * seams it serves must ({@code modalConfirmDialog} returns a boolean): it
 * blocks in a secondary loop of the event queue, which keeps pumping
 * events, exactly as a modal {@code JDialog} does.  A box asked for while
 * another is up waits its turn (the user answers the boxes in the order they
 * came), so the earlier one's caller resumes only after the later box is
 * answered, as with stacked dialogs.
 *
 * <p><b>Timing (V, landfall 05 section 2.4/2.5).</b>
 * <ul>
 *   <li>A box comes in one paint.  A box after another comes no earlier
 *       than {@link #CHAIN_MS} after its close: the original restores the
 *       screen for 0.13-0.27 s between chained boxes (WELCOME, PEACE,
 *       COME; LEARNSTAY, LEARNDONE), or than its own
 *       {@link ClassicAdvisorBox.Request#chainMs} (the father box's F1
 *       page, the box again after it).  A box with an
 *       {@link ClassicAdvisorBox.Request#openDelayMs} comes no earlier than
 *       that after it is asked for (the option boxes, 2-4 frames after
 *       their menu goes).</li>
 *   <li>A box whose portrait is not the last one shown loads that
 *       portrait's palette first, {@link #PALETTE_LEAD_FRAMES} frames before
 *       it appears (2-8 frames in the clip); the recorder's frames get the
 *       portrait's entries then ({@link ClassicFrameRecorder#portraitPalette}).
 *       The same portrait again loads nothing.</li>
 *   <li>A box with a {@link ClassicAdvisorBox.Request#showAtNanos} comes
 *       at that time, no earlier than it could otherwise; its palette goes
 *       in the lead before it, or what is left of the lead, at least a
 *       frame (the landing box, a set time after its key).</li>
 *   <li>The bar moves in one paint; the close restores the screen in one.</li>
 * </ul>
 *
 * <p><b>Input.</b>  While the layer is busy (a box up, or the next one
 * due) it has the focus and consumes every key and mouse event, so neither
 * the map's keys, the key map nor the strip can act behind the box; the
 * HUD's gates ask {@link #isBusy} as a backstop.  Keys: Up/Down (also the
 * keypad's 8/2) move the bar, also when held (auto-repeat); Enter takes the
 * barred row, Escape the cancel row (nothing in a box without Escape); a
 * notice goes on any key; F1 closes a list box with a help hook
 * ({@link ClassicAdvisorBox.Bar#help}).  In a
 * checkbox box (the option boxes) Enter and Space flip the barred row and a
 * row's gold letter flips that row, and the box stays.  Enter, Escape and
 * the other keys are taken only as fresh presses made while the box is on
 * screen: an auto-repeat of a key held from before, or a key pressed before
 * the box was drawn, does nothing.  The mouse:
 * {@link ClassicAdvisorBox.Bar}.  Recorder events: {@code box-palette},
 * {@code box-open}, {@code box-bar}, {@code box-toggle}, {@code box-close}.
 *
 * <p><b>Woodcuts</b> ({@link ClassicWoodcut}, master plan W9) come through
 * the same queue ({@link #showWoodcut}), so a box asked during a woodcut
 * waits for it and a woodcut asked during a box waits too, and every gate
 * of the boxes (busy, focus, the blink's hold, the turn flow) holds for
 * them.  One woodcut: the whole screen black, the frame and its ribbon
 * 86 ms later, the picture's dissolve 43 ms after that, one step per
 * original frame on the timer's deadlines; then it waits for a fresh key
 * or click (anywhere in the window, the letterbox border too) made after
 * the picture is complete (any key, also Escape: a
 * woodcut has no "Nein"); then black, the game palette back a frame
 * later, and 300 ms after the black the layer goes and the screen is
 * painted from the current state.  The next box comes no earlier than the
 * woodcut's follow-up time after that ({@link #holdUntil}).  Recorder
 * events: {@code woodcut-black}, {@code woodcut-frame},
 * {@code woodcut-done}, {@code woodcut-close}, {@code woodcut-palette},
 * {@code woodcut-end}.
 */
final class ClassicAdvisorLayer extends JComponent {

    private static final Logger logger = Logger.getLogger(ClassicAdvisorLayer.class.getName());

    /** The least time between a box's close and the next box (ms). */
    static final double CHAIN_MS = 200.0;

    /** Frames from a portrait's palette load to its box. */
    static final int PALETTE_LEAD_FRAMES = 3;

    /** {@link #PALETTE_LEAD_FRAMES} in ms (70.0863 Hz frames). */
    static final double PALETTE_LEAD_MS = PALETTE_LEAD_FRAMES * 1000.0 / 70.0863;

    /** One frame of the original (70.0863 Hz), in ms. */
    static final double FRAME_MS = 1000.0 / 70.0863;

    /** {@link #show}: the box could not be drawn (no font, too many rows). */
    static final int UNAVAILABLE = Integer.MIN_VALUE + 1;

    /** {@link #showWoodcut}: the woodcut never reached the screen. */
    static final long NOT_SHOWN = Long.MIN_VALUE;

    /** Whether a key event's time looks like wall-clock milliseconds. */
    private static final long CLOCK_SANITY_MS = 60_000L;


    /** What the layer needs from the game view. */
    interface Host {

        /** @return FONTTINY, or null (then no box can be drawn). */
        ClassicFont font();

        /** @return {@code WOODTILE.SS.000}, or null. */
        BufferedImage wood();

        /**
         * @param sprite A portrait's frame ({@code MSS0.SS.000}).
         * @return Its picture, or null.
         */
        BufferedImage portrait(String sprite);

        /**
         * @param p A portrait.
         * @return The palette entries it brings
         *     ({@link ClassicAdvisorBox#portraitPalette}), or null.
         */
        int[] portraitPalette(ClassicAdvisorBox.Portrait p);

        /** A box was asked for: hold the blink, close the menus. */
        void opened();

        /** A box has closed (another may still be due). */
        void closed();

        /** The layer has nothing up or due any more: focus back to the map. */
        void idle();

        /**
         * @param e A key press.
         * @return Whether it is an auto-repeat of a key held down before.
         */
        boolean isAutoRepeat(KeyEvent e);

        /**
         * A woodcut's palette goes in at its black (k &gt; 0: freeze the
         * water, the recorder's woodcut palette, the arrow's dim grey), or
         * the game's comes back (k = 0).  May come twice for one woodcut
         * (its close and an abort); the second does nothing.
         *
         * @param k The woodcut, or 0.
         * @param entries Its palette ({@link ClassicWoodcut#palette}), or null.
         */
        default void woodcutPalette(int k, int[] entries) {}

        /** The arrow is hidden or shown again ({@link #hidesArrow}). */
        default void arrowChanged() {}

        /**
         * A woodcut that was on the screen has ended (its map is back, or
         * the game view went): it counts as shown from now on, also while
         * its caller still waits behind a box asked meanwhile.
         *
         * @param k The woodcut.
         */
        default void woodcutEnded(int k) {}

        /**
         * @return The earliest black of a woodcut now on the layer's
         *     clock: 57 ms after the map's last final draw (a woodcut whose
         *     time came while slides still ran waits for them); 0 for no
         *     limit.
         */
        default long woodcutNotBefore() {
            return 0L;
        }
    }

    /** A woodcut's steps ({@link #showWoodcut}). */
    enum Phase { BLACK, FRAME, DISSOLVE, HELD, CLOSING }

    /** One box or woodcut asked for, until answered. */
    private static final class Pending {

        final ClassicAdvisorBox.Request request;
        final ClassicAdvisorBox.Layout layout;
        final ClassicAdvisorBox.Bar bar;

        /** The woodcut instead of a box, or null. */
        final ClassicWoodcut.Screen woodcut;

        /** A woodcut's palette, its earliest black and its follow-up (ms). */
        final int[] palette;
        final long notBefore;
        final double followMs;

        /** A woodcut's step, its black's time and its dissolve's start. */
        Phase phase = null;
        long blackAt = 0L, dissolveAt = 0L, closeAt = 0L;

        /** A press on the finished woodcut, waiting for its release. */
        boolean pressed = false;

        /** When a woodcut's map came back (or it was aborted after its black). */
        long endedAt = NOT_SHOWN;

        /** The answer, once {@link #done}. */
        int result = ClassicAdvisorBox.Bar.DISMISSED;

        boolean done = false;

        /** Whether its show is scheduled (or it is on screen). */
        boolean scheduled = false;

        /** Its secondary loop, once entered. */
        SecondaryLoop loop = null;

        Pending(ClassicAdvisorBox.Request request, ClassicAdvisorBox.Layout layout) {
            this.request = request;
            this.layout = layout;
            this.bar = new ClassicAdvisorBox.Bar(request);
            this.woodcut = null;
            this.palette = null;
            this.notBefore = 0L;
            this.followMs = 0.0;
        }

        Pending(ClassicWoodcut.Screen woodcut, int[] palette, long notBefore,
                double followMs) {
            this.request = null;
            this.layout = null;
            this.bar = null;
            this.woodcut = woodcut;
            this.palette = palette;
            this.notBefore = notBefore;
            this.followMs = followMs;
        }

        /** @return What it is, for the recorder. */
        String id() {
            return (this.woodcut != null) ? "woodcut " + this.woodcut.k : this.request.id;
        }
    }


    private final Host host;

    /** The clock of the deadlines ({@link ClassicSlide.Clock}). */
    private final ClassicSlide.Clock clock;

    /** Shows the next box at its deadline. */
    private final ClassicOneShot timer;

    /** The boxes asked for, the one on screen (if any) first. */
    private final List<Pending> queue = new ArrayList<>();

    /** The box on screen, or null. */
    private Pending current = null;

    /** Its picture (320x200 ARGB), or null. */
    private BufferedImage picture = null;

    /** When the last box closed (clock ns), or {@code Long.MIN_VALUE}. */
    private long lastClose = Long.MIN_VALUE;

    /** The portrait whose palette was loaded last, or null. */
    private String lastPortrait = null;

    /** When the box on screen appeared (wall clock ms, for key times). */
    private long shownAt = 0L;

    /**
     * The next box or woodcut comes no earlier (clock ns): a woodcut's
     * follow-up after its map came back; {@code Long.MIN_VALUE} for none.
     */
    private long holdUntil = Long.MIN_VALUE;


    /**
     * @param host The game view.
     * @param clock The clock ({@code ClassicGUI.waitClock}).
     * @param poster Runs a due show on the event thread
     *     ({@code SwingUtilities::invokeLater}).
     * @param threaded Whether the timer waits on a thread of its own (false
     *     in tests: {@link #runDue}).
     */
    ClassicAdvisorLayer(Host host, ClassicSlide.Clock clock,
                        Consumer<Runnable> poster, boolean threaded) {
        this.host = host;
        this.clock = clock;
        this.timer = new ClassicOneShot(clock, poster, threaded, "classic-box");
        setOpaque(false);
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        setVisible(false);
        addKeyListener(new KeyListener() {
                @Override
                public void keyPressed(KeyEvent e) {
                    onKey(e);
                }

                @Override
                public void keyReleased(KeyEvent e) {
                    e.consume();
                }

                @Override
                public void keyTyped(KeyEvent e) {
                    onTyped(e);
                }
            });
        final MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    onPress(e);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    onRelease(e);
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    e.consume();
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    e.consume();
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    e.consume();
                }

                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    e.consume();
                }
            };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }


    // State

    /** @return Whether a box is up or due. */
    boolean isBusy() {
        return !this.queue.isEmpty();
    }

    /** @return Whether a box or a woodcut is on screen. */
    boolean isShowingBox() {
        return this.current != null;
    }

    /**
     * @return The id of the box on screen and its bar, or of the woodcut
     *     and its step ({@code woodcut_3:held}), for the probe; or null.
     */
    String probe() {
        final Pending p = this.current;
        if (p == null) return isBusy() ? "due" : null;
        if (p.woodcut != null) {
            return "woodcut_" + p.woodcut.k + ":"
                + p.phase.toString().toLowerCase(Locale.ROOT);
        }
        return p.request.id.replace(' ', '_') + ":" + p.bar.row();
    }

    /** @return The layout of the box on screen, or null (tests, a woodcut). */
    ClassicAdvisorBox.Layout currentLayout() {
        final Pending p = this.current;
        return (p == null) ? null : p.layout;
    }

    /** @return The woodcut on screen, or null. */
    ClassicWoodcut.Screen currentWoodcut() {
        final Pending p = this.current;
        return (p == null) ? null : p.woodcut;
    }

    /** @return The step of the woodcut on screen, or null. */
    Phase woodcutPhase() {
        final Pending p = this.current;
        return (p == null || p.woodcut == null) ? null : p.phase;
    }

    /**
     * @return Whether a woodcut covers the screen (from its black to its
     *     map's return): the map's index hint means nothing then.
     */
    boolean coversScreen() {
        final Pending p = this.current;
        return p != null && p.woodcut != null;
    }

    /**
     * @return Whether the mouse arrow is hidden: during a woodcut's
     *     dissolve (V: landfall #2121-#2175, two or three frames after the
     *     frame's paint until the last dissolve frame).
     */
    boolean hidesArrow() {
        final Pending p = this.current;
        return p != null && p.woodcut != null && p.phase == Phase.DISSOLVE;
    }

    /**
     * Whether a box with this portrait would load the portrait's palette
     * before it ({@link #PALETTE_LEAD_FRAMES}): another portrait was the
     * last one shown.
     *
     * @param p The portrait.
     * @return True if its palette goes in first.
     */
    boolean loadsPalette(ClassicAdvisorBox.Portrait p) {
        return p != null && p.sprite != null && !p.sprite.equals(this.lastPortrait);
    }

    /**
     * @return When the last box on screen closed, on the layer's clock, or
     *     {@code Long.MIN_VALUE} before the first (the landing's slide is
     *     timed from it, build spec W8b).
     */
    long lastCloseNanos() {
        return this.lastClose;
    }

    /** @return The bar's row of the box on screen, or -1 (tests). */
    int currentBar() {
        final Pending p = this.current;
        return (p == null || p.bar == null) ? -1 : p.bar.row();
    }

    /**
     * @return The earliest time of the next box or woodcut after a
     *     woodcut's map came back, or {@code Long.MIN_VALUE}.
     */
    long holdUntilNanos() {
        return this.holdUntil;
    }


    // Showing

    /**
     * Show a box and wait for its answer (class comment).  EDT only.
     *
     * @param r The request.
     * @return The row taken, Escape's answer
     *     ({@link ClassicAdvisorBox.Request#escapeAnswer}), 0 for a notice,
     *     {@link ClassicAdvisorBox.Bar#DISMISSED} when the game view went
     *     ({@link #abort}), or {@link #UNAVAILABLE} when the box cannot be
     *     drawn (the caller falls back to the stopgap).
     */
    int show(ClassicAdvisorBox.Request r) {
        final ClassicFont tiny = this.host.font();
        final ClassicAdvisorBox.Portrait who = r.portrait;
        final BufferedImage pic = (who.sprite == null) ? null
            : this.host.portrait(who.sprite);
        final ClassicAdvisorBox.Layout l = ClassicAdvisorBox.layout(r, tiny, pic);
        if (l == null) return UNAVAILABLE;
        final Pending p = new Pending(r, l);
        enter(p);
        return p.result;
    }

    /**
     * Show a woodcut and wait until it is gone (class comment).  EDT only.
     *
     * @param w The woodcut ({@link ClassicWoodcut.Screen#of}).
     * @param palette Its palette ({@link ClassicWoodcut#palette}), or null.
     * @param notBefore Its black comes no earlier (clock ns; 0: at once).
     * @param followMs The next box or woodcut comes no earlier than this
     *     after the map's return.
     * @return When the map came back on the clock, or when the game view
     *     went if that was after its black; {@link #NOT_SHOWN} if it never
     *     reached the screen.
     */
    long showWoodcut(ClassicWoodcut.Screen w, int[] palette, long notBefore,
                     double followMs) {
        if (w == null) return NOT_SHOWN;
        final Pending p = new Pending(w, palette, notBefore, followMs);
        enter(p);
        return p.endedAt;
    }

    /** Queue a box or woodcut and wait in a secondary loop until it is done. */
    private void enter(Pending p) {
        this.queue.add(p);
        setVisible(true);
        requestFocusInWindow();
        this.host.opened();
        kick();
        // A secondary loop also returns when one it is nested in exits
        // (JDK 21: about a second after the box below it was answered),
        // so a box asked meanwhile would answer "dismissed" while it is
        // still up: wait on until this one is really done.
        while (!p.done) {
            p.loop = Toolkit.getDefaultToolkit().getSystemEventQueue()
                .createSecondaryLoop();
            if (!p.loop.enter()) {
                logger.warning("Classic box: the event loop did not wait for "
                    + p.id());
                break;
            }
        }
    }

    /**
     * Close every box and woodcut, up or due, as dismissed: the game view
     * goes.  EDT only.
     */
    void abort() {
        this.timer.cancel();
        // None of them comes on screen meanwhile (a woodcut drawn counts
        // as shown).
        this.aborting = true;
        try {
            for (Pending p : new ArrayList<>(this.queue)) {
                finish(p, ClassicAdvisorBox.Bar.DISMISSED);
            }
        } finally {
            this.aborting = false;
        }
    }

    /** True while {@link #abort} closes everything. */
    private boolean aborting = false;

    /** End the timer's thread for good (the game view went). */
    void dispose() {
        abort();
        this.timer.close();
    }

    /**
     * The timer's job without its thread (tests): run the show if it is due
     * on the clock.
     *
     * @return True if one was posted.
     */
    boolean runDue() {
        return this.timer.runIfDue();
    }

    /** @return {@code ms} in nanoseconds, rounded. */
    private static long nanos(double ms) {
        return Math.round(ms * 1e6);
    }

    /** Schedule the first box of the queue if none is up or scheduled. */
    private void kick() {
        if (this.current != null || this.queue.isEmpty() || this.aborting) return;
        final Pending p = this.queue.get(0);
        if (p.scheduled) return;
        p.scheduled = true;
        final long now = this.clock.now();
        if (p.woodcut != null) {
            // A woodcut: at its time, after the box before it as a box
            // would come, and after the woodcut before it.
            long due = (p.notBefore == 0L) ? now : Math.max(now, p.notBefore);
            if (this.lastClose != Long.MIN_VALUE) {
                due = Math.max(due, this.lastClose + nanos(CHAIN_MS));
            }
            if (this.holdUntil != Long.MIN_VALUE) due = Math.max(due, this.holdUntil);
            if (due <= now) {
                display(p);
            } else {
                this.timer.schedule(due, () -> display(p));
            }
            return;
        }
        long due = now + nanos(p.request.openDelayMs);
        if (this.lastClose != Long.MIN_VALUE) {
            final double chain = (p.request.chainMs >= 0.0) ? p.request.chainMs
                : CHAIN_MS;
            due = Math.max(due, this.lastClose + nanos(chain));
        }
        final String sprite = (p.layout.portrait == null) ? null
            : p.request.portrait.sprite;
        final boolean load = sprite != null && !sprite.equals(this.lastPortrait);
        long at = due + ((load) ? nanos(PALETTE_LEAD_MS) : 0L);
        long showAt = p.request.showAtNanos;
        if (this.holdUntil != Long.MIN_VALUE && this.holdUntil > due) {
            // After a woodcut: the box at its follow-up time, its palette
            // in the lead before it (landfall #12162 -> #12166).
            showAt = (showAt == 0L) ? this.holdUntil : Math.max(showAt, this.holdUntil);
        }
        if (showAt != 0L) {
            // A box due at a given time: the palette goes in the lead
            // before it, or as much of the lead as is left, at least a
            // frame (the landing box after its key, build spec W8b).
            at = Math.max(showAt, due + ((load) ? nanos(FRAME_MS) : 0L));
            if (load) due = Math.max(due, at - nanos(PALETTE_LEAD_MS));
            else due = at;
        }
        if (load) {
            final long show = at;
            this.timer.schedule(due, () -> {
                    loadPalette(p);
                    if (!p.done) this.timer.schedule(show, () -> display(p));
                });
        } else if (due <= now) {
            display(p);
        } else {
            this.timer.schedule(due, () -> display(p));
        }
    }

    /** The portrait's palette goes in, ahead of its box. */
    private void loadPalette(Pending p) {
        if (p.done) return;
        final ClassicAdvisorBox.Portrait who = p.request.portrait;
        this.lastPortrait = who.sprite;
        final int[] entries = this.host.portraitPalette(who);
        ClassicFrameRecorder.portraitPalette(who.sprite, entries);
        ClassicFrameRecorder.event("box-palette", who.sprite + " for " + p.request.id);
    }

    /** Put a box on screen, or a woodcut's black. */
    private void display(Pending p) {
        if (p.done || this.current != null) return;
        if (p.woodcut != null) {
            // Never sooner than 57 ms after the map's last final draw: a
            // woodcut whose time came during slides (the natives' moves
            // the server sent after a first contact) follows the last one
            // as it follows its trigger's paint.
            final long nb = this.host.woodcutNotBefore();
            if (nb != 0L && nb > this.clock.now()) {
                this.timer.schedule(nb, () -> display(p));
                return;
            }
            this.current = p;
            woodcutBlack(p);
            return;
        }
        this.current = p;
        this.shownAt = System.currentTimeMillis();
        render();
        paintNow(scaled(p.layout.bounds()));
        requestFocusInWindow();
        if (ClassicFrameRecorder.on()) {
            final Rectangle b = p.layout.box;
            final String text = p.request.plainText();
            ClassicFrameRecorder.event("box-open", p.request.id + " box="
                + b.x + "," + b.y + "," + b.width + "," + b.height
                + " portrait=" + p.request.portrait
                + ((p.layout.portraitAt == null) ? ""
                    : "@" + p.layout.portraitAt.x + "," + p.layout.portraitAt.y)
                + " rows=" + p.request.rows.size() + " bar=" + p.bar.row()
                + (p.request.isCheckbox()
                    ? " checks=" + ClassicAdvisorBox.checkString(p.bar.checks()) : "")
                + (p.request.hasField() ? " field=" + p.bar.fieldText() : "")
                + " text=" + text.substring(0, Math.min(80, text.length())));
        }
    }

    /** Close a box with an answer, or end a woodcut, and let the next one come. */
    private void finish(Pending p, int result) {
        if (p.done) return;
        p.done = true;
        p.result = result;
        this.queue.remove(p);
        final boolean wasUp = this.current == p;
        final Rectangle dirty = (p.woodcut != null) ? whole()
            : scaled(p.layout.bounds());
        if (wasUp) {
            this.current = null;
            this.picture = null;
            if (p.woodcut == null) {
                this.lastClose = this.clock.now();
            } else {
                // Shown once it was drawn, also if the game view goes.
                if (p.endedAt == NOT_SHOWN) p.endedAt = this.clock.now();
                this.host.woodcutPalette(0, null);
                this.host.arrowChanged();
                this.host.woodcutEnded(p.woodcut.k);
            }
        }
        if (this.queue.isEmpty()) {
            this.timer.cancel();
            setVisible(false);
        }
        // The screen under the box comes back in the same paint; after a
        // woodcut all of it, drawn from the current state.
        if (wasUp) paintNow(dirty);
        if (p.woodcut != null) {
            ClassicFrameRecorder.event("woodcut-end", p.woodcut.k
                + ((wasUp) ? " map back" : " never shown")
                + ((result == ClassicAdvisorBox.Bar.DISMISSED) ? " dismissed" : ""));
        } else {
            ClassicFrameRecorder.event("box-close", p.request.id + " chosen=" + result
                + (wasUp ? "" : " (never shown)"));
        }
        this.host.closed();
        if (this.queue.isEmpty()) this.host.idle();
        if (p.loop != null) p.loop.exit();
        kick();
    }


    // Woodcuts

    /** The whole layer, in layer coordinates. */
    private Rectangle whole() {
        return new Rectangle(0, 0, getWidth(), getHeight());
    }

    /**
     * A woodcut's first paint: the whole screen black, under the woodcut's
     * palette (the water frozen); its frame {@link
     * ClassicWoodcut#FRAME_AFTER_BLACK_MS} later.
     */
    private void woodcutBlack(Pending p) {
        p.phase = Phase.BLACK;
        p.blackAt = this.clock.now();
        // The original loads the woodcut's palette over the portrait's:
        // the next portrait loads its palette again (landfall #12162).
        this.lastPortrait = null;
        p.woodcut.black();
        this.picture = p.woodcut.image;
        paintNow(whole());
        // The palette after the black is painted: a frame sampled between
        // the two would show the map under the woodcut's colours (live
        // run fs3: 53,567 px recorded as misses in one frame).
        this.host.woodcutPalette(p.woodcut.k, p.palette);
        requestFocusInWindow();
        ClassicFrameRecorder.event("woodcut-black", p.woodcut.k + " pixels="
            + p.woodcut.changed());
        this.timer.schedule(p.blackAt + nanos(ClassicWoodcut.FRAME_AFTER_BLACK_MS),
                            () -> woodcutFrame(p));
    }

    /** The frame, the ribbon, the title and the fill, in one paint. */
    private void woodcutFrame(Pending p) {
        if (p.done || this.current != p) return;
        p.phase = Phase.FRAME;
        p.woodcut.frame();
        paintNow(whole());
        ClassicFrameRecorder.event("woodcut-frame", String.valueOf(p.woodcut.k));
        p.dissolveAt = p.blackAt + nanos(ClassicWoodcut.FRAME_AFTER_BLACK_MS
            + ClassicWoodcut.DISSOLVE_AFTER_FRAME_MS);
        this.timer.schedule(p.dissolveAt, () -> woodcutTick(p));
    }

    /**
     * One frame of the dissolve, on the original's frame grid from its
     * start; a late tick catches up.  The arrow is hidden from the first to
     * the last; then the woodcut waits for a key.
     */
    private void woodcutTick(Pending p) {
        if (p.done || this.current != p) return;
        final ClassicWoodcut.Screen w = p.woodcut;
        if (p.phase != Phase.DISSOLVE) {
            p.phase = Phase.DISSOLVE;
            this.host.arrowChanged();
        }
        final double since = (this.clock.now() - p.dissolveAt) / 1e6;
        if (w.dissolveTo(ClassicWoodcut.revealed(w.changed(), since))) {
            paintNow(scaled(ClassicWoodcut.PICTURE));
        }
        if (w.complete()) {
            p.phase = Phase.HELD;
            this.shownAt = System.currentTimeMillis();
            this.host.arrowChanged();
            ClassicFrameRecorder.event("woodcut-done", w.k + " pixels=" + w.changed());
            return;
        }
        final double step = ClassicWoodcut.DISSOLVE_MS / (ClassicWoodcut.DISSOLVE_FRAMES - 1);
        final long next = (long) Math.floor(Math.max(0.0, since) / step) + 1L;
        this.timer.schedule(p.dissolveAt + nanos(next * step), () -> woodcutTick(p));
    }

    /**
     * The key: black in one paint, the game's palette back a frame later,
     * the map {@link ClassicWoodcut#MAP_BACK_MS} after the black.
     */
    private void woodcutClose(Pending p) {
        p.phase = Phase.CLOSING;
        p.pressed = false;
        p.closeAt = this.clock.now();
        p.woodcut.black();
        paintNow(whole());
        ClassicFrameRecorder.event("woodcut-close", String.valueOf(p.woodcut.k));
        this.timer.schedule(p.closeAt + nanos(ClassicWoodcut.FRAME_MS), () -> {
                if (p.done || this.current != p) return;
                this.host.woodcutPalette(0, null);
                ClassicFrameRecorder.event("woodcut-palette", p.woodcut.k + " back");
                this.timer.schedule(p.closeAt + nanos(ClassicWoodcut.MAP_BACK_MS), () -> {
                        if (p.done || this.current != p) return;
                        p.endedAt = this.clock.now();
                        this.holdUntil = p.endedAt + nanos(p.followMs);
                        finish(p, 0);
                    });
            });
    }


    // Input

    /** Whether an input event was made before the box was on screen. */
    private boolean predates(long when) {
        final long now = System.currentTimeMillis();
        if (Math.abs(now - when) > CLOCK_SANITY_MS) return false;
        return when < this.shownAt;
    }

    /**
     * A key press while busy.  Package-private for the tests.
     *
     * @param e The event.
     */
    void onKey(KeyEvent e) {
        e.consume();
        final Pending p = this.current;
        if (p == null || e.getID() != KeyEvent.KEY_PRESSED) return;
        final int code = e.getKeyCode();
        if (isModifier(code) || e.isAltDown() || e.isControlDown()
            || e.isMetaDown() || e.isAltGraphDown()) return;
        final boolean repeat = this.host.isAutoRepeat(e);
        if (p.woodcut != null) {
            // Any fresh key once the picture is complete (I: the keys are
            // never recorded), also Escape; not one held from before.
            if (p.phase == Phase.HELD && !predates(e.getWhen()) && !repeat) {
                woodcutClose(p);
            }
            return;
        }
        int answer;
        switch (code) {
        case KeyEvent.VK_UP: case KeyEvent.VK_KP_UP: case KeyEvent.VK_NUMPAD8:
            if (predates(e.getWhen()) || (repeat && p.request.isNotice())) return;
            answer = p.bar.up();
            break;
        case KeyEvent.VK_DOWN: case KeyEvent.VK_KP_DOWN: case KeyEvent.VK_NUMPAD2:
            if (predates(e.getWhen()) || (repeat && p.request.isNotice())) return;
            answer = p.bar.down();
            break;
        case KeyEvent.VK_ENTER:
            if (predates(e.getWhen()) || repeat) return;
            answer = p.bar.enter();
            break;
        case KeyEvent.VK_ESCAPE:
            if (predates(e.getWhen()) || repeat) return;
            answer = p.bar.escape();
            break;
        case KeyEvent.VK_SPACE:
            if (predates(e.getWhen()) || repeat) return;
            answer = p.bar.space();
            break;
        case KeyEvent.VK_F1:
            if (predates(e.getWhen()) || repeat) return;
            answer = p.bar.help();
            break;
        case KeyEvent.VK_BACK_SPACE:
            if (predates(e.getWhen()) || !p.request.hasField()) return;
            if (p.bar.backspace()) fieldChanged(p);
            return;
        default:
            if (predates(e.getWhen()) || repeat) return;
            answer = (code >= KeyEvent.VK_A && code <= KeyEvent.VK_Z)
                ? p.bar.letter((char) code) : p.bar.otherKey();
            break;
        }
        settle(p, answer);
    }

    /**
     * A typed character while busy: it goes into the name field of the box
     * on screen ({@link ClassicAdvisorBox.Bar#type}); any other box ignores
     * it (its key press answered already).  A held key types again, as
     * any text field does (I).  Package-private for the tests.
     *
     * @param e The event.
     */
    void onTyped(KeyEvent e) {
        e.consume();
        final Pending p = this.current;
        if (p == null || p.woodcut != null || !p.request.hasField()
            || e.getID() != KeyEvent.KEY_TYPED) return;
        if (e.isControlDown() || e.isMetaDown()
            || (e.isAltDown() && !e.isAltGraphDown())) return;
        final char c = e.getKeyChar();
        if (c == KeyEvent.CHAR_UNDEFINED || c < 32 || c == 127
            || predates(e.getWhen())) return;
        if (p.bar.type(c, this.host.font())) fieldChanged(p);
    }

    /** The name field's text changed: redraw the box in one paint (I). */
    private void fieldChanged(Pending p) {
        if (p != this.current) return;
        render();
        paintNow(scaled(p.layout.box));
        ClassicFrameRecorder.event("box-field", p.request.id + " text="
            + p.bar.fieldText());
    }

    /** @return The name field's text of the box on screen, or null (tests). */
    String currentFieldText() {
        final Pending p = this.current;
        return (p == null || p.bar == null) ? null : p.bar.fieldText();
    }

    /** @return Whether a key code is a bare modifier or a lock. */
    private static boolean isModifier(int code) {
        return code == KeyEvent.VK_SHIFT || code == KeyEvent.VK_CONTROL
            || code == KeyEvent.VK_ALT || code == KeyEvent.VK_META
            || code == KeyEvent.VK_ALT_GRAPH || code == KeyEvent.VK_WINDOWS
            || code == KeyEvent.VK_CONTEXT_MENU || code == KeyEvent.VK_CAPS_LOCK
            || code == KeyEvent.VK_NUM_LOCK || code == KeyEvent.VK_SCROLL_LOCK
            || code == KeyEvent.VK_UNDEFINED;
    }

    /**
     * A mouse press while busy.  Package-private for the tests.
     *
     * @param e The event.
     */
    void onPress(MouseEvent e) {
        e.consume();
        requestFocusInWindow();
        final Pending p = this.current;
        if (p == null || predates(e.getWhen())) return;
        final Point v = virtual(e.getPoint());
        if (p.woodcut != null) {
            // A press and its release anywhere in the window count as a
            // key, the letterbox border too (Roger: any click closes it;
            // I-prep cycle.md 3C).
            p.pressed = p.phase == Phase.HELD;
            return;
        }
        final int row = (v == null) ? -1 : p.layout.rowAt(v.x, v.y);
        final boolean in = v != null && p.layout.inBox(v.x, v.y);
        final int before = p.bar.row();
        p.bar.press(row, in);
        if (p.bar.row() != before) barMoved(p);
    }

    /**
     * A mouse release while busy.  Package-private for the tests.
     *
     * @param e The event.
     */
    void onRelease(MouseEvent e) {
        e.consume();
        final Pending p = this.current;
        if (p == null) return;
        final Point v = virtual(e.getPoint());
        if (p.woodcut != null) {
            if (p.pressed && p.phase == Phase.HELD) woodcutClose(p);
            return;
        }
        final int row = (v == null) ? -1 : p.layout.rowAt(v.x, v.y);
        final boolean in = v != null && p.layout.inBox(v.x, v.y);
        settle(p, p.bar.release(row, in));
    }

    /**
     * After a key or a release: repaint a moved bar or a flipped checkbox,
     * or close with the answer.
     */
    private void settle(Pending p, int answer) {
        if (answer != ClassicAdvisorBox.Bar.OPEN) {
            finish(p, answer);
            return;
        }
        final int flipped = p.bar.takeToggled();
        if (flipped >= 0) {
            toggled(p, flipped);
            return;
        }
        barMoved(p);
    }

    /**
     * A checkbox row flipped (its option already applied by the bar): redraw
     * the box in one paint, where only the bullet's 3x3 inside (and a moved
     * bar) changes (landing-slow #1784 -> #1785: 9 px).
     */
    private void toggled(Pending p, int row) {
        if (p != this.current) return;
        render();
        paintNow(scaled(p.layout.box));
        ClassicFrameRecorder.event("box-toggle", p.request.id + " row=" + row
            + " on=" + p.bar.checked(row) + " bar=" + p.bar.row()
            + " checks=" + ClassicAdvisorBox.checkString(p.bar.checks()));
    }

    /** The bar may have moved: redraw the box in one paint. */
    private void barMoved(Pending p) {
        if (p != this.current) return;
        render();
        paintNow(scaled(p.layout.box));
        ClassicFrameRecorder.event("box-bar", p.request.id + " row=" + p.bar.row());
    }

    /**
     * Paint a part of the screen now, as the slide and the blink do: a box
     * comes, moves its bar and goes in one paint (landfall 05 section
     * 2.5), also in a minimized window, whose {@code repaint()}s Swing may
     * hold back (E1).  Hidden, the layer has the pane under it paint.
     *
     * @param r The part, in layer (= pane) coordinates.
     */
    private void paintNow(Rectangle r) {
        if (isVisible()) {
            paintImmediately(r);
        } else if (getParent() instanceof JComponent) {
            ((JComponent) getParent()).paintImmediately(r);
        }
    }

    /** The 320x200 point under a layer point, or null in the letterbox. */
    private Point virtual(Point p) {
        return ClassicPointer.virtual(getWidth(), getHeight(), p);
    }


    // Painting

    /** Draw the box on screen into its picture. */
    private void render() {
        final Pending p = this.current;
        this.picture = (p == null) ? null
            : (p.woodcut != null) ? p.woodcut.image
            : ClassicAdvisorBox.render(p.layout, p.bar, this.host.wood(),
                                       this.host.font());
    }

    /** A 320x200 rectangle in layer coordinates (one pixel of slack). */
    private Rectangle scaled(Rectangle r) {
        final Rectangle c = ClassicHudOverlay.canvas(getWidth(), getHeight());
        final int s = ClassicHudOverlay.scale(getWidth(), getHeight());
        return new Rectangle(c.x + r.x * s - 1, c.y + r.y * s - 1,
                             r.width * s + 2, r.height * s + 2);
    }

    @Override
    protected void paintComponent(Graphics g) {
        final BufferedImage img = this.picture;
        if (img == null) return;
        final Rectangle c = ClassicHudOverlay.canvas(getWidth(), getHeight());
        final Graphics2D gg = (Graphics2D) g.create();
        try {
            gg.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            gg.drawImage(img, c.x, c.y, c.width, c.height, null);
        } finally {
            gg.dispose();
        }
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "ClassicAdvisorLayer[queue=%d up=%s]",
                             this.queue.size(), probe());
    }
}
