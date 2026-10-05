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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;

import net.sf.freecol.client.ClientOptions;
import net.sf.freecol.common.io.FreeColDirectories;
import net.sf.freecol.common.io.FreeColXMLReader;
import net.sf.freecol.common.option.AudioMixerOption;
import net.sf.freecol.common.option.PercentageOption;
import net.sf.freecol.common.sound.SoundPlayer;


/**
 * <b>Music from the first second:</b> starts the original title piece with
 * the very first picture of the early window (the intro, or the title),
 * seconds before any {@code FreeColClient} exists, and hands that same
 * running player to {@link ClassicSoundController} later, so the piece
 * never restarts and never plays twice.
 *
 * <p><b>Why.</b>  The owner wants the music "from the first second, together
 * with the Vorspann".  Until now the title piece started only when
 * {@code ClassicGUI.showMainPanel} called {@code playTitleMusic}, after the
 * client had attached, ~4.4 s after launch.  The sound controller cannot be
 * built earlier (it belongs to the client), so this class builds a
 * {@link SoundPlayer} of its own with stand-in options, and
 * {@code ClassicSoundController} adopts it through
 * {@code SoundController(FreeColClient, boolean, SoundPlayer)}.
 *
 * <p>{@link #prepare} (called by {@code FreeCol.createClassicSplashScreen}
 * before the window is built) starts a daemon thread that, in ~0.25 s
 * overlapping the window's own ~0.45 s:
 * <ol>
 *   <li>finds the soundtrack pack {@code classic_music} like the art pack
 *       ({@link ClassicPackFiles#modDirectory}) and picks the title piece by
 *       the same rule as the sound controller
 *       ({@link ClassicSoundController.Soundtrack#of}: the configured
 *       {@code sound.classic.music.title} file, else the first track);</li>
 *   <li>peeks the music volume and the mixer from the user's options file,
 *       the way {@code ClientOptions.getSpecialOptions} does before the
 *       client exists (failures mean the defaults: 100 %, automatic mixer;
 *       volume 0 means no early start);</li>
 *   <li>validates silently -- the file's audio header, the mixer's line
 *       support, a test open and close of a line -- so a broken set-up is
 *       refused BEFORE a player exists;</li>
 *   <li>builds the player (under the hand-over lock, and only if nobody
 *       has cancelled meanwhile) and, if {@link #go} was already called,
 *       starts the title piece.</li>
 * </ol>
 * {@link #go} is called right after the first picture is painted.
 *
 * <p><b>Two SoundPlayer traps</b> shape this: its thread is non-daemon and
 * never ends, so a player must never be built and then forgotten (the
 * hand-over builds it only under the lock, and the sound controller
 * silences one it cannot use); and {@code SoundPlayer.run} retries a
 * failing file at once (now backed off by 100 ms), so the line is tested
 * before any playlist is set.
 *
 * <p><b>Every failure ends in today's behaviour</b> -- the piece starts at
 * client attach, or silence -- never in FreeCol music or two pieces: no
 * soundtrack pack, an undecodable title, {@code --no-sound} (no
 * {@code prepare}), volume 0, no usable audio line, a preparation slower
 * than the client constructor ({@link #take} cancels it), or a headless /
 * {@code --no-splash} start (nothing calls {@code prepare}).
 */
public final class ClassicEarlyMusic {

    private static final Logger logger = Logger.getLogger(ClassicEarlyMusic.class.getName());

    /** The player's line buffer, as SoundPlayer opens it. */
    private static final int LINE_BUFFER = 16384;


    /**
     * The hand-over state machine, free of audio and Swing so it is
     * unit-tested with a fake backend.  All transitions are synchronized:
     * the preparation thread, the EDT ({@link #go}) and the client
     * constructor ({@link #take}) race on it.
     *
     * @param <P> The player type.
     */
    static final class Handover<P> {

        /** The states. */
        enum State { IDLE, PREPARING, READY, PLAYING, TAKEN, CANCELLED }

        /** Builds and starts the player. */
        interface Backend<P> {
            /** @return A new player (called under the lock), or null. */
            P build();
            /** Start the title piece on {@code player}. */
            void play(P player);
        }

        /** What {@link #take} hands over. */
        static final class Taken<P> {
            final P player;
            final boolean playing;

            Taken(P player, boolean playing) {
                this.player = player;
                this.playing = playing;
            }
        }

        private State state = State.IDLE;
        private boolean goRequested = false;
        private P player = null;
        private Backend<P> backend = null;

        /** @return True if the preparation should start (IDLE before). */
        synchronized boolean begin() {
            if (this.state != State.IDLE) return false;
            this.state = State.PREPARING;
            return true;
        }

