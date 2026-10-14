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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;


/**
 * The painters of the original's <b>new-game screens</b> on the 320x200
 * canvas: the world of «Neue Welt INDIVIDUALISIEREN» (Roger's land tests
 * {@code landtests/opening_024}, {@code 026..032}; {@link #paintWorld}),
 * difficulty ({@code opening_056..060}, English run {@code 034}),
 * European power ({@code 061..064}, {@code 035}), leader name ({@code 065},
 * {@code 036}), the chosen nation's history and bonus pages ({@code 066},
 * {@code 067}; {@code 037}, {@code 038}) and the audience ({@code 068},
 * {@code 039}).
 *
 * <p>Every screen is an untouched original picture ({@code .PIK}) drawn 1:1
 * with a few overlays -- unselected cards are pure PIK pixels.  All painters
 * are static and take explicit assets, texts and state, with no
 * {@code FreeColClient}, {@code Messages} or {@code ResourceManager}, so the
 * headless preview harness renders them exactly as the game does and diffs
 * them against the captures (all 19 at 0 differing pixels).  Assets come
 * from {@link ClassicPackFiles}, texts from {@link ClassicText}; this class
 * holds only geometry and colours, every one measured on the capture named
 * in its Javadoc.  The mouse arrow is drawn by the caller.
 */
final class ClassicNewWorldScreens {

    private static final Logger logger = Logger.getLogger(ClassicNewWorldScreens.class.getName());

    /**
     * FreeCol's difficulty levels in the original's order (NAMES.TXT
     * {@code @DIFFICULTY}, easiest first; ids from
     * data/rules/classic/specification.xml:3399/3603/3797/3986/4169).
     * {@code model.difficulty.custom} has no original counterpart.
     */
    static final String[] DIFFICULTY_IDS = {
        "model.difficulty.veryEasy", "model.difficulty.easy",
        "model.difficulty.medium", "model.difficulty.hard",
        "model.difficulty.veryHard"
    };

    /** FreeCol's nations in the original's order (NAMES.TXT {@code @COUNTRY}). */
    static final String[] NATION_IDS = {
        "model.nation.english", "model.nation.french",
        "model.nation.spanish", "model.nation.dutch"
    };

    /**
     * The difficulty cards {x, y, w, h} in reading order (top row of two,
     * bottom row of three), measured on DIFFICUL.PIK and 056-060: the
     * selection frame is a 1-px outline exactly on each card's outermost
     * dark border ring, so frame = card bounds = click rect.
     */
    static final int[][] DIFF_CARDS = {
        { 128, 7, 68, 90 }, { 233, 7, 68, 90 },
        { 23, 103, 68, 90 }, { 128, 103, 68, 90 }, { 233, 103, 68, 90 }
    };

    /**
     * The difficulty frame and label colours (056-060): DIFFICUL.PIK palette
     * indices 10, 9, 14, 13, 12.
     */
    static final int[] DIFF_COLOURS = { 0x04B610, 0x048AE3, 0xDFBA00, 0xFF7100, 0xEF0404 };

    /** The nation cards {x, y, w, h}, England, France, Spain, Holland (061-064). */
    static final int[][] NATION_CARDS = {
        { 112, 13, 88, 82 }, { 211, 13, 88, 82 },
        { 112, 104, 88, 82 }, { 211, 104, 88, 82 }
    };

    /**
     * The nation colours (061-064): NATIONS.PIK palette indices 12, 9, 14,
     * 13 -- the colour column of NAMES.TXT {@code @COUNTRY}.  Index 12 is
     * 0xF70000 in this picture's palette but 0xEF0404 in DIFFICUL's, so the
     * two screens must not share one red.
     */
    static final int[] NATION_COLOURS = { 0xF70000, 0x048AE3, 0xDFBA00, 0xFF7100 };

    /**
     * The span the headings and footer are centred in, {@code x = (span -
     * w) / 2}: 116 on DIFFICUL, 114 on NATIONS (115/113 miss the even-width
     * nation heading by one pixel, 419 px of diff).  Where the original
     * takes them from is unknown.
     */
    static final int DIFF_SPAN = 116, NATION_SPAN = 114;

    /** Heading line tops: difficulty 16/29, nations 36/49 (pitch 13). */
    static final int DIFF_HEAD_Y = 16, NATION_HEAD_Y = 36, HEAD_PITCH = 13;

    /** Footer tops: 81 on DIFFICUL (056), 182 on NATIONS (061). */
    static final int DIFF_FOOTER_Y = 81, NATION_FOOTER_Y = 182;

    /**
     * The footer's click rects: the "click when done" line, a little larger
     * than its FONTTINY cell.
     */
    static final Rectangle DIFF_FOOTER = new Rectangle(0, 80, 116, 8),
        NATION_FOOTER = new Rectangle(0, 181, 114, 8);

