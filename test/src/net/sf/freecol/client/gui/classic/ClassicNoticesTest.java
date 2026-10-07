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
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.ModelMessage;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * Tests of the notices (master plan N1, {@link ClassicNotices}): FreeCol's
 * information messages, silent in the base GUI, come as one box each, in
 * GAME.TXT's words where the original has the notice, with its portrait;
 * the illegal moves and a key out of turn stay silent; a value the
 * original does not have gives FreeCol's words.  And the golden check of
 * the boxes the game builds from FreeCol's messages: @BURIAL1 (landfall
 * #20177, from the mounds' "nothing" rumour), @CHIEFGUIDES (clip008
 * #46396), @LEARNALREADY (#37156) and @CHIEFGIFT (#51431), 0 px off on the
 * box and the portrait's pixels (needs the pack and {@code -Dclassic.clips},
 * else skipped with a note).
 */
public class ClassicNoticesTest extends FreeColTestCase {

    /** A classic GUI without a client, our player the test's. */
    private static final class NoticeGUI extends ClassicGUI {

        final Game game;
        final Player me;

        NoticeGUI(Game game, Player me) {
            super(null);
            this.game = game;
            this.me = me;
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
        java.awt.Image iconOf(net.sf.freecol.common.model.FreeColObject display) {
            return null;   // no image resources here
        }
    }

    /** Keeps the boxes asked; answers 0. */
    private static final class Keeper implements ClassicGUI.Prompter {

        final List<ClassicAdvisorBox.Request> boxes = new ArrayList<>();

        @Override
        public int ask(ClassicAdvisorBox.Request r) {
            this.boxes.add(r);
            return 0;
        }
    }

    /** The pack's texts, or null (then a test is skipped with a note). */
    private static ClassicText texts(String test) {
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        if (t == null) {
            System.err.println("ClassicNoticesTest: " + test
                + " skipped, no pack texts (ant classic-assets)");
        }
        return t;
    }

    /** An Arawak village on a test map, and the game. */
    private IndianSettlement arawakVillage(Game game) {
        game.changeMap(getTestMap());
        return new IndianSettlementBuilder(game)
            .player(game.getPlayerByNationId("model.nation.arawak")).build();
    }

    /** The silent ones, and only those. */
    public void testSilent() throws Exception {
        for (String id : new String[] { "move.noAccessWater", "move.noAccessBeached",
                "move.noAccessSettlement", "move.noAttackWater", "move.noTile",
                "info.notYourTurn" }) {
            assertTrue(id, ClassicNotices.silent(id));
        }
        for (String id : new String[] { "info.notEnoughGold", "buildColony.badUnit",
                "scoutSettlement.speakBeads", "move.other", null }) {
            assertFalse(String.valueOf(id), ClassicNotices.silent(id));
        }
        // The GUI asks no box for them, one box for any other.
        final Game game = getStandardGame();
        final NoticeGUI gui = new NoticeGUI(game, game.getPlayerByNationId("model.nation.dutch"));
        final Keeper keep = new Keeper();
        gui.prompter = keep;
        assertNull(gui.showInformationPanel(null, StringTemplate.template("move.noAccessWater")
            .addName("%unit%", "x")));
        assertNull(gui.showInformationPanel(null, StringTemplate.key("info.notYourTurn")));
        assertTrue(keep.boxes.isEmpty());
        assertNull(gui.showInformationPanel(null, StringTemplate.key("info.notEnoughGold")));
        assertNull(gui.showInformationPanel(null, (StringTemplate) null));
        SwingUtilities.invokeAndWait(() -> { });       // the posted box
        assertEquals(1, keep.boxes.size());
        final ClassicAdvisorBox.Request r = keep.boxes.get(0);
        assertTrue(r.isNotice());
        assertEquals("notice info.notEnoughGold", r.id);
        assertEquals(Messages.message("info.notEnoughGold"), r.plainText());
    }

