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

package net.sf.freecol.common.model.production;

import java.util.stream.Stream;

import net.sf.freecol.common.model.AbstractGoods;
import net.sf.freecol.common.model.BuildingType;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Modifier;
import net.sf.freecol.common.model.ProductionType;
import net.sf.freecol.common.model.Resource;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileImprovementType;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.common.option.GameOptions;


/**
 * The original's colony production where FreeCol's differs (part X; the
 * spec {@code freecol-spike-results/x-prep/production-rules.md}, its
 * changes 1 to 7), each behind a game option of the "levi" rules
 * ({@code GameOptions.ORIGINAL_COLONY_CENTER}, {@code ORIGINAL_LUMBER},
 * {@code ORIGINAL_FISHERMAN}, {@code ORIGINAL_HORSE_FOOD},
 * {@code ORIGINAL_ROAD_SILVER}); the classic and freecol rules have none
 * of them and keep FreeCol's numbers.  The rules are applied where FreeCol
 * computes the same numbers, so the colony's production, the colony
 * screen, the job menus and the AI see the same values:
 * <ul>
 *   <li>the colony's centre square ({@link #centerModifiers}):
 *       {@code TileProductionCalculator} for a colony's own centre only
 *       (V, seven colonies: clip 020 #2260, #14817, #123003, #134173,
 *       clip008 #4032, dago's Base, Fur City and Fur Town; and the hills
 *       of clip 051 #13792, 4 corn + 5 ore where FreeCol has 2 + 4).  A native
 *       settlement's land and the planners' site values
 *       ({@code Tile.getPotentialProduction}, {@code getMaximumPotential})
 *       stay FreeCol's;</li>
 *   <li>a road's lumber for an expert lumberjack, once
 *       ({@link #expertBonusDoubled}, {@code TileImprovementType}; V clip
 *       020 #75440 10 and #93178 6 on tropical forest with a road, clip
 *       049 #38577 Base SE 14 on conifer with a road (FreeCol 16), dago's
 *       Fur City S 14), and the Sons of Liberty bonus twice for lumber
 *       ({@link #rebelFactor}, {@code ProductionUtils}; I+: dago's Fur
 *       Town E 22, Base NE 30);</li>
 *   <li>the expert fisherman's + 2 ({@link #fishermanModifiers},
 *       {@code TileProductionCalculator}; V clip 020 #46108, #134173 6,
 *       clip 048 #13896 12 on a fishery, dago 10 at 100 %);</li>
 *   <li>the horses' half of the surplus rounded up ({@link #horseFood},
 *       {@code ProductionCache}; V dago's Fur City 1 of 1, Fur Town 11 of
 *       21);</li>
 *   <li>no road silver without a silver deposit ({@link #roadRaises},
 *       {@code TileImprovement} and {@code TileProductionCalculator}; D
 *       the manual, p. 27, and V clip 048 #47324, #57714: Silver City's
 *       servant «Silber-Bergarbeiter 1/1» on a mountain with a road and no
 *       deposit, where FreeCol makes 2);</li>
 *   <li>the Tory penalty (fixer B after part X, its own option
 *       {@code GameOptions.ORIGINAL_TORY_PENALTY}): one Tory later than
 *       FreeCol's classic levels ({@link #toryLimitOffset}; V «Entdecker»
 *       from 10 Tories, clip 049 #39267 and #40699), and in a building
 *       before the building's level factor ({@link #toryModifier}; V the
 *       Sägewerk, clip 048 #111902 and 049 #39270).</li>
 * </ul>
 * The Sons of Liberty percentage keeps FreeCol's rounding (down, as the
 * original's own pair in the saves shows, spec section 2.11).
 */
public final class OriginalProduction {

    /** The colony centre's extra food and extra second good (V). */
    public static final int CENTER_FOOD_BONUS = 2, CENTER_GOODS_BONUS = 1;

