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

package net.sf.freecol.common.option;


/**
 * Keeps track of the available game options. New options must be
 * added to the {@link net.sf.freecol.common.model.Specification} and
 * each option should be given an unique identifier (defined as a
 * constant in this class).
 */
public class GameOptions {

    public static final String TAG = "gameOptions";

    /** Map game option group. */
    public static final String GAMEOPTIONS_MAP
        = "gameOptions.map";
    
    /** The number of turns to sail between Europe and the New World. */
    public static final String TURNS_TO_SAIL
        = "model.option.turnsToSail";

    /** Changes the settlement limits. */
    public static final String SETTLEMENT_LIMIT_MODIFIER
        = "model.option.settlementLimitModifier";

    /** Enables/disables fog of war. */
    public static final String FOG_OF_WAR
        = "model.option.fogOfWar";

    /** Whether to award exploration points or not. */
    public static final String EXPLORATION_POINTS
        = "model.option.explorationPoints";

    /** Allow amphibious moves. */
    public static final String AMPHIBIOUS_MOVES
        = "model.option.amphibiousMoves";

    /** Allow empty units to trade. */
    public static final String EMPTY_TRADERS
        = "model.option.emptyTraders";

    /** Do settlement actions consume the chief contact bonus. */
    public static final String SETTLEMENT_ACTIONS_CONTACT_CHIEF
        = "model.option.settlementActionsContactChief";

    /**
     * House rule: a human player's unit asked whether to learn at a
     * native settlement keeps its moves until it accepts, so declining
     * (or a settlement with nothing to teach) costs no move.  On in the
     * "levi" rules; rules and saves without it spend the move.
     */
    public static final String CANCEL_KEEPS_MOVE
        = "model.option.cancelKeepsMove";

    /**
     * The original's fortification: a unit whose fortification completes
     * at the turn start keeps its moves for that turn (a click frees it
     * and it can move at once).  FreeCol takes them.  On in the "levi"
     * rules; rules and saves without it take the moves.
     */
    public static final String FORTIFY_KEEPS_MOVES
        = "model.option.fortifyKeepsMoves";

    /**
     * The original's treaty breach (clip opening_018, B3): a human
     * player's offensive unit that fortifies next to, or on the land of,
     * a colony of a European he is at peace (or cease fire) with breaks
     * the treaty, after the original's question; the server declares war
     * and the fortified unit occupies the tile, which becomes the
     * occupier's land ({@code Unit.getFortifyWarColony}).  On in the
     * "levi" rules; rules and saves without it play as FreeCol does (no
     * war, an occupied tile at war loses its owner).
     */
    public static final String FORTIFY_DECLARES_WAR
        = "model.option.fortifyDeclaresWar";

    /**
     * The original's arrival in Europe (part T3, Roger's screenshots
     * 2026-10-14, freecol-spike-results/t/roger-evidence): when a human
     * player's ship arrives in Europe, every unit aboard goes onto the
     * dock at once, marked "S" (SENTRY: boards the next ship that sails);
     * the holds keep only the goods.  FreeCol keeps them aboard.  On in
     * the "levi" rules (and a levi save from before the rule); rules and
     * saves without it keep them aboard.  AI players and the REF never.
     */
    public static final String EUROPE_ARRIVAL_DOCKS
        = "model.option.europeArrivalDocks";

    /**
     * The original's jobs after a colonist is moved on the colony screen
     * (part U, build spec M4.8 rules R2 and R3; clip 020 C5, C10, C14 and
     * dago D3): one dragged from a building onto a work tile becomes a
     * Farmer whatever the tile or his expertise, one dragged from tile to
     * tile keeps his job even where it makes nothing, but a food job
     * follows the square (the review of part Y: a Fischer onto land a
     * Farmer, V clip 053; a Farmer onto water a Fischer, I; the client's
     * {@code ClassicColonyMoves.keptJob}).  FreeCol lets the
     * colony choose the work for the tile.  And (part Y3) a colonist whose
     * job his field's square cannot make makes nothing there (V clip 020
     * #48767, #50256, clip 046 #98141: his job's icon under the red sign,
     * the colony's food unchanged), where FreeCol gives him the square's
     * best other production ({@code ColonyTile.getBestProductionType},
     * {@code OriginalProduction.makesNothing}).  On in the "levi" rules
     * (and a levi save from before the rule); rules and saves without it
     * play as FreeCol does.
     */
    public static final String ORIGINAL_DROP_JOBS
        = "model.option.originalDropJobs";

