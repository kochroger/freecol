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
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import net.sf.freecol.client.gui.GUI;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.tools.classicassets.ClassicAssetDecoderTest;
import net.sf.freecol.tools.classicassets.FfDecoder;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * The classic screens in the main window (master plan M4.0, build spec
 * {@code prep2/M40-mainwindow.md} §7, T1-T8), headless: the screen stack
 * on a pane like the HUD's ({@link ClassicScreenStack}), the HUD's keys
 * disabled while a screen is hosted ({@link ClassicKeyMap}), the GUI's
 * host model (the colony and Europe replacing each other, the keys' home,
 * Europe's close after its last ship on a fake clock), the recorder's index
 * hint, and a census of the window machinery that went.
 */
public class ClassicScreenStackTest extends FreeColTestCase {

    private static final UnitType merchantman
        = spec().getUnitType("model.unit.merchantman");

    /** A clock the test sets. */
    private static final class FakeClock implements ClassicSlide.Clock {

        volatile long now = 1_000_000_000L;

        @Override
        public long now() {
            return this.now;
        }

        @Override
        public void waitUntil(long due) {
            // never waits: the test advances the clock
        }

        void advanceMs(double ms) {
            this.now += Math.round(ms * 1e6);
        }
    }

    /** The boxes' host for a layer of the test (a two-glyph font). */
    private static final class FakeHost implements ClassicAdvisorLayer.Host {

        private final ClassicFont font = tinyFont();

        @Override
        public ClassicFont font() {
            return this.font;
        }

        @Override
        public BufferedImage wood() {
            return null;
        }

        @Override
        public BufferedImage portrait(String sprite) {
            return null;
        }

        @Override
        public int[] portraitPalette(ClassicAdvisorBox.Portrait p) {
            return null;
        }

        @Override
        public void opened() {
        }

        @Override
        public void closed() {
        }

        @Override
        public void idle() {
        }

        @Override
        public boolean isAutoRepeat(KeyEvent e) {
            return false;
        }
    }

    private static int[] glyph(int code, int w) {
        final int[] g = new int[2 + w];
        g[0] = code;
        g[1] = w;
        g[2] = 1;
        return g;
    }

    private static ClassicFont tinyFont() {
        final FfDecoder.Font f = FfDecoder.decodePart(ClassicAssetDecoderTest.ffPart(1, 6,
            new int[][] { glyph('a', 3), glyph(' ', 2) }));
        return ClassicFont.fromAtlas(FfDecoder.toAtlas(f), FfDecoder.metrics(f));
    }

    /** A box layer of the test's own (the fake clock, no thread). */
    private static ClassicAdvisorLayer layer(FakeClock clock) throws Exception {
        return edt(() -> new ClassicAdvisorLayer(new FakeHost(), clock,
            SwingUtilities::invokeLater, false));
    }

    private static <T> T edt(Callable<T> c) throws Exception {
        final Object[] out = new Object[1];
        final Exception[] err = new Exception[1];
        SwingUtilities.invokeAndWait(() -> {
                try {
                    out[0] = c.call();
                } catch (Exception e) {
                    err[0] = e;
                }
            });
        if (err[0] != null) throw err[0];
        @SuppressWarnings("unchecked")
        final T t = (T) out[0];
        return t;
    }

    /** Let the EDT run everything queued so far. */
    private static void flush() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });
    }

    /** An answer that comes later. */
    private static final class Answer {

        final AtomicInteger value = new AtomicInteger(Integer.MIN_VALUE);
        final CountDownLatch done = new CountDownLatch(1);

        int get() throws InterruptedException {
            assertTrue("the box never answered", this.done.await(5, TimeUnit.SECONDS));
            return this.value.get();
        }
    }

    /** Ask a box on the EDT; the answer comes when it closes. */
    private static Answer ask(ClassicAdvisorLayer l, String id) {
        final Answer a = new Answer();
        SwingUtilities.invokeLater(() -> {
                a.value.set(l.show(ClassicAdvisorBox.Request.builder(id)
                    .freeColText("a a a").build()));
                a.done.countDown();
            });
        return a;
    }

    /** Wait until a layer has a box up or due. */
    private static void awaitBusy(ClassicAdvisorLayer l) throws Exception {
        for (int i = 0; i < 200; i++) {
            if (edt(l::isBusy)) return;
            Thread.sleep(10);
        }
        fail("the box never came");
    }

    /**
     * A pane like the HUD's: the game parts on the HUD's layers (the map
     * and the panel, the strip, its dropdowns hidden), the map's box layer
     * and the arrow's stand-in on theirs; the stack over it.
     */
    private static final class Rig {

        /** The full paints at once (the swap in one paint, also minimized). */
        int paintsNow = 0;
        final JLayeredPane pane = new JLayeredPane() {
                @Override
                public void paintImmediately(int x, int y, int w, int h) {
                    if (w >= getWidth() && h >= getHeight()) paintsNow++;
                    super.paintImmediately(x, y, w, h);
                }
            };
        final JPanel map = new JPanel(), panel = new JPanel(), strip = new JPanel(),
            drop = new JPanel(), pointer = new JPanel();
        final JComponent mapBoxes;
        int menuCloses = 0, shown = 0, homes = 0;
        final ClassicScreenStack stack;

        Rig(JComponent mapBoxes, Runnable home) {
            this.mapBoxes = mapBoxes;
            this.pane.setLayout(null);
            this.pane.setSize(1280, 800);
            this.pane.add(this.map, JLayeredPane.DEFAULT_LAYER);
            this.pane.add(this.panel, JLayeredPane.DEFAULT_LAYER);
            this.pane.add(this.strip, JLayeredPane.PALETTE_LAYER);
            this.pane.add(this.drop, JLayeredPane.POPUP_LAYER);
            this.drop.setVisible(false);
            if (mapBoxes != null) this.pane.add(mapBoxes, ClassicHudPane.BOX_LAYER);
            this.pane.add(this.pointer, JLayeredPane.DRAG_LAYER);
            this.map.addComponentListener(new ComponentAdapter() {
                    @Override
                    public void componentShown(ComponentEvent e) {
                        shown++;
                    }
                });
            this.stack = new ClassicScreenStack(this.pane,
                List.of(this.map, this.panel, this.strip, this.drop),
                () -> this.menuCloses++, (home == null) ? () -> this.homes++ : home);
        }

        boolean partsShown() {
            return this.map.isVisible() && this.panel.isVisible() && this.strip.isVisible();
        }

        boolean partsHidden() {
            return !this.map.isVisible() && !this.panel.isVisible()
                && !this.strip.isVisible() && !this.drop.isVisible();
        }
    }

    /** A classic GUI without a client, on the test's game, clock and pane. */
    private static class HostGUI extends ClassicGUI {

        final Game game;
        final Player me;
        final FakeClock clock;

        /** {@link #boxBusy} answers true while set (a box over Europe). */
        boolean busy = false;

        /** At each {@link #europeGone}: were the game parts showing again? */
        final List<Boolean> goneWithMap = new ArrayList<>();

        Rig rig;

        HostGUI(Game game, Player me, FakeClock clock) {
            super(null);
            this.game = game;
            this.me = me;
            this.clock = clock;
        }

        @Override
        protected Game getGame() {
            return this.game;
        }

        @Override
        Player myPlayer() {
            return this.me;
        }

        @Override
        protected Player getMyPlayer() {
            return this.me;
        }

        @Override
        ClassicSlide.Clock waitClock() {
            return (this.clock == null) ? super.waitClock() : this.clock;
        }

        @Override
        boolean boxBusy() {
            return this.busy || super.boxBusy();
        }

        @Override
        void europeGone() {
            this.goneWithMap.add(this.rig != null && this.rig.partsShown());
            super.europeGone();
        }

        /** Host the screens on a rig of the test. */
        Rig host() throws Exception {
            this.rig = edt(() -> new Rig(null, this::focusHome));
            edt(() -> {
                    installScreens(this.rig.stack);
                    return null;
                });
            return this.rig;
        }
    }

    private static int layerOf(JLayeredPane p, Component c) {
        return p.getLayer(c);
    }


    /**
     * T1: the stack with plain panels: the game parts hidden while a
     * screen is up and shown again after its pop (the dropdowns as they
     * were); only the top screen visible; each screen as large as the pane;
     * its boxes on the screen-box layer, under the map's boxes and the
     * arrow; the strip's menu closed before each push; Tab stays the
     * screen's; the build list's pop shows its colony again, the colony's
     * takes the build list with it; a screen popped from under another one
     * changes nothing on screen.
     */
    public void testPushAndPop() throws Exception {
        final FakeClock clock = new FakeClock();
        final ClassicAdvisorLayer mapBoxes = layer(clock);
        final ClassicAdvisorLayer colonyBoxes = layer(clock);
        final Rig r = edt(() -> new Rig(mapBoxes, null));
        final JPanel colony = new JPanel(), list = new JPanel(), europe = new JPanel();
        edt(() -> {
                assertFalse(r.stack.isHosting());
                assertNull(r.stack.probe());
                r.stack.push(ClassicScreenStack.Kind.COLONY, colony, colonyBoxes,
                             "Jamestown", null);
                assertTrue(r.stack.isHosting());
                assertTrue(r.partsHidden());
                assertEquals(1, r.menuCloses);
                assertEquals("the push in one paint", 1, r.paintsNow);
                assertTrue(colony.isVisible());
                assertEquals(new java.awt.Rectangle(0, 0, 1280, 800), colony.getBounds());
                assertEquals(new java.awt.Rectangle(0, 0, 1280, 800), colonyBoxes.getBounds());
                assertEquals(ClassicHudPane.SCREEN_LAYER.intValue(), layerOf(r.pane, colony));
                assertEquals(ClassicHudPane.SCREEN_BOX_LAYER.intValue(),
                             layerOf(r.pane, colonyBoxes));
                assertFalse(colony.getFocusTraversalKeysEnabled());
                // Index 0 is the top: the arrow, the map's boxes, the
                // screen's boxes, the screen, the strip.
                assertTrue(r.pane.getIndexOf(r.pointer) < r.pane.getIndexOf(mapBoxes));
                assertTrue(r.pane.getIndexOf(mapBoxes) < r.pane.getIndexOf(colonyBoxes));
                assertTrue(r.pane.getIndexOf(colonyBoxes) < r.pane.getIndexOf(colony));
                assertTrue(r.pane.getIndexOf(colony) < r.pane.getIndexOf(r.strip));
                assertEquals("colony:Jamestown", r.stack.probe());
                // The build list over its colony: the colony hidden under it.
                r.stack.push(ClassicScreenStack.Kind.BUILD_LIST, list, null, "Jamestown",
                             colony);
                assertEquals(2, r.menuCloses);
                assertEquals(2, r.paintsNow);
                assertFalse(colony.isVisible());
                assertTrue(list.isVisible());
                assertTrue(r.pane.getIndexOf(list) < r.pane.getIndexOf(colony));
                assertSame(list, r.stack.topScreen());
                assertSame("its colony's boxes", colonyBoxes, r.stack.topBoxes());
                assertTrue(r.partsHidden());
                // A second push of the same screen changes nothing.
                r.stack.push(ClassicScreenStack.Kind.BUILD_LIST, list, null, "x", colony);
                assertEquals(2, r.stack.slots().size());
                // Its pop: the colony again.
                assertTrue(r.stack.pop(list, "test"));
                assertNull(list.getParent());
                assertEquals("the pop in one paint", 3, r.paintsNow);
                assertTrue(colony.isVisible());
                assertTrue(r.partsHidden());
                assertSame(colony, r.stack.topScreen());
                assertFalse(r.stack.pop(list, "again"));
                // The colony's pop takes its build list with it.
                r.stack.push(ClassicScreenStack.Kind.BUILD_LIST, list, null, "Jamestown",
                             colony);
                assertTrue(r.stack.pop(colony, "test"));
                assertNull(colony.getParent());
                assertNull(list.getParent());
                assertNull(colonyBoxes.getParent());
                assertFalse(r.stack.isHosting());
                assertTrue(r.partsShown());
                assertFalse("the dropdowns as they were", r.drop.isVisible());
                assertNull(r.stack.probe());
                return null;
            });
        flush();
        assertEquals("the map came back once", 1, r.shown);
        // A screen popped from under another one: nothing visible changes.
        edt(() -> {
                r.stack.push(ClassicScreenStack.Kind.COLONY, colony, null, "Jamestown", null);
                r.stack.push(ClassicScreenStack.Kind.EUROPE, europe, null, "Amsterdam", null);
                assertFalse(colony.isVisible());
                final int paints = r.paintsNow;
                assertTrue(r.stack.pop(colony, "replaced"));
                assertEquals("no paint for a pop from under another one", paints, r.paintsNow);
                assertTrue(europe.isVisible());
                assertTrue(r.partsHidden());
                assertSame(europe, r.stack.topScreen());
                r.stack.popAll("test");
                assertTrue(r.partsShown());
                assertFalse(r.stack.isHosting());
                return null;
            });
        flush();
        assertEquals(2, r.shown);
        assertTrue("the keys after each push and pop", r.homes > 0);
        edt(() -> {
                mapBoxes.dispose();
                return null;
            });
    }

    /**
     * T2: the HUD's key table while a screen is hosted: every bound action
     * reports disabled, so Swing passes the key on
     * ({@code SwingUtilities.notifyAction} false: not consumed) and the
     * hosted screen's own binding of the letter gets it; without a screen
     * the old behaviour: enabled, consumed, and E fires FreeCol's Europe
     * action.
     */
    public void testTheMapKeysAreDisabledWhileAScreenIsUp() throws Exception {
        final JPanel host = new JPanel();
        final boolean[] up = { false };
        final int[] europe = { 0 };
        final Action europeAction = new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    europe[0]++;
                }
            };
        final List<KeyStroke> bound = edt(() -> ClassicKeyMap.install(host, null,
            id -> ("europeAction".equals(id)) ? europeAction : null,
            () -> GUI.ViewMode.MOVE_UNITS, () -> null, () -> false, () -> false,
            null, () -> false, () -> up[0]));
        assertFalse(bound.isEmpty());
        for (String k : new String[] { "A", "U", "E", "R", "L", "F2" }) {
            assertTrue(k, bound.contains(KeyStroke.getKeyStroke(k)));
        }
        final KeyStroke e = KeyStroke.getKeyStroke("E");
        edt(() -> {
                for (KeyStroke ks : bound) {
                    final Action a = host.getActionMap().get(
                        host.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(ks));
                    assertNotNull(ks.toString(), a);
                    up[0] = true;
                    assertFalse(ks + " disabled", a.isEnabled());
                    assertFalse(ks + " not consumed", SwingUtilities.notifyAction(a, ks,
                        new KeyEvent(host, KeyEvent.KEY_PRESSED, 0L, 0,
                                     ks.getKeyCode(), KeyEvent.CHAR_UNDEFINED),
                        host, 0));
                    up[0] = false;
                    assertTrue(ks + " enabled", a.isEnabled());
                }
                // Without a screen the key fires (and is consumed).
                final Action a = host.getActionMap().get(
                    host.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(e));
                assertTrue(SwingUtilities.notifyAction(a, e,
                    new KeyEvent(host, KeyEvent.KEY_PRESSED, 0L, 0, KeyEvent.VK_E,
                                 KeyEvent.CHAR_UNDEFINED), host, 0));
                assertEquals(1, europe[0]);
                // A body called anyway while a screen is up does nothing.
                up[0] = true;
                a.actionPerformed(new ActionEvent(host, ActionEvent.ACTION_PERFORMED, "E"));
                assertEquals(1, europe[0]);
                return null;
            });
        // The old overloads keep the keys enabled.
        final JPanel old = new JPanel();
        final List<KeyStroke> oldBound = edt(() -> ClassicKeyMap.install(old, null,
            id -> null, () -> GUI.ViewMode.MOVE_UNITS, () -> null, () -> false,
            () -> false, null, () -> false));
        edt(() -> {
                for (KeyStroke ks : oldBound) {
                    assertTrue(old.getActionMap().get(old.getInputMap(
                        JComponent.WHEN_IN_FOCUSED_WINDOW).get(ks)).isEnabled());
                }
                return null;
            });
    }

    /**
     * T3 (decision H5): Europe over a hosted colony replaces it, and a box
     * still up on the colony answers "dismissed"; a colony over Europe
     * likewise; the map never shows in between; the colony's own close
     * brings the map back.  Without a game view (no stack) neither screen
     * opens.
     */
    public void testColonyAndEuropeReplaceEachOther() throws Exception {
        final Game game = getStandardGame();
        final Map map = getTestMap(true);
        game.changeMap(map);
        final Colony colony = createStandardColony();
        final Player dutch = colony.getOwner();
        final HostGUI gui = new HostGUI(game, dutch, null);
        // No game view: nothing opens, nothing throws.
        gui.showColonyPanel(colony, null);
        gui.showEuropePanel();
        flush();
        assertNull(gui.hostedScreen());
        final Rig r = gui.host();
        gui.showColonyPanel(colony, null);
        flush();
        assertTrue(edt(gui::colonyHosted));
        assertTrue(edt(() -> gui.hostedScreen() instanceof ClassicColonyPanel));
        assertTrue(edt(r::partsHidden));
        assertEquals(0, r.shown);
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final boolean boxes = pack != null && pack.font(ClassicFont.TINY) != null;
        Answer a = null;
        if (boxes) {
            final ClassicAdvisorLayer cl = edt(gui::colonyBoxLayer);
            a = new Answer();
            final Answer aa = a;
            SwingUtilities.invokeLater(() -> {
                    aa.value.set(gui.colonyBox(ClassicGUI.notice("t3", "Ein Kasten.",
                        "t3", null)));
                    aa.done.countDown();
                });
            awaitBusy(cl);
        }
        gui.showEuropePanel();
        flush();
        assertTrue(edt(gui::europeHosted));
        assertFalse(edt(gui::colonyHosted));
        assertNull(edt(gui::colonyBoxLayer));
        assertTrue(edt(() -> gui.hostedScreen() instanceof ClassicEuropePanel));
        assertEquals(1, edt(() -> gui.screens().slots().size()).intValue());
        assertEquals("no map between the colony and Europe", 0, r.shown);
        if (a != null) assertEquals(ClassicAdvisorBox.Bar.DISMISSED, a.get());
        final ClassicEuropePanel europe = edt(() -> (ClassicEuropePanel) gui.hostedScreen());
        // E again: the same Europe keeps its place.
        gui.showEuropePanel();
        flush();
        assertSame(europe, edt(gui::hostedScreen));
        // The colony over Europe.
        gui.showColonyPanel(colony, null);
        flush();
        assertTrue(edt(gui::colonyHosted));
        assertFalse(edt(gui::europeHosted));
        assertNull(europe.getParent());
        assertEquals("no map between Europe and the colony", 0, r.shown);
        assertTrue("Europe's replacement is no close", gui.goneWithMap.isEmpty());
        // The colony's own Escape: the map again.
        final ClassicColonyPanel cp = edt(() -> (ClassicColonyPanel) gui.hostedScreen());
        edt(() -> {
                cp.getActionMap().get("classic_closeColony").actionPerformed(
                    new ActionEvent(cp, ActionEvent.ACTION_PERFORMED, "esc"));
                return null;
            });
        flush();
        assertFalse(edt(gui::screenHosted));
        assertTrue(edt(r::partsShown));
        assertEquals(1, r.shown);
        // The build list only over its colony; its close shows the colony.
        gui.showBuildQueuePanel(colony);
        flush();
        assertFalse(edt(gui::screenHosted));
        gui.showColonyPanel(colony, null);
        flush();
        gui.showBuildQueuePanel(colony);
        flush();
        assertTrue(edt(() -> gui.hostedScreen() instanceof ClassicBuildQueuePanel));
        assertTrue(edt(gui::colonyHosted));
        final ClassicColonyPanel cp2 = edt(() -> {
                final List<ClassicScreenStack.Slot> s = gui.screens().slots();
                assertEquals(2, s.size());
                assertSame(s.get(0).screen, s.get(1).parent);
                return (ClassicColonyPanel) s.get(0).screen;
            });
        assertFalse(cp2.isVisible());
        final JComponent list = edt(gui::hostedScreen);
        edt(() -> {
                list.getActionMap().get("classic_closeBuildQueue").actionPerformed(
                    new ActionEvent(list, ActionEvent.ACTION_PERFORMED, "esc"));
                return null;
            });
        assertSame(cp2, edt(gui::hostedScreen));
        assertTrue(cp2.isVisible());
        // Europe over the colony with its build list takes both away.
        gui.showBuildQueuePanel(colony);
        flush();
        gui.showEuropePanel();
        flush();
        assertEquals(1, edt(() -> gui.screens().slots().size()).intValue());
        assertTrue(edt(gui::europeHosted));
        assertEquals(1, r.shown);
        // Decision H2: a box asked while a screen is up (a FreeCol notice, a
        // tip: the GUI's own prompter, putBox) is a wood box on that
        // screen's own layer, not the stopgap popup and not the map's.
        Answer h2 = null;
        if (boxes) {
            final ClassicAdvisorLayer el = edt(gui::europeBoxLayer);
            h2 = new Answer();
            final Answer hh = h2;
            SwingUtilities.invokeLater(() -> {
                    hh.value.set(gui.prompter.ask(ClassicGUI.notice("h2", "Eine Nachricht.",
                        "h2", null)));
                    hh.done.countDown();
                });
            awaitBusy(el);
        }
        edt(() -> {
                gui.screens().popAll("test");
                return null;
            });
        if (h2 != null) assertEquals(ClassicAdvisorBox.Bar.DISMISSED, h2.get());
    }

    /**
     * T4: a box of the map that is up or due stays over a hosted screen
     * (its layer above the screen's), so it can never hide under it; the
     * screen's pop answers its own box "dismissed" and leaves the map's
     * box alone.
     */
    public void testTheMapsBoxStaysOverAScreen() throws Exception {
        final FakeClock clock = new FakeClock();
        final ClassicAdvisorLayer mapBoxes = layer(clock);
        final ClassicAdvisorLayer screenBoxes = layer(clock);
        final Rig r = edt(() -> new Rig(mapBoxes, null));
        final Answer onMap = ask(mapBoxes, "tip");
        awaitBusy(mapBoxes);
        final JPanel europe = new JPanel();
        edt(() -> {
                r.stack.push(ClassicScreenStack.Kind.EUROPE, europe, screenBoxes,
                             "Amsterdam", null);
                assertTrue(r.pane.getIndexOf(mapBoxes) < r.pane.getIndexOf(screenBoxes));
                assertTrue(r.pane.getIndexOf(screenBoxes) < r.pane.getIndexOf(europe));
                assertTrue(mapBoxes.isVisible());
                return null;
            });
        final Answer onEurope = ask(screenBoxes, "howmuch");
        awaitBusy(screenBoxes);
        edt(() -> {
                assertTrue(r.stack.pop(europe, "test"));
                return null;
            });
        assertEquals(ClassicAdvisorBox.Bar.DISMISSED, onEurope.get());
        assertTrue("the map's box is still up", edt(mapBoxes::isBusy));
        assertSame(r.pane, mapBoxes.getParent());
        edt(() -> {
                mapBoxes.dispose();
                return null;
            });
        assertEquals(ClassicAdvisorBox.Bar.DISMISSED, onMap.get());
    }

    /**
     * T5: where the keys go home ({@link ClassicGUI#home}): a box of the
     * map that is up or due, else a box over the hosted screen, else the
     * screen, else the map; with the GUI's own stack the hidden map under
     * a screen never gets them.
     */
    public void testTheKeysGoHome() throws Exception {
        final FakeClock clock = new FakeClock();
        final ClassicAdvisorLayer mapBoxes = layer(clock);
        final ClassicAdvisorLayer screenBoxes = layer(clock);
        final JPanel screen = new JPanel(), map = new JPanel();
        assertSame(map, ClassicGUI.home(mapBoxes, null, null, map));
        assertSame(screen, ClassicGUI.home(mapBoxes, screenBoxes, screen, map));
        final Answer s = ask(screenBoxes, "box");
        awaitBusy(screenBoxes);
        assertSame(screenBoxes, ClassicGUI.home(mapBoxes, screenBoxes, screen, map));
        assertSame("no screen: the map", map, ClassicGUI.home(mapBoxes, screenBoxes, null, map));
        final Answer m = ask(mapBoxes, "box");
        awaitBusy(mapBoxes);
        assertSame(mapBoxes, ClassicGUI.home(mapBoxes, screenBoxes, screen, map));
        edt(() -> {
                mapBoxes.dispose();
                screenBoxes.dispose();
                return null;
            });
        m.get();
        s.get();
        // The GUI's own: the screen while it is hosted, the map after.
        final HostGUI gui = new HostGUI(null, null, null);
        final ClassicMapViewer mv = edt(() -> new ClassicMapViewer(null, gui, null, false));
        gui.mapViewer = mv;
        final Rig r = gui.host();
        final JPanel colony = new JPanel();
        try {
            edt(() -> {
                    assertSame(mv, gui.homeComponent());
                    r.stack.push(ClassicScreenStack.Kind.COLONY, colony, null, "Jamestown",
                                 null);
                    assertSame(colony, gui.homeComponent());
                    r.stack.pop(colony, "test");
                    assertSame(mv, gui.homeComponent());
                    return null;
                });
        } finally {
            edt(() -> {
                    mv.dispose();
                    return null;
                });
        }
    }

    /**
     * T6, J1 on a fake clock: the last ship sails from the hosted Europe,
     * which closes {@link ClassicVoyages#EUROPE_CLOSE_MS} later, the map's
     * parts showing again before {@code europeGone}; a close for an Europe
     * that was replaced meanwhile does nothing to the new one; a box over
     * Europe postpones the close by {@link ClassicVoyages#POLL_MS} until
     * it is gone.
     */
    public void testEuropeClosesAfterTheLastShip() throws Exception {
        final Game game = getStandardGame();
        game.changeMap(getTestMap(true));
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final Unit ship = new ServerUnit(game, dutch.getEurope(), dutch, merchantman);
        final FakeClock clock = new FakeClock();
        final HostGUI gui = new HostGUI(game, dutch, clock);
        final Rig r = gui.host();
        final ClassicOneShot timer = gui.europeCloseTimer();
        assertNotNull(timer);
        gui.showEuropePanel();
        flush();
        assertTrue(edt(gui::europeHosted));
        // In port: nothing.
        edt(() -> {
                gui.europeShipSailed(ship);
                return null;
            });
        assertFalse(timer.isScheduled());
        ship.setLocation(dutch.getHighSeas());
        ship.setDestination(game.getMap());
        edt(() -> {
                gui.europeShipSailed(ship);
                return null;
            });
        assertTrue(timer.isScheduled());
        clock.advanceMs(ClassicVoyages.EUROPE_CLOSE_MS - 0.1);
        assertFalse(timer.runIfDue());
        flush();
        assertTrue(edt(gui::europeHosted));
        clock.advanceMs(0.2);
        assertTrue(timer.runIfDue());
        flush();
        assertFalse(edt(gui::europeHosted));
        assertEquals("the map before europeGone", List.of(true), gui.goneWithMap);
        assertTrue(edt(r::partsShown));

        // Replaced meanwhile: the old one's close does nothing.
        final Unit second = new ServerUnit(game, dutch.getEurope(), dutch, merchantman);
        gui.showEuropePanel();
        flush();
        final ClassicEuropePanel p1 = edt(() -> (ClassicEuropePanel) gui.hostedScreen());
        second.setLocation(dutch.getHighSeas());
        edt(() -> {
                gui.europeShipSailed(second);
                return null;
            });
        assertTrue(timer.isScheduled());
        edt(() -> {          // the player's Escape, then E
                p1.getActionMap().get("classic_closeEurope").actionPerformed(
                    new ActionEvent(p1, ActionEvent.ACTION_PERFORMED, "esc"));
                return null;
            });
        assertFalse("the close cancels it", timer.isScheduled());
        assertEquals(List.of(true, true), gui.goneWithMap);
        gui.showEuropePanel();
        flush();
        final ClassicEuropePanel p2 = edt(() -> (ClassicEuropePanel) gui.hostedScreen());
        assertNotSame(p1, p2);
        edt(() -> {
                gui.europeAutoClose(p1);
                return null;
            });
        assertSame(p2, edt(gui::hostedScreen));
        assertEquals(2, gui.goneWithMap.size());

        // A box over Europe: the close after it.
        edt(() -> {
                gui.europeShipSailed(second);
                return null;
            });
        gui.busy = true;
        clock.advanceMs(ClassicVoyages.EUROPE_CLOSE_MS);
        assertTrue(timer.runIfDue());
        flush();
        assertTrue("held by the box", edt(gui::europeHosted));
        assertTrue(timer.isScheduled());
        clock.advanceMs(ClassicVoyages.POLL_MS - 0.1);
        assertFalse(timer.runIfDue());
        clock.advanceMs(0.2);
        assertTrue(timer.runIfDue());
        flush();
        assertTrue(edt(gui::europeHosted));
        gui.busy = false;
        clock.advanceMs(ClassicVoyages.POLL_MS);
        assertTrue(timer.runIfDue());
        flush();
        assertFalse(edt(gui::europeHosted));
        assertEquals(List.of(true, true, true), gui.goneWithMap);
    }

    /**
     * T7: the recorder's index hint is off while a classic screen is
     * hosted (the hidden map's hint would relabel screen pixels of a
     * cycling duplicate's colour), on again after its pop.
     */
    public void testNoIndexHintUnderAScreen() throws Exception {
        final HostGUI gui = new HostGUI(null, null, null);
        final ClassicMapViewer mv = edt(() -> new ClassicMapViewer(null, gui, null, false));
        final Rig r = gui.host();
        final JPanel europe = new JPanel();
        final byte[] hint = new byte[240 * 192];
        try {
            edt(() -> {
                    mv.setTerrain(ClassicTerrainLayerTest.layer(), null);
                    assertTrue(mv.indexHint(hint));
                    r.stack.push(ClassicScreenStack.Kind.EUROPE, europe, null, "Amsterdam",
                                 null);
                    assertTrue(gui.screenHosted());
                    assertFalse("no hint over a screen", mv.indexHint(hint));
                    r.stack.pop(europe, "test");
                    assertTrue(mv.indexHint(hint));
                    return null;
                });
        } finally {
            edt(() -> {
                    mv.dispose();
                    return null;
                });
        }
    }

    /**
     * T8, a census of the sources: the colony, build-list and Europe
     * screens have no window of their own any more (one {@code new JFrame}
     * left: the report screen's; no field of the old windows; one
     * {@code toFront}, Alt+Enter's; one {@code ICONIFIED}, the report
     * window's rule), no activation hook, the keys come back through
     * {@code focusHome} (never straight to the map), the HUD's keys are
     * installed with the hosting rule and report it as {@code isEnabled}.
     */
    public void testNoScreenWindowIsLeft() throws Exception {
        final String gui = read("ClassicGUI");
        assertEquals("only the report's window", 1, count(gui, "new JFrame("));
        final int report = gui.indexOf("new JFrame(Messages.message(titleKey))");
        assertTrue(report > gui.indexOf("private FreeColPanel showReport("));
        for (String gone : new String[] { "colonyFrame", "europeFrame", "buildQueueFrame",
                                          "mapActivated", "windowActivated", "raiseEurope(",
                                          "mapViewer.requestFocusInWindow" }) {
            assertEquals(gone, 0, count(gui, gone));
        }
        assertEquals("Alt+Enter's", 1, count(gui, "toFront()"));
        assertEquals("the report window's rule", 1, count(gui, "ICONIFIED"));
        final String flat = gui.replaceAll("\\s+", " ");
        final int install = flat.indexOf("ClassicKeyMap.install(this.hudPane");
        assertTrue(install > 0);
        final String call = flat.substring(install,
            flat.indexOf("this.boxLayer = new ClassicAdvisorLayer(new BoxHost", install));
        assertTrue(call, call.contains("this::chainWaitsForEurope,")
                   && call.contains("this::screenHosted);"));
        for (String host : new String[] { "private final class BoxHost",
                                          "private final class ScreenBoxHost" }) {
            final int at = flat.indexOf(host);
            assertTrue(host, at > 0);
            final int idle = flat.indexOf("public void idle() {", at);
            final String body = flat.substring(idle, flat.indexOf("}", idle));
            assertTrue(host + ": " + body, body.contains("focusHome();"));
        }
        final String keys = read("ClassicKeyMap").replaceAll("\\s+", " ");
        assertTrue(keys.contains("public boolean isEnabled() { return !screenUp.getAsBoolean()"));
        // The harness finds the hosted screen, not a window.
        final String harness = read("ClassicTestHarness");
        assertTrue(harness.contains("gui.hostedScreen()"));
        assertEquals(0, count(harness, "getContentPane() : null"));
    }

    private static String read(String cls) throws Exception {
        return new String(Files.readAllBytes(new File(
            "src/net/sf/freecol/client/gui/classic/" + cls + ".java").toPath()),
            StandardCharsets.UTF_8);
    }

    private static int count(String s, String what) {
        int n = 0;
        for (int i = s.indexOf(what); i >= 0; i = s.indexOf(what, i + 1)) n++;
        return n;
    }
}
