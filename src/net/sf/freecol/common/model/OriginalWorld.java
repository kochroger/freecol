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

package net.sf.freecol.common.model;

import net.sf.freecol.common.option.MapGeneratorOptions;
import net.sf.freecol.common.option.OptionGroup;
import net.sf.freecol.common.util.RandomUtils.RandomIntCache;


/**
 * The land of the original game's New World, as its world questions
 * make it ({@link MapGeneratorOptions#LAND_GENERATOR_ORIGINAL}, the
 * "levi" rules): how much land a map gets for LANDMASSE (Klein /
 * Normal / Groß) and LANDFORM (Inselgruppe / Normal / Große
 * Kontinente), how widely that amount varies from map to map, and the
 * size of the pieces the land is built from.  {@link LandMap} grows the
 * land from these numbers.
 *
 * <p>The amounts are Roger's games of 2026-10-13 in the original (13
 * new worlds, each saved in its first turn and read from the save:
 * land = rows 1 to 70 of 58x72, terrain below the arctic; every choice
 * seen in its clip):
 * <table>
 *   <caption>Land tiles of the original's new worlds</caption>
 *   <tr><th>Setting</th><th>Games</th><th>Mean</th></tr>
 *   <tr><td>everything Normal</td><td>872, 901</td><td>886</td></tr>
 *   <tr><td>LANDMASSE Klein</td><td>673, 732</td><td>702</td></tr>
 *   <tr><td>LANDMASSE Groß</td><td>1079, 1184</td><td>1131</td></tr>
 *   <tr><td>LANDFORM Inselgruppe</td><td>698</td><td>698</td></tr>
 *   <tr><td>LANDFORM Große Kontinente</td><td>1191</td><td>1191</td></tr>
 *   <tr><td>Groß, Kontinente, Warm, Feucht</td><td>1373</td><td>1373</td></tr>
 *   <tr><td>the title menu's «Neue Welt»</td><td>427, 469, 650</td><td>515</td></tr>
 * </table>
 * The other combinations were not played: a landform adds or removes
 * as much land as it does at Normal (Inselgruppe -188, Kontinente +305),
 * which also predicts «Groß + Kontinente» within 5 % of the one game
 * measured (1436 against 1373; the multiplied shares would give 1520).
 * Temperature and climate are taken not to change the amount, as in
 * Civilization's generator, which the original's is built on.
 *
 * <p>The spread: each map draws its own amount around the setting's
 * mean, with a standard deviation of 5 % (the pairs of games of one
 * setting differ by 1.6 to 4.6 % from their mean, a pooled 5 %), cut
 * off at 12 %.
 *
 * <p>The pieces: the original builds on Civilization's generator, which
 * makes land from random walks of small stamps until the land is
 * enough (darkpanda, "Civ1 Map Generation explained", CivFanatics).
 * Long walks give large land masses, short ones many small islands; the
 * walk lengths here give the shapes of the original's maps: Inselgruppe
 * 12 land masses (9 of 10 tiles or more), the largest 50 % of the land,
 * 0.78 coast tiles per land tile; the «Neue Welt» of 427 tiles 15
 * masses, 30 %, 0.88; four other games (1143 to 1679 tiles) 4 to 12
 * masses, the largest 54 to 93 %, 0.58 to 0.69.
 *
 * <p>The climate: {@code TerrainGenerator} types the land by FreeCol's
 * climate bands with this class's temperatures (TEMPERATUR) and
 * humidity (KLIMA), puts no arctic on the land outside the polar rows
 * and makes the polar rows all arctic, as the original's are.
 */
public final class OriginalWorld {

    /** The original's map, on which the amounts were measured. */
    public static final int ORIGINAL_WIDTH = 58, ORIGINAL_HEIGHT = 72;

    /**
     * The mean land tiles of each setting on the original's 58x72 map,
     * by {@link MapGeneratorOptions#ORIGINAL_LAND_MASS} (small, normal,
     * large), then {@link MapGeneratorOptions#ORIGINAL_LAND_FORM}
     * (islands, normal, continents).  Measured: the normal row, the
     * normal column and large/continents; the rest is the normal
     * row's landform difference added to the measured mass.
     */
    private static final int[][] MEAN_LAND = {
        {  514,  702, 1007 },
        {  698,  886, 1191 },
        {  943, 1131, 1373 } };