    /**
     * The original's price for finishing a build at once (part V, build
     * spec M4.10 rule R1; part X with Roger's playthrough 4): 13 per
     * missing hammer and 6 per missing tool, the whole doubled while no
     * hammer of the build is stored yet (all 23 KAUFEN boxes of clips
     * 045-048 fit, e.g. 048 #70208: 4760 = 2 x (13 x 160 + 6 x 50) with
     * the hammer area empty, 046 #119741: 484 = 13 x 28 + 6 x 20; clip 020
     * #94078: 494 for 38 missing hammers, dago-colony2: 1248 for 96).
     * FreeCol prices a hammer at the goods type's price (20) and the tools
     * at the market's ask plus 10 percent.  On in the "levi" rules (and a
     * levi save from before the rule).
     */
    public static final String ORIGINAL_BUILD_PRICE
        = "model.option.originalBuildPrice";

    /**
     * The original's completed build (part V, build spec M4.10 rule R4;
     * clip 020 #113, #159, #229): a building finished while nothing else
     * is queued stays the colony's current build, "(ABGESCHLOSSEN)", until
     * the player chooses another, and no turn warns that nothing is being
     * built.  FreeCol drops it from the queue.  On in the "levi" rules
     * (and a levi save from before the rule).
     */
    public static final String ORIGINAL_BUILD_COMPLETION
        = "model.option.originalBuildCompletion";

    /**
     * The original's colony reports at the turn start (part W, build spec
     * M4.11): "{Verlag} im Bau in {Base} benötigt {20 Werkzeuge} ..."
     * (@NEEDTOOLS) every turn while a build has all its hammers and lacks
     * tools (clip 020 #35, #69, #75, #85, #91: the stock 10, 10, 13, 16,
     * 19 with the Verlag's 52 hammers stored), and "Eine neue Ladung
     * {Rum} liegt in {Base} bereit" (@CARGOREADY0-2) when a good's stock
     * passes a multiple of 100 (clip 020 #236 rum 103 with a Lagerhaus,
     * clip 040 #84 666 horses at the capacity 300).  FreeCol warns of a
     * build whose missing goods nobody makes, and of a store past its
     * high-water mark or full.  On in the "levi" rules (and a levi save
     * from before the rule).
     */
    public static final String ORIGINAL_COLONY_REPORTS
        = "model.option.originalColonyReports";

    /**
     * The original's docking (part W, build spec M4.12 rule R5; clip008
     * #29957 -&gt; #32928): a ship of a human player that sails into one of
     * its own colonies has no moves left there; it sails on next turn.
     * FreeCol charges only the move's cost.  Its passengers go ashore
     * active with the moves they had (fixer B after part X, FIX-LIST must
     * 8: clip 051 #13459, the soldier sentried aboard since 1492 ashore
     * with «Züge: 1»; clip 050 #33331 the Waffenschmied); FreeCol's
     * disembarking takes their moves.  On in the "levi" rules (and a levi
     * save from before the rule).  AI players never (I: the original's
     * computer ships are not seen docking).
     */
    public static final String DOCKING_ENDS_MOVES
        = "model.option.dockingEndsMoves";

    /**
     * The original's colony centre square (part X, change 1 and 2 of
     * freecol-spike-results/x-prep/production-rules.md): the colony's own
     * centre makes 2 food and 1 of its second good more than FreeCol's
     * unattended value (seven colonies of the clips and dago's save, e.g.
     * clip 020 #2260 swamp, plowed: 6 food + 3 sugar where FreeCol has
     * 4 + 2).  Only the colony's centre: a native settlement's land and
     * the AI's site ranking keep FreeCol's values.  On in the "levi" rules
     * (and a levi save from before the rule).
     */
    public static final String ORIGINAL_COLONY_CENTER
        = "model.option.originalColonyCenter";

