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

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;


/**
 * The original's <b>menu strip</b> at the top of the in-game screen, and
 * its dropdowns: painted from the pack (MENU.TXT, FONTTINY, the wood tile)
 * by {@link ClassicMenuBar} and {@link ClassicMenuBox}, wired to the engine's
 * {@code FreeColAction}s through {@link ClassicMenuModel}.  It replaces
 * FreeCol's Swing menu bar, which could only ever look like a Swing menu.
 *
 * <p>The strip itself is this 320x8 component; the open dropdown is painted
 * by a companion layer ({@link #dropLayer}) that the HUD pane puts over the
 * whole canvas on its popup layer, because the dropdown hangs over the map.
 * Both paint their 320x200 pixels into an off-screen picture and scale it
 * up nearest-neighbour, so the screen shows exactly what the headless
 * preview diffs against the captures.  Enabled and visible states are read
 * at paint time, so a menu always shows the live state of the actions.
 *
 * <p><b>Rows</b> are drawn as {@link ClassicMenuModel} says: grey only for a
 * BEFEHLE order that does not fit right now, normal ink for everything else
 * -- including <em>inert</em> rows (no engine equivalent, or a classic
 * screen that does not exist yet), which look like the original's but only
 * say "follows later" when fired ({@link Host#unavailable}).
 *
 * <p><b>Mouse</b> (captures 001-005 were opened with the mouse: no bar): a
 * press on a title opens its menu, or closes it when it is open; moving
 * over a normal-ink row puts the bar on it; releasing over one fires it (so
 * a press on the title, a drag and a release work as well as two clicks);
 * a press anywhere else closes the menu and is swallowed.
 *
 * <p><b>Keyboard</b> (the manual; capture 053 was opened with Alt+G: row 0
 * barred, and row 0 of SPIEL is an inert row) Alt plus a title's gold letter
 * opens that menu with its first normal-ink row barred.  While a menu is open
 * a {@code KeyEventDispatcher} takes every key: Up/Down move the bar over the
 * normal-ink rows (skipping separators and grey rows -- assumed), Left/Right
 * switch to the neighbouring menu (assumed), Enter fires, Escape or a bare
 * Alt closes, a row's gold letter (only the keys A-Z: numpad and F-key codes
 * are not letters, see {@link #letterOf}) or its printed key (F2, Shift-D
 * ...) fires that row, Alt plus another title letter switches menus;
 * everything else is swallowed.  Alt+Enter and Alt+F4 are the frame's
 * ({@code ClassicGUI}'s {@code FrameKeys} runs first).
 *
 * <p><b>Firing</b> closes the menu and then, on a later event-queue turn
 * (the action may open a modal dialog), calls the action exactly as the old
 * order buttons did: {@code a.actionPerformed(new ActionEvent(strip,
 * ACTION_PERFORMED, id))}, only if it is still enabled; an inert row calls
 * {@link Host#unavailable} instead.
 *
 * <p><b>Band mode</b> ({@link #setBand}): the first game scene's title band
 * instead of the titles, and no menu input.
 */
final class ClassicMenuStrip extends JComponent {

    private static final Logger logger = Logger.getLogger(ClassicMenuStrip.class.getName());

    /** What the strip needs from the game. */
    interface Host {

        /** The active unit's situation for the menus' visibility rules. */
        ClassicMenuModel.Context context();

        /** The engine action of an id, or null. */
        Action action(String id);

        /** Whether input must be ignored (the first scene is up). */
        boolean inputBlocked();

        /**
         * An inert row was fired (normal ink, but no engine equivalent or no
         * classic screen yet): tell the player it follows later.  Called on
         * the event thread after the menu has closed.
         */
        default void unavailable(ClassicMenuModel.Item item) {
            // nothing by default
        }
    }


    private final Host host;

    /** FONTTINY and the wood tile, or null without the pack. */
    private final ClassicFont font;
    private final BufferedImage wood;

    /** Per menu (bar order): title first, then the items; empty without MENU.TXT. */
    private final List<List<String>> menus = new ArrayList<>();

    /** The titles, the widths, the x of each title. */
    private final List<String> titles = new ArrayList<>();
    private int[] titleX = new int[0];
    private int[] titleW = new int[0];

    /** The open menu, or -1; the barred slot, or -1. */
    private int openIndex = -1;
    private int selSlot = -1;

    /** The band text (first scene), or null for the menu bar. */
    private String band = null;

    /** The open dropdown's painter and mouse target. */
    private final DropLayer drop = new DropLayer();

    /** Installed while a menu is open. */
    private KeyEventDispatcher dispatcher = null;

