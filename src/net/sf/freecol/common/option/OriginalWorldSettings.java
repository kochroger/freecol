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

import net.sf.freecol.common.model.OriginalWorld;


/**
 * The answers to the original game's four world questions («Neue Welt
 * INDIVIDUALISIEREN», GAME.TXT {@code @CLAND}, {@code @CCONT},
 * {@code @CTEMP}, {@code @CCLIM}) and the map generator options they
 * stand for in the "levi" rules.
 *
 * <p>Each answer is the row of the original's picture grid: 0 the top
 * row (Klein, Inselgruppe, Kühl, Trocken), 1 the middle one, its
 * default (Normal, Normal, Gemäßigt, Normal), 2 the bottom row (Groß,
 * Große Kontinente, Warm, Feucht).
 *
 * <table>
 *   <caption>The answers as map generator options</caption>
 *   <tr><th>Question</th><th>Option</th><th>Values (rows 0 / 1 / 2)</th></tr>
 *   <tr><td>LANDMASSE</td><td>{@code model.option.originalLandMass}</td>
 *       <td>small / normal / large</td></tr>
 *   <tr><td>LANDFORM</td><td>{@code model.option.originalLandForm}</td>
 *       <td>islands / normal / continents</td></tr>
 *   <tr><td>TEMPERATUR</td><td>{@code model.option.temperature}</td>
 *       <td>chilly 1 / temperate 2 / warm 3</td></tr>
 *   <tr><td>KLIMA</td><td>{@code model.option.humidity}</td>
 *       <td>dry 35 / normal 40 / wet 55</td></tr>
 * </table>
 * and {@code model.option.landGeneratorType} "original", whose land,
 * climate bands and humidity {@link OriginalWorld} measures against
 * Roger's games in the original.
 */
public final class OriginalWorldSettings {

    private static final Logger logger = Logger.getLogger(OriginalWorldSettings.class.getName());

    /** The rows of the original's picture grid. */
    public static final int TOP = 0, MIDDLE = 1, BOTTOM = 2;

    /** Everything as the original's defaults (the middle row). */
    public static final OriginalWorldSettings ALL_NORMAL
        = new OriginalWorldSettings(MIDDLE, MIDDLE, MIDDLE, MIDDLE);

    /**
     * The world of the original's title menu, «Ein Spiel in der NEUEN
     * WELT starten»: Roger's three such games had 427, 469 and 650
     * land tiles, mean 515, the amount of LANDMASSE Klein with LANDFORM
     * Inselgruppe (OriginalWorld: 514); the original's manual and texts
     * do not say what it is (I).
     */
    public static final OriginalWorldSettings ORIGINAL_NEW_WORLD
        = new OriginalWorldSettings(TOP, TOP, MIDDLE, MIDDLE);

    /**
     * The world the Classic UI's «Neue Welt» starts today: everything
     * Normal, the world Roger and Levi play («Individualisieren», all
     * Normal), as long as the Classic UI has no «Individualisieren»
     * screen; with one, «Neue Welt» becomes {@link #ORIGINAL_NEW_WORLD}.
     */
    public static final OriginalWorldSettings CLASSIC_UI_NEW_WORLD = ALL_NORMAL;

    /** The temperature option value of each TEMPERATUR row. */
    private static final int[] TEMPERATURE_VALUES = {
        MapGeneratorOptions.TEMPERATURE_CHILLY,
        MapGeneratorOptions.TEMPERATURE_TEMPERATE,
        MapGeneratorOptions.TEMPERATURE_WARM };

    /** The humidity option value of each KLIMA row. */
    private static final int[] HUMIDITY_VALUES = {
        MapGeneratorOptions.HUMIDITY_DRY,
        MapGeneratorOptions.HUMIDITY_NORMAL,
        MapGeneratorOptions.HUMIDITY_WET };

    /** The answers, each a row (0-2). */
    private final int landMass, landForm, temperature, climate;


    /**
     * Create a set of answers.
     *
     * @param landMass The LANDMASSE row.
     * @param landForm The LANDFORM row.
     * @param temperature The TEMPERATUR row.
     * @param climate The KLIMA row.
     */
    public OriginalWorldSettings(int landMass, int landForm,
                                 int temperature, int climate) {
        this.landMass = row(landMass);
        this.landForm = row(landForm);
        this.temperature = row(temperature);
        this.climate = row(climate);
    }

    private static int row(int v) {
        if (v < TOP || v > BOTTOM) {
            throw new IllegalArgumentException("Not a row: " + v);
        }
        return v;
    }

