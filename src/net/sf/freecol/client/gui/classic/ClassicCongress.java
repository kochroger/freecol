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
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.FoundingFather;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.StringTemplate;


/**
 * The original's founding father join (build spec D8c, clip008
 * 01-fathers section 4; V for the one join of the clip, Minuit in 1513).
 *
 * <ol>
 *   <li><b>{@code @FREEDOM}</b> (GAME.TXT, its {@code @width} 230, no
 *       portrait, centred: (42,85) 236x30, 0 px on #39296): "Holl.
 *       Gründerväter geben bekannt, daß {Peter Minuit} dem
 *       Kontinentalkongreß beigetreten ist!", {@code %STRING1} NAMES
 *       {@code @NATIONALITY}, {@code %STRING0} NAMES {@code @FATHERS} in
 *       gold.  The first message of the turn start,
 *       {@link #FREEDOM_AFTER_TURN_MS} after the indicator (#39272 ->
 *       #39296); FreeCol's own join notice
 *       ({@link #JOINED_MESSAGE}) is not shown.  Any key closes it.</li>
 *   <li><b>The congress hall</b> ({@code CCBKGD.PIK}, full screen, 0 px on
 *       #39719): black {@link #BLACK_AFTER_BOX_MS} after the box went
 *       (#39715 -> #39717), the hall {@link #HALL_AFTER_BLACK_MS} after the
 *       black (its tear #39718, complete #39719), the fathers already in
 *       Congress at their {@code CC-nn} anchors, back to front by the
 *       anchor's y (I: the clip's join was the first father), and the new
 *       father's figure dissolving in from {@link #DISSOLVE_AFTER_HALL_MS}
 *       after the hall over 55 frames (#39721 - #39775, 0 px with Minuit
 *       apart from the arrow), the woodcut's dissolve
 *       ({@link ClassicWoodcut#revealed}, the fixed seeded order).  The
 *       arrow is hidden from the hall to the dissolve's end and keeps its
 *       grey.  Then it stands until a key or a click.</li>
 *   <li><b>Black</b> for {@link #PAGE_AFTER_BLACK_MS} (#40093 -> #40108),
 *       then the father's <b>Colonopedia page</b>
 *       ({@link ClassicPedia#fatherPage}, 0 px on #40109); a key or a
 *       click brings the map back in one paint.</li>
 * </ol>
 *
 * <p>The trigger is our player's father set growing (not FreeCol's
 * message, which its message options can drop): {@link #joined}.  The
 * next father choice waits a turn ({@link ClassicFathers#withheld}).
 */
final class ClassicCongress {

    /** The hall's screen number ({@link ClassicWoodcut.Screen#k}): not a woodcut. */
    static final int HALL = 100;

    /** GAME.TXT's join message. */
    static final String SECTION = "FREEDOM";

    /** FreeCol's join notice ({@code ServerPlayer.csAddFoundingFather}). */
    static final String JOINED_MESSAGE = "model.player.foundingFatherJoinedCongress";

    /** The pack key of the hall. */
    static final String HALL_KEY = "image.classic_original.pik.CCBKGD.PIK";

    /** @FREEDOM after the turn start (V: #39272 -> #39296, 24 frames). */
    static final double FREEDOM_AFTER_TURN_MS = 342.0;

    /** The black after the box went (V: #39715 -> #39717). */
    static final double BLACK_AFTER_BOX_MS = 2 * ClassicWoodcut.FRAME_MS;

    /** The hall after the black (V: #39717 -> #39718, its tear). */
    static final double HALL_AFTER_BLACK_MS = ClassicWoodcut.FRAME_MS;

    /** The figure's first dissolve frame after the hall (V: #39718 -> #39721). */
    static final double DISSOLVE_AFTER_HALL_MS = 3 * ClassicWoodcut.FRAME_MS;

    /** The page after the hall's closing black (V: #40093 -> #40108, 0.21 s). */
    static final double PAGE_AFTER_BLACK_MS = 15 * ClassicWoodcut.FRAME_MS;

    /** The next box after the map is back (I: the layer's chain). */
    static final double FOLLOW_MS = ClassicAdvisorLayer.CHAIN_MS;


    private ClassicCongress() {}   // static helpers only

    /**
     * @param n A father's row ({@link ClassicFathers#index}).
     * @return His figure's frame stem, {@code CC-02.SS.000}.
     */
    static String figure(int n) {
        return String.format(Locale.ROOT, "CC-%02d.SS.000", n);
    }

    /**
     * Where a figure stands: its anchor (bottom centre) as a rectangle.
     *
     * @param pack The pack.
     * @param n The father's row.
     * @return The rectangle, or null without the figure or its anchor.
     */
    static Rectangle figureBounds(ClassicPackFiles pack, int n) {
        if (pack == null || n < 0 || n >= ClassicFathers.IDS.size()) return null;
        final String f = figure(n);
        final Point at = pack.spriteTopLeft(f);
        final BufferedImage img = pack.image(ClassicPackFiles.ssKey(f));
        if (at == null || img == null) return null;
        return new Rectangle(at.x, at.y, img.getWidth(), img.getHeight());
    }

