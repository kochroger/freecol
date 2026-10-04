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
import java.util.Comparator;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import net.sf.freecol.client.ClientOptions;
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
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;


/**
 * The classic-UI <b>Europe</b> screen — the docks of the player's home port in
 * the original 1994 <em>Colonization</em> (design ref: the expert's
 * {@code opening_009}&ndash;{@code 013}).
 *
 * <p>As with {@link ClassicColonyPanel}, everything is painted into a virtual
 * <b>320&times;200</b> canvas (the original's VGA resolution) and then up-scaled
 * by the largest integer factor that fits the window, nearest-neighbour, so the
 * layout constants read straight off the screenshots and the classic pixels stay
 * crisp.  The original {@code EUROPE.PIK} harbour picture (sky, sea, the wooden
 * piers and the row of European town houses) is blitted as the backdrop; the
 * live figures are drawn over it:
 *
 * <ul>
 *   <li><b>Title bar</b> — the port name, turn, tax and treasury, gold on black.</li>
 *   <li><b>Action buttons</b> (top right) — the three golden buttons of the
 *   original (REKRUT / KAUFEN / AUSBILDEN: recruit / purchase / train), plus a
 *   fourth, <b>Set Sail</b>, for the selected-ship interaction below.  No
 *   screenshot of the original's own set-sail affordance has surfaced, so this
 *   mirrors the standard (non-classic) Europe screen's own button of the same
 *   name and key rather than a confirmed original design — a placeholder in
 *   the same vein as the build-queue picker, open for the expert.  Recruit/
 *   purchase/train each open a choice dialog and drive the real
 *   {@link InGameController} path (the dialogs are plain Swing for now, Phase 3
 *   reskins them to the wood-framed look with the colonist portrait, exactly
 *   like the colony-founding seams); Set Sail acts directly on the selected
 *   ship.</li>
 *   <li><b>Ships in port</b> — the naval units waiting in Europe, floating on the
 *   water by the piers.</li>
 *   <li><b>Units on the docks</b> — the land units standing on the quay, ready to
 *   board a ship.</li>
 *   <li><b>Sailing units</b> — the units on the high seas, split into those bound
 *   for the New World and those returning to Europe.</li>
 *   <li><b>Market row</b> — every storable good with its current sale price along
 *   the bottom.</li>
 * </ul>
 *
 * <p>Interaction is click-to-select, click-to-target throughout, the same idiom
 * as every other classic screen: select a colonist on the dock to board a ship
 * ({@link #boardSelected}); select a ship in port to buy and load goods from
 * the market ({@link #loadMarketGood}), sell goods already in its hold
 * ({@link #sellCargo}), or set sail for the New World ({@link #setSail}).
 * Escape or a click on the red exit button closes the screen.
 */
final class ClassicEuropePanel extends JPanel {

    /** The original VGA canvas this screen is laid out in. */
    private static final int VW = 320;
    private static final int VH = 200;

    /** Title bar: full width, gold on black. */
    private static final int TITLE_H = 9;

    /** The three action buttons, stacked at the top right. */
    private static final int BTN_W = 40;
    private static final int BTN_H = 11;
    private static final int BTN_X = VW - BTN_W - 1;
    private static final int BTN_Y = TITLE_H + 3;

    /** The market row along the very bottom. */
    private static final int MARKET_Y = 182;
    private static final int MARKET_H = VH - MARKET_Y;

    /** The selected ship's cargo hold — a strip of goods icons above the piers. */
    private static final int CARGO_Y = 90;
    private static final int CARGO_H = 14;

    /** The exit "E" hot zone at the bottom right (part of the harbour art). */
    private static final int EXIT_X = VW - 14;