    /** The rules: the mapped ids, the ordinary "nothing" rumours, the others none. */
    public void testRules() {
        assertEquals("CHIEFGIFT", ClassicNotices.rule("scoutSettlement.speakBeads").section);
        assertEquals(ClassicNotices.Who.CHIEF,
                     ClassicNotices.rule("info.noMoreSkill").who);
        assertEquals("BURIAL1", ClassicNotices.rule(
            "model.lostCityRumour.nothing.mounds.description").section);
        assertEquals(ClassicNotices.Who.SCOUT, ClassicNotices.rule(
            "model.lostCityRumour.nothing.mounds.description").who);
        assertEquals("LOSTCITY6", ClassicNotices.rule(
            "model.lostCityRumour.nothing.3.description").section);
        assertNull(ClassicNotices.rule("model.lostCityRumour.nothing.mayans.description"));
        assertNull(ClassicNotices.rule("model.lostCityRumour.nothing..description"));
        assertNull(ClassicNotices.rule("info.notEnoughGold"));
        assertNull(ClassicNotices.rule(null));
        // Every rule's section is in GAME.TXT (with the pack).
        final ClassicText t = texts("testRules (sections)");
        if (t == null) return;
        for (ClassicNotices.Rule r : ClassicNotices.RULES.values()) {
            assertNotNull(r.section, t.message(r.section));
            assertNotNull(r.section, t.message(r.section).width);
            assertTrue(r.section, t.message(r.section).options.isEmpty());
        }
        assertNotNull(t.message("LOSTCITY6"));
    }

    /** The tribe: the village's owner, else the message's %nation%. */
    public void testTribe() {
        final Game game = getStandardGame();
        final IndianSettlement is = arawakVillage(game);
        assertEquals(2, ClassicNotices.tribe(StringTemplate.key("x"), is));
        final Player sioux = game.getPlayerByNationId("model.nation.sioux");
        assertEquals(6, ClassicNotices.tribe(StringTemplate.template("x")
            .addStringTemplate("%nation%", sioux.getNationLabel()), null));
        // Our own unit is no tribe: the %nation% decides.
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        assertEquals(-1, ClassicNotices.tribe(StringTemplate.key("x"), dutch));
        assertEquals(-1, ClassicNotices.tribe(null, null));
        assertEquals(-1, ClassicNotices.tribe(StringTemplate.template("x")
            .addStringTemplate("%nation%", StringTemplate.name("Somewhere")), null));
    }

    /**
     * The boxes with the pack: the chief's gift in the original's words
     * with the tribe, "Holl." and the coin; the chief stands at it; a
     * notice without a known tribe, or of a nation the original does not
     * have, keeps FreeCol's words; @ONLYCOL without a portrait.
     */
    public void testTheOriginalsWords() {
        final ClassicText t = texts("testTheOriginalsWords");
        if (t == null) return;
        final Game game = getStandardGame();
        final IndianSettlement is = arawakVillage(game);
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final StringTemplate beads = StringTemplate.template("scoutSettlement.speakBeads")
            .add("%amount%", "640");
        final ClassicAdvisorBox.Request r = ClassicNotices.request(t, "notice",
            beads, Messages.message(beads), is, dutch, "t", null);
        assertEquals("CHIEFGIFT", r.id);
        assertEquals("IND2A0.SS.000", r.portrait.sprite);
        assertTrue(r.isNotice());
        final String text = r.plainText();
        assertTrue(text, text.contains("Araukaner"));
        assertTrue(text, text.contains("Holl."));
        assertTrue(text, text.contains("640$"));
        // Without the village: FreeCol's words, no portrait.
        final ClassicAdvisorBox.Request f = ClassicNotices.request(t, "notice x",
            beads, Messages.message(beads), null, dutch, "t", null);
        assertEquals("notice x", f.id);
        assertEquals(ClassicAdvisorBox.Portrait.NONE, f.portrait);
        assertEquals(Messages.message(beads), f.plainText());
        // A nation the original does not have: FreeCol's words.
        final Player danes = game.getPlayerByNationId("model.nation.danish");
        if (danes != null) {
            assertEquals("notice y", ClassicNotices.request(t, "notice y", beads,
                "w", is, danes, "t", null).id);
        }
        // Without the texts: FreeCol's words.
        assertEquals("notice z", ClassicNotices.request(null, "notice z", beads,
            "w", is, dutch, "t", null).id);
        // @ONLYCOL.
        final ClassicAdvisorBox.Request c = ClassicNotices.request(t, "notice",
            StringTemplate.template("buildColony.badUnit").addName("%unit%", "x"),
            "w", null, dutch, "t", null);
        assertEquals("ONLYCOL", c.id);
        assertEquals(120, c.width);
        assertEquals(ClassicAdvisorBox.Portrait.NONE, c.portrait);
        // A rumour's result with the frontiersman and the money.
        final ModelMessage ruins = new ModelMessage(
            ModelMessage.MessageType.LOST_CITY_RUMOUR,
            "model.lostCityRumour.ruins.description", dutch).addAmount("%money%", 300);
        final ClassicAdvisorBox.Request ru = ClassicNotices.request(t, "message",
            ruins, Messages.message(ruins), null, dutch, "t", null);
        assertEquals("LOSTCITY3", ru.id);
        assertEquals(ClassicAdvisorBox.Portrait.SCOUT, ru.portrait);
        assertTrue(ru.plainText(), ru.plainText().contains("300$"));
    }

