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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import net.sf.freecol.common.io.FreeColRules;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.OriginalWorld;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Settlement;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Topology;
import net.sf.freecol.common.option.OriginalWorldSettings;
import net.sf.freecol.server.generator.OriginalNatives;
import net.sf.freecol.server.generator.SimpleMapGenerator;


/**
 * Measures the natives of new maps of the "levi" rules (part P2,
 * {@link OriginalNatives}) for each world setting, topology and level,
 * made as the Classic UI makes them ({@link WorldSettingsCensus#generate}):
 * villages in all and per tribe, capitals, the land the natives own and
 * whether any of it lies outside a home radius, the natives' people,
 * the settlement sizes, the distance to the nearest other village,
 * where the tribes live (west, east, north, south, at the Pacific),
 * the sites added to tribes with too few, FreeCol's fallback, and
 * whether the map is the same on every level.
 *
 * <p>Usage (in the worktree, after compiling the tests into build/):
 * <pre>
 *   java -cp "build;jars/*" net.sf.freecol.tools.NativeLandCensus
 *       OUT.tsv [--seeds N] [--first S] [--topology square,isometric]
 *       [--settings NNgn,...] [--difficulty veryEasy,medium] [--expect]
 * </pre>
 * {@code --expect} ends with exit code 1 if a map breaks the rules: a
 * tribe outside 6 to 21 villages, not one capital each, more than 84,
 * two villages next to each other, native land outside a home radius,
 * a settlement not of its type's size, maps that differ between the
 * levels of a seed, a site that could not be given, a retired tribe,
 * the Inca or Aztec with a village outside the west, the Iroquois or
 * Arawak outside the east.
 */
public class NativeLandCensus {

    /** The tribes in the original's save order (NAMES.TXT @TRIBES). */
    private static final String[] TRIBES = { "inca", "aztec", "arawak",
        "iroquois", "cherokee", "apache", "sioux", "tupi" };

    /** Roger's games in the original: land / villages per setting. */
    private static final java.util.Map<String, String> ORIGINAL
        = new LinkedHashMap<>();
    static {
        ORIGINAL.put("KIgn", "«Neue Welt» 427/39, 469/41, 650/51");
        ORIGINAL.put("KNgn", "673/52, 732/55");
        ORIGINAL.put("NIgn", "698/56");
        ORIGINAL.put("NNgn", "872/61, 901/60");
        ORIGINAL.put("GNgn", "1079/79, 1184/84");
        ORIGINAL.put("NCgn", "1191/82");
        ORIGINAL.put("GCwf", "1373/81");
    }

    /** The generator's loggers, held: java.util.logging keeps loggers weakly. */
    private static final List<Logger> LOGGERS = new ArrayList<>();

    private static final String DEFAULT_SETTINGS
        = "KIgn,KNgn,KCgn,NIgn,NNgn,NCgn,GIgn,GNgn,GCgn,GCwf";


