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

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import javax.imageio.ImageIO;
import javax.swing.JComponent;


/**
 * The acceptance <b>frame recorder</b> (build spec W1): with
 * {@code -Dfreecol.classic.recordDir=<dir>} the in-game HUD is recorded the
 * way the original's DOSBox clips were decoded -- 320x200 indexed frames on
 * an absolute 70.0863 Hz grid, in {@code ZmbvExtract}'s layout -- so the
 * tools that measured the original (BlinkScan, MoveScan2, Activation,
 * MarginCheck, Ring, EndTurn) measure this game the same way.
 *
 * <p><b>Output</b>, in the record directory:
 * <ul>
 *   <li>{@code frame_NNNNNN.png}: frame 0 and every frame whose pixels
 *       changed, 8 bit indexed with a 256-entry PLTE (always the same
 *       palette, see below);</li>
 *   <li>{@code timeline.csv}: one row per frame, ZmbvExtract's columns
 *       {@value #TIMELINE_HEADER}; frame k is at k * 1000 / 70.0863 ms
 *       after the recorder started;</li>
 *   <li>{@code events.log}: {@code nanoTime,ms,frame,event,detail}, one
 *       line per event, {@code System.nanoTime} stamps, ms and frame on the
 *       timeline's clock (the detail is the rest of the line);</li>
 *   <li>{@code summary.txt}, written when the recorder closes.</li>
 * </ul>
 *
 * <p><b>Events</b> ({@link #event}; a hook costs one volatile read while
 * recording is off).  Names in use: {@code recorder-start/stop},
 * {@code late} (ticks the sampler slept through), {@code state} (the
 * harness probe changed: turn, current player, view mode, active unit and
 * moves, view origin, dialogs), {@code script}, {@code script-error},
 * {@code script-end}, {@code key-post/press/release}, {@code click},
 * {@code move-key}, {@code move-done}, {@code pan}, {@code slide-start},
 * {@code slide-step}, {@code slide-end}, {@code slide-skip},
 * {@code final-draw}, {@code end-turn}, {@code dialog-open/close},
 * {@code menu-open/close}, {@code blink} (W3: {@code arm <reason>},
 * {@code rebase panel}, {@code off n=..}, {@code on n=..},
 * {@code hold <reason>}, {@code stop <reason>}),
 * {@code view-jump} (W4: {@code <reason> <old> -> <new> tile=.. cell=..
 * now=..}, the reason {@code activate}, {@code move}, {@code foreign-move},
 * {@code terrain}, {@code focus}, {@code pan} or {@code default}),
 * {@code music-request}, {@code music-mode} (the jukebox switched to the
 * title or the in-game playlist), {@code pref}.  Reserved for
 * the M1 work items: {@code endturn-timer-start/fire}
 * (W5), {@code palette-step} (W6c), {@code music-fade} (W15).
 *
 * <p><b>Where the pixels come from.</b>  While recording, the HUD pane
 * ({@link ClassicHudPane}) is the painting origin of all its children and
 * paints itself into an image kept here, which is then shown on the screen
 * (one paint plus one blit); each finished paint is also copied, under a
 * lock, into the image the sampler reads.  So a sample holds exactly what
 * the screen shows, never half a paint, every paint stays on the EDT, and
 * the blocking slide
 * ({@code ClassicMapViewer.animateMove}, which pushes its frames with
 * {@code paintImmediately}) is recorded frame by frame.  The sampler thread
 * reads the copy at the centre of each s x s block (s = the HUD's integer
 * scale) on its own absolute clock ({@link #waitUntil}).  Not in the copy:
 * the first scene (the frame's glass pane) and the Swing popups
 * ({@link ClassicDialog} and the other JDialogs), whose opening and closing
 * are events instead.  {@code summary.txt} gives the paint cost (the
 * pane's paint, the copy, the blit), and
 * {@code -Dfreecol.classic.recordFrames=false} records the events alone,
 * with the HUD painting as without the recorder, to see the game's own
 * timing.
 *
 * <p><b>Palette.</b>  {@code -Dfreecol.classic.recordPalette=<file>} names
 * a PNG with a PLTE (the original clip's frame #0) or a raw 768-byte RGB
 * file.  Colours map to indices exactly; where the palette holds a colour
 * twice (59/120, 56/121) the lowest index wins.  A colour that is not in
 * the palette maps to the nearest entry and is counted in the summary.
 * Without a palette file the indices are handed out in order of first
 * sight (the tools then still see changes, but not the original's index
 * meanings).
 */