    /**
     * The GUI's notices: from the event thread the box is asked at once,
     * from another thread it is posted (no server message waits); the
     * model messages go the same way, in the original's words where it
     * has them.
     */
    public void testTheGuiAsksThem() throws Exception {
        final Game game = getStandardGame();
        final IndianSettlement is = arawakVillage(game);
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final NoticeGUI gui = new NoticeGUI(game, dutch);
        final Keeper keep = new Keeper();
        gui.prompter = keep;
        gui.showInformationPanel(is, StringTemplate.template("scoutSettlement.speakTales"));
        assertEquals(0, keep.boxes.size());                // posted
        SwingUtilities.invokeAndWait(() -> { });
        assertEquals(1, keep.boxes.size());
        SwingUtilities.invokeAndWait(() ->
            gui.showInformationPanel(is, StringTemplate.key("info.noMoreSkill")));
        assertEquals(2, keep.boxes.size());
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        if (t != null) {
            assertEquals("CHIEFAREA", keep.boxes.get(0).id);
            assertEquals("LEARNALREADY", keep.boxes.get(1).id);
        }
        gui.showModelMessages(List.of(new ModelMessage(
            ModelMessage.MessageType.LOST_CITY_RUMOUR,
            "model.lostCityRumour.nothing.mounds.description", dutch)));
        assertEquals(3, keep.boxes.size());
        if (t != null) assertEquals("BURIAL1", keep.boxes.get(2).id);
    }


    // The golden check

