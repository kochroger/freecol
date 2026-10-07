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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.AbstractGoods;
import net.sf.freecol.common.model.AbstractUnit;
import net.sf.freecol.common.model.DiplomaticTrade;
import net.sf.freecol.common.model.DiplomaticTrade.TradeContext;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.GoodsContainer;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.LostCityRumour;
import net.sf.freecol.common.model.Market;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.ProductionType;
import net.sf.freecol.common.model.Region;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.model.StanceTradeItem;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Tension;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TradeItem;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;


/**
 * The seams the base GUI leaves silent (master plan N15, G1): what the
 * classic UI answers there, as pure helpers the tests call headless.
 * {@code ClassicGUI} overrides the seams and asks the boxes.
 * <ul>
 *   <li><b>Loot</b> ({@code showCaptureGoodsDialog}).  The server waits for
 *       the answer (a {@code LootSession} without a timer, and every end of
 *       turn waits for open sessions): the base GUI's silence froze the
 *       game after a naval win against a loaded ship.  The winner takes at
 *       once what fits, the most valuable first ({@link #lootTaken}; I: the
 *       original has no capture question in GAME.TXT, only the
 *       {@code @CARGOCAPTURE} notice, {@link #lootNotices}).</li>
 *   <li><b>The recruits</b> ({@code showEmigrationDialog}: William
 *       Brewster, the Fountain of Youth).  A list box of the three
 *       recruits ({@link #emigrationRequest}): GAME.TXT
 *       {@code @RECRUITCHOOSE} with the priest, {@code @LOSTCITY0} with
 *       the frontiersman (I, in no clip).</li>
 *   <li><b>Negotiation</b> ({@code showNegotiationDialog}).  The first
 *       contact with a European nation sends us its peace treaty, and the
 *       server waits for the answer (a {@code DiplomacySession} of 1000
 *       hours in single player): the peace is accepted at once
 *       ({@link #isContactPeace}; I, the original has no such box).  Any
 *       other proposal is a yes/no box in FreeCol's words
 *       ({@link #negotiationText}); our own proposals are not built yet
 *       ({@link #isOwnProposal}).</li>
 *   <li><b>A village, a tile</b> ({@code showIndianSettlementPanel},
 *       {@code showTilePanel}): notices in FreeCol's words
 *       ({@link #villageText}, {@link #tileText}).</li>
 * </ul>
 */
final class ClassicSeams {

    /** GAME.TXT's loot notice. */
    static final String CARGO_CAPTURE = "CARGOCAPTURE";

    /** GAME.TXT's Brewster box. */
    static final String RECRUIT_CHOOSE = "RECRUITCHOOSE";

    /** GAME.TXT's Fountain of Youth box. */
    static final String LOST_CITY_CHOOSE = "LOSTCITY0";


    private ClassicSeams() {}   // static helpers only


    // Loot

    /**
     * The goods a naval winner takes (class comment): of the offered
     * goods, the most valuable first (at our market's bid price; ties and
     * no market keep the offer's order), each one that still fits, as the
     * server's own check adds them one after the other
     * ({@code Unit.canAdd}).  The same objects as offered: the server
     * matches them by type, amount and location.  Never more than fits:
     * one the server cannot add answers the session with an error and
     * leaves it open, which is the freeze again.
     *
     * @param winner The winning unit.
     * @param offered The goods offered, or null.
     * @return The goods taken (empty if nothing fits).
     */
    static List<Goods> lootTaken(Unit winner, List<Goods> offered) {
        final List<Goods> taken = new ArrayList<>();
        if (winner == null || offered == null || offered.isEmpty()) return taken;
        final List<Goods> sorted = new ArrayList<>();
        for (Goods g : offered) {
            if (g != null && g.getType() != null && g.getAmount() > 0) sorted.add(g);
        }
        final Player owner = winner.getOwner();
        final Market market = (owner == null) ? null : owner.getMarket();
        if (market != null) {
            // A stable sort: equal prices keep the offer's order.
            sorted.sort(Comparator.comparingInt((Goods g) ->
                    market.getBidPrice(g.getType(), g.getAmount())).reversed());
        }
        int free = winner.getSpaceLeft();
        final Map<GoodsType, Integer> aboard = new HashMap<>();
        for (Goods g : sorted) {
            final GoodsType type = g.getType();
            final int before = aboard.computeIfAbsent(type, winner::getGoodsCount);
            final int after = before + g.getAmount();
            final int holds = holds(after) - holds(before);
            if (holds > free) continue;
            free -= holds;
            aboard.put(type, after);
            taken.add(g);
        }
        return taken;
    }