    /**
     * The shared UI ink (= ClassicMenuBox's ink), FONTINTR's value-2 shade
     * and the name highlight (= ClassicMenuBox GAME's dark, the menu's
     * selection bar colour, ClassicMenuBox.java:113).
     */
    static final int INK = 0x559634, SHADE = 0x794934, SEL_FILL = 0x3C2018;

    /**
     * FONTINTR's value-3 shadow: black on DIFFICUL/NATIONS, but 0x0C0C0C on
     * WOODPANL (its palette index 47; DIFFICUL/NATIONS have no such colour).
     */
    static final int SHADOW_PICKER = 0x000000, SHADOW_WOOD = 0x0C0C0C;

    /** The highlight colour of '{..}' words on the nation pages (066). */
    static final int HIGHLIGHT = 0xC7A220;

    /** The name screen (065/036): prompt top, box, text origin. */
    static final int NAME_PROMPT_Y = 88;
    static final Rectangle NAME_BOX = new Rectangle(79, 98, 167, 14);
    static final int NAME_X = 82, NAME_Y = 101;

    /**
     * The prompt is centred like a GAME.TXT {@code ^^} line in
     * {@code @LEADERNAME}'s {@code @width=300}: x = 10 + ceil((300 - w)/2).
     */
    static final int NAME_MSG_WIDTH = 300;

    /** The nation pages: FONTINTR, pitch 10 (font height + 1). */
    static final int PAGE_PITCH = 10;

    /** The audience scroll (068/039): FONTKING ink and pitch. */
    static final int KING_TEXT = 0x715545, KING_PITCH = 8;

    /** The audience text section per nation: Holland has a stadtholder. */
    static final String[] AUDIENCE_SECTIONS = { "VICEROY", "VICEROY", "VICEROY", "VICEROY2" };

    /** The banner sprite per nation, first frame (068/039). */
    static final String[] BANNERS = {
        "ENGLND1.SS.000", "FRANCE1.SS.000", "SPAIN1.SS.000", "DUTCH1.SS.000"
    };

    /** The seated ruler and dog, the same for king and stadtholder. */
    static final String KING = "KING1.SS.000";

    /** The pictures. */
    static final String DIFFICUL = "DIFFICUL.PIK", NATIONS = "NATIONS.PIK",
        WOODPANL = "WOODPANL.PIK", KINGLSS1 = "KINGLSS1.PIK";

    /** The picture of «Neue Welt INDIVIDUALISIEREN». */
    static final String CUSTOMIZ = "CUSTOMIZ.PIK";

    /**
     * «Neue Welt INDIVIDUALISIEREN» (Roger's land tests, recordings
     * {@code landtests/2026-10-13_opening_024} and {@code 026..032}): the
     * twelve pictures of CUSTOMIZ.PIK, four columns LANDMASSE, LANDFORM,
     * TEMPERATUR, KLIMA at these left edges, three rows (the answers top
     * to bottom, {@code OriginalWorldSettings} TOP/MIDDLE/BOTTOM) at these
     * top edges, each 72x48.  The frame of a chosen picture is a 1-px
     * outline exactly on these bounds (236 px each, e.g. {@code 024}
     * #321: (10,76), (86,76), (162,76), (238,76); #461: (10,135);
     * {@code 027} #2525: (10,16); {@code 030} #381: (86,16)), which are
     * also the click rects.
     */
    static final int[] WORLD_X = { 10, 86, 162, 238 }, WORLD_Y = { 16, 76, 135 };

    /** The size of a picture of the world screen. */
    static final int WORLD_W = 72, WORLD_H = 48;

    /**
     * The frame and label colours of the world screen ({@code 024} #321
     * ff.): the answer of the column last chosen -- LANDMASSE when the
     * screen opens -- in yellow (CUSTOMIZ.PIK palette index 14, as the
     * clip's palette shows), the answers of the other three columns in green
     * (index 10).  The pointer over a picture changes nothing (024 #371-#394
     * pass over the bottom row without a change).
     */
    static final int WORLD_CURRENT = 0xDFBA00, WORLD_CHOSEN = 0x04B610;

    /** The two label lines of a chosen picture: its top + 17 and + 25. */
    static final int WORLD_LABEL_DY = 17, WORLD_VALUE_DY = 25;

    /**
     * The span the world screen's heading and footer are centred in,
     * {@code x = (span - w) / 2}, as {@link #DIFF_SPAN}: 322, one pixel
     * right of the screen's centre (024 #321: the heading, 140 px, at 91,
     * the footer, 111 px, at 105; 320 misses both by one pixel).
     */
    static final int WORLD_SPAN = 322;

