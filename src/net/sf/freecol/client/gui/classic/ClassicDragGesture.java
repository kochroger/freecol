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


/**
 * The colony screen's <b>press / drag / release machine</b> (build spec
 * M4.0 target 3; dago {@code 01-interact.md} §3-4, V): the press picks
 * the source (and moves the white cursor there); the first motion while
 * pressed starts a drag (dago: even a motion that does not move the
 * arrow; five presses, a hold time does not fit); the release is then the
 * drop, else a click.  During the drag the arrow is hidden
 * ({@link ClassicPointer}), the ghost follows the pointer, no hover label
 * and no blink.  A pure state machine on device-pixel motion, tested
 * headless; the colony's moves use it from M4.8 on (until then the
 * colony screen acts on the press as before).
 */
final class ClassicDragGesture {

    /** Where a gesture is. */
    enum State { IDLE, PRESSED, DRAGGING }

    /** What a release was. */
    enum Result { NONE, CLICK, DROP }

    private State state = State.IDLE;

    /** Whether the pressed thing can be dragged at all. */
    private boolean draggable = false;

    /** The press in device pixels, and in canvas pixels. */
    private int pressX, pressY, pressVx, pressVy;


    /** @return The state. */
    State state() {
        return this.state;
    }

    /** @return Whether a drag runs (the arrow hidden, the ghost shown). */
    boolean dragging() {
        return this.state == State.DRAGGING;
    }

    /** @return The press's canvas point ({@code {x, y}}). */
    int[] pressPoint() {
        return new int[] { this.pressVx, this.pressVy };
    }

    /**
     * The button goes down.
     *
     * @param vx The canvas x.
     * @param vy The canvas y.
     * @param dx The device x.
     * @param dy The device y.
     * @param canDrag Whether the thing under the press can be dragged (a
     *     colonist, a good); a press elsewhere never becomes a drag.
     */
    void press(int vx, int vy, int dx, int dy, boolean canDrag) {
        this.state = State.PRESSED;
        this.draggable = canDrag;
        this.pressVx = vx;
        this.pressVy = vy;
        this.pressX = dx;
        this.pressY = dy;
    }

    /**
     * The pointer moved with the button down.
     *
     * @param dx The device x.
     * @param dy The device y.
     * @return True if the drag starts with this motion.
     */
    boolean motion(int dx, int dy) {
        if (this.state != State.PRESSED || !this.draggable) return false;
        if (dx == this.pressX && dy == this.pressY) return false;
        this.state = State.DRAGGING;
        return true;
    }

    /**
     * The button goes up.
     *
     * @return {@link Result#DROP} after a drag, {@link Result#CLICK} after a
     *     press without one, {@link Result#NONE} without a press.
     */
    Result release() {
        final State s = this.state;
        this.state = State.IDLE;
        this.draggable = false;
        switch (s) {
        case DRAGGING: return Result.DROP;
        case PRESSED: return Result.CLICK;
        default: return Result.NONE;
        }
    }

    /** The gesture ends without a result (the screen closed, a box came). */
    void cancel() {
        this.state = State.IDLE;
        this.draggable = false;
    }
}