public final class ClassicFrameRecorder {

    private static final Logger logger
        = Logger.getLogger(ClassicFrameRecorder.class.getName());

    /** System property: the record directory (no recording without it). */
    public static final String DIR_PROPERTY = "freecol.classic.recordDir";

    /** System property: the palette file (see the class comment). */
    public static final String PALETTE_PROPERTY = "freecol.classic.recordPalette";

    /**
     * System property: "false" records the events only -- no frames, and
     * the HUD paints as without the recorder (to measure the game's own
     * timing, untouched by the frame capture).
     */
    public static final String FRAMES_PROPERTY = "freecol.classic.recordFrames";

    /**
     * The capture rate of the original's DOSBox clips, 70.0863 Hz (their
     * strh rate/scale, 70086303/1000000, as ZmbvExtract reads it).
     */
    public static final double HZ = 70086303 / 1e6;

    /** The canvas size. */
    static final int W = 320, H = 200;

    /** ZMBV's block edge, for the {@code changedBlocks} column. */
    static final int BLOCK = 16;

    /** Blocks in a frame (20 x 13 = 260, as ZmbvExtract's keyframes). */
    static final int BLOCKS = ((W + BLOCK - 1) / BLOCK) * ((H + BLOCK - 1) / BLOCK);

    /** ZmbvExtract's timeline header. */
    static final String TIMELINE_HEADER = "frameIndex,timeMs,keyframe,"
        + "paletteChanged,pixelsChanged,changedBlocks,pngFile,"
        + "paletteEntriesChanged,status";

    /** Nanoseconds per frame on the clip's grid. */
    private static final double NS_PER_FRAME = 1e9 / HZ;

    /** Guards {@link #initDone} and the creation of {@link #instance}. */
    private static final Object INIT = new Object();

    /** Whether {@link #get} has looked at the property. */
    private static boolean initDone = false;

    /** The recorder, or null (recording off): what every hook reads. */
    private static volatile ClassicFrameRecorder instance = null;


    /** The record directory. */
    private final File dir;

    /** The colour to index map (sampler thread only, after start). */
    private final Palette palette;

    /** The start of the clock: frame 0. */
    private final long t0;

    /** Whether frames are recorded (else events only). */
    private final boolean framesOn;

    /** Guards the published HUD copy and its geometry. */
    private final Object frameLock = new Object();

    /**
     * The HUD pane as painted (EDT only): the pane paints into it, unlocked,
     * and it is shown from there.
     */
    private BufferedImage work = null;

    /** The pane {@link #work} belongs to (EDT only). */
    private Object workOwner = null;

    /**
     * The published copy the sampler reads: each finished paint is copied
     * here under {@link #frameLock}, so a sample never sees half a paint.
     */
    private BufferedImage shadow = null;

    /** The 320x200 canvas inside {@link #shadow}, and its scale. */
    private Rectangle canvas = null;
    private int scale = 1;

    /** Guards {@link #events} and {@link #eventsOpen}. */
    private final Object eventsLock = new Object();
    private final Writer events;
    private boolean eventsOpen = true;

    /** The timeline (sampler thread, then close), or null (events only). */
    private final BufferedWriter timeline;

    /** Writes the PNGs off the sampler thread. */
    private final ExecutorService pngWriter;

    /** The sampler. */
    private final Thread sampler;

    /** Set by {@link #close}: the sampler stops at its next tick. */
    private volatile boolean stopping = false;

    /** Whether {@link #close} ran. */
    private boolean closed = false;

    /** The harness's state probe, sampled every frame, or null. */
    private volatile Supplier<String> probe = null;

    /** Sampler statistics. */
    private long frames = 0, pngs = 0, lateTicks = 0, probeErrors = 0;
    private long maxLateNs = 0;

    /**
     * Paint statistics (EDT): the count, and the total and longest ns of
     * the pane's own paint, of the copy for the sampler and of the blit to
     * the screen.
     */
    private long paints = 0;
    private long paintNs = 0, paintMaxNs = 0, publishNs = 0, publishMaxNs = 0,
        blitNs = 0, blitMaxNs = 0;