    /** The heading's top (FONTINTR, 024 #321 rows 4-11). */
    static final int WORLD_HEAD_Y = 4;

    /** The footer's top (FONTTINY, 024 #321 rows 190-194). */
    static final int WORLD_FOOTER_Y = 190;

    /**
     * The footer's click rect: its FONTTINY line (x 106-213, rows 190-194
     * in 024 #321) one pixel larger all round.  Roger's clicks that left the
     * screen were at (194,192), (148,191), (197,193), (166,193), (153,190),
     * (181,190), (176,193), (166,192) (024, 026-032).
     */
    static final Rectangle WORLD_FOOTER = new Rectangle(105, 189, 110, 7);


    private ClassicNewWorldScreens() {}


    /** The pictures, sprites and fonts of the five screens. */
    static final class Assets {

        final BufferedImage difficul, nations, woodpanl, kinglss1, king;
        final Point kingAt;
        final BufferedImage[] banner;
        final Point[] bannerAt;
        final ClassicFont tiny, intro, kingFont;

        /** The mouse arrow (may be null). */
        final BufferedImage cursor;

        /**
         * The departure's ten pictures LEVN0001..0010.PIK
         * ({@link ClassicDeparture}); entries are null when the pack lacks
         * them -- the chain itself works without them, only the departure
         * is then skipped ({@link ClassicDeparture#available}).
         */
        final BufferedImage[] levn;

        Assets(BufferedImage difficul, BufferedImage nations, BufferedImage woodpanl,
               BufferedImage kinglss1, BufferedImage king, Point kingAt,
               BufferedImage[] banner, Point[] bannerAt, ClassicFont tiny,
               ClassicFont intro, ClassicFont kingFont, BufferedImage cursor) {
            this(difficul, nations, woodpanl, kinglss1, king, kingAt, banner,
                 bannerAt, tiny, intro, kingFont, cursor,
                 new BufferedImage[ClassicDeparture.STEPS]);
        }

        Assets(BufferedImage difficul, BufferedImage nations, BufferedImage woodpanl,
               BufferedImage kinglss1, BufferedImage king, Point kingAt,
               BufferedImage[] banner, Point[] bannerAt, ClassicFont tiny,
               ClassicFont intro, ClassicFont kingFont, BufferedImage cursor,
               BufferedImage[] levn) {
            this.difficul = difficul;
            this.nations = nations;
            this.woodpanl = woodpanl;
            this.kinglss1 = kinglss1;
            this.king = king;
            this.kingAt = kingAt;
            this.banner = banner;
            this.bannerAt = bannerAt;
            this.tiny = tiny;
            this.intro = intro;
            this.kingFont = kingFont;
            this.cursor = cursor;
            this.levn = levn;
        }

        /**
         * Load from pack files.
         *
         * @return The assets, or null when anything (but the arrow) is
         *     missing -- a pack converted before the new-game material.
         */
        static Assets load(ClassicPackFiles p) {
            if (p == null || !p.hasNewWorldChain()) return null;
            final BufferedImage[] banner = new BufferedImage[BANNERS.length];
            final Point[] bannerAt = new Point[BANNERS.length];
            for (int i = 0; i < BANNERS.length; i++) {
                banner[i] = p.image(ClassicPackFiles.ssKey(BANNERS[i]));
                bannerAt[i] = p.spriteTopLeft(BANNERS[i]);
                if (banner[i] == null || bannerAt[i] == null) return null;
            }
            final Assets a = new Assets(
                p.image(ClassicPackFiles.pikKey(DIFFICUL)),
                p.image(ClassicPackFiles.pikKey(NATIONS)),
                p.image(ClassicPackFiles.pikKey(WOODPANL)),
                p.image(ClassicPackFiles.pikKey(KINGLSS1)),
                p.image(ClassicPackFiles.ssKey(KING)), p.spriteTopLeft(KING),
                banner, bannerAt,
                p.font(ClassicFont.TINY), p.font(ClassicFont.INTRO),
                p.font(ClassicFont.KING),
                ClassicMainMenuPanel.cursorSprite(
                    p.image(ClassicPackFiles.ssKey("CURSOR.SS.000"))),
                ClassicDeparture.loadPictures(p));
            if (a.difficul == null || a.nations == null || a.woodpanl == null
                || a.kinglss1 == null || a.king == null || a.kingAt == null
                || a.tiny == null || a.intro == null || a.kingFont == null) {
                logger.info("classic_original pack lacks new-game art"
                    + " (re-run ant classic-assets)");
                return null;
            }
            return a;
        }
    }


