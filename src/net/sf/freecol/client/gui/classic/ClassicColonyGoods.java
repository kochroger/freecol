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

import net.sf.freecol.common.model.GoodsContainer;
import net.sf.freecol.common.model.GoodsType;


/**
 * The colony screen's <b>goods between the warehouse and a carrier</b>
 * (build spec M4.9, {@code playthrough-3-analysis/11-spec-M4-colony.md}; O
 * §7, C §2.2 and §4; clip008 §3.5-3.6; several carriers: clip 040,
 * {@code port-landlocked-analysis/10-port-carriers.md} §1.6).  Headless:
 * the rules, the words, the boxes and the times; the panel drags and makes
 * the controller's calls ({@code InGameController.loadCargo},
 * {@code unloadCargo}).
 *
 * <ul>
 *   <li><b>The drag</b> (V, 6 drags in clip 020, 8 in clip 040): the
 *       press on a warehouse cell or on a hold starts it; the good is the
 *       one under the press; the top line holds «Einladen &lt;Ware&gt;» or
 *       «Ausladen &lt;Ware&gt;» (LABELS {@code @CMESSAGE} 15/16) from the
 *       press to the drop, and for as long as an amount box is open; the
 *       warehouse cell's frame is yellow (ink 14) at the press, green (10)
 *       17 frames later; the selected carrier's frame white; the ghost is
 *       the goods' icon.  A drop of a warehouse good on the holds loads
 *       the selected carrier; on another carrier, full size or tiny, that
 *       one, and the selection stays (V clip 040 #67 183).  A drop of a
 *       hold on any warehouse cell unloads it (V #134312: furs dropped on
 *       the ore cell).  A press without motion flashes the notice; a drop
 *       on its own side moves nothing and shows nothing (V clip 040).</li>
 *   <li><b>How much</b> (V): without Shift a hold's worth: the whole hold
 *       to unload, min(stock, 100) to load (V 24, 12, 18), at most what the
 *       carrier takes; with Shift {@code @HOWMUCH1} (load, «Wieviel {Rum}
 *       auf Wagenzug laden (0-100)?») or {@code @HOWMUCH2} (unload,
 *       «Wieviel {Erz} von Handelsschiff auf Base laden (0-4)?»), box
 *       (42,82,236,37), field (75,100,36,10), 26/27 frames after the drop,
 *       <b>its preset the maximum</b>, selected (4, 50, 100; Europe's
 *       {@code @HOWMUCH5} opens with «100» instead).</li>
 *   <li><b>The result</b> (V): «&lt;n&gt; &lt;Ware&gt; bewegt nach
 *       &lt;Kolonie | Typ&gt;» (LABELS {@code @CMESSAGE} 2), the carrier's
 *       NAMES type for a load («… nach Galeone», «… nach Wagenzug»), the
 *       colony for an unload, 2 frames after a drop or 1 after an amount
 *       box closes, for 138 frames, or until the next press (V clip 040
 *       #67 230); the hold's icon and the warehouse's number change
 *       together 8 frames after the drop.  No gold, no moves.</li>
 * </ul>
 */
final class ClassicColonyGoods {

    private ClassicColonyGoods() {}   // static helpers only

    /** LABELS {@code @CMESSAGE}: «bewegt nach», «Einladen», «Ausladen» (LABELS.TXT:269, 282, 283). */
    static final int CMESSAGE_MOVED = 2, CMESSAGE_LOAD = 15, CMESSAGE_UNLOAD = 16;

    /** GAME.TXT's amount boxes: load, unload. */
    static final String LOAD_SECTION = "HOWMUCH1", UNLOAD_SECTION = "HOWMUCH2";

    /** The result notice after a drop: 2 frames (V #10114 -&gt; #10116). */
    static final int NOTICE_FRAMES = 2;

    /** The result notice after an amount box closes: 1 frame (V #47844 -&gt; #47845). */
    static final int AFTER_BOX_FRAMES = 1;

    /** The hold's icon and the warehouse's number after the drop: 8 frames (V #10114 -&gt; #10122). */
    static final int REDRAW_FRAMES = 8;

