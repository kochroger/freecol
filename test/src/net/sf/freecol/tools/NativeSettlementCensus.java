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
import java.util.Random;

import net.sf.freecol.FreeCol;
import net.sf.freecol.common.io.FreeColRules;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Nation;
import net.sf.freecol.common.model.NationOptions;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Topology;
import net.sf.freecol.common.option.MapGeneratorOptions;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.server.generator.SimpleMapGenerator;
import net.sf.freecol.server.model.ServerGame;
import net.sf.freecol.server.model.ServerPlayer;


/**
 * Counts the native settlements of every tribe on new maps of the
 * Classic UI's rules ("levi"), the way a new game makes them: the real
 * server map generator, the topology's map options
 * ({@link MapGeneratorOptions#applyTopologyDefaults}), the four
 * Europeans and the eight tribes as players.
 *
 * Usage (in the worktree, after compiling the tests into build/):
 * <pre>
 *   java -cp "build;jars/*" net.sf.freecol.tools.NativeSettlementCensus
 *       OUT.tsv [--seeds N] [--rules levi]
 *       [--difficulty model.difficulty.veryEasy,...]
 *       [--topology square,isometric]
 * </pre>
 * One TSV row per map, then a summary per topology and difficulty: for
 * every tribe the minimum, median and maximum of its settlements and the
 * maps on which it has none.
 */
public class NativeSettlementCensus {

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("usage: NativeSettlementCensus OUT.tsv"
                + " [--seeds N] [--rules R] [--difficulty D,...]"
                + " [--topology square,isometric]");
            System.exit(2);
        }
        final File out = new File(args[0]);
        int seeds = 40;
        String rules = "levi";
        String[] difficulties = { "model.difficulty.veryEasy",
                                  "model.difficulty.medium" };
        String[] topologies = { "square", "isometric" };
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
            case "--seeds": seeds = Integer.parseInt(args[++i]); break;
            case "--rules": rules = args[++i]; break;
            case "--difficulty": difficulties = args[++i].split(","); break;
            case "--topology": topologies = args[++i].split(","); break;
            default:
                System.err.println("unknown argument " + args[i]);
                System.exit(2);
            }
        }
        java.util.logging.Logger.getLogger("").setLevel(java.util.logging.Level.WARNING);
        // The fallback sites of tribes without one (INFO) go to stderr.
        java.util.logging.Logger.getLogger(SimpleMapGenerator.class.getName())
            .setLevel(java.util.logging.Level.INFO);
        FreeColRules.loadRules();
        net.sf.freecol.common.i18n.Messages.loadMessageBundle(java.util.Locale.US);
        boolean allTribesEverywhere = true;
        try (PrintWriter pw = new PrintWriter(out, StandardCharsets.UTF_8.name())) {
            pw.println("topology\tdifficulty\tseed\tsize\tsettlements\tzero\tper-tribe");
            List<String> summary = new ArrayList<>();
            for (String top : topologies) {
                final Topology topology = ("isometric".equals(top))
                    ? Topology.ISOMETRIC : Topology.SQUARE;
                for (String difficulty : difficulties) {
                    final java.util.Map<String, List<Integer>> counts
                        = new LinkedHashMap<>();
                    final java.util.Map<String, List<Integer>> zeros
                        = new LinkedHashMap<>();
                    for (int seed = 1; seed <= seeds; seed++) {
                        final Map map = generate(rules, difficulty,
                                                 topology, seed);
                        final Game game = map.getGame();
                        StringBuilder per = new StringBuilder();
                        int total = 0;
                        List<String> none = new ArrayList<>();
                        for (Player p : game.getLiveNativePlayerList()) {
                            final String tribe = suffix(p.getNationId());
                            final int n = p.getSettlementList().size();
                            total += n;
                            counts.computeIfAbsent(tribe,
                                k -> new ArrayList<>()).add(n);
                            if (n == 0) {
                                none.add(tribe);
                                zeros.computeIfAbsent(tribe,
                                    k -> new ArrayList<>()).add(seed);
                            }
                            if (per.length() > 0) per.append(',');
                            per.append(tribe).append('=').append(n);
                        }
                        if (!none.isEmpty()) allTribesEverywhere = false;
                        pw.println(top + "\t" + suffix(difficulty) + "\t"
                            + seed + "\t" + map.getWidth() + "x"
                            + map.getHeight() + "\t" + total + "\t"
                            + String.join(",", none) + "\t" + per);
                    }
                    summary.add("# " + top + " " + suffix(difficulty)
                        + ", " + seeds + " seeds: tribe min/median/max"
                        + " (seeds without a settlement)");
                    for (java.util.Map.Entry<String, List<Integer>> e
                             : counts.entrySet()) {
                        List<Integer> l = new ArrayList<>(e.getValue());
                        Collections.sort(l);
                        final double median = (l.size() % 2 == 1)
                            ? l.get(l.size() / 2)
                            : (l.get(l.size() / 2 - 1) + l.get(l.size() / 2)) / 2.0;
                        List<Integer> z = zeros.get(e.getKey());
                        summary.add("#   " + e.getKey() + "\t" + l.get(0)
                            + "/" + median + "/" + l.get(l.size() - 1)
                            + "\t" + ((z == null) ? "-" : z.toString()));
                    }
                }
            }
            for (String s : summary) {
                pw.println(s);
                System.out.println(s);
            }
            final String verdict = "# every tribe has a settlement on every map: "
                + allTribesEverywhere;
            pw.println(verdict);
            System.out.println(verdict);
        }
    }

    /**
     * Generate a map as a new game of the Classic UI does.
     *
     * @param rules The rules identifier.
     * @param difficulty The difficulty level identifier.
     * @param topology The {@code Topology} to use.
     * @param seed The random seed.
     * @return The new {@code Map}.
     */
    static Map generate(String rules, String difficulty, Topology topology,
                        long seed) {
        Topology.setCurrent(topology);
        Specification spec = FreeCol.loadSpecification(
            FreeColRules.getFreeColRulesFile(rules), null, difficulty);
        spec.setFile(MapGeneratorOptions.IMPORT_FILE, null);
        MapGeneratorOptions.applyTopologyDefaults(spec.getMapGeneratorOptions());
        Game game = new ServerGame(spec);
        NationOptions nationOptions = new NationOptions(spec);
        game.setNationOptions(nationOptions);
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

    private static String suffix(String id) {
        return id.substring(id.lastIndexOf('.') + 1);
    }
}
