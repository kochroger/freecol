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

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.ModelMessage;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * Part Z, Z-ITEMS Z2.2-Z2.4: the natives' notices in the original's words
 * ({@link ClassicNotices}, M3 spec A2, A3, A5 with the verifier's
 * corrections): the chief's answers to «Zoll fordern» (@EXTORTSTUFF,
 * @EXTORTPOOR, @EXTORTLAUGH, @EXTORTNO) and FreeCol's tribute question
 * never asked, and the golden check against the clips' frames (needs the
 * pack and {@code -Dclassic.clips}, else a note).
 */
public class ClassicNativeNoticesTest extends FreeColTestCase {

    private static final int ARAWAK = 2, SIOUX = 6;

    /** The pack's texts, or null (with a note: the pack-bound checks are skipped). */
    private ClassicText pack(String test) {
        final ClassicText t = ClassicText.load(ClassicPackFiles.runtime());
        if (t == null || t.message("EXTORTSTUFF") == null) {
            System.err.println(getClass().getSimpleName() + "." + test
                + ": no pack texts (ant classic-assets), skipped");
            return null;
        }
        return t;
    }

    /** A world: the easiest level (our title «Entdecker»), villages of two tribes. */
    private static final class World {
        final Game game;
        final Player dutch, sioux, arawak;
        final IndianSettlement siouxCamp, arawakVillage;
        final Unit soldier;

        World() {
            this.game = getStandardGame();
            this.game.getSpecification().applyDifficultyLevel("model.difficulty.veryEasy");
            this.game.changeMap(getTestMap());
            this.dutch = this.game.getPlayerByNationId("model.nation.dutch");
            this.sioux = this.game.getPlayerByNationId("model.nation.sioux");
            this.arawak = this.game.getPlayerByNationId("model.nation.arawak");
            this.siouxCamp = new FreeColTestCase.IndianSettlementBuilder(this.game)
                .player(this.sioux).settlementTile(this.game.getMap().getTile(5, 8))
                .capital(false).build();
            this.arawakVillage = new FreeColTestCase.IndianSettlementBuilder(this.game)
                .player(this.arawak).settlementTile(this.game.getMap().getTile(10, 8))
                .capital(false).build();
            this.soldier = new ServerUnit(this.game,
                this.siouxCamp.getTile().getNeighbourOrNull(
                    net.sf.freecol.common.model.Direction.N), this.dutch,
                spec().getUnitType("model.unit.freeColonist"),
                spec().getRole("model.role.soldier"));
        }

        /** A tribute answer as the server sends it (InGameController.tributeAnswer). */
        ModelMessage answer(String key, IndianSettlement is) {
            return new ModelMessage(ModelMessage.MessageType.FOREIGN_DIPLOMACY, key,
                                    this.soldier, is)
                .addStringTemplate("%nation%", is.getOwner().getNationLabel());
        }

        /** The goods answer: 28 furs to Silver City (V 048 #44727). */
        ModelMessage goods(IndianSettlement is) {
            return answer(ClassicNotices.TRIBUTE_GOODS, is).addAmount("%amount%", 28)
                .addNamed("%goods%", spec().getGoodsType("model.goods.furs"))
                .addName("%colony%", "Silver City");
        }
    }

    /** A notice as the game builds it ({@link ClassicNotices#request}). */
    private static ClassicAdvisorBox.Request notice(ClassicText t, ModelMessage m,
                                                    IndianSettlement is, Player me) {
        return ClassicNotices.request(t, "message " + m.getId(), m, Messages.message(m),
                                      is, me, "x", null);
    }