    /** @return The holds an amount of one goods type needs. */
    private static int holds(int amount) {
        final int size = GoodsContainer.CARGO_SIZE;
        return (amount + size - 1) / size;
    }

    /**
     * The notices after a loot: one per goods type taken (the amounts of
     * one type added), in the order taken, GAME.TXT {@code @CARGOCAPTURE}:
     * "{%STRING0} Ware ({%NUMBER0 %STRING1}) durch {%STRING2 %STRING3}
     * erobert!" with the loser's and our nation's NAMES
     * {@code @NATIONABBREV} ("Engl.", "Holl."), FreeCol's goods and ship
     * names ("Felle", "Kaperschiff"; they match NAMES {@code @CARGO} and
     * {@code @UNIT}), no portrait (I).  Without the texts, or when the
     * loser is not known or not one of the four nations, one notice in
     * FreeCol's words: "Ladung rauben" and a line per goods.
     *
     * @param t The pack's texts, or null.
     * @param winner The winning unit.
     * @param loser The loser's owner, or null when not known.
     * @param taken The goods taken ({@link #lootTaken}).
     * @param title The stopgap's window title.
     * @return The notices, none when nothing was taken.
     */
    static List<ClassicAdvisorBox.Request> lootNotices(ClassicText t, Unit winner,
                                                       Player loser,
                                                       List<Goods> taken,
                                                       String title) {
        final List<ClassicAdvisorBox.Request> out = new ArrayList<>();
        if (winner == null || taken == null || taken.isEmpty()) return out;
        final Map<GoodsType, Integer> amounts = new LinkedHashMap<>();
        for (Goods g : taken) amounts.merge(g.getType(), g.getAmount(), Integer::sum);
        final ClassicText.Message m = (t == null) ? null : t.message(CARGO_CAPTURE);
        final String ours = abbrev(t, winner.getOwner());
        final String theirs = abbrev(t, loser);
        if (m != null && ours != null && theirs != null) {
            for (Map.Entry<GoodsType, Integer> e : amounts.entrySet()) {
                final Map<String, String> values = new HashMap<>();
                values.put("STRING0", theirs);
                values.put("NUMBER0", Integer.toString(e.getValue()));
                values.put("STRING1", ClassicAdvisorBox.literal(Messages.getName(e.getKey())));
                values.put("STRING2", ours);
                values.put("STRING3", ClassicAdvisorBox.literal(
                        Messages.getName(winner.getType())));
                final ClassicAdvisorBox.Builder b = ClassicAdvisorBox
                    .fromGameText(CARGO_CAPTURE, m, values);
                if (b == null) break;
                out.add(b.stopgap(title, null).build());
            }
            if (out.size() == amounts.size()) return out;
            out.clear();
        }
        final StringBuilder sb = new StringBuilder(
            Messages.message("captureGoodsDialog.title"));
        for (Map.Entry<GoodsType, Integer> e : amounts.entrySet()) {
            sb.append('\n').append(Messages.message(
                    new AbstractGoods(e.getKey(), e.getValue()).getLabel()));
        }
        out.add(ClassicGUI.notice("loot", sb.toString(), title, null));
        return out;
    }

    /**
     * A European nation's NAMES {@code @NATIONABBREV}.
     *
     * @param t The pack's texts, or null.
     * @param p The player, or null.
     * @return "Holl." etc., or null for another nation or without the texts.
     */
    static String abbrev(ClassicText t, Player p) {
        return names(t, "NATIONABBREV", p);
    }

    /** A NAMES.TXT section's first column in the row of a European nation, or null. */
    private static String names(ClassicText t, String section, Player p) {
        if (t == null || p == null) return null;
        final int i = Arrays.asList(ClassicNewWorldScreens.NATION_IDS)
            .indexOf(p.getNationId());
        final List<String[]> rows = t.names(section);
        if (i < 0 || rows == null || i >= rows.size() || rows.get(i).length == 0) {
            return null;
        }
        final String s = rows.get(i)[0].trim();
        return s.isEmpty() ? null : s;
    }


    // The recruits

