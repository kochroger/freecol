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

import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Locale;

import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.StringTemplate;


/**
 * A colony's founding as in the original (master plan D4; clip008
 * {@code 01-colony.md} section 1, frames #941-#4068).  Headless: what
 * {@link ClassicGUI} asks and when.
 *
 * <ul>
 *   <li><b>The site warning.</b>  FreeCol's warnings
 *   ({@code Tile.getBuildColonyWarnings}, a label of {@code warning.*}
 *   keys) become the original's one: a land-locked site is GAME.TXT's
 *   @NOPORT with the frontiersman (V c008_0018, frame #1002: box
 *   (62,112,196,64)), the bar on its first row "Oh, daran hatte ich
 *   nicht gedacht." (V: the bar there, and that row closed it), which,
 *   like Escape, founds nothing; only its second row founds.  FreeCol's
 *   other warnings (little food, little lumber or ore, land owned by us,
 *   by Europeans or by natives) have no words in the original and are
 *   answered "found" without a box (I).  A refusal costs nothing: FreeCol
 *   asks before the server is told.</li>
 *   <li><b>The name.</b>  GAME.TXT's @COLONY with the colonist (MSS5)
 *   and the name field (V #2789: box (71,113,178,37); the section has no
 *   {@code @width}, the box is the field's: 29 + 144 + 5).  The default
 *   is COLONY.TXT's first name of our nation that no settlement in the
 *   game has ("New Amsterdam" for the Dutch); FreeCol's suggestion only
 *   without the file.  Enter founds with the field's text, Escape founds
 *   nothing (no move used).</li>
 *   <li><b>The timeline.</b>  The prompt closes (#3340); the colony on
 *   the map {@link #SPRITE_AFTER_PROMPT_MS} later (#3369); the first
 *   colony's woodcut 2 black {@link #BLACK_AFTER_PROMPT_MS} after the
 *   close (#3374), never sooner than {@link ClassicWoodcut#BLACK_AFTER_COLONY_MS}
 *   after the colony's paint; the map {@link ClassicWoodcut#FOLLOW_COLONY_MS}
 *   (#4009 -&gt; #4032), then the colony screen, then @TUTORIAL4
 *   ({@link ClassicTips#COLONY_TIP_MS}, #4068).</li>
 * </ul>
 */
final class ClassicFounding {

    /** GAME.TXT's site warning without sea access. */
    static final String NOPORT_SECTION = "NOPORT";

    /** GAME.TXT's naming box of a new colony. */
    static final String COLONY_SECTION = "COLONY";

    /** @NOPORT's row that founds the colony ("Und das ist genau das, was ich vorhatte."). */
    static final int NOPORT_FOUND_ROW = 1;

    /** FreeCol's warning of a land-locked site. */
    static final String LAND_LOCKED = "warning.landLocked";

    /** The prefix of FreeCol's site warnings. */
    static final String WARNING = "warning.";

    /**
     * @COLONY's text width: the box is 178 wide (V #2789), the name
     * field's 29 + 144 + 5; a box is its text width + 6.
     */
    static final int COLONY_WIDTH = 172;

    /** From the prompt's close to the colony on the map (29 frames, #3340 -&gt; #3369). */
    static final double SPRITE_AFTER_PROMPT_MS = 29 * ClassicWoodcut.FRAME_MS;

    /** From the prompt's close to woodcut 2's black (34 frames, #3340 -&gt; #3374). */
    static final double BLACK_AFTER_PROMPT_MS = 34 * ClassicWoodcut.FRAME_MS;


    private ClassicFounding() {}


    // The site warning

