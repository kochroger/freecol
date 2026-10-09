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

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * Buying and selling a part of a hold in Europe (gap list B1,
 * {@link ClassicTrade}): the plain click, the Shift click and its
 * {@code @HOWMUCH} boxes, {@code @TUTORIAL18}; their texts and boxes
 * against GAME.TXT and the playthrough-1 clip.
 */
public class ClassicTradeTest extends FreeColTestCase {

    private static final UnitType merchantman
        = spec().getUnitType("model.unit.merchantman");
    private static final UnitType colonist
        = spec().getUnitType("model.unit.freeColonist");
    private static final GoodsType tools = spec().getGoodsType("model.goods.tools");
    private static final GoodsType sugar = spec().getGoodsType("model.goods.sugar");
    private static final GoodsType furs = spec().getGoodsType("model.goods.furs");

    private Game game;
    private Player dutch;
    private Unit ship;
    private int price;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.game = getStandardGame();
        this.dutch = this.game.getPlayerByNationId("model.nation.dutch");
        this.ship = new ServerUnit(this.game, this.dutch.getEurope(), this.dutch,
                                   merchantman);
        this.price = this.dutch.getMarket().getCostToBuy(tools);
        assertTrue(this.price > 0);
    }

    /** A stand-in screen: records the calls, answers the box with {@link #answer}. */
    private static final class Screen implements ClassicTrade.Trader {

        final List<String> calls = new ArrayList<>();
        int answer = -1;
        boolean ok = true;
        Goods sold = null;

        @Override
        public int ask(boolean buying, GoodsType type, int max, int preset) {
            this.calls.add((buying ? "ask buy " : "ask sell ") + type.getSuffix()
                           + " max=" + max + " preset=" + preset);
            return this.answer;
        }

        @Override
        public boolean buy(GoodsType type, int amount, Unit ship) {
            this.calls.add("buy " + type.getSuffix() + " " + amount);
            return this.ok;
        }

        @Override
        public boolean sell(Goods goods) {
            this.calls.add("sell " + goods.getType().getSuffix() + " " + goods.getAmount());
            this.sold = goods;
            return this.ok;
        }

        @Override
        public void cannotPay(GoodsType type) {
            this.calls.add("cannot pay " + type.getSuffix());
        }
    }

    /**
     * One drop loads the hold's free space for the goods, at most 100;
     * the gold pays the most of it at the market's price (the clip: 144
     * gold at 2 pays 72 of 100).
     */
    public void testHoldAndAffordable() {
        assertEquals(100, ClassicTrade.hold(this.ship, tools));
        assertEquals(0, ClassicTrade.hold(null, tools));
        assertEquals(0, ClassicTrade.hold(this.ship, null));
        this.ship.addGoods(sugar, 300);
        this.ship.addGoods(tools, 70);
        assertEquals("the partly filled hold's rest", 30, ClassicTrade.hold(this.ship, tools));
        assertEquals("no hold left", 0, ClassicTrade.hold(this.ship, furs));
        this.dutch.setGold(72 * this.price);
        assertEquals(72, ClassicTrade.affordable(this.dutch, tools, 100));
        assertEquals(50, ClassicTrade.affordable(this.dutch, tools, 50));
        this.dutch.setGold(72 * this.price + this.price - 1);
        assertEquals(72, ClassicTrade.affordable(this.dutch, tools, 100));
        this.dutch.setGold(0);
        assertEquals(0, ClassicTrade.affordable(this.dutch, tools, 100));
        assertEquals(0, ClassicTrade.affordable(null, tools, 100));
    }

    /**
     * A plain click buys the full drop when the gold pays it; one the gold
     * cannot pay buys nothing and tells the screen ({@code @TUTORIAL18});
     * with the hold full nothing happens at all.
     */
    public void testPlainBuy() {
        final Screen s = new Screen();
        this.dutch.setGold(100 * this.price);
        assertEquals(100, ClassicTrade.buy(this.dutch, this.ship, tools, false, s));
        assertEquals(List.of("buy tools 100"), s.calls);
        s.calls.clear();
        this.dutch.setGold(100 * this.price - 1);
        assertEquals(0, ClassicTrade.buy(this.dutch, this.ship, tools, false, s));
        assertEquals(List.of("cannot pay tools"), s.calls);
        s.calls.clear();
        // The controller refuses: nothing bought.
        this.dutch.setGold(100 * this.price);
        s.ok = false;
        assertEquals(0, ClassicTrade.buy(this.dutch, this.ship, tools, false, s));
        assertEquals(List.of("buy tools 100"), s.calls);
        s.calls.clear();
        s.ok = true;
        // A full hold: nothing, no box, no tip.
        this.ship.addGoods(sugar, 400);
        assertEquals(0, ClassicTrade.buy(this.dutch, this.ship, tools, false, s));
        assertEquals(0, ClassicTrade.buy(this.dutch, this.ship, tools, true, s));
        assertTrue(s.calls.isEmpty());
        // Not a ship, no ship, no screen.
        final Unit land = new ServerUnit(this.game, this.dutch.getEurope(), this.dutch,
                                         colonist);
        assertEquals(0, ClassicTrade.buy(this.dutch, land, tools, false, s));
        assertEquals(0, ClassicTrade.buy(this.dutch, null, tools, false, s));
        assertEquals(0, ClassicTrade.buy(this.dutch, this.ship, tools, false, null));
        assertTrue(s.calls.isEmpty());
    }

    /**
     * Shift: the box asks with the "(0-max)" of the hold and the preset
     * "100", the "(0-max)" whatever the gold pays (V #54344: 144 gold at
     * 2, "(0-100)" and "100"; Roger, 2026-10-09); Enter's number is
     * bought; Escape, 0 or an empty field buy nothing; a number the gold
     * cannot pay, the preset itself too, buys nothing and brings no tip
     * (the original's red line, W22).
     */
    public void testShiftBuy() {
        final Screen s = new Screen();
        this.dutch.setGold(72 * this.price);
        s.answer = 50;
        assertEquals(50, ClassicTrade.buy(this.dutch, this.ship, tools, true, s));
        assertEquals(List.of("ask buy tools max=100 preset=100", "buy tools 50"), s.calls);
        for (int a : new int[] { -1, 0 }) {
            s.calls.clear();
            s.answer = a;
            assertEquals(0, ClassicTrade.buy(this.dutch, this.ship, tools, true, s));
            assertEquals(List.of("ask buy tools max=100 preset=100"), s.calls);
        }
        for (int a : new int[] { 73, 100 }) {     // 100: Enter on the preset
            s.calls.clear();
            s.answer = a;
            assertEquals(0, ClassicTrade.buy(this.dutch, this.ship, tools, true, s));
            assertEquals(List.of("ask buy tools max=100 preset=100"), s.calls);
        }
        assertEquals("nothing paid", 72 * this.price, this.dutch.getGold());
        s.calls.clear();
        s.answer = 72;
        assertEquals(72, ClassicTrade.buy(this.dutch, this.ship, tools, true, s));
        assertEquals(List.of("ask buy tools max=100 preset=100", "buy tools 72"), s.calls);
        // Enough gold: Enter on the preset buys the whole "(0-max)"; a
        // partly filled hold's rest is the max and the preset.
        s.calls.clear();
        this.dutch.setGold(100 * this.price);
        s.answer = 100;
        assertEquals(100, ClassicTrade.buy(this.dutch, this.ship, tools, true, s));
        assertEquals(List.of("ask buy tools max=100 preset=100", "buy tools 100"), s.calls);
        s.calls.clear();
        this.dutch.setGold(10000 * this.price);
        this.ship.addGoods(sugar, 300);
        this.ship.addGoods(tools, 70);
        s.answer = 30;
        assertEquals(30, ClassicTrade.buy(this.dutch, this.ship, tools, true, s));
        assertEquals(List.of("ask buy tools max=30 preset=30", "buy tools 30"), s.calls);
        // A boycott: no box, nothing.
        s.calls.clear();
        this.dutch.getMarket().setArrears(tools, 500);
        assertFalse(this.dutch.canTrade(tools));
        assertEquals(0, ClassicTrade.buy(this.dutch, this.ship, tools, true, s));
        assertTrue(s.calls.isEmpty());
    }

    /**
     * A click on a good in the hold sells all of it; Shift asks
     * {@code @HOWMUCH5} with all of it as max and preset and sells the
     * number, the goods still on the ship; Escape or 0 sells nothing.
     */
    public void testSell() {
        final Screen s = new Screen();
        this.ship.addGoods(furs, 100);
        final Goods g = this.ship.getCompactGoodsList().get(0);
        assertSame(furs, g.getType());
        assertEquals(100, ClassicTrade.sell(this.dutch, g, false, s));
        assertEquals(List.of("sell furs 100"), s.calls);
        assertSame(g, s.sold);
        s.calls.clear();
        s.answer = 30;
        assertEquals(30, ClassicTrade.sell(this.dutch, g, true, s));
        assertEquals(List.of("ask sell furs max=100 preset=100", "sell furs 30"), s.calls);
        assertSame(this.ship, s.sold.getLocation());
        assertEquals(30, s.sold.getAmount());
        assertEquals("the hold is the server's to change", 100,
                     this.ship.getGoodsCount(furs));
        for (int a : new int[] { -1, 0 }) {
            s.calls.clear();
            s.answer = a;
            assertEquals(0, ClassicTrade.sell(this.dutch, g, true, s));
            assertEquals(List.of("ask sell furs max=100 preset=100"), s.calls);
        }
        s.calls.clear();
        s.answer = 100;
        assertEquals(100, ClassicTrade.sell(this.dutch, g, true, s));
        assertSame("all of it: the goods themselves", g, s.sold);
        assertEquals(0, ClassicTrade.sell(this.dutch, null, true, s));
    }

    /**
     * The boxes' values: NAMES.TXT's names with the pack, FreeCol's
     * without; the numbers as they are ("$" is GAME.TXT's, after them).
     */
    public void testValues() {
        Map<String, String> v = ClassicTrade.buyValues(null, tools, 2, this.ship, 100);
        assertEquals(Messages.getName(tools), v.get("STRING0"));
        assertEquals("2", v.get("NUMBER1"));
        assertEquals(Messages.getName(merchantman), v.get("STRING1"));
        assertEquals("100", v.get("NUMBER0"));
        v = ClassicTrade.tipValues(null, tools, 2, 144);
        assertEquals("2", v.get("NUMBER0"));
        assertEquals("144", v.get("NUMBER1"));
        v = ClassicTrade.sellValues(null, furs, 3, this.dutch, 60);
        assertEquals("3", v.get("NUMBER1"));
        assertEquals("60", v.get("NUMBER0"));
        assertNotNull(v.get("STRING2"));
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        if (t == null) {
            System.err.println(getClass().getSimpleName()
                + ": the pack's names skipped, no pack texts");
            return;
        }
        v = ClassicTrade.buyValues(t, tools, 2, this.ship, 100);
        assertEquals("Werkzeuge", v.get("STRING0"));
        assertEquals("Handelsschiff", v.get("STRING1"));
        assertEquals("Amsterdam", ClassicTrade.sellValues(t, furs, 3, this.dutch, 60)
                     .get("STRING2"));
        assertEquals("Felle", ClassicTrade.goodsName(t, furs));
    }

    /**
     * With the pack: {@code @HOWMUCH4} as the clip shows it (#54344): the
     * text of GAME.TXT 2725 with the values, re-wrapped, the box
     * (42,79,236,43) without a portrait, "Menge:" at x 47, the field
     * (75,103,36,10) with "100" selected, 27 frames after the click;
     * {@code @HOWMUCH5} alike; {@code @TUTORIAL18} at (7,10,306,66), no
     * portrait.
     */
    public void testBoxesWithThePack() {
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final ClassicText t = ClassicText.load(pack);
        final ClassicFont tiny = (pack == null) ? null : pack.font(ClassicFont.TINY);
        if (t == null || tiny == null) {
            System.err.println(getClass().getSimpleName()
                + ": boxes skipped, no pack texts or FONTTINY (ant classic-assets)");
            return;
        }
        final ClassicAdvisorBox.Request r = ClassicTrade.howMuchRequest(t, true,
            clipValues(), 100, 100);
        assertEquals(ClassicTrade.BUY_SECTION, r.id);
        assertEquals("Wieviel Werkzeuge (zu 2$) kaufen und auf Handelsschiff laden (0-100)?",
                     r.plainText());
        assertEquals("Menge:", ClassicAdvisorBox.plain(r.fieldLabel));
        assertTrue(r.field.isAmount());
        assertEquals("100", r.field.initial);
        assertSame(ClassicAdvisorBox.Portrait.NONE, r.portrait);
        assertEquals(ClassicTrade.HOWMUCH_MS, r.openDelayMs, 1e-9);
        assertEquals(27 * 1000.0 / 70.0863, r.openDelayMs, 1e-6);
        final ClassicAdvisorBox.Layout l = ClassicAdvisorBox.layout(r, tiny, null);
        assertEquals(new Rectangle(42, 79, 236, 43), l.box);
        assertEquals(new Rectangle(75, 103, 36, 10), l.field);
        assertEquals(47, l.fieldLabel.x);

        final ClassicAdvisorBox.Request sell = ClassicTrade.howMuchRequest(t, false,
            ClassicTrade.sellValues(t, furs, 3, this.dutch, 60), 60, 60);
        assertEquals(ClassicTrade.SELL_SECTION, sell.id);
        assertEquals("Wieviel Felle (zu 3$) an Amsterdam verkaufen (0-60)?",
                     sell.plainText());
        assertEquals("60", sell.field.initial);
        assertNotNull(ClassicAdvisorBox.layout(sell, tiny, null));

        final ClassicAdvisorBox.Request tip = ClassicTips.request(t, ClassicTips.PART,
            ClassicTrade.tipValues(t, tools, 2, 144));
        assertEquals("TUTORIAL18", tip.id);
        assertSame(ClassicAdvisorBox.Portrait.NONE, tip.portrait);
        assertTrue(tip.isNotice());
        assertTrue(tip.plainText(), tip.plainText().contains("Da Werkzeuge pro Einheit 2$ kostet, "
            + "wir aber nur über 144$ Gold verfügen"));
        assertEquals(new Rectangle(7, 10, 306, 66),
                     ClassicAdvisorBox.layout(tip, tiny, null).box);
        assertEquals(38 * 1000.0 / 70.0863, ClassicTrade.TIP_MS, 1e-6);
    }

    /**
     * {@code @TUTORIAL18} once per game, with Tutortips on (bit 18 of
     * the player's record, kept in the save): none with Tutortips off,
     * none once shown; its values our gold and the price of one, at its
     * time (38 frames after the click).
     */
    public void testTipOncePerGame() {
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        this.dutch.setGold(144);
        assertNull(ClassicGUI.cannotPayTip(t, this.dutch, tools, false, 5L));
        assertNull(ClassicGUI.cannotPayTip(t, null, tools, true, 5L));
        assertNull(ClassicGUI.cannotPayTip(t, this.dutch, null, true, 5L));
        if (t != null) {
            final ClassicAdvisorBox.Request r
                = ClassicGUI.cannotPayTip(t, this.dutch, tools, true, 5L);
            assertNotNull(r);
            assertEquals("TUTORIAL18", r.id);
            assertEquals(5L, r.showAtNanos);
            assertTrue(r.plainText(), r.plainText().contains("pro Einheit "
                + this.price + "$ kostet, wir aber nur über 144$ Gold"));
        } else {
            assertNull(ClassicGUI.cannotPayTip(null, this.dutch, tools, true, 5L));
        }
        assertFalse(ClassicGUI.tipShown(this.dutch, ClassicTips.PART));
        new ClassicGUI(null).markTip(this.dutch, ClassicTips.PART);
        assertTrue(ClassicGUI.tipShown(this.dutch, ClassicTips.PART));
        assertEquals(1 << 18, this.dutch.getClassicTips());
        assertNull("once per game", ClassicGUI.cannotPayTip(t, this.dutch, tools, true, 5L));
    }

    /** The clip's values (#54344): Werkzeuge at 2, the Handelsschiff, (0-100). */
    private static Map<String, String> clipValues() {
        final Map<String, String> v = new HashMap<>();
        v.put("STRING0", "Werkzeuge");
        v.put("NUMBER1", "2");
        v.put("STRING1", "Handelsschiff");
        v.put("NUMBER0", "100");
        return v;
    }

    /**
     * Golden (the pack and {@code -Dclassic.clips}): our boxes over the
     * playthrough-1 frames must give the frames' pixels on the box, 0 px
     * off (the original's mouse arrow, indices 0, 7, 15, excused and
     * counted): {@code @HOWMUCH4} at #54344 with "100" selected, at
     * #54434 after one Backspace ("10"), at #54556 with "50" typed;
     * {@code @TUTORIAL18} at #53010 (Werkzeuge at 2, 144 gold).
     */
    public void testGoldenAgainstPlaythrough1() throws Exception {
        final String clips = System.getProperty(ClassicTerrainGoldenTest.CLIPS_PROPERTY);
        final File dir = (clips == null) ? null : new File(clips, "playthrough-1");
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final ClassicText t = ClassicText.load(pack);
        final ClassicFont tiny = (pack == null) ? null : pack.font(ClassicFont.TINY);
        if (dir == null || !dir.isDirectory() || t == null || tiny == null) {
            System.err.println(getClass().getSimpleName()
                + ": golden check skipped, no recordings (-D"
                + ClassicTerrainGoldenTest.CLIPS_PROPERTY + ") or no pack");
            return;
        }
        final BufferedImage wood = pack.image(ClassicMenuBar.WOOD_KEY);
        final StringBuilder fails = new StringBuilder();
        int compared = 0, arrow = 0;
        // frame, typed keys ("<" a Backspace), or null for the tip
        final Object[][] cases = {
            { 54344, "" }, { 54434, "<" }, { 54556, "<<<50" }, { 53010, null },
        };
        for (Object[] c : cases) {
            final int frame = (Integer) c[0];
            final String keys = (String) c[1];
            final ClassicAdvisorBox.Request r = (keys == null)
                ? ClassicTips.request(t, ClassicTips.PART,
                                      ClassicTrade.tipValues(t, tools, 2, 144))
                : ClassicTrade.howMuchRequest(t, true, clipValues(), 100, 100);
            final ClassicAdvisorBox.Layout l = ClassicAdvisorBox.layout(r, tiny, null);
            final ClassicAdvisorBox.Bar bar = new ClassicAdvisorBox.Bar(r);
            if (keys != null) {
                for (char k : keys.toCharArray()) {
                    if (k == '<') {
                        assertTrue(bar.backspace());
                    } else {
                        assertTrue(bar.type(k, tiny));
                    }
                }
            }
            final BufferedImage ours = ClassicAdvisorBox.render(l, bar, wood, tiny);
            final File f = new File(dir, String.format("frame_%06d.png", frame));
            final BufferedImage img = ImageIO.read(f);
            assertNotNull(f.toString(), img);
            final Raster idx = (img.getColorModel() instanceof IndexColorModel)
                ? img.getRaster() : null;
            int diff = 0;
            for (int y = l.box.y; y < l.box.y + l.box.height; y++) {
                for (int x = l.box.x; x < l.box.x + l.box.width; x++) {
                    compared++;
                    if ((img.getRGB(x, y) & 0xFFFFFF) == (ours.getRGB(x, y) & 0xFFFFFF)) {
                        continue;
                    }
                    final int i = (idx == null) ? -1 : idx.getSample(x, y, 0);
                    if (i == 0 || i == 7 || i == 15) {
                        arrow++;
                    } else {
                        diff++;
                    }
                }
            }
            if (diff > 0) fails.append(" #").append(frame).append('=').append(diff);
        }
        System.out.println(getClass().getSimpleName() + ": golden check, "
            + cases.length + " frames, " + compared + " px compared, off:"
            + ((fails.length() == 0) ? " none" : fails.toString())
            + ", " + arrow + " arrow px excused");
        assertEquals("pixels off:" + fails, 0, fails.length());
        assertTrue("arrow pixels " + arrow, arrow <= 60);
    }
}
