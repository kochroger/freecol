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

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import net.sf.freecol.client.gui.classic.ClassicEarlyMusic.Handover;
import net.sf.freecol.client.gui.classic.ClassicEarlyMusic.Handover.State;


/**
 * Headless tests of the early-music hand-over ({@link ClassicEarlyMusic}):
 * every ordering of preparation, first picture ({@code go}) and the sound
 * controller's {@code take}, with a fake player -- no audio device.
 */
public class ClassicEarlyMusicTest extends TestCase {

    /** Counts builds and plays. */
    private static final class Fake implements Handover.Backend<String> {
        final List<String> calls = new ArrayList<>();
        int built = 0;

        @Override
        public String build() {
            this.built++;
            this.calls.add("build");
            return "player" + this.built;
        }

        @Override
        public void play(String p) {
            this.calls.add("play " + p);
        }
    }

    public void testGoBeforeReady() {
        final Handover<String> h = new Handover<>();
        final Fake b = new Fake();
        assertTrue(h.begin());
        assertFalse(h.go());                    // not ready yet: remembered
        assertTrue(h.prepared(b));              // ...and started when ready
        assertEquals(State.PLAYING, h.state());
        assertEquals(2, b.calls.size());
        assertEquals("play player1", b.calls.get(1));
        final Handover.Taken<String> t = h.take();
        assertEquals("player1", t.player);
        assertTrue(t.playing);
        assertEquals(State.TAKEN, h.state());
    }

    public void testReadyThenGo() {
        final Handover<String> h = new Handover<>();
        final Fake b = new Fake();
        h.begin();
        h.prepared(b);
        assertEquals(State.READY, h.state());
        assertEquals(1, b.calls.size());        // built, not playing
        assertTrue(h.go());
        assertEquals(State.PLAYING, h.state());
        assertFalse(h.go());                    // once
        assertEquals(2, b.calls.size());
    }

    public void testTakeBeforeReadyCancels() {
        // The client constructor was faster: the preparation is cancelled
        // and its late result builds NO player (the SoundPlayer thread
        // never ends, so an orphan would play on its own).
        final Handover<String> h = new Handover<>();
        final Fake b = new Fake();
        h.begin();
        h.go();
        assertNull(h.take());
        assertEquals(State.CANCELLED, h.state());
        assertFalse(h.prepared(b));
        assertEquals(0, b.built);
        assertNull(h.take());
    }

    public void testTakeWhileReadyNotPlaying() {
        // The window failed after prepare (no go): the controller still
        // adopts the silent player; the title screen starts the piece.
        final Handover<String> h = new Handover<>();
        final Fake b = new Fake();
        h.begin();
        h.prepared(b);
        final Handover.Taken<String> t = h.take();
        assertNotNull(t);
        assertFalse(t.playing);
        assertFalse(h.go());                    // too late now
        assertEquals(1, b.calls.size());
    }

    public void testDoubleTake() {
        final Handover<String> h = new Handover<>();
        h.begin();
        h.prepared(new Fake());
        h.go();
        assertNotNull(h.take());
        assertNull(h.take());
    }

    public void testGoWithoutPrepare() {
        final Handover<String> h = new Handover<>();
        assertFalse(h.go());
        assertEquals(State.IDLE, h.state());
        assertNull(h.take());
        // After the controller looked, a late prepare does nothing.
        assertFalse(h.begin());
        final Fake b = new Fake();
        assertFalse(h.prepared(b));
        assertEquals(0, b.built);
    }

    public void testBeginOnce() {
        final Handover<String> h = new Handover<>();
        assertTrue(h.begin());
        assertFalse(h.begin());
    }

    public void testFailureBuildsNothing() {
        // Validation refused (no audio line, undecodable title, volume 0).
        final Handover<String> h = new Handover<>();
        h.begin();
        h.go();
        h.failed();
        assertEquals(State.CANCELLED, h.state());
        final Fake b = new Fake();
        assertFalse(h.prepared(b));
        assertEquals(0, b.built);
        assertNull(h.take());
    }

    public void testNullPlayerIsAFailure() {
        final Handover<String> h = new Handover<>();
        h.begin();
        assertFalse(h.prepared(new Handover.Backend<String>() {
                @Override public String build() { return null; }
                @Override public void play(String p) { fail("played"); }
            }));
        assertEquals(State.CANCELLED, h.state());
        assertNull(h.take());
    }

    public void testVolume() {
        assertEquals(100, ClassicEarlyMusic.parseVolume(null));
        assertEquals(100, ClassicEarlyMusic.parseVolume("loud"));
        assertEquals(40, ClassicEarlyMusic.parseVolume(" 40 "));
        assertEquals(0, ClassicEarlyMusic.parseVolume("-3"));
        assertEquals(100, ClassicEarlyMusic.parseVolume("250"));
        assertNotNull(ClassicEarlyMusic.checkVolume(0));     // muted: no early start
        assertNull(ClassicEarlyMusic.checkVolume(1));
        assertNull(ClassicEarlyMusic.checkVolume(100));
    }
}
