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

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;

import net.sf.freecol.common.model.Ability;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Turn;


/**
 * The colony screen's <b>chrome and warehouse</b> in the original's look
 * (build spec M4.1, {@code playthrough-3-analysis/11-spec-M4-colony.md}
 * §1.1-1.3): the top line, the two black separators, the parchment ground
 * of the building area, the wood panel with the 3x3 map's black frame, the
 * band picture with its black first row, the 16 warehouse boxes and «Abb»
 * over the exit; plus the screen's shared text pieces (M4.0 targets 5 and
 * 6): FONTTINY in a palette ink, the hover label, the top line's notice
 * slot.  Static painters on the 320x200 canvas, so the live screen
 * ({@link ClassicColonyPanel}) and the golden tests draw with the same
 * code; every number comes from {@link ClassicColonyLayout}.
 *
 * <p>Everything is drawn from the pack ({@code ant classic-assets}) in the
 * original's colours: the sprites as the pack has them, the inks as the
 * game palette ({@code VICEROY}) has them.  0 differing pixels against
 * clip 020 #2260, #14817, #123003, #134173 and #118448 in every region
 * this class draws (ClassicColonyChromeTest).  Without the pack: flat
 * fills and a Swing font, so the screen stays usable.
 *
 * <p>Not drawn here (later steps of M4): the buildings, the 3x3 map's
 * tiles, the band's three panels' contents, the mode buttons.
 */
final class ClassicColonyChrome {

    private ClassicColonyChrome() {}   // static painters only

    /** The fallbacks without the pack: wood, parchment, the band. */
    private static final Color WOOD = new Color(0x5C3A22);
    private static final Color PARCH = new Color(0xEBDBA2);
    private static final Color BAND = new Color(0x698AC3);

    /** The inks the screen uses, as the game palette has them (the fallback). */
    private static final int[][] FALLBACK_INKS = {
        { ClassicColonyLayout.INK_BLACK, 0x000000 },
        { ClassicColonyLayout.INK_GREY, 0xAAAAAA },
        { ClassicColonyLayout.INK_GREEN, 0x55FF55 },
        { ClassicColonyLayout.INK_YELLOW, 0xFFFF55 },
        { ClassicColonyLayout.INK_WHITE, 0xFFFFFF },
        { ClassicColonyLayout.INK_CAPTION, 0x4159A6 },
        { ClassicColonyLayout.INK_LOW_DIGITS, 0x181C7D },
        { ClassicColonyLayout.INK_TOP_LINE, 0x559634 },
        { ClassicColonyLayout.INK_SHADOW, 0x55452C },
        { ClassicColonyLayout.INK_HIGH_DIGITS, 0xE3C328 },
        { ClassicColonyLayout.INK_NOTICE, 0xC7A220 },
    };


    /** The pack's pieces the chrome is drawn from; any may be null. */
    static final class Assets {

        /** FONTTINY. */
        final ClassicFont tiny;

        /** WOODTILE.SS.000, PARCH.SS.000, COLONY.PIK. */
        final BufferedImage wood, parch, band;

        /** ICONS.SS.022-037, the warehouse's goods. */
        final BufferedImage[] goods
            = new BufferedImage[ClassicColonyLayout.WAREHOUSE_BOXES];

        /** The game palette, 256 entries 0xRRGGBB, or null. */
        private final int[] palette;

        private Assets(ClassicPackFiles pack) {
            this.tiny = (pack == null) ? null : pack.font(ClassicFont.TINY);
            this.wood = image(pack, ClassicPackFiles.ssKey("WOODTILE.SS.000"));
            this.parch = image(pack, ClassicPackFiles.ssKey("PARCH.SS.000"));
            this.band = image(pack, ClassicPackFiles.pikKey("COLONY.PIK"));
            for (int i = 0; i < this.goods.length; i++) {
                this.goods[i] = image(pack, ClassicPackFiles.ssKey(String.format(
                    "ICONS.SS.%03d", ClassicColonyLayout.ICON_GOODS + i)));
            }
            this.palette = (pack == null) ? null : pack.gamePalette();
        }

