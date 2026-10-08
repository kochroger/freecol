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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Direction;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Player.NoClaimReason;
import net.sf.freecol.common.model.Role;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;


/**
 * The original's tutorial tips (master plan W11): which {@code @TUTORIALk}
 * a moment brings, its values and its box.  Headless; {@link ClassicGUI}
 * times them ({@code scheduleTip}), each once per game
 * ({@code Player.classicTips}, bit k) and only with Tutortips on (the
 * options box's row, FreeCol's {@code model.option.guiShowTutorial}).
 *
 * <p>The tips and their triggers (V = seen in a clip, I = inferred):
 * <ul>
 *   <li>{@code @TUTORIAL2}, admiral: {@link #TIP_MS} after the @LANDHO box
 *       closed (V: landfall #3185 -&gt; #3217).</li>
 *   <li>{@code @TUTORIAL5}, admiral: {@link #TIP_MS} after the notice of
 *       a colonist the religious unrest brought to the docks closed (V:
 *       @UNREST #18247 -&gt; #18279; the notice is FreeCol's
 *       {@code model.player.emigrate} until W8e's @UNREST).
 *       {@code %STRING0} the home port, {@code %STRING1} the unit
 *       ("Erfahrene Farmer", NAMES.TXT @JOB's second column).</li>
 *   <li>The unit tips, the first that fits, at the switch to the unit:
 *       {@link #TIP_MS} after the last change of the unit before it (the
 *       turn flow's hand-over base), before the unit comes up, which it
 *       does with the tip's close (V: landfall 03 section 6, the block 1
 *       frame after the close; V puts them 0.61 to 0.86 s after the last
 *       change, @TUTORIAL3 0.485 s after the map jumped to the unit); a
 *       unit made active without a hand-over (a click, the view's first
 *       unit, the turn start) {@link #TIP_MS} after it came up (I).  Not
 *       for a unit going by itself (a destination).  In this order:
 *       {@code @TUTORIAL11} (admiral) an empty ship on the map (V: the
 *       ship the soldier just left, #13386; I: empty of goods too),
 *       {@code %STRING0} the ship, {@code %STRING1} the home port;
 *       {@code @TUTORIAL13} (frontiersman) a pioneer on land (V #15080);
 *       {@code @TUTORIAL14} (soldier) a soldier on land (V #17076);
 *       {@code @TUTORIAL3} (frontiersman) before the first colony, a unit
 *       that can found one on land where it may, with a resource on a
 *       neighbouring tile (V #21514, "Felle" for the game at (47,46); the
 *       turn before, standing on that tile, it got none), {@code %STRING0}
 *       its goods.</li>
 *   <li>{@code @TUTORIAL4} (colonist, {@code @x=10}): {@link #COLONY_TIP_MS}
 *       after the screen of the first colony founded opened (V: clip008
 *       #4032 -&gt; #4068); {@code %STRING0} what its colonist makes,
 *       {@code %STRING1} the goods he could make most of there
 *       ({@link #colonyValues}).</li>
 *   <li>{@code @TUTORIAL12} (colonist): {@link #DOCK_TIP_MS} after the
 *       colony screen a ship's docking opened (V: clip008 #30022 -&gt;
 *       #30059); {@code %STRING0} the colony.  D10 builds the docking.</li>
 * </ul>
 * {@code @TUTORIAL1} is the first scene's ({@link ClassicFirstScene}),
 * {@code @TUTORIAL17} the Europe screen's ({@link ClassicBands}).
 */
final class ClassicTips {

    /** A tip's distance from its trigger (V: landfall #3185 -&gt; #3217, #18247 -&gt; #18279). */
    static final double TIP_MS = 457.0;

    /** The colony screen drawn to {@code @TUTORIAL4} (V: clip008 #4032 -&gt; #4068). */
    static final double COLONY_TIP_MS = 514.0;

    /** The docking's colony screen to {@code @TUTORIAL12} (V: clip008 #30022 -&gt; #30059). */
    static final double DOCK_TIP_MS = 542.0;