        /**
         * The preparation succeeded: build the player, unless the hand-over
         * was cancelled or taken meanwhile (then nothing is built), and
         * start it if {@link #go} came first.
         *
         * @param b The backend.
         * @return True if a player was built.
         */
        synchronized boolean prepared(Backend<P> b) {
            if (this.state != State.PREPARING) return false;
            final P p = b.build();
            if (p == null) {
                this.state = State.CANCELLED;
                return false;
            }
            this.player = p;
            this.backend = b;
            this.state = State.READY;
            if (this.goRequested) startPlaying();
            return true;
        }

        /** The preparation refused or failed: no player. */
        synchronized void failed() {
            if (this.state == State.PREPARING) this.state = State.CANCELLED;
        }

        /**
         * The first picture is on screen: start now if ready, else as soon
         * as the preparation is.  A no-op without {@link #begin}.
         *
         * @return True if the piece started now.
         */
        synchronized boolean go() {
            if (this.state == State.IDLE) return false;
            this.goRequested = true;
            if (this.state != State.READY) return false;
            startPlaying();
            return true;
        }

        private void startPlaying() {
            this.backend.play(this.player);
            this.state = State.PLAYING;
        }

        /**
         * The sound controller takes over.  A preparation still running
         * is cancelled (its late result builds nothing); afterwards every
         * call returns null.
         *
         * @return The player and whether it plays, or null.
         */
        synchronized Taken<P> take() {
            switch (this.state) {
            case READY: case PLAYING:
                final Taken<P> t = new Taken<>(this.player, this.state == State.PLAYING);
                this.state = State.TAKEN;
                this.player = null;
                return t;
            case PREPARING:
                this.state = State.CANCELLED;
                return null;
            default:
                if (this.state == State.IDLE) this.state = State.TAKEN;
                return null;
            }
        }

        /** @return The current state. */
        synchronized State state() {
            return this.state;
        }
    }


    /** The built player and what it was built with; immutable. */
    static final class Built {

        final SoundPlayer player;
        /** The title piece. */
        final File title;
        /** The stand-in options the player listens to. */
        final AudioMixerOption mixer;
        final PercentageOption volume;

        Built(SoundPlayer player, File title, AudioMixerOption mixer,
              PercentageOption volume) {
            this.player = player;
            this.title = title;
            this.mixer = mixer;
            this.volume = volume;
        }
    }

    /** What {@link ClassicSoundController} takes over; immutable. */
    static final class Early {

        final SoundPlayer player;
        final File title;
        /** Whether the title piece is already playing. */
        final boolean playing;
        final AudioMixerOption mixer;
        final PercentageOption volume;

        Early(Built b, boolean playing) {
            this.player = b.player;
            this.title = b.title;
            this.playing = playing;
            this.mixer = b.mixer;
            this.volume = b.volume;
        }
    }


    /** The one hand-over of this JVM. */
    private static final Handover<Built> HANDOVER = new Handover<>();

    /** System.nanoTime() when {@link #prepare} was called. */
    private static volatile long prepareStart = 0L;


    private ClassicEarlyMusic() {}   // static only

    /**
     * Start preparing the title piece on a daemon thread.  Any thread,
     * once; later calls do nothing.
     */
    public static void prepare() {
        if (!HANDOVER.begin()) return;
        prepareStart = System.nanoTime();
        final Thread t = new Thread(ClassicEarlyMusic::prepareNow,
                                    "Classic early music");
        t.setDaemon(true);
        t.start();
    }

    /**
     * The first picture is on screen: start the title piece now, or as
     * soon as it is prepared.  A no-op without {@link #prepare}.
     */
    public static void go() {
        HANDOVER.go();
    }

    /**
     * Hand the player over (ClassicSoundController's constructor).
     *
     * @return The player, or null when there is none (never prepared,
     *     refused, or still preparing -- then cancelled).
     */
    static Early take() {
        final Handover.Taken<Built> t = HANDOVER.take();
        return (t == null) ? null : new Early(t.player, t.playing);
    }


    // The preparation thread

