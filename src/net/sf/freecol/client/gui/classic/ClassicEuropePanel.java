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
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.control.InGameController;
import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.AbstractUnit;
import net.sf.freecol.common.model.Europe;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.GoodsContainer;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.HighSeas;
import net.sf.freecol.common.model.Location;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Market;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Role;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;


/**
 * The classic-UI <b>Europe</b> screen — the docks of the player's home port in
 * the original 1994 <em>Colonization</em> (clip008 {@code 01-europe.md},
 * clip 020, the N1 prep {@code n/prep/europe.md}).
 *
 * <p>As with {@link ClassicColonyPanel}, everything is painted into a virtual
 * <b>320&times;200</b> canvas (the original's VGA resolution) and then up-scaled
 * by the largest integer factor that fits the window, nearest-neighbour.  The
 * original {@code EUROPE.PIK} harbour picture is the backdrop; it already
 * holds the three boxes, the six holds, the market slots and the exit "E".
 * Drawn over it, where the original draws them (part N1, Roger 2026-10-09:
 * "In Europa kann ich gar nichts tun ... das Schiff kommt nie an"):
 *
 * <ul>
 *   <li><b>The three boxes' captions</b> (FONTTINY ink #348220, glyph tops
 *       120/127, V clip008 §2.4): «Bald erwartet» (ships sailing to
 *       Europe), «Ziel:» and the New World's name (ships sailing there),
 *       «Einladen:» and the selected ship's type, or «Keine Schiffe im
 *       Hafen».</li>
 *   <li><b>Ships in port</b> 1:1 with their flag in box 3 at (146 + 18i,
 *       146); the selected one in an 18&times;18 green frame.  The first
 *       ship is selected when the screen opens, after each change and
 *       after a ship sailed (V clip008 #17007, clip 020 #24220).</li>
 *   <li><b>The selected ship's six holds</b> (x 147 + 12k, y 165): the
 *       colonists aboard first (I: how the original draws them is not
 *       seen, question 1), then the goods, one hold per 100 (an icon,
 *       grey below 100, V clip008 §2.5), crates on the holds the ship does
 *       not have, six crates without a ship.</li>
 *   <li><b>The docks</b> at (233 + 17i, 138), three per row, the next row
 *       23 px lower, the newest colonist at the left, with its flag ("S":
 *       boards the next ship).</li>
 *   <li><b>Ships under way</b> in box 1 (to Europe) and box 2 (to the New
 *       World): the ship at box x + 1, y 146, its passengers after it 17 px
 *       apart (V clip008 §2.7).</li>
 *   <li>The stopgap's title bar, market row and four buttons (W22: the
 *       original's look; the market now shows "bid/ask").</li>
 * </ul>
 *
 * <p><b>The mouse as in the original</b> (drag and drop, V clip008 §3):
 * <ul>
 *   <li>a ship in port dropped on box 2 «Ziel:» asks {@code @SAILAWAY}
 *       and sails on «Jawohl»; a ship of box 1 dropped on box 2 turns
 *       back to the New World (manual);</li>
 *   <li>a market slot dropped on a ship in port or on the holds buys a
 *       hold (100, or the hold's rest), with Shift {@code @HOWMUCH4};</li>
 *   <li>a hold of goods dropped anywhere on the market row sells it, with
 *       Shift {@code @HOWMUCH5};</li>
 *   <li>a colonist's hold dropped on the docks puts him on the dock
 *       (FreeCol's {@code leaveShip}); a colonist of the dock dropped on a
 *       ship or its holds boards it ({@code boardShip}; I, question 2);
 *       colonists marked "S" board by themselves when the ship sails;</li>
 *   <li>a click on a ship in port selects it, a click on the selected ship
 *       opens {@code @EUROPESHIPCLICK} (clip 020 #27512), a click on a
 *       colonist of the dock {@code @EUROPEARM} ({@link
 *       ClassicEuropeOptions}); a click on a market slot moves the market
 *       cursor there; a drop back on its own place, or anywhere else, does
 *       nothing;</li>
 *   <li>the buttons act on the release (clip008 §2.3).</li>
 * </ul>
 * Keys: Escape and E close the screen; R / 1, K / 2, A / 3 the buttons
 * (their yellow first letters in the original); Enter the selected ship's
 * box; U sells its first hold of goods.  Europe closes by itself
 * {@link ClassicVoyages#EUROPE_CLOSE_MS} after the last ship in port sailed
 * (ClassicGUI).
 */
final class ClassicEuropePanel extends JPanel {

    /** The original VGA canvas this screen is laid out in. */
    static final int VW = 320;
    static final int VH = 200;

    /** Title bar: full width, gold on black. */
    private static final int TITLE_H = 9;

    /** The stopgap's action buttons, stacked at the top right. */
    private static final int BTN_W = 40;
    private static final int BTN_H = 11;
    private static final int BTN_X = VW - BTN_W - 1;
    private static final int BTN_Y = TITLE_H + 3;

    /** The three boxes of the backdrop (V clip008 §2.1/2.4: x 1-70 / 72-141 / 143-223, y 118-177). */
    static final Rectangle BOX1 = new Rectangle(1, 118, 70, 60);
    static final Rectangle BOX2 = new Rectangle(72, 118, 70, 60);
    static final Rectangle BOX3 = new Rectangle(143, 118, 81, 60);

    /** Where a colonist dropped from a hold goes on the dock (the quay right of box 3; I). */
    static final Rectangle DOCKS = new Rectangle(224, 118, 96, 61);

    /** The boxes' captions: FONTTINY ink, glyph tops of the two lines (V clip008 §2.4). */
    static final int CAPTION_RGB = 0x348220, CAPTION_Y1 = 120, CAPTION_Y2 = 127;

    /** Ships in port: the first cell and the step (V clip008 §2.5). */
    static final int SHIP_X = 146, SHIP_Y = 146, SHIP_PITCH = 18;

    /** The holds: hold k's inside at (147 + 12k, 165), 10 x 12 (V clip008 §2.5). */
    static final int HOLD_X = 147, HOLD_Y = 165, HOLD_PITCH = 12, HOLD_W = 10,
        HOLD_H = 12, HOLDS = 6;

    /** The docks: the first cell, the step, the next row, cells per row (V clip008 §2.6, clip 020 #28000). */
    static final int DOCK_X = 233, DOCK_Y = 138, DOCK_PITCH = 17, DOCK_ROW = 23,
        DOCK_PER_ROW = 3;

    /** The last dock cell's left edge (I: the quay ends there). */
    private static final int DOCK_LAST_X = 303;

    /** Ships under way: their y and the step between ship and passengers (V clip008 §2.7). */
    static final int SAIL_Y = 146, SAIL_PITCH = 17;

    /** The market slots: slot k at (19k, 179), 19 x 21 (V clip008 §2.1/2.8). */
    static final int MARKET_Y = 179, MARKET_W = 19, MARKET_H = VH - MARKET_Y;

    /** The stopgap's plate under the market's icons and prices. */
    private static final int PLATE_Y = 182;

    /** The exit "Abb / E" at the bottom right (V clip008 §3.7: x 305-319, y 179-199). */
    static final Rectangle EXIT = new Rectangle(305, 179, 15, 21);

    /** ICONS.SS frames: the goods (coloured, grey) and the crate (V clip008 §2.5). */
    static final int ICON_GOODS = 22, ICON_GOODS_GREY = 38, ICON_CRATE = 122;

    /** The selection frames' green, the white while pressed (VGA 10, 15; V clip008 §2.10). */
    static final Color FRAME = new Color(0x55, 0xFF, 0x55);
    static final Color FRAME_PRESSED = new Color(0xFF, 0xFF, 0xFF);

    private static final Color TITLE_BG = new Color(0x00, 0x00, 0x00);
    private static final Color GOLD = new Color(0xC8, 0xB0, 0x40);
    private static final Color BTN_BG = new Color(0x30, 0x28, 0x10);
    private static final Color BTN_HOT = new Color(0x5A, 0x4C, 0x1C);
    private static final Color TAG_BG = new Color(0x00, 0x00, 0x00, 0xC0);
    private static final Color TAG_FG = new Color(0xFF, 0xFF, 0xFF);
    private static final Color SKY = new Color(0x88, 0xA8, 0xD0);
    private static final Color SEA = new Color(0x28, 0x50, 0x98);
    private static final Color CRATE = new Color(0x8C, 0x6C, 0x3C);
    private static final Color HOLD_BG = new Color(0x10, 0x10, 0x10);

    private final FreeColClient freeColClient;
    private final ImageLibrary lib;
    private final Europe europe;

    /** Run when the screen is dismissed (Escape / the exit button). */
    private final Runnable onClose;

    /**
     * Told of a ship that sailed for the New World ({@link #setSail}): the
     * last ship in port closes the screen by itself (ClassicGUI), or null.
     */
    private final java.util.function.Consumer<Unit> onSailed;

