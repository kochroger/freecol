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
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;


/**
 * The painters of the original's <b>departure</b>: the ten pictures between
 * the audience and the first game scene, in which the ship leaves the home
 * port at night and sails to the horizon by day (native captures
 * {@code start-sequence-dutch/opening_069..082}, Holland from Amsterdam,
 * and {@code start-sequence/opening_040..048}, England from London).
 *
 * <p><b>What it is.</b>  A slideshow, not a sprite animation: step
 * {@code k} (1..10) is the full-screen picture {@code LEVN000k.PIK} drawn
 * 1:1 plus the GAME.TXT caption {@code @BUILDk}.  Below the caption band
 * every settled capture equals its picture exactly, so nothing moves
 * inside a step, and no mouse arrow is drawn.  The pictures are the same
 * for every nation.  Their ten palettes are byte-identical: night turns to
 * day because each picture's sky and sea sit one index further down the
 * blue ramp at palette indices 48..63, i.e. the dawn is baked into the
 * art -- there is no palette fade to imitate.  (The pictures derive from
 * the Europe harbour, but EUROPE.PIK's palette differs in 190 entries, so
 * only the LEVN pictures are used.)
 *
 * <p><b>Captions.</b>  FONTINTR in three fixed colours ({@link
 * #CAPTION_INK}, {@link #CAPTION_SHADE}, {@link #CAPTION_SHADOW}), laid out
 * by {@link ClassicNewWorldScreens#paintMessage} exactly like the nation
 * pages: {@code @width=310} gives left x=5, {@code @y=10} gives top 13,
 * line pitch {@link #CAPTION_PITCH}.  Placeholders, see {@link Captions}.
 * With these rules all 21 settled captures (13 Dutch, 8 English) render
 * with 0 differing pixels over the full 320x200 frame.
 *
 * <p><b>Transitions</b> are a random pixel dissolve over the whole screen,
 * caption band included (all twelve captures caught mid-way -- {@code 042},
 * {@code 077} and ten of the timed run {@code departure-dutch-timed} --
 * contain only old-frame and new-frame pixels, and old and new captions
 * overlap).  The first one starts from BLACK, not from the audience (timed
 * capture 167 is black, 168/169 hold only black and LEVN0001 pixels).
 * The original's order is fixed and covers all 64,000 positions -- the
 * twelve captures, seven transitions in three runs, nest without one
 * exception -- but its generator is unknown (Galois LFSRs and the
 * Microsoft C {@code rand()} were rejected), so {@link #dissolveOrder}
 * uses one fixed seeded shuffle instead.  Mid-dissolve frames therefore
 * never match the original pixel for pixel; settled frames always do.
 * The clock is {@link ClassicDepartureTimeline}.
 *
 * <p>All painters are static and take explicit assets and texts, so the
 * scratch preview harness renders exactly what the game shows.  The
 * original pictures and words stay in the git-ignored pack; this class
 * holds only names, indices, colours and geometry.
 */
final class ClassicDeparture {

    private static final Logger logger = Logger.getLogger(ClassicDeparture.class.getName());

    /** Number of pictures and captions. */
    static final int STEPS = 10;

    /** The virtual canvas. */
    static final int W = 320, H = 200, PIXELS = W * H;

    /**
     * Caption colours, the same on night and day pictures (LEVN palette
     * indices 14, 54 and 47): FONTINTR value 1 ink, value 2 shade, value 3
     * shadow.
     */
    static final int CAPTION_INK = 0xFFFF9A, CAPTION_SHADE = 0x698AC3,
        CAPTION_SHADOW = 0x0C0C0C;

    /** Caption line pitch (FONTINTR height 9 + 1). */
    static final int CAPTION_PITCH = 10;

    /** The nation index of Holland (original order England, France, Spain, Holland). */
    static final int DUTCH = 3;