    public int getLandMass() { return this.landMass; }
    public int getLandForm() { return this.landForm; }
    public int getTemperature() { return this.temperature; }
    public int getClimate() { return this.climate; }

    /**
     * Set the map generator options of a new game to these answers.
     * Rules without the original's world (not "levi") are left alone.
     *
     * @param mgo The map generator options of the new game.
     * @return True if the options were set.
     */
    public boolean apply(OptionGroup mgo) {
        if (mgo == null
            || !mgo.hasOption(MapGeneratorOptions.ORIGINAL_LAND_MASS,
                              SelectOption.class)
            || !mgo.hasOption(MapGeneratorOptions.ORIGINAL_LAND_FORM,
                              SelectOption.class)) {
            logger.info("No original world in these rules: " + this);
            return false;
        }
        mgo.setInteger(MapGeneratorOptions.LAND_GENERATOR_TYPE,
                       MapGeneratorOptions.LAND_GENERATOR_ORIGINAL);
        mgo.setInteger(MapGeneratorOptions.ORIGINAL_LAND_MASS, this.landMass);
        mgo.setInteger(MapGeneratorOptions.ORIGINAL_LAND_FORM, this.landForm);
        mgo.setInteger(MapGeneratorOptions.TEMPERATURE,
                       TEMPERATURE_VALUES[this.temperature]);
        mgo.setInteger(MapGeneratorOptions.HUMIDITY,
                       HUMIDITY_VALUES[this.climate]);
        logger.info("Original world: " + this);
        return true;
    }

    /**
     * The answers a game's map generator options stand for.
     *
     * @param mgo The map generator options.
     * @return The answers, or null if the options are not the
     *     original's world.
     */
    public static OriginalWorldSettings of(OptionGroup mgo) {
        if (!OriginalWorld.isOriginal(mgo)) return null;
        final int t = mgo.getRange(MapGeneratorOptions.TEMPERATURE);
        final int h = mgo.getRange(MapGeneratorOptions.HUMIDITY);
        return new OriginalWorldSettings(OriginalWorld.getLandMass(mgo),
            OriginalWorld.getLandForm(mgo),
            (t <= MapGeneratorOptions.TEMPERATURE_CHILLY) ? TOP
            : (t >= MapGeneratorOptions.TEMPERATURE_WARM) ? BOTTOM : MIDDLE,
            (h <= MapGeneratorOptions.HUMIDITY_DRY) ? TOP
            : (h >= MapGeneratorOptions.HUMIDITY_WET) ? BOTTOM : MIDDLE);
    }

    /**
     * A short code of the answers: LANDMASSE K/N/G, LANDFORM I/N/C,
     * TEMPERATUR k/g/w, KLIMA t/n/f (the German initials), e.g.
     * "NNgn" for everything Normal.
     *
     * @return The code.
     */
    public String getCode() {
        return "KNG".charAt(this.landMass) + "" + "INC".charAt(this.landForm)
            + "kgw".charAt(this.temperature) + "tnf".charAt(this.climate);
    }

    /**
     * The answers of a code ({@link #getCode}).
     *
     * @param code The code.
     * @return The answers.
     * @throws IllegalArgumentException for a bad code.
     */
    public static OriginalWorldSettings ofCode(String code) {
        if (code == null || code.length() != 4) {
            throw new IllegalArgumentException("Bad code: " + code);
        }
        return new OriginalWorldSettings("KNG".indexOf(code.charAt(0)),
            "INC".indexOf(code.charAt(1)), "kgw".indexOf(code.charAt(2)),
            "tnf".indexOf(code.charAt(3)));
    }

    // Override Object

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof OriginalWorldSettings)) return false;
        OriginalWorldSettings w = (OriginalWorldSettings)o;
        return this.landMass == w.landMass && this.landForm == w.landForm
            && this.temperature == w.temperature && this.climate == w.climate;
    }

    @Override
    public int hashCode() {
        return ((this.landMass * 3 + this.landForm) * 3 + this.temperature)
            * 3 + this.climate;
    }

    @Override
    public String toString() {
        final String[][] names = {
            { "Klein", "Normal", "Groß" },
            { "Inselgruppe", "Normal", "Große Kontinente" },
            { "Kühl", "Gemäßigt", "Warm" },
            { "Trocken", "Normal", "Feucht" } };
        return "LANDMASSE " + names[0][this.landMass]
            + ", LANDFORM " + names[1][this.landForm]
            + ", TEMPERATUR " + names[2][this.temperature]
            + ", KLIMA " + names[3][this.climate]
            + " (" + getCode() + ")";
    }
}
