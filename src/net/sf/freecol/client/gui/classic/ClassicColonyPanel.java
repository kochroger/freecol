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
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.control.InGameController;
import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.common.model.Building;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.ColonyTile;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;


/**
 * The classic-UI <b>colony screen</b> — the signature screen of the original
 * 1994 <em>Colonization</em>.
 *
 * <p>Everything is painted into a virtual <b>320&times;200</b> canvas (the
 * original's VGA resolution) which is then up-scaled by the largest integer
 * factor that fits the window, nearest-neighbour, so the classic pixels stay
 * crisp and the layout constants below can be read straight off the original
 * screenshots.  Top to bottom:
 *
 * <ul>
 *   <li><b>Top line</b> — the original's (M4.1, {@link ClassicColonyChrome}):
 *   «Name.   Jahreszeit, Jahr.  Gold: n¤» in FONTTINY on the wood rows, or a
 *   gold notice in its place ({@link ClassicColonyNotice}).</li>
 *   <li><b>Building area</b> (left) — the original's (M4.2, M4.3,
 *   {@link ClassicColonyBuildings}): the 15 fixed slots on the parchment
 *   ground (PARCH.SS, M4.1), each family's {@code BUILDING.SS} sprite at
 *   its level or the class's placeholder, the production rows of icons,
 *   the workers' figures with their shadows, the units outside the colony
 *   on the fortification slot, the NAMES.TXT name as the hover label.</li>
 *   <li><b>Tile pane</b> (right) — the original's 3&times;3 colony map
 *   (M4.4, {@link ClassicColonyTiles}): the colony's nine tiles in 24-px
 *   cells in the black frame on the wood panel ({@code WOODTILE.SS}, M4.1),
 *   each with our map's art of it scaled to 24 (the original's finer art is
 *   the open point), its water cycling with the map's palette; the colony's
 *   sprite and its two rows on the centre; the workers with their rows, a
 *   worker who makes nothing under the red sign; the totem on native land;
 *   an enemy at war on his tile; the selected colonist's green frame, the
 *   blinking white cursor, the tile's hover label.</li>
 *   <li><b>Bottom band</b> — the original {@code COLONY.PIK} chrome
 *   (320&times;72) with its black first row; on the left the original's
 *   left panel (M4.5, {@link ClassicColonyBand}): the SoL flag and Tory
 *   crown with their «p% (n)», the figure row (the colonists by their job,
 *   then the units outside; the selected one's green frame or the white
 *   cursor), the production row (food used, stored or short, crosses,
 *   bells); in the middle the port (M4.6, {@link ClassicColonyPort}): «Keine
 *   Schiffe im Hafen» or «Einladen: Typ», the ship or wagon train in its
 *   frame, its six holds with the goods' icons and the crates; the net
 *   production on the right (the first slice's until M4.7; a click there
 *   opens the build list until M4.7's ÄNDERN), and the original's 16-box
 *   <b>warehouse</b> row along the bottom (M4.1: icons, numbers in their
 *   inks, the selected good's green frame) with «Abb» over the exit.</li>
 * </ul>
 *
 * <p>The screen kit (M4.0): the original arrow is hidden during a drag
 * ({@link #dragging}, {@link ClassicPointer#DRAGGING}); one blink for the
 * cursors ({@link #blinkOff}); the press / drag / release machine
 * ({@link ClassicDragGesture}); the top line's notice slot
 * ({@link #holdNotice}, {@link #showNotice}); the completion dissolve
 * ({@link ClassicColonyDissolve}).  Nothing on the screen can be dragged
 * yet, so its presses act as before (M4.8-M4.10).
 *
 * <p>Interaction is click-to-select, click-to-target — the same style as every
 * other classic screen (order buttons, report rows, the build queue, Europe
 * boarding) rather than drag-and-drop: click a colonist (standing in the
 * colony, already working a building, or already working a tile) to select
 * it, then click a building or work tile to move it there via
 * {@link net.sf.freecol.client.control.InGameController#work}. Loading cargo
 * and per-nation tints remain later slices; for now Escape or a click on the
 * red exit button closes the screen.
 */
final class ClassicColonyPanel extends JPanel {

    /** The original VGA canvas this screen is laid out in. */
    private static final int VW = ClassicColonyLayout.W;
    private static final int VH = ClassicColonyLayout.H;

    /** The exit's hit area: from the warehouse row's last box on (M4.11 makes it the E). */
    private static final int WARE_Y = 178;
    private static final int WARE_W = 306;

    /** The three sub-panels of the band, and their shared top. */
    private static final int PANEL_Y = 130;
    private static final int PANEL1_X = 3;
    private static final int PANEL2_X = 122;
    private static final int PANEL3_X = 207;

    private static final Color TAG_BG = new Color(0x00, 0x00, 0x00, 0xC0);
    private static final Color TAG_FG = new Color(0xFF, 0xFF, 0xFF);

    private final FreeColClient freeColClient;
    private final ImageLibrary lib;
    private final Colony colony;

    /** Run when the screen is dismissed (Escape / the exit button). */
    private final Runnable onClose;

    /**
     * What a click on a unit standing in the colony needs from the game's
     * GUI (clip 019, {@link ClassicColonyUnits}): the row's order and the
     * unit's options box over this screen.
     */
    interface Units {

        /**
         * The colony's row of units as the screen shows it
         * ({@link ClassicColonyUnits#order}).
         *
         * @param standing The units standing in the colony, in the
         *     tile's order.
         * @return The row.
         */
        List<Unit> order(List<Unit> standing);

        /**
         * The unit's options box (@COLONYUNIT) over this screen, and what
         * its answer does; returns when the box is closed.
         *
         * @param unit The unit clicked.
         * @param first Whether it is the first unit of the row.
         */
        void options(Unit unit, boolean first);
    }

    /** The units' box, or null (then a click only selects, as before). */
    private final Units units;

    /**
     * The units standing in the colony as last painted (the row), their
     * cells parallel ({@link #paintPopulation}); a press on one selects it,
     * the release on the same one opens its box.
     */
    private final List<Unit> bandUnits = new ArrayList<>();
    private final List<Rectangle> bandBounds = new ArrayList<>();

    /** The row's unit a press is on, waiting for its release, or null. */
    private Unit pressedBand = null;

    /** The flag letters' font (FONTTINY), or null without the pack. */
    private final ClassicFont tiny;

    /** The original's chrome and warehouse pieces (M4.1), from the pack. */
    private final ClassicColonyChrome.Assets chrome;

    /**
     * The warehouse's selected good (its green frame): global, it carries
     * over to the next colony and the next visit (V clip 020: Prov. opened
     * with Rum framed, the good last dragged in Base).  Food until a goods
     * drag moves it (M4.9).  EDT only.
     */
    private static int warehouseSelection = 0;

    /** The time (nanoseconds) of the blink and the notice slot. */
    private final ClassicSlide.Clock clock;

    /** The top line's notice slot (M4.0 target 6; its texts come with M4.9). */
    private final ClassicColonyNotice notice = new ClassicColonyNotice();

    /** Brings the colony's words back when a result's notice ends, or null. */
    private javax.swing.Timer noticeTimer = null;

    /**
     * The screen's blink (M4.0 target 4: the tile, figure, button and
     * carrier cursors, all in one phase, 23 frames): armed while the
     * screen is shown, stopped during a drag.
     */
    private final ClassicBlink blink;

    /** Whether the blink is in its off half now. */
    private boolean blinkOff = false;

    /**
     * Whether the last paint drew a blinking thing ({@link #blinkOff} read
     * by a painter, M4.4-M4.7): only then does a toggle repaint.
     */
    private boolean blinkUsed = false;

