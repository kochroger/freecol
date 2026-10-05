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

package net.sf.freecol.tools;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import net.sf.freecol.FreeCol;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.io.FreeColRules;
import net.sf.freecol.common.model.Direction;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Nation;
import net.sf.freecol.common.model.NationOptions;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileImprovement;
import net.sf.freecol.common.model.Topology;
import net.sf.freecol.common.option.MapGeneratorOptions;
import net.sf.freecol.common.option.OptionGroup;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.server.generator.SimpleMapGenerator;
import net.sf.freecol.server.model.ServerGame;
import net.sf.freecol.server.model.ServerPlayer;


/**
 * Square topology tool: dump maps as PNG and ASCII with statistics.
 * It lives in the test tree, so it is built by {@code ant
 * build-unit-tests} into build/ but never packed into FreeCol.jar.
 *
 * Generates maps for seeds 1..N with the real server map generator
 * under the square {@link Topology} and the options of
 * {@link MapGeneratorOptions#applyTopologyDefaults}, one isometric map
 * with the default options (seed 1, drawn by raw x,y the way the
 * Classic UI draws it) for comparison, and optionally renders an
 * original Colonization .MP map the same way.  The tool sets the
 * topology itself, so -Dfreecol.topology is not needed.
 *
 * Run from the FreeCol directory, which holds data/, after
 * {@code ant build-unit-tests}:
 * <pre>
 *   java -cp "build;jars/*" net.sf.freecol.tools.SquareMapDump OUTDIR
 *       [MPFILE] [--seeds N] [--set OPTION_ID=VALUE]...
 * </pre>
 * {@code --set} overrides a map generator option of the square maps,
 * for tuning.
 */
public class SquareMapDump {

    /** The rules the Classic UI plays with by default. */
    private static final String RULES = "freecol";

    private static final String DIFFICULTY = "model.difficulty.medium";

    /** Pixels per tile. */
    private static final int PX = 6;

    /** One cell of a map to dump. */
    private static final class Cell {
        /** Tile type suffix, e.g. "plains". */
        String type = "ocean";
        /** River magnitude, 0 for none. */
        int river = 0;
        /** Raw x,y offsets of the neighbours the river connects to. */
        final List<int[]> riverTo = new ArrayList<>();
        /** The directions the river connects in. */
        final List<Direction> riverDirs = new ArrayList<>();
        boolean rumour, settlement, polar, border;

        boolean isWater() {
            return "ocean".equals(type) || "highSeas".equals(type)
                || "lake".equals(type);
        }
        boolean isForest() {
            return type.endsWith("Forest");
        }
    }

    /** Neighbour lookup for connectivity statistics. */
    private interface Neighbours {
        List<int[]> of(int x, int y);
    }

    private static final java.util.Map<String, Color> COLOURS = new HashMap<>();
    private static final java.util.Map<String, Character> CHARS = new HashMap<>();
    static {
        colour("ocean",        40,  80, 170, '.');
        colour("highSeas",     15,  35, 100, ':');
        colour("lake",         80, 140, 220, 'o');
        colour("arctic",      240, 240, 250, '_');
        colour("hills",       170, 120,  60, '^');
        colour("mountains",   115,  95,  85, '*');
        // Families: open land / forested.
        colour("tundra",      170, 170, 140, 't');
        colour("borealForest", 95, 110,  85, 'B');
        colour("desert",      235, 215, 140, 'd');
        colour("scrubForest", 165, 155,  90, 'S');
        colour("plains",      195, 205,  95, 'p');
        colour("mixedForest",  75, 125,  45, 'M');
        colour("prairie",     215, 190, 105, 'r');
        colour("broadleafForest", 120, 115, 45, 'L');
        colour("grassland",   125, 195,  85, 'g');
        colour("coniferForest", 40, 100,  55, 'C');
        colour("savannah",    185, 195,  60, 'v');
        colour("tropicalForest", 60, 140, 35, 'T');
        colour("marsh",       125, 155, 115, 'm');
        colour("wetlandForest", 65,  95,  75, 'W');
        colour("swamp",        95, 125,  85, 's');
        colour("rainForest",   30,  85,  40, 'R');
    }

    private static void colour(String type, int r, int g, int b, char c) {
        COLOURS.put(type, new Color(r, g, b));
        CHARS.put(type, c);
    }

    private static final Color RIVER = new Color(0, 215, 255);
    private static final Color RUMOUR = new Color(255, 230, 0);
    private static final Color SETTLEMENT = new Color(170, 20, 20);
    private static final Color UNKNOWN = Color.MAGENTA;