    /**
     * The chief's answers to «Zoll fordern» (Z2.2): the levi tribute's
     * four messages in GAME.TXT's words with the tribe's chief, 15-18
     * frames after the village box (W4): @EXTORTSTUFF «Große Entdecker, …
     * {Sioux} liefern mit Freuden {28 Felle} nach {Silver City} …» (V 048
     * #44727), @EXTORTPOOR «Mächtiger Entdecker …» (V 048 #48446),
     * @EXTORTLAUGH «… {Araukaner}-Krieger …» (V 045 #30832), @EXTORTNO
     * with our title and our leader's name (I).  FreeCol's own
     * tributeAgree / tributeDisagree keep FreeCol's words.
     */
    public void testTheTributeAnswersInTheOriginalsWords() {
        final ClassicText t = pack("testTheTributeAnswersInTheOriginalsWords");
        if (t == null) return;
        final World w = new World();
        final Object[][] cases = {
            { w.goods(w.siouxCamp), w.siouxCamp, "EXTORTSTUFF",
              new String[] { "Große Entdecker", "Sioux", "28 Felle", "Silver City" } },
            { w.answer("scoutSettlement.tributePoor", w.siouxCamp), w.siouxCamp,
              "EXTORTPOOR", new String[] { "Mächtiger Entdecker", "Sioux" } },
            { w.answer("scoutSettlement.tributeLaugh", w.arawakVillage), w.arawakVillage,
              "EXTORTLAUGH", new String[] { "Araukaner-Krieger" } },
            { w.answer("scoutSettlement.tributeNo", w.siouxCamp), w.siouxCamp,
              "EXTORTNO", new String[] { "Entdecker " + w.dutch.getName(), "Sioux" } } };
        for (Object[] c : cases) {
            final IndianSettlement is = (IndianSettlement) c[1];
            final ClassicAdvisorBox.Request r = notice(t, (ModelMessage) c[0], is, w.dutch);
            assertEquals(c[2], r.id);
            assertEquals(ClassicAdvisorBox.Portrait.chief(
                ClassicGUI.tribeIndex(is.getOwner())).sprite, r.portrait.sprite);
            assertEquals(ClassicVillageBox.TRIBUTE_CHAIN_MS, r.chainMs);
            assertTrue(r.isNotice());
            for (String s : (String[]) c[3]) {
                assertTrue(c[2] + ": " + s + " in " + r.plainText(), r.plainText().contains(s));
            }
        }
        // FreeCol's own answers (classic, freecol rules): FreeCol's words.
        final ModelMessage agree = new ModelMessage(ModelMessage.MessageType.FOREIGN_DIPLOMACY,
            "scoutSettlement.tributeAgree", w.soldier, w.siouxCamp).addAmount("%amount%", 50);
        assertEquals("message scoutSettlement.tributeAgree",
                     notice(t, agree, w.siouxCamp, w.dutch).id);
    }

    /**
     * A tribe's own declaration of war (Z2.3; under the levi rule an
     * attack starts none, the tribe may still declare it when its tension
     * passes hateful): FreeCol's «Schlechte Neuigkeiten, Eure Exzellenz,
     * Sioux hat uns den Krieg erklärt!» in GAME.TXT's @INDIANWAR words
     * with the chief (SRC: never seen); a European's declaration keeps
     * FreeCol's words.  The win notice of our attack on natives is a win
     * notice by its key ({@link ClassicNotices#WIN_NOTICES}), from the
     * attacker only.
     */
    public void testTheWarNoticesOfTheNatives() {
        final ClassicText t = pack("testTheWarNoticesOfTheNatives");
        if (t == null) return;
        final World w = new World();
        final ModelMessage sioux = new ModelMessage(ModelMessage.MessageType.FOREIGN_DIPLOMACY,
            ClassicNotices.WAR_DECLARED, w.sioux)
            .addStringTemplate("%nation%", w.sioux.getNationLabel());
        final ClassicAdvisorBox.Request r = ClassicNotices.request(t, "message "
            + sioux.getId(), sioux, Messages.message(sioux), w.sioux, w.dutch, "x", null);
        assertEquals("INDIANWAR", r.id);
        assertEquals(ClassicAdvisorBox.Portrait.chief(SIOUX).sprite, r.portrait.sprite);
        assertTrue(r.plainText(), r.plainText().contains("Wir Sioux"));
        assertTrue(r.plainText(), r.plainText().contains("KRIEG"));
        final Player france = w.game.getPlayerByNationId("model.nation.french");
        final ModelMessage french = new ModelMessage(ModelMessage.MessageType.FOREIGN_DIPLOMACY,
            ClassicNotices.WAR_DECLARED, france)
            .addStringTemplate("%nation%", france.getNationLabel());
        assertEquals("message " + ClassicNotices.WAR_DECLARED, ClassicNotices.request(t,
            "message " + french.getId(), french, Messages.message(french), france, w.dutch,
            "x", null).id);
        // The win notices, by key and source.
        final java.util.Set<String> ours = java.util.Set.of(w.soldier.getId());
        for (String id : new String[] { "combat.unitSlaughtered.enemy.brave",
                                        "combat.unitSlaughtered.enemy",
                                        "combat.unitDemoted.enemy.armedBrave",
                                        "combat.unitDemotedToUnarmed.enemy" }) {
            final ModelMessage m = new ModelMessage(ModelMessage.MessageType.COMBAT_RESULT,
                                                    id, w.soldier);
            assertTrue(id, ClassicNotices.isOurWinOverNatives(m, ours));
            assertFalse(id, ClassicNotices.isOurWinOverNatives(m, java.util.Set.of()));
        }
        for (String id : new String[] { "combat.unitSlaughtered.ours.freeColonist",
                                        "combat.newConvertFromAttack",
                                        "combat.destroySettlement.enemy",
                                        "combat.unitPromoted" }) {
            assertFalse(id, ClassicNotices.isOurWinOverNatives(new ModelMessage(
                ModelMessage.MessageType.COMBAT_RESULT, id, w.soldier), ours));
        }
        // An attack on natives: a village's owner, else the defender's.
        assertTrue(ClassicNotices.attacksNatives(w.soldier, w.siouxCamp.getTile()));
        final net.sf.freecol.common.model.Tile west
            = w.soldier.getTile().getNeighbourOrNull(net.sf.freecol.common.model.Direction.W);
        assertFalse(ClassicNotices.attacksNatives(w.soldier, west));
        // A European's unit or colony is no attack on natives (the review
        // of part Z: no test had one, so a gate that counts every attack
        // kept the suites green), so the «Sieg bei …» of a fight with the
        // other Europeans stays.
        new ServerUnit(w.game, west, france, spec().getUnitType("model.unit.freeColonist"),
                       spec().getRole("model.role.soldier"));
        assertFalse("a French soldier", ClassicNotices.attacksNatives(w.soldier, west));
        final net.sf.freecol.common.model.Colony quebec
            = net.sf.freecol.util.test.FreeColTestUtils.getColonyBuilder().player(france)
                .colonyName("Quebec").colonyTile(w.game.getMap().getTile(5, 3)).build();
        assertFalse("a French colony", ClassicNotices.attacksNatives(w.soldier,
                                                                    quebec.getTile()));
        // A brave on open land is natives.
        final net.sf.freecol.common.model.Tile east
            = w.soldier.getTile().getNeighbourOrNull(net.sf.freecol.common.model.Direction.E);
        new ServerUnit(w.game, east, w.sioux, spec().getUnitType("model.unit.brave"));
        assertTrue("a brave", ClassicNotices.attacksNatives(w.soldier, east));
    }