    /**
     * Every string the five screens draw, taken from the original's text
     * files for the four nations.  Indices: difficulty 0..4 easiest first,
     * nations 0 England, 1 France, 2 Spain, 3 Holland.
     */
    static final class Texts {

        /**
         * The difficulty labels: NAMES.TXT {@code @DIFFICULTY} (not GAME.TXT
         * {@code @DIFFICULTY}, whose hardest level is spelled with Ö where
         * 060 shows the lowercase ö of NAMES.TXT), upper-cased a-z only,
         * plus ':'.
         */
        final String[] diffNames;
        /** The difficulty subtitles (LABELS {@code @MISC} 164..168). */
        final String[] diffSubs;
        /** The nation names (NAMES.TXT {@code @COUNTRY} column 0). */
        final String[] nationNames;
        /** The nation labels: names upper-cased a-z only, plus ':'. */
        final String[] nationLabels;
        /** The bonus words (LABELS {@code @MISC} 172..175). */
        final String[] bonus;
        /** The two heading lines of each picker (LABELS 162/161, 170/169). */
        final String[] headDiff, headNation;
        /** The picker footer: LABELS 160 in parentheses (added by the game). */
        final String footer;
        /** The name prompt: GAME.TXT {@code @LEADERNAME} line 1 without '^^'. */
        final String namePrompt;
        /** The name field's length in characters ({@code @LEADERNAME}'s option). */
        final int nameFieldLength;
        /** The default leaders (NAMES.TXT {@code @LEADERNAME} column 0). */
        final String[] defaultLeaders;
        /** The history and bonus pages (GAME.TXT {@code @NATION<i>A/B}). */
        final ClassicText.Message[] pageA, pageB;
        /** The audience scroll per nation, {@code %COUNTRY} filled in. */
        final ClassicText.Message[] audience;
        /**
         * The departure's captions GAME.TXT {@code @BUILD1..10}, raw (see
         * {@link ClassicDeparture.Captions}); entries null when missing.
         */
        final ClassicText.Message[] build;
        /**
         * The difficulty titles as written (NAMES.TXT {@code @DIFFICULTY}
         * column 0, easiest first), for {@code @BUILD2}.
         */
        final String[] diffTitles;
        /**
         * The home ports (NAMES.TXT {@code @HOMEPORT}, original nation
         * order), for {@code @BUILD3}; null when missing.
         */
        final String[] homePorts;

        Texts(String[] diffNames, String[] diffSubs, String[] nationNames,
              String[] bonus, String[] headDiff, String[] headNation,
              String footer, String namePrompt, int nameFieldLength,
              String[] defaultLeaders, ClassicText.Message[] pageA,
              ClassicText.Message[] pageB, ClassicText.Message[] audience) {
            this(diffNames, diffSubs, nationNames, bonus, headDiff, headNation,
                 footer, namePrompt, nameFieldLength, defaultLeaders, pageA,
                 pageB, audience, new ClassicText.Message[ClassicDeparture.STEPS],
                 null, null);
        }

        Texts(String[] diffNames, String[] diffSubs, String[] nationNames,
              String[] bonus, String[] headDiff, String[] headNation,
              String footer, String namePrompt, int nameFieldLength,
              String[] defaultLeaders, ClassicText.Message[] pageA,
              ClassicText.Message[] pageB, ClassicText.Message[] audience,
              ClassicText.Message[] build, String[] diffTitles,
              String[] homePorts) {
            this.diffNames = diffNames;
            this.diffSubs = diffSubs;
            this.nationNames = nationNames;
            this.nationLabels = new String[nationNames.length];
            for (int i = 0; i < nationNames.length; i++) {
                this.nationLabels[i] = ClassicText.upperAscii(nationNames[i]) + ":";
            }
            this.bonus = bonus;
            this.headDiff = headDiff;
            this.headNation = headNation;
            this.footer = footer;
            this.namePrompt = namePrompt;
            this.nameFieldLength = nameFieldLength;
            this.defaultLeaders = defaultLeaders;
            this.pageA = pageA;
            this.pageB = pageB;
            this.audience = audience;
            this.build = build;
            this.diffTitles = diffTitles;
            this.homePorts = homePorts;
        }

