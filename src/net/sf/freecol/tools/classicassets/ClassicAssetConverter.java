/**
 *  Copyright (C) 2002-2024   The FreeCol Team
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

package net.sf.freecol.tools.classicassets;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.stream.Stream;

import javax.imageio.ImageIO;


/**
 * One-time converter: turns the user's OWN legally-owned original
 * <i>Sid Meier's Colonization</i> (1994) art into a local, git-ignored
 * FreeCol mod pack ({@code data/mods/classic_original/}) for the classic UI.
 * Driven by the Ant {@code classic-assets} target; see
 * {@code tools/classic_assets/README.md} and CLASSIC_UI_PLAN.md.
 *
 * This ships no copyrighted content: it reads the user's install directory
 * and writes PNGs plus a {@code resources.properties} exposing every frame
 * under a stable {@code image.classic_original.*} key namespace.  The five
 * bitmap fonts ({@code *.FF}) are converted as well: one 2-bit palette atlas
 * per font plus a quoted metrics string (see {@link FfDecoder}).  The
 * committed {@code aliases.properties} (plan item A2) mapping real FreeCol
 * keys onto those is appended if supplied, so re-running never clobbers it.
 *
 * <p>Beside the PNGs, every SS file is also kept as palette indices
 * ({@code ssidx/<NAME>.idx}, {@link #encodeIndexSheet}), together with the
 * game palette ({@code palette/VICEROY.rgb}) and the palette-cycling table
 * ({@code data/CYCLE.DAT}): the classic map is composed from the original
 * indices and drawn through the palette at the current cycling phase (M1c
 * design 10 §4, item W6e).
 *
 * Usage:
 * <pre>
 *   ClassicAssetConverter --install &lt;colonize-dir&gt; --out &lt;pack-dir&gt; [--aliases &lt;file&gt;]
 * </pre>
 */
public final class ClassicAssetConverter {

    private static final String KEY_PREFIX = "image.classic_original";

    /** The pack directory of the index sheets, one per SS file. */
    public static final String INDEX_DIR = "ssidx";

    /** The file suffix of an index sheet: {@code ssidx/TERRAIN.SS.idx}. */
    public static final String INDEX_SUFFIX = ".idx";

    /** The index sheet's magic, its first four bytes. */
    public static final String INDEX_MAGIC = "CSSI";

    /** The index sheet format version. */
    public static final int INDEX_VERSION = 1;

    /**
     * The game palette in the pack: 768 bytes, R G B per entry, already
     * expanded to 8 bits as DOSBox does ({@link Palette}).
     */
    public static final String PALETTE_FILE = "palette/VICEROY.rgb";

    /** The palette-cycling table in the pack, copied unchanged. */
    public static final String CYCLE_FILE = "data/CYCLE.DAT";

    private ClassicAssetConverter() {}

