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

import java.util.ArrayList;
import java.util.List;

import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.FoundingFather;
import net.sf.freecol.common.model.HistoryEvent;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Turn;


/**
 * The original's founding father choice (build spec D8a, clip008
 * 01-fathers): the list box GAME.TXT {@code @WHICHFREEDOM}, and when it
 * comes.
 *
 * <ul>
 *   <li><b>The box (V, 0 px on #12765, #13304, #48314, #49787 and
 *       #50129).</b>  {@code @WHICHFREEDOM} at its {@code @width} 230,
 *       centred, no portrait: (42,59) 236x82 for three prompt lines and
 *       five rows.  One row per offered father, in FreeCol's order (one
 *       per type, the original's), each "<i>name</i> (<i>type</i>
 *       Berater)": NAMES {@code @FATHERS}, NAMES {@code @FOUNDING} (the
 *       hyphen keeps its space: "Handels- Berater"), LABELS {@code @MISC}
 *       102.  The rows at box + 13, the gold footer "(F1 für Hilfe)"
 *       (LABELS {@code @MISC} 188), the bar on row 1.</li>
 *   <li><b>Keys (V for the bar and F1; I for Enter and Escape).</b>  The
 *       arrows move the bar, Enter takes the father under it.  F1 opens
 *       the Colonopedia page of the father under the bar
 *       ({@link ClassicPedia#fatherPage}); any key closes it, and the box
 *       comes back with the bar on row 1 (3 of 3 times).  Escape does
 *       nothing, and neither does a click beside the box: the box has no
 *       cancel row, and a choice is due.</li>
 *   <li><b>When.</b>  At our turn start, after the price messages of that
 *       turn's notices and before the others (choice 1: @COTTON came
 *       after it), and before the year flips.  Not after independence
 *       (Roger's rule).  Not in the turn a father joined: the original
 *       offers the next choice one turn later (V, one case); FreeCol
 *       offers it at once, and offers it again at the next turn start
 *       while none is chosen.</li>
 * </ul>
 *
 * <p>The father's row in NAMES {@code @FATHERS} is also his
 * {@code @FATHERn} page and his {@code CC-nn} figure: {@link #IDS}, an
 * explicit table (V for n = 2, 3, 8, 13).
 */
final class ClassicFathers {

    /** GAME.TXT's father choice. */
    static final String SECTION = "WHICHFREEDOM";

    /** LABELS.TXT {@code @MISC}: "Berater", "(F1 für Hilfe)". */
    static final int MISC_ADVISOR = 102, MISC_F1_HELP = 188;

    /** The F1 page comes this long after the box goes (clip008: 0.13-0.16 s). */
    static final double PAGE_CHAIN_MS = 142.0;

    /** The box comes back this long after the page goes (clip008: 0.257-0.271 s). */
    static final double REOPEN_CHAIN_MS = 264.0;

    /**
     * FreeCol's fathers in the order of NAMES.TXT {@code @FATHERS}, which is
     * also the order of PEDIA.TXT's {@code @FATHERn} and of the congress
     * figures {@code CC-nn} (clip008 01-fathers section 6.1).
     */
    static final List<String> IDS = List.of(
        "model.foundingFather.adamSmith",
        "model.foundingFather.jacobFugger",
        "model.foundingFather.peterMinuit",
        "model.foundingFather.peterStuyvesant",
        "model.foundingFather.janDeWitt",
        "model.foundingFather.ferdinandMagellan",
        "model.foundingFather.franciscoDeCoronado",
        "model.foundingFather.hernandoDeSoto",
        "model.foundingFather.henryHudson",
        "model.foundingFather.laSalle",
        "model.foundingFather.hernanCortes",
        "model.foundingFather.georgeWashington",
        "model.foundingFather.paulRevere",
        "model.foundingFather.francisDrake",
        "model.foundingFather.johnPaulJones",
        "model.foundingFather.thomasJefferson",
        "model.foundingFather.pocahontas",
        "model.foundingFather.thomasPaine",
        "model.foundingFather.simonBolivar",
        "model.foundingFather.benjaminFranklin",
        "model.foundingFather.williamBrewster",
        "model.foundingFather.williamPenn",
        "model.foundingFather.fatherJeanDeBrebeuf",
        "model.foundingFather.juanDeSepulveda",
        "model.foundingFather.bartolomeDeLasCasas");


    private ClassicFathers() {}   // static helpers only

    /**
     * A father's row in NAMES {@code @FATHERS}.
     *
     * @param ff The father, or null.
     * @return The row (0-24), or -1 for a father the original does not have.
     */
    static int index(FoundingFather ff) {
        return (ff == null) ? -1 : IDS.indexOf(ff.getId());
    }

