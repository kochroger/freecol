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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.KeyStroke;


/**
 * Runs a {@link ClassicScript} on its own (daemon) thread against a
 * {@link Host} -- in the game the {@link ClassicTestHarness}, which posts
 * the keys through the same Swing dispatch a real key press takes, so the
 * behaviour is that of a real player.  Every command is logged to the
 * recorder ({@code script}); a failure ({@code script-error}: a timeout,
 * an exception) ends the run, stops the recorder and quits the game, so an
 * unattended run never hangs.
 *
 * <p>The outcome goes to {@code <script>.result}: {@code running} while it
 * runs, then {@code ok}, {@code ended} (no {@code quit} at the end; the
 * game goes on) or {@code error <line>: <reason>}.
 */
final class ClassicScriptDriver {

    private static final Logger logger
        = Logger.getLogger(ClassicScriptDriver.class.getName());

    /** System property: the script file. */
    static final String SCRIPT_PROPERTY = "freecol.classic.script";

    /** How often the waits look (ms). */
    static final long POLL_MS = 50L;

    /** How long the controls must stay ours for {@code waitIdle} (ms). */
    static final long IDLE_STABLE_MS = 300L;

    /** How long a key is held down (ms), about a quick real press. */
    static final long KEY_HOLD_MS = 80L;

    /** A map cell's size on the 320x200 canvas (the 15x12 view of 16x16 cells). */
    static final int CELL = 16;

    /**
     * The 320x200 canvas point of map tile (x, y) in the view whose
     * top-left tile is {@code origin}: its cell's centre, under the menu
     * strip ({@code tclick}).
     *
     * @param origin The view's top-left tile {x, y}, or null.
     * @param x Map x.
     * @param y Map y.
     * @return {x, y} on the canvas, or null if the tile is not in the view.
     */
    static int[] tileCanvasPoint(int[] origin, int x, int y) {
        if (origin == null) return null;
        final int c = x - origin[0], r = y - origin[1];
        if (c < 0 || r < 0 || c >= ClassicHud.VIEW_COLS || r >= ClassicHud.VIEW_ROWS) {
            return null;
        }
        return new int[] { c * CELL + CELL / 2,
                           ClassicMenuBar.HEIGHT + r * CELL + CELL / 2 };
    }

    /** What the script acts on. */
    interface Host {

        /** @return Whether the in-game HUD is up. */
        boolean inGame();

        /**
         * @return Whether our player has the controls now: our turn, no
         *     dialog or scene, no slide.
         */
        boolean isIdle();

        /** @return The current turn number, or -1 without a game. */
        int turnNumber();

        /**
         * @return The advisor box on screen as the recorder's probe writes
         *     it ({@code id:bar}, blanks as {@code _}), or null.
         */
        default String boxOnScreen() {
            return null;
        }

        /**
         * Press and release a key, as a real one.
         *
         * @param key The key.
         * @param holdMs How long it is held.
         * @exception InterruptedException if interrupted.
         */
        void key(KeyStroke key, long holdMs) throws InterruptedException;

        /**
         * Click the left button on the canvas.
         *
         * @param x Canvas x.
         * @param y Canvas y.
         * @exception InterruptedException if interrupted.
         */
        void click(int x, int y) throws InterruptedException;

        /**
         * Move the pointer over the canvas, no button held.
         *
         * @param x Canvas x.
         * @param y Canvas y.
         */
        void move(int x, int y);

        /**
         * Click the left button on the open classic screen's own canvas
         * (the Europe screen).
         *
         * @param x Screen canvas x.
         * @param y Screen canvas y.
         * @exception ScriptException if no such screen is open.
         */
        default void screenClick(int x, int y) throws ScriptException {
            throw new ScriptException("no classic screen to click");
        }

        /**
         * Click the left button on a map tile as the map shows it now
         * ({@link ClassicScriptDriver#tileCanvasPoint}).
         *
         * @param x Map x.
         * @param y Map y.
         * @exception ScriptException if there is no map or the tile is not
         *     in the view.
         * @exception InterruptedException if interrupted.
         */
        default void tileClick(int x, int y)
            throws ScriptException, InterruptedException {
            throw new ScriptException("no map to click");
        }