    /**
     * The turn start's mood notices (Z2.4; Roger's event #7,
     * frame_054799: FreeCol's «Das Oberhaupt der Apachen in Igihua'-a
     * begrüßt seine Freunde …» without the chief): FreeCol's «content»
     * in the original's @INDIANCOMMENT words with the chief, «Die {Sioux}
     * freuen sich über den Wohlstand ihrer Nachbarn, der {Holl.}.» (V
     * war-french #26477, the words nearly FreeCol's); «angry» the plain
     * @PISS0 «Der {Sioux}-Stamm ist nun …» with @ATTITUDINAL and
     * @ATTITUDE at the village's alarm (I), with the chief.
     */
    public void testTheMoodNoticesWithTheChief() {
        final ClassicText t = pack("testTheMoodNoticesWithTheChief");
        if (t == null) return;
        final World w = new World();
        final ModelMessage content = mood(w, "content");
        final ClassicAdvisorBox.Request c = ClassicNotices.request(t, "message "
            + content.getId(), content, Messages.message(content), w.siouxCamp, w.dutch,
            "x", null);
        assertEquals("INDIANCOMMENT", c.id);
        assertEquals(ClassicAdvisorBox.Portrait.chief(SIOUX).sprite, c.portrait.sprite);
        assertTrue(c.plainText(), c.plainText().startsWith(
            "Die Sioux freuen sich über den Wohlstand ihrer Nachbarn, der Holl.."));
        final Object[][] alarms = {
            // the village's alarm, the words
            { 701, "Leicht Verärgert" }, { 721, "Etwas Verärgert" },
            { 790, "Äußerst Verärgert" }, { 650, "Unruhig" }, { 1000, "Kriegerisch" } };
        for (Object[] a : alarms) {
            w.siouxCamp.setAlarm(w.dutch, new net.sf.freecol.common.model.Tension((Integer) a[0]));
            final ModelMessage angry = mood(w, "angry");
            final ClassicAdvisorBox.Request r = ClassicNotices.request(t, "message "
                + angry.getId(), angry, Messages.message(angry), w.siouxCamp, w.dutch, "x", null);
            assertEquals("PISS0", r.id);
            assertEquals(ClassicAdvisorBox.Portrait.chief(SIOUX).sprite, r.portrait.sprite);
            assertTrue(a[0] + ": " + r.plainText(), r.plainText().startsWith(
                "Der Sioux-Stamm ist nun "));
            assertTrue(a[0] + ": " + r.plainText(), r.plainText().contains((String) a[1]));
        }
        // The rows: the level, and fifths of it from the top.
        final net.sf.freecol.common.model.Tension.Level[] levels
            = net.sf.freecol.common.model.Tension.Level.values();
        for (int i = 0; i < levels.length; i++) {
            assertEquals(i, ClassicNotices.attitudeRow(
                new net.sf.freecol.common.model.Tension(levels[i].getLimit())));
        }
        assertEquals(4, ClassicNotices.attitudinalRow(new net.sf.freecol.common.model.Tension(0)));
        assertEquals(0, ClassicNotices.attitudinalRow(new net.sf.freecol.common.model.Tension(100)));
        assertEquals(2, ClassicNotices.attitudinalRow(new net.sf.freecol.common.model.Tension(350)));
        assertEquals(0, ClassicNotices.attitudinalRow(new net.sf.freecol.common.model.Tension(
            net.sf.freecol.common.model.Tension.TENSION_MAX)));
    }