    /**
     * A father's name: NAMES {@code @FATHERS}, the first column ("Hernando
     * Cortez", not the Colonopedia's "Hernan Cortes").
     *
     * @param t The texts.
     * @param n The row.
     * @return The name, or null.
     */
    static String name(ClassicText t, int n) {
        final List<String[]> rows = (t == null) ? null : t.names("FATHERS");
        if (rows == null || n < 0 || n >= rows.size()) return null;
        return rows.get(n)[0];
    }

    /**
     * A father's type in NAMES {@code @FOUNDING} order (the second column
     * of {@code @FATHERS}: 0 trade ... 4 religion).
     *
     * @param t The texts.
     * @param n The row.
     * @return The type, or -1.
     */
    static int type(ClassicText t, int n) {
        final List<String[]> rows = (t == null) ? null : t.names("FATHERS");
        if (rows == null || n < 0 || n >= rows.size() || rows.get(n).length < 2) {
            return -1;
        }
        try {
            return Integer.parseInt(rows.get(n)[1].trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * A row of the box: "Peter Minuit (Handels- Berater)".
     *
     * @param t The texts.
     * @param ff The father.
     * @return The row, or null when the texts lack him.
     */
    static String row(ClassicText t, FoundingFather ff) {
        final int n = index(ff);
        final String name = name(t, n);
        final int type = type(t, n);
        final List<String[]> types = t.names("FOUNDING");
        final String advisor = t.misc(MISC_ADVISOR);
        if (name == null || type < 0 || type >= types.size() || advisor == null) {
            return null;
        }
        return name + " (" + types.get(type)[0] + " " + advisor + ")";
    }

    /**
     * The father box (class comment).
     *
     * @param t The pack's texts, or null (then FreeCol's words).
     * @param ffs The offered fathers.
     * @param help Told the barred row on F1.
     * @return The request.
     */
    static ClassicAdvisorBox.Request request(ClassicText t, List<FoundingFather> ffs,
                                             ClassicAdvisorBox.Help help) {
        return request(t, ffs, help, -1.0);
    }

    /**
     * The father box, asked again after its F1 page.
     *
     * @param t The pack's texts, or null.
     * @param ffs The offered fathers.
     * @param help Told the barred row on F1.
     * @param chainMs The least time after the previous box (the page), or
     *     negative for the usual.
     * @return The request.
     */
    static ClassicAdvisorBox.Request request(ClassicText t, List<FoundingFather> ffs,
                                             ClassicAdvisorBox.Help help,
                                             double chainMs) {
        final ClassicText.Message m = (t == null) ? null : t.message(SECTION);
        final List<String> rows = new ArrayList<>(ffs.size());
        boolean original = m != null && m.width != null;
        for (FoundingFather ff : ffs) {
            final String r = (original) ? row(t, ff) : null;
            if (r == null) original = false;
            rows.add(r);
        }
        final String footer = (t == null) ? null : t.misc(MISC_F1_HELP);
        final ClassicAdvisorBox.Builder b;
        if (original && footer != null) {
            b = ClassicAdvisorBox.fromGameText(SECTION, m, null).rows(rows)
                .footer(footer);
        } else {
            // Without the pack's words: FreeCol's question and names.
            final List<String> fc = new ArrayList<>(ffs.size());
            for (FoundingFather ff : ffs) {
                fc.add(ClassicAdvisorBox.literal(Messages.getName(ff) + " ("
                    + Messages.message(ff.getTypeKey()) + ")"));
            }
            b = ClassicAdvisorBox.Request.builder(SECTION)
                .freeColText(Messages.message("chooseFoundingFatherDialog.title"))
                .rows(fc);
        }
        return b.defaultRow(0).noEscape().outsideCancels(false)
            .rowIndent(ClassicMenuBox.LIST_INDENT).help(help).chain(chainMs)
            .stopgap(Messages.message("chooseFoundingFatherDialog.title"), null)
            .build();
    }

    /**
     * Whether a father offer is shown: not after independence (Roger's
     * rule), and not in the turn a father joined (the original offers the
     * next one a turn later; FreeCol offers it again then).  Asked when
     * the box is due, after the turn's own updates (the father joins in
     * the player update the server sends with the offer).
     *
     * @param player Our player.
     * @param turn The game's turn.
     * @return Null to show it, else why not (for the recorder).
     */
    static String withheld(Player player, Turn turn) {
        if (player == null) return "no player";
        final Player.PlayerType pt = player.getPlayerType();
        if (pt == Player.PlayerType.REBEL || pt == Player.PlayerType.INDEPENDENT) {
            return "after independence";
        }
        if (turn != null) {
            for (HistoryEvent h : player.getHistory()) {
                if (h.getEventType() == HistoryEvent.HistoryEventType.FOUNDING_FATHER
                    && h.getTurn() != null
                    && h.getTurn().getNumber() == turn.getNumber()) {
                    return "a father joined this turn";
                }
            }
        }
        return null;
    }
}