    /**
     * Start recording into {@code dir}.
     *
     * @param dir The record directory (created if missing).
     * @param palette The palette.
     * @param frames Whether to record frames (else events only).
     * @exception IOException if the files cannot be created.
     */
    private ClassicFrameRecorder(File dir, Palette palette, boolean frames)
        throws IOException {
        this.dir = dir;
        this.palette = palette;
        this.framesOn = frames;
        Files.createDirectories(dir.toPath());
        this.events = Files.newBufferedWriter(new File(dir, "events.log").toPath(),
                                              StandardCharsets.UTF_8);
        this.events.write("nanoTime,ms,frame,event,detail\n");
        if (frames) {
            this.timeline = Files.newBufferedWriter(
                new File(dir, "timeline.csv").toPath(), StandardCharsets.UTF_8);
            this.timeline.write(TIMELINE_HEADER + "\n");
        } else {
            this.timeline = null;
        }
        this.pngWriter = Executors.newSingleThreadExecutor(r -> {
                final Thread t = new Thread(r, "classic-recorder-png");
                t.setDaemon(true);
                return t;
            });
        this.t0 = System.nanoTime();
        log(this.t0, "recorder-start", "dir=" + dir.getAbsolutePath()
            + " hz=" + HZ + " frames=" + frames + " palette=" + palette.source);
        this.sampler = new Thread(this::sampleLoop, "classic-recorder");
        this.sampler.setDaemon(true);
        this.sampler.setPriority(Thread.MAX_PRIORITY);
        this.sampler.start();
        Runtime.getRuntime().addShutdownHook(
            new Thread(this::close, "classic-recorder-close"));
    }

    /**
     * Start a recorder that is not the game's (tests).
     *
     * @param dir The record directory.
     * @param palette The palette.
     * @return The running recorder; {@link #close} it.
     * @exception IOException if the files cannot be created.
     */
    static ClassicFrameRecorder open(File dir, Palette palette) throws IOException {
        return new ClassicFrameRecorder(dir, palette, true);
    }

    /**
     * The recorder, started on the first call if {@link #DIR_PROPERTY} is
     * set; null when recording is off (or could not start).
     *
     * @return The recorder, or null.
     */
    static ClassicFrameRecorder get() {
        synchronized (INIT) {
            if (!initDone) {
                initDone = true;
                final String dir = System.getProperty(DIR_PROPERTY);
                if (dir != null && !dir.isBlank()) {
                    try {
                        instance = new ClassicFrameRecorder(new File(dir),
                            Palette.fromProperty(System.getProperty(PALETTE_PROPERTY)),
                            !"false".equalsIgnoreCase(System.getProperty(FRAMES_PROPERTY)));
                        logger.info("Classic recorder: recording into " + dir);
                    } catch (IOException | RuntimeException e) {
                        logger.log(Level.WARNING, "Classic recorder: not started", e);
                    }
                }
            }
            return instance;
        }
    }

    /**
     * Is a recording running?  For hooks whose detail costs something to
     * build.
     *
     * @return True while recording.
     */
    static boolean on() {
        return instance != null;
    }

    /**
     * Log an event, stamped now.  A no-op while recording is off.
     *
     * @param type The event name (see the class comment).
     * @param detail Free text, or null.
     */
    public static void event(String type, String detail) {
        final ClassicFrameRecorder r = instance;
        if (r != null) r.log(System.nanoTime(), type, detail);
    }

    /**
     * The recorder the HUD paints through: the running one if it records
     * frames, else null.
     *
     * @return The recorder, or null.
     */
    static ClassicFrameRecorder forFrames() {
        final ClassicFrameRecorder r = get();
        return (r != null && r.framesOn) ? r : null;
    }

    /** Stop the recording, if one runs (flushes everything). */
    static void shutdown() {
        final ClassicFrameRecorder r = instance;
        if (r != null) r.close();
    }

    /**
     * Set the state probe: sampled once per frame on the sampler thread,
     * logged as a {@code state} event whenever its text changes.  It must
     * be cheap and must not change anything.
     *
     * @param probe The probe, or null.
     */
    void setProbe(Supplier<String> probe) {
        this.probe = probe;
    }

    /** @return The record directory. */
    File getDirectory() {
        return this.dir;
    }

