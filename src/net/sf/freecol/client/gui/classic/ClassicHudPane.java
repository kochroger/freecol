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
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.function.BooleanSupplier;

import javax.swing.JLayeredPane;


/**
 * The in-game screen as <b>one integer-scaled 320x200 canvas</b>, like the
 * title screen ({@code ClassicMainMenuPanel}): the largest whole scale
 * {@code S = max(1, min(W/320, H/200))}, centred, the rest black.  In
 * 320x200 pixels:
 * <ul>
 *   <li>the menu strip (0,0,320,8), {@link ClassicMenuStrip};</li>
 *   <li>the map (0,8,240,192), {@link ClassicMapViewer} on the fixed scale
 *       S, i.e. the original's 15x12 tiles of 16 px;</li>
 *   <li>the right panel (240,8,80,192), {@link ClassicInfoPanel};</li>
 *   <li>over all of it, on the popup layer, the strip's dropdown layer
 *       (visible only while a menu is open);</li>
 *   <li>over them, the advisor boxes ({@link ClassicAdvisorLayer}, build
 *       spec W7; visible only while a box is up or due);</li>
 *   <li>on top, on the drag layer, the original mouse arrow
 *       ({@link ClassicPointer}): it never takes a mouse event, blanks this
 *       pane's cursor (which every child inherits) over the canvas and gives
 *       the system arrow back in the letterbox.  It stays hidden while the
 *       first scene is up, whose overlay draws its own arrow, and during
 *       a woodcut's dissolve (W9), and is drawn in the woodcut palette's
 *       grey while a woodcut is up ({@link ClassicPointer#setDimmed}).</li>
 * </ul>
 * One grid for everything is what lets the strip, the panel and the map
 * line up as in the original, and what the first scene's advisor box (which
 * spans map and panel) needs.  The scene itself is shown by
 * {@link ClassicHudOverlay} as the frame's glass pane, which uses the same
 * scale and letterbox ({@link ClassicHudOverlay#canvas}), so it lies exactly
 * on this canvas.
 *
 * <p>While the acceptance recorder runs ({@link ClassicFrameRecorder}),
 * this pane is the painting origin of all its children and paints through
 * the recorder's copy of the screen, and the recorder reads the map's
 * terrain layer with each paint (its phase and index hint, M1c design 10
 * §9.2); otherwise it paints as any pane.
 */
final class ClassicHudPane extends JLayeredPane {

    private final ClassicMenuStrip strip;
    private final ClassicMapViewer map;
    private final ClassicInfoPanel panel;
    private final ClassicPointer pointer;

    /** The advisor boxes' layer, or null until {@link #installBoxes}. */
    private ClassicAdvisorLayer boxes = null;

    /** The layer of the advisor boxes: over the dropdowns, under the arrow. */
    static final Integer BOX_LAYER = POPUP_LAYER + 10;

    /** The acceptance recorder of frames, or null: the usual case. */
    private final ClassicFrameRecorder recorder = ClassicFrameRecorder.forFrames();

    /** True while this pane paints into the recorder's copy. */
    private boolean recording = false;


    /**
     * @param arrow The original arrow sprite, or null (system cursor).
     * @param arrowHidden True while another layer draws the arrow (the
     *     first scene).
     */
    ClassicHudPane(ClassicMenuStrip strip, ClassicMapViewer map,
                   ClassicInfoPanel panel, BufferedImage arrow,
                   BooleanSupplier arrowHidden) {
        this.strip = strip;
        this.map = map;
        this.panel = panel;
        this.pointer = new ClassicPointer(arrow, this, arrowHidden);
        setOpaque(true);
        setBackground(Color.BLACK);
        setLayout(null);
        add(map, DEFAULT_LAYER);
        add(panel, DEFAULT_LAYER);
        add(strip, PALETTE_LAYER);
        add(strip.dropLayer(), POPUP_LAYER);
        add(this.pointer, DRAG_LAYER);
        // The recorder names each frame's palette by the map's phase and
        // reads its index hint (M1c design 10 §9.2, W6c).
        if (this.recorder != null) {
            this.recorder.setTerrainProbe(new ClassicFrameRecorder.TerrainProbe() {
                    @Override
                    public int paintedPhase() {
                        return map.paintedPhase();
                    }

                    @Override
                    public ClassicGamePalette gamePalette() {
                        return map.gamePalette();
                    }

                    @Override
                    public boolean indexHint(byte[] out) {
                        return map.indexHint(out);
                    }
                });
        }
    }

    /**
     * Put the advisor boxes' layer on the pane (once).  EDT only.
     *
     * @param layer The layer.
     */
    void installBoxes(ClassicAdvisorLayer layer) {
        if (this.boxes != null || layer == null) return;
        this.boxes = layer;
        add(layer, BOX_LAYER);
        layer.setBounds(0, 0, getWidth(), getHeight());
    }

    /** The arrow layer (to refresh it when the first scene goes). */
    ClassicPointer pointer() {
        return this.pointer;
    }

    /**
     * Where the canvas parts go in a w x h pane.
     *
     * @return {canvas, strip, map, panel}.
     */
    static Rectangle[] layout(int w, int h) {
        final Rectangle c = ClassicHudOverlay.canvas(w, h);
        final int s = ClassicHudOverlay.scale(w, h);
        final int top = ClassicMenuBar.HEIGHT * s;
        return new Rectangle[] {
            c,
            new Rectangle(c.x, c.y, ClassicMenuBox.VW * s, top),
            new Rectangle(c.x, c.y + top, ClassicHud.PANEL_X * s,
                          (ClassicMenuBox.VH - ClassicMenuBar.HEIGHT) * s),
            new Rectangle(c.x + ClassicHud.PANEL_X * s, c.y + top,
                          ClassicHud.PANEL_W * s, ClassicHud.PANEL_H * s)
        };
    }

    @Override
    public void doLayout() {
        final Rectangle[] r = layout(getWidth(), getHeight());
        this.map.setFixedScale(ClassicHudOverlay.scale(getWidth(), getHeight()));
        this.strip.setBounds(r[1]);
        this.map.setBounds(r[2]);
        this.panel.setBounds(r[3]);
        this.strip.dropLayer().setBounds(r[0]);
        // The whole pane: the boxes and the arrow map points with the
        // pane's canvas.
        if (this.boxes != null) this.boxes.setBounds(0, 0, getWidth(), getHeight());
        this.pointer.setBounds(0, 0, getWidth(), getHeight());
    }

    /**
     * {@inheritDoc}
     *
     * While recording: true, so a repaint of any child (the slide's
     * {@code paintImmediately} included) is painted from here, through the
     * recorder's copy.
     */
    @Override
    protected boolean isPaintingOrigin() {
        return this.recorder != null;
    }

    /**
     * {@inheritDoc}
     *
     * While recording, the pane and its children are painted into the
     * recorder's copy (as a print, which bypasses Swing's back buffer), and
     * the copy is shown in {@code g}.
     */
    @Override
    public void paint(Graphics g) {
        final ClassicFrameRecorder rec = this.recorder;
        if (rec == null || this.recording) {
            super.paint(g);
            return;
        }
        rec.paintThrough(this, g, ClassicHudOverlay.canvas(getWidth(), getHeight()),
            ClassicHudOverlay.scale(getWidth(), getHeight()), cg -> {
                this.recording = true;
                try {
                    print(cg);
                } finally {
                    this.recording = false;
                }
            });
    }
}
