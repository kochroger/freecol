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

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * The original's <b>first game scene</b>: what the player sees after the
 * departure, before the first move.  References: Dutch
 * {@code start-sequence-dutch/opening_083} (Dutch merchantman) and English
 * {@code start-sequence/opening_049} (English caravel); the advisor-box
 * geometry is cross-checked on the Europe advisor boxes {@code opening_011}
 * and {@code 013}.  A renderer built from this class reproduces the band,
 * the right panel, the advisor box and the portrait of both captures --
 * 30,469 pixels each -- with 0 differing pixels once the original's mouse
 * arrow is painted at (160,100) (without it exactly the arrow's 4 pixels at
 * (165..166, 112..113) differ).
 *
 * <p><b>What it shows.</b>
 * <ul>
 *   <li>The top strip carries a gold <b>title band</b> instead of the menu
 *       bar: NAMES.TXT {@code @NATIONALITY[n]} + ' ' + {@code @UNIT[ship]}
 *       + ' ' + LABELS.TXT {@code @MISC} 6 + ' ' + {@code @HOMEPORT[n]}
 *       (nationality, ship, "arriving from", home port), painted by
 *       {@link ClassicMenuBar#paintBand}.</li>
 *   <li>The right panel in scene mode: minimap, season and gold, no unit
 *       block ({@link ClassicHud}).</li>
 *   <li>The admiral, {@code MSS0.SS.000} (75x91), with a wood text box:
 *       {@link ClassicMenuBox#paintDialogFrame} in the GAME theme, then
 *       GAME.TXT {@code @TUTORIAL1} with {@code %STRING0} = the ship's
 *       {@code @UNIT} name -- the original does not inflect the fixed
 *       possessive before it, also for the feminine caravel (049) -- laid
 *       out by {@link ClassicTextLayout} in FONTTINY with the
 *       message's {@code @width} (230: widths 228..232 give the captured
 *       breaks, 226 does not), left = box.x + 5, top = box.y + 9, pitch 6,
 *       drawn green with {@code {..}} in gold, then the portrait on top:
 *       its hands (sprite rows 71..90) overlap the box's top and left
 *       edges, so it is drawn LAST.</li>
 * </ul>
 *
 * <p><b>Geometry rule (observed, fits 083/049/011/013).</b>  The box is
 * {@code @width + 6} wide and {@link ClassicMenuBox#dialogHeight}{@code
 * (P, 0)} tall (P = laid-out lines; 5 gives 48).  The portrait sits at a
 * fixed offset from the box that depends on the advisor -- MSS0 (admiral)
 * at box + (-4, -71); MSS2 (trade advisor, Europe 011/013) at box + (57,
 * -78), centred over its box.  The UNION of portrait and box is centred on
 * the 320x200 screen: {@code x = (320 - w + 1) / 2}, {@code y = (200 - h +
 * 1) / 2}, the original's {@code (321 - w) div 2} and {@code (201 - h) div
 * 2}.  The union's height runs from the top of box or portrait to the box
 * BOTTOM: a portrait part below the box does not count (the soldier MSS1,
 * 139 tall at -77, below the 40-high box of {@code @WHACKINDIANS}: box
 * (34,119), not (34,108)).  For MSS0 that is a 240x119 union at (40,41):
 * portrait (40,41), box (44,112,236,48).  The portraits' SS header anchors are
 * degenerate (1,1), so the anchor cannot place them.  TUTORIAL1's
 * {@code @x=10} and {@code @y=40} are NOT used: they are neither the text
 * position (49,121), the box (44,112) nor the portrait (40,41), and their
 * meaning is unknown.
 */
final class ClassicFirstScene {

    /** The admiral's portrait frame. */
    static final String PORTRAIT = "MSS0.SS.000";

    /** The advisor's text. */
    static final String TEXT_SECTION = "TUTORIAL1";

    /** The admiral's offset from the box's top-left (083/049). */
    static final int MSS0_OFF_X = -4, MSS0_OFF_Y = -71;

    /** The trade advisor's offset (Europe 011/013), kept for reuse. */
    static final int MSS2_OFF_X = 57, MSS2_OFF_Y = -78;