    /** The numbers of one map. */
    public static final class Stats {
        public int land, villages, target, nativeLand, outside, units,
            wrongSize, adjacent, capitalsWrong, moved, topUps, missing, fallback,
            retired, westOut, eastOut;
        public boolean incaNorth, aztecNorth, iroquoisNorth, arawakNorth;
        public final int[] perTribe = new int[TRIBES.length];
        /** Share of the land west of the tribe's villages (global). */
        public final double[] xShare = new double[TRIBES.length];
        /** Share of the land north of the tribe's villages (global). */
        public final double[] yShare = new double[TRIBES.length];
        public int incaPacific = -1;
        public final int[] nearest = new int[7]; // d = 1 .. 6+, [0] unused
        public String fingerprint = "";
    }


    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("usage: NativeLandCensus OUT.tsv [--seeds N]"
                + " [--first S] [--topology square,isometric]"
                + " [--settings NNgn,...] [--difficulty veryEasy,medium]"
                + " [--expect]");
            System.exit(2);
        }
        final File out = new File(args[0]);
        int seeds = 40, first = 1;
        boolean expect = false;
        String[] topologies = { "square", "isometric" };
        String[] settings = DEFAULT_SETTINGS.split(",");
        String[] levels = { "veryEasy", "medium" };
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
            case "--seeds": seeds = Integer.parseInt(args[++i]); break;
            case "--first": first = Integer.parseInt(args[++i]); break;
            case "--topology": topologies = args[++i].split(","); break;
            case "--settings": settings = args[++i].split(","); break;
            case "--difficulty": levels = args[++i].split(","); break;
            case "--expect": expect = true; break;
            default:
                System.err.println("unknown argument " + args[i]);
                System.exit(2);
            }
        }
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, StandardCharsets.UTF_8.name()));
        Logger.getLogger("").setLevel(Level.SEVERE);
        for (Handler h : Logger.getLogger("").getHandlers()) {
            h.setLevel(Level.SEVERE);
        }
        final List<String> logLines = new ArrayList<>();
        final Handler catcher = new Handler() {
                @Override public void publish(LogRecord r) {
                    logLines.add(r.getMessage());
                }
                @Override public void flush() {}
                @Override public void close() {}
            };
        catcher.setLevel(Level.INFO);
        for (Class<?> c : new Class<?>[] { OriginalNatives.class,
                                           SimpleMapGenerator.class }) {
            final Logger l = Logger.getLogger(c.getName());
            l.setLevel(Level.INFO);
            l.addHandler(catcher);
            LOGGERS.add(l);
        }
        FreeColRules.loadRules();
        net.sf.freecol.common.i18n.Messages.loadMessageBundle(Locale.US);
        final List<String> violations = new ArrayList<>();
        try (PrintWriter pw = new PrintWriter(out, StandardCharsets.UTF_8.name())) {
            pw.println("setting\ttopology\tlevel\tseed\tland\ttarget\tvillages"
                + "\tnativeLand%\toutside\tunits\twrongSize\tadjacent"
                + "\tcapitalsWrong\ttopUps\tmissing\tfallback\tretired"
                + "\tnearest d2/d3/d4/d5/d6+\tincaN/aztecN/iroqN/arawakN"
                + "\tincaPacific\tper tribe (" + String.join(",", TRIBES)
                + ")\tx share %\ty share %");
            final List<String> summary = new ArrayList<>();
            for (String top : topologies) {
                final Topology topology = ("isometric".equals(top))
                    ? Topology.ISOMETRIC : Topology.SQUARE;
                for (String code : settings) {
                    final OriginalWorldSettings world
                        = OriginalWorldSettings.ofCode(code);
                    final java.util.Map<String, List<Stats>> byLevel
                        = new LinkedHashMap<>();
                    for (int seed = first; seed < first + seeds; seed++) {
                        String print = null;
                        for (String level : levels) {
                            logLines.clear();
                            final Map map = WorldSettingsCensus.generate(world,
                                "model.difficulty." + level, topology, seed);
                            final Stats s = measure(map, logLines);
                            byLevel.computeIfAbsent(level,
                                k -> new ArrayList<>()).add(s);
                            pw.println(row(code, top, level, seed, s));
                            pw.flush();
                            final String what = code + " " + top + " " + level
                                + " seed " + seed;
                            check(what, s, violations);
                            if (print == null) {
                                print = s.fingerprint;
                            } else if (!print.equals(s.fingerprint)) {
                                violations.add(what + ": not the map of "
                                    + levels[0]);
                            }
                        }
                    }
                    for (String level : levels) {
                        final List<String> l = summarize(code, top, level,
                            byLevel.get(level));
                        summary.addAll(l);
                        for (String line : l) System.out.println(line);
                    }
                }
            }
            for (String l : summary) pw.println(l);
            final String verdict = "# rule violations: " + violations.size();
            pw.println(verdict);
            System.out.println(verdict);
            for (String v : violations.subList(0, Math.min(50,
                                                           violations.size()))) {
                pw.println("#   " + v);
                System.out.println("#   " + v);
            }
        }
        if (expect && !violations.isEmpty()) System.exit(1);
    }

    private static void check(String what, Stats s, List<String> v) {
        for (int i = 0; i < TRIBES.length; i++) {
            if (s.perTribe[i] < 6 || s.perTribe[i] > 21) {
                v.add(what + ": " + TRIBES[i] + " has " + s.perTribe[i]);
            }
        }
        if (s.villages > 84) v.add(what + ": " + s.villages + " villages");
        if (s.capitalsWrong > 0) v.add(what + ": capitals wrong for "
            + s.capitalsWrong + " tribes");
        if (s.adjacent > 0) v.add(what + ": " + s.adjacent + " adjacent");
        if (s.outside > 0) v.add(what + ": " + s.outside
            + " native tiles outside a home radius");
        if (s.wrongSize > 0) v.add(what + ": " + s.wrongSize
            + " settlements not of their size");
        if (s.missing > 0) v.add(what + ": " + s.missing + " sites missing");
        if (s.retired > 0) v.add(what + ": " + s.retired + " tribes retired");
        if (s.westOut > 0) v.add(what + ": " + s.westOut
            + " Inca/Aztec villages outside the west");
        if (s.eastOut > 0) v.add(what + ": " + s.eastOut
            + " Iroquois/Arawak villages outside the east");
    }

    private static int tribeIndex(Player p) {
        final String id = p.getNationId().substring("model.nation.".length());
        for (int i = 0; i < TRIBES.length; i++) {
            if (TRIBES[i].equals(id)) return i;
        }
        return -1;
    }

    /**
     * Measure one map.
     *
     * @param map The {@code Map}.
     * @param log The generator's log lines of this map.
     * @return Its {@code Stats}.
     */
    public static Stats measure(Map map, List<String> log) {
        final Stats s = new Stats();
        final int w = map.getWidth(), h = map.getHeight();
        s.land = OriginalWorld.countLand(map);
        s.target = OriginalNatives.getTarget(s.land, TRIBES.length, 6, 21, 84);
        final OriginalNatives.Bands bands = new OriginalNatives.Bands(map);
        final int[][] pacific = OriginalNatives.getPacificDistances(map);
        // The land's x and y order, as the original's census counts it.
        final List<Integer> xs = new ArrayList<>(), ys = new ArrayList<>();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                final Tile t = map.getTile(x, y);
                if (t.isLand() && !map.isPolar(t)) {
                    xs.add(x);
                    ys.add(y);
                    if (t.getOwner() != null && t.getOwner().isIndian()) {
                        s.nativeLand++;
                        final Settlement st = t.getOwningSettlement();
                        if (st == null
                            || t.getDistanceTo(st.getTile()) > st.getRadius()) {
                            s.outside++;
                        }
                    }
                }
            }
        }
        Collections.sort(xs);
        Collections.sort(ys);
        final List<IndianSettlement> all = new ArrayList<>();
        final StringBuilder print = new StringBuilder();
        for (Player p : map.getGame().getLiveNativePlayerList()) {
            final int i = tribeIndex(p);
            final List<IndianSettlement> list = p.getIndianSettlementList();
            all.addAll(list);
            s.units += p.getUnitCount();
            int capitals = 0;
            double xsum = 0, ysum = 0, north = 0;
            final List<Integer> pac = new ArrayList<>();
            for (IndianSettlement is : list) {
                if (is.isCapital()) capitals++;
                final Tile t = is.getTile();
                print.append(p.getNationId()).append(t.getX()).append(',')
                    .append(t.getY()).append(is.isCapital() ? "C " : " ");
                if (is.getUnitCount() != is.getType().getMinimumSize()
                    || is.getType().getMinimumSize()
                        != is.getType().getMaximumSize()) s.wrongSize++;
                xsum += share(xs, t.getX());
                ysum += share(ys, t.getY());
                if (bands.isNorth(t)) north++;
                pac.add(pacific[t.getX()][t.getY()]);
                final OriginalNatives.Band band
                    = OriginalNatives.getBand(p.getNationId());
                if (band == OriginalNatives.Band.WEST && !bands.isWest(t)) {
                    s.westOut++;
                }
                if (band == OriginalNatives.Band.EAST && !bands.isEast(t)) {
                    s.eastOut++;
                }
            }
            if (capitals != 1) s.capitalsWrong++;
            if (i < 0) continue;
            s.perTribe[i] = list.size();
            if (!list.isEmpty()) {
                s.xShare[i] = 100.0 * xsum / list.size();
                s.yShare[i] = 100.0 * ysum / list.size();
                final boolean n = north * 2 > list.size();
                switch (TRIBES[i]) {
                case "inca": s.incaNorth = n; break;
                case "aztec": s.aztecNorth = n; break;
                case "iroquois": s.iroquoisNorth = n; break;
                case "arawak": s.arawakNorth = n; break;
                default: break;
                }
                if ("inca".equals(TRIBES[i])) {
                    Collections.sort(pac);
                    s.incaPacific = pac.get(pac.size() / 2);
                }
            }
        }
        s.retired = (int)map.getGame().getPlayers(p -> p.isIndian() && p.isDead()).count();
        s.villages = all.size();
        for (IndianSettlement a : all) {
            int d = Integer.MAX_VALUE;
            for (IndianSettlement b : all) {
                if (a != b) d = Math.min(d, a.getTile().getDistanceTo(b.getTile()));
            }
            if (d <= 1) s.adjacent++;
            if (d != Integer.MAX_VALUE) s.nearest[Math.min(6, Math.max(1, d))]++;
        }
        for (String l : log) {
            if (l == null) continue;
            if (l.startsWith("Original natives:")) {
                s.moved += number(l, "moved ");
                s.topUps += number(l, "added ");
                s.missing += number(l, "missing ");
            } else if (l.startsWith("Fallback settlement site")) {
                s.fallback++;
            }
        }
        s.fingerprint = print.toString();
        return s;
    }

    private static int number(String l, String key) {
        final int i = l.indexOf(key);
        if (i < 0) return 0;
        int j = i + key.length(), n = 0;
        while (j < l.length() && Character.isDigit(l.charAt(j))) {
            n = n * 10 + (l.charAt(j++) - '0');
        }
        return n;
    }

    /** The share of the sorted values below v, counting v half. */
    private static double share(List<Integer> sorted, int v) {
        int lo = 0;
        while (lo < sorted.size() && sorted.get(lo) < v) lo++;
        int hi = lo;
        while (hi < sorted.size() && sorted.get(hi) == v) hi++;
        return (sorted.isEmpty()) ? 0.5 : (lo + hi) / 2.0 / sorted.size();
    }

    private static String row(String code, String top, String level,
                              int seed, Stats s) {
        final StringBuilder per = new StringBuilder(),
            xs = new StringBuilder(), ys = new StringBuilder();
        for (int i = 0; i < TRIBES.length; i++) {
            if (i > 0) {
                per.append(',');
                xs.append(',');
                ys.append(',');
            }
            per.append(s.perTribe[i]);
            xs.append(Math.round(s.xShare[i]));
            ys.append(Math.round(s.yShare[i]));
        }
        return code + "\t" + top + "\t" + level + "\t" + seed + "\t" + s.land
            + "\t" + s.target + "\t" + s.villages
            + "\t" + pct(s.nativeLand, s.land) + "\t" + s.outside
            + "\t" + s.units + "\t" + s.wrongSize + "\t" + s.adjacent
            + "\t" + s.capitalsWrong + "\t" + s.topUps + "\t" + s.missing
            + "\t" + s.fallback + "\t" + s.retired
            + "\t" + s.nearest[2] + "/" + s.nearest[3] + "/" + s.nearest[4]
            + "/" + s.nearest[5] + "/" + s.nearest[6]
            + "\t" + yn(s.incaNorth) + yn(s.aztecNorth) + yn(s.iroquoisNorth)
            + yn(s.arawakNorth) + "\t" + s.incaPacific + "\t" + per
            + "\t" + xs + "\t" + ys;
    }

    private static String yn(boolean b) {
        return (b) ? "N" : "S";
    }

    private static String pct(int a, int b) {
        return String.format(Locale.ROOT, "%.1f", (b == 0) ? 0.0 : 100.0 * a / b);
    }

    private interface Field { double of(Stats s); }

    private static String mmm(List<Stats> all, Field f, String fmt) {
        final List<Double> l = new ArrayList<>();
        for (Stats s : all) l.add(f.of(s));
        Collections.sort(l);
        final double med = (l.size() % 2 == 1) ? l.get(l.size() / 2)
            : (l.get(l.size() / 2 - 1) + l.get(l.size() / 2)) / 2.0;
        return String.format(Locale.ROOT, fmt + "/" + fmt + "/" + fmt,
            l.get(0), med, l.get(l.size() - 1));
    }

    private static int sum(List<Stats> all, Field f) {
        int n = 0;
        for (Stats s : all) n += (int)f.of(s);
        return n;
    }

    private static List<String> summarize(String code, String top,
                                          String level, List<Stats> all) {
        final List<String> ret = new ArrayList<>();
        final String orig = ORIGINAL.getOrDefault(code, "not played");
        ret.add("# " + top + " " + level + " "
            + OriginalWorldSettings.ofCode(code) + " (" + code + "), "
            + all.size() + " maps; the original (land/villages): " + orig);
        ret.add("#   land " + mmm(all, s -> s.land, "%.0f")
            + " | villages " + mmm(all, s -> s.villages, "%.0f")
            + " (target " + mmm(all, s -> s.target, "%.0f") + ")"
            + " | land per village " + mmm(all,
                s -> (double)s.land / Math.max(1, s.villages), "%.1f")
            + " | native land % " + mmm(all,
                s -> 100.0 * s.nativeLand / Math.max(1, s.land), "%.1f")
            + " | native units " + mmm(all, s -> s.units, "%.0f"));
        final StringBuilder sb = new StringBuilder("#   per tribe min/med/max:");
        for (int i = 0; i < TRIBES.length; i++) {
            final int k = i;
            sb.append(' ').append(TRIBES[i]).append(' ')
                .append(mmm(all, s -> s.perTribe[k], "%.0f"));
        }
        ret.add(sb.toString());
        final StringBuilder xb = new StringBuilder(
            "#   land west of the villages % (min/med/max):");
        for (int i = 0; i < TRIBES.length; i++) {
            final int k = i;
            xb.append(' ').append(TRIBES[i]).append(' ')
                .append(mmm(all, s -> s.xShare[k], "%.0f"));
        }
        ret.add(xb.toString());
        int inN = 0, azN = 0, iqN = 0, arN = 0, opposite = 0, oppositeE = 0;
        for (Stats s : all) {
            if (s.incaNorth) inN++;
            if (s.aztecNorth) azN++;
            if (s.iroquoisNorth) iqN++;
            if (s.arawakNorth) arN++;
            if (s.incaNorth != s.aztecNorth) opposite++;
            if (s.iroquoisNorth != s.arawakNorth) oppositeE++;
        }
        int[] near = new int[7];
        for (Stats s : all) for (int i = 1; i < 7; i++) near[i] += s.nearest[i];
        final int nearAll = near[1] + near[2] + near[3] + near[4] + near[5] + near[6];
        ret.add("#   north: Inca " + inN + ", Aztec " + azN + ", Iroquois "
            + iqN + ", Arawak " + arN + " of " + all.size()
            + "; Inca and Aztec apart " + opposite + ", Iroquois and Arawak apart "
            + oppositeE + " | Inca's median distance to the Pacific "
            + mmm(all, s -> s.incaPacific, "%.0f")
            + " | nearest village d1/d2/d3/d4/d5/d6+ % "
            + String.format(Locale.ROOT, "%.0f/%.0f/%.0f/%.0f/%.0f/%.0f",
                100.0 * near[1] / Math.max(1, nearAll),
                100.0 * near[2] / Math.max(1, nearAll),
                100.0 * near[3] / Math.max(1, nearAll),
                100.0 * near[4] / Math.max(1, nearAll),
                100.0 * near[5] / Math.max(1, nearAll),
                100.0 * near[6] / Math.max(1, nearAll)));
        ret.add("#   all maps: capitals wrong " + sum(all, s -> s.capitalsWrong)
            + ", adjacent " + sum(all, s -> s.adjacent)
            + ", native land outside a home radius " + sum(all, s -> s.outside)
            + ", wrong sizes " + sum(all, s -> s.wrongSize)
            + ", spaced sites moved " + sum(all, s -> s.moved)
            + ", sites added " + sum(all, s -> s.topUps)
            + " (maps with any " + sum(all, s -> (s.topUps > 0) ? 1 : 0) + ")"
            + ", missing " + sum(all, s -> s.missing)
            + ", FreeCol fallback " + sum(all, s -> s.fallback)
            + ", retired " + sum(all, s -> s.retired)
            + ", Inca/Aztec outside the west " + sum(all, s -> s.westOut)
            + ", Iroquois/Arawak outside the east " + sum(all, s -> s.eastOut)
            + ", below the target " + sum(all, s -> (s.villages < s.target) ? 1 : 0));
        return ret;
    }
}
