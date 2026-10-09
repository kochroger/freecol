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

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Constants.ArmedUnitSettlementAction;
import net.sf.freecol.common.model.Constants.ScoutColonyAction;
import net.sf.freecol.common.model.Direction;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.Topology;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;
import net.sf.freecol.util.test.FreeColTestUtils;


/**
 * Part N6: our units at a foreign colony ({@link ClassicForeignColony}) and
 * the original's questions before an attack
 * ({@link ClassicGUI#confirmHostileAction}).  The scout's @SCOUTCOLONY:
 * words, rows, bar, Escape, frontiersman and place, 0 px against
 * playthrough-1 #41523; a pioneer's same box with infiltrate and attack
 * greyed (Roger 2026-10-09 09:50), «Bürgermeister treffen» and «Nichts»
 * spending nothing; @HAVETREATY before an attack on a colony at peace,
 * none at war or at a village after «Dorf angreifen», @WHACKINDIANS
 * before an attack on a brave (0 px against landfall #24736).
 */
public class ClassicForeignColonyTest extends FreeColTestCase {

    private static final TileType plains = spec().getTileType("model.tile.plains");

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

    /** The pack's texts, or null (then a note: the pack-bound checks are skipped). */
    private ClassicText texts(String test) {
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        if (t == null || t.message(ClassicForeignColony.SECTION) == null) {
            System.err.println(getClass().getSimpleName() + "." + test
                + ": no pack texts (ant classic-assets), skipped");
            return null;
        }
        return t;
    }

    private static Player player(Game game, String nation) {
        return game.getPlayerByNationId("model.nation." + nation);
    }

    /** A test world: Montreal (French) at (5,8), the Dutch and French at peace. */
    private static final class World {
        final Game game;
        final Map map;
        final Player dutch, french, arawak;
        final Colony montreal;

        World() {
            this.game = getStandardGame();
            this.map = getTestMap(plains);
            this.game.changeMap(this.map);
            this.dutch = player(this.game, "dutch");
            this.french = player(this.game, "french");
            this.arawak = player(this.game, "arawak");
            this.dutch.setStance(this.french, Stance.PEACE);
            this.french.setStance(this.dutch, Stance.PEACE);
            this.montreal = FreeColTestUtils.getColonyBuilder().player(this.french)
                .colonyName("Montreal").colonyTile(this.map.getTile(5, 8))
                .initialColonists(1).build();
        }

        /** A unit of ours east of Montreal. */
        Unit east(String type, String role) {
            final Tile t = this.montreal.getTile().getNeighbourOrNull(Direction.E);
            return (role == null)
                ? new ServerUnit(this.game, t, this.dutch,
                                 spec().getUnitType("model.unit." + type))
                : new ServerUnit(this.game, t, this.dutch,
                                 spec().getUnitType("model.unit." + type),
                                 spec().getRole("model.role." + role));
        }
    }


