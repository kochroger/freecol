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

package net.sf.freecol.common.io;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import junit.framework.TestCase;

import net.sf.freecol.common.MemoryManager;
import net.sf.freecol.common.resources.ImageResource;
import net.sf.freecol.common.resources.ResourceMapping;


/**
 * Pins what {@link FreeColDataFile#getResourceMapping} finds as an image's
 * size alternatives ({@code <name>.size<N>.<ext>}) and variations
 * ({@code <name><N>.<ext>}, one or two digits), so the per-call directory
 * listing cache (the Classic UI fast start) provably keeps the semantics of
 * the old per-image {@code Files.list} + {@code String.matches} search:
 * same files, same (sorted) order, the image itself never its own
 * variation, other images in the same directory unaffected, and a later
 * call sees files added in between (the cache lives for one call only).
 */
public class FreeColDataFileListingTest extends TestCase {

    private File dir;


    @Override
    protected void setUp() throws IOException {
        this.dir = Files.createTempDirectory("fcdf-listing").toFile();
        write("resources.properties",
              "image.x=x.png\n"
              + "image.y=y.png\n"
              + "image.z=sub/z.png\n");
        // x: two size alternatives (listed in sorted order), two variations,
        // the second with a size alternative of its own.
        for (String f : new String[] {
                "x.png", "x.size64.png", "x.size2.png", "x2.png", "x13.png",
                "x2.size3.png",
                // Must NOT match x: wrong suffix, letters, three digits,
                // size of another image.
                "x.size2.jpg", "xa.png", "x123.png", "xy.size2.png",
                "y.png" }) {
            write(f, "");
        }
        write("sub/z.png", "");
    }

    @Override
    protected void tearDown() {
        delete(this.dir);
    }

    private void write(String rel, String content) throws IOException {
        final File f = new File(this.dir, rel);
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private static void delete(File f) {
        final File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) delete(k);
        f.delete();
    }

    /**
     * The size alternatives a lookup must find: none in a low-memory JVM,
     * where FreeColDataFile deliberately skips them (MemoryManager).
     */
    private static List<String> sizes(String... s) {
        return MemoryManager.isHighQualityGraphicsEnabled() ? list(s) : list();
    }

    /** The private alternative locators, as file names (empty if none). */
    private static List<String> alternatives(ImageResource r) throws Exception {
        final Field fld = ImageResource.class.getDeclaredField("alternativeLocators");
        fld.setAccessible(true);
        @SuppressWarnings("unchecked")
        final List<URI> l = (List<URI>) fld.get(r);
        final List<String> out = new ArrayList<>();
        if (l != null) for (URI u : l) out.add(new File(u).getName());
        return out;
    }

    /** The variations' file names, in order (the image itself excluded). */
    private static List<String> variations(ImageResource r) {
        final List<String> out = new ArrayList<>();
        for (int i = 0; i < r.getNumberOfVariations() - 1; i++) {
            out.add(new File(r.getVariation(i).getResourceLocator()).getName());
        }
        return out;
    }

    private static List<String> list(String... s) {
        final List<String> l = new ArrayList<>();
        Collections.addAll(l, s);
        return l;
    }

    public void testSizesAndVariationsOfX() throws Exception {
        final ResourceMapping m = new FreeColDataFile(this.dir).getResourceMapping();
        final ImageResource x = m.getImageResource("image.x");
        assertNotNull(x);
        assertEquals("x.png", new File(x.getResourceLocator()).getName());
        assertEquals(sizes("x.size2.png", "x.size64.png"), alternatives(x));
        // Sorted listing order: "x13.png" < "x2.png".
        assertEquals(list("x13.png", "x2.png"), variations(x));
        assertEquals(list(), alternatives(x.getVariation(0)));
        assertEquals(sizes("x2.size3.png"), alternatives(x.getVariation(1)));
    }

    public void testOtherImagesUnaffected() throws Exception {
        final ResourceMapping m = new FreeColDataFile(this.dir).getResourceMapping();
        final ImageResource y = m.getImageResource("image.y");
        assertNotNull(y);
        assertEquals(list(), alternatives(y));
        assertEquals(list(), variations(y));
        // An image in another directory gets that directory's listing.
        final ImageResource z = m.getImageResource("image.z");
        assertNotNull(z);
        assertEquals(list(), alternatives(z));
        assertEquals(list(), variations(z));
    }

    public void testListingIsPerCall() throws Exception {
        assertEquals(list(), alternatives(new FreeColDataFile(this.dir)
                .getResourceMapping().getImageResource("image.y")));
        write("y.size7.png", "");
        write("y5.png", "");
        final ImageResource y = new FreeColDataFile(this.dir)
            .getResourceMapping().getImageResource("image.y");
        assertEquals(sizes("y.size7.png"), alternatives(y));
        assertEquals(list("y5.png"), variations(y));
    }
}