    /**
     * What the screen's boxes need from the game's GUI (gap list B1, N1):
     * the boxes over the screen ({@code @HOWMUCH4/5}, {@code @SAILAWAY},
     * {@code @EUROPESHIPCLICK}, {@code @EUROPEARM}), {@code @TUTORIAL18}.
     */
    interface Boxes {

        /**
         * Put a box over the Europe screen and wait for its answer
         * ({@code ClassicGUI.Prompter#ask}).
         *
         * @param r The box.
         * @return The answer; {@link ClassicAdvisorBox.Bar#DISMISSED} if
         *     it could not open.
         */
        int ask(ClassicAdvisorBox.Request r);

        /**
         * A plain buy the gold cannot pay ({@link ClassicTrade.Trader#cannotPay}).
         *
         * @param type The goods.
         */
        void cannotPay(GoodsType type);
    }

    /** The boxes over the screen, or null (then no partial trade, no questions). */
    private final Boxes boxes;

    /** The pack, its FONTTINY and its texts; null without the pack. */
    private final ClassicPackFiles pack;
    private final ClassicFont tiny;
    private final ClassicText text;

    /** ICONS.SS frames, loaded once each. */
    private final java.util.Map<Integer, Optional<BufferedImage>> icons = new HashMap<>();

    /** The stopgap's buttons: labels and actions, in order. */
    private final List<String> buttonLabels = new ArrayList<>();
    private final List<Runnable> buttonActions = new ArrayList<>();

    /** Index of the hovered button, or -1. */
    private int hovered = -1;

    /**
     * The selected ship in port (the original's green frame in box 3): its
     * holds are shown, the market's drops and «Segel setzen» go to it.
     * Kept on a ship in port by {@link #selectedShip}.
     */
    private Unit selectedShip;

    /** Ships moved to the front by «Nach vorne bewegen.», the last first (ids). */
    private final List<String> frontedShips = new ArrayList<>();

    /** The market cursor's slot (the original's 1-px frame; I: no blink). */
    private int marketCursor = 0;

    /** The dock cursor's colonist (the original's frame on the dock), or null: slot 0. */
    private Unit dockCursor;

    /** The press of the drag or click under way, or null. */
    private Hit pressed;

    /** Whether Shift was held at the press. */
    private boolean pressShift;

    /** Whether the pointer has left the pressed thing since the press: a drag. */
    private boolean dragging;

    /** The pointer during a press, on the canvas. */
    private int pointerX, pointerY;

    /** True while an action runs (its box, list or controller call). */
    private boolean acting;

    /** Device-space scale + origin of the virtual canvas. */
    private int scale = 1;
    private int originX;
    private int originY;


    ClassicEuropePanel(FreeColClient freeColClient, ImageLibrary lib,
                       Europe europe, Runnable onClose) {
        this(freeColClient, lib, europe, onClose, null, null);
    }