    /**
     * The original's lumber (part X, change 3 and 4): an expert lumberjack
     * gets a road's +2 once, not doubled (clip 020 #75440: 10 on tropical
     * forest with a road where FreeCol has 12), and the Sons of Liberty
     * bonus counts twice for lumber (dago's Fur Town E 22 at 100 %, I+).
     * On in the "levi" rules (and a levi save from before the rule).
     */
    public static final String ORIGINAL_LUMBER
        = "model.option.originalLumber";

    /**
     * The original's expert fisherman (part X, change 5): 2 fish more than
     * a colonist on the same water, FreeCol 3 (clip 020 #46108 and #134173:
     * 6 on a coastal ocean square where a colonist makes 4; clip 048
     * #13896: 12 on a fishery).  On in the "levi" rules (and a levi save
     * from before the rule).
     */
    public static final String ORIGINAL_FISHERMAN
        = "model.option.originalFisherman";

    /**
     * The original's horses (part X, change 6): they take half the food
     * surplus rounded up, FreeCol rounds down (dago's Fur City: a surplus
     * of 1 breeds 1; Fur Town: 21 gives 11).  On in the "levi" rules (and
     * a levi save from before the rule).
     */
    public static final String ORIGINAL_HORSE_FOOD
        = "model.option.originalHorseFood";

    /**
     * The original's roads and silver (part X, change 7; the manual, p.
     * 27: "Building a road in a mountain square does not increase the
     * production of silver unless there is a silver deposit in the
     * square"): a road raises silver only on a square with a silver
     * deposit, FreeCol everywhere.  On in the "levi" rules (and a levi
     * save from before the rule).
     */
    public static final String ORIGINAL_ROAD_SILVER
        = "model.option.originalRoadSilver";

    /**
     * The original's Tory penalty (fixer B after part X, the colony
     * review's FIX-LIST must 7; clip 049 #39267 and #40699, clip 048
     * #111418 and #112088, clip 050 #34423): on «Entdecker» the penalty
     * starts at 10 Tories, not at FreeCol's 9, the Tories counted as the
     * colony screen shows them (the rebels rounded; FreeCol rounds down;
     * clip 049 #38856 «28% (4) 72% (9)» of 13 white), one Tory later on every
     * level, the very bad government too (FreeCol's ratios with the same
     * offset, I), and in a building each worker loses the penalty before
     * the building's level factor (V: a colonist in a Sägewerk 6 -&gt; 4,
     * two expert carpenters 24 -&gt; 20; I: the other level 2 buildings and
     * the factories' level 3 by the same rule; FreeCol takes only 1 from a
     * colonist in most level 2 and 3 buildings).  The option texts name
     * the «Entdecker» threshold and the Sägewerk as seen, the other
     * levels, the -2 limit and the factories as guesses (the xg fixer).
     * On in the "levi" rules (and a levi save from before the rule).
     */
    public static final String ORIGINAL_TORY_PENALTY
        = "model.option.originalToryPenalty";

    /**
     * The original's founding father offer after Escape (fixer B after
     * part X, the colony review's FIX-LIST must 10; Roger 2026-10-16 and
     * clip 051 #12219 -&gt; #14555: Escape on the choice box, and the next
     * turn's offer has three new names of five): a choice declined
     * draws a new offer at the next turn start.  FreeCol offers the same
     * fathers again.  On in the "levi" rules (and a levi save from before
     * the rule).
     */
    public static final String ORIGINAL_FATHER_OFFER
        = "model.option.originalFatherOffer";

    /** Do missionaries provide extra benefits. */
    public static final String ENHANCED_MISSIONARIES
        = "model.option.enhancedMissionaries";

    /** The strength of the influence of a Mission to the
        alarm level of the natives in a settlement. */
    public static final String MISSION_INFLUENCE
        = "model.option.missionInfluence";

    /** Probability of a settlement with a surplus offering a gift. */
    public static final String GIFT_PROBABILITY
        = "model.option.giftProbability";

    /** Probability of an angry settlement making a demand. */
    public static final String DEMAND_PROBABILITY
        = "model.option.demandProbability";

    public static final String ALARM_BONUS_BUY
        = "model.option.alarmBonusBuy";