    /**
     * The box (V, playthrough-1 #73): GAME.TXT @SCOUTCOLONY's words with
     * the colony's name, its four rows, the bar on «Bürgermeister treffen»,
     * Escape «Nichts», the frontiersman; greyed for a colonist; the
     * answers as FreeCol's actions.
     */
    public void testTheBox() {
        final ClassicText t = texts("testTheBox");
        if (t == null) return;
        assertNull(ClassicForeignColony.request(null, "Montreal", true, true, "x", 0L));
        assertNull(ClassicForeignColony.request(t, null, true, true, "x", 0L));
        final ClassicAdvisorBox.Request r
            = ClassicForeignColony.request(t, "Montreal", true, true, "x", 0L);
        assertEquals(ClassicForeignColony.SECTION, r.id);
        assertEquals("Unsere Späher haben die Außenbezirke von Montreal erreicht,"
            + " Eure Exzellenz. Was sollen sie jetzt tun?", r.plainText());
        assertTrue(r.paragraphs.toString(),
                   r.paragraphs.toString().contains("{Montreal}"));
        assertEquals(List.of("Bürgermeister treffen", "Kolonie infiltrieren",
                             "Kolonie angreifen", "Nichts"), List.of(r.plainRows()));
        assertEquals(0, r.defaultRow);
        assertEquals("Escape: Nichts", 3, r.escapeAnswer());
        assertSame(ClassicAdvisorBox.Portrait.SCOUT, r.portrait);
        assertEquals(230, r.width);
        for (int i = 0; i < 4; i++) assertTrue("scout row " + i, r.enabled(i));
        final ClassicGUISeamTest.KeyPrompter keys = new ClassicGUISeamTest.KeyPrompter();
        assertEquals(ScoutColonyAction.SCOUT_COLONY_NEGOTIATE,
            ClassicForeignColony.action(r, keys.answer(r, "ENTER")));
        assertEquals(ScoutColonyAction.SCOUT_COLONY_SPY,
            ClassicForeignColony.action(r, keys.answer(r, "DOWN", "ENTER")));
        assertEquals(ScoutColonyAction.SCOUT_COLONY_ATTACK,
            ClassicForeignColony.action(r, keys.answer(r, "DOWN", "DOWN", "ENTER")));
        assertNull(ClassicForeignColony.action(r,
            keys.answer(r, "DOWN", "DOWN", "DOWN", "ENTER")));
        assertNull(ClassicForeignColony.action(r, keys.answer(r, "ESC")));
        assertNull(ClassicForeignColony.action(r, -1));

        // A colonist (Roger's pioneer): infiltrate and attack greyed.
        final ClassicAdvisorBox.Request c
            = ClassicForeignColony.request(t, "Montreal", true, false, "x", 0L);
        assertEquals(r.plainText(), c.plainText());
        assertEquals(List.of(r.plainRows()), List.of(c.plainRows()));
        assertTrue(c.enabled(0));
        assertFalse(c.enabled(1));
        assertFalse(c.enabled(2));
        assertTrue(c.enabled(3));
        assertEquals(ScoutColonyAction.SCOUT_COLONY_NEGOTIATE,
            ClassicForeignColony.action(c, keys.answer(c, "ENTER")));
        assertNull("a greyed row cannot be taken", ClassicForeignColony.action(c,
            keys.answer(c, "DOWN", "ENTER")));
        assertNull(ClassicForeignColony.action(c, 1));
        assertNull(ClassicForeignColony.action(c, 2));
        assertNull(ClassicForeignColony.action(c, keys.answer(c, "ESC")));
        // The REF's colony: no meeting, the bar on the first row left.
        final ClassicAdvisorBox.Request ref
            = ClassicForeignColony.request(t, "Montreal", false, true, "x", 0L);
        assertFalse(ref.enabled(0));
        assertEquals(1, ref.defaultRow);
        assertEquals(3, ClassicForeignColony.request(t, "Montreal", false, false,
                                                    "x", 0L).defaultRow);
    }

    /**
     * Without the pack the same box in FreeCol's words, greyed the same.
     */
    public void testTheBoxInFreeColsWords() {
        final World w = new World();
        final Unit pioneer = w.east("hardyPioneer", "pioneer");
        final ClassicAdvisorBox.Request r = ClassicForeignColony.freeColRequest(
            pioneer, w.montreal, true, false, "x", 0L);
        assertEquals(4, r.plainRows().length);
        assertTrue(r.plainText(), r.plainText().contains("Montreal"));
        assertTrue(r.enabled(0));
        assertFalse(r.enabled(1));
        assertFalse(r.enabled(2));
        assertEquals(3, r.escapeAnswer());
    }

    /**
     * The place (V, playthrough-1 #41174): box (42,113,236,62), the
     * frontiersman (149x95) at (86,26) over the box, the bar on row 1 at y
     * 137-143.
     */
    public void testTheBoxPlace() {
        final ClassicText t = texts("testTheBoxPlace");
        if (t == null) return;
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final ClassicFont tiny = pack.font(ClassicFont.TINY);
        final BufferedImage scout = pack.image(ClassicPackFiles.ssKey(
            ClassicAdvisorBox.Portrait.SCOUT.sprite));
        assertNotNull(tiny);
        assertNotNull(scout);
        final ClassicAdvisorBox.Layout l = ClassicAdvisorBox.layout(
            ClassicForeignColony.request(t, "Montreal", true, true, "x", 0L),
            tiny, scout);
        assertEquals(new Rectangle(42, 113, 236, 62), l.box);
        assertEquals(new Point(86, 26), l.portraitAt);
        assertEquals(137, l.barRect(0).y);
    }