    /**
     * @param freeColClient The client.
     * @param lib The image library.
     * @param europe Our Europe.
     * @param onClose Run when the screen is dismissed.
     * @param onSailed Told of a ship that sailed for the New World, or
     *     null.
     * @param boxes The boxes over the screen (B1, N1), or null.
     */
    ClassicEuropePanel(FreeColClient freeColClient, ImageLibrary lib,
                       Europe europe, Runnable onClose,
                       java.util.function.Consumer<Unit> onSailed,
                       Boxes boxes) {
        this.freeColClient = freeColClient;
        this.lib = lib;
        this.europe = europe;
        this.onClose = onClose;
        this.onSailed = onSailed;
        this.boxes = boxes;
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        this.pack = pack;
        this.tiny = (pack == null) ? null : pack.font(ClassicFont.TINY);
        this.text = ClassicText.load(pack);
        this.buttonLabels.add(Messages.message("recruit"));
        this.buttonActions.add(this::recruit);
        this.buttonLabels.add(Messages.message("purchase"));
        this.buttonActions.add(this::purchase);
        this.buttonLabels.add(Messages.message("train"));
        this.buttonActions.add(this::train);
        this.buttonLabels.add(Messages.message("setSail"));
        this.buttonActions.add(this::setSail);
        setOpaque(true);
        setBackground(Color.BLACK);
        setPreferredSize(new Dimension(VW * 3, VH * 3));
        setFocusable(true);
        installKeyBindings();
        final MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e)) return;
                    pressAt(canvasX(e.getX()), canvasY(e.getY()), e.isShiftDown());
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    dragTo(canvasX(e.getX()), canvasY(e.getY()));
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e)) return;
                    releaseAt(canvasX(e.getX()), canvasY(e.getY()), e.isShiftDown());
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    onHover(canvasX(e.getX()), canvasY(e.getY()));
                }
            };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        selectedShip();
    }


    // Assets

    /** The original harbour backdrop, or null when the asset pack is absent. */
    private BufferedImage backdrop() {
        return (this.pack == null) ? null
            : this.pack.image("image.classic_original.pik.EUROPE.PIK");
    }

    /**
     * An ICONS.SS frame of the pack, cached; null without the pack.
     *
     * @param frame The frame.
     * @return The picture, or null.
     */
    private BufferedImage icon(int frame) {
        if (this.text == null) return null;
        return this.icons.computeIfAbsent(frame, n -> {
                try {
                    return Optional.ofNullable((this.pack == null) ? null : this.pack.image(
                        ClassicPackFiles.ssKey(String.format("ICONS.SS.%03d", n))));
                } catch (RuntimeException e) {
                    return Optional.<BufferedImage>empty();
                }
            }).orElse(null);
    }

    /** @return FreeCol's picture of a good, or null. */
    private BufferedImage goodsPicture(GoodsType gt) {
        if (this.lib == null || gt == null) return null;
        try {
            return this.lib.getScaledGoodsTypeImage(gt);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** @return A unit's map sprite, at most 16x16, or null. */
    private BufferedImage sprite(Unit u) {
        if (this.lib == null || u == null) return null;
        try {
            return ClassicHud.fit16(this.lib.getScaledUnitImage(u));
        } catch (RuntimeException e) {
            return null;
        }
    }


    // The model as the screen shows it

    /**
     * The ships in port, as box 3 shows them: those moved to the front
     * first, then Europe's order.
     *
     * @return The ships.
     */
    List<Unit> portShips() {
        final List<Unit> ships = new ArrayList<>();
        for (Unit u : this.europe.getUnitList()) {
            if (u.isNaval() && !u.isDisposed()) ships.add(u);
        }
        return ClassicColonyUnits.order(ships, this.frontedShips);
    }

    /**
     * The colonists on the dock as the docks show them: the newest at the
     * left (V clip008 §2.6: a recruit takes slot 0, the others move right).
     *
     * @return The colonists.
     */
    List<Unit> dockUnits() {
        final List<Unit> out = new ArrayList<>();
        for (Unit u : this.europe.getUnitList()) {
            if (!u.isNaval() && !u.isDisposed()) out.add(u);
        }
        Collections.reverse(out);
        return out;
    }

    /**
     * Our ships under way between Europe and the New World.
     *
     * @param toEurope True for those bound for Europe (box 1), false for
     *     those bound for the New World (box 2).
     * @return The ships.
     */
    List<Unit> sailing(boolean toEurope) {
        final List<Unit> out = new ArrayList<>();
        final Player p = (this.freeColClient == null) ? null
            : this.freeColClient.getMyPlayer();
        final HighSeas hs = (p == null) ? null : p.getHighSeas();
        if (hs == null) return out;
        for (Unit u : hs.getUnitList()) {
            if (!u.isNaval() || u.isDisposed()) continue;
            if ((u.getDestination() instanceof Europe) == toEurope) out.add(u);
        }
        return out;
    }

    /**
     * The selected ship: the one chosen while it is still in port, else
     * the first ship in port that can sail, else the first in port (V: the
     * original opens with the first ship selected).
     *
     * @return The ship, or null with none in port.
     */
    Unit selectedShip() {
        final List<Unit> ships = portShips();
        if (this.selectedShip != null && ships.contains(this.selectedShip)) {
            return this.selectedShip;
        }
        Unit pick = null;
        for (Unit u : ships) {
            if (!u.isDamaged()) {
                pick = u;
                break;
            }
        }
        if (pick == null && !ships.isEmpty()) pick = ships.get(0);
        this.selectedShip = pick;
        return pick;
    }

    /** One of the six holds as the screen draws it ({@link #holds}). */
    static final class Hold {

        /** A colonist (or other unit) aboard, in its first hold; or null. */
        final Unit unit;

        /** Whether this is a further hold of a unit taking more than one. */
        final boolean unitRest;

        /** A hold of goods (at most 100), or null. */
        final Goods goods;

        /** Whether the ship has no such hold (a crate). */
        final boolean crate;

        Hold(Unit unit, boolean unitRest, Goods goods, boolean crate) {
            this.unit = unit;
            this.unitRest = unitRest;
            this.goods = goods;
            this.crate = crate;
        }

        /** @return Whether the hold is empty and usable. */
        boolean empty() {
            return this.unit == null && this.goods == null && !this.crate;
        }

        @Override
        public String toString() {
            if (this.crate) return "crate";
            if (this.unit != null) {
                return (this.unitRest ? "+" : "") + this.unit.getType().getSuffix();
            }
            if (this.goods != null) {
                return this.goods.getType().getSuffix() + ":" + this.goods.getAmount();
            }
            return "empty";
        }
    }

    /**
     * The six holds of a ship as the original draws them (class comment):
     * the units aboard, each in as many holds as it takes, then the goods
     * in holds of at most 100 (FreeCol's order of the goods), then the
     * empty holds, then crates for the holds the ship does not have; six
     * crates for no ship.
     *
     * @param ship The ship, or null.
     * @return {@link #HOLDS} holds.
     */
    static List<Hold> holds(Unit ship) {
        final List<Hold> out = new ArrayList<>(HOLDS);
        if (ship != null) {
            for (Unit u : ship.getUnitList()) {
                final int n = Math.max(1, u.getSpaceTaken());
                for (int i = 0; i < n && out.size() < HOLDS; i++) {
                    out.add(new Hold(u, i > 0, null, false));
                }
            }
            for (Goods g : ship.getGoodsList()) {
                if (out.size() >= HOLDS) break;
                if (g.getAmount() > 0) out.add(new Hold(null, false, g, false));
            }
            final int cap = Math.min(HOLDS, ship.getCargoCapacity());
            while (out.size() < cap) out.add(new Hold(null, false, null, false));
        }
        while (out.size() < HOLDS) out.add(new Hold(null, false, null, true));
        return out;
    }


    // Geometry (pure: the painter, the mouse and the tests)

    /**
     * The step between cells in a row of {@code n} cells that must fit
     * {@code room} px between the first cell's and the last cell's left
     * edges: {@code pitch} at most, closer for more cells (I).
     */
    static int pitch(int n, int pitch, int room) {
        if (n <= 1) return pitch;
        return Math.max(1, Math.min(pitch, room / (n - 1)));
    }

    /**
     * @param i The ship's place in port.
     * @param n The ships in port.
     * @return Its 16 x 16 cell in box 3.
     */
    static Rectangle shipCell(int i, int n) {
        final int room = (BOX3.x + BOX3.width - 1) - 16 - SHIP_X;
        return new Rectangle(SHIP_X + i * pitch(n, SHIP_PITCH, room), SHIP_Y, 16, 16);
    }

    /**
     * @param k The hold, 0..5.
     * @return Its inside, 10 x 12.
     */
    static Rectangle holdCell(int k) {
        return new Rectangle(HOLD_X + k * HOLD_PITCH, HOLD_Y, HOLD_W, HOLD_H);
    }

    /**
     * @param k The hold, 0..5.
     * @return What a press or a drop on it takes: the hold with its frame
     *     (the strip x 145-220, y 163-176).
     */
    static Rectangle holdTarget(int k) {
        return new Rectangle(HOLD_X - 2 + k * HOLD_PITCH, HOLD_Y - 2, HOLD_PITCH, HOLD_H + 2);
    }

    /**
     * @param i The colonist's place on the dock (0: the newest).
     * @param n The colonists on the dock.
     * @return Its 16 x 16 cell: three per row, then the next row 23 px
     *     lower; more than six share two rows closer together (I).
     */
    static Rectangle dockCell(int i, int n) {
        final int perRow = Math.max(DOCK_PER_ROW, (n + 1) / 2);
        final int p = pitch(perRow, DOCK_PITCH, DOCK_LAST_X - DOCK_X);
        return new Rectangle(DOCK_X + (i % perRow) * p, DOCK_Y + (i / perRow) * DOCK_ROW,
                             16, 16);
    }

    /**
     * @param box {@link #BOX1} or {@link #BOX2}.
     * @param i The cell's place in the box (ships and their passengers).
     * @param n The cells in the box.
     * @return Its 16 x 16 cell.
     */
    static Rectangle sailingCell(Rectangle box, int i, int n) {
        final int x0 = box.x + 1;
        final int room = (box.x + box.width - 1) - 16 - x0;
        return new Rectangle(x0 + i * pitch(n, SAIL_PITCH, room), SAIL_Y, 16, 16);
    }

    /**
     * @param k The market slot.
     * @return Its place on the row.
     */
    static Rectangle marketSlot(int k) {
        return new Rectangle(k * MARKET_W, MARKET_Y, MARKET_W, MARKET_H);
    }

    /** @return Button {@code i}'s plate (the stopgap's). */
    static Rectangle buttonBounds(int i) {
        return new Rectangle(BTN_X, BTN_Y + i * (BTN_H + 2), BTN_W, BTN_H);
    }

    /** What a point of the screen is (a press's source, a release's target). */
    enum Kind { NONE, EXIT, BUTTON, SHIP, HOLD, DOCK, SAILING, MARKET, BOX1, BOX2, DOCKS }

    /** A thing on the screen under a point. */
    static final class Hit {

        static final Hit NONE = new Hit(Kind.NONE, -1, null, null, null, null);

        final Kind kind;
        final int index;
        final Rectangle r;
        final Unit unit;
        final Hold hold;
        final GoodsType type;

        Hit(Kind kind, int index, Rectangle r, Unit unit, Hold hold, GoodsType type) {
            this.kind = kind;
            this.index = index;
            this.r = r;
            this.unit = unit;
            this.hold = hold;
            this.type = type;
        }

        /** @return Whether the point lies on it. */
        boolean contains(int x, int y) {
            return this.r != null && this.r.contains(x, y);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder(this.kind.toString().toLowerCase());
            if (this.index >= 0) sb.append(this.index);
            if (this.unit != null) sb.append('(').append(this.unit.getId()).append(')');
            if (this.hold != null) sb.append('[').append(this.hold).append(']');
            if (this.type != null) sb.append('[').append(this.type.getSuffix()).append(']');
            return sb.toString();
        }
    }

    /**
     * What lies at a point of the canvas: the exit, a button, a ship in
     * port, a hold of the selected ship, a colonist on the dock, a ship
     * under way, a market slot, else box 2, box 1 or the docks.
     *
     * @param vx Canvas x.
     * @param vy Canvas y.
     * @return The thing, {@link Hit#NONE} for nothing.
     */
    Hit hitAt(int vx, int vy) {
        if (EXIT.contains(vx, vy)) return new Hit(Kind.EXIT, -1, EXIT, null, null, null);
        for (int i = 0; i < this.buttonActions.size(); i++) {
            final Rectangle r = buttonBounds(i);
            if (r.contains(vx, vy)) return new Hit(Kind.BUTTON, i, r, null, null, null);
        }
        final List<Unit> ships = portShips();
        for (int i = ships.size() - 1; i >= 0; i--) {     // the topmost first
            final Rectangle r = shipCell(i, ships.size());
            if (r.contains(vx, vy)) return new Hit(Kind.SHIP, i, r, ships.get(i), null, null);
        }
        final Unit ship = selectedShip();
        final List<Hold> holds = holds(ship);
        for (int k = 0; k < HOLDS; k++) {
            final Rectangle r = holdTarget(k);
            if (r.contains(vx, vy)) {
                return new Hit(Kind.HOLD, k, r, ship, holds.get(k), null);
            }
        }
        final List<Unit> dock = dockUnits();
        for (int i = dock.size() - 1; i >= 0; i--) {
            final Rectangle r = dockCell(i, dock.size());
            if (r.contains(vx, vy)) return new Hit(Kind.DOCK, i, r, dock.get(i), null, null);
        }
        for (boolean toEurope : new boolean[] { true, false }) {
            final List<Unit> cells = sailingCells(toEurope);
            final Rectangle box = (toEurope) ? BOX1 : BOX2;
            for (int i = cells.size() - 1; i >= 0; i--) {
                final Rectangle r = sailingCell(box, i, cells.size());
                final Unit u = cells.get(i);
                if (r.contains(vx, vy) && u.isNaval()) {
                    return new Hit(Kind.SAILING, i, r, u, null, null);
                }
            }
        }
        final List<GoodsType> goods = marketGoods();
        for (int k = 0; k < goods.size(); k++) {
            final Rectangle r = marketSlot(k);
            if (r.contains(vx, vy)) return new Hit(Kind.MARKET, k, r, null, null, goods.get(k));
        }
        if (BOX2.contains(vx, vy)) return new Hit(Kind.BOX2, -1, BOX2, null, null, null);
        if (BOX1.contains(vx, vy)) return new Hit(Kind.BOX1, -1, BOX1, null, null, null);
        if (DOCKS.contains(vx, vy) && vy < MARKET_Y) {
            return new Hit(Kind.DOCKS, -1, DOCKS, null, null, null);
        }
        return Hit.NONE;
    }

    /** @return The cells of a box of ships under way: each ship, then its passengers. */
    private List<Unit> sailingCells(boolean toEurope) {
        final List<Unit> out = new ArrayList<>();
        for (Unit s : sailing(toEurope)) {
            out.add(s);
            out.addAll(s.getUnitList());
        }
        return out;
    }

    /** @return The market's goods, in the original's order (FreeCol's storable goods). */
    private List<GoodsType> marketGoods() {
        final Specification spec = this.europe.getSpecification();
        return (spec == null) ? Collections.<GoodsType>emptyList()
            : spec.getStorableGoodsTypeList();
    }


    // Input

    /** Canvas x of a device x (the scale of the window as it is now). */
    private int canvasX(int x) {
        updateTransform();
        return Math.floorDiv(x - this.originX, this.scale);
    }

    /** Canvas y of a device y. */
    private int canvasY(int y) {
        updateTransform();
        return Math.floorDiv(y - this.originY, this.scale);
    }

    /** The largest integer scale that fits the window, and the letterbox. */
    private void updateTransform() {
        final int w = getWidth(), h = getHeight();
        this.scale = Math.max(1, Math.min(w / VW, h / VH));
        this.originX = (w - VW * this.scale) / 2;
        this.originY = (h - VH * this.scale) / 2;
    }

    private void installKeyBindings() {
        final InputMap im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        final ActionMap am = getActionMap();
        bind(im, am, "ESCAPE", "classic_closeEurope", this::close);
        bind(im, am, "E", "classic_closeEuropeE", this::close);
        // The original's hotkeys: the yellow first letters of REKRUT.,
        // KAUFEN, AUSBILDEN (V clip008 §2.3), and 1-3 (manual).
        final String[][] keys = { { "R", "1" }, { "K", "2" }, { "A", "3" } };
        for (int i = 0; i < keys.length; i++) {
            final int b = i;
            for (String k : keys[i]) {
                bind(im, am, k, "classic_europeButton" + k, () -> button(b));
            }
        }
        bind(im, am, "ENTER", "classic_europeShip", () -> {
                final Unit s = selectedShip();
                if (s != null) act(() -> shipOptions(s));
            });
        bind(im, am, "U", "classic_europeUnload", () -> act(this::sellFirstHold));
    }

    private void bind(InputMap im, ActionMap am, String key, String name, Runnable r) {
        im.put(KeyStroke.getKeyStroke(key), name);
        am.put(name, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (!acting) r.run();
                }
            });
    }

    /** Run button {@code i}'s action (a key or a click). */
    private void button(int i) {
        if (i < 0 || i >= this.buttonActions.size()) return;
        act(this.buttonActions.get(i));
    }

    /**
     * Run an action once at a time: its boxes and lists wait in loops of
     * their own, during which the screen takes no other action.  The
     * screen is brought up to date and has the keys again after it.
     */
    private void act(Runnable r) {
        if (this.acting) return;
        this.acting = true;
        try {
            r.run();
        } finally {
            this.acting = false;
            refresh();
            if (isShowing()) requestFocusInWindow();
        }
    }

    /**
     * A press at a point of the canvas: what is there becomes the source
     * of a drag or a click (the mouse's, the harness's).
     *
     * @param vx Canvas x.
     * @param vy Canvas y.
     * @param shift Whether Shift is held.
     */
    void pressAt(int vx, int vy, boolean shift) {
        if (this.acting) return;
        this.pressed = hitAt(vx, vy);
        this.pressShift = shift;
        this.dragging = false;
        this.pointerX = vx;
        this.pointerY = vy;
        if (this.pressed.kind == Kind.MARKET) this.marketCursor = this.pressed.index;
        if (this.pressed.kind == Kind.DOCK) this.dockCursor = this.pressed.unit;
        repaint();
    }

    /**
     * The pointer moved with the button down: once it leaves what was
     * pressed, a thing that can be dragged is dragged.
     *
     * @param vx Canvas x.
     * @param vy Canvas y.
     */
    void dragTo(int vx, int vy) {
        final Hit p = this.pressed;
        if (p == null) return;
        this.pointerX = vx;
        this.pointerY = vy;
        if (!this.dragging && !p.contains(vx, vy) && draggable(p)) this.dragging = true;
        repaint();
    }

    /** @return Whether a pressed thing can be dragged. */
    private static boolean draggable(Hit h) {
        switch (h.kind) {
        case SHIP: case DOCK: case MARKET:
            return true;
        case SAILING:
            return h.unit != null && h.unit.getDestination() instanceof Europe;
        case HOLD:
            return h.hold != null && (h.hold.unit != null || h.hold.goods != null);
        default:
            return false;
        }
    }

    /**
     * The button goes up: a click if the pointer never left what was
     * pressed and is still on it; the drop of a drag; else nothing.
     *
     * @param vx Canvas x.
     * @param vy Canvas y.
     * @param shift Whether Shift is held now.
     */
    void releaseAt(int vx, int vy, boolean shift) {
        final Hit p = this.pressed;
        final boolean dragged = this.dragging;
        final boolean sh = shift || this.pressShift;
        this.pressed = null;
        this.dragging = false;
        repaint();
        if (p == null || this.acting) return;
        if (!dragged) {
            if (p.contains(vx, vy)) act(() -> click(p, sh));
            return;
        }
        final Hit at = hitAt(vx, vy);
        act(() -> drop(p, at, BOX2.contains(vx, vy), sh));
    }

    /**
     * A click at a point of the 320&times;200 canvas, no Shift.
     *
     * @param vx Canvas x.
     * @param vy Canvas y.
     */
    void clickAt(int vx, int vy) {
        clickAt(vx, vy, false);
    }

    /**
     * A click at a point of the canvas: a press and a release there (the
     * scripted harness's {@code sclick}).
     *
     * @param vx Canvas x.
     * @param vy Canvas y.
     * @param shift Whether Shift was held.
     */
    void clickAt(int vx, int vy, boolean shift) {
        pressAt(vx, vy, shift);
        releaseAt(vx, vy, shift);
    }

    /**
     * A drag from one point of the canvas to another (tests, the harness).
     *
     * @param ax Press x.
     * @param ay Press y.
     * @param bx Release x.
     * @param by Release y.
     * @param shift Whether Shift was held.
     */
    void dragAt(int ax, int ay, int bx, int by, boolean shift) {
        pressAt(ax, ay, shift);
        dragTo((ax + bx) / 2, (ay + by) / 2);
        dragTo(bx, by);
        releaseAt(bx, by, shift);
    }

    /** A click on a thing (class comment). */
    private void click(Hit h, boolean shift) {
        switch (h.kind) {
        case EXIT:
            close();
            break;
        case BUTTON:
            this.buttonActions.get(h.index).run();
            break;
        case SHIP:
            if (h.unit != selectedShip()) {
                this.selectedShip = h.unit;
                ClassicFrameRecorder.event("europe-select", h.unit.getId());
            } else {
                shipOptions(h.unit);
            }
            break;
        case DOCK:
            armOptions(h.unit);
            break;
        case MARKET:
            this.marketCursor = h.index;
            break;
        default:
            break;
        }
    }

    /** A drop of a dragged thing on another (class comment). */
    private void drop(Hit src, Hit dst, boolean inBox2, boolean shift) {
        String result = "nothing";
        switch (src.kind) {
        case SHIP:
            if (inBox2) result = askSail(src.unit);
            break;
        case SAILING:
            if (inBox2 && src.unit.getDestination() instanceof Europe) {
                final boolean ok = igc().moveTo(src.unit, this.freeColClient.getGame().getMap());
                result = (ok) ? "turned to the New World" : "refused";
            }
            break;
        case MARKET: {
            final Unit ship = (dst.kind == Kind.SHIP) ? dst.unit
                : (dst.kind == Kind.HOLD) ? dst.unit : null;
            if (ship != null) {
                this.selectedShip = ship;
                result = "bought " + loadMarketGood(src.type, ship, shift);
            }
            break;
        }
        case HOLD:
            if (src.hold.goods != null && dst.kind == Kind.MARKET) {
                result = "sold " + sellCargo(src.hold.goods, shift);
            } else if (src.hold.unit != null
                       && (dst.kind == Kind.DOCKS || dst.kind == Kind.DOCK)) {
                result = (igc().leaveShip(src.hold.unit)) ? "on the dock" : "refused";
                ClassicFrameRecorder.event("europe-unload", src.hold.unit.getId()
                    + " " + result + " state=" + src.hold.unit.getState());
            }
            break;
        case DOCK: {
            final Unit ship = (dst.kind == Kind.SHIP) ? dst.unit
                : (dst.kind == Kind.HOLD && dst.unit != null) ? dst.unit : null;
            if (ship != null) {
                result = (igc().boardShip(src.unit, ship)) ? "aboard" : "refused";
                if (src.unit.getLocation() == ship) this.selectedShip = ship;
                ClassicFrameRecorder.event("europe-board", src.unit.getId() + " -> "
                    + ship.getId() + " " + result + " state=" + src.unit.getState());
            }
            break;
        }
        default:
            break;
        }
        ClassicFrameRecorder.event("europe-drag", src + " -> " + dst
            + ((shift) ? " shift" : "") + ": " + result);
    }

    /** Track which button the pointer is over, and repaint if it changed. */
    private void onHover(int vx, int vy) {
        int found = -1;
        for (int i = 0; i < this.buttonActions.size(); i++) {
            if (buttonBounds(i).contains(vx, vy)) {
                found = i;
                break;
            }
        }
        if (found != this.hovered) {
            this.hovered = found;
            repaint();
        }
    }

    private void close() {
        if (this.onClose != null) this.onClose.run();
    }

    private InGameController igc() {
        return this.freeColClient.getInGameController();
    }


    // Actions

    /**
     * A ship in port dropped on "Ziel:": {@code @SAILAWAY}, and on
     * «Jawohl» it sails ({@link #setSail(Unit)}); without the box (no
     * pack, no screen boxes) it sails at once.
     *
     * @param ship The ship.
     * @return What happened, for the recorder.
     */
    private String askSail(Unit ship) {
        final ClassicAdvisorBox.Request r = (this.boxes == null) ? null
            : ClassicEuropeOptions.sailRequest(this.text, title());
        if (r != null) {
            final int a = this.boxes.ask(r);
            ClassicFrameRecorder.event("europe-sail-ask", ship.getId() + " chosen=" + a);
            if (!ClassicEuropeOptions.sails(a)) return "stays (" + a + ")";
        }
        return (setSail(ship)) ? "sailed" : "did not sail";
    }

    /**
     * A click on the selected ship: {@code @EUROPESHIPCLICK}
     * ({@link ClassicEuropeOptions}).
     *
     * @param ship The ship.
     */
    private void shipOptions(Unit ship) {
        final ClassicAdvisorBox.Request r = (this.boxes == null) ? null
            : ClassicEuropeOptions.shipRequest(this.text, ship, unitIcon(ship), title());
        if (r == null) {
            ClassicFrameRecorder.event("europe-ship-options", ship.getId() + " no box");
            return;
        }
        final int a = this.boxes.ask(r);
        final ClassicEuropeOptions.ShipOption o = ClassicEuropeOptions.chosenShip(a);
        String result = "";
        switch (o) {
        case FRONT:
            ClassicColonyUnits.front(this.frontedShips, ship);
            this.selectedShip = ship;
            break;
        case SAIL:
            result = (setSail(ship)) ? " sailed" : " did not sail";
            break;
        case UNLOAD:
            int sold = 0;
            for (Goods g : ship.getCompactGoodsList()) sold += sellCargo(g, false);
            result = " sold " + sold;
            break;
        default:
            break;
        }
        ClassicFrameRecorder.event("europe-ship-options", ship.getId() + " chosen=" + a
            + " " + o + result);
    }

    /**
     * A click on a colonist on the dock: {@code @EUROPEARM}
     * ({@link ClassicEuropeOptions}): "S" on or off, equipment bought or
     * sold through FreeCol's {@code equipUnitForRole}.
     *
     * @param unit The colonist.
     */
    private void armOptions(Unit unit) {
        final List<ClassicEuropeOptions.ArmOption> shown
            = ClassicEuropeOptions.armOptions(unit);
        final Player me = this.freeColClient.getMyPlayer();
        final ClassicAdvisorBox.Request r = (this.boxes == null) ? null
            : ClassicEuropeOptions.armRequest(this.text, shown,
                ClassicEuropeOptions.armPrices(unit, shown,
                    (me == null) ? null : me.getMarket()),
                unitIcon(unit), title());
        if (r == null) {
            ClassicFrameRecorder.event("europe-arm", unit.getId() + " no box");
            return;
        }
        final int a = this.boxes.ask(r);
        final ClassicEuropeOptions.ArmOption o = ClassicEuropeOptions.chosenArm(shown, a);
        boolean done = true;
        switch (o) {
        case STAY:
            done = igc().changeState(unit, Unit.UnitState.ACTIVE);
            break;
        case BOARD:
            done = igc().changeState(unit, Unit.UnitState.SENTRY);
            break;
        case NOTHING:
            break;
        default: {
            final Role role = ClassicEuropeOptions.target(unit, o);
            done = role != null && igc().equipUnitForRole(unit, role,
                ClassicEuropeOptions.roleCount(role));
            break;
        }
        }
        ClassicFrameRecorder.event("europe-arm", unit.getId() + " rows=" + shown
            + " chosen=" + a + " " + o + ((done) ? "" : " refused")
            + " role=" + unit.getRole().getSuffix() + " state=" + unit.getState()
            + " gold=" + gold());
    }

    /** U: the selected ship's first hold of goods is sold (manual). */
    private void sellFirstHold() {
        for (Hold h : holds(selectedShip())) {
            if (h.goods != null) {
                sellCargo(h.goods, false);
                return;
            }
        }
    }

    /**
     * A unit's icon in its box ({@link ClassicAdvisorBox.UnitIcon}): its
     * map sprite, its owner's flag and its flag letter.
     */
    private ClassicAdvisorBox.UnitIcon unitIcon(Unit u) {
        return new ClassicAdvisorBox.UnitIcon(sprite(u), ClassicHud.nationRgb(u.getOwner()),
            ClassicHud.orderLetter(this.text, ClassicUnitCycle.ordersRowShown(u)),
            ClassicHud.unitRow(u));
    }

    /** @return The stopgap boxes' window title: the port's name. */
    private String title() {
        return msg(this.europe.getNameKey());
    }

    /**
     * Sell goods off their ship via {@link InGameController#unloadCargo}
     * — which, since the ship is in Europe, routes to {@code sellGoods}
     * (the call {@code GoodsLabel}/{@code MarketPanel} make when a cargo
     * icon is dragged off a carrier in the standard UI).  With Shift,
     * {@code @HOWMUCH5} asks how much ({@link ClassicTrade#sell}).
     *
     * @return The amount sold.
     */
    private int sellCargo(Goods goods, boolean shift) {
        final int n = ClassicTrade.sell(this.freeColClient.getMyPlayer(), goods,
                                        shift, trader(goods.getLocation() instanceof Unit
                                                      ? (Unit)goods.getLocation() : null));
        ClassicFrameRecorder.event("europe-sell", goods.getType().getSuffix()
            + " shift=" + shift + " sold=" + n + " gold=" + gold());
        return n;
    }

    /** @return Our gold (the recorder's trade events). */
    private int gold() {
        final Player me = this.freeColClient.getMyPlayer();
        return (me == null) ? 0 : me.getGold();
    }

    /**
     * The trades' boxes and controller calls ({@link ClassicTrade.Trader}).
     *
     * @param ship The ship traded with (the box's "auf Handelsschiff").
     */
    private ClassicTrade.Trader trader(Unit ship) {
        return new ClassicTrade.Trader() {
            @Override
            public int ask(boolean buying, GoodsType type, int max, int preset) {
                return askAmount(buying, type, ship, max, preset);
            }

            @Override
            public boolean buy(GoodsType type, int amount, Unit carrier) {
                return igc().buyGoods(type, amount, carrier);
            }

            @Override
            public boolean sell(Goods goods) {
                return igc().unloadCargo(goods, false);
            }

            @Override
            public void cannotPay(GoodsType type) {
                if (boxes != null) boxes.cannotPay(type);
            }
        };
    }

    /**
     * A {@code @HOWMUCH} box over the screen ({@link ClassicTrade#howMuchRequest}).
     *
     * @param buying True for {@code @HOWMUCH4}, false for {@code @HOWMUCH5}.
     * @param type The goods.
     * @param ship The ship.
     * @param max The "(0-max)".
     * @param preset The field's preset.
     * @return The amount Enter took, or -1 (Escape, no box, no pack).
     */
    private int askAmount(boolean buying, GoodsType type, Unit ship, int max, int preset) {
        final Player me = this.freeColClient.getMyPlayer();
        final Market market = (me == null) ? null : me.getMarket();
        if (this.boxes == null || market == null) return -1;
        final java.util.Map<String, String> values = (buying)
            ? ClassicTrade.buyValues(this.text, type, market.getCostToBuy(type), ship, max)
            : ClassicTrade.sellValues(this.text, type, market.getPaidForSale(type), me, max);
        final ClassicAdvisorBox.Request r
            = ClassicTrade.howMuchRequest(this.text, buying, values, max, preset);
        if (r == null) return -1;
        this.boxes.ask(r);
        return r.field.amount();
    }

    /**
     * Buy and load a hold of {@code type} onto {@code ship} via {@link
     * InGameController#buyGoods} (the call {@code MarketLabel} makes when
     * dragged onto the cargo panel in the standard UI; {@code loadCargo}'s
     * Europe branch builds goods located in Europe, which FreeCol
     * rejects).  At most one hold per drop, as the original's; one the
     * gold cannot pay buys nothing and brings {@code @TUTORIAL18}; with
     * Shift, {@code @HOWMUCH4} asks how much ({@link ClassicTrade#buy}).
     *
     * @return The amount bought.
     */
    private int loadMarketGood(GoodsType type, Unit ship, boolean shift) {
        final int n = ClassicTrade.buy(this.freeColClient.getMyPlayer(), ship, type,
                                       shift, trader(ship));
        ClassicFrameRecorder.event("europe-buy", type.getSuffix() + " shift=" + shift
            + " bought=" + n + " gold=" + gold());
        return n;
    }

    /** «Segel setzen» (the stopgap's button): the selected ship sails. */
    private void setSail() {
        setSail(selectedShip());
    }

    /**
     * A ship sails for the New World ({@link #sail}).  The last ship
     * closes the screen by itself afterwards, colonists on the dock or not
     * (Roger, 2026-10-08); the next ship in port is selected.
     *
     * @param ship The ship in port.
     * @return True if it sailed.
     */
    private boolean setSail(Unit ship) {
        if (ship == null || !ship.isNaval() || !ship.isInEurope()) return false;
        final Map map = this.freeColClient.getGame().getMap();
        sail(igc()::boardShip, igc()::moveTo, this.europe, ship, map);
        // Sailed (the server took it out of port): the last ship closes the
        // screen by itself, colonists on the dock or not (Roger, 2026-10-08).
        final boolean sailed = !ship.isInEurope();
        if (sailed) {
            this.frontedShips.remove(ship.getId());
            if (this.selectedShip == ship) this.selectedShip = null;
            selectedShip();
            if (this.onSailed != null) this.onSailed.accept(ship);
        }
        return sailed;
    }

    /**
     * Set sail for the New World as the original does (clip opening_015
     * #1683 -&gt; #1686, V): first the colonists on the dock marked "S"
     * (FreeCol's SENTRY, which every land unit gets on the dock) board the
     * ship by themselves, as many as fit, in the dock's order
     * ({@link #boarders}; {@link InGameController#boardShip}, FreeCol's
     * {@code moveAutoload} with {@code Unit.sentryPred}); a colonist marked
     * "-" stays.  Then the ship sails ({@link InGameController#moveTo}, the
     * standard screen's own Set Sail seam).  No question about colonists
     * left behind: FreeCol's "... und die Kolonisten zurücklassen?" box
     * ({@code europePanel.leaveColonists}) is gone, its Enter left the
     * colonist behind unseen (the review of part J).
     *
     * @param board The controller's boarding ({@link InGameController#boardShip}).
     * @param move The controller's move ({@link InGameController#moveTo}).
     * @param europe Our Europe.
     * @param ship The ship in port.
     * @param map The map, the destination.
     * @return True if the ship sailed.
     */
    static boolean sail(java.util.function.BiPredicate<Unit, Unit> board,
                        java.util.function.BiPredicate<Unit, Location> move,
                        Europe europe, Unit ship, Map map) {
        for (Unit u : boarders(europe, ship)) board.test(u, ship);
        return move.test(ship, map);
    }

    /**
     * The colonists on the dock who board a ship when it sails
     * ({@link #sail}): the land units marked "S" (SENTRY) that fit into its
     * space left, in the dock's order; one that does not fit stays, and so
     * does every other one.
     *
     * @param europe Our Europe, or null.
     * @param ship The ship, or null.
     * @return The colonists, in boarding order.
     */
    static List<Unit> boarders(Europe europe, Unit ship) {
        final List<Unit> out = new ArrayList<>();
        if (europe == null || ship == null || !ship.canCarryUnits()) return out;
        int space = ship.getSpaceLeft();
        for (Unit u : europe.getUnitList()) {
            if (!Unit.sentryPred.test(u) || u.isDisposed()) continue;
            final int need = u.getSpaceTaken();
            if (need > space) continue;
            out.add(u);
            space -= need;
        }
        return out;
    }

    /** @return The Europe this screen shows. */
    Europe europe() {
        return this.europe;
    }


    // Recruit / purchase / train — the real controller paths.

    /**
     * The recruit dialog: offer the three migrants waiting on the docks in
     * Europe, each for the current passage price, and recruit the chosen one via
     * {@link InGameController#recruitUnitInEurope(int)} (the same call the
     * standard {@code RecruitPanel} makes).
     */
    private void recruit() {
        final Player player = this.freeColClient.getMyPlayer();
        final List<AbstractUnit> recruitables = this.europe.getExpandedRecruitables(false);
        if (recruitables.isEmpty()) return;
        final int price = player.getEuropeanRecruitPrice();
        final String[] options = new String[recruitables.size()];
        for (int i = 0; i < options.length; i++) {
            options[i] = Messages.message(recruitables.get(i).getSingleLabel())
                + "  (" + price + ")";
        }
        final String prompt = Messages.message(StringTemplate
            .template("recruitPanel.clickOn")
            .addAmount("%money%", price)
            .addAmount("%number%", 0));
        final int idx = choose(Messages.message("recruit"), prompt, options);
        if (idx >= 0 && Europe.MigrationType.validMigrantIndex(idx)) {
            final boolean ok = igc().recruitUnitInEurope(idx);
            ClassicFrameRecorder.event("europe-recruit", "row=" + idx
                + ((ok) ? "" : " refused") + " gold=" + gold());
        }
    }

    /**
     * The train dialog: the unit types Europe can school, cheapest first, paid
     * for through {@link InGameController#trainUnitInEurope(UnitType)}.
     */
    private void train() {
        final Specification spec = this.freeColClient.getGame().getSpecification();
        offerUnits(Messages.message("train"),
                   Messages.message("trainPanel.clickOn"),
                   spec.getUnitTypesTrainedInEurope(this.freeColClient.getMyPlayer()));
    }

    /**
     * The purchase dialog: the artillery and ships Europe sells.  These are paid
     * for through the same {@code trainUnitInEurope} controller call as trained
     * units (as the standard {@code PurchasePanel} does).
     */
    private void purchase() {
        final Specification spec = this.freeColClient.getGame().getSpecification();
        offerUnits(Messages.message("purchase"),
                   Messages.message("purchasePanel.clickOn"),
                   spec.getUnitTypesPurchasedInEurope(this.freeColClient.getMyPlayer()));
    }

    /** Shared body of {@link #train} / {@link #purchase}: a priced unit-type list. */
    private void offerUnits(String title, String prompt, List<UnitType> types) {
        if (types == null || types.isEmpty()) return;
        final List<UnitType> sorted = new ArrayList<>(types);
        sorted.sort(Comparator.comparingInt(this.europe::getUnitPrice));
        final String[] options = new String[sorted.size()];
        for (int i = 0; i < options.length; i++) {
            final UnitType ut = sorted.get(i);
            options[i] = Messages.getName(ut) + "  (" + this.europe.getUnitPrice(ut) + ")";
        }
        final int idx = choose(title, prompt, options);
        if (idx >= 0) {
            final boolean ok = igc().trainUnitInEurope(sorted.get(idx));
            ClassicFrameRecorder.event("europe-train", sorted.get(idx).getSuffix()
                + ((ok) ? "" : " refused") + " gold=" + gold());
        }
    }

    /**
     * Show a plain Swing selection list and return the chosen index, or -1.  The
     * classic wood-framed dialog is Phase 3 (shared with the colony-founding
     * seams); this is the stopgap that puts the real choice in front of the
     * player meanwhile.  It goes through {@code ClassicGUI.chooseFromList},
     * not {@code JOptionPane.showInputDialog}, so that in full screen the list
     * is undecorated like every other classic window instead of a Windows
     * dialog with a title bar over the borderless Europe screen.
     */
    private int choose(String title, String prompt, String[] options) {
        if (this.chooser != null) return this.chooser.choose(title, prompt, options);
        final Object sel = ClassicGUI.chooseFromList(
            SwingUtilities.getWindowAncestor(this), title, prompt, null,
            options);
        if (sel == null) return -1;
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(sel)) return i;
        }
        return -1;
    }

    /** The lists' choice in a test without a screen ({@link #choose}). */
    interface Chooser {

        /**
         * @param title The list's title.
         * @param prompt Its prompt.
         * @param options Its rows.
         * @return The row chosen, or -1.
         */
        int choose(String title, String prompt, String[] options);
    }

    /** The tests' lists, or null: the Swing list. */
    Chooser chooser = null;

    /** Repaint after a model change (a recruit, a purchase, an arrival). */
    void refresh() {
        selectedShip();
        repaint();
    }

    /**
     * The scripted harness: the screen painted into an image (a minimized
     * run's window never paints).  EDT only.
     */
    void paintTargets() {
        final BufferedImage img = new BufferedImage(Math.max(VW, getWidth()),
            Math.max(VH, getHeight()), BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g = img.createGraphics();
        try {
            paintComponent(g);
        } finally {
            g.dispose();
        }
    }

    /**
     * The canvas as it is now, 320 x 200 (tests).
     *
     * @return The picture.
     */
    BufferedImage canvas() {
        final BufferedImage img = new BufferedImage(VW, VH, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g = img.createGraphics();
        try {
            paintCanvas(g);
        } finally {
            g.dispose();
        }
        return img;
    }

    /** @return Whether a drag is under way (tests). */
    boolean dragging() {
        return this.dragging;
    }

    /** @return The market cursor's slot (tests). */
    int marketCursor() {
        return this.marketCursor;
    }


    // Painting

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        final Graphics2D g = (Graphics2D) g0.create();
        updateTransform();
        g.translate(this.originX, this.originY);
        g.scale(this.scale, this.scale);
        g.clipRect(0, 0, VW, VH);
        paintCanvas(g);
        g.dispose();
    }

    /** Everything, on the 320 x 200 canvas. */
    private void paintCanvas(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                           RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                           RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        paintBackground(g);
        paintTitle(g);
        paintCaptions(g);
        paintSailing(g);
        paintPort(g);
        paintHolds(g);
        paintDocks(g);
        paintMarket(g);
        paintButtons(g);
        paintDragged(g);
    }

    /** The harbour backdrop ({@code EUROPE.PIK}), or a plain sea/sky fallback. */
    private void paintBackground(Graphics2D g) {
        final BufferedImage bg = backdrop();
        if (bg != null) {
            g.drawImage(bg, 0, 0, VW, VH, null);
        } else {
            g.setColor(SKY);
            g.fillRect(0, 0, VW, 120);
            g.setColor(SEA);
            g.fillRect(0, 120, VW, VH - 120);
            g.setColor(SKY.brighter());
            for (Rectangle r : new Rectangle[] { BOX1, BOX2, BOX3 }) {
                g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
            }
            g.setColor(HOLD_BG);
            g.fillRect(HOLD_X - 2, HOLD_Y - 2, HOLDS * HOLD_PITCH, HOLD_H + 4);
        }
    }

    /** The gold-on-black header: port name, turn, tax, treasury. */
    private void paintTitle(Graphics2D g) {
        g.setColor(TITLE_BG);
        g.fillRect(0, 0, VW, TITLE_H);
        final Game game = this.freeColClient.getGame();
        final Player player = this.freeColClient.getMyPlayer();
        final StringBuilder sb = new StringBuilder(msg(this.europe.getNameKey()));
        if (game != null && game.getTurn() != null) {
            sb.append(", ").append(msg(game.getTurn().getLabel()));
        }
        if (player != null) {
            sb.append(", ").append(Messages.message("tax")).append(": ")
                .append(player.getTax()).append("%   ")
                .append(Messages.message("gold")).append(": ")
                .append(player.getGold());
        }
        g.setColor(GOLD);
        g.setFont(font(7f, Font.BOLD));
        g.drawString(sb.toString(), 3, 7);
    }

    /**
     * The three boxes' captions (V clip008 §2.4): LABELS.TXT {@code @MISC}
     * «Bald erwartet», «Ziel:» over the New World's name, «Einladen:» over
     * the selected ship's type or «Keine Schiffe im Hafen», centred in the
     * box.  Without the pack: FreeCol's words in the stopgap's plates.
     */
    private void paintCaptions(Graphics2D g) {
        final Unit ship = selectedShip();
        final Player me = this.freeColClient.getMyPlayer();
        final String[][] lines = captions(this.text, me, ship);
        final Rectangle[] boxes = { BOX1, BOX2, BOX3 };
        for (int b = 0; b < boxes.length; b++) {
            for (int l = 0; l < lines[b].length; l++) {
                final String s = lines[b][l];
                if (s == null || s.isEmpty()) continue;
                final int y = (l == 0) ? CAPTION_Y1 : CAPTION_Y2;
                if (this.tiny != null) {
                    this.tiny.draw(g, s, captionX(boxes[b], this.tiny.stringWidth(s)), y,
                                   ClassicFont.colours(CAPTION_RGB));
                } else {
                    g.setFont(font(6f, Font.BOLD));
                    final int w = g.getFontMetrics().stringWidth(s);
                    plate(g, s, boxes[b].x + (boxes[b].width - w) / 2, y + 6);
                }
            }
        }
    }

    /**
     * Where a caption starts: centred in its box, the odd pixel to the
     * right, never left of the box (clip008 §2.4, FONTTINY advances:
     * «Bald erwartet» (49) at x 12 in box 1, «Ziel:» (18) at 99 and
     * «Neuholland» (38) at 89 in box 2, «Handelsschiff» (50) at 159 and
     * «Keine Schiffe im Hafen» (81, the box's width) at 143 in box 3;
     * «Einladen:» (34) comes at 167, the clip has 166: no one rule found
     * gives all six).
     *
     * @param box The box.
     * @param w The caption's advance.
     * @return Its left edge.
     */
    static int captionX(Rectangle box, int w) {
        return box.x + Math.max(0, Math.min(box.width - w, (box.width - w + 2) / 2));
    }

    /**
     * The captions of the three boxes, two lines each (class comment).
     *
     * @param t The pack's texts, or null (then FreeCol's words).
     * @param me Our player, or null.
     * @param ship The selected ship in port, or null.
     * @return {box 1, box 2, box 3} x {line 1, line 2}.
     */
    static String[][] captions(ClassicText t, Player me, Unit ship) {
        final String[][] out = new String[3][2];
        final String soon = (t == null) ? null : t.misc(MISC_SOON);
        final String dest = (t == null) ? null : t.misc(MISC_DEST);
        final String none = (t == null) ? null : t.misc(MISC_NO_SHIPS);
        final String load = (t == null) ? null : t.misc(MISC_LOAD);
        out[0][0] = (soon != null) ? soon.trim() : Messages.message("sailingToEurope");
        if (dest != null) {
            out[1][0] = dest.trim();
            final java.util.Map<String, String> v = ClassicBands.tutorialValues(t, me);
            out[1][1] = v.get("STRING2");
        } else {
            out[1][0] = Messages.message("sailingToAmerica");
        }
        if (ship == null) {
            out[2][0] = (none != null) ? none.trim() : Messages.message("colonyPanel.inPort");
        } else {
            out[2][0] = (load != null) ? load.trim() + ":" : Messages.getName(ship.getType());
            if (load != null) {
                String n = ClassicTips.unitName(t, ship);
                if (n == null) n = Messages.getName(ship.getType());
                out[2][1] = n;
            }
        }
        return out;
    }

    /** LABELS.TXT {@code @MISC}: «Bald erwartet», «Ziel:», «Keine Schiffe im Hafen», «Einladen» (lines 24, 25, 26, 38). */
    static final int MISC_SOON = 9, MISC_DEST = 10, MISC_NO_SHIPS = 11, MISC_LOAD = 23;

    /**
     * The units under way (class comment): box 1 the ships bound for
     * Europe, box 2 those bound for the New World, each followed by its
     * passengers.
     */
    private void paintSailing(Graphics2D g) {
        for (boolean toEurope : new boolean[] { true, false }) {
            final List<Unit> cells = sailingCells(toEurope);
            final Rectangle box = (toEurope) ? BOX1 : BOX2;
            for (int i = 0; i < cells.size(); i++) {
                final Rectangle r = sailingCell(box, i, cells.size());
                paintUnit(g, cells.get(i), r.x, r.y);
            }
        }
    }

    /** The ships in port in box 3, the selected one framed. */
    private void paintPort(Graphics2D g) {
        final List<Unit> ships = portShips();
        final Unit sel = selectedShip();
        for (int i = 0; i < ships.size(); i++) {
            final Rectangle r = shipCell(i, ships.size());
            final Unit u = ships.get(i);
            paintUnit(g, u, r.x, r.y);
            if (u == sel) {
                frame(g, r, this.pressed != null && this.pressed.kind == Kind.SHIP
                      && this.pressed.unit == u);
            }
        }
    }

    /** The selected ship's six holds (class comment). */
    private void paintHolds(Graphics2D g) {
        final List<Hold> holds = holds(selectedShip());
        for (int k = 0; k < HOLDS; k++) {
            final Hold h = holds.get(k);
            final Rectangle c = holdCell(k);
            if (h.crate) {
                final BufferedImage img = icon(ICON_CRATE);
                if (img != null) {
                    g.drawImage(img, c.x, c.y, null);
                } else {
                    g.setColor(CRATE);
                    g.fillRect(c.x, c.y, c.width, c.height);
                }
            } else if (h.unit != null && !h.unitRest) {
                final BufferedImage img = sprite(h.unit);
                if (img != null) drawFittedIn(g, img, c);
            } else if (h.goods != null) {
                drawGoods(g, h.goods.getType(),
                          h.goods.getAmount() >= GoodsContainer.CARGO_SIZE, c);
            }
        }
    }

    /**
     * A goods icon in a hold: ICONS.SS 022 + row coloured, 038 + row grey
     * for a partial hold, 1:1 at x + (10 - w + 1) div 2 (V clip008 §2.5);
     * FreeCol's picture fitted without the pack.
     */
    private void drawGoods(Graphics2D g, GoodsType type, boolean full, Rectangle c) {
        final int row = ClassicHud.cargoRow(type.getId());
        final BufferedImage img = (row < 0) ? null
            : icon((full ? ICON_GOODS : ICON_GOODS_GREY) + row);
        if (img != null) {
            g.drawImage(img, c.x + (c.width - img.getWidth() + 1) / 2, c.y, null);
            return;
        }
        final BufferedImage fc = goodsPicture(type);
        if (fc != null) drawFittedIn(g, fc, c);
    }

    /** The colonists on the dock, the dock cursor framed. */
    private void paintDocks(Graphics2D g) {
        final List<Unit> dock = dockUnits();
        Unit cursor = this.dockCursor;
        if (cursor == null || !dock.contains(cursor)) cursor = dock.isEmpty() ? null : dock.get(0);
        for (int i = 0; i < dock.size(); i++) {
            final Rectangle r = dockCell(i, dock.size());
            final Unit u = dock.get(i);
            paintUnit(g, u, r.x, r.y);
            if (u == cursor) {
                frame(g, r, this.pressed != null && this.pressed.kind == Kind.DOCK
                      && this.pressed.unit == u);
            }
        }
    }

    /** An 18 x 18 frame around a 16 x 16 cell: green, white while pressed. */
    private static void frame(Graphics2D g, Rectangle cell, boolean pressedNow) {
        g.setColor((pressedNow) ? FRAME_PRESSED : FRAME);
        g.drawRect(cell.x - 1, cell.y - 1, 17, 17);
    }

    /**
     * A unit 1:1 with its flag, as on the map (clip006 #8120 Europe): its
     * sprite and shadow, the flag in the owner's colour, the letter of its
     * orders ("S" on the dock: boards the next ship).
     */
    private void paintUnit(Graphics2D g, Unit u, int x, int y) {
        final int row = ClassicUnitCycle.ordersRowShown(u);
        ClassicHud.paintIcon(g, this.tiny, ClassicHud.orderLetter(this.text, row), sprite(u),
            ClassicHud.nationRgb(u.getOwner()),
            ClassicHud.letterInk(row, ClassicHud.nationDark(u.getOwner())),
            ClassicHud.unitRow(u), x, y, ClassicHud.NO_MARKER);
    }

    /**
     * The stopgap's market row: every storable good with its price "bid/ask"
     * (what Amsterdam pays / asks; the original's numbers, clip008 §2.8),
     * the market cursor's 1-px frame.
     */
    private void paintMarket(Graphics2D g) {
        final Player player = this.freeColClient.getMyPlayer();
        final Market market = (player == null) ? null : player.getMarket();
        final List<GoodsType> goods = marketGoods();
        if (goods.isEmpty()) return;
        g.setColor(TAG_BG);
        g.fillRect(0, PLATE_Y, EXIT.x, VH - PLATE_Y);
        g.setFont(font(5.5f, Font.PLAIN));
        for (int i = 0; i < goods.size(); i++) {
            final GoodsType gt = goods.get(i);
            final Rectangle r = marketSlot(i);
            final BufferedImage img = goodsPicture(gt);
            if (img != null) drawFitted(g, img, r.x + (r.width - 12) / 2, PLATE_Y + 1, 12);
            if (market != null) {
                final String s = market.getPaidForSale(gt) + "/" + market.getCostToBuy(gt);
                g.setColor(TAG_FG);
                g.drawString(s, r.x + (r.width - g.getFontMetrics().stringWidth(s)) / 2,
                             VH - 2);
            }
        }
        final int k = Math.max(0, Math.min(goods.size() - 1, this.marketCursor));
        final Rectangle c = marketSlot(k);
        g.setColor(FRAME);
        g.drawRect(c.x, c.y, c.width, c.height - 1);
    }

    /**
     * The stopgap's four golden buttons at the top right — recruit,
     * purchase, train and set sail.  Hovered or held: the lighter plate.
     */
    private void paintButtons(Graphics2D g) {
        g.setFont(font(7f, Font.BOLD));
        for (int i = 0; i < this.buttonLabels.size(); i++) {
            final Rectangle r = buttonBounds(i);
            final boolean held = this.pressed != null && this.pressed.kind == Kind.BUTTON
                && this.pressed.index == i && r.contains(this.pointerX, this.pointerY);
            g.setColor((i == this.hovered || held) ? BTN_HOT : BTN_BG);
            g.fillRect(r.x, r.y, r.width, r.height);
            g.setColor(GOLD);
            g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
            final String s = this.buttonLabels.get(i);
            final int tw = g.getFontMetrics().stringWidth(s);
            g.drawString(s, r.x + (r.width - tw) / 2, r.y + r.height - 3);
        }
    }

    /**
     * What is dragged follows the pointer (V clip008 §3.4-3.6): a ship or
     * colonist without its flag, a goods icon (grey for a partial hold).
     */
    private void paintDragged(Graphics2D g) {
        final Hit p = this.pressed;
        if (p == null || !this.dragging) return;
        final Rectangle at = new Rectangle(this.pointerX - 8, this.pointerY - 8, 16, 16);
        BufferedImage img = null;
        switch (p.kind) {
        case SHIP: case DOCK: case SAILING:
            img = sprite(p.unit);
            break;
        case HOLD:
            if (p.hold.unit != null) {
                img = sprite(p.hold.unit);
            } else if (p.hold.goods != null) {
                drawGoods(g, p.hold.goods.getType(),
                          p.hold.goods.getAmount() >= GoodsContainer.CARGO_SIZE,
                          new Rectangle(at.x + 3, at.y + 2, HOLD_W, HOLD_H));
            }
            break;
        case MARKET:
            drawGoods(g, p.type, true, new Rectangle(at.x + 3, at.y + 2, HOLD_W, HOLD_H));
            break;
        default:
            break;
        }
        if (img != null) g.drawImage(img, at.x + (16 - img.getWidth()) / 2, at.y, null);
    }


    // Shared drawing helpers

    /** A small black-plated caption (the stopgap's, without the pack). */
    private void plate(Graphics2D g, String s, int x, int y) {
        if (s == null || s.isEmpty()) return;
        g.setFont(font(6f, Font.BOLD));
        final int tw = g.getFontMetrics().stringWidth(s);
        g.setColor(TAG_BG);
        g.fillRect(x - 1, y - 6, tw + 2, 7);
        g.setColor(TAG_FG);
        g.drawString(s, x, y);
    }

    /**
     * Draw {@code img} scaled to fit a {@code size}-px box at {@code (x, y)},
     * preserving aspect.
     */
    private void drawFitted(Graphics2D g, BufferedImage img, int x, int y, int size) {
        drawFittedIn(g, img, new Rectangle(x, y, size, size));
    }

    /** Draw {@code img} scaled (nearest) to fit the cell, centred, preserving aspect. */
    private static void drawFittedIn(Graphics2D g, BufferedImage img, Rectangle c) {
        final double s = Math.min((double) c.width / img.getWidth(),
                                  (double) c.height / img.getHeight());
        final int w = Math.max(1, (int) Math.round(img.getWidth() * s));
        final int h = Math.max(1, (int) Math.round(img.getHeight() * s));
        g.drawImage(img, c.x + (c.width - w) / 2, c.y + (c.height - h) / 2, w, h, null);
    }

    /** Render a StringTemplate/key, guarding against nulls / missing keys. */
    private static String msg(String key) {
        try {
            return (key == null) ? "" : Messages.message(key);
        } catch (RuntimeException e) {
            return (key == null) ? "" : key;
        }
    }

    private static String msg(StringTemplate t) {
        try {
            return (t == null) ? "" : Messages.message(t);
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** A font in <em>virtual</em> pixels — the paint transform scales it up. */
    private Font font(float size, int style) {
        return getFont().deriveFont(style, size);
    }
}
