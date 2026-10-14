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

package net.sf.freecol.server.generator;

import static net.sf.freecol.common.util.RandomUtils.randomInt;
import static net.sf.freecol.common.util.RandomUtils.randomShuffle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Logger;

import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.option.IntegerOption;
import net.sf.freecol.common.option.MapGeneratorOptions;
import net.sf.freecol.common.option.OptionGroup;
import net.sf.freecol.common.util.LogBuilder;


/**
 * The villages of the original game's New World, as a new map of the
 * "levi" rules gets them (part P2; the rules' options
 * {@link MapGeneratorOptions#NATIVE_SETTLEMENT_SPACING} and the three
 * after it switch this on).  FreeCol spaces its sites by the level's
 * settlement number (10 to 5 tiles: 12 to 31 villages on our map) and
 * gives each tribe the sites inside its fixed region of the nine
 * thirds of the land.  The original:
 *
 * <ul>
 *   <li><b>How many.</b> As many as that much land carries, the same
 *       on every level: Roger's 13 new worlds of 2026-10-13, each saved
 *       in its first turn ({@code prep2/W-landtests.md}; land = rows 1
 *       to 70 without arctic):
 *       «Neue Welt» 427 / 469 / 650 land tiles, 39 / 41 / 51 villages;
 *       LANDMASSE Klein 673 / 732, 52 / 55; LANDFORM Inselgruppe 698, 56;
 *       everything Normal 872 / 901, 61 / 60; Groß 1079 / 1184, 79 / 84;
 *       Große Kontinente 1191, 82; Groß + Kontinente + Warm + Feucht
 *       1373, 81.  At most 84 on a map (MAPEDIT.TXT @HELP5; 84 in two of
 *       the games).  {@link #getVillages} goes through the means of these
 *       settings; Roger's rule, 6 to 21 villages for every tribe
 *       (2026-10-13: "fix 6 bis 21 pro Stamm"), comes on top: at least
 *       48 on any map (the original's «Neue Welt» has 39 to 51).</li>
 *   <li><b>Where.</b> In the original's five early random maps read
 *       (COLONY00/01/02/09 and Roger's «Neue Welt» #13 and Inselgruppe
 *       #10) the Inca are always in the west (the share of the land
 *       west of their villages 5 to 29 %), next to the Pacific (their
 *       villages' median distance 1 to 4 tiles), the Aztec in the west
 *       too (10 to 38 %), the Iroquois and the Arawak in the east (59 to
 *       93 %); the Inca and the Aztec in different halves (north and
 *       south) on four maps of five, which half drawn per map, and the
 *       Iroquois and the Arawak too; the other four tribes anywhere
 *       (Cherokee 9 to 63 %, Apache 38 to 97 %, Sioux 34 to 85 %, Tupi
 *       13 to 88 %).  Here: each tribe has a home, the Inca on the
 *       Pacific in the western third of its half of the land, the Aztec
 *       in the western third of the other half, the Iroquois and Arawak
 *       likewise in the eastern thirds, the other four anywhere (each
 *       the farthest from the homes before it of {@link #HOME_CHOICES}
 *       random tiles); a site belongs to the nearest home that may have
 *       it (the Inca and Aztec only sites in the western third of the
 *       land's half, the Iroquois and Arawak only in the eastern
 *       third).</li>
 *   <li><b>Which.</b> FreeCol's spaced sites (every suitable tile in the
 *       generator's random order, first one more than, then
 *       {@link MapGeneratorOptions#NATIVE_SETTLEMENT_SPACING} apart: never
 *       two next to each other, the nearest mostly 3 or 4 away, as in
 *       the original) in that order: first the least number of each
 *       tribe, then the others until the map has its number, no tribe
 *       above the most; a tribe whose land has too few takes the spaced
 *       sites nearest to its home that no tribe has or a tribe with more
 *       than its least has, else new ones at least
 *       {@link #TOP_UP_DISTANCE} from every other (the original's closest
 *       villages are 2 apart).</li>
 * </ul>
 * The halves, the order of the four free tribes and the homes are
 * drawn from the generator's random numbers, the rest follows its
 * shuffled tiles, so a seed fixes the map.
 */
