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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.freecol.common.model.AbstractGoods;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Market;
import net.sf.freecol.common.model.Role;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Unit;


/**
 * The boxes of the Europe screen ({@link ClassicEuropePanel}, part N1):
 * what a click on a ship in port, a click on a colonist on the dock and a
 * ship dropped on "Ziel:" ask, and what their answers do.  Headless.
 *
 * <ul>
 *   <li><b>A click on the selected ship in port</b>: {@code
 *       @EUROPESHIPCLICK} «Europäische Hafenanlagen-Optionen für
 *       {Handelsschiff}:» with the four rows of {@code
 *       @EUROPESHIPOPTIONS} (V clip 020 #27512: box (31,72,258,56), the
 *       ship's icon at the left, the bar on row 1, all four rows with a
 *       single ship in port).  «Nach vorne bewegen.» puts the ship first
 *       in box 3 (I), «Segel setzen in die Neue Welt.» sails as the drop on
 *       "Ziel:" does (no second question, I), «Alle Waren entladen.» sells
 *       every hold of goods (I: in Europe the unload is the sale),
 *       «Keine Veränderungen.» and Escape do nothing.</li>
 *   <li><b>A click on a colonist on the dock</b>: {@code @EUROPEARM}
 *       «Optionen für europäische Hafenanlagen:» (V clip008 §3.3: box
 *       (31,64,258,72) for six rows, the unit's sprite at the left, the
 *       bar on row 1) with the rows of {@code @ARMOPTIONS} that fit the
 *       unit (V for a colonist without equipment: «Nicht aufs nächste
 *       Schiff gehen.», muskets, tools, horses, missionary, «Keine
 *       Veränderungen.»; I for the rest): "S" on the dock ({@code
 *       SENTRY}) offers «Nicht aufs nächste Schiff gehen.», "-" «An Bord
 *       des nächsten Schiffes gehen.»; equipment it carries is offered
 *       for sale, equipment it can take for purchase; a missionary only
 *       «{Missionar}-Status aufheben.».  The prices are the market's (V:
 *       50 muskets × ask 3 = 150, 100 tools × 2 = 200, 50 horses × 3 =
 *       150); FreeCol's {@code equipUnitForRole} pays them.  «An die
 *       Spitze der Schlange verlegen.» is not offered (its meaning for
 *       the boarding order is not seen).</li>
 *   <li><b>A ship dropped on "Ziel:"</b>: {@code @SAILAWAY} «Sollen wir
 *       die Segel in Richtung {Neue Welt} setzen, Eure Exzellenz?» with
 *       the admiral, the bar on «Jawohl, setzt alle Segel.» (V clip008
 *       §3.6, 8 frames after the drop); «Nein» and Escape: nothing.  The
 *       original's column of the ship, the colonists and the cargo at
 *       the box's left is not drawn.</li>
 * </ul>
 */
final class ClassicEuropeOptions {

    /** GAME.TXT's ship box and its rows. */
    static final String SHIP_SECTION = "EUROPESHIPCLICK", SHIP_ROWS = "EUROPESHIPOPTIONS";

    /** GAME.TXT's dock box and its rows. */
    static final String ARM_SECTION = "EUROPEARM", ARM_ROWS = "ARMOPTIONS";

    /** GAME.TXT's sail question. */
    static final String SAIL_SECTION = "SAILAWAY";

    /** {@code @SAILAWAY} after the drop (V clip008 #21192 -&gt; #21200: 8 frames). */
    static final double SAIL_MS = 8 * ClassicAdvisorLayer.FRAME_MS;

    /** The rows of {@code @EUROPESHIPOPTIONS}, in its order. */
    enum ShipOption { FRONT, SAIL, UNLOAD, NOTHING }

    /** The rows of {@code @ARMOPTIONS}, in its order (GAME.TXT 1708-1720). */
    enum ArmOption {
        STAY, BOARD, FRONT, ARM, DISARM, TOOLS, SELL_TOOLS, HORSES,
        SELL_HORSES, MISSIONARY, UNMISSIONARY, NOTHING
    }

    /** FreeCol's roles the dock box gives. */
    static final String DEFAULT = "model.role.default", SOLDIER = "model.role.soldier",
        DRAGOON = "model.role.dragoon", PIONEER = "model.role.pioneer",
        SCOUT = "model.role.scout", MISSIONARY = "model.role.missionary";

    /** The goods of the rows' prices, {@code %NUMBER0..2}. */
    static final String[] PRICED = { "model.goods.muskets", "model.goods.tools",
                                     "model.goods.horses" };


    private ClassicEuropeOptions() {}   // static helpers only


    // The ship's box