        /**
         * The pieces of a pack.
         *
         * @param pack The pack, or null (everything falls back).
         * @return The assets.
         */
        static Assets load(ClassicPackFiles pack) {
            return new Assets(pack);
        }

        private static BufferedImage image(ClassicPackFiles pack, String key) {
            return (pack == null) ? null : pack.image(key);
        }

        /**
         * An ink's colour: the game palette's entry, else the fallback.
         *
         * @param ink The palette index.
         * @return 0xRRGGBB.
         */
        int rgb(int ink) {
            if (this.palette != null && ink >= 0 && ink < this.palette.length) {
                return this.palette[ink];
            }
            for (int[] e : FALLBACK_INKS) if (e[0] == ink) return e[1];
            return 0xFFFFFF;
        }

        /** @return Whether the screen can be drawn as the original (font and sprites). */
        boolean complete() {
            if (this.tiny == null || this.wood == null || this.parch == null
                || this.band == null || this.palette == null) return false;
            for (BufferedImage b : this.goods) if (b == null) return false;
            return true;
        }
    }


    /** What the chrome shows of a colony: the top line, the warehouse. */
    static final class Model {

        /** The top line's words ({@link ClassicColonyLayout#topLine}). */
        final String topLine;

        /** The 16 boxes' amounts, in {@link ClassicColonyLayout#WAREHOUSE_GOODS}' order. */
        final int[] amounts;

        /** Which of them the colony's custom house exports (their green digits). */
        final boolean[] exported;

        /** The selected good's box (the green frame), or -1. */
        final int selected;

        Model(String topLine, int[] amounts, boolean[] exported, int selected) {
            this.topLine = topLine;
            this.amounts = amounts.clone();
            this.exported = exported.clone();
            this.selected = selected;
        }
    }

    /**
     * The chrome's model of a colony now.
     *
     * @param colony The colony.
     * @param game The game (its turn), or null.
     * @param player Whose gold the top line shows, or null (the owner's).
     * @param text The pack's texts, or null (FreeCol's words then).
     * @param selected The selected good's box, or -1.
     * @return The model.
     */
    static Model model(Colony colony, Game game, Player player, ClassicText text,
                       int selected) {
        final Player p = (player != null) ? player : colony.getOwner();
        final Turn turn = (game == null) ? null : game.getTurn();
        final String season = seasonName(text, (turn == null) ? 0 : turn.getSeason());
        final String gold = (text == null) ? null
            : text.label("CTITLE", ClassicHud.CTITLE_GOLD);
        final String line = ClassicColonyLayout.topLine(colony.getName(),
            (season == null) ? "" : season, (turn == null) ? 0 : turn.getYear(),
            (gold == null) ? "Gold:" : gold.trim(),
            (p == null) ? 0 : Math.max(0, p.getGold()));
        return new Model(line, amounts(colony), exported(colony), selected);
    }

    /**
     * Which warehouse goods the colony's custom house exports (FreeCol's
     * export settings; only a colony with a custom house is asked, as
     * asking makes FreeCol create the setting).
     *
     * @param colony The colony.
     * @return One flag per box.
     */
    static boolean[] exported(Colony colony) {
        final boolean[] out = new boolean[ClassicColonyLayout.WAREHOUSE_BOXES];
        if (!colony.hasAbility(Ability.EXPORT)) return out;
        final Specification spec = colony.getSpecification();
        for (int i = 0; i < out.length; i++) {
            final GoodsType gt = (spec == null) ? null
                : spec.getGoodsType(ClassicColonyLayout.WAREHOUSE_GOODS[i]);
            out[i] = gt != null && colony.getExportData(gt).getExported();
        }
        return out;
    }

    /** LABELS.TXT {@code @MISC} «Tonnen» (line 73; blank lines skipped). */
    static final int MISC_TONS = 57;

