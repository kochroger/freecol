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
 * {@code 01-shipcolony.md} §2, dago §5; clip 020 at 0 px; several carriers:
 * clip 040, {@code port-landlocked-analysis/10-port-carriers.md}): the
 * caption in ink 57 at floor((328 - w)/2), «Keine Schiffe im Hafen» or
 * «Einladen: <Typ>» of the <b>selected</b> carrier (LABELS {@code @MISC} 26
 * / 38); the carriers on the colony's tile, ships and wagon trains alike
 * (V: wagon trains count), in the <b>port order</b> ({@link #carriers}):
 * the first four full size in slots 18 px apart, each drawn as the map
 * draws a unit (its 2-px black shadow, its flag with the order letter,
 * ClassicHud's box icon; a ship one pixel higher outside slot 0), the rest
 * as 4x4 tiny icons at (123 + 5k, 139); the selected one's frame, green,
 * white while the cursor stands on it in the blink's on half or while a
 * goods drag runs; its six holds: in each a good's icon, the full colours
 * ICONS.SS.022-037 for a full hold of 100, the grey ones 038-053 for a part
 * (V: 5 icons), no number; the crate ICONS.SS.122 over every hold the
 * carrier lacks, all six without one.
 *
 * <p>The port order (V clip 040, 17 openings): a carrier that enters the
 * colony goes to the front, one that leaves is taken out, «Nach vorne
 * bewegen.» puts one to the front ({@link #front}); the others keep their
 * order; ships are not sorted before wagon trains (#52 642 W W W G).  The
 * first look at a colony's port has the newest arrival in front: the
 * tile's last unit (FreeCol appends an arriving unit).  One opening
 * (#61 104) shows an order the rule does not explain (I).  At every
 * opening the front carrier is selected, not the one selected last
 * (#52 642 ended with slot 3, #61 104 opened with slot 0).  A carrier
 * leaves the order where the client sees it go, not only at the next
 * look at the port ({@link #moved}: the map's move of a unit off the
 * colony's tile; {@link #observe}: a carrier seen elsewhere at a refresh),
 * so one that sails away and comes back between two visits is a new
 * arrival in front (the review of part V, fidelity M3).  The order is the
 * client's memory: a loaded game starts with the tile's order (I).
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

        /** The selected carrier (its caption, its holds), or null. */
        final Unit carrier;

        /** Every carrier in port, in the port order. */
        final List<Unit> carriers;

        /** The caption. */
        final String caption;

        /** The six holds. */
        final List<Hold> holds;

        Model(Unit carrier, String caption, List<Hold> holds) {
            this(carrier, (carrier == null) ? Collections.<Unit>emptyList()
                 : Collections.singletonList(carrier), caption, holds);
        }

        Model(Unit carrier, List<Unit> carriers, String caption, List<Hold> holds) {
            this.carrier = carrier;
            this.carriers = Collections.unmodifiableList(new ArrayList<>(carriers));
            this.caption = caption;
            this.holds = Collections.unmodifiableList(new ArrayList<>(holds));
        }

        /** @return Whether the selected carrier is a ship (its cell one higher). */
        boolean naval() {
            return this.carrier == null || this.carrier.isNaval();
        }

        /** @return The selected carrier's place in the port order, or -1. */
        int selectedIndex() {
            return this.carriers.indexOf(this.carrier);
        }

        /** @return The selected carrier's frame, or null without one. */
        Rectangle frame() {
            return (this.carrier == null) ? null : frameOf(selectedIndex(), naval());
        }

        /**
         * A carrier's frame by its place: full size in slots 0-3, a tiny
         * icon's from the 5th on.
         *
         * @param i Its place in the port order.
         * @param naval Whether it is a ship.
         * @return The frame, or null for no place.
         */
        static Rectangle frameOf(int i, boolean naval) {
            if (i < 0) return null;
            return (i < ClassicColonyLayout.FULL_CARRIERS)
                ? ClassicColonyLayout.carrierFrame(naval, i)
                : ClassicColonyLayout.tinyFrame(i - ClassicColonyLayout.FULL_CARRIERS);
        }

        /**
         * The carrier at a point: its full-size frame, or its tiny icon's
         * frame (V clip 040: the presses on slots 0-3 and on the tiny
         * icons, §1.4).
         *
         * @param x The x.
         * @param y The y.
         * @return The carrier, or null.
         */
        Unit carrierAt(int x, int y) {
            for (int i = 0; i < this.carriers.size(); i++) {
                final Unit u = this.carriers.get(i);
                final Rectangle f = frameOf(i, u.isNaval());
                if (f != null && f.contains(x, y)) return u;
            }
            return null;
        }

        @Override
        public String toString() {
            final StringBuilder order = new StringBuilder();
            for (Unit u : this.carriers) {
                order.append((order.length() == 0) ? "" : ",")
                    .append(u.getType().getSuffix())
                    .append((u == this.carrier) ? "*" : "");
            }
            return "\"" + this.caption + "\" "
                + ((this.carrier == null) ? "-" : this.carrier.getType().getSuffix())
                + " " + this.holds + ((this.carriers.size() > 1) ? " port=" + order : "");
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


    // The port order (class comment)

    /**
     * Each colony's carriers (their ids) in the port order, by the
     * colony's id, for the game {@link #ordersGame} (EDT; another game's
     * are dropped).
     */
    private static final Map<String, List<String>> PORTS = new HashMap<>();

    /**
     * The carriers on a colony's tile in the port order (class comment):
     * the order kept from before without those gone, the new ones (newest
     * first: the tile's last unit) in front.
     *
     * @param colony The colony.
     * @return The carriers.
     */
    static List<Unit> carriers(Colony colony) {
        final Tile t = colony.getTile();
        final List<Unit> present = new ArrayList<>();
        if (t == null) return present;
        for (Unit u : t.getUnitList()) if (isCarrier(u)) present.add(u);
        sameGame(colony.getGame());
        final List<String> before = PORTS.get(colony.getId());
        final List<Unit> kept = new ArrayList<>();
        if (before != null) {
            for (String id : before) {
                for (Unit u : present) if (u.getId().equals(id)) kept.add(u);
            }
        }
        final List<Unit> out = new ArrayList<>();
        for (int i = present.size() - 1; i >= 0; i--) {
            if (!kept.contains(present.get(i))) out.add(present.get(i));
        }
        out.addAll(kept);
        final List<String> ids = new ArrayList<>();
        for (Unit u : out) ids.add(u.getId());
        PORTS.put(colony.getId(), ids);
        return out;
    }

    /**
     * A unit moved from a tile to another (the map's move, ClassicGUI's
     * {@code animateUnitMove}; EDT): leaving a colony's tile it leaves
     * that colony's port order, so when it comes back it is a new arrival
     * in front (class comment).
     *
     * @param unit The unit.
     * @param from The tile it left.
     * @param to The tile it went to.
     */
    static void moved(Unit unit, Tile from, Tile to) {
        if (unit == null || from == null || from == to) return;
        final Colony c = from.getColony();
        if (c == null) return;
        final List<String> ids = PORTS.get(c.getId());
        if (ids != null) ids.remove(unit.getId());
    }

    /**
     * What the client sees now (ClassicGUI's refresh; EDT): every carrier
     * in a colony's port order that is no longer on the colony's tile (it
     * sailed to Europe, drove off, sank, was captured) leaves that order;
     * the order of a colony gone is dropped.
     *
     * @param game The game shown, or null.
     */
    static void observe(Game game) {
        if (game == null || PORTS.isEmpty()) return;
        sameGame(game);
        for (java.util.Iterator<Map.Entry<String, List<String>>> it = PORTS.entrySet().iterator();
             it.hasNext();) {
            final Map.Entry<String, List<String>> e = it.next();
            final Colony c = game.getFreeColGameObject(e.getKey(), Colony.class);
            final Tile t = (c == null || c.isDisposed()) ? null : c.getTile();
            if (t == null) {
                it.remove();
                continue;
            }
            e.getValue().removeIf(id -> {
                    final Unit u = game.getFreeColGameObject(id, Unit.class);
                    return u == null || u.isDisposed() || u.getTile() != t;
                });
        }
    }

    /**
     * «Nach vorne bewegen.» (V clip 040 #51 167, #51 512, #72 138): the
     * carrier goes to the front of its colony's port order, the others keep
     * theirs.
     *
     * @param colony The colony.
     * @param u The carrier.
     */
    static void front(Colony colony, Unit u) {
        final List<Unit> now = carriers(colony);
        if (!now.contains(u)) return;
        now.remove(u);
        now.add(0, u);
        final List<String> ids = new ArrayList<>();
        for (Unit c : now) ids.add(c.getId());
        PORTS.put(colony.getId(), ids);
    }

    /**
     * The carrier the panel shows when nobody has chosen: the front one
     * (V clip 040: slot 0 at every opening, 17 openings).
     *
     * @param colony The colony.
     * @return It, or null.
     */
    static Unit carrier(Colony colony) {
        final List<Unit> all = carriers(colony);
        return (all.isEmpty()) ? null : all.get(0);
    }

    /**
     * The port panel's model of a colony now, the front carrier selected.
     *
     * @param colony The colony.
     * @param text The pack's texts, or null (FreeCol's words).
     * @return The model.
     */
    static Model model(Colony colony, ClassicText text) {
        return model(colony, text, null);
    }

    /**
     * The port panel's model of a colony now.
     *
     * @param colony The colony.
     * @param text The pack's texts, or null (FreeCol's words).
     * @param selected The selected carrier, or null (or one gone): the front one.
     * @return The model.
     */
    static Model model(Colony colony, ClassicText text, Unit selected) {
        final List<Unit> all = carriers(colony);
        final Unit carrier = (selected != null && all.contains(selected)) ? selected
            : (all.isEmpty()) ? null : all.get(0);
        return new Model(carrier, all, caption(text, carrier), holds(carrier));
    }

    /**
     * The caption (V: «Keine Schiffe im Hafen», «Einladen: Handelsschiff»,
     * «Einladen: Wagenzug», «Einladen: Galeone»): LABELS {@code @MISC} 26,
     * or 38 with «: » and the carrier's NAMES {@code @UNIT} name.
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
        return ((load != null) ? load.trim() : "Einladen") + ": " + typeName(t, carrier);
    }

    /**
     * A carrier's NAMES {@code @UNIT} name («Galeone», «Wagenzug»), the
     * caption's and the goods notice's (LABELS 269, V clip 040 #65 889).
     *
     * @param t The texts, or null.
     * @param carrier The carrier.
     * @return The name.
     */
    static String typeName(ClassicText t, Unit carrier) {
        String name = ClassicTips.unitName(t, carrier);
        if (name == null) name = Messages.getName(carrier.getType());
        return name;
    }


    // The holds' order (class comment)

    /**
     * Each carrier's goods (their ids) in the order they came aboard, by
     * the carrier's id, for the game {@link #ordersGame} (EDT; another
     * game's are dropped, so no old game is kept).
     */
    private static final Map<String, List<String>> ORDERS = new HashMap<>();

    /** The game {@link #ORDERS} and {@link #PORTS} belong to, or null. */
    private static Game ordersGame = null;

    /** Drop the orders of another game than this one. */
    private static void sameGame(Game game) {
        if (game != ordersGame) {
            ORDERS.clear();
            PORTS.clear();
            ordersGame = game;
        }
    }

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
        sameGame(carrier.getGame());
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

    /** Forget the carriers' and the ports' orders (the game view's teardown, tests). */
    static void forgetOrders() {
        ORDERS.clear();
        PORTS.clear();
        ordersGame = null;
    }

    /** A good's place in the warehouse's order (the 16 boxes), others after. */
    static int goodsRank(GoodsType gt) {
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

    /** What a carrier's flag shows: its order letter. */
    interface Letters {

        /**
         * @param carrier A carrier.
         * @return Its flag's letter.
         */
        String of(Unit carrier);
    }

    /**
     * The port panel as the model says, over the band picture: the
     * caption (it may start at x 121, over the panel's edge: not clipped),
     * the carriers with their shadows, flags and letters (the first four
     * full size, the rest tiny), the selected one's frame (green; white
     * when {@code white}), its holds' icons and crates.
     *
     * @param g The canvas.
     * @param c The chrome's assets (the font, the inks).
     * @param a The sprites.
     * @param tiny FONTTINY for the flag's letter, or null.
     * @param letters The carriers' flag letters.
     * @param m The model.
     * @param white Whether the selected one's frame is white (the cursor
     *     on it in the blink's on half, or a goods drag).
     */
    static void paint(Graphics2D g, ClassicColonyChrome.Assets c,
                      ClassicColonyBuildings.Assets a, ClassicFont tiny, Letters letters,
                      Model m, boolean white) {
        ClassicColonyChrome.text(g, c, m.caption,
            ClassicColonyLayout.portCaptionX(ClassicColonyChrome.textWidth(c, m.caption)),
            ClassicColonyLayout.CAPTION_Y, ClassicColonyLayout.INK_CAPTION);
        final int black = c.rgb(ClassicColonyLayout.INK_BLACK);
        for (int i = 0; i < m.carriers.size(); i++) {
            final Unit u = m.carriers.get(i);
            final BufferedImage s = a.image(ClassicColonyBuildings.figureKey(a, u, true));
            if (i < ClassicColonyLayout.FULL_CARRIERS) {
                final Rectangle cell = ClassicColonyLayout.carrierCell(u.isNaval(), i);
                ClassicHud.paintBoxIcon(g, tiny, (letters == null) ? null : letters.of(u), s,
                    ClassicHud.nationRgb(u.getOwner()), ClassicHud.unitRow(u),
                    cell.x, cell.y);
            } else {
                paintTiny(g, s, black, i - ClassicColonyLayout.FULL_CARRIERS);
            }
        }
        final Rectangle f = m.frame();
        if (f != null) {
            g.setColor(new Color(c.rgb((white) ? ClassicColonyLayout.INK_WHITE
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
                final int fr = holdIcon(h.goods, h.full());
                final BufferedImage icon = (fr < 0) ? null : a.icon(fr);
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
                    final double fit = Math.min(9.0 / s.getWidth(), 12.0 / s.getHeight());
                    final int w = Math.max(1, (int) Math.round(s.getWidth() * fit));
                    final int hh = Math.max(1, (int) Math.round(s.getHeight() * fit));
                    g.drawImage(s, x0 + (9 - w) / 2, ClassicColonyLayout.HOLD_Y + (12 - hh) / 2,
                                w, hh, null);
                }
            }
        }
    }

    /**
     * A tiny icon (V clip 040, 0 px: 007 at (128,139) #61 150 and (123,139)
     * #52 642, 008 at (123,139) #61 150): its 4x4 pixel (i, j) is the
     * sprite's pixel (4i + 1, 4j + 1) where that is opaque and not black,
     * else the port's background shows.
     *
     * @param g The canvas.
     * @param s The carrier's sprite, or null.
     * @param black The black's colour.
     * @param k The tiny icon.
     */
    static void paintTiny(Graphics2D g, BufferedImage s, int black, int k) {
        if (s == null) return;
        final Rectangle r = ClassicColonyLayout.tinyIcon(k);
        for (int j = 0; j < ClassicColonyLayout.TINY_SIZE; j++) {
            for (int i = 0; i < ClassicColonyLayout.TINY_SIZE; i++) {
                final int sx = 4 * i + 1, sy = 4 * j + 1;
                if (sx >= s.getWidth() || sy >= s.getHeight()) continue;
                final int p = s.getRGB(sx, sy);
                if ((p >>> 24) < 128 || (p & 0xFFFFFF) == (black & 0xFFFFFF)) continue;
                g.setColor(new Color(p & 0xFFFFFF));
                g.fillRect(r.x + i, r.y + j, 1, 1);
            }
        }
    }
}