    /** The tips' numbers (GAME.TXT {@code @TUTORIALk}). */
    static final int LAND_HO = 2, SITE = 3, COLONY = 4, UNREST = 5,
        SHIP = 11, DOCK = 12, PIONEER = 13, SOLDIER = 14;

    /** The unit tips, in the order they are tried. */
    static final int[] UNIT_TIPS = { SHIP, PIONEER, SOLDIER, SITE };

    /** FreeCol's notices of a colonist the unrest brought (ServerPlayer.csEmigrate). */
    static final String EMIGRATE = "model.player.emigrate",
        AUTO_RECRUIT = "model.player.autoRecruit";

    /** NAMES.TXT {@code @CARGO} row of each {@code @RESOURCE} row's goods (Wild and Biber: Felle). */
    private static final int[] RESOURCE_CARGO = {
        -1, 0, 0, 3, 2, 1, 6, 0, 4, 4, 5, 5, 7, 6
    };

    /** {@code @CARGO} rows of the goods {@link #colonyValues} offers as another job (ties: this order). */
    private static final int[] OTHER_JOBS = { 1, 2, 3, 4, 5, 6, 7 };

    /** {@code @CARGO} rows past the goods ClassicHud knows. */
    private static final int CARGO_FOOD = 0, CARGO_LUMBER = 5, CARGO_HAMMERS = 16,
        CARGO_CROSSES = 17, CARGO_BELLS = 18;

    private ClassicTips() {}

    /**
     * @param k A tip's number.
     * @return Its GAME.TXT section.
     */
    static String section(int k) {
        return "TUTORIAL" + k;
    }

    /**
     * @param k A tip's number.
     * @return Its advisor (landfall and clip008 crops).
     */
    static ClassicAdvisorBox.Portrait portrait(int k) {
        switch (k) {
        case SITE: case PIONEER: return ClassicAdvisorBox.Portrait.SCOUT;
        case SOLDIER: return ClassicAdvisorBox.Portrait.SOLDIER;
        case COLONY: case DOCK: return ClassicAdvisorBox.Portrait.COLONIST;
        default: return ClassicAdvisorBox.Portrait.ADMIRAL;
        }
    }

    /**
     * A tip's box: GAME.TXT's words with {@code values}, its advisor, no
     * rows; a key or a click closes it.
     *
     * @param t The original texts, or null.
     * @param k The tip's number.
     * @param values The values.
     * @return The box, or null without the text (FreeCol has no such tip).
     */
    static ClassicAdvisorBox.Request request(ClassicText t, int k,
                                             Map<String, String> values) {
        return request(t, k, values, 0L);
    }

    /**
     * {@link #request(ClassicText, int, Map)} shown at a given time, its
     * advisor's palette in the lead before it (the box layer's
     * {@code showAtNanos}).
     *
     * @param t The original texts, or null.
     * @param k The tip's number.
     * @param values The values.
     * @param showAtNanos When the box appears (clock ns), or 0 for at once.
     * @return The box, or null without the text.
     */
    static ClassicAdvisorBox.Request request(ClassicText t, int k,
                                             Map<String, String> values,
                                             long showAtNanos) {
        final String s = section(k);
        final ClassicText.Message m = (t == null) ? null : t.message(s);
        final ClassicAdvisorBox.Builder b = ClassicAdvisorBox.fromGameText(s, m, values);
        if (b == null) return null;
        if (showAtNanos != 0L) b.showAt(showAtNanos);
        // GAME.TXT's @x (only @TUTORIAL4 of these has one: x 10, clip008 #4068).
        return b.portrait(portrait(k)).x(m.x)
            .stopgap(Messages.message("classic.dialog.messages"), null).build();
    }

    /**
     * @param shown The tips shown ({@code Player.classicTips}).
     * @param k A tip's number.
     * @return Whether {@code @TUTORIALk} was shown.
     */
    static boolean shown(int shown, int k) {
        return (shown & (1 << k)) != 0;
    }

