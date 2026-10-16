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

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import net.sf.freecol.common.model.Building;
import net.sf.freecol.common.model.BuildingType;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.ColonyTile;
import net.sf.freecol.common.model.Direction;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Role;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileImprovement;
import net.sf.freecol.common.model.Turn;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.server.model.ServerBuilding;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;
import net.sf.freecol.util.test.FreeColTestUtils;

import static net.sf.freecol.client.gui.classic.ClassicColonyLayout.Row;


/**
 * The colony screen's 3x3 colony map (build spec M4.4; {@link
 * ClassicColonyTiles}, {@link ClassicColonyLayout}): the geometry and the
 * tile rows as pure functions against every row the analyses measured
 * (clip 020's 17, dago's 29), the model of a test colony (the centre's two
 * rows, a worker's row, the prohibition sign, the totem, an enemy, the
 * labels), the white cursor, the terrain art (our map's tile enlarged to 24
 * as the original, part Y5: the rules as pure functions, Silver City's
 * ocean rows, the markers unscaled; its water cycling); then golden: the
 * original's own map of three colonies enlarged against their 3x3 index
 * for index, and the state of clip 020's frames rebuilt
 * in a test game on the classic rules (the tiles' terrain as the clip's
 * hover labels name it, the workers, the selection, the cursor's place and
 * blink half, a hovered tile) and the screen's canvas compared with the
 * frame pixel for pixel over everything the original or ours draws on the
 * terrain (the rows, the count labels, the workers with their shadows, the
 * sign, the totems, the frames, the hover label), the original arrow left
 * out (there the terrain, our test game's, is only counted); and dago's
 * four tile models drawn by the same painter against dago's frames.
 * Where FreeCol's rules make another amount than the clip shows, the test
 * names both and draws the clip's to check the drawing apart from the
 * rules.  Skips the golden part without the pack or the recordings
 * ({@code -Dclassic.clips}).
 */
public class ClassicColonyTilesTest extends FreeColTestCase {

    private static Specification classic() {
        return spec("classic");
    }

    @Override
    protected void tearDown() throws Exception {
        ClassicColonyPanel.forgetFigureOrders();
        ClassicColonyPanel.selectWarehouseGood(0);
        ClassicColonyPanel.setTileCursor(ClassicColonyLayout.CENTRE_TILE);
        classic().applyDifficultyLevel("model.difficulty.medium");
        super.tearDown();
    }


    // The geometry (spec §1.1, M4.4 target 1)

    /** The cells, the tile under a point, the places of what stands on a tile. */
    public void testTheGeometry() {
        assertEquals(new Rectangle(224, 32, 24, 24), ClassicColonyLayout.tileCell(0));
        assertEquals(new Rectangle(248, 56, 24, 24), ClassicColonyLayout.tileCell(4));
        assertEquals(new Rectangle(272, 80, 24, 24), ClassicColonyLayout.tileCell(8));
        assertEquals(0, ClassicColonyLayout.tileAt(224, 32));
        assertEquals(0, ClassicColonyLayout.tileAt(247, 55));
        assertEquals(1, ClassicColonyLayout.tileAt(248, 32));
        assertEquals(4, ClassicColonyLayout.tileAt(260, 70));
        assertEquals(8, ClassicColonyLayout.tileAt(295, 103));
        assertEquals("the black frame", -1, ClassicColonyLayout.tileAt(223, 40));
        assertEquals(-1, ClassicColonyLayout.tileAt(296, 40));
        assertEquals(-1, ClassicColonyLayout.tileAt(250, 104));
        assertEquals("C", ClassicColonyLayout.TILE_NAMES[ClassicColonyLayout.CENTRE_TILE]);
        // The places by direction.
        assertEquals(0, ClassicColonyTiles.placeOf(Direction.NW));
        assertEquals(2, ClassicColonyTiles.placeOf(Direction.NE));
        assertEquals(5, ClassicColonyTiles.placeOf(Direction.E));
        assertEquals(8, ClassicColonyTiles.placeOf(Direction.SE));
        assertEquals(-1, ClassicColonyTiles.placeOf(null));
        // The worker at + (14,6) (V clip 020 ×9, dago 24), the totem at +
        // (8,4) (V #14817), the enemy's icon at + (4,4) (V war-french
        // #13962), the sign at the corner, its good centred in it (V sugar
        // at (276,33) on NE, #50260; playthrough-1 #75026).
        assertEquals(14, ClassicColonyLayout.TILE_WORKER_DX);
        assertEquals(6, ClassicColonyLayout.TILE_WORKER_DY);
        assertEquals(8, ClassicColonyLayout.TOTEM_DX);
        assertEquals(4, ClassicColonyLayout.TOTEM_DY);
        assertEquals(4, ClassicColonyLayout.ENEMY_DX);
        assertEquals(4, ClassicColonyLayout.ENEMY_DY);
        assertEquals(276, ClassicColonyLayout.zeroIconX(272, 8));
        assertEquals(64, ClassicColonyLayout.ICON_NO_PRODUCTION);
        assertEquals(108, ClassicColonyLayout.ICON_TOTEM);
        // The tile's hover label (dago §3.5): «Ozean» (w 20) on W from 226,
        // «Grünland/Straße/Gepflügt» (w 93) on N from 213, not clamped;
        // clip 020 #48712: «Tropen- Wald» (w 47) on N from 236.
        assertEquals(226, ClassicColonyLayout.tileLabelX(224, 20));
        assertEquals(213, ClassicColonyLayout.tileLabelX(248, 93));
        assertEquals(236, ClassicColonyLayout.tileLabelX(248, 47));
    }

    /** One measured row: the tile, the icons' y, the amount, the icon's width, the icons' x, the label's box. */
    private static void assertRow(String what, int tileX, int y, int n, int w, int[] xs,
                                  int[] label) {
        final Row r = ClassicColonyLayout.tileRow(tileX, y, n, w);
        assertNotNull(what, r);
        assertEquals(what + " icons", xs.length, r.m);
        for (int j = 0; j < xs.length; j++) assertEquals(what + " icon " + j, xs[j], r.x(j));
        assertEquals(what + " y", y, r.y);
        assertEquals(what + " label", label != null, r.stacked);
        if (label != null) {
            assertEquals(what + " label x", label[0], r.labelX());
            assertEquals(what + " label y", label[1], r.labelY());
        }
    }

    /** Icons from a first x at a pitch. */
    private static int[] xs(int first, int pitch, int m) {
        final int[] out = new int[m];
        for (int j = 0; j < m; j++) out[j] = first + j * pitch;
        return out;
    }

    /**
     * The tile rows of clip 020 (L §4.1, `TileFit` 0 px, and this step's
     * models of #48712 and #50260): spaced rows at w + 1 or less, the
     * centre's two rows, a stack with its label.
     */
    public void testTheTileRowsOfClip020() {
        // corn 022 w 7, sugar 023 w 8, furs 026 w 9, lumber 027 w 10, ore
        // 028 w 10, silver 029 w 10, fish 057 w 7 (the pack's sizes).
        assertRow("#2260 NW corn 5", 224, 33, 5, 7, xs(224, 4, 5), null);
        assertRow("#2260 N lumber 8", 248, 33, 8, 10, xs(248, 2, 8), null);
        assertRow("#2260 centre corn 6", 248, 57, 6, 7, xs(249, 3, 6), null);
        assertRow("#2260 centre sugar 3", 248, 70, 3, 8, xs(248, 8, 3), null);
        assertRow("#134173 NE ore 5", 272, 33, 5, 10, xs(273, 3, 5), null);
        assertRow("#134173 E fish 6", 272, 57, 6, 7, xs(273, 3, 6), null);
        assertRow("#134173 SW lumber 8", 224, 81, 8, 10, xs(224, 2, 8), null);
        assertRow("#105689 N sugar 12, «12» at (252,34)", 248, 33, 12, 8, xs(250, 1, 12),
                  new int[] { 252, 34 });
        assertRow("#14817 N sugar 1", 248, 33, 1, 8, new int[] { 256 }, null);
        assertRow("#14817 centre corn 4", 248, 57, 4, 7, xs(249, 5, 4), null);
        assertRow("#14817 centre sugar 2", 248, 70, 2, 8, xs(251, 9, 2), null);
        assertRow("#123003 W silver 3", 224, 57, 3, 10, xs(224, 7, 3), null);
        assertRow("#123003 centre corn 4", 248, 57, 4, 7, xs(249, 5, 4), null);
        assertRow("#123003 centre furs 3", 248, 70, 3, 9, xs(248, 7, 3), null);
        assertNull("nothing made: no row (the sign instead)",
                   ClassicColonyLayout.tileRow(272, 33, 0, 8));
    }

    /**
     * dago's 29 tile rows (its models {@code ov_base_1000_tiles},
     * {@code ov_base_1356_tiles}, {@code ov_furcity_9560_tiles},
     * {@code ov_furtown_11360_tiles}, 0 px each): stacks with labels, the
     * halved rows (30 lumber in 15, 22 tobacco in 11, 18 ore in 9, 22 lumber
     * in 11), spaced rows at pitch 2 to 8.
     */
    public void testTheTileRowsOfDago() {
        for (String[] r : DAGO_ROWS) {
            final int n = Integer.parseInt(r[2]);
            final int w = Integer.parseInt(r[3]);
            final String[] x = r[4].split(" ");
            final int[] icons = new int[x.length];
            for (int j = 0; j < x.length; j++) icons[j] = Integer.parseInt(x[j]);
            final int tileX = 224 + 24 * ((icons[0] - 224 + 3) / 24);
            final int[] label = (r[5].isEmpty()) ? null
                : Arrays.stream(r[5].split(" ")).mapToInt(Integer::parseInt).toArray();
            assertRow(r[0], tileX, Integer.parseInt(r[1]), n, w, icons, label);
        }
    }

    /** dago's rows: name, icon y, amount, icon width, the icons' x, the label's box (or ""). */
    private static final String[][] DAGO_ROWS = {
        { "Base #1000 NW fish 10", "33", "10", "7", "228 229 230 231 232 233 234 235 236 237", "230 34" },
        { "Base N tobacco 12", "33", "12", "8", "250 251 252 253 254 255 256 257 258 259 260 261", "252 34" },
        { "Base NE lumber 30", "33", "30", "10", "272 273 274 275 276 277 278 279 280 281 282 283 284 285 286", "274 34" },
        { "Base W fish 10", "57", "10", "7", "228 229 230 231 232 233 234 235 236 237", "230 58" },
        { "Base #1356 centre corn 8", "57", "8", "7", "249 251 253 255 257 259 261 263", "" },
        { "Base #1356 centre cotton 5", "70", "5", "10", "249 252 255 258 261", "" },
        { "Base E ore 18", "57", "18", "10", "275 276 277 278 279 280 281 282 283", "277 58" },
        { "Base SW fish 10", "81", "10", "7", "228 229 230 231 232 233 234 235 236 237", "230 82" },
        { "Base S tobacco 22", "81", "22", "8", "251 252 253 254 255 256 257 258 259 260 261", "253 82" },
        { "Base SE ore 14", "81", "14", "10", "272 273 274 275 276 277 278 279 280 281 282 283 284 285", "274 82" },
        { "Fur City #9560 N furs 12", "33", "12", "9", "250 251 252 253 254 255 256 257 258 259 260 261", "252 34" },
        { "Fur City NE furs 5", "33", "5", "9", "273 276 279 282 285", "" },
        { "Fur City W corn 5", "57", "5", "7", "224 228 232 236 240", "" },
        { "Fur City centre corn 4", "57", "4", "7", "249 254 259 264", "" },
        { "Fur City centre furs 3", "70", "3", "9", "248 255 262", "" },
        { "Fur City E corn 3", "57", "3", "7", "272 280 288", "" },
        { "Fur City SW fish 12", "81", "12", "7", "227 228 229 230 231 232 233 234 235 236 237 238", "229 82" },
        { "Fur City S lumber 14", "81", "14", "10", "248 249 250 251 252 253 254 255 256 257 258 259 260 261", "250 82" },
        { "Fur City SE corn 5", "81", "5", "7", "272 276 280 284 288", "" },
        { "Fur Town #11360 NW furs 10", "33", "10", "9", "227 228 229 230 231 232 233 234 235 236", "229 34" },
        { "Fur Town N furs 7", "33", "7", "9", "249 251 253 255 257 259 261", "" },
        { "Fur Town NE corn 9", "33", "9", "7", "272 274 276 278 280 282 284 286 288", "" },
        { "Fur Town W fish 16", "57", "16", "7", "225 226 227 228 229 230 231 232 233 234 235 236 237 238 239 240", "227 58" },
        { "Fur Town centre corn 6", "57", "6", "7", "249 252 255 258 261 264", "" },
        { "Fur Town centre furs 5", "70", "5", "9", "249 252 255 258 261", "" },
        { "Fur Town E lumber 22", "57", "22", "10", "274 275 276 277 278 279 280 281 282 283 284", "276 58" },
        { "Fur Town SW fish 10", "81", "10", "7", "228 229 230 231 232 233 234 235 236 237", "230 82" },
        { "Fur Town S corn 10", "81", "10", "7", "252 253 254 255 256 257 258 259 260 261", "254 82" },
        { "Fur Town SE furs 10", "81", "10", "9", "275 276 277 278 279 280 281 282 283 284", "277 82" },
    };


