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

import java.awt.Window;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import net.sf.freecol.client.gui.ChoiceItem;
import net.sf.freecol.client.gui.panel.FreeColPanel;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Direction;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.Topology;
import net.sf.freecol.common.model.Unit;
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

    /** Answers every box with {@link #answer} and keeps what it was asked. */
    private static final class FakePrompter implements ClassicGUI.Prompter {

        int answer = -1;
        Object choice = null;
        final List<String[]> asked = new ArrayList<>();
        final List<Integer> defaults = new ArrayList<>();
        final List<String> texts = new ArrayList<>();
        int choices = 0;

        @Override
        public int ask(Window owner, String title, ClassicDialog.Page page,
                       String[] options, int defaultIndex) {
            this.asked.add(options);
            this.defaults.add(defaultIndex);
            this.texts.add(page.text);
            return this.answer;
        }

        @Override
        public Object choose(Window owner, String title, Object message,
                             Icon icon, Object[] options) {
            this.choices++;
            return this.choice;
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
        gui.prompter = ClassicGUI.Prompter.BOXES;
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
        fake.choice = null;
        assertNull(gui.modalChoiceDialog(null, StringTemplate.key("x"),
                                         (ImageIcon) null, "cancel", items));
        fake.choice = items.get(1);
        assertEquals("tribute", gui.modalChoiceDialog(null,
                StringTemplate.key("x"), (ImageIcon) null, "cancel", items));
        assertEquals(2, fake.choices);
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
        // The west edge asks nothing.
        assertFalse(ClassicHud.eastPastView(w, Direction.W, 1, 0));
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
     * The question's words come from GAME.TXT @SAILHOME (markup dropped,
     * Enter on @default), else from FreeCol's high-seas strings.  The test
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
            final ClassicGUI.SailHomeText q = ClassicGUI.sailHomeText(t, null);
            assertEquals("We are on open water. Go home?", q.text);
            assertEquals("Yes, home.", q.options[0]);
            assertEquals("No, stay.", q.options[1]);
            assertEquals(0, q.defaultIndex);
        } finally {
            for (File f : dir.listFiles()) f.delete();
            dir.delete();
        }
        final ClassicGUI.SailHomeText f = ClassicGUI.sailHomeText(null, null);
        assertEquals(Messages.message("highseas.yes"), f.options[0]);
        assertEquals(Messages.message("highseas.no"), f.options[1]);
        assertEquals(0, f.defaultIndex);
        assertFalse(f.text.isEmpty());
    }

    private static <T> T last(List<T> l) {
        return l.get(l.size() - 1);
    }
}
