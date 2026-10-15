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
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.GoodsContainer;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;


/**
 * The colony screen's <b>port panel</b> in the original's look (build spec
 * M4.6, {@code playthrough-3-analysis/11-spec-M4-colony.md}; L §6, clip008
 * {@code 01-shipcolony.md} §2, dago §5; clip 020 at 0 px): the caption in
 * ink 57 at floor((328 - w)/2), «Keine Schiffe im Hafen» or «Einladen:
 * <Typ>» (LABELS {@code @MISC} 26 / 38); the carrier in port, a ship or a
 * wagon train (V: wagon trains count), drawn as the map draws a unit (its
 * 2-px black shadow, its flag with the order letter, ClassicHud's box
 * icon) in its 18x18 frame, green, white while the cursor stands on it in
 * the blink's on half; its six holds: in each a good's icon, the full
 * colours ICONS.SS.022-037 for a full hold of 100, the grey ones 038-053
 * for a part (V: 5 icons), no number; the crate ICONS.SS.122 over every
 * hold the carrier lacks, all six without one.
 *
 * <p>The holds are in the order the goods came aboard as this client saw
 * them (V clip008 §2.5, clip 020 #120499: rum before furs, the loading
 * order, not the goods'; FreeCol keeps goods per type): a good first seen
 * aboard follows the ones already there, goods already aboard when the
 * carrier is first seen in the goods' order; a hold emptied closes up
 * (V #134899: the ore moves into hold 1).  Units aboard first, each in as
 * many holds as it takes (I: how the original draws them is not seen;
 * as our Europe, their figure fitted into the hold).
 *
 * <p>A model ({@link #model}), then one painter ({@link #paint}).
 */
final class ClassicColonyPort {

    private ClassicColonyPort() {}   // static helpers only

    /** LABELS.TXT {@code @MISC}: «Keine Schiffe im Hafen», «Einladen» (lines 26, 38). */
    static final int MISC_NO_SHIPS = ClassicEuropePanel.MISC_NO_SHIPS,
        MISC_LOAD = ClassicEuropePanel.MISC_LOAD;


    /** One of the six holds as drawn. */
    static final class Hold {

        /** A unit aboard in its first hold, or null. */
        final Unit unit;

        /** A good and its amount in this hold (at most 100), or null. */
        final GoodsType goods;
        final int amount;

        /** Whether the carrier has no such hold (the crate). */
        final boolean crate;

        Hold(Unit unit, GoodsType goods, int amount, boolean crate) {
            this.unit = unit;
            this.goods = goods;
            this.amount = amount;
            this.crate = crate;
        }

        /** @return Whether the hold is full (its icon in the full colours). */
        boolean full() {
            return this.goods != null && this.amount >= GoodsContainer.CARGO_SIZE;
        }

        @Override
        public String toString() {
            if (this.crate) return "crate";
            if (this.unit != null) return this.unit.getType().getSuffix();
            if (this.goods != null) return this.goods.getSuffix() + ":" + this.amount;
            return "empty";
        }
    }

    /** What the port panel shows. */
    static final class Model {

        /** The carrier in port, or null. */
        final Unit carrier;

        /** The caption. */
        final String caption;

        /** The six holds. */
        final List<Hold> holds;

        Model(Unit carrier, String caption, List<Hold> holds) {
            this.carrier = carrier;
            this.caption = caption;
            this.holds = Collections.unmodifiableList(new ArrayList<>(holds));
        }

        /** @return Whether the carrier is a ship (its cell one higher). */
        boolean naval() {
            return this.carrier == null || this.carrier.isNaval();
        }

        /** @return The carrier's frame, or null without one. */
        Rectangle frame() {
            return (this.carrier == null) ? null : ClassicColonyLayout.carrierFrame(naval());
        }

        @Override
        public String toString() {
            return "\"" + this.caption + "\" "
                + ((this.carrier == null) ? "-" : this.carrier.getType().getSuffix())
                + " " + this.holds;
        }
    }

    /**
     * Whether a unit on the colony's tile is a carrier the port panel
     * shows: a ship or a wagon train that can carry goods (V), not one
     * under repair.
     *
     * @param u The unit.
     * @return True for a carrier.
     */
    static boolean isCarrier(Unit u) {
        return u != null && u.canCarryGoods() && !u.isDamagedAndUnderForcedRepair();
    }

    /**
     * The carrier the panel shows: the first ship on the colony's tile,
     * else the first wagon train (I: two carriers at once never seen; a
     * ship before a wagon train so a docked ship is never hidden by a
     * wagon train that came first, which stays in «Vorhandene Einheiten»;
     * among ships as Europe's box 3, the first; a question for Roger).
     *
     * @param colony The colony.
     * @return It, or null.
     */
    static Unit carrier(Colony colony) {
        final Tile t = colony.getTile();
        if (t == null) return null;
        Unit wagon = null;
        for (Unit u : t.getUnitList()) {
            if (!isCarrier(u)) continue;
            if (u.isNaval()) return u;
            if (wagon == null) wagon = u;
        }
        return wagon;
    }

