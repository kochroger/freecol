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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;

import net.sf.freecol.FreeCol;
import net.sf.freecol.client.gui.ChoiceItem;
import net.sf.freecol.client.gui.action.ReturnToEuropeAction;
import net.sf.freecol.client.gui.panel.FreeColPanel;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.io.FreeColRules;
import net.sf.freecol.common.model.AbstractUnit;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.DiplomaticTrade;
import net.sf.freecol.common.model.Direction;
import net.sf.freecol.common.model.Europe;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoldTradeItem;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Market;
import net.sf.freecol.common.model.Monarch.MonarchAction;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.Topology;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.option.GameOptions;
import net.sf.freecol.server.ServerTestHelper;
import net.sf.freecol.server.model.LootSession;
import net.sf.freecol.server.model.ServerEurope;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.server.model.Session;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * Tests of {@link ClassicGUI} seams whose base {@code GUI} no-op broke a
 * controller flow (build spec W0a/W0b): the pre-combat question must let
 * the attack through, and the event panel must not be null.  And the
 * classic boxes' answers (W0e, W0f) and Roger's Europe question (W8a),
 * with a fake {@link ClassicGUI.Prompter}: no box can open headless.
 */
public class ClassicGUISeamTest extends FreeColTestCase {

    /**
     * Answers every box with {@link #answer} and keeps what it was asked:
     * the rows (plain), the bar's first row, the text (plain), the box.
     */
    private static final class FakePrompter implements ClassicGUI.Prompter {

        int answer = -1;
        final List<String[]> asked = new ArrayList<>();
        final List<Integer> defaults = new ArrayList<>();
        final List<String> texts = new ArrayList<>();
        final List<ClassicAdvisorBox.Request> boxes = new ArrayList<>();

        @Override
        public int ask(ClassicAdvisorBox.Request r) {
            this.boxes.add(r);
            this.asked.add(r.plainRows());
            this.defaults.add(r.defaultRow);
            this.texts.add(r.plainText());
            return this.answer;
        }
    }

    /**
     * Answers every box by pressing {@link #keys} on its real bar
     * ({@link ClassicAdvisorBox.Bar}): what the player's keys answer.
     * Keys: UP, DOWN, ENTER, ESC, SPACE, X (any other key), OUT (a click outside
     * the box: a press and a release in the letterbox).
     */
    static final class KeyPrompter implements ClassicGUI.Prompter {

        String[] keys = {};
        final List<ClassicAdvisorBox.Request> boxes = new ArrayList<>();

        /** The bar's row after the keys of the last box if it stayed open, else -2. */
        int lastRow = -2;

        KeyPrompter press(String... k) {
            this.keys = k;
            return this;
        }

        @Override
        public int ask(ClassicAdvisorBox.Request r) {
            this.boxes.add(r);
            return answer(r, this.keys);
        }

        int answer(ClassicAdvisorBox.Request r, String... ks) {
            final ClassicAdvisorBox.Bar bar = new ClassicAdvisorBox.Bar(r);
            this.lastRow = -2;
            for (String k : ks) {
                final int a;
                switch (k) {
                case "UP": a = bar.up(); break;
                case "DOWN": a = bar.down(); break;
                case "ENTER": a = bar.enter(); break;
                case "ESC": a = bar.escape(); break;
                case "SPACE": a = bar.space(); break;
                case "OUT":
                    bar.press(-1, false);
                    a = bar.release(-1, false);
                    break;
                default: a = bar.otherKey(); break;
                }
                if (a != ClassicAdvisorBox.Bar.OPEN) return a;
            }
            this.lastRow = bar.row();
            return ClassicAdvisorBox.Bar.DISMISSED;   // left open: as closed
        }
    }

    /** A classic GUI without a client that remembers the ships it sailed home. */
    private static final class SailingGUI extends ClassicGUI {

        final List<Unit> sailed = new ArrayList<>();

        SailingGUI() {
            super(null);
        }

        @Override
        void sailHome(Unit unit) {
            this.sailed.add(unit);
        }
    }

    private static final TileType ocean
        = spec().getTileType("model.tile.ocean");
    private static final TileType highSeas
        = spec().getTileType("model.tile.highSeas");