    /**
     * The tip a unit brings when it becomes the active one.
     *
     * @param u The unit.
     * @param shown The tips shown ({@code Player.classicTips}).
     * @return {@link #SHIP}, {@link #PIONEER}, {@link #SOLDIER},
     *     {@link #SITE}, or -1.
     */
    static int unitTip(Unit u, int shown) {
        // Not a unit on its way by itself (a goto: the player chose nothing).
        if (u == null || u.isDisposed() || u.getTile() == null
            || u.getDestination() != null) return -1;
        for (int k : UNIT_TIPS) {
            if (!shown(shown, k) && fits(u, k)) return k;
        }
        return -1;
    }

    /**
     * @param u A unit on the map.
     * @param k A unit tip.
     * @return Whether the unit brings it.
     */
    static boolean fits(Unit u, int k) {
        final Tile tile = u.getTile();
        if (tile == null) return false;
        if (k == SHIP) {
            return u.isNaval() && !u.hasCargo();
        }
        if (u.isNaval() || u.isOnCarrier() || !tile.isLand()) return false;
        final String role = (u.getRole() == null) ? null : u.getRole().getRoleSuffix();
        switch (k) {
        case PIONEER: return "pioneer".equals(role);
        case SOLDIER: return "soldier".equals(role);
        case SITE: return siteCargo(u) >= 0;
        default: return false;
        }
    }

    /**
     * {@code @TUTORIAL3}'s goods: before the player's first colony, a unit
     * that can found one, on a land tile where it may (also on the
     * natives' land), with a resource on one of the 8 tiles around (I;
     * the first from north, clockwise).
     *
     * @param u The unit.
     * @return The goods' {@code @CARGO} row, or -1.
     */
    static int siteCargo(Unit u) {
        final Tile tile = u.getTile();
        final Player p = u.getOwner();
        if (tile == null || p == null || !tile.isLand() || tile.hasSettlement()
            || p.hasSettlements() || u.getType() == null
            || !u.getType().canBuildColony()) return -1;
        final NoClaimReason why = p.canClaimToFoundSettlementReason(tile);
        if (why != NoClaimReason.NONE && why != NoClaimReason.NATIVES) return -1;
        for (Direction d : Direction.values()) {
            final Tile n = tile.getNeighbourOrNull(d);
            if (n == null || n.getResource() == null
                || n.getResource().getType() == null) continue;
            final int row = ClassicHud.resourceRow(n.getResource().getType().getId());
            if (row >= 0 && row < RESOURCE_CARGO.length && RESOURCE_CARGO[row] >= 0) {
                return RESOURCE_CARGO[row];
            }
        }
        return -1;
    }

    /**
     * A unit tip's values.
     *
     * @param t The original texts, or null.
     * @param u The unit.
     * @param k The tip.
     * @return The values (missing ones left out).
     */
    static Map<String, String> unitValues(ClassicText t, Unit u, int k) {
        final Map<String, String> v = new HashMap<>();
        if (k == SHIP) {
            put(v, "STRING0", cell(t, "UNIT", ClassicHud.unitRow(u), 0));
            put(v, "STRING1", homePort(t, u.getOwner()));
        } else if (k == SITE) {
            put(v, "STRING0", cargoName(t, siteCargo(u)));
        }
        return v;
    }

    /**
     * {@code @TUTORIAL5}'s values: the home port and the colonist the
     * unrest brought (NAMES.TXT @JOB's second column, "Erfahrene Farmer";
     * else FreeCol's name).
     *
     * @param t The original texts, or null.
     * @param p Our player.
     * @param u The colonist, or null.
     * @return The values.
     */
    static Map<String, String> unrestValues(ClassicText t, Player p, Unit u) {
        final Map<String, String> v = new HashMap<>();
        put(v, "STRING0", homePort(t, p));
        String who = null;
        if (u != null && u.getType() != null) {
            who = cell(t, "JOB", ClassicHud.jobRow(Role.getRoleIdSuffix(u.getType().getId())), 1);
            if (who == null) who = Messages.getName(u.getType());
        }
        put(v, "STRING1", who);
        return v;
    }

