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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.sf.freecol.common.model.AbstractGoods;
import net.sf.freecol.common.model.BuildableType;
import net.sf.freecol.common.model.Building;
import net.sf.freecol.common.model.BuildingType;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.FreeColGameObject;
import net.sf.freecol.common.model.FreeColObject;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.ModelMessage;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;


/**
 * The colony's turn reports that lead to its screen (build spec M4.11,
 * part W; clip 020 {@code 10-dialog-catalogue.md} §2.3, clip 040
 * {@code 10-port-carriers.md} §4): each in the original's words (GAME.TXT),
 * with the colonist (MSS5) at (130, box y − 62), and the two rows LABELS
 * {@code @MISC} «Runde fortsetzen.» (the bar) and «Zur Kolonie zoomen.»
 * (LABELS.TXT:49/50, not in the GAME.TXT sections).  V = seen in a clip,
 * I = inferred, SRC = the text file only.
 * <ul>
 *   <li>{@code @BUILT} «Kolonie Base produziert {Verlag}.» (V clip 020
 *       #111, #157, #227, #245; clip 040 #84 900 «Artillerie»): FreeCol's
 *       {@code model.colony.buildingReady} and {@code unitReady}, the
 *       NAMES.TXT name (no longer FreeCol's «Druckerei»).</li>
 *   <li>{@code @NEEDTOOLS} (V clip 020 #35, #69, #75, #85, #91; clip 048
 *       x11) and {@code @NEEDTOOLS0} (V clip 048 #10022, #101904, #135526:
 *       no tools at all, the narrower box (62,105,196,52)): the levi
 *       rule's {@code model.colony.classicNeedTools} (the server's, a
 *       build with all its hammers that lacks tools), or FreeCol's
 *       {@code buildableNeedsGoods} of tools under its own rule.</li>
 *   <li>{@code @ORE} (V clip 020 #204) and its siblings {@code @LUMBER}
 *       (V clip 046 #96631, clip 048 #118915), {@code @COTTON},
 *       {@code @TOBACCO}, {@code @CANESUGAR}, {@code @FURS},
 *       {@code @TOOLS} (SRC, I: the same rows): FreeCol's
 *       {@code model.building.noInput}, a building whose workers stand
 *       idle for want of its input.</li>
 *   <li>{@code @CARGOREADY0} (V clip 020 #236, #274, #283), {@code 1}
 *       (V x9, clips 046 #116549, 047 #400, 048 #24875 ...: box
 *       (42,102,236,58)), {@code 2} (V clip 040 #84 666): the levi rule's
 *       {@code model.colony.classicCargoReady0-2} (a new load of 100), or
 *       FreeCol's {@code warehouseFull} / {@code warehouseOverfull} under
 *       its own rule.</li>
 *   <li>{@code @SPOIL1} (V clip 048 #5690): FreeCol's
 *       {@code model.colony.warehouseWaste}, goods thrown away at the
 *       store's capacity, under every rule; {@code @SPOIL3} without the
 *       warehouse sentence when the warehouse cannot grow, and
 *       {@code @SPOIL2} / {@code @SPOIL4} once for several goods of one
 *       colony (SRC, I: the variants' rule as @CARGOREADY's; FreeCol sends
 *       one message per good; the review of part W, play L7 and fidelity
 *       L2).</li>
 *   <li>{@code @DEPLETION} with the frontiersman MSS3 (V clip 048
 *       #115237, box (77,124,166,40)): FreeCol's
 *       {@code model.colonyTile.resourceExhausted}.</li>
 *   <li>{@code @SONSUP} with the soldier MSS1 at the right (V clip 046
 *       #66519, #110556, clip 048 x3, box (39,110,226,58)) and
 *       {@code @SONSDOWN} (SRC, I: the same): FreeCol's
 *       {@code model.colony.soLIncrease} / {@code soLDecrease}.</li>
 * </ul>
 * {@code @FOOD1} «Der Nahrungsvorrat in … ist erschöpft» carries the rows
 * too (V clip 046 #89692, after a food party emptied the store), but
 * FreeCol sends nothing when a colony's food store runs empty: not built.
 * Before a colony's first box the map jumps to the colony when it is not
 * in view, the box {@link #JUMP_FRAMES} later (the review of part W,
 * fidelity M1: V clip 048 #135474 -&gt; #135504 -&gt; #135526, #118895
 * -&gt; #118906 -&gt; #118915, clip 046 #116539 -&gt; #116549; nothing
 * moves when it is in view, 046 #66519, 048 #19552, #110271).
 * «Zur Kolonie zoomen.» opens the colony screen {@link #ZOOM_FRAMES} after
 * the box closed (V 20 to 28), in the mode of spec §0.1 decision 8
 * ({@link Report#opening}).  The same colony's other reports of the turn
 * come first as boxes without rows that close by themselves after a time
 * that grows with their words ({@link #flashFrames}; V clip 040 #85 061 -
 * #85 184, clip 048 #128660, #135687), a @BUILT among them waits for a
 * key or a click (V the keylogged clips 049 #48095, 050 #30483; fixer A
 * after part X), the other colonies' after the
 * screen closed (V 048 #115205 -&gt; #115237: Base's screen closed, the
 * map on Silver City, its @DEPLETION).  A new load's report brings
 * @TUTORIAL6 after its box whatever row closed it, a zoom's screen after
 * the tip (V clip 046 #116708 -&gt; #116740 -&gt; #116872 -&gt; #116896).
 * The reports of a turn come after its other notices, grouped by colony in
 * the order they came, a colony's Sons of Liberty first ({@link #grouped};
 * the manual p.10: the European issues first, then each colony's; V clip
 * 048 #106927 @SONSUP -&gt; #107109 @NEEDTOOLS, FreeCol's order the other
 * way round; I for the grouping and the rest of the order).  Their lines
 * break by the boxes' measure (W − 6, {@code ClassicTextLayout}): the
 * original's own rule is not known, so a long line with a long colony name
 * can break one word differently (V clip 020 @ORE #96400, clip 040
 * #85132, clip 048 @NEEDTOOLS0 #135526; no width rule fits the 819 clean
 * line ends of playthroughs 3 and 4: the review of part W, fidelity L1).
 */