    public static void main(String[] args) throws IOException {
        Path install = null;
        Path out = null;
        Path aliases = null;
        for (int i = 0; i + 1 < args.length; i += 2) {
            switch (args[i]) {
                case "--install": install = Path.of(args[i + 1]); break;
                case "--out":     out = Path.of(args[i + 1]); break;
                case "--aliases": aliases = Path.of(args[i + 1]); break;
                default:
                    throw new IllegalArgumentException("unknown option: " + args[i]);
            }
        }
        if (install == null || out == null) {
            System.err.println("usage: ClassicAssetConverter --install <colonize-dir> "
                + "--out <pack-dir> [--aliases <file>]");
            System.exit(2);
            return;
        }

        // Master gameplay palette, used to decode palette-less PIK screens.
        Palette viceroy = Palette.readViceroy(Files.readAllBytes(
            findIgnoreCase(install, "VICEROY.PAL")));

        Path imgRoot = out.resolve("resources").resolve("images");
        Path pikDir = imgRoot.resolve("pik");
        Path ssDir = imgRoot.resolve("ss");
        Path ffDir = imgRoot.resolve("ff");
        Path idxDir = out.resolve(INDEX_DIR);

        // The pack is a build artifact: regenerate cleanly.
        deleteRecursively(out);
        Files.createDirectories(pikDir);
        Files.createDirectories(ssDir);
        Files.createDirectories(ffDir);
        Files.createDirectories(idxDir);

        // key -> pack-relative path, sorted for stable output.
        TreeMap<String, String> entries = new TreeMap<>();

        int pikCount = 0;
        for (Path pik : listByExtension(install, ".pik")) {
            String name = pik.getFileName().toString();       // e.g. COLONY.PIK
            BufferedImage img = PikDecoder.decode(Files.readAllBytes(pik), viceroy);
            String png = name + ".png";
            ImageIO.write(img, "png", pikDir.resolve(png).toFile());
            entries.put(KEY_PREFIX + ".pik." + name, "resources/images/pik/" + png);
            pikCount++;
        }

        int ssFrames = 0;
        int ssSheets = 0;
        // stem -> "ax,ay": the frames' screen anchors (see SsDecoder.anchors).
        TreeMap<String, String> anchors = new TreeMap<>();
        for (Path ss : listByExtension(install, ".ss")) {
            String name = ss.getFileName().toString();        // e.g. TERRAIN1.SS
            byte[] bytes = Files.readAllBytes(ss);
            // One decode to indices: the index sheet, and through the
            // file's own palette the PNGs (SsDecoder.decode).
            List<SsDecoder.IndexedFrame> frames = SsDecoder.decodeIndexed(bytes);
            Palette ssPalette = SsDecoder.palette(bytes);
            List<int[]> frameAnchors = SsDecoder.anchors(bytes);
            Files.write(idxDir.resolve(name + INDEX_SUFFIX), encodeIndexSheet(frames));
            ssSheets++;
            for (int i = 0; i < frames.size(); i++) {
                String stem = String.format("%s.%03d", name, i);
                String png = stem + ".png";
                ImageIO.write(SsDecoder.toImage(frames.get(i), ssPalette), "png",
                              ssDir.resolve(png).toFile());
                entries.put(KEY_PREFIX + ".ss." + stem, "resources/images/ss/" + png);
                if (i < frameAnchors.size()) {
                    anchors.put(stem, frameAnchors.get(i)[0] + ","
                        + frameAnchors.get(i)[1]);
                }
                ssFrames++;
            }
        }

        // Bitmap fonts: one atlas PNG per font plus its metrics as a quoted
        // value.  A value ending in a double quote becomes a StringResource
        // (ResourceFactory.java:79-80; precedent image.skin.MiniMap.properties
        // in data/base/resources.properties).  One atlas per font avoids ~480
        // per-glyph files -- each costs FreeColDataFile a directory listing at
        // startup -- and the 266 zero-width glyph slots no PNG could hold.
        int ffCount = 0;
        for (Path ff : listByExtension(install, ".ff")) {
            String name = ff.getFileName().toString();        // e.g. FONTTINY.FF
            FfDecoder.Font font = FfDecoder.decode(Files.readAllBytes(ff));
            String png = name + ".png";
            ImageIO.write(FfDecoder.toAtlas(font), "png", ffDir.resolve(png).toFile());
            entries.put(KEY_PREFIX + ".ff." + name, "resources/images/ff/" + png);
            entries.put(KEY_PREFIX + ".ff." + name + ".properties",
                        "\"" + FfDecoder.metrics(font) + "\"");
            ffCount++;
        }

        // The original's own texts (see copyTexts) and the SS anchors.
        int textCount = copyTexts(install, out.resolve("text"));
        writeAnchors(out.resolve("ss-anchors.properties"), anchors);

        // The game palette and the cycling table, for the index sheets.
        Path palFile = out.resolve(PALETTE_FILE);
        Files.createDirectories(palFile.getParent());
        Files.write(palFile, rgbBytes(viceroy));
        boolean cycle = copyCycleTable(install, out.resolve(CYCLE_FILE));

        writeResourceProperties(out.resolve("resources.properties"), entries, aliases);
        Files.writeString(out.resolve("mod.xml"),
            "<mod id=\"classic_original\"/>\n", StandardCharsets.UTF_8);
        Files.writeString(out.resolve("FreeColMessages.properties"),
            "mod.classic_original.name=Classic Colonization art (local)\n"
            + "mod.classic_original.shortDescription="
            + "Original Sid Meier's Colonization graphics, extracted locally "
            + "from your own game install.\n",
            StandardCharsets.UTF_8);

        System.out.printf("classic_original pack: %d pik + %d ss images + %d fonts"
            + " + %d texts + %d index sheets + palette%s -> %s%n", pikCount,
            ssFrames, ffCount, textCount, ssSheets, (cycle) ? " + cycle table" : "",
            out);
    }