    private static final Color TITLE_BG = new Color(0x00, 0x00, 0x00);
    private static final Color GOLD = new Color(0xC8, 0xB0, 0x40);
    private static final Color BTN_BG = new Color(0x30, 0x28, 0x10);
    private static final Color BTN_HOT = new Color(0x5A, 0x4C, 0x1C);
    private static final Color TAG_BG = new Color(0x00, 0x00, 0x00, 0xC0);
    private static final Color TAG_FG = new Color(0xFF, 0xFF, 0xFF);
    private static final Color SKY = new Color(0x88, 0xA8, 0xD0);
    private static final Color SEA = new Color(0x28, 0x50, 0x98);
    private static final Color SELECT = new Color(0x40, 0xE0, 0x40);
    private static final Color BOARD_HINT = new Color(0xE8, 0xC8, 0x40);

    private final FreeColClient freeColClient;
    private final ImageLibrary lib;
    private final Europe europe;

    /** Run when the screen is dismissed (Escape / the exit button). */
    private final Runnable onClose;

    /** The three action buttons, rebuilt each paint (virtual-space bounds + label). */
    private final List<Rectangle> buttonBounds = new ArrayList<>();
    private final List<String> buttonLabels = new ArrayList<>();
    private final List<Runnable> buttonActions = new ArrayList<>();

    /** Index into {@link #buttonBounds} of the hovered button, or -1. */
    private int hovered = -1;

    /**
     * Click-to-select, click-to-target interaction, shared by every purpose
     * this screen supports — the same click-driven style as every other
     * classic screen (order buttons, report rows), rather than introducing
     * drag-and-drop as a new interaction paradigm. Holds either a colonist on
     * the dock, selected to board a ship ({@link #boardSelected} via
     * {@link #selectPortUnit}), or a ship in port, selected to buy/load cargo
     * ({@link #loadMarketGood}), sell cargo ({@link #sellCargo}), or set sail
     * ({@link #setSail}) — disambiguated by {@link Unit#isNaval()}. A second
     * click on the same unit deselects.
     */
    private Unit selectedUnit;

    /** Land units on the dock, rebuilt each paint: virtual-space bounds + unit. */
    private final List<Rectangle> dockBounds = new ArrayList<>();
    private final List<Unit> dockUnits = new ArrayList<>();

    /** Ships in port, rebuilt each paint: virtual-space bounds + unit. */
    private final List<Rectangle> portBounds = new ArrayList<>();
    private final List<Unit> portUnits = new ArrayList<>();

    /**
     * The selected ship's cargo hold, rebuilt each paint: virtual-space
     * bounds + goods — click targets for {@link #sellCargo}. Empty unless
     * {@link #selectedUnit} is currently a ship.
     */
    private final List<Rectangle> cargoBounds = new ArrayList<>();
    private final List<Goods> cargoGoods = new ArrayList<>();

    /**
     * The market row, rebuilt each paint: virtual-space bounds + goods type —
     * click targets for {@link #loadMarketGood} while a ship is selected.
     */
    private final List<Rectangle> marketBounds = new ArrayList<>();
    private final List<GoodsType> marketTypes = new ArrayList<>();

    /** Device-space scale + origin of the virtual canvas, set on each paint. */
    private int scale = 1;
    private int originX;
    private int originY;