final class ClassicColonyReports {

    /** What a report is about. */
    enum Kind {
        /** A building or a unit finished (@BUILT). */
        BUILT,
        /** A build that lacks tools (@NEEDTOOLS, @NEEDTOOLS0). */
        NEED_TOOLS,
        /** A building idle for want of its input (@ORE and its siblings). */
        RAN_OUT,
        /** A new load of a good (@CARGOREADY0-2). */
        CARGO_READY,
        /** Goods thrown away at the store's capacity (@SPOIL1). */
        SPOILED,
        /** A mine near the colony worked out (@DEPLETION). */
        DEPLETED,
        /** The colony's Sons of Liberty up or down (@SONSUP, @SONSDOWN). */
        SONS
    }

    /** LABELS.TXT {@code @MISC} «Runde fortsetzen.» (LABELS.TXT:49). */
    static final int MISC_CONTINUE = 34;

    /** LABELS.TXT {@code @MISC} «Zur Kolonie zoomen.» (LABELS.TXT:50). */
    static final int MISC_ZOOM = 35;

    /** The rows: «Runde fortsetzen.» (the bar at the opening, Escape) and «Zur Kolonie zoomen.». */
    static final int ROW_CONTINUE = 0, ROW_ZOOM = 1;

    /**
     * The colony screen after «Zur Kolonie zoomen.» closed its box: 23
     * frames (V clip 020: #18336 -&gt; #18359, #57648 -&gt; #57671, #118424
     * -&gt; #118447, #129769 -&gt; #129792 23; #73418 -&gt; #73442,
     * #105665 -&gt; #105689 24; #134147 -&gt; #134173 26; clip 040 #85 185
     * -&gt; #85 208 23).
     */
    static final int ZOOM_FRAMES = 23;