    /** Text inset from the box: left and first glyph top. */
    static final int TEXT_DX = 5, TEXT_DY = 9;

    /** LABELS.TXT {@code @MISC} index of the "arriving from" label (LABELS.TXT:21). */
    static final int MISC_ARRIVAL = 6;

    /**
     * FreeCol's ship types in the order of their NAMES.TXT {@code @UNIT}
     * rows 13..18 (NAMES.TXT:332-337): caravel, merchantman, galleon,
     * privateer, frigate, man-of-war.
     */
    static final String[] SHIP_IDS = {
        "model.unit.caravel", "model.unit.merchantman", "model.unit.galleon",
        "model.unit.privateer", "model.unit.frigate", "model.unit.manOWar"
    };

    /** The {@code @UNIT} row of {@link #SHIP_IDS}[0]. */
    static final int FIRST_SHIP_ROW = 13;


    /** Everything the scene paints besides the map and the panel. */
    static final class Model {

        /** The title band text. */
        final String band;

        /** The advisor's laid-out lines (screen coordinates). */
        final List<ClassicTextLayout.Line> lines;

        /** The text box. */
        final Rectangle box;

        /** The portrait's top-left. */
        final Point portraitAt;

        /** The portrait. */
        final BufferedImage portrait;

        Model(String band, List<ClassicTextLayout.Line> lines, Rectangle box,
              Point portraitAt, BufferedImage portrait) {
            this.band = band;
            this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
            this.box = box;
            this.portraitAt = portraitAt;
            this.portrait = portrait;
        }
    }


    private ClassicFirstScene() {}   // static helpers only


    /** @return The {@code @UNIT} row of a FreeCol ship type id, or -1. */
    static int shipRow(String unitTypeId) {
        final int i = Arrays.asList(SHIP_IDS).indexOf(unitTypeId);
        return (i < 0) ? -1 : FIRST_SHIP_ROW + i;
    }

    /** Column 0 of a NAMES.TXT row, or null. */
    private static String name(ClassicText t, String section, int row) {
        final List<String[]> rows = t.names(section);
        return (row < 0 || row >= rows.size()) ? null : rows.get(row)[0];
    }

    /**
     * The band text: nationality, ship, "arriving from" label, home port.
     *
     * @param nation The nation index (England, France, Spain, Holland).
     * @param shipRow The ship's {@code @UNIT} row.
     * @return The text, or null when a piece is missing.
     */
    static String bandText(ClassicText t, int nation, int shipRow) {
        if (t == null) return null;
        final String nat = name(t, "NATIONALITY", nation);
        final String ship = name(t, "UNIT", shipRow);
        final String arrival = t.misc(MISC_ARRIVAL);
        final String port = name(t, "HOMEPORT", nation);
        if (nat == null || ship == null || arrival == null || port == null) {
            return null;
        }
        return nat + " " + ship + " " + arrival.trim() + " " + port;
    }

    /**
     * {@code @TUTORIAL1} with {@code %STRING0} = the ship's name.
     *
     * @return The message with substituted lines, or null.
     */
    static ClassicText.Message advisorText(ClassicText t, int shipRow) {
        if (t == null) return null;
        final ClassicText.Message m = t.message(TEXT_SECTION);
        final String ship = name(t, "UNIT", shipRow);
        if (m == null || m.width == null || m.text.isEmpty() || ship == null) {
            return null;
        }
        final Map<String, String> v = new HashMap<>();
        v.put("STRING0", ship);
        final List<String> lines = new ArrayList<>(m.text.size());
        for (String s : m.text) lines.add(ClassicText.substitute(s, v));
        return m.withText(lines);
    }

