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
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import net.sf.freecol.common.i18n.Messages;


/**
 * The shared frame for every classic-UI <b>popup</b> — the original 1994
 * <em>Colonization</em>'s wood-framed message and question boxes.
 *
 * <p><b>Since build spec W7 the stopgap only:</b> in the game every
 * question, choice and notice is the original's advisor box in the canvas
 * ({@link ClassicAdvisorBox}, {@link ClassicAdvisorLayer}).  This popup
 * stays where the canvas is not what the player looks at: the title
 * screens, a colony, Europe or report screen in front, a pack without
 * FONTTINY ({@code ClassicGUI.putBox}).
 *
 * <p>The classic UI has no {@code Canvas}, so each popup is hosted in a modal
 * {@link JDialog} of its own -- undecorated while the game runs in
 * borderless full screen, decorated in a window
 * ({@code ClassicGUI.prepareChildWindow}).  As with {@link ClassicReportPanel}, everything is
 * painted into a <b>virtual pixel canvas</b> up-scaled by an integer factor,
 * nearest-neighbour, so layout constants read straight off the original's
 * screenshots and the classic pixels stay crisp: {@link ClassicWood} grain under
 * a raised border, an optional illustration at the left, wrapped green-on-wood
 * text, and a row of raised option plates.
 *
 * <p>Every classic popup routes through here rather than growing its own look —
 * the deliberate "build it once" of the plan's Phase 3.  Two entry points share
 * the painting:
 * <ul>
 *   <li>{@link #showMessages} — page through {@code n} notices, one at a time
 *       (as the original does; it has no batched "turn report" screen), each
 *       dismissed with a single <em>Okay</em> plate.</li>
 *   <li>{@link #ask} — put a question with {@code n} option plates and report
 *       which was chosen.</li>
 * </ul>
 * Both block until dismissed, so both must be called on the event dispatch
 * thread (the modal {@code JDialog} pumps events while blocked).
 *
 * <p><b>Awaiting expert sign-off:</b> the exact popup metrics and the
 * green-on-wood palette below are read off the original's screenshots by eye,
 * not measured from the art — like the Colony Advisor's paging keys, they are a
 * considered guess until the expert validates them.
 */
final class ClassicDialog extends JPanel {

    /** One popup page: its text, and the illustration to its left (optional). */
    static final class Page {

        final String text;
        final Image icon;

        Page(String text, Image icon) {
            this.text = (text == null) ? "" : text;
            this.icon = icon;
        }
    }

    /** Virtual-canvas width of the popup box. */
    private static final int VW = 240;

    /** Up-scale factor of the virtual canvas, matching the report screens. */
    private static final int SCALE = 3;

    private static final int PAD = 10;
    private static final int BORDER = 3;

    /** Side of the illustration box, and the gap to the text column. */
    private static final int ICON = 40;
    private static final int ICON_GAP = 8;

    private static final int LINE_H = 9;

    /** Body text size, in virtual pixels — matches the report screens. */
    private static final int TEXT_SIZE = 7;

    private static final int BTN_H = 12;
    private static final int BTN_GAP = 5;
    private static final int BTN_PAD = 6;
    private static final int BTN_TOP_GAP = 8;

    /**
     * Package-visible (not {@code private}): {@link ClassicGUI}'s menu-dropdown
     * reskin reuses this palette rather than inventing a second one — see
     * {@code ClassicGUI.installClassicMenuDropdownDefaults}.
     */
    static final Color WOOD_FALLBACK = new Color(0x5A, 0x3A, 0x1E);
    /** The original's popup text: a light green over the wood. */
    static final Color TEXT_FG = new Color(0x78, 0xC8, 0x60);
    static final Color BORDER_HI = new Color(0x9A, 0x72, 0x40);
    static final Color BORDER_LO = new Color(0x2A, 0x1A, 0x0C);
    static final Color BTN_BG = new Color(0x3A, 0x24, 0x12);
    static final Color BTN_FG = new Color(0xF0, 0xD8, 0x8C);
    private static final Color BTN_HOT = new Color(0x5A, 0x3A, 0x1E);
    private static final Color ICON_BG = new Color(0x14, 0x10, 0x0A);
    static final Color COUNT_FG = new Color(0xB0, 0x98, 0x60);

    /**
     * Told when a popup opens and when it has closed: the map holds its
     * blink ON while a box is up and restarts it ON at the close (build
     * spec W3).  Set for the in-game view, else null.  EDT only.
     */
    interface Watcher {

        /** A popup is about to open. */
        void opened();

        /** A popup has closed. */
        void closed();
    }

    /** The watcher of every popup, or null ({@link #setWatcher}). */
    private static Watcher watcher = null;

