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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * The schedule of the original <b>opening</b> (the sea-chart credits and
 * the title build-up, {@link ClassicIntro}), parsed from the original's own
 * data files {@code OPENING.TXT} and {@code PATH.DAT}, which the converter
 * copies byte for byte into the git-ignored pack ({@code text/}).
 * Swing-free and immutable, so it is unit-tested with synthetic files.
 *
 * <p><b>Why the original files and not a table here:</b> they are the
 * source of truth OPENING.EXE itself reads (its strings name OPENING,
 * CREDITS and PATH.DAT), and they are copyrighted, so they stay in the
 * pack.  The credits are not text at all: every name is baked into the
 * scroll sprites OPENCRD1-3.SS, so {@code @CREDITS} only says which sprite
 * shows from which chart frame to which.  Nothing of either file is
 * transcribed into tracked code.
 *
 * <p>Format (as OPENING.EXE reads it; verified to 0 px against the
 * captures {@code screenshots/intro-original/opening_104..165} with the
 * painter, see the README "Opening" section):
 * <ul>
 *   <li>Read as ISO-8859-1; everything after ';' is a comment; blank lines
 *       are skipped; a line starting with '@' opens a section.</li>
 *   <li>{@code @CREDITS}: rows {@code start, end, series, sprite} -- chart
 *       frames (both ends inclusive), series 0/1/2 = OPENCRD1/2/3, sprite
 *       1-based.</li>
 *   <li>{@code @OPENING}: rows {@code series, startFrame, repeats, baseX},
 *       series 0-9 = {@link #SERIES_STEMS}; series -1 marks the END frame;
 *       the row {@code 0,0,0,0} ends the table.</li>
 *   <li>Other sections ({@code @MESSAGES}) are ignored.</li>
 *   <li>{@code PATH.DAT}: one ship position {@code x, y} per chart frame
 *       (chart x, screen y).</li>
 * </ul>
 * Malformed input throws {@link IllegalArgumentException}; the caller logs
 * it once and shows the emblem and then the title.
 */
final class ClassicOpeningScript {

    /**
     * The sprite series of the {@code @OPENING} table, by series number:
     * the order of the names in OPENING.EXE's string table, which matches
     * the comments of the original file.
     */
    static final String[] SERIES_STEMS = {
        "OPENWND1", "OPENSUN", "OPENMON1", "OPENWND2", "OPENMON2",
        "OPENMON3", "OPENFISH", "OPENGUY", "OPENLOGO", "OPENBONK"
    };

    /** The credit scroll series 0/1/2 (OPENING.EXE formats "OPENCRD%d"). */
    static final String[] CREDIT_STEMS = { "OPENCRD1", "OPENCRD2", "OPENCRD3" };

    /** The series number of the sun (see {@link ClassicIntro#sunFrame}). */
    static final int SERIES_SUN = 1;


    /** One credit scroll: a sprite shown for a range of chart frames. */
    static final class Credit {

        /** First and last chart frame it is visible (both inclusive). */
        final int start, end;
        /** 0..2: OPENCRD1..3. */
        final int series;
        /** 1-based sprite number in that series (as in the file). */
        final int sprite;

        Credit(int start, int end, int series, int sprite) {
            this.start = start;
            this.end = end;
            this.series = series;
            this.sprite = sprite;
        }
    }

    /** One animation entry of the {@code @OPENING} table. */
    static final class Anim {

        /** 0..9, see {@link #SERIES_STEMS}. */
        final int series;
        /** The chart frame its sprite frame 0 shows. */
        final int start;
        /** 0 = play once and hold the last frame; R &gt; 0 = R+1 plays, then gone. */
        final int repeats;
        /** Chart x of the sprite anchors' origin. */
        final int baseX;

        Anim(int series, int start, int repeats, int baseX) {
            this.series = series;
            this.start = start;
            this.repeats = repeats;
            this.baseX = baseX;
        }
    }


    /** The credit scrolls in file order. */
    final List<Credit> credits;

    /** The animation entries in table order (the END row excluded). */
    final List<Anim> anims;

    /** The ship path, chart x and screen y per frame. */
    private final int[] pathX, pathY;

    /** The chart frame at which the original cuts to the title menu. */
    final int endFrame;


    private ClassicOpeningScript(List<Credit> credits, List<Anim> anims,
                                 int[] pathX, int[] pathY, int endFrame) {
        this.credits = Collections.unmodifiableList(credits);
        this.anims = Collections.unmodifiableList(anims);
        this.pathX = pathX;
        this.pathY = pathY;
        this.endFrame = endFrame;
    }

    /** @return The number of ship path points. */
    int pathLength() {
        return this.pathX.length;
    }

    /** @return The chart x of path point {@code i} (clamped to the path). */
    int pathX(int i) {
        return this.pathX[Math.max(0, Math.min(this.pathX.length - 1, i))];
    }

    /** @return The screen y of path point {@code i} (clamped to the path). */
    int pathY(int i) {
        return this.pathY[Math.max(0, Math.min(this.pathY.length - 1, i))];
    }

    /**
     * Read both files from the pack's {@code text/} directory.
     *
     * @param p The pack.
     * @return The script.
     * @exception IOException if a file is missing or unreadable.
     * @exception IllegalArgumentException if a file is malformed.
     */
    static ClassicOpeningScript load(ClassicPackFiles p) throws IOException {
        final File o = p.textFile("OPENING.TXT"), d = p.textFile("PATH.DAT");
        if (o == null || d == null) throw new IOException("OPENING.TXT or PATH.DAT missing");
        return parse(Files.readAllLines(o.toPath(), StandardCharsets.ISO_8859_1),
                     Files.readAllLines(d.toPath(), StandardCharsets.ISO_8859_1));
    }

    /**
     * Parse the two files.
     *
     * @param openingTxt The lines of {@code OPENING.TXT}.
     * @param pathDat The lines of {@code PATH.DAT}.
     * @return The script.
     * @exception IllegalArgumentException if either is malformed, has no END
     *     row or no path.
     */
    static ClassicOpeningScript parse(List<String> openingTxt, List<String> pathDat) {
        final List<Credit> credits = new ArrayList<>();
        final List<Anim> anims = new ArrayList<>();
        int end = -1;
        String section = "";
        boolean openingClosed = false;
        int lineNo = 0;
        for (String raw : openingTxt) {
            lineNo++;
            final String l = stripComment(raw);
            if (l.isEmpty()) continue;
            if (l.startsWith("@")) {
                section = l.toUpperCase(java.util.Locale.ROOT);
                continue;
            }
            if ("@CREDITS".equals(section)) {
                final int[] v = ints(l, 4, lineNo);
                if (v[2] < 0 || v[2] >= CREDIT_STEMS.length || v[3] < 1
                    || v[0] < 0 || v[1] < v[0]) {
                    throw new IllegalArgumentException("OPENING.TXT:" + lineNo
                        + ": bad credit row '" + l + "'");
                }
                credits.add(new Credit(v[0], v[1], v[2], v[3]));
            } else if ("@OPENING".equals(section) && !openingClosed) {
                final int[] v = ints(l, 4, lineNo);
                if (v[0] == 0 && v[1] == 0 && v[2] == 0 && v[3] == 0) {
                    openingClosed = true;        // the table's terminator
                } else if (v[0] == -1) {
                    end = v[1];
                } else if (v[0] < 0 || v[0] >= SERIES_STEMS.length
                           || v[1] < 0 || v[2] < 0) {
                    throw new IllegalArgumentException("OPENING.TXT:" + lineNo
                        + ": bad animation row '" + l + "'");
                } else {
                    anims.add(new Anim(v[0], v[1], v[2], v[3]));
                }
            }
        }
        if (end <= 0) throw new IllegalArgumentException("OPENING.TXT: no END row");
        final List<int[]> pts = new ArrayList<>();
        lineNo = 0;
        for (String raw : pathDat) {
            lineNo++;
            final String l = stripComment(raw);
            if (l.isEmpty()) continue;
            pts.add(ints(l, 2, lineNo));
        }
        if (pts.isEmpty()) throw new IllegalArgumentException("PATH.DAT: no points");
        final int[] px = new int[pts.size()], py = new int[pts.size()];
        for (int i = 0; i < px.length; i++) {
            px[i] = pts.get(i)[0];
            py[i] = pts.get(i)[1];
        }
        return new ClassicOpeningScript(credits, anims, px, py, end);
    }

    /** The line without its ';' comment, trimmed. */
    private static String stripComment(String raw) {
        if (raw == null) return "";
        final int c = raw.indexOf(';');
        return ((c >= 0) ? raw.substring(0, c) : raw).trim();
    }

    /** Exactly {@code n} comma-separated integers. */
    private static int[] ints(String l, int n, int lineNo) {
        final String[] p = l.split(",");
        if (p.length != n) {
            throw new IllegalArgumentException("line " + lineNo + ": expected "
                + n + " numbers in '" + l + "'");
        }
        final int[] v = new int[n];
        for (int i = 0; i < n; i++) {
            try {
                v[i] = Integer.parseInt(p[i].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("line " + lineNo
                    + ": not a number in '" + l + "'", e);
            }
        }
        return v;
    }
}