    /**
     * A colony's first report after the map jumped to it: 9 frames (V clip
     * 048 #118906 -&gt; #118915, #115228 -&gt; #115237, clip 046 #116540
     * -&gt; #116549; 5 to 8 in 048 #24867/#24870 -&gt; #24875; 21 and 22 in
     * 048 #135504 -&gt; #135526, #140096 -&gt; #140117: I).
     */
    static final int JUMP_FRAMES = 9;

    /**
     * A report without rows after a zoom (not a @BUILT: that one waits for
     * a key or a click, {@link #BUILT_GAP_FRAMES}) stays for a time that
     * grows with its text and closes by itself: {@link #FLASH_BASE_FRAMES} +
     * {@link #FLASH_FRAMES_PER_CHAR} per character of its words
     * ({@link #flashFrames}; the least-squares line through the four seen,
     * I: clip 040 «Eine neue Ladung {Baumwolle} …» 64 characters #85 061
     * -&gt; #85 124, 63 frames, «… {Musketen} …» 63 characters #85 132 -&gt;
     * #85 184, 52 (no key or click between, the calibrated keylog); clip
     * 048 «… {Rum} …» 58 characters #128660 -&gt; #128720, 60, the four
     * lines of a @CARGOREADY1 for Silver City, 194 characters, #135687
     * -&gt; #135804, 117 (no keylog)).
     */
    static final int FLASH_BASE_FRAMES = 31;

    /** {@link #FLASH_BASE_FRAMES}' per character. */
    static final double FLASH_FRAMES_PER_CHAR = 0.44;

    /** The next report without rows after one closed: 7 frames (V clip 040 #85 125 -&gt; #85 132). */
    static final int FLASH_GAP_FRAMES = 7;

    /**
     * A row-less @BUILT after the zoom's box: 5 frames (V clip 049 #48090
     * -&gt; #48095, clip 050 #30478 -&gt; #30483); it waits for a key or a
     * click ({@link #request(ClassicText, Report, boolean, String, long)}).
     */
    static final int BUILT_GAP_FRAMES = 5;

    /** NAMES.TXT {@code @CARGO} goods with a GAME.TXT report that their input ran out. */
    private static final Map<String, String> RAN_OUT_SECTIONS = new HashMap<>();
    static {
        RAN_OUT_SECTIONS.put("lumber", "LUMBER");
        RAN_OUT_SECTIONS.put("cotton", "COTTON");
        RAN_OUT_SECTIONS.put("tobacco", "TOBACCO");
        RAN_OUT_SECTIONS.put("sugar", "CANESUGAR");
        RAN_OUT_SECTIONS.put("furs", "FURS");
        RAN_OUT_SECTIONS.put("ore", "ORE");
        RAN_OUT_SECTIONS.put("tools", "TOOLS");
    }

    /** FreeCol's message ids of the reports (the turn's other notices keep their way). */
    static final String BUILDING_READY = "model.colony.buildingReady",
        UNIT_READY = "model.colony.unitReady",
        NEED_TOOLS = "model.colony.classicNeedTools",
        NEEDS_GOODS = "model.colony.buildableNeedsGoods",
        NO_INPUT = "model.building.noInput",
        CARGO_READY = "model.colony.classicCargoReady",
        STORE_FULL = "model.colony.warehouseFull",
        STORE_AT_CAPACITY = "model.colony.warehouseOverfull",
        STORE_WASTE = "model.colony.warehouseWaste",
        RESOURCE_EXHAUSTED = "model.colonyTile.resourceExhausted",
        SONS_UP = "model.colony.soLIncrease",
        SONS_DOWN = "model.colony.soLDecrease";


    /** One report. */
    static final class Report {

        /** What it is about. */
        final Kind kind;

        /** Its GAME.TXT section ("BUILT", "NEEDTOOLS0", "CARGOREADY2" ...). */
        final String section;

        /** The colony. */
        final Colony colony;

        /** The placeholders' values. */
        final Map<String, String> values;

        /** How «Zur Kolonie zoomen.» opens the screen (spec §0.1 decision 8). */
        final ClassicColonyRight.Opening opening;

