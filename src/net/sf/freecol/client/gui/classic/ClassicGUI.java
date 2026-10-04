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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
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
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

import net.sf.freecol.FreeCol;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.control.PreGameController;
import net.sf.freecol.client.gui.ChoiceItem;
import net.sf.freecol.client.gui.action.ActionManager;
import net.sf.freecol.client.gui.action.FreeColAction;
import net.sf.freecol.client.gui.DialogHandler;
import net.sf.freecol.client.gui.GUI;
import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.client.gui.LoadingSavegameInfo;
import net.sf.freecol.client.gui.FontLibrary;
import net.sf.freecol.client.gui.menu.InGameMenuBar;
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
import net.sf.freecol.common.model.Nation;
import net.sf.freecol.common.model.NationSummary;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.resources.ImageCache;


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

    /** The main application window. */
    private JFrame frame;

    /** The colony screen's window, while one is open (see {@link #showColonyPanel}). */
    private JFrame colonyFrame;

    /** The colony panel inside {@link #colonyFrame}, kept so it can be repainted. */
    private ClassicColonyPanel colonyPanel;

    /** The build-queue screen's window, while one is open (see {@link #showBuildQueuePanel}). */
    private JFrame buildQueueFrame;

    /**
     * The original's menu bar is a dark strip with light labels (design ref:
     * {@code opening_008}), where FreeCol's reused bar is dark-on-parchment.
     * See {@link #styleClassicMenuBar}.
     */
    private static final Color MENU_BAR_BG = new Color(0x20, 0x18, 0x10);
    private static final Color MENU_BAR_FG = new Color(0xE8, 0xE0, 0xC0);

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
     * Create and show the main window on a <b>passive</b> title backdrop: the
     * original title picture ({@link ClassicMainMenuPanel} in
     * {@code PASSIVE} mode) with no menu and no input.  Only
     * {@link #showMainPanel} makes the menu live, so the {@code --fast} path
     * (which goes straight to a game and never calls it) is unchanged -- it
     * merely loads over the clean title picture instead of a placeholder.
     */
    @Override
    public void startGUI(final Dimension desiredWindowSize) {
        logger.info("Starting ClassicGUI.");
        // FreeCol passes Dimension(-1,-1) (WINDOWSIZE_FALLBACK) when no explicit
        // --windowsize is given, meaning "use the full screen".  A plain
        // null-check treats that sentinel as a real size and yields a 1x1 window,
        // so only honour a size with positive dimensions; otherwise maximize
        // (mirrors FreeColFrame's handling of invalid/absent bounds).
        final boolean explicitSize = desiredWindowSize != null
            && desiredWindowSize.width > 0 && desiredWindowSize.height > 0;
        final Dimension size = explicitSize
            ? desiredWindowSize : new Dimension(1024, 768);
        SwingUtilities.invokeLater(() -> {
            this.frame = new JFrame("FreeCol — Classic UI (experimental)");
            this.frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            // The original title picture (OPENMENU.PIK, 1:1 in a letterboxed
            // 320x200 canvas), passive until showMainPanel; without the pack a
            // dark ground.  reconnectGUI replaces it with the in-game view.
            mainMenu().showPassive();
            this.frame.setContentPane(this.mainMenuPanel);
            this.frame.setSize(size);
            this.frame.setLocationByPlatform(true);
            if (!explicitSize) {
                this.frame.setExtendedState(Frame.MAXIMIZED_BOTH);
            }
            this.frame.setVisible(true);
            logger.info("ClassicGUI window shown.");
        });
        logger.info("ClassicGUI started.");
    }


    // Title screen and main menu

    /**
     * What the original's new-game screens collect before a game in the New
     * World starts: the difficulty ({@code opening_034}), the European power
     * ({@code 035}) and the leader's name ({@code 036}).  Null fields mean
     * "FreeCol's default".
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
            public void newWorld() {
                beginNewWorldSetup();
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
        };
    }

    /**
     * THE SEAM for the original's new-game screens.  The difficulty
     * ({@code opening_034}), nation ({@code 035}) and name ({@code 036})
     * screens chain in here, painted on the same title canvas, and finish by
     * calling {@link #startNewWorldGame} with what the player chose.  Until
     * they exist, the game starts at once with FreeCol's defaults.
     */
    void beginNewWorldSetup() {
        startNewWorldGame(NewWorldSetup.defaults());
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
     * @param setup The player's choices.
     */
    void startNewWorldGame(final NewWorldSetup setup) {
        SwingUtilities.invokeLater(() -> {
            try {
                // A fresh Specification per call (FreeCol.java:981-994).
                final Specification spec = FreeCol.loadSpecification(
                    FreeCol.getRulesFile(), FreeCol.getAdvantages(),
                    setup.difficultyId);
                if (spec == null) {
                    showMainPanel(Messages.message("classic.mainMenu.startFailed"));
                    return;
                }
                if (setup.playerName != null) FreeCol.setName(setup.playerName);
                this.pendingNationId = setup.nationId;
                if (!getFreeColClient().getConnectController()
                        .startSinglePlayerGame(spec)) {
                    this.pendingNationId = null;
                    showMainPanel(null);
                }
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "ClassicGUI: new game failed.", e);
                this.pendingNationId = null;
                // Shows the error, then returns to the title (GUI.java:945-951).
                showErrorPanel(e,
                    StringTemplate.key("classic.mainMenu.startFailed"));
            }
        });
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
        if (this.frame != null) this.frame.setJMenuBar(null);
        this.mapViewer = null;
        this.infoPanel = null;
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
     * @param userMsg An optional notice shown over the menu.
     * @return {@code null} (the classic UI hosts its own panels).
     */
    @Override
    public FreeColPanel showMainPanel(final String userMsg) {
        SwingUtilities.invokeLater(() -> {
            if (this.frame == null) return;
            teardownInGame();
            mainMenu().showTitle(userMsg);
            if (this.frame.getContentPane() != this.mainMenuPanel) {
                this.frame.setContentPane(this.mainMenuPanel);
            }
            this.frame.revalidate();
            this.frame.repaint();
            this.mainMenuPanel.requestFocusInWindow();
            updateActions();
        });
        return null;
    }

    /**
     * {@inheritDoc}
     *
     * Back to the title from a running game ({@code ConnectController.mainTitle},
     * which logs out and stops the server right after this), with the intro
     * music as in {@code SwingGUI}.
     */
    @Override
    public void showMainTitle() {
        showMainPanel(null);
        playSound("sound.intro.general");
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
     * The original's intro is not recreated yet, so go straight on.  The base
     * no-op (GUI.java:1141) never ran {@code callback}, so a classic start
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
            // Without a pick (--fast, today's defaults) nothing changes.
            final String nid = this.pendingNationId;
            this.pendingNationId = null;
            if (nid != null && !nid.equals(player.getNationId()) && game != null) {
                try {
                    final Nation n = game.getSpecification().getNation(nid);
                    if (n != null) {
                        final PreGameController pgc
                            = getFreeColClient().getPreGameController();
                        pgc.setNation(n);
                        pgc.setNationType(n.getType());
                    }
                } catch (RuntimeException e) {
                    logger.log(Level.WARNING, "ClassicGUI: cannot select nation "
                        + nid, e);
                }
            }
            player.setReady(true);
            getFreeColClient().getPreGameController().requestLaunch();
        }
        return null;
    }

    // In-game map

    /**
     * {@inheritDoc}
     *
     * Called from {@code FreeColClient.restoreGUI} once a game is ready (the
     * initial active unit / focus tile are supplied here).  Phase 1: build the
     * {@link ClassicMapViewer} and swap it in for the title screen
     * ({@link ClassicMainMenuPanel}), then set the initial view state so the
     * map renders centred on the action.  The view is only built while
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
                this.infoPanel = new ClassicInfoPanel(getFreeColClient(),
                                                      this.mapViewer,
                                                      this.imageLibrary);
                // Phase 2 HUD: the map fills the centre, the classic info/orders
                // strip sits on the right, and the reused InGameMenuBar (wired to
                // the real FreeColActions) is the top menu bar.
                final JPanel content = new JPanel(new BorderLayout());
                content.add(this.mapViewer, BorderLayout.CENTER);
                content.add(this.infoPanel, BorderLayout.EAST);
                this.frame.setContentPane(content);
                try {
                    final InGameMenuBar menuBar
                        = new InGameMenuBar(getFreeColClient(), null);
                    styleClassicMenuBar(menuBar);
                    remapClassicReportAccelerators();
                    this.frame.setJMenuBar(menuBar);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "ClassicGUI: menu bar unavailable",
                               e);
                }
                this.frame.revalidate();
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
        });
    }

    /**
     * Give the reused {@link InGameMenuBar} the original's <b>light-on-dark</b>
     * menu bar (design ref: the expert's {@code opening_008}), in place of
     * FreeCol's dark-on-parchment one.
     *
     * <p>The hook is {@code FreeColMenuBar.paintComponent}, which tiles its
     * parchment background <em>only while the bar is non-opaque</em> and otherwise
     * defers to {@code super.paintComponent} — so making the bar opaque with a dark
     * background swaps the parchment for Col1's dark strip.  The menu labels are
     * then re-coloured light for contrast.  The bar's own golden gold/tax/year
     * status line already reads on dark (it is drawn after this, over the
     * background, by {@code InGameMenuBar.paintComponent}), and the wood border is
     * kept — it still reads classic.
     *
     * <p>Safe to do after construction: {@code InGameMenuBar.reset()} — which
     * rebuilds (and would re-create) the menus — is only called from its own
     * constructor and from {@code FreeColFrame}, which the classic UI does not use.
     * The dropdown popups get their own wood/green reskin — see {@link
     * #installClassicMenuDropdownDefaults}, installed earlier at start-up — plus
     * one per-instance touch-up here for the separators (below).
     */
    private void styleClassicMenuBar(InGameMenuBar menuBar) {
        menuBar.setOpaque(true);
        menuBar.setBackground(MENU_BAR_BG);
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            final JMenu menu = menuBar.getMenu(i);
            if (menu == null) continue;   // separators/glue are null here
            menu.setOpaque(false);
            menu.setForeground(MENU_BAR_FG);
            styleClassicDropdownSeparators(menu.getPopupMenu());
        }
    }

    /**
     * Colour one dropdown's separator lines to match its wood/green reskin.
     *
     * <p>Unlike the rest of {@link #installClassicMenuDropdownDefaults}'s
     * {@code UIManager}-defaults approach, {@code JSeparator}'s bevel-line
     * painter (at least on some JDK versions) reads the <em>component's own</em>
     * foreground/background rather than a {@code UIManager} key, so a global
     * default alone would leave it unstyled on those JDKs — this per-instance
     * pass is the belt-and-suspenders fallback.
     */
    private void styleClassicDropdownSeparators(JPopupMenu popup) {
        for (Component c : popup.getComponents()) {
            if (c instanceof JPopupMenu.Separator) {
                c.setForeground(ClassicDialog.BORDER_HI);
                c.setBackground(ClassicDialog.BORDER_LO);
            }
        }
    }

    /**
     * Reassign the reused report actions' keyboard accelerators to match the
     * <b>observed original 1994 game's F-key scheme</b> (the expert's capture
     * {@code 00_BERICHTE-Menu_Tastenbelegung}: F1 terrain-info, F2 Religious,
     * F3 Congress, F4 Labour, F5 Trade, F6 Colony, F7 Naval, F8 Foreign
     * Affairs, F9 Indian, F10 score — no shift-F* layer at all) rather than
     * FreeCol's own arbitrary layout in {@code FreeColMessages.properties}
     * (e.g. that file's F1=Religious, F3=Colony, F4=Foreign Affairs, F2=Labour
     * — a layout with nothing to do with Col1, just FreeCol's own history).
     *
     * <p><b>Runtime-only, never touches the properties file:</b> that file is
     * shared with {@code SwingGUI}, so editing it would silently re-map the
     * standard game's shortcuts too. {@link FreeColAction#setAccelerator} only
     * mutates the live in-memory {@code Action} object, and {@code --classic}
     * exclusively selects {@code ClassicGUI} for the whole process — see the
     * GUI selector in {@code FreeColClient} — so a standard-UI session never
     * shares a process (or these mutated action objects) with a classic one.
     * Same trick as {@link #styleClassicMenuBar}: reuse the shared component,
     * restyle only this process's copy.
     *
     * <p>Covers only the <b>six reports with a confirmed Col1 counterpart</b>
     * (README "Report screens" / plan §6) plus the two newly-built ones
     * (Labour, Foreign Affairs) — eight remaps. <b>Deliberately leaves
     * Military / Production / Exploration / Cargo's keys untouched</b>: none
     * of those four has a confirmed original counterpart, so reassigning them
     * is not this fix's call to make (plan §6 — a decision for the user/expert).
     *
     * <p>⚠️ <b>Known collision, flagged rather than silently resolved:</b>
     * Naval's confirmed key is F7 — already occupied by Military, left alone
     * per the above. Until §6 is settled both menu items will display "F7" but
     * only one actually responds when pressed (Swing's shared
     * keystroke-to-action input map only keeps the most recently registered
     * binding for a given keystroke).
     */
    private void remapClassicReportAccelerators() {
        final ActionManager am = getFreeColClient().getActionManager();
        remapAccelerator(am, "reportReligionAction", "F2");
        remapAccelerator(am, "reportCongressAction", "F3");
        remapAccelerator(am, "reportLabourAction", "F4");
        remapAccelerator(am, "reportTradeAction", "F5");
        remapAccelerator(am, "reportColonyAction", "F6");
        remapAccelerator(am, "reportNavalAction", "F7");
        remapAccelerator(am, "reportForeignAction", "F8");
        remapAccelerator(am, "reportIndianAction", "F9");
    }

    private void remapAccelerator(ActionManager am, String actionId, String keyStroke) {
        final FreeColAction action = am.getFreeColAction(actionId);
        if (action != null) action.setAccelerator(KeyStroke.getKeyStroke(keyStroke));
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
        if (this.frame != null && this.frame.getJMenuBar() != null) {
            this.frame.getJMenuBar().repaint();
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
                f.setContentPane(panel);
                f.pack();
                f.setLocationRelativeTo(this.frame);
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
                f.pack();
                f.setLocationRelativeTo(this.colonyFrame != null
                    ? this.colonyFrame : this.frame);
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
                f.setContentPane(panel);
                f.pack();
                f.setLocationRelativeTo(this.frame);
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
                f.setContentPane(factory.apply(this::closeReportPanel));
                f.pack();
                f.setLocationRelativeTo(this.frame);
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
     */
    private void showMessagePopup(List<ModelMessage> messages, String titleKey) {
        if (messages == null || messages.isEmpty()) return;
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
                ClassicDialog.showMessages(this.frame,
                    Messages.message(titleKey), pages);
                return null;
            }, null);
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
                    ClassicDialog.showMessages(this.frame,
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
                    chosen = ClassicDialog.ask(this.frame, title, page, options,
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
        final int chosen = onEventThread(() -> ClassicDialog.ask(this.frame,
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
     */
    @Override
    protected <T> T modalChoiceDialog(Tile tile, StringTemplate template,
                                      ImageIcon icon, String cancelKey,
                                      List<ChoiceItem<T>> choices) {
        if (choices == null || choices.isEmpty()) return null;
        final String text = Messages.message(template);
        final ChoiceItem<T>[] options = choices.toArray(new ChoiceItem[0]);
        final ChoiceItem<T> chosen = onEventThread(() -> {
            final Object sel = JOptionPane.showInputDialog(this.frame, text,
                colony(tile), JOptionPane.QUESTION_MESSAGE, icon,
                options, options[0]);
            return (ChoiceItem<T>) sel;
        }, null);
        return (chosen == null) ? null : chosen.getObject();
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
     * font null — fine while nothing painted text, but the Phase 2 HUD (the
     * reused {@link InGameMenuBar} draws a golden gold/tax/year status line via
     * {@code FontLibrary.getMainFont()}) then NPEs.  So initialise the main font
     * here, and the image-border scale factor so the menu bar's wood border
     * renders.
     *
     * <p>We deliberately do <em>not</em> install {@code FreeColLookAndFeel}: it
     * swaps in a {@code PanelUI} that paints the FreeCol parchment texture behind
     * every {@code JPanel}, which would override the classic map's black fog and
     * the dark info panel.  The reused {@link InGameMenuBar} paints its own
     * parchment background + wood border regardless of the active L&F, so the top
     * bar still reads classic; the dropdown popups get the Phase-3 wood/green
     * reskin instead (see {@link #installClassicMenuDropdownDefaults}).  All
     * guarded so a failure just leaves the default look rather than aborting
     * startup.
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
     * Reskin the reused {@link InGameMenuBar}'s dropdown popups — still stock
     * Swing look, per {@link #styleClassicMenuBar}'s Javadoc — to {@link
     * ClassicDialog}'s wood-framed, green-on-wood palette (the same guess its
     * Javadoc already flags for the expert's Q3 sign-off; extending it here adds
     * no new risk).
     *
     * <p>Installed as {@code UIManager} <em>defaults</em>, once, here — before
     * {@link #reconnectGUI} ever builds the {@code InGameMenuBar} — rather than
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
     * <p>Safe process-wide for the same reason as
     * {@link #remapClassicReportAccelerators}: {@code --classic} selects
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
     */
    @Override
    public void quitGUI() {
        final JFrame f = this.frame;
        if (f != null) {
            SwingUtilities.invokeLater(f::dispose);
        }
    }
}