    // A test colony on the classic rules

    /** A colony of the classic rules at «Entdecker», its 3x3 map dressed as a clip's. */
    private static final class State {

        final Game game;
        final Colony colony;
        final Player owner;
        private Unit spare;

        State(String name, int year, String centre, String... centreImprovements) {
            this.game = getStandardGame("classic");
            // Clip 020's level (S2: the king calls the player «Entdecker»).
            classic().applyDifficultyLevel("model.difficulty.veryEasy");
            final net.sf.freecol.common.model.Map map = new MapBuilder(this.game)
                .setBaseTileType(classic().getTileType("model.tile.plains")).build();
            this.game.changeMap(map);
            for (int n = 1; n < 1000; n++) {
                final Turn t = new Turn(n);
                if (t.getYear() == year) {
                    this.game.setTurn(t);
                    break;
                }
            }
            this.owner = this.game.getPlayerByNationId("model.nation.dutch");
            final Tile c = map.getTile(5, 8);
            dress(c, centre, centreImprovements);
            this.colony = FreeColTestUtils.getColonyBuilder().player(this.owner)
                .colonyTile(c).initialColonists(1).build();
            this.colony.setName(name);
            this.colony.setBuildQueue(new ArrayList<>());
            this.spare = this.colony.getUnitList().get(0);
            this.spare.changeType(classic().getUnitType("model.unit.freeColonist"));
            this.colony.addBuilding(new ServerBuilding(this.game, this.colony,
                classic().getBuildingType("model.building.docks")));
            // The founder works the town hall until he is needed.
            this.spare.setLocation(this.colony.getBuilding(
                classic().getBuildingType("model.building.townHall")));
            this.colony.invalidateCache();
        }

        /** A tile's terrain and improvements. */
        void dress(Tile t, String type, String... improvements) {
            t.setType(classic().getTileType("model.tile." + type));
            for (String id : improvements) {
                final TileImprovement ti = new TileImprovement(this.game, t,
                    classic().getTileImprovementType("model.improvement." + id), null);
                ti.setTurnsToComplete(0);
                t.add(ti);
            }
        }

        /** A neighbour's terrain. */
        State around(Direction d, String type, String... improvements) {
            dress(this.colony.getTile().getNeighbourOrNull(d), type, improvements);
            this.colony.invalidateCache();
            return this;
        }

        /** A colonist of a type on a tile, working a good. */
        Unit on(String type, Direction d, String goods) {
            final UnitType ut = classic().getUnitType("model.unit." + type);
            final Unit u;
            if (this.spare != null) {
                u = this.spare;
                this.spare = null;
                u.changeType(ut);
            } else {
                u = new ServerUnit(this.game, this.colony.getTile(), this.owner, ut);
            }
            final ColonyTile ct = this.colony.getColonyTile(
                this.colony.getTile().getNeighbourOrNull(d));
            assertNotNull(d.toString(), ct);
            u.setLocation(ct);
            u.changeWorkType(classic().getGoodsType("model.goods." + goods));
            this.colony.invalidateCache();
            return u;
        }

        /** A colonist in a building (the founder first). */
        Unit in(String type, String building) {
            final UnitType ut = classic().getUnitType("model.unit." + type);
            final Unit u;
            if (this.spare != null) {
                u = this.spare;
                this.spare = null;
                u.changeType(ut);
            } else {
                u = new ServerUnit(this.game, this.colony.getTile(), this.owner, ut);
            }
            final Building b = this.colony.getBuilding(
                classic().getBuildingType("model.building." + building));
            assertTrue(building, u.setLocation(b));
            this.colony.invalidateCache();
            return u;
        }

        /** The work place of a tile. */
        ColonyTile tile(Direction d) {
            return this.colony.getColonyTile(this.colony.getTile().getNeighbourOrNull(d));
        }

        void finish() {
            assertNull("the founder was used", this.spare);
        }
    }

    /** Base's 3x3 map in clip 020 (its hover labels, 1539-1546: N «Tropen- Wald»). */
    private static State base(int year) {
        return new State("Base", year, "swamp", "plow", "road")
            .around(Direction.NW, "grassland")
            .around(Direction.N, "tropicalForest")
            .around(Direction.NE, "hills", "road")
            .around(Direction.W, "rainForest")
            .around(Direction.E, "ocean")
            .around(Direction.SW, "rainForest")
            .around(Direction.S, "ocean")
            .around(Direction.SE, "ocean");
    }

    /** The tiles' rows as drawn: "NW=022x5 C=022x6+023x3 ...", the figures left out. */
    private static String rows(ClassicColonyTiles.Model m) {
        final StringBuilder sb = new StringBuilder();
        for (ClassicColonyTiles.Cell c : m.cells) {
            if (c == null || (c.rows.isEmpty() && c.zero < 0)) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(ClassicColonyLayout.TILE_NAMES[c.index]).append('=');
            for (int k = 0; k < c.rows.size(); k++) {
                if (k > 0) sb.append('+');
                sb.append(c.rows.get(k));
            }
            if (c.zero >= 0) sb.append(String.format("%03dx0", c.zero));
        }
        return sb.toString();
    }

    private static ClassicColonyBuildings.Assets assets() {
        return ClassicColonyBuildings.Assets.load(ClassicPackFiles.runtime());
    }

    /**
     * The model of a test colony: the centre's food first and its other
     * good second; a worker's row of his job's good at y + 1 and his figure
     * at + (14,6); a worker who makes nothing (FreeCol's tile without a
     * production for him) under the sign; FreeCol's own fallback (a job the
     * tile cannot do, the classic rules: the colony gets the tile's best
     * other good) under the sign too, as the original shows it (part Y3);
     * the totem on native land, none
     * when the land costs nothing; an enemy at war; the hover labels.
     */
    public void testTheModel() {
        final State s = base(1546);
        final Unit farmer = s.on("expertFarmer", Direction.NW, "grain");
        s.on("expertLumberJack", Direction.N, "lumber");
        final Unit planter = s.on("masterSugarPlanter", Direction.NE, "sugar");
        s.finish();
        final ClassicText text = ClassicText.load(ClassicPackFiles.runtime());
        ClassicColonyTiles.Model m = ClassicColonyTiles.model(s.colony, text, assets());
        // The classic rules put the planter on the hills' best good (2
        // grain, the colony gets it); the screen shows his job's 0, the
        // sugar under the sign, as the original (part Y3; V clip 020
        // #48712, #50256), never that other good.
        assertTrue(s.colony.getColonyTile(s.colony.getTile().getNeighbourOrNull(Direction.NE))
                   .getProductionInfo().getProduction().stream()
                   .anyMatch(ag -> ag.getType().isFoodType() && ag.getAmount() == 2));
        assertEquals("NW=022x5 N=027x8 NE=023x0 C=022x4+023x2", rows(m));
        // A tile without a production for him (the original's 0 sugar):
        // the sugar under the sign (V #48712).
        s.tile(Direction.NE).setProductionType(null);
        s.colony.invalidateCache();
        m = ClassicColonyTiles.model(s.colony, text, assets());
        assertEquals("NW=022x5 N=027x8 NE=023x0 C=022x4+023x2", rows(m));
        final ClassicColonyTiles.Cell ne = m.cell(2);
        assertEquals(planter, ne.worker.unit);
        assertEquals(286, ne.worker.x);
        assertEquals(38, ne.worker.y);
        assertTrue(ne.rows.isEmpty());
        assertEquals(0, m.placeOf(farmer));
        assertEquals(2, m.placeOf(planter));
        assertEquals(-1, m.placeOf(null));
        // A colonist with no job (the square's production as FreeCol
        // chooses it): what the square makes, with no sign (the review of
        // part Y, regression: this path was not pinned).
        farmer.setWorkType(null);
        s.tile(Direction.NW).updateProductionType();
        s.colony.invalidateCache();
        final ClassicColonyTiles.Cell idle
            = ClassicColonyTiles.model(s.colony, text, assets()).cell(0);
        assertEquals(farmer, idle.worker.unit);
        assertEquals("what the square makes", 1, idle.rows.size());
        assertEquals("no sign", -1, idle.zero);
        assertTrue(idle.rows.get(0).toString().startsWith("022"));
        farmer.changeWorkType(classic().getGoodsType("model.goods.grain"));
        s.colony.invalidateCache();
        final ClassicColonyTiles.Cell c = m.cell(ClassicColonyLayout.CENTRE_TILE);
        assertNull(c.worker);
        assertEquals(57, c.rows.get(0).row.y);
        assertEquals(70, c.rows.get(1).row.y);
        assertTrue(c.rows.get(0).toString().startsWith("022"));
        assertNull("no worker, no row", m.cell(3).worker);
        assertTrue(m.cell(3).rows.isEmpty());
        if (text != null) {
            assertEquals("Grünland", m.cell(0).label);
            assertEquals("Tropen- Wald", m.cell(1).label);
            assertEquals("Hügellandschaft/Straße", m.cell(2).label);
            assertEquals("Regen- Wald", m.cell(3).label);
            assertEquals("Ozean", m.cell(5).label);
            assertEquals("Sumpfland/Straße/Gepflügt", c.label);
        }
        // Native land: the totem, not on the centre; none once the land
        // costs nothing (Peter Minuit).
        final Player tupi = s.game.getPlayerByNationId("model.nation.tupi");
        final Tile w = s.colony.getTile().getNeighbourOrNull(Direction.W);
        w.setOwner(tupi);
        assertTrue(ClassicColonyTiles.nativeLand(s.colony, w));
        m = ClassicColonyTiles.model(s.colony, text, assets());
        assertTrue(m.cell(3).totem);
        assertFalse(m.cell(0).totem);
        assertFalse(m.cell(4).totem);
        s.owner.addFather(classic().getFoundingFather("model.foundingFather.peterMinuit"));
        assertFalse("Minuit: the land costs nothing", ClassicColonyTiles.nativeLand(s.colony, w));
        // An enemy at war on a tile.
        final Player french = s.game.getPlayerByNationId("model.nation.french");
        final Tile sw = s.colony.getTile().getNeighbourOrNull(Direction.SW);
        final Unit soldier = new ServerUnit(s.game, sw, french,
            classic().getUnitType("model.unit.veteranSoldier"),
            classic().getRole("model.role.soldier"));
        s.owner.setStance(french, Stance.WAR);
        french.setStance(s.owner, Stance.WAR);
        m = ClassicColonyTiles.model(s.colony, text, assets());
        assertSame(soldier, m.cell(6).occupier);
        assertTrue(m.toString().contains("SW=- enemy"));
        // His icon at tile + (4,4) (war-french #13962), by the screen's
        // painter of a unit standing on a tile.
        final List<int[]> at = new ArrayList<>();
        final BufferedImage img = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = img.createGraphics();
        ClassicColonyTiles.paint(g, ClassicColonyChrome.Assets.load(ClassicPackFiles.runtime()),
            assets(), m, null, (gg, u, x, y) -> at.add(new int[] { x, y }), -1, -1, true);
        g.dispose();
        assertEquals(1, at.size());
        assertEquals(224 + 4, at.get(0)[0]);
        assertEquals(80 + 4, at.get(0)[1]);
    }

