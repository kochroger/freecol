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
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.HighScore;
import net.sf.freecol.common.model.IndianNationType;
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
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.resources.ImageCache;
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

    /** The report screen's window, while one is open (see {@link #showReportColonyPanel}). */
    private JFrame reportFrame;

    /**
     * The in-game map view, created lazily when a game starts (see
     * {@link #reconnectGUI}).  Null before then (title-screen placeholder).
     * Owns the classic view state (view mode, focus, selected tile, active
     * unit); this class delegates the corresponding {@code GUI} methods to it.
     */
    private ClassicMapViewer mapViewer;

    /**
     * The right-hand info / orders panel (Phase 2 HUD), created alongside the
     * map viewer in {@link #reconnectGUI}.  Repainted whenever the view state or
     * model changes so it tracks the active unit / selected tile / treasury.
     */
    private ClassicInfoPanel infoPanel;

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
        if (modalDialogShowing()) {
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
                // A fresh Specification per call (FreeCol.java:981-994).
                Specification spec = FreeCol.loadSpecification(
                    FreeCol.getRulesFile(), FreeCol.getAdvantages(),
                    setup.difficultyId);
                if (spec != null && setup.difficultyId != null
                    && !setup.difficultyId.equals(spec.getDifficultyLevel())) {
                    logger.warning("ClassicGUI: difficulty " + setup.difficultyId
                        + " not applied (got " + spec.getDifficultyLevel()
                        + "); falling back to " + FreeCol.getDifficulty());
                    spec = FreeCol.loadSpecification(FreeCol.getRulesFile(),
                        FreeCol.getAdvantages(), FreeCol.getDifficulty());
                }
                if (spec == null) {
                    showMainPanel(Messages.message("classic.mainMenu.startFailed"));
                    return;
                }
                if (setup.playerName != null) {
                    final String name = setup.playerName.trim();
                    if (!isFreePlayerName(spec, name)) {
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
     * and any ruler name of the rules -- those are the AI players' names
     * (ServerPlayer.java:239), and two players with one name break
     * {@code Game.getPlayerByName} (an exact {@code equals}, first match)
     * and with it loading the save.
     *
     * @param spec The game's rules.
     * @param name The trimmed name.
     * @return True if the name is free.
     */
    static boolean isFreePlayerName(Specification spec, String name) {
        if (name == null || name.isEmpty() || "mapEditor".equals(name)) return false;
        for (Nation n : spec.getNations()) {
            if (name.equals(Messages.message(n.getRulerNameKey()))) return false;
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
        closeColonyPanel();
        closeBuildQueuePanel();
        closeEuropePanel();
        if (this.reportFrame != null && !(this.reportFrame.getContentPane()
                instanceof ClassicReportHighScoresPanel)) {
            closeReportPanel();
        }
        if (this.mapViewer != null) this.mapViewer.dispose();
        if (this.menuStrip != null) this.menuStrip.closeMenu();
        if (this.frame != null) this.frame.setJMenuBar(null);
        this.mapViewer = null;
        this.infoPanel = null;
        this.menuStrip = null;
        this.hudPane = null;
        // No first scene and no held notices survive the game view.
        this.firstScenePending = false;
        this.heldMessages.clear();
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
            if (this.mapViewer == null) {
                this.mapViewer = new ClassicMapViewer(getFreeColClient(),
                                                      this, this.imageLibrary);
                installInGameHud();
            }
            if (active != null) {
                this.mapViewer.changeToMoveUnits(active);
            } else if (tile != null) {
                this.mapViewer.changeToTerrain(tile);
            }
            // Prefer an active unit for the initial focus: the original
            // always opens looking at the piece that is up. Checked against
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
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final ClassicText text = ClassicText.load(pack);
        final ClassicFont tiny = (pack == null) ? null : pack.font(ClassicFont.TINY);
        final BufferedImage wood = (pack == null) ? null
            : pack.image(ClassicMenuBar.WOOD_KEY);
        this.infoPanel = new ClassicInfoPanel(getFreeColClient(), this.mapViewer,
            this.imageLibrary, text, tiny, wood);
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
                    return (am == null || id == null) ? null
                        : am.getFreeColAction(id);
                }

                @Override
                public boolean inputBlocked() {
                    return sceneShowing;
                }

                @Override
                public void unavailable(ClassicMenuModel.Item item) {
                    // An inert row (normal ink as in the original, but no
                    // engine equivalent or no classic screen yet): say so,
                    // rather than do nothing.
                    showInformationNotice(Messages.message(
                        "classic.mainMenu.notYet"));
                }
            }, tiny, wood, text);
        this.hudPane = new ClassicHudPane(this.menuStrip, this.mapViewer,
            this.infoPanel, arrowSprite(pack), () -> sceneShowing);
        ClassicKeyMap.install(this.hudPane, this.mapViewer,
            id -> (am == null) ? null : am.getFreeColAction(id),
            () -> (mapViewer == null) ? null : mapViewer.getViewMode(),
            () -> (mapViewer == null) ? ClassicMenuModel.Context.NONE
                : ClassicMenuModel.Context.of(mapViewer.getActiveUnit(),
                                              mapViewer.getViewMode()),
            () -> sceneShowing || (menuStrip != null && menuStrip.isMenuOpen()));
        this.frame.setJMenuBar(null);
        this.frame.setContentPane(this.hudPane);
        this.frame.revalidate();
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
     * A one-page classic notice (the shared {@link ClassicDialog}), e.g. for
     * a menu row whose feature follows later.  EDT only.
     */
    private void showInformationNotice(String text) {
        ClassicDialog.showMessages(dialogOwner(),
            Messages.message("classic.dialog.messages"),
            List.of(new ClassicDialog.Page(text, null)));
    }

    // View mode / focus — delegated to the map viewer.

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

    /** {@inheritDoc} */
    @Override
    public void changeView(Tile tile) {
        if (this.mapViewer != null) this.mapViewer.changeToTerrain(tile);
        repaintInfo();
        updateActions();
    }

    /** {@inheritDoc} */
    @Override
    public void changeView(Unit unit, boolean force) {
        if (this.mapViewer != null) this.mapViewer.changeToMoveUnits(unit);
        repaintInfo();
        updateActions();
    }

    /** {@inheritDoc} */
    @Override
    public void changeView() {
        if (this.mapViewer != null) this.mapViewer.changeToEndTurn();
        repaintInfo();
        updateActions();
    }

    /**
     * {@inheritDoc}
     *
     * <p>The base seam no-ops, which made every move an abrupt teleport; the
     * classic viewer slides the sprite tile-to-tile like the original game.
     */
    @Override
    public void animateUnitMove(Unit unit, Tile srcTile, Tile dstTile) {
        if (this.mapViewer != null) {
            this.mapViewer.animateMove(unit, srcTile, dstTile);
        }
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
        SwingUtilities.invokeLater(() -> {
            try {
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
     * {@code Europe} menu action (accelerator {@code E}) and automatically when a
     * ship arrives in Europe (the controller calls this).
     *
     * <p>Only one Europe screen is open at a time; opening another replaces it.
     * Guarded so a failure degrades to a log line rather than breaking the map.
     */
    @Override
    public FreeColPanel showEuropePanel() {
        final Player player = getMyPlayer();
        if (player == null || player.getEurope() == null) return null;
        SwingUtilities.invokeLater(() -> {
            try {
                closeEuropePanel();
                final ClassicEuropePanel panel = new ClassicEuropePanel(
                    getFreeColClient(), this.imageLibrary, player.getEurope(),
                    this::closeEuropePanel);
                final JFrame f = new JFrame(Messages.message(player.getEurope()
                        .getNameKey()));
                this.europeFrame = f;
                this.europePanel = panel;
                f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                f.setBackground(Color.BLACK);
                f.setContentPane(panel);
                prepareChildWindow(f, this.frame, true);
                f.setVisible(true);
                panel.requestFocusInWindow();
            } catch (Exception e) {
                logger.log(Level.WARNING, "ClassicGUI: could not show Europe "
                    + "screen", e);
            }
        });
        return null;
    }

    /** Dismiss the Europe screen if one is open. */
    private void closeEuropePanel() {
        final JFrame f = this.europeFrame;
        this.europeFrame = null;
        this.europePanel = null;
        if (f != null) f.dispose();
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
        showMessagePopup(messages, "classic.dialog.turnMessages");
        return null;
    }

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
     * </ul>
     */
    private void showMessagePopup(List<ModelMessage> messages, String titleKey) {
        messages = withoutStartMessage(messages);
        if (messages.isEmpty()) return;
        if (this.firstScenePending || this.sceneShowing) {
            this.heldMessages.add(new HeldMessages(messages, titleKey));
            logger.info("ClassicGUI: " + messages.size()
                + " notice(s) held until the first scene is dismissed.");
            return;
        }
        final Game game = getGame();
        if (game == null) return;
        final List<ClassicDialog.Page> pages = new ArrayList<>();
        for (ModelMessage m : messages) {
            final ImageIcon icon = this.imageLibrary
                .getObjectImageIcon(game.getMessageDisplay(m));
            pages.add(new ClassicDialog.Page(Messages.message(m),
                    (icon == null) ? null : icon.getImage()));
        }
        onEventThread(() -> {
                ClassicDialog.showMessages(dialogOwner(),
                    Messages.message(titleKey), pages);
                return null;
            }, null);
    }

    // First game scene (ClassicFirstScene)

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
        return this.sceneShowing || super.isDialogShowing();
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
        if (this.hudOverlay != null) this.hudOverlay.hideScene();
        updateActions();
        if (this.mapViewer != null) {
            this.mapViewer.requestFocusInWindow();
            this.mapViewer.repaint();
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
                    ClassicDialog.showMessages(dialogOwner(),
                        Messages.message("classic.dialog.error"),
                        List.of(new ClassicDialog.Page(text, null)));
                } finally {
                    if (callback != null) callback.run();
                }
                return null;
            }, null);
        return null;
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
     * portrait.
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
        askEvent(ImageLibrary.getMonarchImage(monarchKey),
                 Messages.message(hdrKey), msg, yesKey, noKey, handler);
    }

    /**
     * {@inheritDoc}
     *
     * Meeting a native nation for the first time.  The handler carries the
     * player's response back to {@code firstContact}, so — like the monarch and
     * demand dialogs — a no-op dropped the exchange.  Mirrors
     * {@code FirstContactDialog}: the welcome text (offer variant when a
     * {@code tile} is on the table), a per-nation meeting header, over the
     * meeting illustration.
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
        askEvent(ImageLibrary.getMeetingImage(other), Messages.message(hdrKey),
                 msg, "yes", "no", handler);
    }

    /**
     * {@inheritDoc}
     *
     * A native unit demanding tribute (gold / food / other goods) from a
     * colony.  The handler sends accept/reject to {@code indianDemand}, so a
     * no-op left the demand unanswered.  Mirrors {@code NativeDemandDialog}:
     * the demand text and yes/no labels vary by what is demanded, over the
     * colony's settlement sprite.
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
        askEvent(this.imageLibrary.getSmallSettlementImage(colony),
                 Messages.message(title), msg, yes, no, handler);
    }

    /**
     * Shared body of the event-confirm dialogs above: show {@code message} (with
     * {@code icon}) in the classic popup with a Yes/No pair (or a lone No/close
     * plate when {@code yesKey} is null, for acknowledge-only notices), and hand
     * the choice to {@code handler} as a {@code Boolean}.
     *
     * <p>These {@code GUI} seams are asynchronous ({@link DialogHandler}), but
     * the shared {@link ClassicDialog#ask} is modal-blocking — which is right
     * for a demand that must be answered.  The controllers already post them via
     * {@code invokeLater}, so blocking the classic popup on the EDT (which pumps
     * events) is fine; the handler fires with the result the instant it closes.
     * The handler runs in a {@code finally} so the server exchange still resolves
     * (as a reject) if the popup throws, rather than dangling.
     */
    private void askEvent(java.awt.Image icon, String title,
                          StringTemplate message, String yesKey, String noKey,
                          DialogHandler<Boolean> handler) {
        final String[] options = (yesKey == null)
            ? new String[] { Messages.message(noKey) }
            : new String[] { Messages.message(yesKey), Messages.message(noKey) };
        final ClassicDialog.Page page
            = new ClassicDialog.Page(Messages.message(message), icon);
        final String yes = yesKey;   // effectively-final capture
        onEventThread(() -> {
                int chosen = -1;
                try {
                    chosen = ClassicDialog.ask(dialogOwner(), title, page, options,
                                               options.length - 1);
                } finally {
                    final boolean accept = (yes != null && chosen == 0);
                    if (handler != null) handler.handle(accept);
                }
                return null;
            }, null);
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
     * The base {@code GUI} <em>declines</em> every confirmation, which silently
     * aborts the controller flows that gate on one — notably {@code buildColony},
     * which confirms the site warnings before founding a colony.
     *
     * <p>Phase 3: put the question in the classic wood-framed popup shared with
     * every other classic dialog ({@link ClassicDialog}), replacing the plain
     * Swing stopgap this shipped as.  A dismissed popup ({@code -1}) is neither
     * option, so fall back to {@code defaultOk} — the same answer Escape gave
     * before.
     */
    @Override
    public boolean modalConfirmDialog(Tile tile, StringTemplate template,
                                      ImageIcon icon, String okKey,
                                      String cancelKey, boolean defaultOk) {
        final String[] options = {
            Messages.message(okKey), Messages.message(cancelKey)
        };
        final ClassicDialog.Page page = new ClassicDialog.Page(
            Messages.message(template), (icon == null) ? null : icon.getImage());
        final int chosen = onEventThread(() -> ClassicDialog.ask(dialogOwner(),
                colony(tile), page, options, (defaultOk ? 0 : 1)),
            -1);
        return (chosen < 0) ? defaultOk : (chosen == 0);
    }

    /**
     * {@inheritDoc}
     *
     * Wired for the same reason as {@link #modalConfirmDialog}: some controller
     * flows can only proceed through a choice.  Notably, disembarking a carrier
     * that holds more than one unit asks <em>which</em> unit(s) to land — so
     * without this a laden ship could never put colonists ashore, and the colony
     * screen (which needs a founded colony) would be unreachable.  Presented as a
     * plain Swing selection list for now; Phase 3 reskins it.
     *
     * <p>Built by hand rather than with {@code JOptionPane.showInputDialog},
     * whose dialog is already packed (displayable) when it is returned, so
     * its decoration can no longer be changed: in full screen the list must
     * be undecorated like every other classic window
     * ({@link #prepareChildWindow}).  Behaviour is that of
     * {@code showInputDialog}: OK (or a double click) answers the selected
     * item; Cancel, Escape or closing answers null.  Undecorated, the pane
     * gets the popups' wood border so it does not float frameless.
     */
    @Override
    protected <T> T modalChoiceDialog(Tile tile, StringTemplate template,
                                      ImageIcon icon, String cancelKey,
                                      List<ChoiceItem<T>> choices) {
        if (choices == null || choices.isEmpty()) return null;
        final String text = Messages.message(template);
        final ChoiceItem<T>[] options = choices.toArray(new ChoiceItem[0]);
        final ChoiceItem<T> chosen = onEventThread(() ->
            (ChoiceItem<T>) chooseFromList(dialogOwner(), colony(tile), text,
                                           icon, options), null);
        return (chosen == null) ? null : chosen.getObject();
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
        d.setVisible(true);   // blocks until disposed
        final Object sel = pane.getInputValue();
        return (sel == JOptionPane.UNINITIALIZED_VALUE) ? null : sel;
    }

    /** Title for a tile-anchored dialog: the settlement there, else the game name. */
    private static String colony(Tile tile) {
        final Colony c = (tile == null) ? null : tile.getColony();
        return (c == null) ? "FreeCol" : c.getName();
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
