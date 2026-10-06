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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import javax.swing.KeyStroke;


/**
 * The <b>input script</b> of the acceptance harness
 * ({@code -Dfreecol.classic.script=<file>}, run by
 * {@link ClassicScriptDriver}): one command per line, so a test of the
 * feel of moving needs no human at the keyboard.  Swing-free apart from
 * {@link KeyStroke} parsing, and immutable, so it is unit-tested headless.
 *
 * <p>Format: UTF-8 text; blank lines and everything after {@code #} are
 * ignored; a command and its arguments are separated by blanks; command
 * names are case-insensitive.
 * <ul>
 *   <li>{@code wait <ms>}: sleep.</li>
 *   <li>{@code key <keystroke>}: press and release a key, in
 *       {@link KeyStroke#getKeyStroke(String)} syntax ({@code LEFT},
 *       {@code NUMPAD7}, {@code ENTER}, {@code alt G}, {@code shift F1}).
 *       Only pressed keystrokes: the release follows by itself.</li>
 *   <li>{@code click <x> <y>}: press and release the left button at a
 *       point of the 320x200 canvas.</li>
 *   <li>{@code waitGame [timeoutMs]}: until the in-game HUD is up.</li>
 *   <li>{@code waitIdle [timeoutMs]}: until our player has the controls
 *       (our turn, no dialog or scene, no slide running) for a moment.</li>
 *   <li>{@code waitTurn [timeoutMs]}: until a later turn than the current
 *       one has started and we have the controls.</li>
 *   <li>{@code pref <name> <on|off>}: set a classic pref
 *       ({@link ClassicPrefs#DEFAULTS}) or one of the original's rows that
 *       are FreeCol options ({@link ClassicPrefs#CLIENT_OPTIONS});
 *       {@code true}/{@code false} work too.</li>
 *   <li>{@code goto <x> <y>}: give the active unit FreeCol's goto order to
 *       map tile (x, y) ({@code InGameController.goToTile}): it moves
 *       there as far as it can now, and on at the start of later turns
 *       (build spec W5f).</li>
 *   <li>{@code log <text>}: a marker in the recorder's events.</li>
 *   <li>{@code quit}: flush the recorder and quit FreeCol.</li>
 * </ul>
 * Malformed input throws {@link IllegalArgumentException} naming the line;
 * nothing of a broken script runs.
 */
final class ClassicScript {

    /** The commands. */
    enum Op { WAIT, KEY, CLICK, WAIT_GAME, WAIT_IDLE, WAIT_TURN, PREF, LOG, QUIT,
        GOTO }

    /** Default timeouts (ms) of the three waits. */
    static final long WAIT_GAME_TIMEOUT = 180_000L;
    static final long WAIT_IDLE_TIMEOUT = 60_000L;
    static final long WAIT_TURN_TIMEOUT = 300_000L;

    /** One command. */
    static final class Command {

        /** The command. */
        final Op op;

        /** The 1-based source line. */
        final int line;

        /** The source text (comment stripped). */
        final String text;

        /** WAIT: ms; the waits: timeout ms; CLICK, GOTO: x. */
        final long number;

        /** CLICK, GOTO: y. */
        final int y;

        /** KEY: the key. */
        final KeyStroke key;

        /** PREF: the name; LOG: the text. */
        final String name;

        /** PREF: the value. */
        final boolean value;

        Command(Op op, int line, String text, long number, int y,
                KeyStroke key, String name, boolean value) {
            this.op = op;
            this.line = line;
            this.text = text;
            this.number = number;
            this.y = y;
            this.key = key;
            this.name = name;
            this.value = value;
        }

        @Override
        public String toString() {
            return this.line + ": " + this.text;
        }
    }

    /** The commands, in order. */
    final List<Command> commands;


    private ClassicScript(List<Command> commands) {
        this.commands = Collections.unmodifiableList(commands);
    }

    /**
     * Parse a script.
     *
     * @param lines The lines.
     * @return The script.
     * @exception IllegalArgumentException naming the first bad line.
     */
    static ClassicScript parse(List<String> lines) {
        final List<Command> out = new ArrayList<>();
        int n = 0;
        for (String raw : lines) {
            n++;
            String s = raw;
            final int hash = s.indexOf('#');
            if (hash >= 0) s = s.substring(0, hash);
            s = s.strip();
            if (s.isEmpty()) continue;
            try {
                out.add(command(n, s));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("line " + n + " '" + s
                    + "': " + e.getMessage(), e);
            }
        }
        return new ClassicScript(out);
    }

