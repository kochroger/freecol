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
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.MenuSelectionManager;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

import net.sf.freecol.FreeCol;
import net.sf.freecol.client.ClientOptions;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.control.PreGameController;
import net.sf.freecol.client.control.SoundController;
import net.sf.freecol.client.gui.ChoiceItem;
import net.sf.freecol.client.gui.action.ActionManager;
import net.sf.freecol.client.gui.DialogHandler;
import net.sf.freecol.client.gui.GUI;
import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.client.gui.LoadingSavegameInfo;
import net.sf.freecol.client.gui.FontLibrary;
import net.sf.freecol.client.gui.panel.FreeColImageBorder;
import net.sf.freecol.client.gui.panel.FreeColPanel;
import net.sf.freecol.common.FreeColException;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.i18n.NameCache;
import net.sf.freecol.common.io.FreeColModFile;
import net.sf.freecol.common.model.AbstractUnit;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.DiplomaticTrade;
import net.sf.freecol.common.model.DiplomaticTrade.TradeStatus;
import net.sf.freecol.common.model.Direction;
import net.sf.freecol.common.model.Europe;
import net.sf.freecol.common.model.FoundingFather;
import net.sf.freecol.common.model.FreeColObject;
import net.sf.freecol.common.model.FreeColGameObject;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.HighScore;
import net.sf.freecol.common.model.IndianNationType;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.Location;
import net.sf.freecol.common.model.ModelMessage;
import net.sf.freecol.common.model.Monarch.MonarchAction;
import net.sf.freecol.common.model.Game.LogoutReason;
import net.sf.freecol.common.model.Nation;
import net.sf.freecol.common.model.NationOptions;
import net.sf.freecol.common.model.NationOptions.NationState;
import net.sf.freecol.common.model.NationSummary;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Topology;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.option.BooleanOption;
import net.sf.freecol.common.option.MapGeneratorOptions;
import net.sf.freecol.common.resources.ImageCache;
import net.sf.freecol.common.resources.ResourceManager;
import net.sf.freecol.server.FreeColServer;


/**
 * A primitive, original-Colonization-style view for FreeCol.
 *
 * This is an alternative implementation of the {@link GUI} view facade, selected
 * with the {@code --classic} command line option (see
 * {@link net.sf.freecol.client.FreeColClient}).  Because the base {@code GUI}
 * class is a fully functional no-op implementation (used in headless mode), this
 * subclass only needs to override the methods it actually implements; everything
 * else degrades gracefully to a no-op.  Screens are therefore added incrementally,
 * one {@code GUI} method at a time.
 *
 * The window opens on the original title screen and main menu
 * ({@link ClassicMainMenuPanel}); a started or loaded game replaces it with
 * the map and HUD ({@link #reconnectGUI}), and leaving the game brings the
 * title back ({@link #showMainPanel}).  See README.md in this package and
 * CLASSIC_UI_PLAN.md for the screens done so far.
 */
public class ClassicGUI extends GUI {

    private static final Logger logger = Logger.getLogger(ClassicGUI.class.getName());

    /** The main application window ({@code frameState.frame}). */
    private JFrame frame;

    /**
     * The main window with its full-screen / windowed state (EDT only), see
     * {@link ClassicFrame}: created by {@link #startGUI}, or adopted from
     * the early start-up window ({@link ClassicStartupScreen}).  Null before.
     */
    private ClassicFrame frameState;

    /** The Alt+Enter / Alt+F4 dispatcher and key tracker, once installed. */
    private FrameKeys frameKeys;

    /** Whether {@link #closeRequested} is asking "really quit?" right now. */
    private boolean closeAsked = false;

    /** The colony screen's window, while one is open (see {@link #showColonyPanel}). */
    private JFrame colonyFrame;

    /** The colony panel inside {@link #colonyFrame}, kept so it can be repainted. */
    private ClassicColonyPanel colonyPanel;

    /** The build-queue screen's window, while one is open (see {@link #showBuildQueuePanel}). */
    private JFrame buildQueueFrame;

    /** The in-game canvas (strip, map, panel), while a game is shown. */
    private ClassicHudPane hudPane;

    /** The painted menu strip of {@link #hudPane}, while a game is shown. */
    private ClassicMenuStrip menuStrip;

    /** The Europe screen's window, while one is open (see {@link #showEuropePanel}). */
    private JFrame europeFrame;

    /** The Europe panel inside {@link #europeFrame}, kept so it can be repainted. */
    private ClassicEuropePanel europePanel;

    /** When Europe was last asked for and is not open yet (0: not), clock ns. */
    private long europeAskedAt = 0L;

    /** The turn Europe was last asked for in (W13 guard; -1: none). */
    private int europeShownTurn = -1;

    /** Our voyages: the ships at sea and the arrivals (W13). */
    final ClassicVoyages voyages = new ClassicVoyages();

    /** The arrival chain running now, or null (W13). */
    private ClassicVoyages.Chain voyageChain = null;

    /** The arrival chain's next step, the timed band's end, the Europe tip. */
    private ClassicOneShot voyageTimer = null, bandTimer = null, tipTimer = null;

    /** The report screen's window, while one is open (see {@link #showReportColonyPanel}). */
    private JFrame reportFrame;

    /**
     * The in-game map view, created lazily when a game starts (see
     * {@link #reconnectGUI}).  Null before then (title-screen placeholder).
     * Owns the classic view state (view mode, focus, selected tile, active
     * unit); this class delegates the corresponding {@code GUI} methods to it.
     * A test puts its own in (the view changes, {@code ClassicGUISeamTest}).
     */
    ClassicMapViewer mapViewer;

    /**
     * The true terrain of the fog ring (M1c design 10 §5, W6d): in single
     * player the server's map, read only, for the terrain composer alone
     * (fringe, blend, coast bits).  Created with the map viewer in
     * {@link #reconnectGUI}, dropped in {@link #teardownInGame}; null
     * without a game view.  EDT only.
     */
    private ClassicTerrainOracle terrainOracle;

    /**
     * The water cycling (M1c design 10 §7, W6c): palette entries 120-127
     * one step every 575.05 ms, the map viewer its listener.  Created with
     * the map viewer in {@link #reconnectGUI} and started with the first
     * in-game view, closed in {@link #teardownInGame}; null without a game
     * view.  It runs under boxes, menus and the Europe screen; a woodcut
     * (W9) holds it ({@link ClassicWaterCycle#hold}).  EDT only.
     */
    private ClassicWaterCycle waterCycle;

    /**
     * The right-hand info / orders panel (Phase 2 HUD), created alongside the
     * map viewer in {@link #reconnectGUI}.  Repainted whenever the view state or
     * model changes so it tracks the active unit / selected tile / treasury.
     */
    private ClassicInfoPanel infoPanel;

    /**
     * The end of turn and the hand-over to the next unit (build spec W5),
     * created with the in-game HUD ({@link #installInGameHud}) and dropped
     * with it ({@link #teardownInGame}); null without a game view.  A test
     * puts its own in (the blink's hold, the view toggle).
     */
    ClassicTurnFlow turnFlow;

    /**
     * The original's unit cycle (master plan W5f): which unit comes at the
     * turn start and at every hand-over, the goto units at their place in
     * it, the visits.  Cleared with each game view.
     */
    final ClassicUnitCycle unitCycle = new ClassicUnitCycle();

    /**
     * The unit the destination list sent off (G, R2) while the controller
     * moves it, until the action is done; else null
     * ({@link #landGotoRunning}).
     */
    private Unit keyGotoUnit = null;

    /** The turn flow's 50-ms poll (the turn indicator, W5c), with it. */
    private javax.swing.Timer turnPoll;

    /**
     * The title screen and main menu (EDT only).  Created by
     * {@link #startGUI}, it is the frame's content pane whenever no game is
     * shown: passive while a game loads, live after {@link #showMainPanel}.
     */
    private ClassicMainMenuPanel mainMenuPanel;

    /**
     * The nation chosen for the single-player game being started, applied in
     * {@link #showStartGamePanel} (the server ignores the nation at login and
     * hands out the first free one); null = keep the server's choice.
     */
    private String pendingNationId;

    /**
     * Whether the single-player start under way was asked for by the title
     * screen ({@link #startNewWorldGame}: the NEUE WELT chain or its
     * chain-less defaults), as opposed to FreeCol's own {@code --fast} start,
     * which calls {@code startSinglePlayerGame} itself (FreeCol.java:1619 ->
     * FreeColClient) and must never stop on the first scene (it is the
     * scripted smoke path).  Set right before {@code startSinglePlayerGame},
     * consumed by {@link #showStartGamePanel}, cleared on every failure path
     * and by {@link #loadSavedGame}.  Volatile for the same reason as
     * {@link #firstScenePending}.
     */
    private volatile boolean newWorldStartRequested = false;

    /**
     * Whether the game being started is a FRESH game from the title screen,
     * so its first view opens on the original's first scene (the admiral,
     * {@link ClassicFirstScene}).  Set in {@link #showStartGamePanel} -- the
     * engine path every fresh single-player game takes, never a loaded save
     * -- and only when {@link #newWorldStartRequested} says the title screen
     * asked for it (the NEUE WELT chain, the chain-less defaults; never
     * {@code --fast}); consumed by the HUD build in {@link #reconnectGUI};
     * cleared by every way back to the title or into a load
     * ({@link #loadSavedGame}, {@link #prepareShowingMainMenu},
     * {@link #teardownInGame}, {@link #showMainPanel}).  Volatile: the
     * login reply that reaches showStartGamePanel normally runs on the EDT,
     * but nothing guarantees it.
     */
    private volatile boolean firstScenePending = false;

    /** Whether the first scene is on screen (EDT only). */
    private boolean sceneShowing = false;

    /**
     * Engine notices that arrived while the first scene was pending or up,
     * in arrival order, shown after it is dismissed (EDT only).
     */
    private final List<HeldMessages> heldMessages = new ArrayList<>();

    /** The layer that shows the first scene, created on first use. */
    private ClassicHudOverlay hudOverlay;

    /** Persistent image cache, shared by the image libraries. */
    private final ImageCache imageCache;

    /**
     * The image library used to resolve images (e.g. the order-button icons
     * loaded by the {@code FreeColAction}s during construction).  Even at the
     * Phase 0 scaffold stage this must be non-null: {@code ActionManager} builds
     * every action regardless of the active view, and several actions call
     * {@code getGUI().getFixedImageLibrary()} from their constructors.  Phase 0
     * uses a single unscaled ({@code NORMAL_SCALE}) library; the map will likely
     * want a separately scaled one later.
     */
    private final ImageLibrary imageLibrary;

    /**
     * Puts every question, choice and notice of the classic boxes
     * ({@link #putBox}); a test puts its own in, as no box can open
     * headless.
     */
    Prompter prompter = this::putBox;

    /**
     * The in-canvas advisor boxes of the game view (build spec W7), or null
     * without one.
     */
    private ClassicAdvisorLayer boxLayer = null;


    /**
     * Create the classic GUI.
     *
     * @param freeColClient The {@code FreeColClient} for the game.
     */
    public ClassicGUI(FreeColClient freeColClient) {
        super(freeColClient);
        this.imageCache = new ImageCache();
        this.imageLibrary = new ImageLibrary(this.imageCache);
        logger.info("ClassicGUI selected (experimental classic UI).");
    }


    // Lifecycle