    /**
     * The hall with these fathers, back to front: by the anchor's y (the
     * rectangle's bottom), then by row (I).
     *
     * @param pack The pack.
     * @param fathers Their rows; a row without its figure is left out.
     * @return The 320x200 picture, or null without the hall.
     */
    static BufferedImage hall(ClassicPackFiles pack, Collection<Integer> fathers) {
        final BufferedImage bg = (pack == null) ? null : pack.image(HALL_KEY);
        if (bg == null) return null;
        final BufferedImage img = new BufferedImage(ClassicWoodcut.W, ClassicWoodcut.H,
                                                    BufferedImage.TYPE_INT_RGB);
        final List<Integer> order = new ArrayList<>(fathers);
        final Map<Integer, Rectangle> at = new HashMap<>();
        for (int n : order) {
            final Rectangle r = figureBounds(pack, n);
            if (r != null) at.put(n, r);
        }
        order.removeIf(n -> !at.containsKey(n));
        order.sort(Comparator.<Integer>comparingInt(n -> at.get(n).y + at.get(n).height)
                   .thenComparingInt(n -> n));
        final Graphics2D g = img.createGraphics();
        try {
            g.drawImage(bg, 0, 0, null);
            for (int n : order) {
                final Rectangle r = at.get(n);
                g.drawImage(pack.image(ClassicPackFiles.ssKey(figure(n))), r.x, r.y, null);
            }
        } finally {
            g.dispose();
        }
        return img;
    }

    /**
     * The hall's screen for the layer (class comment): the hall with the
     * fathers before him, his figure dissolving in, his page after it.
     *
     * @param pack The pack.
     * @param before The rows of the fathers already in Congress.
     * @param n The new father's row.
     * @param page His Colonopedia page, or null (then the map follows).
     * @return The screen, or null without the hall or his figure.
     */
    static ClassicWoodcut.Screen screen(ClassicPackFiles pack, Collection<Integer> before,
                                        int n, BufferedImage page) {
        final Rectangle fig = figureBounds(pack, n);
        if (fig == null) return null;
        final List<Integer> old = new ArrayList<>(before);
        old.remove(Integer.valueOf(n));
        final List<Integer> all = new ArrayList<>(old);
        all.add(n);
        final BufferedImage from = hall(pack, old), to = hall(pack, all);
        if (from == null || to == null) return null;
        return new ClassicWoodcut.Screen(HALL, false, "congress_" + n, from, to, page,
            HALL_AFTER_BLACK_MS, DISSOLVE_AFTER_HALL_MS, PAGE_AFTER_BLACK_MS,
            BLACK_AFTER_BOX_MS, fig);
    }

    /**
     * The @FREEDOM box (class comment).
     *
     * @param t The pack's texts, or null (then FreeCol's notice).
     * @param owner Our player (for the nationality).
     * @param ff The father.
     * @param openDelayMs Its delay after it is asked (the turn start's
     *     first box: {@link #FREEDOM_AFTER_TURN_MS}), or 0.
     * @return The request.
     */
    static ClassicAdvisorBox.Request freedom(ClassicText t, Player owner,
                                             FoundingFather ff, double openDelayMs) {
        final ClassicText.Message m = (t == null) ? null : t.message(SECTION);
        final String name = ClassicFathers.name(t, ClassicFathers.index(ff));
        final String nat = nationality(t, ClassicDestinations.nation(owner));
        ClassicAdvisorBox.Builder b = null;
        if (m != null && name != null && nat != null) {
            final Map<String, String> v = new HashMap<>();
            v.put("STRING0", name);
            v.put("STRING1", nat);
            b = ClassicAdvisorBox.fromGameText(SECTION, m, v);
        }
        if (b == null) {
            b = ClassicAdvisorBox.Request.builder(SECTION).freeColText(Messages.message(
                StringTemplate.template(JOINED_MESSAGE).addNamed("%foundingFather%", ff)
                    .add("%description%", ff.getDescriptionKey())));
        }
        if (openDelayMs > 0.0) b.openDelay(openDelayMs);
        return b.stopgap(Messages.message("classic.dialog.messages"), null).build();
    }

    /** NAMES @NATIONALITY's first column ("Holl."), or null. */
    private static String nationality(ClassicText t, int nation) {
        final List<String[]> rows = (t == null) ? null : t.names("NATIONALITY");
        if (rows == null || nation < 0 || nation >= rows.size()
            || rows.get(nation).length == 0) return null;
        final String s = rows.get(nation)[0].trim();
        return (s.isEmpty()) ? null : s;
    }

    /**
     * The fathers of a player not in {@code known}, in NAMES order (a
     * father the original lacks last).
     *
     * @param known The ids seen so far.
     * @param p The player, or null.
     * @param take Whether they count as seen now (added to {@code known}).
     * @return The new fathers, possibly none.
     */
    static List<FoundingFather> joined(Set<String> known, Player p, boolean take) {
        final List<FoundingFather> out = new ArrayList<>();
        if (known == null || p == null) return out;
        for (FoundingFather ff : p.getFoundingFathers()) {
            if (!known.contains(ff.getId())) out.add(ff);
        }
        out.sort(Comparator.comparingInt((FoundingFather ff) -> {
                    final int i = ClassicFathers.index(ff);
                    return (i < 0) ? Integer.MAX_VALUE : i;
                }).thenComparing(FoundingFather::getId));
        if (take) for (FoundingFather ff : out) known.add(ff.getId());
        return out;
    }
}