    /**
     * The recruits' box (class comment): a list box (D2's rows at
     * {@link ClassicMenuBox#LIST_INDENT}) with one row per recruit in
     * FreeCol's names, the bar on row 1 (no {@code @default}), Escape and a
     * click beside it doing nothing: there is no "no" row, and FreeCol's
     * own dialog ignores Escape too (I).  With Brewster GAME.TXT
     * {@code @RECRUITCHOOSE} (our {@code @COUNTRY} and {@code @HOMEPORT})
     * over the priest, as {@code @UNREST}, the same message without the
     * choice (landfall #17902); at the Fountain of Youth {@code @LOSTCITY0}
     * over the frontiersman, as the {@code @LOSTCITY1} notice before it.
     * Without the texts, or for another nation, FreeCol's words.
     *
     * @param t The pack's texts, or null.
     * @param player Our player.
     * @param foy Whether it is the Fountain of Youth.
     * @param recruits The recruits ({@code Europe.getExpandedRecruitables}).
     * @param title The stopgap's window title.
     * @return The request.
     */
    static ClassicAdvisorBox.Request emigrationRequest(ClassicText t, Player player,
                                                       boolean foy,
                                                       List<AbstractUnit> recruits,
                                                       String title) {
        final List<String> rows = new ArrayList<>(recruits.size());
        for (AbstractUnit au : recruits) {
            rows.add(ClassicAdvisorBox.literal(Messages.message(au.getSingleLabel())));
        }
        ClassicAdvisorBox.Builder b = null;
        if (t != null) {
            if (foy) {
                b = ClassicAdvisorBox.fromGameText(LOST_CITY_CHOOSE,
                    t.message(LOST_CITY_CHOOSE), null);
                if (b != null) b.portrait(ClassicAdvisorBox.Portrait.SCOUT);
            } else {
                final String country = names(t, "COUNTRY", player);
                final String port = names(t, "HOMEPORT", player);
                if (country != null && port != null) {
                    final Map<String, String> values = new HashMap<>();
                    values.put("COUNTRY", country);
                    values.put("STRING0", port);
                    b = ClassicAdvisorBox.fromGameText(RECRUIT_CHOOSE,
                        t.message(RECRUIT_CHOOSE), values);
                    if (b != null) b.portrait(ClassicAdvisorBox.Portrait.PRIEST);
                }
            }
        }
        if (b == null) {
            final String choose = Messages.message("emigrationDialog.chooseImmigrant");
            b = ClassicAdvisorBox.Request.builder("emigration").freeColText((foy)
                ? Messages.message(LostCityRumour.RumourType.FOUNTAIN_OF_YOUTH
                    .getDescriptionKey()) + "\n" + choose
                : choose);
        }
        return b.rows(rows).defaultRow(0).noEscape().outsideCancels(false)
            .rowIndent(ClassicMenuBox.LIST_INDENT).stopgap(title, null).build();
    }


    // Negotiation

