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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.freecol.client.ClientOptions;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.control.SoundController;
import net.sf.freecol.common.option.AudioMixerOption;
import net.sf.freecol.common.option.PercentageOption;
import net.sf.freecol.common.resources.AudioResource;
import net.sf.freecol.common.resources.ResourceManager;
import net.sf.freecol.common.sound.SoundPlayer;


/**
 * The Classic UI's sound controller: music is the <b>original 1994
 * soundtrack</b> and nothing else, sound effects stay FreeCol's.
 *
 * <p>The owner wants the game to feel like the original, so: the title piece
 * plays (looping) on the title screen; when a game starts -- new or loaded --
 * it is <em>not</em> cut off but plays to its natural end, after which the
 * remaining original tracks cycle (shuffled) for the rest of the session;
 * going back to the title switches back to the title piece.  No FreeCol music
 * file (intro.ogg, the nation intros, anthems, first-contact and
 * fountain-of-youth jingles, the default playlist) may ever play.
 *
 * <p><b>Why a {@code SoundController} subclass and not a {@code ClassicGUI}
 * override of {@code GUI.playSound}:</b> the GUI is not a complete choke
 * point.
 * <ul>
 *   <li>{@code PreGameController.startGameInternal} (PreGameController.java:294-300)
 *       and {@code MapEditorController} (:179-183) call
 *       {@link SoundController#setDefaultPlaylist} directly, bypassing the
 *       GUI, to start FreeCol's own playlist.</li>
 *   <li>{@code GUI.playSound} routes a key to music only when its resource
 *       has {@code .type=music}; the 19thCenturyNations mod's nation intros
 *       have no type line and would reach the <em>effects</em> player, where a
 *       GUI-level filter keyed on the type would miss them.</li>
 * </ul>
 * The two public entry points every music path ends in -- {@link #playMusic}
 * and {@link #setDefaultPlaylist} -- are overridden here and never reach
 * {@code super}, so FreeCol music is unreachable by construction; and
 * {@link #playSound} diverts music-family keys by prefix, whatever their type.
 * {@code FreeColClient} instantiates this class under {@code --classic}.
 *
 * <p><b>How FreeCol's calls are reinterpreted:</b>
 * <ul>
 *   <li>{@code sound.intro.<nation>} -- played by {@code startGameInternal}
 *       for a new game, a load from the title and an in-game load -- means
 *       <em>a game has started</em>: switch to the in-game playlist without
 *       stopping the current piece (see {@link Jukebox#game}).</li>
 *   <li>{@code sound.intro.general} (FreeColClient's start-up calls,
 *       {@code showMainTitle}) is ignored: the title piece is bound to the
 *       title screen itself, {@link ClassicGUI#showMainPanel} calls
 *       {@link #playTitleMusic}.  That is what makes {@code --fast} (which
 *       plays the general intro but never shows the title) start the game
 *       straight on the in-game playlist.</li>
 *   <li>Everything else of the music family, {@code null} for "stop" and
 *       FreeCol's default playlist are ignored: the soundtrack runs
 *       continuously.</li>
 * </ul>
 *
 * <p><b>Where the music comes from:</b> the git-ignored pack
 * {@code data/mods/classic_music/} (id {@value #PACK_ID}), written by
 * {@code tools/classic_assets/convert-soundtrack.ps1} from the owner's own
 * Steam copy and overlaid by {@code FreeColClient.withClassicPacks} like the
 * art pack.  It is a pack of its own because {@code ant classic-assets}
 * deletes and regenerates {@code classic_original} from scratch
 * (ClassicAssetConverter.java:94).  Its {@code resources.properties} holds
 * the one line that chooses the title piece ({@value #TITLE_KEY}) and the
 * directory of all tracks ({@value #TRACKS_KEY}).  The MP3s are converted to
 * 16-bit PCM WAV because neither the JDK nor {@code jars/} can decode MP3.
 * Without the pack (or with a broken track: one undecodable file drops the
 * whole directory resource, AudioResource.java:62-66) music is silent -- never
 * FreeCol's.
 *
 * <p><b>Music from the first second:</b> on a normal launch the title piece
 * is already playing when this controller is built -- {@link ClassicEarlyMusic}
 * started it with the first picture of the early window, on a player of its
 * own.  The constructor adopts that very player (through the protected
 * {@code SoundController} constructor), forwards the real volume and mixer
 * options into it and records TITLE without touching it
 * ({@link Jukebox#adoptTitle}), so the piece never restarts at client
 * attach, at the title screen or at game start.  Without an early player
 * everything is as before: the title screen starts the piece.
 */
