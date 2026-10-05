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

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import net.sf.freecol.common.io.FreeColDirectories;
import net.sf.freecol.common.io.FreeColModFile;


/**
 * Plain-file access to the git-ignored {@code classic_original} pack,
 * <b>without</b> FreeCol's {@code ResourceManager}.
 *
 * <p>Why a second way into the pack: FreeCol only makes a mod's resources
 * available after it has mapped every key of every registered data file and
 * (before the GUI shows) preloaded them -- 12-15 s for this pack's 1557
 * images.  The original's new-game screens (difficulty, nation, name, nation
 * pages, audience; see {@link ClassicNewWorldScreens}) need only a handful of
 * PIK pictures, sprites, fonts and the original text files, so they read
 * them here straight from disk: the screens then never wait for that
 * pipeline, never depend on its timing, and render identically in the
 * headless preview harness, which has no client at all
 * ({@link #forDirectory}).  The text files ({@code text/*.TXT}) and the
 * sprite anchors ({@code ss-anchors.properties}) are not FreeCol resources
 * in the first place.
 *
 * <p>The pack layout is the converter's ({@code ClassicAssetConverter}):
 * {@code resources.properties} maps {@code image.classic_original.*} keys
 * onto PNG paths relative to the pack, font metrics are quoted string values,
 * {@code text/} holds verbatim copies of the original's GAME/NAMES/LABELS.TXT,
 * and {@code ss-anchors.properties} the sprite-header anchors.
 *
 * <p>Thread-safe (all caches synchronized): the title screen prefetches the
 * new-game assets on a daemon thread while the EDT paints.
 */
final class ClassicPackFiles {

    private static final Logger logger = Logger.getLogger(ClassicPackFiles.class.getName());

    /** The pack's mod id and directory name. */
    static final String PACK_ID = "classic_original";

    /** The runtime pack: empty = probed and absent. */
    private static Optional<ClassicPackFiles> runtime = null;

    /** The pack directory. */
    private final File dir;

    /** {@code resources.properties}, quotes stripped from string values. */
    private final Map<String, String> props;

    /** {@code ss-anchors.properties}: frame stem -> {ax, ay}; null = absent. */
    private Map<String, int[]> anchors = null;

    /** Loaded images; missing ones stored as empty so they are probed once. */
    private final Map<String, Optional<BufferedImage>> images = new HashMap<>();

    /** Loaded fonts, same convention. */
    private final Map<String, Optional<ClassicFont>> fonts = new HashMap<>();


    private ClassicPackFiles(File dir) {
        this.dir = dir;
        this.props = readProperties(new File(dir, "resources.properties"), true);
    }

    /**
     * The pack of the running game: the registered mod
     * {@code classic_original} when it is a directory, else
     * {@code <data>/mods/classic_original}.  Probed once.
     *
     * @return The pack, or null when there is none (never converted).
     */
    static synchronized ClassicPackFiles runtime() {
        if (runtime == null) {
            final File d = modDirectory(PACK_ID);
            runtime = Optional.ofNullable((d != null
                    && new File(d, "resources.properties").isFile())
                ? new ClassicPackFiles(d) : null);
        }
        return runtime.orElse(null);
    }

    /**
     * Where a classic pack lives: the registered mod {@code modId} when it
     * is a directory, else {@code <data>/mods/<modId>}.  Also used for the
     * soundtrack pack {@code classic_music} before any client exists
     * ({@link ClassicEarlyMusic}): {@code FreeColModFile.loadMods} runs in
     * {@code FreeCol.main}, long before the window opens.
     *
     * @param modId The mod id, e.g. {@value #PACK_ID}.
     * @return The directory (it may not exist), or null when not even the
     *     mods directory is known.
     */
    static File modDirectory(String modId) {
        try {
            final FreeColModFile mod = FreeColModFile.getFreeColModFile(modId);
            if (mod != null && new File(mod.getPath()).isDirectory()) {
                return new File(mod.getPath());
            }
        } catch (RuntimeException e) {
            logger.log(Level.FINE, "No registered " + modId, e);
        }
        try {
            return new File(FreeColDirectories.getStandardModsDirectory(), modId);
        } catch (RuntimeException e) {
            logger.log(Level.FINE, "No mods directory", e);
            return null;
        }
    }