    /**
     * Whether a proposal is the peace treaty of a first contact between
     * Europeans: the contact context, and only peace both ways
     * ({@code DiplomaticTrade.makePeaceTreaty}).
     *
     * @param dt The proposal, or null.
     * @return True if it is accepted without a box (class comment).
     */
    static boolean isContactPeace(DiplomaticTrade dt) {
        if (dt == null || dt.getContext() != TradeContext.CONTACT
            || dt.getStatus() != DiplomaticTrade.TradeStatus.PROPOSE_TRADE
            || dt.getItems().isEmpty()) return false;
        for (TradeItem ti : dt.getItems()) {
            if (!(ti instanceof StanceTradeItem) || ti.getStance() != Stance.PEACE) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a proposal is our own, fresh one: the scout's negotiation at
     * a foreign colony, a ship's trade there.  It has no items yet; one
     * the server sends always has.  No server session waits for it.
     *
     * @param dt The proposal, or null.
     * @return True for our own.
     */
    static boolean isOwnProposal(DiplomaticTrade dt) {
        return dt != null && dt.getStatus() == DiplomaticTrade.TradeStatus.PROPOSE_TRADE
            && dt.getItems().isEmpty();
    }

    /**
     * A received proposal's text in FreeCol's words: its comment ("Lasst
     * uns mit ... verhandeln.") and a line per item, "giver: item".
     *
     * @param comment The comment, or null.
     * @param dt The proposal.
     * @return The text.
     */
    static String negotiationText(StringTemplate comment, DiplomaticTrade dt) {
        final StringBuilder sb = new StringBuilder();
        if (comment != null) sb.append(Messages.message(comment));
        for (TradeItem ti : dt.getItems()) {
            if (sb.length() > 0) sb.append('\n');
            final Player source = ti.getSource();
            if (source != null) {
                sb.append(Messages.message(source.getNationLabel())).append(": ");
            }
            sb.append(Messages.message(ti.getLabel()));
        }
        return sb.toString();
    }


    // A village, a tile

    /**
     * A native village's notice in FreeCol's words
     * ({@code IndianSettlementPanel}): its name and nation (and the
     * tension), the missionary, the skill taught, the most hated nation,
     * the wanted goods.  What we have not learned yet (the skill and goods
     * before a visit) stays "unbekannt", FreeCol's rule.
     *
     * @param is The village.
     * @param me Our player.
     * @return The text, one line per fact.
     */
    static String villageText(IndianSettlement is, Player me) {
        final boolean contacted = is.hasContacted(me);
        final boolean visited = is.hasVisited(me);
        final List<String> lines = new ArrayList<>();
        String head = Messages.message(is.getLocationLabelFor(me)) + ", "
            + Messages.message(StringTemplate.template((is.isCapital())
                    ? "indianSettlementPanel.indianCapital"
                    : "indianSettlementPanel.indianSettlement")
                .addStringTemplate("%nation%", is.getOwner().getNationLabel()));
        final Tension tension = is.getAlarm(me);
        if (tension != null) head += " (" + Messages.getName(tension) + ")";
        lines.add(head);
        final Unit missionary = is.getMissionary();
        if (missionary != null) {
            lines.add(Messages.message(missionary.getLabel(Unit.UnitLabelType.NATIONAL)));
        }
        lines.add(Messages.message("indianSettlementPanel.learnableSkill") + " "
            + Messages.message(is.getLearnableSkillLabel(visited)));
        lines.add(Messages.message("indianSettlementPanel.mostHated") + " "
            + Messages.message(is.getMostHatedLabel(contacted)));
        // FreeCol's label of a good not known yet is empty (its panel shows
        // no picture there); the notice says FreeCol's "Unbekannt".
        final String first = Messages.message(is.getWantedGoodsLabel(0, me).get(0));
        lines.add(Messages.message("indianSettlementPanel.highlyWanted") + " "
            + ((first.isEmpty())
                ? Messages.message("model.indianSettlement.wantedGoodsUnknown")
                : first));
        final List<String> others = new ArrayList<>();
        for (int i = 1; i < is.getWantedGoodsCount(); i++) {
            final String s = Messages.message(is.getWantedGoodsLabel(i, me).get(0));
            if (!s.isEmpty()) others.add(s);
        }
        if (!others.isEmpty()) {
            lines.add(Messages.message("indianSettlementPanel.otherWanted") + " "
                + String.join(", ", others));
        }
        return String.join("\n", lines);
    }

    /**
     * A tile's notice in FreeCol's words ({@code TilePanel}): its name and
     * place, region, owner, owning settlement, defence bonus, movement
     * cost, and what a free colonist would make there (FreeCol's numbers,
     * the house improvement), one goods per type.
     *
     * @param tile The tile.
     * @param me Our player, or null.
     * @return The text, one line per fact.
     */
    static String tileText(Tile tile, Player me) {
        final List<String> lines = new ArrayList<>();
        lines.add(Messages.message(StringTemplate.template("tilePanel.label")
                .addStringTemplate("%label%", tile.getLabel())
                .addAmount("%x%", tile.getX()).addAmount("%y%", tile.getY())));
        final Region region = tile.getRegion();
        if (region != null && (region.getKey() != null || region.getName() != null
                               || region.getType() != null)) {
            // A region without name or type has no label (a test map's).
            lines.add(Messages.message("tilePanel.region") + " "
                + Messages.message(region.getLabel()));
        }
        if (tile.getOwner() != null && tile.getOwner().getNationLabel() != null) {
            lines.add(Messages.message("tilePanel.owner") + " "
                + Messages.message(tile.getOwner().getNationLabel()));
        }
        if (tile.getOwningSettlement() != null) {
            final StringTemplate s = tile.getOwningSettlement().getLocationLabelFor(me);
            if (s != null) {
                lines.add(Messages.message("tilePanel.settlement") + " "
                    + Messages.message(s));
            }
        }
        final int defence = tile.getDefenceBonusPercentage();
        if (defence != 0) {
            lines.add(Messages.message("tilePanel.defenseBonus") + " " + defence + "%");
        }
        if (tile.getType() == null) return String.join("\n", lines);
        lines.add(Messages.message("tilePanel.movementCost") + " "
            + (tile.getType().getBasicMoveCost() / 3));
        final UnitType colonist = tile.getSpecification().getDefaultUnitType();
        final Map<GoodsType, Integer> made = new LinkedHashMap<>();
        for (ProductionType pt : tile.getType().getAvailableProductionTypes(false)) {
            for (AbstractGoods out : pt.getOutputList()) {
                int amount = out.getAmount();
                if (tile.getTileItemContainer() != null) {
                    amount = tile.getTileItemContainer().getTotalBonusPotential(
                        out.getType(), colonist, amount, true);
                }
                if (amount > 0) made.merge(out.getType(), amount, Math::max);
            }
        }
        final List<String> goods = new ArrayList<>();
        for (Map.Entry<GoodsType, Integer> e : made.entrySet()) {
            goods.add(e.getValue() + " " + Messages.getName(e.getKey()));
        }
        if (!goods.isEmpty()) lines.add(String.join(", ", goods));
        return String.join("\n", lines);
    }
}