        /**
         * Pull the strings from the original texts.
         *
         * @return The texts, or null when a section or entry is missing.
         */
        static Texts load(ClassicText t) {
            if (t == null) return null;
            final List<String[]> diffRows = t.names("DIFFICULTY");
            final List<String[]> countries = t.names("COUNTRY");
            final List<String[]> leaders = t.names("LEADERNAME");
            final int nd = DIFFICULTY_IDS.length, nn = NATION_IDS.length;
            if (diffRows.size() < nd || countries.size() < nn || leaders.size() < nn) {
                return missing("NAMES.TXT rows");
            }
            final String[] diffNames = new String[nd], diffSubs = new String[nd];
            for (int i = 0; i < nd; i++) {
                diffNames[i] = ClassicText.upperAscii(diffRows.get(i)[0]) + ":";
                diffSubs[i] = t.misc(ClassicText.MISC_DIFF_SUB0 + i);
            }
            final String[] diffTitles = new String[nd];
            for (int i = 0; i < nd; i++) diffTitles[i] = diffRows.get(i)[0];
            final List<String[]> ports = t.names("HOMEPORT");
            final String[] homePorts = (ports.size() < nn) ? null : new String[nn];
            final String[] nationNames = new String[nn], bonus = new String[nn],
                defaults = new String[nn];
            final ClassicText.Message[] pageA = new ClassicText.Message[nn],
                pageB = new ClassicText.Message[nn],
                audience = new ClassicText.Message[nn];
            for (int i = 0; i < nn; i++) {
                nationNames[i] = countries.get(i)[0];
                defaults[i] = leaders.get(i)[0];
                if (homePorts != null) homePorts[i] = ports.get(i)[0];
                bonus[i] = t.misc(ClassicText.MISC_BONUS0 + i);
                pageA[i] = t.message("NATION" + i + "A");
                pageB[i] = t.message("NATION" + i + "B");
                final ClassicText.Message m = t.message(AUDIENCE_SECTIONS[i]);
                if (pageA[i] == null || pageB[i] == null || m == null) {
                    return missing("GAME.TXT nation sections");
                }
                final Map<String, String> vars = new HashMap<>();
                vars.put("COUNTRY", nationNames[i]);
                final List<String> filled = new ArrayList<>(m.text.size());
                for (String l : m.text) filled.add(ClassicText.substitute(l, vars));
                audience[i] = m.withText(filled);
            }
            final ClassicText.Message leader = t.message("LEADERNAME");
            if (leader == null || leader.text.isEmpty()) return missing("@LEADERNAME");
            String prompt = leader.text.get(0);
            if (prompt.startsWith("^^")) prompt = prompt.substring(2);
            final int fieldLength = leader.options.isEmpty()
                ? ClassicNewWorldChain.DEFAULT_NAME_LENGTH
                : leader.options.get(0).trim().length();
            final String footer = t.misc(ClassicText.MISC_DONE_CLICK);
            final String[] headDiff = { t.misc(ClassicText.MISC_DIFFICULTY),
                                        t.misc(ClassicText.MISC_CHOOSE_DIFF) };
            final String[] headNation = { t.misc(ClassicText.MISC_POWER),
                                          t.misc(ClassicText.MISC_CHOOSE_NATION) };
            for (String s : new String[] { footer, headDiff[0], headDiff[1],
                                           headNation[0], headNation[1] }) {
                if (s == null) return missing("LABELS.TXT @MISC");
            }
            for (int i = 0; i < nd; i++) if (diffSubs[i] == null) return missing("LABELS.TXT @MISC");
            for (int i = 0; i < nn; i++) if (bonus[i] == null) return missing("LABELS.TXT @MISC");
            return new Texts(diffNames, diffSubs, nationNames, bonus, headDiff,
                headNation, "(" + footer + ")", prompt, fieldLength, defaults,
                pageA, pageB, audience, ClassicDeparture.loadCaptions(t),
                diffTitles, homePorts);
        }

        private static Texts missing(String what) {
            logger.info("Original texts lack " + what
                + " -- the new-game screens are skipped");
            return null;
        }
    }


    /**
     * The picture and words of «Neue Welt INDIVIDUALISIEREN»: CUSTOMIZ.PIK
     * and LABELS.TXT {@code @MISC} (see
     * {@link ClassicText#MISC_WORLD_QUESTION0}); its footer is the pickers'
     * ({@link Texts#footer}).  GAME.TXT's {@code @CLAND}, {@code @CCONT},
     * {@code @CTEMP} and {@code @CCLIM} hold the same questions as text
     * boxes ("Große Kontinente" where the picture says "Kontinente"); no
     * recording shows them, the game shows the pictures.
     */
    static final class World {

        /** CUSTOMIZ.PIK. */
        final BufferedImage picture;
        /** The heading (LABELS 159). */
        final String heading;
        /** The four questions with the ':' the game adds (LABELS 143..146). */
        final String[] questions;
        /** The answers [question][row] (LABELS 147..158). */
        final String[][] answers;

        World(BufferedImage picture, String heading, String[] questions,
              String[][] answers) {
            this.picture = picture;
            this.heading = heading;
            this.questions = questions;
            this.answers = answers;
        }

