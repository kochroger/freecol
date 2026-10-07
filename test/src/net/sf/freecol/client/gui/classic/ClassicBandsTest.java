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
import java.util.Map;

import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * The voyage bands' texts and {@code @TUTORIAL17} (master plan W13, spec R4
 * sections 3 and 5.8): with stand-in texts, and with the pack's original
 * texts where the pack is built (else skipped, as the other text tests).
 */
public class ClassicBandsTest extends FreeColTestCase {

    /** Stand-in texts with the original's layout (the originals are never in the repository). */
    private static ClassicText standIn() throws Exception {
        final File dir = Files.createTempDirectory("bands").toFile();
        try {
            final File game = new File(dir, "GAME.TXT");
            final File names = new File(dir, "NAMES.TXT");
            final File labels = new File(dir, "LABELS.TXT");
            Files.write(game.toPath(), ("@TUTORIAL17\r\n@width=300\r\n@y=10\r\n"
                + "@smallfont\r\nPort %STRING0, %STRING1. Ziel: %STRING2.\r\n\r\n"
                + "@END\r\n").getBytes(StandardCharsets.ISO_8859_1));
            final StringBuilder units = new StringBuilder();
            for (int i = 0; i < 20; i++) units.append("Unit").append(i).append(",  1, 1\r\n");
            Files.write(names.toPath(), ("@NATIONALITY\r\nEngl.\r\nFranz.\r\nSpan.\r\n"
                + "Holl.\r\n\r\n@COUNTRY\r\nEngland, 12\r\nFrankreich, 9\r\n"
                + "Spanien, 14\r\nHolland, 13\r\n\r\n@HOMEPORT\r\nLondon\r\n"
                + "La Rochelle\r\nSevilla\r\nAmsterdam\r\n\r\n@COLONYNAME\r\n"
                + "Neuengland\r\nNeufrankreich\r\nNeuspanien\r\nNeuholland\r\n\r\n"
                + "@UNIT\r\n" + units + "\r\n@END\r\n")
                .getBytes(StandardCharsets.ISO_8859_1));
            Files.write(labels.toPath(), ("@MISC\r\nm0\r\nm1\r\nm2\r\nm3\r\nm4\r\n"
                + "Ziel:\r\nAnkunft aus\r\nTrifft jetzt ein in\r\n\r\n@END\r\n")
                .getBytes(StandardCharsets.ISO_8859_1));
            return ClassicText.fromFiles(game, names, labels);
        } finally {
            for (File x : dir.listFiles()) x.delete();
            dir.delete();
        }
    }

    private Unit ship(Game game, String nation, String type) {
        final Player p = game.getPlayerByNationId(nation);
        return new ServerUnit(game, p.getEurope(), p, spec().getUnitType(type));
    }

    /**
     * The three bands of a ship of an original nation: nationality, ship
     * type (never its name), label, home port; the ship rows 13-18.
     */
    public void testBandsStandIn() throws Exception {
        final ClassicText t = standIn();
        final Game game = getStandardGame();
        final Unit m = ship(game, "model.nation.dutch", "model.unit.merchantman");
        final Unit g = ship(game, "model.nation.dutch", "model.unit.galleon");
        assertEquals("Holl. Unit14 Ziel: Amsterdam", ClassicBands.departure(t, m));
        assertEquals("Holl. Unit14 Trifft jetzt ein in Amsterdam",
                     ClassicBands.arrivalEurope(t, m));
        assertEquals("Holl. Unit15 Trifft jetzt ein in Amsterdam",
                     ClassicBands.arrivalEurope(t, g));
        assertEquals("Holl. Unit14 Ankunft aus Amsterdam", ClassicBands.arrivalNewWorld(t, m));
        final Unit f = ship(game, "model.nation.french", "model.unit.frigate");
        assertEquals("Franz. Unit17 Ziel: La Rochelle", ClassicBands.departure(t, f));
        // The first scene's band is the New World one.
        assertEquals(ClassicFirstScene.bandText(t, 3, 14), ClassicBands.arrivalNewWorld(t, m));
    }

