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

import java.util.logging.Logger;

import net.sf.freecol.common.model.Topology;
import net.sf.freecol.server.generator.MapGenerator;


/**
 * Keeps track of the available map generator options.
 * More of a handy place to organize the names than an actual option type.
 *
 * @see MapGenerator
 * @see net.sf.freecol.common.option.OptionGroup
 */
public class MapGeneratorOptions {

    private static final Logger logger = Logger.getLogger(MapGeneratorOptions.class.getName());

    public static final String TAG = "mapGeneratorOptions";

    /** Map generator options import group. */
    public static final String MAPGENERATOROPTIONS_IMPORT
        = "mapGeneratorOptions.import";

    /** Option for setting a file to be imported (map etc). */
    public static final String IMPORT_FILE 
        = "model.option.importFile";

    /** Option for using the terrain imported from a file. */
    public static final String IMPORT_TERRAIN 
        = "model.option.importTerrain";

    /** Option for using the bonuses imported from a file. */
    public static final String IMPORT_BONUSES 
        = "model.option.importBonuses";

    /** Option for using the lost city rumours imported from a file. */
    public static final String IMPORT_RUMOURS 
        = "model.option.importRumours";

    /** Option for using the settlements imported from a file. */
    public static final String IMPORT_SETTLEMENTS 
        = "model.option.importSettlements";


    /** Map generator options land generator group. */
    public static final String MAPGENERATOROPTIONS_LAND_GENERATOR
        = "mapGeneratorOptions.landGenerator";

    /** Option for setting the map width. */
    public static final String MAP_WIDTH
        = "model.option.mapWidth";

    /** Option for setting the map height. */
    public static final String MAP_HEIGHT
        = "model.option.mapHeight";

    /** Option for setting the land mass of the map. */
    public static final String LAND_MASS
        = "model.option.landMass";

    /** Option for setting the type of land generator to be used. */
    public static final String LAND_GENERATOR_TYPE 
        = "model.option.landGeneratorType";
    public static final int LAND_GENERATOR_CLASSIC     = 0,
                            LAND_GENERATOR_CONTINENT   = 1,
                            LAND_GENERATOR_ARCHIPELAGO = 2,
                            LAND_GENERATOR_ISLANDS     = 3;

    /** Option for setting the preferred distance to the map edge. */
    public static final String PREFERRED_DISTANCE_TO_EDGE
        = "model.option.preferredDistanceToEdge";

    /** Option for setting the maximum distance to the map edge. */
    public static final String MAXIMUM_DISTANCE_TO_EDGE
        = "model.option.maximumDistanceToEdge";

    /** Option for setting the distance to the high seas. */
    public static final String DISTANCE_TO_HIGH_SEA
        = "model.option.distanceToHighSea";


    /** Map generator options terrain generator group. */
    public static final String MAPGENERATOROPTIONS_TERRAIN_GENERATOR
        = "mapGeneratorOptions.terrainGenerator";

    /** The minimum latitude of the map. */
    public static final String MINIMUM_LATITUDE 
        = "model.option.minimumLatitude";

    /** The maximum latitude of the map. */
    public static final String MAXIMUM_LATITUDE 
        = "model.option.maximumLatitude";

    /** Option for enabling generation of great rivers. */
    public static final String ENABLE_GREAT_RIVERS  = "mapGeneratorOptions.enableGreatRivers";
    
    /** Option for setting the number of river tiles on the map as a percentage of all possible tiles for a river. */
    public static final String RIVER_NUMBER
        = "model.option.riverNumber";

    /** Option for setting the number of mountains on the map. */
    public static final String MOUNTAIN_NUMBER
        = "model.option.mountainNumber";

    /** Option for setting the number of rumours on the map. */
    public static final String RUMOUR_NUMBER
        = "model.option.rumourNumber";

    /** Option for setting the percentage of forests on the map. */
    public static final String FOREST_NUMBER 
        = "model.option.forestNumber";

    /** Option for setting the percentage of bonus tiles on the map. */
    public static final String BONUS_NUMBER 
        = "model.option.bonusNumber";

    /** Option for setting the humidity of the map. */
    public static final String HUMIDITY 
        = "model.option.humidity";

    /** Option for setting the temperature of the map. */
    public static final String TEMPERATURE 
        = "model.option.temperature";

    /** One of the settings used by {@link #TEMPERATURE}. */
    public static final int TEMPERATURE_COLD      = 0,
                            TEMPERATURE_CHILLY    = 1,
                            TEMPERATURE_TEMPERATE = 2,
                            TEMPERATURE_WARM      = 3,
                            TEMPERATURE_HOT       = 4;


    /**
     * Adjust the map generator options of a new game to the current
     * {@link Topology}, which the caller sets for the new game first.
     * Does nothing on the isometric map.
     *
     * On the square map the options approximate the map of the
     * original game: 58x72 tiles, sparse land (the original's random
     * maps are mostly ocean), about three quarters of the land
     * forested, and sea lanes on both sides.  The specification
     * defaults are left alone, as the "freecol" rules and the test
     * suite inherit them.  The values could move into a ruleset or
     * mod of their own once the Classic UI has one.
     *
     * @param mgo The map generator {@code OptionGroup} to adjust.
     */
    public static void applyTopologyDefaults(OptionGroup mgo) {
        if (mgo == null || Topology.current() != Topology.SQUARE) return;
        mgo.setInteger(MAP_WIDTH, 58);
        mgo.setInteger(MAP_HEIGHT, 72);
        // The option minimum is 15.
        mgo.setInteger(LAND_MASS, 18);
        // A range option (10/30/50/70/90) giving the chance of forest
        // on flat land only.  90 makes about 75% of all land forest,
        // 70 only about 58%.
        mgo.setInteger(FOREST_NUMBER, 90);
        // "large": about 15% of the land has a river, as on the
        // original America map (16%), instead of about 11%.
        mgo.setInteger(RIVER_NUMBER, 20);
        // High seas may start up to 16 columns from either edge.
        mgo.setInteger(MAXIMUM_DISTANCE_TO_EDGE, 16);
        logger.info("Square topology map options: "
            + mgo.getInteger(MAP_WIDTH) + "x" + mgo.getInteger(MAP_HEIGHT)
            + ", landMass=" + mgo.getInteger(LAND_MASS)
            + ", forestNumber=" + mgo.getInteger(FOREST_NUMBER)
            + ", riverNumber=" + mgo.getInteger(RIVER_NUMBER)
            + ", preferredDistanceToEdge="
            + mgo.getInteger(PREFERRED_DISTANCE_TO_EDGE)
            + ", maximumDistanceToEdge="
            + mgo.getInteger(MAXIMUM_DISTANCE_TO_EDGE)
            + ", distanceToHighSea=" + mgo.getInteger(DISTANCE_TO_HIGH_SEA));
    }


    // Serialization

    public String getXMLTagName() { return TAG; }
}
