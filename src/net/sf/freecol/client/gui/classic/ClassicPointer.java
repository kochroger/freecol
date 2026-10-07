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

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;


/**
 * The original's <b>mouse arrow</b> ({@code CURSOR.SS.000}) over the in-game
 * canvas: a transparent layer, as large as the HUD pane (or the first-scene
 * overlay), that draws the arrow on the letterboxed 320x200 grid at the
 * canvas scale, and hides the system cursor while it does.
 *
 * <p><b>Why.</b>  Captures 083/049 (first scene), 032/052 (play) and 001-005,
 * 053 (open dropdowns) all show the original arrow; before this layer only
 * the title canvas drew it ({@code ClassicMainMenuPanel}), so the first
 * in-game frame showed the Windows pointer.  It is drawn rather than set as
 * a custom system cursor for the reason the title gives: Windows caps custom
 * cursors at 32x32, so a system cursor could not be larger than x2, while
 * the canvas runs at x4/x5 ({@code ClassicMainMenuPanel}, "Why the arrow is
 * drawn into the canvas").  Its hot spot is the sprite origin.
 *
 * <p><b>How.</b>  The layer never takes a mouse event ({@link #contains}
 * is false, so Swing dispatches to the strip, map, panel or dropdown under
 * it); it follows the mouse through an {@code AWTEventListener} for its own
 * window while it is displayable.  Over the canvas it draws the arrow and
 * gives {@link #cursorOwner} (the HUD pane, whose children inherit its
 * cursor; or the overlay itself) a blank cursor; in the letterbox, outside
 * the window, while another window has the mouse (a modal classic dialog,
 * the colony or report windows) or after the window lost activation it draws
 * nothing and gives the system arrow back, so there is never no pointer and
 * never two.
 *
 * <p>{@link #place} puts the arrow somewhere until the mouse first moves:
 * the first scene uses it for the original's (160,100), where the game
 * parks the mouse when the scene appears (083/049).  Only real motion
 * (moved, dragged, pressed, released) moves the arrow; the enter/exit pairs
 * Swing synthesises between child components or when the overlay appears
 * do not, except an exit that leaves the layer, which hides it.
 *
 * <p>Limits: the drawn arrow moves on the event thread, so it stops while the
 * EDT is blocked (the system arrow would not); in play that only happens for
 * the short synchronous server calls.  It is drawn on the 320x200 grid, so
 * it moves in steps of one original pixel, as in the original.
 */
final class ClassicPointer extends JComponent {

    private static final Logger logger = Logger.getLogger(ClassicPointer.class.getName());

    /** Where the original parks the mouse when the first scene appears (083/049). */
    static final int SCENE_X = 160, SCENE_Y = 100;

    /** The shared invisible system cursor, or null if the platform has none. */
    private static Cursor blank = null;
    private static boolean blankFailed = false;

    /** The arrow sprite ({@code ClassicMainMenuPanel.cursorSprite}), or null. */
    private final BufferedImage sprite;

    /** Whose cursor is blanked over the canvas. */
    private final Component cursorOwner;

    /** True while this layer must not draw (another layer draws the arrow). */
    private final BooleanSupplier suppressed;

    /**
     * The arrow under a woodcut's palette ({@link ClassicWoodcut#dimArrow}),
     * made on first use, and whether it is drawn now.
     */
    private BufferedImage dimSprite = null;
    private boolean dimmed = false;

    /** The arrow's virtual top-left, or -1 while it is not drawn. */
    private int vx = -1, vy = -1;

    /** The AWT listener while displayable, or null. */
    private AWTEventListener listener = null;


    /**
     * @param sprite The arrow, or null (then nothing is drawn and the system
     *     cursor stays).
     * @param cursorOwner The component whose cursor is blanked over the
     *     canvas.
     * @param suppressed True while this layer must not draw (null: never).
     */
    ClassicPointer(BufferedImage sprite, Component cursorOwner,
                   BooleanSupplier suppressed) {
        this.sprite = sprite;
        this.cursorOwner = cursorOwner;
        this.suppressed = suppressed;
        setOpaque(false);
        setFocusable(false);
    }