    /**
     * The centre bonus's modifier index: after a resource (10) and an
     * expert (30), before the Sons of Liberty bonus (35) and the plow
     * (50).  Additive, so only a resource's multiplier sees the order (I:
     * no centre with such a resource seen).
     */
    public static final int CENTER_INDEX = Modifier.COLONYTILE_PRODUCTION_INDEX - 1;

    /** The expert fisherman's extra fish over a colonist on the same water (V). */
    public static final int FISHERMAN_BONUS = 2;

    /** How many times the Sons of Liberty bonus counts for lumber (I+). */
    public static final float LUMBER_REBEL_FACTOR = 2.0f;

    /** The id of the silver deposit, the one resource a road raises silver with. */
    public static final String SILVER_DEPOSIT = "model.resource.silver";

    /** The source of the centre's extra yield. */
    public static final Specification.Source CENTER_SOURCE
        = new Specification.Source("model.source.originalColonyCenter");

    /**
     * How many Tories later than FreeCol's limits the original's penalty
     * starts: on «Entdecker» at 10 Tories, FreeCol's classic «very easy»
     * at 9 ({@code badGovernmentLimit} 8: more than 8).  V clip 049
     * #39267 «26% (4) 74% (10)» red and every worker 1 less, #40699 «28%
     * (4) 72% (9)» white and all back; clip 048 #111418 (10, red) and
     * #112088 (9, white); clip 050 #34423 (10, red).  The other levels
     * and the very bad government (-2) are not seen: FreeCol's limits
     * with the same offset (I).
     */
    public static final int TORY_LIMIT_OFFSET = 1;

    /**
     * The index of the original's penalty in a building: after the
     * expert's own modifier (30), so an expert loses what a colonist loses
     * (V clip 049 #39270: the Sägewerk 24 -&gt; 20, two expert carpenters
     * 12 -&gt; 10, I; a master distiller's x 2 of FreeCol before it would
     * take him 4).
     */
    public static final int TORY_INDEX = Modifier.EXPERT_PRODUCTION_INDEX + 1;


    private OriginalProduction() {}


