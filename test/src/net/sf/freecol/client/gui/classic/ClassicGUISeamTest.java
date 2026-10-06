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
import java.util.List;

import javax.swing.ImageIcon;

import net.sf.freecol.FreeCol;
import net.sf.freecol.client.gui.ChoiceItem;
import net.sf.freecol.client.gui.action.ReturnToEuropeAction;
import net.sf.freecol.client.gui.panel.FreeColPanel;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.io.FreeColRules;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Direction;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Monarch.MonarchAction;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.Topology;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.option.GameOptions;
import net.sf.freecol.server.model.ServerUnit;
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
     * Keys: UP, DOWN, ENTER, ESC, X (any other key).
     */
    static final class KeyPrompter implements ClassicGUI.Prompter {

        String[] keys = {};
        final List<ClassicAdvisorBox.Request> boxes = new ArrayList<>();

        /** The bar's row after the keys of the last box (if it stayed open). */
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
            for (String k : ks) {
                final int a;
                switch (k) {
                case "UP": a = bar.up(); break;
                case "DOWN": a = bar.down(); break;
                case "ENTER": a = bar.enter(); break;
                case "ESC": a = bar.escape(); break;
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
     * warnings (land-locked), the landing, the rumour and the hostile
     * action, with their controller's {@code defaultOk}.
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
            { StringTemplate.key("disembark.text"), "ok", "cancel", true },
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
                StringTemplate.key("disembark.text"), (ImageIcon) null,
                "ok", "cancel", true));
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
    }

    /**
     * The king's tax rise (C FINAL trap 1, build spec W24): the bar starts
     * on the original's first row, "Den königlichen Ring küssen" =
     * FreeCol's "yes" (clip005 #17489, clip006 #7380), so Enter kisses the
     * ring; Down and Enter, "no" and Escape hold the party (Roger's rule:
     * Escape answers no).  The mercenary offers list the "no" first with the
     * bar on it (the original's @MERCENARIES); a notice has no rows and any
     * key dismisses it.  The King stands at the left (W7's King exception).
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
            assertEquals(Boolean.TRUE, last(answers));        // the ring
            fake.answer = 1;
            gui.showMonarchDialog(a, t, "model.nation.dutch", answers::add);
            assertEquals(Boolean.FALSE, last(answers));       // the party
            fake.answer = -1;
            gui.showMonarchDialog(a, t, "model.nation.dutch", answers::add);
            assertEquals("Escape, " + a, Boolean.FALSE, last(answers));
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
            assertEquals(Boolean.FALSE, last(answers));
            fake.answer = 1;
            gui.showMonarchDialog(a, t, "model.nation.dutch", answers::add);
            assertEquals(Boolean.TRUE, last(answers));
        }
        // A notice (no "yes"): no rows, answered "no".
        fake.answer = 0;
        gui.showMonarchDialog(MonarchAction.LOWER_TAX_WAR, t,
                              "model.nation.dutch", answers::add);
        assertEquals(0, last(fake.asked).length);
        assertTrue(last(fake.boxes).isNotice());
        assertEquals(Integer.valueOf(-1), last(fake.defaults));
        assertEquals(Boolean.FALSE, last(answers));
        assertFalse(ClassicGUI.monarchEnterAccepts(null));
        assertEquals(11, fake.asked.size());
        assertEquals(11, answers.size());

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
            { tax, new String[] { "ESC" }, false },
            { tax, new String[] { "DOWN", "ESC" }, false },
            { merc, new String[] { "ENTER" }, false },
            { merc, new String[] { "DOWN", "ENTER" }, true },
            { merc, new String[] { "DOWN", "ESC" }, false },
            { MonarchAction.LOWER_TAX_WAR, new String[] { "X" }, false },
            { MonarchAction.LOWER_TAX_WAR, new String[] { "ENTER" }, false },
        };
        for (Object[] c : cases) {
            keys.press((String[]) c[1]);
            gui.showMonarchDialog((MonarchAction) c[0], t, "model.nation.dutch",
                                  answers::add);
            assertEquals(c[0] + " " + String.join(" ", (String[]) c[1]),
                         c[2], last(answers));
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
            assertFalse(ClassicGUI.goneWithNothingLeft(ship));

            // It sails for Europe: off the map, nothing else to move.
            assertNotNull(dutch.getHighSeas());
            ship.setLocation(dutch.getHighSeas());
            assertFalse(ship.hasTile());
            assertTrue(ClassicGUI.goneWithNothingLeft(ship));
            gui.changeView(ship, true);             // the controller's redisplay
            assertNull(mv.getActiveUnit());
            assertEquals(net.sf.freecol.client.gui.GUI.ViewMode.END_TURN,
                         mv.getViewMode());

            // A colonist that can still move: the choice goes on as before.
            final Unit colonist = new ServerUnit(game, land, dutch,
                spec().getUnitType("model.unit.freeColonist"));
            assertTrue(colonist.getMovesLeft() > 0);
            assertFalse(ClassicGUI.goneWithNothingLeft(ship));
            gui.changeView(ship, true);
            assertSame(ship, mv.getActiveUnit());
            assertEquals(net.sf.freecol.client.gui.GUI.ViewMode.MOVE_UNITS,
                         mv.getViewMode());
            assertFalse(ClassicGUI.goneWithNothingLeft(null));
            assertFalse(ClassicGUI.goneWithNothingLeft(colonist));
        } finally {
            mv.dispose();
        }
    }

    private static <T> T last(List<T> l) {
        return l.get(l.size() - 1);
    }
}
