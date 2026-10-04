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
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

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
 * {@link Mode#BUSY} and {@link Mode#QUIT} (the "Colonization beenden?" box
 * that Escape opens on the title -- in borderless full screen there is no
 * window close button, see {@code ClassicGUI.applyFrameMode} -- and that
 * Alt+F4 opens too, see {@link #offerQuit}).  In PASSIVE
 * and BUSY all input is ignored -- that is the double-start guard, as the
 * EDT is free between login and the in-game view.
 *
 * <p>The pointer is the original's own arrow ({@code CURSOR.SS.000}), drawn
 * into the canvas at the canvas scale -- see {@link #paintCursor}.
 */
final class ClassicMainMenuPanel extends JPanel {

    private static final Logger logger = Logger.getLogger(ClassicMainMenuPanel.class.getName());

    /** What the menu items do; implemented by {@link ClassicGUI}. */
    interface Actions {
        /** "Ein Spiel in der NEUEN WELT starten". */
        void newWorld();
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
    enum Mode { PASSIVE, TITLE, LOAD, NOTICE, BUSY, QUIT }

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

    private final Actions actions;

    /** Loaded on the first paint, then kept. */
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
    }


    // Mode changes

    /**
     * Switch mode.  Leaving the load box stops its label thread, and the
     * pointer shape follows the mode (see {@link #updatePointerShape}).
     */
    private void setMode(Mode m) {
        if (this.mode == Mode.LOAD && m != Mode.LOAD) {
            this.loadGeneration.incrementAndGet();
        }
        this.mode = m;
        updatePointerShape();
    }

    /** Just the picture; all input ignored. */
    void showPassive() {
        setMode(Mode.PASSIVE);
        repaint();
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
            case PASSIVE: default:
                paintBackground(g, a);
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
                || this.mode == Mode.NOTICE || this.mode == Mode.QUIT)
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

    /** Hide the system cursor exactly while the arrow is drawn. */
    private void updatePointerShape() {
        final Cursor want = drawsPointer() ? blankCursor() : null;
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

    private static void bind(InputMap im, ActionMap am, String name,
                             Runnable r, int... keys) {
        for (int k : keys) im.put(KeyStroke.getKeyStroke(k, 0), name);
        am.put(name, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
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
            showBusy(Messages.message("classic.mainMenu.starting"));
            this.actions.newWorld();
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