    /**
     * The golden check: the box as the game asks it, with the frontiersman,
     * over playthrough-1's frame #41523, 0 px off on the box and the
     * portrait's opaque pixels.
     */
    public void testGoldenAgainstTheClip() throws Exception {
        final File frameFile = frame("playthrough-1/frame_041523.png");
        if (frameFile == null) return;
        final ClassicText t = texts("testGoldenAgainstTheClip");
        if (t == null) return;
        final int[] got = ClassicWarTest.compare(ImageIO.read(frameFile),
            ClassicForeignColony.request(t, "Montreal", true, true, "x", 0L),
            ClassicPackFiles.runtime(), 0);
        System.out.println(getClass().getSimpleName() + ": golden check, @SCOUTCOLONY"
            + " #41523, " + got[0] + " px compared, " + got[1] + " off");
        assertTrue("compared " + got[0], got[0] > 236 * 62);
        assertEquals("pixels off", 0, got[1]);
    }

    /**
     * The golden check of @WHACKINDIANS: the soldier ordered into an
     * Arawak brave, landfall #24736, 0 px off.
     */
    public void testWhackGoldenAgainstTheClip() throws Exception {
        final File frameFile = frame("landfall/frame_024736.png");
        if (frameFile == null) return;
        final ClassicText t = texts("testWhackGoldenAgainstTheClip");
        if (t == null) return;
        final ClassicAdvisorBox.Request r = ClassicWar.whackRequest(t,
            player(getStandardGame(), "arawak"), "x");
        assertEquals("Sollen wir die Araukaner angreifen, Eure Exzellenz?",
                     r.plainText());
        assertEquals(List.of("Ja", "Nein"), List.of(r.plainRows()));
        assertEquals(0, r.defaultRow);
        assertEquals(1, r.escapeAnswer());
        assertSame(ClassicAdvisorBox.Portrait.SOLDIER, r.portrait);
        final int[] got = ClassicWarTest.compare(ImageIO.read(frameFile), r,
            ClassicPackFiles.runtime(), 0);
        System.out.println(getClass().getSimpleName() + ": golden check, @WHACKINDIANS"
            + " #24736, " + got[0] + " px compared, " + got[1] + " off");
        assertTrue("compared " + got[0], got[0] > 236 * 40);
        assertEquals("pixels off", 0, got[1]);
    }

    /** A clip frame, or null (with a note) without the recordings. */
    private File frame(String name) {
        final String clips = System.getProperty(ClassicTerrainGoldenTest.CLIPS_PROPERTY);
        final File f = (clips == null) ? null : new File(clips, name);
        if (f == null || !f.isFile()) {
            System.err.println(getClass().getSimpleName() + ": golden check "
                + name + " skipped, no recordings (-D"
                + ClassicTerrainGoldenTest.CLIPS_PROPERTY + ")");
            return null;
        }
        return f;
    }

    /**
     * The game asks the scout's box ({@link ClassicGUI#getScoutForeignColonyChoice}):
     * the rows give negotiate, spy, attack; «Nichts», Escape and a box left
     * open give nothing (the controller spends nothing then).
     */
    public void testTheGameAsksTheScoutsBox() {
        Topology.setCurrent(Topology.SQUARE);
        final World w = new World();
        final Unit scout = w.east("seasonedScout", "scout");
        assertEquals(Unit.MoveType.ENTER_FOREIGN_COLONY_WITH_SCOUT,
                     scout.getMoveType(Direction.W));
        final ClassicGUI gui = new ClassicGUI(null);
        final ClassicGUISeamTest.KeyPrompter keys = new ClassicGUISeamTest.KeyPrompter();
        gui.prompter = keys;
        final Object[][] cases = {
            { new String[] { "ENTER" }, ScoutColonyAction.SCOUT_COLONY_NEGOTIATE },
            { new String[] { "DOWN", "ENTER" }, ScoutColonyAction.SCOUT_COLONY_SPY },
            { new String[] { "DOWN", "DOWN", "ENTER" }, ScoutColonyAction.SCOUT_COLONY_ATTACK },
            { new String[] { "DOWN", "DOWN", "DOWN", "ENTER" }, null },
            { new String[] { "ESC" }, null },
            { new String[] {}, null },
        };
        final boolean pack = texts("testTheGameAsksTheScoutsBox") != null;
        for (Object[] c : cases) {
            keys.press((String[]) c[0]);
            assertEquals(String.join(",", (String[]) c[0]), c[1],
                gui.getScoutForeignColonyChoice(w.montreal, scout, true));
            final ClassicAdvisorBox.Request r = keys.boxes.get(keys.boxes.size() - 1);
            assertEquals(ClassicForeignColony.SECTION, r.id);
            if (pack) assertTrue(r.plainText(), r.plainText().contains("Montreal"));
            assertSame(ClassicAdvisorBox.Portrait.SCOUT, r.portrait);
        }
    }