    public static final String ALARM_BONUS_SELL
        = "model.option.alarmBonusSell";

    public static final String ALARM_BONUS_GIFT
        = "model.option.alarmBonusGift";

    /** Maximum number of goods a native settlement will offer for sale. */
    public static final String SETTLEMENT_NUMBER_OF_GOODS_TO_SELL
        = "model.option.settlementNumberOfGoodsToSell";

    /** Continue recruiting founding fathers after declaring independence. */
    public static final String CONTINUE_FOUNDING_FATHER_RECRUITMENT
        = "model.option.continueFoundingFatherRecruitment";

    /** Does the REF "teleport" to its first target. */
    public static final String TELEPORT_REF
        = "model.option.teleportREF";

    /**
     * Use map defined starting positions (if available).
     */
    public static final String MAP_DEFINED_STARTING_POSITIONS = "model.option.mapDefinedStartingPositions";
    
    /** How to determine the starting positions of European players. */
    public static final String STARTING_POSITIONS
        = "model.option.startingPositions";
    public static final int STARTING_POSITIONS_CLASSIC = 0;
    public static final int STARTING_POSITIONS_RANDOM = 1;
    public static final int STARTING_POSITIONS_HISTORICAL = 2;

    /** Percent chance peace will hold if there is a recent treaty. */
    public static final String PEACE_PROBABILITY
        = "model.option.peaceProbability";

    /** Initial immigration target value. */
    public static final String INITIAL_IMMIGRATION
        = "model.option.initialImmigration";

    /** Per turn immigration penalty for units in Europe. */
    public static final String EUROPEAN_UNIT_IMMIGRATION_PENALTY
        = "model.option.europeanUnitImmigrationPenalty";

    /** Per turn player immigration bonus. */
    public static final String PLAYER_IMMIGRATION_BONUS
        = "model.option.playerImmigrationBonus";

    /** Equip European recruits. */
    public static final String EQUIP_EUROPEAN_RECRUITS
        = "model.option.equipEuropeanRecruits";

    /** Enhanced trade routes. */
    public static final String ENHANCED_TRADE_ROUTES
        = "model.option.enhancedTradeRoutes";


    /** Colony game option group. */
    public static final String GAMEOPTIONS_COLONY
        = "gameOptions.colony";

    /** Is bell accumulation capped when 100% rebels achieved? */
    public static final String BELL_ACCUMULATION_CAPPED
        = "model.option.bellAccumulationCapped";

    /** Capture units under repair with colony. */
    public static final String CAPTURE_UNITS_UNDER_REPAIR
        = "model.option.captureUnitsUnderRepair";

    /** Does the Custom House sell boycotted goods? */
    public static final String CUSTOM_IGNORE_BOYCOTT
        = "model.option.customIgnoreBoycott";

    /** Custom Houses only allowed in coastal colonies? */
    public static final String CUSTOMS_ON_COAST
        = "model.option.customsOnCoast";

    /** All units disembark when a carrier arrives in a colony (Col1). */
    public static final String DISEMBARK_IN_COLONY
        = "model.option.disembarkInColony";

    /**
     * Whether experts have connections, producing without raw
     * materials in factories.
     */
    public static final String EXPERTS_HAVE_CONNECTIONS
        = "model.option.expertsHaveConnections";

    /** Can colonies be founded during the war of independence? */
    public static final String FOUND_COLONY_DURING_REBELLION
        = "model.option.foundColonyDuringRebellion";

    /**
     * Can colony buildings be completed quickly by paying for the
     * missing goods?
     */
    public static final String PAY_FOR_BUILDING
        = "model.option.payForBuilding";

    /**
     * Does a hammer surplus after a build completes accumulate to
     * the next build?
     */
    public static final String SAVE_PRODUCTION_OVERFLOW
        = "model.option.saveProductionOverflow";

    /** Clear the hammers when changing production. */
    public static final String CLEAR_HAMMERS_ON_CONSTRUCTION_SWITCH
        = "model.option.clearHammersOnConstructionSwitch";

    /**
     * Whether to educate the least skilled unit first. This is the
     * behaviour of the original game and disallows manually assigning
     * students to teachers.
     */
    public static final String ALLOW_STUDENT_SELECTION
        = "model.option.allowStudentSelection";