    /**
     * {@inheritDoc}
     *
     * Show the main window on a <b>passive</b> title backdrop: the original
     * title picture ({@link ClassicMainMenuPanel} in {@code PASSIVE} mode)
     * with no menu and no input.  Only {@link #showMainPanel} makes the menu
     * live, so the {@code --fast} path (which goes straight to a game and
     * never calls it) is unchanged -- it merely loads over the clean title
     * picture instead of a placeholder.
     *
     * <p><b>Fast start: adopting the early window.</b>  Normally the window
     * already exists: {@link ClassicStartupScreen} opened it about a second
     * after launch, before this client was even constructed, with the live
     * title (or, for {@code --fast} and a save argument, the passive
     * picture) painted straight from the pack files.  It is taken over here
     * <em>synchronously</em> -- frame, full-screen state, title panel --
     * because FreeColClient calls this from its constructor, which runs on
     * the EDT (FreeCol.startClient): the keys and clicks AWT queued while
     * the constructor blocked the EDT are dispatched only after this task,
     * so they reach a menu that already has the real actions
     * ({@link #menuActions}) and Alt+Enter / Alt+F4.  A menu choice made
     * before that (the early window's actions only queue it, see
     * {@link ClassicStartupScreen}) is replayed by {@link #showMainPanel},
     * after the start-up's own call to it, so it is not undone by it.
     *
     * <p>Without an early window (no pack, {@code --no-splash}) the window
     * is created here as before, in an {@code invokeLater}: without an
     * explicit {@code --windowsize} in <b>borderless full screen</b>, like
     * the original running full screen in DOSBox (see
     * {@link ClassicFrame#applyFrameMode}); with one, as the decorated window
     * of that size it always was.  Either way Alt+Enter toggles between the
     * two ({@link #toggleFullScreen}).
     */
    @Override
    public void startGUI(final Dimension desiredWindowSize) {
        logger.info("Starting ClassicGUI.");
        // The acceptance harness (recorder, input script); a no-op unless
        // its properties are set.
        ClassicTestHarness.install(getFreeColClient(), this);
        if (SwingUtilities.isEventDispatchThread()) {
            final ClassicStartupScreen.Taken early = ClassicStartupScreen.take();
            if (early != null) {
                adoptStartupWindow(early);
                logger.info("ClassicGUI started.");
                return;
            }
        }
        SwingUtilities.invokeLater(() -> {
            this.frameState = new ClassicFrame(desiredWindowSize);
            this.frame = this.frameState.frame;
            this.frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    closeRequested();
                }
            });
            // The original title picture (OPENMENU.PIK, 1:1 in a letterboxed
            // 320x200 canvas), passive until showMainPanel; without the pack a
            // dark ground.  reconnectGUI replaces it with the in-game view.
            mainMenu().showPassive();
            this.frame.setContentPane(this.mainMenuPanel);
            installFrameKeys();
            // The black start-up screen has kept this process in front since
            // the double click (see ClassicFrame.showFirst); it is disposed
            // only after this runs (FreeColClient, the splash hand-over).
            this.frameState.showFirst();
            logger.info("ClassicGUI window shown ("
                + (this.frameState.isFullScreen() ? "borderless full screen" : "windowed")
                + ", " + this.frame.getBounds() + ").");
        });
        logger.info("ClassicGUI started.");
    }

    /**
     * A menu choice made in the early window before this GUI existed, to
     * be replayed by the next {@link #showMainPanel} (EDT only); see
     * {@link #startGUI}.
     */
    private Runnable startupReplay = null;

    /**
     * True from adopting a live early window until the next
     * {@link #showMainPanel} (EDT only) -- in practice the start-up's own
     * call to it, {@code FreeColClient.startFirstTaskInGui}.
     *
     * <p>WHY: the early window is shown in the same EDT task that queues the
     * ~3 s client constructor (FreeCol.startClient), so practically every
     * key or click the player makes "early" is dispatched only <em>after</em>
     * the constructor, by the real actions installed in
     * {@link #adoptStartupWindow} -- not by {@code DeferringActions}.  Those
     * events sit in the queue in front of the start-up task, so typing ahead
     * (Enter through the new-game chain, Down x3 + Enter + Enter for a save)
     * has already started the game or the load, and left the panel frozen in
     * STARTING / BUSY, by the time the start-up calls {@code showMainPanel}.
     * Its plain "show the title" must then not reset that frozen panel to a
     * live title: the audience would be replaced by the menu for the seconds
     * the server needs, and NEUE WELT clicked there would start a second
     * game while logged in.  See {@link #showMainPanel}.
     */
    private boolean startupTitlePending = false;

    /**
     * Take over the early start-up window (see {@link #startGUI}).  EDT
     * only, inside FreeColClient's constructor.
     *
     * @param early What {@link ClassicStartupScreen#take} handed over.
     */
    private void adoptStartupWindow(ClassicStartupScreen.Taken early) {
        this.frameState = early.frame;
        this.frame = early.frame.frame;
        this.mainMenuPanel = early.panel;
        final ClassicMainMenuPanel.Actions real = menuActions();
        this.mainMenuPanel.setActions(real);
        this.frame.removeWindowListener(early.closeListener);
        this.frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeRequested();
            }
        });
        installFrameKeys();
        if (early.queued != null) {
            this.startupReplay = () -> early.queued.accept(real);
        }
        // A menu run (not --fast / a save argument, whose picture is passive):
        // the start-up's showMainPanel must not undo what the player types
        // ahead while the constructor still blocks the EDT.
        this.startupTitlePending = !this.mainMenuPanel.isPassive();
        logger.info("Classic start-up: client attached after "
            + ClassicStartupScreen.sinceLaunchMs() + " ms ("
            + (this.frameState.isFullScreen() ? "borderless full screen" : "windowed")
            + ", " + this.frame.getBounds()
            + ((early.queued != null) ? ", replaying an early menu choice" : "")
            + ").");
    }


    // Full screen (borderless) and Alt+Enter

    /**
     * Switch the main window between borderless full screen and a decorated
     * window; see {@link ClassicFrame#applyFrameMode} for how and why.
     *
     * @param full True for borderless full screen, false for a window.
     */
    private void applyFrameMode(boolean full) {
        if (this.frameState != null) this.frameState.applyFrameMode(full);
    }

    /** Whether the main window is in borderless full screen now. */
    private boolean isFullScreen() {
        return this.frameState != null && this.frameState.isFullScreen();
    }

    /**
     * Alt+Enter: switch between borderless full screen and a decorated
     * window, as in DOSBox.  EDT only.
     *
     * <p>The main window switches first ({@link #applyFrameMode}); then every
     * classic sub-window that is still open is re-framed for the new mode
     * through the same {@link #prepareChildWindow} that framed it, in the
     * order they stack (the build queue sits over its colony screen); finally
     * the window that was active comes back to the front and keyboard focus
     * returns to the component that had it (the map, the title menu, a
     * colony screen ...), because a dispose drops the focus.  A sub-window
     * that the player closed through its own close box is disposed but
     * still referenced; {@code isDisplayable()} tells it apart, and it is
     * left alone rather than brought back to life.
     *
     * <p><b>Refused while a modal dialog is up.</b>  Disposing a window
     * disposes the windows it owns, and every {@link ClassicDialog} (and the
     * choice list of {@link #modalChoiceDialog}) is owned by the main frame
     * or a classic screen ({@link #dialogOwner}): a switch then would
     * silently dismiss the question as if Escape had been pressed.  The menu bar's open dropdown is closed first for the
     * same reason (its heavyweight popup is an owned window too).
     */
    void toggleFullScreen() {
        if (this.frame == null) return;
        if (modalDialogShowing() || boxBusy()) {
            logger.info("ClassicGUI: Alt+Enter ignored while a dialog is open.");
            return;
        }
        MenuSelectionManager.defaultManager().clearSelectedPath();
        final Window active = KeyboardFocusManager
            .getCurrentKeyboardFocusManager().getActiveWindow();
        final Component focus = (active == null) ? null
            : active.getMostRecentFocusOwner();
        applyFrameMode(!isFullScreen());
        reframeChild(this.colonyFrame, this.frame);
        reframeChild(this.europeFrame, this.frame);
        reframeChild(this.reportFrame, this.frame);
        reframeChild(this.buildQueueFrame, isOpen(this.colonyFrame)
            ? this.colonyFrame : this.frame);
        final Window target = (active != null && active.isShowing())
            ? active : this.frame;
        target.toFront();
        final Component want = (focus != null && focus.isShowing()
                && SwingUtilities.getWindowAncestor(focus) == target)
            ? focus : defaultFocus(target);
        // requestFocusInWindow on a window that is not yet focused is
        // remembered and granted once the OS activates it (Component
        // Javadoc), which is exactly the state right after setVisible.
        if (want != null) {
            want.requestFocusInWindow();
            SwingUtilities.invokeLater(want::requestFocusInWindow);
        }
        logger.info("ClassicGUI: " + (isFullScreen()
                ? "borderless full screen" : "windowed")
            + " (" + this.frame.getBounds() + ").");
    }

    /** The natural focus owner of {@code w}: its content, or the map/menu. */
    private Component defaultFocus(Window w) {
        if (w == this.frame) {
            return (this.mapViewer != null) ? this.mapViewer
                : this.mainMenuPanel;
        }
        return (w instanceof JFrame) ? ((JFrame) w).getContentPane() : w;
    }

    /** Whether {@code w} is an open (not disposed) window. */
    private static boolean isOpen(Window w) {
        return w != null && w.isDisplayable();
    }

    /** Re-frame an open sub-window for the current mode (see toggle). */
    private static void reframeChild(Window w, Window ref) {
        if (!isOpen(w)) return;
        prepareChildWindow(w, ref, true);
        w.setVisible(true);
    }

    /** Whether any modal dialog of this application is on screen. */
    private static boolean modalDialogShowing() {
        for (Window w : Window.getWindows()) {
            if (w instanceof Dialog && w.isShowing() && ((Dialog) w).isModal()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether {@code ref} belongs to a borderless (full-screen) classic
     * window: it, or the first {@code Frame} up its owner chain, is
     * undecorated.  The classic UI only ever undecorates its frames for full
     * screen, so the decoration <em>is</em> the mode -- which lets
     * {@link ClassicDialog}, which only knows its owner window, follow the
     * mode without a reference to this GUI.
     */
    static boolean isBorderless(Window ref) {
        for (Window w = ref; w != null; w = w.getOwner()) {
            if (w instanceof Frame) return ((Frame) w).isUndecorated();
            if (w instanceof Dialog) return ((Dialog) w).isUndecorated();
        }
        return false;
    }

    /**
     * THE one place that frames a classic sub-window for the current mode;
     * call it after the content is set and before {@code setVisible(true)}.
     * Every window the classic UI opens goes through here -- the colony,
     * build-queue, Europe and report screens ({@code fullScreenSized}), and
     * every {@link ClassicDialog} popup and the choice list
     * ({@code !fullScreenSized}) -- so "no OS window chrome anywhere while
     * full screen" is decided once, not per screen.
     *
     * <ul>
     *   <li><b>Full screen</b> ({@code ref} borderless, see
     *       {@link #isBorderless}): the window is undecorated.  The original's
     *       full-screen screens (all 320x200 canvases that scale themselves
     *       to their size) cover {@code ref}'s bounds exactly, so on the
     *       owner's monitor they too run at x6 with no black bars.  Small
     *       popups keep their own size (they paint their own wood frame) and
     *       are centred over {@code ref}.</li>
     *   <li><b>Windowed:</b> today's behaviour -- decorated, packed to the
     *       preferred size and centred over {@code ref}.</li>
     * </ul>
     *
     * <p>Decoration can only change while a window is not displayable, so an
     * already shown window (a re-frame from {@link #toggleFullScreen}) is
     * disposed first; the caller shows it again.
     *
     * @param w The window to frame.
     * @param ref The window it belongs over: the main frame, or the colony
     *     screen for the build queue; decides the mode and the placement.
     * @param fullScreenSized True for a full-screen original screen, false
     *     for a popup.
     */
    static void prepareChildWindow(Window w, Window ref,
                                   boolean fullScreenSized) {
        final boolean borderless = isBorderless(ref);
        if (w.isDisplayable()) w.dispose();
        if (w instanceof Frame) {
            ((Frame) w).setUndecorated(borderless);
        } else if (w instanceof Dialog) {
            ((Dialog) w).setUndecorated(borderless);
        }
        if (borderless && fullScreenSized && ref != null) {
            w.setBounds(ref.getBounds());
        } else {
            w.pack();
            w.setLocationRelativeTo(ref);
        }
        final java.util.function.Consumer<Window> hook = childWindowHook;
        if (hook != null) hook.accept(w);
    }

    /**
     * The scripted harness's look at every sub-window before it is shown
     * ({@link ClassicTestHarness}: one opened while the game's window is
     * minimized opens minimized, never in front of the desktop); null in
     * a game.
     */
    static volatile java.util.function.Consumer<Window> childWindowHook = null;

    /**
     * The classic screen in front, for the scripted harness's keys when no
     * window has the focus (a minimized run): the build queue, the colony,
     * Europe or the report screen, whichever is open first in that
     * stacking order, else null.  EDT only.
     *
     * @return The screen's window, or null.
     */
    Window openScreen() {
        for (Window w : new Window[] { this.buildQueueFrame, this.colonyFrame,
                                       this.europeFrame, this.reportFrame }) {
            if (isOpen(w)) return w;
        }
        return null;
    }

    /**
     * The owner (and centring reference) for a popup: the classic screen the
     * player is looking at -- the colony, build-queue, Europe or report
     * window in front -- else the main frame.  EDT only.
     *
     * <p>Why not always the main frame: on Windows, bringing an owned window
     * to the top raises its owner directly beneath it.  With the main frame
     * as owner, a notice popping up over a full-screen colony screen would
     * pull the map above the colony screen, and when the notice closed the
     * player would be looking at the map with the colony screen buried
     * behind it.  Owned by the screen in front, the popup leaves the
     * stacking alone.  (A switch to/from full screen is refused while a
     * popup is up, so its owner is never disposed under it by
     * {@link #toggleFullScreen}.)
     *
     * <p><b>Why not simply the active window.</b>  Activation changes
     * asynchronously.  Right after one modal popup is disposed, the active
     * window is still that dead popup (or null) until the OS re-activates
     * the colony screen beneath it, and it is null whenever another
     * application is in front.  Two popups in a row from one server message
     * (end-of-turn notices, then a question) would then hand the second one
     * to the main frame and bury the colony screen after all.  So the active
     * window is only a hint: it and its owner chain are searched first (a
     * dead popup's owner is still the screen it belonged to), and failing
     * that the GUI's own state decides -- the open classic screens in their
     * fixed stacking order (the build queue sits over its colony screen; the
     * others are full-screen and opened from the map).  The main frame wins
     * the owner-chain search only while windowed, where the player can put
     * the map in front of an open screen by clicking it; in full screen an
     * open screen always covers the map.
     */
    private Window dialogOwner() {
        final Window[] screens = {
            this.buildQueueFrame, this.colonyFrame,
            this.europeFrame, this.reportFrame
        };
        for (Window w = KeyboardFocusManager.getCurrentKeyboardFocusManager()
                 .getActiveWindow(); w != null; w = w.getOwner()) {
            for (Window s : screens) {
                if (w == s && s.isShowing()) return s;
            }
            if (w == this.frame) {
                if (!isFullScreen()) return this.frame;
                break;
            }
        }
        for (Window s : screens) {
            if (s != null && s.isShowing()) return s;
        }
        return this.frame;
    }

    /**
     * Install the application-wide keys of the frame: Alt+Enter
     * (full screen / window) and the Alt+F4 fallback.  Once, at start-up.
     *
     * <p>A {@code KeyEventDispatcher} rather than an {@code InputMap} entry,
     * because it must work everywhere -- on the title screen, in game, and in
     * every classic sub-window -- and must <em>win</em> against the panels'
     * own Enter keys: the title menu and the map bind plain {@code ENTER}
     * (select / end turn), and an open in-game dropdown
     * ({@link ClassicMenuStrip}) takes every key through its own dispatcher,
     * registered later and so asked after this one.  The dispatcher sees the
     * key before any component, and consumes it, so Alt+Enter does exactly
     * one thing.  (FreeCol's {@code changeWindowedModeAction}, should anything
     * fire it, is routed here by {@link #changeWindowedMode}.)
     */
    private void installFrameKeys() {
        if (this.frameKeys != null) return;
        this.frameKeys = new FrameKeys();
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
            .addKeyEventDispatcher(this.frameKeys);
    }

    /**
     * The dispatcher behind {@link #installFrameKeys}.
     *
     * <p><b>Alt+Enter</b> toggles once per press: holding the keys makes the
     * OS auto-repeat {@code KEY_PRESSED}, which would flip the mode on every
     * repeat, so the toggle fires only on the first press and the matching
     * {@code KEY_TYPED}/{@code KEY_RELEASED} are swallowed too (an Enter
     * {@code KEY_TYPED} would otherwise reach a text field or a listener as
     * a plain Enter).  The toggle runs {@code invokeLater}, outside the key
     * dispatch, since it disposes the very window the event is being
     * delivered to.  Ctrl/AltGr combinations are not ours (AltGr is Ctrl+Alt
     * on the German keyboard).
     *
     * <p><b>Alt+F4</b> posts {@code WINDOW_CLOSING} to the window that has
     * focus: on the main frame that is {@link #closeRequested} (it asks
     * before anything ends), a sub-window or popup closes as through its
     * close box.  Windows normally sends that close itself, also to an
     * undecorated window, but AWT hands system keys to Java first, so whether
     * the native close still happens is up to the JDK; doing it here makes
     * the one exit path that every borderless window has a certainty.  If the
     * OS closes as well nothing breaks: {@code closeRequested} ignores a
     * second request while its question is open, opening the title's quit
     * box twice is a no-op, and disposing a closed sub-window again is a
     * no-op.
     */
    private final class FrameKeys implements KeyEventDispatcher {

        /**
         * The longest gap between two auto-repeated presses: Windows' longest
         * keyboard repeat delay is 1 s.  A press after a longer gap is a new
         * press even if its release never arrived -- which happens, because
         * the toggle disposes the window that would receive it.
         */
        private static final long REPEAT_GAP_MS = 1100L;

        /** Whether the current Enter press was taken as Alt+Enter. */
        private boolean altEnterHeld = false;

        /** When the last Alt+Enter press (first or repeated) arrived. */
        private long lastPress = 0L;

        /**
         * Keys held down: key code -> time of its latest KEY_PRESSED.  The
         * frame-wide record of which press is an OS auto-repeat, for
         * screens that appear while a key is still held (the first scene,
         * {@link ClassicHudOverlay}, after the departure was skipped with a
         * key held through the engine start).  A press more than
         * {@link #REPEAT_GAP_MS} after the previous one of the same key is
         * new even without a release in between (a release can be lost
         * with a disposed window).
         */
        private final Map<Integer, Long> down = new HashMap<>();

        /** The latest KEY_PRESSED seen: code, time, and whether it repeated. */
        private int lastCode = KeyEvent.VK_UNDEFINED;
        private long lastWhen = 0L;
        private boolean lastRepeat = false;

        /**
         * Whether {@code e} -- a KEY_PRESSED already seen by this dispatcher,
         * which runs before every component -- is an auto-repeat.
         */
        boolean isAutoRepeat(KeyEvent e) {
            return e.getID() == KeyEvent.KEY_PRESSED
                && e.getKeyCode() == this.lastCode && e.getWhen() == this.lastWhen
                && this.lastRepeat;
        }

        /** Record presses and releases (see {@link #down}). */
        private void track(KeyEvent e) {
            final int code = e.getKeyCode();
            if (code == KeyEvent.VK_UNDEFINED) return;
            if (e.getID() == KeyEvent.KEY_PRESSED) {
                // The same event seen twice (re-dispatched) is not a repeat.
                if (code == this.lastCode && e.getWhen() == this.lastWhen) return;
                final Long prev = this.down.put(code, e.getWhen());
                this.lastCode = code;
                this.lastWhen = e.getWhen();
                this.lastRepeat = prev != null
                    && e.getWhen() - prev < REPEAT_GAP_MS;
            } else if (e.getID() == KeyEvent.KEY_RELEASED) {
                this.down.remove(code);
            }
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent e) {
            track(e);
            final boolean ourMods = e.isAltDown() && !e.isControlDown()
                && !e.isMetaDown() && !e.isAltGraphDown();
            if (e.getKeyCode() == KeyEvent.VK_F4) {
                if (e.getID() != KeyEvent.KEY_PRESSED || !ourMods) return false;
                final Window w = (e.getComponent() instanceof Window)
                    ? (Window) e.getComponent()
                    : SwingUtilities.getWindowAncestor(e.getComponent());
                if (w == null) return false;
                SwingUtilities.invokeLater(() -> w.dispatchEvent(
                    new WindowEvent(w, WindowEvent.WINDOW_CLOSING)));
                return true;
            }
            final boolean enter = e.getKeyCode() == KeyEvent.VK_ENTER
                || (e.getID() == KeyEvent.KEY_TYPED
                    && (e.getKeyChar() == '\n' || e.getKeyChar() == '\r'));
            if (!enter) return false;
            switch (e.getID()) {
            case KeyEvent.KEY_PRESSED:
                if (!ourMods) {
                    // A plain Enter: whatever Alt+Enter press came before is
                    // over, even if its release was lost with a disposed
                    // window -- otherwise this Enter's KEY_TYPED would be
                    // swallowed below.
                    this.altEnterHeld = false;
                    return false;
                }
                final long now = e.getWhen();
                final boolean repeat = this.altEnterHeld
                    && now - this.lastPress < REPEAT_GAP_MS;
                this.lastPress = now;
                if (!repeat) {
                    this.altEnterHeld = true;
                    SwingUtilities.invokeLater(ClassicGUI.this::toggleFullScreen);
                }
                return true;
            case KeyEvent.KEY_RELEASED:
                // Alt may already be up when Enter is released.
                final boolean held = this.altEnterHeld;
                this.altEnterHeld = false;
                return held || ourMods;
            default:    // KEY_TYPED
                return this.altEnterHeld || ourMods;
            }
        }
    }

    /**
     * The main window was asked to close (Alt+F4, the decorated window's X,
     * a WM_CLOSE from outside).  EDT only.
     *
     * <p>The frame used to be {@code EXIT_ON_CLOSE}: harmless while the only
     * close gesture was a deliberate click on the title bar's X, but in
     * borderless full screen Alt+F4 is the close gesture, and one slip ended
     * the program at once -- the running game unsaved, and without
     * {@code FreeColClient.quit} (server stop, autosave pruning,
     * {@code quitGUI}).  Now every close goes the way FreeCol's own
     * {@code WindowedFrameListener} goes, with a question first:
     * <ul>
     *   <li><b>A game (or the map editor) runs:</b>
     *       {@code FreeColClient.askToQuit} -- the same classic "are you
     *       sure" popup as Spiel &gt; Beenden, then logout and
     *       {@code quit}.</li>
     *   <li><b>The live title menu is up:</b> its own "Colonization
     *       beenden?" box ({@link ClassicMainMenuPanel#offerQuit}), "Nein"
     *       preselected, exactly as Escape opens it.</li>
     *   <li><b>Otherwise</b> (the passive title backdrop while the game
     *       loads, the busy box while a game starts): nothing is at stake
     *       yet, so {@code quit} directly, as FreeCol does.</li>
     * </ul>
     * A second request while the question is open (Windows may deliver its
     * own close besides the one FrameKeys posts) is ignored.
     */
    void closeRequested() {
        if (this.closeAsked) return;
        final FreeColClient fcc = getFreeColClient();
        if (fcc.isInGame() || fcc.isMapEditor()) {
            this.closeAsked = true;
            try {
                fcc.askToQuit();
            } finally {
                this.closeAsked = false;
            }
            return;
        }
        if (this.mainMenuPanel != null && this.frame != null
            && this.frame.getContentPane() == this.mainMenuPanel
            && this.mainMenuPanel.offerQuit()) {
            return;
        }
        fcc.quit();
    }

    /**
     * {@inheritDoc}
     *
     * False while in borderless full screen.  The base answers a constant
     * true (GUI.java:1068); this one tells the truth for the reused
     * {@code changeWindowedModeAction} and anything else that asks.
     */
    @Override
    public boolean isWindowed() {
        return !isFullScreen();
    }

    /**
     * {@inheritDoc}
     *
     * The reused in-game menu's "full screen" item
     * ({@code ChangeWindowedModeAction}, InGameMenuBar.java:201) lands here;
     * the base no-ops it (GUI.java:1095).  Same toggle as Alt+Enter.
     */
    @Override
    public void changeWindowedMode() {
        invokeNowOrLater(this::toggleFullScreen);
    }


    // Title screen and main menu

    /**
     * What the original's new-game screens collect before a game in the New
     * World starts: the difficulty ({@code opening_056..060}), the European
     * power ({@code 061..064}) and the leader's name ({@code 065}).  Null
     * fields mean "FreeCol's default".
     *
     * <p>The chain ({@link ClassicNewWorldChain#setup}) fills it from two
     * index tables in the original's order, both in
     * {@link ClassicNewWorldScreens}: {@code DIFFICULTY_IDS} (the easiest ..
     * the hardest = {@code model.difficulty.veryEasy .. veryHard}) and
     * {@code NATION_IDS} (England, France, Spain, Holland =
     * {@code model.nation.english, french, spanish, dutch}).
     */
    static final class NewWorldSetup {

        /** A difficulty level id such as {@code model.difficulty.veryEasy}. */
        final String difficultyId;
        /** A nation id such as {@code model.nation.english}, or null. */
        final String nationId;
        /** The leader's name, or null for {@code FreeCol.getName()}. */
        final String playerName;

        NewWorldSetup(String difficultyId, String nationId, String playerName) {
            this.difficultyId = difficultyId;
            this.nationId = nationId;
            this.playerName = playerName;
        }

        /** FreeCol's current defaults (command line or rules difficulty). */
        static NewWorldSetup defaults() {
            return new NewWorldSetup(FreeCol.getDifficulty(), null, null);
        }
    }

    /** The title panel, created on first use (EDT only). */
    private ClassicMainMenuPanel mainMenu() {
        if (this.mainMenuPanel == null) {
            this.mainMenuPanel = new ClassicMainMenuPanel(menuActions());
        }
        return this.mainMenuPanel;
    }

    /** The title menu's actions, routed into this GUI. */
    private ClassicMainMenuPanel.Actions menuActions() {
        return new ClassicMainMenuPanel.Actions() {
            @Override
            public void newWorld(NewWorldSetup setup) {
                beginNewWorldSetup((setup == null) ? NewWorldSetup.defaults() : setup);
            }

            @Override
            public void loadGame(File file) {
                loadSavedGame(file);
            }

            @Override
            public void hallOfFame() {
                // Client-side scores (HighScore.loadHighScores, HighScore.java:420)
                // work before any game; ReportHighScoresAction would ask a
                // server that does not exist yet (InGameController.highScore).
                showHighScoresPanel(null, HighScore.loadHighScores());
            }

            @Override
            public void quit() {
                // FreeCol's own quit path (FreeColClient.java:1056): stops a
                // server if one runs, prunes old autosaves, takes the GUI
                // down (quitGUI) and exits via FreeCol.quit(0).  This is what
                // FreeCol's WindowedFrameListener does when no game is
                // running; no "are you sure" again, the quit box just asked.
                getFreeColClient().quit();
            }
        };
    }

    /**
     * THE SEAM between the original's new-game screens and the engine.  The
     * difficulty, nation, name, nation-page and audience screens run purely
     * on the title canvas ({@link ClassicMainMenuPanel} mode NEW_WORLD, no
     * engine state, so going back needs no teardown); when the audience is
     * dismissed the panel freezes it and lands here with all three choices.
     * A pack without the original texts skips the chain and arrives with
     * {@link NewWorldSetup#defaults}.
     *
     * @param setup The player's choices.
     */
    void beginNewWorldSetup(NewWorldSetup setup) {
        startNewWorldGame(setup);
    }

    /**
     * Start a fresh single-player game in a generated New World -- the same
     * path as FreeCol's own new-game panel; it never resumes a save.  Deferred
     * with {@code invokeLater} so the busy box paints before the server start
     * blocks the EDT.  Does not call {@code FreeCol.setDifficulty}, so a later
     * {@code --fast} keeps its default.
     *
     * <p>Any {@code RuntimeException} is caught, shown, and leads back to the
     * title.  The title panel sits in its input-ignoring BUSY mode while this
     * runs, and nothing on this path reports a runtime failure by itself:
     * {@code FreeColClient.startServer} catches only {@code IOException},
     * {@code startSinglePlayerGame} loads mods and message bundles unguarded,
     * and the client's uncaught-exception handler merely logs.  Without the
     * catch the player would face "Neues Spiel wird vorbereitet ..." forever.
     * FreeCol's own {@code NewPanel} guards the same calls the same way
     * (NewPanel.java:613-615).
     *
     * <p>Where each choice enters the engine (the order is forced by it):
     * <ol>
     *   <li>Difficulty -- now, into the fresh Specification
     *       ({@code FreeCol.loadSpecification} -> {@code Specification.prepare},
     *       Specification.java:645-669); the server plays on this very object
     *       (FreeColServer.java:318-329) and the save records it.  An unknown
     *       id would silently apply nothing, hence the check below.</li>
     *   <li>Name -- now, before login: the one static {@code FreeCol.setName}
     *       becomes the login name (ConnectController.java:351), the player's
     *       name (LoginMessage.java:233) and the save's owner
     *       (FreeColServer.java:908-909).  There is no pre-game rename.</li>
     *   <li>Nation -- after login, in {@link #showStartGamePanel}: the server
     *       ignores the nation at login (LoginMessage.java:214).</li>
     * </ol>
     * {@code FreeCol.setDifficulty}, {@code setAdvantages} and
     * {@code setEuropeanCount} are never called, so {@code --fast} keeps its
     * defaults.
     *
     * @param setup The player's choices.
     */
    void startNewWorldGame(final NewWorldSetup setup) {
        SwingUtilities.invokeLater(() -> {
            try {
                // A fresh Specification per call (FreeCol.java:981-994),
                // of the Classic UI's rules, "levi"
                // (FreeCol.getNewGameRules).
                final FreeColModFile rules = FreeCol.getNewGameRulesFile();
                Specification spec = FreeCol.loadSpecification(
                    rules, FreeCol.getAdvantages(), setup.difficultyId);
                if (spec != null && setup.difficultyId != null
                    && !setup.difficultyId.equals(spec.getDifficultyLevel())) {
                    logger.warning("ClassicGUI: difficulty " + setup.difficultyId
                        + " not applied (got " + spec.getDifficultyLevel()
                        + "); falling back to " + FreeCol.getDifficulty());
                    spec = FreeCol.loadSpecification(rules,
                        FreeCol.getAdvantages(), FreeCol.getDifficulty());
                }
                if (spec == null) {
                    logger.warning("ClassicGUI: no rules "
                        + FreeCol.getNewGameRules() + " for a new game.");
                    showMainPanel(Messages.message("classic.mainMenu.startFailed"));
                    return;
                }
                logger.info("ClassicGUI: new game on the rules " + spec.getId()
                    + ", difficulty " + spec.getDifficultyLevel());
                // A new game is played on the original's square map, with
                // its map size and land shares, unless -Dfreecol.topology
                // says otherwise.  The server's new map takes the topology
                // in use, so set it before the server starts.
                Topology.setCurrent(Topology.forNewGame(Topology.SQUARE));
                MapGeneratorOptions.applyTopologyDefaults(
                    spec.getMapGeneratorOptions());
                if (setup.playerName != null) {
                    final String name = setup.playerName.trim();
                    if (!isFreePlayerName(spec, name, setup.nationId)) {
                        logger.info("ClassicGUI: refused player name " + name);
                        this.pendingNationId = null;
                        showMainPanel(Messages.message("classic.newWorld.nameTaken"));
                        return;
                    }
                    FreeCol.setName(name);
                }
                this.pendingNationId = setup.nationId;
                this.newWorldStartRequested = true;
                if (!getFreeColClient().getConnectController()
                        .startSinglePlayerGame(spec)) {
                    this.pendingNationId = null;
                    this.newWorldStartRequested = false;
                    showMainPanel(null);
                }
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "ClassicGUI: new game failed.", e);
                this.pendingNationId = null;
                this.newWorldStartRequested = false;
                // Shows the error, then returns to the title (GUI.java:945-951).
                showErrorPanel(e,
                    StringTemplate.key("classic.mainMenu.startFailed"));
            }
        });
    }

    /**
     * Whether a typed leader name may be used.  Refused: an empty name (the
     * server rejects it), {@code mapEditor} (refused when a save is loaded)
     * and the name of any other nation's player -- the AI players keep the
     * name the rules give them ({@code NameCache.getLeaderName}: the
     * original's leaders in the "levi" rules, else the ruler), and two
     * players with one name break {@code Game.getPlayerByName} (an exact
     * {@code equals}, first match) and with it loading the save.  The
     * player's own nation has no AI player, so its leader, the name the
     * name screen offers (NAMES.TXT {@code @LEADERNAME}), is free.
     *
     * @param spec The game's rules.
     * @param name The trimmed name.
     * @param nationId The player's nation, or null if not known.
     * @return True if the name is free.
     */
    static boolean isFreePlayerName(Specification spec, String name,
                                    String nationId) {
        if (name == null || name.isEmpty() || "mapEditor".equals(name)) return false;
        for (Nation n : spec.getNations()) {
            if (n.getId().equals(nationId)) continue;
            if (name.equals(NameCache.getLeaderName(spec, n))) return false;
        }
        return true;
    }

    /**
     * Load a save picked in the title screen's load box.  Runs on the EDT
     * like every FreeCol caller of {@code startSavedGame}
     * (FreeColClient.java:324-337); on failure the engine has already shown
     * its error (via {@link #showErrorPanel}) and the title comes back.  A
     * {@code RuntimeException} the engine does not catch itself is shown here
     * and also leads back to the title, so the BUSY box cannot get stuck (see
     * {@link #startNewWorldGame}).
     *
     * @param file The save to load.
     */
    void loadSavedGame(final File file) {
        // A hand-off left by a failed new-game start must never apply here.
        this.pendingNationId = null;
        this.newWorldStartRequested = false;
        this.firstScenePending = false;
        SwingUtilities.invokeLater(() -> {
            try {
                if (!getFreeColClient().getConnectController()
                        .startSavedGame(file)) {
                    showMainPanel(null);
                }
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "ClassicGUI: loading " + file
                    + " failed.", e);
                // Shows the error, then returns to the title (GUI.java:945-951).
                showErrorPanel(e, FreeCol.badFile("error.couldNotLoad", file));
            }
        });
    }

    /**
     * Take the in-game view down so the title can replace it, and so the next
     * {@link #reconnectGUI} builds a fresh one (it only does while
     * {@code mapViewer} is null).  Closes the colony, build-queue, Europe and
     * report windows -- except an open high-score window, which an ended game
     * leaves for the player to read over the title -- stops the map's timer and
     * removes the menu bar.  EDT only; a no-op when no game was shown.
     */
    private void teardownInGame() {
        // A box still up or due answers "dismissed" (no, cancel), first,
        // while the blink and the turn flow it tells are still there.
        if (this.boxLayer != null) this.boxLayer.dispose();
        this.boxLayer = null;
        closeColonyPanel();
        closeBuildQueuePanel();
        closeEuropePanel();
        if (this.reportFrame != null && !(this.reportFrame.getContentPane()
                instanceof ClassicReportHighScoresPanel)) {
            closeReportPanel();
        }
        if (this.turnPoll != null) this.turnPoll.stop();
        ResourceManager.setPreloadHold(null);
        if (this.turnFlow != null) this.turnFlow.dispose();
        this.turnPoll = null;
        this.turnFlow = null;
        // No band, chain or tip of this game outlives it (W13).
        this.voyageChain = null;
        for (ClassicOneShot t : new ClassicOneShot[] {
                this.voyageTimer, this.bandTimer, this.tipTimer }) {
            if (t != null) t.close();
        }
        this.voyageTimer = this.bandTimer = this.tipTimer = null;
        this.voyages.clear();
        if (this.menuStrip != null) this.menuStrip.setBand(null);
        // FreeCol's goto batch again for whatever comes next (W5f), and
        // no held letter or road left over.
        gotoBatch(true);
        this.unitCycle.clear();
        ClassicUnitCycle.unshow(this.unitCycle);
        // The menu first: its close resumes the blink of a live viewer
        // (FINAL "Open" item 11).
        if (this.menuStrip != null) this.menuStrip.closeMenu();
        if (this.waterCycle != null) this.waterCycle.close();
        if (this.mapViewer != null) this.mapViewer.dispose();
        if (this.terrainOracle != null) this.terrainOracle.dispose();
        if (this.frame != null) this.frame.setJMenuBar(null);
        ClassicDialog.setWatcher(null);
        restoreSessionOptions();
        this.landing = null;
        this.boardedUnit = null;
        this.woodcuts = 0;
        this.woodcutsAsked = 0;
        this.woodcutsPosted = 0;
        this.foundingTile = null;
        this.mapViewer = null;
        this.terrainOracle = null;
        this.waterCycle = null;
        this.infoPanel = null;
        this.menuStrip = null;
        this.hudPane = null;
        // No first scene, no held notices and no father offer survive the
        // game view (the server offers the fathers again).
        this.firstScenePending = false;
        this.heldMessages.clear();
        this.pendingFathers = null;
        this.fathersPosted = false;
        if (this.sceneShowing) {
            this.sceneShowing = false;
            if (this.hudOverlay != null) this.hudOverlay.hideScene();
        }
    }

    /**
     * {@inheritDoc}
     *
     * Show the live title menu ({@code opening_033}), tearing down any game
     * view first.  The base {@code GUI} no-ops this (GUI.java:2213), which is
     * why the old placeholder never went away.  Posted with
     * {@code invokeLater}, so at startup it runs after the frame creation
     * {@link #startGUI} queued (FIFO).
     *
     * <p>Also switches the music to the original title piece: every way to
     * the title ends here (start-up, {@link #showMainTitle}, the in-game
     * "Neues Spiel" via {@link #showNewPanel}, defeat/quit logouts, failed
     * starts and loads), so the title piece is bound to the title screen
     * itself rather than to FreeCol's {@code sound.intro.general} calls --
     * which is also why {@code --fast}, which never shows the title, goes
     * straight to the in-game playlist.  Idempotent: re-showing the title
     * does not restart the piece (see {@link ClassicSoundController}).
     *
     * <p><b>A menu that is already live is left alone</b> when there is no
     * notice to show.  With the fast start the title is live in the early
     * window ({@link ClassicStartupScreen}) seconds before the start-up
     * reaches this call, and the player may already have moved the bar,
     * opened the load box or be half-way through the new-game chain;
     * {@code showTitle} resets the mode and the selection
     * (ClassicMainMenuPanel.showTitle) and would throw that away.  Only the
     * live modes are spared ({@link ClassicMainMenuPanel#isLive}): a panel
     * that is PASSIVE (a game was shown), BUSY or STARTING (a failed load or
     * start) is still reset to the title, as is any panel that is not the
     * content pane.  A menu choice the early window queued before this GUI
     * existed is replayed here, after the title is in place (see
     * {@link #startGUI}); its BUSY/STARTING screen is left as it is.
     *
     * <p><b>The start-up's own call after typing ahead.</b>  Nearly all early
     * input is dispatched after the client is attached, by the real actions,
     * yet still before the start-up task that makes this call (see
     * {@link #startupTitlePending}).  So the first call after adopting a live
     * early window finds the game start or load the player already chose in
     * flight -- the panel frozen in STARTING / BUSY (or PASSIVE, or the game
     * view already up).  That call, without a notice, changes nothing: no
     * title reset, no teardown; only the title piece starts if no music has
     * been chosen yet ({@link #playTitleMusicIfSilent}).  Every later call
     * (a failed start or load, the way back from a game) behaves as above.
     *
     * @param userMsg An optional notice shown over the menu.
     * @return {@code null} (the classic UI hosts its own panels).
     */
    @Override
    public FreeColPanel showMainPanel(final String userMsg) {
        SwingUtilities.invokeLater(() -> {
            if (this.frame == null) return;
            final Runnable replay = this.startupReplay;
            this.startupReplay = null;
            final boolean startupCall = this.startupTitlePending;
            this.startupTitlePending = false;
            if (startupCall && userMsg == null && replay == null
                && this.mainMenuPanel != null
                && (this.frame.getContentPane() != this.mainMenuPanel
                    || !this.mainMenuPanel.isLive())) {
                // Typed ahead: a start or load is already under way (see
                // startupTitlePending).  Leave its frozen screen -- or the
                // game view, should it already be up -- exactly as it is.
                logger.info("Classic start-up: an early menu choice is already"
                    + " running; the start-up title is skipped.");
                if (this.frame.getContentPane() == this.mainMenuPanel) {
                    playTitleMusicIfSilent();
                    this.mainMenuPanel.requestFocusInWindow();
                }
                updateActions();
                return;
            }
            playTitleMusic();
            teardownInGame();    // also drops a pending first scene
            final boolean keep = userMsg == null && this.mainMenuPanel != null
                && this.frame.getContentPane() == this.mainMenuPanel
                && (replay != null || this.mainMenuPanel.isLive());
            if (!keep) mainMenu().showTitle(userMsg);
            if (this.frame.getContentPane() != this.mainMenuPanel) {
                this.frame.setContentPane(this.mainMenuPanel);
            }
            this.frame.revalidate();
            this.frame.repaint();
            this.mainMenuPanel.requestFocusInWindow();
            updateActions();
            if (replay != null) {
                logger.info("Classic start-up: replaying the early menu choice.");
                replay.run();
            }
        });
        return null;
    }

    /**
     * {@inheritDoc}
     *
     * Back to the title from a running game ({@code ConnectController.mainTitle},
     * which logs out and stops the server right after this).  Unlike
     * {@code SwingGUI} it plays no {@code sound.intro.general}:
     * {@link #showMainPanel} switches to the original title piece.
     */
    @Override
    public void showMainTitle() {
        showMainPanel(null);
    }

    /**
     * Switch the music to the original title piece, when the classic sound
     * controller is in charge (it always is under {@code --classic}; the
     * check keeps a stray plain {@code SoundController} harmless).
     */
    private void playTitleMusic() {
        final SoundController sc = getFreeColClient().getSoundController();
        if (sc instanceof ClassicSoundController) {
            ((ClassicSoundController)sc).playTitleMusic();
        }
    }

    /**
     * The title piece, but only while no music has been chosen yet: the
     * start-up's skipped title ({@link #showMainPanel}) must not switch a
     * game that has already reached {@code startGameInternal} (the nation
     * intro, which selects the in-game playlist) back to the title piece.
     */
    private void playTitleMusicIfSilent() {
        final SoundController sc = getFreeColClient().getSoundController();
        if (sc instanceof ClassicSoundController
            && ((ClassicSoundController)sc).getMode()
                == ClassicSoundController.Mode.SILENT) {
            ((ClassicSoundController)sc).playTitleMusic();
        }
    }

    /**
     * {@inheritDoc}
     *
     * A game is about to be shown (GUI.java:1758): leave just the picture up,
     * with input off, until {@link #reconnectGUI} replaces it.
     */
    @Override
    public void closeMainPanel() {
        SwingUtilities.invokeLater(() -> {
            if (this.mainMenuPanel != null) this.mainMenuPanel.showPassive();
        });
    }

    /**
     * {@inheritDoc}
     *
     * The in-game "new game" ({@code ConnectController.newGame}) calls this
     * before {@link #showNewPanel}; drop the game view.
     */
    @Override
    public void removeInGameComponents() {
        invokeNowOrLater(this::teardownInGame);
    }

    /**
     * {@inheritDoc}
     *
     * The in-game load ({@code InGameController.loadGame}) calls this before
     * {@code startSavedGame}: drop the game view and show the passive title
     * picture, so {@link #reconnectGUI} rebuilds the view for the loaded game.
     */
    @Override
    public void prepareShowingMainMenu() {
        this.firstScenePending = false;
        invokeNowOrLater(() -> {
            teardownInGame();
            if (this.frame == null) return;
            mainMenu().showPassive();
            if (this.frame.getContentPane() != this.mainMenuPanel) {
                this.frame.setContentPane(this.mainMenuPanel);
                this.frame.revalidate();
                this.frame.repaint();
            }
        });
    }

    /**
     * {@inheritDoc}
     *
     * Go straight on: the Classic UI's intro (the own emblem, the original's
     * chart credits and title build-up) already runs in the early window,
     * {@code ClassicMainMenuPanel} mode INTRO, started by
     * {@link ClassicStartupScreen#show} long before this call, and the
     * start-up {@link #showMainPanel} keeps it running (INTRO counts as
     * live).  FreeCol's intro video is never shown.  The base no-op
     * (GUI.java:1141) never ran {@code callback}, so a classic start
     * without {@code --no-intro} or {@code --fast} never reached any menu.
     */
    @Override
    public void showOpeningVideo(final String userMsg, Runnable callback) {
        if (callback != null) SwingUtilities.invokeLater(callback);
    }

    /**
     * {@inheritDoc}
     *
     * The in-game "new game" lands on the classic title menu, from where a
     * new game starts (the base no-op left an empty window).
     */
    @Override
    public FreeColPanel showNewPanel(Specification spec) {
        showMainPanel(null);
        return null;
    }

    /**
     * {@inheritDoc}
     *
     * FreeCol asks this for saves marked multiplayer, or when the client
     * option says to always ask (ConnectController.java:417-427); a null
     * answer (the base no-op) silently aborts the load.  The classic UI only
     * plays single-player, locally.
     */
    @Override
    public LoadingSavegameInfo showLoadingSavegameDialog(boolean publicServer,
                                                         boolean singlePlayer) {
        return new LoadingSavegameInfo(true, null, -1, null, false);
    }

    // Pre-game lobby

    /**
     * {@inheritDoc}
     *
     * Phase 0 has no pre-game lobby panel.  The base {@code GUI} no-ops this,
     * which leaves a new single-player game stalled at login (see
     * {@code ConnectController.login}: with no map yet, control passes here
     * instead of {@code requestLaunch}).  Until a real lobby exists, auto-launch
     * single-player games so the classic UI can actually reach the in-game view;
     * this mirrors the "Start Game" button of {@code StartGamePanel}.  For
     * multiplayer there is nothing sensible to do headlessly, so we no-op.
     */
    @Override
    public FreeColPanel showStartGamePanel(Game game, Player player,
                                           boolean singlePlayerMode) {
        if (singlePlayerMode && player != null) {
            logger.info("ClassicGUI: auto-launching single-player game "
                + "(no lobby panel in Phase 0).");
            // A nation picked on the title screens (NewWorldSetup) replaces the
            // one the server assigned at login (always the first free one).
            // Without a pick (--fast, defaults) nothing changes.
            final String nid = this.pendingNationId;
            this.pendingNationId = null;
            final boolean fromTitle = this.newWorldStartRequested;
            this.newWorldStartRequested = false;
            if (nid != null) {
                boolean ok;
                try {
                    ok = game != null && applyNewWorldNation(game, player, nid);
                } catch (RuntimeException e) {
                    logger.log(Level.WARNING, "ClassicGUI: cannot select nation "
                        + nid, e);
                    ok = false;
                }
                if (!ok) {
                    abortNewWorldStart("classic.newWorld.nationFailed");
                    return null;
                }
            }
            // A fresh game from the title screen: its first view is the
            // admiral's scene (see firstScenePending; loads never pass
            // through here, and --fast never sets newWorldStartRequested).
            this.firstScenePending = fromTitle;
            player.setReady(true);
            getFreeColClient().getPreGameController().requestLaunch();
        }
        return null;
    }

    /**
     * Make the logged-in human the chosen original power, with exactly the
     * other three originals as AI rivals, and verify it on the server.
     *
     * <p>Why after login: pre-game login ignores the requested nation and
     * takes the first AVAILABLE one in HashMap order (LoginMessage.java:214,
     * NationOptions.java:112).  That is Holland only because Portugal and
     * Sweden are NOT_AVAILABLE with the default four Europeans, so the usual
     * Dutch game skips the switch.  Otherwise, in this order:
     * <ol>
     *   <li>the target nation is made AVAILABLE if it is not (covers
     *       {@code --europeans} below 4) -- the server only lets a player
     *       take an AVAILABLE nation (SetNationMessage.java:116-123);</li>
     *   <li>{@code setNation} and THEN {@code setNationType}: under FIXED
     *       advantages the server checks the type against the player's
     *       current nation (SetNationTypeMessage.java:100-118);</li>
     *   <li>the line-up: the other original powers become AVAILABLE again if
     *       they were not, every other European without a player becomes
     *       NOT_AVAILABLE, so {@code buildGame} (FreeColServer.java:1199-1208)
     *       makes exactly the other three originals AI whatever
     *       {@code --europeans} says;</li>
     *   <li>verification on the in-process server: a server rejection only
     *       shows an error dialog while the ask still returns true, so the
     *       server game must show the human (by name) on the nation.</li>
     * </ol>
     * The asks are nested synchronous requests on the EDT inside
     * {@code startSinglePlayerGame}'s login, which the connection allows
     * (Connection.java:436-441, 558-569).
     *
     * @return True if the human now plays {@code nid}.
     */
    private boolean applyNewWorldNation(Game game, Player player, String nid) {
        final Specification spec = game.getSpecification();
        final Nation n = spec.getNation(nid);
        if (n == null) {
            logger.warning("ClassicGUI: unknown nation " + nid);
            return false;
        }
        final NationOptions options = game.getNationOptions();
        final PreGameController pgc = getFreeColClient().getPreGameController();
        if (!nid.equals(player.getNationId())) {
            if (options.getNationState(n) != NationState.AVAILABLE) {
                pgc.setAvailable(n, NationState.AVAILABLE);
            }
            pgc.setNation(n);
            pgc.setNationType(n.getType());
        }
        final List<String> originals
            = java.util.Arrays.asList(ClassicNewWorldScreens.NATION_IDS);
        for (String id : originals) {
            if (id.equals(nid)) continue;
            final Nation o = spec.getNation(id);
            if (o != null && options.getNationState(o) == NationState.NOT_AVAILABLE
                && game.getPlayerByNationId(id) == null) {
                pgc.setAvailable(o, NationState.AVAILABLE);
            }
        }
        for (Nation e : spec.getEuropeanNations()) {
            if (originals.contains(e.getId())) continue;
            final NationState st = options.getNationState(e);
            if (st != null && st != NationState.NOT_AVAILABLE
                && game.getPlayerByNationId(e.getId()) == null) {
                pgc.setAvailable(e, NationState.NOT_AVAILABLE);
            }
        }
        final FreeColServer server = getFreeColClient().getFreeColServer();
        if (server != null && server.getGame() != null) {
            final Player p = server.getGame().getPlayerByNationId(nid);
            final boolean ok = p != null && FreeCol.getName().equals(p.getName());
            if (!ok) {
                logger.warning("ClassicGUI: server did not assign " + nid
                    + " to " + FreeCol.getName() + " (has " + p + ")");
            }
            return ok;
        }
        return nid.equals(player.getNationId());
    }

    /**
     * Give up a new-game start that got as far as login: back to the title
     * with a notice, log out and stop the in-process server.  Mirrors
     * {@code ConnectController.mainTitle} (ConnectController.java:547-566)
     * without its "really stop?" question, as no game has started.  The
     * game must never launch silently as the wrong power.
     *
     * <p>Deferred with {@code invokeLater}: this is called from inside the
     * login reply ({@code startSinglePlayerGame} -> login ask ->
     * {@link #showStartGamePanel}), and tearing the connection down while
     * that call stack is still on it would turn a clean abort into a
     * "could not log in" error on top.
     *
     * @param messageKey The notice's message key.
     */
    private void abortNewWorldStart(final String messageKey) {
        SwingUtilities.invokeLater(() -> {
            final FreeColClient fcc = getFreeColClient();
            showMainPanel(Messages.message(messageKey));
            try {
                if (fcc.isLoggedIn()) {
                    fcc.getConnectController().requestLogout(LogoutReason.MAIN_TITLE);
                } else {
                    fcc.logout(false);
                }
            } finally {
                fcc.stopServer();
            }
        });
    }

    // In-game map

    /**
     * {@inheritDoc}
     *
     * Called from {@code FreeColClient.restoreGUI} once a game is ready (the
     * initial active unit / focus tile are supplied here).  Build the
     * {@link ClassicMapViewer} and the in-game HUD around it
     * ({@link #installInGameHud}) in place of the title screen
     * ({@link ClassicMainMenuPanel}), then set the initial view state so the
     * map renders on the action.  The view is only built while
     * {@code mapViewer} is null; {@link #teardownInGame} resets it on the way
     * back to the title, which is what lets this run again for the next game.
     */
    @Override
    public void reconnectGUI(Unit active, Tile tile) {
        SwingUtilities.invokeLater(() -> {
            if (this.frame == null) return;
            final boolean built = this.mapViewer == null;
            if (built) {
                this.mapViewer = new ClassicMapViewer(getFreeColClient(),
                                                      this, this.imageLibrary);
                this.terrainOracle = ClassicTerrainOracle.of(getFreeColClient());
                // Whether the fog ring gets its true terrain, and the ring
                // at the start (the recorder's oracle line, W6d).
                if (ClassicFrameRecorder.on()) {
                    ClassicFrameRecorder.note("oracle", this.terrainOracle.census());
                }
                // The terrain as the original's palette indices (W6a); a
                // pack without them keeps the RGBA tiles (installInGameHud
                // logs and notes which).
                final ClassicTerrainLayer layer
                    = ClassicTerrainLayer.create(ClassicPackFiles.runtime());
                this.mapViewer.setTerrain(layer, this.terrainOracle);
                // The water cycling (W6c): the viewer's blocking waits and
                // the panel's tick fire its steps on time.
                this.waterCycle = new ClassicWaterCycle(ClassicSlide.SYSTEM,
                    SwingUtilities::invokeLater, SwingUtilities::isEventDispatchThread,
                    () -> ClassicPrefs.get().is(ClassicPrefs.WATER_CYCLING),
                    this.mapViewer::paletteStep,
                    (layer == null) ? null : layer.palette().cycle(), true);
                this.mapViewer.setSlideClock(this.waterCycle.servicing(ClassicSlide.SYSTEM));
                installInGameHud();
                // The woodcuts this game has seen (W9), before any can come.
                loadWoodcuts();
                // Phase 0 at the first in-game view (fog-start #19).
                this.waterCycle.start();
            }
            if (active != null) {
                this.mapViewer.changeToMoveUnits(active);
            } else if (tile != null) {
                this.mapViewer.changeToTerrain(tile);
            }
            // Prefer an active unit for the initial view: the original
            // always opens looking at the piece that is up, centred and
            // clamped to the map's edge (build spec W4.6: the start ship at
            // (56,42) of the 58x72 map in cell (14,6)). Checked against
            // both the passed-in unit AND the viewer's current active unit,
            // because the controller's own updateActiveUnit → changeView
            // races this reconnect at startup — whichever runs last must not
            // leave the view parked on the saved (often at-sea) tile while
            // the player hunts for their unit.
            final Unit viewerActive = this.mapViewer.getActiveUnit();
            final Tile focusTile =
                (active != null && active.getTile() != null)
                    ? active.getTile()
                : (viewerActive != null && viewerActive.getTile() != null)
                    ? viewerActive.getTile()
                : tile;
            if (focusTile != null) {
                this.mapViewer.setFocus(focusTile);
            }
            this.mapViewer.requestFocusInWindow();
            this.mapViewer.repaint();
            repaintInfo();
            updateActions();
            logger.info("ClassicGUI: in-game map installed.");
            // A fresh game opens on the original's first scene.  The engine's
            // start message and any other notice that arrived before this
            // point were held by showMessagePopup (startGameInternal reaches
            // it before this queued build runs).
            if (this.firstScenePending) {
                this.firstScenePending = false;
                if (!showFirstScene()) flushHeldMessages();
            } else if (built) {
                // A loaded save of an arrival turn (W13), after the
                // controller's first view.
                SwingUtilities.invokeLater(this::loadedArrivals);
            }
        });
    }

    /**
     * Build the in-game HUD and make it the window's content: the original's
     * screen as one integer-scaled 320x200 canvas ({@link ClassicHudPane})
     * holding the painted menu strip ({@link ClassicMenuStrip}), the map on
     * the same grid and the right panel ({@link ClassicInfoPanel}), plus the
     * explicit key map ({@link ClassicKeyMap}).  EDT only; called once per
     * game view by {@link #reconnectGUI}.
     *
     * <p>No {@code JMenuBar} any more.  FreeCol's reused {@code InGameMenuBar}
     * could only look like a Swing menu bar (and drew a gold/tax/year line the
     * original keeps in the panel); its accelerators, which only fired because
     * the bar was installed, are replaced by {@link ClassicKeyMap}.  The
     * strip's items fire the same {@code FreeColAction}s by id, so every
     * engine path (save, quit, back to the title, reports) is unchanged.
     * Without the pack the HUD still works: flat colours, Swing-font text and
     * no menu titles, the keys bound as usual.
     */
    private void installInGameHud() {
        // A high-score window that an ended game left over the title
        // (teardownInGame) belongs to that game: it goes with the new view,
        // rather than linger behind the map.
        if (this.reportFrame != null && this.reportFrame.getContentPane()
                instanceof ClassicReportHighScoresPanel) {
            closeReportPanel();
        }
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        // Whether the pack holds the original indices for the map (W6e): a
        // pack converted before them logs one warning; the recorder notes
        // it in events.log and summary.txt.
        ClassicFrameRecorder.note("terrain", ClassicPackFiles.indexStatus(pack));
        final ClassicText text = ClassicText.load(pack);
        final ClassicFont tiny = (pack == null) ? null : pack.font(ClassicFont.TINY);
        final BufferedImage wood = (pack == null) ? null
            : pack.image(ClassicMenuBar.WOOD_KEY);
        this.infoPanel = new ClassicInfoPanel(getFreeColClient(), this.mapViewer,
            this.imageLibrary, text, tiny, wood);
        this.mapViewer.setIconArt(tiny, text);
        applySessionOptions();
        // What the options box set last for Autom. Sichern, Kampfanalyse and
        // Tutortips (FreeCol options) holds in this session too (W14).
        ClassicOptionBoxes.applyRemembered(getFreeColClient().getClientOptions(),
                                           ClassicPrefs.get());
        final ActionManager am = getFreeColClient().getActionManager();
        this.menuStrip = new ClassicMenuStrip(new ClassicMenuStrip.Host() {
                @Override
                public ClassicMenuModel.Context context() {
                    final ClassicMapViewer mv = mapViewer;
                    return (mv == null) ? ClassicMenuModel.Context.NONE
                        : ClassicMenuModel.Context.of(mv.getActiveUnit(),
                                                      mv.getViewMode());
                }

                @Override
                public javax.swing.Action action(String id) {
                    final javax.swing.Action own = classicAction(id);
                    if (own != null) return own;
                    return (am == null || id == null) ? null
                        : am.getFreeColAction(id);
                }

                @Override
                public boolean inputBlocked() {
                    return sceneShowing || boxBusy();
                }

                @Override
                public void unavailable(ClassicMenuModel.Item item) {
                    // An inert row (normal ink as in the original, but no
                    // engine equivalent or no classic screen yet): say so,
                    // rather than do nothing.
                    showInformationNotice(Messages.message(
                        "classic.mainMenu.notYet"));
                }

                @Override
                public void menuOpened() {
                    ClassicFrameRecorder.event("menu-open", "");
                    if (mapViewer != null) mapViewer.holdBlink("menu");
                }

                @Override
                public void menuClosed() {
                    ClassicFrameRecorder.event("menu-close", "");
                    if (mapViewer != null) mapViewer.resumeBlink("menu");
                    if (turnFlow != null) turnFlow.boxClosed();
                }
            }, tiny, wood, text);
        // Every popup holds the map's blink ON and restarts it at its close;
        // its close also restarts a pending end of turn (build spec W5a).
        ClassicDialog.setWatcher(new ClassicDialog.Watcher() {
                @Override
                public void opened() {
                    if (mapViewer != null) mapViewer.holdBlink("dialog");
                }

                @Override
                public void closed() {
                    if (mapViewer != null) mapViewer.resumeBlink("dialog");
                    if (turnFlow != null) turnFlow.boxClosed();
                }
            });
        // The unit cycle of this game (W5f): FreeCol's goto batch off, the
        // goto units move when the cycle reaches them.
        this.unitCycle.clear();
        ClassicUnitCycle.show(this.unitCycle);
        gotoBatch(false);
        this.turnFlow = new ClassicTurnFlow(new TurnHost(), waitClock(),
            SwingUtilities::invokeLater, true);
        this.infoPanel.setTurnFlow(this.turnFlow);
        // The voyages of this game (W13): the bands, the arrival chain.
        this.voyages.clear();
        this.voyageChain = null;
        this.europeShownTurn = -1;
        this.europeAskedAt = 0L;
        this.voyageTimer = new ClassicOneShot(waitClock(),
            SwingUtilities::invokeLater, true, "ClassicVoyages");
        this.bandTimer = new ClassicOneShot(waitClock(),
            SwingUtilities::invokeLater, true, "ClassicBand");
        this.tipTimer = new ClassicOneShot(waitClock(),
            SwingUtilities::invokeLater, true, "ClassicEuropeTip");
        this.turnPoll = new javax.swing.Timer(ClassicTurnFlow.POLL_MS, e -> {
                if (turnFlow != null) {
                    turnFlow.tick();
                    noteVoyages();
                }
            });
        this.turnPoll.start();
        // The background preload waits while a timed paint is due (M1
        // acceptance F6: it slowed the first 13 s of every game).
        ResourceManager.setPreloadHold(() -> {
                final ClassicMapViewer mv = mapViewer;
                return mv != null && mv.holdsPreload();
            });
        // The arrow: hidden under the first scene (its overlay draws one)
        // and during a woodcut's dissolve (W9).
        this.hudPane = new ClassicHudPane(this.menuStrip, this.mapViewer,
            this.infoPanel, arrowSprite(pack), () -> sceneShowing
                || (boxLayer != null && boxLayer.hidesArrow()));
        // The keys fire the Classic UI's own actions as the strip does
        // (V: the view toggle, classicAction).
        ClassicKeyMap.install(this.hudPane, this.mapViewer,
            id -> {
                final javax.swing.Action own = classicAction(id);
                if (own != null) return own;
                return (am == null) ? null : am.getFreeColAction(id);
            },
            () -> (mapViewer == null) ? null : mapViewer.getViewMode(),
            () -> (mapViewer == null) ? ClassicMenuModel.Context.NONE
                : ClassicMenuModel.Context.of(mapViewer.getActiveUnit(),
                                              mapViewer.getViewMode()),
            () -> sceneShowing || boxBusy()
                || (menuStrip != null && menuStrip.isMenuOpen()),
            this::turnInputBlocked, this::orderRefused);
        // The advisor boxes on the same canvas (build spec W7).
        this.boxLayer = new ClassicAdvisorLayer(new BoxHost(pack, tiny, wood),
            waitClock(), SwingUtilities::invokeLater, true);
        this.hudPane.installBoxes(this.boxLayer);
        this.frame.setJMenuBar(null);
        this.frame.setContentPane(this.hudPane);
        this.frame.revalidate();
    }

    /** What the advisor boxes need from the game view (build spec W7). */
    private final class BoxHost implements ClassicAdvisorLayer.Host {

        private final ClassicPackFiles pack;
        private final ClassicFont tiny;
        private final BufferedImage wood;

        BoxHost(ClassicPackFiles pack, ClassicFont tiny, BufferedImage wood) {
            this.pack = pack;
            this.tiny = tiny;
            this.wood = wood;
        }

        @Override
        public ClassicFont font() {
            return this.tiny;
        }

        @Override
        public BufferedImage wood() {
            return this.wood;
        }

        @Override
        public BufferedImage portrait(String sprite) {
            return (this.pack == null || sprite == null) ? null
                : this.pack.image(ClassicPackFiles.ssKey(sprite));
        }

        @Override
        public int[] portraitPalette(ClassicAdvisorBox.Portrait p) {
            if (this.pack == null || p == null || p.sprite == null) return null;
            return ClassicAdvisorBox.portraitPalette(
                this.pack.indexSheet(p.sheet()), 0, portrait(p.sprite));
        }

        @Override
        public void opened() {
            if (menuStrip != null && menuStrip.isMenuOpen()) menuStrip.closeMenu();
            ClassicDialog.popupOpened();
        }

        @Override
        public void closed() {
            ClassicDialog.popupClosed();
        }

        @Override
        public void idle() {
            // The actions a box disabled (isDialogShowing) come back, and
            // the keys go to the map again.
            updateActions();
            if (mapViewer != null) mapViewer.requestFocusInWindow();
        }

        @Override
        public boolean isAutoRepeat(KeyEvent e) {
            return ClassicGUI.this.isAutoRepeat(e);
        }

        @Override
        public void woodcutPalette(int k, int[] entries) {
            // The water stands still under the woodcut's palette (W6c's
            // hold); the recorder's frames take it, the arrow's grey too.
            final ClassicWaterCycle wc = waterCycle;
            if (k > 0) {
                if (wc != null) wc.hold("woodcut");
                ClassicFrameRecorder.woodcutPalette(k, entries);
            } else {
                if (this.dimmed) ClassicFrameRecorder.woodcutPalette(0, null);
                if (wc != null) wc.release("woodcut");
            }
            this.dimmed = k > 0;
            if (hudPane != null) hudPane.pointer().setDimmed(this.dimmed);
        }

        /** Whether a woodcut's palette is up. */
        private boolean dimmed = false;

        @Override
        public long woodcutNotBefore() {
            return afterFinalDraw();
        }

        @Override
        public void woodcutEnded(int k) {
            markWoodcut(k);
            // The Aztecs' and the Incas' woodcut is the natives' too.
            if (k == ClassicWoodcut.AZTECS || k == ClassicWoodcut.INCAS) {
                markWoodcut(ClassicWoodcut.NATIVES);
            }
        }

        @Override
        public void arrowChanged() {
            if (hudPane != null) hudPane.pointer().refresh();
        }
    }

    /**
     * Whether an advisor box is up or due (build spec W7).  EDT only.
     *
     * @return True if so.
     */
    boolean boxBusy() {
        return (this.boxLayer != null && this.boxLayer.isBusy())
            || this.woodcutsPosted > 0;
    }

    /** @return The advisor boxes' layer, or null (the harness, tests). */
    ClassicAdvisorLayer boxLayer() {
        return this.boxLayer;
    }

    /**
     * @return The overlay showing the first scene while it is up, else null
     *     (the harness: the scene takes the keys).  EDT only.
     */
    Component sceneOverlay() {
        return (this.sceneShowing) ? this.hudOverlay : null;
    }

    /**
     * The original mouse arrow for the in-game canvas and the first scene
     * ({@link ClassicPointer}), or null without the pack (system cursor).
     */
    private static BufferedImage arrowSprite(ClassicPackFiles pack) {
        return (pack == null) ? null : ClassicMainMenuPanel.cursorSprite(
            pack.image(ClassicMainMenuPanel.CURSOR_KEY));
    }

    /**
     * A classic notice (an advisor box without rows, build spec W7), e.g.
     * for a menu row whose feature follows later.  EDT only.
     */
    private void showInformationNotice(String text) {
        this.prompter.ask(notice("notice", text,
            Messages.message("classic.dialog.messages"), null));
    }

    /**
     * A notice box of FreeCol's text: no rows, any key or a click dismisses
     * it (build spec W7).
     *
     * @param id What it is (for the recorder).
     * @param text The text.
     * @param title The stopgap's window title.
     * @param icon The stopgap's illustration, or null.
     * @return The request.
     */
    static ClassicAdvisorBox.Request notice(String id, String text, String title,
                                            java.awt.Image icon) {
        return ClassicAdvisorBox.Request.builder(id).freeColText(text)
            .stopgap(title, icon).build();
    }

    // The option boxes (build spec W14)

    /** SPIEL rows 0 and 1, made on first use. */
    private javax.swing.Action gameOptionsAction = null, colonyOptionsAction = null;

    /** FreeCol's view toggle as the Classic UI fires it ({@link #toggleView}), made on first use. */
    private javax.swing.Action viewToggleAction = null;

    /** The id of FreeCol's view toggle (ANSICHT V and M, the keys V and M). */
    static final String VIEW_TOGGLE = "toggleViewModeAction";

    /**
     * The Classic UI's own menu actions ({@link ClassicMenuModel#GAME_OPTIONS},
     * {@link ClassicMenuModel#COLONY_OPTIONS}), and its own way of firing
     * FreeCol's view toggle ({@link #VIEW_TOGGLE}): the strip fires them as
     * it fires an engine action, after the menu has closed; the key map
     * fires them for their keys.
     *
     * @param id An action id, or null.
     * @return The action, or null for any other id.
     */
    javax.swing.Action classicAction(String id) {
        if (VIEW_TOGGLE.equals(id)) {
            if (this.viewToggleAction == null) {
                this.viewToggleAction = new javax.swing.AbstractAction(VIEW_TOGGLE) {
                        @Override
                        public boolean isEnabled() {
                            final javax.swing.Action fc = freeColAction(VIEW_TOGGLE);
                            return fc == null || fc.isEnabled();
                        }

                        @Override
                        public void actionPerformed(java.awt.event.ActionEvent e) {
                            toggleView(e);
                        }
                    };
            }
            return this.viewToggleAction;
        }
        if (ClassicMenuModel.GAME_OPTIONS.equals(id)) {
            if (this.gameOptionsAction == null) {
                this.gameOptionsAction = optionsAction(ClassicOptionBoxes.GAME_SECTION);
            }
            return this.gameOptionsAction;
        }
        if (ClassicMenuModel.COLONY_OPTIONS.equals(id)) {
            if (this.colonyOptionsAction == null) {
                this.colonyOptionsAction = optionsAction(ClassicOptionBoxes.COLONY_SECTION);
            }
            return this.colonyOptionsAction;
        }
        return null;
    }

    /**
     * The view toggle, V in the units view (ANSICHT row 1): the terrain
     * view on the selected tile as the player's own tile selection
     * ({@link #selectTile}).  FreeCol's toggle calls
     * {@link #changeView(Tile)}, the controller's fallback when it has no
     * unit left, which since W5f brings the unit cycle's next due unit
     * instead: V activated the next unit, and with a visit next froze the
     * turn (G review).  M in the terrain view (row 0) is FreeCol's toggle.
     *
     * @param e The event, passed on to FreeCol's toggle.
     */
    void toggleView(java.awt.event.ActionEvent e) {
        final ClassicMapViewer mv = this.mapViewer;
        if (mv != null && mv.getViewMode() == ViewMode.MOVE_UNITS) {
            Tile t = mv.getSelectedTile();
            final Unit u = mv.getActiveUnit();
            if (t == null && u != null) t = u.getTile();
            if (t == null) t = mv.getFocus();
            ClassicFrameRecorder.event("view", "terrain"
                + ((t == null) ? "" : " at=" + t.getX() + "," + t.getY()));
            selectTile(t);
            return;
        }
        final javax.swing.Action fc = freeColAction(VIEW_TOGGLE);
        if (fc != null) fc.actionPerformed(e);
    }

    /**
     * @param id An action id.
     * @return FreeCol's action of that id, or null without a client.
     */
    private javax.swing.Action freeColAction(String id) {
        final FreeColClient fcc = getFreeColClient();
        final ActionManager am = (fcc == null) ? null : fcc.getActionManager();
        return (am == null) ? null : am.getFreeColAction(id);
    }

    /** An action that opens an option box ({@link #showOptionBox}). */
    private javax.swing.Action optionsAction(String section) {
        return new javax.swing.AbstractAction(section) {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                showOptionBox(section);
            }
        };
    }

    /**
     * SPIEL "Spieloptionen" or "Koloniebericht-Optionen": the original's
     * option box ({@link ClassicOptionBoxes}), until it is closed; each
     * flip acts at once.  Without GAME.TXT's box the "follows later"
     * notice.  EDT only.
     *
     * @param section {@link ClassicOptionBoxes#GAME_SECTION} or
     *     {@link ClassicOptionBoxes#COLONY_SECTION}.
     * @return Whether the box was asked for.
     */
    boolean showOptionBox(String section) {
        final ClassicAdvisorBox.Request r = optionBox(section);
        if (r == null) {
            showInformationNotice(Messages.message("classic.mainMenu.notYet"));
            return false;
        }
        this.prompter.ask(r);
        return true;
    }

    /**
     * An option box of the pack's GAME.TXT on the game view's store: the
     * classic prefs, the client's FreeCol options, and the water cycle
     * switched at once ({@link #optionApplied}).
     *
     * @param section The box's section.
     * @return The box, or null without the pack's text.
     */
    ClassicAdvisorBox.Request optionBox(String section) {
        final FreeColClient fcc = getFreeColClient();
        return ClassicOptionBoxes.request(ClassicText.load(ClassicPackFiles.runtime()),
            section, ClassicOptionBoxes.store(prefs(),
                (fcc == null) ? null : fcc.getClientOptions(), this::optionApplied),
            null);
    }

    /**
     * What an option row's change starts at once: the water stops or goes
     * on cycling in the same step (W6c).  The other rows are read where
     * they are used (the next slide, the next idle decision, the next
     * attack, autosave or notice, the next colony screen).
     */
    private void optionApplied(String key, boolean on) {
        if (ClassicPrefs.WATER_CYCLING.equals(key) && this.waterCycle != null) {
            this.waterCycle.setEnabled(on);
        }
    }

    /**
     * The prefs the option boxes and the report filter use: the shared ones
     * ({@code classic-options.properties}); a test puts in its own.
     *
     * @return The prefs.
     */
    ClassicPrefs prefs() {
        return ClassicPrefs.get();
    }

    // View mode / focus — delegated to the map viewer.

    /**
     * Why the map's blink must hold the active unit ON now (build spec W3),
     * or null to let it blink: the first scene, an open menu, a modal box,
     * a classic screen over the map, a hand-over on its way (the unit up
     * is the one the cycle moved past, {@link ClassicTurnFlow#holdsBlink}),
     * or not our turn (the AI phase).  EDT only; asked at every toggle.
     *
     * @return The reason, or null.
     */
    String blinkHoldReason() {
        if (this.sceneShowing) return "scene";
        if (boxBusy()) return "dialog";
        if (this.menuStrip != null && this.menuStrip.isMenuOpen()) return "menu";
        if (modalDialogShowing()) return "dialog";
        if (classicScreenUp()) return "screen";
        if (this.turnFlow != null && this.turnFlow.holdsBlink()) return "handover";
        final FreeColClient fcc = getFreeColClient();
        if (fcc == null || !fcc.currentPlayerIsMyPlayer()) return "ai";
        return null;
    }

    /**
     * Paint the panel's minimap now: the active unit's dot follows its
     * blink, in the same paint as the map's cell or one later.  EDT only.
     */
    void paintBlinkDot() {
        if (this.infoPanel != null) this.infoPanel.paintMinimapNow();
    }

    /**
     * The panel's refresh after a key move's final draw (M1 acceptance
     * F2): the minimap at once, with the final draw and its reveal
     * (landfall #1166 -&gt; #1167), the rest of the panel one tick later
     * (the block 1-3 frames after the final draw: +1 in 36 of 47 slides,
     * +2 in 9, never in its frame; ours came in the same frame in 21 of
     * 53).  The event thread is held for the tick, as by the slide.  EDT
     * only.
     *
     * @param finalNanos When the final draw was painted.
     */
    void panelAfterFinalDraw(long finalNanos) {
        // A woodcut comes after this final draw: the original refreshes
        // no panel before it (landfall #2107 -> #2111); the map's return
        // repaints everything.
        if (this.infoPanel == null || this.woodcutsPosted > 0) return;
        this.infoPanel.paintMinimapNow();
        try {
            waitClock().waitUntil(finalNanos + Math.round(PANEL_AFTER_FINAL_MS * 1e6));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        this.infoPanel.paintNow();
    }

    /** {@link #panelAfterFinalDraw}: one game tick (ms). */
    static final double PANEL_AFTER_FINAL_MS = ClassicSlide.STEP_MS;

    /** Repaint the HUD info panel if it exists (view/model state changed). */
    private void repaintInfo() {
        if (this.infoPanel != null) this.infoPanel.repaint();
        if (this.europePanel != null) this.europePanel.refresh();
        if (this.colonyPanel != null) this.colonyPanel.refresh();
    }

    /**
     * Refresh the enabled state of the reused {@code FreeColAction}s (and hence
     * the menu items wired to them).  {@code SwingGUI} does this through the
     * {@code Canvas} on every view change / panel open; the classic UI has no
     * {@code Canvas}, so without this call the menu items keep the (disabled)
     * state they were built with — e.g. the {@code Europe} item never enables and
     * the map/turn menus stay greyed.  Cheap and idempotent (it just re-evaluates
     * {@code shouldBeEnabled} on each action).
     */
    private void updateActions() {
        try {
            getFreeColClient().updateActions();
        } catch (Exception e) {
            logger.log(Level.WARNING, "ClassicGUI: updateActions failed.", e);
        }
        repaintHud();
    }

    /**
     * {@inheritDoc}
     *
     * <p>The controller's fallback tile when no unit is left: the unit
     * cycle's next due unit if there is one (a goto unit, a visit, master
     * plan W5f; {@link ClassicTurnFlow#dueInstead}), else the turn flow
     * arms the automatic end if nothing can move any more (build spec W5a).
     * The player's view toggle no longer comes here ({@link #toggleView}).
     */
    @Override
    public void changeView(Tile tile) {
        if (this.mapViewer != null) {
            if (droppedViewChange("tile")) return;
            if (this.turnFlow != null
                && this.turnFlow.dueInstead(this.mapViewer.getActiveUnit())) {
                repaintInfo();
                updateActions();
                return;
            }
        }
        selectTile(tile);
    }

    /**
     * The player's own tile selection (the cursor keys in the terrain
     * view, a click on an empty tile): the terrain view, and the turn flow
     * arms the automatic end if nothing can move any more (build spec
     * W5a).  Never a hand-over to a due unit.
     *
     * @param tile The tile.
     */
    void selectTile(Tile tile) {
        if (this.mapViewer != null) {
            if (droppedViewChange("tile")) return;
            this.mapViewer.changeToTerrain(tile);
            if (this.turnFlow != null) this.turnFlow.noUnitLeft();
        }
        repaintInfo();
        updateActions();
    }

    /**
     * {@inheritDoc}
     *
     * <p>A different unit after the previous one ran out of moves, and the
     * first unit of our turn, come up after the original's pause (build
     * spec W5e): the turn flow takes them over and activates them later.
     * A unit off the map with nothing left to move is no unit: the end
     * view, as {@link #changeView()} ({@link #goneWithNothingLeft}).
     *
     * <p>After a landing the ship's re-selection hands over to the next
     * unit in the cycle instead ({@link #landingDone}); after a boarding
     * the carrier comes next, sooner ({@link #carrierAfterBoarding}; spec
     * delta W18).
     */
    @Override
    public void changeView(Unit unit, boolean force) {
        changeView(unit, false, "unit");
    }

    /**
     * A click on an own unit: it becomes active as the controller's choice
     * would ({@link #changeView(Unit, boolean)}), but at a hand-over the
     * unit cycle does not replace it (master plan W5f).
     *
     * @param unit The unit clicked.
     */
    void unitClicked(Unit unit) {
        changeView(unit, true, "click");
    }

    /**
     * {@link #changeView(Unit, boolean)}, for the controller's choice or
     * the player's click.
     *
     * @param unit The unit, or null.
     * @param clicked The player clicked it.
     * @param what Which change (for the recorder).
     */
    private void changeView(Unit unit, boolean clicked, String what) {
        if (this.mapViewer != null) {
            if (droppedViewChange(what)) return;
            final Unit previous = this.mapViewer.getActiveUnit();
            final Unit carrier;
            if (landingDone(unit)) {
                // The hand-over from the landed unit is on its way.
            } else if (goneWithNothingLeft(unit, this.unitCycle)) {
                ClassicFrameRecorder.event("handover", "off the map "
                    + unit.getId() + ": no unit left");
                this.mapViewer.changeToEndTurn();
                if (this.turnFlow != null) this.turnFlow.noUnitLeft();
            } else if ((carrier = carrierAfterBoarding(unit, previous)) != null) {
                if (this.turnFlow == null
                    || !this.turnFlow.carrierChosen(carrier, previous)) {
                    activateNow(carrier);
                }
            } else if (this.turnFlow == null
                || !((clicked) ? this.turnFlow.unitClicked(unit, previous)
                     : this.turnFlow.unitChosen(unit, previous))) {
                activateNow(unit);
            }
        }
        repaintInfo();
        updateActions();
    }

    /**
     * Make a unit the map's active unit now: the panel's block follows it
     * again, also after a landing held it.
     *
     * @param unit The unit.
     */
    private void activateNow(Unit unit) {
        if (this.infoPanel != null) this.infoPanel.releaseBlock();
        this.mapViewer.changeToMoveUnits(unit);
    }

    /**
     * Whether a unit the controller chose is off the map (a ship that has
     * sailed for Europe, a unit in Europe, a unit gone) while none of its
     * owner's units can move or go to its destination (D acceptance D1).
     * The controller re-selects such a ship after its last move
     * ({@code moveDirection}'s redisplay, {@code doExecuteGotoOrders}'
     * restore of the active unit); after the order "Zurück nach Europa"
     * its end view did not follow (its goto mode left over), so the ship
     * stayed the active unit and the turn never ended (5 of 5 runs).  With
     * a unit left to move (or due in the unit cycle: a goto unit on the
     * map, a visit) the choice goes on, as a hand-over from the ship.  A
     * unit with a destination in Europe is not due: FreeCol's end-of-turn
     * goto pass sends it.
     *
     * @param unit The unit chosen, or null.
     * @param cycle The unit cycle.
     * @return True if it is no unit to show.
     */
    static boolean goneWithNothingLeft(Unit unit, ClassicUnitCycle cycle) {
        if (unit == null || unit.hasTile()) return false;
        final Player p = unit.getOwner();
        return p == null || (!p.hasNextActiveUnit() && !cycle.anyDue(p));
    }

    /**
     * {@inheritDoc}
     *
     * <p>No unit left: the unit cycle's next due unit if there is one (a
     * goto unit, a visit, master plan W5f), else the turn flow ends the
     * turn 485 ms after the last change (build spec W5a), never FreeCol's
     * {@code autoEndTurn}.
     */
    @Override
    public void changeView() {
        if (this.mapViewer != null) {
            if (droppedViewChange("end")) return;
            if (this.turnFlow == null
                || !this.turnFlow.dueInstead(this.mapViewer.getActiveUnit())) {
                this.mapViewer.changeToEndTurn();
                if (this.turnFlow != null) this.turnFlow.noUnitLeft();
            }
        }
        repaintInfo();
        updateActions();
    }

    /**
     * Whether a view change of the controller is dropped: while a goto
     * unit runs, FreeCol asks for one after each step and for the next
     * unit after the run, which the unit cycle decides (master plan W5f,
     * {@link ClassicTurnFlow#ignoring}).
     *
     * @param what Which change (for the recorder).
     * @return True if dropped.
     */
    private boolean droppedViewChange(String what) {
        if (this.turnFlow == null || !this.turnFlow.ignoring()) return false;
        ClassicFrameRecorder.event("handover", "dropped view change " + what);
        return true;
    }

    /**
     * End the turn now on the player's request (Enter, or Space with no
     * unit up), through the turn flow, which lights the turn indicator
     * first (build spec W5c).
     *
     * @param why What asks (for the recorder).
     */
    void requestEndTurn(String why) {
        if (this.turnFlow != null) {
            this.turnFlow.endTurnNow(why);
            return;
        }
        ClassicFrameRecorder.event("end-turn", why);
        getFreeColClient().getInGameController().endTurn(false);
    }

    /**
     * Whether the map must ignore keys and clicks because the player waits
     * (build spec W5d, {@link ClassicTurnFlow#isInputBlocked}).
     *
     * @return True while blocked.
     */
    boolean turnInputBlocked() {
        return this.turnFlow != null && this.turnFlow.isInputBlocked();
    }

    /**
     * The true terrain of the fog ring (M1c design 10 §5, W6d), for the
     * terrain composer alone: the minimap and every other consumer keep
     * using {@code Tile.isExplored} (least knowledge).
     *
     * @return The oracle of the game view, or null without one.
     */
    ClassicTerrainOracle terrainOracle() {
        return this.terrainOracle;
    }

    /**
     * The water cycling of the game view (W6c), for the woodcuts' freeze
     * (W9: {@code hold("woodcut")} / {@code release("woodcut")}) and the
     * options box (W14: {@code setEnabled}).
     *
     * @return The cycle, or null without a game view.
     */
    ClassicWaterCycle waterCycle() {
        return this.waterCycle;
    }

    /**
     * The clock of the blocking waits on the event thread: the water
     * cycle's servicing clock, so a palette step due meanwhile comes on
     * time (design 10 §7.2), else the system clock.
     *
     * @return The clock.
     */
    ClassicSlide.Clock waitClock() {
        final ClassicMapViewer mv = this.mapViewer;
        return (mv == null) ? ClassicSlide.SYSTEM : mv.slideClock();
    }

    /** @return Whether the Spielzugende mode is on (build spec W17). */
    boolean turnPrompt() {
        return this.turnFlow != null && this.turnFlow.isPrompt();
    }

    /**
     * Whether Enter (or Space with no unit) may end the turn now: in the
     * Spielzugende mode, or with no unit up (none, or one off the map: a
     * ship that has sailed for Europe, D acceptance D1) and none left that
     * can move (spec delta W17 item 7).  Without the turn flow, always.
     *
     * @return True if the key ends the turn.
     */
    boolean mayEndTurnByKey() {
        if (this.turnFlow == null || this.turnFlow.isPrompt()) return true;
        final Player p = getMyPlayer();
        final Unit active = (this.mapViewer == null) ? null
            : this.mapViewer.getActiveUnit();
        return this.mapViewer != null && (active == null || !active.hasTile())
            && p != null && !p.hasNextActiveUnit() && !this.unitCycle.anyDue(p);
    }

    /**
     * Paint the panel now for the Spielzugende mode's square: the word and
     * the minimap pixel change with it (build spec W17 item 6).  EDT only.
     */
    void paintPromptPanel() {
        if (this.infoPanel != null) this.infoPanel.paintNow();
    }

    /**
     * The map's Spielzugende square went: a live mode's panel goes back to
     * the unit block; a frozen one keeps its word until the wipe.
     *
     * @param frozen The square was frozen by the end command.
     * @param minimap Repaint the minimap's pixel now.
     */
    void promptCleared(boolean frozen, boolean minimap) {
        if (this.infoPanel == null) return;
        if (!frozen) {
            this.infoPanel.leavePrompt();
        } else if (minimap) {
            this.infoPanel.paintMinimapNow();
        }
    }

    /** The player saw the map or the panel change: the pauses run from the last change. */
    void screenChanged() {
        if (this.turnFlow != null) this.turnFlow.screenChanged();
    }

    /**
     * Whether the turn flow keeps the player waiting (the acceptance
     * harness's idle test, {@link ClassicTurnFlow#isBusy}).
     *
     * @return True while a pause is pending or our new turn is not shown.
     */
    boolean turnFlowBusy() {
        return this.turnFlow != null && this.turnFlow.isBusy();
    }

    /** @return The turn flow's state for the recorder's probe (any thread). */
    String turnFlowState() {
        final ClassicTurnFlow f = this.turnFlow;
        return (f == null) ? "-" : f.probeState();
    }

    /**
     * Whether anything is up that the end of turn and the next unit must
     * wait for: the first scene, a menu, a modal box, a classic screen.
     *
     * @return True if so.
     */
    private boolean turnBlocked() {
        if (this.sceneShowing || modalDialogShowing() || boxBusy()) return true;
        if (this.menuStrip != null && this.menuStrip.isMenuOpen()) return true;
        return classicScreenUp();
    }

    /**
     * Whether a classic screen (colony, Europe, report, build queue) is up
     * where the player can be looking at it ({@link #screenUp}).  While
     * windowed the player can bring the map in front of an open screen by
     * clicking it (see {@link #dialogOwner}), and the map's keys work then;
     * a screen behind the active map, or a minimized one, must not hold the
     * hand-overs, the turn start, the map's input and the blink.  EDT only.
     *
     * @return True if one is up.
     */
    private boolean classicScreenUp() {
        final boolean mapActive = this.frame != null && this.frame.isActive();
        for (Window w : new Window[] { this.colonyFrame, this.europeFrame,
                                        this.reportFrame, this.buildQueueFrame }) {
            if (w != null && screenUp(w.isShowing(), w instanceof Frame
                    && (((Frame) w).getExtendedState() & Frame.ICONIFIED) != 0,
                    mapActive)) return true;
        }
        return false;
    }

    /**
     * The rule of {@link #classicScreenUp} for one screen.
     *
     * @param showing The screen's window is showing.
     * @param minimized It is minimized.
     * @param mapActive The main frame (the map) is the active window.
     * @return True if the screen holds the turn flow and the blink.
     */
    static boolean screenUp(boolean showing, boolean minimized,
                            boolean mapActive) {
        return showing && !minimized && !mapActive;
    }

    /**
     * Switch FreeCol's goto batch ({@code InGameController.setGotoBatch})
     * and its goto stop at a region to discover ({@code setRegionStops},
     * BR#2707): both off while the classic game view is up, where each
     * goto unit moves when the unit cycle reaches it (master plan W5f) and
     * runs on through new regions, whose names are answered at once
     * ({@link #showNamingDialog}; clip006 U22: three road steps in one
     * run); on again when the view goes, so the standard GUI (and a test
     * after it in the same JVM) gets FreeCol's.
     *
     * @param on True for FreeCol's batch and stop.
     */
    private void gotoBatch(boolean on) {
        final FreeColClient fcc = getFreeColClient();
        final net.sf.freecol.client.control.InGameController igc
            = (fcc == null) ? null : fcc.getInGameController();
        if (igc == null) return;
        igc.setGotoBatch(on);
        igc.setRegionStops(on);
    }

    /**
     * Whether a land unit's goto run is under way: FreeCol's colony screen
     * on its arrival at its destination colony is dropped (c6 U25 arrives
     * at Base with moves left: "Keine Befehle", no screen).  A ship's stays
     * (the original opens the colony screen on any docking, clip008
     * 01-shipcolony; I for a goto).  The cycle's run, and the run G starts
     * at once (R2; I: c6 U25 is the cycle's).
     *
     * @return True while a land unit's goto runs.
     */
    boolean landGotoRunning() {
        final ClassicTurnFlow f = this.turnFlow;
        return dropsColonyScreen((f == null) ? null : f.gotoUnit())
            || dropsColonyScreen(this.keyGotoUnit);
    }

    /**
     * The rule of {@link #landGotoRunning}.
     *
     * @param going The unit whose goto runs now, or null.
     * @return True if FreeCol's colony screen is dropped.
     */
    static boolean dropsColonyScreen(Unit going) {
        return going != null && !going.isNaval();
    }

    /**
     * W (wait): the unit cycle's next unit comes at once, the waiting unit
     * again after the wrap ({@link ClassicTurnFlow#waited}); without the
     * turn flow FreeCol's {@code waitUnit}.
     *
     * @param unit The active unit, or null.
     */
    void waitUnit(Unit unit) {
        final ClassicTurnFlow f = this.turnFlow;
        if (f != null && unit != null) {
            f.waited(unit);
            return;
        }
        final FreeColClient fcc = getFreeColClient();
        if (fcc != null) fcc.getInGameController().waitUnit();
    }

    /** What the turn flow drives: the controller, the map and the panel. */
    private final class TurnHost implements ClassicTurnFlow.Host {

        @Override
        public boolean myTurn() {
            final FreeColClient fcc = getFreeColClient();
            return fcc != null && fcc.currentPlayerIsMyPlayer();
        }

        @Override
        public boolean blocked() {
            return turnBlocked();
        }

        @Override
        public boolean hasNextActiveUnit() {
            final Player p = getMyPlayer();
            return p != null && p.hasNextActiveUnit();
        }

        @Override
        public ClassicUnitCycle.Kind dueKind(Unit unit) {
            return unitCycle.kind(unit);
        }

        @Override
        public Unit cycleNext(Unit anchor) {
            return unitCycle.next(anchor, getMyPlayer());
        }

        @Override
        public boolean anyDue() {
            return unitCycle.anyDue(getMyPlayer());
        }

        @Override
        public void putBack(Unit unit) {
            final Player p = getMyPlayer();
            if (p != null && unit != null) p.putBackActiveUnit(unit);
        }

        @Override
        public void turnBegins() {
            unitCycle.turnStarted(getMyPlayer());
        }

        @Override
        public void turnEnding() {
            unitCycle.snapshot(getMyPlayer());
            voyages.note(getMyPlayer());
        }

        @Override
        public void endRefused() {
            unitCycle.endRefused();
            if (mapViewer != null) mapViewer.repaint();
        }

        @Override
        public int turnNumber() {
            final Game g = getGame();
            return (g == null || g.getTurn() == null) ? -1
                : g.getTurn().getNumber();
        }

        @Override
        public boolean promptPref() {
            return ClassicPrefs.get().is(ClassicPrefs.END_TURN_PROMPT);
        }

        @Override
        public Unit activeUnit() {
            return (mapViewer == null) ? null : mapViewer.getActiveUnit();
        }

        @Override
        public boolean wouldJump(Unit unit) {
            return mapViewer != null && unit != null
                && mapViewer.wouldJump(unit.getTile());
        }

        @Override
        public void jumpTo(Unit unit) {
            if (mapViewer != null) mapViewer.jumpTo(unit.getTile(), "handover");
        }

        @Override
        public void activate(Unit unit) {
            if (mapViewer == null) return;
            if (infoPanel != null) infoPanel.releaseBlock();
            mapViewer.changeToMoveUnits(unit);
            // The block now, on time, in ONE panel paint: it is the panel
            // refresh the blink's first OFF is timed from (W3), and a second
            // one queued behind it would shift that phase.  So the actions
            // are refreshed without updateActions' panel repaint.
            if (infoPanel != null) infoPanel.paintNow();
            try {
                getFreeColClient().updateActions();
            } catch (Exception e) {
                logger.log(Level.WARNING, "ClassicGUI: updateActions failed.", e);
            }
            if (menuStrip != null) {
                menuStrip.repaint();
                menuStrip.dropLayer().repaint();
            }
            if (europePanel != null) europePanel.refresh();
            if (colonyPanel != null) colonyPanel.refresh();
        }

        @Override
        public void wipe() {
            if (mapViewer != null && mapViewer.getActiveUnit() != null) {
                mapViewer.changeToEndTurn();
            }
            if (infoPanel != null) {
                infoPanel.clearStale();
                infoPanel.paintNow();
            }
        }

        @Override
        public void paintIndicator() {
            if (infoPanel != null) infoPanel.paintIndicatorNow();
        }

        @Override
        public boolean endTurn() {
            return getFreeColClient().getInGameController().endTurn(false);
        }

        @Override
        public void runGoto(Unit unit) {
            if (mapViewer == null) return;
            unitCycle.ran(unit);
            // The goto unit is the one up while it moves (its block counts
            // its moves down).
            if (mapViewer.getActiveUnit() != unit) mapViewer.changeToMoveUnits(unit);
            getFreeColClient().getInGameController().moveToDestination(unit);
        }

        @Override
        public void visit(Unit unit) {
            if (mapViewer != null) mapViewer.visit(unit);
        }

        @Override
        public void visitShown(Unit unit) {
            unitCycle.visited(unit);
            if (mapViewer != null) mapViewer.visitShown(unit);
        }

        @Override
        public void nextActiveUnit() {
            getFreeColClient().getInGameController().nextActiveUnit();
        }

        @Override
        public void enterPrompt(Tile village) {
            // Build spec W17: the panel's tile mode with the word and the
            // map's square on the cursor tile, in one go (landing-slow
            // #2305/#2306); then it waits for Enter or a press on the word.
            if (mapViewer == null) return;
            final Tile tile = (village != null) ? village : mapViewer.promptTileFor();
            if (infoPanel != null) infoPanel.enterPrompt(tile);
            mapViewer.enterPrompt(tile);   // paints the square, then the panel
        }

        @Override
        public void freezePrompt() {
            if (mapViewer != null) mapViewer.freezePrompt();
            if (infoPanel != null) infoPanel.freezePrompt();
        }

        @Override
        public void leavePrompt() {
            if (mapViewer != null) mapViewer.clearPrompt("left", true);
            if (infoPanel != null) infoPanel.leavePrompt();
        }

        @Override
        public Player currentPlayer() {
            final Game g = getGame();
            return (g == null) ? null : g.getCurrentPlayer();
        }

        @Override
        public Player myPlayer() {
            return getMyPlayer();
        }

        @Override
        public Player nextPlayer() {
            final Game g = getGame();
            return (g == null) ? null : g.getNextPlayer();
        }

        @Override
        public void post(Runnable r) {
            SwingUtilities.invokeLater(r);
        }

        @Override
        public boolean holdTurnStart() {
            return ClassicGUI.this.holdTurnStart();
        }

        @Override
        public boolean europeOpen() {
            return europeHoldsTheEnd();
        }

        @Override
        public boolean bandUp() {
            return menuStrip != null && menuStrip.band() != null;
        }

        @Override
        public boolean openEuropeInstead() {
            return ClassicGUI.this.openEuropeInstead();
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>The base seam no-ops, which made every move an abrupt teleport; the
     * classic viewer slides the sprite tile-to-tile like the original game.
     */
    @Override
    public void animateUnitMove(Unit unit, Tile srcTile, Tile dstTile) {
        if (this.mapViewer == null) return;
        final String pref = movesPref(unit, getMyPlayer());
        if (pref != null && !ClassicPrefs.get().is(pref)) {
            this.mapViewer.moveDequeued(unit, srcTile, dstTile);
            if (ClassicFrameRecorder.on()) {
                ClassicFrameRecorder.event("slide-skip", "unit=" + unit.getId()
                    + " " + pref + "=false");
            }
            return;
        }
        this.mapViewer.animateMove(unit, srcTile, dstTile);
    }

    /**
     * {@inheritDoc}
     *
     * <p>A foreign unit's move: the map keeps it at its source until its
     * slide starts (M1 acceptance F5; the model runs ahead of the slides
     * in the AI phase).  Our own moves slide at once, inside the move.
     * Any thread (the network thread).
     */
    @Override
    public void animateUnitMoveQueued(Unit unit, Tile srcTile, Tile dstTile) {
        final ClassicMapViewer mv = this.mapViewer;
        if (mv == null || movesPref(unit, getMyPlayer()) == null) return;
        mv.moveQueued(unit, srcTile, dstTile);
    }

    /**
     * The classic pref that decides whether a unit's moves are shown (the
     * original's "Bewegungen der Indianer zeigen" and "... der anderen
     * Europäer zeigen" rows, read live): the natives' row for native
     * units, the Europeans' for any other foreign unit.  FreeCol's
     * {@code enemyMoveAnimationSpeed} is not consulted; the HUD ignores it
     * (spec delta section 2).
     *
     * @param unit The moving unit.
     * @param me The client's player, or null.
     * @return {@link ClassicPrefs#SHOW_NATIVE_MOVES},
     *     {@link ClassicPrefs#SHOW_EUROPEAN_MOVES}, or null for the
     *     player's own units (always shown).
     */
    static String movesPref(Unit unit, Player me) {
        final Player owner = (unit == null) ? null : unit.getOwner();
        if (owner == null || owner == me) return null;
        return owner.isIndian() ? ClassicPrefs.SHOW_NATIVE_MOVES
            : ClassicPrefs.SHOW_EUROPEAN_MOVES;
    }

    /**
     * FreeCol client options the in-game Classic UI sets for its session,
     * restored by {@link #teardownInGame}: option id to the classic value.
     * <ul>
     *   <li>{@code unitLastMoveDelay} off: FreeCol sleeps 300 ms on the EDT
     *   after a unit's last move, before the move returns, which held the
     *   slide at offset 15 for 300 ms more (build spec W2: the final draw
     *   comes 72 ms after offset 15).  The pause before the next unit or the
     *   end of turn is the Classic UI's own (W5).</li>
     *   <li>{@code autoEndTurn} off: FreeCol's automatic end comes at once,
     *   inside the controller's last {@code updateActiveUnit}, with no
     *   pause.  The Classic UI ends the turn itself, 485 ms after the last
     *   change ({@link ClassicTurnFlow}, build spec section 2 and W5a).</li>
     * </ul>
     */
    static final Map<String, Boolean> SESSION_OPTIONS
        = Map.of(ClientOptions.UNIT_LAST_MOVE_DELAY, Boolean.FALSE,
                 ClientOptions.AUTO_END_TURN, Boolean.FALSE);

    /** The values {@link #applySessionOptions} replaced, by option id. */
    private final Map<String, Boolean> replacedOptions = new HashMap<>();

    /** Set {@link #SESSION_OPTIONS}, remembering the values they replace. */
    private void applySessionOptions() {
        final FreeColClient fcc = getFreeColClient();
        final ClientOptions co = (fcc == null) ? null : fcc.getClientOptions();
        if (co == null) return;
        for (Map.Entry<String, Boolean> e : SESSION_OPTIONS.entrySet()) {
            try {
                final BooleanOption o = co.getOption(e.getKey(), BooleanOption.class);
                this.replacedOptions.putIfAbsent(e.getKey(), o.getValue());
                o.setValue(e.getValue());
            } catch (RuntimeException ex) {
                logger.log(Level.WARNING, "ClassicGUI: no option " + e.getKey(), ex);
            }
        }
    }

    /** Put back the values {@link #applySessionOptions} replaced. */
    private void restoreSessionOptions() {
        final FreeColClient fcc = getFreeColClient();
        final ClientOptions co = (fcc == null) ? null : fcc.getClientOptions();
        if (co != null) {
            for (Map.Entry<String, Boolean> e : this.replacedOptions.entrySet()) {
                try {
                    co.getOption(e.getKey(), BooleanOption.class).setValue(e.getValue());
                } catch (RuntimeException ex) {
                    logger.log(Level.WARNING, "ClassicGUI: no option " + e.getKey(), ex);
                }
            }
        }
        this.replacedOptions.clear();
    }

    /**
     * {@inheritDoc}
     *
     * <p>The controller calls this after every model change; {@code SwingGUI}
     * repaints its menu bar through the {@code Canvas}, which the classic UI
     * does not have — without this override the reused menu bar's golden
     * gold/tax/year status line kept its start-of-session values.
     */
    @Override
    public void updateMenuBar() {
        repaintHud();
    }

    /**
     * Repaint the painted HUD: the strip (its open dropdown reads the
     * actions' enabled state at paint time) and the panel (gold, tax and
     * year live there now, as in the original).
     */
    private void repaintHud() {
        if (this.menuStrip != null) {
            this.menuStrip.repaint();
            this.menuStrip.dropLayer().repaint();
        }
        if (this.infoPanel != null) this.infoPanel.repaint();
    }

    /**
     * {@inheritDoc}
     *
     * <p>The base seam no-ops; here it closes the menu strip's open dropdown
     * (the engine calls it before it shows something that needs the screen,
     * e.g. {@code PreGameController.startGameInternal}).
     */
    @Override
    public void closeMenus() {
        if (SwingUtilities.isEventDispatchThread()) {
            if (this.menuStrip != null) this.menuStrip.closeMenu();
        } else {
            SwingUtilities.invokeLater(this::closeMenus);
        }
    }

    /** {@inheritDoc} */
    @Override
    public ViewMode getViewMode() {
        return (this.mapViewer != null) ? this.mapViewer.getViewMode()
            : super.getViewMode();
    }

    /** {@inheritDoc} */
    @Override
    public Unit getActiveUnit() {
        return (this.mapViewer != null) ? this.mapViewer.getActiveUnit() : null;
    }

    /** {@inheritDoc} */
    @Override
    public Tile getSelectedTile() {
        return (this.mapViewer != null) ? this.mapViewer.getSelectedTile() : null;
    }

    /** {@inheritDoc} */
    @Override
    public Tile getFocus() {
        return (this.mapViewer != null) ? this.mapViewer.getFocus() : null;
    }

    /** {@inheritDoc} */
    @Override
    public void setFocus(Tile tile) {
        if (this.mapViewer != null) this.mapViewer.setFocus(tile);
    }

    /** {@inheritDoc} */
    @Override
    public void refresh() {
        if (this.mapViewer != null) {
            // A refresh signals a model change (exploration, settlements, unit
            // moves), so rebuild the minimap raster before the next paint.
            this.mapViewer.invalidateMinimap();
            this.mapViewer.repaint();
        }
        repaintInfo();
    }

    /** {@inheritDoc} */
    @Override
    public void refreshTile(Tile tile) {
        if (this.mapViewer != null) {
            this.mapViewer.invalidateMinimap();
            this.mapViewer.repaint();
        }
        repaintInfo();
    }

    // Core screens

    /**
     * {@inheritDoc}
     *
     * Phase 2: show the classic colony screen — {@link ClassicColonyPanel}, a
     * 320&times;200 repaint of the original's signature screen — in a window of
     * its own (the classic UI has no {@code Canvas} to host panels in).  Reached
     * by clicking an owned colony on the map, and automatically by
     * {@code InGameController.buildColony} the moment a colony is founded.
     *
     * <p>Only one colony screen is open at a time; opening another replaces it.
     * The whole thing is guarded, so a failure degrades to a log line rather
     * than breaking the map.  Returns null (as the base {@code GUI} does): no
     * caller uses the returned panel, and the classic screen is not a
     * {@code FreeColPanel}.
     */
    @Override
    public FreeColPanel showColonyPanel(Colony colony, Unit unit) {
        if (colony == null) return null;
        if (landGotoRunning()) {
            // A land unit arrived at its destination colony by goto: it
            // stays the active unit, no screen (c6 U25, master plan W5f).
            ClassicFrameRecorder.event("handover", "colony screen dropped: goto arrival at "
                + colony.getName());
            return null;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                // The first colony's woodcut, before its screen (W9).
                if (!foundingWoodcut(colony)) return;
                closeColonyPanel();
                final ClassicColonyPanel panel = new ClassicColonyPanel(
                    getFreeColClient(), this.imageLibrary, colony,
                    this::closeColonyPanel);
                final JFrame f = new JFrame(colony.getName());
                this.colonyFrame = f;
                this.colonyPanel = panel;
                f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                f.setBackground(Color.BLACK);
                f.setContentPane(panel);
                prepareChildWindow(f, this.frame, true);
                f.setVisible(true);
                f.getContentPane().requestFocusInWindow();
            } catch (Exception e) {
                logger.log(Level.WARNING, "ClassicGUI: could not show colony "
                    + "screen for " + colony.getId(), e);
            }
        });
        return null;
    }

    /** Dismiss the colony screen if one is open. */
    private void closeColonyPanel() {
        final JFrame f = this.colonyFrame;
        this.colonyFrame = null;
        this.colonyPanel = null;
        if (f != null) f.dispose();
    }

    /**
     * {@inheritDoc}
     *
     * Phase 2: the classic <b>build-queue</b> screen — {@link
     * ClassicBuildQueuePanel}, a single-selection "what shall we build next"
     * list mirroring the original 1994 game's build menu (not FreeCol's own
     * drag-reorderable multi-item queue). Reached by clicking the colony
     * screen's construction indicator ({@link ClassicColonyPanel}, top-left of
     * the buildings pane).
     *
     * <p>Only one build-queue screen is open at a time; guarded so a failure
     * degrades to a log line rather than breaking the colony screen behind it.
     */
    @Override
    public FreeColPanel showBuildQueuePanel(Colony colony) {
        if (colony == null) return null;
        SwingUtilities.invokeLater(() -> {
            try {
                closeBuildQueuePanel();
                final JFrame f = new JFrame(Messages.message(
                    "classic.buildQueue.header"));
                this.buildQueueFrame = f;
                f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                // InGameController.setBuildQueue does not itself call
                // GUI.refresh() (its updateGUI only refreshes the map controls
                // and menu bar), so the colony screen behind this one would
                // otherwise keep showing the old construction indicator after
                // a pick; repaint it whenever this screen closes.
                f.setContentPane(new ClassicBuildQueuePanel(getFreeColClient(),
                        this.imageLibrary, colony, () -> {
                            closeBuildQueuePanel();
                            repaintInfo();
                        }));
                f.setBackground(Color.BLACK);
                prepareChildWindow(f, isOpen(this.colonyFrame)
                    ? this.colonyFrame : this.frame, true);
                f.setVisible(true);
                f.getContentPane().requestFocusInWindow();
            } catch (Exception e) {
                logger.log(Level.WARNING, "ClassicGUI: could not show build "
                    + "queue for " + colony.getId(), e);
            }
        });
        return null;
    }

    /** Dismiss the build-queue screen if one is open. */
    private void closeBuildQueuePanel() {
        final JFrame f = this.buildQueueFrame;
        this.buildQueueFrame = null;
        if (f != null) f.dispose();
    }

    /**
     * {@inheritDoc}
     *
     * Phase 2: show the classic Europe screen — {@link ClassicEuropePanel}, a
     * 320&times;200 repaint of the original's harbour — in a window of its own
     * (the classic UI has no {@code Canvas} to host panels in).  Reached by the
     * {@code Europe} menu action (accelerator {@code E}), and by the Classic
     * UI itself when a ship of ours arrives in Europe (W13, the arrival
     * chain; FreeCol's controller never opens it) and by the guard
     * ({@link #openEuropeInstead}).
     *
     * <p>Only one Europe screen is open at a time; opening another replaces it
     * (not a close for the turn flow).  The player's close, by the screen's
     * own exit or by the window's close box / Alt+F4, is
     * {@link #europeClosed}.  The game's first Europe screen brings
     * {@code @TUTORIAL17} ({@link #europeTip}).  Guarded so a failure
     * degrades to a log line rather than breaking the map; the turn flow
     * then waits no longer than {@link ClassicVoyages#EUROPE_TIMEOUT_MS}.
     */
    @Override
    public FreeColPanel showEuropePanel() {
        final Player player = getMyPlayer();
        if (player == null || player.getEurope() == null) return null;
        this.europeShownTurn = turnNumber();
        this.europeAskedAt = waitClock().now();
        SwingUtilities.invokeLater(() -> {
            try {
                closeEuropePanel();
                final ClassicEuropePanel panel = new ClassicEuropePanel(
                    getFreeColClient(), this.imageLibrary, player.getEurope(),
                    this::europeClosed);
                final JFrame f = new JFrame(Messages.message(player.getEurope()
                        .getNameKey()));
                this.europeFrame = f;
                this.europePanel = panel;
                f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                f.addWindowListener(new WindowAdapter() {
                        @Override
                        public void windowClosing(WindowEvent e) {
                            if (europeFrame == f) europeClosed();
                        }
                    });
                f.setBackground(Color.BLACK);
                f.setContentPane(panel);
                prepareChildWindow(f, this.frame, true);
                f.setVisible(true);
                panel.requestFocusInWindow();
                this.europeAskedAt = 0L;
                ClassicFrameRecorder.event("europe-open", "turn=" + turnNumber());
                if (this.tipTimer != null && !tipShown(player,
                        ClassicBands.TUTORIAL_EUROPE_NUMBER)) {
                    this.tipTimer.schedule(waitClock().now() + ClassicVoyages
                        .nanos(ClassicVoyages.TUTORIAL_MS), this::europeTip);
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "ClassicGUI: could not show Europe "
                    + "screen", e);
            }
        });
        return null;
    }

    /**
     * Dismiss the Europe screen if one is open, without telling anyone (a
     * replacement, the game view's end); the player's close is
     * {@link #europeClosed}.
     */
    private void closeEuropePanel() {
        final JFrame f = this.europeFrame;
        this.europeFrame = null;
        this.europePanel = null;
        if (f != null) f.dispose();
    }

    /**
     * The player closed the Europe screen: its own "Abb" or Escape, the
     * window's close box or Alt+F4 ({@code WINDOW_CLOSING}; Alt+Enter's
     * re-framing disposes it too, but fires only {@code windowClosed}, and
     * a replacement in {@link #showEuropePanel} is not a close).  A box's
     * close for the turn flow (W13): the end it held comes
     * {@link ClassicTurnFlow#END_TURN_MS} after it, the turn start it held
     * goes on at once (the arrival chain's next step).  EDT only.
     */
    private void europeClosed() {
        if (this.europeFrame == null) return;
        closeEuropePanel();
        this.europeAskedAt = 0L;
        ClassicFrameRecorder.event("europe-close", "");
        if (this.voyageChain != null) runVoyageChain();
        if (this.turnFlow != null) this.turnFlow.boxClosed();
    }

    /**
     * Whether the Europe screen holds the automatic end (W13): it exists,
     * showing, behind the map or minimized, or it was asked for less than
     * {@link ClassicVoyages#EUROPE_TIMEOUT_MS} ago and has not come yet.
     *
     * @return True if so.
     */
    boolean europeHoldsTheEnd() {
        if (isOpen(this.europeFrame)) return true;
        return this.europeAskedAt != 0L && waitClock().now() - this.europeAskedAt
            < ClassicVoyages.nanos(ClassicVoyages.EUROPE_TIMEOUT_MS);
    }

    /** @return The game's turn number, -1 without one. */
    private int turnNumber() {
        final Game g = (getFreeColClient() == null) ? null : getGame();
        return (g == null || g.getTurn() == null) ? -1 : g.getTurn().getNumber();
    }

    // Voyages: the bands, the arrival chain, the guard (master plan W13,
    // spec R4, ClassicVoyages)

    /**
     * {@inheritDoc}
     *
     * <p>Our ship left the map for Europe: the band "Holl. Handelsschiff
     * Ziel: Amsterdam" for 137 frames (landfall #25735, clip008 #44180), in
     * the paint the ship goes or the next; play goes on under it, and the
     * automatic end waits for its end ({@link ClassicTurnFlow#bandEnded}).
     * Not for a ship at sea that turned around (no tile; R4 verifier item
     * 4).  Any thread.
     */
    @Override
    public void unitSailedForEurope(Unit unit, Tile from) {
        if (!showsDeparture(unit, from, myPlayer())) return;
        final Runnable band = () -> {
            if (this.menuStrip == null) return;
            final String text = ClassicBands.departure(
                ClassicText.load(ClassicPackFiles.runtime()), unit);
            // The ship gone first, the band with it or a frame later.
            if (this.mapViewer != null) this.mapViewer.paintDeparture();
            showBand(text, ClassicVoyages.BAND_MS);
            ClassicFrameRecorder.event("band", "depart unit=" + unit.getId()
                + " at=" + from.getX() + "," + from.getY() + " text=" + text);
        };
        if (SwingUtilities.isEventDispatchThread()) {
            band.run();
        } else {
            SwingUtilities.invokeLater(band);
        }
    }

    /**
     * The rule of {@link #unitSailedForEurope}: our ship that left the map.
     *
     * @param unit The unit that sailed, or null.
     * @param from The tile it left, or null (it was at sea).
     * @param me Our player, or null.
     * @return True if its departure band comes.
     */
    static boolean showsDeparture(Unit unit, Tile from, Player me) {
        return unit != null && from != null && unit.isNaval() && me != null
            && unit.getOwner() == me;
    }

    /**
     * Show a band on the top strip instead of the menu (it takes no input
     * meanwhile), replacing one that is up.  EDT only.
     *
     * @param text The band.
     * @param ms How long, or 0 until {@link #clearBand}.
     */
    void showBand(String text, double ms) {
        if (this.menuStrip == null) return;
        if (this.bandTimer != null) this.bandTimer.cancel();
        this.menuStrip.setBand(text);
        this.menuStrip.paintImmediately(0, 0, this.menuStrip.getWidth(),
                                        this.menuStrip.getHeight());
        if (ms > 0 && this.bandTimer != null) {
            this.bandTimer.schedule(waitClock().now() + ClassicVoyages.nanos(ms),
                                    this::clearBand);
        }
    }

    /**
     * The band goes, the menu titles come back in the same paint (as the
     * band came, {@link #showBand}: a queued repaint came late or, in a
     * minimized run, not at all); the turn flow hears it
     * ({@link ClassicTurnFlow#bandEnded}).  EDT only.
     */
    void clearBand() {
        if (this.bandTimer != null) this.bandTimer.cancel();
        if (this.menuStrip == null || this.menuStrip.band() == null) return;
        this.menuStrip.setBand(null);
        this.menuStrip.paintImmediately(0, 0, this.menuStrip.getWidth(),
                                        this.menuStrip.getHeight());
        ClassicFrameRecorder.event("band", "off");
        if (this.turnFlow != null) this.turnFlow.bandEnded();
    }

    /** Note our ships at sea while our turn is shown (the poll). */
    private void noteVoyages() {
        final ClassicTurnFlow f = this.turnFlow;
        final FreeColClient fcc = getFreeColClient();
        if (f == null || fcc == null || f.isWaiting()
            || !fcc.currentPlayerIsMyPlayer()) return;
        this.voyages.note(getMyPlayer());
    }

    /**
     * The turn start's hold for our ships' arrivals (W13): once per turn,
     * the ships that arrived in Europe or back in the New World start the
     * original's chain ({@link ClassicVoyages.Chain}); the wipe waits until
     * it is over.
     *
     * @return True while the chain runs.
     */
    boolean holdForArrivals() {
        if (this.voyageChain != null) return true;
        final Player me = myPlayer();
        final int turn = turnNumber();
        if (me == null || turn < 0) return false;
        final ClassicVoyages.Arrivals a = this.voyages.take(me, turn);
        if (a.isEmpty()) return false;
        startVoyageChain(a, "turn start");
        return true;
    }

    /**
     * A loaded save of an arrival turn (W13): its ships in port that
     * arrived at this turn's start ({@link ClassicVoyages#loaded}) get the
     * band and Europe as at the turn start, outside the hold (a load shows
     * no turn start).  Once per game view, after its build.
     */
    private void loadedArrivals() {
        final Player me = myPlayer();
        final int turn = turnNumber();
        if (me == null || turn < 0 || this.voyageChain != null
            || this.turnFlow == null) return;
        final ClassicVoyages.Arrivals a = ClassicVoyages.loaded(me);
        this.voyages.take(me, turn);   // nothing else this turn
        if (!a.isEmpty()) {
            startVoyageChain(a, "loaded");
        } else if (guardWants()) {
            // A load shows no automatic end while nothing can move (G
            // acceptance A5: it waits for a key), so the guard comes here.
            ClassicFrameRecorder.event("voyage", "guard: Europe at the load");
            showEuropePanel();
        }
    }

    /**
     * Start the arrival chain; its first step is posted, so the band comes
     * with the next paint after the box that closed last (A5).
     *
     * @param a The arrivals.
     * @param why What starts it (for the recorder).
     */
    private void startVoyageChain(ClassicVoyages.Arrivals a, String why) {
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        this.voyageChain = new ClassicVoyages.Chain(new VoyageHost(), a,
            ship -> ClassicBands.arrivalEurope(t, ship),
            ship -> ClassicBands.arrivalNewWorld(t, ship));
        ClassicFrameRecorder.event("voyage", "arrivals " + a + " (" + why + ")");
        SwingUtilities.invokeLater(this::runVoyageChain);
    }

    /** Run the arrival chain's due step and schedule the next.  EDT only. */
    private void runVoyageChain() {
        final ClassicVoyages.Chain c = this.voyageChain;
        if (c == null) return;
        final ClassicSlide.Clock clock = waitClock();
        final long due = c.step(clock.now());
        if (this.voyageChain != c || this.voyageTimer == null) return;
        if (due < 0L) {
            this.voyageChain = null;
            this.voyageTimer.cancel();
        } else {
            this.voyageTimer.schedule(due, this::runVoyageChain);
        }
    }

    /** What the arrival chain drives. */
    private final class VoyageHost implements ClassicVoyages.Chain.Host {

        @Override
        public void band(String text) {
            showBand(text, 0.0);
        }

        @Override
        public void timedBand(String text) {
            showBand(text, ClassicVoyages.BAND_MS);
        }

        @Override
        public void clearBand() {
            ClassicGUI.this.clearBand();
        }

        @Override
        public boolean boxUp() {
            return sceneShowing || modalDialogShowing() || boxBusy();
        }

        @Override
        public boolean openEurope() {
            final Player me = myPlayer();
            if (me == null || me.getEurope() == null) return false;
            if (isOpen(europeFrame)) {
                // Open already (windowed, behind the map; U5): brought up
                // to date and to the front, not opened again.
                europeShownTurn = turnNumber();
                if (europePanel != null) europePanel.refresh();
                europeFrame.toFront();
                return true;
            }
            showEuropePanel();
            return true;
        }

        @Override
        public boolean europeOpen() {
            return isOpen(europeFrame);
        }

        @Override
        public void jumpTo(Unit ship) {
            if (mapViewer != null && ship != null && ship.hasTile()) {
                mapViewer.jumpTo(ship.getTile(), "arrival");
            }
        }

        @Override
        public void done() {
            voyageChain = null;
            if (turnFlow != null) turnFlow.boxClosed();
        }

        @Override
        public void event(String what) {
            ClassicFrameRecorder.event("voyage", what);
        }
    }

    /**
     * The guard (W13, spec R4 section 5.7), asked at the automatic end when
     * nothing can move: with nothing of ours in the New World or at sea
     * ({@link ClassicVoyages#nothingOut}), a Europe and no Europe screen in
     * this turn yet, Europe opens instead of the end, once per turn; the
     * end comes after its close.  So the turns never run on unseen while
     * everything waits in Europe (Roger, 1504-1545).  The automatic end
     * only (Spielzugende off); not while a band is up.
     *
     * @return True if Europe opens.
     */
    boolean openEuropeInstead() {
        if (!guardWants()) return false;
        ClassicFrameRecorder.event("voyage", "guard: Europe instead of the end");
        showEuropePanel();
        return true;
    }

    /**
     * The guard's conditions ({@link #openEuropeInstead}): a player with a
     * Europe, no arrival chain or band, no Europe screen in this turn yet,
     * nothing out ({@link ClassicVoyages#nothingOut}).  Also asked right
     * after a load ({@link #loadedArrivals}).
     *
     * @return True if Europe is to open.
     */
    boolean guardWants() {
        final Player me = myPlayer();
        return me != null && me.getEurope() != null && this.voyageChain == null
            && (this.menuStrip == null || this.menuStrip.band() == null)
            && this.europeShownTurn != turnNumber()
            && ClassicVoyages.nothingOut(me);
    }

    /**
     * {@code @TUTORIAL17} once per game, {@link ClassicVoyages#TUTORIAL_MS}
     * after the game's first Europe screen is drawn, whatever opened it
     * (U7), with Tutortips on; over the Europe window it is the stopgap's
     * box.  Kept in the save ({@code Player.classicTips}).  EDT only.
     */
    private void europeTip() {
        if (!isOpen(this.europeFrame)) return;
        final Player me = myPlayer();
        final FreeColClient fcc = getFreeColClient();
        // Tutortips is FreeCol's option (ClassicPrefs.CLIENT_OPTIONS).
        if (me == null || fcc == null || !fcc.tutorialMode()
            || tipShown(me, ClassicBands.TUTORIAL_EUROPE_NUMBER)) return;
        if (modalDialogShowing() || boxBusy()) {
            // A box is up: the tip after it (I).
            if (this.tipTimer != null) {
                this.tipTimer.schedule(waitClock().now()
                    + ClassicVoyages.nanos(ClassicVoyages.POLL_MS), this::europeTip);
            }
            return;
        }
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        final ClassicAdvisorBox.Request r = europeTipRequest(t, me);
        if (r == null) return;   // no pack: FreeCol has no such tip
        markTip(me, ClassicBands.TUTORIAL_EUROPE_NUMBER);
        ClassicFrameRecorder.event("tip", ClassicBands.TUTORIAL_EUROPE);
        this.prompter.ask(r);
    }

    /**
     * {@code @TUTORIAL17}'s box: @width 300, @y 10, no portrait, box
     * (7,10,306,90) (landfall #27956), a key or a click closes it.
     *
     * @param t The original texts, or null.
     * @param me Our player.
     * @return The box, or null without the text.
     */
    static ClassicAdvisorBox.Request europeTipRequest(ClassicText t, Player me) {
        final ClassicAdvisorBox.Builder b = ClassicAdvisorBox.fromGameText(
            ClassicBands.TUTORIAL_EUROPE,
            (t == null) ? null : t.message(ClassicBands.TUTORIAL_EUROPE),
            ClassicBands.tutorialValues(t, me));
        return (b == null) ? null : b.portrait(ClassicAdvisorBox.Portrait.NONE)
            .stopgap(Messages.message("classic.dialog.messages"), null).build();
    }

    /**
     * @param me Our player.
     * @param k A tip's number.
     * @return Whether {@code @TUTORIALk} was shown in this game.
     */
    static boolean tipShown(Player me, int k) {
        return (me.getClassicTips() & (1 << k)) != 0;
    }

    /**
     * Mark a tip as shown, on our player and, in a single player game, on
     * the server's copy, whose state every save writes (as
     * {@link #markWoodcut}).
     *
     * @param me Our player.
     * @param k The tip's number.
     */
    void markTip(Player me, int k) {
        me.setClassicTips(me.getClassicTips() | (1 << k));
        final FreeColClient fcc = getFreeColClient();
        final FreeColServer server = (fcc == null) ? null : fcc.getFreeColServer();
        final Game sg = (server == null) ? null : server.getGame();
        final Player sp = (sg == null) ? null
            : sg.getFreeColGameObject(me.getId(), Player.class);
        if (sp != null) sp.setClassicTips(sp.getClassicTips() | (1 << k));
    }

    /**
     * {@inheritDoc}
     *
     * Phase 2: show the classic <b>Colony Advisor report</b> — the original's
     * "KOLONIEBERATER-BERICHT" — as {@link ClassicReportColonyPanel} in a window
     * of its own.  Reached by the reused {@code Colony Advisor} report menu item
     * (accelerator {@code F3}).
     */
    @Override
    public FreeColPanel showReportColonyPanel() {
        return showReport("reportColonyAction.name",
            onClose -> new ClassicReportColonyPanel(getFreeColClient(),
                this.imageLibrary, onClose));
    }

    /**
     * {@inheritDoc}
     *
     * Phase 2: the classic <b>Military Advisor report</b> — the standing-army
     * roster (accelerator {@code F7}).  See {@link ClassicReportMilitaryPanel}.
     */
    @Override
    public FreeColPanel showReportMilitaryPanel() {
        return showReport("reportMilitaryAction.name",
            onClose -> new ClassicReportMilitaryPanel(getFreeColClient(),
                this.imageLibrary, onClose));
    }

    /**
     * {@inheritDoc}
     *
     * Phase 2: the classic <b>Trade Advisor report</b> — the goods ledger
     * (accelerator {@code F9}).  See {@link ClassicReportTradePanel}.
     */
    @Override
    public FreeColPanel showReportTradePanel() {
        return showReport("reportTradeAction.name",
            onClose -> new ClassicReportTradePanel(getFreeColClient(),
                this.imageLibrary, onClose));
    }

    /**
     * {@inheritDoc}
     *
     * Phase 2: the classic <b>Religious Advisor report</b> — the immigration /
     * crosses standing (accelerator {@code F1}).  See
     * {@link ClassicReportReligiousPanel}.
     */
    @Override
    public FreeColPanel showReportReligiousPanel() {
        return showReport("reportReligionAction.name",
            onClose -> new ClassicReportReligiousPanel(getFreeColClient(),
                this.imageLibrary, onClose));
    }

    /**
     * {@inheritDoc}
     *
     * Phase 2: the classic <b>Naval Advisor report</b> — the fleet roster
     * (accelerator {@code F8}).  See {@link ClassicReportNavalPanel}.
     */
    @Override
    public FreeColPanel showReportNavalPanel() {
        return showReport("reportNavalAction.name",
            onClose -> new ClassicReportNavalPanel(getFreeColClient(),
                this.imageLibrary, onClose));
    }

    /**
     * {@inheritDoc}
     *
     * Phase 2: the classic <b>Production Report</b> — per-colony production
     * breakdown (accelerator {@code shift F4}).  See
     * {@link ClassicReportProductionPanel}.
     */
    @Override
    public FreeColPanel showReportProductionPanel() {
        return showReport("reportProductionAction.name",
            onClose -> new ClassicReportProductionPanel(getFreeColClient(),
                this.imageLibrary, onClose));
    }

    /**
     * {@inheritDoc}
     *
     * Phase 2: the classic <b>Continental Congress</b> report — the
     * founding-father standing (accelerator {@code F6}).  See
     * {@link ClassicReportCongressPanel}.
     */
    @Override
    public FreeColPanel showReportContinentalCongressPanel() {
        return showReport("reportCongressAction.name",
            onClose -> new ClassicReportCongressPanel(getFreeColClient(),
                this.imageLibrary, onClose));
    }

    /**
     * {@inheritDoc}
     *
     * Phase 2: the classic <b>Exploration Report</b> — the discovered regions
     * (accelerator {@code shift F2}).  See {@link ClassicReportExplorationPanel}.
     */
    @Override
    public FreeColPanel showReportExplorationPanel() {
        return showReport("reportExplorationAction.name",
            onClose -> new ClassicReportExplorationPanel(getFreeColClient(),
                this.imageLibrary, onClose));
    }

    /**
     * {@inheritDoc}
     *
     * Phase 2: the classic <b>Cargo Report</b> — each carrier's load
     * (accelerator {@code shift F1}).  See {@link ClassicReportCargoPanel}.
     */
    @Override
    public FreeColPanel showReportCargoPanel() {
        return showReport("reportCargoAction.name",
            onClose -> new ClassicReportCargoPanel(getFreeColClient(),
                this.imageLibrary, onClose));
    }

    /**
     * {@inheritDoc}
     *
     * Phase 2: the classic <b>Indian Advisor report</b> — the contacted native
     * nations (accelerator {@code F5}).  See {@link ClassicReportIndianPanel}.
     */
    @Override
    public FreeColPanel showReportIndianPanel() {
        return showReport("reportIndianAction.name",
            onClose -> new ClassicReportIndianPanel(getFreeColClient(),
                this.imageLibrary, onClose));
    }

    /**
     * {@inheritDoc}
     *
     * Phase 2: the classic <b>Labour Advisor report</b> — a three-column census
     * of every unit type the player owns (accelerator {@code F4} in the
     * original; see the README's key-scheme note for why the classic UI does
     * not yet claim that key).  See {@link ClassicReportLabourPanel}.
     */
    @Override
    public FreeColPanel showReportLabourPanel() {
        return showReport("reportLabourAction.name",
            onClose -> new ClassicReportLabourPanel(getFreeColClient(),
                this.imageLibrary, onClose));
    }

    /**
     * {@inheritDoc}
     *
     * Phase 2: the classic <b>Foreign Affairs report</b> — the last report the
     * original actually has, over the map-and-wax-seal illustration
     * (accelerator {@code F4} in the original; see the README's key-scheme
     * note for why the classic UI does not yet claim that key).  See
     * {@link ClassicReportForeignAffairPanel}.
     *
     * <p><b>The {@code nationSummary} trap.</b> Every rival's
     * {@link NationSummary} is a blocking server round trip, so it must not be
     * fetched from {@code paintComponent}.  This override fetches them all on
     * a background thread <em>before</em> the panel is built, then hands the
     * finished stash to the panel on the EDT — the panel itself only ever
     * paints from that stash, never calls {@code nationSummary} directly.
     */
    @Override
    public FreeColPanel showReportForeignAffairPanel() {
        final FreeColClient fcc = getFreeColClient();
        new Thread(FreeCol.CLIENT_THREAD + "ForeignAffairs") {
            @Override
            public void run() {
                final Player me = fcc.getMyPlayer();
                final Game game = fcc.getGame();
                if (me == null || game == null) return;
                final List<Player> others = game.getPlayers(p ->
                    p.isEuropean() && !p.isUnknownEnemy() && !p.isREF()
                        && p != me).collect(Collectors.toList());
                final Map<Player, NationSummary> summaries = new HashMap<>();
                for (Player other : others) {
                    if (other.isDead()) continue;
                    final NationSummary ns
                        = fcc.getInGameController().nationSummary(other);
                    if (ns != null) summaries.put(other, ns);
                }
                SwingUtilities.invokeLater(() -> showReport(
                    "reportForeignAction.name",
                    onClose -> new ClassicReportForeignAffairPanel(fcc,
                        ClassicGUI.this.imageLibrary, onClose, me, others,
                        summaries)));
            }
        }.start();
        return null;
    }

    /**
     * {@inheritDoc}
     *
     * Phase 2: the classic <b>score breakdown</b> — "Kolonisationspunkte" /
     * High Scores, {@code F10} in neither key scheme (uncontested; see the
     * README's key-scheme note).  See {@link ClassicReportHighScoresPanel}.
     *
     * <p>Unlike every other {@code showReport*Panel} override, the data is
     * already in hand (the caller, {@code InGameController.highScoresHandler},
     * has already made the server round trip), so this builds the panel
     * synchronously rather than needing the Foreign Affairs report's
     * background-thread {@code nationSummary} dance.
     */
    @Override
    public FreeColPanel showHighScoresPanel(String messageId,
                                            List<HighScore> scores) {
        return showReport("reportHighScoresAction.name",
            onClose -> new ClassicReportHighScoresPanel(getFreeColClient(),
                this.imageLibrary, onClose, messageId, scores));
    }

    /**
     * Show a classic advisor report in a window of its own, one report at a
     * time.  Every {@code showReport*Panel} override routes through here: it
     * disposes any open report, builds the panel via {@code factory} (passing the
     * close callback), and frames it.  Guarded so a failure degrades to a log
     * line.  The report panels themselves share {@link ClassicReportPanel}.
     *
     * @param titleKey The message key of the window title.
     * @param factory Builds the report panel given its close callback.
     * @return {@code null} (the classic UI hosts its own windows).
     */
    private FreeColPanel showReport(String titleKey,
            java.util.function.Function<Runnable, JPanel> factory) {
        SwingUtilities.invokeLater(() -> {
            try {
                closeReportPanel();
                final JFrame f = new JFrame(Messages.message(titleKey));
                this.reportFrame = f;
                f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                f.setBackground(Color.BLACK);
                f.setContentPane(factory.apply(this::closeReportPanel));
                prepareChildWindow(f, this.frame, true);
                f.setVisible(true);
                f.getContentPane().requestFocusInWindow();
            } catch (Exception e) {
                logger.log(Level.WARNING, "ClassicGUI: could not show report "
                    + titleKey, e);
            }
        });
        return null;
    }

    /** Dismiss the report screen if one is open. */
    private void closeReportPanel() {
        final JFrame f = this.reportFrame;
        this.reportFrame = null;
        if (f != null) f.dispose();
    }


    // Model messages

    /**
     * {@inheritDoc}
     *
     * Phase 3: the in-game notices — a colony starving, a colonist born, a
     * founding father joining, a unit demoted — as classic wood-framed popups.
     *
     * <p>Until this override existed the classic UI <em>silently discarded every
     * notice in the game</em>: the base {@code GUI} no-ops this seam, so the
     * whole channel went to the floor.  (It could not have worked anyway — the
     * controller posts the display task through {@code invokeNowOrWait}, itself a
     * base-class no-op until {@link #invokeNowOrWait} above overrode it.)
     */
    @Override
    public FreeColPanel showModelMessages(List<ModelMessage> modelMessages) {
        showMessagePopup(modelMessages, "classic.dialog.messages");
        return null;
    }

    /**
     * {@inheritDoc}
     *
     * The end-of-turn batch of the same notices, over the same popup.  FreeCol
     * gathers these into one scrolling <em>turn report</em> panel; the original
     * has no such screen, showing each notice in turn, so page through them
     * ({@link ClassicDialog#showMessages}) rather than rebuild the report.
     */
    @Override
    public FreeColPanel showReportTurnPanel(List<ModelMessage> messages) {
        showMessagePopup(messages, TURN_REPORT);
        return null;
    }

    /** The title key of the turn start's notices ({@link #showReportTurnPanel}). */
    static final String TURN_REPORT = "classic.dialog.turnMessages";

    /**
     * Show {@code messages} as a paged classic popup titled {@code titleKey}.
     * Each notice keeps the illustration FreeCol associates with it (the colony,
     * unit or goods the message is about).
     *
     * <p>The shared funnel of {@link #showModelMessages} and
     * {@link #showReportTurnPanel}, so the first scene is handled here:
     * <ul>
     *   <li>FreeCol's own start message ({@link #START_GAME_MESSAGE},
     *       "Nach Monaten auf See ...", Player.addStartGameMessage) is always
     *       dropped.  For a fresh game the admiral's scene replaces it; for
     *       a loaded save of turn 1, which FreeCol re-greets
     *       (PreGameController.startGameInternal), the original shows
     *       nothing.  The scene is triggered by {@link #firstScenePending},
     *       never by this message, so it shows even when the tutorial
     *       messages are switched off (model.option.guiShowTutorial).</li>
     *   <li>Anything else that arrives while the scene is pending or up is
     *       held and shown after it ({@link #flushHeldMessages}).  The
     *       callers are on the EDT already (invokeNowOrWait), so holding
     *       blocks nothing.</li>
     *   <li>A colony report whose row of "Koloniebericht-Optionen" is off
     *       is dropped ({@link ClassicOptionBoxes#reportsShown},
     *       {@link ClassicPrefs#REPORTS}), on top of FreeCol's own message
     *       options.</li>
     *   <li>Each notice is a box in the original's words where GAME.TXT
     *       has it ({@link ClassicNotices}, master plan N1).</li>
     *   <li>The turn report asks a pending father offer after its price
     *       messages and before its other notices (build spec D8a,
     *       {@link #askFathers}).</li>
     *   <li>A notice of an event with a woodcut (the Fountain of Youth, the
     *       first laden ship in Europe, a burning, destroyed or raided
     *       colony; N17, {@link #messageWoodcut}) brings it before its box,
     *       once per game, also when the notice itself is dropped.</li>
     * </ul>
     */
    private void showMessagePopup(List<ModelMessage> all, String titleKey) {
        final List<ModelMessage> messages = noticesShown(all);
        // The woodcut a notice brings (N17) comes also when the notice
        // itself is not shown (I), before the shown ones.
        final Game game = getGame();
        // Our ship's arrival in Europe is the band and the Europe screen
        // at the turn start (W13), not FreeCol's box.
        for (Unit ship : takeArrivalMessages(messages, game, myPlayer())) {
            this.voyages.consumed(ship);
        }
        final List<Integer> unshown = new ArrayList<>();
        for (ModelMessage m : all) {
            if (messages.contains(m)) continue;
            final int k = messageWoodcut(game, m);
            if (k >= 0) unshown.add(k);
        }
        if (messages.isEmpty() && unshown.isEmpty()) return;
        if (this.firstScenePending || this.sceneShowing) {
            this.heldMessages.add(new HeldMessages(all, titleKey));
            logger.info("ClassicGUI: " + messages.size()
                + " notice(s) held until the first scene is dismissed.");
            return;
        }
        if (game == null) return;
        // One box per notice, each after the one before (build spec W7):
        // the original has no paged report.  The original's words where
        // GAME.TXT has the notice (N1, ClassicNotices).
        final ClassicText text = ClassicText.load(ClassicPackFiles.runtime());
        final Player me = myPlayer();
        final String title = Messages.message(titleKey);
        // The turn start's father choice comes after its price messages,
        // before the other notices (clip008 01-fathers section 6.3); else
        // the notices keep their order.
        final PendingFathers fathers = (TURN_REPORT.equals(titleKey))
            ? takePendingFathers() : null;
        final List<ClassicAdvisorBox.Request> prices = new ArrayList<>();
        final List<ClassicAdvisorBox.Request> boxes = new ArrayList<>();
        final List<Integer> woodcutsOf = new ArrayList<>();
        for (ModelMessage m : messages) {
            final FreeColObject display = game.getMessageDisplay(m);
            final ClassicAdvisorBox.Request r = ClassicNotices.request(text,
                "message " + m.getId(), m, Messages.message(m), display, me,
                title, iconOf(display));
            if (fathers != null
                && m.getMessageType() == ModelMessage.MessageType.MARKET_PRICES) {
                prices.add(r);
            } else {
                boxes.add(r);
                woodcutsOf.add(messageWoodcut(game, m));
            }
        }
        onEventThread(() -> {
                for (int k : unshown) noticeWoodcut(k);
                for (ClassicAdvisorBox.Request r : prices) this.prompter.ask(r);
                if (fathers != null) askFathers(fathers);
                for (int i = 0; i < boxes.size(); i++) {
                    noticeWoodcut(woodcutsOf.get(i));
                    this.prompter.ask(boxes.get(i));
                }
                return null;
            }, null);
    }

    /**
     * {@inheritDoc}
     *
     * <p>FreeCol's information messages (master plan N1): the base GUI
     * drops them, so every result of speaking to a chief, every "not
     * enough gold", every village's answer came without a word.  Each is
     * a notice box now ({@link ClassicNotices}: GAME.TXT's words where the
     * original has the notice), except a key out of turn and FreeCol's
     * illegal-move messages, which only a goto's failed step still posts
     * (a move key gets the original's refusal first,
     * {@link #illegalMoveKey}).  The colony refusals come as the original's
     * boxes from their cause ({@link #colonyNotice}).  On the event thread
     * the box is asked at once (the controller posts these with
     * {@code invokeLater}); from another thread it is posted, so no
     * server message waits for the player.
     */
    @Override
    public FreeColPanel showInformationPanel(FreeColObject displayObject,
                                             StringTemplate template) {
        if (template == null) return null;
        final String id = template.getId();
        // The controller's colony refusals (menu row and B alike): the
        // original's box from the unit and its tile (R3).
        final ClassicIllegalMoves.Verdict v = colonyNotice(id, displayObject);
        if (v != null) {
            if (ClassicFrameRecorder.on()) {
                ClassicFrameRecorder.event("refused", id + " section="
                    + ((v.section == null) ? "-" : v.section));
            }
            if (!v.isSilent()) {
                final ClassicAdvisorBox.Request r = refusalRequest(v, 0L);
                if (SwingUtilities.isEventDispatchThread()) {
                    this.prompter.ask(r);
                } else {
                    SwingUtilities.invokeLater(() -> this.prompter.ask(r));
                }
            }
            return null;
        }
        if (ClassicNotices.silent(id)) {
            ClassicFrameRecorder.event("notice-silent", id);
            return null;
        }
        final ClassicAdvisorBox.Request r = ClassicNotices.request(
            ClassicText.load(ClassicPackFiles.runtime()), "notice " + id, template,
            Messages.message(template), displayObject, myPlayer(),
            Messages.message("classic.dialog.messages"), iconOf(displayObject));
        if (SwingUtilities.isEventDispatchThread()) {
            this.prompter.ask(r);
        } else {
            SwingUtilities.invokeLater(() -> this.prompter.ask(r));
        }
        return null;
    }

    /**
     * The original's refusal of a colony the controller posted (R3):
     * {@code buildColony.badUnit} from the unit's own cause
     * ({@link ClassicIllegalMoves#colonyRefusal}: a colonist with no moves
     * left, joining a colony by the menu row, hears nothing, never
     * @ONLYCOL), and FreeCol's site refusals {@code model.noClaimReason.*}
     * (posted without a display object) from the active unit's tile
     * ({@link ClassicIllegalMoves#siteRefusal}).
     *
     * @param id The message id.
     * @param display The display object, or null.
     * @return The refusal, or null for the ordinary notice.
     */
    ClassicIllegalMoves.Verdict colonyNotice(String id, FreeColObject display) {
        if ("buildColony.badUnit".equals(id)) {
            final Unit u = (display instanceof Unit) ? (Unit) display : getActiveUnit();
            final ClassicIllegalMoves.Verdict v = ClassicIllegalMoves.colonyRefusal(u);
            return (v == null) ? ClassicIllegalMoves.SILENT : v;
        }
        if (display == null && id != null
            && id.startsWith(ClassicIllegalMoves.NO_CLAIM)) {
            return ClassicIllegalMoves.siteRefusal(getActiveUnit(), id);
        }
        return null;
    }

    /**
     * The stopgap's illustration of a notice: FreeCol's image of its
     * object, or null.  A test without the image resources replaces it
     * (their fallback image would be fatal there).
     *
     * @param display The object, or null.
     * @return The image, or null.
     */
    java.awt.Image iconOf(FreeColObject display) {
        if (display == null) return null;
        try {
            final ImageIcon icon = this.imageLibrary.getObjectImageIcon(display);
            return (icon == null) ? null : icon.getImage();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** @return Our player, or null without a client (the tests). */
    Player myPlayer() {
        return (getFreeColClient() == null) ? null : getMyPlayer();
    }

    // The founding father choice (build spec D8a, ClassicFathers)

    /** A father offer, until its box is asked. */
    static final class PendingFathers {

        final List<FoundingFather> fathers;
        final DialogHandler<FoundingFather> handler;

        PendingFathers(List<FoundingFather> fathers,
                       DialogHandler<FoundingFather> handler) {
            this.fathers = new ArrayList<>(fathers);
            this.handler = handler;
        }
    }

    /** The father offer of this turn start, until its box is asked (EDT only). */
    private PendingFathers pendingFathers = null;

    /** Whether the turn start's hold has posted the pending offer's box. */
    private boolean fathersPosted = false;

    /**
     * {@inheritDoc}
     *
     * <p>The original's father box ({@link ClassicFathers}).  It is not
     * asked at once: the controller posts the offer before the turn
     * start's notices, and the original shows it after their price
     * messages.  So the offer waits: the turn report asks it after its
     * price messages ({@link #showMessagePopup}); a turn start without
     * notices asks it just before the year flips (the turn flow's hold,
     * {@link #holdTurnStart}).  When it is due it is dropped after
     * independence and in the turn a father joined
     * ({@link ClassicFathers#withheld}); the server offers it again next
     * turn while none is chosen.
     */
    @Override
    public void showChooseFoundingFatherDialog(final List<FoundingFather> ffs,
        final DialogHandler<FoundingFather> handler) {
        if (ffs == null || ffs.isEmpty() || handler == null) return;
        this.pendingFathers = new PendingFathers(ffs, handler);
        this.fathersPosted = false;
        ClassicFrameRecorder.event("fathers-offer", ffs.stream()
            .map(FoundingFather::getId).collect(Collectors.joining(",")));
    }

    /**
     * The pending father offer, taken: it is asked by the caller now.
     *
     * @return The offer, or null.
     */
    PendingFathers takePendingFathers() {
        final PendingFathers p = this.pendingFathers;
        this.pendingFathers = null;
        return p;
    }

    /**
     * The turn flow's hold before the year flips (build spec D8a): a
     * father offer still pending is asked first.  The box is posted (the
     * flow's wipe asks this from inside its own work); the flow then waits
     * for it as for any box, and wipes after it.  Then our ships' arrivals
     * (W13, {@link #holdForArrivals}).
     *
     * @return True if the turn start must wait.
     */
    boolean holdTurnStart() {
        if (this.pendingFathers == null) return holdForArrivals();
        if (!this.fathersPosted) {
            this.fathersPosted = true;
            ClassicFrameRecorder.event("fathers-due", "before the turn start");
            SwingUtilities.invokeLater(() -> {
                    final PendingFathers p = takePendingFathers();
                    if (p != null) askFathers(p);
                });
        }
        return true;
    }

    /**
     * Ask the father box until a father is taken or Escape (EDT only): F1
     * shows the page of the father under the bar and asks the box again,
     * its bar on row 1; Escape closes it with no father (the server offers
     * it again next turn); a click beside it does nothing.  Not after
     * independence, not in the turn a father joined
     * ({@link ClassicFathers#withheld}): then nothing is asked, and the
     * handler is not called.
     *
     * @param p The offer.
     */
    void askFathers(PendingFathers p) {
        final Game game = getGame();
        final String why = ClassicFathers.withheld(myPlayer(),
            (game == null) ? null : game.getTurn());
        if (why != null) {
            ClassicFrameRecorder.event("fathers-withheld", why);
            logger.info("Classic father choice not shown: " + why + ".");
            return;
        }
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final ClassicText text = ClassicText.load(pack);
        final int[] help = { -1 };
        boolean again = false;
        for (int round = 0; round < 1000; round++) {
            // Asked again after its page, it comes a little later (clip008).
            final int chosen = this.prompter.ask(ClassicFathers.request(text,
                p.fathers, row -> help[0] = row,
                (again) ? ClassicFathers.REOPEN_CHAIN_MS : -1.0));
            if (chosen >= 0 && chosen < p.fathers.size()) {
                final FoundingFather ff = p.fathers.get(chosen);
                ClassicFrameRecorder.event("fathers-chosen", ff.getId());
                p.handler.handle(ff);
                return;
            }
            if (chosen == ClassicAdvisorBox.Bar.HELP && help[0] >= 0
                && help[0] < p.fathers.size()) {
                showFatherPage(pack, text, p.fathers.get(help[0]));
                help[0] = -1;
                again = true;
            } else if (chosen == ClassicAdvisorBox.Bar.DISMISSED) {
                // Escape, or the game view went (abort): the server
                // offers it again at the next turn start.
                ClassicFrameRecorder.event("fathers-postponed", "");
                return;
            }
        }
    }

    /**
     * The Colonopedia page of a father ({@link ClassicPedia#fatherPage}),
     * until a key or a click (EDT only).  Without the pack's page the box
     * simply comes back.
     *
     * @param pack The pack, or null.
     * @param text The texts, or null.
     * @param ff The father.
     */
    private void showFatherPage(ClassicPackFiles pack, ClassicText text,
                                FoundingFather ff) {
        final BufferedImage page = (pack == null) ? null : ClassicPedia.fatherPage(
            ClassicPedia.load(pack), text, pack.font(ClassicFont.TINY),
            pack.image(ClassicPedia.WOODPANL_KEY), ClassicFathers.index(ff));
        if (page == null) {
            logger.info("Classic father page missing for " + ff.getId()
                + " (re-run ant classic-assets).");
            return;
        }
        this.prompter.ask(ClassicAdvisorBox.Request.builder("pedia " + ff.getId())
            .freeColText(Messages.getName(ff)).picture(page)
            .chain(ClassicFathers.PAGE_CHAIN_MS)
            .stopgap(Messages.getName(ff), null).build());
    }

    // First game scene (ClassicFirstScene)

    /**
     * The notices of {@link #showMessagePopup} that come up: without
     * FreeCol's start message, and without the colony reports whose row of
     * "Koloniebericht-Optionen" is off (W14).
     *
     * @param messages The messages, or null.
     * @return A new list, possibly empty.
     */
    List<ModelMessage> noticesShown(List<ModelMessage> messages) {
        return ClassicOptionBoxes.reportsShown(withoutStartMessage(messages), prefs());
    }

    /**
     * Take FreeCol's "model.unit.arriveInEurope" messages of our ships out
     * of {@code messages} (W13): the arrival chain shows them.  Another
     * {@code UNIT_ARRIVED} message (the mercenaries) stays.
     *
     * @param messages The notices to show; changed.
     * @param game The game, or null.
     * @param me Our player, or null.
     * @return The ships of the messages taken out.
     */
    static List<Unit> takeArrivalMessages(List<ModelMessage> messages, Game game,
                                          Player me) {
        final List<Unit> ships = new ArrayList<>();
        if (game == null || me == null) return ships;
        for (Iterator<ModelMessage> it = messages.iterator(); it.hasNext();) {
            final ModelMessage m = it.next();
            if (!ClassicVoyages.ARRIVE_IN_EUROPE.equals(m.getId())) continue;
            final FreeColObject d = game.getMessageDisplay(m);
            if (d instanceof Unit && ((Unit) d).getOwner() == me) {
                ships.add((Unit) d);
                it.remove();
            }
        }
        return ships;
    }

    /** The id of FreeCol's start message (Player.java:2731). */
    static final String START_GAME_MESSAGE = "model.player.startGame";

    /**
     * {@code messages} without FreeCol's start message (and without nulls),
     * in their order.
     *
     * @param messages The messages, or null.
     * @return A new list, possibly empty.
     */
    static List<ModelMessage> withoutStartMessage(List<ModelMessage> messages) {
        final List<ModelMessage> rest = new ArrayList<>();
        if (messages == null) return rest;
        for (ModelMessage m : messages) {
            if (m != null && !START_GAME_MESSAGE.equals(m.getId())) rest.add(m);
        }
        return rest;
    }

    /** Notices held while the first scene is pending or up. */
    private static final class HeldMessages {

        final List<ModelMessage> messages;
        final String titleKey;

        HeldMessages(List<ModelMessage> messages, String titleKey) {
            this.messages = new ArrayList<>(messages);
            this.titleKey = titleKey;
        }
    }

    /**
     * {@inheritDoc}
     *
     * True while the first scene is up, so every {@code FreeColAction}
     * disables itself ({@code FreeColAction.shouldBeEnabled}) once
     * {@link #updateActions} runs: the menu bar under the scene goes grey
     * and no accelerator can act behind the admiral's back.  (The scene's
     * layer also consumes every key; this is the backstop.)
     */
    @Override
    public boolean isDialogShowing() {
        return this.sceneShowing || boxBusy() || super.isDialogShowing();
    }

    // Accessors for the acceptance harness (ClassicTestHarness).

    /** @return The main window, or null before it exists. */
    JFrame currentFrame() {
        return this.frame;
    }

    /** @return The in-game map, or null outside a game. */
    ClassicMapViewer currentMapViewer() {
        return this.mapViewer;
    }

    /** @return The title panel, or null before it exists. */
    ClassicMainMenuPanel currentTitlePanel() {
        return this.mainMenuPanel;
    }

    /** @return The in-game HUD, or null outside a game. */
    ClassicHudPane currentHudPane() {
        return this.hudPane;
    }

    /**
     * Show the original's first game scene over the fresh game's view, if
     * everything it needs is there: turn 1, a ship on the map (the active
     * unit, else the player's first ship), one of the four original nations,
     * the pack's texts, FONTTINY and the admiral's portrait.  EDT only;
     * called once by {@link #reconnectGUI} after the HUD is built.
     *
     * @return Whether the scene is shown; false leaves the game as it is.
     */
    private boolean showFirstScene() {
        try {
            final Game game = getGame();
            final Player player = getMyPlayer();
            if (game == null || player == null || this.frame == null
                || this.mapViewer == null || game.getTurn() == null
                || game.getTurn().getNumber() != 1) return false;
            Unit ship = this.mapViewer.getActiveUnit();
            if (ship == null || !ship.isNaval() || !ship.hasTile()
                || ship.getOwner() != player) {
                ship = player.getUnits()
                    .filter(u -> u.isNaval() && u.hasTile())
                    .findFirst().orElse(null);
            }
            if (ship == null || ship.getTile() == null) {
                logger.info("Classic first scene skipped: no ship on the map.");
                return false;
            }
            final int nation = java.util.Arrays.asList(
                ClassicNewWorldScreens.NATION_IDS).indexOf(player.getNationId());
            final int shipRow = ClassicFirstScene.shipRow(ship.getType().getId());
            final ClassicPackFiles pack = ClassicPackFiles.runtime();
            final ClassicText text = ClassicText.load(pack);
            if (nation < 0 || shipRow < 0 || text == null) {
                logger.info("Classic first scene skipped: nation " + nation
                    + ", ship row " + shipRow + ", texts "
                    + (text != null) + ".");
                return false;
            }
            final ClassicFont tiny = pack.font(ClassicFont.TINY);
            final BufferedImage wood = pack.image(ClassicMenuBar.WOOD_KEY);
            final BufferedImage mss0 = pack.image(
                ClassicPackFiles.ssKey(ClassicFirstScene.PORTRAIT));
            final ClassicFirstScene.Model model
                = ClassicFirstScene.build(text, tiny, mss0, nation, shipRow);
            if (model == null) {
                logger.info("Classic first scene skipped: the pack lacks a piece"
                    + " (FONTTINY, MSS0 or a text; re-run ant classic-assets).");
                return false;
            }
            final net.sf.freecol.common.model.Map map = game.getMap();
            final Tile t = ship.getTile();
            final int[] view = ClassicHud.viewFor(map.getWidth(), map.getHeight(),
                                                  t.getX(), t.getY());
            final ClassicHud.PanelModel panel = new ClassicHud.PanelModel(
                ClassicHud.minimapOf(map, view[0], view[1]),
                ClassicHud.seasonLine(text, game.getTurn().getSeason(),
                                      game.getTurn().getYear()),
                ClassicHud.goldLine(text, Math.max(0, player.getGold()),
                                    player.getTax()),
                true);
            final BufferedImage picture
                = ClassicFirstScene.render(model, panel, wood, tiny);
            if (this.hudOverlay == null) {
                this.hudOverlay = new ClassicHudOverlay(this::isAutoRepeat,
                                                        arrowSprite(pack));
            }
            if (this.frame.getGlassPane() != this.hudOverlay) {
                this.frame.setGlassPane(this.hudOverlay);
            }
            closeMenus();
            this.sceneShowing = true;
            ClassicFrameRecorder.event("dialog-open", "first scene");
            this.mapViewer.holdBlink("scene");
            this.hudOverlay.showScene(picture, this.mapViewer,
                                      this::dismissFirstScene);
            updateActions();
            logger.info("Classic first scene shown (" + model.band.length()
                + "-char band, " + model.lines.size() + " text lines).");
            return true;
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Classic first scene failed", e);
            this.sceneShowing = false;
            if (this.hudOverlay != null) this.hudOverlay.hideScene();
            return false;
        }
    }

    /**
     * The player dismissed the first scene: back to the game, actions
     * re-enabled, then the notices that waited.  EDT only.
     */
    private void dismissFirstScene() {
        if (!this.sceneShowing) return;
        this.sceneShowing = false;
        ClassicFrameRecorder.event("dialog-close", "first scene");
        if (this.hudOverlay != null) this.hudOverlay.hideScene();
        updateActions();
        if (this.mapViewer != null) {
            this.mapViewer.requestFocusInWindow();
            this.mapViewer.repaint();
            // The blink restarts ON at the scene's close (build spec W3).
            this.mapViewer.resumeBlink("scene");
        }
        // The HUD's own arrow takes over (it was held back under the scene).
        if (this.hudPane != null) this.hudPane.pointer().refresh();
        repaintInfo();
        logger.info("Classic first scene dismissed.");
        flushHeldMessages();
    }

    /** Show the held notices, in arrival order.  EDT only. */
    private void flushHeldMessages() {
        if (this.heldMessages.isEmpty()) return;
        final List<HeldMessages> held = new ArrayList<>(this.heldMessages);
        this.heldMessages.clear();
        for (HeldMessages h : held) showMessagePopup(h.messages, h.titleKey);
    }

    /** Whether a key press is an auto-repeat (see {@code FrameKeys}). */
    private boolean isAutoRepeat(KeyEvent e) {
        return this.frameKeys != null && this.frameKeys.isAutoRepeat(e);
    }

    /**
     * {@inheritDoc}
     *
     * Every {@code showErrorPanel} overload is {@code final} and funnels into
     * this one non-final seam, which the base {@code GUI} no-ops — so until now
     * <em>every error in the classic UI vanished silently</em>, the same class of
     * bug as the dropped model messages.  Worse, some errors carry a
     * {@code callback} the caller relies on running when the panel closes: the
     * uncaught-exception handler in {@code FreeColClient} shows a serious error
     * with a {@code System.exit} callback, so a no-op left the app hung in a
     * broken state, neither warning the player nor exiting.
     *
     * <p>Show the message in the shared classic popup and run the callback on
     * dismiss — in a {@code finally}, so the exit path fires even if the popup
     * itself throws.
     */
    @Override
    public FreeColPanel showErrorPanel(String message, Runnable callback) {
        final String text = (message == null) ? "" : message;
        onEventThread(() -> {
                try {
                    this.prompter.ask(notice("error", text,
                        Messages.message("classic.dialog.error"), null));
                } finally {
                    if (callback != null) callback.run();
                }
                return null;
            }, null);
        return null;
    }

    /**
     * {@inheritDoc}
     *
     * A notice box (build spec W7): any key or a click closes it, then the
     * controller logs out and the title comes back.  It comes only on the
     * server's verdict ({@code InGameController.setDeadHandler}), in the
     * "levi" rules (Roger's house rule: no revenge mode, a defeat ends the
     * game).  FreeCol's words for now; the original's own text (GAME.TXT
     * {@code @LOSENOCOLONIES}) belongs to the words items (W8, N1).
     */
    @Override
    public void showGameOverPanel(StringTemplate template) {
        final String text = Messages.message(template);
        ClassicFrameRecorder.event("game-over", template.getId());
        onEventThread(() -> this.prompter.ask(notice("game-over", text,
                          Messages.message("classic.dialog.messages"), null)),
                      ClassicAdvisorBox.Bar.DISMISSED);
    }

    // Event confirm dialogs (async Boolean handlers)

    /**
     * {@inheritDoc}
     *
     * The king's demands — raise tax, offer/impose mercenaries, declare war on
     * our behalf.  A no-op here was a real flow bug, not just a missing screen:
     * {@code monarchActionHandler} passes the player's yes/no to
     * {@code answerMonarch} over the wire, so without a dialog a tax hike was
     * silently accepted-by-omission (the exchange dropped) and the player never
     * got to hold a Tea Party.  Mirror the standard {@code MonarchDialog}: the
     * message and per-action button labels come off the {@link MonarchAction}
     * (a null {@code yesKey} = an acknowledge-only notice), over the monarch's
     * portrait.  Enter takes the original's first row
     * ({@link #monarchEnterAccepts}): at a tax rise that is "kiss the ring",
     * not the party.  Escape does nothing in a box with a choice (Roger:
     * "man muss sich entscheiden"): the box and its bar stay until a row is
     * taken; FreeCol's own box takes Escape as the party.  A box that closed
     * without a row (the game view went) answers its first row, the ring or
     * "Nein danke", never the party ({@link #eventAnswer}).  The notices
     * without a choice still close on any key.  In the advisor box
     * (W7) the King stands at the left and the box is flush right
     * ({@link ClassicAdvisorBox.Portrait#KING}); where Enter takes the "no"
     * (the mercenary offers) the "no" is the first row, as the original's
     * {@code @MERCENARIES} lists it.
     */
    @Override
    public void showMonarchDialog(MonarchAction action, StringTemplate template,
                                  String monarchKey,
                                  DialogHandler<Boolean> handler) {
        if (action == null) {
            if (handler != null) handler.handle(false);
            return;
        }
        final String messageId = action.getTextKey();
        String yesKey = action.getYesKey();
        if (!Messages.containsKey(yesKey)) yesKey = null;
        String noKey = action.getNoKey();
        if (!Messages.containsKey(noKey)) noKey = "close";
        String hdrKey = action.getHeaderKey();
        if (!Messages.containsKey(hdrKey)) hdrKey = "monarchDialog.default";
        final StringTemplate msg = (template == null)
            ? StringTemplate.key(messageId)
            : StringTemplate.copy(messageId, template);
        final boolean enterAccepts = monarchEnterAccepts(action);
        askEvent("monarch " + action, ImageLibrary.getMonarchImage(monarchKey),
                 Messages.message(hdrKey), msg, yesKey, noKey, enterAccepts,
                 !enterAccepts, true, ClassicAdvisorBox.Portrait.KING, handler);
    }

    /**
     * Whether Enter accepts the king's box (build spec W24).  Every box of
     * the original opens with its bar on row 1 unless GAME.TXT gives an
     * {@code @default}, and the tax texts (GAME.TXT {@code @KINGSTAMPACT},
     * {@code @KINGTAX}, {@code @KINGWAR} ...) have none: their bar starts on
     * the first {@code @TAXOPTIONS} row, "Den königlichen Ring küssen"
     * (clip005 #17489, clip006 #7380; both times the bar was moved down to
     * the party before the close), which is FreeCol's "yes".  FreeCol's own
     * box defaults to the party.  The mercenary offers keep FreeCol's "no":
     * the first row of {@code @MERCENARIES} is "Nein danke." (I, not seen in
     * a clip).
     *
     * @param action The king's action.
     * @return True if Enter takes the "yes" row.
     */
    static boolean monarchEnterAccepts(MonarchAction action) {
        return action == MonarchAction.RAISE_TAX_ACT
            || action == MonarchAction.RAISE_TAX_WAR;
    }

    /**
     * {@inheritDoc}
     *
     * Meeting a native nation for the first time.  The handler carries the
     * player's response back to {@code firstContact}, so — like the monarch and
     * demand dialogs — a no-op dropped the exchange.  Mirrors
     * {@code FirstContactDialog}: the welcome text (offer variant when a
     * {@code tile} is on the table), a per-nation meeting header, over the
     * meeting illustration.  Enter takes "Ja", the peace: the original's
     * {@code @INDIANWELCOME} has no {@code @default}, so its bar opens on
     * row 1 "Ja", and FreeCol's own box defaults to "yes".  A refusal costs
     * dearly (major tension, a mission ban for that nation, the offered
     * land; the original's {@code @INDIANSHUN}: war).  Escape still answers
     * "no" (W0e, Roger's rule; an open question for him).  In the advisor
     * box (W7) the tribe's chief stands at the right under the box
     * ({@link ClassicAdvisorBox.Portrait#chief}); the words are still
     * FreeCol's (the original's chain {@code @INDIANWELCOME},
     * {@code @INDIANPEACE}, {@code @INDIANCOME} is W8c).
     */
    @Override
    public void showFirstContactDialog(Player player, Player other, Tile tile,
                                       int settlementCount,
                                       DialogHandler<Boolean> handler) {
        final String messageId = (tile != null)
            ? "firstContactDialog.welcomeOffer.text"
            : "firstContactDialog.welcomeSimple.text";
        final String type = ((IndianNationType) other.getNationType())
            .getSettlementTypeKey(true);
        final StringTemplate msg = StringTemplate.template(messageId)
            .addStringTemplate("%nation%", other.getNationLabel())
            .addName("%camps%", Integer.toString(settlementCount))
            .add("%settlementType%", type);
        String hdrKey = "firstContactDialog.meeting."
            + other.getNation().getSuffix();
        if (!Messages.containsKey(hdrKey)) {
            hdrKey = "firstContactDialog.meeting.natives";
        }
        // The first meeting's woodcut, before the box (W9, N17).
        onEventThread(() -> {
                contactWoodcut(other);
                return null;
            }, null);
        askEvent("first-contact " + other.getNation().getSuffix(),
                 ImageLibrary.getMeetingImage(other), Messages.message(hdrKey),
                 msg, "yes", "no", true, false, false,
                 ClassicAdvisorBox.Portrait.chief(tribeIndex(other)), handler);
    }

    /**
     * The original tribes in NAMES.TXT {@code @TRIBES} order, as FreeCol's
     * nation suffixes: the chiefs' portraits {@code IND<n>A0}.
     */
    static final List<String> TRIBES = List.of("inca", "aztec", "arawak",
        "iroquois", "cherokee", "apache", "sioux", "tupi");

    /**
     * The original tribe of a native player.
     *
     * @param player The player.
     * @return Its {@link #TRIBES} index, or -1.
     */
    static int tribeIndex(Player player) {
        return (player == null || player.getNation() == null) ? -1
            : TRIBES.indexOf(player.getNation().getSuffix());
    }

    /**
     * {@inheritDoc}
     *
     * A native unit demanding tribute (gold / food / other goods) from a
     * colony.  The handler sends accept/reject to {@code indianDemand}, so a
     * no-op left the demand unanswered.  Mirrors {@code NativeDemandDialog}:
     * the demand text and yes/no labels vary by what is demanded, over the
     * colony's settlement sprite.  Enter and Escape refuse, as in FreeCol's
     * box: the original's demand boxes (GAME.TXT {@code @INDIANGOLD},
     * {@code @WANTSTUFF}, {@code @INDIANBEGFOOD}) have no {@code @default}
     * and list the refusal first, so their bar opens on it (I: no clip shows
     * one; the rule holds for the king's, the village and the father boxes).
     * The advisor box (W7) lists the refusal first, as the original does,
     * with the bar on it (D acceptance D5).
     */
    @Override
    public void showNativeDemandDialog(Unit unit, Colony colony, GoodsType type,
                                       int amount,
                                       DialogHandler<Boolean> handler) {
        final String nation = Messages.message(unit.getOwner().getNationLabel());
        final StringTemplate msg;
        final String yes, no;
        if (type == null) {
            msg = StringTemplate.template("indianDemand.gold.text")
                .addName("%nation%", nation).addName("%colony%", colony.getName())
                .addAmount("%amount%", amount);
            yes = "accept"; no = "indianDemand.gold.no";
        } else if (type.isFoodType()) {
            msg = StringTemplate.template("indianDemand.food.text")
                .addName("%nation%", nation).addName("%colony%", colony.getName())
                .addAmount("%amount%", amount);
            yes = "indianDemand.food.yes"; no = "indianDemand.food.no";
        } else {
            msg = StringTemplate.template("indianDemand.other.text")
                .addName("%nation%", nation).addName("%colony%", colony.getName())
                .addAmount("%amount%", amount).addNamed("%goods%", type);
            yes = "accept"; no = "indianDemand.other.no";
        }
        final StringTemplate title = StringTemplate
            .template("nativeDemandDialog.name").addName("%colony%", colony.getName());
        askEvent("native-demand", demandIcon(colony), Messages.message(title),
                 msg, yes, no, false, true, false,
                 ClassicAdvisorBox.Portrait.NONE, handler);
    }

    /**
     * The native demand box's icon: the colony's settlement sprite.  A
     * test without the image resources replaces it (their fallback image
     * would be fatal there).
     *
     * @param colony The colony demanded of.
     * @return The icon.
     */
    java.awt.Image demandIcon(Colony colony) {
        return this.imageLibrary.getSmallSettlementImage(colony);
    }

    /**
     * Shared body of the event-confirm dialogs above: show {@code message} in
     * an advisor box (build spec W7) with a "yes" and a "no" row (or as a
     * notice without rows when {@code yesKey} is null), and hand the answer
     * to {@code handler} as a {@code Boolean}.
     *
     * <p>These {@code GUI} seams are asynchronous ({@link DialogHandler}), but
     * the box is modal-blocking ({@link ClassicAdvisorLayer#show}) -- which is
     * right for a demand that must be answered.  The controllers already post
     * them via {@code invokeLater}, so blocking on the EDT (which pumps
     * events) is fine; the handler fires with the result the instant the box
     * closes.  The handler runs in a {@code finally} so the server exchange
     * still resolves (as a reject) if the box throws, rather than dangling.
     * Escape takes the "no" row (W0e), whichever row Enter takes, except
     * where the player must choose ({@code mustChoose}: the King's decision
     * boxes, G1): there Escape does nothing, the box is asked until a row
     * is taken ({@link #askUntilAnswered}), and a box that closed without
     * one answers its first row ({@link #eventAnswer}).  A click
     * outside the box does nothing here ({@link
     * ClassicAdvisorBox.Request#outsideCancels} off): these "no"s cannot be
     * undone (the Tea Party, a refused peace, a refused demand), and the
     * notices that often come just before close on a click anywhere
     * (E acceptance A5).
     *
     * @param id What the box is (for the recorder).
     * @param icon The stopgap's illustration, or null.
     * @param title The stopgap's window title.
     * @param enterAccepts Whether the bar starts on (Enter takes) the "yes"
     *     row; else on the "no" row.
     * @param noFirst Whether the "no" row comes first (the original's order
     *     where Enter refuses: the natives' demands, the mercenaries).
     * @param mustChoose Whether Escape does nothing (a box with rows only).
     * @param portrait Who stands at the box.
     */
    private void askEvent(String id, java.awt.Image icon, String title,
                          StringTemplate message, String yesKey, String noKey,
                          boolean enterAccepts, boolean noFirst,
                          boolean mustChoose,
                          ClassicAdvisorBox.Portrait portrait,
                          DialogHandler<Boolean> handler) {
        final ClassicAdvisorBox.Builder b = ClassicAdvisorBox.Request
            .builder(id).freeColText(Messages.message(message))
            .portrait(portrait).outsideCancels(false).stopgap(title, icon);
        final int yesRow;
        if (yesKey == null) {
            yesRow = -1;
        } else {
            final String yes = ClassicAdvisorBox.literal(Messages.message(yesKey));
            final String no = ClassicAdvisorBox.literal(Messages.message(noKey));
            yesRow = noFirst ? 1 : 0;
            if (noFirst) b.rows(no, yes); else b.rows(yes, no);
            b.defaultRow(enterAccepts ? yesRow : 1 - yesRow).cancelRow(1 - yesRow);
            if (mustChoose) b.noEscape();
        }
        final ClassicAdvisorBox.Request r = b.build();
        onEventThread(() -> {
                int chosen = ClassicAdvisorBox.Bar.DISMISSED;
                try {
                    chosen = askUntilAnswered(r);
                } finally {
                    if (handler != null) handler.handle(eventAnswer(r, yesRow, chosen));
                }
                return null;
            }, null);
    }

    /** How often a box that must be answered is asked at most. */
    static final int ASK_ROUNDS = 1000;

    /**
     * Ask a box until it is answered (EDT only): a prompter may report a
     * box that ignores Escape as still open ({@link ClassicAdvisorBox.Bar#OPEN}:
     * the stopgap's selection list, the tests), and it is then asked again,
     * as the father box is ({@link #askFathers}).
     *
     * @param r The box.
     * @return The answer ({@link Prompter#ask}); {@code OPEN} only after
     *     {@link #ASK_ROUNDS} rounds.
     */
    int askUntilAnswered(ClassicAdvisorBox.Request r) {
        int chosen = ClassicAdvisorBox.Bar.OPEN;
        for (int round = 0; round < ASK_ROUNDS
                 && chosen == ClassicAdvisorBox.Bar.OPEN; round++) {
            chosen = this.prompter.ask(r);
        }
        if (chosen == ClassicAdvisorBox.Bar.OPEN) {
            logger.warning("Classic box " + r.id + " still open after "
                + ASK_ROUNDS + " rounds.");
        }
        return chosen;
    }

    /**
     * The answer of an event box ({@link #askEvent}): its "yes" row, or
     * not.  A box that closed without a row (the game view went, the box
     * failed) answers "no", except a box that ignores Escape (the King's
     * decisions): it answers the row its bar opened on (the ring at a tax
     * rise, "Nein danke" at an offer), so the party, the one "no" that
     * cannot be undone, is never held by itself.
     *
     * @param r The box.
     * @param yesRow Its "yes" row, or -1 for a notice.
     * @param chosen Its answer ({@link Prompter#ask}).
     * @return True for "yes".
     */
    static boolean eventAnswer(ClassicAdvisorBox.Request r, int yesRow,
                               int chosen) {
        if (yesRow < 0) return false;
        if (chosen >= 0 && chosen < r.rows.size()) return chosen == yesRow;
        return !r.escapes && r.defaultRow == yesRow;
    }

    // The silent seams (master plan N15, G1; ClassicSeams)

    /** The last fight shown to us: the units and their owners then. */
    private static final class Fight {

        final Unit attacker, defender;
        final Player attackerOwner, defenderOwner;

        Fight(Unit attacker, Unit defender) {
            this.attacker = attacker;
            this.defender = defender;
            this.attackerOwner = (attacker == null) ? null : attacker.getOwner();
            this.defenderOwner = (defender == null) ? null : defender.getOwner();
        }

        /** @return The owner of the one that fought {@code u}, or null. */
        Player otherOwner(Unit u) {
            if (u == null) return null;
            if (u == this.attacker) return this.defenderOwner;
            if (u == this.defender) return this.attackerOwner;
            return null;
        }
    }

    /** The last fight ({@link #animateUnitAttack}), or null.  EDT only. */
    private Fight lastFight = null;

    /**
     * {@inheritDoc}
     *
     * <p>No animation yet (W12): only who fought is kept, for the loot's
     * notice ({@link #showCaptureGoodsDialog}), whose message does not name
     * the loser.  The server sends the attack's animation before the loot
     * in the same change set, and the controller posts both in that order.
     */
    @Override
    public void animateUnitAttack(Unit attacker, Unit defender,
                                  Tile attackerTile, Tile defenderTile,
                                  boolean success) {
        this.lastFight = new Fight(attacker, defender);
    }

    /**
     * Ask a notice or a box whose answer nobody waits for: at once on the
     * event thread (the controllers post the seams with
     * {@code invokeLater}), else posted, as {@link #showInformationPanel}.
     *
     * @param r The box.
     */
    private void askOrPost(ClassicAdvisorBox.Request r) {
        if (SwingUtilities.isEventDispatchThread()) {
            this.prompter.ask(r);
        } else {
            SwingUtilities.invokeLater(() -> this.prompter.ask(r));
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>The base GUI's silence here froze the game: the server waits for
     * the loot's answer before any end of turn (a {@code LootSession}
     * has no timer).  So it is answered at once, without a question
     * ({@link ClassicSeams#lootTaken}: what fits, the most valuable first),
     * and only then the original's {@code @CARGOCAPTURE} notice comes, one
     * per goods type ({@link ClassicSeams#lootNotices}).  The handler gets
     * a list in every case, empty if nothing fits (null would leave the
     * session open).
     */
    @Override
    public void showCaptureGoodsDialog(final Unit unit, List<Goods> gl,
                                       DialogHandler<List<Goods>> handler) {
        if (handler == null) return;
        if (unit == null) {
            logger.warning("Classic loot without a winner: not answered.");
            return;
        }
        invokeNowOrLater(() -> {
                List<Goods> taken = new ArrayList<>();
                try {
                    taken = ClassicSeams.lootTaken(unit, gl);
                } finally {
                    ClassicFrameRecorder.event("loot", unit.getId() + " offered="
                        + ((gl == null) ? 0 : gl.size()) + " taken=" + taken.size());
                    handler.handle(new ArrayList<>(taken));
                }
                final Fight f = this.lastFight;
                try {
                    for (ClassicAdvisorBox.Request r : ClassicSeams.lootNotices(
                             ClassicText.load(ClassicPackFiles.runtime()), unit,
                             (f == null) ? null : f.otherOwner(unit), taken,
                             Messages.message("captureGoodsDialog.title"))) {
                        this.prompter.ask(r);
                    }
                } catch (RuntimeException e) {
                    logger.log(Level.WARNING, "Classic loot notice failed.", e);
                }
            });
    }

    /**
     * {@inheritDoc}
     *
     * <p>William Brewster (the server no longer picks the recruit) and the
     * Fountain of Youth: the list box of the three recruits
     * ({@link ClassicSeams#emigrationRequest}).  The bar starts on row 1;
     * Enter or a click takes a recruit; a click beside the box does
     * nothing, and so does Escape at the Fountain of Youth.  The base
     * GUI's silence meant that after Brewster no recruit ever came again.
     * A box that closed without a row (Escape at Brewster's, or the game
     * view went) calls nothing: Brewster's choice comes again at the next
     * turn start.
     */
    @Override
    public void showEmigrationDialog(final Player player, final boolean foy,
                                     DialogHandler<Integer> handler) {
        if (player == null || handler == null) return;
        final Europe europe = player.getEurope();
        final List<AbstractUnit> recruits = (europe == null)
            ? new ArrayList<>() : europe.getExpandedRecruitables(false);
        if (recruits.isEmpty()) {
            logger.info("Classic emigration box: no recruits.");
            return;
        }
        final ClassicAdvisorBox.Request r = ClassicSeams.emigrationRequest(
            ClassicText.load(ClassicPackFiles.runtime()), player, foy, recruits,
            Messages.message("classic.dialog.messages"));
        invokeNowOrLater(() -> {
                // The Fountain of Youth's woodcut, if its notice did not
                // bring it first (N17).
                if (foy) noticeWoodcut(ClassicWoodcut.FOUNTAIN);
                final int chosen = askUntilAnswered(r);
                ClassicFrameRecorder.event("emigration", r.id + " foy=" + foy
                    + " rows=" + recruits.size() + " chosen=" + chosen);
                if (chosen >= 0 && chosen < recruits.size()) {
                    handler.handle(Europe.MigrationType.migrantIndexToSlot(chosen));
                }
            });
    }

    /**
     * {@inheritDoc}
     *
     * <p>A region's name ({@link ClassicSeams#namesRegion}): the default
     * name at once, no box (the original names no region).  Unanswered,
     * the server never counted a land region discovered, and FreeCol's
     * goto stopped after every land step that left moves (G review).  The
     * new land's name is still silent (W10).
     */
    @Override
    public void showNamingDialog(StringTemplate template, final String defaultName,
                                 final Unit unit, DialogHandler<String> handler) {
        if (handler == null || !ClassicSeams.namesRegion(template)) return;
        ClassicFrameRecorder.event("region-named", defaultName
            + ((unit == null) ? "" : " unit=" + unit.getId()));
        handler.handle(defaultName);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The first contact with a European nation: the server sends us
     * the peace treaty and waits for the answer (1000 hours in single
     * player) before any end of turn, so the base GUI's silence froze the
     * game.  The peace is accepted at once, without a box
     * ({@link ClassicSeams#isContactPeace}; I: no clip shows such a
     * meeting).  Any other proposal sent to us is a box in FreeCol's words
     * ({@link ClassicSeams#negotiationText}) with "Annehmen" and
     * "Abbrechen", the bar on "Abbrechen", which Escape takes too.  Our own
     * proposals (a scout's negotiation, a ship's trade at a foreign
     * colony) are not built yet: the "not yet" notice, nothing happens.
     */
    @Override
    public void showNegotiationDialog(FreeColGameObject our,
                                      FreeColGameObject other,
                                      final DiplomaticTrade agreement,
                                      StringTemplate comment,
                                      DialogHandler<DiplomaticTrade> handler) {
        if (handler == null) return;
        if (agreement == null) {
            logger.warning("Classic negotiation without an agreement.");
            return;
        }
        final String title = Messages.message("negotiationDialog.title."
            + agreement.getContext().getKey());
        // The first meeting with another European nation: its woodcut
        // first, once per game (N17).
        if (agreement.getContext() == DiplomaticTrade.TradeContext.CONTACT
            && !woodcutShown(ClassicWoodcut.EUROPEANS)) {
            onEventThread(() -> woodcut(ClassicWoodcut.EUROPEANS, 0L,
                                        ClassicAdvisorLayer.CHAIN_MS),
                          ClassicAdvisorLayer.NOT_SHOWN);
        }
        if (ClassicSeams.isOwnProposal(agreement)) {
            ClassicFrameRecorder.event("negotiation", "own "
                + agreement.getContext() + ": not yet");
            try {
                handler.handle(null);
            } finally {
                askOrPost(notice("negotiation", Messages.message(
                    "classic.mainMenu.notYet"), title, null));
            }
            return;
        }
        if (ClassicSeams.isContactPeace(agreement)) {
            ClassicFrameRecorder.event("negotiation", "contact peace accepted");
            agreement.setStatus(TradeStatus.ACCEPT_TRADE);
            handler.handle(agreement);
            return;
        }
        askEvent("negotiation " + agreement.getContext(), null, title,
            StringTemplate.name(ClassicSeams.negotiationText(comment, agreement)),
            "negotiationDialog.accept", "negotiationDialog.cancel", false, false,
            false, ClassicAdvisorBox.Portrait.NONE, (Boolean yes) -> {
                ClassicFrameRecorder.event("negotiation", agreement.getContext()
                    + " accepted=" + yes);
                agreement.setStatus((Boolean.TRUE.equals(yes))
                    ? TradeStatus.ACCEPT_TRADE
                    : TradeStatus.REJECT_TRADE);
                handler.handle(agreement);
            });
    }

    /**
     * {@inheritDoc}
     *
     * <p>A native village's facts in FreeCol's words, as a notice
     * ({@link ClassicSeams#villageText}): the classic map shows it after a
     * click on a village (an invention: the original's reaction to that
     * click is in no clip).
     */
    @Override
    public FreeColPanel showIndianSettlementPanel(IndianSettlement is) {
        final Player me = myPlayer();
        if (is == null || me == null) return null;
        ClassicFrameRecorder.event("village-notice", is.getId());
        askOrPost(notice("village-notice " + is.getId(),
            ClassicSeams.villageText(is, me),
            Messages.message("classic.dialog.messages"), iconOf(is)));
        return null;
    }

    /**
     * {@inheritDoc}
     *
     * <p>A tile's facts in FreeCol's words, as a notice
     * ({@link ClassicSeams#tileText}).  Nothing of the classic UI calls it
     * (the original's tile facts are the view mode's panel); an unexplored
     * tile shows nothing.
     */
    @Override
    public FreeColPanel showTilePanel(Tile tile) {
        if (tile == null || !tile.isExplored()) return null;
        ClassicFrameRecorder.event("tile-notice", tile.getId());
        askOrPost(notice("tile-notice " + tile.getId(),
            ClassicSeams.tileText(tile, myPlayer()),
            Messages.message("classic.dialog.messages"), null));
        return null;
    }

    /**
     * {@inheritDoc}
     *
     * The controllers call this after a recruit / train / purchase so any open
     * Europe view refreshes; repaint the classic Europe screen if it is showing.
     */
    @Override
    public void updateEuropeanSubpanels() {
        final ClassicEuropePanel panel = this.europePanel;
        if (panel != null) SwingUtilities.invokeLater(panel::refresh);
    }

    /**
     * {@inheritDoc}
     *
     * The base implementation asks for the name through {@code modalInputDialog},
     * which the classic {@code GUI} still no-ops (dialogs are Phase 3) — so it
     * would return null and silently abort every attempt to found a colony.
     * Until the classic name prompt exists, take the name FreeCol would have
     * suggested, made unique if the player somehow already used it.
     */
    @Override
    public String getNewColonyName(Player player, Tile tile) {
        // The founding's woodcut comes before the colony screen (W9).
        noteFounding(tile);
        final String suggested = player.getSettlementName(null);
        if (player.getSettlementByName(suggested) == null) return suggested;
        for (int i = 2; i < 100; i++) {
            final String name = suggested + " " + i;
            if (player.getSettlementByName(name) == null) return name;
        }
        return suggested;
    }

    /**
     * {@inheritDoc}
     *
     * A placeholder until the original's KAMPFANALYSE box (build spec
     * W12): attack at once.  {@code GUI.confirmPreCombat} asks here
     * whenever the client option {@code guiShowPreCombat} is on, which it
     * is by default (client-options.xml), and the base {@code GUI} answers
     * false -- so every attack ({@code InGameController.moveAttack}, the
     * ranged path and {@code moveAttackSettlement}) was cancelled before
     * it reached the server.
     */
    @Override
    public boolean showPreCombatDialog(Unit attacker,
                                       FreeColGameObject defender,
                                       Tile tile) {
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * The original has no such event pictures: nothing is shown at the
     * first landing, and the Pacific's discovery shows the original's
     * woodcut 6 instead (N17; FreeCol calls this only for the first
     * discoverer in the whole game).  The callers still need a panel:
     * {@code InGameController.newLandName} adds a closing callback to the
     * result (the build-colony tip and the next message), and the base
     * {@code GUI}'s null would throw there.  The returned stand-in is
     * never shown and counts as already closed, so it runs each closing
     * callback at once.  The other caller (the Pacific discovery) ignores
     * the result.
     */
    @Override
    public FreeColPanel showEventPanel(String header, String image,
                                       String footer) {
        // The Pacific has the original's woodcut 6 (N17), once per game.
        if (PACIFIC_IMAGE.equals(image)
            && !woodcutShown(ClassicWoodcut.PACIFIC)) {
            onEventThread(() -> woodcut(ClassicWoodcut.PACIFIC, 0L,
                                        ClassicAdvisorLayer.CHAIN_MS),
                          ClassicAdvisorLayer.NOT_SHOWN);
        }
        return new ClosedPanel(getFreeColClient());
    }

    /** FreeCol's picture of the Pacific's discovery ({@link #showEventPanel}). */
    static final String PACIFIC_IMAGE = "image.flavor.event.discoverPacific";

    /**
     * The panel {@link #showEventPanel} hands out in place of one it does
     * not show: closed from the start, so closing callbacks run at once.
     */
    private static final class ClosedPanel extends FreeColPanel {

        /**
         * Create the stand-in.
         *
         * @param freeColClient The {@code FreeColClient} for the game.
         */
        ClosedPanel(FreeColClient freeColClient) {
            super(freeColClient);
        }

        /**
         * {@inheritDoc}
         *
         * Runs the callback now: this panel never opens, so it never
         * closes later.
         */
        @Override
        public FreeColPanel addClosingCallback(Runnable runnable) {
            if (runnable != null) runnable.run();
            return this;
        }
    }

    /**
     * {@inheritDoc}
     *
     * The base {@code GUI} <em>declines</em> every confirmation, which silently
     * aborts the controller flows that gate on one — notably {@code buildColony},
     * which confirms the site warnings before founding a colony.
     *
     * <p>The question goes into an advisor box (build spec W7): FreeCol's
     * words, its "yes" row first and its "no" row second, the bar on the row
     * {@code defaultOk} names (the original has no such box, so FreeCol's
     * default stands in for a GAME.TXT {@code @default}).  Escape takes the
     * "no" row, and a box that could not open answers "no" too, whatever the
     * default (build spec W0e): Escape had answered {@code defaultOk}, so it
     * chose "learn" at a village and "found" at the site warnings.
     *
     * <p>FreeCol's own question on sailing from coastal water onto the high
     * seas ({@code InGameController.moveHighSeas}) is never shown: it is
     * answered "no" at once, so that move is a plain one (W0f).  The
     * Europe question comes only at the map's east and west edges
     * ({@link #sailHomeKey}).
     *
     * <p>FreeCol's landing question for a ship with one passenger that can
     * go ashore is the original's @LANDFALL box ({@link #askLandfall},
     * build spec W8b).
     */
    @Override
    public boolean modalConfirmDialog(Tile tile, StringTemplate template,
                                      ImageIcon icon, String okKey,
                                      String cancelKey, boolean defaultOk) {
        if (silentNo(template)) {
            ClassicFrameRecorder.event("dialog-silent", template.getId() + " no");
            return false;
        }
        if (isLandfall(template)) {
            final Unit carrier = landingCarrier((this.mapViewer == null) ? null
                : this.mapViewer.getActiveUnit(), tile);
            return askLandfall(carrier, firstLander(carrier, tile));
        }
        // The learn question is a village box: the first entry's woodcut
        // before it when no key brought one (W9).
        if (template != null && LEARN_QUESTION.equals(template.getId())) villageWoodcut();
        final ClassicAdvisorBox.Request r = ClassicAdvisorBox.Request
            .builder("confirm " + template.getId())
            .freeColText(Messages.message(template))
            .rows(ClassicAdvisorBox.literal(Messages.message(okKey)),
                  ClassicAdvisorBox.literal(Messages.message(cancelKey)))
            .defaultRow(defaultOk ? 0 : 1).cancelRow(1)
            .stopgap(colony(tile), (icon == null) ? null : icon.getImage())
            .build();
        final int chosen = onEventThread(() -> this.prompter.ask(r),
                                         ClassicAdvisorBox.Bar.DISMISSED);
        return confirmed(chosen);
    }

    /** FreeCol's question on crossing onto the high seas, never shown (W0f). */
    static final String HIGH_SEAS_QUESTION = "highseas.text";

    /** FreeCol's question at a village's teacher ({@code moveLearnSkill}). */
    static final String LEARN_QUESTION = "learnSkill.text";

    /**
     * Whether a confirm is answered "no" without a box: FreeCol's high-seas
     * question (W0f).
     *
     * @param template The question.
     * @return True if it is never shown.
     */
    static boolean silentNo(StringTemplate template) {
        return template != null && HIGH_SEAS_QUESTION.equals(template.getId());
    }

    /**
     * The answer of a yes/no box: only its first row is "yes"; the other,
     * Escape and a box that closed or could not open ({@code -1}) are "no"
     * (build spec W0e).
     *
     * @param chosen The row the box returned, -1 if dismissed.
     * @return True for "yes".
     */
    static boolean confirmed(int chosen) {
        return chosen == 0;
    }

    /**
     * What puts the classic boxes: {@link #putBox} in the game, a fake in
     * the tests.  EDT only.
     */
    interface Prompter {

        /**
         * Put a box and wait for its answer.
         *
         * @param request The box.
         * @return The row taken; on Escape the request's cancel row, else
         *     -1 ({@link ClassicAdvisorBox.Request#escapeAnswer}); 0 for a
         *     notice; -1 if the box closed with no row or could not open.
         */
        int ask(ClassicAdvisorBox.Request request);
    }

    /**
     * Put a box: in the game's canvas when the map is what the player is
     * looking at ({@link ClassicAdvisorLayer}, build spec W7), else -- on
     * the title screens, over a colony, Europe or report screen, or without
     * the pack -- in the stopgap window ({@link #stopgapBox}).  EDT only.
     *
     * @param r The box.
     * @return The answer ({@link Prompter#ask}).
     */
    int putBox(ClassicAdvisorBox.Request r) {
        final ClassicAdvisorLayer layer = this.boxLayer;
        if (layer != null && !this.sceneShowing && this.hudPane != null
            && this.hudPane.isShowing() && dialogOwner() == this.frame) {
            final int got = layer.show(r);
            if (got != ClassicAdvisorLayer.UNAVAILABLE) return got;
        }
        return stopgapBox(r);
    }

    /**
     * The stopgap of a box: the shared {@link ClassicDialog} popup (rows as
     * plates), or the selection list for a choice.  EDT only.
     *
     * @param r The box.
     * @return The answer ({@link Prompter#ask}).
     */
    private int stopgapBox(ClassicAdvisorBox.Request r) {
        final Window owner = dialogOwner();
        final ClassicDialog.Page page = new ClassicDialog.Page(r.plainText(), r.icon);
        if (r.isNotice()) {
            ClassicDialog.showMessages(owner, r.title, List.of(page));
            return 0;
        }
        if (r.isCheckbox()) {
            // The option boxes: a row flips and the popup comes back with
            // the new states, until it is closed.
            final ClassicAdvisorBox.Bar bar = new ClassicAdvisorBox.Bar(r);
            final String[] plain = r.plainRows();
            while (true) {
                final String[] rows = new String[plain.length];
                for (int i = 0; i < rows.length; i++) {
                    rows[i] = (bar.checked(i) ? "[x] " : "[ ] ") + plain[i];
                }
                final int chosen = ClassicDialog.ask(owner, r.title, page, rows,
                                                     Math.max(0, bar.row()));
                if (chosen < 0 || chosen >= rows.length) return r.escapeAnswer();
                bar.press(chosen, true);       // as a click on the row
                bar.release(chosen, true);
            }
        }
        final String[] rows = r.plainRows();
        if (r.list) {
            final Object sel = chooseFromList(owner, r.title, r.plainText(),
                (r.icon == null) ? null : new ImageIcon(r.icon), rows);
            for (int i = 0; i < rows.length; i++) {
                if (rows[i] == sel) return i;
            }
            return r.escapeAnswer();
        }
        // A box that ignores Escape (the King's decisions, the father and
        // emigration boxes) ignores it, and the close button, here too.
        final int chosen = ClassicDialog.ask(owner, r.title, page, rows,
                                             r.defaultRow, r.escapes);
        return (chosen < 0) ? r.escapeAnswer() : chosen;
    }

    /**
     * {@inheritDoc}
     *
     * Wired for the same reason as {@link #modalConfirmDialog}: some controller
     * flows can only proceed through a choice.  Notably, disembarking a carrier
     * that holds more than one unit asks <em>which</em> unit(s) to land — so
     * without this a laden ship could never put colonists ashore, and the colony
     * screen (which needs a founded colony) would be unreachable.
     *
     * <p>An advisor box (build spec W7): FreeCol's prompt, one row per
     * choice and, with a {@code cancelKey}, a last row for it ("Handlung
     * abbrechen"), which Escape takes too.  The bar starts on the choice
     * FreeCol marks as the default, else on row 1, as the original's village
     * boxes do (landfall #15898, #22322).  A choice FreeCol disables is
     * greyed and cannot be taken.  Without a cancel row, Escape closes with
     * no choice (null), as before.  Without the in-game canvas, or when the
     * rows do not fit on the screen, the stopgap selection list stays
     * ({@link #chooseFromList}).
     *
     * <p>FreeCol's landing list (a ship with several passengers that can
     * go ashore: one row per unit and "Alle") is the original's @LANDFALL
     * box ({@link #askLandfall}, build spec W8b): "An Land gehen" takes
     * FreeCol's first choice, the passenger aboard longest, and nothing
     * else; "Alle" is never the answer.
     */
    @Override
    protected <T> T modalChoiceDialog(Tile tile, StringTemplate template,
                                      ImageIcon icon, String cancelKey,
                                      List<ChoiceItem<T>> choices) {
        if (choices == null || choices.isEmpty()) return null;
        if (isLandfall(template)) {
            final T first = choices.get(0).getObject();
            final Unit lander = (first instanceof Unit) ? (Unit) first : null;
            final Unit carrier = (lander == null) ? null : lander.getCarrier();
            return (askLandfall(carrier, lander) && lander != null) ? first : null;
        }
        final ClassicAdvisorBox.Request r = choiceRequest(
            Messages.message(template), (cancelKey == null) ? null
                : Messages.message(cancelKey), choices, colony(tile),
            (icon == null) ? null : icon.getImage());
        final int chosen = onEventThread(() -> this.prompter.ask(r),
                                         ClassicAdvisorBox.Bar.DISMISSED);
        return (chosen >= 0 && chosen < choices.size()
                && choices.get(chosen).isEnabled())
            ? choices.get(chosen).getObject() : null;
    }

    /**
     * The box of a choice ({@link #modalChoiceDialog}).
     *
     * @param text The prompt.
     * @param cancel The cancel row's words, or null for none.
     * @param choices The choices.
     * @param title The stopgap's window title.
     * @param icon The stopgap's illustration, or null.
     * @return The request.
     */
    static <T> ClassicAdvisorBox.Request choiceRequest(String text, String cancel,
        List<ChoiceItem<T>> choices, String title, java.awt.Image icon) {
        final List<String> rows = new ArrayList<>();
        final boolean[] disabled = new boolean[choices.size() + 1];
        int def = 0;
        for (int i = 0; i < choices.size(); i++) {
            final ChoiceItem<T> c = choices.get(i);
            rows.add(ClassicAdvisorBox.literal(c.toString()));
            disabled[i] = !c.isEnabled();
            if (c.isDefault() && c.isEnabled()) def = i;
        }
        if (cancel != null) rows.add(ClassicAdvisorBox.literal(cancel));
        final ClassicAdvisorBox.Builder b = ClassicAdvisorBox.Request
            .builder("choice").freeColText(text).rows(rows).disabled(disabled)
            .defaultRow(def).stopgap(title, icon).list();
        if (cancel != null) b.cancelRow(rows.size() - 1); else b.noCancelRow();
        return b.build();
    }

    // The village boxes.  Only their cancel is seen here: the original
    // ends the turn 756 ms after a cancelled village box instead of 485
    // (build spec W5a, delta W5a).  The boxes themselves are W8's; a
    // cancel keeps the unit's move (Roger's house rule), as FreeCol does.
    // The first village entry's woodcut comes before them when no key
    // brought it (W9: a goto, a click; the key's is villageEntryKey).

    /** {@inheritDoc} */
    @Override
    public net.sf.freecol.common.model.Constants.ArmedUnitSettlementAction
        getArmedUnitSettlementChoice(
            net.sf.freecol.common.model.Settlement settlement) {
        villageWoodcut(settlement);
        return villageChoice(settlement,
                             super.getArmedUnitSettlementChoice(settlement));
    }

    /** {@inheritDoc} */
    @Override
    public net.sf.freecol.common.model.Constants.TradeAction
        getIndianSettlementTradeChoice(
            net.sf.freecol.common.model.Settlement settlement,
            StringTemplate template, boolean canBuy, boolean canSell,
            boolean canGift) {
        villageWoodcut(settlement);
        return villageChoice(settlement, super.getIndianSettlementTradeChoice(
                settlement, template, canBuy, canSell, canGift));
    }

    /** {@inheritDoc} */
    @Override
    public net.sf.freecol.common.model.Constants.MissionaryAction
        getMissionaryChoice(Unit unit,
            net.sf.freecol.common.model.IndianSettlement is,
            boolean canEstablish, boolean canDenounce) {
        villageWoodcut(is);
        return villageChoice(is, super.getMissionaryChoice(unit, is, canEstablish,
                                                           canDenounce));
    }

    /** {@inheritDoc} */
    @Override
    public net.sf.freecol.common.model.Constants.ScoutIndianSettlementAction
        getScoutIndianSettlementChoice(
            net.sf.freecol.common.model.IndianSettlement is,
            String numberString) {
        villageWoodcut(is);
        return villageChoice(is, super.getScoutIndianSettlementChoice(is,
                                                                      numberString));
    }

    /**
     * Pass a village box's answer on, telling the turn flow of a cancel
     * (null) so the next idle pause is the original's longer one, and the
     * Spielzugende mode's cursor goes to the village (landing-slow #4193).
     *
     * @param settlement The village, or null.
     * @param choice The answer, null for "Handlung abbrechen".
     * @return {@code choice}.
     */
    private <T> T villageChoice(net.sf.freecol.common.model.Settlement settlement,
                                T choice) {
        if (choice == null) {
            final Tile tile = (settlement == null) ? null : settlement.getTile();
            // The flow of this game only: a late post must not reach the
            // next game's (FINAL "Open" item 11).
            final ClassicTurnFlow flow = this.turnFlow;
            invokeNowOrLater(() -> {
                    if (flow != null && flow == this.turnFlow) {
                        flow.villageBoxCancelled(tile);
                    }
                });
        }
        return choice;
    }

    // The Europe question, by Roger's rule (build spec W8a), in the
    // original's advisor box (W7).

    /** GAME.TXT's Europe question (@SAILHOME). */
    static final String SAIL_HOME_SECTION = "SAILHOME";

    /**
     * Whether a move order gets the Europe question instead of a move, by
     * Roger's rule (master plan section 1): a ship on the high seas in the
     * last column the view shows, ordered east (6, 9 or 3) past it
     * ({@link ClassicHud#eastPastView}); and, the mirror at the west edge
     * (Roger, E1), a ship on the high seas in the first column the view
     * shows, ordered west (4, 7 or 1) past it
     * ({@link ClassicHud#westPastView}).  Entering the light water, leaving
     * it and moving along it are plain moves.
     *
     * @param unit The unit ordered.
     * @param direction The direction ordered.
     * @return True if the order asks the question.
     */
    static boolean asksSailHome(Unit unit, Direction direction) {
        return sailsPastView(unit, direction)
            && unit.getOwner().getEurope() != null;
    }

    /**
     * Whether a ship's order takes it past the view's edge from the high
     * seas ({@link #asksSailHome} without the question's need of Europe):
     * where the rebels' ships hear @EUROPENOTLEAVE
     * ({@link ClassicIllegalMoves#judge}).
     *
     * @param unit The unit ordered.
     * @param direction The direction ordered.
     * @return True for a ship with moves on the high seas at the edge,
     *     ordered past it.
     */
    static boolean sailsPastView(Unit unit, Direction direction) {
        if (unit == null || direction == null || !unit.isNaval()
            || !unit.hasTile() || unit.getMovesLeft() <= 0
            || !unit.getType().canMoveToHighSeas()
            || unit.getOwner() == null) {
            return false;
        }
        final Tile tile = unit.getTile();
        if (!tile.isDirectlyHighSeasConnected() || tile.getMap() == null) return false;
        final Tile target = tile.getNeighbourOrNull(direction);
        return ClassicHud.sidePastView(tile.getMap().getWidth(), direction,
            tile.getX(), (target == null) ? -1 : target.getX());
    }

    /**
     * A map key's move order, if it is the Europe question's
     * ({@link #asksSailHome}): ask it, in the original's advisor box
     * (build spec W7): GAME.TXT {@code @SAILHOME} with the admiral, the bar
     * on {@code @default=1}, "Jawohl" (landfall #7720, c5 #21226).  "Jawohl"
     * sails the ship to Europe ({@link #sailHome}): it leaves the map with
     * no slide, as in the original.  "Nein" and Escape do nothing: the ship
     * keeps its moves and stays the active unit, and the box's close
     * restarts its blink and the turn flow's clock
     * ({@link ClassicDialog.Watcher}).  EDT only.
     *
     * @param unit The unit ordered.
     * @param direction The direction ordered.
     * @return True if the order was the question's (whatever the answer),
     *     false if it is a plain move.
     */
    boolean sailHomeKey(Unit unit, Direction direction) {
        if (!asksSailHome(unit, direction)) return false;
        final Tile from = unit.getTile();
        final ClassicAdvisorBox.Request r = sailHomeRequest(
            ClassicText.load(ClassicPackFiles.runtime()), unit);
        final int chosen = this.prompter.ask(r);
        if (ClassicFrameRecorder.on()) {
            ClassicFrameRecorder.event("sail-home", "unit=" + unit.getId()
                + ((from == null) ? "" : " at=" + from.getX() + "," + from.getY())
                + " " + direction + " chosen=" + chosen);
        }
        if (confirmed(chosen)) sailHome(unit);
        return true;
    }

    // "Gehe zu" (G; R2, master plan W8e, N11; ClassicDestinations).

    /**
     * {@inheritDoc}
     *
     * <p>G and BEFEHLE "Zum Hafen gehen" / "Zum Ort gehen"
     * ({@code GotoAction} -&gt; {@code InGameController.selectDestination}):
     * the original's destination list ({@link ClassicDestinations}),
     * @SAILPORT for a ship, @TRAVELPLACE for a land unit, with no
     * portrait; the bar on row 1, the arrows move it, Enter takes the row.
     * Escape and a click outside choose nothing: the unit keeps its orders
     * and moves and stays the active unit.  With nowhere to go no box comes
     * and nothing happens (I).  A chosen row is returned to the controller,
     * whose {@code goToDestination} sets it and moves the unit at once
     * (the path "Zurück nach Europa" takes): its orders line "Ziel
     * Amsterdam" (or the colony's name) is painted
     * {@link ClassicMapViewer#GOTO_PANEL_MS} after the close and its first
     * slide starts {@link ClassicMapViewer#GOTO_SLIDE_MS} after it
     * (landfall #23372 -&gt; #23375 -&gt; #23381).  The unit counts as run
     * for this turn in the unit cycle; in later turns the cycle runs it
     * when it reaches it (master plan W5f).  EDT (the action's).
     */
    @Override
    public Location showSelectDestinationDialog(Unit unit) {
        if (unit == null) return null;
        final ClassicText text = ClassicText.load(ClassicPackFiles.runtime());
        final long t0 = System.nanoTime();
        final List<ClassicDestinations.Row> rows = ClassicDestinations.rows(text, unit);
        final long searchMs = (System.nanoTime() - t0) / 1_000_000L;
        if (rows.isEmpty()) {
            if (ClassicFrameRecorder.on()) {
                ClassicFrameRecorder.event("goto-list", "unit=" + unit.getId()
                    + " none ms=" + searchMs);
            }
            return null;
        }
        final ClassicAdvisorBox.Request r
            = ClassicDestinations.request(text, unit.isNaval(), rows);
        final int chosen = onEventThread(() -> this.prompter.ask(r),
                                         ClassicAdvisorBox.Bar.DISMISSED);
        final Location dest = (chosen < 0 || chosen >= rows.size()) ? null
            : rows.get(chosen).location;
        if (ClassicFrameRecorder.on()) {
            ClassicFrameRecorder.event("goto-list", "unit=" + unit.getId()
                + " rows=" + rows.stream().map(ClassicDestinations.Row::toString)
                    .collect(Collectors.joining("|"))
                + " chosen=" + chosen + " dest="
                + ((dest == null) ? "-" : dest.getId())
                + " ms=" + searchMs);
        }
        if (dest == null) return null;
        this.unitCycle.ran(unit);
        // The controller moves it now, inside the action: its arrival at a
        // colony is a goto arrival (W5f), until the action is done.
        this.keyGotoUnit = unit;
        SwingUtilities.invokeLater(() -> {
                if (this.keyGotoUnit == unit) this.keyGotoUnit = null;
            });
        if (gotoSlides(unit, dest) && this.mapViewer != null) {
            final long close = boxClosed();
            this.mapViewer.holdFirstSlide(unit,
                close + Math.round(ClassicMapViewer.GOTO_PANEL_MS * 1e6),
                () -> {
                    if (this.infoPanel != null) this.infoPanel.paintNow();
                },
                close + Math.round(ClassicMapViewer.GOTO_SLIDE_MS * 1e6));
        }
        return dest;
    }

    /**
     * Whether a unit sent to a destination slides now: it has moves, and
     * it is not a ship bound for Europe from water that leads there
     * directly (it sails at once, as after "Jawohl").
     *
     * @param unit The unit.
     * @param dest The destination.
     * @return True if its first slide follows.
     */
    static boolean gotoSlides(Unit unit, Location dest) {
        if (unit == null || dest == null || !unit.hasTile()
            || unit.getMovesLeft() <= 0) return false;
        return !(dest instanceof Europe
                 && unit.getTile().isDirectlyHighSeasConnected());
    }

    // The original's refusals (R3, ClassicIllegalMoves).

    /**
     * A map key's move order, if the game refuses it
     * ({@link ClassicIllegalMoves#judge}): the original's box for it, or
     * nothing, instead of FreeCol's illegal-move sound.  The controller
     * never sees the order: the unit keeps its moves, its state and its
     * place, and stays the active unit; the box's close restarts its blink
     * ({@link ClassicDialog.Watcher}), and nothing ends the turn.  The box
     * comes the landing box's time after the key ({@link #landfallShowAt},
     * I: no clip shows a refusal).  EDT only.
     *
     * @param unit The unit ordered.
     * @param direction The direction ordered.
     * @return True if the order was refused (with a box or without),
     *     false if the controller takes it.
     */
    boolean illegalMoveKey(Unit unit, Direction direction) {
        final ClassicIllegalMoves.Verdict v = ClassicIllegalMoves.judge(unit, direction);
        if (v == null) return false;
        if (ClassicFrameRecorder.on()) {
            final Tile from = unit.getTile();
            ClassicFrameRecorder.event("illegal-move", "unit=" + unit.getId()
                + ((from == null) ? "" : " at=" + from.getX() + "," + from.getY())
                + " dir=" + direction + " type=" + unit.getMoveType(direction)
                + " section=" + ((v.section == null) ? "-" : v.section));
        }
        if (v.isSilent()) return true;
        final ClassicAdvisorLayer layer = this.boxLayer;
        final long at = (layer == null || this.mapViewer == null) ? 0L
            : landfallShowAt(this.mapViewer.moveKeyNanos(), waitClock().now(),
                layer.loadsPalette(ClassicNotices.portrait(v.who, v.tribe)));
        this.prompter.ask(refusalRequest(v, at));
        return true;
    }

    /**
     * B with a unit that cannot found a colony now: the original's box for
     * it ({@link ClassicIllegalMoves#colonyRefusal}), or nothing.  EDT only.
     *
     * @param unit The active unit.
     */
    void colonyRefused(Unit unit) {
        refused("B", unit, ClassicIllegalMoves.colonyRefusal(unit));
    }

    /**
     * An order key whose FreeCol order cannot be given now
     * ({@link ClassicKeyMap#install}'s refusal): P and R get the original's
     * box for the cause ({@link ClassicIllegalMoves#orderRefusal}); every
     * other key does nothing, as before.  EDT only.
     *
     * @param binding The key's binding.
     */
    void orderRefused(ClassicKeyMap.Binding binding) {
        if (binding == null || binding.key == null) return;
        final int code = binding.key.getKeyCode();
        if (binding.key.getModifiers() != 0
            || (code != KeyEvent.VK_P
                && code != KeyEvent.VK_R)) return;
        if (this.mapViewer == null
            || this.mapViewer.getViewMode() != GUI.ViewMode.MOVE_UNITS) return;
        final Unit unit = this.mapViewer.getActiveUnit();
        refused((code == KeyEvent.VK_R) ? "R" : "P", unit,
            ClassicIllegalMoves.orderRefusal(unit,
                code == KeyEvent.VK_R));
    }

    /**
     * Show a refusal of a key's order (B, P, R): its box at once, or nothing.
     *
     * @param key The key, for the recorder.
     * @param unit The unit, or null.
     * @param v The refusal, or null (then nothing happens).
     */
    private void refused(String key, Unit unit, ClassicIllegalMoves.Verdict v) {
        if (v == null) return;
        if (ClassicFrameRecorder.on()) {
            ClassicFrameRecorder.event("refused", key + " unit="
                + ((unit == null) ? "-" : unit.getId())
                + " section=" + ((v.section == null) ? "-" : v.section));
        }
        if (!v.isSilent()) this.prompter.ask(refusalRequest(v, 0L));
    }

    /**
     * A refusal's box ({@link ClassicIllegalMoves#request}) from the pack.
     *
     * @param v The refusal, with a section.
     * @param showAtNanos When it should be on screen, 0 for at once.
     * @return The box.
     */
    ClassicAdvisorBox.Request refusalRequest(ClassicIllegalMoves.Verdict v,
                                             long showAtNanos) {
        return ClassicIllegalMoves.request(ClassicText.load(ClassicPackFiles.runtime()),
            v, showAtNanos, Messages.message("classic.dialog.messages"));
    }

    /**
     * "Jawohl": the ship sails to Europe ({@code InGameController.moveTo}),
     * taken off the map at once.
     *
     * @param unit The ship.
     */
    void sailHome(Unit unit) {
        final FreeColClient fcc = getFreeColClient();
        if (fcc == null || unit.getOwner().getEurope() == null) return;
        fcc.getInGameController().moveTo(unit, unit.getOwner().getEurope());
    }

    /**
     * The Europe question's box: GAME.TXT {@code @SAILHOME} from the pack,
     * its markup kept ({@code {hoher See}} in gold), its rows "Jawohl" and
     * "Nein" (Escape's), the bar on {@code @default}; else FreeCol's own
     * high-seas strings.  The admiral stands at the box either way.
     *
     * @param t The original texts, or null.
     * @param unit The ship (its sailing time, for FreeCol's text), or null.
     * @return The box.
     */
    static ClassicAdvisorBox.Request sailHomeRequest(ClassicText t, Unit unit) {
        final ClassicText.Message m = (t == null) ? null
            : t.message(SAIL_HOME_SECTION);
        if (m != null && !m.text.isEmpty() && m.options.size() >= 2
            && m.width != null) {
            final List<String> rows = new ArrayList<>();
            rows.add(m.options.get(0).trim());
            rows.add(m.options.get(1).trim());
            return ClassicAdvisorBox.Request.builder(SAIL_HOME_SECTION)
                .gameText(m.text).width(m.width).y(m.y).rows(rows)
                .defaultRow(ClassicHud.clamp(ClassicAdvisorBox.defaultRow(m), 0, 1))
                .cancelRow(1).portrait(ClassicAdvisorBox.Portrait.ADMIRAL)
                .stopgap(colony(null), null).build();
        }
        return ClassicAdvisorBox.Request.builder(SAIL_HOME_SECTION)
            .freeColText(Messages.message(StringTemplate
                .template("highseas.text")
                .addAmount("%number%", (unit == null) ? 0 : unit.getSailTurns())))
            .rows(ClassicAdvisorBox.literal(Messages.message("highseas.yes")),
                  ClassicAdvisorBox.literal(Messages.message("highseas.no")))
            .defaultRow(0).cancelRow(1)
            .portrait(ClassicAdvisorBox.Portrait.ADMIRAL)
            .stopgap(colony(null), null).build();
    }

    // The landing, as the original's (build spec W8b, master plan W18,
    // landing-slow 02-landing.md).

    /** GAME.TXT's landing question (@LANDFALL). */
    static final String LANDFALL_SECTION = "LANDFALL";

    /** FreeCol's landing question, in both of its seams. */
    static final String DISEMBARK_QUESTION = "disembark.text";

    /** The @LANDFALL row that goes ashore ("An Land gehen"); row 0 stays. */
    static final int LANDFALL_LAND_ROW = 1;

    /**
     * A landing the box sent ashore, until the controller re-selects the
     * ship after the move ({@link #landingDone}).
     */
    private static final class Landing {

        /** The ship. */
        final Unit carrier;

        /** The passenger sent ashore. */
        final Unit unit;

        Landing(Unit carrier, Unit unit) {
            this.carrier = carrier;
            this.unit = unit;
        }
    }

    /** The landing under way, or null. */
    private Landing landing = null;

    /**
     * The unit a movement key is putting aboard a carrier (spec delta
     * W18), while its move runs ({@link #unitBoarding},
     * {@link #carrierAfterBoarding}).
     */
    private Unit boardedUnit = null;

    /**
     * Whether a question is FreeCol's landing question.
     *
     * @param template The question.
     * @return True for {@code disembark.text}.
     */
    static boolean isLandfall(StringTemplate template) {
        return template != null && DISEMBARK_QUESTION.equals(template.getId());
    }

    /**
     * The ship of a landing asked through the one-passenger seam: the
     * active unit, a carrier with passengers next to the target tile (the
     * landing comes from its move order).
     *
     * @param active The map's active unit, or null.
     * @param target The land tile it was ordered onto.
     * @return The carrier, or null if the active unit is none.
     */
    static Unit landingCarrier(Unit active, Tile target) {
        if (active == null || target == null || !active.isCarrier()
            || !active.hasTile() || active.getUnitCount() == 0
            || !active.getTile().isAdjacent(target)) return null;
        return active;
    }

    /**
     * The passenger a landing sends ashore: the one aboard longest that
     * can go onto the target, FreeCol's first disembarkable unit
     * (the carrier's list grows at the end on boarding).  The original
     * lands the longest aboard whatever its kind (clip007 landing 2: the
     * soldier, with the pioneer aboard); one without moves left cannot go
     * (I: in every landing seen the longest aboard had moves).
     *
     * @param carrier The ship, or null.
     * @param target The land tile.
     * @return The passenger, or null.
     */
    static Unit firstLander(Unit carrier, Tile target) {
        if (carrier == null || target == null) return null;
        for (Unit u : carrier.getUnitList()) {
            if (u.getMoveType(target).isProgress()) return u;
        }
        return null;
    }

    /**
     * The landing question, in the original's advisor box (build spec
     * W8b): GAME.TXT {@code @LANDFALL} with the frontiersman, the bar on
     * "Bei den Schiffen bleiben" ({@code @default=1}; landfall #11157,
     * clip007 #2603, #5400, #6446).  Escape and a click beside the box
     * stay too.
     *
     * <ul>
     *   <li>From the order to the next unit the panel keeps its block as
     *   it was before the box ({@link ClassicInfoPanel#holdBlock}): FreeCol
     *   has already woken the passengers that can go ashore, and the
     *   original's panel still shows them "Wache" (clip007 #3040).</li>
     *   <li>"An Land gehen": exactly one passenger goes ashore, the one
     *   FreeCol's seam names first, which is the one aboard longest
     *   ({@link #firstLander}); FreeCol's own move sends it, in the ship's
     *   direction, {@link ClassicMapViewer#LANDING_SLIDE_MS} after the
     *   close.  Every other passenger asleep aboard is woken, also one
     *   without moves left (clip007 landing 3, the farmer).  The ship
     *   keeps its moves.  When the controller then re-selects the ship,
     *   the next unit in the cycle after the landed one comes instead
     *   ({@link #landingDone}).</li>
     *   <li>"Bei den Schiffen bleiben": nothing goes ashore; the ship stays
     *   the active unit with its moves.  The passengers FreeCol woke stay
     *   awake (I: never seen in a clip; Roger's question F2), and the
     *   panel shows them so.</li>
     * </ul>
     *
     * @param carrier The ship, or null if it is not known (then only the
     *     box is shown and answered).
     * @param lander The passenger that would go ashore, or null.
     * @return True for "An Land gehen".
     */
    boolean askLandfall(Unit carrier, Unit lander) {
        final ClassicText text = ClassicText.load(ClassicPackFiles.runtime());
        final int chosen = onEventThread(() -> {
                this.landing = null;
                if (this.infoPanel != null) this.infoPanel.holdBlock();
                final ClassicAdvisorLayer layer = this.boxLayer;
                final long at = (layer == null || this.mapViewer == null) ? 0L
                    : landfallShowAt(this.mapViewer.moveKeyNanos(), waitClock().now(),
                        layer.loadsPalette(ClassicAdvisorBox.Portrait.SCOUT));
                return this.prompter.ask(landfallRequest(text, at));
            }, ClassicAdvisorBox.Bar.DISMISSED);
        final boolean land = chosen == LANDFALL_LAND_ROW && lander != null;
        if (ClassicFrameRecorder.on()) {
            ClassicFrameRecorder.event("landfall", "carrier="
                + ((carrier == null) ? "-" : carrier.getId())
                + " unit=" + ((lander == null) ? "-" : lander.getId())
                + " aboard=" + ((carrier == null) ? "-" : carrier.getUnitList())
                + " chosen=" + chosen + ((land) ? " ashore" : " stay"));
        }
        if (!land) {
            onEventThread(() -> {
                    if (this.infoPanel != null) {
                        this.infoPanel.releaseBlock();
                        this.infoPanel.repaint();
                    }
                    return null;
                }, null);
            return false;
        }
        if (carrier != null) {
            wakePassengers(carrier, lander);
            final long close = boxClosed();
            onEventThread(() -> {
                    this.landing = new Landing(carrier, lander);
                    if (this.mapViewer != null) {
                        this.mapViewer.landingSlide(lander, close
                            + Math.round(ClassicMapViewer.LANDING_SLIDE_MS * 1e6));
                    }
                    return null;
                }, null);
        }
        return true;
    }

    /**
     * When the last box closed (the landing box, the destination list), on
     * the slide clock: the box layer's close, else now (the stopgap
     * window).
     *
     * @return The close.
     */
    private long boxClosed() {
        final long now = waitClock().now();
        final ClassicAdvisorLayer layer = this.boxLayer;
        final long close = (layer == null) ? Long.MIN_VALUE : layer.lastCloseNanos();
        return (close != Long.MIN_VALUE && close <= now
                && now - close < 1_000_000_000L) ? close : now;
    }

    /**
     * "An Land gehen" wakes every other passenger asleep aboard ("Wache"
     * becomes "Keine Befehle"), also one without moves left (clip007
     * landing 3; FreeCol wakes only those that can go ashore, before the
     * question).
     *
     * @param carrier The ship.
     * @param lander The passenger going ashore.
     */
    private void wakePassengers(Unit carrier, Unit lander) {
        for (Unit u : new ArrayList<>(carrier.getUnitList())) {
            if (u != lander && u.getState() == Unit.UnitState.SENTRY) wake(u);
        }
    }

    /**
     * Wake a passenger: the controller's state change, on the server.
     *
     * @param unit The passenger.
     */
    void wake(Unit unit) {
        final FreeColClient fcc = getFreeColClient();
        if (fcc != null) {
            fcc.getInGameController().changeState(unit, Unit.UnitState.ACTIVE);
        }
    }

    /**
     * The landing box: GAME.TXT {@code @LANDFALL} from the pack, its rows
     * "Bei den Schiffen bleiben" (the bar's, Escape's) and "An Land gehen",
     * the frontiersman at the box; else FreeCol's question with its
     * "Abbrechen" and "OK" in the same order.
     *
     * @param t The original texts, or null.
     * @param showAtNanos When the box should be on screen
     *     ({@link #landfallShowAt}), 0 for at once.
     * @return The box.
     */
    static ClassicAdvisorBox.Request landfallRequest(ClassicText t,
                                                     long showAtNanos) {
        final ClassicText.Message m = (t == null) ? null
            : t.message(LANDFALL_SECTION);
        final ClassicAdvisorBox.Builder b = (m == null || m.text.isEmpty()
            || m.options.size() < 2) ? null
            : ClassicAdvisorBox.fromGameText(LANDFALL_SECTION, m, new HashMap<>());
        if (b != null) {
            return b.defaultRow(ClassicHud.clamp(ClassicAdvisorBox.defaultRow(m), 0, 1))
                .cancelRow(0).portrait(ClassicAdvisorBox.Portrait.SCOUT)
                .showAt(showAtNanos).stopgap(colony(null), null).build();
        }
        return ClassicAdvisorBox.Request.builder(LANDFALL_SECTION)
            .freeColText(Messages.message(DISEMBARK_QUESTION))
            .rows(ClassicAdvisorBox.literal(Messages.message("cancel")),
                  ClassicAdvisorBox.literal(Messages.message("ok")))
            .defaultRow(0).cancelRow(0)
            .portrait(ClassicAdvisorBox.Portrait.SCOUT)
            .showAt(showAtNanos).stopgap(colony(null), null).build();
    }

    /**
     * From the move key to the landing box's display when the
     * frontiersman's palette goes in first: the clip shows the box 6
     * frames after the key (landfall #11151 -&gt; #11154 -&gt; #11157,
     * clip007 #2597 -&gt; #2600 -&gt; #2603: 86 ms frame to frame); a paint
     * shows in the frame after it, so the display comes half a frame and
     * the paint's time (about 6 ms) earlier.
     */
    static final double LANDFALL_OPEN_PALETTE_MS = 72.0;

    /**
     * The same without a palette load: 7 frames (clip007 #5393 -&gt;
     * #5400: 100 ms frame to frame).
     */
    static final double LANDFALL_OPEN_MS = 86.0;

    /**
     * When the landing box should be on screen: the original's time after
     * the move key ({@link #LANDFALL_OPEN_PALETTE_MS},
     * {@link #LANDFALL_OPEN_MS}).  The controller asks only after FreeCol
     * has woken the passengers that can go ashore, 10-50 ms after the key;
     * the box layer loads the palette before it
     * ({@link ClassicAdvisorBox.Request#showAtNanos}).
     *
     * @param keyNanos When the key was taken (0: unknown, at once).
     * @param now Now, on the same clock.
     * @param palette The portrait's palette goes in first.
     * @return The time, or 0 for at once.
     */
    static long landfallShowAt(long keyNanos, long now, boolean palette) {
        if (keyNanos == 0L || now - keyNanos > 1_000_000_000L) return 0L;
        return keyNanos + Math.round(((palette) ? LANDFALL_OPEN_PALETTE_MS
                : LANDFALL_OPEN_MS) * 1e6);
    }

    /**
     * The controller re-selects the ship after a landing's move
     * ({@code moveDirection}'s redisplay): the landed unit becomes the
     * unit that has just made its last move, and the next unit comes as a
     * hand-over from it, 500 ms after its final draw (build spec W5e): the
     * next one in the unit cycle after it ({@link ClassicUnitCycle}, the
     * turn flow's hand-over), not the ship (clip007 #3107, #5804, #6643;
     * landfall #13118), and after the last passenger the start ship
     * (clip007 #3697).  A landing whose unit did not go ashore leaves the
     * ship selected.
     *
     * @param unit The unit the controller chose.
     * @return True if it was the landing's ship and the hand-over is on
     *     its way.
     */
    private boolean landingDone(Unit unit) {
        final Landing l = this.landing;
        if (l == null) return false;
        this.landing = null;
        if (this.mapViewer != null) this.mapViewer.landingSlide(null, 0L);
        if (unit != l.carrier || l.unit.isDisposed() || !l.unit.hasTile()
            || l.unit.getLocation() == l.carrier) {
            ClassicFrameRecorder.event("handover", "landing " + l.unit.getId()
                + " not ashore: the ship stays");
            if (this.infoPanel != null) this.infoPanel.releaseBlock();
            return false;
        }
        // The controller's moveUnit asks for the next unit right after
        // this (its updateGUI: the active unit is now the landed one,
        // which cannot move), and the turn flow's hand-over takes the unit
        // cycle's choice after it; asking here too took two units.
        this.mapViewer.finishedUnit(l.unit);
        ClassicFrameRecorder.event("handover", "after landing " + l.unit.getId()
            + " from " + l.carrier.getId() + ": the cycle after it");
        return true;
    }

    /**
     * A movement key orders {@code unit} aboard a carrier (spec delta
     * W18): when the controller then asks for the next unit (inside its
     * {@code moveUnit}), the carrier comes, if it can still move
     * ({@link #carrierAfterBoarding}).
     *
     * @param unit The unit boarding, or null once the move is over.
     */
    void unitBoarding(Unit unit) {
        this.boardedUnit = unit;
    }

    /**
     * The carrier that comes next after a boarding: the unit's carrier,
     * if it can still move, instead of the unit the controller chose,
     * which is put back to come next in its cycle (clip007 #4281, #6188,
     * #6989: the ship, 128 ms after the boarding's final draw).
     *
     * @param unit The unit the controller chose.
     * @param previous The map's active unit.
     * @return The carrier, or null for no boarding hand-over.
     */
    private Unit carrierAfterBoarding(Unit unit, Unit previous) {
        final Unit boarded = this.boardedUnit;
        if (boarded == null || unit == boarded) return null;   // its re-selection
        this.boardedUnit = null;
        final Unit carrier = boarded.getCarrier();
        if (previous != boarded || carrier == null
            || carrier.getOwner() != boarded.getOwner()
            || !carrier.isCandidateForNextActiveUnit()) return null;
        if (unit != null && unit != carrier) {
            final Player p = boarded.getOwner();
            if (p != null) p.putBackActiveUnit(unit);
        }
        return carrier;
    }

    /**
     * The selection list of {@link #modalChoiceDialog}, shared with the
     * Europe screen's recruit/train/buy lists
     * ({@code ClassicEuropePanel.choose}) so that no copy of the plain
     * {@code JOptionPane.showInputDialog} stopgap -- a decorated Windows
     * dialog over the borderless screens -- is left.  EDT only; modal.
     *
     * @param owner The window the list belongs over (decides the mode and
     *     the placement, see {@link #prepareChildWindow}).
     * @param title The window title (shown only while windowed).
     * @param message The prompt.
     * @param icon An optional icon, or null.
     * @param options The items; the first is preselected.
     * @return The chosen item, or null on Cancel, Escape or close.
     */
    static Object chooseFromList(Window owner, String title, Object message,
                                 Icon icon, Object[] options) {
        final JOptionPane pane = new JOptionPane(message,
            JOptionPane.QUESTION_MESSAGE, JOptionPane.OK_CANCEL_OPTION, icon);
        pane.setWantsInput(true);
        pane.setSelectionValues(options);
        pane.setInitialSelectionValue(options[0]);
        final JDialog d = new JDialog(owner, title,
            Dialog.ModalityType.APPLICATION_MODAL);
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        d.setResizable(false);
        d.setContentPane(pane);
        if (isBorderless(owner)) {
            pane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ClassicDialog.BORDER_HI, 3),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        }
        // Any answer (OK, Cancel, Escape) sets the pane's value: close.
        pane.addPropertyChangeListener(JOptionPane.VALUE_PROPERTY,
            e -> d.dispose());
        prepareChildWindow(d, owner, false);
        SwingUtilities.invokeLater(pane::selectInitialValue);
        ClassicDialog.popupOpened();
        try {
            d.setVisible(true);   // blocks until disposed
        } finally {
            ClassicDialog.popupClosed();
        }
        final Object sel = pane.getInputValue();
        return (sel == JOptionPane.UNINITIALIZED_VALUE) ? null : sel;
    }

    /** Title for a tile-anchored dialog: the settlement there, else the game name. */
    private static String colony(Tile tile) {
        final Colony c = (tile == null) ? null : tile.getColony();
        return (c == null) ? "FreeCol" : c.getName();
    }

    // The woodcuts (master plan W9, N17; ClassicWoodcut)

    /**
     * What shows the woodcuts: {@link #putWoodcut} in the game, a fake in
     * the tests.  EDT only.
     */
    interface Woodcutter {

        /**
         * Show woodcut {@code k} and wait until the map is back.
         *
         * @param k The entry ({@link ClassicWoodcut}).
         * @param notBefore Its black comes no earlier (clock ns; 0: at once).
         * @param followMs The next box comes no earlier than this after the
         *     map's return.
         * @return When the map came back ({@link #waitClock}), or
         *     {@link ClassicAdvisorLayer#NOT_SHOWN} when it could not be
         *     shown (then it is not marked, and comes at the next trigger).
         */
        long show(int k, long notBefore, double followMs);
    }

    /** Shows the woodcuts; replaced by the tests. */
    Woodcutter woodcutter = this::putWoodcut;

    /**
     * The woodcuts of this game shown or counted as shown (bit k): the
     * save's record, the ones derived at the view's build
     * ({@link ClassicWoodcut#derived}) and this session's.  EDT only.
     */
    private int woodcuts = 0;

    /** The woodcuts asked for and not over yet (a second trigger waits out). */
    private int woodcutsAsked = 0;

    /** Woodcuts posted and not yet queued: the turn flow waits for them. */
    private int woodcutsPosted = 0;

    /** The woodcuts' art of the pack, loaded on first use. */
    private ClassicWoodcut.Art woodcutArt = null;
    private ClassicPackFiles woodcutPack = null;

    /** The tile of the colony being founded ({@link #noteFounding}), or null. */
    private Tile foundingTile = null;

    /**
     * Read the woodcuts already seen in this game: the save's
     * {@code classicWoodcuts} and, for a save without it (an older build,
     * the standard GUI), the ones whose event left a trace.  EDT only;
     * once per game view, before anything can trigger one.
     */
    private void loadWoodcuts() {
        final Player me = myPlayer();
        final Game game = getGame();
        final int saved = (me == null) ? 0 : me.getClassicWoodcuts();
        final int derived = ClassicWoodcut.derived(me,
            (game == null) ? null : game.getMap());
        this.woodcuts = saved | derived;
        this.woodcutsAsked = 0;
        this.woodcutsPosted = 0;
        this.foundingTile = null;
        ClassicFrameRecorder.note("woodcuts", "saved=" + Integer.toBinaryString(saved)
            + " derived=" + Integer.toBinaryString(derived));
    }

    /**
     * @param k A woodcut.
     * @return Whether it was shown in this game, counts as shown, or is
     *     asked for right now.
     */
    boolean woodcutShown(int k) {
        return ((this.woodcuts | this.woodcutsAsked) & ClassicWoodcut.bit(k)) != 0;
    }

    /** @return The woodcuts shown or counted as shown in this game (tests). */
    int woodcutsShown() {
        return this.woodcuts;
    }

    /**
     * Whether a woodcut covers the screen now: the map's index hint is
     * not the screen then.  EDT only.
     *
     * @return True from a woodcut's black to its map's return.
     */
    boolean woodcutCovers() {
        return this.boxLayer != null && this.boxLayer.coversScreen();
    }

    /**
     * Show woodcut {@code k} once per game: nothing if it was shown, else
     * the woodcutter's, and it is marked once it was on the screen.  EDT
     * only.
     *
     * @param k The woodcut.
     * @param notBefore Its black no earlier (clock ns; 0: at once).
     * @param followMs The next box's least distance from the map's return.
     * @return When the map came back, or {@link ClassicAdvisorLayer#NOT_SHOWN}.
     */
    long woodcut(int k, long notBefore, double followMs) {
        if (woodcutShown(k)) return ClassicAdvisorLayer.NOT_SHOWN;
        final int bit = ClassicWoodcut.bit(k);
        this.woodcutsAsked |= bit;
        long back = ClassicAdvisorLayer.NOT_SHOWN;
        try {
            back = this.woodcutter.show(k, notBefore, followMs);
        } finally {
            this.woodcutsAsked &= ~bit;
        }
        if (back != ClassicAdvisorLayer.NOT_SHOWN) {
            if ((this.woodcuts & bit) == 0) markWoodcut(k);
            repaintInfo();
        }
        return back;
    }

    /**
     * Mark a woodcut as shown: in this session, on our player and, in a
     * single player game, on the server's copy of our player, whose state
     * every save writes (autosaves included), so it is not shown again
     * after a reload.  Multiplayer: this session only.
     *
     * @param k The woodcut.
     */
    void markWoodcut(int k) {
        final int bit = ClassicWoodcut.bit(k);
        this.woodcuts |= bit;
        final Player me = myPlayer();
        if (me == null) return;
        me.setClassicWoodcuts(me.getClassicWoodcuts() | bit);
        final FreeColClient fcc = getFreeColClient();
        final FreeColServer server = (fcc == null) ? null : fcc.getFreeColServer();
        final Game sg = (server == null) ? null : server.getGame();
        final Player sp = (sg == null) ? null
            : sg.getFreeColGameObject(me.getId(), Player.class);
        if (sp != null) sp.setClassicWoodcuts(sp.getClassicWoodcuts() | bit);
        ClassicFrameRecorder.event("woodcut-marked", k + " client="
            + Integer.toBinaryString(me.getClassicWoodcuts()) + " server="
            + ((sp == null) ? "-" : Integer.toBinaryString(sp.getClassicWoodcuts())));
    }

    /**
     * Put a woodcut on the game's canvas ({@link ClassicAdvisorLayer}) when
     * the map is what the player is looking at, as {@link #putBox} does;
     * over a colony, Europe or report screen, the first scene, or without
     * the pack: not shown.  EDT only.
     *
     * @param k The woodcut.
     * @param notBefore Its black no earlier (clock ns).
     * @param followMs The next box's least distance from the map's return.
     * @return When the map came back, or {@link ClassicAdvisorLayer#NOT_SHOWN}.
     */
    long putWoodcut(int k, long notBefore, double followMs) {
        final ClassicAdvisorLayer layer = this.boxLayer;
        if (layer == null || this.sceneShowing || this.hudPane == null
            || !this.hudPane.isShowing() || dialogOwner() != this.frame) {
            ClassicFrameRecorder.event("woodcut-skipped", k + " no game canvas");
            return ClassicAdvisorLayer.NOT_SHOWN;
        }
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        if (pack != this.woodcutPack || this.woodcutArt == null) {
            this.woodcutPack = pack;
            this.woodcutArt = ClassicWoodcut.load(pack);
        }
        final ClassicWoodcut.Screen s = ClassicWoodcut.Screen.of(this.woodcutArt, k);
        if (s == null) {
            ClassicFrameRecorder.event("woodcut-skipped", k + " no art");
            return ClassicAdvisorLayer.NOT_SHOWN;
        }
        // A slide's final draw still due comes first, the black 57 ms
        // after it (a trigger the server sends during the move: the
        // Pacific's region, a first contact).
        long due = notBefore;
        final ClassicMapViewer mv = this.mapViewer;
        if (mv != null) {
            final long last = mv.lastFinalNanos();
            mv.finalDraw();
            if (mv.lastFinalNanos() != last) due = Math.max(due, afterFinalDraw());
        }
        if (ClassicFrameRecorder.on()) {
            ClassicFrameRecorder.event("woodcut-ask", k + ((due == 0L) ? " black at once"
                : " black in " + String.format(java.util.Locale.ROOT, "%.1fms",
                                               (due - waitClock().now()) / 1e6)));
        }
        return layer.showWoodcut(s, ClassicWoodcut.palette(pack, this.woodcutArt, k),
                                 due, followMs);
    }

    /**
     * A woodcut's black after the last final draw: 57 ms later (V:
     * landfall #2107 -&gt; #2111, #11600 -&gt; #11604), at once if that is
     * past.
     *
     * @return The time on the clock, 0 for at once.
     */
    private long afterFinalDraw() {
        final ClassicMapViewer mv = this.mapViewer;
        final long f = (mv == null) ? 0L : mv.lastFinalNanos();
        return (f == 0L) ? 0L
            : f + Math.round(ClassicWoodcut.BLACK_AFTER_TRIGGER_MS * 1e6);
    }

    /**
     * Woodcut 1, the discovery of the New World: the first final draw that
     * shows land as explored (V: landfall #2107, fog-start #1042; the land
     * in the fog ring before it shows nothing).  Called from the map's
     * paint, so the woodcut is posted, never shown inside the paint; it
     * counts as due at once, so the turn flow cannot hand over meanwhile.
     * EDT only.
     *
     * @param finalNanos When the final draw was painted.
     */
    void landSighted(long finalNanos) {
        if (woodcutShown(ClassicWoodcut.DISCOVERY)) return;
        final int bit = ClassicWoodcut.bit(ClassicWoodcut.DISCOVERY);
        this.woodcutsAsked |= bit;
        this.woodcutsPosted++;
        SwingUtilities.invokeLater(() -> {
                this.woodcutsPosted = Math.max(0, this.woodcutsPosted - 1);
                this.woodcutsAsked &= ~bit;
                final long back = woodcut(ClassicWoodcut.DISCOVERY, finalNanos
                    + Math.round(ClassicWoodcut.BLACK_AFTER_TRIGGER_MS * 1e6),
                    ClassicWoodcut.FOLLOW_DISCOVERY_MS);
                if (back != ClassicAdvisorLayer.NOT_SHOWN) discoveryShown(back);
            });
    }

    /** @return Whether a woodcut is posted and not yet queued. */
    boolean woodcutPosted() {
        return this.woodcutsPosted > 0;
    }

    /**
     * The seam of the New World's name (master plan W10): called after
     * woodcut 1's map is back; the original asks @LANDHO 71 ms later
     * ({@link ClassicWoodcut#FOLLOW_DISCOVERY_MS}).  Nothing yet.
     *
     * @param mapBackNanos When the map came back ({@link #waitClock}).
     */
    void discoveryShown(long mapBackNanos) {
        // W10 (Part H): @LANDHO here.
    }

    /**
     * Woodcut 7 on the key that enters a native village for the first time,
     * before the move (V: landfall #15424, no slide).  EDT only.
     *
     * @param unit The unit ordered.
     * @param direction The direction.
     * @return False if the game view went meanwhile (the move is dropped).
     */
    boolean villageEntryKey(Unit unit, Direction direction) {
        if (unit == null || direction == null || !unit.hasTile()
            || woodcutShown(ClassicWoodcut.VILLAGE)) return true;
        final Tile target = unit.getTile().getNeighbourOrNull(direction);
        if (!ClassicWoodcut.entersVillage(unit.getMoveType(direction), target)) {
            return true;
        }
        woodcut(ClassicWoodcut.VILLAGE, 0L, ClassicWoodcut.FOLLOW_VILLAGE_MS);
        return this.mapViewer != null;
    }

    /**
     * Woodcut 7 before a village box that no key brought (a goto, a click):
     * at once, if not shown yet.
     *
     * @param settlement The settlement, or null.
     */
    void villageWoodcut(net.sf.freecol.common.model.Settlement settlement) {
        if (settlement instanceof IndianSettlement) villageWoodcut();
    }

    /** {@link #villageWoodcut(net.sf.freecol.common.model.Settlement)} for a known village. */
    private void villageWoodcut() {
        if (woodcutShown(ClassicWoodcut.VILLAGE)) return;
        onEventThread(() -> woodcut(ClassicWoodcut.VILLAGE, 0L,
                                    ClassicWoodcut.FOLLOW_VILLAGE_MS),
                      ClassicAdvisorLayer.NOT_SHOWN);
    }

    /**
     * Remember the colony being founded: its colony screen comes after
     * woodcut 2 if it is the first (D4's name prompt calls this too).
     *
     * @param tile The colony's tile.
     */
    void noteFounding(Tile tile) {
        this.foundingTile = tile;
    }

    /**
     * Woodcut 2 before the first colony's screen, once the map shows the
     * colony (V: clip008 #3369 the colony on the map, #3374 black, #4009
     * the map, #4032 the colony screen).  EDT only.
     *
     * @param colony The colony whose screen comes.
     * @return False if the game view went meanwhile.
     */
    private boolean foundingWoodcut(Colony colony) {
        final Tile t = this.foundingTile;
        if (t == null || colony.getTile() != t) return true;
        this.foundingTile = null;
        if (woodcutShown(ClassicWoodcut.COLONY)) return true;
        // The map with the new colony first, the black 72 ms after it.
        if (this.hudPane != null) {
            this.hudPane.paintImmediately(0, 0, this.hudPane.getWidth(),
                                          this.hudPane.getHeight());
        }
        final long back = woodcut(ClassicWoodcut.COLONY, waitClock().now()
            + Math.round(ClassicWoodcut.BLACK_AFTER_COLONY_MS * 1e6),
            ClassicWoodcut.FOLLOW_COLONY_MS);
        if (back == ClassicAdvisorLayer.NOT_SHOWN) return this.mapViewer != null;
        try {
            waitClock().waitUntil(back + Math.round(ClassicWoodcut.FOLLOW_COLONY_MS * 1e6));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        return this.mapViewer != null;
    }

    /**
     * The woodcut of a first meeting with a native nation (3, or 4 and 5
     * for the Aztecs and the Incas, which count as 3 too), before its box.
     * EDT only.
     *
     * @param other The native nation.
     */
    private void contactWoodcut(Player other) {
        final int k = ClassicWoodcut.contactWoodcut((other == null
            || other.getNation() == null) ? null : other.getNation().getSuffix());
        // While the Aztecs' or the Incas' woodcut is up, another tribe met
        // meanwhile brings no woodcut 3 after it.
        final int natives = ClassicWoodcut.bit(ClassicWoodcut.NATIVES);
        final boolean own = k != ClassicWoodcut.NATIVES && !woodcutShown(ClassicWoodcut.NATIVES);
        if (own) this.woodcutsAsked |= natives;
        long back = ClassicAdvisorLayer.NOT_SHOWN;
        try {
            back = woodcut(k, afterFinalDraw(), ClassicWoodcut.FOLLOW_NATIVES_MS);
        } finally {
            if (own) this.woodcutsAsked &= ~natives;
        }
        if (back != ClassicAdvisorLayer.NOT_SHOWN && k != ClassicWoodcut.NATIVES
            && (this.woodcuts & natives) == 0) {
            markWoodcut(ClassicWoodcut.NATIVES);
        }
    }

    /**
     * The woodcut a notice brings ({@link ClassicWoodcut#messageWoodcut}).
     *
     * @param game The game.
     * @param m The message.
     * @return The woodcut, or -1.
     */
    static int messageWoodcut(Game game, ModelMessage m) {
        if (m == null) return -1;
        final FreeColObject display = (game == null) ? null : game.getMessageDisplay(m);
        final boolean laden = display instanceof Unit && ((Unit) display).isNaval()
            && ((Unit) display).hasGoodsCargo();
        return ClassicWoodcut.messageWoodcut(m.getId(), laden);
    }

    /**
     * A notice's woodcut, before its box (N17), if not shown yet.  EDT only.
     *
     * @param k The woodcut, or -1.
     */
    private void noticeWoodcut(int k) {
        if (k < 0 || woodcutShown(k)) return;
        woodcut(k, 0L, (k == ClassicWoodcut.CARGO) ? ClassicWoodcut.FOLLOW_CARGO_MS
                : ClassicAdvisorLayer.CHAIN_MS);
    }

    /**
     * Whether a nation id names a European nation (woodcut 10 at its
     * meeting sound).
     *
     * @param nationId The id.
     * @return True for a European nation of this game's rules.
     */
    private boolean isEuropeanNation(String nationId) {
        final Game game = getGame();
        final Specification spec = (game == null) ? null : game.getSpecification();
        final Nation n = (spec == null || nationId == null) ? null
            : spec.getNation(nationId);
        return n != null && n.getType() != null && n.getType().isEuropean();
    }

    /**
     * {@inheritDoc}
     *
     * <p>FreeCol's sound at a first meeting with a European nation
     * ({@code sound.event.meet.<nation>}, played when the other nation's
     * unit made the contact): woodcut 10 first, once per game (N17, I).
     */
    @Override
    public void playSound(String sound) {
        final String nation = ClassicWoodcut.meetNation(sound);
        if (nation != null && !woodcutShown(ClassicWoodcut.EUROPEANS)
            && isEuropeanNation(nation)) {
            invokeNowOrLater(() -> {
                    woodcut(ClassicWoodcut.EUROPEANS, 0L, ClassicAdvisorLayer.CHAIN_MS);
                    if (getFreeColClient() != null) super.playSound(sound);
                });
            return;
        }
        if (getFreeColClient() != null) super.playSound(sound);
    }

    // UI-task dispatch

    /**
     * {@inheritDoc}
     *
     * The controllers hand the view work that must reach the event dispatch
     * thread through this seam and its {@link #invokeNowOrWait} sibling.  Both
     * are <em>no-ops</em> in the base {@code GUI} (headless has no EDT to reach),
     * so a {@code GUI} subclass that does not override them silently drops every
     * task routed through them — the classic UI did, which is why no in-game
     * message ever appeared: {@code InGameController.displayModelMessages} posts
     * its display task here, and {@code Message.clientGeneric} posts the
     * server-driven message flush.  Mirror {@code SwingGUI}: run inline when
     * already on the EDT, else hand off.
     */
    @Override
    public void invokeNowOrLater(Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            SwingUtilities.invokeLater(runnable);
        }
    }

    /**
     * {@inheritDoc}
     *
     * The waiting variant of {@link #invokeNowOrLater} — see there for why this
     * must be overridden at all.  Callers rely on the task having finished when
     * this returns, so off the EDT this blocks.
     */
    @Override
    public void invokeNowOrWait(Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(runnable);
            } catch (Exception e) {
                logger.log(Level.WARNING, "ClassicGUI: UI task failed.", e);
            }
        }
    }

    /**
     * Run {@code task} on the event dispatch thread and return its result.  The
     * controllers call the dialog methods from whichever thread they happen to be
     * on (a key binding runs on the EDT; a server message does not), and Swing
     * dialogs must not be shown off it.  Any failure yields {@code fallback}.
     */
    private <T> T onEventThread(Callable<T> task, T fallback) {
        try {
            if (SwingUtilities.isEventDispatchThread()) return task.call();
            final FutureTask<T> ft = new FutureTask<>(task);
            SwingUtilities.invokeAndWait(ft);
            return ft.get();
        } catch (Exception e) {
            logger.log(Level.WARNING, "ClassicGUI: dialog failed.", e);
            return fallback;
        }
    }

    // Look and feel

    /**
     * {@inheritDoc}
     *
     * Called once during client startup ({@code FreeColClient} constructor).
     * The base {@code GUI} no-ops this, which leaves {@link FontLibrary}'s main
     * font null, and FreeCol panels the classic UI still reuses (and the old
     * {@code InGameMenuBar}, before the painted strip replaced it) NPE on
     * {@code FontLibrary.getMainFont()}.  So initialise the main font here, and
     * the image-border scale factor so FreeCol's image borders render.
     *
     * <p>We deliberately do <em>not</em> install {@code FreeColLookAndFeel}: it
     * swaps in a {@code PanelUI} that paints the FreeCol parchment texture behind
     * every {@code JPanel}, which would override the classic map's black fog.
     * Any stock {@code JPopupMenu} that still appears gets the wood/green reskin
     * (see {@link #installClassicMenuDropdownDefaults}).  All guarded so a
     * failure just leaves the default look rather than aborting startup.
     */
    @Override
    public void installLookAndFeel(String fontName) throws FreeColException {
        try {
            FreeColImageBorder.setScaleFactor(this.imageLibrary.getScaleFactor());
        } catch (Exception e) {
            logger.log(Level.WARNING, "ClassicGUI: image-border scale setup "
                + "failed.", e);
        }
        FontLibrary.createMainFont(fontName);
        installClassicMenuDropdownDefaults();
    }

    /**
     * Reskin stock Swing popup menus to {@link ClassicDialog}'s wood-framed,
     * green-on-wood palette.  Written for FreeCol's reused
     * {@code InGameMenuBar}; the in-game HUD now paints the original's menus
     * itself ({@link ClassicMenuStrip}), so this only still styles any
     * FreeCol {@code JPopupMenu} that might appear -- harmless, and kept for
     * that.
     *
     * <p>Installed as {@code UIManager} <em>defaults</em>, once, here — before
     * any menu is built — rather than
     * restyling the {@code JMenuItem}s after the fact: every item/popup then picks
     * these up as its own built-in colours at construction, Swing's normal
     * hover/disabled painting comes along for free, and the real
     * {@code FreeColAction}s, accelerators and {@code updateActions()} wiring
     * stay untouched (nothing here replaces the menu structure).
     *
     * <p>{@code FreeColMenuBar.getMenuItem()} (shared with {@code SwingGUI}, so
     * not ours to change) leaves every item {@code setOpaque(false)}. Live-testing
     * (screenshotting each open dropdown) showed what that actually buys: Swing's
     * {@code BasicMenuItemUI.paintBackground} skips its own-background fill for
     * the *idle* state when non-opaque — so idle items show no separate
     * rectangle at all and sit directly on the popup's wood fill underneath — but
     * still unconditionally paints the *armed/hover* fill regardless of opaque
     * (an asymmetry easy to get backwards from reading the source alone, which
     * is why this was checked live rather than left as a guess). So
     * {@code selectionBackground} is not dead weight the way idle
     * {@code background} would be: without overriding it, hovering paints the
     * platform L&F's own default (a jarring blue), which is why it is set below
     * to {@link ClassicDialog}'s {@code BTN_BG} — deliberately its darker plate
     * tone, not {@code BTN_HOT} (identical to {@code WOOD_FALLBACK} and so
     * invisible against the popup's own fill). The popup itself is unaffected by
     * any of this — {@code BasicPopupMenuUI} forces {@code JPopupMenu} opaque
     * regardless — so its wood fill and bevelled border always render.
     *
     * <p>Safe process-wide: {@code --classic} selects
     * {@code ClassicGUI} for the whole process, and only the
     * {@code MenuItem}/{@code CheckBoxMenuItem}/{@code RadioButtonMenuItem}/
     * {@code PopupMenu}/{@code Separator} keys are touched — the still-Swing
     * choice/input dialog stopgaps ({@code JOptionPane}, Phase 3 §1) read
     * different keys, so this cannot bleed into them.
     */
    private void installClassicMenuDropdownDefaults() {
        for (String prefix : new String[]
                { "MenuItem", "CheckBoxMenuItem", "RadioButtonMenuItem" }) {
            UIManager.put(prefix + ".foreground", ClassicDialog.TEXT_FG);
            UIManager.put(prefix + ".selectionForeground", ClassicDialog.BTN_FG);
            UIManager.put(prefix + ".selectionBackground", ClassicDialog.BTN_BG);
            UIManager.put(prefix + ".disabledForeground", ClassicDialog.COUNT_FG);
            UIManager.put(prefix + ".acceleratorForeground", ClassicDialog.TEXT_FG);
            UIManager.put(prefix + ".acceleratorSelectionForeground",
                          ClassicDialog.BTN_FG);
        }
        UIManager.put("PopupMenu.background", ClassicDialog.WOOD_FALLBACK);
        UIManager.put("PopupMenu.border", BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ClassicDialog.BORDER_HI, 2),
            BorderFactory.createLineBorder(ClassicDialog.BORDER_LO, 1)));
        // Both eras of the separator-colour key: which one a JSeparator's UI
        // delegate actually reads varies by JDK version, and the per-instance
        // fallback in styleClassicMenuBar covers whichever this turns out not
        // to be.
        UIManager.put("Separator.foreground", ClassicDialog.BORDER_HI);
        UIManager.put("Separator.background", ClassicDialog.BORDER_LO);
        UIManager.put("PopupMenu.separatorForeground", ClassicDialog.BORDER_HI);
        UIManager.put("PopupMenu.separatorBackground", ClassicDialog.BORDER_LO);
    }

    // Image libraries

    /**
     * {@inheritDoc}
     *
     * Overridden so that actions and (later) panels can resolve images; the base
     * {@code GUI} returns {@code null}, which causes a NullPointerException storm
     * as the actions try to load their order-button icons.
     */
    @Override
    public ImageLibrary getFixedImageLibrary() {
        return this.imageLibrary;
    }

    /**
     * {@inheritDoc}
     *
     * Phase 0 has no separate map scaling, so the scaled library is the same as
     * the fixed one.  Phase 1 (the map) may introduce a distinct scaled library.
     */
    @Override
    public ImageLibrary getScaledImageLibrary() {
        return this.imageLibrary;
    }


    /**
     * {@inheritDoc}
     *
     * Also drops the Alt+Enter dispatcher, so no key toggles a window that
     * is being taken down.
     */
    @Override
    public void quitGUI() {
        final KeyEventDispatcher keys = this.frameKeys;
        this.frameKeys = null;
        if (keys != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .removeKeyEventDispatcher(keys);
        }
        final JFrame f = this.frame;
        if (f != null) {
            SwingUtilities.invokeLater(f::dispose);
        }
    }
}