    /**
     * Roger's pioneer ({@link ClassicGUI#illegalMoveKey}): the move into
     * Montreal brings the colony's box, not @CANNOTATTACK; «Bürgermeister
     * treffen» brings the "not yet" notice (the scout's way), «Nichts» and
     * Escape nothing; the pioneer keeps his place and his moves every time.
     * At war the same box.
     */
    public void testThePioneersBox() throws Exception {
        Topology.setCurrent(Topology.SQUARE);
        for (Stance stance : new Stance[] { Stance.PEACE, Stance.WAR }) {
            final World w = new World();
            w.dutch.setStance(w.french, stance);
            w.french.setStance(w.dutch, stance);
            final Unit pioneer = w.east("hardyPioneer", "pioneer");
            final Tile at = pioneer.getTile();
            final int moves = pioneer.getMovesLeft();
            final ClassicGUI gui = new ClassicGUI(null);
            final ClassicGUISeamTest.KeyPrompter keys = new ClassicGUISeamTest.KeyPrompter();
            gui.prompter = keys;
            final boolean pack = texts("testThePioneersBox") != null;

            keys.press("ENTER");
            assertTrue(gui.illegalMoveKey(pioneer, Direction.W));
            SwingUtilities.invokeAndWait(() -> { });   // the posted notice
            assertEquals(stance + " box and notice", 2, keys.boxes.size());
            final ClassicAdvisorBox.Request box = keys.boxes.get(0);
            assertEquals(ClassicForeignColony.SECTION, box.id);
            assertFalse(box.enabled(1));
            assertFalse(box.enabled(2));
            if (pack) {
                assertTrue(box.plainText(), box.plainText().contains("Montreal"));
            }
            assertEquals("negotiation", keys.boxes.get(1).id);
            assertEquals(at, pioneer.getTile());
            assertEquals(moves, pioneer.getMovesLeft());

            for (String[] k : new String[][] { { "ESC" },
                    { "DOWN", "DOWN", "DOWN", "ENTER" }, { "DOWN", "ENTER" }, {} }) {
                keys.boxes.clear();
                keys.press(k);
                assertTrue(gui.illegalMoveKey(pioneer, Direction.W));
                SwingUtilities.invokeAndWait(() -> { });
                assertEquals(String.join(",", k), 1, keys.boxes.size());
                assertEquals(ClassicForeignColony.SECTION, keys.boxes.get(0).id);
                assertEquals(at, pioneer.getTile());
                assertEquals(moves, pioneer.getMovesLeft());
            }
        }
    }