    /**
     * An SS file's frames as an index sheet, the file the classic UI reads
     * back ({@code ClassicIndexSheet}).  Little-endian:
     * <pre>
     *   "CSSI"  u8 version (1)  u16 frames
     *   per frame:  u16 w  u16 h  w*h index bytes, row by row
     * </pre>
     * {@link SsDecoder#TRANSPARENT_INDEX} (0xFD) marks a transparent pixel.
     *
     * @param frames The frames ({@link SsDecoder#decodeIndexed}).
     * @return The file contents.
     * @exception IllegalArgumentException if a count or size exceeds 16 bits.
     */
    public static byte[] encodeIndexSheet(List<SsDecoder.IndexedFrame> frames) {
        int size = INDEX_MAGIC.length() + 1 + 2;
        for (SsDecoder.IndexedFrame f : frames) size += 4 + f.idx.length;
        if (frames.size() > 0xFFFF) {
            throw new IllegalArgumentException("too many frames: " + frames.size());
        }
        final byte[] out = new byte[size];
        int p = 0;
        for (byte b : INDEX_MAGIC.getBytes(StandardCharsets.US_ASCII)) out[p++] = b;
        out[p++] = (byte) INDEX_VERSION;
        p = putU16(out, p, frames.size());
        for (SsDecoder.IndexedFrame f : frames) {
            if (f.w > 0xFFFF || f.h > 0xFFFF || f.idx.length != f.w * f.h) {
                throw new IllegalArgumentException("bad frame " + f.w + "x" + f.h);
            }
            p = putU16(out, p, f.w);
            p = putU16(out, p, f.h);
            System.arraycopy(f.idx, 0, out, p, f.idx.length);
            p += f.idx.length;
        }
        return out;
    }

    private static int putU16(byte[] b, int p, int v) {
        b[p] = (byte) (v & 0xFF);
        b[p + 1] = (byte) ((v >> 8) & 0xFF);
        return p + 2;
    }

    /**
     * A palette as {@link #PALETTE_FILE} holds it: R, G, B of each of the
     * 256 entries, 8 bits each.
     *
     * @param pal The palette (VICEROY.PAL, read with {@link Palette#readViceroy}).
     * @return 768 bytes.
     */
    static byte[] rgbBytes(Palette pal) {
        final byte[] out = new byte[256 * 3];
        for (int i = 0; i < 256; i++) {
            out[i * 3] = (byte) (pal.argb[i] >> 16);
            out[i * 3 + 1] = (byte) (pal.argb[i] >> 8);
            out[i * 3 + 2] = (byte) pal.argb[i];
        }
        return out;
    }

    /**
     * Copy the original's palette-cycling table {@code CYCLE.DAT} byte for
     * byte (it starts {@code 01 00 | 08 3D 78 23}: one cycle of 8 colours
     * from 0x78 = 120, a step every 0x23 = 35 ticks).  Missing is only a
     * warning: the client then uses the same values as defaults.
     *
     * @param install The original's install directory.
     * @param dst The pack's {@link #CYCLE_FILE}.
     * @return Whether the file was copied.
     * @exception IOException if it cannot be copied.
     */
    static boolean copyCycleTable(Path install, Path dst) throws IOException {
        final Path src;
        try {
            src = findIgnoreCase(install, "CYCLE.DAT");
        } catch (IOException e) {
            System.err.println("WARNING: " + e.getMessage()
                + " -- the classic UI cycles the water with its defaults.");
            return false;
        }
        Files.createDirectories(dst.getParent());
        Files.copy(src, dst);
        return true;
    }

    /**
     * The original's text files the classic UI reads at runtime (the
     * new-game screens: picker labels, prompts, nation pages, the audience
     * scroll, the in-game panel's labels) and {@code MENU.TXT}, the in-game
     * menu bar's titles and items ({@code ClassicMenuBar}).  See
     * {@code ClassicText}.
     *
     * <p>{@code OPENING.TXT} and {@code PATH.DAT} drive the original
     * opening (the sea-chart credits and the title build-up,
     * {@code ClassicIntro} / {@code ClassicOpeningScript}): the first is the
     * frame schedule of the credit scrolls and chart animations, the second
     * the ship's 701 path points.  The credits themselves are the OPENCRD
     * sprites, already converted with the other SS frames, so no name is
     * ever transcribed anywhere; these two files only say WHEN and WHERE.
     * Like every other copy they stay in the git-ignored pack.
     *
     * <p>{@code PEDIA.TXT} is the Colonizopedia's text (the founding
     * fathers' pages, {@code @FATHERn}) and {@code COLONY.TXT} the colony
     * names each nation offers in turn when a colony is founded; both are
     * read by later screens (plan D8b and D4).  {@code WOODCUT.TXT} holds
     * the woodcuts' titles (the ribbon texts), for the woodcut screens
     * (plan W9).
     */
    static final String[] TEXT_FILES = { "GAME.TXT", "NAMES.TXT", "LABELS.TXT",
                                         "MENU.TXT", "OPENING.TXT", "PATH.DAT",
                                         "PEDIA.TXT", "COLONY.TXT", "WOODCUT.TXT" };

