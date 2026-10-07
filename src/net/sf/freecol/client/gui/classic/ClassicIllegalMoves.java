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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Direction;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Settlement;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileImprovement;
import net.sf.freecol.common.model.TileImprovementType;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.option.GameOptions;


/**
 * The original's refusals (R3): an order the game refuses gets GAME.TXT's
 * box for it, where the original has one, instead of FreeCol's illegal-move
 * sound and nothing (Roger: "Handelsschiffe können nicht angreifen" came
 * without a word).  Pure and headless: it only reads the model, so the
 * Classic UI can ask it before the controller sees the order.
 *
 * <ul>
 *   <li><b>Moves</b> ({@link #judge}): FreeCol's own move rule
 *   ({@code Unit.getMoveType}) decides; a refused type maps to its box:
 *   a merchantman, caravel or galleon into a foreign ship @SHIPCOMBAT
 *   (admiral), a civilian into a foreign unit or colony @CANNOTATTACK
 *   (soldier), a passenger or a loaded ship onto a square the enemy holds
 *   @LANDFIRST (frontiersman), a ship or wagon train into a foreign colony
 *   at war or not yet contacted @TRADEATWAR (its own words: "... oder noch
 *   keine Beziehungen aufgenommen haben", R3 verifier V2.1), contacted at
 *   peace without de Witt @TRADEMERCANTILISM in FreeCol's words (its
 *   {@code %STRING0} is not known), a ship at an uncontacted village
 *   @DONTKNOWSHIPS (admiral), an empty trader at a village @TRADENOCARGO
 *   (the chief), a ship of the rebels past the edge @EUROPENOTLEAVE
 *   (admiral).  A refused move the original has no words for (into the sea
 *   without our ship, onto a full ship, off the map, an empty ship onto
 *   land) is consumed silently.  Legal moves, a move without moves left
 *   (FreeCol's SKIPPED) and the village's skill and mission answers (D11)
 *   go to the controller as before, and so does a landing (W8b).</li>
 *   <li><b>B</b> ({@link #colonyRefusal}, {@link #siteRefusal}): a ship
 *   @SEACOLONY, a unit type that cannot found colonies @ONLYCOL, the war of
 *   independence with the option off @NOCOLONIESEITHER (never in levi,
 *   Roger's row 11); a colonist without moves silent.  FreeCol's own site
 *   refusals in the original's words: at sea @SEACOLONY, on mountains
 *   @TOOMOUNTAIN, next to a colony @TOONEAR with its name (FreeCol's rule
 *   unchanged: only where FreeCol refuses).</li>
 *   <li><b>P and R</b> ({@link #orderRefusal}) when their order cannot be
 *   given: a land unit that is no pioneer @ONLYPIO, P on ploughed land
 *   @NOPLOW, R on a road @NOROAD; anything else (a ship, no moves, foreign
 *   land) silent.</li>
 * </ul>
 * A refusal costs nothing: the unit keeps its moves and stays the active
 * unit (Roger's house rule, levi {@code cancelKeepsMove}; the original's
 * @LEARNMASTER ended the unit's turn, clip008 01-scout section 5).  The
 * portraits are inferred per box from their families (V: the admiral at
 * @SAILHOME, the soldier at @WHACKINDIANS, the frontiersman at @LANDFALL
 * and @NOPORT, the chief at the village's answers); no clip shows any of
 * these boxes.
 */
final class ClassicIllegalMoves {

    /** A refusal: its box, or none. */
    static final class Verdict {

        /** The GAME.TXT section, or null: the order does nothing. */
        final String section;

        /** Who stands at the box. */
        final ClassicNotices.Who who;

        /** The tribe's {@link ClassicGUI#TRIBES} index (the chief's), or -1. */
        final int tribe;

        /** The placeholders' values ({@code STRING0}), never null. */
        final Map<String, String> values;

        /** FreeCol's words: without the pack, or always ({@link #freeColOnly}). */
        final StringTemplate freeCol;

        /** Whether the box takes FreeCol's words even with the pack. */
        final boolean freeColOnly;

        Verdict(String section, ClassicNotices.Who who, int tribe,
                Map<String, String> values, StringTemplate freeCol,
                boolean freeColOnly) {
            this.section = section;
            this.who = who;
            this.tribe = tribe;
            this.values = (values == null) ? Collections.<String, String>emptyMap()
                : values;
            this.freeCol = freeCol;
            this.freeColOnly = freeColOnly;
        }

        /** @return Whether the order is consumed without a box. */
        boolean isSilent() {
            return this.section == null;
        }

        @Override
        public String toString() {
            return (this.section == null) ? "silent" : this.section + "/" + this.who;
        }
    }

    /** The order does nothing: no box, no sound, no move. */
    static final Verdict SILENT = new Verdict(null, ClassicNotices.Who.NONE, -1,
        null, null, false);

    /** The prefix of the fallback words without the pack. */
    static final String FALLBACK = "classic.refusal.";

    /** FreeCol's id of the land a colony cannot stand on. */
    static final String MOUNTAINS = "model.tile.mountains";

