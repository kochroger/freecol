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

import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.KeyStroke;

import junit.framework.TestCase;


/**
 * Headless tests of the acceptance input script: the parser
 * ({@link ClassicScript}) and the driver ({@link ClassicScriptDriver}) on a
 * fake host.
 */
public class ClassicScriptTest extends TestCase {

    private static ClassicScript parse(String... lines) {
        return ClassicScript.parse(Arrays.asList(lines));
    }

    private static void assertBad(String line, String fragment) {
        try {
            parse("wait 1", line);
            fail("accepted: " + line);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().startsWith("line 2 '"));
            assertTrue(e.getMessage(), e.getMessage().contains(fragment));
        }
    }

    public void testTheProofScriptParses() {
        final ClassicScript s = parse(
            "# W1 proof: idle, four moves, idle, end of turn",
            "waitGame",
            "waitIdle 30000",
            "wait 10000      # idle",
            "key LEFT",
            "wait 2000",
            "Key NUMPAD7",
            "key alt G",
            "click 10 20",
            "",
            "pref moveAccelerator on",
            "pref autoSave false",
            "waitTurn",
            "log hello, world",
            "QUIT");
        final List<ClassicScript.Command> c = s.commands;
        assertEquals(13, c.size());
        assertEquals(ClassicScript.Op.WAIT_GAME, c.get(0).op);
        assertEquals(ClassicScript.WAIT_GAME_TIMEOUT, c.get(0).number);
        assertEquals(2, c.get(0).line);
        assertEquals(ClassicScript.Op.WAIT_IDLE, c.get(1).op);
        assertEquals(30000L, c.get(1).number);
        assertEquals(ClassicScript.Op.WAIT, c.get(2).op);
        assertEquals(10000L, c.get(2).number);
        assertEquals("wait 10000", c.get(2).text);
        assertEquals(ClassicScript.Op.KEY, c.get(3).op);
        assertEquals(KeyEvent.VK_LEFT, c.get(3).key.getKeyCode());
        assertEquals(0, c.get(3).key.getModifiers());
        assertEquals(KeyEvent.VK_NUMPAD7, c.get(5).key.getKeyCode());
        assertEquals(KeyEvent.VK_G, c.get(6).key.getKeyCode());
        assertTrue((c.get(6).key.getModifiers() & KeyEvent.ALT_DOWN_MASK) != 0);
        assertEquals(ClassicScript.Op.CLICK, c.get(7).op);
        assertEquals(10L, c.get(7).number);
        assertEquals(20, c.get(7).y);
        assertEquals(ClassicScript.Op.PREF, c.get(8).op);
        assertEquals("moveAccelerator", c.get(8).name);
        assertTrue(c.get(8).value);
        assertEquals("autoSave", c.get(9).name);
        assertFalse(c.get(9).value);
        assertEquals(ClassicScript.Op.WAIT_TURN, c.get(10).op);
        assertEquals(ClassicScript.WAIT_TURN_TIMEOUT, c.get(10).number);
        assertEquals(ClassicScript.Op.LOG, c.get(11).op);
        assertEquals("hello, world", c.get(11).name);
        assertEquals(ClassicScript.Op.QUIT, c.get(12).op);
        assertEquals(15, c.get(12).line);
        assertTrue(parse("# nothing", "   ").commands.isEmpty());
    }

    public void testMalformedLinesAreRejectedWithTheirLine() {
        assertBad("wait", "milliseconds expected");
        assertBad("wait -5", "negative");
        assertBad("wait soon", "not a number");
        assertBad("waitIdle x", "not a number");
        assertBad("key", "key expected");
        assertBad("key FOO", "not a key");
        assertBad("key released LEFT", "pressed keys only");
        assertBad("key typed a", "pressed keys only");
        assertBad("click 320 0", "outside 0..319");
        assertBad("click 0 200", "outside 0..199");
        assertBad("click 5", "x and y");
        assertBad("pref nope on", "unknown pref");
        assertBad("pref moveAccelerator maybe", "on or off");
        assertBad("pref moveAccelerator", "name and on/off");
        assertBad("goto 5", "goto needs x and y");
        assertBad("goto 5 x", "not a number");
        assertBad("quit now", "no argument");
        assertBad("jump 3", "unknown command");
        assertBad("waitBox", "prefix");
        assertBad("waitBox a 5 FOO", "not a key");
        assertBad("waitBox a x", "not a number");
    }

    public void testKeyCharactersAndLocations() {
        final KeyStroke enter = ClassicScript.keyStroke("ENTER");
        assertEquals('\n', ClassicScript.keyChar(enter));
        assertEquals(' ', ClassicScript.keyChar(ClassicScript.keyStroke("SPACE")));
        assertEquals('b', ClassicScript.keyChar(ClassicScript.keyStroke("B")));
        assertEquals('B', ClassicScript.keyChar(ClassicScript.keyStroke("shift B")));
        assertEquals('7', ClassicScript.keyChar(ClassicScript.keyStroke("NUMPAD7")));
        assertEquals(KeyEvent.CHAR_UNDEFINED,
            ClassicScript.keyChar(ClassicScript.keyStroke("ctrl N")));
        assertEquals(KeyEvent.CHAR_UNDEFINED,
            ClassicScript.keyChar(ClassicScript.keyStroke("LEFT")));
        assertEquals(KeyEvent.KEY_LOCATION_NUMPAD,
            ClassicScript.keyLocation(ClassicScript.keyStroke("NUMPAD1")));
        assertEquals(KeyEvent.KEY_LOCATION_STANDARD,
            ClassicScript.keyLocation(ClassicScript.keyStroke("END")));
        // Every key the map binds is a valid script key.
        for (String k : ClassicMapViewer.BOUND_KEYS) {
            assertEquals(k, KeyStroke.getKeyStroke(k), ClassicScript.keyStroke(k));
        }
    }

    /** A host that records what it is asked to do. */
    private static class FakeHost implements ClassicScriptDriver.Host {

        final List<String> calls = new ArrayList<>();
        volatile boolean game = true;
        volatile int idleAfter = 0;      // isIdle() calls before it says yes
        volatile int turn = 1;
        volatile int turnAfter = -1;     // turnNumber() calls before the turn advances
        private int turnCalls = 0;
        private int idleCalls = 0;
        volatile boolean quit = false;

        @Override
        public boolean inGame() {
            return this.game;
        }

        @Override
        public synchronized boolean isIdle() {
            this.idleCalls++;
            return this.idleCalls > this.idleAfter;
        }

        @Override
        public synchronized int turnNumber() {
            this.turnCalls++;
            if (this.turnAfter >= 0 && this.turnCalls > this.turnAfter) this.turn = 2;
            return this.turn;
        }

        @Override
        public void key(KeyStroke key, long holdMs) {
            this.calls.add("key " + key + " " + holdMs);
        }

        @Override
        public void click(int x, int y) {
            this.calls.add("click " + x + "," + y);
        }

        @Override
        public void gotoTile(int x, int y) {
            this.calls.add("goto " + x + "," + y);
        }

        @Override
        public void pref(String name, boolean value) {
            this.calls.add("pref " + name + "=" + value);
        }

        @Override
        public void quit() {
            this.calls.add("quit");
            this.quit = true;
        }
    }

    private static File resultFile() throws IOException {
        final File f = File.createTempFile("classic-script", ".result");
        f.deleteOnExit();
        return f;
    }

    private static String result(File f) throws IOException {
        return Files.readAllLines(f.toPath(), StandardCharsets.UTF_8).get(0);
    }

    public void testTheDriverRunsTheCommandsInOrder() throws IOException {
        final FakeHost h = new FakeHost();
        h.idleAfter = 3;
        h.turnAfter = 3;
        final File r = resultFile();
        final long t0 = System.nanoTime();
        new ClassicScriptDriver(parse("waitGame 1000", "waitIdle 5000", "wait 100",
                                      "key NUMPAD7", "click 1 2", "goto 36 44",
                                      "pref endTurnPrompt on", "waitTurn 5000", "quit"),
                                h, r).run();
        final long ms = (System.nanoTime() - t0) / 1_000_000L;
        assertEquals(Arrays.asList("key pressed NUMPAD7 " + ClassicScriptDriver.KEY_HOLD_MS,
                                   "click 1,2", "goto 36,44", "pref endTurnPrompt=true",
                                   "quit"),
                     h.calls);
        assertEquals("ok", result(r));
        // Two stable idle waits and the 100 ms sleep.
        assertTrue(ms + " ms", ms >= 100 + 2 * ClassicScriptDriver.IDLE_STABLE_MS);
    }

    /**
     * {@code waitBox}: until a box whose probe id starts with the prefix
     * is on screen (another box, or none, keeps it waiting).
     */
    public void testWaitBox() throws IOException {
        final ClassicScript s = parse("waitBox WHICHFREEDOM", "waitBox pedia 900");
        assertEquals(ClassicScript.Op.WAIT_BOX, s.commands.get(0).op);
        assertEquals("WHICHFREEDOM", s.commands.get(0).name);
        assertEquals(ClassicScript.WAIT_BOX_TIMEOUT, s.commands.get(0).number);
        assertEquals(900L, s.commands.get(1).number);
        final int[] asked = { 0 };
        final FakeHost h = new FakeHost() {
                @Override
                public String boxOnScreen() {
                    return (++asked[0] < 3) ? null
                        : (asked[0] < 5) ? "message_x:-1" : "WHICHFREEDOM:0";
                }
            };
        final File r = resultFile();
        new ClassicScriptDriver(parse("waitBox WHICHFREEDOM 5000 ENTER", "quit"),
                                h, r).run();
        assertEquals(5, asked[0]);
        assertEquals("ok", result(r));
        // ENTER answered the other box once (it stayed for two looks).
        assertEquals(Arrays.asList("key pressed ENTER " + ClassicScriptDriver.KEY_HOLD_MS,
                                   "quit"), h.calls);
        assertEquals(java.awt.event.KeyEvent.VK_ENTER,
                     parse("waitBox x 9 ENTER").commands.get(0).key.getKeyCode());
        assertNull(s.commands.get(0).key);
        // Never: a timeout.
        final FakeHost none = new FakeHost();
        final File r2 = resultFile();
        new ClassicScriptDriver(parse("waitBox pedia 200"), none, r2).run();
        assertTrue(result(r2), result(r2).contains("timeout after 200 ms waiting for a box pedia"));
    }

    public void testATimeoutEndsTheRunAndQuits() throws IOException {
        final FakeHost h = new FakeHost();
        h.idleAfter = Integer.MAX_VALUE;
        final File r = resultFile();
        new ClassicScriptDriver(parse("waitIdle 200", "key ENTER"), h, r).run();
        assertEquals(Arrays.asList("quit"), h.calls);
        assertTrue(result(r), result(r).startsWith("error line 1 (waitIdle 200): timeout after 200 ms"));
    }

    public void testAScriptWithoutQuitEnds() throws IOException {
        final FakeHost h = new FakeHost();
        final File r = resultFile();
        new ClassicScriptDriver(parse("key SPACE", "log done"), h, r).run();
        assertEquals(Arrays.asList("key pressed SPACE " + ClassicScriptDriver.KEY_HOLD_MS),
                     h.calls);
        assertFalse(h.quit);
        assertEquals("ended", result(r));
    }

    public void testABrokenScriptRunsNothing() throws Exception {
        final File f = File.createTempFile("classic-script", ".txt");
        f.deleteOnExit();
        new File(f.getPath() + ".result").deleteOnExit();
        Files.write(f.toPath(), Arrays.asList("key LEFT", "teleport 3 4"),
                    StandardCharsets.UTF_8);
        final FakeHost h = new FakeHost();
        assertNull(ClassicScriptDriver.start(f, h));
        for (int i = 0; i < 100 && !h.quit; i++) Thread.sleep(20);
        assertEquals(Arrays.asList("quit"), h.calls);
        assertTrue(result(new File(f.getPath() + ".result")).startsWith("error parse: line 2"));
    }
}