    /**
     * Whether one of the original's rules holds.
     *
     * @param spec The specification (null: no).
     * @param option The game option ({@code GameOptions.ORIGINAL_...}).
     * @return True if the option is on; false for rules without it.
     */
    public static boolean on(Specification spec, String option) {
        if (spec == null || option == null) return false;
        try {
            return spec.getBoolean(option);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * The colony centre's extra yield (change 1 and 2): + 2 for a food
     * type, + 1 for the second good, for a good the square makes
     * unattended at all.
     *
     * @param goodsType The good.
     * @param tileType The square's type.
     * @return The additive modifier, or none.
     */
    public static Stream<Modifier> centerModifiers(GoodsType goodsType,
                                                   TileType tileType) {
        if (goodsType == null || tileType == null
            || tileType.getBaseProduction(null, goodsType, null) <= 0) {
            return Stream.<Modifier>empty();
        }
        final Modifier m = new Modifier(goodsType.getId(),
            (goodsType.isFoodType()) ? CENTER_FOOD_BONUS : CENTER_GOODS_BONUS,
            Modifier.ModifierType.ADDITIVE, CENTER_SOURCE, CENTER_INDEX);
        return Stream.of(m);
    }

    /**
     * Whether an improvement's bonus is multiplied by an expert's own
     * multiplier, as FreeCol does: not a road's lumber (change 3), the
     * original adds it after the expert's doubling.  A river (natural)
     * keeps FreeCol's rule (I: no river square seen).
     *
     * @param spec The specification.
     * @param improvement The improvement.
     * @param goodsType The good, the expert's own.
     * @return False for the road's lumber under the levi rule.
     */
    public static boolean expertBonusDoubled(Specification spec,
                                             TileImprovementType improvement,
                                             GoodsType goodsType) {
        return improvement == null || improvement.isNatural() || !isLumber(goodsType)
            || !on(spec, GameOptions.ORIGINAL_LUMBER);
    }

    /**
     * The extra factor of the Sons of Liberty bonus on a worked square:
     * 2 for lumber under the levi rule (change 4; I+, dago's Fur Town E 22
     * and Base NE 30 at 100 %), else 1.  A penalty (the Tories) keeps
     * FreeCol's rule, -1 for a colonist and an expert alike, not doubled
     * (V clip 048 #111902 «Holzfäller 5» against 049 #48833 6; 049 #39278
     * Base SE 14 -&gt; 13, an expert lumberjack on conifer with a road,
     * COLONY08).
     *
     * @param goodsType The good.
     * @param bonus The colony's production bonus.
     * @return The factor.
     */
    public static float rebelFactor(GoodsType goodsType, int bonus) {
        return (bonus > 0 && isLumber(goodsType)
                && on(goodsType.getSpecification(), GameOptions.ORIGINAL_LUMBER))
            ? LUMBER_REBEL_FACTOR : 1.0f;
    }

    /**
     * Whether a unit type at a good on a square is the expert fisherman at
     * his work: his expert good, a food, on water.
     *
     * @param unitType The worker.
     * @param goodsType The good.
     * @param tileType The square's type.
     * @return True if so.
     */
    public static boolean isExpertFishing(UnitType unitType, GoodsType goodsType,
                                          TileType tileType) {
        return unitType != null && goodsType != null && tileType != null
            && goodsType == unitType.getExpertProduction()
            && goodsType.isFoodType() && tileType.isWater();
    }

    /**
     * The expert fisherman's own modifiers under the levi rule (change
     * 5): his additive bonus is the original's + 2 (FreeCol: + 3); the
     * others stay.  A fishery's extra for experts is the resource's and
     * stays FreeCol's (V clip 048 #13896: 12 = (2 + 3 + 3 + 2) + 2).
     *
     * @param spec The specification.
     * @param mods The unit type's modifiers for the good.
     * @param unitType The worker.
     * @param goodsType The good.
     * @param tileType The square's type.
     * @return The modifiers to use.
     */
    public static Stream<Modifier> fishermanModifiers(Specification spec,
                                                      Stream<Modifier> mods,
                                                      UnitType unitType,
                                                      GoodsType goodsType,
                                                      TileType tileType) {
        if (!isExpertFishing(unitType, goodsType, tileType)
            || !on(spec, GameOptions.ORIGINAL_FISHERMAN)) return mods;
        return mods.map(m -> {
                if (m.getType() != Modifier.ModifierType.ADDITIVE
                    || m.getValue() <= 0) return m;
                final Modifier o = Modifier.makeModifier(m);
                o.setValue(FISHERMAN_BONUS);
                return o;
            });
    }

    /**
     * The horses' share of a food surplus (change 6): FreeCol's half,
     * rounded up under the levi rule, down (FreeCol) else.
     *
     * @param spec The specification.
     * @param share The surplus after the consumer's modifiers (the half).
     * @return The share.
     */
    public static int horseFood(Specification spec, float share) {
        return (on(spec, GameOptions.ORIGINAL_HORSE_FOOD))
            ? (int)Math.ceil(share) : (int)share;
    }

    /**
     * Whether an improvement raises a good on a square: not a road's
     * silver without a silver deposit there under the levi rule (change
     * 7; the manual, p. 27).  A river (natural) keeps FreeCol's rule.
     *
     * @param spec The specification.
     * @param improvement The improvement.
     * @param goodsType The good.
     * @param tile The square (null: not known, as FreeCol).
     * @return False for the road's silver on a square without a deposit.
     */
    public static boolean roadRaises(Specification spec,
                                     TileImprovementType improvement,
                                     GoodsType goodsType, Tile tile) {
        if (improvement == null || improvement.isNatural() || tile == null
            || !isSilver(goodsType) || !on(spec, GameOptions.ORIGINAL_ROAD_SILVER)) {
            return true;
        }
        final Resource r = tile.getResource();
        return r != null && r.getType() != null
            && SILVER_DEPOSIT.equals(r.getType().getId());
    }

    /**
     * How many Tories later the penalty starts than the rules' limits
     * ({@code badGovernmentLimit}, {@code veryBadGovernmentLimit}) say:
     * {@link #TORY_LIMIT_OFFSET} under the levi rule, else 0 (FreeCol).
     *
     * @param spec The specification.
     * @return The offset.
     */
    public static int toryLimitOffset(Specification spec) {
        return (on(spec, GameOptions.ORIGINAL_TORY_PENALTY)) ? TORY_LIMIT_OFFSET : 0;
    }

    /**
     * The Tory penalty of a worker in a building under the levi rule: the
     * colony's penalty times the building's level factor
     * ({@link #levelFactor}), at {@link #TORY_INDEX}, after the expert's
     * own modifier: what the original takes from the worker's production
     * before the building multiplies it (V clip 048 #111902 «Hämmer 4/6»
     * for a colonist in the Sägewerk, «Rum 2/3» in the Schnapsbrennerei of
     * level 1; 049 #39270 the Sägewerk 24 -&gt; 20).  FreeCol takes the
     * penalty times the building's {@code rebel-factor} (2 only for the
     * Sägewerk and the cathedral, 1.5 for the factories, else 1) before
     * the expert's modifier.
     *
     * @param buildingType The building.
     * @param goodsType The good made.
     * @param bonus The colony's production bonus.
     * @return The modifier, or null where FreeCol's rule holds (no
     *     penalty, the option off, no building).
     */
    public static Modifier toryModifier(BuildingType buildingType,
                                        GoodsType goodsType, int bonus) {
        if (bonus >= 0 || buildingType == null || goodsType == null
            || !on(goodsType.getSpecification(), GameOptions.ORIGINAL_TORY_PENALTY)) {
            return null;
        }
        final float f = levelFactor(buildingType, goodsType);
        final Modifier m = new Modifier(goodsType.getId(), (float)Math.floor(bonus * f),
            Modifier.ModifierType.ADDITIVE, Specification.SOL_MODIFIER_SOURCE, TORY_INDEX);
        return m;
    }

    /**
     * A building's level factor for a good: what a worker makes in it
     * over what he makes in the lowest level of its line that makes the
     * good (the Sägewerk 6 / 3 = 2, a factory 9 / 3 = 3); the building's
     * FreeCol {@code rebel-factor} where no level makes it.
     *
     * @param buildingType The building.
     * @param goodsType The good.
     * @return The factor.
     */
    public static float levelFactor(BuildingType buildingType, GoodsType goodsType) {
        final int own = baseOutput(buildingType, goodsType);
        int low = own;
        for (BuildingType b = buildingType.getUpgradesFrom(); b != null;
             b = b.getUpgradesFrom()) {
            final int n = baseOutput(b, goodsType);
            if (n > 0) low = n;
        }
        return (own > 0 && low > 0) ? (float)own / low : buildingType.getRebelFactor();
    }

    /**
     * What a worker's production type of a building makes of a good.
     *
     * @param buildingType The building.
     * @param goodsType The good.
     * @return The amount, 0 for none.
     */
    private static int baseOutput(BuildingType buildingType, GoodsType goodsType) {
        for (ProductionType pt : buildingType.getAvailableProductionTypes(false)) {
            final AbstractGoods ag = pt.getOutput(goodsType);
            if (ag != null && ag.getAmount() > 0) return ag.getAmount();
        }
        return 0;
    }

    /**
     * Lumber: the raw material of the unstorable building material, the
     * hammers (FreeCol's own test, e.g. {@code AIColony}).
     *
     * @param goodsType The good.
     * @return True for lumber.
     */
    static boolean isLumber(GoodsType goodsType) {
        return goodsType != null && goodsType.isRawMaterialForUnstorableBuildingMaterial()
            && !goodsType.isFoodType();
    }

    /**
     * Silver: the good the silver deposit raises.
     *
     * @param goodsType The good.
     * @return True for silver.
     */
    static boolean isSilver(GoodsType goodsType) {
        return goodsType != null && "model.goods.silver".equals(goodsType.getId());
    }
}
