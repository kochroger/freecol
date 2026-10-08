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

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;


/**
 * Tests of the in-game menu strip's geometry ({@link ClassicMenuBar}), the
 * menu table and its rules ({@link ClassicMenuModel}) and the greyed
 * dropdown rows ({@link ClassicMenuBox}).  Expected numbers are measured in
 * the native captures (Steam 001-005, 032, 000; start-sequence 052, 053);
 * no original word appears here.
 */
public class ClassicMenuBarTest extends TestCase {

    /** FONTTINY widths of the six titles measured in 032 -> x 13 .. 263. */
    public void testTitleX() {
        assertTrue(Arrays.equals(new int[] { 13, 53, 101, 149, 195, 263 },
            ClassicMenuBar.titleX(new int[] { 26, 34, 34, 32, 30, 44 })));
        // The last title is right-aligned 13 px before the edge.
        final int[] x = ClassicMenuBar.titleX(new int[] { 10, 20 });
        assertEquals(13, x[0]);
        assertEquals(320 - 13 - 20, x[1]);
        assertEquals(13, ClassicMenuBar.titleX(new int[] { 50 })[0]);
    }

    /** The open title's dark rectangle: SPIEL 12..41 in row 0 of 003. */
    public void testTitleHighlight() {
        assertEquals(new Rectangle(12, 0, 30, 7), ClassicMenuBar.titleHighlight(13, 26));
        assertEquals(new Rectangle(52, 0, 38, 7), ClassicMenuBar.titleHighlight(53, 34));
    }

    /** Dropdown boxes measured in 003/004/001/002/005; COLONIPAEDIE clamped. */
    public void testDropdownBounds() {
        assertEquals(new Rectangle(12, 9, 100, 109), ClassicMenuBar.dropdownBounds(13, 90, 13));
        assertEquals(new Rectangle(52, 9, 112, 133), ClassicMenuBar.dropdownBounds(53, 102, 16));
        assertEquals(new Rectangle(100, 9, 105, 117), ClassicMenuBar.dropdownBounds(101, 95, 14));
        assertEquals(new Rectangle(148, 9, 110, 109), ClassicMenuBar.dropdownBounds(149, 100, 13));
        assertEquals(new Rectangle(194, 9, 92, 29), ClassicMenuBar.dropdownBounds(195, 82, 3));
        // No capture: pushed left so it ends at the screen edge.
        assertEquals(new Rectangle(243, 9, 77, 69), ClassicMenuBar.dropdownBounds(263, 67, 8));
    }

    /** A context with an active land unit on open, unforested land. */
    private static ClassicMenuModel.Context land() {
        return new ClassicMenuModel.Context(true, false, false, false, false, false, false);
    }

    /** Slot counts of the captured menus (rows plus separators). */
    public void testSlotCounts() {
        final ClassicMenuModel.Context c = land();
        assertEquals(13, ClassicMenuModel.slots(ClassicMenuModel.SPIEL, c, it -> true).size());
        assertEquals(16, ClassicMenuModel.slots(ClassicMenuModel.ANSICHT, c, it -> true).size());
        assertEquals(13, ClassicMenuModel.slots(ClassicMenuModel.BERICHTE, c, it -> true).size());
        assertEquals(3, ClassicMenuModel.slots(ClassicMenuModel.HANDEL, c, it -> true).size());
        assertEquals(8, ClassicMenuModel.slots(ClassicMenuModel.PEDIA, c, it -> true).size());
    }

    /**
     * Capture 001: a colonist without tools on a forest tile, no colony:
     * items 0,1,2,4 | 5,7,9 | 14 | 17 | 19 with four separators, 14 slots.
     */
    public void testOrdersIn001() {
        final ClassicMenuModel.Context c = new ClassicMenuModel.Context(
            true, false, false, false, true, false, false);
        // The live rule: clearing (no tools) and the road (one exists) are
        // the two actions FreeCol disables there.
        final List<ClassicMenuModel.Slot> s = ClassicMenuModel.slots(
            ClassicMenuModel.BEFEHLE, c,
            id -> !(id.equals("clearForestAction") || id.equals("roadAction")));
        assertEquals(14, s.size());
        assertEquals("0 1 2 4 - 5 7 9 - 14 - 17 - 19", describe(s));
        assertEquals("6 7", greyed(s));     // items 7 and 9, nothing else
        assertTrue(s.get(6).greyed);        // item 7
        assertFalse(s.get(6).enabled);
        assertTrue(s.get(5).enabled);
        // Goto (item 14, "Zum Ort gehen") opens the destination list (R2):
        // normal ink (as in 001), live.
        assertFalse(s.get(9).greyed);
        assertTrue(s.get(9).enabled);
        assertTrue(s.get(9).selectable());
    }