    /**
     * Copy {@link #TEXT_FILES} byte for byte into {@code <pack>/text/}, under
     * their upper-case names.
     *
     * <p>Why copies, and why unchanged: the original texts are the source of
     * truth for every string the new-game screens draw (down to a lowercase
     * 'ö' in one label that only one of two files has), and they are
     * copyrighted -- so they may live only in this git-ignored pack
     * ({@code .gitignore}: {@code /data/mods/classic_original/}), never in
     * tracked source or {@code .properties}.  Copying them unchanged keeps the
     * format knowledge (the game's own umlaut codes, the markup, the section
     * layout) in exactly one place, the runtime parser
     * {@code ClassicText}, instead of splitting it between converter and
     * client.  The runtime reads them as plain files, so they are available
     * before FreeCol's resource pipeline registers the pack.
     *
     * <p>A missing file is only a warning: the art is still worth having, and
     * the client then falls back to starting a game with defaults.
     *
     * @param install The original's install directory.
     * @param textDir The pack's {@code text/} directory (created if absent).
     * @return The number of files copied.
     * @exception IOException if a file cannot be copied.
     */
    static int copyTexts(Path install, Path textDir) throws IOException {
        Files.createDirectories(textDir);
        int n = 0;
        for (String name : TEXT_FILES) {
            final Path src;
            try {
                src = findIgnoreCase(install, name);
            } catch (IOException e) {
                System.err.println("WARNING: " + e.getMessage()
                    + " -- the classic new-game screens will be skipped.");
                continue;
            }
            Files.copy(src, textDir.resolve(name.toUpperCase(Locale.ROOT)));
            n++;
        }
        return n;
    }

    /**
     * Write {@code <pack>/ss-anchors.properties}: one line
     * {@code <STEM>.SS.<nnn>=<ax>,<ay>} per SS frame, the raw bottom-centre
     * anchor from the sprite header (see {@link SsDecoder#anchors}).  These
     * are positions, not content; the client turns them into a top-left with
     * the frame's image size ({@code ClassicPackFiles.spriteTopLeft}).
     */
    private static void writeAnchors(Path path, TreeMap<String, String> anchors)
            throws IOException {
        try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write("# GENERATED by ClassicAssetConverter: SS frame anchors"
                + " (bottom-centre x,y on the 320x200 screen).\n");
            w.write("# top-left = (ax - w/2, ay - h + 1), w/h = the frame PNG's size.\n");
            for (var e : anchors.entrySet()) {
                w.write(e.getKey() + "=" + e.getValue() + "\n");
            }
        }
    }

    /**
     * Write the scaffold keys, then append the optional A2 aliases file
     * with its line ends as the scaffold's ({@code \n}), so the pack does
     * not depend on how git checked the file out.
     */
    private static void writeResourceProperties(Path path,
            TreeMap<String, String> entries, Path aliases) throws IOException {
        try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write("# GENERATED by net.sf.freecol.tools.classicassets."
                + "ClassicAssetConverter -- do not edit by hand.\n");
            w.write("# Local pack of the user's OWN original Colonization art (git-ignored).\n");
            w.write("# Scaffold keys expose every extracted frame (pik/ss) and font\n");
            w.write("# (ff: 2-bit atlas + quoted metrics string); FreeCol-key aliases\n");
            w.write("# (plan item A2) are appended from tools/classic_assets/aliases.properties.\n\n");
            for (var e : entries.entrySet()) {
                w.write(e.getKey() + "=" + e.getValue() + "\n");
            }
            if (aliases != null && Files.isRegularFile(aliases)) {
                w.write("\n# --- A2 aliases (from tools/classic_assets/aliases.properties) ---\n");
                w.write(Files.readString(aliases, StandardCharsets.UTF_8)
                    .replace("\r\n", "\n"));
            }
        }
    }

    /** List files under {@code dir} whose name ends with {@code ext}
     *  (case-insensitively), sorted by name. */
    private static List<Path> listByExtension(Path dir, String ext) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)
                        .endsWith(ext))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .collect(java.util.stream.Collectors.toList());
        }
    }

    /** Find a file by exact name ignoring case (installs vary in case). */
    private static Path findIgnoreCase(Path dir, String name) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().equalsIgnoreCase(name))
                    .findFirst()
                    .orElseThrow(() -> new IOException(
                        "not found in " + dir + ": " + name));
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> s = Files.walk(root)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }
}