    /**
     * Without the texts, or for a nation or type the original lacks:
     * FreeCol's words for the missing pieces, never an empty band.
     */
    public void testFallback() throws Exception {
        final Game game = getStandardGame();
        final Unit m = ship(game, "model.nation.dutch", "model.unit.merchantman");
        final String none = ClassicBands.departure(null, m);
        assertNotNull(none);
        assertFalse(none.isEmpty());
        assertNull(ClassicBands.band(null, 3, 14, ClassicBands.MISC_DESTINATION));
        assertNull(ClassicBands.band(standIn(), 4, 14, ClassicBands.MISC_DESTINATION));
        // A non-original nation in the original's texts: the label and
        // FreeCol's nation, type and port.
        assertNotNull(game.getPlayerByNationId("model.nation.swedish"));
        final Unit s = ship(game, "model.nation.swedish", "model.unit.merchantman");
        final String sw = ClassicBands.departure(standIn(), s);
        assertTrue(sw, sw.contains("Unit14 Ziel:"));
        assertFalse(sw, sw.startsWith("Holl."));
        assertEquals("-", ClassicBands.band(null, (Unit) null, 5));
    }

    /**
     * {@code @TUTORIAL17}'s values and box: home port, country, the New
     * World's name (the player's, else "Neuholland"); width 300, y 10, no
     * portrait, no rows.
     */
    public void testTutorial() throws Exception {
        final ClassicText t = standIn();
        final Game game = getStandardGame();
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        Map<String, String> v = ClassicBands.tutorialValues(t, dutch);
        assertEquals("Amsterdam", v.get("STRING0"));
        assertEquals("Holland", v.get("STRING1"));
        assertEquals("Neuholland", v.get("STRING2"));
        dutch.setNewLandName("Levi-Land");
        v = ClassicBands.tutorialValues(t, dutch);
        assertEquals("Levi-Land", v.get("STRING2"));
        final ClassicAdvisorBox.Request r = ClassicGUI.europeTipRequest(t, dutch);
        assertNotNull(r);
        assertEquals("TUTORIAL17", r.id);
        assertEquals("Port Amsterdam, Holland. Ziel: Levi-Land.", r.plainText());
        assertEquals(0, r.plainRows().length);
        assertNull(ClassicGUI.europeTipRequest(null, dutch));
        // Once per game: bit 17 of the player's record.
        assertFalse(ClassicGUI.tipShown(dutch, 17));
        new ClassicGUI(null).markTip(dutch, 17);
        assertTrue(ClassicGUI.tipShown(dutch, 17));
        assertEquals(1 << 17, dutch.getClassicTips());
    }

    /**
     * The original's texts (the pack): the bands as the clips show them
     * (landfall #25735 / #27878, clip005 #18613, clip008 #26209) and where
     * they start (ink x 94, 71, 83, 83: the band's first glyph).
     */
    public void testBandsOriginal() {
        final ClassicPackFiles pack = ClassicPackFiles.runtime();
        final ClassicText t = ClassicText.load(pack);
        final ClassicFont tiny = (pack == null) ? null : pack.font(ClassicFont.TINY);
        if (t == null || tiny == null || t.misc(ClassicBands.MISC_ARRIVES_IN) == null) {
            System.err.println(getClass().getSimpleName()
                + ": original band texts skipped, no pack (ant classic-assets)");
            return;
        }
        final Game game = getStandardGame();
        final Unit m = ship(game, "model.nation.dutch", "model.unit.merchantman");
        final Unit g = ship(game, "model.nation.dutch", "model.unit.galleon");
        final String depart = ClassicBands.departure(t, m);
        final String arrive = ClassicBands.arrivalEurope(t, m);
        final String galleon = ClassicBands.arrivalEurope(t, g);
        final String back = ClassicBands.arrivalNewWorld(t, m);
        assertEquals("Holl. Handelsschiff Ziel: Amsterdam", depart);
        assertEquals("Holl. Handelsschiff Trifft jetzt ein in Amsterdam", arrive);
        assertEquals("Holl. Galeone Trifft jetzt ein in Amsterdam", galleon);
        assertEquals("Holl. Handelsschiff Ankunft aus Amsterdam", back);
        assertEquals(94, ClassicMenuBar.bandX(tiny, depart));
        assertEquals(71, ClassicMenuBar.bandX(tiny, arrive));
        assertEquals(83, ClassicMenuBar.bandX(tiny, galleon));
        assertEquals(83, ClassicMenuBar.bandX(tiny, back));
        // @TUTORIAL17 from GAME.TXT: the box of landfall #27956.
        final Player dutch = game.getPlayerByNationId("model.nation.dutch");
        final ClassicAdvisorBox.Request r = ClassicGUI.europeTipRequest(t, dutch);
        assertNotNull(r);
        assertTrue(r.plainText(), r.plainText().startsWith(
            "Der Europa-Statusbildschirm zeigt Ihren Heimathafen in Amsterdam, Holland."));
        assertTrue(r.plainText(), r.plainText().endsWith("\"Ziel: Neuholland\"."));
    }
}