    /** FreeCol's message id of the controller's site refusals. */
    static final String NO_CLAIM = "model.noClaimReason.";


    private ClassicIllegalMoves() {}   // static helpers only


    /** A box in the original's words, FreeCol's fallback under {@link #FALLBACK}. */
    private static Verdict box(String section, ClassicNotices.Who who) {
        return new Verdict(section, who, -1, null,
            StringTemplate.template(FALLBACK + section), false);
    }

    /**
     * A move order's refusal (class comment).
     *
     * @param unit The unit ordered.
     * @param direction The direction.
     * @return Null when the controller takes the order (a legal move, no
     *     moves left, a landing, the village's skill and mission answers),
     *     {@link #SILENT}, or the box.
     */
    static Verdict judge(Unit unit, Direction direction) {
        if (unit == null || direction == null || !unit.hasTile()
            || unit.getOwner() == null) return null;
        final Player owner = unit.getOwner();
        final Tile to = unit.getTile().getNeighbourOrNull(direction);
        // The rebels' ship past the edge: Europe is closed in the war
        // (@EUROPENOTLEAVE; Roger's Europe question needs Europe).
        if (owner.isRebel() && owner.getEurope() == null
            && ClassicGUI.sailsPastView(unit, direction)) {
            return box("EUROPENOTLEAVE", ClassicNotices.Who.ADMIRAL);
        }
        final Unit.MoveType mt = unit.getMoveType(direction);
        switch (mt) {
        case MOVE_NO_ATTACK_CIVILIAN:
            return (unit.isNaval()) ? box("SHIPCOMBAT", ClassicNotices.Who.ADMIRAL)
                : (unit.isOnCarrier()) ? box("LANDFIRST", ClassicNotices.Who.SCOUT)
                : box("CANNOTATTACK", ClassicNotices.Who.SOLDIER);
        case MOVE_NO_ATTACK_MARINE:
        case MOVE_NO_ACCESS_WATER:
            return box("LANDFIRST", ClassicNotices.Who.SCOUT);
        case MOVE_NO_ACCESS_LAND: {
            // A ship onto land: the landing (W8b) when a passenger can go,
            // FreeCol's moveDisembark refusal when a foreign unit is there.
            final Unit first = (to == null) ? null : to.getFirstUnit();
            if (first != null && first.getOwner() != owner) {
                return (unit.getUnitCount() > 0)
                    ? box("LANDFIRST", ClassicNotices.Who.SCOUT) : SILENT;
            }
            return (ClassicGUI.firstLander(unit, to) == null) ? SILENT : null;
        }
        case MOVE_NO_ACCESS_SETTLEMENT:
            return box("CANNOTATTACK", ClassicNotices.Who.SOLDIER);
        case MOVE_NO_ACCESS_WAR:
            return box("TRADEATWAR", ClassicNotices.Who.NONE);
        case MOVE_NO_ACCESS_TRADE: {
            final Settlement s = (to == null) ? null : to.getSettlement();
            final Player other = (s == null) ? null : s.getOwner();
            if (other == null || !owner.hasContacted(other)
                || owner.atWarWith(other)) {
                return box("TRADEATWAR", ClassicNotices.Who.NONE);
            }
            return new Verdict("TRADEMERCANTILISM", ClassicNotices.Who.NONE, -1,
                null, StringTemplate.template("move.noAccessTrade")
                    .addStringTemplate("%nation%", other.getNationLabel()), true);
        }
        case MOVE_NO_ACCESS_CONTACT:
            return box("DONTKNOWSHIPS", ClassicNotices.Who.ADMIRAL);
        case MOVE_NO_ACCESS_GOODS: {
            final Settlement s = (to == null) ? null : to.getSettlement();
            return new Verdict("TRADENOCARGO", ClassicNotices.Who.CHIEF,
                ClassicGUI.tribeIndex((s == null) ? null : s.getOwner()), null,
                StringTemplate.template(FALLBACK + "TRADENOCARGO"), false);
        }
        case MOVE_NO_EUROPE:
            return box("EUROPENOTLEAVE", ClassicNotices.Who.ADMIRAL);
        case MOVE_NO_ACCESS_EMBARK: case MOVE_NO_ACCESS_FULL:
        case MOVE_NO_ACCESS_BEACHED: case MOVE_NO_REPAIR:
        case MOVE_NO_TILE: case MOVE_ILLEGAL:
            return SILENT;
        default:
            return null;
        }
    }

    /**
     * B with a unit that cannot found a colony now
     * ({@code Unit.canBuildColony} false), as the unit's refusal.
     *
     * @param unit The unit.
     * @return Null when it can found one (the controller then checks the
     *     site, {@link #siteRefusal}), {@link #SILENT}, or the box.
     */
    static Verdict colonyRefusal(Unit unit) {
        if (unit == null || !unit.hasTile()) return SILENT;
        if (unit.isNaval()) return box("SEACOLONY", ClassicNotices.Who.SCOUT);
        if (!unit.getType().canBuildColony()) {
            return box("ONLYCOL", ClassicNotices.Who.NONE);
        }
        if (unit.getMovesLeft() <= 0) return SILENT;
        final Player owner = unit.getOwner();
        if (owner != null && owner.isRebel() && !unit.getSpecification()
            .getBoolean(GameOptions.FOUND_COLONY_DURING_REBELLION)) {
            return box("NOCOLONIESEITHER", ClassicNotices.Who.NONE);
        }
        return null;
    }