    /** Claim all possible surrounding tiles when founding a colony? */
    public static final String CLAIM_ALL_TILES
        = "model.option.claimAllTiles";

    /** Enable upkeep requirement on buildings. */
    public static final String ENABLE_UPKEEP
        = "model.option.enableUpkeep";

    /**
     * Toggle whether artifical tile improvements contribute to the
     * production of secondary (non-food) goods on the colony center
     * tile.
     */
    public static final String ONLY_NATURAL_IMPROVEMENTS
        = "model.option.onlyNaturalImprovements";

    /** Enable natural disasters striking colonies. */
    public static final String NATURAL_DISASTERS
        = "model.option.naturalDisasters";

    ///** The cost of a single hammer when buying a building in a colony. */
    //Unused at the moment
    // public static final String HAMMER_PRICE = "hammerPrice";


    /** Victory conditions game option group. */
    public static final String GAMEOPTIONS_VICTORY_CONDITIONS
        = "gameOptions.victoryConditions";

    /**
     * Victory condition: Should the {@code Player} who first
     * defeats the Royal Expeditionary Force win the game?
     */
    public static final String VICTORY_DEFEAT_REF
        = "model.option.victoryDefeatREF";

    /**
     * Victory condition: Should a {@code Player} who
     * defeats all other European players win the game?
     */
    public static final String VICTORY_DEFEAT_EUROPEANS
        = "model.option.victoryDefeatEuropeans";

    /**
     * Victory condition: Should a {@code Player} who defeats all
     * other human players win the game?
     */
    public static final String VICTORY_DEFEAT_HUMANS
        = "model.option.victoryDefeatHumans";

    /**
     * May a single player who has been defeated play on in revenge mode
     * (undead units)?  Off in the "levi" rules (house rule): a defeat
     * ends the game, and only the server decides on it.  Rules and saves
     * without it ask, as FreeCol does.
     */
    public static final String REVENGE_MODE
        = "model.option.revengeMode";

    /**
     * House rule ("levi"): a European player is defeated when he has no
     * colony left -- from the mandatory colony year on, and at once
     * after declaring independence -- not only when a rebel has lost
     * his last coastal colony.  Rules and saves without it keep
     * FreeCol's checks.
     */
    public static final String LAST_COLONY_DEFEAT
        = "model.option.lastColonyDefeat";


    /** Years game option group. */
    public static final String GAMEOPTIONS_YEARS
        = "gameOptions.years";

    /**
     * The year in which the game starts. At the moment, changing this
     * value only shortens the game.  In future, it might cause the map
     * generator to create foreign colonies.
     */
    public static final String STARTING_YEAR
        = "model.option.startingYear";

    /**
     * The first year in which there are two seasons.  Changing this
     * value influences the duration of the game.
     */
    public static final String SEASON_YEAR
        = "model.option.seasonYear";

    /** The year in which owning at least one colony becomes mandatory. */
    public static final String MANDATORY_COLONY_YEAR
        = "model.option.mandatoryColonyYear";

    /** The very last year of the game. */
    public static final String LAST_YEAR
        = "model.option.lastYear";

    /**
     * The last year of the game for colonial players.  In other
     * words, if a colonial player does not declare independence by
     * the end of this year, the game is lost.
     */
    public static final String LAST_COLONIAL_YEAR
        = "model.option.lastColonialYear";

    /**
     * Score bonus on declaring independence = (1780, Spring) - turn
     * 1780 is documented in the Col1 manual:
     *   ``if you've declared your independence before 1780, your score
     *     is increased; the sooner you declare; the better your Bonus.''
     * which suggests this needs to cut off at 1780.  Using turns
     * rather than years though so that scores are equivalent in games
     * with &gt;2 seasons.
     */
    public static final String INDEPENDENCE_TURN
        = "model.option.independenceTurn";

    /** The "ages" where founding father weights change, specified as years. */
    public static final String AGES
        = "model.option.ages";

    /** The number of seasons. */
    public static final String SEASONS
        = "model.option.seasons";
    
    
    /**
     * Prices game option group.
     * This group is synthesized at game initialization.
     */
    public static final String GAMEOPTIONS_PRICES
        = "gameOptions.prices";
    