    // Pure helpers (headless)

    /**
     * The 320x200 point under a layer point, or null in the letterbox.
     *
     * @param w The layer width.
     * @param h The layer height.
     * @param p The point in layer coordinates.
     */
    static Point virtual(int w, int h, Point p) {
        final Rectangle c = ClassicHudOverlay.canvas(w, h);
        final int s = ClassicHudOverlay.scale(w, h);
        if (p == null || !c.contains(p)) return null;
        return new Point((p.x - c.x) / s, (p.y - c.y) / s);
    }

    /**
     * Draw the arrow with its top-left at the virtual point (x, y) of the
     * letterboxed canvas of a w x h layer, nearest-neighbour, clipped to the
     * canvas (as the original's arrow is clipped to its screen).
     */
    static void paintArrow(Graphics2D g, BufferedImage sprite, int w, int h,
                           int x, int y) {
        if (sprite == null) return;
        final Rectangle c = ClassicHudOverlay.canvas(w, h);
        final int s = ClassicHudOverlay.scale(w, h);
        final Graphics2D gg = (Graphics2D) g.create();
        try {
            gg.clip(c);
            gg.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            gg.drawImage(sprite, c.x + x * s, c.y + y * s,
                         sprite.getWidth() * s, sprite.getHeight() * s, null);
        } finally {
            gg.dispose();
        }
    }

    /** An invisible cursor, or null if the platform cannot make one. */
    static Cursor blankCursor() {
        if (blank == null && !blankFailed) {
            try {
                blank = Toolkit.getDefaultToolkit().createCustomCursor(
                    new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
                    new Point(0, 0), "classic-hud-none");
            } catch (RuntimeException e) {    // e.g. HeadlessException
                logger.log(Level.FINE, "No blank cursor; keeping the system arrow", e);
                blankFailed = true;
            }
        }
        return blank;
    }


    // State

    /** Whether the arrow is drawn now. */
    boolean drawsArrow() {
        return this.sprite != null && this.vx >= 0 && this.vy >= 0
            && blankCursor() != null
            && (this.suppressed == null || !this.suppressed.getAsBoolean());
    }

    /** The arrow's virtual position, or null. */
    Point position() {
        return (this.vx < 0) ? null : new Point(this.vx, this.vy);
    }

    /**
     * Put the arrow at a virtual point until the mouse moves (the first
     * scene: {@link #SCENE_X}, {@link #SCENE_Y}).
     */
    void place(int x, int y) {
        setPos(x, y);
    }

    /**
     * Draw the arrow's grey as the woodcut's palette shows it (W9: #797979
     * instead of #AAAAAA, from a woodcut's black to its palette's return).
     *
     * @param on True while a woodcut's palette is up.
     */
    void setDimmed(boolean on) {
        if (on == this.dimmed) return;
        if (on && this.dimSprite == null) this.dimSprite = ClassicWoodcut.dimArrow(this.sprite);
        this.dimmed = on;
        repaintArrow();
    }

    /** @return Whether the arrow is drawn in the woodcut's grey. */
    boolean isDimmed() {
        return this.dimmed;
    }

    /** Re-evaluate drawing and the cursor (e.g. when suppression ended). */
    void refresh() {
        updateCursor();
        repaintArrow();
    }

    /** A layer that never takes the mouse: events go to what is under it. */
    @Override
    public boolean contains(int x, int y) {
        return false;
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (this.listener == null) {
            this.listener = this::onAwtEvent;
            try {
                Toolkit.getDefaultToolkit().addAWTEventListener(this.listener,
                    AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK
                    | AWTEvent.WINDOW_EVENT_MASK);
            } catch (RuntimeException e) {
                logger.log(Level.FINE, "No AWT listener; system arrow only", e);
                this.listener = null;
            }
        }
        // Start where the mouse already is (it may not move for a while).
        SwingUtilities.invokeLater(this::fromMouseInfo);
    }