    /**
     * Paint a HUD pane through the copy (see the class comment).  EDT only,
     * from the pane's {@code paint}.
     *
     * @param pane The pane.
     * @param g The screen graphics the pane was given.
     * @param canvas The 320x200 canvas in the pane.
     * @param s The canvas scale.
     * @param painter Paints the pane, children included, into the graphics
     *     it is given (clipped like {@code g}).
     */
    void paintThrough(JComponent pane, Graphics g, Rectangle canvas, int s,
                      Consumer<Graphics2D> painter) {
        final int w = pane.getWidth(), h = pane.getHeight();
        if (w <= 0 || h <= 0) return;
        boolean full = false;
        if (this.work == null || this.workOwner != pane
            || this.work.getWidth() != w || this.work.getHeight() != h) {
            this.work = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            this.workOwner = pane;
            full = true;
        }
        final BufferedImage img = this.work;
        final Rectangle clip = full ? null : g.getClipBounds();
        final long a = System.nanoTime();
        final Graphics2D sg = img.createGraphics();
        try {
            if (clip != null) sg.setClip(clip);
            painter.accept(sg);
        } finally {
            sg.dispose();
        }
        final long b = System.nanoTime();
        synchronized (this.frameLock) {
            if (this.shadow == null || this.shadow.getWidth() != w
                || this.shadow.getHeight() != h) {
                this.shadow = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                full = true;
            }
            final Graphics2D pg = this.shadow.createGraphics();
            try {
                if (!full && clip != null) pg.setClip(clip);
                pg.drawImage(img, 0, 0, null);
            } finally {
                pg.dispose();
            }
            this.canvas = new Rectangle(canvas);
            this.scale = Math.max(1, s);
        }
        final long c = System.nanoTime();
        g.drawImage(img, 0, 0, null);
        final long d = System.nanoTime();
        this.paints++;
        this.paintNs += b - a;
        this.paintMaxNs = Math.max(this.paintMaxNs, b - a);
        this.publishNs += c - b;
        this.publishMaxNs = Math.max(this.publishMaxNs, c - b);
        this.blitNs += d - c;
        this.blitMaxNs = Math.max(this.blitMaxNs, d - c);
    }

    /**
     * Wait until a moment on the clock: sleep to about 1.5 ms before it,
     * then spin.  Not {@code LockSupport.parkNanos}: on Windows it wakes on
     * the 15.6 ms system tick (measured here: up to 15 ms late), while
     * {@code Thread.sleep} keeps to about a millisecond.
     *
     * @param due The {@code System.nanoTime} to wait for.
     * @exception InterruptedException if interrupted.
     */
    static void waitUntil(long due) throws InterruptedException {
        for (;;) {
            final long rem = due - System.nanoTime();
            if (rem <= 0) return;
            if (rem > 2_000_000L) {
                Thread.sleep((rem - 1_500_000L) / 1_000_000L);
            } else {
                Thread.onSpinWait();
            }
        }
    }

    /**
     * Write one event line.
     *
     * @param nano The {@code System.nanoTime} stamp.
     * @param type The event name.
     * @param detail The detail, or null.
     */
    void log(long nano, String type, String detail) {
        final long d = nano - this.t0;
        final String line = String.format(Locale.ROOT, "%d,%.3f,%d,%s,%s%n",
            nano, d / 1e6, (long)Math.floor(d / NS_PER_FRAME), type,
            (detail == null) ? "" : detail.replace('\n', ' ').replace('\r', ' '));
        synchronized (this.eventsLock) {
            if (!this.eventsOpen) return;
            try {
                this.events.write(line);
                this.events.flush();
            } catch (IOException e) {
                logger.log(Level.WARNING, "Classic recorder: event lost", e);
            }
        }
    }

    /** The sampler: one frame per tick of the absolute 70.0863 Hz grid. */
    private void sampleLoop() {
        final byte[] prev = new byte[W * H];
        final byte[] cur = new byte[W * H];
        String lastState = null;
        long k = 0;
        try {
            while (!this.stopping) {
                final long due = this.t0 + Math.round(k * NS_PER_FRAME);
                try {
                    waitUntil(due);
                } catch (InterruptedException e) {
                    break;
                }
                if (this.stopping) break;
                final long now = System.nanoTime();
                // Ticks slept through repeat the frame before (the change,
                // if any, lands on the frame sampled now).
                final long kNow = Math.max(k,
                    (long)Math.floor((now - this.t0) / NS_PER_FRAME));
                if (kNow > k) {
                    if (this.framesOn) {
                        for (long j = k; j < kNow; j++) writeRow(j, 0, 0, "");
                    }
                    this.lateTicks += kNow - k;
                    log(now, "late", "ticks=" + (kNow - k) + " at=" + kNow);
                    k = kNow;
                }
                this.maxLateNs = Math.max(this.maxLateNs,
                    now - (this.t0 + Math.round(k * NS_PER_FRAME)));
                if (this.framesOn) sampleFrame(k, prev, cur);
                final Supplier<String> p = this.probe;
                if (p != null) {
                    String s;
                    try {
                        s = p.get();
                    } catch (RuntimeException e) {
                        s = null;
                        this.probeErrors++;
                    }
                    if (s != null && !s.equals(lastState)) {
                        lastState = s;
                        log(System.nanoTime(), "state", s);
                    }
                }
                if (k % 70 == 0 && this.timeline != null) this.timeline.flush();
                k++;
            }
        } catch (IOException | RuntimeException e) {
            logger.log(Level.WARNING, "Classic recorder: sampler stopped", e);
            log(System.nanoTime(), "recorder-error", e.toString());
        }
    }

