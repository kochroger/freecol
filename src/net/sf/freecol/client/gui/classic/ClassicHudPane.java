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
 *   <li>on top, on the drag layer, the original mouse arrow
 *       ({@link ClassicPointer}): it never takes a mouse event, blanks this
 *       pane's cursor (which every child inherits) over the canvas and gives
 *       the system arrow back in the letterbox.  It stays hidden while the
 *       first scene is up, whose overlay draws its own arrow.</li>
 * </ul>
 * One grid for everything is what lets the strip, the panel and the map
 * line up as in the original, and what the first scene's advisor box (which
 * spans map and panel) needs.  The scene itself is shown by
 * {@link ClassicHudOverlay} as the frame's glass pane, which uses the same
 * scale and letterbox ({@link ClassicHudOverlay#canvas}), so it lies exactly
 * on this canvas.
 */
final class ClassicHudPane extends JLayeredPane {

    private final ClassicMenuStrip strip;
    private final ClassicMapViewer map;
    private final ClassicInfoPanel panel;
    private final ClassicPointer pointer;


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
        // The whole pane: the arrow layer maps points with the pane's canvas.
        this.pointer.setBounds(0, 0, getWidth(), getHeight());
    }
}