    /**
     * A tile another colony works gets a red frame (fixer A after part X,
     * the colony review's later item: V clip 048 #86873, Guadelupe's SW, S
     * and SE tiles, each cell its own 1-px frame #FF0000, palette 12): the
     * model marks it, the painter draws it on the tile's border under the
     * green and the white frames; the colony's own, the centre and native
     * land not.
     */
    public void testATileOfAnotherColonyIsFramedRed() {
        final State s = base(1546);
        s.on("expertFarmer", Direction.NW, "grain");
        s.finish();
        final ClassicText text = ClassicText.load(ClassicPackFiles.runtime());
        final Tile sw = s.colony.getTile().getNeighbourOrNull(Direction.SW);
        final Tile se = s.colony.getTile().getNeighbourOrNull(Direction.SE);
        final Tile two = s.colony.getTile().getNeighbourOrNull(Direction.S)
            .getNeighbourOrNull(Direction.S);
        final Player french = s.game.getPlayerByNationId("model.nation.french");
        final net.sf.freecol.common.model.Colony montreal
            = new net.sf.freecol.server.model.ServerColony(s.game, french, "Montreal", two);
        sw.changeOwnership(french, montreal);
        se.changeOwnership(s.owner, montreal);
        assertTrue(ClassicColonyTiles.takenByAnother(s.colony, sw));
        assertTrue("a colony of ours too (I)", ClassicColonyTiles.takenByAnother(s.colony, se));
        assertFalse(ClassicColonyTiles.takenByAnother(s.colony, s.colony.getTile()));
        assertFalse(ClassicColonyTiles.takenByAnother(montreal, sw));
        assertFalse(ClassicColonyTiles.takenByAnother(s.colony, null));
        final ClassicColonyTiles.Model m = ClassicColonyTiles.model(s.colony, text, assets());
        assertTrue(m.cell(6).taken);
        assertTrue(m.cell(8).taken);
        assertFalse(m.cell(7).taken);
        assertFalse(m.cell(ClassicColonyLayout.CENTRE_TILE).taken);
        assertTrue(m.toString().contains("taken"));
        final ClassicColonyChrome.Assets chrome
            = ClassicColonyChrome.Assets.load(ClassicPackFiles.runtime());
        final BufferedImage img = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = img.createGraphics();
        // The cursor white on SE: it wins over the red.
        ClassicColonyTiles.paint(g, chrome, assets(), m, null, null, -1, 8, true);
        g.dispose();
        final int red = chrome.rgb(ClassicColonyLayout.INK_RED) & 0xFFFFFF;
        final java.awt.Rectangle c6 = ClassicColonyLayout.tileCell(6);
        assertEquals(new java.awt.Rectangle(224, 80, 24, 24), c6);
        for (int x = c6.x; x < c6.x + c6.width; x++) {
            assertEquals(red, img.getRGB(x, c6.y) & 0xFFFFFF);
            assertEquals(red, img.getRGB(x, c6.y + c6.height - 1) & 0xFFFFFF);
        }
        for (int y = c6.y; y < c6.y + c6.height; y++) {
            assertEquals(red, img.getRGB(c6.x, y) & 0xFFFFFF);
            assertEquals(red, img.getRGB(c6.x + c6.width - 1, y) & 0xFFFFFF);
        }
        final java.awt.Rectangle c8 = ClassicColonyLayout.tileCell(8);
        assertEquals(chrome.rgb(ClassicColonyLayout.INK_WHITE) & 0xFFFFFF,
                     img.getRGB(c8.x + 5, c8.y) & 0xFFFFFF);
        final java.awt.Rectangle c7 = ClassicColonyLayout.tileCell(7);
        assertTrue(red != (img.getRGB(c7.x + 5, c7.y) & 0xFFFFFF));
        // The clip's frame: the same red on the same borders (#86873).
        final String clips = System.getProperty(ClassicTerrainGoldenTest.CLIPS_PROPERTY);
        final java.io.File f = (clips == null) ? null
            : new java.io.File(clips, "fixer-a-analysis/c048/frame_086873.png");
        if (f != null && f.isFile()) {
            try {
                final BufferedImage clip = javax.imageio.ImageIO.read(f);
                for (int i = 6; i <= 8; i++) {
                    final java.awt.Rectangle r = ClassicColonyLayout.tileCell(i);
                    for (int x = r.x; x < r.x + r.width; x++) {
                        assertEquals("cell " + i, red, clip.getRGB(x, r.y) & 0xFFFFFF);
                        assertEquals("cell " + i, red,
                                     clip.getRGB(x, r.y + r.height - 1) & 0xFFFFFF);
                    }
                    for (int y = r.y; y < r.y + r.height; y++) {
                        assertEquals("cell " + i, red, clip.getRGB(r.x, y) & 0xFFFFFF);
                        assertEquals("cell " + i, red,
                                     clip.getRGB(r.x + r.width - 1, y) & 0xFFFFFF);
                    }
                }
            } catch (java.io.IOException e) {
                fail(e.toString());
            }
        }
    }

    /**
     * Part X: on the levi rules the screen's tile rows show the original's
     * numbers (the model's, ClassicColonyTiles.made): Base's centre
     * «Sumpfland/Straße/Gepflügt» 6 food + 3 sugar (V clip 020 #2260),
     * the expert lumberjack on tropical forest with a road 10 (V #75440),
     * the expert fisherman on the coast 6 (V #134173 E); with the rules'
     * options off FreeCol's 4 + 2, 12 and 7.
     */
    public void testTheLeviRulesShowTheOriginalsNumbers() {
        final Specification levi = spec("levi");
        final String[] options = {
            net.sf.freecol.common.option.GameOptions.ORIGINAL_COLONY_CENTER,
            net.sf.freecol.common.option.GameOptions.ORIGINAL_LUMBER,
            net.sf.freecol.common.option.GameOptions.ORIGINAL_FISHERMAN,
            net.sf.freecol.common.option.GameOptions.ORIGINAL_HORSE_FOOD,
            net.sf.freecol.common.option.GameOptions.ORIGINAL_ROAD_SILVER };
        try {
            final Game game = getStandardGame("levi");
            levi.applyDifficultyLevel("model.difficulty.veryEasy");
            final net.sf.freecol.common.model.Map map = new MapBuilder(game)
                .setBaseTileType(levi.getTileType("model.tile.plains")).build();
            game.changeMap(map);
            final Player dutch = game.getPlayerByNationId("model.nation.dutch");
            final Tile c = map.getTile(5, 8);
            c.setType(levi.getTileType("model.tile.swamp"));
            final Tile n = c.getNeighbourOrNull(Direction.N);
            n.setType(levi.getTileType("model.tile.tropicalForest"));
            final Tile e = c.getNeighbourOrNull(Direction.E);
            e.setType(levi.getTileType("model.tile.ocean"));
            for (Object[] ti : new Object[][] { { c, "plow" }, { c, "road" }, { n, "road" },
                                                 { e, "fishBonusLand" } }) {
                final TileImprovement imp = new TileImprovement(game, (Tile)ti[0],
                    levi.getTileImprovementType("model.improvement." + ti[1]), null);
                imp.setTurnsToComplete(0);
                ((Tile)ti[0]).add(imp);
            }
            final Colony colony = new net.sf.freecol.server.model.ServerColony(game, dutch,
                "Base", c);
            dutch.addSettlement(colony);
            colony.placeSettlement(true);
            colony.addBuilding(new ServerBuilding(game, colony,
                levi.getBuildingType("model.building.docks")));
            final Unit lumberjack = new ServerUnit(game, c, dutch,
                levi.getUnitType("model.unit.expertLumberJack"));
            assertTrue(lumberjack.setLocation(colony.getColonyTile(n)));
            lumberjack.changeWorkType(levi.getGoodsType("model.goods.lumber"));
            final Unit fisherman = new ServerUnit(game, c, dutch,
                levi.getUnitType("model.unit.expertFisherman"));
            assertTrue(fisherman.setLocation(colony.getColonyTile(e)));
            fisherman.changeWorkType(levi.getGoodsType("model.goods.fish"));
            colony.invalidateCache();
            final ClassicText text = ClassicText.load(ClassicPackFiles.runtime());
            assertEquals("N=027x10 C=022x6+023x3 E=057x6",
                         rows(ClassicColonyTiles.model(colony, text, assets())));
            for (String id : options) levi.setBoolean(id, false);
            colony.invalidateCache();
            assertEquals("FreeCol's", "N=027x12 C=022x4+023x2 E=057x7",
                         rows(ClassicColonyTiles.model(colony, text, assets())));
        } finally {
            for (String id : options) levi.setBoolean(id, true);
            levi.applyDifficultyLevel("model.difficulty.medium");
        }
    }

    /**
     * A stack's count label follows «Beschriftung an Waren und Terrain»
     * (I: the option's meaning in the original, as the buildings' «12»):
     * the expert lumberjack's 12 lumber on the tropical forest with a road
     * (FreeCol's) are a stack from 249 at pitch 1, «12» at (251,34).
     */
    public void testTheCountLabelsFollowTheOption() throws Exception {
        final State s = base(1546).around(Direction.N, "tropicalForest", "road");
        s.on("expertLumberJack", Direction.N, "lumber");
        s.finish();
        final ClassicColonyPanel p = panel(s);
        // The shared prefs in memory for this test (no file written).
        final java.lang.reflect.Field shared = ClassicPrefs.class.getDeclaredField("shared");
        shared.setAccessible(true);
        final Object was = shared.get(null);
        final ClassicPrefs prefs = new ClassicPrefs(null);
        shared.set(null, prefs);
        try {
            prefs.set(ClassicPrefs.GOODS_TERRAIN_LABELS, true);
            final BufferedImage on = p.canvas();
            final ClassicColonyTiles.TileRow r = p.tiles().cell(1).rows.get(0);
            assertEquals(12, r.row.n);
            assertTrue(r.row.stacked);
            assertEquals(251, r.row.labelX());
            assertEquals(34, r.row.labelY());
            prefs.set(ClassicPrefs.GOODS_TERRAIN_LABELS, false);
            final BufferedImage off = p.canvas();
            int black = 0, differ = 0;
            for (int y = 34; y < 41; y++) {
                for (int x = 251; x < 251 + 9; x++) {
                    if ((on.getRGB(x, y) & 0xFFFFFF) == 0) black++;
                    if (on.getRGB(x, y) != off.getRGB(x, y)) differ++;
                }
            }
            assertTrue("the label's box: " + black, black > 20);
            assertTrue("gone without the option: " + differ, differ > 20);
        } finally {
            shared.set(null, was);
        }
    }

    /**
     * The screen draws the colony's sprite on the centre and its overhang
     * in the W and E cells (the review of part Y, fidelity; V clip 020
     * #2260's E): a 21-px sprite at cell x - 2 covers W's source columns
     * 14-15 and E's 0-2; no other cell gets any of it.
     */
    public void testTheScreenDrawsTheOverhang() throws Exception {
        if (!ClassicColonyTiles.Art.load(ClassicPackFiles.runtime(), null).indexed()) {
            System.err.println(getName() + ": no index sheets, skipped");
            return;
        }
        final State s = base(1539);
        final String key = net.sf.freecol.client.gui.ImageLibrary.getSettlementKey(s.colony);
        final BufferedImage huts = new BufferedImage(21, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 21; x++) huts.setRGB(x, y, 0xFF123456);
        final net.sf.freecol.client.gui.ImageLibrary lib
            = new net.sf.freecol.client.gui.ImageLibrary(1f, new net.sf.freecol.common.resources.ImageCache() {
                @Override
                public BufferedImage getScaledImage(String k, float scale, boolean grayscale) {
                    return (key.equals(k)) ? huts
                        : new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                }

                @Override
                public BufferedImage getSizedImage(String k, java.awt.Dimension size,
                                                   boolean grayscale, int seed) {
                    return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                }
            });
        ClassicColonyPanel.forgetFigureOrders();
        ClassicColonyPanel.setTileCursor(ClassicColonyPanel.CURSOR_OFF_TILES);
        final ClassicColonyPanel p = new ClassicColonyPanel(
            ClassicColonyBuildingsTest.client(s.game, s.owner), lib, s.colony, null, null,
            ClassicSlide.SYSTEM, false);
        final BufferedImage img = p.canvas();
        for (int slot = 0; slot < 9; slot++) {
            if (slot == ClassicColonyLayout.CENTRE_TILE) continue;
            final Rectangle r = ClassicColonyLayout.tileCell(slot);
            int hut = 0;
            for (int y = 0; y < 24; y++) {
                for (int x = 0; x < 24; x++) {
                    final int sx = ClassicColonyTiles.enlargedSourceX(x);
                    final boolean want = (slot == 3 && sx >= 14) || (slot == 5 && sx <= 2);
                    final boolean is = (img.getRGB(r.x + x, r.y + y) & 0xFFFFFF) == 0x123456;
                    assertEquals(ClassicColonyLayout.TILE_NAMES[slot] + " " + x + "," + y,
                                 want, is);
                    if (is) hut++;
                }
            }
            assertEquals(ClassicColonyLayout.TILE_NAMES[slot],
                         (slot == 3) ? 3 * 24 : (slot == 5) ? 5 * 24 : 0, hut);
        }
    }

