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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import junit.framework.TestCase;

import net.sf.freecol.client.gui.classic.ClassicSoundController.Jukebox;
import net.sf.freecol.client.gui.classic.ClassicSoundController.Mode;
import net.sf.freecol.client.gui.classic.ClassicSoundController.MusicOutput;
import net.sf.freecol.client.gui.classic.ClassicSoundController.Soundtrack;


/**
 * Headless tests of the Classic UI's music logic ({@link ClassicSoundController}):
 * which keys count as FreeCol music, how the title piece and in-game tracks
 * are selected, and the title/game state machine against a recording player.
 * No audio device, resource manager or soundtrack pack is needed (the files
 * need not exist).
 */
public class ClassicSoundControllerTest extends TestCase {

    /** A player stand-in that records the jukebox's calls. */
    private static final class Recorder implements MusicOutput {
        final List<String> calls = new ArrayList<>();

        @Override
        public void setDefaultPlaylist(List<File> files) {
            final List<String> names = new ArrayList<>();
            for (File f : files) names.add(f.getName());
            calls.add("list" + names);
        }

        @Override
        public void stop() {
            calls.add("stop");
        }
    }

    private static final File DIR = new File("classic-music-test", "music");

    private static File track(int n) {
        return new File(DIR, String.format("track%02d.wav", n));
    }

    private static List<String> names(List<File> files) {
        final List<String> ret = new ArrayList<>();
        for (File f : files) ret.add(f.getName());
        return ret;
    }

    public void testMusicKeys() {
        // FreeCol's music family: never played in the classic UI.
        assertTrue(ClassicSoundController.isMusicKey("sound.intro.general"));
        assertTrue(ClassicSoundController.isMusicKey("sound.intro.model.nation.dutch"));
        assertTrue(ClassicSoundController.isMusicKey("sound.anthem.model.nation.dutch"));
        assertTrue(ClassicSoundController.isMusicKey("sound.event.meet.model.nation.aztec"));
        assertTrue(ClassicSoundController.isMusicKey("sound.event.fountainOfYouth"));
        assertTrue(ClassicSoundController.isMusicKey("sound.music.playlist.default"));
        assertTrue(ClassicSoundController.isMusicKey(ClassicSoundController.TITLE_KEY));
        // Effects stay effects; null (= stop the effect) too.
        assertFalse(ClassicSoundController.isMusicKey(null));
        assertFalse(ClassicSoundController.isMusicKey("sound.event.illegalMove"));
        assertFalse(ClassicSoundController.isMusicKey("sound.attack.artillery"));
        assertFalse(ClassicSoundController.isMusicKey("sound.event.shipSunk"));

        // Only a nation intro (PreGameController.startGameInternal) means
        // "a game has started"; the general intro does not.
        assertTrue(ClassicSoundController.isGameStartKey("sound.intro.model.nation.english"));
        assertTrue(ClassicSoundController.isGameStartKey("sound.intro.model.nation.frenchREF"));
        assertFalse(ClassicSoundController.isGameStartKey("sound.intro.general"));
        assertFalse(ClassicSoundController.isGameStartKey(null));
        assertFalse(ClassicSoundController.isGameStartKey("sound.anthem.model.nation.dutch"));
    }

    public void testSoundtrackSelection() {
        final List<File> all = Arrays.asList(track(3), track(1), track(10), track(2));

        // No title line: the first track (by name = album order) is the title.
        Soundtrack s = Soundtrack.of(null, all);
        assertEquals("track01.wav", s.title.getName());
        assertEquals(Arrays.asList("track02.wav", "track03.wav", "track10.wav"),
                     names(s.gameTracks));
        assertFalse(s.isEmpty());

        // Configured title, spelled differently from the directory listing
        // (the properties path vs. listFiles): still excluded from the game.
        final File spelled = new File(new File(DIR, "sub"), "../track03.wav");
        s = Soundtrack.of(spelled, all);
        assertSame(spelled, s.title);
        assertEquals(Arrays.asList("track01.wav", "track02.wav", "track10.wav"),
                     names(s.gameTracks));

        // A title outside the track directory: every track plays in game.
        s = Soundtrack.of(new File("elsewhere.wav"), all);
        assertEquals(4, s.gameTracks.size());

        // A single track: it is title and in-game music both.
        s = Soundtrack.of(null, Collections.singletonList(track(1)));
        assertEquals("track01.wav", s.title.getName());
        assertEquals(Arrays.asList("track01.wav"), names(s.gameTracks));

        // No pack: nothing at all (silence, never FreeCol music).
        s = Soundtrack.of(null, Collections.<File>emptyList());
        assertTrue(s.isEmpty());
        assertNull(s.title);
        assertTrue(s.gameTracks.isEmpty());
    }

    public void testTitleThenGameThenTitle() {
        final Soundtrack s = Soundtrack.of(null,
            Arrays.asList(track(1), track(2), track(3)));
        final Recorder r = new Recorder();
        final Jukebox j = new Jukebox(r);
        assertEquals(Mode.SILENT, j.getMode());

        // Title screen: list = the title piece alone, then stop -> it
        // starts at once and loops.
        assertTrue(j.title(s));
        assertEquals(Arrays.asList("list[track01.wav]", "stop"), r.calls);
        assertEquals(Mode.TITLE, j.getMode());

        // Re-showing the title (failed load, in-game "Neues Spiel" while
        // already there) must not restart the piece.
        r.calls.clear();
        assertFalse(j.title(s));
        assertTrue(r.calls.isEmpty());

        // Game start: hand over WITHOUT stop -- the title piece plays on to
        // its end, then the in-game list (without the title) cycles.
        assertTrue(j.game(s));
        assertEquals(Arrays.asList("list[track02.wav, track03.wav]"), r.calls);
        assertEquals(Mode.GAME, j.getMode());

        // An in-game load keeps the current track.
        r.calls.clear();
        assertFalse(j.game(s));
        assertTrue(r.calls.isEmpty());

        // Back to the title: cut the in-game track, title piece again.
        assertTrue(j.title(s));
        assertEquals(Arrays.asList("list[track01.wav]", "stop"), r.calls);
    }

    public void testGameStraightAway() {
        // --fast / a savegame on the command line: no title screen was
        // shown, so the in-game list starts directly; nothing is stopped.
        final Soundtrack s = Soundtrack.of(null,
            Arrays.asList(track(1), track(2)));
        final Recorder r = new Recorder();
        final Jukebox j = new Jukebox(r);
        assertTrue(j.game(s));
        assertEquals(Arrays.asList("list[track02.wav]"), r.calls);
    }

    public void testNoSoundtrackIsSilent() {
        final Soundtrack s = Soundtrack.of(null, Collections.<File>emptyList());
        final Recorder r = new Recorder();
        final Jukebox j = new Jukebox(r);
        assertTrue(j.title(s));
        assertEquals(Arrays.asList("list[]", "stop"), r.calls);
        r.calls.clear();
        assertTrue(j.game(s));
        assertEquals(Arrays.asList("list[]"), r.calls);
    }
}