    /**
     * {@code @EUROPESHIPCLICK} with its four rows (class comment).
     *
     * @param t The original texts, or null.
     * @param ship The ship.
     * @param icon Its icon in the box, or null.
     * @param title The stopgap's window title.
     * @return The box, or null without the texts.
     */
    static ClassicAdvisorBox.Request shipRequest(ClassicText t, Unit ship,
                                                 ClassicAdvisorBox.UnitIcon icon,
                                                 String title) {
        final ClassicText.Message head = (t == null) ? null : t.message(SHIP_SECTION);
        final ClassicText.Message rows = (t == null) ? null : t.message(SHIP_ROWS);
        if (ship == null || head == null || head.text.isEmpty() || head.width == null
            || rows == null || rows.text.size() < ShipOption.values().length) return null;
        final Map<String, String> values = new HashMap<>();
        String name = ClassicTips.unitName(t, ship);
        if (name == null) name = ClassicColonyUnits.unitName(t, ship);
        values.put("STRING0", name);
        final List<String> lines = new ArrayList<>(head.text.size());
        for (String s : head.text) lines.add(ClassicText.substitute(s, values));
        final List<String> labels = new ArrayList<>();
        for (ShipOption o : ShipOption.values()) labels.add(rows.text.get(o.ordinal()).trim());
        return ClassicAdvisorBox.Request.builder(SHIP_SECTION).gameText(lines)
            .width(head.width).rows(labels).defaultRow(0)
            .cancelRow(labels.size() - 1).unitIcon(icon).stopgap(title, null).build();
    }

    /**
     * @param answer The row the ship's box returned (-1: none).
     * @return The option; «Keine Veränderungen.» for no row.
     */
    static ShipOption chosenShip(int answer) {
        final ShipOption[] all = ShipOption.values();
        return (answer < 0 || answer >= all.length) ? ShipOption.NOTHING : all[answer];
    }


    // The dock's box

    /**
     * The rows the dock box offers a unit (class comment), in
     * {@code @ARMOPTIONS} order.
     *
     * @param u The unit on the dock.
     * @return The rows, «Keine Veränderungen.» last.
     */
    static List<ArmOption> armOptions(Unit u) {
        final List<ArmOption> out = new ArrayList<>();
        if (u == null) {
            out.add(ArmOption.NOTHING);
            return out;
        }
        out.add((u.getState() == Unit.UnitState.SENTRY) ? ArmOption.STAY : ArmOption.BOARD);
        final String role = roleId(u);
        if (MISSIONARY.equals(role)) {
            out.add(ArmOption.UNMISSIONARY);
        } else {
            for (ArmOption o : new ArmOption[] { ArmOption.ARM, ArmOption.DISARM,
                    ArmOption.TOOLS, ArmOption.SELL_TOOLS, ArmOption.HORSES,
                    ArmOption.SELL_HORSES, ArmOption.MISSIONARY }) {
                if (offered(u, o)) out.add(o);
            }
        }
        out.add(ArmOption.NOTHING);
        return out;
    }

    /** Whether an equipment row fits the unit (its role now, its type). */
    private static boolean offered(Unit u, ArmOption o) {
        final String role = roleId(u);
        final boolean muskets = SOLDIER.equals(role) || DRAGOON.equals(role);
        final boolean horses = SCOUT.equals(role) || DRAGOON.equals(role);
        final boolean tools = PIONEER.equals(role);
        switch (o) {
        case ARM: if (muskets) return false; break;
        case DISARM: return muskets;
        case TOOLS: if (tools) return false; break;
        case SELL_TOOLS: return tools;
        case HORSES: if (horses) return false; break;
        case SELL_HORSES: return horses;
        case MISSIONARY: if (!DEFAULT.equals(role)) return false; break;
        default: return false;
        }
        final Role r = target(u, o);
        return r != null && u.roleIsAvailable(r);
    }

    /** @return The id of the unit's role ({@link #DEFAULT} for none). */
    private static String roleId(Unit u) {
        return (u.getRole() == null) ? DEFAULT : u.getRole().getId();
    }