    /**
     * Prefixed to Holland's name in {@code @BUILD4} and {@code @BUILD7}.
     * MEASURED QUIRK: the Dutch captures {@code 074}, {@code 075} and
     * {@code 079} show two spaces before the name where the English ones
     * show one before theirs; with the plain name the three frames differ
     * by 912/1410 px, with this prefix by 0.  Neither NAMES.TXT, GAME.TXT
     * nor the audience (which matched with the plain name) has the extra
     * space; its source is unknown.  France and Spain have no captures and
     * get no prefix (unverified).
     */
    static final String DUTCH_COUNTRY_PREFIX = " ";

    /** The seed of the fixed dissolve order (any constant would do). */
    static final long DISSOLVE_SEED = 0x1492L;

    /** Whether the "departure unavailable" INFO line was logged. */
    private static boolean missLogged = false;


    private ClassicDeparture() {}


    /** @return The pack picture name of step {@code k} (1..10), e.g. LEVN0003.PIK. */
    static String picture(int k) {
        return String.format("LEVN%04d.PIK", k);
    }

    /** @return The GAME.TXT section of step {@code k}'s caption, e.g. BUILD3. */
    static String section(int k) {
        return "BUILD" + k;
    }

    /**
     * The ten pictures from the pack.
     *
     * @param p The pack (may be null).
     * @return Ten entries, null where a picture is missing.
     */
    static BufferedImage[] loadPictures(ClassicPackFiles p) {
        final BufferedImage[] levn = new BufferedImage[STEPS];
        if (p == null) return levn;
        for (int k = 1; k <= STEPS; k++) {
            levn[k - 1] = p.image(ClassicPackFiles.pikKey(picture(k)));
        }
        return levn;
    }

    /**
     * The ten raw captions (placeholders unfilled).
     *
     * @param t The texts (may be null).
     * @return Ten entries, null where a section is missing.
     */
    static ClassicText.Message[] loadCaptions(ClassicText t) {
        final ClassicText.Message[] m = new ClassicText.Message[STEPS];
        if (t == null) return m;
        for (int k = 1; k <= STEPS; k++) m[k - 1] = t.message(section(k));
        return m;
    }

    /**
     * Whether the chain's assets and texts hold everything the departure
     * needs.  If not, one INFO line names what is missing and the caller
     * starts the game at once, as before the departure existed.
     *
     * @param a The chain's assets.
     * @param t The chain's texts.
     * @return True when all ten pictures and captions and the names for
     *     the placeholders are there.
     */
    static boolean available(ClassicNewWorldScreens.Assets a,
                             ClassicNewWorldScreens.Texts t) {
        String miss = null;
        if (a == null || t == null) {
            miss = "the new-game material";
        } else if (a.levn == null || a.levn.length != STEPS) {
            miss = "LEVN0001-0010.PIK";
        } else if (t.build == null || t.build.length != STEPS) {
            miss = "GAME.TXT @BUILD1-10";
        } else if (t.diffTitles == null || t.homePorts == null) {
            miss = "NAMES.TXT @DIFFICULTY/@HOMEPORT";
        } else {
            for (int k = 1; k <= STEPS && miss == null; k++) {
                if (a.levn[k - 1] == null) miss = picture(k);
                else if (t.build[k - 1] == null) miss = "GAME.TXT @" + section(k);
            }
        }
        if (miss == null) return true;
        synchronized (ClassicDeparture.class) {
            if (!missLogged) {
                missLogged = true;
                logger.info("classic_original pack lacks " + miss
                    + " -- the departure is skipped (re-run ant classic-assets)");
            }
        }
        return false;
    }


    /**
     * The ten captions with their placeholders filled for one game.
     *
     * <ul>
     *   <li>{@code @BUILD2}: {@code %STRING0} = the difficulty's title
     *       (NAMES.TXT {@code @DIFFICULTY} column 0, as written -- INFERRED:
     *       both captured runs played the easiest level and show its title),
     *       {@code %STRING1} = the leader's name.</li>
     *   <li>{@code @BUILD3}: {@code %STRING0} = the home port (NAMES.TXT
     *       {@code @HOMEPORT}).</li>
     *   <li>{@code @BUILD4}, {@code @BUILD7}: {@code %STRING0} = the
     *       nation's name (NAMES.TXT {@code @COUNTRY} column 0), Holland's
     *       with {@link #DUTCH_COUNTRY_PREFIX}.</li>
     *   <li>The others have no placeholders.</li>
     * </ul>
     */
    static final class Captions {