    /**
     * The screen knows when its 3x3 map holds water that cycles (a palette
     * step then paints it again): Base's swamp centre does, a colony among
     * plains does not.
     */
    public void testTheScreenKnowsItsCyclingWater() throws Exception {
        if (!ClassicColonyTiles.Art.load(ClassicPackFiles.runtime(), null).indexed()) {
            System.err.println(getName() + ": no index sheets, skipped");
            return;
        }
        final State s = base(1539);
        s.on("expertFarmer", Direction.NW, "grain");
        final ClassicColonyPanel p = panel(s);
        p.canvas();
        assertTrue(p.tilesCycle());
        final State plain = new State("Plain", 1539, "plains");
        plain.on("expertFarmer", Direction.NW, "grain");
        final ClassicColonyPanel q = panel(plain);
        q.canvas();
        assertFalse(q.tilesCycle());
    }

    /**
     * The white cursor: global (the next visit, another colony), on the
     * centre at first; a press on a tile or its worker brings it there, a
     * press on another colonist takes it off the tiles; drawn only in the
     * blink's on half, over the selected one's green frame.
     */
    public void testTheCursor() throws Exception {
        final State s = base(1539);
        final Unit farmer = s.on("expertFarmer", Direction.NW, "grain");
        final Unit vet = s.in("veteranSoldier", "townHall");
        s.finish();
        assertEquals(ClassicColonyLayout.CENTRE_TILE, ClassicColonyPanel.tileCursor());
        final ClassicColonyPanel p = panel(s);
        p.canvas();
        // A press on the farmer: the cursor on NW, he is selected.
        p.clickAt(240, 45);
        assertEquals(0, ClassicColonyPanel.tileCursor());
        assertSame(farmer, p.selected());
        // On an empty tile: the cursor there (nobody selected: nothing
        // moves; the first slice's click-target needs the game's
        // controller).
        p.select(null);
        p.clickAt(250, 90);
        assertEquals(7, ClassicColonyPanel.tileCursor());
        assertNull(p.selected());
        // On the Rathaus' worker: off the tiles.
        p.canvas();
        final ClassicColonyBuildings.Figure f = p.area().figures().stream()
            .filter(x -> x.unit == vet).findFirst().get();
        p.clickAt(f.x + 1, f.y + 8);
        assertEquals(ClassicColonyPanel.CURSOR_OFF_TILES, ClassicColonyPanel.tileCursor());
        assertSame(vet, p.selected());
        // It carries over to a new screen.
        ClassicColonyPanel.setTileCursor(5);
        final ClassicColonyPanel q = panel(s);
        q.select(null);
        final BufferedImage on = q.canvas();
        q.blinkPhase(true);
        final BufferedImage off = q.canvas();
        final Rectangle e = ClassicColonyLayout.tileCell(5);
        final int white = ClassicColonyChrome.Assets.load(ClassicPackFiles.runtime())
            .rgb(ClassicColonyLayout.INK_WHITE);
        assertEquals("white on in the on half", white, on.getRGB(e.x, e.y + 5) & 0xFFFFFF);
        assertFalse("not in the off half", (off.getRGB(e.x, e.y + 5) & 0xFFFFFF) == white
                    && (off.getRGB(e.x + 10, e.y) & 0xFFFFFF) == white);
    }

