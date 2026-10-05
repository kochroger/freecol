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
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.Timer;

import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.resources.ResourceManager;


/**
 * The original's <b>title screen and main menu</b> ({@code opening_033}),
 * plus the boxes that open over it: the load box (in the style of
 * {@code opening_055}), notices, and a busy box while a game starts.
 *
 * <p>The background is exactly the title picture {@code OPENMENU.PIK} drawn
 * 1:1 into a 320x200 virtual canvas -- outside the menu box the native
 * capture equals it pixel for pixel.  (It is <em>not</em> {@code OPENING.PIK},
 * which is a 960x132 sea chart.)  Only the menu box is drawn on top, by
 * {@link ClassicMenuBox} with the {@link ClassicFont} bitmap font.  The
 * canvas is scaled by the largest whole factor that fits, nearest-neighbour,
 * with a black letterbox -- the same scheme as the colony and Europe screens.
 *
 * <p>All painting lives in package-private <em>static</em> methods taking
 * explicit images, font and state, so a headless preview harness can render
 * them without a {@code FreeColClient} and diff them against the captures.
 *
 * <p>Modes: {@link Mode#PASSIVE} (just the picture: the backdrop while
 * {@code --fast} loads a game, before {@code showMainPanel} makes the menu
 * live), {@link Mode#TITLE}, {@link Mode#LOAD}, {@link Mode#NOTICE},
 * {@link Mode#BUSY}, {@link Mode#QUIT} (the "Colonization beenden?" box
 * that Escape opens on the title -- in borderless full screen there is no
 * window close button, see {@code ClassicGUI.applyFrameMode} -- and that
 * Alt+F4 opens too, see {@link #offerQuit}), {@link Mode#NEW_WORLD} (the
 * original's new-game chain -- difficulty, power, name, the nation's two
 * pages, the audience -- painted by {@link ClassicNewWorldScreens} and
 * driven by a {@link ClassicNewWorldChain}), {@link Mode#DEPARTURE} (the
 * original's ten-picture departure after the audience, painted by
 * {@link ClassicDeparture} on a {@link ClassicDepartureTimeline}; any key
 * or click skips it) and {@link Mode#STARTING} (the departure's last
 * picture -- or, without the departure, the audience -- frozen while the
 * engine starts the game).  In PASSIVE, BUSY and STARTING all input is
 * ignored -- that is the double-start guard, as the EDT is free between
 * login and the in-game view.
 *
 * <p>The pointer is the original's own arrow ({@code CURSOR.SS.000}), drawn
 * into the canvas at the canvas scale -- see {@link #paintCursor}.
 *
 * <p><b>Fast start.</b>  This panel usually exists before any client does:
 * {@link ClassicStartupScreen} creates it about a second after launch with
 * pack-file assets ({@link #useAssets}, {@link MenuAssets#fromPackFiles})
 * and actions that only queue a choice, and {@code ClassicGUI} later adopts
 * it and swaps in its real actions ({@link #setActions}).  Everything the
 * title, the load box and the new-game chain draw therefore comes from
 * plain pack files, never from the resource manager, which is still being
 * set up then; {@link #isLive} tells {@code ClassicGUI.showMainPanel} not
 * to reset a menu the player is already using.
 */
final class ClassicMainMenuPanel extends JPanel {

    private static final Logger logger = Logger.getLogger(ClassicMainMenuPanel.class.getName());

    /** What the menu items do; implemented by {@link ClassicGUI}. */
    interface Actions {
        /**
         * "Ein Spiel in der NEUEN WELT starten", once the new-game chain
         * is through.
         *
         * @param setupOrNull The player's choices, or null for FreeCol's
         *     defaults (a pack without the original texts skips the chain).
         */
        void newWorld(ClassicGUI.NewWorldSetup setupOrNull);
        /** "Spiel LADEN", after a save was picked. */
        void loadGame(File file);
        /** "Ruhmeshalle besuchen". */
        void hallOfFame();
        /**
         * "Ja" in the quit box ({@link Mode#QUIT}): leave the application
         * through FreeCol's normal quit path.
         */
        void quit();
    }

    /** What the canvas currently shows. */
    enum Mode { PASSIVE, TITLE, LOAD, NOTICE, BUSY, QUIT, NEW_WORLD, DEPARTURE, STARTING }

    /** The images and font the static painters use; any may be null. */
    static final class MenuAssets {

        final BufferedImage title, openTile, woodTile;
        final ClassicFont font;

        /** The mouse arrow, ready to draw (see {@link #cursorSprite}). */
        final BufferedImage cursor;

        MenuAssets(BufferedImage title, BufferedImage openTile,
                   BufferedImage woodTile, ClassicFont font) {
            this(title, openTile, woodTile, font, null);
        }

        MenuAssets(BufferedImage title, BufferedImage openTile,
                   BufferedImage woodTile, ClassicFont font,
                   BufferedImage cursor) {
            this.title = title;
            this.openTile = openTile;
            this.woodTile = woodTile;
            this.font = font;
            this.cursor = cursor;
        }

        /** Load from the {@code classic_original} pack with silent probes. */
        static MenuAssets fromResources() {
            return new MenuAssets(image(TITLE_KEY),
                image(ClassicMenuBox.TITLE.fillKey),
                image(ClassicMenuBox.GAME.fillKey),
                ClassicFont.get(ClassicFont.TINY),
                cursorSprite(image(CURSOR_KEY)));
        }

        /**
         * Load straight from the pack's files, without the resource manager
         * (the early start-up window, {@link ClassicStartupScreen}): the
         * same title picture, menu fill tiles, FONTTINY atlas and metrics and
         * arrow that {@link #fromResources} gets, read by
         * {@link ClassicPackFiles}.  WHY: at the moment the early window
         * opens, FreeCol has not even begun to register the pack (12-15 s
         * before the fast start, ~0.6 s after it, and only inside the
         * client's constructor); these few files take ~0.1-0.2 s.  A
         * headless render of the title from exactly these files has 0
         * differing pixels against {@code opening_033} (with the original
         * version line).
         *
         * @param p The pack, or null.
         * @return The assets, or null when the pack lacks the title picture,
         *     the title fill tile or the font (the caller then falls back to
         *     the old start-up).
         */
        static MenuAssets fromPackFiles(ClassicPackFiles p) {
            if (p == null) return null;
            final BufferedImage title = p.image(TITLE_KEY);
            final BufferedImage openTile = p.image(ClassicMenuBox.TITLE.fillKey);
            final ClassicFont font = p.font(ClassicFont.TINY);
            if (title == null || openTile == null || font == null) return null;
            return new MenuAssets(title, openTile,
                p.image(ClassicMenuBox.GAME.fillKey), font,
                cursorSprite(p.image(CURSOR_KEY)));
        }

        private static BufferedImage image(String key) {
            try {
                return (ResourceManager.getImageResource(key, false) == null) ? null
                    : ImageLibrary.getUnscaledImage(key);
            } catch (RuntimeException e) {
                return null;
            }
        }

        /** The fill tile of a theme. */
        BufferedImage tileFor(ClassicMenuBox.Theme t) {
            return (t == ClassicMenuBox.GAME) ? this.woodTile : this.openTile;
        }
    }


    /** The virtual canvas. */
    static final int VW = 320;
    static final int VH = 200;

    /** The title picture. */
    static final String TITLE_KEY = "image.classic_original.pik.OPENMENU.PIK";

    /** The original's mouse arrow (frame 001 is another pointer shape). */
    static final String CURSOR_KEY = "image.classic_original.ss.CURSOR.SS.000";

    /**
     * The opaque key colour the two CURSOR.SS frames -- and no other of the
     * 1517 SS frames -- carry in their top-right and bottom-left corner
     * pixels.  The game never shows them (the arrow in {@code opening_033}
     * has none), so {@link #cursorSprite} clears them.
     */
    private static final int CURSOR_KEY_COLOUR = 0xFF5555FF;