    /**
     * Sample one frame: downsample the published copy, compare it with the
     * frame before, write its timeline row and, if it changed, its PNG.
     * Sampler thread.
     *
     * @param k The frame.
     * @param prev The previous frame (updated on a change).
     * @param cur Scratch for this frame.
     * @exception IOException if the timeline cannot be written.
     */
    private void sampleFrame(long k, byte[] prev, byte[] cur) throws IOException {
        synchronized (this.frameLock) {
            downsample(this.shadow, this.canvas, this.scale, this.palette, cur);
        }
        final int changed, blocks;
        if (k == 0) {
            changed = W * H;
            blocks = BLOCKS;
        } else {
            final int[] cb = diff(prev, cur);
            changed = cb[0];
            blocks = cb[1];
        }
        String png = "";
        if (changed > 0) {
            png = String.format(Locale.ROOT, "frame_%06d.png", k);
            final File f = new File(this.dir, png);
            final byte[] pix = cur.clone();
            final byte[] plte = this.palette.plte();
            this.pngWriter.execute(() -> {
                    try {
                        writeIndexedPng(f, W, H, pix, plte);
                    } catch (IOException e) {
                        logger.log(Level.WARNING,
                            "Classic recorder: " + f + " not written", e);
                    }
                });
            this.pngs++;
            System.arraycopy(cur, 0, prev, 0, cur.length);
        }
        writeRow(k, changed, blocks, png);
    }

    /**
     * Write one timeline row (ZmbvExtract's format).  Sampler thread.
     *
     * @param k The frame.
     * @param changed The changed pixels.
     * @param blocks The changed 16x16 blocks.
     * @param png The PNG written for it, or "".
     * @exception IOException if the timeline cannot be written.
     */
    private void writeRow(long k, int changed, int blocks, String png)
        throws IOException {
        this.timeline.write(timelineRow(k, changed, blocks, png));
        this.frames = k + 1;
    }

    /**
     * One timeline row, as ZmbvExtract writes it: frame 0 is the keyframe
     * with the whole palette, every later frame keeps it.
     *
     * @param k The frame.
     * @param changed The changed pixels.
     * @param blocks The changed blocks.
     * @param png The PNG file name, or "".
     * @return The row, newline included.
     */
    static String timelineRow(long k, int changed, int blocks, String png) {
        final boolean key = k == 0;
        return String.format(Locale.ROOT, "%d,%.3f,%d,%d,%d,%d,%s,%d,ok\n",
            k, k * 1000.0 / HZ, key ? 1 : 0, key ? 1 : 0, changed, blocks,
            png, key ? 256 : 0);
    }

    /**
     * Downsample the HUD copy to the 320x200 canvas: the pixel at the
     * centre of each s x s block, mapped to its index.  Outside the copy (or
     * without one) the canvas is black.
     *
     * @param img The copy, or null.
     * @param canvas The canvas in it, or null.
     * @param s The scale.
     * @param palette The colour map.
     * @param out The 320x200 indices.
     */
    static void downsample(BufferedImage img, Rectangle canvas, int s,
                           Palette palette, byte[] out) {
        if (img == null || canvas == null) {
            Arrays.fill(out, (byte)palette.index(0));
            return;
        }
        final int iw = img.getWidth(), ih = img.getHeight();
        final int[] row = new int[iw];
        final boolean direct = img.getType() == BufferedImage.TYPE_INT_RGB;
        final int half = s / 2;
        for (int y = 0; y < H; y++) {
            final int py = canvas.y + y * s + half;
            if (py < 0 || py >= ih) {
                Arrays.fill(row, 0);
            } else if (direct) {
                img.getRaster().getDataElements(0, py, iw, 1, row);
            } else {
                img.getRGB(0, py, iw, 1, row, 0, iw);
            }
            for (int x = 0; x < W; x++) {
                final int px = canvas.x + x * s + half;
                final int rgb = (px < 0 || px >= iw || py < 0 || py >= ih) ? 0
                    : row[px] & 0xFFFFFF;
                out[y * W + x] = (byte)palette.index(rgb);
            }
        }
    }

