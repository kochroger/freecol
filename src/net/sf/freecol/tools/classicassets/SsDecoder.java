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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * Decoder for {@code .SS} "shape set" sprite files -- multi-frame sets of
 * units, terrain, buildings and UI icons from Sid Meier's Colonization
 * (1994).  Clean-room implementation from the published format
 * documentation.
 *
 * An {@code .SS} is a MADSPACK with four parts: a header (part 0), a table of
 * per-sprite headers (part 1), a palette (part 2) and the packed pixel data
 * (part 3).  Each sprite is run-length encoded with a per-line "linemode"
 * command scheme; the palette index {@code 0xFD} is the transparent
 * background, decoded here to a fully transparent pixel.
 */
public final class SsDecoder {

    private static final int PFLAG_OFFSET = 0x0C;
    private static final int NSPRITES_OFFSET = 0x26;
    private static final int SPRITE_HEADER_SIZE = 16;

    private static final int BG = 0xFD;          // transparent background index
    private static final int TRANSPARENT = 0x00000000;

    private SsDecoder() {}

    /**
     * Decode every frame of an {@code .SS} file.
     *
     * @param file The whole {@code .SS} file contents.
     * @return The frames as ARGB images, in file order.
     */
    public static List<BufferedImage> decode(byte[] file) {
        List<byte[]> parts = MadsPack.read(file);
        byte[] header = parts.get(0);
        int mode = Bytes.u8(header, 0);              // part-3 encoding: 0 raw, 1 FAB
        int pflag = Bytes.u8(header, PFLAG_OFFSET);
        int nsprites = Bytes.u16(header, NSPRITES_OFFSET);

        byte[] spriteHeaders = parts.get(1);
        Palette pal = (pflag != 0)
            ? Palette.readCol(parts.get(2))
            : Palette.readRex(parts.get(2));
        byte[] pixels = parts.get(3);

        List<BufferedImage> frames = new ArrayList<>(nsprites);
        for (int i = 0; i < nsprites; i++) {
            int base = i * SPRITE_HEADER_SIZE;
            int startOffset = (int) Bytes.u32(spriteHeaders, base);
            int length = (int) Bytes.u32(spriteHeaders, base + 4);
            int width = Bytes.u16(spriteHeaders, base + 12);
            int height = Bytes.u16(spriteHeaders, base + 14);
            frames.add(decodeSprite(pixels, startOffset, length, width, height, pal, mode));
        }
        return frames;
    }

    /**
     * The per-frame screen anchors that {@link #decode} drops: bytes 8-11
     * of each 16-byte sprite header hold the position of the sprite's
     * <em>bottom-centre</em> on the original 320x200 screen, as two
     * little-endian 16-bit values (read signed, so a sprite hanging off the
     * left or top edge would come out negative).
     *
     * <p>Why export them: the original composes its scenes from a backdrop
     * {@code .PIK} plus sprites that each know where they stand, so the
     * classic UI needs these numbers to put e.g. the king and the nation
     * banner of the audience screen exactly where the game does -- rather
     * than hard-coding positions measured from captures.  The top-left of a
     * frame of size {@code w x h} is {@code (ax - w/2, ay - h + 1)}
     * (integer division).  Verified on the audience captures
     * {@code opening_068}/{@code 039}: KING1 frame 0 has (ax,ay,w,h) =
     * (94,198,189,187), i.e. top-left (0,12); DUTCH1 gives (34,0), ENGLND1
     * (32,0); FRANCE1 (30,0) and SPAIN1 (35,0) follow from the same rule
     * (no capture shows them).
     *
     * @param file The whole {@code .SS} file contents.
     * @return One {@code {ax, ay, w, h}} per frame, in file order.
     */
    public static List<int[]> anchors(byte[] file) {
        List<byte[]> parts = MadsPack.read(file);
        int nsprites = Bytes.u16(parts.get(0), NSPRITES_OFFSET);
        byte[] spriteHeaders = parts.get(1);
        List<int[]> out = new ArrayList<>(nsprites);
        for (int i = 0; i < nsprites; i++) {
            int base = i * SPRITE_HEADER_SIZE;
            out.add(new int[] {
                (short) Bytes.u16(spriteHeaders, base + 8),
                (short) Bytes.u16(spriteHeaders, base + 10),
                Bytes.u16(spriteHeaders, base + 12),
                Bytes.u16(spriteHeaders, base + 14) });
        }
        return out;
    }

    private static BufferedImage decodeSprite(byte[] pixels, int startOffset,
            int length, int width, int height, Palette pal, int mode) {
        byte[] data = (mode == 0)
            ? Arrays.copyOfRange(pixels, startOffset, startOffset + length)
            : Fab.decode(pixels, startOffset).data;

        // A 0x0 sprite is stored as a lone stop command; represent it as a
        // 1x1 image (PNG cannot hold a 0x0 image).
        if (width == 0 || height == 0) {
            BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            img.setRGB(0, 0, pal.argb[0]);
            return img;
        }

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int k = 0;
        int x = 0;
        int y = 0;
        int lm = data[k++] & 0xFF;

        while (true) {
            if (lm == 0xFF) {
                // fill the rest of this line with background, then next line
                while (x < width) put(img, x++, y, TRANSPARENT);
                x = 0;
                y++;
                lm = data[k++] & 0xFF;
            } else if (lm == 0xFC) {
                break;                              // end of image
            } else {
                int c = data[k++] & 0xFF;
                if (c == 0xFF) {
                    while (x < width) put(img, x++, y, TRANSPARENT);
                    x = 0;
                    y++;
                    lm = data[k++] & 0xFF;
                } else if (lm == 0xFE) {
                    // pixel mode
                    if (c == 0xFE) {
                        int runLen = data[k++] & 0xFF;
                        int ci = data[k++] & 0xFF;
                        for (int n = 0; n < runLen; n++) put(img, x++, y, colour(pal, ci));
                    } else {
                        put(img, x++, y, colour(pal, c));
                    }
                } else if (lm == 0xFD) {
                    // multipixel mode: c is the run length, next byte the colour
                    int ci = data[k++] & 0xFF;
                    for (int n = 0; n < c; n++) put(img, x++, y, colour(pal, ci));
                } else {
                    throw new IllegalStateException("unknown SS linemode: " + lm);
                }
            }
        }
        return img;
    }

    private static int colour(Palette pal, int index) {
        return (index == BG) ? TRANSPARENT : pal.argb[index];
    }

    private static void put(BufferedImage img, int x, int y, int argb) {
        if (x >= 0 && x < img.getWidth() && y >= 0 && y < img.getHeight()) {
            img.setRGB(x, y, argb);
        }
    }
}