        /**
         * Set an option.
         *
         * @param name A classic pref or a mapped client option name.
         * @param value The value.
         */
        void pref(String name, boolean value);

        /**
         * Give the active unit a goto order to a map tile.
         *
         * @param x The tile's column.
         * @param y The tile's row.
         * @exception ScriptException if there is no active unit or no path.
         */
        void gotoTile(int x, int y) throws ScriptException;

        /** Stop the recorder and quit the game (may not return). */
        void quit();
    }

    /** A failed command. */
    static final class ScriptException extends Exception {

        ScriptException(String message) {
            super(message);
        }
    }

    /** The script. */
    private final ClassicScript script;

    /** The host. */
    private final Host host;

    /**
     * The turn {@code markTurn} noted, for the next {@code waitTurn}, or
     * {@link Integer#MIN_VALUE} for none.
     */
    private int markedTurn = Integer.MIN_VALUE;

    /** The result file, or null. */
    private final File result;


    ClassicScriptDriver(ClassicScript script, Host host, File result) {
        this.script = script;
        this.host = host;
        this.result = result;
    }

    /**
     * Read the script file and run it on a new daemon thread.  A script
     * that does not parse runs nothing: it is reported and the game quits.
     *
     * @param file The script.
     * @param host The host.
     * @return The thread, or null if the script did not parse.
     */
    static Thread start(File file, Host host) {
        final File result = new File(file.getPath() + ".result");
        final ClassicScript script;
        try {
            script = ClassicScript.parse(
                Files.readAllLines(file.toPath(), StandardCharsets.UTF_8));
        } catch (IOException | IllegalArgumentException e) {
            logger.log(Level.SEVERE, "Classic script " + file + " not run", e);
            ClassicFrameRecorder.event("script-error", "parse: " + e.getMessage());
            writeResult(result, "error parse: " + e.getMessage());
            final Thread q = new Thread(host::quit, "classic-script-quit");
            q.setDaemon(true);
            q.start();
            return null;
        }
        logger.info("Classic script " + file + ": " + script.commands.size()
            + " commands.");
        final ClassicScriptDriver d = new ClassicScriptDriver(script, host, result);
        final Thread t = new Thread(d::run, "classic-script");
        t.setDaemon(true);
        t.start();
        return t;
    }

    /** Run every command; on a failure report it and quit. */
    void run() {
        writeResult(this.result, "running");
        for (ClassicScript.Command c : this.script.commands) {
            ClassicFrameRecorder.event("script", c.toString());
            try {
                execute(c);
            } catch (ScriptException | RuntimeException e) {
                fail(c, e.getMessage());
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail(c, "interrupted");
                return;
            }
            if (c.op == ClassicScript.Op.QUIT) return;
        }
        ClassicFrameRecorder.event("script-end", "no quit; the game goes on");
        writeResult(this.result, "ended");
    }

    /** Report a failure and quit. */
    private void fail(ClassicScript.Command c, String why) {
        final String msg = "line " + c.line + " (" + c.text + "): " + why;
        logger.severe("Classic script failed, " + msg);
        ClassicFrameRecorder.event("script-error", msg);
        writeResult(this.result, "error " + msg);
        this.host.quit();
    }