    /** The spread of one map's land around its setting's mean. */
    public static final double LAND_SPREAD = 0.05;

    /** No map's land lies further than this from its setting's mean. */
    public static final double LAND_SPREAD_LIMIT = 0.12;

    /**
     * The shortest and longest walk of one piece of land, by landform
     * (islands, normal, continents).
     */
    private static final int[][] WALK = {
        {  1,  64 },
        {  1, 128 },
        { 64, 256 } };


    /**
     * No land outside the polar rows is colder than this: just above the
     * arctic's range (classic rules, -20 to 0).  The original has its
     * arctic in the polar rows and in the water next to them (rows 1
     * and 70: 25 to 33 tiles each), never on its land, and its land is
     * the same for every TEMPERATUR.
     */
    public static final int LOWEST_LAND_TEMPERATURE = 1;

    /**
     * Pole and equator temperatures by temperature option value (0 cold,
     * 1 chilly = Kühl, 2 temperate = Gemäßigt, 3 warm = Warm, 4 hot), for
     * FreeCol's climate bands ({@code TerrainGenerator}: the latitude
     * gives the temperature between the two, varied by 7 degrees).
     * Gemäßigt gives tundra 8.0 % and savannah 10.0 % of the flat land on
     * everything Normal, which fits the two known Gemäßigt games in the
     * original (#2 7.7 / 10.5, #10 8.6 / 5.9); the mean of its seven random
     * maps read, 6.6 / 11.9 %, mixes unknown settings (FreeCol's
     * temperate, -10 / 35, gives 15 % tundra); Warm gives Roger's game
     * «Groß, Kontinente, Warm, Feucht» (tundra 7.7 to 3.9 %, savannah 10.5
     * to 15.6 %, plains 23.4 to 18.9 % against his game with everything
     * Normal); Kühl is Warm's mirror (I: not played).
     */
    private static final int[][] TEMPERATURES = {
        { -15, 31 }, { -10, 33 }, { -5, 35 }, { 0, 37 }, { 5, 39 } };

    private OriginalWorld() {} // Static only.

    /**
     * The mean humidity of the original's New World for a humidity
     * option value (the rules' 25 very dry, 35 dry = Trocken, 40 normal
     * = Normal, 55 wet = Feucht, 60 very wet).  FreeCol's own values
     * would turn a quarter of the land into swamp (wet) or 14 % into
     * desert (dry); in Roger's game «Groß, Kontinente, Warm, Feucht»
     * marsh and swamp rose only from 11.3 to 13.1 % of the land and the
     * desert stayed at 8 %, which Warm alone already gives here.  So
     * KLIMA moves the humidity by 2 only (Feucht: 2 points more marsh and
     * swamp, 2 less desert; Trocken the reverse; I, not played alone).
     *
     * @param humidity The humidity option value.
     * @return The mean humidity of the map's land.
     */
    public static int getHumidity(int humidity) {
        return (humidity <= 25) ? 36 : (humidity <= 35) ? 38
            : (humidity <= 40) ? 40 : (humidity <= 55) ? 42 : 44;
    }

    /**
     * The temperature at the poles of the original's New World.
     *
     * @param temperature The temperature option value (0 cold .. 4 hot;
     *     the original's Kühl / Gemäßigt / Warm are 1 / 2 / 3).
     * @return The pole temperature.
     */
    public static int getPoleTemperature(int temperature) {
        final int t = Math.max(0, Math.min(4, temperature));
        return TEMPERATURES[t][0];
    }

    /**
     * The temperature at the equator of the original's New World.
     *
     * @param temperature The temperature option value.
     * @return The equator temperature.
     */
    public static int getEquatorTemperature(int temperature) {
        final int t = Math.max(0, Math.min(4, temperature));
        return TEMPERATURES[t][1];
    }


    /**
     * Do a map's options ask for the original's New World?
     *
     * @param mgo The map generator options.
     * @return True for {@link MapGeneratorOptions#LAND_GENERATOR_ORIGINAL}.
     */
    public static boolean isOriginal(OptionGroup mgo) {
        return mgo != null
            && mgo.hasOption(MapGeneratorOptions.LAND_GENERATOR_TYPE,
                net.sf.freecol.common.option.SelectOption.class)
            && mgo.getSelection(MapGeneratorOptions.LAND_GENERATOR_TYPE)
                == MapGeneratorOptions.LAND_GENERATOR_ORIGINAL;
    }