    /**
     * The menu's title line.  {@code {..}} marks the gold word; the rest is
     * drawn in the line's green.
     *
     * <p>GAME.TXT:42 has {@code {COLONIZATION} Version %STRING0 -- %STRING1},
     * which VICEROY.EXE fills with "2.26" and "19-Sept-94" (it contains no
     * "1.26", and the capture's pixels next to the cursor fit '2', not '1').
     * <b>Deliberately not reproduced:</b> the owner asked for this line to
     * read "COLONIZATION Version 2026" instead -- this recreation's own
     * version label -- so the original's version and date are dropped on
     * purpose.  Same font, same colours, same position; only the text
     * differs from {@code opening_033}.
     */
    static final String TITLE_LINE = "{COLONIZATION} Version 2026";

    /** GAME.TXT:38-41 {@code @width=160 @y=91}: the box (77,91,166,64). */
    static final int TITLE_INNER_WIDTH = 160;
    static final int TITLE_Y = 91;

    /** GAME.TXT {@code @SAVEGAME}/{@code @LOADGAME} {@code @width=190}. */
    static final int DIALOG_INNER_WIDTH = 190;

    /** Rows the load box shows at once (as in 055). */
    static final int LOAD_VISIBLE_ROWS = 10;

    /** Menu item indices. */
    static final int ITEM_NEW_WORLD = 0;
    static final int ITEM_AMERICA = 1;
    static final int ITEM_CUSTOMIZE = 2;
    static final int ITEM_LOAD = 3;
    static final int ITEM_HALL_OF_FAME = 4;

    /** Message keys of the items, in order. */
    private static final String[] ITEM_KEYS = {
        "classic.mainMenu.newWorld", "classic.mainMenu.america",
        "classic.mainMenu.customize", "classic.mainMenu.load",
        "classic.mainMenu.hallOfFame"
    };

    /** Notice wrap width: the dialog interior less the text margins. */
    private static final int NOTICE_WRAP = DIALOG_INNER_WIDTH - 10;

    /** Most notice lines: a box of 8*20+16 = 176 px fits the 200-px canvas. */
    static final int NOTICE_MAX_LINES = 20;

    /** Ground colour without the title picture. */
    private static final Color FALLBACK_BACKGROUND = new Color(0x1C140C);

    /** Quit box rows: "Ja" first, as in GAME.TXT {@code @DOS}. */
    static final int QUIT_YES = 0;
    static final int QUIT_NO = 1;

    /**
     * Narrowest quit-box interior.  GAME.TXT's {@code @DOS} box has no
     * {@code @width}, so the box is sized to its prompt (see
     * {@link #quitBounds}); this floor keeps a short translation from
     * producing a sliver.
     */
    private static final int QUIT_MIN_INNER_WIDTH = 80;


    // Static painting (no FreeColClient, no Messages)

    /** The title picture 1:1, or a dark ground without the pack. */
    static void paintBackground(Graphics2D g, MenuAssets a) {
        if (a.title != null) {
            g.drawImage(a.title, 0, 0, null);
        } else {
            g.setColor(FALLBACK_BACKGROUND);
            g.fillRect(0, 0, VW, VH);
        }
    }

    /** The bounds of the title menu box for {@code itemCount} items. */
    static Rectangle titleBounds(int itemCount) {
        return ClassicMenuBox.dialogBounds(TITLE_INNER_WIDTH, 1, itemCount, TITLE_Y);
    }

    /** The bounds of the load box for {@code rows} visible rows. */
    static Rectangle loadBounds(int rows) {
        return ClassicMenuBox.dialogBounds(DIALOG_INNER_WIDTH, 1, Math.max(1, rows), -1);
    }

    /** The whole title screen: picture plus menu box ({@code opening_033}). */
    static void paintTitleScreen(Graphics2D g, MenuAssets a, String titleLine,
                                 List<String> items, int selected) {
        paintBackground(g, a);
        ClassicMenuBox.paintDialog(g, titleBounds(items.size()),
            ClassicMenuBox.TITLE, a.openTile, a.font,
            Collections.singletonList(titleLine), items, selected);
    }

    /**
     * The load box ({@code opening_055} layout) over whatever is already
     * painted.  Callers have already cut the rows with
     * {@link ClassicMenuBox#fit}.
     */
    static void paintLoadBox(Graphics2D g, MenuAssets a, ClassicMenuBox.Theme t,
                             String prompt, List<String> visibleRows,
                             int selectedInWindow) {
        ClassicMenuBox.paintDialog(g, loadBounds(visibleRows.size()), t,
            a.tileFor(t), a.font, Collections.singletonList(prompt),
            visibleRows, selectedInWindow);
    }

    /**
     * A notice: the lines as the prompt of a row-less box, centred.  Drawn
     * plain, not marked: notices carry free-form engine and user text, whose
     * '{', '}' and '~' must show as written rather than turn text gold.
     */
    static void paintNotice(Graphics2D g, MenuAssets a, ClassicMenuBox.Theme t,
                            List<String> lines) {
        ClassicMenuBox.paintDialog(g,
            ClassicMenuBox.dialogBounds(DIALOG_INNER_WIDTH, lines.size(), 0, -1),
            t, a.tileFor(t), a.font, lines, false,
            Collections.<String>emptyList(), -1);
    }

    /**
     * The bounds of the quit box: one prompt line, two rows, centred.  The
     * interior is the prompt's width plus the text margins (prompt at X+5,
     * so 5 px either side), never narrower than
     * {@link #QUIT_MIN_INNER_WIDTH}.
     */
    static Rectangle quitBounds(MenuAssets a, String prompt) {
        final int inner = Math.max(QUIT_MIN_INNER_WIDTH,
            ClassicMenuBox.textWidth(a.font, prompt, false) + 10);
        return ClassicMenuBox.dialogBounds(inner, 1, 2, -1);
    }

    /**
     * The quit box over the bare title picture, in the title's style.
     *
     * <p>Modelled on the original's own exit question, GAME.TXT {@code @DOS}
     * ("Abbrechen zu DOS?" / Ja / Nein, {@code @default=2}): a DIALOG box
     * with one prompt line and the two rows.  The prompt text is the
     * owner's ("Colonization beenden?") since there is no DOS to return
     * to.  Drawn like the load box -- picture plus box, without the title
     * menu, which the centred box would otherwise half cover.  The prompt is
     * drawn plain (our own text, no markup).
     *
     * @param prompt The question.
     * @param rows The two answers, "Ja" first.
     * @param selected The barred row.
     */
    static void paintQuitBox(Graphics2D g, MenuAssets a, String prompt,
                             List<String> rows, int selected) {
        paintBackground(g, a);
        ClassicMenuBox.paintDialog(g, quitBounds(a, prompt),
            ClassicMenuBox.TITLE, a.openTile, a.font,
            Collections.singletonList(prompt), false, rows, selected);
    }

    /**
     * Wrap a notice to the box width (by characters without the font), at
     * most {@link #NOTICE_MAX_LINES} lines -- more would push the box off the
     * 200-px canvas at both ends.  A cut message ends in "..." and is logged
     * in full.
     */
    static List<String> wrapNotice(MenuAssets a, String msg) {
        final String s = sanitiseNotice(msg);
        final List<String> out;
        if (a.font != null) {
            out = a.font.wrap(s, NOTICE_WRAP);
        } else {
            out = new ArrayList<>();
            for (String para : s.split("\n", -1)) {
                final StringBuilder line = new StringBuilder();
                for (String word : para.split(" ")) {
                    if (line.length() > 0 && line.length() + 1 + word.length() > 44) {
                        out.add(line.toString());
                        line.setLength(0);
                    }
                    if (line.length() > 0) line.append(' ');
                    line.append(word);
                }
                out.add(line.toString());
            }
        }
        if (out.size() <= NOTICE_MAX_LINES) return out;
        logger.info("Notice cut to " + NOTICE_MAX_LINES + " lines: " + msg);
        final List<String> cut = new ArrayList<>(out.subList(0, NOTICE_MAX_LINES));
        final String last = cut.get(NOTICE_MAX_LINES - 1) + " ...";
        cut.set(NOTICE_MAX_LINES - 1, (a.font == null) ? last
            : a.font.fit(last, NOTICE_WRAP));
        return cut;
    }

