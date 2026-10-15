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
import java.util.List;


/**
 * How the colony screen <b>moves a colonist</b> (build spec M4.8 targets 1,
 * 2, 7, 9, 10; {@code 02-colony-operations.md} §2, §4; dago
 * {@code 01-interact.md} §3-5; dago-colony2 {@code 01-base.md} §4-6): the
 * drop matrix as a pure function, the times of the boxes that follow a
 * move, which figure a press picks where figures overlap, and who is
 * selected when the screen opens.  Headless; {@code ClassicColonyPanel}
 * acts on it.
 *
 * <p><b>The drop matrix</b> ({@link #resolve}; V for the cases, I for the
 * rule):
 * <table>
 *   <caption>From / to</caption>
 *   <tr><td>building &rarr; building</td><td>the target's work (C1-C3, C12,
 *       C15)</td></tr>
 *   <tr><td>building &rarr; tile</td><td><b>Farmer</b>, whatever the tile
 *       or the expert (C5, C10, dago D3; levi, R2)</td></tr>
 *   <tr><td>tile &rarr; tile</td><td><b>his work kept</b>, even at 0 (C14
 *       #50256; levi, R3)</td></tr>
 *   <tr><td>tile &rarr; building</td><td>the building's work (C4,
 *       C15)</td></tr>
 *   <tr><td>fence &rarr; building</td><td>the building's work, the role's
 *       goods back to the warehouse (C8, C9, C13)</td></tr>
 *   <tr><td>fence &rarr; tile</td><td>Farmer (I: not seen; as from a
 *       building, the work the original gives a newcomer on a tile)</td></tr>
 *   <tr><td>building or fence &rarr; an ocean tile (with docks)</td><td>
 *       <b>Fischer</b>, not Farmer (I: the Farmer rule's evidence is land
 *       only, no clip shows a drop on water)</td></tr>
 *   <tr><td>anything &rarr; fence</td><td>J1 (C7, C11)</td></tr>
 *   <tr><td>onto a full building</td><td>@MORETHANTHREE, nothing moves
 *       (V dago-colony2 #10945)</td></tr>
 *   <tr><td>onto a school he may not teach in, a full school, water
 *       without docks</td><td>the original's box (@NOTEACHER, @TEACHCONVERT,
 *       @NEEDCOLLEGE, @NEEDUNIVERSITY, @SCHOOL1, @COLLEGE2, @UNIV3,
 *       @NODOCKS; GAME.TXT, the triggers I), nothing moves, nothing is
 *       asked of the server</td></tr>
 *   <tr><td>onto his own place, the centre, an occupied tile, a
 *       placeholder, nowhere</td><td>nothing (V dago P1: a drag dropped
 *       in place did nothing; clip 042 #4150, #4839: two drops on a worked
 *       tile, no box, he stays selected; I for the rest)</td></tr>
 * </table>
 * Without the levi rule ({@code model.option.originalDropJobs}) a move
 * onto a tile takes FreeCol's choice of work ({@link Action#TILE_FREECOL}).
 */
final class ClassicColonyMoves {

    /** Where a dragged colonist comes from or goes. */
    enum Place {
        /** A building with workplaces. */
        BUILDING,
        /** One of the eight work tiles. */
        TILE,
        /** The fence picture «(Außerhalb der Kolonie)» (slot X). */
        FENCE,
        /** Anything else: a placeholder, the harbour, the centre, the band. */
        NONE
    }

    /** What a drop does. */
    enum Action {
        /** Nothing moves. */
        NOTHING,
        /** Into the building, at its work. */
        BUILDING_JOB,
        /** Onto the tile as a Farmer (R2). */
        TILE_FARMER,
        /** Onto the tile keeping his work (R3). */
        TILE_KEEP_JOB,
        /** Onto the tile at FreeCol's choice (rules without R2/R3). */
        TILE_FREECOL,
        /** J1, the role menu. */
        ROLE_MENU,
        /** @MORETHANTHREE: the building is full. */
        FULL
    }

    /** J1 after the drop: +5 frames (V #6310 → #6315; #77 +4; dago +5, +6, +7). */
    static final int J1_FRAMES = 5;

    /**
     * J2 after the release: +5 frames (I: dago P2, the menu ≈5 frames after
     * the release; clip 042: release +5 and +8/+9 for a worker, +4…+6 for
     * a unit at the fence, #15426, #15901).  A unit at the fence opens it
     * the same way, from the release (clip 042; clip 020's +44 from the
     * press, #46063 → #46108, was a long hold, I).
     */
    static final int J2_FRAMES = 5;