    /**
     * Count the land of a map outside the polar rows, the land the
     * mountains and rumours of a new map are made for.
     *
     * @param map The {@code Map} to count.
     * @return The number of land tiles outside the polar rows.
     */
    public static int countLand(Map map) {
        int n = 0;
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                Tile t = map.getTile(x, y);
                if (t != null && t.isLand() && !map.isPolar(t)) n++;
            }
        }
        return n;
    }


    /**
     * The LANDMASSE answer of a map's options.
     *
     * @param mgo The map generator options.
     * @return {@link MapGeneratorOptions#ORIGINAL_LAND_MASS_SMALL},
     *     {@code _NORMAL} (also when the rules have no such option) or
     *     {@code _LARGE}.
     */
    public static int getLandMass(OptionGroup mgo) {
        return answer(mgo, MapGeneratorOptions.ORIGINAL_LAND_MASS,
                      MapGeneratorOptions.ORIGINAL_LAND_MASS_NORMAL);
    }

    /**
     * The LANDFORM answer of a map's options.
     *
     * @param mgo The map generator options.
     * @return {@link MapGeneratorOptions#ORIGINAL_LAND_FORM_ISLANDS},
     *     {@code _NORMAL} (also when the rules have no such option) or
     *     {@code _CONTINENTS}.
     */
    public static int getLandForm(OptionGroup mgo) {
        return answer(mgo, MapGeneratorOptions.ORIGINAL_LAND_FORM,
                      MapGeneratorOptions.ORIGINAL_LAND_FORM_NORMAL);
    }

    private static int answer(OptionGroup mgo, String id, int fallback) {
        if (mgo == null || !mgo.hasOption(id,
                net.sf.freecol.common.option.SelectOption.class)) {
            return fallback;
        }
        final int v = mgo.getSelection(id);
        return (v < 0 || v > 2) ? fallback : v;
    }

    /**
     * The mean land of a setting on a map of the given size: the
     * original's mean, in proportion to the map's tiles.
     *
     * @param mass The LANDMASSE answer.
     * @param form The LANDFORM answer.
     * @param width The map width.
     * @param height The map height.
     * @return The mean number of land tiles.
     */
    public static int getMeanLand(int mass, int form, int width, int height) {
        return (int)Math.round(MEAN_LAND[clamp(mass)][clamp(form)]
            * (double)(width * height) / (ORIGINAL_WIDTH * ORIGINAL_HEIGHT));
    }

    /**
     * The land of one new map: the setting's mean, varied by
     * {@link #LAND_SPREAD} (a sum of four uniform draws, close to a
     * normal distribution), never more than {@link #LAND_SPREAD_LIMIT}
     * away.
     *
     * @param mass The LANDMASSE answer.
     * @param form The LANDFORM answer.
     * @param width The map width.
     * @param height The map height.
     * @param cache The map's random number source.
     * @return The number of land tiles to make.
     */
    public static int drawLand(int mass, int form, int width, int height,
                               RandomIntCache cache) {
        double sum = 0.0;
        for (int i = 0; i < 4; i++) sum += cache.nextInt(10000) / 10000.0;
        // Four uniform draws: mean 2, standard deviation sqrt(4/12).
        double g = (sum - 2.0) / Math.sqrt(4.0 / 12.0);
        double f = Math.max(-LAND_SPREAD_LIMIT,
            Math.min(LAND_SPREAD_LIMIT, LAND_SPREAD * g));
        return (int)Math.round(getMeanLand(mass, form, width, height)
                               * (1.0 + f));
    }

    /**
     * The shortest walk of one piece of land.
     *
     * @param form The LANDFORM answer.
     * @return The number of steps.
     */
    public static int getShortestWalk(int form) {
        return WALK[clamp(form)][0];
    }

    /**
     * The longest walk of one piece of land.
     *
     * @param form The LANDFORM answer.
     * @return The number of steps.
     */
    public static int getLongestWalk(int form) {
        return WALK[clamp(form)][1];
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(2, v));
    }
}
