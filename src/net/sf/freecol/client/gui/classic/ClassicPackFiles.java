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
 * {@code text/} holds verbatim copies of the original's GAME/NAMES/LABELS.TXT
 * and the other text files ({@code ClassicAssetConverter.TEXT_FILES}),
 * and {@code ss-anchors.properties} the sprite-header anchors.
 *
 * <p>The index pipeline (M1c design 10 §4, W6e): {@code ssidx/<NAME>.idx}
 * holds each SS file's frames as palette indices ({@link #indexSheet}),
 * {@code palette/VICEROY.rgb} the game palette ({@link #gamePalette}) and
 * {@code data/CYCLE.DAT} the original's colour cycle ({@link #cycleSpec});
 * {@link #terrainSpriteFor} reads which TERRAIN.SS frame a tile type uses
 * from the alias lines, so {@code aliases.properties} stays the one place
 * that says it.  Packs converted before W6e lack them: {@link #indexStatus}
 * then says "fallback" and logs one warning.
 *
 * <p>Thread-safe (all caches synchronized): the title screen prefetches the
 * new-game assets on a daemon thread while the EDT paints.
 */
final class ClassicPackFiles {

    private static final Logger logger = Logger.getLogger(ClassicPackFiles.class.getName());

    /** The pack's mod id and directory name. */
    static final String PACK_ID = "classic_original";

    /** The directory of the index sheets (the converter's {@code INDEX_DIR}). */
    static final String INDEX_DIR = "ssidx";

    /** The suffix of an index sheet, as in {@code ssidx/TERRAIN.SS.idx}. */
    static final String INDEX_SUFFIX = ".idx";

    /** The game palette, 768 bytes of 8-bit R G B (the converter's {@code PALETTE_FILE}). */
    static final String PALETTE_FILE = "palette/VICEROY.rgb";

    /** The original's colour-cycling table (the converter's {@code CYCLE_FILE}). */
    static final String CYCLE_FILE = "data/CYCLE.DAT";

    /** The map's base sprites and its overlays, fringes, masks and coast pieces. */
    static final String TERRAIN_SS = "TERRAIN.SS", PHYS0_SS = "PHYS0.SS";

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

    /** Loaded index sheets by SS name, same convention. */
    private final Map<String, Optional<ClassicIndexSheet>> sheets = new HashMap<>();

    /** The game palette (0xRRGGBB); null = not read yet, empty = absent. */
    private Optional<int[]> palette = null;

    /** The colour cycle; null = not read yet. */
    private ClassicGamePalette.Cycle cycle = null;

    /** Whether {@link #indexStatus} has warned about a missing pipeline. */
    private boolean indexWarned = false;


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


    // The index pipeline (W6e)

    /**
     * An SS file's frames as palette indices, from
     * {@code ssidx/<ss>.idx}.  Read once; a missing or broken file is
     * remembered as missing (a broken one is logged).
     *
     * @param ss The SS file name, e.g. {@link #TERRAIN_SS}.
     * @return The sheet (shared), or null when the pack has none.
     */
    ClassicIndexSheet indexSheet(String ss) {
        synchronized (this.sheets) {
            Optional<ClassicIndexSheet> o = this.sheets.get(ss);
            if (o == null) {
                ClassicIndexSheet sheet = null;
                final File f = new File(new File(this.dir, INDEX_DIR), ss + INDEX_SUFFIX);
                if (f.isFile()) {
                    try {
                        sheet = ClassicIndexSheet.parse(ss, Files.readAllBytes(f.toPath()));
                    } catch (IOException | IllegalArgumentException e) {
                        logger.log(Level.WARNING, "Unreadable index sheet " + f, e);
                    }
                }
                o = Optional.ofNullable(sheet);
                this.sheets.put(ss, o);
            }
            return o.orElse(null);
        }
    }

    /**
     * The game palette {@code VICEROY.PAL} as the converter wrote it
     * ({@link #PALETTE_FILE}: 768 bytes, already 8-bit).  Read once.
     *
     * @return A copy of the 256 entries, 0xRRGGBB, or null when the file
     *     is missing or not 768 bytes long.
     */
    synchronized int[] gamePalette() {
        if (this.palette == null) {
            int[] e = null;
            final File f = new File(this.dir, PALETTE_FILE);
            if (f.isFile()) {
                try {
                    final byte[] b = Files.readAllBytes(f.toPath());
                    if (b.length == 256 * 3) {
                        e = new int[256];
                        for (int i = 0; i < 256; i++) {
                            e[i] = ((b[i * 3] & 0xFF) << 16)
                                | ((b[i * 3 + 1] & 0xFF) << 8) | (b[i * 3 + 2] & 0xFF);
                        }
                    } else {
                        logger.warning("Game palette " + f + ": " + b.length
                            + " bytes, expected 768");
                    }
                } catch (IOException ex) {
                    logger.log(Level.WARNING, "Unreadable " + f, ex);
                }
            }
            this.palette = Optional.ofNullable(e);
        }
        return this.palette.map(int[]::clone).orElse(null);
    }

    /**
     * The original's colour cycle from {@link #CYCLE_FILE}: a 16-bit
     * cycle count, then 4 bytes per cycle -- the number of entries, a byte
     * not interpreted here (0x3D), the first entry and the ticks per step
     * ({@code 01 00 | 08 3D 78 23}: 8 entries from 120, 35 ticks).  Only
     * the first cycle is used; the original has one.  Read once.
     *
     * @return The cycle; {@link ClassicGamePalette.Cycle#DEFAULT} (the same
     *     values) when the file is missing or unusable (the latter logged).
     */
    synchronized ClassicGamePalette.Cycle cycleSpec() {
        if (this.cycle == null) {
            ClassicGamePalette.Cycle c = ClassicGamePalette.Cycle.DEFAULT;
            final File f = new File(this.dir, CYCLE_FILE);
            if (f.isFile()) {
                try {
                    c = parseCycle(Files.readAllBytes(f.toPath()));
                } catch (IOException | IllegalArgumentException e) {
                    logger.log(Level.WARNING, "Unusable " + f + ", default cycle "
                        + c, e);
                }
            }
            this.cycle = c;
        }
        return this.cycle;
    }

    /**
     * Read the first cycle of a {@code CYCLE.DAT}.
     *
     * @param b The file contents.
     * @return The cycle.
     * @exception IllegalArgumentException if it holds no usable cycle.
     */
    static ClassicGamePalette.Cycle parseCycle(byte[] b) {
        if (b.length < 6 || ((b[0] & 0xFF) | ((b[1] & 0xFF) << 8)) < 1) {
            throw new IllegalArgumentException("no cycle in " + b.length + " bytes");
        }
        return new ClassicGamePalette.Cycle(b[2] & 0xFF, b[4] & 0xFF, b[5] & 0xFF);
    }

    /**
     * The TERRAIN.SS frame a tile type is drawn with, read from the pack's
     * alias line {@code image.tile.<id>.center=resource:image.classic_original.ss.TERRAIN.SS.NNN}
     * (appended from {@code tools/classic_assets/aliases.properties}).
     *
     * @param tileTypeId E.g. {@code model.tile.plains}.
     * @return NNN, or -1 when the type has no such alias.
     */
    int terrainSpriteFor(String tileTypeId) {
        final String v = this.props.get("image.tile." + tileTypeId + ".center");
        final String prefix = "resource:" + ssKey(TERRAIN_SS + ".");
        if (v == null || !v.startsWith(prefix)) return -1;
        try {
            return Integer.parseInt(v.substring(prefix.length()));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Whether the map can be composed from indices, as one line for the
     * log and the recorder ({@code terrain} event and summary line):
     * {@code index TERRAIN.SS=12 PHYS0.SS=154 palette=VICEROY cycle=8@120/35},
     * or {@code fallback <what is missing>}.  Packs converted before the
     * index pipeline lack it; the first fallback logs one warning.
     *
     * @return The status line.
     */
    String indexStatus() {
        final ClassicIndexSheet t = indexSheet(TERRAIN_SS);
        final ClassicIndexSheet p = indexSheet(PHYS0_SS);
        final String missing = (t == null) ? INDEX_DIR + "/" + TERRAIN_SS + INDEX_SUFFIX
            : (p == null) ? INDEX_DIR + "/" + PHYS0_SS + INDEX_SUFFIX
            : (gamePalette() == null) ? PALETTE_FILE
            : null;
        if (missing == null) {
            return "index " + TERRAIN_SS + "=" + t.size() + " " + PHYS0_SS + "="
                + p.size() + " palette=VICEROY cycle=" + cycleSpec()
                + (new File(this.dir, CYCLE_FILE).isFile() ? "" : " (default)");
        }
        synchronized (this) {
            if (!this.indexWarned) {
                this.indexWarned = true;
                logger.warning("The classic pack " + this.dir + " has no " + missing
                    + ": the map falls back to the RGBA tiles without palette"
                    + " cycling.  Re-run ant classic-assets to convert it again.");
            }
        }
        return "fallback no " + missing;
    }

    /**
     * {@link #indexStatus} of a pack that may be missing.
     *
     * @param pack The pack, or null.
     * @return The status line ({@code fallback no pack} for null).
     */
    static String indexStatus(ClassicPackFiles pack) {
        return (pack == null) ? "fallback no pack" : pack.indexStatus();
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