        /** The finished building of a building's @BUILT (its dissolve), or null. */
        final BuildingType built;

        /** The good of a @CARGOREADY and its stock (@TUTORIAL6's values), or null / 0. */
        final GoodsType goods;
        final int amount;

        /** FreeCol's message. */
        final ModelMessage message;

        Report(Kind kind, String section, Colony colony, Map<String, String> values,
               ClassicColonyRight.Opening opening, BuildingType built,
               GoodsType goods, int amount, ModelMessage message) {
            this.kind = kind;
            this.section = section;
            this.colony = colony;
            this.values = Collections.unmodifiableMap(new HashMap<>(values));
            this.opening = opening;
            this.built = built;
            this.goods = goods;
            this.amount = amount;
            this.message = message;
        }

        @Override
        public String toString() {
            return this.section + " " + ((this.colony == null) ? "-" : this.colony.getName());
        }
    }


    private ClassicColonyReports() {}   // static helpers only


    /**
     * The colony a report is about: its source (the colony, or the
     * building of a {@code noInput}).
     *
     * @param game The game.
     * @param m The message.
     * @return The colony, or null.
     */
    static Colony colonyOf(Game game, ModelMessage m) {
        if (game == null || m == null) return null;
        final FreeColGameObject src = game.getMessageSource(m);
        if (src instanceof Colony) return (Colony) src;
        if (src instanceof Building) return ((Building) src).getColony();
        return null;
    }

    /**
     * Whether a message is one of the reports (class comment), whatever
     * its values.
     *
     * @param id The message id, or null.
     * @return True if so.
     */
    static boolean isReportId(String id) {
        return id != null && (BUILDING_READY.equals(id) || UNIT_READY.equals(id)
            || NEED_TOOLS.equals(id) || NEEDS_GOODS.equals(id) || NO_INPUT.equals(id)
            || id.startsWith(CARGO_READY) || STORE_FULL.equals(id)
            || STORE_AT_CAPACITY.equals(id) || STORE_WASTE.equals(id)
            || RESOURCE_EXHAUSTED.equals(id) || SONS_UP.equals(id) || SONS_DOWN.equals(id));
    }