    /**
     * The port panel's model of a colony now.
     *
     * @param colony The colony.
     * @param text The pack's texts, or null (FreeCol's words).
     * @return The model.
     */
    static Model model(Colony colony, ClassicText text) {
        final Unit carrier = carrier(colony);
        return new Model(carrier, caption(text, carrier), holds(carrier));
    }

    /**
     * The caption (V: «Keine Schiffe im Hafen», «Einladen: Handelsschiff»,
     * «Einladen: Wagenzug»): LABELS {@code @MISC} 26, or 38 with «: » and
     * the carrier's NAMES {@code @UNIT} name.
     *
     * @param t The texts, or null.
     * @param carrier The carrier, or null.
     * @return The caption.
     */
    static String caption(ClassicText t, Unit carrier) {
        if (carrier == null) {
            final String s = (t == null) ? null : t.misc(MISC_NO_SHIPS);
            return (s != null) ? s.trim() : Messages.message("colonyPanel.inPort");
        }
        final String load = (t == null) ? null : t.misc(MISC_LOAD);
        String name = ClassicTips.unitName(t, carrier);
        if (name == null) name = Messages.getName(carrier.getType());
        return ((load != null) ? load.trim() : "Einladen") + ": " + name;
    }


    // The holds' order (class comment)

    /**
     * Each carrier's goods (their ids) in the order they came aboard, by
     * the carrier's id, for the game {@link #ordersGame} (EDT; another
     * game's are dropped, so no old game is kept).
     */
    private static final Map<String, List<String>> ORDERS = new HashMap<>();

    /** The game {@link #ORDERS} belongs to, or null. */
    private static Game ordersGame = null;

    /**
     * A carrier's goods in their holds' order: the goods aboard before in
     * their order, then the new ones in the goods' order (the warehouse's).
     *
     * @param carrier The carrier.
     * @return The goods aboard, in order.
     */
    static List<GoodsType> goodsOrder(Unit carrier) {
        final List<GoodsType> aboard = new ArrayList<>();
        for (Goods g : carrier.getCompactGoodsList()) {
            if (g.getAmount() > 0) aboard.add(g.getType());
        }
        aboard.sort((x, y) -> Integer.compare(goodsRank(x), goodsRank(y)));
        final Game game = carrier.getGame();
        if (game != ordersGame) {
            ORDERS.clear();
            ordersGame = game;
        }
        final List<String> before = ORDERS.get(carrier.getId());
        final List<GoodsType> out = new ArrayList<>();
        if (before != null) {
            for (String id : before) {
                for (GoodsType gt : aboard) if (gt.getId().equals(id)) out.add(gt);
            }
        }
        for (GoodsType gt : aboard) if (!out.contains(gt)) out.add(gt);
        final List<String> ids = new ArrayList<>();
        for (GoodsType gt : out) ids.add(gt.getId());
        ORDERS.put(carrier.getId(), ids);
        return out;
    }

    /** @return How many carriers' orders are remembered (tests: one game's only). */
    static int remembered() {
        return ORDERS.size();
    }

    /** Forget the carriers' orders (the game view's teardown, tests). */
    static void forgetOrders() {
        ORDERS.clear();
        ordersGame = null;
    }

    /** A good's place in the warehouse's order (the 16 boxes), others after. */
    private static int goodsRank(GoodsType gt) {
        final String id = gt.getId();
        for (int i = 0; i < ClassicColonyLayout.WAREHOUSE_GOODS.length; i++) {
            if (ClassicColonyLayout.WAREHOUSE_GOODS[i].equals(id)) return i;
        }
        return ClassicColonyLayout.WAREHOUSE_GOODS.length;
    }

    /**
     * The six holds of a carrier (class comment): the units aboard, then
     * each good in its order in holds of 100 and its rest, then the empty
     * holds, then crates for the holds it lacks; six crates for none.
     *
     * @param carrier The carrier, or null.
     * @return {@link ClassicColonyLayout#HOLDS} holds.
     */
    static List<Hold> holds(Unit carrier) {
        final int n = ClassicColonyLayout.HOLDS;
        final List<Hold> out = new ArrayList<>(n);
        if (carrier != null) {
            for (Unit u : carrier.getUnitList()) {
                final int k = Math.max(1, u.getSpaceTaken());
                for (int i = 0; i < k && out.size() < n; i++) {
                    out.add(new Hold((i == 0) ? u : null, null, 0, false));
                }
            }
            for (GoodsType gt : goodsOrder(carrier)) {
                int left = carrier.getGoodsCount(gt);
                while (left > 0 && out.size() < n) {
                    final int a = Math.min(GoodsContainer.CARGO_SIZE, left);
                    out.add(new Hold(null, gt, a, false));
                    left -= a;
                }
            }
            final int cap = Math.min(n, carrier.getCargoCapacity());
            while (out.size() < cap) out.add(new Hold(null, null, 0, false));
        }
        while (out.size() < n) out.add(new Hold(null, null, 0, true));
        return out;
    }