public final class OriginalNatives {

    private static final Logger logger = Logger.getLogger(OriginalNatives.class.getName());

    /** The tribes of the west, the first at the Pacific. */
    public static final List<String> WEST_TRIBES
        = List.of("model.nation.inca", "model.nation.aztec");

    /** The tribes of the east. */
    public static final List<String> EAST_TRIBES
        = List.of("model.nation.iroquois", "model.nation.arawak");

    /**
     * Land tiles and villages of the original's settings (Roger's
     * games, the means of each setting; Klein and Inselgruppe together,
     * as they have the same land): «Neue Welt» (427, 469, 650 / 39, 41,
     * 51), Klein and Inselgruppe (673, 732, 698 / 52, 55, 56), everything
     * Normal (872, 901 / 61, 60), Groß (1079, 1184 / 79, 84), Große
     * Kontinente (1191 / 82).  More land than the last row gives no more
     * villages (1373 / 81).
     */
    private static final double[][] VILLAGES = {
        {  515.3, 43.67 },
        {  701.0, 54.33 },
        {  886.5, 60.5 },
        { 1131.5, 81.5 },
        { 1191.0, 82.0 } };

    /**
     * The least distance of a site added to give a tribe its least
     * number: the original's closest villages are 2 apart (4 pairs of
     * 75 to 84 villages, always inside one tribe).
     */
    public static final int TOP_UP_DISTANCE = 2;

    /**
     * A home is the farthest from the homes before it of this many
     * random tiles of its area (1 would give some tribes almost no
     * land, many would space them evenly; the original's shares are
     * uneven, 1 to 22 villages).
     */
    public static final int HOME_CHOICES = 3;

    /**
     * The Inca's home is at most this far from the Pacific, or as near
     * as its area allows.
     */
    public static final int PACIFIC_HOME_DISTANCE = 2;

    /** Where a tribe may live. */
    public enum Band { WEST, EAST, ANYWHERE }


    private OriginalNatives() {} // Static and nested only.


    /**
     * Do a new map's options ask for the original's natives?
     *
     * @param mgo The map generator options.
     * @return True for the "levi" rules' options.
     */
    public static boolean applies(OptionGroup mgo) {
        return mgo != null
            && mgo.hasOption(MapGeneratorOptions.NATIVE_SETTLEMENT_SPACING,
                             IntegerOption.class)
            && mgo.hasOption(MapGeneratorOptions.NATIVE_SETTLEMENTS_MINIMUM,
                             IntegerOption.class)
            && mgo.hasOption(MapGeneratorOptions.NATIVE_SETTLEMENTS_MAXIMUM,
                             IntegerOption.class)
            && mgo.hasOption(MapGeneratorOptions.NATIVE_SETTLEMENTS_TOTAL,
                             IntegerOption.class);
    }

    /**
     * The band of the land a tribe lives in.
     *
     * @param nationId The tribe's nation identifier.
     * @return Its {@code Band}.
     */
    public static Band getBand(String nationId) {
        return (WEST_TRIBES.contains(nationId)) ? Band.WEST
            : (EAST_TRIBES.contains(nationId)) ? Band.EAST
            : Band.ANYWHERE;
    }

    /**
     * The original's villages on so much land: the line through the
     * means of its settings, as steep below the first as between the
     * first two, no more above the last.
     *
     * @param land The land tiles of the map (outside the polar rows).
     * @return The villages, not rounded.
     */
    public static double getVillages(int land) {
        final int n = VILLAGES.length;
        if (land >= VILLAGES[n - 1][0]) return VILLAGES[n - 1][1];
        int i = 1;
        while (i < n - 1 && land > VILLAGES[i][0]) i++;
        final double[] a = VILLAGES[i - 1], b = VILLAGES[i];
        return Math.max(0.0,
            a[1] + (land - a[0]) * (b[1] - a[1]) / (b[0] - a[0]));
    }