    /**
     * A FreeCol message as the original's report.
     *
     * @param t The pack's texts, or null.
     * @param m The message.
     * @param game The game.
     * @return The report, or null when it is none (FreeCol's words then)
     *     or a value is not known.
     */
    static Report of(ClassicText t, ModelMessage m, Game game) {
        if (t == null || m == null || game == null || !isReportId(m.getId())) return null;
        final Colony colony = colonyOf(game, m);
        if (colony == null) return null;
        final Specification spec = colony.getSpecification();
        final String id = m.getId();
        final Map<String, String> v = new LinkedHashMap<>();
        v.put("STRING0", colony.getName());
        if (BUILDING_READY.equals(id)) {
            final BuildingType bt = buildingOf(spec, m, "%building%");
            if (bt == null) return null;
            v.put("STRING1", ClassicColonyRight.buildName(t, bt));
            return new Report(Kind.BUILT, "BUILT", colony, v,
                ClassicColonyRight.Opening.BUILT_BUILDING, bt, null, 0, m);
        }
        if (UNIT_READY.equals(id)) {
            final FreeColObject d = game.getMessageDisplay(m);
            final UnitType ut = (d instanceof Unit) ? ((Unit) d).getType() : null;
            if (ut == null) return null;
            v.put("STRING1", ClassicColonyRight.buildName(t, ut));
            return new Report(Kind.BUILT, "BUILT", colony, v,
                ClassicColonyRight.Opening.BUILT_UNIT, null, null, 0, m);
        }
        if (NEED_TOOLS.equals(id) || NEEDS_GOODS.equals(id)) {
            final BuildableType build = buildableOf(spec, m, "%buildable%");
            final GoodsType tools = spec.getGoodsType("model.goods.tools");
            if (build == null || tools == null) return null;
            int need, have;
            if (NEED_TOOLS.equals(id)) {
                need = number(m, "%amount%");
                have = number(m, "%number%");
            } else {
                // FreeCol's rule: only its report of missing tools.
                if (goodsOf(spec, m, "%goodsType%") != tools) return null;
                need = required(build, tools);
                have = need - number(m, "%amount%");
            }
            if (need <= 0 || have < 0) return null;
            v.put("STRING1", ClassicColonyRight.buildName(t, build));
            v.put("NUMBER0", Integer.toString(need));
            v.put("NUMBER1", Integer.toString(have));
            return new Report(Kind.NEED_TOOLS, (have == 0) ? "NEEDTOOLS0" : "NEEDTOOLS",
                colony, v, ClassicColonyRight.Opening.NEED_TOOLS, null, null, 0, m);
        }
        if (NO_INPUT.equals(id)) {
            final GoodsType g = goodsOf(spec, m, "%inputGoods%");
            final String section = (g == null) ? null : RAN_OUT_SECTIONS.get(g.getSuffix());
            if (section == null) return null;
            return new Report(Kind.RAN_OUT, section, colony, v,
                ClassicColonyRight.Opening.ORE, null, g, 0, m);
        }
        if (STORE_WASTE.equals(id)) {
            // «{%NUMBER0} Tonnen {%STRING1} wurden weggeworfen» (V clip 048
            // #5690), with the hint of a bigger warehouse while it can grow,
            // else @SPOIL3 without it (SRC, I: as @CARGOREADY1/2).  Its zoom
            // opens in house mode (V 048 #5690 -> #5884/#5887 after a visit
            // that ended in hammer mode; the review of part W, fidelity M3).
            final GoodsType g = goodsOf(spec, m, "%goods%");
            final String name = (g == null) ? null
                : ClassicTips.cargoName(t, ClassicTips.cargoRow(g.getId()));
            final int waste = number(m, "%waste%");
            if (name == null || waste <= 0) return null;
            v.put("STRING1", name);
            v.put("NUMBER0", Integer.toString(waste));
            return new Report(Kind.SPOILED, (storeCanGrow(colony)) ? "SPOIL1" : "SPOIL3",
                colony, v, ClassicColonyRight.Opening.ORE, null, g, 0, m);
        }
        if (RESOURCE_EXHAUSTED.equals(id)) {
            // «Mine erschöpft in der Nähe von %STRING0.» (V clip 048 #115237).
            return new Report(Kind.DEPLETED, "DEPLETION", colony, v,
                ClassicColonyRight.Opening.OTHER, null, null, 0, m);
        }
        if (SONS_UP.equals(id) || SONS_DOWN.equals(id)) {
            // «… ist auf %NUMBER0%% gestiegen …» (V clip 046 #66519: 11 %).
            final int sol = number(m, "%newSoL%");
            if (sol < 0) return null;
            v.put("NUMBER0", Integer.toString(sol));
            return new Report(Kind.SONS, (SONS_UP.equals(id)) ? "SONSUP" : "SONSDOWN",
                colony, v, ClassicColonyRight.Opening.OTHER, null, null, 0, m);
        }
        // A new load (the levi rule's, or FreeCol's store warnings under its own).
        final GoodsType g = goodsOf(spec, m, "%goods%");
        final String name = (g == null) ? null
            : ClassicTips.cargoName(t, ClassicTips.cargoRow(g.getId()));
        if (name == null) return null;
        final int variant;
        final int level;
        int amount;
        if (id.startsWith(CARGO_READY)) {
            try {
                variant = Integer.parseInt(id.substring(CARGO_READY.length()));
            } catch (NumberFormatException e) {
                return null;
            }
            if (variant < 0 || variant > 2) return null;
            level = number(m, "%level%");
            amount = number(m, "%amount%");
        } else {
            level = colony.getWarehouseCapacity();
            variant = (STORE_FULL.equals(id)) ? 0 : (storeCanGrow(colony)) ? 1 : 2;
            amount = -1;
        }
        if (amount < 0) amount = colony.getGoodsCount(g);
        v.put("STRING1", name);
        v.put("NUMBER0", Integer.toString(level));
        return new Report(Kind.CARGO_READY, "CARGOREADY" + variant, colony, v,
            ClassicColonyRight.Opening.OTHER, null, g, amount, m);
    }