    ClassicEuropePanel(FreeColClient freeColClient, ImageLibrary lib,
                       Europe europe, Runnable onClose) {
        this.freeColClient = freeColClient;
        this.lib = lib;
        this.europe = europe;
        this.onClose = onClose;
        setOpaque(true);
        setBackground(Color.BLACK);
        setPreferredSize(new Dimension(VW * 3, VH * 3));
        setFocusable(true);
        installKeyBindings();
        addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    onClick(e);
                }
            });
        addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    onHover(e);
                }
            });
    }


    // Assets

    /** The original harbour backdrop, or null when the asset pack is absent. */
    private BufferedImage backdrop() {
        return ImageLibrary.getUnscaledImage("image.classic_original.pik.EUROPE.PIK");
    }


    // Input

    private void installKeyBindings() {
        final InputMap im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        final ActionMap am = getActionMap();
        im.put(KeyStroke.getKeyStroke("ESCAPE"), "classic_closeEurope");
        am.put("classic_closeEurope", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    close();
                }
            });
    }

    private void onClick(MouseEvent e) {
        if (this.scale <= 0) return;
        final int vx = (e.getX() - this.originX) / this.scale;
        final int vy = (e.getY() - this.originY) / this.scale;
        // The exit button ("E") at the bottom right, as in the original art.
        if (vx >= EXIT_X && vy >= MARKET_Y) {
            close();
            return;
        }
        for (int i = 0; i < this.buttonBounds.size(); i++) {
            if (this.buttonBounds.get(i).contains(vx, vy)) {
                this.buttonActions.get(i).run();
                return;
            }
        }
        for (int i = 0; i < this.cargoBounds.size(); i++) {
            if (this.cargoBounds.get(i).contains(vx, vy)) {
                sellCargo(this.cargoGoods.get(i));
                return;
            }
        }
        for (int i = 0; i < this.portBounds.size(); i++) {
            if (this.portBounds.get(i).contains(vx, vy)) {
                selectPortUnit(this.portUnits.get(i));
                return;
            }
        }
        for (int i = 0; i < this.dockBounds.size(); i++) {
            if (this.dockBounds.get(i).contains(vx, vy)) {
                selectDockUnit(this.dockUnits.get(i));
                return;
            }
        }
        for (int i = 0; i < this.marketBounds.size(); i++) {
            if (this.marketBounds.get(i).contains(vx, vy)) {
                loadMarketGood(this.marketTypes.get(i));
                return;
            }
        }
    }

    /** Select (or, on a second click, deselect) a colonist standing on the dock. */
    private void selectDockUnit(Unit unit) {
        this.selectedUnit = (this.selectedUnit == unit) ? null : unit;
        repaint();
    }

    /**
     * Click a ship in port: boards the selected dock colonist onto it if one
     * is selected ({@link #boardSelected}); otherwise selects (or, on a
     * second click, deselects) the ship itself for the cargo/set-sail actions
     * below — the same click-to-select, click-to-target idiom, just
     * disambiguated by whether a land or naval unit is currently selected.
     */
    private void selectPortUnit(Unit ship) {
        if (this.selectedUnit != null && !this.selectedUnit.isNaval()) {
            boardSelected(ship);
        } else {
            this.selectedUnit = (this.selectedUnit == ship) ? null : ship;
            repaint();
        }
    }

    /**
     * Board the selected colonist onto {@code ship} via the real controller —
     * the same call {@code CargoPanel} makes in the standard UI.
     */
    private void boardSelected(Unit ship) {
        igc().boardShip(this.selectedUnit, ship);
        this.selectedUnit = null;
        refresh();
    }

    /**
     * Sell one type of cargo off the selected ship via
     * {@link InGameController#unloadCargo} — which, since the ship is in
     * Europe, routes to {@code sellGoods} internally (the same call
     * {@code GoodsLabel}/{@code MarketPanel} make when a cargo icon is
     * dragged off a carrier in the standard UI). The ship stays selected so
     * several goods types can be sold in one visit.
     */
    private void sellCargo(Goods goods) {
        igc().unloadCargo(goods, false);
        refresh();
    }

    /**
     * Buy and load a full hold's worth of {@code type} onto the selected ship
     * via {@link InGameController#buyGoods} — the same call {@code
     * MarketLabel} makes when dragged onto the cargo panel in the standard
     * UI. ({@link InGameController#loadCargo}'s own Europe branch looks like
     * the equivalent seam, but it builds a {@code Goods} located at {@code
     * Europe} first, and {@code Europe} has no {@code GoodsContainer} —
     * {@link Goods}'s constructor rejects that with a live
     * "Can not store goods at: Europe" {@code RuntimeException}, caught while
     * live-testing this slice; {@code buyGoods} needs no such object.)
     * Capped at one cargo hold ({@link GoodsContainer#CARGO_SIZE}) per click,
     * mirroring that drag; the ship stays selected so several goods types can
     * be bought in one visit.
     */
    private void loadMarketGood(GoodsType type) {
        if (this.selectedUnit == null || !this.selectedUnit.isNaval()) return;
        final Unit ship = this.selectedUnit;
        int loadable = ship.getLoadableAmount(type);
        if (loadable <= 0) return;
        if (loadable > GoodsContainer.CARGO_SIZE) loadable = GoodsContainer.CARGO_SIZE;
        igc().buyGoods(type, loadable, ship);
        refresh();
    }

    /**
     * Set sail for the New World with the selected ship via
     * {@link InGameController#moveTo} — the literal "set sail" seam
     * (Javadoc: "Called from EuropePanel.DestinationPanel"), mirroring the
     * standard Europe screen's own Set Sail button ({@code
     * EuropePanel#sailAction}, which drops the selected ship onto its
     * "sail to America" destination target). Mirrors that button's one
     * safety check too: if a colonist is still waiting on the dock and
     * auto-load-emigrants is off, confirm before leaving them behind (the
     * same {@code europePanel.leaveColonists} template, through the classic
     * UI's own wired {@code modalConfirmDialog}).
     */
    private void setSail() {
        if (this.selectedUnit == null || !this.selectedUnit.isNaval()) return;
        final Unit ship = this.selectedUnit;
        final Map map = this.freeColClient.getGame().getMap();
        if (!this.freeColClient.getClientOptions()
                .getBoolean(ClientOptions.AUTOLOAD_EMIGRANTS)
            && !this.dockUnits.isEmpty()
            && ship.hasSpaceLeft()) {
            final StringTemplate locName
                = map.getLocationLabelFor(this.freeColClient.getMyPlayer());
            if (!this.freeColClient.getGUI().modalConfirmDialog(null,
                    StringTemplate.template("europePanel.leaveColonists")
                        .addStringTemplate("%newWorld%", locName),
                    ship, "ok", "cancel", true)) return;
        }
        igc().moveTo(ship, map);
        this.selectedUnit = null;
        refresh();
    }

    /** Track which button the pointer is over, and repaint if it changed. */
    private void onHover(MouseEvent e) {
        if (this.scale <= 0) return;
        final int vx = (e.getX() - this.originX) / this.scale;
        final int vy = (e.getY() - this.originY) / this.scale;
        int found = -1;
        for (int i = 0; i < this.buttonBounds.size(); i++) {
            if (this.buttonBounds.get(i).contains(vx, vy)) {
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
            igc().recruitUnitInEurope(idx);
            refresh();
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
            igc().trainUnitInEurope(sorted.get(idx));
            refresh();
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
        final Object sel = ClassicGUI.chooseFromList(
            SwingUtilities.getWindowAncestor(this), title, prompt, null,
            options);
        if (sel == null) return -1;
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(sel)) return i;
        }
        return -1;
    }

    /** Repaint after a model change (a recruit, a purchase, an arrival). */
    void refresh() {
        repaint();
    }


    // Painting

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        final Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                           RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                           RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        this.scale = Math.max(1, Math.min(getWidth() / VW, getHeight() / VH));
        this.originX = (getWidth() - VW * this.scale) / 2;
        this.originY = (getHeight() - VH * this.scale) / 2;
        g.translate(this.originX, this.originY);
        g.scale(this.scale, this.scale);
        g.clipRect(0, 0, VW, VH);

        paintBackground(g);
        paintTitle(g);
        paintSailing(g);
        paintCargo(g);
        paintPort(g);
        paintDocks(g);
        paintMarket(g);
        paintButtons(g);
        g.dispose();
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
     * The units on the high seas, split by heading: those sailing out to the New
     * World and those returning to Europe.  Each row is a caption plus the unit
     * sprites, at the left of the sky as in the original.
     */
    private void paintSailing(Graphics2D g) {
        final Player player = this.freeColClient.getMyPlayer();
        final HighSeas highSeas = (player == null) ? null : player.getHighSeas();
        final List<Unit> toAmerica = new ArrayList<>();
        final List<Unit> toEurope = new ArrayList<>();
        if (highSeas != null) {
            for (Unit u : highSeas.getUnitList()) {
                final Location dest = u.getDestination();
                if (dest instanceof Europe) {
                    toEurope.add(u);
                } else {
                    toAmerica.add(u);
                }
            }
        }
        paintSailingRow(g, Messages.message("sailingToAmerica"), toAmerica, TITLE_H + 4);
        paintSailingRow(g, Messages.message("sailingToEurope"), toEurope, TITLE_H + 22);
    }

    private void paintSailingRow(Graphics2D g, String caption, List<Unit> units, int y) {
        if (units.isEmpty()) return;
        plate(g, caption, 3, y);
        int x = 3;
        for (Unit u : units) {
            final BufferedImage img = this.lib.getScaledUnitImage(u);
            if (img != null) drawFitted(g, img, x, y + 2, 18);
            x += 18;
            if (x > 120) break;
        }
    }

    /**
     * The ships waiting in port, floating on the water by the piers — click
     * targets for {@link #selectPortUnit}. Highlighted gold while a dock unit
     * is selected (a hint of where it can board), or green if the ship itself
     * is the current selection (the cargo/set-sail target).
     */
    private void paintPort(Graphics2D g) {
        this.portBounds.clear();
        this.portUnits.clear();
        int x = 6;
        final int y = 122;
        for (Unit u : this.europe.getUnitList()) {
            if (!u.isNaval()) continue;
            final BufferedImage img = this.lib.getScaledUnitImage(u);
            if (img != null) drawFitted(g, img, x, y, 22);
            this.portBounds.add(new Rectangle(x, y, 22, 22));
            this.portUnits.add(u);
            if (u == this.selectedUnit) {
                g.setColor(SELECT);
                g.drawRect(x, y, 21, 21);
            } else if (this.selectedUnit != null && !this.selectedUnit.isNaval()) {
                g.setColor(BOARD_HINT);
                g.drawRect(x, y, 21, 21);
            }
            x += 24;
            if (x > 150) break;
        }
    }

    /**
     * The selected ship's cargo hold — goods icons in a strip just above the
     * piers, click targets for {@link #sellCargo}. Empty (nothing drawn)
     * unless a ship is currently selected, since there is nothing to sell/
     * click otherwise.
     */
    private void paintCargo(Graphics2D g) {
        this.cargoBounds.clear();
        this.cargoGoods.clear();
        if (this.selectedUnit == null || !this.selectedUnit.isNaval()) return;
        int x = 3;
        g.setFont(font(6f, Font.PLAIN));
        for (Goods good : this.selectedUnit.getCompactGoodsList()) {
            final BufferedImage img = this.lib.getScaledGoodsTypeImage(good.getType());
            if (img != null) drawFitted(g, img, x, CARGO_Y, CARGO_H);
            this.cargoBounds.add(new Rectangle(x, CARGO_Y, CARGO_H, CARGO_H));
            this.cargoGoods.add(good);
            final String s = String.valueOf(good.getAmount());
            g.setColor(TAG_FG);
            g.drawString(s, x, CARGO_Y + CARGO_H + 6);
            x += CARGO_H + 2;
            if (x > VW - CARGO_H) break;
        }
    }

    /**
     * The land units standing on the quay, ready to embark — click to select
     * one, then click a ship above to board it ({@link #selectDockUnit}).
     */
    private void paintDocks(Graphics2D g) {
        this.dockBounds.clear();
        this.dockUnits.clear();
        int x = 112;
        final int y = 150;
        int row = 0;
        for (Unit u : this.europe.getUnitList()) {
            if (u.isNaval()) continue;
            final BufferedImage img = this.lib.getScaledUnitImage(u);
            if (img != null) drawFitted(g, img, x, y + row * 16, 16);
            this.dockBounds.add(new Rectangle(x, y + row * 16, 16, 16));
            this.dockUnits.add(u);
            if (u == this.selectedUnit) {
                g.setColor(SELECT);
                g.drawRect(x, y + row * 16, 15, 15);
            }
            x += 13;
            if (x > VW - 16) {           // wrap onto a second rank
                x = 112;
                row++;
                if (row > 1) break;
            }
        }
    }

    /**
     * The market row: every storable good with its current sale price — click
     * targets for {@link #loadMarketGood} while a ship is selected (hinted
     * gold, the same {@link #BOARD_HINT} treatment as the boarding targets).
     */
    private void paintMarket(Graphics2D g) {
        this.marketBounds.clear();
        this.marketTypes.clear();
        final Player player = this.freeColClient.getMyPlayer();
        final Market market = (player == null) ? null : player.getMarket();
        final List<GoodsType> goods
            = this.europe.getSpecification().getStorableGoodsTypeList();
        if (goods.isEmpty()) return;

        g.setColor(TAG_BG);
        g.fillRect(0, MARKET_Y, VW, MARKET_H);

        final boolean shipSelected
            = this.selectedUnit != null && this.selectedUnit.isNaval();
        final int cw = (VW - 14) / goods.size();      // leave room for the exit "E"
        g.setFont(font(6f, Font.PLAIN));
        for (int i = 0; i < goods.size(); i++) {
            final GoodsType gt = goods.get(i);
            final int x = i * cw;
            final BufferedImage img = this.lib.getScaledGoodsTypeImage(gt);
            if (img != null) drawFitted(g, img, x + (cw - 12) / 2, MARKET_Y + 1, 12);
            if (market != null) {
                final String s = String.valueOf(market.getPaidForSale(gt));
                g.setColor(TAG_FG);
                g.drawString(s, x + (cw - g.getFontMetrics().stringWidth(s)) / 2,
                             MARKET_Y + MARKET_H - 2);
            }
            final Rectangle r = new Rectangle(x, MARKET_Y, cw, MARKET_H);
            this.marketBounds.add(r);
            this.marketTypes.add(gt);
            if (shipSelected) {
                g.setColor(BOARD_HINT);
                g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
            }
        }
    }

    /**
     * The four golden action buttons at the top right — recruit, purchase,
     * train and set sail.  Their bounds and actions are recorded here so
     * {@link #onClick} and {@link #onHover} can drive them.
     */
    private void paintButtons(Graphics2D g) {
        this.buttonBounds.clear();
        this.buttonLabels.clear();
        this.buttonActions.clear();
        addButton(Messages.message("recruit"), this::recruit);
        addButton(Messages.message("purchase"), this::purchase);
        addButton(Messages.message("train"), this::train);
        addButton(Messages.message("setSail"), this::setSail);

        g.setFont(font(7f, Font.BOLD));
        for (int i = 0; i < this.buttonBounds.size(); i++) {
            final Rectangle r = this.buttonBounds.get(i);
            g.setColor((i == this.hovered) ? BTN_HOT : BTN_BG);
            g.fillRect(r.x, r.y, r.width, r.height);
            g.setColor(GOLD);
            g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
            final String s = this.buttonLabels.get(i);
            final int tw = g.getFontMetrics().stringWidth(s);
            g.drawString(s, r.x + (r.width - tw) / 2, r.y + r.height - 3);
        }
    }

    private void addButton(String label, Runnable action) {
        final int i = this.buttonBounds.size();
        this.buttonBounds.add(new Rectangle(BTN_X, BTN_Y + i * (BTN_H + 2), BTN_W, BTN_H));
        this.buttonLabels.add(label);
        this.buttonActions.add(action);
    }


    // Shared drawing helpers

    /** A small black-plated caption, as used above the sailing rows. */
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
     * preserving aspect — the original sprites are small so this up-scales, the
     * pack-absent FreeCol art is large and shrinks (see {@code ClassicColonyPanel}).
     */
    private void drawFitted(Graphics2D g, BufferedImage img, int x, int y, int size) {
        final double s = Math.min((double) size / img.getWidth(),
                                  (double) size / img.getHeight());
        final int w = Math.max(1, (int) Math.round(img.getWidth() * s));
        final int h = Math.max(1, (int) Math.round(img.getHeight() * s));
        g.drawImage(img, x + (size - w) / 2, y + (size - h) / 2, w, h, null);
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