public final class ClassicSoundController extends SoundController {

    private static final Logger logger
        = Logger.getLogger(ClassicSoundController.class.getName());

    /** The mod id of the git-ignored soundtrack pack. */
    public static final String PACK_ID = "classic_music";

    /**
     * The title-piece resource: THE one line the owner edits (in the pack's
     * {@code resources.properties}) to pick the title track by ear.
     */
    public static final String TITLE_KEY = "sound.classic.music.title";

    /** The directory resource holding all original tracks. */
    public static final String TRACKS_KEY = "sound.classic.music.tracks";

    /** The FreeCol key of the long general intro (title music). */
    private static final String GENERAL_INTRO_KEY = "sound.intro.general";

    /**
     * Key prefixes of FreeCol's music family (data/default/resources.properties:
     * intros :6-21, anthems :22-37, first contact :43-46) plus FreeCol's
     * playlist keys.
     */
    private static final String[] MUSIC_PREFIXES = {
        "sound.intro.", "sound.anthem.", "sound.event.meet.", "sound.music.",
        "sound.classic.music."
    };

    /** The fountain-of-youth jingle (typed music, resources.properties:51-52). */
    private static final String FOUNTAIN_KEY = "sound.event.fountainOfYouth";

    /** What the music is doing. */
    enum Mode {
        /** Nothing selected yet (start-up, or a game started straight away). */
        SILENT,
        /** The title piece, looping. */
        TITLE,
        /** The in-game playlist (possibly after the title piece's end). */
        GAME
    }

    /**
     * Where the jukebox sends its decisions.  The production implementation
     * drives the music {@link SoundPlayer}; tests record the calls.
     */
    interface MusicOutput {

        /**
         * Replace the list the player cycles (shuffled) whenever its queue
         * runs empty -- that is, after the current piece ends.
         *
         * @param files The new list, possibly empty.
         */
        void setDefaultPlaylist(List<File> files);

        /** Cut the current piece; the player then refills from the list. */
        void stop();
    }

    /**
     * The resolved soundtrack: the title piece and the in-game tracks.
     * Immutable.
     */
    static final class Soundtrack {

        /** The title piece, or null when there is no soundtrack. */
        final File title;

        /** The in-game tracks in track order; empty when there is none. */
        final List<File> gameTracks;

        private Soundtrack(File title, List<File> gameTracks) {
            this.title = title;
            this.gameTracks = Collections.unmodifiableList(gameTracks);
        }

        /**
         * Select title and in-game tracks.
         *
         * <ul>
         *   <li>The tracks are sorted by file name; the converter's zero-padded
         *       {@code trackNN.wav} names make that the album order
         *       ({@code AudioResource.getAllAudio} is in unspecified
         *       {@code File.listFiles} order).</li>
         *   <li>No configured (or a missing, hence unresolved) title falls back
         *       to the first track, so a bad title line is never silent.</li>
         *   <li>In game, every track except the title piece -- the title should
         *       stay special -- unless that leaves nothing, then the title
         *       piece itself.</li>
         * </ul>
         *
         * @param configuredTitle The file of the title key, or null.
         * @param all The files of the tracks key, possibly empty.
         * @return The selection.
         */
        static Soundtrack of(File configuredTitle, List<File> all) {
            final List<File> sorted = new ArrayList<>(all);
            sorted.sort(Comparator.comparing(File::getName)
                .thenComparing(File::getPath));
            final File title = (configuredTitle != null) ? configuredTitle
                : (sorted.isEmpty()) ? null : sorted.get(0);
            final List<File> game = new ArrayList<>();
            final String titleId = (title == null) ? null : identity(title);
            for (File f : sorted) {
                if (titleId == null || !titleId.equals(identity(f))) {
                    game.add(f);
                }
            }
            if (game.isEmpty() && title != null) game.add(title);
            return new Soundtrack(title, game);
        }