    /** FreeCol's mood notice of the Sioux camp as the server sends it. */
    private static ModelMessage mood(World w, String level) {
        return new ModelMessage(ModelMessage.MessageType.FOREIGN_DIPLOMACY,
            "model.player.alarmIncrease.tension." + level, w.siouxCamp)
            .addStringTemplate("%nation%", w.sioux.getNationLabel())
            .addStringTemplate("%enemy%", w.dutch.getNationLabel())
            .addName("%settlement%", w.siouxCamp.getName());
    }

    /**
     * An uncontacted tribe's land (Z2.4, N-F9; Roger's event #6,
     * frame_030679: FreeCol's «Einige unkontaktierte Menschen leben in
     * diesen Ländern» with two rows): the original's @INDIANLAND with the
     * tribe's name, its three rows, the pay row priced and greyed without
     * the gold, the bar and Escape on leave, the chief.
     */
    public void testTheUncontactedTribesLand() {
        final ClassicText t = pack("testTheUncontactedTribesLand");
        if (t == null) return;
        final World w = new World();
        final Player iroquois = w.game.getPlayerByNationId("model.nation.iroquois");
        assertFalse(iroquois.hasContacted(w.dutch));
        final ClassicAdvisorBox.Request r = ClassicNativeLand.claimRequest(t, iroquois,
            false, 146, true, "Base");
        assertTrue(r.plainText(), r.plainText().startsWith("\"Ihr seid in Irokesen-Land"));
        assertEquals(3, r.rows.size());
        assertEquals("\"Wir bieten Euch 146$ für dieses Land.\"",
                     ClassicAdvisorBox.plain(r.rows.get(ClassicNativeLand.PAY_ROW)));
        assertEquals(ClassicNativeLand.LEAVE_ROW, r.defaultRow);
        assertEquals(ClassicNativeLand.LEAVE_ROW, r.escapeAnswer());
        assertEquals("IND3A0.SS.000", r.portrait.sprite);
        assertEquals(net.sf.freecol.common.model.Constants.ClaimAction.CLAIM_ACCEPT,
                     ClassicNativeLand.action(r, ClassicNativeLand.PAY_ROW));
        assertFalse("pay greyed without the gold", ClassicNativeLand.claimRequest(t,
            iroquois, false, 146, false, "Base").enabled(ClassicNativeLand.PAY_ROW));
    }

