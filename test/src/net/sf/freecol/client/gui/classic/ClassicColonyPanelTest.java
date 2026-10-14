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
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import net.sf.freecol.common.model.BuildingType;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * Tests of the colony screen's building sprites
 * ({@code ClassicColonyBuildings.frameOf}, build spec M4.2 target 3;
 * dago {@code 01-buildset.md} §6): the frame is the NAMES.TXT
 * {@code @BUILDING} row, the seven buildings of a new colony as matched
 * on the original's screen (clip008), their chains in consecutive frames,
 * the armory chain, the custom house, the press and the schools at their
 * rows (since M4.2; the first slice had 25 of 39 wrong), FreeCol's own
 * base levels on their slot's placeholder.
 */
public class ClassicColonyPanelTest extends FreeColTestCase {

    private static Integer frame(String type) {
        return ClassicColonyBuildings.frameOf("model.building." + type);
    }

    /** The seven buildings a new colony starts with, as the original draws them. */
    public void testTheStartingBuildingsHaveTheOriginalsSprites() {
        assertEquals(Integer.valueOf(9), frame("townHall"));
        assertEquals(Integer.valueOf(35), frame("carpenterHouse"));
        assertEquals(Integer.valueOf(27), frame("distillerHouse"));
        assertEquals(Integer.valueOf(21), frame("weaverHouse"));
        assertEquals(Integer.valueOf(24), frame("tobacconistHouse"));
        assertEquals(Integer.valueOf(32), frame("furTraderHouse"));
        assertEquals(Integer.valueOf(39), frame("blacksmithHouse"));
    }

    /** Each chain's levels are consecutive frames (NAMES.TXT's rows). */
    public void testEachHouseIsFollowedByItsUpgrades() {
        final String[][] chains = {
            { "weaverHouse", "weaverShop", "textileMill" },
            { "tobacconistHouse", "tobacconistShop", "cigarFactory" },
            { "distillerHouse", "rumDistillery", "rumFactory" },
            { "furTraderHouse", "furTradingPost", "furFactory" },
            { "blacksmithHouse", "blacksmithShop", "ironWorks" },
            { "carpenterHouse", "lumberMill" },
            { "stockade", "fort", "fortress" },
            { "armory", "magazine", "arsenal" },
            { "docks", "drydock", "shipyard" },
            { "schoolhouse", "college", "university" },
            { "printingPress", "newspaper" },
            { "church", "cathedral" },
        };
        for (String[] chain : chains) {
            final int base = frame(chain[0]);
            for (int i = 1; i < chain.length; i++) {
                assertEquals(chain[i], Integer.valueOf(base + i), frame(chain[i]));
            }
        }
        assertEquals(Integer.valueOf(0), frame("stockade"));
        assertEquals(Integer.valueOf(3), frame("armory"));
        assertEquals(Integer.valueOf(12), frame("schoolhouse"));
        assertEquals(Integer.valueOf(18), frame("customHouse"));
        assertEquals(Integer.valueOf(19), frame("printingPress"));
        assertEquals(Integer.valueOf(37), frame("church"));
    }

    /**
     * FreeCol's own base levels (chapel, depot, country) have no sprite:
     * their slot shows its placeholder (trees); the Lagerhaus and its
     * expansion share 015 (the expansion adds the «2»), the Ställe 046.
     */
    public void testFreeColsBaseLevelsDrawThePlaceholder() {
        for (String type : new String[] { "chapel", "depot", "country" }) {
            assertNull(type, frame(type));
        }
        assertEquals(Integer.valueOf(15), frame("warehouse"));
        assertEquals(Integer.valueOf(15), frame("warehouseExpansion"));
        assertEquals(Integer.valueOf(46), frame("stables"));
    }

    /**
     * Every building type of the classic rules but FreeCol's three base
     * levels has a frame of {@code BUILDING.SS} (0-47), and only the
     * Lagerhaus and its expansion share one: a misspelt key in the table
     * would leave its type without a sprite.
     */
    public void testEveryOtherBuildingHasAFrameOfItsOwn() {
        final Set<String> unmapped = new TreeSet<>();
        final Map<Integer, String> owners = new HashMap<>();
        for (BuildingType bt : spec("classic").getBuildingTypeList()) {
            final Integer f = ClassicColonyBuildings.frameOf(bt.getId());
            if (f == null) {
                unmapped.add(bt.getSuffix());
                continue;
            }
            assertTrue(bt.getId() + " " + f, f >= 0 && f < 48);
            final String other = owners.put(f, bt.getSuffix());
            if (other != null) {
                assertEquals("frame " + f, Integer.valueOf(15), f);
            }
        }
        assertEquals("[chapel, country, depot]", unmapped.toString());
        assertEquals(37, owners.size());
    }

    /** The prefix is optional, as for every caller with a short id. */
    public void testShortIds() {
        assertEquals(Integer.valueOf(9), ClassicColonyBuildings.frameOf("townHall"));
        assertNull(ClassicColonyBuildings.frameOf("model.building.noSuchBuilding"));
    }
}