    // Difficulty options are effectively special multi-valued game options.

    /** Immigration (difficulty) group. */
    public static final String DIFFICULTY_IMMIGRATION
        = "model.difficulty.immigration";

    /** Rate cross requirement increases. */
    public static final String CROSSES_INCREMENT
        = "model.option.crossesIncrement";

    /** Rate recruitment prices increase. */
    public static final String RECRUIT_PRICE_INCREASE
        = "model.option.recruitPriceIncrease";

    /** Lower bound on the recruitment price. */
    public static final String LOWER_CAP_INCREASE
        = "model.option.lowerCapIncrease";

    /** A price increase prefix. */
    public static final String PRICE_INCREASE
        = "model.option.priceIncrease";

    /** Are there price increases specific to unit type. */
    public static final String PRICE_INCREASE_PER_TYPE
        = "model.option.priceIncreasePerType";

    /** Are the initial immigrants expert units. */
    public static final String EXPERT_STARTING_UNITS
        = "model.option.expertStartingUnits";

    /** Predefined initial immigrants. */
    public static final String IMMIGRANTS
        = "model.option.immigrants";


    /** Native (difficulty) group. */
    public static final String DIFFICULTY_NATIVES
        = "model.difficulty.natives";

    /** Base multiplier from land productivity to price. */
    public static final String LAND_PRICE_FACTOR
        = "model.option.landPriceFactor";

    /** Probability of an attack extracting converts. */
    public static final String NATIVE_CONVERT_PROBABILITY
        = "model.option.nativeConvertProbability";

    /** Probability of angry natives burning missions. */
    public static final String BURN_PROBABILITY
        = "model.option.burnProbability";

    /** Factor for how a successful native demand reduces tension. */
    public static final String NATIVE_DEMANDS
        = "model.option.nativeDemands";

    /** Rumour difficulty factor. [FIXME: should go away] */
    public static final String RUMOUR_DIFFICULTY
        = "model.option.rumourDifficulty";

    /** The penalty applied to trading with the natives from a ship. */
    public static final String SHIP_TRADE_PENALTY
        = "model.option.shipTradePenalty";

    /** The score penalty for destroying a settlement. */
    public static final String DESTROY_SETTLEMENT_SCORE
        = "model.option.destroySettlementScore";

    /** The behaviour when building on native land. */
    public static final String BUILD_ON_NATIVE_LAND
        = "model.option.buildOnNativeLand";
    public static final String BUILD_ON_NATIVE_LAND_ALWAYS
        = "model.option.buildOnNativeLand.always";
    public static final String BUILD_ON_NATIVE_LAND_FIRST
        = "model.option.buildOnNativeLand.first";
    public static final String BUILD_ON_NATIVE_LAND_FIRST_AND_UNCONTACTED
        = "model.option.buildOnNativeLand.firstAndUncontacted";
    public static final String BUILD_ON_NATIVE_LAND_NEVER
        = "model.option.buildOnNativeLand.never";

    /**
     * Option for setting the number of settlements on the map.
     * Note: *not* a MapGeneratorOption because it is difficulty-sensitive.
     */
    public static final String SETTLEMENT_NUMBER
        = "model.option.settlementNumber";


    /** Monarch (difficulty) group. */
    public static final String DIFFICULTY_MONARCH
        = "model.difficulty.monarch";

    /**
     * The grace period at the start of the game before the monarch
     * begins to meddle.
     */
    public static final String MONARCH_MEDDLING
        = "model.option.monarchMeddling";

    /** Moderating factor for tax raises. */
    public static final String TAX_ADJUSTMENT
        = "model.option.taxAdjustment";

    /** Percentage of real price to change for mercenary units. */
    public static final String MERCENARY_PRICE
        = "model.option.mercenaryPrice";

    /** Maximum tax rate. */
    public static final String MAXIMUM_TAX
        = "model.option.maximumTax";

    /** The degree of monarch support. */
    public static final String MONARCH_SUPPORT
        = "model.option.monarchSupport";

    /** Percentage fee for transporting treasures to Europe. */
    public static final String TREASURE_TRANSPORT_FEE
        = "model.option.treasureTransportFee";

