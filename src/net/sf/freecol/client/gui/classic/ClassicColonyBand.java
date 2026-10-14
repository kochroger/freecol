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
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.WeakHashMap;

import net.sf.freecol.common.model.AbstractGoods;
import net.sf.freecol.common.model.Building;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.ProductionInfo;
import net.sf.freecol.common.model.Specification;


/**
 * The colony screen's <b>band, left panel</b> in the original's look (build
 * spec M4.5, {@code playthrough-3-analysis/11-spec-M4-colony.md}; L §5,
 * dago {@code 01-layout.md} §4; clip 020 and dago at 0 px): the Sons of
 * Liberty line, the flag ICONS.SS.123 at (2,132) and «11% (1)» at (15,133)
 * when the colony has members, the crown 124 at (104,132) and its words
 * ending at x 104 when it has Tories; and the production row at y 163,
 * the food used (its fish part, then its corn part), the food stored or
 * the shortfall's crossed corn, the crosses, the bells, at one pitch for
 * the whole row or, when even pitch 1 does not fit, at one scale with
 * count labels ({@link ClassicColonyLayout#bandRow}).  The figure row
 * between them is {@link ClassicColonyBuildings#figureRow}'s (M4.3).
 *
 * <p>A model of what is drawn ({@link #model}), then one painter
 * ({@link #paint}), as {@link ClassicColonyTiles}.  The numbers are
 * FreeCol's (the colony's food, crosses and bells as its rules make them);
 * the counts in brackets the original's rounding.
 *
 * <p>The flag's three canton pixels are palette indices 122/123 of the
 * water's cycle (ICONS.SS's only cycling pixels; V clip 020: they change
 * with the frame's palette phase, #2260 phase 3, #18954, #123003 phase 0):
 * it is drawn from the pack's index sheet through the game palette at the
 * map's phase, as the 3x3 map's water.
 */
final class ClassicColonyBand {

    private ClassicColonyBand() {}   // static helpers only

    /** FreeCol's goods the row shows. */
    static final String FISH = "model.goods.fish", CROSSES = "model.goods.crosses",
        BELLS = "model.goods.bells";

    /** The production row's four groups, in their order. */
    static final int USED = 0, STORED = 1, CROSS = 2, BELL = 3;


    /** What the left panel shows of a colony. */
    static final class Model {

        /** The SoL and Tory percentages and the counts in brackets. */
        final int sol, tory, rebels, tories;

        /** The colonists the counts are of. */
        final int population;

        /**
         * The food the colonists (and the horses) use, from fish and from
         * the rest; the food stored this turn, or the shortfall (crossed);
         * the crosses; the bells.
         */
        final int fishUsed, cornUsed, stored, shortfall, crosses, bells;

        /** The production row laid out. */
        final ClassicColonyLayout.BandRow row;

        /** The icons' widths (corn, fish, crosses, bells), for a re-layout. */
        private final int[] widths;

        Model(int sol, int population, int fishUsed, int cornUsed, int stored,
              int shortfall, int crosses, int bells, int[] widths) {
            this.sol = Math.max(0, Math.min(100, sol));
            this.tory = 100 - this.sol;
            this.rebels = ClassicColonyLayout.memberCount(this.sol, population);
            this.tories = ClassicColonyLayout.memberCount(this.tory, population);
            this.fishUsed = Math.max(0, fishUsed);
            this.cornUsed = Math.max(0, cornUsed);
            this.stored = Math.max(0, stored);
            this.shortfall = Math.max(0, shortfall);
            this.crosses = Math.max(0, crosses);
            this.bells = Math.max(0, bells);
            this.widths = widths.clone();
            final int corn = widths[0], fish = widths[1], cross = widths[2], bell = widths[3];
            // The stored food and the shortfall share the group's place
            // (V #18955: the two crossed corn after the gap where #2260's
            // stored corn stands); only one is ever above 0.
            this.row = ClassicColonyLayout.bandRow(new int[][] {
                    { this.fishUsed, this.cornUsed },
                    { this.stored + this.shortfall },
                    { this.crosses },
                    { this.bells } },
                new int[][] { { fish, corn }, { corn }, { cross }, { bell } });
            this.population = Math.max(0, population);
        }

        /**
         * The same with other amounts (a test's: the clip's, where FreeCol's
         * rules make others), laid out again.
         *
         * @param fishUsed Food used from fish.
         * @param cornUsed Food used from the rest.
         * @param stored Food stored.
         * @param shortfall Food missing.
         * @param crosses Crosses.
         * @param bells Bells.
         * @return The model.
         */
        Model with(int fishUsed, int cornUsed, int stored, int shortfall, int crosses,
                   int bells) {
            return new Model(this.sol, this.population, fishUsed, cornUsed, stored,
                             shortfall, crosses, bells, this.widths);
        }