        /**
         * Load from the pack.
         *
         * @param p The pack, or null.
         * @param t The pack's texts, or null.
         * @return The screen's material, or null when the pack lacks
         *     CUSTOMIZ.PIK or a label (INDIVIDUALISIEREN then keeps its
         *     "follows later" notice).
         */
        static World load(ClassicPackFiles p, ClassicText t) {
            if (p == null || t == null) return null;
            final BufferedImage pic = p.image(ClassicPackFiles.pikKey(CUSTOMIZ));
            final String head = t.misc(ClassicText.MISC_WORLD_HEAD);
            final String[] q = new String[WORLD_X.length];
            final String[][] ans = new String[WORLD_X.length][WORLD_Y.length];
            boolean ok = pic != null && head != null;
            for (int c = 0; c < q.length && ok; c++) {
                final String s = t.misc(ClassicText.MISC_WORLD_QUESTION0 + c);
                ok = s != null;
                q[c] = s + ":";
                for (int r = 0; r < WORLD_Y.length && ok; r++) {
                    ans[c][r] = t.misc(ClassicText.MISC_WORLD_ANSWER0
                                       + WORLD_Y.length * c + r);
                    ok = ans[c][r] != null;
                }
            }
            if (!ok) {
                logger.info("classic_original pack lacks CUSTOMIZ.PIK or its"
                    + " labels -- Neue Welt INDIVIDUALISIEREN stays closed");
                return null;
            }
            return new World(pic, head, q, ans);
        }
    }


    // Painters (1:1 at canvas coordinates; the caller draws the arrow)

    /** Dispatch to the painter of the view's screen (no world screen). */
    static void paint(Graphics2D g, Assets a, Texts t, ClassicNewWorldChain.View v) {
        paint(g, a, t, null, v);
    }

    /**
     * Dispatch to the painter of the view's screen.
     *
     * @param w The world screen's material; only the world screen needs
     *     it (without it that screen paints nothing).
     */
    static void paint(Graphics2D g, Assets a, Texts t, World w,
                      ClassicNewWorldChain.View v) {
        switch (v.step) {
        case WORLD:
            if (w != null) paintWorld(g, a, t, w, v.worldRows, v.worldColumn);
            break;
        case DIFFICULTY: paintDifficulty(g, a, t, v.diffSel); break;
        case NATION: paintNation(g, a, t, v.nationSel); break;
        case NAME: paintName(g, a, t, v.nationSel, v.name, v.nameSelected); break;
        case PAGE_A: paintNationPage(g, a, t, v.nationSel, false); break;
        case PAGE_B: paintNationPage(g, a, t, v.nationSel, true); break;
        case AUDIENCE: default: paintAudience(g, a, t, v.nationSel); break;
        }
    }

    /**
     * «Neue Welt INDIVIDUALISIEREN» ({@code landtests/opening_024},
     * {@code 026..032}): CUSTOMIZ.PIK, the FONTINTR heading centred in
     * {@link #WORLD_SPAN} at {@link #WORLD_HEAD_Y} in the pickers' ink, the
     * FONTTINY footer centred likewise at {@link #WORLD_FOOTER_Y}, and for each column the
     * chosen picture's frame with its two labels (the question with ':'
     * and the answer, {@link #cardLabel} at the picture's top + 17 and
     * + 25), the current column's in yellow, the others in green.
     *
     * @param rows The chosen row of each column (0 top .. 2 bottom).
     * @param current The current column (0..3).
     */
    static void paintWorld(Graphics2D g, Assets a, Texts t, World w, int[] rows,
                           int current) {
        g.drawImage(w.picture, 0, 0, null);
        final int hw = a.intro.stringWidth(w.heading);
        a.intro.draw(g, w.heading, (WORLD_SPAN - hw) / 2, WORLD_HEAD_Y,
                     ClassicFont.colours(INK, SHADE, SHADOW_PICKER));
        final int fw = a.tiny.stringWidth(t.footer);
        a.tiny.draw(g, t.footer, (WORLD_SPAN - fw) / 2, WORLD_FOOTER_Y,
                    ClassicFont.colours(INK));
        for (int c = 0; c < WORLD_X.length; c++) {
            final int[] r = worldCell(c, rows[c]);
            final int rgb = (c == current) ? WORLD_CURRENT : WORLD_CHOSEN;
            outline(g, r, rgb);
            cardLabel(g, a.tiny, w.questions[c], r, r[1] + WORLD_LABEL_DY, rgb);
            cardLabel(g, a.tiny, w.answers[c][rows[c]], r, r[1] + WORLD_VALUE_DY, rgb);
        }
    }

    /** The picture of a column and row as {x, y, w, h}. */
    static int[] worldCell(int column, int row) {
        return new int[] { WORLD_X[column], WORLD_Y[row], WORLD_W, WORLD_H };
    }