    /** The topology in use before the test. */
    private Topology savedTopology;


    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.savedTopology = Topology.current();
    }

    @Override
    protected void tearDown() throws Exception {
        Topology.setCurrent(this.savedTopology);
        super.tearDown();
    }


    /**
     * {@code GUI.confirmPreCombat} asks this whenever the default-on
     * option {@code guiShowPreCombat} is set; false cancelled every attack.
     */
    public void testPreCombatLetsTheAttackThrough() {
        final ClassicGUI gui = new ClassicGUI(null);
        assertTrue(gui.showPreCombatDialog(null, null, null));
    }

    /**
     * {@code InGameController.newLandName} adds a closing callback to the
     * event panel; it must get a panel, and the callback must run (nothing
     * is shown, so nothing will close later).
     */
    public void testEventPanelRunsClosingCallbacks() {
        final ClassicGUI gui = new ClassicGUI(null);
        final FreeColPanel panel = gui.showEventPanel("header",
            "image.flavor.event.firstLanding", null);
        assertNotNull(panel);
        final int[] runs = { 0 };
        assertSame(panel, panel.addClosingCallback(() -> runs[0]++));
        assertEquals(1, runs[0]);
        panel.addClosingCallback(null);
        assertEquals(1, runs[0]);
    }

    /**
     * W0e: Escape (and the close button) answers "no" in every classic
     * confirm, whichever option Enter takes; Enter still takes the
     * controller's default.  The confirms of the learn question, the site
     * warnings (land-locked), the rumour and the hostile action, with
     * their controller's {@code defaultOk} (the landing has its own box,
     * {@link #testLandfallBox}).
     */
    public void testEscapeAnswersNo() {
        final ClassicGUI gui = new ClassicGUI(null);
        final FakePrompter fake = new FakePrompter();
        gui.prompter = fake;
        final Player arawak = getStandardGame()
            .getPlayerByNationId("model.nation.arawak");
        final StringTemplate landLocked = StringTemplate.label("\n")
            .add("warning.landLocked");
        final Object[][] confirms = {
            { StringTemplate.template("learnSkill.text")
                  .addName("%skill%", "Pelzjäger"),
              "learnSkill.yes", "learnSkill.no", true },
            { landLocked, "buildColony.yes", "buildColony.no", true },
            { StringTemplate.key("exploreLostCityRumour.text"),
              "exploreLostCityRumour.yes", "exploreLostCityRumour.no", true },
            { StringTemplate.template("confirmHostile.peace")
                  .addStringTemplate("%nation%", arawak.getNationLabel()),
              "confirmHostile.yes", "cancel", false },
        };
        for (Object[] c : confirms) {
            final StringTemplate t = (StringTemplate) c[0];
            final String ok = (String) c[1], cancel = (String) c[2];
            final boolean defaultOk = (Boolean) c[3];
            final String what = t.getId() + " defaultOk=" + defaultOk;
            fake.answer = -1;
            assertFalse("Escape on " + what, gui.modalConfirmDialog(null, t,
                    (ImageIcon) null, ok, cancel, defaultOk));
            assertEquals(what, Messages.message(ok), last(fake.asked)[0]);
            assertEquals(what, Messages.message(cancel), last(fake.asked)[1]);
            assertEquals("Enter's option, " + what,
                         Integer.valueOf(defaultOk ? 0 : 1), last(fake.defaults));
            assertTrue("a click outside: no, " + what,
                       last(fake.boxes).outsideCancels);
            fake.answer = 0;
            assertTrue("yes on " + what, gui.modalConfirmDialog(null, t,
                    (ImageIcon) null, ok, cancel, defaultOk));
            fake.answer = 1;
            assertFalse("no on " + what, gui.modalConfirmDialog(null, t,
                    (ImageIcon) null, ok, cancel, defaultOk));
        }
        assertEquals(3 * confirms.length, fake.asked.size());

        // The same through a base GUI helper (final in GUI, it lands on the
        // override): Escape on "stop the current game?" keeps the game.
        fake.answer = -1;
        assertFalse(gui.confirmStopGame());

        // A box that could not open counts as dismissed: no.
        gui.prompter = gui::putBox;
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            assertFalse(gui.modalConfirmDialog(null,
                    StringTemplate.template("learnSkill.text")
                        .addName("%skill%", "Pelzjäger"),
                    (ImageIcon) null, "learnSkill.yes", "learnSkill.no", true));
        }

        assertTrue(ClassicGUI.confirmed(0));
        assertFalse(ClassicGUI.confirmed(1));
        assertFalse(ClassicGUI.confirmed(-1));
    }

    /**
     * W0e for the choice lists (the village boxes, which unit lands): a
     * dismissed list is null, the controllers' "cancel".
     */
    public void testEscapeCancelsAChoice() {
        final ClassicGUI gui = new ClassicGUI(null);
        final FakePrompter fake = new FakePrompter();
        gui.prompter = fake;
        final List<ChoiceItem<String>> items = new ArrayList<>();
        items.add(new ChoiceItem<>("speak", "speak"));
        items.add(new ChoiceItem<>("tribute", "tribute"));
        fake.answer = -1;
        assertNull(gui.modalChoiceDialog(null, StringTemplate.key("x"),
                                         (ImageIcon) null, "cancel", items));
        fake.answer = 1;
        assertEquals("tribute", gui.modalChoiceDialog(null,
                StringTemplate.key("x"), (ImageIcon) null, "cancel", items));
        fake.answer = 2;                     // the cancel row
        assertNull(gui.modalChoiceDialog(null, StringTemplate.key("x"),
                                         (ImageIcon) null, "cancel", items));
        assertEquals(3, fake.asked.size());
        // The rows: the choices, then the cancel row (Escape's).
        assertEquals(3, last(fake.asked).length);
        assertEquals("speak", last(fake.asked)[0]);
        assertEquals(Messages.message("cancel"), last(fake.asked)[2]);
        assertEquals(2, last(fake.boxes).cancelRow);
        assertEquals(0, last(fake.boxes).defaultRow);
        assertTrue(last(fake.boxes).outsideCancels);   // a click outside: cancel
    }

    /**
     * W7: a choice box's keys, on its real bar.  The bar starts on row 1
     * (the original's village boxes: landfall #15898, #22322) or on the
     * choice FreeCol marks as the default; Up and Down move it one row
     * (never past the ends), Enter takes its row, Escape the cancel row; a
     * greyed choice cannot be taken.  Without a cancel row Escape gives no
     * choice.
     */
    public void testChoiceBoxKeys() {
        final ClassicGUI gui = new ClassicGUI(null);
        final KeyPrompter keys = new KeyPrompter();
        gui.prompter = keys;
        final List<ChoiceItem<String>> items = new ArrayList<>();
        items.add(new ChoiceItem<>("speak", "speak"));
        items.add(new ChoiceItem<>("tribute", "tribute"));
        items.add(new ChoiceItem<>("attack", "attack"));
        final StringTemplate t = StringTemplate.key("x");
        final ImageIcon none = null;
        keys.press("ENTER");
        assertEquals("speak", gui.modalChoiceDialog(null, t, none, "cancel", items));
        keys.press("DOWN", "ENTER");
        assertEquals("tribute", gui.modalChoiceDialog(null, t, none, "cancel", items));
        keys.press("DOWN", "DOWN", "ENTER");
        assertEquals("attack", gui.modalChoiceDialog(null, t, none, "cancel", items));
        keys.press("DOWN", "DOWN", "DOWN", "DOWN", "DOWN", "ENTER");   // the last row
        assertNull(gui.modalChoiceDialog(null, t, none, "cancel", items));
        keys.press("DOWN", "UP", "UP", "UP", "ENTER");                 // the first row
        assertEquals("speak", gui.modalChoiceDialog(null, t, none, "cancel", items));
        keys.press("DOWN", "ESC");
        assertNull(gui.modalChoiceDialog(null, t, none, "cancel", items));
        keys.press("X", "Y", "ENTER");                                 // other keys: nothing
        assertEquals("speak", gui.modalChoiceDialog(null, t, none, "cancel", items));
        // FreeCol's default choice: the bar starts there.
        items.get(2).defaultOption();
        keys.press("ENTER");
        assertEquals("attack", gui.modalChoiceDialog(null, t, none, "cancel", items));
        keys.press("UP", "ENTER");
        assertEquals("tribute", gui.modalChoiceDialog(null, t, none, "cancel", items));
        // A greyed choice cannot be taken: the box stays.
        final List<ChoiceItem<String>> trade = new ArrayList<>();
        trade.add(new ChoiceItem<>("buy", "buy", false));
        trade.add(new ChoiceItem<>("sell", "sell", true));
        keys.press("ENTER");
        assertNull(gui.modalChoiceDialog(null, t, none, "cancel", trade));
        assertEquals(0, keys.lastRow);
        assertTrue(last(keys.boxes).disabled[0]);
        keys.press("ENTER", "DOWN", "ENTER");
        assertEquals("sell", gui.modalChoiceDialog(null, t, none, "cancel", trade));
        // Without a cancel key: no cancel row, Escape gives no choice.
        keys.press("ESC");
        assertNull(gui.modalChoiceDialog(null, t, none, null, items));
        assertEquals(3, last(keys.boxes).rows.size());
        assertEquals(-1, last(keys.boxes).cancelRow);
    }

    /**
     * W0f: FreeCol's question on sailing onto the high seas is answered
     * "no" without a box, so the controller makes the plain move.  Every
     * other confirm is still put.
     */
    public void testHighSeasQuestionIsSilent() {
        final ClassicGUI gui = new ClassicGUI(null);
        final FakePrompter fake = new FakePrompter();
        gui.prompter = fake;
        fake.answer = 0;   // a "yes" if it were asked
        assertFalse(gui.modalConfirmDialog(null, StringTemplate
                .template("highseas.text").addAmount("%number%", 3),
                (ImageIcon) null, "highseas.yes", "highseas.no", true));
        assertTrue(fake.asked.isEmpty());
        assertTrue(ClassicGUI.silentNo(StringTemplate.template("highseas.text")));
        assertFalse(ClassicGUI.silentNo(StringTemplate.key("learnSkill.text")));
        assertFalse(ClassicGUI.silentNo(null));
        assertTrue(gui.modalConfirmDialog(null,
                StringTemplate.key("exploreLostCityRumour.text"), (ImageIcon) null,
                "exploreLostCityRumour.yes", "exploreLostCityRumour.no", true));
        assertEquals(1, fake.asked.size());
    }

    /**
     * Roger's rule: the step east past the last drawn column (x = W-2),
     * and only that, is the Europe question's.
     */
    public void testEastPastView() {
        final int w = 58;
        assertEquals(56, ClassicHud.lastViewColumn(w));
        for (Direction d : Direction.values()) {
            final boolean east = d == Direction.E || d == Direction.NE
                || d == Direction.SE;
            // From the last drawn column onto the never-drawn ring.
            assertEquals(d.toString(), east, ClassicHud.eastPastView(w, d, 56, 57));
            // From the ring (FreeCol may still put a ship there) off the map.
            assertEquals(d.toString(), east, ClassicHud.eastPastView(w, d, 57, -1));
            // One column before the edge: a plain move (master plan section 10).
            assertFalse(d.toString(), ClassicHud.eastPastView(w, d, 55, 56));
        }
        // An isometric NE that stays in the column is a plain move.
        assertFalse(ClassicHud.eastPastView(w, Direction.NE, 56, 56));
        // The west edge is westPastView's.
        assertFalse(ClassicHud.eastPastView(w, Direction.W, 1, 0));
    }

    /**
     * E1, Roger's rule mirrored: the step west past the first drawn column
     * (x = 1), and only that, is the Europe question's too.
     */
    public void testWestPastView() {
        final int w = 58;
        assertEquals(1, ClassicHud.firstViewColumn());
        for (Direction d : Direction.values()) {
            final boolean west = d == Direction.W || d == Direction.NW
                || d == Direction.SW;
            final boolean east = d == Direction.E || d == Direction.NE
                || d == Direction.SE;
            // From the first drawn column onto the never-drawn ring.
            assertEquals(d.toString(), west, ClassicHud.westPastView(d, 1, 0));
            // From the ring (FreeCol may still put a ship there) off the map.
            assertEquals(d.toString(), west, ClassicHud.westPastView(d, 0, -1));
            // One column after the edge: a plain move.
            assertFalse(d.toString(), ClassicHud.westPastView(d, 2, 1));
            // Both edges together.
            assertEquals(d.toString(), west,
                ClassicHud.sidePastView(w, d, 1, 0));
            assertEquals(d.toString(), east,
                ClassicHud.sidePastView(w, d, 56, 57));
            assertFalse(d.toString(), ClassicHud.sidePastView(w, d, 2, 1));
            assertFalse(d.toString(), ClassicHud.sidePastView(w, d, 55, 56));
        }
        // An isometric NW that stays in the column is a plain move.
        assertFalse(ClassicHud.westPastView(Direction.NW, 1, 1));
        assertFalse(ClassicHud.westPastView(Direction.SW, 1, 1));
        // The east edge is not westPastView's.
        assertFalse(ClassicHud.westPastView(Direction.E, 56, 57));
    }

    /**
     * W8a: a ship on the high seas in the last drawn column ordered E, NE
     * or SE gets the question; "Nein" and Escape leave it with its moves,
     * "Jawohl" (Enter's row) sails it home.  Every other order, and every
     * other unit, is a plain move.
     */
    public void testEuropeQuestionAtTheEastEdge() {
        Topology.setCurrent(Topology.SQUARE);
        final Game game = getStandardGame();
        final MapBuilder builder = new MapBuilder(game);
        builder.setDimensions(20, 15).setBaseTileType(ocean)
            .setExploredByAll(true);
        for (int y = 0; y < 15; y++) {
            for (int x = 15; x < 20; x++) builder.setTileType(x, y, highSeas);
        }
        final Map map = builder.build();
        game.changeMap(map);
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final Tile edge = map.getTile(18, 7);       // x = W-2
        final Tile inner = map.getTile(17, 7);      // one column before
        final Unit ship = new ServerUnit(game, edge, dutch,
            spec().getUnitType("model.unit.merchantman"));
        final int moves = ship.getMovesLeft();
        assertTrue(moves > 0);
        assertNotNull(dutch.getEurope());

        for (Direction d : Direction.values()) {
            final boolean east = d == Direction.E || d == Direction.NE
                || d == Direction.SE;
            assertEquals(d.toString(), east, ClassicGUI.asksSailHome(ship, d));
        }

        final SailingGUI gui = new SailingGUI();
        final FakePrompter fake = new FakePrompter();
        gui.prompter = fake;
        // Nein, then Escape: nothing happens, the moves are kept.
        fake.answer = 1;
        assertTrue(gui.sailHomeKey(ship, Direction.E));
        fake.answer = -1;
        assertTrue(gui.sailHomeKey(ship, Direction.SE));
        assertTrue(gui.sailed.isEmpty());
        assertSame(edge, ship.getTile());
        assertEquals(moves, ship.getMovesLeft());
        assertEquals(2, last(fake.asked).length);
        assertEquals(Integer.valueOf(0), last(fake.defaults));
        // Jawohl: the ship sails home.
        fake.answer = 0;
        assertTrue(gui.sailHomeKey(ship, Direction.NE));
        assertEquals(List.of(ship), gui.sailed);
        assertEquals(3, fake.asked.size());
        // Every other order is a plain move, with no question.
        assertFalse(gui.sailHomeKey(ship, Direction.N));
        assertFalse(gui.sailHomeKey(ship, Direction.W));
        assertEquals(3, fake.asked.size());

        // One column before the edge, on light water: plain moves.
        ship.setLocation(inner);
        for (Direction d : Direction.values()) {
            assertFalse(d.toString(), ClassicGUI.asksSailHome(ship, d));
        }
        // At the edge but on ordinary ocean: plain moves.
        final Tile coast = map.getTile(18, 3);
        coast.setType(ocean);
        ship.setLocation(coast);
        assertFalse(ClassicGUI.asksSailHome(ship, Direction.E));
        // No moves left: no question (the controller says why).
        ship.setLocation(edge);
        ship.setMovesLeft(0);
        assertFalse(ClassicGUI.asksSailHome(ship, Direction.E));
        ship.setMovesLeft(moves);
        assertTrue(ClassicGUI.asksSailHome(ship, Direction.E));
        // A land unit is never asked.
        final Unit colonist = new ServerUnit(game, ship, dutch,
            spec().getUnitType("model.unit.freeColonist"));
        assertFalse(ClassicGUI.asksSailHome(colonist, Direction.E));
        assertFalse(ClassicGUI.asksSailHome(null, Direction.E));
        assertFalse(ClassicGUI.asksSailHome(ship, null));
    }

    /**
     * E1, the mirror of W8a (Roger): a ship on the high seas in the first
     * drawn column (x = 1) ordered W, NW or SW gets the same question;
     * "Nein" and Escape leave it with its moves, "Jawohl" sails it home.
     * Every other order there, and the west orders one column further in,
     * are plain moves.
     */
    public void testEuropeQuestionAtTheWestEdge() {
        Topology.setCurrent(Topology.SQUARE);
        final Game game = getStandardGame();
        final MapBuilder builder = new MapBuilder(game);
        builder.setDimensions(20, 15).setBaseTileType(ocean)
            .setExploredByAll(true);
        for (int y = 0; y < 15; y++) {
            for (int x = 0; x < 5; x++) builder.setTileType(x, y, highSeas);
            for (int x = 15; x < 20; x++) builder.setTileType(x, y, highSeas);
        }
        final Map map = builder.build();
        game.changeMap(map);
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final Tile edge = map.getTile(1, 7);        // the first drawn column
        final Tile inner = map.getTile(2, 7);       // one column after
        final Unit ship = new ServerUnit(game, edge, dutch,
            spec().getUnitType("model.unit.merchantman"));
        final int moves = ship.getMovesLeft();
        assertTrue(moves > 0);
        assertNotNull(dutch.getEurope());

        for (Direction d : Direction.values()) {
            final boolean west = d == Direction.W || d == Direction.NW
                || d == Direction.SW;
            assertEquals(d.toString(), west, ClassicGUI.asksSailHome(ship, d));
        }

        final SailingGUI gui = new SailingGUI();
        final FakePrompter fake = new FakePrompter();
        gui.prompter = fake;
        // Nein, then Escape: nothing happens, the moves are kept.
        fake.answer = 1;
        assertTrue(gui.sailHomeKey(ship, Direction.W));
        fake.answer = -1;
        assertTrue(gui.sailHomeKey(ship, Direction.NW));
        assertTrue(gui.sailed.isEmpty());
        assertSame(edge, ship.getTile());
        assertEquals(moves, ship.getMovesLeft());
        // The same box as at the east edge: two rows, Enter on "Jawohl".
        assertEquals(2, last(fake.asked).length);
        assertEquals(Integer.valueOf(0), last(fake.defaults));
        // Jawohl: the ship sails home.
        fake.answer = 0;
        assertTrue(gui.sailHomeKey(ship, Direction.SW));
        assertEquals(List.of(ship), gui.sailed);
        assertEquals(3, fake.asked.size());
        // Every other order is a plain move, with no question.
        assertFalse(gui.sailHomeKey(ship, Direction.N));
        assertFalse(gui.sailHomeKey(ship, Direction.E));
        assertFalse(gui.sailHomeKey(ship, Direction.NE));
        assertEquals(3, fake.asked.size());

        // On the column before the drawn map (FreeCol's x = 0, on a map
        // without the outer ring): west is off the map, and asks too.
        ship.setLocation(map.getTile(0, 7));
        assertTrue(ClassicGUI.asksSailHome(ship, Direction.W));
        assertTrue(ClassicGUI.asksSailHome(ship, Direction.SW));
        assertFalse(ClassicGUI.asksSailHome(ship, Direction.E));
        // One column after the edge, on light water: plain moves.
        ship.setLocation(inner);
        for (Direction d : Direction.values()) {
            assertFalse(d.toString(), ClassicGUI.asksSailHome(ship, d));
        }
        // At the edge but on ordinary ocean: plain moves.
        final Tile coast = map.getTile(1, 3);
        coast.setType(ocean);
        ship.setLocation(coast);
        assertFalse(ClassicGUI.asksSailHome(ship, Direction.W));
        // No moves left: no question (the controller says why).
        ship.setLocation(edge);
        ship.setMovesLeft(0);
        assertFalse(ClassicGUI.asksSailHome(ship, Direction.W));
        ship.setMovesLeft(moves);
        assertTrue(ClassicGUI.asksSailHome(ship, Direction.W));
        // A land unit is never asked.
        final Unit colonist = new ServerUnit(game, ship, dutch,
            spec().getUnitType("model.unit.freeColonist"));
        assertFalse(ClassicGUI.asksSailHome(colonist, Direction.W));

        // Isometric: W always leaves the column, a NW or SW step asks only
        // where it does (on every other row it stays in column 1).
        Topology.setCurrent(Topology.ISOMETRIC);
        int plain = 0;
        for (int y = 6; y <= 9; y++) {
            ship.setLocation(map.getTile(1, y));
            for (Direction d : new Direction[] {
                    Direction.W, Direction.NW, Direction.SW }) {
                final Tile to = ship.getTile().getNeighbourOrNull(d);
                final boolean leaves = to == null || to.getX() < 1;
                if (!leaves) plain++;
                assertEquals(d + " at y=" + y, leaves,
                    ClassicGUI.asksSailHome(ship, d));
            }
            assertTrue(ClassicGUI.asksSailHome(ship, Direction.W));
        }
        assertTrue(plain > 0);
    }

    /**
     * The question's box: GAME.TXT @SAILHOME (markup kept for the box,
     * dropped for the stopgap; the bar on @default, Escape on "Nein", the
     * admiral), else FreeCol's high-seas strings.  The test
     * file is a stand-in: the original's text is never in the repository.
     */
    public void testSailHomeText() throws Exception {
        final File dir = Files.createTempDirectory("sailhome").toFile();
        try {
            final File game = new File(dir, "GAME.TXT");
            final File names = new File(dir, "NAMES.TXT");
            final File labels = new File(dir, "LABELS.TXT");
            Files.write(game.toPath(), ("@SAILHOME\r\n@width=230\r\n@default=1\r\n"
                + "We are on {open} water.\r\nGo  home?\r\n\r\n"
                + "Yes, home.\r\nNo, stay.\r\n\r\n@END\r\n")
                .getBytes(StandardCharsets.US_ASCII));
            Files.write(names.toPath(), "@END\r\n".getBytes(StandardCharsets.US_ASCII));
            Files.write(labels.toPath(), "@END\r\n".getBytes(StandardCharsets.US_ASCII));
            final ClassicText t = ClassicText.fromFiles(game, names, labels);
            final ClassicAdvisorBox.Request q = ClassicGUI.sailHomeRequest(t, null);
            // The box keeps the markup (gold), its stopgap drops it.
            assertEquals(List.of(List.of("We are on {open} water.", "Go  home?")),
                         q.paragraphs);
            assertEquals("We are on open water. Go home?", q.plainText());
            assertEquals(List.of("Yes, home.", "No, stay."), q.rows);
            assertEquals(230, q.width);
            assertEquals(0, q.defaultRow);
            assertEquals(1, q.cancelRow);
            assertSame(ClassicAdvisorBox.Portrait.ADMIRAL, q.portrait);
            assertEquals(ClassicGUI.SAIL_HOME_SECTION, q.id);
            assertTrue(q.outsideCancels);              // a click outside: "Nein"
            assertEquals(1, new KeyPrompter().answer(q, "OUT"));
        } finally {
            for (File f : dir.listFiles()) f.delete();
            dir.delete();
        }
        final ClassicAdvisorBox.Request f = ClassicGUI.sailHomeRequest(null, null);
        assertEquals(Messages.message("highseas.yes"), f.plainRows()[0]);
        assertEquals(Messages.message("highseas.no"), f.plainRows()[1]);
        assertEquals(0, f.defaultRow);
        assertEquals(1, f.cancelRow);
        assertFalse(f.plainText().isEmpty());
        assertSame(ClassicAdvisorBox.Portrait.ADMIRAL, f.portrait);
        assertTrue(f.outsideCancels);
    }

    /**
     * The king's tax rise (C FINAL trap 1, build spec W24): the bar starts
     * on the original's first row, "Den königlichen Ring küssen" =
     * FreeCol's "yes" (clip005 #17489, clip006 #7380), so Enter kisses the
     * ring; Down and Enter, "no", hold the party.  Escape does nothing in
     * the King's decision boxes (Roger: "man muss sich entscheiden", G1):
     * the box and its bar stay, and Enter then takes the barred row; a box
     * that closed without a row answers its first row, never the party.
     * The mercenary offers list the "no" first with the bar on it (the
     * original's @MERCENARIES); a notice has no rows and any key, Escape
     * too, dismisses it.  The King stands at the left (W7's King exception).
     */
    public void testKingsBoxEnterKissesTheRing() {
        final ClassicGUI gui = new ClassicGUI(null);
        final FakePrompter fake = new FakePrompter();
        gui.prompter = fake;
        final List<Boolean> answers = new ArrayList<>();
        final StringTemplate t = StringTemplate.template("x")
            .addAmount("%amount%", 5);
        for (MonarchAction a : new MonarchAction[] {
                MonarchAction.RAISE_TAX_ACT, MonarchAction.RAISE_TAX_WAR }) {
            assertTrue(a.toString(), ClassicGUI.monarchEnterAccepts(a));
            fake.answer = 0;
            gui.showMonarchDialog(a, t, "model.nation.dutch", answers::add);
            assertEquals(a.toString(), 2, last(fake.asked).length);
            assertEquals(Messages.message(a.getYesKey()), last(fake.asked)[0]);
            assertEquals(Messages.message(a.getNoKey()), last(fake.asked)[1]);
            assertEquals("Enter's row, " + a, Integer.valueOf(0), last(fake.defaults));
            assertEquals(1, last(fake.boxes).cancelRow);
            assertSame(ClassicAdvisorBox.Portrait.KING, last(fake.boxes).portrait);
            assertFalse(a.toString(), last(fake.boxes).escapes);
            assertEquals(ClassicAdvisorBox.Bar.OPEN, last(fake.boxes).escapeAnswer());
            assertFalse(last(fake.boxes).outsideCancels);
            assertEquals(Boolean.TRUE, last(answers));        // the ring
            fake.answer = 1;
            gui.showMonarchDialog(a, t, "model.nation.dutch", answers::add);
            assertEquals(Boolean.FALSE, last(answers));       // the party
            fake.answer = ClassicAdvisorBox.Bar.DISMISSED;
            gui.showMonarchDialog(a, t, "model.nation.dutch", answers::add);
            assertEquals("closed without a row, " + a, Boolean.TRUE, last(answers));
        }
        for (MonarchAction a : new MonarchAction[] {
                MonarchAction.MONARCH_MERCENARIES,
                MonarchAction.HESSIAN_MERCENARIES }) {
            assertFalse(a.toString(), ClassicGUI.monarchEnterAccepts(a));
            fake.answer = 0;
            gui.showMonarchDialog(a, t, "model.nation.dutch", answers::add);
            assertEquals(a.toString(), 2, last(fake.asked).length);
            assertEquals(Messages.message(a.getNoKey()), last(fake.asked)[0]);
            assertEquals(Messages.message(a.getYesKey()), last(fake.asked)[1]);
            assertEquals("Enter's row, " + a, Integer.valueOf(0), last(fake.defaults));
            assertEquals(0, last(fake.boxes).cancelRow);
            assertFalse(a.toString(), last(fake.boxes).escapes);
            assertEquals(ClassicAdvisorBox.Bar.OPEN, last(fake.boxes).escapeAnswer());
            assertFalse(last(fake.boxes).outsideCancels);
            assertEquals(Boolean.FALSE, last(answers));
            fake.answer = 1;
            gui.showMonarchDialog(a, t, "model.nation.dutch", answers::add);
            assertEquals(Boolean.TRUE, last(answers));
            fake.answer = ClassicAdvisorBox.Bar.DISMISSED;
            gui.showMonarchDialog(a, t, "model.nation.dutch", answers::add);
            assertEquals("closed without a row, " + a, Boolean.FALSE, last(answers));
        }
        // A notice (no "yes"): no rows, answered "no"; Escape closes it.
        fake.answer = 0;
        gui.showMonarchDialog(MonarchAction.LOWER_TAX_WAR, t,
                              "model.nation.dutch", answers::add);
        assertEquals(0, last(fake.asked).length);
        assertTrue(last(fake.boxes).isNotice());
        assertEquals(0, last(fake.boxes).escapeAnswer());
        assertEquals(Integer.valueOf(-1), last(fake.defaults));
        assertEquals(Boolean.FALSE, last(answers));
        assertFalse(ClassicGUI.monarchEnterAccepts(null));
        assertEquals(13, fake.asked.size());
        assertEquals(13, answers.size());

        // The same with the player's keys on the real bar.
        final KeyPrompter keys = new KeyPrompter();
        gui.prompter = keys;
        final MonarchAction tax = MonarchAction.RAISE_TAX_ACT;
        final MonarchAction merc = MonarchAction.MONARCH_MERCENARIES;
        final Object[][] cases = {
            { tax, new String[] { "ENTER" }, true },
            { tax, new String[] { "DOWN", "ENTER" }, false },
            { tax, new String[] { "DOWN", "UP", "ENTER" }, true },
            { tax, new String[] { "UP", "ENTER" }, true },
            // G1: Escape does nothing; Enter then takes the barred row.
            { tax, new String[] { "ESC", "ENTER" }, true },
            { tax, new String[] { "DOWN", "ESC", "ENTER" }, false },
            { tax, new String[] { "ESC", "OUT", "ESC", "ENTER" }, true },
            { tax, new String[] { "ESC", "DOWN", "ENTER" }, false },
            { merc, new String[] { "ENTER" }, false },
            { merc, new String[] { "DOWN", "ENTER" }, true },
            { merc, new String[] { "ESC", "ENTER" }, false },
            { merc, new String[] { "ESC", "DOWN", "ENTER" }, true },
            { merc, new String[] { "DOWN", "ESC", "ENTER" }, true },
            { MonarchAction.LOWER_TAX_WAR, new String[] { "X" }, false },
            { MonarchAction.LOWER_TAX_WAR, new String[] { "ENTER" }, false },
            { MonarchAction.LOWER_TAX_WAR, new String[] { "ESC" }, false },
            // E acceptance must-fix: a click outside answers nothing.
            { tax, new String[] { "OUT", "ENTER" }, true },
            { tax, new String[] { "OUT", "OUT", "UP", "ENTER" }, true },
            { tax, new String[] { "DOWN", "OUT", "ENTER" }, false },
            { merc, new String[] { "OUT", "ENTER" }, false },
            { merc, new String[] { "OUT", "DOWN", "ENTER" }, true },
            { MonarchAction.LOWER_TAX_WAR, new String[] { "OUT" }, false },
        };
        for (Object[] c : cases) {
            keys.press((String[]) c[1]);
            gui.showMonarchDialog((MonarchAction) c[0], t, "model.nation.dutch",
                                  answers::add);
            assertEquals(c[0] + " " + String.join(" ", (String[]) c[1]),
                         c[2], last(answers));
            assertFalse(c[0].toString(), last(keys.boxes).outsideCancels);
        }
        // A click outside alone leaves the box up, the bar where it was;
        // so does Escape.
        for (MonarchAction a : new MonarchAction[] { tax, merc }) {
            keys.press("OUT");
            gui.showMonarchDialog(a, t, "model.nation.dutch", answers::add);
            assertEquals(a.toString(), 0, keys.lastRow);
            keys.press("DOWN", "OUT", "OUT");
            gui.showMonarchDialog(a, t, "model.nation.dutch", answers::add);
            assertEquals(a.toString(), 1, keys.lastRow);
            keys.press("ESC");
            gui.showMonarchDialog(a, t, "model.nation.dutch", answers::add);
            assertEquals(a.toString(), 0, keys.lastRow);
            keys.press("DOWN", "ESC", "ESC");
            gui.showMonarchDialog(a, t, "model.nation.dutch", answers::add);
            assertEquals(a.toString(), 1, keys.lastRow);
        }
        final ClassicAdvisorBox.Request m = last(keys.boxes);
        assertEquals(ClassicAdvisorBox.Bar.DISMISSED, keys.answer(m, "OUT", "ESC"));
        assertEquals(m.defaultRow, keys.lastRow);      // still up, on "Nein danke"
    }

    /**
     * G1: a King's box reported as still open (a stopgap list, a fake) is
     * asked again until a row is taken; the handler is called once, with
     * that row's answer.  One that never answers ends after the guard with
     * its first row's answer, never the party.
     */
    public void testKingsBoxAsksAgainWhileOpen() {
        final ClassicGUI gui = new ClassicGUI(null);
        final int[] asks = { 0 };
        final int[] script = { ClassicAdvisorBox.Bar.OPEN,
                               ClassicAdvisorBox.Bar.OPEN, 1 };
        gui.prompter = r -> script[Math.min(asks[0]++, script.length - 1)];
        final List<Boolean> answers = new ArrayList<>();
        final StringTemplate t = StringTemplate.template("x").addAmount("%amount%", 5);
        gui.showMonarchDialog(MonarchAction.RAISE_TAX_ACT, t, "model.nation.dutch",
                              answers::add);
        assertEquals(3, asks[0]);
        assertEquals(1, answers.size());
        assertEquals(Boolean.FALSE, answers.get(0));   // row 1: the party, chosen
        asks[0] = 0;
        gui.prompter = r -> {
            asks[0]++;
            return ClassicAdvisorBox.Bar.OPEN;
        };
        gui.showMonarchDialog(MonarchAction.RAISE_TAX_WAR, t, "model.nation.dutch",
                              answers::add);
        assertEquals(ClassicGUI.ASK_ROUNDS, asks[0]);
        assertEquals(2, answers.size());
        assertEquals(Boolean.TRUE, answers.get(1));    // the ring
        gui.showMonarchDialog(MonarchAction.HESSIAN_MERCENARIES, t,
                              "model.nation.dutch", answers::add);
        assertEquals(3, answers.size());
        assertEquals(Boolean.FALSE, answers.get(2));   // "Nein danke"
    }

    /**
     * G1: a King's box that ends without a row answers its first row (the
     * ring at a tax rise, "Nein danke" at an offer), also when the box
     * throws (the handler runs in a {@code finally}); the boxes with
     * Escape (a first contact, a demand) answer "no" as before.
     */
    public void testKingsBoxWithoutARow() {
        final ClassicGUI gui = new ClassicGUI(null);
        final List<Boolean> answers = new ArrayList<>();
        final StringTemplate t = StringTemplate.template("x").addAmount("%amount%", 5);
        final MonarchAction[] acts = { MonarchAction.RAISE_TAX_ACT,
            MonarchAction.RAISE_TAX_WAR, MonarchAction.MONARCH_MERCENARIES,
            MonarchAction.HESSIAN_MERCENARIES };
        final boolean[] expect = { true, true, false, false };
        for (int i = 0; i < acts.length; i++) {
            gui.prompter = r -> ClassicAdvisorBox.Bar.DISMISSED;
            gui.showMonarchDialog(acts[i], t, "model.nation.dutch", answers::add);
            assertEquals(acts[i] + " dismissed", Boolean.valueOf(expect[i]),
                         last(answers));
            gui.prompter = r -> {
                throw new IllegalStateException("the box failed (test)");
            };
            final int before = answers.size();
            gui.showMonarchDialog(acts[i], t, "model.nation.dutch", answers::add);
            assertEquals(before + 1, answers.size());
            assertEquals(acts[i] + " failed", Boolean.valueOf(expect[i]),
                         last(answers));
        }
        // The helper itself: a row is the row's answer; no row, the first
        // row in a box without Escape, else "no".
        final ClassicAdvisorBox.Request king = ClassicAdvisorBox.Request
            .builder("king").freeColText("x").rows("ja", "nein").defaultRow(0)
            .cancelRow(1).noEscape().build();
        assertTrue(ClassicGUI.eventAnswer(king, 0, 0));
        assertFalse(ClassicGUI.eventAnswer(king, 0, 1));
        assertTrue(ClassicGUI.eventAnswer(king, 0, ClassicAdvisorBox.Bar.DISMISSED));
        assertTrue(ClassicGUI.eventAnswer(king, 0, ClassicAdvisorBox.Bar.OPEN));
        final ClassicAdvisorBox.Request contact = ClassicAdvisorBox.Request
            .builder("contact").freeColText("x").rows("ja", "nein").defaultRow(0)
            .cancelRow(1).build();
        assertFalse(ClassicGUI.eventAnswer(contact, 0, ClassicAdvisorBox.Bar.DISMISSED));
        assertFalse(ClassicGUI.eventAnswer(contact, 0, 1));
        assertTrue(ClassicGUI.eventAnswer(contact, 0, 0));
        assertFalse(ClassicGUI.eventAnswer(king, -1, 0));
    }

    /**
     * G1: the stopgap window of a box without Escape (the King's
     * decisions, the father and recruit boxes) has no Escape binding; the
     * others keep it.  The panel is built headless, the window is not
     * opened.
     */
    public void testStopgapWithoutEscape() {
        final List<ClassicDialog.Page> pages = List.of(new ClassicDialog.Page("x", null));
        final String[] rows = { "a", "b" };
        final javax.swing.KeyStroke esc = javax.swing.KeyStroke.getKeyStroke("ESCAPE");
        final javax.swing.KeyStroke enter = javax.swing.KeyStroke.getKeyStroke("ENTER");
        final ClassicDialog fixed = new ClassicDialog(pages, rows, 0, false);
        final javax.swing.InputMap fim = fixed.getInputMap(
            javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        assertNull(fim.get(esc));
        assertNull(fixed.getActionMap().get("classic_dialogCancel"));
        assertEquals("classic_dialogDefault", fim.get(enter));
        final ClassicDialog plain = new ClassicDialog(pages, rows, 0, true);
        final javax.swing.InputMap pim = plain.getInputMap(
            javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        assertEquals("classic_dialogCancel", pim.get(esc));
        assertEquals("classic_dialogDefault", pim.get(enter));
    }

    /**
     * The stopgap window's plates (build spec W20): the pointer alone
     * lights nothing and has no mouse-motion listener at all; a left press
     * marks a plate (painted lighter), the release on the same plate takes
     * it, a release elsewhere only unmarks; the right button does nothing.
     */
    public void testStopgapPressMarksReleaseTakes() {
        final List<ClassicDialog.Page> pages = List.of(new ClassicDialog.Page("x", null));
        final ClassicDialog d = new ClassicDialog(pages, new String[] { "aaaa", "bbbb" },
                                                  0, true);
        assertEquals(0, d.getMouseMotionListeners().length);
        final java.awt.Dimension size = d.getPreferredSize();
        d.setSize(size);
        final java.awt.image.BufferedImage before = paintDialog(d);
        final java.awt.Rectangle p0 = d.plateBounds(0), p1 = d.plateBounds(1);
        assertNotNull(p0);
        assertNotNull(p1);
        final int x0 = p0.x + p0.width / 2, y0 = p0.y + p0.height / 2;
        final int x1 = p1.x + p1.width / 2, y1 = p1.y + p1.height / 2;
        // A press on plate 0 marks it, its plate turns lighter.
        dialogMouse(d, java.awt.event.MouseEvent.MOUSE_PRESSED, x0, y0, true);
        assertEquals(0, d.pressedPlate());
        assertEquals(-1, d.chosen());
        final java.awt.image.BufferedImage marked = paintDialog(d);
        assertFalse(before.getRGB(p0.x + 3, p0.y + 3) == marked.getRGB(p0.x + 3, p0.y + 3));
        assertEquals(before.getRGB(p1.x + 3, p1.y + 3), marked.getRGB(p1.x + 3, p1.y + 3));
        // Released over plate 1: nothing is taken, the mark goes.
        dialogMouse(d, java.awt.event.MouseEvent.MOUSE_RELEASED, x1, y1, true);
        assertEquals(-1, d.pressedPlate());
        assertEquals(-1, d.chosen());
        assertEquals(before.getRGB(p0.x + 3, p0.y + 3),
                     paintDialog(d).getRGB(p0.x + 3, p0.y + 3));
        // The right button marks nothing.
        dialogMouse(d, java.awt.event.MouseEvent.MOUSE_PRESSED, x1, y1, false);
        assertEquals(-1, d.pressedPlate());
        // Press and release on plate 1: taken.
        dialogMouse(d, java.awt.event.MouseEvent.MOUSE_PRESSED, x1, y1, true);
        assertEquals(1, d.pressedPlate());
        dialogMouse(d, java.awt.event.MouseEvent.MOUSE_RELEASED, x1, y1, true);
        assertEquals(1, d.chosen());
    }

    private static java.awt.image.BufferedImage paintDialog(ClassicDialog d) {
        final java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
            d.getWidth(), d.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
        final java.awt.Graphics2D g = img.createGraphics();
        try {
            d.paint(g);
        } finally {
            g.dispose();
        }
        return img;
    }

    private static void dialogMouse(ClassicDialog d, int id, int x, int y, boolean left) {
        final java.awt.event.MouseEvent e = new java.awt.event.MouseEvent(d, id,
            System.currentTimeMillis(), (left && id == java.awt.event.MouseEvent.MOUSE_PRESSED)
                ? java.awt.event.InputEvent.BUTTON1_DOWN_MASK
                : (id == java.awt.event.MouseEvent.MOUSE_PRESSED)
                    ? java.awt.event.InputEvent.BUTTON3_DOWN_MASK : 0,
            x, y, 1, false, left ? java.awt.event.MouseEvent.BUTTON1
                                 : java.awt.event.MouseEvent.BUTTON3);
        for (java.awt.event.MouseListener l : d.getMouseListeners()) {
            if (id == java.awt.event.MouseEvent.MOUSE_PRESSED) l.mousePressed(e);
            if (id == java.awt.event.MouseEvent.MOUSE_RELEASED) l.mouseReleased(e);
        }
    }

    /**
     * The natives' demand at a colony (C FINAL trap 1): the box lists the
     * refusal first with the bar on it, as the original's @INDIANGOLD,
     * @WANTSTUFF, @INDIANBEGFOOD do (no @default; D acceptance D5), so
     * Enter and Escape refuse and only Down then Enter pays.  The first
     * contact box, through the same code, takes "Ja" (the peace) on Enter
     * (GAME.TXT @INDIANWELCOME lists "Ja" first, no @default; FreeCol's
     * own default), Escape still answers "no" (D acceptance review), and
     * the tribe's chief stands at the box (W7).
     */
    public void testNativeDemandEnterRefuses() {
        final Game game = getStandardGame();
        final Map map = getTestMap(true);
        game.changeMap(map);
        final Colony colony = createStandardColony();
        final Player inca = game.getPlayerByNationId("model.nation.inca");
        final Tile near = colony.getTile().getNeighbourOrNull(Direction.N);
        final Unit brave = new ServerUnit(game, near, inca,
            spec().getUnitType("model.unit.brave"));
        final ClassicGUI gui = new ClassicGUI(null) {
                @Override
                java.awt.Image demandIcon(Colony c) {
                    return null;   // no image resources in the test
                }
            };
        final FakePrompter fake = new FakePrompter();
        gui.prompter = fake;
        final List<Boolean> answers = new ArrayList<>();
        final GoodsType[] demands = { null,
            spec().getGoodsType("model.goods.food"),
            spec().getGoodsType("model.goods.furs") };
        final String[] yes = { "accept", "indianDemand.food.yes", "accept" };
        final String[] no = { "indianDemand.gold.no", "indianDemand.food.no",
                              "indianDemand.other.no" };
        for (int i = 0; i < demands.length; i++) {
            final String what = (demands[i] == null) ? "gold"
                : demands[i].getId();
            fake.answer = 0;
            gui.showNativeDemandDialog(brave, colony, demands[i], 50,
                                       answers::add);
            assertEquals(what, Messages.message(no[i]), last(fake.asked)[0]);
            assertEquals(what, Messages.message(yes[i]), last(fake.asked)[1]);
            assertEquals("Enter's row, " + what, Integer.valueOf(0),
                         last(fake.defaults));
            assertEquals(0, last(fake.boxes).cancelRow);
            assertEquals(what, Boolean.FALSE, last(answers));
            fake.answer = -1;
            gui.showNativeDemandDialog(brave, colony, demands[i], 50,
                                       answers::add);
            assertEquals("Escape, " + what, Boolean.FALSE, last(answers));
            fake.answer = 1;
            gui.showNativeDemandDialog(brave, colony, demands[i], 50,
                                       answers::add);
            assertEquals("yes, " + what, Boolean.TRUE, last(answers));
        }
        final Player dutch = colony.getOwner();
        fake.answer = 0;                   // Enter: its row 0, "Ja"
        gui.showFirstContactDialog(dutch, inca, null, 3, answers::add);
        assertEquals(Messages.message("yes"), last(fake.asked)[0]);
        assertEquals(Messages.message("no"), last(fake.asked)[1]);
        assertEquals("Enter's row, first contact", Integer.valueOf(0),
                     last(fake.defaults));
        assertEquals(1, last(fake.boxes).cancelRow);
        assertEquals("IND0A0.SS.000", last(fake.boxes).portrait.sprite);
        assertSame(ClassicAdvisorBox.Portrait.Kind.CHIEF,
                   last(fake.boxes).portrait.kind);
        assertEquals(Boolean.TRUE, last(answers));
        fake.answer = -1;                  // Escape: no
        gui.showFirstContactDialog(dutch, inca, null, 3, answers::add);
        assertEquals("Escape, first contact", Boolean.FALSE, last(answers));
        fake.answer = 1;
        gui.showFirstContactDialog(dutch, inca, null, 3, answers::add);
        assertEquals(Boolean.FALSE, last(answers));
        assertEquals(3 * demands.length + 3, answers.size());

        // The tribes and their chiefs: NAMES.TXT @TRIBES order.
        assertEquals(2, ClassicGUI.tribeIndex(
            game.getPlayerByNationId("model.nation.arawak")));
        assertEquals(6, ClassicGUI.tribeIndex(
            game.getPlayerByNationId("model.nation.sioux")));
        assertEquals(-1, ClassicGUI.tribeIndex(dutch));
        assertEquals(-1, ClassicGUI.tribeIndex(null));

        // The same with the player's keys on the real bar.
        final KeyPrompter keys = new KeyPrompter();
        gui.prompter = keys;
        final String[][] refuse = { { "ENTER" }, { "ESC" }, { "DOWN", "ESC" },
                                    { "DOWN", "UP", "ENTER" }, { "UP", "ENTER" } };
        for (String[] k : refuse) {
            keys.press(k);
            gui.showNativeDemandDialog(brave, colony, null, 50, answers::add);
            assertEquals("demand " + String.join(" ", k), Boolean.FALSE, last(answers));
        }
        keys.press("DOWN", "ENTER");
        gui.showNativeDemandDialog(brave, colony, null, 50, answers::add);
        assertEquals(Boolean.TRUE, last(answers));
        keys.press("DOWN", "DOWN", "ENTER");          // no row past the last
        gui.showNativeDemandDialog(brave, colony, null, 50, answers::add);
        assertEquals(Boolean.TRUE, last(answers));
        keys.press("ENTER");
        gui.showFirstContactDialog(dutch, inca, null, 3, answers::add);
        assertEquals(Boolean.TRUE, last(answers));
        keys.press("DOWN", "ENTER");
        gui.showFirstContactDialog(dutch, inca, null, 3, answers::add);
        assertEquals(Boolean.FALSE, last(answers));
        keys.press("ESC");
        gui.showFirstContactDialog(dutch, inca, null, 3, answers::add);
        assertEquals(Boolean.FALSE, last(answers));
        // G1 leaves them as they are: Escape answers "Nein" (not answered
        // by Roger yet), unlike the King's decisions.
        assertTrue(last(keys.boxes).escapes);
        assertEquals(1, last(keys.boxes).escapeAnswer());
        keys.press("DOWN", "ESC");
        gui.showNativeDemandDialog(brave, colony, null, 50, answers::add);
        assertEquals(Boolean.FALSE, last(answers));
        assertTrue(last(keys.boxes).escapes);
        assertEquals(0, last(keys.boxes).escapeAnswer());

        // E acceptance must-fix: a click outside answers neither box.
        keys.press("OUT");
        gui.showNativeDemandDialog(brave, colony, null, 50, answers::add);
        assertFalse(last(keys.boxes).outsideCancels);
        assertEquals(0, keys.lastRow);                 // still up, on the refusal
        keys.press("OUT", "DOWN", "ENTER");
        gui.showNativeDemandDialog(brave, colony, null, 50, answers::add);
        assertEquals(Boolean.TRUE, last(answers));
        keys.press("OUT");
        gui.showFirstContactDialog(dutch, inca, null, 3, answers::add);
        assertFalse(last(keys.boxes).outsideCancels);
        assertEquals(0, keys.lastRow);                 // still up, on "Ja"
        keys.press("OUT", "OUT", "ENTER");
        gui.showFirstContactDialog(dutch, inca, null, 3, answers::add);
        assertEquals("peace after stray clicks", Boolean.TRUE, last(answers));
    }

    /**
     * W7: every confirm's keys on its real bar (the learn question, the
     * site warning, the landing, the rumour, the hostile action): the bar
     * starts on FreeCol's default ({@code defaultOk}: "yes" or "no"), Up
     * and Down move it one row, Enter takes its row, Escape is "no".
     */
    public void testConfirmBoxKeys() {
        final ClassicGUI gui = new ClassicGUI(null);
        final KeyPrompter keys = new KeyPrompter();
        gui.prompter = keys;
        final StringTemplate learn = StringTemplate.template("learnSkill.text")
            .addName("%skill%", "Pelzjäger");
        final StringTemplate hostile = StringTemplate.key("confirmHostile.peace");
        final Object[][] cases = {
            // defaultOk = true: the bar on "yes"
            { learn, true, new String[] { "ENTER" }, true },
            { learn, true, new String[] { "ESC" }, false },
            { learn, true, new String[] { "DOWN", "ENTER" }, false },
            { learn, true, new String[] { "DOWN", "UP", "ENTER" }, true },
            { learn, true, new String[] { "UP", "UP", "ENTER" }, true },
            { learn, true, new String[] { "DOWN", "DOWN", "ENTER" }, false },
            { learn, true, new String[] { "X", "ENTER" }, true },
            // defaultOk = false: the bar on "no"
            { hostile, false, new String[] { "ENTER" }, false },
            { hostile, false, new String[] { "ESC" }, false },
            { hostile, false, new String[] { "UP", "ENTER" }, true },
            { hostile, false, new String[] { "UP", "ESC" }, false },
            { hostile, false, new String[] { "UP", "DOWN", "ENTER" }, false },
        };
        for (Object[] c : cases) {
            keys.press((String[]) c[2]);
            final boolean got = gui.modalConfirmDialog(null, (StringTemplate) c[0],
                (ImageIcon) null, "ok", "cancel", (Boolean) c[1]);
            assertEquals(((StringTemplate) c[0]).getId() + " defaultOk=" + c[1]
                + " " + String.join(" ", (String[]) c[2]), c[3], got);
            final ClassicAdvisorBox.Request r = last(keys.boxes);
            assertEquals(2, r.rows.size());
            assertEquals(1, r.cancelRow);
            assertEquals(((Boolean) c[1]) ? 0 : 1, r.defaultRow);
            assertTrue(r.id.startsWith("confirm "));
        }
    }

    /**
     * W7 for the Europe question: on its real bar, Enter is "Jawohl" (the
     * ship sails), Down then Enter is "Nein", Escape is "Nein"; Up never
     * goes past "Jawohl".
     */
    public void testSailHomeBoxKeys() {
        Topology.setCurrent(Topology.SQUARE);
        final Game game = getStandardGame();
        final MapBuilder builder = new MapBuilder(game);
        builder.setDimensions(20, 15).setBaseTileType(ocean)
            .setExploredByAll(true);
        for (int y = 0; y < 15; y++) {
            for (int x = 15; x < 20; x++) builder.setTileType(x, y, highSeas);
        }
        final Map map = builder.build();
        game.changeMap(map);
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final Unit ship = new ServerUnit(game, map.getTile(18, 7), dutch,
            spec().getUnitType("model.unit.merchantman"));
        final SailingGUI gui = new SailingGUI();
        final KeyPrompter keys = new KeyPrompter();
        gui.prompter = keys;
        final Object[][] cases = {
            { new String[] { "ENTER" }, 1 },
            { new String[] { "DOWN", "ENTER" }, 0 },
            { new String[] { "ESC" }, 0 },
            { new String[] { "DOWN", "ESC" }, 0 },
            { new String[] { "DOWN", "UP", "ENTER" }, 1 },
            { new String[] { "UP", "ENTER" }, 1 },
            { new String[] { "DOWN", "DOWN", "ENTER" }, 0 },
            { new String[] { "X", "ENTER" }, 1 },
        };
        for (Object[] c : cases) {
            gui.sailed.clear();
            keys.press((String[]) c[0]);
            assertTrue(gui.sailHomeKey(ship, Direction.E));
            assertEquals(String.join(" ", (String[]) c[0]), c[1], gui.sailed.size());
            assertSame(ClassicAdvisorBox.Portrait.ADMIRAL, last(keys.boxes).portrait);
        }
    }

    /**
     * E3 (house rule, levi): a defeat ends the game.  The controller asks
     * no revenge question; it shows FreeCol's game-over words in a notice
     * box (any key closes it) and then logs out.
     */
    public void testGameOverNotice() {
        final ClassicGUI gui = new ClassicGUI(null);
        final FakePrompter fake = new FakePrompter();
        gui.prompter = fake;
        gui.showGameOverPanel(StringTemplate.template("defeatedGameOver.text"));
        assertEquals(1, fake.boxes.size());
        assertTrue(last(fake.boxes).isNotice());
        assertEquals("game-over", last(fake.boxes).id);
        assertEquals(Messages.message("defeatedGameOver.text"), last(fake.texts));
        assertFalse(Messages.message("defeatedGameOver.text")
                    .equals("defeatedGameOver.text"));
    }

    /**
     * E3 (R1a): the Classic UI's new games play the "levi" rules, unless
     * {@code --rules} names others; the default rules, which name the
     * options folder, stay "freecol".  NEUE WELT loads them with the
     * chosen difficulty.
     */
    public void testNewGamesPlayLevi() throws Exception {
        assertEquals("levi", FreeCol.CLASSIC_RULES);
        assertEquals("levi", FreeCol.newGameRules(true, null));
        assertEquals("freecol", FreeCol.newGameRules(false, null));
        assertEquals("classic", FreeCol.newGameRules(true, "classic"));
        assertEquals("the options folder does not move",
                     "freecol", FreeCol.getRules());
        final Specification spec = FreeCol.loadSpecification(
            FreeColRules.getFreeColRulesFile(FreeCol.newGameRules(true, null)),
            null, "model.difficulty.veryHard");
        assertNotNull(spec);
        assertEquals("levi", spec.getId());
        assertEquals("model.difficulty.veryHard", spec.getDifficultyLevel());
        assertFalse(spec.getBoolean(GameOptions.REVENGE_MODE));
    }

    /**
     * E3: the name screen's leader may not be another nation's player's
     * name.  In levi the AI players are the original's leaders, so the
     * player's own leader (the name screen's default) is free, the others
     * are not; the rulers are free there.  In freecol the AIs are the
     * rulers.
     */
    public void testFreePlayerNames() {
        final Specification levi = spec("levi");
        final String dutch = "model.nation.dutch";
        final String english = "model.nation.english";
        assertTrue(ClassicGUI.isFreePlayerName(levi, "Michiel De Ruyter", dutch));
        assertFalse(ClassicGUI.isFreePlayerName(levi, "Walter Raleigh", dutch));
        assertTrue(ClassicGUI.isFreePlayerName(levi, "Walter Raleigh", english));
        assertFalse(ClassicGUI.isFreePlayerName(levi, "Michiel De Ruyter", english));
        assertTrue(ClassicGUI.isFreePlayerName(levi, "Elizabeth I", dutch));
        assertTrue(ClassicGUI.isFreePlayerName(levi, "Roger", dutch));
        assertFalse(ClassicGUI.isFreePlayerName(levi, "", dutch));
        assertFalse(ClassicGUI.isFreePlayerName(levi, "mapEditor", dutch));
        // A REF player keeps the REF's ruler.
        assertFalse(ClassicGUI.isFreePlayerName(levi, Messages.message(
            levi.getNation("model.nation.dutchREF").getRulerNameKey()), dutch));

        final Specification freecol = spec("freecol");
        assertFalse(ClassicGUI.isFreePlayerName(freecol, "Elizabeth I", dutch));
        assertTrue(ClassicGUI.isFreePlayerName(freecol, "Walter Raleigh", dutch));
        assertTrue(ClassicGUI.isFreePlayerName(freecol, "William I", dutch));
    }

    /**
     * W7: the notices.  Each model message is a box of its own, without
     * rows, one after the other in their order; the error notice runs its
     * callback when it closes.  Any key dismisses a notice.
     */
    public void testNoticesOneBoxEach() {
        final ClassicGUI gui = new ClassicGUI(null);
        final FakePrompter fake = new FakePrompter();
        gui.prompter = fake;
        final int[] ran = { 0 };
        gui.showErrorPanel("broken", () -> ran[0]++);
        assertEquals(1, ran[0]);
        assertEquals(1, fake.boxes.size());
        assertTrue(last(fake.boxes).isNotice());
        assertEquals("broken", last(fake.texts));
        assertEquals("error", last(fake.boxes).id);
        // The callback runs even if the box fails.
        gui.prompter = r -> {
            throw new IllegalStateException("no box");
        };
        gui.showErrorPanel("broken again", () -> ran[0]++);
        assertEquals(2, ran[0]);
        // A notice on its real bar: any key, Enter, Escape dismiss it.
        final KeyPrompter keys = new KeyPrompter();
        final ClassicAdvisorBox.Request n = ClassicGUI.notice("notice",
            "Line one.\nLine {two}.", "t", null);
        assertTrue(n.isNotice());
        assertEquals(-1, n.defaultRow);
        assertEquals(-1, n.cancelRow);
        assertEquals(2, n.paragraphs.size());
        assertEquals("Line (two).", n.paragraphs.get(1).get(0));
        for (String k : new String[] { "X", "ENTER", "ESC", "UP", "DOWN" }) {
            assertEquals(k, 0, keys.answer(n, k));
        }
    }

    /**
     * C FINAL trap 2: BEFEHLE "Zurück nach Europa" (row 16, ships only) and
     * its gold letter R fire {@code returnToEuropeAction}, enabled for a
     * ship on the map whose player still has Europe; R stays the road
     * order for land units.
     */
    public void testReturnToEuropeOrder() {
        Topology.setCurrent(Topology.SQUARE);
        final Game game = getStandardGame();
        final MapBuilder builder = new MapBuilder(game);
        builder.setDimensions(20, 15).setBaseTileType(ocean)
            .setExploredByAll(true);
        for (int y = 0; y < 15; y++) {
            for (int x = 0; x < 5; x++) {
                builder.setTileType(x, y, spec().getTileType("model.tile.plains"));
            }
        }
        final Map map = builder.build();
        game.changeMap(map);
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        // A ship far from the high seas (the west coast, say): the order applies.
        final Unit ship = new ServerUnit(game, map.getTile(6, 7), dutch,
            spec().getUnitType("model.unit.merchantman"));
        assertFalse(ship.getTile().isDirectlyHighSeasConnected());
        assertTrue(ReturnToEuropeAction.canReturnToEurope(ship));
        // Land units never; a ship in Europe has no tile.
        final Unit colonist = new ServerUnit(game, map.getTile(3, 7), dutch,
            spec().getUnitType("model.unit.freeColonist"));
        assertFalse(ReturnToEuropeAction.canReturnToEurope(colonist));
        assertFalse(ReturnToEuropeAction.canReturnToEurope(null));
        final Unit docked = new ServerUnit(game, dutch.getEurope(), dutch,
            spec().getUnitType("model.unit.caravel"));
        assertFalse(ReturnToEuropeAction.canReturnToEurope(docked));

        // The menu row: listed for ships only, wired, grey when disabled.
        final ClassicMenuModel.Item row = ClassicMenuModel
            .items(ClassicMenuModel.BEFEHLE).get(16);
        assertEquals(ReturnToEuropeAction.id, row.actionId);
        assertEquals(javax.swing.KeyStroke.getKeyStroke("R"), row.key);
        final ClassicMenuModel.Context shipCtx = ClassicMenuModel.Context.of(ship,
            net.sf.freecol.client.gui.GUI.ViewMode.MOVE_UNITS);
        final ClassicMenuModel.Context landCtx = ClassicMenuModel.Context.of(colonist,
            net.sf.freecol.client.gui.GUI.ViewMode.MOVE_UNITS);
        assertTrue(ClassicMenuModel.isVisible(row, shipCtx));
        assertFalse(ClassicMenuModel.isVisible(row, landCtx));
        assertTrue(ClassicMenuModel.isLive(row, id -> true));
        assertFalse(ClassicMenuModel.isLive(row, id -> false));
        assertTrue(ClassicMenuModel.isGreyed(row, id -> false));
        // The key: road first (disabled on water), then back to Europe.
        ClassicKeyMap.Binding r = null;
        for (ClassicKeyMap.Binding b : ClassicKeyMap.bindings()) {
            if (javax.swing.KeyStroke.getKeyStroke("R").equals(b.key)) r = b;
        }
        assertNotNull(r);
        assertEquals("[roadAction, returnToEuropeAction]", r.actionIds.toString());
        assertEquals(ReturnToEuropeAction.id, ClassicKeyMap.pick(r,
            id -> id.equals(ReturnToEuropeAction.id)));
        assertEquals("roadAction", ClassicKeyMap.pick(r, id -> true));
        assertNull(ClassicKeyMap.pick(r, id -> false));
    }

    /**
     * D acceptance D1: a ship that has sailed for Europe ("Zurück nach
     * Europa"), re-selected by the controller after its last move, is no
     * unit to show while nothing else can move: the end view (in the game
     * the turn flow then ends the turn by itself).  With a unit left to
     * move the choice is made as before (a hand-over from the ship).
     */
    public void testShipAtSeaGivesTheEndView() {
        final Game game = getStandardGame();
        final Map map = getCoastTestMap(spec().getTileType("model.tile.plains"), true);
        game.changeMap(map);
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final Tile sea = map.getTile(15, 7);
        final Tile land = map.getTile(5, 7);
        assertTrue(!sea.isLand() && land.isLand());
        final Unit ship = new ServerUnit(game, sea, dutch,
            spec().getUnitType("model.unit.merchantman"));
        final ClassicGUI gui = new ClassicGUI(null);
        final ClassicMapViewer mv = new ClassicMapViewer(null, gui, null, false);
        gui.mapViewer = mv;
        try {
            mv.setFocus(sea);
            gui.changeView(ship, false);            // on the map: it is up
            assertSame(ship, mv.getActiveUnit());
            assertEquals(net.sf.freecol.client.gui.GUI.ViewMode.MOVE_UNITS,
                         mv.getViewMode());
            assertFalse(ClassicGUI.goneWithNothingLeft(ship, gui.unitCycle));

            // It sails for Europe: off the map, nothing else to move.
            assertNotNull(dutch.getHighSeas());
            ship.setLocation(dutch.getHighSeas());
            assertFalse(ship.hasTile());
            assertTrue(ClassicGUI.goneWithNothingLeft(ship, gui.unitCycle));
            gui.changeView(ship, true);             // the controller's redisplay
            assertNull(mv.getActiveUnit());
            assertEquals(net.sf.freecol.client.gui.GUI.ViewMode.END_TURN,
                         mv.getViewMode());

            // A colonist that can still move: the choice goes on as before.
            final Unit colonist = new ServerUnit(game, land, dutch,
                spec().getUnitType("model.unit.freeColonist"));
            assertTrue(colonist.getMovesLeft() > 0);
            assertFalse(ClassicGUI.goneWithNothingLeft(ship, gui.unitCycle));
            gui.changeView(ship, true);
            assertSame(ship, mv.getActiveUnit());
            assertEquals(net.sf.freecol.client.gui.GUI.ViewMode.MOVE_UNITS,
                         mv.getViewMode());
            assertFalse(ClassicGUI.goneWithNothingLeft(null, gui.unitCycle));
            assertFalse(ClassicGUI.goneWithNothingLeft(colonist, gui.unitCycle));
        } finally {
            mv.dispose();
        }
    }

    /**
     * A classic GUI without a client, with prefs of its own (in memory, so
     * no test writes the user's classic-options.properties) and a game.
     */
    private static final class PrefsGUI extends ClassicGUI {

        final ClassicPrefs mine = new ClassicPrefs(null);
        final Game game;

        PrefsGUI(Game game) {
            super(null);
            this.game = game;
        }

        @Override
        ClassicPrefs prefs() {
            return this.mine;
        }

        @Override
        protected Game getGame() {
            return this.game;
        }
    }

    /**
     * SPIEL rows 0 and 1 (build spec W14): the strip's host gives their
     * classic actions, and each opens its option box from the pack's
     * GAME.TXT on the real bar: there Down x3 and Enter switch Spielzugende
     * on at once, Escape closes; in the colony box Down and Space switch the
     * numbers on the work tiles off.  Without the pack the row says that it
     * follows later.
     */
    public void testTheOptionRowsOpenTheBoxes() {
        final PrefsGUI gui = new PrefsGUI(null);
        final javax.swing.Action ga = gui.classicAction(ClassicMenuModel.GAME_OPTIONS);
        assertNotNull(ga);
        assertTrue(ga.isEnabled());
        assertSame(ga, gui.classicAction(ClassicMenuModel.GAME_OPTIONS));
        assertNotNull(gui.classicAction(ClassicMenuModel.COLONY_OPTIONS));
        assertNotSame(ga, gui.classicAction(ClassicMenuModel.COLONY_OPTIONS));
        assertNull(gui.classicAction("saveAction"));
        assertNull(gui.classicAction(null));
        final KeyPrompter keys = new KeyPrompter()
            .press("DOWN", "DOWN", "DOWN", "ENTER", "ESC");
        gui.prompter = keys;
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        if (t == null || t.message(ClassicOptionBoxes.GAME_SECTION) == null) {
            System.err.println("testTheOptionRowsOpenTheBoxes: no pack texts,"
                + " the notice only");
            assertFalse(gui.showOptionBox(ClassicOptionBoxes.GAME_SECTION));
            assertTrue(last(keys.boxes).isNotice());
            return;
        }
        assertTrue(gui.showOptionBox(ClassicOptionBoxes.GAME_SECTION));
        final ClassicAdvisorBox.Request r = last(keys.boxes);
        assertTrue(r.isCheckbox());
        assertEquals(8, r.rows.size());
        assertEquals("XXooXXXX", ClassicAdvisorBox.checkString(r.checks));
        assertTrue(gui.mine.is(ClassicPrefs.END_TURN_PROMPT));
        assertFalse(gui.mine.is(ClassicPrefs.MOVE_ACCELERATOR));
        // The next opening shows it, the bar again on row 1.
        keys.press("ESC");
        gui.classicAction(ClassicMenuModel.GAME_OPTIONS).actionPerformed(null);
        assertEquals("XXoXXXXX", ClassicAdvisorBox.checkString(last(keys.boxes).checks));
        assertEquals(0, last(keys.boxes).defaultRow);
        // The colony box.
        keys.press("DOWN", "SPACE", "ESC");
        gui.classicAction(ClassicMenuModel.COLONY_OPTIONS).actionPerformed(null);
        assertEquals(10, last(keys.boxes).rows.size());
        assertFalse(gui.mine.is(ClassicPrefs.GOODS_TERRAIN_LABELS));
        assertTrue(gui.mine.is(ClassicPrefs.BUILDING_LABELS));
        assertEquals(3, keys.boxes.size());
    }

    /**
     * The notices that come up (the funnel of both notice seams): FreeCol's
     * start message never, a colony report only while its row of
     * "Koloniebericht-Optionen" is on; a held-back notice asks no box.
     */
    public void testColonyReportsHeldBack() {
        final Game game = getStandardGame();
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final PrefsGUI gui = new PrefsGUI(game);
        final FakePrompter fake = new FakePrompter();
        gui.prompter = fake;
        final net.sf.freecol.common.model.ModelMessage famine
            = new net.sf.freecol.common.model.ModelMessage(
                net.sf.freecol.common.model.ModelMessage.MessageType.WARNING,
                "model.colony.famineFeared", dutch, dutch);
        final net.sf.freecol.common.model.ModelMessage father
            = new net.sf.freecol.common.model.ModelMessage(
                net.sf.freecol.common.model.ModelMessage.MessageType.SONS_OF_LIBERTY,
                "model.player.foundingFatherJoinedCongress", dutch, dutch);
        final net.sf.freecol.common.model.ModelMessage start
            = new net.sf.freecol.common.model.ModelMessage(
                net.sf.freecol.common.model.ModelMessage.MessageType.DEFAULT,
                ClassicGUI.START_GAME_MESSAGE, dutch, dutch);
        assertEquals(List.of(famine, father), gui.noticesShown(List.of(start, famine, father)));
        gui.mine.set(ClassicPrefs.REPORT_FOOD, false);
        assertEquals(List.of(father), gui.noticesShown(List.of(start, famine, father)));
        gui.mine.set(ClassicPrefs.REPORT_FOOD, true);
        gui.mine.set(ClassicPrefs.REPORT_SONS_OF_LIBERTY, false);
        assertEquals(List.of(famine, father), gui.noticesShown(List.of(famine, father)));
        assertTrue(gui.noticesShown(null).isEmpty());
        // Held back in both seams: no box.
        gui.mine.set(ClassicPrefs.REPORT_FOOD, false);
        assertNull(gui.showModelMessages(List.of(famine)));
        assertNull(gui.showReportTurnPanel(List.of(famine)));
        assertTrue(fake.boxes.isEmpty());
    }

    /**
     * A classic GUI without a client that wakes the passengers itself (the
     * controller's state change needs a server) and keeps whom it woke.
     */
    private static final class LandingGUI extends ClassicGUI {

        final List<Unit> woken = new ArrayList<>();

        LandingGUI() {
            super(null);
        }

        @Override
        void wake(Unit unit) {
            this.woken.add(unit);
            unit.setState(Unit.UnitState.ACTIVE);
        }
    }

    /** FreeCol's landing list for a ship: one row per unit that can go, then "Alle". */
    private static List<ChoiceItem<Unit>> landingChoices(Unit ship, Tile target) {
        final List<ChoiceItem<Unit>> choices = new ArrayList<>();
        for (Unit u : ship.getUnitList()) {
            if (u.getMoveType(target).isProgress()) {
                choices.add(new ChoiceItem<>(u.getDescription(Unit.UnitLabelType.NATIONAL), u));
            }
        }
        choices.add(new ChoiceItem<>(Messages.message("all"), ship));
        return choices;
    }

    /**
     * The landing box (build spec W8b): GAME.TXT @LANDFALL with the
     * frontiersman, the bar on "Bei den Schiffen bleiben" (row 1), Escape
     * staying; without the pack FreeCol's question with the same rows.  A
     * set time from the key: 72 ms with the palette load, 86 without.
     */
    public void testLandfallBox() {
        assertTrue(ClassicGUI.isLandfall(StringTemplate.key("disembark.text")));
        assertFalse(ClassicGUI.isLandfall(StringTemplate.key("highseas.text")));
        assertFalse(ClassicGUI.isLandfall(null));

        final ClassicAdvisorBox.Request fallback = ClassicGUI.landfallRequest(null, 0L);
        assertEquals(ClassicGUI.LANDFALL_SECTION, fallback.id);
        assertEquals(2, fallback.rows.size());
        assertEquals(0, fallback.defaultRow);
        assertEquals(0, fallback.escapeAnswer());
        assertSame(ClassicAdvisorBox.Portrait.SCOUT, fallback.portrait);
        assertEquals(0L, fallback.showAtNanos);
        assertEquals(123L, ClassicGUI.landfallRequest(null, 123L).showAtNanos);

        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        if (t != null && t.message("LANDFALL") != null) {
            final ClassicAdvisorBox.Request r = ClassicGUI.landfallRequest(t, 0L);
            assertEquals(List.of("Bei den Schiffen bleiben", "An Land gehen"),
                         List.of(r.plainRows()));
            assertTrue(r.plainText().startsWith("Sollen wir an Land gehen"));
            assertEquals(0, r.defaultRow);
            assertEquals(0, r.escapeAnswer());
            assertSame(ClassicAdvisorBox.Portrait.SCOUT, r.portrait);
            assertEquals(190, r.width);
        } else {
            System.err.println(getClass().getSimpleName()
                + ": no pack texts, the GAME.TXT rows are not checked");
        }

        final long key = 1_000_000_000L;
        assertEquals(key + 72_000_000L, ClassicGUI.landfallShowAt(key, key + 30_000_000L, true));
        assertEquals(key + 86_000_000L, ClassicGUI.landfallShowAt(key, key + 30_000_000L, false));
        assertEquals(0L, ClassicGUI.landfallShowAt(0L, key, true));
        assertEquals(0L, ClassicGUI.landfallShowAt(key, key + 2_000_000_000L, false));
    }

    /**
     * "An Land gehen" (build spec W8b, master plan W18): FreeCol's list
     * seam returns only its first unit, the one aboard longest; every
     * other passenger asleep is woken, also one without moves; "Bei den
     * Schiffen bleiben", Escape and a box that could not open land
     * nothing, and "Alle" is never the answer.  The ship's re-selection
     * after the move hands over to the next unit in the cycle after the
     * landed one: a passenger aboard made after it.
     */
    public void testLandfallSendsTheLongestAboard() {
        final Game game = getStandardGame();
        final Map map = getCoastTestMap(spec().getTileType("model.tile.plains"), true);
        game.changeMap(map);
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final Tile sea = map.getTile(10, 7), land = map.getTile(9, 7);
        assertTrue(!sea.isLand() && land.isLand() && sea.isAdjacent(land));
        final net.sf.freecol.common.model.UnitType colonist
            = spec().getUnitType("model.unit.freeColonist");
        final Unit ship = new ServerUnit(game, sea, dutch,
            spec().getUnitType("model.unit.merchantman"));
        final Unit a = new ServerUnit(game, ship, dutch, colonist);   // aboard first
        final Unit b = new ServerUnit(game, ship, dutch, colonist);
        final Unit c = new ServerUnit(game, ship, dutch, colonist);   // boarded this turn
        c.setMovesLeft(0);
        final int shipMoves = ship.getMovesLeft();
        for (Unit u : List.of(a, b, c)) assertEquals(Unit.UnitState.SENTRY, u.getState());
        final StringTemplate q = StringTemplate.key("disembark.text");

        final LandingGUI gui = new LandingGUI();
        final FakePrompter fake = new FakePrompter();
        gui.prompter = fake;
        final ClassicMapViewer mv = new ClassicMapViewer(null, gui, null, false);
        gui.mapViewer = mv;
        try {
            mv.setFocus(sea);
            mv.changeToMoveUnits(ship);
            final List<ChoiceItem<Unit>> choices = landingChoices(ship, land);
            assertEquals(3, choices.size());   // a, b and "Alle"

            // Stay, Escape (row 0) and a closed box: nothing lands, nobody woken.
            for (int answer : new int[] { 0, -1 }) {
                fake.answer = answer;
                assertNull(gui.modalChoiceDialog(sea, q, (ImageIcon) null, "none", choices));
            }
            assertTrue(gui.woken.isEmpty());
            assertEquals(2, fake.boxes.size());
            assertEquals(ClassicGUI.LANDFALL_SECTION, last(fake.boxes).id);
            assertEquals(2, last(fake.asked).length);   // never a row per unit, never "Alle"
            assertEquals(Integer.valueOf(0), last(fake.defaults));

            // An Land gehen: a only; b and c woken.
            fake.answer = 1;
            assertSame(a, gui.modalChoiceDialog(sea, q, (ImageIcon) null, "none", choices));
            assertEquals(List.of(b, c), gui.woken);
            assertEquals(Unit.UnitState.SENTRY, a.getState());
            assertEquals(shipMoves, ship.getMovesLeft());

            // FreeCol moves a ashore (all its moves) and re-selects the
            // ship: the landed unit is the one that just finished, and the
            // cycle goes on after it -- b, aboard, then the ship.
            a.setLocation(land);
            a.setMovesLeft(0);
            gui.changeView(ship, true);
            assertSame(a, mv.getActiveUnit());
            assertTrue(b.isActivePassenger());
            assertFalse(c.isActivePassenger());       // no moves
            // The unit cycle's hand-over (the turn flow's, W5f): b aboard,
            // then (c without moves) the ship.
            assertSame(b, gui.unitCycle.next(a, dutch));
            assertSame(ship, gui.unitCycle.next(b, dutch));
            gui.changeView(b, false);                 // no turn flow: at once
            assertSame(b, mv.getActiveUnit());
            assertSame(b, mv.displayUnit(sea));       // drawn instead of the ship

            // The one-unit seam (FreeCol's confirm): b is the only one that
            // can go; stay is false, An Land gehen true.
            mv.changeToMoveUnits(ship);
            fake.answer = 0;
            assertFalse(gui.modalConfirmDialog(land, q, (ImageIcon) null, "ok", "cancel", true));
            fake.answer = -1;
            assertFalse(gui.modalConfirmDialog(land, q, (ImageIcon) null, "ok", "cancel", true));
            fake.answer = 1;
            assertTrue(gui.modalConfirmDialog(land, q, (ImageIcon) null, "ok", "cancel", true));
            assertEquals(ClassicGUI.LANDFALL_SECTION, last(fake.boxes).id);
            // A landing that did not go ashore leaves the ship selected.
            gui.changeView(ship, true);
            assertSame(ship, mv.getActiveUnit());
        } finally {
            mv.dispose();
        }
    }

    /**
     * The landings of clip007 with FreeCol's real start ids (the start
     * ship made after the pioneer and the soldier, c1-edge.fsg: 5885,
     * 5886, 5887) and the unit cycle's hand-overs (master plan W5f): the
     * ship first at the turn start (#1928); after the farmer lands, the
     * scout aboard (#3107); after the scout lands, the EMPTY ship, though
     * the pioneer and the soldier on land can move (#3697: F2's carrier
     * rank put the pioneer first); after the ship's last move the pioneer
     * (#4579).  Landing 2: the soldier boarded before the older pioneer,
     * so it goes ashore; the next unit is the farmer on land, not the
     * pioneer aboard (#5804).  The farmer boards: the ship comes next, and
     * the unit the controller chose is put back to come next in FreeCol's
     * cycle (#4281, #6188, #6989).
     */
    public void testLandingCycleAndBoarding() {
        final Game game = getStandardGame();
        final Map map = getCoastTestMap(spec().getTileType("model.tile.plains"), true);
        game.changeMap(map);
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final Tile sea = map.getTile(10, 7), land = map.getTile(9, 7);
        final Tile shore = map.getTile(9, 6), shore2 = map.getTile(8, 7);
        final net.sf.freecol.common.model.UnitType colonist
            = spec().getUnitType("model.unit.freeColonist");
        final Unit pioneer = new ServerUnit(game, shore, dutch, colonist);
        final Unit soldier = new ServerUnit(game, shore2, dutch, colonist);
        final Unit ship = new ServerUnit(game, sea, dutch,
            spec().getUnitType("model.unit.merchantman"));
        final Unit farmer = new ServerUnit(game, ship, dutch, colonist);   // bought later
        final Unit scout = new ServerUnit(game, ship, dutch, colonist);
        assertTrue(ClassicUnitCycle.startCarrier(ship));
        assertTrue(pioneer.getIdNumber() < soldier.getIdNumber()
                   && soldier.getIdNumber() < ship.getIdNumber());
        assertEquals(List.of(farmer, scout), ship.getUnitList());
        final ClassicUnitCycle cycle;

        final LandingGUI gui = new LandingGUI();
        cycle = gui.unitCycle;
        final FakePrompter fake = new FakePrompter();
        gui.prompter = fake;
        final ClassicMapViewer mv = new ClassicMapViewer(null, gui, null, false);
        gui.mapViewer = mv;
        try {
            // The turn start: the ship, the head of the list.
            assertSame(ship, cycle.next(null, dutch));
            mv.setFocus(sea);
            mv.changeToMoveUnits(ship);
            assertSame(ship, ClassicGUI.landingCarrier(ship, land));
            assertNull(ClassicGUI.landingCarrier(ship, map.getTile(5, 7)));   // not next to it
            assertNull(ClassicGUI.landingCarrier(farmer, land));               // no carrier

            // Landing 1: the farmer (aboard longest) goes ashore, the scout
            // is woken; after the farmer the scout aboard comes.
            fake.answer = 1;
            assertSame(farmer, gui.modalChoiceDialog(sea, StringTemplate.key("disembark.text"),
                (ImageIcon) null, "none", landingChoices(ship, land)));
            assertEquals(List.of(scout), gui.woken);
            farmer.setLocation(land);
            farmer.setMovesLeft(0);
            gui.changeView(ship, true);
            assertSame(farmer, mv.getActiveUnit());   // the landed unit is done
            assertSame(scout, cycle.next(farmer, dutch));
            gui.changeView(scout, false);              // no turn flow: at once
            // The scout lands too: the empty ship, not the pioneer (#3697).
            scout.setLocation(land);
            scout.setMovesLeft(0);
            assertTrue(pioneer.getMovesLeft() > 0 && soldier.getMovesLeft() > 0);
            assertTrue(ship.getUnitList().isEmpty());
            assertSame(ship, cycle.next(scout, dutch));
            // The ship's last move: the pioneer (#4579), then the soldier.
            ship.setMovesLeft(0);
            assertSame(pioneer, cycle.next(ship, dutch));
            pioneer.setMovesLeft(0);
            assertSame(soldier, cycle.next(pioneer, dutch));

            // Landing 2: the soldier boarded before the pioneer.
            ship.setMovesLeft(3);
            farmer.setMovesLeft(1);
            scout.setMovesLeft(0);
            soldier.setLocation(ship);
            pioneer.setLocation(ship);
            pioneer.setMovesLeft(1);
            soldier.setMovesLeft(1);
            assertEquals(List.of(soldier, pioneer), ship.getUnitList());
            assertSame(soldier, ClassicGUI.firstLander(ship, land));
            gui.woken.clear();
            mv.changeToMoveUnits(ship);
            assertSame(soldier, gui.modalChoiceDialog(sea, StringTemplate.key("disembark.text"),
                (ImageIcon) null, "none", landingChoices(ship, land)));
            assertEquals(List.of(pioneer), gui.woken);
            soldier.setLocation(land);
            soldier.setMovesLeft(0);
            gui.changeView(ship, true);
            assertSame(soldier, mv.getActiveUnit());
            assertSame(farmer, cycle.next(soldier, dutch));   // not the pioneer aboard
            gui.changeView(farmer, false);
            assertSame(farmer, mv.getActiveUnit());

            // The farmer boards (a movement key): the controller's
            // re-selection keeps it, then (say) it chooses the pioneer
            // aboard; the ship comes instead, and the pioneer is put back
            // to come next in FreeCol's cycle.
            gui.unitBoarding(farmer);
            farmer.setLocation(ship);
            farmer.setMovesLeft(0);
            gui.changeView(farmer, true);
            assertSame(farmer, mv.getActiveUnit());
            assertSame(ship, mv.displayUnit(sea));           // asleep aboard: the ship
            gui.changeView(pioneer, false);
            gui.unitBoarding(null);
            assertSame(ship, mv.getActiveUnit());
            assertSame(pioneer, dutch.getNextActiveUnit());
            // A carrier that cannot move: no boarding hand-over.
            gui.unitBoarding(farmer);
            ship.setMovesLeft(0);
            mv.changeToMoveUnits(farmer);
            gui.changeView(pioneer, false);
            gui.unitBoarding(null);
            assertSame(pioneer, mv.getActiveUnit());
        } finally {
            mv.dispose();
        }
    }

    /**
     * W5f: FreeCol's colony screen on a goto arrival at a colony is
     * dropped for a land unit while its run is under way (c6 U25 arrives
     * at Base with moves left: "Keine Befehle", no screen); a ship's stays
     * (the docking screen, clip008 01-shipcolony).  Without the turn flow
     * nothing is dropped.  (The controller's batch flag: MoveTest.)
     */
    public void testColonyScreenOnGotoArrival() {
        final ClassicGUI gui = new ClassicGUI(null);
        assertFalse(gui.landGotoRunning());          // no turn flow

        final Game game = getStandardGame();
        final Map map = getCoastTestMap(spec().getTileType("model.tile.plains"), true);
        game.changeMap(map);
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final Unit soldier = new ServerUnit(game, map.getTile(5, 7), dutch,
            spec().getUnitType("model.unit.freeColonist"));
        final Unit ship = new ServerUnit(game, map.getTile(15, 7), dutch,
            spec().getUnitType("model.unit.merchantman"));
        assertTrue(ClassicGUI.dropsColonyScreen(soldier));    // c6 U25 at Base
        assertFalse(ClassicGUI.dropsColonyScreen(ship));      // a docking: the screen
        assertFalse(ClassicGUI.dropsColonyScreen(null));      // no goto run
    }

    /** Run on the event thread and wait: the seams answer there. */
    private static void onEdt(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
    }

    /** The goods, most valuable first at a player's market (a stable sort). */
    private static List<Goods> byPrice(Player p, List<Goods> gl) {
        final Market market = p.getMarket();
        final List<Goods> out = new ArrayList<>(gl);
        out.sort(Comparator.comparingInt((Goods g) ->
                market.getBidPrice(g.getType(), g.getAmount())).reversed());
        return out;
    }

    /**
     * G1 (N15): after a naval win the loot is answered at once, before any
     * box, so the server's session ends (the base GUI's silence froze the
     * game at the next end of turn): as many goods as fit, the most
     * valuable first, the offered objects themselves; an empty list when
     * nothing is offered.  A failing notice cannot stop the answer.
     */
    public void testLootIsAnsweredAtOnce() throws Exception {
        final Game game = getStandardGame();
        final Map map = getCoastTestMap(spec().getTileType("model.tile.plains"), true);
        game.changeMap(map);
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        assertNotNull(dutch.getMarket());
        final Unit privateer = new ServerUnit(game, map.getTile(15, 7), dutch,
            spec().getUnitType("model.unit.privateer"));
        assertEquals(2, privateer.getSpaceLeft());
        final GoodsType furs = spec().getGoodsType("model.goods.furs");
        final GoodsType sugar = spec().getGoodsType("model.goods.sugar");
        final GoodsType silver = spec().getGoodsType("model.goods.silver");
        final GoodsType food = spec().getGoodsType("model.goods.food");
        final List<Goods> offer = List.of(new Goods(game, null, furs, 100),
            new Goods(game, null, sugar, 100), new Goods(game, null, silver, 100));
        final List<Goods> expect = byPrice(dutch, offer);
        final List<String> log = new ArrayList<>();
        final List<List<Goods>> answers = new ArrayList<>();
        final ClassicGUI gui = new ClassicGUI(null);
        gui.prompter = r -> {
            log.add("box " + r.id);
            return 0;
        };
        onEdt(() -> gui.showCaptureGoodsDialog(privateer, offer, gl -> {
                    log.add("answer");
                    answers.add(gl);
                }));
        assertEquals(1, answers.size());
        assertEquals(List.of("answer", "box loot"), log);   // the answer first
        assertEquals(2, answers.get(0).size());
        assertSame(expect.get(0), answers.get(0).get(0));
        assertSame(expect.get(1), answers.get(0).get(1));

        // One offered: that one.  None (or null): an empty list, no box.
        log.clear();
        onEdt(() -> gui.showCaptureGoodsDialog(privateer, List.of(offer.get(1)),
                                               answers::add));
        assertEquals(List.of(offer.get(1)), last(answers));
        assertSame(offer.get(1), last(answers).get(0));
        log.clear();
        onEdt(() -> gui.showCaptureGoodsDialog(privateer, new ArrayList<>(),
                                               answers::add));
        assertNotNull(last(answers));
        assertTrue(last(answers).isEmpty());
        assertTrue(log.isEmpty());
        onEdt(() -> gui.showCaptureGoodsDialog(privateer, null, answers::add));
        assertTrue(last(answers).isEmpty());
        assertEquals(4, answers.size());

        // A notice that fails: the answer is out already.
        gui.prompter = r -> {
            throw new IllegalStateException("no box (test)");
        };
        onEdt(() -> gui.showCaptureGoodsDialog(privateer, offer, answers::add));
        assertEquals(5, answers.size());
        assertEquals(2, last(answers).size());

        // One hold taken: the most valuable one only.
        privateer.addGoods(food, 100);
        assertEquals(1, privateer.getSpaceLeft());
        assertEquals(List.of(expect.get(0)), ClassicSeams.lootTaken(privateer, offer));
        // A part-filled hold takes goods of its type without a new hold.
        privateer.removeGoods(food);
        privateer.addGoods(furs, 50);
        privateer.addGoods(food, 100);
        assertEquals(0, privateer.getSpaceLeft());
        final Goods fifty = new Goods(game, null, furs, 50);
        assertEquals(List.of(fifty), ClassicSeams.lootTaken(privateer,
            List.of(new Goods(game, null, sugar, 100), fifty)));
        assertTrue(ClassicSeams.lootTaken(privateer,
            List.of(new Goods(game, null, furs, 51))).isEmpty());
        assertTrue(ClassicSeams.lootTaken(null, offer).isEmpty());
    }

    /**
     * G1 (N15), on the server: the loot session the base GUI never
     * answered (the end of turn waits for it) ends with the seam's
     * answer, and the server accepts every good it takes.
     */
    public void testLootSessionCompletes() throws Exception {
        final Game game = ServerTestHelper.startServerGame(
            getTestMap(spec().getTileType("model.tile.ocean")));
        try {
            final net.sf.freecol.server.control.InGameController igc
                = ServerTestHelper.getInGameController();
            final Map map = game.getMap();
            final ServerPlayer dutch = getServerPlayer(game, "model.nation.dutch");
            final ServerPlayer english = getServerPlayer(game, "model.nation.english");
            final Unit winner = new ServerUnit(game, map.getTile(5, 5), dutch,
                spec().getUnitType("model.unit.privateer"));
            final Unit loser = new ServerUnit(game, map.getTile(6, 5), english,
                spec().getUnitType("model.unit.merchantman"));
            final GoodsType furs = spec().getGoodsType("model.goods.furs");
            final GoodsType sugar = spec().getGoodsType("model.goods.sugar");
            final GoodsType silver = spec().getGoodsType("model.goods.silver");
            winner.addGoods(furs, 30);              // a part-filled hold
            loser.addGoods(furs, 100);
            loser.addGoods(sugar, 100);
            loser.addGoods(silver, 100);
            // As ServerPlayer.csLootShip does it.
            final List<Goods> capture = loser.getGoodsList();
            for (Goods g : capture) g.setLocation(null);
            new LootSession(winner, loser, capture).register();
            loser.getGoodsContainer().removeAll();
            assertTrue(Session.waitingForSession());   // the freeze, unanswered
            final List<Goods> offered = new ArrayList<>(capture);
            final List<Goods> expect = ClassicSeams.lootTaken(winner, offered);
            assertEquals(1, expect.size());           // one free hold
            final List<Goods> answered = new ArrayList<>();
            final ClassicGUI gui = new ClassicGUI(null);
            gui.prompter = r -> 0;
            onEdt(() -> {
                    gui.animateUnitAttack(winner, loser, winner.getTile(),
                                          loser.getTile(), true);
                    gui.showCaptureGoodsDialog(winner, offered, gl -> {
                            answered.addAll(gl);
                            igc.lootCargo(dutch, winner, loser.getId(), gl);
                        });
                });
            assertEquals(expect, answered);
            assertFalse("the session ended", Session.waitingForSession());
            final Goods g = answered.get(0);
            assertEquals(g.getAmount() + ((g.getType() == furs) ? 30 : 0),
                         winner.getGoodsCount(g.getType()));
        } finally {
            Session.clearAll();
            ServerTestHelper.stopServerGame();
        }
    }

    /**
     * G1 (N15): the loot's notices.  With the pack's texts and a known
     * loser, one GAME.TXT @CARGOCAPTURE per goods type ("Engl. Ware (150
     * Felle) durch Holl. Kaperschiff erobert!"); the loser is the one the
     * winner fought last ({@code animateUnitAttack}).  Without the texts,
     * or the loser, FreeCol's words in one notice.
     */
    public void testLootNotice() throws Exception {
        final Game game = getStandardGame();
        final Map map = getCoastTestMap(spec().getTileType("model.tile.plains"), true);
        game.changeMap(map);
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final Player english = game.getPlayerByNationId("model.nation.english");
        final Unit privateer = new ServerUnit(game, map.getTile(15, 7), dutch,
            spec().getUnitType("model.unit.privateer"));
        final Unit loser = new ServerUnit(game, map.getTile(16, 7), english,
            spec().getUnitType("model.unit.merchantman"));
        final Unit stranger = new ServerUnit(game, map.getTile(17, 7), english,
            spec().getUnitType("model.unit.frigate"));
        final GoodsType furs = spec().getGoodsType("model.goods.furs");
        final GoodsType sugar = spec().getGoodsType("model.goods.sugar");
        final List<Goods> taken = List.of(new Goods(game, null, furs, 100),
            new Goods(game, null, sugar, 100), new Goods(game, null, furs, 50));
        final String title = Messages.message("captureGoodsDialog.title");
        // FreeCol's words: the title and one line per goods type.
        List<ClassicAdvisorBox.Request> ns = ClassicSeams.lootNotices(null, privateer,
            english, taken, "t");
        assertEquals(1, ns.size());
        assertTrue(ns.get(0).isNotice());
        final String fc = ns.get(0).plainText();
        assertTrue(fc, fc.startsWith(title));
        assertTrue(fc, fc.contains(ClassicAdvisorBox.literal(Messages.message(
            new net.sf.freecol.common.model.AbstractGoods(furs, 150).getLabel()))));
        assertEquals(3, fc.split("\n").length);
        assertTrue(ClassicSeams.lootNotices(null, privateer, english,
            new ArrayList<>(), "t").isEmpty());

        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        if (t == null) {
            System.err.println("ClassicGUISeamTest: testLootNotice's GAME.TXT part"
                + " skipped, no pack texts (ant classic-assets)");
            return;
        }
        assertEquals("loot", ClassicSeams.lootNotices(t, privateer, null, taken,
            "t").get(0).id);                         // the loser not known
        ns = ClassicSeams.lootNotices(t, privateer, english, taken, "t");
        assertEquals(2, ns.size());
        final String first = ns.get(0).plainText();
        assertEquals(ClassicSeams.CARGO_CAPTURE, ns.get(0).id);
        assertTrue(ns.get(0).isNotice());
        assertSame(ClassicAdvisorBox.Portrait.NONE, ns.get(0).portrait);
        for (String s : new String[] { "Engl.", "Holl.", "150",
                Messages.getName(furs), Messages.getName(privateer.getType()) }) {
            assertTrue(s + " in " + first, first.contains(s));
        }
        assertTrue(ns.get(1).plainText().contains(Messages.getName(sugar)));
        assertEquals("Holl.", ClassicSeams.abbrev(t, dutch));
        assertNull(ClassicSeams.abbrev(t, game.getPlayerByNationId("model.nation.inca")));

        // Through the GUI: the fight shown before names the loser.
        final FakePrompter fake = new FakePrompter();
        final ClassicGUI gui = new ClassicGUI(null);
        gui.prompter = fake;
        onEdt(() -> {
                gui.animateUnitAttack(loser, privateer, loser.getTile(),
                                      privateer.getTile(), false);
                gui.showCaptureGoodsDialog(privateer, taken.subList(0, 1), gl -> {});
            });
        assertEquals(ClassicSeams.CARGO_CAPTURE, last(fake.boxes).id);
        // A fight of others: FreeCol's words.
        onEdt(() -> {
                gui.animateUnitAttack(stranger, loser, stranger.getTile(),
                                      loser.getTile(), true);
                gui.showCaptureGoodsDialog(privateer, taken.subList(0, 1), gl -> {});
            });
        assertEquals("loot", last(fake.boxes).id);
    }

    /**
     * G1 (N15): William Brewster's and the Fountain of Youth's choice of
     * a recruit: a list box of the three, the bar on row 1, Escape and a
     * click beside it doing nothing; a row answers its slot (1-based), a
     * box closed without a row answers nothing.  GAME.TXT @RECRUITCHOOSE
     * with our country and port over the priest, @LOSTCITY0 over the
     * frontiersman, else FreeCol's words.
     */
    public void testEmigrationBox() throws Exception {
        final Game game = getStandardGame();
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final Europe europe = dutch.getEurope();
        if (europe.getExpandedRecruitables(false).size() < 3) {
            ((ServerEurope) europe).initializeMigration(new Random(7));
        }
        final List<AbstractUnit> rs = europe.getExpandedRecruitables(false);
        assertEquals(3, rs.size());
        final ClassicGUI gui = new ClassicGUI(null);
        final FakePrompter fake = new FakePrompter();
        gui.prompter = fake;
        final List<Integer> slots = new ArrayList<>();
        fake.answer = 2;
        onEdt(() -> gui.showEmigrationDialog(dutch, false, slots::add));
        assertEquals(List.of(3), slots);
        final ClassicAdvisorBox.Request r = last(fake.boxes);
        assertEquals(3, r.rows.size());
        for (int i = 0; i < 3; i++) {
            assertEquals(ClassicAdvisorBox.literal(Messages.message(
                rs.get(i).getSingleLabel())), r.plainRows()[i]);
        }
        assertEquals(0, r.defaultRow);
        assertFalse(r.escapes);
        assertEquals(ClassicAdvisorBox.Bar.OPEN, r.escapeAnswer());
        assertFalse(r.outsideCancels);
        assertEquals(ClassicMenuBox.LIST_INDENT, r.rowIndent);
        fake.answer = ClassicAdvisorBox.Bar.DISMISSED;
        onEdt(() -> gui.showEmigrationDialog(dutch, true, slots::add));
        assertEquals(1, slots.size());                 // nothing taken

        // The player's keys on the real bar.
        final KeyPrompter keys = new KeyPrompter();
        gui.prompter = keys;
        keys.press("ESC");
        onEdt(() -> gui.showEmigrationDialog(dutch, false, slots::add));
        assertEquals(1, slots.size());
        assertEquals(0, keys.lastRow);                 // still up, on row 1
        keys.press("OUT", "ESC");
        onEdt(() -> gui.showEmigrationDialog(dutch, false, slots::add));
        assertEquals(1, slots.size());
        assertEquals(0, keys.lastRow);
        keys.press("ESC", "DOWN", "ENTER");
        onEdt(() -> gui.showEmigrationDialog(dutch, false, slots::add));
        assertEquals(Integer.valueOf(2), last(slots));
        keys.press("DOWN", "DOWN", "DOWN", "ENTER");
        onEdt(() -> gui.showEmigrationDialog(dutch, true, slots::add));
        assertEquals(Integer.valueOf(3), last(slots));
        keys.press("ENTER");
        onEdt(() -> gui.showEmigrationDialog(dutch, false, slots::add));
        assertEquals(Integer.valueOf(1), last(slots));
        // Nobody to choose (natives have no Europe): no box, no answer.
        final int asked = keys.boxes.size();
        onEdt(() -> gui.showEmigrationDialog(
                game.getPlayerByNationId("model.nation.inca"), false, slots::add));
        assertEquals(asked, keys.boxes.size());
        assertEquals(4, slots.size());

        // FreeCol's words without the texts.
        final ClassicAdvisorBox.Request f = ClassicSeams.emigrationRequest(null,
            dutch, false, rs, "t");
        assertEquals(Messages.message("emigrationDialog.chooseImmigrant"), f.plainText());
        assertSame(ClassicAdvisorBox.Portrait.NONE, f.portrait);
        final ClassicAdvisorBox.Request ff = ClassicSeams.emigrationRequest(null,
            dutch, true, rs, "t");
        assertTrue(ff.plainText().startsWith(ClassicAdvisorBox.literal(Messages.message(
            "model.lostCityRumour.fountainOfYouth.description"))));
        assertFalse(ff.escapes);
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        if (t == null) {
            System.err.println("ClassicGUISeamTest: testEmigrationBox's GAME.TXT part"
                + " skipped, no pack texts (ant classic-assets)");
            return;
        }
        final ClassicAdvisorBox.Request b = ClassicSeams.emigrationRequest(t, dutch,
            false, rs, "t");
        assertEquals(ClassicSeams.RECRUIT_CHOOSE, b.id);
        assertTrue(b.plainText(), b.plainText().contains("Holland"));
        assertTrue(b.plainText(), b.plainText().contains("Amsterdam"));
        assertFalse(b.plainText(), b.plainText().contains("%"));
        assertSame(ClassicAdvisorBox.Portrait.PRIEST, b.portrait);
        assertEquals(3, b.rows.size());
        assertFalse(b.escapes);
        assertEquals(ClassicMenuBox.LIST_INDENT, b.rowIndent);
        final ClassicAdvisorBox.Request s = ClassicSeams.emigrationRequest(t,
            game.getPlayerByNationId("model.nation.spanish"), false, rs, "t");
        assertTrue(s.plainText(), s.plainText().contains("Sevilla"));
        final ClassicAdvisorBox.Request y = ClassicSeams.emigrationRequest(t, dutch,
            true, rs, "t");
        assertEquals(ClassicSeams.LOST_CITY_CHOOSE, y.id);
        assertSame(ClassicAdvisorBox.Portrait.SCOUT, y.portrait);
        assertEquals(3, y.rows.size());
        assertEquals(0, y.defaultRow);
        assertFalse(y.escapes);
    }

    /**
     * G1 (N15): a proposal sent to us.  The first contact's peace with
     * another European nation is accepted at once, no box (the server waits
     * 1000 hours for it otherwise); any other is a box with "Annehmen" and
     * "Abbrechen", the bar and Escape on "Abbrechen"; each answers the
     * handler once.  Our own fresh proposal is not built yet: the "not
     * yet" notice, and nothing is sent.
     */
    public void testNegotiation() throws Exception {
        final Game game = getStandardGame();
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final Player french = game.getPlayerByNationId("model.nation.french");
        final ClassicGUI gui = new ClassicGUI(null);
        final FakePrompter fake = new FakePrompter();
        gui.prompter = fake;
        final List<DiplomaticTrade> got = new ArrayList<>();
        final DiplomaticTrade peace = DiplomaticTrade.makePeaceTreaty(
            DiplomaticTrade.TradeContext.CONTACT, dutch, french);
        assertTrue(ClassicSeams.isContactPeace(peace));
        assertFalse(ClassicSeams.isOwnProposal(peace));
        final List<net.sf.freecol.common.model.TradeItem> items
            = new ArrayList<>(peace.getItems());
        onEdt(() -> gui.showNegotiationDialog(null, null, peace,
            peace.getReceiveMessage(french), got::add));
        assertEquals(1, got.size());
        assertSame(peace, got.get(0));
        assertEquals(DiplomaticTrade.TradeStatus.ACCEPT_TRADE, peace.getStatus());
        assertEquals(items, peace.getItems());          // unchanged
        assertTrue(fake.boxes.isEmpty());

        // Gold: a box, "Abbrechen" barred.
        final DiplomaticTrade gold = new DiplomaticTrade(game,
            DiplomaticTrade.TradeContext.DIPLOMATIC, french, dutch,
            List.of(new GoldTradeItem(game, french, dutch, 100)), 1);
        assertFalse(ClassicSeams.isContactPeace(gold));
        assertFalse(ClassicSeams.isOwnProposal(gold));
        fake.answer = 1;
        onEdt(() -> gui.showNegotiationDialog(null, null, gold,
            gold.getReceiveMessage(french), got::add));
        assertEquals(2, got.size());
        assertSame(gold, last(got));
        assertEquals(DiplomaticTrade.TradeStatus.REJECT_TRADE, gold.getStatus());
        final ClassicAdvisorBox.Request box = last(fake.boxes);
        assertEquals(Messages.message("negotiationDialog.accept"), box.plainRows()[0]);
        assertEquals(Messages.message("negotiationDialog.cancel"), box.plainRows()[1]);
        assertEquals(1, box.defaultRow);
        assertEquals(1, box.cancelRow);
        assertTrue(box.escapes);
        assertFalse(box.outsideCancels);
        assertTrue(box.plainText(), box.plainText().contains(ClassicAdvisorBox.literal(
            Messages.message(gold.getItems().get(0).getLabel()))));
        assertTrue(box.plainText(), box.plainText().contains(ClassicAdvisorBox.literal(
            Messages.message(gold.getReceiveMessage(french)))));
        final KeyPrompter keys = new KeyPrompter();
        gui.prompter = keys;
        final Object[][] cases = {
            { new String[] { "ENTER" }, DiplomaticTrade.TradeStatus.REJECT_TRADE },
            { new String[] { "UP", "ENTER" }, DiplomaticTrade.TradeStatus.ACCEPT_TRADE },
            { new String[] { "ESC" }, DiplomaticTrade.TradeStatus.REJECT_TRADE },
            { new String[] { "UP", "ESC" }, DiplomaticTrade.TradeStatus.REJECT_TRADE },
            { new String[] { "OUT" }, DiplomaticTrade.TradeStatus.REJECT_TRADE },
        };
        for (Object[] c : cases) {
            gold.setStatus(DiplomaticTrade.TradeStatus.PROPOSE_TRADE);
            final int before = got.size();
            keys.press((String[]) c[0]);
            onEdt(() -> gui.showNegotiationDialog(null, null, gold, null, got::add));
            assertEquals(before + 1, got.size());
            assertEquals(String.join(" ", (String[]) c[0]), c[1], gold.getStatus());
        }
        // A contact treaty that asks for more than peace is no plain peace.
        final DiplomaticTrade more = DiplomaticTrade.makePeaceTreaty(
            DiplomaticTrade.TradeContext.CONTACT, dutch, french);
        more.add(new GoldTradeItem(game, dutch, french, 50));
        assertFalse(ClassicSeams.isContactPeace(more));

        // Our own fresh proposal: the "not yet" notice, null to the handler.
        gui.prompter = fake;
        final int boxes = fake.boxes.size();
        final DiplomaticTrade own = new DiplomaticTrade(game,
            DiplomaticTrade.TradeContext.DIPLOMATIC, dutch, french, null, 0);
        assertTrue(ClassicSeams.isOwnProposal(own));
        onEdt(() -> gui.showNegotiationDialog(null, null, own, null, got::add));
        assertNull(last(got));
        assertEquals(boxes + 1, fake.boxes.size());
        assertTrue(last(fake.boxes).isNotice());
        assertEquals(Messages.message("classic.mainMenu.notYet"), last(fake.texts));
    }

    /**
     * G1 (N15), on the server: the first contact of our land unit with a
     * European one opens a diplomacy session of 1000 hours (single
     * player) that the end of turn waits for; the seam's answer, the peace
     * without a box, ends it, and the two nations are at peace.
     */
    public void testEuropeanContactSessionCompletes() throws Exception {
        final Game game = ServerTestHelper.startServerGame(getTestMap(true));
        try {
            final net.sf.freecol.server.control.InGameController igc
                = ServerTestHelper.getInGameController();
            final Map map = game.getMap();
            final ServerPlayer dutch = getServerPlayer(game, "model.nation.dutch");
            final ServerPlayer french = getServerPlayer(game, "model.nation.french");
            final Tile here = map.getTile(5, 8);
            final Tile there = here.getNeighbourOrNull(Direction.N);
            final Unit scout = new ServerUnit(game, here, dutch,
                spec().getUnitType("model.unit.freeColonist"));
            final Unit other = new ServerUnit(game, there, french,
                spec().getUnitType("model.unit.freeColonist"));
            assertNotSame(net.sf.freecol.common.model.Stance.PEACE,
                          dutch.getStance(french));
            dutch.csEuropeanFirstContact(scout, null, other,
                new net.sf.freecol.common.networking.ChangeSet());
            assertTrue(Session.waitingForSession());   // the freeze, unanswered
            final net.sf.freecol.server.model.DiplomacySession ds
                = net.sf.freecol.server.model.DiplomacySession
                .findContactSession(scout, other);
            assertNotNull(ds);
            final DiplomaticTrade dt = ds.getAgreement();
            assertTrue(ClassicSeams.isContactPeace(dt));
            final ClassicGUI gui = new ClassicGUI(null);
            final FakePrompter fake = new FakePrompter();
            gui.prompter = fake;
            onEdt(() -> gui.showNegotiationDialog(scout, other, dt, null,
                    a -> igc.europeanFirstContact(dutch, scout, null, other, null, a)));
            assertTrue(fake.boxes.isEmpty());
            assertFalse("the session ended", Session.waitingForSession());
            assertSame(net.sf.freecol.common.model.Stance.PEACE, dutch.getStance(french));
        } finally {
            Session.clearAll();
            ServerTestHelper.stopServerGame();
        }
    }

    /**
     * G1 (N15): a village's and a tile's facts as notices in FreeCol's
     * words; nothing for null or an unexplored tile.  The village notice
     * fits the screen (nothing is cut).
     */
    public void testVillageAndTileNotices() throws Exception {
        final Game game = getStandardGame();
        final Map map = getTestMap(true);
        game.changeMap(map);
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final IndianSettlement is = new IndianSettlementBuilder(game)
            .player(game.getPlayerByNationId("model.nation.arawak")).build();
        final ClassicGUI gui = new ClassicGUI(null) {
                @Override
                Player myPlayer() {
                    return dutch;
                }

                @Override
                java.awt.Image iconOf(net.sf.freecol.common.model.FreeColObject d) {
                    return null;   // no image resources in the test
                }
            };
        final FakePrompter fake = new FakePrompter();
        gui.prompter = fake;
        final Object[] ret = { "x" };
        onEdt(() -> ret[0] = gui.showIndianSettlementPanel(is));
        assertNull(ret[0]);
        assertEquals(1, fake.boxes.size());
        final ClassicAdvisorBox.Request v = last(fake.boxes);
        assertTrue(v.isNotice());
        final String text = v.plainText();
        for (String s : new String[] {
                Messages.message(is.getLocationLabelFor(dutch)),
                Messages.message("indianSettlementPanel.learnableSkill"),
                Messages.message(is.getLearnableSkillLabel(false)),
                Messages.message("indianSettlementPanel.mostHated"),
                // Not visited: FreeCol's "Unbekannt", not an empty value.
                Messages.message("indianSettlementPanel.highlyWanted") + " "
                    + Messages.message("model.indianSettlement.wantedGoodsUnknown") }) {
            assertTrue(s + " in " + text, text.contains(ClassicAdvisorBox.literal(s)));
        }
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final ClassicFont tiny = (pack == null) ? null : pack.font(ClassicFont.TINY);
        if (tiny != null) {
            final ClassicAdvisorBox.Layout l = ClassicAdvisorBox.layout(v, tiny, null);
            assertNotNull(l);
            for (ClassicTextLayout.Line line : l.prompt) {
                assertFalse(line.marked, line.marked.endsWith(" ..."));
            }
        }
        final Tile tile = map.getTile(3, 4);
        onEdt(() -> ret[0] = gui.showTilePanel(tile));
        assertNull(ret[0]);
        assertEquals(2, fake.boxes.size());
        assertTrue(last(fake.boxes).isNotice());
        assertTrue(last(fake.texts), last(fake.texts).contains("(3, 4)"));
        assertTrue(last(fake.texts), last(fake.texts).contains(
            Messages.message("tilePanel.movementCost")));
        onEdt(() -> {
                gui.showIndianSettlementPanel(null);
                gui.showTilePanel(null);
            });
        assertEquals(2, fake.boxes.size());
    }

    /**
     * A classic GUI without a client whose woodcuts and boxes are fakes:
     * it keeps the order in which they came ("woodcut k", the box's id).
     */
    private static class WoodcutGUI extends ClassicGUI {

        final Game game;
        final Player me;
        final List<String> order = new ArrayList<>();
        final List<Integer> woodcutsCut = new ArrayList<>();

        /** What the fake woodcutter answers: shown (1) or not (NOT_SHOWN). */
        long shows = 1L;

        WoodcutGUI(Game game, Player me) {
            super(null);
            this.game = game;
            this.me = me;
            this.woodcutter = (k, notBefore, followMs) -> {
                this.order.add("woodcut " + k);
                this.woodcutsCut.add(k);
                return this.shows;
            };
            this.prompter = r -> {
                this.order.add(r.id);
                return ClassicAdvisorBox.Bar.DISMISSED;
            };
        }

        @Override
        protected Game getGame() {
            return this.game;
        }

        @Override
        Player myPlayer() {
            return this.me;
        }

        @Override
        protected Player getMyPlayer() {
            return this.me;
        }

        @Override
        java.awt.Image iconOf(net.sf.freecol.common.model.FreeColObject d) {
            return null;   // no image resources in the test
        }
    }

    /**
     * W9/N17: the first meeting with a native nation shows woodcut 3
     * before the first-contact box, once per game (V: clip004's Sioux
     * after the landfall's Arawaks has none); the Aztecs and the Incas have
     * their own (4, 5), which count as 3 too.  The meeting sound of a
     * European nation and a European contact's negotiation show 10, once;
     * a native nation's meeting sound shows nothing.  A woodcut that could
     * not be shown is not marked: it comes at the next trigger.
     */
    public void testWoodcutsOfTheMeetings() throws Exception {
        final Game game = getStandardGame();
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final Player arawak = game.getPlayerByNationId("model.nation.arawak");
        final Player sioux = game.getPlayerByNationId("model.nation.sioux");
        final Player aztec = game.getPlayerByNationId("model.nation.aztec");
        final Player inca = game.getPlayerByNationId("model.nation.inca");
        final Player english = game.getPlayerByNationId("model.nation.english");
        final WoodcutGUI gui = new WoodcutGUI(game, dutch);
        gui.shows = ClassicAdvisorLayer.NOT_SHOWN;      // no canvas: not marked
        onEdt(() -> gui.showFirstContactDialog(dutch, arawak, null, 3, b -> { }));
        assertEquals(List.of("woodcut 3", "first-contact arawak"), gui.order);
        assertEquals(0, gui.woodcutsShown());
        gui.shows = 1L;
        gui.order.clear();
        onEdt(() -> gui.showFirstContactDialog(dutch, arawak, null, 3, b -> { }));
        onEdt(() -> gui.showFirstContactDialog(dutch, sioux, null, 3, b -> { }));
        onEdt(() -> gui.showFirstContactDialog(dutch, aztec, null, 3, b -> { }));
        onEdt(() -> gui.showFirstContactDialog(dutch, inca, null, 3, b -> { }));
        onEdt(() -> gui.showFirstContactDialog(dutch, aztec, null, 3, b -> { }));
        assertEquals(List.of("woodcut 3", "first-contact arawak", "first-contact sioux",
                             "woodcut 4", "first-contact aztec", "woodcut 5",
                             "first-contact inca", "first-contact aztec"), gui.order);
        assertEquals(ClassicWoodcut.bit(3) | ClassicWoodcut.bit(4) | ClassicWoodcut.bit(5),
                     gui.woodcutsShown());
        assertEquals(gui.woodcutsShown(), dutch.getClassicWoodcuts());
        // The Aztecs first: 4, and 3 counts as shown.
        final Game g2 = getStandardGame();
        final Player d2 = g2.getPlayerByNationId("model.nation.dutch");
        final WoodcutGUI gui2 = new WoodcutGUI(g2, d2);
        onEdt(() -> gui2.showFirstContactDialog(d2, g2.getPlayerByNationId("model.nation.aztec"),
                                                null, 3, b -> { }));
        onEdt(() -> gui2.showFirstContactDialog(d2, g2.getPlayerByNationId("model.nation.tupi"),
                                                null, 3, b -> { }));
        assertEquals(List.of("woodcut 4", "first-contact aztec", "first-contact tupi"),
                     gui2.order);
        assertTrue(gui2.woodcutShown(ClassicWoodcut.NATIVES));
        // Europeans: the native meeting sound shows nothing, the European's 10.
        gui.order.clear();
        onEdt(() -> gui.playSound("sound.event.meet." + arawak.getNationId()));
        onEdt(() -> gui.playSound("sound.event.buildingComplete"));
        assertTrue(gui.order.isEmpty());
        onEdt(() -> gui.playSound("sound.event.meet." + english.getNationId()));
        onEdt(() -> gui.playSound("sound.event.meet." + english.getNationId()));
        assertEquals(List.of("woodcut 10"), gui.order);
        // A European contact's negotiation: 10, once (our own proposal:
        // the "not yet" notice after it).
        final WoodcutGUI gui3 = new WoodcutGUI(game, english);
        final DiplomaticTrade dt = new DiplomaticTrade(game,
            DiplomaticTrade.TradeContext.CONTACT, english, dutch, new ArrayList<>(), 0);
        onEdt(() -> gui3.showNegotiationDialog(null, null, dt, null, a -> { }));
        onEdt(() -> gui3.showNegotiationDialog(null, null, dt, null, a -> { }));
        assertEquals(List.of("woodcut 10", "negotiation", "negotiation"), gui3.order);
    }

    /**
     * W9/N17: the Pacific's woodcut 6 at FreeCol's event, once; the first
     * landing's event shows nothing and its closing callback still runs.
     * The notices' woodcuts before their boxes, once each: the Fountain of
     * Youth (8, also when its recruit box comes first), the first laden
     * ship in Europe (9; an empty one: none), a burning (11), a destroyed
     * (12) and a raided colony (13); a dropped notice still brings its
     * woodcut.
     */
    public void testWoodcutsOfTheEventsAndNotices() throws Exception {
        final Game game = getStandardGame();
        final Map map = getTestMap(true);
        game.changeMap(map);
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final WoodcutGUI gui = new WoodcutGUI(game, dutch);
        final int[] runs = { 0 };
        onEdt(() -> gui.showEventPanel("h", "image.flavor.event.firstLanding", null)
              .addClosingCallback(() -> runs[0]++));
        assertEquals(1, runs[0]);
        assertTrue(gui.order.isEmpty());
        onEdt(() -> gui.showEventPanel("h", ClassicGUI.PACIFIC_IMAGE, null));
        onEdt(() -> gui.showEventPanel("h", ClassicGUI.PACIFIC_IMAGE, null));
        assertEquals(List.of("woodcut 6"), gui.order);
        // The notices.
        final Unit empty = new ServerUnit(game, map.getTile(10, 4), dutch,
            spec().getUnitType("model.unit.merchantman"));
        final Unit laden = new ServerUnit(game, map.getTile(10, 5), dutch,
            spec().getUnitType("model.unit.merchantman"));
        laden.addGoods(spec().getGoodsType("model.goods.furs"), 15);
        final net.sf.freecol.common.model.ModelMessage.MessageType t
            = net.sf.freecol.common.model.ModelMessage.MessageType.DEFAULT;
        final java.util.function.BiFunction<String, net.sf.freecol.common.model.FreeColGameObject,
            net.sf.freecol.common.model.ModelMessage> msg = (id, display)
            -> new net.sf.freecol.common.model.ModelMessage(t, id, dutch, display);
        gui.order.clear();
        onEdt(() -> gui.showModelMessages(List.of(
            msg.apply("model.unit.arriveInEurope", empty),
            msg.apply("model.unit.arriveInEurope", laden),
            msg.apply("model.unit.arriveInEurope", laden),
            msg.apply("combat.raid.building", dutch),
            msg.apply("combat.colonyBurned.ours", dutch),
            msg.apply("combat.raid.ours", dutch),
            msg.apply("combat.raid.ours", dutch))));
        assertEquals(List.of("message model.unit.arriveInEurope", "woodcut 9",
                             "message model.unit.arriveInEurope",
                             "message model.unit.arriveInEurope",
                             "woodcut 11", "message combat.raid.building",
                             "woodcut 12", "message combat.colonyBurned.ours",
                             "woodcut 13", "message combat.raid.ours",
                             "message combat.raid.ours"), gui.order);
        // The Fountain of Youth: its recruit box first brings it, its
        // notice later none.
        gui.order.clear();
        onEdt(() -> gui.showEmigrationDialog(dutch, true, i -> { }));
        onEdt(() -> gui.showModelMessages(List.of(msg.apply(
            "model.lostCityRumour.fountainOfYouth.description", dutch))));
        assertEquals("woodcut 8", gui.order.get(0));
        assertEquals(1, java.util.Collections.frequency(gui.order, "woodcut 8"));
        // A notice dropped by the colony report options still brings its
        // woodcut (I), in a game where it is new.
        final WoodcutGUI gui2 = new WoodcutGUI(game, dutch) {
                @Override
                List<net.sf.freecol.common.model.ModelMessage> noticesShown(
                    List<net.sf.freecol.common.model.ModelMessage> messages) {
                    return new ArrayList<>();
                }
            };
        onEdt(() -> gui2.showModelMessages(List.of(msg.apply("combat.raid.ours", dutch))));
        assertEquals(List.of("woodcut 13"), gui2.order);
    }

    /**
     * W9: the first colony's woodcut 2 comes between the founding (the
     * name, {@code getNewColonyName}) and its colony screen; opening a
     * colony later, and a second founding, show none.  The first village
     * entry's woodcut 7 before the village box or the learn question when
     * no key brought it, once.
     */
    public void testWoodcutsOfTheColonyAndTheVillage() throws Exception {
        final Game game = getStandardGame();
        final Map map = getTestMap(true);
        game.changeMap(map);
        final Colony colony = createStandardColony();
        final Player dutch = colony.getOwner();
        final WoodcutGUI gui = new WoodcutGUI(game, dutch);
        // Opening a colony without a founding: nothing.
        onEdt(() -> gui.showColonyPanel(colony, null));
        onEdt(() -> { });
        assertTrue(gui.order.isEmpty());
        onEdt(() -> gui.getNewColonyName(dutch, colony.getTile()));
        onEdt(() -> gui.showColonyPanel(colony, null));
        onEdt(() -> { });
        assertEquals(List.of("woodcut 2"), gui.order);
        onEdt(() -> gui.getNewColonyName(dutch, colony.getTile()));
        onEdt(() -> gui.showColonyPanel(colony, null));
        onEdt(() -> { });
        assertEquals(List.of("woodcut 2"), gui.order);
        // The village: the box of a goto or a click (the four village
        // seams call villageWoodcut first; their boxes need FreeCol's
        // images, so the test calls it alone), then the learn question,
        // the woodcut only before the first; a colony is no village.
        final IndianSettlement is = new IndianSettlementBuilder(game)
            .player(game.getPlayerByNationId("model.nation.arawak"))
            .settlementTile(map.getTile(12, 12)).build();
        gui.order.clear();
        onEdt(() -> gui.villageWoodcut(colony));
        assertTrue(gui.order.isEmpty());
        onEdt(() -> gui.villageWoodcut(is));
        onEdt(() -> gui.modalConfirmDialog(map.getTile(12, 11), StringTemplate
            .template(ClassicGUI.LEARN_QUESTION).addName("%skill%", "x"),
            (ImageIcon) null, "learnSkill.yes", "learnSkill.no", true));
        assertEquals(List.of("woodcut 7", "confirm " + ClassicGUI.LEARN_QUESTION), gui.order);
        // The learn question first in a new game.
        final WoodcutGUI gui2 = new WoodcutGUI(game, dutch);
        onEdt(() -> gui2.modalConfirmDialog(map.getTile(12, 11), StringTemplate
            .template(ClassicGUI.LEARN_QUESTION).addName("%skill%", "x"),
            (ImageIcon) null, "learnSkill.yes", "learnSkill.no", true));
        assertEquals("woodcut 7", gui2.order.get(0));
        // The key's rule: a move into the village's tile, not a refused one.
        assertTrue(ClassicWoodcut.entersVillage(Unit.MoveType.ENTER_INDIAN_SETTLEMENT_WITH_SCOUT,
                                                is.getTile()));
        assertTrue(ClassicWoodcut.entersVillage(Unit.MoveType.ATTACK_SETTLEMENT, is.getTile()));
        assertTrue(ClassicWoodcut.entersVillage(
            Unit.MoveType.ENTER_SETTLEMENT_WITH_CARRIER_AND_GOODS, is.getTile()));
        assertFalse(ClassicWoodcut.entersVillage(Unit.MoveType.MOVE_NO_ACCESS_SETTLEMENT,
                                                 is.getTile()));
        assertFalse(ClassicWoodcut.entersVillage(Unit.MoveType.ATTACK_SETTLEMENT,
                                                 colony.getTile()));
        assertFalse(ClassicWoodcut.entersVillage(Unit.MoveType.MOVE, map.getTile(3, 3)));
        assertFalse(ClassicWoodcut.entersVillage(null, is.getTile()));
        // The key: woodcut 7 before the move when the move enters a village.
        final Unit scout = new ServerUnit(game, map.getTile(12, 11), dutch,
            spec().getUnitType("model.unit.seasonedScout"));
        final Direction toVillage = map.getDirection(scout.getTile(), is.getTile());
        final WoodcutGUI gui3 = new WoodcutGUI(game, dutch);
        final boolean[] goOn = { false };
        onEdt(() -> goOn[0] = gui3.villageEntryKey(scout, toVillage.getReverseDirection()));
        assertTrue(goOn[0]);
        assertTrue(gui3.order.isEmpty());
        if (ClassicWoodcut.entersVillage(scout.getMoveType(toVillage), is.getTile())) {
            onEdt(() -> goOn[0] = gui3.villageEntryKey(scout, toVillage));
            assertFalse(goOn[0]);                      // no map viewer: the move is dropped
            assertEquals(List.of("woodcut 7"), gui3.order);
        }
    }

    /**
     * W9: the land sighted at a final draw posts woodcut 1, due at once
     * (the turn flow waits: busy), 57 ms after the final draw, then the
     * New World's naming seam (W10) with the map's return; once per game,
     * also for a second sighting before the first one ran.  A woodcut
     * marked shown is in our player's record; one that could not be shown
     * comes at the next sighting.
     */
    public void testWoodcutOfTheDiscovery() throws Exception {
        final Game game = getStandardGame();
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final List<Long> named = new ArrayList<>();
        final List<Long> notBefore = new ArrayList<>();
        final WoodcutGUI gui = new WoodcutGUI(game, dutch) {
                @Override
                void discoveryShown(long mapBackNanos) {
                    named.add(mapBackNanos);
                }
            };
        final ClassicGUI.Woodcutter fake = gui.woodcutter;
        gui.woodcutter = (k, nb, f) -> {
            notBefore.add(nb);
            assertEquals(ClassicWoodcut.FOLLOW_DISCOVERY_MS, f);
            return fake.show(k, nb, f);
        };
        gui.shows = ClassicAdvisorLayer.NOT_SHOWN;
        final boolean[] busy = { false, true };
        onEdt(() -> {
                gui.landSighted(1_000_000_000L);
                gui.landSighted(1_000_000_001L);       // the same move: once
                busy[0] = gui.boxBusy();
            });
        onEdt(() -> busy[1] = gui.boxBusy());
        assertTrue(busy[0]);
        assertFalse(busy[1]);
        assertEquals(List.of("woodcut 1"), gui.order);
        assertEquals(List.of(1_057_000_000L), notBefore);
        assertTrue(named.isEmpty());                   // not shown: no naming
        assertEquals(0, dutch.getClassicWoodcuts());
        gui.shows = 1L;
        onEdt(() -> gui.landSighted(2_000_000_000L));
        onEdt(() -> { });
        onEdt(() -> gui.landSighted(3_000_000_000L));
        onEdt(() -> { });
        assertEquals(List.of("woodcut 1", "woodcut 1"), gui.order);
        assertEquals(List.of(1L), named);
        assertEquals(ClassicWoodcut.bit(ClassicWoodcut.DISCOVERY), dutch.getClassicWoodcuts());
    }

    /**
     * W9: the woodcuts a save without the record counts as shown, from the
     * traces of their events (spec G5 section 4.2): explored land, a
     * colony, a met native nation (the Aztecs, the Incas), a visited
     * village, goods sold in Europe, a met European.
     */
    public void testDerivedWoodcuts() {
        final Game game = getStandardGame();
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        assertEquals(0, ClassicWoodcut.derived(null, null));
        assertEquals(0, ClassicWoodcut.derived(dutch, null));
        final Map map = getTestMap(true);
        game.changeMap(map);
        int d = ClassicWoodcut.derived(dutch, map);
        assertTrue((d & ClassicWoodcut.bit(ClassicWoodcut.DISCOVERY)) != 0);
        for (int k : new int[] { ClassicWoodcut.COLONY, ClassicWoodcut.NATIVES,
                                 ClassicWoodcut.AZTECS, ClassicWoodcut.VILLAGE,
                                 ClassicWoodcut.CARGO, ClassicWoodcut.EUROPEANS,
                                 ClassicWoodcut.FOUNTAIN, ClassicWoodcut.RAID }) {
            assertEquals("k=" + k, 0, d & ClassicWoodcut.bit(k));
        }
        final Player aztec = game.getPlayerByNationId("model.nation.aztec");
        final Player english = game.getPlayerByNationId("model.nation.english");
        dutch.setStance(aztec, net.sf.freecol.common.model.Stance.PEACE);
        dutch.setStance(english, net.sf.freecol.common.model.Stance.PEACE);
        createStandardColony();
        d = ClassicWoodcut.derived(dutch, map);
        for (int k : new int[] { ClassicWoodcut.COLONY, ClassicWoodcut.NATIVES,
                                 ClassicWoodcut.AZTECS, ClassicWoodcut.EUROPEANS }) {
            assertTrue("k=" + k, (d & ClassicWoodcut.bit(k)) != 0);
        }
        assertEquals(0, d & ClassicWoodcut.bit(ClassicWoodcut.INCAS));
        assertEquals(0, d & ClassicWoodcut.bit(ClassicWoodcut.FOUNTAIN));
    }

    private static <T> T last(List<T> l) {
        return l.get(l.size() - 1);
    }
}