    /**
     * FreeCol's refusal of a colony's site
     * ({@code InGameController.buildColony}: the message
     * {@code model.noClaimReason.*}) in the original's words, from the
     * founding unit's tile.  Only where FreeCol refuses: its rule stays.
     *
     * @param unit The founding unit (the active unit).
     * @param id The message id.
     * @return The box, or null for FreeCol's words.
     */
    static Verdict siteRefusal(Unit unit, String id) {
        if (unit == null || id == null || !id.startsWith(NO_CLAIM)
            || !unit.hasTile() || unit.getOwner() == null) return null;
        final Tile tile = unit.getTile();
        switch (unit.getOwner().canClaimToFoundSettlementReason(tile)) {
        case TERRAIN:
            return (!tile.isLand()) ? box("SEACOLONY", ClassicNotices.Who.SCOUT)
                : (MOUNTAINS.equals(tile.getType().getId()))
                ? box("TOOMOUNTAIN", ClassicNotices.Who.SCOUT) : null;
        case SETTLEMENT: case WORKED: case EUROPEANS: {
            final Colony near = nearColony(tile);
            if (near == null) return null;
            final Map<String, String> values = new HashMap<>();
            values.put("STRING0", near.getName());
            return new Verdict("TOONEAR", ClassicNotices.Who.SCOUT, -1, values,
                StringTemplate.template(FALLBACK + "TOONEAR")
                    .addName("%colony%", near.getName()), false);
        }
        default:
            return null;
        }
    }

    /**
     * The colony a site is too near: the one that owns the tile when it is
     * next to it, else the first colony next to it.
     *
     * @param tile The site.
     * @return The colony, or null.
     */
    static Colony nearColony(Tile tile) {
        final Settlement owning = tile.getOwningSettlement();
        if (owning instanceof Colony && owning.getTile() != null
            && owning.getTile().isAdjacent(tile)) return (Colony) owning;
        final List<Colony> adjacent = tile.getAdjacentColonies();
        return (adjacent.isEmpty()) ? null : adjacent.get(0);
    }

    /**
     * P or R when FreeCol's order cannot be given (its action is disabled):
     * the cause, in this order (R3 verifier V2.4): a land unit that is no
     * pioneer, P on ploughed land, R where a road is; else nothing.
     *
     * @param unit The active unit, or null.
     * @param road True for R (a road), false for P (plough or clear).
     * @return {@link #SILENT} or the box.
     */
    static Verdict orderRefusal(Unit unit, boolean road) {
        if (unit == null || !unit.hasTile() || unit.isNaval()) return SILENT;
        final Tile tile = unit.getTile();
        final TileImprovementType type = unit.getSpecification()
            .getTileImprovementType((road) ? "model.improvement.road"
                : (tile.isForested()) ? "model.improvement.clearForest"
                : "model.improvement.plow");
        if (type == null) return SILENT;
        if (!type.isWorkerAllowed(unit)) return box("ONLYPIO", ClassicNotices.Who.NONE);
        if (!road) {
            final TileImprovement plow = tile.getTileImprovement(unit
                .getSpecification().getTileImprovementType("model.improvement.plow"));
            if (plow != null && plow.isComplete()) {
                return box("NOPLOW", ClassicNotices.Who.NONE);
            }
        } else {
            final TileImprovement r = tile.getRoad();
            if (r != null && r.isComplete()) return box("NOROAD", ClassicNotices.Who.NONE);
        }
        return SILENT;
    }

    /**
     * A refusal's box: GAME.TXT's words with the portrait, a notice (any key
     * closes it); FreeCol's words without the pack.
     *
     * @param t The pack's texts, or null.
     * @param v The refusal, with a section.
     * @param showAtNanos When the box should be on screen, 0 for at once.
     * @param title The stopgap's window title.
     * @return The box.
     */
    static ClassicAdvisorBox.Request request(ClassicText t, Verdict v,
                                             long showAtNanos, String title) {
        final ClassicText.Message m = (t == null || v.freeColOnly) ? null
            : t.message(v.section);
        ClassicAdvisorBox.Builder b = (m == null || m.text.isEmpty()) ? null
            : ClassicAdvisorBox.fromGameText(v.section, m, v.values);
        if (b == null) {
            b = ClassicAdvisorBox.Request.builder(v.section)
                .freeColText(Messages.message(v.freeCol));
        }
        return b.portrait(ClassicNotices.portrait(v.who, v.tribe))
            .showAt(showAtNanos).stopgap(title, null).build();
    }
}