    /**
     * Whether a confirm is FreeCol's site warning
     * ({@code Tile.getBuildColonyWarnings}): a label whose parts are all
     * {@code warning.*} keys.
     *
     * @param t The question.
     * @return True for the site warnings.
     */
    static boolean siteWarnings(StringTemplate t) {
        if (t == null || t.getTemplateType() != StringTemplate.TemplateType.LABEL
            || t.entryList().isEmpty()) return false;
        for (SimpleEntry<String, StringTemplate> e : t.entryList()) {
            final StringTemplate v = e.getValue();
            if (v == null || v.getId() == null || !v.getId().startsWith(WARNING)) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param t FreeCol's site warnings ({@link #siteWarnings}).
     * @return Whether the site is land-locked: the original's @NOPORT.
     */
    static boolean landLocked(StringTemplate t) {
        if (!siteWarnings(t)) return false;
        for (SimpleEntry<String, StringTemplate> e : t.entryList()) {
            if (LAND_LOCKED.equals(e.getValue().getId())) return true;
        }
        return false;
    }

    /**
     * The @NOPORT box: GAME.TXT's text and rows, the frontiersman, the
     * bar on row 1 (GAME.TXT has no {@code @default}), Escape and a
     * click beside it as row 1.
     *
     * @param t The original texts, or null.
     * @param title The stopgap's title.
     * @return The box, or null without the text.
     */
    static ClassicAdvisorBox.Request noPortRequest(ClassicText t, String title) {
        final ClassicText.Message m = (t == null) ? null : t.message(NOPORT_SECTION);
        if (m == null || m.options.size() < 2) return null;
        final ClassicAdvisorBox.Builder b = ClassicAdvisorBox.fromGameText(
            NOPORT_SECTION, m, null);
        return (b == null) ? null
            : b.defaultRow(ClassicHud.clamp(ClassicAdvisorBox.defaultRow(m), 0, 1))
                .cancelRow(0).portrait(ClassicAdvisorBox.Portrait.SCOUT)
                .stopgap(title, null).build();
    }

    /**
     * @param chosen The @NOPORT box's answer.
     * @return Whether it founds the colony: only its second row.
     */
    static boolean foundsAnyway(int chosen) {
        return chosen == NOPORT_FOUND_ROW;
    }


    // The name

    /**
     * COLONY.TXT's section of a player's nation: the nation's suffix in
     * upper case ({@code model.nation.dutch} is {@code DUTCH}).
     *
     * @param player The player.
     * @return The section, or null.
     */
    static String section(Player player) {
        final String s = (player == null || player.getNation() == null) ? null
            : player.getNation().getSuffix();
        return (s == null || s.isEmpty()) ? null : s.toUpperCase(Locale.ROOT);
    }

    /**
     * The default name: the first of the nation's names that no
     * settlement in the game has (the server refuses a name in use).
     *
     * @param names COLONY.TXT's names of the nation, or null.
     * @param game The game, or null.
     * @return The name, or null when the list is missing or used up.
     */
    static String defaultName(List<String> names, Game game) {
        if (names == null) return null;
        for (String n : names) {
            if (game == null || game.getSettlementByName(n) == null) return n;
        }
        return null;
    }

    /**
     * The @COLONY box: GAME.TXT's question, the colonist over it, the name
     * field with the default selected and GAME.TXT's "Name:" as its label.
     *
     * @param t The original texts, or null.
     * @param dflt The default name.
     * @param title The stopgap's title.
     * @return The box, or null without the text.
     */
    static ClassicAdvisorBox.Request colonyRequest(ClassicText t, String dflt,
                                                   String title) {
        final ClassicText.Message m = (t == null) ? null : t.message(COLONY_SECTION);
        if (m == null || m.text.isEmpty() || m.options.isEmpty()) return null;
        return ClassicAdvisorBox.Request.builder(COLONY_SECTION).gameText(m.text)
            .width((m.width != null) ? m.width : COLONY_WIDTH).rows(m.options)
            .portrait(ClassicAdvisorBox.Portrait.COLONIST).field(dflt)
            .stopgap(title, null).build();
    }

    /**
     * The name a @COLONY box was answered with.
     *
     * @param r The box.
     * @param chosen Its answer.
     * @return The name (the field's text, the default for an empty one),
     *     or null: Escape, a click beside it or a box that could not open
     *     found nothing.
     */
    static String answered(ClassicAdvisorBox.Request r, int chosen) {
        if (r == null || r.field == null || chosen != 0 || !r.field.taken()) return null;
        return r.field.answer();
    }

    /**
     * FreeCol's words for a name already in use, as its {@code GUI} says
     * them.
     *
     * @param name The name.
     * @return The notice's template.
     */
    static StringTemplate notUnique(String name) {
        return StringTemplate.template("nameColony.notUnique").addName("%name%", name);
    }

    /** @return The stopgap's title of these boxes. */
    static String title() {
        return Messages.message("classic.dialog.messages");
    }
}