    /** Original .MP terrain codes 0..26 (see ColonizationMapReader). */
    private static final String[] MP_TYPES = {
        "tundra", "desert", "plains", "prairie",
        "grassland", "savannah", "marsh", "swamp",
        "borealForest", "scrubForest", "mixedForest", "broadleafForest",
        "coniferForest", "tropicalForest", "wetlandForest", "rainForest",
        "borealForest", "scrubForest", "mixedForest", "broadleafForest",
        "coniferForest", "tropicalForest", "wetlandForest", "rainForest",
        "arctic", "ocean", "highSeas"
    };


    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        Logger.getLogger("").setLevel(Level.WARNING);
        if (args.length < 1) {
            System.err.println("Usage: SquareMapDump OUTDIR [MPFILE]"
                + " [--seeds N] [--set OPTION_ID=VALUE]...");
            System.exit(1);
        }
        final File out = new File(args[0]);
        File mpFile = null;
        int seeds = 10;
        final List<String[]> sets = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            if ("--seeds".equals(args[i])) {
                seeds = Integer.parseInt(args[++i]);
            } else if ("--set".equals(args[i])) {
                sets.add(args[++i].split("=", 2));
            } else {
                mpFile = new File(args[i]);
            }
        }
        if (!out.isDirectory() && !out.mkdirs()) {
            throw new IOException("Can not create " + out);
        }
        FreeColRules.loadRules();
        Messages.loadMessageBundle(Locale.US);

        final Topology saved = Topology.current();
        try {
            for (int seed = 1; seed <= seeds; seed++) {
                Map map = generate(Topology.SQUARE, seed, sets);
                String name = String.format("seed-%02d", seed);
                dumpMap(map, out, name, "square topology, seed " + seed);
            }
            Map iso = generate(Topology.ISOMETRIC, 1, Collections.emptyList());
            dumpMap(iso, out, "iso-seed-01",
                    "isometric topology, default options, seed 1");
        } finally {
            Topology.setCurrent(saved);
        }
        if (mpFile != null) dumpMp(mpFile, out, "amer2");
    }

    /**
     * Generate a map with the real server map generator.
     *
     * @param topology The {@code Topology} to use.
     * @param seed The random seed.
     * @param sets Option overrides for square maps.
     * @return The new {@code Map}.
     */
    private static Map generate(Topology topology, long seed,
                                List<String[]> sets) {
        Topology.setCurrent(topology);
        Specification spec = FreeCol.loadSpecification(
            FreeColRules.getFreeColRulesFile(RULES), null, DIFFICULTY);
        spec.setFile(MapGeneratorOptions.IMPORT_FILE, null);
        OptionGroup mgo = spec.getMapGeneratorOptions();
        MapGeneratorOptions.applyTopologyDefaults(mgo);
        if (topology == Topology.SQUARE) {
            for (String[] s : sets) mgo.setInteger(s[0], Integer.parseInt(s[1]));
        }

        // As FreeColTestCase.getStandardGame.
        Game game = new ServerGame(spec);
        NationOptions nationOptions = new NationOptions(spec);
        for (Nation nation : spec.getEuropeanNations()) {
            nationOptions.setNationState(nation,
                NationOptions.NationState.AVAILABLE);
        }
        game.setNationOptions(nationOptions);
        for (Nation n : spec.getNations()) {
            if (n.isUnknownEnemy()) continue;
            Player p = new ServerPlayer(game, false, n);
            boolean ai = !n.getType().isEuropean() || n.getType().isREF();
            p.setAI(ai);
            if (ai || game.canAddNewPlayer()) game.addPlayer(p);
        }
        new SimpleMapGenerator(new Random(seed))
            .generateMap(game, null, false, new LogBuilder(-1));
        return game.getMap();
    }

    private static String suffix(String id) {
        return id.substring(id.lastIndexOf('.') + 1);
    }

    private static void dumpMap(Map map, File out, String name, String title)
        throws IOException {
        final int w = map.getWidth(), h = map.getHeight();
        final Cell[][] cells = new Cell[w][h];
        for (Tile t : map) {
            Cell c = new Cell();
            c.type = suffix(t.getType().getId());
            c.rumour = t.hasLostCityRumour();
            c.settlement = t.hasSettlement();
            c.polar = map.isPolar(t);
            TileImprovement river = t.getRiver();
            if (river != null) {
                c.river = Math.max(1, Math.min(2, river.getMagnitude()));
                for (Direction d : Topology.current().edgeDirections()) {
                    if (!river.isConnectedTo(d)) continue;
                    Map.Position p = d.step(t.getX(), t.getY());
                    c.riverTo.add(new int[] { p.x - t.getX(), p.y - t.getY() });
                    c.riverDirs.add(d);
                }
            }
            cells[t.getX()][t.getY()] = c;
        }
        Neighbours nb = (x, y) -> {
            List<int[]> ret = new ArrayList<>();
            for (Direction d : Direction.values()) {
                Tile n = map.getTile(x, y).getNeighbourOrNull(d);
                if (n != null) ret.add(new int[] { n.getX(), n.getY() });
            }
            return ret;
        };
        String opts = optionSummary(map.getGame().getMapGeneratorOptions());
        write(cells, nb, out, name, title + "\n" + opts);
    }

    private static String optionSummary(OptionGroup mgo) {
        return "options: " + mgo.getInteger(MapGeneratorOptions.MAP_WIDTH)
            + "x" + mgo.getInteger(MapGeneratorOptions.MAP_HEIGHT)
            + " landMass=" + mgo.getInteger(MapGeneratorOptions.LAND_MASS)
            + " forestNumber=" + mgo.getInteger(MapGeneratorOptions.FOREST_NUMBER)
            + " riverNumber=" + mgo.getInteger(MapGeneratorOptions.RIVER_NUMBER)
            + " preferredDistanceToEdge="
            + mgo.getInteger(MapGeneratorOptions.PREFERRED_DISTANCE_TO_EDGE)
            + " maximumDistanceToEdge="
            + mgo.getInteger(MapGeneratorOptions.MAXIMUM_DISTANCE_TO_EDGE)
            + " distanceToHighSea="
            + mgo.getInteger(MapGeneratorOptions.DISTANCE_TO_HIGH_SEA)
            + " landGenerator="
            + mgo.getSelectionName(MapGeneratorOptions.LAND_GENERATOR_TYPE);
    }

    /**
     * Render an original Colonization .MP map: a 6 byte header (width,
     * height, 4 as little endian shorts) and three layers of
     * width*height bytes, row major.  Layer 1 holds the terrain in the
     * low five bits and hills/river/major flags in the high three.
     * The outer ring is the map's ocean border.
     */
    private static void dumpMp(File file, File out, String name)
        throws IOException {
        final byte[] data = Files.readAllBytes(file.toPath());
        final int w = (data[0] & 0xff) | ((data[1] & 0xff) << 8);
        final int h = (data[2] & 0xff) | ((data[3] & 0xff) << 8);
        final Cell[][] cells = new Cell[w][h];
        int unknown = 0, strayBorder = 0, innerLand = 0, forestCodes = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                final int b = data[6 + y * w + x] & 0xff;
                final int terrain = b & 0x1f, overlay = b >> 5;
                Cell c = new Cell();
                c.border = x == 0 || y == 0 || x == w - 1 || y == h - 1;
                if (!c.border && terrain < 24) {
                    innerLand++;
                    if (terrain >= 8) forestCodes++;
                }
                if (terrain < MP_TYPES.length) {
                    c.type = MP_TYPES[terrain];
                } else {
                    c.type = "unknown" + terrain;
                    unknown++;
                }
                if (overlay == 1 || overlay == 3) c.type = "hills";
                if (overlay == 5 || overlay == 7) c.type = "mountains";
                if (overlay == 4) unknown++;
                if ((overlay & 2) != 0) c.river = ((overlay & 4) != 0) ? 2 : 1;
                if (c.border) {
                    if (!c.isWater()) strayBorder++;
                    c.type = "ocean";
                    c.river = 0;
                }
                cells[x][y] = c;
            }
        }
        // The file has no river connections: join orthogonal river
        // neighbours, as the original's 4-way river art does.
        final int[][] sides = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };
        final Direction[] sideDirs = { Direction.N, Direction.E,
                                       Direction.S, Direction.W };
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (cells[x][y].river == 0) continue;
                for (int i = 0; i < sides.length; i++) {
                    int nx = x + sides[i][0], ny = y + sides[i][1];
                    if (nx >= 0 && ny >= 0 && nx < w && ny < h
                        && cells[nx][ny].river > 0) {
                        cells[x][y].riverTo.add(sides[i]);
                        cells[x][y].riverDirs.add(sideDirs[i]);
                    }
                }
            }
        }
        Neighbours nb = (x, y) -> {
            List<int[]> ret = new ArrayList<>();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    int nx = x + dx, ny = y + dy;
                    if ((dx != 0 || dy != 0) && nx >= 0 && ny >= 0
                        && nx < w && ny < h) ret.add(new int[] { nx, ny });
                }
            }
            return ret;
        };
        write(cells, nb, out, name, "original map " + file.getName()
            + " (" + w + "x" + h + "), outer ring treated as ocean border ("
            + strayBorder + " stray land codes there ignored), "
            + "statistics over the inner " + (w - 2) + "x" + (h - 2)
            + "\nunknown codes: " + unknown
            + "; rivers joined to orthogonal river neighbours (the file"
            + " stores no connections)"
            + "\nforest terrain codes 8..23 under any overlay: " + forestCodes
            + " = " + pct(forestCodes, innerLand) + " of land (below, hills and"
            + " mountains count as not forested, as in FreeCol)");
    }

    private static void write(Cell[][] cells, Neighbours nb, File out,
                              String name, String header) throws IOException {
        ImageIO.write(render(cells), "png", new File(out, name + ".png"));
        try (PrintWriter pw = new PrintWriter(new File(out, name + ".txt"),
                                              StandardCharsets.UTF_8.name())) {
            pw.println(name + ": " + header);
            pw.print(stats(cells, nb));
            pw.println();
            pw.println("legend: . ocean  : high seas/sea lane  o lake  _ arctic"
                + "  ^ hills  * mountains  ~ minor river  = major river");
            pw.println("        t d p r g v m s = tundra desert plains prairie"
                + " grassland savannah marsh swamp");
            pw.println("        B S M L C T W R = their forests (boreal scrub"
                + " mixed broadleaf conifer tropical wetland rain)");
            pw.println();
            pw.print(ascii(cells));
        }
        System.out.println("Wrote " + new File(out, name + ".png")
            + " and .txt");
    }

    private static BufferedImage render(Cell[][] cells) {
        final int w = cells.length, h = cells[0].length;
        BufferedImage img = new BufferedImage(w * PX, h * PX,
                                              BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                Color col = COLOURS.get(cells[x][y].type);
                g.setColor((col == null) ? UNKNOWN : col);
                g.fillRect(x * PX, y * PX, PX, PX);
            }
        }
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                Cell c = cells[x][y];
                int cx = x * PX + PX / 2, cy = y * PX + PX / 2;
                if (c.river > 0) {
                    g.setColor(RIVER);
                    g.setStroke(new BasicStroke(c.river));
                    g.fillRect(cx - 1, cy - 1, 2, 2);
                    for (int[] o : c.riverTo) {
                        g.drawLine(cx, cy, cx + o[0] * PX / 2,
                                   cy + o[1] * PX / 2);
                    }
                }
                if (c.settlement) {
                    g.setColor(SETTLEMENT);
                    g.fillRect(x * PX + 1, y * PX + 1, PX - 2, PX - 2);
                } else if (c.rumour) {
                    g.setColor(RUMOUR);
                    g.fillRect(cx - 1, cy - 1, 2, 2);
                }
            }
        }
        g.dispose();
        return img;
    }

    private static String ascii(Cell[][] cells) {
        final int w = cells.length, h = cells[0].length;
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Cell c = cells[x][y];
                Character ch = CHARS.get(c.type);
                char out = (ch == null) ? '?' : ch;
                if (c.river == 1) out = (out == '^') ? 'x' : '~';
                if (c.river == 2) out = (out == '^' || out == '*') ? 'X' : '=';
                sb.append(out);
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String pct(int part, int whole) {
        return String.format(Locale.US, "%.1f%%",
                             (whole == 0) ? 0.0 : 100.0 * part / whole);
    }

    private static String stats(Cell[][] cells, Neighbours nb) {
        final int w = cells.length, h = cells[0].length;
        int area = 0, nonPolarArea = 0, land = 0, nonPolarLand = 0,
            forest = 0, hills = 0, mountains = 0, elevated = 0,
            arctic = 0, lake = 0,
            highL = 0, highR = 0, rowsL = 0, rowsR = 0,
            rivers = 0, major = 0, straight = 0, bends = 0, ends = 0,
            junctions = 0, rumours = 0, settlements = 0,
            polarTop = 0, polarBottom = 0;
        StringBuilder arcticRows = new StringBuilder();
        for (int y = 0; y < h; y++) {
            boolean hasL = false, hasR = false, polarRow = false;
            int arcticInRow = 0;
            for (int x = 0; x < w; x++) {
                Cell c = cells[x][y];
                if (c.border) continue;
                area++;
                if (c.polar) polarRow = true; else nonPolarArea++;
                if ("highSeas".equals(c.type)) {
                    if (x < w / 2) { highL++; hasL = true; }
                    else { highR++; hasR = true; }
                }
                if ("lake".equals(c.type)) lake++;
                if (c.rumour) rumours++;
                if (c.settlement) settlements++;
                if (c.isWater()) continue;
                land++;
                if (!c.polar) nonPolarLand++;
                if (c.isForest() && !c.polar) forest++;
                if ("hills".equals(c.type)) hills++;
                if ("mountains".equals(c.type)) mountains++;
                if (!c.polar && ("hills".equals(c.type)
                        || "mountains".equals(c.type))) elevated++;
                if ("arctic".equals(c.type)) { arctic++; arcticInRow++; }
                if (c.river > 0) {
                    rivers++;
                    if (c.river > 1) major++;
                    int n = c.riverTo.size();
                    if (n <= 1) ends++;
                    else if (n >= 3) junctions++;
                    else if (c.riverDirs.get(0).getReverseDirection()
                             == c.riverDirs.get(1)) straight++;
                    else bends++;
                }
            }
            if (hasL) rowsL++;
            if (hasR) rowsR++;
            if (polarRow) {
                if (y < h / 2) polarTop++; else polarBottom++;
            }
            if (arcticInRow > 0) {
                arcticRows.append(arcticRows.length() == 0 ? "" : ",").append(y);
            }
        }

        // Landmasses: non-polar land, connected through the topology.
        boolean[][] seen = new boolean[w][h];
        List<Integer> sizes = new ArrayList<>();
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                Cell c = cells[x][y];
                if (seen[x][y] || c.border || c.polar || c.isWater()) continue;
                int size = 0;
                Deque<int[]> todo = new ArrayDeque<>();
                todo.add(new int[] { x, y });
                seen[x][y] = true;
                while (!todo.isEmpty()) {
                    int[] p = todo.poll();
                    size++;
                    for (int[] n : nb.of(p[0], p[1])) {
                        Cell nc = cells[n[0]][n[1]];
                        if (seen[n[0]][n[1]] || nc.border || nc.polar
                            || nc.isWater()) continue;
                        seen[n[0]][n[1]] = true;
                        todo.add(n);
                    }
                }
                sizes.add(size);
            }
        }
        sizes.sort(Collections.reverseOrder());
        int big = 0;
        for (int s : sizes) if (s >= 10) big++;

        StringBuilder sb = new StringBuilder();
        sb.append("size ").append(w).append('x').append(h)
            .append(", area counted ").append(area).append(" tiles\n");
        sb.append("land ").append(land).append(" tiles = ").append(pct(land, area))
            .append(" of the map; without polar rows ").append(nonPolarLand)
            .append(" = ").append(pct(nonPolarLand, nonPolarArea)).append('\n');
        sb.append("forest ").append(forest).append(" = ")
            .append(pct(forest, nonPolarLand)).append(" of non-polar land, ")
            .append(pct(forest, nonPolarLand - elevated))
            .append(" of non-polar land without hills/mountains\n");
        sb.append("hills ").append(hills).append(", mountains ").append(mountains)
            .append(", arctic tiles ").append(arctic).append(" (rows ")
            .append(arcticRows.length() == 0 ? "none" : arcticRows).append(")")
            .append(", lake tiles ").append(lake).append('\n');
        sb.append("polar rows (Map.isPolar): top ").append(polarTop)
            .append(", bottom ").append(polarBottom).append('\n');
        sb.append("landmasses (non-polar, 8-way): ").append(sizes.size())
            .append(", of which >= 10 tiles: ").append(big)
            .append("; largest: ").append(sizes.subList(0, Math.min(3, sizes.size())))
            .append('\n');
        sb.append("high seas: left half ").append(highL).append(" tiles in ")
            .append(rowsL).append(" rows, right half ").append(highR)
            .append(" tiles in ").append(rowsR).append(" rows\n");
        sb.append("river tiles ").append(rivers).append(" (major ").append(major)
            .append("): straight ").append(straight).append(", bends ")
            .append(bends).append(", ends ").append(ends)
            .append(", junctions ").append(junctions).append('\n');
        sb.append("lost city rumours ").append(rumours)
            .append(", native settlements ").append(settlements).append('\n');
        return sb.toString();
    }
}