    /**
     * The press / drag / release machine (M4.0 target 3).  Nothing on the
     * screen can be dragged yet ({@link #canDragAt}): the colony's drags
     * come with M4.8/M4.9, and until then its presses act as before.
     */
    private final ClassicDragGesture gesture = new ClassicDragGesture();

    /** The building area's sprites (M4.2, M4.3), from the pack. */
    private final ClassicColonyBuildings.Assets buildingArt;

    /**
     * The building area as the last paint drew it ({@link
     * ClassicColonyBuildings#model}): the 15 slots with their labels, work
     * targets and figures; the hover label and the presses read it.
     */
    private ClassicColonyBuildings.Model area
        = new ClassicColonyBuildings.Model(new ArrayList<>());

    /**
     * Where the colony's founding families stand, or null for the
     * colony's own layout ({@link ClassicColonyBuildings.Founding#of(long)}
     * from its id); a clip's colony in the golden tests.
     */
    private ClassicColonyBuildings.Founding founding = null;

    /**
     * While recording: what the building area drew (each slot's sprite,
     * row and figures), as the last paint left it and as last logged
     * ({@link #recordBuildings}).
     */
    private String buildingsDrawn = "";
    private String buildingsLogged = null;

    /** The 3x3 map's terrain (M4.4): our map's tiles scaled to 24. */
    private final ClassicColonyTiles.Art tileArt;

    /**
     * The 3x3 map as the last paint drew it ({@link ClassicColonyTiles#model}):
     * the nine tiles with their rows, workers, signs and labels.
     */
    private ClassicColonyTiles.Model tiles = new ClassicColonyTiles.Model(null);

    /** What draws a tile's terrain: the art (null), or a test's. */
    private ClassicColonyTiles.Ground ground = null;

    /**
     * The water's cycling phase the map's terrain shows now (the game's
     * terrain layer; the original cycles one hardware palette for the map
     * and this screen), 0 without one.
     */
    private java.util.function.IntSupplier waterPhase = () -> 0;

    /** Whether the last paint drew a tile holding a cycling index (its water). */
    private boolean tilesCycle = false;

    /** The 3x3 map's tile under the pointer (its hover label), or -1. */
    private int hoveredTile = -1;

    /**
     * The white cursor's tile (0-8, the centre 4), or {@link #CURSOR_OFF_TILES}
     * when it stands on a figure (the figure row's look is M4.5's): global,
     * as the warehouse's selection, so it carries over to the next visit
     * (V clip 020: openings with it on Mitte, NW, NO, N, SW or on a figure;
     * I: across colonies).  The centre at the first opening (V #2260).  A
     * press on a tile or its worker brings it there, a press on another
     * colonist takes it off the tiles (dago §3: the press moves the cursor;
     * the keys that move it in the original come with M4.8).  EDT only.
     */
    private static int tileCursor = ClassicColonyLayout.CENTRE_TILE;

    /**
     * {@link #tileCursor} off the tiles, on the selected colonist's figure
     * in the band's figure row (M4.5: white in the blink's on half, nothing
     * in its off half; V clip 020 #123003, #6560).
     */
    static final int CURSOR_OFF_TILES = -1;

    /**
     * {@link #tileCursor} on the carrier in port (M4.6: its frame white in
     * the blink's on half, green in its off half; V clip 020 #120499,
     * #120521, #134899, clip008 #30022: the colony opened with it there; a
     * press on the carrier brings it, I: the goods' drags too, M4.9).
     */
    static final int CURSOR_CARRIER = -2;

    /** The tiles as last logged by the recorder ({@link #recordBuildings}). */
    private String tilesLogged = null;

    /** The band's left panel as the last paint drew it (M4.5, {@link ClassicColonyBand}). */
    private ClassicColonyBand.Model band = null;

    /** A test's change of the left panel's model before it is drawn, or null. */
    private java.util.function.UnaryOperator<ClassicColonyBand.Model> bandAdjust = null;

    /** Whether the last paint drew the SoL flag (its canton cycles with the water). */
    private boolean flagDrawn = false;

    /** The port panel as the last paint drew it (M4.6, {@link ClassicColonyPort}). */
    private ClassicColonyPort.Model port = null;

    /** The left panel and the port as last logged by the recorder. */
    private String bandLogged = null, portLogged = null;

    /** The figure row's colonists as this paint orders them ({@link #figureOrder}). */
    private List<Unit> rowOrder = new ArrayList<>();

    /** The band's figure row as last painted. */
    private List<ClassicColonyBuildings.Figure> figures = new ArrayList<>();

    /** @return The band's figure row as last painted (tests, the report). */
    List<ClassicColonyBuildings.Figure> rowFigures() {
        return this.figures;
    }

    /** The stills taken so far, for their names (EDT only). */
    private static int stills = 0;

    /**
     * Until the right panel's ÄNDERN (M4.7, M4.10) opens the build list, a
     * press on the right panel does (the first slice's black construction
     * plate over the buildings is gone: the original shows the build only
     * in its hammer panel, M4.2).
     */
    private static final Rectangle BUILD_LIST_AREA = ClassicColonyLayout.RIGHT_PANEL;

    /** The slot of {@link #area} under the pointer (its hover label), or -1. */
    private int hovered = -1;

    /** The warehouse box under the pointer (its good's hover label), or -1. */
    private int hoveredBox = -1;

    /**
     * Whether the production numbers are drawn, read at each paint: the
     * count labels of the buildings' stacked rows ("Beschriftung an
     * Gebäuden", M4.3), and the numbers on the work tiles and
     * under the net production ("Beschriftung an Waren und Terrain";
     * {@link ClassicPrefs#BUILDING_LABELS},
     * {@link ClassicPrefs#GOODS_TERRAIN_LABELS}).  The warehouse's amounts
     * stay (I: they are the store itself, not a label).
     */
    private boolean buildingNumbers = true, goodsTerrainNumbers = true;

    /**
     * Work-assignment interaction: select a colonist — standing in the colony,
     * or already working a building/tile — then click a building or work tile
     * to move it there via {@link InGameController#work}. Click-to-select,
     * click-to-target — the same style as Europe boarding, rather than
     * introducing drag-and-drop as a new interaction paradigm.
     */
    private Unit selectedUnit;

    /**
     * Every clickable colonist sprite on this screen (standing in the colony,
     * working a building, or working a tile), rebuilt each paint: virtual-space
     * bounds + unit.
     */
    private final List<Rectangle> unitBounds = new ArrayList<>();
    private final List<Unit> unitTargets = new ArrayList<>();

    /** The 3x3 map's clickable cells (excluding the centre), rebuilt each paint. */
    private final List<Rectangle> tileBounds = new ArrayList<>();
    private final List<ColonyTile> tileTargets = new ArrayList<>();

    /** The workers of the tiles among {@link #unitTargets} (a press on one brings the cursor). */
    private final List<Unit> tileWorkers = new ArrayList<>();

    /** Device-space scale + origin of the virtual canvas, set on each paint. */
    private int scale = 1;
    private int originX;
    private int originY;


    ClassicColonyPanel(FreeColClient freeColClient, ImageLibrary lib,
                       Colony colony, Runnable onClose) {
        this(freeColClient, lib, colony, onClose, null);
    }

    /**
     * @param freeColClient The client.
     * @param lib The images.
     * @param colony The colony shown.
     * @param onClose Run when the screen is dismissed.
     * @param units What a click on a unit standing in the colony does, or
     *     null (a click only selects it).
     */
    ClassicColonyPanel(FreeColClient freeColClient, ImageLibrary lib,
                       Colony colony, Runnable onClose, Units units) {
        this(freeColClient, lib, colony, onClose, units, ClassicSlide.SYSTEM, true);
    }