    /**
     * The union-centring rule (class comment).
     *
     * @param boxW The box width.
     * @param boxH The box height.
     * @param pw The portrait width.
     * @param ph The portrait height.
     * @param offX The portrait's x offset from the box.
     * @param offY The portrait's y offset from the box.
     * @return {box top-left, portrait top-left}.
     */
    static Point[] place(int boxW, int boxH, int pw, int ph, int offX, int offY) {
        final int ux0 = Math.min(0, offX), uy0 = Math.min(0, offY);
        // Vertically the union ends at the box bottom: a portrait part
        // below the box (the soldier under a short box) does not count.
        final int ux1 = Math.max(boxW, offX + pw), uy1 = boxH;
        final int uw = ux1 - ux0, uh = uy1 - uy0;
        final int left = (ClassicMenuBox.VW - uw + 1) / 2;
        final int top = (ClassicMenuBox.VH - uh + 1) / 2;
        final Point box = new Point(left - ux0, top - uy0);
        return new Point[] { box, new Point(box.x + offX, box.y + offY) };
    }

    /**
     * Build the scene for a nation and its ship.
     *
     * @param t The original texts.
     * @param tiny FONTTINY.
     * @param mss0 The admiral's portrait.
     * @param nation The nation index (England 0, France 1, Spain 2, Holland 3).
     * @param shipRow The ship's {@code @UNIT} row ({@link #shipRow}).
     * @return The model, or null when any piece is missing.
     */
    static Model build(ClassicText t, ClassicFont tiny, BufferedImage mss0,
                       int nation, int shipRow) {
        if (t == null || tiny == null || mss0 == null) return null;
        final String band = bandText(t, nation, shipRow);
        final ClassicText.Message m = advisorText(t, shipRow);
        if (band == null || m == null) return null;
        // The breaks do not depend on the position: lay out once to count
        // the lines, place the box, then lay out at the final position.
        final int p = ClassicTextLayout.layout(tiny, m.text, m.width, 0, 0,
            ClassicMenuBox.PROMPT_PITCH).size();
        if (p == 0) return null;
        final int bw = m.width + 6, bh = ClassicMenuBox.dialogHeight(p, 0);
        final Point[] at = place(bw, bh, mss0.getWidth(), mss0.getHeight(),
                                 MSS0_OFF_X, MSS0_OFF_Y);
        final Rectangle box = new Rectangle(at[0].x, at[0].y, bw, bh);
        final List<ClassicTextLayout.Line> lines = ClassicTextLayout.layout(
            tiny, m.text, m.width, box.x + TEXT_DX, box.y + TEXT_DY,
            ClassicMenuBox.PROMPT_PITCH);
        return new Model(band, lines, box, at[1], mss0);
    }

    /**
     * The advisor: box frame, text, then the portrait on top.
     *
     * @param g The graphics, in 320x200 virtual pixels.
     * @param m The model.
     * @param wood {@code WOODTILE.SS.000}, or null.
     * @param tiny FONTTINY, or null for the Swing fallback.
     */
    static void paintAdvisor(Graphics2D g, Model m, BufferedImage wood,
                             ClassicFont tiny) {
        ClassicMenuBox.paintDialogFrame(g, m.box, ClassicMenuBox.GAME, wood);
        for (ClassicTextLayout.Line l : m.lines) {
            ClassicMenuBox.text(g, tiny, l.marked, l.x, l.y, ClassicMenuBox.GAME,
                                true);
        }
        g.drawImage(m.portrait, m.portraitAt.x, m.portraitAt.y, null);
    }

    /**
     * Everything of the scene except the map: band, panel (scene mode),
     * advisor.  The map area (0,8,240,192) is left untouched.
     *
     * @param g The graphics, in 320x200 virtual pixels.
     */
    static void paintScene(Graphics2D g, Model m, ClassicHud.PanelModel panel,
                           BufferedImage wood, ClassicFont tiny) {
        ClassicMenuBar.paintBand(g, tiny, wood, m.band);
        ClassicHud.paintPanel(g, tiny, wood, panel);
        paintAdvisor(g, m, wood, tiny);
    }

    /**
     * The scene as a 320x200 ARGB picture whose map area is transparent
     * where the advisor does not cover it, for an overlay over the live
     * map.
     */
    static BufferedImage render(Model m, ClassicHud.PanelModel panel,
                                BufferedImage wood, ClassicFont tiny) {
        final BufferedImage img = new BufferedImage(ClassicMenuBox.VW,
            ClassicMenuBox.VH, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g = img.createGraphics();
        try {
            paintScene(g, m, panel, wood, tiny);
        } finally {
            g.dispose();
        }
        return img;
    }
}
