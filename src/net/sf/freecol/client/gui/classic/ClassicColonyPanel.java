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
 *   frame, its six holds with the goods' icons and the crates; on the
 *   right the original's right panel (M4.7, {@link ClassicColonyRight}):
 *   the three mode buttons and the house rows, «Vorhandene Einheiten» or
 *   the hammer panel with KAUFEN and ÄNDERN (which opens the build list,
 *   the first slice's until M4.10), and the original's 16-box
 *   <b>warehouse</b> row along the bottom (M4.1: icons, numbers in their
 *   inks, the selected good's green frame) with «Abb» over the exit.</li>
 * </ul>
 *
 * <p>The screen kit (M4.0): the original arrow is hidden during a drag
 * ({@link #dragging}, {@link ClassicPointer#DRAGGING}); one blink for the
 * cursors ({@link #blinkOff}); the press / drag / release machine
 * ({@link ClassicDragGesture}); the top line's notice slot
 * ({@link #holdNotice}, {@link #showNotice}); the completion dissolve
 * ({@link ClassicColonyDissolve}).
 *
 * <p><b>Moving colonists</b> (M4.8, {@link ClassicColonyMoves},
 * {@link ClassicColonyJobs}): the press on a colonist (in a building, on a
 * tile, at the fence) selects him, moves the white cursor (his tile, or
 * his figure in the band) and freezes the blink; motion drags his figure
 * as the ghost, the arrow hidden, no hover label; the drop acts by the
 * drop matrix (a building's work, a tile as Farmer or keeping his work,
 * the fence's J1, @MORETHANTHREE).  A release without motion on the
 * colonist who was already selected opens J2, on a unit at the fence his
 * job menu; on an empty tile or a building it moves the selected
 * colonist there.  A role leaves the colony with the warehouse's goods;
 * the last colonist leaving asks @ABANDON (@ABANDON2 for the only colony)
 * with the bar on «Niemals!», whose «Ja» gives up the colony.  Goods,
 * KAUFEN and the build list remain later steps (M4.9, M4.10); Escape or a
 * click on the exit closes the screen.
 */
final class ClassicColonyPanel extends JPanel {

    /** The original VGA canvas this screen is laid out in. */
    private static final int VW = ClassicColonyLayout.W;
    private static final int VH = ClassicColonyLayout.H;

    /** The exit's hit area: from the warehouse row's last box on (M4.11 makes it the E). */
    private static final int WARE_Y = 178;
    private static final int WARE_W = 306;

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

        /**
         * A wood box over this screen (the job menus, @ABANDON,
         * @MORETHANTHREE, M4.8): its answer, when it is closed.
         *
         * @param r The box.
         * @return The row taken, or {@link ClassicAdvisorBox.Bar#DISMISSED}.
         */
        default int ask(ClassicAdvisorBox.Request r) {
            return ClassicAdvisorBox.Bar.DISMISSED;
        }
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

    /**
     * {@link #tileCursor} on a unit of the musket panel's «Vorhandene
     * Einheiten» (M4.7: the selected one's frame white in the blink's on
     * half, green in its off half; V clip 019 #366-#550, #1322-#1506; a
     * press on a unit there brings it).
     */
    static final int CURSOR_LIST = -3;

    /**
     * {@link #tileCursor} on the hammer panel's KAUFEN or ÄNDERN (M4.7
     * target 5: the button inverted in the blink's on half; V #105806 on,
     * #105829 off: after a building's @BUILT zoom, M4.11; a press on the
     * button brings it, I as dago §3's press).
     */
    static final int CURSOR_BUY = -4, CURSOR_CHANGE = -5;

    /**
     * The mode the last colony visit ended in, for all colonies (spec §0.1
     * decision 8, L §7.1; the house at the session's first opening: clip008
     * «House is pressed by default»).  EDT only.
     */
    private static ClassicColonyRight.Mode lastMode = ClassicColonyRight.Mode.HOUSE;

    /**
     * The right panel's pressed button and the panel it shows: a press on
     * a button moves the first (V: the pressed look in the press's frame,
     * clip 019 #542, #640), its release the second (I: the panel 15-19
     * frames later, clip008 #10350 -&gt; #10365, clip 019 #640 -&gt; #659,
     * as long as a click lasts).
     */
    private ClassicColonyRight.Mode buttonMode = lastMode, mode = lastMode;

    /** Whether a press on a mode button waits for its release. */
    private boolean modePressed = false;

    /** The musket panel's selected unit (its frame), or null for the first. */
    private Unit listSelected = null;

    /** The right panel as the last paint drew it (M4.7, {@link ClassicColonyRight}). */
    private ClassicColonyRight.Model right = null;

    /** A test's change of the right panel's model before it is drawn, or null. */
    private java.util.function.UnaryOperator<ClassicColonyRight.Model> rightAdjust = null;

    /** The right panel as last logged by the recorder. */
    private String rightLogged = null;

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

    /** The left panel and the port as last logged by the recorder (the right panel: {@link #rightLogged}). */
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

    /** The slot of {@link #area} under the pointer (its hover label), or -1. */
    private int hovered = -1;

    /** The warehouse box under the pointer (its good's hover label), or -1. */
    private int hoveredBox = -1;

    /**
     * Whether the production numbers are drawn, read at each paint: the
     * count labels of the buildings' stacked rows ("Beschriftung an
     * Gebäuden", M4.3), and the count labels of the tiles', the band's and
     * the right panel's house rows ("Beschriftung an Waren und Terrain";
     * {@link ClassicPrefs#BUILDING_LABELS},
     * {@link ClassicPrefs#GOODS_TERRAIN_LABELS}).  The warehouse's amounts
     * stay (I: they are the store itself, not a label).
     */
    private boolean buildingNumbers = true, goodsTerrainNumbers = true;

    /**
     * The selected colonist (his green frames on his figure in the band
     * and on his building, tile or fence figure; M4.8): a press on a
     * colonist selects him, a drag the dragged one; at the opening the
     * founder, else the one selected last here (target 9).
     */
    private Unit selectedUnit;

    /**
     * The colonist each colony had selected last (its id by the colony's),
     * the opening's choice (M4.8 target 9: V #19585 → #19980, kept across
     * visits).  EDT only.
     */
    private static final java.util.Map<String, String> lastSelected
        = new java.util.HashMap<>();

    /** The figure a press is on (a colonist in a building, on a tile, at the fence), or null. */
    private Unit pressedUnit = null;

    /** Where {@link #pressedUnit} stands, and his figure's sprite (the ghost). */
    private ClassicColonyMoves.Place pressedPlace = ClassicColonyMoves.Place.NONE;
    private String pressedKey = null;

    /** Whether {@link #pressedUnit} was the selected one before the press (a click: J2). */
    private boolean pressedWasSelected = false;

    /** When the press came (the clock's nanoseconds; a fence unit's menu, +44 frames). */
    private long pressedAt = 0L;

    /** A press elsewhere: where it was (a tile 0-8 or a slot), for a click's move; -1 for none. */
    private int pressedTile = -1, pressedSlot = -1;

    /** The pointer during a drag (canvas): the ghost's hotspot. */
    private int ghostX = -1, ghostY = -1;

    /**
     * Whether the screen waits for the abandoned colony's map (M4.8 target
     * 8: «Ja», the unit on the fence, the map 31 frames later): no input
     * meanwhile.
     */
    private boolean leaving = false;

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
                    ClassicDragGesture.Result result = ClassicDragGesture.Result.NONE;
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        result = gesture.release();
                        if (result == ClassicDragGesture.Result.DROP) dragEnded();
                    }
                    releaseAt(canvasX(e.getX()), canvasY(e.getY()), result);
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
                    if (gesture.dragging()) ghostTo(canvasX(e.getX()), canvasY(e.getY()));
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
     * Otherwise a press on a colonist selects him (M4.8, {@link #pressAt}),
     * and the release acts ({@link #releaseAt}).
     */
    private void onClick(MouseEvent e) {
        pressAt((e.getX() - this.originX) / this.scale,
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
        final int s = Math.max(1, this.scale);
        this.gesture.press(vx, vy, this.originX + vx * s, this.originY + vy * s,
                           canDragAt(vx, vy));
        pressAt(vx, vy);
        releaseAt(vx, vy, this.gesture.release());
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

    /**
     * The release of a press (M4.8): after a drag on a colonist the drop
     * ({@link #drop}); a click on the colonist who was selected opens J2,
     * on a unit at the fence his menu; a click on an empty tile or a
     * building moves the selected colonist there; on the unit of
     * «Vorhandene Einheiten» the press selected, that unit's options box
     * ({@link Units#options}; clip 019: the frame on the press #916, the
     * box 0.6 s later, I: on the release); a mode button's release its
     * panel.
     *
     * @param vx The x.
     * @param vy The y.
     * @param result What the gesture was.
     */
    private void releaseAt(int vx, int vy, ClassicDragGesture.Result result) {
        final Unit u = this.pressedUnit;
        final ClassicColonyMoves.Place from = this.pressedPlace;
        final boolean wasSelected = this.pressedWasSelected;
        final int tile = this.pressedTile, slot = this.pressedSlot;
        this.pressedUnit = null;
        this.pressedPlace = ClassicColonyMoves.Place.NONE;
        this.pressedTile = this.pressedSlot = -1;
        if (u != null) {
            if (!this.gesture.dragging() && isDisplayable()) this.blink.arm();
            repaint();
            if (this.leaving) return;
            if (result == ClassicDragGesture.Result.DROP) {
                drop(u, from, vx, vy);
            } else if (result == ClassicDragGesture.Result.CLICK && figureAt(vx, vy) == u) {
                if (from == ClassicColonyMoves.Place.FENCE) {
                    // A unit at the fence: his job menu (V ×3), 44
                    // frames after the press.
                    final double held = (this.clock.now() - this.pressedAt) / 1e6;
                    jobMenu(u, ClassicColonyJobs.Kind.OUTSIDE, Math.max(
                        ClassicColonyMoves.ms(ClassicColonyMoves.J2_FRAMES),
                        ClassicColonyMoves.ms(ClassicColonyMoves.FENCE_MENU_FRAMES) - held));
                } else if (wasSelected) {
                    jobMenu(u, ClassicColonyJobs.Kind.WORKER,
                            ClassicColonyMoves.ms(ClassicColonyMoves.J2_FRAMES));
                }
            }
            return;
        }
        if (!this.leaving && result == ClassicDragGesture.Result.CLICK
            && this.selectedUnit != null) {
            // A click on an empty tile or a building: the selected
            // colonist goes there (V ×4 for the tile, I for the building).
            final int t = ClassicColonyLayout.tileAt(vx, vy);
            if (tile >= 0 && t == tile) {
                moveSelectedTo(ClassicColonyMoves.Place.TILE, vx, vy);
                return;
            }
            if (slot >= 0 && slotAt(vx, vy) == slot) {
                moveSelectedTo(ClassicColonyMoves.Place.BUILDING, vx, vy);
                return;
            }
        }
        // A mode button's release shows its panel (M4.7; I: on the release).
        if (this.modePressed) {
            this.modePressed = false;
            if (this.mode != this.buttonMode) {
                this.mode = this.buttonMode;
                lastMode = this.mode;
                ClassicFrameRecorder.event("colony-mode", this.mode.word());
            }
            repaint();
        }
        final Unit pressed = this.pressedBand;
        this.pressedBand = null;
        if (pressed == null || this.units == null) return;
        final int i = bandAt(vx, vy);
        if (i < 0 || this.bandUnits.get(i) != pressed) return;
        // «Nach vorne bewegen.» is offered to all but the first of
        // «Vorhandene Einheiten» (clip 019 #959; M4.7: the box is the
        // list's, M2 had it on the figure row's units).
        final int at = ClassicColonyRight.listed(this.colony, this.units::order)
            .indexOf(pressed);
        ClassicFrameRecorder.event("colony-unit", "click " + pressed.getId()
            + " cell=" + at + " state=" + pressed.getState());
        this.units.options(pressed, at == 0);
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
        // The right panel's mode buttons (M4.7): the pressed look at once,
        // the panel at the release.
        final int button = ClassicColonyLayout.modeAt(vx, vy);
        if (button >= 0) {
            this.buttonMode = ClassicColonyRight.Mode.values()[button];
            this.modePressed = true;
            repaint();
            return;
        }
        if (pressRight(vx, vy)) return;
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

        if (this.leaving) return;
        // A colonist in a building, on a tile or at the fence (M4.8): the
        // press selects him and freezes the blink, his hover label goes
        // (dago §0.1, dago-colony2 §4); the white cursor to his tile, else
        // to his figure in the band.
        final Hit h = hitAt(vx, vy);
        if (h != null) {
            this.pressedUnit = h.unit;
            this.pressedPlace = h.place;
            this.pressedKey = h.key;
            this.pressedWasSelected = this.selectedUnit == h.unit;
            this.pressedAt = this.clock.now();
            tileCursor = (h.place == ClassicColonyMoves.Place.TILE) ? h.tile : CURSOR_OFF_TILES;
            this.hovered = this.hoveredTile = this.hoveredBox = -1;
            this.blink.stop();
            select(h.unit);
            return;
        }
        // A figure of the band's row: it is selected (I: the original's
        // press there is not seen; no drag, no menu from the row).
        final int f = ClassicColonyMoves.pick(boundsOf(this.figures), vx, vy);
        if (f >= 0) {
            tileCursor = CURSOR_OFF_TILES;
            select(this.figures.get(f).unit);
            return;
        }
        if (tile >= 0) {
            this.pressedTile = tile;
            repaint();
            return;
        }
        // A building with workplaces (not a placeholder, the harbour, the
        // fortification or the Lagerhaus' slot): a click's target.
        final int slot = slotAt(vx, vy);
        if (slot >= 0 && slot < this.area.slots.size()
            && this.area.slots.get(slot).target != null) {
            this.pressedSlot = slot;
        }
    }

    /** A figure a press can pick and drag: the unit, his place, his tile, his sprite. */
    static final class Hit {
        final Unit unit;
        final ClassicColonyMoves.Place place;
        final int tile;
        final String key;

        Hit(Unit unit, ClassicColonyMoves.Place place, int tile, String key) {
            this.unit = unit;
            this.place = place;
            this.tile = tile;
            this.key = key;
        }
    }

    /**
     * The colonist figure at a canvas point (as the last paint drew them):
     * the workers of the buildings and the units at the fence (in the draw
     * order, the first drawn wins where they overlap, target 10), then the
     * tiles' workers.
     *
     * @param vx The x.
     * @param vy The y.
     * @return The hit, or null.
     */
    Hit hitAt(int vx, int vy) {
        final List<Rectangle> bounds = new ArrayList<>();
        final List<Hit> hits = new ArrayList<>();
        for (ClassicColonyBuildings.Placed p : this.area.slots) {
            final ClassicColonyMoves.Place place = (p.slot.cls == ClassicColonyLayout.SlotClass.FENCE)
                ? ClassicColonyMoves.Place.FENCE : ClassicColonyMoves.Place.BUILDING;
            for (ClassicColonyBuildings.Figure fig : p.figures) {
                if (fig.unit == null) continue;
                bounds.add(fig.bounds());
                hits.add(new Hit(fig.unit, place, -1, fig.key));
            }
        }
        for (ClassicColonyTiles.Cell c : this.tiles.cells) {
            if (c == null || c.worker == null || c.worker.unit == null) continue;
            bounds.add(c.worker.bounds());
            hits.add(new Hit(c.worker.unit, ClassicColonyMoves.Place.TILE, c.index,
                             c.worker.key));
        }
        final int i = ClassicColonyMoves.pick(bounds, vx, vy);
        return (i < 0) ? null : hits.get(i);
    }

    /** @return The unit of the colonist figure at a point ({@link #hitAt}), or null. */
    private Unit figureAt(int vx, int vy) {
        final Hit h = hitAt(vx, vy);
        return (h == null) ? null : h.unit;
    }

    /** @return The figures' rectangles. */
    private static List<Rectangle> boundsOf(List<ClassicColonyBuildings.Figure> figs) {
        final List<Rectangle> out = new ArrayList<>(figs.size());
        for (ClassicColonyBuildings.Figure f : figs) out.add(f.bounds());
        return out;
    }

    /**
     * A press on the right panel's mode (M4.7): in the musket panel a unit
     * of «Vorhandene Einheiten» is selected, the white cursor goes to it and
     * its @COLONYUNIT box comes on the release (clip 019 #916: the frame
     * white on the press, the box 43 frames later; I: on the release; M2
     * had it on the band's units); in the hammer panel a press on KAUFEN or
     * ÄNDERN brings the cursor (I, as dago §3's press), ÄNDERN opens the
     * build list (the first slice's until M4.10), KAUFEN does nothing yet
     * (M4.10: @BUYME0 / @BUYME1); the house panel takes no press.
     *
     * @param vx The x.
     * @param vy The y.
     * @return Whether the press was the right panel's.
     */
    private boolean pressRight(int vx, int vy) {
        if (!ClassicColonyLayout.RIGHT_PANEL.contains(vx, vy) || this.right == null) {
            return false;
        }
        switch (this.mode) {
        case MUSKET: {
            ClassicColonyRight.Listed hit = null;
            for (ClassicColonyRight.Listed l : this.right.units) {
                if (ClassicColonyLayout.unitListFrame(l.cell).contains(vx, vy)) hit = l;
            }
            if (hit != null) {
                this.listSelected = hit.unit;
                tileCursor = CURSOR_LIST;
                if (this.units != null) this.pressedBand = hit.unit;
                repaint();
            }
            return true;
        }
        case HAMMER:
            if (ClassicColonyLayout.BUY_BOX.contains(vx, vy)) {
                tileCursor = CURSOR_BUY;
                repaint();
            } else if (ClassicColonyLayout.CHANGE_BOX.contains(vx, vy)) {
                tileCursor = CURSOR_CHANGE;
                repaint();
                this.freeColClient.getGUI().showBuildQueuePanel(this.colony);
            }
            return true;
        default:
            return true;
        }
    }

    /**
     * The slot of the building area under a point: the first in the draw
     * order (the one drawn underneath) whose sprite rectangle holds it, for
     * the hover label and a press's work target alike (V dago
     * 01-interact §2.2: in the one overlap, M2 inside the harbour W's
     * rectangle, M2 wins, «Waffenarsenal» not «Werft» at (170,72), #4835 →
     * #4840, #5604; the slots are fixed, so this needs no paint;
     * rectangles, not pixels, dago §2.2; clip008: a label does not depend on
     * where in its sprite the pointer is).
     *
     * @param vx The x.
     * @param vy The y.
     * @return Its index in {@link ClassicColonyLayout#SLOTS}, or -1.
     */
    static int slotAt(int vx, int vy) {
        for (int i = 0; i < ClassicColonyLayout.SLOTS.length; i++) {
            if (ClassicColonyLayout.SLOTS[i].bounds().contains(vx, vy)) return i;
        }
        return -1;
    }


    // Moving colonists (M4.8)

    /**
     * Whether the levi rule R2/R3 holds ({@code model.option.originalDropJobs}).
     *
     * @return True if so.
     */
    private boolean originalJobs() {
        final net.sf.freecol.common.model.Specification spec = this.colony.getSpecification();
        try {
            return spec != null
                && spec.getBoolean(net.sf.freecol.common.option.GameOptions.ORIGINAL_DROP_JOBS);
        } catch (RuntimeException e) {
            return false;   // rules without the option: FreeCol's
        }
    }

    /**
     * Where a point of the canvas is for a dropped or a clicked colonist:
     * the fence picture (slot X), a building with workplaces, one of the
     * eight work tiles; anything else is nowhere.
     *
     * @param vx The x.
     * @param vy The y.
     * @return The place.
     */
    ClassicColonyMoves.Place placeAt(int vx, int vy) {
        final int slot = slotAt(vx, vy);
        if (slot >= 0) {
            final ClassicColonyLayout.Slot s = ClassicColonyLayout.SLOTS[slot];
            if (s.cls == ClassicColonyLayout.SlotClass.FENCE) return ClassicColonyMoves.Place.FENCE;
            return (slot < this.area.slots.size() && this.area.slots.get(slot).target != null)
                ? ClassicColonyMoves.Place.BUILDING : ClassicColonyMoves.Place.NONE;
        }
        final int tile = ClassicColonyLayout.tileAt(vx, vy);
        final ClassicColonyTiles.Cell c = (tile < 0) ? null : this.tiles.cell(tile);
        return (c != null && c.colonyTile != null && tile != ClassicColonyLayout.CENTRE_TILE)
            ? ClassicColonyMoves.Place.TILE : ClassicColonyMoves.Place.NONE;
    }

    /** @return The building with workplaces at a point, or null. */
    private Building buildingAt(int vx, int vy) {
        final int slot = slotAt(vx, vy);
        return (slot < 0 || slot >= this.area.slots.size()) ? null
            : this.area.slots.get(slot).target;
    }

    /** @return The work tile at a point, or null. */
    private ColonyTile colonyTileAt(int vx, int vy) {
        final int tile = ClassicColonyLayout.tileAt(vx, vy);
        final ClassicColonyTiles.Cell c = (tile < 0) ? null : this.tiles.cell(tile);
        return (c == null) ? null : c.colonyTile;
    }

    /**
     * Whether a building is full for a colonist (FreeCol's capacity, the
     * original's three, @MORETHANTHREE).
     */
    private static boolean full(Building b, Unit u) {
        return b.getNoAddReason(u)
            == net.sf.freecol.common.model.UnitLocation.NoAddReason.CAPACITY_EXCEEDED;
    }

    /**
     * A drop of a dragged colonist (the drop matrix,
     * {@link ClassicColonyMoves#resolve}).
     *
     * @param u The colonist.
     * @param from Where he was picked.
     * @param vx The drop's x.
     * @param vy Its y.
     */
    private void drop(Unit u, ClassicColonyMoves.Place from, int vx, int vy) {
        final ClassicColonyMoves.Place to = placeAt(vx, vy);
        final Building b = (to == ClassicColonyMoves.Place.BUILDING) ? buildingAt(vx, vy) : null;
        final ColonyTile ct = (to == ClassicColonyMoves.Place.TILE) ? colonyTileAt(vx, vy) : null;
        final boolean same = (b != null && u.getLocation() == b)
            || (ct != null && u.getLocation() == ct);
        final boolean blocked = (b != null && !same && full(b, u))
            || (ct != null && !same && !ClassicColonyJobs.free(ct));
        final ClassicColonyMoves.Action a = ClassicColonyMoves.resolve(from, to, same, blocked,
                                                                       originalJobs());
        final boolean done = act(u, a, b, ct);
        ClassicFrameRecorder.event("colony-drag", u.getId() + " " + from + " -> " + to
            + " " + a + ((done) ? "" : " refused") + " " + outcome(u));
        recordStill("drop " + u.getId());
        if (a == ClassicColonyMoves.Action.ROLE_MENU) {
            jobMenu(u, ClassicColonyJobs.Kind.FENCE_DROP,
                    ClassicColonyMoves.ms(ClassicColonyMoves.J1_FRAMES));
        } else if (a == ClassicColonyMoves.Action.FULL) {
            moreThanThree(ClassicColonyMoves.ms(ClassicColonyMoves.FULL_FRAMES));
        }
    }

    /**
     * A click on an empty tile or a building with the selected colonist
     * (target 2: he goes there with his work, V ×4, #48808, #75610; I for
     * a building, and for a building worker onto a tile: the drop's rule).
     *
     * @param to The tile or the building.
     * @param vx The x.
     * @param vy The y.
     */
    private void moveSelectedTo(ClassicColonyMoves.Place to, int vx, int vy) {
        final Unit u = this.selectedUnit;
        if (u == null) return;
        final ClassicColonyMoves.Place from = placeOf(u);
        if (from == ClassicColonyMoves.Place.NONE) return;
        final Building b = (to == ClassicColonyMoves.Place.BUILDING) ? buildingAt(vx, vy) : null;
        final ColonyTile ct = (to == ClassicColonyMoves.Place.TILE) ? colonyTileAt(vx, vy) : null;
        if (b == null && ct == null) return;
        final boolean same = (b != null && u.getLocation() == b)
            || (ct != null && u.getLocation() == ct);
        final boolean blocked = (b != null && !same && full(b, u))
            || (ct != null && !same && !ClassicColonyJobs.free(ct));
        // A click on a tile, the fence's role menu: none (the menus are J1's).
        final ClassicColonyMoves.Action a = ClassicColonyMoves.resolve(from, to, same, blocked,
                                                                       originalJobs());
        final boolean done = act(u, a, b, ct);
        ClassicFrameRecorder.event("colony-click", u.getId() + " " + from + " -> " + to
            + " " + a + ((done) ? "" : " refused") + " " + outcome(u));
        if (a == ClassicColonyMoves.Action.FULL) {
            moreThanThree(ClassicColonyMoves.ms(ClassicColonyMoves.FULL_FRAMES));
        }
    }

    /** @return Where a unit of this colony stands (building, tile, fence, or nowhere). */
    private ClassicColonyMoves.Place placeOf(Unit u) {
        final Object loc = u.getLocation();
        if (loc instanceof Building && ((Building) loc).getColony() == this.colony) {
            return ClassicColonyMoves.Place.BUILDING;
        }
        if (loc instanceof ColonyTile && ((ColonyTile) loc).getColony() == this.colony) {
            return ClassicColonyMoves.Place.TILE;
        }
        return (loc == this.colony.getTile() && u.isPerson())
            ? ClassicColonyMoves.Place.FENCE : ClassicColonyMoves.Place.NONE;
    }

    /** @return A unit's work as the recorder names it. */
    private static String jobNow(Unit u) {
        final GoodsType wt = u.getWorkType();
        return (u.isInColony() && wt != null) ? wt.getSuffix() : "-" + u.getRole().getSuffix();
    }

    /**
     * What a move left (the recorder): his work, his place, the
     * warehouse's muskets, tools and horses.
     *
     * @param u The colonist.
     * @return The words.
     */
    private String outcome(Unit u) {
        final net.sf.freecol.common.model.Specification spec = this.colony.getSpecification();
        final StringBuilder sb = new StringBuilder("job=").append(jobNow(u)).append(" at=")
            .append((u.getLocation() == null) ? "-" : u.getLocation().getId());
        for (String g : new String[] { "muskets", "tools", "horses" }) {
            final GoodsType gt = (spec == null) ? null : spec.getGoodsType("model.goods." + g);
            if (gt != null) sb.append(' ').append(g).append('=').append(this.colony.getGoodsCount(gt));
        }
        return sb.toString();
    }

    /**
     * Do a move's action through the real controller (its server calls,
     * as the standard UI's {@code ColonyPanel} makes them): {@code work}
     * into a building or onto a tile (it claims a tile and confirms an
     * education given up), then for the levi rule the Farmer's work or the
     * work he had ({@code changeWorkType}, R2/R3).  The selection stays on
     * him (V #97000, dago-colony2 §4); the white cursor goes to his tile,
     * else to his figure in the band (I).
     *
     * @param u The colonist.
     * @param a The action.
     * @param b The target building, or null.
     * @param ct The target tile, or null.
     * @return True if he is now where the action puts him (or it moves nothing).
     */
    private boolean act(Unit u, ClassicColonyMoves.Action a, Building b, ColonyTile ct) {
        final InGameController igc = igc();
        boolean ok = true;
        switch (a) {
        case BUILDING_JOB:
            ok = igc.work(u, b);
            break;
        case TILE_FARMER: {
            final GoodsType farm = ClassicColonyJobs.goodsOf(this.colony.getSpecification(), 0);
            ok = igc.work(u, ct);
            if (ok && farm != null && u.getWorkType() != farm) ok = igc.changeWorkType(u, farm);
            break;
        }
        case TILE_KEEP_JOB: {
            final GoodsType had = u.getWorkType();
            ok = igc.work(u, ct);
            if (ok && had != null && u.getWorkType() != had) ok = igc.changeWorkType(u, had);
            break;
        }
        case TILE_FREECOL:
            ok = igc.work(u, ct);
            break;
        default:
            break;
        }
        if (ct != null && u.getLocation() == ct) {
            final int at = this.tiles.placeOfTile(ct);
            if (at >= 0) tileCursor = at;
        } else if (a != ClassicColonyMoves.Action.NOTHING) {
            tileCursor = CURSOR_OFF_TILES;
        }
        select(u);
        refresh();
        return ok;
    }

    /**
     * Ask a box over this screen ({@link Units#ask}); without the game's
     * boxes (a test) nothing is asked.
     *
     * @param r The box, or null.
     * @return The row, or {@link ClassicAdvisorBox.Bar#DISMISSED}.
     */
    private int ask(ClassicAdvisorBox.Request r) {
        if (r == null || this.units == null) return ClassicAdvisorBox.Bar.DISMISSED;
        this.gesture.cancel();
        // No blink while a box is open (dago §0.6, dago-colony2 §3.4).
        this.blink.stop();
        try {
            return this.units.ask(r);
        } finally {
            if (isDisplayable()) {
                this.blinkOff = false;
                this.blink.arm();
            }
        }
    }

    /**
     * A job menu and its picks (J2, a fence unit's, J1; their pages):
     * asked until a pick that is no page change, or none.
     *
     * @param u The colonist.
     * @param kind Which menu.
     * @param delayMs When the first comes after now.
     */
    private void jobMenu(Unit u, ClassicColonyJobs.Kind kind, double delayMs) {
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        int page = 1;
        double delay = delayMs, chain = -1.0;
        for (int guard = 0; guard < 16; guard++) {
            final ClassicColonyJobs.Menu m = ClassicColonyJobs.menu(t, this.colony, u, kind, page);
            if (m == null) {
                ClassicFrameRecorder.event("colony-job", u.getId() + " no texts");
                return;
            }
            final int answer = ask(ClassicColonyJobs.request(t, m, delay, chain));
            if (answer < 0 || answer >= m.rows.size()) {
                ClassicFrameRecorder.event("colony-job", u.getId() + " " + kind + " " + page
                    + " none");
                refresh();
                return;
            }
            final ClassicColonyJobs.Row row = m.rows.get(answer);
            ClassicFrameRecorder.event("colony-job", u.getId() + " " + kind + " " + page
                + " " + row.pick + " " + row.job);
            if (row.pick == ClassicColonyJobs.Pick.MORE) {
                // The other page, 3 frames after the pick (V #7188 → #7191).
                page = 3 - page;
                delay = 0.0;
                chain = ClassicColonyMoves.ms(ClassicColonyMoves.MORE_FRAMES);
                continue;
            }
            pick(u, kind, row);
            return;
        }
    }

    /**
     * What a menu's row does (M4.8 targets 3-6, 8).
     *
     * @param u The colonist.
     * @param kind The menu.
     * @param row The row picked.
     */
    private void pick(Unit u, ClassicColonyJobs.Kind kind, ClassicColonyJobs.Row row) {
        final InGameController igc = igc();
        final net.sf.freecol.common.model.Specification spec = this.colony.getSpecification();
        select(u);
        switch (row.pick) {
        case CLEAR:
            // FreeCol's question in the original's words (@LOBOTOMIZE,
            // ClassicGUI.modalConfirmDialog).
            igc.clearSpeciality(u);
            break;
        case JOB:
            pickJob(u, row.job);
            break;
        case ROLE:
            pickRole(u, ClassicColonyJobs.roleOf(spec, row.job), row.count);
            break;
        default:
            break;
        }
        select(u);
        refresh();
        ClassicFrameRecorder.event("colony-picked", u.getId() + " " + row.pick + " "
            + row.job + " " + outcome(u));
        recordStill("picked " + u.getId());
    }

    /**
     * A work picked (target 3): a tile's work for a tile worker on the
     * same tile (V #48712: no move), for anyone else onto the best free
     * tile (V #4690, #20760, #97000); a building's work into that building
     * (V #37, #42, #71), @MORETHANTHREE where it is full (I).
     *
     * @param u The colonist.
     * @param job The work.
     */
    private void pickJob(Unit u, int job) {
        final InGameController igc = igc();
        final GoodsType g = ClassicColonyJobs.goodsOf(this.colony.getSpecification(), job);
        if (job < ClassicColonyLayout.TILE_JOBS) {
            if (g == null) return;
            if (u.getLocation() instanceof ColonyTile
                && ((ColonyTile) u.getLocation()).getColony() == this.colony) {
                if (u.getWorkType() != g) igc.changeWorkType(u, g);
                return;
            }
            final ColonyTile ct = ClassicColonyJobs.bestFreeTile(this.colony, u.getType(), g);
            if (ct == null) return;
            if (igc.work(u, ct) && u.getWorkType() != g) igc.changeWorkType(u, g);
            if (u.getLocation() == ct) {
                final int at = this.tiles.placeOfTile(ct);
                if (at >= 0) tileCursor = at;
            }
            return;
        }
        final Building b = ClassicColonyJobs.buildingFor(this.colony, job, g, u.getType());
        if (b == null || u.getLocation() == b) return;
        if (full(b, u)) {
            moreThanThree(ClassicColonyMoves.ms(ClassicColonyMoves.FULL_FRAMES));
            return;
        }
        igc.work(u, b);
        tileCursor = CURSOR_OFF_TILES;
    }

    /**
     * A role picked (targets 4, 5, 8): a colonist in the colony goes
     * outside with its goods from the warehouse (V: Soldat 50 → 0 muskets
     * #7323, Pionier 75 → 15 tools #121100), the last one only after
     * @ABANDON's «Ja», which gives up the colony; a unit at the fence takes
     * the role there (the goods of its old one back, I).
     *
     * @param u The colonist.
     * @param role The role.
     * @param count Its count.
     */
    private void pickRole(Unit u, net.sf.freecol.common.model.Role role, int count) {
        if (role == null) return;
        final InGameController igc = igc();
        if (!u.isInColony()) {
            if (role != u.getRole() || count != u.getRoleCount()) {
                igc.equipUnitForRole(u, role, (role.isDefaultRole()) ? 0 : count);
            }
            return;
        }
        if (this.colony.getUnitCount() <= 1) {
            if (!askAbandon()) return;
            leave(u, role, count);
            if (u.isInColony()) return;
            // The unit on the fence in its role, the map without the
            // colony 31 frames after «Ja» (V #35577, #35605).
            this.leaving = true;
            ClassicFrameRecorder.event("colony-abandon", this.colony.getName() + " yes");
            this.later.run((int) Math.round(ClassicColonyMoves.ms(
                ClassicColonyMoves.ABANDON_MAP_FRAMES)), this::abandonNow);
            return;
        }
        leave(u, role, count);
    }

    /** A colonist leaves the colony in a role: outside, then equipped. */
    private void leave(Unit u, net.sf.freecol.common.model.Role role, int count) {
        final InGameController igc = igc();
        if (!igc.putOutsideColony(u)) return;
        if (!role.isDefaultRole() && u.getLocation() == this.colony.getTile()) {
            igc.equipUnitForRole(u, role, count);
        }
        tileCursor = CURSOR_OFF_TILES;
    }

    /** The colony with nobody in it is given up and the screen goes (target 8). */
    void abandonNow() {
        if (this.colony.isDisposed() || this.colony.getUnitCount() > 0) {
            this.leaving = false;
            return;
        }
        final boolean done = igc().abandonColony(this.colony);
        ClassicFrameRecorder.event("colony-abandon", this.colony.getName()
            + ((done) ? " done" : " refused"));
        this.leaving = false;
        if (done) close();
    }

    /** @return Whether the screen waits for the abandoned colony's map (tests). */
    boolean leaving() {
        return this.leaving;
    }

    /** Something run once after a time (the abandoned colony's map). */
    interface Later {

        /**
         * @param ms The time.
         * @param r What runs then, on the event thread.
         */
        void run(int ms, Runnable r);
    }

    /** {@link Later} by a Swing timer; tests run it themselves. */
    private Later later = (ms, r) -> {
        final javax.swing.Timer t = new javax.swing.Timer(ms, e -> r.run());
        t.setRepeats(false);
        t.start();
    };

    /** @param l What runs things later (tests: at once, or recorded). */
    void setLater(Later l) {
        this.later = l;
    }

    /**
     * @ABANDON (another colony exists) or @ABANDON2 (it is the only one;
     * SRC: its text warns of the King's right after 1600), 8 frames after
     * the menu closed, MSS5 over it, the bar and Escape on «Niemals!»
     * (V #35379, dago-colony2).
     *
     * @return True for «Ja».
     */
    private boolean askAbandon() {
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        final Player owner = this.colony.getOwner();
        final String section = (owner != null && owner.getColonyList().size() <= 1)
            ? "ABANDON2" : "ABANDON";
        final ClassicText.Message m = (t == null) ? null : t.message(section);
        final java.util.Map<String, String> values = new java.util.HashMap<>();
        values.put("STRING0", this.colony.getName());
        final ClassicAdvisorBox.Builder b = ClassicAdvisorBox.fromGameText(section, m, values);
        if (b == null) return false;
        // 8 frames with the portrait's palette lead (3) the layer adds.
        final ClassicAdvisorBox.Request r = b.portrait(ClassicAdvisorBox.Portrait.COLONIST)
            .chain(ClassicColonyMoves.ms(ClassicColonyMoves.ABANDON_FRAMES
                                         - ClassicAdvisorLayer.PALETTE_LEAD_FRAMES))
            .stopgap(this.colony.getName(), null).build();
        final int answer = ask(r);
        ClassicFrameRecorder.event("colony-abandon", this.colony.getName() + " " + section
            + " answer=" + answer);
        return answer == 0;
    }

    /**
     * @MORETHANTHREE: a building full, nothing moves (V dago-colony2
     * #10945: 32 frames after the drop, MSS5 over it, no rows).
     *
     * @param delayMs When it comes.
     */
    private void moreThanThree(double delayMs) {
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        final ClassicAdvisorBox.Builder b = ClassicAdvisorBox.fromGameText("MORETHANTHREE",
            (t == null) ? null : t.message("MORETHANTHREE"), null);
        if (b == null) return;
        // With the portrait's palette lead (3 frames) the layer adds.
        ask(b.portrait(ClassicAdvisorBox.Portrait.COLONIST)
            .openDelay(Math.max(0.0, delayMs - ClassicAdvisorLayer.PALETTE_LEAD_MS))
            .stopgap(this.colony.getName(), null).build());
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

    /** @return The hovered slot's index in {@link ClassicColonyLayout#SLOTS} (tests), or -1. */
    int hovered() {
        return this.hovered;
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
     * dragged: a colonist in a building, on a tile or at the fence (M4.8;
     * the goods come with M4.9).
     *
     * @param vx The x.
     * @param vy The y.
     * @return True for a colonist's figure.
     */
    private boolean canDragAt(int vx, int vy) {
        return !this.leaving && ClassicColonyLayout.modeAt(vx, vy) < 0
            && !ClassicColonyLayout.RIGHT_PANEL.contains(vx, vy) && hitAt(vx, vy) != null;
    }

    /**
     * The pointer moved during a drag: the ghost follows (dago §0.2,
     * every frame).
     *
     * @param vx The canvas x.
     * @param vy The canvas y.
     */
    private void ghostTo(int vx, int vy) {
        if (vx == this.ghostX && vy == this.ghostY) return;
        this.ghostX = vx;
        this.ghostY = vy;
        repaint();
    }

    /**
     * The drag's ghost (V dago §0.2, 3 drops; dago-colony2 #8890): the
     * dragged colonist's own figure at the hotspot − (1, 8) with a black
     * copy of its silhouette 2 px to its left; the source keeps its figure.
     */
    private void paintGhost(Graphics2D g) {
        if (!this.gesture.dragging() || this.pressedKey == null || this.ghostX < 0) return;
        final BufferedImage s = this.buildingArt.image(this.pressedKey);
        if (s == null) return;
        final int x = this.ghostX + ClassicColonyMoves.GHOST_DX;
        final int y = this.ghostY + ClassicColonyMoves.GHOST_DY;
        final BufferedImage sh = this.buildingArt.silhouette(this.pressedKey,
            this.chrome.rgb(ClassicColonyLayout.INK_BLACK));
        if (sh != null) g.drawImage(sh, x + ClassicColonyLayout.SHADOW_DX, y, null);
        g.drawImage(s, x, y, null);
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
        final int[] p = this.gesture.pressPoint();
        this.ghostX = p[0];
        this.ghostY = p[1];
        this.hovered = this.hoveredTile = this.hoveredBox = -1;
        firePropertyChange(ClassicPointer.DRAGGING, false, true);
        repaint();
    }

    /** A drag ended: the blink starts again (I: a new phase), the arrow comes back. */
    private void dragEnded() {
        this.ghostX = this.ghostY = -1;
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
     * way, the release (a colonist's drag since M4.8; elsewhere a press at
     * a and a release at b).
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
        if (this.gesture.dragging()) ghostTo(bx, by);
        final ClassicDragGesture.Result result = this.gesture.release();
        if (result == ClassicDragGesture.Result.DROP) dragEnded();
        releaseAt(bx, by, result);
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

        // The numbers, as SPIEL "Koloniebericht-Optionen" switches them
        // (W14): the count labels of the buildings' stacked rows (M4.3) and
        // of the tiles', the band's and the right panel's house rows (M4.4,
        // M4.5, M4.7; I: the options' meaning in the original).
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
        // No hover label during a press on a colonist or a drag (dago §0.1).
        if (this.pressedUnit == null && !this.gesture.dragging()) paintHover(g);
        paintGhost(g);
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
        // The right panel (M4.7) with the white cursor's place.
        final String rightDrawn = this.right + " cursor=" + cursorName();
        final boolean side = !rightDrawn.equals(this.rightLogged);
        if (!area && !map && !left && !dock && !side) return;
        this.rightLogged = rightDrawn;
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
        if (side) {
            ClassicFrameRecorder.event("colony-right",
                name + " " + this.colony.getId() + " " + rightDrawn);
        }
        ClassicFrameRecorder.still(name, canvas());
    }

    /** @return Where the white cursor stands (the recorder): a tile's name, «figure», «carrier». */
    private static String cursorName() {
        if (tileCursor >= 0) return ClassicColonyLayout.TILE_NAMES[tileCursor];
        switch (tileCursor) {
        case CURSOR_CARRIER: return "carrier";
        case CURSOR_LIST: return "list";
        case CURSOR_BUY: return "buy";
        case CURSOR_CHANGE: return "change";
        default: return "figure";
        }
    }

    /**
     * The building area (M4.2, M4.3; {@link ClassicColonyBuildings}): the
     * 15 slots with their families' sprites or placeholders, the rows,
     * the workers and the units outside, clipped to x 0-198, y 8-127; the
     * figures are the presses' and drags' sources ({@link #hitAt}, M4.8).
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
        if (u != null) lastSelected.put(this.colony.getId(), u.getId());
        repaint();
    }

    /**
     * Forget the colonies' last selections (the game view's teardown;
     * tests: each frame's state a fresh visit).
     */
    static void forgetSelections() {
        lastSelected.clear();
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
     * The workers are the presses' and drags' sources, the eight outer
     * cells their targets ({@link #hitAt}, {@link #placeAt}, M4.8).
     */
    private void paintWorkTiles(Graphics2D g) {
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
     * @param tile The tile, 0-8, {@link #CURSOR_OFF_TILES},
     *     {@link #CURSOR_CARRIER}, {@link #CURSOR_LIST}, {@link #CURSOR_BUY} or
     *     {@link #CURSOR_CHANGE}.
     */
    static void setTileCursor(int tile) {
        tileCursor = (tile >= 0 && tile < ClassicColonyLayout.TILE_COUNT) ? tile
            : (tile == CURSOR_CARRIER || tile == CURSOR_LIST || tile == CURSOR_BUY
               || tile == CURSOR_CHANGE) ? tile : CURSOR_OFF_TILES;
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
     * right panel (M4.7: the mode buttons and the shown mode's panel), and
     * the original's warehouse row along the bottom with «Abb» over the
     * exit (M4.1).
     */
    private void paintBand(Graphics2D g) {
        ClassicColonyChrome.paintBand(g, this.chrome);
        this.bandUnits.clear();
        this.bandBounds.clear();
        paintPopulation(g);
        paintPort(g);
        paintRight(g);
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
     * a figure selects it (as on a building or tile); the @COLONYUNIT box of
     * a unit standing in the colony is the musket panel's since M4.7
     * ({@link #pressRight}), the artillery, a treasure and the rest stand
     * only there; a wagon train stands in the port since M4.6.
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

        final Tile tile = this.colony.getTile();
        if (tile == null) return;
        // The colonists, then the persons outside (M4.7: the artillery, a
        // treasure and the rest only in the musket panel's «Vorhandene
        // Einheiten», V clip 019 #1290; the ships and the wagon train in
        // the port, M4.6).
        final List<ClassicColonyBuildings.Figure> figures
            = ClassicColonyBuildings.figureRow(this.buildingArt, this.rowOrder, outside());
        this.figures = figures;
        // The white cursor on the selected one's figure (it blinks: only
        // then is the blink asked).
        // During a press on a colonist or a drag his figure's frame is
        // green (dago-colony2 #8886, #10718), the white cursor after it.
        boolean onFigure = false;
        if (tileCursor == CURSOR_OFF_TILES && this.selectedUnit != null
            && this.pressedUnit == null && !this.gesture.dragging()) {
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
        // Every figure is selected by a press ({@link #pressAt}); the
        // @COLONYUNIT box of a person outside is the musket panel's since
        // M4.7 (spec §0.1 decision 7), his job menu the fence figure's
        // (M4.8).
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
        // Another game's rows are dropped, so no old game is kept (as
        // ClassicColonyPort's holds' order).
        for (Object[] e : figureOrders.values()) {
            if (e[0] != game) {
                figureOrders.clear();
                break;
            }
        }
        final Object[] was = figureOrders.get(this.colony.getId());
        final List<Unit> order = (was != null && was[0] == game && (Integer) was[1] == turn)
            ? ClassicColonyBuildings.keepOrder((List<Unit>) was[2], byJob) : byJob;
        figureOrders.put(this.colony.getId(), new Object[] { game, turn, order });
        return order;
    }

    /**
     * Forget the figure rows' orders (the game view's teardown; tests: each
     * frame's state is a fresh visit).
     */
    static void forgetFigureOrders() {
        figureOrders.clear();
    }

    /** @return How many colonies' figure rows are remembered (tests: one game's only). */
    static int rememberedFigureOrders() {
        return figureOrders.size();
    }

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
            // The scale and origin a paint of this size would set, so that
            // real mouse events land where they would (part U's drags).
            if (getWidth() >= VW && getHeight() >= VH) {
                this.scale = Math.max(1, Math.min(getWidth() / VW, getHeight() / VH));
                this.originX = (getWidth() - VW * this.scale) / 2;
                this.originY = (getHeight() - VH * this.scale) / 2;
            }
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

    /**
     * The right panel (M4.7, {@link ClassicColonyRight}): the three mode
     * buttons, the pressed one sunk; the house rows (what the colony makes
     * and its shortfalls crossed), «Vorhandene Einheiten» (the units on the
     * tile as the map draws them, the selected one framed, white under the
     * cursor in its on half), or the hammer panel (the build, KAUFEN and
     * ÄNDERN, the hammers, «(Benötigt …)»; the cursor on a box inverts it in
     * its on half).  The musket panel's units are press targets for their
     * box ({@link #pressRight}).
     */
    private void paintRight(Graphics2D g) {
        final ClassicText text = ClassicText.load(ClassicPackFiles.runtime());
        final ClassicColonyRight.Model m = ClassicColonyRight.model(this.colony, text,
            this.buildingArt, this.buttonMode, this.mode,
            (this.units == null) ? null : this.units::order, this.listSelected);
        this.right = (this.rightAdjust == null) ? m : this.rightAdjust.apply(m);
        final ClassicColonyRight.Cursor cursor;
        switch (tileCursor) {
        case CURSOR_LIST:
            cursor = (this.right.selected == null) ? ClassicColonyRight.Cursor.NONE
                : ClassicColonyRight.Cursor.LIST;
            break;
        case CURSOR_BUY:
            cursor = ClassicColonyRight.Cursor.BUY;
            break;
        case CURSOR_CHANGE:
            cursor = ClassicColonyRight.Cursor.CHANGE;
            break;
        default:
            cursor = ClassicColonyRight.Cursor.NONE;
            break;
        }
        // The blink is asked only where the cursor stands on this panel's
        // shown mode (else its toggles cost no repaint).
        final boolean blinks = (cursor == ClassicColonyRight.Cursor.LIST
                                && this.mode == ClassicColonyRight.Mode.MUSKET)
            || (cursor != ClassicColonyRight.Cursor.NONE
                && cursor != ClassicColonyRight.Cursor.LIST
                && this.mode == ClassicColonyRight.Mode.HAMMER);
        final boolean on = blinks && !blinkOff();
        ClassicColonyRight.paint(g, this.chrome, this.buildingArt, this.tiny, this.right,
                                 cursor, on, this.goodsTerrainNumbers);
        for (ClassicColonyRight.Listed l : this.right.units) {
            this.bandUnits.add(l.unit);
            this.bandBounds.add(ClassicColonyLayout.unitListFrame(l.cell));
        }
    }

    /** @return The right panel as last painted (tests). */
    ClassicColonyRight.Model right() {
        return this.right;
    }

    /**
     * Change the right panel's model before it is drawn (the golden tests:
     * the clip's amounts where FreeCol's rules make others).
     *
     * @param f The change, or null.
     */
    void setRightAdjust(java.util.function.UnaryOperator<ClassicColonyRight.Model> f) {
        this.rightAdjust = f;
        repaint();
    }

    /**
     * How the screen was opened (M4.7 target 6, spec §0.1 decision 8): the
     * right panel's mode from it and the last visit's
     * ({@link ClassicColonyRight#modeAtOpening}); after a building's @BUILT
     * zoom the white cursor on ÄNDERN (V ×3; the turn boxes' zoom rows come
     * with M4.11).
     *
     * @param how How it was opened.
     */
    void opened(ClassicColonyRight.Opening how) {
        this.mode = this.buttonMode = ClassicColonyRight.modeAtOpening(how, lastMode);
        lastMode = this.mode;
        if (how == ClassicColonyRight.Opening.BUILT_BUILDING) tileCursor = CURSOR_CHANGE;
        // Who is selected (M4.8 target 9): the founder at a founding, else
        // the one selected last here, else the row's first figure.
        final List<Unit> row = new ArrayList<>(figureOrder());
        row.addAll(outside());
        final List<Unit> inside = this.colony.getUnitList();
        final Unit founder = (how == ClassicColonyRight.Opening.FOUNDING && inside.size() == 1)
            ? inside.get(0) : null;
        final String lastId = lastSelected.get(this.colony.getId());
        Unit last = null;
        for (Unit u : row) if (u.getId().equals(lastId)) last = u;
        final Unit chosen = ClassicColonyMoves.openingSelection(row, founder, last);
        if (chosen != null) select(chosen);
        ClassicFrameRecorder.event("colony-select", this.colony.getId() + " "
            + ((chosen == null) ? "none" : chosen.getId())
            + ((chosen != null && chosen == founder) ? " founder"
               : (chosen != null && chosen == last) ? " last" : " first"));
        repaint();
    }

    /** @return The right panel's mode shown (tests, the recorder). */
    ClassicColonyRight.Mode mode() {
        return this.mode;
    }

    /**
     * Show a mode at once, its button pressed (tests: a clip's mode).
     *
     * @param m The mode.
     */
    void setMode(ClassicColonyRight.Mode m) {
        this.mode = this.buttonMode = m;
        lastMode = m;
        repaint();
    }

    /** @return The mode the last colony visit ended in (global). */
    static ClassicColonyRight.Mode lastMode() {
        return lastMode;
    }

    /**
     * Forget the last visit's mode (tests: each frame's state a fresh
     * session).
     *
     * @param m The mode the next screen opens in.
     */
    static void setLastMode(ClassicColonyRight.Mode m) {
        lastMode = (m == null) ? ClassicColonyRight.Mode.HOUSE : m;
    }

    /**
     * Select a unit of «Vorhandene Einheiten» as a press on it does (tests).
     *
     * @param u The unit, or null for the first.
     */
    void selectListed(Unit u) {
        this.listSelected = u;
        repaint();
    }
}
