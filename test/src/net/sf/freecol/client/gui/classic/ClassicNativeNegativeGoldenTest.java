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

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.CombatModel.CombatEffectType;
import net.sf.freecol.common.model.Direction;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.ModelMessage;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Tension;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.networking.ChangeSet;
import net.sf.freecol.common.option.GameOptions;
import net.sf.freecol.server.ServerTestHelper;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * Part Z, Z-ITEMS Z2.5: the negative goldens of Roger's test of
 * 2026-10-16 15:21 ({@code our-recordings/2026-10-16_1521}, package X):
 * the eight native boxes in FreeCol's words or without a counterpart in
 * the original (the M3 verifier's W10 and W11) must not come back.  For
 * each frame the test first checks that it shows the old box (its black
 * outer ring at the place the recording logged, its light inner border),
 * then what the game does now in that situation:
 * <ul>
 *   <li>#6 the uncontacted Iroquois' land (frame_030679, (84,77,236,46)):
 *       the original's @INDIANLAND;</li>
 *   <li>#7 the Apache mood notice (frame_054799, (42,79,236,42)):
 *       @INDIANCOMMENT with the chief;</li>
 *   <li>#11 the dragoon's village menu (frame_063964, (42,73,236,54)):
 *       @VILLAGEHAPPY with «Dorf angreifen / Zoll fordern / Handlung
 *       abbrechen»;</li>
 *   <li>#12 the tribute question (frame_064125, (42,74,236,52)): no box;</li>
 *   <li>#14 and #17 «Sieg bei Grasland …» (frame_064810, frame_069337,
 *       (42,85,236,30)): no box after our attack on natives;</li>
 *   <li>#15 «Irokesen hat uns den Krieg erklärt!» (frame_065184, W11: the
 *       box of the event at #65183 is in this frame): no war and no
 *       notice under the levi rule, no notice in the Classic UI without
 *       it;</li>
 *   <li>#16 the village menu at war (frame_069203, (42,70,236,60)):
 *       @VILLAGEWAR.</li>
 * </ul>
 * A new box is drawn over the frame: at least a fifth of the old box's
 * pixels must differ, and FreeCol's words must be gone.  Needs the pack
 * and the recordings ({@code -Dclassic.clips}, the recording beside the
 * clips' folder), else a note.
 */
public class ClassicNativeNegativeGoldenTest extends FreeColTestCase {

    private static final int IROQUOIS = 3, APACHE = 5;

    @Override
    protected void tearDown() throws Exception {
        ServerTestHelper.stopServerGame();
        super.tearDown();
    }

    /** Roger's recording of 2026-10-16 15:21, or null (with a note). */
    private File recording(String test) {
        final String clips = System.getProperty(ClassicTerrainGoldenTest.CLIPS_PROPERTY);
        final File dir = (clips == null) ? null
            : new File(new File(clips).getParentFile(), "our-recordings/2026-10-16_1521");
        if (dir == null || !dir.isDirectory()) {
            System.err.println(getClass().getSimpleName() + "." + test
                + ": no recordings (-D" + ClassicTerrainGoldenTest.CLIPS_PROPERTY
                + "), skipped");
            return null;
        }
        return dir;
    }

    /** The pack's texts, or null (with a note). */
    private ClassicText pack(String test) {
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        if (t == null || ClassicPackFiles.runtime().font(ClassicFont.TINY) == null) {
            System.err.println(getClass().getSimpleName() + "." + test
                + ": no pack (ant classic-assets), skipped");
            return null;
        }
        return t;
    }

    /**
     * A frame of the recording with the old box checked: the outer ring
     * black (palette index 0, the mouse arrow's 7 and 15 excused, at most
     * 12 pixels) and the inner top border light (index 134).
     *
     * @param dir The recording.
     * @param frame The frame number.
     * @param old The old box's place, as the recording logged it.
     * @return The frame.
     */
    private static BufferedImage oldBox(File dir, int frame, Rectangle old) throws Exception {
        final BufferedImage f = ImageIO.read(new File(dir,
            String.format("frame_%06d.png", frame)));
        assertNotNull("frame " + frame, f);
        final Raster r = f.getRaster();
        int arrow = 0;
        for (int x = old.x; x < old.x + old.width; x++) {
            for (int y : new int[] { old.y, old.y + old.height - 1 }) {
                final int i = r.getSample(x, y, 0);
                if (i == 7 || i == 15) arrow++;
                else assertEquals(frame + " ring at " + x + "," + y, 0, i);
            }
            if (x > old.x && x < old.x + old.width - 1) {
                assertEquals(frame + " inner border at " + x, 134, r.getSample(x, old.y + 1, 0));
            }
        }
        for (int y = old.y; y < old.y + old.height; y++) {
            for (int x : new int[] { old.x, old.x + old.width - 1 }) {
                final int i = r.getSample(x, y, 0);
                if (i == 7 || i == 15) arrow++;
                else assertEquals(frame + " ring at " + x + "," + y, 0, i);
            }
        }
        assertTrue(frame + " arrow " + arrow, arrow <= 12);
        return f;
    }

    /**
     * How many pixels of the old box differ once the new box is drawn over
     * the frame (with its portrait).
     *
     * @param frame The frame.
     * @param r The new box.
     * @param old The old box's place.
     * @return The count.
     */
    private static int differing(BufferedImage frame, ClassicAdvisorBox.Request r,
                                 Rectangle old) {
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final ClassicFont tiny = pack.font(ClassicFont.TINY);
        final BufferedImage pic = (r.portrait.sprite == null) ? null
            : pack.image(ClassicPackFiles.ssKey(r.portrait.sprite));
        final ClassicAdvisorBox.Layout l = ClassicAdvisorBox.layout(r, tiny, pic);
        final BufferedImage canvas = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = canvas.createGraphics();
        g.drawImage(frame, 0, 0, null);
        ClassicAdvisorBox.paint(g, l, r.defaultRow, pack.image(ClassicMenuBar.WOOD_KEY), tiny);
        g.dispose();
        int n = 0;
        for (int y = old.y; y < old.y + old.height; y++) {
            for (int x = old.x; x < old.x + old.width; x++) {
                if ((frame.getRGB(x, y) & 0xFFFFFF) != (canvas.getRGB(x, y) & 0xFFFFFF)) n++;
            }
        }
        return n;
    }

    /** A world: an Iroquois village and an Apache camp, our dragoon next to the first. */
    private static final class World {
        final Game game;
        final Player dutch, iroquois, apache;
        final IndianSettlement onondaga, apacheCamp;
        final Unit dragoon;

        World() {
            this.game = getStandardGame();
            this.game.changeMap(getTestMap());
            this.dutch = this.game.getPlayerByNationId("model.nation.dutch");
            this.iroquois = this.game.getPlayerByNationId("model.nation.iroquois");
            this.apache = this.game.getPlayerByNationId("model.nation.apache");
            this.onondaga = new FreeColTestCase.IndianSettlementBuilder(this.game)
                .player(this.iroquois).settlementTile(this.game.getMap().getTile(5, 8))
                .capital(false).build();
            this.apacheCamp = new FreeColTestCase.IndianSettlementBuilder(this.game)
                .player(this.apache).settlementTile(this.game.getMap().getTile(10, 8))
                .capital(false).build();
            this.dragoon = new ServerUnit(this.game,
                this.onondaga.getTile().getNeighbourOrNull(Direction.NW), this.dutch,
                spec().getUnitType("model.unit.freeColonist"),
                spec().getRole("model.role.dragoon"));
        }
    }

    /**
     * The four boxes that come back in the original's words (#6, #7, #11,
     * #16): each frame shows the old box; the new box is GAME.TXT's,
     * FreeCol's words are gone, and at least a fifth of the old box's
     * pixels differ once it is drawn over the frame.
     */
    public void testTheBoxesInFreeColsWordsDoNotComeBack() throws Exception {
        final File dir = recording("testTheBoxesInFreeColsWordsDoNotComeBack");
        final ClassicText t = pack("testTheBoxesInFreeColsWordsDoNotComeBack");
        if (dir == null || t == null) return;
        final World w = new World();
        final int[] armed = ClassicVillageBox.rows(ClassicVillageBox.Kind.ARMED, false, false);
        final ModelMessage mood = new ModelMessage(ModelMessage.MessageType.FOREIGN_DIPLOMACY,
            "model.player.alarmIncrease.tension.content", w.apacheCamp)
            .addStringTemplate("%nation%", w.apache.getNationLabel())
            .addStringTemplate("%enemy%", w.dutch.getNationLabel())
            .addName("%settlement%", "Igihua'-a");
        final Object[][] cases = {
            // event, frame, old box, new box, its section, FreeCol's words,
            // the original's
            { "#6", 30679, new Rectangle(84, 77, 236, 46),
              ClassicNativeLand.claimRequest(t, w.iroquois, false, 146, true, "Base"),
              ClassicNativeLand.SECTION, "unkontaktierte", "Irokesen-Land" },
            { "#7", 54799, new Rectangle(42, 79, 236, 42),
              ClassicNotices.request(t, "message " + mood.getId(), mood,
                  Messages.message(mood), w.apacheCamp, w.dutch, "x", null),
              "INDIANCOMMENT", "begrüßt seine Freunde", "Wohlstand ihrer Nachbarn" },
            { "#11", 63964, new Rectangle(42, 73, 236, 54),
              ClassicVillageBox.builder(t, ClassicVillageBox.moodTag(Tension.Level.HAPPY,
                  IROQUOIS, false), IROQUOIS, false, armed, null).build(),
              ClassicVillageBox.HAPPY, "Einen Tribut fordern", "ein Dorf der Irokesen" },
            { "#16", 69203, new Rectangle(42, 70, 236, 60),
              ClassicVillageBox.builder(t, ClassicVillageBox.moodTag(Tension.Level.HATEFUL,
                  IROQUOIS, true), IROQUOIS, false, armed, null).build(),
              ClassicVillageBox.WAR, "klirren mit den Waffen", "Kriegstrommeln" } };
        final StringBuilder out = new StringBuilder();
        for (Object[] c : cases) {
            final Rectangle old = (Rectangle) c[2];
            final BufferedImage frame = oldBox(dir, (Integer) c[1], old);
            final ClassicAdvisorBox.Request r = (ClassicAdvisorBox.Request) c[3];
            assertEquals(c[0] + "", c[4], r.id);
            final String words = r.plainText() + " " + String.join(" ", r.plainRows());
            assertFalse(c[0] + " " + words, words.contains((String) c[5]));
            assertTrue(c[0] + " " + words, words.contains((String) c[6]));
            final int n = differing(frame, r, old);
            out.append(' ').append(c[0]).append(' ').append(n).append('/')
                .append(old.width * old.height);
            assertTrue(c[0] + " differs " + n, n * 5 > old.width * old.height);
        }
        // #11 and #16: FreeCol's rows «Einen Tribut fordern / Angriff /
        // Abbrechen», the bar on the tribute; the original's «Dorf angreifen /
        // Zoll fordern / Handlung abbrechen», the bar on «Dorf angreifen».
        for (int i : new int[] { 2, 3 }) {
            final ClassicAdvisorBox.Request r = (ClassicAdvisorBox.Request) cases[i][3];
            assertEquals(java.util.List.of("Dorf angreifen", "Zoll fordern",
                "Handlung abbrechen"), java.util.Arrays.asList(r.plainRows()));
            assertEquals(0, r.defaultRow);
        }
        System.out.println(getClass().getSimpleName() + ": boxes in FreeCol's words,"
            + " px of the old box differing:" + out);
    }

    /**
     * The boxes the original does not have (#12, #14, #15, #17): each
     * frame shows the old box; now FreeCol's tribute question is answered
     * without a box, the win notices of our attack on natives are dropped,
     * and the attack declares no war (the levi rule) and tells us of none
     * (the Classic UI without the rule).
     */
    public void testTheBoxesWithoutACounterpartDoNotComeBack() throws Exception {
        final File dir = recording("testTheBoxesWithoutACounterpartDoNotComeBack");
        if (dir == null) return;
        oldBox(dir, 64125, new Rectangle(42, 74, 236, 52));
        oldBox(dir, 64810, new Rectangle(42, 85, 236, 30));
        oldBox(dir, 65184, new Rectangle(42, 85, 236, 30));
        oldBox(dir, 69337, new Rectangle(42, 85, 236, 30));
        // #12: no box.
        final ClassicGUI gui = new ClassicGUI(null);
        final ClassicGUISeamTest.KeyPrompter keys = new ClassicGUISeamTest.KeyPrompter();
        gui.prompter = keys;
        final StringTemplate q = StringTemplate.template("confirmTribute.normal")
            .addName("%settlement%", "Onondaga").addName("%nation%", "Irokesen");
        assertTrue(gui.modalConfirmDialog(null, q, (ImageIcon) null, "confirmTribute.yes",
                                          "confirmTribute.no", true));
        assertTrue("#12 no box", keys.boxes.isEmpty());
        // #14, #17: dropped after our attack.
        final World w = new World();
        final ModelMessage win = new ModelMessage(ModelMessage.MessageType.COMBAT_RESULT,
            "combat.unitSlaughtered.enemy.brave", w.dragoon)
            .addDefaultId("combat.unitSlaughtered.enemy");
        assertTrue("#14 #17", ClassicNotices.isOurWinOverNatives(win,
                                                                Set.of(w.dragoon.getId())));
        assertTrue(ClassicNotices.attacksNatives(w.dragoon, w.onondaga.getTile()));
        // #15: the attack and its war notice, on the server.
        final boolean saved = net.sf.freecol.FreeCol.getClassic();
        try {
            net.sf.freecol.FreeCol.setClassic(true);
            for (boolean rule : new boolean[] { true, false }) {
                ServerTestHelper.stopServerGame();
                final Game game = ServerTestHelper.startServerGame(getTestMap());
                game.getSpecification().setBoolean(GameOptions.ORIGINAL_NATIVE_WAR, rule);
                try {
                    final ServerPlayer dutch = getServerPlayer(game, "model.nation.dutch");
                    final ServerPlayer iroquois = getServerPlayer(game, "model.nation.iroquois");
                    Player.makeContact(dutch, iroquois);
                    final ServerUnit dragoon = new ServerUnit(game, game.getMap().getTile(5, 5),
                        dutch, spec().getUnitType("model.unit.freeColonist"),
                        spec().getRole("model.role.dragoon"));
                    final ServerUnit brave = new ServerUnit(game, game.getMap().getTile(6, 5),
                        iroquois, spec().getUnitType("model.unit.brave"));
                    final ChangeSet cs = new ChangeSet();
                    dutch.csCombat(dragoon, brave, new ArrayList<>(List.of(
                        CombatEffectType.WIN, CombatEffectType.SLAUGHTER_UNIT)),
                        new java.util.Random(1), cs);
                    assertEquals("#15 rule=" + rule, (rule) ? Stance.PEACE : Stance.WAR,
                                 dutch.getStance(iroquois));
                    assertFalse("#15 rule=" + rule + " no notice to us",
                        String.valueOf(cs.build(dutch)).contains(Stance.WAR.getStanceChangeKey()));
                } finally {
                    game.getSpecification().setBoolean(GameOptions.ORIGINAL_NATIVE_WAR, false);
                }
            }
        } finally {
            net.sf.freecol.FreeCol.setClassic(saved);
        }
    }
}