    private final List<Page> pages;
    private final String[] options;

    /** Index of the page on show; pages are stepped through by the Okay plate. */
    private int page;

    /** The option index chosen, or -1 while none is (and if dismissed). */
    private int chosen = -1;

    /** The dialog hosting this panel, disposed when a choice is made. */
    private JDialog dialog;

    /** Virtual-space bounds of each option plate, rebuilt every paint. */
    private final List<Rectangle> buttonBounds = new ArrayList<>();

    /**
     * Index of the plate the left button went down on and is still held
     * over, or -1 (build spec W20: the press marks, the release on the same
     * plate takes it; the pointer alone never lights a plate).
     */
    private int pressed = -1;

    /** Device-space origin of the virtual canvas, set on each paint. */
    private int originX;
    private int originY;

    /** Virtual-canvas height, computed from the content by {@link #layoutHeight}. */
    private final int vh;


    /**
     * A popup's panel.  Package-private for the tests: the panel can be
     * built headless, its {@code JDialog} cannot.
     *
     * @param pages The pages.
     * @param options The option plates.
     * @param defaultIndex The option {@code Enter} picks.
     * @param cancellable Whether Escape dismisses it; else Escape does
     *     nothing (the King's decision boxes, the father box).
     */
    ClassicDialog(List<Page> pages, String[] options, int defaultIndex,
                  boolean cancellable) {
        this.pages = pages;
        this.options = options;
        this.page = 0;
        this.vh = layoutHeight();
        setOpaque(true);
        setBackground(Color.BLACK);
        setPreferredSize(new Dimension(VW * SCALE, this.vh * SCALE));
        setFocusable(true);
        installKeyBindings(defaultIndex, cancellable);
        addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    onPress(e);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    onRelease(e);
                }
            });
    }


    // Entry points

    /**
     * Page through {@code pages} of notices, blocking until the last is
     * dismissed.  Does nothing when {@code pages} is empty.
     *
     * @param owner The window to centre on.
     * @param title The dialog window's title.
     * @param pages The notices, shown one at a time in order.
     */
    static void showMessages(Window owner, String title, List<Page> pages) {
        if (pages == null || pages.isEmpty()) return;
        show(owner, title, pages, new String[] { Messages.message("ok") }, 0,
             true);
    }

    /**
     * Put a question, blocking until it is answered.
     *
     * @param owner The window to centre on.
     * @param title The dialog window's title.
     * @param page The question and its illustration.
     * @param options The option plates, left to right.
     * @param defaultIndex The option {@code Enter} picks.
     * @return The index into {@code options} chosen, or {@code -1} if the popup
     *     was dismissed without choosing (Escape / the window close button).
     */
    static int ask(Window owner, String title, Page page, String[] options,
                   int defaultIndex) {
        return ask(owner, title, page, options, defaultIndex, true);
    }

    /**
     * Put a question, blocking until it is answered.  One that is not
     * {@code cancellable} ignores Escape and the window's close button:
     * only a plate, or Enter, answers it.
     *
     * @param owner The window to centre on.
     * @param title The dialog window's title.
     * @param page The question and its illustration.
     * @param options The option plates, left to right.
     * @param defaultIndex The option {@code Enter} picks.
     * @param cancellable Whether Escape and the close button dismiss it.
     * @return The index into {@code options} chosen, or {@code -1} if the
     *     popup was dismissed without choosing.
     */
    static int ask(Window owner, String title, Page page, String[] options,
                   int defaultIndex, boolean cancellable) {
        return show(owner, title, List.of(page), options, defaultIndex,
                    cancellable);
    }

    /**
     * Frame a popup and block until it is dismissed.  Multi-page popups step
     * through their pages on any option plate and only report the choice made on
     * the <em>last</em> page — so a popup that both pages and asks is not
     * meaningful, and the entry points above never build one.
     */
    private static int show(Window owner, String title, List<Page> pages,
                            String[] options, int defaultIndex,
                            boolean cancellable) {
        final ClassicDialog p = new ClassicDialog(pages, options, defaultIndex,
                                                  cancellable);
        final JDialog d = new JDialog(owner, title, JDialog.ModalityType.APPLICATION_MODAL);
        p.dialog = d;
        d.setDefaultCloseOperation((cancellable)
            ? WindowConstants.DISPOSE_ON_CLOSE
            : WindowConstants.DO_NOTHING_ON_CLOSE);
        d.setContentPane(p);
        d.setResizable(false);
        // Undecorated and centred over the owner in full screen (the popup
        // paints its own wood frame, so OS chrome would only double it);
        // decorated as before in a window.  See ClassicGUI.prepareChildWindow.
        ClassicGUI.prepareChildWindow(d, owner, false);
        SwingUtilities.invokeLater(p::requestFocusInWindow);
        if (ClassicFrameRecorder.on()) {
            final String text = pages.isEmpty() ? "" : pages.get(0).text;
            ClassicFrameRecorder.event("dialog-open", title + ": "
                + text.substring(0, Math.min(120, text.length())));
        }
        popupOpened();
        try {
            d.setVisible(true);   // blocks until disposed
        } finally {
            if (ClassicFrameRecorder.on()) {
                ClassicFrameRecorder.event("dialog-close", title + " chosen=" + p.chosen);
            }
            popupClosed();
        }
        return p.chosen;
    }

    /**
     * Set the watcher told of every popup ({@link Watcher}).
     *
     * @param w The watcher, or null for none.
     */
    static void setWatcher(Watcher w) {
        watcher = w;
    }

    /**
     * Tell the watcher a popup is about to open: every classic popup, and
     * the other modal boxes of the classic UI (the selection list of
     * {@code ClassicGUI.chooseFromList}).  EDT only.
     */
    static void popupOpened() {
        final Watcher w = watcher;
        if (w != null) w.opened();
    }

    /** Tell the watcher a popup has closed.  EDT only. */
    static void popupClosed() {
        final Watcher w = watcher;
        if (w != null) w.closed();
    }


    // Input

    private void installKeyBindings(int defaultIndex, boolean cancellable) {
        final InputMap im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        final ActionMap am = getActionMap();
        if (cancellable) {
            im.put(KeyStroke.getKeyStroke("ESCAPE"), "classic_dialogCancel");
            am.put("classic_dialogCancel", new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        close(-1);
                    }
                });
        }
        im.put(KeyStroke.getKeyStroke("ENTER"), "classic_dialogDefault");
        am.put("classic_dialogDefault", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    pick(defaultIndex);
                }
            });
    }

    /** A left press on a plate marks it (W20). */
    void onPress(MouseEvent e) {
        if (!SwingUtilities.isLeftMouseButton(e)) return;
        final int i = buttonAt(e);
        if (i == this.pressed) return;
        this.pressed = i;
        repaint();
    }

    /** The release on the marked plate takes it; anywhere else it unmarks. */
    void onRelease(MouseEvent e) {
        if (!SwingUtilities.isLeftMouseButton(e)) return;
        final int i = this.pressed;
        this.pressed = -1;
        if (i >= 0 && buttonAt(e) == i) {
            pick(i);
        } else if (i >= 0) {
            repaint();
        }
    }

    /** @return The plate marked by a press, or -1 (tests). */
    int pressedPlate() {
        return this.pressed;
    }

    /** @return The option chosen, or -1 while none is (tests). */
    int chosen() {
        return this.chosen;
    }

    /**
     * @param i The plate.
     * @return Plate {@code i} in component pixels as the last paint laid it
     *     out, or null (tests).
     */
    Rectangle plateBounds(int i) {
        if (i < 0 || i >= this.buttonBounds.size()) return null;
        final Rectangle r = this.buttonBounds.get(i);
        return new Rectangle(this.originX + r.x * SCALE, this.originY + r.y * SCALE,
                             r.width * SCALE, r.height * SCALE);
    }

    /** The option plate under {@code e}, or -1. */
    private int buttonAt(MouseEvent e) {
        final int vx = (e.getX() - this.originX) / SCALE;
        final int vy = (e.getY() - this.originY) / SCALE;
        for (int i = 0; i < this.buttonBounds.size(); i++) {
            if (this.buttonBounds.get(i).contains(vx, vy)) return i;
        }
        return -1;
    }

    /** Take option {@code i}: step to the next page, or close reporting it. */
    private void pick(int i) {
        if (i < 0 || i >= this.options.length) return;
        if (this.page < this.pages.size() - 1) {
            this.page++;
            this.pressed = -1;
            repaint();
        } else {
            close(i);
        }
    }

    private void close(int result) {
        this.chosen = result;
        final JDialog d = this.dialog;
        this.dialog = null;
        if (d != null) d.dispose();
    }


    // Layout

    /**
     * The virtual-canvas height this popup needs: the tallest page's text block
     * (or the illustration, whichever is deeper) plus the option row.
     */
    private int layoutHeight() {
        int lines = 1;
        boolean icon = false;
        for (Page p : this.pages) {
            lines = Math.max(lines, wrap(p.text).size());
            icon |= (p.icon != null);
        }
        final int body = Math.max(lines * LINE_H, icon ? ICON : 0);
        return PAD + body + BTN_TOP_GAP + BTN_H + PAD;
    }

    /** Virtual-space x where the text column starts on {@code page}. */
    private int textX() {
        return PAD + (anyIcon() ? ICON + ICON_GAP : 0);
    }

    private boolean anyIcon() {
        for (Page p : this.pages) if (p.icon != null) return true;
        return false;
    }

    /**
     * Break {@code text} into lines that fit the text column.  Measured with the
     * virtual-size font, since that is the space being filled.
     */
    private List<String> wrap(String text) {
        final List<String> out = new ArrayList<>();
        final FontMetrics fm = getFontMetrics(font(Font.PLAIN));
        final int width = VW - textX() - PAD;
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (word.isEmpty()) continue;
            final String probe = (line.length() == 0)
                ? word : line + " " + word;
            if (fm.stringWidth(probe) <= width || line.length() == 0) {
                line.setLength(0);
                line.append(probe);
            } else {
                out.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (line.length() > 0) out.add(line.toString());
        if (out.isEmpty()) out.add("");
        return out;
    }

    private Font font(int style) {
        return getFont().deriveFont(style, (float) TEXT_SIZE);
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

        this.originX = (getWidth() - VW * SCALE) / 2;
        this.originY = (getHeight() - this.vh * SCALE) / 2;
        g.translate(this.originX, this.originY);
        g.scale(SCALE, SCALE);
        g.clipRect(0, 0, VW, this.vh);

        paintChrome(g);
        final Page p = this.pages.get(this.page);
        paintIcon(g, p);
        paintText(g, p);
        paintButtons(g);
        paintPageCount(g);
        g.dispose();
    }

    /** The wood ground under a raised border. */
    private void paintChrome(Graphics2D g) {
        if (!ClassicWood.paint(g, 0, 0, VW, this.vh)) {
            g.setColor(WOOD_FALLBACK);
            g.fillRect(0, 0, VW, this.vh);
        }
        for (int i = 0; i < BORDER; i++) {
            g.setColor((i == BORDER - 1) ? BORDER_LO : BORDER_HI);
            g.drawRect(i, i, VW - 1 - 2 * i, this.vh - 1 - 2 * i);
        }
    }

    /** The message's illustration, in a sunken plate at the left. */
    private void paintIcon(Graphics2D g, Page p) {
        if (p.icon == null) return;
        g.setColor(ICON_BG);
        g.fillRect(PAD, PAD, ICON, ICON);
        final int w = p.icon.getWidth(null);
        final int h = p.icon.getHeight(null);
        if (w <= 0 || h <= 0) return;
        final double s = Math.min((double) ICON / w, (double) ICON / h);
        final int dw = Math.max(1, (int) Math.round(w * s));
        final int dh = Math.max(1, (int) Math.round(h * s));
        g.drawImage(p.icon, PAD + (ICON - dw) / 2, PAD + (ICON - dh) / 2,
                    dw, dh, null);
    }

    /** The wrapped green-on-wood body text. */
    private void paintText(Graphics2D g, Page p) {
        g.setFont(font(Font.BOLD));
        g.setColor(TEXT_FG);
        int y = PAD + LINE_H - 2;
        for (String line : wrap(p.text)) {
            g.drawString(line, textX(), y);
            y += LINE_H;
        }
    }

    /** The raised option plates, centred in a row along the bottom. */
    private void paintButtons(Graphics2D g) {
        this.buttonBounds.clear();
        g.setFont(font(Font.BOLD));
        final FontMetrics fm = g.getFontMetrics();
        final int[] w = new int[this.options.length];
        int total = 0;
        for (int i = 0; i < this.options.length; i++) {
            w[i] = fm.stringWidth(this.options[i]) + 2 * BTN_PAD;
            total += w[i];
        }
        total += BTN_GAP * (this.options.length - 1);

        int x = (VW - total) / 2;
        final int y = this.vh - PAD - BTN_H;
        for (int i = 0; i < this.options.length; i++) {
            final Rectangle r = new Rectangle(x, y, w[i], BTN_H);
            this.buttonBounds.add(r);
            g.setColor((i == this.pressed) ? BTN_HOT : BTN_BG);
            g.fillRect(r.x, r.y, r.width, r.height);
            g.setColor(BORDER_HI);
            g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
            g.setColor(BTN_FG);
            g.drawString(this.options[i], r.x + BTN_PAD, r.y + BTN_H - 4);
            x += w[i] + BTN_GAP;
        }
    }

    /** "i / n" at the bottom left while paging through several notices. */
    private void paintPageCount(Graphics2D g) {
        if (this.pages.size() < 2) return;
        g.setFont(font(Font.PLAIN));
        g.setColor(COUNT_FG);
        g.drawString((this.page + 1) + "/" + this.pages.size(),
                     PAD, this.vh - PAD - 3);
    }
}