    /**
     * Compare two frames.
     *
     * @param a The previous frame.
     * @param b The current frame.
     * @return {changed pixels, changed 16x16 blocks}.
     */
    static int[] diff(byte[] a, byte[] b) {
        final int bw = (W + BLOCK - 1) / BLOCK;
        final boolean[] hit = new boolean[BLOCKS];
        int n = 0, blocks = 0;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                final int i = y * W + x;
                if (a[i] == b[i]) continue;
                n++;
                final int bi = (y / BLOCK) * bw + x / BLOCK;
                if (!hit[bi]) {
                    hit[bi] = true;
                    blocks++;
                }
            }
        }
        return new int[] { n, blocks };
    }

    /**
     * Stop and flush: the sampler ends, the PNG queue drains, the files are
     * closed and the summary is written.  Idempotent; also the shutdown
     * hook.
     */
    void close() {
        synchronized (this) {
            if (this.closed) return;
            this.closed = true;
        }
        this.stopping = true;
        if (this.sampler != Thread.currentThread()) {
            this.sampler.interrupt();
            try {
                this.sampler.join(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        this.pngWriter.shutdown();
        try {
            if (!this.pngWriter.awaitTermination(30, TimeUnit.SECONDS)) {
                logger.warning("Classic recorder: PNG queue did not drain");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (this.timeline != null) {
            try {
                this.timeline.close();
            } catch (IOException e) {
                logger.log(Level.WARNING, "Classic recorder: timeline", e);
            }
        }
        final List<String> s = new ArrayList<>();
        s.add("frames: " + (this.framesOn ? "" + this.frames : "off (events only)"));
        s.add(String.format(Locale.ROOT, "duration: %.3f s",
                            (System.nanoTime() - this.t0) / 1e9));
        s.add("pngs: " + this.pngs);
        s.add("lateTicks: " + this.lateTicks);
        s.add(String.format(Locale.ROOT, "maxLateMs: %.3f", this.maxLateNs / 1e6));
        s.add("probeErrors: " + this.probeErrors);
        final long np = Math.max(1, this.paints);
        s.add(String.format(Locale.ROOT, "paints: %d; paint mean %.3f max %.3f ms;"
            + " copy mean %.3f max %.3f ms; blit mean %.3f max %.3f ms",
            this.paints, this.paintNs / 1e6 / np, this.paintMaxNs / 1e6,
            this.publishNs / 1e6 / np, this.publishMaxNs / 1e6,
            this.blitNs / 1e6 / np, this.blitMaxNs / 1e6));
        synchronized (this.frameLock) {
            s.add("canvas: " + this.canvas + " scale " + this.scale);
        }
        s.add("palette: " + this.palette.source);
        s.add("paletteMissPixels: " + this.palette.missPixels);
        s.add("paletteMissColours: " + this.palette.missedColours());
        try {
            Files.write(new File(this.dir, "summary.txt").toPath(), s,
                        StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Classic recorder: summary", e);
        }
        log(System.nanoTime(), "recorder-stop", "frames=" + this.frames
            + " pngs=" + this.pngs + " lateTicks=" + this.lateTicks);
        synchronized (this.eventsLock) {
            this.eventsOpen = false;
            try {
                this.events.close();
            } catch (IOException e) {
                logger.log(Level.WARNING, "Classic recorder: events", e);
            }
        }
        logger.info("Classic recorder: stopped, " + this.frames + " frames, "
            + this.pngs + " PNGs in " + this.dir);
    }


    // PNG

    /** The PNG signature. */
    private static final byte[] PNG_SIG = {
        (byte)0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'
    };

    /**
     * Write an 8-bit indexed PNG (filter none, one IDAT, a 256-entry
     * PLTE), the format ZmbvExtract writes.
     *
     * @param f The file.
     * @param w The width.
     * @param h The height.
     * @param pix The w*h indices.
     * @param plte The 768-byte palette.
     * @exception IOException if the file cannot be written.
     */
    static void writeIndexedPng(File f, int w, int h, byte[] pix, byte[] plte)
        throws IOException {
        final byte[] raw = new byte[h * (w + 1)];
        for (int y = 0; y < h; y++) {
            System.arraycopy(pix, y * w, raw, y * (w + 1) + 1, w);
        }
        final Deflater def = new Deflater(Deflater.BEST_SPEED);
        final ByteArrayOutputStream idat = new ByteArrayOutputStream(raw.length / 4 + 64);
        try {
            def.setInput(raw);
            def.finish();
            final byte[] buf = new byte[1 << 15];
            while (!def.finished()) {
                final int n = def.deflate(buf);
                idat.write(buf, 0, n);
            }
        } finally {
            def.end();
        }
        final byte[] ihdr = new byte[13];
        putInt(ihdr, 0, w);
        putInt(ihdr, 4, h);
        ihdr[8] = 8;    // bit depth
        ihdr[9] = 3;    // indexed colour
        try (OutputStream os = new BufferedOutputStream(
                 Files.newOutputStream(f.toPath()), 1 << 16)) {
            os.write(PNG_SIG);
            chunk(os, "IHDR", ihdr);
            chunk(os, "PLTE", plte);
            chunk(os, "IDAT", idat.toByteArray());
            chunk(os, "IEND", new byte[0]);
        }
    }

    private static void putInt(byte[] b, int p, int v) {
        b[p] = (byte)(v >>> 24);
        b[p + 1] = (byte)(v >>> 16);
        b[p + 2] = (byte)(v >>> 8);
        b[p + 3] = (byte)v;
    }

    private static void chunk(OutputStream os, String type, byte[] data)
        throws IOException {
        final byte[] len = new byte[4];
        putInt(len, 0, data.length);
        final byte[] t = type.getBytes(StandardCharsets.US_ASCII);
        final CRC32 crc = new CRC32();
        crc.update(t);
        crc.update(data);
        final byte[] c = new byte[4];
        putInt(c, 0, (int)crc.getValue());
        os.write(len);
        os.write(t);
        os.write(data);
        os.write(c);
    }


    /**
     * The colour to index map of the recording.  Not thread safe: the
     * sampler alone uses it once the recorder runs.
     */
    static final class Palette {

        /** Where the palette came from (for the summary). */
        final String source;

        /** The entries, 0xRRGGBB. */
        private final int[] rgb = new int[256];

        /** Whether the entries are fixed (a file) or handed out as seen. */
        private final boolean fixed;

        /** Entries handed out so far (adaptive only). */
        private int used = 0;

        /** Exact colour to the lowest index holding it. */
        private final Map<Integer, Integer> exact = new HashMap<>();

        /** Colours not in a fixed palette, to their nearest index. */
        private final Map<Integer, Integer> nearest = new HashMap<>();

        /** Pixels mapped to a nearest entry, by colour. */
        private final Map<Integer, Long> misses = new HashMap<>();

        /** Pixels mapped to a nearest entry. */
        long missPixels = 0;

        /** One-entry cache: runs of one colour are the common case. */
        private int lastRgb = -1, lastIndex = 0;
        private boolean lastMiss = false;

        private Palette(String source, int[] entries, boolean fixed) {
            this.source = source;
            this.fixed = fixed;
            if (entries != null) {
                for (int i = 0; i < 256; i++) {
                    this.rgb[i] = entries[i] & 0xFFFFFF;
                    // Ascending, so a colour held twice keeps the lowest index.
                    this.exact.putIfAbsent(this.rgb[i], i);
                }
            }
        }

        /**
         * A fixed palette.
         *
         * @param source A description.
         * @param entries 256 entries, 0xRRGGBB.
         * @return The palette.
         */
        static Palette of(String source, int[] entries) {
            if (entries == null || entries.length != 256) {
                throw new IllegalArgumentException("256 entries expected");
            }
            return new Palette(source, entries, true);
        }

        /**
         * A palette handed out in order of first sight.
         *
         * @return The palette.
         */
        static Palette adaptive() {
            return new Palette("adaptive (no " + PALETTE_PROPERTY + ")", null,
                               false);
        }

        /**
         * The palette named by the property, else adaptive.
         *
         * @param path The property value, or null.
         * @return The palette.
         */
        static Palette fromProperty(String path) {
            if (path == null || path.isBlank()) return adaptive();
            try {
                return load(new File(path));
            } catch (IOException | RuntimeException e) {
                logger.log(Level.WARNING, "Classic recorder: palette " + path
                    + " unreadable, indices handed out as seen", e);
                return adaptive();
            }
        }

        /**
         * Load a palette: a PNG with an indexed colour model (its PLTE),
         * else a raw file of 768 bytes (RGB, 8 bits each).
         *
         * @param f The file.
         * @return The palette.
         * @exception IOException if it cannot be read.
         */
        static Palette load(File f) throws IOException {
            final byte[] b = Files.readAllBytes(f.toPath());
            if (b.length == 768) {
                final int[] e = new int[256];
                for (int i = 0; i < 256; i++) {
                    e[i] = ((b[3 * i] & 0xFF) << 16) | ((b[3 * i + 1] & 0xFF) << 8)
                        | (b[3 * i + 2] & 0xFF);
                }
                return of(f.getPath(), e);
            }
            final BufferedImage img = ImageIO.read(f);
            if (img == null || !(img.getColorModel() instanceof IndexColorModel)) {
                throw new IOException(f + ": neither an indexed PNG nor 768 bytes");
            }
            final IndexColorModel cm = (IndexColorModel)img.getColorModel();
            final int[] e = new int[256];
            for (int i = 0; i < Math.min(256, cm.getMapSize()); i++) {
                e[i] = cm.getRGB(i) & 0xFFFFFF;
            }
            return of(f.getPath(), e);
        }

        /**
         * The index for a colour (see the class comment of the recorder).
         *
         * @param colour 0xRRGGBB (higher bits ignored).
         * @return The index, 0..255.
         */
        int index(int colour) {
            final int c = colour & 0xFFFFFF;
            if (c == this.lastRgb) {
                if (this.lastMiss) miss(c);
                return this.lastIndex;
            }
            Integer i = this.exact.get(c);
            boolean m = false;
            if (i == null) {
                if (!this.fixed && this.used < 256) {
                    i = this.used++;
                    this.rgb[i] = c;
                    this.exact.put(c, i);
                } else {
                    i = this.nearest.get(c);
                    if (i == null) {
                        i = nearestIndex(c);
                        this.nearest.put(c, i);
                    }
                    m = true;
                    miss(c);
                }
            }
            this.lastRgb = c;
            this.lastIndex = i;
            this.lastMiss = m;
            return i;
        }

        private void miss(int c) {
            this.missPixels++;
            this.misses.merge(c, 1L, Long::sum);
        }

        /** The nearest entry (squared RGB distance; ties: the lowest index). */
        private int nearestIndex(int c) {
            final int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            final int n = this.fixed ? 256 : this.used;
            int best = 0;
            long bd = Long.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                final int e = this.rgb[i];
                final long dr = ((e >> 16) & 0xFF) - r, dg = ((e >> 8) & 0xFF) - g,
                    db = (e & 0xFF) - b;
                final long d = dr * dr + dg * dg + db * db;
                if (d < bd) {
                    bd = d;
                    best = i;
                }
            }
            return best;
        }

        /**
         * The PLTE chunk data.
         *
         * @return 768 bytes (a fresh array).
         */
        byte[] plte() {
            final byte[] p = new byte[768];
            for (int i = 0; i < 256; i++) {
                p[3 * i] = (byte)(this.rgb[i] >> 16);
                p[3 * i + 1] = (byte)(this.rgb[i] >> 8);
                p[3 * i + 2] = (byte)this.rgb[i];
            }
            return p;
        }

        /**
         * The colours that were not in the palette, most pixels first.
         *
         * @return Up to 12 "#rrggbb->index xN" entries.
         */
        String missedColours() {
            final List<Map.Entry<Integer, Long>> l = new ArrayList<>(this.misses.entrySet());
            l.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
            final StringBuilder sb = new StringBuilder();
            sb.append(l.size()).append(" distinct");
            for (int i = 0; i < Math.min(12, l.size()); i++) {
                final int c = l.get(i).getKey();
                sb.append(String.format(Locale.ROOT, " #%06x->%d x%d", c,
                    this.nearest.getOrDefault(c, -1), l.get(i).getValue()));
            }
            return sb.toString();
        }
    }
}