        /**
         * A path that compares equal for the same file however it was
         * spelled ("resources/music/track01.wav" vs. the directory listing).
         *
         * @param f The file.
         * @return Its canonical path, or the absolute path if that fails.
         */
        private static String identity(File f) {
            try {
                return f.getCanonicalPath();
            } catch (IOException e) {
                return f.getAbsolutePath();
            }
        }

        /**
         * Is there anything to play at all?
         *
         * @return True if a title piece exists (there are then also in-game
         *     tracks).
         */
        boolean isEmpty() {
            return this.title == null;
        }

        @Override
        public String toString() {
            return (this.title == null) ? "no soundtrack"
                : "title " + this.title.getName() + ", "
                    + this.gameTracks.size() + " in-game tracks";
        }
    }

    /**
     * The music state machine.  Kept free of {@code SoundPlayer} and
     * {@code ResourceManager} so it can be unit-tested; synchronized because
     * it is called from the EDT and from the client's start-game thread.
     *
     * <p>It works entirely through the player's <em>default playlist</em>,
     * which {@code SoundPlayer.run} (SoundPlayer.java:204-216) plays, shuffled
     * and looping, whenever its queue is empty: never queuing a piece with
     * {@code playOnce} means there is never a stale queue entry to race with.
     */
    static final class Jukebox {

        private final MusicOutput out;

        private Mode mode = Mode.SILENT;

        /**
         * Create a jukebox.
         *
         * @param out Where to send the decisions.
         */
        Jukebox(MusicOutput out) {
            this.out = out;
        }

        /**
         * The current mode.
         *
         * @return The mode.
         */
        synchronized Mode getMode() {
            return this.mode;
        }

        /**
         * The title screen is showing: loop the title piece.
         *
         * <p>Idempotent -- the title screen is (re)shown many times without
         * leaving it (start-up, a failed load, the in-game "Neues Spiel"),
         * and must not restart the piece.  Otherwise the list becomes the
         * title piece alone <em>before</em> the stop, so the player thread,
         * refilling right after the stop, starts the title piece at once.
         *
         * @param s The soundtrack.
         * @return True if the music changed.
         */
        synchronized boolean title(Soundtrack s) {
            if (this.mode == Mode.TITLE) return false;
            this.mode = Mode.TITLE;
            final List<File> list = new ArrayList<>();
            if (s.title != null) list.add(s.title);
            this.out.setDefaultPlaylist(list);
            this.out.stop();
            return true;
        }

        /**
         * A game has started: hand over to the in-game playlist.
         *
         * <p>Deliberately <em>no</em> stop: the piece that is playing (the
         * title piece, when coming from the title) is the dequeued current
         * file, so it plays to its natural end and only then does the player
         * refill from the in-game list -- the owner's "the title music must
         * not be cut off at game start".  With nothing playing (a game
         * started straight away), the first in-game track starts within the
         * player's 100 ms poll.  Idempotent too: an in-game load keeps the
         * current track.
         *
         * @param s The soundtrack.
         * @return True if the music changed.
         */
        synchronized boolean game(Soundtrack s) {
            if (this.mode == Mode.GAME) return false;
            this.mode = Mode.GAME;
            this.out.setDefaultPlaylist(new ArrayList<>(s.gameTracks));
            return true;
        }

        /**
         * The title piece is ALREADY playing (started with the first
         * picture by {@link ClassicEarlyMusic}, on the very player this
         * controller adopted): just record it.  No output call at all --
         * a stop would restart the piece -- and the later {@link #title}
         * of the title screen is then the usual no-op.
         *
         * @return True if the mode changed (SILENT before).
         */
        synchronized boolean adoptTitle() {
            if (this.mode != Mode.SILENT) return false;
            this.mode = Mode.TITLE;
            return true;
        }
    }


    /** The music state machine. */
    private final Jukebox jukebox;


    /**
     * Create the classic sound controller.
     *
     * @param freeColClient The {@code FreeColClient} for the game.
     * @param sound Enable sound if true.
     */
    public ClassicSoundController(FreeColClient freeColClient, boolean sound) {
        this(freeColClient, sound, ClassicEarlyMusic.take());
    }