    /**
     * A pack in an explicit directory (preview harness, tests).
     *
     * @param dir The pack directory.
     * @return The pack (its files may still be missing).
     */
    static ClassicPackFiles forDirectory(File dir) {
        return new ClassicPackFiles(dir);
    }

    /** @return The pack directory. */
    File directory() {
        return this.dir;
    }


    // Access

    /**
     * An image by pack key, read with ImageIO from the mapped file.
     *
     * @param key A key such as {@code image.classic_original.pik.NATIONS.PIK}.
     * @return The image (shared, do not modify), or null when missing.
     */
    BufferedImage image(String key) {
        synchronized (this.images) {
            Optional<BufferedImage> o = this.images.get(key);
            if (o == null) {
                BufferedImage img = null;
                final String rel = this.props.get(key);
                if (rel != null && !rel.startsWith("resource:")) {
                    final File f = new File(this.dir, rel);
                    if (f.isFile()) {
                        try {
                            img = ImageIO.read(f);
                        } catch (IOException e) {
                            logger.log(Level.WARNING, "Unreadable " + f, e);
                        }
                    }
                }
                o = Optional.ofNullable(img);
                this.images.put(key, o);
            }
            return o.orElse(null);
        }
    }

    /** @return A string value of {@code resources.properties}, or null. */
    String string(String key) {
        return this.props.get(key);
    }

    /**
     * A bitmap font from its atlas and metrics, the same pair
     * {@link ClassicFont#get} reads through the resource manager.
     *
     * @param ffName E.g. {@link ClassicFont#INTRO}.
     * @return The font, or null when the pack lacks it.
     */
    ClassicFont font(String ffName) {
        synchronized (this.fonts) {
            Optional<ClassicFont> o = this.fonts.get(ffName);
            if (o == null) {
                ClassicFont f = null;
                final String key = ClassicFont.KEY_PREFIX + ffName;
                final BufferedImage atlas = image(key);
                final String metrics = string(key + ".properties");
                if (atlas != null && metrics != null) {
                    try {
                        f = ClassicFont.fromAtlas(atlas, metrics);
                    } catch (IllegalArgumentException e) {
                        logger.log(Level.WARNING, "Bad bitmap font " + ffName, e);
                    }
                }
                o = Optional.ofNullable(f);
                this.fonts.put(ffName, o);
            }
            return o.orElse(null);
        }
    }

    /**
     * Where the original draws an SS frame: its header anchor (bottom
     * centre) turned into a top-left with the frame's size,
     * {@code (ax - w/2, ay - h + 1)}.
     *
     * @param ssFrame A frame stem such as {@code KING1.SS.000}.
     * @return The top-left, or null when the anchor or frame is unknown.
     */
    Point spriteTopLeft(String ssFrame) {
        final int[] a;
        synchronized (this) {
            if (this.anchors == null) {
                final Map<String, int[]> m = new HashMap<>();
                for (Map.Entry<String, String> e : readProperties(
                         new File(this.dir, "ss-anchors.properties"), false).entrySet()) {
                    final String[] p = e.getValue().split(",");
                    if (p.length != 2) continue;
                    try {
                        m.put(e.getKey(), new int[] { Integer.parseInt(p[0].trim()),
                                                      Integer.parseInt(p[1].trim()) });
                    } catch (NumberFormatException nfe) {
                        // skip the line
                    }
                }
                this.anchors = m;
            }
            a = this.anchors.get(ssFrame);
        }
        final BufferedImage img = image(ssKey(ssFrame));
        if (a == null || img == null) return null;
        return new Point(a[0] - img.getWidth() / 2, a[1] - img.getHeight() + 1);
    }

