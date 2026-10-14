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

package net.sf.freecol.tools;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import net.sf.freecol.FreeCol;
import net.sf.freecol.common.io.FreeColRules;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Nation;
import net.sf.freecol.common.model.NationOptions;
import net.sf.freecol.common.model.OriginalWorld;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Region;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Topology;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.option.MapGeneratorOptions;
import net.sf.freecol.common.option.OptionGroup;
import net.sf.freecol.common.option.OriginalWorldSettings;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.server.generator.SimpleMapGenerator;
import net.sf.freecol.server.model.ServerGame;
import net.sf.freecol.server.model.ServerPlayer;


/**
 * Measures the new maps of the original's world settings (the "levi"
 * rules, {@link OriginalWorldSettings}) against the original: land,
 * land masses, coasts, rivers, relief, terrain mix, the Pacific, the
 * high seas lanes and the start lanes, made the way a new game of the
 * Classic UI makes them (the real server map generator, the topology's
 * map options, the four Europeans and the eight tribes).
 *
 * <p>Usage (in the worktree, after compiling the tests into build/):
 * <pre>
 *   java -cp "build;jars/*" net.sf.freecol.tools.WorldSettingsCensus
 *       OUT.tsv [--seeds N] [--first S] [--topology square,isometric]
 *       [--settings NNgn,KIgn,...] [--difficulty model.difficulty.veryEasy]
 *       [--as-is]
 * </pre>
 * A setting is {@link OriginalWorldSettings#getCode}: LANDMASSE K/N/G,
 * LANDFORM I/N/C, TEMPERATUR k/g/w, KLIMA t/n/f.  {@code --as-is}
 * measures the rules' own map options instead (one setting "as-is"),
 * e.g. for rules without the original's world.
 *
 * <p>Land is counted as in the original's saves: land tiles outside the
 * polar rows that are not arctic (the original: rows 1 to 70 of 72,
 * terrain below the arctic).  One TSV row per map, then a summary per
 * setting and topology.
 */
public class WorldSettingsCensus {

    /** The original's terrain types, by the types of the rules. */
    private static final String[] BASE = { "tundra", "desert", "plains",
        "prairie", "grass", "savannah", "marsh", "swamp" };

    /** Roger's games in the original per setting (land tiles). */
    private static final java.util.Map<String, int[]> ORIGINAL
        = new LinkedHashMap<>();
    static {
        ORIGINAL.put("NNgn", new int[] { 872, 901 });
        ORIGINAL.put("KNgn", new int[] { 673, 732 });
        ORIGINAL.put("GNgn", new int[] { 1079, 1184 });
        ORIGINAL.put("NIgn", new int[] { 698 });
        ORIGINAL.put("NCgn", new int[] { 1191 });
        ORIGINAL.put("GCwf", new int[] { 1373 });
        ORIGINAL.put("KIgn", new int[] { 427, 469, 650 }); // «Neue Welt»
    }

    /** The settings measured by default. */
    private static final String DEFAULT_SETTINGS
        = "KIgn,KNgn,KCgn,NIgn,NNgn,NCgn,GIgn,GNgn,GCgn,"
        + "NNkn,NNwn,NNgt,NNgf,GCwf,KIkt";