    /**
     * The terrain art (part Y5): our map's composed tile (the pack's index
     * sheets) enlarged as the original ({@link ClassicColonyTiles#enlarge},
     * the cell's slot anchoring the shades) through the game palette at
     * the phase; the colony's sprite on the centre where the map draws it,
     * its palette colours shaded with the terrain, a colour the palette
     * lacks kept; the swamp holds cycling indices and changes with the
     * phase, the plains do not; the fallback's scale in the original's
     * geometry, floor(2x/3) and floor((2y + 1)/3).
     */
    public void testTheArt() {
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final ClassicColonyTiles.Art art = ClassicColonyTiles.Art.load(pack, null);
        if (!art.indexed()) {
            System.err.println(getName() + ": no index sheets, skipped");
            return;
        }
        final State s = base(1539);
        final Tile c = s.colony.getTile();
        final Tile far = c.getMap().getTile(c.getMap().getWidth() - 3, c.getMap().getHeight() - 3);
        final boolean[] cyc = { false };
        // Base's centre, the swamp: its water cycles (the composer's
        // cycling indices 120-127: swamps, rivers, sea lanes, beaches).
        final int centre = ClassicColonyLayout.CENTRE_TILE;
        final BufferedImage swamp0 = art.tile(c.getMap(), c, centre, null, 0, cyc);
        assertEquals(24, swamp0.getWidth());
        assertEquals(24, swamp0.getHeight());
        assertTrue("the swamp cycles", cyc[0]);
        final BufferedImage swamp3 = art.tile(c.getMap(), c, centre, null, 3, null);
        int changed = 0;
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 24; x++) if (swamp0.getRGB(x, y) != swamp3.getRGB(x, y)) changed++;
        }
        assertTrue("another phase, other water: " + changed, changed > 0);
        cyc[0] = false;
        final BufferedImage land = art.tile(c.getMap(), far, 0, null, 0, cyc);
        assertNotNull(land);
        assertFalse("plains among plains do not cycle", cyc[0]);
        // Each pixel: the composed cell's, enlarged and shaded at its slot.
        final ClassicTerrainComposer comp = ClassicTerrainLayer.create(pack).composer();
        final ClassicGamePalette pal = art.palette();
        final byte[] cell = new byte[256];
        comp.composeCell(ClassicColonyTiles.source(c.getMap()), c.getX(), c.getY(), cell, 0, 16);
        final byte[] big = ClassicColonyTiles.enlarge(cell, centre);
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 24; x++) {
                assertEquals(x + "," + y, pal.rgb(3, big[y * 24 + x] & 0xFF),
                             swamp3.getRGB(x, y) & 0xFFFFFF);
            }
        }
        // The same tile in another slot: other shades.
        final BufferedImage elsewhere = art.tile(c.getMap(), c, 0, null, 3, null);
        int other = 0;
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 24; x++) if (elsewhere.getRGB(x, y) != swamp3.getRGB(x, y)) other++;
        }
        assertTrue("the shades are the 3x3's: " + other, other > 100);
        // The fallback's scale: the original's geometry.
        final int[] src = new int[256];
        for (int k = 0; k < 256; k++) src[k] = 0xFF000000 | k;
        final BufferedImage sc = ClassicColonyTiles.scale(src, 16, 24);
        for (int d = 0; d < 24; d++) {
            assertEquals(d + "", (2 * d) / 3, sc.getRGB(d, 0) & 0xFF);
            assertEquals(d + "", 16 * ((2 * d + 1) / 3), sc.getRGB(0, d) & 0xFF);
        }
        // The colony's sprite over the centre: an opaque 21-px sprite
        // overhangs 2 px each side on the map (settlementOffset -2), so in
        // the 24-px cell it covers every column, its 16 rows the cell's 24.
        // A colour the palette lacks: kept, unshaded.
        final BufferedImage sprite = new BufferedImage(21, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 21; x++) sprite.setRGB(x, y, 0xFF123456);
        final BufferedImage kept = art.tile(c.getMap(), c, centre, sprite, 0, null);
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 24; x++) {
                assertEquals(x + "," + y, 0x123456, kept.getRGB(x, y) & 0xFFFFFF);
            }
        }
        // Its overhang in the neighbours (the review of part Y): columns 0-1
        // in W's source columns 14-15 (its x 21-23), columns 18-20 in E's
        // source columns 0-2 (its x 0-4); the rest their own terrain, and
        // no other cell is reached.
        assertEquals(0, ClassicColonyTiles.spriteDx(centre));
        assertEquals(16, ClassicColonyTiles.spriteDx(centre - 1));
        assertEquals(-16, ClassicColonyTiles.spriteDx(centre + 1));
        for (int slot : new int[] { 0, 1, 2, 6, 7, 8 }) {
            assertEquals(ClassicColonyTiles.NO_SPRITE, ClassicColonyTiles.spriteDx(slot));
        }
        final BufferedImage west = art.tile(c.getMap(), far, centre - 1, sprite, 16, 0, null);
        final BufferedImage east = art.tile(c.getMap(), far, centre + 1, sprite, -16, 0, null);
        final BufferedImage plainW = art.tile(c.getMap(), far, centre - 1, null, 0, null);
        final BufferedImage plainE = art.tile(c.getMap(), far, centre + 1, null, 0, null);
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 24; x++) {
                final int sx = ClassicColonyTiles.enlargedSourceX(x);
                assertEquals("W " + x + "," + y, (sx >= 14) ? 0x123456
                             : plainW.getRGB(x, y) & 0xFFFFFF, west.getRGB(x, y) & 0xFFFFFF);
                assertEquals("E " + x + "," + y, (sx <= 2) ? 0x123456
                             : plainE.getRGB(x, y) & 0xFFFFFF, east.getRGB(x, y) & 0xFFFFFF);
            }
        }
        // The fallback without the index sheets: the overhang alike.
        final ClassicColonyTiles.Art fallback
            = ClassicColonyTiles.Art.load(null, ClassicColonyBuildingsTest.library());
        assertFalse(fallback.indexed());
        final BufferedImage fbW = fallback.tile(c.getMap(), far, centre - 1, sprite, 16, 0, null);
        final BufferedImage fbE = fallback.tile(c.getMap(), far, centre + 1, sprite, -16, 0, null);
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 24; x++) {
                final int sx = ClassicColonyTiles.enlargedSourceX(x);
                assertEquals("fallback W " + x + "," + y, sx >= 14,
                             (fbW.getRGB(x, y) & 0xFFFFFF) == 0x123456);
                assertEquals("fallback E " + x + "," + y, sx <= 2,
                             (fbE.getRGB(x, y) & 0xFFFFFF) == 0x123456);
            }
        }
        // A palette green (0x44): the green's shades at the centre's
        // place (V the colony's sprite shaded with the terrain, #123003);
        // the Dutch flag's orange 0x0D: an EGA colour, unshaded (V).
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 21; x++) {
                sprite.setRGB(x, y, 0xFF000000 | pal.rgb(0, (x == 10) ? 0x0D : 0x44));
            }
        }
        final BufferedImage shaded = art.tile(c.getMap(), c, centre, sprite, 0, null);
        final java.util.Set<Integer> greens = new java.util.HashSet<>();
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 24; x++) {
                final int sx = ClassicColonyTiles.enlargedSourceX(x) + 2;
                final int want = (sx == 10) ? 0x0D
                    : ClassicColonyTiles.shaded(0x44, 24 + x, 24 + y);
                assertEquals(x + "," + y, pal.rgb(0, want), shaded.getRGB(x, y) & 0xFFFFFF);
                if (sx != 10) greens.add(want);
            }
        }
        assertTrue("the green's shades: " + greens, greens.size() >= 3
                   && greens.stream().allMatch(v -> v >= 0x40 && v <= 0x47));
    }

    /** The source index shaded at a step of the pattern (the same at every pixel of that step). */
    private static int shadedAt(int s, int q) {
        int found = -1;
        for (int y = 0; y < 72; y++) {
            for (int x = 0; x < 72; x++) {
                if ((x + ClassicColonyTiles.noiseRow(y)) % ClassicColonyTiles.NOISE_PERIOD != q) {
                    continue;
                }
                final int v = ClassicColonyTiles.shaded(s, x, y);
                if (found < 0) found = v;
                assertEquals("step " + q + " at " + x + "," + y, found, v);
            }
        }
        assertTrue("step " + q + " in the 3x3", found >= 0);
        return found;
    }

    /**
     * The original's enlargement as pure functions (part Y5, prep y
     * {@code 3x3-scaler.md} §3 and builder 2's chain): the geometry, the
     * rows' offsets of the pattern, its antiperiods and its values, the
     * ramps, and the shades of the frames' cases: s +- d in the ramp, the
     * middle one of three chained to the pixel left of it.
     */
    public void testTheEnlargementRules() {
        // The geometry: columns 2, 1, 2 ... per source pixel, rows 1, 2, 1 ...
        final int[] sx = { 0, 0, 1, 2, 2, 3 }, sy = { 0, 1, 1, 2, 3, 3 };
        for (int d = 0; d < 24; d++) {
            assertEquals(d + "", sx[d % 6] + 4 * (d / 6), ClassicColonyTiles.enlargedSourceX(d));
            assertEquals(d + "", sy[d % 6] + 4 * (d / 6), ClassicColonyTiles.enlargedSourceY(d));
        }
        assertEquals(15, ClassicColonyTiles.enlargedSourceX(23));
        assertEquals(15, ClassicColonyTiles.enlargedSourceY(23));
        // The rows' offsets (prep §3.1), the same as the steps between
        // rows -72 -36 -24 +60 +84 +48 by y mod 6, over all 72 rows.
        final int[] off = { 0, 156, 132, 0, 84, 132, 60, 24, 0, 60, 144, 0, 120, 84, 60, 120,
                            12, 60, 180, 144, 120, 180, 72, 120 };
        for (int y = 0; y < off.length; y++) {
            assertEquals("row " + y, off[y], ClassicColonyTiles.noiseRow(y));
        }
        final int[] step = { -72, -36, -24, 60, 84, 48 };
        int o = 0;
        for (int y = 0; y < 72; y++) {
            if (y > 0) o = Math.floorMod(o + step[y % 6], 192);
            assertEquals("row " + y, o, ClassicColonyTiles.noiseRow(y));
            assertEquals(0, o % 12);
        }
        // The patterns: 192 steps, the antiperiod; +-3 only in the middle
        // of three, the others 0, +-1, +-2.
        assertEquals(192, ClassicColonyTiles.NOISE.length);
        assertEquals(192, ClassicColonyTiles.GREY_NOISE.length);
        for (int q = 0; q < 96; q++) {
            assertEquals("antiperiod " + q, -ClassicColonyTiles.NOISE[q],
                         ClassicColonyTiles.NOISE[q + 96]);
            assertEquals("grey antiperiod " + q, -ClassicColonyTiles.GREY_NOISE[q],
                         ClassicColonyTiles.GREY_NOISE[q + 96]);
        }
        for (int q = 0; q < 192; q++) {
            final int a = Math.abs(ClassicColonyTiles.NOISE[q]);
            assertTrue("step " + q + ": " + a, (q % 3 == 1) ? a <= 3 : a <= 2);
        }
        // The grey's own steps (not 4 x the others), the chain's six.
        assertEquals(3, ClassicColonyTiles.GREY_NOISE[4]);
        assertEquals(8, ClassicColonyTiles.GREY_NOISE[7]);
        assertEquals(8, ClassicColonyTiles.GREY_NOISE[37]);
        assertEquals(12, ClassicColonyTiles.GREY_NOISE[94]);
        assertEquals(-8, ClassicColonyTiles.GREY_NOISE[133]);
        assertEquals(10, ClassicColonyTiles.GREY_NOISE[139]);
        assertEquals(10, ClassicColonyTiles.GREY_NOISE[169]);
        assertEquals(-12, ClassicColonyTiles.GREY_NOISE[190]);
        // The ramps.
        assertEquals(0x10, ClassicColonyTiles.rampLow(0x29));
        assertEquals(0x2F, ClassicColonyTiles.rampHigh(0x10));
        assertEquals(0x30, ClassicColonyTiles.rampLow(0x3F));
        assertEquals(0x3F, ClassicColonyTiles.rampHigh(0x30));
        assertEquals(0x40, ClassicColonyTiles.rampLow(0x47));
        assertEquals(0x47, ClassicColonyTiles.rampHigh(0x40));
        assertEquals(0x68, ClassicColonyTiles.rampLow(0x6F));
        assertEquals(0x78, ClassicColonyTiles.rampLow(0x7A));
        assertEquals(0x80, ClassicColonyTiles.rampLow(0x87));
        for (int s : new int[] { 0x00, 0x05, 0x0D, 0x0F, 0x70, 0x77, 0x88, 0x8A, 0x90, 0xFD }) {
            assertEquals(Integer.toHexString(s), -1, ClassicColonyTiles.rampLow(s));
            for (int q = 0; q < 192; q += 7) assertEquals(s, shadedAt(s, q));
        }
        // s +- d (prep §3.4): in the ramp, else reflected.
        assertEquals(0x44, shadedAt(0x43, 3));    // +1
        assertEquals(0x46, shadedAt(0x47, 3));    // +1 at the ramp's end: 46 (V 128 times)
        assertEquals(0x44, shadedAt(0x46, 15));   // +2: 44
        assertEquals(0x49, shadedAt(0x48, 5));    // -1 at the ramp's start: 49
        // The chain: the middle of three (q mod 3 = 1) is the pixel left
        // of it plus the step, reflected about it.  q 46 (+2, +1): Base's
        // W, #2260 (10,18) s46 -> 43, (16,19) s47 -> 44; in the ramp +1.
        assertEquals(0x43, shadedAt(0x46, 46));
        assertEquals(0x44, shadedAt(0x47, 46));
        assertEquals(0x44, shadedAt(0x43, 46));
        // q 79 (+1, +3): s47 -> 44, s46 -> 45 (the prep's |d| = 3 rule).
        assertEquals(0x44, shadedAt(0x47, 79));
        assertEquals(0x45, shadedAt(0x46, 79));
        // q 142 (-2, -1): Silver City's N, #123003 (16,22) s48 -> 4B.
        assertEquals(0x4B, shadedAt(0x48, 142));
        // The grey: q 190 (-4, -12), Silver City's SW s29 -> 1D (the
        // prep's table: +4, 2D); q 4 (+5, +3): s29 -> 2C (V prep §3.3).
        assertEquals(0x1D, shadedAt(0x29, 190));
        assertEquals(0x2C, shadedAt(0x29, 4));
    }

    /**
     * Golden (part Y5, self-contained): three open-ocean cells
     * (TERRAIN.SS.010) composed and enlarged at the slots NW, N, NE are
     * the original's first six rows of Silver City's 3x3 (clip 020
     * #123003, y 32-37, x 224-295), 432 of 432 indices.
     */
    public void testTheOceanAsTheOriginal() {
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final ClassicTerrainLayer layer = (pack == null) ? null : ClassicTerrainLayer.create(pack);
        if (layer == null) {
            System.err.println(getName() + ": no index sheets, skipped");
            return;
        }
        final String[] rows = {
            "3a393a3c3c3b3b3c3b3b3b3c3a383b3e3d3b3b3b3a3b3c3c39393b3b393d3d3c3b3d3d3a3b3c3a3b3b3c3a393c3d3c3a3b3b393b3c3c3a3a3c3c3b3b3d3c383d3d3a3a3b3b3a3a3d",
            "3a3b3c3a3a3c3b3a3b3d3d3a3b3c3b3b3c3d3a383c3a393b3d3d393b3c3b3a3a3d3b393c3b3a3b3d3d3a3b3c3b39393c3b393b3d3c3b3c3c3a3c3d3b3a3a3c3c3a3c3d3c3b3b3b3a",
            "3c3b3a3c3c3a3b3c3a3a3b3d3a3a3c3c3b3b3c3c3a3a3b3b3a3b3c3a3a3c3b3a3b3d3d3a3b3c3b3b3c3d3a383c3a393b3d3d393b3c3b3a3a3d3b393c3b3a3b3d3d3a3b3c3b39393c",
            "3b3a3b3c3c3b3b3c3a3a3a3c39373c3d3c3b3b3b3a3b3c3b3a3a3c3b393d3d3c3a3c3c3a3a3b3b3a3a3c3a393c3d3c393c3c3a3b3c3c3a3a3b3b3a3b3c3b393c3c3a3a3b3b3a3a3c",
            "39393c3c3b3b3c3c393c3e3a3b3c3a3a3a3c3b3a3b3b3b393b3d3a3a3b3b3a3a3c3b3a3b3c3c393b3d3a393a3b39393b3a393b3d3d3a3b3c3a393a3d3a3a3b3b3a3b3c3c3a3a3b3a",
            "3a393b3d3d3a3b3c3a393a3d3a3a3b3b3a3b3c3c3a3a3b3a38393d3b3b3c3b3a3b3c3c3a3b3c3a3a3b3d3a383c3a393a3b3b3a3c3d3b3a3a3d3a383c3b3a3a3c3c3a3b3c3b39393b" };
        final net.sf.freecol.common.model.TileType ocean = classic().getTileType("model.tile.ocean");
        final ClassicTerrainComposer.TerrainSource sea = new ClassicTerrainComposer.TerrainSource() {
                @Override
                public boolean onMap(int x, int y) {
                    return x >= 0 && y >= 0 && x < 9 && y < 9;
                }

                @Override
                public boolean explored(int x, int y) {
                    return onMap(x, y);
                }

                @Override
                public net.sf.freecol.common.model.TileType type(int x, int y) {
                    return onMap(x, y) ? ocean : null;
                }

                @Override
                public void overlays(int x, int y, java.util.function.IntConsumer frames) {}
            };
        int equal = 0, old = 0;
        for (int slot = 0; slot < 3; slot++) {
            final byte[] cell = new byte[256];
            layer.composer().composeCell(sea, 3 + slot, 3, cell, 0, 16);
            final byte[] big = ClassicColonyTiles.enlarge(cell, slot);
            for (int y = 0; y < rows.length; y++) {
                for (int x = 0; x < 24; x++) {
                    final int at = 2 * (24 * slot + x);
                    final int want = Integer.parseInt(rows[y].substring(at, at + 2), 16);
                    if ((big[y * 24 + x] & 0xFF) == want) equal++;
                    if ((cell[((2 * y + 1) / 3) * 16 + (2 * x + 1) / 3] & 0xFF) == want) old++;
                }
            }
        }
        System.err.println(getName() + ": " + equal + " of 432 (before part Y5, floor((2d + 1)/3)"
                           + " without shades: " + old + ")");
        assertEquals(432, equal);
    }

    /**
     * The resource marker and the rumour: drawn unscaled and unshaded
     * over the enlarged terrain at tile + (4,4) (part Y5, V the fish on
     * Provisorische Kolonie's NE, clip 020 #14817, 70 of 70 px; the other
     * markers and the rumour I), not in the composed cell that is
     * enlarged; on the colony's own tile, under its sprite, the map's cell
     * as it is (I).
     */
    public void testTheMarkersAreDrawnUnscaled() {
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final ClassicColonyTiles.Art art = ClassicColonyTiles.Art.load(pack, null);
        if (!art.indexed()) {
            System.err.println(getName() + ": no index sheets, skipped");
            return;
        }
        final State s = base(1539);
        final net.sf.freecol.common.model.Map map = s.colony.getTile().getMap();
        final Tile e = s.colony.getTile().getNeighbourOrNull(Direction.E);
        e.addResource(new net.sf.freecol.common.model.Resource(s.game, e,
            classic().getResourceType("model.resource.fish")));
        final Tile w = s.colony.getTile().getNeighbourOrNull(Direction.W);
        w.addLostCityRumour(new net.sf.freecol.common.model.LostCityRumour(s.game, w));
        final ClassicTerrainComposer comp = ClassicTerrainLayer.create(pack).composer();
        final ClassicIndexSheet phys = pack.indexSheet(ClassicPackFiles.PHYS0_SS);
        final ClassicGamePalette pal = art.palette();
        final int[][] cases = { { 5, 96 }, { 3, ClassicTileArt.LOST_CITY } };
        for (int[] k : cases) {
            final Tile t = (k[0] == 5) ? e : w;
            final List<Integer> marks = new ArrayList<>();
            final byte[] cell = new byte[256], whole = new byte[256];
            comp.composeCell(ClassicColonyTiles.source(map, marks::add), t.getX(), t.getY(),
                             cell, 0, 16);
            comp.composeCell(ClassicColonyTiles.source(map), t.getX(), t.getY(), whole, 0, 16);
            assertEquals(Arrays.asList(k[1]), marks);
            assertFalse("the map's cell holds the marker", Arrays.equals(cell, whole));
            final byte[] big = ClassicColonyTiles.enlarge(cell, k[0]);
            final byte[] px = phys.pixels(k[1]);
            final int fw = phys.width(k[1]);
            final BufferedImage img = art.tile(map, t, k[0], null, 0, null);
            int marker = 0;
            for (int y = 0; y < 24; y++) {
                for (int x = 0; x < 24; x++) {
                    final int mx = x - 4, my = y - 4;
                    int v = big[y * 24 + x] & 0xFF;
                    if (mx >= 0 && my >= 0 && mx < Math.min(16, fw)
                        && my < Math.min(16, phys.height(k[1]))) {
                        final int m = px[my * fw + mx] & 0xFF;
                        if (m != ClassicIndexSheet.TRANSPARENT) {
                            v = m;
                            marker++;
                        }
                    }
                    assertEquals(k[1] + " at " + x + "," + y, pal.rgb(0, v),
                                 img.getRGB(x, y) & 0xFFFFFF);
                }
            }
            assertTrue(k[1] + ": " + marker, marker > 20);
            // Beside the colony, its sprite handed over for the overhang
            // (an empty one here): the marker still apart, unscaled (the
            // review of part Y).
            final BufferedImage none = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            final BufferedImage beside = art.tile(map, t, k[0], none,
                                                  ClassicColonyTiles.spriteDx(k[0]), 0, null);
            for (int y = 0; y < 24; y++) {
                for (int x = 0; x < 24; x++) {
                    assertEquals(k[1] + " beside at " + x + "," + y, img.getRGB(x, y),
                                 beside.getRGB(x, y));
                }
            }
        }
        // The colony's own tile with a resource and its sprite: the map's
        // cell, the marker in it, enlarged (I).
        final Tile c = s.colony.getTile();
        c.addResource(new net.sf.freecol.common.model.Resource(s.game, c,
            classic().getResourceType("model.resource.grain")));
        final BufferedImage sprite = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        final byte[] whole = new byte[256];
        comp.composeCell(ClassicColonyTiles.source(map), c.getX(), c.getY(), whole, 0, 16);
        final byte[] big = ClassicColonyTiles.enlarge(whole, ClassicColonyLayout.CENTRE_TILE);
        final BufferedImage img = art.tile(map, c, ClassicColonyLayout.CENTRE_TILE, sprite, 0,
                                           null);
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 24; x++) {
                assertEquals(x + "," + y, pal.rgb(0, big[y * 24 + x] & 0xFF),
                             img.getRGB(x, y) & 0xFFFFFF);
            }
        }
    }

    /** A screen of a test colony. */
    private static ClassicColonyPanel panel(State s) throws Exception {
        ClassicColonyPanel.forgetFigureOrders();
        return new ClassicColonyPanel(ClassicColonyBuildingsTest.client(s.game, s.owner),
            ClassicColonyBuildingsTest.library(), s.colony, null, null, ClassicSlide.SYSTEM,
            false);
    }


    // Golden: clip 020

    /** One rebuilt frame: its state's screen and the original's tile model. */
    private static final class Shot {

        final int frame;
        final ClassicColonyPanel panel;
        final String[] model;

        /** What FreeCol's rules make and what the clip shows ("tile row n"), and the change drawn. */
        final Map<String, int[]> clip = new LinkedHashMap<>();

        /** The white cursor's place in the frame (global: set at the shot's paint). */
        int cursor = ClassicColonyPanel.CURSOR_OFF_TILES;

        Shot(int frame, ClassicColonyPanel panel, String... model) {
            this.frame = frame;
            this.panel = panel;
            this.model = model;
        }

        /** The cursor's place. */
        Shot cursor(int place) {
            this.cursor = place;
            return this;
        }

        /** The clip shows another amount in a tile's row than FreeCol makes. */
        Shot clip(String tile, int row, int n) {
            this.clip.put(tile + " " + row, new int[] { Arrays.asList(
                ClassicColonyLayout.TILE_NAMES).indexOf(tile), row, n });
            return this;
        }
    }

    /** The original's tiles of Base (the ops of dago's `Ov`, V 0 px: this step's models). */
    private static final String NW5 = "icons ICONS.SS.022 33 224 228 232 236 240;fig ICONS.SS.081 238 38",
        N8 = "icons ICONS.SS.027 33 248 250 252 254 256 258 260 262;fig ICONS.SS.086 262 38",
        C63 = "icons ICONS.SS.022 57 249 252 255 258 261 264;icons ICONS.SS.023 70 248 256 264",
        NE5 = "icons ICONS.SS.028 33 273 276 279 282 285;fig ICONS.SS.095 286 38",
        E6 = "icons ICONS.SS.057 57 273 276 279 282 285 288;fig ICONS.SS.089 286 62",
        SW8 = "icons ICONS.SS.027 81 224 226 228 230 232 234 236 238;fig ICONS.SS.086 238 86",
        NE0 = "icons ICONS.SS.023 33 276;spr ICONS.SS.064 272 32;fig ICONS.SS.082 286 38",
        PROV = "icons ICONS.SS.023 33 256;fig ICONS.SS.060 262 38;"
            + "icons ICONS.SS.022 57 249 254 259 264;icons ICONS.SS.023 70 251 260;"
            + "spr ICONS.SS.108 232 36;spr ICONS.SS.108 232 60;spr ICONS.SS.108 232 84;"
            + "spr ICONS.SS.108 256 84;frame 248 32 271 55 10";

    /** Clip 020's frames as rebuilt states. */
    private List<Shot> shots() throws Exception {
        final List<Shot> out = new ArrayList<>();
        // #2260 Base 1539: the farmer on NW (selected), the lumberjack on
        // N, the centre's corn and sugar; the cursor on the centre, white.
        {
            final State s = base(1539);
            final Unit farmer = s.on("expertFarmer", Direction.NW, "grain");
            s.on("expertLumberJack", Direction.N, "lumber");
            s.in("veteranSoldier", "townHall");
            final ClassicColonyPanel p = panel(s);
            p.select(farmer);
            out.add(new Shot(2260, p, NW5, N8, C63, "frame 224 32 247 55 10",
                             "frame 248 56 271 79 15").cursor(ClassicColonyLayout.CENTRE_TILE)
                    .clip("C", 0, 6).clip("C", 1, 3));
        }
        // #48712 Base 1546 (visit 9): the sugar planter set to sugar on the
        // hills (NE) makes nothing (#103: «stays on NO with 0»), selected;
        // the fisherman on E; «Tropen- Wald» over N; the cursor off the
        // tiles.  #50260: the cursor on NE, white over the green.
        for (int f : new int[] { 48712, 50260 }) {
            final State s = base(1546);
            s.on("expertFarmer", Direction.NW, "grain");
            s.on("expertLumberJack", Direction.N, "lumber");
            final Unit planter = s.on("masterSugarPlanter", Direction.NE, "sugar");
            s.tile(Direction.NE).setProductionType(null);
            s.on("expertFisherman", Direction.E, "fish");
            s.colony.invalidateCache();
            final ClassicColonyPanel p = panel(s);
            p.select(planter);
            if (f == 48712) {
                p.hoverAt(258, 40);
                out.add(new Shot(f, p, NW5, N8, NE0, E6, C63, "frame 272 32 295 55 10",
                                 "label 236 32 Tropen- Wald")
                        .clip("E", 0, 6).clip("C", 0, 6).clip("C", 1, 3));
            } else {
                out.add(new Shot(f, p, NW5, N8, NE0, E6, C63, "frame 272 32 295 55 15")
                        .cursor(2).clip("E", 0, 6).clip("C", 0, 6).clip("C", 1, 3));
            }
        }
        // #105689 Base 1562 / #134173 Base 1571: N cleared and plowed
        // («Savanne/Straße/Gepflügt», visit 21), the master sugar planter
        // there (#105689: 12 with «12»; #134173 nobody), the blacksmith on
        // the ore (NE), the fisherman (E), the lumberjack on SW; the farmer
        // selected; the cursor off (#105689) or on N in its off half
        // (#134173: N blinks from #134196).
        for (int f : new int[] { 105689, 134173 }) {
            final State s = base((f == 105689) ? 1562 : 1571)
                .around(Direction.N, "savannah", "road", "plow");
            final Unit farmer = s.on("expertFarmer", Direction.NW, "grain");
            if (f == 105689) s.on("masterSugarPlanter", Direction.N, "sugar");
            s.on("masterBlacksmith", Direction.NE, "ore");
            s.on("expertFisherman", Direction.E, "fish");
            s.on("expertLumberJack", Direction.SW, "lumber");
            final ClassicColonyPanel p = panel(s);
            p.select(farmer);
            final Shot shot;
            if (f == 105689) {
                shot = new Shot(f, p, NW5, "icons ICONS.SS.023 33 250 251 252 253 254 255 256 "
                    + "257 258 259 260 261;label 252 34 12;fig ICONS.SS.082 262 38", NE5, E6, SW8,
                    C63, "frame 224 32 247 55 10").clip("N", 0, 12);
            } else {
                p.blinkPhase(true);
                shot = new Shot(f, p, NW5, NE5, E6, SW8, C63, "frame 224 32 247 55 10")
                    .cursor(1);
            }
            out.add(shot.clip("E", 0, 6).clip("C", 0, 6).clip("C", 1, 3));
        }
        // #14817 / #35057 Provisorische Kolonie 1541 / 1544: its centre
        // «Regen- Wald/Straße» (#35125), the scout on N (rain forest) making
        // sugar, selected; NW, W, SW and S native land (the totems).
        for (int[] k : new int[][] { { 14817, 1541 }, { 35057, 1544 } }) {
            final State s = new State("Provisorische Kolonie", k[1], "rainForest", "road")
                .around(Direction.N, "rainForest");
            final Unit scout = s.on("seasonedScout", Direction.N, "sugar");
            final Player tupi = s.game.getPlayerByNationId("model.nation.tupi");
            for (Direction d : new Direction[] { Direction.NW, Direction.W, Direction.SW,
                                                 Direction.S }) {
                s.colony.getTile().getNeighbourOrNull(d).setOwner(tupi);
            }
            final ClassicColonyPanel p = panel(s);
            p.select(scout);
            out.add(new Shot(k[0], p, PROV.split(";")).clip("C", 0, 4).clip("C", 1, 2));
        }
        // #123003 Silver City 1566 (founding): the scout on silver (W),
        // selected; no totem (after Peter Minuit); the cursor on his figure.
        // Its terrain is not named in the clip (I: W mountains, the centre a
        // mixed forest).
        {
            final State s = new State("Silver City", 1566, "mixedForest", "road")
                .around(Direction.W, "mountains");
            final Unit scout = s.on("seasonedScout", Direction.W, "silver");
            final ClassicColonyPanel p = panel(s);
            p.select(scout);
            out.add(new Shot(123003, p, "icons ICONS.SS.029 57 224 231 238",
                             "fig ICONS.SS.060 238 62", "icons ICONS.SS.022 57 249 254 259 264",
                             "icons ICONS.SS.026 70 248 255 262", "frame 224 56 247 79 10")
                    .clip("W", 0, 3).clip("C", 0, 4).clip("C", 1, 3));
        }
        return out;
    }

    /**
     * A model with a tile row's amount changed (the clip's, where FreeCol's
     * rules make another), its row laid out again.
     */
    private static UnaryOperator<ClassicColonyTiles.Model> withClip(Shot s,
            ClassicColonyBuildings.Assets a) {
        return m -> {
            final ClassicColonyTiles.Cell[] cells = new ClassicColonyTiles.Cell[9];
            for (int i = 0; i < 9; i++) cells[i] = m.cell(i);
            for (int[] k : s.clip.values()) {
                final ClassicColonyTiles.Cell c = cells[k[0]];
                if (c == null || k[1] >= c.rows.size()) continue;
                final List<ClassicColonyTiles.TileRow> rows = new ArrayList<>(c.rows);
                final ClassicColonyTiles.TileRow old = rows.get(k[1]);
                final BufferedImage icon = a.icon(old.icon);
                rows.set(k[1], new ClassicColonyTiles.TileRow(old.icon,
                    ClassicColonyLayout.tileRow(c.bounds().x, old.row.y, k[2],
                                                (icon == null) ? 8 : icon.getWidth())));
                cells[k[0]] = new ClassicColonyTiles.Cell(c.index, c.tile, c.colonyTile, rows,
                    c.zero, c.worker, c.totem, c.occupier, c.label);
            }
            return new ClassicColonyTiles.Model(cells);
        };
    }

    /** The 3x3 map's inside, x 224-295, y 32-103. */
    private static final Rectangle TILES = new Rectangle(224, 32, 72, 72);

    /**
     * What our screen draws on the terrain: the pixels of the tile area
     * that are not the terrain when the terrain is two plain colours in
     * turn.
     */
    private static ClassicColonyGolden.Mask ours(ClassicColonyPanel p) {
        final int[] fills = { 0x010203, 0xFDFEFC };
        final int[][] px = new int[2][];
        for (int k = 0; k < 2; k++) {
            final int fill = fills[k];
            p.setGround((g, c) -> {
                g.setColor(new java.awt.Color(fill));
                final Rectangle r = c.bounds();
                g.fillRect(r.x, r.y, r.width, r.height);
                return false;
            });
            px[k] = ClassicColonyGolden.pixels(p.canvas());
        }
        p.setGround(null);
        final ClassicColonyGolden.Mask m = new ClassicColonyGolden.Mask();
        for (int y = TILES.y; y < TILES.y + TILES.height; y++) {
            for (int x = TILES.x; x < TILES.x + TILES.width; x++) {
                final int i = y * 320 + x;
                if (px[0][i] != fills[0] || px[1][i] != fills[1]) m.set(x, y);
            }
        }
        return m;
    }

    /** The original's model as a mask (its ops, the ink after a frame left out). */
    private static ClassicColonyGolden.Mask theirs(ClassicColonyGolden gold, String[] model)
        throws Exception {
        final List<String> lines = new ArrayList<>();
        for (String op : model) lines.addAll(Arrays.asList(op.split(";")));
        return gold.content("tiles", lines, TILES, l -> true);
    }

    /**
     * Golden (M4.4 acceptance): the 3x3 map of eight frames of clip 020
     * rebuilt in a test game, compared with the frame on everything the
     * original or ours draws over the terrain (the union of the original's
     * model, verified with dago's `Ov` at 0 px, and our drawing), exact
     * RGB, the arrow left out: the rows (#2260 NW corn 5 at 224/4, N
     * lumber 8 at 248/2, the centre's corn 6 and sugar 3; #134173 NE ore 5,
     * E fish 6, SW lumber 8; #105689 N sugar 12 with «12»; #14817 / #35057
     * the scout's 1 sugar, corn 4, sugar 2; #123003 silver 3, corn 4, furs
     * 3), the workers with their black shadows, the sign (#48712, #50260),
     * the four totems (#14817, #35057), the green frame of the selected
     * one's tile, the white cursor (#2260 on the centre, #50260 over the
     * green), «Tropen- Wald» (#48712): 0 differing pixels each, with the
     * clip's amounts where FreeCol's rules make others (named in the
     * output: the centre tiles, the expert fisherman on the ocean, the
     * master sugar planter on the plowed savannah, the scout on silver).
     */
    public void testTheTilesAgainstClip020() throws Exception {
        final ClassicColonyGolden gold = ClassicColonyGolden.open(getName());
        if (gold == null) return;
        final ClassicColonyBuildings.Assets a = ClassicColonyBuildings.Assets.load(gold.pack);
        final StringBuilder report = new StringBuilder();
        final StringBuilder fails = new StringBuilder();
        for (Shot s : shots()) {
            ClassicColonyPanel.setTileCursor(s.cursor);
            final BufferedImage frame = gold.frame(s.frame);
            final int[] theirs = ClassicColonyGolden.pixels(frame);
            final ClassicColonyGolden.Mask arrow = gold.pointer(frame);
            final ClassicColonyGolden.Mask model = theirs(gold, s.model);
            // FreeCol's numbers first, then the clip's.
            s.panel.canvas();
            final String freeCol = rows(s.panel.tiles());
            final ClassicColonyGolden.Diff byRules = compare(s, gold, theirs, arrow, model,
                                                             "FreeCol's amounts");
            s.panel.setTilesAdjust(withClip(s, a));
            s.panel.canvas();
            final String clip = rows(s.panel.tiles());
            final ClassicColonyGolden.Diff d = compare(s, gold, theirs, arrow, model,
                                                       "#" + s.frame);
            dump(s.panel.canvas(), frame, s.frame);
            int terrain = 0, equal = 0;
            final int[] ourPx = ClassicColonyGolden.pixels(s.panel.canvas());
            final ClassicColonyGolden.Mask over = ours(s.panel);
            for (int y = TILES.y; y < TILES.y + TILES.height; y++) {
                for (int x = TILES.x; x < TILES.x + TILES.width; x++) {
                    if (over.get(x, y) || model.get(x, y) || arrow.get(x, y)) continue;
                    terrain++;
                    if (ourPx[y * 320 + x] == theirs[y * 320 + x]) equal++;
                }
            }
            report.append("  ").append(d).append('\n')
                .append(String.format("      terrain (ours, not compared) %d px, of them equal"
                    + " to the frame %d (%.0f %%)%n", terrain, equal, 100.0 * equal
                    / Math.max(1, terrain)))
                .append("      rows FreeCol: ").append(freeCol).append('\n')
                .append("      rows clip:    ").append(clip).append("  (FreeCol's amounts: ")
                .append(byRules.differing).append(" px differ)\n");
            if (d.differing != 0) fails.append(' ').append(d.name).append(": ").append(d.differing);
            assertTrue(d.name, d.compared > 600);
        }
        System.err.println(getName() + ":\n" + report);
        assertEquals("differing pixels:" + fails + "\n" + report, 0, fails.length());
    }

    /** Our canvas against the frame on the union of the two masks, the arrow out. */
    private static ClassicColonyGolden.Diff compare(Shot s, ClassicColonyGolden gold,
                                                    int[] theirs, ClassicColonyGolden.Mask arrow,
                                                    ClassicColonyGolden.Mask model, String name) {
        final ClassicColonyGolden.Mask union = ours(s.panel);
        for (int y = TILES.y; y < TILES.y + TILES.height; y++) {
            for (int x = TILES.x; x < TILES.x + TILES.width; x++) {
                if (model.get(x, y)) union.set(x, y);
            }
        }
        // compare() leaves out "later" pixels: the complement of the union.
        final ClassicColonyGolden.Mask rest = new ClassicColonyGolden.Mask();
        for (int y = TILES.y; y < TILES.y + TILES.height; y++) {
            for (int x = TILES.x; x < TILES.x + TILES.width; x++) {
                if (!union.get(x, y)) rest.set(x, y);
            }
        }
        return ClassicColonyGolden.compare("tiles " + name, ClassicColonyGolden.pixels(
            s.panel.canvas()), theirs, TILES, arrow, rest);
    }

    /**
     * The frames of the enlargement (part Y5, prep y §1 and §4): a colony
     * frame of clip 020, a map frame of the same colony and the 3x3's
     * 48x48 block on it (its NW corner), then per clean tile (nothing over
     * it in either frame) the pixels it misses: one source pixel at an
     * outer coast tile's corner each, where the map frame shows land and
     * the 3x3 water (I, named in {@link ClassicColonyTiles#enlarge}).
     */
    private static final int[][] ENLARGED = {
        { 2260, 1700, 80, 104,    2, 0,   3, 0,   5, 1,   6, 0 },
        { 123003, 122700, 96, 56,    0, 4,   1, 0,   2, 0,   6, 0,   8, 0 },
        { 123003, 123300, 96, 56,    5, 0 },
        { 14817, 14300, 96, 88,    8, 1 },
        { 14817, 15500, 80, 88,    5, 0 } };

    /** A frame's palette indices, [y][x]. */
    private static int[][] indices(BufferedImage frame) {
        assertTrue("an indexed frame",
                   frame.getColorModel() instanceof java.awt.image.IndexColorModel);
        final int[][] p = new int[frame.getHeight()][frame.getWidth()];
        for (int y = 0; y < p.length; y++) {
            for (int x = 0; x < p[y].length; x++) p[y][x] = frame.getRaster().getSample(x, y, 0);
        }
        return p;
    }

    /**
     * Golden (part Y5, the 3x3's terrain): the original's own map of a
     * colony (its 16-px cells in a map frame) enlarged by {@link
     * ClassicColonyTiles#enlarge} is the original's 3x3 of that colony,
     * index for index, on the twelve clean tiles of Base #2260, Silver
     * City #123003 and Provisorische Kolonie #14817 (ocean, coast, forest,
     * hills, mountains, plains): 6906 of 6912 px, nine tiles all 576, the
     * misses named in {@link #ENLARGED}; before part Y5 (floor((2d +
     * 1)/3) without shades) about a quarter.  And the fish of #14817's NE
     * (PHYS0.SS 096) unscaled at its tile + (4,4) with its own indices, 70
     * of 70 px.
     */
    public void testTheEnlargementAgainstClip020() throws Exception {
        final ClassicColonyGolden gold = ClassicColonyGolden.open(getName());
        if (gold == null) return;
        final StringBuilder report = new StringBuilder();
        int all = 0, equal = 0, before = 0;
        for (int[] k : ENLARGED) {
            final int[][] colony = indices(gold.frame(k[0])), map = indices(gold.frame(k[1]));
            for (int i = 4; i < k.length; i += 2) {
                final int t = k[i], c = t % 3, r = t / 3;
                final byte[] cell = new byte[256];
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        cell[y * 16 + x] = (byte) map[k[3] + 16 * r + y][k[2] + 16 * c + x];
                    }
                }
                final byte[] big = ClassicColonyTiles.enlarge(cell, t);
                int miss = 0, old = 0;
                for (int y = 0; y < 24; y++) {
                    for (int x = 0; x < 24; x++) {
                        final int want = colony[32 + 24 * r + y][224 + 24 * c + x];
                        if ((big[y * 24 + x] & 0xFF) != want) miss++;
                        if ((cell[((2 * y + 1) / 3) * 16 + (2 * x + 1) / 3] & 0xFF) == want) old++;
                    }
                }
                all += 576;
                equal += 576 - miss;
                before += old;
                final String name = "#" + k[0] + " / #" + k[1] + " "
                    + ClassicColonyLayout.TILE_NAMES[t];
                report.append(String.format("  %-22s %3d of 576 (before %3d)%n", name, 576 - miss,
                                            old));
                assertEquals(name + " misses", k[i + 1], miss);
            }
        }
        // The fish: PHYS0.SS 096 at NE + (4,4), unscaled, unshaded.
        final ClassicIndexSheet phys = gold.pack.indexSheet(ClassicPackFiles.PHYS0_SS);
        final int[][] prov = indices(gold.frame(14817));
        final byte[] fish = phys.pixels(96);
        int opaque = 0, same = 0;
        for (int y = 0; y < phys.height(96); y++) {
            for (int x = 0; x < phys.width(96); x++) {
                final int v = fish[y * phys.width(96) + x] & 0xFF;
                if (v == ClassicIndexSheet.TRANSPARENT) continue;
                opaque++;
                if (prov[32 + ClassicColonyLayout.TILE_MARKER_DY + y]
                    [224 + 48 + ClassicColonyLayout.TILE_MARKER_DX + x] == v) same++;
            }
        }
        report.append(String.format("  all %d of %d (%.2f %%), before %d (%.1f %%); the fish %d of"
            + " %d%n", equal, all, 100.0 * equal / all, before, 100.0 * before / all, same,
            opaque));
        System.err.println(getName() + ":\n" + report);
        assertEquals(6906, equal);
        assertEquals(70, opaque);
        assertEquals(70, same);
    }

    /**
     * Golden (the review of part Y, fidelity): the colony's huts overhang
     * into the 3x3's E as on the map.  ICONS.SS.003 is 21 px wide, drawn
     * at cell x - 2, so its columns 18-19 fall into E's columns 0-1 (V
     * clip 020 #1700, Base's E x 112-113, y 121-127); the original's 3x3
     * of Base #2260 shows them enlarged and shaded at E's x 0-2.  Ours,
     * the pack's sprite pasted into E by {@link ClassicColonyTiles.Art#tile}
     * at -16 and enlarged at E's slot, equals the frame on every pixel
     * that comes from the sprite (before: none of them, the roof cut off
     * at the centre's edge).
     */
    public void testTheHutsOverhangIntoTheEastAsClip020() throws Exception {
        final ClassicColonyGolden gold = ClassicColonyGolden.open(getName());
        if (gold == null) return;
        final ClassicColonyTiles.Art art = ClassicColonyTiles.Art.load(gold.pack, null);
        final ClassicIndexSheet icons = gold.pack.indexSheet(ClassicColonyBand.ICONS_SS);
        if (!art.indexed() || icons == null) {
            System.err.println(getName() + ": no index sheets, skipped");
            return;
        }
        final int f = 3, w = icons.width(f), h = icons.height(f);
        assertEquals("the huts' width", 21, w);
        assertTrue(h <= 16);
        final byte[] px = icons.pixels(f);
        final BufferedImage huts = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                final int v = px[y * w + x] & 0xFF;
                if (v != ClassicIndexSheet.TRANSPARENT) {
                    huts.setRGB(x, y, 0xFF000000 | art.palette().rgb(0, v));
                }
            }
        }
        final State s = base(1539);
        final Tile c = s.colony.getTile();
        final int e = ClassicColonyLayout.CENTRE_TILE + 1;
        final BufferedImage ours = art.tile(c.getMap(), c, e, huts,
                                            ClassicColonyTiles.spriteDx(e), 0, null);
        final BufferedImage frame = gold.frame(2260);
        final java.awt.image.IndexColorModel cm
            = (java.awt.image.IndexColorModel) frame.getColorModel();
        final int ox = ClassicHud.settlementOffset(w) + ClassicColonyTiles.spriteDx(e);
        int sprite = 0, same = 0;
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 24; x++) {
                final int sx = ClassicColonyTiles.enlargedSourceX(x) - ox;
                final int sy = ClassicColonyTiles.enlargedSourceY(y);
                if (sx < 0 || sx >= w || sy >= h
                    || (px[sy * w + sx] & 0xFF) == ClassicIndexSheet.TRANSPARENT) continue;
                sprite++;
                final int want = cm.getRGB(frame.getRaster().getSample(224 + 48 + x, 32 + 24 + y, 0));
                if ((ours.getRGB(x, y) & 0xFFFFFF) == (want & 0xFFFFFF)) same++;
            }
        }
        System.err.println(getName() + ": the huts in E " + same + " of " + sprite);
        assertTrue("the huts reach E: " + sprite, sprite >= 20);
        assertEquals(sprite, same);
    }


    // Golden: dago's colonies

    /**
     * Golden (dago §3, the painter alone): dago's four tile models
     * ({@code ov_base_1000_tiles}, {@code ov_base_1356_tiles},
     * {@code ov_furcity_9560_tiles}, {@code ov_furtown_11360_tiles}; 29
     * rows, 24 workers, the frames) laid out by {@link
     * ClassicColonyLayout#tileRow} from their amounts and drawn by {@link
     * ClassicColonyTiles#paint} against dago's frames, exact RGB on the
     * union of both: 0 differing pixels (Base #1000's centre carries the
     * labels «8» and «5» that dago's analysis could not explain (its Q3,
     * gone at #1356): left out there).
     */
    public void testTheTilesAgainstDago() throws Exception {
        final ClassicColonyGolden gold = ClassicColonyGolden.open(getName());
        if (gold == null) return;
        final File clip = new File(gold.recordings, "dago-colony");
        final File models = new File(gold.recordings, "dago-colony-analysis/support");
        if (!clip.isDirectory() || !models.isDirectory()) {
            System.err.println(getName() + ": no dago clip, skipped");
            return;
        }
        final ClassicColonyBuildings.Assets a = ClassicColonyBuildings.Assets.load(gold.pack);
        final ClassicColonyChrome.Assets chrome = ClassicColonyChrome.Assets.load(gold.pack);
        final StringBuilder report = new StringBuilder();
        final StringBuilder fails = new StringBuilder();
        // The frames' green and white tiles (dago §3.1, V: Base N green,
        // the centre white at #1356; Fur City E green; Fur Town N white, NE
        // green).
        final Object[][] frames = {
            { 1000, "ov_base_1000_tiles.txt", 1, -1 }, { 1356, "ov_base_1356_tiles.txt", 1, 4 },
            { 9560, "ov_furcity_9560_tiles.txt", 5, -1 },
            { 11360, "ov_furtown_11360_tiles.txt", 2, 1 },
        };
        for (Object[] f : frames) {
            final int n = (Integer) f[0];
            final File modelFile = new File(models, (String) f[1]);
            final List<String> lines = java.nio.file.Files.readAllLines(modelFile.toPath(),
                java.nio.charset.StandardCharsets.UTF_8);
            final ClassicColonyTiles.Model m = dagoModel(lines, a, n == 1000);
            final BufferedImage frame = javax.imageio.ImageIO.read(
                ClassicTerrainGoldenTest.frameFile(clip, n));
            final int[] theirs = ClassicColonyGolden.pixels(frame);
            final ClassicColonyGolden.Mask model = gold.content(modelFile, TILES,
                l -> !(n == 1000 && l.startsWith("label 251 ")));
            // Ours on a background that is no colour of the sprites.
            final int[][] px = new int[2][];
            final int[] fills = { 0x010203, 0xFDFEFC };
            for (int k = 0; k < 2; k++) {
                final BufferedImage img = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
                final Graphics2D g = img.createGraphics();
                g.setColor(new java.awt.Color(fills[k]));
                g.fillRect(0, 0, 320, 200);
                ClassicColonyTiles.paint(g, chrome, a, m, null, null, (Integer) f[2],
                                         (Integer) f[3], true);
                g.dispose();
                px[k] = ClassicColonyGolden.pixels(img);
            }
            final ClassicColonyGolden.Mask rest = new ClassicColonyGolden.Mask();
            int drawn = 0;
            for (int y = TILES.y; y < TILES.y + TILES.height; y++) {
                for (int x = TILES.x; x < TILES.x + TILES.width; x++) {
                    final int i = y * 320 + x;
                    final boolean ours = px[0][i] != fills[0] || px[1][i] != fills[1];
                    if (ours) drawn++;
                    if (!ours && !model.get(x, y)) rest.set(x, y);
                }
            }
            if (n == 1000) {
                // Base #1000's centre labels «8» (251,58) and «5» (251,71),
                // which the rule does not give (dago Q3): left out.
                rest.rect(251, 58, 251 + chrome.tiny.stringWidth("8"), 64);
                rest.rect(251, 71, 251 + chrome.tiny.stringWidth("5"), 77);
            }
            final ClassicColonyGolden.Diff d = ClassicColonyGolden.compare("dago #" + n, px[0],
                theirs, TILES, gold.pointer(frame), rest);
            report.append("  ").append(d).append(" (ours drew ").append(drawn).append(" px; ")
                .append(m).append(")\n");
            if (d.differing != 0) fails.append(' ').append(d.name).append(": ").append(d.differing);
            assertTrue(d.name, d.compared > 1500);
        }
        System.err.println(getName() + ":\n" + report);
        assertEquals("differing pixels:" + fails + "\n" + report, 0, fails.length());
    }

    /**
     * A model of a dago tile model: each "icons" line a row of its count
     * (its label's number when it has one), laid out by the rule; each
     * "fig" a worker; the frames are the painter's (its arguments).
     */
    private static ClassicColonyTiles.Model dagoModel(List<String> lines,
            ClassicColonyBuildings.Assets a, boolean anomaly) {
        final Map<Integer, List<ClassicColonyTiles.TileRow>> rows = new LinkedHashMap<>();
        final Map<Integer, ClassicColonyBuildings.Figure> figs = new LinkedHashMap<>();
        for (int k = 0; k < lines.size(); k++) {
            final String[] q = lines.get(k).trim().split("\\s+");
            if (q[0].equals("icons")) {
                final int icon = Integer.parseInt(q[1].substring(q[1].lastIndexOf('.') + 1));
                final int y = Integer.parseInt(q[2]), x0 = Integer.parseInt(q[3]);
                final int place = ClassicColonyLayout.tileAt(x0 + 3, y);
                int n = q.length - 3;
                final String[] next = (k + 1 < lines.size())
                    ? lines.get(k + 1).trim().split("\\s+") : new String[0];
                if (next.length == 4 && next[0].equals("label")
                    && !(anomaly && place == ClassicColonyLayout.CENTRE_TILE)) {
                    n = Integer.parseInt(next[3]);
                }
                final BufferedImage img = a.icon(icon);
                rows.computeIfAbsent(place, p -> new ArrayList<>()).add(
                    new ClassicColonyTiles.TileRow(icon, ClassicColonyLayout.tileRow(
                        ClassicColonyLayout.tileCell(place).x, y, n,
                        (img == null) ? 8 : img.getWidth())));
            } else if (q[0].equals("fig")) {
                final int x = Integer.parseInt(q[2]), y = Integer.parseInt(q[3]);
                final int place = ClassicColonyLayout.tileAt(x, y);
                final String key = ClassicPackFiles.ssKey(q[1]);
                final BufferedImage img = a.image(key);
                figs.put(place, new ClassicColonyBuildings.Figure(null, key,
                    Integer.parseInt(q[1].substring(q[1].lastIndexOf('.') + 1)),
                    ClassicColonyLayout.tileCell(place).x + ClassicColonyLayout.TILE_WORKER_DX,
                    ClassicColonyLayout.tileCell(place).y + ClassicColonyLayout.TILE_WORKER_DY,
                    (img == null) ? 8 : img.getWidth()));
                assertEquals("the figure at + (14,6)", x, figs.get(place).x);
                assertEquals("the figure at + (14,6)", y, figs.get(place).y);
            }
        }
        final ClassicColonyTiles.Cell[] cells = new ClassicColonyTiles.Cell[9];
        for (int i = 0; i < 9; i++) {
            cells[i] = new ClassicColonyTiles.Cell(i, null, null,
                rows.getOrDefault(i, new ArrayList<>()), -1, figs.get(i), false, null, "");
        }
        return new ClassicColonyTiles.Model(cells);
    }

    /**
     * With {@code -Dclassic.colony.dump=<folder>}: our canvas and the
     * frame side by side, 3x (for the report's eyes; nothing is asserted).
     */
    private static void dump(BufferedImage ours, BufferedImage frame, int n)
        throws java.io.IOException {
        final String d = System.getProperty("classic.colony.dump");
        if (d == null || d.isBlank()) return;
        final BufferedImage out = new BufferedImage(2 * 960 + 12, 600,
                                                    BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = out.createGraphics();
        g.drawImage(ours, 0, 0, 960, 600, null);
        g.drawImage(frame, 972, 0, 960, 600, null);
        g.dispose();
        javax.imageio.ImageIO.write(out, "png", new File(d, "s3-ours-vs-" + n + ".png"));
    }
}