    @Override
    public void removeNotify() {
        if (this.listener != null) {
            Toolkit.getDefaultToolkit().removeAWTEventListener(this.listener);
            this.listener = null;
        }
        this.vx = this.vy = -1;
        if (this.cursorOwner != null) this.cursorOwner.setCursor(null);
        super.removeNotify();
    }

    /** Take the position from MouseInfo, if the mouse is over our canvas. */
    private void fromMouseInfo() {
        if (!isShowing() || this.vx >= 0) return;
        try {
            final PointerInfo pi = MouseInfo.getPointerInfo();
            if (pi == null) return;
            final Point p = new Point(pi.getLocation());
            SwingUtilities.convertPointFromScreen(p, this);
            final Window w = SwingUtilities.getWindowAncestor(this);
            if (w == null || !w.isActive()) return;
            final Point v = virtual(getWidth(), getHeight(), p);
            if (v != null) setPos(v.x, v.y);
        } catch (RuntimeException e) {
            logger.log(Level.FINE, "No pointer info", e);
        }
    }

    private void onAwtEvent(AWTEvent ev) {
        if (!isShowing()) return;
        final Window mine = SwingUtilities.getWindowAncestor(this);
        if (ev instanceof WindowEvent) {
            final int id = ev.getID();
            if (((WindowEvent) ev).getWindow() == mine
                && (id == WindowEvent.WINDOW_DEACTIVATED
                    || id == WindowEvent.WINDOW_LOST_FOCUS
                    || id == WindowEvent.WINDOW_ICONIFIED)) {
                setPos(-1, -1);
            }
            return;
        }
        if (!(ev instanceof MouseEvent)) return;
        final MouseEvent e = (MouseEvent) ev;
        final Component src = e.getComponent();
        if (src == null) return;
        final Window w = (src instanceof Window) ? (Window) src
            : SwingUtilities.getWindowAncestor(src);
        if (w != mine) {
            // The mouse is in another window: no drawn arrow here.
            setPos(-1, -1);
            return;
        }
        final Point p = SwingUtilities.convertPoint(src, e.getPoint(), this);
        switch (e.getID()) {
        case MouseEvent.MOUSE_MOVED: case MouseEvent.MOUSE_DRAGGED:
        case MouseEvent.MOUSE_PRESSED: case MouseEvent.MOUSE_RELEASED:
            final Point v = virtual(getWidth(), getHeight(), p);
            if (v == null) setPos(-1, -1); else setPos(v.x, v.y);
            break;
        case MouseEvent.MOUSE_EXITED:
            if (!new Rectangle(0, 0, getWidth(), getHeight()).contains(p)) {
                setPos(-1, -1);
            }
            break;
        default:
            break;
        }
    }

    private void setPos(int x, int y) {
        if (x == this.vx && y == this.vy) return;
        repaintArrow();
        this.vx = x;
        this.vy = y;
        updateCursor();
        repaintArrow();
    }

    /** Blank the owner's cursor exactly while the arrow is drawn. */
    private void updateCursor() {
        if (this.cursorOwner == null) return;
        final Cursor want = (this.sprite != null && this.vx >= 0) ? blankCursor() : null;
        final boolean set = this.cursorOwner.isCursorSet();
        if (want == null ? set : this.cursorOwner.getCursor() != want) {
            this.cursorOwner.setCursor(want);    // null: inherit the default arrow
        }
    }

    /** Repaint the screen area of the arrow at its current position. */
    private void repaintArrow() {
        if (this.sprite == null || this.vx < 0) return;
        final Rectangle c = ClassicHudOverlay.canvas(getWidth(), getHeight());
        final int s = ClassicHudOverlay.scale(getWidth(), getHeight());
        repaint(c.x + this.vx * s, c.y + this.vy * s,
                this.sprite.getWidth() * s, this.sprite.getHeight() * s);
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (!drawsArrow()) return;
        paintArrow((Graphics2D) g, (this.dimmed && this.dimSprite != null)
                   ? this.dimSprite : this.sprite, getWidth(), getHeight(),
                   this.vx, this.vy);
    }
}