    /**
     * Difficulty ({@code 056..060}): DIFFICUL.PIK, the two FONTINTR heading
     * lines, the FONTTINY footer, the selected card's frame and its two
     * labels (y = card top + 38 and + 46).
     */
    static void paintDifficulty(Graphics2D g, Assets a, Texts t, int sel) {
        g.drawImage(a.difficul, 0, 0, null);
        paintPickerText(g, a, t.headDiff, t.footer, DIFF_SPAN, DIFF_HEAD_Y, DIFF_FOOTER_Y);
        final int[] c = DIFF_CARDS[sel];
        outline(g, c, DIFF_COLOURS[sel]);
        cardLabel(g, a.tiny, t.diffNames[sel], c, c[1] + 38, DIFF_COLOURS[sel]);
        cardLabel(g, a.tiny, t.diffSubs[sel], c, c[1] + 46, DIFF_COLOURS[sel]);
    }

    /**
     * European power ({@code 061..064}): NATIONS.PIK, headings, footer, the
     * selected card's frame and its labels (name at card top + 2, bonus
     * word at the card's bottom - 8).  The power's own heading word in
     * LABELS 171 is not drawn by the original.
     */
    static void paintNation(Graphics2D g, Assets a, Texts t, int sel) {
        g.drawImage(a.nations, 0, 0, null);
        paintPickerText(g, a, t.headNation, t.footer, NATION_SPAN, NATION_HEAD_Y,
                        NATION_FOOTER_Y);
        final int[] c = NATION_CARDS[sel];
        outline(g, c, NATION_COLOURS[sel]);
        cardLabel(g, a.tiny, t.nationLabels[sel], c, c[1] + 2, NATION_COLOURS[sel]);
        cardLabel(g, a.tiny, t.bonus[sel], c, c[1] + c[3] - 8, NATION_COLOURS[sel]);
    }

    /**
     * Leader name ({@code 065}/{@code 036}): WOODPANL.PIK, the prompt, the
     * 1-px box, the name -- on the dark selection fill while it is the
     * untouched default -- and a static FONTINTR '_' caret after it.
     */
    static void paintName(Graphics2D g, Assets a, Texts t, int nation,
                          String name, boolean selected) {
        g.drawImage(a.woodpanl, 0, 0, null);
        final int[] ink = ClassicFont.colours(INK, SHADE, SHADOW_WOOD);
        final int pw = a.intro.stringWidth(t.namePrompt);
        final int px = (320 - NAME_MSG_WIDTH) / 2
            + Math.floorDiv(NAME_MSG_WIDTH - pw + 1, 2);
        a.intro.draw(g, t.namePrompt, px, NAME_PROMPT_Y, ink);
        outline(g, new int[] { NAME_BOX.x, NAME_BOX.y, NAME_BOX.width, NAME_BOX.height }, INK);
        final String n = (name == null) ? "" : name;
        final int w = a.intro.stringWidth(n);
        if (selected && !n.isEmpty()) {
            g.setColor(new Color(SEL_FILL));
            g.fillRect(NAME_X - 1, NAME_Y - 1, w + 2, 10);
        }
        a.intro.draw(g, n, NAME_X, NAME_Y, ink);
        a.intro.draw(g, "_", NAME_X + w, NAME_Y, ink);
    }

    /**
     * A nation's history ({@code @NATION<i>A}, 066/037) or bonus page
     * ({@code @NATION<i>B}, 067/038): WOODPANL.PIK plus the message laid out
     * by {@link ClassicTextLayout} in FONTINTR, highlighted words in gold.
     */
    static void paintNationPage(Graphics2D g, Assets a, Texts t, int nation,
                                boolean bonusPage) {
        g.drawImage(a.woodpanl, 0, 0, null);
        final ClassicText.Message m = bonusPage ? t.pageB[nation] : t.pageA[nation];
        paintMessage(g, a.intro, m, PAGE_PITCH,
                     ClassicFont.colours(INK, SHADE, SHADOW_WOOD),
                     ClassicFont.colours(HIGHLIGHT, SHADE, SHADOW_WOOD));
    }

    /**
     * The audience ({@code 068}/{@code 039}): KINGLSS1.PIK, the nation's
     * banner and the seated ruler at their sprite anchors, and the scroll
     * text in FONTKING ({@code @VICEROY}, Holland {@code @VICEROY2}).
     */
    static void paintAudience(Graphics2D g, Assets a, Texts t, int nation) {
        g.drawImage(a.kinglss1, 0, 0, null);
        g.drawImage(a.banner[nation], a.bannerAt[nation].x, a.bannerAt[nation].y, null);
        g.drawImage(a.king, a.kingAt.x, a.kingAt.y, null);
        final int[] ink = ClassicFont.colours(KING_TEXT, KING_TEXT, 0x000000);
        paintMessage(g, a.kingFont, t.audience[nation], KING_PITCH, ink, ink);
    }

