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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;


/**
 * Tests of the original-text parser ({@link ClassicText}).  The files are
 * SYNTHETIC: they use the original's byte format (CRLF, the game's umlaut
 * codes, ';' comments, sections, directives, the 0x1A tail, a blank line
 * inside {@code @MISC}) but contain no original text.
 */
public class ClassicTextTest extends TestCase {

    /** Bytes from a string whose chars are raw byte values. */
    private static byte[] raw(String s) {
        final ByteArrayOutputStream b = new ByteArrayOutputStream();
        for (int i = 0; i < s.length(); i++) b.write(s.charAt(i) & 0xFF);
        return b.toByteArray();
    }

    private static final String GAME =
        ";\r\n;  synthetic header\r\n;\r\n\r\n"
        + "@SCROLL\r\n@width=81\r\n@x=235\r\n@y=16\r\n"
        + "^\r\n^Gr\u001C\u001De %COUNTRY\r\nzwei\r\n\r\nJa\r\nNein\r\n\r\n"
        + "@ASKNAME\r\n@width=300\r\n^^Tipp `" + (char) 0x5C + "\r\n_\r\n_\r\n@options\r\n"
        + "______\r\n\r\n\r\n"
        + "@PICK\r\n@default=1\r\nWahl\r\n\r\nA\r\nB\r\n\r\n"
        + "@END\r\n\u001A";

    private static final String NAMES =
        ";  synthetic\r\n@COUNTRY\r\nAlpha,          12\r\nBeta,  9\r\n\r\n"
        + ";@COUNTRY2 commented out\r\n"
        + "@DIFFICULTY\r\nLeicht\r\nMeisterk\u001Cnig\r\n\r\n";

    private static final String LABELS =
        "@INFO\r\ni0\r\n\r\n@MISC\r\nm0\r\nm1\r\n\r\nm2\r\n\u007Fm3\r\n@OTHER\r\nx\r\n";

    private File dir;

    @Override
    protected void setUp() throws IOException {
        this.dir = Files.createTempDirectory("classic-text").toFile();
        final File text = new File(this.dir, "text");
        text.mkdirs();
        Files.write(new File(text, "GAME.TXT").toPath(), raw(GAME));
        Files.write(new File(text, "NAMES.TXT").toPath(), raw(NAMES));
        Files.write(new File(text, "LABELS.TXT").toPath(), raw(LABELS));
    }

    @Override
    protected void tearDown() {
        for (String n : new String[] { "GAME.TXT", "NAMES.TXT", "LABELS.TXT" }) {
            new File(new File(this.dir, "text"), n).delete();
        }
        new File(this.dir, "text").delete();
        this.dir.delete();
    }

    private ClassicText text() {
        final ClassicText t = ClassicText.load(ClassicPackFiles.forDirectory(this.dir));
        assertNotNull(t);
        return t;
    }

    public void testDecodeTable() {
        assertEquals('ö', ClassicText.decodeByte(0x1C));
        assertEquals('ß', ClassicText.decodeByte(0x1D));
        assertEquals('Ä', ClassicText.decodeByte(0x1E));
        assertEquals('Ö', ClassicText.decodeByte(0x1F));
        assertEquals('Ü', ClassicText.decodeByte(0x5C));
        assertEquals('ä', ClassicText.decodeByte(0x60));
        assertEquals('ü', ClassicText.decodeByte(0x7F));
        assertEquals('A', ClassicText.decodeByte('A'));
        assertEquals('{', ClassicText.decodeByte('{'));
        // The table agrees with the font's mapping, so decoded text draws
        // back on the very glyph codes it came from.
        for (char[] e : ClassicText.TABLE) {
            assertEquals((int) e[0], ClassicFont.toCode(e[1]));
        }
    }