    /** Bells to generate to trigger the intervention force. */
    public static final String INTERVENTION_BELLS
        = "model.option.interventionBells";

    /** How often to update the intervention force. */
    public static final String INTERVENTION_TURNS
        = "model.option.interventionTurns";

    /** The basic composition of the REF.  "refSize" is a legacy term. */
    public static final String REF_FORCE
        = "model.option.refSize";

    /** The basic composition of the intervention force. */
    public static final String INTERVENTION_FORCE
        = "model.option.interventionForce";

    /** The basic composition of the mercenary force. */
    public static final String MERCENARY_FORCE
        = "model.option.mercenaryForce";

    /** The base mercenary force sometimes supplied with a war declaration. */
    public static final String WAR_SUPPORT_FORCE
        = "model.option.warSupportForce";

    /** The base amount of gold sometimes supplied with a war declaration. */
    public static final String WAR_SUPPORT_GOLD
        = "model.option.warSupportGold";

    /** Government (difficulty) group. */
    public static final String DIFFICULTY_GOVERNMENT
        = "model.difficulty.government";

    /** The percent SoL to achieve "bad" government. */
    public static final String BAD_GOVERNMENT_LIMIT
        = "model.option.badGovernmentLimit";

    /** The percent SoL to achieve "very bad" government. */
    public static final String VERY_BAD_GOVERNMENT_LIMIT
        = "model.option.veryBadGovernmentLimit";

    /** The percent SoL to achieve "good" government. */
    public static final String GOOD_GOVERNMENT_LIMIT
        = "model.option.goodGovernmentLimit";

    /** The percent SoL to achieve "very good" government. */
    public static final String VERY_GOOD_GOVERNMENT_LIMIT
        = "model.option.veryGoodGovernmentLimit";


    /** Other (difficulty) group. */
    public static final String DIFFICULTY_OTHER
        = "model.difficulty.other";

    /** The amount of money each player will receive before the game starts. */
    public static final String STARTING_MONEY
        = "model.option.startingMoney";

    /** Rate the bells for founding father recruitment grows. */
    public static final String FOUNDING_FATHER_FACTOR
        = "model.option.foundingFatherFactor";

    /** Retributive markup on tea party goods. */
    public static final String ARREARS_FACTOR
        = "model.option.arrearsFactor";

    /** The number of units that do not consume bells. */
    public static final String UNITS_THAT_USE_NO_BELLS
        = "model.option.unitsThatUseNoBells";

    /** Tile production. */
    public static final String TILE_PRODUCTION
        = "model.option.tileProduction";

    /** Bad rumour chance. */
    public static final String BAD_RUMOUR
        = "model.option.badRumour";

    /** Good rumour chance. */
    public static final String GOOD_RUMOUR
        = "model.option.goodRumour";


    /** Cheat (difficulty) group. */
    public static final String DIFFICULTY_CHEAT
        = "model.difficulty.cheat";

    public static final String LIFT_BOYCOTT_CHEAT
        = "model.option.liftBoycottCheat";
    public static final String EQUIP_SCOUT_CHEAT
        = "model.option.equipScoutCheat";
    public static final String EQUIP_PIONEER_CHEAT
        = "model.option.equipPioneerCheat";
    public static final String LAND_UNIT_CHEAT
        = "model.option.landUnitCheat";
    public static final String OFFENSIVE_LAND_UNIT_CHEAT
        = "model.option.offensiveLandUnitCheat";
    public static final String OFFENSIVE_NAVAL_UNIT_CHEAT
        = "model.option.offensiveNavalUnitCheat";
    public static final String TRANSPORT_NAVAL_UNIT_CHEAT
        = "model.option.transportNavalUnitCheat";
    
    public static final String TRADE_PROFIT_MULTIPLIER_CHEAT
        = "model.option.tradeProfitMultiplierCheat";
    public static final String TRADE_PROFIT_MULTIPLIER_CHEAT_TURNS
        = "model.option.tradeProfitMultiplierCheatTurns";


    // Serialization

    /**
     * {@inheritDoc}
     */
    public String getXMLTagName() { return TAG; }
}
