/**
 *  Copyright (C) 2002-2024   The FreeCol Team
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

package net.sf.freecol.tools.classicassets;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;
import java.util.Set;


/**
 * Decoder for {@code .PIK} full-screen pictures -- the game's screens and UI
 * chrome from Sid Meier's Colonization (1994).  Clean-room implementation
 * from the published format documentation.
 *
 * A {@code .PIK} is a MADSPACK with a header (part 0: height, width), an
 * 8-bit indexed image (part 1) and, usually, an embedded palette (part 2).
 * A few screens (e.g. COLONY.PIK) ship without a palette and are drawn under
 * the master gameplay palette, so VICEROY.PAL is supplied as a fallback.
 * A few others carry a palette the original never loads (EUROPE.PIK,
 * {@link #GAME_PALETTE_PIKS}); they are decoded under VICEROY.PAL too.
 */
public final class PikDecoder {

    /**
     * The screens the original draws under the game palette although they
     * carry one of their own: it loads no palette when they open, so their
     * own entries never reach the screen (W22p).  EUROPE.PIK's own sea and
     * market blues 54-59 are lighter than the game's; the clips show the
     * game's (clip006 #7980 opens it without a palette change; under
     * VICEROY.PAL the backdrop is 0 px off in six frames of clip005, 006
     * and 008).  Not a rule for every PIK: the new-game screens show their
     * own 54-59.
     */
    static final Set<String> GAME_PALETTE_PIKS = Set.of("EUROPE.PIK");

    private PikDecoder() {}

    /**
     * Whether the original draws this screen under the game palette
     * instead of its own ({@link #GAME_PALETTE_PIKS}).
     *
     * @param name The file name, e.g. {@code EUROPE.PIK} (any case).
     * @return True for such a screen.
     */
    public static boolean drawnWithGamePalette(String name) {
        return name != null
            && GAME_PALETTE_PIKS.contains(name.toUpperCase(Locale.ROOT));
    }

    /**
     * Decode a {@code .PIK} screen through its own palette, or VICEROY.PAL
     * when it has none.
     *
     * @param file The whole {@code .PIK} file contents.
     * @param viceroy The master palette, used for palette-less screens; may
     *     be {@code null} if every screen is known to embed its own.
     * @return The screen as an opaque RGB image.
     */
    public static BufferedImage decode(byte[] file, Palette viceroy) {
        return decode(file, viceroy, false);
    }

    /**
     * Decode a {@code .PIK} screen.
     *
     * @param file The whole {@code .PIK} file contents.
     * @param viceroy The master palette, used for palette-less screens and
     *     with {@code gamePalette}; may be {@code null} if neither occurs.
     * @param gamePalette Whether to ignore the screen's own palette and use
     *     {@code viceroy} ({@link #drawnWithGamePalette}).
     * @return The screen as an opaque RGB image.
     */
    public static BufferedImage decode(byte[] file, Palette viceroy,
                                       boolean gamePalette) {
        List<byte[]> parts = MadsPack.read(file);
        byte[] header = parts.get(0);
        int height = Bytes.u16(header, 0);
        int width = Bytes.u16(header, 2);

        Palette pal;
        if (parts.size() >= 3 && !gamePalette) {
            pal = Palette.readCol(parts.get(2));
        } else if (viceroy != null) {
            pal = viceroy;
        } else {
            throw new IllegalStateException(gamePalette
                ? "a PIK drawn under the game palette needs VICEROY.PAL"
                : "palette-less PIK needs VICEROY.PAL");
        }

        byte[] indices = parts.get(1);
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int o = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                img.setRGB(x, y, pal.argb[indices[o++] & 0xFF]);
            }
        }
        return img;
    }
}