    public void testDecodeStopsAndComments() {
        final List<String> lines = ClassicText.decode(raw("a\r\n;c\r\n\r\nb\r\n@END\r\nnot\r\n"));
        assertEquals(3, lines.size());
        assertEquals("a", lines.get(0));
        assertEquals("", lines.get(1));
        assertEquals("b", lines.get(2));
        final List<String> eof = ClassicText.decode(raw("x\r\ny\u001Az\r\n"));
        assertEquals(2, eof.size());
        assertEquals("y", eof.get(1));
    }

    public void testMessage() {
        final ClassicText.Message m = text().message("SCROLL");
        assertNotNull(m);
        assertEquals(Integer.valueOf(81), m.width);
        assertEquals(Integer.valueOf(235), m.x);
        assertEquals(Integer.valueOf(16), m.y);
        assertNull(m.defaultOption);
        assertFalse(m.hasOptions);
        assertEquals(3, m.text.size());
        assertEquals("^", m.text.get(0));
        assertEquals("^Größe %COUNTRY", m.text.get(1));     // markup kept
        assertEquals(2, m.options.size());
        assertEquals("Nein", m.options.get(1));

        final ClassicText.Message n = text().message("ASKNAME");
        assertTrue(n.hasOptions);
        assertEquals(3, n.text.size());
        assertEquals("^^Tipp äÜ", n.text.get(0));
        assertEquals(1, n.options.size());
        assertEquals(6, n.options.get(0).length());

        final ClassicText.Message p = text().message("PICK");
        assertEquals(Integer.valueOf(1), p.defaultOption);
        assertEquals(1, p.text.size());
        assertEquals(2, p.options.size());

        assertNull(text().message("MISSING"));
    }

    public void testNames() {
        final List<String[]> c = text().names("COUNTRY");
        assertEquals(2, c.size());
        assertEquals("Alpha", c.get(0)[0]);
        assertEquals("12", c.get(0)[1]);
        assertEquals("Beta", c.get(1)[0]);
        assertEquals("9", c.get(1)[1]);
        final List<String[]> d = text().names("DIFFICULTY");
        assertEquals(2, d.size());
        assertEquals("Meisterkönig", d.get(1)[0]);
        assertTrue(text().names("NONE").isEmpty());
    }

    public void testMiscSkipsBlankLines() {
        final ClassicText t = text();
        assertEquals("m0", t.misc(0));
        assertEquals("m1", t.misc(1));
        assertEquals("m2", t.misc(2));      // the blank line does not count
        assertEquals("üm3", t.misc(3));
        assertNull(t.misc(4));              // the next section is not @MISC
        assertNull(t.misc(-1));
    }

    public void testSubstitute() {
        final Map<String, String> v = new HashMap<>();
        v.put("COUNTRY", "Utopia");
        v.put("STRING0", "x");
        v.put("STRING10", "long");
        assertEquals("von Utopia.", ClassicText.substitute("von %COUNTRY.", v));
        assertEquals("50%", ClassicText.substitute("50%%", v));
        assertEquals("{50%-x}", ClassicText.substitute("{50%-x}", v));
        assertEquals("x und %STRING1", ClassicText.substitute("%STRING0 und %STRING1", v));
        assertEquals("long", ClassicText.substitute("%STRING10", v));   // longest key
        assertEquals("100%", ClassicText.substitute("100%", v));
        assertNull(ClassicText.substitute(null, v));
    }

    public void testUpperAscii() {
        assertEquals("AöZ", ClassicText.upperAscii("aöz"));
        assertEquals("ÄBC:", ClassicText.upperAscii("Äbc:"));
    }

    public void testMissingFiles() {
        new File(new File(this.dir, "text"), "LABELS.TXT").delete();
        final File other = new File(this.dir, "other");
        assertNull(ClassicText.load(ClassicPackFiles.forDirectory(other)));
        assertNull(ClassicText.load(null));
        assertFalse(ClassicPackFiles.forDirectory(this.dir).hasNewWorldChain());
    }
}