    /**
     * Map characters FONTTINY cannot show to near equivalents, so engine
     * messages stay readable: a '\' would draw as 'Ü' (code 92 is Ü in the
     * game's character set, see {@link ClassicFont#toCode}), and
     * {@code _ < > = @ * ^} have no glyph and would all become '?'.  This
     * matters most for file paths in error messages.
     */
    static String sanitiseNotice(String msg) {
        if (msg == null) return "";
        final StringBuilder sb = new StringBuilder(msg.length());
        for (int i = 0; i < msg.length(); i++) {
            final char ch = msg.charAt(i);
            switch (ch) {
            case '\\': sb.append('/'); break;
            case '_': sb.append('-'); break;
            case '<': case '{': case '[': sb.append('('); break;
            case '>': case '}': case ']': sb.append(')'); break;
            case '=': sb.append(':'); break;
            case '@': sb.append('a'); break;
            case '*': sb.append('x'); break;
            case '^': case '~': case '`': case '|': sb.append(' '); break;
            default: sb.append(ch); break;
            }
        }
        return sb.toString();
    }

    /**
     * The mouse arrow sprite: {@code raw} as ARGB with its two key-colour
     * corner pixels cleared (see {@link #CURSOR_KEY_COLOUR}).
     *
     * @return The sprite, or null without {@code raw}.
     */
    static BufferedImage cursorSprite(BufferedImage raw) {
        if (raw == null || raw.getWidth() <= 0 || raw.getHeight() <= 0) return null;
        final int w = raw.getWidth(), h = raw.getHeight();
        final BufferedImage c = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g = c.createGraphics();
        try {
            g.drawImage(raw, 0, 0, null);
        } finally {
            g.dispose();
        }
        if (c.getRGB(w - 1, 0) == CURSOR_KEY_COLOUR) c.setRGB(w - 1, 0, 0);
        if (c.getRGB(0, h - 1) == CURSOR_KEY_COLOUR) c.setRGB(0, h - 1, 0);
        return c;
    }

    /**
     * The original's arrow with its top-left at the virtual point
     * {@code (x, y)} -- the hot spot is the sprite origin: in
     * {@code opening_033} the game draws it at (160,100), the centre of the
     * 320x200 screen where it puts the mouse at start.
     */
    static void paintCursor(Graphics2D g, MenuAssets a, int x, int y) {
        if (a.cursor != null) g.drawImage(a.cursor, x, y, null);
    }


    // Instance state (EDT only)

    /**
     * What the menu items do.  Not final: the early start-up window
     * ({@link ClassicStartupScreen}) creates this panel before any client
     * exists, with actions that only queue a choice, and
     * {@code ClassicGUI} swaps in its real ones when it adopts the window
     * ({@link #setActions}).  EDT only.
     */
    private Actions actions;

    /**
     * Loaded on the first paint, then kept -- or given up front from the
     * pack files ({@link #useAssets}), so the early window never touches
     * the resource manager, which is not even set up at that point.
     */
    private MenuAssets assets;

    private Mode mode = Mode.PASSIVE;

    /** Where a notice returns to when dismissed. */
    private Mode noticeReturn = Mode.TITLE;

    /** The barred title item. */
    private int selected = 0;

    /** The item texts (from Messages). */
    private List<String> items = Collections.emptyList();

    /** The load box's saves, its selection and the first visible row. */
    private List<ClassicSaveGames.Entry> saves = Collections.emptyList();
    private int loadSel = 0;
    private int loadTop = 0;

    /**
     * Bumped whenever the load box opens or closes, so stale label updates
     * are ignored and the label thread of a closed box stops (it reads this
     * from its own thread, hence atomic).
     */
    private final AtomicInteger loadGeneration = new AtomicInteger();

    private List<String> noticeLines = Collections.emptyList();

    /** The barred row of the quit box ({@link #QUIT_YES} / {@link #QUIT_NO}). */
    private int quitSel = QUIT_NO;

    /** The last paint's canvas placement, for mapping the mouse back. */
    private int originX = 0, originY = 0, scale = 1;

    /**
     * The pointer's virtual position, or -1 while it is off the canvas (in
     * the letterbox, outside the window, or not yet seen).
     */
    private int pointerX = -1, pointerY = -1;

    /** The invisible system cursor shown while the arrow is drawn. */
    private Cursor blankCursor = null;

    /** Whether making {@link #blankCursor} failed (then keep the system arrow). */
    private boolean blankCursorFailed = false;

    /** Accumulated precise wheel rotation (touchpads send fractions). */
    private double wheelRest = 0.0;

    /** The new-game chain's art and texts, loaded together. */
    private static final class ChainData {

        final ClassicNewWorldScreens.Assets assets;
        final ClassicNewWorldScreens.Texts texts;

        ChainData(ClassicNewWorldScreens.Assets assets,
                  ClassicNewWorldScreens.Texts texts) {
            this.assets = assets;
            this.texts = texts;
        }
    }

    /**
     * The chain's art and texts, read from pack files on a daemon thread as
     * soon as the title first goes live, so NEUE WELT opens without a pause
     * (joined in {@link #startChain}).
     */
    private FutureTask<ChainData> chainPrefetch = null;

    /** The loaded chain data while the chain is shown, else null. */
    private ChainData chainData = null;

    /** The running new-game chain (NEW_WORLD, STARTING), else null. */
    private ClassicNewWorldChain chain = null;

    /** Whether the "chain unavailable" INFO line was logged. */
    private static boolean chainMissLogged = false;

    /**
     * Repaint period of the departure, ms (about 66 Hz).  The timer only
     * samples the {@link ClassicDepartureTimeline} by wall-clock time, so
     * its own jitter never changes the show's length.
     */
    static final int DEPARTURE_TICK_MS = 15;

    /** A running departure (EDT only). */
    private static final class Departure {

        /** The choices to start the game with when the show ends. */
        final ClassicGUI.NewWorldSetup setup;
        /** Frame 0 (audience) .. 10 (last picture), 64,000 RGB pixels each. */
        final int[][] frames;
        /** The reveal order of transition k (frame k -> k+1). */
        final int[][] orders;
        final ClassicDepartureTimeline timeline;
        /** What is on screen; {@link #shown} is its pixel array. */
        final BufferedImage image;
        final int[] shown;
        /** System.nanoTime() at the start. */
        final long t0;
        /** The transition being applied and how many of its pixels are. */
        int appliedTransition = 0, appliedCount = 0;

        Departure(ClassicGUI.NewWorldSetup setup, int[][] frames, int[][] orders,
                  ClassicDepartureTimeline timeline, BufferedImage image, long t0) {
            this.setup = setup;
            this.frames = frames;
            this.orders = orders;
            this.timeline = timeline;
            this.image = image;
            this.shown = ClassicDeparture.pixels(image);
            this.t0 = t0;
        }

        long elapsedMs() {
            return (System.nanoTime() - this.t0) / 1000000L;
        }
    }

    /** The running departure (DEPARTURE), else null. */
    private Departure departure = null;

    /** Drives the departure while it runs (DEPARTURE only). */
    private Timer departureTimer = null;

    /**
     * The departure's last picture, kept on screen after the show while
     * the engine starts (STARTING) and while the passive picture stands in
     * for the game view ({@code ClassicGUI.closeMainPanel} calls
     * {@link #showPassive} from {@code startGameInternal} just before the
     * game view replaces this panel).  Without it the title picture would
     * flash between the ship and the game.  Null otherwise.
     */
    private BufferedImage frozenFrame = null;