    private static void prepareNow() {
        try {
            final File title = resolveTitle();
            if (title == null) {
                refuse("no classic_music soundtrack");
                return;
            }
            final Map<String, String> opts = peekOptions();
            final int volume = parseVolume(opts.get(ClientOptions.MUSIC_VOLUME));
            final String vr = checkVolume(volume);
            if (vr != null) {
                refuse(vr);
                return;
            }
            final AudioMixerOption mixer = new AudioMixerOption(null);
            mixer.setValue(findMixer(mixer, opts.get(ClientOptions.AUDIO_MIXER)));
            final PercentageOption vol = new PercentageOption(ClientOptions.MUSIC_VOLUME, null);
            vol.setValue(volume);
            final String lr = checkLine(title, mixer);
            if (lr != null) {
                refuse(lr);
                return;
            }
            HANDOVER.prepared(new Handover.Backend<Built>() {
                    @Override
                    public Built build() {
                        return new Built(new SoundPlayer(mixer, vol), title, mixer, vol);
                    }

                    @Override
                    public void play(Built b) {
                        b.player.setDefaultPlaylist(b.title);
                        logger.info("Classic start-up: title piece started after "
                            + ClassicStartupScreen.sinceLaunchMs() + " ms ("
                            + b.title.getName() + ", prepared in "
                            + (System.nanoTime() - prepareStart) / 1000000L
                            + " ms)");
                    }
                });
        } catch (RuntimeException | Error e) {
            HANDOVER.failed();
            logger.log(Level.INFO, "Classic start-up: early music not started: "
                + e, e);
        }
    }

    private static void refuse(String reason) {
        HANDOVER.failed();
        logger.info("Classic start-up: early music not started: " + reason
            + " (the title piece starts with the client, as before)");
    }

    /**
     * The title piece, by ClassicSoundController's rule, from the plain
     * soundtrack pack files.
     *
     * @return The file, or null without a soundtrack.
     */
    static File resolveTitle() {
        final File dir = ClassicPackFiles.modDirectory(ClassicSoundController.PACK_ID);
        if (dir == null || !dir.isDirectory()) return null;
        final Map<String, String> props = ClassicPackFiles.readProperties(
            new File(dir, "resources.properties"), true);
        File configured = null;
        final String tv = props.get(ClassicSoundController.TITLE_KEY);
        if (tv != null && !tv.isEmpty()) {
            final File f = new File(dir, tv);
            if (f.isFile()) configured = f;
        }
        final String td = props.get(ClassicSoundController.TRACKS_KEY);
        final File tracks = new File(dir, (td == null || td.isEmpty())
                                     ? "resources/music" : td);
        final List<File> all = new ArrayList<>();
        final File[] files = tracks.listFiles();
        if (files != null) {
            for (File f : files) {
                final String n = f.getName().toLowerCase(Locale.ROOT);
                if (f.isFile() && (n.endsWith(".wav") || n.endsWith(".ogg"))) all.add(f);
            }
        }
        return ClassicSoundController.Soundtrack.of(configured, all).title;
    }

    /** The music volume and mixer from the options file; empty on failure. */
    private static Map<String, String> peekOptions() {
        final Map<String, String> m = new HashMap<>();
        m.put(ClientOptions.MUSIC_VOLUME, null);
        m.put(ClientOptions.AUDIO_MIXER, null);
        try {
            final File f = FreeColDirectories.getClientOptionsFile();
            if (f != null && f.isFile()) {
                try (FreeColXMLReader xr = new FreeColXMLReader(f)) {
                    xr.readAttributeValues(m, "value");
                }
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "Options peek failed; defaults", e);
        }
        return m;
    }

    /**
     * The music volume from its option string.
     *
     * @param s The value, or null.
     * @return 0..100; 100 (the option's default) when missing or bad.
     */
    static int parseVolume(String s) {
        if (s == null) return 100;
        try {
            return Math.max(0, Math.min(100, Integer.parseInt(s.trim())));
        } catch (NumberFormatException e) {
            return 100;
        }
    }

    /**
     * Whether a volume allows the early start.
     *
     * @param volume 0..100.
     * @return Null if so, else the reason.
     */
    static String checkVolume(int volume) {
        return (volume <= 0) ? "music volume 0" : null;
    }

    /** The configured mixer among the choices, or null for automatic. */
    private static AudioMixerOption.MixerWrapper findMixer(AudioMixerOption o,
                                                           String name) {
        if (name == null) return null;
        for (AudioMixerOption.MixerWrapper mw : o.getChoices()) {
            if (name.equals(mw.getKey())) return mw;
        }
        return null;
    }

    /**
     * Open the title's audio stream and test-open a line on the mixer,
     * silently (nothing is written).
     *
     * @return Null if playable, else the reason.
     */
    private static String checkLine(File title, AudioMixerOption mixer) {
        try (AudioInputStream in = SoundPlayer.getAudioInputStream(title)) {
            final AudioFormat fmt = in.getFormat();
            final Mixer m = AudioSystem.getMixer(mixer.getValue().getMixerInfo());
            final DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
            if (!m.isLineSupported(info)) return "the mixer cannot play " + fmt;
            final SourceDataLine line = (SourceDataLine) m.getLine(info);
            line.open(fmt, LINE_BUFFER);
            line.close();
            return null;
        } catch (Exception e) {
            return "no usable audio line (" + e + ")";
        }
    }
}