    /** A GAME.TXT message through the original's layout. */
    static void paintMessage(Graphics2D g, ClassicFont f, ClassicText.Message m,
                             int pitch, int[] normal, int[] high) {
        if (m == null) return;
        final int w = (m.width != null) ? m.width : 300;
        final Integer top = (m.y != null) ? Integer.valueOf(m.y + 3) : null;
        for (ClassicTextLayout.Line l : ClassicTextLayout.layout(f, m.text, w,
                 m.x, top, pitch)) {
            f.drawMarked(g, l.marked, l.x, l.y, normal, high);
        }
    }

    /** The two FONTINTR heading lines and the FONTTINY footer of a picker. */
    private static void paintPickerText(Graphics2D g, Assets a, String[] head,
                                        String footer, int span, int headY,
                                        int footerY) {
        final int[] ink = ClassicFont.colours(INK, SHADE, SHADOW_PICKER);
        for (int i = 0; i < head.length; i++) {
            final int w = a.intro.stringWidth(head[i]);
            a.intro.draw(g, head[i], (span - w) / 2, headY + i * HEAD_PITCH, ink);
        }
        final int fw = a.tiny.stringWidth(footer);
        a.tiny.draw(g, footer, (span - fw) / 2, footerY, ClassicFont.colours(INK));
    }

    /**
     * A card label: centred on the card as {@code x = fx + 1 + (fw - w)/2},
     * first in black one pixel to the right, then in the card colour.
     */
    private static void cardLabel(Graphics2D g, ClassicFont f, String s, int[] card,
                                  int y, int rgb) {
        final int x = card[0] + 1 + (card[2] - f.stringWidth(s)) / 2;
        f.draw(g, s, x + 1, y, ClassicFont.colours(0x000000));
        f.draw(g, s, x, y, ClassicFont.colours(rgb));
    }

    /** A 1-px outline exactly on {x, y, w, h} (fills, so it scales cleanly). */
    private static void outline(Graphics2D g, int[] r, int rgb) {
        g.setColor(new Color(rgb));
        g.fillRect(r[0], r[1], r[2], 1);
        g.fillRect(r[0], r[1] + r[3] - 1, r[2], 1);
        g.fillRect(r[0], r[1], 1, r[3]);
        g.fillRect(r[0] + r[2] - 1, r[1], 1, r[3]);
    }


    // Hit tests

    /**
     * The card under a virtual point.
     *
     * @return The card index, or -1 (also on the other screens).
     */
    static int cardAt(ClassicNewWorldChain.Step step, int x, int y) {
        final int[][] cards = (step == ClassicNewWorldChain.Step.DIFFICULTY) ? DIFF_CARDS
            : (step == ClassicNewWorldChain.Step.NATION) ? NATION_CARDS : null;
        if (cards == null) return -1;
        for (int i = 0; i < cards.length; i++) {
            final int[] c = cards[i];
            if (x >= c[0] && x < c[0] + c[2] && y >= c[1] && y < c[1] + c[3]) return i;
        }
        return -1;
    }

    /** Whether a virtual point is on a picker's or the world screen's footer. */
    static boolean footerHit(ClassicNewWorldChain.Step step, int x, int y) {
        return (step == ClassicNewWorldChain.Step.DIFFICULTY) ? DIFF_FOOTER.contains(x, y)
            : (step == ClassicNewWorldChain.Step.NATION) ? NATION_FOOTER.contains(x, y)
            : (step == ClassicNewWorldChain.Step.WORLD) && WORLD_FOOTER.contains(x, y);
    }

    /**
     * The picture of the world screen under a virtual point.
     *
     * @return {column, row}, or null between the pictures.
     */
    static int[] worldCellAt(int x, int y) {
        for (int c = 0; c < WORLD_X.length; c++) {
            if (x < WORLD_X[c] || x >= WORLD_X[c] + WORLD_W) continue;
            for (int r = 0; r < WORLD_Y.length; r++) {
                if (y >= WORLD_Y[r] && y < WORLD_Y[r] + WORLD_H) return new int[] { c, r };
            }
        }
        return null;
    }

    /** @return The laid-out lines of a nation page (for tests and review). */
    static List<ClassicTextLayout.Line> pageLines(Assets a, ClassicText.Message m) {
        if (m == null) return Collections.emptyList();
        return ClassicTextLayout.layout(a.intro, m.text,
            (m.width != null) ? m.width : 300, m.x,
            (m.y != null) ? Integer.valueOf(m.y + 3) : null, PAGE_PITCH);
    }
}