        /** Nation index 0..3 (England, France, Spain, Holland). */
        final int nation;

        /** The filled-in values. */
        final String difficultyTitle, leader, homePort, country;

        /** The ten captions, placeholders filled. */
        final ClassicText.Message[] messages;

        private Captions(int nation, String difficultyTitle, String leader,
                         String homePort, String country,
                         ClassicText.Message[] messages) {
            this.nation = nation;
            this.difficultyTitle = difficultyTitle;
            this.leader = leader;
            this.homePort = homePort;
            this.country = country;
            this.messages = messages;
        }

        /** @return The caption of step {@code k} (1..10). */
        ClassicText.Message step(int k) {
            return this.messages[k - 1];
        }

        /**
         * Fill the captions.
         *
         * @param build The ten raw captions.
         * @param diffTitles NAMES.TXT {@code @DIFFICULTY} column 0, easiest first.
         * @param homePorts NAMES.TXT {@code @HOMEPORT}, original nation order.
         * @param countries NAMES.TXT {@code @COUNTRY} column 0.
         * @param nation The nation index.
         * @param difficulty The difficulty index.
         * @param leader The leader's name.
         * @return The captions, or null when an entry is missing.
         */
        static Captions of(ClassicText.Message[] build, String[] diffTitles,
                           String[] homePorts, String[] countries, int nation,
                           int difficulty, String leader) {
            if (build == null || build.length != STEPS || diffTitles == null
                || homePorts == null || countries == null
                || nation < 0 || nation >= homePorts.length
                || nation >= countries.length
                || difficulty < 0 || difficulty >= diffTitles.length) return null;
            final String country = ((nation == DUTCH) ? DUTCH_COUNTRY_PREFIX : "")
                + countries[nation];
            final String name = (leader == null) ? "" : leader;
            final ClassicText.Message[] out = new ClassicText.Message[STEPS];
            for (int k = 1; k <= STEPS; k++) {
                final ClassicText.Message m = build[k - 1];
                if (m == null) return null;
                final Map<String, String> v = values(k, diffTitles[difficulty],
                    name, homePorts[nation], country);
                final List<String> filled = new ArrayList<>(m.text.size());
                for (String l : m.text) filled.add(ClassicText.substitute(l, v));
                out[k - 1] = m.withText(filled);
            }
            return new Captions(nation, diffTitles[difficulty], name,
                                homePorts[nation], country, out);
        }

        /**
         * Fill the captions for the chain's choices.
         *
         * @param t The chain's texts.
         * @param setup The choices ({@code ClassicNewWorldChain.setup}).
         * @return The captions, or null for an unknown nation or level.
         */
        static Captions of(ClassicNewWorldScreens.Texts t,
                           ClassicGUI.NewWorldSetup setup) {
            if (t == null || setup == null) return null;
            final int n = Arrays.asList(ClassicNewWorldScreens.NATION_IDS)
                .indexOf(setup.nationId);
            final int d = Arrays.asList(ClassicNewWorldScreens.DIFFICULTY_IDS)
                .indexOf(setup.difficultyId);
            return of(t.build, t.diffTitles, t.homePorts, t.nationNames, n, d,
                      setup.playerName);
        }

        /** The placeholder values of step {@code k}. */
        static Map<String, String> values(int k, String difficultyTitle,
                                          String leader, String homePort,
                                          String country) {
            final Map<String, String> v = new HashMap<>();
            switch (k) {
            case 2:
                v.put("STRING0", difficultyTitle);
                v.put("STRING1", leader);
                break;
            case 3:
                v.put("STRING0", homePort);
                break;
            case 4: case 7:
                v.put("STRING0", country);
                break;
            default:
                break;
            }
            return v;
        }
    }


    // Painters