    /**
     * Execute one command.
     *
     * @param c The command.
     * @exception ScriptException if a wait times out.
     * @exception InterruptedException if interrupted.
     */
    void execute(ClassicScript.Command c)
        throws ScriptException, InterruptedException {
        switch (c.op) {
        case WAIT:
            Thread.sleep(c.number);
            break;
        case KEY:
            this.host.key(c.key, KEY_HOLD_MS);
            break;
        case CLICK:
            this.host.click((int)c.number, c.y);
            break;
        case MOVE:
            this.host.move((int)c.number, c.y);
            break;
        case SCREEN_CLICK:
            this.host.screenClick((int)c.number, c.y);
            Thread.sleep(KEY_HOLD_MS);
            break;
        case TILE_CLICK:
            this.host.tileClick((int)c.number, c.y);
            break;
        case WAIT_GAME:
            await(this.host::inGame, 0L, c.number, "the in-game HUD");
            break;
        case WAIT_IDLE:
            await(this.host::isIdle, IDLE_STABLE_MS, c.number, "the controls");
            break;
        case MARK_TURN:
            this.markedTurn = this.host.turnNumber();
            break;
        case WAIT_TURN: {
            final int start = (this.markedTurn != Integer.MIN_VALUE) ? this.markedTurn
                : this.host.turnNumber();
            this.markedTurn = Integer.MIN_VALUE;
            final BooleanSupplier cond = (c.key == null)
                ? () -> this.host.turnNumber() > start && this.host.isIdle()
                : answering(c.key, () -> this.host.turnNumber() > start
                            && this.host.isIdle());
            await(cond, IDLE_STABLE_MS, c.number, "a turn after turn " + start);
            break;
        }
        case WAIT_BOX:
            waitBox(c);
            break;
        case PREF:
            this.host.pref(c.name, c.value);
            break;
        case GOTO:
            this.host.gotoTile((int)c.number, c.y);
            break;
        case LOG:
            ClassicFrameRecorder.event("log", c.name);
            break;
        case QUIT:
            writeResult(this.result, "ok");
            this.host.quit();
            break;
        default:
            throw new ScriptException("unknown command " + c.op);
        }
    }

    /**
     * A condition that first answers a box on screen with {@code key}, once
     * per box, as {@link #waitBox} does ({@code waitTurn} with a key: the
     * turn start's notices); while a box is up the condition is false.
     *
     * @param key The key.
     * @param cond The condition.
     * @return The answering condition.
     */
    private BooleanSupplier answering(KeyStroke key, BooleanSupplier cond) {
        final String[] answered = { null };
        return () -> {
            final String b = this.host.boxOnScreen();
            if (b == null) {
                answered[0] = null;
                return cond.getAsBoolean();
            }
            if (!b.equals(answered[0])) {
                ClassicFrameRecorder.event("log", "waitTurn answers " + b);
                answered[0] = b;
                try {
                    this.host.key(key, KEY_HOLD_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return false;
        };
    }

    /**
     * {@code waitBox}: until a box with the prefix is on screen; with a
     * key, every other box on screen meanwhile gets it (once per box: the
     * next box comes no sooner than the layer's chain time).
     *
     * @param c The command.
     * @exception ScriptException on a timeout.
     * @exception InterruptedException if interrupted.
     */
    private void waitBox(ClassicScript.Command c)
        throws ScriptException, InterruptedException {
        final long end = System.nanoTime() + c.number * 1_000_000L;
        String answered = null;
        for (;;) {
            final String b = this.host.boxOnScreen();
            if (b != null && b.startsWith(c.name)) return;
            if (b != null && c.key != null && !b.equals(answered)) {
                ClassicFrameRecorder.event("log", "waitBox answers " + b);
                this.host.key(c.key, KEY_HOLD_MS);
                answered = b;
            } else if (b == null) {
                answered = null;
            }
            if (System.nanoTime() >= end) {
                throw new ScriptException("timeout after " + c.number
                    + " ms waiting for a box " + c.name);
            }
            Thread.sleep(POLL_MS);
        }
    }

    /**
     * Wait until a condition has held for {@code stableMs}.
     *
     * @param cond The condition.
     * @param stableMs How long it must hold (0: once is enough).
     * @param timeoutMs The timeout.
     * @param what What is waited for (for the message).
     * @exception ScriptException on a timeout.
     * @exception InterruptedException if interrupted.
     */
    static void await(BooleanSupplier cond, long stableMs, long timeoutMs,
                      String what) throws ScriptException, InterruptedException {
        final long start = System.nanoTime();
        final long end = start + timeoutMs * 1_000_000L;
        long since = -1L;
        for (;;) {
            final long now = System.nanoTime();
            if (cond.getAsBoolean()) {
                if (since < 0) since = now;
                if (now - since >= stableMs * 1_000_000L) return;
            } else {
                since = -1L;
            }
            if (now >= end) {
                throw new ScriptException("timeout after " + timeoutMs
                    + " ms waiting for " + what);
            }
            Thread.sleep(POLL_MS);
        }
    }

    /** Write the result file (best effort). */
    private static void writeResult(File f, String text) {
        if (f == null) return;
        try {
            Files.write(f.toPath(), List.of(text), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Classic script: " + f + " not written", e);
        }
    }
}