    /**
     * The questions before an attack ({@link ClassicGUI#confirmHostileAction}):
     * Montreal at peace @HAVETREATY (only «Friedensvertrag brechen.»
     * attacks), at war none; a village none (after «Dorf angreifen»); a
     * brave at peace @WHACKINDIANS («Ja» attacks, «Nein» and Escape do
     * not), at war none.  And the armed unit's menu at a colony: none, it
     * attacks ({@link ClassicGUI#getArmedUnitSettlementChoice}).
     */
    public void testTheQuestionsBeforeAnAttack() {
        Topology.setCurrent(Topology.SQUARE);
        final World w = new World();
        final ClassicGUI gui = new ClassicGUI(null);
        final ClassicGUISeamTest.KeyPrompter keys = new ClassicGUISeamTest.KeyPrompter();
        gui.prompter = keys;
        final boolean pack = texts("testTheQuestionsBeforeAnAttack") != null;
        final Unit soldier = w.east("veteranSoldier", "soldier");
        final Tile colony = w.montreal.getTile();
        assertEquals(Unit.MoveType.ATTACK_SETTLEMENT, soldier.getMoveType(Direction.W));

        // The colony: no menu, it attacks.
        assertEquals(ArmedUnitSettlementAction.SETTLEMENT_ATTACK,
                     gui.getArmedUnitSettlementChoice(w.montreal));
        assertTrue("no box for the menu", keys.boxes.isEmpty());

        if (pack) {
            keys.press("ENTER");
            assertFalse(gui.confirmHostileAction(soldier, colony));
            assertEquals(ClassicWar.TREATY_SECTION, keys.boxes.get(0).id);
            keys.press("ESC");
            assertFalse(gui.confirmHostileAction(soldier, colony));
            keys.press("DOWN", "ENTER");
            assertTrue(gui.confirmHostileAction(soldier, colony));
            assertEquals(3, keys.boxes.size());
            for (ClassicAdvisorBox.Request r : keys.boxes) {
                assertEquals(ClassicWar.TREATY_SECTION, r.id);
                assertTrue(r.plainText(), r.plainText().contains("Frz."));
            }
            keys.boxes.clear();
        }
        w.dutch.setStance(w.french, Stance.WAR);
        w.french.setStance(w.dutch, Stance.WAR);
        keys.press("ESC");
        assertTrue("at war: no question", gui.confirmHostileAction(soldier, colony));
        assertTrue(keys.boxes.isEmpty());

        // A village at peace: no question after «Dorf angreifen».
        w.dutch.setStance(w.arawak, Stance.PEACE);
        w.arawak.setStance(w.dutch, Stance.PEACE);
        final Tile vt = w.map.getTile(10, 4);
        final IndianSettlement camp = new FreeColTestCase.IndianSettlementBuilder(w.game)
            .player(w.arawak).settlementTile(vt).build();
        assertNotNull(camp);
        final Unit dragoon = new ServerUnit(w.game, vt.getNeighbourOrNull(Direction.E),
            w.dutch, spec().getUnitType("model.unit.veteranSoldier"),
            spec().getRole("model.role.dragoon"));
        assertTrue(gui.confirmHostileAction(dragoon, vt));
        assertTrue(keys.boxes.isEmpty());

        // A brave in the open at peace: @WHACKINDIANS.
        final Tile bt = w.map.getTile(14, 10);
        new ServerUnit(w.game, bt, w.arawak, spec().getUnitType("model.unit.brave"));
        final Unit attacker = new ServerUnit(w.game, bt.getNeighbourOrNull(Direction.E),
            w.dutch, spec().getUnitType("model.unit.veteranSoldier"),
            spec().getRole("model.role.soldier"));
        if (pack) {
            keys.press("ENTER");
            assertTrue("Ja", gui.confirmHostileAction(attacker, bt));
            keys.press("DOWN", "ENTER");
            assertFalse("Nein", gui.confirmHostileAction(attacker, bt));
            keys.press("ESC");
            assertFalse("Escape", gui.confirmHostileAction(attacker, bt));
            assertEquals(3, keys.boxes.size());
            for (ClassicAdvisorBox.Request r : keys.boxes) {
                assertEquals(ClassicWar.WHACK_SECTION, r.id);
                assertTrue(r.plainText(), r.plainText().contains("Araukaner"));
                assertSame(ClassicAdvisorBox.Portrait.SOLDIER, r.portrait);
            }
            keys.boxes.clear();
        }
        w.dutch.setStance(w.arawak, Stance.WAR);
        w.arawak.setStance(w.dutch, Stance.WAR);
        keys.press("ESC");
        assertTrue("natives at war: no question", gui.confirmHostileAction(attacker, bt));
        assertTrue(keys.boxes.isEmpty());
    }
}