    /**
     * Create the controller, adopting the music player that
     * {@link ClassicEarlyMusic} started with the first picture, if any.
     *
     * @param freeColClient The {@code FreeColClient} for the game.
     * @param sound Enable sound if true.
     * @param early The early player, or null.
     */
    private ClassicSoundController(FreeColClient freeColClient, boolean sound,
                                   ClassicEarlyMusic.Early early) {
        super(freeColClient, sound, (early == null) ? null : early.player);
        this.jukebox = new Jukebox(new MusicOutput() {
                @Override
                public void setDefaultPlaylist(List<File> files) {
                    // Guarded like SoundController.play: with no usable
                    // mixer the player would retry (and warn) forever.
                    final SoundPlayer mp = getMusicPlayer();
                    if (mp == null || !canPlaySound()) return;
                    mp.setDefaultPlaylist(files.toArray(new File[0]));
                }

                @Override
                public void stop() {
                    final SoundPlayer mp = getMusicPlayer();
                    if (mp == null || !canPlaySound()) return;
                    mp.stop();
                }
            });
        if (early != null) adoptEarly(freeColClient, early);
    }

    /**
     * Take over the early title piece (see {@link ClassicEarlyMusic}).
     * <ul>
     *   <li>Not usable -- {@code --no-sound}, an unreadable mixer option
     *       (the base class then built no players and ignored this one), or
     *       no valid mixer: the early player is silenced for good (its
     *       thread never ends, so it must not keep playing).</li>
     *   <li>Otherwise the real music volume and mixer options are forwarded
     *       into the stand-ins the player listens to -- the volume applies to
     *       the line already playing -- and kept forwarded.</li>
     *   <li>If the piece already plays and is the title the resources
     *       resolve to (or they resolve to nothing yet), the jukebox records
     *       TITLE without touching the player ({@link Jukebox#adoptTitle}),
     *       so {@code playTitleMusic} at the title screen is a no-op and
     *       the piece never restarts.  A different resolved title switches
     *       once.</li>
     * </ul>
     */
    private void adoptEarly(FreeColClient fcc, ClassicEarlyMusic.Early early) {
        if (getMusicPlayer() != early.player || !canPlaySound()) {
            early.player.setDefaultPlaylist();
            early.player.stop();
            logger.info("Classic music: early title piece silenced (sound off"
                + " or no usable mixer)");
            return;
        }
        try {
            final ClientOptions opts = fcc.getClientOptions();
            final PercentageOption realVolume
                = opts.getOption(ClientOptions.MUSIC_VOLUME, PercentageOption.class);
            final AudioMixerOption realMixer
                = opts.getOption(ClientOptions.AUDIO_MIXER, AudioMixerOption.class);
            if (realVolume != null) {
                early.volume.setValue(realVolume.getValue());
                realVolume.addPropertyChangeListener(e ->
                    early.volume.setValue(realVolume.getValue()));
            }
            if (realMixer != null) {
                early.mixer.setValue(realMixer.getValue());
                realMixer.addPropertyChangeListener(e ->
                    early.mixer.setValue(realMixer.getValue()));
            }
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Classic music: options not forwarded", e);
        }
        if (!early.playing) return;     // the title screen starts it as usual
        final Soundtrack s = resolve();
        if (s.isEmpty() || sameFile(s.title, early.title)) {
            this.jukebox.adoptTitle();
            logger.info("Classic music: title piece adopted from the start-up ("
                + early.title.getName() + ")");
        } else {
            this.jukebox.title(s);
            logger.info("Classic music: start-up played " + early.title.getName()
                + ", resources say " + s.title.getName() + " -- switched");
        }
    }

    /** Whether two files are the same file (canonical paths). */
    static boolean sameFile(File a, File b) {
        if (a == null || b == null) return false;
        try {
            return a.getCanonicalPath().equals(b.getCanonicalPath());
        } catch (IOException e) {
            return a.getAbsolutePath().equals(b.getAbsolutePath());
        }
    }

    /**
     * The jukebox (tests).
     *
     * @return The music state machine.
     */
    Jukebox jukebox() {
        return this.jukebox;
    }

