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
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.util.function.Predicate;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;


/**
 * A full-window layer over the in-game view that shows the <b>first game
 * scene</b> ({@link ClassicFirstScene}) and takes every input while it is up.
 *
 * <p><b>Why a layer over the whole window.</b>  The advisor's box spans x
 * 44..279 of the 320x200 screen -- across both the map and the right panel
 * -- and the band replaces the top strip, so the scene cannot live inside
 * the map viewer or the info panel.  The layer is installed as the frame's
 * <em>glass pane</em> and paints the scene on its own integer-scaled,
 * letterboxed 320x200 canvas: band, panel and advisor opaque, the map area
 * transparent so the live map shows through, the letterbox black.  The
 * in-game HUD ({@link ClassicHudPane}) is the same canvas -- same
 * {@link #scale} and {@link #canvas}, and the frame has no menu bar -- so
 * the scene's band and panel lie exactly on the strip and the panel, and
 * the map viewer exactly under the map area (on the HUD's fixed scale:
 * 15x12 tiles of 16 px).  Any part of the map area that the live map
 * viewer does not cover is still painted black, as a guard.
 *
 * <p><b>Input.</b>  While visible the layer has the focus and consumes every
 * key event, so neither the map viewer's {@code WHEN_IN_FOCUSED_WINDOW}
 * bindings (Enter, Space, W, B, arrows) nor the menu bar's accelerators can
 * fire ({@code JComponent.processKeyEvent} skips the key bindings of a
 * consumed event); it is the deepest component with mouse listeners
 * everywhere, so every mouse event lands here too.  It is dismissed by:
 * <ul>
 *   <li>a key press that is not auto-repeat, not a bare modifier, and has no
 *       Alt/Ctrl/Meta down (Alt+Enter and Alt+F4 are handled before any
 *       component by {@code ClassicGUI.FrameKeys});</li>
 *   <li>a left or right mouse press.</li>
 * </ul>
 * Input made before the scene was on screen never dismisses it: a press
 * whose event time precedes the moment the scene was shown is ignored, and
 * the auto-repeat of a key held since then (for instance since skipping the
 * departure, through the engine's ~2 s start) is recognised by the
 * frame-wide key tracker passed in as {@code autoRepeat}.  The original's
 * own dismiss rule is not visible in stills (a proposal, like the
 * departure's skip).
 *
 * <p><b>Arrow.</b>  083/049 show the original arrow at (160,100), where the
 * game parks the mouse when the scene appears.  A {@link ClassicPointer}
 * child draws it there when the scene is shown and follows the real mouse
 * from its first move on; it blanks this layer's cursor over the canvas
 * (the system arrow stays in the letterbox).  {@link #paintOverlay} with an
 * arrow is the same picture for the preview harness.
 */
final class ClassicHudOverlay extends JComponent {

    /** Whether a key event's time looks like wall-clock milliseconds. */
    private static final long CLOCK_SANITY_MS = 60_000L;

    /** Tells whether a KEY_PRESSED is an auto-repeat (frame-wide tracker). */
    private final Predicate<KeyEvent> autoRepeat;

    /** The scene picture (320x200 ARGB, map area transparent), or null. */
    private BufferedImage scene = null;

    /** The live map viewer under the layer (for the uncovered area), or null. */
    private Component mapView = null;

    /** Called once when the player dismisses the scene. */
    private Runnable onDismiss = null;

    /** When the scene was shown (wall clock, ms). */
    private long shownAt = 0L;

    /** The original arrow over the scene. */
    private final ClassicPointer pointer;