    /**
     * Only a disabled BEFEHLE order is grey; inert rows (no engine action, a
     * no-op seam) and disabled actions elsewhere keep the normal ink, as in
     * 053 (1492: independence in normal ink), 003, 004 and 002 (F1 row).
     */
    public void testGreyRule() {
        final ClassicMenuModel.Context c = land();
        final List<ClassicMenuModel.Slot> spiel = ClassicMenuModel.slots(
            ClassicMenuModel.SPIEL, c, id -> !id.equals("declareIndependenceAction"));
        assertEquals("", greyed(spiel));
        // Row 0 (the options box, W14) is where Alt+G puts the bar (053).
        assertEquals(0, ClassicMenuStrip.nextSelectable(spiel, -1, 1));
        assertTrue(spiel.get(0).enabled);
        assertTrue(spiel.get(1).enabled);   // the colony report options
        assertFalse(spiel.get(6).enabled);  // save: a no-op seam, inert
        assertFalse(spiel.get(6).greyed);
        assertTrue(spiel.get(12).enabled);  // quit
        // Without the host's action (the preview) the option rows are inert.
        final List<ClassicMenuModel.Slot> none = ClassicMenuModel.slots(
            ClassicMenuModel.SPIEL, c, id -> false);
        assertFalse(none.get(0).enabled);
        assertFalse(none.get(0).greyed);
        for (int m : new int[] { ClassicMenuModel.ANSICHT, ClassicMenuModel.BERICHTE,
                                 ClassicMenuModel.HANDEL, ClassicMenuModel.PEDIA }) {
            assertEquals(Integer.toString(m), "",
                         greyed(ClassicMenuModel.slots(m, c, id -> false)));
        }
        // Every order disabled: grey are exactly the wired orders.
        final List<ClassicMenuModel.Slot> orders = ClassicMenuModel.slots(
            ClassicMenuModel.BEFEHLE, c, id -> false);
        for (ClassicMenuModel.Slot sl : orders) {
            if (sl.item == null) continue;
            final boolean wired = sl.item.actionId != null
                && !ClassicMenuModel.NOOP_SEAMS.contains(sl.item.actionId);
            assertEquals(sl.item.toString(), wired, sl.greyed);
            assertFalse(sl.item.toString(), sl.enabled);
        }
    }