    /**
     * The villages of a new map: {@link #getVillages}, rounded, but at
     * least the least of every tribe, at most the most of every tribe
     * and never more than the map's most.
     *
     * @param land The land tiles of the map (outside the polar rows).
     * @param tribes The number of tribes.
     * @param minimum The least villages of a tribe.
     * @param maximum The most villages of a tribe.
     * @param total The most villages of a map.
     * @return The number of villages.
     */
    public static int getTarget(int land, int tribes, int minimum,
                                int maximum, int total) {
        int n = (int)Math.round(getVillages(land));
        n = Math.max(n, tribes * minimum);
        n = Math.min(n, tribes * maximum);
        return Math.max(0, Math.min(n, total));
    }


    /**
     * The land's halves (north and south, each half of the land) and
     * the thirds of each half (west, middle, east, each a third of the
     * half's land), by the land's own tiles outside the polar rows: a
     * tile's place is the share of the land before its row (column),
     * counting the tiles of its own row (column) half.
     */
    public static final class Bands {

        private final Map map;
        private final double[] rowShare;
        private final double[][] columnShare;

        /**
         * Measure a map's land.
         *
         * @param map The {@code Map}.
         */
        public Bands(Map map) {
            this.map = map;
            final int w = map.getWidth(), h = map.getHeight();
            final int[] rows = new int[h];
            int land = 0;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (isLand(map.getTile(x, y))) {
                        rows[y]++;
                        land++;
                    }
                }
            }
            rowShare = new double[h];
            int before = 0;
            for (int y = 0; y < h; y++) {
                rowShare[y] = (land == 0) ? 0.5
                    : (before + rows[y] / 2.0) / land;
                before += rows[y];
            }
            // The columns of each half.
            final int[][] cols = new int[2][w];
            final int[] halfLand = new int[2];
            for (int y = 0; y < h; y++) {
                final int half = (rowShare[y] < 0.5) ? 0 : 1;
                for (int x = 0; x < w; x++) {
                    if (isLand(map.getTile(x, y))) {
                        cols[half][x]++;
                        halfLand[half]++;
                    }
                }
            }
            columnShare = new double[2][w];
            for (int half = 0; half < 2; half++) {
                before = 0;
                for (int x = 0; x < w; x++) {
                    columnShare[half][x] = (halfLand[half] == 0) ? 0.5
                        : (before + cols[half][x] / 2.0) / halfLand[half];
                    before += cols[half][x];
                }
            }
        }

        private boolean isLand(Tile t) {
            return t != null && t.isLand() && !map.isPolar(t);
        }

        /**
         * Is a tile in the land's northern half?
         *
         * @param t The {@code Tile}.
         * @return True in the north.
         */
        public boolean isNorth(Tile t) {
            return rowShare[t.getY()] < 0.5;
        }

        /**
         * The share of its half's land west of a tile.
         *
         * @param t The {@code Tile}.
         * @return 0 (far west) to 1 (far east).
         */
        public double getColumnShare(Tile t) {
            return columnShare[(isNorth(t)) ? 0 : 1][t.getX()];
        }

        /**
         * Is a tile in the western third of its half's land?
         *
         * @param t The {@code Tile}.
         * @return True in the west.
         */
        public boolean isWest(Tile t) {
            return getColumnShare(t) < 1.0 / 3;
        }

        /**
         * Is a tile in the eastern third of its half's land?
         *
         * @param t The {@code Tile}.
         * @return True in the east.
         */
        public boolean isEast(Tile t) {
            return getColumnShare(t) > 2.0 / 3;
        }

        /**
         * Is a tile in a band?
         *
         * @param band The {@code Band}.
         * @param t The {@code Tile}.
         * @return True if the band has the tile.
         */
        public boolean contains(Band band, Tile t) {
            switch (band) {
            case WEST: return isWest(t);
            case EAST: return isEast(t);
            default: return true;
            }
        }
    }


    /**
     * The distance of every tile to the Pacific (the water of the
     * Pacific regions), through the map's own neighbours.
     *
     * @param map The {@code Map}.
     * @return The distances by x and y, {@code Integer.MAX_VALUE} where
     *     no Pacific is reachable.
     */
    public static int[][] getPacificDistances(Map map) {
        final int w = map.getWidth(), h = map.getHeight();
        final int[][] d = new int[w][h];
        final Deque<Tile> queue = new ArrayDeque<>();
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                final Tile t = map.getTile(x, y);
                if (t != null && !t.isLand() && t.getRegion() != null
                    && t.getRegion().isPacific()) {
                    d[x][y] = 0;
                    queue.add(t);
                } else {
                    d[x][y] = Integer.MAX_VALUE;
                }
            }
        }
        while (!queue.isEmpty()) {
            final Tile t = queue.poll();
            final int next = d[t.getX()][t.getY()] + 1;
            for (Tile n : t.getSurroundingTiles(1)) {
                if (d[n.getX()][n.getY()] > next) {
                    d[n.getX()][n.getY()] = next;
                    queue.add(n);
                }
            }
        }
        return d;
    }


    /**
     * The placement of one new map's villages.
     */
    public static final class Placement {

        private final Map map;
        private final List<Player> natives;
        private final List<Tile> candidates;
        private final List<Tile> allTiles;
        private final Predicate<Tile> suitable;
        private final Random random;
        private final int minimum, maximum, total;
        private final Bands bands;

        /** The homes, in the order they were chosen. */
        private final java.util.Map<Player, Tile> homes
            = new LinkedHashMap<>();
        /** The sites of each tribe, in the generator's order. */
        private final java.util.Map<Player, Set<Tile>> areas
            = new LinkedHashMap<>();
        /** All sites, in the generator's order, then the added ones. */
        private final List<Tile> sites = new ArrayList<>();
        private int target, landTiles, moved, topUps, missing;
        private boolean westFirstNorth, eastFirstNorth;


        /**
         * Prepare the placement.
         *
         * @param map The new {@code Map}.
         * @param natives The live native players.
         * @param candidates The spaced sites, in the generator's order.
         * @param allTiles The map's tiles in the generator's shuffled
         *     order.
         * @param suitable Whether a tile could hold a village (the
         *     generator's own test).
         * @param random The generator's random number source.
         * @param minimum The least villages of a tribe.
         * @param maximum The most villages of a tribe.
         * @param total The most villages of the map.
         */
        public Placement(Map map, List<Player> natives, List<Tile> candidates,
                         List<Tile> allTiles, Predicate<Tile> suitable,
                         Random random, int minimum, int maximum,
                         int total) {
            this.map = map;
            this.natives = natives;
            this.candidates = candidates;
            this.allTiles = allTiles;
            this.suitable = suitable;
            this.random = random;
            this.minimum = minimum;
            this.maximum = Math.max(minimum, maximum);
            this.total = total;
            this.bands = new Bands(map);
        }

        /**
         * Is a tile free to take a new village?
         *
         * @param t The {@code Tile}.
         * @return True if it could.
         */
        private boolean isFree(Tile t) {
            return t.isLand() && !t.isPolar() && !t.isOuterRing()
                && !sites.contains(t) && suitable.test(t);
        }

        private int spacing(Tile t) {
            int d = Integer.MAX_VALUE;
            for (Tile s : sites) d = Math.min(d, t.getDistanceTo(s));
            return d;
        }

        private int homeSpacing(Tile t) {
            int d = Integer.MAX_VALUE;
            for (Tile s : homes.values()) d = Math.min(d, t.getDistanceTo(s));
            return d;
        }

        private static int square(Tile a, Tile b) {
            final int dx = a.getX() - b.getX(), dy = a.getY() - b.getY();
            return dx * dx + dy * dy;
        }

        private Band bandOf(Player p) {
            return getBand(p.getNationId());
        }

        /**
         * May a tribe have a site here?
         *
         * @param p The tribe.
         * @param t The {@code Tile}.
         * @return True if it may.
         */
        private boolean mayHave(Player p, Tile t) {
            return homes.containsKey(p) && bands.contains(bandOf(p), t);
        }

        /**
         * The tribe a tile belongs to: the nearest home that may have
         * it (then the nearer in a straight line, then the home chosen
         * first).
         *
         * @param t The {@code Tile}.
         * @return The tribe, or null if no home may have the tile.
         */
        public Player getOwner(Tile t) {
            Player best = null;
            int bestDistance = Integer.MAX_VALUE, bestSquare = Integer.MAX_VALUE;
            for (java.util.Map.Entry<Player, Tile> e : homes.entrySet()) {
                if (!mayHave(e.getKey(), t)) continue;
                final int d = t.getDistanceTo(e.getValue());
                final int sq = square(t, e.getValue());
                if (d < bestDistance || (d == bestDistance && sq < bestSquare)) {
                    best = e.getKey();
                    bestDistance = d;
                    bestSquare = sq;
                }
            }
            return best;
        }

        /**
         * Choose a tribe's home in an area: of {@link #HOME_CHOICES}
         * random free tiles of the area (not a home already), the one
         * farthest from the homes before it.
         *
         * @param area The area.
         * @return The home, or null if the area has no free tile.
         */
        private Tile chooseHome(Predicate<Tile> area) {
            final List<Tile> free = new ArrayList<>();
            for (Tile t : allTiles) {
                if (isFree(t) && area.test(t)
                    && !homes.containsValue(t)) free.add(t);
            }
            if (free.isEmpty()) return null;
            Tile best = null;
            int bestSpacing = -1;
            for (int i = 0; i < HOME_CHOICES; i++) {
                final Tile t = free.get(randomInt(logger, "Home", random,
                                                  free.size()));
                final int d = (homes.isEmpty()) ? 0 : homeSpacing(t);
                if (d > bestSpacing) {
                    best = t;
                    bestSpacing = d;
                }
            }
            return best;
        }

        /**
         * Choose the homes of all tribes.
         */
        private void chooseHomes() {
            westFirstNorth = randomInt(logger, "West tribes north", random, 2) == 0;
            eastFirstNorth = randomInt(logger, "East tribes north", random, 2) == 0;
            final int[][] pacific = getPacificDistances(map);
            final List<Player> banded = new ArrayList<>(), free = new ArrayList<>();
            for (String id : WEST_TRIBES) addTribe(id, banded);
            for (String id : EAST_TRIBES) addTribe(id, banded);
            for (Player p : natives) {
                if (getBand(p.getNationId()) == Band.ANYWHERE) free.add(p);
            }
            randomShuffle(logger, "Free tribes", free, random);
            for (Player p : banded) {
                final Band band = bandOf(p);
                final List<String> ids = (band == Band.WEST) ? WEST_TRIBES
                    : EAST_TRIBES;
                final boolean first = ids.indexOf(p.getNationId()) == 0;
                final boolean north = (band == Band.WEST)
                    ? (first == westFirstNorth) : (first == eastFirstNorth);
                final Predicate<Tile> inBand = t -> bands.contains(band, t);
                final Predicate<Tile> cell = t -> inBand.test(t)
                    && bands.isNorth(t) == north;
                Predicate<Tile> area = cell;
                if (WEST_TRIBES.get(0).equals(p.getNationId())) {
                    // The Inca: at the Pacific, or as near as can be.
                    int least = Integer.MAX_VALUE;
                    for (Tile t : allTiles) {
                        if (isFree(t) && cell.test(t)) {
                            least = Math.min(least, pacific[t.getX()][t.getY()]);
                        }
                    }
                    final int limit = Math.max(PACIFIC_HOME_DISTANCE, least);
                    area = t -> cell.test(t)
                        && pacific[t.getX()][t.getY()] <= limit;
                }
                Tile home = chooseHome(area);
                if (home == null) home = chooseHome(inBand);
                if (home == null) home = chooseHome(t -> true);
                if (home != null) homes.put(p, home);
            }
            for (Player p : free) {
                final Tile home = chooseHome(t -> true);
                if (home != null) homes.put(p, home);
            }
        }

        private void addTribe(String id, List<Player> list) {
            for (Player p : natives) {
                if (id.equals(p.getNationId())) list.add(p);
            }
        }

        private int count(Player p) {
            final Set<Tile> s = areas.get(p);
            return (s == null) ? 0 : s.size();
        }

        private void add(Player p, Tile t) {
            areas.computeIfAbsent(p, k -> new LinkedHashSet<>()).add(t);
        }

        /**
         * Place the villages.
         *
         * @param lb A {@code LogBuilder} to log to.
         * @return This placement.
         */
        public Placement place(LogBuilder lb) {
            for (Tile t : allTiles) {
                if (bands.isLand(t)) landTiles++;
            }
            target = OriginalNatives.getTarget(landTiles, natives.size(),
                                               minimum, maximum, total);
            chooseHomes();
            for (Player p : homes.keySet()) {
                areas.put(p, new LinkedHashSet<>());
            }

            // Each spaced site to its tribe.
            final java.util.Map<Tile, Player> owner = new LinkedHashMap<>();
            for (Tile t : candidates) {
                final Player p = getOwner(t);
                if (p != null) owner.put(t, p);
            }
            // First the least of every tribe, then the rest in order.
            final Set<Tile> chosen = new HashSet<>();
            final java.util.Map<Player, Integer> n = new LinkedHashMap<>();
            for (java.util.Map.Entry<Tile, Player> e : owner.entrySet()) {
                final int c = n.getOrDefault(e.getValue(), 0);
                if (c < minimum) {
                    chosen.add(e.getKey());
                    n.put(e.getValue(), c + 1);
                }
            }
            int placed = chosen.size();
            for (java.util.Map.Entry<Tile, Player> e : owner.entrySet()) {
                if (placed >= target) break;
                if (chosen.contains(e.getKey())) continue;
                final int c = n.getOrDefault(e.getValue(), 0);
                if (c >= maximum) continue;
                chosen.add(e.getKey());
                n.put(e.getValue(), c + 1);
                placed++;
            }
            for (Tile t : candidates) {
                if (!chosen.contains(t)) continue;
                sites.add(t);
                add(owner.get(t), t);
            }

            // A tribe with too few gets more near its own: first a spaced
            // site no tribe has or one of a tribe with more than its
            // least, else a new one.
            for (Player p : homes.keySet()) {
                while (count(p) < minimum) {
                    final Tile shared = findShared(p);
                    if (shared != null) {
                        final Player q = siteOwner(shared);
                        if (q == null) {
                            sites.add(shared);
                        } else {
                            areas.get(q).remove(shared);
                        }
                        add(p, shared);
                        moved++;
                        continue;
                    }
                    final Tile t = findTopUp(p);
                    if (t == null) {
                        logger.info("No top-up site for " + p.getNationId()
                            + " at " + count(p));
                        missing += minimum - count(p);
                        break;
                    }
                    sites.add(t);
                    add(p, t);
                    topUps++;
                }
            }
            // Back to the map's number: the latest site of a tribe that
            // has more than its least goes first.
            for (int i = sites.size() - 1; i >= 0 && sites.size() > target; i--) {
                final Tile t = sites.get(i);
                for (java.util.Map.Entry<Player, Set<Tile>> e : areas.entrySet()) {
                    if (!e.getValue().contains(t)) continue;
                    if (e.getValue().size() > minimum) {
                        e.getValue().remove(t);
                        sites.remove(i);
                    }
                    break;
                }
            }

            final StringBuilder sb = new StringBuilder();
            for (java.util.Map.Entry<Player, Set<Tile>> e : areas.entrySet()) {
                final Tile home = homes.get(e.getKey());
                sb.append(' ').append(e.getKey().getNationId()
                    .substring("model.nation.".length()))
                    .append('=').append(e.getValue().size())
                    .append('@').append(home.getX()).append(',')
                    .append(home.getY());
            }
            final String text = "Original natives: land " + landTiles
                + ", villages " + sites.size() + " of " + target
                + " (spaced sites " + candidates.size() + ", moved " + moved
                + ", added " + topUps + ", missing " + missing + "), west "
                + ((westFirstNorth) ? "Inca north" : "Inca south")
                + ", east " + ((eastFirstNorth) ? "Iroquois north"
                    : "Iroquois south") + ":" + sb;
            logger.info(text);
            lb.add(text, "\n");
            return this;
        }

        /**
         * The tribe that has a site.
         *
         * @param t The {@code Tile}.
         * @return The tribe, or null if the tile is no site.
         */
        private Player siteOwner(Tile t) {
            for (java.util.Map.Entry<Player, Set<Tile>> e : areas.entrySet()) {
                if (e.getValue().contains(t)) return e.getKey();
            }
            return null;
        }

        /**
         * A spaced site for a tribe that has too few: of the spaced sites
         * it may have, no tribe has, or a tribe with more than its least
         * has, the one nearest to its home (ties to the earlier in the
         * generator's order).
         *
         * @param p The tribe.
         * @return The site, or null if there is none.
         */
        Tile findShared(Player p) {
            final Tile home = homes.get(p);
            Tile best = null;
            int bestDistance = Integer.MAX_VALUE;
            for (Tile t : candidates) {
                if (!mayHave(p, t)) continue;
                final Player q = siteOwner(t);
                if (q == p || (q != null && count(q) <= minimum)) continue;
                // A site no tribe has keeps clear of the added ones.
                if (q == null && spacing(t) < TOP_UP_DISTANCE) continue;
                final int d = t.getDistanceTo(home);
                if (d < bestDistance) {
                    best = t;
                    bestDistance = d;
                }
            }
            return best;
        }

        /**
         * A site to give a tribe that has too few: in its own land the
         * free tile farthest from every site; with no room there, the
         * free tile of its band nearest to its home; else the free tile
         * nearest to its home anywhere; always at least
         * {@link #TOP_UP_DISTANCE} from every site.  Ties go to the
         * earlier tile of the generator's order.
         *
         * @param p The tribe.
         * @return The site, or null if there is none.
         */
        Tile findTopUp(Player p) {
            Tile best = null;
            int bestSpacing = TOP_UP_DISTANCE - 1;
            for (Tile t : allTiles) {
                if (!isFree(t) || getOwner(t) != p) continue;
                final int d = spacing(t);
                if (d > bestSpacing) {
                    best = t;
                    bestSpacing = d;
                }
            }
            if (best != null) return best;
            final Tile home = homes.get(p);
            for (int pass = 0; pass < 2 && best == null; pass++) {
                int bestDistance = Integer.MAX_VALUE;
                for (Tile t : allTiles) {
                    if (!isFree(t) || (pass == 0 && !mayHave(p, t))
                        || spacing(t) < TOP_UP_DISTANCE) continue;
                    final int d = t.getDistanceTo(home);
                    if (d < bestDistance) {
                        best = t;
                        bestDistance = d;
                    }
                }
            }
            return best;
        }

        /** @return The sites, in the order the generator places them. */
        public List<Tile> getSites() {
            return Collections.unmodifiableList(sites);
        }

        /** @return The sites of each tribe. */
        public java.util.Map<Player, Set<Tile>> getAreas() {
            return areas;
        }

        /** @return The tribes' homes. */
        public java.util.Map<Player, Tile> getHomes() {
            return Collections.unmodifiableMap(homes);
        }

        /** @return The map's number of villages. */
        public int getTarget() {
            return target;
        }

        /** @return The land tiles counted. */
        public int getLandTiles() {
            return landTiles;
        }

        /** @return The spaced sites given to tribes with too few. */
        public int getMoved() {
            return moved;
        }

        /** @return The sites added to tribes with too few. */
        public int getTopUps() {
            return topUps;
        }

        /** @return The sites no tribe could get. */
        public int getMissing() {
            return missing;
        }

        /** @return Whether the Inca live in the north. */
        public boolean isIncaNorth() {
            return westFirstNorth;
        }

        /** @return Whether the Iroquois live in the north. */
        public boolean isIroquoisNorth() {
            return eastFirstNorth;
        }

        /** @return The land's bands. */
        public Bands getBands() {
            return bands;
        }
    }
}
