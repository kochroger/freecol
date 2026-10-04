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
 * Usage:
 * <pre>
 *   ClassicAssetConverter --install &lt;colonize-dir&gt; --out &lt;pack-dir&gt; [--aliases &lt;file&gt;]
 * </pre>
 */
public final class ClassicAssetConverter {

    private static final String KEY_PREFIX = "image.classic_original";

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

        // The pack is a build artifact: regenerate cleanly.
        deleteRecursively(out);
        Files.createDirectories(pikDir);
        Files.createDirectories(ssDir);
        Files.createDirectories(ffDir);

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
        for (Path ss : listByExtension(install, ".ss")) {
            String name = ss.getFileName().toString();        // e.g. TERRAIN1.SS
            List<BufferedImage> frames = SsDecoder.decode(Files.readAllBytes(ss));
            for (int i = 0; i < frames.size(); i++) {
                String stem = String.format("%s.%03d", name, i);
                String png = stem + ".png";
                ImageIO.write(frames.get(i), "png", ssDir.resolve(png).toFile());
                entries.put(KEY_PREFIX + ".ss." + stem, "resources/images/ss/" + png);
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

        writeResourceProperties(out.resolve("resources.properties"), entries, aliases);
        Files.writeString(out.resolve("mod.xml"),
            "<mod id=\"classic_original\"/>\n", StandardCharsets.UTF_8);
        Files.writeString(out.resolve("FreeColMessages.properties"),
            "mod.classic_original.name=Classic Colonization art (local)\n"
            + "mod.classic_original.shortDescription="
            + "Original Sid Meier's Colonization graphics, extracted locally "
            + "from your own game install.\n",
            StandardCharsets.UTF_8);

        System.out.printf("classic_original pack: %d pik + %d ss images + %d fonts -> %s%n",
            pikCount, ssFrames, ffCount, out);
    }

    /** Write the scaffold keys, then append the optional A2 aliases file. */
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
                w.write(Files.readString(aliases, StandardCharsets.UTF_8));
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