    /**
     * {@code @TUTORIAL4}'s values: what the colony's first colonist makes
     * ({@code %STRING0}, clip008 "Felle"), and another goods he could make
     * on his tile ({@code %STRING1}, "Nutzholz"; I: of sugar, tobacco,
     * cotton, furs, lumber, ore and silver the one the tile gives him most
     * of, ties in that order; else lumber, or food for a lumberjack).
     *
     * @param t The original texts, or null.
     * @param colony The colony.
     * @return The values (missing ones left out).
     */
    static Map<String, String> colonyValues(ClassicText t, Colony colony) {
        final Map<String, String> v = new HashMap<>();
        final List<Unit> units = (colony == null) ? List.of() : colony.getUnitList();
        if (units.isEmpty()) return v;
        final Unit u = units.get(0);
        final GoodsType now = u.getWorkType();
        final int nowRow = (now == null) ? -1 : cargoRow(now.getId());
        put(v, "STRING0", cargoName(t, nowRow));
        int other = -1, most = 0;
        final Tile work = u.getWorkTile();
        if (work != null && t != null) {
            for (int row : OTHER_JOBS) {
                if (row == nowRow) continue;
                final GoodsType g = goodsOfRow(colony, row);
                final int n = (g == null || !work.canProduce(g, u.getType())) ? 0
                    : work.getPotentialProduction(g, u.getType());
                if (n > most) {
                    most = n;
                    other = row;
                }
            }
        }
        if (other < 0) other = (nowRow == CARGO_LUMBER) ? CARGO_FOOD : CARGO_LUMBER;
        put(v, "STRING1", cargoName(t, other));
        return v;
    }

    /**
     * {@code @TUTORIAL12}'s values: the colony.
     *
     * @param colony The colony.
     * @return The values.
     */
    static Map<String, String> dockValues(Colony colony) {
        final Map<String, String> v = new HashMap<>();
        if (colony != null) put(v, "STRING0", colony.getName());
        return v;
    }

    /**
     * NAMES.TXT {@code @CARGO} row of a FreeCol goods type: grain and fish
     * are food, hammers, crosses and bells after the muskets.
     *
     * @param goodsTypeId The goods type's id.
     * @return The row, or -1.
     */
    static int cargoRow(String goodsTypeId) {
        if (goodsTypeId == null) return -1;
        switch (Role.getRoleIdSuffix(goodsTypeId)) {
        case "grain": case "fish": case "food": return CARGO_FOOD;
        case "hammers": return CARGO_HAMMERS;
        case "crosses": return CARGO_CROSSES;
        case "bells": return CARGO_BELLS;
        default: return ClassicHud.cargoRow(goodsTypeId);
        }
    }

    /** The goods type of a {@code @CARGO} row in the colony's rules (1..7 only). */
    private static GoodsType goodsOfRow(Colony colony, int row) {
        final String[] ids = { null, "sugar", "tobacco", "cotton", "furs", "lumber",
                               "ore", "silver" };
        if (row < 1 || row >= ids.length) return null;
        return colony.getSpecification().getGoodsType("model.goods." + ids[row]);
    }

    /** NAMES.TXT {@code @CARGO} name of a row, or null. */
    static String cargoName(ClassicText t, int row) {
        return cell(t, "CARGO", row, 0);
    }

    /** NAMES.TXT {@code @HOMEPORT} of a player, or null. */
    private static String homePort(ClassicText t, Player p) {
        return cell(t, "HOMEPORT", ClassicDestinations.nation(p), 0);
    }

    private static void put(Map<String, String> v, String k, String s) {
        if (s != null) v.put(k, s);
    }

    /** A NAMES.TXT row's column, trimmed, or null. */
    private static String cell(ClassicText t, String section, int row, int col) {
        if (t == null || row < 0) return null;
        final List<String[]> rows = t.names(section);
        if (rows == null || row >= rows.size() || rows.get(row).length <= col) return null;
        final String s = rows.get(row)[col].trim();
        return (s.isEmpty()) ? null : s;
    }
}
