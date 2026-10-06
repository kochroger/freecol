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
 * ({@code ClassicColonyPanel.buildingFrame}, plan D0e): the frames matched
 * on the original's colony screen (clip008 {@code 01-colony.md} &sect;2.3),
 * the upgrade chains inferred from them, and the buildings left without a
 * sprite until a capture shows them.
 */
public class ClassicColonyPanelTest extends FreeColTestCase {

    private static Integer frame(String type) {
        return ClassicColonyPanel.buildingFrame("model.building." + type);
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

    /** Each house's upgrades are the frames right after it. */
    public void testEachHouseIsFollowedByItsUpgrades() {
        final String[][] chains = {
            { "weaverHouse", "weaverShop", "textileMill" },
            { "tobacconistHouse", "tobacconistShop", "cigarFactory" },
            { "distillerHouse", "rumDistillery", "rumFactory" },
            { "furTraderHouse", "furTradingPost", "furFactory" },
            { "blacksmithHouse", "blacksmithShop", "ironWorks" },
            { "carpenterHouse", "lumberMill" },
        };
        for (String[] chain : chains) {
            final int base = frame(chain[0]);
            for (int i = 1; i < chain.length; i++) {
                assertEquals(chain[i], Integer.valueOf(base + i), frame(chain[i]));
            }
        }
        assertEquals(Integer.valueOf(37), frame("church"));
        assertEquals(Integer.valueOf(38), frame("cathedral"));
    }

    /**
     * The wrong old entries are gone: the town hall is no longer 19, frame
     * 9 (the town hall) is no longer the custom house's, and the custom
     * house and the armory chain draw nothing until a capture shows them.
     */
    public void testUnknownBuildingsDrawNoSprite() {
        for (String type : new String[] { "customHouse", "armory", "magazine",
                                          "arsenal", "depot", "country" }) {
            assertNull(type, frame(type));
        }
        for (BuildingType bt : spec("classic").getBuildingTypeList()) {
            assertFalse(bt.getId(),
                Integer.valueOf(19).equals(ClassicColonyPanel.buildingFrame(bt.getId())));
        }
    }

    /**
     * Every building type of the classic rules but the unmapped six has a
     * frame of {@code BUILDING.SS} (0-47), and no two share one: a
     * misspelt key in the table would leave its type without a sprite.
     */
    public void testEveryOtherBuildingHasAFrameOfItsOwn() {
        final Set<String> unmapped = new TreeSet<>();
        final Map<Integer, String> owners = new HashMap<>();
        for (BuildingType bt : spec("classic").getBuildingTypeList()) {
            final Integer f = ClassicColonyPanel.buildingFrame(bt.getId());
            if (f == null) {
                unmapped.add(bt.getSuffix());
                continue;
            }
            assertTrue(bt.getId() + " " + f, f >= 0 && f < 48);
            final String other = owners.put(f, bt.getSuffix());
            assertNull("frame " + f + ": " + other + " and " + bt.getSuffix(), other);
        }
        assertEquals("[armory, arsenal, country, customHouse, depot, magazine]",
                     unmapped.toString());
        assertEquals(35, owners.size());
    }

    /** The prefix is optional, as for every caller with a short id. */
    public void testShortIds() {
        assertEquals(Integer.valueOf(9), ClassicColonyPanel.buildingFrame("townHall"));
        assertNull(ClassicColonyPanel.buildingFrame("model.building.noSuchBuilding"));
    }
}