    /** The warehouse cell's yellow frame before it turns green: 17 frames (V png #10057 -&gt; #10074). */
    static final int YELLOW_FRAMES = 17;

    /**
     * The amount box after the drop: 26/27 frames (V #47616 -&gt; #47642,
     * #118940 -&gt; #118966, #134706 -&gt; #134733): Europe's 27
     * ({@link ClassicTrade#HOWMUCH_MS}).
     */
    static final double HOWMUCH_MS = ClassicTrade.HOWMUCH_MS;

    /**
     * @param n Frames of the original.
     * @return Their milliseconds.
     */
    static double ms(int n) {
        return ClassicColonyLayout.frames(n);
    }

    /**
     * The notice while a good is dragged: «Einladen Erz», «Ausladen Felle»
     * (V clip 020 #10058, #134313; LABELS 282/283 and the NAMES good).
     *
     * @param t The pack's texts, or null.
     * @param load True from the warehouse, false from a hold.
     * @param gt The good.
     * @return The notice.
     */
    static String dragNotice(ClassicText t, boolean load, GoodsType gt) {
        final String w = (t == null) ? null
            : t.label("CMESSAGE", (load) ? CMESSAGE_LOAD : CMESSAGE_UNLOAD);
        return ((w == null) ? ((load) ? "Einladen" : "Ausladen") : w.trim()) + " "
            + ClassicTrade.goodsName(t, gt);
    }

    /**
     * The result: «24 Erz bewegt nach Handelsschiff», «18 Felle bewegt
     * nach Base» (V clip 020 #10116, #134376; clip 040 #65 889).
     *
     * @param t The pack's texts, or null.
     * @param n The amount moved.
     * @param gt The good.
     * @param to The carrier's NAMES type, or the colony's name.
     * @return The notice.
     */
    static String movedNotice(ClassicText t, int n, GoodsType gt, String to) {
        final String w = (t == null) ? null : t.label("CMESSAGE", CMESSAGE_MOVED);
        return n + " " + ClassicTrade.goodsName(t, gt) + " "
            + ((w == null) ? "bewegt nach" : w.trim()) + " " + to;
    }

    /**
     * How much a load moves at most: a hold's worth, min(stock, 100), and
     * no more than the carrier takes (V 24, 12, 18; @HOWMUCH1's «(0-100)»
     * with 121 rum in stock).
     *
     * @param stock The warehouse's amount.
     * @param loadable What the carrier can take of it.
     * @return The amount, at least 0.
     */
    static int loadMax(int stock, int loadable) {
        return Math.max(0, Math.min(GoodsContainer.CARGO_SIZE, Math.min(stock, loadable)));
    }

    /**
     * An amount box (class comment): {@code @HOWMUCH1} to load,
     * {@code @HOWMUCH2} to unload, no portrait, the field's preset the
     * maximum, 26/27 frames after the drop.
     *
     * @param t The pack's texts, or null.
     * @param load True to load.
     * @param gt The good.
     * @param carrier The carrier's NAMES type.
     * @param colony The colony's name.
     * @param max The «(0-max)», also the preset.
     * @return The box, or null without the texts.
     */
    static ClassicAdvisorBox.Request howMuch(ClassicText t, boolean load, GoodsType gt,
                                            String carrier, String colony, int max) {
        final String section = (load) ? LOAD_SECTION : UNLOAD_SECTION;
        final Map<String, String> values = new HashMap<>();
        values.put("STRING0", ClassicTrade.goodsName(t, gt));
        values.put("STRING1", carrier);
        values.put("STRING2", colony);
        values.put("NUMBER0", Integer.toString(max));
        final ClassicAdvisorBox.Builder b = ClassicAdvisorBox.fromGameText(section,
            (t == null) ? null : t.message(section), values);
        return (b == null) ? null : b.portrait(ClassicAdvisorBox.Portrait.NONE)
            .amountField(max, max).openDelay(HOWMUCH_MS).stopgap(colony, null).build();
    }
}