    /**
     * A notice the game builds, drawn over a frame: the box and the
     * portrait's opaque pixels compared.
     *
     * @return {compared, off, off on the original's arrow}.
     */
    private static int[] compare(BufferedImage frame, int ox, int oy,
                                 ClassicAdvisorBox.Request r, ClassicPackFiles pack,
                                 Rectangle box) {
        final ClassicFont tiny = pack.font(ClassicFont.TINY);
        final BufferedImage pic = (r.portrait.sprite == null) ? null
            : pack.image(ClassicPackFiles.ssKey(r.portrait.sprite));
        final ClassicAdvisorBox.Layout l = ClassicAdvisorBox.layout(r, tiny, pic);
        assertEquals(r.id, box, l.box);
        final BufferedImage canvas = new BufferedImage(320, 200,
            BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = canvas.createGraphics();
        g.drawImage(frame, ox, oy, null);
        ClassicAdvisorBox.paint(g, l, -1, pack.image(ClassicMenuBar.WOOD_KEY), tiny);
        g.dispose();
        final Raster idx = (frame.getColorModel() instanceof IndexColorModel)
            ? frame.getRaster() : null;
        final int[] out = new int[3];
        for (int y = 0; y < frame.getHeight(); y++) {
            for (int x = 0; x < frame.getWidth(); x++) {
                final int sx = ox + x, sy = oy + y;
                boolean on = l.box.contains(sx, sy);
                if (!on && pic != null) {
                    final int px = sx - l.portraitAt.x, py = sy - l.portraitAt.y;
                    on = px >= 0 && py >= 0 && px < pic.getWidth() && py < pic.getHeight()
                        && (pic.getRGB(px, py) >>> 24) != 0;
                }
                if (!on) continue;
                out[0]++;
                if ((frame.getRGB(x, y) & 0xFFFFFF) == (canvas.getRGB(sx, sy) & 0xFFFFFF)) {
                    continue;
                }
                final int i = (idx == null) ? -1 : idx.getSample(x, y, 0);
                out[(i == 0 || i == 7 || i == 15) ? 2 : 1]++;
            }
        }
        return out;
    }

    /**
     * The golden check (class comment): each notice as the GUI builds it
     * from FreeCol's message, over its frame: 0 px off on the box and the
     * portrait, apart from the original's mouse arrow; each box where the
     * clip has it.
     */
    public void testGoldenAgainstTheClips() throws Exception {
        final String clips = System.getProperty(ClassicTerrainGoldenTest.CLIPS_PROPERTY);
        final File dir = (clips == null) ? null : new File(clips);
        if (dir == null || !new File(dir, "clip008").isDirectory()) {
            System.err.println(getClass().getSimpleName()
                + ": golden check skipped, no recordings (-D"
                + ClassicTerrainGoldenTest.CLIPS_PROPERTY + ")");
            return;
        }
        final ClassicText t = texts("testGoldenAgainstTheClips");
        if (t == null) return;
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final Game game = getStandardGame();
        final IndianSettlement is = arawakVillage(game);
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final Object[][] cases = {
            { "landfall/analysis/img/21_BURIAL1_frontiersman_1x.png", 42, 45,
              new ModelMessage(ModelMessage.MessageType.LOST_CITY_RUMOUR,
                  "model.lostCityRumour.nothing.mounds.description", dutch), null,
              new Rectangle(42, 132, 236, 24) },
            { "clip008/frame_046396.png", 0, 0,
              StringTemplate.template("scoutSettlement.expertScout")
                  .addNamed("%unit%", spec().getUnitType("model.unit.seasonedScout")),
              is, new Rectangle(7, 79, 236, 42) },
            { "clip008/frame_037156.png", 0, 0, StringTemplate.key("info.noMoreSkill"),
              is, null },
            { "clip008/frame_051431.png", 0, 0,
              StringTemplate.template("scoutSettlement.speakBeads").add("%amount%", "640"),
              is, new Rectangle(7, 82, 236, 36) },
        };
        final StringBuilder fails = new StringBuilder();
        int compared = 0, arrow = 0;
        for (Object[] c : cases) {
            final StringTemplate m = (StringTemplate) c[3];
            final ClassicAdvisorBox.Request r = ClassicNotices.request(t, "notice",
                m, Messages.message(m), (IndianSettlement) c[4], dutch, "t", null);
            assertFalse((String) c[0], "notice".equals(r.id));
            final BufferedImage frame = ImageIO.read(new File(dir, (String) c[0]));
            assertNotNull((String) c[0], frame);
            Rectangle box = (Rectangle) c[5];
            if (box == null) {
                box = ClassicAdvisorBox.layout(r, pack.font(ClassicFont.TINY),
                    pack.image(ClassicPackFiles.ssKey(r.portrait.sprite))).box;
            }
            final int[] got = compare(frame, (Integer) c[1], (Integer) c[2], r, pack, box);
            compared += got[0];
            arrow += got[2];
            if (got[1] > 0) fails.append(' ').append(r.id).append('=').append(got[1]);
        }
        System.out.println(getClass().getSimpleName() + ": golden check, "
            + cases.length + " notices, " + compared + " px compared, off:"
            + ((fails.length() == 0) ? " none" : fails.toString()) + ", "
            + arrow + " arrow px excused");
        assertEquals("pixels off:" + fails, 0, fails.length());
        assertTrue("arrow pixels " + arrow, arrow <= 100);
    }
}