    /** One non-empty line. */
    private static Command command(int n, String s) {
        final String[] w = s.split("\\s+", 2);
        final String cmd = w[0].toLowerCase(Locale.ROOT);
        final String rest = (w.length > 1) ? w[1].strip() : "";
        switch (cmd) {
        case "wait":
            return new Command(Op.WAIT, n, s, millis(rest, -1L), 0, null, null, false);
        case "key":
            return new Command(Op.KEY, n, s, 0, 0, keyStroke(rest), null, false);
        case "click": {
            final String[] xy = rest.split("\\s+");
            if (xy.length != 2) throw new IllegalArgumentException("click needs x and y");
            final int x = integer(xy[0], ClassicFrameRecorder.W);
            final int y = integer(xy[1], ClassicFrameRecorder.H);
            return new Command(Op.CLICK, n, s, x, y, null, null, false);
        }
        case "waitgame":
            return new Command(Op.WAIT_GAME, n, s, millis(rest, WAIT_GAME_TIMEOUT),
                               0, null, null, false);
        case "waitidle":
            return new Command(Op.WAIT_IDLE, n, s, millis(rest, WAIT_IDLE_TIMEOUT),
                               0, null, null, false);
        case "waitturn":
            return new Command(Op.WAIT_TURN, n, s, millis(rest, WAIT_TURN_TIMEOUT),
                               0, null, null, false);
        case "pref": {
            final String[] nv = rest.split("\\s+");
            if (nv.length != 2) throw new IllegalArgumentException("pref needs a name and on/off");
            if (!ClassicPrefs.isKnown(nv[0])
                && !ClassicPrefs.CLIENT_OPTIONS.containsKey(nv[0])) {
                throw new IllegalArgumentException("unknown pref " + nv[0]);
            }
            return new Command(Op.PREF, n, s, 0, 0, null, nv[0], bool(nv[1]));
        }
        case "goto": {
            final String[] xy = rest.split("\\s+");
            if (xy.length != 2) throw new IllegalArgumentException("goto needs x and y");
            final int x = integer(xy[0], 10_000);
            final int y = integer(xy[1], 10_000);
            return new Command(Op.GOTO, n, s, x, y, null, null, false);
        }
        case "log":
            return new Command(Op.LOG, n, s, 0, 0, null, rest, false);
        case "quit":
            if (!rest.isEmpty()) throw new IllegalArgumentException("quit takes no argument");
            return new Command(Op.QUIT, n, s, 0, 0, null, null, false);
        default:
            throw new IllegalArgumentException("unknown command " + w[0]);
        }
    }

    /**
     * A number of milliseconds.
     *
     * @param s The text, possibly empty.
     * @param dflt The value for empty text, or negative if one is required.
     * @return The value.
     */
    private static long millis(String s, long dflt) {
        if (s.isEmpty()) {
            if (dflt < 0) throw new IllegalArgumentException("milliseconds expected");
            return dflt;
        }
        final long v;
        try {
            v = Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("not a number of milliseconds: " + s);
        }
        if (v < 0) throw new IllegalArgumentException("negative time " + v);
        return v;
    }

    /** An integer in [0, max). */
    private static int integer(String s, int max) {
        final int v;
        try {
            v = Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("not a number: " + s);
        }
        if (v < 0 || v >= max) throw new IllegalArgumentException(v + " outside 0.." + (max - 1));
        return v;
    }

    /** on/off/true/false. */
    private static boolean bool(String s) {
        switch (s.toLowerCase(Locale.ROOT)) {
        case "on": case "true": return true;
        case "off": case "false": return false;
        default: throw new IllegalArgumentException("on or off expected: " + s);
        }
    }

    /**
     * A pressed keystroke.
     *
     * @param s {@link KeyStroke#getKeyStroke(String)} syntax.
     * @return The keystroke.
     */
    static KeyStroke keyStroke(String s) {
        if (s.isEmpty()) throw new IllegalArgumentException("key expected");
        final KeyStroke k = KeyStroke.getKeyStroke(s);
        if (k != null && k.getKeyEventType() != KeyEvent.KEY_PRESSED) {
            throw new IllegalArgumentException("pressed keys only: " + s);
        }
        if (k == null || k.getKeyCode() == KeyEvent.VK_UNDEFINED) {
            throw new IllegalArgumentException("not a key: " + s
                + " (e.g. LEFT, NUMPAD7, ENTER, alt G)");
        }
        return k;
    }

    /**
     * The character a key types, as a real key press would deliver it
     * (letters lower case unless shifted), or {@code CHAR_UNDEFINED}.
     *
     * @param k The keystroke.
     * @return The character.
     */
    static char keyChar(KeyStroke k) {
        final int c = k.getKeyCode();
        final int m = k.getModifiers();
        if ((m & (KeyEvent.CTRL_DOWN_MASK | KeyEvent.ALT_DOWN_MASK
                  | KeyEvent.META_DOWN_MASK | KeyEvent.ALT_GRAPH_DOWN_MASK)) != 0) {
            return KeyEvent.CHAR_UNDEFINED;
        }
        final boolean shift = (m & KeyEvent.SHIFT_DOWN_MASK) != 0;
        if (c >= KeyEvent.VK_A && c <= KeyEvent.VK_Z) {
            final char ch = (char)('a' + (c - KeyEvent.VK_A));
            return shift ? Character.toUpperCase(ch) : ch;
        }
        if (c >= KeyEvent.VK_0 && c <= KeyEvent.VK_9 && !shift) {
            return (char)('0' + (c - KeyEvent.VK_0));
        }
        if (c >= KeyEvent.VK_NUMPAD0 && c <= KeyEvent.VK_NUMPAD9) {
            return (char)('0' + (c - KeyEvent.VK_NUMPAD0));
        }
        switch (c) {
        case KeyEvent.VK_ENTER: return '\n';
        case KeyEvent.VK_SPACE: return ' ';
        case KeyEvent.VK_ESCAPE: return (char)27;
        case KeyEvent.VK_BACK_SPACE: return '\b';
        case KeyEvent.VK_TAB: return '\t';
        default: return KeyEvent.CHAR_UNDEFINED;
        }
    }

    /**
     * Where a real key sits: the numpad keys report the numpad location.
     *
     * @param k The keystroke.
     * @return A {@code KeyEvent.KEY_LOCATION_*}.
     */
    static int keyLocation(KeyStroke k) {
        final int c = k.getKeyCode();
        return ((c >= KeyEvent.VK_NUMPAD0 && c <= KeyEvent.VK_NUMPAD9)
                || c == KeyEvent.VK_MULTIPLY || c == KeyEvent.VK_ADD
                || c == KeyEvent.VK_SUBTRACT || c == KeyEvent.VK_DECIMAL
                || c == KeyEvent.VK_DIVIDE)
            ? KeyEvent.KEY_LOCATION_NUMPAD : KeyEvent.KEY_LOCATION_STANDARD;
    }
}