    /** The numbers of one map. */
    public static final class Stats {
        public int land, masses, masses10, masses100, largest, coast;
        public int river, hills, mountains, forested, arctic, lakes;
        public int pacific, pacificRows, highSeas, startRowsEast,
            startRowsWest, gapEastMedian, gapEastMin, gapWestMedian,
            gapWestMin, landWest, landEast, shipToLand, villages,
            landRegions, rumours;
        public final int[] base = new int[BASE.length];
        public final int[] bands = new int[7];
        /** Rows (y) without high seas leading to Europe on a side. */
        public final List<Integer> rowsWithoutEast = new ArrayList<>(),
            rowsWithoutWest = new ArrayList<>();
        /** Rows (y) of the land rows without a Pacific tile. */
        public final List<Integer> rowsWithoutPacific = new ArrayList<>();
    }


    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("usage: WorldSettingsCensus OUT.tsv"
                + " [--seeds N] [--first S] [--topology square,isometric]"
                + " [--settings NNgn,...] [--difficulty D] [--as-is]");
            System.exit(2);
        }
        final File out = new File(args[0]);
        int seeds = 30, first = 1;
        String[] topologies = { "square", "isometric" };
        String[] settings = DEFAULT_SETTINGS.split(",");
        String difficulty = "model.difficulty.veryEasy";
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
            case "--seeds": seeds = Integer.parseInt(args[++i]); break;
            case "--first": first = Integer.parseInt(args[++i]); break;
            case "--topology": topologies = args[++i].split(","); break;
            case "--settings": settings = args[++i].split(","); break;
            case "--difficulty": difficulty = args[++i]; break;
            case "--as-is": settings = new String[] { "as-is" }; break;
            default:
                System.err.println("unknown argument " + args[i]);
                System.exit(2);
            }
        }
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, StandardCharsets.UTF_8.name()));
        java.util.logging.Logger.getLogger("")
            .setLevel(java.util.logging.Level.SEVERE);
        FreeColRules.loadRules();
        net.sf.freecol.common.i18n.Messages.loadMessageBundle(Locale.US);
        try (PrintWriter pw = new PrintWriter(out, StandardCharsets.UTF_8.name())) {
            pw.println("setting\ttopology\tseed\tsize\tland\tmean\tmasses\t>=10\t>=100"
                + "\tlargest%\tcoast/land\triver%\thills%\tmountains%\tforest%"
                + "\tarctic\tlakes\tpacific\tpacificRows\thighSeas\tstartRowsE/W"
                + "\tgapE med/min\tgapW med/min\tlandX\tship->land\tvillages"
                + "\tlandRegions\trumours\tbands\tmix");
            final List<String> summary = new ArrayList<>();
            for (String top : topologies) {
                final Topology topology = ("isometric".equals(top))
                    ? Topology.ISOMETRIC : Topology.SQUARE;
                for (String code : settings) {
                    final OriginalWorldSettings world = ("as-is".equals(code))
                        ? null : OriginalWorldSettings.ofCode(code);
                    final List<Stats> all = new ArrayList<>();
                    int mean = -1;
                    String size = "";
                    for (int seed = first; seed < first + seeds; seed++) {
                        final Map map = generate(world, difficulty,
                                                 topology, seed);
                        final Stats s = measure(map);
                        all.add(s);
                        final OptionGroup mgo = map.getGame()
                            .getMapGeneratorOptions();
                        mean = (OriginalWorld.isOriginal(mgo))
                            ? OriginalWorld.getMeanLand(
                                OriginalWorld.getLandMass(mgo),
                                OriginalWorld.getLandForm(mgo),
                                map.getWidth(), map.getHeight())
                            : -1;
                        size = map.getWidth() + "x" + map.getHeight();
                        pw.println(code + "\t" + top + "\t" + seed + "\t" + size
                            + "\t" + s.land + "\t" + mean + "\t" + s.masses
                            + "\t" + s.masses10 + "\t" + s.masses100
                            + "\t" + pct(s.largest, s.land)
                            + "\t" + ratio(s.coast, s.land)
                            + "\t" + pct(s.river, s.land)
                            + "\t" + pct(s.hills, s.land)
                            + "\t" + pct(s.mountains, s.land)
                            + "\t" + pct(s.forested, s.land)
                            + "\t" + s.arctic + "\t" + s.lakes
                            + "\t" + s.pacific + "\t" + s.pacificRows
                            + "\t" + s.highSeas
                            + "\t" + s.startRowsEast + "/" + s.startRowsWest
                            + "\t" + s.gapEastMedian + "/" + s.gapEastMin
                            + "\t" + s.gapWestMedian + "/" + s.gapWestMin
                            + "\t" + s.landWest + "-" + s.landEast
                            + "\t" + s.shipToLand + "\t" + s.villages
                            + "\t" + s.landRegions + "\t" + s.rumours
                            + "\t" + Arrays.toString(s.bands)
                            + "\t" + mix(s));
                        pw.flush();
                    }
                    summary.addAll(summarize(code, top, size, mean, all));
                    for (String l : summary.subList(summary.size() - 7,
                                                    summary.size())) {
                        System.out.println(l);
                    }
                    System.out.flush();
                }
            }
            for (String l : summary) pw.println(l);
        }
    }

    /**
     * Generate a map as a new game of the Classic UI does, with the
     * original's world set to some answers.
     *
     * @param world The answers, or null for the rules' own options.
     * @param difficulty The difficulty level identifier.
     * @param topology The {@code Topology} to use.
     * @param seed The random seed.
     * @return The new {@code Map}.
     */
    public static Map generate(OriginalWorldSettings world, String difficulty,
                               Topology topology, long seed) {
        Topology.setCurrent(topology);
        Specification spec = FreeCol.loadSpecification(
            FreeColRules.getFreeColRulesFile("levi"), null, difficulty);
        spec.setFile(MapGeneratorOptions.IMPORT_FILE, null);
        MapGeneratorOptions.applyTopologyDefaults(spec.getMapGeneratorOptions());
        if (world != null) world.apply(spec.getMapGeneratorOptions());
        Game game = new ServerGame(spec);
        game.setNationOptions(new NationOptions(spec));
        for (Nation n : spec.getNations()) {
            if (n.isUnknownEnemy() || n.getType().isREF()) continue;
            Player p = new ServerPlayer(game, false, n);
            p.setAI(!n.getType().isEuropean()
                || !"model.nation.dutch".equals(n.getId()));
            game.addPlayer(p);
        }
        new SimpleMapGenerator(new Random(seed))
            .generateMap(game, null, true, new LogBuilder(-1));
        return game.getMap();
    }

    /**
     * Is a tile land as the original's saves count it: land, outside the
     * polar rows, not arctic?
     *
     * @param map The {@code Map}.
     * @param t The {@code Tile}.
     * @return True for land.
     */
    public static boolean isOriginalLand(Map map, Tile t) {
        return t.isLand() && !map.isPolar(t)
            && !"model.tile.arctic".equals(t.getType().getId());
    }

    /**
     * Measure one map.
     *
     * @param map The {@code Map} to measure.
     * @return Its {@code Stats}.
     */
    public static Stats measure(Map map) {
        final Stats s = new Stats();
        final int w = map.getWidth(), h = map.getHeight();
        final boolean[][] land = new boolean[w][h];
        final int[] westLand = new int[h], eastLand = new int[h];
        Arrays.fill(westLand, -1);
        Arrays.fill(eastLand, -1);
        s.landWest = Integer.MAX_VALUE;
        s.landEast = -1;
        final Set<Integer> pacificRows = new HashSet<>();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                final Tile t = map.getTile(x, y);
                final String id = t.getType().getId();
                if ("model.tile.highSeas".equals(id)) s.highSeas++;
                if ("model.tile.lake".equals(id)) s.lakes++;
                if (t.getRegion() != null && t.getRegion().isPacific()) {
                    s.pacific++;
                    pacificRows.add(y);
                }
                if (t.hasLostCityRumour()) s.rumours++;
                if (t.isLand() && !map.isPolar(t)
                    && "model.tile.arctic".equals(id)) s.arctic++;
                if (!isOriginalLand(map, t)) continue;
                land[x][y] = true;
                s.land++;
                if (westLand[y] < 0) westLand[y] = x;
                eastLand[y] = x;
                s.landWest = Math.min(s.landWest, x);
                s.landEast = Math.max(s.landEast, x);
                s.bands[Math.max(0, Math.min(6, (y - 1) * 7 / (h - 2)))]++;
                boolean coast = false;
                for (Tile n : t.getSurroundingTiles(1)) {
                    if (!n.isLand()) coast = true;
                }
                if (coast) s.coast++;
                if (t.hasRiver()) s.river++;
                if ("model.tile.hills".equals(id)) {
                    s.hills++;
                } else if ("model.tile.mountains".equals(id)) {
                    s.mountains++;
                } else {
                    final int b = baseOf(id);
                    if (b >= 0) s.base[b]++;
                    if (t.getType().isForested()) s.forested++;
                }
            }
        }
        s.pacificRows = pacificRows.size();
        for (int y = 0; y < h; y++) {
            if (westLand[y] >= 0 && !pacificRows.contains(y)) {
                s.rowsWithoutPacific.add(y);
            }
        }
        // Land masses, 8-connected through the map's own neighbours.
        final boolean[][] seen = new boolean[w][h];
        final List<Integer> masses = new ArrayList<>();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!land[x][y] || seen[x][y]) continue;
                int n = 0;
                Deque<Tile> q = new ArrayDeque<>();
                q.add(map.getTile(x, y));
                seen[x][y] = true;
                while (!q.isEmpty()) {
                    Tile t = q.poll();
                    n++;
                    for (Tile u : t.getSurroundingTiles(1)) {
                        if (land[u.getX()][u.getY()]
                            && !seen[u.getX()][u.getY()]) {
                            seen[u.getX()][u.getY()] = true;
                            q.add(u);
                        }
                    }
                }
                masses.add(n);
            }
        }
        masses.sort(Collections.reverseOrder());
        s.masses = masses.size();
        for (int m : masses) {
            if (m >= 10) s.masses10++;
            if (m >= 100) s.masses100++;
        }
        s.largest = (masses.isEmpty()) ? 0 : masses.get(0);
        for (Region r : map.getRegions()) {
            if (r.getType() == Region.RegionType.LAND) s.landRegions++;
        }
        // The start lanes: FreeCol's start tiles of each row.
        final List<Tile> east = new ArrayList<>(), west = new ArrayList<>();
        map.collectStartingTiles(east, west);
        s.startRowsEast = east.size();
        s.startRowsWest = west.size();
        final Set<Integer> eastRows = new HashSet<>(), westRows = new HashSet<>();
        final List<Integer> gapE = new ArrayList<>(), gapW = new ArrayList<>();
        for (Tile t : east) {
            eastRows.add(t.getY());
            if (eastLand[t.getY()] >= 0) gapE.add(t.getX() - eastLand[t.getY()] - 1);
        }
        for (Tile t : west) {
            westRows.add(t.getY());
            if (westLand[t.getY()] >= 0) gapW.add(westLand[t.getY()] - t.getX() - 1);
        }
        for (int y = 0; y < h; y++) {
            final Tile t = map.getTile(w / 2, y);
            if (map.isPolar(t)) continue;
            if (!eastRows.contains(y)) s.rowsWithoutEast.add(y);
            if (!westRows.contains(y)) s.rowsWithoutWest.add(y);
        }
        Collections.sort(gapE);
        Collections.sort(gapW);
        s.gapEastMedian = (gapE.isEmpty()) ? -1 : gapE.get(gapE.size() / 2);
        s.gapEastMin = (gapE.isEmpty()) ? -1 : gapE.get(0);
        s.gapWestMedian = (gapW.isEmpty()) ? -1 : gapW.get(gapW.size() / 2);
        s.gapWestMin = (gapW.isEmpty()) ? -1 : gapW.get(0);
        // Our ship: the Dutch start tile, and its nearest land.
        s.shipToLand = -1;
        for (Player p : map.getGame().getLiveEuropeanPlayerList()) {
            if (!"model.nation.dutch".equals(p.getNationId())) continue;
            for (Unit u : p.getUnitSet()) {
                final Tile st = u.getTile();
                if (st == null) continue;
                int d = Integer.MAX_VALUE;
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        if (land[x][y]) d = Math.min(d,
                            st.getDistanceTo(map.getTile(x, y)));
                    }
                }
                s.shipToLand = d;
                break;
            }
        }
        for (Player p : map.getGame().getLiveNativePlayerList()) {
            s.villages += p.getSettlementList().size();
        }
        return s;
    }

    private static int baseOf(String id) {
        switch (id) {
        case "model.tile.tundra": case "model.tile.borealForest": return 0;
        case "model.tile.desert": case "model.tile.scrubForest": return 1;
        case "model.tile.plains": case "model.tile.mixedForest": return 2;
        case "model.tile.prairie": case "model.tile.broadleafForest": return 3;
        case "model.tile.grassland": case "model.tile.coniferForest": return 4;
        case "model.tile.savannah": case "model.tile.tropicalForest": return 5;
        case "model.tile.marsh": case "model.tile.wetlandForest": return 6;
        case "model.tile.swamp": case "model.tile.rainForest": return 7;
        default: return -1;
        }
    }

    private static String mix(Stats s) {
        int n = 0;
        for (int v : s.base) n += v;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < BASE.length; i++) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(BASE[i]).append(' ').append(pct(s.base[i], n));
        }
        return sb.toString();
    }

    private static String pct(int a, int b) {
        return String.format(Locale.ROOT, "%.1f", (b == 0) ? 0.0 : 100.0 * a / b);
    }

    private static String ratio(int a, int b) {
        return String.format(Locale.ROOT, "%.2f", (b == 0) ? 0.0 : (double)a / b);
    }

    private interface Field { double of(Stats s); }

    private static String mmm(List<Stats> all, Field f, String fmt) {
        List<Double> l = new ArrayList<>();
        for (Stats s : all) l.add(f.of(s));
        Collections.sort(l);
        final double med = (l.size() % 2 == 1) ? l.get(l.size() / 2)
            : (l.get(l.size() / 2 - 1) + l.get(l.size() / 2)) / 2.0;
        return String.format(Locale.ROOT, fmt + "/" + fmt + "/" + fmt,
            l.get(0), med, l.get(l.size() - 1));
    }

    private static List<String> summarize(String code, String top, String size,
                                          int mean, List<Stats> all) {
        final List<String> ret = new ArrayList<>();
        final int[] orig = ORIGINAL.get(code);
        double scale = 1.0;
        if (!size.isEmpty()) {
            String[] wh = size.split("x");
            scale = Integer.parseInt(wh[0]) * Integer.parseInt(wh[1])
                / (double)(OriginalWorld.ORIGINAL_WIDTH * OriginalWorld.ORIGINAL_HEIGHT);
        }
        String origText = "not played";
        if (orig != null) {
            StringBuilder sb = new StringBuilder();
            for (int v : orig) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(v);
                if (scale != 1.0) sb.append(" (").append(Math.round(v * scale)).append(')');
            }
            origText = sb.toString();
        }
        String title = ("as-is".equals(code)) ? "the rules' own options"
            : OriginalWorldSettings.ofCode(code).toString();
        ret.add("# " + top + " " + size + " " + title + ", " + all.size()
            + " maps; mean land " + mean + "; Roger's games: " + origText);
        ret.add("#   land min/med/max " + mmm(all, s -> s.land, "%.0f")
            + " | masses " + mmm(all, s -> s.masses, "%.0f")
            + ", >=10 " + mmm(all, s -> s.masses10, "%.0f")
            + ", >=100 " + mmm(all, s -> s.masses100, "%.0f")
            + ", largest % " + mmm(all, s -> 100.0 * s.largest / Math.max(1, s.land), "%.0f")
            + " | coast/land " + mmm(all, s -> (double)s.coast / Math.max(1, s.land), "%.2f"));
        ret.add("#   river % " + mmm(all, s -> 100.0 * s.river / Math.max(1, s.land), "%.1f")
            + ", hills % " + mmm(all, s -> 100.0 * s.hills / Math.max(1, s.land), "%.1f")
            + ", mountains % " + mmm(all, s -> 100.0 * s.mountains / Math.max(1, s.land), "%.1f")
            + ", forest % " + mmm(all, s -> 100.0 * s.forested / Math.max(1, s.land - s.hills - s.mountains), "%.0f")
            + ", arctic off the poles " + mmm(all, s -> s.arctic, "%.0f")
            + ", lakes " + mmm(all, s -> s.lakes, "%.0f"));
        ret.add("#   Pacific tiles " + mmm(all, s -> s.pacific, "%.0f")
            + " in rows " + mmm(all, s -> s.pacificRows, "%.0f")
            + ", land rows without Pacific (all maps) " + sum(all, s -> s.rowsWithoutPacific.size())
            + " | high seas " + mmm(all, s -> s.highSeas, "%.0f")
            + ", start rows E " + mmm(all, s -> s.startRowsEast, "%.0f")
            + " W " + mmm(all, s -> s.startRowsWest, "%.0f")
            + ", rows without a lane E/W (all maps) " + sum(all, s -> s.rowsWithoutEast.size())
            + "/" + sum(all, s -> s.rowsWithoutWest.size()));
        ret.add("#   gap land->east lane med " + mmm(all, s -> s.gapEastMedian, "%.0f")
            + " min " + mmm(all, s -> s.gapEastMin, "%.0f")
            + ", west med " + mmm(all, s -> s.gapWestMedian, "%.0f")
            + " min " + mmm(all, s -> s.gapWestMin, "%.0f")
            + ", land columns " + mmm(all, s -> s.landWest, "%.0f") + " to " + mmm(all, s -> s.landEast, "%.0f")
            + ", ship -> land " + mmm(all, s -> s.shipToLand, "%.0f"));
        int[] bands = new int[7];
        int landAll = 0;
        double[] base = new double[BASE.length];
        for (Stats s : all) {
            for (int i = 0; i < 7; i++) bands[i] += s.bands[i];
            landAll += s.land;
            int n = 0;
            for (int v : s.base) n += v;
            for (int i = 0; i < BASE.length; i++) base[i] += 100.0 * s.base[i] / Math.max(1, n) / all.size();
        }
        StringBuilder sb = new StringBuilder("#   land per seventh of the rows (N->S) %:");
        for (int b : bands) sb.append(String.format(Locale.ROOT, " %.1f", 100.0 * b / Math.max(1, landAll)));
        sb.append(" | villages ").append(mmm(all, s -> s.villages, "%.0f"))
            .append(", land regions ").append(mmm(all, s -> s.landRegions, "%.0f"))
            .append(", rumours ").append(mmm(all, s -> s.rumours, "%.0f"));
        ret.add(sb.toString());
        sb = new StringBuilder("#   mix of the flat land % (mean):");
        for (int i = 0; i < BASE.length; i++) sb.append(String.format(Locale.ROOT, " %s %.1f", BASE[i], base[i]));
        ret.add(sb.toString());
        return ret;
    }

    private static int sum(List<Stats> all, Field f) {
        int n = 0;
        for (Stats s : all) n += (int)f.of(s);
        return n;
    }
}
