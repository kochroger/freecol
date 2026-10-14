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
 * The colony screen's <b>notice slot</b> in the top line (build spec M4.0
 * target 6): a gold notice replaces the colony's words while a goods drag
 * runs («Einladen Erz», V clip 020 #10058) and for 138 frames after a
 * result («24 Erz bewegt nach Handelsschiff», #10116; 1969 ms), then the
 * words come back.  A pure state on an injectable clock (nanoseconds, as
 * {@link ClassicSlide.Clock}); the painter is
 * {@link ClassicColonyChrome#paintNotice}.  The texts are the goods step's
 * (M4.9).  EDT only.
 */
final class ClassicColonyNotice {

    /** A result's lifetime: 138 frames. */
    static final double RESULT_MS = ClassicColonyLayout.frames(ClassicColonyLayout.NOTICE_FRAMES);

    /** The notice, or null. */
    private String text = null;

    /** When it goes ({@code Long.MAX_VALUE} while it is held). */
    private long until = 0L;


    /**
     * A notice while something runs (a drag): it stays until
     * {@link #show} or {@link #clear}.
     *
     * @param notice The notice.
     */
    void hold(String notice) {
        this.text = notice;
        this.until = Long.MAX_VALUE;
    }

    /**
     * A result's notice: it stays {@link #RESULT_MS}.
     *
     * @param notice The notice.
     * @param now The time now (nanoseconds).
     */
    void show(String notice, long now) {
        this.text = notice;
        this.until = now + Math.round(RESULT_MS * 1_000_000.0);
    }

    /** No notice: the colony's words. */
    void clear() {
        this.text = null;
        this.until = 0L;
    }

    /**
     * What the slot shows.
     *
     * @param now The time now (nanoseconds).
     * @return The notice, or null for the colony's words.
     */
    String at(long now) {
        if (this.text == null || now >= this.until) return null;
        return this.text;
    }

    /**
     * @return When the notice goes (nanoseconds; {@code Long.MAX_VALUE}
     *     while held), or 0 without one.
     */
    long until() {
        return (this.text == null) ? 0L : this.until;
    }
}