        /**
         * @param sol Another SoL percentage.
         * @return The same with it.
         */
        Model withSol(int sol) {
            return new Model(sol, this.population, this.fishUsed, this.cornUsed, this.stored,
                             this.shortfall, this.crosses, this.bells, this.widths);
        }

        @Override
        public String toString() {
            return "sol=" + ((this.sol > 0)
                    ? ClassicColonyLayout.memberText(this.sol, this.rebels) : "-")
                + " tory=" + ((this.tory > 0)
                    ? ClassicColonyLayout.memberText(this.tory, this.tories) : "-")
                + " food=" + this.fishUsed + "f+" + this.cornUsed + "c"
                + ((this.shortfall > 0) ? " short=" + this.shortfall : " stored=" + this.stored)
                + " crosses=" + this.crosses + " bells=" + this.bells + " row " + this.row;
        }
    }

    /**
     * The left panel's model of a colony now (FreeCol's numbers): the SoL
     * percentage; the food made (its tiles: fish and the rest), what its
     * colonists eat (2 each) and its horses take of the surplus (FreeCol's
     * country and stables; the original's horses take theirs too, I: Prov.
     * #14817 uses 3 for 1 colonist), the rest stored; when the food made
     * is less than the colonists eat, all of it used and the rest the
     * shortfall (V #18955); the crosses and bells its buildings make.
     *
     * @param colony The colony.
     * @param a The sprites (the icons' widths).
     * @return The model.
     */
    static Model model(Colony colony, ClassicColonyBuildings.Assets a) {
        final Specification spec = colony.getSpecification();
        final GoodsType fishType = spec.getGoodsType(FISH);
        final int made = Math.max(0, colony.getFoodProduction());
        final int fish = Math.min(made, (fishType == null) ? 0
                                  : Math.max(0, colony.getTotalProductionOf(fishType)));
        final int eaten = Math.max(0, colony.getFoodConsumption());
        int used, stored = 0, shortfall = 0;
        if (made >= eaten) {
            int horses = 0;
            for (Building b : colony.getBuildings()) {
                final ProductionInfo pi = colony.getProductionInfo(b);
                if (pi == null) continue;
                for (AbstractGoods ag : pi.getConsumption()) {
                    if (ag.getType().isFoodType()) horses += ag.getAmount();
                }
            }
            used = eaten + Math.max(0, Math.min(horses, made - eaten));
            stored = made - used;
        } else {
            used = made;
            shortfall = eaten - made;
        }
        final int fishUsed = Math.min(fish, used);
        final GoodsType crossType = spec.getGoodsType(CROSSES);
        final GoodsType bellType = spec.getGoodsType(BELLS);
        final int crosses = (crossType == null) ? 0 : colony.getTotalProductionOf(crossType);
        final int bells = (bellType == null) ? 0 : colony.getTotalProductionOf(bellType);
        return new Model(colony.getSonsOfLiberty(), colony.getUnitCount(), fishUsed,
                         used - fishUsed, stored, shortfall, crosses, bells, widths(a));
    }

    /** The widths of corn, fish, crosses and bells (the pack's; 8 without it). */
    static int[] widths(ClassicColonyBuildings.Assets a) {
        final int[] icons = { ClassicColonyLayout.ICON_CORN, ClassicColonyLayout.ICON_FISH,
                              ClassicColonyLayout.ICON_CROSSES, ClassicColonyLayout.ICON_BELLS };
        final int[] out = new int[icons.length];
        for (int i = 0; i < icons.length; i++) {
            final BufferedImage img = (a == null) ? null : a.icon(icons[i]);
            out[i] = (img == null) ? 8 : img.getWidth();
        }
        return out;
    }


    // The flag through the game palette

    /** Per pack: its game palette and the flag at each phase (small). */
    private static final Map<ClassicPackFiles, Object[]> FLAGS = new WeakHashMap<>();