    /**
     * @param freeColClient The client.
     * @param lib The images.
     * @param colony The colony shown.
     * @param onClose Run when the screen is dismissed.
     * @param units What a click on a unit standing in the colony does, or
     *     null.
     * @param clock The blink's and the notice's time.
     * @param threadedBlink Whether the blink waits on a thread of its own
     *     (the game); false leaves its toggles to the caller (tests).
     */
    ClassicColonyPanel(FreeColClient freeColClient, ImageLibrary lib,
                       Colony colony, Runnable onClose, Units units,
                       ClassicSlide.Clock clock, boolean threadedBlink) {
        this.freeColClient = freeColClient;
        this.lib = lib;
        this.colony = colony;
        this.onClose = onClose;
        this.units = units;
        this.clock = clock;
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        this.tiny = (pack == null) ? null : pack.font(ClassicFont.TINY);
        this.chrome = ClassicColonyChrome.Assets.load(pack);
        this.buildingArt = ClassicColonyBuildings.Assets.load(pack);
        this.tileArt = ClassicColonyTiles.Art.load(pack, lib);
        this.blink = new ClassicBlink(clock, SwingUtilities::invokeLater,
                                      this::blinkToggled, threadedBlink);
        setOpaque(true);
        setBackground(Color.BLACK);
        setPreferredSize(new Dimension(VW * 3, VH * 3));
        setFocusable(true);
        installKeyBindings();
        addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        final int vx = canvasX(e.getX()), vy = canvasY(e.getY());
                        gesture.press(vx, vy, e.getX(), e.getY(), canDragAt(vx, vy));
                    }
                    onClick(e);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)
                        && gesture.release() == ClassicDragGesture.Result.DROP) {
                        dragEnded();
                    }
                    onRelease(e);
                }
            });
        addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    onHover(e);
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (gesture.motion(e.getX(), e.getY())) dragStarted();
                }
            });
    }


    // Dismissal

    private void installKeyBindings() {
        final InputMap im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        final ActionMap am = getActionMap();
        im.put(KeyStroke.getKeyStroke("ESCAPE"), "classic_closeColony");
        am.put("classic_closeColony", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    close();
                }
            });
    }

    /**
     * The original's exit button is the red "E" at the bottom right of the
     * {@code COLONY.PIK} chrome; a click anywhere on it closes the screen.
     * Otherwise: a click on a colonist selects it (see {@link #selectUnit}); a
     * click on a building or work tile while one is selected moves it there
     * (see {@link #assignWork}).
     */
    private void onClick(MouseEvent e) {
        pressAt((e.getX() - this.originX) / this.scale,
                (e.getY() - this.originY) / this.scale);
    }

    /**
     * The release of a press: on the unit of the colony's row the press
     * selected, that unit's options box ({@link Units#options}; clip 019:
     * the frame on the press #916, the box 0.6 s later, I: on the
     * release).
     */
    private void onRelease(MouseEvent e) {
        releaseAt((e.getX() - this.originX) / this.scale,
                  (e.getY() - this.originY) / this.scale);
    }

    /**
     * A click at a point of the 320x200 canvas, press and release (the
     * acceptance harness's {@code sclick} on this screen).
     *
     * @param vx The x.
     * @param vy The y.
     */
    void clickAt(int vx, int vy) {
        pressAt(vx, vy);
        releaseAt(vx, vy);
    }

    /**
     * The unit of the colony's row at a canvas point: the topmost (the
     * last drawn) where cells overlap.
     *
     * @param vx The x.
     * @param vy The y.
     * @return Its index in {@link #bandUnits}, or -1.
     */
    private int bandAt(int vx, int vy) {
        for (int i = this.bandBounds.size() - 1; i >= 0; i--) {
            if (this.bandBounds.get(i).contains(vx, vy)) return i;
        }
        return -1;
    }

    private void releaseAt(int vx, int vy) {
        final Unit pressed = this.pressedBand;
        this.pressedBand = null;
        if (pressed == null || this.units == null) return;
        final int i = bandAt(vx, vy);
        if (i < 0 || this.bandUnits.get(i) != pressed) return;
        ClassicFrameRecorder.event("colony-unit", "click " + pressed.getId()
            + " cell=" + i + " state=" + pressed.getState());
        this.units.options(pressed, i == 0);
        refresh();
        recordStill("unit " + pressed.getId());
    }

    /**
     * While the acceptance recorder runs: the canvas as a still now (from
     * the time this screen was a window outside the HUD copy; since M4.0
     * it is hosted in the HUD and filmed too), after a unit's box: its
     * flag as the answer left it.
     *
     * @param why What it shows, for the event.
     */
    private void recordStill(String why) {
        if (!ClassicFrameRecorder.on()) return;
        final String name = "colony-" + (++stills);
        ClassicFrameRecorder.event("colony-still", name + " " + why);
        ClassicFrameRecorder.still(name, canvas());
    }

    /**
     * The 320x200 canvas as the screen paints it now, in its own picture
     * (the recorder's stills, the golden tests).
     *
     * @return The picture (RGB).
     */
    BufferedImage canvas() {
        final BufferedImage still
            = new BufferedImage(VW, VH, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = still.createGraphics();
        try {
            paintCanvas(g);
        } finally {
            g.dispose();
        }
        return still;
    }

    private void pressAt(int vx, int vy) {
        this.pressedBand = null;
        if (BUILD_LIST_AREA.contains(vx, vy)) {
            this.freeColClient.getGUI().showBuildQueuePanel(this.colony);
            return;
        }
        if (vx >= WARE_W && vy >= WARE_Y) { close(); return; }
        // The white cursor goes to a pressed tile (its worker's or an
        // empty one: dago §3, playthrough-1 #75221), off the tiles to
        // another pressed colonist (M4.4; the original's keys move it too,
        // M4.8).
        final int tile = ClassicColonyLayout.tileAt(vx, vy);
        if (tile >= 0 && this.tiles.cell(tile) != null) tileCursor = tile;

        // The carrier in port (M4.6): the press brings the cursor (dago §3:
        // the press moves it); a wagon train's box on the release, as from
        // the figure row's end where it stood until the port showed it.
        final Rectangle carrier = (this.port == null) ? null : this.port.frame();
        if (carrier != null && carrier.contains(vx, vy)) {
            tileCursor = CURSOR_CARRIER;
            if (!this.port.carrier.isNaval() && this.units != null) {
                this.pressedBand = this.port.carrier;
            }
            repaint();
            return;
        }

        // A unit standing in the colony (the original's "Vorhandene
        // Einheiten"): the press selects it, its release opens its box.
        final int band = bandAt(vx, vy);
        if (band >= 0 && this.units != null) {
            this.pressedBand = this.bandUnits.get(band);
            this.selectedUnit = this.pressedBand;
            tileCursor = CURSOR_OFF_TILES;
            repaint();
            return;
        }
        for (int i = 0; i < this.unitBounds.size(); i++) {
            if (this.unitBounds.get(i).contains(vx, vy)) {
                final Unit u = this.unitTargets.get(i);
                if (!this.tileWorkers.contains(u)) tileCursor = CURSOR_OFF_TILES;
                selectUnit(u);
                return;
            }
        }
        if (tile >= 0) repaint();
        if (this.selectedUnit == null) return;
        // A building with workplaces (not a placeholder, the harbour, the
        // fortification or the Lagerhaus' slot).
        final int slot = slotAt(vx, vy);
        if (slot >= 0) {
            final Building target = (slot < this.area.slots.size())
                ? this.area.slots.get(slot).target : null;
            if (target != null) assignWork(target);
            return;
        }
        for (int i = 0; i < this.tileBounds.size(); i++) {
            if (this.tileBounds.get(i).contains(vx, vy)) {
                assignWork(this.tileTargets.get(i));
                return;
            }
        }
    }

    /**
     * The slot of the building area under a point: the topmost (the last
     * drawn) whose sprite rectangle holds it (the slots are fixed, so this
     * needs no paint; I: the rectangles, clip008: a label does not depend
     * on where in its sprite the pointer is).
     *
     * @param vx The x.
     * @param vy The y.
     * @return Its index in {@link ClassicColonyLayout#SLOTS}, or -1.
     */
    static int slotAt(int vx, int vy) {
        for (int i = ClassicColonyLayout.SLOTS.length - 1; i >= 0; i--) {
            if (ClassicColonyLayout.SLOTS[i].bounds().contains(vx, vy)) return i;
        }
        return -1;
    }

    /** Select (or, on a second click of the same unit, deselect) a colonist to move. */
    private void selectUnit(Unit unit) {
        this.selectedUnit = (this.selectedUnit == unit) ? null : unit;
        repaint();
    }

    /**
     * Move the selected colonist into {@code target} via the real controller —
     * the same {@link InGameController#work} call {@code ColonyPanel}/
     * {@code UnitLabel} make in the standard UI, for both a building and a work
     * tile ({@code Building}/{@code ColonyTile} both implement
     * {@code WorkLocation}). It already claims an unowned tile and confirms
     * abandoning education when needed, so no extra guarding is required here.
     */
    private void assignWork(net.sf.freecol.common.model.WorkLocation target) {
        igc().work(this.selectedUnit, target);
        this.selectedUnit = null;
        refresh();
    }

    /** Track which building the pointer is over, and repaint if it changed. */
    private void onHover(MouseEvent e) {
        if (this.scale <= 0) return;
        hoverAt((e.getX() - this.originX) / this.scale,
                (e.getY() - this.originY) / this.scale);
    }

    /**
     * The pointer is over a canvas point (the mouse, tests): the building
     * there shows its name, a warehouse good its «Ware (n Tonnen)» label
     * (V clip008 §2.6, clip 020 #21747).
     *
     * @param vx The x, or -1 for none.
     * @param vy The y.
     */
    void hoverAt(int vx, int vy) {
        final int found = (vx < 0) ? -1 : slotAt(vx, vy);
        final int box = (vx < 0) ? -1 : ClassicColonyLayout.warehouseBoxAt(vx, vy);
        // A tile of the 3x3 map: its terrain's words (dago §3.5).
        final int tile = (vx < 0) ? -1 : ClassicColonyLayout.tileAt(vx, vy);
        if (found != this.hovered || box != this.hoveredBox || tile != this.hoveredTile) {
            this.hovered = found;
            this.hoveredBox = box;
            this.hoveredTile = tile;
            repaint();
        }
    }

    private void close() {
        if (this.onClose != null) this.onClose.run();
    }

    private InGameController igc() {
        return this.freeColClient.getInGameController();
    }

    /** Repaint after a model change (a build-queue pick, production, gold). */
    void refresh() {
        repaint();
    }


    // The screen kit (M4.0: blink, drag, notice, warehouse selection)

    /** @return The canvas x of a device x (the last paint's scale and origin). */
    private int canvasX(int x) {
        return (x - this.originX) / Math.max(1, this.scale);
    }

    /** @return The canvas y of a device y. */
    private int canvasY(int y) {
        return (y - this.originY) / Math.max(1, this.scale);
    }

    /**
     * Whether a press at a canvas point picks something that can be
     * dragged: a colonist (M4.8) or a good (M4.9).  Nothing yet: the
     * colony's presses still act as the first slice's.
     *
     * @param vx The x.
     * @param vy The y.
     * @return False.
     */
    private boolean canDragAt(int vx, int vy) {
        return false;
    }

    /**
     * @return Whether a drag runs on this screen: the arrow is hidden
     *     meanwhile ({@link ClassicPointer#DRAGGING}).
     */
    boolean dragging() {
        return this.gesture.dragging();
    }

    /** A drag started: no blink during it (dago §4.1), the arrow goes. */
    private void dragStarted() {
        this.blink.stop();
        firePropertyChange(ClassicPointer.DRAGGING, false, true);
        repaint();
    }

    /** A drag ended: the blink starts again (I: a new phase), the arrow comes back. */
    private void dragEnded() {
        if (isDisplayable()) this.blink.arm();
        this.blinkOff = false;
        firePropertyChange(ClassicPointer.DRAGGING, true, false);
        repaint();
    }

    /** A toggle of the blink: repainted only if something blinks. */
    private void blinkToggled(int n) {
        this.blinkOff = ClassicBlink.isOff(n);
        if (this.blinkUsed) repaint();
    }

    /**
     * Whether a blinking thing is in its off half now; a painter that
     * asks makes the next toggles repaint the screen.
     *
     * @return True in the off half.
     */
    boolean blinkOff() {
        this.blinkUsed = true;
        return this.blinkOff;
    }

    /** @return The blink (tests). */
    ClassicBlink blink() {
        return this.blink;
    }

    @Override
    public void addNotify() {
        super.addNotify();
        // The blink's phase starts with the shown screen (V: the cursor is
        // on in the first frame, I).
        this.blinkOff = false;
        this.blink.arm();
    }

    @Override
    public void removeNotify() {
        // Not displayable any more (the pop; also a full-screen switch,
        // which makes the window displayable again): no blink meanwhile.
        this.gesture.cancel();
        this.blink.stop();
        if (this.noticeTimer != null) this.noticeTimer.stop();
        super.removeNotify();
    }

    /** The screen is gone for good (its close or replacement): the blink's thread ends. */
    void dispose() {
        this.gesture.cancel();
        this.blink.close();
        if (this.noticeTimer != null) this.noticeTimer.stop();
    }

    /**
     * A notice in the top line while something runs (a goods drag's
     * «Einladen …», M4.9): it stays until {@link #showNotice} or
     * {@link #clearNotice}.
     *
     * @param text The notice.
     */
    void holdNotice(String text) {
        this.notice.hold(text);
        repaint(0, 0, getWidth(), getHeight());
    }

    /**
     * A result's notice in the top line (M4.9: «24 Erz bewegt nach …»):
     * the colony's words come back 138 frames later.
     *
     * @param text The notice.
     */
    void showNotice(String text) {
        this.notice.show(text, this.clock.now());
        if (this.noticeTimer == null) {
            this.noticeTimer = new javax.swing.Timer(0, e -> repaint());
            this.noticeTimer.setRepeats(false);
        }
        this.noticeTimer.setInitialDelay((int) Math.ceil(ClassicColonyNotice.RESULT_MS));
        this.noticeTimer.restart();
        repaint();
    }

    /** The colony's words again in the top line. */
    void clearNotice() {
        this.notice.clear();
        repaint();
    }

    /** @return The notice the top line shows now, or null (tests). */
    String noticeShown() {
        return this.notice.at(this.clock.now());
    }

    /** @return The warehouse's selected good's box (global). */
    static int warehouseSelection() {
        return warehouseSelection;
    }

    /**
     * Select a good of the warehouse (the goods step, M4.9; tests): its
     * box gets the green frame, in every colony, until another is selected.
     *
     * @param box The box, 0-15.
     */
    static void selectWarehouseGood(int box) {
        if (box >= 0 && box < ClassicColonyLayout.WAREHOUSE_BOXES) {
            warehouseSelection = box;
        }
    }

    /**
     * A drag from one canvas point to another, as the mouse would do it
     * (the harness's {@code sdrag}, tests): the press, a motion on the
     * way, the release.  Until M4.8 nothing on this screen can be dragged,
     * so this is a press at a and a release at b.
     *
     * @param ax Press x.
     * @param ay Press y.
     * @param bx Release x.
     * @param by Release y.
     * @param shift Whether Shift is held (the goods step's partial amount).
     */
    void dragAt(int ax, int ay, int bx, int by, boolean shift) {
        final int s = Math.max(1, this.scale);
        this.gesture.press(ax, ay, this.originX + ax * s, this.originY + ay * s,
                           canDragAt(ax, ay));
        pressAt(ax, ay);
        if (this.gesture.motion(this.originX + bx * s, this.originY + by * s)) {
            dragStarted();
        }
        if (this.gesture.release() == ClassicDragGesture.Result.DROP) dragEnded();
        releaseAt(bx, by);
    }


    // Painting

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        final Graphics2D g = (Graphics2D) g0.create();

        // Fit the 320x200 canvas into the window at the largest integer scale.
        this.scale = Math.max(1, Math.min(getWidth() / VW, getHeight() / VH));
        this.originX = (getWidth() - VW * this.scale) / 2;
        this.originY = (getHeight() - VH * this.scale) / 2;
        g.translate(this.originX, this.originY);
        g.scale(this.scale, this.scale);
        paintCanvas(g);
        g.dispose();
        recordBuildings();
    }

    /** Paint the 320x200 canvas into {@code g}, in canvas coordinates. */
    private void paintCanvas(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                           RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                           RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.clipRect(0, 0, VW, VH);

        // Rebuilt across paintBuildings/paintWorkTiles/paintPopulation below,
        // so cleared once here rather than per method.
        this.unitBounds.clear();
        this.unitTargets.clear();
        // The numbers, as SPIEL "Koloniebericht-Optionen" switches them
        // (W14): the count labels of the buildings' stacked rows (M4.3) and
        // of the tiles' (M4.4; I: the options' meaning in the original),
        // and the first slice's figures under the net production (until
        // M4.7).
        final ClassicPrefs prefs = ClassicPrefs.get();
        this.buildingNumbers = prefs.is(ClassicPrefs.BUILDING_LABELS);
        this.goodsTerrainNumbers = prefs.is(ClassicPrefs.GOODS_TERRAIN_LABELS);

        // Whether this paint draws a blinking thing (blinkOff() asked).
        this.blinkUsed = false;
        // The original's chrome (M4.1): the top line or its notice, the
        // parchment and its black column, the wood panel and the 3x3
        // map's frame; then what stands on them.
        final String shown = this.notice.at(this.clock.now());
        if (shown != null) {
            ClassicColonyChrome.paintNotice(g, this.chrome, shown);
        } else {
            ClassicColonyChrome.paintTopLine(g, this.chrome, topLine());
        }
        ClassicColonyChrome.paintGround(g, this.chrome);
        ClassicColonyChrome.paintWoodPanel(g, this.chrome);
        paintBuildings(g);
        paintWorkTiles(g);
        paintBand(g);
        paintHover(g);
    }

    /** @return The top line's words now ({@link ClassicColonyChrome#model}). */
    private String topLine() {
        return chromeModel().topLine;
    }

    /** @return What the chrome shows of the colony now. */
    ClassicColonyChrome.Model chromeModel() {
        final Game game = (this.freeColClient == null) ? null : this.freeColClient.getGame();
        final Player player = (this.freeColClient == null) ? null
            : this.freeColClient.getMyPlayer();
        return ClassicColonyChrome.model(this.colony, game, player,
            ClassicText.load(ClassicPackFiles.runtime()), warehouseSelection);
    }

    /**
     * While the acceptance recorder runs: log what each slot of the
     * building area shows ({@code colony-buildings}: its sprite frame, row
     * and figures, {@link ClassicColonyBuildings.Model}) and each tile of
     * the 3x3 map ({@code colony-tiles}: its rows, worker, sign, totem,
     * enemy, and the cursor's place, {@link ClassicColonyTiles.Model}), and
     * save the canvas as a still, whenever that changes (since M4.0 the
     * screen is hosted in the HUD, so the recorder's frames hold it too).
     */
    private void recordBuildings() {
        if (!ClassicFrameRecorder.on()) return;
        final String drawn = this.buildingsDrawn;
        final String tilesDrawn = this.tiles.toString();
        final String bandDrawn = String.valueOf(this.band);
        // The port with the white cursor's place (a press on the carrier brings it).
        final String portDrawn = this.port + " cursor=" + cursorName();
        final boolean area = !drawn.equals(this.buildingsLogged);
        final boolean map = !tilesDrawn.equals(this.tilesLogged);
        final boolean left = !bandDrawn.equals(this.bandLogged);
        final boolean dock = !portDrawn.equals(this.portLogged);
        if (!area && !map && !left && !dock) return;
        this.buildingsLogged = drawn;
        this.tilesLogged = tilesDrawn;
        this.bandLogged = bandDrawn;
        this.portLogged = portDrawn;
        final String name = "colony-" + (++stills);
        if (area) {
            ClassicFrameRecorder.event("colony-buildings",
                name + " " + this.colony.getId() + " " + drawn);
        }
        // The 3x3 map (M4.4): each tile's rows, worker, sign, totem.
        if (map) {
            ClassicFrameRecorder.event("colony-tiles",
                name + " " + this.colony.getId() + " " + tilesDrawn + " cursor="
                + cursorName());
        }
        // The band's left panel (M4.5) and the port (M4.6).
        if (left) {
            ClassicFrameRecorder.event("colony-band",
                name + " " + this.colony.getId() + " " + bandDrawn);
        }
        if (dock) {
            ClassicFrameRecorder.event("colony-port",
                name + " " + this.colony.getId() + " " + portDrawn);
        }
        ClassicFrameRecorder.still(name, canvas());
    }

    /** @return Where the white cursor stands (the recorder): a tile's name, «figure», «carrier». */
    private static String cursorName() {
        if (tileCursor >= 0) return ClassicColonyLayout.TILE_NAMES[tileCursor];
        return (tileCursor == CURSOR_CARRIER) ? "carrier" : "figure";
    }

    /**
     * The building area (M4.2, M4.3; {@link ClassicColonyBuildings}): the
     * 15 slots with their families' sprites or placeholders, the rows,
     * the workers and the units outside, clipped to x 0-198, y 8-127; the
     * figures become click targets (a press selects, as the first slice's).
     */
    private void paintBuildings(Graphics2D g0) {
        final ClassicText text = ClassicText.load(ClassicPackFiles.runtime());
        final List<Unit> outside = outside();
        this.rowOrder = figureOrder();
        this.area = ClassicColonyBuildings.model(this.colony, text, this.buildingArt,
                                                 this.founding, outside, this.rowOrder);
        this.buildingsDrawn = this.area.toString();
        keepLayout(this.area.layout);
        final Graphics2D g = (Graphics2D) g0.create();
        final Rectangle r = ClassicColonyLayout.BUILDING_AREA;
        g.clipRect(r.x, r.y, r.width, r.height);
        try {
            ClassicColonyBuildings.paint(g, this.chrome, this.buildingArt, this.area,
                                         this.selectedUnit, this.buildingNumbers);
        } finally {
            g.dispose();
        }
        // The workers are click targets (select, then a building or tile);
        // the units outside on the fence not yet (M4.8: the job menu).
        for (ClassicColonyBuildings.Placed p : this.area.slots) {
            if (p.slot.cls == ClassicColonyLayout.SlotClass.FENCE) continue;
            for (ClassicColonyBuildings.Figure f : p.figures) {
                if (f.unit == null) continue;
                this.unitBounds.add(f.bounds());
                this.unitTargets.add(f.unit);
            }
        }
    }

    /**
     * The colony keeps its picture's layout ({@code Colony.classicSlots}:
     * a family's slot from its first showing on, M4.2), on our copy and, in
     * a single player game, on the server's, whose state every save writes
     * (as {@code ClassicGUI.markTip}).
     *
     * @param layout The layout ({@link ClassicColonyBuildings.Model#layout}).
     */
    private void keepLayout(String layout) {
        if (layout == null || layout.isEmpty()
            || layout.equals(this.colony.getClassicSlots())) return;
        this.colony.setClassicSlots(layout);
        final net.sf.freecol.server.FreeColServer server = (this.freeColClient == null)
            ? null : this.freeColClient.getFreeColServer();
        final Game sg = (server == null) ? null : server.getGame();
        final Colony sc = (sg == null) ? null
            : sg.getFreeColGameObject(this.colony.getId(), Colony.class);
        if (sc != null && sc != this.colony) sc.setClassicSlots(layout);
    }

    /**
     * The units outside the colony in the order they stand
     * ({@link ClassicColonyBuildings#outside}): the screen's row order
     * ({@link Units#order}, the units moved to the front first) within
     * one figure job.
     *
     * @return The units.
     */
    private List<Unit> outside() {
        return ClassicColonyBuildings.outside(this.buildingArt, this.colony,
            (this.units == null) ? null : this.units::order);
    }

    /**
     * The hovered slot's name over everything, the original's hover label
     * (spec §1.2, M4.2 target 4): white FONTTINY on its black box, centred
     * over the sprite and clamped to the building area, at the sprite's top
     * for the houses, the mid slots and the fortification, centred for the
     * large slots, the harbour and the trees; NAMES.TXT's names, «Bäume»,
     * «(Außerhalb der Kolonie)».
     */
    private void paintHover(Graphics2D g) {
        if (this.hoveredTile >= 0) {
            ClassicColonyTiles.paintLabel(g, this.chrome, this.tiles.cell(this.hoveredTile));
        }
        if (this.hovered < 0 || this.hovered >= this.area.slots.size()) return;
        final ClassicColonyBuildings.Placed p = this.area.slots.get(this.hovered);
        if (p.label == null || p.label.isEmpty()) return;
        ClassicColonyChrome.paintHoverLabel(g, this.chrome, p.label, p.slot.x,
                                            p.slot.cls.w, p.labelY);
    }

    /**
     * Where the colony's founding families stand (a clip's colony in the
     * golden tests; null: the colony's own).
     *
     * @param f The layout, or null.
     */
    void setFounding(ClassicColonyBuildings.Founding f) {
        this.founding = f;
        repaint();
    }

    /** @return The building area as last painted (tests). */
    ClassicColonyBuildings.Model area() {
        return this.area;
    }

    /**
     * Select a unit as a click on its figure does (tests: a clip's
     * selected colonist).
     *
     * @param u The unit, or null.
     */
    void select(Unit u) {
        this.selectedUnit = u;
        repaint();
    }

    /** @return The selected unit, or null (tests). */
    Unit selected() {
        return this.selectedUnit;
    }

    /**
     * The 3x3 colony map (M4.4, {@link ClassicColonyTiles}): inside the
     * wood panel's black frame ({@link ClassicColonyChrome#paintWoodPanel}),
     * the colony's nine tiles placed by their compass direction from the
     * colony (FreeCol's isometric map: the raw offsets do not fill a
     * square), each with our map's art scaled to 24 at the water's phase,
     * the colony's sprite on the centre; the rows, the workers, the sign,
     * the totem, an enemy at war at tile + (4,4) with his flag (M2,
     * war-french #13962); the selected colonist's green frame and the
     * white cursor in its blink's on half (the blink: {@link #blinkOff}).
     * The worked tiles' workers and the eight outer cells are click targets
     * (the first slice's select, then a building or tile).
     */
    private void paintWorkTiles(Graphics2D g) {
        this.tileBounds.clear();
        this.tileTargets.clear();
        this.tileWorkers.clear();
        final ClassicText text = ClassicText.load(ClassicPackFiles.runtime());
        final ClassicColonyTiles.Model m = ClassicColonyTiles.model(this.colony, text,
                                                                    this.buildingArt);
        this.tiles = (this.tilesAdjust == null) ? m : this.tilesAdjust.apply(m);
        // The cursor blinks only where it stands on a tile (else nothing
        // here asks the blink, so its toggles cost no repaint).
        final int cursor = (tileCursor >= 0 && this.tiles.cell(tileCursor) != null
                            && !blinkOff()) ? tileCursor : -1;
        final ClassicColonyTiles.Ground ground = (this.ground != null) ? this.ground
            : this::paintGround;
        this.tilesCycle = ClassicColonyTiles.paint(g, this.chrome, this.buildingArt,
            this.tiles, ground, this::paintStanding, this.tiles.placeOf(this.selectedUnit), cursor,
            this.goodsTerrainNumbers);
        for (ClassicColonyTiles.Cell c : this.tiles.cells) {
            if (c == null) continue;
            if (c.index != ClassicColonyLayout.CENTRE_TILE && c.colonyTile != null) {
                this.tileBounds.add(c.bounds());
                this.tileTargets.add(c.colonyTile);
            }
            if (c.worker != null && c.worker.unit != null) {
                this.unitBounds.add(c.worker.bounds());
                this.unitTargets.add(c.worker.unit);
                this.tileWorkers.add(c.worker.unit);
            }
        }
    }

    /**
     * A tile's terrain: our map's art of it scaled to 24 ({@link
     * ClassicColonyTiles.Art#tile}), with the colony's sprite on the
     * centre as the map draws it (its flag in the owner's colours).
     *
     * @param g The canvas, clipped to the cell.
     * @param c The cell.
     * @return Whether it holds a cycling index.
     */
    private boolean paintGround(Graphics2D g, ClassicColonyTiles.Cell c) {
        if (c.tile == null) return false;
        BufferedImage settlement = null;
        if (c.index == ClassicColonyLayout.CENTRE_TILE && this.lib != null) {
            final BufferedImage art = this.lib.getScaledSettlementImage(this.colony);
            if (art != null) {
                settlement = ClassicHud.fitSettlement(
                    ClassicHud.colonyFlag(art, this.colony.getOwner()));
            }
        }
        final boolean[] cycles = { false };
        final BufferedImage img = this.tileArt.tile(c.tile.getMap(), c.tile, settlement,
            this.waterPhase.getAsInt(), cycles);
        if (img == null) return false;
        final Rectangle r = c.bounds();
        g.drawImage(img, r.x, r.y, null);
        return cycles[0];
    }

    /**
     * The water's phase the map's terrain shows (the game's layer), read at
     * each paint of the tiles.
     *
     * @param phase The phase's supplier.
     */
    void setWaterPhase(java.util.function.IntSupplier phase) {
        this.waterPhase = (phase == null) ? () -> 0 : phase;
    }

    /**
     * A step of the water's cycling (the game's {@code ClassicWaterCycle},
     * its palette for the map and this screen as the original's one VGA
     * palette): the 3x3 map is painted again when one of its tiles holds a
     * cycling index (V clip 020: 144 px of its water every 40-41 frames),
     * and the SoL flag when it is drawn (M4.5: its canton's 3 pixels cycle
     * too, V #2260 phase 3, #123003 phase 0).
     */
    void paletteStep() {
        if (!isShowing() || this.scale <= 0) return;
        if (this.tilesCycle) repaintCanvas(ClassicColonyLayout.MAP_FRAME);
        if (this.flagDrawn) {
            repaintCanvas(new Rectangle(ClassicColonyLayout.SOL_ICON_X,
                ClassicColonyLayout.SOL_ICON_Y, 13, 11));
        }
    }

    /** Repaint a rectangle of the canvas. */
    private void repaintCanvas(Rectangle f) {
        repaint(this.originX + f.x * this.scale, this.originY + f.y * this.scale,
                f.width * this.scale, f.height * this.scale);
    }

    /** @return Whether the last paint drew the SoL flag (its canton cycles; tests). */
    boolean flagCycles() {
        return this.flagDrawn;
    }

    /**
     * What draws the tiles' terrain (tests: a plain fill to tell what is
     * drawn over it), or null for the art.
     *
     * @param g The painter, or null.
     */
    void setGround(ClassicColonyTiles.Ground g) {
        this.ground = g;
        repaint();
    }

    /** @return The 3x3 map as last painted (tests). */
    ClassicColonyTiles.Model tiles() {
        return this.tiles;
    }

    /** A test's change of the 3x3 map's model before it is drawn, or null. */
    private java.util.function.UnaryOperator<ClassicColonyTiles.Model> tilesAdjust = null;

    /**
     * Change the 3x3 map's model before it is drawn (the golden tests: a
     * clip's amount where FreeCol's rules make another, so that the
     * drawing is checked apart from the rules).
     *
     * @param f The change, or null.
     */
    void setTilesAdjust(java.util.function.UnaryOperator<ClassicColonyTiles.Model> f) {
        this.tilesAdjust = f;
        repaint();
    }

    /**
     * Put the blink in its on or off half (tests: a clip frame's phase).
     *
     * @param off Whether in its off half.
     */
    void blinkPhase(boolean off) {
        this.blinkOff = off;
        repaint();
    }

    /** @return Whether the last paint drew a tile holding a cycling index (tests). */
    boolean tilesCycle() {
        return this.tilesCycle;
    }

    /**
     * Put the white cursor on a tile (tests: a clip's place), off the
     * tiles on the selected one's figure ({@link #CURSOR_OFF_TILES}), or
     * on the carrier in port ({@link #CURSOR_CARRIER}).
     *
     * @param tile The tile, 0-8, {@link #CURSOR_OFF_TILES} or
     *     {@link #CURSOR_CARRIER}.
     */
    static void setTileCursor(int tile) {
        tileCursor = (tile >= 0 && tile < ClassicColonyLayout.TILE_COUNT) ? tile
            : (tile == CURSOR_CARRIER) ? CURSOR_CARRIER : CURSOR_OFF_TILES;
    }

    /** @return The white cursor's tile, {@link #CURSOR_OFF_TILES} or {@link #CURSOR_CARRIER}. */
    static int tileCursor() {
        return tileCursor;
    }

    /**
     * The bottom band: the original {@code COLONY.PIK} chrome with its
     * black first row ({@link ClassicColonyChrome#paintBand}), then over
     * it the left panel (M4.5: the SoL line, the figure row, the production
     * row), the port (M4.6: the caption, the carrier, its holds), the
     * colony's net production (right, the first slice's until M4.7), and
     * the original's warehouse row along the bottom with «Abb» over the
     * exit (M4.1).
     */
    private void paintBand(Graphics2D g) {
        ClassicColonyChrome.paintBand(g, this.chrome);
        paintPopulation(g);
        paintPort(g);
        paintNetProduction(g);
        final int[] amounts = ClassicColonyChrome.amounts(this.colony);
        ClassicColonyChrome.paintWarehouse(g, this.chrome, amounts,
            ClassicColonyChrome.exported(this.colony), warehouseSelection);
        ClassicColonyChrome.paintExitLabel(g, this.chrome);
        if (this.hoveredBox >= 0) {
            final net.sf.freecol.common.model.Specification spec
                = this.colony.getSpecification();
            final GoodsType gt = (spec == null) ? null
                : spec.getGoodsType(ClassicColonyLayout.WAREHOUSE_GOODS[this.hoveredBox]);
            if (gt != null) {
                ClassicColonyChrome.paintWarehouseLabel(g, this.chrome,
                    ClassicColonyChrome.warehouseLabel(
                        ClassicText.load(ClassicPackFiles.runtime()), gt,
                        amounts[this.hoveredBox]), this.hoveredBox);
            }
        }
    }

    /**
     * Left panel (M4.5, {@link ClassicColonyBand}): the SoL line (the flag
     * and «p% (n)» when the colony has members, the crown and its words
     * ending at x 104 when it has Tories), the original's figure row (L
     * §5.2; {@link ClassicColonyBuildings#figureRow}): every colonist by
     * his job now, then the units outside after 4 px more, at y 142
     * without shadow (V clip 020 #2260, #6560, #134173; clip 019 #1290: no
     * artillery there, it stands only in the musket panel's «Vorhandene
     * Einheiten»), the selected one's green frame or, where the white
     * cursor stands on him, the cursor; and the production row at y 163
     * (the food used, stored or short, the crosses, the bells).  A press on
     * a colonist selects him (as on his building or tile); a press on a
     * unit standing in the colony opens its box (@COLONYUNIT, M2) on the
     * release, as the first slice's row did.  Until the musket panel (M4.7)
     * lists them, the colony tile's other units (artillery, a treasure)
     * follow at the row's end so that their box stays at hand (not the
     * original's); a wagon train stands in the port since M4.6.
     */
    private void paintPopulation(Graphics2D g) {
        final ClassicColonyBand.Model m = ClassicColonyBand.model(this.colony,
                                                                  this.buildingArt);
        this.band = (this.bandAdjust == null) ? m : this.bandAdjust.apply(m);
        final Rectangle left = ClassicColonyLayout.LEFT_PANEL;
        {
            final Graphics2D g2 = (Graphics2D) g.create();
            g2.clipRect(left.x, left.y, left.width, left.height);
            try {
                this.flagDrawn = ClassicColonyBand.paint(g2, this.chrome, this.buildingArt,
                    ClassicColonyBand.flag(ClassicPackFiles.runtime(), this.buildingArt,
                                           this.waterPhase.getAsInt()),
                    this.band, this.goodsTerrainNumbers);
            } finally {
                g2.dispose();
            }
        }

        this.bandUnits.clear();
        this.bandBounds.clear();
        final Tile tile = this.colony.getTile();
        if (tile == null) return;
        final List<Unit> standing = new ArrayList<>();
        for (Unit u : tile.getUnitList()) {
            // Ships and wagon trains go in the port (M4.6).
            if (!u.isNaval() && !ClassicColonyPort.isCarrier(u)) standing.add(u);
        }
        final List<Unit> ordered = (this.units == null) ? standing
            : this.units.order(standing);
        final List<Unit> outside = outside();
        final List<Unit> row = new ArrayList<>(outside);
        for (Unit u : ordered) if (!row.contains(u)) row.add(u);
        final List<ClassicColonyBuildings.Figure> figures
            = ClassicColonyBuildings.figureRow(this.buildingArt, this.rowOrder, row);
        this.figures = figures;
        // The white cursor on the selected one's figure (it blinks: only
        // then is the blink asked).
        boolean onFigure = false;
        if (tileCursor == CURSOR_OFF_TILES && this.selectedUnit != null) {
            for (ClassicColonyBuildings.Figure f : figures) {
                if (f.unit == this.selectedUnit) onFigure = true;
            }
        }
        final boolean cursorShown = onFigure && !blinkOff();
        final Graphics2D g2 = (Graphics2D) g.create();
        g2.clipRect(left.x, left.y, left.width, left.height);
        try {
            ClassicColonyBuildings.paintFigureRow(g2, this.chrome, this.buildingArt,
                                                  figures, this.selectedUnit, onFigure,
                                                  cursorShown);
        } finally {
            g2.dispose();
        }
        for (ClassicColonyBuildings.Figure f : figures) {
            final Rectangle r = f.bounds();
            if (standing.contains(f.unit)) {
                this.bandUnits.add(f.unit);
                this.bandBounds.add(r);
                if (this.units == null) {
                    this.unitBounds.add(r);
                    this.unitTargets.add(f.unit);
                }
            } else {
                this.unitBounds.add(r);
                this.unitTargets.add(f.unit);
            }
        }
    }

    /**
     * Each colony's figure row this turn (EDT): its game, the turn's
     * number and the colonists' order ({@link
     * ClassicColonyBuildings#keepOrder}).
     */
    private static final java.util.Map<String, Object[]> figureOrders
        = new java.util.HashMap<>();

    /**
     * The colonists' order in the figure row: by job at the turn's first
     * paint of the colony, then kept (a colonist with a new job keeps his
     * place, one who joined follows at the end; V during a visit, #3500,
     * #46350, #47291, #130343; by job again in a later turn, #134173; I:
     * the original's moment of sorting is not seen, ours the turn's first
     * look at the colony).
     *
     * @return The colonists in the row's order.
     */
    @SuppressWarnings("unchecked")
    private List<Unit> figureOrder() {
        final List<Unit> byJob = ClassicColonyBuildings.colonistsByJob(this.buildingArt,
                                                                       this.colony);
        final Game game = this.colony.getGame();
        final int turn = (game == null || game.getTurn() == null) ? -1
            : game.getTurn().getNumber();
        final Object[] was = figureOrders.get(this.colony.getId());
        final List<Unit> order = (was != null && was[0] == game && (Integer) was[1] == turn)
            ? ClassicColonyBuildings.keepOrder((List<Unit>) was[2], byJob) : byJob;
        figureOrders.put(this.colony.getId(), new Object[] { game, turn, order });
        return order;
    }

    /** Forget the figure rows' orders (tests: each frame's state is a fresh visit). */
    static void forgetFigureOrders() {
        figureOrders.clear();
    }

    /**
     * The step between the cells of the musket panel's «Vorhandene
     * Einheiten» (M4.7; the first slice drew them in the left panel, which
     * shows the original's figure row since M4.3): the original's 18 px
     * (clip 019 #1290: the dragoon from x 213, the artillery from 231,
     * 249, 267), closer when more units stand there than 18 px cells fit,
     * so that every one can be clicked (the topmost wins where cells
     * overlap).
     *
     * @param n The number of units.
     * @return The step, 18 at most, 1 at least.
     */
    static int bandPitch(int n) {
        final int room = (PANEL2_X - 3) - (PANEL1_X + 3) - 16;
        if (n <= 1) return BAND_PITCH;
        return Math.max(1, Math.min(BAND_PITCH, room / (n - 1)));
    }

    /** The original's step between the units' cells (18 px). */
    static final int BAND_PITCH = 18;

    /**
     * A unit standing in the colony: its map sprite with its order flag,
     * the letter in black (clip 019 #1290: "F" and "-" alike black, the
     * flag in the owner's colour), its shadow as on the map (I); without the pack's font the
     * flag has no letter, without its sprite FreeCol's picture fitted.
     */
    private void paintStanding(Graphics2D g, Unit u, int x, int y) {
        final BufferedImage img = (this.lib == null) ? null
            : this.lib.getScaledUnitImage(u);
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        ClassicHud.paintBoxIcon(g, this.tiny,
            ClassicHud.orderLetter(t, ClassicUnitCycle.ordersRowShown(u)),
            (img == null) ? null : ClassicHud.fit16(img),
            ClassicHud.nationRgb(u.getOwner()), ClassicHud.unitRow(u), x, y);
    }

    /**
     * The enemy unit occupying a work tile of the colony at war, which no
     * colonist can then work (clip opening_018 #13962: a French dragoon on
     * Cotton Town's west tile, a French soldier on its south-east one,
     * fortified or not): an offensive unit of a player at war with the
     * colony's owner.  None at peace (I).
     *
     * @param t The work tile.
     * @param owner The colony's owner.
     * @return The unit, or null.
     */
    static Unit occupier(Tile t, Player owner) {
        return ClassicColonyTiles.occupier(t, owner);
    }

    /**
     * Paint the units again into a picture nobody sees, so that the
     * clickable places exist before the screen was ever painted (a
     * minimized scripted run; the harness's {@code sclick}).
     */
    void paintTargets() {
        final BufferedImage img = new BufferedImage(VW, VH, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = img.createGraphics();
        try {
            this.scale = Math.max(1, this.scale);
            paintCanvas(g);
        } finally {
            g.dispose();
        }
    }

    /**
     * The port panel (M4.6, {@link ClassicColonyPort}): the caption, the
     * carrier in port (the tile's first ship or wagon train) as the map
     * draws a unit, in its frame (green; white while the cursor stands on
     * it in the blink's on half), its six holds.  A wagon train there is a
     * press target for its box ({@link #pressAt}), as it was at the figure
     * row's end.
     */
    private void paintPort(Graphics2D g) {
        final ClassicText text = ClassicText.load(ClassicPackFiles.runtime());
        this.port = ClassicColonyPort.model(this.colony, text);
        final Unit carrier = this.port.carrier;
        final boolean white = carrier != null && tileCursor == CURSOR_CARRIER && !blinkOff();
        final String letter = (carrier == null) ? null
            : ClassicHud.orderLetter(text, ClassicUnitCycle.ordersRowShown(carrier));
        ClassicColonyPort.paint(g, this.chrome, this.buildingArt, this.tiny, letter,
                                this.port, white);
        if (carrier != null && !carrier.isNaval()) {
            this.bandUnits.add(carrier);
            this.bandBounds.add(this.port.frame());
        }
    }

    /** @return The port panel as last painted (tests). */
    ClassicColonyPort.Model port() {
        return this.port;
    }

    /** @return The left panel as last painted (tests). */
    ClassicColonyBand.Model band() {
        return this.band;
    }

    /**
     * Change the left panel's model before it is drawn (the golden tests:
     * the clip's amounts where FreeCol's rules make others).
     *
     * @param f The change, or null.
     */
    void setBandAdjust(java.util.function.UnaryOperator<ClassicColonyBand.Model> f) {
        this.bandAdjust = f;
        repaint();
    }

    /** Right panel: what the colony nets each turn, good by good. */
    private void paintNetProduction(Graphics2D g) {
        int x = PANEL3_X + 2;
        int y = PANEL_Y + 2;
        int n = 0;
        for (GoodsType gt : this.colony.getSpecification().getStorableGoodsTypeList()) {
            final int net = this.colony.getNetProductionOf(gt);
            if (net == 0) continue;
            if (n == 4) {                    // second row
                x = PANEL3_X + 2;
                y += 16;
            }
            if (n >= 8) break;
            final BufferedImage img = this.lib.getScaledGoodsTypeImage(gt);
            if (img != null) drawFitted(g, img, x, y, 12);
            if (this.goodsTerrainNumbers) {
                tag(g, (net > 0 ? "+" : "") + net, x, y + 12);
            }
            x += 22;
            n++;
        }
    }

    // Shared drawing helpers

    /** A tiny black-plated number, as used under the net-production icons. */
    private void tag(Graphics2D g, String s, int x, int y) {
        g.setFont(font(6f, Font.BOLD));
        g.setColor(TAG_BG);
        g.fillRect(x, y - 5, g.getFontMetrics().stringWidth(s) + 2, 6);
        g.setColor(TAG_FG);
        g.drawString(s, x + 1, y);
    }

    /**
     * Draw {@code img} scaled to fit a {@code size}-px box at {@code (x, y)},
     * preserving aspect.  The original sprites are small (~16px), so this is
     * usually an up-scale; the pack-absent FreeCol art is large and shrinks.
     */
    private void drawFitted(Graphics2D g, BufferedImage img, int x, int y, int size) {
        final double s = Math.min((double) size / img.getWidth(),
                                  (double) size / img.getHeight());
        final int w = Math.max(1, (int) Math.round(img.getWidth() * s));
        final int h = Math.max(1, (int) Math.round(img.getHeight() * s));
        g.drawImage(img, x + (size - w) / 2, y + (size - h) / 2, w, h, null);
    }

    /** A font in <em>virtual</em> pixels — the paint transform scales it up. */
    private Font font(float size, int style) {
        return getFont().deriveFont(style, size);
    }
}