    /**
     * A report's box: GAME.TXT's words with its values, its advisor over
     * it ({@link #portrait}: the colonist MSS5 centred at box y − 62), the
     * box and portrait centred on y 100 together (V clip 020: @BUILT
     * (87,111,146,40), @NEEDTOOLS (42,105,236,52), @ORE (42,108,236,46),
     * @CARGOREADY0 (42,111,236,40); clips 046-048: @CARGOREADY1
     * (42,102,236,58), @NEEDTOOLS0 (62,105,196,52), @LUMBER and @SPOIL1
     * (42,105,236,52), @DEPLETION (77,124,166,40) with MSS3 at (86,37),
     * @SONSUP (39,110,226,58) with MSS1 at (210,33)).  With its rows:
     * «Runde fortsetzen.» / «Zur Kolonie zoomen.», the bar on row 1,
     * Escape on row 1 (I).  Without (after a zoom, V clip 040 #85 061:
     * (42,116,236,30), #85 132: (42,119,236,24); clip 048 #135687:
     * (42,110,236,42)): no bar, it closes by itself after
     * {@link #flashFrames} of its words; a row-less @BUILT waits for a key
     * or a click instead (V clips 049, 050 with the keylog,
     * {@link #BUILT_GAP_FRAMES}).
     *
     * @param t The pack's texts, or null.
     * @param r The report.
     * @param rows Whether it carries its two rows.
     * @param title The stopgap's window title.
     * @return The request, or null without the texts.
     */
    static ClassicAdvisorBox.Request request(ClassicText t, Report r, boolean rows,
                                             String title) {
        return request(t, r, rows, title, 0L);
    }

    /**
     * A report's box ({@link #request(ClassicText, Report, boolean,
     * String)}), with its rows at a set moment: a colony's first box
     * {@link #JUMP_FRAMES} after the map jumped to it.
     *
     * @param t The pack's texts, or null.
     * @param r The report.
     * @param rows Whether it carries its two rows.
     * @param title The stopgap's window title.
     * @param showAt When a box with rows is to appear (the box layer's
     *     clock), or 0 for as soon as it can.
     * @return The request, or null without the texts.
     */
    static ClassicAdvisorBox.Request request(ClassicText t, Report r, boolean rows,
                                             String title, long showAt) {
        final ClassicText.Message m = (t == null || r == null) ? null : t.message(r.section);
        final ClassicAdvisorBox.Builder b = ClassicAdvisorBox.fromGameText(
            "report " + r.section, m, r.values);
        if (b == null) return null;
        b.portrait(portrait(r.kind));
        if (rows) {
            final String go = label(t, MISC_CONTINUE), zoom = label(t, MISC_ZOOM);
            if (go == null || zoom == null) return null;
            b.rows(ClassicAdvisorBox.literal(go), ClassicAdvisorBox.literal(zoom))
                .defaultRow(ROW_CONTINUE).cancelRow(ROW_CONTINUE);
            if (showAt != 0L) b.showAt(showAt);
            return b.stopgap(title, null).build();
        }
        if (r.kind == Kind.BUILT) {
            // A row-less @BUILT waits for a key or a click, as any notice
            // (fixer A after part X, the keylogged clips: 049 «Kolonie Base
            // produziert Druckerei.» #48095-#48191 closed by the click's
            // release, 97 frames; 050 «Kolonie Base produziert Ställe.»
            // #30483-#30585 closed by Enter, 102 frames; the timed model
            // below would have closed them after 46 and 45), 5 frames after
            // the zoom's box (049 #48090 -> #48095, 050 #30478 -> #30483).
            return b.chain(ClassicColonyMoves.ms(BUILT_GAP_FRAMES)).stopgap(title, null)
                .build();
        }
        // 7 frames after the box before (the zoom's or the last of these),
        // not the boxes' usual 200 ms (V clip 040 #85 054 -> #85 061,
        // #85 124 -> #85 132; clip 048 #128653 -> #128660); its time from
        // its words.
        b.chain(ClassicColonyMoves.ms(FLASH_GAP_FRAMES));
        final int chars = b.stopgap(title, null).build().plainText().trim().length();
        return b.closeAfter(ClassicColonyMoves.ms(flashFrames(chars))).build();
    }