    /**
     * The SoL flag ICONS.SS.123 at a phase of the water's cycle: its
     * index sheet's pixels through the game palette (V: its canton's 122,
     * 123 cycle); the pack's PNG without the sheet or the palette.
     *
     * @param pack The pack, or null.
     * @param a The sprites (the PNG).
     * @param phase The phase.
     * @return The flag, or null.
     */
    static BufferedImage flag(ClassicPackFiles pack, ClassicColonyBuildings.Assets a,
                              int phase) {
        final int f = ClassicColonyLayout.ICON_SOL;
        if (pack == null) return (a == null) ? null : a.icon(f);
        synchronized (FLAGS) {
            Object[] e = FLAGS.get(pack);
            if (e == null) {
                final ClassicGamePalette pal = ClassicGamePalette.of(pack);
                e = new Object[] { pal, pack.indexSheet(ICONS_SS),
                                   (pal == null) ? null : new BufferedImage[pal.phaseCount()] };
                FLAGS.put(pack, e);
            }
            final ClassicGamePalette pal = (ClassicGamePalette) e[0];
            final ClassicIndexSheet sheet = (ClassicIndexSheet) e[1];
            final BufferedImage[] cache = (BufferedImage[]) e[2];
            if (pal == null || sheet == null || f >= sheet.size()) {
                return (a == null) ? null : a.icon(f);
            }
            final int p = pal.normalise(phase);
            if (cache[p] == null) {
                final int w = sheet.width(f), h = sheet.height(f);
                final BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        final int i = sheet.index(f, x, y);
                        if (i == ClassicIndexSheet.TRANSPARENT) continue;
                        img.setRGB(x, y, 0xFF000000 | pal.rgb(p, i));
                    }
                }
                cache[p] = img;
            }
            return cache[p];
        }
    }

    /** The icons' SS file. */
    static final String ICONS_SS = "ICONS.SS";


    // Painting

    /**
     * The left panel's SoL line and production row as the model says (the
     * band picture is the chrome's, {@link ClassicColonyChrome#paintBand};
     * the figure row {@link ClassicColonyBuildings#paintFigureRow}'s): the
     * flag and «p% (n)» in white when the colony has members, the crown and
     * its words ending at x 104 when it has Tories; the row's icons, a
     * shortfall's corn each under the red X 055 (V #18955), the groups'
     * count labels (when «Beschriftung an Waren und Terrain» is on: I, the
     * option's meaning in the original).
     *
     * @param g The canvas.
     * @param c The chrome's assets (the font, the inks).
     * @param a The sprites.
     * @param flag The SoL flag at the water's phase ({@link #flag}), or null.
     * @param m The model.
     * @param labels Whether the groups carry their count labels.
     * @return Whether the flag was drawn (its canton cycles).
     */
    static boolean paint(Graphics2D g, ClassicColonyChrome.Assets c,
                         ClassicColonyBuildings.Assets a, BufferedImage flag, Model m,
                         boolean labels) {
        boolean flagDrawn = false;
        if (m.sol > 0) {
            if (flag != null) {
                g.drawImage(flag, ClassicColonyLayout.SOL_ICON_X,
                            ClassicColonyLayout.SOL_ICON_Y, null);
                flagDrawn = true;
            }
            ClassicColonyChrome.text(g, c, ClassicColonyLayout.memberText(m.sol, m.rebels),
                ClassicColonyLayout.SOL_TEXT_X, ClassicColonyLayout.SOL_Y,
                ClassicColonyLayout.INK_WHITE);
        }
        if (m.tory > 0) {
            final BufferedImage crown = a.icon(ClassicColonyLayout.ICON_TORY);
            if (crown != null) {
                g.drawImage(crown, ClassicColonyLayout.TORY_ICON_X,
                            ClassicColonyLayout.TORY_ICON_Y, null);
            }
            final String s = ClassicColonyLayout.memberText(m.tory, m.tories);
            ClassicColonyChrome.text(g, c, s,
                ClassicColonyLayout.toryTextX(ClassicColonyChrome.textWidth(c, s)),
                ClassicColonyLayout.SOL_Y, ClassicColonyLayout.INK_WHITE);
        }
        final ClassicColonyLayout.BandRow r = m.row;
        final int[][] icons = {
            { ClassicColonyLayout.ICON_FISH, ClassicColonyLayout.ICON_CORN },
            { ClassicColonyLayout.ICON_CORN },
            { ClassicColonyLayout.ICON_CROSSES },
            { ClassicColonyLayout.ICON_BELLS } };
        final BufferedImage cross = a.icon(ClassicColonyLayout.ICON_SHORTFALL);
        final int y = ClassicColonyLayout.BAND_ROW_Y;
        for (int grp = 0; grp < r.x.length; grp++) {
            final boolean crossed = grp == STORED && m.shortfall > 0;
            for (int q = 0; q < r.x[grp].length; q++) {
                final BufferedImage icon = a.icon(icons[grp][q]);
                for (int x : r.x[grp][q]) {
                    if (icon != null) g.drawImage(icon, x, y, null);
                    if (crossed && cross != null) g.drawImage(cross, x, y, null);
                }
            }
        }
        if (labels) {
            for (int grp = 0; grp < r.x.length; grp++) {
                if (!r.labelled[grp]) continue;
                ClassicColonyBuildings.countLabel(g, c, String.valueOf(r.total(grp)),
                    r.labelX[grp], y + ClassicColonyLayout.BAND_LABEL_DY);
            }
        }
        return flagDrawn;
    }
}