    /**
     * The golden check of @INDIANCOMMENT (the pack and {@code
     * -Dclassic.clips}): war-french #26477, the box (84,82,236,36) as the
     * game builds it, with the Tupi chief in the frame's picture IND7A2 at
     * (0,10) (which of his four pictures comes when is A7's G15; with A0
     * the box would stand at 78), 0 px off on the box and the chief.
     */
    public void testTheCommentAgainstWarFrench() throws Exception {
        final ClassicText t = pack("testTheCommentAgainstWarFrench");
        final String clips = System.getProperty(ClassicTerrainGoldenTest.CLIPS_PROPERTY);
        final ClassicPackFiles p = ClassicPackFiles.runtime();
        final File f = (clips == null) ? null : new File(clips, "war-french/frame_026477.png");
        if (t == null || f == null || !f.isFile() || p == null
            || p.font(ClassicFont.TINY) == null) {
            System.err.println(getClass().getSimpleName()
                + ": INDIANCOMMENT golden skipped, no recordings or no pack");
            return;
        }
        final World w = new World();
        final Player tupi = w.game.getPlayerByNationId("model.nation.tupi");
        final IndianSettlement camp = new FreeColTestCase.IndianSettlementBuilder(w.game)
            .player(tupi).settlementTile(w.game.getMap().getTile(5, 12)).capital(false).build();
        final ModelMessage m = new ModelMessage(ModelMessage.MessageType.FOREIGN_DIPLOMACY,
            "model.player.alarmIncrease.tension.content", camp)
            .addStringTemplate("%nation%", tupi.getNationLabel())
            .addStringTemplate("%enemy%", w.dutch.getNationLabel())
            .addName("%settlement%", camp.getName());
        final ClassicAdvisorBox.Request r = ClassicNotices.request(t, "message " + m.getId(),
            m, Messages.message(m), camp, w.dutch, "x", null);
        assertEquals("INDIANCOMMENT", r.id);
        final BufferedImage a2 = p.image(ClassicPackFiles.ssKey("IND7A2.SS.000"));
        final int[] got = compare(ImageIO.read(f), r, p, new Rectangle(84, 82, 236, 36), a2);
        System.out.println(getClass().getSimpleName() + ": INDIANCOMMENT golden, war-french"
            + " #26477 " + got[0] + " px, off " + got[1] + ", arrow " + got[2]);
        assertTrue("compared " + got[0], got[0] > 236 * 36);
        assertEquals("pixels off", 0, got[1]);
    }

    /**
     * FreeCol's question before a tribute (Z2.2; Roger's event #12,
     * frame_064125) is never shown: its four versions answer «yes» at
     * once, no box; another confirm still asks.
     */
    public void testTheTributeQuestionIsNeverAsked() {
        final ClassicGUI gui = new ClassicGUI(null);
        final ClassicGUISeamTest.KeyPrompter keys = new ClassicGUISeamTest.KeyPrompter();
        gui.prompter = keys;
        for (String id : ClassicGUI.TRIBUTE_QUESTIONS) {
            final StringTemplate q = StringTemplate.template(id)
                .addName("%settlement%", "Onondaga").addName("%nation%", "Irokesen");
            assertTrue(id, ClassicGUI.silentYes(q));
            keys.press("DOWN", "ENTER");   // FreeCol's «no»
            assertTrue(id, gui.modalConfirmDialog(null, q, (ImageIcon) null,
                "confirmTribute.yes", "confirmTribute.no", true));
        }
        assertEquals("no box", 0, keys.boxes.size());
        assertEquals(4, ClassicGUI.TRIBUTE_QUESTIONS.size());
        assertFalse(ClassicGUI.silentYes(StringTemplate.template("confirmTribute.european")));
    }

    /**
     * The golden check of the tribute answers (the pack and {@code
     * -Dclassic.clips}): each box as the game builds it, with the chief,
     * over the clip's frame (kept in {@code
     * playthrough-4-analysis/key/}), its place as measured, 0 px off on
     * the box and the chief's opaque pixels, the original's mouse arrow
     * excused: 048 #44727 @EXTORTSTUFF (0,79,236,42), the Sioux chief at
     * (210,10); 048 #48447 @EXTORTPOOR (the frame after the box's, when
     * the chief is drawn); 045 #30833 @EXTORTLAUGH (7,82,236,36), the
     * Arawak chief at (246,8) (the frame after the box's #30832, whose
     * chief has no head yet: the original draws it a frame later, as at
     * @EXTORTPOOR).
     */
    public void testTheTributeAnswersAgainstTheClips() throws Exception {
        final ClassicText t = pack("testTheTributeAnswersAgainstTheClips");
        final String clips = System.getProperty(ClassicTerrainGoldenTest.CLIPS_PROPERTY);
        final ClassicPackFiles p = ClassicPackFiles.runtime();
        if (t == null || clips == null || p == null || p.font(ClassicFont.TINY) == null) {
            System.err.println(getClass().getSimpleName()
                + ": tribute golden skipped, no recordings or no pack");
            return;
        }
        final World w = new World();
        final Object[][] cases = {
            { "c048_044727_EXTORTSTUFF_sioux.png", w.goods(w.siouxCamp), w.siouxCamp,
              new Rectangle(0, 79, 236, 42) },
            { "c048_048447_EXTORTPOOR_sioux.png",
              w.answer("scoutSettlement.tributePoor", w.siouxCamp), w.siouxCamp,
              new Rectangle(0, 79, 236, 42) },
            { "c045_030833_EXTORTLAUGH_arawak.png",
              w.answer("scoutSettlement.tributeLaugh", w.arawakVillage), w.arawakVillage,
              new Rectangle(7, 82, 236, 36) } };
        final StringBuilder out = new StringBuilder();
        int bad = 0;
        for (Object[] c : cases) {
            final File f = new File(clips, "playthrough-4-analysis/key/" + c[0]);
            if (!f.isFile()) {
                System.err.println(getClass().getSimpleName() + ": " + f + " missing");
                continue;
            }
            final ClassicAdvisorBox.Request r = notice(t, (ModelMessage) c[1],
                (IndianSettlement) c[2], w.dutch);
            final int[] got = compare(ImageIO.read(f), r, p, (Rectangle) c[3]);
            out.append(' ').append(c[0]).append(' ').append(got[0]).append(" px, off ")
                .append(got[1]).append(", arrow ").append(got[2]);
            assertTrue(c[0] + " compared " + got[0], got[0] > 236 * 36);
            bad += got[1];
        }
        System.out.println(getClass().getSimpleName() + ": tribute golden," + out);
        assertEquals("pixels off:" + out, 0, bad);
    }