    /**
     * How long a report without rows stays (class comment; I: the
     * least-squares line of the four seen, {@link #FLASH_BASE_FRAMES} +
     * {@link #FLASH_FRAMES_PER_CHAR} per character, rounded).
     *
     * @param chars The characters of its words.
     * @return The frames.
     */
    static int flashFrames(int chars) {
        return FLASH_BASE_FRAMES + (int) Math.round(FLASH_FRAMES_PER_CHAR * Math.max(0, chars));
    }

    /**
     * Who stands at a report: the colonist (MSS5), the frontiersman (MSS3)
     * at a worked-out mine (V clip 048 #115237), the soldier (MSS1) for
     * the Sons of Liberty (V clip 046 #66519).
     *
     * @param kind The report's kind.
     * @return The portrait.
     */
    static ClassicAdvisorBox.Portrait portrait(Kind kind) {
        if (kind == Kind.DEPLETED) return ClassicAdvisorBox.Portrait.SCOUT;
        if (kind == Kind.SONS) return ClassicAdvisorBox.Portrait.SOLDIER;
        return ClassicAdvisorBox.Portrait.COLONIST;
    }

    /**
     * The reports of a turn in the order the player sees them: grouped by
     * colony, the colonies in the order of their first report (I); in each
     * colony its Sons of Liberty first, the rest in the order they came (V
     * clip 048 #106927 @SONSUP -&gt; #107109 @NEEDTOOLS; @NEEDTOOLS before
     * @CARGOREADY as FreeCol's, V 048 #110271 -&gt; #110402, #128314 -&gt;
     * #128660, #135526 -&gt; #135687; I for the rest), and the goods it
     * threw away in one box when there are several ({@link #spoiled}).
     *
     * @param reports The reports in FreeCol's order.
     * @return The groups, one list per colony.
     */
    static List<List<Report>> grouped(List<Report> reports) {
        final Map<Colony, List<Report>> by = new LinkedHashMap<>();
        if (reports != null) {
            for (Report r : reports) {
                if (r == null) continue;
                by.computeIfAbsent(r.colony, c -> new ArrayList<>()).add(r);
            }
        }
        final List<List<Report>> out = new ArrayList<>();
        for (List<Report> g : by.values()) {
            final List<Report> sons = new ArrayList<>(), rest = new ArrayList<>();
            for (Report r : spoiled(g)) {
                ((r.kind == Kind.SONS) ? sons : rest).add(r);
            }
            sons.addAll(rest);
            out.add(sons);
        }
        return out;
    }

    /**
     * A colony's goods thrown away: one {@code @SPOIL1} / {@code @SPOIL3}
     * per good as FreeCol sends them, but several goods in one turn as one
     * {@code @SPOIL2} («Ein Teil unserer Waren wurde weggeworfen.» with the
     * warehouse sentence) or, when the warehouse cannot grow,
     * {@code @SPOIL4}, where the first of them stood (SRC, I: GAME.TXT's
     * sections, no clip shows them).
     *
     * @param g One colony's reports.
     * @return Its reports with the goods thrown away merged.
     */
    static List<Report> spoiled(List<Report> g) {
        int n = 0;
        for (Report r : g) if (r.kind == Kind.SPOILED) n++;
        if (n < 2) return g;
        final List<Report> out = new ArrayList<>();
        boolean merged = false;
        for (Report r : g) {
            if (r.kind != Kind.SPOILED) {
                out.add(r);
            } else if (!merged) {
                merged = true;
                final Map<String, String> v = new LinkedHashMap<>();
                v.put("STRING0", r.colony.getName());
                out.add(new Report(Kind.SPOILED, (storeCanGrow(r.colony)) ? "SPOIL2" : "SPOIL4",
                    r.colony, v, ClassicColonyRight.Opening.ORE, null, null, 0, r.message));
            }
        }
        return out;
    }