    /**
     * A good's ICONS.SS frame in a hold: the warehouse box's icon, full or
     * grey.
     *
     * @param gt The good.
     * @param full Whether the hold is full.
     * @return The frame, or -1 for a good the warehouse has not.
     */
    static int holdIcon(GoodsType gt, boolean full) {
        final int r = goodsRank(gt);
        if (r >= ClassicColonyLayout.WAREHOUSE_GOODS.length) return -1;
        return ((full) ? ClassicColonyLayout.ICON_GOODS : ClassicColonyLayout.ICON_GOODS_GREY) + r;
    }


    // Painting

    /**
     * The port panel as the model says, over the band picture: the
     * caption (it may start at x 121, over the panel's edge: not clipped),
     * the carrier with its shadow, flag and letter, its frame (green; white
     * when {@code cursorWhite}), the holds' icons and crates.
     *
     * @param g The canvas.
     * @param c The chrome's assets (the font, the inks).
     * @param a The sprites.
     * @param tiny FONTTINY for the flag's letter, or null.
     * @param letter The carrier's flag letter.
     * @param m The model.
     * @param cursorWhite Whether the cursor stands on the carrier in its on half.
     */
    static void paint(Graphics2D g, ClassicColonyChrome.Assets c,
                      ClassicColonyBuildings.Assets a, ClassicFont tiny, String letter,
                      Model m, boolean cursorWhite) {
        ClassicColonyChrome.text(g, c, m.caption,
            ClassicColonyLayout.portCaptionX(ClassicColonyChrome.textWidth(c, m.caption)),
            ClassicColonyLayout.CAPTION_Y, ClassicColonyLayout.INK_CAPTION);
        if (m.carrier != null) {
            final Rectangle cell = ClassicColonyLayout.carrierCell(m.naval());
            final BufferedImage s = a.image(
                ClassicColonyBuildings.figureKey(a, m.carrier, true));
            ClassicHud.paintBoxIcon(g, tiny, letter, s,
                ClassicHud.nationRgb(m.carrier.getOwner()), ClassicHud.unitRow(m.carrier),
                cell.x, cell.y);
            final Rectangle f = m.frame();
            g.setColor(new Color(c.rgb((cursorWhite) ? ClassicColonyLayout.INK_WHITE
                                       : ClassicColonyLayout.INK_GREEN)));
            g.fillRect(f.x, f.y, f.width, 1);
            g.fillRect(f.x, f.y + f.height - 1, f.width, 1);
            g.fillRect(f.x, f.y, 1, f.height);
            g.fillRect(f.x + f.width - 1, f.y, 1, f.height);
        }
        for (int k = 0; k < m.holds.size(); k++) {
            final Hold h = m.holds.get(k);
            if (h.crate) {
                final BufferedImage crate = a.icon(ClassicColonyLayout.ICON_CRATE);
                if (crate != null) {
                    g.drawImage(crate, ClassicColonyLayout.crateX(k), ClassicColonyLayout.HOLD_Y,
                                null);
                }
            } else if (h.goods != null) {
                final int f = holdIcon(h.goods, h.full());
                final BufferedImage icon = (f < 0) ? null : a.icon(f);
                if (icon != null) {
                    g.drawImage(icon, ClassicColonyLayout.holdIconX(k, icon.getWidth()),
                                ClassicColonyLayout.HOLD_Y, null);
                }
            } else if (h.unit != null) {
                // I (class comment): the unit's figure fitted into the hold.
                final BufferedImage s = a.image(ClassicColonyBuildings.figureKey(a, h.unit,
                                                                                  true));
                if (s != null) {
                    final int x0 = ClassicColonyLayout.crateX(k) + 1;
                    final double f = Math.min(9.0 / s.getWidth(), 12.0 / s.getHeight());
                    final int w = Math.max(1, (int) Math.round(s.getWidth() * f));
                    final int hh = Math.max(1, (int) Math.round(s.getHeight() * f));
                    g.drawImage(s, x0 + (9 - w) / 2, ClassicColonyLayout.HOLD_Y + (12 - hh) / 2,
                                w, hh, null);
                }
            }
        }
    }
}