    /**
     * Step {@code k} (1..10): the picture 1:1, then its caption.
     *
     * @param g The canvas (320x200 virtual coordinates).
     * @param a The chain's assets ({@code levn} and the FONTINTR font).
     * @param caption The filled caption.
     * @param k The step.
     */
    static void paintStep(Graphics2D g, ClassicNewWorldScreens.Assets a,
                          ClassicText.Message caption, int k) {
        g.drawImage(a.levn[k - 1], 0, 0, null);
        final int[] p = ClassicFont.colours(CAPTION_INK, CAPTION_SHADE, CAPTION_SHADOW);
        ClassicNewWorldScreens.paintMessage(g, a.intro, caption, CAPTION_PITCH, p, p);
    }

    /**
     * Render one frame of the show into a fresh 320x200 RGB image.
     *
     * @param a The chain's assets.
     * @param t The chain's texts.
     * @param c The captions.
     * @param k 0 = the audience still the key leaves (as the chain paints
     *     it, without the arrow; the show itself starts from black, see
     *     {@code ClassicMainMenuPanel.startDeparture}); 1..10 = the
     *     departure steps.
     * @return The frame.
     */
    static BufferedImage renderImage(ClassicNewWorldScreens.Assets a,
                                     ClassicNewWorldScreens.Texts t,
                                     Captions c, int k) {
        final BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = img.createGraphics();
        try {
            if (k == 0) {
                ClassicNewWorldScreens.paintAudience(g, a, t, c.nation);
            } else {
                paintStep(g, a, c.step(k), k);
            }
        } finally {
            g.dispose();
        }
        return img;
    }

    /**
     * Render one frame as 64,000 RGB pixels, row by row.
     *
     * @see #renderImage
     */
    static int[] renderStep(ClassicNewWorldScreens.Assets a,
                            ClassicNewWorldScreens.Texts t, Captions c, int k) {
        return pixels(renderImage(a, t, c, k));
    }

    /**
     * The pixel array behind a {@code TYPE_INT_RGB} image (shared, not a
     * copy).
     */
    static int[] pixels(BufferedImage rgb) {
        return ((DataBufferInt) rgb.getRaster().getDataBuffer()).getData();
    }


    // The dissolve

    /** The fixed permutation of all pixel indices, built once. */
    private static final class Perm {
        static final int[] ORDER = build();

        private static int[] build() {
            final int[] p = new int[PIXELS];
            for (int i = 0; i < PIXELS; i++) p[i] = i;
            final Random r = new Random(DISSOLVE_SEED);
            for (int i = PIXELS - 1; i > 0; i--) {
                final int j = r.nextInt(i + 1);
                final int tmp = p[i];
                p[i] = p[j];
                p[j] = tmp;
            }
            return p;
        }
    }

    /** @return The fixed dissolve permutation of 0..63999 (shared, do not modify). */
    static int[] permutation() {
        return Perm.ORDER;
    }

    /**
     * The order in which a dissolve from {@code from} to {@code to}
     * reveals pixels: exactly the indices that differ, in the order of the
     * one fixed permutation ({@link #DISSOLVE_SEED}), so every transition
     * uses the same spatially random order, as the original does.
     *
     * @param from The old frame's pixels.
     * @param to The new frame's pixels.
     * @return The differing indices, in reveal order.
     */
    static int[] dissolveOrder(int[] from, int[] to) {
        final int[] perm = permutation();
        int n = 0;
        for (int i = 0; i < PIXELS; i++) if (from[i] != to[i]) n++;
        final int[] out = new int[n];
        int j = 0;
        for (int idx : perm) {
            if (from[idx] != to[idx]) out[j++] = idx;
        }
        return out;
    }

    /**
     * Copy the first {@code count} pixels of a dissolve onto a frame
     * (preview and test helper; the panel applies them incrementally).
     *
     * @param shown The pixels to change (the old frame).
     * @param to The new frame.
     * @param order The dissolve order.
     * @param from First index into {@code order} to apply.
     * @param count Index into {@code order} to stop before.
     */
    static void apply(int[] shown, int[] to, int[] order, int from, int count) {
        final int end = Math.min(count, order.length);
        for (int i = Math.max(0, from); i < end; i++) {
            final int idx = order[i];
            shown[idx] = to[idx];
        }
    }
}