    /**
     * The target's hover label after a drop or a click's move, the pointer
     * still (clip 042): +13 frames on a tile (#2863 → #2876), +16 in a
     * building (#4700 → #4716, #16934 → #16951), +3 after a refused drop
     * (#4341 → #4344, #5070 → #5073).
     */
    static final int HOVER_TILE_FRAMES = 13, HOVER_BUILDING_FRAMES = 16,
        HOVER_REFUSED_FRAMES = 3;

    /**
     * A click on an empty spot of the fence with a colonist selected: his
     * role menu (J1, or the roles of a unit at the fence) +3 frames after
     * the release (V clip 043: #3326 ≈ +3, #7048 +2, #8471 +3; clip 042
     * #15119 ≈ +2; the release from the keylog, I ±1).
     */
    static final int FENCE_CLICK_FRAMES = 3;

    /** After «Ja» on @ABANDON: the unit on the fence +3 frames (V clip 020 #35574 → #35577). */
    static final int ABANDON_FENCE_FRAMES = 3;

    /** The «(Weitere)» page after the pick: +3 frames (V #7188 → #7191; dago-colony2 +3). */
    static final int MORE_FRAMES = 3;

    /** @ABANDON after J1 closes: +8 frames (V #35371 → #35379). */
    static final int ABANDON_FRAMES = 8;

    /**
     * @MORETHANTHREE after the drop: +32 frames (V dago-colony2 #10913 →
     * #10945); the school's and the docks' refusals (@NOTEACHER,
     * @NEEDCOLLEGE, @NEEDUNIVERSITY, @TEACHCONVERT, @SCHOOL1, @COLLEGE2,
     * @UNIV3, @NODOCKS) the same (I: not seen).
     */
    static final int FULL_FRAMES = 32;

    /**
     * After «Ja»: the map without the colony +31 frames (V #35574 →
     * #35605/#35606, the unit on the fence at +3 in between).
     */
    static final int ABANDON_MAP_FRAMES = 31;

    /** The ghost's sprite at the hotspot − (1, 8) (V dago, 3 drops). */
    static final int GHOST_DX = -1, GHOST_DY = -8;


    private ClassicColonyMoves() {}   // static helpers only


    /**
     * The drop matrix (class comment).
     *
     * @param from Where the colonist was picked.
     * @param to Where he is dropped.
     * @param samePlace Whether the drop is on his own place (his
     *     building, his tile).
     * @param blocked Whether the target cannot take him: a full building,
     *     an occupied tile.
     * @param original Whether the levi rule R2/R3 holds.
     * @return What the drop does.
     */
    static Action resolve(Place from, Place to, boolean samePlace, boolean blocked,
                          boolean original) {
        if (from == null || to == null || from == Place.NONE || to == Place.NONE
            || samePlace) return Action.NOTHING;
        switch (to) {
        case FENCE:
            return (from == Place.FENCE) ? Action.NOTHING : Action.ROLE_MENU;
        case BUILDING:
            return (blocked) ? Action.FULL : Action.BUILDING_JOB;
        case TILE:
            if (blocked) return Action.NOTHING;
            if (!original) return Action.TILE_FREECOL;
            return (from == Place.TILE) ? Action.TILE_KEEP_JOB : Action.TILE_FARMER;
        default:
            return Action.NOTHING;
        }
    }

    /**
     * Which of overlapping figures a press picks (target 10): the first
     * drawn, the one underneath, as the buildings' hit test (V dago
     * {@code 01-interact.md} §2.2: in the one overlap of two buildings the
     * one drawn first wins); for the figures no clip shows it (I).
     *
     * @param bounds The figures' rectangles in their draw order.
     * @param x The press's x.
     * @param y Its y.
     * @return The index, or -1.
     */
    static int pick(List<Rectangle> bounds, int x, int y) {
        for (int i = 0; i < bounds.size(); i++) {
            if (bounds.get(i).contains(x, y)) return i;
        }
        return -1;
    }

    /**
     * Who is selected when the screen opens (target 9; V clip 020, 23 of
     * 23 openings select a colonist): at a founding the founder (#14817,
     * #123003, clip008 #4032); otherwise the colonist selected last in this
     * colony, while he is still in it (#19585 → #19980); otherwise the
     * first figure of the row (I).
     *
     * @param <T> The unit's type.
     * @param row The figure row's units, in its order.
     * @param founder The founder at a founding, or null.
     * @param last The colonist selected last here, or null.
     * @return The unit, or null for an empty row.
     */
    static <T> T openingSelection(List<T> row, T founder, T last) {
        if (founder != null && row.contains(founder)) return founder;
        if (last != null && row.contains(last)) return last;
        return (row.isEmpty()) ? null : row.get(0);
    }

    /**
     * A box's delay in milliseconds from a number of original frames.
     *
     * @param frames The frames.
     * @return The ms.
     */
    static double ms(int frames) {
        return ClassicColonyLayout.frames(frames);
    }
}
