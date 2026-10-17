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
 *       #50256; levi, R3; V clip 053 the lumber, the furs, the silver
 *       kept), but the food work follows the square ({@link #keptJob}): a
 *       Fischer onto land is a Farmer (V clip 053), a Farmer onto water a
 *       Fischer (I)</td></tr>
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
 *       @NODOCKS; GAME.TXT, the school's triggers I; @NODOCKS V clip 053,
 *       the drag #50365 - #50461 and the click #31382, both refused with
 *       the admiral), nothing moves, nothing is asked of the server</td></tr>
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
     * The hover label the press took, back after a click that selected a
     * colonist, the pointer still (part Z, Z-ITEMS Z1.2, clip 053's
     * comparison D3; ours brought it back only when the pointer moved): a
     * tile worker's tile label +5 to +7 frames after the release (V #8241,
     * #8444, #8923, #10229, #11320, #45998: 7 taken), a unit at the fence
     * +9 (V #36204, «(Außerhalb der Kolonie)»); a building worker's 7 (I:
     * not seen).
     */
    static final int HOVER_SELECT_FRAMES = 7, HOVER_SELECT_FENCE_FRAMES = 9;

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
     * #10945); the school's refusals (@NOTEACHER, @NEEDCOLLEGE,
     * @NEEDUNIVERSITY, @TEACHCONVERT, @SCHOOL1, @COLLEGE2, @UNIV3) the same
     * (I: not seen); @NODOCKS has its own time ({@link #NODOCKS_FRAMES}).
     */
    static final int FULL_FRAMES = 32;

    /**
     * @NODOCKS (water without docks) on screen +7 frames after the drop or
     * the click's release, the admiral's palette ahead of it where it goes
     * in first (Z-ITEMS Z1.3, clip 053's comparison D4): V the drag N -&gt;
     * S of Test, the drop drawn #50460, the box #50467 (+7, no palette
     * change: MSS0's was in from the box before); the click on the empty
     * water NE, the keylog's release #31388, MSS0's palette #31391, the box
     * #31393 (+5 from the logged release, I +-2: the keylog's alignment).
     * Ours had the 32 frames of @MORETHANTHREE.
     */
    static final int NODOCKS_FRAMES = 7;

    /**
     * After «Ja»: the map without the colony +31 frames (V #35574 →
     * #35605/#35606, the unit on the fence at +3 in between).
     */
    static final int ABANDON_MAP_FRAMES = 31;

    /**
     * The exit (build spec M4.11 target 3, part W): the map replaces the
     * screen 30 frames after the release on «Abb» or of Escape, in one
     * frame (a torn frame at most, clip 020 #11159); meanwhile the screen
     * stays as it was, its blink frozen from the press, no label, while
     * the arrow still moves (V clip 042 #17664 - #17688, clip 020 #74315 -
     * #74327).  V: clip 042 #17658 -&gt; #17689 (+31), clip 043 #8090 -&gt;
     * #8119 (+29), #8690 -&gt; #8722 (+32; the releases from the keylogs,
     * a few frames' error), the abandon's box close -&gt; map +31 (V
     * #35574 -&gt; #35605, frame-exact).  The original's wait varies: in
     * clip 020's 15 exits the blink stops at the press and the map comes
     * 7 to 56 frames later (I: its own work; the build spec's "1-2 frames"
     * is the replacement itself, not the wait).
     */
    static final int EXIT_MAP_FRAMES = 30;

    /** The ghost's sprite at the hotspot − (1, 8) (V dago, 3 drops). */
    static final int GHOST_DX = -1, GHOST_DY = -8;

    /**
     * A press held without motion on the colonist already selected lifts
     * him as a drag's start after 10 frames ({@link #holdFrames}; part Z,
     * Z-ITEMS Z1.5, clip 053's comparison D1 and its verifier's item 3,
     * the press lengths from the keylog): presses of 4-9 frames opened the
     * job menu (#4829, #22314, #29807, #30783, #45206 and 18 more), every
     * isolated press of 12 frames or more lifted him (#6050, #6171, #7529,
     * #7601, #7688, #8288, #59615, #66083, #26195 ...), at 10 and 11
     * frames both happened (#10318 and #25539 lifted, #66212 the menu;
     * #43706 and #7447 lifted at 11): the threshold between 9 and 11, 10
     * taken (I).  The original shows the copy 7-9 frames after the press
     * while still deciding; ours shows it at the threshold.
     */
    static final int HOLD_LIFT_FRAMES = 10;

    /**
     * A press held without motion on a colonist not yet selected lifts him
     * after 16 frames ({@link #holdFrames}; I, between 15 and 17: V clip
     * 053, the copy shown +12 to +15 after the press when it was held 16 to
     * 21 frames, #48071, #66029, #43681, #24520 and every long drag from an
     * unselected worker, but presses held 15 or 16 frames that only
     * selected him, #8219, #8421, #8900, #10207, #11297, the gate unit
     * #36179; the selected one's copy shows about 3 frames before his
     * threshold as well).
     */
    static final int HOLD_LIFT_UNSELECTED_FRAMES = 16;

    /**
     * No lift by holding within this many frames after the click that
     * selected him: the second tap of a double tap opens the job menu
     * even when held 10-12 frames (V clip 053, 11 of 11: #6958, #9717,
     * #10656, #11037, #12121, #13234, #14527, #16026, #17194, #17891,
     * #21783; the taps 15-17 frames apart; a press 69 frames after the
     * selecting click and held 16 lifted him, #8219 / #8288); 20 is the
     * comparison's guess (I).
     */
    static final int DOUBLE_TAP_FRAMES = 20;

    /**
     * How long a press on a colonist must be held without motion to lift
     * him (part Z, Z-ITEMS Z1.5, clip 053's comparison D1 with its
     * verifier's corrections; the original's behaviour, Roger's rule
     * "überall Original"; the motion still starts a drag at once, dago):
     * on the one already selected {@link #HOLD_LIFT_FRAMES}, a shorter
     * press is a click (his job menu); on another {@link
     * #HOLD_LIFT_UNSELECTED_FRAMES}, a shorter press only selects him.
     * Never on the selected one when the press is the second tap of a
     * double tap ({@link #DOUBLE_TAP_FRAMES}), nor on a tile worker whose
     * tile the white cursor was not on before the press (V the first press
     * after a drop from the fence or a building onto a field: his cell
     * green, the cursor on his figure in the band, one press held 15
     * frames opened the menu, #18900 / #18970, #21490 / #21768).  V for
     * tile workers and units at the fence (#36231 lifted, #36405 the
     * menu, #36179 a first press only selecting); building workers the
     * same (I: not seen).
     *
     * @param selected Whether he was the selected colonist before the press.
     * @param cursorThere Whether the white cursor was on his tile before
     *     the press (true for a colonist in a building or at the fence).
     * @param sinceSelect Frames since the click that selected him
     *     ({@link Integer#MAX_VALUE} for none).
     * @return The frames, or -1 for no lift by holding.
     */
    static int holdFrames(boolean selected, boolean cursorThere, int sinceSelect) {
        if (!selected) return HOLD_LIFT_UNSELECTED_FRAMES;
        if (!cursorThere || sinceSelect < DOUBLE_TAP_FRAMES) return -1;
        return HOLD_LIFT_FRAMES;
    }


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
     * The work a colonist does after a move from tile to tile (R3,
     * {@link Action#TILE_KEEP_JOB}: the drag, and the click on an empty
     * tile): his own, but the food work follows the square, food being one
     * good in the original («Nahrungsmittel», the same corn on land and
     * water).  A Fischer moved onto land is a Farmer there (V clip 053, the
     * verifier's item 16: the convert working SW, the ocean, with food
     * #3500, dragged onto NE, the Nadel-Wald, #3610 - #3763: corn and no
     * sign #3920, his menu «Konvertierte Indianer(Farmer)» #9740 before
     * any pick; the trapper, Fischer 2/2 on NW, the ocean, #25812, dragged
     * onto NE #25936 - #26039: corn, «(Farmer)» 2/2 #26045, #26274); a
     * Farmer moved onto water a Fischer (I: by symmetry, as a colonist
     * from a building becomes one there; no clip shows it, named for
     * Roger).  Every other work is kept (V clip 053: the lumber, the furs,
     * the silver), also on water where it makes nothing (I: no clip shows
     * a land work dragged onto water).  Ours kept the fish on land, which
     * since part Y3's rule made nothing there under the red sign (the
     * review of part Y, fidelity; Z-ITEMS Z1 item 4).
     *
     * @param job His work's NAMES {@code @JOB} index (0 Farmer ... 8
     *     Fischer), or -1.
     * @param water Whether the target tile is water.
     * @return The index of the work he does there.
     */
    static int keptJob(int job, boolean water) {
        if (job == 0 && water) return ClassicColonyLayout.JOB_FISHERMAN;
        if (job == ClassicColonyLayout.JOB_FISHERMAN && !water) return 0;
        return job;
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