    /**
     * Is this key one of FreeCol's music family?  Decided by key, not by its
     * {@code .type} resource, so untyped mod intros are caught too.
     *
     * @param key The sound resource key (may be null).
     * @return True for music keys.
     */
    static boolean isMusicKey(String key) {
        if (key == null) return false;
        if (FOUNTAIN_KEY.equals(key)) return true;
        for (String p : MUSIC_PREFIXES) {
            if (key.startsWith(p)) return true;
        }
        return false;
    }

    /**
     * Does this music key mean "a game has started"?  Only the nation intro
     * that {@code PreGameController.startGameInternal} plays
     * (PreGameController.java:295) does.
     *
     * @param key The sound resource key (may be null).
     * @return True for {@code sound.intro.<nation>}.
     */
    static boolean isGameStartKey(String key) {
        return key != null && key.startsWith("sound.intro.")
            && !GENERAL_INTRO_KEY.equals(key);
    }

    /**
     * Resolve the soundtrack from the loaded resources.  Done on every
     * switch (cheap: the files were opened once when the pack was mapped),
     * so it always matches the current {@code ResourceManager} state.
     *
     * @return The soundtrack; empty when the pack is absent or broken.
     */
    static Soundtrack resolve() {
        final AudioResource tracks
            = ResourceManager.getAudioResource(TRACKS_KEY, false);
        final AudioResource title
            = ResourceManager.getAudioResource(TITLE_KEY, false);
        final List<File> all = (tracks == null) ? Collections.<File>emptyList()
            : tracks.getAllAudio();
        File t = null;
        if (title != null && !title.getAllAudio().isEmpty()) {
            final List<File> tl = new ArrayList<>(title.getAllAudio());
            tl.sort(Comparator.comparing(File::getName));
            t = tl.get(0);
        }
        return Soundtrack.of(t, all);
    }

    /**
     * The title screen is showing ({@link ClassicGUI#showMainPanel}): play
     * the title piece, looping.  No-op while it already plays.
     */
    public void playTitleMusic() {
        final Soundtrack s = resolve();
        if (this.jukebox.title(s)) {
            logger.info("Classic music: title screen -- "
                + ((s.isEmpty()) ? "silent (no '" + PACK_ID + "' soundtrack)"
                    : s.title.getName() + " (looping)"));
        }
    }

    /**
     * A game has started: the in-game playlist takes over once the current
     * piece has ended.
     */
    private void playGameMusic() {
        final Soundtrack s = resolve();
        if (this.jukebox.game(s)) {
            logger.info("Classic music: game started -- "
                + ((s.isEmpty()) ? "silent (no '" + PACK_ID + "' soundtrack)"
                    : "current piece plays on, then "
                        + s.gameTracks.size() + " in-game tracks (shuffled)"));
        }
    }

    /**
     * Get the current music mode (diagnostics and tests).
     *
     * @return The mode.
     */
    Mode getMode() {
        return this.jukebox.getMode();
    }


    // Override SoundController

    /**
     * {@inheritDoc}
     *
     * Music-family keys are diverted to {@link #playMusic} whatever their
     * {@code .type}; everything else (including {@code null} = stop the
     * effect) is a FreeCol sound effect and unchanged.
     */
    @Override
    public void playSound(String sound) {
        if (isMusicKey(sound)) {
            playMusic(sound);
        } else {
            super.playSound(sound);
        }
    }

    /**
     * {@inheritDoc}
     *
     * Never plays the key: a nation intro means "game started", everything
     * else (including {@code null} and the general intro) is ignored.
     */
    @Override
    public void playMusic(String sound) {
        if (isGameStartKey(sound)) {
            playGameMusic();
        } else {
            logger.finest("Classic music: ignored FreeCol music " + sound);
        }
    }

    /**
     * {@inheritDoc}
     *
     * Ignored: FreeCol's default playlist (PreGameController.java:297-300,
     * MapEditorController.java:179-181) never plays in the Classic UI.
     */
    @Override
    public void setDefaultPlaylist(List<File> files) {
        logger.finest("Classic music: ignored FreeCol default playlist");
    }
}