    /** A bare Alt was pressed while the menu was open (closes on its release). */
    private boolean altArmed = false;


    /**
     * @param host The game side.
     * @param font FONTTINY, or null.
     * @param wood {@code WOODTILE.SS.000}, or null.
     * @param text The pack's texts (for MENU.TXT), or null.
     */
    ClassicMenuStrip(Host host, ClassicFont font, BufferedImage wood,
                     ClassicText text) {
        this.host = host;
        this.font = font;
        this.wood = wood;
        if (text != null && text.hasMenus()) {
            for (String s : ClassicMenuModel.SECTIONS) {
                final List<String> m = text.menu(s);
                if (m == null) {
                    this.menus.clear();
                    break;
                }
                this.menus.add(m);
            }
        }
        for (List<String> m : this.menus) this.titles.add(m.get(0));
        this.titleW = ClassicMenuBar.titleWidths(font, this.titles);
        this.titleX = ClassicMenuBar.titleX(this.titleW);
        if (this.menus.isEmpty()) {
            logger.info("Classic menu strip: no MENU.TXT in the pack -- wood"
                + " only; the keys still work (re-run ant classic-assets).");
        }
        setOpaque(true);
        setFocusable(false);
        final MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    onStripPress(e);
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    drop.onMove(SwingUtilities.convertMouseEvent(
                        ClassicMenuStrip.this, e, drop));
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    drop.onRelease(SwingUtilities.convertMouseEvent(
                        ClassicMenuStrip.this, e, drop));
                }
            };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        installAltKeys();
    }


    // State

    /** The layer the HUD pane puts on its popup layer, over the canvas. */
    JComponent dropLayer() {
        return this.drop;
    }

    /** @return The open menu, or -1. */
    int openIndex() {
        return this.openIndex;
    }

    /** Whether a menu is open. */
    boolean isMenuOpen() {
        return this.openIndex >= 0;
    }

    /**
     * Show a title band instead of the titles (null: the menu bar again).
     * Closes an open menu.
     */
    void setBand(String text) {
        closeMenu();
        this.band = text;
        repaint();
    }

    /** The integer scale of the canvas this strip is part of. */
    private int scale() {
        return Math.max(1, getWidth() / ClassicMenuBar.WIDTH);
    }

    /** Whether menus may be used now. */
    private boolean usable() {
        return this.band == null && !this.menus.isEmpty() && !this.host.inputBlocked();
    }

    /** The slots of menu {@code m} in the live context. */
    List<ClassicMenuModel.Slot> slots(int m) {
        return ClassicMenuModel.slots(m, this.host.context(), id -> {
                final Action a = this.host.action(id);
                return a != null && a.isEnabled();
            });
    }

    /** The open dropdown's box in 320x200 pixels, or null. */
    private Rectangle box(List<ClassicMenuModel.Slot> slots) {
        if (this.openIndex < 0) return null;
        return dropdownBox(this.font, this.menus.get(this.openIndex),
                           this.titleX[this.openIndex], slots.size());
    }

    /**
     * A menu's dropdown box in 320x200 pixels.
     *
     * @param menu The MENU.TXT lines: title, then the items.
     * @param titleX The title's x.
     * @param slots The number of slots (rows plus separators).
     */
    static Rectangle dropdownBox(ClassicFont font, List<String> menu, int titleX,
                                 int slots) {
        return ClassicMenuBar.dropdownBounds(titleX,
            ClassicMenuBar.widest(font, menu.subList(1, menu.size())), slots);
    }

    /**
     * Paint a dropdown: each slot's MENU.TXT item (or a separator), the
     * greyed rows and the bar, through {@link ClassicMenuBox#paintDropdown}.
     * Shared by the live strip and the preview harness.
     *
     * @param menu The MENU.TXT lines: title, then the items.
     * @param box The box ({@link #dropdownBox}).
     * @param slots The slots ({@link ClassicMenuModel#slots}).
     * @param selSlot The barred slot, or -1.
     */
    static void paintDropdown(Graphics2D g, ClassicFont font, BufferedImage wood,
                              List<String> menu, Rectangle box,
                              List<ClassicMenuModel.Slot> slots, int selSlot) {
        final List<String> rows = new ArrayList<>();
        final boolean[] disabled = new boolean[slots.size()];
        for (int k = 0; k < slots.size(); k++) {
            final ClassicMenuModel.Slot sl = slots.get(k);
            rows.add(sl.item == null ? null : menu.get(sl.item.index + 1));
            disabled[k] = sl.item != null && sl.greyed;
        }
        ClassicMenuBox.paintDropdown(g, box, ClassicMenuBox.GAME, wood, font,
                                     rows, disabled, selSlot);
    }

    /** The title under a 320x200 point, or -1. */
    private int titleAt(int vx, int vy) {
        for (int i = 0; i < this.titles.size(); i++) {
            if (ClassicMenuBar.titleHighlight(this.titleX[i], this.titleW[i])
                .contains(vx, vy)) return i;
        }
        return -1;
    }

    /** The menu whose title's gold letter is {@code c}, or -1. */
    private int titleForLetter(char c) {
        for (int i = 0; i < this.titles.size(); i++) {
            if (ClassicMenuModel.hotkey(this.titles.get(i)) == Character.toUpperCase(c)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The first (dir 1) or last (dir -1) normal-ink slot after {@code from},
     * or -1 ({@link ClassicMenuModel.Slot#selectable}).
     */
    static int nextSelectable(List<ClassicMenuModel.Slot> slots, int from, int dir) {
        final int n = slots.size();
        if (n == 0) return -1;
        int i = from;
        for (int k = 0; k < n; k++) {
            i = (i < 0 && dir < 0) ? n - 1 : Math.floorMod(i + dir, n);
            if (slots.get(i).selectable()) return i;
        }
        return -1;
    }

    /**
     * The upper-case letter of a key code, or 0 when the key is not one of
     * A-Z.  Virtual key codes outside VK_A..VK_Z are no letters even where
     * their value is a letter's code: VK_NUMPAD1..9 are 0x61..0x69 ('a'..'i'),
     * VK_F1..F11 are 0x70..0x7A ('p'..'z') -- treating them as letters let a
     * numpad move or an F-key fire a BEFEHLE order while the menu was open.
     */
    static char letterOf(int keyCode) {
        return (keyCode >= KeyEvent.VK_A && keyCode <= KeyEvent.VK_Z)
            ? (char) keyCode : 0;
    }

    /**
     * The row a key press fires in an open menu: the first normal-ink row
     * whose gold letter is the key's letter (no Shift/Ctrl), or whose
     * printed key ({@code Item.key}: F2, Shift-D ...) is the keystroke.
     *
     * @param menu The MENU.TXT lines: title, then the items.
     * @param slots The open menu's slots.
     * @param keyCode The KEY_PRESSED code.
     * @param pressed The keystroke of the event.
     * @param plain Whether neither Shift nor Ctrl is down.
     * @return The slot index, or -1.
     */
    static int slotForKey(List<String> menu, List<ClassicMenuModel.Slot> slots,
                          int keyCode, KeyStroke pressed, boolean plain) {
        final char ch = letterOf(keyCode);
        for (int k = 0; k < slots.size(); k++) {
            final ClassicMenuModel.Slot s = slots.get(k);
            if (!s.selectable()) continue;
            final char hk = ClassicMenuModel.hotkey(menu.get(s.item.index + 1));
            if ((hk != 0 && hk == ch && plain)
                || (s.item.key != null && s.item.key.equals(pressed))) return k;
        }
        return -1;
    }

    /**
     * Open menu {@code m}.
     *
     * @param barFirst Whether the bar starts on the first normal-ink row
     *     (keys; 053: row 0) or nowhere (mouse).
     */
    void openMenu(int m, boolean barFirst) {
        if (m < 0 || m >= this.menus.size() || !usable()) return;
        this.openIndex = m;
        this.selSlot = barFirst ? nextSelectable(slots(m), -1, 1) : -1;
        this.altArmed = false;
        if (this.dispatcher == null) {
            this.dispatcher = this::dispatchOpen;
            KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(this.dispatcher);
        }
        this.drop.setVisible(true);
        repaint();
        this.drop.repaint();
    }

    /** Close the open menu, if any.  EDT only. */
    void closeMenu() {
        if (this.dispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .removeKeyEventDispatcher(this.dispatcher);
            this.dispatcher = null;
        }
        if (this.openIndex < 0) return;
        this.openIndex = -1;
        this.selSlot = -1;
        this.drop.setVisible(false);
        repaint();
        this.drop.repaint();
    }

    /**
     * Fire a row: close, then on a later event run its action -- or, for an
     * inert row, tell the player it follows later.  Grey rows do nothing.
     */
    private void fire(ClassicMenuModel.Slot s) {
        if (s == null || !s.selectable()) return;
        final ClassicMenuModel.Item it = s.item;
        closeMenu();
        if (!s.enabled) {
            SwingUtilities.invokeLater(() -> {
                    if (this.host.inputBlocked()) return;
                    logger.fine("Classic menu: inert " + it);
                    this.host.unavailable(it);
                });
            return;
        }
        SwingUtilities.invokeLater(() -> {
                if (this.host.inputBlocked()) return;
                if (!it.fires.test(this.host.context())) return;
                final Action a = this.host.action(it.actionId);
                if (a == null || !a.isEnabled()) return;
                logger.fine("Classic menu: " + it);
                a.actionPerformed(new ActionEvent(this,
                    ActionEvent.ACTION_PERFORMED, it.actionId));
            });
    }

    @Override
    public void removeNotify() {
        closeMenu();
        super.removeNotify();
    }


    // Input

    /** Alt + a title's gold letter opens that menu (bound on the strip). */
    private void installAltKeys() {
        for (int i = 0; i < this.titles.size(); i++) {
            final char c = ClassicMenuModel.hotkey(this.titles.get(i));
            if (c == 0) continue;
            final int code = KeyEvent.getExtendedKeyCodeForChar(c);
            if (code == KeyEvent.VK_UNDEFINED) continue;
            final KeyStroke ks = KeyStroke.getKeyStroke(code, KeyEvent.ALT_DOWN_MASK);
            final String name = "classicMenu." + i;
            final int menu = i;
            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(ks, name);
            getActionMap().put(name, new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        if (isMenuOpen()) return;
                        openMenu(menu, true);
                    }
                });
        }
    }

    private void onStripPress(MouseEvent e) {
        if (!usable()) return;
        if (!SwingUtilities.isLeftMouseButton(e)) return;
        final int s = scale();
        final int t = titleAt(e.getX() / s, e.getY() / s);
        if (t < 0) {
            closeMenu();
        } else if (t == this.openIndex) {
            closeMenu();
        } else {
            openMenu(t, false);
        }
    }

    /** The key dispatcher while a menu is open. */
    private boolean dispatchOpen(KeyEvent e) {
        if (this.openIndex < 0) return false;
        final Component c = e.getComponent();
        final Window ew = (c instanceof Window) ? (Window) c
            : SwingUtilities.getWindowAncestor(c);
        if (ew == null || ew != SwingUtilities.getWindowAncestor(this)) {
            // A key in another window: the menu is not where the player is.
            SwingUtilities.invokeLater(this::closeMenu);
            return false;
        }
        final int code = e.getKeyCode();
        if (e.isAltDown() && (code == KeyEvent.VK_ENTER || code == KeyEvent.VK_F4)) {
            return false;
        }
        if (e.getID() == KeyEvent.KEY_TYPED) return true;
        if (e.getID() == KeyEvent.KEY_RELEASED) {
            if (code == KeyEvent.VK_ALT && this.altArmed) closeMenu();
            this.altArmed = false;
            return true;
        }
        if (code == KeyEvent.VK_ALT) {
            this.altArmed = true;
            return true;
        }
        this.altArmed = false;
        if (code == KeyEvent.VK_SHIFT || code == KeyEvent.VK_CONTROL
            || code == KeyEvent.VK_META || code == KeyEvent.VK_ALT_GRAPH) {
            return true;
        }
        final List<ClassicMenuModel.Slot> slots = slots(this.openIndex);
        switch (code) {
        case KeyEvent.VK_UP: case KeyEvent.VK_KP_UP:
            this.selSlot = nextSelectable(slots, this.selSlot, -1);
            this.drop.repaint();
            return true;
        case KeyEvent.VK_DOWN: case KeyEvent.VK_KP_DOWN:
            this.selSlot = nextSelectable(slots, this.selSlot, 1);
            this.drop.repaint();
            return true;
        case KeyEvent.VK_LEFT: case KeyEvent.VK_KP_LEFT:
            openMenu(Math.floorMod(this.openIndex - 1, this.menus.size()), true);
            return true;
        case KeyEvent.VK_RIGHT: case KeyEvent.VK_KP_RIGHT:
            openMenu(Math.floorMod(this.openIndex + 1, this.menus.size()), true);
            return true;
        case KeyEvent.VK_ENTER:
            if (this.selSlot >= 0 && this.selSlot < slots.size()) {
                fire(slots.get(this.selSlot));
            }
            return true;
        case KeyEvent.VK_ESCAPE:
            closeMenu();
            return true;
        default:
            break;
        }
        if (e.isAltDown()) {
            final char ch = letterOf(code);
            final int m = (ch == 0) ? -1 : titleForLetter(ch);
            if (m >= 0 && m != this.openIndex) openMenu(m, true);
            return true;
        }
        final int k = slotForKey(this.menus.get(this.openIndex), slots, code,
            KeyStroke.getKeyStrokeForEvent(e),
            !e.isShiftDown() && !e.isControlDown());
        if (k >= 0) fire(slots.get(k));
        return true;
    }


    // Painting

    /** Draw a 320x200-pixel picture region scaled by {@code s}, nearest-neighbour. */
    static void blit(Graphics g, BufferedImage img, int x, int y, int s) {
        final Graphics2D gg = (Graphics2D) g.create();
        try {
            gg.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            gg.drawImage(img, x, y, img.getWidth() * s, img.getHeight() * s, null);
        } finally {
            gg.dispose();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        final BufferedImage img = new BufferedImage(ClassicMenuBar.WIDTH,
            ClassicMenuBar.HEIGHT, BufferedImage.TYPE_INT_RGB);
        final Graphics2D ig = img.createGraphics();
        try {
            if (this.band != null) {
                ClassicMenuBar.paintBand(ig, this.font, this.wood, this.band);
            } else {
                ClassicMenuBar.paintBar(ig, this.font, this.wood, this.titles,
                                        this.openIndex);
            }
        } finally {
            ig.dispose();
        }
        blit(g, img, 0, 0, scale());
    }


    /**
     * The open dropdown over the whole canvas: paints only the box, takes
     * every mouse event while visible (a press outside the box closes the
     * menu, as a press outside any menu does).
     */
    private final class DropLayer extends JComponent {

        DropLayer() {
            setOpaque(false);
            setVisible(false);
            final MouseAdapter mouse = new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        onPress(e);
                    }

                    @Override
                    public void mouseReleased(MouseEvent e) {
                        onRelease(e);
                    }

                    @Override
                    public void mouseMoved(MouseEvent e) {
                        onMove(e);
                    }

                    @Override
                    public void mouseDragged(MouseEvent e) {
                        onMove(e);
                    }
                };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
            addMouseWheelListener(e -> e.consume());
        }

        private int s() {
            return Math.max(1, getWidth() / ClassicMenuBox.VW);
        }

        /** The slot under a point of this layer, or -1. */
        private int slotAt(Point p, List<ClassicMenuModel.Slot> slots) {
            final Rectangle b = box(slots);
            if (b == null) return -1;
            final int vx = p.x / s(), vy = p.y / s();
            if (!b.contains(vx, vy)) return -1;
            for (int k = 0; k < slots.size(); k++) {
                final int top = ClassicMenuBox.dropdownSlotTop(b, k);
                if (vy >= top && vy < top + ClassicMenuBox.PITCH) return k;
            }
            return -1;
        }

        void onPress(MouseEvent e) {
            e.consume();
            if (openIndex < 0) return;
            final int t = titleAt(e.getX() / s(), e.getY() / s());
            if (t >= 0) {
                if (t == openIndex) closeMenu(); else openMenu(t, false);
                return;
            }
            final List<ClassicMenuModel.Slot> slots = slots(openIndex);
            final Rectangle b = box(slots);
            if (b == null || !b.contains(e.getX() / s(), e.getY() / s())) {
                closeMenu();
                return;
            }
            onMove(e);
        }

        void onMove(MouseEvent e) {
            if (openIndex < 0) return;
            final List<ClassicMenuModel.Slot> slots = slots(openIndex);
            final int k = slotAt(e.getPoint(), slots);
            if (k < 0) return;      // outside the box: the bar stays
            final int sel = slots.get(k).selectable() ? k : -1;
            if (sel != selSlot) {
                selSlot = sel;
                repaint();
            }
        }

        void onRelease(MouseEvent e) {
            if (openIndex < 0) return;
            if (!SwingUtilities.isLeftMouseButton(e)) return;
            final List<ClassicMenuModel.Slot> slots = slots(openIndex);
            final int k = slotAt(e.getPoint(), slots);
            if (k >= 0) fire(slots.get(k));
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (openIndex < 0) return;
            final List<ClassicMenuModel.Slot> slots = slots(openIndex);
            final Rectangle b = box(slots);
            if (b == null) return;
            final BufferedImage img = new BufferedImage(b.width, b.height,
                                                        BufferedImage.TYPE_INT_RGB);
            final Graphics2D ig = img.createGraphics();
            try {
                ig.translate(-b.x, -b.y);
                ClassicMenuStrip.paintDropdown(ig, font, wood, menus.get(openIndex),
                                               b, slots, selSlot);
            } finally {
                ig.dispose();
            }
            blit(g, img, b.x * s(), b.y * s(), s());
        }
    }
}