    /**
     * The role a row of the dock box gives the unit (FreeCol's roles: muskets
     * make a soldier, a dragoon with horses; tools a pioneer; horses a
     * scout, a dragoon with muskets; their sale the role without them).
     *
     * @param u The unit.
     * @param o The row.
     * @return The role, or null for a row that changes no equipment.
     */
    static Role target(Unit u, ArmOption o) {
        if (u == null || o == null) return null;
        final Specification spec = u.getSpecification();
        final String role = roleId(u);
        final boolean muskets = SOLDIER.equals(role) || DRAGOON.equals(role);
        final boolean horses = SCOUT.equals(role) || DRAGOON.equals(role);
        final String id;
        switch (o) {
        case ARM: id = (horses) ? DRAGOON : SOLDIER; break;
        case DISARM: id = (horses) ? SCOUT : DEFAULT; break;
        case TOOLS: id = PIONEER; break;
        case SELL_TOOLS: id = DEFAULT; break;
        case HORSES: id = (muskets) ? DRAGOON : SCOUT; break;
        case SELL_HORSES: id = (muskets) ? SOLDIER : DEFAULT; break;
        case MISSIONARY: id = MISSIONARY; break;
        case UNMISSIONARY: id = DEFAULT; break;
        default: return null;
        }
        try {
            return spec.getRole(id);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * The role count a row gives: the role's most (a pioneer's 100 tools,
     * V clip008: "Mit Werkzeugen ausrüsten (Kosten 200¤)" at 2).
     *
     * @param r The role.
     * @return The count.
     */
    static int roleCount(Role r) {
        return (r == null || r.isDefaultRole()) ? 0 : Math.max(1, r.getMaximumCount());
    }

    /**
     * The rows' prices, {@code %NUMBER0} muskets, {@code %NUMBER1} tools,
     * {@code %NUMBER2} horses: what the unit's offered row pays (the
     * market's ask for a purchase) or brings (its bid for a sale) for
     * those goods (V clip008 §3.3).
     *
     * @param u The unit.
     * @param shown The rows offered ({@link #armOptions}).
     * @param m The market, or null (all 0).
     * @return Three prices.
     */
    static int[] armPrices(Unit u, List<ArmOption> shown, Market m) {
        final int[] out = new int[PRICED.length];
        if (u == null || shown == null || m == null) return out;
        for (ArmOption o : shown) {
            final Role r = target(u, o);
            if (r == null) continue;
            for (AbstractGoods ag : u.getGoodsDifference(r, roleCount(r))) {
                final GoodsType gt = ag.getType();
                for (int i = 0; i < PRICED.length; i++) {
                    if (!PRICED[i].equals(gt.getId()) || !rowPrices(o, i)) continue;
                    final int a = ag.getAmount();
                    out[i] = (a > 0) ? m.getBidPrice(gt, a) : m.getSalePrice(gt, -a);
                }
            }
        }
        return out;
    }

    /** Whether row {@code o} shows price {@code %NUMBERi}. */
    private static boolean rowPrices(ArmOption o, int i) {
        switch (o) {
        case ARM: case DISARM: return i == 0;
        case TOOLS: case SELL_TOOLS: return i == 1;
        case HORSES: case SELL_HORSES: return i == 2;
        default: return false;
        }
    }

    /**
     * {@code @EUROPEARM} with the rows offered (class comment).
     *
     * @param t The original texts, or null.
     * @param shown The rows ({@link #armOptions}).
     * @param prices Their prices ({@link #armPrices}).
     * @param icon The unit's icon in the box, or null.
     * @param title The stopgap's window title.
     * @return The box, or null without the texts.
     */
    static ClassicAdvisorBox.Request armRequest(ClassicText t, List<ArmOption> shown,
                                                int[] prices,
                                                ClassicAdvisorBox.UnitIcon icon,
                                                String title) {
        final ClassicText.Message head = (t == null) ? null : t.message(ARM_SECTION);
        final ClassicText.Message rows = (t == null) ? null : t.message(ARM_ROWS);
        if (head == null || head.text.isEmpty() || head.width == null || rows == null
            || rows.text.size() < ArmOption.values().length || shown == null
            || shown.isEmpty()) return null;
        final Map<String, String> values = new HashMap<>();
        for (int i = 0; i < PRICED.length; i++) {
            values.put("NUMBER" + i, Integer.toString((prices == null || i >= prices.length)
                                                      ? 0 : prices[i]));
        }
        final List<String> labels = new ArrayList<>(shown.size());
        for (ArmOption o : shown) {
            labels.add(ClassicText.substitute(rows.text.get(o.ordinal()).trim(), values));
        }
        return ClassicAdvisorBox.Request.builder(ARM_SECTION).gameText(head.text)
            .width(head.width).rows(labels).defaultRow(0)
            .cancelRow(labels.size() - 1).unitIcon(icon).stopgap(title, null).build();
    }

    /**
     * @param shown The rows offered.
     * @param answer The row the box returned (-1: none).
     * @return The option; «Keine Veränderungen.» for no row.
     */
    static ArmOption chosenArm(List<ArmOption> shown, int answer) {
        return (shown == null || answer < 0 || answer >= shown.size())
            ? ArmOption.NOTHING : shown.get(answer);
    }


    // The sail question

    /**
     * {@code @SAILAWAY} (class comment): the admiral, Jawohl / Nein.
     *
     * @param t The original texts, or null.
     * @param title The stopgap's window title.
     * @return The box, or null without the texts (then the ship sails
     *     without a question).
     */
    static ClassicAdvisorBox.Request sailRequest(ClassicText t, String title) {
        final ClassicText.Message m = (t == null) ? null : t.message(SAIL_SECTION);
        if (m == null || m.text.isEmpty() || m.options.size() < 2 || m.width == null) {
            return null;
        }
        final List<String> rows = new ArrayList<>();
        rows.add(m.options.get(0).trim());
        rows.add(m.options.get(1).trim());
        return ClassicAdvisorBox.Request.builder(SAIL_SECTION)
            .gameText(m.text).width(m.width).y(m.y).rows(rows)
            .defaultRow(ClassicHud.clamp(ClassicAdvisorBox.defaultRow(m), 0, 1))
            .cancelRow(1).portrait(ClassicAdvisorBox.Portrait.ADMIRAL)
            .openDelay(SAIL_MS).stopgap(title, null).build();
    }

    /**
     * @param answer The answer of {@code @SAILAWAY}.
     * @return Whether it was «Jawohl».
     */
    static boolean sails(int answer) {
        return answer == 0;
    }
}