    /**
     * Create the panel (in {@link Mode#PASSIVE}).
     *
     * @param actions What the menu items do.
     */
    ClassicMainMenuPanel(Actions actions) {
        this.actions = actions;
        setOpaque(true);
        setBackground(Color.BLACK);
        setPreferredSize(new Dimension(VW * 3, VH * 3));
        setFocusable(true);
        installKeyBindings();
        final MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    trackPointer(e);
                    onPress(e);
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    trackPointer(e);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    setPointer(-1, -1);
                }

                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    onWheel(e);
                }
            };
        addMouseListener(mouse);
        addMouseWheelListener(mouse);
        addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    trackPointer(e);
                    onHover(e);
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    trackPointer(e);
                }
            });
        // The new-game chain reads raw key events (any key advances a page,
        // typed characters fill the name); see onChainKey.  The listener sees
        // every press before the menu's key bindings do (JComponent
        // processKeyEvent), in every mode, so heldKeys also knows about the
        // Enter on NEUE WELT that opened the chain.
        addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    final boolean repeat = !heldKeys.add(e.getKeyCode());
                    if (mode == Mode.DEPARTURE) {
                        onDepartureKey(e, repeat);
                    } else {
                        onChainKey(e, repeat);
                    }
                }

                @Override
                public void keyReleased(KeyEvent e) {
                    heldKeys.remove(e.getKeyCode());
                }

                @Override
                public void keyTyped(KeyEvent e) {
                    onChainTyped(e);
                }
            });
        // A release made while another window has the focus never arrives
        // here: forget everything held rather than block that key for good.
        addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    heldKeys.clear();
                }
            });
    }


    // Mode changes

    /**
     * Switch mode.  Leaving the load box stops its label thread, leaving
     * the departure stops its timer, entering a live mode drops the frozen
     * departure picture, and the pointer shape follows the mode (see
     * {@link #updatePointerShape}).
     */
    private void setMode(Mode m) {
        if (this.mode == Mode.LOAD && m != Mode.LOAD) {
            this.loadGeneration.incrementAndGet();
        }
        if (m != Mode.DEPARTURE) {
            stopDepartureTimer();
            this.departure = null;
        }
        this.mode = m;
        if (isLive()) this.frozenFrame = null;
        updatePointerShape();
    }

    /**
     * Just the picture; all input ignored.  Coming from STARTING (the game
     * is about to be shown, see {@code ClassicGUI.closeMainPanel}) the
     * departure's last picture stays up; any other way it is the title
     * picture -- e.g. the backdrop of an in-game load, long after the
     * departure.
     */
    void showPassive() {
        if (this.mode != Mode.STARTING) this.frozenFrame = null;
        setMode(Mode.PASSIVE);
        repaint();
    }

    /**
     * The game view has replaced this panel: the frozen departure picture
     * has done its job.
     */
    @Override
    public void removeNotify() {
        if (this.mode == Mode.PASSIVE) this.frozenFrame = null;
        super.removeNotify();
    }

    /**
     * The live title menu, first item barred.
     *
     * @param userMsg An optional notice to show over it (e.g. why a start
     *     failed).
     */
    void showTitle(String userMsg) {
        final List<String> list = new ArrayList<>();
        for (String key : ITEM_KEYS) list.add(Messages.message(key));
        this.items = list;
        this.selected = 0;
        this.chain = null;
        this.chainData = null;
        prefetchChain();
        setMode(Mode.TITLE);
        if (userMsg != null && !userMsg.isEmpty()) {
            showNotice(userMsg, Mode.TITLE);
        } else {
            repaint();
        }
    }

    /**
     * A box with {@code msg} and no choices, painted at once: the caller is
     * about to block the EDT (server start, game load).
     */
    void showBusy(String msg) {
        this.noticeLines = wrapNotice(assets(), msg);
        setMode(Mode.BUSY);
        paintImmediately(0, 0, getWidth(), getHeight());
    }

    private void showNotice(String msg, Mode back) {
        this.noticeLines = wrapNotice(assets(), msg);
        this.noticeReturn = back;
        setMode(Mode.NOTICE);
        repaint();
    }

    private void openLoadBox() {
        final List<ClassicSaveGames.Entry> list = ClassicSaveGames.list();
        if (list.isEmpty()) {
            showNotice(Messages.message("classic.mainMenu.noSaves"), Mode.TITLE);
            return;
        }
        this.saves = list;
        this.loadSel = 0;
        this.loadTop = 0;
        setMode(Mode.LOAD);
        final int gen = this.loadGeneration.incrementAndGet();
        ClassicSaveGames.fillLabelsAsync(list, gen, this.loadGeneration, g -> {
                if (g == this.loadGeneration.get() && this.mode == Mode.LOAD) {
                    repaint();
                }
            });
        repaint();
    }

    private void backToTitle() {
        setMode(Mode.TITLE);     // keeps the previous selection
        repaint();
    }

    /**
     * Open the quit box with "Nein" barred -- the original's {@code @DOS}
     * has {@code @default=2}, so an Escape followed by a reflexive Enter
     * never ends the session by accident.
     */
    private void openQuitBox() {
        this.quitSel = QUIT_NO;
        setMode(Mode.QUIT);
        repaint();
    }

    /**
     * The window is being closed (Alt+F4, see
     * {@code ClassicGUI.closeRequested}): ask "Colonization beenden?" the
     * same way Escape does, instead of ending the program on one key.
     *
     * @return True if the live menu took the request (the quit box is now,
     *     or already was, open); false in {@code PASSIVE} and {@code BUSY},
     *     where nothing is on screen to ask with and the caller quits.
     */
    boolean offerQuit() {
        switch (this.mode) {
        case NEW_WORLD:
            // The chain holds no engine state, so dropping it is all the
            // teardown there is; "Nein" then returns to the title.
            this.chain = null;
            this.chainData = null;
            openQuitBox();
            return true;
        case TITLE: case LOAD: case NOTICE:
            openQuitBox();
            return true;
        case QUIT:
            return true;
        default:
            return false;
        }
    }

    private String quitPrompt() {
        return Messages.message("classic.mainMenu.quitPrompt");
    }

    private List<String> quitRows() {
        final List<String> rows = new ArrayList<>(2);
        rows.add(Messages.message("classic.mainMenu.quitYes"));
        rows.add(Messages.message("classic.mainMenu.quitNo"));
        return rows;
    }

    /** Answer the quit box: "Ja" quits, anything else returns to the title. */
    private void answerQuit(int row) {
        if (row == QUIT_YES) {
            this.actions.quit();
        } else {
            backToTitle();
        }
    }

    private MenuAssets assets() {
        if (this.assets == null) this.assets = MenuAssets.fromResources();
        return this.assets;
    }

    /**
     * Use these images and font from now on instead of loading them through
     * the resource manager on the first paint (the early start-up window,
     * see {@link MenuAssets#fromPackFiles}).  EDT only.
     *
     * @param a The assets; null is ignored.
     */
    void useAssets(MenuAssets a) {
        if (a != null) this.assets = a;
    }

    /**
     * Replace what the menu items do (see {@link #actions}).  EDT only.
     *
     * @param a The new actions; null is ignored.
     */
    void setActions(Actions a) {
        if (a != null) this.actions = a;
    }

    /**
     * Whether the player is using the menu right now: the title, the load
     * box, a notice, the quit box or the new-game chain.  Not live: the
     * passive picture and the frozen BUSY / STARTING screens.
     * {@code ClassicGUI.showMainPanel} leaves a live panel alone instead of
     * resetting it to the title, so a choice made in the early start-up
     * window survives the start-up's own call to it.
     *
     * @return True in TITLE, LOAD, NOTICE, QUIT and NEW_WORLD.
     */
    boolean isLive() {
        switch (this.mode) {
        case TITLE: case LOAD: case NOTICE: case QUIT: case NEW_WORLD:
            return true;
        default:
            return false;
        }
    }

    /**
     * Whether only the passive title picture is up ({@code --fast}, a save
     * argument, or a game about to be shown, see
     * {@code ClassicGUI.closeMainPanel}).
     *
     * @return True in PASSIVE.
     */
    boolean isPassive() {
        return this.mode == Mode.PASSIVE;
    }


    // Painting

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        final Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                               RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                               RenderingHints.VALUE_ANTIALIAS_OFF);
            // Fit the 320x200 canvas at the largest whole scale (as
            // ClassicColonyPanel.paintComponent); the black ground letterboxes.
            this.scale = Math.max(1, Math.min(getWidth() / VW, getHeight() / VH));
            this.originX = (getWidth() - VW * this.scale) / 2;
            this.originY = (getHeight() - VH * this.scale) / 2;
            g.translate(this.originX, this.originY);
            g.scale(this.scale, this.scale);
            g.clipRect(0, 0, VW, VH);

            final MenuAssets a = assets();
            switch (this.mode) {
            case TITLE:
                paintTitleScreen(g, a, TITLE_LINE, this.items, this.selected);
                break;
            case LOAD:
                paintBackground(g, a);
                paintLoadBox(g, a, ClassicMenuBox.TITLE,
                    Messages.message("classic.mainMenu.loadPrompt"),
                    visibleLoadRows(a), this.loadSel - this.loadTop);
                break;
            case NOTICE: case BUSY:
                paintBackground(g, a);
                paintNotice(g, a, ClassicMenuBox.TITLE, this.noticeLines);
                break;
            case QUIT:
                paintQuitBox(g, a, quitPrompt(), quitRows(), this.quitSel);
                break;
            case DEPARTURE:
                if (this.departure != null) {
                    g.drawImage(this.departure.image, 0, 0, null);
                } else {
                    paintBackground(g, a);
                }
                break;
            case NEW_WORLD: case STARTING:
                // STARTING: the departure's last picture, or -- without
                // the departure -- the audience, frozen.
                if (this.mode == Mode.STARTING && this.frozenFrame != null) {
                    g.drawImage(this.frozenFrame, 0, 0, null);
                } else if (this.chain != null && this.chainData != null) {
                    ClassicNewWorldScreens.paint(g, this.chainData.assets,
                        this.chainData.texts, this.chain.view());
                } else {
                    paintBackground(g, a);
                }
                break;
            case PASSIVE: default:
                if (this.frozenFrame != null) {
                    g.drawImage(this.frozenFrame, 0, 0, null);
                } else {
                    paintBackground(g, a);
                }
                break;
            }
            if (drawsPointer()) paintCursor(g, a, this.pointerX, this.pointerY);
        } finally {
            g.dispose();
        }
    }


    // The original's mouse arrow

    /*
     * Why the arrow is drawn into the canvas instead of being a custom system
     * cursor: at the usual x4/x5 scale the original arrow (10x14 visible
     * pixels) is 40x56 to 50x70 screen pixels, but Windows custom cursors are
     * capped at Toolkit.getBestCursorSize -- 32x32 here, at any requested
     * size -- and Java shrinks larger images to fit, so a system cursor could
     * not be larger than x2.  Drawn in the canvas it scales exactly like
     * everything else and moves on the 320x200 grid, as in the original.
     * The system cursor is hidden only while the arrow is drawn: never in
     * the letterbox, and never in PASSIVE/BUSY, when the EDT may be blocked
     * and a drawn arrow would freeze.
     */

    /** Whether the drawn arrow replaces the system cursor right now. */
    private boolean drawsPointer() {
        return this.pointerX >= 0 && this.pointerY >= 0
            && (this.mode == Mode.TITLE || this.mode == Mode.LOAD
                || this.mode == Mode.NOTICE || this.mode == Mode.QUIT
                || this.mode == Mode.NEW_WORLD)
            && assets().cursor != null && blankCursor() != null;
    }

    /** Follow the mouse; only the old and new arrow areas are repainted. */
    private void trackPointer(MouseEvent e) {
        final int x = vx(e), y = vy(e);
        final boolean in = x >= 0 && x < VW && y >= 0 && y < VH;
        setPointer(in ? x : -1, in ? y : -1);
    }

    private void setPointer(int x, int y) {
        if (x == this.pointerX && y == this.pointerY) return;
        final boolean drew = drawsPointer();
        if (drew) repaintPointer();
        this.pointerX = x;
        this.pointerY = y;
        updatePointerShape();
        if (drawsPointer()) repaintPointer();
    }

    /** Repaint the screen area of the arrow at its current position. */
    private void repaintPointer() {
        final BufferedImage c = assets().cursor;
        if (c == null) return;
        repaint(this.originX + this.pointerX * this.scale,
                this.originY + this.pointerY * this.scale,
                c.getWidth() * this.scale, c.getHeight() * this.scale);
    }

    /**
     * Whether the pointer is hidden altogether: over the departure and the
     * frozen picture after it, where the original shows no arrow (none of
     * the 23 departure captures has one) -- the system arrow must not
     * appear there either.
     */
    private boolean hidesPointer() {
        return this.pointerX >= 0 && this.pointerY >= 0
            && (this.mode == Mode.DEPARTURE || this.mode == Mode.STARTING);
    }

    /**
     * Hide the system cursor exactly while the arrow is drawn, and over
     * the departure ({@link #hidesPointer}).
     */
    private void updatePointerShape() {
        final Cursor want = (drawsPointer() || hidesPointer()) ? blankCursor() : null;
        if (!isCursorSet() ? want != null : getCursor() != want) {
            setCursor(want);    // null: inherit the default arrow
        }
    }

    /** An invisible cursor, or null if the platform cannot make one. */
    private Cursor blankCursor() {
        if (this.blankCursor == null && !this.blankCursorFailed) {
            try {
                this.blankCursor = Toolkit.getDefaultToolkit().createCustomCursor(
                    new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
                    new Point(0, 0), "classic-none");
            } catch (RuntimeException e) {    // e.g. HeadlessException
                logger.log(Level.FINE, "No blank cursor; keeping the system arrow", e);
                this.blankCursorFailed = true;
            }
        }
        return this.blankCursor;
    }

    /** The labels of the visible load rows, cut to the box. */
    private List<String> visibleLoadRows(MenuAssets a) {
        final int n = Math.min(LOAD_VISIBLE_ROWS, this.saves.size() - this.loadTop);
        final int max = ClassicMenuBox.rowTextMaxWidth(loadBounds(n));
        final List<String> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            rows.add(ClassicMenuBox.fit(a.font,
                this.saves.get(this.loadTop + i).label, max));
        }
        return rows;
    }


    // Input

    private void installKeyBindings() {
        final InputMap im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        final ActionMap am = getActionMap();
        bind(im, am, "classic_menuUp", () -> move(-1),
             KeyEvent.VK_UP, KeyEvent.VK_KP_UP, KeyEvent.VK_NUMPAD8);
        bind(im, am, "classic_menuDown", () -> move(1),
             KeyEvent.VK_DOWN, KeyEvent.VK_KP_DOWN, KeyEvent.VK_NUMPAD2);
        bind(im, am, "classic_menuHome", () -> move(Integer.MIN_VALUE / 2),
             KeyEvent.VK_HOME, KeyEvent.VK_NUMPAD7);
        bind(im, am, "classic_menuEnd", () -> move(Integer.MAX_VALUE / 2),
             KeyEvent.VK_END, KeyEvent.VK_NUMPAD1);
        bind(im, am, "classic_menuPageUp", () -> page(-1),
             KeyEvent.VK_PAGE_UP, KeyEvent.VK_NUMPAD9);
        bind(im, am, "classic_menuPageDown", () -> page(1),
             KeyEvent.VK_PAGE_DOWN, KeyEvent.VK_NUMPAD3);
        bind(im, am, "classic_menuEnter", this::enter, KeyEvent.VK_ENTER);
        bind(im, am, "classic_menuSpace", this::space, KeyEvent.VK_SPACE);
        bind(im, am, "classic_menuEscape", this::escape, KeyEvent.VK_ESCAPE);
    }

    /**
     * Bind keys to a menu action.  In {@link Mode#NEW_WORLD} and
     * {@link Mode#DEPARTURE} the bound actions do nothing: the key listener
     * ({@link #onChainKey}, {@link #onDepartureKey}) handles and consumes
     * those keys, so nothing is handled twice.
     */
    private void bind(InputMap im, ActionMap am, String name,
                      Runnable r, int... keys) {
        for (int k : keys) im.put(KeyStroke.getKeyStroke(k, 0), name);
        am.put(name, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (ClassicMainMenuPanel.this.mode == Mode.NEW_WORLD
                        || ClassicMainMenuPanel.this.mode == Mode.DEPARTURE) return;
                    r.run();
                }
            });
    }

    /** Move the bar by {@code delta}, clamped (no wrap). */
    private void move(int delta) {
        if (this.mode == Mode.TITLE) {
            final int n = clamp((long) this.selected + delta, 0, this.items.size() - 1);
            if (n != this.selected) {
                this.selected = n;
                repaint();
            }
        } else if (this.mode == Mode.LOAD) {
            setLoadSel(clamp((long) this.loadSel + delta, 0, this.saves.size() - 1));
        } else if (this.mode == Mode.QUIT) {
            final int n = clamp((long) this.quitSel + delta, QUIT_YES, QUIT_NO);
            if (n != this.quitSel) {
                this.quitSel = n;
                repaint();
            }
        }
    }

    private void page(int dir) {
        if (this.mode == Mode.LOAD) {
            move(dir * LOAD_VISIBLE_ROWS);
        } else {
            move(dir < 0 ? Integer.MIN_VALUE / 2 : Integer.MAX_VALUE / 2);
        }
    }

    private static int clamp(long v, int lo, int hi) {
        return (int) Math.max(lo, Math.min(hi, v));
    }

    /** Select save {@code n}, scrolling so it stays in the window. */
    private void setLoadSel(int n) {
        if (n == this.loadSel) return;
        this.loadSel = n;
        if (this.loadSel < this.loadTop) this.loadTop = this.loadSel;
        if (this.loadSel >= this.loadTop + LOAD_VISIBLE_ROWS) {
            this.loadTop = this.loadSel - LOAD_VISIBLE_ROWS + 1;
        }
        repaint();
    }

    private void enter() {
        switch (this.mode) {
        case TITLE: activate(this.selected); break;
        case LOAD: loadSelected(); break;
        case NOTICE: dismissNotice(); break;
        case QUIT: answerQuit(this.quitSel); break;
        default: break;
        }
    }

    private void space() {
        if (this.mode == Mode.NOTICE) dismissNotice();
    }

    /**
     * ESC: on the title, open the quit box; in the quit box, "Nein"; back
     * elsewhere.
     *
     * <p>The original ignores Escape on its title menu, but it runs in a DOS
     * box that the player can always close.  Here the window is borderless
     * full screen (no close button), and the owner asked for an obvious way
     * out, so Escape asks "Colonization beenden?" first -- the same question
     * the original asks on its own exit ({@code @DOS}).  Escape again (or
     * "Nein") goes back, so nothing ends by a stray key.
     */
    private void escape() {
        if (this.mode == Mode.TITLE) {
            openQuitBox();
        } else if (this.mode == Mode.QUIT) {
            answerQuit(QUIT_NO);
        } else if (this.mode == Mode.LOAD) {
            backToTitle();
        } else if (this.mode == Mode.NOTICE) {
            dismissNotice();
        }
    }

    private void dismissNotice() {
        setMode(this.noticeReturn);
        repaint();
    }

    private void activate(int item) {
        switch (item) {
        case ITEM_NEW_WORLD:
            startChain();
            break;
        case ITEM_AMERICA: case ITEM_CUSTOMIZE:
            showNotice(Messages.message("classic.mainMenu.notYet"), Mode.TITLE);
            break;
        case ITEM_LOAD:
            openLoadBox();
            break;
        case ITEM_HALL_OF_FAME:
            this.actions.hallOfFame();
            break;
        default:
            break;
        }
    }

    // The new-game chain

    /**
     * Start reading the chain's art and texts in the background, once.
     * They come from plain pack files ({@link ClassicPackFiles}), not from
     * the resource manager, so this never waits for or competes with it.
     */
    private void prefetchChain() {
        if (this.chainPrefetch != null) return;
        this.chainPrefetch = new FutureTask<>(ClassicMainMenuPanel::loadChainData);
        final Thread t = new Thread(this.chainPrefetch, "classic-newworld-prefetch");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    /** Load the chain's assets and texts, or null when the pack lacks them. */
    private static ChainData loadChainData() {
        final ClassicPackFiles p = ClassicPackFiles.runtime();
        final ClassicNewWorldScreens.Assets a = ClassicNewWorldScreens.Assets.load(p);
        final ClassicNewWorldScreens.Texts t = (a == null) ? null
            : ClassicNewWorldScreens.Texts.load(ClassicText.load(p));
        return (a == null || t == null) ? null : new ChainData(a, t);
    }

    /** The prefetched chain data (joined), or null. */
    private ChainData joinChainData() {
        prefetchChain();
        try {
            return this.chainPrefetch.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            logger.log(Level.WARNING, "Classic new-game screens failed to load", e);
            return null;
        }
    }

    /**
     * NEUE WELT: open the original's new-game chain on this canvas.  A pack
     * converted before the chain's material existed has no texts: then the
     * game starts at once with FreeCol's defaults, as before, and one INFO
     * line asks for {@code ant classic-assets}.
     */
    private void startChain() {
        final ChainData cd = joinChainData();
        if (cd == null) {
            if (!chainMissLogged) {
                chainMissLogged = true;
                logger.info("Classic new-game screens unavailable (re-run ant"
                    + " classic-assets); starting with FreeCol's defaults.");
            }
            showBusy(Messages.message("classic.mainMenu.starting"));
            this.actions.newWorld(null);
            return;
        }
        this.chainData = cd;
        this.chain = ClassicNewWorldChain.create(cd.texts, cd.assets.intro);
        setMode(Mode.NEW_WORLD);
        requestFocusInWindow();
        repaint();
    }

    /**
     * The key codes held down right now (KEY_PRESSED seen, KEY_RELEASED not
     * yet), EDT only.  Another press of a held key is the keyboard's
     * auto-repeat; see {@link #onChainKey}.
     */
    private final Set<Integer> heldKeys = new HashSet<>();

    /**
     * Whether the last mouse press in the chain changed its screen; see
     * {@link #onPress}.  EDT only.
     */
    private boolean chainPressChangedScreen = false;

    /** Act on what a chain operation returned. */
    private void handleChain(ClassicNewWorldChain.Result r) {
        switch (r) {
        case REPAINT:
            repaint();
            break;
        case CANCEL:
            // Back from the first screen: the title, NEUE WELT barred.
            this.chain = null;
            this.chainData = null;
            this.selected = ITEM_NEW_WORLD;
            setMode(Mode.TITLE);
            repaint();
            break;
        case DONE:
            // The original's departure, then the engine start.  Without the
            // departure's pictures or captions: freeze the audience (no
            // arrow: the EDT now blocks while the server starts) and hand
            // the choices to the engine at once, as before.
            final ClassicGUI.NewWorldSetup setup = this.chain.setup();
            if (!startDeparture(setup)) {
                setMode(Mode.STARTING);
                paintImmediately(0, 0, getWidth(), getHeight());
                this.actions.newWorld(setup);
            }
            break;
        case STAY: default:
            break;
        }
    }

    // The departure

    /**
     * Start the departure after the audience (see {@link ClassicDeparture}).
     *
     * <p>WHY the engine starts only afterwards, not in parallel: the show
     * ends on a still picture (LEVN0010 with its caption, held for about
     * 9 s), so the engine's 1-2 s start -- which blocks this thread, see
     * {@code ClassicGUI.startNewWorldGame} -- falls on that same frozen
     * frame ({@link Mode#STARTING}).  Nothing of the engine exists while
     * the show runs: skipping, Alt+F4 and failures need no special care,
     * no autosave or turn report can arrive mid-show, the in-game view and
     * {@code closeMainPanel} need no gating, and the in-game music
     * ({@code PreGameController.startGameInternal}) begins when the ship
     * has gone, not two seconds into the night picture.  The cost is the
     * engine's start time spent on the last picture.
     *
     * <p>All eleven frames (the audience still and the ten steps) and the
     * ten dissolve orders are prepared here at once; that takes a few tens
     * of milliseconds.
     *
     * @param setup The chain's choices.
     * @return False when the departure is unavailable (pack material
     *     missing, see {@link ClassicDeparture#available}); the caller then
     *     starts the game directly.
     */
    private boolean startDeparture(ClassicGUI.NewWorldSetup setup) {
        final ChainData cd = this.chainData;
        if (cd == null || !ClassicDeparture.available(cd.assets, cd.texts)) return false;
        final ClassicDeparture.Captions c = ClassicDeparture.Captions.of(cd.texts, setup);
        if (c == null) {
            logger.info("Classic departure: no captions for " + setup.nationId
                + " / " + setup.difficultyId + " -- skipped");
            return false;
        }
        final long prep = System.nanoTime();
        final int[][] frames = new int[ClassicDeparture.STEPS + 1][];
        for (int k = 0; k <= ClassicDeparture.STEPS; k++) {
            frames[k] = ClassicDeparture.renderStep(cd.assets, cd.texts, c, k);
        }
        final int[][] orders = new int[ClassicDeparture.STEPS][];
        final int[] changed = new int[ClassicDeparture.STEPS];
        for (int k = 0; k < ClassicDeparture.STEPS; k++) {
            orders[k] = ClassicDeparture.dissolveOrder(frames[k], frames[k + 1]);
            changed[k] = orders[k].length;
        }
        final BufferedImage image = new BufferedImage(ClassicDeparture.W,
            ClassicDeparture.H, BufferedImage.TYPE_INT_RGB);
        final int[] shown = ClassicDeparture.pixels(image);
        System.arraycopy(frames[0], 0, shown, 0, shown.length);
        final ClassicDepartureTimeline tl = new ClassicDepartureTimeline(changed);
        this.departure = new Departure(setup, frames, orders, tl, image, System.nanoTime());
        this.frozenFrame = null;
        setMode(Mode.DEPARTURE);
        logger.info("Classic departure: start (prepared in "
            + (System.nanoTime() - prep) / 1000000L + " ms, about "
            + tl.endMs() / 1000L + " s)");
        this.departureTimer = new Timer(DEPARTURE_TICK_MS, ev -> departureTick());
        this.departureTimer.setCoalesce(true);
        this.departureTimer.start();
        requestFocusInWindow();
        repaint();
        return true;
    }

    /**
     * One timer tick: bring the screen up to the timeline's state.  Every
     * transition before the current one is completed first, so a stalled
     * event thread only skips ahead, never lags behind.
     */
    private void departureTick() {
        final Departure d = this.departure;
        if (this.mode != Mode.DEPARTURE || d == null) {
            stopDepartureTimer();
            return;
        }
        final ClassicDepartureTimeline.State st = d.timeline.at(d.elapsedMs());
        boolean changed = false;
        while (d.appliedTransition < st.transition) {
            final int[] order = d.orders[d.appliedTransition];
            if (d.appliedCount < order.length) {
                ClassicDeparture.apply(d.shown, d.frames[d.appliedTransition + 1],
                                       order, d.appliedCount, order.length);
                changed = true;
            }
            d.appliedTransition++;
            d.appliedCount = 0;
        }
        final int[] order = d.orders[d.appliedTransition];
        final int target = st.done ? order.length : st.revealed;
        if (target > d.appliedCount) {
            ClassicDeparture.apply(d.shown, d.frames[d.appliedTransition + 1],
                                   order, d.appliedCount, target);
            d.appliedCount = target;
            changed = true;
        }
        if (changed) repaint();
        if (st.done) finishDeparture(false);
    }

    /** A key or click ends the show at once (see {@link #onDepartureKey}). */
    private void skipDeparture() {
        final Departure d = this.departure;
        if (this.mode != Mode.DEPARTURE || d == null) return;
        d.timeline.skip();
        finishDeparture(true);
    }

    /**
     * The show is over (or skipped): the last picture stays up, frozen,
     * and the engine starts -- the unchanged path of a chain without the
     * departure ({@code ClassicGUI.beginNewWorldSetup}).
     */
    private void finishDeparture(boolean skipped) {
        final Departure d = this.departure;
        if (this.mode != Mode.DEPARTURE || d == null) return;
        stopDepartureTimer();
        final int[] last = d.frames[ClassicDeparture.STEPS];
        System.arraycopy(last, 0, d.shown, 0, d.shown.length);
        logger.info("Classic departure: finished after " + d.elapsedMs()
            + " ms (skipped=" + skipped + ")");
        this.frozenFrame = d.image;
        setMode(Mode.STARTING);
        paintImmediately(0, 0, getWidth(), getHeight());
        this.actions.newWorld(d.setup);
    }

    private void stopDepartureTimer() {
        if (this.departureTimer != null) {
            this.departureTimer.stop();
            this.departureTimer = null;
        }
    }

    /**
     * A key press during the departure.  How the original reacts is not
     * known (stills cannot show it); this is a PROPOSAL: any new press
     * skips the rest of the show, Escape included -- there is no way back
     * to the audience.  Left alone: chords with Alt, Ctrl or Meta (Alt+Enter
     * full screen and Alt+F4 quit are handled by {@code ClassicGUI}'s
     * frame keys), bare modifiers, and the auto-repeat of a key still held
     * from the audience ({@code repeat}), so a held Enter cannot swallow the
     * show -- one physical press, at most one screen, as in the chain.
     *
     * @param e The event.
     * @param repeat Whether the key was already held (auto-repeat).
     */
    private void onDepartureKey(KeyEvent e, boolean repeat) {
        if (this.mode != Mode.DEPARTURE) return;
        if (e.isAltDown() || e.isControlDown() || e.isMetaDown()) return;
        e.consume();
        if (repeat || isModifierKey(e.getKeyCode())) return;
        skipDeparture();
    }

    /** Whether a key code is a bare modifier or lock key. */
    private static boolean isModifierKey(int k) {
        switch (k) {
        case KeyEvent.VK_SHIFT: case KeyEvent.VK_CONTROL: case KeyEvent.VK_ALT:
        case KeyEvent.VK_META: case KeyEvent.VK_WINDOWS: case KeyEvent.VK_ALT_GRAPH:
        case KeyEvent.VK_CAPS_LOCK: case KeyEvent.VK_NUM_LOCK:
        case KeyEvent.VK_SCROLL_LOCK: case KeyEvent.VK_CONTEXT_MENU:
            return true;
        default:
            return false;
        }
    }

    /**
     * A key press in the chain.  Chords with Alt, Ctrl or Meta are left
     * alone, so Alt+Enter (full screen) and Alt+F4 (quit box) still reach
     * {@code ClassicGUI.installFrameKeys}; every other press is consumed,
     * which also keeps the menu's own key bindings out.
     *
     * <ul>
     *   <li>Pickers: Left/Up/keypad 4/8 = previous, Right/Down/keypad 6/2 =
     *       next in reading order, Home/End = first/last, Enter = confirm.</li>
     *   <li>Name: Enter = confirm, Backspace = delete (typing arrives via
     *       {@link #onChainTyped}).</li>
     *   <li>Pages and audience: any key but a bare modifier advances.</li>
     *   <li>Everywhere: Escape = one screen back.</li>
     * </ul>
     *
     * <p><b>Auto-repeat never changes the screen.</b>  A held key repeats its
     * press many times a second, and every screen of the chain is left by a
     * single press: a held Enter would confirm the difficulty and then
     * England (each nation screen opens on England) -- the wrong nation for
     * a family that always plays Holland -- and a held key on the pages
     * would skip the audience.  So a repeated press ({@code repeat}) only
     * does what is harmless to repeat: stepping through a picker, and
     * Backspace on the name.  Enter, Escape and the "any key" of the pages
     * act once per physical press.
     *
     * @param e The event.
     * @param repeat Whether the key was already held (auto-repeat).
     */
    private void onChainKey(KeyEvent e, boolean repeat) {
        if (this.mode != Mode.NEW_WORLD || this.chain == null) return;
        if (e.isAltDown() || e.isControlDown() || e.isMetaDown()) return;
        final int k = e.getKeyCode();
        if (repeat) {
            final ClassicNewWorldChain.Step step = this.chain.step();
            final boolean harmless = (step == ClassicNewWorldChain.Step.NAME)
                ? k == KeyEvent.VK_BACK_SPACE
                : !this.chain.advancesOnAnyInput()
                    && k != KeyEvent.VK_ENTER && k != KeyEvent.VK_ESCAPE;
            if (!harmless) {
                e.consume();
                return;
            }
        }
        final ClassicNewWorldChain.Result r;
        if (k == KeyEvent.VK_ESCAPE) {
            r = this.chain.back();
        } else if (this.chain.advancesOnAnyInput()) {
            r = isModifierKey(k) ? ClassicNewWorldChain.Result.STAY : this.chain.anyKey();
        } else if (this.chain.step() == ClassicNewWorldChain.Step.NAME) {
            r = (k == KeyEvent.VK_ENTER) ? this.chain.confirm()
                : (k == KeyEvent.VK_BACK_SPACE) ? this.chain.backspace()
                : ClassicNewWorldChain.Result.STAY;
        } else {
            switch (k) {
            case KeyEvent.VK_LEFT: case KeyEvent.VK_KP_LEFT: case KeyEvent.VK_NUMPAD4:
            case KeyEvent.VK_UP: case KeyEvent.VK_KP_UP: case KeyEvent.VK_NUMPAD8:
                r = this.chain.prev();
                break;
            case KeyEvent.VK_RIGHT: case KeyEvent.VK_KP_RIGHT: case KeyEvent.VK_NUMPAD6:
            case KeyEvent.VK_DOWN: case KeyEvent.VK_KP_DOWN: case KeyEvent.VK_NUMPAD2:
                r = this.chain.next();
                break;
            case KeyEvent.VK_HOME: case KeyEvent.VK_NUMPAD7:
                r = this.chain.first();
                break;
            case KeyEvent.VK_END: case KeyEvent.VK_NUMPAD1:
                r = this.chain.last();
                break;
            case KeyEvent.VK_ENTER:
                r = this.chain.confirm();
                break;
            default:
                r = ClassicNewWorldChain.Result.STAY;
                break;
            }
        }
        e.consume();
        handleChain(r);
    }

    /** A typed character in the chain: only the name screen takes it. */
    private void onChainTyped(KeyEvent e) {
        if (this.mode != Mode.NEW_WORLD || this.chain == null
            || this.chain.step() != ClassicNewWorldChain.Step.NAME) return;
        final char c = e.getKeyChar();
        if (c == KeyEvent.CHAR_UNDEFINED) return;
        e.consume();
        handleChain(this.chain.typed(c));
    }

    private void loadSelected() {
        if (this.loadSel < 0 || this.loadSel >= this.saves.size()) return;
        final File file = this.saves.get(this.loadSel).file;
        showBusy(Messages.message("classic.mainMenu.loading"));
        this.actions.loadGame(file);
    }

    /** The virtual x of a mouse event (floorDiv: exact in the letterbox). */
    private int vx(MouseEvent e) {
        return Math.floorDiv(e.getX() - this.originX, this.scale);
    }

    private int vy(MouseEvent e) {
        return Math.floorDiv(e.getY() - this.originY, this.scale);
    }

    /** The title item under a virtual point, or -1. */
    private int titleItemAt(int x, int y) {
        final Rectangle b = titleBounds(this.items.size());
        for (int i = 0; i < this.items.size(); i++) {
            if (ClassicMenuBox.rowHitRect(b, 1, i).contains(x, y)) return i;
        }
        return -1;
    }

    /** The quit-box row under a virtual point, or -1. */
    private int quitRowAt(int x, int y) {
        final Rectangle b = quitBounds(assets(), quitPrompt());
        for (int i = QUIT_YES; i <= QUIT_NO; i++) {
            if (ClassicMenuBox.rowHitRect(b, 1, i).contains(x, y)) return i;
        }
        return -1;
    }

    /** The save index under a virtual point, or -1. */
    private int loadRowAt(int x, int y) {
        final int n = Math.min(LOAD_VISIBLE_ROWS, this.saves.size() - this.loadTop);
        final Rectangle b = loadBounds(n);
        for (int i = 0; i < n; i++) {
            if (ClassicMenuBox.rowHitRect(b, 1, i).contains(x, y)) {
                return this.loadTop + i;
            }
        }
        return -1;
    }

    private void onHover(MouseEvent e) {
        final int x = vx(e), y = vy(e);
        if (this.mode == Mode.TITLE) {
            final int i = titleItemAt(x, y);
            if (i >= 0 && i != this.selected) {
                this.selected = i;
                repaint();
            }
        } else if (this.mode == Mode.LOAD) {
            final int i = loadRowAt(x, y);
            if (i >= 0) setLoadSel(i);
        } else if (this.mode == Mode.QUIT) {
            final int i = quitRowAt(x, y);
            if (i >= 0 && i != this.quitSel) {
                this.quitSel = i;
                repaint();
            }
        }
    }

    private void onPress(MouseEvent e) {
        requestFocusInWindow();
        final int x = vx(e), y = vy(e);
        switch (this.mode) {
        case NEW_WORLD:
            if (this.chain == null) break;
            // The second press of a double click must not act on the screen
            // the first one opened: the difficulty card under the pointer
            // lies inside the England card of the nation screen (which opens
            // on England), so double-clicking the highlighted card would
            // confirm England too; on the pages it would skip the audience.
            // Only a press that changed the screen makes the rest of its
            // click series inert, so double-clicking a card that is not yet
            // selected still selects and then confirms it.
            if (e.getClickCount() > 1 && this.chainPressChangedScreen) break;
            if (e.getButton() == MouseEvent.BUTTON1
                || e.getButton() == MouseEvent.BUTTON3) {
                final ClassicNewWorldChain.Step before = this.chain.step();
                final ClassicNewWorldChain.Result r
                    = (e.getButton() == MouseEvent.BUTTON1)
                    ? this.chain.clickPrimary(x, y)
                    : this.chain.clickSecondary(x, y);
                this.chainPressChangedScreen
                    = r == ClassicNewWorldChain.Result.DONE
                    || r == ClassicNewWorldChain.Result.CANCEL
                    || this.chain.step() != before;
                handleChain(r);
            }
            break;
        case DEPARTURE:
            // Left or right press skips -- but not the rest of the click
            // series that dismissed the audience (a double click there must
            // not swallow the whole show), the same rule as above.
            if (e.getClickCount() > 1 && this.chainPressChangedScreen) break;
            if (e.getButton() == MouseEvent.BUTTON1
                || e.getButton() == MouseEvent.BUTTON3) {
                this.chainPressChangedScreen = true;
                skipDeparture();
            }
            break;
        case TITLE:
            if (e.getButton() == MouseEvent.BUTTON1) {
                final int i = titleItemAt(x, y);
                if (i >= 0) {
                    this.selected = i;
                    activate(i);
                }
            }
            break;
        case LOAD:
            if (e.getButton() == MouseEvent.BUTTON3) {
                backToTitle();
            } else if (e.getButton() == MouseEvent.BUTTON1) {
                final int i = loadRowAt(x, y);
                if (i >= 0) {
                    setLoadSel(i);
                    loadSelected();
                }
            }
            break;
        case NOTICE:
            dismissNotice();
            break;
        case QUIT:
            // Like the load box: a right click is "back" (= Nein), a left
            // click answers with the row under the arrow; elsewhere nothing.
            if (e.getButton() == MouseEvent.BUTTON3) {
                answerQuit(QUIT_NO);
            } else if (e.getButton() == MouseEvent.BUTTON1) {
                final int i = quitRowAt(x, y);
                if (i >= 0) {
                    this.quitSel = i;
                    answerQuit(i);
                }
            }
            break;
        default:
            break;      // PASSIVE, BUSY: ignored
        }
    }

    /**
     * One row per wheel notch.  The precise rotation is summed, because a
     * touchpad's gentle scroll arrives as fractions whose
     * {@code getWheelRotation()} stays 0.
     */
    private void onWheel(MouseWheelEvent e) {
        if (this.mode != Mode.LOAD) {
            this.wheelRest = 0.0;
            return;
        }
        this.wheelRest += e.getPreciseWheelRotation();
        while (this.wheelRest <= -1.0) {
            this.wheelRest += 1.0;
            move(-1);
        }
        while (this.wheelRest >= 1.0) {
            this.wheelRest -= 1.0;
            move(1);
        }
    }
}