    /**
     * @param autoRepeat Whether a key press is an auto-repeat of a key held
     *     down before (null: never).
     * @param arrow The original arrow sprite, or null (system cursor).
     */
    ClassicHudOverlay(Predicate<KeyEvent> autoRepeat, BufferedImage arrow) {
        this.autoRepeat = autoRepeat;
        this.pointer = new ClassicPointer(arrow, this, null);
        setLayout(null);
        add(this.pointer);
        setOpaque(false);
        setFocusable(true);
        // Tab must arrive as a key (it dismisses), not move the focus away.
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
                    e.consume();
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


    /** @return Whether the scene is up. */
    boolean isSceneShowing() {
        return this.scene != null && isVisible();
    }

    /**
     * Show a scene and take the focus.  EDT only.
     *
     * @param picture The 320x200 scene ({@link ClassicFirstScene#render}).
     * @param mapView The live map viewer under the layer, or null.
     * @param dismiss Run once when the player dismisses the scene.
     */
    void showScene(BufferedImage picture, Component mapView, Runnable dismiss) {
        this.scene = picture;
        this.mapView = mapView;
        this.onDismiss = dismiss;
        this.shownAt = System.currentTimeMillis();
        setVisible(true);
        doLayout();
        // The original parks the mouse in the screen's centre (083/049).
        this.pointer.place(ClassicPointer.SCENE_X, ClassicPointer.SCENE_Y);
        repaint();
        requestFocusInWindow();
        // Granted once the window is active (a just-revalidated frame may
        // not be yet).
        SwingUtilities.invokeLater(() -> {
                if (isSceneShowing()) requestFocusInWindow();
            });
    }

    /** Hide the scene (no callback).  EDT only. */
    void hideScene() {
        this.scene = null;
        this.mapView = null;
        this.onDismiss = null;
        setVisible(false);
    }


    // Input

    /** Whether an input event was made before the scene was on screen. */
    private boolean predates(long when) {
        final long now = System.currentTimeMillis();
        if (Math.abs(now - when) > CLOCK_SANITY_MS) return false;
        return when < this.shownAt;
    }

    private void onKey(KeyEvent e) {
        e.consume();
        if (!isSceneShowing()) return;
        final int code = e.getKeyCode();
        if (code == KeyEvent.VK_SHIFT || code == KeyEvent.VK_CONTROL
            || code == KeyEvent.VK_ALT || code == KeyEvent.VK_META
            || code == KeyEvent.VK_ALT_GRAPH || code == KeyEvent.VK_WINDOWS
            || code == KeyEvent.VK_CONTEXT_MENU || code == KeyEvent.VK_CAPS_LOCK
            || code == KeyEvent.VK_NUM_LOCK || code == KeyEvent.VK_UNDEFINED) {
            return;
        }
        if (e.isAltDown() || e.isControlDown() || e.isMetaDown()
            || e.isAltGraphDown()) return;
        if (predates(e.getWhen())) return;
        if (this.autoRepeat != null && this.autoRepeat.test(e)) return;
        dismiss();
    }

    private void onPress(MouseEvent e) {
        e.consume();
        requestFocusInWindow();
        if (!isSceneShowing()) return;
        if (!SwingUtilities.isLeftMouseButton(e)
            && !SwingUtilities.isRightMouseButton(e)) return;
        if (predates(e.getWhen())) return;
        dismiss();
    }

    private void dismiss() {
        final Runnable r = this.onDismiss;
        this.onDismiss = null;
        if (r != null) r.run();
    }


    // Painting

    /** The canvas scale for a w x h layer: the largest whole factor, at least 1. */
    static int scale(int w, int h) {
        return Math.max(1, Math.min(w / ClassicMenuBox.VW, h / ClassicMenuBox.VH));
    }

    /** The letterboxed 320x200 canvas in a w x h layer. */
    static Rectangle canvas(int w, int h) {
        final int s = scale(w, h);
        final int cw = ClassicMenuBox.VW * s, ch = ClassicMenuBox.VH * s;
        return new Rectangle((w - cw) / 2, (h - ch) / 2, cw, ch);
    }

    /**
     * Paint a scene over a w x h layer: black letterbox, black where the
     * map area is not over the live map, the scene picture scaled
     * nearest-neighbour.
     *
     * @param g The layer's graphics.
     * @param w The layer width.
     * @param h The layer height.
     * @param scene The 320x200 scene.
     * @param mapViewBounds The live map viewer in layer coordinates, or null.
     */
    static void paintOverlay(Graphics2D g, int w, int h, BufferedImage scene,
                             Rectangle mapViewBounds) {
        paintOverlay(g, w, h, scene, mapViewBounds, null, 0, 0);
    }

    /**
     * {@link #paintOverlay(Graphics2D, int, int, BufferedImage, Rectangle)}
     * plus the original arrow at the virtual point (ax, ay) -- what the live
     * layer and its {@link ClassicPointer} child show together.
     *
     * @param arrow The arrow sprite, or null for none.
     */
    static void paintOverlay(Graphics2D g, int w, int h, BufferedImage scene,
                             Rectangle mapViewBounds, BufferedImage arrow,
                             int ax, int ay) {
        paintScene(g, w, h, scene, mapViewBounds);
        ClassicPointer.paintArrow(g, arrow, w, h, ax, ay);
    }

    private static void paintScene(Graphics2D g, int w, int h, BufferedImage scene,
                                   Rectangle mapViewBounds) {
        final Rectangle c = canvas(w, h);
        final int s = scale(w, h);
        final Area black = new Area(new Rectangle(0, 0, w, h));
        black.subtract(new Area(c));
        final Area map = new Area(new Rectangle(c.x, c.y + ClassicMenuBar.HEIGHT * s,
            ClassicHud.PANEL_X * s, (ClassicMenuBox.VH - ClassicMenuBar.HEIGHT) * s));
        if (mapViewBounds != null) map.subtract(new Area(mapViewBounds));
        black.add(map);
        g.setColor(Color.BLACK);
        g.fill(black);
        final Graphics2D gg = (Graphics2D) g.create();
        try {
            gg.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            gg.drawImage(scene, c.x, c.y, c.width, c.height, null);
        } finally {
            gg.dispose();
        }
    }

    @Override
    public void doLayout() {
        this.pointer.setBounds(0, 0, getWidth(), getHeight());
    }

    @Override
    protected void paintComponent(Graphics g) {
        final BufferedImage img = this.scene;
        if (img == null) return;
        Rectangle mv = null;
        final Component m = this.mapView;
        if (m != null && m.isShowing()) {
            mv = SwingUtilities.convertRectangle(m.getParent(), m.getBounds(), this);
        }
        paintOverlay((Graphics2D) g, getWidth(), getHeight(), img, mv);
    }
}
