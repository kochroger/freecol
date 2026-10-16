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

import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Modifier;
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
 *       clip008 #4032, dago's Base, Fur City and Fur Town).  A native
 *       settlement's land and the planners' site values
 *       ({@code Tile.getPotentialProduction}, {@code getMaximumPotential})
 *       stay FreeCol's;</li>
 *   <li>a road's lumber for an expert lumberjack, once
 *       ({@link #expertBonusDoubled}, {@code TileImprovementType}; V clip
 *       020 #75440 10 and #93178 6 on tropical forest with a road, dago's
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
 *       the manual, p. 27).</li>
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
     * 2 for lumber under the levi rule (change 4), else 1.  A penalty (the
     * Tories) keeps FreeCol's rule (I: not seen).
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