    /**
     * A warehouse good's hover label: «Ware (n Tonnen)» (V clip008 §2.6:
     * «Erz (15 Tonnen)», «Musketen (50 Tonnen)»; clip 020 #21747): the
     * good's NAMES.TXT {@code @CARGO} name, its amount and LABELS
     * {@code @MISC} «Tonnen».
     *
     * @param t The texts, or null (FreeCol's name, "t").
     * @param type The good.
     * @param amount Its amount.
     * @return The label.
     */
    static String warehouseLabel(ClassicText t, GoodsType type, int amount) {
        final String tons = (t == null) ? null : t.misc(MISC_TONS);
        return ClassicTrade.goodsName(t, type) + " (" + Math.max(0, amount) + " "
            + ((tons == null) ? "t" : tons.trim()) + ")";
    }

    /**
     * A warehouse good's hover label: white FONTTINY on a black (w + 1) x
     * 7 box on rows 193-199, centred on the good's box and ending at x
     * 304 at most ({@link ClassicColonyLayout#warehouseLabelX}).
     *
     * @param g The canvas.
     * @param a The assets.
     * @param label The label.
     * @param box The good's box.
     * @return The label's box.
     */
    static Rectangle paintWarehouseLabel(Graphics2D g, Assets a, String label, int box) {
        final int w = textWidth(a, label);
        final int x = ClassicColonyLayout.warehouseLabelX(box, w);
        final Rectangle r = new Rectangle(x, ClassicColonyLayout.WAREHOUSE_LABEL_Y, w + 1,
                                          ClassicColonyLayout.HOVER_H);
        g.setColor(new Color(a.rgb(ClassicColonyLayout.INK_BLACK)));
        g.fillRect(r.x, r.y, r.width, r.height);
        text(g, a, label, x + 1, r.y + 1, ClassicColonyLayout.INK_WHITE);
        return r;
    }

    /**
     * The season's name: NAMES.TXT {@code @SEASONS} (0 spring, 1 autumn);
     * FreeCol has no season before 1600 ({@code Turn.getSeason} -1), the
     * original writes spring from 1492 on (as {@link ClassicHud#seasonLine}).
     *
     * @param t The texts, or null.
     * @param season FreeCol's season.
     * @return The name, or null without the texts.
     */
    static String seasonName(ClassicText t, int season) {
        if (t == null) return null;
        final List<String[]> rows = t.names("SEASONS");
        if (rows.isEmpty()) return null;
        return rows.get(Math.floorMod(Math.max(0, season), rows.size()))[0];
    }

    /**
     * The colony's stock of the 16 warehouse goods.
     *
     * @param colony The colony.
     * @return The amounts, in the original's order (0 for a good the
     *     rules lack).
     */
    static int[] amounts(Colony colony) {
        final int[] out = new int[ClassicColonyLayout.WAREHOUSE_BOXES];
        final Specification spec = colony.getSpecification();
        for (int i = 0; i < out.length; i++) {
            final GoodsType gt = (spec == null) ? null
                : spec.getGoodsType(ClassicColonyLayout.WAREHOUSE_GOODS[i]);
            out[i] = (gt == null) ? 0 : Math.max(0, colony.getGoodsCount(gt));
        }
        return out;
    }


    // Text (M4.0 target 5)