    /**
     * An original text file copied by the converter.
     *
     * @param name E.g. {@code GAME.TXT}.
     * @return The file, or null when it is not there.
     */
    File textFile(String name) {
        final File f = new File(new File(this.dir, "text"), name);
        return f.isFile() ? f : null;
    }

    /**
     * Whether the pack was converted with the new-game material: the three
     * text files and the sprite anchors.  Packs built before they existed
     * lack them and must be regenerated ({@code ant classic-assets}).
     */
    boolean hasNewWorldChain() {
        return textFile("GAME.TXT") != null && textFile("NAMES.TXT") != null
            && textFile("LABELS.TXT") != null
            && new File(this.dir, "ss-anchors.properties").isFile();
    }

    /**
     * The sprite stems the original opening draws: the ship, the three
     * credit-scroll series and the ten animation series of OPENING.TXT's
     * {@code @OPENING} table ({@link ClassicOpeningScript#SERIES_STEMS}).
     */
    static final String[] OPENING_STEMS = {
        "OPENSHIP", "OPENCRD1", "OPENCRD2", "OPENCRD3",
        "OPENWND1", "OPENSUN", "OPENMON1", "OPENWND2", "OPENMON2",
        "OPENMON3", "OPENFISH", "OPENGUY", "OPENLOGO", "OPENBONK"
    };

    /**
     * Whether the pack holds the original opening's material: OPENING.TXT
     * and PATH.DAT (copied by the converter since the intro step), the
     * three opening pictures, frame 000 of every opening sprite series and
     * the sprite anchors.  Cheap (file probes only, nothing is loaded).
     * Without it the intro shows the emblem and then the title
     * ({@link ClassicIntroPlayer}).
     *
     * @return True when the chart credits and title build-up can be shown.
     */
    boolean hasOpening() {
        if (textFile("OPENING.TXT") == null || textFile("PATH.DAT") == null
            || !new File(this.dir, "ss-anchors.properties").isFile()) {
            return false;
        }
        for (String pik : new String[] { "OPENBORD.PIK", "OPENING.PIK",
                                         "OPENMENU.PIK" }) {
            if (!isFileKey(pikKey(pik))) return false;
        }
        for (String stem : OPENING_STEMS) {
            if (!isFileKey(ssKey(stem + ".SS.000"))) return false;
        }
        return true;
    }

    /** Whether a pack key maps onto an existing file (nothing is loaded). */
    private boolean isFileKey(String key) {
        final String rel = this.props.get(key);
        return rel != null && !rel.startsWith("resource:")
            && new File(this.dir, rel).isFile();
    }


    // Keys

    /** @return The pack key of a PIK picture, e.g. {@code NATIONS.PIK}. */
    static String pikKey(String pik) {
        return "image." + PACK_ID + ".pik." + pik;
    }

    /** @return The pack key of an SS frame, e.g. {@code KING1.SS.000}. */
    static String ssKey(String frame) {
        return "image." + PACK_ID + ".ss." + frame;
    }


    // Parsing

    /**
     * Read a {@code key=value} file: '#' lines and lines without '=' are
     * skipped; with {@code unquote}, a value in double quotes loses them (as
     * {@code StringResource} values do).  A missing file gives an empty map.
     * Package-private: {@link ClassicEarlyMusic} reads the soundtrack pack's
     * title line with it before the resource manager knows that pack.
     */
    static Map<String, String> readProperties(File f, boolean unquote) {
        final Map<String, String> m = new HashMap<>();
        if (!f.isFile()) return m;
        try {
            for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                final String l = line.trim();
                if (l.isEmpty() || l.startsWith("#")) continue;
                final int eq = l.indexOf('=');
                if (eq <= 0) continue;
                String v = l.substring(eq + 1).trim();
                if (unquote && v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
                    v = v.substring(1, v.length() - 1);
                }
                m.put(l.substring(0, eq).trim(), v);
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Unreadable " + f, e);
        }
        return m;
    }
}
