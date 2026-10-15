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

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * The colony screen's <b>completion dissolve</b> (build spec M4.0 target
 * 7, used by M4.10): opened from «Zur Kolonie zoomen.» after a completion,
 * the screen is first drawn complete with the old picture; 19 frames later
 * (V: 19, 19, 17) the changed rectangles -- the new building's slot, the
 * town hall's bells, the band's production row -- dissolve into the new
 * picture in 55 frames (V clip 020 #57671 -&gt; #57690-#57744, #73443 -&gt;
 * #73462-#73516, #105689 -&gt; #105706-#105760), a random pixel dissolve,
 * as the woodcuts' (the same 55 frames and the same fixed order,
 * {@link ClassicWoodcut#revealed}, {@link ClassicDeparture#dissolveOrder}).
 *
 * <p>Only the pixels that differ inside the rectangles change; outside
 * them the old picture stays until the screen's next paint.  A pure state
 * on the 320x200 canvas's pixels (0xRRGGBB), on an injectable time (ms
 * since the screen was complete): tested headless against the clip
 * (ClassicColonyKitTest).
 */
final class ClassicColonyDissolve {

    /** From the complete screen to the dissolve's first frame: 19 frames (271 ms). */
    static final double START_MS = ClassicColonyLayout.frames(
        ClassicColonyLayout.DISSOLVE_START_FRAMES);

    /** The rectangles that dissolve (copies). */
    private final List<Rectangle> rects;

    /** The new picture's pixels. */
    private final int[] to;

    /** The pixels that change, in reveal order. */
    private final int[] order;

    /** How many are shown. */
    private int revealed = 0;


    /**
     * @param from The old picture (320x200 pixels, 0xRRGGBB).
     * @param to The new picture.
     * @param rects The rectangles that dissolve (their union).
     */
    ClassicColonyDissolve(int[] from, int[] to, List<Rectangle> rects) {
        final List<Rectangle> rs = new ArrayList<>();
        for (Rectangle r : rects) rs.add(new Rectangle(r));
        this.rects = Collections.unmodifiableList(rs);
        this.to = to.clone();
        this.order = order(from, to, this.rects);
    }

    /**
     * The pixels a dissolve reveals: those that differ inside the union of
     * the rectangles, in the fixed random order.
     *
     * @param from The old picture.
     * @param to The new picture.
     * @param rects The rectangles.
     * @return Their indices (y * 320 + x), in reveal order.
     */
    static int[] order(int[] from, int[] to, List<Rectangle> rects) {
        final int w = ClassicColonyLayout.W, h = ClassicColonyLayout.H;
        final boolean[] in = new boolean[w * h];
        for (Rectangle r : rects) {
            final Rectangle c = r.intersection(new Rectangle(0, 0, w, h));
            for (int y = c.y; y < c.y + c.height; y++) {
                for (int x = c.x; x < c.x + c.width; x++) in[y * w + x] = true;
            }
        }
        final int[] all = ClassicDeparture.dissolveOrder(from, to);
        int n = 0;
        for (int i : all) if (in[i]) n++;
        final int[] out = new int[n];
        int j = 0;
        for (int i : all) if (in[i]) out[j++] = i;
        return out;
    }

    /** @return The rectangles. */
    List<Rectangle> rects() {
        return this.rects;
    }

    /** @return The pixels that change. */
    int changed() {
        return this.order.length;
    }

    /** @return The pixels shown so far. */
    int revealed() {
        return this.revealed;
    }

    /**
     * How many pixels are shown a time after the screen was complete:
     * none before {@link #START_MS}, 1/55 in its first frame, all 54
     * frames later.
     *
     * @param sinceCompleteMs Milliseconds since the screen was complete.
     * @return 0 ... {@link #changed}.
     */
    int due(double sinceCompleteMs) {
        return ClassicWoodcut.revealed(this.order.length, sinceCompleteMs - START_MS);
    }

    /**
     * Show what is due on the picture shown.
     *
     * @param sinceCompleteMs Milliseconds since the screen was complete.
     * @param shown The shown pixels (the old picture, changed in place).
     * @return Whether a pixel changed.
     */
    boolean step(double sinceCompleteMs, int[] shown) {
        final int n = due(sinceCompleteMs);
        if (n <= this.revealed) return false;
        ClassicDeparture.apply(shown, this.to, this.order, this.revealed, n);
        this.revealed = n;
        return true;
    }

    /**
     * The old picture's pixels still showing a time after the screen was
     * complete (the screen paints its new picture, then these over it from
     * the old one; part V's dissolve on the hosted screen).
     *
     * @param sinceCompleteMs Milliseconds since the screen was complete.
     * @return Their indices (y * 320 + x), the next to be revealed first.
     */
    int[] pending(double sinceCompleteMs) {
        final int n = Math.max(0, Math.min(this.order.length, due(sinceCompleteMs)));
        return java.util.Arrays.copyOfRange(this.order, n, this.order.length);
    }

    /** @return Whether the new picture is complete. */
    boolean complete() {
        return this.revealed >= this.order.length;
    }

    /**
     * The recorder's words ({@code colony-dissolve}): the union's bounds
     * and the pixels.
     *
     * @return E.g. {@code "x88-167 y15-41 + x0-118 y163-174 px 1712"}.
     */
    String describe() {
        final StringBuilder sb = new StringBuilder();
        for (Rectangle r : this.rects) {
            if (sb.length() > 0) sb.append(" + ");
            sb.append('x').append(r.x).append('-').append(r.x + r.width - 1)
                .append(" y").append(r.y).append('-').append(r.y + r.height - 1);
        }
        return sb.append(" px ").append(this.order.length).toString();
    }
}
