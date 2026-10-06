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
 *       COME; LEARNSTAY, LEARNDONE).</li>
 *   <li>A box whose portrait is not the last one shown loads that
 *       portrait's palette first, {@link #PALETTE_LEAD_FRAMES} frames before
 *       it appears (2-8 frames in the clip); the recorder's frames get the
 *       portrait's entries then ({@link ClassicFrameRecorder#portraitPalette}).
 *       The same portrait again loads nothing.</li>
 *   <li>The bar moves in one paint; the close restores the screen in one.</li>
 * </ul>
 *
 * <p><b>Input.</b>  While the layer is busy (a box up, or the next one
 * due) it has the focus and consumes every key and mouse event, so neither
 * the map's keys, the key map nor the strip can act behind the box; the
 * HUD's gates ask {@link #isBusy} as a backstop.  Keys: Up/Down (also the
 * keypad's 8/2) move the bar, also when held (auto-repeat); Enter takes the
 * barred row, Escape the cancel row; a notice goes on any key.  Enter,
 * Escape and the other keys are taken only as fresh presses made while the
 * box is on screen: an auto-repeat of a key held from before, or a key
 * pressed before the box was drawn, does nothing.  The mouse:
 * {@link ClassicAdvisorBox.Bar}.  Recorder events: {@code box-palette},
 * {@code box-open}, {@code box-bar}, {@code box-close}.
 */
final class ClassicAdvisorLayer extends JComponent {

    private static final Logger logger = Logger.getLogger(ClassicAdvisorLayer.class.getName());

    /** The least time between a box's close and the next box (ms). */
    static final double CHAIN_MS = 200.0;

    /** Frames from a portrait's palette load to its box. */
    static final int PALETTE_LEAD_FRAMES = 3;

    /** {@link #PALETTE_LEAD_FRAMES} in ms (70.0863 Hz frames). */
    static final double PALETTE_LEAD_MS = PALETTE_LEAD_FRAMES * 1000.0 / 70.0863;

    /** {@link #show}: the box could not be drawn (no font, too many rows). */
    static final int UNAVAILABLE = Integer.MIN_VALUE + 1;

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
    }

    /** One box asked for, until answered. */
    private static final class Pending {

        final ClassicAdvisorBox.Request request;
        final ClassicAdvisorBox.Layout layout;
        final ClassicAdvisorBox.Bar bar;

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
                    e.consume();
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

    /** @return Whether a box is on screen. */
    boolean isShowingBox() {
        return this.current != null;
    }

    /** @return The id of the box on screen and its bar, for the probe; or null. */
    String probe() {
        final Pending p = this.current;
        if (p == null) return isBusy() ? "due" : null;
        return p.request.id.replace(' ', '_') + ":" + p.bar.row();
    }

    /** @return The layout of the box on screen, or null (tests). */
    ClassicAdvisorBox.Layout currentLayout() {
        final Pending p = this.current;
        return (p == null) ? null : p.layout;
    }

    /** @return The bar's row of the box on screen, or -1 (tests). */
    int currentBar() {
        final Pending p = this.current;
        return (p == null) ? -1 : p.bar.row();
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
        this.queue.add(p);
        setVisible(true);
        requestFocusInWindow();
        this.host.opened();
        kick();
        if (!p.done) {
            p.loop = Toolkit.getDefaultToolkit().getSystemEventQueue()
                .createSecondaryLoop();
            if (!p.loop.enter()) {
                logger.warning("Classic box: the event loop did not wait for "
                    + r.id);
            }
        }
        return p.result;
    }

    /**
     * Close every box, up or due, as dismissed: the game view goes.  EDT
     * only.
     */
    void abort() {
        this.timer.cancel();
        for (Pending p : new ArrayList<>(this.queue)) {
            finish(p, ClassicAdvisorBox.Bar.DISMISSED);
        }
    }

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

    /** Schedule the first box of the queue if none is up or scheduled. */
    private void kick() {
        if (this.current != null || this.queue.isEmpty()) return;
        final Pending p = this.queue.get(0);
        if (p.scheduled) return;
        p.scheduled = true;
        final long now = this.clock.now();
        long due = now;
        if (this.lastClose != Long.MIN_VALUE) {
            due = Math.max(due, this.lastClose + Math.round(CHAIN_MS * 1e6));
        }
        final String sprite = (p.layout.portrait == null) ? null
            : p.request.portrait.sprite;
        final boolean load = sprite != null && !sprite.equals(this.lastPortrait);
        if (load) {
            final long at = due + Math.round(PALETTE_LEAD_MS * 1e6);
            this.timer.schedule(due, () -> {
                    loadPalette(p);
                    if (!p.done) this.timer.schedule(at, () -> display(p));
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

    /** Put a box on screen. */
    private void display(Pending p) {
        if (p.done || this.current != null) return;
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
                + " text=" + text.substring(0, Math.min(80, text.length())));
        }
    }

    /** Close a box with an answer and let the next one come. */
    private void finish(Pending p, int result) {
        if (p.done) return;
        p.done = true;
        p.result = result;
        this.queue.remove(p);
        final boolean wasUp = this.current == p;
        final Rectangle dirty = scaled(p.layout.bounds());
        if (wasUp) {
            this.current = null;
            this.picture = null;
            this.lastClose = this.clock.now();
        }
        if (this.queue.isEmpty()) {
            this.timer.cancel();
            setVisible(false);
        }
        // The screen under the box comes back in the same paint.
        if (wasUp) paintNow(dirty);
        ClassicFrameRecorder.event("box-close", p.request.id + " chosen=" + result
            + (wasUp ? "" : " (never shown)"));
        this.host.closed();
        if (this.queue.isEmpty()) this.host.idle();
        if (p.loop != null) p.loop.exit();
        kick();
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
        default:
            if (predates(e.getWhen()) || repeat) return;
            answer = p.bar.otherKey();
            break;
        }
        settle(p, answer);
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
        final int row = (v == null) ? -1 : p.layout.rowAt(v.x, v.y);
        final boolean in = v != null && p.layout.inBox(v.x, v.y);
        settle(p, p.bar.release(row, in));
    }

    /** After a key: repaint a moved bar, or close with the answer. */
    private void settle(Pending p, int answer) {
        if (answer != ClassicAdvisorBox.Bar.OPEN) {
            finish(p, answer);
            return;
        }
        barMoved(p);
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
        this.picture = (p == null) ? null : ClassicAdvisorBox.render(p.layout,
            p.bar.row(), this.host.wood(), this.host.font());
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