    /**
     * Only VK_A..VK_Z are letters: numpad and F-key codes whose values equal
     * letters' codes (VK_NUMPAD2 = 'b', VK_F1 = 'p', VK_F3 = 'r') must not
     * fire BEFEHLE rows while the menu is open.
     */
    public void testKeyLetters() {
        assertEquals(0, ClassicMenuStrip.letterOf(java.awt.event.KeyEvent.VK_NUMPAD2));
        assertEquals(0, ClassicMenuStrip.letterOf(java.awt.event.KeyEvent.VK_F1));
        assertEquals(0, ClassicMenuStrip.letterOf(java.awt.event.KeyEvent.VK_SUBTRACT));
        assertEquals('B', ClassicMenuStrip.letterOf(java.awt.event.KeyEvent.VK_B));
        // Synthetic BEFEHLE items with the gold letters of the original's.
        final String letters = "AWFFSBBPPRLU GGT  O ";
        final List<String> menu = new ArrayList<>();
        menu.add("TITLE");
        for (int i = 0; i < 20; i++) {
            final char l = letters.charAt(i);
            menu.add(l == ' ' ? "xxx" : "~" + l + "xx");
        }
        final ClassicMenuModel.Context c = land();
        final List<ClassicMenuModel.Slot> s = ClassicMenuModel.slots(
            ClassicMenuModel.BEFEHLE, c, id -> true);
        for (int code : new int[] { java.awt.event.KeyEvent.VK_NUMPAD2,
                                    java.awt.event.KeyEvent.VK_NUMPAD6,
                                    java.awt.event.KeyEvent.VK_NUMPAD1,
                                    java.awt.event.KeyEvent.VK_F1,
                                    java.awt.event.KeyEvent.VK_F3,
                                    java.awt.event.KeyEvent.VK_F4,
                                    java.awt.event.KeyEvent.VK_F8 }) {
            assertEquals(Integer.toHexString(code), -1, ClassicMenuStrip.slotForKey(menu, s,
                code, javax.swing.KeyStroke.getKeyStroke(code, 0), true));
        }
        final int b = ClassicMenuStrip.slotForKey(menu, s, java.awt.event.KeyEvent.VK_B,
            javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_B, 0), true);
        assertTrue(b >= 0);
        assertEquals(5, s.get(b).item.index);
        // Shift-D is the disband row's printed key.
        final int d = ClassicMenuStrip.slotForKey(menu, s, java.awt.event.KeyEvent.VK_D,
            javax.swing.KeyStroke.getKeyStroke("shift D"), false);
        assertEquals(19, s.get(d).item.index);
    }

    /** Other order contexts: a ship at sea, a soldier in a colony, no unit. */
    public void testOrdersContexts() {
        final ClassicMenuModel.Context ship = new ClassicMenuModel.Context(
            true, true, true, false, false, false, false);
        assertEquals("0 1 2 4 - 10 - 13 15 16 - 17 - 18 19",
            describe(ClassicMenuModel.slots(ClassicMenuModel.BEFEHLE, ship, it -> true)));
        final ClassicMenuModel.Context shipInColony = new ClassicMenuModel.Context(
            true, true, true, false, false, true, false);
        assertEquals("0 1 2 4 - 10 11 - 13 15 16 - 17 - 19",
            describe(ClassicMenuModel.slots(ClassicMenuModel.BEFEHLE, shipInColony, it -> true)));
        final ClassicMenuModel.Context soldier = new ClassicMenuModel.Context(
            true, false, false, true, false, true, false);
        assertEquals("0 1 2 4 - 6 8 9 - 12 - 14 - 17 - 19",
            describe(ClassicMenuModel.slots(ClassicMenuModel.BEFEHLE, soldier, it -> true)));
        assertEquals("0 1 - 17", describe(ClassicMenuModel.slots(ClassicMenuModel.BEFEHLE,
            ClassicMenuModel.Context.NONE, it -> true)));
    }

    /** The item -> action map (spec table), spot-checked per menu. */
    public void testActionMap() {
        final String[][] expect = {
            { "0", "0", "classic.gameOptions" }, { "0", "1", "classic.colonyOptions" },
            { "0", "2", null }, { "0", "4", "saveAction" },
            { "0", "5", "openAction" }, { "0", "6", "declareIndependenceAction" },
            { "0", "7", "retireAction" }, { "0", "8", "quitAction" },
            { "1", "0", "toggleViewModeAction" }, { "1", "1", "toggleViewModeAction" },
            { "1", "2", "europeAction" }, { "1", "3", "findSettlementAction" },
            { "1", "6", null }, { "1", "10", null }, { "1", "11", "centerAction" },
            { "2", "0", "clearOrdersAction" }, { "2", "2", "fortifyAction" },
            { "2", "7", "clearForestAction" }, { "2", "8", "plowAction" },
            { "2", "12", null }, { "2", "14", "gotoAction" },
            { "2", "16", "returnToEuropeAction" },
            { "2", "17", "skipUnitAction" }, { "2", "18", "unloadAction" },
            { "2", "19", "disbandUnitAction" },
            { "3", "0", null }, { "3", "1", "reportReligionAction" },
            { "3", "6", "reportNavalAction" }, { "3", "9", "reportHighScoresAction" },
            { "4", "2", "tradeRouteAction" },
            { "5", "0", "colopediaAction.goods" }, { "5", "3", null },
            { "5", "6", "colopediaAction.concepts" }, { "5", "7", null },
        };
        for (String[] e : expect) {
            final ClassicMenuModel.Item it = ClassicMenuModel.items(Integer.parseInt(e[0]))
                .get(Integer.parseInt(e[1]));
            assertEquals(Arrays.toString(e), e[2], it.actionId);
        }
        assertEquals(9, ClassicMenuModel.items(ClassicMenuModel.SPIEL).size());
        assertEquals(12, ClassicMenuModel.items(ClassicMenuModel.ANSICHT).size());
        assertEquals(20, ClassicMenuModel.items(ClassicMenuModel.BEFEHLE).size());
        assertEquals(10, ClassicMenuModel.items(ClassicMenuModel.BERICHTE).size());
        assertEquals(3, ClassicMenuModel.items(ClassicMenuModel.HANDEL).size());
        assertEquals(8, ClassicMenuModel.items(ClassicMenuModel.PEDIA).size());
    }

    /** Enabled = an action, not a no-op seam, and the action enabled. */
    public void testLiveRule() {
        final ClassicMenuModel.Item save = ClassicMenuModel.items(ClassicMenuModel.SPIEL).get(4);
        final ClassicMenuModel.Item quit = ClassicMenuModel.items(ClassicMenuModel.SPIEL).get(8);
        final ClassicMenuModel.Item music = ClassicMenuModel.items(ClassicMenuModel.SPIEL).get(3);
        assertFalse(ClassicMenuModel.isLive(save, id -> true));    // no-op seam
        assertTrue(ClassicMenuModel.isLive(quit, id -> true));
        assertFalse(ClassicMenuModel.isLive(quit, id -> false));   // action disabled
        assertFalse(ClassicMenuModel.isLive(music, id -> true));   // no action
        for (ClassicMenuModel.Item it : ClassicMenuModel.allItems()) {
            if (it.actionId != null && it.actionId.startsWith("colopediaAction.")) {
                // Only the fathers have their classic screen (D8b).
                assertEquals(it.toString(), it.actionId.equals("colopediaAction.fathers"),
                             ClassicMenuModel.isLive(it, id -> true));
            }
        }
    }

    /** The view-mode rows fire only towards their own mode. */
    public void testViewModeRows() {
        final ClassicMenuModel.Item move = ClassicMenuModel.items(ClassicMenuModel.ANSICHT).get(0);
        final ClassicMenuModel.Item view = ClassicMenuModel.items(ClassicMenuModel.ANSICHT).get(1);
        final ClassicMenuModel.Context terrain = new ClassicMenuModel.Context(
            false, false, false, false, false, false, true);
        assertTrue(move.fires.test(terrain));
        assertFalse(view.fires.test(terrain));
        assertFalse(move.fires.test(land()));
        assertTrue(view.fires.test(land()));
    }

    /** The gold letter: the only '~'-marked character, else none. */
    public void testHotkey() {
        assertEquals('G', ClassicMenuModel.hotkey("ABCDE ~G"));
        assertEquals('R', ClassicMenuModel.hotkey("BE~RICHTE"));
        assertEquals('A', ClassicMenuModel.hotkey("Xxx ~axx"));
        assertEquals(0, ClassicMenuModel.hotkey("~F~1 Xxx"));
        assertEquals(0, ClassicMenuModel.hotkey("Xxx (~S~h~i~f~t~-~D)"));
        assertEquals(0, ClassicMenuModel.hotkey("plain"));
        assertEquals(0, ClassicMenuModel.hotkey(null));
    }

    /** Every no-op seam names an action the table uses. */
    public void testNoopSeamsUsed() {
        final Set<String> used = new HashSet<>();
        for (ClassicMenuModel.Item it : ClassicMenuModel.allItems()) used.add(it.actionId);
        for (String id : ClassicMenuModel.NOOP_SEAMS) assertTrue(id, used.contains(id));
    }

    /** A greyed row is drawn in 0x555555 and nothing green. */
    public void testDisabledRow() {
        final BufferedImage img = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = img.createGraphics();
        final Rectangle b = ClassicMenuBar.dropdownBounds(13, 90, 2);
        ClassicMenuBox.paintDropdown(g, b, ClassicMenuBox.GAME, null, null,
            Arrays.asList("WWWWWW", "WWWWWW"), new boolean[] { true, false }, -1);
        g.dispose();
        final int green = ClassicMenuBox.GAME.ink.getRGB() & 0xFFFFFF;
        boolean grey0 = false, green0 = false, green1 = false;
        for (int y = b.y + 3; y < b.y + 3 + 8; y++) {
            for (int x = b.x + 4; x < b.x + b.width - 2; x++) {
                final int c = img.getRGB(x, y) & 0xFFFFFF;
                if (c == ClassicMenuBox.DISABLED_INK) grey0 = true;
                if (c == green) green0 = true;
            }
        }
        for (int y = b.y + 11; y < b.y + 11 + 8; y++) {
            for (int x = b.x + 4; x < b.x + b.width - 2; x++) {
                if ((img.getRGB(x, y) & 0xFFFFFF) == green) green1 = true;
            }
        }
        assertTrue(grey0);
        assertFalse(green0);
        assertTrue(green1);
    }

    // The strip's mouse (build spec W20): press marks, release fires, never hover.

    /**
     * A strip from a synthetic MENU.TXT (no original words: every section
     * "Mk" with 20 items "Ik.j") whose host enables every action and logs
     * the ones fired.
     */
    private static ClassicMenuStrip strip(List<String> fired) throws Exception {
        final java.io.File dir = java.nio.file.Files.createTempDirectory("classic-strip").toFile();
        final java.io.File g = new java.io.File(dir, "GAME.TXT"),
            n = new java.io.File(dir, "NAMES.TXT"), l = new java.io.File(dir, "LABELS.TXT"),
            m = new java.io.File(dir, "MENU.TXT");
        final StringBuilder menu = new StringBuilder();
        for (int k = 0; k < ClassicMenuModel.SECTIONS.length; k++) {
            menu.append('@').append(ClassicMenuModel.SECTIONS[k]).append("\r\n  M")
                .append(k).append("\r\n");
            for (int j = 0; j < 20; j++) menu.append("  I").append(k).append('.').append(j)
                .append("\r\n");
            menu.append("\r\n");
        }
        final byte[] none = "@X\r\n\r\n".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        java.nio.file.Files.write(g.toPath(), none);
        java.nio.file.Files.write(n.toPath(), none);
        java.nio.file.Files.write(l.toPath(), none);
        java.nio.file.Files.write(m.toPath(), menu.toString()
            .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        final ClassicText t;
        try {
            t = ClassicText.fromFiles(g, n, l, m);
        } finally {
            for (java.io.File f : new java.io.File[] { g, n, l, m }) f.delete();
            dir.delete();
        }
        final ClassicMenuStrip.Host host = new ClassicMenuStrip.Host() {
                @Override
                public ClassicMenuModel.Context context() {
                    return land();
                }

                @Override
                public javax.swing.Action action(String id) {
                    return new javax.swing.AbstractAction() {
                            @Override
                            public void actionPerformed(java.awt.event.ActionEvent e) {
                                fired.add(id);
                            }
                        };
                }

                @Override
                public boolean inputBlocked() {
                    return false;
                }
            };
        final ClassicMenuStrip[] s = new ClassicMenuStrip[1];
        javax.swing.SwingUtilities.invokeAndWait(() -> {
                s[0] = new ClassicMenuStrip(host, null, null, t);
                s[0].setSize(320, 8);
                s[0].dropLayer().setSize(320, 200);
            });
        return s[0];
    }

    /** A mouse event on the strip's drop layer, through its own listeners. */
    private static void mouse(ClassicMenuStrip s, int id, int x, int y) throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
                final javax.swing.JComponent c = s.dropLayer();
                final boolean button = id == java.awt.event.MouseEvent.MOUSE_PRESSED
                    || id == java.awt.event.MouseEvent.MOUSE_RELEASED;
                final java.awt.event.MouseEvent e = new java.awt.event.MouseEvent(c, id,
                    System.currentTimeMillis(), (id == java.awt.event.MouseEvent.MOUSE_MOVED
                        || id == java.awt.event.MouseEvent.MOUSE_RELEASED) ? 0
                    : java.awt.event.InputEvent.BUTTON1_DOWN_MASK, x, y, 1, false,
                    button ? java.awt.event.MouseEvent.BUTTON1
                    : java.awt.event.MouseEvent.NOBUTTON);
                for (java.awt.event.MouseListener ml : c.getMouseListeners()) {
                    if (id == java.awt.event.MouseEvent.MOUSE_PRESSED) ml.mousePressed(e);
                    if (id == java.awt.event.MouseEvent.MOUSE_RELEASED) ml.mouseReleased(e);
                }
                for (java.awt.event.MouseMotionListener ml : c.getMouseMotionListeners()) {
                    if (id == java.awt.event.MouseEvent.MOUSE_MOVED) ml.mouseMoved(e);
                    if (id == java.awt.event.MouseEvent.MOUSE_DRAGGED) ml.mouseDragged(e);
                }
            });
        javax.swing.SwingUtilities.invokeAndWait(() -> { });   // a fired row runs later
    }

    /** The centre of slot {@code k} of the open dropdown. */
    private static int[] row(ClassicMenuStrip s, int k) {
        final Rectangle b = s.openBox();
        return new int[] { b.x + b.width / 2,
                           ClassicMenuBox.dropdownSlotTop(b, k) + ClassicMenuBox.PITCH / 2 };
    }

    private static void open(ClassicMenuStrip s, boolean barFirst) throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> s.openMenu(ClassicMenuModel.SPIEL,
                                                                  barFirst));
    }

    /**
     * A dropdown opened with the mouse has no bar while the pointer rests
     * on or crosses its rows (landing-slow #335-#373, clip006 #706-#791:
     * no bar pixels), and nothing fires.
     */
    public void testMenuHoverDoesNotBar() throws Exception {
        final List<String> fired = new ArrayList<>();
        final ClassicMenuStrip s = strip(fired);
        open(s, false);
        assertTrue(s.isMenuOpen());
        assertEquals(-1, s.barSlot());
        for (int k = 0; k < 3; k++) {
            final int[] p = row(s, k);
            mouse(s, java.awt.event.MouseEvent.MOUSE_MOVED, p[0], p[1]);
            assertEquals(-1, s.barSlot());
        }
        assertTrue(s.isMenuOpen());
        assertEquals("[]", fired.toString());
    }

    /**
     * The bar comes with the press and the row fires at the release
     * (landing-slow #384 -&gt; #391, clip006 #833 -&gt; #842); a drag with
     * the button held moves the bar and the release fires the row under it
     * (I, kept); a press on a separator removes the bar and fires nothing.
     */
    public void testMenuPressBarsReleaseFires() throws Exception {
        final List<String> fired = new ArrayList<>();
        final ClassicMenuStrip s = strip(fired);
        open(s, false);
        int[] p = row(s, 0);
        mouse(s, java.awt.event.MouseEvent.MOUSE_PRESSED, p[0], p[1]);
        assertEquals(0, s.barSlot());
        assertTrue(s.isMenuOpen());
        assertEquals("[]", fired.toString());
        mouse(s, java.awt.event.MouseEvent.MOUSE_RELEASED, p[0], p[1]);
        assertFalse(s.isMenuOpen());
        assertEquals("[classic.gameOptions]", fired.toString());
        // Press on row 0, drag to row 1, release there: row 1 fires.
        fired.clear();
        open(s, false);
        p = row(s, 0);
        mouse(s, java.awt.event.MouseEvent.MOUSE_PRESSED, p[0], p[1]);
        p = row(s, 1);
        mouse(s, java.awt.event.MouseEvent.MOUSE_DRAGGED, p[0], p[1]);
        assertEquals(1, s.barSlot());
        mouse(s, java.awt.event.MouseEvent.MOUSE_RELEASED, p[0], p[1]);
        assertFalse(s.isMenuOpen());
        assertEquals("[classic.colonyOptions]", fired.toString());
        // A separator: no bar, nothing fires, the menu stays.
        fired.clear();
        open(s, true);
        final List<ClassicMenuModel.Slot> slots = s.slots(ClassicMenuModel.SPIEL);
        int sep = -1;
        for (int k = 0; k < slots.size() && sep < 0; k++) {
            if (slots.get(k).item == null) sep = k;
        }
        assertTrue(sep > 0);
        p = row(s, sep);
        mouse(s, java.awt.event.MouseEvent.MOUSE_PRESSED, p[0], p[1]);
        assertEquals(-1, s.barSlot());
        mouse(s, java.awt.event.MouseEvent.MOUSE_RELEASED, p[0], p[1]);
        assertTrue(s.isMenuOpen());
        assertEquals("[]", fired.toString());
        javax.swing.SwingUtilities.invokeAndWait(s::closeMenu);
    }

    /**
     * A press outside the open dropdown removes the bar and its release
     * closes the menu (dago-colony #27-#179 bar on row 0, #189 bar gone,
     * #194 menu gone); a drag back inside bars nothing, and a release back
     * inside leaves the menu open without a bar (I).
     */
    public void testMenuOutsidePressRemovesBarReleaseCloses() throws Exception {
        final List<String> fired = new ArrayList<>();
        final ClassicMenuStrip s = strip(fired);
        open(s, true);
        assertEquals(0, s.barSlot());   // the keyboard's bar (landing-slow #32)
        final Rectangle b = s.openBox();
        assertFalse(b.contains(250, 128));
        mouse(s, java.awt.event.MouseEvent.MOUSE_PRESSED, 250, 128);
        assertTrue(s.isMenuOpen());
        assertEquals(-1, s.barSlot());
        final int[] p0 = row(s, 0);
        mouse(s, java.awt.event.MouseEvent.MOUSE_DRAGGED, p0[0], p0[1]);
        assertEquals(-1, s.barSlot());
        mouse(s, java.awt.event.MouseEvent.MOUSE_DRAGGED, 250, 128);
        mouse(s, java.awt.event.MouseEvent.MOUSE_RELEASED, 250, 128);
        assertFalse(s.isMenuOpen());
        assertEquals("[]", fired.toString());
        // Outside press, release back inside: open, no bar, nothing fired.
        open(s, true);
        mouse(s, java.awt.event.MouseEvent.MOUSE_PRESSED, 250, 128);
        mouse(s, java.awt.event.MouseEvent.MOUSE_RELEASED, p0[0], p0[1]);
        assertTrue(s.isMenuOpen());
        assertEquals(-1, s.barSlot());
        assertEquals("[]", fired.toString());
        // The next press and release on a row work as ever.
        final int[] p1 = row(s, 1);
        mouse(s, java.awt.event.MouseEvent.MOUSE_PRESSED, p1[0], p1[1]);
        assertEquals(1, s.barSlot());
        mouse(s, java.awt.event.MouseEvent.MOUSE_RELEASED, p1[0], p1[1]);
        assertFalse(s.isMenuOpen());
        assertEquals("[classic.colonyOptions]", fired.toString());
        // A press on the open title still closes it at once.
        open(s, false);
        mouse(s, java.awt.event.MouseEvent.MOUSE_PRESSED, b.x + 3, 3);
        assertFalse(s.isMenuOpen());
    }

    /** The slot numbers drawn grey, space-separated. */
    private static String greyed(List<ClassicMenuModel.Slot> slots) {
        final List<String> out = new ArrayList<>();
        for (int k = 0; k < slots.size(); k++) {
            if (slots.get(k).greyed) out.add(Integer.toString(k));
        }
        return String.join(" ", out);
    }

    /** "0 1 - 5": item indices, '-' for a separator. */
    private static String describe(List<ClassicMenuModel.Slot> slots) {
        final List<String> out = new ArrayList<>();
        for (ClassicMenuModel.Slot s : slots) {
            out.add(s.item == null ? "-" : Integer.toString(s.item.index));
        }
        return String.join(" ", out);
    }
}