    /**
     * Whether a colony's warehouse can still grow (the variant of
     * {@code @CARGOREADY1/2} and {@code @SPOIL1/3}, as the server's
     * {@code ServerColony.originalCargoReady}).
     *
     * @param colony The colony.
     * @return True if its store building has an upgrade.
     */
    static boolean storeCanGrow(Colony colony) {
        final Building store = (colony == null) ? null : colony.getWarehouse();
        return store != null && store.getType().getUpgradesTo() != null;
    }

    /**
     * {@code @TUTORIAL6}'s values after a @CARGOREADY0 closed on row 1 (V
     * clip 020 #237: «Sir,in {Base} sind {103 Rum}. Wenn wir ein Schiff nach
     * Base schicken, … nach Amsterdam in Europa bringen …»): {@code %STRING0}
     * the good, {@code %STRING1} the colony, {@code %NUMBER0} its stock,
     * {@code %STRING2} the home port.
     *
     * @param t The pack's texts, or null.
     * @param r The report.
     * @return The values (missing ones left out).
     */
    static Map<String, String> cargoTipValues(ClassicText t, Report r) {
        final Map<String, String> v = new HashMap<>();
        if (r == null) return v;
        final String good = r.values.get("STRING1");
        if (good != null) v.put("STRING0", good);
        if (r.colony != null) {
            v.put("STRING1", r.colony.getName());
            final String port = ClassicTips.homePort(t, r.colony.getOwner());
            if (port != null) v.put("STRING2", port);
        }
        v.put("NUMBER0", Integer.toString(Math.max(0, r.amount)));
        return v;
    }


    // Values

    /** A LABELS.TXT {@code @MISC} entry, trimmed, or null. */
    private static String label(ClassicText t, int misc) {
        final String s = (t == null) ? null : t.misc(misc);
        return (s == null || s.trim().isEmpty()) ? null : s.trim();
    }

    /** A placeholder's id ({@code model.building.printingPress.name}), or null. */
    private static String replacement(StringTemplate m, String key) {
        final StringTemplate r = (m == null) ? null : m.getReplacement(key);
        final String s = (r == null) ? null : r.getId();
        return (s == null || s.isEmpty()) ? null : s;
    }

    /** A named type's id from its name key ({@code model.goods.ore.name}), or null. */
    private static String typeId(StringTemplate m, String key) {
        final String s = replacement(m, key);
        return (s != null && s.endsWith(".name")) ? s.substring(0, s.length() - 5) : s;
    }

    /** A building type named by a placeholder, or null. */
    private static BuildingType buildingOf(Specification spec, StringTemplate m, String key) {
        final String id = typeId(m, key);
        try {
            return (id == null) ? null : spec.getBuildingType(id);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** A building or unit type named by a placeholder, or null. */
    private static BuildableType buildableOf(Specification spec, StringTemplate m, String key) {
        final String id = typeId(m, key);
        if (id == null) return null;
        try {
            if (id.startsWith("model.building.")) return spec.getBuildingType(id);
            if (id.startsWith("model.unit.")) return spec.getUnitType(id);
        } catch (RuntimeException e) {
            return null;
        }
        return null;
    }

    /** A goods type named by a placeholder, or null. */
    private static GoodsType goodsOf(Specification spec, StringTemplate m, String key) {
        final String id = typeId(m, key);
        try {
            return (id == null) ? null : spec.getGoodsType(id);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** An amount placeholder, or -1. */
    private static int number(StringTemplate m, String key) {
        final String s = replacement(m, key);
        try {
            return (s == null) ? -1 : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** How much of a goods type a build needs, 0 for none. */
    static int required(BuildableType build, GoodsType type) {
        if (build == null || type == null) return 0;
        for (AbstractGoods ag : build.getRequiredGoodsList()) {
            if (ag.getType() == type) return ag.getAmount();
        }
        return 0;
    }
}