    /**
     * A box with its portrait drawn over a frame: its place, then its
     * pixels and the portrait's opaque ones against the frame's, the
     * mouse arrow (indices 0, 7, 15 where they differ, inside one 12 x 20
     * rectangle) excused.
     *
     * @param frame The 320x200 frame.
     * @param r The box.
     * @param pack The pack.
     * @param box The box's measured place.
     * @return {compared, off, arrow}.
     */
    static int[] compare(BufferedImage frame, ClassicAdvisorBox.Request r,
                         ClassicPackFiles pack, Rectangle box) {
        return compare(frame, r, pack, box, null);
    }

    /**
     * The comparison ({@link #compare(BufferedImage, ClassicAdvisorBox.Request,
     * ClassicPackFiles, Rectangle)}) with another picture of the portrait.
     *
     * @param frame The 320x200 frame.
     * @param r The box.
     * @param pack The pack.
     * @param box The box's measured place.
     * @param picture The portrait's picture, or null for the request's.
     * @return {compared, off, arrow}.
     */
    static int[] compare(BufferedImage frame, ClassicAdvisorBox.Request r,
                         ClassicPackFiles pack, Rectangle box, BufferedImage picture) {
        final ClassicFont tiny = pack.font(ClassicFont.TINY);
        final BufferedImage pic = (picture != null) ? picture
            : (r.portrait.sprite == null) ? null
            : pack.image(ClassicPackFiles.ssKey(r.portrait.sprite));
        final ClassicAdvisorBox.Layout l = ClassicAdvisorBox.layout(r, tiny, pic);
        assertEquals(r.id, box, l.box);
        final BufferedImage canvas = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = canvas.createGraphics();
        g.drawImage(frame, 0, 0, null);
        ClassicAdvisorBox.paint(g, l, r.defaultRow, pack.image(ClassicMenuBar.WOOD_KEY), tiny);
        g.dispose();
        final Raster idx = (frame.getColorModel() instanceof IndexColorModel)
            ? frame.getRaster() : null;
        final int[] out = new int[3];
        final Rectangle arrow = new Rectangle();
        for (int y = 0; y < 200; y++) {
            for (int x = 0; x < 320; x++) {
                boolean on = l.box.contains(x, y);
                if (!on && pic != null) {
                    final int px = x - l.portraitAt.x, py = y - l.portraitAt.y;
                    on = px >= 0 && py >= 0 && px < pic.getWidth() && py < pic.getHeight()
                        && (pic.getRGB(px, py) >>> 24) != 0;
                }
                if (!on) continue;
                out[0]++;
                if ((frame.getRGB(x, y) & 0xFFFFFF) == (canvas.getRGB(x, y) & 0xFFFFFF)) continue;
                final int i = (idx == null) ? -1 : idx.getSample(x, y, 0);
                if (i == 0 || i == 7 || i == 15) {
                    out[2]++;
                    if (arrow.isEmpty()) arrow.setBounds(x, y, 1, 1);
                    else arrow.add(new Rectangle(x, y, 1, 1));
                } else {
                    out[1]++;
                }
            }
        }
        if (arrow.width > 12 || arrow.height > 20) {
            out[1] += out[2];   // more than one arrow: not excused
            out[2] = 0;
        }
        return out;
    }
}