    /**
     * FONTTINY in a palette ink, glyph top-left at (x, y); a Swing font
     * without the pack.
     *
     * @param g The canvas.
     * @param a The assets.
     * @param s The text (plain).
     * @param x The x.
     * @param y The glyph top.
     * @param ink The ink.
     * @return The x after the text.
     */
    static int text(Graphics2D g, Assets a, String s, int x, int y, int ink) {
        if (s == null || s.isEmpty()) return x;
        if (a.tiny != null) {
            return a.tiny.draw(g, s, x, y, ClassicFont.colours(a.rgb(ink)));
        }
        g.setColor(new Color(a.rgb(ink)));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 6));
        g.drawString(s, x, y + 5);
        return x + g.getFontMetrics().stringWidth(s);
    }

    /**
     * A text's width as {@link #text} draws it.
     *
     * @param a The assets.
     * @param s The text.
     * @return The width.
     */
    static int textWidth(Assets a, String s) {
        if (s == null) return 0;
        if (a.tiny != null) return a.tiny.stringWidth(s);
        return 4 * s.length();
    }

    /**
     * The hover label (spec §1.2): white FONTTINY on a black (w + 1) x 7
     * box, the glyphs one pixel in; the box at {@link
     * ClassicColonyLayout#hoverBoxX} of the sprite, at the given y (the
     * sprite's top for the small houses and the fence, centred for the
     * large ones and the trees: M4.2).
     *
     * @param g The canvas.
     * @param a The assets.
     * @param s The label.
     * @param sx The sprite's x.
     * @param sw The sprite's width.
     * @param y The box's top.
     * @return The box.
     */
    static Rectangle paintHoverLabel(Graphics2D g, Assets a, String s, int sx, int sw,
                                     int y) {
        final int w = textWidth(a, s);
        final int x = ClassicColonyLayout.hoverBoxX(sx, sw, w);
        final Rectangle box = new Rectangle(x, y, w + 1, ClassicColonyLayout.HOVER_H);
        g.setColor(new Color(a.rgb(ClassicColonyLayout.INK_BLACK)));
        g.fillRect(box.x, box.y, box.width, box.height);
        text(g, a, s, x + 1, y + 1, ClassicColonyLayout.INK_WHITE);
        return box;
    }


    // The chrome (M4.1 target 1)

    /**
     * The top line: the wood rows 0-6 tiled from (0,0) and the black row 7
     * ({@link ClassicMenuBar#paintStrip}, the same chrome as the map's menu
     * strip), the colony's words in ink 68 at floor((322 - w)/2), glyph
     * top 1.
     *
     * @param g The canvas.
     * @param a The assets.
     * @param line The words.
     */
    static void paintTopLine(Graphics2D g, Assets a, String line) {
        strip(g, a);
        if (line == null || line.isEmpty()) return;
        text(g, a, line, ClassicColonyLayout.topLineX(textWidth(a, line)),
             ClassicColonyLayout.TOP_LINE_Y, ClassicColonyLayout.INK_TOP_LINE);
    }

    /**
     * The top line with a notice instead of the colony's words (M4.0
     * target 6; V #10058, #10116): the same wood, the notice in gold (ink
     * 149) at floor((320 - w)/2) -- the map strip's band rule
     * ({@link ClassicMenuBar#paintBand}).
     *
     * @param g The canvas.
     * @param a The assets.
     * @param notice The notice.
     */
    static void paintNotice(Graphics2D g, Assets a, String notice) {
        strip(g, a);
        if (notice == null || notice.isEmpty()) return;
        text(g, a, notice, ClassicColonyLayout.noticeX(textWidth(a, notice)),
             ClassicColonyLayout.TOP_LINE_Y, ClassicColonyLayout.INK_NOTICE);
    }

    /** The wood rows 0-6 and the black row 7. */
    private static void strip(Graphics2D g, Assets a) {
        tile(g, a.wood, ClassicColonyLayout.TOP_LINE, WOOD);
        g.setColor(new Color(a.rgb(ClassicColonyLayout.INK_BLACK)));
        g.fillRect(0, ClassicColonyLayout.TOP_SEPARATOR_Y, ClassicColonyLayout.W, 1);
    }

    /**
     * The building area's ground, PARCH.SS.000 tiled from (0,0), and the
     * black column x 199 beside it.
     *
     * @param g The canvas.
     * @param a The assets.
     */
    static void paintGround(Graphics2D g, Assets a) {
        tile(g, a.parch, ClassicColonyLayout.BUILDING_AREA, PARCH);
        final Rectangle r = ClassicColonyLayout.BUILDING_AREA;
        g.setColor(new Color(a.rgb(ClassicColonyLayout.INK_BLACK)));
        g.fillRect(ClassicColonyLayout.AREA_SEPARATOR_X, r.y, 1, r.height);
    }

    /**
     * The wood panel, WOODTILE.SS.000 tiled from (0,0), and the 3x3 map's
     * black frame (its tiles are the map step's, M4.4).
     *
     * @param g The canvas.
     * @param a The assets.
     */
    static void paintWoodPanel(Graphics2D g, Assets a) {
        tile(g, a.wood, ClassicColonyLayout.WOOD_PANEL, WOOD);
        final Rectangle f = ClassicColonyLayout.MAP_FRAME;
        g.setColor(new Color(a.rgb(ClassicColonyLayout.INK_BLACK)));
        g.fillRect(f.x, f.y, f.width, 1);
        g.fillRect(f.x, f.y + f.height - 1, f.width, 1);
        g.fillRect(f.x, f.y, 1, f.height);
        g.fillRect(f.x + f.width - 1, f.y, 1, f.height);
    }

    /**
     * The band: COLONY.PIK at (0,128), unscaled, and its first row black
     * (V: the picture's row 0 is blue, the screen's row 128 black, #2260).
     *
     * @param g The canvas.
     * @param a The assets.
     */
    static void paintBand(Graphics2D g, Assets a) {
        final int y = ClassicColonyLayout.BAND_Y;
        if (a.band != null) {
            g.drawImage(a.band, 0, y, null);
        } else {
            g.setColor(BAND);
            g.fillRect(0, y, ClassicColonyLayout.W, ClassicColonyLayout.H - y);
        }
        g.setColor(new Color(a.rgb(ClassicColonyLayout.INK_BLACK)));
        g.fillRect(0, y, ClassicColonyLayout.W, 1);
    }

    /**
     * The warehouse row (M4.1 target 2; V #2260, #118448, #134173): in
     * box i the good's icon ICONS.SS.(22 + i) at (19i + 10 - floor(w/2),
     * 181) and its amount at glyph top 194, the last two digits in ink 61,
     * higher ones in ink 148; no mark for the capacity (V: 121 rum with a
     * warehouse in the plain colours); the selected good's box framed green
     * (ink 10), x 19i ... 19i + 19, y 179-199.
     *
     * @param g The canvas.
     * @param a The assets.
     * @param amounts The 16 amounts.
     * @param exported Which goods the custom house exports (their last two
     *     digits green, ink 10; V dago, I the reason), or null for none.
     * @param selected The selected box, or -1.
     */
    static void paintWarehouse(Graphics2D g, Assets a, int[] amounts, boolean[] exported,
                               int selected) {
        for (int i = 0; i < ClassicColonyLayout.WAREHOUSE_BOXES; i++) {
            final BufferedImage icon = a.goods[i];
            if (icon != null) {
                g.drawImage(icon, ClassicColonyLayout.warehouseIconX(i, icon.getWidth()),
                            ClassicColonyLayout.WAREHOUSE_ICON_Y, null);
            }
            final int n = (amounts == null || i >= amounts.length) ? 0 : amounts[i];
            final String s = String.valueOf(Math.max(0, n));
            final int[] inks = ClassicColonyLayout.digitInks(n,
                exported != null && i < exported.length && exported[i]);
            int x = ClassicColonyLayout.warehouseNumberX(i, s.length());
            for (int k = 0; k < s.length(); k++) {
                x = text(g, a, s.substring(k, k + 1), x,
                         ClassicColonyLayout.WAREHOUSE_NUMBER_Y, inks[k]);
            }
        }
        if (selected >= 0 && selected < ClassicColonyLayout.WAREHOUSE_BOXES) {
            final Rectangle b = ClassicColonyLayout.warehouseBox(selected);
            g.setColor(new Color(a.rgb(ClassicColonyLayout.INK_GREEN)));
            g.fillRect(b.x, b.y, b.width, 1);
            g.fillRect(b.x, b.y + b.height - 1, b.width, 1);
            g.fillRect(b.x, b.y, 1, b.height);
            g.fillRect(b.x + b.width - 1, b.y, 1, b.height);
        }
    }

    /**
     * «Abb» in white over the red E (V #2260: glyph top 179, x 306).
     *
     * @param g The canvas.
     * @param a The assets.
     */
    static void paintExitLabel(Graphics2D g, Assets a) {
        text(g, a, ClassicColonyLayout.EXIT_LABEL, ClassicColonyLayout.EXIT_LABEL_X,
             ClassicColonyLayout.EXIT_LABEL_Y, ClassicColonyLayout.INK_WHITE);
    }

    /** Tile a sprite over a rectangle from (0,0); a flat fill without it. */
    private static void tile(Graphics2D g, BufferedImage sprite, Rectangle r,
                             Color fallback) {
        ClassicMenuBox.fillTiled(g, sprite, r, 0, 0, fallback);
    }
}
